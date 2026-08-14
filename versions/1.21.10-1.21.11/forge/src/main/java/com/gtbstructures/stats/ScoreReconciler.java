package com.gtbstructures.stats;

import com.gtbstructures.translation.TranslationData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Platform-neutral per-round score reconciliation copied from the 1.8.9 tracker.
 */
public final class ScoreReconciler {

    private ScoreReconciler() {
    }

    public static void recordGuessDelta(RoundScoreData round, String ign, int orderZeroBased) {
        if (round == null || ign == null || ign.isEmpty()) return;
        int points = orderZeroBased == 0 ? 3 : (orderZeroBased == 1 ? 2 : 1);
        ScoreDelta existing = round.scoreDeltas.get(ign);
        if (existing != null && existing.isDefinite()
                && "scoreboard".equals(existing.source) && existing.value != 0) {
            return;
        }

        if (hasReconnectGapBeforeGuess(round)) {
            round.scoreDeltas.put(ign, ScoreDelta.fork(
                    guessOptionsAfterReconnect(round, ign, orderZeroBased), "guess_rejoin_gap"));
            return;
        }
        round.scoreDeltas.put(ign, ScoreDelta.definite(points, "guess"));
    }

    public static void recordGuessDelta(GameStats.RoundData round, String ign, int orderZeroBased) {
        if (round == null || ign == null || ign.isEmpty()) return;
        int points = orderZeroBased == 0 ? 3 : (orderZeroBased == 1 ? 2 : 1);
        ScoreDelta existing = round.scoreDeltas.get(ign);
        if (existing != null && existing.isDefinite()
                && "scoreboard".equals(existing.source) && existing.value != 0) {
            return;
        }

        if (hasReconnectGapBeforeGuess(round)) {
            round.scoreDeltas.put(ign, ScoreDelta.fork(
                    guessOptionsAfterReconnect(round, ign, orderZeroBased), "guess_rejoin_gap"));
            return;
        }
        round.scoreDeltas.put(ign, ScoreDelta.definite(points, "guess"));
    }

    public static void recordExplicitGuessDelta(RoundScoreData round, String ign,
                                                int points, String source) {
        if (round == null || ign == null || ign.isEmpty()) return;
        if (points < 1 || points > 3) return;
        round.scoreDeltas.put(ign, ScoreDelta.definite(points,
                source == null || source.isEmpty() ? "explicit_guess" : source));
    }

    public static void computeBuilderDelta(RoundScoreData round) {
        if (round == null || round.builder == null || round.builder.isEmpty()) return;
        if (round.theme == null || round.theme.isEmpty()) return;

        if (round.correctGuesses == null || round.correctGuesses.isEmpty()) {
            round.scoreDeltas.put(round.builder, ScoreDelta.definite(0, "no_correct_guesses"));
            return;
        }

        ScoreDelta existing = round.scoreDeltas.get(round.builder);
        if (existing != null && existing.isDefinite() && "scoreboard".equals(existing.source)) return;

        if (round.builderThemeOptions != null && round.builderThemeOptions.length >= 3) {
            for (int i = 0; i < 3; i++) {
                String option = round.builderThemeOptions[i];
                if (option != null && option.equalsIgnoreCase(round.theme)) {
                    round.scoreDeltas.put(round.builder, ScoreDelta.definite(i + 1, "builder_gui"));
                    return;
                }
            }
        }

        Set<Integer> options = TranslationData.lookupBuilderPoints(round.langCode, round.theme);
        if (options.isEmpty()) return;
        round.scoreDeltas.put(round.builder, ScoreDelta.fork(options, "builder"));
    }

    public static void computeBuilderDelta(GameStats.RoundData round) {
        if (round == null || round.builder == null || round.builder.isEmpty()) return;
        if (round.theme == null || round.theme.isEmpty()) return;

        if (round.correctGuesses == null || round.correctGuesses.isEmpty()) {
            round.scoreDeltas.put(round.builder, ScoreDelta.definite(0, "no_correct_guesses"));
            return;
        }

        ScoreDelta existing = round.scoreDeltas.get(round.builder);
        if (existing != null && existing.isDefinite() && "scoreboard".equals(existing.source)) return;

        if (round.builderThemeOptions != null && round.builderThemeOptions.length >= 3) {
            for (int i = 0; i < 3; i++) {
                String option = round.builderThemeOptions[i];
                if (option != null && option.equalsIgnoreCase(round.theme)) {
                    round.scoreDeltas.put(round.builder, ScoreDelta.definite(i + 1, "builder_gui"));
                    return;
                }
            }
        }

        Set<Integer> options = TranslationData.lookupBuilderPoints(round.langCode, round.theme);
        if (options.isEmpty()) return;
        round.scoreDeltas.put(round.builder, ScoreDelta.fork(options, "builder"));
    }

