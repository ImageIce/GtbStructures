package com.gtbstructures.chat;

import com.gtbstructures.scoreboard.GtbGame;

/**
 * Runtime state for the modern GTB chat cooldown counter.
 */
public final class ModernChatCooldownCounter {
    private static final long ACCEPTANCE_CONFIRMATION_MS = 5000L;
    private static final long PROGRESS_SOUND_GRACE_MS = 250L;
    private static final long READY_SOUND_GRACE_MS = 1000L;
    public static final int SOUND_NONE = 0;
    public static final int SOUND_PROGRESS = 1;
    public static final int SOUND_READY = 2;
    private static volatile long cooldownEndMs;
    private static volatile long pendingRestartSentAtMs;
    private static volatile int progressSoundMark;
    private static volatile boolean suppressUntilRoundEnds;
    private static volatile long previewCooldownEndMs;
    private static volatile int previewProgressSoundMark;

    private ModernChatCooldownCounter() {
    }

    public static void onRoundStart() {
        cooldownEndMs = 0L;
        pendingRestartSentAtMs = 0L;
        progressSoundMark = 0;
        suppressUntilRoundEnds = false;
    }

    public static void onUserCorrectGuess() {
        cooldownEndMs = 0L;
        pendingRestartSentAtMs = 0L;
        progressSoundMark = 0;
        suppressUntilRoundEnds = true;
    }

    public static void onOutboundChat(boolean enabled, GtbGame.State state, String message, long now) {
        // Called after MODIFY_CHAT / Forge's GTBS transforms, even if the HUD is disabled.
        com.gtbstructures.stats.GameStats.INSTANCE.onOutboundGuess(message, now);
        expirePendingRestart(now);
        if (cooldownEndMs > 0L && now >= cooldownEndMs) cooldownEndMs = 0L;
        if (ChatCooldownLogic.shouldStartCooldown(enabled, state, message, now,
                cooldownEndMs, suppressUntilRoundEnds)) {
            cooldownEndMs = now + ChatCooldownLogic.cooldownMillis();
            pendingRestartSentAtMs = 0L;
            progressSoundMark = 0;
        } else if (ChatCooldownLogic.shouldAwaitAcceptance(enabled, state, message, now,
                cooldownEndMs, suppressUntilRoundEnds)) {
            pendingRestartSentAtMs = now;
        }
    }

    public static void onUserChatAccepted(long now) {
        if (suppressUntilRoundEnds || pendingRestartSentAtMs <= 0L) return;
        long sentAt = pendingRestartSentAtMs;
        pendingRestartSentAtMs = 0L;
        if (now < sentAt || now - sentAt > ACCEPTANCE_CONFIRMATION_MS) return;
        long confirmedEnd = sentAt + ChatCooldownLogic.cooldownMillis();
        cooldownEndMs = confirmedEnd > now ? confirmedEnd : 0L;
        progressSoundMark = (int) Math.min(2L, (now - sentAt) / 1000L);
    }

    public static void onTick(boolean enabled, GtbGame.State state, long now) {
        onTick(enabled, state, now, false, false);
    }

    public static int onTick(boolean enabled, GtbGame.State state, long now,
                             boolean sfxEnabled, boolean progressBeats) {
        if (!enabled || state != GtbGame.State.ROUND_BUILD) {
            resetLiveState();
            return SOUND_NONE;
        }
        if (suppressUntilRoundEnds) {
            cooldownEndMs = 0L;
            pendingRestartSentAtMs = 0L;
            progressSoundMark = 0;
            return SOUND_NONE;
        }
        expirePendingRestart(now);
        int sound = cooldownSound(now, cooldownEndMs, progressSoundMark, sfxEnabled, progressBeats);
        if (cooldownEndMs > 0L && now >= cooldownEndMs) {
            cooldownEndMs = 0L;
            progressSoundMark = 0;
        } else if (cooldownEndMs > 0L) {
            progressSoundMark = Math.max(progressSoundMark, reachedProgressMark(now, cooldownEndMs));
        }
        return sound;
    }

