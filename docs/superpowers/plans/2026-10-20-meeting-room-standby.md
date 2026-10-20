# Meeting-room Standby Screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the office TV's launcher a standby screen (clock, date, weather, CI status bar) that appears after 30 s idle, and make an HDMI plug-in switch the TV to that port and an unplug bring Home back.

**Architecture:** Everything lives in the existing launcher (`launcher/`). Pure-Java units carry the logic and are unit-tested on the JVM: `StandbyConfig` (validated settings), `StandbyData` (JSON parsing, WMO and relative-time mapping), `InputWatcher` (plug/unplug decisions). Three Android units wrap them: `StandbyView` (the overlay and its idle timer), `StandbyFeed` (background HTTP polling), and small hooks in `MainActivity`. A `StandbyFeature` guard switches the whole feature off for the process on the first exception so Home keeps working.

**Tech Stack:** Java 17, Android SDK 34 (minSdk 26), AndroidX already present, `org.json` (platform; `org.json:json:20240303` for JVM tests), JUnit 4.13.2, `HttpURLConnection`. Spec: `docs/superpowers/specs/2026-10-20-meeting-room-standby-design.md`.

## Global Constraints

- Every new behaviour is **off unless configured**: `standby` false, `hdmi_auto_switch` false, no sources. Upstream users must see no change.
- No third-party HTTP or JSON libraries in the APK; no images, no blur (2 GB TV).
- Pure-Java classes (`StandbyConfig`, `StandbyData`, `InputWatcher`, `StandbyFeature`) import nothing from `android.*` except `android.util.Log` in the guard, following `CardFeature`.
- All new Config keys: `standby`, `standby_idle_seconds` (5–3600, default 30), `weather_lat`, `weather_lon`, `gitlab_url`, `gitlab_token`, `gitlab_projects`, `feed_url`, `hdmi_auto_switch`.
- A `gitlab_token` of `REPLACE_ME` or blank means "no GitLab source".
- Fetch intervals: weather 30 min, GitLab 5 min, feed 1 min; 10 s connect and read timeouts; a failure keeps the previous result.
- HDMI: toast 1500 ms then switch; 3 s debounce between switches; first state report after boot and non-HDMI inputs are ignored.
- Idle fade in 600 ms; fade out 300 ms; the key that wakes the launcher is consumed (down and up).
- Commit after every task on branch `meeting-room-standby`. Commit messages end with `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.

### Environment for every build and test command

Run from `launcher/`:

```bash
export JAVA_HOME=/Users/pang/.local/opt/homebrew/opt/openjdk@17
export ANDROID_HOME=/Users/pang/.local/opt/homebrew/share/android-commandlinetools
export PATH="$JAVA_HOME/bin:$PATH"
```

Unit tests: `./gradlew testReleaseUnitTest --tests 'com.gathin.tvhome.<TestClass>' -q --console=plain` (all: drop `--tests`). Expected output on success: nothing printed (quiet) and exit code 0; on failure the failing assertions are printed.

TV: `TV_HOST=192.168.2.2`, shell commands via `tools/venv/bin/python -I tools/tv.py '<cmd>'` from the repo root (one connection at a time; never two in parallel), screenshots via `tools/tv.py --screenshot <file.png>` if available, otherwise `screencap -p /sdcard/s.png` then `tools/tv.py --pull`. Build and install: `cd launcher && TV_HOST=192.168.2.2 STOCK_LAUNCHER=none ./build-and-install.sh`. Push the theme: `TV_HOST=192.168.2.2 tools/theme.sh themes/meeting-room`.

### File structure

| File | Responsibility |
|---|---|
| `launcher/app/src/main/java/com/gathin/tvhome/StandbyConfig.java` (new) | Validated, immutable standby settings built from raw strings/numbers |
| `launcher/app/src/main/java/com/gathin/tvhome/Config.java` (modify) | Read the nine new keys from `config.xml` and the JSON overlay; expose `standby` |
| `launcher/app/src/main/res/values/config.xml` (modify) | Defaults for the nine keys |
| `launcher/app/src/main/java/com/gathin/tvhome/StandbyData.java` (new) | `WeatherInfo`, `CiInfo`, `StandbySnapshot`, `Age`; parsers for Open-Meteo, GitLab pipelines and the feed JSON; WMO and relative-time mapping |
| `launcher/app/src/main/java/com/gathin/tvhome/InputWatcher.java` (new) | Plug/unplug decision logic with debounce and boot seeding |
| `launcher/app/src/main/java/com/gathin/tvhome/StandbyFeature.java` (new) | Guard: first exception switches the feature off for the process |
| `launcher/app/src/main/java/com/gathin/tvhome/StandbyFeed.java` (new) | `HandlerThread` polling the sources, posting snapshots to the main thread |
| `launcher/app/src/main/java/com/gathin/tvhome/StandbyView.java` (new) | The overlay: idle timer, fades, key consumption, rendering, HDMI toast |
| `launcher/app/src/main/res/layout/standby.xml`, `res/drawable/hdmi_badge.xml`, `res/drawable/standby_bar.xml` (new) | Overlay layout and drawables |
| `launcher/app/src/main/res/values/strings.xml`, `values-zh-rCN/strings.xml` (modify) | Standby strings |
| `launcher/app/src/main/java/com/gathin/tvhome/MainActivity.java` (modify) | Hooks: create/attach view, watcher, feed; key and lifecycle forwarding; `onInputStateChanged` |
| `launcher/app/src/main/AndroidManifest.xml` (modify) | `INTERNET` permission |
| `launcher/app/build.gradle` (modify) | `testImplementation 'org.json:json:20240303'` |
| `launcher/app/src/test/java/com/gathin/tvhome/{StandbyConfigTest,StandbyDataTest,InputWatcherTest,StandbyFeatureTest}.java` (new) | JVM tests |
| `themes/meeting-room/config.json` (new), `themes/README.md` (modify), `examples/xiaomi-mitv-astp0-mulan/debloat.txt` (modify) | Office theme and docs |

---

### Task 1: StandbyConfig and the Config keys

**Files:**
- Create: `launcher/app/src/main/java/com/gathin/tvhome/StandbyConfig.java`
- Create: `launcher/app/src/test/java/com/gathin/tvhome/StandbyConfigTest.java`
- Modify: `launcher/app/src/main/java/com/gathin/tvhome/Config.java` (fields, constructor, `load`)
- Modify: `launcher/app/src/main/res/values/config.xml` (before `</resources>`)

**Interfaces:**
- Produces: `StandbyConfig.from(boolean enabled, int idleSeconds, String lat, String lon, String gitlabUrl, String gitlabToken, List<String> gitlabProjects, String feedUrl, boolean hdmiAutoSwitch)` and the fields `enabled, idleSeconds, weatherLat, weatherLon, gitlabUrl, gitlabToken, gitlabProjects, feedUrl, hdmiAutoSwitch`, plus `hasWeather()`, `hasGitlab()`, `hasFeed()`, `hasSources()`. `Config.standby` holds one.

- [ ] **Step 1: Write the failing test**

`launcher/app/src/test/java/com/gathin/tvhome/StandbyConfigTest.java`:

```java
package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The validated standby settings: what a theme's config.json turns into. */
public class StandbyConfigTest {

    private static StandbyConfig full() {
        return StandbyConfig.from(true, 30, "31.30", "120.58", "https://git.example.com/", "tok",
                Arrays.asList("team/one", " team/two ", ""), "", true);
    }

    @Test
    public void everythingOffByDefault() {
        StandbyConfig c = StandbyConfig.from(false, 30, "", "", "", "", Collections.emptyList(), "", false);
        assertFalse(c.enabled);
        assertFalse(c.hdmiAutoSwitch);
        assertFalse(c.hasWeather());
        assertFalse(c.hasGitlab());
        assertFalse(c.hasFeed());
        assertFalse(c.hasSources());
    }

    @Test
    public void aFullConfigIsKeptAndTidied() {
        StandbyConfig c = full();
        assertTrue(c.enabled);
        assertEquals(30, c.idleSeconds);
        assertEquals(31.30, c.weatherLat, 1e-9);
        assertEquals(120.58, c.weatherLon, 1e-9);
        assertTrue(c.hasWeather());
        assertEquals("https://git.example.com", c.gitlabUrl);     // trailing slash dropped
        assertEquals("tok", c.gitlabToken);
        assertEquals(Arrays.asList("team/one", "team/two"), c.gitlabProjects);   // trimmed, blanks dropped
        assertTrue(c.hasGitlab());
        assertFalse(c.hasFeed());
        assertTrue(c.hasSources());
    }

    @Test
    public void idleSecondsClampToRange() {
        assertEquals(5, StandbyConfig.from(true, 1, "", "", "", "", Collections.emptyList(), "", false).idleSeconds);
        assertEquals(3600, StandbyConfig.from(true, 99999, "", "", "", "", Collections.emptyList(), "", false).idleSeconds);
    }

    @Test
    public void weatherNeedsBothCoordinatesAsNumbers() {
        assertFalse(StandbyConfig.from(true, 30, "31.3", "", "", "", Collections.emptyList(), "", false).hasWeather());
        assertFalse(StandbyConfig.from(true, 30, "north", "120", "", "", Collections.emptyList(), "", false).hasWeather());
        assertFalse(StandbyConfig.from(true, 30, "91", "120", "", "", Collections.emptyList(), "", false).hasWeather());
        assertTrue(StandbyConfig.from(true, 30, "-33.9", "151.2", "", "", Collections.emptyList(), "", false).hasWeather());
    }

    @Test
    public void gitlabNeedsUrlTokenAndProjects() {
        assertFalse(StandbyConfig.from(true, 30, "", "", "https://g", "REPLACE_ME", Arrays.asList("a/b"), "", false).hasGitlab());
        assertFalse(StandbyConfig.from(true, 30, "", "", "https://g", " ", Arrays.asList("a/b"), "", false).hasGitlab());
        assertFalse(StandbyConfig.from(true, 30, "", "", "", "tok", Arrays.asList("a/b"), "", false).hasGitlab());
        assertFalse(StandbyConfig.from(true, 30, "", "", "https://g", "tok", Collections.singletonList(" "), "", false).hasGitlab());
        assertFalse(StandbyConfig.from(true, 30, "", "", "ftp://g", "tok", Arrays.asList("a/b"), "", false).hasGitlab());
    }

