package com.gtbstructures.sound;

import java.util.Locale;
import java.util.Set;

/** Maps vanilla sound identifiers to recognizable vanilla item icons. */
public final class SoundIconMapping {
    private static final String FALLBACK = "note_block";

    /**
     * Sound subjects whose identifier is also an item identifier in at least one supported modern target.
     * Version-exclusive entries are safe because their sound is only offered by the matching runtime catalog.
     */
    private static final Set<String> DIRECT_ITEMS = Set.of(
            "amethyst_block", "amethyst_cluster", "ancient_debris", "anvil", "azalea", "azalea_leaves",
            "bamboo", "barrel", "basalt", "beacon", "beehive", "bell", "big_dripleaf", "bone_block",
            "bone_meal", "book", "brewing_stand", "brush", "bucket", "bundle", "cactus_flower", "cake",
            "calcite", "campfire", "candle", "cherry_leaves", "cherry_sapling", "cherry_wood", "chest",
            "chiseled_bookshelf", "chorus_flower", "chorus_fruit", "cinnabar", "cobweb", "comparator",
            "composter", "conduit", "copper_bulb", "copper_chest", "copper_door", "copper_golem_statue",
            "copper_grate", "copper_trapdoor", "crafter", "creaking_heart", "crossbow", "decorated_pot",
            "deepslate", "deepslate_bricks", "deepslate_tiles", "dispenser", "dried_ghast",
            "dripstone_block", "elytra", "end_portal_frame", "ender_chest", "firefly_bush", "flowering_azalea",
            "frogspawn", "furnace", "gilded_blackstone", "glass", "glow_ink_sac", "goat_horn",
            "golden_dandelion", "gravel", "grindstone", "hanging_roots", "heavy_core", "honey_block",
            "honey_bottle", "honeycomb", "ink_sac", "iron_door", "iron_trapdoor", "ladder", "lantern",
            "large_amethyst_bud", "lead", "leaf_litter", "lever", "lily_pad", "lodestone", "mace",
            "mangrove_roots", "medium_amethyst_bud", "moss_carpet", "mud", "mud_bricks",
            "muddy_mangrove_roots", "nether_bricks", "nether_gold_ore", "nether_sprouts", "nether_wart",
            "netherite_block", "netherrack", "note_block", "ominous_bottle", "packed_mud",
            "pale_hanging_moss", "pink_petals", "piston", "pointed_dripstone", "polished_deepslate",
            "polished_tuff", "potent_sulfur", "pumpkin", "redstone_torch", "resin_bricks", "respawn_anchor",
            "rooted_dirt", "saddle", "sand", "scaffolding", "sculk", "sculk_catalyst", "sculk_sensor",
            "sculk_shrieker", "sculk_vein", "shears", "shield", "shroomlight", "shulker_box", "slime_block",
            "small_amethyst_bud", "small_dripleaf", "smithing_table", "smoker", "sniffer_egg", "snow",
            "soul_sand", "soul_soil", "spawner", "sponge", "spore_blossom", "spyglass", "stone",
            "stone_button", "stone_pressure_plate", "sulfur", "sulfur_spike", "suspicious_gravel",
            "suspicious_sand", "trial_spawner", "trident", "tuff", "tuff_bricks", "vault", "vine",
            "weeping_vines", "wet_sponge", "wolf_armor");