    public static int onClientTick(boolean masterEnabled, boolean counterEnabled, boolean liveEnabled,
                                   boolean previewActive, GtbGame.State state, long now,
                                   boolean sfxEnabled, boolean progressBeats) {
        if (!masterEnabled) {
            onDisconnect();
            return SOUND_NONE;
        }
        boolean previewTesting = previewCooldownEndMs > 0L;
        int previewSound = updatePreviewTest(now, previewActive, counterEnabled, sfxEnabled, progressBeats);
        int liveSound = onTick(counterEnabled && liveEnabled, state, now, sfxEnabled, progressBeats);
        return previewTesting ? previewSound : liveSound;
    }

    public static void startPreviewTest() {
        startPreviewTest(System.currentTimeMillis());
    }

    public static void startPreviewTest(long now) {
        previewCooldownEndMs = now + ChatCooldownLogic.cooldownMillis();
        previewProgressSoundMark = 0;
    }

    public static void cancelPreviewTest() {
        previewCooldownEndMs = 0L;
        previewProgressSoundMark = 0;
    }

    public static long previewRemainingMs(long now) {
        return previewCooldownEndMs <= 0L ? -1L
                : Math.max(0L, Math.min(ChatCooldownLogic.cooldownMillis(), previewCooldownEndMs - now));
    }

    public static void onDisconnect() {
        resetLiveState();
        cancelPreviewTest();
    }

    static int updatePreviewTest(long now, boolean previewActive, boolean counterEnabled,
                                 boolean sfxEnabled, boolean progressBeats) {
        if (!previewActive || !counterEnabled) {
            cancelPreviewTest();
            return SOUND_NONE;
        }
        int sound = cooldownSound(now, previewCooldownEndMs, previewProgressSoundMark, sfxEnabled, progressBeats);
        if (previewCooldownEndMs > 0L && now >= previewCooldownEndMs) cancelPreviewTest();
        else if (previewCooldownEndMs > 0L) {
            previewProgressSoundMark = Math.max(previewProgressSoundMark, reachedProgressMark(now, previewCooldownEndMs));
        }
        return sound;
    }

    private static int cooldownSound(long now, long end, int playedProgressMark,
                                     boolean sfxEnabled, boolean progressBeats) {
        if (end <= 0L) return SOUND_NONE;
        if (now >= end) return sfxEnabled && now - end <= READY_SOUND_GRACE_MS ? SOUND_READY : SOUND_NONE;
        long elapsed = now - (end - ChatCooldownLogic.cooldownMillis());
        int reachedMark = reachedProgressMark(now, end);
        if (reachedMark <= playedProgressMark) return SOUND_NONE;
        return sfxEnabled && progressBeats && elapsed - reachedMark * 1000L <= PROGRESS_SOUND_GRACE_MS
                ? SOUND_PROGRESS : SOUND_NONE;
    }

    private static int reachedProgressMark(long now, long end) {
        return (int) Math.min(2L, Math.max(0L, now - (end - ChatCooldownLogic.cooldownMillis())) / 1000L);
    }

    private static void resetLiveState() {
        cooldownEndMs = 0L;
        pendingRestartSentAtMs = 0L;
        progressSoundMark = 0;
        suppressUntilRoundEnds = false;
    }

    private static void expirePendingRestart(long now) {
        if (pendingRestartSentAtMs > 0L
                && (now < pendingRestartSentAtMs
                || now - pendingRestartSentAtMs > ACCEPTANCE_CONFIRMATION_MS)) {
            pendingRestartSentAtMs = 0L;
        }
    }

    public static long remainingMs(long now) {
        return Math.max(0L, cooldownEndMs - now);
    }

    public static boolean shouldRender(boolean enabled, boolean previewActive, GtbGame.State state,
                                       String builder, String localIgn, long now) {
        return remainingMs(now) > 0L
                && ChatCooldownLogic.shouldRenderForRound(enabled, previewActive, state, builder, localIgn);
    }

    static void resetForTest() {
        onDisconnect();
    }

    static long cooldownEndForTest() {
        return cooldownEndMs;
    }

    static long pendingRestartForTest() {
        return pendingRestartSentAtMs;
    }

    static boolean suppressingForTest() {
        return suppressUntilRoundEnds;
    }
}
