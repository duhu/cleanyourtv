package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/** The six digits the flip clock shows for a time, and when the next second tick is due. */
public class FlipClockTest {

    private static char[] chars(String hhmmss) {
        return hhmmss.replace(":", "").toCharArray();
    }

    @Test
    public void twentyFourHourIsZeroPadded() {
        assertArrayEquals(chars("00:05:07"), FlipClockView.digits(0, 5, 7, true));
        assertArrayEquals(chars("14:18:00"), FlipClockView.digits(14, 18, 0, true));
        assertArrayEquals(chars("23:59:59"), FlipClockView.digits(23, 59, 59, true));
    }

    @Test
    public void twelveHourShowsTwelveAtMidnightAndNoon() {
        assertArrayEquals(chars("12:00:00"), FlipClockView.digits(0, 0, 0, false));
        assertArrayEquals(chars("12:30:45"), FlipClockView.digits(12, 30, 45, false));
        assertArrayEquals(chars("02:30:10"), FlipClockView.digits(14, 30, 10, false));
        assertArrayEquals(chars("11:59:59"), FlipClockView.digits(23, 59, 59, false));
    }

    @Test
    public void nextTickLandsOnTheSecondBoundary() {
        assertEquals(1000, FlipClockView.msToNextSecond(0));
        assertEquals(1, FlipClockView.msToNextSecond(999));
        assertEquals(766, FlipClockView.msToNextSecond(1234));
        assertEquals(1000, FlipClockView.msToNextSecond(5000));
    }
}
