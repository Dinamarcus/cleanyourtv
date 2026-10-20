package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Plain JVM tests for the weather rules: request, answer parsing, code mapping, formatting and freshness. */
public class WeatherTest {

    private static final long MIN = 60_000L;

    private static final String ANSWER = "{\"latitude\":41.375,\"longitude\":2.125,\"generationtime_ms\":0.05,"
            + "\"utc_offset_seconds\":7200,\"timezone\":\"Europe/Madrid\",\"elevation\":18.0,"
            + "\"current_units\":{\"time\":\"iso8601\",\"interval\":\"seconds\",\"temperature_2m\":\"°C\","
            + "\"weather_code\":\"wmo code\",\"is_day\":\"\"},"
            + "\"current\":{\"time\":\"2026-10-19T20:15\",\"interval\":900,\"temperature_2m\":18.4,"
            + "\"weather_code\":3,\"is_day\":0}}";

    // ---- parse -----------------------------------------------------------------------------------

    @Test
    public void parsesTheCurrentObjectOfARealAnswer() {
        Weather.Reading r = Weather.parse(ANSWER);
        assertNotNull(r);
        assertEquals(18.4, r.temperature, 0.0001);
        assertEquals(3, r.code);
        assertFalse(r.isDay);
    }

    @Test
    public void readsNegativeTemperaturesAndAnyFieldOrder() {
        Weather.Reading r = Weather.parse("{\"current\": { \"is_day\" : 1, \"weather_code\":71, \"temperature_2m\" : -7.25 }}");
        assertNotNull(r);
        assertEquals(-7.25, r.temperature, 0.0001);
        assertEquals(71, r.code);
        assertTrue(r.isDay);
    }

    @Test
    public void unitsBlockAloneIsNotAReading() {
        // current_units has the same keys with text values; it must never be mistaken for current.
        assertNull(Weather.parse("{\"current_units\":{\"temperature_2m\":\"°C\",\"weather_code\":\"wmo code\",\"is_day\":\"\"}}"));
    }

    @Test
    public void aMissingOrNonNumericFieldGivesNoReading() {
        assertNull(Weather.parse("{\"current\":{\"weather_code\":3,\"is_day\":1}}"));
        assertNull(Weather.parse("{\"current\":{\"temperature_2m\":null,\"weather_code\":3,\"is_day\":1}}"));
        assertNull(Weather.parse("{\"current\":{\"temperature_2m\":\"warm\",\"weather_code\":3,\"is_day\":1}}"));
        assertNull(Weather.parse("{\"current\":{\"temperature_2m\":20,\"is_day\":1}}"));
        assertNull(Weather.parse("{\"current\":{\"temperature_2m\":20,\"weather_code\":3}}"));
    }

    @Test
    public void garbageGivesNoReading() {
        assertNull(Weather.parse(null));
        assertNull(Weather.parse(""));
        assertNull(Weather.parse("<html>502 Bad Gateway</html>"));
        assertNull(Weather.parse("{\"error\":true,\"reason\":\"Latitude must be in range of -90 to 90\"}"));
        assertNull(Weather.parse("{\"current\":{\"temperature_2m\":18.4,"));   // cut off mid-object
    }

    // ---- request ---------------------------------------------------------------------------------

    @Test
    public void coordinatesParseTrimAndAcceptACommaDecimal() {
        assertEquals(41.3874, Weather.coordinate(" 41.3874 ", 90), 0.0);
        assertEquals(-2.5, Weather.coordinate("-2,5", 180), 0.0);
        assertEquals(0.0, Weather.coordinate("0", 90), 0.0);
    }

    @Test
    public void badCoordinatesAreNaN() {
        assertTrue(Double.isNaN(Weather.coordinate(null, 90)));
        assertTrue(Double.isNaN(Weather.coordinate("", 90)));
        assertTrue(Double.isNaN(Weather.coordinate("   ", 90)));
        assertTrue(Double.isNaN(Weather.coordinate("north", 90)));
        assertTrue(Double.isNaN(Weather.coordinate("NaN", 90)));
        assertTrue(Double.isNaN(Weather.coordinate("Infinity", 90)));
        assertTrue(Double.isNaN(Weather.coordinate("91", 90)));
        assertTrue(Double.isNaN(Weather.coordinate("-181", 180)));
        assertTrue(Double.isNaN(Weather.coordinate("1&x=2", 90)));
    }

    @Test
    public void urlCarriesTheCoordinatesAndUnitAndNothingElseFromConfig() {
        assertEquals("https://api.open-meteo.com/v1/forecast?latitude=41.3874&longitude=2.1686"
                        + "&current=temperature_2m,weather_code,is_day&temperature_unit=celsius&timezone=auto",
                Weather.url(41.3874, 2.1686, false));
        assertEquals("https://api.open-meteo.com/v1/forecast?latitude=-33.8700&longitude=151.2100"
                        + "&current=temperature_2m,weather_code,is_day&temperature_unit=fahrenheit&timezone=auto",
                Weather.url(-33.87, 151.21, true));
    }

