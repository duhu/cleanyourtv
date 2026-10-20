package com.gathin.tvhome;

import java.util.Set;

/** Which packages get a tile: hidden_apps never, and with a non-empty only_apps list, nothing outside it. */
final class TileFilter {
    private TileFilter() {
    }

    static boolean shows(String pkg, Set<String> hidden, Set<String> only) {
        if (hidden.contains(pkg)) return false;
        return only.isEmpty() || only.contains(pkg);
    }
}
