# Dim and Evening Sleep Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Home darkens after `idle_seconds` without a key, and inside the evening/weekend window puts the TV to sleep instead (device-admin `lockNow`).

**Architecture:** Two pure classes (`ScreenSchedule` for the timings and the window, `DimKeys` for the key handling while dimmed) tested on the JVM; `MainActivity` wires them to an idle timer, the minute tick, a black `dimLayer` view and `DevicePolicyManager.lockNow()`. The `ScreenAdmin` device admin (force-lock) is activated over adb by `build-and-install.sh`.

**Tech Stack:** Android (minSdk 26, Java), JUnit 4 unit tests (`./gradlew testReleaseUnitTest`), `tools/tv.py` over adb for the TV checks.

Spec: `docs/superpowers/specs/2026-10-22-screen-schedule-design.md`.

## Global Constraints

- Build env (from `launcher/`): `export JAVA_HOME=/Users/pang/.local/opt/homebrew/opt/openjdk@17 ANDROID_HOME=/Users/pang/.local/opt/homebrew/share/android-commandlinetools PATH=$JAVA_HOME/bin:$PATH`.
- Tests: `./gradlew testReleaseUnitTest -q --console=plain`; full check: `./gradlew testReleaseUnitTest lintRelease assembleRelease -q --console=plain`.
- Every page/schedule call in `MainActivity` runs under `StandbyFeature.run(...)`.
- Theme keys: `idle_seconds` (0..86400, default 0), `dim_percent` (0..95, default 50), `sleep_time`, `wake_time` (`HH:mm`, default empty). Meeting room: 600, 50, "20:00", "09:00".
- No morning wake-up code; the experiment's `PowerReceiver`, `WAKE_LOCK` and `turnScreenOnIfAsked` are removed.

---

### Task 1: `ScreenSchedule`

**Files:**
- Create: `launcher/app/src/main/java/com/gathin/tvhome/ScreenSchedule.java`
- Test: `launcher/app/src/test/java/com/gathin/tvhome/ScreenScheduleTest.java`

**Interfaces:**
- Produces: `ScreenSchedule.from(int idleSeconds, int dimPercent, String sleepTime, String wakeTime)`, fields `idleSeconds`, `dimPercent`, `sleepMinute`, `wakeMinute`, `boolean dims()`, `boolean sleeps()`, `boolean inSleepWindow(DayOfWeek, int minuteOfDay)`, `enum Idle { NONE, DIM, SLEEP }`, `Idle afterIdle(DayOfWeek, int minuteOfDay, boolean canSleep)`, `static final int MAX_IDLE = 86400`, `MAX_DIM = 95`.

- [ ] **Step 1: Write the failing tests**

