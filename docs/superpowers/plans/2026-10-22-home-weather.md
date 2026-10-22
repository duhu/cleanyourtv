# Home weather band Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Weather band, three day cards and a status bar on Home, from the standby feed, per the G2 mockup.

**Architecture:** Parsing stays pure in `StandbyData`; `StandbyFeed` asks Open-Meteo for hourly/daily too;
`StatusCells` holds the cell code both bars share; `HomeWeatherView` renders the band; `MainActivity` wires it.

**Tech Stack:** Android views (Java), vector drawables, JUnit 4 with org.json.

## Global Constraints

- Build from `launcher/` with JAVA_HOME=/Users/pang/.local/opt/homebrew/opt/openjdk@17 and
  ANDROID_HOME=/Users/pang/.local/opt/homebrew/share/android-commandlinetools.
- 2 px per dp on the office TV: mockup pixels halve into dp/sp.

---

### Task 1: Forecast data (pure)
**Files:** Modify `StandbyData.java`; Test `StandbyDataTest.java`
- [ ] Tests for `parseOpenMeteoForecast`, `Forecast.hoursFrom`, snapshot without forecast → compile failure.
- [ ] Add `HourInfo`, `DayInfo`, `Forecast`, `parseOpenMeteoForecast`, snapshot field → tests pass.

### Task 2: Feed
**Files:** Modify `StandbyFeed.java`
- [ ] Extra query parameters; keep `forecast`; publish the three-argument snapshot everywhere.

### Task 3: Shared status bar
**Files:** Create `StatusCells.java`, `res/layout/status_bar.xml`; Modify `StandbyView.java`, `standby.xml`, `activity_main.xml`
- [ ] Move the bar markup into `status_bar.xml`; include it twice; move cell code into `StatusCells`.

### Task 4: Home weather view
**Files:** Create `HomeWeatherView.java`, `res/layout/home_weather.xml`, `res/drawable/ic_wx_*.xml`; strings en + zh-rCN
- [ ] Band and day cards; `render`, `tick`.

### Task 5: Config and wiring
**Files:** Modify `config.xml`, `Config.java`, `MainActivity.java`, `themes/README.md`, `themes/meeting-room/config.json`
- [ ] `home_weather`, `home_status_bar`; feed start condition; snapshot fan-out; minute tick.
- [ ] `./gradlew testReleaseUnitTest lintRelease assembleRelease -q --console=plain` passes.

### Task 6: Device check and commit
- [ ] Install, push theme, screenshot Home; standby after 5 s; commit `home: weather band, day cards and status bar`.
