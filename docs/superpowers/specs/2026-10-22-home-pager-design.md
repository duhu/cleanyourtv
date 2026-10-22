# Home as pages: clock, weather, apps

Design for the office TV, 2026-10-22. Mockups: `designs/home-pager/` (P3 apps page, P1 clock page,
P2 weather page, all approved). Replaces the standby layer of the 2026-10-20 design and the Home
weather band of the 2026-10-22 home-weather design.

## Goal

There is no longer a standby screen over a home screen. Home is one row of pages the remote flips
through with Left and Right: the flip clock is the page the TV rests on, the weather page sits next
to it, and the apps page (casting capsule, HDMI quick-switch tiles, top buttons) is one more page.
A page you flipped to stays until you flip again or until a long idle time passes; then the clock
comes back. New pages (a meeting schedule, CI) are later just another page view and name.

## Decisions

| Question | Decision |
|---|---|
| Where the pages live | In `MainActivity` itself: a `PagerView` holds the page views side by side. No overlay, no fade-in, no swallowed wake-up key |
| Pages in this version | `apps`, `clock`, `weather`. Order and presence come from the theme's `pages` list; the meeting-room theme uses `["apps", "clock", "weather"]` so the clock is one press from each neighbour |
| Default page | `default_page` (`clock` for the meeting room). Shown at launch and whenever Home comes back from another app, without animation. Must be in `pages`; otherwise the first page |
| Keys on the clock and weather pages | Left/Right flip (nothing happens at the ends); OK or Enter jumps to the apps page with its dock focused; Back goes to the default page; Up/Down are swallowed. Volume, mute and every other key pass through untouched |
| Keys on the apps page | Unchanged focus navigation. Left on the leftmost focusable item (first dock tile, or the leftmost top button) flips to the previous page; Right on the rightmost (last dock tile, or the rightmost top button) flips to the next |
| Return to the default page | `page_return_seconds` (0 = never, at most 86400; 600 for the meeting room) after the last key press while on another page. Every key press restarts the timer |
| Transition | A 350 ms horizontal slide (decelerate); instant when `animations` is off or `reduced_motion` is on. Pages not on screen are INVISIBLE so they take no focus |
| Shared chrome | The status bar (HDMI hint, CI cells) stays at the bottom on every page when `status_bar` is on. With more than one page, page dots and a "‹ › 翻页" hint sit at its right end; without `status_bar` the row shows only the dots, with no panel background |
| HDMI | Auto-switch and the toast are unchanged and work on every page. The toast moves to its own `HdmiToastView` |
| Burn-in | The clock page keeps the once-a-minute random drift; the other pages do not drift |
| Weather page | Current conditions large (icon, temperature, condition word, today's high/low, the date), 12 hourly columns from the current hour, three day cards; the same snapshot as before. Until the first weather arrives it shows "等待天气数据…". No place name (Open-Meteo gives none) |
| Clock page | The former standby content: flip clock with seconds (or plain digits with `flip_clock: false`), date, weekday and the current weather word and temperature. The seconds tick only while the page is on screen |
| Theme keys | New: `pages` (list), `default_page` (string), `page_return_seconds` (int), `flip_clock` (bool), `status_bar` (bool). Removed: `standby`, `standby_idle_seconds`, `standby_flip_clock`, `home_weather`, `home_status_bar`. `weather_*`, `gitlab_*`, `feed_url`, `hdmi_auto_switch`, `hdmi_tiles` unchanged |
| Defaults | `pages: ["apps"]`, `default_page: "apps"`, `page_return_seconds: 600`, `flip_clock: true`, `status_bar: false`: a theme that says nothing gets today's launcher, no dots, no bar |
| Validation | Page names are trimmed and lower-cased; unknown names and duplicates are dropped; `weather` is dropped when no coordinates are configured; an empty list becomes `["apps"]`; `page_return_seconds` outside 0..86400 is clamped |
| Data feed | Runs when any shown page or the bar needs it: `clock` or `weather` in `pages`, or `status_bar`, and at least one source is configured |
| Failure | Every page, pager and feed call runs under `StandbyFeature.run`: a failure switches the pages feature off for the process (the apps page alone stays) and never takes Home down |

## Components

### `PagesConfig` (pure; replaces `StandbyConfig`)

Fields: `pages` (unmodifiable `List<String>`), `defaultIndex`, `returnSeconds`, `flipClock`,
`statusBar`, plus the unchanged `weatherLat/Lon`, `gitlabUrl/Token/Projects`, `feedUrl`,
`hdmiAutoSwitch`. Constants `PAGE_APPS = "apps"`, `PAGE_CLOCK = "clock"`, `PAGE_WEATHER = "weather"`,
`MAX_RETURN = 86400`. `from(pages, defaultPage, returnSeconds, flipClock, statusBar, lat, lon,
gitlabUrl, gitlabToken, gitlabProjects, feedUrl, hdmiAutoSwitch)` validates as in the table.
Helpers: `has(String page)`, `indexOf(String page)` (-1 when absent), `multiPage()`, `needsFeed()`
(`statusBar || has(clock) || has(weather)`), and the existing `hasWeather/hasGitlab/hasFeed/hasSources`.

### `PageKeys` (pure)

`enum Action { PASS, CONSUME, PREV, NEXT, APPS, DEFAULT }` and
`static Action onKey(int keyCode, int action, int repeatCount, boolean onAppsPage)`:

- `onAppsPage` → `PASS` for every key.
- otherwise, for `ACTION_DOWN`: `DPAD_LEFT` → `PREV`, `DPAD_RIGHT` → `NEXT` (both `CONSUME` on a
  repeat), `DPAD_CENTER`/`ENTER` → `APPS`, `BACK` → `DEFAULT`, `DPAD_UP`/`DPAD_DOWN` → `CONSUME`,
  anything else → `PASS`.
- `ACTION_UP` of those same keys → `CONSUME`; other keys `PASS`.

### `PagerView`

A `FrameLayout` whose children are the page views, all `match_parent`. `setPages(List<View> pages,
int start, boolean animations)`; `int current()`; `void show(int index, boolean animate)` (no-op when
out of range or already current); `boolean move(int delta)` (false at an end; animated); a
`Listener.onPage(int index)` called after the new page is on screen. The transition: the leaving page
slides to `-dir * width`, the arriving page from `+dir * width` to 0, 350 ms decelerate; a move during
a transition finishes the running one first. Pages not current are INVISIBLE with translation 0.

### `ClockPageView` (renamed from `StandbyView`)

Inflates `res/layout/clock_page.xml` (the old standby content without the layer, the bar and the
toast). `configure(boolean flipClock, boolean animations)`, `render(snapshot)` (the weather words),
`setShown(boolean)` (starts or stops the flip clock's ticking and the minute drift).

### `WeatherPageView` (replaces `HomeWeatherView`)

Inflates `res/layout/weather_page.xml`: a top block (icon 100 dp, temperature 130 sp extralight,
condition 24 sp, high/low 16 sp via `home_hilo`, the date 14 sp via `standby_date_pattern` and
`standby_weekday_pattern`), an hours row of up to 12 columns (label 13 sp, icon 32 dp, temperature
19 sp), a day-card row (75 dp cards, name 16 sp, icon 28 dp, condition 16 sp, range 18 sp), and a
centred `weather_waiting` text shown while the snapshot has no weather. `render(snapshot)`,
`tick()` as before. Hour and day labels keep `home_hour_now`, `home_hour_pattern`,
`home_day_tomorrow`, `home_day_pattern`.

### `HdmiToastView`

`res/layout/hdmi_toast.xml` (the old `standbyToast` block) and `show(String label, Runnable then)`,
moved out of `StandbyView` unchanged.

### `status_bar.xml` / `StatusCells`

The bar becomes `[statusCells (weight 1: HDMI cell, CI cells)] [pageDots]`. `StatusCells.fill` works
on `statusCells`. `StatusCells.dots(LinearLayout dots, int count, int current, float density)`
draws `count` 7 dp dots (current 8 dp at 90 % white, others 25 %) followed by the `pages_hint` text,
or nothing when `count < 2`.

### `activity_main.xml`

The content column becomes `[PagerView pager (weight 1)] [status bar]`. The pager's children in
layout order: `appsPage` (the old header + grid wrapper), `clockPage`, `weatherPage`. `heroName`,
`glass` and `ambient` stay in the root; `HdmiToastView toast` replaces the `StandbyView` at the end.
`home_weather.xml` and `standby.xml` are deleted.

### `MainActivity`

- Builds the pager from `cfg.pages`: the page views in `pages` order (the rest GONE), start at
  `defaultIndex`.
- `dispatchKeyEvent`: `PageKeys.onKey(...)` → PASS falls through; CONSUME returns true; PREV/NEXT
  call `pager.move`; APPS shows the apps page; DEFAULT shows the default page. Every key and
  `onUserInteraction` restart the return timer (`handler.postDelayed(returnToDefault, returnSeconds *
  1000)` when `returnSeconds > 0` and the current page is not the default).
- `onTileVerticalKey` at a horizontal edge and the top-button key listener at its ends: `pager.move(-1
  / +1)` instead of only swallowing the key.
- Pager listener: arriving on the apps page runs the existing `restoreFocus()` (HDMI tile first, else
  the last tile); leaving it hides the glass, the hero name and the ambient glow. The clock page gets
  `setShown(true/false)`; the weather page `tick()`s on arrival. Dots are redrawn.
- `onResume`: `pager.show(defaultIndex, false)`, then the return timer. `onPause`: timer removed,
  `clockPage.setShown(false)`.
- The feed starts when `cfg.pages.needsFeed() && hasSources()`; each snapshot renders the clock page,
  the weather page and the bar. The minute tick refreshes the weather page and the bar as before.
- The HDMI toast call moves to `toast.show(...)`.
- The bar row is visible when `statusBar || multiPage()`; the `statusCells` row and the panel background only
  with `statusBar`.

## Theme and docs

`themes/meeting-room/config.json`: `"pages": ["apps", "clock", "weather"]`, `"default_page":
"clock"`, `"page_return_seconds": 600`, `"flip_clock": true`, `"status_bar": true`; the five old keys
removed. `config.xml` gets the new resources (`pages` string-array, `default_page` string,
`page_return_seconds` integer, `flip_clock` and `status_bar` bools) and loses the old ones.
`themes/README.md`: the five rows replaced by five new ones, the meeting-room row and the
"Meeting-room standby screen" section rewritten as "Meeting-room pages". Strings: `pages_hint`
("‹ › Pages" / "‹ › 翻页"), `weather_waiting` ("Waiting for weather…" / "等待天气数据…").

## Testing

- `PagesConfigTest` (from `StandbyConfigTest`): the defaults; unknown, duplicate and empty lists;
  `weather` dropped without coordinates; `default_page` outside the list; the clamp; `needsFeed`;
  the unchanged source checks.
- `PageKeysTest`: the mapping above, including repeats, key-ups and the pass-through of volume keys.
- On the TV: Home opens on the clock with seconds flipping and the bar with three dots; Right shows
  the weather page, Left twice the apps page with the capsule focused; OK on the clock lands on the
  capsule; Right from the capsule (the rightmost tile) goes to the clock; after `page_return_seconds`
  (set to 20 for the check) the clock comes back; opening the casting app and pressing Home shows the
  clock; plugging HDMI still shows the toast and switches.