    public static void crosscheckBuilderFromScoreboard(RoundScoreData round,
                                                       String liveLangCode,
                                                       String scoreboardTheme) {
        if (round == null || round.builder == null
                || scoreboardTheme == null || scoreboardTheme.isEmpty()) return;
        ScoreDelta existing = round.scoreDeltas.get(round.builder);
        if (existing != null && existing.isDefinite() && "scoreboard".equals(existing.source)) return;

        String live = normalize(liveLangCode);
        Set<Integer> options;
        if (live == null || "en".equals(live)) {
            options = new TreeSet<Integer>();
            options.add(TranslationData.lengthBucket(scoreboardTheme));
        } else {
            options = TranslationData.lookupBuilderPoints(liveLangCode, scoreboardTheme);
        }
        if (options.isEmpty()) return;

        if (existing == null) {
            round.scoreDeltas.put(round.builder, ScoreDelta.fork(options, "builder"));
            return;
        }

        Set<Integer> intersection = new TreeSet<Integer>();
        for (Integer value : options) {
            if (existing.canBe(value.intValue())) intersection.add(value);
        }
        if (intersection.isEmpty()) return;
        if (intersection.size() == 1) {
            round.scoreDeltas.put(round.builder,
                    ScoreDelta.definite(intersection.iterator().next(), existing.source));
        } else if (!existing.isDefinite()) {
            round.scoreDeltas.put(round.builder, ScoreDelta.fork(intersection, existing.source));
        }
    }

    public static void crosscheckBuilderFromScoreboard(GameStats.RoundData round,
                                                       String liveLangCode,
                                                       String scoreboardTheme) {
        if (round == null || round.builder == null
                || scoreboardTheme == null || scoreboardTheme.isEmpty()) return;
        ScoreDelta existing = round.scoreDeltas.get(round.builder);
        if (existing != null && existing.isDefinite() && "scoreboard".equals(existing.source)) return;

        String live = normalize(liveLangCode);
        Set<Integer> options;
        if (live == null || "en".equals(live)) {
            options = new TreeSet<Integer>();
            options.add(Integer.valueOf(TranslationData.lengthBucket(scoreboardTheme)));
        } else {
            options = TranslationData.lookupBuilderPoints(liveLangCode, scoreboardTheme);
        }
        if (options.isEmpty()) return;

        if (existing == null) {
            round.scoreDeltas.put(round.builder, ScoreDelta.fork(options, "builder"));
            return;
        }

        Set<Integer> intersection = new TreeSet<Integer>();
        for (Integer value : options) {
            if (existing.canBe(value.intValue())) intersection.add(value);
        }
        if (intersection.isEmpty()) return;
        if (intersection.size() == 1) {
            round.scoreDeltas.put(round.builder,
                    ScoreDelta.definite(intersection.iterator().next().intValue(), existing.source));
        } else if (!existing.isDefinite()) {
            round.scoreDeltas.put(round.builder, ScoreDelta.fork(intersection, existing.source));
        }
    }

    public static void observeScoreboard(RoundScoreData round, String ign,
                                         int observedCumulative, Integer priorCumulative,
                                         Map<String, Integer> residualSink) {
        if (priorCumulative == null) return;
        observeScoreboard(round, ign, observedCumulative, priorCumulative.intValue(), residualSink);
    }

