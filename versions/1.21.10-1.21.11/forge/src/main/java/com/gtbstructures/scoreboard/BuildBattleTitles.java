package com.gtbstructures.scoreboard;

import com.gtbstructures.stats.LangPatterns;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BuildBattleTitles {
    private static final String LEADERBOARD_TITLE_NUM = "(?:10|[1-9])(?!\\d)";
    private static final Pattern LEADERBOARD_TITLE_PATTERN = Pattern.compile(
            "^(?:#\\s*" + LEADERBOARD_TITLE_NUM + "\\s*\\S+(?:\\s+\\S+)*|"
                    + LEADERBOARD_TITLE_NUM + "\\s*[\\.\\u00b0\\u00ba]\\s+\\S+(?:\\s+\\S+)*|"
                    + "\\S+(?:\\s+\\S+)*\\s+(?:\\u010d\\.|nr|#|\\u2116)\\s*"
                    + LEADERBOARD_TITLE_NUM + ")$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LEADERBOARD_TITLE_PREFIX_HASH = Pattern.compile(
            "^#\\s*(" + LEADERBOARD_TITLE_NUM + ")\\s*\\S+(?:\\s+\\S+)*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LEADERBOARD_TITLE_PREFIX_ORDINAL = Pattern.compile(
            "^(" + LEADERBOARD_TITLE_NUM + ")\\s*[\\.\\u00b0\\u00ba]\\s+\\S+(?:\\s+\\S+)*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LEADERBOARD_TITLE_SUFFIX_NUMBER = Pattern.compile(
            "^\\S+(?:\\s+\\S+)*\\s+(?:\\u010d\\.|nr|#|\\u2116)\\s*("
                    + LEADERBOARD_TITLE_NUM + ")$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LEADERBOARD_TITLE_FRAGMENT_HASH = Pattern.compile(
            "(?:^|\\s)#\\s*(" + LEADERBOARD_TITLE_NUM + ")(?:\\s|$)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LEADERBOARD_TITLE_FRAGMENT_ORDINAL = Pattern.compile(
            "(?:^|\\s)(" + LEADERBOARD_TITLE_NUM + ")\\s*[\\.\\u00b0\\u00ba](?:\\s|$)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LEADERBOARD_TITLE_FRAGMENT_SUFFIX_NUMBER = Pattern.compile(
            "(?:^|\\s)(?:\\u010d\\.|nr|#|\\u2116)\\s*(" + LEADERBOARD_TITLE_NUM + ")(?:\\s|$)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NON_WHITESPACE_TOKEN_PATTERN = Pattern.compile("\\S+");

    private static final String[] LANGUAGE_CODES = new String[] {
            "en", "pirate", "zh_cn", "zh_tw", "cs", "da", "nl", "fi",
            "fr", "de", "hu", "it", "ja", "ko", "no", "pl", "pt_br",
            "pt", "ro", "ru", "es", "sv", "tr", "uk"
    };

    private static final String[] EN_BB_TITLES = new String[] {
            "Rookie", "Untrained", "Amateur", "Prospect", "Apprentice",
            "Experienced", "Seasoned", "Trained", "Skilled", "Talented",
            "Professional", "Artisan", "Expert", "Master", "Legend",
            "Grandmaster", "Celestial", "Divine", "Ascended"
    };
    public static final int ACTIVE_BB_TITLE_COUNT = EN_BB_TITLES.length;

    private static final String[] BB_TITLE_COLORS = new String[] {
            "\u00a7f", "\u00a77", "\u00a78", "\u00a7a", "\u00a72",
            "\u00a7b", "\u00a73", "\u00a79", "\u00a71", "\u00a75",
            "\u00a7d", "\u00a7c", "\u00a74", "\u00a76", "\u00a7a\u00a7l",
            "\u00a7b\u00a7l", "\u00a7d\u00a7l", "\u00a7c\u00a7l", "\u00a76\u00a7l"
    };
    private static final String[] BUILDER_WORDS = new String[] {
            "Builder", "Builder", "\u5efa\u7b51\u5e08", "\u5efa\u7bc9\u5e2b",
            "Stavitel", "Bygger", "Bouwer", "Rakentaja", "Constructeur",
            "Erbauer", "\u00c9p\u00edt\u00e9sz", "Costruttore", "\u4f5c\u8005",
            "\uac74\ucd95\uac00", "Bygger", "Budowniczy", "Construtor",
            "Construtor", "Constructor", "\u0421\u0442\u0440\u043e\u0438\u0442\u0435\u043b\u044c",
            "Constructor", "Byggare", "Mimar", "\u0411\u0443\u0434\u0456\u0432\u0435\u043b\u044c\u043d\u0438\u043a"
    };
    private static int titleArrayBuildCount;
    private static final Map<String, String[]> PREFERRED_CHAT_TITLES =
            createPreferredChatTitles();
    private static final Set<String> KNOWN_CHAT_TITLES = createKnownChatTitles();
    private static final String[] LEADING_BB_TITLES = createLeadingBbTitles();
    private static final int STRIP_CACHE_LIMIT = 256;
    private static final Map<String, String> STRIPPED_BB_TITLE_CACHE =
            new LinkedHashMap<String, String>();
    private static boolean testCountersEnabled;
    private static long stripCacheHits;
    private static long stripCacheMisses;
    private static long titlePrefixChecks;

    private BuildBattleTitles() {
    }

    public static boolean startsWithChatTitle(String cleanText) {
        if (cleanText == null) return false;
        cleanText = cleanText.trim();
        cleanText = stripLeadingRankPrefixes(cleanText);

        int start = 0;
        while (start < cleanText.length()) {
            char c = cleanText.charAt(start);
            if (Character.isLetterOrDigit(c) || c == '#') break;
            start++;
        }
        if (start > 0) cleanText = stripLeadingRankPrefixes(cleanText.substring(start).trim());

        int colonIdx = cleanText.indexOf(": ");
        if (colonIdx < 0) return false;

        String beforeColon = cleanText.substring(0, colonIdx).trim();
        String[] tokens = beforeColon.split("\\s+");
        if (tokens.length < 2) return false;

        for (int i = 0; i < tokens.length - 1; i++) {
            String token = stripLeadingRankPrefixes(tokens[i]);
            if (isKnownChatTitle(token)) return true;
        }

        if (startsWithLeaderboardChatTitle(beforeColon)) return true;

        int lastSpace = beforeColon.lastIndexOf(' ');
        if (lastSpace > 0) {
            String leaderboardPart = beforeColon.substring(0, lastSpace).trim();
            leaderboardPart = stripLeadingRankPrefixes(leaderboardPart);
            return LEADERBOARD_TITLE_PATTERN.matcher(leaderboardPart).matches();
        }
        return false;
    }

    static String stripLeadingBbTitle(String text) {
        if (text == null) return null;
        synchronized (STRIPPED_BB_TITLE_CACHE) {
            String cached = STRIPPED_BB_TITLE_CACHE.get(text);
            if (cached != null) {
                if (testCountersEnabled) stripCacheHits++;
                return cached;
            }
            if (testCountersEnabled) stripCacheMisses++;
            String stripped = stripLeadingBbTitleUncached(text);
            if (STRIPPED_BB_TITLE_CACHE.size() >= STRIP_CACHE_LIMIT) {
                STRIPPED_BB_TITLE_CACHE.remove(
                        STRIPPED_BB_TITLE_CACHE.keySet().iterator().next());
            }
            STRIPPED_BB_TITLE_CACHE.put(text, stripped);
            return stripped;
        }
    }

    private static String stripLeadingBbTitleUncached(String text) {
        String clean = normalizeLeadingBbTitleCandidate(text);

        String bestTitle = leadingBbTitle(clean, true);
        if (bestTitle == null) return clean;
        return clean.substring(bestTitle.length()).trim();
    }

    public static boolean hasLeadingBbTitle(String text) {
        if (text == null) return false;
        return leadingBbTitle(normalizeLeadingBbTitleCandidate(text), false) != null;
    }

    public static Integer bbRankIndexForTitle(String title, String langCode) {
        if (title == null) return null;
        String trimmed = stripLeadingRankPrefixes(title.trim());
        if (trimmed.isEmpty()) return null;
        String lower = trimmed.toLowerCase(Locale.ROOT);

        Integer preferred = matchPreferredChatTitleIndex(lower, langCode);
        if (preferred != null) return preferred;

        Set<Integer> exact = new HashSet<Integer>();
        Set<Integer> prefix = new HashSet<Integer>();
        for (String lang : LANGUAGE_CODES) {
            collectMatchingTitleIndexes(lower, lang, exact, prefix);
        }
        collectMatchingTitleIndexes(lower, "en", exact, prefix);
        if (exact.size() == 1) return exact.iterator().next();
        if (!exact.isEmpty()) return null;
        return prefix.size() == 1 ? prefix.iterator().next() : null;
    }

    public static String bbTitleForRankIndex(int rankIndex, String langCode) {
        String[] preferred = preferredChatTitles(langCode);
        if (preferred != null && rankIndex >= 0 && rankIndex < ACTIVE_BB_TITLE_COUNT) {
            return preferred[rankIndex];
        }
        if (rankIndex >= ACTIVE_BB_TITLE_COUNT) {
            int leaderboardRank = rankIndex - ACTIVE_BB_TITLE_COUNT + 1;
            if (leaderboardRank < 1 || leaderboardRank > 10) return null;
            return leaderboardBuilderTitle(leaderboardRank, langCode);
        }
        String[] english = preferredChatTitles("en");
        return rankIndex >= 0 && rankIndex < ACTIVE_BB_TITLE_COUNT ? english[rankIndex] : null;
    }

    public static Integer bbLeaderboardRankIndex(String title) {
        if (title == null) return null;
        String clean = stripLeadingRankPrefixes(title.trim());
        if (clean.isEmpty()) return null;
        Matcher m = LEADERBOARD_TITLE_PREFIX_HASH.matcher(clean);
        if (!m.matches()) m = LEADERBOARD_TITLE_PREFIX_ORDINAL.matcher(clean);
        if (!m.matches()) m = LEADERBOARD_TITLE_SUFFIX_NUMBER.matcher(clean);
        if (!m.matches()) return null;
        return leaderboardRankIndex(m.group(1));
    }

    public static Integer bbLeaderboardRankIndexFromFragment(String title) {
        if (title == null) return null;
        String clean = stripLeadingRankPrefixes(title.trim());
        if (clean.isEmpty()) return null;
        Integer exact = bbLeaderboardRankIndex(clean);
        if (exact != null) return exact;
        Matcher m = LEADERBOARD_TITLE_FRAGMENT_HASH.matcher(clean);
        if (m.find()) return leaderboardRankIndex(m.group(1));
        m = LEADERBOARD_TITLE_FRAGMENT_ORDINAL.matcher(clean);
        if (m.find()) return leaderboardRankIndex(m.group(1));
        m = LEADERBOARD_TITLE_FRAGMENT_SUFFIX_NUMBER.matcher(clean);
        if (!m.find()) return null;
        return leaderboardRankIndex(m.group(1));
    }

    public static Integer bbLeaderboardRankIndexForPosition(int rank) {
        if (rank < 1 || rank > 10) return null;
        return Integer.valueOf(ACTIVE_BB_TITLE_COUNT + rank - 1);
    }

    public static Integer bbLeaderboardPositionForRankIndex(int rankIndex) {
        if (rankIndex < ACTIVE_BB_TITLE_COUNT) return null;
        int rank = rankIndex - ACTIVE_BB_TITLE_COUNT + 1;
        return rank >= 1 && rank <= 10 ? Integer.valueOf(rank) : null;
    }

    public static String bbColorForRankIndex(int rankIndex) {
        if (rankIndex >= ACTIVE_BB_TITLE_COUNT && rankIndex < ACTIVE_BB_TITLE_COUNT + 10) {
            return "\u00a7e\u00a7l";
        }
        return rankIndex >= 0 && rankIndex < ACTIVE_BB_TITLE_COUNT
                ? BB_TITLE_COLORS[rankIndex] : "\u00a7f";
    }

    private static String normalizeLeadingBbTitleCandidate(String text) {
        String clean = stripLeadingRankPrefixes(text.trim());
        int start = 0;
        while (start < clean.length()) {
            char c = clean.charAt(start);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '[' || c == '#') break;
            start++;
        }
        if (start > 0) {
            clean = stripLeadingRankPrefixes(clean.substring(start).trim());
        }
        return clean;
    }

    private static boolean startsWithLeaderboardChatTitle(String beforeColon) {
        if (beforeColon == null) return false;
        String clean = stripLeadingRankPrefixes(beforeColon.trim());
        if (clean.isEmpty()) return false;

        Matcher tokenMatcher = NON_WHITESPACE_TOKEN_PATTERN.matcher(clean);
        List<Integer> tokenEnds = new ArrayList<Integer>();
        while (tokenMatcher.find()) {
            tokenEnds.add(Integer.valueOf(tokenMatcher.end()));
        }
        if (tokenEnds.size() < 2) return false;

        for (int i = 0; i < tokenEnds.size() - 1; i++) {
            int end = tokenEnds.get(i).intValue();
            String candidate = clean.substring(0, end).trim();
            if (!LEADERBOARD_TITLE_PATTERN.matcher(candidate).matches()) continue;
            String remainder = clean.substring(end).trim();
            if (hasIgnTokenAfterTitle(remainder)) return true;
        }
        return false;
    }

    private static boolean hasIgnTokenAfterTitle(String remainder) {
        String clean = stripLeadingRankPrefixes(remainder == null ? "" : remainder.trim());
        if (clean.isEmpty()) return false;
        String[] tokens = clean.split("\\s+");
        for (int i = tokens.length - 1; i >= 0; i--) {
            if (isMinecraftUsernameToken(stripLeadingRankPrefixes(tokens[i]))) return true;
        }
        return false;
    }

    private static boolean isMinecraftUsernameToken(String token) {
        if (token == null || token.length() < 1 || token.length() > 16) return false;
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9') || c == '_') continue;
            return false;
        }
        return true;
    }

    private static boolean isKnownChatTitle(String token) {
        return token != null && !token.isEmpty() && KNOWN_CHAT_TITLES.contains(token);
    }

    private static String leadingBbTitle(String clean, boolean requireFollowingToken) {
        for (String title : LEADING_BB_TITLES) {
            if (isTitlePrefix(clean, title, requireFollowingToken)) return title;
        }
        return null;
    }

    private static boolean isTitlePrefix(String text, String title, boolean requireFollowingToken) {
        if (testCountersEnabled) titlePrefixChecks++;
        if (text == null || title == null || title.isEmpty()) return false;
        if (text.length() < title.length()) return false;
        if (!text.regionMatches(true, 0, title, 0, title.length())) return false;
        if (text.length() == title.length()) return !requireFollowingToken;
        return Character.isWhitespace(text.charAt(title.length()));
    }

    private static String stripLeadingRankPrefixes(String text) {
        while (text.startsWith("[")) {
            int closeIdx = text.indexOf(']');
            if (closeIdx < 0) break;
            text = text.substring(closeIdx + 1).trim();
        }
        return text;
    }

    private static Integer leaderboardRankIndex(String rawRank) {
        int rank;
        try {
            rank = Integer.parseInt(rawRank);
        } catch (NumberFormatException ignored) {
            return null;
        }
        return bbLeaderboardRankIndexForPosition(rank);
    }

    private static String leaderboardBuilderTitle(int rank, String langCode) {
        String builder = builderWord(langCode);
        String code = langCode == null ? "" : langCode.toLowerCase(Locale.ROOT);
        if ("cs".equals(code)) return builder + " \u010d. " + rank;
        if ("no".equals(code) || "da".equals(code) || "sv".equals(code)
                || "nl".equals(code)) return builder + " nr " + rank;
        if ("ru".equals(code) || "uk".equals(code)) return builder + " #" + rank;
        if ("fr".equals(code)) return rank + "\u00b0 " + builder;
        if ("es".equals(code) || "pt".equals(code) || "pt_br".equals(code)
                || "it".equals(code)) return rank + "\u00ba " + builder;
        return "#" + rank + " " + builder;
    }

    private static String builderWord(String langCode) {
        if (langCode != null) {
            for (int i = 0; i < LANGUAGE_CODES.length && i < BUILDER_WORDS.length; i++) {
                if (LANGUAGE_CODES[i].equalsIgnoreCase(langCode)) return BUILDER_WORDS[i];
            }
        }
        return "Builder";
    }

    private static Integer matchPreferredChatTitleIndex(String lowerTitle, String langCode) {
        String[] titles = preferredChatTitles(langCode);
        if (titles == null) return null;
        Integer prefixMatch = null;
        for (int i = 0; i < titles.length && i < ACTIVE_BB_TITLE_COUNT; i++) {
            String title = titles[i];
            if (title == null) continue;
            String lowerCandidate = title.toLowerCase(Locale.ROOT);
            if (lowerCandidate.equals(lowerTitle)) return Integer.valueOf(i);
            if (lowerCandidate.startsWith(lowerTitle)) {
                if (prefixMatch != null) return null;
                prefixMatch = Integer.valueOf(i);
            }
        }
        return prefixMatch;
    }

    private static void collectMatchingTitleIndexes(String lowerTitle, String langCode,
                                                    Set<Integer> exact, Set<Integer> prefix) {
        String[] titles = preferredChatTitles(langCode);
        if (titles == null) return;
        for (int i = 0; i < titles.length && i < ACTIVE_BB_TITLE_COUNT; i++) {
            String title = titles[i];
            if (title == null) continue;
            String lowerCandidate = title.toLowerCase(Locale.ROOT);
            if (lowerCandidate.equals(lowerTitle)) {
                exact.add(Integer.valueOf(i));
            } else if (lowerCandidate.startsWith(lowerTitle)) {
                prefix.add(Integer.valueOf(i));
            }
        }
    }

    private static String[] preferredChatTitles(String langCode) {
        if (langCode == null) return null;
        return PREFERRED_CHAT_TITLES.get(langCode.toLowerCase(Locale.ROOT));
    }

    private static String[] uncachedPreferredChatTitles(String langCode) {
        if (langCode == null) return null;
        String code = langCode.toLowerCase(Locale.ROOT);
        if ("en".equals(code) || "pirate".equals(code)) return EN_BB_TITLES;
        boolean supported = false;
        for (String language : LANGUAGE_CODES) {
            if (language.equals(code)) {
                supported = true;
                break;
            }
        }
        if (!supported) return null;

        String[] titles = new String[ACTIVE_BB_TITLE_COUNT];
        for (int rankIndex = 0; rankIndex < titles.length; rankIndex++) {
            titles[rankIndex] = LangPatterns.bbTitleForRankIndex(rankIndex, code);
        }
        titleArrayBuildCount++;
        return titles;
    }

    private static Map<String, String[]> createPreferredChatTitles() {
        Map<String, String[]> out = new LinkedHashMap<String, String[]>();
        for (String code : LANGUAGE_CODES) {
            String[] titles = uncachedPreferredChatTitles(code);
            if (titles != null) out.put(code, titles);
        }
        return Collections.unmodifiableMap(out);
    }

    private static Set<String> createKnownChatTitles() {
        Set<String> out = new LinkedHashSet<String>();
        for (String[] titles : PREFERRED_CHAT_TITLES.values()) {
            Collections.addAll(out, titles);
        }
        return Collections.unmodifiableSet(out);
    }

    private static String[] createLeadingBbTitles() {
        List<String> titles = new ArrayList<String>(KNOWN_CHAT_TITLES);
        Collections.sort(titles, new Comparator<String>() {
            @Override public int compare(String left, String right) {
                return Integer.compare(right.length(), left.length());
            }
        });
        return titles.toArray(new String[titles.size()]);
    }

    static void resetTitleCacheStatsForTests() {
        synchronized (STRIPPED_BB_TITLE_CACHE) {
            STRIPPED_BB_TITLE_CACHE.clear();
            stripCacheHits = 0L;
            stripCacheMisses = 0L;
            titlePrefixChecks = 0L;
            testCountersEnabled = true;
        }
    }

    static int titleArrayBuildCountForTests() {
        return titleArrayBuildCount;
    }

    static long stripCacheHitsForTests() {
        return stripCacheHits;
    }

    static long stripCacheMissesForTests() {
        return stripCacheMisses;
    }

    static long titlePrefixChecksForTests() {
        return titlePrefixChecks;
    }

    static int stripCacheSizeForTests() {
        synchronized (STRIPPED_BB_TITLE_CACHE) {
            return STRIPPED_BB_TITLE_CACHE.size();
        }
    }
}
