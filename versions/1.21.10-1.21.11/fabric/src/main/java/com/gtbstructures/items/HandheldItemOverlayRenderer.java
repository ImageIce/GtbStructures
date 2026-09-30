package com.gtbstructures.items;

import com.gtbstructures.GtbStructuresSettings;
import com.gtbstructures.ModernRuntimeBridge;
import com.gtbstructures.diagnostics.RawPacketRecorder;
import com.gtbstructures.overlay.FabricHudPose;
import com.gtbstructures.overlay.FabricHudElementBridge;
import com.gtbstructures.overlay.HudPosition;
import com.gtbstructures.overlay.HudPanelScale;
import com.gtbstructures.scoreboard.InterfacePreview;
import com.gtbstructures.scoreboard.PreviewLayoutEditor;
import com.gtbstructures.scoreboard.ScoreboardLayout;
import com.gtbstructures.stats.GameStats;
import com.gtbstructures.util.MinecraftFormatting;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class HandheldItemOverlayRenderer {
    private static final Object REFLECTION_MISS = new Object();
    private static final Logger LOGGER = LoggerFactory.getLogger(HandheldItemOverlayRenderer.class);
    private static final int MARGIN = 2;
    private static final int PANEL_PAD = 2;
    private static final int CAPTION_LINE_HEIGHT = 8;
    private static final int HORIZONTAL_CELL_SIZE = HandheldItemTracker.ICON_SIZE + 2;
    private static final String CLASSIC_EGG_BASE =
            "gtbstructures:textures/item/spawn_egg/classic/spawn_egg.png";
    private static final String CLASSIC_EGG_OVERLAY =
            "gtbstructures:textures/item/spawn_egg/classic/spawn_egg_overlay.png";
    private static final Set<String> WARNED_ICON_FAILURES = ConcurrentHashMap.newKeySet();
    private static final Map<String, Object> ITEM_STACK_CACHE = new ConcurrentHashMap<>();
    private static final Set<String> UNRENDERABLE_ITEM_STACKS = ConcurrentHashMap.newKeySet();
    private static final Map<String, Object> IDENTIFIER_CACHE = new ConcurrentHashMap<>();
    private static final ClassValue<Map<Class<?>, Object>> TEXTURE_PIPELINE_TYPES =
            new ClassValue<Map<Class<?>, Object>>() {
                @Override protected Map<Class<?>, Object> computeValue(Class<?> graphicsType) {
                    return new ConcurrentHashMap<>();
                }
            };
    private static final Map<Class<?>, Object> TEXTURE_PIPELINES = new ConcurrentHashMap<>();
    private static final RuntimeReflectionCache.InvocationSite DRAW_ITEM_CALL =
            RuntimeReflectionCache.instanceSite("drawItem", "renderItem", "method_51427");
    private static final RuntimeReflectionCache.InvocationSite DRAW_ITEM_OVERLAY_CALL =
            RuntimeReflectionCache.instanceSite(
                    "drawStackOverlay", "renderItemDecorations", "method_51431");
    private static final RuntimeReflectionCache.InvocationSite DRAW_TEXTURE_CALL =
            RuntimeReflectionCache.instanceSite(
                    "drawTexture", "blit", "method_25293", "method_25302", "method_25291", "method_25290");
    private static final RuntimeReflectionCache.InvocationSite SHADER_COLOR_CALL =
            RuntimeReflectionCache.staticSite("setShaderColor");
    private static final RuntimeReflectionCache.InvocationSite FILL_CALL =
            RuntimeReflectionCache.instanceSite("fill", "method_25294");
    private static final RuntimeReflectionCache.InvocationSite DRAW_TEXT_CALL =
            RuntimeReflectionCache.instanceSite("drawText", "drawString", "text", "method_51433");
    private static final RuntimeReflectionCache.InvocationSite TEXT_WIDTH_CALL =
            RuntimeReflectionCache.instanceSite("getWidth", "width", "method_1727");
    private static boolean previewWasActive;
    private static String packetBuilder = "";
    private static int packetBuilderEntityId = -1;

    private HandheldItemOverlayRenderer() {
    }

    public static void register() {
        registerTick();
        registerRender();
    }

    private static void registerTick() {
        try {
            Class<?> callbackClass = Class.forName(
                    "net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents$EndTick");
            Object event = Class.forName(
                    "net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents")
                    .getField("END_CLIENT_TICK").get(null);
            Object callback = java.lang.reflect.Proxy.newProxyInstance(
                    HandheldItemOverlayRenderer.class.getClassLoader(),
                    new Class<?>[] {callbackClass},
                    (proxy, method, args) -> {
                        if (method.getName().equals("onEndTick") && args != null && args.length >= 1) {
                            onClientTick(args[0]);
                            return null;
                        }
                        if (method.getName().equals("toString")) return "GtbStructures handheld item tick";
                        return null;
                    });
            Method register = event.getClass().getMethod("register", Object.class);
            register.setAccessible(true);
            register.invoke(event, callback);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.error("Unable to register GtbStructures Fabric handheld item tick", e);
        }
    }

    private static void registerRender() {
        if (FabricHudElementBridge.registerBeforeChat(
                "handheld_item_overlay", HandheldItemOverlayRenderer::renderOverlay)) {
            return;
        }
        try {
            Class<?> callbackClass = Class.forName(
                    "net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback");
            Object event = callbackClass.getField("EVENT").get(null);
            Object callback = java.lang.reflect.Proxy.newProxyInstance(
                    HandheldItemOverlayRenderer.class.getClassLoader(),
                    new Class<?>[] {callbackClass},
                    (proxy, method, args) -> {
                        if (method.getName().equals("onHudRender") && args != null && args.length >= 1) {
                            renderOverlay(args[0]);
                            return null;
                        }
                        if (method.getName().equals("toString")) return "GtbStructures handheld item overlay";
                        return null;
                    });
            Method register = event.getClass().getMethod("register", Object.class);
            register.setAccessible(true);
            register.invoke(event, callback);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.error("Unable to register GtbStructures Fabric handheld item overlay", e);
        }
    }

    private static void onClientTick(Object mc) {
        long now = System.currentTimeMillis();
        try {
            if (!GtbStructuresSettings.INSTANCE.modEnabled) {
                if (previewWasActive) HandheldItemTracker.INSTANCE.endPreview(now);
                previewWasActive = false;
                return;
            }
            if (InterfacePreview.isActive()) {
                HandheldItemTracker.ItemSnapshot observed = snapshotForLocalPlayer(mc);
                if (!previewWasActive) {
                    HandheldItemTracker.INSTANCE.beginPreview(observed == null ? "" : observed.key, now);
                    previewWasActive = true;
                }
                HandheldItemTracker.INSTANCE.tickPreview(observed, now);
            } else if (previewWasActive) {
                HandheldItemTracker.INSTANCE.endPreview(now);
                previewWasActive = false;
            }
            tickLive(mc, now);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Unable to tick GtbStructures Fabric handheld item overlay", e);
            HandheldItemTracker.INSTANCE.closeForTest(now);
        }
    }

    private static void tickLive(Object mc, long now) throws ReflectiveOperationException {
        GameStats stats = GameStats.INSTANCE;
        String builder = stats.getCurrentBuilder();
        HandheldItemTracker.ItemSnapshot snapshot = null;
        if (builder != null && !builder.trim().isEmpty()) {
            Object player = findPlayer(mc, builder);
            rememberBuilderEntity(builder, player);
            snapshot = snapshotFor(player);
        } else {
            rememberBuilderEntity(builder, null);
        }
        HandheldItemTracker.INSTANCE.tickRound(stats.getState(), stats.getCurrentRound(),
                builder, snapshot, now);
    }

    public static void onEquipmentPacket(Object packet) {
        if (!GtbStructuresSettings.INSTANCE.modEnabled) return;
        long now = System.currentTimeMillis();
        try {
            ModernEquipmentPacketReader.MainHandUpdate update =
                    ModernEquipmentPacketReader.readMainHand(packet);
            if (update == null) return;

            GameStats stats = GameStats.INSTANCE;
            String builder = stats.getCurrentBuilder();
            Object player = builder == null || builder.trim().isEmpty()
                    ? null : findPlayer(minecraftClient(), builder);
            rememberBuilderEntity(builder, player);
            boolean builderMatch = packetBuilderEntityId >= 0
                    && update.entityId == packetBuilderEntityId;
            HandheldItemTracker.ItemSnapshot snapshot = snapshotForStack(update.stack);
            RawPacketRecorder.INSTANCE.captureSummary("S04PacketEntityEquipment",
                    "{\"entityId\":" + update.entityId
                            + ",\"slot\":\"mainhand\""
                            + ",\"builder\":" + RawPacketRecorder.json(builder)
                            + ",\"builderEntityId\":"
                            + (packetBuilderEntityId < 0 ? "null" : String.valueOf(packetBuilderEntityId))
                            + ",\"builderMatch\":" + builderMatch
                            + ",\"empty\":" + (snapshot == null)
                            + ",\"registryName\":" + RawPacketRecorder.json(
                            snapshot == null ? null : snapshot.registryName)
                            + "}");
            if (!builderMatch) return;
            HandheldItemTracker.INSTANCE.tickRound(stats.getState(), stats.getCurrentRound(),
                    builder, snapshot, now);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Unable to capture GtbStructures Fabric equipment packet", e);
        }
    }

    private static void rememberBuilderEntity(String builder, Object player)
            throws ReflectiveOperationException {
        String cleanBuilder = builder == null ? "" : builder.trim();
        if (!cleanBuilder.equalsIgnoreCase(packetBuilder)) {
            packetBuilder = cleanBuilder;
            packetBuilderEntityId = -1;
        }
        if (player == null) return;
        Object entityId = invokeAny(player, "getId", "method_5628", "az");
        if (entityId instanceof Number) {
            packetBuilderEntityId = ((Number) entityId).intValue();
        }
    }

    private static void renderOverlay(Object graphics) {
        try {
            GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
            if (!settings.modEnabled) return;
            if (!settings.handheldItemOverlay) return;
            Object mc = minecraftClient();
            Object font = fieldAny(mc, "textRenderer", "font", "field_1772");
            Object window = invokeAny(mc, "getWindow", "method_22683");
            if (graphics == null || font == null || window == null) return;
            int screenWidth = intValue(invokeAny(window, "getScaledWidth", "getGuiScaledWidth", "method_4486"));
            int screenHeight = intValue(invokeAny(window, "getScaledHeight", "getGuiScaledHeight", "method_4502"));
            if (settings.migrateLegacyHandheldPanelSize(screenWidth, screenHeight)) settings.save();

            boolean preview = InterfacePreview.isActive();
            List<GameStats.HandheldItemEntry> entries;
            if (preview) {
                entries = HandheldItemTracker.INSTANCE.previewRenderEntries(settings.handheldItemMaxVisible);
            } else {
                if (!GameStats.INSTANCE.shouldRenderRoundOverlays()) return;
                entries = HandheldItemTracker.INSTANCE.renderEntries(
                        GameStats.INSTANCE.getCurrentRound(), settings.handheldItemMaxVisible);
            }
            if (entries == null || entries.isEmpty()) return;
            int requestedWidth = GtbStructuresSettings.handheldPanelWidthPixels(
                    settings.handheldItemPanelWidthRatio, screenWidth);
            int requestedHeight = GtbStructuresSettings.handheldPanelHeightPixels(
                    settings.handheldItemPanelHeightRatio, screenHeight);

            PanelLayout layout = settings.handheldItemVerticalLayout
                    ? verticalLayout(font, entries, requestedWidth,
                    requestedHeight, settings.handheldItemVerticalNewestAtBottom)
                    : horizontalLayout(entries, requestedWidth,
                    requestedHeight, settings.handheldItemHorizontalNewestAtRight,
                    settings.handheldItemHorizontalNewestAtBottom);
            float scale = HudPanelScale.normalize(settings.handheldItemScale);
            int scaledWidth = HudPanelScale.scaledPixels(layout.width, scale);
            int scaledHeight = HudPanelScale.scaledPixels(layout.height, scale);
            int x = HudPosition.left(settings.handheldItemPosX,
                    screenWidth, scaledWidth, MARGIN);
            int bottom = HudPosition.bottom(settings.handheldItemPosY,
                    screenHeight, scaledHeight, MARGIN);
            int top = bottom - scaledHeight;
            if (preview) {
                PreviewLayoutEditor.registerWithBackground("handhelditem", x, top,
                        scaledWidth, scaledHeight, PreviewLayoutEditor.HANDHELD,
                        (left, bgTop, right, bottomEdge, color) ->
                                fill(graphics, left, bgTop, right, bottomEdge, color));
            }
            FabricHudPose.Scope pose = FabricHudPose.push(graphics, x, top, scale);
            try {
                if (settings.handheldItemBackground) {
                    fill(graphics, -1, -1, layout.width + 1, layout.height + 1,
                            ScoreboardLayout.BACKGROUND_COLOR);
                }
                if (settings.handheldItemVerticalLayout) {
                    renderVertical(graphics, font, entries, 0, layout.gridY, layout.width,
                            settings.handheldItemVerticalNewestAtBottom);
                } else {
                    renderHorizontal(graphics, font, entries, 0, 0, layout,
                            settings.handheldItemHorizontalNewestAtRight,
                            settings.handheldItemHorizontalNewestAtBottom);
                }
            } finally {
                pose.pop();
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Unable to render GtbStructures Fabric handheld item overlay", e);
        }
    }

    private static PanelLayout verticalLayout(Object font, List<GameStats.HandheldItemEntry> entries,
                                              int requestedWidth, int requestedHeight,
                                              boolean newestAtBottom) {
        int width = requestedWidth;
        int contentHeight = 0;
        for (GameStats.HandheldItemEntry entry : entries) {
            contentHeight += verticalEntryHeight(font, entry, width);
        }
        int height = Math.max(requestedHeight, contentHeight);
        int gridY = newestAtBottom ? 0 : height - contentHeight;
        return new PanelLayout(width, height, 1, entries.size(), 0, gridY);
    }

    private static PanelLayout horizontalLayout(List<GameStats.HandheldItemEntry> entries,
                                                int requestedWidth, int requestedHeight,
                                                boolean newestAtRight, boolean newestAtBottom) {
        int width = requestedWidth;
        int requested = requestedHeight;
        int columns = Math.max(1, Math.min(entries.size(), width / HORIZONTAL_CELL_SIZE));
        int rows = Math.max(1, (entries.size() + columns - 1) / columns);
        int gridWidth = columns * HORIZONTAL_CELL_SIZE;
        int gridHeight = rows * HORIZONTAL_CELL_SIZE;
        int height = Math.max(requested, gridHeight);
        int gridX = newestAtRight ? 0 : width - gridWidth;
        int gridY = newestAtBottom ? 0 : height - gridHeight;
        return new PanelLayout(width, height, columns, rows, gridX, gridY);
    }

    private static void renderVertical(Object graphics, Object font,
                                       List<GameStats.HandheldItemEntry> newestFirst,
                                       int x, int top, int width, boolean newestAtBottom)
            throws ReflectiveOperationException {
        int y = top;
        int fontHeight = fontHeight(font);
        for (int i = 0; i < newestFirst.size(); i++) {
            int source = newestAtBottom ? newestFirst.size() - 1 - i : i;
            GameStats.HandheldItemEntry entry = newestFirst.get(source);
            int entryHeight = verticalEntryHeight(font, entry, width);
            renderVerticalEntry(graphics, font, entry, x + PANEL_PAD, y,
                    width - PANEL_PAD * 2, entryHeight, fontHeight);
            y += entryHeight;
        }
    }

    private static void renderVerticalEntry(Object graphics, Object font, GameStats.HandheldItemEntry entry,
                                            int x, int y, int width, int height, int fontHeight)
            throws ReflectiveOperationException {
        int textX = x + HandheldItemTracker.ICON_SIZE + HandheldItemTracker.TEXT_GAP;
        int textWidth = Math.max(1, width - (textX - x));
        List<CaptionLine> lines = captionLines(font, entry, textWidth);
        int iconY = y + Math.max(0, (height - HandheldItemTracker.ICON_SIZE) / 2);
        renderIcon(graphics, font, entry, x, iconY);
        int textY = captionStartY(y, height, lines.size(), fontHeight);
        for (CaptionLine line : lines) {
            drawText(graphics, font, line.text, textX, textY, line.color);
            textY += CAPTION_LINE_HEIGHT;
        }
    }

    private static int captionStartY(int entryY, int entryHeight, int lineCount, int fontHeight) {
        if (lineCount == 1) {
            return entryY + Math.max(0, (entryHeight - fontHeight) / 2);
        }
        return entryY + 1;
    }

    private static int fontHeight(Object font) {
        try {
            int height = intValue(fieldAny(font, "fontHeight", "lineHeight", "field_2000"));
            if (height > 0) return height;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Fall back to the native nine-pixel modern font height.
        }
        return CAPTION_LINE_HEIGHT + 1;
    }

    private static void renderHorizontal(Object graphics, Object font,
                                         List<GameStats.HandheldItemEntry> newestFirst,
                                         int x, int top, PanelLayout layout,
                                         boolean newestAtRight, boolean newestAtBottom)
            throws ReflectiveOperationException {
        for (int i = 0; i < newestFirst.size(); i++) {
            int col = i % layout.columns;
            int row = i / layout.columns;
            if (newestAtRight) col = layout.columns - 1 - col;
            if (newestAtBottom) row = layout.rows - 1 - row;
            renderIcon(graphics, font, newestFirst.get(i),
                    x + layout.gridX + col * HORIZONTAL_CELL_SIZE + 1,
                    top + layout.gridY + row * HORIZONTAL_CELL_SIZE + 1);
        }
    }

    private static int verticalEntryHeight(Object font, GameStats.HandheldItemEntry entry, int panelWidth) {
        int textWidth = Math.max(1, panelWidth - PANEL_PAD * 2
                - HandheldItemTracker.ICON_SIZE - HandheldItemTracker.TEXT_GAP);
        return Math.max(HandheldItemTracker.ROW_HEIGHT,
                captionLines(font, entry, textWidth).size() * CAPTION_LINE_HEIGHT + 2);
    }

    private static List<CaptionLine> captionLines(Object font, GameStats.HandheldItemEntry entry, int textWidth) {
        GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
        ArrayList<CaptionLine> lines = new ArrayList<CaptionLine>();
        if (settings.handheldItemCaptionName) {
            String name = entry == null || entry.name == null || entry.name.isEmpty()
                    ? "(unknown item)" : entry.name;
            lines.add(new CaptionLine(ellipsize(font, name, textWidth), 0xFFFFFF));
        }
        ArrayList<String> secondary = new ArrayList<String>();
        if (settings.handheldItemCaptionId) {
            secondary.add(itemId(entry));
        }
        if (settings.handheldItemCaptionMetadata) {
            secondary.add(String.valueOf(entry == null ? 0 : entry.metadata));
        }
        if (settings.handheldItemCaptionSpawnEggMob && isSpawnEgg(entry)) {
            secondary.add(spawnEggMobName(entry));
        }
        addPackedCaptions(lines, font, secondary, textWidth, 0xC8C8C8);
        return lines;
    }

    private static String itemId(GameStats.HandheldItemEntry entry) {
        if (entry == null) return "unknown";
        if (entry.registryName != null && !entry.registryName.isEmpty()) return entry.registryName;
        if (entry.numericId >= 0) return String.valueOf(entry.numericId);
        return entry.modernKind == null || entry.modernKind.isEmpty() ? "unknown" : entry.modernKind;
    }

    private static boolean isSpawnEgg(GameStats.HandheldItemEntry entry) {
        return entry != null && "spawn_egg".equals(entry.modernKind);
    }

    private static String spawnEggMobName(GameStats.HandheldItemEntry entry) {
        ModernSpawnEggData egg = entry == null ? null : ModernSpawnEggData.byDamage(entry.metadata);
        if (egg != null) return titleCase(egg.textureName);
        if (entry == null || entry.modernName == null) return null;
        String name = ModernRuntimeBridge.stripMinecraftFormatting(entry.modernName).trim();
        if (name.isEmpty() || "Spawn Egg".equalsIgnoreCase(name)) return null;
        String suffix = " Spawn Egg";
        if (name.length() > suffix.length()
                && name.regionMatches(true, name.length() - suffix.length(), suffix, 0, suffix.length())) {
            String mob = name.substring(0, name.length() - suffix.length()).trim();
            return mob.isEmpty() ? null : mob;
        }
        String prefix = "Spawn ";
        if (name.length() > prefix.length()
                && name.regionMatches(true, 0, prefix, 0, prefix.length())) {
            String mob = name.substring(prefix.length()).trim();
            return mob.isEmpty() || "Egg".equalsIgnoreCase(mob) ? null : mob;
        }
        return null;
    }

    private static void addPackedCaptions(List<CaptionLine> out, Object font, List<String> captions,
                                          int width, int color) {
        String current = "";
        for (String caption : captions) {
            if (caption == null || caption.isEmpty()) continue;
            String fitted = ellipsize(font, caption, width);
            String candidate = current.isEmpty() ? fitted : current + " | " + fitted;
            if (!current.isEmpty() && textWidth(font, candidate) > width) {
                out.add(new CaptionLine(current, color));
                current = fitted;
            } else {
                current = candidate;
            }
        }
        if (!current.isEmpty()) out.add(new CaptionLine(current, color));
    }

    private static String titleCase(String raw) {
        if (raw == null || raw.isEmpty()) return "Unknown";
        String[] words = raw.replace('_', ' ').split(" ");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) out.append(word.substring(1).toLowerCase(java.util.Locale.ROOT));
        }
        return out.length() == 0 ? "Unknown" : out.toString();
    }

    private static final class CaptionLine {
        private final String text;
        private final int color;

        private CaptionLine(String text, int color) {
            this.text = text;
            this.color = color;
        }
    }

    private static final class PanelLayout {
        private final int width;
        private final int height;
        private final int columns;
        private final int rows;
        private final int gridX;
        private final int gridY;

        private PanelLayout(int width, int height, int columns, int rows, int gridX, int gridY) {
            this.width = width;
            this.height = height;
            this.columns = columns;
            this.rows = rows;
            this.gridX = gridX;
            this.gridY = gridY;
        }
    }

    private static void renderIcon(Object graphics, Object font,
                                   GameStats.HandheldItemEntry entry, int x, int y)
            throws ReflectiveOperationException {
        try {
            if (entry != null && "spawn_egg".equals(entry.modernKind)
                    && drawSpawnEggIcon(graphics, entry, x, y)) {
                return;
            }
            Object stack = itemStackForEntry(entry);
            if (stack != null) {
                if (!DRAW_ITEM_CALL.invokeIfPresent(graphics, stack, x, y)) {
                    if (shouldLogIconFailure(entry)) {
                        LOGGER.warn("No compatible item draw method for GtbStructures handheld icon {}",
                                itemDebugName(entry));
                    }
                    return;
                }
                DRAW_ITEM_OVERLAY_CALL.invokeIfPresent(graphics,
                        font, stack, x, y);
                return;
            }
            if (shouldLogIconFailure(entry)) {
                LOGGER.warn("No ItemStack available for GtbStructures handheld icon {}",
                        itemDebugName(entry));
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (shouldLogIconFailure(entry)) {
                LOGGER.warn("Unable to render GtbStructures handheld item icon for {}", itemDebugName(entry), e);
            }
        }
    }

    static boolean shouldLogIconFailureForTest(GameStats.HandheldItemEntry entry) {
        return shouldLogIconFailure(entry);
    }

    private static boolean shouldLogIconFailure(GameStats.HandheldItemEntry entry) {
        return WARNED_ICON_FAILURES.add(itemDebugName(entry));
    }

    private static String itemDebugName(GameStats.HandheldItemEntry entry) {
        if (entry == null) return "<null>";
        String registry = entry.registryName == null ? "" : entry.registryName;
        return entry.name + " [" + registry + " | " + entry.metadata + "]";
    }

    private static boolean drawSpawnEggIcon(Object graphics, GameStats.HandheldItemEntry entry, int x, int y)
            throws ReflectiveOperationException {
        ModernSpawnEggData egg = ModernSpawnEggData.byDamage(entry.metadata);
        if (egg == null) return false;
        if ("new".equals(GtbStructuresSettings.INSTANCE.spawnEggSkin)) {
            return drawTexture(graphics,
                    "gtbstructures:textures/item/spawn_egg/new/" + egg.textureName + "_spawn_egg.png",
                    x, y, HandheldItemTracker.ICON_SIZE, 0xFFFFFF);
        }
        boolean base = drawTexture(graphics, CLASSIC_EGG_BASE,
                x, y, HandheldItemTracker.ICON_SIZE, egg.classicBaseColor);
        boolean overlay = drawTexture(graphics, CLASSIC_EGG_OVERLAY,
                x, y, HandheldItemTracker.ICON_SIZE, egg.classicOverlayColor);
        return base && overlay;
    }

    private static boolean drawTexture(Object graphics, String textureName, int x, int y, int size, int tintRgb)
            throws ReflectiveOperationException {
        Object texture = identifier(textureName);
        if (texture == null) return false;
        Object pipeline = texturePipeline(graphics, texture);
        int color = 0xFF000000 | (tintRgb & 0x00FFFFFF);
        setShaderColor(tintRgb);
        try {
            if (pipeline != null && tryDrawTexture(graphics, pipeline, texture, x, y, size, color)) {
                return true;
            }
            return tryDrawTexture(graphics, null, texture, x, y, size, color);
        } finally {
            setShaderColor(0xFFFFFF);
        }
    }

    private static boolean tryDrawTexture(Object graphics, Object pipeline, Object texture,
                                          int x, int y, int size, int color)
            throws ReflectiveOperationException {
        Object[][] attempts = pipeline == null
                ? new Object[][] {
                {texture, x, y, Float.valueOf(0.0F), Float.valueOf(0.0F),
                        size, size, size, size, color},
                {texture, x, y, Float.valueOf(0.0F), Float.valueOf(0.0F),
                        size, size, size, size},
                {texture, x, y, 0, 0, size, size, size, size}
        }
                : new Object[][] {
                {pipeline, texture, x, y, Float.valueOf(0.0F), Float.valueOf(0.0F),
                        size, size, size, size, color},
                {pipeline, texture, x, y, Float.valueOf(0.0F), Float.valueOf(0.0F),
                        size, size, size, size}
        };
        for (Object[] attempt : attempts) {
            if (DRAW_TEXTURE_CALL.invokeIfPresent(graphics, attempt)) {
                return true;
            }
        }
        return false;
    }

    private static Object texturePipeline(Object graphics, Object texture) throws ReflectiveOperationException {
        if (graphics == null || texture == null) return null;
        Map<Class<?>, Object> types = TEXTURE_PIPELINE_TYPES.get(graphics.getClass());
        Object cachedType = types.computeIfAbsent(texture.getClass(),
                type -> resolveTexturePipelineType(graphics.getClass(), type));
        if (!(cachedType instanceof Class)) return null;
        Class<?> pipelineType = (Class<?>) cachedType;
        Object pipeline = TEXTURE_PIPELINES.computeIfAbsent(pipelineType,
                HandheldItemOverlayRenderer::resolveTexturePipeline);
        return pipeline == REFLECTION_MISS ? null : pipeline;
    }

    private static Object resolveTexturePipelineType(Class<?> graphicsType, Class<?> textureType) {
        for (Method method : graphicsType.getMethods()) {
            if ((!method.getName().equals("drawTexture") && !method.getName().equals("blit")
                    && !method.getName().equals("method_25290")
                    && !method.getName().equals("method_25293") && !method.getName().equals("method_25302"))
                    || method.getParameterCount() < 2) {
                continue;
            }
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters[1].isAssignableFrom(textureType)
                    && !parameters[0].isAssignableFrom(textureType)) {
                return parameters[0];
            }
        }
        return REFLECTION_MISS;
    }

    private static Object resolveTexturePipeline(Class<?> pipelineType) {
        String[] holders = {
                "net.minecraft.client.renderer.RenderPipelines",
                "net.minecraft.client.render.RenderPipelines",
                "net.minecraft.client.gl.RenderPipelines",
                "net.minecraft.client.gui.render.RenderPipelines",
                "net.minecraft.class_10799"
        };
        for (String holderName : holders) {
            try {
                Class<?> holder = RuntimeReflectionCache.findClassAny(holderName);
                if (holder == null) continue;
                Object pipeline = staticAssignableField(holder, pipelineType, "GUI_TEXTURED");
                if (pipeline == null) pipeline = staticAssignableField(holder, pipelineType, "field_56883");
                if (pipeline == null) pipeline = staticAssignableField(holder, pipelineType, "");
                if (pipeline != null) return pipeline;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Try the next holder or fall back to a pipeline-free overload.
            }
        }
        return REFLECTION_MISS;
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

    private static void setShaderColor(int tintRgb) {
        try {
            Class<?> renderSystem = RuntimeReflectionCache.findClassAny(
                    "com.mojang.blaze3d.systems.RenderSystem");
            if (renderSystem == null) return;
            float red = ((tintRgb >> 16) & 0xFF) / 255.0F;
            float green = ((tintRgb >> 8) & 0xFF) / 255.0F;
            float blue = (tintRgb & 0xFF) / 255.0F;
            SHADER_COLOR_CALL.invokeStatic(renderSystem,
                    Float.valueOf(red), Float.valueOf(green), Float.valueOf(blue), Float.valueOf(1.0F));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Texture overloads with a color argument do not need shader color.
        }
    }

    private static Object itemStackForEntry(GameStats.HandheldItemEntry entry)
            throws ReflectiveOperationException {
        String registry = modernRegistryName(entry);
        if (registry == null || registry.isEmpty()) return null;
        if (UNRENDERABLE_ITEM_STACKS.contains(registry)) return null;
        Object cached = ITEM_STACK_CACHE.get(registry);
        if (cached != null) return cached;
        try {
            Object item = itemForRegistry(registry);
            if (item != null) {
                Class<?> stackClass = itemStackClass();
                for (Constructor<?> constructor : stackClass.getConstructors()) {
                    if (constructor.getParameterCount() == 1
                            && isCompatible(constructor.getParameterTypes()[0], item)) {
                        Object resolved = constructor.newInstance(item);
                        Object raced = ITEM_STACK_CACHE.putIfAbsent(registry, resolved);
                        return raced == null ? resolved : raced;
                    }
                }
            }
            UNRENDERABLE_ITEM_STACKS.add(registry);
            return null;
        } catch (ReflectiveOperationException | RuntimeException error) {
            UNRENDERABLE_ITEM_STACKS.add(registry);
            throw error;
        }
    }

    private static Object itemForRegistry(String registry) throws ReflectiveOperationException {
        Object id = identifier(registry);
        Object itemRegistry = itemRegistry();
        if (id == null || itemRegistry == null) return null;
        Class<?> stackClass = itemStackClass();
        for (Method method : itemRegistry.getClass().getMethods()) {
            if (method.getParameterCount() != 1 || !isCompatible(method.getParameterTypes()[0], id)) continue;
            Object item = method.invoke(itemRegistry, id);
            if (item == null) continue;
            for (Constructor<?> constructor : stackClass.getConstructors()) {
                if (constructor.getParameterCount() == 1
                        && isCompatible(constructor.getParameterTypes()[0], item)) {
                    return item;
                }
            }
        }
        return null;
    }

    private static Class<?> itemStackClass() throws ClassNotFoundException {
        return classAny("net.minecraft.world.item.ItemStack", "net.minecraft.item.ItemStack",
                "net.minecraft.class_1799", "dhp", "dlt");
    }

    private static Object itemRegistry() throws ReflectiveOperationException {
        Class<?> registries = classAny("net.minecraft.core.registries.BuiltInRegistries",
                "net.minecraft.registry.Registries", "net.minecraft.class_7923", "mo", "mi");
        return staticFieldAny(registries, "ITEM", "h", "field_41178", "field_11142");
    }

    private static Object identifier(String registry) throws ReflectiveOperationException {
        String value = registry.indexOf(':') >= 0 ? registry : "minecraft:" + registry;
        Object identifier = IDENTIFIER_CACHE.computeIfAbsent(value,
                HandheldItemOverlayRenderer::resolveIdentifier);
        return identifier == REFLECTION_MISS ? null : identifier;
    }

    private static Object resolveIdentifier(String value) {
        try {
            Class<?> idClass = classAny("net.minecraft.resources.ResourceLocation",
                    "net.minecraft.resources.Identifier",
                    "net.minecraft.util.Identifier", "net.minecraft.class_2960", "amj", "amo");
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
                return REFLECTION_MISS;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return REFLECTION_MISS;
        }
    }

    private static String modernRegistryName(GameStats.HandheldItemEntry entry) {
        if (entry == null) return null;
        if ("spawn_egg".equals(entry.modernKind)) {
            ModernSpawnEggData egg = ModernSpawnEggData.byDamage(entry.metadata);
            return egg == null ? "minecraft:spawn_egg" : "minecraft:" + egg.textureName + "_spawn_egg";
        }
        String registry = entry.registryName == null ? "" : entry.registryName.trim();
        if (registry.isEmpty()) return null;
        int colon = registry.indexOf(':');
        String namespace = colon >= 0 ? registry.substring(0, colon) : "minecraft";
        String path = colon >= 0 ? registry.substring(colon + 1) : registry;
        if ("wool".equals(path)) {
            if (entry.metadata == 0) path = "white_wool";
            else if (entry.metadata == 7) path = "gray_wool";
        } else if ("stone".equals(path)) {
            if (entry.metadata == 3) path = "diorite";
            else if (entry.metadata == 4) path = "polished_diorite";
            else if (entry.metadata == 5) path = "andesite";
        } else if ("stained_hardened_clay".equals(path)) {
            if (entry.metadata == 9) path = "cyan_terracotta";
        }
        return namespace + ":" + path;
    }

    private static Object findPlayer(Object mc, String ign) throws ReflectiveOperationException {
        Object world = fieldAny(mc, "world", "level", "field_1687");
        Object players = invokeAny(world, "getPlayers", "players", "method_18112");
        if (!(players instanceof Iterable)) return null;
        for (Object player : (Iterable<?>) players) {
            String name = playerName(player);
            if (name != null && name.equalsIgnoreCase(ign)) return player;
        }
        return null;
    }

    private static HandheldItemTracker.ItemSnapshot snapshotForLocalPlayer(Object mc)
            throws ReflectiveOperationException {
        return snapshotFor(fieldAny(mc, "player", "field_1724"));
    }

    private static HandheldItemTracker.ItemSnapshot snapshotFor(Object player)
            throws ReflectiveOperationException {
        if (player == null) return null;
        return snapshotForStack(invokeAny(player, "getMainHandStack", "getMainHandItem", "method_6047"));
    }

    private static HandheldItemTracker.ItemSnapshot snapshotForStack(Object stack)
            throws ReflectiveOperationException {
        if (stack == null || booleanValue(invokeAny(stack, "isEmpty", "method_7960"))) return null;
        Object item = invokeAny(stack, "getItem", "method_7909");
        if (item == null) return null;
        String registry = registryNameForItem(item);
        String name = formattedItemName(
                invokeAny(stack, "getName", "getHoverName", "method_7964"));
        int count = intValue(invokeAny(stack, "getCount", "method_7947"));
        if (HandheldItemTracker.spawnEggDataFromRegistry(registry) != null) {
            return HandheldItemTracker.spawnEggSnapshot(registry, name, count);
        }
        Object translation = invokeAny(item, "getTranslationKey", "getDescriptionId", "method_7876");
        return HandheldItemTracker.stackSnapshot(name, registry, -1, 0, count,
                translation == null ? null : translation.toString(), "");
    }

    private static String registryNameForItem(Object item) {
        try {
            Object itemRegistry = itemRegistry();
            Object id = invokeAnyWithArg(itemRegistry, item, "getId", "method_10221");
            return id == null ? String.valueOf(item) : id.toString();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return String.valueOf(item);
        }
    }

    private static String ellipsize(Object font, String text, int maxWidth) {
        if (text == null) return "";
        if (textWidth(font, text) <= maxWidth) return text;
        String suffix = "...";
        int suffixWidth = textWidth(font, suffix);
        String out = text;
        while (!out.isEmpty() && textWidth(font, out) + suffixWidth > maxWidth) {
            out = out.substring(0, out.length() - 1);
        }
        return out.isEmpty() ? suffix : out + suffix;
    }

    private static String playerName(Object player) throws ReflectiveOperationException {
        Object profile = invokeAny(player, "getGameProfile", "method_7334");
        Object name = invokeAny(profile, "getName");
        if (name instanceof String) return (String) name;
        Object text = invokeAny(player, "getName", "method_5477");
        return textString(text);
    }

    private static String textString(Object text) throws ReflectiveOperationException {
        if (text == null) return "";
        Object value = invokeAny(text, "getString", "method_10851");
        return value == null ? text.toString() : value.toString();
    }

    static String formattedItemNameForTest(Object text) {
        return formattedItemName(text);
    }

    static String spawnEggMobNameForTest(GameStats.HandheldItemEntry entry) {
        return spawnEggMobName(entry);
    }

    static int captionStartYForTest(int entryY, int entryHeight, int lineCount, int fontHeight) {
        return captionStartY(entryY, entryHeight, lineCount, fontHeight);
    }

    private static String formattedItemName(Object text) {
        return MinecraftFormatting.legacyFormattedText(text);
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

    private static Object staticFieldAny(Class<?> type, String... names) throws ReflectiveOperationException {
        return RuntimeReflectionCache.staticFieldAny(type, names);
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

    private static boolean tryInvokeAnyArgs(Object target, String[] names, Object... args)
            throws ReflectiveOperationException {
        if (target == null) return false;
        Method method = RuntimeReflectionCache.findCompatibleMethod(
                target.getClass(), false, names, args);
        if (method == null) return false;
        method.invoke(target, args);
        return true;
    }

    private static Object invokeStaticAnyArgs(Class<?> type, String[] names, Object... args)
            throws ReflectiveOperationException {
        if (type == null) return null;
        Method method = RuntimeReflectionCache.findCompatibleMethod(type, true, names, args);
        return method == null ? null : method.invoke(null, args);
    }

    private static boolean isCompatible(Class<?> parameter, Object arg) {
        if (arg == null) return !parameter.isPrimitive();
        if (!parameter.isPrimitive()) return parameter.isInstance(arg);
        if (parameter.equals(Integer.TYPE)) return arg instanceof Integer;
        if (parameter.equals(Boolean.TYPE)) return arg instanceof Boolean;
        if (parameter.equals(Float.TYPE)) return arg instanceof Float;
        if (parameter.equals(Long.TYPE)) return arg instanceof Long;
        if (parameter.equals(Double.TYPE)) return arg instanceof Double;
        if (parameter.equals(Byte.TYPE)) return arg instanceof Byte;
        if (parameter.equals(Short.TYPE)) return arg instanceof Short;
        if (parameter.equals(Character.TYPE)) return arg instanceof Character;
        return false;
    }

    private static boolean booleanValue(Object value) {
        return value instanceof Boolean && ((Boolean) value).booleanValue();
    }

    private static int intValue(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private static void fill(Object graphics, int left, int top, int right, int bottom, int color)
            throws ReflectiveOperationException {
        FILL_CALL.invokeIfPresent(graphics,
                left, top, right, bottom, color);
    }

    private static void drawText(Object graphics, Object font, String text, int x, int y, int color)
            throws ReflectiveOperationException {
        DRAW_TEXT_CALL.invokeIfPresent(graphics,
                font, text, x, y, opaqueColor(color), true);
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
}
