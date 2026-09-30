package com.gtbstructures.translation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Platform-neutral core for /gtbs copytranslation.
 */
public final class CopyTranslation {
    private static final String[] LANGUAGE_CODES = {
            "en", "fr", "de", "it", "nl", "es", "pt_BR", "pt", "ru", "ko", "pl", "ja",
            "pirate", "no", "sv", "tr", "da", "cs", "fi", "zh_CN", "zh_TW", "ro", "hu", "uk"
    };

    private CopyTranslation() {
    }

    public static Result buildMessage(String themeInput, List<String> requestedLangCodes,
                                      boolean includePrefix) {
        return buildMessage(themeInput, requestedLangCodes, includePrefix, true);
    }

    public static Result buildMessage(String themeInput, List<String> requestedLangCodes,
                                      boolean includePrefix, boolean capitalizeTranslations) {
        if (!TranslationData.isLoaded()) {
            return Result.error("Translations are still loading; try again in a few seconds.");
        }
        String english = TranslationData.resolveEnglishTheme(themeInput);
        if (english == null) {
            return Result.error("Unknown English theme: \u00a7f" + themeInput);
        }

        List<String> langCodes = requestedLangCodes;
        if (langCodes == null || langCodes.isEmpty()) {
            langCodes = allLanguageCodes();
        }
        LinkedHashSet<String> uniqueTranslations = new LinkedHashSet<String>();
        for (String code : langCodes) {
            String displayed = TranslationData.getDisplayedTheme(code, english);
            if (displayed != null && !displayed.isEmpty()) {
                uniqueTranslations.add(capitalizeTranslations
                        ? capitalizeFirstTranslationLetter(displayed)
                        : lowercaseTranslation(displayed));
            }
        }
        if (uniqueTranslations.isEmpty()) {
            return Result.error("No translations found for: \u00a7f" + english);
        }

        StringBuilder sb = new StringBuilder();
        if (includePrefix) sb.append("The theme is ");
        int i = 0;
        for (String translation : uniqueTranslations) {
            if (i++ > 0) sb.append(", ");
            sb.append(translation);
        }
        return Result.ok(sb.toString());
    }

