package com.gtbstructures.translation;

import com.gtbstructures.gtw.GtwLogic;

import java.util.Locale;

/** Received-chat-only handling for Hypixel's Cyrillic/Latin lookalike rendering. */
public final class HypixelGuessChat {
    private HypixelGuessChat() {
    }

    /**
     * Matches what Hypixel may display for an accepted source spelling. The
     * normal accepted-answer matcher remains authoritative and unchanged.
     */
    public static boolean matchesObserved(String guess, String originalTranslation, String lang) {
        if (GtwLogic.gtbMatches(guess, originalTranslation, lang)) return true;
        if (guess == null || originalTranslation == null) return false;
        String input = guess.trim();
        if (input.isEmpty()) return false;

        if ("co".equals(lang)) {
            return emissionEquals(input, originalTranslation, true);
        }

        if (emissionEquals(input, originalTranslation)) return true;

        if (originalTranslation.length() > 0
                && originalTranslation.charAt(originalTranslation.length() - 1) == 's'
                && emissionEquals(input, originalTranslation.substring(0,
                        originalTranslation.length() - 1))) {
            return true;
        }
        if (input.endsWith("s")
                && emissionEquals(input.substring(0, input.length() - 1), originalTranslation)) {
            return true;
        }

        if (emissionEquals(removeWhitespace(input), removeWhitespace(originalTranslation))) {
            return true;
        }

        return emissionEquals(GtwLogic.accentNormForReceivedGuess(input),
                GtwLogic.accentNormForReceivedGuess(originalTranslation));
    }

    /** Coarse received-only bucket key; callers must validate each candidate with matchesObserved. */
    static String coarseKey(String value) {
        if (value == null) return "";
        String accentNormalized = GtwLogic.accentNormForReceivedGuess(value);
        StringBuilder skeleton = new StringBuilder(accentNormalized.length());
        for (int offset = 0; offset < accentNormalized.length();) {
            int codePoint = accentNormalized.codePointAt(offset);
            int lower = Character.toLowerCase(codePoint);
            int upper = Character.toUpperCase(codePoint);
            int renderedLower = render(lower);
            int renderedUpper = render(upper);
            int canonical = renderedLower != lower ? renderedLower
                    : renderedUpper != upper ? renderedUpper : codePoint;
            skeleton.appendCodePoint(canonical);
            offset += Character.charCount(codePoint);
        }

        String normalized = skeleton.toString().toLowerCase(Locale.ROOT).replace('\u0131', 'i');
        StringBuilder compact = new StringBuilder(normalized.length());
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (!Character.isWhitespace(c)) compact.append(c);
        }
        while (compact.length() > 0 && compact.charAt(compact.length() - 1) == 's') {
            compact.setLength(compact.length() - 1);
        }
        return compact.toString();
    }

    /** True when compatibility conversion could make a received Latin form ambiguous. */
    static boolean containsLatinLookalikeOutput(String value) {
        if (value == null) return false;
        for (int i = 0; i < value.length(); i++) {
            switch (value.charAt(i)) {
                case 'a': case 'e': case 'o': case 'p': case 'c': case 'x':
                case 'A': case 'B': case 'E': case 'K': case 'M': case 'H':
                case 'O': case 'P': case 'C': case 'T': case 'X':
                    return true;
                default:
                    break;
            }
        }
        return false;
    }

    private static boolean emissionEquals(String received, String source) {
        return emissionEquals(received, source, false);
    }

    private static boolean emissionEquals(String received, String source, boolean complement) {
        if (received == null || source == null) return false;
        StringBuilder submitted = new StringBuilder(received.length());
        int receivedOffset = 0;
        int sourceOffset = 0;
        while (sourceOffset < source.length()) {
            int sourceCodePoint = source.codePointAt(sourceOffset);
            if (receivedOffset >= received.length()) return false;
            int receivedCodePoint = received.codePointAt(receivedOffset);
            int lower = Character.toLowerCase(sourceCodePoint);
            int upper = Character.toUpperCase(sourceCodePoint);
            // Restore only a confirmed emission of this source character. Literal
            // Latin characters and old unnormalized Cyrillic stay untouched.
            if (render(lower) != lower && receivedCodePoint == render(lower)) {
                submitted.appendCodePoint(lower);
            } else if (render(upper) != upper && receivedCodePoint == render(upper)) {
                submitted.appendCodePoint(upper);
            } else {
                submitted.appendCodePoint(receivedCodePoint);
            }
            sourceOffset += Character.charCount(sourceCodePoint);
            receivedOffset += Character.charCount(receivedCodePoint);
        }
        if (receivedOffset != received.length()) return false;
        // Fold whole strings, retaining the strict matcher's contextual casing
        // and its different dotless-i rules for complements and other languages.
        if (complement) {
            return submitted.toString().toLowerCase(Locale.ROOT)
                    .equals(source.toLowerCase(Locale.ROOT));
        }
        return submitted.toString().toLowerCase().replace('\u0131', 'i')
                .equals(source.toLowerCase().replace('\u0131', 'i'));
    }

    private static String removeWhitespace(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!Character.isWhitespace(c)) out.append(c);
        }
        return out.toString();
    }

    private static int render(int codePoint) {
        switch (codePoint) {
            case '\u0430': return 'a';
            case '\u0435': return 'e';
            case '\u043e': return 'o';
            case '\u0440': return 'p';
            case '\u0441': return 'c';
            case '\u0445': return 'x';
            case '\u0410': return 'A';
            case '\u0412': return 'B';
            case '\u0415': return 'E';
            case '\u041a': return 'K';
            case '\u041c': return 'M';
            case '\u041d': return 'H';
            case '\u041e': return 'O';
            case '\u0420': return 'P';
            case '\u0421': return 'C';
            case '\u0422': return 'T';
            case '\u0425': return 'X';
            default: return codePoint;
        }
    }
}
