package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The motion of the ambient particles over a theme's wallpaper (config.json ambient_particles): fireflies for
 * the night picture, sakura petals for the day one. Pure numbers, so the paths can be tested on the JVM;
 * {@link AmbientParticles} turns each {@link Spec} into a few looping property animators.
 *
 * A particle is a small view moved only through translation, alpha and rotation (render node properties), so
 * nothing is drawn per frame on the CPU. Positions are the view's top-left corner in pixels.
 */
final class ParticleMotion {
    static final int KIND_FIREFLY = 0;
    static final int KIND_PETAL = 1;
    /** How many of each kind are made; the total stays small because every moving dot costs the GPU a redraw. */
    static final int COUNT = 12;
    static final int MAX_COUNT = 14;

    private ParticleMotion() {
    }

    /** The kind that fits the picture on screen: petals by day, fireflies otherwise. */
    static int kindFor(boolean daytime) {
        return daytime ? KIND_PETAL : KIND_FIREFLY;
    }

    /**
     * One particle. X, Y, alpha and rotation each travel from {@code *From} to {@code *To} in {@code *Ms} and
     * back (fireflies), or the fall restarts at the top (petals, see {@link #yRestarts}). A zero duration means
     * that property does not move. {@code phase} (0 to 1) is how far into its loop the particle starts.
     */
    static final class Spec {
        float xFrom, xTo, yFrom, yTo;
        long xMs, yMs;
        float alphaLow, alphaHigh;
        long alphaMs;
        float rotationFrom, rotationTo;
        long rotationMs;
        float scale;
        float phase;
        boolean yRestarts;
    }

    /**
     * Makes {@link #COUNT} particles for a screen of w by h pixels; {@code size} is the particle's bitmap size
     * in pixels, so that none starts or wanders off the screen. Deterministic for a given Random.
     */
    static List<Spec> create(int kind, int w, int h, int size, Random random) {
        List<Spec> out = new ArrayList<>(COUNT);
        for (int i = 0; i < COUNT; i++) {
            out.add(kind == KIND_PETAL ? petal(i, w, h, size, random) : firefly(w, h, size, random));
        }
        return out;
    }

    /** A warm dot that wanders slowly around a spot (X and Y with different periods) and pulses in alpha. */
    private static Spec firefly(int w, int h, int size, Random r) {
        Spec s = new Spec();
        s.scale = between(r, 0.7f, 1.3f);
        float reach = size * s.scale;
        float ampX = between(r, 0.03f, 0.08f) * w, ampY = between(r, 0.04f, 0.09f) * h;
        float x = between(r, ampX, Math.max(ampX, w - reach - ampX));
        float y = between(r, Math.max(ampY, 0.15f * h), Math.max(ampY, h - reach - ampY));
        s.xFrom = x - ampX;
        s.xTo = x + ampX;
        s.yFrom = y - ampY;
        s.yTo = y + ampY;
        s.xMs = (long) between(r, 7000f, 12000f);
        s.yMs = (long) between(r, 9000f, 15000f);
        s.alphaLow = between(r, 0.05f, 0.2f);
        s.alphaHigh = between(r, 0.65f, 0.95f);
        s.alphaMs = (long) between(r, 1400f, 3200f);
        s.phase = r.nextFloat();
        return s;
    }

    /**
     * A petal that falls from above the top edge to below the bottom one, swaying side to side and turning
     * slowly. Petals take a column each (spread over the width, shuffled by chance) so they do not bunch up.
     */
    private static Spec petal(int index, int w, int h, int size, Random r) {
        Spec s = new Spec();
        s.scale = between(r, 0.8f, 1.25f);
        float reach = size * s.scale;
        float amp = between(r, 0.015f, 0.04f) * w;
        float column = (index + r.nextFloat()) / COUNT;
        float x = amp + column * Math.max(0f, w - reach - 2 * amp);
        s.xFrom = x - amp;
        s.xTo = x + amp;
        s.yFrom = -2 * reach;
        s.yTo = h + reach;
        s.yRestarts = true;
        s.xMs = (long) between(r, 3000f, 5500f);
        s.yMs = (long) between(r, 16000f, 26000f);
        s.alphaLow = s.alphaHigh = between(r, 0.7f, 0.9f);
        s.rotationFrom = between(r, -40f, 40f);
        s.rotationTo = s.rotationFrom + (r.nextBoolean() ? 1 : -1) * between(r, 70f, 150f);
        s.rotationMs = (long) between(r, 4000f, 7500f);
        s.phase = r.nextFloat();
        return s;
    }

    private static float between(Random r, float lo, float hi) {
        return lo + r.nextFloat() * (hi - lo);
    }
}
