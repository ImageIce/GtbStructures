package com.gtbstructures.update;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Turns the release body's Markdown into short, inert chat lines. */
public final class ReleaseChangelog {
    private static final int MAX_CONTENT_LINES = 8;
    private static final int MAX_LINE_CHARS = 160;
    private static final String TRUNCATION_NOTICE = "... Read the full changelog on GitHub.";

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
            if (line.length() > MAX_LINE_CHARS) {
                content.add(clip(line, MAX_LINE_CHARS - 3) + "...");
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
            JsonElement parsed = JsonParser.parseString(json);
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
        line = stripHtmlTags(line).trim();
        if (line.isEmpty()) return "";

        while (!line.isEmpty() && (line.charAt(0) == '#' || line.charAt(0) == '>')) {
            line = line.substring(1).trim();
        }
        if (line.startsWith("- ") || line.startsWith("* ") || line.startsWith("+ ")) {
            line = "• " + line.substring(2).trim();
        } else if (line.matches("^\\d+[.)]\\s+.*")) {
            line = "• " + line.replaceFirst("^\\d+[.)]\\s+", "");
        }

        line = replaceMarkdownLinks(line);
        line = line.replace("`", "")
                .replaceAll("\\*\\*(.+?)\\*\\*", "$1")
                .replaceAll("__(.+?)__", "$1")
                .replaceAll("(?<![\\p{L}\\p{N}])\\*(.+?)\\*(?![\\p{L}\\p{N}])", "$1")
                .replaceAll("(?<![\\p{L}\\p{N}])_(.+?)_(?![\\p{L}\\p{N}])", "$1");
        return stripUnsafeControls(line).trim();
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
            if (c == '\u00a7' && i + 1 < line.length()) {
                i++;
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

    private static String clip(String value, int maxChars) {
        int end = Math.min(maxChars, value.length());
        if (end > 0 && end < value.length()
                && Character.isHighSurrogate(value.charAt(end - 1))
                && Character.isLowSurrogate(value.charAt(end))) end--;
        return value.substring(0, end);
    }
}
