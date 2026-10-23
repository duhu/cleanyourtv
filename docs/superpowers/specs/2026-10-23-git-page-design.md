# The git page: a repo panorama on Home

Design for the office TV, 2026-10-23. Mockup: `designs/git-board/A 仓库卡片墙.html` (approved; B, a
trend with a ranked list, was not chosen). Fourth page of the Home pager of
`docs/superpowers/specs/2026-10-22-home-pager-design.md`.

## Goal

One more page to the right of the weather: how busy the team's repositories have been over the last
30 days, and who touched each one last. A big total, a daily bar chart for all repositories, then a
wall of up to eight repository cards sorted by activity. Someone walking past the meeting room reads
"wevantage, 244 commits, 李想 12 minutes ago" without stopping.

This version draws the page from **built-in sample data** so the layout can be judged on the real TV.
No network, no new theme keys. The data source (every project of one GitLab group, discovered
automatically, with commits per day, the last commit and the latest pipeline) is the next design;
it will fill the same `GitData.Snapshot` and nothing in the page changes. Until then the header's
right caption reads "示例数据" so nobody takes the numbers for real.

## Decisions

| Question | Decision |
|---|---|
| Page name | `git`, accepted by `PagesConfig` like `clock` and `weather`. The meeting-room theme uses `["apps", "clock", "weather", "git"]`: clock stays the default, Right from the weather reaches the repositories |
| Data | `GitData.Snapshot`: a list of `Repo` (name, group path, 30 daily commit counts ending today, last commit author, time and message, latest pipeline `CiStatus`) and the instant the numbers were taken. Pure Java, built by `GitData.sample(now)` for now |
| Header | Top-left: the 30-day total of all repositories, 75sp extralight, then "次提交 · 近 30 天". Under it: "N 个活跃仓库 · 本周 X · 上周 Y". Top-right: the daily chart, 350×65dp, with the caption "每日提交 · 全部仓库" on the left and "示例数据" on the right, and five date labels under it (the first day, then every 7 days, "今天" last) |
| Cards | At most eight, four per row, sorted by 30-day commits descending. A card: tier dot and name (16sp medium, one line, ellipsised), the 30-day count (17sp light, "提交" 10sp beside it) at the right; the group path under the name (10sp, 40 % white); a 28dp sparkline of the same 30 days; a footer line: author · age, the message ellipsised, the pipeline word ("CI ✓" green, "CI ✗" red, "CI …" blue, nothing when unknown). Fewer than five repositories make one row |
| Tiers | hot: a commit in the last 7 days (green dot); warm: a commit in the last 30 (amber); cold: none (dim dot and the whole card at 55 % alpha) |
| Charts | One `BarChartView` draws both the big chart and the sparklines: bars with a gap, heights relative to the largest value, at least 2dp; weekend days at 20 % white, weekdays 42 %, today white. Which days are weekends comes from the snapshot's instant (`GitData.isWeekend(asOf, dayIndex)`) |
| Ages | "12 分钟前", "2 小时前", "3 天前" through the existing `StandbyData.age` and `StatusCells.ageText`; refreshed when the page comes on screen and on the minute tick, like the weather page |
| Empty | A snapshot with no repositories shows "等待仓库数据…" centred, like the weather page before its first fetch. The sample is never empty, so this only shows once a real feed exists and has nothing yet |
| Keys and return | As on the clock and weather pages: Left/Right flip, OK goes to the apps page, Back to the default page; the return timer is unchanged |
| Feed | Untouched. `needsFeed()` does not change; `StandbyFeed` does not know the page yet |
| Failure | Every call into the page runs under `StandbyFeature.run`, so a drawing bug switches the pages feature off for the process and never takes Home down |
| Theme and docs | `themes/meeting-room/config.json` adds `"git"` to `pages`; the themes README row and the meeting-room section say the fourth page shows sample repository numbers until the GitLab source lands |

## Layout

Mockup pixels are 2 per dp (1080p panel). Left and right margins 48dp; the status bar keeps its
place at the bottom (`activity_main.xml`), the page is the area above it.

- Total: left 56dp, top 42dp, 75sp `outfit_extralight`, letter spacing −0.04. "次提交 · 近 30 天" 17sp at
  70 % white on the same baseline, 13dp to the right. The sub line 14sp at 40 % white with the numbers at
  70 %, 10dp under the total.
- Daily chart: right 56dp, top 48dp, width 350dp, bars 65dp high; caption 12sp at 55 % white above
  (7dp gap); labels 11sp at 40 % white below (6dp gap).
