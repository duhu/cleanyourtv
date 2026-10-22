package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The validated page and source settings: what a theme's config.json turns into. */
public class PagesConfigTest {
    private static final List<String> NONE = Collections.emptyList();

    /** Pages and the rest at their config.xml defaults, with the given coordinates. */
    private static PagesConfig pages(List<String> pages, String defaultPage, String lat, String lon) {
        return PagesConfig.from(pages, defaultPage, 600, true, false, lat, lon, "", "", NONE, "", false);
    }

    private static PagesConfig sources(String lat, String lon, String gitlabUrl, String token, List<String> projects, String feedUrl) {
        return PagesConfig.from(Arrays.asList("apps"), "apps", 600, true, false, lat, lon, gitlabUrl, token, projects, feedUrl, false);
    }

    @Test
    public void defaultsAreTheLauncherAlone() {
        PagesConfig c = pages(Arrays.asList("apps"), "apps", "", "");
        assertEquals(Arrays.asList("apps"), c.pages);
        assertEquals(0, c.defaultIndex);
        assertEquals(600, c.returnSeconds);
        assertTrue(c.flipClock);
        assertFalse(c.statusBar);
        assertFalse(c.multiPage());
        assertFalse(c.needsFeed());
        assertFalse(c.hdmiAutoSwitch);
        assertFalse(c.hasSources());
    }

    @Test
    public void pageNamesAreTidiedAndUnknownOnesDropped() {
        PagesConfig c = pages(Arrays.asList(" Clock", "weather", "x", "clock", "", null, "APPS"), "clock", "31.3", "120.6");
        assertEquals(Arrays.asList("clock", "weather", "apps"), c.pages);
        assertEquals(0, c.defaultIndex);
        assertEquals(2, c.indexOf("apps"));
        assertEquals(-1, c.indexOf("ci"));
        assertTrue(c.has("weather"));
        assertTrue(c.multiPage());
    }

    @Test
    public void weatherPageNeedsCoordinates() {
        PagesConfig c = pages(Arrays.asList("clock", "weather"), "weather", "", "");
        assertEquals(Arrays.asList("clock"), c.pages);
        assertEquals(0, c.defaultIndex);     // the default page went with the weather
    }

    @Test
    public void emptyOrNullListsFallBackToApps() {
        assertEquals(Arrays.asList("apps"), pages(NONE, "clock", "", "").pages);
        assertEquals(Arrays.asList("apps"), pages(Arrays.asList("x"), "", "", "").pages);
        assertEquals(Arrays.asList("apps"), pages(null, null, "", "").pages);
    }

    @Test
    public void defaultPageOutsideTheListIsTheFirst() {
        PagesConfig c = pages(Arrays.asList("apps", "clock"), "weather", "", "");
        assertEquals(0, c.defaultIndex);
        assertEquals(1, pages(Arrays.asList("apps", "clock"), " CLOCK ", "", "").defaultIndex);
    }

    @Test
    public void returnSecondsClamp() {
        assertEquals(0, PagesConfig.from(Arrays.asList("apps"), "apps", -5, true, false, "", "", "", "", NONE, "", false).returnSeconds);
        assertEquals(86400, PagesConfig.from(Arrays.asList("apps"), "apps", 99999, true, false, "", "", "", "", NONE, "", false).returnSeconds);
    }

    @Test
    public void theFeedIsNeededByTheBarAndTheInfoPages() {
        assertTrue(PagesConfig.from(Arrays.asList("apps"), "apps", 600, true, true, "", "", "", "", NONE, "", false).needsFeed());
        assertTrue(pages(Arrays.asList("apps", "clock"), "clock", "", "").needsFeed());
        assertTrue(pages(Arrays.asList("weather"), "weather", "31.3", "120.6").needsFeed());
        assertFalse(pages(Arrays.asList("apps"), "apps", "31.3", "120.6").needsFeed());
    }

    @Test
    public void aFullSourceConfigIsKeptAndTidied() {
        PagesConfig c = sources("31.30", "120.58", "https://git.example.com/", "tok", Arrays.asList("team/one", " team/two ", ""), "");
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
    public void weatherNeedsBothCoordinatesAsNumbers() {
        assertFalse(sources("31.3", "", "", "", NONE, "").hasWeather());
        assertFalse(sources("north", "120", "", "", NONE, "").hasWeather());
        assertFalse(sources("91", "120", "", "", NONE, "").hasWeather());
        assertTrue(sources("-33.9", "151.2", "", "", NONE, "").hasWeather());
    }

    @Test
    public void gitlabNeedsUrlTokenAndProjects() {
        assertFalse(sources("", "", "https://g", "REPLACE_ME", Arrays.asList("a/b"), "").hasGitlab());
        assertFalse(sources("", "", "https://g", " ", Arrays.asList("a/b"), "").hasGitlab());
        assertFalse(sources("", "", "", "tok", Arrays.asList("a/b"), "").hasGitlab());
        assertFalse(sources("", "", "https://g", "tok", Collections.singletonList(" "), "").hasGitlab());
        assertFalse(sources("", "", "ftp://g", "tok", Arrays.asList("a/b"), "").hasGitlab());
    }

    @Test
    public void aFeedUrlIsASourceOnItsOwn() {
        PagesConfig c = sources("", "", "", "", NONE, "http://10.0.0.5/status.json");
        assertTrue(c.hasFeed());
        assertTrue(c.hasSources());
        assertFalse(sources("", "", "", "", NONE, "status.json").hasFeed());
    }
}
