# GtbStructures

GtbStructures (GTBS) is a client-side utility mod for Hypixel's Guess The Build. It adds configurable game overlays, multilingual theme tools, local match statistics, and quality-of-life commands across legacy Forge and current Fabric/Forge versions.

You can download the mod through [releases](https://github.com/ImageIce/GtbStructures/releases) or by visiting the [Modrinth](https://modrinth.com/mod/gtbstructures) page.

## Features

- Configurable custom scoreboards with player state, round points, places, Build Battle titles, and emblems.
- Hint-structure, handheld-item, environment, and chat-cooldown overlays.
- Multilingual theme matching, translation lookup, shortcut reminders, and GuessPeek annotations.
- Local game tracking, statistics summaries, and opt-in stats/raw-packet exports.
- Preview and layout controls for configuring interface elements outside a live game.
- Utility commands for queueing, leaving/rejoining, requeueing, and checking builder progress.

Run `/gtbs help` in game for the categorized command list. `/gtbs preview` opens a session-only preview of enabled interface features.

## Supported versions

Each loader directory is an independent Gradle project; this repository is not an aggregate or Stonecutter build.

| Minecraft | Loader | Runtime/build Java | Source root |
| --- | --- | --- | --- |
| 1.8.9 | Forge | Java 8 | `versions/1.8.9/forge` |
| 1.21.10-1.21.11 | Fabric | Java 21 | `versions/1.21.10-1.21.11/fabric` |
| 1.21.10-1.21.11 | Forge | Java 21 | `versions/1.21.10-1.21.11/forge` |
| 26.1 | Fabric | Java 25 | `versions/26.1/fabric` |
| 26.1 | Forge | Java 25 | `versions/26.1/forge` |
| 26.2 | Fabric | Java 25 | `versions/26.2/fabric` |
| 26.2 | Forge | Java 25 | `versions/26.2/forge` |

## Building

Run the wrapper from the loader directory being built. Artifacts are written to that root's `build/libs/` directory.

| Root | Verification command |
| --- | --- |
| `versions/1.8.9/forge` | `gradlew.bat --no-daemon clean build` |
| `versions/1.21.10-1.21.11/fabric` | `gradlew.bat --no-daemon clean build` |
| `versions/1.21.10-1.21.11/forge` | `gradlew.bat --no-daemon clean build` |
| `versions/26.1/fabric` | `gradlew.bat --no-daemon clean build` |
| `versions/26.1/forge` | `gradlew.bat --no-daemon clean build` |
| `versions/26.2/fabric` | `gradlew.bat --no-daemon clean build` |
| `versions/26.2/forge` | `gradlew.bat --no-daemon clean build` |

Use Java 8 for the legacy root, Java 21 for 1.21.10-1.21.11, and Java 25 for 26.x. On a constrained system drive, pass `-PgtbsTargetWorkRoot=<path>` to redirect generated build/run output without changing the portable default.

## Privacy and network behavior

GTBS does not ship a hardcoded player leaderboard or account roster. A fresh installation starts with an empty leaderboard cache.

The mod makes read-only HTTP requests to:

- Hypixel's public Build Battle leaderboard, used to refresh the local builder-ranking fallback.
- The `zmh-program/gtb_platform` raw translation dataset on GitHub, used to refresh theme translations.

Those requests use a GTBS user-agent and do not upload GTBS logs, account credentials, or local statistics. Public leaderboard entries and player ranks observed in game may be cached locally in `.minecraft/config/gtbstructures_builder_leaderboard.txt`.

Game records, interrupted-session recovery, translation history, and user-requested exports are stored under `.minecraft/config/gtbstats/`.

## Translation history

Every maintained JAR bundles a translation snapshot and compressed history used to resolve old log entries consistently. Runtime-observed snapshots are stored as deduplicated compressed files under `.minecraft/config/gtbstats/translation-history/`.

## License and attribution

Original GTBS code is licensed under the [MIT License](LICENSE). Adapted code, artwork, and bundled data are documented in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
