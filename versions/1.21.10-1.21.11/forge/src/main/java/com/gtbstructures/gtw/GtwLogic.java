package com.gtbstructures.gtw;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Platform-neutral Guess The Wordhint logic.
 */
public final class GtwLogic {

    private static final Map<String, String> LANG_LOOKUP;
    private static final List<String> ALL_CODES;
    private static final int ACCENT_NORM_CACHE_LIMIT = 512;
    private static final Map<String, String> ACCENT_NORM_CACHE =
            new LinkedHashMap<String, String>(ACCENT_NORM_CACHE_LIMIT, 0.75f, true) {
                private static final long serialVersionUID = 1L;

                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > ACCENT_NORM_CACHE_LIMIT;
                }
            };
    private static long accentNormComputations;

    static {
        Map<String, String> lookup = new LinkedHashMap<String, String>();
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
        List<String> codes = new ArrayList<String>();
        for (Object[] row : table) {
            String code = (String) row[0];
            String name = (String) row[1];
            String mod = (String) row[2];
            lookup.put(code, mod);
            lookup.put(name, mod);
            if ("ptbr".equals(code)) {
                lookup.put("pt_br", mod);
                lookup.put("pt-br", mod);
            }
            if ("zh_cn".equals(code)) {
                lookup.put("zhcn", mod);
                lookup.put("zh-cn", mod);
            }
            if ("zh_tw".equals(code)) {
                lookup.put("zhtw", mod);
                lookup.put("zh-tw", mod);
            }
            if ("ir".equals(code)) {
                lookup.put("pirate speak", mod);
            }
            codes.add(code);
        }
        LANG_LOOKUP = Collections.unmodifiableMap(lookup);
        ALL_CODES = Collections.unmodifiableList(codes);
    }

    private GtwLogic() {
    }

    public static String[] buildProgressiveHints(String text, int maxReveals, Random rng) {
        int length = text.length();
        char[] base = new char[length];
        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            base[i] = c == ' ' ? ' ' : '_';
        }
        List<Integer> revealable = new ArrayList<Integer>();
        for (int i = 0; i < length; i++) {
            if (text.charAt(i) != ' ') {
                revealable.add(i);
            }
        }
        Collections.shuffle(revealable, rng);

        int levels = Math.min(maxReveals, revealable.size());
        if (levels <= 0) {
            return new String[] { new String(base) };
        }
        String[] out = new String[levels];
        char[] current = base.clone();
        for (int level = 0; level < levels; level++) {
            int index = revealable.get(level);
            current[index] = text.charAt(index);
            out[level] = new String(current);
        }
        return out;
    }

    public static String structureNotation(String text) {
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

    public static boolean gtbMatches(String userInput, String translation, String lang) {
        if (userInput == null || translation == null) return false;
        if ("co".equals(lang)) {
            return userInput.trim().toLowerCase(Locale.ROOT).equals(translation.toLowerCase(Locale.ROOT));
        }
        String trimmedInput = userInput.trim();
        String input = baseNorm(trimmedInput);
        String original = baseNorm(translation);

        if (input.equals(original)) return true;

        if (translation.length() > 0 && translation.charAt(translation.length() - 1) == 's') {
            if (input.equals(baseNorm(translation.substring(0, translation.length() - 1)))) return true;
        }
        if (trimmedInput.endsWith("s") && input.equals(original + "s")) return true;

        if (removeWhitespace(input).equals(removeWhitespace(original))) return true;

        return accentNorm(input).equals(accentNorm(original));
    }

    public static List<LegalShortcutCandidate> legalShortcutCandidates(
            String value, boolean preferExtendedLatin) {
        if (value == null) return Collections.emptyList();
        String raw = value;
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

    public static String resolveLanguage(String input) {
        if (input == null) return null;
        String key = input.trim().toLowerCase(Locale.ROOT);
        return LANG_LOOKUP.get(key);
    }

    public static String listAllCodes() {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String code : ALL_CODES) {
            if (!first) sb.append(", ");
            first = false;
            sb.append(code);
        }
        return sb.toString();
    }

    public static List<String> languageCompletionOptions() {
        ArrayList<String> options = new ArrayList<String>(LANG_LOOKUP.keySet());
        Collections.sort(options);
        return options;
    }

    public static List<String> matching(String prefix, String... options) {
        List<String> out = new ArrayList<String>();
        String normalizedPrefix = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(normalizedPrefix)) {
                out.add(option);
            }
        }
        return out;
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

    private static String baseNorm(String value) {
        return value.toLowerCase(Locale.ROOT).replace('\u0131', 'i');
    }

    private static String accentNorm(String value) {
        synchronized (ACCENT_NORM_CACHE) {
            String cached = ACCENT_NORM_CACHE.get(value);
            if (cached != null) return cached;
        }

        String result = computeAccentNorm(value);
        synchronized (ACCENT_NORM_CACHE) {
            String cached = ACCENT_NORM_CACHE.get(value);
            if (cached != null) return cached;
            ACCENT_NORM_CACHE.put(value, result);
            accentNormComputations++;
        }
        return result;
    }

    private static String computeAccentNorm(String value) {
        boolean ascii = true;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) > 0x7f) {
                ascii = false;
                break;
            }
        }
        if (ascii) return value;

        final char feminineOrdinalSentinel = '\ue000';
        final char masculineOrdinalSentinel = '\ue001';
        String prepared = value.replace('\u00aa', feminineOrdinalSentinel)
                .replace('\u00ba', masculineOrdinalSentinel)
                .replace('\u0142', 'l').replace('\u0141', 'l');
        String normalized = normalizePreservingFullWidthLatin(prepared, null);
        StringBuilder sb = new StringBuilder(normalized.length());
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (c >= 0x0300 && c <= 0x036f) continue;
            sb.append(c);
        }
        return Normalizer.normalize(sb.toString(), Normalizer.Form.NFC)
                .replace(feminineOrdinalSentinel, '\u00aa')
                .replace(masculineOrdinalSentinel, '\u00ba');
    }

    private static String normalizePreservingFullWidthLatin(String prepared, int[] callCount) {
        StringBuilder normalized = new StringBuilder(prepared.length());
        StringBuilder normalizableRun = new StringBuilder(prepared.length());
        for (int offset = 0; offset < prepared.length();) {
            int codePoint = prepared.codePointAt(offset);
            if (isFullWidthLatin(codePoint)) {
                appendNormalizedRun(normalized, normalizableRun, callCount);
                normalized.appendCodePoint(codePoint);
            } else {
                normalizableRun.appendCodePoint(codePoint);
            }
            offset += Character.charCount(codePoint);
        }
        appendNormalizedRun(normalized, normalizableRun, callCount);
        return normalized.toString();
    }

    private static void appendNormalizedRun(
            StringBuilder normalized, StringBuilder run, int[] callCount) {
        if (run.length() == 0) return;
        normalized.append(Normalizer.normalize(run, Normalizer.Form.NFKD));
        run.setLength(0);
        if (callCount != null) callCount[0]++;
    }

    static String accentNormForTest(String value) {
        return accentNorm(value);
    }

    static int accentNormCacheLimitForTest() {
        return ACCENT_NORM_CACHE_LIMIT;
    }

    static int accentNormCacheSizeForTest() {
        synchronized (ACCENT_NORM_CACHE) {
            return ACCENT_NORM_CACHE.size();
        }
    }

    static long accentNormComputationCountForTest() {
        synchronized (ACCENT_NORM_CACHE) {
            return accentNormComputations;
        }
    }

    static int accentNormNfkdCallCountForTest(String value) {
        String prepared = value.replace('\u00aa', '\ue000')
                .replace('\u00ba', '\ue001')
                .replace('\u0142', 'l').replace('\u0141', 'l');
        int[] callCount = {0};
        normalizePreservingFullWidthLatin(prepared, callCount);
        return callCount[0];
    }

    static void clearAccentNormCacheForTest() {
        synchronized (ACCENT_NORM_CACHE) {
            ACCENT_NORM_CACHE.clear();
            accentNormComputations = 0L;
        }
    }

    private static boolean isFullWidthLatin(int codePoint) {
        return (codePoint >= 0xFF21 && codePoint <= 0xFF3A)
                || (codePoint >= 0xFF41 && codePoint <= 0xFF5A);
    }

    private static boolean hasDisplayablePreservedWhitespace(String value) {
        return value != null
                && value.equals(value.trim())
                && value.indexOf('\n') < 0
                && value.indexOf('\r') < 0;
    }

    private static String removeWhitespace(String value) {
        if (value == null || value.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!Character.isWhitespace(c)) {
                sb.append(c);
            }
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
}
