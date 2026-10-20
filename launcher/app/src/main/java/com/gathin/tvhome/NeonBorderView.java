package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * The now playing card's neon border: a rounded rectangle stroked with a sweep gradient that turns slowly, so
 * light seems to flow round the edge. The glow is the same shape drawn three times, wide and faint, medium, then
 * a crisp core, so there is no blur (too slow on the TV's GPU).
 *
 * The view is larger than the card by {@code now_playing_glow_pad} on every side, to leave room for the glow, and
 * sits behind the card. Per frame only the shader's matrix changes and only this view is invalidated.
 * Nothing runs unless {@link #setRunning} says so, and nothing turns when motion is reduced.
 */
public class NeonBorderView extends View {
    private static final long TURN_MS = 4000L;
    private static final long PALETTE_FADE_MS = 400L;
    /** Stroke width in dp and alpha of the outer glow, the inner glow and the core. */
    private static final float[] WIDTH_DP = {10f, 5f, 2f};
    private static final float[] ALPHA = {0.18f, 0.35f, 1f};
    private static final float CORNER_DP = 16f;
    /** Brightness while nothing plays, and how much brighter the glow gets while a button has focus. */
    private static final float IDLE_LEVEL = 0.6f;
    private static final float FOCUS_GLOW = 1.4f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Matrix matrix = new Matrix();
    private final float density;
    private final float[] widths = new float[3];
    private final float pad;
    private final int[] current = NeonPalette.fallback();
    private final int[] from = new int[NeonPalette.COUNT];
    private int[] target = NeonPalette.fallback();
    private SweepGradient shader;
    private float angle;
    private float cx, cy;
    private boolean reducedMotion;
    private boolean running;
    private boolean lit = true;
    private boolean focused;
    private ValueAnimator spin;
    private ValueAnimator fade;

    public NeonBorderView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        pad = getResources().getDimension(R.dimen.now_playing_glow_pad);
        for (int i = 0; i < widths.length; i++) widths[i] = WIDTH_DP[i] * density;
        paint.setStyle(Paint.Style.STROKE);
    }

    /** True turns the border off for good: a static gradient, and palette changes jump instead of fading. */
    void setReducedMotion(boolean value) {
        reducedMotion = value;
        updateSpin();
    }

    /** Fades the palette to the one for {@code accent} ({@link ArtColor#NONE} for the pink/cyan/violet default). */
    void setAccent(int accent) {
        int[] next = NeonPalette.stops(accent);
        if (java.util.Arrays.equals(next, target)) return;
        target = next;
        if (fade != null) fade.cancel();
        if (reducedMotion || shader == null || !isShown()) {
            System.arraycopy(target, 0, current, 0, current.length);
            rebuildShader();
            invalidate();
            return;
        }
        System.arraycopy(current, 0, from, 0, from.length);
        fade = ValueAnimator.ofFloat(0f, 1f);
        fade.setDuration(PALETTE_FADE_MS);
        fade.addUpdateListener(a -> {
            NeonPalette.blend(from, target, a.getAnimatedFraction(), current);
            rebuildShader();
            invalidate();
        });
        fade.start();
    }

    /** True while the border should flow: the card is on screen, playing, and Home is in front. */
    void setRunning(boolean value) {
        running = value;
        updateSpin();
    }

    /** False dims the border and holds it still (paused); true is the normal brightness. */
    void setLit(boolean value) {
        if (lit == value) return;
        lit = value;
        invalidate();
    }

    /** A transport button has focus: the glow gets a little brighter. */
    void setFocused(boolean value) {
        if (focused == value) return;
        focused = value;
        invalidate();
    }

    private void updateSpin() {
        boolean want = running && !reducedMotion && getWidth() > 0;
        if (want == (spin != null)) return;
        if (!want) {
            spin.cancel();
            spin = null;
            return;
        }
        spin = ValueAnimator.ofFloat(0f, 360f);
        spin.setDuration(TURN_MS);
        spin.setInterpolator(new LinearInterpolator());
        spin.setRepeatCount(ValueAnimator.INFINITE);
        spin.addUpdateListener(a -> {
            angle = (float) a.getAnimatedValue();
            applyRotation();
            invalidate();   // this view only: its bounds are the dirty region
        });
        spin.start();
    }

    private void applyRotation() {
        if (shader == null) return;
        matrix.setRotate(angle, cx, cy);
        shader.setLocalMatrix(matrix);
    }

    private void rebuildShader() {
        if (getWidth() == 0) return;
        shader = new SweepGradient(cx, cy, current, NeonPalette.POSITIONS);
        applyRotation();
        paint.setShader(shader);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // The core runs along the card's edge, half a core width inside it; the glow spreads out from there.
        float inset = pad + widths[2] / 2f;
        rect.set(inset, inset, w - inset, h - inset);
        cx = w / 2f;
        cy = h / 2f;
        rebuildShader();
        updateSpin();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (spin != null) {
            spin.cancel();
            spin = null;
        }
        if (fade != null) fade.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateSpin();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (shader == null) return;
        float level = lit ? 1f : IDLE_LEVEL;
        float radius = CORNER_DP * density - widths[2] / 2f;
        for (int i = 0; i < widths.length; i++) {
            float a = ALPHA[i] * level * (focused && i < 2 ? FOCUS_GLOW : 1f);
            paint.setStrokeWidth(widths[i]);
            paint.setAlpha(Math.round(255f * Math.min(1f, a)));
            canvas.drawRoundRect(rect, radius, radius, paint);
        }
    }
}
