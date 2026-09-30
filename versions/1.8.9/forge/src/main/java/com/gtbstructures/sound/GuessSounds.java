package com.gtbstructures.sound;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

/** Persistent selection model and fixed vanilla 1.8.9 sound catalog. */
public final class GuessSounds {
    public static final String DEFAULT_SOUND = "random.orb";
    public static final String NONE = "none";
    public static final String RANDOM = "random";
    public static final float DEFAULT_VOLUME = 20.0F;
    public static final float MAX_VOLUME = 30.0F;
    private static final List<String> SOUND_IDS = loadSoundIds();
    private static final Map<String, String> CANONICAL_IDS = canonicalIds();
    private static final Map<String, String> SOUND_LABELS = loadSoundLabels();
    private static final String[] SELECTED = {DEFAULT_SOUND, DEFAULT_SOUND, DEFAULT_SOUND};
    private static float volume = DEFAULT_VOLUME;
    private static List<Choice> choices;

    private GuessSounds() { }

    public static synchronized String getSound(int points) {
        return SELECTED[index(points)];
    }

    public static synchronized void setSound(int points, String id) {
        int index = index(points);
        String normalized = normalizeChoice(id);
        if (normalized == null) throw new IllegalArgumentException("Unknown Minecraft sound: " + id);
        SELECTED[index] = normalized;
    }

    public static synchronized float getVolume() { return volume; }

