package com.gtbstructures.items;

import com.gtbstructures.ModernRuntimeBridge;
import com.gtbstructures.stats.GameStats;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Platform-neutral handheld item history logic. Loader code can feed snapshots
 * from live held-item APIs and render the returned entries however it needs.
 */
public final class HandheldItemTracker {
    public static final HandheldItemTracker INSTANCE = new HandheldItemTracker();

    private static final int MIN_VISIBLE = 2;
    private static final int MAX_VISIBLE = 10;
    public static final int PANEL_WIDTH = 198;
    public static final int ROW_HEIGHT = 18;
    public static final int ICON_SIZE = 16;
    public static final int TEXT_GAP = 4;
    private static final float TEXT_SCALE = 1.0F;
    private static final boolean TEXT_SHADOW = true;

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

    public HandheldItemTracker() {
    }

    public void recordSnapshotForTest(GameStats.RoundData round, String builder, ItemSnapshot snapshot, long now) {
        ensureRound(round, builder == null ? "" : builder, now);
        recordSnapshot(round, snapshot, now);
    }

    public void closeForTest(long now) {
        closeCurrent(now);
    }

    public void tickRound(GameStats.TrackingState state, GameStats.RoundData round,
                          String builder, ItemSnapshot snapshot, long now) {
        if (state == GameStats.TrackingState.PAUSED) {
            closeCurrent(now);
            return;
        }
        if (!isRoundState(state) || round == null || builder == null || builder.trim().isEmpty()) {
            closeCurrent(now);
            clearRuntimeRound();
            return;
        }

        ensureRound(round, builder.trim(), now);
        if (state == GameStats.TrackingState.ROUND_END || snapshot == null) {
            closeCurrent(now);
            return;
        }
        recordSnapshot(round, snapshot, now);
    }

    public void beginPreview(String observedKey, long now) {
        clearPreview(now);
        seedPreviewDefaults(now);
        previewObservedKey = observedKey == null ? "" : observedKey;
        previewSuppressCurrentUntilChange = true;
    }

    public void tickPreview(ItemSnapshot snapshot, long now) {
        if (previewRound.handheldItems.isEmpty()) seedPreviewDefaults(now);
        tickPreviewSnapshot(snapshot, now);
    }

    public void endPreview(long now) {
        clearPreview(now);
    }

    public List<GameStats.HandheldItemEntry> visibleEntriesForTest(GameStats.RoundData round, int maxVisible) {
        return visibleEntries(round, maxVisible);
    }

    public List<GameStats.HandheldItemEntry> renderEntriesTopToBottomForTest(GameStats.RoundData round, int maxVisible) {
        return renderEntriesTopToBottom(round, maxVisible);
    }

    public List<GameStats.HandheldItemEntry> renderEntries(GameStats.RoundData round, int maxVisible) {
        return renderEntriesTopToBottom(round, maxVisible);
    }

    public List<GameStats.HandheldItemEntry> previewRenderEntries(int maxVisible) {
        return renderEntriesTopToBottom(previewRound, maxVisible);
    }

    public void recordPreviewSnapshotForTest(ItemSnapshot snapshot, long now) {
        recordPreviewSnapshot(snapshot, now);
    }

    public void closePreviewForTest(long now) {
        closePreviewCurrent(now);
    }

    public void beginPreviewForTest(String observedKey, long now) {
        clearPreview(now);
        seedPreviewDefaults(now);
        previewObservedKey = observedKey == null ? "" : observedKey;
        previewSuppressCurrentUntilChange = true;
    }

    public void tickPreviewSnapshotForTest(ItemSnapshot snapshot, long now) {
        tickPreviewSnapshot(snapshot, now);
    }

    public List<GameStats.HandheldItemEntry> previewVisibleEntriesForTest(int maxVisible) {
        return visibleEntries(previewRound, maxVisible);
    }

    public List<GameStats.HandheldItemEntry> previewRenderEntriesTopToBottomForTest(int maxVisible) {
        return renderEntriesTopToBottom(previewRound, maxVisible);
    }

    public static String itemIdLineForTest(GameStats.HandheldItemEntry entry) {
        return itemIdLine(entry);
    }

    public static String itemIdLine(GameStats.HandheldItemEntry entry) {
        return itemIdLineInternal(entry);
    }

    public static float textScaleForTest() {
        return TEXT_SCALE;
    }

    public static boolean textShadowForTest() {
        return TEXT_SHADOW;
    }

    public static String itemKeyForTest(String registry, int metadata, String nbt, String modernKind) {
        return itemKey(registry, metadata, nbt, modernKind);
    }

    public static String normalizedNbtForTest(String nbt) {
        return normalizedNbtForKey(nbt);
    }

    public static ItemSnapshot testSnapshot(String key, String name) {
        return new ItemSnapshot(key, name, "minecraft:" + key, 1, 0, 1,
                "item." + key, null, null, "");
    }

