package com.gathin.tvhome;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

/**
 * The launcher's settings: the values in res/values/config.xml, with an optional overlay read from
 * {@code config.json} in the app's external files directory
 * ({@code /sdcard/Android/data/com.gathin.tvhome/files/}). Every key in the JSON is optional and an
 * unknown or badly typed key is ignored, so a theme can change the dock and the look without a rebuild:
 * {@code tools/theme.sh themes/<name>} pushes a theme folder there and restarts Home.
 *
 * Read once in {@link MainActivity#onCreate}. Nothing here touches the network; the only network use in the
 * launcher is the optional weather, see {@link WeatherSource}.
 */
final class Config {
    private static final String TAG = "TvHome.Config";
    static final String FILE_NAME = "config.json";
    static final String WALLPAPER_NAME = "wallpaper.jpg";
    /** Optional daytime picture; wallpaper.jpg is then the night one. */
    static final String WALLPAPER_DAY_NAME = "wallpaper_day.jpg";

    final List<String> dockApps;
    final List<String> hiddenApps;
    final String liveTvPackage;
    final String liveTvAction;
    final String sourcePanelService;
    final String sourcePanelAction;
    final String inputAction;
    final int columns;
    final int rowsBelowDock;
    final boolean shamsiDate;
    final boolean moduleCards;
    /** Calmer motion for weak GPUs (config.xml, reduced_motion): stiff spring, short fades, no replay. */
    final boolean reducedMotion;
    final String vpnApp;
    final boolean showHomeSettingsShortcut;
    /** Background colour behind everything, #RRGGBB; black unless a theme says otherwise. */
    final int backgroundColor;
    /** Full-screen picture behind the launcher, or null for the plain colour. With a day picture this is the night one. */
    final File wallpaper;
    /** Daytime picture, only when {@link #wallpaper} exists too; null otherwise. */
    final File wallpaperDay;
    /** Start of day and of night, in minutes since midnight. */
    final int dayStartMinute;
    final int nightStartMinute;
    /** TV input id for the quick pill before Inputs, and its text; empty when unused (label: the input's own). */
    final String quickInput;
    final String quickInputLabel;
    /** Now playing card above the dock (needs notification access for the app). */
    final boolean nowPlaying;
    /** Fireflies or sakura petals over the wallpaper; off by default. */
    final boolean ambientParticles;
    /** Name added to the greeting; empty for the plain greeting. */
    final String greetingName;
    /** Place for the weather after the greeting, as the text from config (see {@link Weather#coordinate}); empty: no weather, no network. */
    final String weatherLat;
    final String weatherLon;
    /** "celsius" (default) or "fahrenheit". */
    final String weatherUnits;
    /** Whether the overlay file was present and parsed. */
    final boolean overlayApplied;

    private Config(List<String> dockApps, List<String> hiddenApps, String liveTvPackage, String liveTvAction,
                   String sourcePanelService, String sourcePanelAction, String inputAction,
                   int columns, int rowsBelowDock,
                   boolean shamsiDate, boolean moduleCards, boolean reducedMotion, String vpnApp,
                   boolean showHomeSettingsShortcut, int backgroundColor,
                   File wallpaper, File wallpaperDay, int dayStartMinute, int nightStartMinute,
                   String quickInput, String quickInputLabel, boolean nowPlaying, boolean ambientParticles,
                   String greetingName, String weatherLat, String weatherLon, String weatherUnits,
                   boolean overlayApplied) {
        this.dockApps = Collections.unmodifiableList(dockApps);
        this.hiddenApps = Collections.unmodifiableList(hiddenApps);
        this.liveTvPackage = liveTvPackage;
        this.liveTvAction = liveTvAction;
        this.sourcePanelService = sourcePanelService;
        this.sourcePanelAction = sourcePanelAction;
        this.inputAction = inputAction;
        this.columns = columns;
        this.rowsBelowDock = rowsBelowDock;
        this.shamsiDate = shamsiDate;
        this.moduleCards = moduleCards;
        this.reducedMotion = reducedMotion;
        this.vpnApp = vpnApp;
        this.showHomeSettingsShortcut = showHomeSettingsShortcut;
        this.backgroundColor = backgroundColor;
        this.wallpaper = wallpaper;
        this.wallpaperDay = wallpaperDay;
        this.dayStartMinute = dayStartMinute;
        this.nightStartMinute = nightStartMinute;
        this.quickInput = quickInput;
        this.quickInputLabel = quickInputLabel;
        this.nowPlaying = nowPlaying;
        this.ambientParticles = ambientParticles;
        this.greetingName = greetingName;
        this.weatherLat = weatherLat;
        this.weatherLon = weatherLon;
        this.weatherUnits = weatherUnits;
        this.overlayApplied = overlayApplied;
    }

