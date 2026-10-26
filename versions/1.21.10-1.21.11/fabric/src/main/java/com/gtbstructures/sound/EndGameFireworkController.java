package com.gtbstructures.sound;

import com.gtbstructures.stats.GameStats;

/** Filters only vanilla game-end fireworks while the GTB post-game screen is active. */
public final class EndGameFireworkController {
    public static final EndGameFireworkController INSTANCE = new EndGameFireworkController();

    private static final String[] FIREWORK_SOUNDS = {
            "entity.firework_rocket.launch", "entity.firework_rocket.blast", "entity.firework_rocket.blast_far",
            "entity.firework_rocket.large_blast", "entity.firework_rocket.large_blast_far",
            "entity.firework_rocket.twinkle", "entity.firework_rocket.twinkle_far"
    };

    private boolean observedGtbScoreboard;
    private GameStats.TrackingState observedState = GameStats.TrackingState.NOT_IN_GAME;
    private boolean postGameLatched;
    private boolean postGameConfirmed;

    private EndGameFireworkController() { }

    public synchronized void observeScoreboard(GameStats.TrackingState state, boolean gtbScoreboard) {
        observedGtbScoreboard = gtbScoreboard;
        observedState = state == null ? GameStats.TrackingState.NOT_IN_GAME : state;
        if (!gtbScoreboard || observedState == GameStats.TrackingState.NOT_IN_GAME
                || observedState == GameStats.TrackingState.LOBBY
                || observedState == GameStats.TrackingState.PAUSED) {
            clearLatch();
            return;
        }
        if (observedState == GameStats.TrackingState.POST_GAME) {
            postGameLatched = true;
            postGameConfirmed = true;
            return;
        }
        if (!postGameLatched) return;
        if (postGameConfirmed && isNewRoundState(observedState)) {
            clearLatch();
        }
    }

    /** Winners chat is trusted only while the last observed sidebar belongs to an active GTB game. */
    public synchronized void observeWinnersNotice() {
        if (!observedGtbScoreboard || !(isRoundState(observedState)
                || observedState == GameStats.TrackingState.POST_GAME)) return;
        postGameLatched = true;
        postGameConfirmed = observedState == GameStats.TrackingState.POST_GAME;
    }

    public synchronized void onRoundBoundary() { clearLatch(); }
    public synchronized void reset() {
        observedGtbScoreboard = false;
        observedState = GameStats.TrackingState.NOT_IN_GAME;
        clearLatch();
    }

    public synchronized boolean isPostGameLatched() { return postGameLatched; }

    public static boolean isVanillaFireworkSound(String soundName) {
        if (soundName == null) return false;
        String path = soundName.startsWith("minecraft:")
                ? soundName.substring("minecraft:".length()) : soundName;
        for (String candidate : FIREWORK_SOUNDS) {
            if (candidate.equals(path)) return true;
        }
        return false;
    }

    public static boolean shouldSuppress(String soundName, boolean masterEnabled,
                                         boolean optionEnabled, boolean postGame,
                                         boolean playingReplacement) {
        return masterEnabled && !optionEnabled && postGame && !playingReplacement
                && isVanillaFireworkSound(soundName);
    }

    private void clearLatch() {
        postGameLatched = false;
        postGameConfirmed = false;
    }

    private static boolean isRoundState(GameStats.TrackingState state) {
        return state == GameStats.TrackingState.ROUND_PRE
                || state == GameStats.TrackingState.ROUND_BUILD
                || state == GameStats.TrackingState.ROUND_END;
    }

    private static boolean isNewRoundState(GameStats.TrackingState state) {
        return state == GameStats.TrackingState.ROUND_PRE
                || state == GameStats.TrackingState.ROUND_BUILD;
    }
}
