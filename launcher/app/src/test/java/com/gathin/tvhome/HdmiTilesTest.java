package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class HdmiTilesTest {
    private static final HdmiTiles.Port P1 = new HdmiTiles.Port("hdmi/HW5", "HDMI1");
    private static final HdmiTiles.Port P2 = new HdmiTiles.Port("hdmi/HW6", "HDMI2");
    private static final HdmiTiles.Port P3 = new HdmiTiles.Port("hdmi/HW7", "HDMI3");

    @Test
    public void labelKeepsFirmwareNamesThatSayHdmi() {
        assertEquals("HDMI 2", HdmiTiles.label("HDMI2"));
        assertEquals("hdmi 2", HdmiTiles.label("hdmi 2"));
        assertEquals("HDMI 1 (ARC)", HdmiTiles.label("HDMI 1 (ARC)"));
    }

    @Test
    public void labelPrefixesBareNumbersAndFallsBackWhenEmpty() {
        assertEquals("HDMI 2", HdmiTiles.label("2"));
        assertEquals("HDMI", HdmiTiles.label(""));
        assertEquals("HDMI", HdmiTiles.label(null));
    }

    @Test
    public void connectedListsOnlyPluggedPortsInOrder() {
        List<HdmiTiles.Port> ports = Arrays.asList(P1, P2, P3);
        List<HdmiTiles.Port> got = HdmiTiles.connected(ports,
                Arrays.asList(InputWatcher.STANDBY, InputWatcher.CONNECTED, InputWatcher.DISCONNECTED));
        assertEquals(1, got.size());
        assertEquals("hdmi/HW6", got.get(0).inputId);
    }

    @Test
    public void connectedIsEmptyWhenEveryPortClaimsADevice() {
        List<HdmiTiles.Port> ports = Arrays.asList(P1, P2, P3);
        assertTrue(HdmiTiles.connected(ports,
                Arrays.asList(InputWatcher.CONNECTED, InputWatcher.CONNECTED, InputWatcher.CONNECTED)).isEmpty());
    }

    @Test
    public void connectedTrustsASingleConnectedPort() {
        assertEquals(1, HdmiTiles.connected(Collections.singletonList(P2),
                Collections.singletonList(InputWatcher.CONNECTED)).size());
    }

    @Test
    public void connectedIgnoresMismatchedInput() {
        assertTrue(HdmiTiles.connected(Arrays.asList(P1, P2), Collections.singletonList(InputWatcher.CONNECTED)).isEmpty());
    }
}
