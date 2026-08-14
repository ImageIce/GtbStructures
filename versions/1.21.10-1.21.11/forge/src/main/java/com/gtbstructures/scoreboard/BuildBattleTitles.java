package com.gtbstructures.scoreboard;

import java.util.ArrayList;
import java.util.Arrays;
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
    private static final int[] LEGACY_TO_CURRENT = new int[] {
            0, 1, 2, 4, 5,
            6, 7, 8, 9, 10,
            11, 3, 12, 13, 14,
            15, 16, 17, 18
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
        if ("zh_cn".equals(code)) return titleArray(
                "\u521d\u6765\u4e4d\u5230", "\u672a\u7ecf\u96d5\u7422", "\u521d\u7aa5\u95e8\u5f84", "\u5b66\u6709\u6240\u6210",
                "\u9a7e\u8f7b\u5c31\u719f", "\u5386\u7ec3\u8001\u6210", "\u6280\u827a\u7cbe\u6e5b", "\u7089\u706b\u7eaf\u9752",
                "\u6280\u60ca\u56db\u5ea7", "\u5de7\u593a\u5929\u5de5", null, null, "\u95fb\u540d\u4e8e\u4e16",
                "\u5efa\u7b51\u5927\u5e08", "\u4f20\u8bf4\u661f\u8000", "\u6700\u5f3a\u738b\u8005", null,
                "\u8d85\u51e1\u5165\u5723", "\u767b\u5cf0\u9020\u6781");
        if ("zh_tw".equals(code)) return titleArray(
                "\u521d\u4f86\u4e4d\u5230", "\u6280\u85dd\u751f\u758f", "\u521d\u7aa5\u9580\u5f91", "\u5b78\u5f92",
                "\u99d5\u8f15\u5c31\u719f", "\u8b58\u9014\u8001\u99ac", "\u6280\u85dd\u7cbe\u6e5b", "\u7210\u706b\u7d14\u9752",
                "\u6280\u9a5a\u56db\u5ea7", "\u5de7\u596a\u5929\u5de5", null, null, "\u805e\u540d\u65bc\u4e16",
                "\u5927\u5e2b", "\u50b3\u8aaa", "\u4e00\u4ee3\u5b97\u5e2b", null, "\u51fa\u795e\u5165\u5316",
                "\u81f3\u9ad8\u7121\u4e0a");
        if ("cs".equals(code)) return titleArray("Nov\u00e1\u010dek", "Nezku\u0161en\u00fd", "Amat\u00e9r", "U\u010dedn\u00edk", "Zku\u0161en\u00fd",
                "Ost\u0159\u00edlen\u00fd", "Zb\u011bhl\u00fd", "\u0160ikovn\u00fd", "Talentovan\u00fd", "Profesion\u00e1l",
                null, null, "Ekspert", "Mistr", "Legenda", "Velmistr", null, "Bo\u017esk\u00e1", "Nanebevzet\u00ed");
        if ("da".equals(code)) return titleArray("Nybegynder", "Utr\u00e6net", "Amat\u00f8r", "L\u00e6rling", "\u00d8vet",
                null, "Kvalificeret", "Kyndig", "Talentfuld", "Professionel", null, null, null,
                "Mester", "Legende", "Stormester", null, "Guddommelig", "Alm\u00e6gtig");
        if ("nl".equals(code)) return titleArray("Beginner", "Ongetraind", null, "Leerling", "Ervaren",
                "Doorwinterd", "Opgeleid", null, "Getalenteerd", "Vakkundige", null, null, null,
                "Meester", null, "Grootmeester", null, "Goddelijk", "Gerezen");
        if ("fi".equals(code)) return titleArray("Aloittelija", "Kokematon", "Amat\u00f6\u00f6ri", "Oppipoika",
                "Harjoitellut", "Kokenut", "Treenattu", "Taitava", "Lahjakas", "Pro", null, null,
                "Ekspertti", "Mestari", null, "Suurmestari", null, "Jumalallinen", "Noussut");
        if ("fr".equals(code)) return titleArray("D\u00e9butant", "Novice", "Dilettante", "Apprenti", "Exp\u00e9riment\u00e9",
                "Saisonnier", "Entra\u00een\u00e9", "Comp\u00e9tent", "Talentueux", "Professionnel", null, null,
                "Experte", "Maitre", "L\u00e9gende", "Grand maitre", null, null, "Ascendu");
        if ("de".equals(code)) return titleArray("Anf\u00e4nger", "Untrainiert", null, "Lehrling", "Erfahren",
                null, "Trainiert", "Bewandt", "Talentiert", "Professionell", null, null, "Experte",
                "Meister", null, "Gro\u00dfmeister", null, "G\u00f6ttlich", "Aufgestiegen");
        if ("hu".equals(code)) return titleArray("\u00dajonc", "K\u00e9pzetlen", "Amat\u0151r", "Tanonc", "Tapasztalt",
                "Szezon\u00e1lis", "Edzett", "\u00dcgyes", "Tehets\u00e9ges", "Profi", null, null,
                "Szak\u00e9rt\u0151", null, null, "Nagymester", null, "Isteni", "Felemelkedett");
        if ("it".equals(code)) return titleArray("Novellino", "Non Allenato", null, "Apprendista", "Esperto",
                null, "Allenato", "Abile", "Talentuoso", "Professionista", null, null, null,
                "Maestro", "Leggenda", "Gran Maestro", null, "Divino", "Asceso");
        if ("ja".equals(code)) return titleArray("\u65b0\u4eba", "\u4e00\u822c\u4eba", "\u30a2\u30de\u30c1\u30e5\u30a2",
                "\u898b\u7fd2", "\u7d4c\u9a13\u8005", "\u719f\u9054\u8005", "\u30d9\u30c6\u30e9\u30f3",
                "\u719f\u7df4\u8005", "\u4eba\u6750", "\u8077\u696d\u4eba", null, null, "\u5c02\u9580\u5bb6",
                "\u30de\u30b9\u30bf\u30fc", "\u30ec\u30b8\u30a7\u30f3\u30c9", "\u30b0\u30e9\u30f3\u30c9\u30de\u30b9\u30bf\u30fc",
                null, null, "\u6607\u5929");
        if ("ko".equals(code)) return titleArray("\ud480\ub0b4\uae30", "\uc77c\ubc18\uc778", "\uc544\ub9c8\ucd94\uc5b4",
                "\uacac\uc2b5", "\uacbd\ud5d8\uc790", "\uc219\ub2ec\uc790", "\ubca0\ud14c\ub791",
                "\uc219\ub828\uc790", "\uc778\uc7ac", "\uc9c1\uc5c5\uc778", null, null, "\uc804\ubb38\uac00",
                "\ub9c8\uc2a4\ud130", "\ub808\uc804\ub4dc", "\uadf8\ub79c\ub4dc\ub9c8\uc2a4\ud130",
                null, "\ucd5c\uc0c1", "\uc2b9\ucc9c");
        if ("no".equals(code)) return titleArray("Nybegynner", "Utrent", null, null, null, "Kunnskapsrik",
                "Trent", "Dyktig", "Talentfull", "Profesjonell", null, null, null, "Mester",
                null, null, null, null, "Oppsteget");
        if ("pl".equals(code)) return titleArray("Pocz\u0105tkuj\u0105cy", "Niewprawny", "Amator", "Praktykant",
                "Do\u015bwiadczony", "Zaprawiony", "Wyszkolony", "Uzdolniony", "Utalentowany",
                "Zawodowiec", null, null, "Znawca", "Mistrz", null, "Arcymistrz", null, "Boska",
                "Wst\u0105pi\u0142");
        if ("pt_br".equals(code)) return titleArray("Novato", "Caloiro", "Dedicado", "Aprendiz", "Experiente",
                "Gladiador", "Perspicaz", "Craque", "Talentoso", "Profissional", null, null, null,
                "Mestre", "Lenda", "Supremo", null, "Divina", "Ascendente");
        if ("pt".equals(code)) return titleArray("Novato", "Amador", "Aficionado", "Aprendiz", "Veterano",
                "Habilidoso", "Inteligente", "Perito", "Talentoso", "Profissional", null, null, null,
                "Mestre", "Lenda", "Supremo", null, "Divina", "Ascendente");
        if ("ro".equals(code)) return titleArray("\u00cencep\u0103tor", "Neinstruit", null, "Ucenic", "Experimentat",
                "Sezonier", "Antrenat", "\u00cendem\u00e2natic", "Talentat", "Profesionist", null, null,
                null, "Maestru", "Legend\u0103", "Mare Maestru", null, "Divin", null);
        if ("ru".equals(code)) return titleArray("\u041d\u043e\u0432\u0438\u0447\u043e\u043a",
                "\u041d\u0435\u043e\u0431\u0443\u0447\u0435\u043d\u043d\u044b\u0439", "\u041b\u044e\u0431\u0438\u0442\u0435\u043b\u044c",
                "\u0423\u0447\u0435\u043d\u0438\u043a", "\u041e\u043f\u044b\u0442\u043d\u044b\u0439", "\u0411\u044b\u0432\u0430\u043b\u044b\u0439",
                "\u041e\u0431\u0443\u0447\u0435\u043d\u043d\u044b\u0439", "\u0423\u043c\u0435\u043b\u044b\u0439",
                "\u0422\u0430\u043b\u0430\u043d\u0442\u043b\u0438\u0432\u044b\u0439", "\u041f\u0440\u043e\u0444\u0435\u0441\u0441\u0438\u043e\u043d\u0430\u043b",
                null, null, "\u042d\u043a\u0441\u043f\u0435\u0440\u0442", "\u041c\u0430\u0441\u0442\u0435\u0440",
                "\u041b\u0435\u0433\u0435\u043d\u0434\u0430", "\u0413\u0440\u043e\u0441\u0441\u043c\u0435\u0439\u0441\u0442\u0435\u0440",
                null, "\u0411\u043e\u0436\u0435\u0441\u0442\u0432\u0435\u043d\u043d\u044b\u0439", "\u0412\u043e\u0437\u043d\u0435\u0441\u0451\u043d\u043d\u044b\u0439");
        if ("es".equals(code)) return titleArray("Novato", "Amador", "Aficionado", "Aprendiz", "Veterano",
                "Habilidoso", "Inteligente", "Perito", "Talentoso", "Profesional", null, null,
                "Experto", "Maestro", "Leyenda", null, null, "Divina", "Ascendido");
        if ("sv".equals(code)) return titleArray("Nyb\u00f6rjare", "Otr\u00e4nad", "Amat\u00f6r", "L\u00e4rling", null,
                "H\u00e4rdad", "Tr\u00e4nad", "Skicklig", "Talangfull", null, null, null, null,
                "M\u00e4stare", null, "Storm\u00e4stare", null, "Underbart", "Uppsteg");
        if ("tr".equals(code)) return titleArray("\u00c7aylak", "E\u011fitimsiz", null, "Acemi", "Deneyimli",
                "Tecr\u00fcbeli", "E\u011fitilmi\u015f", "Becerikli", "Yetenekli", "Profesyonel",
                null, null, "Uzman", "Usta", "Efsane", "\u00dcstat", null, "\u0130lahi", "Y\u00fckselmi\u015f");
        if ("uk".equals(code)) return titleArray("\u041d\u043e\u0432\u0430\u0447\u043e\u043a",
                "\u041d\u0435\u043d\u0430\u0432\u0447\u0435\u043d\u0438\u0439", null, "\u0423\u0447\u0435\u043d\u044c",
                "\u0414\u043e\u0441\u0432\u0456\u0434\u0447\u0435\u043d\u0438\u0439", "\u0411\u0443\u0432\u0430\u043b\u0438\u0439",
                "\u041d\u0430\u0432\u0447\u0435\u043d\u0438\u0439", "\u0423\u043c\u0456\u043b\u0438\u0439",
                "\u0422\u0430\u043b\u0430\u043d\u043e\u0432\u0438\u0442\u0438\u0439", "\u041f\u0440\u043e\u0444\u0435\u0441\u0456\u043e\u043d\u0430\u043b",
                null, null, "\u0415\u043a\u0441\u043f\u0435\u0440\u0442", "\u041c\u0430\u0439\u0441\u0442\u0435\u0440",
                null, "\u0413\u0440\u043e\u0441\u043c\u0435\u0439\u0441\u0442\u0435\u0440", null,
                "\u0411\u043e\u0436\u0435\u0441\u0442\u0432\u0435\u043d\u043d\u0438\u0439", "\u0412\u043e\u0437\u043d\u0435\u0441\u0435\u043d\u0438\u0439");
        return null;
    }

    private static String[] titleArray(String... titles) {
        titleArrayBuildCount++;
        String[] out = Arrays.copyOf(EN_BB_TITLES, EN_BB_TITLES.length);
        for (int i = 0; i < titles.length && i < LEGACY_TO_CURRENT.length; i++) {
            int target = LEGACY_TO_CURRENT[i];
            if (target >= 17) continue;
            if (titles[i] != null && !titles[i].isEmpty()) out[target] = titles[i];
        }
        return out;
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
