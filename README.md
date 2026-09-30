# GtbStructures

GtbStructures (GTBS) is a client-side utility mod for Hypixel's Guess The Build. It adds configurable game overlays, multilingual theme tools, local match statistics, and quality-of-life commands across legacy Forge and current Fabric/Forge versions.

GTBS is an independent community project. It is not affiliated with or endorsed by Hypixel Studios or Mojang Studios.

Download GTBS from [GitHub Releases](https://github.com/ImageIce/GtbStructures/releases) or [Modrinth](https://modrinth.com/mod/gtbstructures). See the [changelog](CHANGELOG.md) for version-to-version changes.

## Features

- Configurable custom scoreboards with player state, round points, places, Build Battle titles, emblems, optional Default-skin builder details, and optional Hypixel-skin round progress.
- Independently scalable hint-structure, handheld-item, environment, and chat-cooldown overlays.
- Multilingual theme matching, difficulty-filtered GTW practice, optional result counts, configurable translation-copy casing, shortcut reminders, and GuessPeek annotations.
- Local game tracking, statistics summaries, and opt-in stats/raw-packet exports.
- Experimental configurable correct-guess sounds with three point selectors, None/Random choices, default volume 20 and maximum volume 30, and a session-only `/gtbs preview` editor. Modern targets enumerate the complete Minecraft sound catalog, exclude music and discs, use native English subtitle captions with audited qualifiers, and show semantically matched block, item, or species-specific spawn-egg icons.
- Preview and layout controls for configuring interface elements outside a live game.
- Utility commands for queueing, leaving/rejoining, configurable-delay requeueing, and checking builder progress.

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

The 26.x roots reuse version-neutral implementation code and resources from the maintained 1.21 loader baseline, while keeping their target-specific adapters and metadata locally.

For the 1.1.1 publication, the 26.1 and 26.2 Forge roots are source-only:
their built JARs are intentionally not published. GTBS 1.1.1 is the final
version that includes 26.x Forge source; those targets are discontinued after
this version.

## Installation

1. Install the loader matching your Minecraft version. Fabric installations also need Fabric API.
2. Download the GTBS JAR for that exact Minecraft version and loader from the repository's Releases page.
3. Put the JAR in the instance's `mods` directory.
4. Remove older or duplicate GTBS JARs from that same instance, then start Minecraft.
5. Use `/gtbs help` and `/gtbs preview` to explore and configure the mod.

Do not mix Fabric and Forge artifacts or use a JAR built for a different Minecraft line.

## Building and testing

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

### Accelerated development verification

Use `tools/gtbs.ps1` from the project directory that contains `tools` during active development. It
selects Java 8, 21, or 25 automatically, keeps incremental outputs, and runs
only the checks mapped to the affected subsystem. The first invocation
starts the appropriate Gradle daemon; later invocations reuse it. This does not
replace the clean seven-root release gate.

```powershell
# Inspect what would run.
.\tools\gtbs.ps1 fast -Target 26.2-fabric -Area preview -PlanOnly

# Incrementally check one target and run the Preview checks.
.\tools\gtbs.ps1 fast -Target 26.2-fabric -Area preview

# Check the affected subsystem across every maintained target.
.\tools\gtbs.ps1 parity -Area preview

# Run the clean, complete, sequential release matrix.
.\tools\gtbs.ps1 release
```

Available areas are `preview`, `scoreboard`, `translation`, `lrj`,
`guesspeek`, `guesssounds`, `autorequeue`, `identity`, `commands`, and `resources`. Add one or
more exact Gradle tasks with `-Task`; use `-NoDaemon` when daemon reuse is not
desired. `fast` also accepts `-DeployTo <existing-jar>` after successful
verification. Deployment refuses a missing target, multiple active GTBS JARs,
any remaining Java process, or a SHA-256 mismatch, and never creates a backup.
All GTBS Gradle daemon families are stopped automatically before this guard.
Use `-PlanOnly -Json` when another tool needs the resolved plan.

The area/task mapping and release matrix live in
`tools/verification-scopes.json`. Update that manifest whenever a relevant
verification task or maintained target changes.

## Privacy and network behavior

GTBS does not ship a hardcoded player leaderboard or account roster. A fresh installation starts with an empty leaderboard cache.

The mod makes read-only HTTP requests to:

- Hypixel's public Build Battle leaderboard, used to refresh the local builder-ranking fallback.
- The `zmh-program/gtb_platform` raw translation dataset on GitHub, used to refresh theme translations.

Those requests use a GTBS user-agent and do not upload GTBS logs, account credentials, or local statistics. Public leaderboard entries and player ranks observed in game may be cached locally in `.minecraft/config/gtbstructures_builder_leaderboard.txt`.

Game records, interrupted-session recovery, translation history, and user-requested exports are stored under `.minecraft/config/gtbstats/`. These files can contain player names, chat/scoreboard text, and match details. Review and sanitize them before attaching them to an issue or sharing them publicly.

Build outputs, development runs, logs, crash reports, IDE settings, and heap dumps are ignored by the repository-level `.gitignore`.

## Translation history

Every maintained JAR bundles a translation snapshot and compressed history used to resolve old log entries consistently. Runtime-observed snapshots are stored as deduplicated compressed files under `.minecraft/config/gtbstats/translation-history/`.

Run `node tools/generate_translation_history_seed.js` to refresh all loader bundles from GitHub. Use `--target=legacy`, `--target=fabric`, or `--target=forge` for one loader, and `--dry-run` to download and validate without writing resources.

## Contributing

- Keep equivalent Fabric and Forge behavior aligned where both loaders are supported.
- Keep loader- or Minecraft-specific runtime code inside its applicable project.
- Run the affected root's clean build before proposing behavior changes.
- Never commit real player fixtures, account UUIDs, runtime logs, local statistics, or credentials.
- Add future targets under `versions/<minecraft-version>/<loader>/` and update this README and the repository project documentation.

## License and attribution

Original GTBS code is licensed under the [MIT License](LICENSE). Adapted code, artwork, and bundled data are documented in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