    /** The folder the theme tools push into; null when external storage is unavailable. */
    static File dir(Context ctx) {
        return ctx.getExternalFilesDir(null);
    }

    static Config load(Context ctx) {
        Resources res = ctx.getResources();
        List<String> dock = new ArrayList<>(Arrays.asList(res.getStringArray(R.array.dock_apps)));
        List<String> hidden = new ArrayList<>(Arrays.asList(res.getStringArray(R.array.hidden_apps)));
        String liveTv = res.getString(R.string.live_tv_package).trim();
        String liveTvAction = res.getString(R.string.live_tv_action).trim();
        String panelService = res.getString(R.string.source_panel_service).trim();
        String panelAction = res.getString(R.string.source_panel_action).trim();
        String inputAction = res.getString(R.string.input_action).trim();
        int columns = res.getInteger(R.integer.columns);
        int rowsBelow = res.getInteger(R.integer.rows_below_dock);
        boolean shamsi = res.getBoolean(R.bool.shamsi_date);
        boolean moduleCards = res.getBoolean(R.bool.module_cards);
        boolean reducedMotion = res.getBoolean(R.bool.reduced_motion);
        String vpn = res.getString(R.string.vpn_app).trim();
        boolean homeSettings = res.getBoolean(R.bool.show_home_settings_shortcut);
        String dayStart = res.getString(R.string.day_start).trim();
        String nightStart = res.getString(R.string.night_start).trim();
        String quickInput = res.getString(R.string.quick_input).trim();
        String quickInputLabel = res.getString(R.string.quick_input_label).trim();
        boolean nowPlaying = res.getBoolean(R.bool.now_playing);
        boolean particles = res.getBoolean(R.bool.ambient_particles);
        String greetingName = res.getString(R.string.greeting_name).trim();
        String weatherLat = res.getString(R.string.weather_lat).trim();
        String weatherLon = res.getString(R.string.weather_lon).trim();
        String weatherUnits = res.getString(R.string.weather_units).trim();
        int background = 0xFF000000;
        boolean applied = false;

        File dir = dir(ctx);
        File file = dir != null ? new File(dir, FILE_NAME) : null;
        JSONObject json = read(file);
        if (json != null) {
            applied = true;
            dock = stringList(json, "dock_apps", dock);
            hidden = stringList(json, "hidden_apps", hidden);
            liveTv = string(json, "live_tv_package", liveTv);
            liveTvAction = string(json, "live_tv_action", liveTvAction);
            panelService = string(json, "source_panel_service", panelService);
            panelAction = string(json, "source_panel_action", panelAction);
            inputAction = string(json, "input_action", inputAction);
            columns = integer(json, "columns", columns, 1, 8);
            rowsBelow = integer(json, "rows_below_dock", rowsBelow, 0, 3);
            shamsi = bool(json, "shamsi_date", shamsi);
            moduleCards = bool(json, "module_cards", moduleCards);
            reducedMotion = bool(json, "reduced_motion", reducedMotion);
            vpn = string(json, "vpn_app", vpn);
            homeSettings = bool(json, "show_home_settings_shortcut", homeSettings);
            background = color(json, "background_color", background);
            dayStart = string(json, "day_start", dayStart);
            nightStart = string(json, "night_start", nightStart);
            quickInput = string(json, "quick_input", quickInput);
            quickInputLabel = string(json, "quick_input_label", quickInputLabel);
            nowPlaying = bool(json, "now_playing", nowPlaying);
            particles = bool(json, "ambient_particles", particles);
            greetingName = string(json, "greeting_name", greetingName);
            weatherLat = stringOrNumber(json, "weather_lat", weatherLat);
            weatherLon = stringOrNumber(json, "weather_lon", weatherLon);
            weatherUnits = string(json, "weather_units", weatherUnits);
        }
        File wallpaper = pictureIn(dir, WALLPAPER_NAME);
        File wallpaperDay = pictureIn(dir, WALLPAPER_DAY_NAME);
        // A theme can also switch the wallpaper off while the files are still there.
        if (json != null && json.has("wallpaper") && !bool(json, "wallpaper", true)) {
            wallpaper = null;
            wallpaperDay = null;
        }
        // A day picture alone is just the picture; it only pairs with a night one.
        if (wallpaper == null) {
            wallpaper = wallpaperDay;
            wallpaperDay = null;
        }

        return new Config(dock, hidden, liveTv, liveTvAction, panelService, panelAction, inputAction,
                Math.max(1, columns), Math.max(0, rowsBelow), shamsi, moduleCards, reducedMotion,
                vpn, homeSettings, background, wallpaper, wallpaperDay,
                DayNight.parseMinutes(dayStart, DayNight.DEFAULT_DAY_START),
                DayNight.parseMinutes(nightStart, DayNight.DEFAULT_NIGHT_START),
                quickInput, quickInputLabel, nowPlaying, particles, greetingName,
                weatherLat, weatherLon, weatherUnits, applied);
    }

