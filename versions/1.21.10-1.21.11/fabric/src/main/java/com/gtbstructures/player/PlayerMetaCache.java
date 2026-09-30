package com.gtbstructures.player;

import com.gtbstructures.scoreboard.BuildBattleTitles;
import com.gtbstructures.stats.GameStats;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Platform-neutral player metadata harvested from GTB tab/team/chat evidence.
 * Loader code supplies packet and display-name observations.
 */
public final class PlayerMetaCache {
    public static final PlayerMetaCache INSTANCE = new PlayerMetaCache();

    private static final Set<String> HYPIXEL_RANKS = new HashSet<String>(Arrays.asList(
            "VIP", "VIP+", "MVP", "MVP+", "MVP++", "YOUTUBE",
            "HELPER", "MOD", "MODERATOR", "ADMIN", "ADMINISTRATOR", "OWNER",
            "BUILD TEAM", "MOJANG", "PIG", "PIG+++", "SLAYER", "ANGEL",
            "EVENTS", "GAME MASTER", "GM", "JR HELPER", "JR. HELPER"
    ));

    private final Map<String, String> harvestedRankColors = new HashMap<String, String>();
    private final Map<String, String> harvestedRankLabels = new HashMap<String, String>();
    private final Map<String, String> harvestedEmblems = new HashMap<String, String>();
    private final Map<String, Integer> harvestedBbRankIndexes = new HashMap<String, Integer>();
    private final Map<String, String> capturedDisplayNames = new HashMap<String, String>();
    private final Set<String> encodedGameRoster = new LinkedHashSet<String>();
    private final Set<String> packetRoster = new LinkedHashSet<String>();
    private final Map<String, TeamFormat> packetTeams = new HashMap<String, TeamFormat>();
    private final Map<String, String> packetPlayerTeams = new HashMap<String, String>();
    private String currentLangCode = "en";

    private PlayerMetaCache() {
    }

    public void setCurrentLangCode(String currentLangCode) {
        this.currentLangCode = currentLangCode == null || currentLangCode.trim().isEmpty()
                ? "en" : currentLangCode.trim();
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
        packetTeams.clear();
        packetPlayerTeams.clear();
    }

    public void harvestChatMessageRank(String ign, String rawFormattedLine) {
        if (ign == null || ign.isEmpty() || rawFormattedLine == null
                || rawFormattedLine.isEmpty()) return;

        String lowerIgn = ign.toLowerCase(Locale.ROOT);
        harvestEmblemFromFormattedName(lowerIgn, rawFormattedLine);
        harvestBbRankFromFormattedName(lowerIgn, rawFormattedLine);
        if (hasStrongRankColor(lowerIgn) && harvestedRankLabels.containsKey(lowerIgn)) return;

        String clean = stripControlCodes(rawFormattedLine);
        if (clean == null || clean.isEmpty()) return;

        int colon = clean.indexOf(": ");
        if (colon < 0) return;
        String speaker = clean.substring(0, colon);
        int visibleIgnStart = speaker.toLowerCase(Locale.ROOT).lastIndexOf(lowerIgn);
        if (visibleIgnStart < 0) return;

        String label = hypixelRankLabelBefore(speaker, 0, visibleIgnStart);
        boolean hasBuildBattleTitle = label == null
                && hasBuildBattleTitleBeforeIgn(speaker, 0, visibleIgnStart);

        int rawIgnStart = rawIndexForVisibleOffset(rawFormattedLine, visibleIgnStart);
        if (rawIgnStart < 0) return;

        String color = lastColorBefore(rawFormattedLine, rawIgnStart);
        putRankColor(lowerIgn, color);
        // Build Battle titles are not Hypixel donor ranks, but the IGN color
        // still carries the unranked §7 state.
        if (hasBuildBattleTitle) return;
        if (label != null && !harvestedRankLabels.containsKey(lowerIgn)) {
            harvestedRankLabels.put(lowerIgn, label);
        }
    }

