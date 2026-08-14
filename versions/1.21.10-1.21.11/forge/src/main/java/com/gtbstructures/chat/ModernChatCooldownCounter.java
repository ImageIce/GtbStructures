package com.gtbstructures.chat;

import com.gtbstructures.scoreboard.GtbGame;

/**
 * Runtime state for the modern GTB chat cooldown counter.
 */
public final class ModernChatCooldownCounter {
    private static final long ACCEPTANCE_CONFIRMATION_MS = 5000L;
    private static volatile long cooldownEndMs;
    private static volatile long pendingRestartSentAtMs;
    private static volatile boolean suppressUntilRoundEnds;

    private ModernChatCooldownCounter() {
    }

    public static void onRoundStart() {
        cooldownEndMs = 0L;
        pendingRestartSentAtMs = 0L;
        suppressUntilRoundEnds = false;
    }

    public static void onUserCorrectGuess() {
        cooldownEndMs = 0L;
        pendingRestartSentAtMs = 0L;
        suppressUntilRoundEnds = true;
    }

    public static void onOutboundChat(boolean enabled, GtbGame.State state, String message, long now) {
        expirePendingRestart(now);
        if (cooldownEndMs > 0L && now >= cooldownEndMs) cooldownEndMs = 0L;
        if (ChatCooldownLogic.shouldStartCooldown(enabled, state, message, now,
                cooldownEndMs, suppressUntilRoundEnds)) {
            cooldownEndMs = now + ChatCooldownLogic.cooldownMillis();
            pendingRestartSentAtMs = 0L;
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
    }

    public static void onTick(boolean enabled, GtbGame.State state, long now) {
        if (!enabled || state != GtbGame.State.ROUND_BUILD) {
            cooldownEndMs = 0L;
            pendingRestartSentAtMs = 0L;
            suppressUntilRoundEnds = false;
            return;
        }
        if (suppressUntilRoundEnds) {
            cooldownEndMs = 0L;
            pendingRestartSentAtMs = 0L;
            return;
        }
        if (cooldownEndMs > 0L && now >= cooldownEndMs) {
            cooldownEndMs = 0L;
        }
        expirePendingRestart(now);
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
        cooldownEndMs = 0L;
        pendingRestartSentAtMs = 0L;
        suppressUntilRoundEnds = false;
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
