package com.gtbstructures;

import com.gtbstructures.hints.HintStructureParser;
import com.gtbstructures.diagnostics.RawPacketRecorder;
import com.gtbstructures.stats.GameStats;
import com.gtbstructures.stats.LangPatterns;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Shared live-event intake used by modern loader entrypoints. */
public final class ModernRuntimeBridge {
    public static final long HINT_OVERLAY_DURATION_MS = 2500L;
    public static final long ACTIONBAR_HINT_DURATION_MS = 3000L;
    private static final long HINT_FADE_DURATION_MS = 500L;

    private static String currentActionbarHintText = "";
    private static String currentStructure = "";
    private static String lastScoreboardTitle = "";
    private static List<String> lastScoreboardLines = Collections.emptyList();
    private static long actionbarHintStartTime;
    private static long overlayStartTime;
    private static long lastNonEmptyScoreboardAt;
    private static long emptyScoreboardPollStart;
    private static final long TRANSIENT_EMPTY_SCOREBOARD_GRACE_MS = 2500L;

    private ModernRuntimeBridge() {
    }

    public static void onClientTick() {
        GameStats.INSTANCE.fireGtbTick();
    }

    public static void onClientUnavailable() {
        currentActionbarHintText = "";
        currentStructure = "";
        actionbarHintStartTime = 0L;
        overlayStartTime = 0L;
        lastScoreboardTitle = "";
        lastScoreboardLines = Collections.emptyList();
        lastNonEmptyScoreboardAt = 0L;
        emptyScoreboardPollStart = 0L;
        GameStats.INSTANCE.onServerSessionReset();
        GameStats.INSTANCE.onScoreboardTick(null, null, null);
        GameStats.INSTANCE.captureSidebar(null, null);
    }

    public static void onChat(String text) {
        onChat(text, text);
    }

    public static void onChat(String raw, String plain) {
        String rawText = raw == null ? "" : raw;
        String clean = cleanText(plain == null ? rawText : plain);
        if (clean.isEmpty()) return;
        RawPacketRecorder.INSTANCE.captureInboundChat("chat", rawText, 0);
        GameStats.INSTANCE.onChatMessage(rawText, clean);
    }

    public static void onActionbar(String text) {
        long now = System.currentTimeMillis();
        RawPacketRecorder.INSTANCE.captureInboundChat("actionbar", text == null ? "" : text, 2);
        HintStructureParser.ParsedHint parsed = HintStructureParser.parse(text);
        boolean visibleHint = parsed.isHint && isHintOverlayState(GameStats.INSTANCE.getState());
        if (visibleHint) {
            currentActionbarHintText = text == null ? "" : text;
            actionbarHintStartTime = now;
        } else {
            currentActionbarHintText = "";
            actionbarHintStartTime = 0L;
        }
        currentStructure = visibleHint && parsed.structure != null ? parsed.structure : "";
        if (!currentStructure.isEmpty()) {
            overlayStartTime = now;
        } else {
            overlayStartTime = 0L;
        }
        GameStats.INSTANCE.onHint(parsed, now);
    }

    private static boolean isHintOverlayState(GameStats.TrackingState state) {
        return GameStats.isActiveGame(state) || state == GameStats.TrackingState.PAUSED;
    }

    public static String currentActionbarHintText(long now) {
        if (currentActionbarHintText.isEmpty() || actionbarHintStartTime <= 0L) return "";
        long elapsed = now - actionbarHintStartTime;
        return elapsed >= 0L && elapsed <= ACTIONBAR_HINT_DURATION_MS ? currentActionbarHintText : "";
    }

    public static String currentHintStructure(long now) {
        if (currentStructure.isEmpty() || overlayStartTime <= 0L) return "";
        long elapsed = now - overlayStartTime;
        return elapsed >= 0L && elapsed <= HINT_OVERLAY_DURATION_MS ? currentStructure : "";
    }

    public static int currentHintAlpha(long now) {
        if (currentHintStructure(now).isEmpty()) return 0;
        long elapsed = now - overlayStartTime;
        long fadeStart = HINT_OVERLAY_DURATION_MS - HINT_FADE_DURATION_MS;
        if (elapsed <= fadeStart) return 255;
        long remaining = HINT_OVERLAY_DURATION_MS - elapsed;
        return Math.max(0, Math.min(255, (int) ((remaining * 255L + HINT_FADE_DURATION_MS - 1L)
                / HINT_FADE_DURATION_MS)));
    }

