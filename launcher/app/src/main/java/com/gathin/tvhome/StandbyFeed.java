package com.gathin.tvhome;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Fetches what the standby screen shows, on one background thread, at a fixed cadence: the feed JSON
 * every minute when feed_url is set, otherwise Open-Meteo every 30 minutes and each GitLab project's
 * latest pipeline every 5 minutes; for the git page, the token's most recently active projects and
 * their 30 days of commits every 10 minutes. A failed fetch keeps the previous value and waits for
 * the next turn; there are no retries in between. Results reach the listener on the main thread.
 */
final class StandbyFeed {
    interface Listener {
        void onSnapshot(StandbyData.StandbySnapshot snapshot);
    }

    static final long FEED_MS = 60_000L, GITLAB_MS = 5 * 60_000L, WEATHER_MS = 30 * 60_000L, GIT_MS = 10 * 60_000L;
    private static final String PROJECTS_QUERY = "/api/v4/projects?membership=true&simple=true&archived=false&order_by=last_activity_at&sort=desc&per_page=50";
    private static final int TIMEOUT_MS = 10_000, MAX_BYTES = 256 * 1024;

    private final PagesConfig cfg;
    private final Handler main;
    private final Listener listener;
    private HandlerThread thread;
    private Handler worker;
    private StandbyData.WeatherInfo weather;
    private StandbyData.Forecast forecast;
    private List<StandbyData.CiInfo> ci = new ArrayList<>();
    private GitData.Snapshot git;

    private final Runnable fetchFeed = new Runnable() {
        @Override
        public void run() {
            StandbyData.StandbySnapshot s = StandbyData.parseFeed(get(cfg.feedUrl, null));
            if (s != null) publish(s);
            else Log.w(StandbyFeature.TAG, "feed not updated: " + cfg.feedUrl);
            worker.postDelayed(this, FEED_MS);
        }
    };

    private final Runnable fetchWeather = new Runnable() {
        @Override
        public void run() {
            // One answer carries the current conditions for the standby screen and the hourly/daily
            // forecast for the home screen's weather band; timezone=auto puts the times in the location's zone.
            String url = String.format(Locale.ROOT,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code"
                            + "&hourly=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min,weather_code"
                            + "&timezone=auto&forecast_days=4",
                    cfg.weatherLat, cfg.weatherLon);
            String body = get(url, null);
            StandbyData.WeatherInfo w = StandbyData.parseOpenMeteo(body);
            if (w != null) {
                weather = w;
                StandbyData.Forecast f = StandbyData.parseOpenMeteoForecast(body);
                if (f != null) forecast = f;
                publish(new StandbyData.StandbySnapshot(weather, ci, forecast, git));
            } else {
                Log.w(StandbyFeature.TAG, "weather not updated");
            }
            worker.postDelayed(this, WEATHER_MS);
        }
    };

    private final Runnable fetchGitlab = new Runnable() {
        @Override
        public void run() {
            List<StandbyData.CiInfo> fresh = new ArrayList<>();
            boolean anyFailed = false;
            for (String project : cfg.gitlabProjects) {
                String url;
                try {
                    url = cfg.gitlabUrl + "/api/v4/projects/" + URLEncoder.encode(project, "UTF-8") + "/pipelines?per_page=1";
                } catch (Exception e) {
                    continue;
                }
                String name = StandbyData.projectName(project);
                StandbyData.CiInfo info = StandbyData.parseGitlabPipelines(name, get(url, cfg.gitlabToken));
                if (info != null) {
                    fresh.add(info);
                } else {
                    anyFailed = true;
                    StandbyData.CiInfo old = previous(name);
                    if (old != null) fresh.add(old);
                }
            }
            if (anyFailed) Log.w(StandbyFeature.TAG, "gitlab: some projects not updated");
            ci = fresh;
            publish(new StandbyData.StandbySnapshot(weather, ci, forecast, git));
            worker.postDelayed(this, GITLAB_MS);
        }
    };

