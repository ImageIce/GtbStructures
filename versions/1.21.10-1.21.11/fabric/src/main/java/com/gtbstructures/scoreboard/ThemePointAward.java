package com.gtbstructures.scoreboard;

import com.gtbstructures.translation.TranslationData;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Multi-language fork logic for the builder's theme award.
 */
public final class ThemePointAward {

    private ThemePointAward() {
    }

    public static PointEntry compute(String displayedTheme, String langCode) {
        return compute(displayedTheme, langCode, Collections.<String>emptySet());
    }

    public static PointEntry compute(String displayedTheme, String langCode,
                                     Collection<String> ruledOutEnglishThemes) {
        if (displayedTheme == null || displayedTheme.isEmpty()) return PointEntry.zero();

        // Pirate is stored as translated data and must use reverse lookup.
        String normalized = TranslationData.normalizeLangCode(langCode);
        if (normalized == null || "en".equals(normalized)) {
            return PointEntry.definite(awardForEnglishLength(displayedTheme));
        }

        Set<Integer> candidates = TranslationData.lookupBuilderPoints(
                langCode, displayedTheme, ruledOutEnglishThemes);
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

    private static int awardForEnglishLength(String englishTheme) {
        return awardForEnglishLength(englishTheme.length());
    }

    private static int awardForEnglishLength(int length) {
        return length <= 5 ? 1 : length <= 8 ? 2 : 3;
    }
}
