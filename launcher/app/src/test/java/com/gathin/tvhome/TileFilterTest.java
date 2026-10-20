package com.gathin.tvhome;

import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** hidden_apps and the only_apps allowlist. */
public class TileFilterTest {
    private static Set<String> set(String... s) {
        return new HashSet<>(java.util.Arrays.asList(s));
    }

    @Test
    public void everythingShowsWithoutLists() {
        assertTrue(TileFilter.shows("a.b", Collections.emptySet(), Collections.emptySet()));
    }

    @Test
    public void hiddenNeverShows() {
        assertFalse(TileFilter.shows("a.b", set("a.b"), Collections.emptySet()));
        assertFalse(TileFilter.shows("a.b", set("a.b"), set("a.b")));   // hidden wins over the allowlist
    }

    @Test
    public void onlyListKeepsJustItsPackages() {
        assertTrue(TileFilter.shows("com.xiaomi.mitv.smartshare", Collections.emptySet(), set("com.xiaomi.mitv.smartshare")));
        assertFalse(TileFilter.shows("com.xiaomi.mitv.appstore", Collections.emptySet(), set("com.xiaomi.mitv.smartshare")));
    }
}
