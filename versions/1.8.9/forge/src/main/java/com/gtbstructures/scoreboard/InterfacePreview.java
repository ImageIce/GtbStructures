package com.gtbstructures.scoreboard;

import com.gtbstructures.GtbStructures;
import com.gtbstructures.chat.ChatCooldownCounter;
import com.gtbstructures.hints.HintOverlay;
import com.gtbstructures.overlay.HudPanelScale;
import com.gtbstructures.overlay.HudPosition;
import com.gtbstructures.stats.GameStats;
import com.gtbstructures.stats.LangPatterns;
import com.gtbstructures.translation.TranslationData;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;

public final class InterfacePreview {
    public static final InterfacePreview INSTANCE = new InterfacePreview();

    private static final String PREVIEW_THEME = "Swimming Pool";
    private static final String PREVIEW_TIMER = "00:13";
    private static final String PREVIEW_DATE = "\u00a77" + "05/09/26  \u00a78m38D1AF";
    private static final String PREVIEW_FOOTER = "\u00a7ewww.hypixel.net";
    private static final int SCOREBOARD_ICON_SIZE = 8;
    private static final int SCOREBOARD_ICON_OUTSIDE_GAP = 2;
    private static final int SCOREBOARD_INLINE_ICON_GAP = 2;
    private static final int COOLDOWN_BAR_WIDTH = 42;
    private static final int COOLDOWN_BAR_HEIGHT = 4;
    private static final int COOLDOWN_TIMER_BG_PAD_X = 2;
    private static final int COOLDOWN_TIMER_BG_PAD_Y = 1;

    private boolean enabled;
    private World worldAnchor;
    private boolean mockActionbarVisible = true;

    private InterfacePreview() {
    }

    public boolean toggle() {
        enabled = !enabled;
        if (enabled) {
            Minecraft mc = Minecraft.getMinecraft();
            worldAnchor = mc == null ? null : mc.theWorld;
            mockActionbarVisible = true;
            PreviewLayoutEditor.INSTANCE.reset();
            openPreviewScreen();
        } else {
            worldAnchor = null;
            mockActionbarVisible = true;
            PreviewLayoutEditor.INSTANCE.reset();
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
        ChatCooldownCounter.INSTANCE.cancelPreviewTest();
        worldAnchor = null;
        mockActionbarVisible = true;
        PreviewLayoutEditor.INSTANCE.reset();
        closePreviewScreen();
    }

    static boolean isMockActionbarVisible() {
        return INSTANCE.mockActionbarVisible;
    }

    static void setMockActionbarVisible(boolean visible) {
        INSTANCE.mockActionbarVisible = visible;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !enabled) return;
        Minecraft mc = Minecraft.getMinecraft();
        World currentWorld = mc == null ? null : mc.theWorld;
        if (currentWorld == null) {
            disableSilently();
            return;
        }
        if (worldAnchor == null) {
            worldAnchor = currentWorld;
            return;
        }
        if (currentWorld != worldAnchor) {
            disableSilently();
            return;
        }
        openPreviewScreen();
    }

