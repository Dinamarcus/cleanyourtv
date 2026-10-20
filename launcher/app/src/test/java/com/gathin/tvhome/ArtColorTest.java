package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Plain JVM tests for the album cover accent colour. */
public class ArtColorTest {

    private static int[] fill(int n, int argb) {
        int[] p = new int[n];
        Arrays.fill(p, argb);
        return p;
    }

    private static int red(int c) { return (c >> 16) & 0xFF; }
    private static int green(int c) { return (c >> 8) & 0xFF; }
    private static int blue(int c) { return c & 0xFF; }

    @Test
    public void aSolidColourComesBackAsItself() {
        assertEquals(0xFFC83232, ArtColor.accent(fill(64, 0xFFC83232)));
    }

    @Test
    public void theResultIsOpaque() {
        assertEquals(0xFF, ArtColor.accent(fill(64, 0xE0408020)) >>> 24);
    }

    @Test
    public void nothingUsableGivesNone() {
        assertEquals(ArtColor.NONE, ArtColor.accent(null));
        assertEquals(ArtColor.NONE, ArtColor.accent(new int[0]));
        assertEquals(ArtColor.NONE, ArtColor.accent(fill(64, 0xFF000000)));
        assertEquals(ArtColor.NONE, ArtColor.accent(fill(64, 0xFFFFFFFF)));
        assertEquals(ArtColor.NONE, ArtColor.accent(fill(64, 0x00FF0000)));
    }

    @Test
    public void blackWhiteAndTransparentPixelsAreIgnored() {
        int[] p = fill(64, 0xFF000000);
        Arrays.fill(p, 0, 16, 0xFFFFFFFF);
        Arrays.fill(p, 16, 32, 0x00123456);
        Arrays.fill(p, 32, 40, 0xFF3366CC);
        assertEquals(0xFF3366CC, ArtColor.accent(p));
    }

    @Test
    public void vividPixelsOutweighGreyOnes() {
        int[] p = fill(64, 0xFF808080);   // mostly grey
        Arrays.fill(p, 0, 8, 0xFFE02020); // a red logo
        int c = ArtColor.accent(p);
        assertTrue("red should lead: " + Integer.toHexString(c), red(c) > green(c) + 40 && red(c) > blue(c) + 40);
    }

    @Test
    public void aDarkColourIsLiftedToBeVisible() {
        int c = ArtColor.accent(fill(64, 0xFF101840));
        assertEquals(ArtColor.MIN_PEAK, Math.max(red(c), Math.max(green(c), blue(c))));
        assertTrue(blue(c) > red(c));
    }

    @Test
    public void mixedColoursAverage() {
        int[] p = new int[64];
        Arrays.fill(p, 0, 32, 0xFFFF0000);
        Arrays.fill(p, 32, 64, 0xFF0000FF);
        int c = ArtColor.accent(p);
        assertTrue(Math.abs(red(c) - blue(c)) <= 1);
        assertEquals(0, green(c));
    }
}
