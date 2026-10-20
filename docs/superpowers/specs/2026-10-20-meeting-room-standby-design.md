# Meeting-room standby screen, HDMI auto-switch and CI status bar

Design for the office TV (Redmi A50 2025, `examples/xiaomi-mitv-astp0-mulan`), 2026-10-20.
Mockups: `designs/meeting-room-screen/` (variant C is the chosen layout, D the HDMI toast).

## Goal

The TV is used for one thing: people plug a laptop into HDMI and share their screen in meetings.
Between meetings it should be a calm information screen instead of a launcher with app tiles, and
plugging a cable in should bring the laptop up without anyone touching the remote.

Three features, all inside the existing launcher (`launcher/`), all off unless configured:

1. **Standby screen**: a full-screen clock with date, weather and a status bar, shown over Home after
   30 s without a key press.
2. **HDMI auto-switch**: plugging a device into an HDMI port switches the TV to that port; unplugging
   it brings Home (and so the standby screen) back.
3. **CI status**: the status bar shows the latest pipeline of each configured GitLab project.

Out of scope for this iteration: meeting schedule (企业微信), duty roster, pull-request counts.
The data layer keeps a `feed_url` hook so a Mac-side aggregator can add those later without an APK
change.

## Decisions already made

| Question | Decision |
|---|---|
| Layout | Variant C: centred clock, date line with weather, bottom status bar |
| Architecture | Everything in the launcher; no server; `feed_url` hook for later |
| Trigger | Fade in after 30 s idle on Home; any key fades out and is consumed |
| HDMI | Toast ~1.5 s, then switch; unplug returns Home; only react to changes |
| Network | Design for a wired office port. (The TV already associates with the 2.4 GHz `LY-guest` Wi-Fi but is held at a captive portal; whitelisting MAC `c4:8b:66:d6:c4:85` would also work.) |
| CI source | Self-hosted GitLab `https://git.17usoft.com`, read-only token, two placeholder projects |
| Weather | Open-Meteo, no key, Suzhou (31.30, 120.58) |
| Room name | Not shown |

## Verified on the device

- `dumpsys tv_input`: with a laptop on HDMI 2, `Hdmi2InputService/HW6` reports state 0
  (`INPUT_STATE_CONNECTED`); empty HDMI ports report 1 (`INPUT_STATE_STANDBY`). So the standard
  `TvInputManager` state distinguishes plugged from unplugged on this set. The callback
  `onInputStateChanged` has **not** been observed yet; see Risks.
- Switching inputs works through `com.xiaomi.mitv.tvplayer.EXTSRC_PLAY` with `--ei input N`
  (`input_map` in `config.xml`, already shipped).
- `https://api.open-meteo.com/v1/forecast?...&current=temperature_2m,weather_code` answers without
  a key; `https://git.17usoft.com/api/v4/version` answers 401 without a token (reachable).
- `auto_time` is 1; the clock is wrong only because the TV has had no internet (no NTP). It
  corrects itself once the office network passes traffic.

## Components

All new code lives in `launcher/app/src/main/java/com/gathin/tvhome/` next to the existing
classes. `MainActivity` only gains hooks; the logic sits in three small units.

### `StandbyView` (UI)

A `FrameLayout` inflated from `res/layout/standby.xml`, added last in `activity_main.xml` so it
covers everything, `visibility=GONE` by default.

Layout (1920×1080, Outfit for digits, PingFang/system for CJK, black background):

- Clock: `HH:mm`, Outfit ExtraLight, ~440 px high, centred horizontally, top ≈ 200 px.
- Date line under it: `10月20日 | 星期二 | 多云 21°`, 42 px, 70 % white. The weather segment is
  omitted when no weather is known.
- Status bar: a glass panel (`glass_panel` drawable) 110 px high, 96 px from the sides and 64 px
  from the bottom. Cells divided by 1 px lines at 25 % white:
  - cell 1, always: an outlined `HDMI` badge + `插线即投屏`;
  - one cell per CI entry: a 14 px dot (green `success`, red `failed`, amber for `running`/`pending`,
    grey for anything else or unknown) + project name + relative time (`2 分钟前`, `1 小时前`,
    `3 天前`). Up to 4 CI cells; more are dropped from the end.
  - When there are no CI entries the bar shows only the HDMI cell.
- Optional ambient glow: reuse `SkyView` behind the clock when `sky_intensity` > 0; nothing else
  (no images, no blur).
