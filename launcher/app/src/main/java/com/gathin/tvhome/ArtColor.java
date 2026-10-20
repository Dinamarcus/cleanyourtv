package com.gathin.tvhome;

/**
 * The accent colour of an album cover, from a tiny downscaled copy of it. Pure arithmetic over ARGB pixels,
 * so it runs in plain JVM tests (android.graphics.Color is not available there).
 */
final class ArtColor {
    /** Returned when the picture has no usable colour: all black, all white or transparent. */
    static final int NONE = 0;
    /** The brightest channel is lifted to at least this, so the accent stays visible on dark glass. */
    static final int MIN_PEAK = 160;

    private ArtColor() {
    }

    /**
     * A saturation weighted average of the pixels, as an opaque ARGB colour, or {@link #NONE}. Transparent,
     * near-black and near-white pixels are ignored, and vivid pixels count far more than greyish ones, so a
     * cover that is mostly black with a red logo gives red rather than a muddy brown.
     */
    static int accent(int[] pixels) {
        if (pixels == null) return NONE;
        double sumR = 0, sumG = 0, sumB = 0, sumW = 0;
        for (int p : pixels) {
            if ((p >>> 24) < 128) continue;
            int r = (p >> 16) & 0xFF, g = (p >> 8) & 0xFF, b = p & 0xFF;
            int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
            if (max < 24 || min > 232) continue;
            double saturation = (max - min) / (double) max;
            double weight = 0.02 + saturation * saturation;
            sumR += r * weight;
            sumG += g * weight;
            sumB += b * weight;
            sumW += weight;
        }
        if (sumW == 0) return NONE;
        int r = (int) Math.round(sumR / sumW), g = (int) Math.round(sumG / sumW), b = (int) Math.round(sumB / sumW);
        int peak = Math.max(r, Math.max(g, b));
        if (peak < MIN_PEAK) {
            double lift = MIN_PEAK / (double) Math.max(peak, 1);
            r = (int) Math.min(255, Math.round(r * lift));
            g = (int) Math.min(255, Math.round(g * lift));
            b = (int) Math.min(255, Math.round(b * lift));
        }
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
