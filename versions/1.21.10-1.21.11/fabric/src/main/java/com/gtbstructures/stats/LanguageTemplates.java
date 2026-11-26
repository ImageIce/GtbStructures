package com.gtbstructures.stats;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Compiles approved mirror strings into literal parser aliases. */
final class LanguageTemplates {
    private static final Pattern TOKEN = Pattern.compile("%%([a-zA-Z_]+)%%");
    private static final Set<String> FORMATTING = new HashSet<>(Arrays.asList(
            "black", "dark_blue", "dark_green", "dark_aqua", "dark_red",
            "dark_purple", "gold", "gray", "dark_gray", "blue", "green",
            "aqua", "red", "light_purple", "yellow", "white", "reset", "bold",
            "italic", "underline", "underlined", "strikethrough", "obfuscated", "geen"));
    private static final String[] LOBBY_KEYS = {
            "hypixel.lobby_join", "hypixel.lobby_join_halloween",
            "hypixel.lobby_join_sled", "hypixel.lobby_join_slid"
    };

    final List<LangPatterns> patterns;
    final Map<String, LangPatterns.ThemeRevealPattern> reveals = new LinkedHashMap<>();
    final List<LangPatterns.ThemeRevealPattern> revealPatterns;
    final Set<String> chatTitles;
    final Set<String> rankTitleAliases;
    final Set<String> scoreboardTitles = new HashSet<>();
    final Set<String> guesserPrefixes = new LinkedHashSet<>();
    final Set<String> alreadyGuessed = new HashSet<>();
    final List<Pattern> selfGuessPoints = new ArrayList<>();
    final List<Pattern> transfers = new ArrayList<>();
    final List<Pattern> lobbyProtection = new ArrayList<>();
    final Map<String, List<Pattern>> lobbyJoins = new LinkedHashMap<>();
    final Map<String, String[]> rankTitles = new HashMap<>();
    final List<Pattern> leaderboardRanks = new ArrayList<>();

    private LanguageTemplates(List<LangPatterns> patterns, List<LangPatterns.ThemeRevealPattern> reveals,
                              Set<String> titles, Set<String> aliases) {
        this.patterns = Collections.unmodifiableList(patterns);
        this.revealPatterns = Collections.unmodifiableList(reveals);
        this.chatTitles = Collections.unmodifiableSet(titles);
        this.rankTitleAliases = Collections.unmodifiableSet(aliases);
    }

