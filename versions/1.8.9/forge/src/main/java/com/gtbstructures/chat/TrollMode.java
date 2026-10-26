package com.gtbstructures.chat;

import com.gtbstructures.GtbStructures;
import com.gtbstructures.stats.GameStats;
import com.gtbstructures.translation.TranslationData;

public final class TrollMode {
    private static final int MAX_CHAT_LENGTH = 100;
    private static final String TROLL_MARKER = "\uA781";

    private TrollMode() {
    }

    /** Explicit one-shot chat: no setting, round-state, or theme lookup. */
    public static String prepareOneShot(String message) {
        if (message == null || message.codePoints().allMatch(c ->
                Character.isWhitespace(c) || Character.isSpaceChar(c))
                || message.length() + TROLL_MARKER.length() > MAX_CHAT_LENGTH) {
            return null;
        }
        return message + TROLL_MARKER;
    }

    public static String transformOutbound(String message) {
        return transform(message, GameStats.INSTANCE.getState(),
                GameStats.INSTANCE.hasLocalPlayerCorrectlyGuessedCurrentRound(),
                GtbStructures.trollMode);
    }

    static String transformForTest(String message,
                                   GameStats.TrackingState state,
                                   boolean alreadyGuessed,
                                   boolean enabled) {
        return transform(message, state, alreadyGuessed, enabled);
    }

    private static String transform(String message,
                                    GameStats.TrackingState state,
                                    boolean alreadyGuessed,
                                    boolean enabled) {
        if (!enabled || alreadyGuessed || state != GameStats.TrackingState.ROUND_BUILD
                || message == null) {
            return message;
        }
        if (message.length() + TROLL_MARKER.length() > MAX_CHAT_LENGTH
                || TranslationData.findActiveGuessThemes(message).isEmpty()) {
            return message;
        }
        return message + TROLL_MARKER;
    }
}
