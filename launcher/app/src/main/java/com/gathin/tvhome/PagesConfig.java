package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The home pages and their data sources (config.xml, overlaid by a theme's config.json; see Config):
 * which pages Home has and in what order, the page it rests on, how long another page stays before
 * the default one comes back, and where the weather and CI status come from. Built from raw values so
 * the checks run in plain JVM tests; a value that does not make sense is dropped or clamped, never a
 * reason to fail the home screen.
 */
final class PagesConfig {
    static final String PAGE_APPS = "apps", PAGE_CLOCK = "clock", PAGE_WEATHER = "weather";
    static final int MAX_RETURN = 86400;
    /** The token the shipped theme carries until the owner replaces it. */
    static final String TOKEN_PLACEHOLDER = "REPLACE_ME";

    /** Page names in Left-to-Right order; never empty. */
    final List<String> pages;
    /** Index into pages of the page shown at launch and after idling. */
    final int defaultIndex;
    /** Seconds on another page before the default one comes back; 0 never. */
    final int returnSeconds;
    /** Split-flap clock cards instead of plain digits. */
    final boolean flipClock;
    /** The HDMI hint and CI cells at the bottom of every page. */
    final boolean statusBar;
    /** NaN when weather is off. */
    final double weatherLat, weatherLon;
    /** Base URL without a trailing slash, or "" when GitLab is off. */
    final String gitlabUrl;
    final String gitlabToken;
    /** group/project paths, trimmed, no blanks. */
    final List<String> gitlabProjects;
    /** One JSON for everything; when set, the weather and GitLab sources are not used. */
    final String feedUrl;
    final boolean hdmiAutoSwitch;

    private PagesConfig(List<String> pages, int defaultIndex, int returnSeconds, boolean flipClock, boolean statusBar,
                        double lat, double lon, String gitlabUrl, String gitlabToken, List<String> gitlabProjects,
                        String feedUrl, boolean hdmiAutoSwitch) {
        this.pages = Collections.unmodifiableList(pages);
        this.defaultIndex = defaultIndex;
        this.returnSeconds = returnSeconds;
        this.flipClock = flipClock;
        this.statusBar = statusBar;
        this.weatherLat = lat;
        this.weatherLon = lon;
        this.gitlabUrl = gitlabUrl;
        this.gitlabToken = gitlabToken;
        this.gitlabProjects = Collections.unmodifiableList(gitlabProjects);
        this.feedUrl = feedUrl;
        this.hdmiAutoSwitch = hdmiAutoSwitch;
    }

    static PagesConfig from(List<String> pageNames, String defaultPage, int returnSeconds, boolean flipClock, boolean statusBar,
                            String lat, String lon, String gitlabUrl, String gitlabToken, List<String> gitlabProjects,
                            String feedUrl, boolean hdmiAutoSwitch) {
        double la = coordinate(lat, 90), lo = coordinate(lon, 180);
        if (Double.isNaN(la) || Double.isNaN(lo)) la = lo = Double.NaN;
        List<String> pages = new ArrayList<>();
        if (pageNames != null) {
            for (String p : pageNames) {
                String name = pageName(p);
                if (name.isEmpty() || pages.contains(name)) continue;
                if (PAGE_WEATHER.equals(name) && Double.isNaN(la)) continue;   // nothing to show without coordinates
                pages.add(name);
            }
        }
        if (pages.isEmpty()) pages.add(PAGE_APPS);
        int defaultIndex = Math.max(0, pages.indexOf(pageName(defaultPage)));
        int ret = Math.max(0, Math.min(MAX_RETURN, returnSeconds));
        String url = httpUrl(gitlabUrl);
        while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        String token = gitlabToken == null ? "" : gitlabToken.trim();
        if (TOKEN_PLACEHOLDER.equals(token)) token = "";
        List<String> projects = new ArrayList<>();
        if (gitlabProjects != null) {
            for (String p : gitlabProjects) {
                if (p == null) continue;
                String t = p.trim();
                if (!t.isEmpty()) projects.add(t);
            }
        }
        return new PagesConfig(pages, defaultIndex, ret, flipClock, statusBar, la, lo, url, token, projects,
                httpUrl(feedUrl), hdmiAutoSwitch);
    }

    /** A known page name from raw text, or "". */
    private static String pageName(String text) {
        if (text == null) return "";
        String t = text.trim().toLowerCase(Locale.ROOT);
        return PAGE_APPS.equals(t) || PAGE_CLOCK.equals(t) || PAGE_WEATHER.equals(t) ? t : "";
    }

    boolean has(String page) {
        return pages.contains(page);
    }

    /** The page's position, or -1 when it is not shown. */
    int indexOf(String page) {
        return pages.indexOf(page);
    }

    boolean multiPage() {
        return pages.size() > 1;
    }

    /** Something on screen shows the feed's data: the bar, the clock's weather line or the weather page. */
    boolean needsFeed() {
        return statusBar || has(PAGE_CLOCK) || has(PAGE_WEATHER);
    }

    boolean hasWeather() {
        return !Double.isNaN(weatherLat);
    }

    boolean hasGitlab() {
        return !gitlabUrl.isEmpty() && !gitlabToken.isEmpty() && !gitlabProjects.isEmpty();
    }

    boolean hasFeed() {
        return !feedUrl.isEmpty();
    }

    /** Anything for StandbyFeed to fetch. */
    boolean hasSources() {
        return hasFeed() || hasWeather() || hasGitlab();
    }

    /** A decimal degree within ±limit, or NaN. */
    private static double coordinate(String text, double limit) {
        if (text == null) return Double.NaN;
        try {
            double v = Double.parseDouble(text.trim());
            return Math.abs(v) <= limit ? v : Double.NaN;
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    /** The URL trimmed, or "" unless it starts with http:// or https://. */
    private static String httpUrl(String text) {
        if (text == null) return "";
        String t = text.trim();
        return t.startsWith("http://") || t.startsWith("https://") ? t : "";
    }
}
