package com.gtbstructures.stats;

import com.gtbstructures.translation.TranslationData;

import java.util.logging.Level;
import java.util.logging.Logger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.*;

/**
 * JSON serialization, file I/O, and export logic for GTB game stats.
 */
public class GameExporter {

    private static final Logger LOGGER = Logger.getLogger("GtbStructures");
    private static final int RULED_OUT_GUESS_LIST_CACHE_LIMIT = 128;
    // RoundData retains and mutates this list; identity avoids mutable hash keys.
    private static final Map<List<GameStats.GuessEntry>, GuessRuleOutCache>
            RULED_OUT_GUESS_LIST_CACHE =
            new IdentityHashMap<List<GameStats.GuessEntry>, GuessRuleOutCache>();
    private static int ruledOutGuessListRebuildCount;

    private static final class GuessRuleOutCache {
        final List<String> guessTexts;
        final String translationDataHash;
        final Set<String> englishThemes;

        GuessRuleOutCache(List<String> guessTexts,
                          String translationDataHash,
                          Set<String> englishThemes) {
            this.guessTexts = guessTexts;
            this.translationDataHash = translationDataHash;
            this.englishThemes = englishThemes;
        }
    }

    // ── JSON Generation ─────────────────────────────────────────────────

    static String toJson(long gameStart, long endTime, boolean completed, boolean interrupted,
                         String interruptReason, String language, String localPlayer,
                         String trueIgn, String nick, boolean isNicked,
                         long activeTimeMs, List<String> players,
                         List<GameStats.RoundData> rounds, List<GameStats.RankEntry> rankings,
                         Map<String, Integer> unknownResidual) {
        return toJson(gameStart, endTime, completed, interrupted, interruptReason, language,
                localPlayer, trueIgn, null, nick, isNicked, activeTimeMs, players, rounds,
                rankings, unknownResidual);
    }

