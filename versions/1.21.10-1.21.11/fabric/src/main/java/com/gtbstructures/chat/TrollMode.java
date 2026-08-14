package com.gtbstructures.chat;

import com.gtbstructures.GtbStructuresSettings;
import com.gtbstructures.stats.GameStats;
import com.gtbstructures.translation.TranslationData;

public final class TrollMode {
    private static final int MAX_CHAT_LENGTH = 256;
    private static final String TROLL_MARKER = "\uA781";

    private TrollMode() {
    }

    public static String transformOutbound(String message) {
        return transform(message, GameStats.INSTANCE.getState(),
                GameStats.INSTANCE.hasLocalPlayerCorrectlyGuessedCurrentRound(),
                GtbStructuresSettings.INSTANCE.trollMode);
    }

    static String transformForTest(String message, GameStats.TrackingState state,
                                   boolean alreadyGuessed, boolean enabled) {
        return transform(message, state, alreadyGuessed, enabled);
    }

    private static String transform(String message, GameStats.TrackingState state,
                                    boolean alreadyGuessed, boolean enabled) {
        if (!isEligible(message, state, alreadyGuessed, enabled)) return message;
        if (message.length() + TROLL_MARKER.length() > MAX_CHAT_LENGTH) return message;
        return message + TROLL_MARKER;
    }

    private static boolean isEligible(String message, GameStats.TrackingState state,
                                      boolean alreadyGuessed, boolean enabled) {
        if (!enabled || alreadyGuessed || state != GameStats.TrackingState.ROUND_BUILD
                || message == null) {
            return false;
        }
        return !TranslationData.findActiveGuessThemes(message).isEmpty();
    }
}
