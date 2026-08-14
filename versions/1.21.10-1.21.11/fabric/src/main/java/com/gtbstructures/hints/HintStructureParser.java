package com.gtbstructures.hints;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class HintStructureParser {
    private static final List<ThemeRevealPattern> THEME_REVEAL_PATTERNS = buildThemeRevealPatterns();

    private HintStructureParser() {
    }

    public static final class ParsedHint {
        public static final ParsedHint EMPTY = new ParsedHint(false, "", "");

        public final boolean isHint;
        public final String structure;
        public final String trimmedHint;

        private ParsedHint(boolean isHint, String structure, String trimmedHint) {
            this.isHint = isHint;
            this.structure = structure;
            this.trimmedHint = trimmedHint;
        }
    }

    public static final class ThemeRevealPattern {
        public final String prefix;
        public final String suffix;

        private ThemeRevealPattern(String prefix, String suffix) {
            this.prefix = prefix;
            this.suffix = suffix == null ? "" : suffix;
        }
    }

    public static String getStructure(String message) {
        return parse(message).structure;
    }

    public static ParsedHint parse(String message) {
        if (message == null) return ParsedHint.EMPTY;
        String hint = stripFormatting(message).trim();
        if (hint.endsWith(" [C]")) {
            hint = hint.substring(0, hint.length() - 4);
        } else if (hint.endsWith("[C]")) {
            hint = hint.substring(0, hint.length() - 3);
        }
        hint = hint.trim();
        if (hint.isEmpty()) return ParsedHint.EMPTY;

        for (ThemeRevealPattern pattern : THEME_REVEAL_PATTERNS) {
            int prefixAt = hint.indexOf(pattern.prefix);
            if (prefixAt < 0) continue;

            String body = hint.substring(prefixAt + pattern.prefix.length());
            if (!pattern.suffix.isEmpty()) {
                int suffixAt = body.lastIndexOf(pattern.suffix);
                if (suffixAt >= 0) body = body.substring(0, suffixAt);
            }
            return new ParsedHint(true, structureOf(body), body);
        }

        return ParsedHint.EMPTY;
    }

    public static String actionbarThemeRevealPrefix(String langCode) {
        return themeRevealPatternForLang(langCode).prefix;
    }

    public static ThemeRevealPattern themeRevealPatternForLang(String langCode) {
        String code = langCode == null || langCode.trim().isEmpty()
                ? "en" : langCode.trim().toLowerCase(Locale.ROOT).replace('-', '_');
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

    private static List<ThemeRevealPattern> buildThemeRevealPatterns() {
        List<ThemeRevealPattern> patterns = new ArrayList<>();
        patterns.add(new ThemeRevealPattern("The theme is ", ""));
        patterns.add(new ThemeRevealPattern("\u4e3b\u9898\u662f", ""));
        patterns.add(new ThemeRevealPattern("\u4e3b\u984c\u662f", ""));
        patterns.add(new ThemeRevealPattern("Le theme est ", ""));
        patterns.add(new ThemeRevealPattern("Das Thema ist ", ""));
        patterns.add(new ThemeRevealPattern("Il tema \u00e8 ", ""));
        patterns.add(new ThemeRevealPattern("\u0422\u0435\u043c\u0430 \u2014 ", ""));
        patterns.add(new ThemeRevealPattern("El tema es ", ""));
        patterns.add(new ThemeRevealPattern("Temat \u00e4r ", ""));
        patterns.add(new ThemeRevealPattern("\u30c6\u30fc\u30de\u306f ", " \u3067\u3059"));
        patterns.add(new ThemeRevealPattern("\uc8fc\uc81c\ub294 ", "\uc785\ub2c8\ub2e4"));
        patterns.add(new ThemeRevealPattern("T\u00e9ma je ", ""));
        patterns.add(new ThemeRevealPattern("Temaet er ", ""));
        patterns.add(new ThemeRevealPattern("Het thema is ", ""));
        patterns.add(new ThemeRevealPattern("Aihe on ", ""));
        patterns.add(new ThemeRevealPattern("A t\u00e9ma", ""));
        patterns.add(new ThemeRevealPattern("Th' theme be ", ""));
        patterns.add(new ThemeRevealPattern("Motyw to ", ""));
        patterns.add(new ThemeRevealPattern("O tema \u00e9 ", ""));
        patterns.add(new ThemeRevealPattern("Tema este ", ""));
        patterns.add(new ThemeRevealPattern("Tema ", ""));
        patterns.add(new ThemeRevealPattern("\u0422\u0435\u043c\u0430: ", ""));
        return Collections.unmodifiableList(patterns);
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

    private static String structureOf(String hint) {
        List<Integer> counts = new ArrayList<>();
        int len = 0;
        for (int i = 0; i < hint.length(); i++) {
            char c = hint.charAt(i);
            if (c == ' ') {
                if (len > 0) {
                    counts.add(len);
                    len = 0;
                }
            } else {
                len++;
            }
        }
        if (len > 0) counts.add(len);

        StringBuilder out = new StringBuilder();
        for (int i = 0; i < counts.size(); i++) {
            if (i > 0) out.append('-');
            out.append(counts.get(i));
        }
        return out.toString();
    }

    private static String stripFormatting(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\u00a7' && i + 1 < value.length()) {
                i++;
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }
}
