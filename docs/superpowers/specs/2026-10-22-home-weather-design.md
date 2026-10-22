# Home weather band and status bar

Design for the office TV's home screen, 2026-10-22. Mockup: `designs/home-tiles/G2 天气横条.html`
(approved; G1 was the rejected right-hand panel). Builds on the 2026-10-20 standby design.

## Goal

With only the casting capsule on it, Home is an empty screen. Now that the TV has internet, Home shows
the weather the standby screen already fetches, extended with the next hours and the next three days,
and the same status bar as the standby screen. The standby screen then comes back after 5 s idle.

## Decisions

| Question | Decision |
|---|---|
| Layout | G2: a full-width band under the header (now: icon, temperature, condition, today's high/low; then up to 8 hourly columns), a row of three day cards, the tile grid, the status bar at the bottom |
| Data | The existing Open-Meteo request also asks for `hourly=temperature_2m,weather_code`, `daily=temperature_2m_max,temperature_2m_min,weather_code`, `timezone=auto`, `forecast_days=4`; one request every 30 min as before |
| Place name | Not shown (Open-Meteo returns none); the condition word stands alone |
| Focus | Nothing in the band or the cards takes focus; the grid keeps its navigation |
| Status bar on Home | Same cells as the standby bar (HDMI hint, then CI cells); the cell code is shared |
| Configuration | `home_weather` (band + day cards) and `home_status_bar`, both bool, default false; need `weather_lat`/`weather_lon` (and GitLab for CI cells). The feed thread runs when the standby screen or either Home feature needs it |
| Idle | `themes/meeting-room/config.json`: `standby_idle_seconds` 5, `home_weather` and `home_status_bar` true |
| Stale data | A failed fetch keeps the last snapshot; nothing is shown until the first weather arrives (the band stays GONE) |

## Components

### `StandbyData` (pure)

- `HourInfo{LocalDateTime time, double tempC, WeatherKind kind}`,
  `DayInfo{LocalDate date, double minC, double maxC, WeatherKind kind}`.
- `Forecast{double todayMinC, todayMaxC (NaN unknown), List<HourInfo> hours, List<DayInfo> days}` where
  `days` excludes today; `hoursFrom(LocalDateTime now, int max)` returns the hours from the one that
  contains `now`, at most `max`.
- `parseOpenMeteoForecast(json)` reads `hourly` and `daily`; null when either is missing or malformed.
- `StandbySnapshot` gains `forecast` (nullable); the two-argument constructor keeps it null.

### `StandbyFeed`

`fetchWeather` requests the extra fields, parses current and forecast, keeps both across GitLab
publishes.

### `StatusCells` (new)

The CI cell builder, status colour, age text and weather word, moved out of `StandbyView` so the
Home bar renders the same cells. `res/layout/status_bar.xml` holds the bar with its HDMI cell;
`standby.xml` and `activity_main.xml` include it (`standbyBar`, `homeBar`).

### `HomeWeatherView` (new)

A vertical `LinearLayout` inflated from `res/layout/home_weather.xml`: the band (panel background
`standby_bar`, 115 dp) and the day-card row (three cards, 55 dp). `render(snapshot)` stores the
snapshot; `tick()` redraws from the current time (the "now" column, day names). Hour labels use
`home_hour_pattern` ("H时" / "HH"), the first column `home_hour_now`; day names `home_day_tomorrow`
for the first card, then `home_day_pattern` ("EEE"). High/low `home_hilo`. Weather icons are seven
stroke vector drawables mapped from `WeatherKind` (CLEAR sun, CLOUDY partly cloudy, OVERCAST cloud,
FOG fog, RAIN rain, SNOW snow, THUNDER storm, UNKNOWN cloud).

### `MainActivity`

Finds the two views, shows them per config, starts the feed when any consumer needs it, forwards each
snapshot to the standby view, the band and the Home bar, and calls `tick()` and the bar refresh from
the existing minute tick. Every call goes through `StandbyFeature.run`.

## Testing

- `StandbyDataTest`: forecast parsing from a real Open-Meteo answer (hours, today's high/low, three
  days, kinds); `hoursFrom` drops past hours, keeps the current one and caps the count; missing
  `daily` → null; the two-argument snapshot has no forecast.
- On the TV: push the theme, Home shows the band with today's Suzhou weather, hours from the current
  hour, three day cards and the bar; standby returns after 5 s; the capsule still takes focus and OK
  still opens it.
