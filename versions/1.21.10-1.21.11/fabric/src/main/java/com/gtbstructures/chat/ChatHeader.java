package com.gtbstructures.chat;

import java.util.ArrayList;
import java.util.List;

/** Formatting-aware prefix shared by all GTBS chat notices. */
public final class ChatHeader {
    public static final String DEFAULT_RAW = "&6[&eGtbStructures&6]";
    public static final int MAX_VISIBLE_LENGTH = 32;

    private static String raw = DEFAULT_RAW;

    private ChatHeader() {
    }

    public static synchronized boolean set(String candidate) {
        String prepared = prepare(candidate);
        if (prepared == null) return false;
        raw = prepared;
        return true;
    }

    public static synchronized void reset() {
        raw = DEFAULT_RAW;
    }

    public static synchronized String raw() {
        return raw;
    }

    public static synchronized String rendered() {
        return render(raw);
    }

    public static synchronized String prefix() {
        return render(raw) + "\u00a7r\u00a77 ";
    }

    public static String prepare(String candidate) {
        String normalized = candidate == null ? "" : candidate.replace('\r', ' ').replace('\n', ' ');
        List<Token> tokens = tokenize(normalized);
        int firstVisible = -1;
        int lastVisible = -1;
        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            if (!token.format && !token.whitespace) {
                if (firstVisible < 0) firstVisible = i;
                lastVisible = i;
            }
        }
        if (firstVisible < 0) return DEFAULT_RAW;

        StringBuilder out = new StringBuilder(normalized.length());
        int visibleLength = 0;
        for (int i = 0; i <= lastVisible; i++) {
            Token token = tokens.get(i);
            if (token.format) {
                out.append(token.text);
            } else if (i >= firstVisible) {
                out.append(token.text);
                visibleLength++;
            }
        }
        return visibleLength <= MAX_VISIBLE_LENGTH ? out.toString() : null;
    }

    public static int visibleLength(String candidate) {
        String normalized = candidate == null ? "" : candidate.replace('\r', ' ').replace('\n', ' ');
        List<Token> tokens = tokenize(normalized);
        int firstVisible = -1;
        int lastVisible = -1;
        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            if (!token.format && !token.whitespace) {
                if (firstVisible < 0) firstVisible = i;
                lastVisible = i;
            }
        }
        if (firstVisible < 0) return 0;
        int length = 0;
        for (int i = firstVisible; i <= lastVisible; i++) {
            if (!tokens.get(i).format) length++;
        }
        return length;
    }

    public static String render(String candidate) {
        String prepared = prepare(candidate);
        if (prepared == null) prepared = DEFAULT_RAW;
        StringBuilder out = new StringBuilder(prepared.length());
        for (int i = 0; i < prepared.length();) {
            if (isFormatCodeAt(prepared, i)) {
                out.append('\u00a7').append(Character.toLowerCase(prepared.charAt(i + 1)));
                i += 2;
                continue;
            }
            int codePoint = prepared.codePointAt(i);
            out.appendCodePoint(codePoint);
            i += Character.charCount(codePoint);
        }
        return out.toString();
    }

    private static List<Token> tokenize(String value) {
        List<Token> tokens = new ArrayList<Token>();
        for (int i = 0; i < value.length();) {
            if (isFormatCodeAt(value, i)) {
                tokens.add(new Token("&" + Character.toLowerCase(value.charAt(i + 1)), true, false));
                i += 2;
                continue;
            }
            int codePoint = value.codePointAt(i);
            tokens.add(new Token(new String(Character.toChars(codePoint)), false,
                    Character.isWhitespace(codePoint)));
            i += Character.charCount(codePoint);
        }
        return tokens;
    }

    private static boolean isFormatCodeAt(String value, int index) {
        if (index < 0 || index + 1 >= value.length()) return false;
        char marker = value.charAt(index);
        if (marker != '&' && marker != '\u00a7') return false;
        char code = Character.toLowerCase(value.charAt(index + 1));
        return (code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')
                || (code >= 'k' && code <= 'o') || code == 'r';
    }

    private static final class Token {
        final String text;
        final boolean format;
        final boolean whitespace;

        Token(String text, boolean format, boolean whitespace) {
            this.text = text;
            this.format = format;
            this.whitespace = whitespace;
        }
    }
}
