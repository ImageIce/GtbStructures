package com.gtbstructures.scoreboard;

import com.gtbstructures.GtbStructuresSettings;
import com.gtbstructures.player.PlayerMetaCache;
import com.gtbstructures.stats.GameStats;
import com.gtbstructures.translation.TranslationData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Platform-neutral tracker core ported from the GtbStructures 1.8.9 behavior.
 * Loader code calls these methods from modern parse points so scoreboard
 * bookkeeping stays outside renderer code.
 */
public final class GameTracker implements GameStats.GtbHooks {

    public static final GameTracker INSTANCE = new GameTracker();

    /** Null between games. The renderer must check before reading. */
    public GtbGame game;

    /** Monotonic tick counter for the build spinner. */
    public static int tickCounter;

    /** Floor inferences (off-leaderboard fork pruning, first-round zero
     *  settlement) require a sidebar snapshot whose roster and floor survived
     *  unchanged this many ticks. One-frame redraw dropouts otherwise produce
     *  a bogus low floor that irreversibly collapses fork uncertainty. */
    private static final int STABLE_FLOOR_TICKS = 20;
    private Map<String, Integer> pendingFloorScores;
    private int pendingFloorValue;
    private int pendingFloorSinceTick;

    private GameTracker() {}

    // Lifecycle

    public void onGameStart(List<String> players, String localIgn) {
        this.game = null;
        resetPendingFloor();
        java.util.ArrayList<String> roster = new java.util.ArrayList<String>();
        if (players != null) {
            for (String ign : players) {
                if (ign == null || ign.isEmpty()) continue;
                if (!containsIgnoreCase(roster, ign)) roster.add(ign);
            }
        }
        if (localIgn != null && !localIgn.isEmpty() && !containsIgnoreCase(roster, localIgn)) {
            roster.add(localIgn);
        }
        GtbGame g = new GtbGame();
        g.totalRounds = roster.size();
        for (String ign : roster) {
            if (ign == null || ign.isEmpty()) continue;
            GtbPlayer p = new GtbPlayer(ign);
            p.points = new PointEntry[g.totalRounds];
            for (int i = 0; i < g.totalRounds; i++) p.points[i] = PointEntry.zero();
            p.isUser = ign.equals(localIgn);
            p.presentAtGameStart = true;
            p.firstSeenTick = tickCounter;
            g.players.put(ign, p);
        }
        g.state = GtbGame.State.SETUP;
        this.game = g;


    }

    public void onTickActivity() {
        if (game == null) return;
        for (GtbPlayer p : game.players.values()) p.inactiveTicks++;
        tickCounter++;
        applyStableFloorIfReady();

    }

    public void onTimerUpdate(String timer) {
        if (game == null) return;
        game.currentTimer = timer == null ? "" : timer;
    }

    // Round flow

    @Override
    public void onRoundPre() {
        if (game == null) {
            onGameStart(null, null);
            if (game == null) return;
        }
        if (game.pendingRejoinFromUserBuildRound) {
            clearRejoinRoundScoreUncertainty();
            game.rejoinGuessOrderUncertain = false;
            game.rejoinBuilderChangedBeforeRoundHeader = false;
        }
        if (game.totalRounds <= 0) {
            game.totalRounds = Math.max(1, game.players.size());
            for (GtbPlayer p : game.players.values()) {
                ensurePointsCapacity(p, game.totalRounds);
            }
        }
        game.currentTheme = "";
        game.state = GtbGame.State.ROUND_PRE;
    }

    public void onRoundStart(int currentRound, int totalRounds) {
        if (game == null) {
            onGameStart(null, null);
            if (game == null) return;
        }
        boolean previousRoundEnded = game.state == GtbGame.State.ROUND_END;
        if (currentRound > 0 && game.currentRound == currentRound) {
            updateKnownRoundCount(totalRounds);
            game.roundCountUnknown = false;
            game.rejoinGuessOrderUncertain = false;
            resetRejoinGuessEvidence();
            game.rejoinBuilderChangedBeforeRoundHeader = false;
            game.suppressPointBadgesUntilRoundHeader = false;
            clearRejoinRoundScoreUncertainty();
            game.state = GtbGame.State.ROUND_BUILD;
            if (currentRound == 1) {
                game.initialRoundHeaderConfirmed = true;
                resolveConfirmedInitialRoundIdentities();
            }
            if (game.currentBuilder != null && game.currentBuilder.buildRound != currentRound) {
                game.currentBuilder.buildRound = currentRound;
                onBuilderAssigned(game.currentBuilder);
            }
            applyStableFloorIfReady();
            return;
        }
        int previousRound = game.currentRound;
        if (previousRound > 0 && currentRound > previousRound
                && game.pendingRejoinFromRound < 0) {
            materializeRejoinRoundUncertainty(previousRound);
        }
        // Consume any pending rejoin detection now that we have authoritative round info.
        boolean freshRoundResolvedPendingRejoin = game.pendingRejoinFromRound >= 0;
        if (freshRoundResolvedPendingRejoin) {
            int from = game.pendingRejoinFromRound;
            int missed = currentRound - from - 1;
            if (missed >= 1) {
                injectMissedRoundForks(from, currentRound, missed);

            }
            game.pendingRejoinFromRound = -1;
            game.pendingRejoinFromUserBuildRound = false;
        } else if (previousRound > 0 && currentRound > previousRound + 1) {
            int missed = currentRound - previousRound - 1;
            injectMissedRoundForks(previousRound, currentRound, missed);

        }
        game.currentRound = currentRound;
        updateKnownRoundCount(totalRounds);
        game.oneSecondAlertReached = false;
        game.correctGuessesThisRound = 0;
        game.currentTheme = "";
        game.currentHintStructure = "";
        game.ruledOutEnglishThemesThisRound.clear();
        game.wrongGuessesThisRound.clear();
        game.roundCountUnknown = false;
        game.rejoinGuessOrderUncertain = false;
        resetRejoinGuessEvidence();
        game.rejoinBuilderChangedBeforeRoundHeader = false;
        game.suppressPointBadgesUntilRoundHeader = false;
        game.roundSkippedThisRound = false;
        clearPendingBuilderScoreMarkers();
        snapshotRoundStartScores();
        if (freshRoundResolvedPendingRejoin) {
            clearRejoinRoundScoreUncertainty();
        }
        game.roundStartScoreBaselinePending = previousRoundEnded
                && previousRound > 0 && currentRound > previousRound;
        promoteAwayUnbuiltLeavers(totalRounds);
        game.state = GtbGame.State.ROUND_BUILD;
        if (currentRound == 1) {
            game.initialRoundHeaderConfirmed = true;
            resolveConfirmedInitialRoundIdentities();
        }
        // Set buildRound only when the currentBuilder is one announced for THIS
        // round (PRE-phase onBuilderChange). If currentBuilder is stale from the
        // previous round (e.g. abrupt round change with no PRE-phase
        // announcement), it was cleared at onRoundEnd so this no-ops; the next
        // onBuilderChange (in ROUND_BUILD) will set buildRound correctly.
        if (game.currentBuilder != null && game.currentBuilder.buildRound != currentRound) {
            game.currentBuilder.buildRound = currentRound;
            onBuilderAssigned(game.currentBuilder);
        }
        // Apply any stable floor evidence right away: with a visible 0 row the
        // sorted-board deduction settles freshly injected gap forks to exact
        // zeros within this same call, so they never render as uncertainty.
        applyStableFloorIfReady();


    }

    @Override
    public void onInferredRoundStart(int currentRound, int totalRounds,
                                     boolean guessOrderUncertain) {
        boolean hadPendingRejoin = game != null && game.pendingRejoinFromRound >= 0;
        onRoundStart(currentRound, totalRounds);
        if (game == null) return;
        if (hadPendingRejoin) {
            markCurrentRejoinRoundScoreUncertainty();
        }
        game.roundStartScoreBaselinePending = false;
        game.rejoinGuessOrderUncertain = guessOrderUncertain;
        resetRejoinGuessEvidence();
    }

    /** Bump per-player missed-round counters and inject {0,1,2,3} forks for fully
     *  unobserved rounds during a disconnect window. Local user is exempt
     *  (we observed those rounds, just not whose builder/who guessed). */
    private void injectMissedRoundForks(int leaveRound, int currentRound, int missed) {
        java.util.Set<Integer> open = new java.util.HashSet<Integer>();
        open.add(0); open.add(1); open.add(2); open.add(3);
        for (GtbPlayer p : game.players.values()) {
            if (p.isUser) continue;
            if (isConfirmedLeaver(p)) continue;
            p.roundsMissedDuringDisconnect += missed;
            if (p.points == null) continue;
            // Round indices [leaveRound .. currentRound-2] inclusive (1-indexed rounds).
            for (int r = leaveRound; r < currentRound - 1; r++) {
                if (r >= 0 && r < p.points.length
                        && p.points[r].kind == PointEntry.Kind.ZERO) {
                    p.points[r] = PointEntry.fork(open);
                }
            }
        }
    }

    public void onBuilderChange(String previous, String current) {
        if (game == null) {

            return;
        }
        if (current == null) {
            game.currentBuilder = null;

            return;
        }
        if (game.state != GtbGame.State.ROUND_PRE) {
            ensureImplicitFirstRound();
        }
        GtbPlayer p = ensurePlayer(current, "builder");
        if (p == null) {

            return;
        }
        resolveConfirmedInitialRoundIdentity(p);
        if (game.roundCountUnknown && game.pendingRejoinFromRound >= 0
                && game.currentBuilder == null) {
            resetRoundLocalAfterRejoinBuilderChange();
        }
        if (game.currentBuilder != null && game.currentBuilder != p) {
            resetRejoinGuessEvidence();
        }
        game.currentBuilder = p;
        // Set buildRound only when the round is *already* underway (state ==
        // ROUND_BUILD). For PRE/SETUP, currentRound still points at the old
        // round number; let onRoundStart handle it. For abrupt round changes
        // where onRoundStart fires before the new builder is announced, this
        // catches the late assignment.
        if (game.state == GtbGame.State.ROUND_BUILD && game.currentRound > 0) {
            p.buildRound = game.currentRound;
        }
        onBuilderAssigned(p);

    }