- Cards: top 165dp, four columns, 12dp gaps, each 124dp high, `glass_panel` background, padding 15dp
  horizontal, 12dp top, 11dp bottom. Row 2 under row 1 with the same gap; nothing scrolls.
- Inside a card: header row (dot 7dp, name, count); group line 1dp under; sparkline 8dp under the group,
  28dp high; the footer pinned to the bottom, 11.5sp at 70 % white for the author, 55 % for the message.

## Components

### `GitData` (pure Java, `GitDataTest`)

```java
final class GitData {
    static final int DAYS = 30;
    enum Tier { HOT, WARM, COLD }
    static final class Repo {
        final String name, group;
        final int[] daily;               // DAYS values, today last; never null, never shorter
        final String lastAuthor, lastMessage;
        final long lastAtMillis;         // 0 when unknown
        final StandbyData.CiStatus ci;
        int total();                     // sum of daily
        int last(int days);              // sum of the last n values
        Tier tier();                     // HOT when last(7) > 0, WARM when total() > 0, else COLD
    }
    static final class Snapshot {
        static final Snapshot EMPTY;
        final List<Repo> repos;          // sorted by total() descending, ties by name
        final long asOfMillis;
        int[] dailyTotals();             // DAYS sums across repos
        int total();
        int activeRepos();               // tier HOT
        int thisWeek();                  // last 7 days, all repos
        int lastWeek();                  // the 7 days before those
    }
    static boolean isWeekend(long asOfMillis, int dayIndex);   // dayIndex 0..DAYS-1, DAYS-1 is the day of asOf, local zone
    static Snapshot sample(long nowMillis);                    // the eight repositories of the mockup
}
```

`Repo` copies and pads or trims `daily` to `DAYS` so a short feed never breaks the chart. `sample`
builds last-commit times relative to `now` (12 minutes, 38 minutes, 1 hour, 2 hours, 15 hours, 22
hours, 3 days, 25 minutes ago) so the ages look alive on the TV.

### `BarChartView`

A `View` with `setValues(int[] values, boolean[] weekend)` and `setBarColors(int weekday, int weekend,
int today)`. `onDraw` divides the width into `values.length` slots with a gap of a quarter slot (at least
1dp), rounds the top corners by half the bar width, and draws each bar from the bottom with height
`max(2dp, value / max * height)`; an all-zero series draws every bar at 2dp. No animation, no text.

### `GitPageView` (`res/layout/git_page.xml`)

A `FrameLayout` subclass like the weather page: the waiting line, the header views, the chart, a
`GridLayout`-free pair of horizontal `LinearLayout` rows for the cards (built in code, `glass_panel`
background). `render(GitData.Snapshot)` keeps the snapshot and redraws; `tick()` redraws (ages).
Card views are created on each render; eight small cards once every refresh is nothing.

### `MainActivity`

`setUpPages` maps `PagesConfig.PAGE_GIT` to the `gitPage` child of the pager and renders
`GitData.sample(System.currentTimeMillis())` into it under `StandbyFeature.run`. `onPage` calls
`gitPage.tick()` when it comes on screen; the minute tick that refreshes the weather page refreshes
this one too.

### `PagesConfig`

`PAGE_GIT = "git"` joins `pageName`. Nothing else: no source check yet.

## Testing

- `GitDataTest`: a `Repo` pads a short series and trims a long one; `total`, `last(7)` and `tier` on
  hand-made series (hot, warm, cold); `Snapshot` sorts by total then name, `dailyTotals` adds across
  repositories, `thisWeek`/`lastWeek` take the right windows; `isWeekend` for a known Friday instant
  (index 29 Friday → false, 28 Thursday → false, 27 Wednesday → false, 24 Sunday → true, 23 Saturday
  → true); `sample` has eight repositories, each with 30 values, five hot, and every age in the past.
- `PagesConfigTest`: `git` is kept and ordered; `["apps","clock","weather","git"]` has four pages.
- On the TV: push the meeting-room theme, flip Right twice from the clock, screenshot; check the
  cards do not clip at 1080p, ages read naturally, and Left/Right/OK/Back behave as on the weather page.

## Later (not in this spec)

The GitLab source: a `gitlab_group` theme key; `GET /api/v4/groups/<path>/projects?include_subgroups=true&archived=false&order_by=last_activity_at&per_page=50`,
then per project `GET /projects/<id>/repository/commits?since=<30 days ago>&per_page=100` (paged, counted
per local day; the first commit is the last commit) and the existing pipelines call. The status bar's CI
cells would then come from the same projects. Contributors per repository, a "busiest day" line and a
ranked view are possible once the data is real.
