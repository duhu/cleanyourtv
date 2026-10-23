package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The repo panorama the git page shows (docs/superpowers/specs/2026-10-23-git-page-design.md): what
 * GitLab's project list and commit lists turn into, and the sums over them. Pure Java apart from
 * org.json, so everything here runs in the JVM tests; nothing touches the network or a view, and no
 * parser throws.
 */
final class GitData {
    /** The window of days, today last. */
    static final int DAYS = 30;
    /** Projects measured per round (the most recently active), repositories shown, pages of commits per project. */
    static final int CANDIDATES = 12, SHOWN = 8, MAX_PAGES = 5, PAGE = 100;
    /** Commits in the last 7 days that make a repository hot. */
    static final int HOT_WEEK = 10;
    private static final int WEEK = 7;

    enum Tier { HOT, WARM, COLD }

    /** One entry of GET /projects?simple=true. */
    static final class Project {
        final long id;
        /** "group/sub/name". */
        final String path;

        Project(long id, String path) {
            this.id = id;
            this.path = path;
        }
    }

    /** One entry of GET /projects/:id/repository/commits. */
    static final class Commit {
        final String author, title;
        final long committedAtMillis;

        Commit(String author, String title, long committedAtMillis) {
            this.author = author;
            this.title = title;
            this.committedAtMillis = committedAtMillis;
        }
    }

    /** One repository card: its 30 days of commits, its last commit and its latest pipeline. */
    static final class Repo {
        final String path, name, namespace;
        /** DAYS values, today last; never null. */
        final int[] daily;
        /** Null when there is no commit in the window. */
        final String lastAuthor, lastTitle;
        /** 0 when there is no commit in the window. */
        final long lastAtMillis;
        final StandbyData.CiStatus ci;
        /** The pipeline's last update, 0 when unknown. */
        final long ciAtMillis;

        Repo(String path, int[] daily, String lastAuthor, String lastTitle, long lastAtMillis, StandbyData.CiStatus ci, long ciAtMillis) {
            this.path = path;
            this.name = StandbyData.projectName(path);
            int cut = path.lastIndexOf('/');
            this.namespace = cut < 0 ? "" : path.substring(0, cut);
            this.daily = fit(daily);
            this.lastAuthor = lastAuthor;
            this.lastTitle = lastTitle;
            this.lastAtMillis = lastAtMillis;
            this.ci = ci == null ? StandbyData.CiStatus.UNKNOWN : ci;
            this.ciAtMillis = ciAtMillis;
        }

        /** The last DAYS values of the series, padded with zeros at the front. */
        private static int[] fit(int[] series) {
            int[] out = new int[DAYS];
            if (series == null) return out;
            int n = Math.min(DAYS, series.length);
            System.arraycopy(series, series.length - n, out, DAYS - n, n);
            return out;
        }

        int total() {
            return last(DAYS);
        }

        /** The sum of the last n days. */
        int last(int days) {
            int sum = 0;
            for (int i = Math.max(0, DAYS - days); i < DAYS; i++) sum += daily[i];
            return sum;
        }

        Tier tier() {
            int week = last(WEEK);
            return week >= HOT_WEEK ? Tier.HOT : week > 0 ? Tier.WARM : Tier.COLD;
        }

        Repo withCi(StandbyData.CiInfo info) {
            return new Repo(path, daily, lastAuthor, lastTitle, lastAtMillis, info.status, info.updatedAtMillis);
        }
    }

    /** What the page shows: the most active repositories, sorted, and when the numbers were taken. */
    static final class Snapshot {
        /** By 30-day total descending, ties by path; at most SHOWN. */
        final List<Repo> repos;
        final long asOfMillis;

        Snapshot(List<Repo> all, long asOfMillis) {
            List<Repo> sorted = new ArrayList<>(all);
            Collections.sort(sorted, (a, b) -> a.total() != b.total() ? Integer.compare(b.total(), a.total()) : a.path.compareTo(b.path));
            if (sorted.size() > SHOWN) sorted = new ArrayList<>(sorted.subList(0, SHOWN));
            this.repos = Collections.unmodifiableList(sorted);
            this.asOfMillis = asOfMillis;
        }

        int[] dailyTotals() {
            int[] out = new int[DAYS];
            for (Repo r : repos) for (int i = 0; i < DAYS; i++) out[i] += r.daily[i];
            return out;
        }

        int total() {
            int sum = 0;
            for (Repo r : repos) sum += r.total();
            return sum;
        }

