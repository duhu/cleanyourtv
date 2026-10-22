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
