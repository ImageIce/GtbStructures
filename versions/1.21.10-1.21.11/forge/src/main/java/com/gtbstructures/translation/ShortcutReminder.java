package com.gtbstructures.translation;

import com.gtbstructures.chat.ChatHeader;
import com.gtbstructures.gtw.GtwLogic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Platform-neutral core for shortcut reminders shown after a GTB theme reveal.
 * Platform handlers own chat delivery and config storage.
 */
public final class ShortcutReminder {
    /** @deprecated Use {@link #prefix()} so custom headers are reflected at send time. */
    @Deprecated
    public static final String PREFIX = ChatHeader.render(ChatHeader.DEFAULT_RAW) + "\u00a7r\u00a77 ";

    public static final class Options {
        public boolean enabled = true;
        public boolean showAllShortcuts;
        public boolean showMultiwords;
        public boolean showAllMultiwords;
        public boolean showNonAscii;
        public boolean showExtendedLatin = true;
        public boolean showCyrillic = true;
        public boolean showHanzi = true;
        public boolean showJapanese = true;
        public boolean showHangul = true;
    }

    public static final class RoundState {
        public String langCode;
        public String theme;
        public boolean shortcutReminderSent;
    }

    private ShortcutReminder() {
    }

    public static List<String> prepareMessages(Options options, RoundState round) {
        return prepareMessages(options, round, round == null ? null : round.theme);
    }