    @Test
    public void aFeedUrlIsASourceOnItsOwn() {
        StandbyConfig c = StandbyConfig.from(true, 30, "", "", "", "", Collections.emptyList(), "http://10.0.0.5/status.json", false);
        assertTrue(c.hasFeed());
        assertTrue(c.hasSources());
        assertFalse(StandbyConfig.from(true, 30, "", "", "", "", Collections.emptyList(), "status.json", false).hasFeed());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run (from `launcher/`, env exported): `./gradlew testReleaseUnitTest --tests 'com.gathin.tvhome.StandbyConfigTest' -q --console=plain`
Expected: compilation error, `cannot find symbol: class StandbyConfig`.

- [ ] **Step 3: Write StandbyConfig**

`launcher/app/src/main/java/com/gathin/tvhome/StandbyConfig.java`:

```java
package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The meeting-room standby settings (config.xml, overlaid by a theme's config.json; see Config):
 * whether the standby screen and HDMI auto-switch are on, and where the weather and CI status come
 * from. Built from raw values so the checks run in plain JVM tests; a value that does not make sense
 * simply switches its source off, never the home screen.
 */
final class StandbyConfig {
    static final int MIN_IDLE = 5, MAX_IDLE = 3600, DEFAULT_IDLE = 30;
    /** The token the shipped theme carries until the owner replaces it. */
    static final String TOKEN_PLACEHOLDER = "REPLACE_ME";

    final boolean enabled;
    final int idleSeconds;
    /** NaN when weather is off. */
    final double weatherLat, weatherLon;
    /** Base URL without a trailing slash, or "" when GitLab is off. */
    final String gitlabUrl;
    final String gitlabToken;
    /** group/project paths, trimmed, no blanks. */
    final List<String> gitlabProjects;
    /** One JSON for everything; when set, the weather and GitLab sources are not used. */
    final String feedUrl;
    final boolean hdmiAutoSwitch;

    private StandbyConfig(boolean enabled, int idleSeconds, double lat, double lon, String gitlabUrl, String gitlabToken,
                          List<String> gitlabProjects, String feedUrl, boolean hdmiAutoSwitch) {
        this.enabled = enabled;
        this.idleSeconds = idleSeconds;
        this.weatherLat = lat;
        this.weatherLon = lon;
        this.gitlabUrl = gitlabUrl;
        this.gitlabToken = gitlabToken;
        this.gitlabProjects = Collections.unmodifiableList(gitlabProjects);
        this.feedUrl = feedUrl;
        this.hdmiAutoSwitch = hdmiAutoSwitch;
    }

    static StandbyConfig from(boolean enabled, int idleSeconds, String lat, String lon, String gitlabUrl, String gitlabToken,
                              List<String> gitlabProjects, String feedUrl, boolean hdmiAutoSwitch) {
        int idle = Math.max(MIN_IDLE, Math.min(MAX_IDLE, idleSeconds));
        double la = coordinate(lat, 90), lo = coordinate(lon, 180);
        if (Double.isNaN(la) || Double.isNaN(lo)) la = lo = Double.NaN;
        String url = httpUrl(gitlabUrl);
        while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        String token = gitlabToken == null ? "" : gitlabToken.trim();
        if (TOKEN_PLACEHOLDER.equals(token)) token = "";
        List<String> projects = new ArrayList<>();
        if (gitlabProjects != null) {
            for (String p : gitlabProjects) {
                if (p == null) continue;
                String t = p.trim();
                if (!t.isEmpty()) projects.add(t);
            }
        }
        return new StandbyConfig(enabled, idle, la, lo, url, token, projects, httpUrl(feedUrl), hdmiAutoSwitch);
    }

    boolean hasWeather() {
        return !Double.isNaN(weatherLat);
    }

    boolean hasGitlab() {
        return !gitlabUrl.isEmpty() && !gitlabToken.isEmpty() && !gitlabProjects.isEmpty();
    }

    boolean hasFeed() {
        return !feedUrl.isEmpty();
    }

    /** Anything for StandbyFeed to fetch. */
    boolean hasSources() {
        return hasFeed() || hasWeather() || hasGitlab();
    }

    /** A decimal degree within ±limit, or NaN. */
    private static double coordinate(String text, double limit) {
        if (text == null) return Double.NaN;
        try {
            double v = Double.parseDouble(text.trim());
            return Math.abs(v) <= limit ? v : Double.NaN;
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    /** The URL trimmed, or "" unless it starts with http:// or https://. */
    private static String httpUrl(String text) {
        if (text == null) return "";
        String t = text.trim();
        return t.startsWith("http://") || t.startsWith("https://") ? t : "";
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew testReleaseUnitTest --tests 'com.gathin.tvhome.StandbyConfigTest' -q --console=plain`
Expected: no output, exit 0.

- [ ] **Step 5: Add the defaults to config.xml**

In `launcher/app/src/main/res/values/config.xml`, insert before the final `</resources>`:

```xml
    <!-- Meeting-room standby screen (docs/superpowers/specs/2026-10-20-meeting-room-standby-design.md).
         Off unless a theme turns it on: a clock, the date and weather, and a status bar with the latest
         CI pipeline of each GitLab project, shown after standby_idle_seconds without a key press. -->
    <bool name="standby">false</bool>
    <integer name="standby_idle_seconds">30</integer>
    <!-- Open-Meteo coordinates, decimal degrees as text; both empty keeps the weather off. -->
    <string name="weather_lat" translatable="false"></string>
    <string name="weather_lon" translatable="false"></string>
    <!-- Self-hosted GitLab: base URL, a read_api token, and group/project paths for the status bar. -->
    <string name="gitlab_url" translatable="false"></string>
    <string name="gitlab_token" translatable="false"></string>
    <string-array name="gitlab_projects" />
    <!-- One JSON with everything (see StandbyData.parseFeed); when set, the two sources above are not used. -->
    <string name="feed_url" translatable="false"></string>
    <!-- Plugging a device into an HDMI port switches to it; unplugging brings Home back. -->
    <bool name="hdmi_auto_switch">false</bool>
```

- [ ] **Step 6: Read the keys in Config**

In `launcher/app/src/main/java/com/gathin/tvhome/Config.java`:

Add a field after `final Festival festival;`:

```java
    /** Meeting-room standby screen and HDMI auto-switch (standby, hdmi_auto_switch, weather_*, gitlab_*, feed_url). */
    final StandbyConfig standby;
```

Add a last constructor parameter `StandbyConfig standby` (after `Festival festival`) and `this.standby = standby;` as the last assignment.

In `load`, after `boolean applied = false;` add:

```java
        boolean standbyOn = res.getBoolean(R.bool.standby);
        int standbyIdle = res.getInteger(R.integer.standby_idle_seconds);
        String weatherLat = res.getString(R.string.weather_lat).trim();
        String weatherLon = res.getString(R.string.weather_lon).trim();
        String gitlabUrl = res.getString(R.string.gitlab_url).trim();
        String gitlabToken = res.getString(R.string.gitlab_token).trim();
        List<String> gitlabProjects = new ArrayList<>(Arrays.asList(res.getStringArray(R.array.gitlab_projects)));
        String feedUrl = res.getString(R.string.feed_url).trim();
        boolean hdmiAutoSwitch = res.getBoolean(R.bool.hdmi_auto_switch);
```

Inside `if (json != null) { ... }`, after the `settingsKeyPanel = ...` line add:

```java
            standbyOn = bool(json, "standby", standbyOn);
            standbyIdle = integer(json, "standby_idle_seconds", standbyIdle, StandbyConfig.MIN_IDLE, StandbyConfig.MAX_IDLE);
            weatherLat = numberText(json, "weather_lat", weatherLat);
            weatherLon = numberText(json, "weather_lon", weatherLon);
            gitlabUrl = string(json, "gitlab_url", gitlabUrl);
            gitlabToken = string(json, "gitlab_token", gitlabToken);
            gitlabProjects = stringList(json, "gitlab_projects", gitlabProjects);
            feedUrl = string(json, "feed_url", feedUrl);
            hdmiAutoSwitch = bool(json, "hdmi_auto_switch", hdmiAutoSwitch);
```

Replace the final `return new Config(...)` so it ends with `festival,` followed by:

```java
                StandbyConfig.from(standbyOn, standbyIdle, weatherLat, weatherLon, gitlabUrl, gitlabToken,
                        gitlabProjects, feedUrl, hdmiAutoSwitch));
```

Add a helper next to `string(...)`:

```java
    /** A number or a string holding one, as text; StandbyConfig does the parsing and range check. */
    private static String numberText(JSONObject o, String key, String fallback) {
        Object v = o.opt(key);
        if (v instanceof Number) return v.toString();
        return v instanceof String ? ((String) v).trim() : fallback;
    }
```

- [ ] **Step 7: Build to verify the resources and Config compile**

Run: `./gradlew assembleRelease -q --console=plain`
Expected: no output, exit 0. Then `./gradlew testReleaseUnitTest -q --console=plain` passes (all existing tests still green).

- [ ] **Step 8: Commit**

```bash
git add launcher/app/src/main/java/com/gathin/tvhome/StandbyConfig.java launcher/app/src/test/java/com/gathin/tvhome/StandbyConfigTest.java launcher/app/src/main/java/com/gathin/tvhome/Config.java launcher/app/src/main/res/values/config.xml
git commit -m "Launcher: standby settings (StandbyConfig) read from config.xml and themes

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

Note: `Config.java` and `config.xml` already hold uncommitted changes from the Xiaomi work (extra_apps, input_map). Committing them together with this task is fine; they are on the same branch.

---

### Task 2: StandbyData: models, parsers, WMO and relative time

**Files:**
- Create: `launcher/app/src/main/java/com/gathin/tvhome/StandbyData.java`
- Create: `launcher/app/src/test/java/com/gathin/tvhome/StandbyDataTest.java`
- Modify: `launcher/app/build.gradle` (dependencies block)

**Interfaces:**
- Produces:
  - `enum StandbyData.WeatherKind { CLEAR, CLOUDY, OVERCAST, FOG, RAIN, SNOW, THUNDER, UNKNOWN }`
  - `StandbyData.WeatherInfo(double tempC, WeatherKind kind, String text)` with `text` nullable (feed-supplied label wins over `kind`)
  - `enum StandbyData.CiStatus { SUCCESS, FAILED, RUNNING, UNKNOWN }`
  - `StandbyData.CiInfo(String name, CiStatus status, long updatedAtMillis)` (`updatedAtMillis` 0 when unknown)
  - `StandbyData.StandbySnapshot(WeatherInfo weather, List<CiInfo> ci)` both may be null/empty; `StandbySnapshot.EMPTY`
  - `StandbyData.Age(int unit, int count)` with `Age.JUST_NOW, MINUTES, HOURS, DAYS`
  - `static WeatherInfo parseOpenMeteo(String json)` → null on any problem
  - `static CiInfo parseGitlabPipelines(String name, String json)` → status UNKNOWN on empty array, null on malformed
  - `static StandbySnapshot parseFeed(String json)` → null on malformed
  - `static WeatherKind weatherKind(int wmoCode)`
  - `static Age age(long thenMillis, long nowMillis)`
  - `static long parseIso8601(String text)` → epoch millis or 0
  - `static String projectName(String path)` → last path segment

- [ ] **Step 1: Add the JSON test dependency**

In `launcher/app/build.gradle`, inside `dependencies { ... }` after the junit line:

```groovy
    // The platform's org.json is a stub in JVM tests; the real one has the same API.
    testImplementation 'org.json:json:20240303'
```

- [ ] **Step 2: Write the failing test**

`launcher/app/src/test/java/com/gathin/tvhome/StandbyDataTest.java`:

```java
package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** What the standby screen makes of the JSON it fetches. */
public class StandbyDataTest {

    private static final String OPEN_METEO = "{\"latitude\":31.25,\"current_units\":{\"temperature_2m\":\"°C\"},"
            + "\"current\":{\"time\":\"2026-10-20T14:15\",\"interval\":900,\"temperature_2m\":21.4,\"weather_code\":3}}";

    @Test
    public void openMeteoCurrentWeather() {
        StandbyData.WeatherInfo w = StandbyData.parseOpenMeteo(OPEN_METEO);
        assertNotNull(w);
        assertEquals(21.4, w.tempC, 1e-9);
        assertEquals(StandbyData.WeatherKind.OVERCAST, w.kind);
        assertNull(w.text);
    }

    @Test
    public void openMeteoProblemsGiveNull() {
        assertNull(StandbyData.parseOpenMeteo("not json"));
        assertNull(StandbyData.parseOpenMeteo("{\"current\":{\"weather_code\":1}}"));     // no temperature
        assertNull(StandbyData.parseOpenMeteo("{\"error\":true,\"reason\":\"bad lat\"}"));
        assertNull(StandbyData.parseOpenMeteo(null));
    }

    @Test
    public void wmoCodesMapToAFewKinds() {
        assertEquals(StandbyData.WeatherKind.CLEAR, StandbyData.weatherKind(0));
        assertEquals(StandbyData.WeatherKind.CLEAR, StandbyData.weatherKind(1));
        assertEquals(StandbyData.WeatherKind.CLOUDY, StandbyData.weatherKind(2));
        assertEquals(StandbyData.WeatherKind.OVERCAST, StandbyData.weatherKind(3));
        assertEquals(StandbyData.WeatherKind.FOG, StandbyData.weatherKind(45));
        assertEquals(StandbyData.WeatherKind.FOG, StandbyData.weatherKind(48));
        assertEquals(StandbyData.WeatherKind.RAIN, StandbyData.weatherKind(51));
        assertEquals(StandbyData.WeatherKind.RAIN, StandbyData.weatherKind(65));
        assertEquals(StandbyData.WeatherKind.SNOW, StandbyData.weatherKind(71));
        assertEquals(StandbyData.WeatherKind.SNOW, StandbyData.weatherKind(77));
        assertEquals(StandbyData.WeatherKind.RAIN, StandbyData.weatherKind(80));
        assertEquals(StandbyData.WeatherKind.SNOW, StandbyData.weatherKind(86));
        assertEquals(StandbyData.WeatherKind.THUNDER, StandbyData.weatherKind(95));
        assertEquals(StandbyData.WeatherKind.THUNDER, StandbyData.weatherKind(99));
        assertEquals(StandbyData.WeatherKind.UNKNOWN, StandbyData.weatherKind(-1));
        assertEquals(StandbyData.WeatherKind.UNKNOWN, StandbyData.weatherKind(120));
    }

    @Test
    public void gitlabLatestPipeline() {
        String json = "[{\"id\":9,\"status\":\"success\",\"ref\":\"main\",\"updated_at\":\"2026-10-20T06:12:00.123Z\"}]";
        StandbyData.CiInfo ci = StandbyData.parseGitlabPipelines("cleanyourtv", json);
        assertNotNull(ci);
        assertEquals("cleanyourtv", ci.name);
        assertEquals(StandbyData.CiStatus.SUCCESS, ci.status);
        assertEquals(StandbyData.parseIso8601("2026-10-20T06:12:00.123Z"), ci.updatedAtMillis);
    }

    @Test
    public void gitlabStatusWords() {
        for (String s : new String[]{"running", "pending", "created", "waiting_for_resource", "preparing", "scheduled"}) {
            assertEquals(s, StandbyData.CiStatus.RUNNING, pipeline(s).status);
        }
        assertEquals(StandbyData.CiStatus.FAILED, pipeline("failed").status);
        assertEquals(StandbyData.CiStatus.UNKNOWN, pipeline("canceled").status);
        assertEquals(StandbyData.CiStatus.UNKNOWN, pipeline("skipped").status);
        assertEquals(StandbyData.CiStatus.UNKNOWN, pipeline("manual").status);
    }

    private static StandbyData.CiInfo pipeline(String status) {
        StandbyData.CiInfo ci = StandbyData.parseGitlabPipelines("p", "[{\"status\":\"" + status + "\"}]");
        assertNotNull(ci);
        return ci;
    }

    @Test
    public void gitlabEmptyAndBroken() {
        StandbyData.CiInfo none = StandbyData.parseGitlabPipelines("p", "[]");
        assertNotNull(none);
        assertEquals(StandbyData.CiStatus.UNKNOWN, none.status);
        assertEquals(0, none.updatedAtMillis);
        assertNull(StandbyData.parseGitlabPipelines("p", "{\"message\":\"401 Unauthorized\"}"));
        assertNull(StandbyData.parseGitlabPipelines("p", "<html>"));
        assertNull(StandbyData.parseGitlabPipelines("p", null));
    }

    @Test
    public void iso8601WithZoneOrOffsetOrNothingUsable() {
        assertEquals(1_792_476_720_000L, StandbyData.parseIso8601("2026-10-20T06:12:00Z"));
        assertEquals(1_792_476_720_000L, StandbyData.parseIso8601("2026-10-20T14:12:00+08:00"));
        assertEquals(1_792_476_720_123L, StandbyData.parseIso8601("2026-10-20T06:12:00.123Z"));
        assertEquals(0, StandbyData.parseIso8601("yesterday"));
        assertEquals(0, StandbyData.parseIso8601(null));
    }

    @Test
    public void feedJsonWithEverything() {
        String json = "{\"weather\":{\"temp_c\":21,\"text\":\"多云\"},"
                + "\"ci\":[{\"name\":\"cleanyourtv\",\"status\":\"success\",\"updated_at\":\"2026-10-20T06:12:00Z\"},"
                + "{\"name\":\"api\",\"status\":\"failed\"}],"
                + "\"next_event\":{\"title\":\"需求评审\",\"start\":\"2026-10-20T06:30:00Z\"}}";
        StandbyData.StandbySnapshot s = StandbyData.parseFeed(json);
        assertNotNull(s);
        assertEquals(21.0, s.weather.tempC, 1e-9);
        assertEquals("多云", s.weather.text);
        assertEquals(StandbyData.WeatherKind.UNKNOWN, s.weather.kind);
        assertEquals(2, s.ci.size());
        assertEquals("cleanyourtv", s.ci.get(0).name);
        assertEquals(StandbyData.CiStatus.SUCCESS, s.ci.get(0).status);
        assertEquals(StandbyData.CiStatus.FAILED, s.ci.get(1).status);
        assertEquals(0, s.ci.get(1).updatedAtMillis);
    }

    @Test
    public void feedJsonWithMissingSections() {
        StandbyData.StandbySnapshot s = StandbyData.parseFeed("{}");
        assertNotNull(s);
        assertNull(s.weather);
        assertTrue(s.ci.isEmpty());
        StandbyData.StandbySnapshot bad = StandbyData.parseFeed("{\"ci\":[{\"status\":\"success\"}],\"weather\":{\"text\":\"晴\"}}");
        assertNotNull(bad);
        assertTrue(bad.ci.isEmpty());          // an entry without a name is dropped
        assertNull(bad.weather);               // weather without a temperature is dropped
        assertNull(StandbyData.parseFeed("[]"));
        assertNull(StandbyData.parseFeed(null));
    }

    @Test
    public void relativeAge() {
        long now = 1_792_476_720_000L;
        assertEquals(StandbyData.Age.JUST_NOW, StandbyData.age(now - 30_000, now).unit);
        StandbyData.Age m = StandbyData.age(now - 2 * 60_000, now);
        assertEquals(StandbyData.Age.MINUTES, m.unit);
        assertEquals(2, m.count);
        StandbyData.Age h = StandbyData.age(now - 90 * 60_000, now);
        assertEquals(StandbyData.Age.HOURS, h.unit);
        assertEquals(1, h.count);
        StandbyData.Age d = StandbyData.age(now - 3 * 24 * 3600_000L, now);
        assertEquals(StandbyData.Age.DAYS, d.unit);
        assertEquals(3, d.count);
        assertEquals(StandbyData.Age.JUST_NOW, StandbyData.age(now + 60_000, now).unit);   // clock skew: never negative
    }

    @Test
    public void projectNameIsTheLastSegment() {
        assertEquals("cleanyourtv", StandbyData.projectName("team/tv/cleanyourtv"));
        assertEquals("solo", StandbyData.projectName("solo"));
        assertEquals("x", StandbyData.projectName("a/x/"));
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew testReleaseUnitTest --tests 'com.gathin.tvhome.StandbyDataTest' -q --console=plain`
Expected: compilation error, `cannot find symbol: class StandbyData`.

- [ ] **Step 4: Write StandbyData**

`launcher/app/src/main/java/com/gathin/tvhome/StandbyData.java`:

```java
package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What the standby screen shows, and how the fetched JSON turns into it. Pure Java apart from
 * org.json, so every parser runs in the JVM tests; nothing here touches the network or a view.
 */
final class StandbyData {

    enum WeatherKind { CLEAR, CLOUDY, OVERCAST, FOG, RAIN, SNOW, THUNDER, UNKNOWN }

    enum CiStatus { SUCCESS, FAILED, RUNNING, UNKNOWN }

    static final class WeatherInfo {
        final double tempC;
        final WeatherKind kind;
        /** A label supplied by the feed, shown instead of the kind; null from Open-Meteo. */
        final String text;

        WeatherInfo(double tempC, WeatherKind kind, String text) {
            this.tempC = tempC;
            this.kind = kind;
            this.text = text;
        }
    }

    static final class CiInfo {
        final String name;
        final CiStatus status;
        /** Epoch millis of the pipeline's last update, 0 when unknown. */
        final long updatedAtMillis;

        CiInfo(String name, CiStatus status, long updatedAtMillis) {
            this.name = name;
            this.status = status;
            this.updatedAtMillis = updatedAtMillis;
        }
    }

    /** Everything the view renders; a null weather or empty ci list means "nothing known". */
    static final class StandbySnapshot {
        static final StandbySnapshot EMPTY = new StandbySnapshot(null, Collections.emptyList());
        final WeatherInfo weather;
        final List<CiInfo> ci;

        StandbySnapshot(WeatherInfo weather, List<CiInfo> ci) {
            this.weather = weather;
            this.ci = Collections.unmodifiableList(new ArrayList<>(ci));
        }
    }

    /** How long ago something happened, in the unit the status bar shows. */
    static final class Age {
        static final int JUST_NOW = 0, MINUTES = 1, HOURS = 2, DAYS = 3;
        final int unit;
        final int count;

        Age(int unit, int count) {
            this.unit = unit;
            this.count = count;
        }
    }

    private StandbyData() {
    }

    /** Open-Meteo: {"current":{"temperature_2m":21.4,"weather_code":3}}. */
    static WeatherInfo parseOpenMeteo(String json) {
        if (json == null) return null;
        try {
            JSONObject current = new JSONObject(json).optJSONObject("current");
            if (current == null || !(current.opt("temperature_2m") instanceof Number)) return null;
            double temp = current.getDouble("temperature_2m");
            int code = current.opt("weather_code") instanceof Number ? current.getInt("weather_code") : -1;
            return new WeatherInfo(temp, weatherKind(code), null);
        } catch (Exception e) {
            return null;
        }
    }

    /** WMO weather interpretation codes, folded into the few words the date line shows. */
    static WeatherKind weatherKind(int code) {
        if (code == 0 || code == 1) return WeatherKind.CLEAR;
        if (code == 2) return WeatherKind.CLOUDY;
        if (code == 3) return WeatherKind.OVERCAST;
        if (code == 45 || code == 48) return WeatherKind.FOG;
        if (code >= 51 && code <= 67) return WeatherKind.RAIN;
        if (code >= 71 && code <= 77) return WeatherKind.SNOW;
        if (code >= 80 && code <= 82) return WeatherKind.RAIN;
        if (code == 85 || code == 86) return WeatherKind.SNOW;
        if (code >= 95 && code <= 99) return WeatherKind.THUNDER;
        return WeatherKind.UNKNOWN;
    }

    /** GitLab GET /projects/:id/pipelines?per_page=1: a JSON array; the first entry is the latest. */
    static CiInfo parseGitlabPipelines(String name, String json) {
        if (json == null) return null;
        try {
            JSONArray a = new JSONArray(json);
            if (a.length() == 0) return new CiInfo(name, CiStatus.UNKNOWN, 0);
            JSONObject p = a.getJSONObject(0);
            return new CiInfo(name, ciStatus(p.optString("status", "")), parseIso8601(p.optString("updated_at", null)));
        } catch (Exception e) {
            return null;
        }
    }

    static CiStatus ciStatus(String word) {
        switch (word) {
            case "success": return CiStatus.SUCCESS;
            case "failed": return CiStatus.FAILED;
            case "running": case "pending": case "created": case "waiting_for_resource":
            case "preparing": case "scheduled": return CiStatus.RUNNING;
            default: return CiStatus.UNKNOWN;
        }
    }

    /**
     * The aggregated feed (feed_url): {"weather":{"temp_c":21,"text":"多云"},
     * "ci":[{"name":"x","status":"success","updated_at":"..."}], "next_event":{...}}.
     * next_event is accepted but not shown in this version.
     */
    static StandbySnapshot parseFeed(String json) {
        if (json == null) return null;
        try {
            JSONObject o = new JSONObject(json);
            WeatherInfo weather = null;
            JSONObject w = o.optJSONObject("weather");
            if (w != null && w.opt("temp_c") instanceof Number) {
                String text = w.opt("text") instanceof String ? w.getString("text").trim() : "";
                weather = new WeatherInfo(w.getDouble("temp_c"), WeatherKind.UNKNOWN, text.isEmpty() ? null : text);
            }
            List<CiInfo> ci = new ArrayList<>();
            JSONArray a = o.optJSONArray("ci");
            for (int i = 0; a != null && i < a.length(); i++) {
                JSONObject c = a.optJSONObject(i);
                if (c == null) continue;
                String name = c.opt("name") instanceof String ? c.getString("name").trim() : "";
                if (name.isEmpty()) continue;
                ci.add(new CiInfo(name, ciStatus(c.optString("status", "")), parseIso8601(c.optString("updated_at", null))));
            }
            return new StandbySnapshot(weather, ci);
        } catch (Exception e) {
            return null;
        }
    }

    /** "2026-10-20T06:12:00Z" or with an offset, to epoch millis; 0 when unreadable. */
    static long parseIso8601(String text) {
        if (text == null) return 0;
        try {
            return OffsetDateTime.parse(text.trim()).toInstant().toEpochMilli();
        } catch (Exception e) {
            return 0;
        }
    }

    static Age age(long thenMillis, long nowMillis) {
        long s = Math.max(0, (nowMillis - thenMillis) / 1000);
        if (s < 60) return new Age(Age.JUST_NOW, 0);
        if (s < 3600) return new Age(Age.MINUTES, (int) (s / 60));
        if (s < 86400) return new Age(Age.HOURS, (int) (s / 3600));
        return new Age(Age.DAYS, (int) (s / 86400));
    }

    /** "group/sub/project" → "project". */
    static String projectName(String path) {
        String p = path;
        while (p.endsWith("/")) p = p.substring(0, p.length() - 1);
        return p.substring(p.lastIndexOf('/') + 1);
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew testReleaseUnitTest --tests 'com.gathin.tvhome.StandbyDataTest' -q --console=plain`
Expected: no output, exit 0. If `OffsetDateTime` fails under the test JVM, check that `sourceCompatibility` is 17 (it is) and that the epoch constants match: `2026-10-20T06:12:00Z` is `1792476720` seconds (verify with `date -u -j -f "%Y-%m-%dT%H:%M:%SZ" 2026-10-20T06:12:00Z +%s` on macOS).

- [ ] **Step 6: Commit**

```bash
git add launcher/app/build.gradle launcher/app/src/main/java/com/gathin/tvhome/StandbyData.java launcher/app/src/test/java/com/gathin/tvhome/StandbyDataTest.java
git commit -m "Launcher: StandbyData parsers for Open-Meteo, GitLab pipelines and the feed JSON

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 3: InputWatcher decision logic

**Files:**
- Create: `launcher/app/src/main/java/com/gathin/tvhome/InputWatcher.java`
- Create: `launcher/app/src/test/java/com/gathin/tvhome/InputWatcherTest.java`

**Interfaces:**
- Produces: `InputWatcher(boolean enabled)`; `void seed(String inputId, int state)`; `Action onState(String inputId, boolean hdmi, int state, long nowMillis)`; `void track(String inputId)` (a manual switch from the Inputs menu); `void switchFailed()`; `String current()`. `Action.kind` is `Kind.NONE | SWITCH | HOME`, `Action.inputId` the port to switch to. State constants mirror `TvInputManager`: `CONNECTED = 0`, `STANDBY = 1`, `DISCONNECTED = 2`.

- [ ] **Step 1: Write the failing test**

`launcher/app/src/test/java/com/gathin/tvhome/InputWatcherTest.java`:

```java
package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** Plug a laptop in: switch to it. Unplug it: back to Home. Nothing else moves the TV. */
public class InputWatcherTest {

    private static final String HDMI1 = "com.droidlogic.tvinput/.services.Hdmi1InputService/HW5";
    private static final String HDMI2 = "com.droidlogic.tvinput/.services.Hdmi2InputService/HW6";
    private static final String AV = "com.droidlogic.tvinput/.services.AV1InputService/HW1";

    private static InputWatcher booted() {
        InputWatcher w = new InputWatcher(true);
        w.seed(HDMI1, InputWatcher.STANDBY);
        w.seed(HDMI2, InputWatcher.STANDBY);
        w.seed(AV, InputWatcher.STANDBY);
        return w;
    }

    @Test
    public void plugInSwitchesToThatPort() {
        InputWatcher w = booted();
        InputWatcher.Action a = w.onState(HDMI2, true, InputWatcher.CONNECTED, 10_000);
        assertEquals(InputWatcher.Kind.SWITCH, a.kind);
        assertEquals(HDMI2, a.inputId);
        assertEquals(HDMI2, w.current());
    }

    @Test
    public void unplugOfTheCurrentPortGoesHome() {
        InputWatcher w = booted();
        w.onState(HDMI2, true, InputWatcher.CONNECTED, 10_000);
        InputWatcher.Action a = w.onState(HDMI2, true, InputWatcher.STANDBY, 20_000);
        assertEquals(InputWatcher.Kind.HOME, a.kind);
        assertNull(w.current());
        // and DISCONNECTED counts as gone too
        w.onState(HDMI2, true, InputWatcher.CONNECTED, 30_000);
        assertEquals(InputWatcher.Kind.HOME, w.onState(HDMI2, true, InputWatcher.DISCONNECTED, 40_000).kind);
    }

    @Test
    public void unplugOfAnotherPortDoesNothing() {
        InputWatcher w = booted();
        w.onState(HDMI2, true, InputWatcher.CONNECTED, 10_000);
        w.onState(HDMI1, true, InputWatcher.CONNECTED, 11_000);       // inside the debounce: ignored, HDMI2 stays current
        assertEquals(InputWatcher.Kind.NONE, w.onState(HDMI1, true, InputWatcher.STANDBY, 30_000).kind);
        assertEquals(HDMI2, w.current());
    }

    @Test
    public void aCableAlreadyInAtBootIsIgnored() {
        InputWatcher w = new InputWatcher(true);
        w.seed(HDMI2, InputWatcher.CONNECTED);
        assertEquals(InputWatcher.Kind.NONE, w.onState(HDMI2, true, InputWatcher.CONNECTED, 10_000).kind);
        // Unplugging it is not "our" port either: the TV was never switched by us.
        assertEquals(InputWatcher.Kind.NONE, w.onState(HDMI2, true, InputWatcher.STANDBY, 20_000).kind);
        // Plugging it back in after that is a real change.
        assertEquals(InputWatcher.Kind.SWITCH, w.onState(HDMI2, true, InputWatcher.CONNECTED, 30_000).kind);
    }

    @Test
    public void firstReportOfAnUnseededPortIsIgnored() {
        InputWatcher w = booted();
        String hdmi3 = "com.droidlogic.tvinput/.services.Hdmi3InputService/HW7";
        assertEquals(InputWatcher.Kind.NONE, w.onState(hdmi3, true, InputWatcher.CONNECTED, 10_000).kind);
        assertEquals(InputWatcher.Kind.NONE, w.onState(hdmi3, true, InputWatcher.STANDBY, 20_000).kind);
        assertEquals(InputWatcher.Kind.SWITCH, w.onState(hdmi3, true, InputWatcher.CONNECTED, 30_000).kind);
    }

    @Test
    public void nonHdmiAndRepeatsAreIgnored() {
        InputWatcher w = booted();
        assertEquals(InputWatcher.Kind.NONE, w.onState(AV, false, InputWatcher.CONNECTED, 10_000).kind);
        w.onState(HDMI2, true, InputWatcher.CONNECTED, 20_000);
        assertEquals(InputWatcher.Kind.NONE, w.onState(HDMI2, true, InputWatcher.CONNECTED, 30_000).kind);   // same state again
    }

    @Test
    public void debounceWithinThreeSeconds() {
        InputWatcher w = booted();
        assertEquals(InputWatcher.Kind.SWITCH, w.onState(HDMI2, true, InputWatcher.CONNECTED, 10_000).kind);
        w.onState(HDMI2, true, InputWatcher.STANDBY, 10_500);           // loose plug: goes home
        assertEquals(InputWatcher.Kind.NONE, w.onState(HDMI2, true, InputWatcher.CONNECTED, 11_000).kind);
        w.onState(HDMI2, true, InputWatcher.STANDBY, 12_000);           // not ours any more: nothing
        assertEquals(InputWatcher.Kind.SWITCH, w.onState(HDMI2, true, InputWatcher.CONNECTED, 13_001).kind);
    }

    @Test
    public void disabledWatcherNeverActs() {
        InputWatcher w = new InputWatcher(false);
        w.seed(HDMI2, InputWatcher.STANDBY);
        assertEquals(InputWatcher.Kind.NONE, w.onState(HDMI2, true, InputWatcher.CONNECTED, 10_000).kind);
        assertNull(w.current());
    }

    @Test
    public void aManualSwitchIsTrackedAndAFailedSwitchForgotten() {
        // The cable was in at boot and the owner picked HDMI 1 from the Inputs menu.
        InputWatcher w = new InputWatcher(true);
        w.seed(HDMI1, InputWatcher.CONNECTED);
        w.seed(HDMI2, InputWatcher.STANDBY);
        w.track(HDMI1);
        assertEquals(InputWatcher.Kind.HOME, w.onState(HDMI1, true, InputWatcher.STANDBY, 10_000).kind);
        w.onState(HDMI2, true, InputWatcher.CONNECTED, 20_000);
        w.switchFailed();
        assertNull(w.current());
        assertEquals(InputWatcher.Kind.NONE, w.onState(HDMI2, true, InputWatcher.STANDBY, 30_000).kind);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew testReleaseUnitTest --tests 'com.gathin.tvhome.InputWatcherTest' -q --console=plain`
Expected: compilation error, `cannot find symbol: class InputWatcher`.

- [ ] **Step 3: Write InputWatcher**

`launcher/app/src/main/java/com/gathin/tvhome/InputWatcher.java`:

```java
package com.gathin.tvhome;

import java.util.HashMap;
import java.util.Map;

/**
 * Decides what a change of an HDMI port's state means: a device plugged in switches the TV to that
 * port, the device going away from the port we switched to brings Home back. Only changes count:
 * the states seeded at boot never trigger anything, and a port is only acted on once its previous
 * state is known. Pure Java; MainActivity feeds it TvInputManager callbacks and runs the actions.
 */
final class InputWatcher {
    /** android.media.tv.TvInputManager.INPUT_STATE_* */
    static final int CONNECTED = 0, STANDBY = 1, DISCONNECTED = 2;
    /** Two switches closer than this are one loose plug. */
    static final long DEBOUNCE_MS = 3000;

    enum Kind { NONE, SWITCH, HOME }

    static final class Action {
        static final Action NONE = new Action(Kind.NONE, null);
        final Kind kind;
        final String inputId;

        private Action(Kind kind, String inputId) {
            this.kind = kind;
            this.inputId = inputId;
        }
    }

    private final boolean enabled;
    private final Map<String, Integer> lastState = new HashMap<>();
    private String current;
    private long lastSwitch = Long.MIN_VALUE / 2;

    InputWatcher(boolean enabled) {
        this.enabled = enabled;
    }

    /** The state at boot, so a cable already in never triggers a switch. */
    void seed(String inputId, int state) {
        lastState.put(inputId, state);
    }

    Action onState(String inputId, boolean hdmi, int state, long nowMillis) {
        if (!enabled || !hdmi || inputId == null) return Action.NONE;
        Integer previous = lastState.put(inputId, state);
        if (previous == null || previous == state) return Action.NONE;
        if (state == CONNECTED) {
            if (nowMillis - lastSwitch < DEBOUNCE_MS) return Action.NONE;
            lastSwitch = nowMillis;
            current = inputId;
            return new Action(Kind.SWITCH, inputId);
        }
        if (inputId.equals(current)) {
            current = null;
            return new Action(Kind.HOME, inputId);
        }
        return Action.NONE;
    }

    /** The owner switched to this port from the Inputs menu: unplugging it should come Home too. */
    void track(String inputId) {
        if (enabled) current = inputId;
    }

    /** The switch intent went nowhere: forget the port so the next plug-in can try again. */
    void switchFailed() {
        current = null;
    }

    /** The port the TV was switched to, or null. */
    String current() {
        return current;
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew testReleaseUnitTest --tests 'com.gathin.tvhome.InputWatcherTest' -q --console=plain`
Expected: no output, exit 0.

- [ ] **Step 5: Commit**

```bash
git add launcher/app/src/main/java/com/gathin/tvhome/InputWatcher.java launcher/app/src/test/java/com/gathin/tvhome/InputWatcherTest.java
git commit -m "Launcher: InputWatcher decides HDMI plug-in switch and unplug return

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 4: StandbyFeature guard

**Files:**
- Create: `launcher/app/src/main/java/com/gathin/tvhome/StandbyFeature.java`
- Create: `launcher/app/src/test/java/com/gathin/tvhome/StandbyFeatureTest.java`

**Interfaces:**
- Produces: `StandbyFeature.run(Step<T> step, T fallback)`, `StandbyFeature.run(Runnable step)`, `StandbyFeature.working()`, `StandbyFeature.reset()`, `StandbyFeature.reporter`. Same shape as `CardFeature`, separate state so a card failure and a standby failure do not switch each other off. `StandbyFeature.TAG = "TvHome.Standby"` is the log tag for the whole feature.

- [ ] **Step 1: Write the failing test**

`launcher/app/src/test/java/com/gathin/tvhome/StandbyFeatureTest.java`:

```java
package com.gathin.tvhome;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The guard that keeps a failing standby feature from taking the home screen down. */
public class StandbyFeatureTest {

    private final List<Throwable> reported = new ArrayList<>();

    @Before
    public void setUp() {
        StandbyFeature.reset();
        StandbyFeature.reporter = reported::add;
    }

    @After
    public void tearDown() {
        StandbyFeature.reset();
    }

    @Test
    public void aWorkingStepRuns() {
        assertEquals("ok", StandbyFeature.run(() -> "ok", "fallback"));
        int[] ran = {0};
        StandbyFeature.run(() -> { ran[0]++; });
        assertEquals(1, ran[0]);
        assertTrue(StandbyFeature.working());
        assertTrue(reported.isEmpty());
    }

    @Test
    public void theFirstFailureSwitchesTheFeatureOff() {
        assertEquals("fallback", StandbyFeature.run(() -> {
            throw new IllegalStateException("no view");
        }, "fallback"));
        assertFalse(StandbyFeature.working());
        assertEquals(1, reported.size());
        int[] ran = {0};
        StandbyFeature.run(() -> { ran[0]++; });
        assertEquals(0, ran[0]);                 // skipped from now on
        assertEquals(1, reported.size());        // and logged only once
    }

    @Test
    public void independentOfTheCardGuard() {
        CardFeature.reset();
        // Two-argument form: a lambda that only throws fits both overloads, so name the one meant.
        StandbyFeature.run(() -> {
            throw new RuntimeException();
        }, null);
        assertTrue(CardFeature.working());
        CardFeature.reset();
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew testReleaseUnitTest --tests 'com.gathin.tvhome.StandbyFeatureTest' -q --console=plain`
Expected: compilation error, `cannot find symbol: class StandbyFeature`.

- [ ] **Step 3: Write StandbyFeature**

`launcher/app/src/main/java/com/gathin/tvhome/StandbyFeature.java`:

```java
package com.gathin.tvhome;

import android.util.Log;

/**
 * The guard around the meeting-room standby feature (StandbyView, StandbyFeed, InputWatcher): every
 * call from the home screen into it goes through {@link #run}. The first failure that is safe to
 * catch is logged once and switches the feature off for the rest of the process, so Home keeps
 * working exactly as with standby and hdmi_auto_switch false. Same shape as {@link CardFeature},
 * with its own state. Out of memory and other virtual machine errors are not caught.
 */
final class StandbyFeature {
    static final String TAG = "TvHome.Standby";

    interface Step<T> {
        T run() throws Exception;
    }

    interface Reporter {
        void failed(Throwable t);
    }

    private static boolean broken;
    static Reporter reporter = t -> Log.w(TAG, "standby switched off for this session: " + t);

    private StandbyFeature() {
    }

    static boolean working() {
        return !broken;
    }

    static <T> T run(Step<T> step, T fallback) {
        if (broken) return fallback;
        try {
            return step.run();
        } catch (Exception | LinkageError e) {
            broken = true;
            try {
                reporter.failed(e);
            } catch (Exception ignored) {
            }
            return fallback;
        }
    }

    /** For void steps. A lambda that only throws, or whose body is an expression, fits both overloads: give those a block body with a statement, or use the two-argument form. */
    static void run(Runnable step) {
        run(() -> {
            step.run();
            return null;
        }, null);
    }

    /** For tests only: forget a failure. */
    static void reset() {
        broken = false;
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew testReleaseUnitTest --tests 'com.gathin.tvhome.StandbyFeatureTest' -q --console=plain`
Expected: no output, exit 0.

- [ ] **Step 5: Commit**

```bash
git add launcher/app/src/main/java/com/gathin/tvhome/StandbyFeature.java launcher/app/src/test/java/com/gathin/tvhome/StandbyFeatureTest.java
git commit -m "Launcher: StandbyFeature guard, one failure switches standby off for the session

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 5: StandbyView overlay with idle timer and key consumption

**Files:**
- Create: `launcher/app/src/main/res/layout/standby.xml`
- Create: `launcher/app/src/main/res/drawable/hdmi_badge.xml`
- Create: `launcher/app/src/main/res/drawable/standby_bar.xml`
- Create: `launcher/app/src/main/java/com/gathin/tvhome/StandbyView.java`
- Modify: `launcher/app/src/main/res/values/strings.xml`, `launcher/app/src/main/res/values-zh-rCN/strings.xml`
- Modify: `launcher/app/src/main/res/layout/activity_main.xml` (append before `</FrameLayout>`)
- Modify: `launcher/app/src/main/java/com/gathin/tvhome/MainActivity.java` (field, `onCreate`, `onResume`, `onPause`, new `dispatchKeyEvent` and `onUserInteraction`)

**Interfaces:**
- Consumes: `Config.standby` (Task 1), `StandbyData.StandbySnapshot` (Task 2), `StandbyFeature` (Task 4).
- Produces: `StandbyView` with `void configure(StandbyConfig cfg)`, `void touch()`, `boolean consumeKey(KeyEvent e)`, `void cancel()`, `boolean isShowing()`, `void render(StandbyData.StandbySnapshot s)`, `void showHdmiToast(String label, Runnable then)`.

- [ ] **Step 1: Strings**

Append inside `<resources>` of `launcher/app/src/main/res/values/strings.xml`:

```xml
    <!-- Meeting-room standby screen -->
    <string name="standby_date_pattern">MMMM d</string>
    <string name="standby_weekday_pattern" translatable="false">EEEE</string>
    <string name="standby_hdmi_hint">Plug in HDMI to share</string>
    <string name="standby_hdmi_badge" translatable="false">HDMI</string>
    <string name="standby_hdmi_switching">%1$s detected, switching…</string>
    <string name="standby_hdmi_unplug">Unplug the cable to come back here</string>
    <string name="standby_age_now">just now</string>
    <string name="standby_age_minutes">%1$d min ago</string>
    <string name="standby_age_hours">%1$d h ago</string>
    <string name="standby_age_days">%1$d d ago</string>
    <string name="weather_clear">Clear</string>
    <string name="weather_cloudy">Cloudy</string>
    <string name="weather_overcast">Overcast</string>
    <string name="weather_fog">Fog</string>
    <string name="weather_rain">Rain</string>
    <string name="weather_snow">Snow</string>
    <string name="weather_thunder">Thunderstorm</string>
```

Append inside `<resources>` of `launcher/app/src/main/res/values-zh-rCN/strings.xml`:

```xml
    <!-- 会议室待机屏 -->
    <string name="standby_date_pattern">M月d日</string>
    <string name="standby_hdmi_hint">插线即投屏</string>
    <string name="standby_hdmi_switching">检测到 %1$s，正在切换…</string>
    <string name="standby_hdmi_unplug">拔掉线缆会自动回到待机屏</string>
    <string name="standby_age_now">刚刚</string>
    <string name="standby_age_minutes">%1$d 分钟前</string>
    <string name="standby_age_hours">%1$d 小时前</string>
    <string name="standby_age_days">%1$d 天前</string>
    <string name="weather_clear">晴</string>
    <string name="weather_cloudy">多云</string>
    <string name="weather_overcast">阴</string>
    <string name="weather_fog">雾</string>
    <string name="weather_rain">雨</string>
    <string name="weather_snow">雪</string>
    <string name="weather_thunder">雷雨</string>
```

(`translatable="false"` strings live only in `values/`. `standby_date_pattern` is a translatable date pattern on purpose: CI runs `lintRelease`, which rejects a locale override of a non-translatable string.)

- [ ] **Step 2: Drawables**

`launcher/app/src/main/res/drawable/hdmi_badge.xml` (the outlined HDMI badge of the mockup):

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="#00000000" />
    <stroke android:width="1dp" android:color="#66FFFFFF" />
    <corners android:radius="5dp" />
</shape>
```

`launcher/app/src/main/res/drawable/standby_bar.xml` (the bottom panel; `glass_panel` has 20dp corners, the bar wants 14dp):

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <gradient android:angle="270" android:startColor="#F22A2A2E" android:endColor="#F5161618" />
    <stroke android:width="1dp" android:color="#38FFFFFF" />
    <corners android:radius="14dp" />
</shape>
```

- [ ] **Step 3: Layout**

`launcher/app/src/main/res/layout/standby.xml` (merged into `StandbyView`, a `FrameLayout`; the TV is 1080p at density 2, so every mockup pixel is half a dp):

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Meeting-room standby screen (StandbyView): the layer that fades in over Home after a while
     without key presses, and the HDMI toast. Mockups: designs/meeting-room-screen/. -->
<merge xmlns:android="http://schemas.android.com/apk/res/android">

    <FrameLayout
        android:id="@+id/standbyLayer"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:background="#FF000000"
        android:visibility="gone"
        android:alpha="0">

        <LinearLayout
            android:id="@+id/standbyContent"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="88dp"
            android:orientation="vertical"
            android:gravity="center_horizontal">

            <TextClock
                android:id="@+id/standbyClock"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:format12Hour="h:mm"
                android:format24Hour="HH:mm"
                android:textColor="#FFFFFFFF"
                android:textSize="220sp"
                android:fontFamily="@font/outfit_extralight"
                android:letterSpacing="-0.03"
                android:includeFontPadding="false" />

            <LinearLayout
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="14dp"
                android:orientation="horizontal"
                android:gravity="center_vertical">

                <TextClock
                    android:id="@+id/standbyDate"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:format12Hour="@string/standby_date_pattern"
                    android:format24Hour="@string/standby_date_pattern"
                    android:textColor="#B3FFFFFF"
                    android:textSize="21sp"
                    android:fontFamily="@font/outfit_light" />

                <View
                    android:layout_width="1dp"
                    android:layout_height="17dp"
                    android:layout_marginStart="14dp"
                    android:layout_marginEnd="14dp"
                    android:background="#40FFFFFF" />

                <TextClock
                    android:id="@+id/standbyWeekday"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:format12Hour="@string/standby_weekday_pattern"
                    android:format24Hour="@string/standby_weekday_pattern"
                    android:textColor="#B3FFFFFF"
                    android:textSize="21sp"
                    android:fontFamily="@font/outfit_light" />

                <View
                    android:id="@+id/standbyWeatherSep"
                    android:layout_width="1dp"
                    android:layout_height="17dp"
                    android:layout_marginStart="14dp"
                    android:layout_marginEnd="14dp"
                    android:background="#40FFFFFF"
                    android:visibility="gone" />

                <TextView
                    android:id="@+id/standbyWeather"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:textColor="#B3FFFFFF"
                    android:textSize="21sp"
                    android:fontFamily="@font/outfit_light"
                    android:visibility="gone" />
            </LinearLayout>
        </LinearLayout>

        <!-- Status bar: the HDMI hint, then one cell per CI project (added in code). -->
        <LinearLayout
            android:id="@+id/standbyBar"
            android:layout_width="match_parent"
            android:layout_height="55dp"
            android:layout_gravity="bottom"
            android:layout_marginStart="48dp"
            android:layout_marginEnd="48dp"
            android:layout_marginBottom="32dp"
            android:paddingStart="10dp"
            android:paddingEnd="10dp"
            android:orientation="horizontal"
            android:gravity="center_vertical"
            android:background="@drawable/standby_bar">

            <LinearLayout
                android:layout_width="0dp"
                android:layout_height="match_parent"
                android:layout_weight="1"
                android:orientation="horizontal"
                android:gravity="center_vertical"
                android:paddingStart="14dp"
                android:paddingEnd="14dp">

                <TextView
                    android:layout_width="20dp"
                    android:layout_height="20dp"
                    android:gravity="center"
                    android:background="@drawable/hdmi_badge"
                    android:text="@string/standby_hdmi_badge"
                    android:textColor="#8CFFFFFF"
                    android:textSize="7sp"
                    android:fontFamily="@font/outfit_medium"
                    android:letterSpacing="0.05" />

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginStart="9dp"
                    android:text="@string/standby_hdmi_hint"
                    android:textColor="#E6FFFFFF"
                    android:textSize="13sp" />
            </LinearLayout>
        </LinearLayout>
    </FrameLayout>

    <!-- HDMI toast: "HDMI 2 detected, switching…" with a 1.5 s progress line. -->
    <LinearLayout
        android:id="@+id/standbyToast"
        android:layout_width="410dp"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:padding="28dp"
        android:background="@drawable/glass_panel"
        android:visibility="gone"
        android:alpha="0">

        <TextView
            android:id="@+id/standbyToastBadge"
            android:layout_width="55dp"
            android:layout_height="55dp"
            android:gravity="center"
            android:background="@drawable/hdmi_badge"
            android:textColor="#FFFFFFFF"
            android:textSize="11sp"
            android:fontFamily="@font/outfit_medium"
            android:letterSpacing="0.06" />

        <LinearLayout
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:layout_marginStart="20dp"
            android:orientation="vertical">

            <TextView
                android:id="@+id/standbyToastTitle"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:textColor="#FFFFFFFF"
                android:textSize="21sp" />

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="5dp"
                android:text="@string/standby_hdmi_unplug"
                android:textColor="#8CFFFFFF"
                android:textSize="13sp" />

            <FrameLayout
                android:layout_width="match_parent"
                android:layout_height="3dp"
                android:layout_marginTop="13dp"
                android:background="#1FFFFFFF">

                <View
                    android:id="@+id/standbyToastProgress"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    android:background="#E6FFFFFF"
                    android:scaleX="0" />
            </FrameLayout>
        </LinearLayout>
    </LinearLayout>
</merge>
```

The progress line grows from the left: its pivot is set in code (`toastProgress.setPivotX(0)`).

- [ ] **Step 4: Write StandbyView**

`launcher/app/src/main/java/com/gathin/tvhome/StandbyView.java`:

```java
package com.gathin.tvhome;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;
import java.util.Random;

/**
 * The meeting-room standby screen: a clock, the date and weather, and a status bar, that fades in
 * over Home after {@link StandbyConfig#idleSeconds} without a key press and fades out on the next
 * key, which it swallows. Also hosts the HDMI toast shown just before an automatic input switch.
 * MainActivity forwards keys, user interaction and its lifecycle; every call goes through
 * {@link StandbyFeature#run} there, so a failure here switches the feature off, never Home.
 */
public class StandbyView extends FrameLayout {
    private static final long FADE_IN_MS = 600, FADE_OUT_MS = 300, TOAST_MS = 1500, MINUTE_MS = 60_000;
    private static final int MAX_CI_CELLS = 4;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private View layer, content, toast, toastProgress;
    private TextView weather, weatherSep, toastBadge, toastTitle;
    private LinearLayout bar;
    private long idleMs;
    private boolean enabled;
    private boolean showing;
    /** Set while the layer is in its fade-out: the key that woke Home is consumed on key up too. */
    private boolean swallowingKey;
    private StandbyData.StandbySnapshot snapshot = StandbyData.StandbySnapshot.EMPTY;
    private float density;

    private final Runnable showAfterIdle = this::show;
    private final Runnable minuteTick = new Runnable() {
        @Override
        public void run() {
            if (!showing) return;
            driftContent();
            renderBar();
            handler.postDelayed(this, MINUTE_MS);
        }
    };

    public StandbyView(Context context, AttributeSet attrs) {
        super(context, attrs);
        LayoutInflater.from(context).inflate(R.layout.standby, this, true);
        density = getResources().getDisplayMetrics().density;
        layer = findViewById(R.id.standbyLayer);
        content = findViewById(R.id.standbyContent);
        weather = findViewById(R.id.standbyWeather);
        weatherSep = findViewById(R.id.standbyWeatherSep);
        bar = findViewById(R.id.standbyBar);
        toast = findViewById(R.id.standbyToast);
        toastBadge = findViewById(R.id.standbyToastBadge);
        toastTitle = findViewById(R.id.standbyToastTitle);
        toastProgress = findViewById(R.id.standbyToastProgress);
        toastProgress.setPivotX(0);
        setVisibility(GONE);
    }

    void configure(StandbyConfig cfg) {
        enabled = cfg.enabled;
        idleMs = cfg.idleSeconds * 1000L;
    }

    /** A key press or other interaction on Home: the idle clock starts over. */
    void touch() {
        if (!enabled || showing) return;
        handler.removeCallbacks(showAfterIdle);
        handler.postDelayed(showAfterIdle, idleMs);
    }

    /** Home is leaving the screen: nothing of ours stays up, and no fade-in while another app is in front. */
    void cancel() {
        handler.removeCallbacks(showAfterIdle);
        handler.removeCallbacks(minuteTick);
        if (showing) {
            showing = false;
            layer.animate().cancel();
            layer.setAlpha(0f);
            layer.setVisibility(GONE);
        }
        if (toast.getVisibility() != VISIBLE) setVisibility(GONE);
    }

    boolean isShowing() {
        return showing;
    }

    /**
     * Keys while the standby layer is up: the first one hides it and is consumed, as is its key-up
     * and any repeat, so the launcher underneath never sees it.
     */
    boolean consumeKey(KeyEvent event) {
        if (showing) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                swallowingKey = true;
                hide();
            }
            return true;
        }
        if (swallowingKey) {
            if (event.getAction() == KeyEvent.ACTION_UP) swallowingKey = false;
            return true;
        }
        return false;
    }

    void render(StandbyData.StandbySnapshot s) {
        snapshot = s == null ? StandbyData.StandbySnapshot.EMPTY : s;
        StandbyData.WeatherInfo w = snapshot.weather;
        if (w == null) {
            weather.setVisibility(GONE);
            weatherSep.setVisibility(GONE);
        } else {
            String label = w.text != null ? w.text : weatherText(w.kind);
            weather.setText(label.isEmpty() ? String.format(Locale.getDefault(), "%d°", Math.round(w.tempC))
                    : String.format(Locale.getDefault(), "%s %d°", label, Math.round(w.tempC)));
            weather.setVisibility(VISIBLE);
            weatherSep.setVisibility(VISIBLE);
        }
        renderBar();
    }

    /** "HDMI 2 detected, switching…" for 1.5 s over whatever is on screen, then runs the switch. */
    void showHdmiToast(String label, Runnable then) {
        toastBadge.setText(label);
        toastTitle.setText(getContext().getString(R.string.standby_hdmi_switching, label));
        setVisibility(VISIBLE);
        toast.setVisibility(VISIBLE);
        toast.setAlpha(0f);
        toast.animate().alpha(1f).setDuration(200).start();
        toastProgress.setScaleX(0f);
        toastProgress.animate().scaleX(1f).setDuration(TOAST_MS).start();
        handler.postDelayed(() -> {
            toast.animate().alpha(0f).setDuration(200).withEndAction(() -> {
                toast.setVisibility(GONE);
                if (!showing) setVisibility(GONE);
            }).start();
            then.run();
        }, TOAST_MS);
    }

    private void show() {
        if (!enabled || showing) return;
        showing = true;
        setVisibility(VISIBLE);
        layer.setAlpha(0f);
        layer.setVisibility(VISIBLE);
        content.setTranslationX(0f);
        content.setTranslationY(0f);
        renderBar();
        layer.animate().alpha(1f).setDuration(FADE_IN_MS).start();
        handler.postDelayed(minuteTick, MINUTE_MS);
    }

    private void hide() {
        if (!showing) return;
        showing = false;
        handler.removeCallbacks(minuteTick);
        layer.animate().alpha(0f).setDuration(FADE_OUT_MS).withEndAction(() -> {
            layer.setVisibility(GONE);
            if (toast.getVisibility() != VISIBLE) setVisibility(GONE);
            touch();   // the idle clock starts again from the key that woke Home
        }).start();
    }

    /** A small random shift once a minute so the clock never burns in. */
    private void driftContent() {
        float range = 24 * density;
        content.setTranslationX((random.nextFloat() * 2 - 1) * range);
        content.setTranslationY((random.nextFloat() * 2 - 1) * range);
    }

    /** The status bar: the fixed HDMI cell, then up to MAX_CI_CELLS CI cells from the snapshot. */
    private void renderBar() {
        while (bar.getChildCount() > 1) bar.removeViewAt(bar.getChildCount() - 1);
        long now = System.currentTimeMillis();
        int n = 0;
        for (StandbyData.CiInfo ci : snapshot.ci) {
            if (n++ >= MAX_CI_CELLS) break;
            bar.addView(ciCell(ci, now));
        }
    }

    private View ciCell(StandbyData.CiInfo ci, long now) {
        Context ctx = getContext();
        LinearLayout cell = new LinearLayout(ctx);
        cell.setOrientation(LinearLayout.HORIZONTAL);
        cell.setGravity(android.view.Gravity.CENTER_VERTICAL);
        int pad = (int) (14 * density);
        cell.setPadding(pad, 0, pad, 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        cell.setLayoutParams(lp);

        View divider = new View(ctx);
        divider.setBackgroundColor(0x40FFFFFF);
        cell.addView(divider, new LinearLayout.LayoutParams(1, (int) (20 * density)));

        View dot = new View(ctx);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(statusColor(ci.status));
        dot.setBackground(d);
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams((int) (7 * density), (int) (7 * density));
        dp.setMarginStart(pad);
        cell.addView(dot, dp);

        TextView name = new TextView(ctx);
        name.setText(ci.name);
        name.setTextColor(0xE6FFFFFF);
        name.setTextSize(13f);
        name.setSingleLine(true);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        np.setMarginStart((int) (9 * density));
        cell.addView(name, np);

        if (ci.updatedAtMillis > 0) {
            TextView age = new TextView(ctx);
            age.setText(ageText(StandbyData.age(ci.updatedAtMillis, now)));
            age.setTextColor(0x8CFFFFFF);
            age.setTextSize(11f);
            age.setSingleLine(true);
            LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            ap.setMarginStart((int) (8 * density));
            cell.addView(age, ap);
        }
        return cell;
    }

    private static int statusColor(StandbyData.CiStatus s) {
        switch (s) {
            case SUCCESS: return 0xFF4CD964;
            case FAILED: return 0xFFFF5A5A;
            case RUNNING: return 0xFFFFB340;
            default: return 0x66FFFFFF;
        }
    }

    private String ageText(StandbyData.Age a) {
        Context ctx = getContext();
        switch (a.unit) {
            case StandbyData.Age.MINUTES: return ctx.getString(R.string.standby_age_minutes, a.count);
            case StandbyData.Age.HOURS: return ctx.getString(R.string.standby_age_hours, a.count);
            case StandbyData.Age.DAYS: return ctx.getString(R.string.standby_age_days, a.count);
            default: return ctx.getString(R.string.standby_age_now);
        }
    }

    private String weatherText(StandbyData.WeatherKind k) {
        Context ctx = getContext();
        switch (k) {
            case CLEAR: return ctx.getString(R.string.weather_clear);
            case CLOUDY: return ctx.getString(R.string.weather_cloudy);
            case OVERCAST: return ctx.getString(R.string.weather_overcast);
            case FOG: return ctx.getString(R.string.weather_fog);
            case RAIN: return ctx.getString(R.string.weather_rain);
            case SNOW: return ctx.getString(R.string.weather_snow);
            case THUNDER: return ctx.getString(R.string.weather_thunder);
            default: return "";
        }
    }
}
```

- [ ] **Step 5: Add the view to activity_main.xml**

In `launcher/app/src/main/res/layout/activity_main.xml`, after the `GlassView` element and before the closing `</FrameLayout>`:

```xml
    <!-- Meeting-room standby screen and HDMI toast, over everything (config.xml standby / hdmi_auto_switch). -->
    <com.gathin.tvhome.StandbyView
        android:id="@+id/standby"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:visibility="gone"
        android:importantForAccessibility="no" />
```

- [ ] **Step 6: Hook it into MainActivity**

In `MainActivity.java`:

Field, next to `private SkyView sky;`:

```java
    /** Meeting-room standby screen (config.xml standby); null-safe through StandbyFeature. */
    private StandbyView standby;
    private StandbyConfig standbyConfig = StandbyConfig.from(false, 30, "", "", "", "", java.util.Collections.emptyList(), "", false);
```

In `onCreate`, right after `sky.setIntensity(cfg.skyIntensity);`:

```java
        standbyConfig = cfg.standby;
        standby = findViewById(R.id.standby);
        StandbyFeature.run(() -> standby.configure(standbyConfig));
```

In `onResume`, after `handler.postDelayed(minuteTick, MINUTE_MS);`:

```java
        StandbyFeature.run(() -> standby.touch());
```

In `onPause`, after `handler.removeCallbacks(minuteTick);`:

```java
        StandbyFeature.run(() -> standby.cancel());
```

Add two overrides next to `onBackPressed`:

```java
    /** Any remote key: the standby screen either resets its idle clock or, when it is up, swallows the key. */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        boolean consumed = StandbyFeature.run(() -> standby.consumeKey(event), false);
        if (consumed) return true;
        StandbyFeature.run(() -> standby.touch());
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        StandbyFeature.run(() -> standby.touch());
    }
```

- [ ] **Step 7: Build, install and verify on the TV**

```bash
cd launcher && ./gradlew testReleaseUnitTest -q --console=plain && TV_HOST=192.168.2.2 STOCK_LAUNCHER=none ./build-and-install.sh
```

Expected: tests quiet, install prints `Success`, `Status: ok`, `Home now resolves to com.gathin.tvhome/.MainActivity`.

Push a temporary theme to turn it on (the real theme comes in Task 8): write `/tmp/standby-test/config.json`:

```json
{ "standby": true, "standby_idle_seconds": 10, "extra_apps": ["com.xiaomi.mitv.tvplayer/.ExternalSourceActivity", "com.xiaomi.mitv.appstore/.MainActivity", "com.xiaomi.mitv.mediaexplorer/.NewScraperMainEntryActivity"] }
```

then `TV_HOST=192.168.2.2 tools/theme.sh /tmp/standby-test`. Wait 12 s, screenshot: the clock, date, weekday and the bar with only the HDMI cell on black. Send `tools/tv.py 'input keyevent 20'` (Down): screenshot shows the normal launcher and the focused tile has **not** moved (the key was swallowed). Wait 12 s again: standby is back. `logcat -d | grep TvHome.Standby` must be empty.

- [ ] **Step 8: Commit**

```bash
git add launcher/app/src/main/res/layout/standby.xml launcher/app/src/main/res/drawable/hdmi_badge.xml launcher/app/src/main/res/drawable/standby_bar.xml launcher/app/src/main/java/com/gathin/tvhome/StandbyView.java launcher/app/src/main/res/values/strings.xml launcher/app/src/main/res/values-zh-rCN/strings.xml launcher/app/src/main/res/layout/activity_main.xml launcher/app/src/main/java/com/gathin/tvhome/MainActivity.java
git commit -m "Launcher: standby screen overlay after idle, keys wake Home and are swallowed

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

(`MainActivity.java` also carries the earlier uncommitted Xiaomi input_map changes; committing them here is fine.)

---

### Task 6: StandbyFeed network polling

**Files:**
- Create: `launcher/app/src/main/java/com/gathin/tvhome/StandbyFeed.java`
- Modify: `launcher/app/src/main/AndroidManifest.xml` (permissions)
- Modify: `launcher/app/src/main/java/com/gathin/tvhome/MainActivity.java` (`onCreate`, `onDestroy`)

**Interfaces:**
- Consumes: `StandbyConfig` (Task 1), `StandbyData.*` parsers (Task 2), `StandbyView.render` (Task 5).
- Produces: `StandbyFeed(StandbyConfig cfg, Handler main, Listener listener)`, `void start()`, `void stop()`; `Listener.onSnapshot(StandbyData.StandbySnapshot)` is called on the main thread after every successful fetch.

- [ ] **Step 1: Permission**

In `launcher/app/src/main/AndroidManifest.xml`, after the `ACCESS_WIFI_STATE` permission line:

```xml
    <!-- Meeting-room standby screen only: weather and CI status (config.xml weather_*, gitlab_*, feed_url). -->
    <uses-permission android:name="android.permission.INTERNET" />
```

- [ ] **Step 2: Write StandbyFeed**

`launcher/app/src/main/java/com/gathin/tvhome/StandbyFeed.java`:

```java
package com.gathin.tvhome;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Fetches what the standby screen shows, on one background thread, at a fixed cadence: the feed JSON
 * every minute when feed_url is set, otherwise Open-Meteo every 30 minutes and each GitLab project's
 * latest pipeline every 5 minutes. A failed fetch keeps the previous value and waits for the next
 * turn; there are no retries in between. Results reach the listener on the main thread.
 */
final class StandbyFeed {
    interface Listener {
        void onSnapshot(StandbyData.StandbySnapshot snapshot);
    }

    static final long FEED_MS = 60_000L, GITLAB_MS = 5 * 60_000L, WEATHER_MS = 30 * 60_000L;
    private static final int TIMEOUT_MS = 10_000, MAX_BYTES = 256 * 1024;

    private final StandbyConfig cfg;
    private final Handler main;
    private final Listener listener;
    private HandlerThread thread;
    private Handler worker;
    private StandbyData.WeatherInfo weather;
    private List<StandbyData.CiInfo> ci = new ArrayList<>();

    private final Runnable fetchFeed = new Runnable() {
        @Override
        public void run() {
            StandbyData.StandbySnapshot s = StandbyData.parseFeed(get(cfg.feedUrl, null));
            if (s != null) publish(s);
            else Log.w(StandbyFeature.TAG, "feed not updated: " + cfg.feedUrl);
            worker.postDelayed(this, FEED_MS);
        }
    };

    private final Runnable fetchWeather = new Runnable() {
        @Override
        public void run() {
            String url = String.format(Locale.ROOT,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code",
                    cfg.weatherLat, cfg.weatherLon);
            StandbyData.WeatherInfo w = StandbyData.parseOpenMeteo(get(url, null));
            if (w != null) {
                weather = w;
                publish(new StandbyData.StandbySnapshot(weather, ci));
            } else {
                Log.w(StandbyFeature.TAG, "weather not updated");
            }
            worker.postDelayed(this, WEATHER_MS);
        }
    };

    private final Runnable fetchGitlab = new Runnable() {
        @Override
        public void run() {
            List<StandbyData.CiInfo> fresh = new ArrayList<>();
            boolean anyFailed = false;
            for (String project : cfg.gitlabProjects) {
                String url;
                try {
                    url = cfg.gitlabUrl + "/api/v4/projects/" + URLEncoder.encode(project, "UTF-8") + "/pipelines?per_page=1";
                } catch (Exception e) {
                    continue;
                }
                StandbyData.CiInfo info = StandbyData.parseGitlabPipelines(StandbyData.projectName(project), get(url, cfg.gitlabToken));
                if (info != null) {
                    fresh.add(info);
                } else {
                    anyFailed = true;
                    StandbyData.CiInfo old = previous(StandbyData.projectName(project));
                    if (old != null) fresh.add(old);
                }
            }
            if (anyFailed) Log.w(StandbyFeature.TAG, "gitlab: some projects not updated");
            ci = fresh;
            publish(new StandbyData.StandbySnapshot(weather, ci));
            worker.postDelayed(this, GITLAB_MS);
        }
    };

    StandbyFeed(StandbyConfig cfg, Handler main, Listener listener) {
        this.cfg = cfg;
        this.main = main;
        this.listener = listener;
    }

    /** Starts polling; a second call is a no-op. Nothing happens when the config has no sources. */
    void start() {
        if (worker != null || !cfg.hasSources()) return;
        thread = new HandlerThread("standby-feed");
        thread.start();
        worker = new Handler(thread.getLooper());
        if (cfg.hasFeed()) {
            worker.post(fetchFeed);
        } else {
            if (cfg.hasWeather()) worker.post(fetchWeather);
            if (cfg.hasGitlab()) worker.post(fetchGitlab);
        }
    }

    void stop() {
        if (worker == null) return;
        worker.removeCallbacksAndMessages(null);
        thread.quitSafely();
        thread = null;
        worker = null;
    }

    private StandbyData.CiInfo previous(String name) {
        for (StandbyData.CiInfo c : ci) if (c.name.equals(name)) return c;
        return null;
    }

    private void publish(StandbyData.StandbySnapshot s) {
        main.post(() -> listener.onSnapshot(s));
    }

    /** The body of a 2xx GET as UTF-8, at most MAX_BYTES, or null for anything else. Never throws. */
    static String get(String url, String privateToken) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(TIMEOUT_MS);
            c.setReadTimeout(TIMEOUT_MS);
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("User-Agent", "cleanyourtv-launcher");
            if (privateToken != null && !privateToken.isEmpty()) c.setRequestProperty("PRIVATE-TOKEN", privateToken);
            int code = c.getResponseCode();
            if (code < 200 || code > 299) {
                Log.w(StandbyFeature.TAG, "HTTP " + code + " from " + url);
                return null;
            }
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    if (out.size() > MAX_BYTES) return null;
                }
                return out.toString(StandardCharsets.UTF_8.name());
            }
        } catch (Exception e) {
            Log.w(StandbyFeature.TAG, "fetch failed: " + url + ": " + e);
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }
}
```

- [ ] **Step 3: Wire it into MainActivity**

Field next to `private StandbyView standby;`:

```java
    private StandbyFeed standbyFeed;
```

In `onCreate`, after the `StandbyFeature.run(() -> standby.configure(standbyConfig));` line:

```java
        if (standbyConfig.enabled) {
            StandbyFeature.run(() -> {
                standbyFeed = new StandbyFeed(standbyConfig, handler, s -> StandbyFeature.run(() -> standby.render(s)));
                standbyFeed.start();
            });
        }
```

In `onDestroy`, as the first statement after `super.onDestroy();`:

```java
        if (standbyFeed != null) StandbyFeature.run(() -> standbyFeed.stop());
```

- [ ] **Step 4: Build, install and verify on the TV**

```bash
cd launcher && ./gradlew testReleaseUnitTest -q --console=plain && TV_HOST=192.168.2.2 STOCK_LAUNCHER=none ./build-and-install.sh
```

Theme for the test, `/tmp/standby-test/config.json` (keep the `extra_apps` line from Task 5; the Mac's Internet Sharing must be passing traffic for the weather, check with `tools/tv.py 'ping -c1 -W2 api.open-meteo.com'` first):

```json
{ "standby": true, "standby_idle_seconds": 10, "weather_lat": 31.30, "weather_lon": 120.58,
  "gitlab_url": "https://git.17usoft.com", "gitlab_token": "REPLACE_ME", "gitlab_projects": ["team/placeholder-one"],
  "extra_apps": ["com.xiaomi.mitv.tvplayer/.ExternalSourceActivity", "com.xiaomi.mitv.appstore/.MainActivity", "com.xiaomi.mitv.mediaexplorer/.NewScraperMainEntryActivity"] }
```

`TV_HOST=192.168.2.2 tools/theme.sh /tmp/standby-test`, wait 15 s, screenshot: the date line ends with the weather (e.g. `多云 21°`); the bar has the HDMI cell only (token is the placeholder, so GitLab is off). `logcat -d | grep TvHome.Standby` shows no `switched off` line (a `fetch failed` line is fine when the TV has no internet, and then the date line has no weather).

If the user provides a real token: put it in the JSON, push again; the bar shows one cell per project with a dot and `x 分钟前`.

- [ ] **Step 5: Commit**

```bash
git add launcher/app/src/main/java/com/gathin/tvhome/StandbyFeed.java launcher/app/src/main/AndroidManifest.xml launcher/app/src/main/java/com/gathin/tvhome/MainActivity.java
git commit -m "Launcher: StandbyFeed polls Open-Meteo, GitLab pipelines or a feed JSON for the standby screen

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 7: HDMI auto-switch wiring

**Files:**
- Modify: `launcher/app/src/main/java/com/gathin/tvhome/MainActivity.java` (`inputCallback`, `onCreate`, `openInput`, new helpers)

**Interfaces:**
- Consumes: `InputWatcher` (Task 3), `StandbyView.showHdmiToast` (Task 5), existing `openInput(String)` and `TvInputManager` callback.

- [ ] **Step 1: Seed the watcher in onCreate**

Field next to `private StandbyFeed standbyFeed;`:

```java
    /** HDMI auto-switch (config.xml hdmi_auto_switch); fed by inputCallback. */
    private InputWatcher inputWatcher = new InputWatcher(false);
```

In `onCreate`, right after the existing `if (tim != null) tim.registerCallback(inputCallback, handler);` line:

```java
        inputWatcher = new InputWatcher(standbyConfig.hdmiAutoSwitch);
        if (tim != null && standbyConfig.hdmiAutoSwitch) {
            StandbyFeature.run(() -> {
                for (TvInputInfo info : tim.getTvInputList()) {
                    if (info.isPassthroughInput() && info.getType() == TvInputInfo.TYPE_HDMI) {
                        inputWatcher.seed(info.getId(), tim.getInputState(info.getId()));
                    }
                }
            });
        }
```

- [ ] **Step 2: React to state changes in inputCallback**

Extend the existing `inputCallback` (the anonymous `TvInputManager.TvInputCallback` near line 166) with:

```java
        @Override
        public void onInputStateChanged(String inputId, int state) {
            Log.d(StandbyFeature.TAG, "input " + inputId + " state " + state);
            StandbyFeature.run(() -> onInputState(inputId, state));
        }
```

(`import android.util.Log;` is added in Step 3.)

- [ ] **Step 3: The helpers**

Add after `openInput(...)`:

```java
    /** An HDMI port changed state: plug-in switches to it after the toast, unplug of our port returns Home. */
    private void onInputState(String inputId, int state) {
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (tim == null) return;
        TvInputInfo info = tim.getTvInputInfo(inputId);
        boolean hdmi = info != null && info.isPassthroughInput() && info.getType() == TvInputInfo.TYPE_HDMI;
        InputWatcher.Action action = inputWatcher.onState(inputId, hdmi, state, SystemClock.elapsedRealtime());
        if (action.kind == InputWatcher.Kind.SWITCH) {
            CharSequence l = info.loadLabel(this);
            String label = l == null ? "HDMI" : l.toString();
            if (!label.toUpperCase(Locale.ROOT).contains("HDMI")) label = "HDMI " + label;
            standby.showHdmiToast(label, () -> StandbyFeature.run(() -> {
                if (!openInputChecked(action.inputId)) inputWatcher.switchFailed();
            }));
        } else if (action.kind == InputWatcher.Kind.HOME) {
            startActivity(new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        }
    }

    /** openInput, reporting whether something handled the switch. */
    private boolean openInputChecked(String inputId) {
        Uri uri = TvContract.buildChannelUriForPassthroughInput(inputId);
        Intent view = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "vnd.android.cursor.item/channel")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (view.resolveActivity(getPackageManager()) != null) {
            openInput(inputId);
            return true;
        }
        if (inputAction.isEmpty() || mappedInput(inputId) == null) return false;
        openInput(inputId);
        return true;
    }
```

Add to the import block of `MainActivity.java` (neither is imported yet; `Locale`, `TvInputInfo`, `TvContract` and `Uri` already are):

```java
import android.os.SystemClock;
import android.util.Log;
```

In `openInput(String inputId)`, add as the first line so a manual choice from the Inputs menu is tracked too:

```java
        inputWatcher.track(inputId);
```

- [ ] **Step 4: Build, install and verify on the TV**

```bash
cd launcher && ./gradlew testReleaseUnitTest -q --console=plain && TV_HOST=192.168.2.2 STOCK_LAUNCHER=none ./build-and-install.sh
```

Add `"hdmi_auto_switch": true` to `/tmp/standby-test/config.json`, push it. Ask the user (through AskHuman) to plug a laptop into HDMI 2 with nothing plugged at Home start. Then from the repo root:

```bash
tools/venv/bin/python -I tools/tv.py 'logcat -d | grep -E "TvHome.Standby" | tail -5; dumpsys activity activities | grep -E "mResumedActivity"'
```

Expected: a `input com.droidlogic.tvinput/.services.Hdmi2InputService/HW6 state 0` line and `mResumedActivity` naming `com.xiaomi.mitv.tvplayer`. Ask the user to unplug; within 3 s the same command shows `state 1` and `mResumedActivity: ... com.gathin.tvhome/.MainActivity`. Repeat three times. Reboot the TV with the cable in (`tools/tv.py reboot`, re-enable ADB on the TV if the toggle resets): Home stays on Home.

If the `state` log line never appears when plugging: the callback does not fire on this firmware. Then add a 2 s poll in `StandbyFeed`'s worker (`tim.getInputState` for every seeded HDMI id, call `onInputState` on the main handler when it differs from the last value) and re-test; keep the callback too.

- [ ] **Step 5: Commit**

```bash
git add launcher/app/src/main/java/com/gathin/tvhome/MainActivity.java
git commit -m "Launcher: HDMI plug-in switches to that port after a toast, unplug returns Home

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 8: Theme, docs and final verification

**Files:**
- Create: `themes/meeting-room/config.json`
- Modify: `themes/README.md` (table row + keys)
- Modify: `examples/xiaomi-mitv-astp0-mulan/debloat.txt` (header)
- `README.md` has no themes table (only two theme screenshots), so it is not touched.

- [ ] **Step 1: The theme**

`themes/meeting-room/config.json`:

```json
{
  "dock_apps": ["com.xiaomi.mitv.tvplayer", "com.xiaomi.mitv.appstore", "com.xiaomi.mitv.smartshare", "com.xiaomi.mitv.mediaexplorer"],
  "hidden_apps": ["com.mitv.tvhome", "com.mitv.gallery"],
  "extra_apps": ["com.xiaomi.mitv.tvplayer/.ExternalSourceActivity", "com.xiaomi.mitv.appstore/.MainActivity", "com.xiaomi.mitv.mediaexplorer/.NewScraperMainEntryActivity"],
  "standby": true,
  "standby_idle_seconds": 30,
  "weather_lat": 31.30,
  "weather_lon": 120.58,
  "gitlab_url": "https://git.17usoft.com",
  "gitlab_token": "REPLACE_ME",
  "gitlab_projects": ["team/placeholder-one", "team/placeholder-two"],
  "hdmi_auto_switch": true
}
```

- [ ] **Step 2: Document the keys**

In `themes/README.md`, add to the theme table:

```markdown
| `meeting-room/` | An office TV used only for HDMI screen sharing: a standby clock with weather and CI status after 30 s idle, and automatic switching to a freshly plugged HDMI port (Redmi A50 2025) |
```

Add to the keys table, after the `settings_key_panel` row:

```markdown
| `standby` | bool | Standby screen: a clock, the date and weather, and a status bar, fading in over Home after `standby_idle_seconds` (5 to 3600, default 30) without a key press; any key brings Home back and is swallowed |
| `weather_lat`, `weather_lon` | number | Open-Meteo coordinates for the weather on the standby screen; both needed, no API key |
| `gitlab_url`, `gitlab_token`, `gitlab_projects` | string, string, list of `group/project` | Latest pipeline of each project in the status bar, fetched every 5 minutes with a read-only (`read_api`) token. The token sits in plain text in the TV's config.json; `REPLACE_ME` keeps the source off |
| `feed_url` | string | One JSON with everything instead of the two sources above, fetched every minute: `{"weather":{"temp_c":21,"text":"多云"},"ci":[{"name":"x","status":"success","updated_at":"2026-10-20T06:12:00Z"}]}` |
| `hdmi_auto_switch` | bool | Plugging a device into an HDMI port switches to it after a short toast; unplugging it brings Home back. Only changes count: a cable already in at boot does nothing |
```

Add a section before "## Adding a theme":

```markdown
## Meeting-room standby screen

`themes/meeting-room` turns the launcher into an office screen-sharing TV: after 30 seconds without
a key press Home fades into a large clock with the date, the weather and a status bar; the bar shows
`插线即投屏` and the latest CI pipeline of each `gitlab_projects` entry. Plugging a laptop into any
HDMI port shows "HDMI 2 detected, switching…" for a moment and switches to it; unplugging comes back.
The TV needs a network for the weather and CI (the clock works without). Put a read-only GitLab token
in `gitlab_token` before pushing the theme, and change the coordinates to your office. The design and
the device checks are in `docs/superpowers/specs/2026-10-20-meeting-room-standby-design.md`.
```

In `examples/xiaomi-mitv-astp0-mulan/debloat.txt`, add to the header comment block:

```
# Office use: tools/theme.sh themes/meeting-room gives a standby clock and HDMI auto-switch (themes/README.md).
```

- [ ] **Step 3: Tests and the examples table check**

From the repo root: `python3 -m unittest -q tests.test_examples` → `OK`. From `launcher/`: `./gradlew testReleaseUnitTest -q --console=plain` → quiet, exit 0.

- [ ] **Step 4: Final device verification**

`TV_HOST=192.168.2.2 tools/theme.sh themes/meeting-room`, then run the spec's device list:

1. Wait 30 s on Home: standby fades in; press a key: fades out, key swallowed. Screenshot both.
2. Unplug the Mac's Ethernet: wait for the next standby; clock, date and the HDMI cell only. Plug it back.
3. Plug a laptop into HDMI 2 three times: toast, then the Xiaomi player; unplug: Home within 3 s (check `mResumedActivity`).
4. Reboot with the cable in: Home stays on Home.
5. Only with a real token: CI cells show status and time.

Record the results (screenshots in `cleanyourtv-backup/MiTV-ASTP0-mulan-2026-10-20/screens/standby-*.png`) and report them through AskHuman with the screenshots attached.

- [ ] **Step 5: Commit**

```bash
git add themes/meeting-room/config.json themes/README.md examples/xiaomi-mitv-astp0-mulan/debloat.txt README.md examples/README.md examples/xiaomi-mitv-astp0-mulan designs
git commit -m "Themes: meeting-room standby theme, docs and the Xiaomi mulan example

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

(This also commits the earlier uncommitted example folder, README rows and the design mockups, so the branch holds the whole piece of work.)