    public static ItemSnapshot testSpawnEggSnapshot(String key, String name, int metadata,
                                                    String modernKind, String nbt) {
        return new ItemSnapshot(key, name, "minecraft:spawn_egg", 383, metadata, 1,
                "item.monsterPlacer", modernKind,
                modernKind == null ? null : plainModernName(name),
                nbt == null ? "" : nbt);
    }

    public static ItemSnapshot stackSnapshot(String name, String registry, int stackSize,
                                             String unlocalizedName, String nbt) {
        return stackSnapshot(name, registry, -1, 0, stackSize, unlocalizedName, nbt);
    }

    public static ItemSnapshot stackSnapshot(String name, String registry, int numericId,
                                             int metadata, int stackSize,
                                             String unlocalizedName, String nbt) {
        String normalizedNbt = normalizedNbtForKey(nbt);
        String key = itemKey(registry, metadata, normalizedNbt, null);
        return new ItemSnapshot(key, name, registry, numericId, metadata, stackSize,
                unlocalizedName, null, null, normalizedNbt);
    }

    public static ItemSnapshot spawnEggSnapshot(String registry, String displayName, int stackSize) {
        ModernSpawnEggData egg = spawnEggDataFromRegistry(registry);
        if (egg == null) return stackSnapshot(displayName, registry, -1, 0, stackSize, null, "");
        String name = displayName == null || displayName.isEmpty()
                ? titleCase(egg.textureName) + " Spawn Egg" : displayName;
        return new ItemSnapshot(itemKey("minecraft:spawn_egg", egg.damage, "", "spawn_egg"),
                name, "minecraft:spawn_egg", 383, egg.damage, stackSize,
                "item.monsterPlacer", "spawn_egg",
                plainModernName(name), "");
    }

    private static String plainModernName(String displayName) {
        return ModernRuntimeBridge.stripMinecraftFormatting(displayName).replace(" Spawn Egg", "");
    }

    public static ModernSpawnEggData spawnEggDataFromRegistry(String registry) {
        if (registry == null) return null;
        int colon = registry.indexOf(':');
        String path = colon >= 0 ? registry.substring(colon + 1) : registry;
        if (!path.endsWith("_spawn_egg")) return null;
        return ModernSpawnEggData.byTextureName(path.substring(0, path.length() - "_spawn_egg".length()));
    }

