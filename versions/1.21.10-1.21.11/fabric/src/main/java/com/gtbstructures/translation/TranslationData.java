package com.gtbstructures.translation;

import com.gtbstructures.gtw.GtwLogic;
import com.gtbstructures.stats.ScoreDelta;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Logger;

/**
 * Loads and queries Hypixel GTB theme/translation data, used to deduce builder
 * point values for non-English rounds.
 *
 * Source JSON: lib/source/translations-data.json from
 * https://github.com/zmh-program/gtb_platform. The mod ships a bundled snapshot
 * in its resources and tries to refresh from GitHub once per launch (async).
 */
public class TranslationData {

    private static final Logger LOGGER = Logger.getLogger("GtbStructures");

    private static final String REMOTE_URL =
            "https://raw.githubusercontent.com/zmh-program/gtb_platform/main/lib/source/translations-data.json";
    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int READ_TIMEOUT_MS = 8000;
    private static final int LONG_THEME_PREFIX_MIN_LENGTH = 20;

    private static final class LongThemePrefixCandidate {
        final String displayedTheme;
        final Set<String> englishOrigins;

        LongThemePrefixCandidate(String displayedTheme, Set<String> englishOrigins) {
            this.displayedTheme = displayedTheme;
            this.englishOrigins = englishOrigins;
        }
    }

    /**
     * Immutable snapshot of the parsed translation tables, swapped in atomically
     * by {@link #parse(String)} so readers (lookupBuilderPoints, isKnownEnglishTheme,
     * findMatchingDisplayedThemes, …) always see a fully-built, consistent view.
     * Previously these were mutable maps cleared+repopulated in place, which made
     * concurrent readers observe partially-rebuilt indexes during refresh.
     */
    private static final class Snapshot {
        /** Per-language: translation string -> set of English themes that map to it. Case-sensitive, NFC-normalized. */
        final Map<String, Map<String, Set<String>>> index;
        /** All English theme strings, lowercased -> original (NFC) case. */
        final Map<String, String> englishThemes;
        /** Shortcut metadata keyed by lowercased English theme. */
        final Map<String, ShortcutThemeData> shortcutThemes;
        /**
         * Length-bucketed view of {@link #index}: lang -> length -> (translation -> englishOrigins).
         * A hint pattern of length L can only match candidates of length L, so callers walk just
         * the matching bucket instead of the full per-language map. Inner Map and Set references
         * are shared with {@link #index} (no additional String/Set allocation at parse time).
         */
        final Map<String, Map<Integer, Map<String, Set<String>>>> indexByLen;
        /** Length-bucketed view of {@link #englishThemes} values. Strings shared with {@link #englishThemes}. */
        final Map<Integer, List<String>> englishByLen;
        /**
         * Per-language precomputed set of English-origin themes covered by translations in that
         * language. Lets {@link #findMatchingDisplayedThemes} skip the full langIndex walk that
         * previously rebuilt this set on every lookup.
         */
        final Map<String, Set<String>> coveredEnglishByLang;
        /** Long displayed themes keyed by language and their first 20 characters. */
        final Map<String, Map<String, List<LongThemePrefixCandidate>>> longThemePrefixes;
        final ActiveGuessIndex activeGuessIndex;

        Snapshot(Map<String, Map<String, Set<String>>> index,
                 Map<String, String> englishThemes,
                 Map<String, ShortcutThemeData> shortcutThemes,
                 Map<String, Map<Integer, Map<String, Set<String>>>> indexByLen,
                 Map<Integer, List<String>> englishByLen,
                 Map<String, Set<String>> coveredEnglishByLang,
                 Map<String, Map<String, List<LongThemePrefixCandidate>>> longThemePrefixes,
                 ActiveGuessIndex activeGuessIndex) {
            this.index = index;
            this.englishThemes = englishThemes;
            this.shortcutThemes = shortcutThemes;
            this.indexByLen = indexByLen;
            this.englishByLen = englishByLen;
            this.coveredEnglishByLang = coveredEnglishByLang;
            this.longThemePrefixes = longThemePrefixes;
            this.activeGuessIndex = activeGuessIndex;
        }

        static final Snapshot EMPTY = new Snapshot(
                Collections.<String, Map<String, Set<String>>>emptyMap(),
                Collections.<String, String>emptyMap(),
                Collections.<String, ShortcutThemeData>emptyMap(),
                Collections.<String, Map<Integer, Map<String, Set<String>>>>emptyMap(),
                Collections.<Integer, List<String>>emptyMap(),
                Collections.<String, Set<String>>emptyMap(),
                Collections.<String, Map<String, List<LongThemePrefixCandidate>>>emptyMap(),
                ActiveGuessIndex.EMPTY);
    }

    private static final class ActiveGuessIndex {
        static final ActiveGuessIndex EMPTY = new ActiveGuessIndex(
                Collections.<String, Set<String>>emptyMap(), Collections.<String, Set<String>>emptyMap(),
                Collections.<String, Set<String>>emptyMap(), Collections.<String, Set<String>>emptyMap(),
                Collections.<String, Set<String>>emptyMap(), Collections.<String, Set<String>>emptyMap(),
                Collections.<String, Set<String>>emptyMap(), Collections.<String, List<String>>emptyMap());

        final Map<String, Set<String>> directThemes;
        final Map<String, Set<String>> whitespaceThemes;
        final Map<String, Set<String>> accentThemes;
        final Map<String, Set<String>> originalEndingSDirectThemes;
        final Map<String, Set<String>> originalEndingSWhitespaceThemes;
        final Map<String, Set<String>> originalEndingSAccentThemes;
        final Map<String, Set<String>> complementThemes;
        final Map<String, List<String>> candidatesByThemeSet;

        ActiveGuessIndex(Map<String, Set<String>> directThemes,
                         Map<String, Set<String>> whitespaceThemes,
                         Map<String, Set<String>> accentThemes,
                         Map<String, Set<String>> originalEndingSDirectThemes,
                         Map<String, Set<String>> originalEndingSWhitespaceThemes,
                         Map<String, Set<String>> originalEndingSAccentThemes,
                         Map<String, Set<String>> complementThemes,
                         Map<String, List<String>> candidatesByThemeSet) {
            this.directThemes = directThemes;
            this.whitespaceThemes = whitespaceThemes;
            this.accentThemes = accentThemes;
            this.originalEndingSDirectThemes = originalEndingSDirectThemes;
            this.originalEndingSWhitespaceThemes = originalEndingSWhitespaceThemes;
            this.originalEndingSAccentThemes = originalEndingSAccentThemes;
            this.complementThemes = complementThemes;
            this.candidatesByThemeSet = candidatesByThemeSet;
        }
    }

    private static volatile Snapshot SNAPSHOT = Snapshot.EMPTY;
    private static volatile String currentDataHash = "";
    private static volatile long currentDataObservedAt;