    static String capitalizeFirstTranslationLetter(String value) {
        if (value == null || value.isEmpty()) return value;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            int codePointLength = Character.charCount(codePoint);
            if (Character.isLetter(codePoint)) {
                String original = value.substring(offset, offset + codePointLength);
                String replacement = original.toUpperCase(Locale.ROOT);
                if (original.equals(replacement)) return value;
                return value.substring(0, offset) + replacement
                        + value.substring(offset + codePointLength);
            }
            offset += codePointLength;
        }
        return value;
    }

    static String lowercaseTranslation(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    public static Request parseRequest(String[] args) {
        if (args == null || args.length == 0) return null;
        String fullTheme = joinArgs(args, 0, args.length);
        if (TranslationData.resolveEnglishTheme(fullTheme) != null) {
            return new Request(fullTheme, null, null);
        }
        for (int split = args.length - 1; split >= 1; split--) {
            String themePrefix = joinArgs(args, 0, split);
            if (TranslationData.resolveEnglishTheme(themePrefix) != null) {
                LanguageListParse parsed = parseLanguageList(joinArgs(args, split, args.length));
                if (parsed.ok) {
                    return new Request(themePrefix, parsed.codes, null);
                }
                return new Request(themePrefix, null,
                        "Unknown language: \u00a7f" + parsed.invalid
                                + "\u00a77. Available codes/names: \u00a7f" + availableLanguageCodes());
            }
        }
        return new Request(fullTheme, null, null);
    }

    public static List<String> allLanguageCodes() {
        ArrayList<String> out = new ArrayList<String>();
        Collections.addAll(out, LANGUAGE_CODES);
        return out;
    }

    public static String availableLanguageCodes() {
        StringBuilder sb = new StringBuilder();
        List<String> codes = allLanguageCodes();
        for (int i = 0; i < codes.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(codes.get(i));
        }
        return sb.toString();
    }

    public static String resolveLanguageCode(String raw) {
        if (raw == null) return null;
        String token = raw.trim();
        if (token.isEmpty()) return null;
        String lower = token.toLowerCase(Locale.ROOT);
        if (lower.equals("jp")) lower = "ja";
        if (lower.equals("ptbr") || lower.equals("pt-br") || lower.equals("br")) lower = "pt_BR";
        if (lower.equals("zhcn") || lower.equals("zh-cn") || lower.equals("cn")) lower = "zh_CN";
        if (lower.equals("zhtw") || lower.equals("zh-tw") || lower.equals("tw")) lower = "zh_TW";
        String compact = compactLanguageName(lower);
        if (compact.equals("portuguesebr") || compact.equals("portuguesebrazil")
                || compact.equals("brazilianportuguese")) lower = "pt_BR";
        if (compact.equals("chinesesimplified") || compact.equals("simplifiedchinese")) lower = "zh_CN";
        if (compact.equals("chinesetraditional") || compact.equals("traditionalchinese")) lower = "zh_TW";
        if (compact.equals("piratespeak")) lower = "pirate";

        String normalized = TranslationData.normalizeLangCode(lower);
        for (String code : LANGUAGE_CODES) {
            if (code.equalsIgnoreCase(lower)) return code;
            String codeNorm = TranslationData.normalizeLangCode(code);
            if (codeNorm != null && codeNorm.equalsIgnoreCase(normalized)) return code;
        }

        String byNativeName = codeFromNativeName(token);
        if (byNativeName != null) return byNativeName;

        for (String code : LANGUAGE_CODES) {
            String displayName = displayName(code);
            if (displayName != null && compactLanguageName(displayName).equals(compact)) {
                return code;
            }
        }
        return null;
    }

    private static LanguageListParse parseLanguageList(String raw) {
        if (raw == null || raw.trim().isEmpty()) return LanguageListParse.error(raw);
        String[] pieces = raw.split(",");
        ArrayList<String> out = new ArrayList<String>();
        HashSet<String> seen = new HashSet<String>();
        for (String piece : pieces) {
            String code = resolveLanguageCode(piece);
            if (code == null) return LanguageListParse.error(piece);
            String key = code.toLowerCase(Locale.ROOT);
            if (seen.add(key)) out.add(code);
        }
        return out.isEmpty() ? LanguageListParse.error(raw) : LanguageListParse.ok(out);
    }

    private static String joinArgs(String[] args, int start, int endExclusive) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < endExclusive; i++) {
            if (i > start) sb.append(' ');
            sb.append(args[i]);
        }
        return sb.toString().trim();
    }

    private static String displayName(String code) {
        if (code == null) return null;
        String lower = code.toLowerCase(Locale.ROOT);
        if ("en".equals(lower)) return "English";
        if ("fr".equals(lower)) return "French";
        if ("de".equals(lower)) return "German";
        if ("it".equals(lower)) return "Italian";
        if ("nl".equals(lower)) return "Dutch";
        if ("es".equals(lower)) return "Spanish";
        if ("pt_br".equals(lower)) return "Portuguese (BR)";
        if ("pt".equals(lower)) return "Portuguese";
        if ("ru".equals(lower)) return "Russian";
        if ("ko".equals(lower)) return "Korean";
        if ("pl".equals(lower)) return "Polish";
        if ("ja".equals(lower)) return "Japanese";
        if ("pirate".equals(lower)) return "Pirate Speak";
        if ("no".equals(lower)) return "Norwegian";
        if ("sv".equals(lower)) return "Swedish";
        if ("tr".equals(lower)) return "Turkish";
        if ("da".equals(lower)) return "Danish";
        if ("cs".equals(lower)) return "Czech";
        if ("fi".equals(lower)) return "Finnish";
        if ("zh_cn".equals(lower)) return "Chinese (Simplified)";
        if ("zh_tw".equals(lower)) return "Chinese (Traditional)";
        if ("ro".equals(lower)) return "Romanian";
        if ("hu".equals(lower)) return "Hungarian";
        if ("uk".equals(lower)) return "Ukrainian";
        return code;
    }

    private static String codeFromNativeName(String raw) {
        if (raw == null) return null;
        String lower = raw.toLowerCase(Locale.ROOT);
        if ("english".equals(lower)) return "en";
        if ("\u010de\u0161tina".equals(lower) || "cestina".equals(lower)) return "cs";
        if ("dansk".equals(lower)) return "da";
        if ("deutsch".equals(lower)) return "de";
        if ("espa\u00f1ol".equals(lower) || "espanol".equals(lower)) return "es";
        if ("fran\u00e7ais".equals(lower) || "francais".equals(lower)) return "fr";
        if ("italiano".equals(lower)) return "it";
        if ("magyar".equals(lower)) return "hu";
        if ("nederlands".equals(lower)) return "nl";
        if ("norsk".equals(lower)) return "no";
        if ("polski".equals(lower)) return "pl";
        if ("portugu\u00eas".equals(lower) || "portugues".equals(lower)) return "pt";
        if ("portugu\u00eas (br)".equals(lower) || "portugues (br)".equals(lower)) return "pt_BR";
        if ("rom\u00e2n\u0103".equals(lower) || "romana".equals(lower)) return "ro";
        if ("suomi".equals(lower)) return "fi";
        if ("svenska".equals(lower)) return "sv";
        if ("t\u00fcrk\u00e7e".equals(lower) || "turkce".equals(lower)) return "tr";
        if ("pirate speak".equals(lower)) return "pirate";
        if ("\u0440\u0443\u0441\u0441\u043a\u0438\u0439".equals(lower)) return "ru";
        if ("\u0443\u043a\u0440\u0430\u0457\u043d\u0441\u044c\u043a\u0430".equals(lower)) return "uk";
        if ("\ud55c\uad6d\uc5b4".equals(lower)) return "ko";
        if ("\u65e5\u672c\u8a9e".equals(lower)) return "ja";
        if ("\u7b80\u4f53\u4e2d\u6587".equals(lower)) return "zh_CN";
        if ("\u7e41\u9ad4\u4e2d\u6587".equals(lower)) return "zh_TW";
        return null;
    }

    private static String compactLanguageName(String raw) {
        if (raw == null) return "";
        String lower = raw.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static final class Request {
        public final String theme;
        public final List<String> langCodes;
        public final String langError;

        private Request(String theme, List<String> langCodes, String langError) {
            this.theme = theme;
            this.langCodes = langCodes == null ? null
                    : Collections.unmodifiableList(new ArrayList<String>(langCodes));
            this.langError = langError;
        }
    }

    private static final class LanguageListParse {
        final boolean ok;
        final List<String> codes;
        final String invalid;

        private LanguageListParse(boolean ok, List<String> codes, String invalid) {
            this.ok = ok;
            this.codes = codes;
            this.invalid = invalid;
        }

        static LanguageListParse ok(List<String> codes) {
            return new LanguageListParse(true, codes, null);
        }

        static LanguageListParse error(String invalid) {
            return new LanguageListParse(false, null, invalid == null ? "" : invalid.trim());
        }
    }

    public static final class Result {
        public final boolean ok;
        public final String message;
        public final String error;

        private Result(boolean ok, String message, String error) {
            this.ok = ok;
            this.message = message;
            this.error = error;
        }

        static Result ok(String message) {
            return new Result(true, message, null);
        }

        static Result error(String error) {
            return new Result(false, null, error);
        }
    }
}