    /** Entity sound subjects with a spawn egg in at least one supported modern target. */
    private static final Set<String> SPAWN_EGG_ENTITIES = Set.of(
            "allay", "armadillo", "axolotl", "bat", "bee", "blaze", "bogged", "breeze", "camel",
            "camel_husk", "cat", "chicken", "cod", "copper_golem", "cow", "creaking", "creeper", "dolphin",
            "donkey", "drowned", "elder_guardian", "ender_dragon", "enderman", "endermite", "evoker", "fox",
            "frog", "ghast", "glow_squid", "goat", "guardian", "happy_ghast", "hoglin", "horse", "husk",
            "iron_golem", "llama", "magma_cube", "mooshroom", "mule", "nautilus", "ocelot", "panda",
            "parched", "parrot", "phantom", "pig", "piglin", "piglin_brute", "pillager", "polar_bear",
            "rabbit", "ravager", "salmon", "sheep", "shulker", "silverfish", "skeleton", "skeleton_horse",
            "slime", "sniffer", "snow_golem", "spider", "squid", "stray", "strider", "sulfur_cube", "tadpole",
            "tropical_fish", "turtle", "vex", "villager", "vindicator", "wandering_trader", "warden", "witch",
            "wither", "wither_skeleton", "wolf", "zoglin", "zombie", "zombie_horse", "zombie_nautilus",
            "zombie_villager", "zombified_piglin");

    private SoundIconMapping() { }

    public static String itemIdFor(String soundId) {
        if (soundId == null) return "barrier";
        String id = soundId.trim().toLowerCase(Locale.ROOT);
        if (id.startsWith("minecraft:")) id = id.substring("minecraft:".length());
        if (GuessSounds.NONE.equals(id)) return "barrier";
        if (GuessSounds.RANDOM.equals(id)) return "ender_chest";

        if (id.startsWith("block.")) return blockIcon(firstPart(id, 6));
        if (id.startsWith("item.")) return itemIcon(id, firstPart(id, 5));
        if (id.startsWith("entity.")) return entityIcon(firstPart(id, 7));
        if (id.startsWith("player.")) return "player_head";
        if (id.startsWith("music.") || id.startsWith("music_disc.") || id.startsWith("record.")) return "jukebox";
        return systemIcon(id);
    }

    private static String blockIcon(String block) {
        switch (block) {
            case "bamboo_sapling": return "bamboo";
            case "bamboo_wood": return "bamboo_block";
            case "bamboo_wood_button": return "bamboo_button";
            case "bamboo_wood_door": return "bamboo_door";
            case "bamboo_wood_fence_gate": return "bamboo_fence_gate";
            case "bamboo_wood_hanging_sign": return "bamboo_hanging_sign";
            case "bamboo_wood_pressure_plate": return "bamboo_pressure_plate";
            case "bamboo_wood_trapdoor": return "bamboo_trapdoor";
            case "blastfurnace": return "blast_furnace";
            case "bubble_column": return "water_bucket";
            case "cave_vines": return "glow_berries";
            case "chain": return "iron_chain";
            case "cherry_wood_button": return "cherry_button";
            case "cherry_wood_door": return "cherry_door";
            case "cherry_wood_fence_gate": return "cherry_fence_gate";
            case "cherry_wood_hanging_sign": return "cherry_hanging_sign";
            case "cherry_wood_pressure_plate": return "cherry_pressure_plate";
            case "cherry_wood_trapdoor": return "cherry_trapdoor";
            case "copper": return "copper_block";
            case "copper_chest_oxidized": return "oxidized_copper_chest";
            case "copper_chest_weathered": return "weathered_copper_chest";
            case "coral_block": return "tube_coral_block";
            case "crop": return "wheat";
            case "deadbush": return "dead_bush";
            case "dry_grass": return "short_dry_grass";
            case "enchantment_table": return "enchanting_table";
            case "end_gateway":
            case "end_portal": return "end_portal_frame";
            case "eyeblossom": return "open_eyeblossom";
            case "fence_gate": return "oak_fence_gate";
            case "fire": return "flint_and_steel";
            case "froglight": return "ochre_froglight";
            case "fungus": return "crimson_fungus";
            case "grass": return "short_grass";
            case "growing_plant": return "twisting_vines";
            case "hanging_sign": return "oak_hanging_sign";
            case "iron":
            case "metal": return "iron_block";
            case "lava": return "lava_bucket";
            case "metal_pressure_plate": return "heavy_weighted_pressure_plate";
            case "moss": return "moss_block";
            case "nether_ore": return "nether_quartz_ore";
            case "nether_wood": return "crimson_stem";
            case "nether_wood_button": return "crimson_button";
            case "nether_wood_door": return "crimson_door";
            case "nether_wood_fence_gate": return "crimson_fence_gate";
            case "nether_wood_hanging_sign": return "crimson_hanging_sign";
            case "nether_wood_pressure_plate": return "crimson_pressure_plate";
            case "nether_wood_trapdoor": return "crimson_trapdoor";
            case "nylium": return "crimson_nylium";
            case "portal": return "obsidian";
            case "powder_snow": return "powder_snow_bucket";
            case "resin": return "resin_block";
            case "roots": return "crimson_roots";
            case "shelf": return "oak_shelf";
            case "sign": return "oak_sign";
            case "stem": return "pumpkin_seeds";
            case "sweet_berry_bush": return "sweet_berries";
            case "tripwire": return "tripwire_hook";
            case "wart_block": return "nether_wart_block";
            case "water": return "water_bucket";
            case "wet_grass": return "grass_block";
            case "wood": return "oak_log";
            case "wooden_button": return "oak_button";
            case "wooden_door": return "oak_door";
            case "wooden_pressure_plate": return "oak_pressure_plate";
            case "wooden_trapdoor": return "oak_trapdoor";
            case "wool": return "white_wool";
            default: return directItem(block);
        }
    }

