package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The validated standby settings: what a theme's config.json turns into. */
public class StandbyConfigTest {

    private static StandbyConfig full() {
        return StandbyConfig.from(true, 30, "31.30", "120.58", "https://git.example.com/", "tok",
                Arrays.asList("team/one", " team/two ", ""), "", true);
    }

    @Test
    public void everythingOffByDefault() {
        StandbyConfig c = StandbyConfig.from(false, 30, "", "", "", "", Collections.emptyList(), "", false);
        assertFalse(c.enabled);
        assertFalse(c.hdmiAutoSwitch);
        assertFalse(c.hasWeather());
        assertFalse(c.hasGitlab());
        assertFalse(c.hasFeed());
        assertFalse(c.hasSources());
    }

    @Test
    public void aFullConfigIsKeptAndTidied() {
        StandbyConfig c = full();
        assertTrue(c.enabled);
        assertEquals(30, c.idleSeconds);
        assertEquals(31.30, c.weatherLat, 1e-9);
        assertEquals(120.58, c.weatherLon, 1e-9);
        assertTrue(c.hasWeather());
        assertEquals("https://git.example.com", c.gitlabUrl);     // trailing slash dropped
        assertEquals("tok", c.gitlabToken);
        assertEquals(Arrays.asList("team/one", "team/two"), c.gitlabProjects);   // trimmed, blanks dropped
        assertTrue(c.hasGitlab());
        assertFalse(c.hasFeed());
        assertTrue(c.hasSources());
    }

    @Test
    public void idleSecondsClampToRange() {
        assertEquals(5, StandbyConfig.from(true, 1, "", "", "", "", Collections.emptyList(), "", false).idleSeconds);
        assertEquals(3600, StandbyConfig.from(true, 99999, "", "", "", "", Collections.emptyList(), "", false).idleSeconds);
    }

    @Test
    public void weatherNeedsBothCoordinatesAsNumbers() {
        assertFalse(StandbyConfig.from(true, 30, "31.3", "", "", "", Collections.emptyList(), "", false).hasWeather());
        assertFalse(StandbyConfig.from(true, 30, "north", "120", "", "", Collections.emptyList(), "", false).hasWeather());
        assertFalse(StandbyConfig.from(true, 30, "91", "120", "", "", Collections.emptyList(), "", false).hasWeather());
        assertTrue(StandbyConfig.from(true, 30, "-33.9", "151.2", "", "", Collections.emptyList(), "", false).hasWeather());
    }

    @Test
    public void gitlabNeedsUrlTokenAndProjects() {
        assertFalse(StandbyConfig.from(true, 30, "", "", "https://g", "REPLACE_ME", Arrays.asList("a/b"), "", false).hasGitlab());
        assertFalse(StandbyConfig.from(true, 30, "", "", "https://g", " ", Arrays.asList("a/b"), "", false).hasGitlab());
        assertFalse(StandbyConfig.from(true, 30, "", "", "", "tok", Arrays.asList("a/b"), "", false).hasGitlab());
        assertFalse(StandbyConfig.from(true, 30, "", "", "https://g", "tok", Collections.singletonList(" "), "", false).hasGitlab());
        assertFalse(StandbyConfig.from(true, 30, "", "", "ftp://g", "tok", Arrays.asList("a/b"), "", false).hasGitlab());
    }

    @Test
    public void aFeedUrlIsASourceOnItsOwn() {
        StandbyConfig c = StandbyConfig.from(true, 30, "", "", "", "", Collections.emptyList(), "http://10.0.0.5/status.json", false);
        assertTrue(c.hasFeed());
        assertTrue(c.hasSources());
        assertFalse(StandbyConfig.from(true, 30, "", "", "", "", Collections.emptyList(), "status.json", false).hasFeed());
    }
}