```java
package com.gathin.tvhome;

import org.junit.Test;

import java.time.DayOfWeek;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Idle dimming and the evening sleep window: what a theme's idle_seconds, dim_percent, sleep_time and wake_time turn into. */
public class ScreenScheduleTest {
    private static final ScreenSchedule MEETING = ScreenSchedule.from(600, 50, "20:00", "09:00");

    private static int at(int hour, int minute) {
        return hour * 60 + minute;
    }

    @Test
    public void defaultsNeitherDimNorSleep() {
        ScreenSchedule s = ScreenSchedule.from(0, 50, "", "");
        assertFalse(s.dims());
        assertFalse(s.sleeps());
        assertEquals(ScreenSchedule.Idle.NONE, s.afterIdle(DayOfWeek.MONDAY, at(21, 0), true));
    }

    @Test
    public void numbersAreClamped() {
        ScreenSchedule s = ScreenSchedule.from(-5, 120, "", "");
        assertEquals(0, s.idleSeconds);
        assertEquals(ScreenSchedule.MAX_DIM, s.dimPercent);
        assertEquals(ScreenSchedule.MAX_IDLE, ScreenSchedule.from(999999, 50, "", "").idleSeconds);
    }

    @Test
    public void timesParseWithOrWithoutLeadingZero() {
        ScreenSchedule s = ScreenSchedule.from(600, 50, "20:00", "9:05");
        assertEquals(at(20, 0), s.sleepMinute);
        assertEquals(at(9, 5), s.wakeMinute);
        assertTrue(s.sleeps());
    }

    @Test
    public void badOrMissingTimesSwitchSleepOff() {
        assertFalse(ScreenSchedule.from(600, 50, "20:00", "").sleeps());
        assertFalse(ScreenSchedule.from(600, 50, "", "09:00").sleeps());
        assertFalse(ScreenSchedule.from(600, 50, "24:00", "09:00").sleeps());
        assertFalse(ScreenSchedule.from(600, 50, "20:60", "09:00").sleeps());
        assertFalse(ScreenSchedule.from(600, 50, "abc", "09:00").sleeps());
        assertFalse(ScreenSchedule.from(600, 50, "20:00", "20:00").sleeps());
        assertEquals(-1, ScreenSchedule.from(600, 50, "20:00", "").sleepMinute);
    }

    @Test
    public void weekdayWindowRunsThroughTheNight() {
        assertFalse(MEETING.inSleepWindow(DayOfWeek.MONDAY, at(19, 59)));
        assertTrue(MEETING.inSleepWindow(DayOfWeek.MONDAY, at(20, 0)));
        assertTrue(MEETING.inSleepWindow(DayOfWeek.MONDAY, at(23, 59)));
        assertTrue(MEETING.inSleepWindow(DayOfWeek.TUESDAY, at(0, 0)));
        assertTrue(MEETING.inSleepWindow(DayOfWeek.TUESDAY, at(8, 59)));
        assertFalse(MEETING.inSleepWindow(DayOfWeek.TUESDAY, at(9, 0)));
        assertFalse(MEETING.inSleepWindow(DayOfWeek.FRIDAY, at(12, 0)));
    }

    @Test
    public void weekendIsAllInTheWindow() {
        assertTrue(MEETING.inSleepWindow(DayOfWeek.SATURDAY, at(12, 0)));
        assertTrue(MEETING.inSleepWindow(DayOfWeek.SUNDAY, at(9, 30)));
        assertTrue(MEETING.inSleepWindow(DayOfWeek.MONDAY, at(3, 0)));
    }

    @Test
    public void sameDayWindowWhenSleepIsBeforeWake() {
        ScreenSchedule s = ScreenSchedule.from(600, 50, "01:00", "06:00");
        assertFalse(s.inSleepWindow(DayOfWeek.WEDNESDAY, at(0, 59)));
        assertTrue(s.inSleepWindow(DayOfWeek.WEDNESDAY, at(1, 0)));
        assertTrue(s.inSleepWindow(DayOfWeek.WEDNESDAY, at(5, 59)));
        assertFalse(s.inSleepWindow(DayOfWeek.WEDNESDAY, at(6, 0)));
        assertFalse(s.inSleepWindow(DayOfWeek.WEDNESDAY, at(23, 0)));
    }

    @Test
    public void noWindowWithoutTimes() {
        assertFalse(ScreenSchedule.from(600, 50, "", "").inSleepWindow(DayOfWeek.SATURDAY, at(12, 0)));
    }

    @Test
    public void idleSleepsInTheWindowAndDimsOutsideIt() {
        assertEquals(ScreenSchedule.Idle.SLEEP, MEETING.afterIdle(DayOfWeek.MONDAY, at(20, 0), true));
        assertEquals(ScreenSchedule.Idle.DIM, MEETING.afterIdle(DayOfWeek.MONDAY, at(14, 0), true));
        assertEquals(ScreenSchedule.Idle.SLEEP, MEETING.afterIdle(DayOfWeek.SUNDAY, at(14, 0), true));
    }

    @Test
    public void idleDimsInTheWindowWhenItCannotSleep() {
        assertEquals(ScreenSchedule.Idle.DIM, MEETING.afterIdle(DayOfWeek.MONDAY, at(20, 0), false));
    }

    @Test
    public void idleDoesNothingWithoutDimmingOutsideTheWindow() {
        ScreenSchedule s = ScreenSchedule.from(600, 0, "20:00", "09:00");
        assertFalse(s.dims());
        assertEquals(ScreenSchedule.Idle.NONE, s.afterIdle(DayOfWeek.MONDAY, at(14, 0), true));
        assertEquals(ScreenSchedule.Idle.SLEEP, s.afterIdle(DayOfWeek.MONDAY, at(21, 0), true));
        assertEquals(ScreenSchedule.Idle.NONE, s.afterIdle(DayOfWeek.MONDAY, at(21, 0), false));
    }

    @Test
    public void zeroIdleSecondsSwitchesEverythingOff() {
        ScreenSchedule s = ScreenSchedule.from(0, 50, "20:00", "09:00");
        assertEquals(ScreenSchedule.Idle.NONE, s.afterIdle(DayOfWeek.MONDAY, at(21, 0), true));
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run (from `launcher/`): `./gradlew testReleaseUnitTest -q --console=plain --tests 'com.gathin.tvhome.ScreenScheduleTest'`
Expected: compilation error, `ScreenSchedule` does not exist.

- [ ] **Step 3: Write the implementation**

```java
package com.gathin.tvhome;

