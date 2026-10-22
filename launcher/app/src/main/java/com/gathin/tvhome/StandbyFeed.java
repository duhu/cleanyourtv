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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Fetches what the standby screen shows, on one background thread, at a fixed cadence: the feed JSON
 * every minute when feed_url is set, otherwise Open-Meteo every 30 minutes and each GitLab project's
 * latest pipeline every 5 minutes. A failed fetch keeps the previous value and waits for the next
 * turn; there are no retries in between. Results reach the listener on the main thread.
 */
final class StandbyFeed {
    interface Listener {
        void onSnapshot(StandbyData.StandbySnapshot snapshot);
    }

    static final long FEED_MS = 60_000L, GITLAB_MS = 5 * 60_000L, WEATHER_MS = 30 * 60_000L;
    private static final int TIMEOUT_MS = 10_000, MAX_BYTES = 256 * 1024;

    private final StandbyConfig cfg;
    private final Handler main;
    private final Listener listener;
    private HandlerThread thread;
    private Handler worker;
    private StandbyData.WeatherInfo weather;
    private StandbyData.Forecast forecast;
    private List<StandbyData.CiInfo> ci = new ArrayList<>();

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
                publish(new StandbyData.StandbySnapshot(weather, ci, forecast));
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
            publish(new StandbyData.StandbySnapshot(weather, ci, forecast));
            worker.postDelayed(this, GITLAB_MS);
        }
    };

    StandbyFeed(StandbyConfig cfg, Handler main, Listener listener) {
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
            if (cfg.hasGitlab()) worker.post(fetchGitlab);
        }
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
