package com.gathin.tvhome;

import android.view.KeyEvent;

/**
 * What a remote key does while Home shows one of its pages (docs/superpowers/specs/2026-10-22-home-pager-design.md).
 * On the apps page every key keeps its ordinary meaning (focus navigation; the row ends flip pages
 * from MainActivity). On the other pages Left and Right flip, OK jumps to the apps page, Back to the
 * default page, Up and Down do nothing, and everything else (volume, mute, the TV's own keys) passes
 * through untouched. Pure, so the mapping is unit-tested.
 */
final class PageKeys {
    enum Action { PASS, CONSUME, PREV, NEXT, APPS, DEFAULT }

    private PageKeys() {
    }

    static Action onKey(int keyCode, int action, int repeatCount, boolean onAppsPage) {
        if (onAppsPage || !ours(keyCode)) return Action.PASS;
        if (action != KeyEvent.ACTION_DOWN) return Action.CONSUME;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return repeatCount > 0 ? Action.CONSUME : Action.PREV;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return repeatCount > 0 ? Action.CONSUME : Action.NEXT;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                return Action.APPS;
            case KeyEvent.KEYCODE_BACK:
                return Action.DEFAULT;
            default:
                return Action.CONSUME;
        }
    }

    private static boolean ours(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BACK:
                return true;
            default:
                return false;
        }
    }
}
