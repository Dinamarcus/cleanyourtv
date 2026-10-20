package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The only network use in the launcher: one small HTTPS request to Open-Meteo (no key, no account) for the
 * current temperature at the configured place. It runs on a single background thread, and only when
 * {@link Config#weatherLat} and {@link Config#weatherLon} are set. The last good reading is kept in
 * SharedPreferences so it shows at once on the next start; every failure is silent and keeps what we have.
 * Used from the main thread only.
 */
final class WeatherSource {
    private static final String TAG = "TvHome.Weather";
    private static final String PREFS = "weather";
    private static final int TIMEOUT_MS = 5_000;
    private static final int MAX_BYTES = 16 * 1024;

    /** Called on the main thread with the reading to show, or null to hide the weather. */
    interface Listener {
        void onWeather(Weather.Reading reading);
    }

    private final SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final String url;
    private final String key;
    private final Listener listener;
    private ExecutorService executor;
    private boolean busy;
    private boolean closed;
    private long lastAttempt;
    private Weather.Reading reading;
    private long fetchedAt;

    private WeatherSource(Context ctx, String url, String key, Listener listener) {
        this.prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.url = url;
        this.key = key;
        this.listener = listener;
        load();
    }

    /** The source for this config, or null when no valid latitude and longitude are set (the feature is off). */
    static WeatherSource create(Context ctx, Config config, Listener listener) {
        double lat = Weather.coordinate(config.weatherLat, 90);
        double lon = Weather.coordinate(config.weatherLon, 180);
        if (Double.isNaN(lat) || Double.isNaN(lon)) return null;
        boolean fahrenheit = Weather.isFahrenheit(config.weatherUnits);
        return new WeatherSource(ctx.getApplicationContext(), Weather.url(lat, lon, fahrenheit),
                Weather.cacheKey(lat, lon, fahrenheit), listener);
    }

    /** The cached reading if it is recent enough to show, else null. */
    Weather.Reading current() {
        return reading != null && Weather.isShowable(fetchedAt, System.currentTimeMillis()) ? reading : null;
    }

    /**
     * Fetches in the background, then calls the listener. Without {@code force} it does nothing while the
     * cached reading is under 30 minutes old; either way it skips the request while one is running or the
     * last attempt was under two minutes ago.
     */
    void refresh(boolean force) {
        long now = System.currentTimeMillis();
        long elapsed = SystemClock.elapsedRealtime();
        if (closed || busy || !Weather.mayAttempt(lastAttempt, elapsed)) return;
        if (!force && !Weather.needsRefresh(fetchedAt, now)) return;
        busy = true;
        lastAttempt = elapsed;
        if (executor == null) {
            executor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "weather-fetch");
                t.setDaemon(true);
                return t;
            });
        }
        try {
            executor.execute(() -> {
                final Weather.Reading result = fetch();
                main.post(() -> done(result));
            });
        } catch (RuntimeException e) {
            busy = false;
        }
    }

    /** Stops the thread; later calls do nothing. */
    void close() {
        closed = true;
        if (executor != null) executor.shutdownNow();
        executor = null;
    }

    private void done(Weather.Reading result) {
        busy = false;
        if (closed) return;
        if (result != null) {
            reading = result;
            fetchedAt = System.currentTimeMillis();
            store();
        }
        listener.onWeather(current());
    }

    private Weather.Reading fetch() {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestProperty("User-Agent", "TvHome-Launcher (github.com/Gathin23/cleanyourtv)");
            conn.setRequestProperty("Accept", "application/json");
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) return null;
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            try (InputStream in = conn.getInputStream()) {
                byte[] buf = new byte[2048];
                int n;
                while ((n = in.read(buf)) > 0 && body.size() < MAX_BYTES) body.write(buf, 0, n);
            }
            return Weather.parse(new String(body.toByteArray(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            // Offline, DNS, timeout, TLS: silent by design. The class only; the message can carry the address.
            Log.w(TAG, "weather not updated: " + e.getClass().getSimpleName());
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private void load() {
        if (!key.equals(prefs.getString("key", null))) return;
        long at = prefs.getLong("at", 0);
        if (at <= 0) return;
        reading = new Weather.Reading(prefs.getFloat("temp", 0f), prefs.getInt("code", -1), prefs.getBoolean("day", true));
        fetchedAt = at;
    }

    private void store() {
        prefs.edit().putString("key", key).putLong("at", fetchedAt).putFloat("temp", (float) reading.temperature)
                .putInt("code", reading.code).putBoolean("day", reading.isDay).apply();
    }
}