    static String toJson(long gameStart, long endTime, boolean completed, boolean interrupted,
                         String interruptReason, String language, String localPlayer,
                         String trueIgn, String playerUuid, String nick, boolean isNicked,
                         long activeTimeMs, List<String> players,
                         List<GameStats.RoundData> rounds, List<GameStats.RankEntry> rankings,
                         Map<String, Integer> unknownResidual) {
        StringBuilder sb = new StringBuilder();
        String translationDataHash = TranslationData.getCurrentDataHash();
        sb.append("{\n");
        sb.append("  \"schemaVersion\": 6,\n");
        sb.append("  \"gameStart\": ").append(gameStart).append(",\n");
        sb.append("  \"gameEnd\": ").append(endTime).append(",\n");
        sb.append("  \"translationDataHash\": ").append(jsonStr(
                translationDataHash == null || translationDataHash.isEmpty()
                        ? null : translationDataHash)).append(",\n");
        sb.append("  \"translationDataObservedAt\": ")
                .append(TranslationData.getCurrentDataObservedAt()).append(",\n");
        sb.append("  \"completed\": ").append(completed).append(",\n");
        sb.append("  \"interrupted\": ").append(interrupted).append(",\n");
        sb.append("  \"interruptReason\": ").append(jsonStr(interruptReason)).append(",\n");
        sb.append("  \"language\": ").append(jsonStr(language)).append(",\n");
        sb.append("  \"localPlayer\": ").append(jsonStr(localPlayer)).append(",\n");
        sb.append("  \"trueIgn\": ").append(jsonStr(trueIgn)).append(",\n");
        sb.append("  \"playerUuid\": ").append(jsonStr(PlayerUuid.normalize(playerUuid)))
                .append(",\n");
        sb.append("  \"nick\": ").append(jsonStr(isNicked ? nick : null)).append(",\n");
        sb.append("  \"isNicked\": ").append(isNicked).append(",\n");
        sb.append("  \"activeTimeMs\": ").append(Math.max(0L, activeTimeMs)).append(",\n");
        sb.append("  \"players\": ").append(jsonStrArr(players)).append(",\n");
        ThemeDifficultyDistribution themeDifficulty = new ThemeDifficultyDistribution();
        sb.append("  \"rounds\": [\n");
        for (int i = 0; i < rounds.size(); i++) {
            GameStats.RoundData r = rounds.get(i);
            String languageCode = r.langCode != null ? r.langCode
                    : LangPatterns.codeFromDisplayName(r.language);
            ScoreDelta builderDelta = findScoreDelta(r.scoreDeltas, r.builder);
            Set<String> ruledOutEnglishThemes = ruledOutEnglishThemes(r.guesses);
            List<String> englishThemes =
                    TranslationData.resolveEnglishThemes(
                            languageCode, r.theme, builderDelta, ruledOutEnglishThemes);
            themeDifficulty.addEnglishThemes(englishThemes);
            sb.append("    {\n");
            sb.append("      \"round\": ").append(formatRoundNum(r.roundNumber)).append(",\n");
            sb.append("      \"totalRounds\": ").append(formatRoundNum(r.totalRounds)).append(",\n");
            sb.append("      \"roundStart\": ").append(r.roundStart).append(",\n");
            sb.append("      \"roundEnd\": ").append(r.roundEnd).append(",\n");
            sb.append("      \"builder\": ").append(jsonStr(r.builder)).append(",\n");
            sb.append("      \"theme\": ").append(jsonStr(r.theme)).append(",\n");
            sb.append("      \"themeCharCount\": ").append(r.themeCharCount).append(",\n");
            sb.append("      \"language\": ").append(jsonStr(r.language)).append(",\n");
            sb.append("      \"languageCode\": ").append(jsonStr(languageCode)).append(",\n");
            sb.append("      \"englishThemes\": ").append(jsonStrArr(englishThemes)).append(",\n");
            sb.append("      \"localParticipationComplete\": ")
                    .append(isLocalParticipationComplete(r, localPlayer)).append(",\n");
            sb.append("      \"builderThemeOptions\": ");
            if (r.builderThemeOptions != null) {
                sb.append(jsonStrArr(Arrays.asList(r.builderThemeOptions)));
            } else {
                sb.append("null");
            }
            sb.append(",\n");

            // Guesses
            sb.append("      \"guesses\": [");
            for (int j = 0; j < r.guesses.size(); j++) {
                GameStats.GuessEntry g = r.guesses.get(j);
                sb.append("{\"t\":").append(g.t)
                        .append(",\"physicalTimer\":").append(jsonInt(g.physicalTimer))
                        .append(",\"ign\":").append(jsonStr(g.ign))
                        .append(",\"text\":").append(jsonStr(g.text)).append("}");
                if (j < r.guesses.size() - 1) sb.append(",");
            }
            sb.append("],\n");

            // Correct guesses
            sb.append("      \"correctGuesses\": [");
            for (int j = 0; j < r.correctGuesses.size(); j++) {
                GameStats.CorrectGuessEntry cg = r.correctGuesses.get(j);
                sb.append("{\"t\":").append(cg.t)
                        .append(",\"physicalTimer\":").append(jsonInt(cg.physicalTimer))
                        .append(",\"ign\":").append(jsonStr(cg.ign)).append("}");
                if (j < r.correctGuesses.size() - 1) sb.append(",");
            }
            sb.append("],\n");

            // Hints (shared structure + per-hint items)
            sb.append("      \"hints\": {\"structure\":").append(jsonStr(r.hintStructure))
                    .append(",\"items\":[");
            for (int j = 0; j < r.hints.size(); j++) {
                GameStats.HintEntry h = r.hints.get(j);
                sb.append("{\"t\":").append(h.t)
                        .append(",\"physicalTimer\":").append(jsonInt(h.physicalTimer))
                        .append(",\"text\":").append(jsonStr(h.text)).append("}");
                if (j < r.hints.size() - 1) sb.append(",");
            }
            sb.append("]},\n");

            // Builder handheld item history
            sb.append("      \"handheldItems\": [");
            for (int j = 0; j < r.handheldItems.size(); j++) {
                GameStats.HandheldItemEntry item = r.handheldItems.get(j);
                sb.append("{\"key\":").append(jsonStr(item.key))
                        .append(",\"name\":").append(jsonStr(item.name))
                        .append(",\"registryName\":").append(jsonStr(item.registryName))
                        .append(",\"numericId\":").append(item.numericId)
                        .append(",\"metadata\":").append(item.metadata)
                        .append(",\"stackSize\":").append(item.stackSize)
                        .append(",\"unlocalizedName\":").append(jsonStr(item.unlocalizedName))
                        .append(",\"modernKind\":").append(jsonStr(item.modernKind))
                        .append(",\"modernName\":").append(jsonStr(item.modernName))
                        .append(",\"nbt\":").append(jsonStr(item.nbt))
                        .append(",\"firstSeen\":").append(item.firstSeen)
                        .append(",\"lastSeen\":").append(item.lastSeen)
                        .append(",\"intervals\":[");
                for (int k = 0; k < item.intervals.size(); k++) {
                    GameStats.HandheldItemInterval interval = item.intervals.get(k);
                    sb.append("{\"start\":").append(interval.start)
                            .append(",\"end\":").append(interval.end)
                            .append(",\"startPhysicalTimer\":").append(jsonInt(interval.startPhysicalTimer))
                            .append(",\"endPhysicalTimer\":").append(jsonInt(interval.endPhysicalTimer))
                            .append("}");
                    if (k < item.intervals.size() - 1) sb.append(",");
                }
                sb.append("]}");
                if (j < r.handheldItems.size() - 1) sb.append(",");
            }
            sb.append("],\n");

            // Events
            sb.append("      \"events\": [");
            for (int j = 0; j < r.events.size(); j++) {
                GameStats.GameEvent e = r.events.get(j);
                sb.append("{\"t\":").append(e.t)
                        .append(",\"physicalTimer\":").append(jsonInt(e.physicalTimer))
                        .append(",\"type\":").append(jsonStr(e.type));
                if (e.detail != null) sb.append(",\"detail\":").append(jsonStr(e.detail));
                if (e.physicalTimerBefore != null) {
                    sb.append(",\"physicalTimerBefore\":").append(jsonInt(e.physicalTimerBefore));
                }
                if (e.physicalTimerAfter != null) {
                    sb.append(",\"physicalTimerAfter\":").append(jsonInt(e.physicalTimerAfter));
                }
                sb.append("}");
                if (j < r.events.size() - 1) sb.append(",");
            }
            sb.append("],\n");

            // Score deltas (per-round reconciled deltas keyed by IGN)
            sb.append("      \"scoreDeltas\": {");
            List<Map.Entry<String, ScoreDelta>> sortedDeltas =
                    new ArrayList<Map.Entry<String, ScoreDelta>>(r.scoreDeltas.entrySet());
            Collections.sort(sortedDeltas, new Comparator<Map.Entry<String, ScoreDelta>>() {
                @Override
                public int compare(Map.Entry<String, ScoreDelta> a, Map.Entry<String, ScoreDelta> b) {
                    return a.getKey().compareToIgnoreCase(b.getKey());
                }
            });
            if (!sortedDeltas.isEmpty()) sb.append("\n");
            for (int si = 0; si < sortedDeltas.size(); si++) {
                Map.Entry<String, ScoreDelta> entry = sortedDeltas.get(si);
                sb.append("        ").append(jsonStr(entry.getKey()))
                        .append(": ").append(entry.getValue().toJson());
                if (si < sortedDeltas.size() - 1) sb.append(",");
                sb.append("\n");
            }
            if (!sortedDeltas.isEmpty()) sb.append("      ");
            sb.append("},\n");
            sb.append("      \"dataGap\": ").append(r.dataGap).append("\n");

            sb.append("    }");
            if (i < rounds.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ],\n");
        sb.append("  \"themeDifficultyDistribution\": ")
                .append(themeDifficulty.toJson()).append(",\n");

        // Result (rankings) — sorted like the final leaderboard
        sb.append("  \"result\": ");
        if (!rankings.isEmpty()) {
            List<GameStats.RankEntry> sortedRanks = new ArrayList<GameStats.RankEntry>(rankings);
            Collections.sort(sortedRanks, new Comparator<GameStats.RankEntry>() {
                @Override
                public int compare(GameStats.RankEntry a, GameStats.RankEntry b) {
                    return a.place - b.place;
                }
            });
            sb.append("{\n    \"rankings\": [\n");
            for (int i = 0; i < sortedRanks.size(); i++) {
                GameStats.RankEntry re = sortedRanks.get(i);
                sb.append("      {\"place\": ").append(re.place)
                        .append(", \"ign\": ").append(jsonStr(re.ign))
                        .append(", \"score\": ").append(re.score).append("}");
                if (i < sortedRanks.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("    ]\n  }");
        } else {
            sb.append("null");
        }

        // Unattributed residuals from end-of-game leaderboard reconciliation
        if (unknownResidual != null && !unknownResidual.isEmpty()) {
            sb.append(",\n  \"unknownResidual\": {");
            List<Map.Entry<String, Integer>> sortedRes =
                    new ArrayList<Map.Entry<String, Integer>>(unknownResidual.entrySet());
            Collections.sort(sortedRes, new Comparator<Map.Entry<String, Integer>>() {
                @Override
                public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) {
                    return a.getKey().compareToIgnoreCase(b.getKey());
                }
            });
            sb.append("\n");
            for (int i = 0; i < sortedRes.size(); i++) {
                Map.Entry<String, Integer> e = sortedRes.get(i);
                sb.append("    ").append(jsonStr(e.getKey())).append(": ").append(e.getValue());
                if (i < sortedRes.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("  }");
        }

        sb.append("\n}\n");

        return sb.toString();
    }

    static synchronized Set<String> ruledOutEnglishThemes(
            List<GameStats.GuessEntry> guesses) {
        if (guesses == null || guesses.isEmpty()) return Collections.emptySet();
        String translationDataHash = TranslationData.getCurrentDataHash();
        GuessRuleOutCache cached = RULED_OUT_GUESS_LIST_CACHE.get(guesses);
        if (cached != null
                && Objects.equals(cached.translationDataHash, translationDataHash)
                && guessTextsMatch(cached.guessTexts, guesses)) {
            return cached.englishThemes;
        }

        List<String> guessTexts = snapshotGuessTexts(guesses);
        Set<String> englishThemes = Collections.unmodifiableSet(
                TranslationData.ruledOutEnglishThemesFromGuesses(guessTexts));
        if (cached == null && RULED_OUT_GUESS_LIST_CACHE.size()
                >= RULED_OUT_GUESS_LIST_CACHE_LIMIT) {
            Iterator<List<GameStats.GuessEntry>> victim =
                    RULED_OUT_GUESS_LIST_CACHE.keySet().iterator();
            if (victim.hasNext()) {
                victim.next();
                victim.remove();
            }
        }
        RULED_OUT_GUESS_LIST_CACHE.put(guesses, new GuessRuleOutCache(
                guessTexts, translationDataHash, englishThemes));
        ruledOutGuessListRebuildCount++;
        return englishThemes;
    }

    private static List<String> snapshotGuessTexts(List<GameStats.GuessEntry> guesses) {
        List<String> out = new ArrayList<String>(guesses.size());
        for (GameStats.GuessEntry guess : guesses) {
            out.add(guess == null ? null : guess.text);
        }
        return Collections.unmodifiableList(out);
    }

    private static boolean guessTextsMatch(List<String> snapshot,
                                           List<GameStats.GuessEntry> guesses) {
        if (snapshot.size() != guesses.size()) return false;
        for (int i = 0; i < snapshot.size(); i++) {
            GameStats.GuessEntry guess = guesses.get(i);
            if (!Objects.equals(snapshot.get(i), guess == null ? null : guess.text)) return false;
        }
        return true;
    }

    static synchronized void resetRuledOutGuessCacheForTest() {
        RULED_OUT_GUESS_LIST_CACHE.clear();
        ruledOutGuessListRebuildCount = 0;
    }

    static synchronized int ruledOutGuessRebuildCountForTest() {
        return ruledOutGuessListRebuildCount;
    }

    static synchronized int ruledOutGuessCacheSizeForTest() {
        return RULED_OUT_GUESS_LIST_CACHE.size();
    }

    static int ruledOutGuessCacheLimitForTest() {
        return RULED_OUT_GUESS_LIST_CACHE_LIMIT;
    }

    private static ScoreDelta findScoreDelta(Map<String, ScoreDelta> deltas, String ign) {
        if (deltas == null || ign == null) return null;
        for (Map.Entry<String, ScoreDelta> entry : deltas.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(ign)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static boolean isLocalParticipationComplete(GameStats.RoundData round,
                                                        String localPlayer) {
        if (round == null || localPlayer == null || localPlayer.isEmpty()) return false;
        if (round.roundNumber <= 0 || round.roundStart <= 0L
                || round.roundEnd < round.roundStart || round.dataGap) {
            return false;
        }
        for (GameStats.GameEvent event : round.events) {
            if (event == null || event.type == null) continue;
            if ("disconnect".equals(event.type)
                    || "rejoin".equals(event.type)
                    || "rejoin_round_change".equals(event.type)
                    || "rejoin_same_round".equals(event.type)
                    || "builder_left".equals(event.type)
                    || "afk_skip".equals(event.type)) {
                return false;
            }
        }
        return true;
    }

    // ── JSON Utilities ──────────────────────────────────────────────────

    static final class ThemeDifficultyDistribution {
        double easyWeight;
        double mediumWeight;
        double hardWeight;
        int resolvedRounds;

        void addEnglishThemes(List<String> englishThemes) {
            if (englishThemes == null || englishThemes.isEmpty()) return;
            double weight = 1.0D / englishThemes.size();
            for (String englishTheme : englishThemes) {
                addBucket(TranslationData.lengthBucket(englishTheme), weight);
            }
            resolvedRounds++;
        }

        void addBucket(int bucket, double weight) {
            if (bucket <= 1) easyWeight += weight;
            else if (bucket == 2) mediumWeight += weight;
            else hardWeight += weight;
        }

        double totalWeight() {
            return easyWeight + mediumWeight + hardWeight;
        }

        String toJson() {
            double total = totalWeight();
            return "{\"easyWeight\":" + jsonDouble(easyWeight)
                    + ",\"mediumWeight\":" + jsonDouble(mediumWeight)
                    + ",\"hardWeight\":" + jsonDouble(hardWeight)
                    + ",\"totalWeight\":" + jsonDouble(total)
                    + ",\"resolvedRounds\":" + resolvedRounds
                    + ",\"easyPercent\":" + jsonDouble(percent(easyWeight, total))
                    + ",\"mediumPercent\":" + jsonDouble(percent(mediumWeight, total))
                    + ",\"hardPercent\":" + jsonDouble(percent(hardWeight, total))
                    + "}";
        }

        private static double percent(double value, double total) {
            return total == 0.0D ? 0.0D : value * 100.0D / total;
        }
    }

    /** -1 (unknown) and 0 (not yet filled) are rendered as the JSON string "?". */
    static String formatRoundNum(int n) {
        if (n <= 0) return "\"?\"";
        return String.valueOf(n);
    }

    static String jsonStr(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append("\"");
        return sb.toString();
    }

    static String jsonInt(Integer n) {
        return n == null ? "null" : String.valueOf(n.intValue());
    }

    static String jsonDouble(double value) {
        if (Math.abs(value) < 0.0000000001D) value = 0.0D;
        if (value == Math.rint(value)) return String.valueOf((long) value);
        return Double.toString(value);
    }

    static String jsonStrArr(List<String> list) {
        if (list == null || list.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            sb.append(jsonStr(list.get(i)));
            if (i < list.size() - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    // ── File I/O ────────────────────────────────────────────────────────

    static void writeJsonFile(File file, String json) {
        try {
            BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));
            w.write(json);
            w.close();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to write " + file.getName(), e);
        }
    }


    static File getStatsDir(File gameDir) {
        if (gameDir == null) return null;
        File statsDir = new File(new File(gameDir, "config"), "gtbstats");
        if (!statsDir.exists()) statsDir.mkdirs();
        return statsDir;
    }

}