    public static List<String> prepareMessages(Options options, RoundState round,
                                               String displayedTheme) {
        Options effective = options == null ? new Options() : options;
        if (!effective.enabled || round == null || round.shortcutReminderSent) {
            return Collections.emptyList();
        }
        if (displayedTheme == null || displayedTheme.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String cleanTheme = displayedTheme.trim();
        if (TranslationData.findShortcutThemes(round.langCode, cleanTheme).isEmpty()) {
            return Collections.emptyList();
        }

        round.shortcutReminderSent = true;
        List<String> messages = new ArrayList<String>();
        messages.add(buildMessage(round.langCode, cleanTheme,
                effective.showAllShortcuts,
                effective.showNonAscii,
                effective.showExtendedLatin,
                effective.showCyrillic,
                effective.showHanzi,
                effective.showJapanese,
                effective.showHangul));

        if (effective.showMultiwords) {
            String multiwordMessage = buildMultiwordMessage(round.langCode, cleanTheme,
                    effective.showAllMultiwords,
                    effective.showNonAscii,
                    effective.showExtendedLatin,
                    effective.showCyrillic,
                    effective.showHanzi,
                    effective.showJapanese,
                    effective.showHangul);
            if (multiwordMessage != null) {
                messages.add(multiwordMessage);
            }
        }
        return messages;
    }

    public static String buildMessageForTest(String langCode, String displayedTheme,
                                             boolean showAll, boolean showNonAscii,
                                             boolean extendedLatin, boolean cyrillic,
                                             boolean hanzi, boolean japanese,
                                             boolean hangul) {
        return buildMessage(langCode, displayedTheme, showAll, showNonAscii,
                extendedLatin, cyrillic, hanzi, japanese, hangul);
    }

    public static String buildMultiwordMessageForTest(String langCode, String displayedTheme,
                                                      boolean showAllMultiwords,
                                                      boolean showNonAscii,
                                                      boolean extendedLatin,
                                                      boolean cyrillic,
                                                      boolean hanzi,
                                                      boolean japanese,
                                                      boolean hangul) {
        return buildMultiwordMessage(langCode, displayedTheme, showAllMultiwords,
                showNonAscii, extendedLatin, cyrillic, hanzi, japanese, hangul);
    }

    private static String buildMessage(String langCode, String displayedTheme,
                                       boolean showAll, boolean showNonAscii,
                                       boolean extendedLatin, boolean cyrillic,
                                       boolean hanzi, boolean japanese,
                                       boolean hangul) {
        List<TranslationData.ShortcutThemeData> themes =
                TranslationData.findShortcutThemes(langCode, displayedTheme);
        int referenceLength = shortcutReferenceLength(themes);
        Map<String, Candidate> candidates = new LinkedHashMap<String, Candidate>();
        Map<String, List<GtwLogic.LegalShortcutCandidate>> variantCache =
                new LinkedHashMap<String, List<GtwLogic.LegalShortcutCandidate>>();
        Set<String> requiredThemes = new HashSet<String>();
        for (TranslationData.ShortcutThemeData theme : themes) {
            requiredThemes.add(theme.englishTheme);
        }

        for (TranslationData.ShortcutThemeData theme : themes) {
            Set<String> parentTheme = Collections.singleton(theme.englishTheme);
            boolean preferExtendedLatin = showNonAscii && extendedLatin;
            if (showAll) {
                addCandidateVariants(candidates, theme.englishTheme, theme,
                        "en", parentTheme, preferExtendedLatin, variantCache);
                addShortcutFieldCandidate(candidates, theme.selectedShortcut, theme,
                        parentTheme, preferExtendedLatin, variantCache);
                for (TranslationData.ShortcutMultiwordData multiword : theme.multiwords) {
                    Set<String> coveredThemes = multiword.themes.isEmpty()
                            ? parentTheme : multiword.themes;
                    addMultiwordCandidate(candidates, multiword.value, theme,
                            coveredThemes, preferExtendedLatin, variantCache);
                }
                for (Map.Entry<String, String> translation : theme.translations.entrySet()) {
                    if ("co".equals(translation.getKey())) {
                        addFixedCandidate(candidates, translation.getValue(), theme,
                                parentTheme, preferExtendedLatin, variantCache);
                    } else {
                        addCandidateVariants(candidates, translation.getValue(), theme,
                                translation.getKey(), parentTheme, preferExtendedLatin,
                                variantCache);
                    }
                }
            } else {
                addShortcutFieldCandidate(candidates, theme.selectedShortcut, theme,
                        parentTheme, preferExtendedLatin, variantCache);
            }
        }

        List<String> accepted = new ArrayList<String>();
        int minimumLength = Integer.MAX_VALUE;
        int bestPreference = Integer.MAX_VALUE;
        for (Candidate candidate : candidates.values()) {
            if (!candidate.coveredThemes.containsAll(requiredThemes)) continue;
            if (!candidate.hasMeaningfulForm && candidate.coveredThemes.size() <= 1) continue;
            if (!isAllowed(candidate, showNonAscii, extendedLatin, cyrillic,
                    hanzi, japanese, hangul)) {
                continue;
            }
            int length = shortcutLength(candidate.processed);
            if (length > referenceLength) continue;
            if (length < minimumLength
                    || (length == minimumLength && candidate.preference < bestPreference)) {
                accepted.clear();
                minimumLength = length;
                bestPreference = candidate.preference;
            }
            if (length == minimumLength && candidate.preference == bestPreference) {
                accepted.add(candidate.processed);
            }
        }

        Collections.sort(accepted, new Comparator<String>() {
            @Override
            public int compare(String left, String right) {
                int byLength = shortcutLength(left) - shortcutLength(right);
                return byLength != 0
                        ? byLength
                        : String.CASE_INSENSITIVE_ORDER.compare(left, right);
            }
        });

        StringBuilder message = new StringBuilder(prefix());
        if (accepted.isEmpty()) {
            return message.append("No shortcuts found for \u00a7f")
                    .append(displayedTheme).toString();
        }
        message.append(accepted.size() == 1 ? "Shortcut for \u00a7f" : "Shortcuts for \u00a7f")
                .append(displayedTheme).append("\u00a77: ");
        for (int i = 0; i < accepted.size(); i++) {
            if (i > 0) message.append("\u00a77, ");
            message.append("\u00a7f").append(accepted.get(i));
        }
        return message.toString();
    }

    private static String buildMultiwordMessage(String langCode, String displayedTheme,
                                                boolean showAllMultiwords,
                                                boolean showNonAscii,
                                                boolean extendedLatin,
                                                boolean cyrillic,
                                                boolean hanzi,
                                                boolean japanese,
                                                boolean hangul) {
        List<TranslationData.ShortcutThemeData> themes =
                TranslationData.findShortcutThemes(langCode, displayedTheme);
        if (themes.isEmpty()) return null;
        Set<String> requiredThemes = new HashSet<String>();
        for (TranslationData.ShortcutThemeData theme : themes) {
            requiredThemes.add(theme.englishTheme);
        }

        Map<String, MultiwordGroup> groups = new LinkedHashMap<String, MultiwordGroup>();
        Map<String, List<GtwLogic.LegalShortcutCandidate>> variantCache =
                new LinkedHashMap<String, List<GtwLogic.LegalShortcutCandidate>>();
        boolean preferExtendedLatin = showNonAscii && extendedLatin;
        for (TranslationData.ShortcutThemeData theme : themes) {
            Set<String> parentTheme = Collections.singleton(theme.englishTheme);
            for (TranslationData.ShortcutMultiwordData multiword : theme.multiwords) {
                Set<String> coveredThemes = multiword.themes.isEmpty()
                        ? parentTheme : multiword.themes;
                if (!coveredThemes.containsAll(requiredThemes)) continue;
                addMultiwordDisplayCandidates(groups, multiword.value, theme,
                        coveredThemes, preferExtendedLatin, variantCache);
            }
        }

        List<MultiwordResultGroup> resultGroups =
                new ArrayList<MultiwordResultGroup>();
        for (MultiwordGroup group : groups.values()) {
            List<Candidate> accepted = acceptedMultiwordCandidates(group,
                    showNonAscii, extendedLatin, cyrillic, hanzi, japanese, hangul);
            if (accepted.isEmpty()) continue;
            if (!showAllMultiwords && accepted.size() > 1) {
                accepted = Collections.singletonList(accepted.get(0));
            }
            List<String> values = new ArrayList<String>();
            for (Candidate candidate : accepted) values.add(candidate.processed);
            resultGroups.add(new MultiwordResultGroup(group.themes, values));
        }
        if (resultGroups.isEmpty()) return null;
        Collections.sort(resultGroups, MULTIWORD_GROUP_ORDER);

        boolean compact = !showAllMultiwords;
        if (!compact) {
            compact = true;
            for (MultiwordResultGroup group : resultGroups) {
                if (group.values.size() > 1) {
                    compact = false;
                    break;
                }
            }
        }
        return compact
                ? formatCompactMultiwordMessage(displayedTheme, resultGroups)
                : formatExpandedMultiwordMessage(displayedTheme, resultGroups);
    }

    private static void addMultiwordDisplayCandidates(
            Map<String, MultiwordGroup> groups,
            String raw,
            TranslationData.ShortcutThemeData theme,
            Set<String> coveredThemes,
            boolean preferExtendedLatin,
            Map<String, List<GtwLogic.LegalShortcutCandidate>> variantCache) {
        if (raw == null) return;
        Set<String> groupThemes =
                new java.util.TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        if (coveredThemes != null) groupThemes.addAll(coveredThemes);
        if (groupThemes.isEmpty() && theme != null) groupThemes.add(theme.englishTheme);
        if (groupThemes.isEmpty()) return;
        String key = themeSetKey(groupThemes);
        MultiwordGroup group = groups.get(key);
        if (group == null) {
            group = new MultiwordGroup(groupThemes);
            groups.put(key, group);
        }

        addFixedCandidate(group.candidates, raw, theme, groupThemes,
                preferExtendedLatin, variantCache);
    }

    private static List<Candidate> acceptedMultiwordCandidates(
            MultiwordGroup group,
            boolean showNonAscii,
            boolean extendedLatin,
            boolean cyrillic,
            boolean hanzi,
            boolean japanese,
            boolean hangul) {
        List<Candidate> accepted = new ArrayList<Candidate>();
        for (Candidate candidate : group.candidates.values()) {
            if (!candidate.hasMeaningfulForm && candidate.coveredThemes.size() <= 1) {
                continue;
            }
            if (!isAllowed(candidate, showNonAscii, extendedLatin, cyrillic,
                    hanzi, japanese, hangul)) {
                continue;
            }
            accepted.add(candidate);
        }
        Collections.sort(accepted, CANDIDATE_ORDER);
        return accepted;
    }

    private static String formatCompactMultiwordMessage(
            String displayedTheme, List<MultiwordResultGroup> groups) {
        int count = countMultiwords(groups);
        StringBuilder message = new StringBuilder(prefix());
        message.append(count == 1 ? "Multiword for \u00a7f" : "Multiwords for \u00a7f")
                .append(displayedTheme).append("\u00a77: ");
        boolean first = true;
        for (MultiwordResultGroup group : groups) {
            for (String value : group.values) {
                if (!first) message.append("\u00a77, ");
                first = false;
                message.append("\u00a7f").append(value).append("\u00a77 (");
                appendThemes(message, group.themes);
                message.append("\u00a77)");
            }
        }
        return message.toString();
    }

    private static String formatExpandedMultiwordMessage(
            String displayedTheme, List<MultiwordResultGroup> groups) {
        int count = countMultiwords(groups);
        StringBuilder message = new StringBuilder(prefix());
        message.append(count == 1 ? "Multiword for \u00a7f" : "Multiwords for \u00a7f")
                .append(displayedTheme).append("\u00a77:");
        for (MultiwordResultGroup group : groups) {
            message.append("\n\u00a77- ");
            appendValues(message, group.values);
            message.append(" \u00a77(");
            appendThemes(message, group.themes);
            message.append("\u00a77)");
        }
        return message.toString();
    }

    private static void appendValues(StringBuilder message, List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) message.append("\u00a77, ");
            message.append("\u00a7f").append(values.get(i));
        }
    }

    private static void appendThemes(StringBuilder message, Set<String> themes) {
        int index = 0;
        for (String theme : themes) {
            if (index++ > 0) message.append("\u00a77, ");
            message.append("\u00a77").append(theme);
        }
    }

    private static int countMultiwords(List<MultiwordResultGroup> groups) {
        int count = 0;
        for (MultiwordResultGroup group : groups) count += group.values.size();
        return count;
    }

    public static String prefix() {
        return ChatHeader.prefix();
    }

    private static String themeSetKey(Set<String> themes) {
        StringBuilder key = new StringBuilder();
        for (String theme : themes) {
            if (key.length() > 0) key.append('\u0001');
            key.append(theme.toLowerCase(Locale.ROOT));
        }
        return key.toString();
    }

    private static void addShortcutFieldCandidate(Map<String, Candidate> candidates, String raw,
                                                  TranslationData.ShortcutThemeData theme,
                                                  Set<String> coveredThemes,
                                                  boolean preferExtendedLatin,
                                                  Map<String, List<GtwLogic.LegalShortcutCandidate>> variantCache) {
        addFixedCandidate(candidates, raw, theme, coveredThemes, preferExtendedLatin,
                variantCache);
    }

    private static void addMultiwordCandidate(Map<String, Candidate> candidates, String raw,
                                              TranslationData.ShortcutThemeData theme,
                                              Set<String> coveredThemes,
                                              boolean preferExtendedLatin,
                                              Map<String, List<GtwLogic.LegalShortcutCandidate>> variantCache) {
        addFixedCandidate(candidates, raw, theme, coveredThemes, preferExtendedLatin,
                variantCache);
    }

    private static void addFixedCandidate(Map<String, Candidate> candidates, String raw,
                                          TranslationData.ShortcutThemeData theme,
                                          Set<String> coveredThemes,
                                          boolean preferExtendedLatin,
                                          Map<String, List<GtwLogic.LegalShortcutCandidate>> variantCache) {
        if (raw == null) return;
        String processed = normalizeFixedCandidate(raw);
        if (processed.isEmpty()) return;
        addCandidate(candidates, processed, 0, theme, null, coveredThemes,
                preferExtendedLatin, variantCache);
    }

    private static String normalizeFixedCandidate(String raw) {
        return raw.trim().toLowerCase(Locale.ROOT).replace('\u0131', 'i');
    }

    private static void addCandidateVariants(Map<String, Candidate> candidates, String raw,
                                             TranslationData.ShortcutThemeData theme,
                                             String explicitLang, Set<String> coveredThemes,
                                             boolean preferExtendedLatin,
                                             Map<String, List<GtwLogic.LegalShortcutCandidate>> variantCache) {
        for (GtwLogic.LegalShortcutCandidate variant :
                legalShortcutCandidates(raw, preferExtendedLatin, variantCache)) {
            addCandidate(candidates, variant.value, variant.preference, theme,
                    explicitLang, coveredThemes, preferExtendedLatin, variantCache);
        }
    }

    private static void addCandidate(Map<String, Candidate> candidates, String processed,
                                     int preference, TranslationData.ShortcutThemeData theme,
                                     String explicitLang, Set<String> coveredThemes,
                                     boolean preferExtendedLatin,
                                     Map<String, List<GtwLogic.LegalShortcutCandidate>> variantCache) {
        if (processed.isEmpty()) return;
        String key = processed.toLowerCase(Locale.ROOT);
        Candidate candidate = candidates.get(key);
        if (candidate == null) {
            candidate = new Candidate(processed);
            candidates.put(key, candidate);
        }
        if (preference < candidate.preference) candidate.preference = preference;
        if (coveredThemes != null) candidate.coveredThemes.addAll(coveredThemes);
        if (!isParentThemeVariant(processed, theme)) {
            candidate.hasMeaningfulForm = true;
        }
        if (explicitLang != null) candidate.sourceLangs.add(explicitLang);
        for (Map.Entry<String, String> translation : theme.translations.entrySet()) {
            if (canProduce(translation.getValue(), processed, preferExtendedLatin,
                    variantCache)) {
                candidate.sourceLangs.add(translation.getKey());
            }
        }
    }

    private static boolean isParentThemeVariant(String processed,
                                                TranslationData.ShortcutThemeData theme) {
        String englishTheme = theme == null || theme.englishTheme == null
                ? "" : theme.englishTheme;
        return processed.equalsIgnoreCase(englishTheme);
    }

    private static boolean canProduce(String raw, String processed,
                                      boolean preferExtendedLatin,
                                      Map<String, List<GtwLogic.LegalShortcutCandidate>> variantCache) {
        for (GtwLogic.LegalShortcutCandidate variant :
                legalShortcutCandidates(raw, preferExtendedLatin, variantCache)) {
            if (processed.equalsIgnoreCase(variant.value)) return true;
        }
        return false;
    }

    private static List<GtwLogic.LegalShortcutCandidate> legalShortcutCandidates(
            String raw,
            boolean preferExtendedLatin,
            Map<String, List<GtwLogic.LegalShortcutCandidate>> variantCache) {
        if (raw == null) return Collections.emptyList();
        String key = (preferExtendedLatin ? "1:" : "0:") + raw;
        List<GtwLogic.LegalShortcutCandidate> cached = variantCache.get(key);
        if (cached == null) {
            cached = GtwLogic.legalShortcutCandidates(raw, preferExtendedLatin);
            variantCache.put(key, cached);
        }
        return cached;
    }

    private static boolean isAllowed(Candidate candidate, boolean showNonAscii,
                                     boolean extendedLatin, boolean cyrillic,
                                     boolean hanzi, boolean japanese, boolean hangul) {
        if (isAscii(candidate.processed)) return true;
        if (!showNonAscii) return false;
        NonAsciiCategory category = category(candidate);
        if (category == NonAsciiCategory.HANZI
                && candidate.sourceLangs.contains("ja")
                && (candidate.sourceLangs.contains("zh_cn")
                || candidate.sourceLangs.contains("zh_tw"))) {
            return hanzi || japanese;
        }
        switch (category) {
            case EXTENDED_LATIN: return extendedLatin;
            case CYRILLIC: return cyrillic;
            case HANZI: return hanzi;
            case JAPANESE: return japanese;
            case HANGUL: return hangul;
            default: return false;
        }
    }

    private static NonAsciiCategory category(Candidate candidate) {
        boolean japaneseSource = candidate.sourceLangs.contains("ja");
        boolean chineseSource = candidate.sourceLangs.contains("zh_cn")
                || candidate.sourceLangs.contains("zh_tw");
        boolean hasHan = false;
        boolean hasExtendedLatin = false;
        for (int offset = 0; offset < candidate.processed.length();) {
            int cp = candidate.processed.codePointAt(offset);
            offset += Character.charCount(cp);
            if (cp < 128) continue;
            if (isCyrillic(cp)) return NonAsciiCategory.CYRILLIC;
            if (isKana(cp)) return NonAsciiCategory.JAPANESE;
            if (isHangul(cp)) return NonAsciiCategory.HANGUL;
            if (isHan(cp)) {
                hasHan = true;
            } else if (Character.UnicodeScript.of(cp) == Character.UnicodeScript.LATIN) {
                hasExtendedLatin = true;
            } else {
                return NonAsciiCategory.UNSUPPORTED;
            }
        }
        if (hasHan) {
            if (japaneseSource && !chineseSource) return NonAsciiCategory.JAPANESE;
            return NonAsciiCategory.HANZI;
        }
        return hasExtendedLatin
                ? NonAsciiCategory.EXTENDED_LATIN
                : NonAsciiCategory.UNSUPPORTED;
    }

    public static String categoryForTest(String value, String sourceLang) {
        Candidate candidate = new Candidate(value);
        if (sourceLang != null) candidate.sourceLangs.add(sourceLang);
        return category(candidate).name();
    }

    private static boolean isAscii(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) >= 128) return false;
        }
        return true;
    }

    private static boolean isCyrillic(int cp) {
        return (cp >= 0x0400 && cp <= 0x052F)
                || (cp >= 0x2DE0 && cp <= 0x2DFF)
                || (cp >= 0xA640 && cp <= 0xA69F);
    }

    private static boolean isHan(int cp) {
        return (cp >= 0x3400 && cp <= 0x4DBF)
                || (cp >= 0x4E00 && cp <= 0x9FFF)
                || (cp >= 0xF900 && cp <= 0xFAFF);
    }

    private static boolean isKana(int cp) {
        return (cp >= 0x3040 && cp <= 0x30FF)
                || (cp >= 0x31F0 && cp <= 0x31FF);
    }

    private static boolean isHangul(int cp) {
        return (cp >= 0x1100 && cp <= 0x11FF)
                || (cp >= 0x3130 && cp <= 0x318F)
                || (cp >= 0xAC00 && cp <= 0xD7AF);
    }

    private static int codePointLength(String value) {
        return value.codePointCount(0, value.length());
    }

    private static int shortcutReferenceLength(
            List<TranslationData.ShortcutThemeData> themes) {
        int reference = 0;
        for (TranslationData.ShortcutThemeData theme : themes) {
            String englishTheme = theme.englishTheme == null
                    ? "" : theme.englishTheme.trim();
            int themeLength = shortcutLength(englishTheme);
            reference = Math.max(reference, themeLength);
        }
        return reference;
    }

    private static int shortcutLength(String value) {
        if (value == null || value.isEmpty()) return 0;
        return value.codePointCount(0, value.length());
    }

    private static final Comparator<Candidate> CANDIDATE_ORDER =
            new Comparator<Candidate>() {
                @Override
                public int compare(Candidate left, Candidate right) {
                    int byLength = codePointLength(left.processed)
                            - codePointLength(right.processed);
                    if (byLength != 0) return byLength;
                    int byPreference = left.preference - right.preference;
                    if (byPreference != 0) return byPreference;
                    return String.CASE_INSENSITIVE_ORDER.compare(
                            left.processed, right.processed);
                }
            };

    private static final Comparator<MultiwordResultGroup> MULTIWORD_GROUP_ORDER =
            new Comparator<MultiwordResultGroup>() {
                @Override
                public int compare(MultiwordResultGroup left,
                                   MultiwordResultGroup right) {
                    int byThemeCount = left.themes.size() - right.themes.size();
                    if (byThemeCount != 0) return byThemeCount;
                    int byThemes = themeSetKey(left.themes).compareToIgnoreCase(
                            themeSetKey(right.themes));
                    if (byThemes != 0) return byThemes;
                    return String.CASE_INSENSITIVE_ORDER.compare(
                            left.values.get(0), right.values.get(0));
                }
            };

    private enum NonAsciiCategory {
        EXTENDED_LATIN, CYRILLIC, HANZI, JAPANESE, HANGUL, UNSUPPORTED
    }

    private static final class Candidate {
        final String processed;
        final Set<String> sourceLangs = new HashSet<String>();
        final Set<String> coveredThemes =
                new java.util.TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        boolean hasMeaningfulForm;
        int preference = Integer.MAX_VALUE;

        Candidate(String processed) {
            this.processed = processed;
        }
    }

    private static final class MultiwordGroup {
        final Set<String> themes =
                new java.util.TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        final Map<String, Candidate> candidates = new LinkedHashMap<String, Candidate>();

        MultiwordGroup(Set<String> themes) {
            if (themes != null) this.themes.addAll(themes);
        }
    }

    private static final class MultiwordResultGroup {
        final Set<String> themes =
                new java.util.TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        final List<String> values;

        MultiwordResultGroup(Set<String> themes, List<String> values) {
            if (themes != null) this.themes.addAll(themes);
            this.values = values;
        }
    }
}
