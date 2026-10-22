package com.gathin.tvhome;

import android.view.KeyEvent;

/** Keys while Home is dimmed: the first press undims and is swallowed, volume keys undim and pass through. */
final class DimKeys {
    static final int NO_KEY = -1;

    enum Action {
        /** Not ours: let the key through. */
        PASS,
        /** Undim, then let the key through (volume, mute). */
        WAKE_PASS,
        /** Undim and swallow the key; its key-up too (remember it as swallowKey). */
        WAKE_CONSUME,
        /** The key-up of a swallowed press: drop it and forget swallowKey. */
        CONSUME
    }

    private DimKeys() {
    }

    static Action onKey(int keyCode, int action, boolean dimmed, int swallowKey) {
        if (dimmed) {
            if (action != KeyEvent.ACTION_DOWN) return Action.PASS;
            return passesThrough(keyCode) ? Action.WAKE_PASS : Action.WAKE_CONSUME;
        }
        if (action == KeyEvent.ACTION_UP && keyCode == swallowKey) return Action.CONSUME;
        return Action.PASS;
    }

    private static boolean passesThrough(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE || keyCode == KeyEvent.KEYCODE_MUTE;
    }
}
