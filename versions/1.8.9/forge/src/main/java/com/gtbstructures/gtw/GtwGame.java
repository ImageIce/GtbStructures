package com.gtbstructures.gtw;

import com.gtbstructures.commands.CommandGtb;
import com.gtbstructures.GtbStructures;
import com.gtbstructures.hints.ShowAllMatching;
import com.gtbstructures.stats.LangPatterns;
import com.gtbstructures.translation.TranslationData;

import net.minecraft.client.Minecraft;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * /gtbs gtw "Guess The Wordhint" mini-game.
 *
 * Picks a random English theme, displays hints in the user-selected language
 * with progressive single-letter reveals (1 → 2 → 3 letters at 12 s intervals),
 * accepts /gtbs guess input. On a correct guess or 12 s after hint 3 it ends
 * and lists every theme whose displayed name in that language matches the
 * effective initial hint.
 */
public final class GtwGame {

    public static final GtwGame INSTANCE = new GtwGame();

    private static final int TICKS_PER_HINT = 240; // 12 s @ 20 TPS
    private static final int ACCENT_NORM_CACHE_LIMIT = 512;
    private static final String DIFFICULTY_EASY = "easy";
    private static final String DIFFICULTY_MEDIUM = "medium";
    private static final String DIFFICULTY_HARD = "hard";
    private static final String DIFFICULTY_ALL = "all";
    private static final Map<String, String> ACCENT_NORM_CACHE =
            new LinkedHashMap<String, String>(ACCENT_NORM_CACHE_LIMIT, 0.75f, true) {
                private static final long serialVersionUID = 1L;

                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > ACCENT_NORM_CACHE_LIMIT;
                }
            };

    // ── Session state (only meaningful when active == true) ──
    private boolean active;
    private String langCode;
    private String activeDifficulty = DIFFICULTY_ALL;
    private String englishTheme;
    private String displayedTheme;
    private String[] hints;     // hints[0..2] with 1, 2, 3 letters revealed
    private int hintLevel;      // 0 → hint1 sent; 1 → hint2 sent; 2 → hint3 sent; 3 → ended
    private int ticksToNext;
    private int guessCount;
    private final Random rng = new Random();

    private GtwGame() {}

    public boolean isActive() { return active; }

    // ──────────────────────────── Public API ────────────────────────────

    public void onClientTick() {
        if (!active) return;
        if (--ticksToNext > 0) return;

        hintLevel++;
        // Send the next hint if more reveal levels exist for this theme.
        // Themes shorter than 3 revealable chars stop earlier (e.g. a 1-char
        // theme has only hint 1, then 12s later the game ends).
        if (hintLevel < hints.length) {
            send("Hint " + (hintLevel + 1) + ": §f" + hints[hintLevel]);
            ticksToNext = TICKS_PER_HINT;
            return;
        }
        endWithReveal();
    }

    /**
     * /gtbs gtw <start|stop|showenglish|showcount|difficulty ...>. The first arg has already been
     * consumed by the caller; `args` here starts at the gtw subcommand.
     */
    public static void processGtw(ICommandSender sender, String[] args) {
        if (args.length == 0) {
            send(sender, "Usage: /gtbs gtw <start|stop|showenglish|showcount|difficulty> ...");
            return;
        }
        String sub = args[0].toLowerCase();
        if (sub.equals("start")) {
            String langCode;
            if (args.length < 2) {
                langCode = GtbStructures.gtwDefaultLang;
                if (langCode == null || langCode.isEmpty()) langCode = "en";
            } else {
                String langInput = joinFrom(args, 1);
                langCode = resolveLanguage(langInput);
                if (langCode == null) {
                    send(sender, "Unknown language. Available codes: §f" + listAllCodes());
                    return;
                }
            }
            INSTANCE.start(langCode);
        } else if (sub.equals("stop")) {
            INSTANCE.stopByUser();
        } else if (sub.equals("showenglish")) {
            if (args.length < 2) {
                send(sender, "Show English (in /gtbs gtw) is currently " +
                        (GtbStructures.gtwShowEnglish ? "§aON" : "§cOFF"));
                return;
            }
            String v = args[1].toLowerCase();
            if (v.equals("true") || v.equals("on") || v.equals("1")) {
                GtbStructures.gtwShowEnglish = true;
            } else if (v.equals("false") || v.equals("off") || v.equals("0")) {
                GtbStructures.gtwShowEnglish = false;
            } else {
                send(sender, "Usage: /gtbs gtw showenglish <true|false>");
                return;
            }
            GtbStructures.saveConfig();
            send(sender, "Show English (in /gtbs gtw) is currently " +
                    (GtbStructures.gtwShowEnglish ? "§aON" : "§cOFF"));
        } else if (sub.equals("showcount")) {
            if (args.length == 1) {
                send(sender, showCountStatus(GtbStructures.gtwShowCount));
                return;
            }
            if (args.length != 2) {
                send(sender, "Usage: /gtw showcount [on|off]");
                return;
            }
            Boolean enabled = parseBooleanSetting(args[1]);
            if (enabled == null) {
                send(sender, "Usage: /gtw showcount [on|off]");
                return;
            }
            GtbStructures.gtwShowCount = enabled.booleanValue();
            GtbStructures.saveConfig();
            send(sender, showCountStatus(GtbStructures.gtwShowCount));
        } else if (sub.equals("difficulty")) {
            if (args.length < 2) {
                send(sender, "GTW difficulty is currently: §f"
                        + normalizeDifficulty(GtbStructures.gtwDifficulty));
                return;
            }
            String difficulty = parseDifficulty(args[1]);
            if (args.length != 2 || difficulty == null) {
                send(sender, "Usage: /gtw difficulty <easy|medium|hard|all>");
                return;
            }
            GtbStructures.gtwDifficulty = difficulty;
            GtbStructures.saveConfig();
            send(sender, "GTW difficulty is currently: §f" + difficulty);
        } else {
            send(sender, "Usage: /gtbs gtw <start|stop|showenglish|showcount|difficulty> ...");
        }
    }

    /** /gtbs guess <guess words...>. */
    public static void processGuess(ICommandSender sender, String[] args) {
        if (args.length == 0) {
            send(sender, "Usage: /gtbs guess <guess>");
            return;
        }
        String guess = joinFrom(args, 0);
        INSTANCE.onGuess(guess);
    }

    /** Tab completion for /gtbs gtw. `args` starts at the gtw subcommand. */
    public static List<String> tabGtw(String[] args) {
        if (args.length == 1) return matching(args[0],
                "start", "stop", "showenglish", "showcount", "difficulty");
        String sub = args[0].toLowerCase();
        if (args.length == 2 && sub.equals("start")) {
            // Suggest 2-letter codes plus full names.
            List<String> options = new ArrayList<String>(LANG_LOOKUP.keySet());
            Collections.sort(options);
            return matching(args[1], options.toArray(new String[0]));
        }
        if (args.length == 2 && sub.equals("showenglish")) {
            return matching(args[1], "true", "false");
        }
        if (args.length == 2 && sub.equals("showcount")) {
            return matching(args[1], "on", "off");
        }
        if (args.length == 2 && sub.equals("difficulty")) {
            return matching(args[1], DIFFICULTY_EASY, DIFFICULTY_MEDIUM,
                    DIFFICULTY_HARD, DIFFICULTY_ALL);
        }
        return null;
    }

    /** Tab completion for /gtbs guess: no suggestions (free text). */
    public static List<String> tabGuess(String[] args) {
        return null;
    }

    // ───────────────────────── Session control ──────────────────────────

    private synchronized void start(String langCode) {
        if (active) {
            send("A Guess The Wordhint game is already in progress. Use §f/gtbs gtw stop§7 first.");
            return;
        }
        if (!TranslationData.isLoaded()) {
            send("Translation data isn't loaded yet — try again in a moment.");
            return;
        }
        String selectedDifficulty = normalizeDifficulty(GtbStructures.gtwDifficulty);
        String pickedEnglish = pickEnglishTheme(rng, selectedDifficulty);
        if (pickedEnglish == null) {
            send("No themes available.");
            return;
        }
        String displayed = TranslationData.getDisplayedTheme(langCode, pickedEnglish);
        if (displayed == null || displayed.isEmpty()) {
            send("Failed to resolve theme translation.");
            return;
        }

        this.langCode = langCode;
        this.activeDifficulty = selectedDifficulty;
        this.englishTheme = pickedEnglish;
        this.displayedTheme = displayed;
        this.guessCount = 0;
        this.hints = buildProgressiveHints(displayed, 3, rng);
        this.hintLevel = 0;
        this.ticksToNext = TICKS_PER_HINT;
        this.active = true;

        // Persist the chosen language so the next /gtbs gtw start without an
        // arg picks up where the player left off.
        if (!langCode.equals(GtbStructures.gtwDefaultLang)) {
            GtbStructures.gtwDefaultLang = langCode;
            GtbStructures.saveConfig();
        }

        send("Starting a Guess The Wordhint game...");
        send(buildSelectedSettingsMessage(this.langCode, this.activeDifficulty));
        send("Hint 1: §f" + hints[0] + " §7( " + structureNotation(displayed) + " )");
    }

    private synchronized void stopByUser() {
        if (!active) {
            send("You are not currently in a Guess The Wordhint game");
            return;
        }
        endWithReveal();
    }

    private synchronized void onGuess(String guess) {
        if (!active) {
            send("You are not currently in a Guess The Wordhint game");
            return;
        }
        guessCount++;
        send("Guess " + guessCount + ": §f" + guess);

        if (matchesAnyTranslation(guess, englishTheme)) {
            endWithCorrect();
        }
    }

    private void endWithReveal() {
        send("Game over! The theme is: §f" + displayedTheme + englishParens(displayedTheme, englishTheme));
        sendAllMatching();
        active = false;
    }

    private void endWithCorrect() {
        send("Correct! The theme is: §f" + displayedTheme + englishParens(displayedTheme, englishTheme));
        sendAllMatching();
        active = false;
    }

    // ───────────────────────── Output building ──────────────────────────

    private String englishParens(String displayed, String english) {
        if (!showEnglishForLang()) return "";
        if (displayed != null && english != null && displayed.equals(english)) return "";
        return "§7 (" + english + ")";
    }

    /** True iff English origins should be annotated (account for english/pirate edge cases). */
    private boolean showEnglishForLang() {
        if (!GtbStructures.gtwShowEnglish) return false;
        return !"en".equalsIgnoreCase(langCode);
    }

    private void sendAllMatching() {
        String message = buildAllMatchingMessage(
                langCode, hints[0], activeDifficulty, showEnglishForLang(),
                GtbStructures.gtwShowCount);
        if (message != null) send(message);
    }

    static String buildAllMatchingMessageForTest(String langCode, String initialHint,
                                                  String difficulty, boolean showEnglish,
                                                  boolean showCount) {
        boolean withEnglish = showEnglish && !"en".equalsIgnoreCase(langCode);
        return buildAllMatchingMessage(langCode, initialHint, difficulty,
                withEnglish, showCount);
    }

    private static String buildAllMatchingMessage(String langCode, String initialHint,
                                                  String difficulty, boolean withEnglish,
                                                  boolean showCount) {
        Map<String, Set<String>> matches = filterMatchesForDifficulty(
                TranslationData.findMatchingDisplayedThemes(langCode, initialHint), difficulty);
        if (matches.isEmpty()) return null;

        boolean isPirate = "pirate".equalsIgnoreCase(langCode);
        StringBuilder sb = new StringBuilder();
        sb.append("All themes matching the initial hint: ");
        boolean first = true;
        for (Map.Entry<String, Set<String>> e : matches.entrySet()) {
            if (!first) sb.append("§7, ");
            first = false;
            sb.append("§f").append(e.getKey());
            if (withEnglish && shouldShowEnglish(isPirate, e.getKey(), e.getValue())) {
                sb.append("§7 (");
                boolean firstEn = true;
                for (String en : e.getValue()) {
                    if (!firstEn) sb.append(", ");
                    firstEn = false;
                    sb.append(en);
                }
                sb.append(")");
            }
        }
        if (showCount) sb.append(" §7(§e").append(matches.size()).append("§7)");
        return sb.toString();
    }

    static Map<String, Set<String>> filterMatchesForDifficultyForTest(
            Map<String, Set<String>> matches, String difficulty) {
        return filterMatchesForDifficulty(matches, difficulty);
    }

    private static Map<String, Set<String>> filterMatchesForDifficulty(
            Map<String, Set<String>> matches, String difficulty) {
        Map<String, Set<String>> filtered = new TreeMap<String, Set<String>>();
        if (matches == null || matches.isEmpty()) return filtered;
        for (Map.Entry<String, Set<String>> entry : matches.entrySet()) {
            Set<String> origins = new TreeSet<String>();
            if (entry.getValue() != null) {
                for (String englishOrigin : entry.getValue()) {
                    if (matchesDifficulty(englishOrigin, difficulty)) origins.add(englishOrigin);
                }
            }
            if (!origins.isEmpty()) filtered.put(entry.getKey(), origins);
        }
        return filtered;
    }

    /**
     * Mirrors ShowAllMatching.shouldShowEnglish: for pirate, suppress brackets
     * when the pirate translation already equals its single English origin.
     */
    private static boolean shouldShowEnglish(boolean isPirate, String displayed, Set<String> englishOrigins) {
        if (!isPirate) return true;
        if (englishOrigins == null || englishOrigins.isEmpty()) return false;
        if (englishOrigins.size() == 1 && englishOrigins.contains(displayed)) return false;
        return true;
    }

    public static String normalizeDifficulty(String difficulty) {
        String parsed = parseDifficulty(difficulty);
        return parsed == null ? DIFFICULTY_ALL : parsed;
    }

    private static String parseDifficulty(String difficulty) {
        if (difficulty == null) return null;
        String normalized = difficulty.trim().toLowerCase(Locale.ROOT);
        if (DIFFICULTY_EASY.equals(normalized)
                || DIFFICULTY_MEDIUM.equals(normalized)
                || DIFFICULTY_HARD.equals(normalized)
                || DIFFICULTY_ALL.equals(normalized)) {
            return normalized;
        }
        return null;
    }

    static boolean isDifficultyForTest(String difficulty) {
        return parseDifficulty(difficulty) != null;
    }

    static String buildSelectedSettingsMessageForTest(String langCode, String difficulty) {
        return buildSelectedSettingsMessage(langCode, difficulty);
    }

    private static String buildSelectedSettingsMessage(String langCode, String difficulty) {
        String languageLookup = "ptbr".equalsIgnoreCase(langCode) ? "pt_br" : langCode;
        String languageName = LangPatterns.mapLanguageName(languageLookup);
        if (languageName == null || languageName.trim().isEmpty()) {
            languageName = langCode == null ? "" : langCode;
        }

        String normalizedDifficulty = normalizeDifficulty(difficulty);
        String difficultyColor = "§f";
        if (DIFFICULTY_EASY.equals(normalizedDifficulty)) {
            difficultyColor = "§a";
        } else if (DIFFICULTY_MEDIUM.equals(normalizedDifficulty)) {
            difficultyColor = "§e";
        } else if (DIFFICULTY_HARD.equals(normalizedDifficulty)) {
            difficultyColor = "§c";
        }
        return "Selected language: §f" + languageName + "§7; difficulty: "
                + difficultyColor + normalizedDifficulty.toUpperCase(Locale.ROOT);
    }

    static boolean matchesDifficultyForTest(String englishTheme, String difficulty) {
        return matchesDifficulty(englishTheme, difficulty);
    }

    private static boolean matchesDifficulty(String englishTheme, String difficulty) {
        int point = difficultyPoint(difficulty);
        return point == 0 || TranslationData.lengthBucket(englishTheme) == point;
    }

    private static int difficultyPoint(String difficulty) {
        String normalized = normalizeDifficulty(difficulty);
        if (DIFFICULTY_EASY.equals(normalized)) return 1;
        if (DIFFICULTY_MEDIUM.equals(normalized)) return 2;
        if (DIFFICULTY_HARD.equals(normalized)) return 3;
        return 0;
    }

    static String pickEnglishThemeForTest(Random rng, String difficulty) {
        return pickEnglishTheme(rng, difficulty);
    }

    private static String pickEnglishTheme(Random rng, String difficulty) {
        List<String> eligible = new ArrayList<String>();
        for (String theme : TranslationData.getEnglishThemes()) {
            if (matchesDifficulty(theme, difficulty)) eligible.add(theme);
        }
        if (eligible.isEmpty()) return null;
        return eligible.get(rng.nextInt(eligible.size()));
    }

    // ─────────────────────── Hint / structure builders ──────────────────

    /**
     * Build hint patterns with progressive single-letter reveals (each level
     * reveals one more non-space char, sharing a single permutation of
     * revealable indices). Returns at most {@code maxReveals} entries, but is
     * clamped to the number of non-space characters in {@code text} — so a
     * 1-char theme yields a single fully-revealed hint and a "1-1" theme
     * yields two. Caller stops sending hints once the array runs out.
     * Spaces are preserved verbatim; non-space characters start as '_'.
     */
    static String[] buildProgressiveHints(String text, int maxReveals, Random rng) {
        int n = text.length();
        char[] base = new char[n];
        for (int i = 0; i < n; i++) {
            char c = text.charAt(i);
            base[i] = (c == ' ') ? ' ' : '_';
        }
        List<Integer> revealable = new ArrayList<Integer>();
        for (int i = 0; i < n; i++) {
            if (text.charAt(i) != ' ') revealable.add(i);
        }
        Collections.shuffle(revealable, rng);

        int levels = Math.min(maxReveals, revealable.size());
        if (levels <= 0) {
            // Edge case: no revealable characters (e.g. empty / all-space).
            // Return one fully-blank hint so the caller still has something
            // to display before falling through to the reveal.
            return new String[] { new String(base) };
        }
        String[] out = new String[levels];
        char[] cur = base.clone();
        for (int level = 0; level < levels; level++) {
            int idx = revealable.get(level);
            cur[idx] = text.charAt(idx);
            out[level] = new String(cur);
        }
        return out;
    }

    /** "treasure chest" → "8-5"; "castle" → "6"; spaces split words; no spaces in output. */
    static String structureNotation(String text) {
        StringBuilder sb = new StringBuilder();
        int wordLen = 0;
        boolean started = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ') {
                if (started) {
                    sb.append(wordLen);
                    sb.append('-');
                }
                wordLen = 0;
                started = false;
                continue;
            }
            wordLen++;
            started = true;
        }
        sb.append(wordLen);
        return sb.toString();
    }

    // ─────────────────── Answer matching (port of gtb_gui) ──────────────

    /** Accept English, approved translations, and exact complements for the theme. */
    private static boolean matchesAnyTranslation(String guess, String englishTheme) {
        return TranslationData.findActiveGuessThemes(guess).contains(englishTheme);
    }

    public static boolean matchesAnyTranslationForTest(String guess, String englishTheme) {
        return matchesAnyTranslation(guess, englishTheme);
    }

    /** Port of gtb_gui core/practice.gtb_matches. */
    public static boolean gtbMatches(String userInput, String translation, String lang) {
        if (userInput == null || translation == null) return false;
        String trimmedInput = userInput.trim();
        if ("co".equals(lang)) {
            return trimmedInput.toLowerCase(Locale.ROOT).equals(translation.toLowerCase(Locale.ROOT));
        }
        String inp = baseNorm(trimmedInput);
        String orig = baseNorm(translation);

        if (inp.equals(orig)) return true;

        // Mod 1: trailing 's' add/remove
        if (translation.length() > 0 && translation.charAt(translation.length() - 1) == 's') {
            if (inp.equals(baseNorm(translation.substring(0, translation.length() - 1)))) return true;
        }
        if (trimmedInput.endsWith("s") && inp.equals(orig + "s")) return true;

        // Mod 2: space add/remove
        if (removeWhitespace(inp).equals(removeWhitespace(orig))) return true;

        // Mod 3: NFKD accent exchange
        if (accentNorm(inp).equals(accentNorm(orig))) return true;

        return false;
    }

    private static String baseNorm(String s) {
        return s.toLowerCase().replace('ı', 'i');
    }

    private static String accentNorm(String s) {
        String cached;
        synchronized (ACCENT_NORM_CACHE) {
            cached = ACCENT_NORM_CACHE.get(s);
        }
        if (cached != null) return cached;

        String cacheKey = s;
        final char feminineOrdinalSentinel = '\ue000';
        final char masculineOrdinalSentinel = '\ue001';
        s = s.replace('\u00aa', feminineOrdinalSentinel);
        s = s.replace('\u00ba', masculineOrdinalSentinel);
        s = s.replace('ł', 'l').replace('Ł', 'l');
        String normalized = decomposePreservingFullWidthLatin(s);
        StringBuilder sb = new StringBuilder(normalized.length());
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (c >= 0x0300 && c <= 0x036f) continue;
            sb.append(c);
        }
        // NFKD also splits Hangul syllables into Jamo. Recompose canonical
        // sequences so shortcut length remains the visible character count.
        String result = Normalizer.normalize(sb.toString(), Normalizer.Form.NFC)
                .replace(feminineOrdinalSentinel, '\u00aa')
                .replace(masculineOrdinalSentinel, '\u00ba');
        synchronized (ACCENT_NORM_CACHE) {
            cached = ACCENT_NORM_CACHE.get(cacheKey);
            if (cached != null) return cached;
            ACCENT_NORM_CACHE.put(cacheKey, result);
        }
        return result;
    }

    private static String decomposePreservingFullWidthLatin(String s) {
        boolean hasFullWidthLatin = false;
        for (int offset = 0; offset < s.length();) {
            int codePoint = s.codePointAt(offset);
            if (isFullWidthLatin(codePoint)) {
                hasFullWidthLatin = true;
                break;
            }
            offset += Character.charCount(codePoint);
        }
        if (!hasFullWidthLatin) {
            return Normalizer.normalize(s, Normalizer.Form.NFKD);
        }

        StringBuilder normalized = new StringBuilder(s.length());
        int segmentStart = 0;
        for (int offset = 0; offset < s.length();) {
            int codePoint = s.codePointAt(offset);
            int nextOffset = offset + Character.charCount(codePoint);
            if (isFullWidthLatin(codePoint)) {
                appendDecomposedSegment(normalized, s, segmentStart, offset);
                normalized.appendCodePoint(codePoint);
                segmentStart = nextOffset;
            }
            offset = nextOffset;
        }
        appendDecomposedSegment(normalized, s, segmentStart, s.length());
        return normalized.toString();
    }

    private static void appendDecomposedSegment(
            StringBuilder target, String source, int start, int end) {
        if (start >= end) return;
        target.append(Normalizer.normalize(
                source.substring(start, end), Normalizer.Form.NFKD));
    }

    private static boolean isFullWidthLatin(int codePoint) {
        return (codePoint >= 0xFF21 && codePoint <= 0xFF3A)
                || (codePoint >= 0xFF41 && codePoint <= 0xFF5A);
    }

    /**
     * Generate legal shortcut-display variants accepted by GTB from one source
     * string. Case and Turkish dotless-i normalization are free; each returned
     * variant applies at most one of: lowercase trailing-s removal, whitespace
     * removal, or Unicode normalized-form exchange.
     */
    public static List<LegalShortcutCandidate> legalShortcutCandidates(
            String s, boolean preferExtendedLatin) {
        if (s == null) return Collections.emptyList();
        String raw = s;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return Collections.emptyList();

        Map<String, LegalShortcutCandidate> out =
                new LinkedHashMap<String, LegalShortcutCandidate>();
        addLegalShortcutCandidate(out, baseNorm(trimmed), 1);

        if (raw.endsWith("s")) {
            String withoutS = baseNorm(raw.substring(0, raw.length() - 1));
            if (hasDisplayablePreservedWhitespace(withoutS)) {
                addLegalShortcutCandidate(out, withoutS, 0);
            }
        }

        String normalizedRaw = baseNorm(raw);
        String withoutWhitespace = removeWhitespace(normalizedRaw);
        boolean removesWhitespace = !withoutWhitespace.equals(normalizedRaw);
        addLegalShortcutCandidate(out, withoutWhitespace,
                removesWhitespace ? (preferExtendedLatin ? 0 : 2) : 1);

        String accentFolded = accentNorm(normalizedRaw);
        if (hasDisplayablePreservedWhitespace(accentFolded)) {
            addLegalShortcutCandidate(out, accentFolded,
                    removesWhitespace && preferExtendedLatin ? 2 : 0);
        }
        return new ArrayList<LegalShortcutCandidate>(out.values());
    }

    private static void addLegalShortcutCandidate(
            Map<String, LegalShortcutCandidate> out, String value, int preference) {
        if (value == null || value.isEmpty()) return;
        String key = value.toLowerCase(Locale.ROOT);
        LegalShortcutCandidate existing = out.get(key);
        if (existing == null || preference < existing.preference) {
            out.put(key, new LegalShortcutCandidate(value, preference));
        }
    }

    private static boolean hasDisplayablePreservedWhitespace(String value) {
        return value != null
                && value.equals(value.trim())
                && value.indexOf('\n') < 0
                && value.indexOf('\r') < 0;
    }

    private static String removeWhitespace(String s) {
        if (s == null || s.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isWhitespace(c)) sb.append(c);
        }
        return sb.toString();
    }

    public static final class LegalShortcutCandidate {
        public final String value;
        public final int preference;

        LegalShortcutCandidate(String value, int preference) {
            this.value = value;
            this.preference = preference;
        }
    }

    // ────────────────────────── Language lookup ─────────────────────────

    /**
     * Lowercased input → mod-internal lang code. Built once; covers every
     * language with both its 2-letter (or short) code and its full English name.
     * For Pirate, both "pirate" and "ir" resolve to "pirate" per the spec.
     */
    private static final Map<String, String> LANG_LOOKUP;
    /** Canonical short code for every language, in the order listed in build.gradle's i18n table. */
    private static final List<String> ALL_CODES;

    static {
        Map<String, String> m = new LinkedHashMap<String, String>();
        // [code, fullName, modCode]
        Object[][] table = {
            {"en",    "english",                "en"},
            {"fr",    "french",                 "fr"},
            {"de",    "german",                 "de"},
            {"it",    "italian",                "it"},
            {"nl",    "dutch",                  "nl"},
            {"es",    "spanish",                "es"},
            {"pt",    "portuguese",             "pt"},
            {"ptbr",  "portuguese (brazil)",    "ptbr"},
            {"ru",    "russian",                "ru"},
            {"ko",    "korean",                 "ko"},
            {"pl",    "polish",                 "pl"},
            {"ja",    "japanese",               "ja"},
            {"ir",    "pirate",                 "pirate"},
            {"no",    "norwegian",              "no"},
            {"sv",    "swedish",                "sv"},
            {"tr",    "turkish",                "tr"},
            {"da",    "danish",                 "da"},
            {"cs",    "czech",                  "cs"},
            {"fi",    "finnish",                "fi"},
            {"zh_cn", "chinese (simplified)",   "zh_cn"},
            {"zh_tw", "chinese (traditional)",  "zh_tw"},
            {"ro",    "romanian",               "ro"},
            {"hu",    "hungarian",              "hu"},
            {"uk",    "ukrainian",              "uk"},
        };
        List<String> all = new ArrayList<String>();
        for (Object[] row : table) {
            String code = (String) row[0];
            String name = (String) row[1];
            String mod  = (String) row[2];
            m.put(code, mod);
            m.put(name, mod);
            // Friendly aliases for the >2-char "abbreviations".
            if ("ptbr".equals(code))  { m.put("pt_br", mod); m.put("pt-br", mod); }
            if ("zh_cn".equals(code)) { m.put("zhcn", mod);  m.put("zh-cn", mod); }
            if ("zh_tw".equals(code)) { m.put("zhtw", mod);  m.put("zh-tw", mod); }
            // "Pirate Speak" full name as well.
            if ("ir".equals(code))    { m.put("pirate speak", mod); }
            all.add(code);
        }
        LANG_LOOKUP = Collections.unmodifiableMap(m);
        ALL_CODES = Collections.unmodifiableList(all);
    }

    private static String resolveLanguage(String input) {
        if (input == null) return null;
        String key = input.trim().toLowerCase();
        return LANG_LOOKUP.get(key);
    }

    private static String listAllCodes() {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String c : ALL_CODES) {
            if (!first) sb.append(", ");
            first = false;
            sb.append(c);
        }
        return sb.toString();
    }

    // ─────────────────────────── Misc helpers ───────────────────────────

    private static String joinFrom(String[] args, int start) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (i > start) sb.append(' ');
            sb.append(args[i]);
        }
        return sb.toString();
    }

    private static Boolean parseBooleanSetting(String raw) {
        if (raw == null) return null;
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("true") || normalized.equals("on")
                || normalized.equals("1") || normalized.equals("yes")) {
            return Boolean.TRUE;
        }
        if (normalized.equals("false") || normalized.equals("off")
                || normalized.equals("0") || normalized.equals("no")) {
            return Boolean.FALSE;
        }
        return null;
    }

    static String buildShowCountStatusForTest(boolean enabled) {
        return showCountStatus(enabled);
    }

    private static String showCountStatus(boolean enabled) {
        return "GTW total theme count is currently " + (enabled ? "§aON" : "§cOFF");
    }

    private static List<String> matching(String prefix, String... options) {
        List<String> out = new ArrayList<String>();
        String p = prefix == null ? "" : prefix.toLowerCase();
        for (String o : options) {
            if (o.toLowerCase().startsWith(p)) out.add(o);
        }
        return out;
    }

    /** Send a message prefixed with the GtbStructures chat header to the local player. */
    private static void send(String body) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(new ChatComponentText(CommandGtb.head + body));
        }
    }

    /** Send via the command sender (used when called from /gtbs ... so the message lands at the right time). */
    private static void send(ICommandSender sender, String body) {
        sender.addChatMessage(new ChatComponentText(CommandGtb.head + body));
    }
}
