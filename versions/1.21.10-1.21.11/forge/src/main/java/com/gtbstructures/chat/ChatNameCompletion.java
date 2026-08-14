package com.gtbstructures.chat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Platform-neutral helpers for GTB chat-name tab completion.
 */
public final class ChatNameCompletion {

    private ChatNameCompletion() {
    }

    public static final class Session {
        private Object activeScreen;
        private String activePrefix = "";
        private int activeStart = -1;
        private String activeBase = "";
        private List<String> activeMatches = new ArrayList<String>();
        private int activeIndex;

        public Completion complete(Object screen, String text, int cursor,
                                   Collection<String> names, boolean completionState) {
            if (!completionState) {
                clear();
                return null;
            }
            if (text == null) text = "";
            int safeCursor = Math.max(0, Math.min(cursor, text.length()));
            int start = tokenStart(text, safeCursor);
            String prefix = text.substring(0, start);
            String token = text.substring(start, safeCursor);
            boolean commandHead = isCommandHead(text, safeCursor, start);

            if (canCycle(screen, prefix, start, token, commandHead)) {
                String replacement = activeMatches.get(activeIndex++ % activeMatches.size());
                return new Completion(start, safeCursor, replacement);
            }

            List<String> matches = matchingNames(names, token, commandHead);
            if (matches.isEmpty()) {
                clear();
                return null;
            }

            String replacement;
            String common = commonPrefix(matches);
            if (common.length() > token.length()) {
                replacement = matchTokenCase(token, common);
                activeMatches = matches;
                activeIndex = 0;
            } else {
                replacement = matches.get(0);
                activeMatches = matches;
                activeIndex = matches.size() > 1 ? 1 : 0;
            }
            activeScreen = screen;
            activePrefix = prefix;
            activeStart = start;
            activeBase = token;
            return new Completion(start, safeCursor, replacement);
        }

        public void clear() {
            activeScreen = null;
            activePrefix = "";
            activeStart = -1;
            activeBase = "";
            activeMatches = new ArrayList<String>();
            activeIndex = 0;
        }

        private boolean canCycle(Object screen, String prefix, int start,
                                 String token, boolean commandHead) {
            if (commandHead) return false;
            if (screen == null || screen != activeScreen) return false;
            if (start != activeStart || !prefix.equals(activePrefix)) return false;
            if (activeMatches == null || activeMatches.size() <= 1) return false;
            return canCycleToken(activeMatches, activeBase, token);
        }
    }

    public static final class Completion {
        public final int start;
        public final int cursor;
        public final String replacement;

        private Completion(int start, int cursor, String replacement) {
            this.start = start;
            this.cursor = cursor;
            this.replacement = replacement;
        }

        public String apply(String text) {
            String value = text == null ? "" : text;
            int safeStart = Math.max(0, Math.min(start, value.length()));
            int safeCursor = Math.max(safeStart, Math.min(cursor, value.length()));
            return value.substring(0, safeStart) + replacement + value.substring(safeCursor);
        }

        public int newCursor() {
            return start + (replacement == null ? 0 : replacement.length());
        }
    }

    public static List<String> matchingNames(Collection<String> names, String token, boolean commandHead) {
        List<String> out = new ArrayList<String>();
        if (commandHead || names == null) return out;
        String lower = token == null ? "" : token.toLowerCase();
        for (String name : names) {
            if (!isValidMcUsername(name)) continue;
            if (containsIgnoreCase(out, name)) continue;
            if (lower.isEmpty() || name.toLowerCase().startsWith(lower)) {
                out.add(name);
            }
        }
        return out;
    }

    public static int tokenStart(String text, int cursor) {
        if (text == null) return 0;
        int i = Math.max(0, Math.min(cursor, text.length()));
        while (i > 0 && isUsernameChar(text.charAt(i - 1))) {
            i--;
        }
        return i;
    }

    public static boolean isCommandHead(String text, int cursor, int start) {
        if (text == null || text.isEmpty() || text.charAt(0) != '/') return false;
        int limitedCursor = Math.max(0, Math.min(cursor, text.length()));
        return start <= 1 && text.substring(0, limitedCursor).indexOf(' ') < 0;
    }

    public static String commonPrefix(List<String> values) {
        if (values == null || values.isEmpty()) return "";
        String prefix = values.get(0);
        for (int i = 1; i < values.size(); i++) {
            String next = values.get(i);
            int max = Math.min(prefix.length(), next.length());
            int j = 0;
            while (j < max && Character.toLowerCase(prefix.charAt(j)) == Character.toLowerCase(next.charAt(j))) {
                j++;
            }
            prefix = prefix.substring(0, j);
            if (prefix.isEmpty()) break;
        }
        return prefix;
    }

    public static String matchTokenCase(String token, String replacement) {
        if (token == null || token.isEmpty() || replacement == null || replacement.isEmpty()) return replacement;
        boolean upper = true;
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (Character.isLetter(c) && !Character.isUpperCase(c)) {
                upper = false;
                break;
            }
        }
        return upper ? replacement.toUpperCase() : replacement;
    }

    public static boolean canCycleToken(List<String> activeMatches, String activeBase, String token) {
        if (activeMatches == null || activeMatches.size() <= 1) return false;
        String current = token == null ? "" : token;
        if (current.equalsIgnoreCase(activeBase == null ? "" : activeBase)) return true;
        for (String match : activeMatches) {
            if (match.equalsIgnoreCase(current)) return true;
        }
        return false;
    }

    public static boolean isValidMcUsername(String value) {
        if (value == null) return false;
        int length = value.length();
        if (length < 1 || length > 16) return false;
        for (int i = 0; i < length; i++) {
            if (!isUsernameChar(value.charAt(i))) return false;
        }
        return true;
    }

    private static boolean isUsernameChar(char c) {
        return c == '_' || (c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    private static boolean containsIgnoreCase(List<String> values, String value) {
        for (String existing : values) {
            if (existing.equalsIgnoreCase(value)) return true;
        }
        return false;
    }
}
