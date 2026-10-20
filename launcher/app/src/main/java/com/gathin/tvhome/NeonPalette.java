package com.gathin.tvhome;

/**
 * Colours for the now playing card's neon border, from the cover's accent. Pure arithmetic over ARGB ints
 * (no android.graphics), so it runs in plain JVM tests.
 */
final class NeonPalette {
    /** Colours in a palette: four vivid stops, a faded tail, and the first stop again so the sweep loops. */
    static final int COUNT = 6;
    /** Where each colour sits on the sweep; the tail fades out and the last rise into the first is the "head". */
    static final float[] POSITIONS = {0f, 0.2f, 0.4f, 0.6f, 0.85f, 1f};
    /** Tokyo-night neon, used when there is no cover or it has no clear hue. */
    static final int PINK = 0xFFFF4FD8, CYAN = 0xFF3DF2FF, VIOLET = 0xFF9B6CFF;
    private static final int TAIL_ALPHA = 0x33;
    private static final float SATURATION = 0.9f;
    private static final float[] HUE_OFFSETS = {0f, 40f, 180f, 300f};
    /** Below this saturation the accent is grey and its hue means nothing. */
    private static final float MIN_ACCENT_SATURATION = 0.12f;

    private NeonPalette() {
    }

    /** A new palette of {@link #COUNT} colours for {@code accent} (opaque ARGB, or {@link ArtColor#NONE}). */
    static int[] stops(int accent) {
        float[] hsv = rgbToHsv(accent);
        if (accent == ArtColor.NONE || hsv[1] < MIN_ACCENT_SATURATION) return fallback();
        int[] out = new int[COUNT];
        for (int i = 0; i < HUE_OFFSETS.length; i++) out[i] = hsvToRgb(hsv[0] + HUE_OFFSETS[i], SATURATION, 1f);
        out[4] = withAlpha(out[3], TAIL_ALPHA);
        out[5] = out[0];
        return out;
    }

    /** The pink, cyan and violet palette. */
    static int[] fallback() {
        int[] out = new int[COUNT];
        out[0] = PINK;
        out[1] = CYAN;
        out[2] = VIOLET;
        out[3] = lerp(VIOLET, PINK, 0.5f);
        out[4] = withAlpha(out[3], TAIL_ALPHA);
        out[5] = out[0];
        return out;
    }

    /** Channel by channel (alpha included) blend of two ARGB colours; {@code t} is clamped to 0..1. */
    static int lerp(int a, int b, float t) {
        float k = Math.max(0f, Math.min(1f, t));
        int out = 0;
        for (int shift = 24; shift >= 0; shift -= 8) {
            int ca = (a >>> shift) & 0xFF, cb = (b >>> shift) & 0xFF;
            out |= Math.round(ca + (cb - ca) * k) << shift;
        }
        return out;
    }

    /** Writes the blend of two palettes into {@code out}; all three have {@link #COUNT} entries. */
    static void blend(int[] from, int[] to, float t, int[] out) {
        for (int i = 0; i < COUNT; i++) out[i] = lerp(from[i], to[i], t);
    }

    static int withAlpha(int argb, int alpha) {
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    /** Hue 0..360, saturation 0..1, value 0..1 of an RGB colour (alpha ignored). */
    static float[] rgbToHsv(int argb) {
        float r = ((argb >> 16) & 0xFF) / 255f, g = ((argb >> 8) & 0xFF) / 255f, b = (argb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h;
        if (d == 0f) h = 0f;
        else if (max == r) h = 60f * (((g - b) / d) % 6f);
        else if (max == g) h = 60f * ((b - r) / d + 2f);
        else h = 60f * ((r - g) / d + 4f);
        if (h < 0f) h += 360f;
        return new float[]{h, max == 0f ? 0f : d / max, max};
    }

    /** Opaque ARGB for a hue in degrees (any value, wrapped), saturation and value in 0..1. */
    static int hsvToRgb(float hue, float s, float v) {
        float h = ((hue % 360f) + 360f) % 360f / 60f;
        int sector = (int) h;
        float f = h - sector;
        float p = v * (1f - s), q = v * (1f - s * f), t = v * (1f - s * (1f - f));
        float r, g, b;
        switch (sector) {
            case 0: r = v; g = t; b = p; break;
            case 1: r = q; g = v; b = p; break;
            case 2: r = p; g = v; b = t; break;
            case 3: r = p; g = q; b = v; break;
            case 4: r = t; g = p; b = v; break;
            default: r = v; g = p; b = q; break;
        }
        return 0xFF000000 | (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255);
    }
}