    private static void openPreviewScreen() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.currentScreen instanceof PreviewScreen) return;
        mc.displayGuiScreen(new PreviewScreen());
    }

    private static void closePreviewScreen() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.currentScreen instanceof PreviewScreen) {
            mc.displayGuiScreen(null);
        }
    }

    @SubscribeEvent
    public void onConnected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        disableSilently();
    }

    @SubscribeEvent
    public void onDisconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        disableSilently();
    }

    @SubscribeEvent
    public void onRender(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.CHAT) return;
        if (!enabled) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.theWorld == null || mc.fontRendererObj == null) return;

        if (!GtbStructures.modEnabled) {
            renderMockActionbar(mc, true, false);
            renderVanillaScoreboard(mc, 0, false);
            return;
        }
        renderHintStructure(mc, false);
        renderMockActionbar(mc, false, false);
        renderCooldownCounter(mc, false);
        renderScoreboard(mc, false);
    }

    static void collectPreviewLabels(Minecraft mc) {
        if (!isActive() || mc == null || mc.theWorld == null || mc.fontRendererObj == null) return;
        if (!GtbStructures.modEnabled) {
            renderMockActionbar(mc, true, true);
            renderVanillaScoreboard(mc, 0, true);
            return;
        }
        renderHintStructure(mc, true);
        renderMockActionbar(mc, false, true);
        renderCooldownCounter(mc, true);
        renderScoreboard(mc, true);
    }

    private static void renderHintStructure(Minecraft mc, boolean layoutOnly) {
        if (!GtbStructures.enabled) return;
        ScaledResolution sr = new ScaledResolution(mc);
        FontRenderer fr = mc.fontRendererObj;
        String structure = previewHintStructure(previewLangCode());
        String display = GtbStructures.colorBrackets + "[ "
                + GtbStructures.colorNumbers + structure.replace("-", GtbStructures.colorHyphens + "-" + GtbStructures.colorNumbers)
                + GtbStructures.colorBrackets + " ]";
        int width = fr.getStringWidth(display);
        float panelScale = HudPanelScale.normalize(GtbStructures.hintStructureScale);
        int scaledWidth = HudPanelScale.scaledPixels(width, panelScale);
        int scaledHeight = HudPanelScale.scaledPixels(fr.FONT_HEIGHT, panelScale);
        int drawX = HudPosition.left(GtbStructures.posX, sr.getScaledWidth(), scaledWidth, 2);
        int y = HudPosition.top(GtbStructures.posY, sr.getScaledHeight(), scaledHeight, 2);
        if (layoutOnly) {
            PreviewLayoutEditor.register("hintstructure", drawX, y, scaledWidth, scaledHeight,
                    PreviewLayoutEditor.HINT_STRUCTURE);
            return;
        }
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(drawX, y, 0.0F);
            GlStateManager.scale(panelScale, panelScale, 1.0F);
            if (GtbStructures.hintStructureBackground) {
                drawTextBackground(mc, 0, 0, width);
            }
            fr.drawStringWithShadow(display, 0, 0, 0xFFFFFF);
        } finally {
            GlStateManager.popMatrix();
        }
    }

    private static void renderMockActionbar(Minecraft mc, boolean nativeOnly,
                                            boolean layoutOnly) {
        if (!previewActionbarVisibleForMaster(GtbStructures.modEnabled,
                INSTANCE.mockActionbarVisible)) return;
        ScaledResolution sr = new ScaledResolution(mc);
        FontRenderer fr = mc.fontRendererObj;
        String text = previewActionbarText(previewLangCode());
        int width = fr.getStringWidth(text);
        int x = sr.getScaledWidth() / 2 - width / 2;
        int y = sr.getScaledHeight() - 72;
        if (layoutOnly) {
            PreviewLayoutEditor.register("actionbar", x, y, width, fr.FONT_HEIGHT,
                    PreviewLayoutEditor.ACTIONBAR);
            return;
        }
        if (!nativeOnly && GtbStructures.hintBackgroundOverlay) {
            drawTextBackground(mc, x, y, width);
        }
        fr.drawStringWithShadow(text, x, y, 0xFFFFFF);
    }

    static String cooldownPreviewText(boolean timer, boolean bar, boolean sfx) {
        return cooldownPreviewText(timer, bar, sfx, -1L);
    }

    static String cooldownPreviewText(boolean timer, boolean bar, boolean sfx, long testRemainingMs) {
        if (timer) return ChatCooldownCounter.displayRemaining(testRemainingMs < 0L ? 3000L : testRemainingMs);
        // Keep an editor anchor when the runtime counter has no visible parts.
        return !bar && sfx ? "SFX only" : "";
    }

    static int cooldownPreviewFillWidth(long testRemainingMs) {
        return ChatCooldownCounter.fillWidth(COOLDOWN_BAR_WIDTH, testRemainingMs < 0L ? 3000L : testRemainingMs);
    }

    private static void renderCooldownCounter(Minecraft mc, boolean layoutOnly) {
        if (!GtbStructures.chatCooldownCounter) return;
        boolean showTimer = GtbStructures.chatCooldownTimerEnabled;
        boolean showBar = GtbStructures.chatCooldownBarEnabled;
        long testRemaining = ChatCooldownCounter.INSTANCE.previewRemainingMs(System.currentTimeMillis());
        String previewText = cooldownPreviewText(showTimer, showBar, GtbStructures.chatCooldownSfxEnabled, testRemaining);
        boolean showLabel = !previewText.isEmpty();
        if (!showLabel && !showBar) return;
        ScaledResolution sr = new ScaledResolution(mc);
        int fillColor = GtbStructures.minecraftColorArgb(GtbStructures.chatCooldownBarColor, 0xCC);
        String label = (showTimer
                ? GtbStructures.normalizeMinecraftColorCode(GtbStructures.chatCooldownTimerColor, "\u00a7f")
                : "\u00a77") + previewText;
        FontRenderer fr = mc.fontRendererObj;
        int labelWidth = showLabel ? fr.getStringWidth(label) : 0;
        int widgetWidth = Math.max(showBar ? COOLDOWN_BAR_WIDTH : 0, labelWidth);
        int widgetHeight = (showLabel ? fr.FONT_HEIGHT : 0)
                + (showLabel && showBar ? 3 : 0)
                + (showBar ? COOLDOWN_BAR_HEIGHT : 0);
        float panelScale = HudPanelScale.normalize(GtbStructures.chatCooldownScale);
        int scaledWidth = HudPanelScale.scaledPixels(widgetWidth, panelScale);
        int scaledHeight = HudPanelScale.scaledPixels(widgetHeight, panelScale);
        int widgetLeft = HudPosition.left(GtbStructures.chatCooldownPosX,
                sr.getScaledWidth(), scaledWidth, 2);
        int widgetTop = HudPosition.top(GtbStructures.chatCooldownPosY,
                sr.getScaledHeight(), scaledHeight, 2);
        if (layoutOnly) {
            PreviewLayoutEditor.register("cooldowncounter", widgetLeft, widgetTop,
                    scaledWidth, scaledHeight, PreviewLayoutEditor.COOLDOWN);
            return;
        }
        int centerX = widgetWidth / 2;
        int x = centerX - COOLDOWN_BAR_WIDTH / 2;
        int y = showLabel ? fr.FONT_HEIGHT + 3 : 0;
        int labelX = centerX - labelWidth / 2;
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(widgetLeft, widgetTop, 0.0F);
            GlStateManager.scale(panelScale, panelScale, 1.0F);
            if (showTimer && GtbStructures.chatCooldownBackground) {
                Gui.drawRect(
                        labelX - COOLDOWN_TIMER_BG_PAD_X,
                        -COOLDOWN_TIMER_BG_PAD_Y,
                        labelX + labelWidth + COOLDOWN_TIMER_BG_PAD_X,
                        fr.FONT_HEIGHT + COOLDOWN_TIMER_BG_PAD_Y,
                        ScoreboardLayout.BACKGROUND_COLOR
                );
            }
            if (showBar) {
                if (GtbStructures.chatCooldownBarBackground) {
                    Gui.drawRect(x - 1, y - 1, x + COOLDOWN_BAR_WIDTH + 1, y + COOLDOWN_BAR_HEIGHT + 1, ScoreboardLayout.BACKGROUND_COLOR);
                    Gui.drawRect(x, y, x + COOLDOWN_BAR_WIDTH, y + COOLDOWN_BAR_HEIGHT, 0x66000000);
                }
                int fillWidth = cooldownPreviewFillWidth(testRemaining);
                if (fillWidth > 0) Gui.drawRect(x, y, x + fillWidth, y + COOLDOWN_BAR_HEIGHT, fillColor);
            }
            if (showLabel) {
                fr.drawStringWithShadow(label, labelX, 0, 0xFFFFFF);
            }
        } finally {
            GlStateManager.popMatrix();
        }
    }

    private static void renderScoreboard(Minecraft mc, boolean layoutOnly) {
        if (!GtbStructures.sbConfig.isCustomEnabled() || GtbStructures.sbConfig.isSkin("vanilla")) {
            renderVanillaScoreboard(mc, layoutOnly);
        } else if (GtbStructures.sbConfig.isSkin("hypixel")) {
            renderHypixelScoreboard(mc, layoutOnly);
        } else {
            renderDefaultScoreboard(mc, layoutOnly);
        }
    }

    private static void renderDefaultScoreboard(Minecraft mc, boolean layoutOnly) {
        FontRenderer fr = mc.fontRendererObj;
        ScaledResolution sr = new ScaledResolution(mc);
        PreviewPlayer[] players = previewPlayers();
        boolean expanded = CustomScoreboard.isPlayerListKeyDown(mc);
        boolean showPointsGained = GtbStructures.sbConfig.showPointsGained(expanded);
        boolean showPlaces = GtbStructures.sbConfig.showPlaces(expanded);
        boolean showTitles = GtbStructures.sbConfig.showTitles(expanded);
        boolean showEmblems = GtbStructures.sbConfig.showEmblems(expanded);
        boolean textShadow = GtbStructures.sbConfig.textShadow;
        PreviewRow[] metadataRows = defaultMetadataRows(players);
        String[] left = new String[players.length];
        String[] right = new String[players.length];
        int[] places = previewDensePlaces(players);
        for (int i = 0; i < players.length; i++) {
            left[i] = playerLeft(players[i], places[i], showPlaces, showTitles, showEmblems, false);
            right[i] = playerRight(players[i], showPointsGained);
        }

        String theme = localizedPreviewTheme();
        String themeLabel = "\u00a77Theme [\u00a7a" + lengthBucket(theme) + "\u00a77]:";
        String themeValue = "\u00a7a" + theme;

        int contentWidth = 80;
        for (PreviewRow row : metadataRows) {
            contentWidth = Math.max(contentWidth, measurePair(fr, row.left, row.right));
        }
        contentWidth = Math.max(contentWidth, fr.getStringWidth(themeLabel));
        contentWidth = Math.max(contentWidth, fr.getStringWidth(themeValue));
        for (int i = 0; i < players.length; i++) {
            int lw = fr.getStringWidth(left[i]) + (players[i].statusIcon >= 0 ? SCOREBOARD_ICON_SIZE + SCOREBOARD_INLINE_ICON_GAP : 0);
            int w = lw + 6 + fr.getStringWidth(right[i]);
            contentWidth = Math.max(contentWidth, w);
        }
        contentWidth += 6;

        int lineCount = metadataRows.length + 1 + players.length + 1 + 2;
        ScoreboardLayout.Placement p = ScoreboardLayout.place(sr, fr, contentWidth, lineCount,
                GtbStructures.sbConfig.heightOffset);
        int editorLeft = p.bgX1 - SCOREBOARD_ICON_OUTSIDE_GAP - SCOREBOARD_ICON_SIZE;
        if (layoutOnly) {
            PreviewLayoutEditor.register("scoreboard", editorLeft, p.bgY1,
                    p.bgX2 - editorLeft, p.bgY2 - p.bgY1, PreviewLayoutEditor.SCOREBOARD);
            return;
        }
        Gui.drawRect(p.bgX1, p.bgY1, p.bgX2, p.bgY2, GtbStructures.sbConfig.backgroundColor());

        int y = p.y;
        for (PreviewRow row : metadataRows) {
            drawPair(fr, row.left, row.right, p.x, y, contentWidth, textShadow);
            y += ScoreboardLayout.LINE_HEIGHT;
        }
        Gui.drawRect(p.x - 1, y + 3, p.x + contentWidth + 1, y + 4, 0x60AAAAAA);
        y += ScoreboardLayout.LINE_HEIGHT;

        for (int i = 0; i < players.length; i++) {
            drawScoreboardPlayerRow(fr, players[i], left[i], right[i],
                    p.bgX1, p.x, p.bgX2, y, contentWidth, textShadow, true);
            y += ScoreboardLayout.LINE_HEIGHT;
        }

        Gui.drawRect(p.x - 1, y + 3, p.x + contentWidth + 1, y + 4, 0x60AAAAAA);
        y += ScoreboardLayout.LINE_HEIGHT;
        drawString(fr, themeLabel, p.x, y, textShadow);
        y += ScoreboardLayout.LINE_HEIGHT;
        drawString(fr, themeValue, p.x + fr.getStringWidth(" "), y, textShadow);
    }

    private static void renderHypixelScoreboard(Minecraft mc, boolean layoutOnly) {
        renderSidebarScoreboard(mc,
                hypixelRows(previewLangCode(), CustomScoreboard.isPlayerListKeyDown(mc)),
                true, layoutOnly);
    }

    private static void renderVanillaScoreboard(Minecraft mc, boolean layoutOnly) {
        renderVanillaScoreboard(mc, GtbStructures.sbConfig.heightOffset, layoutOnly);
    }

    private static void renderVanillaScoreboard(Minecraft mc, int heightOffset,
                                                boolean layoutOnly) {
        renderSidebarScoreboard(mc, vanillaRows(previewLangCode()), false,
                heightOffset, layoutOnly);
    }

    private static void renderSidebarScoreboard(Minecraft mc, PreviewRow[] rows,
                                                boolean customPlayerRows,
                                                boolean layoutOnly) {
        renderSidebarScoreboard(mc, rows, customPlayerRows,
                GtbStructures.sbConfig.heightOffset, layoutOnly);
    }

    private static void renderSidebarScoreboard(Minecraft mc, PreviewRow[] rows,
                                                boolean customPlayerRows, int heightOffset,
                                                boolean layoutOnly) {
        FontRenderer fr = mc.fontRendererObj;
        ScaledResolution sr = new ScaledResolution(mc);
        String title = animatedHypixelTitle();
        int contentWidth = fr.getStringWidth(title);
        for (PreviewRow row : rows) {
            int lw = fr.getStringWidth(row.left);
            if (row.player != null && row.player.statusIcon >= 0) {
                lw += SCOREBOARD_ICON_SIZE + SCOREBOARD_INLINE_ICON_GAP;
            }
            int rw = row.right.isEmpty() ? 0 : fr.getStringWidth(row.right);
            contentWidth = Math.max(contentWidth, lw + (rw == 0 ? 0 : 6 + rw));
        }

        int lineH = fr.FONT_HEIGHT;
        int totalPanelHeight = (rows.length + 1) * lineH;
        int bottomY = sr.getScaledHeight() / 2 + totalPanelHeight / 2 - heightOffset;
        int topY = bottomY - totalPanelHeight;
        int rightX = sr.getScaledWidth() - 3;
        int leftX = rightX - contentWidth;
        int rightCellRight = rightX + 2;
        int editorLeft = leftX - 2 - SCOREBOARD_ICON_OUTSIDE_GAP - SCOREBOARD_ICON_SIZE;
        if (layoutOnly) {
            PreviewLayoutEditor.register("scoreboard", editorLeft, topY,
                    rightCellRight - editorLeft, totalPanelHeight + 1,
                    PreviewLayoutEditor.SCOREBOARD);
            return;
        }
        boolean textShadow = customPlayerRows && GtbStructures.sbConfig.textShadow;
        int backgroundColor = customPlayerRows
                ? GtbStructures.sbConfig.backgroundColor()
                : ScoreboardLayout.BACKGROUND_COLOR;
        int titleBackgroundColor = customPlayerRows
                ? GtbStructures.sbConfig.titleBackgroundColor()
                : 0x60000000;

        Gui.drawRect(leftX - 2, topY, rightCellRight, topY + lineH, titleBackgroundColor);
        Gui.drawRect(leftX - 2, topY + lineH, rightCellRight, topY + lineH + 1, backgroundColor);
        int y = topY + lineH + 1;
        for (PreviewRow row : rows) {
            Gui.drawRect(leftX - 2, y, rightCellRight, y + lineH, backgroundColor);
            y += lineH;
        }

        drawString(fr, title, leftX + contentWidth / 2 - fr.getStringWidth(title) / 2, topY, textShadow);

        y = topY + lineH + 1;
        for (PreviewRow row : rows) {
            if (customPlayerRows && row.player != null) {
                drawScoreboardPlayerRow(fr, row.player, row.left, row.right,
                        leftX - 2, leftX, rightCellRight, y, contentWidth, textShadow, false);
            } else {
                drawString(fr, row.left, leftX, y, textShadow);
                if (!row.right.isEmpty()) {
                    int rw = fr.getStringWidth(row.right);
                    drawString(fr, row.right, rightCellRight - rw, y, textShadow);
                }
            }
            y += lineH;
        }
    }

    private static PreviewRow[] vanillaRows(String lang) {
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

    static String vanillaPreviewLineForTest(String lang, int index) {
        PreviewRow[] rows = vanillaRows(lang);
        return index >= 0 && index < rows.length ? rows[index].left : null;
    }

    static String vanillaPreviewRightForTest(String lang, int index) {
        PreviewRow[] rows = vanillaRows(lang);
        return index >= 0 && index < rows.length ? rows[index].right : null;
    }

    private static PreviewRow[] hypixelRows(String lang, boolean expanded) {
        PreviewPlayer[] players = previewPlayers();
        boolean showPointsGained = GtbStructures.sbConfig.showPointsGained(expanded);
        boolean showPlaces = GtbStructures.sbConfig.showPlaces(expanded);
        boolean showTitles = GtbStructures.sbConfig.showTitles(expanded);
        boolean showEmblems = GtbStructures.sbConfig.showEmblems(expanded);
        int[] places = previewDensePlaces(players);
        PreviewRow[] rows = new PreviewRow[players.length + 10];
        int i = 0;
        rows[i++] = new PreviewRow(PREVIEW_DATE, "");
        String roundProgress = GtbStructures.sbConfig.showHypixelRounds(expanded)
                ? CustomScoreboard.roundProgressForScoreboard(10, 10, false, false)
                : "";
        rows[i++] = new PreviewRow("\u00a7f" + LangPatterns.scoreboardBuilderLabel(lang), roundProgress);
        rows[i++] = new PreviewRow(" \u00a77" + builderName(players), "");
        rows[i++] = new PreviewRow("", "");
        for (int p = 0; p < players.length; p++) {
            PreviewPlayer player = players[p];
            rows[i++] = new PreviewRow(
                    playerLeft(player, places[p], showPlaces, showTitles, showEmblems, false),
                    playerRight(player, showPointsGained), player);
        }
        rows[i++] = new PreviewRow("", "");
        rows[i++] = new PreviewRow("\u00a7f" + labelWithTrailingSpace(LangPatterns.scoreboardTimeLabel(lang)) + "\u00a7a00:13", "");
        rows[i++] = new PreviewRow("", "");
        rows[i++] = new PreviewRow("\u00a7f" + LangPatterns.scoreboardThemeLabel(lang), "");
        rows[i++] = new PreviewRow(" \u00a7a" + localizedPreviewTheme(), "");
        rows[i++] = new PreviewRow(PREVIEW_FOOTER, "");
        return rows;
    }

    private static PreviewRow[] defaultMetadataRows(PreviewPlayer[] players) {
        boolean showBuilders = GtbStructures.sbConfig.defaultExplicitlyShowBuilders;
        PreviewRow[] rows = new PreviewRow[showBuilders ? 3 : 2];
        int i = 0;
        rows[i++] = new PreviewRow("\u00a77Round",
                CustomScoreboard.roundProgressForScoreboard(10, 10, false, false));
        if (showBuilders) {
            rows[i++] = new PreviewRow("\u00a77Builder", builderNameWithColor(players));
        }
        rows[i] = new PreviewRow("\u00a77Time Left", "\u00a7a0:13");
        return rows;
    }

    private static void drawScoreboardPlayerRow(FontRenderer fr, PreviewPlayer player,
                                                String left, String right,
                                                int bgLeft, int textLeft, int rightCellRight,
                                                int rowY, int contentWidth,
                                                boolean shadow, boolean lineHeightTen) {
        int rowHeight = lineHeightTen ? ScoreboardLayout.LINE_HEIGHT : fr.FONT_HEIGHT;
        boolean hasStatusIcon = player.statusIcon >= 0;
        int iconY = rowY + (rowHeight - SCOREBOARD_ICON_SIZE) / 2 - 2;
        if (!hasStatusIcon && player.currentBuilder) {
            CustomScoreboard.drawRowHighlight(bgLeft, rowY, rightCellRight, rowHeight, false, true);
            CustomScoreboard.drawRowMarker(bgLeft, rowY, rowHeight, -1, true);
        }
        int buildIcon = player.buildIcon;
        int buildBgIcon = BuilderIcons.backgroundForBuildIcon(buildIcon);
        int iconX = bgLeft - SCOREBOARD_ICON_OUTSIDE_GAP - SCOREBOARD_ICON_SIZE;
        BuilderIcons.draw(buildBgIcon, iconX, iconY, SCOREBOARD_ICON_SIZE,
                BuilderIcons.tintFor(BuilderIcons.IDX_BUILD_BG), 0.5f);
        if (buildIcon >= 0) {
            BuilderIcons.draw(buildIcon, iconX, iconY, SCOREBOARD_ICON_SIZE,
                    BuilderIcons.tintFor(buildIcon), 0.5f);
        }

        int textX = textLeft;
        if (hasStatusIcon) textX += SCOREBOARD_ICON_SIZE + SCOREBOARD_INLINE_ICON_GAP;
        drawString(fr, left, textX, rowY, shadow);
        int rw = fr.getStringWidth(right);
        drawString(fr, right, textLeft + contentWidth - rw, rowY, shadow);
        if (hasStatusIcon) {
            CustomScoreboard.drawRowHighlight(bgLeft, rowY, rightCellRight, rowHeight, true, false);
            CustomScoreboard.drawRowMarker(bgLeft, rowY, rowHeight, player.statusIcon, false);
            BuilderIcons.draw(player.statusIcon, textLeft, iconY, SCOREBOARD_ICON_SIZE,
                    BuilderIcons.tintFor(player.statusIcon), 0.5f);
        }
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

    private static String playerLeft(PreviewPlayer player, int place, boolean showPlace,
                                     boolean showRank, boolean showEmblem,
                                     boolean indentBuilder) {
        String placeText = showPlace ? CustomScoreboard.placePrefix(place) : "";
        String rank = showRank ? bbRankPrefix(player) : "";
        String emblem = showEmblem && player.emblem != null && !player.emblem.isEmpty()
                ? " " + player.emblem : "";
        String indent = indentBuilder && player.currentBuilder ? " " : "";
        return placeText + rank + player.nameColor + indent + player.name + emblem;
    }

    private static String playerRight(PreviewPlayer player, boolean showDelta) {
        String total = "\u00a7a" + player.total;
        if (player.lowerBound >= 0) {
            total += " \u00a77(\u00a72" + player.lowerBound + "\u00a77)";
        }
        return player.delta <= 0 || !showDelta ? total : tierColor(player.delta) + "+" + player.delta + " " + total;
    }

    private static String bbRankPrefix(PreviewPlayer player) {
        String title = LangPatterns.bbTitleForRankIndex(player.bbRankIndex, previewLangCode());
        if (title == null || title.isEmpty()) return "";
        return LangPatterns.bbColorForRankIndex(player.bbRankIndex) + title + "\u00a7r ";
    }

    private static String builderName(PreviewPlayer[] players) {
        for (PreviewPlayer player : players) {
            if (player.currentBuilder) return player.name;
        }
        return "Player143";
    }

    private static String builderNameWithColor(PreviewPlayer[] players) {
        for (PreviewPlayer player : players) {
            if (player.currentBuilder) return player.nameColor + player.name;
        }
        return "\u00a77Player143";
    }

    static String previewLangCodeForTest(String current, String fallback) {
        if (current != null && !current.trim().isEmpty()) return current.trim();
        if (fallback != null && !fallback.trim().isEmpty()) return fallback.trim();
        return "en";
    }

    public static boolean previewActionbarVisibleForMaster(boolean modEnabled, boolean configuredVisible) {
        return !modEnabled || configuredVisible;
    }

    private static String previewLangCode() {
        return previewLangCodeForTest(GameStats.INSTANCE.getCurrentLangCode(), GtbStructures.lastLangCode);
    }

    static String localizedPreviewThemeForTest(String langCode) {
        return localizedPreviewTheme(langCode);
    }

    static String previewActionbarTextForTest(String langCode) {
        return previewActionbarText(langCode);
    }

    static String hypixelPreviewLineForTest(String langCode, int index) {
        return hypixelPreviewLineForTest(langCode, index, false);
    }

    static String hypixelPreviewLineForTest(String langCode, int index, boolean showRanks) {
        PreviewRow[] rows = hypixelRows(langCode, showRanks);
        return index >= 0 && index < rows.length ? rows[index].left : "";
    }

    static String hypixelPreviewRightForTest(String langCode, int index) {
        return hypixelPreviewRightForTest(langCode, index, false);
    }

    static String hypixelPreviewRightForTest(String langCode, int index, boolean expanded) {
        PreviewRow[] rows = hypixelRows(langCode, expanded);
        return index >= 0 && index < rows.length ? rows[index].right : "";
    }

    static int hypixelPreviewRowCountForTest(String langCode, boolean expanded) {
        return hypixelRows(langCode, expanded).length;
    }

    static int defaultPreviewMetadataRowCountForTest() {
        return defaultMetadataRows(previewPlayers()).length;
    }

    static String defaultPreviewMetadataLeftForTest(int index) {
        PreviewRow[] rows = defaultMetadataRows(previewPlayers());
        return index >= 0 && index < rows.length ? rows[index].left : "";
    }

    static String defaultPreviewMetadataRightForTest(int index) {
        PreviewRow[] rows = defaultMetadataRows(previewPlayers());
        return index >= 0 && index < rows.length ? rows[index].right : "";
    }

    static String previewHintStructureForTest(String langCode) {
        return previewHintStructure(langCode);
    }

    static int cooldownBarWidthForTest() {
        return COOLDOWN_BAR_WIDTH;
    }

    private static String previewActionbarText(String langCode) {
        LangPatterns.ThemeRevealPattern reveal = LangPatterns.themeRevealPatternForLang(langCode);
        String suffix = reveal.suffix == null ? "" : reveal.suffix;
        return "\u00a7b" + reveal.prefix + "\u00a7e" + localizedPreviewTheme(langCode)
                + (suffix.isEmpty() ? "" : "\u00a7b" + suffix);
    }

    private static String previewHintStructure(String langCode) {
        HintOverlay.ParsedHint parsed = HintOverlay.parsePreview(previewActionbarText(langCode));
        return parsed.isHint ? parsed.structure : lengthBucket(localizedPreviewTheme(langCode));
    }

    private static String localizedPreviewTheme() {
        return localizedPreviewTheme(previewLangCode());
    }

    private static String localizedPreviewTheme(String langCode) {
        TranslationData.ensureLocalLoaded();
        String displayed = TranslationData.getDisplayedTheme(langCode, PREVIEW_THEME);
        return displayed == null || displayed.trim().isEmpty() ? PREVIEW_THEME : displayed.trim();
    }

    private static String labelWithTrailingSpace(String label) {
        if (label == null || label.isEmpty()) return "";
        return label.endsWith(" ") ? label : label + " ";
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

    private static void drawPair(FontRenderer fr, String left, String right,
                                 int x, int y, int width, boolean shadow) {
        drawString(fr, left, x, y, shadow);
        drawString(fr, right, x + width - fr.getStringWidth(right), y, shadow);
    }

    private static int measurePair(FontRenderer fr, String left, String right) {
        return fr.getStringWidth(left) + fr.getStringWidth(right);
    }

    private static void drawString(FontRenderer fr, String text, int x, int y, boolean shadow) {
        if (shadow) {
            fr.drawStringWithShadow(text, x, y, 0xFFFFFF);
        } else {
            fr.drawString(text, x, y, 0xFFFFFF);
        }
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

    private static void drawTextBackground(Minecraft mc, int x, int y, int width) {
        Gui.drawRect(x - 2, y - 1, x + width + 2, y + mc.fontRendererObj.FONT_HEIGHT + 1,
                ScoreboardLayout.BACKGROUND_COLOR);
    }

    private static final class PreviewPlayer {
        final String name;
        final String nameColor;
        final String emblem;
        final int delta;
        final int total;
        final int lowerBound;
        final boolean currentBuilder;
        final int buildIcon;
        final int statusIcon;
        final int bbRankIndex;

        PreviewPlayer(String name, String nameColor, String emblem, int delta, int total,
                      int lowerBound, boolean currentBuilder, boolean built,
                      boolean inactive, int bbRankIndex) {
            this.name = name;
            this.nameColor = nameColor;
            this.emblem = emblem;
            this.delta = delta;
            this.total = total;
            this.lowerBound = lowerBound;
            this.currentBuilder = currentBuilder;
            this.buildIcon = currentBuilder
                    ? BuilderIcons.IDX_SPINNER_0 + BuilderIcons.getSpinnerFrame()
                    : built ? BuilderIcons.IDX_CHECK : -1;
            this.statusIcon = inactive ? BuilderIcons.IDX_INACTIVE : -1;
            this.bbRankIndex = bbRankIndex;
        }
    }

    private static final class PreviewRow {
        final String left;
        final String right;
        final PreviewPlayer player;

        PreviewRow(String left, String right) {
            this(left, right, null);
        }

        PreviewRow(String left, String right, PreviewPlayer player) {
            this.left = left == null ? "" : left;
            this.right = right == null ? "" : right;
            this.player = player;
        }
    }

    private static final class PreviewScreen extends GuiScreen {
        @Override
        public boolean doesGuiPauseGame() {
            return false;
        }

        @Override
        public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            PreviewLayoutEditor.INSTANCE.drawScreen(Minecraft.getMinecraft(), mouseX, mouseY);
        }

        @Override
        public void handleMouseInput() throws IOException {
            super.handleMouseInput();
            int wheel = Mouse.getEventDWheel();
            if (wheel == 0) return;
            int mouseX = Mouse.getEventX() * width / mc.displayWidth;
            int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;
            PreviewLayoutEditor.INSTANCE.onMouseWheel(new ScaledResolution(mc), wheel, mouseX, mouseY);
        }

        @Override
        protected void keyTyped(char typedChar, int keyCode) throws IOException {
            if (PreviewLayoutEditor.INSTANCE.onKeyTyped(typedChar, keyCode)) return;
            if (keyCode == Keyboard.KEY_ESCAPE) {
                InterfacePreview.INSTANCE.disableSilently();
                return;
            }
            super.keyTyped(typedChar, keyCode);
        }
    }
}