    public static synchronized void setVolume(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value) || value < 0 || value > MAX_VOLUME) {
            throw new IllegalArgumentException("Guess sound volume must be between 0 and 30");
        }
        volume = value;
    }

    public static synchronized void resetDefaults() {
        for (int i = 0; i < SELECTED.length; i++) SELECTED[i] = DEFAULT_SOUND;
        volume = DEFAULT_VOLUME;
    }

    public static String normalizeChoice(String input) {
        if (input == null) return null;
        String id = input.trim().toLowerCase(Locale.ROOT);
        if (id.startsWith("minecraft:")) id = id.substring("minecraft:".length());
        if (NONE.equals(id) || RANDOM.equals(id)) return id;
        return CANONICAL_IDS.get(id);
    }

    public static boolean isValidChoice(String input) { return normalizeChoice(input) != null; }

    public static String resolveSound(int points, Random random) {
        String id = getSound(points);
        if (NONE.equals(id)) return null;
        if (RANDOM.equals(id)) return SOUND_IDS.get(random.nextInt(SOUND_IDS.size()));
        return id;
    }

    /** IDs are available without initializing Minecraft's item registry. */
    public static List<String> soundIds() { return SOUND_IDS; }

    /** Build icon stacks only after a client GUI requests them. */
    public static synchronized List<Choice> choices() {
        if (choices == null) {
            List<Choice> result = new ArrayList<Choice>();
            result.add(new Choice(NONE, "None", new ItemStack(Blocks.barrier)));
            result.add(new Choice(RANDOM, "Random", new ItemStack(Blocks.command_block)));
            // Keep the vanilla default easy to find, then list the rest canonically.
            result.add(new Choice(DEFAULT_SOUND, label(DEFAULT_SOUND), icon(DEFAULT_SOUND)));
            for (String id : SOUND_IDS) {
                if (!DEFAULT_SOUND.equals(id)) result.add(new Choice(id, label(id), icon(id)));
            }
            choices = Collections.unmodifiableList(result);
        }
        return choices;
    }

    private static int index(int points) {
        if (points < 1 || points > 3) throw new IllegalArgumentException("Guess points must be 1, 2 or 3");
        return points - 1;
    }

    private static List<String> loadSoundIds() {
        InputStream stream = GuessSounds.class.getResourceAsStream("/assets/gtbstructures/guess-sounds.txt");
        if (stream == null) throw new IllegalStateException("Missing vanilla guess sound catalog");
        List<String> result = new ArrayList<String>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String id = line.trim();
                if (!id.isEmpty() && !id.startsWith("#")) result.add(id);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read vanilla guess sound catalog", e);
        }
        if (result.isEmpty()) throw new IllegalStateException("Empty vanilla guess sound catalog");
        return Collections.unmodifiableList(result);
    }

    private static Map<String, String> canonicalIds() {
        Map<String, String> result = new LinkedHashMap<String, String>();
        for (String id : SOUND_IDS) result.put(id.toLowerCase(Locale.ROOT), id);
        return Collections.unmodifiableMap(result);
    }

    private static Map<String, String> loadSoundLabels() {
        InputStream stream = GuessSounds.class.getResourceAsStream("/assets/gtbstructures/guess-sound-labels.properties");
        if (stream == null) throw new IllegalStateException("Missing audited guess sound labels");
        Properties properties = new Properties();
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read audited guess sound labels", e);
        }
        if (properties.size() != SOUND_IDS.size()) {
            throw new IllegalStateException("Audited labels must cover exactly the vanilla SFX catalog");
        }
        Map<String, String> result = new LinkedHashMap<String, String>();
        for (String id : SOUND_IDS) {
            String value = properties.getProperty(id);
            if (value == null || value.trim().isEmpty()) {
                throw new IllegalStateException("Missing audited label for " + id);
            }
            result.put(id, value.trim());
        }
        return Collections.unmodifiableMap(result);
    }

    static String label(String id) {
        String value = SOUND_LABELS.get(id);
        if (value == null) throw new IllegalArgumentException("Unknown Minecraft sound: " + id);
        return value;
    }

    private static ItemStack icon(String id) {
        if (id.startsWith("note.")) return new ItemStack(Blocks.noteblock);
        if (id.contains("orb") || id.contains("levelup")) return new ItemStack(Items.experience_bottle);
        if (id.contains("successful_hit") || id.contains("bowhit")) return new ItemStack(Items.arrow);
        if (id.contains("bow")) return new ItemStack(Items.bow);
        if (id.startsWith("fireworks.")) return new ItemStack(Items.fireworks);
        if (id.equals("fire.ignite")) return new ItemStack(Items.flint_and_steel);
        if (id.equals("item.fireCharge.use")) return new ItemStack(Items.fire_charge);
        if (id.contains("potion")) return new ItemStack(Items.potionitem, 1, 16384);
        if (id.equals("random.drink")) return new ItemStack(Items.potionitem);
        if (id.equals("random.eat") || id.equals("random.burp")) return new ItemStack(Items.apple);
        if (id.equals("random.break")) return new ItemStack(Items.iron_pickaxe);
        if (id.contains("minecart")) return new ItemStack(Items.minecart);
        if (id.startsWith("mob.")) return mobIcon(id);
        if (id.contains("anvil")) return new ItemStack(Blocks.anvil);
        if (id.contains("chest")) return new ItemStack(Blocks.chest);
        if (id.contains("glass")) return new ItemStack(Blocks.glass);
        if (id.contains("portal")) return new ItemStack(Blocks.obsidian);
        if (id.contains("tnt") || id.contains("explode") || id.contains("creeper")) return new ItemStack(Blocks.tnt);
        if (id.contains("water") || id.contains("swim") || id.contains("splash") || id.contains("rain")) return new ItemStack(Items.water_bucket);
        if (id.contains("lava") || id.startsWith("fire.")) return new ItemStack(Items.lava_bucket);
        if (id.contains("piston")) return new ItemStack(Blocks.piston);
        if (id.contains("cloth")) return new ItemStack(Blocks.wool);
        if (id.contains("wood") || id.contains("door")) return new ItemStack(Blocks.planks);
        if (id.contains("grass")) return new ItemStack(Blocks.grass);
        if (id.contains("gravel")) return new ItemStack(Blocks.gravel);
        if (id.contains("sand")) return new ItemStack(Blocks.sand);
        if (id.contains("snow")) return new ItemStack(Blocks.snow);
        if (id.contains("stone")) return new ItemStack(Blocks.stone);
        if (id.contains("click") || id.startsWith("gui.")) return new ItemStack(Blocks.stone_button);
        return new ItemStack(Blocks.noteblock);
    }

    private static ItemStack mobIcon(String id) {
        if (id.startsWith("mob.horse.")) {
            if (id.endsWith("armor")) return new ItemStack(Items.iron_horse_armor);
            return new ItemStack(Items.saddle);
        }
        if (id.startsWith("mob.sheep.") && id.endsWith("shear")) return new ItemStack(Items.shears);
        if (id.startsWith("mob.enderdragon.")) return new ItemStack(Blocks.dragon_egg);
        if (id.startsWith("mob.irongolem.")) return new ItemStack(Items.iron_ingot);
        if (id.startsWith("mob.snowman.")) return new ItemStack(Items.snowball);
        if (id.startsWith("mob.wither.")) return new ItemStack(Items.skull, 1, 1);
        String mob = id.substring(4, id.indexOf('.', 4));
        int egg = "creeper".equals(mob) ? 50 : "skeleton".equals(mob) ? 51
                : "spider".equals(mob) ? 52 : "zombie".equals(mob) ? 54
                : "slime".equals(mob) ? 55 : "ghast".equals(mob) ? 56
                : "zombiepig".equals(mob) ? 57 : "endermen".equals(mob) ? 58
                : "silverfish".equals(mob) ? 60 : "blaze".equals(mob) ? 61
                : "magmacube".equals(mob) ? 62 : "bat".equals(mob) ? 65
                : "guardian".equals(mob) ? 68 : "pig".equals(mob) ? 90
                : "sheep".equals(mob) ? 91 : "cow".equals(mob) ? 92
                : "chicken".equals(mob) ? 93 : "wolf".equals(mob) ? 95
                : "cat".equals(mob) ? 98 : "rabbit".equals(mob) ? 101
                : "villager".equals(mob) ? 120 : -1;
        return egg < 0 ? new ItemStack(Blocks.mob_spawner) : new ItemStack(Items.spawn_egg, 1, egg);
    }

    public static final class Choice {
        public final String id;
        public final String label;
        public final ItemStack icon;

        private Choice(String id, String label, ItemStack icon) {
            this.id = id;
            this.label = label;
            this.icon = icon;
        }
    }
}
