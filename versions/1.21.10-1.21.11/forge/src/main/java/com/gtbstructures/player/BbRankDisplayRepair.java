package com.gtbstructures.player;

import com.gtbstructures.scoreboard.BuildBattleTitles;
import com.gtbstructures.stats.GameStats;
import com.gtbstructures.util.MinecraftFormatting;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/** Repairs Build Battle titles truncated in the live scoreboard team prefixes. */
public final class BbRankDisplayRepair {
    public static final BbRankDisplayRepair INSTANCE = new BbRankDisplayRepair();

    private static final char FORMAT = '\u00a7';
    private static final String TEAM_COLOR_CODES = "0123456789abcdef";
    private static final String[] TEAM_COLOR_NAMES = new String[] {
            "BLACK", "DARK_BLUE", "DARK_GREEN", "DARK_AQUA",
            "DARK_RED", "DARK_PURPLE", "GOLD", "GRAY",
            "DARK_GRAY", "BLUE", "GREEN", "AQUA",
            "RED", "LIGHT_PURPLE", "YELLOW", "WHITE"
    };
    private static volatile Method cachedTeamColorSetter;
    private static volatile Class<?> cachedTeamColorOwner;
    private static volatile Object[] modernTeamColors;

    private final Map<PlayerTeam, PatchedTeam> patchedTeams =
            new IdentityHashMap<PlayerTeam, PatchedTeam>();

    private BbRankDisplayRepair() {
    }

    public void onPlayerListPoll(Minecraft mc, Collection<String> listedPlayerNames) {
        if (!GameStats.INSTANCE.shouldRepairBbRankDisplay()) {
            restoreAll();
            return;
        }
        if (mc == null || mc.level == null || mc.player == null) {
            restoreAll();
            return;
        }

        Set<String> names = new LinkedHashSet<String>();
        if (listedPlayerNames != null) {
            for (String name : listedPlayerNames) addName(names, name);
        }
        for (Player player : mc.level.players()) {
            if (player != null && player.getGameProfile() != null) {
                addName(names, player.getGameProfile().name());
            }
        }

        Scoreboard scoreboard = mc.level.getScoreboard();
        for (String name : names) repairExistingTeam(scoreboard, name);
    }

    public void reset() {
        restoreAll();
    }

    /** Repairs only the encoded team member color, before roster data is available. */
    public void repairEncodedTeamColor(PlayerTeam team) {
        if (team == null) return;
        Object desiredColor = bbMemberColor(team, team.getName());
        if (desiredColor == null) return;

        Component currentComponent = team.getPlayerPrefix();
        String currentPrefix = MinecraftFormatting.legacyFormattedText(currentComponent);
        Object currentColor = team.getColor();
        PatchedTeam existing = patchedTeams.get(team);
        if (existing == null) {
            if (same(currentColor, desiredColor)) return;
            existing = new PatchedTeam(currentComponent, currentPrefix, currentColor);
            existing.desiredPrefix = currentPrefix;
            existing.appliedPrefix = currentPrefix;
            patchedTeams.put(team, existing);
        } else if (existing.appliedColor != null
                && !same(currentColor, existing.appliedColor)) {
            existing.originalColor = currentColor;
        }
        if (!same(currentColor, desiredColor)) setTeamColor(team, desiredColor);
        existing.appliedColor = desiredColor;
    }

    /** Drops detached packet-removed teams without mutating their stale objects. */
    public void discardTeam(String teamName) {
        if (teamName == null || patchedTeams.isEmpty()) return;
        Iterator<Map.Entry<PlayerTeam, PatchedTeam>> entries = patchedTeams.entrySet().iterator();
        while (entries.hasNext()) {
            PlayerTeam team = entries.next().getKey();
            try {
                if (team != null && same(teamName, team.getName())) entries.remove();
            } catch (RuntimeException ignored) {
                // A detached team is already outside the live scoreboard lifecycle.
            }
        }
    }

    static String repairPrefix(String teamName, String prefix, String langCode) {
        return repairObservedPrefix(teamName, prefix, null, langCode);
    }

    static String repairObservedPrefix(String teamName, String prefix,
                                       Integer cachedRankIndex, String langCode) {
        Integer rankIndex = encodedBbRankIndex(teamName);
        if (rankIndex == null) rankIndex = cachedRankIndex;
        if (rankIndex == null) rankIndex = rankIndexForPrefix(prefix, langCode);
        if (rankIndex == null) return prefix;
        return repairPrefixForRank(rankIndex.intValue(), prefix, langCode);
    }