    public void harvestCorrectGuessMessageRank(String ign, String rawFormattedLine,
                                               String correctGuessPrefix) {
        harvestSystemLineRank(ign, rawFormattedLine, correctGuessPrefix);
    }

    public void harvestJoinMessageRank(String ign, String rawFormattedLine) {
        harvestSystemLineRank(ign, rawFormattedLine, null);
    }

    public void harvestBuilderMessageRank(String ign, String rawFormattedLine,
                                          String builderPrefix) {
        harvestSystemLineRank(ign, rawFormattedLine, builderPrefix);
    }

    private void harvestSystemLineRank(String ign, String rawFormattedLine, String requiredPrefix) {
        if (ign == null || ign.isEmpty() || rawFormattedLine == null
                || rawFormattedLine.isEmpty()) return;

        String lowerIgn = ign.toLowerCase(Locale.ROOT);
        harvestEmblemFromFormattedName(lowerIgn, rawFormattedLine);
        harvestBbRankFromFormattedName(lowerIgn, rawFormattedLine);
        if (hasStrongRankColor(lowerIgn) && harvestedRankLabels.containsKey(lowerIgn)) return;

        String clean = stripControlCodes(rawFormattedLine);
        if (clean == null || clean.isEmpty()) return;

        int searchStart = 0;
        if (requiredPrefix != null && !requiredPrefix.isEmpty()) {
            int prefixAt = clean.indexOf(requiredPrefix);
            if (prefixAt < 0) return;
            searchStart = prefixAt + requiredPrefix.length();
        }

        int visibleIgnStart = clean.toLowerCase(Locale.ROOT).indexOf(lowerIgn, searchStart);
        if (visibleIgnStart < 0) return;

        String label = hypixelRankLabelBefore(clean, searchStart, visibleIgnStart);
        if (label == null && hasBuildBattleTitleBeforeIgn(clean, searchStart, visibleIgnStart)) return;

        int rawIgnStart = rawIndexForVisibleOffset(rawFormattedLine, visibleIgnStart);
        if (rawIgnStart < 0) return;

        String color = lastColorBefore(rawFormattedLine, rawIgnStart);
        putRankColor(lowerIgn, color);
        if (label != null && !harvestedRankLabels.containsKey(lowerIgn)) {
            harvestedRankLabels.put(lowerIgn, label);
        }
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
                for (String ign : members) observeTeamMember(teamName, fmt, ign);
            }
        } else if (mode == 2) {
            for (Map.Entry<String, String> entry : packetPlayerTeams.entrySet()) {
                if (teamName.equals(entry.getValue())) {
                    harvestLobbyRankColorFromTeamName(entry.getKey(), teamName);
                    harvestTeamFormat(entry.getKey(), entry.getKey(), fmt, true);
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

    public void observePacketRosterAdd(String ign) {
        ign = GameStats.INSTANCE.canonicalRosterIgn(ign);
        if (isValidMcUsername(ign) && GameStats.INSTANCE.acceptsLobbyRosterEvidence(ign)) {
            packetRoster.add(ign);
        }
    }

    public String observePacketRosterRemove(String ign) {
        ign = GameStats.INSTANCE.canonicalRosterIgn(ign);
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

    public void captureDisplayName(String ign, String formatted) {
        String observedIgn = ign;
        ign = GameStats.INSTANCE.canonicalRosterIgn(ign);
        if (!isValidMcUsername(ign) || formatted == null || formatted.isEmpty()) return;
        if (isValidMcUsername(observedIgn) && !observedIgn.equalsIgnoreCase(ign)) {
            // The source component belongs to the stale true-IGN alias. Keep
            // roster canonicalization, but do not relabel that presentation as
            // if the server had displayed it for the active nick.
            return;
        }
        String lowerIgn = ign.toLowerCase(Locale.ROOT);
        putRicher(capturedDisplayNames, lowerIgn, formatted);
        harvestEmblemFromFormattedName(lowerIgn, formatted);
        harvestBbRankFromFormattedName(lowerIgn, formatted);
    }

    private void observeTeamMember(String teamName, TeamFormat fmt, String ign) {
        String observedIgn = ign;
        ign = GameStats.INSTANCE.canonicalRosterIgn(ign);
        if (!isValidMcUsername(ign)) return;
        boolean remappedLocalAlias = isValidMcUsername(observedIgn)
                && !observedIgn.equalsIgnoreCase(ign);
        String lower = ign.toLowerCase(Locale.ROOT);
        if (GameStats.INSTANCE.acceptsLobbyRosterEvidence(ign)) {
            packetRoster.add(ign);
        }
        if (!remappedLocalAlias) {
            packetPlayerTeams.put(lower, teamName);
            harvestLobbyRankColorFromTeamName(lower, teamName);
        }
        harvestFromEncodedTeamName(lower, ign, teamName);
        harvestTeamFormat(lower, ign, fmt, !remappedLocalAlias);
    }

    public void transferIdentity(String oldIgn, String newIgn) {
        if (!isValidMcUsername(oldIgn) || !isValidMcUsername(newIgn)
                || oldIgn.equalsIgnoreCase(newIgn)) return;
        String oldLower = oldIgn.toLowerCase(Locale.ROOT);
        String newLower = newIgn.toLowerCase(Locale.ROOT);
        // Hypixel rank presentation belongs to the server-visible identity, not
        // the underlying account. Carrying the true IGN's rank label/color to a
        // nick can paint the nick with the account's original rank until fresh
        // nick evidence arrives. Drop the old presentation and preserve any
        // independently harvested metadata already stored for the nick.
        harvestedRankColors.remove(oldLower);
        harvestedRankLabels.remove(oldLower);
        moveStringMapValue(harvestedEmblems, oldLower, newLower);
        moveIntegerMapValue(harvestedBbRankIndexes, oldLower, newLower);
        capturedDisplayNames.remove(oldLower);
        packetPlayerTeams.remove(oldLower);
        replaceSetIdentity(encodedGameRoster, oldIgn, newIgn);
        replaceSetIdentity(packetRoster, oldIgn, newIgn);
    }

    private void harvestLobbyRankColorFromTeamName(String lowerIgn, String teamName) {
        if (lowerIgn == null || teamName == null || !encodedGameRoster.isEmpty()) return;
        if (GameStats.INSTANCE.getState() != GameStats.TrackingState.LOBBY) return;
        String color = lobbyRankColorFromTeamName(teamName);
        if (color != null) putRankColor(lowerIgn, color);
    }

    static String lobbyRankColorFromTeamName(String teamName) {
        if (teamName == null) return null;
        int marker = teamName.indexOf("pre&");
        if (marker < 0) marker = teamName.indexOf("pre\u00a7");
        if (marker <= 0 || marker + 4 >= teamName.length()) return null;
        for (int i = 0; i < marker; i++) {
            if (!Character.isDigit(teamName.charAt(i))) return null;
        }
        char code = Character.toLowerCase(teamName.charAt(marker + 4));
        if ("0123456789abcdef".indexOf(code) < 0) return null;
        return "\u00a7" + code;
    }

    private void harvestFromEncodedTeamName(String lowerIgn, String ign, String teamName) {
        if (lowerIgn == null || teamName == null) return;
        EncodedTeamMeta meta = parseEncodedTeamMeta(teamName);
        if (meta == null) return;
        if (isValidMcUsername(ign) && GameStats.INSTANCE.acceptsLobbyRosterEvidence(ign)) {
            addIgnoreCase(encodedGameRoster, ign);
            GameStats.INSTANCE.onEncodedGameRosterPlayer(ign);
        }

        String color = mcColorFromIndex(meta.colorIndex.intValue());
        if (!harvestedEmblems.containsKey(lowerIgn)
                && meta.emblem != null && !meta.emblem.isEmpty()) {
            char emblem = meta.emblem.charAt(0);
            if (isEmblemChar(emblem)) harvestedEmblems.put(lowerIgn, color + emblem);
        }

        if (!harvestedBbRankIndexes.containsKey(lowerIgn)) {
            String title = BuildBattleTitles.bbTitleForRankIndex(meta.rankIndex.intValue(), currentLangCode);
            if (title != null && !title.isEmpty()) {
                harvestedBbRankIndexes.put(lowerIgn, meta.rankIndex);
                BuilderLeaderboardFallback.INSTANCE.observeRankIndex(ign, meta.rankIndex.intValue());
            }
        }
    }

    public static Integer encodedBbRankIndexForTest(String teamName) {
        EncodedTeamMeta meta = parseEncodedTeamMeta(teamName);
        return meta == null ? null : meta.rankIndex;
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

    private static Integer parseSmallInt(String value) {
        if (value == null || value.isEmpty() || value.length() > 3) return null;
        int parsed = 0;
        for (int i = 0; i < value.length(); i++) {
            char digit = value.charAt(i);
            if (digit < '0' || digit > '9') return null;
            parsed = parsed * 10 + (digit - '0');
            if (parsed > 31) return null;
        }
        return Integer.valueOf(parsed);
    }

    private static String mcColorFromIndex(int index) {
        final String codes = "0123456789abcdef";
        int clamped = Math.max(0, Math.min(index, codes.length() - 1));
        return "\u00a7" + codes.charAt(clamped);
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

    private void harvestTeamFormat(String lowerIgn, String displayIgn, TeamFormat fmt,
                                   boolean harvestHypixelRank) {
        if (fmt == null) return;
        harvestBbRankFromPacketTeamFormat(lowerIgn, displayIgn, fmt);
        if (harvestHypixelRank) harvestFromTeamPrefix(lowerIgn, fmt.prefix);
        harvestFromTeamSuffix(lowerIgn, fmt.suffix);
        if (harvestHypixelRank) {
            String synthetic = (fmt.prefix == null ? "" : fmt.prefix)
                    + (displayIgn == null ? lowerIgn : displayIgn)
                    + (fmt.suffix == null ? "" : fmt.suffix);
            if (!synthetic.equals(displayIgn)) {
                putRicher(capturedDisplayNames, lowerIgn, synthetic);
            }
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

    private void harvestFromTeamPrefix(String lowerIgn, String prefix) {
        if (lowerIgn == null || prefix == null || prefix.isEmpty()) return;
        if (hasStrongRankColor(lowerIgn) && harvestedRankLabels.containsKey(lowerIgn)) return;

        String clean = stripControlCodes(prefix);
        String bracketLabel = bracketLabel(clean);
        boolean hasRankLabel = bracketLabel != null && isHypixelRankLabel(bracketLabel);
        if (!hasRankLabel) return;

        String color = null;
        for (int i = prefix.length() - 1; i >= 1; i--) {
            if (prefix.charAt(i - 1) == '\u00a7') {
                char c = Character.toLowerCase(prefix.charAt(i));
                if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')) {
                    color = "\u00a7" + c;
                    break;
                }
            }
        }
        putRankColor(lowerIgn, color);
        if (!harvestedRankLabels.containsKey(lowerIgn)) {
            harvestedRankLabels.put(lowerIgn, bracketLabel);
        }
    }

    private void harvestFromTeamSuffix(String lowerIgn, String suffix) {
        if (lowerIgn == null || suffix == null || suffix.isEmpty()) return;
        if (harvestedEmblems.containsKey(lowerIgn)) return;
        String currentColor = null;
        for (int i = 0; i < suffix.length(); i++) {
            char c = suffix.charAt(i);
            if (c == '\u00a7' && i + 1 < suffix.length()) {
                char code = Character.toLowerCase(suffix.charAt(i + 1));
                if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')) {
                    currentColor = "\u00a7" + code;
                }
                i++;
                continue;
            }
            if (isEmblemChar(c)) {
                harvestedEmblems.put(lowerIgn, (currentColor == null ? "\u00a7f" : currentColor) + c);
                return;
            }
        }
    }

    private void harvestBbRankFromFormattedName(String lowerIgn, String formatted) {
        if (lowerIgn == null || formatted == null || formatted.isEmpty()) return;
        if (harvestedBbRankIndexes.containsKey(lowerIgn)) return;
        String clean = stripControlCodes(formatted);
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
        String clean = stripControlCodes(formatted);
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
        String rawClean = stripControlCodes(formattedTitle.trim()).trim();
        String clean = normalizeBbRankTitleCandidate(rawClean);
        if (clean.isEmpty()) return;
        Integer leaderboardRankIndex = BuildBattleTitles.bbLeaderboardRankIndex(clean);
        if (leaderboardRankIndex == null) {
            leaderboardRankIndex = BuildBattleTitles.bbLeaderboardRankIndexFromFragment(clean);
        }
        if (leaderboardRankIndex != null) {
            harvestedBbRankIndexes.put(lowerIgn, leaderboardRankIndex);
            BuilderLeaderboardFallback.INSTANCE.observeRankIndex(lowerIgn, leaderboardRankIndex.intValue());
            return;
        }
        Integer titleIndex = BuildBattleTitles.bbRankIndexForTitle(clean, currentLangCode);
        if (titleIndex != null) harvestedBbRankIndexes.put(lowerIgn, titleIndex);
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
            if (BuildBattleTitles.hasLeadingBbTitle(remainder)
                    || BuildBattleTitles.bbLeaderboardRankIndex(remainder) != null
                    || BuildBattleTitles.bbLeaderboardRankIndexFromFragment(remainder) != null) {
                out = remainder;
            }
        }
        return out;
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
        return BuildBattleTitles.hasLeadingBbTitle(prefix)
                || BuildBattleTitles.bbLeaderboardRankIndex(prefix) != null
                || BuildBattleTitles.bbLeaderboardRankIndexFromFragment(prefix) != null;
    }

    private static boolean isHypixelRankLabel(String label) {
        return label != null && HYPIXEL_RANKS.contains(label.trim().toUpperCase(Locale.ROOT));
    }

    private void putRankColor(String lowerIgn, String color) {
        if (lowerIgn == null || color == null) return;
        String key = lowerIgn.toLowerCase(Locale.ROOT);
        String existing = harvestedRankColors.get(key);
        if (existing != null && !isWhiteColor(existing)) return;
        if (isWhiteColor(color)) return;
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

    public static String parseHypixelRank(String display) {
        if (display == null) return null;
        String clean = stripControlCodes(display);
        int open = clean.indexOf('[');
        int close = clean.indexOf(']');
        if (open < 0 || close < 0 || close <= open) return null;
        String rank = clean.substring(open + 1, close).trim().toUpperCase(Locale.ROOT);
        if (rank.contains("++")) rank = rank.replaceAll("\\s*\\+\\+\\s*", "++");
        if (rank.length() > 20) return null;
        if (!HYPIXEL_RANKS.contains(rank) && !"MVP++".equals(rank)) {
            return rank.length() > 0 && rank.length() <= 12 ? rank : null;
        }
        return rank;
    }

    private static String stripControlCodes(String raw) {
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

    private static boolean isLeadingTitleDecoration(char c) {
        if (c == '#' || c == '[' || c == '\u2116') return false;
        if (Character.isLetterOrDigit(c) || c == '_') return false;
        return true;
    }

    private static boolean isEmblemChar(char c) {
        return c == '\u2248' || c == '\u03b1' || c == '\u03a9'
                || c == '$' || c == '\u03c0' || c == '\u0192'
                || c == '\u263a' || c == '\u2600' || c == '\u2605'
                || c == '\u2691' || c == '\u29c8' || c == '\u00bb';
    }

    public static boolean isValidMcUsername(String value) {
        if (value == null) return false;
        int length = value.length();
        if (length < 1 || length > 16) return false;
        for (int i = 0; i < length; i++) {
            char c = value.charAt(i);
            boolean valid = (c >= 'A' && c <= 'Z')
                    || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '_';
            if (!valid) return false;
        }
        return true;
    }

    public static String ensureEmblemColor(String token) {
        if (token == null || token.isEmpty()) return token;
        return token.indexOf('\u00a7') >= 0 ? token : "\u00a7f" + token;
    }

    private static void putRicher(Map<String, String> map, String key, String value) {
        if (key == null || value == null || value.isEmpty()) return;
        String existing = map.get(key);
        if (existing == null || value.length() > existing.length()) map.put(key, value);
    }

    private static void moveStringMapValue(Map<String, String> map,
                                           String oldKey, String newKey) {
        String value = map.remove(oldKey);
        if (value != null && !map.containsKey(newKey)) map.put(newKey, value);
    }

    private static void moveIntegerMapValue(Map<String, Integer> map,
                                            String oldKey, String newKey) {
        Integer value = map.remove(oldKey);
        if (value != null && !map.containsKey(newKey)) map.put(newKey, value);
    }

    private static void replaceSetIdentity(Set<String> values, String oldIgn, String newIgn) {
        boolean removed = false;
        Iterator<String> iterator = values.iterator();
        while (iterator.hasNext()) {
            String existing = iterator.next();
            if (existing != null && existing.equalsIgnoreCase(oldIgn)) {
                iterator.remove();
                removed = true;
            }
        }
        if (removed) addIgnoreCase(values, newIgn);
    }

    private static void addIgnoreCase(Set<String> set, String value) {
        if (set == null || value == null || value.isEmpty()) return;
        for (String existing : set) {
            if (existing != null && existing.equalsIgnoreCase(value)) return;
        }
        set.add(value);
    }

    public Integer getBbRankIndex(String ign) {
        if (ign == null) return null;
        Integer index = harvestedBbRankIndexes.get(ign.toLowerCase(Locale.ROOT));
        return index != null ? index : BuilderLeaderboardFallback.INSTANCE.rankIndexForIgn(ign);
    }

    public String getBbRank(String ign) {
        if (ign == null) return null;
        Integer index = getBbRankIndex(ign);
        if (index == null) return null;
        String title = BuildBattleTitles.bbTitleForRankIndex(index.intValue(), currentLangCode);
        return title == null ? null : BuildBattleTitles.bbColorForRankIndex(index.intValue()) + title;
    }

    public String getBbRankEnglish(String ign) {
        if (ign == null) return null;
        Integer index = harvestedBbRankIndexes.get(ign.toLowerCase(Locale.ROOT));
        if (index == null) index = BuilderLeaderboardFallback.INSTANCE.rankIndexForIgn(ign);
        if (index == null) return getBbRank(ign);
        String title = BuildBattleTitles.bbTitleForRankIndex(index.intValue(), "en");
        return title == null ? getBbRank(ign)
                : BuildBattleTitles.bbColorForRankIndex(index.intValue()) + title;
    }

    public String getRankColor(String ign) {
        return ign == null ? null : harvestedRankColors.get(ign.toLowerCase(Locale.ROOT));
    }

    public String getRankLabel(String ign) {
        return ign == null ? null : harvestedRankLabels.get(ign.toLowerCase(Locale.ROOT));
    }

    public String getEmblem(String ign) {
        return ign == null ? null : ensureEmblemColor(harvestedEmblems.get(ign.toLowerCase(Locale.ROOT)));
    }

    public String getCapturedDisplayName(String ign) {
        return ign == null ? null : capturedDisplayNames.get(ign.toLowerCase(Locale.ROOT));
    }

    public List<String> packetRosterSnapshot() {
        return new ArrayList<String>(packetRoster);
    }

    public List<String> encodedGameRosterSnapshot() {
        return new ArrayList<String>(encodedGameRoster);
    }

    public int harvestedBbRankCount() {
        return harvestedBbRankIndexes.size();
    }

    public int harvestedSize() {
        return harvestedRankColors.size();
    }

    private static final class TeamFormat {
        final String prefix;
        final String suffix;

        TeamFormat(String prefix, String suffix) {
            this.prefix = prefix;
            this.suffix = suffix;
        }
    }
}
