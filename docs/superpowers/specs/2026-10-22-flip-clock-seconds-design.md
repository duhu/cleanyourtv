# Seconds on the standby flip clock

Design for the office TV's standby screen, 2026-10-22. Follows the 2026-10-20 meeting-room design;
only `FlipClockView` changes.

## Goal

The split-flap clock flips once a minute, so for most of the time it looks static. Two seconds cards
make it visibly a flip clock: the ones digit flips every second, the tens every ten seconds.

## Decisions

| Question | Decision |
|---|---|
| Seconds cards | Two cards to the right of the minutes, 62 % the size of the hour/minute cards, bottom edges aligned, no colon, a double gap before them |
| Hour/minute cards | 136×200 dp instead of 150×220 dp so the six cards fit with ~120 px to each edge at 1920 px (plus the ±24 px burn-in drift) |
| Tick source | A main-thread `Handler` runnable aligned to the next second boundary while the clock is started; the minute broadcast is no longer needed, the time/timezone-changed broadcasts stay |
| Reduced motion | `setAnimated(false)` still updates every second, without the flip |
| Configuration | None added: seconds are part of `standby_flip_clock`. The README row says so |
| Plain clock (`standby_flip_clock: false`) | Unchanged, `HH:mm` |

## Components

`FlipClockView`:

- `digits(hourOfDay, minute, second, is24h)` returns six characters (hour and minute rules as before).
- `msToNextSecond(nowMillis)` returns `1000 - now % 1000` (1..1000), pure, tested.
- `start()` shows the time at once, registers the time/timezone receiver and posts the second tick;
  `stop()` removes the tick, unregisters and settles the cards.
- The seconds `Digit`s get `LayoutParams` of 84×124 dp with `Gravity.BOTTOM` in the row; the row's
  gravity becomes `BOTTOM` with the hour/minute cards filling the row height, so bottom edges align.

## Testing

- `FlipClockTest`: six digits for 24-hour and 12-hour times; `msToNextSecond` at 0, 999 and 1234 ms.
- On the TV: standby after 30 s idle; two screenshots one second apart show different seconds; a key
  press hides the clock and the tick stops (no `FlipClock` log lines after hide).
