package com.gtbstructures.util;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Shared Minecraft formatting helpers from the 1.8.9 mod.
 */
public final class MinecraftFormatting {
    private static final int MAX_COMPONENT_DEPTH = 32;
    private static final String FORMAT_CODES = "0123456789abcdefklmnor";
    private static final AtomicLong REFLECTION_SCAN_COUNT = new AtomicLong();
    private static final ClassValue<NoArgMethodCache> NO_ARG_METHODS = new ClassValue<NoArgMethodCache>() {
        @Override
        protected NoArgMethodCache computeValue(Class<?> type) {
            return new NoArgMethodCache(type);
        }
    };

    private MinecraftFormatting() {
    }

    public static String normalizeSpawnEggSkin(String skin) {
        if (skin == null) return "classic";
        String normalized = skin.trim().toLowerCase();
        return "new".equals(normalized) ? "new" : "classic";
    }

    public static String normalizeMinecraftColorCode(String color, String fallback) {
        String fb = fallback == null ? "\u00a7f" : fallback;
        if (color == null) return fb;
        String value = color.trim().toLowerCase();
        if (value.startsWith("\u00a7") || value.startsWith("&")) {
            value = value.substring(1);
        }
        if (value.length() == 1 && "0123456789abcdef".indexOf(value.charAt(0)) >= 0) {
            return "\u00a7" + value;
        }
        return fb;
    }

    public static int minecraftColorArgb(String color, int alpha) {
        String normalized = normalizeMinecraftColorCode(color, "\u00a7f");
        char code = normalized.charAt(1);
        int rgb;
        switch (code) {
            case '0': rgb = 0x000000; break;
            case '1': rgb = 0x0000AA; break;
            case '2': rgb = 0x00AA00; break;
            case '3': rgb = 0x00AAAA; break;
            case '4': rgb = 0xAA0000; break;
            case '5': rgb = 0xAA00AA; break;
            case '6': rgb = 0xFFAA00; break;
            case '7': rgb = 0xAAAAAA; break;
            case '8': rgb = 0x555555; break;
            case '9': rgb = 0x5555FF; break;
            case 'a': rgb = 0x55FF55; break;
            case 'b': rgb = 0x55FFFF; break;
            case 'c': rgb = 0xFF5555; break;
            case 'd': rgb = 0xFF55FF; break;
            case 'e': rgb = 0xFFFF55; break;
            case 'f':
            default: rgb = 0xFFFFFF; break;
        }
        return ((alpha & 0xFF) << 24) | rgb;
    }

    public static String legacyFormattedText(Object component) {
        Object value = unwrapOptional(component);
        if (value == null) return "";
        if (value instanceof CharSequence) return value.toString();
        StringBuilder out = new StringBuilder();
        appendLegacyComponent(out, value, LegacyStyle.EMPTY, 0);
        String formatted = out.toString();
        return formatted.isEmpty() ? plainText(value) : formatted;
    }

    private static void appendLegacyComponent(StringBuilder out, Object component,
                                              LegacyStyle inherited, int depth) {
        Object value = unwrapOptional(component);
        if (value == null) return;
        if (value instanceof CharSequence) {
            out.append(value.toString());
            return;
        }
        if (depth > MAX_COMPONENT_DEPTH) {
            out.append(plainText(value));
            return;
        }

        List<Object> siblings = componentSiblings(value);
        LegacyStyle style = inherited.merge(componentStyle(value));
        String ownText = ownComponentText(value, siblings);
        if (!ownText.isEmpty()) {
            if (out.length() > 0) out.append("\u00a7r");
            out.append(style.codes()).append(ownText);
        }
        for (Object sibling : siblings) {
            appendLegacyComponent(out, sibling, style, depth + 1);
        }
    }

    private static String ownComponentText(Object component, List<Object> siblings) {
        String literal = literalContent(component);
        if (literal != null) return literal;

        String full = plainText(component);
        if (full.isEmpty() || siblings.isEmpty()) return full;

        StringBuilder childText = new StringBuilder();
        for (Object sibling : siblings) {
            childText.append(plainText(sibling));
        }
        String child = childText.toString();
        if (!child.isEmpty() && full.endsWith(child)) {
            return full.substring(0, full.length() - child.length());
        }
        return full;
    }

    private static String literalContent(Object component) {
        Object contents = invokeNoArg(component, "getContents", "b");
        if (contents == null) return null;
        Object text = invokeNoArg(contents, "text", "value", "string", "getString");
        return text instanceof String ? (String) text : null;
    }

