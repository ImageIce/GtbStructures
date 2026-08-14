package com.gtbstructures.chat;

import com.gtbstructures.scoreboard.BuildBattleTitles;
import com.gtbstructures.scoreboard.GtbGame;
import com.gtbstructures.translation.TranslationData;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Platform-neutral guess-peek parsing and tooltip logic.
 */
public final class GuessChatAnnotator {

    private GuessChatAnnotator() {
    }

    public static ParsedGuess parseGuess(String unformatted) {
        if (unformatted == null) return null;
        String visible = stripControlCodes(unformatted);
        String clean = stripChatSuffix(visible);
        if (clean == null || clean.isEmpty()) return null;

        boolean isPlayerMessage = clean.contains(": ")
                && BuildBattleTitles.startsWithChatTitle(stripEmojis(clean).trim());
        if (!isPlayerMessage) return null;

        int colonIdx = clean.indexOf(": ");
        if (colonIdx < 0) return null;
        String guess = clean.substring(colonIdx + 2).trim();
        if (guess.isEmpty()) return null;

        int visibleStart = visible.indexOf(guess, Math.min(colonIdx + 2, visible.length()));
        if (visibleStart < 0) visibleStart = Math.min(colonIdx + 2, visible.length());
        return new ParsedGuess(guess, visibleStart, visibleStart + guess.length());
    }

    public static Annotation annotationForGuess(String guess, boolean showLangCodes) {
        List<TranslationData.GuessMatch> raw = TranslationData.findGuessMatches(guess);
        if (raw.isEmpty()) return null;
        boolean multiword = isMultiword(guess);
        if (raw.size() == 1 && isExactEnglishGuess(guess, raw.get(0))) {
            return null;
        }
        List<TranslationData.GuessMatch> matches = new ArrayList<TranslationData.GuessMatch>();
        for (TranslationData.GuessMatch match : raw) {
            if (match == null || match.englishTheme == null) continue;
            matches.add(match);
        }
        if (matches.isEmpty()) return null;
        return new Annotation(tooltip(matches, showLangCodes, multiword), guess);
    }

    public static boolean isAnnotationState(GtbGame.State state) {
        return state == GtbGame.State.ROUND_PRE
                || state == GtbGame.State.ROUND_BUILD
                || state == GtbGame.State.ROUND_END;
    }

    public static String formattedGuessSegment(String formatted, int visibleStart, int visibleEnd) {
        return splitFormattedGuess(formatted, visibleStart, visibleEnd).guess;
    }

    public static String copyCommandForGuess(String guess) {
        return "/gtbs copyguess " + encodeCopyPayload(guess);
    }

    public static String suffixText(String result) {
        return " \u00a77[" + result + "\u00a77]";
    }

    public static String encodeCopyPayload(String guess) {
        String url = URLEncoder.encode(guess == null ? "" : guess, StandardCharsets.UTF_8);
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(url.getBytes(StandardCharsets.UTF_8));
    }

    public static String decodeCopyPayload(String payload) {
        if (payload == null || payload.isEmpty()) return "";
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(payload);
            String url = new String(bytes, StandardCharsets.UTF_8);
            return URLDecoder.decode(url, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return "";
        }
    }

    private static boolean isExactEnglishGuess(String guess, TranslationData.GuessMatch match) {
        return match.sourceLangCodes.contains("en")
                && guess != null
                && guess.trim().equalsIgnoreCase(match.englishTheme);
    }

    private static boolean isMultiword(String guess) {
        if (guess == null) return false;
        for (int i = 0; i < guess.length(); i++) {
            if (Character.isWhitespace(guess.charAt(i))) return true;
        }
        return false;
    }

    private static String tooltip(List<TranslationData.GuessMatch> matches,
                                  boolean showLangCodes, boolean multiword) {
        StringBuilder sb = new StringBuilder();
        boolean mergeLangCodes = showLangCodes && multiword && matches.size() > 1
                && sameSourceLangCodes(matches);
        for (int i = 0; i < matches.size(); i++) {
            if (i > 0) sb.append("\u00a77, ");
            TranslationData.GuessMatch match = matches.get(i);
            sb.append("\u00a7f").append(match.englishTheme);
            if (showLangCodes && (!mergeLangCodes || i == matches.size() - 1)) {
                sb.append("\u00a77 (").append(formatLangCodes(match.sourceLangCodes)).append(")");
            }
        }
        return sb.toString();
    }

    private static boolean sameSourceLangCodes(List<TranslationData.GuessMatch> matches) {
        if (matches == null || matches.isEmpty()) return false;
        for (int i = 1; i < matches.size(); i++) {
            if (!matches.get(0).sourceLangCodes.equals(matches.get(i).sourceLangCodes)) return false;
        }
        return true;
    }

