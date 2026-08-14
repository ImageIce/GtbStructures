package com.gtbstructures.stats;

import com.gtbstructures.translation.TranslationData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Computes per-round score deltas for every player from chat events, scoreboard
 * observations, and the post-game leaderboard. Replaces the old "snapshot what
 * the sidebar happens to show" approach.
 */
public final class ScoreReconciler {

    private ScoreReconciler() {}

    // ── Per-round writers ───────────────────────────────────────────────

    /**
     * Record a guesser's delta. Hypixel awards 3/2/1/1/1... in order of correct guess
     * (1st = 3, 2nd = 2, everyone after = 1). `orderZeroBased` is the index of this
     * guess within the round's correct-guess list (0-based).
     */
    public static void recordGuessDelta(GameStats.RoundData r, String ign, int orderZeroBased) {
        if (r == null || ign == null || ign.isEmpty()) return;
        int pts;
        if (orderZeroBased == 0) pts = 3;
        else if (orderZeroBased == 1) pts = 2;
        else pts = 1;
        // Don't overwrite a scoreboard-confirmed value with a guess heuristic,
        // except for stale zeroes captured before Hypixel's score row updated.
        ScoreDelta existing = r.scoreDeltas.get(ign);
        if (existing != null && existing.isDefinite() && "scoreboard".equals(existing.source) && existing.value != 0) return;

        if (hasReconnectGapBeforeGuess(r)) {
            r.scoreDeltas.put(ign, ScoreDelta.fork(guessOptionsAfterReconnect(r, ign, orderZeroBased),
                    "guess_rejoin_gap"));
            return;
        }
        r.scoreDeltas.put(ign, ScoreDelta.definite(pts, "guess"));
    }

    public static void recordExplicitGuessDelta(GameStats.RoundData r, String ign, int points, String source) {
        if (r == null || ign == null || ign.isEmpty()) return;
        if (points < 1 || points > 3) return;
        r.scoreDeltas.put(ign, ScoreDelta.definite(points,
                source == null || source.isEmpty() ? "explicit_guess" : source));
    }

    /**
     * Compute the builder's delta from the round's revealed theme + locked
     * round-build-start language. Stores the result under the builder's IGN.
     * Falls back gracefully if either is missing.
     *
     * If the local player was the builder and the chest GUI's theme options were
     * captured (slots 11/13/15 = 1pt/2pt/3pt left-to-right), match the revealed
     * theme against those options for an authoritative point value (works in any
     * language — bypasses translation lookup entirely).
     */
    public static void computeBuilderDelta(GameStats.RoundData r) {
        if (r == null || r.builder == null || r.builder.isEmpty()) return;
        if (r.theme == null || r.theme.isEmpty()) return;

        // Hypixel only awards builder theme points when at least one player guessed
        // correctly during the round. If nobody guessed correctly, builder scores 0
        // regardless of whether the round was skipped, the builder left, or the
        // timer simply expired — matching GTU's onThemeUpdate / onCorrectGuess logic.
        if (r.correctGuesses == null || r.correctGuesses.isEmpty()) {
            r.scoreDeltas.put(r.builder, ScoreDelta.definite(0, "no_correct_guesses"));
            return;
        }

        // If a scoreboard observation has already locked the builder's delta this round, leave it alone.
        ScoreDelta existing = r.scoreDeltas.get(r.builder);
        if (existing != null && existing.isDefinite() && "scoreboard".equals(existing.source)) return;

        // Authoritative path: local player is the builder, GUI options were captured.
        // Slot order is fixed: index 0 = 1pt, 1 = 2pt, 2 = 3pt. If the user didn't pick,
        // the 2pt option is auto-selected, so the revealed theme is always one of the three.
        if (r.builderThemeOptions != null && r.builderThemeOptions.length >= 3) {
            for (int i = 0; i < 3; i++) {
                String opt = r.builderThemeOptions[i];
                if (opt != null && opt.equalsIgnoreCase(r.theme)) {
                    r.scoreDeltas.put(r.builder, ScoreDelta.definite(i + 1, "builder_gui"));
                    return;
                }
            }
        }

        Set<Integer> options = TranslationData.lookupBuilderPoints(r.langCode, r.theme);
        if (options.isEmpty()) {
            // Unknown — leave unrecorded; finalizer will treat as residual.
            return;
        }
        r.scoreDeltas.put(r.builder, ScoreDelta.fork(options, "builder"));
    }