import java.time.DayOfWeek;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Idle dimming and the evening sleep window (config.xml idle_seconds, dim_percent, sleep_time, wake_time).
 * Pure: MainActivity feeds it the day and the minute and acts on the answer.
 */
final class ScreenSchedule {
    static final int MAX_IDLE = 86400;
    static final int MAX_DIM = 95;
    private static final Pattern TIME = Pattern.compile("(\\d{1,2}):(\\d{2})");

    /** What to do when Home has been idle for idleSeconds. */
    enum Idle { NONE, DIM, SLEEP }

    /** Seconds without a key press before dimming or sleeping; 0 switches both off. */
    final int idleSeconds;
    /** Opacity of the black layer, 0..95; 0 never dims. */
    final int dimPercent;
    /** Minute of the day the window opens and closes on weekdays; -1 when there is no window. */
    final int sleepMinute, wakeMinute;

    private ScreenSchedule(int idleSeconds, int dimPercent, int sleepMinute, int wakeMinute) {
        this.idleSeconds = idleSeconds;
        this.dimPercent = dimPercent;
        this.sleepMinute = sleepMinute;
        this.wakeMinute = wakeMinute;
    }

    static ScreenSchedule from(int idleSeconds, int dimPercent, String sleepTime, String wakeTime) {
        int sleep = minute(sleepTime), wake = minute(wakeTime);
        if (sleep < 0 || wake < 0 || sleep == wake) sleep = wake = -1;
        return new ScreenSchedule(Math.max(0, Math.min(MAX_IDLE, idleSeconds)),
                Math.max(0, Math.min(MAX_DIM, dimPercent)), sleep, wake);
    }

    /** "H:mm" or "HH:mm" as a minute of the day; -1 for anything else. */
    static int minute(String text) {
        if (text == null) return -1;
        Matcher m = TIME.matcher(text.trim());
        if (!m.matches()) return -1;
        int hour = Integer.parseInt(m.group(1)), minute = Integer.parseInt(m.group(2));
        if (hour > 23 || minute > 59) return -1;
        return hour * 60 + minute;
    }

    boolean dims() {
        return idleSeconds > 0 && dimPercent > 0;
    }

    boolean sleeps() {
        return sleepMinute >= 0;
    }

