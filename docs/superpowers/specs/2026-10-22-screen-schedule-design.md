# Dim when idle, sleep in the evening

Design for the office TV, 2026-10-22. Builds on the Home pages of the 2026-10-22 home-pager design.
Experiment of the same day: the TV's Xiaomi power service lets only the remote's power key wake the
screen (`wakeup reason … not allow!` for wake locks, `setTurnScreenOn`, alarms and shell commands), so
the launcher can put the TV to sleep but cannot wake it. The user chose "real standby, a person wakes
it": no morning wake-up is attempted.

## Goal

1. After `idle_seconds` without a key press, Home darkens: a black layer at `dim_percent` opacity over
   every page. Any key brings the page back (and is otherwise ignored, except volume and mute).
2. Inside the sleep window (from `sleep_time` to `wake_time` on weekdays, all day on Saturday and
   Sunday) Home does not dim when idle; it puts the TV to sleep the way the remote's power key does.
   So at 20:00 an unattended TV sleeps within a minute; one in use sleeps `idle_seconds` after the
   last key; one woken at night by the remote sleeps again `idle_seconds` after it is left alone.
3. In the morning a person presses the remote's power key; Home comes back on the default page (the
   clock) as it already does. `wake_time` only ends the window.

Everything runs inside `MainActivity` while it is resumed. While another app is in front (casting,
HDMI) the launcher sees no keys and does nothing; the rules apply again when Home is back.

## Decisions

| Question | Decision |
|---|---|
| How the TV sleeps | `DevicePolicyManager.lockNow()` from the `ScreenAdmin` device admin (policy `force-lock` only), activated over adb by `build-and-install.sh`: `dpm set-active-admin --user 0 com.gathin.tvhome/.ScreenAdmin`. Verified: the TV goes to `mWakefulness=Asleep`, screen off, like the power key |
| Admin not active | The window degrades to dimming; one log line per idle timeout, no prompt |
| Idle | Time since the last key event Home saw (`dispatchKeyEvent` / `onUserInteraction`), or since `onResume` when nothing was pressed yet. The page-return timer is separate and unchanged |
| The timeout | One `idleTimeout` runnable posted `idle_seconds` after every key; plus the existing minute tick checks the idle time, so a TV that dimmed at 19:50 goes to sleep at the 20:00 tick |
| The dim layer | A black `View` (`dimLayer`), last child of the root layout, over the pages, the bar, the glass and the HDMI toast. Alpha animates 0 → `dim_percent / 100` in 600 ms (instant without `animations`). The clock keeps ticking under it |
| Keys while dimmed | The first `ACTION_DOWN` undims and is consumed, and its `ACTION_UP` too; volume up/down and mute undim but pass through. Keys the launcher consumes are never seen by the pages |
| HDMI while dimmed | An auto-switch undims first so the toast is visible; nothing else changes |
| Pause | `onPause` removes the layer at once and cancels the timeout; `onResume` restarts the idle clock |
| Sleep window | `sleep_time` and `wake_time` as `HH:mm`; both needed, equal or invalid switches the window off. Weekdays: from `sleep_time` to `wake_time` (through midnight when `sleep_time` is later). Saturday and Sunday: all day, so Friday 20:00 → Monday 09:00 for the meeting room |
| After waking | Nothing special: a wake by the remote resumes Home on the default page, and the idle clock starts again |
| Theme keys | `idle_seconds` (0..86400, 0 = never dim or sleep; default 0), `dim_percent` (0..95, 0 = no dimming; default 50), `sleep_time`, `wake_time` (default empty). Meeting room: 600, 50, "20:00", "09:00" |
| Uninstalling | An active admin blocks `pm uninstall`. `ScreenAdminOff`, an exported receiver, removes the admin when sent `com.gathin.tvhome.SCREEN_ADMIN_OFF`; documented in the README |
| Experiment code | `PowerReceiver`, the `WAKE_LOCK` permission and `setTurnScreenOn` in MainActivity are removed |
| Known side effect | Each standby/wake cycle re-enables five of the debloated packages (analytics, webcontent, shoplugin, videodaily, tv.gallery). Accepted by the user; not handled |

## Components

### `ScreenSchedule` (pure)