    /**
     * Crosscheck path: when the live scoreboard `Theme:` line is observed alongside
     * the live language, use it to lock or refine the builder's delta. The
     * scoreboard theme syncs live with whatever language is currently selected,
     * so an "en" snapshot is the authoritative English theme name regardless of
     * what `r.langCode` was at ROUND_BUILD start.
     */
    public static void crosscheckBuilderFromScoreboard(GameStats.RoundData r,
                                                       String liveLangCode,
                                                       String scoreboardTheme) {
        if (r == null || r.builder == null || scoreboardTheme == null || scoreboardTheme.isEmpty()) return;
        ScoreDelta existing = r.scoreDeltas.get(r.builder);
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
            r.scoreDeltas.put(r.builder, ScoreDelta.fork(options, "builder"));
            return;
        }
        // Intersect existing fork with crosscheck options
        Set<Integer> intersection = new TreeSet<Integer>();
        for (Integer v : options) {
            if (existing.canBe(v)) intersection.add(v);
        }
        if (intersection.isEmpty()) return; // crosscheck disagrees — keep original, conservative
        if (intersection.size() == 1) {
            r.scoreDeltas.put(r.builder, ScoreDelta.definite(intersection.iterator().next(), existing.source));
        } else if (!existing.isDefinite()) {
            r.scoreDeltas.put(r.builder, ScoreDelta.fork(intersection, existing.source));
        }
    }

    /**
     * Observe a player's cumulative score on the side scoreboard. If their score
     * grew since the last confirmed cumulative (`priorCumulative`), the difference
     * is the authoritative delta for THIS round. Locks the delta as definite and
     * collapses any matching fork already recorded.
     *
     * <p>If the observed delta exceeds 3 (max possible per-round score), the
     * delta spans multiple missed rounds and cannot honestly be attributed to
     * this round alone. Instead:
     * <ul>
     *   <li>Place a fork {0,1,2,3} on the round (uncertainty about its contribution).</li>
     *   <li>Add the full observed growth to {@code residualSink}, the per-player
     *       mid-game unattributed residual surfaced by the panel and folded into
     *       finalizeWithLeaderboard.</li>
     * </ul>
     * Note: we do NOT residualize on {@code r.dataGap} alone. A scoreboard
     * observation of growth ≤ 3 is reliable even if we missed chat events —
     * the cumulative score on screen is authoritative. dataGap only flags chat
     * uncertainty, not scoreboard uncertainty.
     */
    public static void observeScoreboard(GameStats.RoundData r, String ign,
                                         int observedCumulative, Integer priorCumulative,
                                         Map<String, Integer> residualSink) {
        if (priorCumulative == null) return;
        observeScoreboard(r, ign, observedCumulative, priorCumulative.intValue(), residualSink);
    }

    public static void observeScoreboard(GameStats.RoundData r, String ign,
                                         int observedCumulative, int priorCumulative,
                                         Map<String, Integer> residualSink) {
        if (r == null || ign == null || ign.isEmpty()) return;
        int delta = observedCumulative - priorCumulative;
        if (delta < 0) return; // scoreboard didn't grow this round

        // Oversize delta: spans more than one round's worth of points; cannot
        // honestly lock to this round alone. Fork it and bank as residual.
        if (delta > 3) {
            ScoreDelta existing = r.scoreDeltas.get(ign);
            // Only accumulate residual on the first observation for this player/round.
            // Without this guard, every tick (~0.5s) re-adds the full delta to the
            // residual sink, causing unbounded score inflation.
            if (residualSink != null && delta > 0 && existing == null) {
                Integer cur = residualSink.get(ign);
                int next = (cur == null ? 0 : cur) + delta;
                residualSink.put(ign, next);
            }
            if (existing == null || !existing.isDefinite()) {
                Set<Integer> opts = new TreeSet<Integer>();
                opts.add(0); opts.add(1); opts.add(2); opts.add(3);
                r.scoreDeltas.put(ign, ScoreDelta.fork(opts, "scoreboard_residual"));
            }
            return;
        }

        ScoreDelta existing = r.scoreDeltas.get(ign);
        if (existing != null && existing.isDefinite()) {
            // Already locked. If the scoreboard shows delta=0 but we already have
            // a non-scoreboard-sourced definite (e.g. from guess/builder), don't
            // overwrite — the scoreboard simply hasn't updated yet. For conflicting
            // non-zero scoreboard observations, trust the scoreboard as authoritative.
            if (delta == 0 && !"scoreboard".equals(existing.source)) {
                return;
            }
            if (existing.value != delta || !"scoreboard".equals(existing.source)) {
                r.scoreDeltas.put(ign, ScoreDelta.definite(delta, "scoreboard"));
            }
            return;
        }
        // A matching cumulative-score subtraction is authoritative even when it
        // collapses a theme-derived fork. Preserve that provenance so consumers
        // such as theme-point reveal can distinguish it from inference alone.
        if (existing != null && !existing.isDefinite() && existing.canBe(delta)) {
            r.scoreDeltas.put(ign, ScoreDelta.definite(delta, "scoreboard"));
            return;
        }
        // No prior entry, or delta not in fork — record as scoreboard-sourced definite.
        r.scoreDeltas.put(ign, ScoreDelta.definite(delta, "scoreboard"));
    }

    /** Back-compat overload — no residual sink. Tests still call this. */
    public static void observeScoreboard(GameStats.RoundData r, String ign,
                                         int observedCumulative, int priorCumulative) {
        observeScoreboard(r, ign, observedCumulative, priorCumulative, null);
    }

    // ── End-of-game finalizer ───────────────────────────────────────────

    public static class FinalizationResult {
        public final Map<String, Integer> unknownResidual; // ign -> leftover residual
        public FinalizationResult(Map<String, Integer> unknownResidual) {
            this.unknownResidual = unknownResidual;
        }
    }

    /**
     * For each player on the final leaderboard:
     *   1. Sum their definite per-round deltas.
     *   2. residual = finalScore - definiteSum.
     *   3. Try to pick one value from each unresolved fork that sums to `residual`.
     *      If a unique solution exists, collapse those forks to definites.
     *   4. Any leftover residual (or any data-gap rounds for the player) -> unknownResidual.
     * Players not on the leaderboard (left mid-game) are untouched.
     */
    public static FinalizationResult finalizeWithLeaderboard(List<GameStats.RoundData> rounds,
                                                             List<GameStats.RankEntry> rankings) {
        return finalizeWithLeaderboard(rounds, rankings, null);
    }

    public static FinalizationResult finalizeWithLeaderboard(List<GameStats.RoundData> rounds,
                                                             List<GameStats.RankEntry> rankings,
                                                             Map<String, Integer> midGameResidual) {
        Map<String, Integer> unknown = new HashMap<String, Integer>();
        if (rounds == null || rankings == null || rankings.isEmpty()) {
            return new FinalizationResult(unknown);
        }

        for (GameStats.RankEntry rk : rankings) {
            if (rk == null || rk.ign == null) continue;
            int finalScore = rk.score;
            int definiteSum = 0;
            boolean hasGap = false;
            List<int[]> forkRoundIdxAndOptions = new ArrayList<int[]>();
            List<List<Integer>> forks = new ArrayList<List<Integer>>();

            for (int i = 0; i < rounds.size(); i++) {
                GameStats.RoundData r = rounds.get(i);
                if (r.dataGap) hasGap = true;
                ScoreDelta d = r.scoreDeltas.get(rk.ign);
                if (d == null) continue;
                if (d.isDefinite()) {
                    definiteSum += d.value;
                } else {
                    forkRoundIdxAndOptions.add(new int[]{i});
                    forks.add(d.fork);
                }
            }

            // Subtract pre-attributed mid-game residual from the brute-force target.
            // What's left after fork resolution becomes the truly unknown residual.
            int residualBucket = (midGameResidual != null && midGameResidual.get(rk.ign) != null)
                    ? midGameResidual.get(rk.ign) : 0;
            int residual = finalScore - definiteSum - residualBucket;

            if (forks.isEmpty()) {
                if (residual != 0 && hasGap) {
                    unknown.put(rk.ign, residual);
                } else if (residual != 0) {
                    // No forks but still off — likely a missed scoreboard event.
                    unknown.put(rk.ign, residual);
                }
                continue;
            }

            // Brute-force: enumerate every combination of fork picks; collect those summing to residual.
            List<int[]> solutions = new ArrayList<int[]>();
            int[] pick = new int[forks.size()];
            enumerate(forks, 0, 0, residual, pick, solutions);

            if (solutions.size() == 1) {
                int[] sol = solutions.get(0);
                for (int k = 0; k < forks.size(); k++) {
                    int roundIdx = forkRoundIdxAndOptions.get(k)[0];
                    GameStats.RoundData r = rounds.get(roundIdx);
                    ScoreDelta old = r.scoreDeltas.get(rk.ign);
                    if (old != null) {
                        r.scoreDeltas.put(rk.ign, ScoreDelta.definite(sol[k], old.source));
                    }
                }
            } else if (solutions.isEmpty()) {
                // No combination matches — record the whole residual as unknown.
                if (residual != 0) unknown.put(rk.ign, residual);
            } else {
                // Multiple solutions — partial info only. If certain positions agree across all
                // solutions, lock those individually.
                for (int k = 0; k < forks.size(); k++) {
                    int v0 = solutions.get(0)[k];
                    boolean allAgree = true;
                    for (int s = 1; s < solutions.size(); s++) {
                        if (solutions.get(s)[k] != v0) { allAgree = false; break; }
                    }
                    if (allAgree) {
                        int roundIdx = forkRoundIdxAndOptions.get(k)[0];
                        GameStats.RoundData r = rounds.get(roundIdx);
                        ScoreDelta old = r.scoreDeltas.get(rk.ign);
                        if (old != null) {
                            r.scoreDeltas.put(rk.ign, ScoreDelta.definite(v0, old.source));
                        }
                    }
                }
                // Anything left ambiguous stays as a fork; nothing added to unknown.
            }
        }

        return new FinalizationResult(unknown);
    }

    private static void enumerate(List<List<Integer>> forks, int idx, int sum, int target,
                                  int[] pick, List<int[]> solutions) {
        if (idx == forks.size()) {
            if (sum == target) solutions.add(pick.clone());
            return;
        }
        for (Integer v : forks.get(idx)) {
            pick[idx] = v;
            enumerate(forks, idx + 1, sum + v, target, pick, solutions);
        }
    }

    private static String normalize(String code) {
        return TranslationData.normalizeLangCode(code);
    }

    private static boolean hasReconnectGapBeforeGuess(GameStats.RoundData r) {
        if (r == null || r.events == null || r.events.isEmpty()) return false;
        for (GameStats.GameEvent event : r.events) {
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

    private static Set<Integer> guessOptionsAfterReconnect(GameStats.RoundData r,
                                                            String ign,
                                                            int orderZeroBased) {
        int scoreboardRankFloor = 0;
        Set<String> scoreboardGuessers = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        for (Map.Entry<String, ScoreDelta> entry : r.scoreDeltas.entrySet()) {
            String otherIgn = entry.getKey();
            ScoreDelta delta = entry.getValue();
            if (otherIgn == null || otherIgn.equalsIgnoreCase(ign)
                    || (r.builder != null && otherIgn.equalsIgnoreCase(r.builder))) continue;
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
            if (!appearsAmongPriorCorrectGuesses(r, scoreboardIgn, orderZeroBased)) {
                missingScoreboardGuessers++;
            }
        }
        int earliestPossibleOrder = Math.max(scoreboardRankFloor,
                orderZeroBased + missingScoreboardGuessers);
        Set<Integer> options = new TreeSet<Integer>();
        options.add(1);
        if (earliestPossibleOrder <= 1) options.add(2);
        if (earliestPossibleOrder <= 0) options.add(3);
        return options;
    }

    private static boolean appearsAmongPriorCorrectGuesses(GameStats.RoundData r,
                                                            String ign,
                                                            int orderZeroBased) {
        if (r.correctGuesses == null || ign == null) return false;
        int limit = Math.min(Math.max(0, orderZeroBased), r.correctGuesses.size());
        for (int i = 0; i < limit; i++) {
            GameStats.CorrectGuessEntry guess = r.correctGuesses.get(i);
            if (guess != null && guess.ign != null && guess.ign.equalsIgnoreCase(ign)) return true;
        }
        return false;
    }

    /** Sum a player's definite deltas and fork-mins across all rounds prior to the given round. */
    public static int priorCumulative(List<GameStats.RoundData> rounds, String ign,
                                      GameStats.RoundData currentRound) {
        if (rounds == null || ign == null) return 0;
        int sum = 0;
        for (GameStats.RoundData r : rounds) {
            if (r == currentRound) break;
            ScoreDelta d = r.scoreDeltas.get(ign);
            if (d == null) continue;
            if (d.isDefinite()) {
                sum += d.value;
            } else if (d.fork != null && !d.fork.isEmpty()) {
                int fMin = d.fork.get(0);
                for (Integer v : d.fork) if (v < fMin) fMin = v;
                sum += fMin; // use fork-min as the conservative baseline
            }
        }
        return sum;
    }

    /** Sum a player's prior deltas only when every prior contribution is known. */
    public static Integer knownPriorCumulative(List<GameStats.RoundData> rounds, String ign,
                                               GameStats.RoundData currentRound) {
        if (rounds == null || ign == null) return Integer.valueOf(0);
        int sum = 0;
        for (GameStats.RoundData r : rounds) {
            if (r == currentRound) break;
            ScoreDelta d = r.scoreDeltas.get(ign);
            if (d == null) {
                if (r.dataGap) return null;
                continue;
            }
            if (!d.isDefinite()) return null;
            sum += d.value;
        }
        return Integer.valueOf(sum);
    }
}
