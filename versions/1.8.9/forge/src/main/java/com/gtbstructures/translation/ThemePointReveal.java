package com.gtbstructures.translation;

import com.gtbstructures.commands.CommandGtb;
import com.gtbstructures.scoreboard.PointEntry;
import com.gtbstructures.scoreboard.ThemePointAward;
import com.gtbstructures.stats.GameStats;
import com.gtbstructures.stats.ScoreDelta;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Queue;

/** Emits one exact theme-point notice per GTB round. */
public final class ThemePointReveal {
    private static final Queue<String> PENDING = new ConcurrentLinkedQueue<String>();

    private ThemePointReveal() {}

    public static void markCompleteThemeRevealed(GameStats.RoundData round) {
        if (round != null) round.completeThemeRevealedToUser = true;
    }

    public static void maybeSendFromHint(GameStats.RoundData round,
                                         boolean enabled,
                                         boolean showAfterGuess) {
        if (round == null) return;
        queueIfAllowed(round, pointFromHint(round.langCode, round.hintStructure),
                enabled, showAfterGuess);
    }

    public static void maybeSendFromTheme(GameStats.RoundData round,
                                          String displayedTheme,
                                          String liveLangCode,
                                          Collection<String> ruledOutEnglishThemes,
                                          boolean enabled,
                                          boolean showAfterGuess) {
        if (round == null) return;
        Integer point = pointFromTheme(displayedTheme,
                liveLangCode == null ? round.langCode : liveLangCode,
                round.builderThemeOptions, ruledOutEnglishThemes,
                currentBuilderScoreboardPoint(round));
        queueIfAllowed(round, point, enabled, showAfterGuess);
    }

    public static void maybeSendFromScoreDelta(GameStats.RoundData round,
                                                boolean enabled,
                                                boolean showAfterGuess) {
        queueIfAllowed(round, currentBuilderScoreboardPoint(round), enabled, showAfterGuess);
    }

    static Integer pointFromHint(String langCode, String structure) {
        String normalized = TranslationData.normalizeLangCode(langCode);
        if (!"en".equals(normalized)) return null;
        PointEntry point = ThemePointAward.computeEnglishHintStructure(structure);
        return exactPoint(point);
    }

    static Integer pointFromTheme(String displayedTheme,
                                  String langCode,
                                  String[] builderThemeOptions,
                                  Collection<String> ruledOutEnglishThemes,
                                  Integer scoreboardPoint) {
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
        if (inferred != null && inferred.kind == PointEntry.Kind.FORK
                && inferred.fork != null && scoreboardPoint != null
                && inferred.fork.contains(scoreboardPoint)
                && isThemePoint(scoreboardPoint.intValue())) {
            return scoreboardPoint;
        }
        return null;
    }

    static Integer currentBuilderScoreboardPoint(GameStats.RoundData round) {
        if (round == null || round.builder == null || round.builder.isEmpty()) return null;
        ScoreDelta delta = scoreDeltaIgnoreCase(round, round.builder);
        if (delta == null || !delta.isDefinite() || !"scoreboard".equals(delta.source)
                || delta.value == null || !isThemePoint(delta.value.intValue())) {
            return null;
        }
        return delta.value;
    }

    static String buildMessageForTest(int point) {
        String color = point == 1 ? "\u00a7a" : point == 2 ? "\u00a7e" : "\u00a7c";
        return CommandGtb.head + "Point value of the current theme: " + color + point;
    }

    public static void drainPending() {
        if (PENDING.isEmpty()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null) return;
        String message;
        while ((message = PENDING.poll()) != null) {
            mc.thePlayer.addChatMessage(new ChatComponentText(message));
        }
    }

    public static void discardPending() {
        PENDING.clear();
    }

    static void clearPendingForTest() {
        discardPending();
    }

    static String pollPendingForTest() {
        return PENDING.poll();
    }

    private static void queueIfAllowed(GameStats.RoundData round,
                                       Integer point,
                                       boolean enabled,
                                       boolean showAfterGuess) {
        queueIfAllowed(round, point, enabled, showAfterGuess, isSelfBuildRound(round));
    }

    static void queueIfAllowedForTest(GameStats.RoundData round, Integer point,
                                      boolean enabled, boolean showAfterGuess,
                                      boolean selfBuildRound) {
        queueIfAllowed(round, point, enabled, showAfterGuess, selfBuildRound);
    }

    private static void queueIfAllowed(GameStats.RoundData round,
                                       Integer point,
                                       boolean enabled,
                                       boolean showAfterGuess,
                                       boolean selfBuildRound) {
        if (!enabled || round == null || round.themePointRevealSent || point == null) return;
        if (round.localBuildOwner || selfBuildRound) return;
        if (round.completeThemeRevealedToUser && !showAfterGuess) return;
        if (!isThemePoint(point.intValue())) return;
        round.themePointRevealSent = true;
        PENDING.add(buildMessageForTest(point.intValue()));
    }

    private static boolean isSelfBuildRound(GameStats.RoundData round) {
        if (round == null || round.builder == null || round.builder.isEmpty()) return false;
        try {
            Minecraft mc = Minecraft.getMinecraft();
            return mc != null && mc.thePlayer != null
                    && round.builder.equalsIgnoreCase(mc.thePlayer.getName());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Integer exactPoint(PointEntry point) {
        if (point == null || point.kind != PointEntry.Kind.DEFINITE
                || !isThemePoint(point.value)) {
            return null;
        }
        return Integer.valueOf(point.value);
    }

    private static boolean isThemePoint(int point) {
        return point >= 1 && point <= 3;
    }

    private static ScoreDelta scoreDeltaIgnoreCase(GameStats.RoundData round, String name) {
        ScoreDelta direct = round.scoreDeltas.get(name);
        if (direct != null) return direct;
        for (java.util.Map.Entry<String, ScoreDelta> entry : round.scoreDeltas.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