    public void onCorrectGuess(List<String> guessers) {
        if (game != null && game.currentRound == 0) {
            ensureImplicitFirstRound();
        }
        if (game == null || game.currentRound == 0 || guessers == null) {

            return;
        }
        if (isAbandonedLocalBuildRound()) return;
        int r = game.currentRound - 1;
        if (r < 0) return;
        StringBuilder awarded = new StringBuilder();
        boolean awardedAnyNewGuess = false;
        for (String ign : guessers) {
            if (ign == null) continue;
            GtbPlayer p = ensurePlayer(ign, "correct_guess");
            if (p == null) {
                awarded.append(" SKIP-not-in-game(").append(ign).append(")");
                continue;
            }
            resolveConfirmedInitialRoundIdentity(p);
            if (game.rejoinGuessOrderUncertain
                    && indexOfIgnoreCase(game.rejoinObservedGuessOrder, ign) < 0) {
                game.rejoinObservedGuessOrder.add(p.name);
            }
            PointEntry award = correctGuessAwardEntry();
            if (r < p.points.length && p.points[r].kind == PointEntry.Kind.ZERO) {
                p.points[r] = award;
                p.suppressedPointBadgeRound = 0;
                if (award.kind == PointEntry.Kind.DEFINITE && award.value > 0) {
                    advanceRetainedScoreFromRoundStart(p, award.value, false);
                    clearCurrentRejoinRoundUncertainty(p);
                }
                awardedAnyNewGuess = true;
                if (award.max() > 0) {
                    p.everScored = true;
                    markMessageOrScoreEvidence(p);
                }
                awarded.append(" ").append(ign).append("=+").append(award.max());
            } else {
                awarded.append(" SKIP-already-set(").append(ign).append(")");
            }
            onActivity(p);
            game.correctGuessesThisRound++;
        }
        awardBuilderIfReady();
        awardBuilderFromEnglishHintIfReady();
        if (awardedAnyNewGuess) markBuilderPendingIfNeeded();

    }

    private PointEntry correctGuessAwardEntry() {
        int observedIndex = game == null ? 0 : game.correctGuessesThisRound;
        if (game == null || !game.rejoinGuessOrderUncertain) {
            return PointEntry.definite(Math.max(1, 3 - observedIndex));
        }

        int maxAward = Math.max(1, 3 - game.rejoinPriorGuessFloor - observedIndex);
        if (maxAward == 1) return PointEntry.definite(1);
        java.util.Set<Integer> options = new java.util.HashSet<Integer>();
        for (int v = 1; v <= maxAward; v++) {
            options.add(Integer.valueOf(v));
        }
        return PointEntry.fork(options);
    }

    public void onCorrectGuessScore(String ign, int points) {
        if (game == null || ign == null || ign.isEmpty()) return;
        if (points < 1 || points > 3 || game.currentRound <= 0) return;
        if (isAbandonedLocalBuildRound()) return;
        GtbPlayer p = ensurePlayer(ign, "self_guess_points");
        if (p == null || p == game.currentBuilder) return;
        resolveConfirmedInitialRoundIdentity(p);
        int r = game.currentRound - 1;
        if (r < 0) return;
        ensurePointsCapacity(p, Math.max(game.totalRounds, game.currentRound));
        if (r >= p.points.length) return;
        p.points[r] = PointEntry.definite(points);
        p.suppressedPointBadgeRound = 0;
        advanceRetainedScoreFromRoundStart(p, points, true);
        resolveRejoinGuessOrderFromExactDelta(p, points);
        p.everScored = true;
        onActivity(p);
        markMessageOrScoreEvidence(p);
    }

    public void onHintUpdate(String hint, String structure, String langCode) {
        if (game == null) return;
        game.currentHintStructure = structure == null ? "" : structure;
        updateLanguage(langCode);
        if (game.correctGuessesThisRound > 0) {
            awardBuilderFromEnglishHintIfReady();
        }
    }

    public void onThemeUpdate(String theme, String langCode) {
        if (game == null) {

            return;
        }
        String prevTheme = game.currentTheme;
        String prevLang = game.currentLangCode;
        game.currentTheme = theme == null ? "" : theme;
        updateLanguage(langCode);
        if (game.correctGuessesThisRound > 0) {
            applyGuessExclusionsIfCurrentThemeIsMultiword();
            awardBuilderIfReady();
        }

    }