    /**
     * The git page: the token's most recently active projects, the 30 days of commits of the first
     * CANDIDATES of them (all branches, paged), then the latest pipeline of the SHOWN most active. A
     * failed project list keeps the previous snapshot; a project whose first page fails is left out of
     * this round; a failed pipeline call leaves that card without CI. With no gitlab_projects, the
     * status bar's cells come from the same snapshot.
     */
    private final Runnable fetchGit = new Runnable() {
        @Override
        public void run() {
            long now = System.currentTimeMillis();
            String base = cfg.gitlabUrl + "/api/v4";
            List<GitData.Project> projects = GitData.parseProjects(get(cfg.gitlabUrl + PROJECTS_QUERY, cfg.gitlabToken), cfg.gitlabGroups);
            if (projects == null) {
                Log.w(StandbyFeature.TAG, "git: projects not updated");
            } else {
                String since = GitData.since(now);
                ZoneId zone = ZoneId.systemDefault();
                List<GitData.Repo> repos = new ArrayList<>();
                for (GitData.Project p : projects) {
                    if (repos.size() >= GitData.CANDIDATES) break;
                    List<GitData.Commit> all = new ArrayList<>();
                    boolean counted = true;
                    for (int page = 1; page <= GitData.MAX_PAGES; page++) {
                        String url = base + "/projects/" + p.id + "/repository/commits?all=true&since=" + since
                                + "&per_page=" + GitData.PAGE + "&page=" + page;
                        List<GitData.Commit> c = GitData.parseCommits(get(url, cfg.gitlabToken));
                        if (c == null) {
                            counted = page > 1;   // a later page lost: keep what came before
                            break;
                        }
                        all.addAll(c);
                        if (c.size() < GitData.PAGE) break;
                    }
                    if (!counted) {
                        Log.w(StandbyFeature.TAG, "git: " + p.path + " not counted");
                        continue;
                    }
                    repos.add(GitData.repo(p, all, now, zone));
                }
                List<GitData.Repo> shown = new ArrayList<>();
                for (GitData.Repo r : new GitData.Snapshot(repos, now).repos) {
                    StandbyData.CiInfo info = null;
                    try {
                        info = StandbyData.parseGitlabPipelines(r.name,
                                get(base + "/projects/" + URLEncoder.encode(r.path, "UTF-8") + "/pipelines?per_page=1", cfg.gitlabToken));
                    } catch (Exception ignored) {
                    }
                    shown.add(info == null ? r : r.withCi(info));
                }
                git = new GitData.Snapshot(shown, now);
                if (!cfg.hasProjects()) ci = git.statusCells();
                publish(new StandbyData.StandbySnapshot(weather, ci, forecast, git));
            }
            worker.postDelayed(this, GIT_MS);
        }
    };

    StandbyFeed(PagesConfig cfg, Handler main, Listener listener) {
        this.cfg = cfg;
        this.main = main;
        this.listener = listener;
    }

    /** Starts polling; a second call is a no-op. Nothing happens when the config has no sources. */
    void start() {
        if (worker != null || !cfg.hasSources()) return;
        thread = new HandlerThread("standby-feed");
        thread.start();
        worker = new Handler(thread.getLooper());
        if (cfg.hasFeed()) {
            worker.post(fetchFeed);
        } else {
            if (cfg.hasWeather()) worker.post(fetchWeather);
            if (cfg.hasGitlab() && cfg.hasProjects()) worker.post(fetchGitlab);
        }
        if (cfg.hasGitlab() && cfg.has(PagesConfig.PAGE_GIT)) worker.post(fetchGit);
    }

    void stop() {
        if (worker == null) return;
        worker.removeCallbacksAndMessages(null);
        thread.quitSafely();
        thread = null;
        worker = null;
    }

    private StandbyData.CiInfo previous(String name) {
        for (StandbyData.CiInfo c : ci) if (c.name.equals(name)) return c;
        return null;
    }

    private void publish(StandbyData.StandbySnapshot s) {
        main.post(() -> listener.onSnapshot(s));
    }

    /** The body of a 2xx GET as UTF-8, at most MAX_BYTES, or null for anything else. Never throws. */
    static String get(String url, String privateToken) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(TIMEOUT_MS);
            c.setReadTimeout(TIMEOUT_MS);
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("User-Agent", "cleanyourtv-launcher");
            if (privateToken != null && !privateToken.isEmpty()) c.setRequestProperty("PRIVATE-TOKEN", privateToken);
            int code = c.getResponseCode();
            if (code < 200 || code > 299) {
                Log.w(StandbyFeature.TAG, "HTTP " + code + " from " + url);
                return null;
            }
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    if (out.size() > MAX_BYTES) return null;
                }
                return out.toString(StandardCharsets.UTF_8.name());
            }
        } catch (Exception e) {
            Log.w(StandbyFeature.TAG, "fetch failed: " + url + ": " + e);
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }
}
