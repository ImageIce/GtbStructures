package com.gtbstructures.scoreboard;

import com.gtbstructures.stats.LangPatterns;

import java.util.LinkedHashMap;
import java.util.Map;

final class LeaderboardRowClassifier {
    private static final int PARSED_ROW_CACHE_LIMIT = 128;
    private static final ParsedRow INVALID_ROW = new ParsedRow("", null);
    private static final Map<String, ParsedRow> PARSED_ROWS =
            new LinkedHashMap<String, ParsedRow>(PARSED_ROW_CACHE_LIMIT + 1, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, ParsedRow> eldest) {
                    return size() > PARSED_ROW_CACHE_LIMIT;
                }
            };
    private static int cacheHitCount;
    private static int cacheMissCount;
    private static int parsedRowAllocationCount;

    private LeaderboardRowClassifier() {}

    static boolean isLeaderboardRow(String formattedLeft, GtbGame g) {
        // Pure structural test: a Hypixel leaderboard row is shaped exactly
        // "<MC-username>: <int>". Some Hypixel rows append emblems after the
        // name, so also accept "<known player> <emblem>: <int>".
        ParsedRow parsed = parsedRow(formattedLeft);
        if (parsed.score == null) return false;
        return isValidMcUsername(parsed.normalizedName)
                || isKnownDecoratedPlayerName(parsed.normalizedName, g);
    }

    static boolean isKnownLeaderboardName(String formattedLeft, GtbGame g) {
        return knownLeaderboardName(formattedLeft, g) != null;
    }

    static String knownLeaderboardName(String formattedLeft, GtbGame g) {
        if (formattedLeft == null || g == null || g.players == null) return null;
        String clean = parsedRow(formattedLeft).normalizedName;
        for (GtbPlayer player : g.players.values()) {
            if (player == null || player.name == null || player.name.isEmpty()) continue;
            String ign = player.name;
            if (!clean.startsWith(ign)) continue;
            if (clean.length() == ign.length() || !isUsernameChar(clean.charAt(ign.length()))) {
                return ign;
            }
        }
        return null;
    }

    static Integer displayedLeaderboardScore(String formattedLeft) {
        return parsedRow(formattedLeft).score;
    }

    private static ParsedRow parsedRow(String formattedLeft) {
        if (formattedLeft == null) return INVALID_ROW;
        synchronized (PARSED_ROWS) {
            ParsedRow cached = PARSED_ROWS.get(formattedLeft);
            if (cached != null) {
                cacheHitCount++;
                return cached;
            }
            cacheMissCount++;
            ParsedRow parsed = parseUncached(formattedLeft);
            parsedRowAllocationCount++;
            PARSED_ROWS.put(formattedLeft, parsed);
            return parsed;
        }
    }

    private static ParsedRow parseUncached(String formattedLeft) {
        String clean = stripControlCodes(formattedLeft).trim();
        int colon = clean.indexOf(':');
        Integer score = parseScore(clean, colon);
        String nameText = score != null ? clean.substring(0, colon).trim() : clean;
        return new ParsedRow(LangPatterns.stripLeadingBbTitle(nameText), score);
    }

    private static Integer parseScore(String clean, int colon) {
        if (colon <= 0) return null;
        String maybeNum = clean.substring(colon + 1).trim();
        if (maybeNum.isEmpty()) return null;
        int value = 0;
        for (int i = 0; i < maybeNum.length(); i++) {
            char c = maybeNum.charAt(i);
            if (c < '0' || c > '9') return null;
            int digit = c - '0';
            if (value > (Integer.MAX_VALUE - digit) / 10) return null;
            value = value * 10 + digit;
        }
        return Integer.valueOf(value);
    }

    static void resetCacheForTest() {
        synchronized (PARSED_ROWS) {
            PARSED_ROWS.clear();
            cacheHitCount = 0;
            cacheMissCount = 0;
            parsedRowAllocationCount = 0;
        }
    }

    static int cacheHitCountForTest() {
        synchronized (PARSED_ROWS) {
            return cacheHitCount;
        }
    }

    static int cacheMissCountForTest() {
        synchronized (PARSED_ROWS) {
            return cacheMissCount;
        }
    }

    static int parsedRowAllocationCountForTest() {
        synchronized (PARSED_ROWS) {
            return parsedRowAllocationCount;
        }
    }

    private static final class ParsedRow {
        final String normalizedName;
        final Integer score;

        ParsedRow(String normalizedName, Integer score) {
            this.normalizedName = normalizedName;
            this.score = score;
        }
    }

    private static String stripControlCodes(String s) {
        if (s == null || s.indexOf('\u00a7') < 0) return s == null ? "" : s;
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\u00a7' && i + 1 < s.length()) {
                i++;
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    private static boolean isKnownDecoratedPlayerName(String text, GtbGame g) {
        if (text == null || g == null || g.players == null) return false;
        String clean = text.trim();
        for (GtbPlayer player : g.players.values()) {
            if (player == null || player.name == null || player.name.isEmpty()) continue;
            String ign = player.name;
            if (!clean.startsWith(ign)) continue;
            if (clean.length() == ign.length()) return true;
            if (!isUsernameChar(clean.charAt(ign.length()))) return true;
        }
        return false;
    }

    private static boolean isValidMcUsername(String s) {
        if (s == null) return false;
        int n = s.length();
        if (n < 1 || n > 16) return false;
        for (int i = 0; i < n; i++) {
            if (!isUsernameChar(s.charAt(i))) return false;
        }
        return true;
    }

    private static boolean isUsernameChar(char c) {
        return (c >= 'A' && c <= 'Z')
                || (c >= 'a' && c <= 'z')
                || (c >= '0' && c <= '9')
                || c == '_';
    }
}
