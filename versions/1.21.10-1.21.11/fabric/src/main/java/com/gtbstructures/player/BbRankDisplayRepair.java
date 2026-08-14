package com.gtbstructures.player;

import com.gtbstructures.scoreboard.BuildBattleTitles;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Repairs truncated Build Battle titles on the live client scoreboard teams. */
public final class BbRankDisplayRepair {
    public static final BbRankDisplayRepair INSTANCE = new BbRankDisplayRepair();

    private static final char FORMAT = '\u00a7';
    private static final Integer DEFAULT_MEMBER_COLOR_INDEX = Integer.valueOf(15);
    private final Map<Object, PatchedTeam> patchedTeams = new IdentityHashMap<Object, PatchedTeam>();

    private BbRankDisplayRepair() {
    }

    public interface TeamPrefixTarget {
        Object identity();
        String teamName();
        String prefix();
        void setPrefix(String prefix);
        Integer colorIndex();
        void setColorIndex(Integer colorIndex);
    }

    public void onClientTick(boolean active) {
        if (!active) restorePatchedTeams(false);
    }

    public void repairEncodedTeamColor(TeamPrefixTarget team) {
        repairTeam(team, null, null, false, true);
    }

    public void repairExistingTeam(TeamPrefixTarget team, String ign, String langCode) {
        repairTeam(team, ign, langCode, true, true);
    }

    private void repairTeam(TeamPrefixTarget team, String ign, String langCode,
                            boolean repairPrefix, boolean repairColor) {
        if (team == null) return;
        Object identity = teamIdentity(team);
        String currentPrefix = team.prefix();
        Integer currentColorIndex = team.colorIndex();
        PatchedTeam existing = patchedTeams.get(identity);
        if (existing != null) existing.target = team;

        String sourcePrefix = currentPrefix;
        boolean prefixStillApplied = existing != null && existing.prefixPatched
                && same(currentPrefix, existing.appliedPrefix);
        if (prefixStillApplied) {
            sourcePrefix = existing.originalPrefix;
        } else if (existing != null) {
            existing.prefixPatched = false;
        }

        Integer sourceColorIndex = currentColorIndex;
        boolean colorStillApplied = existing != null && existing.colorPatched
                && same(currentColorIndex, existing.appliedColorIndex);
        if (colorStillApplied) {
            sourceColorIndex = existing.originalColorIndex;
        } else if (existing != null) {
            existing.colorPatched = false;
        }

        Integer cachedRankIndex = repairPrefix
                ? PlayerMetaCache.INSTANCE.getBbRankIndex(ign) : null;
        String repairedPrefix = repairPrefix
                ? repairObservedPrefix(team.teamName(), sourcePrefix, cachedRankIndex, langCode)
                : sourcePrefix;
        Integer repairedColorIndex = repairColor ? bbMemberColorIndex(team.teamName()) : null;
        boolean needsPrefixRepair = repairPrefix && !same(sourcePrefix, repairedPrefix);
        boolean needsColorRepair = repairColor && repairedColorIndex != null
                && !same(sourceColorIndex, repairedColorIndex);

        if (existing == null && (needsPrefixRepair || needsColorRepair)) {
            existing = new PatchedTeam(team);
            patchedTeams.put(identity, existing);
        }
        if (existing == null) return;

        if (repairPrefix && needsPrefixRepair) {
            if (!prefixStillApplied) existing.originalPrefix = sourcePrefix;
            if (!prefixStillApplied || !same(existing.desiredPrefix, repairedPrefix)) {
                team.setPrefix(repairedPrefix);
                existing.desiredPrefix = repairedPrefix;
                existing.appliedPrefix = team.prefix();
                existing.prefixPatched = true;
            }
        } else if (repairPrefix && prefixStillApplied) {
            team.setPrefix(existing.originalPrefix);
            existing.prefixPatched = false;
        }

        if (repairColor && needsColorRepair) {
            if (!colorStillApplied) existing.originalColorIndex = sourceColorIndex;
            if (!colorStillApplied || !same(existing.desiredColorIndex, repairedColorIndex)) {
                team.setColorIndex(repairedColorIndex);
                existing.desiredColorIndex = repairedColorIndex;
                existing.appliedColorIndex = team.colorIndex();
                existing.colorPatched = true;
            }
        } else if (repairColor && colorStillApplied) {
            team.setColorIndex(existing.originalColorIndex);
            existing.colorPatched = false;
        }

        if (!existing.prefixPatched && !existing.colorPatched) patchedTeams.remove(identity);
    }

    public void restoreAll() {
        restorePatchedTeams(true);
    }

    public void discardTeam(TeamPrefixTarget team) {
        if (team == null) return;
        PatchedTeam patch = patchedTeams.remove(teamIdentity(team));
        if (patch == null) return;
        patch.target = team;
        restorePrefix(patch);
        restoreColor(patch);
    }

    public void discardTeam(String teamName) {
        if (teamName == null || patchedTeams.isEmpty()) return;
        Iterator<Map.Entry<Object, PatchedTeam>> entries = patchedTeams.entrySet().iterator();
        while (entries.hasNext()) {
            PatchedTeam patch = entries.next().getValue();
            try {
                if (patch != null && patch.target != null
                        && same(teamName, patch.target.teamName())) {
                    entries.remove();
                }
            } catch (RuntimeException ignored) {
                // The removed team is already detached; retain unknown entries for restoreAll().
            }
        }
    }