    private static String itemIcon(String id, String item) {
        if ("armor".equals(item)) return armorIcon(firstPart(id, "item.armor.".length()));
        if ("bucket".equals(item)) return bucketIcon(id);
        if ("bottle".equals(item)) return id.endsWith("dragonbreath") ? "dragon_breath" : "glass_bottle";
        switch (item) {
            case "axe": return "iron_axe";
            case "crop": return "wheat_seeds";
            case "dye": return "red_dye";
            case "firecharge": return "fire_charge";
            case "flintandsteel": return "flint_and_steel";
            case "hoe": return "iron_hoe";
            case "horse_armor": return "iron_horse_armor";
            case "llama_carpet": return "white_carpet";
            case "lodestone_compass": return "compass";
            case "nautilus_saddle_equip":
            case "nautilus_saddle_underwater_equip": return "saddle";
            case "shovel": return "iron_shovel";
            case "spear": return "iron_spear";
            case "spear_wood": return "wooden_spear";
            case "totem": return "totem_of_undying";
            default: return directItem(item);
        }
    }

    private static String armorIcon(String action) {
        switch (action) {
            case "equip_chain": return "chainmail_helmet";
            case "equip_copper": return "copper_helmet";
            case "equip_diamond": return "diamond_helmet";
            case "equip_elytra": return "elytra";
            case "equip_generic": return "iron_chestplate";
            case "equip_gold": return "golden_helmet";
            case "equip_iron": return "iron_helmet";
            case "equip_leather": return "leather_helmet";
            case "equip_nautilus":
            case "unequip_nautilus": return "iron_nautilus_armor";
            case "equip_netherite": return "netherite_helmet";
            case "equip_turtle": return "turtle_helmet";
            case "equip_wolf":
            case "unequip_wolf": return "wolf_armor";
            default: return FALLBACK;
        }
    }

    private static String bucketIcon(String id) {
        if (id.endsWith("_axolotl")) return "axolotl_bucket";
        if (id.endsWith("_fish")) return "cod_bucket";
        if (id.endsWith("_lava")) return "lava_bucket";
        if (id.endsWith("_powder_snow")) return "powder_snow_bucket";
        if (id.endsWith("_sulfur_cube")) return "sulfur_cube_bucket";
        if (id.endsWith("_tadpole")) return "tadpole_bucket";
        return "bucket";
    }