    public static String formatHintStructure(String structure, String colorBrackets,
                                             String colorNumbers, String colorHyphens) {
        String value = structure == null ? "" : structure;
        String brackets = colorBrackets == null ? "" : colorBrackets;
        String numbers = colorNumbers == null ? "" : colorNumbers;
        String hyphens = colorHyphens == null ? "" : colorHyphens;
        return brackets + "[ " + numbers
                + value.replace("-", hyphens + "-" + numbers)
                + brackets + " ]";
    }

    public static void onScoreboard(String title, List<String> visibleLines) {
        List<String> rawLines = visibleLines == null
                ? Collections.<String>emptyList()
                : new ArrayList<String>(visibleLines);
        long now = System.currentTimeMillis();
        String rawTitle = title == null ? "" : title;
        if (shouldIgnoreTransientEmptyScoreboard(rawTitle, rawLines, now)) {
            RawPacketRecorder.INSTANCE.captureOverlayDecision(
                    "scoreboard_poll", "transient_empty_ignored",
                    "recent_gtb_sidebar");
            return;
        }

        lastScoreboardTitle = rawTitle;
        lastScoreboardLines = Collections.unmodifiableList(new ArrayList<String>(rawLines));
        RawPacketRecorder.INSTANCE.captureScoreboardSnapshot(lastScoreboardTitle, rawLines);
        if (!rawTitle.isEmpty() || !rawLines.isEmpty()) {
            lastNonEmptyScoreboardAt = now;
            emptyScoreboardPollStart = 0L;
        }
        List<String> cleanLines = new ArrayList<String>(rawLines.size());
        for (String line : rawLines) {
            cleanLines.add(cleanText(line));
        }
        GameStats.INSTANCE.onScoreboardTick(cleanText(title), cleanLines, rawLines);
        GameStats.INSTANCE.captureSidebar(cleanText(title), cleanLines);
    }

    private static boolean shouldIgnoreTransientEmptyScoreboard(String title, List<String> rawLines, long now) {
        if ((title != null && !title.isEmpty()) || (rawLines != null && !rawLines.isEmpty())) {
            return false;
        }
        if (!GameStats.isActiveGame(GameStats.INSTANCE.getState())) return false;
        if (lastScoreboardLines.isEmpty() && lastScoreboardTitle.isEmpty()) return false;
        if (lastNonEmptyScoreboardAt <= 0L) return false;
        if (emptyScoreboardPollStart <= 0L) emptyScoreboardPollStart = now;
        long sinceLastSidebar = now - lastNonEmptyScoreboardAt;
        long emptyDuration = now - emptyScoreboardPollStart;
        return sinceLastSidebar >= 0L
                && sinceLastSidebar <= TRANSIENT_EMPTY_SCOREBOARD_GRACE_MS
                && emptyDuration <= TRANSIENT_EMPTY_SCOREBOARD_GRACE_MS;
    }

    public static boolean onThemeSelectionScreen(String title, String[] themes, String localPlayerName) {
        String cleanTitle = cleanText(title);
        if (!GameStats.INSTANCE.isThemeSelectionGui(cleanTitle)) return false;
        if (!GameStats.INSTANCE.isInActiveRound() || GameStats.INSTANCE.getCurrentRound() == null) {
            return false;
        }
        return GameStats.INSTANCE.onGuiThemePick(themes, localPlayerName);
    }

    public static String lastScoreboardTitle() {
        return lastScoreboardTitle;
    }

    public static List<String> lastScoreboardLines() {
        return lastScoreboardLines;
    }

    public static String cleanText(String value) {
        if (value == null) return "";
        String stripped = stripMinecraftFormatting(value);
        stripped = LangPatterns.stripEmojis(stripped);
        return Normalizer.normalize(stripped, Normalizer.Form.NFC).trim();
    }

    public static String stripMinecraftFormatting(String value) {
        if (value == null) return "";
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

    static String currentStructureForTest() {
        return currentStructure;
    }

    static String currentActionbarHintTextForTest() {
        return currentActionbarHintText;
    }

    static long overlayStartTimeForTest() {
        return overlayStartTime;
    }

    static long actionbarHintStartTimeForTest() {
        return actionbarHintStartTime;
    }
}
