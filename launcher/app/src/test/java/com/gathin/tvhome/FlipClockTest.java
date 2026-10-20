package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

/** The four digits the flip clock shows for an hour and minute. */
public class FlipClockTest {

    @Test
    public void twentyFourHourIsZeroPadded() {
        assertArrayEquals("00:05".replace(":", "").toCharArray(), FlipClockView.digits(0, 5, true));
        assertArrayEquals("14:18".replace(":", "").toCharArray(), FlipClockView.digits(14, 18, true));
        assertArrayEquals("23:59".replace(":", "").toCharArray(), FlipClockView.digits(23, 59, true));
    }

    @Test
    public void twelveHourShowsTwelveAtMidnightAndNoon() {
        assertArrayEquals("12:00".replace(":", "").toCharArray(), FlipClockView.digits(0, 0, false));
        assertArrayEquals("12:30".replace(":", "").toCharArray(), FlipClockView.digits(12, 30, false));
        assertArrayEquals("02:30".replace(":", "").toCharArray(), FlipClockView.digits(14, 30, false));
        assertArrayEquals("11:59".replace(":", "").toCharArray(), FlipClockView.digits(23, 59, false));
    }
}