    private void restorePatchedTeams(boolean restoreColors) {
        if (patchedTeams.isEmpty()) return;
        Iterator<Map.Entry<Object, PatchedTeam>> entries = patchedTeams.entrySet().iterator();
        while (entries.hasNext()) {
            PatchedTeam patch = entries.next().getValue();
            restorePrefix(patch);
            if (restoreColors) restoreColor(patch);
            if (patch == null || (!patch.prefixPatched && !patch.colorPatched)) entries.remove();
        }
    }

    private static void restorePrefix(PatchedTeam patch) {
        if (patch == null || !patch.prefixPatched) return;
        try {
            if (patch.target != null && same(patch.target.prefix(), patch.appliedPrefix)) {
                patch.target.setPrefix(patch.originalPrefix);
            }
        } catch (RuntimeException ignored) {
            // Cosmetic-only restoration. Discarded worlds and teams are harmless.
        } finally {
            patch.prefixPatched = false;
        }
    }

    private static void restoreColor(PatchedTeam patch) {
        if (patch == null || !patch.colorPatched) return;
        try {
            if (patch.target != null && same(patch.target.colorIndex(), patch.appliedColorIndex)) {
                patch.target.setColorIndex(patch.originalColorIndex);
            }
        } catch (RuntimeException ignored) {
            // Cosmetic-only restoration. Discarded worlds and teams are harmless.
        } finally {
            patch.colorPatched = false;
        }
    }

    private static Object teamIdentity(TeamPrefixTarget team) {
        Object identity = team.identity();
        return identity == null ? team : identity;
    }

    static int trackedTeamCountForTest() {
        return INSTANCE.patchedTeams.size();
    }

    static String repairPrefix(String teamName, String prefix, String langCode) {
        return repairObservedPrefix(teamName, prefix, null, langCode);
    }

    static String repairObservedPrefix(String teamName, String prefix,
                                       Integer cachedRankIndex, String langCode) {
        Integer rankIndex = encodedBbRankIndex(teamName);
        if (rankIndex == null) rankIndex = cachedRankIndex;
        if (rankIndex == null) rankIndex = rankIndexForPrefix(prefix, langCode);
        return rankIndex == null ? prefix : repairPrefixForRank(rankIndex.intValue(), prefix, langCode);
    }

    static String repairPrefixForRank(int rankIndex, String prefix, String langCode) {
        if (prefix == null || prefix.isEmpty()) return prefix;
        String rankPrefix = fullRankPrefix(rankIndex, langCode);
        if (rankPrefix == null) return prefix;

        int titleEnd = leadingTitleRawEnd(prefix, rankIndex, langCode);
        if (titleEnd < 0) return prefix;

        String remainder = trimLeadingWhitespace(prefix.substring(titleEnd));
        if (remainder.isEmpty()) return rankPrefix + " \u00a7r";
        if (containsBold(rankPrefix) && !startsWithColorOrReset(remainder)) remainder = "\u00a7r" + remainder;
        return rankPrefix + " " + remainder;
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
            if (parseSmallInt(parts[i]) != null) return DEFAULT_MEMBER_COLOR_INDEX;
        }
        return null;
    }

    private static Integer rankIndexForPrefix(String prefix, String langCode) {
        if (prefix == null || prefix.isEmpty()) return null;
        VisibleScan scan = visibleScan(prefix);
        for (int end = scan.visible.length(); end >= 1; end--) {
            String fragment = scan.visible.substring(0, end).trim();
            if (fragment.isEmpty()) continue;
            Integer rankIndex = BuildBattleTitles.bbRankIndexForTitle(fragment, langCode);
            if (rankIndex == null) rankIndex = BuildBattleTitles.bbLeaderboardRankIndex(fragment);
            if (rankIndex == null) rankIndex = BuildBattleTitles.bbLeaderboardRankIndexFromFragment(fragment);
            if (rankIndex != null) return rankIndex;
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

    private static String fullRankPrefix(int rankIndex, String langCode) {
        String title = BuildBattleTitles.bbTitleForRankIndex(rankIndex, langCode);
        if (title == null && (langCode == null || !"en".equalsIgnoreCase(langCode))) {
            title = BuildBattleTitles.bbTitleForRankIndex(rankIndex, "en");
        }
        return title == null || title.isEmpty() ? null
                : BuildBattleTitles.bbColorForRankIndex(rankIndex) + title;
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
            if (formatted.charAt(i) == FORMAT && i + 1 < formatted.length()) {
                i += 2;
            } else {
                visible.append(formatted.charAt(i));
                rawEnds.add(Integer.valueOf(i + 1));
                i++;
            }
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
        return formatted != null && formatted.toLowerCase(Locale.ROOT).contains("\u00a7l");
    }

    private static boolean startsWithColorOrReset(String text) {
        if (text == null || text.length() < 2 || text.charAt(0) != FORMAT) return false;
        char code = Character.toLowerCase(text.charAt(1));
        return code == 'r' || (code >= '0' && code <= '9') || (code >= 'a' && code <= 'f');
    }

    private static boolean same(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
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
        TeamPrefixTarget target;
        String originalPrefix;
        String desiredPrefix;
        String appliedPrefix;
        boolean prefixPatched;
        Integer originalColorIndex;
        Integer desiredColorIndex;
        Integer appliedColorIndex;
        boolean colorPatched;

        PatchedTeam(TeamPrefixTarget target) {
            this.target = target;
        }
    }
}
