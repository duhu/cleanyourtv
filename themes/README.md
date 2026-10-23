# Themes

A theme is a folder with a `config.json` and, optionally, a `wallpaper.jpg`. Pushing one changes the
launcher's dock, layout and look on the TV without rebuilding the APK: the launcher reads
`config.json` as an overlay over its built-in `config.xml` the next time it starts.
Choices made in Launcher settings take priority until you choose Follow theme. Pushing a theme or
using `tools/theme.sh --reset` also clears those on-TV column and motion choices.

```bash
TV_HOST=192.168.1.50 tools/theme.sh themes/aurora          # push a theme and restart Home
TV_HOST=192.168.1.50 tools/theme.sh --reset                # back to the built-in config.xml
powershell -ExecutionPolicy Bypass -File tools\theme.ps1 -TvHost 192.168.1.50 themes\aurora
```

| Theme | What it does |
|---|---|
| `default/` | The built-in look written out as JSON, as a starting point for your own |
| `aurora/` | A dark aurora wallpaper (teal and violet on navy, darker at the bottom) with a matching background colour |
| `dussehra/` | Festive skin for Dussehra (ends 21 Oct 2026): a marigold toran, a faint gold rangoli, a diya for Wi-Fi, a bell for Inputs, a marigold for Settings and a petal shower |
| `six-columns/` | Six smaller tiles per row and one grid row visible under the dock |
| `meeting-room/` | An office TV used only for HDMI screen sharing: Home is four pages (apps, a flip clock, the weather, the team's GitLab repositories) flipped with Left/Right and resting on the clock, with a CI status bar and automatic switching to a freshly plugged HDMI port (Redmi A50 2025) |

## Keys

Every key is optional; a missing key keeps the value from `config.xml`, an unknown or wrongly typed key
is ignored, and a file that doesn't parse is ignored as a whole, so a theme can never break Home.

Keys you do not want in git, such as the GitLab token, go in `config.local.json` next to `config.json`
(the folder's `.gitignore` entry keeps it out); `tools/theme.sh` and `theme.ps1` lay its top-level keys
over `config.json` when they push, so the committed file keeps `REPLACE_ME`.

| Key | Type | Meaning |
|---|---|---|
| `dock_apps` | list of package names | The dock row, in order; missing apps are skipped |
| `fill_dock` | bool | Fill the dock slots `dock_apps` leaves free with the first grid apps (true by default); false leaves them empty |
| `hidden_apps` | list of package names | Never shown as tiles |
| `only_apps` | list of package names | When not empty, only these packages get tiles (`hidden_apps` still applies): a TV kept for one thing, e.g. a meeting-room set that only casts |
| `show_greeting` | bool | The greeting line above the clock ("Good evening"); false hides it |
| `tile_style` | `card` or `capsule` | `card` (default): banner or icon on a dark card with the name under it and the glass highlight. `capsule`: a round icon and the name in one pill like the top-right buttons, faint until focused, no glass and no large title |
| `dim_top_buttons` | bool | The Inputs, network and Settings buttons stay at 40 % until one has focus |
| `extra_apps` | list of `package/activity` | Tiles for exported activities that declare no launcher category (Xiaomi China firmware hides its app store this way) |
| `live_tv_package`, `live_tv_action` | string | The Live TV app and, if it has no leanback entry, the action to start it with |
| `input_extra`, `input_map` | string | For players that switch inputs by an integer extra instead of a channel URI (Xiaomi China firmware): the extra's name, and `HW5=23,HW6=24,...` pairing the framework input id with that number, sent with `input_action` |
| `source_panel_service`, `source_panel_action` | string | The TV's own source panel, for sets with no TvInputManager |
| `input_action` | string | Action sent with an HDMI/AV row's channel URI when nothing handles the standard `VIEW` (XGIMI: `com.xgimi.action.hdmiPlayer`) |
| `columns` | 1 to 8 | Tiles per row; tiles shrink to fit from 5 up |
| `rows_below_dock` | 0 to 3 | Grid rows left visible under the dock |
| `shamsi_date` | bool | Show the Solar Hijri date above the Gregorian one |
| `module_cards` | bool | Look for module cards and ask about them (`true`); `false` turns the feature off |
| `reduced_motion` | bool | Calmer motion for weak GPUs: stiff highlight spring, no tile overshoot, short fades, no entrance replay |
| `vpn_app` | string | App the network pill opens when a VPN is configured; also shows a red "VPN off" badge while no VPN is up |
| `animations` | bool | Basic focus, glass, title and tile entrance motion; true by default |
| `advanced_effects` | bool | Optional lifted tile, icon zoom, smooth ambient colour and app opening effects; false by default |
| `show_home_settings_shortcut` | bool | Keep the optional Default-home tile visible when the TV has a home picker; false by default |
| `background_color` | `#RRGGBB` | Colour behind everything; black by default |
| `sky_intensity` | 0 to 100 | Time-of-day backdrop: a dark gradient that follows the clock, 0 hides it (default) |
| `wallpaper` | bool | Show `wallpaper.jpg` from the theme folder (`true`), or ignore it (`false`) |
| `festival` | string | Turns on a festive skin: the network pill becomes a diya whose flame follows the connection, Inputs a temple bell, Settings a marigold, and the first Home of the day gets a petal shower. `dussehra` and `diwali` have built-in greetings |
| `festival_until` | `YYYY-MM-DD` | Last day the festive skin shows; it switches itself off after that. A malformed date turns it off |
| `greeting` | string | Replaces the time-of-day greeting while the festive skin is on |
| `accent_color` | `#RRGGBB` | Colour of the focus glow while the festive skin is on; diya gold if not set |
| `settings_key_keycodes` | list of keycodes | Remote buttons that open Settings, for `HomeKeyService` (TCL Google TV gear: `[83]`) |
| `settings_key_panel` | bool | Those buttons open a small quick settings panel (TV settings, Display, Network, Apps, Launcher settings) instead of the TV's Settings |
| `pages` | list of `apps`, `clock`, `weather`, `git` | The pages Left and Right flip through, in that order: `apps` is the launcher itself; `clock` a large clock with the date and the current weather; `weather` the current conditions, the next 12 hours and three days (needs `weather_lat`/`weather_lon`). Unknown names are dropped; `["apps"]` (the default) is the plain launcher with no flipping. On the apps page, Left on the leftmost item or Right on the rightmost flips; on the other pages OK jumps to the apps page and Back to the default page |
| `default_page` | string | The page Home opens on, comes back to from another app and returns to after idling; one of `pages`, else the first (`apps` by default) |
| `page_return_seconds` | 0 to 86400 | Seconds on another page without a key press before `default_page` comes back; 0 never (600 by default) |
| `flip_clock` | bool | The clock page as split-flap cards: hour and minute cards plus two smaller seconds cards, flipping on every change (true by default); false shows plain thin digits without seconds |
| `status_bar` | bool | A bar at the bottom of every page: `插线即投屏`, then the latest CI pipeline of each `gitlab_projects` entry (or of the `git` page's most active repositories). With more than one page the bar's right end shows page dots (they show without the bar too) |
| `idle_seconds` | 0 to 86400 | Seconds without a key press before Home dims, or sleeps inside the window below; 0 never (the default) |
| `dim_percent` | 0 to 95 | Opacity of the black layer that dims Home after `idle_seconds`; 0 never dims (50 by default). Any key brings the page back; the first press is swallowed unless it is a volume key |
| `sleep_time`, `wake_time` | `HH:mm` | The sleep window: weekdays from `sleep_time` to `wake_time`, all of Saturday and Sunday. Inside it an idle Home puts the TV to sleep like the power key (needs the device admin that `build-and-install.sh` activates). Nothing wakes the TV: the remote's power key does. Both empty keeps the TV awake |
| `weather_lat`, `weather_lon` | number | Open-Meteo coordinates for the weather on the clock and weather pages; both needed, no API key |
| `gitlab_url`, `gitlab_token` | string, string | A self-hosted GitLab and a read-only (`read_api`) token: the `git` page and the status bar's CI cells. The token sits in plain text in the TV's config.json (put it in `config.local.json`, see above); `REPLACE_ME` keeps the source off |
| `gitlab_projects` | list of `group/project` | Projects for the status bar's CI cells by hand, fetched every 5 minutes. Empty: the cells are the three most active repositories the `git` page finds |
| `gitlab_groups` | list of namespace paths | Keeps the `git` page to these groups (`"we-project"` also matches `we-project/sub/x`); empty means every project the token is a member of |
| `feed_url` | string | One JSON with everything instead of the two sources above, fetched every minute: `{"weather":{"temp_c":21,"text":"多云"},"ci":[{"name":"x","status":"success","updated_at":"2026-10-20T06:12:00Z"}]}` |
| `hdmi_auto_switch` | bool | Plugging a device into an HDMI port switches to it after a short toast; unplugging it brings Home back. Only changes count: a cable already in at boot does nothing |
| `hdmi_tiles` | bool | A quick-switch tile in the dock row for every HDMI port with a device plugged in (plug icon, port name, green dot); OK on it switches there. Focus lands on it whenever Home shows, so a cable already in at boot is one OK away. The tile comes and goes with the cable |

## Wallpapers

`wallpaper.jpg` is drawn full screen behind the launcher, centre-cropped. Keep it dark, 1920×1080 and
under about 300 KB: the text, the hero name and the dock band are designed for black, and a 1 GB TV
decodes the picture into memory on every Home start. Pictures larger than the screen are scaled down to
it; a picture that is still over 4K (about 8.4 million pixels) after it is sampled down to the screen is not shown at all, and the plain colour stays.

## Reduced motion

`"reduced_motion": true` keeps the look but calms the movement: the highlight spring is stiff instead
of bouncing, tiles zoom without overshoot, fades are shorter, and Home does not replay the tile
entrance when you come back to it. Use it on TVs whose GPU cannot keep up; a MediaTek MT5895 on
Android 10 is the known case. The default, `false`, keeps the normal motion.

```json
{ "reduced_motion": true }
```

## Time-of-day backdrop

`"sky_intensity": 60` draws a dark gradient over the wallpaper and behind everything else, that follows the
clock: night blues, a soft blue day, then a warm horizon that peaks just before eight in the evening.
The glow near the bottom edge travels from left to right with the sun. The colours stay dark so the
white clock, the hero name and the dock band keep their contrast. It is drawn as one 480×270 bitmap
and redrawn once a minute, never animated, so even a 1 GB TV has nothing to keep up with. `0` keeps
the plain background; the default is off (the reference launcher looks exactly as before).

```json
{ "sky_intensity": 60 }
```

## Meeting-room pages

`themes/meeting-room` turns the launcher into an office screen-sharing TV. Home is four pages:
the apps page (the casting capsule and HDMI quick-switch tiles), a large flip clock with the date
and weather, a weather page with the next hours and three days, and a git page: the 30-day commit
total and daily chart of the GitLab projects the token can see, with a card per repository (commits,
a sparkline, who committed last and when, the pipeline), refreshed every ten minutes
(`docs/superpowers/specs/2026-10-23-git-page-design.md`). Left and Right flip between
them; the TV rests on the clock and goes back to it ten minutes after the last key press, or as
soon as you come back from an app. The status bar at the bottom shows `插线即投屏` and the latest CI
pipeline of the three most active repositories. Plugging a laptop into any HDMI port shows "HDMI 2
detected, switching…" for a moment and switches to it; unplugging comes back. The TV needs a network
for the weather and CI (the clock works without). After ten minutes without a key the pages dim to
half; from 20:00 to 09:00 on weekdays and all weekend the TV goes to sleep instead and stays asleep
until someone presses the remote's power key (the launcher cannot wake the TV: Xiaomi's power service
only accepts the power key). The wallpaper is a dusk mountain range (Unsplash photo 17_tB-oI0ao,
Unsplash License) darkened so the white clock and the status bar stay readable. Put a read-only
GitLab token in `gitlab_token` before pushing the theme, and change the coordinates and times to your
office. The design and the device checks are in
`docs/superpowers/specs/2026-10-22-home-pager-design.md` and
`docs/superpowers/specs/2026-10-22-screen-schedule-design.md`.

## Adding a theme

Open a PR with a new folder here, a one-line row in the table above and a screenshot from a real TV
in the PR description. Keep the dock list generic (the big streaming apps) unless the theme is for a
specific TV, and say which TV you tried it on.
