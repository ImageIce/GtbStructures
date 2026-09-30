package com.gtbstructures.chat;

import com.gtbstructures.GtbStructures;
import com.gtbstructures.stats.GameStats;
import com.gtbstructures.stats.LangPatterns;
import com.gtbstructures.translation.TranslationData;

import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StringUtils;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public final class GuessChatAnnotator {
    private GuessChatAnnotator() {
    }

    public static IChatComponent annotate(IChatComponent original) {
        if (original == null) return null;
        if (!GtbStructures.guessPeek) return original;
        if (!GameStats.INSTANCE.isTrackingEnabled()) return original;
        if (!isAnnotationState(GameStats.INSTANCE.getState())) return original;
        if (!TranslationData.isLoaded()) return original;

        ParsedGuess parsed = parseGuess(original.getUnformattedText());
        if (parsed == null) return original;
        Annotation annotation = annotationForGuess(parsed.guess,
                GtbStructures.guessPeekShowLangCodes);
        if (annotation == null) return original;
        return applyAnnotation(original, parsed, annotation,
                GtbStructures.normalizeGuessPeekDisplay(GtbStructures.guessPeekDisplay));
    }

    private static IChatComponent applyAnnotation(IChatComponent original, ParsedGuess parsed,
                                                  Annotation annotation, String display) {
        ComponentParts parts = detachTrailingCopyButton(original);

        if ("suffix".equals(display)) {
            IChatComponent suffixed = parts.base;
            suffixed.appendSibling(new ChatComponentText(suffixText(annotation.tooltip)));
            appendTrailing(suffixed, parts.trailing);
            return suffixed;
        }

        String formatted = parts.base.getFormattedText();
        Split split = splitFormattedGuess(formatted, parsed.visibleGuessStart, parsed.visibleGuessEnd);
        ChatComponentText root = new ChatComponentText(split.prefix);
        ChatComponentText guess = new ChatComponentText(split.guess);
        ChatStyle style = guess.getChatStyle().createShallowCopy();
        style.setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ChatComponentText(annotation.tooltip)));
        style.setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                "/gtbs copyguess " + encode(annotation.copyGuess)));
        guess.setChatStyle(style);
        root.appendSibling(guess);
        if (!split.suffix.isEmpty()) {
            root.appendSibling(new ChatComponentText(split.suffix));
        }
        appendTrailing(root, parts.trailing);
        return root;
    }

    static IChatComponent applyAnnotationForTest(IChatComponent original, ParsedGuess parsed,
                                                 Annotation annotation, String display) {
        return applyAnnotation(original, parsed, annotation, display);
    }

    private static ComponentParts detachTrailingCopyButton(IChatComponent original) {
        IChatComponent base = original.createCopy();
        List<IChatComponent> siblings = base.getSiblings();
        if (siblings.isEmpty()) return new ComponentParts(base, null);
        IChatComponent last = siblings.get(siblings.size() - 1);
        String text = StringUtils.stripControlCodes(last.getUnformattedText());
        ChatStyle style = last.getChatStyle();
        if (text == null || !"[C]".equals(text.trim())
                || style == null || style.getChatClickEvent() == null) {
            return new ComponentParts(base, null);
        }
        // Materialize inherited formatting before appendSibling assigns a new style parent.
        last.setChatStyle(style.createDeepCopy());
        siblings.remove(siblings.size() - 1);
        return new ComponentParts(base, last);
    }

    private static void appendTrailing(IChatComponent root, IChatComponent trailing) {
        if (trailing != null) root.appendSibling(trailing);
    }

    static ParsedGuess parseGuessForTest(String unformatted) {
        return parseGuess(unformatted);
    }

    static Annotation annotationForGuessForTest(String guess, boolean showLangCodes) {
        return annotationForGuess(guess, showLangCodes);
    }

    static boolean isAnnotationState(GameStats.TrackingState state) {
        return state == GameStats.TrackingState.ROUND_PRE
                || state == GameStats.TrackingState.ROUND_BUILD
                || state == GameStats.TrackingState.ROUND_END;
    }

    static String formattedGuessSegmentForTest(String formatted, int visibleStart, int visibleEnd) {
        return splitFormattedGuess(formatted, visibleStart, visibleEnd).guess;
    }

    static String suffixTextForTest(String result) {
        return suffixText(result);
    }

    private static String suffixText(String result) {
        return " \u00a77[" + result + "\u00a77]";
    }

    private static ParsedGuess parseGuess(String unformatted) {
        if (unformatted == null) return null;
        String visible = StringUtils.stripControlCodes(unformatted);
        String clean = GameStats.stripChatSuffix(visible);
        if (clean == null || clean.isEmpty()) return null;

        boolean isPlayerMessage = clean.contains(": ")
                && LangPatterns.startsWithChatTitle(LangPatterns.stripEmojis(clean).trim());
        if (!isPlayerMessage) return null;

        int colonIdx = clean.indexOf(": ");
        if (colonIdx < 0) return null;
        String guess = clean.substring(colonIdx + 2).trim();
        if (guess.isEmpty()) return null;

        int visibleColonIdx = visible.indexOf(": ");
        if (visibleColonIdx < 0) return null;
        int searchFrom = Math.min(visibleColonIdx + 2, visible.length());
        int visibleStart = visible.indexOf(guess, searchFrom);
        if (visibleStart < 0) visibleStart = searchFrom;
        return new ParsedGuess(guess, visibleStart, visibleStart + guess.length());
    }

    private static Annotation annotationForGuess(String guess, boolean showLangCodes) {
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

    private static String tooltip(List<TranslationData.GuessMatch> matches, boolean showLangCodes, boolean multiword) {
        StringBuilder sb = new StringBuilder();
        boolean mergeLangCodes = showLangCodes && multiword && matches.size() > 1 && sameSourceLangCodes(matches);
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

    private static Split splitFormattedGuess(String formatted, int visibleStart, int visibleEnd) {
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

    private static boolean isColorCode(char code) {
        return (code >= '0' && code <= '9') || (code >= 'a' && code <= 'f');
    }

    private static boolean startsWithColorOrReset(String text) {
        return text != null
                && text.length() >= 2
                && text.charAt(0) == '\u00a7'
                && (isColorCode(Character.toLowerCase(text.charAt(1)))
                || Character.toLowerCase(text.charAt(1)) == 'r');
    }

    private static boolean isFormatCode(char code) {
        return code >= 'k' && code <= 'o';
    }

    private static String encode(String guess) {
        try {
            String url = URLEncoder.encode(guess == null ? "" : guess, "UTF-8");
            return Base64.getUrlEncoder().withoutPadding().encodeToString(url.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            return "";
        }
    }

    public static String decodeCopyPayload(String payload) {
        if (payload == null || payload.isEmpty()) return "";
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(payload);
            String url = new String(bytes, "UTF-8");
            return java.net.URLDecoder.decode(url, "UTF-8");
        } catch (Exception ignored) {
            return "";
        }
    }

    static final class ParsedGuess {
        final String guess;
        final int visibleGuessStart;
        final int visibleGuessEnd;

        ParsedGuess(String guess, int visibleGuessStart, int visibleGuessEnd) {
            this.guess = guess;
            this.visibleGuessStart = visibleGuessStart;
            this.visibleGuessEnd = visibleGuessEnd;
        }
    }

    static final class Annotation {
        final String tooltip;
        final String copyGuess;

        Annotation(String tooltip, String copyGuess) {
            this.tooltip = tooltip;
            this.copyGuess = copyGuess;
        }
    }

    private static final class Split {
        final String prefix;
        final String guess;
        final String suffix;

        Split(String prefix, String guess, String suffix) {
            this.prefix = prefix;
            this.guess = guess;
            this.suffix = suffix;
        }
    }

    private static final class ComponentParts {
        final IChatComponent base;
        final IChatComponent trailing;

        ComponentParts(IChatComponent base, IChatComponent trailing) {
            this.base = base;
            this.trailing = trailing;
        }
    }
}
