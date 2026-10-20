package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/** Plain JVM tests for the neon border palette. */
public class NeonPaletteTest {

    private static float hueOf(int argb) {
        return NeonPalette.rgbToHsv(argb)[0];
    }

    private static float hueGap(float a, float b) {
        float d = Math.abs(a - b) % 360f;
        return Math.min(d, 360f - d);
    }

    @Test
    public void noAccentGivesTheTokyoNightFallback() {
        int[] s = NeonPalette.stops(ArtColor.NONE);
        assertEquals(NeonPalette.COUNT, s.length);
        assertEquals(NeonPalette.PINK, s[0]);
        assertEquals(NeonPalette.CYAN, s[1]);
        assertEquals(NeonPalette.VIOLET, s[2]);
        assertArrayEquals(NeonPalette.fallback(), s);
    }

    @Test
    public void aGreyAccentHasNoHueAndFallsBack() {
        assertArrayEquals(NeonPalette.fallback(), NeonPalette.stops(0xFF808080));
        assertArrayEquals(NeonPalette.fallback(), NeonPalette.stops(0xFFA09C9C));
    }

    @Test
    public void thePaletteLoopsSeamlessly() {
        for (int accent : new int[]{ArtColor.NONE, 0xFFC83232, 0xFF2060E0}) {
            int[] s = NeonPalette.stops(accent);
            assertEquals(s[0], s[s.length - 1]);
        }
        assertEquals(NeonPalette.COUNT, NeonPalette.POSITIONS.length);
        assertEquals(0f, NeonPalette.POSITIONS[0], 0f);
        assertEquals(1f, NeonPalette.POSITIONS[NeonPalette.COUNT - 1], 0f);
    }

    @Test
    public void vividStopsKeepTheAccentHueAndOffsets() {
        int accent = 0xFFC83232;   // red, hue 0
        int[] s = NeonPalette.stops(accent);
        float h = hueOf(accent);
        assertEquals(0f, hueGap(h, hueOf(s[0])), 1.5f);
        assertEquals(0f, hueGap(h + 40f, hueOf(s[1])), 1.5f);
        assertEquals(0f, hueGap(h + 180f, hueOf(s[2])), 1.5f);
        assertEquals(0f, hueGap(h + 300f, hueOf(s[3])), 1.5f);
        for (int i = 0; i < 4; i++) {
            float[] hsv = NeonPalette.rgbToHsv(s[i]);
            assertEquals(0xFF, s[i] >>> 24);
            assertTrue(hsv[1] >= 0.84f);
            assertEquals(1f, hsv[2], 0.01f);
        }
    }

    @Test
    public void theTailIsTheLastVividStopFaded() {
        int[] s = NeonPalette.stops(0xFF2060E0);
        assertTrue((s[4] >>> 24) < 0x80);
        assertEquals(s[3] & 0x00FFFFFF, s[4] & 0x00FFFFFF);
    }

    @Test
    public void differentCoversGiveDifferentPalettes() {
        assertNotEquals(NeonPalette.stops(0xFFC83232)[0], NeonPalette.stops(0xFF2060E0)[0]);
    }

    @Test
    public void hsvRoundTripsPrimaries() {
        assertEquals(0xFFFF0000, NeonPalette.hsvToRgb(0f, 1f, 1f));
        assertEquals(0xFF00FF00, NeonPalette.hsvToRgb(120f, 1f, 1f));
        assertEquals(0xFF0000FF, NeonPalette.hsvToRgb(240f, 1f, 1f));
        assertEquals(0xFF00FFFF, NeonPalette.hsvToRgb(-180f, 1f, 1f));
        assertEquals(240f, hueOf(0xFF0000FF), 0.01f);
    }

    @Test
    public void lerpEndpointsAndMidpoint() {
        assertEquals(0xFF102030, NeonPalette.lerp(0xFF102030, 0x00FFFFFF, 0f));
        assertEquals(0x00FFFFFF, NeonPalette.lerp(0xFF102030, 0x00FFFFFF, 1f));
        assertEquals(0x80808080, NeonPalette.lerp(0x00000000, 0xFFFFFFFF, 0.5f));
        assertEquals(0x00000000, NeonPalette.lerp(0x00000000, 0xFFFFFFFF, -1f));
        assertEquals(0xFFFFFFFF, NeonPalette.lerp(0x00000000, 0xFFFFFFFF, 2f));
    }

    @Test
    public void blendFillsTheOutputPaletteAtTheEnds() {
        int[] a = NeonPalette.fallback();
        int[] b = NeonPalette.stops(0xFFC83232);
        int[] out = new int[NeonPalette.COUNT];
        NeonPalette.blend(a, b, 0f, out);
        assertArrayEquals(a, out);
        NeonPalette.blend(a, b, 1f, out);
        assertArrayEquals(b, out);
    }
}
