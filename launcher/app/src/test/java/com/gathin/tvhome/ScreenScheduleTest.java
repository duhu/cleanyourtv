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
        assertFalse(ScreenSchedule.from(600, 50, null, "09:00").sleeps());
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