        int activeRepos() {
            int n = 0;
            for (Repo r : repos) if (r.tier() == Tier.HOT) n++;
            return n;
        }

        int thisWeek() {
            int sum = 0;
            for (Repo r : repos) sum += r.last(WEEK);
            return sum;
        }

        int lastWeek() {
            int sum = 0;
            for (Repo r : repos) sum += r.last(2 * WEEK) - r.last(WEEK);
            return sum;
        }

        /** The status bar's cells: the first three repositories with a pipeline, or the first three. */
        List<StandbyData.CiInfo> statusCells() {
            List<StandbyData.CiInfo> out = new ArrayList<>();
            for (Repo r : repos) {
                if (r.ci == StandbyData.CiStatus.UNKNOWN) continue;
                out.add(new StandbyData.CiInfo(r.name, r.ci, r.ciAtMillis));
                if (out.size() == 3) break;
            }
            if (out.isEmpty()) {
                for (Repo r : repos) {
                    out.add(new StandbyData.CiInfo(r.name, r.ci, r.ciAtMillis));
                    if (out.size() == 3) break;
                }
            }
            return out;
        }
    }

    private GitData() {
    }

    /**
     * GET /projects?membership=true&simple=true&...: a JSON array. Entries without a path, archived ones
     * and, when groups is not empty, those outside every group prefix are dropped. Null for anything
     * that is not a JSON array.
     */
    static List<Project> parseProjects(String json, List<String> groups) {
        if (json == null) return null;
        try {
            JSONArray a = new JSONArray(json);
            List<Project> out = new ArrayList<>();
            for (int i = 0; i < a.length(); i++) {
                JSONObject p = a.optJSONObject(i);
                if (p == null || p.optBoolean("archived", false)) continue;
                String path = p.optString("path_with_namespace", "").trim();
                if (path.isEmpty() || !(p.opt("id") instanceof Number)) continue;
                if (!inGroups(path, groups)) continue;
                out.add(new Project(p.getLong("id"), path));
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean inGroups(String path, List<String> groups) {
        if (groups == null || groups.isEmpty()) return true;
        for (String g : groups) if (path.equals(g) || path.startsWith(g + "/")) return true;
        return false;
    }

    /** GET /projects/:id/repository/commits: a JSON array; an entry without a readable date is skipped. Null when not an array. */
    static List<Commit> parseCommits(String json) {
        if (json == null) return null;
        try {
            JSONArray a = new JSONArray(json);
            List<Commit> out = new ArrayList<>();
            for (int i = 0; i < a.length(); i++) {
                JSONObject c = a.optJSONObject(i);
                if (c == null) continue;
                long at = StandbyData.parseIso8601(c.optString("committed_date", null));
                if (at == 0) continue;
                out.add(new Commit(c.optString("author_name", "").trim(), c.optString("title", "").trim(), at));
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    /** The project's card: each commit counted on its local day inside the window, the newest one kept. */
    static Repo repo(Project p, List<Commit> commits, long asOfMillis, ZoneId zone) {
        LocalDate today = day(asOfMillis, zone);
        int[] daily = new int[DAYS];
        Commit last = null;
        for (Commit c : commits) {
            long back = ChronoUnit.DAYS.between(day(c.committedAtMillis, zone), today);
            if (back < 0 || back >= DAYS) continue;
            daily[(int) (DAYS - 1 - back)]++;
            if (last == null || c.committedAtMillis > last.committedAtMillis) last = c;
        }
        return new Repo(p.path, daily, last == null ? null : last.author, last == null ? null : last.title,
                last == null ? 0 : last.committedAtMillis, StandbyData.CiStatus.UNKNOWN, 0);
    }

    /** Whether day dayIndex (0..DAYS-1, DAYS-1 the day of asOf) is a Saturday or Sunday. */
    static boolean isWeekend(long asOfMillis, int dayIndex, ZoneId zone) {
        DayOfWeek d = day(asOfMillis, zone).minusDays(DAYS - 1 - dayIndex).getDayOfWeek();
        return d == DayOfWeek.SATURDAY || d == DayOfWeek.SUNDAY;
    }

    /** The commits API's since parameter: DAYS days before now, ISO-8601 in UTC to the second. */
    static String since(long nowMillis) {
        return DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(nowMillis).minus(DAYS, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS));
    }

    private static LocalDate day(long millis, ZoneId zone) {
        return Instant.ofEpochMilli(millis).atZone(zone).toLocalDate();
    }
}