- Burn-in: the whole content block shifts by a random offset of up to ±24 px each minute, like
  `ClockDream` does.

Behaviour, owned by a small `StandbyController` inside the view's file:

- `MainActivity.onUserInteraction()` and `dispatchKeyEvent()` call `controller.touch()`, which
  restarts a 30 s (`standby_idle_seconds`) timer on the main handler.
- Timer fires → `show()`: fade in over 600 ms, start the minute tick.
- While visible, `dispatchKeyEvent()` calls `controller.consumeKey(event)`: `hide()` (fade out
  300 ms) and return `true` for both the down and the up event of that key, so the key never reaches
  the launcher. Back and Home are consumed the same way.
- `onPause()` → `cancel()`: stop the timer, hide instantly. `onResume()` → `touch()`.
- The HDMI toast (see below) is a child of `StandbyView` and can show whether or not the standby
  layer is visible; it uses the same panel style as the mockup D.

### `InputWatcher` (HDMI auto-switch)

Registered in `MainActivity.onCreate()` on the existing `TvInputManager` callback (the launcher
already registers one for `onInputAdded/Removed`; extend it rather than add a second).

```
onInputStateChanged(inputId, state):
    if !hdmi_auto_switch or input is not TYPE_HDMI passthrough: return
    previous = lastState[inputId]; lastState[inputId] = state
    if previous == null: return                       // first report after boot: no action
    if previous != CONNECTED and state == CONNECTED:  // plugged in
        if now - lastSwitch < 3 s: return              // debounce a loose plug
        lastSwitch = now; showToast(label, 1500 ms, then openInput(inputId)); current = inputId
    if inputId == current and state != CONNECTED:     // the port we are watching went away
        current = null; bringHomeToFront()
```

- `lastState` is seeded in `onCreate()` from `getInputState()` for every HDMI input, so a cable
  already plugged at boot never triggers a switch.
- `openInput(inputId)` is the existing method (channel URI first, Xiaomi `EXTSRC_PLAY` fallback).
- `bringHomeToFront()`: `startActivity(new Intent(this, MainActivity.class)
  .addFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP))`.
  The launcher process stays alive while Home is in the back stack, which is where it is while the
  Xiaomi player shows HDMI, so the callback keeps arriving. Verified assumption, see Risks.
- Only HDMI inputs take part; AV and the tuner are ignored.

### `StandbyFeed` (data)

One background thread (`HandlerThread`) owned by `MainActivity`, started when the standby feature
is on and at least one source is configured, stopped in `onDestroy()`.

- Sources, each with its own interval and 10 s connect/read timeouts, `HttpURLConnection`, no
  third-party HTTP library:
  - Weather, every 30 min: Open-Meteo current temperature and WMO code →
    `WeatherInfo{tempC, text}`; the WMO code maps to 晴 / 多云 / 阴 / 雾 / 雨 / 雪 / 雷雨 with
    a small table.
  - GitLab, every 5 min, one request per project:
    `GET {gitlab_url}/api/v4/projects/{url-encoded path}/pipelines?per_page=1` with header
    `PRIVATE-TOKEN: {gitlab_token}` → `CiInfo{name, status, updatedAtMillis}`; `name` is the last
    path segment. Empty array → status `unknown`.
  - Feed, every 1 min, used **instead of** the two above when `feed_url` is set:
    ```json
    {"weather": {"temp_c": 21, "text": "多云"},
     "ci": [{"name": "cleanyourtv", "status": "success", "updated_at": "2026-10-20T06:12:00Z"}],
     "next_event": {"title": "需求评审", "start": "2026-10-20T06:30:00Z"}}
    ```
    `next_event` is parsed and stored but not rendered in this iteration.
- Results land in an immutable `StandbySnapshot{weather, ci, fetchedAt}` posted to the main
  thread; a failed fetch keeps the previous snapshot, logs one line, and waits for the next
  interval (no retry bursts). The view renders whatever the snapshot holds.
- Parsing uses `org.json` in plain Java classes with no other Android imports. The platform's
  `org.json` is a stub in JVM unit tests, so `build.gradle` adds
  `testImplementation 'org.json:json:20240303'`; the API is the same.

### Configuration

New keys, read by `Config` from `config.xml` defaults and the `config.json` overlay like every
other key. All optional; a missing key keeps the feature off.

