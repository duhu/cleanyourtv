# Home pages Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Home becomes a row of pages (apps, clock, weather) flipped with Left/Right, resting on the clock, per
`docs/superpowers/specs/2026-10-22-home-pager-design.md`.

**Architecture:** `PagesConfig` (pure) validates the theme keys; `PageKeys` (pure) maps keys to pager actions;
`PagerView` slides page views; `ClockPageView`, `WeatherPageView` and `HdmiToastView` are the split-up standby and
Home weather views; `MainActivity` wires keys, the return timer, focus and the shared bar.

**Tech Stack:** Android views (Java), JUnit 4.

## Global Constraints

- Build from `launcher/` with JAVA_HOME=/Users/pang/.local/opt/homebrew/opt/openjdk@17 and
  ANDROID_HOME=/Users/pang/.local/opt/homebrew/share/android-commandlinetools:
  `./gradlew testReleaseUnitTest lintRelease assembleRelease -q --console=plain`.
- 2 px per dp on the office TV: mockup pixels halve into dp/sp.
- Every pager, page and feed call in `MainActivity` goes through `StandbyFeature.run`.
- Commit after each task; messages end with `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.

---

### Task 1: `PagesConfig` and the theme keys (pure)
**Files:** `git mv StandbyConfig.java PagesConfig.java`, `git mv StandbyConfigTest.java PagesConfigTest.java`;
Modify `Config.java`, `res/values/config.xml`, `StandbyFeed.java` (type name only), `themes/meeting-room/config.json`,
`themes/README.md`.
- [ ] Rewrite the test for `PagesConfig.from(List<String> pages, String defaultPage, int returnSeconds, boolean flipClock,
  boolean statusBar, String lat, String lon, String gitlabUrl, String gitlabToken, List<String> gitlabProjects,
  String feedUrl, boolean hdmiAutoSwitch)`: defaults (`["apps"]`, index 0, 600, no feed); `[" Clock", "weather", "x", "clock"]`
  with coordinates → `[clock, weather]`; without coordinates → `[clock]`; empty → `[apps]`; `default_page` absent →
  index 0, present → its index; return seconds -5 → 0 and 99999 → 86400; `needsFeed` true for `status_bar` alone, for
  clock, for weather, false for `[apps]`; the old weather/GitLab/feed cases kept. Run: fails to compile.
- [ ] Implement `PagesConfig` with `PAGE_APPS/CLOCK/WEATHER`, `MAX_RETURN`, `pages`, `defaultIndex`, `returnSeconds`,
  `flipClock`, `statusBar`, `has()`, `indexOf()`, `multiPage()`, `needsFeed()`. Run: tests pass.
- [ ] `config.xml`: replace `standby`, `standby_idle_seconds`, `standby_flip_clock`, `home_weather`, `home_status_bar`
  with `<string-array name="pages"><item>apps</item></string-array>`, `<string name="default_page">apps</string>`,
  `<integer name="page_return_seconds">600</integer>`, `<bool name="flip_clock">true</bool>`,
  `<bool name="status_bar">false</bool>`, with a comment pointing at the spec.
- [ ] `Config.java`: field `PagesConfig pages` (replacing `standby`, `homeWeather`, `homeStatusBar`); read the five
  resources, overlay JSON keys `pages` (stringList), `default_page`, `page_return_seconds` (integer 0..86400),
  `flip_clock`, `status_bar`; build with `PagesConfig.from(...)`.
- [ ] `themes/meeting-room/config.json`: the new keys, old ones removed. `themes/README.md`: five new rows, the
  meeting-room table row and the section rewritten ("Meeting-room pages"). Build compiles except for
  `MainActivity`/`StandbyView` references (leave those to Task 4). Commit.

### Task 2: `PageKeys` (pure)
**Files:** Create `PageKeys.java`, `PageKeysTest.java`.
- [ ] Tests: on the apps page everything is PASS; otherwise DOWN of Left/Right → PREV/NEXT, repeat → CONSUME,
  Center/Enter → APPS, Back → DEFAULT, Up/Down → CONSUME, VOLUME_UP/HOME → PASS; UP of Left → CONSUME, UP of
  VOLUME_UP → PASS. Run: fails to compile.
- [ ] Implement `enum Action` and `static Action onKey(int keyCode, int action, int repeatCount, boolean onAppsPage)`
  using `KeyEvent` constants (plain ints; the class imports `android.view.KeyEvent` for the constants only, which the
  unit test JVM resolves from the stub jar with `unitTests.returnDefaultValues`; if that fails, copy the keycode
  values as private constants). Run: pass. Commit.

### Task 3: `PagerView`
**Files:** Create `PagerView.java`.
- [ ] `FrameLayout`; `setPages(List<View> pages, int start, boolean animations)` adds nothing (the pages are already
  children in the layout) but records the list, sets non-listed children GONE, listed ones INVISIBLE except `start`;
  `current()`, `show(int, boolean)`, `move(int)`, `setListener(Listener)`. Transition as in the spec (350 ms,
  `DecelerateInterpolator`, `animate().translationX(...)`, `withEndAction` sets the old page INVISIBLE and calls the
  listener); `animate` false or animations off sets visibilities directly and calls the listener at once; a move while
  animating cancels and finishes the running one. Width 0 (before layout) behaves like no animation.
- [ ] Build compiles. Commit.

### Task 4: Split the standby view; the shared bar with dots
**Files:** `git mv StandbyView.java ClockPageView.java`; `git mv res/layout/standby.xml res/layout/clock_page.xml`;
Create `HdmiToastView.java`, `res/layout/hdmi_toast.xml`; Modify `res/layout/status_bar.xml`, `StatusCells.java`,
`res/layout/activity_main.xml`, `res/values/strings.xml`, `res/values-zh-rCN/strings.xml`.
- [ ] `clock_page.xml`: keep only the `standbyContent` column (flip clock, plain clock, date/weekday/weather line) as
  a `merge`; the root view is the `ClockPageView` (a `FrameLayout`, black background, content centred with top margin
  88 dp as before). `ClockPageView`: `configure(boolean flipClock, boolean animations)`, `render(snapshot)`,
  `setShown(boolean)` (flip clock start/stop, minute drift on/off, translation reset). Drop `touch/cancel/consumeKey/
  isShowing/show/hide` and the fade.
- [ ] `hdmi_toast.xml` + `HdmiToastView` (`FrameLayout`, `show(String label, Runnable then)`), the toast block and its
  code moved verbatim; the view is GONE except while the toast shows.
- [ ] `status_bar.xml`: `[LinearLayout statusCells weight 1: HDMI cell] [LinearLayout pageDots wrap, gravity center]`;
  `StatusCells.fill` keeps the first child of `statusCells`; add `StatusCells.dots(LinearLayout dots, int count,
  int current, float density)` and the `pages_hint` string (en "‹ › Pages", zh "‹ › 翻页"); `weather_waiting`
  (en "Waiting for weather…", zh "等待天气数据…").
- [ ] `activity_main.xml`: inside the content column, wrap the header `RelativeLayout` and the grid wrapper in a
  vertical `LinearLayout` `appsPage`; put it, `ClockPageView clockPage` and `WeatherPageView weatherPage` (Task 5;
  add a placeholder `FrameLayout` with that id for now) inside `<com.gathin.tvhome.PagerView android:id="@+id/pager"
  weight 1>`; keep the status bar include (`homeBar`); remove the `HomeWeatherView`; replace the `StandbyView` at the end
  with `<com.gathin.tvhome.HdmiToastView android:id="@+id/toast">`. Commit (build still broken until Task 6).

### Task 5: `WeatherPageView`
**Files:** `git mv HomeWeatherView.java WeatherPageView.java`, `git mv res/layout/home_weather.xml res/layout/weather_page.xml`.
- [ ] `weather_page.xml` (merge into a vertical `LinearLayout`): `wxWaiting` text (centred, 20 sp, 55 % white, GONE
  when data); `wxTop` row (icon 100 dp, temp 130 sp extralight, column: cond 24 sp, hi/lo 16 sp, `wxDate` 14 sp
  55 % white); `wxHours` row (height 120 dp, top margin 30 dp); `wxDays` row (75 dp, top margin 30 dp, cards with
  20 dp gaps). Sizes per the spec's component section; the view is always VISIBLE as a page, `redraw()` toggles
  `wxWaiting` versus the three blocks. `MAX_HOURS = 12`. Hour column: label 13 sp, icon 32 dp, temp 19 sp; day card:
  name 16 sp width 55 dp, icon 28 dp, cond 16 sp, range 18 sp, padding 24 dp.
- [ ] `render(snapshot)`, `tick()` unchanged in meaning; the date line from `standby_date_pattern` + " " +
  `standby_weekday_pattern` via `DateTimeFormatter` on `LocalDate.now()`. Commit.

### Task 6: `MainActivity` wiring
**Files:** Modify `MainActivity.java`.
- [ ] Fields: `PagesConfig pagesConfig; PagerView pager; View appsPage; ClockPageView clockPage; WeatherPageView
  weatherPage; HdmiToastView toast; LinearLayout homeBar, statusCells, pageDots; int appsIndex;` the return
  runnable `returnToDefault = () -> StandbyFeature.run(() -> pager.show(pagesConfig.defaultIndex, true))`.
  Remove `standby`, `standbyConfig`, `homeWeather`, `homeWeatherOn`, `homeBarOn`, `homeSnapshot`, `renderHomeBar`.
- [ ] `onCreate`: find the views; build `List<View>` in `pagesConfig.pages` order (`apps` → `appsPage`, `clock` →
  `clockPage`, `weather` → `weatherPage`); `pager.setPages(list, defaultIndex, animations && !reducedMotion)`;
  `clockPage.configure(flipClock, animations && !reducedMotion)`; bar visibility rule (`statusBar || multiPage()`;
  `statusCells` and background only with `statusBar`); `drawDots()`; feed when `needsFeed() && hasSources()` rendering
  `clockPage`, `weatherPage`, `StatusCells.fill(statusCells, ...)`; `pager.setListener(this::onPage)`.
- [ ] `onPage(int index)`: `drawDots()`; `clockPage.setShown(view == clockPage)`; `weatherPage.tick()` when shown;
  apps page arriving → `restoreFocus()`; apps page leaving → `hideGlass(); hideHero(); ambient.animate().cancel();
  ambient.setAlpha(0f)`. `boolean onAppsPage()` = current page view is `appsPage`.
- [ ] `dispatchKeyEvent`: `PageKeys.onKey(event.getKeyCode(), event.getAction(), event.getRepeatCount(), onAppsPage())`
  → switch; `restartReturnTimer()` on every event; `onUserInteraction` restarts it too. `restartReturnTimer()`:
  remove, then post when `returnSeconds > 0 && pager.current() != defaultIndex`.
- [ ] `onTileVerticalKey`: when `atHorizontalEdge` → `pager.move(left ? -1 : 1)`, return true. `pillKeys`: Left on the
  left button → `pager.move(-1)`, Right on the right button → `pager.move(1)`, return true.
- [ ] `onResume`: `pager.show(defaultIndex, false)` then `restartReturnTimer()`; `onPause`: remove the runnable,
  `clockPage.setShown(false)`. `onInputState`: `toast.show(label, ...)`. Minute tick: `weatherPage.tick()` and the
  bar fill.
- [ ] Build: `./gradlew testReleaseUnitTest lintRelease assembleRelease -q --console=plain` clean. Commit.

### Task 7: Device check and docs
- [ ] `TV_HOST=10.181.112.34 STOCK_LAUNCHER=none ./build-and-install.sh`; push the theme with `page_return_seconds`
  20 for the check, then 600. Walk the spec's device list with screenshots (`tools/tv.py --pull`).
- [ ] Memory note update (`cleanyourtv-redmi-a50-setup.md`): pages model, keys. Commit.
