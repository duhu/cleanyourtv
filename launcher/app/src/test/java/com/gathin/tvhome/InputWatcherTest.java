package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** Plug a laptop in: switch to it. Unplug it: back to Home. Nothing else moves the TV. */
public class InputWatcherTest {

    private static final String HDMI1 = "com.droidlogic.tvinput/.services.Hdmi1InputService/HW5";
    private static final String HDMI2 = "com.droidlogic.tvinput/.services.Hdmi2InputService/HW6";
    private static final String AV = "com.droidlogic.tvinput/.services.AV1InputService/HW1";

    private static InputWatcher booted() {
        InputWatcher w = new InputWatcher(true);
        w.seed(HDMI1, InputWatcher.STANDBY);
        w.seed(HDMI2, InputWatcher.STANDBY);
        w.seed(AV, InputWatcher.STANDBY);
        return w;
    }

    @Test
    public void plugInSwitchesToThatPort() {
        InputWatcher w = booted();
        InputWatcher.Action a = w.onState(HDMI2, true, InputWatcher.CONNECTED, 10_000);
        assertEquals(InputWatcher.Kind.SWITCH, a.kind);
        assertEquals(HDMI2, a.inputId);
        assertEquals(HDMI2, w.current());
    }

    @Test
    public void unplugOfTheCurrentPortGoesHome() {
        InputWatcher w = booted();
        w.onState(HDMI2, true, InputWatcher.CONNECTED, 10_000);
        InputWatcher.Action a = w.onState(HDMI2, true, InputWatcher.STANDBY, 20_000);
        assertEquals(InputWatcher.Kind.HOME, a.kind);
        assertNull(w.current());
        // and DISCONNECTED counts as gone too
        w.onState(HDMI2, true, InputWatcher.CONNECTED, 30_000);
        assertEquals(InputWatcher.Kind.HOME, w.onState(HDMI2, true, InputWatcher.DISCONNECTED, 40_000).kind);
    }

    @Test
    public void unplugOfAnotherPortDoesNothing() {
        InputWatcher w = booted();
        w.onState(HDMI2, true, InputWatcher.CONNECTED, 10_000);
        w.onState(HDMI1, true, InputWatcher.CONNECTED, 11_000);       // inside the debounce: ignored, HDMI2 stays current
        assertEquals(InputWatcher.Kind.NONE, w.onState(HDMI1, true, InputWatcher.STANDBY, 30_000).kind);
        assertEquals(HDMI2, w.current());
    }

    @Test
    public void aCableAlreadyInAtBootIsIgnored() {
        InputWatcher w = new InputWatcher(true);
        w.seed(HDMI2, InputWatcher.CONNECTED);
        assertEquals(InputWatcher.Kind.NONE, w.onState(HDMI2, true, InputWatcher.CONNECTED, 10_000).kind);
        // Unplugging it is not "our" port either: the TV was never switched by us.
        assertEquals(InputWatcher.Kind.NONE, w.onState(HDMI2, true, InputWatcher.STANDBY, 20_000).kind);
        // Plugging it back in after that is a real change.
        assertEquals(InputWatcher.Kind.SWITCH, w.onState(HDMI2, true, InputWatcher.CONNECTED, 30_000).kind);
    }

    @Test
    public void firstReportOfAnUnseededPortIsIgnored() {
        InputWatcher w = booted();
        String hdmi3 = "com.droidlogic.tvinput/.services.Hdmi3InputService/HW7";
        assertEquals(InputWatcher.Kind.NONE, w.onState(hdmi3, true, InputWatcher.CONNECTED, 10_000).kind);
        assertEquals(InputWatcher.Kind.NONE, w.onState(hdmi3, true, InputWatcher.STANDBY, 20_000).kind);
        assertEquals(InputWatcher.Kind.SWITCH, w.onState(hdmi3, true, InputWatcher.CONNECTED, 30_000).kind);
    }

    @Test
    public void nonHdmiAndRepeatsAreIgnored() {
        InputWatcher w = booted();
        assertEquals(InputWatcher.Kind.NONE, w.onState(AV, false, InputWatcher.CONNECTED, 10_000).kind);
        w.onState(HDMI2, true, InputWatcher.CONNECTED, 20_000);
        assertEquals(InputWatcher.Kind.NONE, w.onState(HDMI2, true, InputWatcher.CONNECTED, 30_000).kind);   // same state again
    }

    @Test
    public void debounceWithinThreeSeconds() {
        InputWatcher w = booted();
        assertEquals(InputWatcher.Kind.SWITCH, w.onState(HDMI2, true, InputWatcher.CONNECTED, 10_000).kind);
        w.onState(HDMI2, true, InputWatcher.STANDBY, 10_500);           // loose plug: goes home
        assertEquals(InputWatcher.Kind.NONE, w.onState(HDMI2, true, InputWatcher.CONNECTED, 11_000).kind);
        w.onState(HDMI2, true, InputWatcher.STANDBY, 12_000);           // not ours any more: nothing
        assertEquals(InputWatcher.Kind.SWITCH, w.onState(HDMI2, true, InputWatcher.CONNECTED, 13_001).kind);
    }

    @Test
    public void disabledWatcherNeverActs() {
        InputWatcher w = new InputWatcher(false);
        w.seed(HDMI2, InputWatcher.STANDBY);
        assertEquals(InputWatcher.Kind.NONE, w.onState(HDMI2, true, InputWatcher.CONNECTED, 10_000).kind);
        assertNull(w.current());
    }

    @Test
    public void aManualSwitchIsTrackedAndAFailedSwitchForgotten() {
        // The cable was in at boot and the owner picked HDMI 1 from the Inputs menu.
        InputWatcher w = new InputWatcher(true);
        w.seed(HDMI1, InputWatcher.CONNECTED);
        w.seed(HDMI2, InputWatcher.STANDBY);
        w.track(HDMI1);
        assertEquals(InputWatcher.Kind.HOME, w.onState(HDMI1, true, InputWatcher.STANDBY, 10_000).kind);
        w.onState(HDMI2, true, InputWatcher.CONNECTED, 20_000);
        w.switchFailed();
        assertNull(w.current());
        assertEquals(InputWatcher.Kind.NONE, w.onState(HDMI2, true, InputWatcher.STANDBY, 30_000).kind);
    }
}
