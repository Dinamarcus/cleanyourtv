# Themes

A theme is a folder with a `config.json` and, optionally, a `wallpaper.jpg` (and a `wallpaper_day.jpg`).
Pushing one changes the launcher's dock, layout and look on the TV without rebuilding the APK: the launcher reads
`config.json` as an overlay over its built-in `config.xml` the next time it starts.

```bash
TV_HOST=192.168.1.50 tools/theme.sh themes/aurora          # push a theme and restart Home
TV_HOST=192.168.1.50 tools/theme.sh --reset                # back to the built-in config.xml
powershell -ExecutionPolicy Bypass -File tools\theme.ps1 -TvHost 192.168.1.50 themes\aurora
```

| Theme | What it does |
|---|---|
| `default/` | The built-in look written out as JSON, as a starting point for your own |
| `aurora/` | A dark aurora wallpaper (teal and violet on navy, darker at the bottom) with a matching background colour |
| `six-columns/` | Six smaller tiles per row and one grid row visible under the dock |

## Keys

Every key is optional; a missing key keeps the value from `config.xml`, an unknown or wrongly typed key
is ignored, and a file that doesn't parse is ignored as a whole, so a theme can never break Home.

| Key | Type | Meaning |
|---|---|---|
| `dock_apps` | list of package names | The dock row, in order; missing apps are skipped |
| `hidden_apps` | list of package names | Never shown as tiles |
| `live_tv_package`, `live_tv_action` | string | The Live TV app and, if it has no leanback entry, the action to start it with |
| `source_panel_service`, `source_panel_action` | string | The TV's own source panel, for sets with no TvInputManager |
| `input_action` | string | Action sent with an HDMI/AV row's channel URI when nothing handles the standard `VIEW` (XGIMI: `com.xgimi.action.hdmiPlayer`) |
| `columns` | 1 to 8 | Tiles per row; tiles shrink to fit from 5 up |
| `rows_below_dock` | 0 to 3 | Grid rows left visible under the dock |
| `shamsi_date` | bool | Show the Solar Hijri date above the Gregorian one |
| `module_cards` | bool | Look for module cards and ask about them (`true`); `false` turns the feature off |
| `reduced_motion` | bool | Calmer motion for weak GPUs: stiff highlight spring, no tile overshoot, short fades, no entrance replay |
| `vpn_app` | string | App the network pill opens when a VPN is configured; also shows a red "VPN off" badge while no VPN is up |
| `show_home_settings_shortcut` | bool | The "Default home" tile, where the TV has a home picker |
| `background_color` | `#RRGGBB` | Colour behind everything; black by default |
| `wallpaper` | bool | Show `wallpaper.jpg` (and `wallpaper_day.jpg`) from the theme folder (`true`), or ignore them (`false`) |
| `day_start`, `night_start` | `"HH:mm"` | When the day picture starts (default `"07:00"`) and the night one (default `"19:00"`); a bad value keeps the default |
| `quick_input`, `quick_input_label` | string | A TV input id (as `dumpsys tv_input` lists it, matched exactly) that gets its own pill before Inputs, e.g. a camera input; the label defaults to the input's own. No pill when empty or not on the TV |
| `now_playing` | bool | A card above the dock, on the right, with the title, artist and cover of what is playing (default `true`); needs notification access, see below |
| `ambient_particles` | bool | Fireflies over the night picture and sakura petals over the day one (default `false`); only with a wallpaper and `reduced_motion` off |
| `weather_lat`, `weather_lon` | string (a bare number works too) | Place for the weather after the greeting, e.g. `"41.3874"` and `"2.1686"`; empty (the default) keeps the weather off and the launcher offline, see Weather below |
| `weather_units` | `"celsius"` \| `"fahrenheit"` | Unit for the temperature; default `"celsius"`, anything else is Celsius |
| `greeting_name` | string | A name for the greeting: "Good evening, Name" (translated); empty keeps the plain greeting |

## Wallpapers

`wallpaper.jpg` is drawn full screen behind the launcher, centre-cropped. Keep it dark, 1920×1080 and
under about 300 KB: the text, the hero name and the dock band are designed for black, and a 1 GB TV
decodes the picture into memory on every Home start. Pictures larger than the screen are downsampled.

A theme can also ship `wallpaper_day.jpg`: `wallpaper.jpg` is then the night picture. Home shows the one
for the time on the TV (`day_start` to `night_start` is day, which may wrap midnight) and fades to the
other at the change, within a minute, without a restart; the screensaver picks by the time it starts.
Without `wallpaper.jpg`, `wallpaper_day.jpg` is shown all day. The same size advice applies to both.

## Now playing

`now_playing` reads the active media sessions, which Android only allows with notification access. Home
ships an empty listener service for that and never reads a notification. Grant it once over adb:

```bash
adb shell cmd notification allow_listener com.gathin.tvhome/com.gathin.tvhome.MediaListenerService
```

Without it the card stays hidden and nothing else changes. The card shows while a session is playing and
fades out a few seconds after it pauses; the border takes the colour of the cover. It listens only while
Home is in front.

Under the text are Previous, Play/Pause and Next buttons (Previous and Next only when the app offers them). Press
Up from the dock to reach them, Left and Right to move between them, OK to press one, Down to return to the dock
and Up again for the top bar. The card stays up while one of them has focus, even if playback is paused.

## Weather

With `weather_lat` and `weather_lon` set, the greeting line gets the current weather after it: "Good evening,
Name · ☁ 18°", with an icon for clear, partly cloudy, cloudy, fog, drizzle, rain, snow or thunderstorm (a moon
at night where it fits) and a spoken description for screen readers. It is off unless both are set, and then
the launcher makes no network request at all.

This is the only network use in Home. It asks [Open-Meteo](https://open-meteo.com/) (no key or account) for the
temperature at those coordinates, which are sent to it with each request, over HTTPS in the background, and:

- polls every 30 minutes while Home is in front (and when it comes back to the front with an older value),
  never while another app or the screensaver is showing;
- keeps the last answer so it shows at once on the next start, and hides it once it is over 3 hours old;
- fails silently: no network, a bad answer or a slow server just keeps the old value while it is fresh, or
  shows nothing. A failed request is not retried for 2 minutes.

```json
{ "weather_lat": "41.3874", "weather_lon": "2.1686", "weather_units": "celsius" }
```

For a build without a theme, set the same three values in `res/values/config_extras.xml`.

## Ambient particles

`"ambient_particles": true` drifts a dozen fireflies over the night picture and a dozen sakura petals over
the day one (`wallpaper_day.jpg`), following the day and night switch. They are tiny views moved by the GPU,
but a weak GPU still redraws the screen while they move, so they stay off with `reduced_motion` and without a
wallpaper, and stop whenever Home is not in front.

## Reduced motion

`"reduced_motion": true` keeps the look but calms the movement: the highlight spring is stiff instead
of bouncing, tiles zoom without overshoot, fades are shorter, and Home does not replay the tile
entrance when you come back to it. Use it on TVs whose GPU cannot keep up; a MediaTek MT5895 on
Android 10 is the known case. The default, `false`, keeps the normal motion.

```json
{ "reduced_motion": true }
```

## Adding a theme

Open a PR with a new folder here, a one-line row in the table above and a screenshot from a real TV
in the PR description. Keep the dock list generic (the big streaming apps) unless the theme is for a
specific TV, and say which TV you tried it on.
