package com.gathin.tvhome;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.io.File;

import java.util.Random;

/**
 * Screensaver: the launcher's thin clock and date on black, over a slow ambient glow.
 * Every minute the clock drifts to a new spot and the glow shifts colour, so nothing burns in.
 * No network. With a theme wallpaper, the picture sits dimmed behind the clock and drifts a little each
 * minute; otherwise it is black. One downsampled bitmap at most: cheap enough for a 1 GB TV.
 */
public class ClockDream extends DreamService {

    private static final long MOVE_MS = 60_000L;
    /** Deep, low-brightness glow colours; the glow is drawn at partial alpha on black. */
    private static final int[] PALETTE = {0xFF2B5CFF, 0xFF00A39A, 0xFF8A3FFC, 0xFFE0661B, 0xFFD6336C};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private View content;
    private ImageView wall;
    private GradientDrawable glow;
    private int colorIndex;
    private int glowColor;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            moveClock();
            shiftGlow();
            driftWall();
            handler.postDelayed(this, MOVE_MS);
        }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);   // any key wakes the TV back to where it was
        setFullscreen(true);
        setScreenBright(false);
        setContentView(R.layout.dream_clock);

        content = findViewById(R.id.dreamContent);
        View ambient = findViewById(R.id.dreamAmbient);
        glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        ambient.setBackground(glow);
        ambient.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                glow.setGradientRadius((r - l) * 0.55f));
        // The day picture or the night one, by the time the screensaver starts.
        File picture = Config.load(this).wallpaperAt(Config.minuteOfDay());
        if (picture != null) showWall(picture);
        colorIndex = random.nextInt(PALETTE.length);
        setGlow(PALETTE[colorIndex]);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        content.setAlpha(0f);
        content.post(() -> {
            placeClock();
            content.animate().alpha(1f).setDuration(1200).start();
        });
        handler.postDelayed(tick, MOVE_MS);
    }

    @Override
    public void onDreamingStopped() {
        handler.removeCallbacks(tick);
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        handler.removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    private void moveClock() {
        content.animate().alpha(0f).setDuration(900).withEndAction(() -> {
            placeClock();
            content.animate().alpha(1f).setDuration(1200).start();
        }).start();
    }

    /** A random spot that keeps the whole clock on screen, with the glow centred behind it. */
    private void placeClock() {
        View parent = (View) content.getParent();
        int maxX = Math.max(0, parent.getWidth() - content.getWidth());
        int maxY = Math.max(0, parent.getHeight() - content.getHeight());
        float x = maxX * (0.1f + 0.8f * random.nextFloat());
        float y = maxY * (0.1f + 0.8f * random.nextFloat());
        content.setX(x);
        content.setY(y);
        if (parent.getWidth() > 0 && parent.getHeight() > 0) {
            glow.setGradientCenter((x + content.getWidth() / 2f) / parent.getWidth(),
                    (y + content.getHeight() / 2f) / parent.getHeight());
        }
    }

    /** The theme's wallpaper, dimmed and slightly enlarged so it has room to drift without showing edges. */
    private void showWall(File file) {
        try {
            android.graphics.Bitmap bmp = Wallpaper.decode(file, getResources().getDisplayMetrics());
            if (bmp == null) return;
            wall = new ImageView(this);
            wall.setScaleType(ImageView.ScaleType.CENTER_CROP);
            wall.setImageBitmap(bmp);
            wall.setAlpha(0.4f);
            wall.setScaleX(1.12f);
            wall.setScaleY(1.12f);
            ViewGroup root = (ViewGroup) content.getParent();
            root.addView(wall, 0, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            shadowTexts(content);
        } catch (Exception | OutOfMemoryError e) {
            wall = null;   // a bad picture leaves the plain black screensaver
        }
    }

    /** Over the picture the thin clock needs a soft shadow to stay readable. */
    private void shadowTexts(View v) {
        float d = getResources().getDisplayMetrics().density;
        if (v instanceof android.widget.TextView) {
            ((android.widget.TextView) v).setShadowLayer(14f * d, 0f, 2f * d, 0xB3000000);
        } else if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) shadowTexts(g.getChildAt(i));
        }
    }

    /** A slow move to a new spot within the enlarged picture's margin; nothing stays put long enough to burn in. */
    private void driftWall() {
        if (wall == null || wall.getWidth() == 0) return;
        float mx = wall.getWidth() * 0.05f;
        float my = wall.getHeight() * 0.05f;
        wall.animate()
                .translationX((random.nextFloat() * 2f - 1f) * mx)
                .translationY((random.nextFloat() * 2f - 1f) * my)
                .setDuration(20_000).setInterpolator(new DecelerateInterpolator(1.5f))
                .withLayer().start();
    }

    private void shiftGlow() {
        int from = glowColor;
        colorIndex = (colorIndex + 1) % PALETTE.length;
        ValueAnimator anim = ValueAnimator.ofObject(new ArgbEvaluator(), from, PALETTE[colorIndex]);
        anim.setDuration(8000);
        anim.addUpdateListener(a -> setGlow((int) a.getAnimatedValue()));
        anim.start();
    }

    private void setGlow(int color) {
        glowColor = color;
        int c = color & 0x00FFFFFF;
        glow.setColors(new int[]{0x40000000 | c, 0x14000000 | c, 0x00000000 | c});
    }
}