    static LanguageTemplates compile(String json, List<LangPatterns> builtins, String[] rankNames,
                                     List<LangPatterns.ThemeRevealPattern> builtinReveals, Set<String> builtinTitles) {
        JsonObject data = new JsonParser().parse(json).getAsJsonObject();
        if (literal(data, "gtb.builder_announcement", "en") == null
                || parts(data, "gtb.correct_guess", "en", "player") == null
                || parts(data, "hypixel.lobby_join", "en", "player") == null) {
            throw new IllegalArgumentException("Missing core GTB language templates");
        }
        List<LangPatterns> patterns = new ArrayList<>(builtins);
        List<LangPatterns.ThemeRevealPattern> reveals = new ArrayList<>(builtinReveals);
        Set<String> titles = new HashSet<>(builtinTitles);
        Set<String> aliases = new HashSet<>();
        LanguageTemplates compiled = new LanguageTemplates(patterns, reveals, titles, aliases);
        for (LangPatterns base : builtins) {
            String code = base.lang.toLowerCase(Locale.ROOT);
            LangPatterns chat = new LangPatterns();
            chat.lang = base.lang;
            chat.guiTitle = literal(data, "gtb.select_theme_gui", code);
            chat.builderPrefix = append(literal(data, "gtb.builder_announcement", code), " ");
            chat.roundPrefix = roundPrefix(data, code);
            chat.winnersKeyword = literal(data, "gtb.winners", code);
            chat.themeSelectingTitle = literal(data, "gtb.theme_selecting_title", code);
            chat.afkSubstring = literal(data, "gtb.builder_afk", code);
            chat.builderLeftV1 = literal(data, "gtb.builder_left_game", code);
            chat.builderLeftV2 = literal(data, "gtb.builder_left_before_round", code);
            chat.sbTitle = literal(data, "gtb.scoreboard_title", code);
            addLiteral(compiled.scoreboardTitles, data, "gtb.scoreboard_title", code);
            addLiteral(compiled.scoreboardTitles, data, "gtb.scoreboard_title_yellow", code);
            addLiteral(compiled.scoreboardTitles, data, "gtb.scoreboard_title_gold", code);
            chat.sbBuilder = literal(data, "gtb.scoreboard_builder", code);
            chat.sbTheme = literal(data, "gtb.scoreboard_theme", code);
            chat.sbNextRound = appendLabelColon(literal(data, "gtb.scoreboard_next_round", code), base.sbNextRound);

            String[] split = parts(data, "gtb.scoreboard_mode", code, "mode");
            String mode = literal(data, "gtb.scoreboard_mode_name", code);
            if (split != null && mode != null) chat.sbMode = split[0] + mode + split[1];
            split = parts(data, "gtb.scoreboard_time", code, "time");
            if (split != null && !split[0].isEmpty() && split[1].isEmpty()) chat.sbTime = split[0].trim();
            split = parts(data, "gtb.scoreboard_starts_in_with_time", code, "time");
            if (split != null && (split[0].isEmpty() || split[1].isEmpty())) {
                chat.sbStartsInIsSuffix = split[0].isEmpty();
                chat.sbStartsIn = split[chat.sbStartsInIsSuffix ? 1 : 0];
            }
            split = parts(data, "gtb.correct_guess", code, "player");
            if (split != null && !split[1].isEmpty()) {
                chat.correctGuessPrefix = split[0];
                chat.correctGuessSuffix = split[1];
            }
            split = parts(data, "gtb.theme_reveal_chat", code, "theme");
            if (split != null && !split[0].isEmpty()) {
                chat.themeWasPrefix = split[0];
                chat.themeWasSuffix = split[1];
            }
            split = parts(data, "gtb.theme_length", code, "size");
            if (split != null && !split[0].isEmpty()) {
                chat.charsLongPrefix = split[0];
                chat.charsLongSuffix = split[1];
            }
            split = parts(data, "hypixel.language_selected", code, "language");
            if (split != null && !split[0].isEmpty()) {
                chat.langSelectPrefix = split[0];
                chat.langSelectSuffix = split[1];
            }
            split = parts(data, "gtb.rejoin_guesser", code, "player");
            if (split != null && !split[0].isEmpty() && !split[1].isEmpty()) {
                chat.rejoinGuesserPrefix = split[0];
                chat.rejoinGuesserSuffix = split[1];
            }
            split = parts(data, "gtb.rejoin_builder", code, "plot");
            if (split != null && !split[0].isEmpty()) {
                chat.rejoinBuilderPrefix = split[0];
                chat.rejoinBuilderSuffix = split[1];
            }
            split = parts(data, "hypixel.countdown_second", code, "time");
            if (split != null) chat.countdown1Sec = split[0] + "1" + split[1];
            split = parts(data, "hypixel.player_joined_game", code, "player", "count");
            if (split != null && !split[1].isEmpty()) {
                chat.lobbyJoinPrefix = split[0];
                chat.lobbyJoinSuffix = split[1] + "\\d+/\\d+" + split[2];
            }
            split = parts(data, "hypixel.player_quit_game", code, "player");
            if (split != null && !split[1].isEmpty()) {
                chat.lobbyQuitPrefix = split[0];
                chat.lobbyQuitSuffix = split[1];
            }
            patterns.add(chat);

            LangPatterns alternate = new LangPatterns();
            alternate.lang = base.lang;
            alternate.winnersKeyword = literal(data, "gtb.winners_heading", code);
            alternate.themeSelectingTitle = literal(data, "gtb.theme_selecting_title_legacy", code);
            alternate.sbBuilder = literal(data, "gtb.scoreboard_builder_gray", code);
            split = parts(data, "gtb.theme_length_letters", code, "size");
            if (split != null && !split[0].isEmpty()) {
                alternate.charsLongPrefix = split[0];
                alternate.charsLongSuffix = split[1];
            }
            split = parts(data, "hypixel.language_selected_by_name", code, "name");
            if (split != null && !split[0].isEmpty()) {
                alternate.langSelectPrefix = split[0];
                alternate.langSelectSuffix = split[1];
            }
            patterns.add(alternate);

            split = parts(data, "gtb.theme_reveal_actionbar", code, "theme");
            if (split != null && !split[0].isEmpty()) {
                LangPatterns.ThemeRevealPattern reveal = new LangPatterns.ThemeRevealPattern(split[0], split[1]);
                compiled.reveals.put(code, reveal);
                if (reveals.stream().noneMatch(old -> old.prefix.equals(reveal.prefix) && old.suffix.equals(reveal.suffix))) {
                    reveals.add(reveal);
                }
            }
            addLiteral(compiled.guesserPrefixes, data, "gtb.guesser_chat", code);
            addLiteral(compiled.alreadyGuessed, data, "gtb.already_guessed", code);
            addPattern(compiled.selfGuessPoints, data, "gtb.correct_guess_point", code, "points", "([123])");
            addPattern(compiled.selfGuessPoints, data, "gtb.correct_guess_points", code, "points", "([123])");
            addPattern(compiled.transfers, data, "hypixel.server_transfer_server", code, "server", "(?i:mini[0-9][0-9a-z]*)");
            addPattern(compiled.transfers, data, "hypixel.server_transfer_session", code, "session", "(?i:mini[0-9][0-9a-z]*)");
            addPattern(compiled.lobbyProtection, data, "hypixel.lobby_protection", code, "game", ".+");
            List<Pattern> joins = new ArrayList<>();
            for (String key : LOBBY_KEYS) {
                addPattern(joins, data, key, code, "player",
                        LangPatterns.LobbyLanguageFallback.PLAYER_DISPLAY.pattern());
            }
            compiled.lobbyJoins.put(code, joins);
            String[] ranks = new String[rankNames.length];
            for (int i = 0; i < ranks.length; i++) {
                ranks[i] = literal(data, "build_battle.rank." + rankNames[i].toLowerCase(Locale.ROOT), code);
                if (ranks[i] != null && titles.add(ranks[i])) aliases.add(ranks[i]);
            }
            compiled.rankTitles.put(code, ranks);
            String leaderboard = literal(data, "build_battle.rank.leaderboard_builder", code);
            if (leaderboard != null && leaderboard.indexOf("#x") == leaderboard.lastIndexOf("#x")
                    && leaderboard.contains("#x")) {
                int marker = leaderboard.indexOf("#x");
                compiled.leaderboardRanks.add(Pattern.compile(
                        Pattern.quote(leaderboard.substring(0, marker) + "#") + "(10|[1-9])"
                                + Pattern.quote(leaderboard.substring(marker + 2))));
            }
        }
        reveals.sort(Comparator.comparingInt((LangPatterns.ThemeRevealPattern r) -> r.prefix.length())
                .thenComparingInt(r -> r.suffix.length()).reversed());
        return compiled;
    }

