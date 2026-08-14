package com.gtbstructures.chat;

import com.gtbstructures.scoreboard.GtbGame;

/**
 * Platform-neutral rules for the GTB chat cooldown counter.
 */
public final class ChatCooldownLogic {

    private static final long COOLDOWN_MS = 3100L;
    private static final long DISPLAY_MAX_MS = 3000L;
    private static final long START_GATE_PADDING_MS = COOLDOWN_MS - DISPLAY_MAX_MS;
    private static final int BAR_WIDTH = 42;

    private ChatCooldownLogic() {
    }

    public static long cooldownMillis() {
        return COOLDOWN_MS;
    }

    public static boolean isNormalChatMessage(String message) {
        if (message == null) return false;
        String trimmed = message.trim();
        return !trimmed.isEmpty() && !trimmed.startsWith("/");
    }

    public static boolean shouldStartCooldown(boolean enabled, GtbGame.State state,
                                              String message, long now, long cooldownEnd) {
        return shouldStartCooldown(enabled, state, message, now, cooldownEnd, false);
    }

    public static boolean shouldStartCooldown(boolean enabled, GtbGame.State state,
                                              String message, long now, long cooldownEnd,
                                              boolean suppressAfterCorrectGuess) {
        return enabled
                && !suppressAfterCorrectGuess
                && state == GtbGame.State.ROUND_BUILD
                && isNormalChatMessage(message)
                && now >= cooldownEnd;
    }

    public static boolean shouldAwaitAcceptance(boolean enabled, GtbGame.State state,
                                                String message, long now, long cooldownEnd,
                                                boolean suppressAfterCorrectGuess) {
        return enabled
                && !suppressAfterCorrectGuess
                && state == GtbGame.State.ROUND_BUILD
                && isNormalChatMessage(message)
                && cooldownEnd > now
                && now >= cooldownEnd - START_GATE_PADDING_MS;
    }

    public static boolean shouldRenderForRound(boolean enabled, boolean previewActive,
                                               GtbGame.State state, String builder,
                                               String localIgn) {
        if (!enabled || previewActive) return false;
        if (state != GtbGame.State.ROUND_BUILD) return false;
        if (builder == null || localIgn == null || localIgn.isEmpty()) return true;
        return !builder.equalsIgnoreCase(localIgn);
    }

    public static String displayRemaining(long remainingMs) {
        long clamped = Math.max(0L, Math.min(DISPLAY_MAX_MS, remainingMs));
        int tenths = (int) ((clamped + 99L) / 100L);
        int seconds = tenths / 10;
        int dec = tenths % 10;
        return seconds + "." + dec + "s";
    }

    public static int fillWidth(int width, long remainingMs) {
        long clamped = Math.max(0L, Math.min(DISPLAY_MAX_MS, remainingMs));
        return Math.max(0, Math.min(width, (int) ((clamped * width + DISPLAY_MAX_MS - 1L) / DISPLAY_MAX_MS)));
    }

    public static int barWidth() {
        return BAR_WIDTH;
    }
}
