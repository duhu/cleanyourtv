# Flip clock seconds Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Two smaller seconds cards on the standby flip clock, flipping every second.

**Architecture:** `FlipClockView` grows from four to six `Digit` cards and swaps the minute broadcast for a
handler runnable aligned to the second. No config key, no layout file change.

**Tech Stack:** Android views (Java), JUnit 4 for the pure helpers.

## Global Constraints

- Build from `launcher/` with JAVA_HOME=/Users/pang/.local/opt/homebrew/opt/openjdk@17 and
  ANDROID_HOME=/Users/pang/.local/opt/homebrew/share/android-commandlinetools.
- Commit on `main` (this repo's working branch for the office TV).

---

### Task 1: Pure helpers

**Files:** Modify `launcher/app/src/main/java/com/gathin/tvhome/FlipClockView.java`,
Test `launcher/app/src/test/java/com/gathin/tvhome/FlipClockTest.java`

- [ ] Replace the test with six-digit expectations and `msToNextSecond` cases.
- [ ] Run `./gradlew testReleaseUnitTest --tests '*FlipClockTest*' -q` → compile failure.
- [ ] Add `second` to `digits(...)` and `static long msToNextSecond(long now)`.
- [ ] Tests pass.

### Task 2: Six cards and the second tick

**Files:** Modify `FlipClockView.java`

- [ ] Constants: CARD 136×200 dp, SEC_CARD 84×124 dp, SEC_GAP 28 dp.
- [ ] Constructor builds hour, hour, colon, minute, minute, seconds, seconds; seconds cards use
      `Gravity.BOTTOM` layout params; row gravity `BOTTOM`.
- [ ] `setTime` shows six digits; `secondTick` runnable reposts with `msToNextSecond`.
- [ ] `start()` posts the tick; `stop()` removes it; receiver keeps TIME_CHANGED/TIMEZONE_CHANGED only.
- [ ] `./gradlew testReleaseUnitTest lintRelease assembleRelease -q --console=plain` passes.

### Task 3: Docs, device check, commit

- [ ] `themes/README.md` `standby_flip_clock` row mentions the seconds cards.
- [ ] `TV_HOST=10.181.112.34 STOCK_LAUNCHER=none ./build-and-install.sh`; idle 30 s; two screenshots
      one second apart differ in the seconds cards.
- [ ] Commit: `standby: seconds cards on the flip clock`.
