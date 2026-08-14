package com.gtbstructures.chat;

import com.gtbstructures.GtbStructures;
import com.gtbstructures.stats.GameStats;
import com.gtbstructures.translation.TranslationData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ThreadLocalRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public final class ScrambleGuess {
    private static final int MAX_CHAT_LENGTH = 100;
    private static final int FANCY_ATTEMPTS = 16;
    private static final Map<Character, List<Character>> ACCENT_ALTERNATIVES = buildAccentAlternatives();

    private ScrambleGuess() {
    }

    public static String transformOutbound(String message) {
        return transform(message, GameStats.INSTANCE.getState(),
                GameStats.INSTANCE.hasLocalPlayerCorrectlyGuessedCurrentRound(),
                GtbStructures.scrambleGuess, GtbStructures.scrambleGuessFancy,
                ThreadLocalRandom.current());
    }

    static String transformForTest(String message,
                                   GameStats.TrackingState state,
                                   boolean enabled,
                                   boolean fancy,
                                   Random random) {
        return transform(message, state, false, enabled, fancy, random);
    }

    static String transformForTest(String message,
                                   GameStats.TrackingState state,
                                   boolean alreadyGuessed,
                                   boolean enabled,
                                   boolean fancy,
                                   Random random) {
        return transform(message, state, alreadyGuessed, enabled, fancy, random);
    }

    static String caseVariantForTest(String value, Random random) {
        return caseVariant(value, random);
    }

    static String accentVariantForTest(String value, Random random) {
        return accentVariant(value, random);
    }

    static int accentAlternativeCountForTest(char value) {
        List<Character> alternatives = ACCENT_ALTERNATIVES.get(Character.valueOf(value));
        return alternatives == null ? 0 : alternatives.size();
    }

    static String whitespaceVariantForTest(String value, Random random) {
        return whitespaceVariant(value, random);
    }

    static String trailingSVariantForTest(String value) {
        return trailingSVariant(value);
    }

    static String trailingSCaseVariantForTest(String value, Random random) {
        return trailingSCaseVariant(value, random);
    }

    static String dotlessIVariantForTest(String value, Random random) {
        return dotlessIVariant(value, random);
    }

    private static String transform(String message,
                                    GameStats.TrackingState state,
                                    boolean alreadyGuessed,
                                    boolean enabled,
                                    boolean fancy,
                                    Random random) {
        if (!enabled || alreadyGuessed || state != GameStats.TrackingState.ROUND_BUILD
                || message == null || random == null) {
            return message;
        }

        Set<String> themes = TranslationData.findActiveGuessThemes(message);
        if (themes.isEmpty()) return message;
        List<String> candidates = safeCandidates(TranslationData.findScrambleCandidates(message));
        if (candidates.isEmpty()) return message;

        String selected = candidates.get(random.nextInt(candidates.size()));
        if (!fancy) {
            return TranslationData.worksForExactActiveThemes(selected, themes)
                    ? selected : message;
        }
        for (int i = 0; i < FANCY_ATTEMPTS; i++) {
            String decorated = fancyVariant(selected, random);
            if (isChatSafe(decorated)
                    && TranslationData.worksForExactActiveThemes(decorated, themes)) {
                return decorated;
            }
        }
        return TranslationData.worksForExactActiveThemes(selected, themes)
                ? selected : message;
    }

    private static List<String> safeCandidates(List<String> candidates) {
        if (candidates == null || candidates.isEmpty()) return Collections.emptyList();
        List<String> safe = new ArrayList<String>(candidates.size());
        for (String candidate : candidates) {
            if (isChatSafe(candidate)) safe.add(candidate);
        }
        return safe;
    }

    private static String fancyVariant(String base, Random random) {
        String variant = base;
        switch (random.nextInt(4)) {
            case 1:
                variant = accentVariant(base, random);
                break;
            case 2:
                variant = whitespaceVariant(base, random);
                break;
            case 3:
                return trailingSCaseVariant(base, random);
            default:
                break;
        }
        return caseVariant(dotlessIVariant(variant, random), random);
    }

    private static String trailingSCaseVariant(String value, Random random) {
        String cased = caseVariant(dotlessIVariant(value, random), random);
        if (value.endsWith("s")) {
            return cased.substring(0, cased.length() - 1);
        }
        return cased + "s";
    }

    private static String dotlessIVariant(String value, Random random) {
        if (value == null || value.isEmpty() || random == null) return value;
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c == 'i' || c == '\u0131') && random.nextBoolean()) {
                c = c == 'i' ? '\u0131' : 'i';
            }
            out.append(c);
        }
        return out.toString();
    }

    private static String caseVariant(String value, Random random) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isLetter(c) && random.nextBoolean()) {
                c = Character.toUpperCase(c);
            }
            out.append(c);
        }
        return out.toString();
    }

    private static String accentVariant(String value, Random random) {
        List<Integer> replaceable = new ArrayList<Integer>();
        for (int i = 0; i < value.length(); i++) {
            List<Character> alternatives = ACCENT_ALTERNATIVES.get(value.charAt(i));
            if (alternatives != null && alternatives.size() > 1) replaceable.add(Integer.valueOf(i));
        }
        if (replaceable.isEmpty()) return value;

        char[] chars = value.toCharArray();
        int required = replaceable.get(random.nextInt(replaceable.size())).intValue();
        for (Integer boxed : replaceable) {
            int index = boxed.intValue();
            if (index != required && !random.nextBoolean()) continue;
            List<Character> alternatives = ACCENT_ALTERNATIVES.get(chars[index]);
            chars[index] = differentCharacter(chars[index], alternatives, random);
        }
        return new String(chars);
    }

    private static char differentCharacter(char current, List<Character> alternatives, Random random) {
        if (alternatives == null || alternatives.size() < 2) return current;
        int start = random.nextInt(alternatives.size());
        for (int i = 0; i < alternatives.size(); i++) {
            char candidate = alternatives.get((start + i) % alternatives.size()).charValue();
            if (candidate != current) return candidate;
        }
        return current;
    }

    private static String whitespaceVariant(String value, Random random) {
        StringBuilder compact = new StringBuilder(value.length());
        boolean hadWhitespace = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isWhitespace(c)) {
                hadWhitespace = true;
            } else {
                compact.append(c);
            }
        }
        if (hadWhitespace || compact.length() < 2) return compact.toString();

        int boundary = 1 + random.nextInt(compact.length() - 1);
        int spaces = 1 + random.nextInt(2);
        StringBuilder out = new StringBuilder(compact.length() + spaces);
        out.append(compact, 0, boundary);
        for (int i = 0; i < spaces; i++) out.append(' ');
        out.append(compact, boundary, compact.length());
        return out.toString();
    }

    private static String trailingSVariant(String value) {
        return value.endsWith("s") ? value.substring(0, value.length() - 1) : value + "s";
    }

    private static boolean isChatSafe(String value) {
        if (value == null || value.isEmpty() || value.length() > MAX_CHAT_LENGTH) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\r' || c == '\n' || c == '\u0000') return false;
        }
        return true;
    }

    private static Map<Character, List<Character>> buildAccentAlternatives() {
        Map<String, List<Character>> byNormalized = new HashMap<String, List<Character>>();
        addAccentAlternatives(byNormalized, 0x0041, 0x024F);

        Map<Character, List<Character>> out = new HashMap<Character, List<Character>>();
        for (List<Character> values : byNormalized.values()) {
            List<Character> frozen = Collections.unmodifiableList(values);
            for (Character value : values) out.put(value, frozen);
        }
        return Collections.unmodifiableMap(out);
    }

    private static void addAccentAlternatives(Map<String, List<Character>> byNormalized,
                                              int first,
                                              int last) {
        for (int codePoint = first; codePoint <= last; codePoint++) {
            char c = (char) codePoint;
            if (!Character.isLetter(c)) continue;
            char lower = Character.toLowerCase(c);
            String normalized = TranslationData.accentNormForScramble(String.valueOf(lower))
                    .toLowerCase(Locale.ROOT);
            if (normalized.length() != 1 || normalized.charAt(0) < 'a' || normalized.charAt(0) > 'z') continue;
            List<Character> values = byNormalized.get(normalized);
            if (values == null) {
                values = new ArrayList<Character>();
                byNormalized.put(normalized, values);
            }
            Character value = Character.valueOf(lower);
            if (!values.contains(value)) values.add(value);
        }
    }
}
