package com.gtbstructures.scoreboard;

import com.gtbstructures.GtbStructuresSettings;
import com.gtbstructures.hints.HintStructureParser;
import com.gtbstructures.stats.GameStats;
import com.gtbstructures.stats.LangPatterns;
import com.gtbstructures.translation.TranslationData;

/**
 * Platform-neutral interface preview content. Loader renderers can use these
 * rows to draw the same session-only preview UI as the 1.8.9 source.
 */
public final class InterfacePreview {
    public static final InterfacePreview INSTANCE = new InterfacePreview();

    private static final String PREVIEW_THEME = "Swimming Pool";
    private static final String PREVIEW_DATE = "\u00a77" + "05/09/26  \u00a78m38D1AF";
    private static final String PREVIEW_FOOTER = "\u00a7ewww.hypixel.net";
    private static final int COOLDOWN_BAR_WIDTH = 42;
    private static ScreenController screenController;

    private boolean enabled;
    private boolean mockActionbarVisible = true;
    private int previewTicks;
    private String previewSessionKey = "";

    private InterfacePreview() {
    }

    public boolean toggle() {
        enabled = !enabled;
        mockActionbarVisible = true;
        previewTicks = 0;
        if (enabled) {
            previewSessionKey = currentSessionKey(screenController);
            openPreviewScreen();
        } else {
            closePreviewScreen();
        }
        return enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public static boolean isActive() {
        return INSTANCE.enabled;
    }

    public void disableSilently() {
        enabled = false;
        mockActionbarVisible = true;
        previewTicks = 0;
        previewSessionKey = "";
        closePreviewScreen();
    }

    public static void setScreenController(ScreenController controller) {
        screenController = controller;
        if (INSTANCE.enabled) {
            INSTANCE.previewSessionKey = INSTANCE.currentSessionKey(controller);
            INSTANCE.openPreviewScreen();
        }
    }

    public static void tick() {
        INSTANCE.tickInternal();
    }

    private void tickInternal() {
        if (!enabled) return;
        previewTicks++;
        ScreenController controller = screenController;
        if (controller == null) return;
        if (!controller.isInGame()) {
            disableSilently();
            return;
        }
        String currentSessionKey = currentSessionKey(controller);
        if (!currentSessionKey.isEmpty()) {
            if (previewSessionKey.isEmpty()) {
                previewSessionKey = currentSessionKey;
            } else if (!previewSessionKey.equals(currentSessionKey)) {
                disableSilently();
                return;
            }
        }
        if (!controller.isPreviewScreenOpen()) {
            openPreviewScreen();
        }
    }

    private String currentSessionKey(ScreenController controller) {
        if (controller == null) return "";
        try {
            String key = controller.currentSessionKey();
            return key == null ? "" : key.trim();
        } catch (RuntimeException e) {
            return "";
        }
    }

    private void openPreviewScreen() {
        if (screenController != null) screenController.openPreviewScreen();
    }

    private void closePreviewScreen() {
        if (screenController != null) screenController.closePreviewScreen();
    }

    static boolean isMockActionbarVisible() {
        return INSTANCE.mockActionbarVisible;
    }

    static void setMockActionbarVisible(boolean visible) {
        INSTANCE.mockActionbarVisible = visible;
    }

    static int previewTicks() {
        return INSTANCE.previewTicks;
    }

    static String previewLangCodeForTest(String current, String fallback) {
        return previewLangCode(current, fallback);
    }

    public static String previewLangCode() {
        return previewLangCode(GameStats.INSTANCE.getCurrentLangCode(),
                GtbStructuresSettings.INSTANCE.lastLangCode);
    }

    public static String previewLangCode(String current, String fallback) {
        String currentCode = normalizePreviewLang(current);
        String fallbackCode = normalizePreviewLang(fallback);
        if (!currentCode.isEmpty() && !isEnglishLike(currentCode)) return currentCode;
        if (!fallbackCode.isEmpty() && !isEnglishLike(fallbackCode)) return fallbackCode;
        if (!currentCode.isEmpty()) return currentCode;
        if (!fallbackCode.isEmpty()) return fallbackCode;
        return "en";
    }

    private static String normalizePreviewLang(String langCode) {
        return langCode == null ? "" : langCode.trim();
    }

    private static boolean isEnglishLike(String langCode) {
        if (langCode == null) return false;
        String key = langCode.trim().toLowerCase(java.util.Locale.ROOT);
        return "en".equals(key) || "en_us".equals(key) || "en-us".equals(key);
    }

    static String localizedPreviewThemeForTest(String langCode) {
        return localizedPreviewTheme(langCode);
    }

    public static String localizedPreviewTheme(String langCode) {
        TranslationData.ensureLocalLoaded();
        String displayed = TranslationData.getDisplayedTheme(langCode, PREVIEW_THEME);
        return displayed == null || displayed.trim().isEmpty() ? PREVIEW_THEME : displayed.trim();
    }

    static String previewActionbarTextForTest(String langCode) {
        return previewActionbarText(langCode);
    }

    public static String previewActionbarText(String langCode) {
        LangPatterns.ThemeRevealPattern reveal = LangPatterns.themeRevealPatternForLang(langCode);
        String suffix = reveal.suffix == null ? "" : reveal.suffix;
        return "\u00a7b" + reveal.prefix + "\u00a7e" + localizedPreviewTheme(langCode)
                + (suffix.isEmpty() ? "" : "\u00a7b" + suffix);
    }

    static String previewHintStructureForTest(String langCode) {
        return previewHintStructure(langCode);
    }

    public static String previewHintStructure(String langCode) {
        HintStructureParser.ParsedHint parsed = HintStructureParser.parse(previewActionbarText(langCode));
        return parsed.isHint ? parsed.structure : lengthBucket(localizedPreviewTheme(langCode));
    }

    static int cooldownBarWidthForTest() {
        return COOLDOWN_BAR_WIDTH;
    }

    static String vanillaPreviewLineForTest(String lang, int index) {
        PreviewRow[] rows = vanillaRows(lang);
        return index >= 0 && index < rows.length ? rows[index].left : null;
    }

    static String vanillaPreviewRightForTest(String lang, int index) {
        PreviewRow[] rows = vanillaRows(lang);
        return index >= 0 && index < rows.length ? rows[index].right : null;
    }

    static String hypixelPreviewLineForTest(String langCode, int index) {
        return hypixelPreviewLineForTest(langCode, index, false);
    }

    static String hypixelPreviewLineForTest(String langCode, int index, boolean showRanks) {
        PreviewRow[] rows = hypixelRows(langCode, showRanks);
        return index >= 0 && index < rows.length ? rows[index].left : "";
    }

    static String hypixelPreviewRightForTest(String langCode, int index) {
        PreviewRow[] rows = hypixelRows(langCode, false);
        return index >= 0 && index < rows.length ? rows[index].right : "";
    }

    public static PreviewRow[] vanillaRows(String lang) {
        PreviewPlayer[] players = previewPlayers();
        PreviewRow[] rows = new PreviewRow[13];
        PreviewPlayer[] top = topPreviewPlayers(players, 3);
        int i = 0;
        rows[i++] = new PreviewRow(PREVIEW_DATE, "\u00a7c13");
        rows[i++] = new PreviewRow("\u00a7f" + LangPatterns.scoreboardBuilderLabel(lang), "\u00a7c12");
        rows[i++] = new PreviewRow(" \u00a77" + builderName(players), "\u00a7c11");
        rows[i++] = new PreviewRow("", "\u00a7c10");
        for (int p = 0; p < top.length; p++) {
            int sidebarScore = 9 - p;
            rows[i++] = new PreviewRow(top[p].nameColor + top[p].name
                    + "\u00a7f: \u00a7a" + top[p].total, "\u00a7c" + sidebarScore);
        }
        rows[i++] = new PreviewRow("", "\u00a7c6");
        rows[i++] = new PreviewRow("\u00a7f" + labelWithTrailingSpace(
                LangPatterns.scoreboardTimeLabel(lang)) + "\u00a7a00:13", "\u00a7c5");
        rows[i++] = new PreviewRow("", "\u00a7c4");
        rows[i++] = new PreviewRow("\u00a7f" + LangPatterns.scoreboardThemeLabel(lang), "\u00a7c3");
        rows[i++] = new PreviewRow(" \u00a7a" + localizedPreviewTheme(lang), "\u00a7c2");
        rows[i++] = new PreviewRow(PREVIEW_FOOTER, "\u00a7c1");
        return rows;
    }

    public static PreviewRow[] hypixelRows(String lang, boolean expanded) {
        PreviewPlayer[] players = previewPlayers();
        ScoreboardConfig config = GtbStructuresSettings.INSTANCE.sbConfig;
        PreviewRow[] rows = new PreviewRow[players.length + 10];
        int[] places = previewDensePlaces(players);
        int i = 0;
        rows[i++] = new PreviewRow(PREVIEW_DATE, "");
        rows[i++] = new PreviewRow("\u00a7f" + LangPatterns.scoreboardBuilderLabel(lang), "");
        rows[i++] = new PreviewRow(" \u00a77" + builderName(players), "");
        rows[i++] = new PreviewRow("", "");
        for (int p = 0; p < players.length; p++) {
            PreviewPlayer player = players[p];
            rows[i++] = new PreviewRow(playerLeft(player, config, expanded, false, lang, places[p]),
                    playerRight(player, config, expanded));
        }
        rows[i++] = new PreviewRow("", "");
        rows[i++] = new PreviewRow("\u00a7f" + labelWithTrailingSpace(
                LangPatterns.scoreboardTimeLabel(lang)) + "\u00a7a00:13", "");
        rows[i++] = new PreviewRow("", "");
        rows[i++] = new PreviewRow("\u00a7f" + LangPatterns.scoreboardThemeLabel(lang), "");
        rows[i++] = new PreviewRow(" \u00a7a" + localizedPreviewTheme(lang), "");
        rows[i++] = new PreviewRow(PREVIEW_FOOTER, "");
        return rows;
    }

    private static PreviewPlayer[] previewPlayers() {
        return new PreviewPlayer[] {
                new PreviewPlayer("Player598", "\u00a7b", "\u00a73\u03c0", 2, 26, -1, false, 15),
                new PreviewPlayer("Player742", "\u00a77", "\u00a7f\u263a", 3, 26, -1, false, 7),
                new PreviewPlayer("Player314", "\u00a7a", "\u00a7f\u263a", 1, 13, -1, false, 3),
                new PreviewPlayer("Player807", "\u00a77", "\u00a7f\u263a", 0, 9, -1, false, 10),
                new PreviewPlayer("Player621", "\u00a7b", "\u00a7f\u263a", 1, 7, -1, false, 5),
                new PreviewPlayer("Player143", "\u00a77", "\u00a7f\u263a", 3, 6, -1, true, 8),
                new PreviewPlayer("Player905", "\u00a7a", "\u00a7f\u263a", 0, 5, -1, false, 2),
                new PreviewPlayer("Player267", "\u00a77", "\u00a7f\u263a", 0, 4, -1, false, 6),
                new PreviewPlayer("Player781", "\u00a77", "\u00a7f\u263a", 0, 3, -1, false, 4),
                new PreviewPlayer("Player432", "\u00a77", "\u00a7f\u263a", 0, 3, 2, false, 1)
        };
    }

    private static PreviewPlayer[] topPreviewPlayers(PreviewPlayer[] players, int limit) {
        if (players == null || limit <= 0) return new PreviewPlayer[0];
        PreviewPlayer[] out = new PreviewPlayer[Math.min(limit, players.length)];
        for (PreviewPlayer player : players) {
            if (player == null || player.currentBuilder) continue;
            for (int i = 0; i < out.length; i++) {
                if (out[i] == null || player.total > out[i].total) {
                    for (int j = out.length - 1; j > i; j--) out[j] = out[j - 1];
                    out[i] = player;
                    break;
                }
            }
        }
        int count = 0;
        while (count < out.length && out[count] != null) count++;
        if (count == out.length) return out;
        PreviewPlayer[] trimmed = new PreviewPlayer[count];
        for (int i = 0; i < count; i++) trimmed[i] = out[i];
        return trimmed;
    }

    private static int[] previewDensePlaces(PreviewPlayer[] players) {
        int[] places = new int[players == null ? 0 : players.length];
        int place = 0;
        Integer previousScore = null;
        for (int i = 0; i < places.length; i++) {
            PreviewPlayer player = players[i];
            if (player == null) continue;
            int score = player.total;
            if (previousScore == null || score != previousScore.intValue()) {
                place++;
                previousScore = Integer.valueOf(score);
            }
            places[i] = place;
        }
        return places;
    }

    private static String playerLeft(PreviewPlayer player, ScoreboardConfig config,
                                     boolean expanded, boolean indentBuilder, String langCode,
                                     int placeNumber) {
        String place = config.showPlaces(expanded) ? CustomScoreboardLogic.placePrefix(placeNumber) : "";
        String rank = config.showTitles(expanded) ? bbRankPrefix(player, langCode) : "";
        String emblem = !config.showEmblems(expanded)
                || player.emblem == null || player.emblem.isEmpty() ? "" : " " + player.emblem;
        String indent = indentBuilder && player.currentBuilder ? " " : "";
        return place + rank + player.nameColor + indent + player.name + emblem;
    }

    private static String playerRight(PreviewPlayer player, ScoreboardConfig config, boolean expanded) {
        String total = "\u00a7a" + player.total;
        if (player.lowerBound >= 0) {
            total += " \u00a77(\u00a72" + player.lowerBound + "\u00a77)";
        }
        return player.delta <= 0 || !config.showPointsGained(expanded)
                ? total : tierColor(player.delta) + "+" + player.delta + " " + total;
    }

    private static String bbRankPrefix(PreviewPlayer player, String langCode) {
        String title = LangPatterns.bbTitleForRankIndex(player.bbRankIndex, langCode);
        if (title == null || title.isEmpty()) return "";
        return LangPatterns.bbColorForRankIndex(player.bbRankIndex) + title + "\u00a7r ";
    }

    private static String builderName(PreviewPlayer[] players) {
        for (PreviewPlayer player : players) {
            if (player.currentBuilder) return player.name;
        }
        return "Player143";
    }

    private static String labelWithTrailingSpace(String label) {
        if (label == null || label.isEmpty()) return "";
        return label.endsWith(" ") ? label : label + " ";
    }

    private static String tierColor(int value) {
        if (value <= 1) return "\u00a7a";
        if (value <= 2) return "\u00a7e";
        return "\u00a7c";
    }

    private static String lengthBucket(String theme) {
        if (theme == null || theme.isEmpty()) return "";
        String[] words = theme.split(" ");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (i > 0) out.append('-');
            out.append(words[i].length());
        }
        return out.toString();
    }

    public interface ScreenController {
        boolean isInGame();

        boolean isPreviewScreenOpen();

        void openPreviewScreen();

        void closePreviewScreen();

        default String currentSessionKey() {
            return "";
        }
    }

    private static final class PreviewPlayer {
        final String name;
        final String nameColor;
        final String emblem;
        final int delta;
        final int total;
        final int lowerBound;
        final boolean currentBuilder;
        final int bbRankIndex;

        PreviewPlayer(String name, String nameColor, String emblem, int delta, int total,
                      int lowerBound, boolean currentBuilder, int bbRankIndex) {
            this.name = name;
            this.nameColor = nameColor;
            this.emblem = emblem;
            this.delta = delta;
            this.total = total;
            this.lowerBound = lowerBound;
            this.currentBuilder = currentBuilder;
            this.bbRankIndex = bbRankIndex;
        }
    }

    public static final class PreviewRow {
        public final String left;
        public final String right;

        PreviewRow(String left, String right) {
            this.left = left == null ? "" : left;
            this.right = right == null ? "" : right;
        }
    }
}