    private static String entityIcon(String entity) {
        switch (entity) {
            case "armor_stand": return "armor_stand";
            case "arrow": return "arrow";
            case "baby_cat":
            case "cat_royal": return "cat_spawn_egg";
            case "baby_chicken":
            case "chicken_picky": return "chicken_spawn_egg";
            case "baby_horse": return "horse_spawn_egg";
            case "baby_nautilus": return "nautilus_spawn_egg";
            case "baby_pig":
            case "pig_big":
            case "pig_mini": return "pig_spawn_egg";
            case "baby_wolf":
            case "wolf_angry":
            case "wolf_big":
            case "wolf_cute":
            case "wolf_grumpy":
            case "wolf_puglin":
            case "wolf_sad": return "wolf_spawn_egg";
            case "boat": return "oak_boat";
            case "copper_golem_become_statue": return "copper_golem_statue";
            case "copper_golem_oxidized": return "oxidized_copper_golem_statue";
            case "copper_golem_weathered": return "weathered_copper_golem_statue";
            case "cow_moody": return "cow_spawn_egg";
            case "dragon_fireball": return "fire_charge";
            case "egg": return "egg";
            case "ender_eye": return "ender_eye";
            case "ender_pearl": return "ender_pearl";
            case "evoker_fangs": return "evoker_spawn_egg";
            case "experience_bottle":
            case "experience_orb": return "experience_bottle";
            case "firework_rocket": return "firework_rocket";
            case "fish": return "cod";
            case "fishing_bobber": return "fishing_rod";
            case "generic":
            case "player": return "player_head";
            case "ghastling": return "happy_ghast_spawn_egg";
            case "glow_item_frame": return "glow_item_frame";
            case "hostile": return "iron_sword";
            case "illusioner": return "bow";
            case "item": return "bundle";
            case "item_frame": return "item_frame";
            case "lightning_bolt": return "lightning_rod";
            case "lingering_potion": return "lingering_potion";
            case "minecart": return "minecart";
            case "painting": return "painting";
            case "puffer_fish": return "pufferfish_spawn_egg";
            case "shulker_bullet": return "shulker_shell";
            case "small_sulfur_cube": return "sulfur_cube_spawn_egg";
            case "snowball": return "snowball";
            case "splash_potion": return "splash_potion";
            case "tnt": return "tnt";
            case "wind_charge": return "wind_charge";
            default: return SPAWN_EGG_ENTITIES.contains(entity) ? entity + "_spawn_egg" : FALLBACK;
        }
    }

    private static String systemIcon(String id) {
        String category = firstPart(id, 0);
        int prefixLength = category.length() + 1;
        String subject = prefixLength <= id.length() ? firstPart(id, prefixLength) : "";
        switch (category + "." + subject) {
            case "ambient.basalt_deltas": return "basalt";
            case "ambient.cave": return "echo_shard";
            case "ambient.crimson_forest": return "crimson_nylium";
            case "ambient.nether_wastes": return "netherrack";
            case "ambient.soul_sand_valley": return "soul_sand";
            case "ambient.underwater": return "water_bucket";
            case "ambient.warped_forest": return "warped_nylium";
            case "ui.button": return "stone_button";
            case "ui.cartography_table": return "cartography_table";
            case "ui.hud": return "water_bucket";
            case "ui.loom": return "loom";
            case "ui.stonecutter": return "stonecutter";
            case "ui.toast": return "knowledge_book";
            case "weather.end_flash": return "end_crystal";
            case "weather.rain": return "water_bucket";
            case "event.mob_effect": return "ominous_bottle";
            case "event.raid": return "goat_horn";
            case "enchant.thorns": return "enchanted_book";
            case "particle.soul_escape": return "soul_sand";
            default: return FALLBACK;
        }
    }

    private static String directItem(String subject) {
        return DIRECT_ITEMS.contains(subject) ? subject : FALLBACK;
    }

    private static String firstPart(String id, int prefixLength) {
        if (prefixLength >= id.length()) return "";
        int dot = id.indexOf('.', prefixLength);
        return id.substring(prefixLength, dot < 0 ? id.length() : dot);
    }
}
