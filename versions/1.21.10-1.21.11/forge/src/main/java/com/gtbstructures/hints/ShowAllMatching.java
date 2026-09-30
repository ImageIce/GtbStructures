package com.gtbstructures.hints;

import com.gtbstructures.chat.ChatHeader;
import com.gtbstructures.GtbStructuresSettings;
import com.gtbstructures.translation.TranslationData;

import java.util.Map;
import java.util.Set;

/**
 * Platform-neutral core for /gtbs showallmatching. Platform event handlers own
 * chat scheduling; this class owns the 1.8.9 matching and formatting rules.
 */
public final class ShowAllMatching {
    /** @deprecated Use {@link #prefix()} so custom headers are reflected at send time. */
    @Deprecated
    public static final String PREFIX = ChatHeader.render(ChatHeader.DEFAULT_RAW) + "\u00a7r\u00a77 ";

    public static final class RoundState {
        public String langCode;
        public String builder;
        public String effectiveInitialHint;
        public int themeCharCount;
        public boolean showAllMatchingSent;
    }

    private ShowAllMatching() {
    }

    public static void captureInitialHint(RoundState round, String trimmedHint) {
        if (round == null || round.showAllMatchingSent || round.effectiveInitialHint != null) return;
        if (trimmedHint == null || trimmedHint.isEmpty()) return;
        if (trimmedHint.indexOf('_') < 0) return;
        if (!hasRevealedChar(trimmedHint)) return;
        round.effectiveInitialHint = trimmedHint;
    }

    public static String maybeBuildMessage(boolean enabled, RoundState round, String localIgn,
                                           boolean showEnglishConfig) {
        if (!enabled) return null;
        if (round == null || round.showAllMatchingSent) return null;

        if (round.builder != null && localIgn != null && round.builder.equalsIgnoreCase(localIgn)) {
            round.showAllMatchingSent = true;
            return null;
        }

        round.showAllMatchingSent = true;
        return buildMessage(round.langCode, round.effectiveInitialHint,
                round.themeCharCount, showEnglishConfig,
                GtbStructuresSettings.INSTANCE.showAllMatchingShowCount);
    }

    public static String buildMessage(String langCode, String hint, int themeCharCount,
                                      boolean showEnglishConfig) {
        return buildMessage(langCode, hint, themeCharCount, showEnglishConfig,
                GtbStructuresSettings.INSTANCE.showAllMatchingShowCount);
    }

    public static String buildMessage(String langCode, String hint, int themeCharCount,
                                      boolean showEnglishConfig, boolean showCountConfig) {
        Map<String, Set<String>> matches;
        String displayedHint;
        if (hint != null && !hint.isEmpty()) {
            matches = TranslationData.findMatchingDisplayedThemes(langCode, hint);
            displayedHint = hint;
        } else if (themeCharCount == 1) {
            matches = TranslationData.findOneLetterDisplayedThemes(langCode);
            displayedHint = "_";
        } else {
            return null;
        }

        if (matches == null || matches.isEmpty()) return null;

        boolean isEnglish = isEnglish(langCode);
        boolean isPirate = isPirate(langCode);
        boolean withEnglish = showEnglishConfig && !isEnglish;

        StringBuilder sb = new StringBuilder();
        sb.append(prefix());
        sb.append("All themes matching the initial hint (\u00a7e")
                .append(displayedHint)
                .append("\u00a77): ");

        boolean first = true;
        for (Map.Entry<String, Set<String>> entry : matches.entrySet()) {
            if (!first) sb.append("\u00a77, ");
            first = false;
            sb.append("\u00a7f").append(entry.getKey());
            if (withEnglish && shouldShowEnglish(isPirate, entry.getKey(), entry.getValue())) {
                sb.append("\u00a77 (");
                boolean firstEnglish = true;
                for (String english : entry.getValue()) {
                    if (!firstEnglish) sb.append(", ");
                    firstEnglish = false;
                    sb.append(english);
                }
                sb.append(")");
            }
        }
        if (showCountConfig) sb.append(" \u00a77(\u00a7e").append(matches.size()).append("\u00a77)");
        return sb.toString();
    }

    private static boolean hasRevealedChar(String hint) {
        for (int i = 0; i < hint.length(); i++) {
            char c = hint.charAt(i);
            if (c != '_' && c != ' ') return true;
        }
        return false;
    }

    private static boolean shouldShowEnglish(boolean isPirate, String displayed,
                                             Set<String> englishOrigins) {
        if (!isPirate) return true;
        if (englishOrigins == null || englishOrigins.isEmpty()) return false;
        return englishOrigins.size() != 1 || !englishOrigins.contains(displayed);
    }

    private static boolean isEnglish(String langCode) {
        return langCode != null && "en".equalsIgnoreCase(langCode);
    }

    private static boolean isPirate(String langCode) {
        return langCode != null && "pirate".equalsIgnoreCase(langCode);
    }

    public static String prefix() {
        return ChatHeader.prefix();
    }
}