    public static void observeScoreboard(RoundScoreData round, String ign,
                                         int observedCumulative, int priorCumulative,
                                         Map<String, Integer> residualSink) {
        if (round == null || ign == null || ign.isEmpty()) return;
        int delta = observedCumulative - priorCumulative;
        if (delta < 0) return;

        if (delta > 3) {
            ScoreDelta existing = round.scoreDeltas.get(ign);
            if (residualSink != null && delta > 0 && existing == null) {
                Integer current = residualSink.get(ign);
                residualSink.put(ign, (current == null ? 0 : current.intValue()) + delta);
            }
            if (existing == null || !existing.isDefinite()) {
                Set<Integer> options = new TreeSet<Integer>();
                options.add(0);
                options.add(1);
                options.add(2);
                options.add(3);
                round.scoreDeltas.put(ign, ScoreDelta.fork(options, "scoreboard_residual"));
            }
            return;
        }

        ScoreDelta existing = round.scoreDeltas.get(ign);
        if (existing != null && existing.isDefinite()) {
            if (delta == 0 && !"scoreboard".equals(existing.source)) return;
            if (existing.value != delta || !"scoreboard".equals(existing.source)) {
                round.scoreDeltas.put(ign, ScoreDelta.definite(delta, "scoreboard"));
            }
            return;
        }
        if (existing != null && !existing.isDefinite() && existing.canBe(delta)) {
            round.scoreDeltas.put(ign, ScoreDelta.definite(delta, "scoreboard"));
            return;
        }
        round.scoreDeltas.put(ign, ScoreDelta.definite(delta, "scoreboard"));
    }

    public static void observeScoreboard(RoundScoreData round, String ign,
                                         int observedCumulative, int priorCumulative) {
        observeScoreboard(round, ign, observedCumulative, priorCumulative, null);
    }

    public static void observeScoreboard(GameStats.RoundData round, String ign,
                                         int observedCumulative, Integer priorCumulative,
                                         Map<String, Integer> residualSink) {
        if (priorCumulative == null) return;
        observeScoreboard(round, ign, observedCumulative, priorCumulative.intValue(), residualSink);
    }

    public static void observeScoreboard(GameStats.RoundData round, String ign,
                                         int observedCumulative, int priorCumulative,
                                         Map<String, Integer> residualSink) {
        if (round == null || ign == null || ign.isEmpty()) return;
        int delta = observedCumulative - priorCumulative;
        if (delta < 0) return;

        if (delta > 3) {
            ScoreDelta existing = round.scoreDeltas.get(ign);
            if (residualSink != null && delta > 0 && existing == null) {
                Integer current = residualSink.get(ign);
                residualSink.put(ign, Integer.valueOf((current == null ? 0 : current.intValue()) + delta));
            }
            if (existing == null || !existing.isDefinite()) {
                Set<Integer> options = new TreeSet<Integer>();
                options.add(Integer.valueOf(0));
                options.add(Integer.valueOf(1));
                options.add(Integer.valueOf(2));
                options.add(Integer.valueOf(3));
                round.scoreDeltas.put(ign, ScoreDelta.fork(options, "scoreboard_residual"));
            }
            return;
        }

        ScoreDelta existing = round.scoreDeltas.get(ign);
        if (existing != null && existing.isDefinite()) {
            if (delta == 0 && !"scoreboard".equals(existing.source)) return;
            if (existing.value != delta || !"scoreboard".equals(existing.source)) {
                round.scoreDeltas.put(ign, ScoreDelta.definite(delta, "scoreboard"));
            }
            return;
        }
        if (existing != null && !existing.isDefinite() && existing.canBe(delta)) {
            round.scoreDeltas.put(ign, ScoreDelta.definite(delta, "scoreboard"));
            return;
        }
        round.scoreDeltas.put(ign, ScoreDelta.definite(delta, "scoreboard"));
    }

    public static FinalizationResult finalizeWithLeaderboard(List<RoundScoreData> rounds,
                                                             List<RankEntry> rankings) {
        return finalizeWithLeaderboard(rounds, rankings, null);
    }

