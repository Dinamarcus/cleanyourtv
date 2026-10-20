package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Plain JVM tests for the day and night window and its "HH:mm" parser. */
public class DayNightTest {

    private static final int DAY = 7 * 60, NIGHT = 19 * 60;

    // ---- isDaytime -------------------------------------------------------------------------------

    @Test
    public void dayRunsFromDayStartUpToNightStart() {
        assertFalse(DayNight.isDaytime(6 * 60 + 59, DAY, NIGHT));
        assertTrue(DayNight.isDaytime(7 * 60, DAY, NIGHT));
        assertTrue(DayNight.isDaytime(12 * 60, DAY, NIGHT));
        assertTrue(DayNight.isDaytime(18 * 60 + 59, DAY, NIGHT));
        assertFalse(DayNight.isDaytime(19 * 60, DAY, NIGHT));
        assertFalse(DayNight.isDaytime(23 * 60 + 59, DAY, NIGHT));
        assertFalse(DayNight.isDaytime(0, DAY, NIGHT));
    }

    @Test
    public void aDayWindowThatWrapsMidnightWorks() {
        // A night owl: day starts at 20:00 and night at 06:00.
        int dayStart = 20 * 60, nightStart = 6 * 60;
        assertTrue(DayNight.isDaytime(20 * 60, dayStart, nightStart));
        assertTrue(DayNight.isDaytime(23 * 60 + 59, dayStart, nightStart));
        assertTrue(DayNight.isDaytime(0, dayStart, nightStart));
        assertTrue(DayNight.isDaytime(5 * 60 + 59, dayStart, nightStart));
        assertFalse(DayNight.isDaytime(6 * 60, dayStart, nightStart));
        assertFalse(DayNight.isDaytime(12 * 60, dayStart, nightStart));
        assertFalse(DayNight.isDaytime(19 * 60 + 59, dayStart, nightStart));
    }

    @Test
    public void dayStartingAtMidnightCoversTheMorning() {
        assertTrue(DayNight.isDaytime(0, 0, NIGHT));
        assertTrue(DayNight.isDaytime(NIGHT - 1, 0, NIGHT));
        assertFalse(DayNight.isDaytime(NIGHT, 0, NIGHT));
    }

    @Test
    public void equalTimesMeanItIsAlwaysDay() {
        assertTrue(DayNight.isDaytime(0, DAY, DAY));
        assertTrue(DayNight.isDaytime(DAY, DAY, DAY));
        assertTrue(DayNight.isDaytime(23 * 60 + 59, DAY, DAY));
    }

    // ---- parseMinutes ----------------------------------------------------------------------------

    @Test
    public void parsesHoursAndMinutes() {
        assertEquals(0, DayNight.parseMinutes("00:00", -1));
        assertEquals(7 * 60, DayNight.parseMinutes("07:00", -1));
        assertEquals(7 * 60, DayNight.parseMinutes("7:00", -1));
        assertEquals(19 * 60 + 30, DayNight.parseMinutes("19:30", -1));
        assertEquals(23 * 60 + 59, DayNight.parseMinutes("23:59", -1));
        assertEquals(6 * 60 + 5, DayNight.parseMinutes("  06:05  ", -1));
    }

    @Test
    public void badValuesFallBack() {
        assertEquals(-1, DayNight.parseMinutes(null, -1));
        assertEquals(-1, DayNight.parseMinutes("", -1));
        assertEquals(-1, DayNight.parseMinutes("noon", -1));
        assertEquals(-1, DayNight.parseMinutes("24:00", -1));
        assertEquals(-1, DayNight.parseMinutes("12:60", -1));
        assertEquals(-1, DayNight.parseMinutes("12:5", -1));
        assertEquals(-1, DayNight.parseMinutes("-1:00", -1));
        assertEquals(-1, DayNight.parseMinutes("12", -1));
        assertEquals(-1, DayNight.parseMinutes("12:00:00", -1));
        assertEquals(-1, DayNight.parseMinutes("1200", -1));
        assertEquals(-1, DayNight.parseMinutes("99999999999:00", -1));
    }

    @Test
    public void defaultsAreSevenAndNineteen() {
        assertEquals(DAY, DayNight.DEFAULT_DAY_START);
        assertEquals(NIGHT, DayNight.DEFAULT_NIGHT_START);
    }
}
