package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The meeting-room standby settings (config.xml, overlaid by a theme's config.json; see Config):
 * whether the standby screen and HDMI auto-switch are on, and where the weather and CI status come
 * from. Built from raw values so the checks run in plain JVM tests; a value that does not make sense
 * simply switches its source off, never the home screen.
 */
final class StandbyConfig {
    static final int MIN_IDLE = 5, MAX_IDLE = 3600, DEFAULT_IDLE = 30;
    /** The token the shipped theme carries until the owner replaces it. */
    static final String TOKEN_PLACEHOLDER = "REPLACE_ME";

    final boolean enabled;
    final int idleSeconds;
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
    /** Split-flap clock cards instead of plain digits. */
    final boolean flipClock;

    private StandbyConfig(boolean enabled, int idleSeconds, double lat, double lon, String gitlabUrl, String gitlabToken,
                          List<String> gitlabProjects, String feedUrl, boolean hdmiAutoSwitch, boolean flipClock) {
        this.enabled = enabled;
        this.idleSeconds = idleSeconds;
        this.weatherLat = lat;
        this.weatherLon = lon;
        this.gitlabUrl = gitlabUrl;
        this.gitlabToken = gitlabToken;
        this.gitlabProjects = Collections.unmodifiableList(gitlabProjects);
        this.feedUrl = feedUrl;
        this.hdmiAutoSwitch = hdmiAutoSwitch;
        this.flipClock = flipClock;
    }

    static StandbyConfig from(boolean enabled, int idleSeconds, String lat, String lon, String gitlabUrl, String gitlabToken,
                              List<String> gitlabProjects, String feedUrl, boolean hdmiAutoSwitch, boolean flipClock) {
        int idle = Math.max(MIN_IDLE, Math.min(MAX_IDLE, idleSeconds));
        double la = coordinate(lat, 90), lo = coordinate(lon, 180);
        if (Double.isNaN(la) || Double.isNaN(lo)) la = lo = Double.NaN;
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
        return new StandbyConfig(enabled, idle, la, lo, url, token, projects, httpUrl(feedUrl), hdmiAutoSwitch, flipClock);
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
