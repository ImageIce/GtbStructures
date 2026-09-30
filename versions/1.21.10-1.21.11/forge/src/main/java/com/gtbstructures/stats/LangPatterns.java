package com.gtbstructures.stats;

import com.gtbstructures.scoreboard.GtbGame;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Language pattern definitions for all 24 GTB languages, plus
 * pattern-based query helpers (scoreboard state detection, title matching, etc.).
 */
public class LangPatterns {

    // ── Per-language fields ─────────────────────────────────────────────
    String lang;
    // Chat patterns
    String roundPrefix;
    String builderPrefix;
    String correctGuessSuffix;
    String correctGuessPrefix;    // null for most; "Hráč " (cs), "Гравець " (uk), "¡" (es)
    String themeWasPrefix;
    String themeWasSuffix;
    String charsLongPrefix;
    String charsLongSuffix;       // null for ru, cs (number at end)
    String winnersKeyword;
    String langSelectPrefix;
    String langSelectSuffix;      // null for most; " язык" (ru), "로 변경했습니다" (ko)
    // GUI
    String guiTitle;
    // Scoreboard
    String sbTheme;
    String sbBuilder;
    String sbTime;
    String sbStartsIn;
    boolean sbStartsInIsSuffix;
    String sbTitle;               // "GUESS THE BUILD" for most
    String sbMode;                // "Mode: Guess The Build" — lobby scoreboard line
    String sbNextRound;           // "Next Round:" — round-end timer label
    // Title packet
    String themeSelectingTitle;
    // AFK unique substring
    String afkSubstring;
    // Builder leave — two variants per language ("has left the game" vs short "owner left")
    // V1 is the long "plot owner has left the game" line, normally after a
    // build round is already announced. V2 is the short "owner left; skipping"
    // line that can happen during pre-round countdown.
    String builderLeftV1;
    String builderLeftV2;

    enum BuilderLeftMessageKind {
        NONE(null),
        PLOT_OWNER_LEFT_GAME("plot_owner_left_game"),
        OWNER_LEFT_BEFORE_ROUND("owner_left_before_round");

        final String detail;

        BuilderLeftMessageKind(String detail) {
            this.detail = detail;
        }
    }
    // Rejoin guesser: prefix + BUILDER + suffix
    String rejoinGuesserPrefix;
    String rejoinGuesserSuffix;
    // Rejoin builder: prefix + THEME + suffix
    String rejoinBuilderPrefix;
    String rejoinBuilderSuffix;
    // Lobby 1-second countdown chat message (e.g. "The game starts in 1 second!")
    // Used to schedule an accurate tab-list snapshot exactly 1s later when the game starts.
    String countdown1Sec;

    // Lobby join/leave detection for maintaining the player list during lobby.
    // Suffix that appears AFTER the player name in join/leave chat messages.
    // The join suffix contains a count like "(3/10)" — stored as a regex pattern.
    String lobbyJoinPrefix;   // text before player name (e.g. "Hráč " for cs), "" if name is first
    String lobbyJoinSuffix;   // regex pattern after player name, e.g. " has joined (\\d+/\\d+)!"
    String lobbyQuitPrefix;   // text before player name in quit message
    String lobbyQuitSuffix;   // text after player name in quit message

    // ── Pattern Registry ────────────────────────────────────────────────