    /** The picture to show at this time of day (minutes since midnight); null for the plain colour. */
    File wallpaperAt(int minuteOfDay) {
        if (wallpaperDay != null && DayNight.isDaytime(minuteOfDay, dayStartMinute, nightStartMinute)) return wallpaperDay;
        return wallpaper;
    }

    /** Minutes since midnight on the TV's clock now. */
    static int minuteOfDay() {
        Calendar now = Calendar.getInstance();
        return now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
    }

    private static File pictureIn(File dir, String name) {
        File f = dir != null ? new File(dir, name) : null;
        return f != null && f.isFile() && f.length() > 0 ? f : null;
    }

    /**
     * Writes the dock into config.json, keeping every other key the file has; the file is created when
     * missing. A file that exists but cannot be read back (invalid JSON, over 64 KB) is left alone and
     * the save fails, since rewriting it would drop what we could not parse. The new content goes to a
     * temporary file first and replaces config.json in one rename. False when nothing was written.
     */
    static boolean saveDockApps(Context ctx, List<String> dock) {
        File dir = dir(ctx);
        if (dir == null) return false;
        File file = new File(dir, FILE_NAME);
        File tmp = new File(dir, FILE_NAME + ".tmp");
        try {
            JSONObject json = new JSONObject();
            if (file.exists()) {
                json = read(file);
                if (json == null) return false;
            }
            json.put("dock_apps", new JSONArray(dock));
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(json.toString(2).getBytes(StandardCharsets.UTF_8));
                out.getFD().sync();
            }
            try {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (Exception e) {
            Log.w(TAG, "cannot write " + file + ": " + e);
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return false;
        }
    }

    /** Reads and parses the overlay; any problem means "no overlay", never a crash on the home screen. */
    private static JSONObject read(File file) {
        if (file == null || !file.isFile()) return null;
        if (file.length() > 64 * 1024) {
            Log.w(TAG, "ignoring " + file + ": larger than 64 KB");
            return null;
        }
        try (InputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[(int) file.length()];
            int n = 0, r;
            while (n < buf.length && (r = in.read(buf, n, buf.length - n)) > 0) n += r;
            String text = new String(buf, 0, n, StandardCharsets.UTF_8);
            if (text.startsWith("﻿")) text = text.substring(1);   // Windows editors add a BOM
            return new JSONObject(text);
        } catch (Exception e) {
            Log.w(TAG, "ignoring " + file + ": " + e.getMessage());
            return null;
        }
    }

    private static String string(JSONObject o, String key, String fallback) {
        Object v = o.opt(key);
        return v instanceof String ? ((String) v).trim() : fallback;
    }

    /** A string, or a bare number written as one (coordinates are easy to type without quotes). */
    private static String stringOrNumber(JSONObject o, String key, String fallback) {
        Object v = o.opt(key);
        if (v instanceof Number) return String.valueOf(((Number) v).doubleValue());
        return v instanceof String ? ((String) v).trim() : fallback;
    }

    private static boolean bool(JSONObject o, String key, boolean fallback) {
        Object v = o.opt(key);
        return v instanceof Boolean ? (Boolean) v : fallback;
    }

    private static int integer(JSONObject o, String key, int fallback, int min, int max) {
        Object v = o.opt(key);
        if (!(v instanceof Number)) return fallback;
        int i = ((Number) v).intValue();
        return i < min || i > max ? fallback : i;
    }

    private static int color(JSONObject o, String key, int fallback) {
        Object v = o.opt(key);
        if (!(v instanceof String)) return fallback;
        try {
            int c = Color.parseColor(((String) v).trim());
            return c | 0xFF000000;   // always opaque: the window behind is black anyway
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static List<String> stringList(JSONObject o, String key, List<String> fallback) {
        Object v = o.opt(key);
        if (!(v instanceof JSONArray)) return fallback;
        JSONArray a = (JSONArray) v;
        List<String> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            Object item = a.opt(i);
            if (item instanceof String && !((String) item).trim().isEmpty()) out.add(((String) item).trim());
        }
        return out;
    }
}
