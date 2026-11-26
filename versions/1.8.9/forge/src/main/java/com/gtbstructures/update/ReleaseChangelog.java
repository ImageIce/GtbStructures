package com.gtbstructures.update;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Turns a release body's Markdown into bounded chat text with display-only styles. */
public final class ReleaseChangelog {
    private static final int MAX_CONTENT_LINES = 8;
    private static final String TRUNCATION_NOTICE = "... Read the full changelog on GitHub.";
    private static final String RESET = "\u00a7r";
    private static final String BOLD = "\u00a7l";
    private static final String ITALIC = "\u00a7o";
    private static final int MAX_STYLE_DEPTH = 8;
    private static final Pattern INLINE_STYLE = Pattern.compile(
            "(?<ticks>`+)(?<code>[^`]+)\\k<ticks>"
            + "|(?<![\\\\\\p{L}\\p{N}*_~])(?<marker>\\*\\*\\*|___|\\*\\*|__|~~|\\*|_)"
            + "(?=\\S)(?<emphasis>.+?)(?<=\\S)\\k<marker>(?![\\p{L}\\p{N}*_~])"
            + "|(?<color>&[0-9a-fA-F])(?<colored>.+?)&[rR]");

    private ReleaseChangelog() {
    }

    static List<String> fromReleaseJson(String json) {
        return format(markdownBody(json));
    }

    static List<String> format(String markdown) {
        if (markdown == null || markdown.isEmpty()) return Collections.emptyList();

        String source = removeHtmlComments(markdown);
        List<String> content = new ArrayList<String>();
        String[] rawLines = source.split("\\r?\\n", -1);
        boolean truncated = false;
        for (String rawLine : rawLines) {
            String line = cleanMarkdownLine(rawLine);
            if (line.isEmpty()) continue;
            if (content.size() == MAX_CONTENT_LINES) {
                truncated = true;
                break;
            }
            content.add(line);
        }

        if (content.isEmpty()) return Collections.emptyList();
        if (truncated) content.add(TRUNCATION_NOTICE);
        return Collections.unmodifiableList(content);
    }

    /** Reads only the top-level GitHub release `body` string, never nested asset fields. */
    private static String markdownBody(String json) {
        if (json == null) return null;
        try {
            JsonElement parsed = new JsonParser().parse(json);
            if (!parsed.isJsonObject()) return null;
            JsonObject release = parsed.getAsJsonObject();
            JsonElement body = release.get("body");
            if (body == null || !body.isJsonPrimitive()
                    || !body.getAsJsonPrimitive().isString()) return null;
            return body.getAsString();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String cleanMarkdownLine(String raw) {
        String line = stripMinecraftFormatting(raw);
        line = stripHtmlTags(line);
        line = line.trim();
        if (line.isEmpty()) return "";

        boolean heading = line.charAt(0) == '#';
        while (!line.isEmpty() && (line.charAt(0) == '#' || line.charAt(0) == '>')) {
            line = line.substring(1).trim();
        }
        if (line.startsWith("- ") || line.startsWith("* ") || line.startsWith("+ ")) {
            line = "• " + line.substring(2).trim();
        } else if (line.matches("^\\d+[.)]\\s+.*")) {
            line = "• " + line.replaceFirst("^\\d+[.)]\\s+", "");
        }

        line = stripUnsafeControls(replaceMarkdownLinks(line)).trim();
        if (line.isEmpty()) return "";
        String style = heading ? BOLD : "";
        return style + formatInline(line, style, 0);
    }

    private static String formatInline(String line, String style, int depth) {
        if (depth >= MAX_STYLE_DEPTH) return line;
        Matcher matcher = INLINE_STYLE.matcher(line);
        StringBuilder out = new StringBuilder(line.length());
        int cursor = 0;
        while (matcher.find()) {
            out.append(line, cursor, matcher.start());
            String codeText = matcher.group("code");
            if (codeText != null) {
                out.append(codeText);
            } else {
                String color = matcher.group("color");
                String nestedStyle;
                String text;
                if (color != null) {
                    // Color codes clear text styles in Minecraft. Reapply the active emphasis.
                    boolean hasColor = !style.isEmpty()
                            && "0123456789abcdef".indexOf(style.charAt(1)) >= 0;
                    nestedStyle = "\u00a7" + Character.toLowerCase(color.charAt(1))
                            + (hasColor ? style.substring(2) : style);
                    text = matcher.group("colored");
                } else {
                    nestedStyle = withEmphasis(style, matcher.group("marker"));
                    text = matcher.group("emphasis");
                }
                boolean changed = !nestedStyle.equals(style);
                if (changed) out.append(nestedStyle);
                out.append(formatInline(text, nestedStyle, depth + 1));
                if (changed) out.append(RESET).append(style);
            }
            cursor = matcher.end();
        }
        return out.append(line, cursor, line.length()).toString();
    }

    private static String withEmphasis(String style, String marker) {
        if (marker.equals("~~")) return style.contains("\u00a7m") ? style : style + "\u00a7m";
        if (marker.length() >= 2 && !style.contains(BOLD)) style += BOLD;
        if (marker.length() != 2 && !style.contains(ITALIC)) style += ITALIC;
        return style;
    }

    private static String replaceMarkdownLinks(String line) {
        StringBuilder out = new StringBuilder(line.length());
        int cursor = 0;
        while (cursor < line.length()) {
            int open = line.indexOf('[', cursor);
            if (open < 0) {
                out.append(line.substring(cursor));
                break;
            }
            int close = line.indexOf("](", open + 1);
            int end = close < 0 ? -1 : line.indexOf(')', close + 2);
            if (end < 0) {
                out.append(line, cursor, open + 1);
                cursor = open + 1;
                continue;
            }
            out.append(line, cursor, open).append(line, open + 1, close);
            cursor = end + 1;
        }
        return out.toString();
    }

    private static String stripHtmlTags(String line) {
        StringBuilder out = new StringBuilder(line.length());
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '<') {
                int close = line.indexOf('>', i + 1);
                if (close >= 0) {
                    i = close;
                    out.append(' ');
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
    }

    private static String stripMinecraftFormatting(String line) {
        StringBuilder out = new StringBuilder(line.length());
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\u00a7') {
                if (i + 1 < line.length()) i++;
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    private static String stripUnsafeControls(String line) {
        StringBuilder out = new StringBuilder(line.length());
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (Character.isISOControl(c) || c == '\u007f'
                    || (c >= '\u202a' && c <= '\u202e')
                    || (c >= '\u2066' && c <= '\u2069')) continue;
            out.append(c);
        }
        return out.toString();
    }

    private static String removeHtmlComments(String value) {
        StringBuilder out = new StringBuilder(value.length());
        int cursor = 0;
        while (cursor < value.length()) {
            int start = value.indexOf("<!--", cursor);
            if (start < 0) {
                out.append(value.substring(cursor));
                break;
            }
            out.append(value, cursor, start);
            int end = value.indexOf("-->", start + 4);
            if (end < 0) break;
            cursor = end + 3;
        }
        return out.toString();
    }

}
