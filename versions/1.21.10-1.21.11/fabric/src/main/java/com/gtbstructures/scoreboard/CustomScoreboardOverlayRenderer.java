package com.gtbstructures.scoreboard;

import com.gtbstructures.GtbStructuresSettings;
import com.gtbstructures.ModernRuntimeBridge;
import com.gtbstructures.diagnostics.RawPacketRecorder;
import com.gtbstructures.overlay.FabricHudElementBridge;
import com.gtbstructures.stats.GameStats;
import com.gtbstructures.stats.LangPatterns;
import com.gtbstructures.util.FabricInputBridge;
import com.gtbstructures.util.RuntimeReflectionCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CustomScoreboardOverlayRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(CustomScoreboardOverlayRenderer.class);
    private static final String PREVIEW_DATE = "\u00a77" + "05/09/26  \u00a78m38D1AF";
    private static final String PREVIEW_FOOTER = "\u00a7ewww.hypixel.net";
    private static final Object REFLECTION_MISS = new Object();
    private static final Map<String, Object> IDENTIFIER_CACHE =
            new ConcurrentHashMap<String, Object>();
    private static final Map<TexturePipelineKey, Object> TEXTURE_PIPELINE_CACHE =
            new ConcurrentHashMap<TexturePipelineKey, Object>();
    private static final RuntimeReflectionCache.InvocationSite FILL_CALL =
            RuntimeReflectionCache.instanceSite("fill", "method_25294");
    private static final RuntimeReflectionCache.InvocationSite DRAW_TEXT_CALL =
            RuntimeReflectionCache.instanceSite("drawText", "drawString", "text", "method_51433");
    private static final RuntimeReflectionCache.InvocationSite TEXT_WIDTH_CALL =
            RuntimeReflectionCache.instanceSite("getWidth", "width", "method_1727");
    private static final RuntimeReflectionCache.InvocationSite DRAW_TEXTURE_CALL =
            RuntimeReflectionCache.instanceSite(
                    "drawTexture", "blit", "method_25293", "method_25302", "method_25291", "method_25290");
    private static final RuntimeReflectionCache.InvocationSite SHADER_COLOR_CALL =
            RuntimeReflectionCache.staticSite("setShaderColor");
    private static long lastCustomScoreboardCaptureEpoch = Long.MIN_VALUE;
    private static String lastCustomScoreboardSnapshotSkin;
    private static List<RawPacketRecorder.ScoreboardLine> lastCustomScoreboardSnapshotRows;
    private static int customScoreboardSnapshotRowBuildCountForTest;

    private CustomScoreboardOverlayRenderer() {
    }

    public static void register() {
        FabricHudElementBridge.replaceVanillaScoreboard(
                CustomScoreboardOverlayRenderer::shouldSuppressVanillaScoreboard,
                CustomScoreboardOverlayRenderer::vanillaScoreboardHeightOffset);
        if (FabricHudElementBridge.registerBeforeChat(
                "custom_scoreboard_overlay", CustomScoreboardOverlayRenderer::renderPanel)) {
            return;
        }
        try {
            Class<?> callbackClass = Class.forName(
                    "net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback");
            Object event = callbackClass.getField("EVENT").get(null);
            Object callback = java.lang.reflect.Proxy.newProxyInstance(
                    CustomScoreboardOverlayRenderer.class.getClassLoader(),
                    new Class<?>[] {callbackClass},
                    (proxy, method, args) -> {
                        if (method.getName().equals("onHudRender") && args != null && args.length >= 1) {
                            renderPanel(args[0]);
                            return null;
                        }
                        if (method.getName().equals("toString")) return "GtbStructures custom scoreboard overlay";
                        return null;
                    });
            Method register = event.getClass().getMethod("register", Object.class);
            register.setAccessible(true);
            register.invoke(event, callback);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.error("Unable to register GtbStructures Fabric custom scoreboard overlay", e);
        }
    }

    private static boolean shouldSuppressVanillaScoreboard() {
        if (!GtbStructuresSettings.INSTANCE.modEnabled) {
            return shouldSuppressVanillaForMaster(false, InterfacePreview.isActive(), false);
        }
        if (InterfacePreview.isActive()) return true;
        if (!GameStats.INSTANCE.shouldRenderRoundOverlays()) return false;
        GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
        GtbGame game = GameTracker.INSTANCE.game;
        if (!CustomScoreboardLogic.shouldRenderCustomPanel(settings.sbConfig, game, false)) return false;
        if (!HypixelSkinScoreboardLogic.shouldRender(settings.sbConfig, game, false)) return true;
        return HypixelSkinScoreboardLogic.buildPanel(
                ModernRuntimeBridge.lastScoreboardTitle(),
                ModernRuntimeBridge.lastScoreboardLines(),
                game, settings.sbConfig, false, GameTracker.tickCounter,
                InterfacePreview.previewLangCode()) != null;
    }

    private static int vanillaScoreboardHeightOffset() {
        GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
        return vanillaScoreboardHeightOffsetForMaster(settings.modEnabled,
                settings.sbConfig.isSkin("vanilla"), settings.sbConfig.heightOffset);
    }

    private static void renderPanel(Object graphics) {
        try {
            GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
            GtbGame game = GameTracker.INSTANCE.game;

            Object mc = minecraftClient();
            Object font = fieldAny(mc, "textRenderer", "font", "field_1772");
            Object window = invokeAny(mc, "getWindow", "method_22683");
            if (graphics == null || font == null || window == null) return;

            if (InterfacePreview.isActive()) {
                renderPreviewScoreboard(graphics, font, window, settings, isPlayerListKeyDown(mc));
                return;
            }
            if (!customScoreboardVisibleForMaster(settings.modEnabled)) return;

            if (!GameStats.INSTANCE.shouldRenderRoundOverlays()
                    || !CustomScoreboardLogic.shouldRenderCustomPanel(settings.sbConfig, game, false)) return;

            Object player = fieldAny(mc, "player", "field_1724");
            if (player == null) return;
            boolean expanded = isPlayerListKeyDown(mc);

            if (HypixelSkinScoreboardLogic.shouldRender(settings.sbConfig, game,
                    InterfacePreview.isActive())) {
                HypixelSkinScoreboardLogic.PanelRows hypixelPanel =
                        HypixelSkinScoreboardLogic.buildPanel(
                                ModernRuntimeBridge.lastScoreboardTitle(),
                                ModernRuntimeBridge.lastScoreboardLines(),
                                game, settings.sbConfig, expanded, GameTracker.tickCounter,
                                InterfacePreview.previewLangCode());
                if (hypixelPanel == null) {
                    RawPacketRecorder.INSTANCE.captureOverlayDecision(
                            "custom_scoreboard", "hidden", "hypixel_panel_unavailable");
                    return;
                }
                int contentWidth = HypixelSkinScoreboardLogic.measureContentWidth(
                        hypixelPanel, text -> textWidth(font, text));
                ScoreboardLayout.Placement placement = ScoreboardLayout.place(
                        intValue(invokeAny(window, "getScaledWidth", "getGuiScaledWidth", "method_4486")),
                        intValue(invokeAny(window, "getScaledHeight", "getGuiScaledHeight", "method_4502")),
                        contentWidth, hypixelPanel.lineCount(), settings.sbConfig.heightOffset);
                drawHypixelPanel(graphics, font, hypixelPanel, placement, contentWidth,
                        settings.sbConfig);
                captureHypixelSnapshot(settings.sbConfig.skin, hypixelPanel);
                registerPreviewBounds(placement);
                return;
            }

            CustomScoreboardLogic.PanelRows panel = CustomScoreboardLogic.buildPanelRows(game,
                    settings.sbConfig, expanded, GameTracker.tickCounter);
            if (panel == null) return;
            int contentWidth = CustomScoreboardLogic.measureContentWidth(panel, text -> textWidth(font, text));
            ScoreboardLayout.Placement placement = ScoreboardLayout.place(
                    intValue(invokeAny(window, "getScaledWidth", "getGuiScaledWidth", "method_4486")),
                    intValue(invokeAny(window, "getScaledHeight", "getGuiScaledHeight", "method_4502")),
                    contentWidth, panel.lineCount(), settings.sbConfig.heightOffset);
            drawPanel(graphics, font, panel, placement, contentWidth, settings.sbConfig);
            captureSnapshot(settings.sbConfig.skin, panel);
            registerPreviewBounds(placement);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Unable to render GtbStructures Fabric custom scoreboard", e);
        }
    }

    private static void registerPreviewBounds(ScoreboardLayout.Placement placement) {
        PreviewLayoutEditor.register("scoreboard",
                placement.bgX1 - CustomScoreboardLogic.ICON_OUTSIDE_GAP
                        - CustomScoreboardLogic.ICON_DRAW_SIZE,
                placement.bgY1,
                placement.bgX2 - placement.bgX1
                        + CustomScoreboardLogic.ICON_OUTSIDE_GAP
                        + CustomScoreboardLogic.ICON_DRAW_SIZE,
                placement.bgY2 - placement.bgY1,
                PreviewLayoutEditor.SCOREBOARD);
    }

    private static void renderPreviewScoreboard(Object graphics, Object font, Object window,
                                                GtbStructuresSettings settings, boolean expanded)
            throws ReflectiveOperationException {
        String lang = InterfacePreview.previewLangCode();
        int screenWidth = intValue(invokeAny(window, "getScaledWidth", "getGuiScaledWidth", "method_4486"));
        int screenHeight = intValue(invokeAny(window, "getScaledHeight", "getGuiScaledHeight", "method_4502"));
        if (nativeOnlyPreviewForMaster(settings.modEnabled)) {
            ScoreboardConfig vanilla = new ScoreboardConfig();
            renderPreviewSidebar(graphics, font, screenWidth, screenHeight,
                    InterfacePreview.vanillaRows(lang), false, false, vanilla, 0);
            return;
        }
        if (!settings.sbConfig.isCustomEnabled() || settings.sbConfig.isSkin("vanilla")) {
            renderPreviewSidebar(graphics, font, screenWidth, screenHeight,
                    InterfacePreview.vanillaRows(lang), false, expanded, settings.sbConfig,
                    settings.sbConfig.heightOffset);
        } else if (settings.sbConfig.isSkin("hypixel")) {
            renderPreviewSidebar(graphics, font, screenWidth, screenHeight,
                    InterfacePreview.hypixelRows(lang, expanded), true, expanded, settings.sbConfig,
                    settings.sbConfig.heightOffset);
        } else {
            renderPreviewDefaultPanel(graphics, font, screenWidth, screenHeight, lang, expanded,
                    settings.sbConfig, settings.sbConfig.heightOffset);
        }
    }

    static boolean shouldSuppressVanillaForMaster(boolean modEnabled, boolean preview,
                                                  boolean configuredSuppression) {
        return modEnabled ? preview || configuredSuppression : preview;
    }

    static int vanillaScoreboardHeightOffsetForMaster(boolean modEnabled, boolean vanillaSkin,
                                                      int savedOffset) {
        return modEnabled && vanillaSkin ? savedOffset : 0;
    }

    static boolean customScoreboardVisibleForMaster(boolean modEnabled) {
        return modEnabled;
    }

    static boolean nativeOnlyPreviewForMaster(boolean modEnabled) {
        return !modEnabled;
    }

    private static void renderPreviewDefaultPanel(Object graphics, Object font,
                                                  int screenWidth, int screenHeight,
                                                  String lang, boolean expanded,
                                                  ScoreboardConfig config, int heightOffset)
            throws ReflectiveOperationException {
        PreviewPlayer[] players = previewPlayers();
        String[] left = new String[players.length];
        String[] right = new String[players.length];
        int[] places = previewDensePlaces(players);
        for (int i = 0; i < players.length; i++) {
            left[i] = playerLeft(players[i], config, expanded, false, "en", places[i]);
            right[i] = playerRight(players[i], config, expanded);
        }

        List<CustomScoreboardLogic.MetadataRow> metadataRows =
                new ArrayList<CustomScoreboardLogic.MetadataRow>();
        metadataRows.add(new CustomScoreboardLogic.MetadataRow("header", "\u00a77Round",
                CustomScoreboardLogic.formatRoundProgress(10, 10, false, false)));
        if (config.defaultExplicitlyShowBuilders) {
            metadataRows.add(new CustomScoreboardLogic.MetadataRow(
                    "builder", "\u00a77Builder", previewBuilderValue(players)));
        }
        metadataRows.add(new CustomScoreboardLogic.MetadataRow(
                "timer", "\u00a77Time Left", "\u00a7a0:13"));
        String theme = InterfacePreview.localizedPreviewTheme(lang);
        String themeLabel = "\u00a77Theme [\u00a7a" + lengthBucket(theme) + "\u00a77]:";
        String themeValue = "\u00a7a" + theme;

        int contentWidth = 80;
        for (CustomScoreboardLogic.MetadataRow row : metadataRows) {
            contentWidth = Math.max(contentWidth, measurePair(font, row.left, row.right));
        }
        contentWidth = Math.max(contentWidth, textWidth(font, themeLabel));
        contentWidth = Math.max(contentWidth, textWidth(font, themeValue));
        for (int i = 0; i < players.length; i++) {
            int leftWidth = textWidth(font, left[i])
                    + (players[i].statusIcon >= 0
                    ? CustomScoreboardLogic.ICON_DRAW_SIZE + CustomScoreboardLogic.INLINE_STATUS_GAP : 0);
            int width = leftWidth + 6 + textWidth(font, right[i]);
            contentWidth = Math.max(contentWidth, width);
        }
        contentWidth += 6;

        int lineCount = metadataRows.size() + 1 + players.length + 1 + 2;
        ScoreboardLayout.Placement placement = ScoreboardLayout.place(screenWidth, screenHeight,
                contentWidth, lineCount, heightOffset);
        int editorLeft = placement.bgX1 - CustomScoreboardLogic.ICON_OUTSIDE_GAP
                - CustomScoreboardLogic.ICON_DRAW_SIZE;
        PreviewLayoutEditor.registerWithBackground("scoreboard", editorLeft, placement.bgY1,
                placement.bgX2 - editorLeft, placement.bgY2 - placement.bgY1,
                PreviewLayoutEditor.SCOREBOARD,
                (bgLeft, bgTop, bgRight, bgBottom, color) ->
                        fill(graphics, bgLeft, bgTop, bgRight, bgBottom, color));
        fill(graphics, placement.bgX1, placement.bgY1, placement.bgX2, placement.bgY2,
                config.backgroundColor());

        int y = placement.y;
        for (CustomScoreboardLogic.MetadataRow row : metadataRows) {
            drawPair(graphics, font, row.left, row.right, placement.x, y, contentWidth,
                    config.textShadow);
            y += ScoreboardLayout.LINE_HEIGHT;
        }
        drawSeparator(graphics, placement.x, y, contentWidth);
        y += ScoreboardLayout.LINE_HEIGHT;

        for (int i = 0; i < players.length; i++) {
            drawPreviewPlayerRow(graphics, font, players[i], left[i], right[i],
                    placement.bgX1, placement.x, placement.bgX2, y, contentWidth,
                    config, config.textShadow, ScoreboardLayout.LINE_HEIGHT);
            y += ScoreboardLayout.LINE_HEIGHT;
        }

        drawSeparator(graphics, placement.x, y, contentWidth);
        y += ScoreboardLayout.LINE_HEIGHT;
        drawTextMaybeShadow(graphics, font, themeLabel, placement.x, y, 0xFFFFFF,
                config.textShadow);
        y += ScoreboardLayout.LINE_HEIGHT;
        drawTextMaybeShadow(graphics, font, themeValue, placement.x + textWidth(font, " "), y,
                0xFFFFFF, config.textShadow);
    }

    private static void renderPreviewSidebar(Object graphics, Object font,
                                             int screenWidth, int screenHeight,
                                             InterfacePreview.PreviewRow[] rows,
                                             boolean customPlayerRows, boolean expanded,
                                             ScoreboardConfig config, int heightOffset)
            throws ReflectiveOperationException {
        String title = animatedHypixelTitle();
        PreviewPlayer[] players = previewPlayers();
        int contentWidth = textWidth(font, title);
        for (int i = 0; i < rows.length; i++) {
            InterfacePreview.PreviewRow row = rows[i];
            int leftWidth = textWidth(font, row.left);
            PreviewPlayer player = customPlayerRows ? previewPlayerForHypixelRow(players, i) : null;
            if (player != null && player.statusIcon >= 0) {
                leftWidth += CustomScoreboardLogic.ICON_DRAW_SIZE + CustomScoreboardLogic.INLINE_STATUS_GAP;
            }
            int rightWidth = row.right.isEmpty() ? 0 : textWidth(font, row.right);
            contentWidth = Math.max(contentWidth, leftWidth + (rightWidth == 0 ? 0 : 6 + rightWidth));
        }

        int lineHeight = fontHeight(font);
        int totalPanelHeight = (rows.length + 1) * lineHeight;
        int bottomY = screenHeight / 2 + totalPanelHeight / 2 - heightOffset;
        int topY = bottomY - totalPanelHeight;
        int rightX = screenWidth - 3;
        int leftX = rightX - contentWidth;
        int rightCellRight = rightX + 2;
        int editorLeft = leftX - 2 - CustomScoreboardLogic.ICON_OUTSIDE_GAP
                - CustomScoreboardLogic.ICON_DRAW_SIZE;

        int titleBackground = customPlayerRows ? config.backgroundColor() : 0x60000000;
        int rowBackground = customPlayerRows ? config.backgroundColor() : ScoreboardLayout.BACKGROUND_COLOR;
        boolean shadow = customPlayerRows && config.textShadow;
        PreviewLayoutEditor.registerWithBackground("scoreboard", editorLeft, topY,
                rightCellRight - editorLeft, totalPanelHeight + 1,
                PreviewLayoutEditor.SCOREBOARD,
                (left, top, right, bottom, color) -> fill(graphics, left, top, right, bottom, color));
        fill(graphics, leftX - 2, topY, rightCellRight, topY + lineHeight, titleBackground);
        fill(graphics, leftX - 2, topY + lineHeight, rightCellRight,
                topY + lineHeight + 1, rowBackground);
        int y = topY + lineHeight + 1;
        for (int i = 0; i < rows.length; i++) {
            fill(graphics, leftX - 2, y, rightCellRight, y + lineHeight,
                    rowBackground);
            y += lineHeight;
        }

        drawTextMaybeShadow(graphics, font, title,
                leftX + contentWidth / 2 - textWidth(font, title) / 2, topY, 0xFFFFFF,
                shadow);

        y = topY + lineHeight + 1;
        for (int i = 0; i < rows.length; i++) {
            InterfacePreview.PreviewRow row = rows[i];
            PreviewPlayer player = customPlayerRows ? previewPlayerForHypixelRow(players, i) : null;
            if (customPlayerRows && player != null) {
                drawPreviewPlayerRow(graphics, font, player, row.left, row.right,
                        leftX - 2, leftX, rightCellRight, y, contentWidth,
                        config, shadow, lineHeight);
            } else {
                drawTextMaybeShadow(graphics, font, row.left, leftX, y, 0xFFFFFF, shadow);
                if (!row.right.isEmpty()) {
                    int rightWidth = textWidth(font, row.right);
                    drawTextMaybeShadow(graphics, font, row.right, rightCellRight - rightWidth,
                            y, 0xFFFFFF, shadow);
                }
            }
            y += lineHeight;
        }
    }

    private static PreviewPlayer previewPlayerForHypixelRow(PreviewPlayer[] players, int rowIndex) {
        int playerIndex = rowIndex - 4;
        return players != null && playerIndex >= 0 && playerIndex < players.length
                ? players[playerIndex] : null;
    }

    private static void drawPreviewPlayerRow(Object graphics, Object font, PreviewPlayer player,
                                             String left, String right,
                                             int bgLeft, int textLeft, int rightCellRight,
                                             int rowY, int contentWidth,
                                             ScoreboardConfig config, boolean shadow, int rowHeight)
            throws ReflectiveOperationException {
        boolean hasStatusIcon = player.statusIcon >= 0;
        int decorationY = rowY + CustomScoreboardLogic.STATE_DECORATION_Y_OFFSET;
        int iconY = rowY + (rowHeight - CustomScoreboardLogic.ICON_DRAW_SIZE) / 2
                + CustomScoreboardLogic.STATE_DECORATION_Y_OFFSET;
        if (!hasStatusIcon && player.currentBuilder) {
            drawRowHighlight(graphics, bgLeft, decorationY, rightCellRight, rowHeight,
                    false, true, config);
            drawRowMarker(graphics, bgLeft, decorationY, rowHeight, -1, true, config);
        }

        int buildIcon = player.displayBuildIcon();
        int buildBackground = BuilderIconLogic.backgroundForBuildIcon(buildIcon);
        int iconX = bgLeft - CustomScoreboardLogic.ICON_OUTSIDE_GAP
                - CustomScoreboardLogic.ICON_DRAW_SIZE;
        drawIcon(graphics, buildBackground, iconX, iconY,
                CustomScoreboardLogic.ICON_DRAW_SIZE, BuilderIconLogic.tintFor(buildBackground));
        if (buildIcon >= 0) {
            drawIcon(graphics, buildIcon, iconX, iconY,
                    CustomScoreboardLogic.ICON_DRAW_SIZE,
                    BuilderIconLogic.tintFor(buildIcon, spinnerTintTick()));
        }

        int textX = textLeft;
        if (hasStatusIcon) {
            textX += CustomScoreboardLogic.ICON_DRAW_SIZE + CustomScoreboardLogic.INLINE_STATUS_GAP;
        }
        drawTextMaybeShadow(graphics, font, left, textX, rowY, 0xFFFFFF, shadow);
        int rightWidth = textWidth(font, right);
        drawTextMaybeShadow(graphics, font, right, textLeft + contentWidth - rightWidth, rowY,
                0xFFFFFF, shadow);
        if (hasStatusIcon) {
            drawRowHighlight(graphics, bgLeft, decorationY, rightCellRight, rowHeight,
                    true, false, config);
            drawRowMarker(graphics, bgLeft, decorationY, rowHeight, player.statusIcon, false, config);
            drawIcon(graphics, player.statusIcon, textLeft, iconY,
                    CustomScoreboardLogic.ICON_DRAW_SIZE,
                    BuilderIconLogic.tintFor(player.statusIcon));
        }
    }

    private static int measurePair(Object font, String left, String right) {
        return textWidth(font, left) + textWidth(font, right);
    }

    private static PreviewPlayer[] previewPlayers() {
        return new PreviewPlayer[] {
                new PreviewPlayer("Player598", "\u00a7b", "\u00a73\u03c0", 2, 26, -1, false, true, false, 15),
                new PreviewPlayer("Player742", "\u00a77", "\u00a7f\u263a", 3, 26, -1, false, true, false, 7),
                new PreviewPlayer("Player314", "\u00a7a", "\u00a7f\u263a", 1, 13, -1, false, true, false, 3),
                new PreviewPlayer("Player807", "\u00a77", "\u00a7f\u263a", 0, 9, -1, false, true, false, 10),
                new PreviewPlayer("Player621", "\u00a7b", "\u00a7f\u263a", 1, 7, -1, false, true, false, 5),
                new PreviewPlayer("Player143", "\u00a77", "\u00a7f\u263a", 3, 6, -1, true, false, false, 8),
                new PreviewPlayer("Player905", "\u00a7a", "\u00a7f\u263a", 0, 5, -1, false, true, false, 2),
                new PreviewPlayer("Player267", "\u00a77", "\u00a7f\u263a", 0, 4, -1, false, true, false, 6),
                new PreviewPlayer("Player781", "\u00a77", "\u00a7f\u263a", 0, 3, -1, false, true, false, 4),
                new PreviewPlayer("Player432", "\u00a77", "\u00a7f\u263a", 0, 3, 2, false, true, true, 1)
        };
    }

    private static String previewBuilderValue(PreviewPlayer[] players) {
        if (players != null) {
            for (PreviewPlayer player : players) {
                if (player != null && player.currentBuilder) {
                    return player.nameColor + player.name;
                }
            }
        }
        return "\u00a7c???";
    }

    private static String playerLeft(PreviewPlayer player, ScoreboardConfig config, boolean expanded,
                                     boolean indentBuilder, String lang, int placeNumber) {
        String place = config.showPlaces(expanded) ? CustomScoreboardLogic.placePrefix(placeNumber) : "";
        String rank = config.showTitles(expanded) ? bbRankPrefix(player, lang) : "";
        String emblem = !config.showEmblems(expanded)
                || player.emblem == null || player.emblem.isEmpty() ? "" : " " + player.emblem;
        String indent = indentBuilder && player.currentBuilder ? " " : "";
        return place + rank + player.nameColor + indent + player.name + emblem;
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

    private static String playerRight(PreviewPlayer player, ScoreboardConfig config, boolean expanded) {
        String total = "\u00a7a" + player.total;
        if (player.lowerBound >= 0) {
            total += " \u00a77(\u00a72" + player.lowerBound + "\u00a77)";
        }
        return player.delta <= 0 || !config.showPointsGained(expanded)
                ? total : tierColor(player.delta) + "+" + player.delta + " " + total;
    }

    private static String bbRankPrefix(PreviewPlayer player, String lang) {
        String title = LangPatterns.bbTitleForRankIndex(player.bbRankIndex, lang);
        if (title == null || title.isEmpty()) return "";
        return LangPatterns.bbColorForRankIndex(player.bbRankIndex) + title + "\u00a7r ";
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

    private static String animatedHypixelTitle() {
        final String base = "GUESS THE BUILD";
        long tick = (System.currentTimeMillis() / 100L) % (base.length() + 6);
        if (tick >= base.length()) {
            return (tick % 2L == 0L ? "\u00a7e\u00a7l" : "\u00a7f\u00a7l") + base;
        }
        int highlight = (int) tick;
        StringBuilder sb = new StringBuilder(base.length() * 4);
        for (int i = 0; i < base.length(); i++) {
            if (i < highlight) sb.append("\u00a7f\u00a7l");
            else if (i == highlight) sb.append("\u00a76\u00a7l");
            else sb.append("\u00a7e\u00a7l");
            sb.append(base.charAt(i));
        }
        return sb.toString();
    }

    private static void drawPanel(Object graphics, Object font,
                                  CustomScoreboardLogic.PanelRows panel,
                                  ScoreboardLayout.Placement placement, int contentWidth,
                                  ScoreboardConfig config)
            throws ReflectiveOperationException {
        fill(graphics, placement.bgX1, placement.bgY1, placement.bgX2, placement.bgY2,
                config.backgroundColor());
        int y = placement.y;
        for (CustomScoreboardLogic.MetadataRow row : panel.metadataRows) {
            drawPair(graphics, font, row.left, row.right, placement.x, y, contentWidth,
                    config.textShadow);
            y += ScoreboardLayout.LINE_HEIGHT;
        }
        drawSeparator(graphics, placement.x, y, contentWidth);
        y += ScoreboardLayout.LINE_HEIGHT;

        for (CustomScoreboardLogic.Row row : panel.rows) {
            int iconX = placement.bgX1
                    - CustomScoreboardLogic.ICON_OUTSIDE_GAP
                    - CustomScoreboardLogic.ICON_DRAW_SIZE;
            int decorationY = y + CustomScoreboardLogic.STATE_DECORATION_Y_OFFSET;
            int iconY = y + (ScoreboardLayout.LINE_HEIGHT - CustomScoreboardLogic.ICON_DRAW_SIZE) / 2
                    + CustomScoreboardLogic.STATE_DECORATION_Y_OFFSET;
            boolean hasStatusIcon = row.statusIcon >= 0;
            if (!hasStatusIcon && row.builderRow) {
                drawRowHighlight(graphics, placement.bgX1, decorationY, placement.bgX2,
                        false, true, config);
                drawRowMarker(graphics, placement.bgX1, decorationY, -1, true, config);
            }

            int buildBackground = BuilderIconLogic.backgroundForBuildIcon(row.buildIcon);
            drawIcon(graphics, buildBackground, iconX, iconY, CustomScoreboardLogic.ICON_DRAW_SIZE,
                    BuilderIconLogic.tintFor(buildBackground));
            if (row.buildIcon >= 0) {
                drawIcon(graphics, row.buildIcon, iconX, iconY, CustomScoreboardLogic.ICON_DRAW_SIZE,
                        BuilderIconLogic.tintFor(row.buildIcon, spinnerTintTick()));
            }

            int textX = placement.x;
            if (hasStatusIcon) textX += CustomScoreboardLogic.ICON_DRAW_SIZE
                    + CustomScoreboardLogic.INLINE_STATUS_GAP;
            drawTextMaybeShadow(graphics, font, row.left, textX, y, 0xFFFFFF,
                    config.textShadow);
            int rightWidth = textWidth(font, row.right);
            drawTextMaybeShadow(graphics, font, row.right,
                    placement.x + contentWidth - rightWidth, y, 0xFFFFFF,
                    config.textShadow);
            if (hasStatusIcon) {
                drawRowHighlight(graphics, placement.bgX1, decorationY, placement.bgX2,
                        true, false, config);
                drawRowMarker(graphics, placement.bgX1, decorationY, row.statusIcon, false, config);
                drawIcon(graphics, row.statusIcon, placement.x, iconY,
                        CustomScoreboardLogic.ICON_DRAW_SIZE,
                        BuilderIconLogic.tintFor(row.statusIcon));
            }
            y += ScoreboardLayout.LINE_HEIGHT;
        }

        drawSeparator(graphics, placement.x, y, contentWidth);
        y += ScoreboardLayout.LINE_HEIGHT;
        drawTextMaybeShadow(graphics, font, panel.themeLabel, placement.x, y, 0xFFFFFF,
                config.textShadow);
        y += ScoreboardLayout.LINE_HEIGHT;
        drawTextMaybeShadow(graphics, font, panel.themeValueLine, placement.x + textWidth(font, " "),
                y, 0xFFFFFF, config.textShadow);
    }

    private static void drawHypixelPanel(Object graphics, Object font,
                                         HypixelSkinScoreboardLogic.PanelRows panel,
                                         ScoreboardLayout.Placement placement, int contentWidth,
                                         ScoreboardConfig config)
            throws ReflectiveOperationException {
        int y = placement.y;
        fill(graphics, placement.bgX1, y, placement.bgX2,
                y + panel.lineCount() * ScoreboardLayout.LINE_HEIGHT, config.backgroundColor());
        int titleWidth = textWidth(font, panel.title);
        drawTextMaybeShadow(graphics, font, panel.title,
                placement.x + (contentWidth - titleWidth) / 2, y, 0xFFFFFF,
                config.textShadow);
        y += ScoreboardLayout.LINE_HEIGHT;

        for (int i = panel.rowsBottomToTop.size() - 1; i >= 0; i--) {
            HypixelSkinScoreboardLogic.Row row = panel.rowsBottomToTop.get(i);
            drawHypixelRow(graphics, font, row, placement, contentWidth, y, config);
            y += ScoreboardLayout.LINE_HEIGHT;
        }
    }

    private static void drawHypixelRow(Object graphics, Object font,
                                       HypixelSkinScoreboardLogic.Row row,
                                       ScoreboardLayout.Placement placement,
                                       int contentWidth, int y, ScoreboardConfig config)
            throws ReflectiveOperationException {
        boolean hasStatusIcon = row.statusIcon >= 0;
        int decorationY = y + CustomScoreboardLogic.STATE_DECORATION_Y_OFFSET;
        if (row.player != null) {
            if (!hasStatusIcon && row.currentBuilder) {
                drawRowHighlight(graphics, placement.bgX1, decorationY, placement.bgX2,
                        false, true, config);
                drawRowMarker(graphics, placement.bgX1, decorationY, -1, true, config);
            } else if (hasStatusIcon) {
                drawRowHighlight(graphics, placement.bgX1, decorationY, placement.bgX2,
                        true, false, config);
                drawRowMarker(graphics, placement.bgX1, decorationY, row.statusIcon, false, config);
            }

            int iconX = placement.bgX1
                    - CustomScoreboardLogic.ICON_OUTSIDE_GAP
                    - CustomScoreboardLogic.ICON_DRAW_SIZE;
            int iconY = y + (ScoreboardLayout.LINE_HEIGHT - CustomScoreboardLogic.ICON_DRAW_SIZE) / 2
                    + CustomScoreboardLogic.STATE_DECORATION_Y_OFFSET;
            int buildBackground = BuilderIconLogic.backgroundForBuildIcon(row.buildIcon);
            drawIcon(graphics, buildBackground, iconX, iconY, CustomScoreboardLogic.ICON_DRAW_SIZE,
                    BuilderIconLogic.tintFor(buildBackground));
            if (row.buildIcon >= 0) {
                drawIcon(graphics, row.buildIcon, iconX, iconY, CustomScoreboardLogic.ICON_DRAW_SIZE,
                        BuilderIconLogic.tintFor(row.buildIcon, spinnerTintTick()));
            }
            if (hasStatusIcon) {
                drawIcon(graphics, row.statusIcon, placement.x, iconY,
                        CustomScoreboardLogic.ICON_DRAW_SIZE,
                        BuilderIconLogic.tintFor(row.statusIcon));
            }
        }

        int textX = placement.x;
        if (hasStatusIcon) textX += CustomScoreboardLogic.ICON_DRAW_SIZE
                + CustomScoreboardLogic.INLINE_STATUS_GAP;
        drawTextMaybeShadow(graphics, font, row.left, textX, y, 0xFFFFFF,
                config.textShadow);
        if (!row.right.isEmpty()) {
            int rightWidth = textWidth(font, row.right);
            drawTextMaybeShadow(graphics, font, row.right,
                    placement.x + contentWidth - rightWidth, y, 0xFFFFFF,
                    config.textShadow);
        }
    }

    private static void drawSeparator(Object graphics, int x, int y, int contentWidth)
            throws ReflectiveOperationException {
        fill(graphics, x - 1, y + 3, x + contentWidth + 1, y + 4, 0x60AAAAAA);
    }

    private static int spinnerTintTick() {
        return InterfacePreview.isActive() ? InterfacePreview.previewTicks() : GameTracker.tickCounter;
    }

    private static void drawPair(Object graphics, Object font, String left, String right,
                                 int x, int y, int contentWidth, boolean shadow)
            throws ReflectiveOperationException {
        drawTextMaybeShadow(graphics, font, left, x, y, 0xFFFFFF, shadow);
        int rightWidth = textWidth(font, right);
        drawTextMaybeShadow(graphics, font, right, x + contentWidth - rightWidth, y, 0xFFFFFF,
                shadow);
    }

    private static void drawRowHighlight(Object graphics, int left, int rowY, int right,
                                         boolean hasStatusIcon, boolean currentBuilder,
                                         ScoreboardConfig config)
            throws ReflectiveOperationException {
        drawRowHighlight(graphics, left, rowY, right, ScoreboardLayout.LINE_HEIGHT,
                hasStatusIcon, currentBuilder, config);
    }

    private static void drawRowHighlight(Object graphics, int left, int rowY, int right,
                                         int rowHeight,
                                         boolean hasStatusIcon, boolean currentBuilder,
                                         ScoreboardConfig config)
            throws ReflectiveOperationException {
        int color = CustomScoreboardLogic.rowHighlightColor(config, hasStatusIcon, currentBuilder);
        if (color != 0) {
            fill(graphics, left, rowY, right, rowY + rowHeight, color);
        }
    }

    private static void drawRowMarker(Object graphics, int left, int rowY,
                                      int statusIcon, boolean currentBuilder,
                                      ScoreboardConfig config)
            throws ReflectiveOperationException {
        drawRowMarker(graphics, left, rowY, ScoreboardLayout.LINE_HEIGHT, statusIcon, currentBuilder,
                config);
    }

    private static void drawRowMarker(Object graphics, int left, int rowY, int rowHeight,
                                      int statusIcon, boolean currentBuilder,
                                      ScoreboardConfig config)
            throws ReflectiveOperationException {
        int color = CustomScoreboardLogic.rowMarkerColor(config, statusIcon, currentBuilder);
        if (color != 0) {
            fill(graphics, left - 1, rowY, left, rowY + rowHeight, color);
        }
    }

    private static void drawIcon(Object graphics, int cell, int x, int y, int size, int tintRgb)
            throws ReflectiveOperationException {
        if (cell < 0) return;
        drawIconTexture(graphics, cell, x, y, size, tintRgb);
    }

    private static boolean drawIconTexture(Object graphics, int cell, int x, int y, int size, int tintRgb) {
        if (cell < 0 || cell >= BuilderIconLogic.CELL_COUNT) return false;
        try {
            Object texture = identifier("gtbstructures:textures/font/gtb_icons.png");
            if (texture == null) return false;
            Object pipeline = texturePipeline(graphics, texture);
            int u = cell * BuilderIconLogic.CELL_SIZE;
            int textureWidth = BuilderIconLogic.CELL_COUNT * BuilderIconLogic.CELL_SIZE;
            int textureHeight = BuilderIconLogic.CELL_SIZE;
            int color = 0xFF000000 | (tintRgb & 0x00FFFFFF);

            if (pipeline != null) {
                if (tryDrawTexture(graphics, pipeline, texture, x, y, u, size, textureWidth, textureHeight, color)) {
                    return true;
                }
            }
            setShaderColor(tintRgb);
            try {
                return tryDrawTexture(graphics, null, texture, x, y, u, size, textureWidth, textureHeight, color);
            } finally {
                setShaderColor(0xFFFFFF);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private static boolean tryDrawTexture(Object graphics, Object pipeline, Object texture,
                                          int x, int y, int u, int size,
                                          int textureWidth, int textureHeight, int color)
            throws ReflectiveOperationException {
        Object[][] attempts = pipeline == null
                ? new Object[][] {
                {texture, x, y, Float.valueOf(u), Float.valueOf(0.0F),
                        size, size, BuilderIconLogic.GLYPH_SOURCE_SIZE, BuilderIconLogic.GLYPH_SOURCE_SIZE,
                        textureWidth, textureHeight, color},
                {texture, x, y, Float.valueOf(u), Float.valueOf(0.0F),
                        BuilderIconLogic.GLYPH_SOURCE_SIZE, BuilderIconLogic.GLYPH_SOURCE_SIZE,
                        textureWidth, textureHeight},
                {texture, x, y, Float.valueOf(u), Float.valueOf(0.0F),
                        size, size, textureWidth, textureHeight, color},
                {texture, x, y, u, 0, size, size, textureWidth, textureHeight}
        }
                : new Object[][] {
                {pipeline, texture, x, y, Float.valueOf(u), Float.valueOf(0.0F),
                        size, size, BuilderIconLogic.GLYPH_SOURCE_SIZE, BuilderIconLogic.GLYPH_SOURCE_SIZE,
                        textureWidth, textureHeight, color},
                {pipeline, texture, x, y, Float.valueOf(u), Float.valueOf(0.0F),
                        BuilderIconLogic.GLYPH_SOURCE_SIZE, BuilderIconLogic.GLYPH_SOURCE_SIZE,
                        textureWidth, textureHeight},
                {pipeline, texture, x, y, Float.valueOf(u), Float.valueOf(0.0F),
                        size, size, textureWidth, textureHeight, color},
                {pipeline, texture, x, y, Float.valueOf(u), Float.valueOf(0.0F),
                        size, size, textureWidth, textureHeight}
        };
        for (Object[] attempt : attempts) {
            if (DRAW_TEXTURE_CALL.invokeIfPresent(graphics, attempt)) {
                return true;
            }
        }
        return false;
    }

    private static Object texturePipeline(Object graphics, Object texture) {
        if (graphics == null || texture == null) return null;
        TexturePipelineKey key = new TexturePipelineKey(graphics.getClass(), texture.getClass());
        Object cached = TEXTURE_PIPELINE_CACHE.get(key);
        if (cached != null) return cached == REFLECTION_MISS ? null : cached;
        synchronized (TEXTURE_PIPELINE_CACHE) {
            cached = TEXTURE_PIPELINE_CACHE.get(key);
            if (cached != null) return cached == REFLECTION_MISS ? null : cached;
            Object pipeline = null;
            try {
                pipeline = resolveTexturePipeline(graphics, texture);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // A pipeline is optional; the renderer will probe legacy texture overloads.
            }
            TEXTURE_PIPELINE_CACHE.put(key, pipeline == null ? REFLECTION_MISS : pipeline);
            return pipeline;
        }
    }

    private static Object resolveTexturePipeline(Object graphics, Object texture)
            throws ReflectiveOperationException {
        Class<?> pipelineType = null;
        for (Method method : graphics.getClass().getMethods()) {
            if ((!method.getName().equals("drawTexture") && !method.getName().equals("blit")
                    && !method.getName().equals("method_25290")
                    && !method.getName().equals("method_25293") && !method.getName().equals("method_25302"))
                    || method.getParameterCount() < 2) {
                continue;
            }
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters[1].isInstance(texture) && !parameters[0].isInstance(texture)) {
                pipelineType = parameters[0];
                break;
            }
        }
        if (pipelineType == null) return null;
        String[] holders = {
                "net.minecraft.client.renderer.RenderPipelines",
                "net.minecraft.client.render.RenderPipelines",
                "net.minecraft.client.gl.RenderPipelines",
                "net.minecraft.client.gui.render.RenderPipelines",
                "net.minecraft.class_10799"
        };
        for (String holderName : holders) {
            Class<?> holder = RuntimeReflectionCache.findClassAny(holderName);
            if (holder == null) continue;
            Object pipeline = staticAssignableField(holder, pipelineType, "GUI_TEXTURED");
            if (pipeline == null) pipeline = staticAssignableField(holder, pipelineType, "field_56883");
            if (pipeline == null) pipeline = staticAssignableField(holder, pipelineType, "");
            if (pipeline != null) return pipeline;
        }
        return null;
    }

    private static Object staticAssignableField(Class<?> type, Class<?> fieldType, String preferred)
            throws ReflectiveOperationException {
        if (preferred != null && !preferred.isEmpty()) {
            for (Field field : type.getFields()) {
                if (Modifier.isStatic(field.getModifiers())
                        && fieldType.isAssignableFrom(field.getType())
                        && preferred.equals(field.getName())) {
                    return field.get(null);
                }
            }
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())
                        && fieldType.isAssignableFrom(field.getType())
                        && preferred.equals(field.getName())) {
                    field.setAccessible(true);
                    return field.get(null);
                }
            }
            return null;
        }
        for (Field field : type.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) && fieldType.isAssignableFrom(field.getType())) {
                return field.get(null);
            }
        }
        for (Field field : type.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && fieldType.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                return field.get(null);
            }
        }
        return null;
    }

    private static Object identifier(String registry) {
        Object cached = IDENTIFIER_CACHE.get(registry);
        if (cached != null) return cached == REFLECTION_MISS ? null : cached;
        synchronized (IDENTIFIER_CACHE) {
            cached = IDENTIFIER_CACHE.get(registry);
            if (cached != null) return cached == REFLECTION_MISS ? null : cached;
            Object resolved = null;
            try {
                resolved = resolveIdentifier(registry);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // A stable miss is cached so a missing runtime adapter cannot storm exceptions.
            }
            IDENTIFIER_CACHE.put(registry, resolved == null ? REFLECTION_MISS : resolved);
            return resolved;
        }
    }

    private static Object resolveIdentifier(String registry) throws ReflectiveOperationException {
        Class<?> idClass = RuntimeReflectionCache.findClassAny(
                "net.minecraft.resources.ResourceLocation",
                "net.minecraft.resources.Identifier",
                "net.minecraft.util.Identifier", "net.minecraft.class_2960", "amj", "amo");
        if (idClass == null) return null;
        String value = registry.indexOf(':') >= 0 ? registry : "minecraft:" + registry;
        int colon = value.indexOf(':');
        if (colon > 0 && colon + 1 < value.length()) {
            String namespace = value.substring(0, colon);
            String path = value.substring(colon + 1);
            for (Method method : idClass.getMethods()) {
                if (!Modifier.isStatic(method.getModifiers())
                        || !idClass.isAssignableFrom(method.getReturnType())
                        || method.getParameterCount() != 2
                        || !method.getParameterTypes()[0].equals(String.class)
                        || !method.getParameterTypes()[1].equals(String.class)) {
                    continue;
                }
                try {
                    Object out = method.invoke(null, namespace, path);
                    if (out != null) return out;
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // Try the next ResourceLocation factory.
                }
            }
            try {
                Constructor<?> constructor = idClass.getDeclaredConstructor(String.class, String.class);
                constructor.setAccessible(true);
                return constructor.newInstance(namespace, path);
            } catch (NoSuchMethodException ignored) {
                // Try the one-string constructor below.
            }
        }
        for (Method method : idClass.getMethods()) {
            if (!Modifier.isStatic(method.getModifiers())
                    || !idClass.isAssignableFrom(method.getReturnType())
                    || method.getParameterCount() != 1
                    || !method.getParameterTypes()[0].equals(String.class)) {
                continue;
            }
            try {
                Object out = method.invoke(null, value);
                if (out != null) return out;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Try the next ResourceLocation factory.
            }
        }
        try {
            Constructor<?> constructor = idClass.getDeclaredConstructor(String.class);
            constructor.setAccessible(true);
            return constructor.newInstance(value);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static void setShaderColor(int tintRgb) {
        try {
            Class<?> renderSystem = RuntimeReflectionCache.findClassAny(
                    "com.mojang.blaze3d.systems.RenderSystem");
            if (renderSystem == null) return;
            float red = ((tintRgb >> 16) & 0xFF) / 255.0F;
            float green = ((tintRgb >> 8) & 0xFF) / 255.0F;
            float blue = (tintRgb & 0xFF) / 255.0F;
            SHADER_COLOR_CALL.invokeStaticIfPresent(renderSystem,
                    Float.valueOf(red), Float.valueOf(green), Float.valueOf(blue), Float.valueOf(1.0F));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // The color-arg texture overload does not need a shader color.
        }
    }

    static void captureSnapshot(String skin, CustomScoreboardLogic.PanelRows panel) {
        if (panel == null || !prepareCustomScoreboardCapture()
                || matchesSnapshot(skin, panel)) return;
        List<RawPacketRecorder.ScoreboardLine> snapshotRows =
                new ArrayList<RawPacketRecorder.ScoreboardLine>();
        for (CustomScoreboardLogic.MetadataRow row : panel.metadataRows) {
            snapshotRows.add(new RawPacketRecorder.ScoreboardLine(row.kind, row.left, row.right));
        }
        for (CustomScoreboardLogic.Row row : panel.rows) {
            snapshotRows.add(new RawPacketRecorder.ScoreboardLine("player", row.left, row.right));
        }
        snapshotRows.add(new RawPacketRecorder.ScoreboardLine("theme_label", panel.themeLabel, ""));
        snapshotRows.add(new RawPacketRecorder.ScoreboardLine("theme", panel.themeValueLine, ""));
        customScoreboardSnapshotRowBuildCountForTest++;
        RawPacketRecorder.INSTANCE.captureCustomScoreboard(skin, snapshotRows);
        cacheSnapshot(skin, snapshotRows);
    }

    static void captureHypixelSnapshot(String skin, HypixelSkinScoreboardLogic.PanelRows panel) {
        if (panel == null || !prepareCustomScoreboardCapture()
                || matchesHypixelSnapshot(skin, panel)) return;
        List<RawPacketRecorder.ScoreboardLine> snapshotRows =
                new ArrayList<RawPacketRecorder.ScoreboardLine>();
        snapshotRows.add(new RawPacketRecorder.ScoreboardLine("title", panel.title, ""));
        for (int i = panel.rowsBottomToTop.size() - 1; i >= 0; i--) {
            HypixelSkinScoreboardLogic.Row row = panel.rowsBottomToTop.get(i);
            snapshotRows.add(new RawPacketRecorder.ScoreboardLine(
                    row.player == null ? "vanilla" : "player", row.left, row.right));
        }
        customScoreboardSnapshotRowBuildCountForTest++;
        RawPacketRecorder.INSTANCE.captureCustomScoreboard(skin, snapshotRows);
        cacheSnapshot(skin, snapshotRows);
    }

    private static boolean prepareCustomScoreboardCapture() {
        long epoch = RawPacketRecorder.INSTANCE.customScoreboardCaptureEpoch();
        if (lastCustomScoreboardCaptureEpoch != epoch) {
            lastCustomScoreboardCaptureEpoch = epoch;
            lastCustomScoreboardSnapshotSkin = null;
            lastCustomScoreboardSnapshotRows = null;
        }
        return epoch >= 0L;
    }

    private static boolean matchesSnapshot(String skin, CustomScoreboardLogic.PanelRows panel) {
        if (!matchesSnapshotHeader(skin, panel.metadataRows.size() + panel.rows.size() + 2)) {
            return false;
        }
        int snapshotIndex = 0;
        for (CustomScoreboardLogic.MetadataRow row : panel.metadataRows) {
            if (!matchesSnapshotRow(snapshotIndex++, row.kind, row.left, row.right)) return false;
        }
        for (int i = 0; i < panel.rows.size(); i++) {
            CustomScoreboardLogic.Row row = panel.rows.get(i);
            if (!matchesSnapshotRow(snapshotIndex++, "player", row.left, row.right)) return false;
        }
        return matchesSnapshotRow(snapshotIndex, "theme_label", panel.themeLabel, "")
                && matchesSnapshotRow(snapshotIndex + 1, "theme", panel.themeValueLine, "");
    }

    private static boolean matchesHypixelSnapshot(String skin,
                                                    HypixelSkinScoreboardLogic.PanelRows panel) {
        if (!matchesSnapshotHeader(skin, panel.rowsBottomToTop.size() + 1)
                || !matchesSnapshotRow(0, "title", panel.title, "")) return false;
        for (int i = panel.rowsBottomToTop.size() - 1, snapshotIndex = 1;
             i >= 0; i--, snapshotIndex++) {
            HypixelSkinScoreboardLogic.Row row = panel.rowsBottomToTop.get(i);
            if (!matchesSnapshotRow(snapshotIndex,
                    row.player == null ? "vanilla" : "player", row.left, row.right)) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesSnapshotHeader(String skin, int rowCount) {
        return lastCustomScoreboardSnapshotRows != null
                && lastCustomScoreboardSnapshotRows.size() == rowCount
                && scoreboardValueEquals(lastCustomScoreboardSnapshotSkin, skin);
    }

    private static boolean matchesSnapshotRow(int index, String kind, String label, String score) {
        RawPacketRecorder.ScoreboardLine cached = lastCustomScoreboardSnapshotRows.get(index);
        return scoreboardValueEquals(cached.kind, kind)
                && scoreboardValueEquals(cached.label, label)
                && scoreboardValueEquals(cached.score, score);
    }

    private static boolean scoreboardValueEquals(String cached, String current) {
        return cached != null && cached.equals(current == null ? "" : current);
    }

    private static void cacheSnapshot(String skin,
                                      List<RawPacketRecorder.ScoreboardLine> snapshotRows) {
        lastCustomScoreboardSnapshotSkin = skin == null ? "" : skin;
        lastCustomScoreboardSnapshotRows = snapshotRows;
    }

    static void resetCustomScoreboardCaptureGateForTest() {
        lastCustomScoreboardCaptureEpoch = Long.MIN_VALUE;
        lastCustomScoreboardSnapshotSkin = null;
        lastCustomScoreboardSnapshotRows = null;
        customScoreboardSnapshotRowBuildCountForTest = 0;
    }

    static int customScoreboardSnapshotRowBuildCountForTest() {
        return customScoreboardSnapshotRowBuildCountForTest;
    }

    private static boolean isPlayerListKeyDown(Object mc) {
        try {
            Object options = fieldAny(mc, "options", "field_1690");
            Object key = fieldAny(options, "playerListKey", "keyPlayerList", "field_1902");
            Object down = invokeAny(key, "isPressed", "isDown", "method_1434");
            if (down instanceof Boolean && ((Boolean) down).booleanValue()) return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            // Fall back to the physical Tab key state below.
        }
        return isTabKeyPhysicallyDown(mc);
    }

    private static boolean isTabKeyPhysicallyDown(Object mc) {
        return FabricInputBridge.isKeyDown(mc, FabricInputBridge.Key.TAB);
    }

    private static Object minecraftClient() throws ReflectiveOperationException {
        Class<?> clientClass = classAny("net.minecraft.client.MinecraftClient",
                "net.minecraft.client.Minecraft", "net.minecraft.class_310");
        Method method = methodAny(clientClass, "getInstance", "method_1551");
        return method.invoke(null);
    }

    private static Class<?> classAny(String... names) throws ClassNotFoundException {
        return RuntimeReflectionCache.classAny(names);
    }

    private static Method methodAny(Class<?> type, String... names) throws NoSuchMethodException {
        return RuntimeReflectionCache.methodAny(type, names);
    }

    private static Object fieldAny(Object target, String... names) throws ReflectiveOperationException {
        return RuntimeReflectionCache.fieldAny(target, names);
    }

    private static Object invokeAny(Object target, String... names) throws ReflectiveOperationException {
        return RuntimeReflectionCache.invokeAny(target, names);
    }

    private static boolean tryInvokeAnyArgs(Object target, String[] names, Object... args)
            throws ReflectiveOperationException {
        if (target == null) return false;
        Method method = RuntimeReflectionCache.findCompatibleMethod(
                target.getClass(), false, names, args);
        if (method == null) return false;
        method.invoke(target, args);
        return true;
    }

    private static void fill(Object graphics, int left, int top, int right, int bottom, int color)
            throws ReflectiveOperationException {
        FILL_CALL.invokeIfPresent(graphics,
                left, top, right, bottom, color);
    }

    private static void drawTextMaybeShadow(Object graphics, Object font, String text,
                                            int x, int y, int color, boolean shadow)
            throws ReflectiveOperationException {
        DRAW_TEXT_CALL.invokeIfPresent(graphics,
                font, text == null ? "" : text, x, y, opaqueColor(color), Boolean.valueOf(shadow));
    }

    private static int opaqueColor(int color) {
        return (color & 0xFF000000) == 0 ? 0xFF000000 | color : color;
    }

    private static int textWidth(Object font, String text) {
        try {
            return intValue(TEXT_WIDTH_CALL.invoke(font, text));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return text == null ? 0 : text.length() * 6;
        }
    }

    private static int fontHeight(Object font) {
        try {
            Object value = fieldAny(font, "fontHeight", "lineHeight", "field_2000");
            return value instanceof Number ? ((Number) value).intValue() : 9;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 9;
        }
    }

    private static int intValue(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private static final class TexturePipelineKey {
        private final Class<?> graphicsType;
        private final Class<?> textureType;

        private TexturePipelineKey(Class<?> graphicsType, Class<?> textureType) {
            this.graphicsType = graphicsType;
            this.textureType = textureType;
        }

        @Override public boolean equals(Object value) {
            if (!(value instanceof TexturePipelineKey)) return false;
            TexturePipelineKey other = (TexturePipelineKey) value;
            return graphicsType.equals(other.graphicsType) && textureType.equals(other.textureType);
        }

        @Override public int hashCode() {
            return graphicsType.hashCode() * 31 + textureType.hashCode();
        }
    }

    private static final class PreviewPlayer {
        private final String name;
        private final String nameColor;
        private final String emblem;
        private final int delta;
        private final int total;
        private final int lowerBound;
        private final boolean currentBuilder;
        private final int buildIcon;
        private final int statusIcon;
        private final int bbRankIndex;

        private PreviewPlayer(String name, String nameColor, String emblem, int delta, int total,
                              int lowerBound, boolean currentBuilder, boolean built,
                              boolean inactive, int bbRankIndex) {
            this.name = name;
            this.nameColor = nameColor;
            this.emblem = emblem;
            this.delta = delta;
            this.total = total;
            this.lowerBound = lowerBound;
            this.currentBuilder = currentBuilder;
            this.bbRankIndex = bbRankIndex;
            this.buildIcon = currentBuilder
                    ? BuilderIconLogic.IDX_SPINNER_0
                    : (built ? BuilderIconLogic.IDX_CHECK : BuilderIconLogic.IDX_INACTIVE);
            this.statusIcon = inactive ? BuilderIconLogic.IDX_INACTIVE : -1;
        }

        private int displayBuildIcon() {
            return currentBuilder ? BuilderIconLogic.spinnerCellForTick(InterfacePreview.previewTicks()) : buildIcon;
        }
    }
}
