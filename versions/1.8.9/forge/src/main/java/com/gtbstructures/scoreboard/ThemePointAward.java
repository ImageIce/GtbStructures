package com.gtbstructures.scoreboard;

import com.gtbstructures.translation.TranslationData;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Multi-language fork logic for the builder's theme award. GTU uses a plain
 * English length rule; we extend that with a reverse lookup so a non-English
 * theme that maps to N English candidates with differing point values yields a
 * fork PointEntry instead of a single guess.
 *
 * <p>Path 1 — English play (or no translation table coverage): apply Hypixel's
 * length rule directly to the displayed string.
 * <p>Path 2 — non-English: ask {@link TranslationData#lookupBuilderPoints} for
 * the set of possible point values; collapse to definite if size==1, else fork.
 *
 * <p>{@link TranslationData#lookupBuilderPoints} already returns the
 * length-bucket fallback when a translation isn't in the table, so this class
 * just routes to it for non-English and forwards the result.
 */
public final class ThemePointAward {

    private ThemePointAward() {}

    public static PointEntry compute(String displayedTheme, String langCode) {
        return compute(displayedTheme, langCode, Collections.<String>emptySet());
    }

    public static PointEntry compute(String displayedTheme, String langCode,
                                     Collection<String> ruledOutEnglishThemes) {
        return compute(displayedTheme, langCode, ruledOutEnglishThemes,
                Collections.<String>emptyList());
    }

    public static PointEntry compute(String displayedTheme, String langCode,
                                     Collection<String> ruledOutEnglishThemes,
                                     Collection<String> acceptedGuessTexts) {
        return compute(displayedTheme, langCode, ruledOutEnglishThemes, acceptedGuessTexts, null);
    }

    public static PointEntry compute(String displayedTheme, String langCode,
                                     Collection<String> ruledOutEnglishThemes,
                                     Collection<String> acceptedGuessTexts,
                                     Integer scoreboardPoint) {
        if (displayedTheme == null || displayedTheme.isEmpty()) return PointEntry.zero();
        if (scoreboardPoint != null && scoreboardPoint.intValue() >= 1
                && scoreboardPoint.intValue() <= 3) {
            return PointEntry.definite(scoreboardPoint.intValue());
        }

        // Path 1: English / unknown lang — direct length bucket. Pirate is a
        // translated language in the source data and follows the reverse lookup.
        String norm = TranslationData.normalizeLangCode(langCode);
        if (norm == null || "en".equals(norm)) {
            return PointEntry.definite(awardForEnglishLength(displayedTheme));
        }

        // Path 2: non-English — reverse lookup. Already handles fallback when
        // the translation isn't in the table (returns lengthBucket of displayed).
        Set<Integer> candidates = TranslationData.lookupBuilderPoints(
                langCode, displayedTheme, ruledOutEnglishThemes, acceptedGuessTexts);
        if (candidates == null || candidates.isEmpty()) {
            return PointEntry.definite(awardForEnglishLength(displayedTheme));
        }
        if (candidates.size() == 1) {
            return PointEntry.definite(candidates.iterator().next());
        }
        return PointEntry.fork(new HashSet<Integer>(candidates));
    }

    public static PointEntry computeEnglishHintStructure(String structure) {
        int length = englishLengthFromStructure(structure);
        if (length <= 0) return PointEntry.zero();
        return PointEntry.definite(awardForEnglishLength(length));
    }

    static int englishLengthFromStructure(String structure) {
        if (structure == null || structure.trim().isEmpty()) return 0;
        String[] parts = structure.trim().split("-");
        int total = 0;
        int words = 0;
        for (String part : parts) {
            if (part == null || part.trim().isEmpty()) return 0;
            try {
                int n = Integer.parseInt(part.trim());
                if (n <= 0) return 0;
                total += n;
                words++;
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return words == 0 ? 0 : total + words - 1;
    }

    /** ≤5 chars → 1, 6–8 → 2, ≥9 → 3 (space-inclusive). Mirrors TranslationData.lengthBucket. */
    private static int awardForEnglishLength(String englishTheme) {
        return awardForEnglishLength(englishTheme.length());
    }

    private static int awardForEnglishLength(int n) {
        return n <= 5 ? 1 : n <= 8 ? 2 : 3;
    }
}