    private static String value(JsonObject data, String key, String code) {
        JsonElement entry = data.get(key);
        if (entry == null || !entry.isJsonObject()) return null;
        JsonObject row = entry.getAsJsonObject();
        JsonElement text;
        if ("en".equals(code)) {
            text = row.get("source");
        } else {
            JsonElement translations = row.get("translations");
            if (translations == null || !translations.isJsonObject()) return null;
            JsonElement local = translations.getAsJsonObject().get(code);
            if (local == null || !local.isJsonObject()) return null;
            JsonObject translation = local.getAsJsonObject();
            JsonElement approved = translation.get("is_approved");
            if (approved == null || !approved.isJsonPrimitive()
                    || !approved.getAsJsonPrimitive().isBoolean() || !approved.getAsBoolean()) return null;
            text = translation.get("translation");
        }
        if (text == null || !text.isJsonPrimitive() || !text.getAsJsonPrimitive().isString()) return null;
        String raw = text.getAsString();
        if (raw.length() > 512) return null;
        Matcher tokens = TOKEN.matcher(raw);
        StringBuffer plain = new StringBuffer();
        while (tokens.find()) {
            String name = tokens.group(1);
            String result = FORMATTING.contains(name) ? "" : "%%" + ("perceel".equals(name) ? "plot" : name) + "%%";
            tokens.appendReplacement(plain, Matcher.quoteReplacement(result));
        }
        tokens.appendTail(plain);
        return plain.toString().trim();
    }

    private static String literal(JsonObject data, String key, String code) {
        String text = value(data, key, code);
        return text == null || text.isEmpty() || text.contains("%%") || text.contains("\n") || text.contains("\r")
                ? null : text;
    }

    private static String[] parts(JsonObject data, String key, String code, String... slots) {
        String text = value(data, key, code);
        if (text == null) return null;
        String[] parts = new String[slots.length + 1];
        int start = 0;
        for (int i = 0; i < slots.length; i++) {
            String token = "%%" + slots[i] + "%%";
            int position = text.indexOf(token, start);
            if (position < 0) return null;
            parts[i] = text.substring(start, position);
            start = position + token.length();
        }
        parts[slots.length] = text.substring(start);
        boolean hasLiteral = false;
        for (String part : parts) {
            if (part.contains("%%")) return null;
            hasLiteral |= !part.trim().isEmpty();
        }
        return hasLiteral ? parts : null;
    }

    private static String roundPrefix(JsonObject data, String code) {
        String[] split = parts(data, "gtb.round_announcement", code, "round", "totalrounds");
        if (split == null || !"/".equals(split[1].trim()) || !split[2].isEmpty()) return null;
        String prefix = split[0].replace('\n', ' ').replace('\r', ' ');
        return prefix.trim().isEmpty() ? null : prefix;
    }

    private static String append(String text, String suffix) {
        return text == null ? null : text + suffix;
    }

    private static String appendLabelColon(String label, String fallback) {
        if (label == null || label.endsWith(":") || label.endsWith("：")) return label;
        return append(label, fallback != null && fallback.endsWith("：") ? "：" : ":");
    }

    private static void addLiteral(Set<String> target, JsonObject data, String key, String code) {
        String text = literal(data, key, code);
        if (text != null) target.add(text);
    }

    private static void addPattern(List<Pattern> target, JsonObject data, String key,
                                   String code, String slot, String capture) {
        String[] split = parts(data, key, code, slot);
        if (split != null) target.add(Pattern.compile(Pattern.quote(split[0]) + capture + Pattern.quote(split[1])));
    }

    static boolean matches(List<Pattern> patterns, String line) {
        if (line == null) return false;
        for (Pattern pattern : patterns) {
            if (pattern.matcher(line.trim()).matches()) return true;
        }
        return false;
    }
}
