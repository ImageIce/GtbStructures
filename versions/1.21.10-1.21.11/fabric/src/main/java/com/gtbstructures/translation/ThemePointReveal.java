package com.gtbstructures.translation;

import com.gtbstructures.scoreboard.PointEntry;
import com.gtbstructures.scoreboard.ThemePointAward;
import com.gtbstructures.stats.ScoreDelta;

import java.util.Collection;
import java.util.Collections;

/** Platform-neutral exact theme-point notice resolver. */
public final class ThemePointReveal {
    public static final String PREFIX = "\u00a76[\u00a7eGtbStructures\u00a76] \u00a77";

    public static final class RoundState {
        public String langCode;
        public String hintStructure;
        public String[] builderThemeOptions;
        public boolean selfBuildRound;
        public boolean completeThemeRevealedToUser;
        public boolean themePointRevealSent;
    }

    private ThemePointReveal() {
    }

    public static void markCompleteThemeRevealed(RoundState round) {
        if (round != null) round.completeThemeRevealedToUser = true;
    }

    public static String maybeBuildFromHint(RoundState round,
                                            boolean enabled,
                                            boolean showAfterGuess) {
        if (round == null) return null;
        return buildIfAllowed(round, pointFromHint(round.langCode, round.hintStructure),
                enabled, showAfterGuess);
    }

    public static String maybeBuildFromTheme(RoundState round,
                                             String displayedTheme,
                                             String liveLangCode,
                                             Collection<String> ruledOutEnglishThemes,
                                             ScoreDelta scoreboardDelta,
                                             boolean enabled,
                                             boolean showAfterGuess) {
        if (round == null) return null;
        Integer point = pointFromTheme(displayedTheme,
                liveLangCode == null ? round.langCode : liveLangCode,
                round.builderThemeOptions, ruledOutEnglishThemes, scoreboardDelta);
        return buildIfAllowed(round, point, enabled, showAfterGuess);
    }

    public static String maybeBuildFromScoreDelta(RoundState round,
                                                   ScoreDelta scoreboardDelta,
                                                   boolean enabled,
                                                   boolean showAfterGuess) {
        return buildIfAllowed(round, pointFromScoreboardDelta(scoreboardDelta),
                enabled, showAfterGuess);
    }

    static Integer pointFromHint(String langCode, String structure) {
        if (!"en".equals(TranslationData.normalizeLangCode(langCode))) return null;
        return exactPoint(ThemePointAward.computeEnglishHintStructure(structure));
    }

    static Integer pointFromTheme(String displayedTheme,
                                  String langCode,
                                  String[] builderThemeOptions,
                                  Collection<String> ruledOutEnglishThemes,
                                  ScoreDelta scoreboardDelta) {
        if (displayedTheme == null || displayedTheme.trim().isEmpty()) return null;
        String cleanTheme = displayedTheme.trim();
        if (builderThemeOptions != null) {
            for (int i = 0; i < Math.min(3, builderThemeOptions.length); i++) {
                String option = builderThemeOptions[i];
                if (option != null && option.trim().equalsIgnoreCase(cleanTheme)) {
                    return Integer.valueOf(i + 1);
                }
            }
        }

        PointEntry inferred = ThemePointAward.compute(cleanTheme, langCode,
                ruledOutEnglishThemes == null
                        ? Collections.<String>emptySet() : ruledOutEnglishThemes);
        Integer exact = exactPoint(inferred);
        if (exact != null) return exact;
        Integer reconciledPoint = pointFromScoreboardDelta(scoreboardDelta);
        if (inferred != null && inferred.kind == PointEntry.Kind.FORK
                && inferred.fork != null && reconciledPoint != null
                && inferred.fork.contains(reconciledPoint)) {
            return reconciledPoint;
        }
        return null;
    }

    static String buildMessageForTest(int point) {
        String color = point == 1 ? "\u00a7a" : point == 2 ? "\u00a7e" : "\u00a7c";
        return PREFIX + "Point value of the current theme: " + color + point;
    }

    private static String buildIfAllowed(RoundState round,
                                         Integer point,
                                         boolean enabled,
                                         boolean showAfterGuess) {
        if (!enabled || round == null || round.themePointRevealSent || point == null) return null;
        if (round.selfBuildRound && !showAfterGuess) return null;
        if (round.completeThemeRevealedToUser && !showAfterGuess) return null;
        if (!isThemePoint(point.intValue())) return null;
        round.themePointRevealSent = true;
        return buildMessageForTest(point.intValue());
    }

    private static Integer exactPoint(PointEntry point) {
        if (point == null || point.kind != PointEntry.Kind.DEFINITE
                || !isThemePoint(point.value)) {
            return null;
        }
        return Integer.valueOf(point.value);
    }

    private static Integer pointFromScoreboardDelta(ScoreDelta delta) {
        if (delta == null || !delta.isDefinite() || !"scoreboard".equals(delta.source)
                || delta.value == null || !isThemePoint(delta.value.intValue())) {
            return null;
        }
        return delta.value;
    }

    private static boolean isThemePoint(int point) {
        return point >= 1 && point <= 3;
    }

}
