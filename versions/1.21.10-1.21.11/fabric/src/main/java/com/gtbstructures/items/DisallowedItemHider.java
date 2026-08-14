package com.gtbstructures.items;

import com.gtbstructures.GtbStructuresSettings;
import com.gtbstructures.scoreboard.GameTracker;
import com.gtbstructures.scoreboard.GtbGame;
import com.gtbstructures.util.RuntimeReflectionCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** GTU-compatible creative inventory hider for Hypixel GTB build phases. */
public final class DisallowedItemHider {
    private static final Logger LOGGER = LoggerFactory.getLogger(DisallowedItemHider.class);
    private static final Set<String> ALLOWED_REGISTRY_IDS = allowedRegistryIds();
    private static Boolean lastActive;
    private static boolean registered;

    private DisallowedItemHider() {
    }

    public static void registerFabricHooks() {
        if (registered) return;
        registered = true;
        try {
            Class<?> eventsClass = classAny(
                    "net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents",
                    "net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents");
            Object event = staticFieldAny(eventsClass, "MODIFY_ENTRIES_ALL", "MODIFY_OUTPUT_ALL");
            Class<?> callbackClass = callbackClass(eventsClass);
            Object callback = Proxy.newProxyInstance(
                    DisallowedItemHider.class.getClassLoader(),
                    new Class<?>[] {callbackClass},
                    (proxy, method, args) -> {
                        if (("modifyEntries".equals(method.getName())
                                || "modifyOutput".equals(method.getName())) && args != null) {
                            filterEntries(args);
                            return null;
                        }
                        if ("toString".equals(method.getName())) return "GtbStructures disallowed item hider";
                        return null;
                    });
            Method register = event.getClass().getMethod("register", Object.class);
            register.setAccessible(true);
            register.invoke(event, callback);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.error("Unable to register GtbStructures Fabric disallowed item hider", e);
        }
    }

    public static void onClientTick() {
        boolean active = isActive();
        if (lastActive != null && lastActive.booleanValue() == active) return;
        lastActive = Boolean.valueOf(active);
        invalidateCreativeTabCache();
    }

    public static boolean isAllowedRegistryForTest(String registry, GtbGame.State state) {
        return !isActiveState(state) || ALLOWED_REGISTRY_IDS.contains(normalizeRegistry(registry));
    }

    public static boolean shouldHideRegistryForTest(String registry, GtbGame.State state) {
        return !isAllowedRegistryForTest(registry, state);
    }

    public static boolean shouldHideRegistryForMasterForTest(String registry, GtbGame.State state,
                                                             boolean modEnabled) {
        return modEnabled && shouldHideRegistryForTest(registry, state);
    }

    public static int allowedCountForTest() {
        return ALLOWED_REGISTRY_IDS.size();
    }

    private static boolean isAllowedRegistry(String registry) {
        return !isActive() || ALLOWED_REGISTRY_IDS.contains(normalizeRegistry(registry));
    }

    private static boolean isActive() {
        if (!GtbStructuresSettings.INSTANCE.modEnabled) return false;
        GtbGame game = GameTracker.INSTANCE.game;
        return game != null && isActiveState(game.state);
    }

    private static boolean isActiveState(GtbGame.State state) {
        return state == GtbGame.State.ROUND_PRE || state == GtbGame.State.ROUND_BUILD;
    }

    private static void filterEntries(Object[] args) {
        IdentityHashMap<Collection<?>, Boolean> seen = new IdentityHashMap<Collection<?>, Boolean>();
        for (Object arg : args) {
            if (arg == null) continue;
            filterCollectionGetter(arg, seen, "getDisplayStacks");
            filterCollectionGetter(arg, seen, "getSearchTabStacks");
        }
    }

