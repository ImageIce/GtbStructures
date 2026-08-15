package com.gtbstructures.player;

import com.gtbstructures.stats.GameStats;
import com.gtbstructures.stats.LangPatterns;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.util.StringUtils;

import java.util.*;

/**
 * Per-player metadata harvested from the client-side tab list (player info map).
 * Provides typed access to Hypixel rank, display name, ping, and skin status
 * for every player currently visible on the server.
 *
 * Refreshed by {@link GameStats#snapshotTabList()} each time the tab list is
 * captured — no separate poll loop needed.
 *
 * Plan reference: Phase 5 — Netty player-list rank/emblem/title harvest.
 * Uses the simpler polling approach (player info map) rather than raw Netty
 * packet interception, since both expose the same underlying data.
 */
public final class PlayerMetaCache {

    public static final PlayerMetaCache INSTANCE = new PlayerMetaCache();

    /** UUID → RankInfo. Preserves insertion order. */
    private final Map<UUID, RankInfo> byUuid = new LinkedHashMap<>();
    /** Lowercase IGN → UUID, rebuilt on each refresh. */
    private final Map<String, UUID> byName = new LinkedHashMap<>();

    /** Lowercase IGN → leading § color code (e.g. "§a" for VIP), harvested from
     *  the LOBBY-phase sidebar where Hypixel paints each waiting player's IGN
     *  in their network rank color. Survives game-start; cleared on next lobby. */
    private final Map<String, String> harvestedRankColors = new HashMap<>();
    /** Lowercase IGN → bracket text from a lobby line (e.g. "VIP+" if present). */
    private final Map<String, String> harvestedRankLabels = new HashMap<>();
    /** Lowercase IGN → emblem token (formatted, e.g. "§6⚑") harvested from
     *  the transient structured tab-list display name Hypixel pushes around
     *  game-start. Sticky across the rest of the game. */
    private final Map<String, String> harvestedEmblems = new HashMap<>();
    /** Lowercase IGN -> encoded Build Battle rank index from game-start team names. */
    private final Map<String, Integer> harvestedBbRankIndexes = new HashMap<>();
    /** Canonical IGN roster from GTB game-start encoded team names. */
    private final Set<String> encodedGameRoster = new LinkedHashSet<>();
    /** Lowercase IGN → captured structured display name (with § codes) from
     *  any non-null observation. Sticky so we don't lose data when Hypixel's
     *  later tab-list refreshes push a stripped-down version. */
    private final Map<String, String> capturedDisplayNames = new HashMap<>();
    /** IGN roster observed directly from tab-list packets. Cleared per lobby. */
    private final Set<String> packetRoster = new LinkedHashSet<>();
    /** UUID -> latest packet IGN. REMOVE_PLAYER often arrives after the name is no longer in the live tab map. */
    private final Map<UUID, String> packetUuidNames = new HashMap<>();
    /** Scoreboard-team formatting observed directly from S3E team packets. */
    private final Map<String, TeamFormat> packetTeams = new HashMap<>();
    /** Lowercase IGN -> latest observed team name from S3E packets. */
    private final Map<String, String> packetPlayerTeams = new HashMap<>();

    private static final class TeamFormat {
        final String prefix;
        final String suffix;

        TeamFormat(String prefix, String suffix) {
            this.prefix = prefix;
            this.suffix = suffix;
        }
    }

    public static final class RankInfo {
        public final UUID uuid;
        public final String ign;
        /** Raw formatted display name from the tab list (with § color codes), or null. */
        public final String displayName;
        /** Parsed Hypixel network rank, or null if unranked / not detected. */
        public final String hypixelRank;
        /** Parsed Build Battle title from the display name, or null. */
        public final String bbTitle;
        /** Ping in milliseconds. */
        public final int ping;
        /** True if the player has a custom skin loaded on the client. */
        public final boolean hasSkin;
        /** Sibling count of the IChatComponent display-name . -1 if no display name. */
        public final int siblingCount;

        RankInfo(UUID uuid, String ign, String displayName, String hypixelRank,
                 String bbTitle, int ping, boolean hasSkin, int siblingCount) {
            this.uuid = uuid;
            this.ign = ign;
            this.displayName = displayName;
            this.hypixelRank = hypixelRank;
            this.bbTitle = bbTitle;
            this.ping = ping;
            this.hasSkin = hasSkin;
            this.siblingCount = siblingCount;
        }
    }

    /** (The known set of Hypixel network ranks, in rough order.) */
    private static final Set<String> HYPIXEL_RANKS = new HashSet<>(Arrays.asList(
            "VIP", "VIP+", "MVP", "MVP+", "MVP++", "YOUTUBE",
            "HELPER", "MOD", "MODERATOR", "ADMIN", "ADMINISTRATOR", "OWNER",
            "BUILD TEAM", "MOJANG", "PIG", "PIG+++", "SLAYER", "ANGEL",
            "EVENTS", "GAME MASTER", "GM", "JR HELPER", "JR. HELPER"
    ));

    /**
     * Refresh the cache from the current tick's player info map.
     * Call this from {@link GameStats#snapshotTabList()} so both the IGN list
     * and the metadata cache are populated from the same data source.
     */
    public void refresh() {
        refresh(false);
    }