    private static volatile boolean started = false;
    private static final int RULED_OUT_GUESS_CACHE_LIMIT = 512;
    private static final Map<String, Set<String>> RULED_OUT_GUESS_CACHE =
            Collections.synchronizedMap(new LinkedHashMap<String, Set<String>>(128, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Set<String>> eldest) {
                    return size() > RULED_OUT_GUESS_CACHE_LIMIT;
                }
            });

    /**
     * Configure instance-local history, load the best local snapshot, then
     * refresh from GitHub. Safe to call multiple times; only the first call
     * starts the loader thread.
     */
    public static synchronized void initAsync(File gameDir) {
        TranslationHistory.configure(gameDir);
        if (started) return;
        started = true;

        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                loadFromBestSource();
                TranslationHistory.StoredData refreshed = tryRefresh();
                if (refreshed != null) publish(refreshed);
            }
        }, "GtbStructures-TranslationLoader");
        t.setDaemon(true);
        t.start();
    }

    public static synchronized void initAsync() {
        initAsync(null);
    }

    public static synchronized void ensureLocalLoaded() {
        if (!SNAPSHOT.index.isEmpty()) return;
        loadFromBestSource();
    }

    private static TranslationHistory.StoredData tryRefresh() {
        if (!TranslationHistory.isConfigured()) return null;
        HttpURLConnection conn = null;
        try {
            URL url = new URL(REMOTE_URL);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("User-Agent", "GtbStructures/1.1");
            int code = conn.getResponseCode();
            if (code != 200) {
                LOGGER.warning("Translation data refresh: HTTP " + code);
                return null;
            }
            InputStream in = conn.getInputStream();
            byte[] body = readAll(in);
            in.close();
            TranslationHistory.StoredData stored = TranslationHistory.recordRemote(
                    new String(body, "UTF-8"), System.currentTimeMillis());
            if (stored != null) {
                LOGGER.info("Translation data refreshed and archived (" + body.length + " bytes)");
            }
            return stored;
        } catch (Exception e) {
            LOGGER.warning("Translation data refresh failed: " + e.getMessage());
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static void loadFromBestSource() {
        try {
            InputStream in = TranslationData.class.getResourceAsStream("/translations-data.json");
            if (in == null) {
                LOGGER.warning("Bundled translations-data.json not found; builder fork lookup disabled");
                return;
            }
            String bundled = new String(readAll(in), "UTF-8");
            in.close();
            publish(TranslationHistory.prepareBundled(bundled));
            Snapshot snap = SNAPSHOT;
            LOGGER.info("Loaded translations: " + snap.index.size()
                    + " languages, " + snap.englishThemes.size() + " English themes");
        } catch (Exception e) {
            LOGGER.warning("Failed to load translation data: " + e.getMessage());
        }
    }

    private static void publish(TranslationHistory.StoredData data) {
        if (data == null || data.json == null) return;
        parse(data.json);
        currentDataHash = data.hash;
        currentDataObservedAt = data.validFrom;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] tmp = new byte[8192];
        int n;
        while ((n = in.read(tmp)) > 0) buf.write(tmp, 0, n);
        return buf.toByteArray();
    }

    // ── JSON parser (purpose-built; avoids pulling in Gson) ─────────────

    private static class Cursor { int i; }

    private static class ParsedTranslation {
        String languageCode;
        String translation;
        boolean explicitlyUnapproved;
    }

    static void parse(String json) {
        // Build into local maps so concurrent readers never observe a half-built
        // index. Only after parsing completes do we publish via a single
        // volatile assignment to SNAPSHOT.
        TranslationHistory.setCurrentThemeUniverse(json);
        Map<String, Map<String, Set<String>>> index = new HashMap<String, Map<String, Set<String>>>();
        Map<String, Map<String, Set<String>>> guessIndex = new HashMap<String, Map<String, Set<String>>>();
        Map<String, String> englishThemes = new HashMap<String, String>();
        Map<String, ShortcutThemeData> shortcutThemes = new HashMap<String, ShortcutThemeData>();

        Cursor cur = new Cursor();
        cur.i = 0;
        skipWs(json, cur);
        if (cur.i >= json.length() || json.charAt(cur.i) != '[') {
            SNAPSHOT = buildSnapshot(index, guessIndex, englishThemes, shortcutThemes);
            currentDataHash = TranslationHistory.sha256Utf8(json);
            currentDataObservedAt = 0L;
            RULED_OUT_GUESS_CACHE.clear();
            return;
        }
        cur.i++; // consume '['
        skipWs(json, cur);

        while (cur.i < json.length()) {
            skipWs(json, cur);
            char c = json.charAt(cur.i);
            if (c == ']') { cur.i++; break; }
            if (c == ',') { cur.i++; continue; }
            if (c == '{') {
                parseThemeObject(json, cur, index, guessIndex, englishThemes, shortcutThemes);
            } else {
                // Unknown token; skip to keep parser robust.
                cur.i++;
            }
        }

        SNAPSHOT = buildSnapshot(index, guessIndex, englishThemes, shortcutThemes);
        currentDataHash = TranslationHistory.sha256Utf8(json);
        currentDataObservedAt = 0L;
        RULED_OUT_GUESS_CACHE.clear();
    }

    /**
     * Build the immutable Snapshot from freshly parsed flat maps. Constructs the
     * length-bucketed views and per-language coveredEnglish sets, all sharing
     * String/Set references with the flat maps so memory cost is just the
     * additional container nodes.
     */
    private static Snapshot buildSnapshot(Map<String, Map<String, Set<String>>> index,
                                          Map<String, Map<String, Set<String>>> guessIndex,
                                          Map<String, String> englishThemes,
                                          Map<String, ShortcutThemeData> shortcutThemes) {
        Map<String, Map<Integer, Map<String, Set<String>>>> indexByLen =
                new HashMap<String, Map<Integer, Map<String, Set<String>>>>();
        Map<String, Set<String>> coveredByLang = new HashMap<String, Set<String>>();

        for (Map.Entry<String, Map<String, Set<String>>> langE : index.entrySet()) {
            Map<Integer, Map<String, Set<String>>> lenMap = new HashMap<Integer, Map<String, Set<String>>>();
            Set<String> covered = new HashSet<String>();
            for (Map.Entry<String, Set<String>> trE : langE.getValue().entrySet()) {
                int len = trE.getKey().length();
                Map<String, Set<String>> bucket = lenMap.get(len);
                if (bucket == null) {
                    bucket = new HashMap<String, Set<String>>();
                    lenMap.put(len, bucket);
                }
                bucket.put(trE.getKey(), trE.getValue());
                covered.addAll(trE.getValue());
            }
            // Freeze inner buckets so callers can't accidentally mutate shared state.
            Map<Integer, Map<String, Set<String>>> lenView =
                    new HashMap<Integer, Map<String, Set<String>>>(lenMap.size());
            for (Map.Entry<Integer, Map<String, Set<String>>> e : lenMap.entrySet()) {
                lenView.put(e.getKey(), Collections.unmodifiableMap(e.getValue()));
            }
            indexByLen.put(langE.getKey(), Collections.unmodifiableMap(lenView));
            coveredByLang.put(langE.getKey(), Collections.unmodifiableSet(covered));
        }

        Map<Integer, List<String>> englishByLen = new HashMap<Integer, List<String>>();
        for (String enTheme : englishThemes.values()) {
            int len = enTheme.length();
            List<String> bucket = englishByLen.get(len);
            if (bucket == null) {
                bucket = new ArrayList<String>();
                englishByLen.put(len, bucket);
            }
            bucket.add(enTheme);
        }
        Map<Integer, List<String>> englishByLenView =
                new HashMap<Integer, List<String>>(englishByLen.size());
        for (Map.Entry<Integer, List<String>> e : englishByLen.entrySet()) {
            englishByLenView.put(e.getKey(), Collections.unmodifiableList(e.getValue()));
        }
        ActiveGuessIndex activeGuessIndex = buildActiveGuessIndex(
                index, guessIndex, englishThemes);
        Map<String, Map<String, List<LongThemePrefixCandidate>>> longThemePrefixes =
                buildLongThemePrefixIndex(index, englishThemes);

        return new Snapshot(
                Collections.unmodifiableMap(index),
                Collections.unmodifiableMap(englishThemes),
                Collections.unmodifiableMap(shortcutThemes),
                Collections.unmodifiableMap(indexByLen),
                Collections.unmodifiableMap(englishByLenView),
                Collections.unmodifiableMap(coveredByLang),
                longThemePrefixes,
                activeGuessIndex);
    }

    private static Map<String, Map<String, List<LongThemePrefixCandidate>>>
    buildLongThemePrefixIndex(Map<String, Map<String, Set<String>>> index,
                              Map<String, String> englishThemes) {
        Map<String, Map<String, List<LongThemePrefixCandidate>>> out =
                new HashMap<String, Map<String, List<LongThemePrefixCandidate>>>();
        for (String englishTheme : englishThemes.values()) {
            addLongThemePrefix(out, "en", englishTheme, Collections.singleton(englishTheme));
        }
        for (Map.Entry<String, Map<String, Set<String>>> language : index.entrySet()) {
            for (Map.Entry<String, Set<String>> translation : language.getValue().entrySet()) {
                addLongThemePrefix(out, language.getKey(), translation.getKey(), translation.getValue());
            }
        }

        Map<String, Map<String, List<LongThemePrefixCandidate>>> frozen =
                new HashMap<String, Map<String, List<LongThemePrefixCandidate>>>(out.size());
        for (Map.Entry<String, Map<String, List<LongThemePrefixCandidate>>> language : out.entrySet()) {
            Map<String, List<LongThemePrefixCandidate>> prefixes =
                    new HashMap<String, List<LongThemePrefixCandidate>>(language.getValue().size());
            for (Map.Entry<String, List<LongThemePrefixCandidate>> prefix
                    : language.getValue().entrySet()) {
                prefixes.put(prefix.getKey(), Collections.unmodifiableList(prefix.getValue()));
            }
            frozen.put(language.getKey(), Collections.unmodifiableMap(prefixes));
        }
        return Collections.unmodifiableMap(frozen);
    }

    private static void addLongThemePrefix(
            Map<String, Map<String, List<LongThemePrefixCandidate>>> index,
            String langCode, String displayedTheme, Set<String> englishOrigins) {
        if (displayedTheme == null || displayedTheme.length() <= LONG_THEME_PREFIX_MIN_LENGTH
                || englishOrigins == null || englishOrigins.isEmpty()) return;
        Map<String, List<LongThemePrefixCandidate>> language = index.get(langCode);
        if (language == null) {
            language = new HashMap<String, List<LongThemePrefixCandidate>>();
            index.put(langCode, language);
        }
        String prefix = displayedTheme.substring(0, LONG_THEME_PREFIX_MIN_LENGTH);
        List<LongThemePrefixCandidate> candidates = language.get(prefix);
        if (candidates == null) {
            candidates = new ArrayList<LongThemePrefixCandidate>();
            language.put(prefix, candidates);
        }
        candidates.add(new LongThemePrefixCandidate(displayedTheme, englishOrigins));
    }

    private static ActiveGuessIndex buildActiveGuessIndex(
            Map<String, Map<String, Set<String>>> index,
            Map<String, Map<String, Set<String>>> guessIndex,
            Map<String, String> englishThemes) {
        Map<String, Set<String>> direct = new HashMap<String, Set<String>>();
        Map<String, Set<String>> whitespace = new HashMap<String, Set<String>>();
        Map<String, Set<String>> accent = new HashMap<String, Set<String>>();
        Map<String, Set<String>> endingSDirect = new HashMap<String, Set<String>>();
        Map<String, Set<String>> endingSWhitespace = new HashMap<String, Set<String>>();
        Map<String, Set<String>> endingSAccent = new HashMap<String, Set<String>>();
        Map<String, Set<String>> complement = new HashMap<String, Set<String>>();
        Set<String> candidates = new LinkedHashSet<String>();

        for (String englishTheme : englishThemes.values()) {
            Set<String> theme = Collections.singleton(englishTheme);
            addActiveGuessBase(direct, whitespace, accent, endingSDirect,
                    endingSWhitespace, endingSAccent, englishTheme, theme);
            candidates.add(englishTheme.toLowerCase(Locale.ROOT));
        }
        for (Map<String, Set<String>> language : index.values()) {
            for (Map.Entry<String, Set<String>> translation : language.entrySet()) {
                addActiveGuessBase(direct, whitespace, accent, endingSDirect,
                        endingSWhitespace, endingSAccent,
                        translation.getKey(), translation.getValue());
                candidates.add(translation.getKey().toLowerCase(Locale.ROOT));
            }
        }
        Map<String, Set<String>> complementTranslations = guessIndex.get("co");
        if (complementTranslations != null) {
            for (Map.Entry<String, Set<String>> translation : complementTranslations.entrySet()) {
                String candidate = translation.getKey().toLowerCase(Locale.ROOT);
                addThemes(complement, candidate, translation.getValue());
                candidates.add(candidate);
            }
        }

        Map<String, List<String>> byThemeSet = new HashMap<String, List<String>>();
        for (String candidate : candidates) {
            Set<String> themes = activeGuessThemes(candidate, direct, whitespace, accent,
                    endingSDirect, endingSWhitespace, endingSAccent, complement);
            if (themes.isEmpty()) continue;
            String key = activeThemeSetKey(themes);
            List<String> values = byThemeSet.get(key);
            if (values == null) {
                values = new ArrayList<String>();
                byThemeSet.put(key, values);
            }
            values.add(candidate);
        }

        return new ActiveGuessIndex(
                freezeThemeLookup(direct),
                freezeThemeLookup(whitespace),
                freezeThemeLookup(accent),
                freezeThemeLookup(endingSDirect),
                freezeThemeLookup(endingSWhitespace),
                freezeThemeLookup(endingSAccent),
                freezeThemeLookup(complement),
                freezeCandidateLookup(byThemeSet));
    }

    private static void addActiveGuessBase(Map<String, Set<String>> direct,
                                           Map<String, Set<String>> whitespace,
                                           Map<String, Set<String>> accent,
                                           Map<String, Set<String>> endingSDirect,
                                           Map<String, Set<String>> endingSWhitespace,
                                           Map<String, Set<String>> endingSAccent,
                                           String raw,
                                           Set<String> themes) {
        if (raw == null || raw.isEmpty() || themes == null || themes.isEmpty()) return;
        String base = activeBaseNorm(raw);
        addThemes(direct, base, themes);
        if (raw.charAt(raw.length() - 1) == 's') {
            addThemes(direct, activeBaseNorm(raw.substring(0, raw.length() - 1)), themes);
        }
        addThemes(direct, base + "s", themes);
        addThemes(whitespace, removeActiveWhitespace(base), themes);
        addThemes(accent, activeAccentNorm(base), themes);
        if (base.endsWith("s")) {
            addThemes(endingSDirect, base, themes);
            addThemes(endingSWhitespace, removeActiveWhitespace(base), themes);
            addThemes(endingSAccent, activeAccentNorm(base), themes);
        }
    }

    private static void addThemes(Map<String, Set<String>> target,
                                  String key,
                                  Set<String> themes) {
        if (key == null || key.isEmpty()) return;
        Set<String> values = target.get(key);
        if (values == null) {
            values = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
            target.put(key, values);
        }
        values.addAll(themes);
    }

    private static Map<String, Set<String>> freezeThemeLookup(Map<String, Set<String>> source) {
        Map<String, Set<String>> out = new HashMap<String, Set<String>>(source.size());
        for (Map.Entry<String, Set<String>> entry : source.entrySet()) {
            out.put(entry.getKey(), Collections.unmodifiableSet(entry.getValue()));
        }
        return Collections.unmodifiableMap(out);
    }

    private static Map<String, List<String>> freezeCandidateLookup(Map<String, List<String>> source) {
        Map<String, List<String>> out = new HashMap<String, List<String>>(source.size());
        for (Map.Entry<String, List<String>> entry : source.entrySet()) {
            out.put(entry.getKey(), Collections.unmodifiableList(entry.getValue()));
        }
        return Collections.unmodifiableMap(out);
    }

    private static Set<String> activeGuessThemes(String guess,
                                                 Map<String, Set<String>> direct,
                                                 Map<String, Set<String>> whitespace,
                                                 Map<String, Set<String>> accent,
                                                 Map<String, Set<String>> endingSDirect,
                                                 Map<String, Set<String>> endingSWhitespace,
                                                 Map<String, Set<String>> endingSAccent,
                                                 Map<String, Set<String>> complement) {
        if (guess == null) return Collections.emptySet();
        String trimmed = guess.trim();
        String input = activeBaseNorm(trimmed);
        if (input.isEmpty()) return Collections.emptySet();
        boolean uppercaseTrailingS = trimmed.charAt(trimmed.length() - 1) == 'S';
        Set<String> themes = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        addLookupThemes(themes, (uppercaseTrailingS ? endingSDirect : direct).get(input));
        addLookupThemes(themes, (uppercaseTrailingS ? endingSWhitespace : whitespace)
                .get(removeActiveWhitespace(input)));
        addLookupThemes(themes, (uppercaseTrailingS ? endingSAccent : accent)
                .get(activeAccentNorm(input)));
        addLookupThemes(themes, complement.get(trimmed.toLowerCase(Locale.ROOT)));
        return themes;
    }

    private static void addLookupThemes(Set<String> target, Set<String> source) {
        if (source != null) target.addAll(source);
    }

    private static String activeBaseNorm(String value) {
        return value.toLowerCase(Locale.ROOT).replace('\u0131', 'i');
    }

    private static String removeActiveWhitespace(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!Character.isWhitespace(c)) out.append(c);
        }
        return out.toString();
    }

    private static String activeAccentNorm(String value) {
        final char feminineOrdinalSentinel = '\ue000';
        final char masculineOrdinalSentinel = '\ue001';
        String prepared = value.replace('\u00aa', feminineOrdinalSentinel)
                .replace('\u00ba', masculineOrdinalSentinel)
                .replace('\u0142', 'l').replace('\u0141', 'l');
        StringBuilder decomposed = new StringBuilder(prepared.length());
        for (int offset = 0; offset < prepared.length();) {
            int codePoint = prepared.codePointAt(offset);
            if (isFullWidthLatin(codePoint)) {
                decomposed.appendCodePoint(codePoint);
            } else {
                decomposed.append(Normalizer.normalize(
                        new String(Character.toChars(codePoint)), Normalizer.Form.NFKD));
            }
            offset += Character.charCount(codePoint);
        }
        StringBuilder out = new StringBuilder(decomposed.length());
        for (int i = 0; i < decomposed.length(); i++) {
            char c = decomposed.charAt(i);
            if (c < 0x0300 || c > 0x036f) out.append(c);
        }
        return Normalizer.normalize(out.toString(), Normalizer.Form.NFC)
                .replace(feminineOrdinalSentinel, '\u00aa')
                .replace(masculineOrdinalSentinel, '\u00ba');
    }

    private static boolean isFullWidthLatin(int codePoint) {
        return (codePoint >= 0xFF21 && codePoint <= 0xFF3A)
                || (codePoint >= 0xFF41 && codePoint <= 0xFF5A);
    }

    private static String activeThemeSetKey(Collection<String> themes) {
        TreeSet<String> sorted = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        sorted.addAll(themes);
        StringBuilder key = new StringBuilder();
        for (String theme : sorted) {
            if (key.length() > 0) key.append('\u0000');
            key.append(theme.toLowerCase(Locale.ROOT));
        }
        return key.toString();
    }

    /** Parse one top-level theme object: extracts "theme" and walks "translations". */
    private static void parseThemeObject(String json, Cursor cur,
                                         Map<String, Map<String, Set<String>>> index,
                                         Map<String, Map<String, Set<String>>> guessIndex,
                                         Map<String, String> englishThemes,
                                         Map<String, ShortcutThemeData> shortcutThemes) {
        cur.i++; // consume '{'
        String englishTheme = null;
        long themeId = 0L;
        String selectedShortcut = null;
        List<ShortcutMultiwordData> multiwords = new ArrayList<ShortcutMultiwordData>();
        List<ParsedTranslation> pendingTranslations = new ArrayList<ParsedTranslation>();

        while (cur.i < json.length()) {
            skipWs(json, cur);
            char c = json.charAt(cur.i);
            if (c == '}') { cur.i++; break; }
            if (c == ',') { cur.i++; continue; }
            if (c != '"') { cur.i++; continue; }

            String key = readString(json, cur);
            skipWs(json, cur);
            if (cur.i >= json.length() || json.charAt(cur.i) != ':') continue;
            cur.i++; // consume ':'
            skipWs(json, cur);

            if ("theme".equals(key)) {
                if (cur.i < json.length() && json.charAt(cur.i) == '"') {
                    englishTheme = readString(json, cur);
                } else {
                    skipValue(json, cur);
                }
            } else if ("id".equals(key)) {
                themeId = readLong(json, cur);
            } else if ("shortcut".equals(key)) {
                if (cur.i < json.length() && json.charAt(cur.i) == '"') {
                    selectedShortcut = readString(json, cur);
                } else {
                    skipValue(json, cur);
                }
            } else if ("multiwords".equals(key)) {
                parseMultiwordsArray(json, cur, multiwords);
            } else if ("translations".equals(key)) {
                parseTranslationsObject(json, cur, pendingTranslations);
            } else {
                skipValue(json, cur);
            }
        }

        if (englishTheme != null && !englishTheme.isEmpty()) {
            // NFC-normalize once at parse time so lookup-time matchesHint can compare
            // candidates byte-for-byte without normalizing each one (a hot path that
            // was previously running ~6k normalize calls per /gtbs showallmatching trigger).
            String nfcEnglish = Normalizer.normalize(englishTheme, Normalizer.Form.NFC);
            englishThemes.put(nfcEnglish.toLowerCase(), nfcEnglish);
            Map<String, String> translations = new HashMap<String, String>();
            for (ParsedTranslation pair : pendingTranslations) {
                String lang = pair.languageCode;
                String tr = pair.translation;
                if (pair.explicitlyUnapproved) {
                    tr = TranslationHistory.latestApproved(themeId, lang);
                }
                if (lang == null || tr == null || tr.isEmpty()) continue;
                String nfcTr = Normalizer.normalize(tr, Normalizer.Form.NFC);
                translations.put(lang, nfcTr);
                addTranslation(guessIndex, lang, nfcTr, nfcEnglish);
                if ("co".equals(lang)) continue; // complement-shortcut data, not a real language
                addTranslation(index, lang, nfcTr, nfcEnglish);
            }
            List<ShortcutMultiwordData> normalizedMultiwords =
                    new ArrayList<ShortcutMultiwordData>(multiwords.size());
            for (ShortcutMultiwordData multiword : multiwords) {
                if (multiword == null || multiword.value.isEmpty()) continue;
                Set<String> normalizedThemes = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
                for (String theme : multiword.themes) {
                    if (theme != null && !theme.isEmpty()) {
                        normalizedThemes.add(Normalizer.normalize(theme, Normalizer.Form.NFC));
                    }
                }
                normalizedMultiwords.add(new ShortcutMultiwordData(
                        Normalizer.normalize(multiword.value, Normalizer.Form.NFC),
                        normalizedThemes));
            }
            shortcutThemes.put(nfcEnglish.toLowerCase(), new ShortcutThemeData(
                    nfcEnglish,
                    selectedShortcut == null ? null
                            : Normalizer.normalize(selectedShortcut, Normalizer.Form.NFC),
                    normalizedMultiwords,
                    translations));
        }
    }

    /** Parse top-level multiword entries and the English themes each one covers. */
    private static void parseMultiwordsArray(String json, Cursor cur,
                                             List<ShortcutMultiwordData> out) {
        skipWs(json, cur);
        if (cur.i >= json.length() || json.charAt(cur.i) != '[') {
            skipValue(json, cur);
            return;
        }
        cur.i++;
        while (cur.i < json.length()) {
            skipWs(json, cur);
            char c = json.charAt(cur.i);
            if (c == ']') { cur.i++; break; }
            if (c == ',') { cur.i++; continue; }
            if (c != '{') { skipValue(json, cur); continue; }

            cur.i++;
            String multiword = null;
            Set<String> themes = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
            while (cur.i < json.length()) {
                skipWs(json, cur);
                char d = json.charAt(cur.i);
                if (d == '}') { cur.i++; break; }
                if (d == ',') { cur.i++; continue; }
                if (d != '"') { cur.i++; continue; }
                String key = readString(json, cur);
                skipWs(json, cur);
                if (cur.i >= json.length() || json.charAt(cur.i) != ':') continue;
                cur.i++;
                skipWs(json, cur);
                if ("multiword".equals(key) && cur.i < json.length()
                        && json.charAt(cur.i) == '"') {
                    multiword = readString(json, cur);
                } else if ("occurrences".equals(key)) {
                    parseOccurrenceThemesArray(json, cur, themes);
                } else {
                    skipValue(json, cur);
                }
            }
            if (multiword != null && !multiword.isEmpty()) {
                out.add(new ShortcutMultiwordData(multiword, themes));
            }
        }
    }

    private static void parseOccurrenceThemesArray(String json, Cursor cur, Set<String> out) {
        skipWs(json, cur);
        if (cur.i >= json.length() || json.charAt(cur.i) != '[') {
            skipValue(json, cur);
            return;
        }
        cur.i++;
        while (cur.i < json.length()) {
            skipWs(json, cur);
            char c = json.charAt(cur.i);
            if (c == ']') { cur.i++; break; }
            if (c == ',') { cur.i++; continue; }
            if (c != '{') { skipValue(json, cur); continue; }

            cur.i++;
            String theme = null;
            while (cur.i < json.length()) {
                skipWs(json, cur);
                char d = json.charAt(cur.i);
                if (d == '}') { cur.i++; break; }
                if (d == ',') { cur.i++; continue; }
                if (d != '"') { cur.i++; continue; }
                String key = readString(json, cur);
                skipWs(json, cur);
                if (cur.i >= json.length() || json.charAt(cur.i) != ':') continue;
                cur.i++;
                skipWs(json, cur);
                if ("theme".equals(key) && cur.i < json.length()
                        && json.charAt(cur.i) == '"') {
                    theme = readString(json, cur);
                } else {
                    skipValue(json, cur);
                }
            }
            if (theme != null && !theme.isEmpty()) out.add(theme);
        }
    }

    private static void addTranslation(Map<String, Map<String, Set<String>>> target,
                                       String lang,
                                       String translation,
                                       String englishTheme) {
        Map<String, Set<String>> langIndex = target.get(lang);
        if (langIndex == null) {
            langIndex = new HashMap<String, Set<String>>();
            target.put(lang, langIndex);
        }
        Set<String> themes = langIndex.get(translation);
        if (themes == null) {
            themes = new HashSet<String>();
            langIndex.put(translation, themes);
        }
        themes.add(englishTheme);
    }

    /** Parse the "translations" object: keys are language codes, values are objects with a "translation" string. */
    private static void parseTranslationsObject(String json, Cursor cur,
                                                List<ParsedTranslation> out) {
        skipWs(json, cur);
        if (cur.i >= json.length() || json.charAt(cur.i) != '{') {
            skipValue(json, cur);
            return;
        }
        cur.i++; // consume '{'

        while (cur.i < json.length()) {
            skipWs(json, cur);
            char c = json.charAt(cur.i);
            if (c == '}') { cur.i++; break; }
            if (c == ',') { cur.i++; continue; }
            if (c != '"') { cur.i++; continue; }

            String langKey = readString(json, cur);
            // The JSON's "en" key is actually Pirate Speak — rename it so the published
            // snapshot's index["pirate"] holds the real pirate translations. The genuine
            // English theme name is the top-level `theme` field (captured into snapshot.englishThemes).
            if ("en".equals(langKey)) langKey = "pirate";
            skipWs(json, cur);
            if (cur.i >= json.length() || json.charAt(cur.i) != ':') continue;
            cur.i++; // consume ':'
            skipWs(json, cur);

            // Value should be an object (or null for missing translations)
            if (cur.i < json.length() && json.charAt(cur.i) == '{') {
                ParsedTranslation tr = parseLangBody(json, cur);
                if (tr != null) {
                    tr.languageCode = langKey;
                    out.add(tr);
                }
            } else {
                skipValue(json, cur);
            }
        }
    }

    /** Inside a per-language object: extract approved "translation" strings. */
    private static ParsedTranslation parseLangBody(String json, Cursor cur) {
        cur.i++; // consume '{'
        String tr = null;
        Boolean approved = null;
        while (cur.i < json.length()) {
            skipWs(json, cur);
            char c = json.charAt(cur.i);
            if (c == '}') { cur.i++; break; }
            if (c == ',') { cur.i++; continue; }
            if (c != '"') { cur.i++; continue; }

            String k = readString(json, cur);
            skipWs(json, cur);
            if (cur.i >= json.length() || json.charAt(cur.i) != ':') continue;
            cur.i++;
            skipWs(json, cur);

            if ("translation".equals(k)) {
                if (cur.i < json.length() && json.charAt(cur.i) == '"') {
                    tr = readString(json, cur);
                } else {
                    skipValue(json, cur);
                }
            } else if ("is_approved".equals(k)) {
                approved = readBoolean(json, cur);
                if (approved == null) skipValue(json, cur);
            } else {
                skipValue(json, cur);
            }
        }
        if (tr == null) return null;
        ParsedTranslation parsed = new ParsedTranslation();
        parsed.translation = tr;
        parsed.explicitlyUnapproved = Boolean.FALSE.equals(approved);
        return parsed;
    }

    private static Boolean readBoolean(String json, Cursor cur) {
        if (json.startsWith("true", cur.i)) {
            cur.i += 4;
            return Boolean.TRUE;
        }
        if (json.startsWith("false", cur.i)) {
            cur.i += 5;
            return Boolean.FALSE;
        }
        return null;
    }

    private static long readLong(String json, Cursor cur) {
        skipWs(json, cur);
        int start = cur.i;
        if (cur.i < json.length() && json.charAt(cur.i) == '-') cur.i++;
        while (cur.i < json.length() && Character.isDigit(json.charAt(cur.i))) cur.i++;
        if (start == cur.i || (start + 1 == cur.i && json.charAt(start) == '-')) return 0L;
        try {
            return Long.parseLong(json.substring(start, cur.i));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static void skipValue(String json, Cursor cur) {
        skipWs(json, cur);
        if (cur.i >= json.length()) return;
        char c = json.charAt(cur.i);
        if (c == '"') { readString(json, cur); return; }
        if (c == '{' || c == '[') {
            char open = c;
            char close = (c == '{') ? '}' : ']';
            int depth = 0;
            while (cur.i < json.length()) {
                char d = json.charAt(cur.i);
                if (d == '"') { readString(json, cur); continue; }
                if (d == open) depth++;
                else if (d == close) { depth--; cur.i++; if (depth == 0) return; continue; }
                cur.i++;
            }
            return;
        }
        // scalar (number, true, false, null) — read until ,}]ws
        while (cur.i < json.length()) {
            char d = json.charAt(cur.i);
            if (d == ',' || d == '}' || d == ']' || d == ' ' || d == '\n' || d == '\r' || d == '\t') return;
            cur.i++;
        }
    }

    private static void skipWs(String s, Cursor cur) {
        while (cur.i < s.length()) {
            char c = s.charAt(cur.i);
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') cur.i++;
            else break;
        }
    }

    /** Read a quoted JSON string starting at `cur.i` pointing to the opening '"'. Advances cur past the closing '"'. */
    private static String readString(String s, Cursor cur) {
        StringBuilder sb = new StringBuilder();
        cur.i++; // consume '"'
        while (cur.i < s.length()) {
            char c = s.charAt(cur.i);
            if (c == '"') { cur.i++; return sb.toString(); }
            if (c == '\\' && cur.i + 1 < s.length()) {
                char esc = s.charAt(cur.i + 1);
                switch (esc) {
                    case '"':  sb.append('"');  cur.i += 2; break;
                    case '\\': sb.append('\\'); cur.i += 2; break;
                    case '/':  sb.append('/');  cur.i += 2; break;
                    case 'n':  sb.append('\n'); cur.i += 2; break;
                    case 'r':  sb.append('\r'); cur.i += 2; break;
                    case 't':  sb.append('\t'); cur.i += 2; break;
                    case 'b':  sb.append('\b'); cur.i += 2; break;
                    case 'f':  sb.append('\f'); cur.i += 2; break;
                    case 'u':
                        if (cur.i + 5 < s.length()) {
                            try {
                                int cp = Integer.parseInt(s.substring(cur.i + 2, cur.i + 6), 16);
                                sb.append((char) cp);
                            } catch (NumberFormatException ignored) {}
                            cur.i += 6;
                        } else { cur.i = s.length(); }
                        break;
                    default: sb.append(esc); cur.i += 2;
                }
                continue;
            }
            sb.append(c);
            cur.i++;
        }
        return sb.toString();
    }

    // ── Public API ──────────────────────────────────────────────────────

    /**
     * Normalize mod-internal language codes (LangPatterns.lang) to the JSON keys.
     * Handles the few inconsistent cases explicitly:
     *   "pt_BR" -> "ptbr"     (no separator in JSON)
     *   "zh_CN" -> "zh_cn"    (separator preserved in JSON)
     *   "zh_TW" -> "zh_tw"
     *   everything else -> simple lowercase
     */
    public static String normalizeLangCode(String code) {
        if (code == null) return null;
        String lower = code.toLowerCase();
        if ("pt_br".equals(lower)) return "ptbr";
        return lower; // covers en, fr, de, ..., zh_cn, zh_tw, pirate
    }

    /**
     * Returns the set of possible builder point values for the round.
     * Empty set means "unknown / no data" (caller should treat as length-bucket fallback).
     */
    public static Set<Integer> lookupBuilderPoints(String langCode, String displayedTheme) {
        return lookupBuilderPoints(langCode, displayedTheme, Collections.<String>emptySet());
    }

    public static Set<Integer> lookupBuilderPoints(String langCode, String displayedTheme,
                                                   Collection<String> ruledOutEnglishThemes) {
        Set<Integer> out = new TreeSet<Integer>();
        if (displayedTheme == null || displayedTheme.isEmpty()) return out;

        String lang = normalizeLangCode(langCode);
        // SNAPSHOT keys are NFC-normalized at parse time; normalize the lookup key too
        // so any non-NFC input from chat still hits.
        displayedTheme = Normalizer.normalize(displayedTheme, Normalizer.Form.NFC);

        // Pirate is stored under the source data's `en` translation key and must
        // be reverse-mapped like every other translated language.
        if (lang == null || "en".equals(lang)) {
            out.add(lengthBucket(displayedTheme));
            return out;
        }
        Snapshot snap = SNAPSHOT;
        Map<String, Set<String>> langIndex = snap.index.get(lang);
        if (langIndex != null) {
            Set<String> englishThemes = langIndex.get(displayedTheme);
            if (englishThemes != null && !englishThemes.isEmpty()) {
                for (String et : filterRuledOutEnglishThemes(englishThemes, ruledOutEnglishThemes)) {
                    out.add(lengthBucket(et));
                }
                return out;
            }
        }
        // Translation not found — Hypixel falls back to displaying the English theme.
        out.add(lengthBucket(displayedTheme));
        return out;
    }

    /** Hypixel's length rule for English themes: <=5 -> 1, 6..8 -> 2, >=9 -> 3 (space-inclusive). */
    public static int lengthBucket(String englishTheme) {
        if (englishTheme == null) return 1;
        int len = englishTheme.length();
        if (len <= 5) return 1;
        if (len <= 8) return 2;
        return 3;
    }

    public static boolean isKnownEnglishTheme(String s) {
        if (s == null) return false;
        return SNAPSHOT.englishThemes.containsKey(s.toLowerCase());
    }

    public static String resolveEnglishTheme(String s) {
        if (s == null) return null;
        String key = Normalizer.normalize(s.trim(), Normalizer.Form.NFC).toLowerCase();
        if (key.isEmpty()) return null;
        Snapshot snap = SNAPSHOT;
        String exact = snap.englishThemes.get(key);
        if (exact != null) return exact;

        String compact = compactSpaces(key);
        if (compact.isEmpty()) return null;
        String best = null;
        for (String candidate : snap.englishThemes.values()) {
            String candidateKey = compactSpaces(candidate.toLowerCase());
            if (!compact.equals(candidateKey)) continue;
            if (best == null
                    || compareSpaceFallbackCandidate(candidate, best, compact) < 0) {
                best = candidate;
            }
        }
        return best;
    }

    private static int compareSpaceFallbackCandidate(String a, String b, String compactInput) {
        boolean aExactCompact = a != null && a.equalsIgnoreCase(compactInput);
        boolean bExactCompact = b != null && b.equalsIgnoreCase(compactInput);
        if (aExactCompact != bExactCompact) return aExactCompact ? -1 : 1;
        int aSpaces = countWhitespace(a);
        int bSpaces = countWhitespace(b);
        if (aSpaces != bSpaces) return aSpaces - bSpaces;
        return String.CASE_INSENSITIVE_ORDER.compare(a, b);
    }

    private static String compactSpaces(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isWhitespace(c)) sb.append(c);
        }
        return sb.toString();
    }

    private static int countWhitespace(String s) {
        if (s == null) return 0;
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (Character.isWhitespace(s.charAt(i))) n++;
        }
        return n;
    }

    public static boolean isLoaded() {
        return !SNAPSHOT.index.isEmpty();
    }

    public static int getEnglishThemeCount() {
        return SNAPSHOT.englishThemes.size();
    }

    public static List<String> getEnglishThemes() {
        Snapshot snap = SNAPSHOT;
        TreeSet<String> ordered = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        ordered.addAll(snap.englishThemes.values());
        return new ArrayList<String>(ordered);
    }

    public static String getCurrentDataHash() {
        return currentDataHash;
    }

    public static long getCurrentDataObservedAt() {
        return currentDataObservedAt;
    }

    /**
     * Resolve with the exact exported snapshot when available, otherwise the
     * snapshot whose validity interval contains {@code seenAt}. Falls back to
     * the current data only when no historical source is known.
     */
    public static List<String> resolveEnglishThemesAt(
            String dataHash, long seenAt, String langCode, String displayedTheme,
            ScoreDelta builderDelta) {
        if (seenAt <= 0L && dataHash != null && dataHash.equalsIgnoreCase(currentDataHash)) {
            return resolveEnglishThemes(langCode, displayedTheme, builderDelta);
        }
        TranslationHistory.HistoricalResolution historical = TranslationHistory.resolve(
                dataHash, seenAt, langCode, displayedTheme, builderDelta);
        return historical.found
                ? historical.englishThemes
                : resolveEnglishThemes(langCode, displayedTheme, builderDelta);
    }

    /**
     * Resolve the English origins of a displayed theme and, when possible,
     * narrow multiword translations using the reconciled builder award.
     */
    public static List<String> resolveEnglishThemes(String langCode, String displayedTheme,
                                                    ScoreDelta builderDelta) {
        return resolveEnglishThemes(langCode, displayedTheme, builderDelta,
                Collections.<String>emptySet());
    }

    public static List<String> resolveEnglishThemes(String langCode, String displayedTheme,
                                                    ScoreDelta builderDelta,
                                                    Collection<String> ruledOutEnglishThemes) {
        if (displayedTheme == null || displayedTheme.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String displayed = Normalizer.normalize(displayedTheme.trim(), Normalizer.Form.NFC);
        String lang = normalizeLangCode(langCode);
        Snapshot snap = SNAPSHOT;
        Set<String> resolved = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);

        if (lang == null || "en".equals(lang)) {
            String english = resolveEnglishTheme(displayed);
            if (english != null) resolved.add(english);
        } else {
            Map<String, Set<String>> langIndex = snap.index.get(lang);
            if (langIndex != null) {
                Set<String> origins = langIndex.get(displayed);
                if (origins != null) resolved.addAll(origins);
            }
            if (resolved.isEmpty()) {
                String english = resolveEnglishTheme(displayed);
                if (english != null) resolved.add(english);
            }
        }

        if (resolved.size() <= 1 || builderDelta == null) {
            return filterRuledOutEnglishThemes(resolved, ruledOutEnglishThemes);
        }

        List<String> narrowed = new ArrayList<String>();
        for (String english : resolved) {
            if (builderDelta.canBe(lengthBucket(english))) {
                narrowed.add(english);
            }
        }
        return filterRuledOutEnglishThemes(
                narrowed.isEmpty() ? resolved : narrowed, ruledOutEnglishThemes);
    }

    public static Set<String> ruledOutEnglishThemesFromGuesses(Collection<String> guesses) {
        Set<String> out = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        if (guesses == null || guesses.isEmpty()) return out;
        for (String guess : guesses) {
            if (guess == null || guess.trim().isEmpty()) continue;
            out.addAll(ruledOutEnglishThemesFromGuess(guess));
        }
        return out;
    }

    public static boolean isDisplayedMultiwordTheme(String langCode, String displayedTheme) {
        if (displayedTheme == null || displayedTheme.trim().isEmpty()) return false;
        String lang = normalizeLangCode(langCode);
        if (lang == null || "en".equals(lang) || "pirate".equals(lang)) return false;

        Snapshot snap = SNAPSHOT;
        Map<String, Set<String>> langIndex = snap.index.get(lang);
        if (langIndex == null) return false;
        String displayed = Normalizer.normalize(displayedTheme.trim(), Normalizer.Form.NFC);
        Set<String> origins = langIndex.get(displayed);
        return origins != null && origins.size() > 1;
    }

    private static Set<String> ruledOutEnglishThemesFromGuess(String guess) {
        String key = normalizedGuessCacheKey(guess);
        if (key.isEmpty()) return Collections.emptySet();

        Set<String> cached = RULED_OUT_GUESS_CACHE.get(key);
        if (cached != null) return cached;

        // The immutable active-guess index already contains every normalized
        // English name, approved translation, and exact complement mapping.
        // Avoid rescanning every translation through GtwLogic.gtbMatches here.
        Set<String> frozen = findActiveGuessThemes(guess);
        RULED_OUT_GUESS_CACHE.put(key, frozen);
        return frozen;
    }

    private static String normalizedGuessCacheKey(String guess) {
        if (guess == null) return "";
        String normalized = Normalizer.normalize(guess.trim(), Normalizer.Form.NFC);
        if (normalized.isEmpty()) return "";
        // gtbMatches only permits adding a trailing lowercase 's'. Preserve
        // that one case-sensitive distinction while folding every other case.
        boolean uppercaseTrailingS = normalized.charAt(normalized.length() - 1) == 'S';
        return (uppercaseTrailingS ? "\u0001" : "\u0000")
                + normalized.toLowerCase(Locale.ROOT);
    }

    public static List<String> filterRuledOutEnglishThemes(Collection<String> candidates,
                                                           Collection<String> ruledOutEnglishThemes) {
        TreeSet<String> original = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        if (candidates != null) {
            for (String candidate : candidates) {
                if (candidate != null && !candidate.isEmpty()) original.add(candidate);
            }
        }
        if (original.size() <= 1 || ruledOutEnglishThemes == null || ruledOutEnglishThemes.isEmpty()) {
            return new ArrayList<String>(original);
        }

        TreeSet<String> ruledOut = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        for (String theme : ruledOutEnglishThemes) {
            if (theme != null && !theme.isEmpty()) ruledOut.add(theme);
        }
        if (ruledOut.isEmpty()) return new ArrayList<String>(original);

        TreeSet<String> kept = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        for (String candidate : original) {
            if (!ruledOut.contains(candidate)) kept.add(candidate);
        }
        return kept.isEmpty() ? new ArrayList<String>(original) : new ArrayList<String>(kept);
    }

    public static final class ShortcutThemeData {
        public final String englishTheme;
        public final String selectedShortcut;
        public final List<ShortcutMultiwordData> multiwords;
        public final Map<String, String> translations;

        ShortcutThemeData(String englishTheme, String selectedShortcut,
                          List<ShortcutMultiwordData> multiwords,
                          Map<String, String> translations) {
            this.englishTheme = englishTheme;
            this.selectedShortcut = selectedShortcut;
            this.multiwords = Collections.unmodifiableList(
                    new ArrayList<ShortcutMultiwordData>(multiwords));
            this.translations = Collections.unmodifiableMap(new HashMap<String, String>(translations));
        }
    }

    public static final class ShortcutMultiwordData {
        public final String value;
        public final Set<String> themes;

        ShortcutMultiwordData(String value, Set<String> themes) {
            this.value = value == null ? "" : value;
            TreeSet<String> copy = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
            if (themes != null) copy.addAll(themes);
            this.themes = Collections.unmodifiableSet(copy);
        }
    }

    /**
     * Resolve an exact displayed theme or a unique long-theme prefix emitted by
     * Hypixel's sidebar. Prefix matching never crosses languages and rejects
     * shared translations or multiple full titles.
     */
    public static String resolveExactOrUniqueLongDisplayedTheme(
            String langCode, String displayedTheme) {
        if (displayedTheme == null || displayedTheme.trim().isEmpty()) return null;
        String displayed = Normalizer.normalize(displayedTheme.trim(), Normalizer.Form.NFC);
        String lang = normalizeLangCode(langCode);
        Snapshot snap = SNAPSHOT;

        if (lang == null || "en".equals(lang)) {
            String english = snap.englishThemes.get(displayed.toLowerCase(Locale.ROOT));
            if (english != null) return english;
            lang = "en";
        } else {
            Map<String, Set<String>> language = snap.index.get(lang);
            if (language != null && language.containsKey(displayed)) return displayed;
            String english = snap.englishThemes.get(displayed.toLowerCase(Locale.ROOT));
            if (english != null) return english;
        }
        if (displayed.length() < LONG_THEME_PREFIX_MIN_LENGTH) return null;

        Map<String, List<LongThemePrefixCandidate>> language = snap.longThemePrefixes.get(lang);
        if (language == null) return null;
        List<LongThemePrefixCandidate> candidates = language.get(
                displayed.substring(0, LONG_THEME_PREFIX_MIN_LENGTH));
        if (candidates == null) return null;

        String resolvedDisplay = null;
        String resolvedEnglish = null;
        for (LongThemePrefixCandidate candidate : candidates) {
            if (candidate.displayedTheme.length() <= displayed.length()
                    || !candidate.displayedTheme.startsWith(displayed)) continue;
            if (candidate.englishOrigins.size() != 1) return null;
            String english = candidate.englishOrigins.iterator().next();
            if (resolvedDisplay != null
                    && (!resolvedDisplay.equals(candidate.displayedTheme)
                    || !resolvedEnglish.equalsIgnoreCase(english))) {
                return null;
            }
            resolvedDisplay = candidate.displayedTheme;
            resolvedEnglish = english;
        }
        return resolvedDisplay;
    }

    /** Resolve shortcut metadata for every English origin of a displayed theme. */
    public static List<ShortcutThemeData> findShortcutThemes(String langCode, String displayedTheme) {
        if (displayedTheme == null || displayedTheme.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String displayed = Normalizer.normalize(displayedTheme.trim(), Normalizer.Form.NFC);
        String lang = normalizeLangCode(langCode);
        Snapshot snap = SNAPSHOT;
        Set<String> englishOrigins = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);

        if (lang == null || "en".equals(lang)) {
            String english = resolveEnglishTheme(displayed);
            if (english != null) englishOrigins.add(english);
        } else {
            Map<String, Set<String>> langIndex = snap.index.get(lang);
            if (langIndex != null) {
                Set<String> resolved = langIndex.get(displayed);
                if (resolved != null) englishOrigins.addAll(resolved);
            }
            if (englishOrigins.isEmpty()) {
                String english = resolveEnglishTheme(displayed);
                if (english != null) englishOrigins.add(english);
            }
        }

        List<ShortcutThemeData> out = new ArrayList<ShortcutThemeData>();
        for (String english : englishOrigins) {
            ShortcutThemeData data = snap.shortcutThemes.get(english.toLowerCase());
            if (data != null) out.add(data);
        }
        return out;
    }

    public static final class GuessMatch {
        public final String englishTheme;
        public final Set<String> sourceLangCodes;

        GuessMatch(String englishTheme, Set<String> sourceLangCodes) {
            this.englishTheme = englishTheme;
            this.sourceLangCodes = Collections.unmodifiableSet(new TreeSet<String>(sourceLangCodes));
        }
    }

    /**
     * Find English themes whose English name or translation is accepted by the
     * GTBGUI-style guess matcher. Source codes use normalized mod language
     * codes, except the JSON's complement field ("co") is exposed as
     * "complement" for UI display.
     */
    public static List<GuessMatch> findGuessMatches(String guess) {
        return findGuessMatches(guess, null);
    }

    private static List<GuessMatch> findGuessMatches(String guess, int[] inspectionCount) {
        if (guess == null || guess.trim().isEmpty()) return Collections.emptyList();
        Snapshot snap = SNAPSHOT;
        if (snap.englishThemes.isEmpty()) return Collections.emptyList();

        ActiveGuessIndex index = snap.activeGuessIndex;
        Set<String> candidates = activeGuessThemes(guess,
                index.directThemes, index.whitespaceThemes, index.accentThemes,
                index.originalEndingSDirectThemes, index.originalEndingSWhitespaceThemes,
                index.originalEndingSAccentThemes, index.complementThemes);
        if (candidates.isEmpty()) return Collections.emptyList();

        Map<String, Set<String>> byEnglish = new TreeMap<String, Set<String>>();
        for (String englishTheme : candidates) {
            ShortcutThemeData data = snap.shortcutThemes.get(
                    englishTheme.toLowerCase(Locale.ROOT));
            if (data == null) continue;
            if (matchesGuessSource(guess, data.englishTheme, "en", inspectionCount)) {
                addGuessSource(byEnglish, data.englishTheme, "en");
            }
            for (Map.Entry<String, String> translation : data.translations.entrySet()) {
                String lang = translation.getKey();
                if (!matchesGuessSource(guess, translation.getValue(), lang, inspectionCount)) {
                    continue;
                }
                String displayLang = displayGuessSourceLang(lang);
                addGuessSource(byEnglish, data.englishTheme, displayLang);
            }
        }

        List<GuessMatch> out = new ArrayList<GuessMatch>(byEnglish.size());
        for (Map.Entry<String, Set<String>> e : byEnglish.entrySet()) {
            out.add(new GuessMatch(e.getKey(), e.getValue()));
        }
        return out;
    }

    static List<GuessMatch> findGuessMatchesForTest(String guess, int[] inspectionCount) {
        if (inspectionCount == null || inspectionCount.length == 0) {
            throw new IllegalArgumentException("inspectionCount must have at least one element");
        }
        inspectionCount[0] = 0;
        return findGuessMatches(guess, inspectionCount);
    }

    private static boolean matchesGuessSource(
            String guess, String candidate, String lang, int[] inspectionCount) {
        if (inspectionCount != null) inspectionCount[0]++;
        return GtwLogic.gtbMatches(guess, candidate, lang);
    }

    /** Active guesses include approved translations plus exact, case-insensitive complements. */
    public static Set<String> findActiveGuessThemes(String guess) {
        ActiveGuessIndex index = SNAPSHOT.activeGuessIndex;
        Set<String> themes = activeGuessThemes(guess,
                index.directThemes, index.whitespaceThemes, index.accentThemes,
                index.originalEndingSDirectThemes, index.originalEndingSWhitespaceThemes,
                index.originalEndingSAccentThemes, index.complementThemes);
        if (themes.isEmpty()) return Collections.emptySet();
        return Collections.unmodifiableSet(themes);
    }

    /** Lowercased source translations accepted for exactly the same English-theme set. */
    public static List<String> findScrambleCandidates(String guess) {
        ActiveGuessIndex index = SNAPSHOT.activeGuessIndex;
        Set<String> themes = activeGuessThemes(guess,
                index.directThemes, index.whitespaceThemes, index.accentThemes,
                index.originalEndingSDirectThemes, index.originalEndingSWhitespaceThemes,
                index.originalEndingSAccentThemes, index.complementThemes);
        if (themes.isEmpty()) return Collections.emptyList();
        List<String> candidates = index.candidatesByThemeSet.get(activeThemeSetKey(themes));
        return candidates == null ? Collections.<String>emptyList() : candidates;
    }

    public static boolean worksForExactActiveThemes(String guess, Set<String> expectedThemes) {
        if (expectedThemes == null || expectedThemes.isEmpty()) return false;
        ActiveGuessIndex index = SNAPSHOT.activeGuessIndex;
        Set<String> actual = activeGuessThemes(guess,
                index.directThemes, index.whitespaceThemes, index.accentThemes,
                index.originalEndingSDirectThemes, index.originalEndingSWhitespaceThemes,
                index.originalEndingSAccentThemes, index.complementThemes);
        return activeThemeSetKey(actual).equals(activeThemeSetKey(expectedThemes));
    }

    public static String accentNormForScramble(String value) {
        return value == null ? "" : activeAccentNorm(activeBaseNorm(value));
    }

    private static void addGuessSource(Map<String, Set<String>> byEnglish,
                                       String englishTheme,
                                       String sourceLang) {
        Set<String> langs = byEnglish.get(englishTheme);
        if (langs == null) {
            langs = new TreeSet<String>();
            byEnglish.put(englishTheme, langs);
        }
        langs.add(sourceLang);
    }

    private static String displayGuessSourceLang(String lang) {
        return "co".equals(lang) ? "complement" : lang;
    }

    // ── Hint Matching ──────────────────────────────────────────────────

    /**
     * Find every theme whose displayed name in `langCode` matches `hintPattern`.
     *
     * Hint pattern:
     *   '_'   matches any single non-space character
     *   ' '   must align with a space in the candidate (word boundary)
     *   else  exact case + accent sensitive match
     *
     * Returns a TreeMap keyed by displayedTheme (alphabetical) -> set of English
     * themes that produce that displayed name. For non-English languages, both
     * native translations and English-fallback themes (English themes without
     * a translation in this language) are returned.
     */
    public static Map<String, Set<String>> findMatchingDisplayedThemes(String langCode, String hintPattern) {
        Map<String, Set<String>> result = new TreeMap<String, Set<String>>();
        if (hintPattern == null || hintPattern.isEmpty()) return result;

        String normHint = Normalizer.normalize(hintPattern, Normalizer.Form.NFC);
        int hintLen = normHint.length();
        String lang = normalizeLangCode(langCode);
        // Pirate has its own translations (loaded from the JSON's "en" field, renamed
        // to "pirate" on parse). Only real English short-circuits to englishThemes.
        boolean englishLike = lang == null || "en".equals(lang);
        Snapshot snap = SNAPSHOT;

        if (englishLike) {
            List<String> bucket = snap.englishByLen.get(hintLen);
            if (bucket == null) return result;
            for (String enTheme : bucket) {
                if (matchesHint(enTheme, normHint)) {
                    Set<String> ens = new TreeSet<String>();
                    ens.add(enTheme);
                    result.put(enTheme, ens);
                }
            }
            return result;
        }

        // Walk only the length-N bucket of the per-language index.
        Map<Integer, Map<String, Set<String>>> langLenMap = snap.indexByLen.get(lang);
        if (langLenMap != null) {
            Map<String, Set<String>> bucket = langLenMap.get(hintLen);
            if (bucket != null) {
                for (Map.Entry<String, Set<String>> e : bucket.entrySet()) {
                    if (matchesHint(e.getKey(), normHint)) {
                        result.put(e.getKey(), new TreeSet<String>(e.getValue()));
                    }
                }
            }
        }
        // Themes without a translation in this language are displayed as English.
        // coveredEnglishByLang is precomputed at parse time across ALL translation lengths,
        // so we still skip an English theme even if its translation in this language has
        // a different length than the hint.
        Set<String> coveredEnglish = snap.coveredEnglishByLang.get(lang);
        if (coveredEnglish == null) coveredEnglish = Collections.emptySet();
        List<String> englishBucket = snap.englishByLen.get(hintLen);
        if (englishBucket != null) {
            for (String enTheme : englishBucket) {
                if (coveredEnglish.contains(enTheme)) continue;
                if (matchesHint(enTheme, normHint)) {
                    Set<String> ens = result.get(enTheme);
                    if (ens == null) {
                        ens = new TreeSet<String>();
                        result.put(enTheme, ens);
                    }
                    ens.add(enTheme);
                }
            }
        }
        return result;
    }

    /**
     * Resolve the displayed theme name in `langCode` for an English theme. For
     * "en" (or unknown lang) and for English themes that lack a translation in
     * `langCode`, returns the English theme verbatim — the same fallback Hypixel
     * uses when displaying a round.
     */
    public static String getDisplayedTheme(String langCode, String englishTheme) {
        if (englishTheme == null || englishTheme.isEmpty()) return englishTheme;
        String nfcEng = Normalizer.normalize(englishTheme, Normalizer.Form.NFC);
        String lang = normalizeLangCode(langCode);
        if (lang == null || "en".equals(lang)) return nfcEng;

        Snapshot snap = SNAPSHOT;
        Map<String, Set<String>> langIndex = snap.index.get(lang);
        if (langIndex != null) {
            for (Map.Entry<String, Set<String>> e : langIndex.entrySet()) {
                if (e.getValue().contains(nfcEng)) return e.getKey();
            }
        }
        return nfcEng;
    }

    /**
     * Return every translation of `englishTheme` keyed by language code
     * (e.g. "fr", "pirate", "ptbr"). Used by the /gtbs gtw answer matcher,
     * which accepts any language's translation as a correct guess.
     */
    public static Map<String, String> getAllTranslations(String englishTheme) {
        Map<String, String> result = new HashMap<String, String>();
        if (englishTheme == null || englishTheme.isEmpty()) return result;
        String nfcEng = Normalizer.normalize(englishTheme, Normalizer.Form.NFC);
        Snapshot snap = SNAPSHOT;
        for (Map.Entry<String, Map<String, Set<String>>> langE : snap.index.entrySet()) {
            for (Map.Entry<String, Set<String>> trE : langE.getValue().entrySet()) {
                if (trE.getValue().contains(nfcEng)) {
                    result.put(langE.getKey(), trE.getKey());
                    break;
                }
            }
        }
        return result;
    }

    /**
     * Pick a random English theme uniformly across the whole pool. Returns null
     * if no themes are loaded yet.
     */
    public static String pickRandomEnglishTheme(Random rng) {
        Snapshot snap = SNAPSHOT;
        Map<String, String> en = snap.englishThemes;
        int n = en.size();
        if (n == 0) return null;
        int target = rng.nextInt(n);
        int i = 0;
        for (String v : en.values()) {
            if (i++ == target) return v;
        }
        return null;
    }

    /**
     * Return all single-character themes displayed in `langCode`.
     * Used as the exception-1 fallback: in some Nordic and East-Asian languages,
     * 1-letter themes never reveal a hint character throughout the round, so we
     * have nothing to match against — the best we can do is enumerate them.
     */
    public static Map<String, Set<String>> findOneLetterDisplayedThemes(String langCode) {
        Map<String, Set<String>> result = new TreeMap<String, Set<String>>();
        String lang = normalizeLangCode(langCode);
        // Pirate has its own translations; only real English short-circuits to englishThemes.
        boolean englishLike = lang == null || "en".equals(lang);
        Snapshot snap = SNAPSHOT;

        if (englishLike) {
            List<String> bucket = snap.englishByLen.get(1);
            if (bucket != null) {
                for (String enTheme : bucket) {
                    Set<String> ens = new TreeSet<String>();
                    ens.add(enTheme);
                    result.put(enTheme, ens);
                }
            }
            return result;
        }

        Map<Integer, Map<String, Set<String>>> langLenMap = snap.indexByLen.get(lang);
        if (langLenMap != null) {
            Map<String, Set<String>> bucket = langLenMap.get(1);
            if (bucket != null) {
                for (Map.Entry<String, Set<String>> e : bucket.entrySet()) {
                    result.put(e.getKey(), new TreeSet<String>(e.getValue()));
                }
            }
        }
        Set<String> coveredEnglish = snap.coveredEnglishByLang.get(lang);
        if (coveredEnglish == null) coveredEnglish = Collections.emptySet();
        List<String> englishBucket = snap.englishByLen.get(1);
        if (englishBucket != null) {
            for (String enTheme : englishBucket) {
                if (coveredEnglish.contains(enTheme)) continue;
                Set<String> ens = result.get(enTheme);
                if (ens == null) {
                    ens = new TreeSet<String>();
                    result.put(enTheme, ens);
                }
                ens.add(enTheme);
            }
        }
        return result;
    }

    /**
     * Candidates are NFC-normalized at parse time so this comparison is direct.
     * The hint must already be NFC-normalized by the caller (done once per lookup).
     */
    private static boolean matchesHint(String candidate, String normHint) {
        if (candidate == null) return false;
        if (candidate.length() != normHint.length()) return false;
        for (int i = 0; i < normHint.length(); i++) {
            char h = normHint.charAt(i);
            char cc = candidate.charAt(i);
            if (h == ' ') {
                if (cc != ' ') return false;
            } else if (h == '_') {
                if (cc == ' ') return false;
            } else {
                if (cc != h) return false;
            }
        }
        return true;
    }
}