    static String repairPrefixForRank(int rankIndex, String prefix, String langCode) {
        if (prefix == null || prefix.isEmpty()) return prefix;
        String rankPrefix = fullRankPrefix(rankIndex, langCode);
        if (rankPrefix == null) return prefix;

        int titleEnd = leadingTitleRawEnd(prefix, rankIndex, langCode);
        if (titleEnd < 0) return prefix;

        String remainder = trimLeadingWhitespace(prefix.substring(titleEnd));
        if (remainder.isEmpty()) return rankPrefix + " \u00a7r";
        if (containsBold(rankPrefix) && !startsWithColorOrReset(remainder)) {
            remainder = "\u00a7r" + remainder;
        }
        return rankPrefix + " " + remainder;
    }

    void repairExistingTeam(Scoreboard scoreboard, String ign) {
        if (scoreboard == null || ign == null || ign.isEmpty()) return;
        PlayerTeam team = scoreboard.getPlayersTeam(ign);
        if (team == null) return;

        Component currentComponent = team.getPlayerPrefix();
        String currentPrefix = MinecraftFormatting.legacyFormattedText(currentComponent);
        PatchedTeam existing = patchedTeams.get(team);
        String sourcePrefix = currentPrefix;
        boolean patchStillApplied = false;
        if (existing != null) {
            sourcePrefix = latestServerPrefix(currentPrefix, existing.originalPrefix, existing.appliedPrefix);
            if (same(currentPrefix, existing.appliedPrefix)) {
                patchStillApplied = true;
                if (existing.appliedColor != null
                        && !same(team.getColor(), existing.appliedColor)) {
                    existing.originalColor = team.getColor();
                }
            } else {
                restoreAppliedColor(team, existing);
                patchedTeams.remove(team);
                existing = null;
            }
        }

        Integer cachedRankIndex = PlayerMetaCache.INSTANCE.getBbRankIndex(ign);
        String repairedPrefix = repairObservedPrefix(team.getName(), sourcePrefix,
                cachedRankIndex, GameStats.INSTANCE.getCurrentLangCode());
        Object desiredColor = bbMemberColor(team, team.getName());
        boolean prefixNeedsRepair = !same(sourcePrefix, repairedPrefix);
        boolean colorNeedsRepair = desiredColor != null
                && !same(team.getColor(), desiredColor);
        if (existing == null && !prefixNeedsRepair && !colorNeedsRepair) return;

        if (existing == null) {
            existing = new PatchedTeam(currentComponent, sourcePrefix, team.getColor());
            patchedTeams.put(team, existing);
        }
        if (prefixNeedsRepair
                && (!patchStillApplied || !same(existing.desiredPrefix, repairedPrefix))) {
            team.setPlayerPrefix(legacyComponent(repairedPrefix));
            existing.desiredPrefix = repairedPrefix;
            existing.appliedPrefix = MinecraftFormatting.legacyFormattedText(team.getPlayerPrefix());
        } else if (!patchStillApplied) {
            existing.desiredPrefix = sourcePrefix;
            existing.appliedPrefix = currentPrefix;
        }
        if (desiredColor != null) {
            if (!same(team.getColor(), desiredColor)) setTeamColor(team, desiredColor);
            existing.appliedColor = desiredColor;
        }
    }

    public void restoreAll() {
        if (patchedTeams.isEmpty()) return;
        for (Map.Entry<PlayerTeam, PatchedTeam> entry : patchedTeams.entrySet()) {
            PlayerTeam team = entry.getKey();
            PatchedTeam patch = entry.getValue();
            try {
                String current = team == null ? null
                        : MinecraftFormatting.legacyFormattedText(team.getPlayerPrefix());
                if (team != null && patch != null) {
                    if (same(current, patch.appliedPrefix)) {
                        team.setPlayerPrefix(patch.originalComponent);
                    }
                    restoreAppliedColor(team, patch);
                }
            } catch (RuntimeException ignored) {
                // Cosmetic-only restoration; discarded worlds and teams are harmless.
            }
        }
        patchedTeams.clear();
    }

    static int trackedTeamCountForTest() {
        return INSTANCE.patchedTeams.size();
    }

