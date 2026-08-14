package com.gtbstructures.scoreboard;

import java.util.LinkedHashMap;
import java.util.Map;

final class LeaderboardRowClassifier {
    private static final int CACHE_LIMIT = 256;
    private static final Classification EMPTY = new Classification(null, null, false);
    private static Session currentSession;
    private static boolean testCountersEnabled;
    private static long classifierSessionBuilds;
    private static long classifierCacheHits;
    private static long classifierCacheMisses;
    private static long classificationBuilds;

    static final class Classification {
        final String knownName;
        final Integer score;
        final boolean leaderboardRow;

        Classification(String knownName, Integer score, boolean leaderboardRow) {
            this.knownName = knownName;
            this.score = score;
            this.leaderboardRow = leaderboardRow;
        }
    }

    static final class Session {
        private final GtbGame game;
        private final String[] playerNames;
        private final Map<String, Classification> cache =
                new LinkedHashMap<String, Classification>();

        Session(GtbGame game) {
            this.game = game;
            this.playerNames = snapshotPlayerNames(game);
        }

        synchronized Classification classify(String formattedLeft) {
            if (formattedLeft == null) return EMPTY;
            Classification cached = cache.get(formattedLeft);
            if (cached != null) {
                if (testCountersEnabled) classifierCacheHits++;
                return cached;
            }
            if (testCountersEnabled) classifierCacheMisses++;
            Classification result = classifyUncached(formattedLeft, playerNames);
            if (testCountersEnabled) classificationBuilds++;
            if (cache.size() >= CACHE_LIMIT) {
                cache.remove(cache.keySet().iterator().next());
            }
            cache.put(formattedLeft, result);
            return result;
        }

        boolean matches(GtbGame candidate) {
            if (game != candidate) return false;
            if (candidate == null || candidate.players == null) {
                return playerNames.length == 0;
            }
            if (candidate.players.size() != playerNames.length) return false;
            int index = 0;
            for (GtbPlayer player : candidate.players.values()) {
                String currentName = player == null ? null : player.name;
                String snapshotName = playerNames[index++];
                if (snapshotName == null ? currentName != null : !snapshotName.equals(currentName)) {
                    return false;
                }
            }
            return true;
        }

        synchronized int cacheSize() {
            return cache.size();
        }
    }

    private LeaderboardRowClassifier() {
    }

    static synchronized Session forGame(GtbGame game) {
        if (currentSession == null || !currentSession.matches(game)) {
            currentSession = new Session(game);
            if (testCountersEnabled) classifierSessionBuilds++;
        }
        return currentSession;
    }

    static boolean isLeaderboardRow(String formattedLeft, GtbGame game) {
        return forGame(game).classify(formattedLeft).leaderboardRow;
    }

    static boolean isKnownLeaderboardName(String formattedLeft, GtbGame game) {
        return knownLeaderboardName(formattedLeft, game) != null;
    }

    static String knownLeaderboardName(String formattedLeft, GtbGame game) {
        if (game == null || game.players == null) return null;
        return forGame(game).classify(formattedLeft).knownName;
    }

    static Integer displayedLeaderboardScore(String formattedLeft) {
        if (formattedLeft == null) return null;
        String clean = stripControlCodes(formattedLeft).trim();
        return displayedLeaderboardScore(clean, clean.indexOf(':'));
    }

    private static Classification classifyUncached(String formattedLeft, String[] playerNames) {
        String clean = stripControlCodes(formattedLeft).trim();
        int colon = clean.indexOf(':');
        Integer score = displayedLeaderboardScore(clean, colon);
        String nameColumn = colon > 0 && score != null
                ? clean.substring(0, colon).trim()
                : clean;
        String candidate = BuildBattleTitles.stripLeadingBbTitle(nameColumn);
        String knownName = knownPlayerName(candidate, playerNames);
        boolean leaderboardRow = colon > 0 && score != null
                && (isValidMcUsername(candidate) || knownName != null);
        return new Classification(knownName, score, leaderboardRow);
    }

    private static Integer displayedLeaderboardScore(String clean, int colon) {
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

    private static String[] snapshotPlayerNames(GtbGame game) {
        if (game == null || game.players == null || game.players.isEmpty()) {
            return new String[0];
        }
        String[] names = new String[game.players.size()];
        int index = 0;
        for (GtbPlayer player : game.players.values()) {
            names[index++] = player == null ? null : player.name;
        }
        return names;
    }

    private static String knownPlayerName(String text, String[] playerNames) {
        if (text == null || playerNames == null) return null;
        String clean = text.trim();
        for (String ign : playerNames) {
            if (ign == null || ign.isEmpty()) continue;
            if (!clean.startsWith(ign)) continue;
            if (clean.length() == ign.length() || !isUsernameChar(clean.charAt(ign.length()))) {
                return ign;
            }
        }
        return null;
    }

    private static String stripControlCodes(String text) {
        if (text == null || text.indexOf('\u00a7') < 0) return text == null ? "" : text;
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u00a7' && i + 1 < text.length()) {
                i++;
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    private static boolean isValidMcUsername(String text) {
        if (text == null) return false;
        int length = text.length();
        if (length < 1 || length > 16) return false;
        for (int i = 0; i < length; i++) {
            if (!isUsernameChar(text.charAt(i))) return false;
        }
        return true;
    }

    private static boolean isUsernameChar(char c) {
        return (c >= 'A' && c <= 'Z')
                || (c >= 'a' && c <= 'z')
                || (c >= '0' && c <= '9')
                || c == '_';
    }

    static synchronized void resetClassifierCacheStatsForTests() {
        currentSession = null;
        classifierSessionBuilds = 0L;
        classifierCacheHits = 0L;
        classifierCacheMisses = 0L;
        classificationBuilds = 0L;
        testCountersEnabled = true;
    }

    static long classifierSessionBuildsForTests() {
        return classifierSessionBuilds;
    }

    static long classifierCacheHitsForTests() {
        return classifierCacheHits;
    }

    static long classifierCacheMissesForTests() {
        return classifierCacheMisses;
    }

    static long classificationBuildsForTests() {
        return classificationBuilds;
    }

    static synchronized int classifierCacheSizeForTests() {
        return currentSession == null ? 0 : currentSession.cacheSize();
    }
}