    public void refresh(boolean allowColorOnlyRankPrefixes) {
        byUuid.clear();
        byName.clear();
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) return;
        if (mc.thePlayer == null || mc.thePlayer.sendQueue == null) return;
        for (NetworkPlayerInfo info : mc.thePlayer.sendQueue.getPlayerInfoMap()) {
            if (info == null) continue;
            UUID uuid = info.getGameProfile().getId();
            String ign = info.getGameProfile().getName();
            if (ign == null || ign.isEmpty()) continue;

            String displayName = null;
            String teamFormattedName = null;
            int siblingCount = -1;
            net.minecraft.util.IChatComponent comp = info.getDisplayName();
            if (comp != null) {
                displayName = comp.getFormattedText();
                try {
                    java.util.List<net.minecraft.util.IChatComponent> sibs = comp.getSiblings();
                    siblingCount = sibs == null ? 0 : sibs.size();
                } catch (Throwable ignored) { /* best-effort */ }
            }

            // Hypixel typically does NOT call setPlayerListName — getDisplayName
            // returns null. Rank prefixes live on the scoreboard team for the
            // player. Build a synthetic display name as "<prefix><ign><suffix>"
            // so parseHypixelRank can extract the bracketed rank, and harvest
            // the leading § color code + bracket label into the sticky maps.
            // Sticky merge is essential: the lobby tab list has full rank
            // prefixes, but the in-game tab list often resets teams (or sets
            // them to plain colors), so without persistence we'd lose ranks at
            // game start. First-capture wins per IGN.
            String teamPrefix = null;
            if (mc.theWorld != null) {
                try {
                    net.minecraft.scoreboard.ScorePlayerTeam team =
                            mc.theWorld.getScoreboard().getPlayersTeam(ign);
                    if (team != null) {
                        String prefix = team.getColorPrefix();
                        String suffix = team.getColorSuffix();
                        teamPrefix = prefix;
                        teamFormattedName = net.minecraft.scoreboard.ScorePlayerTeam.formatPlayerName(team, ign);
                        if (displayName == null) {
                            displayName = teamFormattedName;
                        }
                        harvestFromTeamSuffix(ign.toLowerCase(), suffix);
                    }
                } catch (Throwable ignored) { /* best-effort */ }
            }
            String lowerIgn = ign.toLowerCase(Locale.ROOT);
            harvestFromTeamPrefix(lowerIgn, teamPrefix, allowColorOnlyRankPrefixes);
            harvestRankColorFromFormattedName(lowerIgn, teamFormattedName, ign, allowColorOnlyRankPrefixes);

            String hypixelRank = parseHypixelRank(displayName);
            String bbTitle = null; // BB titles not visible in tab-list display name

            int ping = info.getResponseTime();
            boolean hasSkin = info.getLocationSkin() != null;

            byUuid.put(uuid, new RankInfo(uuid, ign, displayName, hypixelRank,
                    bbTitle, ping, hasSkin, siblingCount));
            byName.put(ign.toLowerCase(), uuid);
        }

    }

    /**
     * Extract Hypixel rank from a tab-list display name.
     * Hypixel tab-list entries typically show {@code §<color>[RANK] §<color>IGN}
     * for ranked players, or just {@code §<color>IGN} for Default/unranked.
     *
     * Returns the rank string (e.g. "MVP++", "VIP") or null if no bracket rank
     * is found.
     */
    static String parseHypixelRank(String display) {
        if (display == null) return null;
        String clean = StringUtils.stripControlCodes(display);
        int open = clean.indexOf('[');
        int close = clean.indexOf(']');
        if (open < 0 || close < 0 || close <= open) return null;
        String rank = clean.substring(open + 1, close).trim().toUpperCase();
        // Normalize common variations
        if (rank.contains("++")) rank = rank.replaceAll("\\s*\\+\\+\\s*", "++");
        if (rank.length() > 20) return null; // not a real rank
        if (!HYPIXEL_RANKS.contains(rank) && !rank.equals("MVP++")) {
            // Some ranks use non-standard formatting; still accept bracket text
            // that looks like a plausible Hypixel rank.
            if (rank.length() > 0 && rank.length() <= 12) return rank;
            return null;
        }
        return rank;
    }

    /**
     * Harvest rank colors/labels from a LOBBY-phase sidebar snapshot.
     * Each line is the formatted (§-coded) text of one sidebar row. We match
     * the stripped text against known IGNs (from {@link #byName}) and, for any
     * hit, record the leading § color code immediately preceding the IGN as
     * the rank color, and any {@code [BRACKET]} text as the rank label.
     *
     * Once captured for an IGN, the value sticks for the rest of the game —
     * subsequent calls only fill IN missing entries; they do not overwrite.
     * Call {@link #clearHarvestedRanks()} on lobby (re-)entry to reset.
     */
    public void harvestSidebarRanks(java.util.List<String> rawFormattedLines) {
        if (rawFormattedLines == null || rawFormattedLines.isEmpty()) return;
        if (byName.isEmpty()) return;
        for (String raw : rawFormattedLines) {
            if (raw == null || raw.isEmpty()) continue;
            String clean = StringUtils.stripControlCodes(raw);
            if (clean == null || clean.isEmpty()) continue;

            // Find which known IGN appears in this line, if any. Match longest
            // first so the full username wins over a shorter substring of it.
            String matchedIgn = null;
            int matchedAt = -1;
            for (String lowerIgn : byName.keySet()) {
                int idx = clean.toLowerCase().indexOf(lowerIgn);
                if (idx >= 0 && (matchedIgn == null || lowerIgn.length() > matchedIgn.length())) {
                    matchedIgn = lowerIgn;
                    matchedAt = idx;
                }
            }
            if (matchedIgn == null) continue;

            // Locate the IGN inside the RAW (formatted) string. Walk through
            // raw, skipping § codes, counting visible chars to land at matchedAt.
            int rawIgnStart = -1;
            int visible = 0;
            for (int i = 0; i < raw.length(); i++) {
                if (raw.charAt(i) == '§' && i + 1 < raw.length()) { i++; continue; }
                if (visible == matchedAt) { rawIgnStart = i; break; }
                visible++;
            }
            if (rawIgnStart < 0) continue;

            // Extract the § code immediately preceding rawIgnStart (or the most
            // recent one earlier in the line). That's the rank color.
            String color = null;
            for (int i = rawIgnStart - 1; i >= 1; i--) {
                if (raw.charAt(i - 1) == '§') {
                    char c = Character.toLowerCase(raw.charAt(i));
                    if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')) {
                        color = "§" + c;
                        break;
                    }
                }
            }
            // Optional bracket label, e.g. "[VIP+]".
            int open = clean.indexOf('[');
            int close = clean.indexOf(']');
            String label = null;
            if (open >= 0 && close > open && close - open <= 12) {
                label = clean.substring(open + 1, close).trim();
                if (!label.isEmpty() && isHypixelRankLabel(label)) {
                    harvestedRankLabels.put(matchedIgn, label);
                }
            }
            if ((label == null || !isHypixelRankLabel(label))
                    && hasBuildBattleTitleBeforeIgn(clean, 0, matchedAt)) {
                continue;
            }
            putRankColor(matchedIgn, color);
        }
    }

    /**
     * Backup rank-color harvest from a lobby join chat line. This covers players
     * who enter seconds before game-start, where the tab-list snapshot may miss
     * the full lobby roster but chat still contains the formatted joined IGN.
     */
    public void harvestJoinMessageRank(String ign, String rawFormattedLine) {
        if (ign == null || ign.isEmpty() || rawFormattedLine == null
                || rawFormattedLine.isEmpty()) return;

        String lowerIgn = ign.toLowerCase(Locale.ROOT);
        harvestEmblemFromFormattedName(lowerIgn, rawFormattedLine);
        harvestBbRankFromFormattedName(lowerIgn, rawFormattedLine);
        if (hasStrongRankColor(lowerIgn)
                && harvestedRankLabels.containsKey(lowerIgn)) return;

        String clean = StringUtils.stripControlCodes(rawFormattedLine);
        if (clean == null || clean.isEmpty()) return;

        int visibleIgnStart = clean.toLowerCase(Locale.ROOT).indexOf(lowerIgn);
        if (visibleIgnStart < 0) return;

        int rawIgnStart = rawIndexForVisibleOffset(rawFormattedLine, visibleIgnStart);
        if (rawIgnStart < 0) return;

        String color = lastColorBefore(rawFormattedLine, rawIgnStart);
        putRankColor(lowerIgn, color);

        if (!harvestedRankLabels.containsKey(lowerIgn)) {
            int close = clean.lastIndexOf(']', visibleIgnStart);
            int open = close >= 0 ? clean.lastIndexOf('[', close) : -1;
            if (open >= 0 && close > open && close - open <= 12) {
                String label = clean.substring(open + 1, close).trim();
                if (!label.isEmpty() && isHypixelRankLabel(label)) {
                    harvestedRankLabels.put(lowerIgn, label);
                }
            }
        }
    }

    /**
     * Backup rank-color harvest from an in-game player chat line. GTB chat
     * keeps the speaker prefix formatted, so ranked players can still reveal
     * their Hypixel name color after the lobby/sidebar capture window.
     */
    public void harvestChatMessageRank(String ign, String rawFormattedLine) {
        if (ign == null || ign.isEmpty() || rawFormattedLine == null
                || rawFormattedLine.isEmpty()) return;

        String lowerIgn = ign.toLowerCase(Locale.ROOT);
        harvestEmblemFromFormattedName(lowerIgn, rawFormattedLine);
        harvestBbRankFromFormattedName(lowerIgn, rawFormattedLine);
        if (hasStrongRankColor(lowerIgn)
                && harvestedRankLabels.containsKey(lowerIgn)) return;

        String clean = StringUtils.stripControlCodes(rawFormattedLine);
        if (clean == null || clean.isEmpty()) return;

        int colon = clean.indexOf(": ");
        if (colon < 0) return;
        String speaker = clean.substring(0, colon);
        int visibleIgnStart = speaker.toLowerCase(Locale.ROOT).lastIndexOf(lowerIgn);
        if (visibleIgnStart < 0) return;

        String label = hypixelRankLabelBefore(speaker, 0, visibleIgnStart);
        if (label == null && hasBuildBattleTitleBeforeIgn(speaker, 0, visibleIgnStart)) {
            return;
        }

        int rawIgnStart = rawIndexForVisibleOffset(rawFormattedLine, visibleIgnStart);
        if (rawIgnStart < 0) return;

        String color = lastColorBefore(rawFormattedLine, rawIgnStart);
        putRankColor(lowerIgn, color);

        if (label != null && !harvestedRankLabels.containsKey(lowerIgn)) {
            harvestedRankLabels.put(lowerIgn, label);
        }
    }

    /**
     * Backup rank-color harvest from localized correct-guess system lines.
     * Correct-guess messages keep the guesser's formatted IGN even when normal
     * in-game tab metadata is stripped.
     */
    public void harvestCorrectGuessMessageRank(String ign, String rawFormattedLine,
                                               String correctGuessPrefix) {
        if (ign == null || ign.isEmpty() || rawFormattedLine == null
                || rawFormattedLine.isEmpty()) return;

        String lowerIgn = ign.toLowerCase(Locale.ROOT);
        harvestBbRankFromFormattedName(lowerIgn, rawFormattedLine);
        if (hasStrongRankColor(lowerIgn)
                && harvestedRankLabels.containsKey(lowerIgn)) return;

        String clean = StringUtils.stripControlCodes(rawFormattedLine);
        if (clean == null || clean.isEmpty()) return;

        int searchStart = 0;
        if (correctGuessPrefix != null && !correctGuessPrefix.isEmpty()) {
            int prefixAt = clean.indexOf(correctGuessPrefix);
            if (prefixAt < 0) return;
            searchStart = prefixAt + correctGuessPrefix.length();
        }

        int visibleIgnStart = clean.toLowerCase(Locale.ROOT).indexOf(lowerIgn, searchStart);
        if (visibleIgnStart < 0) return;

        String label = hypixelRankLabelBefore(clean, searchStart, visibleIgnStart);
        if (label == null && hasBuildBattleTitleBeforeIgn(clean, searchStart, visibleIgnStart)) {
            return;
        }

        int rawIgnStart = rawIndexForVisibleOffset(rawFormattedLine, visibleIgnStart);
        if (rawIgnStart < 0) return;

        String color = lastColorBefore(rawFormattedLine, rawIgnStart);
        putRankColor(lowerIgn, color);

        if (label != null && !harvestedRankLabels.containsKey(lowerIgn)) {
            harvestedRankLabels.put(lowerIgn, label);
        }
    }

    /**
     * Backup rank-color harvest from localized "Builder: <name>" system lines.
     * This catches in-game rank color evidence after the lobby/sidebar window.
     */
    public void harvestBuilderMessageRank(String ign, String rawFormattedLine,
                                          String builderPrefix) {
        if (ign == null || ign.isEmpty() || rawFormattedLine == null
                || rawFormattedLine.isEmpty() || builderPrefix == null) return;

        String lowerIgn = ign.toLowerCase(Locale.ROOT);
        harvestBbRankFromFormattedName(lowerIgn, rawFormattedLine);
        if (hasStrongRankColor(lowerIgn)
                && harvestedRankLabels.containsKey(lowerIgn)) return;

        String clean = StringUtils.stripControlCodes(rawFormattedLine);
        if (clean == null || clean.isEmpty()) return;

        int prefixAt = clean.indexOf(builderPrefix);
        if (prefixAt < 0) return;
        int searchStart = prefixAt + builderPrefix.length();
        int visibleIgnStart = clean.toLowerCase(Locale.ROOT).indexOf(lowerIgn, searchStart);
        if (visibleIgnStart < 0) return;

        String label = hypixelRankLabelBefore(clean, searchStart, visibleIgnStart);
        if (label == null && hasBuildBattleTitleBeforeIgn(clean, searchStart, visibleIgnStart)) {
            return;
        }

        int rawIgnStart = rawIndexForVisibleOffset(rawFormattedLine, visibleIgnStart);
        if (rawIgnStart < 0) return;

        String color = lastColorBefore(rawFormattedLine, rawIgnStart);
        putRankColor(lowerIgn, color);

        if (label != null && !harvestedRankLabels.containsKey(lowerIgn)) {
            harvestedRankLabels.put(lowerIgn, label);
        }
    }

    private static int rawIndexForVisibleOffset(String raw, int visibleOffset) {
        int visible = 0;
        for (int i = 0; i < raw.length(); i++) {
            if (raw.charAt(i) == '\u00a7' && i + 1 < raw.length()) {
                i++;
                continue;
            }
            if (visible == visibleOffset) return i;
            visible++;
        }
        return visible == visibleOffset ? raw.length() : -1;
    }

    private static String lastColorBefore(String raw, int rawIndex) {
        for (int i = rawIndex - 1; i >= 1; i--) {
            if (raw.charAt(i - 1) == '\u00a7') {
                char c = Character.toLowerCase(raw.charAt(i));
                if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')) {
                    return "\u00a7" + c;
                }
            }
        }
        return null;
    }

    public void observePacketRosterAdd(String ign) {
        observePacketRosterAdd(null, ign);
    }

    public void observePacketRosterAdd(UUID uuid, String ign) {
        if (!isValidMcUsername(ign)) return;
        if (!shouldTrackPacketRoster()) return;
        if (uuid != null) packetUuidNames.put(uuid, ign);
        packetRoster.add(ign);
    }

    public String observePacketRosterRemove(String ign) {
        return observePacketRosterRemove(null, ign);
    }

    public String observePacketRosterRemove(UUID uuid, String ign) {
        if (!shouldTrackPacketRoster()) return null;
        if (!isValidMcUsername(ign) && uuid != null) {
            ign = packetUuidNames.get(uuid);
        }
        if (!isValidMcUsername(ign)) return null;
        Iterator<String> it = packetRoster.iterator();
        while (it.hasNext()) {
            if (it.next().equalsIgnoreCase(ign)) it.remove();
        }
        Iterator<String> encodedIt = encodedGameRoster.iterator();
        while (encodedIt.hasNext()) {
            if (encodedIt.next().equalsIgnoreCase(ign)) encodedIt.remove();
        }
        packetPlayerTeams.remove(ign.toLowerCase(Locale.ROOT));
        return ign;
    }

    public List<String> packetRosterSnapshot() {
        return new ArrayList<>(packetRoster);
    }

    public List<String> encodedGameRosterSnapshot() {
        return new ArrayList<>(encodedGameRoster);
    }

    public void observePacketDisplayName(String ign, net.minecraft.util.IChatComponent comp) {
        if (!isValidMcUsername(ign) || comp == null) return;
        String formatted = comp.getFormattedText();
        if (formatted == null || formatted.isEmpty()) return;
        captureDisplayName(ign.toLowerCase(Locale.ROOT), formatted, comp);
    }

    public void observeTeamPacket(String teamName, String prefix, String suffix,
                                  Collection<String> members, int mode) {
        if (teamName == null || teamName.isEmpty()) return;

        if (mode == 1) {
            packetTeams.remove(teamName);
            Iterator<Map.Entry<String, String>> it = packetPlayerTeams.entrySet().iterator();
            while (it.hasNext()) {
                if (teamName.equals(it.next().getValue())) it.remove();
            }
            return;
        }

        if (mode == 0 || mode == 2) {
            packetTeams.put(teamName, new TeamFormat(prefix, suffix));
        }

        TeamFormat fmt = packetTeams.get(teamName);
        if (fmt == null && (prefix != null || suffix != null)) {
            fmt = new TeamFormat(prefix, suffix);
        }

        if (mode == 0 || mode == 3) {
            if (members != null) {
                for (String ign : members) {
                    observeTeamMember(teamName, fmt, ign);
                }
            }
        } else if (mode == 2) {
            for (Map.Entry<String, String> entry : packetPlayerTeams.entrySet()) {
                if (teamName.equals(entry.getValue())) {
                    harvestLobbyRankColorFromTeamName(entry.getKey(), teamName);
                    harvestTeamFormat(entry.getKey(), entry.getKey(), fmt);
                }
            }
        } else if (mode == 4 && members != null) {
            for (String ign : members) {
                if (!isValidMcUsername(ign)) continue;
                String lower = ign.toLowerCase(Locale.ROOT);
                if (teamName.equals(packetPlayerTeams.get(lower))) {
                    packetPlayerTeams.remove(lower);
                }
            }
        }
    }

    private void observeTeamMember(String teamName, TeamFormat fmt, String ign) {
        if (!isValidMcUsername(ign)) return;
        String lower = ign.toLowerCase(Locale.ROOT);
        if (shouldTrackPacketRoster()) {
            packetRoster.add(ign);
        }
        packetPlayerTeams.put(lower, teamName);
        harvestLobbyRankColorFromTeamName(lower, teamName);
        harvestFromEncodedTeamName(lower, ign, teamName);
        harvestTeamFormat(lower, ign, fmt);
    }

    private static boolean shouldTrackPacketRoster() {
        return GameStats.INSTANCE.acceptsGtbRosterPackets();
    }

    /**
     * Lobby tab teams can carry the Hypixel rank color in the team name
     * itself, e.g. "132pre&a", while prefix/suffix are empty. Capture this
     * before the GTB game-start encoded teams take over tab metadata.
     */
    private void harvestLobbyRankColorFromTeamName(String lowerIgn, String teamName) {
        if (lowerIgn == null || teamName == null || !encodedGameRoster.isEmpty()) return;
        GameStats.TrackingState state = GameStats.INSTANCE.getState();
        if (state != GameStats.TrackingState.LOBBY) return;
        String color = lobbyRankColorFromTeamName(teamName);
        if (color == null) return;
        putRankColor(lowerIgn, color);

    }

    static String lobbyRankColorFromTeamName(String teamName) {
        if (teamName == null) return null;
        int marker = teamName.indexOf("pre&");
        if (marker <= 0 || marker + 4 >= teamName.length()) return null;
        for (int i = 0; i < marker; i++) {
            if (!Character.isDigit(teamName.charAt(i))) return null;
        }
        char code = Character.toLowerCase(teamName.charAt(marker + 4));
        if ("0123456789abcdef".indexOf(code) < 0) return null;
        return "\u00a7" + code;
    }

    /**
     * Hypixel's game-start GTB tab teams encode shop metadata in the team
     * name itself: "<emblemColorIndex>_<emblem>_<bbRankIndex>", e.g.
     * "15_\u263a_9" or "3_\u03c0_15". Prefix/suffix/displayName are empty in
     * the same packet, so this is the only reliable source for BB title data.
     */
    private void harvestFromEncodedTeamName(String lowerIgn, String ign, String teamName) {
        if (lowerIgn == null || teamName == null) return;
        EncodedTeamMeta meta = parseEncodedTeamMeta(teamName);
        if (meta == null) return;
        if (isValidMcUsername(ign)) {
            addIgnoreCase(encodedGameRoster, ign);
            GameStats.INSTANCE.onEncodedGameRosterPlayer(ign);
        }

        String color = mcColorFromIndex(meta.colorIndex.intValue());
        if (!harvestedEmblems.containsKey(lowerIgn) && meta.emblem != null && !meta.emblem.isEmpty()) {
            char emblem = meta.emblem.charAt(0);
            if (isEmblemChar(emblem)) {
                harvestedEmblems.put(lowerIgn, color + emblem);

            }
        }

        if (!harvestedBbRankIndexes.containsKey(lowerIgn)) {
            String title = LangPatterns.bbTitleForRankIndex(meta.rankIndex.intValue(),
                    GameStats.INSTANCE.getCurrentLangCode());
            if (title != null && !title.isEmpty()) {
                harvestedBbRankIndexes.put(lowerIgn, meta.rankIndex);
                BuilderLeaderboardFallback.INSTANCE.observeRankIndex(ign, meta.rankIndex.intValue());

            }
        }
    }

    private static EncodedTeamMeta parseEncodedTeamMeta(String teamName) {
        if (teamName == null) return null;
        String[] parts = teamName.split("_", -1);
        if (parts.length < 3) return null;
        Integer colorIndex = parseSmallInt(parts[0]);
        if (colorIndex == null) return null;
        Integer rankIndex = null;
        for (int i = parts.length - 1; i >= 2; i--) {
            rankIndex = parseSmallInt(parts[i]);
            if (rankIndex != null) break;
        }
        if (rankIndex == null) return null;
        return new EncodedTeamMeta(colorIndex, parts[1], rankIndex);
    }

    static Integer encodedBbRankIndex(String teamName) {
        EncodedTeamMeta meta = parseEncodedTeamMeta(teamName);
        return meta == null ? null : meta.rankIndex;
    }

    static Integer encodedBbRankIndexForTest(String teamName) {
        return encodedBbRankIndex(teamName);
    }

    private static final class EncodedTeamMeta {
        final Integer colorIndex;
        final String emblem;
        final Integer rankIndex;

        EncodedTeamMeta(Integer colorIndex, String emblem, Integer rankIndex) {
            this.colorIndex = colorIndex;
            this.emblem = emblem;
            this.rankIndex = rankIndex;
        }
    }

    private static Integer parseSmallInt(String s) {
        if (s == null || s.isEmpty() || s.length() > 3) return null;
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') return null;
            n = n * 10 + (c - '0');
        }
        return n <= 31 ? Integer.valueOf(n) : null;
    }

    private static String mcColorFromIndex(int index) {
        final String codes = "0123456789abcdef";
        int clamped = Math.max(0, Math.min(index, codes.length() - 1));
        return "\u00a7" + codes.charAt(clamped);
    }

    private void harvestTeamFormat(String lowerIgn, String displayIgn, TeamFormat fmt) {
        if (fmt == null) return;
        harvestBbRankFromPacketTeamFormat(lowerIgn, displayIgn, fmt);
        harvestFromTeamPrefix(lowerIgn, fmt.prefix);
        harvestFromTeamSuffix(lowerIgn, fmt.suffix);
        String synthetic = (fmt.prefix == null ? "" : fmt.prefix)
                + (displayIgn == null ? lowerIgn : displayIgn)
                + (fmt.suffix == null ? "" : fmt.suffix);
        if (!synthetic.equals(displayIgn)) {
            putRicher(capturedDisplayNames, lowerIgn, synthetic);
        }
    }

    private void harvestBbRankFromPacketTeamFormat(String lowerIgn, String displayIgn, TeamFormat fmt) {
        if (lowerIgn == null || fmt == null) return;
        String synthetic = (fmt.prefix == null ? "" : fmt.prefix)
                + (displayIgn == null ? lowerIgn : displayIgn)
                + (fmt.suffix == null ? "" : fmt.suffix);
        harvestBbRankFromFormattedName(lowerIgn, synthetic);
        if (harvestedBbRankIndexes.containsKey(lowerIgn)) return;
        harvestBbRankTitle(lowerIgn, fmt.prefix);
        if (harvestedBbRankIndexes.containsKey(lowerIgn)) return;
        harvestBbRankTitle(lowerIgn, fmt.suffix);
    }

    /** Harvest emblem char from a scoreboard team suffix like {@code " §6⚑"}.
     *  In 1.8.9 Hypixel BB the in-game tab list uses team suffixes to attach
     *  the GTB shop emblem to each name (since {@code getDisplayName()} is
     *  null). Sticky — first non-empty capture per IGN wins. */
    private void harvestFromTeamSuffix(String lowerIgn, String suffix) {
        if (lowerIgn == null || suffix == null || suffix.isEmpty()) return;
        if (harvestedEmblems.containsKey(lowerIgn)) return;
        // Walk the suffix, tracking the most-recent § color. The first emblem
        // char we see gets stored as "<§color><char>" so it renders coloured.
        String currentColor = null;
        for (int i = 0; i < suffix.length(); i++) {
            char c = suffix.charAt(i);
            if (c == '§' && i + 1 < suffix.length()) {
                char code = Character.toLowerCase(suffix.charAt(i + 1));
                if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')) {
                    currentColor = "§" + code;
                }
                i++;
                continue;
            }
            if (isEmblemChar(c)) {
                String token = (currentColor == null ? "\u00a7f" : currentColor) + c;
                harvestedEmblems.put(lowerIgn, token);
                return;
            }
        }
    }

    /** Harvest rank color (leading § code) and bracket label from a scoreboard
     *  team prefix like {@code "§a[VIP+] §a"}. Sticky — first-capture wins so
     *  lobby data survives the in-game refresh. */
    private void harvestFromTeamPrefix(String lowerIgn, String prefix) {
        harvestFromTeamPrefix(lowerIgn, prefix, false);
    }

    private void harvestFromTeamPrefix(String lowerIgn, String prefix,
                                       boolean allowColorOnlyRankPrefix) {
        if (lowerIgn == null || prefix == null || prefix.isEmpty()) return;
        if (hasStrongRankColor(lowerIgn)
                && harvestedRankLabels.containsKey(lowerIgn)) return;

        String clean = StringUtils.stripControlCodes(prefix);
        String bracketLabel = bracketLabel(clean);
        boolean hasRankLabel = bracketLabel != null && isHypixelRankLabel(bracketLabel);
        if (!hasRankLabel && !allowColorOnlyRankPrefix) {
            return;
        }

        // The IGN's color is the LAST § code in the prefix (the one that
        // applies to the IGN that follows). For prefix "§a[VIP+] §a", we
        // want §a; for plain ranks the same § applies throughout.
        String color = null;
        for (int i = prefix.length() - 1; i >= 1; i--) {
            if (prefix.charAt(i - 1) == '§') {
                char c = Character.toLowerCase(prefix.charAt(i));
                if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')) {
                    color = "§" + c;
                    break;
                }
            }
        }
        putRankColor(lowerIgn, color);


        if (!harvestedRankLabels.containsKey(lowerIgn)) {
            if (hasRankLabel) {
                harvestedRankLabels.put(lowerIgn, bracketLabel);
            }
        }
    }

    private void harvestRankColorFromFormattedName(String lowerIgn, String formatted,
                                                   String visibleIgn,
                                                   boolean allowColorOnlyRankPrefix) {
        if (!allowColorOnlyRankPrefix || lowerIgn == null || formatted == null || formatted.isEmpty()) return;
        String clean = StringUtils.stripControlCodes(formatted);
        if (clean == null) return;
        int nameAt = clean.toLowerCase(Locale.ROOT).indexOf(visibleIgn.toLowerCase(Locale.ROOT));
        if (nameAt < 0) return;
        if (hasBuildBattleTitleBeforeIgn(clean, 0, nameAt)) return;
        int rawNameAt = rawIndexForVisibleOffset(formatted, nameAt);
        if (rawNameAt < 0) return;
        String color = lastColorBefore(formatted, rawNameAt);
        putRankColor(lowerIgn, color);

    }

    private static String bracketLabel(String clean) {
        if (clean == null) return null;
        int open = clean.indexOf('[');
        int close = clean.indexOf(']');
        if (open < 0 || close <= open || close - open > 12) return null;
        String label = clean.substring(open + 1, close).trim();
        return label.isEmpty() ? null : label;
    }

    private static String hypixelRankLabelBefore(String clean, int start, int before) {
        if (clean == null || before <= start || start < 0 || before > clean.length()) return null;
        int close = clean.lastIndexOf(']', before);
        if (close < start) return null;
        int open = clean.lastIndexOf('[', close);
        if (open < start || close <= open || close - open > 12) return null;
        String label = clean.substring(open + 1, close).trim();
        return isHypixelRankLabel(label) ? label : null;
    }

    private static boolean hasBuildBattleTitleBeforeIgn(String clean, int start, int ignStart) {
        if (clean == null || start < 0 || ignStart <= start || ignStart > clean.length()) return false;
        String prefix = clean.substring(start, ignStart).trim();
        if (prefix.isEmpty()) return false;
        if (hypixelRankLabelBefore(clean, start, ignStart) != null) return false;
        return LangPatterns.hasLeadingBbTitle(prefix);
    }

    private static boolean isHypixelRankLabel(String label) {
        return label != null && HYPIXEL_RANKS.contains(label.trim().toUpperCase(Locale.ROOT));
    }

    private void putRankColor(String lowerIgn, String color) {
        if (lowerIgn == null || color == null) return;
        String key = lowerIgn.toLowerCase(Locale.ROOT);
        String existing = harvestedRankColors.get(key);
        if (existing != null && !isWhiteColor(existing)) {

            return;
        }
        if (isWhiteColor(color)) {

            return;
        }
        harvestedRankColors.put(key, color);

    }

    private boolean hasStrongRankColor(String lowerIgn) {
        String existing = lowerIgn == null ? null
                : harvestedRankColors.get(lowerIgn.toLowerCase(Locale.ROOT));
        return existing != null && !isWhiteColor(existing);
    }

    private static boolean isWhiteColor(String color) {
        return color != null && color.length() >= 2
                && color.charAt(0) == '\u00a7'
                && Character.toLowerCase(color.charAt(1)) == 'f';
    }

    public void clearHarvestedRanks() {
        harvestedRankColors.clear();
        harvestedRankLabels.clear();
        harvestedEmblems.clear();
        harvestedBbRankIndexes.clear();
        capturedDisplayNames.clear();
        clearRosterEvidence();
    }

    public void clearRosterEvidence() {
        encodedGameRoster.clear();
        packetRoster.clear();
        packetUuidNames.clear();
        packetTeams.clear();
        packetPlayerTeams.clear();
    }

    /**
     * Walk the player-info-map and capture any non-null structured display
     * name we haven't seen yet. Hypixel's BB pushes a brief tab-list update
     * around game-start carrying [title, name, emblem] structured Components;
     * a once-per-event {@link #refresh()} can miss it. Polling every
     * scoreboard tick gives many chances to catch it.
     *
     * Sticky: never overwrites a captured display name with null.
     */
    public void pollStructuredDisplayNames() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) return;
        if (mc.thePlayer == null || mc.thePlayer.sendQueue == null) return;
        for (NetworkPlayerInfo info : mc.thePlayer.sendQueue.getPlayerInfoMap()) {
            if (info == null) continue;
            String ign = info.getGameProfile().getName();
            if (ign == null || ign.isEmpty()) continue;
            net.minecraft.util.IChatComponent comp = info.getDisplayName();
            if (comp == null) continue;

            String formatted = comp.getFormattedText();
            if (formatted == null || formatted.isEmpty()) continue;

            String key = ign.toLowerCase();

            captureDisplayName(key, formatted, comp);
        }
    }

    private void captureDisplayName(String lowerIgn, String formatted,
                                    net.minecraft.util.IChatComponent comp) {
        putRicher(capturedDisplayNames, lowerIgn, formatted);

        // Try to extract an emblem from the structured siblings (GTU path).
        // Some snapshots carry extra emblem-like siblings; the last post-name
        // emblem is the visible shop emblem.
        try {
            java.util.List<net.minecraft.util.IChatComponent> sibs = comp.getSiblings();
            if (sibs != null && sibs.size() >= 3) {
                net.minecraft.util.IChatComponent titleComp = sibs.get(0);
                if (titleComp != null) {
                    harvestBbRankTitle(lowerIgn, titleComp.getFormattedText());
                }
                if (!harvestedEmblems.containsKey(lowerIgn)) {
                    java.util.List<String> formattedParts = new java.util.ArrayList<String>();
                    for (int i = 2; i < sibs.size(); i++) {
                        net.minecraft.util.IChatComponent emblemComp = sibs.get(i);
                        if (emblemComp != null) {
                            formattedParts.add(emblemComp.getFormattedText());
                        }
                    }
                    String emblem = extractStructuredEmblemToken(formattedParts);
                    if (emblem != null) {
                        harvestedEmblems.put(lowerIgn, emblem);
                    }
                }
            }
        } catch (Throwable ignored) { /* best-effort */ }
        harvestBbRankFromFormattedName(lowerIgn, formatted);
    }

    static String extractStructuredEmblemToken(java.util.List<String> formattedParts) {
        if (formattedParts == null || formattedParts.isEmpty()) return null;
        String found = null;
        for (String formatted : formattedParts) {
            if (formatted == null) continue;
            String trimmed = formatted.trim();
            String stripped = stripFormattingCodes(trimmed).trim();
            if (stripped.length() == 1 && isEmblemChar(stripped.charAt(0))) {
                found = trimmed;
            }
        }
        return found;
    }

    private static String stripFormattingCodes(String raw) {
        if (raw == null || raw.isEmpty()) return raw;
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\u00a7' && i + 1 < raw.length()) {
                i++;
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private void harvestBbRankFromFormattedName(String lowerIgn, String formatted) {
        if (lowerIgn == null || formatted == null || formatted.isEmpty()) return;
        if (harvestedBbRankIndexes.containsKey(lowerIgn)) return;
        String clean = StringUtils.stripControlCodes(formatted);
        if (clean == null || clean.isEmpty()) return;
        int nameAt = clean.toLowerCase(Locale.ROOT).indexOf(lowerIgn.toLowerCase(Locale.ROOT));
        if (nameAt <= 0) return;
        int rawNameAt = rawIndexForVisibleOffset(formatted, nameAt);
        if (rawNameAt <= 0) return;
        harvestBbRankTitle(lowerIgn, formatted.substring(0, rawNameAt).trim());
    }

    private void harvestEmblemFromFormattedName(String lowerIgn, String formatted) {
        if (lowerIgn == null || formatted == null || formatted.isEmpty()) return;
        if (harvestedEmblems.containsKey(lowerIgn)) return;
        String clean = StringUtils.stripControlCodes(formatted);
        if (clean == null || clean.isEmpty()) return;
        int nameAt = clean.toLowerCase(Locale.ROOT).indexOf(lowerIgn.toLowerCase(Locale.ROOT));
        if (nameAt <= 0) return;
        int rawNameAt = rawIndexForVisibleOffset(formatted, nameAt);
        if (rawNameAt <= 0) return;
        for (int i = 0; i < rawNameAt; i++) {
            char c = formatted.charAt(i);
            if (c == '\u00a7' && i + 1 < rawNameAt) {
                i++;
                continue;
            }
            if (!isEmblemChar(c)) continue;
            String color = lastColorBefore(formatted, i);
            harvestedEmblems.put(lowerIgn, (color == null ? "\u00a7f" : color) + c);
            return;
        }
    }

    private void harvestBbRankTitle(String lowerIgn, String formattedTitle) {
        if (lowerIgn == null || formattedTitle == null || formattedTitle.trim().isEmpty()) return;
        if (harvestedBbRankIndexes.containsKey(lowerIgn)) return;
        String trimmed = formattedTitle.trim();
        String rawClean = StringUtils.stripControlCodes(trimmed).trim();
        String clean = normalizeBbRankTitleCandidate(rawClean);
        if (clean.isEmpty()) return;
        Integer leaderboardRankIndex = LangPatterns.bbLeaderboardRankIndex(clean);
        if (leaderboardRankIndex == null) {
            leaderboardRankIndex = LangPatterns.bbLeaderboardRankIndexFromFragment(clean);
        }
        if (leaderboardRankIndex != null) {
            harvestedBbRankIndexes.put(lowerIgn, leaderboardRankIndex);
            BuilderLeaderboardFallback.INSTANCE.observeRankIndex(lowerIgn, leaderboardRankIndex.intValue());
            return;
        }
        Integer titleIndex = LangPatterns.bbRankIndexForTitle(clean, GameStats.INSTANCE.getCurrentLangCode());
        if (titleIndex == null) return;
        harvestedBbRankIndexes.put(lowerIgn, titleIndex);

    }

    private static String normalizeBbRankTitleCandidate(String clean) {
        if (clean == null) return "";
        String out = clean.trim();
        int start = 0;
        while (start < out.length()) {
            char c = out.charAt(start);
            if (Character.isWhitespace(c) || isEmblemChar(c) || isLeadingTitleDecoration(c)) {
                start++;
                continue;
            }
            break;
        }
        if (start > 0) out = out.substring(start).trim();
        out = stripLeadingHypixelRankLabel(out);
        out = stripTrailingHypixelRankLabel(out);
        int firstSpace = out.indexOf(' ');
        if (firstSpace == 1) {
            String remainder = stripLeadingHypixelRankLabel(out.substring(firstSpace + 1).trim());
            remainder = stripTrailingHypixelRankLabel(remainder);
            if (LangPatterns.hasLeadingBbTitle(remainder)
                    || LangPatterns.bbLeaderboardRankIndex(remainder) != null
                    || LangPatterns.bbLeaderboardRankIndexFromFragment(remainder) != null) {
                out = remainder;
            }
        }
        return out;
    }

    private static boolean isLeadingTitleDecoration(char c) {
        if (c == '#' || c == '[' || c == '\u2116') return false;
        if (Character.isLetterOrDigit(c) || c == '_') return false;
        return true;
    }

    private static String stripLeadingHypixelRankLabel(String clean) {
        String out = clean == null ? "" : clean.trim();
        while (out.startsWith("[")) {
            int close = out.indexOf(']');
            if (close <= 0) break;
            String label = out.substring(1, close).trim();
            if (!isHypixelRankLabel(label)) break;
            out = out.substring(close + 1).trim();
        }
        return out;
    }

    private static String stripTrailingHypixelRankLabel(String clean) {
        String out = clean == null ? "" : clean.trim();
        while (out.endsWith("]")) {
            int open = out.lastIndexOf('[');
            int close = out.length() - 1;
            if (open < 0 || close <= open) break;
            String label = out.substring(open + 1, close).trim();
            if (!isHypixelRankLabel(label)) break;
            out = out.substring(0, open).trim();
        }
        return out;
    }

    private static void putRicher(Map<String, String> map, String key, String value) {
        if (key == null || value == null || value.isEmpty()) return;
        String existing = map.get(key);
        if (existing == null || value.length() > existing.length()) {
            map.put(key, value);
        }
    }

    private static void addIgnoreCase(Set<String> set, String value) {
        if (set == null || value == null || value.isEmpty()) return;
        for (String existing : set) {
            if (existing != null && existing.equalsIgnoreCase(value)) return;
        }
        set.add(value);
    }

    /** Hypixel nicks still use Minecraft's username shape; this only rejects
     *  sidebar fake team entries such as emoji/date row keys. */
    public static boolean isValidMcUsername(String s) {
        if (s == null) return false;
        int n = s.length();
        if (n < 1 || n > 16) return false;
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            boolean ok = (c >= 'A' && c <= 'Z')
                    || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '_';
            if (!ok) return false;
        }
        return true;
    }

    public static String ensureEmblemColor(String token) {
        if (token == null || token.isEmpty()) return token;
        return token.indexOf('\u00a7') >= 0 ? token : "\u00a7f" + token;
    }

    /** GTB shop emblem allowlist (mirrors {@code GameStats.isEmblemChar}). */
    private static boolean isEmblemChar(char c) {
        return c == '≈'  // ≈
            || c == 'α'  // α
            || c == 'Ω'  // Ω
            || c == '$'
            || c == 'π'  // π
            || c == 'ƒ'  // ƒ
            || c == '☺'  // ☺
            || c == '☀'  // ☀
            || c == '★'  // ★
            || c == '⚑'  // ⚑
            || c == '⧈'  // ⧈
            || c == '»'; // »
    }

    /** Returns the harvested emblem token (e.g. "§6⚑") for an IGN, or null. */
    public String getEmblem(String ign) {
        if (ign == null) return null;
        return ensureEmblemColor(harvestedEmblems.get(ign.toLowerCase()));
    }

    /** Returns the most-recently-captured structured display name for an IGN
     *  (debug metadata), or null. */
    public String getCapturedDisplayName(String ign) {
        if (ign == null) return null;
        return capturedDisplayNames.get(ign.toLowerCase());
    }

    public int capturedDisplayNameCount() { return capturedDisplayNames.size(); }
    public int harvestedEmblemCount() { return harvestedEmblems.size(); }
    public int harvestedBbRankCount() { return harvestedBbRankIndexes.size(); }

    public Integer getBbRankIndex(String ign) {
        if (ign == null) return null;
        Integer index = harvestedBbRankIndexes.get(ign.toLowerCase(Locale.ROOT));
        return index != null ? index : BuilderLeaderboardFallback.INSTANCE.rankIndexForIgn(ign);
    }

    /** Returns the harvested § color code for an IGN, or null. */
    public String getBbRank(String ign) {
        if (ign == null) return null;
        Integer index = getBbRankIndex(ign);
        if (index != null) {
            String title = LangPatterns.bbTitleForRankIndex(index.intValue(),
                    GameStats.INSTANCE.getCurrentLangCode());
            if (title != null && !title.isEmpty()) {
                return LangPatterns.bbColorForRankIndex(index.intValue()) + title;
            }
        }
        return null;
    }

    public String getBbRankEnglish(String ign) {
        if (ign == null) return null;
        Integer index = harvestedBbRankIndexes.get(ign.toLowerCase(Locale.ROOT));
        if (index == null) index = BuilderLeaderboardFallback.INSTANCE.rankIndexForIgn(ign);
        if (index == null) return getBbRank(ign);
        String title = LangPatterns.bbTitleForRankIndex(index.intValue(), "en");
        return title == null ? getBbRank(ign) : LangPatterns.bbColorForRankIndex(index.intValue()) + title;
    }

    public String getRankColor(String ign) {
        if (ign == null) return null;
        return harvestedRankColors.get(ign.toLowerCase());
    }

    /** Returns the harvested rank label (e.g. "VIP+"), or null. */
    public String getRankLabel(String ign) {
        if (ign == null) return null;
        return harvestedRankLabels.get(ign.toLowerCase());
    }

    public int harvestedSize() { return harvestedRankColors.size(); }

    // ── Accessors ──

    public RankInfo byUuid(UUID uuid) {
        return uuid != null ? byUuid.get(uuid) : null;
    }

    public RankInfo byIgn(String ign) {
        if (ign == null) return null;
        UUID uuid = byName.get(ign.toLowerCase());
        return uuid != null ? byUuid.get(uuid) : null;
    }

    public Collection<RankInfo> all() { return byUuid.values(); }
    public int size() { return byUuid.size(); }

    private PlayerMetaCache() {}
}
