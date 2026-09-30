package com.gtbstructures.scoreboard;

import com.gtbstructures.GtbStructuresSettings;
import com.gtbstructures.ModernRuntimeBridge;
import com.gtbstructures.diagnostics.RawPacketRecorder;
import com.gtbstructures.overlay.ForgeGuiTextureBridge;
import com.gtbstructures.overlay.ForgeHudLayerBridge;
import com.gtbstructures.stats.GameStats;
import com.gtbstructures.stats.LangPatterns;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public final class CustomScoreboardOverlayRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(CustomScoreboardOverlayRenderer.class);

    private CustomScoreboardOverlayRenderer() {
    }

    public static void register() {
        AddGuiOverlayLayersEvent.BUS.addListener(CustomScoreboardOverlayRenderer::addLayer);
    }

    private static void addLayer(AddGuiOverlayLayersEvent event) {
        ForgeHudLayerBridge.relocateVanillaScoreboard(event.getLayeredDraw(),
                CustomScoreboardOverlayRenderer::shouldSuppressVanillaScoreboard,
                CustomScoreboardOverlayRenderer::vanillaScoreboardHeightOffset);
        ForgeHudLayerBridge.addAboveChat(event.getLayeredDraw(), "custom_scoreboard",
                CustomScoreboardOverlayRenderer::render);
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
        GameStats.RoundData currentRound = GameStats.INSTANCE.getCurrentRound();
        String canonicalTheme = currentRound == null ? null : currentRound.theme;
        return HypixelSkinScoreboardLogic.buildPanel(
                ModernRuntimeBridge.lastScoreboardTitle(),
                ModernRuntimeBridge.lastScoreboardLines(),
                game, settings.sbConfig, false, GameTracker.tickCounter,
                InterfacePreview.previewLangCode(), canonicalTheme) != null;
    }

    private static int vanillaScoreboardHeightOffset() {
        GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
        return vanillaScoreboardHeightOffsetForMaster(settings.modEnabled,
                settings.sbConfig.isSkin("vanilla"), settings.sbConfig.heightOffset);
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        try {
            renderPanel(graphics);
        } catch (RuntimeException e) {
            LOGGER.warn("Unable to render GtbStructures custom scoreboard", e);
        }
    }

    private static void renderPanel(GuiGraphics graphics) {
        GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
        GtbGame game = GameTracker.INSTANCE.game;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null || mc.getWindow() == null) return;
        Font font = mc.font;

        if (InterfacePreview.isActive()) {
            renderPreviewScoreboard(graphics, font, mc, settings, isPlayerListKeyDown(mc));
            return;
        }
        if (!customScoreboardVisibleForMaster(settings.modEnabled)) return;

        if (!GameStats.INSTANCE.shouldRenderRoundOverlays()) return;
        if (!CustomScoreboardLogic.shouldRenderCustomPanel(settings.sbConfig, game, false)) return;
        if (mc.player == null) return;

        if (HypixelSkinScoreboardLogic.shouldRender(settings.sbConfig, game,
                InterfacePreview.isActive())) {
            boolean expanded = isPlayerListKeyDown(mc);
            GameStats.RoundData currentRound = GameStats.INSTANCE.getCurrentRound();
            String canonicalTheme = currentRound == null ? null : currentRound.theme;
            HypixelSkinScoreboardLogic.PanelRows hypixelPanel =
                    HypixelSkinScoreboardLogic.buildPanel(
                            ModernRuntimeBridge.lastScoreboardTitle(),
                            ModernRuntimeBridge.lastScoreboardLines(),
                            game, settings.sbConfig, expanded, mc.player.tickCount,
                            InterfacePreview.previewLangCode(),
                            canonicalTheme);
            if (hypixelPanel == null) return;
            int contentWidth = HypixelSkinScoreboardLogic.measureContentWidth(
                    hypixelPanel, font::width);
            ScoreboardLayout.Placement placement = ScoreboardLayout.place(
                    mc.getWindow().getGuiScaledWidth(),
                    mc.getWindow().getGuiScaledHeight(),
                    contentWidth, hypixelPanel.lineCount(), settings.sbConfig.heightOffset);
            drawHypixelPanel(graphics, font, hypixelPanel, placement, contentWidth,
                    settings.sbConfig);
            captureHypixelSnapshot(settings.sbConfig.skin, hypixelPanel);
            registerPreviewBounds(placement);
            return;
        }

        CustomScoreboardLogic.PanelRows panel = CustomScoreboardLogic.buildPanelRows(game,
                settings.sbConfig, isPlayerListKeyDown(mc), mc.player.tickCount);
        if (panel == null) return;
        int contentWidth = CustomScoreboardLogic.measureContentWidth(panel, font::width);
        ScoreboardLayout.Placement placement = ScoreboardLayout.place(
                mc.getWindow().getGuiScaledWidth(),
                mc.getWindow().getGuiScaledHeight(),
                contentWidth, panel.lineCount(), settings.sbConfig.heightOffset);
        drawPanel(graphics, font, panel, placement, contentWidth, settings.sbConfig);
        captureSnapshot(settings.sbConfig.skin, panel);
        registerPreviewBounds(placement);
    }

    private static void registerPreviewBounds(ScoreboardLayout.Placement placement) {
        PreviewLayoutEditor.register("scoreboard",
                placement.bgX1 - CustomScoreboardLogic.ICON_OUTSIDE_GAP - CustomScoreboardLogic.ICON_DRAW_SIZE,
                placement.bgY1,
                placement.bgX2 - placement.bgX1
                        + CustomScoreboardLogic.ICON_OUTSIDE_GAP + CustomScoreboardLogic.ICON_DRAW_SIZE,
                placement.bgY2 - placement.bgY1,
                PreviewLayoutEditor.SCOREBOARD);
    }

    private static void renderPreviewScoreboard(GuiGraphics graphics, Font font, Minecraft mc,
                                                GtbStructuresSettings settings, boolean expanded) {
        String lang = InterfacePreview.previewLangCode();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        if (nativeOnlyPreviewForMaster(settings.modEnabled)) {
            ScoreboardConfig vanilla = new ScoreboardConfig();
            renderPreviewSidebar(graphics, font, screenWidth, screenHeight,
                    InterfacePreview.vanillaRows(lang), false, lang, vanilla, 0);
            return;
        }
        if (!settings.sbConfig.isCustomEnabled() || settings.sbConfig.isSkin("vanilla")) {
            renderPreviewSidebar(graphics, font, screenWidth, screenHeight,
                    InterfacePreview.vanillaRows(lang), false, lang, settings.sbConfig,
                    settings.sbConfig.heightOffset);
        } else if (settings.sbConfig.isSkin("hypixel")) {
            renderPreviewSidebar(graphics, font, screenWidth, screenHeight,
                    InterfacePreview.hypixelRows(lang, expanded), true, lang, settings.sbConfig,
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

    private static void renderPreviewDefaultPanel(GuiGraphics graphics, Font font,
                                                  int screenWidth, int screenHeight,
                                                  String lang, boolean expanded,
                                                  ScoreboardConfig config, int heightOffset) {
        PreviewPlayer[] players = previewPlayers();
        String[] left = new String[players.length];
        String[] right = new String[players.length];
        int[] places = previewDensePlaces(players);
        for (int i = 0; i < players.length; i++) {
            left[i] = playerLeft(players[i], config, expanded, "en", places[i]);
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
            contentWidth = Math.max(contentWidth, font.width(row.left) + font.width(row.right));
        }
        contentWidth = Math.max(contentWidth, font.width(themeLabel));
        contentWidth = Math.max(contentWidth, font.width(themeValue));
        for (int i = 0; i < players.length; i++) {
            int leftWidth = font.width(left[i])
                    + (players[i].statusIcon >= 0
                    ? CustomScoreboardLogic.ICON_DRAW_SIZE + CustomScoreboardLogic.INLINE_STATUS_GAP : 0);
            contentWidth = Math.max(contentWidth, leftWidth + 6 + font.width(right[i]));
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
                        graphics.fill(bgLeft, bgTop, bgRight, bgBottom, color));
        graphics.fill(placement.bgX1, placement.bgY1, placement.bgX2, placement.bgY2,
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
                    placement.bgX1, placement.x, placement.bgX2, y, contentWidth, config);
            y += ScoreboardLayout.LINE_HEIGHT;
        }
        drawSeparator(graphics, placement.x, y, contentWidth);
        y += ScoreboardLayout.LINE_HEIGHT;
        graphics.drawString(font, themeLabel, placement.x, y, opaqueColor(0xFFFFFF), config.textShadow);
        y += ScoreboardLayout.LINE_HEIGHT;
        graphics.drawString(font, themeValue, placement.x + font.width(" "), y,
                opaqueColor(0xFFFFFF), config.textShadow);
    }

    private static void renderPreviewSidebar(GuiGraphics graphics, Font font,
                                             int screenWidth, int screenHeight,
                                             InterfacePreview.PreviewRow[] rows,
                                             boolean customPlayerRows,
                                             String lang,
                                             ScoreboardConfig config, int heightOffset) {
        String title = animatedHypixelTitle();
        PreviewPlayer[] players = previewPlayers();
        int contentWidth = font.width(title);
        for (int i = 0; i < rows.length; i++) {
            InterfacePreview.PreviewRow row = rows[i];
            int leftWidth = font.width(row.left);
            PreviewPlayer player = customPlayerRows ? previewPlayerForHypixelRow(players, i) : null;
            if (player != null && player.statusIcon >= 0) {
                leftWidth += CustomScoreboardLogic.ICON_DRAW_SIZE + CustomScoreboardLogic.INLINE_STATUS_GAP;
            }
            int rightWidth = row.right.isEmpty() ? 0 : font.width(row.right);
            contentWidth = Math.max(contentWidth, leftWidth + (rightWidth == 0 ? 0 : 6 + rightWidth));
        }

        int lineHeight = font.lineHeight;
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
                (left, top, right, bottom, color) ->
                        graphics.fill(left, top, right, bottom, color));
        graphics.fill(leftX - 2, topY, rightCellRight, topY + lineHeight, titleBackground);
        graphics.fill(leftX - 2, topY + lineHeight, rightCellRight,
                topY + lineHeight + 1, rowBackground);
        int y = topY + lineHeight + 1;
        for (int i = 0; i < rows.length; i++) {
            graphics.fill(leftX - 2, y, rightCellRight, y + lineHeight, rowBackground);
            y += lineHeight;
        }
        graphics.drawString(font, title,
                leftX + contentWidth / 2 - font.width(title) / 2, topY,
                opaqueColor(0xFFFFFF), shadow);

        y = topY + lineHeight + 1;
        for (int i = 0; i < rows.length; i++) {
            InterfacePreview.PreviewRow row = rows[i];
            PreviewPlayer player = customPlayerRows ? previewPlayerForHypixelRow(players, i) : null;
            if (customPlayerRows && player != null) {
                drawPreviewPlayerRow(graphics, font, player, row.left, row.right,
                        leftX - 2, leftX, rightCellRight, y, contentWidth, config);
            } else {
                graphics.drawString(font, row.left, leftX, y, opaqueColor(0xFFFFFF), shadow);
                if (!row.right.isEmpty()) {
                    int rightWidth = font.width(row.right);
                    graphics.drawString(font, row.right, rightCellRight - rightWidth,
                            y, opaqueColor(0xFFFFFF), shadow);
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

    private static void drawPanel(GuiGraphics graphics, Font font,
                                  CustomScoreboardLogic.PanelRows panel,
                                  ScoreboardLayout.Placement placement, int contentWidth,
                                  ScoreboardConfig config) {
        graphics.fill(placement.bgX1, placement.bgY1, placement.bgX2, placement.bgY2,
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
            int iconY = stateIconY(y, ScoreboardLayout.LINE_HEIGHT);
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
            graphics.drawString(font, row.left, textX, y,
                    opaqueColor(0xFFFFFF), config.textShadow);
            int rightWidth = font.width(row.right);
            graphics.drawString(font, row.right,
                    placement.x + contentWidth - rightWidth, y,
                    opaqueColor(0xFFFFFF), config.textShadow);
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
        graphics.drawString(font, panel.themeLabel, placement.x, y,
                opaqueColor(0xFFFFFF), config.textShadow);
        y += ScoreboardLayout.LINE_HEIGHT;
        graphics.drawString(font, panel.themeValueLine, placement.x + font.width(" "), y,
                opaqueColor(0xFFFFFF), config.textShadow);
    }

    private static void drawHypixelPanel(GuiGraphics graphics, Font font,
                                         HypixelSkinScoreboardLogic.PanelRows panel,
                                         ScoreboardLayout.Placement placement, int contentWidth,
                                         ScoreboardConfig config) {
        int y = placement.y;
        graphics.fill(placement.bgX1, y, placement.bgX2,
                y + panel.lineCount() * ScoreboardLayout.LINE_HEIGHT, config.backgroundColor());
        int titleWidth = font.width(panel.title);
        graphics.drawString(font, panel.title,
                placement.x + (contentWidth - titleWidth) / 2, y,
                opaqueColor(0xFFFFFF), config.textShadow);
        y += ScoreboardLayout.LINE_HEIGHT;

        for (int i = panel.rowsBottomToTop.size() - 1; i >= 0; i--) {
            HypixelSkinScoreboardLogic.Row row = panel.rowsBottomToTop.get(i);
            drawHypixelRow(graphics, font, row, placement, contentWidth, y, config);
            y += ScoreboardLayout.LINE_HEIGHT;
        }
    }

    private static void drawHypixelRow(GuiGraphics graphics, Font font,
                                       HypixelSkinScoreboardLogic.Row row,
                                       ScoreboardLayout.Placement placement,
                                       int contentWidth, int y, ScoreboardConfig config) {
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
            int iconY = stateIconY(y, ScoreboardLayout.LINE_HEIGHT);
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
        graphics.drawString(font, row.left, textX, y,
                opaqueColor(0xFFFFFF), config.textShadow);
        if (!row.right.isEmpty()) {
            int rightWidth = font.width(row.right);
            graphics.drawString(font, row.right,
                    placement.x + contentWidth - rightWidth, y,
                    opaqueColor(0xFFFFFF), config.textShadow);
        }
    }

    private static void drawPreviewPlayerRow(GuiGraphics graphics, Font font, PreviewPlayer player,
                                             String left, String right,
                                             int bgLeft, int textLeft, int rightCellRight,
                                             int rowY, int contentWidth, ScoreboardConfig config) {
        boolean hasStatusIcon = player.statusIcon >= 0;
        int decorationY = rowY + CustomScoreboardLogic.STATE_DECORATION_Y_OFFSET;
        int iconY = stateIconY(rowY, ScoreboardLayout.LINE_HEIGHT);
        if (!hasStatusIcon && player.currentBuilder) {
            drawRowHighlight(graphics, bgLeft, decorationY, rightCellRight, false, true, config);
            drawRowMarker(graphics, bgLeft, decorationY, -1, true, config);
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
        graphics.drawString(font, left, textX, rowY,
                opaqueColor(0xFFFFFF), config.textShadow);
        int rightWidth = font.width(right);
        graphics.drawString(font, right, textLeft + contentWidth - rightWidth, rowY,
                opaqueColor(0xFFFFFF), config.textShadow);
        if (hasStatusIcon) {
            drawRowHighlight(graphics, bgLeft, decorationY, rightCellRight, true, false, config);
            drawRowMarker(graphics, bgLeft, decorationY, player.statusIcon, false, config);
            drawIcon(graphics, player.statusIcon, textLeft, iconY,
                    CustomScoreboardLogic.ICON_DRAW_SIZE,
                    BuilderIconLogic.tintFor(player.statusIcon));
        }
    }

    private static void drawSeparator(GuiGraphics graphics, int x, int y, int contentWidth) {
        graphics.fill(x - 1, y + 3, x + contentWidth + 1, y + 4, 0x60AAAAAA);
    }

    private static void drawPair(GuiGraphics graphics, Font font, String left, String right,
                                 int x, int y, int contentWidth, boolean shadow) {
        graphics.drawString(font, left, x, y, opaqueColor(0xFFFFFF), shadow);
        int rightWidth = font.width(right);
        graphics.drawString(font, right, x + contentWidth - rightWidth, y,
                opaqueColor(0xFFFFFF), shadow);
    }

    private static void drawRowHighlight(GuiGraphics graphics, int left, int rowY, int right,
                                         boolean hasStatusIcon, boolean currentBuilder,
                                         ScoreboardConfig config) {
        int color = CustomScoreboardLogic.rowHighlightColor(config, hasStatusIcon, currentBuilder);
        if (color != 0) {
            graphics.fill(left, rowY, right, rowY + ScoreboardLayout.LINE_HEIGHT, color);
        }
    }

    private static void drawRowMarker(GuiGraphics graphics, int left, int rowY,
                                      int statusIcon, boolean currentBuilder,
                                      ScoreboardConfig config) {
        int color = CustomScoreboardLogic.rowMarkerColor(config, statusIcon, currentBuilder);
        if (color != 0) {
            graphics.fill(left - 1, rowY, left, rowY + ScoreboardLayout.LINE_HEIGHT, color);
        }
    }

    private static void drawIcon(GuiGraphics graphics, int cell, int x, int y, int size, int tintRgb) {
        if (cell < 0 || cell >= BuilderIconLogic.CELL_COUNT) return;
        ForgeGuiTextureBridge.drawRegion(graphics,
                "gtbstructures:textures/font/gtb_icons.png",
                x, y, size, size,
                cell * BuilderIconLogic.CELL_SIZE, 0,
                BuilderIconLogic.GLYPH_SOURCE_SIZE, BuilderIconLogic.GLYPH_SOURCE_SIZE,
                BuilderIconLogic.CELL_COUNT * BuilderIconLogic.CELL_SIZE,
                BuilderIconLogic.CELL_SIZE, tintRgb);
    }

    private static int spinnerTintTick() {
        return InterfacePreview.isActive() ? InterfacePreview.previewTicks() : GameTracker.tickCounter;
    }

    private static void captureSnapshot(String skin, CustomScoreboardLogic.PanelRows panel) {
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
        RawPacketRecorder.INSTANCE.captureCustomScoreboard(skin, snapshotRows);
    }

    private static void captureHypixelSnapshot(String skin, HypixelSkinScoreboardLogic.PanelRows panel) {
        List<RawPacketRecorder.ScoreboardLine> snapshotRows =
                new ArrayList<RawPacketRecorder.ScoreboardLine>();
        snapshotRows.add(new RawPacketRecorder.ScoreboardLine("title", panel.title, ""));
        for (int i = panel.rowsBottomToTop.size() - 1; i >= 0; i--) {
            HypixelSkinScoreboardLogic.Row row = panel.rowsBottomToTop.get(i);
            snapshotRows.add(new RawPacketRecorder.ScoreboardLine(
                    row.player == null ? "vanilla" : "player", row.left, row.right));
        }
        RawPacketRecorder.INSTANCE.captureCustomScoreboard(skin, snapshotRows);
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
                                     String lang, int placeNumber) {
        String place = config.showPlaces(expanded) ? CustomScoreboardLogic.placePrefix(placeNumber) : "";
        String rank = config.showTitles(expanded) ? bbRankPrefix(player, lang) : "";
        String emblem = !config.showEmblems(expanded)
                || player.emblem == null || player.emblem.isEmpty() ? "" : " " + player.emblem;
        return place + rank + player.nameColor + player.name + emblem;
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
        return animatedHypixelTitle(System.currentTimeMillis());
    }

    private static String animatedHypixelTitle(long nowMillis) {
        final String base = "GUESS THE BUILD";
        long tick = (nowMillis / 100L) % (base.length() + 6);
        if (tick >= base.length()) {
            return (tick % 2L == 0L ? "\u00a7e\u00a7l" : "\u00a7f\u00a7l") + base;
        }
        int highlight = (int) tick;
        StringBuilder out = new StringBuilder(base.length() * 4);
        for (int i = 0; i < base.length(); i++) {
            if (i < highlight) out.append("\u00a7f\u00a7l");
            else if (i == highlight) out.append("\u00a76\u00a7l");
            else out.append("\u00a7e\u00a7l");
            out.append(base.charAt(i));
        }
        return out.toString();
    }

    private static int stateIconY(int rowY, int rowHeight) {
        return rowY + (rowHeight - CustomScoreboardLogic.ICON_DRAW_SIZE) / 2
                + CustomScoreboardLogic.STATE_DECORATION_Y_OFFSET;
    }

    private static boolean isPlayerListKeyDown(Minecraft mc) {
        if (mc == null) return false;
        if (mc.options != null
                && mc.options.keyPlayerList != null
                && mc.options.keyPlayerList.isDown()) {
            return true;
        }
        try {
            long windowHandle = mc.getWindow() == null ? 0L : mc.getWindow().handle();
            return windowHandle != 0L
                    && GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_TAB) == GLFW.GLFW_PRESS;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static int opaqueColor(int color) {
        return (color & 0xFF000000) == 0 ? 0xFF000000 | color : color;
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
            return currentBuilder
                    ? BuilderIconLogic.spinnerCellForTick(InterfacePreview.previewTicks()) : buildIcon;
        }
    }
}
