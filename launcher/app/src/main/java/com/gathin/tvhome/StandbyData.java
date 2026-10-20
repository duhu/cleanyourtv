package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What the standby screen shows, and how the fetched JSON turns into it. Pure Java apart from
 * org.json, so every parser runs in the JVM tests; nothing here touches the network or a view.
 */
final class StandbyData {

    enum WeatherKind { CLEAR, CLOUDY, OVERCAST, FOG, RAIN, SNOW, THUNDER, UNKNOWN }

    enum CiStatus { SUCCESS, FAILED, RUNNING, UNKNOWN }

    static final class WeatherInfo {
        final double tempC;
        final WeatherKind kind;
        /** A label supplied by the feed, shown instead of the kind; null from Open-Meteo. */
        final String text;

        WeatherInfo(double tempC, WeatherKind kind, String text) {
            this.tempC = tempC;
            this.kind = kind;
            this.text = text;
        }
    }

    static final class CiInfo {
        final String name;
        final CiStatus status;
        /** Epoch millis of the pipeline's last update, 0 when unknown. */
        final long updatedAtMillis;

        CiInfo(String name, CiStatus status, long updatedAtMillis) {
            this.name = name;
            this.status = status;
            this.updatedAtMillis = updatedAtMillis;
        }
    }

    /** Everything the view renders; a null weather or empty ci list means "nothing known". */
    static final class StandbySnapshot {
        static final StandbySnapshot EMPTY = new StandbySnapshot(null, Collections.emptyList());
        final WeatherInfo weather;
        final List<CiInfo> ci;

        StandbySnapshot(WeatherInfo weather, List<CiInfo> ci) {
            this.weather = weather;
            this.ci = Collections.unmodifiableList(new ArrayList<>(ci));
        }
    }

    /** How long ago something happened, in the unit the status bar shows. */
    static final class Age {
        static final int JUST_NOW = 0, MINUTES = 1, HOURS = 2, DAYS = 3;
        final int unit;
        final int count;

        Age(int unit, int count) {
            this.unit = unit;
            this.count = count;
        }
    }

    private StandbyData() {
    }

    /** Open-Meteo: {"current":{"temperature_2m":21.4,"weather_code":3}}. */
    static WeatherInfo parseOpenMeteo(String json) {
        if (json == null) return null;
        try {
            JSONObject current = new JSONObject(json).optJSONObject("current");
            if (current == null || !(current.opt("temperature_2m") instanceof Number)) return null;
            double temp = current.getDouble("temperature_2m");
            int code = current.opt("weather_code") instanceof Number ? current.getInt("weather_code") : -1;
            return new WeatherInfo(temp, weatherKind(code), null);
        } catch (Exception e) {
            return null;
        }
    }

    /** WMO weather interpretation codes, folded into the few words the date line shows. */
    static WeatherKind weatherKind(int code) {
        if (code == 0 || code == 1) return WeatherKind.CLEAR;
        if (code == 2) return WeatherKind.CLOUDY;
        if (code == 3) return WeatherKind.OVERCAST;
        if (code == 45 || code == 48) return WeatherKind.FOG;
        if (code >= 51 && code <= 67) return WeatherKind.RAIN;
        if (code >= 71 && code <= 77) return WeatherKind.SNOW;
        if (code >= 80 && code <= 82) return WeatherKind.RAIN;
        if (code == 85 || code == 86) return WeatherKind.SNOW;
        if (code >= 95 && code <= 99) return WeatherKind.THUNDER;
        return WeatherKind.UNKNOWN;
    }

    /** GitLab GET /projects/:id/pipelines?per_page=1: a JSON array; the first entry is the latest. */
    static CiInfo parseGitlabPipelines(String name, String json) {
        if (json == null) return null;
        try {
            JSONArray a = new JSONArray(json);
            if (a.length() == 0) return new CiInfo(name, CiStatus.UNKNOWN, 0);
            JSONObject p = a.getJSONObject(0);
            return new CiInfo(name, ciStatus(p.optString("status", "")), parseIso8601(p.optString("updated_at", null)));
        } catch (Exception e) {
            return null;
        }
    }

    static CiStatus ciStatus(String word) {
        switch (word) {
            case "success": return CiStatus.SUCCESS;
            case "failed": return CiStatus.FAILED;
            case "running": case "pending": case "created": case "waiting_for_resource":
            case "preparing": case "scheduled": return CiStatus.RUNNING;
            default: return CiStatus.UNKNOWN;
        }
    }

    /**
     * The aggregated feed (feed_url): {"weather":{"temp_c":21,"text":"多云"},
     * "ci":[{"name":"x","status":"success","updated_at":"..."}], "next_event":{...}}.
     * next_event is accepted but not shown in this version.
     */
    static StandbySnapshot parseFeed(String json) {
        if (json == null) return null;
        try {
            JSONObject o = new JSONObject(json);
            WeatherInfo weather = null;
            JSONObject w = o.optJSONObject("weather");
            if (w != null && w.opt("temp_c") instanceof Number) {
                String text = w.opt("text") instanceof String ? w.getString("text").trim() : "";
                weather = new WeatherInfo(w.getDouble("temp_c"), WeatherKind.UNKNOWN, text.isEmpty() ? null : text);
            }
            List<CiInfo> ci = new ArrayList<>();
            JSONArray a = o.optJSONArray("ci");
            for (int i = 0; a != null && i < a.length(); i++) {
                JSONObject c = a.optJSONObject(i);
                if (c == null) continue;
                String name = c.opt("name") instanceof String ? c.getString("name").trim() : "";
                if (name.isEmpty()) continue;
                ci.add(new CiInfo(name, ciStatus(c.optString("status", "")), parseIso8601(c.optString("updated_at", null))));
            }
            return new StandbySnapshot(weather, ci);
        } catch (Exception e) {
            return null;
        }
    }

    /** "2026-10-20T06:12:00Z" or with an offset, to epoch millis; 0 when unreadable. */
    static long parseIso8601(String text) {
        if (text == null) return 0;
        try {
            return OffsetDateTime.parse(text.trim()).toInstant().toEpochMilli();
        } catch (Exception e) {
            return 0;
        }
    }

    static Age age(long thenMillis, long nowMillis) {
        long s = Math.max(0, (nowMillis - thenMillis) / 1000);
        if (s < 60) return new Age(Age.JUST_NOW, 0);
        if (s < 3600) return new Age(Age.MINUTES, (int) (s / 60));
        if (s < 86400) return new Age(Age.HOURS, (int) (s / 3600));
        return new Age(Age.DAYS, (int) (s / 86400));
    }

    /** "group/sub/project" → "project". */
    static String projectName(String path) {
        String p = path;
        while (p.endsWith("/")) p = p.substring(0, p.length() - 1);
        return p.substring(p.lastIndexOf('/') + 1);
    }
}
