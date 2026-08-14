package com.gtbstructures.hints;

import com.gtbstructures.stats.GameStats;
import com.gtbstructures.stats.LangPatterns;

import java.util.ArrayList;
import java.util.List;

public class HintOverlay {

    /**
     * Result of parsing an action-bar message. Built once per message in
     * {@link #parse(String)} so callers don't redundantly strip control codes,
     * the [C] suffix, and the theme prefix/suffix.
     */
    public static final class ParsedHint {
        public final boolean isHint;
        /** Word-length structure (e.g. "7-3"); empty when {@link #isHint} is false. */
        public final String structure;
        /** Hint text after the prefix/suffix have been stripped (still contains '_' / spaces). */
        public final String trimmedHint;

        static final ParsedHint EMPTY = new ParsedHint(false, "", "");

        ParsedHint(boolean isHint, String structure, String trimmedHint) {
            this.isHint = isHint;
            this.structure = structure;
            this.trimmedHint = trimmedHint;
        }
    }

    public static String getStructure(String hint) {
        return parse(hint).structure;
    }

    public static ParsedHint parsePreview(String hint) {
        return parse(hint, false);
    }

    /**
     * Strip control codes + ChatReplacer [C] suffix, find the theme prefix/suffix,
     * and compute the word-length structure — all in one pass. Both the overlay
     * and {@link GameStats#onHint} consume the same result, so the parsing can't
     * drift between them (a previous bug had {@code GameStats.onHint} truncating
     * with {@code indexOf} while the overlay used {@code lastIndexOf}).
     */
    public static ParsedHint parse(String hint) {
        return parse(hint, true);
    }

    private static ParsedHint parse(String hint, boolean requireActiveGame) {
        if (hint == null) return ParsedHint.EMPTY;
        hint = net.minecraft.util.StringUtils.stripControlCodes(hint);
        if (hint == null) return ParsedHint.EMPTY;
        hint = hint.trim();

        // Strip " [C]" (with space) and bare "[C]" ChatReplacer suffix
        if (hint.endsWith(" [C]")) {
            hint = hint.substring(0, hint.length() - 4);
        } else if (hint.endsWith("[C]")) {
            hint = hint.substring(0, hint.length() - 3);
        }
        hint = hint.trim();

        if (hint.isEmpty()) return ParsedHint.EMPTY;

        // Check that we're in an active GTB game via GameStats state
        if (requireActiveGame) {
            GameStats.TrackingState state = GameStats.INSTANCE.getState();
            if (!GameStats.isActiveGame(state) && state != GameStats.TrackingState.PAUSED) {
                return ParsedHint.EMPTY;
            }
        }

        // Remove known prefix/suffix
        boolean isHint = false;

        for (LangPatterns.ThemeRevealPattern theme : LangPatterns.themeRevealPatterns()) {
            String prefix = theme.prefix;
            String suffix = theme.suffix;

            if (hint.contains(prefix)) {
                isHint = true;
                hint = hint.substring(hint.indexOf(prefix) + prefix.length());
                if (!suffix.isEmpty() && hint.contains(suffix)) {
                    // Use lastIndexOf to avoid truncating themes that contain the suffix string
                    hint = hint.substring(0, hint.lastIndexOf(suffix));
                }
                break;
            }
        }

        if (!isHint) return ParsedHint.EMPTY;

        List<Integer> counts = new ArrayList<>();
        int len = 0;
        for (int i = 0; i < hint.length(); i++) {
            char c = hint.charAt(i);
            if (c == ' ') {
                if (len > 0) {
                    counts.add(len);
                    len = 0;
                }
            } else len++;
        }
        if (len > 0) counts.add(len);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < counts.size(); i++) {
            sb.append(counts.get(i));
            if (i < counts.size() - 1) sb.append("-");
        }

        return new ParsedHint(true, sb.toString(), hint);
    }
}