    @Override
    public void onPlayerGuess(String ign, String text) {
        if (game == null || game.currentRound <= 0 || text == null || text.trim().isEmpty()) return;
        if (game.currentBuilder != null && game.currentBuilder.name != null
                && game.currentBuilder.name.equalsIgnoreCase(ign)) {
            return;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return;
        game.wrongGuessesThisRound.add(trimmed);
        if (!applyGuessExclusionsIfCurrentThemeIsMultiword()) return;
        awardBuilderIfReady();
    }

    private boolean applyGuessExclusionsIfCurrentThemeIsMultiword() {
        if (game == null || game.wrongGuessesThisRound.isEmpty()) return false;
        if (!TranslationData.isDisplayedMultiwordTheme(game.currentLangCode, game.currentTheme)) {
            return false;
        }
        Set<String> ruledOut = TranslationData.ruledOutEnglishThemesFromGuesses(
                game.wrongGuessesThisRound);
        if (ruledOut.isEmpty()) return false;
        int before = game.ruledOutEnglishThemesThisRound.size();
        game.ruledOutEnglishThemesThisRound.addAll(ruledOut);
        return game.ruledOutEnglishThemesThisRound.size() != before;
    }

    private void updateLanguage(String langCode) {
        if (game == null) return;
        String normalized = langCode == null ? "" : langCode.trim();
        if (normalized.isEmpty()) return;
        game.currentLangCode = normalized;
        GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
        if (!normalized.equals(settings.lastLangCode)) {
            settings.lastLangCode = normalized;
            settings.save();
        }
    }

    public void onRoundEnd(boolean skipped) {
        if (game == null) {

            return;
        }
        if (game.state == GtbGame.State.ROUND_END) return;
        boolean actuallySkipped = skipped
                || game.roundSkippedThisRound
                || (game.currentBuilder != null && game.currentBuilder.isUser && game.userRoundSkipped);
        // Round-based "away" counter: per player, increment if the round just
        // ended and the player neither chatted nor correctly guessed. Reset
        // once they speak (handled in onActivity). Skipped if absent (local
        // user left during the round).
        for (GtbPlayer p : game.players.values()) {
            if (p.absentThisRound) {
                // Player was not present this round; do not count it. Reset
                // flags for next round's bookkeeping.
            } else if (actuallySkipped) {
                if (p.spokeThisRound) p.silentRoundsConsecutive = 0;
            } else if (!p.spokeThisRound) {
                p.silentRoundsConsecutive++;
            } else {
                p.silentRoundsConsecutive = 0;
            }
            p.spokeThisRound = false;
            p.absentThisRound = false;
        }
        // Clear currentBuilder so a stale reference from this round doesn't
        // pollute the next onRoundStart's buildRound assignment when an abrupt
        // round change skips the PRE-phase onBuilderChange announcement.
        // Keep unresolved pending builder-score markers until the next round
        // start; the revealed-theme message can arrive during ROUND_END after
        // the builder reference is cleared.
        game.currentBuilder = null;
        game.currentTheme = "";
        game.currentHintStructure = "";
        game.state = GtbGame.State.ROUND_END;
        game.userRoundSkipped = false;


    }

    public void onRoundSkipped() {
        if (game == null) {

            return;
        }
        if (game.currentBuilder != null) {
            restoreBuilderAssignmentOnlyActivity(game.currentBuilder);
            game.currentBuilder.absentThisRound = true;
            if (isKnownLeaver(game.currentBuilder.name)) {
                game.currentBuilder.leaverState = GtbPlayer.LeaverState.LEAVER;
            }
            if (game.state == GtbGame.State.ROUND_BUILD && game.currentRound > 0) {
                game.currentBuilder.buildRound = game.currentRound;
            } else if (game.state == GtbGame.State.ROUND_PRE) {
                game.currentBuilder.buildRound = Math.max(1, game.currentRound + 1);
            }
        }
        game.skippedRounds++;
        game.roundSkippedThisRound = true;

    }

    public void onGameEnd(Map<String, Integer> scores) {
        resolveUnresolvedIdentities(scores);


        game = null;
    }

    // Leaderboard / identity

    public void onTrueScoresUpdate(Map<String, Integer> observedScores) {
        if (game == null || observedScores == null) {

            return;
        }
        Map<String, Integer> previousVisible = game.visibleTrueScore == null
                ? new HashMap<String, Integer>()
                : new HashMap<String, Integer>(game.visibleTrueScore);
        Map<String, Integer> latest = game.latestTrueScore == null
                ? new HashMap<String, Integer>()
                : new HashMap<String, Integer>(game.latestTrueScore);
        Map<String, Integer> visible = new HashMap<String, Integer>();
        boolean closingScoreSnapshot = isClosingScoreSnapshot();
        boolean settleRoundStartBaseline = game.roundStartScoreBaselinePending
                && !closingScoreSnapshot;
        for (Map.Entry<String, Integer> e : observedScores.entrySet()) {
            GtbPlayer p = ensurePlayer(e.getKey(), "leaderboard");
            if (p == null) {
                continue;
            }
            Integer obs = e.getValue();
            if (obs == null) continue;
            Integer prior = scoreIgnoreCase(latest, e.getKey());
            int rawObserved = obs.intValue();
            if (!isPlausibleObservedScore(rawObserved)) {
                continue;
            }
            Integer authoritativeBuilderTotal = authoritativeBuilderTotal(p);
            int stableObserved;
            if (!closingScoreSnapshot && authoritativeBuilderTotal != null) {
                stableObserved = authoritativeBuilderTotal.intValue();
            } else {
                stableObserved = rawObserved;
                if (!closingScoreSnapshot && prior != null && prior.intValue() > stableObserved
                        && isPlausibleObservedScore(prior.intValue())) {
                    stableObserved = prior.intValue();
                }
                if (!isPlausibleObservedScore(stableObserved)) {
                    stableObserved = rawObserved;
                }
            }
            rememberObservedScore(p, stableObserved);
            String scoreKey = p.name == null ? e.getKey() : p.name;
            visible.put(scoreKey, Integer.valueOf(stableObserved));
            p.everOnLeaderboard = true;
            // Only mark activity when the leaderboard score actually grew.
            // The sidebar re-emits the same observation every tick; calling
            // onActivity on every emission resets silentRoundsConsecutive
            // for every leaderboard player every tick, which means the top-N
            // can never go "away". prior=null is treated as a 0 baseline so
            // a fresh appearance with non-zero points is counted as activity.
            int priorVal = prior == null ? 0 : prior.intValue();
            settleLateFirstRoundScore(p, stableObserved, prior);
            if (stableObserved > priorVal) {
                onActivity(p);
                p.everScored = true;
                markMessageOrScoreEvidence(p);
            }
            boolean absorbedAsRoundStartBaseline = settleRoundStartBaseline
                    && absorbRoundStartScoreBaseline(p, stableObserved);
            if (!absorbedAsRoundStartBaseline) {
                applyVisibleScoreDelta(p, stableObserved, prior,
                        scoreIgnoreCase(previousVisible, e.getKey()));
            }
            resolvePendingBuilderFromLeaderboard(p, stableObserved, prior);
            collapseForks(p, stableObserved);
            clearDisconnectScoreUncertainty(p);
            latest.put(scoreKey, Integer.valueOf(stableObserved));
        }
        game.visibleTrueScore = visible;
        game.latestTrueScore = latest;
        if (settleRoundStartBaseline && !visible.isEmpty()) {
            game.roundStartScoreBaselinePending = false;
        }
        // Floor inferences (off-leaderboard pruning / zero settlement) are
        // deferred until this snapshot proves stable; a one-frame redraw
        // dropout must never collapse fork uncertainty.
        updateStableFloor(visible);
        applyStableFloorIfReady();

    }

    @Override
    public void onAuthoritativeLowRosterSnapshot(Set<String> presentPlayers) {
        if (game == null || presentPlayers == null) return;
        Set<String> normalizedPresent = new HashSet<String>();
        for (String ign : presentPlayers) {
            if (ign != null && !ign.trim().isEmpty()) {
                normalizedPresent.add(ign.trim().toLowerCase(java.util.Locale.ROOT));
            }
        }
        if (normalizedPresent.isEmpty() || normalizedPresent.size() > 2) return;

        for (GtbPlayer player : game.players.values()) {
            if (player == null || player.name == null) continue;
            if (!normalizedPresent.contains(player.name.toLowerCase(java.util.Locale.ROOT))) {
                onConfirmedPlayerLeft(player.name);
            }
        }
    }

    @Override
    public void onConfirmedPlayerLeft(String ign) {
        if (game == null || ign == null) return;
        String key = findKeyIgnoreCase(game.players, ign);
        if (key == null) return;
        GtbPlayer player = game.players.get(key);
        if (player != null) player.leaverState = GtbPlayer.LeaverState.LEAVER;
    }

    private boolean isPlausibleObservedScore(int score) {
        if (score < 0) return false;
        if (game == null || game.roundCountUnknown || game.currentRound <= 0) return true;
        return score <= game.currentRound * 3;
    }

    public void onIdentityTransfer(String oldIgn, String newIgn) {
        if (game == null || oldIgn == null || newIgn == null) {

            return;
        }
        GtbPlayer p = removePlayerIgnoreCase(oldIgn);
        if (p == null) {

            return;
        }
        GtbPlayer shell = removePlayerIgnoreCase(newIgn);
        if (shell != null && shell != p) mergeIdentityShell(p, shell);
        p.name = newIgn;
        game.players.put(newIgn, p);
        if (game.currentBuilder == shell) game.currentBuilder = p;
        transferScoreKey(game.latestTrueScore, oldIgn, newIgn);
        transferScoreKey(game.roundStartTrueScore, oldIgn, newIgn);
        transferScoreKey(game.visibleTrueScore, oldIgn, newIgn);

    }

    private GtbPlayer removePlayerIgnoreCase(String ign) {
        if (game == null || ign == null) return null;
        String key = findKeyIgnoreCase(game.players, ign);
        return key == null ? null : game.players.remove(key);
    }

    private static boolean containsIgnoreCase(List<String> values, String key) {
        if (values == null || key == null) return false;
        for (String existing : values) {
            if (existing != null && existing.equalsIgnoreCase(key)) return true;
        }
        return false;
    }

    private static <V> String findKeyIgnoreCase(Map<String, V> map, String key) {
        if (map == null || key == null) return null;
        for (String existing : map.keySet()) {
            if (existing != null && existing.equalsIgnoreCase(key)) return existing;
        }
        return null;
    }

    private void mergeIdentityShell(GtbPlayer target, GtbPlayer shell) {
        target.isUser = target.isUser || shell.isUser;
        target.presentAtGameStart = target.presentAtGameStart || shell.presentAtGameStart;
        target.unresolvedIdentity = target.unresolvedIdentity && shell.unresolvedIdentity;
        target.everOnLeaderboard = target.everOnLeaderboard || shell.everOnLeaderboard;
        target.everScored = target.everScored || shell.everScored;
        if (target.rankColor == null) target.rankColor = shell.rankColor;
        if (target.emblem == null) target.emblem = shell.emblem;
        if (target.title == null) target.title = shell.title;
        if (target.firstSeenTick < 0 || (shell.firstSeenTick >= 0 && shell.firstSeenTick < target.firstSeenTick)) {
            target.firstSeenTick = shell.firstSeenTick;
        }
        target.lastMessageOrScoreTick = Math.max(target.lastMessageOrScoreTick, shell.lastMessageOrScoreTick);
        target.inactiveTicks = Math.min(target.inactiveTicks, shell.inactiveTicks);
        target.silentRoundsConsecutive = Math.min(target.silentRoundsConsecutive, shell.silentRoundsConsecutive);
        target.spokeThisRound = target.spokeThisRound || shell.spokeThisRound;
        target.absentThisRound = target.absentThisRound && shell.absentThisRound;
        if (target.leaverState == GtbPlayer.LeaverState.NORMAL) target.leaverState = shell.leaverState;
        target.roundsMissedDuringDisconnect = Math.max(target.roundsMissedDuringDisconnect, shell.roundsMissedDuringDisconnect);
        target.observedScoreFloor = Math.max(target.observedScoreFloor, shell.observedScoreFloor);
        target.preDisconnectScoreBaseline = Math.max(
                target.preDisconnectScoreBaseline, shell.preDisconnectScoreBaseline);
        target.disconnectUncertainRounds.addAll(shell.disconnectUncertainRounds);
        target.rejoinRoundScoreUncertain = target.rejoinRoundScoreUncertain
                || shell.rejoinRoundScoreUncertain;
        target.authoritativeBuilderScoreRound = Math.max(
                target.authoritativeBuilderScoreRound,
                shell.authoritativeBuilderScoreRound);
    }

    private void resolveUnresolvedIdentities(Map<String, Integer> finalScores) {
        if (game == null || finalScores == null || finalScores.isEmpty()) return;
        GtbPlayer[] snapshot = game.players.values().toArray(new GtbPlayer[game.players.size()]);
        for (GtbPlayer late : snapshot) {
            if (late == null || !late.unresolvedIdentity || late.name == null) continue;
            if (findKeyIgnoreCase(game.players, late.name) == null) continue;
            GtbPlayer match = null;
            for (GtbPlayer old : snapshot) {
                if (old == null || old == late || !old.presentAtGameStart || old.name == null) continue;
                if (!backupIdentityEvidencePasses(old, late, finalScores)) continue;
                if (match != null) {

                    match = null;
                    break;
                }
                match = old;
            }
            if (match == null) continue;
            boolean oldFinal = hasScoreIgnoreCase(finalScores, match.name);
            String from = oldFinal ? late.name : match.name;
            String to = oldFinal ? match.name : late.name;
            mergeFinalIdentity(from, to);

        }
    }

    private boolean backupIdentityEvidencePasses(GtbPlayer old, GtbPlayer late,
                                                 Map<String, Integer> finalScores) {
        boolean oldFinal = hasScoreIgnoreCase(finalScores, old.name);
        boolean lateFinal = hasScoreIgnoreCase(finalScores, late.name);
        if (oldFinal == lateFinal) return false;
        if (old.lastMessageOrScoreTick >= late.firstSeenTick) return false;

        boolean oldHigh = isMvpPlusPlusOrAbove(old.name);
        boolean lateHigh = isMvpPlusPlusOrAbove(late.name);
        if (oldHigh == lateHigh) return false;

        if (!old.everOnLeaderboard && !late.everOnLeaderboard) {
            boolean allFlagged = old.unresolvedIdentity && late.unresolvedIdentity;
            if (!allFlagged) {
                String finalIgn = oldFinal ? old.name : late.name;
                Integer finalScore = scoreIgnoreCase(finalScores, finalIgn);
                if (finalScore == null) return false;
                int min = old.totalMin() + late.totalMin();
                int max = old.totalMax() + late.totalMax();
                if (finalScore.intValue() < min || finalScore.intValue() > max) return false;
            }
        }
        return true;
    }

    private void mergeFinalIdentity(String fromIgn, String toIgn) {
        if (fromIgn == null || toIgn == null || fromIgn.equalsIgnoreCase(toIgn)) return;
        GtbPlayer source = removePlayerIgnoreCase(fromIgn);
        if (source == null) return;
        GtbPlayer target = ensurePlayer(toIgn, "final_identity_merge");
        if (target == null) return;
        mergePlayerState(target, source);
        target.name = toIgn;
        target.unresolvedIdentity = false;
        game.players.put(toIgn, target);
        transferIdentity(fromIgn, toIgn);
        if (game.currentBuilder == source) game.currentBuilder = target;
        transferScoreKeyIfAbsent(game.latestTrueScore, fromIgn, toIgn);
        transferScoreKeyIfAbsent(game.roundStartTrueScore, fromIgn, toIgn);
        transferScoreKeyIfAbsent(game.visibleTrueScore, fromIgn, toIgn);
    }

    private void transferScoreKey(Map<String, Integer> scores, String oldIgn, String newIgn) {
        if (scores == null) return;
        String scoreKey = findKeyIgnoreCase(scores, oldIgn);
        if (scoreKey != null) {
            Integer score = scores.remove(scoreKey);
            scores.put(newIgn, score);
        }
    }

    private void transferScoreKeyIfAbsent(Map<String, Integer> scores, String fromIgn, String toIgn) {
        if (scores == null) return;
        String sourceKey = findKeyIgnoreCase(scores, fromIgn);
        if (sourceKey != null && findKeyIgnoreCase(scores, toIgn) == null) {
            Integer score = scores.remove(sourceKey);
            scores.put(toIgn, score);
        }
    }

    private void mergePlayerState(GtbPlayer target, GtbPlayer source) {
        target.presentAtGameStart = target.presentAtGameStart || source.presentAtGameStart;
        target.isUser = target.isUser || source.isUser;
        target.everOnLeaderboard = target.everOnLeaderboard || source.everOnLeaderboard;
        target.everScored = target.everScored || source.everScored;
        if (target.rankColor == null) target.rankColor = source.rankColor;
        if (target.emblem == null) target.emblem = source.emblem;
        if (target.title == null) target.title = source.title;
        if (target.firstSeenTick < 0 || (source.firstSeenTick >= 0 && source.firstSeenTick < target.firstSeenTick)) {
            target.firstSeenTick = source.firstSeenTick;
        }
        target.lastMessageOrScoreTick = Math.max(target.lastMessageOrScoreTick, source.lastMessageOrScoreTick);
        target.inactiveTicks = Math.min(target.inactiveTicks, source.inactiveTicks);
        target.silentRoundsConsecutive = Math.min(target.silentRoundsConsecutive, source.silentRoundsConsecutive);
        target.spokeThisRound = target.spokeThisRound || source.spokeThisRound;
        target.absentThisRound = target.absentThisRound && source.absentThisRound;
        target.roundsMissedDuringDisconnect = Math.max(target.roundsMissedDuringDisconnect, source.roundsMissedDuringDisconnect);
        target.observedScoreFloor = Math.max(target.observedScoreFloor, source.observedScoreFloor);
        target.preDisconnectScoreBaseline = Math.max(
                target.preDisconnectScoreBaseline, source.preDisconnectScoreBaseline);
        target.disconnectUncertainRounds.addAll(source.disconnectUncertainRounds);
        target.rejoinRoundScoreUncertain = target.rejoinRoundScoreUncertain
                || source.rejoinRoundScoreUncertain;
        if (target.leaverState == GtbPlayer.LeaverState.NORMAL) target.leaverState = source.leaverState;
        if (source.buildRound != 0 && target.buildRound == 0) target.buildRound = source.buildRound;
        mergePoints(target, source);
    }

    private void mergePoints(GtbPlayer target, GtbPlayer source) {
        if (source.points == null) return;
        if (target.points == null || target.points.length < source.points.length) {
            PointEntry[] next = new PointEntry[source.points.length];
            for (int i = 0; i < next.length; i++) next[i] = PointEntry.zero();
            if (target.points != null) {
                for (int i = 0; i < target.points.length; i++) next[i] = target.points[i];
            }
            target.points = next;
        }
        for (int i = 0; i < source.points.length; i++) {
            target.points[i] = mergePointEntry(target.points[i], source.points[i]);
        }
    }

    private PointEntry mergePointEntry(PointEntry a, PointEntry b) {
        Set<Integer> av = pointValues(a);
        Set<Integer> bv = pointValues(b);
        Set<Integer> sums = new HashSet<Integer>();
        for (Integer x : av) {
            for (Integer y : bv) {
                sums.add(Integer.valueOf(x.intValue() + y.intValue()));
            }
        }
        return PointEntry.fork(sums);
    }

    private Set<Integer> pointValues(PointEntry e) {
        Set<Integer> values = new HashSet<Integer>();
        if (e == null || e.kind == PointEntry.Kind.ZERO) {
            values.add(Integer.valueOf(0));
        } else if (e.kind == PointEntry.Kind.DEFINITE) {
            values.add(Integer.valueOf(e.value));
        } else if (e.fork != null && !e.fork.isEmpty()) {
            values.addAll(e.fork);
        } else {
            values.add(Integer.valueOf(0));
        }
        return values;
    }

    private boolean hasScoreIgnoreCase(Map<String, Integer> scores, String ign) {
        return scoreIgnoreCase(scores, ign) != null;
    }

    private Integer scoreIgnoreCase(Map<String, Integer> scores, String ign) {
        String key = findKeyIgnoreCase(scores, ign);
        return key == null ? null : scores.get(key);
    }

    private boolean isMvpPlusPlusOrAbove(String ign) {
        String rank = PlayerMetaCache.INSTANCE.getRankLabel(ign);
        if (rank == null) return false;
        rank = rank.toUpperCase();
        return rank.contains("MVP++")
                || rank.equals("YOUTUBE") || rank.equals("YT")
                || rank.contains("ADMIN") || rank.contains("OWNER")
                || rank.contains("MOD") || rank.contains("GAME MASTER")
                || rank.equals("GM");
    }

    private boolean isKnownLeaver(String ign) {
        return GameStats.INSTANCE.isLeaver(ign);
    }

    private void transferIdentity(String fromIgn, String toIgn) {
        GameStats.INSTANCE.transferIdentity(fromIgn, toIgn);
    }

    // Disconnect / rejoin

    public void onUserLeave() {
        if (game == null) {

            return;
        }
        game.userDisconnectedThisGame = true;
        snapshotPreDisconnectScoreBaselines();
        // Pre-leave board state must not age into "stable" evidence across the
        // disconnect window; scores may grow while we are gone.
        resetPendingFloor();
        game.leaveState = game.state;
        game.leaveRound = game.currentRound;
        game.leaveBuilder = game.currentBuilder;
        if (game.leaveBuilder != null && game.leaveBuilder.isUser
                && game.leaveState == GtbGame.State.ROUND_BUILD) {
            game.userRoundSkipped = true;
            game.abandonedLocalBuildRound = game.leaveRound;
        }
        // Mark the local user absent for the in-progress round so its silence
        // doesn't count toward the away threshold.
        for (GtbPlayer p : game.players.values()) {
            if (p.isUser) p.absentThisRound = true;
        }

    }

    public void onUserRejoin() {
        if (game == null) {

            return;
        }
        game.roundCountUnknown = true;
        game.roundStartScoreBaselinePending = false;
        resetPendingFloor();
        game.currentBuilder = null;
        clearPendingBuilderScoreMarkers();
        if (game.leaveRound > 0) {
            game.pendingRejoinFromRound = game.leaveRound;
        }
        game.pendingRejoinFromUserBuildRound = game.leaveBuilder != null
                && game.leaveBuilder.isUser
                && game.leaveState == GtbGame.State.ROUND_BUILD;
        markDisconnectScoreUncertainty(game.pendingRejoinFromUserBuildRound);
        game.rejoinGuessOrderUncertain = true;
        resetRejoinGuessEvidence();
        game.rejoinBuilderChangedBeforeRoundHeader = false;
        game.visibleTrueScore.clear();
        resetPendingFloor();
        // Clear local-user away state; rounds spent disconnected do not count
        // toward the silent-rounds tally. From the rejoin round onward, a
        // fresh count begins (per the spec: leave R1, rejoin R4; R4 = first
        // counted round, away possible at R6).
        for (GtbPlayer p : game.players.values()) {
            if (p.isUser) {
                p.silentRoundsConsecutive = 0;
                p.spokeThisRound = false;
                p.absentThisRound = false;
                if (p.leaverState != GtbPlayer.LeaverState.NORMAL) {
                    p.leaverState = GtbPlayer.LeaverState.NORMAL;
                }
            }
        }

        game.leaveState = null;
        game.leaveRound = -1;
        game.leaveBuilder = null;
    }

    public void onUserRejoinSameRound(String builder) {
        if (game == null) return;
        boolean localBuildWasAbandoned = game.pendingRejoinFromUserBuildRound;
        boolean rejoinedWithLocalBuilder = false;
        suppressInvalidatedLocalBuilderBadge(builder);
        if (builder != null && !builder.isEmpty()) {
            GtbPlayer p = ensurePlayer(builder, "rejoin_same_round_builder");
            if (p != null) {
                rejoinedWithLocalBuilder = p.isUser;
                game.currentBuilder = p;
                if (game.currentRound > 0 && game.state == GtbGame.State.ROUND_BUILD
                        && p.buildRound != game.currentRound) {
                    p.buildRound = game.currentRound;
                    onBuilderAssigned(p);
                }
            }
        }
        game.roundCountUnknown = false;
        game.rejoinBuilderChangedBeforeRoundHeader = false;
        clearSameRoundConsumedScoreUncertainty();
        carrySameRoundRejoinGuessRankFloor();
        if (localBuildWasAbandoned || rejoinedWithLocalBuilder) {
            clearRejoinRoundScoreUncertainty();
            game.rejoinGuessOrderUncertain = false;
        }
        game.pendingRejoinFromRound = -1;
        game.pendingRejoinFromUserBuildRound = false;
        resetPendingFloor();
    }

    private void suppressInvalidatedLocalBuilderBadge(String rejoinBuilder) {
        if (game == null || !game.pendingRejoinFromUserBuildRound || game.currentRound <= 0) return;
        if (rejoinBuilder == null || rejoinBuilder.isEmpty()) return;
        GtbPlayer user = localUser();
        if (user == null || user.name == null || user.name.equalsIgnoreCase(rejoinBuilder)) return;
        user.suppressedPointBadgeRound = game.currentRound;
        if (user.pendingBuilderScoreRound == game.currentRound) user.pendingBuilderScoreRound = 0;
        if (user.authoritativeBuilderScoreRound == game.currentRound) {
            user.authoritativeBuilderScoreRound = 0;
        }
        if (user.buildRound == game.currentRound) user.buildRound = 0;
        restoreBuilderAssignmentOnlyActivity(user);
    }

    private GtbPlayer localUser() {
        if (game == null) return null;
        for (GtbPlayer p : game.players.values()) {
            if (p != null && p.isUser) return p;
        }
        return null;
    }

    public void onPlayerChat(String ign) {
        if (game == null || ign == null) return;
        GtbPlayer p = ensurePlayer(ign, "chat");
        if (p != null) {
            resolveConfirmedInitialRoundIdentity(p);
            onActivity(p);
            markMessageOrScoreEvidence(p);
        }
    }

    public void onPlayerRejoin(String ign) {
        if (game == null || ign == null) return;
        GtbPlayer p = ensurePlayer(ign, "player_rejoin");
        if (p == null) return;
        resolveConfirmedInitialRoundIdentity(p);
        onPassivePresence(p);
        p.absentThisRound = false;
        if (p.leaverState == GtbPlayer.LeaverState.LEAVER
                || p.leaverState == GtbPlayer.LeaverState.POTENTIAL_LEAVER) {
            p.leaverState = GtbPlayer.LeaverState.NORMAL;
        }
    }

    public void onGameRosterPlayer(String ign) {
        if (game == null || ign == null || ign.isEmpty()) return;
        GtbPlayer p = ensurePlayer(ign, "game_roster");
        if (p == null) return;
        p.presentAtGameStart = true;
        p.unresolvedIdentity = false;
        if (p.firstSeenTick < 0) p.firstSeenTick = tickCounter;
        onActivity(p);

    }

    public void onPlayerTabPresent(String ign) {
        if (game == null || ign == null || !acceptsTabRosterState()) return;
        String key = findKeyIgnoreCase(game.players, ign);
        if (key == null) return;
        GtbPlayer p = game.players.get(key);
        if (p == null) return;
        onPassivePresence(p);
        p.absentThisRound = false;

    }

    public void onPlayerTabAbsent(String ign) {
        if (game == null || ign == null || !acceptsTabRosterState()) return;
        String key = findKeyIgnoreCase(game.players, ign);
        if (key == null) return;
        GtbPlayer p = game.players.get(key);
        if (p == null) return;

    }

    public void onOneSecondAlert() {
        if (game == null) return;
        game.oneSecondAlertReached = true;

    }

    public void onPlayerEmblem(String ign, String emblem) {
        if (game == null || ign == null) {

            return;
        }
        GtbPlayer p = ensurePlayer(ign, "emblem");
        if (p == null) {

            return;
        }
        resolveConfirmedInitialRoundIdentity(p);
        String prev = p.emblem;
        if (prev != null && prev.equals(emblem)) return;  // dedupe noise
        p.emblem = emblem;

    }

    // Helpers

    private GtbPlayer ensurePlayer(String ign, String source) {
        if (game == null || ign == null || ign.isEmpty()) return null;
        String key = findKeyIgnoreCase(game.players, ign);
        if (key != null) {
            GtbPlayer existing = game.players.get(key);
            ensurePointsCapacity(existing, Math.max(game.totalRounds, game.currentRound));
            return existing;
        }
        GtbPlayer p = new GtbPlayer(ign);
        ensurePointsCapacity(p, Math.max(game.totalRounds, game.currentRound));
        p.presentAtGameStart = false;
        p.unresolvedIdentity = true;
        p.firstSeenTick = tickCounter;
        game.players.put(ign, p);

        return p;
    }

    private void resolveConfirmedInitialRoundIdentities() {
        if (game == null || !canResolveConfirmedInitialRoundIdentities()) return;
        for (GtbPlayer p : game.players.values()) {
            resolveConfirmedInitialRoundIdentity(p);
        }
    }

    private void resolveConfirmedInitialRoundIdentity(GtbPlayer p) {
        if (p == null || !canResolveConfirmedInitialRoundIdentities()) return;
        if (p.presentAtGameStart || !p.unresolvedIdentity) return;
        if (p.roundsMissedDuringDisconnect > 0) return;
        p.presentAtGameStart = true;
        p.unresolvedIdentity = false;
    }

    private boolean canResolveConfirmedInitialRoundIdentities() {
        return game != null
                && game.initialRoundHeaderConfirmed
                && game.currentRound == 1
                && !game.userDisconnectedThisGame
                && !game.roundCountUnknown
                && game.pendingRejoinFromRound < 0
                && game.leaveState == null
                && game.leaveRound < 0;
    }

    private void ensurePointsCapacity(GtbPlayer p, int size) {
        if (p == null) return;
        int targetSize = Math.max(0, size);
        if (p.points != null && p.points.length >= targetSize) return;
        PointEntry[] next = new PointEntry[targetSize];
        for (int i = 0; i < next.length; i++) next[i] = PointEntry.zero();
        if (p.points != null) {
            for (int i = 0; i < p.points.length; i++) next[i] = p.points[i];
        }
        p.points = next;
    }

    private void updateKnownRoundCount(int totalRounds) {
        if (game == null || totalRounds <= 0) return;
        game.totalRounds = totalRounds;
        int capacity = Math.max(game.totalRounds, game.currentRound);
        for (GtbPlayer p : game.players.values()) {
            ensurePointsCapacity(p, capacity);
        }
    }

    private void ensureImplicitFirstRound() {
        if (game == null || game.currentRound > 0) return;
        if (hasLocalRejoinUncertainty()) return;
        if (game.totalRounds <= 0) {
            game.totalRounds = Math.max(1, game.players.size());
        }
        for (GtbPlayer p : game.players.values()) {
            ensurePointsCapacity(p, game.totalRounds);
        }
        game.currentRound = 1;
        game.oneSecondAlertReached = false;
        game.correctGuessesThisRound = 0;
        game.currentTheme = "";
        game.currentHintStructure = "";
        game.ruledOutEnglishThemesThisRound.clear();
        game.wrongGuessesThisRound.clear();
        game.roundSkippedThisRound = false;
        clearPendingBuilderScoreMarkers();
        game.state = GtbGame.State.ROUND_BUILD;
        snapshotRoundStartScores();
    }

    private void snapshotRoundStartScores() {
        if (game == null) return;
        game.roundStartTrueScore = new HashMap<String, Integer>(game.latestTrueScore);
        for (GtbPlayer p : game.players.values()) {
            addObservedScoreFloor(game.roundStartTrueScore, p);
        }
    }

    private void advanceRetainedScoreFromRoundStart(GtbPlayer p, int delta, boolean allowUser) {
        if (game == null || p == null || p.name == null || delta <= 0) return;
        if (!allowUser && p.isUser) return;
        int r = game.currentRound - 1;
        Integer baseline = retainedBaselineForRound(p, r);
        if (baseline == null) return;
        int next = baseline.intValue() + delta;
        if (!isPlausibleObservedScore(next)) return;
        putRetainedScore(p, next, false);
    }

    private boolean hasLocalRejoinUncertainty() {
        if (game == null) return false;
        return game.userDisconnectedThisGame
                || game.roundCountUnknown
                || game.pendingRejoinFromRound >= 0
                || game.leaveState != null
                || game.leaveRound >= 0;
    }

    private boolean hasPendingRejoinRoundUncertainty() {
        if (game == null) return false;
        return game.roundCountUnknown
                || game.pendingRejoinFromRound >= 0
                || game.leaveState != null
                || game.leaveRound >= 0;
    }

    private void resetRoundLocalAfterRejoinBuilderChange() {
        if (game == null) return;
        game.rejoinBuilderChangedBeforeRoundHeader = true;
        game.suppressPointBadgesUntilRoundHeader = true;
        game.correctGuessesThisRound = 0;
        resetRejoinGuessEvidence();
        game.currentTheme = "";
        game.currentHintStructure = "";
        game.ruledOutEnglishThemesThisRound.clear();
        game.wrongGuessesThisRound.clear();
        game.roundSkippedThisRound = false;
        clearPendingBuilderScoreMarkers();
    }

    private boolean absorbRoundStartScoreBaseline(GtbPlayer p, int observed) {
        if (game == null || p == null || p.name == null || p.points == null) return false;
        if (p.pendingBuilderScoreRound == game.currentRound) return false;
        int r = game.currentRound - 1;
        if (r < 0 || r >= p.points.length) return false;
        PointEntry slot = p.points[r];
        if (slot == null || slot.kind != PointEntry.Kind.ZERO) return false;

        String key = findKeyIgnoreCase(game.roundStartTrueScore, p.name);
        game.roundStartTrueScore.put(key == null ? p.name : key, Integer.valueOf(observed));
        return true;
    }

    private void markMessageOrScoreEvidence(GtbPlayer p) {
        if (p == null) return;
        p.lastMessageOrScoreTick = tickCounter;
    }

    private void applyVisibleScoreDelta(GtbPlayer p, int observed, Integer prior,
                                        Integer previousVisible) {
        if (game == null || p == null || p.points == null) return;
        if (isClosingScoreSnapshot() || previousVisible == null
                || observed > previousVisible.intValue()) {
            if (applyVisibleCurrentRoundResidual(p, observed)) return;
        }
        if (prior == null || previousVisible == null) return;
        if (previousVisible.intValue() != prior.intValue()) return;
        int delta = observed - previousVisible.intValue();
        if (delta <= 0 || delta > 3) return;
        int r = game.currentRound - 1;
        if (r < 0 || r >= p.points.length) return;

        PointEntry slot = p.points[r];
        if (slot == null) return;
        if (slot.kind == PointEntry.Kind.FORK) {
            if (slot.fork != null && slot.fork.contains(Integer.valueOf(delta))) {
                p.points[r] = PointEntry.definite(delta);
            }
            return;
        }
        if (slot.kind == PointEntry.Kind.ZERO || slot.value != delta) {
            p.points[r] = PointEntry.definite(delta);
        }
    }

    private boolean applyVisibleCurrentRoundResidual(GtbPlayer p, int observed) {
        if (game == null || p == null || p.name == null || p.points == null) return false;
        if (game.roundCountUnknown || game.pendingRejoinFromRound >= 0) return false;
        if (isAbandonedLocalBuildRound()) return true;
        int r = game.currentRound - 1;
        if (r < 0 || r >= p.points.length) return false;

        PointEntry slot = p.points[r];
        if (slot == null) return false;

        Integer baseline = scoreIgnoreCase(game.roundStartTrueScore, p.name);
        if (baseline == null) {
            if (hasLocalRejoinUncertainty()
                    || (game.currentRound > 1 && !game.initialRoundHeaderConfirmed)) {
                return false;
            }
            baseline = exactTotalExceptRound(p.points, r);
        }
        if (baseline == null) return false;

        int delta = observed - baseline.intValue();
        if (delta < 0 || delta > 3) return false;
        if (slot.kind == PointEntry.Kind.DEFINITE) {
            if (delta > slot.value) p.points[r] = PointEntry.definite(delta);
            else if (delta < slot.value) {
                if (!isClosingScoreSnapshot()) return true;
                p.points[r] = PointEntry.definite(delta);
            }
            if (delta > 0) {
                p.everScored = true;
                markMessageOrScoreEvidence(p);
                resolveRejoinGuessOrderFromExactDelta(p, delta);
            }
            return true;
        }
        if (slot.kind == PointEntry.Kind.FORK) {
            if (slot.fork == null || !slot.fork.contains(Integer.valueOf(delta))) return false;
            p.points[r] = PointEntry.definite(delta);
        } else if (slot.kind == PointEntry.Kind.ZERO) {
            if (delta == 0) return false;
            p.points[r] = PointEntry.definite(delta);
        } else {
            return false;
        }

        if (delta > 0) {
            p.everScored = true;
            markMessageOrScoreEvidence(p);
            resolveRejoinGuessOrderFromExactDelta(p, delta);
        }
        return true;
    }

    private boolean isClosingScoreSnapshot() {
        return game != null && (game.state == GtbGame.State.ROUND_END
                || game.state == GtbGame.State.POST_GAME);
    }

    private void resolveRejoinGuessOrderFromExactDelta(GtbPlayer scorer, int delta) {
        if (game == null || !game.rejoinGuessOrderUncertain || scorer == null
                || scorer == game.currentBuilder) return;
        int observedIndex = indexOfIgnoreCase(game.rejoinObservedGuessOrder, scorer.name);
        if (delta == 3) {
            if (observedIndex == 0) resolveObservedRejoinGuessAwards(0, true);
            else if (observedIndex < 0) game.rejoinPriorGuessFloor = Math.max(game.rejoinPriorGuessFloor, 1);
            return;
        }
        if (delta != 2) return;
        GtbPlayer firstGuess = exactCurrentRoundNonBuilderWithAward(3, scorer);
        if (firstGuess == null) return;
        int firstObservedIndex = indexOfIgnoreCase(game.rejoinObservedGuessOrder, firstGuess.name);
        if (observedIndex == 1 && firstObservedIndex == 0) resolveObservedRejoinGuessAwards(0, true);
        else if (observedIndex == 0 && firstObservedIndex < 0) resolveObservedRejoinGuessAwards(1, true);
        else if (observedIndex < 0 && firstObservedIndex < 0) resolveObservedRejoinGuessAwards(2, false);
    }

    private GtbPlayer exactCurrentRoundNonBuilderWithAward(int award, GtbPlayer excluded) {
        if (game == null || game.currentRound <= 0) return null;
        int r = game.currentRound - 1;
        for (GtbPlayer candidate : game.players.values()) {
            if (candidate == null || candidate == excluded || candidate == game.currentBuilder
                    || candidate.points == null || r >= candidate.points.length) continue;
            PointEntry entry = candidate.points[r];
            if (entry != null && entry.kind == PointEntry.Kind.DEFINITE && entry.value == award) return candidate;
        }
        return null;
    }

    private void resolveObservedRejoinGuessAwards(int priorGuesses, boolean gapScorersComplete) {
        if (game == null || game.currentRound <= 0) return;
        int r = game.currentRound - 1;
        for (int i = 0; i < game.rejoinObservedGuessOrder.size(); i++) {
            String key = findKeyIgnoreCase(game.players, game.rejoinObservedGuessOrder.get(i));
            GtbPlayer p = key == null ? null : game.players.get(key);
            if (p == null || p.points == null || r >= p.points.length) continue;
            int award = Math.max(1, 3 - priorGuesses - i);
            PointEntry existing = p.points[r];
            if (existing == null || existing.kind == PointEntry.Kind.ZERO
                    || existing.kind == PointEntry.Kind.FORK || existing.value < award) {
                p.points[r] = PointEntry.definite(award);
            }
        }
        game.rejoinPriorGuessFloor = priorGuesses;
        game.correctGuessesThisRound = Math.max(game.correctGuessesThisRound,
                priorGuesses + game.rejoinObservedGuessOrder.size());
        game.rejoinGuessOrderUncertain = false;
        if (gapScorersComplete) {
            game.rejoinBuilderChangedBeforeRoundHeader = false;
            clearRejoinRoundScoreUncertainty();
        }
    }

    private void resetRejoinGuessEvidence() {
        if (game == null) return;
        game.rejoinObservedGuessOrder.clear();
        game.rejoinPriorGuessFloor = 0;
    }

    private void carrySameRoundRejoinGuessRankFloor() {
        if (game == null || game.currentRound <= 0 || game.currentBuilder == null) return;
        int roundIndex = game.currentRound - 1;
        int exactScorerCount = 0;
        int rankFloor = 0;
        for (GtbPlayer player : game.players.values()) {
            if (player == null || player == game.currentBuilder || player.points == null
                    || roundIndex >= player.points.length) {
                continue;
            }
            PointEntry entry = player.points[roundIndex];
            if (entry == null || entry.kind != PointEntry.Kind.DEFINITE
                    || entry.value < 1 || entry.value > 3) {
                continue;
            }
            exactScorerCount++;
            rankFloor = Math.max(rankFloor, 4 - entry.value);
        }

        int provenOccupiedSlots = Math.max(exactScorerCount, rankFloor);
        int preGapObservedGuesses = Math.max(0,
                game.correctGuessesThisRound - game.rejoinObservedGuessOrder.size());
        int hiddenOccupiedSlots = Math.max(0, provenOccupiedSlots - preGapObservedGuesses);
        game.rejoinPriorGuessFloor = Math.max(game.rejoinPriorGuessFloor, hiddenOccupiedSlots);
    }

    private int indexOfIgnoreCase(List<String> values, String target) {
        if (values == null || target == null) return -1;
        for (int i = 0; i < values.size(); i++) {
            String value = values.get(i);
            if (value != null && value.equalsIgnoreCase(target)) return i;
        }
        return -1;
    }

    /** Award the builder from the revealed theme, correcting a tentative sidebar delta. */
    private void awardBuilderIfReady() {
        if (game == null) return;
        if (game.correctGuessesThisRound <= 0) return;
        if (isAbandonedLocalBuildRound()) return;
        if (game.currentTheme == null || game.currentTheme.isEmpty()) return;
        GtbPlayer builder = builderAwaitingThemeAward();
        if (builder == null) return;
        int r = game.currentRound - 1;
        if (r < 0 || builder.points == null || r >= builder.points.length) return;
        PointEntry award = ThemePointAward.compute(
                game.currentTheme, game.currentLangCode, game.ruledOutEnglishThemesThisRound);
        if (award == null) return;
        award = resolveAwardFromKnownTotal(builder, r, award);
        PointEntry existing = builder.points[r];
        if (existing.kind == PointEntry.Kind.FORK && award.kind == PointEntry.Kind.FORK) {
            award = intersectFork(existing, award);
            if (award == null) return;
        } else if (existing.kind != PointEntry.Kind.ZERO
                && award.kind != PointEntry.Kind.DEFINITE) {
            return;
        }
        applyBuilderAward(builder, r, award);
        if (award != null && award.max() > 0) {
            builder.everScored = true;
            markMessageOrScoreEvidence(builder);
        }
    }

    private PointEntry intersectFork(PointEntry existing, PointEntry award) {
        if (existing == null || award == null || existing.fork == null || award.fork == null) {
            return null;
        }
        Set<Integer> kept = new HashSet<Integer>();
        for (Integer value : existing.fork) {
            if (value != null && award.fork.contains(value)) kept.add(value);
        }
        if (kept.isEmpty()) return null;
        if (kept.size() == existing.fork.size()) return existing;
        return PointEntry.fork(kept);
    }

    private GtbPlayer builderAwaitingThemeAward() {
        if (game == null) return null;
        if (game.currentBuilder != null) return game.currentBuilder;
        if (hasPendingRejoinRoundUncertainty()) return null;
        if (game.currentRound <= 0) return null;
        for (GtbPlayer p : game.players.values()) {
            if (p != null && p.pendingBuilderScoreRound == game.currentRound) return p;
        }
        for (GtbPlayer p : game.players.values()) {
            if (p != null && p.buildRound == game.currentRound) return p;
        }
        return null;
    }

    private void awardBuilderFromEnglishHintIfReady() {
        if (game == null || game.currentBuilder == null) return;
        if (isAbandonedLocalBuildRound()) return;
        if (game.correctGuessesThisRound <= 0) return;
        if (game.currentTheme != null && !game.currentTheme.isEmpty()) return;
        if (!isEnglishRound()) return;
        if (game.currentHintStructure == null || game.currentHintStructure.isEmpty()) return;
        int r = game.currentRound - 1;
        if (r < 0 || r >= game.currentBuilder.points.length) return;

        PointEntry award = ThemePointAward.computeEnglishHintStructure(game.currentHintStructure);
        if (award == null || award.kind != PointEntry.Kind.DEFINITE || award.value <= 0) return;
        applyBuilderAward(game.currentBuilder, r, award);
        game.currentBuilder.everScored = true;
        markMessageOrScoreEvidence(game.currentBuilder);
    }

    private void applyBuilderAward(GtbPlayer builder, int roundIndex, PointEntry award) {
        if (game == null || builder == null || award == null) return;
        builder.points[roundIndex] = award;
        builder.suppressedPointBadgeRound = 0;
        builder.pendingBuilderScoreRound = 0;
        clearCurrentRejoinRoundUncertainty(builder);
        resolveCurrentRoundEntryFromKnownTotal(builder, roundIndex);
        PointEntry settled = builder.points[roundIndex];
        if (settled == null || settled.kind != PointEntry.Kind.DEFINITE) return;

        builder.authoritativeBuilderScoreRound = game.currentRound;
        Integer total = authoritativeBuilderTotal(builder);
        if (total == null || builder.name == null) return;

        Integer retainedBaseline = retainedBaselineForRound(builder, roundIndex);
        if (retainedBaseline != null) {
            int retainedTotal = retainedBaseline.intValue() + settled.value;
            if (retainedTotal > total.intValue()) total = Integer.valueOf(retainedTotal);
        }
        putRetainedScore(builder, total.intValue(), true);
    }

    private Integer retainedBaselineForRound(GtbPlayer p, int roundIndex) {
        if (game == null || p == null || p.name == null) return null;
        Integer baseline = scoreIgnoreCase(game.roundStartTrueScore, p.name);
        if (baseline != null) return baseline;
        if (roundIndex >= 0) {
            baseline = exactTotalExceptRound(p.points, roundIndex);
            if (baseline != null) return baseline;
        }
        return scoreIgnoreCase(game.latestTrueScore, p.name);
    }

    private void putRetainedScore(GtbPlayer p, int score, boolean allowDecrease) {
        if (game == null || p == null || p.name == null) return;
        if (!isPlausibleObservedScore(score)) return;
        Integer latest = scoreIgnoreCase(game.latestTrueScore, p.name);
        if (!allowDecrease && latest != null && latest.intValue() >= score) return;
        Integer next = Integer.valueOf(score);
        String latestKey = findKeyIgnoreCase(game.latestTrueScore, p.name);
        game.latestTrueScore.put(latestKey == null ? p.name : latestKey, next);
        String visibleKey = findKeyIgnoreCase(game.visibleTrueScore, p.name);
        if (visibleKey != null) game.visibleTrueScore.put(visibleKey, next);
    }

    private void rememberObservedScore(GtbPlayer p, int score) {
        if (p == null || !isPlausibleObservedScore(score)) return;
        if (score > p.observedScoreFloor) {
            p.observedScoreFloor = score;
        }
    }

    private void snapshotPreDisconnectScoreBaselines() {
        if (game == null) return;
        for (GtbPlayer p : game.players.values()) {
            if (p == null || p.name == null) continue;
            int exact = p.observedScoreFloor;
            Integer visible = scoreIgnoreCase(game.visibleTrueScore, p.name);
            if (visible != null) exact = Math.max(exact, visible.intValue());
            if (!p.hasUncertainty()) {
                exact = Math.max(exact, p.totalMin());
                Integer retained = scoreIgnoreCase(game.latestTrueScore, p.name);
                if (retained != null) exact = Math.max(exact, retained.intValue());
            }
            if (exact >= 0) {
                p.preDisconnectScoreBaseline = Math.max(p.preDisconnectScoreBaseline, exact);
            }
        }
    }

    private void markDisconnectScoreUncertainty(boolean localBuildWasAbandoned) {
        if (game == null) return;
        boolean leaveRoundCouldScore = !localBuildWasAbandoned
                && game.leaveState == GtbGame.State.ROUND_BUILD;
        for (GtbPlayer p : game.players.values()) {
            if (p == null || p.isUser || isConfirmedLeaver(p)) continue;
            if (leaveRoundCouldScore && game.leaveRound > 0) {
                p.disconnectUncertainRounds.add(Integer.valueOf(game.leaveRound));
            }
            p.rejoinRoundScoreUncertain = true;
        }
    }

    private void markCurrentRejoinRoundScoreUncertainty() {
        if (game == null) return;
        for (GtbPlayer p : game.players.values()) {
            if (p == null || p.isUser || isConfirmedLeaver(p)) continue;
            p.rejoinRoundScoreUncertain = true;
        }
    }

    private void materializeRejoinRoundUncertainty(int round) {
        if (game == null) return;
        for (GtbPlayer p : game.players.values()) {
            if (p == null || !p.rejoinRoundScoreUncertain) continue;
            p.disconnectUncertainRounds.add(Integer.valueOf(round));
            p.rejoinRoundScoreUncertain = false;
        }
    }

    private void clearRejoinRoundScoreUncertainty() {
        if (game == null) return;
        for (GtbPlayer p : game.players.values()) {
            clearCurrentRejoinRoundUncertainty(p);
        }
    }

    private void clearSameRoundConsumedScoreUncertainty() {
        if (game == null || game.currentRound <= 0) return;
        int roundIndex = game.currentRound - 1;
        for (GtbPlayer p : game.players.values()) {
            if (p == null || p.points == null || roundIndex >= p.points.length) continue;
            PointEntry entry = p.points[roundIndex];
            boolean alreadyScored = entry != null && entry.min() > 0;
            boolean builderWillScore = p == game.currentBuilder && game.correctGuessesThisRound > 0;
            if (alreadyScored || builderWillScore) {
                clearCurrentRejoinRoundUncertainty(p);
            }
        }
    }

    private void clearCurrentRejoinRoundUncertainty(GtbPlayer p) {
        if (p == null) return;
        p.rejoinRoundScoreUncertain = false;
        if (game != null && game.currentRound > 0) {
            p.disconnectUncertainRounds.remove(Integer.valueOf(game.currentRound));
        }
    }

    private void clearDisconnectScoreUncertainty(GtbPlayer p) {
        if (p == null) return;
        p.disconnectUncertainRounds.clear();
        p.rejoinRoundScoreUncertain = false;
        p.roundsMissedDuringDisconnect = 0;
    }

    private boolean isAbandonedLocalBuildRound() {
        return game != null && game.currentRound > 0
                && game.currentRound == game.abandonedLocalBuildRound;
    }

    private void addObservedScoreFloor(Map<String, Integer> scores, GtbPlayer p) {
        if (scores == null || p == null || p.name == null || p.observedScoreFloor < 0) return;
        String key = findKeyIgnoreCase(scores, p.name);
        Integer existing = key == null ? null : scores.get(key);
        if (existing == null || p.observedScoreFloor > existing.intValue()) {
            scores.put(key == null ? p.name : key, Integer.valueOf(p.observedScoreFloor));
        }
    }

    private PointEntry resolveAwardFromKnownTotal(GtbPlayer builder, int roundIndex, PointEntry award) {
        if (award == null || award.kind != PointEntry.Kind.FORK) return award;
        Integer residual = residualFromKnownTotal(builder, roundIndex);
        if (residual != null && award.fork != null && award.fork.contains(residual)) {
            return PointEntry.definite(residual.intValue());
        }
        return award;
    }

    private void resolveCurrentRoundEntryFromKnownTotal(GtbPlayer builder, int roundIndex) {
        if (builder == null || builder.points == null || roundIndex < 0
                || roundIndex >= builder.points.length) {
            return;
        }
        PointEntry slot = builder.points[roundIndex];
        if (slot == null || slot.kind != PointEntry.Kind.FORK || slot.fork == null) return;
        Integer residual = residualFromKnownTotal(builder, roundIndex);
        if (residual != null && slot.fork.contains(residual)) {
            builder.points[roundIndex] = PointEntry.definite(residual.intValue());
        }
    }

    private Integer residualFromKnownTotal(GtbPlayer player, int roundIndex) {
        if (game == null || player == null || player.name == null) return null;
        Integer observed = scoreIgnoreCase(game.visibleTrueScore, player.name);
        Integer latest = scoreIgnoreCase(game.latestTrueScore, player.name);
        if (observed == null || (latest != null && latest.intValue() > observed.intValue())) {
            observed = latest;
        }
        if (observed == null) return null;
        Integer exactOtherRounds = exactTotalExceptRound(player.points, roundIndex);
        if (exactOtherRounds == null) return null;
        int residual = observed.intValue() - exactOtherRounds.intValue();
        if (residual < 0 || residual > 3) return null;
        return Integer.valueOf(residual);
    }

    private Integer authoritativeBuilderTotal(GtbPlayer player) {
        if (game == null || game.currentRound <= 0 || player == null
                || player.authoritativeBuilderScoreRound != game.currentRound) {
            return null;
        }
        int lo = player.totalMin();
        int hi = player.totalMax();
        return lo == hi ? Integer.valueOf(hi) : null;
    }

    private boolean isEnglishRound() {
        if (game.currentLangCode == null) return false;
        String lang = game.currentLangCode.trim().toLowerCase();
        return "en".equals(lang) || "en_us".equals(lang) || "en-us".equals(lang);
    }

    private void markBuilderPendingIfNeeded() {
        if (game == null || game.currentBuilder == null) return;
        if (game.correctGuessesThisRound <= 0) return;
        if (game.state != GtbGame.State.ROUND_BUILD || game.currentRound <= 0) return;
        if (game.currentBuilder.buildRound != game.currentRound) return;
        if (game.currentTheme != null && !game.currentTheme.isEmpty()) return;
        awardBuilderFromEnglishHintIfReady();
        int r = game.currentRound - 1;
        if (r < 0 || game.currentBuilder.points == null || r >= game.currentBuilder.points.length) return;
        if (game.currentBuilder.points[r].kind != PointEntry.Kind.ZERO) return;
        game.currentBuilder.pendingBuilderScoreRound = game.currentRound;
        clearCurrentRejoinRoundUncertainty(game.currentBuilder);
    }

    private void resolvePendingBuilderFromLeaderboard(GtbPlayer p, int observed, Integer prior) {
        if (game == null || p == null) return;
        if (p.pendingBuilderScoreRound != game.currentRound) return;
        if (game.currentBuilder != p && p.buildRound != game.currentRound) return;
        int r = game.currentRound - 1;
        if (r < 0 || p.points == null || r >= p.points.length) return;
        PointEntry slot = p.points[r];
        if (slot == null) return;

        int delta = pendingBuilderDeltaFromLeaderboard(p, r, observed, prior);
        if (delta >= 1 && delta <= 3) {
            p.points[r] = PointEntry.definite(delta);
            p.pendingBuilderScoreRound = 0;
            p.everScored = true;
            markMessageOrScoreEvidence(p);
            return;
        }

        if (p.points[r].kind != PointEntry.Kind.ZERO) {
            p.pendingBuilderScoreRound = 0;
        }
    }

    private int pendingBuilderDeltaFromLeaderboard(GtbPlayer p, int roundIndex, int observed,
                                                   Integer prior) {
        int delta = Integer.MIN_VALUE;
        if (prior != null) {
            delta = observed - prior.intValue();
        }
        if (delta < 1 || delta > 3) {
            if (hasLocalRejoinUncertainty()
                    || p.roundsMissedDuringDisconnect != 0
                    || p.unresolvedIdentity
                    || !p.presentAtGameStart) {
                return Integer.MIN_VALUE;
            }
            Integer exactOtherRounds = exactTotalExceptRound(p.points, roundIndex);
            if (exactOtherRounds == null) return Integer.MIN_VALUE;
            delta = observed - exactOtherRounds.intValue();
        }
        return delta;
    }

    private Integer exactTotalExceptRound(PointEntry[] points, int excludedIndex) {
        if (points == null) return null;
        int sum = 0;
        for (int i = 0; i < points.length; i++) {
            if (i == excludedIndex) continue;
            PointEntry e = points[i];
            if (e == null || e.kind == PointEntry.Kind.ZERO) continue;
            if (e.kind == PointEntry.Kind.DEFINITE) {
                sum += e.value;
            } else if (e.fork != null && e.min() == e.max()) {
                sum += e.min();
            } else {
                return null;
            }
        }
        return Integer.valueOf(sum);
    }

    /**
     * If the lobby tab parse missed players because the client joined late, their
     * first leaderboard appearance during round 1, or at the clean start of
     * round 2, is still enough to settle round 1: there is only one possible
     * scoring event before round 2 guesses start. Across a leave/rejoin gap,
     * only settle when the observed score itself proves the non-builder +3, or
     * when +3 ownership is already known and this row proves the +2.
     */
    private void settleLateFirstRoundScore(GtbPlayer p, int observed, Integer prior) {
        if (game == null || p == null || p.points == null) return;
        if (prior != null) return;
        if (game.currentRound != 1
                && !(game.currentRound == 2 && game.correctGuessesThisRound == 0)) return;
        if (observed < 0 || observed > 3) return;
        boolean hasRejoinUncertainty = game.roundCountUnknown
                || game.pendingRejoinFromRound >= 0;
        if (hasRejoinUncertainty) return;
        if (p.presentAtGameStart || !p.unresolvedIdentity) return;
        if (p.roundsMissedDuringDisconnect > 0) return;
        if (p.points.length < 1 || p.points[0].kind != PointEntry.Kind.ZERO) return;
        if (p.totalMax() != 0) return;

        p.points[0] = PointEntry.definite(observed);
        p.presentAtGameStart = true;
        p.unresolvedIdentity = false;
        if (observed > 0) {
            p.everScored = true;
            markMessageOrScoreEvidence(p);
        }
    }

    /**
     * At the clean start of round 2, a visible leaderboard row with 0 points
     * proves every off-leaderboard player also finished round 1 on 0. This
     * settles late roster identities from incomplete start extraction or L/RJ
     * without waiting for each player to appear on the leaderboard.
     */
    private void reconcileOffLeaderboardFirstRoundZeros(Map<String, Integer> observedScores) {
        if (game == null || observedScores == null || observedScores.isEmpty()) return;
        // While a rejoin window is unresolved, currentRound is still the stale
        // leave round; the "clean start of round 2" gate below would compare
        // against the wrong round and stamp zeros off post-gap evidence.
        if (game.roundCountUnknown || game.pendingRejoinFromRound >= 0) return;
        if (game.currentRound != 2 || game.correctGuessesThisRound != 0) return;
        if (!hasZeroLeaderboardScore(observedScores)) return;

        for (GtbPlayer p : game.players.values()) {
            if (p == null || p.name == null || p.points == null || p.points.length < 1) continue;
            if (findKeyIgnoreCase(observedScores, p.name) != null) continue;
            PointEntry first = p.points[0];
            if (first == null) continue;
            if (first.kind == PointEntry.Kind.DEFINITE && first.value != 0) continue;
            if (first.kind == PointEntry.Kind.FORK
                    && (first.fork == null || !first.fork.contains(Integer.valueOf(0)))) continue;

            p.points[0] = PointEntry.definite(0);
            if (p.totalMax() == 0) {
                p.presentAtGameStart = true;
                p.unresolvedIdentity = false;
                p.roundsMissedDuringDisconnect = 0;
            }
        }
    }

    private boolean hasZeroLeaderboardScore(Map<String, Integer> observedScores) {
        if (observedScores == null) return false;
        for (Integer score : observedScores.values()) {
            if (score != null && score.intValue() == 0) return true;
        }
        return false;
    }

    private void clearPendingBuilderScoreMarkers() {
        if (game == null) return;
        for (GtbPlayer p : game.players.values()) {
            if (p != null) p.pendingBuilderScoreRound = 0;
        }
    }

    private void promoteAwayUnbuiltLeavers(int announcedTotalRounds) {
        if (game == null || announcedTotalRounds <= 0) return;
        java.util.List<GtbPlayer> candidates = new java.util.ArrayList<GtbPlayer>();
        int notLeft = 0;
        int plausibleUnbuiltRemoteCount = 0;
        for (GtbPlayer p : game.players.values()) {
            if (p == null || p.name == null) continue;
            if (p.leaverState == GtbPlayer.LeaverState.LEAVER) continue;
            if (isKnownLeaver(p.name)) continue;
            notLeft++;
            // The local client's presence is authoritative. For remotes, silence
            // narrows the candidates but cannot eliminate another unbuilt player:
            // that player may have left after their most recent activity.
            if (p.isUser || p.buildRound != 0) continue;
            plausibleUnbuiltRemoteCount++;
            if (p.silentRoundsConsecutive >= 2) {
                candidates.add(p);
            }
        }
        int missing = notLeft - announcedTotalRounds;
        if (missing <= 0) return;
        if (plausibleUnbuiltRemoteCount != missing
                || candidates.size() != plausibleUnbuiltRemoteCount) return;
        for (GtbPlayer p : candidates) {
            p.leaverState = GtbPlayer.LeaverState.LEAVER;
        }

    }

    private void onActivity(GtbPlayer p) {
        if (p == null) return;
        p.builderAssignmentActivityRound = 0;
        p.inactiveTicks = 0;
        p.spokeThisRound = true;
        p.silentRoundsConsecutive = 0;
        if (p.leaverState == GtbPlayer.LeaverState.LEAVER
                || p.leaverState == GtbPlayer.LeaverState.POTENTIAL_LEAVER) {
            p.leaverState = GtbPlayer.LeaverState.NORMAL;
        }
    }

    private void onPassivePresence(GtbPlayer p) {
        if (p == null) return;
        p.inactiveTicks = 0;
    }

    private boolean acceptsTabRosterState() {
        return game != null
                && game.state != GtbGame.State.NONE
                && game.state != GtbGame.State.LOBBY
                && game.state != GtbGame.State.POST_GAME;
    }

    private void onBuilderAssigned(GtbPlayer p) {
        if (p == null || game == null) return;
        int previousSilent = p.builderAssignmentActivityRound != 0
                ? p.silentRoundsBeforeBuilderAssignment
                : p.silentRoundsConsecutive;
        onActivity(p);
        p.builderAssignmentActivityRound = game.currentRound;
        p.silentRoundsBeforeBuilderAssignment = previousSilent;
    }

    private void restoreBuilderAssignmentOnlyActivity(GtbPlayer p) {
        if (p == null || game == null) return;
        if (p.builderAssignmentActivityRound != game.currentRound) return;
        if (p.silentRoundsBeforeBuilderAssignment > p.silentRoundsConsecutive) {
            p.silentRoundsConsecutive = p.silentRoundsBeforeBuilderAssignment;
        }
        p.spokeThisRound = false;
        p.builderAssignmentActivityRound = 0;
    }

    /**
     * Collapse forks for a single player against an authoritative leaderboard score.
     * Walks {@code p.points[]} and tries to lock fork rounds whose value alone makes
     * the running sum match. Mirrors the 1.8.9 reconcile logic; conservative:
     * only
     * collapses when exactly one fork member fits.
     */
    private void collapseForks(GtbPlayer p, int observed) {
        if (p == null || p.points == null) return;
        int defSum = p.definiteSum();
        int residual = observed - defSum;

        // Tally forks
        int forkCount = 0;
        int forkMin = 0;
        int forkMax = 0;
        for (PointEntry e : p.points) {
            if (e != null && e.kind == PointEntry.Kind.FORK) {
                forkCount++;
                forkMin += e.min();
                forkMax += e.max();
            }
        }
        if (forkCount == 0) return;
        if (residual < forkMin || residual > forkMax) return;  // observation can't be explained by forks alone

        // Single-fork case: residual IS that fork's value.
        if (forkCount == 1) {
            for (int i = 0; i < p.points.length; i++) {
                PointEntry e = p.points[i];
                if (e != null && e.kind == PointEntry.Kind.FORK && e.fork.contains(residual)) {
                    p.points[i] = PointEntry.definite(residual);
                    return;
                }
            }
            return;
        }

        // Multi-fork: lock any fork whose value alone resolves the residual under
        // the remaining forks' min/max envelope. Iterate to convergence.
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 0; i < p.points.length; i++) {
                PointEntry e = p.points[i];
                if (e == null || e.kind != PointEntry.Kind.FORK) continue;
                int otherMin = 0, otherMax = 0;
                for (int j = 0; j < p.points.length; j++) {
                    if (j == i) continue;
                    PointEntry o = p.points[j];
                    if (o != null && o.kind == PointEntry.Kind.FORK) {
                        otherMin += o.min();
                        otherMax += o.max();
                    }
                }
                Integer lockTo = null;
                int matches = 0;
                for (Integer v : e.fork) {
                    int need = residual - v;
                    if (need >= otherMin && need <= otherMax) {
                        lockTo = v;
                        matches++;
                    }
                }
                if (matches == 1 && lockTo != null) {
                    p.points[i] = PointEntry.definite(lockTo);
                    residual -= lockTo;
                    changed = true;
                }
            }
        }
    }

    /**
     * Off-leaderboard players are at or below the lowest visible vanilla
     * leaderboard score. Equality is still ambiguous because Hypixel can omit
     * tied players, but any fork alternative that forces a total strictly above
     * that floor is impossible.
     */
    // Stable floor evidence
    // The vanilla sidebar is the sorted top-N, so its lowest visible score
    // bounds every off-leaderboard player from above. But a single parsed
    // snapshot may be a one-frame redraw dropout in which rows are missing;
    // acting on it instantly produced bogus floors that irreversibly
    // collapsed fork uncertainty. Floor inferences therefore only start once
    // the same roster+floor has survived unchanged for STABLE_FLOOR_TICKS,
    // remain active as later point evidence arrives, and never run while a
    // rejoin window is unresolved (stale currentRound).

    private void updateStableFloor(Map<String, Integer> visible) {
        Integer floor = leaderboardFloor(visible);
        if (floor == null) {
            resetPendingFloor();
            return;
        }
        if (pendingFloorScores != null
                && pendingFloorValue == floor.intValue()
                && pendingFloorScores.keySet().equals(visible.keySet())) {
            // Same evidence shape (score growth above the floor is fine):
            // refresh values, keep aging.
            pendingFloorScores = new HashMap<String, Integer>(visible);
            return;
        }
        pendingFloorScores = new HashMap<String, Integer>(visible);
        pendingFloorValue = floor.intValue();
        pendingFloorSinceTick = tickCounter;
    }

    private void applyStableFloorIfReady() {
        if (game == null || pendingFloorScores == null) return;
        if (tickCounter - pendingFloorSinceTick < STABLE_FLOOR_TICKS) return;
        if (game.roundCountUnknown || game.pendingRejoinFromRound >= 0) {
            if (game.rejoinGuessOrderUncertain && pendingFloorValue > 0) {
                reconcileOffLeaderboardForksAgainstFloor(pendingFloorScores);
            }
            return;
        }
        reconcileOffLeaderboardForksAgainstFloor(pendingFloorScores);
        if (pendingFloorValue == 0) {
            settleOffLeaderboardZeros(pendingFloorScores);
        }
        reconcileOffLeaderboardFirstRoundZeros(pendingFloorScores);
    }

    private void resetPendingFloor() {
        pendingFloorScores = null;
        pendingFloorValue = 0;
        pendingFloorSinceTick = 0;
    }

    /**
     * Sorted-board deduction: a stable board with a visible 0 row proves every
     * active off-leaderboard player still has exactly 0 points in total. Their
     * gap forks settle to definite zeros and their missed-round uncertainty is
     * dropped; no (!) badge for a score that is fully deducible. Skips
     * leavers (their rows are removed from the board, not sorted below it)
     * and players whose proven minimum contradicts the floor.
     */
    private void settleOffLeaderboardZeros(Map<String, Integer> observedScores) {
        if (game == null || observedScores == null || observedScores.isEmpty()) return;
        for (GtbPlayer p : game.players.values()) {
            if (p == null || p.name == null || p.points == null || p.isUser) continue;
            if (isConfirmedLeaver(p)) continue;
            if (hasScoreIgnoreCase(observedScores, p.name)) continue;
            if (p.totalMin() != 0) continue;
            for (int i = 0; i < p.points.length && i < game.currentRound - 1; i++) {
                if (p.points[i] != null && p.points[i].kind == PointEntry.Kind.FORK) {
                    p.points[i] = PointEntry.definite(0);
                }
            }
            boolean anyForkLeft = false;
            for (PointEntry e : p.points) {
                if (e != null && e.kind == PointEntry.Kind.FORK) {
                    anyForkLeft = true;
                    break;
                }
            }
            if (!anyForkLeft) {
                clearDisconnectScoreUncertainty(p);
            }
        }
    }

    private void reconcileOffLeaderboardForksAgainstFloor(Map<String, Integer> observedScores) {
        if (game == null || observedScores == null || observedScores.isEmpty()) return;
        Integer floor = leaderboardFloor(observedScores);
        if (floor == null) return;

        for (GtbPlayer p : game.players.values()) {
            if (p == null || p.name == null || p.points == null) continue;
            if (isConfirmedLeaver(p)) continue;
            if (hasScoreIgnoreCase(observedScores, p.name)) continue;
            pruneForksAboveLeaderboardFloor(p, floor.intValue());
            if (knownScoreLowerBound(p) == floor.intValue()) {
                collapseForks(p, floor.intValue());
                clearDisconnectScoreUncertainty(p);
            }
        }
    }

    private int knownScoreLowerBound(GtbPlayer p) {
        if (p == null) return 0;
        int lower = p.totalMin();
        lower = Math.max(lower, p.observedScoreFloor);
        lower = Math.max(lower, p.preDisconnectScoreBaseline);
        return lower;
    }

    private boolean isConfirmedLeaver(GtbPlayer p) {
        if (p == null || p.name == null) return false;
        return p.leaverState == GtbPlayer.LeaverState.LEAVER
                || GameStats.INSTANCE.isLeaver(p.name);
    }

    private Integer leaderboardFloor(Map<String, Integer> observedScores) {
        List<Integer> scores = new ArrayList<Integer>();
        for (Integer score : observedScores.values()) {
            if (score != null) scores.add(score);
        }
        if (scores.isEmpty()) return null;
        Collections.sort(scores, Collections.reverseOrder());
        return scores.get(Math.min(2, scores.size() - 1));
    }

    private void pruneForksAboveLeaderboardFloor(GtbPlayer p, int floor) {
        for (int i = 0; i < p.points.length; i++) {
            PointEntry e = p.points[i];
            if (e == null || e.kind != PointEntry.Kind.FORK || e.fork == null || e.fork.isEmpty()) {
                continue;
            }

            int fixedMin = totalMinExcept(p.points, i);
            Set<Integer> kept = new HashSet<Integer>();
            for (Integer v : e.fork) {
                if (v != null && fixedMin + v.intValue() <= floor) {
                    kept.add(v);
                }
            }
            if (!kept.isEmpty() && kept.size() < e.fork.size()) {
                if (kept.size() == 1) {
                    p.points[i] = PointEntry.definite(kept.iterator().next().intValue());
                } else {
                    p.points[i] = PointEntry.narrowedFork(kept);
                }
            }
        }
    }

    private int totalMinExcept(PointEntry[] points, int excludedIndex) {
        int sum = 0;
        for (int i = 0; i < points.length; i++) {
            if (i == excludedIndex) continue;
            PointEntry e = points[i];
            if (e == null || e.kind == PointEntry.Kind.ZERO) continue;
            if (e.kind == PointEntry.Kind.DEFINITE) {
                sum += e.value;
            } else if (e.fork != null && !e.fork.isEmpty()) {
                sum += e.min();
            }
        }
        return sum;
    }
}
