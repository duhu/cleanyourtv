package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Quick-switch tiles for HDMI ports with a device plugged in (config.xml hdmi_tiles): one tile per
 * connected port, in the dock row after the dock apps. Pure Java; MainActivity feeds it the
 * TvInputManager states and AppAdapter shows the result.
 */
final class HdmiTiles {
    /** One connected port: the framework input id and the label the tile shows. */
    static final class Port {
        final String inputId;
        final String label;

        Port(String inputId, String label) {
            this.inputId = inputId;
            this.label = label;
        }
    }

    /**
     * The label of a port the way Home names it: the firmware's label, prefixed when it lacks "HDMI"
     * ("2" → "HDMI 2") and spaced when it runs the word and the number together ("HDMI2" → "HDMI 2").
     */
    static String label(CharSequence firmwareLabel) {
        String label = firmwareLabel == null ? "" : firmwareLabel.toString().trim();
        if (label.isEmpty()) return "HDMI";
        if (!label.toUpperCase(Locale.ROOT).contains("HDMI")) return "HDMI " + label;
        return label.replaceFirst("(?i)^(hdmi)(\\d+)$", "$1 $2");
    }

    /**
     * The ports to show as tiles, in the order given: those whose state is CONNECTED. Nothing when the
     * firmware reports every one of several ports as connected, which means it cannot tell (see the
     * Inputs menu's same check), so no tile can lie.
     */
    static List<Port> connected(List<Port> ports, List<Integer> states) {
        List<Port> out = new ArrayList<>();
        if (ports.size() != states.size()) return out;
        for (int i = 0; i < ports.size(); i++) {
            if (states.get(i) == InputWatcher.CONNECTED) out.add(ports.get(i));
        }
        if (ports.size() > 1 && out.size() == ports.size()) out.clear();
        return out;
    }

    private HdmiTiles() {
    }
}
