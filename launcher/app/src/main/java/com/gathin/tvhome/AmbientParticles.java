package com.gathin.tvhome;

import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.DisplayMetrics;
import android.util.Property;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Fireflies over a night wallpaper and sakura petals over a day one (config.json ambient_particles).
 *
 * Built to cost the GPU as little as it can: every particle is a small ImageView sharing one pre-rendered
 * bitmap per kind, moved only by looping property animators on translation, alpha and rotation (render node
 * properties, so there is no onDraw and no invalidate loop). The layer sits between the wallpaper and the UI.
 * Nothing exists while stopped: {@link #stop()} cancels every animator and removes the views. The motion
 * itself is decided by {@link ParticleMotion}.
 */
final class AmbientParticles {
    private final Context ctx;
    private final FrameLayout root;
    private final DisplayMetrics dm;
    private final float density;
    private final List<ObjectAnimator> animators = new ArrayList<>();
    private FrameLayout layer;
    private int kind = -1;
    private Bitmap fireflyBitmap, petalBitmap;

    AmbientParticles(Context ctx, FrameLayout root) {
        this.ctx = ctx;
        this.root = root;
        dm = ctx.getResources().getDisplayMetrics();
        density = dm.density;
    }

    /**
     * Shows this kind just above {@code wall}. The same kind already running is left alone; another kind
     * replaces it. Fails silently: if a bitmap cannot be made there are simply no particles.
     */
    void start(int wantedKind, View wall) {
        if (layer != null && kind == wantedKind) return;
        stop();
        try {
            Bitmap bmp = bitmapFor(wantedKind);
            int at = root.indexOfChild(wall);
            if (bmp == null || at < 0) return;
            kind = wantedKind;
            layer = new FrameLayout(ctx);
            layer.setClickable(false);
            layer.setFocusable(false);
            layer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            root.addView(layer, at + 1, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            for (ParticleMotion.Spec s : ParticleMotion.create(wantedKind, dm.widthPixels, dm.heightPixels,
                    Math.max(bmp.getWidth(), bmp.getHeight()), new Random())) {
                addParticle(bmp, s);
            }
        } catch (RuntimeException | OutOfMemoryError e) {
            stop();
        }
    }

    /** Cancels every animation and removes the particles. */
    void stop() {
        for (ObjectAnimator a : animators) a.cancel();
        animators.clear();
        if (layer != null) {
            for (int i = 0; i < layer.getChildCount(); i++) layer.getChildAt(i).animate().cancel();
            root.removeView(layer);
            layer = null;
        }
        kind = -1;
    }

    private void addParticle(Bitmap bmp, ParticleMotion.Spec s) {
        ImageView v = new ImageView(ctx);
        v.setImageBitmap(bmp);
        v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        v.setScaleX(s.scale);
        v.setScaleY(s.scale);
        v.setTranslationX(s.xFrom);
        v.setTranslationY(s.yFrom);
        v.setRotation(s.rotationFrom);
        v.setAlpha(s.alphaLow);
        layer.addView(v, new FrameLayout.LayoutParams(bmp.getWidth(), bmp.getHeight(), Gravity.TOP | Gravity.LEFT));
        TimeInterpolator smooth = new AccelerateDecelerateInterpolator();
        if (s.yRestarts) {
            // A petal falls at a steady pace and starts over at the top; the sway and the turn go back and forth.
            loop(v, View.TRANSLATION_Y, s.yFrom, s.yTo, s.yMs, new LinearInterpolator(), ObjectAnimator.RESTART, s.phase);
            v.setAlpha(0f);
            v.animate().alpha(s.alphaHigh).setDuration(1200).start();   // a gentle fade-in, not a pop
        } else {
            loop(v, View.TRANSLATION_Y, s.yFrom, s.yTo, s.yMs, smooth, ObjectAnimator.REVERSE, s.phase);
            loop(v, View.ALPHA, s.alphaLow, s.alphaHigh, s.alphaMs, smooth, ObjectAnimator.REVERSE, s.phase);
        }
        loop(v, View.TRANSLATION_X, s.xFrom, s.xTo, s.xMs, smooth, ObjectAnimator.REVERSE, s.phase);
        if (s.rotationMs > 0) {
            loop(v, View.ROTATION, s.rotationFrom, s.rotationTo, s.rotationMs, smooth, ObjectAnimator.REVERSE, s.phase);
        }
    }

    private void loop(View v, Property<View, Float> property, float from, float to, long ms,
                      TimeInterpolator interpolator, int repeatMode, float phase) {
        ObjectAnimator a = ObjectAnimator.ofFloat(v, property, from, to);
        a.setDuration(ms);
        a.setInterpolator(interpolator);
        a.setRepeatCount(ObjectAnimator.INFINITE);
        a.setRepeatMode(repeatMode);
        animators.add(a);
        a.start();
        a.setCurrentFraction(phase);   // so the particles do not all start in step
    }

    private Bitmap bitmapFor(int wantedKind) {
        if (wantedKind == ParticleMotion.KIND_PETAL) {
            if (petalBitmap == null) petalBitmap = petal();
            return petalBitmap;
        }
        if (fireflyBitmap == null) fireflyBitmap = firefly();
        return fireflyBitmap;
    }

    /** A soft warm yellow-green glow: bright core, falling off to nothing at the edge. 20 to 24 px on a TV. */
    private Bitmap firefly() {
        int size = Math.max(16, Math.min(24, Math.round(16 * density)));
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float c = size / 2f;
        paint.setShader(new RadialGradient(c, c, c,
                new int[]{0xFFF4FF9E, 0xB3D4F04A, 0x33B9D93A, 0x00B9D93A},
                new float[]{0f, 0.3f, 0.65f, 1f}, Shader.TileMode.CLAMP));
        new Canvas(bmp).drawCircle(c, c, c, paint);
        return bmp;
    }

    /** A pink petal with a notch at the tip, a shade deeper towards the base. About 16 by 20 px. */
    private Bitmap petal() {
        int w = Math.max(14, Math.min(20, Math.round(12 * density)));
        int h = Math.max(16, Math.min(24, Math.round(15 * density)));
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Path p = new Path();
        p.moveTo(w * 0.5f, h * 0.16f);                                   // the notch
        p.cubicTo(w * 0.42f, h * -0.04f, w * 0.05f, h * 0.0f, w * 0.06f, h * 0.38f);
        p.cubicTo(w * 0.07f, h * 0.7f, w * 0.4f, h * 0.92f, w * 0.5f, h);
        p.cubicTo(w * 0.6f, h * 0.92f, w * 0.93f, h * 0.7f, w * 0.94f, h * 0.38f);
        p.cubicTo(w * 0.95f, h * 0.0f, w * 0.58f, h * -0.04f, w * 0.5f, h * 0.16f);
        p.close();
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new LinearGradient(0, 0, 0, h, 0xFFFFE3EC, 0xFFF48FB1, Shader.TileMode.CLAMP));
        new Canvas(bmp).drawPath(p, paint);
        return bmp;
    }
}
