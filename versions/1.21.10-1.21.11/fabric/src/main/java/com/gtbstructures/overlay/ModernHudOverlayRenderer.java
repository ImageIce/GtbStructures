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
import com.gtbstructures.util.RuntimeReflectionCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

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
        actionbarBackgroundLayerRegistered = FabricHudElementBridge.registerBeforeVanilla(
                "OVERLAY_MESSAGE", "modern_hud_actionbar_background",
                ModernHudOverlayRenderer::renderActionbarBackground);
        if (FabricHudElementBridge.registerBeforeChat("modern_hud", ModernHudOverlayRenderer::renderHud)) {
            return;
        }
        try {
            Class<?> callbackClass = Class.forName(
                    "net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback");
            Object event = callbackClass.getField("EVENT").get(null);
            Object callback = java.lang.reflect.Proxy.newProxyInstance(
                    ModernHudOverlayRenderer.class.getClassLoader(),
                    new Class<?>[] {callbackClass},
                    (proxy, method, args) -> {
                        if (method.getName().equals("onHudRender") && args != null && args.length >= 1) {
                            renderHud(args[0]);
                            return null;
                        }
                        if (method.getName().equals("toString")) return "GtbStructures modern HUD overlay";
                        return null;
                    });
            Method register = event.getClass().getMethod("register", Object.class);
            register.setAccessible(true);
            register.invoke(event, callback);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.error("Unable to register GtbStructures Fabric modern HUD overlay", e);
        }
    }

    private static void renderActionbarBackground(Object graphics) {
        try {
            if (InterfacePreview.isActive()) return;
            GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
            long now = System.currentTimeMillis();
            GtbGame.State state = currentState();
            String actionbarHint = ModernRuntimeBridge.currentActionbarHintText(now);
            if (!settings.modEnabled || !settings.hintBackgroundOverlay
                    || !isActiveRound(state) || actionbarHint.isEmpty()) return;
            Object mc = minecraftClient();
            Object font = fieldAny(mc, "textRenderer", "font", "field_1772");
            Object window = invokeAny(mc, "getWindow", "method_22683");
            if (graphics == null || font == null || window == null) return;
            int screenWidth = intValue(invokeAny(window, "getScaledWidth", "getGuiScaledWidth", "method_4486"));
            int screenHeight = intValue(invokeAny(window, "getScaledHeight", "getGuiScaledHeight", "method_4502"));
            renderActionbar(graphics, font, actionbarHint, screenWidth, screenHeight, true, false);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Unable to render GtbStructures Fabric actionbar background", e);
        }
    }

    private static void renderHud(Object graphics) {
        try {
            GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
            boolean preview = InterfacePreview.isActive();
            if (!preview && (!settings.modEnabled
                    || !GameStats.INSTANCE.shouldRenderRoundOverlays())) return;
            Object mc = minecraftClient();
            Object font = fieldAny(mc, "textRenderer", "font", "field_1772");
            Object window = invokeAny(mc, "getWindow", "method_22683");
            if (graphics == null || font == null || window == null) return;
            int screenWidth = intValue(invokeAny(window, "getScaledWidth", "getGuiScaledWidth", "method_4486"));
            int screenHeight = intValue(invokeAny(window, "getScaledHeight", "getGuiScaledHeight", "method_4502"));
            if (preview) {
                renderPreview(graphics, font, screenWidth, screenHeight, settings);
            } else {
                renderLive(graphics, font, screenWidth, screenHeight, settings, mc);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Unable to render GtbStructures Fabric modern HUD overlay", e);
        }
    }

    private static void renderPreview(Object graphics, Object font, int screenWidth, int screenHeight,
                                      GtbStructuresSettings settings) throws ReflectiveOperationException {
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
                3000L, true);
    }

    private static void renderLive(Object graphics, Object font, int screenWidth, int screenHeight,
                                   GtbStructuresSettings settings, Object mc)
            throws ReflectiveOperationException {
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

        boolean cooldownEnabled = settings.chatCooldownCounter
                && GameStats.INSTANCE.isTrackingEnabled();
        if (cooldownEnabled && ModernChatCooldownCounter.remainingMs(now) > 0L
                && ModernChatCooldownCounter.shouldRender(true,
                false, state, builderName(), localPlayerName(mc), now)) {
            renderCooldown(graphics, font, screenWidth, screenHeight, settings,
                    ModernChatCooldownCounter.remainingMs(now), false);
        }
    }

    private static void renderActionbar(Object graphics, Object font, String text,
                                        int screenWidth, int screenHeight,
                                        boolean background, boolean preview)
            throws ReflectiveOperationException {
        if (text == null || text.isEmpty()) return;
        int fontHeight = intField(font, 9, "fontHeight", "lineHeight", "field_2000");
        int width = textWidth(font, text);
        int x = (screenWidth - width) / 2;
        int y = screenHeight - ACTIONBAR_Y_OFFSET;
        if (background) {
            fill(graphics, x - TIMER_BG_PAD_X, y - TIMER_BG_PAD_Y,
                    x + width + TIMER_BG_PAD_X, y + fontHeight + TIMER_BG_PAD_Y,
                    ScoreboardLayout.BACKGROUND_COLOR);
        }
        if (preview) {
            PreviewLayoutEditor.registerWithBackground("actionbar", x, y, width, fontHeight,
                    PreviewLayoutEditor.ACTIONBAR,
                    (left, top, right, bottom, color) -> fill(graphics, left, top, right, bottom, color));
            drawText(graphics, font, text, x, y, 0xFFFFFF);
        }
    }

    private static void renderHintStructure(Object graphics, Object font, int screenWidth, int screenHeight,
                                            GtbStructuresSettings settings, String structure,
                                            int alpha, boolean preview)
            throws ReflectiveOperationException {
        if (structure == null || structure.isEmpty() || alpha <= 0) return;
        int fontHeight = intField(font, 9, "fontHeight", "lineHeight", "field_2000");
        String display = ModernRuntimeBridge.formatHintStructure(structure,
                settings.colorBrackets, settings.colorNumbers, settings.colorHyphens);
        int width = textWidth(font, display);
        int x = HudPosition.left(settings.posX, screenWidth, width, MARGIN);
        int y = HudPosition.top(settings.posY, screenHeight, fontHeight, MARGIN);
        if ((preview || isActiveRound(currentState())) && settings.hintStructureBackground) {
            fill(graphics, x - TIMER_BG_PAD_X, y - TIMER_BG_PAD_Y,
                    x + width + TIMER_BG_PAD_X, y + fontHeight + TIMER_BG_PAD_Y,
                    alphaColor(ScoreboardLayout.BACKGROUND_COLOR, alpha));
        }
        if (preview) {
            PreviewLayoutEditor.registerWithBackground("hint", x, y, width, fontHeight,
                    PreviewLayoutEditor.HINT_STRUCTURE,
                    (left, top, right, bottom, color) -> fill(graphics, left, top, right, bottom, color));
        }
        drawText(graphics, font, display, x, y, alphaColor(0xFFFFFF, alpha));
    }

    private static void renderCooldown(Object graphics, Object font, int screenWidth, int screenHeight,
                                       GtbStructuresSettings settings, long remaining, boolean preview)
            throws ReflectiveOperationException {
        if (!settings.chatCooldownCounter) return;
        boolean showTimer = settings.chatCooldownTimerEnabled;
        boolean showBar = settings.chatCooldownBarEnabled;
        if (!showTimer && !showBar) return;

        int fontHeight = intField(font, 9, "fontHeight", "lineHeight", "field_2000");
        String label = MinecraftFormatting.normalizeMinecraftColorCode(settings.chatCooldownTimerColor, "\u00a7f")
                + ChatCooldownLogic.displayRemaining(remaining);
        int labelWidth = showTimer ? textWidth(font, label) : 0;
        int barWidth = ChatCooldownLogic.barWidth();
        int widgetWidth = Math.max(showBar ? barWidth : 0, labelWidth);
        int widgetHeight = (showTimer ? fontHeight : 0)
                + (showTimer && showBar ? TIMER_BAR_GAP : 0)
                + (showBar ? BAR_HEIGHT : 0);
        int widgetLeft = HudPosition.left(settings.chatCooldownPosX, screenWidth, widgetWidth, MARGIN);
        int widgetTop = HudPosition.top(settings.chatCooldownPosY, screenHeight, widgetHeight, MARGIN);
        int centerX = widgetLeft + widgetWidth / 2;
        int labelX = centerX - labelWidth / 2;
        int labelY = widgetTop;
        int barX = centerX - barWidth / 2;
        int barY = widgetTop + (showTimer ? fontHeight + TIMER_BAR_GAP : 0);

        if (showTimer && settings.chatCooldownBackground) {
            fill(graphics, labelX - TIMER_BG_PAD_X, labelY - TIMER_BG_PAD_Y,
                    labelX + labelWidth + TIMER_BG_PAD_X,
                    labelY + fontHeight + TIMER_BG_PAD_Y,
                    ScoreboardLayout.BACKGROUND_COLOR);
        }
        if (showBar) {
            if (settings.chatCooldownBarBackground) {
                fill(graphics, barX - 1, barY - 1, barX + barWidth + 1, barY + BAR_HEIGHT + 1,
                        ScoreboardLayout.BACKGROUND_COLOR);
                fill(graphics, barX, barY, barX + barWidth, barY + BAR_HEIGHT, 0x66000000);
            }
        }
        if (preview) {
            PreviewLayoutEditor.registerWithBackground("cooldown", widgetLeft, widgetTop, widgetWidth, widgetHeight,
                    PreviewLayoutEditor.COOLDOWN,
                    (left, top, right, bottom, color) -> fill(graphics, left, top, right, bottom, color));
        }
        if (showBar) {
            int fill = ChatCooldownLogic.fillWidth(barWidth, remaining);
            fill(graphics, barX, barY, barX + fill, barY + BAR_HEIGHT,
                    MinecraftFormatting.minecraftColorArgb(settings.chatCooldownBarColor, 0xCC));
        }
        if (showTimer) {
            drawText(graphics, font, label, labelX, labelY, 0xFFFFFF);
        }
    }

    private static void fill(Object graphics, int left, int top, int right, int bottom, int color)
            throws ReflectiveOperationException {
        invokeAnyArgs(graphics, new String[] {"fill", "method_25294"}, left, top, right, bottom, color);
    }

    private static void drawText(Object graphics, Object font, String text, int x, int y, int color)
            throws ReflectiveOperationException {
        invokeAnyArgs(graphics, new String[] {"drawText", "drawString", "text", "method_51433"},
                font, text, x, y, opaqueColor(color), true);
    }

    private static int opaqueColor(int color) {
        return (color & 0xFF000000) == 0 ? 0xFF000000 | color : color;
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

    private static String localPlayerName(Object mc) {
        try {
            Object player = fieldAny(mc, "player", "field_1724");
            if (player == null) return null;
            Object profile = invokeAny(player, "getGameProfile", "method_7334");
            if (profile == null) return null;
            Object name = invokeAny(profile, "name", "getName");
            return name instanceof String ? (String) name : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
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

    private static Object invokeAnyWithArg(Object target, Object arg, String... names)
            throws ReflectiveOperationException {
        return RuntimeReflectionCache.invokeAnyWithArg(target, arg, names);
    }

    private static Object invokeAnyArgs(Object target, String[] names, Object... args)
            throws ReflectiveOperationException {
        return RuntimeReflectionCache.invokeAnyArgs(target, names, args);
    }

    private static int intField(Object target, int fallback, String... names) {
        try {
            Object value = fieldAny(target, names);
            return value instanceof Number ? ((Number) value).intValue() : fallback;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return fallback;
        }
    }

    private static int textWidth(Object font, String text) throws ReflectiveOperationException {
        return intValue(invokeAnyWithArg(font, text, "getWidth", "width", "method_1727"));
    }

    private static int intValue(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private static int alphaColor(int color, int alpha) {
        int baseAlpha = (color >>> 24) & 0xFF;
        if (baseAlpha == 0) baseAlpha = 0xFF;
        int outAlpha = (baseAlpha * (alpha & 0xFF) + 127) / 255;
        return (outAlpha << 24) | (color & 0x00FFFFFF);
    }
}
