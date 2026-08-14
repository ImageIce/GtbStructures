package com.gtbstructures.player;

import com.gtbstructures.GtbStructures;
import com.gtbstructures.stats.GameStats;
import com.gtbstructures.stats.LangPatterns;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class BbRankDisplayRepair {
    public static final BbRankDisplayRepair INSTANCE = new BbRankDisplayRepair();

    private static final char FORMAT = '\u00a7';

    private final Map<ScorePlayerTeam, PatchedTeam> patchedTeams =
            new IdentityHashMap<ScorePlayerTeam, PatchedTeam>();

    private BbRankDisplayRepair() {
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!repairEnabledForMaster(GtbStructures.modEnabled)
                || !GameStats.INSTANCE.shouldRepairBbRankDisplay()) {
            restoreAll();
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.theWorld == null || mc.thePlayer == null) {
            restoreAll();
            return;
        }

        Set<String> names = new LinkedHashSet<String>();
        if (mc.thePlayer.sendQueue != null) {
            Collection<NetworkPlayerInfo> infos = mc.thePlayer.sendQueue.getPlayerInfoMap();
            if (infos != null) {
                for (NetworkPlayerInfo info : infos) {
                    if (info == null || info.getGameProfile() == null) continue;
                    addName(names, info.getGameProfile().getName());
                }
            }
        }
        for (Object value : mc.theWorld.playerEntities) {
            if (!(value instanceof EntityPlayer)) continue;
            addName(names, ((EntityPlayer) value).getName());
        }

        Scoreboard scoreboard = mc.theWorld.getScoreboard();
        for (String name : names) {
            repairExistingTeam(scoreboard, name);
        }
    }

    static String repairPrefix(String teamName, String prefix, String langCode) {
        return repairObservedPrefix(teamName, prefix, null, langCode);
    }

    public static boolean repairEnabledForMaster(boolean modEnabled) {
        return modEnabled;
    }

    static String repairObservedPrefix(String teamName, String prefix,
                                       Integer cachedRankIndex, String langCode) {
        Integer rankIndex = PlayerMetaCache.encodedBbRankIndex(teamName);
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
        if (remainder.isEmpty()) {
            return rankPrefix + " \u00a7r";
        }
        if (containsBold(rankPrefix) && !startsWithColorOrReset(remainder)) {
            remainder = "\u00a7r" + remainder;
        }
        return rankPrefix + " " + remainder;
    }

    void repairExistingTeam(Scoreboard scoreboard, String ign) {
        if (scoreboard == null || ign == null || ign.isEmpty()) return;
        ScorePlayerTeam team = scoreboard.getPlayersTeam(ign);
        if (team == null) return;

        String currentPrefix = team.getColorPrefix();
        PatchedTeam existing = patchedTeams.get(team);
        String sourcePrefix = currentPrefix;
        if (existing != null) {
            if (same(currentPrefix, existing.appliedPrefix)) {
                sourcePrefix = existing.originalPrefix;
            } else {
                patchedTeams.remove(team);
                existing = null;
            }
        }

        Integer cachedRankIndex = PlayerMetaCache.INSTANCE.getBbRankIndex(ign);
        String repairedPrefix = repairObservedPrefix(team.getRegisteredName(),
                sourcePrefix, cachedRankIndex, GameStats.INSTANCE.getCurrentLangCode());
        if (same(sourcePrefix, repairedPrefix)) return;

        if (existing == null) {
            existing = new PatchedTeam(sourcePrefix, repairedPrefix);
            patchedTeams.put(team, existing);
        } else {
            existing.appliedPrefix = repairedPrefix;
        }
        if (!same(currentPrefix, repairedPrefix)) {
            team.setNamePrefix(repairedPrefix);
        }
    }

    void restoreAll() {
        if (patchedTeams.isEmpty()) return;
        for (Map.Entry<ScorePlayerTeam, PatchedTeam> entry : patchedTeams.entrySet()) {
            ScorePlayerTeam team = entry.getKey();
            PatchedTeam patch = entry.getValue();
            try {
                if (team != null && patch != null
                        && same(team.getColorPrefix(), patch.appliedPrefix)) {
                    team.setNamePrefix(patch.originalPrefix);
                }
            } catch (Throwable ignored) {
                // Cosmetic-only restoration. A discarded world/team is harmless.
            }
        }
        patchedTeams.clear();
    }

    private static Integer rankIndexForPrefix(String prefix, String langCode) {
        if (prefix == null || prefix.isEmpty()) return null;
        VisibleScan scan = visibleScan(prefix);
        for (int end = scan.visible.length(); end >= 1; end--) {
            String fragment = scan.visible.substring(0, end).trim();
            if (fragment.isEmpty()) continue;
            Integer rankIndex = LangPatterns.bbRankIndexForTitle(fragment, langCode);
            if (rankIndex != null) return rankIndex;
            rankIndex = LangPatterns.bbLeaderboardRankIndex(fragment);
            if (rankIndex != null) return rankIndex;
            rankIndex = LangPatterns.bbLeaderboardRankIndexFromFragment(fragment);
            if (rankIndex != null) return rankIndex;
        }
        return null;
    }

    private static void addName(Set<String> names, String name) {
        if (name != null && !name.isEmpty()) names.add(name);
    }

    private static boolean same(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private static String fullRankPrefix(int rankIndex, String langCode) {
        String title = LangPatterns.bbTitleForRankIndex(rankIndex, langCode);
        if (title == null && (langCode == null || !"en".equalsIgnoreCase(langCode))) {
            title = LangPatterns.bbTitleForRankIndex(rankIndex, "en");
        }
        if (title == null || title.isEmpty()) return null;
        return LangPatterns.bbColorForRankIndex(rankIndex) + title;
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
        Integer titleIndex = LangPatterns.bbRankIndexForTitle(fragment, langCode);
        if (titleIndex != null && titleIndex.intValue() == rankIndex) return true;
        titleIndex = LangPatterns.bbRankIndexForTitle(fragment, "en");
        if (titleIndex != null && titleIndex.intValue() == rankIndex) return true;
        Integer leaderboardIndex = LangPatterns.bbLeaderboardRankIndex(fragment);
        return leaderboardIndex != null && leaderboardIndex.intValue() == rankIndex;
    }

    private static boolean matchesCandidatePrefix(String fragment, List<String> candidates) {
        String lower = fragment.toLowerCase(Locale.ROOT);
        for (String candidate : candidates) {
            if (candidate == null || candidate.isEmpty()) continue;
            if (candidate.toLowerCase(Locale.ROOT).startsWith(lower)) return true;
        }
        return false;
    }

    private static List<String> rankTitleCandidates(int rankIndex, String langCode) {
        List<String> out = new ArrayList<String>();
        addCandidate(out, LangPatterns.bbTitleForRankIndex(rankIndex, langCode));
        addCandidate(out, LangPatterns.bbTitleForRankIndex(rankIndex, "en"));
        return out;
    }

    private static void addCandidate(List<String> out, String value) {
        if (value == null || value.isEmpty()) return;
        for (String existing : out) {
            if (existing.equalsIgnoreCase(value)) return;
        }
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
                    && Character.toLowerCase(formatted.charAt(i + 1)) == 'l') {
                return true;
            }
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
        final String originalPrefix;
        String appliedPrefix;

        PatchedTeam(String originalPrefix, String appliedPrefix) {
            this.originalPrefix = originalPrefix;
            this.appliedPrefix = appliedPrefix;
        }
    }
}
