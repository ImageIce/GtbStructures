# GtbStructures Changelog

## 1.1

Compared with 1.0, this release turns GtbStructures from a hint-only
overlay into a full Guess-The-Build companion: it tracks games
round-by-round, exports structured game history, adds a custom scoreboard,
and expands the hint tools into multi-language practice utilities.

### Added

- **Full game tracking.** The mod now follows GTB games from lobby through
  the final leaderboard. It records players, builders, themes, theme
  lengths, guesses, correct guesses, hints, per-round score deltas,
  rejoin gaps, final rankings, and unresolved residuals.
- **Auto-saved game history.** Completed games, interrupted games, manual
  exports, and the live `current.json` snapshot are written under
  `.minecraft/config/gtbstats/`.
- **Custom scoreboard.** `/gtbs scoreboard` toggles a GTU-style live panel
  with round/timer/theme lines, rank-colored names, emblems, builder-state
  icons, per-round score chips, total ranges, and uncertainty markers.
- **Hypixel scoreboard skin.** `/gtbs scoreboard skin <default|hypixel>`
  switches between the fully custom panel and a Hypixel-style skin that
  keeps the vanilla sidebar layout while replacing leaderboard rows with
  the mod's tracked rows.
- **Multi-language builder-score forks.** Non-English themes can resolve to
  multiple English themes with different builder-point values. The custom
  scoreboard shows ranges until a scoreboard observation collapses the
  fork.
- **`/gtbs stats`.** Print a summary of your current or most recent game
  in chat: rounds, guesses, correct guesses, scores, and tracking state.
- **`/gtbs export`.** Manually export the current game state to JSON.
- **`/gtbs tracking`.** Toggle game tracking on or off independently of
  the hint overlay.
- **`/gtbs autorequeue`.** Auto-queue the next game when the current one
  ends.
- **`/gtbs haveibuilt`.** Tells you whether you've built yet in the
  current game, and which round(s) you built on.
- **`/gtbs whohasbuilt`.** Lists everyone who has built or is up next to
  build in the current game.
- **`/gtbs showallmatching`.** Once a round is over, the mod prints every
  theme whose name in the round's language matches the initial hint
  pattern. A sub-toggle adds the English origin in parentheses for
  non-English languages.
- **`/gtbs gtw start [language]` and `/gtbs guess <text>`.** Practice
  mini-game in chat: the mod picks a random English theme, displays its
  hint in the selected language with progressive letter reveals, and
  accepts translated answers. `/gtbs gtw stop` reveals the theme, and
  `/gtbs gtw showenglish <true|false>` controls English annotations.
  Aliases: `/gtw` for `/gtbs gtw`, and `/g` or `/guess` for `/gtbs guess`.
- **`/gtbs help`.** Help is grouped into General, Game Tracking, Hint
  Overlay, Custom Scoreboard, and Miscellaneous sections, with short
  explanations for tracking output and scoreboard symbols.
- **24 supported languages** for chat, scoreboard, GUI, and title
  detection: en, fr, de, it, nl, es, pt_BR, pt, ru, ko, pl, ja, pirate,
  no, sv, tr, da, cs, fi, zh_CN, zh_TW, ro, hu, uk. Mid-game language
  changes no longer break tracking.
- **Builder theme-option capture.** When you're the builder and the
  theme-pick chest opens, the mod records all three options the game
  offered you.
- **Rank color and emblem capture.** Player names in the custom scoreboard
  use captured Hypixel rank colors, and GTB shop emblems are retained
  when available from chat, tab/team metadata, or packet snapshots.
- **Packet-driven tab/team metadata capture.** The mod listens for
  player-list and team packets to catch transient rank/emblem data at
  game start instead of relying on fixed polling timers.
- **Bundled translation data** (`translations-data.json`). Powers
  `showallmatching`, GTW, and multi-language theme lookups.

### Changed

- **Hint overlay** still works as in 1.0: same position, fade, and color
  settings. It now coexists with the full tracking system rather than
  being the whole mod.
- **Source layout** is split by responsibility: scoreboard rendering and
  tracking, GTB tracking/export commands, GTW practice-game code, and
  miscellaneous client metadata helpers.
- **Wrong-argument messages** for `/gtbs` are shorter and point at
  `/gtbs help`.
- **Vanilla scoreboard suppression** is tied to active GTB rounds so the
  custom scoreboard does not compete with the original sidebar.

### Fixed

- **Theme sync in the custom scoreboard.** The displayed theme now updates
  from the vanilla scoreboard when revealed, and resets to `???` between
  rounds instead of leaking the previous round's theme.
- **Disconnect/rejoin uncertainty.** Missed rounds inject score ranges and
  `(!)` markers only where data is actually uncertain; authoritative
  scoreboard observations clear the marker for observed players.
- **Nick/denick succession.** Direct identity transfers preserve tracked
  score history, leaderboard state, rank/emblem metadata, and builder
  state. Late unrecorded identities are tracked conservatively and merged
  only when final-game evidence is strong enough.
- **Late lobby joins.** Join-message rank parsing backs up tab snapshots
  for players who enter the GTB lobby shortly before game start.
- **Default emblem color.** Emblems without an explicit color code render
  as white instead of inheriting the wrong rank color.
- **Custom scoreboard row details.** Builder-state icons are scaled and
  aligned with Minecraft text, the panel is vertically centered, and the
  Hypixel skin identifies Hypixel's hidden sidebar position keys correctly.

### Performance

- Theme lookup for `/gtbs showallmatching` is bucketed by language and
  hint length, so even on a long hint the search only walks relevant
  themes; typical lookup is under a millisecond.
- Translation data is hot-swapped atomically on reload; in-flight lookups
  never see a half-built index.
- Player-list metadata capture is packet-driven and event-drained, which
  avoids hard-coded extraction timers while still catching short-lived tab
  content.

### Notes

- Saved games are written to `.minecraft/config/gtbstats/`. Safe to delete
  individual JSON files at any time.
- Tracking is best-effort: if Hypixel changes a chat or scoreboard string,
  that specific event may be missed until the language patterns are
  updated. The hint overlay is independent and keeps working.
- Hypixel nicks can look like real Minecraft usernames. The identity
  resolver is intentionally conservative and leaves ambiguous nick/denick
  cases unresolved instead of merging unrelated players.

## 1.0

Initial release.

- Hint overlay: parses GTB action-bar messages and shows the word
  structure (for example, `[ 4-3 ]` for a 4-letter + 3-letter theme)
  above the hotbar for about 2.5 seconds with a fade-out.
- About 22 hint-prefix locales recognised.
- Configurable overlay position (`/gtbs setlocation`) and colors
  (`/gtbs setcolor`, `/gtbs colorlist`).
- `/gtbs toggle` to enable or disable the overlay.
- Settings persist in `config/GtbStructures.cfg`.
