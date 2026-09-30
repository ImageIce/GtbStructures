package com.gtbstructures.items;

import com.gtbstructures.GtbStructures;
import com.gtbstructures.overlay.HudPanelScale;
import com.gtbstructures.overlay.HudPosition;
import com.gtbstructures.scoreboard.InterfacePreview;
import com.gtbstructures.scoreboard.PreviewLayoutEditor;
import com.gtbstructures.scoreboard.ScoreboardLayout;
import com.gtbstructures.stats.GameStats;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StringUtils;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class HandheldItemTracker {
    public static final HandheldItemTracker INSTANCE = new HandheldItemTracker();

    private static final int MIN_VISIBLE = 2;
    private static final int MAX_VISIBLE = 10;
    private static final int MIN_PANEL_WIDTH = 32;
    private static final int MIN_PANEL_HEIGHT = 18;
    private static final int PANEL_MARGIN = 2;
    private static final int ROW_HEIGHT = 18;
    private static final int ICON_SIZE = 16;
    private static final float TEXT_SCALE = 1.0F;
    private static final boolean TEXT_SHADOW = true;
    private static final int TEXT_GAP = 4;

    private static final ResourceLocation CLASSIC_EGG_BASE =
            new ResourceLocation("gtbstructures", "textures/item/spawn_egg/classic/spawn_egg.png");
    private static final ResourceLocation CLASSIC_EGG_OVERLAY =
            new ResourceLocation("gtbstructures", "textures/item/spawn_egg/classic/spawn_egg_overlay.png");

    private final Map<String, ItemSnapshot> renderSnapshots = new LinkedHashMap<String, ItemSnapshot>();
    private GameStats.RoundData activeRound;
    private String activeBuilder = "";
    private String currentKey = "";
    private GameStats.HandheldItemEntry currentEntry;
    private GameStats.HandheldItemInterval currentInterval;
    private final GameStats.RoundData previewRound = new GameStats.RoundData();
    private final Map<String, ItemSnapshot> previewRenderSnapshots = new LinkedHashMap<String, ItemSnapshot>();
    private String previewCurrentKey = "";
    private GameStats.HandheldItemEntry previewCurrentEntry;
    private GameStats.HandheldItemInterval previewCurrentInterval;
    private String previewObservedKey = "";
    private boolean previewSuppressCurrentUntilChange;
    private boolean previewWasActive;

    HandheldItemTracker() {
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        long now = System.currentTimeMillis();
        if (!runtimeEnabledForMaster(GtbStructures.modEnabled)) {
            closeCurrent(now);
            clearRuntimeRound();
            clearPreview();
            previewWasActive = false;
            return;
        }
        try {
            if (InterfacePreview.isActive()) {
                if (!previewWasActive) {
                    beginPreview(now);
                    previewWasActive = true;
                }
                tickPreview(now);
            } else if (previewWasActive) {
                clearPreview();
                previewWasActive = false;
            }
            tick(now);
        } catch (Throwable ignored) {
            closeCurrent(now);
            closePreviewCurrent(now);
        }
    }

    @SubscribeEvent
    public void onRender(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.CHAT) return;
        if (!runtimeEnabledForMaster(GtbStructures.modEnabled)) return;
        try {
            renderOverlay();
        } catch (Throwable ignored) {
            // HUD overlays must never break vanilla rendering.
        }
    }

    void recordSnapshotForTest(GameStats.RoundData round, String builder, ItemSnapshot snapshot, long now) {
        ensureRound(round, builder == null ? "" : builder, now);
        recordSnapshot(round, snapshot, now);
    }

    void closeForTest(long now) {
        closeCurrent(now);
    }

    List<GameStats.HandheldItemEntry> visibleEntriesForTest(GameStats.RoundData round, int maxVisible) {
        return visibleEntries(round, maxVisible);
    }

    List<GameStats.HandheldItemEntry> renderEntriesTopToBottomForTest(GameStats.RoundData round, int maxVisible) {
        return renderEntriesTopToBottom(round, maxVisible);
    }

    void recordPreviewSnapshotForTest(ItemSnapshot snapshot, long now) {
        recordPreviewSnapshot(snapshot, now);
    }

    void closePreviewForTest(long now) {
        closePreviewCurrent(now);
    }

    void beginPreviewForTest(String observedKey, long now) {
        clearPreview();
        seedPreviewDefaults(now, false);
        previewObservedKey = observedKey == null ? "" : observedKey;
        previewSuppressCurrentUntilChange = true;
    }

    void tickPreviewSnapshotForTest(ItemSnapshot snapshot, long now) {
        tickPreviewSnapshot(snapshot, now);
    }

    List<GameStats.HandheldItemEntry> previewVisibleEntriesForTest(int maxVisible) {
        return visibleEntries(previewRound, maxVisible);
    }

    List<GameStats.HandheldItemEntry> previewRenderEntriesTopToBottomForTest(int maxVisible) {
        return renderEntriesTopToBottom(previewRound, maxVisible);
    }

    static String itemIdLineForTest(GameStats.HandheldItemEntry entry) {
        return itemIdLine(entry);
    }

    public static boolean runtimeEnabledForMaster(boolean modEnabled) {
        return modEnabled;
    }

    static String spawnedMobLineItemForTest(GameStats.HandheldItemEntry entry) {
        return spawnedMobLineItem(entry);
    }

    static float textScaleForTest() {
        return TEXT_SCALE;
    }

    static boolean textShadowForTest() {
        return TEXT_SHADOW;
    }

    static String itemKeyForTest(String registry, int metadata, String nbt, String modernKind) {
        return itemKey(registry, metadata, nbt, modernKind);
    }

    static String normalizedNbtForTest(NBTTagCompound tag) {
        return normalizedNbtForKey(tag);
    }

    static ItemSnapshot testSnapshot(String key, String name) {
        return new ItemSnapshot(key, name, "minecraft:" + key, 1, 0, 1,
                "item." + key, null, null, "", null, null);
    }

    static ItemSnapshot testSpawnEggSnapshot(String key, String name, int metadata,
                                              String modernKind, String nbt) {
        return new ItemSnapshot(key, name, "minecraft:spawn_egg", 383, metadata, 1,
                "item.monsterPlacer", modernKind,
                modernKind == null ? null : name.replace(" Spawn Egg", ""),
                nbt == null ? "" : nbt, null, null);
    }

    private void tick(long now) {
        Minecraft mc = Minecraft.getMinecraft();
        GameStats stats = GameStats.INSTANCE;
        GameStats.TrackingState state = stats.getState();
        GameStats.RoundData round = stats.getCurrentRound();

        if (state == GameStats.TrackingState.PAUSED) {
            closeCurrent(now);
            return;
        }
        if (!isRoundState(state) || round == null || round.builder == null || round.builder.trim().isEmpty()) {
            closeCurrent(now);
            clearRuntimeRound();
            return;
        }

        String builder = round.builder.trim();
        ensureRound(round, builder, now);

        if (state == GameStats.TrackingState.ROUND_END) {
            closeCurrent(now);
            return;
        }
        if (mc == null || mc.theWorld == null || mc.thePlayer == null) {
            closeCurrent(now);
            return;
        }

        EntityPlayer builderEntity = findPlayer(mc, builder);
        if (builderEntity == null) {
            closeCurrent(now);
            return;
        }

        ItemSnapshot snapshot = snapshotFor(builderEntity);
        if (snapshot == null) {
            closeCurrent(now);
            return;
        }
        recordSnapshot(round, snapshot, now);
    }

    private void tickPreview(long now) {
        if (previewRound.handheldItems.isEmpty()) seedPreviewDefaults(now);
    }

    private void tickPreviewSnapshot(ItemSnapshot snapshot, long now) {
        String key = snapshot == null || snapshot.key == null ? "" : snapshot.key;
        if (previewSuppressCurrentUntilChange) {
            if (key.equals(previewObservedKey)) {
                closePreviewCurrent(now);
                return;
            }
            previewSuppressCurrentUntilChange = false;
        }

        previewObservedKey = key;
        if (snapshot == null) {
            closePreviewCurrent(now);
            return;
        }
        recordPreviewSnapshot(snapshot, now);
    }

    private void ensureRound(GameStats.RoundData round, String builder, long now) {
        boolean sameRound = activeRound == round;
        boolean sameBuilder = activeBuilder != null && activeBuilder.equalsIgnoreCase(builder);
        if (sameRound && sameBuilder) return;

        closeCurrent(now);
        if (sameRound && !sameBuilder && round != null && round.handheldItems != null) {
            round.handheldItems.clear();
        }
        activeRound = round;
        activeBuilder = builder == null ? "" : builder;
        currentKey = "";
        currentEntry = null;
        currentInterval = null;
        renderSnapshots.clear();
    }

    private void clearRuntimeRound() {
        activeRound = null;
        activeBuilder = "";
        currentKey = "";
        currentEntry = null;
        currentInterval = null;
        renderSnapshots.clear();
    }

    private void clearPreview() {
        closePreviewCurrent(System.currentTimeMillis());
        previewRound.handheldItems.clear();
        previewCurrentKey = "";
        previewCurrentEntry = null;
        previewCurrentInterval = null;
        previewObservedKey = "";
        previewSuppressCurrentUntilChange = false;
        previewRenderSnapshots.clear();
    }

    private void beginPreview(long now) {
        clearPreview();
        seedPreviewDefaults(now);
    }

    private void seedPreviewDefaults(long now) {
        seedPreviewDefaults(now, true);
    }

    private void seedPreviewDefaults(long now, boolean includeRenderStacks) {
        ItemSnapshot[] defaults = previewDefaultSnapshotsTopToBottom(includeRenderStacks);
        int offset = 0;
        for (int i = defaults.length - 1; i >= 0; i--) {
            recordPreviewSnapshot(defaults[i], now + offset);
            offset++;
        }
        closePreviewCurrent(now + defaults.length);
    }

    private static ItemSnapshot[] previewDefaultSnapshotsTopToBottom(boolean includeRenderStacks) {
        return new ItemSnapshot[] {
                previewModernSnapshot(ModernEquipmentItem.spawnEgg(117)),
                previewStackSnapshot("Quartz Block", "minecraft:quartz_block", 155, 0,
                        "tile.quartzBlock", includeRenderStacks ? previewBlockStack(Blocks.quartz_block, 0) : null),
                previewStackSnapshot("White Wool", "minecraft:wool", 35, 0,
                        "tile.cloth.white", includeRenderStacks ? previewBlockStack(Blocks.wool, 0) : null),
                previewStackSnapshot("Diorite", "minecraft:stone", 1, 3,
                        "tile.stone.diorite", includeRenderStacks ? previewBlockStack(Blocks.stone, 3) : null),
                previewStackSnapshot("Polished Diorite", "minecraft:stone", 1, 4,
                        "tile.stone.dioriteSmooth", includeRenderStacks ? previewBlockStack(Blocks.stone, 4) : null),
                previewStackSnapshot("Andesite", "minecraft:stone", 1, 5,
                        "tile.stone.andesite", includeRenderStacks ? previewBlockStack(Blocks.stone, 5) : null),
                previewStackSnapshot("Dispenser", "minecraft:dispenser", 23, 0,
                        "tile.dispenser", includeRenderStacks ? previewBlockStack(Blocks.dispenser, 0) : null),
                previewStackSnapshot("Cyan Stained Clay", "minecraft:stained_hardened_clay", 159, 9,
                        "tile.clayHardenedStained.cyan",
                        includeRenderStacks ? previewBlockStack(Blocks.stained_hardened_clay, 9) : null),
                previewStackSnapshot("Gray Wool", "minecraft:wool", 35, 7,
                        "tile.cloth.gray", includeRenderStacks ? previewBlockStack(Blocks.wool, 7) : null),
                previewStackSnapshot("Obsidian", "minecraft:obsidian", 49, 0,
                        "tile.obsidian", includeRenderStacks ? previewBlockStack(Blocks.obsidian, 0) : null)
        };
    }

    private static ItemSnapshot previewStackSnapshot(String name, String registry, int numericId,
                                                     int metadata, String unlocalizedName,
                                                     ItemStack renderStack) {
        String key = itemKey(registry, metadata, "", null);
        return new ItemSnapshot(key, name, registry, numericId, metadata, 1,
                unlocalizedName, null, null, "", renderStack, null);
    }

    private static ItemStack previewBlockStack(net.minecraft.block.Block block, int metadata) {
        try {
            return new ItemStack(block, 1, metadata);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static ItemSnapshot previewModernSnapshot(ModernEquipmentItem modern) {
        return snapshotFromModern(modern);
    }

    private void recordSnapshot(GameStats.RoundData round, ItemSnapshot snapshot, long now) {
        if (round == null || snapshot == null || snapshot.key == null || snapshot.key.isEmpty()) return;

        if (snapshot.key.equals(currentKey) && currentEntry != null) {
            copySnapshot(snapshot, currentEntry, now);
            if (currentInterval != null) currentInterval.touch(now);
            renderSnapshots.put(snapshot.key, snapshot);
            return;
        }

        closeCurrent(now);
        GameStats.HandheldItemEntry entry = findEntry(round, snapshot.key);
        if (entry == null) {
            entry = new GameStats.HandheldItemEntry(snapshot.key);
            round.handheldItems.add(entry);
        } else {
            round.handheldItems.remove(entry);
            round.handheldItems.add(entry);
        }

        mergeInitialSpawnEggPlaceholder(round, snapshot, entry);
        copySnapshot(snapshot, entry, now);
        GameStats.HandheldItemInterval interval = new GameStats.HandheldItemInterval(now);
        entry.intervals.add(interval);
        currentKey = snapshot.key;
        currentEntry = entry;
        currentInterval = interval;
        renderSnapshots.put(snapshot.key, snapshot);
    }

    private void mergeInitialSpawnEggPlaceholder(GameStats.RoundData round,
                                                  ItemSnapshot snapshot,
                                                  GameStats.HandheldItemEntry resolvedEntry) {
        if (!isResolvedSpawnEgg(snapshot) || round == null || round.handheldItems == null) return;

        GameStats.HandheldItemEntry placeholder = null;
        for (GameStats.HandheldItemEntry entry : round.handheldItems) {
            if (entry == null || entry == resolvedEntry) continue;
            if (isResolvedSpawnEgg(entry)) return;
            if (!isPlaceholderSpawnEgg(entry)) continue;
            if (placeholder != null) return;
            placeholder = entry;
        }
        if (placeholder == null) return;

        resolvedEntry.intervals.addAll(0, placeholder.intervals);
        if (resolvedEntry.firstSeen == 0L
                || (placeholder.firstSeen > 0L && placeholder.firstSeen < resolvedEntry.firstSeen)) {
            resolvedEntry.firstSeen = placeholder.firstSeen;
        }
        resolvedEntry.lastSeen = Math.max(resolvedEntry.lastSeen, placeholder.lastSeen);
        round.handheldItems.remove(placeholder);
        renderSnapshots.remove(placeholder.key);
    }

    private static boolean isResolvedSpawnEgg(ItemSnapshot snapshot) {
        return snapshot != null
                && "spawn_egg".equals(snapshot.modernKind)
                && snapshot.metadata > 0;
    }

    private static boolean isResolvedSpawnEgg(GameStats.HandheldItemEntry entry) {
        return entry != null
                && "spawn_egg".equals(entry.modernKind)
                && entry.metadata > 0;
    }

    private static boolean isPlaceholderSpawnEgg(GameStats.HandheldItemEntry entry) {
        if (entry == null
                || !"minecraft:spawn_egg".equals(entry.registryName)
                || entry.metadata != 0
                || entry.modernKind != null) {
            return false;
        }
        String nbt = entry.nbt == null ? "" : entry.nbt.trim();
        return nbt.isEmpty()
                || "{}".equals(nbt)
                || "{EntityTag:{id:0}}".equals(nbt);
    }

    private void recordPreviewSnapshot(ItemSnapshot snapshot, long now) {
        if (snapshot == null || snapshot.key == null || snapshot.key.isEmpty()) return;

        if (snapshot.key.equals(previewCurrentKey) && previewCurrentEntry != null) {
            copySnapshot(snapshot, previewCurrentEntry, now);
            if (previewCurrentInterval != null) previewCurrentInterval.touch(now);
            previewRenderSnapshots.put(snapshot.key, snapshot);
            return;
        }

        closePreviewCurrent(now);
        GameStats.HandheldItemEntry entry = findEntry(previewRound, snapshot.key);
        if (entry == null) {
            entry = new GameStats.HandheldItemEntry(snapshot.key);
            previewRound.handheldItems.add(entry);
        } else {
            previewRound.handheldItems.remove(entry);
            previewRound.handheldItems.add(entry);
        }

        copySnapshot(snapshot, entry, now);
        GameStats.HandheldItemInterval interval = new GameStats.HandheldItemInterval(now);
        entry.intervals.add(interval);
        previewCurrentKey = snapshot.key;
        previewCurrentEntry = entry;
        previewCurrentInterval = interval;
        previewRenderSnapshots.put(snapshot.key, snapshot);
    }

    private void closeCurrent(long now) {
        if (currentInterval != null) currentInterval.touch(now);
        if (currentEntry != null) currentEntry.lastSeen = now;
        currentKey = "";
        currentEntry = null;
        currentInterval = null;
    }

    private void closePreviewCurrent(long now) {
        if (previewCurrentInterval != null) previewCurrentInterval.touch(now);
        if (previewCurrentEntry != null) previewCurrentEntry.lastSeen = now;
        previewCurrentKey = "";
        previewCurrentEntry = null;
        previewCurrentInterval = null;
    }

    private static void copySnapshot(ItemSnapshot snapshot, GameStats.HandheldItemEntry entry, long now) {
        if (entry.intervals.isEmpty()) entry.firstSeen = now;
        entry.lastSeen = now;
        entry.name = snapshot.name;
        entry.registryName = snapshot.registryName;
        entry.numericId = snapshot.numericId;
        entry.metadata = snapshot.metadata;
        entry.stackSize = snapshot.stackSize;
        entry.unlocalizedName = snapshot.unlocalizedName;
        entry.modernKind = snapshot.modernKind;
        entry.modernName = snapshot.modernName;
        entry.nbt = snapshot.nbt;
    }

    private static GameStats.HandheldItemEntry findEntry(GameStats.RoundData round, String key) {
        if (round == null || round.handheldItems == null) return null;
        for (GameStats.HandheldItemEntry entry : round.handheldItems) {
            if (entry != null && key.equals(entry.key)) return entry;
        }
        return null;
    }

    private static EntityPlayer findPlayer(Minecraft mc, String ign) {
        if (mc == null || mc.theWorld == null || ign == null) return null;
        for (Object obj : mc.theWorld.playerEntities) {
            if (!(obj instanceof EntityPlayer)) continue;
            EntityPlayer player = (EntityPlayer) obj;
            if (player.getName() != null && player.getName().equalsIgnoreCase(ign)) return player;
        }
        return null;
    }

    private static boolean isRoundState(GameStats.TrackingState state) {
        return state == GameStats.TrackingState.ROUND_PRE
                || state == GameStats.TrackingState.ROUND_BUILD
                || state == GameStats.TrackingState.ROUND_END;
    }

    private static ItemSnapshot snapshotFor(EntityPlayer player) {
        ItemStack stack = player.getHeldItem();
        ModernEquipmentItem modern = decodedModernItem(stack);
        if (modern == null) {
            modern = ModernEquipmentItemPatch.INSTANCE.heldItem(player, stack);
        }
        if (stack == null && modern == null) return null;

        if (stack != null && stack.getItem() != null) {
            return snapshotFromStack(stack, modern);
        }
        return snapshotFromModern(modern);
    }

    private static ModernEquipmentItem decodedModernItem(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return null;
        if (stack.getItem() == Items.spawn_egg) {
            return ModernEquipmentItem.spawnEgg(stack.getMetadata());
        }
        if (stack.getItem() == Items.skull && stack.getMetadata() == 5) {
            return ModernEquipmentItem.dragonHead();
        }
        return null;
    }

    private static ItemSnapshot snapshotFromStack(ItemStack stack, ModernEquipmentItem modern) {
        Item item = stack.getItem();
        int numericId = Item.getIdFromItem(item);
        int metadata = modernMetadataOrFallback(modern, stack.getMetadata());
        String registry = item.getRegistryName();
        if (registry == null || registry.isEmpty()) registry = String.valueOf(numericId);
        String modernKind = modern == null ? null : modern.kind.name().toLowerCase(Locale.ROOT);
        String nbt = normalizedNbtForKey(stack, modern);
        String modernName = modernDisplayName(modern);
        String key = itemKey(registry, metadata, nbt, modernKind);
        String displayName = modernName == null ? stack.getDisplayName() : modernName;
        return new ItemSnapshot(key, displayName, registry, numericId, metadata,
                stack.stackSize, stack.getUnlocalizedName(), modernKind, modernName, nbt,
                stack.copy(), modern);
    }

    private static int modernMetadataOrFallback(ModernEquipmentItem modern, int fallback) {
        if (modern == null) return fallback;
        if (modern.kind == ModernEquipmentItem.Kind.SPAWN_EGG && modern.spawnEgg != null) {
            return modern.spawnEgg.damage;
        }
        if (modern.kind == ModernEquipmentItem.Kind.BED) {
            return modern.colorMeta;
        }
        return fallback;
    }

    private static ItemSnapshot snapshotFromModern(ModernEquipmentItem modern) {
        if (modern == null) return null;
        String modernKind = modern.kind.name().toLowerCase(Locale.ROOT);
        String modernName = modernDisplayName(modern);
        int metadata = modern.kind == ModernEquipmentItem.Kind.SPAWN_EGG && modern.spawnEgg != null
                ? modern.spawnEgg.damage : modern.colorMeta;
        String registry = modernRegistryName(modern);
        String key = itemKey(registry, metadata, "", modernKind);
        return new ItemSnapshot(key, modernName, registry, modernNumericId(modern), metadata, 1,
                null, modernKind, modernName, "", null, modern);
    }

    private static String itemKey(String registry, int metadata, String nbt, String modernKind) {
        if (modernKind != null && !modernKind.isEmpty()) {
            return "modern:" + modernKind + ":" + metadata;
        }
        String id = registry == null || registry.isEmpty() ? "unknown" : registry;
        return "stack:" + id + ":" + metadata + ":" + (nbt == null ? "" : nbt);
    }

    private static String normalizedNbtForKey(ItemStack stack, ModernEquipmentItem modern) {
        if (stack == null || stack.getTagCompound() == null) return "";
        if (modern != null) return "";
        return normalizedNbtForKey(stack.getTagCompound());
    }

    private static String normalizedNbtForKey(NBTTagCompound tag) {
        if (tag == null) return "";
        NBTTagCompound cleaned = (NBTTagCompound) tag.copy();
        cleaned.removeTag("overrideMeta");
        NBTBase attributes = cleaned.getTag("AttributeModifiers");
        if (attributes != null && "[]".equals(attributes.toString())) {
            cleaned.removeTag("AttributeModifiers");
        }
        return cleaned.hasNoTags() ? "" : cleaned.toString();
    }

    private static String modernRegistryName(ModernEquipmentItem modern) {
        if (modern == null) return null;
        if (modern.kind == ModernEquipmentItem.Kind.SPAWN_EGG) return "minecraft:spawn_egg";
        if (modern.kind == ModernEquipmentItem.Kind.BED) return "minecraft:bed";
        if (modern.kind == ModernEquipmentItem.Kind.DRAGON_HEAD) return "minecraft:skull";
        return null;
    }

    private static int modernNumericId(ModernEquipmentItem modern) {
        if (modern == null) return -1;
        if (modern.kind == ModernEquipmentItem.Kind.SPAWN_EGG) return 383;
        if (modern.kind == ModernEquipmentItem.Kind.BED) return 355;
        if (modern.kind == ModernEquipmentItem.Kind.DRAGON_HEAD) return 397;
        return -1;
    }

    private static String modernDisplayName(ModernEquipmentItem item) {
        if (item == null) return null;
        if (item.kind == ModernEquipmentItem.Kind.SPAWN_EGG && item.spawnEgg != null) {
            return titleCase(item.spawnEgg.textureName) + " Spawn Egg";
        }
        if (item.kind == ModernEquipmentItem.Kind.BED) {
            return titleCase(colorName(item.colorMeta)) + " Bed";
        }
        if (item.kind == ModernEquipmentItem.Kind.DRAGON_HEAD) {
            return "Dragon Head";
        }
        return item.kind.name();
    }

    private static String titleCase(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        String[] parts = raw.replace('_', ' ').split(" ");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) out.append(part.substring(1).toLowerCase(Locale.ROOT));
        }
        return out.toString();
    }

    private static String colorName(int meta) {
        switch (meta) {
            case 0: return "white";
            case 1: return "orange";
            case 2: return "magenta";
            case 3: return "light blue";
            case 4: return "yellow";
            case 5: return "lime";
            case 6: return "pink";
            case 7: return "gray";
            case 8: return "light gray";
            case 9: return "cyan";
            case 10: return "purple";
            case 11: return "blue";
            case 12: return "brown";
            case 13: return "green";
            case 14: return "red";
            case 15: return "black";
            default: return "colored";
        }
    }

    private void renderOverlay() {
        if (!GtbStructures.handheldItemOverlay) return;
        if (InterfacePreview.isActive()) {
            renderPreviewOverlay(false);
            return;
        }
        GameStats stats = GameStats.INSTANCE;
        if (!stats.shouldRenderRoundOverlays()) return;
        GameStats.RoundData round = stats.getCurrentRound();
        List<GameStats.HandheldItemEntry> visible = visibleEntries(round, visibleMax());
        if (visible.isEmpty()) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.fontRendererObj == null) return;
        ScaledResolution sr = new ScaledResolution(mc);
        FontRenderer fr = mc.fontRendererObj;

        GtbStructures.migrateHandheldPanelRatios(sr.getScaledWidth(), sr.getScaledHeight());
        PanelSize panel = panelSize(visible, fr, sr.getScaledWidth(), sr.getScaledHeight());
        renderPanel(mc, sr, fr, visible, panel, renderSnapshots, false);
    }

    public void collectPreviewLabel() {
        if (!runtimeEnabledForMaster(GtbStructures.modEnabled)
                || !InterfacePreview.isActive()
                || !GtbStructures.handheldItemOverlay) return;
        renderPreviewOverlay(true);
    }

    private void renderPreviewOverlay(boolean layoutOnly) {
        List<GameStats.HandheldItemEntry> visible = visibleEntries(previewRound, visibleMax());
        if (visible.isEmpty()) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.fontRendererObj == null) return;
        ScaledResolution sr = new ScaledResolution(mc);
        FontRenderer fr = mc.fontRendererObj;

        GtbStructures.migrateHandheldPanelRatios(sr.getScaledWidth(), sr.getScaledHeight());
        PanelSize panel = panelSize(visible, fr, sr.getScaledWidth(), sr.getScaledHeight());
        renderPanel(mc, sr, fr, visible, panel, previewRenderSnapshots, layoutOnly);
    }

    private void renderPanel(Minecraft mc, ScaledResolution sr, FontRenderer fr,
                             List<GameStats.HandheldItemEntry> visible, PanelSize panel,
                             Map<String, ItemSnapshot> snapshots, boolean layoutOnly) {
        float panelScale = HudPanelScale.normalize(GtbStructures.handheldItemScale);
        int scaledWidth = HudPanelScale.scaledPixels(panel.width, panelScale);
        int scaledHeight = HudPanelScale.scaledPixels(panel.height, panelScale);
        int x = HudPosition.left(GtbStructures.handheldItemPosX,
                sr.getScaledWidth(), scaledWidth, 2);
        int bottom = HudPosition.bottom(GtbStructures.handheldItemPosY,
                sr.getScaledHeight(), scaledHeight, 2);
        int top = bottom - scaledHeight;
        if (layoutOnly) {
            PreviewLayoutEditor.register("handhelditem", x, top, scaledWidth, scaledHeight,
                    PreviewLayoutEditor.HANDHELD);
            return;
        }
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x, top, 0.0F);
            GlStateManager.scale(panelScale, panelScale, 1.0F);
            drawBackgroundIfEnabled(0, 0, panel.width, panel.height);
            renderEntries(mc, fr, visible, 0, 0, panel, snapshots);
        } finally {
            GlStateManager.popMatrix();
        }
    }

    private static void drawBackgroundIfEnabled(int x, int y, int width, int height) {
        if (!GtbStructures.handheldItemBackground) return;
        Gui.drawRect(x - 1, y - 1, x + width + 1, y + height + 1, ScoreboardLayout.BACKGROUND_COLOR);
    }

    private void renderEntries(Minecraft mc, FontRenderer fr, List<GameStats.HandheldItemEntry> visible,
                               int x, int y, PanelSize panel, Map<String, ItemSnapshot> snapshots) {
        if (GtbStructures.handheldItemVerticalLayout) {
            renderVerticalEntries(mc, fr, visible, x, y, panel, snapshots);
        } else {
            renderHorizontalEntries(mc, visible, x, y, panel, snapshots);
        }
    }

    private void renderVerticalEntries(Minecraft mc, FontRenderer fr, List<GameStats.HandheldItemEntry> visible,
                                       int x, int y, PanelSize panel, Map<String, ItemSnapshot> snapshots) {
        List<GameStats.HandheldItemEntry> ordered = verticalRenderEntries(visible);
        int contentHeight = verticalContentHeight(ordered, fr, panel.width);
        int rowY = y + (GtbStructures.handheldItemVerticalNewestAtBottom ? 0 : panel.height - contentHeight);
        for (GameStats.HandheldItemEntry entry : ordered) {
            int entryHeight = verticalEntryHeight(entry, fr, panel.width);
            renderVerticalEntry(mc, fr, entry, x, rowY, panel.width, entryHeight, snapshots);
            rowY += entryHeight;
        }
    }

    private void renderHorizontalEntries(Minecraft mc, List<GameStats.HandheldItemEntry> visible,
                                         int x, int y, PanelSize panel, Map<String, ItemSnapshot> snapshots) {
        int columns = Math.max(1, Math.min(panel.width / ROW_HEIGHT, visible.size()));
        int rows = Math.max(1, (visible.size() + columns - 1) / columns);
        int gridX = x + (GtbStructures.handheldItemHorizontalNewestAtRight ? 0
                : panel.width - columns * ROW_HEIGHT);
        int gridY = y + (GtbStructures.handheldItemHorizontalNewestAtBottom ? 0
                : panel.height - rows * ROW_HEIGHT);
        for (int i = 0; i < visible.size(); i++) {
            int column = i % columns;
            int row = i / columns;
            if (GtbStructures.handheldItemHorizontalNewestAtRight) column = columns - 1 - column;
            if (GtbStructures.handheldItemHorizontalNewestAtBottom) row = rows - 1 - row;
            GameStats.HandheldItemEntry entry = visible.get(visible.size() - 1 - i);
            renderIcon(mc, snapshots.get(entry.key), gridX + column * ROW_HEIGHT + 1, gridY + row * ROW_HEIGHT + 1);
        }
    }

    private void renderVerticalEntry(Minecraft mc, FontRenderer fr, GameStats.HandheldItemEntry entry,
                                     int x, int y, int width, int height, Map<String, ItemSnapshot> snapshots) {
        ItemSnapshot snapshot = snapshots.get(entry.key);
        int iconX = x;
        int iconY = y + (height - ICON_SIZE) / 2;
        renderIcon(mc, snapshot, iconX, iconY);

        int textX = iconX + ICON_SIZE + TEXT_GAP;
        int textWidth = width - (textX - x) - 3;
        List<String> lines = captionLines(entry, fr, textWidth);
        int lineY = y + Math.max(1, (height - lines.size() * 8) / 2);
        for (int i = 0; i < lines.size(); i++) {
            int color = i == 0 && GtbStructures.handheldItemCaptionName ? 0xFFFFFF : 0xC8C8C8;
            drawScaledString(fr, lines.get(i), textX, lineY, color);
            lineY += 8;
        }
    }

    private static List<String> captionLines(GameStats.HandheldItemEntry entry, FontRenderer fr, int width) {
        ArrayList<String> out = new ArrayList<String>();
        int usableWidth = Math.max(8, width);
        if (GtbStructures.handheldItemCaptionName) out.add(ellipsizeCaption(fr,
                entry.name == null || entry.name.isEmpty() ? "(unknown item)" : entry.name, usableWidth));
        ArrayList<String> secondary = new ArrayList<String>();
        if (GtbStructures.handheldItemCaptionId) secondary.add(itemIdLine(entry));
        if (GtbStructures.handheldItemCaptionMetadata) secondary.add(String.valueOf(entry.metadata));
        if (GtbStructures.handheldItemCaptionSpawnEggMob) {
            String mob = spawnedMobLineItem(entry);
            if (mob != null) secondary.add(mob);
        }
        packSecondaryCaptionTokens(out, secondary, fr, usableWidth);
        return out;
    }

    private static void packSecondaryCaptionTokens(List<String> out, List<String> tokens, FontRenderer fr, int width) {
        String row = "";
        for (String raw : tokens) {
            String token = ellipsizeCaption(fr, raw, width);
            String combined = row.isEmpty() ? token : row + " | " + token;
            if (!row.isEmpty() && fr.getStringWidth(combined) > width) {
                out.add(row);
                row = token;
            } else {
                row = combined;
            }
        }
        if (!row.isEmpty()) out.add(row);
    }

    private static String ellipsizeCaption(FontRenderer fr, String text, int width) {
        String value = text == null ? "" : text;
        if (fr.getStringWidth(value) <= width) return value;
        String suffix = "...";
        if (fr.getStringWidth(suffix) > width) {
            suffix = ".";
            if (fr.getStringWidth(suffix) > width) return "";
        }
        int end = value.length();
        while (end > 0 && fr.getStringWidth(value.substring(0, end)) + fr.getStringWidth(suffix) > width) end--;
        return end <= 0 ? suffix : value.substring(0, end) + suffix;
    }

    private static int verticalEntryHeight(GameStats.HandheldItemEntry entry, FontRenderer fr, int width) {
        return Math.max(ROW_HEIGHT, captionLines(entry, fr, width - ICON_SIZE - TEXT_GAP - 3).size() * 8 + 1);
    }

    private static int verticalContentHeight(List<GameStats.HandheldItemEntry> entries, FontRenderer fr, int width) {
        int height = 0;
        for (GameStats.HandheldItemEntry entry : entries) height += verticalEntryHeight(entry, fr, width);
        return height;
    }

    private static List<GameStats.HandheldItemEntry> verticalRenderEntries(List<GameStats.HandheldItemEntry> visible) {
        ArrayList<GameStats.HandheldItemEntry> ordered = new ArrayList<GameStats.HandheldItemEntry>(visible);
        if (GtbStructures.handheldItemVerticalNewestAtBottom) return ordered;
        java.util.Collections.reverse(ordered);
        return ordered;
    }

    private static PanelSize panelSize(List<GameStats.HandheldItemEntry> visible, FontRenderer fr,
                                       int screenWidth, int screenHeight) {
        int width = panelWidthPixels(GtbStructures.handheldItemPanelWidthRatio, screenWidth);
        if (GtbStructures.handheldItemVerticalLayout) {
            int height = Math.max(panelHeightPixels(GtbStructures.handheldItemPanelHeightRatio, screenHeight),
                    verticalContentHeight(visible, fr, width));
            return new PanelSize(width, height);
        }
        int columns = Math.max(1, Math.min(visible.size(), width / ROW_HEIGHT));
        int rows = Math.max(1, (visible.size() + columns - 1) / columns);
        int height = Math.max(panelHeightPixels(GtbStructures.handheldItemPanelHeightRatio, screenHeight), rows * ROW_HEIGHT);
        return new PanelSize(width, height);
    }

    private static String itemIdLine(GameStats.HandheldItemEntry entry) {
        String id = entry.registryName != null && !entry.registryName.isEmpty()
                ? entry.registryName : (entry.numericId >= 0 ? String.valueOf(entry.numericId) : entry.modernKind);
        if (id == null || id.isEmpty()) id = "unknown";
        return id;
    }

    private static String spawnedMobLineItem(GameStats.HandheldItemEntry entry) {
        if (entry == null || !("spawn_egg".equals(entry.modernKind)
                || "minecraft:spawn_egg".equals(entry.registryName))) return null;
        ModernSpawnEggData egg = ModernSpawnEggData.byDamage(entry.metadata);
        if (egg != null) return titleCase(egg.textureName);
        String itemName = entry.modernName == null || entry.modernName.trim().isEmpty()
                ? entry.name : entry.modernName;
        if (itemName == null) return null;
        String trimmed = StringUtils.stripControlCodes(itemName).trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        String suffix = " spawn egg";
        if (lower.endsWith(suffix) && trimmed.length() > suffix.length()) {
            return trimmed.substring(0, trimmed.length() - suffix.length()).trim();
        }
        String prefix = "spawn ";
        if (lower.startsWith(prefix) && trimmed.length() > prefix.length()
                && !"spawn egg".equals(lower)) {
            return trimmed.substring(prefix.length()).trim();
        }
        return null;
    }

    private static void drawScaledString(FontRenderer fr, String text, int x, int y, int color) {
        if (TEXT_SCALE == 1.0F) {
            if (TEXT_SHADOW) {
                fr.drawStringWithShadow(text, x, y, color);
            } else {
                fr.drawString(text, x, y, color);
            }
            return;
        }
        GlStateManager.pushMatrix();
        GlStateManager.scale(TEXT_SCALE, TEXT_SCALE, 1.0F);
        if (TEXT_SHADOW) {
            fr.drawStringWithShadow(text, x / TEXT_SCALE, y / TEXT_SCALE, color);
        } else {
            fr.drawString(text, (int) (x / TEXT_SCALE), (int) (y / TEXT_SCALE), color);
        }
        GlStateManager.popMatrix();
    }

    private static void renderIcon(Minecraft mc, ItemSnapshot snapshot, int x, int y) {
        if (snapshot != null && snapshot.modernItem != null) {
            if (snapshot.modernItem.kind == ModernEquipmentItem.Kind.SPAWN_EGG) {
                if (!snapshot.modernItem.spawnEgg.vanilla18 || "new".equals(GtbStructures.spawnEggSkin)
                        || snapshot.renderStack == null) {
                    drawSpawnEggIcon(mc, snapshot.modernItem.spawnEgg, x, y, ICON_SIZE);
                    return;
                }
            }
            if (snapshot.modernItem.kind == ModernEquipmentItem.Kind.BED) {
                drawFlatIcon(x, y, bedColor(snapshot.modernItem.colorMeta), "B");
                return;
            }
            if (snapshot.modernItem.kind == ModernEquipmentItem.Kind.DRAGON_HEAD) {
                ModernEquipmentRenderPatch.ModernDragonHeadRenderer.renderGuiIcon(mc, x, y, ICON_SIZE);
                return;
            }
        }
        if (snapshot != null && snapshot.renderStack != null && snapshot.renderStack.getItem() != null) {
            RenderItem renderItem = mc.getRenderItem();
            RenderHelper.enableGUIStandardItemLighting();
            GlStateManager.enableRescaleNormal();
            renderItem.renderItemAndEffectIntoGUI(snapshot.renderStack, x, y);
            renderItem.renderItemOverlays(mc.fontRendererObj, snapshot.renderStack, x, y);
            GlStateManager.disableRescaleNormal();
            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableLighting();
            return;
        }
        drawFlatIcon(x, y, 0x555555, "?");
    }

    private static void drawSpawnEggIcon(Minecraft mc, ModernSpawnEggData egg, int x, int y, int size) {
        if (egg == null) {
            drawFlatIcon(x, y, 0x555555, "?");
            return;
        }

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        if ("new".equals(GtbStructures.spawnEggSkin)) {
            mc.getTextureManager().bindTexture(egg.newTexture);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            Gui.drawModalRectWithCustomSizedTexture(x, y, 0, 0, size, size, size, size);
        } else {
            mc.getTextureManager().bindTexture(CLASSIC_EGG_BASE);
            setColor(egg.classicBaseColor);
            Gui.drawModalRectWithCustomSizedTexture(x, y, 0, 0, size, size, size, size);
            mc.getTextureManager().bindTexture(CLASSIC_EGG_OVERLAY);
            setColor(egg.classicOverlayColor);
            Gui.drawModalRectWithCustomSizedTexture(x, y, 0, 0, size, size, size, size);
        }
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private static void drawFlatIcon(int x, int y, int rgb, String label) {
        Gui.drawRect(x, y, x + ICON_SIZE, y + ICON_SIZE, 0xFF000000 | rgb);
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.fontRendererObj != null && label != null && !label.isEmpty()) {
            String ch = label.substring(0, 1);
            int tx = x + (ICON_SIZE - mc.fontRendererObj.getStringWidth(ch)) / 2;
            mc.fontRendererObj.drawStringWithShadow(ch, tx, y + 4, 0xFFFFFF);
        }
    }

    private static void setColor(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255.0F;
        float g = ((rgb >> 8) & 0xFF) / 255.0F;
        float b = (rgb & 0xFF) / 255.0F;
        GlStateManager.color(r, g, b, 1.0F);
    }

    private static int bedColor(int meta) {
        switch (meta) {
            case 0: return 0xF9FFFE;
            case 1: return 0xF9801D;
            case 2: return 0xC74EBD;
            case 3: return 0x3AB3DA;
            case 4: return 0xFED83D;
            case 5: return 0x80C71F;
            case 6: return 0xF38BAA;
            case 7: return 0x474F52;
            case 8: return 0x9D9D97;
            case 9: return 0x169C9C;
            case 10: return 0x8932B8;
            case 11: return 0x3C44AA;
            case 12: return 0x835432;
            case 13: return 0x5E7C16;
            case 14: return 0xB02E26;
            case 15: return 0x1D1D21;
            default: return 0x777777;
        }
    }

    private static List<GameStats.HandheldItemEntry> visibleEntries(GameStats.RoundData round, int maxVisible) {
        ArrayList<GameStats.HandheldItemEntry> out = new ArrayList<GameStats.HandheldItemEntry>();
        if (round == null || round.handheldItems == null || round.handheldItems.isEmpty()) return out;
        int max = Math.max(MIN_VISIBLE, Math.min(MAX_VISIBLE, maxVisible));
        int start = Math.max(0, round.handheldItems.size() - max);
        for (int i = start; i < round.handheldItems.size(); i++) {
            GameStats.HandheldItemEntry entry = round.handheldItems.get(i);
            if (entry != null) out.add(entry);
        }
        return out;
    }

    private static List<GameStats.HandheldItemEntry> renderEntriesTopToBottom(GameStats.RoundData round, int maxVisible) {
        List<GameStats.HandheldItemEntry> visible = visibleEntries(round, maxVisible);
        ArrayList<GameStats.HandheldItemEntry> out = new ArrayList<GameStats.HandheldItemEntry>(visible.size());
        for (int i = visible.size() - 1; i >= 0; i--) {
            out.add(visible.get(i));
        }
        return out;
    }

    private static int visibleMax() {
        return Math.max(MIN_VISIBLE, Math.min(MAX_VISIBLE, GtbStructures.handheldItemMaxVisible));
    }

    public static int clampMaxVisible(int value) {
        return Math.max(MIN_VISIBLE, Math.min(MAX_VISIBLE, value));
    }

    public static float clampPanelRatio(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return 0F;
        return Math.max(0F, Math.min(1F, value));
    }

    public static int panelWidthPixels(float ratio, int screenWidth) {
        return panelPixels(ratio, screenWidth, MIN_PANEL_WIDTH);
    }

    public static int panelHeightPixels(float ratio, int screenHeight) {
        return panelPixels(ratio, screenHeight, MIN_PANEL_HEIGHT);
    }

    public static float panelWidthRatioForPixels(int pixels, int screenWidth) {
        return panelRatioForPixels(pixels, screenWidth, MIN_PANEL_WIDTH);
    }

    public static float panelHeightRatioForPixels(int pixels, int screenHeight) {
        return panelRatioForPixels(pixels, screenHeight, MIN_PANEL_HEIGHT);
    }

    private static int panelPixels(float ratio, int screenDimension, int minimum) {
        int usable = Math.max(1, screenDimension - PANEL_MARGIN * 2);
        int requested = Math.round(clampPanelRatio(ratio) * Math.max(1, screenDimension));
        return Math.min(usable, Math.max(Math.min(minimum, usable), requested));
    }

    private static float panelRatioForPixels(int pixels, int screenDimension, int minimum) {
        int usable = Math.max(1, screenDimension - PANEL_MARGIN * 2);
        int clamped = Math.min(usable, Math.max(Math.min(minimum, usable), pixels));
        if (clamped >= usable) return 1F;
        return clampPanelRatio((float) clamped / (float) Math.max(1, screenDimension));
    }

    private static final class PanelSize {
        final int width;
        final int height;

        PanelSize(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }

    static final class ItemSnapshot {
        final String key;
        final String name;
        final String registryName;
        final int numericId;
        final int metadata;
        final int stackSize;
        final String unlocalizedName;
        final String modernKind;
        final String modernName;
        final String nbt;
        final ItemStack renderStack;
        final ModernEquipmentItem modernItem;

        private ItemSnapshot(String key, String name, String registryName, int numericId, int metadata,
                             int stackSize, String unlocalizedName, String modernKind, String modernName,
                             String nbt, ItemStack renderStack, ModernEquipmentItem modernItem) {
            this.key = key;
            this.name = name;
            this.registryName = registryName;
            this.numericId = numericId;
            this.metadata = metadata;
            this.stackSize = stackSize;
            this.unlocalizedName = unlocalizedName;
            this.modernKind = modernKind;
            this.modernName = modernName;
            this.nbt = nbt;
            this.renderStack = renderStack;
            this.modernItem = modernItem;
        }
    }
}