    public static FinalizationResult finalizeWithLeaderboard(List<RoundScoreData> rounds,
                                                             List<RankEntry> rankings,
                                                             Map<String, Integer> midGameResidual) {
        Map<String, Integer> unknown = new HashMap<String, Integer>();
        if (rounds == null || rankings == null || rankings.isEmpty()) {
            return new FinalizationResult(unknown);
        }

        for (RankEntry rank : rankings) {
            if (rank == null || rank.ign == null) continue;
            int definiteSum = 0;
            boolean hasGap = false;
            List<int[]> forkRoundIndexes = new ArrayList<int[]>();
            List<List<Integer>> forks = new ArrayList<List<Integer>>();

            for (int i = 0; i < rounds.size(); i++) {
                RoundScoreData round = rounds.get(i);
                if (round.dataGap) hasGap = true;
                ScoreDelta delta = round.scoreDeltas.get(rank.ign);
                if (delta == null) continue;
                if (delta.isDefinite()) {
                    definiteSum += delta.value;
                } else {
                    forkRoundIndexes.add(new int[]{i});
                    forks.add(delta.fork);
                }
            }

            int residualBucket = midGameResidual != null && midGameResidual.get(rank.ign) != null
                    ? midGameResidual.get(rank.ign) : 0;
            int residual = rank.score - definiteSum - residualBucket;

            if (forks.isEmpty()) {
                if (residual != 0 && hasGap) unknown.put(rank.ign, residual);
                else if (residual != 0) unknown.put(rank.ign, residual);
                continue;
            }

            List<int[]> solutions = new ArrayList<int[]>();
            int[] pick = new int[forks.size()];
            enumerate(forks, 0, 0, residual, pick, solutions);

            if (solutions.size() == 1) {
                int[] solution = solutions.get(0);
                for (int i = 0; i < forks.size(); i++) {
                    int roundIndex = forkRoundIndexes.get(i)[0];
                    RoundScoreData round = rounds.get(roundIndex);
                    ScoreDelta old = round.scoreDeltas.get(rank.ign);
                    if (old != null) {
                        round.scoreDeltas.put(rank.ign, ScoreDelta.definite(solution[i], old.source));
                    }
                }
            } else if (solutions.isEmpty()) {
                if (residual != 0) unknown.put(rank.ign, residual);
            } else {
                for (int i = 0; i < forks.size(); i++) {
                    int first = solutions.get(0)[i];
                    boolean allAgree = true;
                    for (int s = 1; s < solutions.size(); s++) {
                        if (solutions.get(s)[i] != first) {
                            allAgree = false;
                            break;
                        }
                    }
                    if (allAgree) {
                        int roundIndex = forkRoundIndexes.get(i)[0];
                        RoundScoreData round = rounds.get(roundIndex);
                        ScoreDelta old = round.scoreDeltas.get(rank.ign);
                        if (old != null) {
                            round.scoreDeltas.put(rank.ign, ScoreDelta.definite(first, old.source));
                        }
                    }
                }
            }
        }

        return new FinalizationResult(unknown);
    }

    public static FinalizationResult finalizeGameStatsWithLeaderboard(
            List<GameStats.RoundData> rounds, List<GameStats.RankEntry> rankings) {
        return finalizeGameStatsWithLeaderboard(rounds, rankings, null);
    }

    public static FinalizationResult finalizeGameStatsWithLeaderboard(
            List<GameStats.RoundData> rounds,
            List<GameStats.RankEntry> rankings,
            Map<String, Integer> midGameResidual) {
        Map<String, Integer> unknown = new HashMap<String, Integer>();
        if (rounds == null || rankings == null || rankings.isEmpty()) {
            return new FinalizationResult(unknown);
        }

        for (GameStats.RankEntry rank : rankings) {
            if (rank == null || rank.ign == null) continue;
            int definiteSum = 0;
            boolean hasGap = false;
            List<int[]> forkRoundIndexes = new ArrayList<int[]>();
            List<List<Integer>> forks = new ArrayList<List<Integer>>();

            for (int i = 0; i < rounds.size(); i++) {
                GameStats.RoundData round = rounds.get(i);
                if (round.dataGap) hasGap = true;
                ScoreDelta delta = round.scoreDeltas.get(rank.ign);
                if (delta == null) continue;
                if (delta.isDefinite()) {
                    definiteSum += delta.value;
                } else {
                    forkRoundIndexes.add(new int[]{i});
                    forks.add(delta.fork);
                }
            }

            int residualBucket = midGameResidual != null && midGameResidual.get(rank.ign) != null
                    ? midGameResidual.get(rank.ign).intValue() : 0;
            int residual = rank.score - definiteSum - residualBucket;

            if (forks.isEmpty()) {
                if (residual != 0 && hasGap) unknown.put(rank.ign, Integer.valueOf(residual));
                else if (residual != 0) unknown.put(rank.ign, Integer.valueOf(residual));
                continue;
            }

            List<int[]> solutions = new ArrayList<int[]>();
            int[] pick = new int[forks.size()];
            enumerate(forks, 0, 0, residual, pick, solutions);

            if (solutions.size() == 1) {
                int[] solution = solutions.get(0);
                for (int i = 0; i < forks.size(); i++) {
                    int roundIndex = forkRoundIndexes.get(i)[0];
                    GameStats.RoundData round = rounds.get(roundIndex);
                    ScoreDelta old = round.scoreDeltas.get(rank.ign);
                    if (old != null) {
                        round.scoreDeltas.put(rank.ign, ScoreDelta.definite(solution[i], old.source));
                    }
                }
            } else if (solutions.isEmpty()) {
                if (residual != 0) unknown.put(rank.ign, Integer.valueOf(residual));
            } else {
                for (int i = 0; i < forks.size(); i++) {
                    int first = solutions.get(0)[i];
                    boolean allAgree = true;
                    for (int s = 1; s < solutions.size(); s++) {
                        if (solutions.get(s)[i] != first) {
                            allAgree = false;
                            break;
                        }
                    }
                    if (allAgree) {
                        int roundIndex = forkRoundIndexes.get(i)[0];
                        GameStats.RoundData round = rounds.get(roundIndex);
                        ScoreDelta old = round.scoreDeltas.get(rank.ign);
                        if (old != null) {
                            round.scoreDeltas.put(rank.ign, ScoreDelta.definite(first, old.source));
                        }
                    }
                }
            }
        }

        return new FinalizationResult(unknown);
    }