    /** Weekdays from sleep_time to wake_time (through midnight when sleep_time is the later); all of Saturday and Sunday. */
    boolean inSleepWindow(DayOfWeek day, int minuteOfDay) {
        if (!sleeps()) return false;
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) return true;
        if (sleepMinute < wakeMinute) return minuteOfDay >= sleepMinute && minuteOfDay < wakeMinute;
        return minuteOfDay >= sleepMinute || minuteOfDay < wakeMinute;
    }

    /** After idleSeconds without a key: sleep inside the window when the device admin allows it, else dim. */
    Idle afterIdle(DayOfWeek day, int minuteOfDay, boolean canSleep) {
        if (idleSeconds == 0) return Idle.NONE;
        if (canSleep && inSleepWindow(day, minuteOfDay)) return Idle.SLEEP;
        return dimPercent > 0 ? Idle.DIM : Idle.NONE;
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew testReleaseUnitTest -q --console=plain --tests 'com.gathin.tvhome.ScreenScheduleTest'`
Expected: no output (all pass).

- [ ] **Step 5: Commit**

```bash
git add launcher/app/src/main/java/com/gathin/tvhome/ScreenSchedule.java launcher/app/src/test/java/com/gathin/tvhome/ScreenScheduleTest.java
git commit -m "launcher: ScreenSchedule, the idle timeout and the evening sleep window"
```

---

### Task 2: `DimKeys`

**Files:**
- Create: `launcher/app/src/main/java/com/gathin/tvhome/DimKeys.java`
- Test: `launcher/app/src/test/java/com/gathin/tvhome/DimKeysTest.java`

**Interfaces:**
- Produces: `enum DimKeys.Action { PASS, WAKE_PASS, WAKE_CONSUME, CONSUME }`, `static Action DimKeys.onKey(int keyCode, int action, boolean dimmed, int swallowKey)`, `static final int NO_KEY = -1`.

- [ ] **Step 1: Write the failing tests**

```java
package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Keys while Home is dimmed: the first press only undims, except volume keys which also pass through. */
public class DimKeysTest {
    // android.view.KeyEvent constants, inlined so the test runs on the JVM.
    private static final int DOWN = 0, UP = 1;
    private static final int DPAD_RIGHT = 22, CENTER = 23, VOLUME_UP = 24, VOLUME_DOWN = 25, MUTE = 164, BACK = 4;

    @Test
    public void notDimmedPassesEverything() {
        assertEquals(DimKeys.Action.PASS, DimKeys.onKey(CENTER, DOWN, false, DimKeys.NO_KEY));
        assertEquals(DimKeys.Action.PASS, DimKeys.onKey(CENTER, UP, false, DimKeys.NO_KEY));
        assertEquals(DimKeys.Action.PASS, DimKeys.onKey(VOLUME_UP, DOWN, false, DimKeys.NO_KEY));
    }

    @Test
    public void dimmedFirstPressWakesAndIsConsumed() {
        assertEquals(DimKeys.Action.WAKE_CONSUME, DimKeys.onKey(DPAD_RIGHT, DOWN, true, DimKeys.NO_KEY));
        assertEquals(DimKeys.Action.WAKE_CONSUME, DimKeys.onKey(BACK, DOWN, true, DimKeys.NO_KEY));
    }

    @Test
    public void dimmedVolumeKeysWakeAndPassThrough() {
        assertEquals(DimKeys.Action.WAKE_PASS, DimKeys.onKey(VOLUME_UP, DOWN, true, DimKeys.NO_KEY));
        assertEquals(DimKeys.Action.WAKE_PASS, DimKeys.onKey(VOLUME_DOWN, DOWN, true, DimKeys.NO_KEY));
        assertEquals(DimKeys.Action.WAKE_PASS, DimKeys.onKey(MUTE, DOWN, true, DimKeys.NO_KEY));
    }

    @Test
    public void dimmedKeyUpIsIgnored() {
        assertEquals(DimKeys.Action.PASS, DimKeys.onKey(DPAD_RIGHT, UP, true, DimKeys.NO_KEY));
    }

    @Test
    public void theSwallowedKeyUpIsConsumedOnce() {
        assertEquals(DimKeys.Action.CONSUME, DimKeys.onKey(DPAD_RIGHT, UP, false, DPAD_RIGHT));
        assertEquals(DimKeys.Action.PASS, DimKeys.onKey(CENTER, UP, false, DPAD_RIGHT));
        assertEquals(DimKeys.Action.PASS, DimKeys.onKey(DPAD_RIGHT, DOWN, false, DPAD_RIGHT));
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew testReleaseUnitTest -q --console=plain --tests 'com.gathin.tvhome.DimKeysTest'`
Expected: compilation error, `DimKeys` does not exist.

- [ ] **Step 3: Write the implementation**

```java
package com.gathin.tvhome;

import android.view.KeyEvent;

/** Keys while Home is dimmed: the first press undims and is swallowed, volume keys undim and pass through. */
final class DimKeys {
    static final int NO_KEY = -1;

    enum Action {
        /** Not ours: let the key through. */
        PASS,
        /** Undim, then let the key through (volume, mute). */
        WAKE_PASS,
        /** Undim and swallow the key; its key-up too (remember it as swallowKey). */
        WAKE_CONSUME,
        /** The key-up of a swallowed press: drop it and forget swallowKey. */
        CONSUME
    }

    private DimKeys() {
    }

    static Action onKey(int keyCode, int action, boolean dimmed, int swallowKey) {
        if (dimmed) {
            if (action != KeyEvent.ACTION_DOWN) return Action.PASS;
            return passesThrough(keyCode) ? Action.WAKE_PASS : Action.WAKE_CONSUME;
        }
        if (action == KeyEvent.ACTION_UP && keyCode == swallowKey) return Action.CONSUME;
        return Action.PASS;
    }

    private static boolean passesThrough(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE || keyCode == KeyEvent.KEYCODE_MUTE;
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew testReleaseUnitTest -q --console=plain --tests 'com.gathin.tvhome.DimKeysTest'`
Expected: pass.

- [ ] **Step 5: Commit**

```bash
git add launcher/app/src/main/java/com/gathin/tvhome/DimKeys.java launcher/app/src/test/java/com/gathin/tvhome/DimKeysTest.java
git commit -m "launcher: DimKeys, the key handling while Home is dimmed"
```

---

### Task 3: Config, resources, theme and docs

**Files:**
- Modify: `launcher/app/src/main/java/com/gathin/tvhome/Config.java` (field + `load`)
- Modify: `launcher/app/src/main/res/values/config.xml` (after `status_bar`)
- Modify: `themes/meeting-room/config.json`, `themes/README.md`

**Interfaces:**
- Consumes: `ScreenSchedule.from(...)`, `ScreenSchedule.MAX_IDLE`, `MAX_DIM`.
- Produces: `Config.screen` (`ScreenSchedule`).

- [ ] **Step 1: Resources**

In `config.xml`, after the `status_bar` bool:

```xml
    <!-- Seconds without a key press before Home dims (dim_percent) or, inside the sleep window, puts the TV to
         sleep; 0 never, at most 86400. -->
    <integer name="idle_seconds">0</integer>
    <!-- Opacity of the black layer over Home after idle_seconds, 0 to 95; 0 never dims. -->
    <integer name="dim_percent">50</integer>
    <!-- The sleep window, HH:mm: from sleep_time to wake_time on weekdays, all of Saturday and Sunday. Inside it an
         idle Home puts the TV to sleep (needs the ScreenAdmin device admin, see build-and-install.sh). Both empty
         keeps the TV awake. -->
    <string name="sleep_time" translatable="false"></string>
    <string name="wake_time" translatable="false"></string>
```

- [ ] **Step 2: Config**

Add the field next to `pages`:

```java
    /** Idle dimming and the evening sleep window (idle_seconds, dim_percent, sleep_time, wake_time). */
    final ScreenSchedule screen;
```

Add a constructor parameter `ScreenSchedule screen` right after `PagesConfig pages` and assign it. In `load`, read the defaults after `statusBar`:

```java
        int idleSeconds = res.getInteger(R.integer.idle_seconds);
        int dimPercent = res.getInteger(R.integer.dim_percent);
        String sleepTime = res.getString(R.string.sleep_time).trim();
        String wakeTime = res.getString(R.string.wake_time).trim();
```

the JSON overrides after `statusBar = bool(...)`:

```java
            idleSeconds = integer(json, "idle_seconds", idleSeconds, 0, ScreenSchedule.MAX_IDLE);
            dimPercent = integer(json, "dim_percent", dimPercent, 0, ScreenSchedule.MAX_DIM);
            sleepTime = string(json, "sleep_time", sleepTime);
            wakeTime = string(json, "wake_time", wakeTime);
```

and pass `ScreenSchedule.from(idleSeconds, dimPercent, sleepTime, wakeTime)` after the `PagesConfig.from(...)` argument. Update the field doc comment of `pages` list only if it names the keys.

- [ ] **Step 3: Theme and README**

`themes/meeting-room/config.json`, after `"status_bar": true,`:

```json
  "idle_seconds": 600,
  "dim_percent": 50,
  "sleep_time": "20:00",
  "wake_time": "09:00",
```

`themes/README.md`, after the `status_bar` row:

```markdown
| `idle_seconds` | 0 to 86400 | Seconds without a key press before Home dims, or sleeps inside the window below; 0 never (the default) |
| `dim_percent` | 0 to 95 | Opacity of the black layer that dims Home after `idle_seconds`; 0 never dims (50 by default). Any key brings the page back; the first press is swallowed unless it is a volume key |
| `sleep_time`, `wake_time` | `HH:mm` | The sleep window: weekdays from `sleep_time` to `wake_time`, all of Saturday and Sunday. Inside it an idle Home puts the TV to sleep like the power key (needs the device admin that `build-and-install.sh` activates). Nothing wakes the TV: the remote's power key does. Both empty keeps the TV awake |
```

and in the "Meeting-room pages" section a sentence: "After 10 minutes without a key the pages dim to half; from 20:00 to 09:00 on weekdays and all weekend the TV goes to sleep instead, and stays asleep until someone presses the remote's power key."

- [ ] **Step 4: Build and test**

Run: `./gradlew testReleaseUnitTest -q --console=plain`
Expected: pass.

- [ ] **Step 5: Commit**

```bash
git add launcher/app/src/main/java/com/gathin/tvhome/Config.java launcher/app/src/main/res/values/config.xml themes/meeting-room/config.json themes/README.md
git commit -m "launcher: idle_seconds, dim_percent, sleep_time and wake_time theme keys"
```

---

### Task 4: Device admin receivers; drop the experiment

**Files:**
- Delete: `launcher/app/src/main/java/com/gathin/tvhome/PowerReceiver.java`
- Keep: `launcher/app/src/main/java/com/gathin/tvhome/ScreenAdmin.java`, `launcher/app/src/main/res/xml/screen_admin.xml`
- Create: `launcher/app/src/main/java/com/gathin/tvhome/ScreenAdminOff.java`
- Modify: `launcher/app/src/main/AndroidManifest.xml`, `launcher/app/src/main/java/com/gathin/tvhome/MainActivity.java` (remove `turnScreenOnIfAsked` and its two calls), `launcher/build-and-install.sh`, `README.md`

- [ ] **Step 1: Remove the experiment**

Delete `PowerReceiver.java`. In `MainActivity.java` remove the `turnScreenOnIfAsked(getIntent());` line in `onCreate`, the `turnScreenOnIfAsked(intent);` line in `onNewIntent`, and the whole `turnScreenOnIfAsked` method. In the manifest remove the `WAKE_LOCK` permission with its comment and the `.PowerReceiver` receiver block.

- [ ] **Step 2: `ScreenAdminOff`**

```java
package com.gathin.tvhome;

import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Deactivates the ScreenAdmin device admin, which an active admin otherwise keeps pm uninstall from
 * removing the launcher. Over adb:
 *   am broadcast -n com.gathin.tvhome/.ScreenAdminOff -a com.gathin.tvhome.SCREEN_ADMIN_OFF
 */
public class ScreenAdminOff extends BroadcastReceiver {
    static final String ACTION = "com.gathin.tvhome.SCREEN_ADMIN_OFF";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (!ACTION.equals(intent.getAction())) return;
        DevicePolicyManager dpm = (DevicePolicyManager) ctx.getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName admin = new ComponentName(ctx, ScreenAdmin.class);
        if (dpm != null && dpm.isAdminActive(admin)) {
            dpm.removeActiveAdmin(admin);
            Log.i(StandbyFeature.TAG, "ScreenAdmin deactivated");
        } else {
            Log.i(StandbyFeature.TAG, "ScreenAdmin was not active");
        }
    }
}
```

Manifest, replacing the `.PowerReceiver` block (and fix the `.ScreenAdmin` comment to "Evening sleep (ScreenSchedule): the device admin lockNow needs; activated over adb by build-and-install.sh."):

```xml
        <receiver
            android:name=".ScreenAdminOff"
            android:exported="true">
            <intent-filter>
                <action android:name="com.gathin.tvhome.SCREEN_ADMIN_OFF" />
            </intent-filter>
        </receiver>
```

- [ ] **Step 3: build-and-install.sh and README**

In the post-install batch (`printf '%s\n' ...`), add after the `cmd package compile` line:

```sh
  "dpm set-active-admin --user 0 com.gathin.tvhome/.ScreenAdmin 2>&1 | head -n 1" \
```

`README.md`, in the launcher section near the `build-and-install.sh` table row, a short paragraph:

"The install also makes the launcher a device admin with the force-lock policy only (`dpm set-active-admin`), which the meeting-room theme's `sleep_time` window uses to put the TV to sleep. An active admin blocks `pm uninstall`; before uninstalling run `am broadcast -n com.gathin.tvhome/.ScreenAdminOff -a com.gathin.tvhome.SCREEN_ADMIN_OFF`."

- [ ] **Step 4: Build**

Run: `./gradlew testReleaseUnitTest lintRelease assembleRelease -q --console=plain`
Expected: pass.

- [ ] **Step 5: Commit**

```bash
git add -A launcher/app/src/main README.md launcher/build-and-install.sh
git commit -m "launcher: ScreenAdmin device admin (force-lock), activated by build-and-install"
```

---

### Task 5: MainActivity: the dim layer, the idle timer and sleeping

**Files:**
- Modify: `launcher/app/src/main/res/layout/activity_main.xml` (after the toast)
- Modify: `launcher/app/src/main/java/com/gathin/tvhome/MainActivity.java`

**Interfaces:**
- Consumes: `Config.screen`, `ScreenSchedule`, `DimKeys`, `ScreenAdmin`.

- [ ] **Step 1: Layout**

After the `HdmiToastView`:

```xml
    <!-- Idle dimming (config.xml idle_seconds, dim_percent): a black layer over everything, faded in when Home is left alone. -->
    <View
        android:id="@+id/dimLayer"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:background="#000000"
        android:alpha="0"
        android:visibility="gone"
        android:focusable="false"
        android:importantForAccessibility="no" />
```

- [ ] **Step 2: Fields and the timer**

Fields next to `returnToDefault`:

```java
    /** Idle dimming and the evening sleep window (config.xml idle_seconds, dim_percent, sleep_time, wake_time). */
    private ScreenSchedule schedule = ScreenSchedule.from(0, 0, "", "");
    private View dimLayer;
    private boolean dimmed;
    /** The key whose key-up is still to be swallowed after it undimmed Home; DimKeys.NO_KEY otherwise. */
    private int swallowKey = DimKeys.NO_KEY;
    /** elapsedRealtime of the last key Home saw (or of onResume). */
    private long lastKeyAt;
    private static final long DIM_MS = 600;
    /** idle_seconds after the last key: dim, or sleep inside the window. */
    private final Runnable idleTimeout = () -> StandbyFeature.run(this::onIdle);
```

In `onCreate` after `pagesConfig = cfg.pages;`: `schedule = cfg.screen;` and after finding views: `dimLayer = findViewById(R.id.dimLayer);`.

Methods near `restartReturnTimer`:

```java
    /** A key, or Home coming to the front: the idle clock starts over. */
    private void restartIdleTimer() {
        lastKeyAt = SystemClock.elapsedRealtime();
        handler.removeCallbacks(idleTimeout);
        if (schedule.idleSeconds > 0) handler.postDelayed(idleTimeout, schedule.idleSeconds * 1000L);
    }

    /** The minute tick: a Home that dimmed before the window opened goes to sleep once it has. */
    private void checkIdle() {
        if (schedule.idleSeconds > 0 && SystemClock.elapsedRealtime() - lastKeyAt >= schedule.idleSeconds * 1000L) onIdle();
    }

    private void onIdle() {
        LocalDateTime now = LocalDateTime.now();
        boolean canSleep = adminActive();
        ScreenSchedule.Idle idle = schedule.afterIdle(now.getDayOfWeek(), now.getHour() * 60 + now.getMinute(), canSleep);
        if (idle == ScreenSchedule.Idle.SLEEP) {
            Log.i(StandbyFeature.TAG, "idle inside the sleep window: sleeping");
            hideDim(false);
            ((DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE)).lockNow();
        } else if (idle == ScreenSchedule.Idle.DIM) {
            if (!canSleep && schedule.inSleepWindow(now.getDayOfWeek(), now.getHour() * 60 + now.getMinute())) {
                Log.w(StandbyFeature.TAG, "ScreenAdmin is not active: dimming instead of sleeping");
            }
            showDim();
        }
    }

    private boolean adminActive() {
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        return dpm != null && dpm.isAdminActive(new ComponentName(this, ScreenAdmin.class));
    }

    private void showDim() {
        if (dimmed) return;
        dimmed = true;
        dimLayer.animate().cancel();
        dimLayer.setVisibility(View.VISIBLE);
        float alpha = schedule.dimPercent / 100f;
        if (animations) dimLayer.animate().alpha(alpha).setDuration(DIM_MS).start();
        else dimLayer.setAlpha(alpha);
    }

    private void hideDim(boolean animate) {
        if (!dimmed) return;
        dimmed = false;
        dimLayer.animate().cancel();
        if (animate && animations) {
            dimLayer.animate().alpha(0f).setDuration(DIM_MS).withEndAction(() -> dimLayer.setVisibility(View.GONE)).start();
        } else {
            dimLayer.setAlpha(0f);
            dimLayer.setVisibility(View.GONE);
        }
    }
```

Imports: `android.app.admin.DevicePolicyManager`, `android.content.ComponentName`, `java.time.LocalDateTime` (check which already exist).

- [ ] **Step 3: Wiring**

`dispatchKeyEvent`, before the `PageKeys` block:

```java
        DimKeys.Action dim = StandbyFeature.run(() -> {
            DimKeys.Action d = DimKeys.onKey(event.getKeyCode(), event.getAction(), dimmed, swallowKey);
            restartIdleTimer();
            if (d == DimKeys.Action.WAKE_CONSUME || d == DimKeys.Action.WAKE_PASS) hideDim(true);
            if (d == DimKeys.Action.WAKE_CONSUME) swallowKey = event.getKeyCode();
            if (d == DimKeys.Action.CONSUME) swallowKey = DimKeys.NO_KEY;
            return d;
        }, DimKeys.Action.PASS);
        if (dim == DimKeys.Action.WAKE_CONSUME || dim == DimKeys.Action.CONSUME) return true;
```

`onUserInteraction`: add `restartIdleTimer();` inside the `StandbyFeature.run`. `onResume`: inside the existing `StandbyFeature.run` block add `restartIdleTimer();`. `onPause`: `handler.removeCallbacks(idleTimeout);` and inside its `StandbyFeature.run`: `hideDim(false);`. `minuteTick`: inside its `StandbyFeature.run`, add `checkIdle();`. `onInputState`, in the SWITCH branch before `toast.show`: `hideDim(true);`.

- [ ] **Step 4: Build**

Run: `./gradlew testReleaseUnitTest lintRelease assembleRelease -q --console=plain`
Expected: pass.

- [ ] **Step 5: Commit**

```bash
git add launcher/app/src/main/res/layout/activity_main.xml launcher/app/src/main/java/com/gathin/tvhome/MainActivity.java
git commit -m "launcher: dim Home when idle; sleep inside the evening window"
```

---

### Task 6: Check on the TV

- [ ] **Step 1: Install** — `TV_HOST=10.181.112.34 STOCK_LAUNCHER=none ./build-and-install.sh` (from `launcher/`); the batch output includes the `dpm` line ("Success: ..." or "already an active admin").
- [ ] **Step 2: Dim** — push a scratch copy of the meeting-room theme with `"idle_seconds": 20` and no window (`"sleep_time": ""`): `TV_HOST=10.181.112.34 tools/theme.sh <scratch>`; then `am force-stop; am start; sleep 30; screencap` → the clock page under a half-black layer; `input keyevent 22; sleep 2; screencap` → the next page at full brightness (the key was swallowed, so the page did NOT change: expect the clock page still, undimmed).
- [ ] **Step 3: Sleep** — scratch theme with a window covering now (e.g. `"sleep_time": "00:00", "wake_time": "23:59"` on a weekday); restart Home, wait 30 s, `dumpsys power | grep -m1 mWakefulness` → `Asleep`; wake with `input keyevent 26`; Home shows the clock; `pm list packages -d | grep -c .` and re-disable what came back if wanted.
- [ ] **Step 4: Restore** — `tools/theme.sh themes/meeting-room`; confirm `logcat -s TvHome.Standby` shows nothing odd.
- [ ] **Step 5: Memory note and commit anything left.**