| Key | Type | Default | Meaning |
|---|---|---|---|
| `standby` | bool | false | Show the standby screen |
| `standby_idle_seconds` | int | 30 | Idle time before it appears (5–3600) |
| `weather_lat`, `weather_lon` | number | unset | Open-Meteo coordinates; both needed |
| `gitlab_url` | string | unset | e.g. `https://git.17usoft.com` |
| `gitlab_token` | string | unset | Read-only token (`read_api`) |
| `gitlab_projects` | list of `group/project` | empty | Projects for the status bar, in order |
| `feed_url` | string | unset | One JSON for everything; replaces the two sources above |
| `hdmi_auto_switch` | bool | false | Plug-in switches input, unplug returns Home |

- `themes/meeting-room/config.json` ships the office setup with two placeholder projects
  (`team/placeholder-one`, `team/placeholder-two`) and `gitlab_token` left as `"REPLACE_ME"`;
  `themes/README.md` documents the keys.
- The token sits in `/sdcard/Android/data/com.gathin.tvhome/files/config.json`, readable by
  anyone with ADB or a file manager on the TV. Accepted: it is read-only and the TV is on the office
  network. A token value that still reads `REPLACE_ME` disables the GitLab source.
- `examples/xiaomi-mitv-astp0-mulan/debloat.txt` header gets one line pointing at the theme.

## Failure handling

- The whole feature set is guarded the way `CardFeature` guards module cards: every entry point
  from `MainActivity` into the standby, watcher and feed code runs through a `StandbyFeature.run()`
  wrapper. The first caught exception logs once and switches the feature off for the process; Home
  keeps working exactly as with `standby=false`.
- No network, DNS failure, HTTP error, bad JSON: the snapshot keeps its last value; a never-fetched
  source simply does not render. No error text on screen.
- `openInput()` failing (nothing handles the intent) already shows the launcher's existing toast;
  the watcher resets `current` so the next plug-in can try again.
- `hdmi_auto_switch` and `standby` can be turned off independently by pushing a theme, which
  restarts Home.

## Testing

JVM unit tests (`launcher/app/src/test/`, plain JUnit like the existing tests):

- `StandbyFeedParserTest`: Open-Meteo JSON → `WeatherInfo`; WMO table edge codes; GitLab pipelines
  array (success, failed, running, empty, malformed) → `CiInfo`; feed JSON with missing sections;
  relative-time text (`刚刚`, `2 分钟前`, `1 小时前`, `3 天前`).
- `InputWatcherTest` (pure decision logic, states and a fake clock): first report ignored; standby →
  connected switches; connected → standby on the current port returns Home; non-HDMI ignored;
  debounce inside 3 s; boot-time plugged cable ignored.
- `StandbyConfigTest`: the new values go through a small pure-Java `StandbyConfig` holder
  (`Config` only reads the raw keys): `standby_idle_seconds` clamps to 5–3600, both coordinates
  required, `REPLACE_ME` or blank token treated as unset, project paths trimmed and blanks dropped.

On the TV (`TV_HOST=192.168.2.2`, screenshots via `tools/tv.py`):

1. Push `themes/meeting-room`, wait 30 s on Home: standby fades in; press any key: it fades out and
   the key does nothing else.
2. Unplug the Mac's Ethernet (no network): standby shows clock + date + HDMI cell only.
3. Plug a laptop into HDMI 2 three times: toast, then `topResumedActivity` is the Xiaomi player;
   unplug: `topResumedActivity` is `com.gathin.tvhome/.MainActivity` within 3 s.
4. Reboot with the cable already in: Home stays on Home.
5. With a valid token: CI cells show the real projects' status and time.

## Risks

- `onInputStateChanged` may not fire on this firmware even though `getInputState()` is right.
  First implementation task is to log the callback while plugging a cable. If it does not fire,
  `InputWatcher` polls `getInputState()` every 2 s from the feed thread instead; the decision logic
  is the same, only the source of state changes differs.
- Whether the launcher process survives while the Xiaomi player is in front for an hour is not
  proven. If Android kills it, unplugging would not bring Home back; the fallback is a sticky
  `Service` holding the callback, added only if the test in step 3 fails after a long session.
- Office network reachability for `api.open-meteo.com` (internet) and `git.17usoft.com` (intranet)
  is unknown until the TV is on a wired port; the design degrades to clock-only without it.