    private static void filterCollectionGetter(Object target, IdentityHashMap<Collection<?>, Boolean> seen,
                                               String methodName) {
        try {
            Object value = RuntimeReflectionCache.invokeAny(target, methodName);
            if (value instanceof Collection) {
                filterStacks((Collection<?>) value, seen);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Unable to filter GtbStructures creative item group entries", e);
        }
    }

    private static void filterStacks(Collection<?> stacks, IdentityHashMap<Collection<?>, Boolean> seen) {
        if (stacks == null || seen.put(stacks, Boolean.TRUE) != null || !isActive()) return;
        Iterator<?> it = stacks.iterator();
        while (it.hasNext()) {
            Object stack = it.next();
            if (shouldHideStack(stack)) it.remove();
        }
    }

    private static boolean shouldHideStack(Object stack) {
        try {
            if (stack == null) return false;
            Object empty = invokeAny(stack, "isEmpty", "method_7960");
            if (empty instanceof Boolean && ((Boolean) empty).booleanValue()) return false;
            Object item = invokeAny(stack, "getItem", "method_7909");
            String registry = registryNameForItem(item);
            return !isAllowedRegistry(registry);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Unable to inspect GtbStructures creative item stack", e);
            return false;
        }
    }

    private static String registryNameForItem(Object item) throws ReflectiveOperationException {
        if (item == null) return "";
        Object itemRegistry = itemRegistry();
        Object id = invokeAnyWithArg(itemRegistry, item, "getKey", "getId", "method_10221");
        return id == null || id instanceof Number ? "" : id.toString();
    }

    private static Object itemRegistry() throws ReflectiveOperationException {
        Class<?> registries = classAny("net.minecraft.core.registries.BuiltInRegistries",
                "net.minecraft.registry.Registries", "net.minecraft.class_7923", "mo", "mi");
        return staticFieldAny(registries, "ITEM", "h", "field_41178", "field_11142");
    }

    private static void invalidateCreativeTabCache() {
        try {
            Class<?> tabs = classAny("net.minecraft.world.item.CreativeModeTabs",
                    "net.minecraft.item.ItemGroups", "net.minecraft.class_1761");
            for (Field field : tabs.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) continue;
                String typeName = field.getType().getName();
                if (!"CACHED_PARAMETERS".equals(field.getName())
                        && !field.getName().startsWith("field_")
                        && !typeName.contains("ItemDisplayParameters")) {
                    continue;
                }
                if (!typeName.contains("ItemDisplayParameters") && !"CACHED_PARAMETERS".equals(field.getName())) {
                    continue;
                }
                field.setAccessible(true);
                field.set(null, null);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Unable to invalidate GtbStructures creative tab cache", e);
        }
    }

    private static Class<?> callbackClass(Class<?> eventsClass) throws ClassNotFoundException {
        for (Class<?> nested : eventsClass.getDeclaredClasses()) {
            if ("ModifyEntriesAll".equals(nested.getSimpleName())
                    || "ModifyOutputAll".equals(nested.getSimpleName())) return nested;
        }
        return classAny(
                "net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents$ModifyEntriesAll",
                "net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents$ModifyOutputAll");
    }

    private static Object invokeAny(Object target, String... names) throws ReflectiveOperationException {
        return RuntimeReflectionCache.invokeAny(target, names);
    }

    private static Object invokeAnyWithArg(Object target, Object arg, String... names)
            throws ReflectiveOperationException {
        return RuntimeReflectionCache.invokeAnyWithArg(target, arg, names);
    }

    private static Object staticFieldAny(Class<?> type, String... names) throws ReflectiveOperationException {
        return RuntimeReflectionCache.staticFieldAny(type, names);
    }

    private static Class<?> classAny(String... names) throws ClassNotFoundException {
        return RuntimeReflectionCache.classAny(names);
    }

    private static String normalizeRegistry(String registry) {
        if (registry == null) return "";
        String value = registry.trim().toLowerCase(Locale.ROOT);
        return value.indexOf(':') >= 0 ? value : "minecraft:" + value;
    }

    private static Set<String> allowedRegistryIds() {
        LinkedHashSet<String> out = new LinkedHashSet<String>();
        String raw = """
minecraft:acacia_door
minecraft:acacia_fence
minecraft:acacia_fence_gate
minecraft:armor_stand
minecraft:white_banner
minecraft:orange_banner
minecraft:magenta_banner
minecraft:light_blue_banner
minecraft:yellow_banner
minecraft:lime_banner
minecraft:pink_banner
minecraft:gray_banner
minecraft:light_gray_banner
minecraft:cyan_banner
minecraft:purple_banner
minecraft:blue_banner
minecraft:brown_banner
minecraft:green_banner
minecraft:red_banner
minecraft:black_banner
minecraft:barrier
minecraft:birch_door
minecraft:birch_fence
minecraft:birch_fence_gate
minecraft:cooked_mutton
minecraft:cooked_rabbit
minecraft:dark_oak_door
minecraft:dark_oak_fence
minecraft:dark_oak_fence_gate
minecraft:iron_trapdoor
minecraft:jungle_door
minecraft:jungle_fence
minecraft:jungle_fence_gate
minecraft:mutton
minecraft:prismarine
minecraft:prismarine_crystals
minecraft:prismarine_shard
minecraft:rabbit
minecraft:rabbit_foot
minecraft:rabbit_hide
minecraft:rabbit_stew
minecraft:red_sandstone
minecraft:red_sandstone_stairs
minecraft:cut_red_sandstone
minecraft:chiseled_red_sandstone
minecraft:red_sandstone_slab
minecraft:sea_lantern
minecraft:slime_block
minecraft:spruce_door
minecraft:spruce_fence
minecraft:spruce_fence_gate
minecraft:diorite
minecraft:polished_diorite
minecraft:andesite
minecraft:polished_andesite
minecraft:granite
minecraft:polished_granite
minecraft:dark_prismarine
minecraft:prismarine_bricks
minecraft:wet_sponge
minecraft:coarse_dirt
minecraft:rabbit_spawn_egg
minecraft:guardian_spawn_egg
minecraft:endermite_spawn_egg
minecraft:white_stained_glass
minecraft:orange_stained_glass
minecraft:magenta_stained_glass
minecraft:light_blue_stained_glass
minecraft:yellow_stained_glass
minecraft:lime_stained_glass
minecraft:pink_stained_glass
minecraft:gray_stained_glass
minecraft:light_gray_stained_glass
minecraft:cyan_stained_glass
minecraft:purple_stained_glass
minecraft:blue_stained_glass
minecraft:brown_stained_glass
minecraft:green_stained_glass
minecraft:red_stained_glass
minecraft:black_stained_glass
minecraft:white_stained_glass_pane
minecraft:orange_stained_glass_pane
minecraft:magenta_stained_glass_pane
minecraft:light_blue_stained_glass_pane
minecraft:yellow_stained_glass_pane
minecraft:lime_stained_glass_pane
minecraft:pink_stained_glass_pane
minecraft:gray_stained_glass_pane
minecraft:light_gray_stained_glass_pane
minecraft:cyan_stained_glass_pane
minecraft:purple_stained_glass_pane
minecraft:blue_stained_glass_pane
minecraft:brown_stained_glass_pane
minecraft:green_stained_glass_pane
minecraft:red_stained_glass_pane
minecraft:black_stained_glass_pane
minecraft:acacia_log
minecraft:dark_oak_log
minecraft:acacia_planks
minecraft:dark_oak_planks
minecraft:acacia_slab
minecraft:dark_oak_slab
minecraft:acacia_stairs
minecraft:dark_oak_stairs
minecraft:acacia_sapling
minecraft:dark_oak_sapling
minecraft:acacia_leaves
minecraft:dark_oak_leaves
minecraft:packed_ice
minecraft:podzol
minecraft:red_sand
minecraft:red_tulip
minecraft:orange_tulip
minecraft:white_tulip
minecraft:pink_tulip
minecraft:blue_orchid
minecraft:allium
minecraft:azure_bluet
minecraft:oxeye_daisy
minecraft:sunflower
minecraft:peony
minecraft:rose_bush
minecraft:lilac
minecraft:tall_grass
minecraft:large_fern
minecraft:infested_mossy_stone_bricks
minecraft:infested_cracked_stone_bricks
minecraft:infested_chiseled_stone_bricks
minecraft:pufferfish
minecraft:salmon
minecraft:cooked_salmon
minecraft:tropical_fish
minecraft:hay_block
minecraft:white_carpet
minecraft:orange_carpet
minecraft:magenta_carpet
minecraft:light_blue_carpet
minecraft:yellow_carpet
minecraft:lime_carpet
minecraft:pink_carpet
minecraft:gray_carpet
minecraft:light_gray_carpet
minecraft:cyan_carpet
minecraft:purple_carpet
minecraft:blue_carpet
minecraft:brown_carpet
minecraft:green_carpet
minecraft:red_carpet
minecraft:black_carpet
minecraft:terracotta
minecraft:white_terracotta
minecraft:orange_terracotta
minecraft:magenta_terracotta
minecraft:light_blue_terracotta
minecraft:yellow_terracotta
minecraft:lime_terracotta
minecraft:pink_terracotta
minecraft:gray_terracotta
minecraft:light_gray_terracotta
minecraft:cyan_terracotta
minecraft:purple_terracotta
minecraft:blue_terracotta
minecraft:brown_terracotta
minecraft:green_terracotta
minecraft:red_terracotta
minecraft:black_terracotta
minecraft:coal_block
minecraft:iron_horse_armor
minecraft:golden_horse_armor
minecraft:diamond_horse_armor
minecraft:horse_spawn_egg
minecraft:lead
minecraft:name_tag
minecraft:activator_rail
minecraft:redstone_block
minecraft:daylight_detector
minecraft:dropper
minecraft:hopper
minecraft:comparator
minecraft:trapped_chest
minecraft:light_weighted_pressure_plate
minecraft:heavy_weighted_pressure_plate
minecraft:nether_quartz_ore
minecraft:quartz_block
minecraft:quartz_slab
minecraft:quartz_stairs
minecraft:chiseled_quartz_block
minecraft:quartz_pillar
minecraft:nether_brick
minecraft:quartz
minecraft:tnt_minecart
minecraft:hopper_minecart
minecraft:nether_brick_slab
minecraft:enchanted_book
minecraft:firework_star
minecraft:firework_rocket
minecraft:music_disc_wait
minecraft:command_block
minecraft:command_block_minecart
minecraft:beacon
minecraft:anvil
minecraft:chipped_anvil
minecraft:damaged_anvil
minecraft:flower_pot
minecraft:cobblestone_wall
minecraft:mossy_cobblestone_wall
minecraft:creeper_head
minecraft:zombie_head
minecraft:skeleton_skull
minecraft:wither_skeleton_skull
minecraft:player_head
minecraft:oak_button
minecraft:potato
minecraft:poisonous_potato
minecraft:baked_potato
minecraft:carrot
minecraft:golden_carrot
minecraft:carrot_on_a_stick
minecraft:pumpkin_pie
minecraft:nether_star
minecraft:bat_spawn_egg
minecraft:witch_spawn_egg
minecraft:item_frame
minecraft:emerald_ore
minecraft:emerald_block
minecraft:emerald
minecraft:ender_chest
minecraft:tripwire_hook
minecraft:sandstone_stairs
minecraft:writable_book
minecraft:written_book
minecraft:enchanted_golden_apple
minecraft:oak_slab
minecraft:spruce_slab
minecraft:birch_slab
minecraft:jungle_slab
minecraft:spruce_stairs
minecraft:birch_stairs
minecraft:jungle_stairs
minecraft:birch_planks
minecraft:spruce_planks
minecraft:jungle_planks
minecraft:chiseled_sandstone
minecraft:cut_sandstone
minecraft:chiseled_stone_bricks
minecraft:redstone_lamp
minecraft:jungle_log
minecraft:jungle_leaves
minecraft:jungle_sapling
minecraft:experience_bottle
minecraft:fire_charge
minecraft:ocelot_spawn_egg
minecraft:blaze_spawn_egg
minecraft:cave_spider_spawn_egg
minecraft:chicken_spawn_egg
minecraft:cow_spawn_egg
minecraft:creeper_spawn_egg
minecraft:enderman_spawn_egg
minecraft:ghast_spawn_egg
minecraft:magma_cube_spawn_egg
minecraft:mooshroom_spawn_egg
minecraft:pig_spawn_egg
minecraft:sheep_spawn_egg
minecraft:silverfish_spawn_egg
minecraft:skeleton_spawn_egg
minecraft:slime_spawn_egg
minecraft:spider_spawn_egg
minecraft:squid_spawn_egg
minecraft:villager_spawn_egg
minecraft:wolf_spawn_egg
minecraft:zombie_spawn_egg
minecraft:zombified_piglin_spawn_egg
minecraft:brewing_stand
minecraft:cauldron
minecraft:dragon_egg
minecraft:enchanting_table
minecraft:end_portal_frame
minecraft:end_stone
minecraft:lily_pad
minecraft:mycelium
minecraft:nether_bricks
minecraft:nether_brick_fence
minecraft:nether_brick_stairs
minecraft:nether_wart
minecraft:blaze_powder
minecraft:blaze_rod
minecraft:ender_eye
minecraft:spider_eye
minecraft:fermented_spider_eye
minecraft:ghast_tear
minecraft:glass_bottle
minecraft:glistering_melon_slice
minecraft:gold_nugget
minecraft:magma_cream
minecraft:music_disc_13
minecraft:music_disc_cat
minecraft:music_disc_blocks
minecraft:music_disc_chirp
minecraft:music_disc_far
minecraft:music_disc_mall
minecraft:music_disc_mellohi
minecraft:music_disc_stal
minecraft:music_disc_strad
minecraft:music_disc_ward
minecraft:music_disc_11
minecraft:potion
minecraft:splash_potion
minecraft:stone_bricks
minecraft:mossy_stone_bricks
minecraft:cracked_stone_bricks
minecraft:infested_stone
minecraft:infested_stone_bricks
minecraft:infested_cobblestone
minecraft:brick_slab
minecraft:stone_brick_slab
minecraft:mushroom_stem
minecraft:brown_mushroom_block
minecraft:red_mushroom_block
minecraft:melon
minecraft:vine
minecraft:iron_bars
minecraft:glass_pane
minecraft:oak_fence_gate
minecraft:brick_stairs
minecraft:stone_brick_stairs
minecraft:chicken
minecraft:cooked_chicken
minecraft:beef
minecraft:cooked_beef
minecraft:ender_pearl
minecraft:melon_seeds
minecraft:melon_slice
minecraft:pumpkin_seeds
minecraft:rotten_flesh
minecraft:piston
minecraft:sticky_piston
minecraft:shears
minecraft:dead_bush
minecraft:short_grass
minecraft:fern
minecraft:oak_trapdoor
minecraft:map
minecraft:filled_map
minecraft:birch_sapling
minecraft:cobweb
minecraft:detector_rail
minecraft:powered_rail
minecraft:spruce_sapling
minecraft:cookie
minecraft:red_bed
minecraft:repeater
minecraft:cobblestone_slab
minecraft:petrified_oak_slab
minecraft:sandstone_slab
minecraft:painting
minecraft:cake
minecraft:dispenser
minecraft:lapis_lazuli
minecraft:lapis_ore
minecraft:lapis_block
minecraft:note_block
minecraft:sandstone
minecraft:spruce_log
minecraft:birch_log
minecraft:spruce_leaves
minecraft:birch_leaves
minecraft:bone
minecraft:bone_meal
minecraft:cocoa_beans
minecraft:ink_sac
minecraft:charcoal
minecraft:sugar
minecraft:purple_dye
minecraft:cyan_dye
minecraft:light_gray_dye
minecraft:gray_dye
minecraft:pink_dye
minecraft:lime_dye
minecraft:yellow_dye
minecraft:light_blue_dye
minecraft:magenta_dye
minecraft:orange_dye
minecraft:red_dye
minecraft:green_dye
minecraft:orange_wool
minecraft:magenta_wool
minecraft:light_blue_wool
minecraft:yellow_wool
minecraft:lime_wool
minecraft:pink_wool
minecraft:gray_wool
minecraft:light_gray_wool
minecraft:cyan_wool
minecraft:purple_wool
minecraft:blue_wool
minecraft:brown_wool
minecraft:green_wool
minecraft:red_wool
minecraft:black_wool
minecraft:jack_o_lantern
minecraft:glowstone
minecraft:clock
minecraft:netherrack
minecraft:cod
minecraft:cooked_cod
minecraft:soul_sand
minecraft:carved_pumpkin
minecraft:glowstone_dust
minecraft:fishing_rod
minecraft:compass
minecraft:grass_block
minecraft:bedrock
minecraft:water_bucket
minecraft:lava_bucket
minecraft:wooden_axe
minecraft:wooden_hoe
minecraft:wooden_pickaxe
minecraft:wooden_shovel
minecraft:wooden_sword
minecraft:stone_axe
minecraft:stone_hoe
minecraft:stone_pickaxe
minecraft:stone_shovel
minecraft:stone_sword
minecraft:iron_axe
minecraft:iron_hoe
minecraft:iron_pickaxe
minecraft:iron_shovel
minecraft:iron_sword
minecraft:golden_axe
minecraft:golden_hoe
minecraft:golden_pickaxe
minecraft:golden_shovel
minecraft:golden_sword
minecraft:diamond_axe
minecraft:diamond_hoe
minecraft:diamond_pickaxe
minecraft:diamond_shovel
minecraft:diamond_sword
minecraft:leather_helmet
minecraft:leather_chestplate
minecraft:leather_leggings
minecraft:leather_boots
minecraft:chainmail_helmet
minecraft:chainmail_chestplate
minecraft:chainmail_leggings
minecraft:chainmail_boots
minecraft:iron_helmet
minecraft:iron_chestplate
minecraft:iron_leggings
minecraft:iron_boots
minecraft:golden_helmet
minecraft:golden_chestplate
minecraft:golden_leggings
minecraft:golden_boots
minecraft:diamond_helmet
minecraft:diamond_chestplate
minecraft:diamond_leggings
minecraft:diamond_boots
minecraft:bucket
minecraft:milk_bucket
minecraft:flint_and_steel
minecraft:saddle
minecraft:oak_boat
minecraft:rail
minecraft:minecart
minecraft:chest_minecart
minecraft:furnace_minecart
minecraft:snowball
minecraft:egg
minecraft:bow
minecraft:arrow
minecraft:apple
minecraft:golden_apple
minecraft:porkchop
minecraft:cooked_porkchop
minecraft:bread
minecraft:mushroom_stew
minecraft:coal
minecraft:iron_ingot
minecraft:gold_ingot
minecraft:diamond
minecraft:stick
minecraft:flint
minecraft:wheat
minecraft:string
minecraft:feather
minecraft:leather
minecraft:slime_ball
minecraft:clay
minecraft:clay_ball
minecraft:bowl
minecraft:brick
minecraft:paper
minecraft:book
minecraft:redstone
minecraft:gunpowder
minecraft:redstone_torch
minecraft:lever
minecraft:stone_button
minecraft:oak_pressure_plate
minecraft:stone_pressure_plate
minecraft:chest
minecraft:furnace
minecraft:redstone_ore
minecraft:jukebox
minecraft:oak_door
minecraft:iron_door
minecraft:oak_sign
minecraft:ladder
minecraft:torch
minecraft:crafting_table
minecraft:cactus
minecraft:sugar_cane
minecraft:wheat_seeds
minecraft:farmland
minecraft:snow
minecraft:snow_block
minecraft:ice
minecraft:diamond_ore
minecraft:diamond_block
minecraft:oak_fence
minecraft:oak_stairs
minecraft:cobblestone_stairs
minecraft:obsidian
minecraft:tnt
minecraft:bookshelf
minecraft:iron_block
minecraft:bricks
minecraft:coal_ore
minecraft:gold_ore
minecraft:iron_ore
minecraft:mossy_cobblestone
minecraft:smooth_stone_slab
minecraft:dandelion
minecraft:poppy
minecraft:brown_mushroom
minecraft:red_mushroom
minecraft:gold_block
minecraft:white_wool
minecraft:sponge
minecraft:glass
minecraft:stone
minecraft:dirt
minecraft:cobblestone
minecraft:oak_planks
minecraft:oak_sapling
minecraft:oak_log
minecraft:oak_leaves
minecraft:sand
minecraft:gravel
                """;
        for (String line : raw.split("\\R")) {
            String normalized = normalizeRegistry(line);
            if (!normalized.isEmpty()) out.add(normalized);
        }
        return Collections.unmodifiableSet(out);
    }
}
