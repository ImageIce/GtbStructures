package com.gtbstructures.items;

import com.gtbstructures.GtbStructuresSettings;
import com.gtbstructures.ModernRuntimeBridge;
import com.gtbstructures.overlay.ForgeHudLayerBridge;
import com.gtbstructures.overlay.HudPosition;
import com.gtbstructures.scoreboard.InterfacePreview;
import com.gtbstructures.scoreboard.PreviewLayoutEditor;
import com.gtbstructures.scoreboard.ScoreboardLayout;
import com.gtbstructures.stats.GameStats;
import com.gtbstructures.util.MinecraftFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.event.TickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class HandheldItemOverlayRenderer {
    private static final int EXACT_ITEM_STACK_CACHE_LIMIT = 256;
    private static final Logger LOGGER = LoggerFactory.getLogger(HandheldItemOverlayRenderer.class);
    private static final int MARGIN = 2;
    private static final int CAPTION_LINE_HEIGHT = 8;
    private static final int PANEL_PADDING = 2;
    private static final int ICON_CELL_SIZE = 18;
    private static final Set<String> WARNED_ICON_FAILURES = ConcurrentHashMap.newKeySet();
    private static final Map<String, ItemStack> EXACT_ITEM_STACK_CACHE = new ConcurrentHashMap<>();
    private static boolean previewWasActive;

    private HandheldItemOverlayRenderer() {
    }

    public static void register() {
        AddGuiOverlayLayersEvent.BUS.addListener(HandheldItemOverlayRenderer::addLayer);
        TickEvent.ClientTickEvent.Post.BUS.addListener(HandheldItemOverlayRenderer::onClientTickPost);
    }

    private static void addLayer(AddGuiOverlayLayersEvent event) {
        ForgeHudLayerBridge.addAboveChat(event.getLayeredDraw(), "handheld_items",
                HandheldItemOverlayRenderer::render);
    }

    private static void onClientTickPost(TickEvent.ClientTickEvent.Post event) {
        long now = System.currentTimeMillis();
        try {
            if (!GtbStructuresSettings.INSTANCE.modEnabled) {
                if (previewWasActive) HandheldItemTracker.INSTANCE.endPreview(now);
                previewWasActive = false;
                return;
            }
            Minecraft mc = Minecraft.getInstance();
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
        } catch (RuntimeException e) {
            LOGGER.warn("Unable to tick GtbStructures handheld item overlay", e);
            HandheldItemTracker.INSTANCE.closeForTest(now);
        }
    }

    private static void tickLive(Minecraft mc, long now) {
        GameStats stats = GameStats.INSTANCE;
        String builder = stats.getCurrentBuilder();
        HandheldItemTracker.ItemSnapshot snapshot = null;
        if (builder != null && !builder.trim().isEmpty() && mc != null && mc.level != null) {
            snapshot = snapshotFor(findPlayer(mc, builder));
        }
        HandheldItemTracker.INSTANCE.tickRound(stats.getState(), stats.getCurrentRound(),
                builder, snapshot, now);
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        try {
            renderOverlay(graphics);
        } catch (RuntimeException e) {
            LOGGER.warn("Unable to render GtbStructures handheld item overlay", e);
        }
    }

    private static void renderOverlay(GuiGraphicsExtractor graphics) {
        GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
        if (!settings.modEnabled) return;
        if (!settings.handheldItemOverlay) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null || mc.getWindow() == null) return;
        if (settings.migrateLegacyHandheldPanelSize(mc.getWindow().getGuiScaledWidth(),
                mc.getWindow().getGuiScaledHeight())) {
            settings.save();
        }

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

        if (settings.handheldItemVerticalLayout) {
            renderVertical(graphics, mc.font, settings, entries, preview,
                    mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        } else {
            renderHorizontal(graphics, mc.font, settings, entries, preview,
                    mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        }
    }

    private static void renderVertical(GuiGraphicsExtractor graphics, Font font, GtbStructuresSettings settings,
                                       List<GameStats.HandheldItemEntry> entries, boolean preview,
                                       int screenWidth, int screenHeight) {
        int width = GtbStructuresSettings.handheldPanelWidthPixels(settings.handheldItemPanelWidthRatio, screenWidth);
        List<EntryLines> rendered = new ArrayList<EntryLines>();
        int contentHeight = 0;
        for (GameStats.HandheldItemEntry entry : entries) {
            EntryLines lines = entryLines(font, entry, settings,
                    width - HandheldItemTracker.ICON_SIZE - HandheldItemTracker.TEXT_GAP - PANEL_PADDING * 2);
            rendered.add(lines);
            contentHeight += lines.height;
        }
        int height = Math.max(GtbStructuresSettings.handheldPanelHeightPixels(settings.handheldItemPanelHeightRatio, screenHeight),
                contentHeight);
        int x = HudPosition.left(settings.handheldItemPosX, screenWidth, width, MARGIN);
        int bottom = HudPosition.bottom(settings.handheldItemPosY, screenHeight, height, MARGIN);
        int top = bottom - height;
        drawPanel(graphics, settings, preview, x, top, width, height);

        if (settings.handheldItemVerticalNewestAtBottom) {
            reverseEntryLines(rendered);
            int y = top;
            for (EntryLines entry : rendered) {
                renderVerticalEntry(graphics, font, entry, x, y, width);
                y += entry.height;
            }
        } else {
            int y = top + height - contentHeight;
            for (EntryLines entry : rendered) {
                renderVerticalEntry(graphics, font, entry, x, y, width);
                y += entry.height;
            }
        }
    }

    private static void renderHorizontal(GuiGraphicsExtractor graphics, Font font, GtbStructuresSettings settings,
                                         List<GameStats.HandheldItemEntry> entries, boolean preview,
                                         int screenWidth, int screenHeight) {
        int width = Math.max(ICON_CELL_SIZE, GtbStructuresSettings.handheldPanelWidthPixels(
                settings.handheldItemPanelWidthRatio, screenWidth));
        int columns = Math.max(1, Math.min(entries.size(), width / ICON_CELL_SIZE));
        int rows = Math.max(1, (entries.size() + columns - 1) / columns);
        int contentWidth = columns * ICON_CELL_SIZE;
        int contentHeight = rows * ICON_CELL_SIZE;
        int height = Math.max(GtbStructuresSettings.handheldPanelHeightPixels(settings.handheldItemPanelHeightRatio, screenHeight),
                contentHeight);
        int x = HudPosition.left(settings.handheldItemPosX, screenWidth, width, MARGIN);
        int bottom = HudPosition.bottom(settings.handheldItemPosY, screenHeight, height, MARGIN);
        int top = bottom - height;
        drawPanel(graphics, settings, preview, x, top, width, height);

        int gridX = settings.handheldItemHorizontalNewestAtRight ? x : x + width - contentWidth;
        int gridY = settings.handheldItemHorizontalNewestAtBottom ? top : top + height - contentHeight;
        for (int i = 0; i < entries.size(); i++) {
            int column = i % columns;
            int row = i / columns;
            if (settings.handheldItemHorizontalNewestAtRight) column = columns - 1 - column;
            if (settings.handheldItemHorizontalNewestAtBottom) row = rows - 1 - row;
            int cellX = gridX + column * ICON_CELL_SIZE;
            int cellY = gridY + row * ICON_CELL_SIZE;
            renderIcon(graphics, font, entries.get(i), cellX + 1, cellY + 1);
        }
    }

    private static void drawPanel(GuiGraphicsExtractor graphics, GtbStructuresSettings settings, boolean preview,
                                  int x, int top, int width, int height) {
        if (settings.handheldItemBackground) {
            graphics.fill(x - 1, top - 1, x + width + 1, top + height + 1,
                    ScoreboardLayout.BACKGROUND_COLOR);
        }
        if (preview) {
            PreviewLayoutEditor.registerWithBackground("handhelditem", x, top, width, height,
                    PreviewLayoutEditor.HANDHELD,
                    (left, panelTop, right, bottom, color) -> graphics.fill(left, panelTop, right, bottom, color));
        }
    }

    private static void renderVerticalEntry(GuiGraphicsExtractor graphics, Font font, EntryLines entry,
                                            int x, int y, int width) {
        int iconY = y + Math.max(0, (entry.height - HandheldItemTracker.ICON_SIZE) / 2);
        renderIcon(graphics, font, entry.entry, x + PANEL_PADDING, iconY);
        int textX = x + PANEL_PADDING + HandheldItemTracker.ICON_SIZE + HandheldItemTracker.TEXT_GAP;
        int textY = y + captionTopOffset(entry.height, entry.lines.size());
        for (CaptionLine line : entry.lines) {
            graphics.text(font, line.text, textX, textY, opaqueColor(line.color), true);
            textY += CAPTION_LINE_HEIGHT;
        }
    }

    private static int captionTopOffset(int entryHeight, int lineCount) {
        int captionHeight = Math.max(1, lineCount) * CAPTION_LINE_HEIGHT + 2;
        return Math.max(0, (entryHeight - captionHeight) / 2) + 1;
    }

    static int captionTopOffsetForTest(int entryHeight, int lineCount) {
        return captionTopOffset(entryHeight, lineCount);
    }

    private static EntryLines entryLines(Font font, GameStats.HandheldItemEntry entry,
                                         GtbStructuresSettings settings, int width) {
        List<CaptionLine> out = new ArrayList<CaptionLine>();
        int maxWidth = Math.max(8, width);
        if (settings.handheldItemCaptionName) {
            out.add(new CaptionLine(ellipsize(font, entry == null || entry.name == null || entry.name.isEmpty()
                    ? "(unknown item)" : entry.name, maxWidth), 0xFFFFFF));
        }
        List<String> secondary = new ArrayList<String>();
        if (settings.handheldItemCaptionId) {
            String id = entry == null ? "unknown" : entry.registryName;
            if (id == null || id.isEmpty()) id = entry.numericId >= 0 ? String.valueOf(entry.numericId) : "unknown";
            secondary.add(ellipsize(font, id, maxWidth));
        }
        if (settings.handheldItemCaptionMetadata && entry != null) {
            secondary.add(ellipsize(font, String.valueOf(entry.metadata), maxWidth));
        }
        if (settings.handheldItemCaptionSpawnEggMob) {
            String mob = spawnEggMobName(entry);
            if (mob != null) secondary.add(ellipsize(font, mob, maxWidth));
        }
        appendPackedSecondary(out, font, secondary, maxWidth);
        return new EntryLines(entry, out, Math.max(HandheldItemTracker.ROW_HEIGHT,
                Math.max(1, out.size()) * CAPTION_LINE_HEIGHT + 2));
    }

    private static void appendPackedSecondary(List<CaptionLine> out, Font font, List<String> tokens, int width) {
        String row = "";
        for (String token : tokens) {
            String candidate = row.isEmpty() ? token : row + " | " + token;
            if (!row.isEmpty() && font.width(candidate) > width) {
                out.add(new CaptionLine(row, 0xC8C8C8));
                row = token;
            } else {
                row = candidate;
            }
        }
        if (!row.isEmpty()) out.add(new CaptionLine(row, 0xC8C8C8));
    }

    private static String spawnEggMobName(GameStats.HandheldItemEntry entry) {
        if (entry == null || !"spawn_egg".equals(entry.modernKind)) return null;
        ModernSpawnEggData egg = ModernSpawnEggData.byDamage(entry.metadata);
        if (egg != null) return titleCase(egg.textureName);
        if (entry.modernName == null || entry.modernName.trim().isEmpty()) return null;
        String name = ModernRuntimeBridge.stripMinecraftFormatting(entry.modernName).trim();
        String suffix = " Spawn Egg";
        int suffixStart = name.length() - suffix.length();
        if (suffixStart > 0 && name.regionMatches(true, suffixStart, suffix, 0, suffix.length())) {
            return name.substring(0, suffixStart).trim();
        }
        String prefix = "Spawn ";
        if (name.length() > prefix.length() && name.regionMatches(true, 0, prefix, 0, prefix.length())) {
            String mob = name.substring(prefix.length()).trim();
            if (!"Egg".equalsIgnoreCase(mob)) return mob;
        }
        return null;
    }

    static String spawnEggMobNameForTest(GameStats.HandheldItemEntry entry) {
        return spawnEggMobName(entry);
    }

    private static String titleCase(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        String[] parts = raw.replace('_', ' ').split(" ");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1).toLowerCase());
        }
        return out.toString();
    }

    private static void reverseEntryLines(List<EntryLines> entries) {
        for (int low = 0, high = entries.size() - 1; low < high; low++, high--) {
            EntryLines value = entries.get(low);
            entries.set(low, entries.get(high));
            entries.set(high, value);
        }
    }

    private static void renderIcon(GuiGraphicsExtractor graphics, Font font,
                                   GameStats.HandheldItemEntry entry, int x, int y) {
        try {
            ItemStack stack = exactItemStackForEntry(entry);
            if (stack == null) {
                stack = SpawnEggItemModelSelector.stackForRegistryName(modernRegistryName(entry));
            }
            if (stack.isEmpty()) {
                throw new IllegalStateException("No ItemStack for " + itemDebugName(entry));
            }
            stack = SpawnEggItemModelSelector.selectForRender(stack);
            graphics.item(stack, x, y);
            graphics.itemDecorations(font, stack, x, y);
        } catch (RuntimeException e) {
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

    static String modernRegistryNameForTest(GameStats.HandheldItemEntry entry) {
        return modernRegistryName(entry);
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

    private static AbstractClientPlayer findPlayer(Minecraft mc, String ign) {
        if (mc == null || mc.level == null || ign == null) return null;
        for (AbstractClientPlayer player : mc.level.players()) {
            if (player.getName() != null
                    && ign.equalsIgnoreCase(player.getName().getString())) {
                return player;
            }
        }
        return null;
    }

    private static HandheldItemTracker.ItemSnapshot snapshotForLocalPlayer(Minecraft mc) {
        return mc == null ? null : snapshotFor(mc.player);
    }

    private static HandheldItemTracker.ItemSnapshot snapshotFor(AbstractClientPlayer player) {
        if (player == null) return null;
        return snapshotForStack(player.getMainHandItem());
    }

    static HandheldItemTracker.ItemSnapshot snapshotForStackForTest(ItemStack stack) {
        return snapshotForStack(stack);
    }

    private static HandheldItemTracker.ItemSnapshot snapshotForStack(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getItem() == null) return null;
        String registry = registryName(stack);
        String name = formattedItemName(stack.getHoverName());
        int count = stack.getCount();
        if (HandheldItemTracker.spawnEggDataFromRegistry(registry) != null) {
            return HandheldItemTracker.spawnEggSnapshot(registry, name, count);
        }
        String components = stack.getComponentsPatch().isEmpty()
                ? "" : stack.getComponentsPatch().toString();
        HandheldItemTracker.ItemSnapshot snapshot = HandheldItemTracker.stackSnapshot(
                name, registry, -1, 0, count, stack.getItem().getDescriptionId(), components);
        if (!components.isEmpty()) rememberExactItemStack(snapshot.key, stack);
        return snapshot;
    }

    private static void rememberExactItemStack(String key, ItemStack stack) {
        if (key == null || stack == null || stack.isEmpty()) return;
        if (EXACT_ITEM_STACK_CACHE.containsKey(key)) return;
        if (EXACT_ITEM_STACK_CACHE.size() >= EXACT_ITEM_STACK_CACHE_LIMIT) {
            EXACT_ITEM_STACK_CACHE.clear();
        }
        EXACT_ITEM_STACK_CACHE.putIfAbsent(key, stack.copy());
    }

    static ItemStack exactItemStackForTest(GameStats.HandheldItemEntry entry) {
        return exactItemStackForEntry(entry);
    }

    private static ItemStack exactItemStackForEntry(GameStats.HandheldItemEntry entry) {
        return entry == null ? null : EXACT_ITEM_STACK_CACHE.get(entry.key);
    }

    private static String registryName(ItemStack stack) {
        String id = SpawnEggItemModelSelector.registryNameForItem(stack.getItem());
        return id == null ? "unknown" : id;
    }

    static String formattedItemNameForTest(Component text) {
        return formattedItemName(text);
    }

    private static String formattedItemName(Component text) {
        return MinecraftFormatting.legacyFormattedText(text);
    }

    private static boolean isRoundState(GameStats.TrackingState state) {
        return state == GameStats.TrackingState.ROUND_PRE
                || state == GameStats.TrackingState.ROUND_BUILD
                || state == GameStats.TrackingState.ROUND_END;
    }

    private static String ellipsize(Font font, String text, int maxWidth) {
        if (text == null) return "";
        if (font.width(text) <= maxWidth) return text;
        String suffix = "...";
        int suffixWidth = font.width(suffix);
        String out = text;
        while (!out.isEmpty() && font.width(out) + suffixWidth > maxWidth) {
            out = out.substring(0, out.length() - 1);
        }
        return out.isEmpty() ? suffix : out + suffix;
    }

    private static int opaqueColor(int color) {
        return (color & 0xFF000000) == 0 ? 0xFF000000 | color : color;
    }

    private static final class CaptionLine {
        private final String text;
        private final int color;

        private CaptionLine(String text, int color) {
            this.text = text;
            this.color = color;
        }
    }

    private static final class EntryLines {
        private final GameStats.HandheldItemEntry entry;
        private final List<CaptionLine> lines;
        private final int height;

        private EntryLines(GameStats.HandheldItemEntry entry, List<CaptionLine> lines, int height) {
            this.entry = entry;
            this.lines = lines;
            this.height = height;
        }
    }

}
