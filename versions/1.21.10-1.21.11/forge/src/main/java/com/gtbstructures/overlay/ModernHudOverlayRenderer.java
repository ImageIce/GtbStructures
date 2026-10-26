package com.gtbstructures.overlay;

import com.gtbstructures.chat.ChatCooldownLogic;
import com.gtbstructures.chat.ModernChatCooldownCounter;
import com.gtbstructures.GtbStructuresSettings;
import com.gtbstructures.ModernRuntimeBridge;
import com.gtbstructures.scoreboard.GameTracker;
import com.gtbstructures.scoreboard.GtbGame;
import com.gtbstructures.scoreboard.InterfacePreview;
import com.gtbstructures.scoreboard.PreviewLayoutEditor;
import com.gtbstructures.scoreboard.ScoreboardLayout;
import com.gtbstructures.stats.GameStats;
import com.gtbstructures.util.MinecraftFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ModernHudOverlayRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModernHudOverlayRenderer.class);
    private static final int MARGIN = 2;
    private static final int TIMER_BG_PAD_X = 2;
    private static final int TIMER_BG_PAD_Y = 1;
    private static final int BAR_HEIGHT = 4;
    private static final int TIMER_BAR_GAP = 3;
    private static final int ACTIONBAR_Y_OFFSET = 72;
    private static boolean actionbarBackgroundLayerRegistered;

    private ModernHudOverlayRenderer() {
    }

    public static void register() {
        AddGuiOverlayLayersEvent.BUS.addListener(ModernHudOverlayRenderer::addLayer);
    }

    private static void addLayer(AddGuiOverlayLayersEvent event) {
        try {
            ForgeHudLayerBridge.addBelowHotbarMessage(event.getLayeredDraw(), "modern_hud_actionbar_background",
                    ModernHudOverlayRenderer::renderActionbarBackground);
            actionbarBackgroundLayerRegistered = true;
        } catch (RuntimeException e) {
            actionbarBackgroundLayerRegistered = false;
            LOGGER.warn("Unable to register GtbStructures actionbar background below vanilla hotbar message", e);
        }
        ForgeHudLayerBridge.addAboveChat(event.getLayeredDraw(), "modern_hud",
                ModernHudOverlayRenderer::render);
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        try {
            renderHud(graphics);
        } catch (RuntimeException e) {
            LOGGER.warn("Unable to render GtbStructures modern HUD overlay", e);
        }
    }

    private static void renderActionbarBackground(GuiGraphics graphics, DeltaTracker deltaTracker) {
        try {
            renderActionbarBackground(graphics);
        } catch (RuntimeException e) {
            LOGGER.warn("Unable to render GtbStructures actionbar background", e);
        }
    }

    private static void renderActionbarBackground(GuiGraphics graphics) {
        if (InterfacePreview.isActive()) return;
        if (!GtbStructuresSettings.INSTANCE.modEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null || mc.getWindow() == null) return;
        GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
        long now = System.currentTimeMillis();
        GtbGame.State state = currentState();
        String actionbarHint = ModernRuntimeBridge.currentActionbarHintText(now);
        if (settings.hintBackgroundOverlay && isActiveRound(state) && !actionbarHint.isEmpty()) {
            renderActionbar(graphics, mc.font,
                    actionbarHint, mc.getWindow().getGuiScaledWidth(),
                    mc.getWindow().getGuiScaledHeight(), true, false);
        }
    }

    private static void renderHud(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null || mc.getWindow() == null) return;
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
        if (InterfacePreview.isActive()) {
            renderPreview(graphics, mc.font, screenWidth, screenHeight, settings);
        } else {
            renderLive(graphics, mc.font, screenWidth, screenHeight, settings);
        }
    }

    private static void renderPreview(GuiGraphics graphics, Font font, int screenWidth, int screenHeight,
                                      GtbStructuresSettings settings) {
        String lang = InterfacePreview.previewLangCode();
        if (!settings.modEnabled) {
            renderActionbar(graphics, font, InterfacePreview.previewActionbarText(lang),
                    screenWidth, screenHeight, false, true);
            return;
        }
        renderActionbar(graphics, font, InterfacePreview.previewActionbarText(lang),
                screenWidth, screenHeight, settings.hintBackgroundOverlay, true);
        if (settings.enabled) {
            renderHintStructure(graphics, font, screenWidth, screenHeight, settings,
                    InterfacePreview.previewHintStructure(lang), 255, true);
        }
        renderCooldown(graphics, font, screenWidth, screenHeight, settings,
                ModernChatCooldownCounter.previewRemainingMs(System.currentTimeMillis()), true);
    }

    private static void renderLive(GuiGraphics graphics, Font font, int screenWidth, int screenHeight,
                                   GtbStructuresSettings settings) {
        if (!settings.modEnabled) return;
        if (!GameStats.INSTANCE.shouldRenderRoundOverlays()) return;
        long now = System.currentTimeMillis();
        GtbGame.State state = currentState();
        String actionbarHint = ModernRuntimeBridge.currentActionbarHintText(now);
        if (!actionbarBackgroundLayerRegistered
                && settings.hintBackgroundOverlay && isActiveRound(state) && !actionbarHint.isEmpty()) {
            renderActionbar(graphics, font, actionbarHint, screenWidth, screenHeight, true, false);
        }

        String structure = ModernRuntimeBridge.currentHintStructure(now);
        if (settings.enabled && isActiveRound(state) && !structure.isEmpty()) {
            renderHintStructure(graphics, font, screenWidth, screenHeight, settings,
                    structure, ModernRuntimeBridge.currentHintAlpha(now), false);
        }

        if (ModernChatCooldownCounter.shouldRender(settings.chatCooldownCounter
                        && GameStats.INSTANCE.isTrackingEnabled(),
                false, state, builderName(), localPlayerName(), now)) {
            renderCooldown(graphics, font, screenWidth, screenHeight, settings,
                    ModernChatCooldownCounter.remainingMs(now), false);
        }
    }

    private static void renderActionbar(GuiGraphics graphics, Font font, String text,
                                        int screenWidth, int screenHeight,
                                        boolean background, boolean preview) {
        if (text == null || text.isEmpty()) return;
        int width = font.width(text);
        int x = (screenWidth - width) / 2;
        int y = screenHeight - ACTIONBAR_Y_OFFSET;
        if (preview) {
            PreviewLayoutEditor.registerWithBackground("actionbar", x, y, width, font.lineHeight,
                    PreviewLayoutEditor.ACTIONBAR,
                    (left, top, right, bottom, color) ->
                            graphics.fill(left, top, right, bottom, color));
        }
        if (background) {
            graphics.fill(x - TIMER_BG_PAD_X, y - TIMER_BG_PAD_Y,
                    x + width + TIMER_BG_PAD_X, y + font.lineHeight + TIMER_BG_PAD_Y,
                    ScoreboardLayout.BACKGROUND_COLOR);
        }
        if (preview) {
            graphics.drawString(font, text, x, y, opaqueColor(0xFFFFFF), true);
        }
    }

    private static void renderHintStructure(GuiGraphics graphics, Font font, int screenWidth, int screenHeight,
                                            GtbStructuresSettings settings, String structure,
                                            int alpha, boolean preview) {
        if (structure == null || structure.isEmpty() || alpha <= 0) return;
        String display = ModernRuntimeBridge.formatHintStructure(structure,
                settings.colorBrackets, settings.colorNumbers, settings.colorHyphens);
        int width = font.width(display);
        float scale = HudPanelScale.normalize(settings.hintStructureScale);
        int scaledWidth = HudPanelScale.scaledPixels(width, scale);
        int scaledHeight = HudPanelScale.scaledPixels(font.lineHeight, scale);
        int x = HudPosition.left(settings.posX, screenWidth, scaledWidth, MARGIN);
        int y = HudPosition.top(settings.posY, screenHeight, scaledHeight, MARGIN);
        if (preview) {
            PreviewLayoutEditor.registerWithBackground("hint", x, y, scaledWidth, scaledHeight,
                    PreviewLayoutEditor.HINT_STRUCTURE,
                    (left, top, right, bottom, color) ->
                            graphics.fill(left, top, right, bottom, color));
        }
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(x, y);
            graphics.pose().scale(scale, scale);
            if ((preview || isActiveRound(currentState())) && settings.hintStructureBackground) {
                graphics.fill(-TIMER_BG_PAD_X, -TIMER_BG_PAD_Y,
                        width + TIMER_BG_PAD_X, font.lineHeight + TIMER_BG_PAD_Y,
                        alphaColor(ScoreboardLayout.BACKGROUND_COLOR, alpha));
            }
            graphics.drawString(font, display, 0, 0, opaqueColor(alphaColor(0xFFFFFF, alpha)), true);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    private static void renderCooldown(GuiGraphics graphics, Font font, int screenWidth, int screenHeight,
                                       GtbStructuresSettings settings, long remaining, boolean preview) {
        if (!settings.chatCooldownCounter) return;
        boolean showTimer = settings.chatCooldownTimerEnabled;
        boolean showBar = settings.chatCooldownBarEnabled;
        String previewText = preview
                ? InterfacePreview.cooldownPreviewText(showTimer, showBar, settings.chatCooldownSfxEnabled, remaining)
                : showTimer ? ChatCooldownLogic.displayRemaining(remaining) : "";
        boolean showLabel = !previewText.isEmpty();
        if (!showLabel && !showBar) return;

        String label = MinecraftFormatting.normalizeMinecraftColorCode(settings.chatCooldownTimerColor, "\u00a7f")
                + previewText;
        int labelWidth = showLabel ? font.width(label) : 0;
        int barWidth = ChatCooldownLogic.barWidth();
        int widgetWidth = Math.max(showBar ? barWidth : 0, labelWidth);
        int widgetHeight = (showLabel ? font.lineHeight : 0)
                + (showLabel && showBar ? TIMER_BAR_GAP : 0)
                + (showBar ? BAR_HEIGHT : 0);
        float scale = HudPanelScale.normalize(settings.chatCooldownScale);
        int scaledWidgetWidth = HudPanelScale.scaledPixels(widgetWidth, scale);
        int scaledWidgetHeight = HudPanelScale.scaledPixels(widgetHeight, scale);
        int widgetLeft = HudPosition.left(settings.chatCooldownPosX, screenWidth, scaledWidgetWidth, MARGIN);
        int widgetTop = HudPosition.top(settings.chatCooldownPosY, screenHeight, scaledWidgetHeight, MARGIN);
        int centerX = widgetWidth / 2;
        int labelX = centerX - labelWidth / 2;
        int labelY = 0;
        int barX = centerX - barWidth / 2;
        int barY = showLabel ? font.lineHeight + TIMER_BAR_GAP : 0;
        if (preview) {
            PreviewLayoutEditor.registerWithBackground("cooldown", widgetLeft, widgetTop,
                    scaledWidgetWidth, scaledWidgetHeight, PreviewLayoutEditor.COOLDOWN,
                    (left, top, right, bottom, color) ->
                            graphics.fill(left, top, right, bottom, color));
        }
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(widgetLeft, widgetTop);
            graphics.pose().scale(scale, scale);
            if (showTimer && settings.chatCooldownBackground) {
                graphics.fill(labelX - TIMER_BG_PAD_X, labelY - TIMER_BG_PAD_Y,
                        labelX + labelWidth + TIMER_BG_PAD_X,
                        labelY + font.lineHeight + TIMER_BG_PAD_Y,
                        ScoreboardLayout.BACKGROUND_COLOR);
            }
            if (showBar && settings.chatCooldownBarBackground) {
                graphics.fill(barX - 1, barY - 1, barX + barWidth + 1, barY + BAR_HEIGHT + 1,
                        ScoreboardLayout.BACKGROUND_COLOR);
                graphics.fill(barX, barY, barX + barWidth, barY + BAR_HEIGHT, 0x66000000);
            }
            if (showBar) {
                int fillWidth = preview ? InterfacePreview.cooldownPreviewFillWidth(remaining)
                        : ChatCooldownLogic.fillWidth(barWidth, remaining);
                graphics.fill(barX, barY, barX + fillWidth, barY + BAR_HEIGHT,
                        MinecraftFormatting.minecraftColorArgb(settings.chatCooldownBarColor, 0xCC));
            }
            if (showLabel) {
                graphics.drawString(font, label, labelX, labelY, opaqueColor(0xFFFFFF), true);
            }
        } finally {
            graphics.pose().popMatrix();
        }
    }

    private static boolean isActiveRound(GtbGame.State state) {
        return state == GtbGame.State.ROUND_PRE
                || state == GtbGame.State.ROUND_BUILD
                || state == GtbGame.State.ROUND_END;
    }

    private static GtbGame.State currentState() {
        GtbGame game = GameTracker.INSTANCE.game;
        return game == null ? GtbGame.State.NONE : game.state;
    }

    private static String builderName() {
        GtbGame game = GameTracker.INSTANCE.game;
        return game == null || game.currentBuilder == null ? null : game.currentBuilder.name;
    }

    private static String localPlayerName() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? null : mc.player.getGameProfile().name();
    }

    private static int alphaColor(int color, int alpha) {
        int baseAlpha = (color >>> 24) & 0xFF;
        if (baseAlpha == 0) baseAlpha = 0xFF;
        int outAlpha = (baseAlpha * (alpha & 0xFF) + 127) / 255;
        return (outAlpha << 24) | (color & 0x00FFFFFF);
    }

    private static int opaqueColor(int color) {
        return (color & 0xFF000000) == 0 ? 0xFF000000 | color : color;
    }
}