    private static void restoreAppliedColor(PlayerTeam team, PatchedTeam patch) {
        if (team != null && patch != null && patch.appliedColor != null
                && same(team.getColor(), patch.appliedColor)) {
            setTeamColor(team, patch.originalColor);
        }
    }

    static String latestServerPrefix(String currentPrefix, String originalPrefix, String appliedPrefix) {
        return same(currentPrefix, appliedPrefix) ? originalPrefix : currentPrefix;
    }

    static Component legacyComponent(String legacy) {
        MutableComponent root = Component.empty();
        List<ChatFormatting> formats = new ArrayList<ChatFormatting>();
        int segmentStart = 0;
        for (int i = 0; i < legacy.length(); i++) {
            if (legacy.charAt(i) != FORMAT || i + 1 >= legacy.length()) continue;
            appendLegacySegment(root, legacy.substring(segmentStart, i), formats);
            applyLegacyFormatCode(formats, legacy.charAt(i + 1));
            segmentStart = i + 2;
            i++;
        }
        appendLegacySegment(root, legacy.substring(segmentStart), formats);
        return root;
    }

    private static void appendLegacySegment(MutableComponent root, String value,
                                            List<ChatFormatting> formats) {
        if (value.isEmpty()) return;
        root.append(Component.literal(value).withStyle(
                formats.toArray(new ChatFormatting[formats.size()])));
    }

