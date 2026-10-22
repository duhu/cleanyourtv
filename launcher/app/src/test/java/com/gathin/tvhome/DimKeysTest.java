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
