package com.gathin.tvhome;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which time of day it is for a theme that ships a day and a night wallpaper (config.json day_start and
 * night_start, "HH:mm"). Pure functions over minutes since midnight, so they run in plain JVM tests.
 */
final class DayNight {
    static final int DEFAULT_DAY_START = 7 * 60;
    static final int DEFAULT_NIGHT_START = 19 * 60;
    private static final Pattern HH_MM = Pattern.compile("(\\d{1,2}):(\\d{2})");

    private DayNight() {
    }

    /** "HH:mm" as minutes since midnight, or the fallback when the text is missing or not a valid time. */
    static int parseMinutes(String text, int fallback) {
        if (text == null) return fallback;
        Matcher m = HH_MM.matcher(text.trim());
        if (!m.matches()) return fallback;
        int hour = Integer.parseInt(m.group(1));
        int minute = Integer.parseInt(m.group(2));
        return hour > 23 || minute > 59 ? fallback : hour * 60 + minute;
    }

    /**
     * True from dayStart up to (not including) nightStart. A window that wraps midnight works too
     * (dayStart 20:00, nightStart 06:00 is day from 20:00 through the night). Equal times mean no night.
     */
    static boolean isDaytime(int minuteOfDay, int dayStartMin, int nightStartMin) {
        if (dayStartMin == nightStartMin) return true;
        if (dayStartMin < nightStartMin) return minuteOfDay >= dayStartMin && minuteOfDay < nightStartMin;
        return minuteOfDay >= dayStartMin || minuteOfDay < nightStartMin;
    }
}