    private static void applyLegacyFormatCode(List<ChatFormatting> formats, char rawCode) {
        char code = Character.toLowerCase(rawCode);
        if (code == 'r' || (code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')) {
            formats.clear();
        }
        ChatFormatting formatting = legacyFormatting(code);
        if (formatting != null && !formats.contains(formatting)) formats.add(formatting);
    }

    private static ChatFormatting legacyFormatting(char code) {
        switch (code) {
            case '0': return ChatFormatting.BLACK;
            case '1': return ChatFormatting.DARK_BLUE;
            case '2': return ChatFormatting.DARK_GREEN;
            case '3': return ChatFormatting.DARK_AQUA;
            case '4': return ChatFormatting.DARK_RED;
            case '5': return ChatFormatting.DARK_PURPLE;
            case '6': return ChatFormatting.GOLD;
            case '7': return ChatFormatting.GRAY;
            case '8': return ChatFormatting.DARK_GRAY;
            case '9': return ChatFormatting.BLUE;
            case 'a': return ChatFormatting.GREEN;
            case 'b': return ChatFormatting.AQUA;
            case 'c': return ChatFormatting.RED;
            case 'd': return ChatFormatting.LIGHT_PURPLE;
            case 'e': return ChatFormatting.YELLOW;
            case 'f': return ChatFormatting.WHITE;
            case 'k': return ChatFormatting.OBFUSCATED;
            case 'l': return ChatFormatting.BOLD;
            case 'm': return ChatFormatting.STRIKETHROUGH;
            case 'n': return ChatFormatting.UNDERLINE;
            case 'o': return ChatFormatting.ITALIC;
            default: return null;
        }
    }

    private static Integer rankIndexForPrefix(String prefix, String langCode) {
        if (prefix == null || prefix.isEmpty()) return null;
        VisibleScan scan = visibleScan(prefix);
        for (int end = scan.visible.length(); end >= 1; end--) {
            String fragment = scan.visible.substring(0, end).trim();
            if (fragment.isEmpty()) continue;
            Integer rankIndex = BuildBattleTitles.bbRankIndexForTitle(fragment, langCode);
            if (rankIndex != null) return rankIndex;
            rankIndex = BuildBattleTitles.bbLeaderboardRankIndex(fragment);
            if (rankIndex != null) return rankIndex;
            rankIndex = BuildBattleTitles.bbLeaderboardRankIndexFromFragment(fragment);
            if (rankIndex != null) return rankIndex;
        }
        return null;
    }

    static Integer encodedBbRankIndex(String teamName) {
        if (teamName == null) return null;
        String[] parts = teamName.split("_", -1);
        if (parts.length < 3 || parseSmallInt(parts[0]) == null) return null;
        for (int i = parts.length - 1; i >= 2; i--) {
            Integer rankIndex = parseSmallInt(parts[i]);
            if (rankIndex != null) return rankIndex;
        }
        return null;
    }

    static Integer bbMemberColorIndex(String teamName) {
        if (teamName == null) return null;
        String[] parts = teamName.split("_", -1);
        if (parts.length < 3) return null;
        Integer emblemColorIndex = parseSmallInt(parts[0]);
        if (emblemColorIndex == null || emblemColorIndex.intValue() > 15) return null;
        for (int i = parts.length - 1; i >= 2; i--) {
            if (parseSmallInt(parts[i]) != null) return Integer.valueOf(15);
        }
        return null;
    }

    private static Object bbMemberColor(PlayerTeam team, String teamName) {
        Integer colorIndex = bbMemberColorIndex(teamName);
        if (team == null || colorIndex == null) return null;
        return teamColorValue(team.getColor(), colorIndex.intValue());
    }

    private static Object teamColorValue(Object currentColor, int colorIndex) {
        if (colorIndex < 0 || colorIndex >= TEAM_COLOR_CODES.length()) return null;
        if (currentColor instanceof Optional<?>) {
            return Optional.of(modernTeamColor(colorIndex));
        }
        return legacyFormatting(TEAM_COLOR_CODES.charAt(colorIndex));
    }

    private static Object modernTeamColor(int colorIndex) {
        Object[] colors = modernTeamColors;
        if (colors == null) {
            synchronized (BbRankDisplayRepair.class) {
                colors = modernTeamColors;
                if (colors == null) {
                    try {
                        Class<?> colorClass = Class.forName("net.minecraft.world.scores.TeamColor",
                                false, PlayerTeam.class.getClassLoader());
                        colors = colorClass.getEnumConstants();
                    } catch (ClassNotFoundException e) {
                        throw new IllegalStateException("Unable to resolve modern scoreboard team colors", e);
                    }
                    if (colors == null || colors.length < TEAM_COLOR_NAMES.length) {
                        throw new IllegalStateException("Incomplete modern scoreboard team color table");
                    }
                    modernTeamColors = colors;
                }
            }
        }
        return colors[colorIndex];
    }

    private static void setTeamColor(PlayerTeam team, Object color) {
        try {
            teamColorSetter(team.getClass()).invoke(team, color);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to update scoreboard team color", e);
        }
    }

    private static Method teamColorSetter(Class<?> teamClass) {
        Method setter = cachedTeamColorSetter;
        if (setter != null && cachedTeamColorOwner == teamClass) return setter;
        synchronized (BbRankDisplayRepair.class) {
            setter = cachedTeamColorSetter;
            if (setter != null && cachedTeamColorOwner == teamClass) return setter;
            for (Method method : teamClass.getMethods()) {
                if ("setColor".equals(method.getName()) && method.getParameterCount() == 1) {
                    cachedTeamColorOwner = teamClass;
                    cachedTeamColorSetter = method;
                    return method;
                }
            }
        }
        throw new IllegalStateException("Unable to find scoreboard team color setter");
    }

    static void setTeamColorForTest(PlayerTeam team, int colorIndex) {
        setTeamColor(team, teamColorValue(team.getColor(), colorIndex));
    }

    static Integer teamColorIndexForTest(PlayerTeam team) {
        if (team == null) return null;
        Object color = team.getColor();
        if (color instanceof Optional<?>) color = ((Optional<?>) color).orElse(null);
        if (color == null) return null;
        if (color instanceof ChatFormatting) {
            for (int i = 0; i < TEAM_COLOR_CODES.length(); i++) {
                if (color == legacyFormatting(TEAM_COLOR_CODES.charAt(i))) return Integer.valueOf(i);
            }
            return null;
        }
        if (color instanceof Enum<?>) {
            String name = ((Enum<?>) color).name();
            for (int i = 0; i < TEAM_COLOR_NAMES.length; i++) {
                if (TEAM_COLOR_NAMES[i].equals(name)) return Integer.valueOf(i);
            }
        }
        return null;
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

    private static void addName(Set<String> names, String name) {
        if (name != null && !name.isEmpty()) names.add(name);
    }

    private static boolean same(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
    }

    private static String fullRankPrefix(int rankIndex, String langCode) {
        String title = BuildBattleTitles.bbTitleForRankIndex(rankIndex, langCode);
        if (title == null && (langCode == null || !"en".equalsIgnoreCase(langCode))) {
            title = BuildBattleTitles.bbTitleForRankIndex(rankIndex, "en");
        }
        if (title == null || title.isEmpty()) return null;
        return BuildBattleTitles.bbColorForRankIndex(rankIndex) + title;
    }

    private static int leadingTitleRawEnd(String formatted, int rankIndex, String langCode) {
        VisibleScan scan = visibleScan(formatted);
        if (scan.visible.isEmpty()) return -1;
        List<String> candidates = rankTitleCandidates(rankIndex, langCode);
        for (int end = scan.visible.length(); end >= 1; end--) {
            String fragment = scan.visible.substring(0, end).trim();
            if (fragment.isEmpty()) continue;
            if (matchesRankIndex(fragment, rankIndex, langCode)
                    || matchesCandidatePrefix(fragment, candidates)) {
                return scan.rawEnds.get(end - 1).intValue();
            }
        }
        return -1;
    }

    private static boolean matchesRankIndex(String fragment, int rankIndex, String langCode) {
        Integer titleIndex = BuildBattleTitles.bbRankIndexForTitle(fragment, langCode);
        if (titleIndex != null && titleIndex.intValue() == rankIndex) return true;
        titleIndex = BuildBattleTitles.bbRankIndexForTitle(fragment, "en");
        if (titleIndex != null && titleIndex.intValue() == rankIndex) return true;
        Integer leaderboardIndex = BuildBattleTitles.bbLeaderboardRankIndex(fragment);
        return leaderboardIndex != null && leaderboardIndex.intValue() == rankIndex;
    }

    private static boolean matchesCandidatePrefix(String fragment, List<String> candidates) {
        String lower = fragment.toLowerCase(Locale.ROOT);
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isEmpty()
                    && candidate.toLowerCase(Locale.ROOT).startsWith(lower)) return true;
        }
        return false;
    }

