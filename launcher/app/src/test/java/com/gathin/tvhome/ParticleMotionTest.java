package com.gathin.tvhome;

import org.junit.Test;

import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Plain JVM tests for the ambient particle paths: counts, bounds and determinism. */
public class ParticleMotionTest {

    private static final int W = 1280, H = 720, SIZE = 22;

    private static List<ParticleMotion.Spec> make(int kind, long seed) {
        return ParticleMotion.create(kind, W, H, SIZE, new Random(seed));
    }

    @Test
    public void countStaysSmall() {
        assertTrue(ParticleMotion.COUNT <= ParticleMotion.MAX_COUNT);
        assertEquals(ParticleMotion.COUNT, make(ParticleMotion.KIND_FIREFLY, 1).size());
        assertEquals(ParticleMotion.COUNT, make(ParticleMotion.KIND_PETAL, 1).size());
    }

    @Test
    public void petalsByDayFirefliesByNight() {
        assertEquals(ParticleMotion.KIND_PETAL, ParticleMotion.kindFor(true));
        assertEquals(ParticleMotion.KIND_FIREFLY, ParticleMotion.kindFor(false));
    }

    @Test
    public void sameSeedSameMotion() {
        for (int kind : new int[]{ParticleMotion.KIND_FIREFLY, ParticleMotion.KIND_PETAL}) {
            List<ParticleMotion.Spec> a = make(kind, 7), b = make(kind, 7), c = make(kind, 8);
            for (int i = 0; i < a.size(); i++) {
                assertEquals(a.get(i).xFrom, b.get(i).xFrom, 0f);
                assertEquals(a.get(i).yMs, b.get(i).yMs);
            }
            assertTrue(a.get(0).xFrom != c.get(0).xFrom || a.get(0).yMs != c.get(0).yMs);
        }
    }

    @Test
    public void firefliesWanderInsideTheScreen() {
        for (long seed = 0; seed < 50; seed++) {
            for (ParticleMotion.Spec s : make(ParticleMotion.KIND_FIREFLY, seed)) {
                float reach = SIZE * s.scale;
                assertTrue(s.xFrom >= 0 && s.xTo + reach <= W);
                assertTrue(s.yFrom >= 0 && s.yTo + reach <= H);
                assertTrue(s.xFrom < s.xTo && s.yFrom < s.yTo);
                assertFalse(s.yRestarts);
            }
        }
    }

    @Test
    public void firefliesAreSlowAndPulse() {
        for (ParticleMotion.Spec s : make(ParticleMotion.KIND_FIREFLY, 3)) {
            assertTrue(s.xMs >= 7000 && s.yMs >= 9000);
            assertTrue(s.alphaMs >= 1400 && s.alphaLow < s.alphaHigh);
            assertTrue(s.alphaLow >= 0f && s.alphaHigh <= 1f);
            assertEquals(0, s.rotationMs);
        }
    }

    @Test
    public void petalsFallFromAboveToBelowAndRespawn() {
        for (long seed = 0; seed < 50; seed++) {
            for (ParticleMotion.Spec s : make(ParticleMotion.KIND_PETAL, seed)) {
                assertTrue(s.yRestarts);
                assertTrue("starts above the top edge", s.yFrom + SIZE * s.scale < 0);
                assertTrue("ends below the bottom edge", s.yTo >= H);
                assertTrue("sways inside the width", s.xFrom >= 0 && s.xTo + SIZE * s.scale <= W);
                assertTrue(s.yMs >= 16000);
                assertTrue(s.rotationMs > 0 && s.rotationFrom != s.rotationTo);
                assertEquals(s.alphaLow, s.alphaHigh, 0f);
            }
        }
    }

    @Test
    public void everyParticleStartsAtItsOwnPointOfTheLoop() {
        for (int kind : new int[]{ParticleMotion.KIND_FIREFLY, ParticleMotion.KIND_PETAL}) {
            float first = -1;
            boolean differs = false;
            for (ParticleMotion.Spec s : make(kind, 5)) {
                assertTrue(s.phase >= 0f && s.phase < 1f);
                if (first < 0) first = s.phase;
                else if (s.phase != first) differs = true;
            }
            assertTrue(differs);
        }
    }
}
