package com.gathin.tvhome;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The current-weather reading shown after the greeting, and the pure rules around it: building the
 * Open-Meteo request, pulling the numbers out of its answer, mapping WMO weather codes to a small set of
 * conditions, formatting the temperature and deciding when a cached reading is due for a refresh or too old
 * to show. No Android types, so it runs in plain JVM tests; {@link WeatherSource} does the networking.
 */
final class Weather {
    static final int UNKNOWN = -1;
    static final int CLEAR = 0;
    static final int PARTLY_CLOUDY = 1;
    static final int CLOUDY = 2;
    static final int FOG = 3;
    static final int DRIZZLE = 4;
    static final int RAIN = 5;
    static final int SNOW = 6;
    static final int STORM = 7;

    /** A cached reading older than this is fetched again when Home comes to the front, and every this often while it stays. */
    static final long REFRESH_MS = 30 * 60_000L;
    /** Older than this is not shown at all: a stale temperature is worse than none. */
    static final long MAX_AGE_MS = 3 * 60 * 60_000L;
    /** Least time between two attempts, so a failing network is never retried in a tight loop. */
    static final long RETRY_MS = 2 * 60_000L;

    private static final Pattern CURRENT = Pattern.compile("\"current\"\\s*:\\s*\\{([^{}]*)\\}");
    private static final Pattern TEMPERATURE = field("temperature_2m");
    private static final Pattern CODE = field("weather_code");
    private static final Pattern IS_DAY = field("is_day");

    private Weather() {
    }

    /** Temperature in the unit asked for, WMO weather code, and whether the sun is up at the location. */
    static final class Reading {
        final double temperature;
        final int code;
        final boolean isDay;

        Reading(double temperature, int code, boolean isDay) {
            this.temperature = temperature;
            this.code = code;
            this.isDay = isDay;
        }
    }

    // ---- request ---------------------------------------------------------------------------------

    /**
     * A latitude or longitude from config ("41.38", a comma as decimal separator is fine) as a number, or NaN
     * when it is empty, not a number or outside -limit..limit (90 for latitude, 180 for longitude).
     */
    static double coordinate(String text, double limit) {
        if (text == null) return Double.NaN;
        String t = text.trim().replace(',', '.');
        if (t.isEmpty()) return Double.NaN;
        try {
            double v = Double.parseDouble(t);
            return Double.isNaN(v) || Double.isInfinite(v) || Math.abs(v) > limit ? Double.NaN : v;
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    /** True for "fahrenheit" in any case; anything else is Celsius. */
    static boolean isFahrenheit(String units) {
        return units != null && units.trim().equalsIgnoreCase("fahrenheit");
    }

    /** The Open-Meteo request for the current conditions. The coordinates are reformatted, never pasted from config. */
    static String url(double lat, double lon, boolean fahrenheit) {
        return String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                        + "&current=temperature_2m,weather_code,is_day&temperature_unit=%s&timezone=auto",
                lat, lon, fahrenheit ? "fahrenheit" : "celsius");
    }

    /** What a cached reading belongs to; a different place or unit in config makes the cache not apply. */
    static String cacheKey(double lat, double lon, boolean fahrenheit) {
        return String.format(Locale.US, "%.4f,%.4f,%s", lat, lon, fahrenheit ? "f" : "c");
    }

    // ---- answer ----------------------------------------------------------------------------------

    /**
     * The numbers in the {@code current} object of an Open-Meteo answer, or null when the object or any of the
     * three fields is missing or not a number. Looks only inside {@code current} (not {@code current_units},
     * which has the same keys with text values). org.json is not on the plain JVM test classpath, hence the
     * small extractor instead of a parser.
     */
    static Reading parse(String json) {
        if (json == null) return null;
        Matcher block = CURRENT.matcher(json);
        if (!block.find()) return null;
        String body = block.group(1);
        Double temperature = number(TEMPERATURE, body);
        Double code = number(CODE, body);
        Double day = number(IS_DAY, body);
        if (temperature == null || code == null || day == null) return null;
        return new Reading(temperature, (int) Math.round(code), day != 0);
    }

    private static Pattern field(String key) {
        return Pattern.compile("\"" + key + "\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)");
    }

    private static Double number(Pattern pattern, String body) {
        Matcher m = pattern.matcher(body);
        if (!m.find()) return null;
        try {
            double v = Double.parseDouble(m.group(1));
            return Double.isNaN(v) || Double.isInfinite(v) ? null : v;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ---- presentation ----------------------------------------------------------------------------

    /** The WMO weather interpretation code as one of the conditions above, {@link #UNKNOWN} for a code we have no word for. */
    static int condition(int code) {
        switch (code) {
            case 0: case 1: return CLEAR;
            case 2: return PARTLY_CLOUDY;
            case 3: return CLOUDY;
            case 45: case 48: return FOG;
            case 51: case 53: case 55: case 56: case 57: return DRIZZLE;
            case 61: case 63: case 65: case 66: case 67: case 80: case 81: case 82: return RAIN;
            case 71: case 73: case 75: case 77: case 85: case 86: return SNOW;
            case 95: case 96: case 99: return STORM;
            default: return UNKNOWN;
        }
    }

    /** "18°": rounded half up (-2.5 is -2), and never "-0°". */
    static String format(double temperature) {
        long rounded = (long) Math.floor(temperature + 0.5);
        return rounded + "°";
    }

    // ---- freshness -------------------------------------------------------------------------------

    /** True when there is no reading, it is older than {@link #REFRESH_MS}, or the clock went back past it. */
    static boolean needsRefresh(long fetchedAt, long now) {
        if (fetchedAt <= 0) return true;
        long age = now - fetchedAt;
        return age < 0 || age >= REFRESH_MS;
    }

    /** True when the reading is recent enough to show: at most {@link #MAX_AGE_MS} old and not from the future. */
    static boolean isShowable(long fetchedAt, long now) {
        if (fetchedAt <= 0) return false;
        long age = now - fetchedAt;
        return age >= 0 && age <= MAX_AGE_MS;
    }

    /** True when enough time has passed since the last attempt (success or not) to try again. */
    static boolean mayAttempt(long lastAttemptElapsed, long nowElapsed) {
        return lastAttemptElapsed <= 0 || nowElapsed - lastAttemptElapsed >= RETRY_MS;
    }
}
