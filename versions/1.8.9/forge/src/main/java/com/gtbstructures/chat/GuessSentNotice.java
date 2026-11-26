package com.gtbstructures.chat;

/** Client-thread state for guesses sent without relying on a server chat echo. */
final class GuessSentNotice {
    private static final long COOLDOWN_MS = 3000L;

    private long cooldownEnd;
    private long lastSentAt;
    private int spamCount;

    String onOutbound(String message, long now, boolean onlySpam, int minimumSpam,
                      boolean showGuess) {
        if (message == null || message.trim().isEmpty() || message.trim().startsWith("/")) {
            return null;
        }
        if (cooldownEnd > 0L && now < lastSentAt) reset();
        if (cooldownEnd > now) {
            if (spamCount < Integer.MAX_VALUE) spamCount++;
            return null;
        }

        boolean notify = !onlySpam || spamCount >= Math.max(1, minimumSpam);
        cooldownEnd = now + COOLDOWN_MS;
        lastSentAt = now;
        spamCount = 0;
        return notify ? format(message, showGuess) : null;
    }

    void reset() {
        cooldownEnd = 0L;
        lastSentAt = 0L;
        spamCount = 0;
    }

    static String format(String message, boolean showGuess) {
        return "\u00a77Successfully sent guess" + (showGuess ? ": \u00a7f" + message : "");
    }
}
