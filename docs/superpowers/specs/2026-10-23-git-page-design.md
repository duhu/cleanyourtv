# The git page: a repo panorama on Home

Design for the office TV, 2026-10-23. Mockup: `designs/git-board/A 仓库卡片墙.html` (approved; B, a
trend with a ranked list, was not chosen). Fourth page of the Home pager of
`docs/superpowers/specs/2026-10-22-home-pager-design.md`, fed from the company GitLab.

## Goal

One more page to the right of the weather: how busy the repositories have been over the last 30
days, and who touched each one last. A big total, a daily bar chart for all shown repositories, then
a wall of up to eight repository cards sorted by activity. Someone walking past the meeting room
reads "wanda-ctc-dev-v2, 58 commits, 庞绍进 12 分钟前" without stopping.

The data comes straight from GitLab with the read-only token the status bar already uses: the
projects the token is a member of, ordered by last activity, and for the most recent of them the
commits of the last 30 days on every branch. No WeVantage, no collector, nothing written anywhere.

## What was checked on git.17usoft.com (GitLab 10.8.7)

- The TV reaches it on the office Wi-Fi (3 ms); the certificate is a public GlobalSign one, so
  Android 9 trusts it without any install.
- `GET /api/v4/projects?membership=true&simple=true&archived=false&order_by=last_activity_at&sort=desc&per_page=50`
  works (457 projects for the owner's token, 12 KB per 20 projects, 0.2 s).
- `GET /api/v4/projects/<id>/repository/commits?all=true&since=<ISO>&per_page=100` works and counts
  every branch: a project that showed 0 commits on its default branch has 71 with `all=true`. The
  busiest project of the month needed three pages (222 commits). `x-next-page` headers exist, but
  "a full page means there may be another" is enough.
- Push events (`/events?action=pushed`) are useless for counting: a branch creation carries the
  whole history in `commit_count`.
- `GET /projects/<id>/pipelines?per_page=1` is what the status bar already calls; many projects have
  no pipeline at all and answer `[]`.

## Decisions

| Question | Decision |
|---|---|
| Page name | `git`, accepted by `PagesConfig` like `clock` and `weather`, and dropped like `weather` without coordinates when `gitlab_url` or `gitlab_token` is missing. The meeting-room theme uses `["apps", "clock", "weather", "git"]` |
| Which repositories | Discovery, not a list: the first page (50) of the token's member projects by last activity, optionally kept to the `gitlab_groups` prefixes (a new theme key, a list of namespace paths such as `we-project`; empty means every group). The first `GitData.CANDIDATES` (12) of those are measured; the page shows the `GitData.SHOWN` (8) with the most commits in 30 days |
| Counting | Per project, `repository/commits?all=true&since=<now − 30 days>` paged by 100 up to `GitData.MAX_PAGES` (5); each commit goes into the local day of its `committed_date`; the newest commit is the last commit (`author_name`, `title`, `committed_date`). More than 500 commits in a month is shown as 500 (the oldest days lose their share) |
| Pipelines | The latest pipeline of each shown repository (eight calls), the same parser as the status bar |
| Status bar | When `gitlab_projects` is empty, the CI cells are the three most active shown repositories that have a pipeline (status not UNKNOWN); when none has one, the three most active anyway. A non-empty `gitlab_projects` keeps today's behaviour and the git page ignores it. The meeting-room theme drops its placeholder `gitlab_projects` |
| Cadence | `StandbyFeed.GIT_MS` = 10 minutes: at most 1 + 12 × 5 + 8 = 69 small GETs on the intranet, sequential on the feed's worker thread. A failed discovery keeps the previous snapshot; a failed commits call drops that project from this round; a failed pipeline call gives UNKNOWN. The first fetch runs at Home start like the others |
| Header | Top-left: the 30-day total of the shown repositories, 75sp extralight, then "次提交 · 近 30 天". Under it: "N 个活跃仓库 · 本周 X · 上周 Y". Top-right: the daily chart, 350×65dp, caption "每日提交 · 全部仓库" on the left and "更新于 12 分钟前" (the snapshot's age) on the right, five date labels under it (the first day, then every 7 days, "今天" last) |
| Cards | At most eight, four per row, sorted by 30-day commits descending, ties by path. A card: tier dot and name (16sp medium, one line, ellipsised); under it the namespace (10sp, 40 % white) on the left and the 30-day count (17sp light, "提交" 10sp beside it) on the right, so a long name keeps the whole first line; a 26dp sparkline of the same 30 days; a footer line: author · age, the title ellipsised, the pipeline word ("CI ✓" green, "CI ✗" red, "CI …" amber, nothing when unknown). Fewer than five repositories make one row |
| Tiers | From the last 7 days only. hot: 10 commits or more (green dot); warm: 1 to 9 (amber); cold: none this week (dim dot and the whole card at 55 % alpha) |
| Charts | One `BarChartView` draws the big chart and the sparklines: bars with a gap, heights relative to the largest value, at least 2dp; weekend days at 20 % white, weekdays 42 %, today white. Which days are weekends comes from the snapshot's instant |
| Ages | "12 分钟前", "2 小时前", "3 天前" through `StandbyData.age` and `StatusCells.ageText`; refreshed when the page comes on screen and on the minute tick, like the weather page |
| Waiting | Until the first snapshot the page shows "等待仓库数据…" centred; a snapshot with no repositories shows "没有找到仓库" |
| Keys and return | As on the clock and weather pages: Left/Right flip, OK goes to the apps page, Back to the default page; the return timer is unchanged |
| Secrets | `tools/theme.sh` and `theme.ps1` merge `<theme>/config.local.json` over `config.json` before pushing (top-level keys, the local file wins); `.gitignore` keeps `themes/*/config.local.json` out. The owner's token lives there; the committed theme keeps `REPLACE_ME` |
| Failure | Every call into the page and the feed runs under `StandbyFeature.run`; a parser never throws |

## Layout

Mockup pixels are 2 per dp (1080p panel). Left and right margins 48dp; the status bar keeps its
place at the bottom (`activity_main.xml`); the page is the area above it.

- Total: left 56dp, top 42dp, 75sp `outfit_extralight`, letter spacing −0.04. "次提交 · 近 30 天" 17sp at
  70 % white on the same baseline, 13dp to the right. The sub line 14sp at 40 % white with the numbers at
  70 %, 10dp under the total.
- Daily chart: right 56dp, top 48dp, width 350dp, bars 65dp high; caption 12sp at 55 % white above
  (7dp gap); labels 11sp at 40 % white below (6dp gap).
- Cards: top 165dp, four columns, 12dp gaps, each 124dp high, `glass_panel` background, padding 15dp
  horizontal, 12dp top, 11dp bottom. Row 2 under row 1 with the same gap; nothing scrolls.
- Inside a card: header row (dot 7dp, name); the namespace and count line 1dp under; sparkline 6dp under it,
  26dp high; the footer pinned to the bottom, 11.5sp at 70 % white for the author, 55 % for the title.

## Components

### `GitData` (pure Java, `GitDataTest`)

```java
final class GitData {
    static final int DAYS = 30, CANDIDATES = 12, SHOWN = 8, MAX_PAGES = 5, PAGE = 100;
    enum Tier { HOT, WARM, COLD }

    static final class Project { final long id; final String path; /* "group/sub/name" */ }
    static final class Commit { final String author, title; final long committedAtMillis; }

    static final class Repo {
        final String path, name, namespace;   // name = last segment, namespace = the rest
        final int[] daily;                    // DAYS values, today last
        final String lastAuthor, lastTitle;
        final long lastAtMillis;              // 0 when there is no commit
        final StandbyData.CiStatus ci;
        int total(); int last(int days); Tier tier();   // last(7) >= 10 HOT, >= 1 WARM, else COLD
        Repo withCi(StandbyData.CiStatus ci);
    }

    static final class Snapshot {
        final List<Repo> repos;               // sorted by total() descending, then path; at most SHOWN
        final long asOfMillis;
        int[] dailyTotals(); int total(); int activeRepos(); int thisWeek(); int lastWeek();
        List<StandbyData.CiInfo> statusCells(); // three with a pipeline, else the first three
    }

    static List<Project> parseProjects(String json, List<String> groups);   // simple=true answer; null on bad JSON
    static List<Commit> parseCommits(String json);                           // null on bad JSON; a commit without a date is skipped
    static Repo repo(Project p, List<Commit> commits, long asOfMillis, ZoneId zone);
    static boolean isWeekend(long asOfMillis, int dayIndex, ZoneId zone);
    static String since(long nowMillis);      // ISO-8601 UTC, now − DAYS days
}
```

`repo` puts each commit into `daily[DAYS - 1 - daysBetween(commitDay, today)]`, skipping commits
outside the window (GitLab's `since` is inclusive but the clock may have moved). A `groups` prefix
matches `path.equals(g) || path.startsWith(g + "/")`.

### `BarChartView`

A `View` with `setValues(int[] values, boolean[] weekend)` and `setBarColors(int weekday, int weekend,
int today)`. `onDraw` divides the width into `values.length` slots with a gap of a quarter slot (at least
1dp), rounds the top corners by half the bar width, and draws each bar from the bottom with height
`max(2dp, value / max * height)`; an all-zero series draws every bar at 2dp. No animation, no text.

### `GitPageView` (`res/layout/git_page.xml`)

A `FrameLayout` subclass like the weather page: the waiting line, the header views, the chart and
two horizontal `LinearLayout` rows for the cards (built in code, `glass_panel` background).
`render(GitData.Snapshot)` keeps the snapshot and redraws; `tick()` redraws (ages). Card views are
created on each render; eight small cards every ten minutes is nothing.

### `StandbyFeed`

A new `fetchGit` runnable every `GIT_MS`: discovery → for each candidate the commits pages → the
pipelines of the shown repositories → `GitData.Snapshot`; stored in the feed and published inside
`StandbySnapshot` (new field `git`, null until the first fetch). When `cfg.gitlabProjects` is empty,
`ci` becomes `snapshot.statusCells()`. Posted at start when `cfg.has(PAGE_GIT) && cfg.hasGitlab()`.

### `PagesConfig`

`PAGE_GIT = "git"`; kept only when `gitlabUrl` and `gitlabToken` are set; `needsFeed()` includes it;
`hasGitlab()` no longer needs projects (`gitlabUrl` and `gitlabToken` suffice); new `gitlabGroups`
(trimmed, no blanks, trailing slashes removed). `Config` reads `gitlab_groups` from config.xml and
the theme.

### `MainActivity`

`setUpPages` maps `PAGE_GIT` to the `gitPage` child of the pager; the feed listener renders
`s.git` into it; `onPage` calls `gitPage.tick()` when it comes on screen; the minute tick refreshes
it like the weather page.

## Testing

- `GitDataTest`: `parseProjects` keeps id and path, drops archived ones and applies the group
  prefixes; `parseCommits` reads author, title and date and skips a dated-less entry; `repo` fills
  the right day slots for commits today, yesterday and 29 days ago and ignores a 31-day-old one;
  `total`, `last(7)` and `tier` (10 this week hot, 9 warm, 0 this week but 6 last month cold);
  `Snapshot` sorts and caps to SHOWN, `dailyTotals`, `thisWeek`/`lastWeek`, `activeRepos`,
  `statusCells` prefers pipelines; `isWeekend` on a known Friday (indices 29 Fri, 24 Sun, 23 Sat);
  `since` formats UTC.
- `PagesConfigTest`: `git` needs the GitLab URL and token; `needsFeed` and `hasGitlab` without
  projects; `gitlab_groups` tidied.
- On the TV: push the meeting-room theme with the local token, flip Right twice from the clock,
  screenshot after the first fetch; check the cards do not clip, the numbers match a spot check with
  curl, the ages read naturally, and the status bar shows three discovered projects.