    static final List<LangPatterns> ALL_PATTERNS = new ArrayList<>();
    static final Set<String> CHAT_TITLES = new HashSet<>();
    private static final String[] GUESSER_CHAT_PREFIXES = {
            "[GUESSER CHAT]",
            "[\u731c\u4e2d\u8005\u9891\u9053]",
            "[\u731c\u984c\u6703\u6664]",
            "[CHAT V\u00cdT\u011aZ\u016e]",
            "[G\u00c6TTER CHAT]",
            "[RADER CHAT]",
            "[ARVAUS-CHAT]",
            "[CHAT DE DEVINEUR]",
            "[BESSERWISSER CHAT]",
            "[TIPPEL\u0150 CHAT]",
            "[CHAT INDOVINO]",
            "[\u63a8\u6e2c\u8005\u30c1\u30e3\u30c3\u30c8]",
            "[\ucd94\uce21\uc790 \ucc44\ud305]",
            "[GJETTER-CHAT]",
            "[GUESSER CHAT]",
            "[CZAT ZGADUJ\u0104CYCH]",
            "[CHAT DE ADIVINHADOR]",
            "[CHAT DOS ADIVINHADORES]",
            "[GUESSER CHAT]",
            "[\u0427\u0410\u0422 \u041e\u0422\u0413\u0410\u0414\u0410\u0412\u0428\u0418\u0425]",
            "[CHAT DE ADIVINADOR]",
            "[GISSARECHATT]",
            "[TAHM\u0130NC\u0130 SOHBET\u0130]",
            "[\u0427\u0410\u0422 \u0412\u0406\u0414\u0413\u0410\u0414\u0423\u0412\u0410\u0427\u0406\u0412]"
    };
    private static final String LEADERBOARD_TITLE_NUM = "(?:10|[1-9])(?!\\d)";
    static final Pattern LEADERBOARD_TITLE_PATTERN = Pattern.compile(
            "^(?:#\\s*" + LEADERBOARD_TITLE_NUM + "\\s*\\S+(?:\\s+\\S+)*|"
                    + LEADERBOARD_TITLE_NUM + "\\s*[\\.\u00b0\u00ba]\\s+\\S+(?:\\s+\\S+)*|"
                    + "\\S+(?:\\s+\\S+)*\\s+(?:\u010d\\.|nr|#|\u2116)\\s*"
                    + LEADERBOARD_TITLE_NUM + ")$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LEADERBOARD_TITLE_PREFIX_HASH = Pattern.compile(
            "^#\\s*(" + LEADERBOARD_TITLE_NUM + ")\\s*\\S+(?:\\s+\\S+)*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LEADERBOARD_TITLE_PREFIX_ORDINAL = Pattern.compile(
            "^(" + LEADERBOARD_TITLE_NUM + ")\\s*[\\.\u00b0\u00ba]\\s+\\S+(?:\\s+\\S+)*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LEADERBOARD_TITLE_SUFFIX_NUMBER = Pattern.compile(
            "^\\S+(?:\\s+\\S+)*\\s+(?:\u010d\\.|nr|#|\u2116)\\s*("
                    + LEADERBOARD_TITLE_NUM + ")$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LEADERBOARD_TITLE_FRAGMENT_HASH = Pattern.compile(
            "(?:^|\\s)#\\s*(" + LEADERBOARD_TITLE_NUM + ")(?:\\s|$)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LEADERBOARD_TITLE_FRAGMENT_ORDINAL = Pattern.compile(
            "(?:^|\\s)(" + LEADERBOARD_TITLE_NUM + ")\\s*[\\.\u00b0\u00ba](?:\\s|$)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LEADERBOARD_TITLE_FRAGMENT_SUFFIX_NUMBER = Pattern.compile(
            "(?:^|\\s)(?:\u010d\\.|nr|#|\u2116)\\s*(" + LEADERBOARD_TITLE_NUM + ")(?:\\s|$)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SELF_CORRECT_GUESS_POINTS_PATTERN = Pattern.compile("^\\+\\s*([123])(?:\\s|$).*$");
    private static final Pattern MINI_SESSION_ID_PATTERN = Pattern.compile("^mini[0-9][0-9a-z]*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NON_WHITESPACE_TOKEN_PATTERN = Pattern.compile("\\S+");
    private static final Pattern NON_EMPTY_TEXT_PATTERN = Pattern.compile(".*\\S.*");
    private static final String[][] NEW_SERVER_TRANSFER_TEMPLATES = new String[][] {
            { "Sending you to ", "!" }, // en
            { "\u6b63\u5728\u524d\u5f80", "\uff01" }, // zh_cn
            { "\u6b63\u5728\u5c07\u4f60\u524d\u5f80\u81f3 ", "\uff01" }, // zh_tw
            { "Pos\u00edl\u00e1me t\u011b do ", "!" }, // cs
            { "Sender dig til ", "!" }, // da
            { "We sturen je naar ", "!" }, // nl
            { "L\u00e4hetet\u00e4\u00e4n sinut kohteeseen ", "!" }, // fi
            { "Envoi vers ", "!" }, // fr
            { "Du wirst zu ", " gesendet!" }, // de
            { "K\u00fcld\u00e9s ", " szerverre!" }, // hu
            { "Ti sto portando su ", "!" }, // it
            { "", "\u306b\u79fb\u52d5\u4e2d\u3067\u3059\uff01" }, // ja
            { "", "\uc73c\ub85c \uc774\ub3d9\ud569\ub2c8\ub2e4!" }, // ko
            { "Sender deg til ", "!" }, // no
            { "Sailin' ye t' ", "!" }, // pirate
            { "Wysy\u0142amy Ci\u0119 do ", "!" }, // pl
            { "A enviar-te para ", "!" }, // pt
            { "Conectando em ", "!" }, // pt_br
            { "Te trimis c\u0103tre ", "!" }, // ro
            { "\u041e\u0442\u043f\u0440\u0430\u0432\u043b\u044f\u0435\u043c \u0432\u0430\u0441 \u043d\u0430 ", "!" }, // ru
            { "Envi\u00e1ndote hacia ", "!" }, // es
            { "Skickar dig till ", "!" }, // sv
            { " ", " sunucusuna y\u00f6nlendiriliyorsunuz!" }, // tr
            { "\u0412\u0456\u0434\u043f\u0440\u0430\u0432\u043b\u044f\u0454\u043c\u043e \u0432\u0430\u0441 \u0434\u043e ", "!" }, // uk
    };
    private static final String[][] LOBBY_PROTECTION_TEMPLATES = new String[][] {
            { "To leave ", ", type /lobby" }, // en
            { "\u8f93\u5165/lobby\u6765\u79bb\u5f00", "\u3002" }, // zh_cn
            { "\u82e5\u8981\u96e2\u958b", "\uff0c\u8acb\u8f38\u5165 /lobby" }, // zh_tw
            { "Pro opu\u0161t\u011bn\u00ed ", ", napi\u0161 /lobby" }, // cs
            { "For at forlade ", ", skriv /lobby" }, // da
            { "Om ", " te verlaten, typ /lobby" }, // nl
            { "Poistuaksesi pelist\u00e4 ", " kirjoita /lobby" }, // fi
            { "Pour quitter ", ", tapez /lobby" }, // fr
            { "Schreibe /lobby ", ", um das Spiel zu verlassen" }, // de
            { "A ", ", elhagy\u00e1s\u00e1hoz \u00edrd be /lobby" }, // hu
            { "Per uscire dalle ", ", digita /lobby" }, // it
            { "", " \u304b\u3089\u9000\u51fa\u3059\u308b\u969b\u306f /lobby \u3092\u4f7f\u7528\u3057\u3066\u304f\u3060\u3055\u3044\u3002" }, // ja
            { "", "\uc744(\ub97c) \ub098\uac00\ub824\uba74, /lobby\ub97c \uc785\ub825\ud558\uc138\uc694" }, // ko
            { "For \u00e5 forlate ", ", skriv /lobby" }, // no
            { "T' leave ", ", type /lobby" }, // pirate
            { "Aby wyj\u015b\u0107 z ", ", wpisz /lobby" }, // pl
            { "Para abandonar ", ", escreve /lobby" }, // pt
            { "Para sair do ", ", use /lobby" }, // pt_br
            { "Dac\u0103 vrei s\u0103 ie\u0219i din ", ", scrie /lobby" }, // ro
            { "\u0427\u0442\u043e\u0431\u044b \u0432\u044b\u0439\u0442\u0438 \u0438\u0437 ", ", \u0432\u0432\u0435\u0434\u0438\u0442\u0435 /lobby" }, // ru
            { "Para abandonar ", ", utiliza /lobby" }, // es
            { "F\u00f6r att l\u00e4mna ", ", skriv /lobby" }, // sv
            { "", " oyunundan \u00e7\u0131kmak i\u00e7in /lobby yaz\u0131n." }, // tr
            { "\u0429\u043e\u0431 \u043f\u043e\u043a\u0438\u043d\u0443\u0442\u0438 ", ", \u043f\u0440\u043e\u043f\u0438\u0448\u0456\u0442\u044c /lobby" }, // uk
    };
    private static final String[] EN_BB_TITLES = new String[] {
            "Rookie", "Untrained", "Amateur", "Prospect", "Apprentice",
            "Experienced", "Seasoned", "Trained", "Skilled", "Talented",
            "Professional", "Artisan", "Expert", "Master", "Legend",
            "Grandmaster", "Celestial", "Divine", "Ascended"
    };
    // Hypixel currently exposes Divine and Ascended in English only. Keep their
    // rank indexes active so encoded GTB tab metadata maps 17/18 to the named
    // ranks, not to synthetic "#N Builder" leaderboard titles.
    private static final int ACTIVE_BB_TITLE_COUNT = 19;
    private static final String[] BB_TITLE_COLORS = new String[] {
            "\u00a7f", "\u00a77", "\u00a78", "\u00a7a", "\u00a72",
            "\u00a7b", "\u00a73", "\u00a79", "\u00a71", "\u00a75",
            "\u00a7d", "\u00a7c", "\u00a74", "\u00a76", "\u00a7a\u00a7l",
            "\u00a7b\u00a7l", "\u00a7d\u00a7l", "\u00a7c\u00a7l", "\u00a76\u00a7l"
    };
    private static final int[] LEGACY_TO_CURRENT_BB_TITLE_INDEX = new int[] {
            0, 1, 2, 4, 5,
            6, 7, 8, 9, 10,
            11, 3, 12, 13, 14,
            15, 16, 17, 18
    };
    private static final Map<String, String[]> PREFERRED_CHAT_TITLES =
            new HashMap<String, String[]>();
    private static int preferredChatTitleArrayBuildCount;

    private static final List<ThemeRevealPattern> THEME_REVEAL_PATTERNS = buildThemeRevealPatterns();

    public static final class ThemeRevealPattern {
        public final String prefix;
        public final String suffix;

        private ThemeRevealPattern(String prefix, String suffix) {
            this.prefix = prefix;
            this.suffix = suffix;
        }
    }

    private static LangPatterns lp(String lang) {
        LangPatterns p = new LangPatterns();
        p.lang = lang;
        p.sbTitle = "GUESS THE BUILD";
        p.sbStartsInIsSuffix = false;
        return p;
    }

    private static List<ThemeRevealPattern> buildThemeRevealPatterns() {
        List<ThemeRevealPattern> patterns = new ArrayList<>();
        patterns.add(new ThemeRevealPattern("The theme is ", ""));
        patterns.add(new ThemeRevealPattern("主题是", ""));
        patterns.add(new ThemeRevealPattern("主題是", ""));
        patterns.add(new ThemeRevealPattern("Le theme est ", ""));
        patterns.add(new ThemeRevealPattern("Das Thema ist ", ""));
        patterns.add(new ThemeRevealPattern("Il tema è ", ""));
        patterns.add(new ThemeRevealPattern("Тема — ", ""));
        patterns.add(new ThemeRevealPattern("El tema es ", ""));
        patterns.add(new ThemeRevealPattern("Temat är ", ""));
        patterns.add(new ThemeRevealPattern("テーマは ", " です"));
        patterns.add(new ThemeRevealPattern("주제는 ", "입니다"));
        patterns.add(new ThemeRevealPattern("Téma je ", ""));
        patterns.add(new ThemeRevealPattern("Temaet er ", ""));
        patterns.add(new ThemeRevealPattern("Het thema is ", ""));
        patterns.add(new ThemeRevealPattern("Aihe on ", ""));
        patterns.add(new ThemeRevealPattern("A téma", ""));
        patterns.add(new ThemeRevealPattern("Th' theme be ", ""));
        patterns.add(new ThemeRevealPattern("Motyw to ", ""));
        patterns.add(new ThemeRevealPattern("O tema é ", ""));
        patterns.add(new ThemeRevealPattern("Tema este ", ""));  // Romanian must come before Turkish.
        patterns.add(new ThemeRevealPattern("Tema ", ""));        // Turkish.
        patterns.add(new ThemeRevealPattern("Тема: ", ""));
        return Collections.unmodifiableList(patterns);
    }

    public static List<ThemeRevealPattern> themeRevealPatterns() {
        return THEME_REVEAL_PATTERNS;
    }

    public static ThemeRevealPattern themeRevealPatternForLang(String langCode) {
        String code = langCode == null || langCode.trim().isEmpty()
                ? "en" : langCode.trim().toLowerCase(java.util.Locale.ROOT).replace('-', '_');
        if ("zh_cn".equals(code)) return findThemeRevealPattern("\u4e3b\u9898\u662f", "");
        if ("zh_tw".equals(code)) return findThemeRevealPattern("\u4e3b\u984c\u662f", "");
        if ("fr".equals(code)) return findThemeRevealPattern("Le theme est ", "");
        if ("de".equals(code)) return findThemeRevealPattern("Das Thema ist ", "");
        if ("it".equals(code)) return findThemeRevealPattern("Il tema \u00e8 ", "");
        if ("ru".equals(code)) return findThemeRevealPattern("\u0422\u0435\u043c\u0430 \u2014 ", "");
        if ("es".equals(code)) return findThemeRevealPattern("El tema es ", "");
        if ("sv".equals(code)) return findThemeRevealPattern("Temat \u00e4r ", "");
        if ("ja".equals(code)) return findThemeRevealPattern("\u30c6\u30fc\u30de\u306f ", " \u3067\u3059");
        if ("ko".equals(code)) return findThemeRevealPattern("\uc8fc\uc81c\ub294 ", "\uc785\ub2c8\ub2e4");
        if ("cs".equals(code)) return findThemeRevealPattern("T\u00e9ma je ", "");
        if ("no".equals(code) || "da".equals(code)) return findThemeRevealPattern("Temaet er ", "");
        if ("nl".equals(code)) return findThemeRevealPattern("Het thema is ", "");
        if ("fi".equals(code)) return findThemeRevealPattern("Aihe on ", "");
        if ("hu".equals(code)) return findThemeRevealPattern("A t\u00e9ma", "");
        if ("pirate".equals(code)) return findThemeRevealPattern("Th' theme be ", "");
        if ("pl".equals(code)) return findThemeRevealPattern("Motyw to ", "");
        if ("pt_br".equals(code) || "pt".equals(code)) return findThemeRevealPattern("O tema \u00e9 ", "");
        if ("ro".equals(code)) return findThemeRevealPattern("Tema este ", "");
        if ("tr".equals(code)) return findThemeRevealPattern("Tema ", "");
        return findThemeRevealPattern("The theme is ", "");
    }

    private static ThemeRevealPattern findThemeRevealPattern(String prefix, String suffix) {
        String expectedSuffix = suffix == null ? "" : suffix;
        for (ThemeRevealPattern pattern : THEME_REVEAL_PATTERNS) {
            if (pattern.prefix.equals(prefix) && pattern.suffix.equals(expectedSuffix)) {
                return pattern;
            }
        }
        return THEME_REVEAL_PATTERNS.get(0);
    }

    public static Integer selfCorrectGuessPoints(String cleanLine) {
        if (cleanLine == null) return null;
        Matcher m = SELF_CORRECT_GUESS_POINTS_PATTERN.matcher(cleanLine.trim());
        if (!m.matches()) return null;
        return Integer.valueOf(Integer.parseInt(m.group(1)));
    }

    static boolean isMiniServerTransferMessage(String cleanLine) {
        return matchesVariableTemplate(cleanLine, NEW_SERVER_TRANSFER_TEMPLATES,
                MINI_SESSION_ID_PATTERN);
    }

    /** Matches a stripped, localized Hypixel /l protection line with a nonempty game name. */
    public static boolean isLobbyProtectionMessage(String cleanLine) {
        return matchesVariableTemplate(cleanLine, LOBBY_PROTECTION_TEMPLATES,
                NON_EMPTY_TEXT_PATTERN);
    }

    private static boolean matchesVariableTemplate(String cleanLine, String[][] templates,
                                                    Pattern variablePattern) {
        if (cleanLine == null) return false;
        for (String[] template : templates) {
            String prefix = template[0];
            String suffix = template[1];
            if (!cleanLine.startsWith(prefix) || !cleanLine.endsWith(suffix)) continue;
            int start = prefix.length();
            int end = cleanLine.length() - suffix.length();
            if (end < start) continue;
            String variable = cleanLine.substring(start, end).trim();
            if (variablePattern.matcher(variable).matches()) return true;
        }
        return false;
    }

    static BuilderLeftMessageKind builderLeftMessageKind(String cleanLine) {
        if (cleanLine == null || cleanLine.isEmpty()) return BuilderLeftMessageKind.NONE;
        BuilderLeftMessageKind best = BuilderLeftMessageKind.NONE;
        int bestLen = -1;
        for (LangPatterns lp : ALL_PATTERNS) {
            if (lp.builderLeftV1 != null && cleanLine.contains(lp.builderLeftV1)
                    && lp.builderLeftV1.length() > bestLen) {
                best = BuilderLeftMessageKind.PLOT_OWNER_LEFT_GAME;
                bestLen = lp.builderLeftV1.length();
            }
            if (lp.builderLeftV2 != null && cleanLine.contains(lp.builderLeftV2)
                    && lp.builderLeftV2.length() > bestLen) {
                best = BuilderLeftMessageKind.OWNER_LEFT_BEFORE_ROUND;
                bestLen = lp.builderLeftV2.length();
            }
        }
        return best;
    }

    // ── Language Definitions ────────────────────────────────────────────

    static {
        // ── English ──
        LangPatterns en = lp("en");
        en.roundPrefix = "Round: ";
        en.builderPrefix = "Builder: ";
        en.correctGuessSuffix = " correctly guessed the theme!";
        en.themeWasPrefix = "The theme was: ";
        en.themeWasSuffix = "!";
        en.charsLongPrefix = "The theme is ";
        en.charsLongSuffix = " characters long";
        en.winnersKeyword = "Winners";
        en.langSelectPrefix = "Selected language: ";
        en.guiTitle = "Select a theme to build!";
        en.sbTheme = "Theme:"; en.sbBuilder = "Builder:"; en.sbTime = "Time:"; en.sbStartsIn = "Starts In:";
        en.sbMode = "Mode: Guess The Build"; en.sbNextRound = "Next Round:";
        en.themeSelectingTitle = "Theme is being selected...";
        en.afkSubstring = "AFK! Skipping";
        en.builderLeftV1 = "has left the game";
        en.builderLeftV2 = "The owner left";
        en.rejoinGuesserPrefix = "Welcome back! It's ";
        en.rejoinGuesserSuffix = "'s turn to build. You have to guess the build!";
        en.rejoinBuilderPrefix = "Welcome back! You are building something relevant to the theme ";
        en.rejoinBuilderSuffix = ".";
        en.countdown1Sec = "The game starts in 1 second!";
        en.lobbyJoinSuffix = " has joined (\\d+/\\d+)!";
        en.lobbyQuitSuffix = " has quit!";
        ALL_PATTERNS.add(en);

        // ── French ──
        LangPatterns fr = lp("fr");
        fr.roundPrefix = "Manche: ";
        fr.builderPrefix = "Constructeur: ";
        fr.correctGuessSuffix = " a correctement devin\u00e9 le th\u00e8me!";
        fr.themeWasPrefix = "Le th\u00e8me \u00e9tait: ";
        fr.themeWasSuffix = "!";
        fr.charsLongPrefix = "Le th\u00e8me est compos\u00e9 de ";
        fr.charsLongSuffix = " lettres";
        fr.winnersKeyword = "Gagnants";
        fr.langSelectPrefix = "Langue s\u00e9lectionn\u00e9e: ";
        fr.guiTitle = "S\u00e9lectionne un th\u00e8me \u00e0 construire!";
        fr.sbTheme = "Th\u00e8me:"; fr.sbBuilder = "Constructeur:"; fr.sbTime = "Temps:"; fr.sbStartsIn = "D\u00e9bute dans";
        fr.sbMode = "Mode: Guess The Build"; fr.sbNextRound = "Manche suivante:";
        fr.themeSelectingTitle = "Th\u00e8me en cours de s\u00e9lection...";
        fr.afkSubstring = "AFK! Au suivant";
        fr.builderLeftV1 = "a quitt\u00e9 la partie";
        fr.builderLeftV2 = "Le propri\u00e9taire a quitt\u00e9";
        fr.rejoinGuesserPrefix = "Re-Bonjour! C'est \u00e0 ";
        fr.rejoinGuesserSuffix = " de construire. Vous devez deviner sa construction!";
        fr.rejoinBuilderPrefix = "Bon retour! Vous \u00eates en train de construire quelque chose sur le th\u00e8me ";
        fr.rejoinBuilderSuffix = ".";
        fr.countdown1Sec = "La partie d\u00e9bute dans 1 seconde!";
        fr.lobbyJoinSuffix = " a rejoint (\\d+/\\d+)!";
        fr.lobbyQuitSuffix = " a quitté!";
        ALL_PATTERNS.add(fr);

        // ── German ──
        LangPatterns de = lp("de");
        de.roundPrefix = "Runde: ";
        de.builderPrefix = "Erbauer: ";
        de.correctGuessSuffix = " hat das Thema richtig erraten!";
        de.themeWasPrefix = "Das Thema war: ";
        de.themeWasSuffix = "!";
        de.charsLongPrefix = "Das Thema ist ";
        de.charsLongSuffix = " Zeichen lang";
        de.winnersKeyword = "Sieger";
        de.langSelectPrefix = "Ausgew\u00e4hlte Sprache: ";
        de.guiTitle = "W\u00e4hle zum Bauen ein Thema aus!";
        de.sbTheme = "Thema:"; de.sbBuilder = "Erbauer:"; de.sbTime = "Zeit:"; de.sbStartsIn = "Startet in";
        de.sbMode = "Modus: Guess The Build"; de.sbNextRound = "N\u00e4chste Runde:";
        de.themeSelectingTitle = "Thema wird ausgew\u00e4hlt ...";
        de.afkSubstring = "AFK! \u00dcberspringen";
        de.builderLeftV1 = "hat das Spiel verlassen";
        de.builderLeftV2 = "Besitzer ist weg";
        de.rejoinGuesserPrefix = "Willkommen zur\u00fcck! Es ist gerade ";
        de.rejoinGuesserSuffix = "'s Runde. Du musst das Thema erraten!";
        de.rejoinBuilderPrefix = "Willkommen zur\u00fcck! Du baust etwas zum Thema ";
        de.rejoinBuilderSuffix = ".";
        de.countdown1Sec = "Das Spiel beginnt in 1 Sekunde!";
        de.lobbyJoinSuffix = " hat das Spiel betreten (\\d+/\\d+)!";
        de.lobbyQuitSuffix = " hat das Spiel verlassen!";
        ALL_PATTERNS.add(de);

        // ── Italian ──
        LangPatterns it = lp("it");
        it.roundPrefix = "Round: ";
        it.builderPrefix = "Costruttore: ";
        it.correctGuessSuffix = " ha indovinato il tema!";
        it.themeWasPrefix = "Il tema era: ";
        it.themeWasSuffix = "!";
        it.charsLongPrefix = "Il tema \u00e8 lungo ";
        it.charsLongSuffix = " lettere";
        it.winnersKeyword = "Vincitori";
        it.langSelectPrefix = "Lingua selezionata: ";
        it.guiTitle = "Seleziona un tema da costruire!";
        it.sbTheme = "Tema:"; it.sbBuilder = "Costruttore:"; it.sbTime = "Tempo:"; it.sbStartsIn = "Inizia Tra";
        it.sbMode = "Modalit\u00e0: Guess The Build"; it.sbNextRound = "Prossimo Round:";
        it.themeSelectingTitle = "Il tema sta venendo selezionato...";
        it.afkSubstring = "AFK! Saltando";
        it.builderLeftV1 = "lotto \u00e8 uscito";
        it.builderLeftV2 = "proprietario \u00e8 uscito";
        it.rejoinGuesserPrefix = "Bentornato! Tocca a ";
        it.rejoinGuesserSuffix = " costruire. Devi indovinare la costruzione!";
        it.rejoinBuilderPrefix = "Bentornato! Costruisci qualcosa che riguarda: ";
        it.rejoinBuilderSuffix = ".";
        it.countdown1Sec = "La partita inizia tra 1 secondo!";
        it.lobbyJoinSuffix = " è entrato (\\d+/\\d+)!";
        it.lobbyQuitSuffix = " è uscito!";
        ALL_PATTERNS.add(it);

        // ── Dutch ──
        LangPatterns nl = lp("nl");
        nl.roundPrefix = "Runde: ";
        nl.builderPrefix = "Bouwer: ";
        nl.correctGuessSuffix = " heeft het thema correct geraden!";
        nl.themeWasPrefix = "Het thema was: ";
        nl.themeWasSuffix = "!";
        nl.charsLongPrefix = "Het thema is ";
        nl.charsLongSuffix = " letters lang";
        nl.winnersKeyword = "Winnaars";
        nl.langSelectPrefix = "Geselecteerde taal: ";
        nl.guiTitle = "Selecteer een thema om te bouwen!";
        nl.sbTheme = "Thema:"; nl.sbBuilder = "Bouwer:"; nl.sbTime = "Tijd:"; nl.sbStartsIn = "Begint over";
        nl.sbMode = "Modus: Guess The Build"; nl.sbNextRound = "Volgende Ronde:";
        nl.themeSelectingTitle = "Thema wordt geselecteerd...";
        nl.afkSubstring = "afwezig! Overslaan";
        nl.builderLeftV1 = "eigenaar is weggegaan";
        nl.builderLeftV2 = "eigenaar is weggegaan";
        nl.rejoinGuesserPrefix = "Welkom terug! Het is ";
        nl.rejoinGuesserSuffix = "'s zijn beurt om te bouwen. Je moet het bouwwerk raden!";
        nl.rejoinBuilderPrefix = "Welkom terug! Je bouwt iets relevant tot het thema ";
        nl.rejoinBuilderSuffix = ".";
        nl.countdown1Sec = "Het spel begint over 1 seconde!";
        nl.lobbyJoinSuffix = " trad toe (\\d+/\\d+)!";
        nl.lobbyQuitSuffix = " is weggegaan!";
        ALL_PATTERNS.add(nl);

        // ── Spanish ──
        LangPatterns es = lp("es");
        es.roundPrefix = "Ronda: ";
        es.builderPrefix = "Constructor: ";
        es.correctGuessPrefix = "\u00a1";
        es.correctGuessSuffix = " adivin\u00f3 el tema correctamente!";
        es.themeWasPrefix = "\u00a1El tema fue: ";
        es.themeWasSuffix = "!";
        es.charsLongPrefix = "El tema es ";
        es.charsLongSuffix = " letras de largo";
        es.winnersKeyword = "Ganadores";
        es.langSelectPrefix = "Idioma seleccionado: ";
        es.guiTitle = "\u00a1Elige un tema para construir!";
        es.sbTheme = "Tema:"; es.sbBuilder = "Constructor:"; es.sbTime = "Tiempo:"; es.sbStartsIn = "Comienza En";
        es.sbMode = "Modo: Guess The Build"; es.sbNextRound = "Siguiente Ronda:";
        es.themeSelectingTitle = "El tema est\u00e1 siendo seleccionado...";
        es.afkSubstring = "AFK! Pasando al siguiente";
        es.builderLeftV1 = "abandonado el juego";
        es.builderLeftV2 = "due\u00f1o sali\u00f3";
        es.rejoinGuesserPrefix = "\u00a1Bienvenido! Es turno de ";
        es.rejoinGuesserSuffix = " para construir. \u00a1Tienes que adivinar la construcci\u00f3n!";
        es.rejoinBuilderPrefix = "\u00a1Bienvenido de nuevo! Est\u00e1s construyendo algo relevante al tema ";
        es.rejoinBuilderSuffix = ".";
        es.countdown1Sec = "El juego comienza en 1 segundo!";
        es.lobbyJoinPrefix = "¡";
        es.lobbyJoinSuffix = " se ha unido (\\d+/\\d+)!";
        es.lobbyQuitPrefix = "¡";
        es.lobbyQuitSuffix = " ha salido!";
        ALL_PATTERNS.add(es);

        // ── Portuguese (BR) ──
        LangPatterns ptBR = lp("pt_BR");
        ptBR.roundPrefix = "Rodada: ";
        ptBR.builderPrefix = "Construtor: ";
        ptBR.correctGuessSuffix = " adivinhou o tema!";
        ptBR.themeWasPrefix = "O tema era ";
        ptBR.themeWasSuffix = "!";
        ptBR.charsLongPrefix = "O tema tem ";
        ptBR.charsLongSuffix = " letras";
        ptBR.winnersKeyword = "Vencedores";
        ptBR.langSelectPrefix = "Idioma selecionado: ";
        ptBR.guiTitle = "Escolha um tema para construir!";
        ptBR.sbTheme = "Tema:"; ptBR.sbBuilder = "Construtor:"; ptBR.sbTime = "Tempo:"; ptBR.sbStartsIn = "Come\u00e7a em";
        ptBR.sbMode = "Modo: Guess The Build"; ptBR.sbNextRound = "Pr\u00f3xima rodada:";
        ptBR.themeSelectingTitle = "O tema est\u00e1 sendo escolhido...";
        ptBR.afkSubstring = "ausente! Pulando";
        ptBR.builderLeftV1 = "desta constru\u00e7\u00e3o saiu do jogo";
        ptBR.builderLeftV2 = "construtor saiu";
        ptBR.rejoinGuesserPrefix = "Bem-vindo de volta! ";
        ptBR.rejoinGuesserSuffix = " est\u00e1 construindo e voc\u00ea tem que adivinhar a constru\u00e7\u00e3o!";
        ptBR.rejoinBuilderPrefix = "Bem-vindo de volta! Voc\u00ea est\u00e1 construindo algo relevante para o tema ";
        ptBR.rejoinBuilderSuffix = ".";
        ptBR.countdown1Sec = "O jogo come\u00e7a em 1 segundo!";
        ptBR.lobbyJoinSuffix = " entrou! (\\d+/\\d+)";
        ptBR.lobbyQuitSuffix = " saiu!";
        ALL_PATTERNS.add(ptBR);

        // ── Portuguese ──
        LangPatterns pt = lp("pt");
        pt.roundPrefix = "Ronda: ";
        pt.builderPrefix = "Construtor: ";
        pt.correctGuessSuffix = " adivinhou corretamente o tema!";
        pt.themeWasPrefix = "O tema era: ";
        pt.themeWasSuffix = "!";
        pt.charsLongPrefix = "O tema tem ";
        pt.charsLongSuffix = " letras";
        pt.winnersKeyword = "Vencedores";
        pt.langSelectPrefix = "Idioma selecionado: ";
        pt.guiTitle = "Escolhe um tema para construir!";
        pt.sbTheme = "Tema:"; pt.sbBuilder = "Construtor:"; pt.sbTime = "Tempo:"; pt.sbStartsIn = "Come\u00e7a em";
        pt.sbMode = "Modo: Guess The Build"; pt.sbNextRound = "Pr\u00f3xima ronda:";
        pt.themeSelectingTitle = "O tema est\u00e1 a ser escolhido...";
        pt.afkSubstring = "ausente! Passemos";
        pt.builderLeftV1 = "dono do plot saiu do jogo";
        pt.builderLeftV2 = "dono do plot saiu";
        pt.rejoinGuesserPrefix = "Bem-vindo de volta! \u00c9 a vez de ";
        pt.rejoinGuesserSuffix = " a construir. Tens de adivinhar a constru\u00e7\u00e3o!";
        pt.rejoinBuilderPrefix = "Bem-vindo de volta! Est\u00e1s a construir algo relevante com o tema ";
        pt.rejoinBuilderSuffix = ".";
        pt.countdown1Sec = "O jogo come\u00e7a em 1 segundo!";
        pt.lobbyJoinSuffix = " entrou (\\d+/\\d+)!";
        pt.lobbyQuitSuffix = " saiu!";
        ALL_PATTERNS.add(pt);

        // ── Russian ──
        LangPatterns ru = lp("ru");
        ru.roundPrefix = "\u0420\u0430\u0443\u043d\u0434: ";
        ru.builderPrefix = "\u0421\u0442\u0440\u043e\u0438\u0442\u0435\u043b\u044c: ";
        ru.correctGuessSuffix = " \u043e\u0442\u0433\u0430\u0434\u0430\u043b \u0442\u0435\u043c\u0443!";
        ru.themeWasPrefix = "\u0422\u0435\u043c\u043e\u0439 \u044f\u0432\u043b\u044f\u043b\u0430\u0441\u044c: ";
        ru.themeWasSuffix = "!";
        ru.charsLongPrefix = "\u041a\u043e\u043b\u0438\u0447\u0435\u0441\u0442\u0432\u043e \u0441\u0438\u043c\u0432\u043e\u043b\u043e\u0432 \u0432 \u0442\u0435\u043c\u0435: ";
        ru.charsLongSuffix = null;
        ru.winnersKeyword = "\u041f\u043e\u0431\u0435\u0434\u0438\u0442\u0435\u043b\u0438";
        ru.langSelectPrefix = "\u0412\u044b \u0432\u044b\u0431\u0440\u0430\u043b\u0438 ";
        ru.langSelectSuffix = " \u044f\u0437\u044b\u043a";
        ru.guiTitle = "\u0412\u044b\u0431\u0435\u0440\u0438\u0442\u0435 \u0442\u0435\u043c\u0443 \u0434\u043b\u044f \u043f\u043e\u0441\u0442\u0440\u043e\u0439\u043a\u0438!";
        ru.sbTheme = "\u0422\u0435\u043c\u0430:"; ru.sbBuilder = "\u0421\u0442\u0440\u043e\u0438\u0442\u0435\u043b\u044c:";
        ru.sbTime = "\u0412\u0440\u0435\u043c\u044f:"; ru.sbStartsIn = "\u041d\u0430\u0447\u043d\u0451\u0442\u0441\u044f \u0447\u0435\u0440\u0435\u0437";
        ru.sbMode = "\u0420\u0435\u0436\u0438\u043c: Guess The Build"; ru.sbNextRound = "\u0421\u043b\u0435\u0434\u0443\u044e\u0449\u0438\u0439 \u0440\u0430\u0443\u043d\u0434:";
        ru.themeSelectingTitle = "\u0412\u044b\u0431\u043e\u0440 \u0442\u0435\u043c\u044b...";
        ru.afkSubstring = "\u043e\u0442\u043e\u0448\u0451\u043b! \u041f\u0440\u043e\u043f\u0443\u0441\u043a";
        ru.builderLeftV1 = "\u0412\u043b\u0430\u0434\u0435\u043b\u0435\u0446 \u0443\u0447\u0430\u0441\u0442\u043a\u0430 \u043f\u043e\u043a\u0438\u043d\u0443\u043b";
        ru.builderLeftV2 = "\u0421\u0442\u0440\u043e\u0438\u0442\u0435\u043b\u044c \u0432\u044b\u0448\u0435\u043b";
        ru.rejoinGuesserPrefix = "\u0421 \u0432\u043e\u0437\u0432\u0440\u0430\u0449\u0435\u043d\u0438\u0435\u043c! \u041e\u0447\u0435\u0440\u0435\u0434\u044c \u0441\u0442\u0440\u043e\u0438\u0442\u044c \u0438\u0433\u0440\u043e\u043a\u0443 ";
        ru.rejoinGuesserSuffix = ". \u0412\u044b \u0434\u043e\u043b\u0436\u043d\u044b \u0443\u0433\u0430\u0434\u0430\u0442\u044c \u0435\u0433\u043e \u043f\u043e\u0441\u0442\u0440\u043e\u0439\u043a\u0443!";
        ru.rejoinBuilderPrefix = "\u0421 \u0432\u043e\u0437\u0432\u0440\u0430\u0449\u0435\u043d\u0438\u0435\u043c! \u041f\u043e\u0441\u0442\u0440\u043e\u0439\u0442\u0435 \u0447\u0442\u043e-\u0442\u043e \u043d\u0430 \u0442\u0435\u043c\u0443 ";
        ru.rejoinBuilderSuffix = ".";
        ru.countdown1Sec = "\u0418\u0433\u0440\u0430 \u043d\u0430\u0447\u043d\u0451\u0442\u0441\u044f \u0447\u0435\u0440\u0435\u0437 1 \u0441\u0435\u043a.!";
        ru.lobbyJoinSuffix = " присоединился (\\d+/\\d+)!";
        ru.lobbyQuitSuffix = " вышел!";
        ALL_PATTERNS.add(ru);

        // ── Korean ──
        LangPatterns ko = lp("ko");
        ko.roundPrefix = "\ub77c\uc6b4\ub4dc: ";
        ko.builderPrefix = "\uac74\ucd95\uac00: ";
        ko.correctGuessSuffix = " \ub2d8\uc774 \uc815\ud655\ud788 \uc8fc\uc81c\ub97c \uc54c\uc544\ub9de\ud600\uc2b5\ub2c8\ub2e4!";
        ko.themeWasPrefix = "\uc8fc\uc81c\ub294 ";
        ko.themeWasSuffix = "\uc774\uc5c8\uc2b5\ub2c8\ub2e4!";
        ko.charsLongPrefix = "\uc774 \uc8fc\uc81c\ub294 ";
        ko.charsLongSuffix = "\uae00\uc790\ub97c \uac00\uc9d1\ub2c8\ub2e4";
        ko.winnersKeyword = "\uc2b9\uc790";
        ko.langSelectPrefix = "\uc5b8\uc5b4\ub97c ";
        ko.langSelectSuffix = "\ub85c \ubcc0\uacbd\ud588\uc2b5\ub2c8\ub2e4";
        ko.guiTitle = "\uac74\ucd95\ud560 \uc8fc\uc81c\ub97c \uc120\ud0dd\ud558\uc138\uc694!";
        ko.sbTheme = "\uc8fc\uc81c:"; ko.sbBuilder = "\uac74\ucd95\uac00:"; ko.sbTime = "\uc2dc\uac04:"; ko.sbStartsIn = "\uc2dc\uc791\uae4c\uc9c0";
        ko.sbMode = "\ubaa8\ub4dc: Guess The Build"; ko.sbNextRound = "\ub2e4\uc74c \ub77c\uc6b4\ub4dc:";
        ko.themeSelectingTitle = "\uc8fc\uc81c\uac00 \uc120\uc815\ub418\uace0 \uc788\uc2b5\ub2c8\ub2e4...";
        ko.afkSubstring = "\uc790\ub9ac\ub97c \ube44\uc6e0\uc2b5\ub2c8\ub2e4";
        ko.builderLeftV1 = "\uc18c\uc720\uc790\uac00 \uac8c\uc784\uc744 \ub098\uac14";
        ko.builderLeftV2 = "\uc18c\uc720\uc790\uac00 \ub098\uac14";
        ko.rejoinGuesserPrefix = "\ub2e4\uc2dc \ub3cc\uc544\uc628 \uac83\uc744 \ud658\uc601\ud569\ub2c8\ub2e4! \uc9c0\uae08\uc740 ";
        ko.rejoinGuesserSuffix = " \ub2d8\uc758 \ucc28\ub840\uc785\ub2c8\ub2e4. \uac74\ucd95\ubb3c\uc744 \ucd94\uce21\ud574\ubcf4\uc138\uc694!";
        ko.rejoinBuilderPrefix = "\uc5b4\uc11c\uc624\uc138\uc694! ";
        ko.rejoinBuilderSuffix = " \uc8fc\uc81c\uc5d0 \uad00\ub828\ub41c \ubb34\uc5b8\uac00\ub97c \uac74\ucd95\ud558\uc138\uc694.";
        ko.countdown1Sec = "\uac8c\uc784\uc774 1\ucd08 \ud6c4\uc5d0 \uc2dc\uc791\ub429\ub2c8\ub2e4!";
        ko.lobbyJoinSuffix = " 님이 참여했습니다! (\\d+/\\d+)";
        ko.lobbyQuitSuffix = " 님이 나갔습니다!";
        ALL_PATTERNS.add(ko);

        // ── Polish ──
        LangPatterns pl = lp("pl");
        pl.roundPrefix = "Runda: ";
        pl.builderPrefix = "Budowniczy: ";
        pl.correctGuessSuffix = " poprawnie odgad\u0142 motyw!";
        pl.themeWasPrefix = "Motywem by\u0142: ";
        pl.themeWasSuffix = "!";
        pl.charsLongPrefix = "Motyw jest ";
        pl.charsLongSuffix = " literowy";
        pl.winnersKeyword = "Zwyci\u0119zcy";
        pl.langSelectPrefix = "Wybrany j\u0119zyk: ";
        pl.guiTitle = "Wybierz motyw do zbudowania!";
        pl.sbTheme = "Motyw:"; pl.sbBuilder = "Budowniczy:"; pl.sbTime = "Czas:"; pl.sbStartsIn = "Rozpocz\u0119cie za";
        pl.sbMode = "Tryb: Guess The Build"; pl.sbNextRound = "Nast\u0119pna runda:";
        pl.themeSelectingTitle = "Wybieranie motywu...";
        pl.afkSubstring = "AFK! Omijanie";
        pl.builderLeftV1 = "wyszed\u0142 z gry";
        pl.builderLeftV2 = "W\u0142a\u015bciciel wyszed\u0142";
        pl.rejoinGuesserPrefix = "Witaj z powrotem! Teraz jest kolej gracza ";
        pl.rejoinGuesserSuffix = ". Musisz zgadywa\u0107 co buduje!";
        pl.rejoinBuilderPrefix = "Witaj ponownie! Budujesz co\u015b zwi\u0105zan\u00e9ho z motywem ";
        pl.rejoinBuilderSuffix = ".";
        pl.countdown1Sec = "Gra rozpocznie si\u0119 za 1 sek.!";
        pl.lobbyJoinSuffix = " dołącza (\\d+/\\d+)!";
        pl.lobbyQuitSuffix = " wychodzi!";
        ALL_PATTERNS.add(pl);

        // ── Japanese ──
        LangPatterns ja = lp("ja");
        ja.roundPrefix = "\u30e9\u30a6\u30f3\u30c9\uff1a";
        ja.builderPrefix = "\u5efa\u7bc9\u5bb6\uff1a ";
        ja.correctGuessSuffix = " \u304c\u6b63\u3057\u3044\u30c6\u30fc\u30de\u3092\u5f53\u3066\u307e\u3057\u305f\uff01";
        ja.themeWasPrefix = "\u304a\u984c\u306f\uff1a";
        ja.themeWasSuffix = "\uff01";
        ja.charsLongPrefix = "\u30c6\u30fc\u30de\u306e\u6587\u5b57\u6570\u306f ";
        ja.charsLongSuffix = " \u3067\u3059";
        ja.winnersKeyword = "\u512a\u52dd\u8005";
        ja.langSelectPrefix = "\u9078\u629e\u3057\u305f\u8a00\u8a9e\uff1a";
        ja.guiTitle = "\u4f5c\u308a\u305f\u3044\u30c6\u30fc\u30de\u3092\u9078\u629e\u3057\u3066\u304f\u3060\u3055\u3044\uff01";
        ja.sbTheme = "\u30c6\u30fc\u30de\uff1a"; ja.sbBuilder = "\u4f5c\u8005\uff1a";
        ja.sbTime = "\u6642\u9593:"; ja.sbStartsIn = "\u958b\u59cb\u307e\u3067";
        ja.sbMode = "\u30e2\u30fc\u30c9\uff1aGuess The Build"; ja.sbNextRound = "\u6b21\u306e\u30e9\u30a6\u30f3\u30c9\uff1a";
        ja.themeSelectingTitle = "\u30c6\u30fc\u30de\u3092\u9078\u629e\u4e2d\u3067\u3059...";
        ja.afkSubstring = "\u7121\u64cd\u4f5c\u72b6\u614b";
        ja.builderLeftV1 = "\u6240\u6709\u8005\u304c\u30b2\u30fc\u30e0\u304b\u3089\u9000\u51fa";
        ja.builderLeftV2 = "\u6240\u6709\u8005\u304c\u9000\u51fa";
        ja.rejoinGuesserPrefix = "\u304a\u304b\u3048\u308a\u306a\u3055\u3044\uff01 \u4eca\u306f ";
        ja.rejoinGuesserSuffix = " \u306e\u756a\u3067\u3059\u3002\u30c6\u30fc\u30de\u3092\u63a8\u6e2c\u3057\u3066\u304f\u3060\u3055\u3044\uff01";
        ja.rejoinBuilderPrefix = "\u304a\u304b\u3048\u308a\u306a\u3055\u3044\uff01 \u3042\u306a\u305f\u306f ";
        ja.rejoinBuilderSuffix = " \u306b\u95a2\u9023\u3059\u308b\u7269\u3092\u9020\u3063\u3066\u3044\u307e\u3059\u3002";
        ja.countdown1Sec = "\u3042\u3068 1 \u79d2\u3067\u30b2\u30fc\u30e0\u304c\u958b\u59cb\u3057\u307e\u3059\uff01";
        ja.lobbyJoinSuffix = " が参加しました (\\d+/\\d+) ！";
        ja.lobbyQuitSuffix = " が退出しました！";
        ALL_PATTERNS.add(ja);

        // ── Pirate ──
        LangPatterns pirate = lp("pirate");
        pirate.roundPrefix = "Round: ";
        pirate.builderPrefix = "Builder: ";
        pirate.correctGuessSuffix = " correctly guessed th' theme!";
        pirate.themeWasPrefix = "Th' theme be: ";
        pirate.themeWasSuffix = "!";
        pirate.charsLongPrefix = "Th' theme be ";
        pirate.charsLongSuffix = " characters long";
        pirate.winnersKeyword = "Winners";
        pirate.langSelectPrefix = "Selected language: ";
        pirate.guiTitle = "Select a theme t' build!";
        pirate.sbTheme = "Theme:"; pirate.sbBuilder = "Builder:"; pirate.sbTime = "Time:"; pirate.sbStartsIn = "Starts In";
        pirate.sbMode = "Mode: Guess The Build"; pirate.sbNextRound = "Next Round:";
        pirate.themeSelectingTitle = "Theme be bein' selected...";
        pirate.afkSubstring = "AFK! Skippin'";
        pirate.builderLeftV1 = "captain has left";
        pirate.builderLeftV2 = "captain left";
        pirate.rejoinGuesserPrefix = "Welcome back! It be ";
        pirate.rejoinGuesserSuffix = "'s turn t' build. Ye 'ave t' guess th' build!";
        pirate.rejoinBuilderPrefix = "Welcome back! Ye be buildin' somethin' relevant t' th' theme ";
        pirate.rejoinBuilderSuffix = ".";
        pirate.countdown1Sec = "Th' voyage begins in 1 second!";
        pirate.lobbyJoinSuffix = " has boarded (\\d+/\\d+)!";
        pirate.lobbyQuitSuffix = " has quit!";
        ALL_PATTERNS.add(pirate);

        // ── Norwegian ──
        LangPatterns no = lp("no");
        no.roundPrefix = "Runde: ";
        no.builderPrefix = "Bygger: ";
        no.correctGuessSuffix = " gjettet riktig tema!";
        no.themeWasPrefix = "Temaet var: ";
        no.themeWasSuffix = "!";
        no.charsLongPrefix = "Temaet er ";
        no.charsLongSuffix = " bokstaver langt";
        no.winnersKeyword = "Vinnere";
        no.langSelectPrefix = "Valgt spr\u00e5k: ";
        no.guiTitle = "Velg et tema som skal bygges etter!";
        no.sbTheme = "Tema:"; no.sbBuilder = "Bygger:"; no.sbTime = "Tid:"; no.sbStartsIn = "Starter om";
        no.sbMode = "Modus: Guess The Build"; no.sbNextRound = "Neste runde:";
        no.themeSelectingTitle = "Temaet er i ferd med \u00e5 bli valgt...";
        no.afkSubstring = "AFK! Hopper over";
        no.builderLeftV1 = "forlatt spillet";
        no.builderLeftV2 = "Eieren har forlatt";
        no.rejoinGuesserPrefix = "Velkommen tilbake! Det er ";
        no.rejoinGuesserSuffix = " sin tur til \u00e5 bygge. Du m\u00e5 gjette hva personen bygger!";
        no.rejoinBuilderPrefix = "Velkommen tilbake! Du bygger noe relevant til temaet ";
        no.rejoinBuilderSuffix = ".";
        no.countdown1Sec = "Spillet starter om 1 sekund!";
        no.lobbyJoinSuffix = " har blitt med (\\d+/\\d+)!";
        no.lobbyQuitSuffix = " har forlatt spillet!";
        ALL_PATTERNS.add(no);

        // ── Swedish ──
        LangPatterns sv = lp("sv");
        sv.roundPrefix = "Omg\u00e5ng: ";
        sv.builderPrefix = "Byggare: ";
        sv.correctGuessSuffix = " gissade r\u00e4tt!";
        sv.themeWasPrefix = "Temat var: ";
        sv.themeWasSuffix = "!";
        sv.charsLongPrefix = "Temat \u00e4r ";
        sv.charsLongSuffix = " bokst\u00e4ver l\u00e5ngt";
        sv.winnersKeyword = "Vinnare";
        sv.langSelectPrefix = "Valt spr\u00e5k: ";
        sv.guiTitle = "V\u00e4lj ett tema att bygga!";
        sv.sbTheme = "Tema:"; sv.sbBuilder = "Byggare:"; sv.sbTime = "Tid:"; sv.sbStartsIn = "Startar om";
        sv.sbMode = "L\u00e4ge: Guess The Build"; sv.sbNextRound = "N\u00e4sta omg\u00e5ng:";
        sv.themeSelectingTitle = "Temat v\u00e4ljs...";
        sv.afkSubstring = "AFK! Hoppar \u00f6ver";
        sv.builderLeftV1 = "Byggaren har l\u00e4mnat spelet";
        sv.builderLeftV2 = "\u00c4garen l\u00e4mnade";
        sv.rejoinGuesserPrefix = "V\u00e4lkommen tillbaka! Det \u00e4r ";
        sv.rejoinGuesserSuffix = "s tur att bygga. Du m\u00e5ste gissa bygget!";
        sv.rejoinBuilderPrefix = "V\u00e4lkommen tillbaka! Du bygger n\u00e5got relevant till temat ";
        sv.rejoinBuilderSuffix = ".";
        sv.countdown1Sec = "Spelet startar om 1 sekund!";
        sv.lobbyJoinSuffix = " anslöt sig (\\d+/\\d+)!";
        sv.lobbyQuitSuffix = " lämnade!";
        ALL_PATTERNS.add(sv);

        // ── Turkish ──
        LangPatterns tr = lp("tr");
        tr.roundPrefix = "Tur: ";
        tr.builderPrefix = "Mimar: ";
        tr.correctGuessSuffix = " temayi do\u011fru tahmin etti!";
        tr.themeWasPrefix = "Tema: ";
        tr.themeWasSuffix = " idi!";
        tr.charsLongPrefix = "Tema ";
        tr.charsLongSuffix = " harf uzunlu\u011funda";
        tr.winnersKeyword = "Kazananlar";
        tr.langSelectPrefix = "Se\u00e7ili dil: ";
        tr.guiTitle = "\u0130n\u015fa etmek i\u00e7in bir tema se\u00e7!";
        tr.sbTheme = "Tema:"; tr.sbBuilder = "Mimar:"; tr.sbTime = "Zaman:";
        tr.sbStartsIn = "Ba\u015fl\u0131yor:";
        tr.sbMode = "Mod: Guess The Build"; tr.sbNextRound = "Sonraki Tur:";
        tr.themeSelectingTitle = "Tema se\u00e7iliyor...";
        tr.afkSubstring = "AFK! Ge\u00e7iliyor";
        tr.builderLeftV1 = "oyundan \u00e7\u0131kt\u0131";
        tr.builderLeftV2 = "Arsa sahibi ayr\u0131ld\u0131";
        tr.rejoinGuesserPrefix = "Tekrar ho\u015fgeldin! \u015eu anda in\u015fa s\u0131ras\u0131 ";
        tr.rejoinGuesserSuffix = " oyuncusunun. Ne yapt\u0131\u011f\u0131n\u0131 tahmin etmen gerek!";
        tr.rejoinBuilderPrefix = "Tekrardan ho\u015fgeldin! Temaya uygun bir\u015feyler in\u015fa ediyorsun ";
        tr.rejoinBuilderSuffix = ".";
        tr.countdown1Sec = "Oyun 1 saniye i\u00e7inde ba\u015fl\u0131yor!";
        tr.lobbyJoinSuffix = " katıldı (\\d+/\\d+)!";
        tr.lobbyQuitSuffix = " çıktı!";
        ALL_PATTERNS.add(tr);

        // ── Danish ──
        LangPatterns da = lp("da");
        da.roundPrefix = "Runde: ";
        da.builderPrefix = "Bygger: ";
        da.correctGuessSuffix = " g\u00e6ttede temaet korrekt!";
        da.themeWasPrefix = "Temaet var: ";
        da.themeWasSuffix = "!";
        da.charsLongPrefix = "Temaet er ";
        da.charsLongSuffix = " bogstaver langt";
        da.winnersKeyword = "Vindere";
        da.langSelectPrefix = "Valgt sprog: ";
        da.guiTitle = "V\u00e6lg et tema, der skal bygges!";
        da.sbTheme = "Tema:"; da.sbBuilder = "Bygger:"; da.sbTime = "Tid:"; da.sbStartsIn = "Starter om";
        da.sbMode = "Tilstand: Guess The Build"; da.sbNextRound = "N\u00e6ste Runde:";
        da.themeSelectingTitle = "Temaet bliver valgt...";
        da.afkSubstring = "AFK! Videre til n\u00e6ste";
        da.builderLeftV1 = "har forladt spillet";
        da.builderLeftV2 = "Ejeren forlod";
        da.rejoinGuesserPrefix = "Velkommen tilbage! Det er ";
        da.rejoinGuesserSuffix = "'s tur til at bygge. Du skal g\u00e6tte bygningen!";
        da.rejoinBuilderPrefix = "Velkommen tilbage! Du bygger noget relevant til temaet ";
        da.rejoinBuilderSuffix = ".";
        da.countdown1Sec = "Spillet starter om 1 sekund!";
        da.lobbyJoinSuffix = " har tilsluttet sig (\\d+/\\d+)!";
        da.lobbyQuitSuffix = " forlod!";
        ALL_PATTERNS.add(da);

        // ── Czech ──
        LangPatterns cs = lp("cs");
        cs.roundPrefix = "Kolo: ";
        cs.builderPrefix = "Stavitel: ";
        cs.correctGuessPrefix = "Hr\u00e1\u010d ";
        cs.correctGuessSuffix = " uhodl t\u00e9ma!";
        cs.themeWasPrefix = "T\u00e9ma bylo: ";
        cs.themeWasSuffix = "!";
        cs.charsLongPrefix = "T\u00e9ma m\u00e1 tento po\u010det p\u00edsmen: ";
        cs.charsLongSuffix = null;
        cs.winnersKeyword = "Stupe\u0148 v\u00edt\u011bz\u016f";
        cs.langSelectPrefix = "Vybran\u00fd jazyk: ";
        cs.guiTitle = "Vyber t\u00e9ma, kter\u00e9 chce\u0161 stav\u011bt!";
        cs.sbTheme = "T\u00e9ma:"; cs.sbBuilder = "Stavitel:"; cs.sbTime = "\u010cas:"; cs.sbStartsIn = "Za\u010d\u00edn\u00e1 za";
        cs.sbMode = "M\u00f3d: Guess The Build"; cs.sbNextRound = "Dal\u0161\u00ed kolo:";
        cs.themeSelectingTitle = "Vyb\u00edr\u00e1 se t\u00e9ma...";
        cs.afkSubstring = "ne\u010dinn\u00fd! P\u0159eskakujeme";
        cs.builderLeftV1 = "opustil hru";
        cs.builderLeftV2 = "Majitel ode\u0161el";
        cs.rejoinGuesserPrefix = "V\u00edtej zp\u011bt! Se stav\u011bn\u00edm je na \u0159ad\u011b ";
        cs.rejoinGuesserSuffix = ". Mus\u00ed\u0161 uhodnout stavbu!";
        cs.rejoinBuilderPrefix = "V\u00edtej zp\u011bt! M\u00e1\u0161 postavit n\u011bco, co souvis\u00ed s t\u00e9matem ";
        cs.rejoinBuilderSuffix = ".";
        cs.countdown1Sec = "Hra za\u010d\u00edn\u00e1 za 1 s!";
        cs.lobbyJoinPrefix = "Hráč ";
        cs.lobbyJoinSuffix = " se připojil (\\d+/\\d+)!";
        cs.lobbyQuitPrefix = "Hráč ";
        cs.lobbyQuitSuffix = " odešel!";
        ALL_PATTERNS.add(cs);

        // ── Finnish ──
        LangPatterns fi = lp("fi");
        fi.roundPrefix = "Kierros: ";
        fi.builderPrefix = "Rakentaja: ";
        fi.correctGuessSuffix = " arvasi oikein!";
        fi.themeWasPrefix = "Aihe oli: ";
        fi.themeWasSuffix = "!";
        fi.charsLongPrefix = "Aihe on ";
        fi.charsLongSuffix = " kirjainta pitk\u00e4";
        fi.winnersKeyword = "Voittajat";
        fi.langSelectPrefix = "Valittu kieli: ";
        fi.guiTitle = "Valitse aihe rakentaaksesi!";
        fi.sbTheme = "Aihe:"; fi.sbBuilder = "Rakentaja:"; fi.sbTime = "Aikav\u00e4li:"; fi.sbStartsIn = "Alkaa ajassa";
        fi.sbMode = "Pelimuoto: Guess The Build"; fi.sbNextRound = "Seuraava kierros:";
        fi.themeSelectingTitle = "Aihetta valitaan...";
        fi.afkSubstring = "AFK! Ohitetaan";
        fi.builderLeftV1 = "poistui pelist\u00e4";
        fi.builderLeftV2 = "Omistaja poistui";
        fi.rejoinGuesserPrefix = "Tervetuloa takaisin! On pelaajan ";
        fi.rejoinGuesserSuffix = " vuoro rakentaa. Arvaa, mit\u00e4 h\u00e4n rakentaa!";
        fi.rejoinBuilderPrefix = "Tervetuloa takaisin! Olet rakentamassa jotain liittyen aiheeseen ";
        fi.rejoinBuilderSuffix = ".";
        fi.countdown1Sec = "Pelin alkuun 1 sekunti!";
        fi.lobbyJoinSuffix = " liittyi (\\d+/\\d+)!";
        fi.lobbyQuitSuffix = " poistui!";
        ALL_PATTERNS.add(fi);

        // ── Chinese Simplified ──
        LangPatterns zhCN = lp("zh_CN");
        zhCN.roundPrefix = "\u56de\u5408\uff1a";
        zhCN.builderPrefix = "\u5efa\u7b51\u5e08\uff1a ";
        zhCN.correctGuessSuffix = "\u6b63\u786e\u5730\u731c\u51fa\u4e86\u4e3b\u9898\uff01";
        zhCN.themeWasPrefix = "\u4e3b\u9898\u662f\uff1a";
        zhCN.themeWasSuffix = "\uff01";
        zhCN.charsLongPrefix = "\u8be5\u4e3b\u9898\u5b57\u6570\u4e3a";
        zhCN.charsLongSuffix = "\u4e2a\u5b57";
        zhCN.winnersKeyword = "\u80dc\u8005";
        zhCN.langSelectPrefix = "\u5df2\u9009\u62e9\u8bed\u8a00\uff1a";
        zhCN.guiTitle = "\u9009\u62e9\u8981\u5efa\u9020\u7684\u4e3b\u9898\uff01";
        zhCN.sbTheme = "\u4e3b\u9898\uff1a"; zhCN.sbBuilder = "\u5efa\u7b51\u5e08\uff1a";
        zhCN.sbTime = "\u65f6\u95f4:"; zhCN.sbStartsIn = "\u5f00\u59cb\u4e8e:";
        zhCN.sbMode = "\u6a21\u5f0f\uff1a\u5efa\u7b51\u731c\u731c\u4e50"; zhCN.sbNextRound = "\u4e0b\u4e00\u8f6e:";
        zhCN.sbTitle = "\u5efa\u7b51\u731c\u731c\u4e50";
        zhCN.themeSelectingTitle = "\u6b63\u5728\u9009\u62e9\u4e3b\u9898\u3002";
        zhCN.afkSubstring = "\u6682\u65f6\u79bb\u5f00\u4e86";
        zhCN.builderLeftV1 = "\u4e3b\u4eba\u79bb\u5f00\u4e86";
        zhCN.builderLeftV2 = "\u4e3b\u4eba\u79bb\u5f00\u4e86";
        zhCN.rejoinGuesserPrefix = "\u6b22\u8fce\u56de\u6765\uff01\u73b0\u5728\u662f";
        zhCN.rejoinGuesserSuffix = "\u7684\u5efa\u7b51\u9636\u6bb5\u3002\u4f60\u9700\u8981\u731c\u6d4b\u8fd9\u4e2a\u5efa\u7b51\uff01";
        zhCN.rejoinBuilderPrefix = "\u6b22\u8fce\u56de\u6765 \uff01\u4f60\u6b63\u5728\u6784\u5efa\u4e0e\u4e3b\u9898";
        zhCN.rejoinBuilderSuffix = "\u6709\u5173\u7684\u4e1c\u897f\u3002";
        zhCN.countdown1Sec = "\u6e38\u620f\u5c06\u57281\u79d2\u540e\u5f00\u59cb\uff01";
        zhCN.lobbyJoinSuffix = "加入了游戏 （\\d+/\\d+）！";
        zhCN.lobbyQuitSuffix = "离开了游戏！";
        ALL_PATTERNS.add(zhCN);

        // ── Chinese Traditional ──
        LangPatterns zhTW = lp("zh_TW");
        zhTW.roundPrefix = "\u56de\u5408\uff1a";
        zhTW.builderPrefix = "\u5efa\u7bc9\u5e2b\uff1a ";
        zhTW.correctGuessSuffix = " \u6b63\u78ba\u731c\u51fa\u4e3b\u984c\u4e86\uff01";
        zhTW.themeWasPrefix = "\u4e3b\u984c\u662f\uff1a";
        zhTW.themeWasSuffix = "\uff01";
        zhTW.charsLongPrefix = "\u4e3b\u984c\u70ba ";
        zhTW.charsLongSuffix = " \u500b\u5b57";
        zhTW.winnersKeyword = "\u512a\u52dd\u8005";
        zhTW.langSelectPrefix = "\u5df2\u9078\u5b9a\u8a9e\u8a00\uff1a";
        zhTW.guiTitle = "\u9078\u64c7\u8981\u5efa\u9020\u7684\u4e3b\u984c\uff01";
        zhTW.sbTheme = "\u4e3b\u984c\uff1a"; zhTW.sbBuilder = "\u5efa\u7bc9\u5e2b\uff1a";
        zhTW.sbTime = "\u6642\u9593:"; zhTW.sbStartsIn = "\u5373\u5c07\u958b\u59cb\u65bc:";
        zhTW.sbMode = "\u6a21\u5f0f\uff1a\u4f60\u84cb\u6211\u731c"; zhTW.sbNextRound = "\u4e0b\u4e00\u56de\uff1a";
        zhTW.sbTitle = "\u4f60\u84cb\u6211\u731c";
        zhTW.themeSelectingTitle = "\u6b63\u5728\u9078\u64c7\u4e3b\u984c\u4e2d......";
        zhTW.afkSubstring = "\u639b\u7db2";
        zhTW.builderLeftV1 = "\u4e3b\u4eba\u96e2\u958b\u904a\u6232\u4e86";
        zhTW.builderLeftV2 = "\u4e3b\u4eba\u5df2\u96e2\u7dda";
        zhTW.rejoinGuesserPrefix = "\u6b61\u8fce\u56de\u4f86\uff01 \u73fe\u5728\u662f ";
        zhTW.rejoinGuesserSuffix = " \u7684\u5efa\u7bc9\u56de\u5408\uff01 \u4f60\u5fc5\u9808\u731c\u51fa\u4e3b\u984c\u4f86\uff01";
        zhTW.rejoinBuilderPrefix = "\u6b61\u8fce\u56de\u4f86\uff01 \u4f60\u6b63\u5728\u5efa\u9020\u8207 ";
        zhTW.rejoinBuilderSuffix = " \u6709\u95dc\u7684\u5167\u5bb9\u3002";
        zhTW.countdown1Sec = "\u904a\u6232\u5c07\u65bc 1 \u79d2\u5f8c\u958b\u59cb\uff01";
        zhTW.lobbyJoinSuffix = " 加入了遊戲（\\d+/\\d+）！";
        zhTW.lobbyQuitSuffix = " 離開了遊戲！";
        ALL_PATTERNS.add(zhTW);

        // ── Romanian ──
        LangPatterns ro = lp("ro");
        ro.roundPrefix = "Rund\u0103: ";
        ro.builderPrefix = "Constructor: ";
        ro.correctGuessSuffix = " a ghicit tema corect!";
        ro.themeWasPrefix = "Tema a fost: ";
        ro.themeWasSuffix = "!";
        ro.charsLongPrefix = "Tema este de ";
        ro.charsLongSuffix = " litere";
        ro.winnersKeyword = "\u00cenving\u0103tori";
        ro.langSelectPrefix = "Limb\u0103 Selectat\u0103: ";
        ro.guiTitle = "Selecteaz\u0103 o tem\u0103!";
        ro.sbTheme = "Tem\u0103:"; ro.sbBuilder = "Constructor:"; ro.sbTime = "Timp:"; ro.sbStartsIn = "\u00cencepe \u00een";
        ro.sbMode = "Mod: Guess The Build"; ro.sbNextRound = "Runda Urm\u0103toare:";
        ro.themeSelectingTitle = "Tema este \u00een curs de selectare...";
        ro.afkSubstring = "AFK! S\u0103rim peste";
        ro.builderLeftV1 = "nu a p\u0103r\u0103sit jocul";
        ro.builderLeftV2 = "De\u021bin\u0103torul a p\u0103r\u0103sit";
        ro.rejoinGuesserPrefix = "Bun venit \u00eenapoi! Este r\u00e2ndul lui ";
        ro.rejoinGuesserSuffix = " s\u0103 construiasc\u0103. Ghice\u015fte-i construc\u021bia!";
        ro.rejoinBuilderPrefix = "Bun venit \u00eenapoi! Construie\u015fti ceva relevant temei ";
        ro.rejoinBuilderSuffix = null;
        ro.countdown1Sec = "Jocul \u00eencepe \u00een 1 secund\u0103!";
        ro.lobbyJoinSuffix = " a intrat (\\d+/\\d+)!";
        ro.lobbyQuitSuffix = " a ieșit!";
        ALL_PATTERNS.add(ro);

        // ── Hungarian ──
        LangPatterns hu = lp("hu");
        hu.roundPrefix = "K\u00f6r: ";
        hu.builderPrefix = "\u00c9p\u00edt\u00e9sz: ";
        hu.correctGuessSuffix = " kital\u00e1lta a t\u00e9m\u00e1t!";
        hu.themeWasPrefix = "A t\u00e9ma a(z) ";
        hu.themeWasSuffix = " volt!";
        hu.charsLongPrefix = "A t\u00e9ma ";
        hu.charsLongSuffix = " bet\u0171b\u0151l \u00e1ll";
        hu.winnersKeyword = "Gy\u0151ztesek";
        hu.langSelectPrefix = "Kiv\u00e1lasztott nyelv: ";
        hu.guiTitle = "V\u00e1laszd ki a t\u00e9m\u00e1t az \u00e9p\u00edt\u00e9shez!";
        hu.sbTheme = "T\u00e9ma:"; hu.sbBuilder = "\u00c9p\u00edt\u00e9sz:"; hu.sbTime = "Id\u0151:"; hu.sbStartsIn = "Indul";
        hu.sbMode = "M\u00f3d: Guess The Build"; hu.sbNextRound = "K\u00f6vetkez\u0151 K\u00f6r:";
        hu.themeSelectingTitle = "A t\u00e9ma kiv\u00e1laszt\u00e1s alatt van...";
        hu.afkSubstring = "AFK! Kihagy\u00e1s";
        hu.builderLeftV1 = "tulajdonosa kil\u00e9pett";
        hu.builderLeftV2 = "tulajdonos kil\u00e9pett";
        hu.rejoinGuesserPrefix = "\u00dcdv\u00f6z\u00f6llek \u00fajra! Most ";
        hu.rejoinGuesserSuffix = " \u00e9p\u00edt. Ki kell tal\u00e1lnod mit \u00e9p\u00edt!";
        hu.rejoinBuilderPrefix = "\u00dcdv\u00f6z\u00f6llek \u00fajra! \u00c9p\u00edts valamit a(z) ";
        hu.rejoinBuilderSuffix = " t\u00e9m\u00e1ra.";
        hu.countdown1Sec = "A j\u00e1t\u00e9k 1 m\u00e1sodperc m\u00falva indul!";
        hu.lobbyJoinSuffix = " csatlakozott (\\d+/\\d+)!";
        hu.lobbyQuitSuffix = " kilépett!";
        ALL_PATTERNS.add(hu);

        // ── Ukrainian ──
        LangPatterns uk = lp("uk");
        uk.roundPrefix = "\u0420\u0430\u0443\u043d\u0434: ";
        uk.builderPrefix = "\u0411\u0443\u0434\u0456\u0432\u0435\u043b\u044c\u043d\u0438\u043a: ";
        uk.correctGuessPrefix = "\u0413\u0440\u0430\u0432\u0435\u0446\u044c ";
        uk.correctGuessSuffix = " \u0432\u0456\u0434\u0433\u0430\u0434\u0430\u0432 \u0442\u0435\u043c\u0443!";
        uk.themeWasPrefix = "\u0422\u0435\u043c\u0430 \u0431\u0443\u043b\u0430: ";
        uk.themeWasSuffix = "!";
        uk.charsLongPrefix = "\u0422\u0435\u043c\u0430 \u043c\u0456\u0441\u0442\u0438\u0442\u044c ";
        uk.charsLongSuffix = " \u0441\u0438\u043c\u0432\u043e\u043b\u0456\u0432";
        uk.winnersKeyword = "\u041f\u0435\u0440\u0435\u043c\u043e\u0436\u0446\u0456";
        uk.langSelectPrefix = "\u0412\u0438\u0431\u0440\u0430\u043d\u043e \u043c\u043e\u0432\u0443: ";
        uk.guiTitle = "\u041e\u0431\u0435\u0440\u0456\u0442\u044c \u0442\u0435\u043c\u0443 \u0434\u043b\u044f \u0431\u0443\u0434\u0456\u0432\u043d\u0438\u0446\u0442\u0432\u0430!";
        uk.sbTheme = "\u0422\u0435\u043c\u0430:"; uk.sbBuilder = "\u0411\u0443\u0434\u0456\u0432\u0435\u043b\u044c\u043d\u0438\u043a:";
        uk.sbTime = "\u0427\u0430\u0441:"; uk.sbStartsIn = "\u041f\u043e\u0447\u043d\u0435\u0442\u044c\u0441\u044f \u0447\u0435\u0440\u0435\u0437";
        uk.sbMode = "\u0420\u0435\u0436\u0438\u043c: Guess The Build"; uk.sbNextRound = "\u041d\u0430\u0441\u0442\u0443\u043f\u043d\u0438\u0439 \u0440\u0430\u0443\u043d\u0434:";
        uk.themeSelectingTitle = "\u041e\u0431\u0438\u0440\u0430\u0454\u0442\u044c\u0441\u044f \u0442\u0435\u043c\u0430...";
        uk.afkSubstring = "\u0431\u0435\u0437\u0434\u0456\u044f\u043b\u044c\u043d\u0438\u0439";
        uk.builderLeftV1 = "\u0437\u0430\u043b\u0438\u0448\u0438\u0432 \u0433\u0440\u0443";
        uk.builderLeftV2 = "\u0411\u0443\u0434\u0456\u0432\u0435\u043b\u044c\u043d\u0438\u043a \u0432\u0438\u0439\u0448\u043e\u0432";
        uk.rejoinGuesserPrefix = "\u0417 \u043f\u043e\u0432\u0435\u0440\u043d\u0435\u043d\u043d\u044f\u043c! \u0417\u0430\u0440\u0430\u0437 \u0431\u0443\u0434\u0443\u0454 ";
        uk.rejoinGuesserSuffix = ". \u0412\u0438 \u043f\u043e\u0432\u0438\u043d\u043d\u0456 \u0432\u0433\u0430\u0434\u0430\u0442\u0438 \u0439\u043e\u0433\u043e \u0441\u043f\u043e\u0440\u0443\u0434\u0443!";
        uk.rejoinBuilderPrefix = "\u0417 \u043f\u043e\u0432\u0435\u0440\u043d\u0435\u043d\u043d\u044f\u043c! \u0417\u0430\u0440\u0430\u0437 \u0432\u0438 \u0431\u0443\u0434\u0443\u0454\u0442\u0435 \u0449\u043e\u0441\u044c, \u043f\u043e\u0432'\u044f\u0437\u0430\u043d\u0435 \u0437 \u0442\u0435\u043c\u043e\u044e: ";
        uk.rejoinBuilderSuffix = ".";
        uk.countdown1Sec = "\u0413\u0440\u0430 \u043f\u043e\u0447\u043d\u0435\u0442\u044c\u0441\u044f \u0447\u0435\u0440\u0435\u0437 1 \u0441\u0435\u043a\u0443\u043d\u0434\u0443!";
        uk.lobbyJoinPrefix = "Гравець ";
        uk.lobbyJoinSuffix = " приєднався (\\d+/\\d+)!";
        uk.lobbyQuitPrefix = "Гравець ";
        uk.lobbyQuitSuffix = " вийшов!";
        ALL_PATTERNS.add(uk);
    }

    // ── Chat Titles (GTB rank titles across all 24 languages) ───────────
    static {
        Collections.addAll(CHAT_TITLES,
            // Rookie
            "Rookie", "\u521d\u6765\u4e4d\u5230", "\u521d\u4f86\u4e4d\u5230",
            "Nov\u00e1\u010dek", "Nybegynder", "Beginner", "Aloittelija",
            "D\u00e9butant", "Anf\u00e4nger", "\u00dajonc", "Novellino",
            "\u65b0\u4eba", "\ud480\ub0b4\uae30", "Nybegynner",
            "Pocz\u0105tkuj\u0105cy", "Novato", "\u00cencep\u0103tor",
            "\u041d\u043e\u0432\u0438\u0447\u043e\u043a", "Nyb\u00f6rjare",
            "\u00c7aylak", "\u041d\u043e\u0432\u0430\u0447\u043e\u043a",
            // Untrained
            "Untrained", "\u672a\u7ecf\u96d5\u7422", "\u6280\u85dd\u751f\u758f",
            "Nezku\u0161en\u00fd", "Utr\u00e6net", "Ongetraind", "Kokematon",
            "Novice", "Untrainiert", "K\u00e9pzetlen", "Non Allenato",
            "\u4e00\u822c\u4eba", "\uc77c\ubc18\uc778", "Utrent", "Niewprawny",
            "Caloiro", "Amador", "Neinstruit",
            "\u041d\u0435\u043e\u0431\u0443\u0447\u0435\u043d\u043d\u044b\u0439",
            "Iniciado", "Otr\u00e4nad", "E\u011fitimsiz",
            "\u041d\u0435\u043d\u0430\u0432\u0447\u0435\u043d\u0438\u0439",
            // Amateur
            "Amateur", "\u521d\u7aa5\u95e8\u5f84", "\u521d\u7aa5\u9580\u5f91",
            "Amat\u00e9r", "Amat\u00f8r", "Amat\u00f6\u00f6ri", "Amat\u0151r",
            "Dilettante", "\u30a2\u30de\u30c1\u30e5\u30a2", "\uc544\ub9c8\ucd94\uc5b4",
            "Amator", "Dedicado", "\u041b\u044e\u0431\u0438\u0442\u0435\u043b\u044c",
            "Aficionado", "Amat\u00f6r",
            // Apprentice
            "Apprentice", "\u5b66\u6709\u6240\u6210", "\u5b78\u5f92",
            "U\u010dedn\u00edk", "L\u00e6rling", "Leerling", "Oppipoika",
            "Apprenti", "Lehrling", "Tanonc", "Apprendista", "\u898b\u7fd2",
            "\uacac\uc2b5", "Praktykant", "Aprendiz", "Ucenic",
            "\u0423\u0447\u0435\u043d\u0438\u043a", "L\u00e4rling", "Acemi",
            "\u0423\u0447\u0435\u043d\u044c",
            // Experienced
            "Experienced", "\u9a7e\u8f7b\u5c31\u719f", "\u99d5\u8f15\u5c31\u719f",
            "Zku\u0161en\u00fd", "\u00d8vet", "Ervaren", "Harjoitellut",
            "Exp\u00e9riment\u00e9", "Erfahren", "Tapasztalt", "Esperto",
            "\u7d4c\u9a13\u8005", "\uacbd\ud5d8\uc790", "Do\u015bwiadczony",
            "Experiente", "Veterano", "Experimentat",
            "\u041e\u043f\u044b\u0442\u043d\u044b\u0439", "Experimentado",
            "Deneyimli", "\u0414\u043e\u0441\u0432\u0456\u0434\u0447\u0435\u043d\u0438\u0439",
            // Seasoned
            "Seasoned", "\u5386\u7ec3\u8001\u6210", "\u8b58\u9014\u8001\u99ac",
            "Ost\u0159\u00edlen\u00fd", "Doorwinterd", "Kokenut", "Saisonnier",
            "Szezon\u00e1lis", "\u719f\u9054\u8005", "\uc219\ub2ec\uc790",
            "Kunnskapsrik", "Zaprawiony", "Gladiador", "Habilidoso", "Sezonier",
            "\u0411\u044b\u0432\u0430\u043b\u044b\u0439", "H\u00e4rdad",
            "Tecr\u00fcbeli", "\u0411\u0443\u0432\u0430\u043b\u0438\u0439",
            // Trained
            "Trained", "\u6280\u827a\u7cbe\u6e5b", "\u6280\u85dd\u7cbe\u6e5b",
            "Zb\u011bhl\u00fd", "Kvalificeret", "Opgeleid", "Treenattu",
            "Entra\u00een\u00e9", "Trainiert", "Edzett", "Allenato",
            "\u30d9\u30c6\u30e9\u30f3", "\ubca0\ud14c\ub791", "Trent",
            "Wyszkolony", "Perspicaz", "Inteligente", "Antrenat",
            "\u041e\u0431\u0443\u0447\u0435\u043d\u043d\u044b\u0439", "Entrenado",
            "Tr\u00e4nad", "E\u011fitilmi\u015f",
            "\u041d\u0430\u0432\u0447\u0435\u043d\u0438\u0439",
            // Skilled
            "Skilled", "\u7089\u706b\u7eaf\u9752", "\u7210\u706b\u7d14\u9752",
            "\u0160ikovn\u00fd", "Kyndig", "Taitava", "Comp\u00e9tent", "Bewandt",
            "\u00dcgyes", "Abile", "\u719f\u7df4\u8005", "\uc219\ub828\uc790",
            "Dyktig", "Uzdolniony", "Craque", "Perito",
            "\u00cendem\u00e2natic", "\u0423\u043c\u0435\u043b\u044b\u0439",
            "Skicklig", "Becerikli", "\u0423\u043c\u0456\u043b\u0438\u0439",
            // Talented
            "Talented", "\u6280\u60ca\u56db\u5ea7", "\u6280\u9a5a\u56db\u5ea7",
            "Talentovan\u00fd", "Talentfuld", "Getalenteerd", "Lahjakas",
            "Talentueux", "Talentiert", "Tehets\u00e9ges", "Talentuoso",
            "\u4eba\u6750", "\uc778\uc7ac", "Talentfull", "Utalentowany",
            "Talentoso", "Talentat",
            "\u0422\u0430\u043b\u0430\u043d\u0442\u043b\u0438\u0432\u044b\u0439",
            "Talangfull", "Yetenekli",
            "\u0422\u0430\u043b\u0430\u043d\u043e\u0432\u0438\u0442\u0438\u0439",
            // Professional
            "Professional", "\u5de7\u593a\u5929\u5de5", "\u5de7\u596a\u5929\u5de5",
            "Profesion\u00e1l", "Professionel", "Vakkundige", "Pro",
            "Professionnel", "Professionell", "Profi", "Professionista",
            "\u8077\u696d\u4eba", "\uc9c1\uc5c5\uc778", "Profesjonell",
            "Zawodowiec", "Profissional", "Profesionist",
            "\u041f\u0440\u043e\u0444\u0435\u0441\u0441\u0438\u043e\u043d\u0430\u043b",
            "Profesional", "Profesyonel",
            "\u041f\u0440\u043e\u0444\u0435\u0441\u0456\u043e\u043d\u0430\u043b",
            // Artisan
            "Artisan",
            // Prospect (English-only additional title)
            "Prospect",
            // Expert
            "Expert", "\u95fb\u540d\u4e8e\u4e16", "\u805e\u540d\u65bc\u4e16",
            "Ekspert", "Ekspertti", "Experte",
            "Szak\u00e9rt\u0151", "\u5c02\u9580\u5bb6", "\uc804\ubb38\uac00",
            "Znawca", "\u042d\u043a\u0441\u043f\u0435\u0440\u0442", "Experto",
            "Uzman", "\u0415\u043a\u0441\u043f\u0435\u0440\u0442",
            // Master
            "Master", "\u5efa\u7b51\u5927\u5e08", "\u5927\u5e2b", "Mistr",
            "Mester", "Meester", "Mestari", "Maitre", "Meister", "Maestro",
            "\u30de\u30b9\u30bf\u30fc", "\ub9c8\uc2a4\ud130", "Mistrz", "Mestre",
            "Maestru", "\u041c\u0430\u0441\u0442\u0435\u0440",
            "M\u00e4stare", "Usta", "\u041c\u0430\u0439\u0441\u0442\u0435\u0440",
            // Legend
            "Legend", "\u4f20\u8bf4\u661f\u8000", "\u50b3\u8aaa", "Legenda",
            "Legende", "L\u00e9gende", "Leggenda", "\u30ec\u30b8\u30a7\u30f3\u30c9",
            "\ub808\uc804\ub4dc", "Lenda", "Legend\u0103",
            "\u041b\u0435\u0433\u0435\u043d\u0434\u0430", "Leyenda", "Efsane",
            // Grandmaster
            "Grandmaster", "\u6700\u5f3a\u738b\u8005", "\u4e00\u4ee3\u5b97\u5e2b",
            "Velmistr", "Stormester", "Grootmeester", "Suurmestari",
            "Grand maitre", "Gro\u00dfmeister", "Nagymester", "Gran Maestro",
            "\u30b0\u30e9\u30f3\u30c9\u30de\u30b9\u30bf\u30fc",
            "\uadf8\ub79c\ub4dc\ub9c8\uc2a4\ud130", "Arcymistrz", "Supremo",
            "Mare Maestru",
            "\u0413\u0440\u043e\u0441\u0441\u043c\u0435\u0439\u0441\u0442\u0435\u0440",
            "Storm\u00e4stare", "\u00dcstat",
            "\u0413\u0440\u043e\u0441\u043c\u0435\u0439\u0441\u0442\u0435\u0440",
            // Celestial
            "Celestial",
            // Divine
            "Divine", "\u8d85\u51e1\u5165\u5723", "\u51fa\u795e\u5165\u5316",
            "Bo\u017esk\u00e1", "Guddommelig", "Goddelijk", "Jumalallinen",
            "G\u00f6ttlich", "Isteni", "Divino", "\ucd5c\uc0c1", "Boska",
            "Divina", "Divin",
            "\u0411\u043e\u0436\u0435\u0441\u0442\u0432\u0435\u043d\u043d\u044b\u0439",
            "Underbart", "\u0130lahi",
            "\u0411\u043e\u0436\u0435\u0441\u0442\u0432\u0435\u043d\u043d\u0438\u0439",
            // Ascended
            "Ascended", "\u767b\u5cf0\u9020\u6781", "\u81f3\u9ad8\u7121\u4e0a",
            "Nanebevzet\u00ed", "Alm\u00e6gtig", "Gerezen", "Noussut",
            "Ascendu", "Aufgestiegen", "Felemelkedett", "Asceso", "\u6607\u5929",
            "\uc2b9\ucc9c", "Oppsteget", "Wst\u0105pi\u0142", "Ascendente",
            "\u0412\u043e\u0437\u043d\u0435\u0441\u0451\u043d\u043d\u044b\u0439",
            "Ascendido", "Uppsteg", "Y\u00fckselmi\u015f",
            "\u0412\u043e\u0437\u043d\u0435\u0441\u0435\u043d\u0438\u0439"
        );
        retainActiveBbTitles();
    }

    // ── Query Methods ───────────────────────────────────────────────────

    /** Strip supplementary-plane characters (emoji surrogate pairs). */
    public static String stripEmojis(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isHighSurrogate(c) && i + 1 < s.length()
                    && Character.isLowSurrogate(s.charAt(i + 1))) {
                i++; // skip surrogate pair
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Check if clean text starts with a known GTB chat title (rank title).
     * Format: "&lt;EMBLEM?&gt; &lt;TITLE&gt; IGN: TEXT"
     */
    static boolean startsWithChatTitle(String cleanText) {
        cleanText = cleanText.trim();
        cleanText = stripLeadingRankPrefixes(cleanText);

        int start = 0;
        while (start < cleanText.length()) {
            char c = cleanText.charAt(start);
            if (Character.isLetterOrDigit(c) || c == '#') break;
            start++;
        }
        if (start > 0) cleanText = cleanText.substring(start).trim();
        cleanText = stripLeadingRankPrefixes(cleanText);

        int colonIdx = cleanText.indexOf(": ");
        if (colonIdx < 0) return false;

        String beforeColon = cleanText.substring(0, colonIdx).trim();
        String[] tokens = beforeColon.split("\\s+");
        if (tokens.length < 2) return false;  // need at least title + IGN

        // Check each non-last token against CHAT_TITLES (rank prefix may be interleaved)
        for (int i = 0; i < tokens.length - 1; i++) {
            String token = stripLeadingRankPrefixes(tokens[i]);
            if (CHAT_TITLES.contains(token)) return true;
        }

        // Leaderboard title check (e.g. "#1 Builder <ign>" or
        // "Builder #1 [MVP++] <ign>").
        if (startsWithLeaderboardChatTitle(beforeColon)) return true;

        int lastSpace = beforeColon.lastIndexOf(' ');
        if (lastSpace > 0) {
            String leaderboardPart = beforeColon.substring(0, lastSpace).trim();
            leaderboardPart = stripLeadingRankPrefixes(leaderboardPart);
            if (LEADERBOARD_TITLE_PATTERN.matcher(leaderboardPart).matches()) return true;
        }
        return false;
    }

    /**
     * Remove the localized prefix Hypixel adds to chat sent after a player has
     * already guessed correctly. Returns null for ordinary chat.
     */
    public static String stripGuesserChatPrefix(String cleanText) {
        if (cleanText == null) return null;
        String clean = stripEmojis(cleanText).trim();
        for (String prefix : GUESSER_CHAT_PREFIXES) {
            if (!clean.startsWith(prefix)) continue;
            int end = prefix.length();
            if (end < clean.length() && !Character.isWhitespace(clean.charAt(end))) {
                continue;
            }
            return clean.substring(end).trim();
        }
        return null;
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

    /** Complete a possibly-truncated GTB rank title observed in the tab list.
     * Hypixel can truncate titles longer than 11 visible chars there. */
    public static String completeChatTitle(String title) {
        return completeChatTitle(title, null);
    }

    public static String completeChatTitle(String title, String langCode) {
        if (title == null) return null;
        String trimmed = title.trim();
        if (trimmed.isEmpty()) return trimmed;
        if (CHAT_TITLES.contains(trimmed)) return trimmed;
        String lower = trimmed.toLowerCase(Locale.ROOT);
        String preferred = matchPreferredChatTitle(lower, langCode);
        if (preferred != null) return preferred;
        String best = null;
        int matches = 0;
        for (String candidate : CHAT_TITLES) {
            if (candidate == null) continue;
            if (!candidate.toLowerCase(Locale.ROOT).startsWith(lower)) continue;
            matches++;
            if (best == null || candidate.length() < best.length()) {
                best = candidate;
            }
        }
        return matches == 1 ? best : trimmed;
    }

    /** Map a localized or truncated Build Battle rank title to its canonical rank index. */
    public static Integer bbRankIndexForTitle(String title, String langCode) {
        if (title == null) return null;
        String trimmed = title.trim();
        if (trimmed.isEmpty()) return null;
        String lower = trimmed.toLowerCase(Locale.ROOT);

        Integer preferred = matchPreferredChatTitleIndex(lower, langCode);
        if (preferred != null) return preferred;

        Set<Integer> exact = new HashSet<Integer>();
        Set<Integer> prefix = new HashSet<Integer>();
        for (LangPatterns lp : ALL_PATTERNS) {
            if (lp == null || lp.lang == null) continue;
            collectMatchingTitleIndexes(lower, lp.lang, exact, prefix);
        }
        collectMatchingTitleIndexes(lower, "en", exact, prefix);
        if (exact.size() == 1) return exact.iterator().next();
        if (!exact.isEmpty()) return null;
        return prefix.size() == 1 ? prefix.iterator().next() : null;
    }

    /** Return the localized Build Battle title for the tab team-name rank index. */
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

    /** Parse localized top-builder BB titles like "#1 Builder", "1. Builder",
     *  "Builder #1", or "Builder nr 1" into the synthetic rank index. */
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

    /** Parse a leaderboard rank marker from a packet prefix/suffix fragment.
     *  This handles tab truncation where the builder word is missing but the
     *  useful "#7", "7.", or "№7" marker is still present. */
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
        return ACTIVE_BB_TITLE_COUNT + rank - 1;
    }

    public static Integer bbLeaderboardPositionForRankIndex(int rankIndex) {
        if (rankIndex < ACTIVE_BB_TITLE_COUNT) return null;
        int rank = rankIndex - ACTIVE_BB_TITLE_COUNT + 1;
        return rank >= 1 && rank <= 10 ? Integer.valueOf(rank) : null;
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
            for (LangPatterns lp : ALL_PATTERNS) {
                if (lp.lang == null || !lp.lang.equalsIgnoreCase(langCode)) continue;
                String label = stripLabelPunctuation(lp.sbBuilder);
                if (label != null && !label.isEmpty()) return label;
            }
        }
        return "Builder";
    }

    public static String scoreboardTitle(String langCode) {
        LangPatterns lp = patternForLang(langCode);
        return lp != null && lp.sbTitle != null ? lp.sbTitle : "GUESS THE BUILD";
    }

    public static String scoreboardBuilderLabel(String langCode) {
        LangPatterns lp = patternForLang(langCode);
        return lp != null && lp.sbBuilder != null ? lp.sbBuilder : "Builder:";
    }

    public static String scoreboardTimeLabel(String langCode) {
        LangPatterns lp = patternForLang(langCode);
        return lp != null && lp.sbTime != null ? lp.sbTime : "Time:";
    }

    public static String scoreboardThemeLabel(String langCode) {
        LangPatterns lp = patternForLang(langCode);
        return lp != null && lp.sbTheme != null ? lp.sbTheme : "Theme:";
    }

    public static String actionbarThemeRevealPrefix(String langCode) {
        return themeRevealPatternForLang(langCode).prefix;
    }

    private static LangPatterns patternForLang(String langCode) {
        String requested = langCode == null || langCode.trim().isEmpty()
                ? "en" : langCode.trim();
        LangPatterns fallback = null;
        for (LangPatterns lp : ALL_PATTERNS) {
            if (lp.lang == null) continue;
            if ("en".equalsIgnoreCase(lp.lang)) fallback = lp;
            if (lp.lang.equalsIgnoreCase(requested)) return lp;
        }
        return fallback;
    }

    private static String stripLabelPunctuation(String label) {
        if (label == null) return null;
        String out = label.trim();
        while (!out.isEmpty()) {
            char c = out.charAt(out.length() - 1);
            if (c == ':' || c == '\uff1a') {
                out = out.substring(0, out.length() - 1).trim();
            } else {
                break;
            }
        }
        return out;
    }

    /** Return the Build Battle title color for the tab team-name rank index. */
    public static String bbColorForRankIndex(int rankIndex) {
        if (rankIndex >= ACTIVE_BB_TITLE_COUNT
                && rankIndex < ACTIVE_BB_TITLE_COUNT + 10) return "\u00a7e\u00a7l";
        return rankIndex >= 0 && rankIndex < ACTIVE_BB_TITLE_COUNT
                ? BB_TITLE_COLORS[rankIndex] : "\u00a7f";
    }

    private static String matchPreferredChatTitle(String lowerTitle, String langCode) {
        String[] titles = preferredChatTitles(langCode);
        if (titles == null) return null;
        for (int i = 0; i < titles.length && i < ACTIVE_BB_TITLE_COUNT; i++) {
            String title = titles[i];
            if (title == null) continue;
            if (title.toLowerCase(Locale.ROOT).startsWith(lowerTitle)) return title;
        }
        return null;
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
        String code = langCode.toLowerCase(Locale.ROOT);
        String[] cached = PREFERRED_CHAT_TITLES.get(code);
        if (cached != null) return cached;
        String[] created = createPreferredChatTitles(code);
        if (created != null) PREFERRED_CHAT_TITLES.put(code, created);
        return created;
    }

    private static String[] createPreferredChatTitles(String code) {
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
        preferredChatTitleArrayBuildCount++;
        String[] out = Arrays.copyOf(EN_BB_TITLES, EN_BB_TITLES.length);
        for (int i = 0; i < titles.length && i < LEGACY_TO_CURRENT_BB_TITLE_INDEX.length; i++) {
            int target = LEGACY_TO_CURRENT_BB_TITLE_INDEX[i];
            if (target >= 17) continue;
            if (titles[i] != null && !titles[i].isEmpty()) out[target] = titles[i];
        }
        return out;
    }

    static int preferredChatTitleArrayBuildCountForTest() {
        return preferredChatTitleArrayBuildCount;
    }

    private static void retainActiveBbTitles() {
        Set<String> active = new HashSet<String>();
        for (LangPatterns lp : ALL_PATTERNS) {
            if (lp == null || lp.lang == null) continue;
            String[] titles = preferredChatTitles(lp.lang);
            if (titles == null) continue;
            for (int i = 0; i < titles.length && i < ACTIVE_BB_TITLE_COUNT; i++) {
                if (titles[i] != null) active.add(titles[i]);
            }
        }
        Collections.addAll(active, EN_BB_TITLES);
        CHAT_TITLES.retainAll(active);
    }

    /** Strip a leading Build Battle title only when another token remains for the IGN. */
    public static String stripLeadingBbTitle(String s) {
        if (s == null) return null;
        String clean = normalizeLeadingBbTitleCandidate(s);

        String bestTitle = leadingBbTitle(clean, true);
        if (bestTitle == null) return clean;
        return clean.substring(bestTitle.length()).trim();
    }

    public static boolean hasLeadingBbTitle(String s) {
        if (s == null) return false;
        return leadingBbTitle(normalizeLeadingBbTitleCandidate(s), false) != null;
    }

    private static String normalizeLeadingBbTitleCandidate(String s) {
        String clean = stripLeadingRankPrefixes(s.trim());
        int start = 0;
        while (start < clean.length()) {
            char c = clean.charAt(start);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '[' || c == '#') break;
            start++;
        }
        if (start > 0) clean = stripLeadingRankPrefixes(clean.substring(start).trim());
        return clean;
    }

    private static String leadingBbTitle(String clean, boolean requireFollowingToken) {
        String bestTitle = null;
        for (LangPatterns lp : ALL_PATTERNS) {
            if (lp == null || lp.lang == null) continue;
            String[] titles = preferredChatTitles(lp.lang);
            if (titles == null) continue;
            for (String title : titles) {
                if (!isTitlePrefix(clean, title, requireFollowingToken)) continue;
                if (bestTitle == null || title.length() > bestTitle.length()) {
                    bestTitle = title;
                }
            }
        }
        return bestTitle;
    }

    private static boolean isTitlePrefix(String text, String title, boolean requireFollowingToken) {
        if (text == null || title == null || title.isEmpty()) return false;
        if (text.length() < title.length()) return false;
        if (!text.regionMatches(true, 0, title, 0, title.length())) return false;
        if (text.length() == title.length()) return !requireFollowingToken;
        return Character.isWhitespace(text.charAt(title.length()));
    }

    /** Strip leading bracketed rank prefixes like "[VIP]", "[MVP+]", "[MVP++]" (repeatedly). */
    private static String stripLeadingRankPrefixes(String s) {
        while (s.startsWith("[")) {
            int closeIdx = s.indexOf(']');
            if (closeIdx < 0) break;
            s = s.substring(closeIdx + 1).trim();
        }
        return s;
    }

    /** Check if the scoreboard title matches any GTB title variant. */
    static boolean isGtbTitle(String titleClean) {
        if (titleClean == null) return false;
        String stripped = stripEmojis(titleClean).trim();
        return stripped.contains("GUESS THE BUILD")
                || stripped.contains("\u5efa\u7b51\u731c\u731c\u4e50")
                || stripped.contains("\u4f60\u84cb\u6211\u731c");
    }

    /** Check if a GUI title matches any language's theme selection GUI title. */
    static boolean isThemeSelectionGui(String title) {
        if (title == null) return false;
        for (LangPatterns lp : ALL_PATTERNS) {
            if (lp.guiTitle != null && title.equals(lp.guiTitle)) return true;
        }
        return false;
    }

    /**
     * Check if a scoreboard line is a known label (not a player score).
     * Matches Theme:/Builder:/Time:/Starts In: and their translations.
     */
    static boolean isScoreboardLabel(String cleanLine) {
        for (LangPatterns lp : ALL_PATTERNS) {
            if (lp.sbTheme != null && cleanLine.startsWith(lp.sbTheme)) return true;
            if (lp.sbBuilder != null && cleanLine.startsWith(lp.sbBuilder)) return true;
            if (lp.sbTime != null && cleanLine.startsWith(lp.sbTime)) return true;
            if (startsWithRoundEndLabel(cleanLine, lp.sbNextRound)) return true;
            if (lp.sbMode != null && cleanLine.equals(lp.sbMode)) return true;
            if (lp.sbStartsIn != null) {
                if (lp.sbStartsInIsSuffix) {
                    if (cleanLine.contains(lp.sbStartsIn)) return true;
                } else {
                    if (cleanLine.startsWith(lp.sbStartsIn)) return true;
                }
            }
        }
        return false;
    }

    /** Round-end labels are compared through Locale.ROOT lowercase forms. */
    static boolean startsWithRoundEndLabel(String line, String label) {
        return line != null && label != null
                && line.toLowerCase(Locale.ROOT).startsWith(label.toLowerCase(Locale.ROOT));
    }

    public static boolean isScoreboardBuilderLabel(String cleanLine) {
        if (cleanLine == null) return false;
        for (LangPatterns lp : ALL_PATTERNS) {
            if (lp.sbBuilder != null && cleanLine.startsWith(lp.sbBuilder)) return true;
        }
        return false;
    }

    /** Detect the game language from scoreboard patterns. */
    static String detectLanguageFromScoreboard(List<String> cleanLines) {
        for (LangPatterns lp : ALL_PATTERNS) {
            if (lp.sbStartsIn != null) {
                for (String line : cleanLines) {
                    if (lp.sbStartsInIsSuffix) {
                        if (line.contains(lp.sbStartsIn)) return lp.lang;
                    } else {
                        if (line.startsWith(lp.sbStartsIn)) return lp.lang;
                    }
                }
            }
        }
        for (LangPatterns lp : ALL_PATTERNS) {
            if (lp.sbTime != null) {
                for (String line : cleanLines) {
                    if (line.startsWith(lp.sbTime)) return lp.lang;
                }
            }
        }
        for (LangPatterns lp : ALL_PATTERNS) {
            if (lp.sbNextRound != null) {
                for (String line : cleanLines) {
                    if (startsWithRoundEndLabel(line, lp.sbNextRound)) return lp.lang;
                }
            }
        }
        return null;
    }

    /**
     * Derive game state from scoreboard content.
     * Checks lines for known labels to determine which phase we're in.
     */
    static GtbGame.State getStateFromScoreboard(List<String> cleanLines, boolean scoreboardShowsGtb) {
        if (!scoreboardShowsGtb || cleanLines.isEmpty()) return GtbGame.State.NONE;

        for (LangPatterns lp : ALL_PATTERNS) {
            if (lp.sbMode != null) {
                for (String line : cleanLines) {
                    if (line.equals(lp.sbMode)) return GtbGame.State.LOBBY;
                }
            }
        }

        // Round-phase checks come BEFORE the "1. " POST_GAME check because the
        // in-game sidebar also has "1. IGN score" leaderboard lines.  During a
        // real round these coexist with a phase label (Time:/Starts In:/Next
        // Round:); during POST_GAME none of those labels are present.
        for (LangPatterns lp : ALL_PATTERNS) {
            if (lp.sbStartsIn != null) {
                for (String line : cleanLines) {
                    if (lp.sbStartsInIsSuffix) {
                        if (line.contains(lp.sbStartsIn)) return GtbGame.State.ROUND_PRE;
                    } else {
                        if (line.startsWith(lp.sbStartsIn)) return GtbGame.State.ROUND_PRE;
                    }
                }
            }
        }

        for (LangPatterns lp : ALL_PATTERNS) {
            if (lp.sbNextRound != null) {
                for (String line : cleanLines) {
                    if (startsWithRoundEndLabel(line, lp.sbNextRound)) return GtbGame.State.ROUND_END;
                }
            }
        }

        for (LangPatterns lp : ALL_PATTERNS) {
            if (lp.sbTime != null) {
                for (String line : cleanLines) {
                    if (line.startsWith(lp.sbTime)) return GtbGame.State.ROUND_BUILD;
                }
            }
        }

        // POST_GAME is recognised solely by the presence of "1. " leaderboard
        // lines when no round-phase label coexists.  This must run last.
        for (String line : cleanLines) {
            if (line.startsWith("1. ")) return GtbGame.State.POST_GAME;
        }

        return GtbGame.State.NONE;
    }

    public static List<String> languageCodes() {
        List<String> codes = new ArrayList<>();
        for (LangPatterns lp : ALL_PATTERNS) {
            if (lp.lang != null) codes.add(lp.lang);
        }
        return Collections.unmodifiableList(codes);
    }

    /** Map detected language names (native or ISO code) to English with proper capitalization. */
    public static String mapLanguageName(String lang) {
        if (lang == null) return null;
        switch (lang.toLowerCase()) {
            // Native names
            case "english": return "English";
            case "\u010de\u0161tina": case "cestina": return "Czech";
            case "dansk": return "Danish";
            case "deutsch": return "German";
            case "espa\u00f1ol": case "espanol": return "Spanish";
            case "fran\u00e7ais": case "francais": return "French";
            case "italiano": return "Italian";
            case "magyar": return "Hungarian";
            case "nederlands": return "Dutch";
            case "norsk": return "Norwegian";
            case "polski": return "Polish";
            case "portugu\u00eas": case "portugues": return "Portuguese";
            case "portugu\u00eas (br)": case "portugues (br)": return "Portuguese (BR)";
            case "rom\u00e2n\u0103": case "romana": return "Romanian";
            case "suomi": return "Finnish";
            case "svenska": return "Swedish";
            case "t\u00fcrk\u00e7e": case "turkce": return "Turkish";
            case "pirate speak": return "Pirate Speak";
            case "\u0440\u0443\u0441\u0441\u043a\u0438\u0439": return "Russian";
            case "\u0443\u043a\u0440\u0430\u0457\u043d\u0441\u044c\u043a\u0430": return "Ukrainian";
            case "\ud55c\uad6d\uc5b4": return "Korean";
            case "\u65e5\u672c\u8a9e": return "Japanese";
            case "\u7b80\u4f53\u4e2d\u6587": return "Chinese (Simplified)";
            case "\u7e41\u9ad4\u4e2d\u6587": return "Chinese (Traditional)";
            // ISO codes (from detectLanguageFromScoreboard)
            case "en": return "English";
            case "fr": return "French";
            case "de": return "German";
            case "it": return "Italian";
            case "nl": return "Dutch";
            case "es": return "Spanish";
            case "pt_br": return "Portuguese (BR)";
            case "pt": return "Portuguese";
            case "ru": return "Russian";
            case "ko": return "Korean";
            case "pl": return "Polish";
            case "ja": return "Japanese";
            case "pirate": return "Pirate Speak";
            case "no": return "Norwegian";
            case "sv": return "Swedish";
            case "tr": return "Turkish";
            case "da": return "Danish";
            case "cs": return "Czech";
            case "fi": return "Finnish";
            case "zh_cn": return "Chinese (Simplified)";
            case "zh_tw": return "Chinese (Traditional)";
            case "ro": return "Romanian";
            case "hu": return "Hungarian";
            case "uk": return "Ukrainian";
            default: return lang;
        }
    }

    /**
     * Reverse of mapLanguageName: given a display name (e.g. "Pirate Speak"),
     * return the matching language code (e.g. "pirate"). Used after parsing
     * the "Selected language: X" chat message because several languages share
     * the same langSelectPrefix and the displayed name disambiguates.
     */
    static String codeFromDisplayName(String displayName) {
        if (displayName == null) return null;
        for (LangPatterns lp : ALL_PATTERNS) {
            if (lp.lang == null) continue;
            String dn = mapLanguageName(lp.lang);
            if (dn != null && dn.equalsIgnoreCase(displayName)) {
                return lp.lang;
            }
        }
        // Try the native-name fallback (e.g. "Pirate Speak" → mapLanguageName lookup)
        String mapped = mapLanguageName(displayName);
        if (mapped != null && !mapped.equals(displayName)) {
            for (LangPatterns lp : ALL_PATTERNS) {
                if (lp.lang == null) continue;
                String dn = mapLanguageName(lp.lang);
                if (dn != null && dn.equalsIgnoreCase(mapped)) {
                    return lp.lang;
                }
            }
        }
        return null;
    }
}
