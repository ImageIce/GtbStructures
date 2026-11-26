package com.gtbstructures.chat;

import com.gtbstructures.GtbStructuresSettings;

/** Client-thread state independent of the cooldown HUD and server chat echoes. */
final class GuessSentNotice {
    private static final long COOLDOWN_MS = 3000L;
    private long cooldownEnd;
    private long lastSentAt;
    private int spamCount;

    String onOutbound(String message, long now, boolean onlySpam, int minimumSpam,
                      boolean showGuess) {
        if (!ChatCooldownLogic.isNormalChatMessage(message)) return null;
        if (cooldownEnd > 0L && now < lastSentAt) reset();
        if (cooldownEnd > now) {
            if (spamCount < Integer.MAX_VALUE) spamCount++;
            return null;
        }
        int threshold = Math.max(GtbStructuresSettings.MIN_GUESS_SENT_SPAM_COUNT,
                Math.min(GtbStructuresSettings.MAX_GUESS_SENT_SPAM_COUNT, minimumSpam));
        boolean notify = !onlySpam || spamCount >= threshold;
        cooldownEnd = now + COOLDOWN_MS;
        lastSentAt = now;
        spamCount = 0;
        return notify ? "\u00a77Successfully sent guess"
                + (showGuess ? ": \u00a7f" + message : "") : null;
    }

    void reset() {
        cooldownEnd = 0L;
        lastSentAt = 0L;
        spamCount = 0;
    }
}
