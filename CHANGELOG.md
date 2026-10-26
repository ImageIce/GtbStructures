# GtbStructures Changelog

## 1.2.0

### Added

- **Minecraft 26.3 Fabric.** Now supported alongside the existing Minecraft versions.
- **Cooldown sounds.** Add a ready sound option to cooldown counter, along with optional Progress Beats, and a Test button in `/gtbs preview` to try your setup.
- **LRJ delay.** Set an extra wait after the /l lobby is ready.
- **Optional firework muting.** Turn end-game firework sounds on or off in Experimental settings.
- **More flexible Troll Mode messages.** Send a single Troll Mode message with `/troll` without changing the persistent setting.
- **Easier help.** Find commands with `/gtbs search`, get better suggestions, and click the header arrows to change pages.
- **Update summaries.** Update notifications now include a short preview of what's new.

### Changed

- Expanded localized Build Battle ranks, including Celestial, Divine, Ascended, Prospect, and Artisan, plus seasonal MVP+/MVP++ join messages.
- Refreshed bundled translations while retaining translation history for older game records.
- Added a GitHub project link to the mod information screen.

### Fixed

- The custom scoreboard no longer stays at `1/?` when the server has already supplied the round total.
- Guess detection now handles Hypixel's recent Cyrillic-to-Latin chat changes while remaining compatible with older chat.
- Ambiguous translated themes are resolved more reliably using chat and accepted guesses. Conflicting chat restores the unknown theme and possible-point display until stronger evidence resolves it.
- Minor scoreboard L/RJ compatibility fixes.

### Compatibility and publication scope

- Supports Minecraft 1.8.9 Forge, 1.21.10-1.21.11 Fabric/Forge, and 26.1-26.3 Fabric. Fabric needs Fabric API; 26.x builds need Java 25.
- 26.1/26.2 Forge is discontinued and is not included in this release.


## 1.1.1

GTBS 1.1.1 brings the legacy and modern builds back into feature parity,
expands the Preview editor, and hardens game tracking around nick changes,
disconnects, localized scoreboards, and skipped builders.

### Added

- **Configurable correct-guess sounds.** Choose separate sounds for 1-, 2-,
  and 3-point guesses, including None and Random choices, and set their volume
  from `/gtbs preview`.
- **Independent HUD scaling.** The hint, handheld-item, environment, and chat
  cooldown panels can be scaled independently from commands or Preview.
- **More scoreboard controls.** Preview can independently show or hide current
  builder details and round progress for the Default and Hypixel skins.
- **More language-tool controls.** GTW supports difficulty-filtered theme
  pools; matching-theme output can include a result count; copied translations
  can preserve or remove capitalization; and shortcut reminder languages can
  be organized into editable groups.
- **Safer automation.** Auto-requeue has a configurable delay, while
  leave/rejoin can optionally bypass lobby protection for an intentional
  reconnect.
- **Export links.** Export and stats notices can open their local folders
  without blocking the client.


### Changed

- Translation history now resolves themes against approval-time snapshots,
  restricts history work to current themes, and refreshes bundled translation
  data more defensively.
- Theme statistics count every offered builder choice as an occurrence, even
  when that choice was not selected.
- On 1.8.9, theme export can discover logs from common launcher layouts,
  accept recent logs outside conventionally named log folders, and report
  aggregate progress.
- Command help and tab completion are synchronized across legacy Forge and all
  modern Fabric/Forge targets.
- Preview menus have clearer layout, scrolling, selection, and field behavior
  across the supported Minecraft versions.

### Fixed

- Improved local and nicked player identity handling and builder-score
  preservation across rejoin, nick changes, late roster updates, and final
  score awards.
- Corrected localized lobby and round-end transitions, pre-round and consecutive
  builder skips, first-round countdowns, and unknown mid-game round totals.
- Prevented unrelated chat channels from being recorded as GTB guesses and kept
  paused lobby chat from leaking into the active game.
- Restored GuessPeek detection, offsets, hitboxes, and nick handling across
  legacy, Fabric, and Forge builds.
- Fixed auto-requeue cancellation and lobby-transition scheduling, plus direct
  leave/rejoin scheduling on modern builds.
- Fixed Preview interaction regressions including hidden Guess Sounds entry
  points, overlapping controls, scrollbar dragging, and volume editing.
- Fixed correct-guess sound replacement timing, pitch, captions, item lookup,
  and icons on the affected Fabric and Forge targets.
- Fixed GTW complement handling and made tied shortcut-language strengths
  resolve deterministically.
- Fixed active-scoreboard rank color capture on 1.8.9 and the fallback color
  for unranked players across legacy, Fabric, and Forge builds.

### Compatibility and publication scope

- The source tree covers Minecraft 1.8.9 Forge; 1.21.10-1.21.11 Fabric and
  Forge; and 26.1/26.2 Fabric and Forge.
- The 1.1.1 publication includes JARs for 1.8.9 Forge, 1.21.10-1.21.11
  Fabric and Forge, and 26.1/26.2 Fabric.
- 26.1 and 26.2 Forge are discontinued after 1.1.1 and will not receive
  subsequent GTBS source or binary maintenance.

## 1.1.0

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