    public static Integer knownPriorCumulative(List<GameStats.RoundData> rounds, String ign,
                                               GameStats.RoundData currentRound) {
        if (rounds == null || ign == null) return Integer.valueOf(0);
        int sum = 0;
        for (GameStats.RoundData round : rounds) {
            if (round == currentRound) break;
            if (round.dataGap) return null;
            ScoreDelta delta = round.scoreDeltas.get(ign);
            if (delta == null) continue;
            if (!delta.isDefinite()) return null;
            sum += delta.value;
        }
        return Integer.valueOf(sum);
    }

    public static int priorCumulative(List<RoundScoreData> rounds, String ign,
                                      RoundScoreData currentRound) {
        if (rounds == null || ign == null) return 0;
        int sum = 0;
        for (RoundScoreData round : rounds) {
            if (round == currentRound) break;
            ScoreDelta delta = round.scoreDeltas.get(ign);
            if (delta == null) continue;
            if (delta.isDefinite()) {
                sum += delta.value;
            } else if (delta.fork != null && !delta.fork.isEmpty()) {
                int min = delta.fork.get(0);
                for (Integer value : delta.fork) {
                    if (value.intValue() < min) min = value.intValue();
                }
                sum += min;
            }
        }
        return sum;
    }

    public static Integer knownPriorCumulative(List<RoundScoreData> rounds, String ign,
                                               RoundScoreData currentRound) {
        if (rounds == null || ign == null) return Integer.valueOf(0);
        int sum = 0;
        for (RoundScoreData round : rounds) {
            if (round == currentRound) break;
            if (round.dataGap) return null;
            ScoreDelta delta = round.scoreDeltas.get(ign);
            if (delta == null) continue;
            if (!delta.isDefinite()) return null;
            sum += delta.value;
        }
        return Integer.valueOf(sum);
    }

    private static void enumerate(List<List<Integer>> forks, int index, int sum,
                                  int target, int[] pick, List<int[]> solutions) {
        if (index == forks.size()) {
            if (sum == target) solutions.add(pick.clone());
            return;
        }
        for (Integer value : forks.get(index)) {
            pick[index] = value.intValue();
            enumerate(forks, index + 1, sum + value.intValue(), target, pick, solutions);
        }
    }

    private static String normalize(String code) {
        return TranslationData.normalizeLangCode(code);
    }

