package com.gathin.tvhome;

import android.view.KeyEvent;

import org.junit.Test;

import static com.gathin.tvhome.PageKeys.Action;
import static org.junit.Assert.assertEquals;

/** The remote keys on a page that is not the apps page. */
public class PageKeysTest {
    private static Action down(int keyCode) {
        return PageKeys.onKey(keyCode, KeyEvent.ACTION_DOWN, 0, false);
    }

    @Test
    public void theAppsPageKeepsEveryKey() {
        assertEquals(Action.PASS, PageKeys.onKey(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.ACTION_DOWN, 0, true));
        assertEquals(Action.PASS, PageKeys.onKey(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.ACTION_DOWN, 0, true));
        assertEquals(Action.PASS, PageKeys.onKey(KeyEvent.KEYCODE_BACK, KeyEvent.ACTION_UP, 0, true));
    }

    @Test
    public void leftAndRightFlip() {
        assertEquals(Action.PREV, down(KeyEvent.KEYCODE_DPAD_LEFT));
        assertEquals(Action.NEXT, down(KeyEvent.KEYCODE_DPAD_RIGHT));
    }

    @Test
    public void aHeldKeyFlipsOnce() {
        assertEquals(Action.CONSUME, PageKeys.onKey(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.ACTION_DOWN, 3, false));
    }

    @Test
    public void okGoesToAppsAndBackToTheDefault() {
        assertEquals(Action.APPS, down(KeyEvent.KEYCODE_DPAD_CENTER));
        assertEquals(Action.APPS, down(KeyEvent.KEYCODE_ENTER));
        assertEquals(Action.DEFAULT, down(KeyEvent.KEYCODE_BACK));
    }

    @Test
    public void upAndDownDoNothing() {
        assertEquals(Action.CONSUME, down(KeyEvent.KEYCODE_DPAD_UP));
        assertEquals(Action.CONSUME, down(KeyEvent.KEYCODE_DPAD_DOWN));
    }

    @Test
    public void keyUpsOfOurKeysAreSwallowedToo() {
        assertEquals(Action.CONSUME, PageKeys.onKey(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.ACTION_UP, 0, false));
        assertEquals(Action.CONSUME, PageKeys.onKey(KeyEvent.KEYCODE_BACK, KeyEvent.ACTION_UP, 0, false));
    }

    @Test
    public void otherKeysPassThrough() {
        assertEquals(Action.PASS, down(KeyEvent.KEYCODE_VOLUME_UP));
        assertEquals(Action.PASS, down(KeyEvent.KEYCODE_HOME));
        assertEquals(Action.PASS, down(KeyEvent.KEYCODE_MENU));
        assertEquals(Action.PASS, PageKeys.onKey(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.ACTION_UP, 0, false));
    }
}