Fields: `idleSeconds` (0..86400), `dimPercent` (0..95), `sleepMinute`, `wakeMinute` (minute of day,
-1 when off). `static from(int idleSeconds, int dimPercent, String sleepTime, String wakeTime)`
clamps the numbers and parses `H:mm`/`HH:mm` (invalid, missing or equal times → both -1).
`boolean dims()` (`idleSeconds > 0 && dimPercent > 0`), `boolean sleeps()` (`sleepMinute >= 0`),
`boolean inSleepWindow(DayOfWeek day, int minuteOfDay)` as decided above, and
`enum Idle { NONE, DIM, SLEEP }` with `Idle afterIdle(DayOfWeek day, int minuteOfDay, boolean canSleep)`:
`idleSeconds == 0` → NONE; in the window and `canSleep` → SLEEP; `dimPercent > 0` → DIM; else NONE.

### `DimKeys` (pure)

`enum Action { PASS, WAKE_PASS, WAKE_CONSUME, CONSUME }` and
`static Action onKey(int keyCode, int action, boolean dimmed, int swallowKey)`:
dimmed and `ACTION_DOWN`: volume up/down/mute → WAKE_PASS, others → WAKE_CONSUME; not dimmed and
`action == ACTION_UP && keyCode == swallowKey` → CONSUME; everything else → PASS. The activity sets
`swallowKey` to the key code on WAKE_CONSUME and clears it on CONSUME.

### `MainActivity`

- `ScreenSchedule schedule` from `cfg.screen`; `View dimLayer`; `boolean dimmed`; `int swallowKey`;
  `long lastKeyAt` (`SystemClock.elapsedRealtime()`); `Runnable idleTimeout`.
- `restartIdleTimer()`: `lastKeyAt = now`; remove `idleTimeout`; post it after `idleSeconds * 1000`
  when `idleSeconds > 0`. Called from `dispatchKeyEvent` (every event), `onUserInteraction`, `onResume`.
- `onIdle()`: `schedule.afterIdle(today, minuteOfDay, adminActive())`: SLEEP → log and `lockNow()`;
  DIM → `showDim()`; NONE → nothing. `checkIdle()` (minute tick): `onIdle()` when
  `idleSeconds > 0 && now - lastKeyAt >= idleSeconds * 1000`.
- `showDim()` / `hideDim(boolean animate)` as decided. `dispatchKeyEvent` consults `DimKeys` first.
- `onPause`: `hideDim(false)`, remove `idleTimeout`. `onInputState` SWITCH: `hideDim(true)`.
- All of it under `StandbyFeature.run`.

### `ScreenAdmin`, `ScreenAdminOff`

`ScreenAdmin extends DeviceAdminReceiver` (unchanged, `res/xml/screen_admin.xml` with `force-lock`).
`ScreenAdminOff extends BroadcastReceiver`: `dpm.removeActiveAdmin(ScreenAdmin)` and a log line;
manifest: exported, intent-filter `com.gathin.tvhome.SCREEN_ADMIN_OFF`.

### `Config`, resources, theme, scripts

`Config.screen` (`ScreenSchedule`) from `R.integer.idle_seconds`, `R.integer.dim_percent`,
`R.string.sleep_time`, `R.string.wake_time`, overridden by the JSON keys of the same names.
`activity_main.xml`: `dimLayer` after the toast. `themes/meeting-room/config.json` and
`themes/README.md` get the four keys; `README.md` the admin note. `build-and-install.sh` adds the
`dpm set-active-admin` line to the post-install batch (a failure only prints).

## Testing

- `ScreenScheduleTest`: clamps; parsing (`9:00`, `09:00`, `24:00`, `abc`, empty, equal); the window on
  a weekday before/at/after `sleep_time`, before/at `wake_time`, past midnight; Saturday and Sunday;
  `afterIdle` for each branch including `canSleep = false`.
- `DimKeysTest`: the mapping above, including a volume key while dimmed and the swallowed key-up.
- On the TV with a scratch theme (`idle_seconds: 20`): the apps page dims after 20 s (screencap shows
  the dark layer), a DPAD key restores it without moving focus; with a window covering now, the TV
  goes to `mWakefulness=Asleep` 20 s after the last key; an adb power key wakes it back to the clock.