    private static List<Object> componentSiblings(Object component) {
        Object raw = invokeNoArg(component, "getSiblings", "method_10855", "c");
        Object value = unwrapOptional(raw);
        if (value instanceof Iterable) {
            List<Object> siblings = new ArrayList<Object>();
            for (Object sibling : (Iterable<?>) value) {
                siblings.add(sibling);
            }
            return siblings;
        }
        if (value != null && value.getClass().isArray()) {
            List<Object> siblings = new ArrayList<Object>();
            int length = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < length; i++) {
                siblings.add(java.lang.reflect.Array.get(value, i));
            }
            return siblings;
        }
        return Collections.emptyList();
    }

    private static LegacyStyle componentStyle(Object component) {
        Object style = invokeNoArg(component, "getStyle", "method_10866", "a");
        if (style == null) return LegacyStyle.EMPTY;
        return new LegacyStyle(
                legacyColorCode(styleColor(style)),
                styleFlag(style, "isBold", "getBold", "method_10984"),
                styleFlag(style, "isItalic", "getItalic", "method_10966"),
                styleFlag(style, "isUnderlined", "getUnderlined", "isUnderline", "method_10965"),
                styleFlag(style, "isStrikethrough", "getStrikethrough", "method_10986"),
                styleFlag(style, "isObfuscated", "getObfuscated", "method_10987"));
    }

    private static String styleColor(Object style) {
        Object color = invokeNoArg(style, "getColor", "method_10973", "a");
        if (color == null) return null;
        Object serialized = invokeNoArg(color, "serialize", "getName", "asString", "method_10976", "b");
        return serialized == null ? color.toString() : serialized.toString();
    }

    private static Boolean styleFlag(Object style, String... names) {
        Object value = invokeNoArg(style, names);
        return value instanceof Boolean ? (Boolean) value : null;
    }

    private static String legacyColorCode(String raw) {
        if (raw == null) return null;
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) return null;
        if (value.startsWith("minecraft:")) value = value.substring("minecraft:".length());
        if ((value.startsWith("\u00a7") || value.startsWith("&")) && value.length() >= 2) {
            char code = Character.toLowerCase(value.charAt(1));
            return FORMAT_CODES.indexOf(code) >= 0 && "klmnor".indexOf(code) < 0
                    ? "\u00a7" + code : null;
        }
        if (value.length() == 1 && "0123456789abcdef".indexOf(value.charAt(0)) >= 0) {
            return "\u00a7" + value;
        }
        String named = legacyNamedColor(value);
        if (named != null) return named;

        int hash = value.indexOf('#');
        if (hash >= 0 && hash + 7 <= value.length()) {
            String hex = value.substring(hash, hash + 7);
            return legacyHexColor(hex);
        }
        return null;
    }

    private static String legacyNamedColor(String value) {
        if ("black".equals(value)) return "\u00a70";
        if ("dark_blue".equals(value)) return "\u00a71";
        if ("dark_green".equals(value)) return "\u00a72";
        if ("dark_aqua".equals(value)) return "\u00a73";
        if ("dark_red".equals(value)) return "\u00a74";
        if ("dark_purple".equals(value)) return "\u00a75";
        if ("gold".equals(value)) return "\u00a76";
        if ("gray".equals(value)) return "\u00a77";
        if ("dark_gray".equals(value)) return "\u00a78";
        if ("blue".equals(value)) return "\u00a79";
        if ("green".equals(value)) return "\u00a7a";
        if ("aqua".equals(value)) return "\u00a7b";
        if ("red".equals(value)) return "\u00a7c";
        if ("light_purple".equals(value)) return "\u00a7d";
        if ("yellow".equals(value)) return "\u00a7e";
        if ("white".equals(value)) return "\u00a7f";
        return null;
    }

    private static String legacyHexColor(String hex) {
        if ("#000000".equals(hex)) return "\u00a70";
        if ("#0000aa".equals(hex)) return "\u00a71";
        if ("#00aa00".equals(hex)) return "\u00a72";
        if ("#00aaaa".equals(hex)) return "\u00a73";
        if ("#aa0000".equals(hex)) return "\u00a74";
        if ("#aa00aa".equals(hex)) return "\u00a75";
        if ("#ffaa00".equals(hex)) return "\u00a76";
        if ("#aaaaaa".equals(hex)) return "\u00a77";
        if ("#555555".equals(hex)) return "\u00a78";
        if ("#5555ff".equals(hex)) return "\u00a79";
        if ("#55ff55".equals(hex)) return "\u00a7a";
        if ("#55ffff".equals(hex)) return "\u00a7b";
        if ("#ff5555".equals(hex)) return "\u00a7c";
        if ("#ff55ff".equals(hex)) return "\u00a7d";
        if ("#ffff55".equals(hex)) return "\u00a7e";
        if ("#ffffff".equals(hex)) return "\u00a7f";
        return null;
    }

    private static String plainText(Object component) {
        Object value = unwrapOptional(component);
        if (value == null) return "";
        if (value instanceof CharSequence) return value.toString();
        Object text = invokeNoArg(value, "getString", "method_10851");
        return text instanceof String ? (String) text : value.toString();
    }

    private static Object unwrapOptional(Object value) {
        if (value instanceof Optional) {
            Optional<?> optional = (Optional<?>) value;
            return optional.isPresent() ? optional.get() : null;
        }
        return value;
    }

    private static Object invokeNoArg(Object target, String... names) {
        if (target == null || names == null) return null;
        NoArgMethodCache methods = NO_ARG_METHODS.get(target.getClass());
        for (String name : names) {
            for (Method method : methods.publicMethods(name)) {
                Object value = invokeMethod(target, method);
                if (value != null) return value;
            }
        }
        for (String name : names) {
            for (Method method : methods.declaredMethods(name)) {
                Object value = invokeMethod(target, method);
                if (value != null) return value;
            }
        }
        return null;
    }

    private static Object invokeMethod(Object target, Method method) {
        try {
            return method.invoke(target);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    static long reflectionScanCountForTests() {
        return REFLECTION_SCAN_COUNT.get();
    }

    private static final class NoArgMethodCache {
        private final Class<?> type;
        private final ConcurrentHashMap<String, List<Method>> publicMethods =
                new ConcurrentHashMap<String, List<Method>>();
        private final ConcurrentHashMap<String, List<Method>> declaredMethods =
                new ConcurrentHashMap<String, List<Method>>();

        private NoArgMethodCache(Class<?> type) {
            this.type = type;
        }

        private List<Method> publicMethods(String name) {
            if (name == null) return Collections.emptyList();
            return publicMethods.computeIfAbsent(name, this::scanPublicMethods);
        }

        private List<Method> declaredMethods(String name) {
            if (name == null) return Collections.emptyList();
            return declaredMethods.computeIfAbsent(name, this::scanDeclaredMethods);
        }

        private List<Method> scanPublicMethods(String name) {
            REFLECTION_SCAN_COUNT.incrementAndGet();
            List<Method> matches = new ArrayList<Method>();
            for (Method method : type.getMethods()) {
                if (method.getParameterCount() == 0 && method.getName().equals(name)
                        && makeAccessible(method)) {
                    matches.add(method);
                }
            }
            return immutableMethods(matches);
        }

        private List<Method> scanDeclaredMethods(String name) {
            REFLECTION_SCAN_COUNT.incrementAndGet();
            List<Method> matches = new ArrayList<Method>();
            Class<?> current = type;
            while (current != null) {
                for (Method method : current.getDeclaredMethods()) {
                    if (method.getParameterCount() == 0 && method.getName().equals(name)
                            && makeAccessible(method)) {
                        matches.add(method);
                    }
                }
                current = current.getSuperclass();
            }
            return immutableMethods(matches);
        }

        private static boolean makeAccessible(Method method) {
            try {
                return method.trySetAccessible();
            } catch (RuntimeException e) {
                return false;
            }
        }

        private static List<Method> immutableMethods(List<Method> methods) {
            return methods.isEmpty()
                    ? Collections.<Method>emptyList()
                    : Collections.unmodifiableList(methods);
        }
    }

    private static final class LegacyStyle {
        private static final LegacyStyle EMPTY = new LegacyStyle(null, null, null, null, null, null);

        private final String color;
        private final Boolean bold;
        private final Boolean italic;
        private final Boolean underlined;
        private final Boolean strikethrough;
        private final Boolean obfuscated;

        private LegacyStyle(String color, Boolean bold, Boolean italic, Boolean underlined,
                            Boolean strikethrough, Boolean obfuscated) {
            this.color = color;
            this.bold = bold;
            this.italic = italic;
            this.underlined = underlined;
            this.strikethrough = strikethrough;
            this.obfuscated = obfuscated;
        }

        private LegacyStyle merge(LegacyStyle child) {
            if (child == null) return this;
            return new LegacyStyle(
                    child.color != null ? child.color : color,
                    child.bold != null ? child.bold : bold,
                    child.italic != null ? child.italic : italic,
                    child.underlined != null ? child.underlined : underlined,
                    child.strikethrough != null ? child.strikethrough : strikethrough,
                    child.obfuscated != null ? child.obfuscated : obfuscated);
        }

        private String codes() {
            StringBuilder out = new StringBuilder(12);
            if (color != null) out.append(color);
            if (Boolean.TRUE.equals(obfuscated)) out.append("\u00a7k");
            if (Boolean.TRUE.equals(bold)) out.append("\u00a7l");
            if (Boolean.TRUE.equals(strikethrough)) out.append("\u00a7m");
            if (Boolean.TRUE.equals(underlined)) out.append("\u00a7n");
            if (Boolean.TRUE.equals(italic)) out.append("\u00a7o");
            return out.toString();
        }
    }
}
