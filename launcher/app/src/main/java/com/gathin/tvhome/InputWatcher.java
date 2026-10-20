package com.gathin.tvhome;

import java.util.HashMap;
import java.util.Map;

/**
 * Decides what a change of an HDMI port's state means: a device plugged in switches the TV to that
 * port, the device going away from the port we switched to brings Home back. Only changes count:
 * the states seeded at boot never trigger anything, and a port is only acted on once its previous
 * state is known. Pure Java; MainActivity feeds it TvInputManager callbacks and runs the actions.
 */
final class InputWatcher {
    /** android.media.tv.TvInputManager.INPUT_STATE_* */
    static final int CONNECTED = 0, STANDBY = 1, DISCONNECTED = 2;
    /** Two switches closer than this are one loose plug. */
    static final long DEBOUNCE_MS = 3000;

    enum Kind { NONE, SWITCH, HOME }

    static final class Action {
        static final Action NONE = new Action(Kind.NONE, null);
        final Kind kind;
        final String inputId;

        private Action(Kind kind, String inputId) {
            this.kind = kind;
            this.inputId = inputId;
        }
    }

    private final boolean enabled;
    private final Map<String, Integer> lastState = new HashMap<>();
    private String current;
    private long lastSwitch = Long.MIN_VALUE / 2;

    InputWatcher(boolean enabled) {
        this.enabled = enabled;
    }

    /** The state at boot, so a cable already in never triggers a switch. */
    void seed(String inputId, int state) {
        lastState.put(inputId, state);
    }

    Action onState(String inputId, boolean hdmi, int state, long nowMillis) {
        if (!enabled || !hdmi || inputId == null) return Action.NONE;
        Integer previous = lastState.put(inputId, state);
        if (previous == null || previous == state) return Action.NONE;
        if (state == CONNECTED) {
            if (nowMillis - lastSwitch < DEBOUNCE_MS) return Action.NONE;
            lastSwitch = nowMillis;
            current = inputId;
            return new Action(Kind.SWITCH, inputId);
        }
        if (inputId.equals(current)) {
            current = null;
            return new Action(Kind.HOME, inputId);
        }
        return Action.NONE;
    }

    /** The owner switched to this port from the Inputs menu: unplugging it should come Home too. */
    void track(String inputId) {
        if (enabled) current = inputId;
    }

    /** The switch intent went nowhere: forget the port so the next plug-in can try again. */
    void switchFailed() {
        current = null;
    }

    /** The port the TV was switched to, or null. */
    String current() {
        return current;
    }
}
