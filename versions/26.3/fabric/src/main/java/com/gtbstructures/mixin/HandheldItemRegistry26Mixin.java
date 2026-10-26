package com.gtbstructures.mixin;

import com.gtbstructures.items.HandheldItemTracker;
import com.gtbstructures.stats.GameStats;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Bridges the 26.x optional registry lookup used by the shared handheld renderer. */
@Mixin(targets = "com.gtbstructures.items.HandheldItemOverlayRenderer", remap = false)
public abstract class HandheldItemRegistry26Mixin {
    private static final int EXACT_ITEM_STACK_CACHE_LIMIT = 256;
    private static final Map<String, ItemStack> ITEM_STACK_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, ItemStack> EXACT_ITEM_STACK_CACHE = new ConcurrentHashMap<>();
    private static final Set<String> UNRENDERABLE_REGISTRIES = ConcurrentHashMap.newKeySet();

    @Inject(method = "registryNameForItem(Ljava/lang/Object;)Ljava/lang/String;",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void gtbs$registryName26(Object candidate, CallbackInfoReturnable<String> callback) {
        String registryName = registryNameForItem26(candidate);
        if (registryName != null) callback.setReturnValue(registryName);
    }

    @Inject(method = "itemForRegistry(Ljava/lang/String;)Ljava/lang/Object;",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void gtbs$resolve26Item(String registry, CallbackInfoReturnable<Object> callback) {
        if (normalizedRegistry(registry) == null) return;
        callback.setReturnValue(itemForRegistry26(registry));
    }

    @Inject(method = "snapshotForStack(Ljava/lang/Object;)"
            + "Lcom/gtbstructures/items/HandheldItemTracker$ItemSnapshot;",
            at = @At("RETURN"), cancellable = true, remap = false)
    private static void gtbs$preserve26ItemComponents(Object candidate,
                                                       CallbackInfoReturnable<HandheldItemTracker.ItemSnapshot> callback) {
        if (!(candidate instanceof ItemStack stack)) return;
        HandheldItemTracker.ItemSnapshot captured = callback.getReturnValue();
        HandheldItemTracker.ItemSnapshot componentAware = componentAwareSnapshot26(stack, captured);
        if (componentAware != null) callback.setReturnValue(componentAware);
    }

    @Inject(method = "renderIcon(Ljava/lang/Object;Ljava/lang/Object;"
            + "Lcom/gtbstructures/stats/GameStats$HandheldItemEntry;II)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void gtbs$render26ItemIcon(Object graphics, Object font,
                                               GameStats.HandheldItemEntry entry, int x, int y,
                                               CallbackInfo callback) {
        if (!(graphics instanceof GuiGraphicsExtractor extractor)
                || !(font instanceof Font textFont)
                || entry == null || "spawn_egg".equals(entry.modernKind)) return;
        ItemStack stack = exactItemStackForEntry26(entry);
        if (stack == null) stack = itemStackForRegistry26(modernRegistryName(entry));
        if (stack == null) {
            callback.cancel();
            return;
        }
        // This renderer-owned stack is never mutated or exposed; Minecraft's item render API reads it only.
        extractor.item(stack, x, y);
        extractor.itemDecorations(textFont, stack, x, y);
        callback.cancel();
    }

    private static Item itemForRegistry26(String registry) {
        ItemStack stack = itemStackForRegistry26(registry);
        return stack == null ? null : stack.getItem();
    }

    private static HandheldItemTracker.ItemSnapshot componentAwareSnapshot26(
            ItemStack stack, HandheldItemTracker.ItemSnapshot captured) {
        if (stack == null || stack.isEmpty() || captured == null
                || "spawn_egg".equals(captured.modernKind)) return captured;
        String components = stack.getComponentsPatch().isEmpty()
                ? "" : stack.getComponentsPatch().toString();
        if (components.isEmpty()) return captured;
        HandheldItemTracker.ItemSnapshot exact = HandheldItemTracker.stackSnapshot(
                captured.name, captured.registryName, captured.numericId, captured.metadata,
                captured.stackSize, captured.unlocalizedName, components);
        rememberExactItemStack26(exact.key, stack);
        return exact;
    }

    private static void rememberExactItemStack26(String key, ItemStack stack) {
        if (key == null || stack == null || stack.isEmpty()) return;
        if (EXACT_ITEM_STACK_CACHE.containsKey(key)) return;
        if (EXACT_ITEM_STACK_CACHE.size() >= EXACT_ITEM_STACK_CACHE_LIMIT) EXACT_ITEM_STACK_CACHE.clear();
        EXACT_ITEM_STACK_CACHE.putIfAbsent(key, stack.copy());
    }

    private static ItemStack exactItemStackForEntry26(GameStats.HandheldItemEntry entry) {
        return entry == null ? null : EXACT_ITEM_STACK_CACHE.get(entry.key);
    }

    private static ItemStack itemStackForRegistry26(String registry) {
        String value = normalizedRegistry(registry);
        if (value == null || UNRENDERABLE_REGISTRIES.contains(value)) return null;
        ItemStack cached = ITEM_STACK_CACHE.get(value);
        if (cached != null) return cached;
        try {
            int colon = value.indexOf(':');
            Identifier id = Identifier.fromNamespaceAndPath(value.substring(0, colon), value.substring(colon + 1));
            Item item = BuiltInRegistries.ITEM.getValue(id);
            if (item == null) {
                UNRENDERABLE_REGISTRIES.add(value);
                return null;
            }
            ItemStack resolved = new ItemStack(item);
            if (resolved.isEmpty()) {
                UNRENDERABLE_REGISTRIES.add(value);
                return null;
            }
            ItemStack raced = ITEM_STACK_CACHE.putIfAbsent(value, resolved);
            return raced == null ? resolved : raced;
        } catch (RuntimeException error) {
            UNRENDERABLE_REGISTRIES.add(value);
            return null;
        }
    }

    private static String normalizedRegistry(String registry) {
        if (registry == null || registry.trim().isEmpty()) return null;
        String value = registry.trim();
        value = value.indexOf(':') >= 0 ? value : "minecraft:" + value;
        int colon = value.indexOf(':');
        if (colon <= 0 || colon + 1 >= value.length()) return null;
        return value;
    }

    private static void clearItemStackCacheForTest() {
        ITEM_STACK_CACHE.clear();
        EXACT_ITEM_STACK_CACHE.clear();
        UNRENDERABLE_REGISTRIES.clear();
    }

    private static ItemStack seedItemStackCacheForTest(String registry) {
        String value = normalizedRegistry(registry);
        if (value == null) return null;
        ITEM_STACK_CACHE.put(value, ItemStack.EMPTY);
        return ItemStack.EMPTY;
    }

    private static int itemStackCacheSizeForTest() { return ITEM_STACK_CACHE.size(); }
    private static int exactItemStackCacheSizeForTest() { return EXACT_ITEM_STACK_CACHE.size(); }

    private static boolean unrenderableRegistryCachedForTest(String registry) {
        String value = normalizedRegistry(registry);
        return value != null && UNRENDERABLE_REGISTRIES.contains(value);
    }

    private static String registryNameForItem26(Object candidate) {
        if (!(candidate instanceof Item item)) return null;
        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        return id == null ? null : id.toString();
    }

    private static String modernRegistryNameForTest(GameStats.HandheldItemEntry entry) {
        return modernRegistryName(entry);
    }

    private static String modernRegistryName(GameStats.HandheldItemEntry entry) {
        if (entry == null || entry.registryName == null) return null;
        String registry = entry.registryName.trim();
        if (registry.isEmpty()) return null;
        int colon = registry.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : registry.substring(0, colon);
        String path = colon < 0 ? registry : registry.substring(colon + 1);
        if ("wool".equals(path)) {
            if (entry.metadata == 0) path = "white_wool";
            else if (entry.metadata == 7) path = "gray_wool";
        } else if ("stone".equals(path)) {
            if (entry.metadata == 3) path = "diorite";
            else if (entry.metadata == 4) path = "polished_diorite";
            else if (entry.metadata == 5) path = "andesite";
        } else if ("stained_hardened_clay".equals(path) && entry.metadata == 9) {
            path = "cyan_terracotta";
        }
        return namespace + ":" + path;
    }
}