    @Test
    public void unitsAreCelsiusUnlessFahrenheitIsSpelledOut() {
        assertTrue(Weather.isFahrenheit("fahrenheit"));
        assertTrue(Weather.isFahrenheit(" Fahrenheit "));
        assertFalse(Weather.isFahrenheit("celsius"));
        assertFalse(Weather.isFahrenheit("kelvin"));
        assertFalse(Weather.isFahrenheit(""));
        assertFalse(Weather.isFahrenheit(null));
    }

    @Test
    public void cacheKeyChangesWithPlaceAndUnit() {
        String base = Weather.cacheKey(41.3874, 2.1686, false);
        assertEquals(base, Weather.cacheKey(41.3874, 2.1686, false));
        assertFalse(base.equals(Weather.cacheKey(41.3874, 2.1686, true)));
        assertFalse(base.equals(Weather.cacheKey(40.4168, 2.1686, false)));
    }

    // ---- conditions ------------------------------------------------------------------------------

    @Test
    public void wmoCodesMapToTheSmallSetOfConditions() {
        assertEquals(Weather.CLEAR, Weather.condition(0));
        assertEquals(Weather.CLEAR, Weather.condition(1));
        assertEquals(Weather.PARTLY_CLOUDY, Weather.condition(2));
        assertEquals(Weather.CLOUDY, Weather.condition(3));
        assertEquals(Weather.FOG, Weather.condition(45));
        assertEquals(Weather.FOG, Weather.condition(48));
        for (int code : new int[]{51, 53, 55, 56, 57}) assertEquals("code " + code, Weather.DRIZZLE, Weather.condition(code));
        for (int code : new int[]{61, 63, 65, 66, 67, 80, 81, 82}) assertEquals("code " + code, Weather.RAIN, Weather.condition(code));
        for (int code : new int[]{71, 73, 75, 77, 85, 86}) assertEquals("code " + code, Weather.SNOW, Weather.condition(code));
        for (int code : new int[]{95, 96, 99}) assertEquals("code " + code, Weather.STORM, Weather.condition(code));
    }

    @Test
    public void aCodeWeHaveNoWordForIsUnknown() {
        assertEquals(Weather.UNKNOWN, Weather.condition(4));
        assertEquals(Weather.UNKNOWN, Weather.condition(100));
        assertEquals(Weather.UNKNOWN, Weather.condition(-1));
    }

    // ---- format ----------------------------------------------------------------------------------

    @Test
    public void temperatureRoundsHalfUp() {
        assertEquals("18°", Weather.format(18.4));
        assertEquals("19°", Weather.format(18.5));
        assertEquals("19°", Weather.format(18.6));
        assertEquals("-2°", Weather.format(-2.5));
        assertEquals("-3°", Weather.format(-2.6));
        assertEquals("-2°", Weather.format(-2.4));
        assertEquals("0°", Weather.format(0.0));
    }

    @Test
    public void temperatureNeverShowsNegativeZero() {
        assertEquals("0°", Weather.format(-0.4));
        assertEquals("0°", Weather.format(-0.0));
        assertEquals("-1°", Weather.format(-0.6));
    }

    // ---- freshness -------------------------------------------------------------------------------

    @Test
    public void aReadingIsRefreshedAtThirtyMinutes() {
        long at = 1_000_000_000L;
        assertFalse(Weather.needsRefresh(at, at));
        assertFalse(Weather.needsRefresh(at, at + 29 * MIN + 59_000));
        assertTrue(Weather.needsRefresh(at, at + 30 * MIN));
        assertTrue(Weather.needsRefresh(at, at + 5 * 60 * MIN));
    }

    @Test
    public void noReadingAndAClockThatWentBackNeedARefresh() {
        assertTrue(Weather.needsRefresh(0, 1_000_000_000L));
        assertTrue(Weather.needsRefresh(2_000_000_000L, 1_000_000_000L));
    }

    @Test
    public void aReadingIsShownUpToThreeHours() {
        long at = 1_000_000_000L;
        assertTrue(Weather.isShowable(at, at));
        assertTrue(Weather.isShowable(at, at + 90 * MIN));
        assertTrue(Weather.isShowable(at, at + 180 * MIN));
        assertFalse(Weather.isShowable(at, at + 180 * MIN + 1));
    }

    @Test
    public void neverFetchedOrFromTheFutureIsNotShown() {
        assertFalse(Weather.isShowable(0, 1_000_000_000L));
        assertFalse(Weather.isShowable(2_000_000_000L, 1_000_000_000L));
    }

    @Test
    public void attemptsAreSpacedByTwoMinutes() {
        assertTrue(Weather.mayAttempt(0, 5_000));
        assertFalse(Weather.mayAttempt(100_000, 100_000 + 119_999));
        assertTrue(Weather.mayAttempt(100_000, 100_000 + 120_000));
    }
}