    private static boolean hasReconnectGapBeforeGuess(RoundScoreData round) {
        if (round == null || round.events == null || round.events.isEmpty()) return false;
        for (GameEvent event : round.events) {
            if (event == null || event.type == null) continue;
            if ("disconnect".equals(event.type)
                    || "rejoin".equals(event.type)
                    || "rejoin_round_change".equals(event.type)
                    || "rejoin_same_round".equals(event.type)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasReconnectGapBeforeGuess(GameStats.RoundData round) {
        if (round == null || round.events == null || round.events.isEmpty()) return false;
        for (GameStats.GameEvent event : round.events) {
            if (event == null || event.type == null) continue;
            if ("disconnect".equals(event.type)
                    || "rejoin".equals(event.type)
                    || "rejoin_round_change".equals(event.type)
                    || "rejoin_same_round".equals(event.type)) {
                return true;
            }
        }
        return false;
    }

    private static Set<Integer> guessOptionsAfterReconnect(RoundScoreData round,
                                                            String ign,
                                                            int orderZeroBased) {
        Set<String> priorGuessers = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        if (round.correctGuesses != null) {
            int limit = Math.min(Math.max(0, orderZeroBased), round.correctGuesses.size());
            for (int i = 0; i < limit; i++) {
                String priorIgn = round.correctGuesses.get(i);
                if (priorIgn != null) priorGuessers.add(priorIgn);
            }
        }
        return guessOptionsAfterReconnect(round.scoreDeltas, round.builder, ign,
                orderZeroBased, priorGuessers);
    }

    private static Set<Integer> guessOptionsAfterReconnect(GameStats.RoundData round,
                                                            String ign,
                                                            int orderZeroBased) {
        Set<String> priorGuessers = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        if (round.correctGuesses != null) {
            int limit = Math.min(Math.max(0, orderZeroBased), round.correctGuesses.size());
            for (int i = 0; i < limit; i++) {
                GameStats.CorrectGuessEntry guess = round.correctGuesses.get(i);
                if (guess != null && guess.ign != null) priorGuessers.add(guess.ign);
            }
        }
        return guessOptionsAfterReconnect(round.scoreDeltas, round.builder, ign,
                orderZeroBased, priorGuessers);
    }

    private static Set<Integer> guessOptionsAfterReconnect(Map<String, ScoreDelta> scoreDeltas,
                                                            String builder,
                                                            String ign,
                                                            int orderZeroBased,
                                                            Set<String> priorGuessers) {
        int scoreboardRankFloor = 0;
        Set<String> scoreboardGuessers = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        for (Map.Entry<String, ScoreDelta> entry : scoreDeltas.entrySet()) {
            String otherIgn = entry.getKey();
            ScoreDelta delta = entry.getValue();
            if (otherIgn == null || otherIgn.equalsIgnoreCase(ign)
                    || (builder != null && otherIgn.equalsIgnoreCase(builder))) continue;
            if (delta == null || !delta.isDefinite()
                    || !"scoreboard".equals(delta.source)) continue;

            // Guess awards are 3, 2, then repeatable 1. A definite non-builder
            // award therefore establishes a lower bound on how many guessers
            // already scored before the first chat message seen after reconnect.
            if (delta.value == 3) scoreboardRankFloor = Math.max(scoreboardRankFloor, 1);
            else if (delta.value == 2) scoreboardRankFloor = Math.max(scoreboardRankFloor, 2);
            else if (delta.value == 1) scoreboardRankFloor = Math.max(scoreboardRankFloor, 3);
            else continue;
            scoreboardGuessers.add(otherIgn);
        }

        int missingScoreboardGuessers = 0;
        for (String scoreboardIgn : scoreboardGuessers) {
            if (!priorGuessers.contains(scoreboardIgn)) missingScoreboardGuessers++;
        }
        int earliestPossibleOrder = Math.max(scoreboardRankFloor,
                orderZeroBased + missingScoreboardGuessers);
        Set<Integer> options = new TreeSet<Integer>();
        options.add(1);
        if (earliestPossibleOrder <= 1) options.add(2);
        if (earliestPossibleOrder <= 0) options.add(3);
        return options;
    }

    public static final class RoundScoreData {
        public final Map<String, ScoreDelta> scoreDeltas = new HashMap<String, ScoreDelta>();
        public final List<String> correctGuesses = new ArrayList<String>();
        public final List<GameEvent> events = new ArrayList<GameEvent>();
        public String builder;
        public String theme;
        public String langCode;
        public String[] builderThemeOptions;
        public boolean dataGap;
    }

    public static final class GameEvent {
        public final long timestamp;
        public final String type;
        public final String detail;

        public GameEvent(long timestamp, String type, String detail) {
            this.timestamp = timestamp;
            this.type = type;
            this.detail = detail;
        }
    }

    public static final class RankEntry {
        public final int place;
        public final String ign;
        public final int score;

        public RankEntry(int place, String ign, int score) {
            this.place = place;
            this.ign = ign;
            this.score = score;
        }
    }

    public static final class FinalizationResult {
        public final Map<String, Integer> unknownResidual;

        public FinalizationResult(Map<String, Integer> unknownResidual) {
            this.unknownResidual = unknownResidual;
        }
    }
}
