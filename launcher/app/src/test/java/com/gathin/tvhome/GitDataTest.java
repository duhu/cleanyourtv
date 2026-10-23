package com.gathin.tvhome;

import org.junit.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The repo panorama: what GitLab's answers turn into, and the sums the git page shows. */
public class GitDataTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    /** Friday 2026-10-23 09:00 in Shanghai. */
    private static final long FRI = ZonedDateTime.of(2026, 10, 23, 9, 0, 0, 0, ZONE).toInstant().toEpochMilli();
    private static final long HOUR = 3600_000L, DAY = 24 * HOUR;
    private static final List<String> NO_GROUPS = Collections.emptyList();

    private static final String PROJECTS = "[{\"id\":48799,\"path_with_namespace\":\"we-project/wanda-ctc-dev-v2\",\"last_activity_at\":\"2026-10-23T01:12:14.000Z\"},"
            + "{\"id\":26217,\"path_with_namespace\":\"travel-intelligent-tech/cdm\"},"
            + "{\"id\":1,\"path_with_namespace\":\"old/thing\",\"archived\":true},"
            + "{\"id\":2,\"name\":\"no path\"},"
            + "{\"id\":3,\"path_with_namespace\":\"we-project-x/other\"},"
            + "{\"id\":4,\"path_with_namespace\":\"we-project/sub/deep\"}]";

    @Test
    public void projectsKeepIdAndPathAndDropArchivedOrPathless() {
        List<GitData.Project> p = GitData.parseProjects(PROJECTS, NO_GROUPS);
        assertEquals(4, p.size());
        assertEquals(48799, p.get(0).id);
        assertEquals("we-project/wanda-ctc-dev-v2", p.get(0).path);
        assertEquals("we-project/sub/deep", p.get(3).path);
    }

    @Test
    public void projectsFilteredByGroups() {
        List<GitData.Project> p = GitData.parseProjects(PROJECTS, Arrays.asList("we-project"));
        assertEquals(2, p.size());                                   // not we-project-x
        assertEquals("we-project/wanda-ctc-dev-v2", p.get(0).path);
        assertEquals("we-project/sub/deep", p.get(1).path);
        assertEquals(1, GitData.parseProjects(PROJECTS, Arrays.asList("we-project/sub", "nothing")).size());
        assertTrue(GitData.parseProjects("[]", NO_GROUPS).isEmpty());
    }

    private static final String COMMITS = "[{\"id\":\"3ef7\",\"title\":\"feat: directory\",\"author_name\":\"庞绍进\",\"committed_date\":\"2026-10-23T08:48:00.000+08:00\"},"
            + "{\"id\":\"aaaa\",\"title\":\"fix\",\"author_name\":\"A\",\"committed_date\":\"2026-10-22T23:30:00.000+08:00\"},"
            + "{\"id\":\"bbbb\",\"title\":\"undated\",\"author_name\":\"B\"},"
            + "{\"id\":\"cccc\",\"title\":\"old\",\"author_name\":\"C\",\"committed_date\":\"2026-09-24T10:00:00.000+08:00\"},"
            + "{\"id\":\"dddd\",\"title\":\"too old\",\"author_name\":\"D\",\"committed_date\":\"2026-09-23T10:00:00.000+08:00\"}]";

    @Test
    public void commitsReadAuthorTitleAndDate() {
        List<GitData.Commit> c = GitData.parseCommits(COMMITS);
        assertEquals(4, c.size());                                   // the undated one is skipped
        assertEquals("庞绍进", c.get(0).author);
        assertEquals("feat: directory", c.get(0).title);
        assertEquals(FRI - 12 * 60_000L, c.get(0).committedAtMillis);
        assertNull(GitData.parseCommits("{\"message\":\"401 Unauthorized\"}"));
        assertNull(GitData.parseCommits(null));
        assertNull(GitData.parseProjects("<html>", NO_GROUPS));
    }

    @Test
    public void repoFillsDaySlotsAndKeepsTheLatestCommit() {
        GitData.Project p = new GitData.Project(48799, "we-project/wanda-ctc-dev-v2");
        GitData.Repo r = GitData.repo(p, GitData.parseCommits(COMMITS), FRI, ZONE);
        assertEquals("wanda-ctc-dev-v2", r.name);
        assertEquals("we-project", r.namespace);
        assertEquals(GitData.DAYS, r.daily.length);
        assertEquals(1, r.daily[GitData.DAYS - 1]);                 // today
        assertEquals(1, r.daily[GitData.DAYS - 2]);                 // yesterday 23:30 local
        assertEquals(1, r.daily[0]);                                 // 29 days ago
        assertEquals(3, r.total());                                  // the 30-day-old commit is outside
        assertEquals("庞绍进", r.lastAuthor);
        assertEquals("feat: directory", r.lastTitle);
        assertEquals(FRI - 12 * 60_000L, r.lastAtMillis);
        assertEquals(StandbyData.CiStatus.UNKNOWN, r.ci);
        GitData.Repo none = GitData.repo(new GitData.Project(1, "solo"), Collections.emptyList(), FRI, ZONE);
        assertEquals("solo", none.name);
        assertEquals("", none.namespace);
        assertEquals(0, none.total());
        assertEquals(0, none.lastAtMillis);
        assertNull(none.lastAuthor);
    }

    private static GitData.Repo repo(String path, int... lastDays) {
        int[] daily = new int[GitData.DAYS];
        for (int i = 0; i < lastDays.length; i++) daily[GitData.DAYS - lastDays.length + i] = lastDays[i];
        return new GitData.Repo(path, daily, "x", "t", FRI - HOUR, StandbyData.CiStatus.UNKNOWN, 0);
    }

    @Test
    public void tiersComeFromThisWeek() {
        assertEquals(GitData.Tier.HOT, repo("a", 2, 2, 2, 2, 1, 1, 0).tier());        // 10 in 7 days
        assertEquals(GitData.Tier.WARM, repo("a", 9, 2, 2, 2, 1, 1, 0, 0).tier());     // 9 in 7 days, 9 more before
        assertEquals(GitData.Tier.COLD, repo("a", 6, 0, 0, 0, 0, 0, 0, 0).tier());     // only last month
        assertEquals(7, repo("a", 1, 1, 1, 1, 1, 1, 1, 1).last(7));
        assertEquals(8, repo("a", 1, 1, 1, 1, 1, 1, 1, 1).total());
    }

    @Test
    public void shortOrLongSeriesAreFittedToTheWindow() {
        GitData.Repo s = new GitData.Repo("p", new int[] {1, 2}, null, null, 0, StandbyData.CiStatus.UNKNOWN, 0);
        assertEquals(GitData.DAYS, s.daily.length);
        assertEquals(2, s.daily[GitData.DAYS - 1]);
        assertEquals(1, s.daily[GitData.DAYS - 2]);
        assertEquals(0, s.daily[0]);
        int[] big = new int[GitData.DAYS + 5];
        Arrays.fill(big, 1);
        big[0] = 9;
        assertEquals(GitData.DAYS, new GitData.Repo("p", big, null, null, 0, StandbyData.CiStatus.UNKNOWN, 0).total());
        assertEquals(GitData.DAYS, new GitData.Repo("p", null, null, null, 0, StandbyData.CiStatus.UNKNOWN, 0).daily.length);
    }

    @Test
    public void snapshotSortsCapsAndSums() {
        List<GitData.Repo> all = Arrays.asList(repo("g/b", 1), repo("g/a", 1), repo("g/c", 5), repo("g/d", 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2),
                repo("g/e"), repo("g/f", 3), repo("g/g", 4), repo("g/h", 6), repo("g/i", 7), repo("g/j", 8));
        GitData.Snapshot s = new GitData.Snapshot(all, FRI);
        assertEquals(GitData.SHOWN, s.repos.size());
        assertEquals("g/d", s.repos.get(0).path);                    // 28
        assertEquals("g/j", s.repos.get(1).path);
        assertEquals("g/a", s.repos.get(7).path);                    // ties by path: a before b, e (0) is out
        assertEquals(28 + 8 + 7 + 6 + 5 + 4 + 3 + 1, s.total());
        int[] totals = s.dailyTotals();
        assertEquals(GitData.DAYS, totals.length);
        assertEquals(2 + 8 + 7 + 6 + 5 + 4 + 3 + 1, totals[GitData.DAYS - 1]);
        assertEquals(14 + 8 + 7 + 6 + 5 + 4 + 3 + 1, s.thisWeek());
        assertEquals(14, s.lastWeek());
        assertEquals(1, s.activeRepos());                            // only g/d has 10 this week
        assertTrue(new GitData.Snapshot(Collections.emptyList(), FRI).repos.isEmpty());
        assertEquals(0, new GitData.Snapshot(Collections.emptyList(), FRI).total());
    }

    @Test
    public void statusCellsPreferReposWithAPipeline() {
        GitData.Repo a = repo("g/a", 9), b = repo("g/b", 8).withCi(new StandbyData.CiInfo("b", StandbyData.CiStatus.SUCCESS, FRI - HOUR));
        GitData.Repo c = repo("g/c", 7), d = repo("g/d", 6).withCi(new StandbyData.CiInfo("d", StandbyData.CiStatus.FAILED, FRI - DAY));
        GitData.Repo e = repo("g/e", 5).withCi(new StandbyData.CiInfo("e", StandbyData.CiStatus.RUNNING, 0));
        GitData.Repo f = repo("g/f", 4).withCi(new StandbyData.CiInfo("f", StandbyData.CiStatus.SUCCESS, 0));
        List<StandbyData.CiInfo> cells = new GitData.Snapshot(Arrays.asList(a, b, c, d, e, f), FRI).statusCells();
        assertEquals(3, cells.size());
        assertEquals("b", cells.get(0).name);
        assertEquals(StandbyData.CiStatus.SUCCESS, cells.get(0).status);
        assertEquals(FRI - HOUR, cells.get(0).updatedAtMillis);
        assertEquals("d", cells.get(1).name);
        assertEquals("e", cells.get(2).name);
        List<StandbyData.CiInfo> none = new GitData.Snapshot(Arrays.asList(a, c), FRI).statusCells();
        assertEquals(2, none.size());                                // nothing has a pipeline: the most active anyway
        assertEquals("a", none.get(0).name);
        assertEquals(StandbyData.CiStatus.UNKNOWN, none.get(0).status);
    }

    @Test
    public void weekendsFollowTheSnapshotDay() {
        assertFalse(GitData.isWeekend(FRI, GitData.DAYS - 1, ZONE));    // Friday
        assertFalse(GitData.isWeekend(FRI, GitData.DAYS - 2, ZONE));    // Thursday
        assertTrue(GitData.isWeekend(FRI, GitData.DAYS - 6, ZONE));     // Sunday 10-18
        assertTrue(GitData.isWeekend(FRI, GitData.DAYS - 7, ZONE));     // Saturday 10-17
        assertFalse(GitData.isWeekend(FRI, GitData.DAYS - 8, ZONE));    // Friday 10-16
        assertFalse(GitData.isWeekend(FRI, 0, ZONE));                   // index 0 is 29 days back: Thursday 09-24
        assertTrue(GitData.isWeekend(FRI, 2, ZONE));                    // Saturday 09-26
    }

    @Test
    public void sinceIsThirtyDaysBackInUtc() {
        assertEquals("2026-09-23T01:00:00Z", GitData.since(FRI));
    }
}