    public static int clampMaxVisible(int value) {
        return Math.max(MIN_VISIBLE, Math.min(MAX_VISIBLE, value));
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

    private void clearPreview(long now) {
        closePreviewCurrent(now);
        previewRound.handheldItems.clear();
        previewCurrentKey = "";
        previewCurrentEntry = null;
        previewCurrentInterval = null;
        previewObservedKey = "";
        previewSuppressCurrentUntilChange = false;
        previewRenderSnapshots.clear();
    }

    private void clearRuntimeRound() {
        activeRound = null;
        activeBuilder = "";
        currentKey = "";
        currentEntry = null;
        currentInterval = null;
        renderSnapshots.clear();
    }

    private static boolean isRoundState(GameStats.TrackingState state) {
        return state == GameStats.TrackingState.ROUND_PRE
                || state == GameStats.TrackingState.ROUND_BUILD
                || state == GameStats.TrackingState.ROUND_END;
    }

    private void seedPreviewDefaults(long now) {
        ItemSnapshot[] defaults = previewDefaultSnapshotsTopToBottom();
        int offset = 0;
        for (int i = defaults.length - 1; i >= 0; i--) {
            recordPreviewSnapshot(defaults[i], now + offset);
            offset++;
        }
        closePreviewCurrent(now + defaults.length);
    }

    private static ItemSnapshot[] previewDefaultSnapshotsTopToBottom() {
        return new ItemSnapshot[] {
                previewSpawnEggSnapshot(117),
                previewStackSnapshot("Quartz Block", "minecraft:quartz_block", 155, 0,
                        "tile.quartzBlock"),
                previewStackSnapshot("White Wool", "minecraft:wool", 35, 0,
                        "tile.cloth.white"),
                previewStackSnapshot("Diorite", "minecraft:stone", 1, 3,
                        "tile.stone.diorite"),
                previewStackSnapshot("Polished Diorite", "minecraft:stone", 1, 4,
                        "tile.stone.dioriteSmooth"),
                previewStackSnapshot("Andesite", "minecraft:stone", 1, 5,
                        "tile.stone.andesite"),
                previewStackSnapshot("Dispenser", "minecraft:dispenser", 23, 0,
                        "tile.dispenser"),
                previewStackSnapshot("Cyan Stained Clay", "minecraft:stained_hardened_clay", 159, 9,
                        "tile.clayHardenedStained.cyan"),
                previewStackSnapshot("Gray Wool", "minecraft:wool", 35, 7,
                        "tile.cloth.gray"),
                previewStackSnapshot("Obsidian", "minecraft:obsidian", 49, 0,
                        "tile.obsidian")
        };
    }

    private static ItemSnapshot previewStackSnapshot(String name, String registry, int numericId,
                                                     int metadata, String unlocalizedName) {
        String key = itemKey(registry, metadata, "", null);
        return new ItemSnapshot(key, name, registry, numericId, metadata, 1,
                unlocalizedName, null, null, "");
    }

    private static ItemSnapshot previewSpawnEggSnapshot(int damage) {
        ModernSpawnEggData egg = ModernSpawnEggData.byDamage(damage);
        String name = egg == null ? "Spawn Egg" : titleCase(egg.textureName) + " Spawn Egg";
        return new ItemSnapshot(itemKey("minecraft:spawn_egg", damage, "", "spawn_egg"),
                name, "minecraft:spawn_egg", 383, damage, 1,
                "item.monsterPlacer", "spawn_egg",
                egg == null ? null : name.replace(" Spawn Egg", ""), "");
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

    private static String itemKey(String registry, int metadata, String nbt, String modernKind) {
        if (modernKind != null && !modernKind.isEmpty()) {
            return "modern:" + modernKind + ":" + metadata;
        }
        String id = registry == null || registry.isEmpty() ? "unknown" : registry;
        return "stack:" + id + ":" + metadata + ":" + (nbt == null ? "" : nbt);
    }

    private static String normalizedNbtForKey(String raw) {
        if (raw == null) return "";
        String value = raw.trim();
        if (value.isEmpty() || "{}".equals(value)) return "";
        if (value.startsWith("{") && value.endsWith("}") && value.length() >= 2) {
            value = value.substring(1, value.length() - 1);
        }
        List<String> kept = new ArrayList<String>();
        for (String part : splitTopLevel(value)) {
            String token = part.trim();
            if (token.isEmpty()) continue;
            if (isTransientNbtToken(token)) continue;
            kept.add(token);
        }
        if (kept.isEmpty()) return "";
        StringBuilder out = new StringBuilder("{");
        for (int i = 0; i < kept.size(); i++) {
            if (i > 0) out.append(',');
            out.append(kept.get(i));
        }
        out.append('}');
        return out.toString();
    }

    private static boolean isTransientNbtToken(String token) {
        String normalized = token.replace(" ", "");
        return normalized.startsWith("overrideMeta:")
                || "AttributeModifiers:[]".equals(normalized);
    }

    private static List<String> splitTopLevel(String value) {
        ArrayList<String> out = new ArrayList<String>();
        int depth = 0;
        boolean quoted = false;
        int start = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' && (i == 0 || value.charAt(i - 1) != '\\')) {
                quoted = !quoted;
                continue;
            }
            if (quoted) continue;
            if (c == '{' || c == '[' || c == '(') depth++;
            if (c == '}' || c == ']' || c == ')') depth = Math.max(0, depth - 1);
            if (c == ',' && depth == 0) {
                out.add(value.substring(start, i));
                start = i + 1;
            }
        }
        out.add(value.substring(start));
        return out;
    }

    private static String itemIdLineInternal(GameStats.HandheldItemEntry entry) {
        String id = entry.registryName != null && !entry.registryName.isEmpty()
                ? entry.registryName : (entry.numericId >= 0 ? String.valueOf(entry.numericId) : entry.modernKind);
        if (id == null || id.isEmpty()) id = "unknown";
        String line = id + " | " + entry.metadata;
        String spawnedMob = spawnedMobLineItem(entry);
        return spawnedMob == null ? line : line + " | " + spawnedMob;
    }

    private static String spawnedMobLineItem(GameStats.HandheldItemEntry entry) {
        if (entry == null || !"spawn_egg".equals(entry.modernKind)) return null;
        ModernSpawnEggData egg = ModernSpawnEggData.byDamage(entry.metadata);
        if (egg == null) return null;
        return titleCase(egg.textureName);
    }

    private static List<GameStats.HandheldItemEntry> visibleEntries(GameStats.RoundData round, int maxVisible) {
        ArrayList<GameStats.HandheldItemEntry> out = new ArrayList<GameStats.HandheldItemEntry>();
        if (round == null || round.handheldItems == null || round.handheldItems.isEmpty()) return out;
        int max = clampMaxVisible(maxVisible);
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

    public static final class ItemSnapshot {
        public final String key;
        public final String name;
        public final String registryName;
        public final int numericId;
        public final int metadata;
        public final int stackSize;
        public final String unlocalizedName;
        public final String modernKind;
        public final String modernName;
        public final String nbt;

        private ItemSnapshot(String key, String name, String registryName, int numericId, int metadata,
                             int stackSize, String unlocalizedName, String modernKind, String modernName,
                             String nbt) {
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
        }
    }
}