    private static String formatLangCodes(Iterable<String> codes) {
        ArrayList<String> list = new ArrayList<String>();
        for (String code : codes) {
            if (code != null && !code.trim().isEmpty()) list.add(code.trim());
        }
        if (list.size() > 3) {
            return list.get(0) + ", " + list.get(1) + ", " + list.get(2) + ", ...";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(list.get(i));
        }
        return sb.toString();
    }

    public static Split splitFormattedGuess(String formatted, int visibleStart, int visibleEnd) {
        if (formatted == null) return new Split("", "", "");
        int start = formatted.length();
        int end = formatted.length();
        int visible = 0;
        for (int i = 0; i < formatted.length();) {
            if (visible == visibleStart && start == formatted.length()) start = i;
            if (visible == visibleEnd) {
                end = i;
                break;
            }
            char c = formatted.charAt(i);
            if (c == '\u00a7' && i + 1 < formatted.length()) {
                i += 2;
                continue;
            }
            visible++;
            i++;
        }
        if (visible == visibleStart && start == formatted.length()) start = formatted.length();
        if (visible == visibleEnd && end == formatted.length()) end = formatted.length();
        if (start > end) start = end;
        String activeAtGuess = activeFormattingAt(formatted, start);
        String activeAfterGuess = activeFormattingAt(formatted, end);
        String suffix = formatted.substring(end);
        return new Split(formatted.substring(0, start),
                prependActiveFormatting(activeAtGuess, formatted.substring(start, end)),
                suffix.isEmpty() ? "" : prependActiveFormatting(activeAfterGuess, suffix));
    }

    private static String prependActiveFormatting(String active, String segment) {
        if (active == null || active.isEmpty() || segment == null || segment.isEmpty()) return segment;
        if (segment.startsWith(active) || startsWithColorOrReset(segment)) return segment;
        return active + segment;
    }

    private static String activeFormattingAt(String formatted, int index) {
        String color = "";
        StringBuilder formats = new StringBuilder();
        int limit = Math.min(Math.max(index, 0), formatted.length());
        for (int i = 0; i + 1 < limit; i++) {
            if (formatted.charAt(i) != '\u00a7') continue;
            char code = Character.toLowerCase(formatted.charAt(i + 1));
            if (isColorCode(code)) {
                color = "\u00a7" + String.valueOf(code);
                formats.setLength(0);
            } else if (isFormatCode(code)) {
                String token = "\u00a7" + String.valueOf(code);
                if (formats.indexOf(token) < 0) formats.append(token);
            } else if (code == 'r') {
                color = "";
                formats.setLength(0);
            }
            i++;
        }
        return color + formats.toString();
    }

    private static boolean startsWithColorOrReset(String text) {
        return text != null
                && text.length() >= 2
                && text.charAt(0) == '\u00a7'
                && (isColorCode(Character.toLowerCase(text.charAt(1)))
                || Character.toLowerCase(text.charAt(1)) == 'r');
    }

    private static boolean isColorCode(char code) {
        return (code >= '0' && code <= '9') || (code >= 'a' && code <= 'f');
    }

    private static boolean isFormatCode(char code) {
        return code >= 'k' && code <= 'o';
    }

    private static String stripControlCodes(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u00a7' && i + 1 < text.length()) {
                i++;
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static String stripChatSuffix(String text) {
        if (text == null) return "";
        String out = text;
        if (out.endsWith("\u00a77 [C]\u00a7r")) {
            out = out.substring(0, out.length() - 8);
        } else if (out.endsWith("\u00a77 [C]")) {
            out = out.substring(0, out.length() - 6);
        }
        if (out.endsWith(" [C]")) {
            out = out.substring(0, out.length() - 4);
        }
        while (out.startsWith("\u00a7r")) {
            out = out.substring(2);
        }
        return out.trim();
    }

    private static String stripEmojis(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isHighSurrogate(c) && i + 1 < text.length()
                    && Character.isLowSurrogate(text.charAt(i + 1))) {
                i++;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static final class ParsedGuess {
        public final String guess;
        public final int visibleGuessStart;
        public final int visibleGuessEnd;

        private ParsedGuess(String guess, int visibleGuessStart, int visibleGuessEnd) {
            this.guess = guess;
            this.visibleGuessStart = visibleGuessStart;
            this.visibleGuessEnd = visibleGuessEnd;
        }
    }

    public static final class Annotation {
        public final String tooltip;
        public final String copyGuess;

        private Annotation(String tooltip, String copyGuess) {
            this.tooltip = tooltip;
            this.copyGuess = copyGuess;
        }
    }

    public static final class Split {
        public final String prefix;
        public final String guess;
        public final String suffix;

        private Split(String prefix, String guess, String suffix) {
            this.prefix = prefix;
            this.guess = guess;
            this.suffix = suffix;
        }
    }
}