    private static List<String> rankTitleCandidates(int rankIndex, String langCode) {
        List<String> out = new ArrayList<String>();
        addCandidate(out, BuildBattleTitles.bbTitleForRankIndex(rankIndex, langCode));
        addCandidate(out, BuildBattleTitles.bbTitleForRankIndex(rankIndex, "en"));
        return out;
    }

    private static void addCandidate(List<String> out, String value) {
        if (value == null || value.isEmpty()) return;
        for (String existing : out) if (existing.equalsIgnoreCase(value)) return;
        out.add(value);
    }

    private static VisibleScan visibleScan(String formatted) {
        StringBuilder visible = new StringBuilder();
        List<Integer> rawEnds = new ArrayList<Integer>();
        for (int i = 0; i < formatted.length();) {
            char c = formatted.charAt(i);
            if (c == FORMAT && i + 1 < formatted.length()) {
                i += 2;
                continue;
            }
            visible.append(c);
            rawEnds.add(Integer.valueOf(i + 1));
            i++;
        }
        return new VisibleScan(visible.toString(), rawEnds);
    }

    private static String trimLeadingWhitespace(String text) {
        if (text == null || text.isEmpty()) return "";
        int i = 0;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) i++;
        return i == 0 ? text : text.substring(i);
    }

    private static boolean containsBold(String formatted) {
        if (formatted == null) return false;
        for (int i = 0; i + 1 < formatted.length(); i++) {
            if (formatted.charAt(i) == FORMAT
                    && Character.toLowerCase(formatted.charAt(i + 1)) == 'l') return true;
        }
        return false;
    }

    private static boolean startsWithColorOrReset(String text) {
        if (text == null || text.length() < 2 || text.charAt(0) != FORMAT) return false;
        char code = Character.toLowerCase(text.charAt(1));
        return code == 'r' || (code >= '0' && code <= '9') || (code >= 'a' && code <= 'f');
    }

    private static final class VisibleScan {
        final String visible;
        final List<Integer> rawEnds;

        VisibleScan(String visible, List<Integer> rawEnds) {
            this.visible = visible;
            this.rawEnds = rawEnds;
        }
    }

    private static final class PatchedTeam {
        final Component originalComponent;
        final String originalPrefix;
        Object originalColor;
        String desiredPrefix;
        String appliedPrefix;
        Object appliedColor;

        PatchedTeam(Component originalComponent, String originalPrefix,
                    Object originalColor) {
            this.originalComponent = originalComponent;
            this.originalPrefix = originalPrefix;
            this.originalColor = originalColor;
        }
    }
}
