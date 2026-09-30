package com.gtbstructures.sound;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/** Modern runtime-discovered sound selections, independent of Minecraft APIs. */
public final class GuessSounds {
    public static final String DEFAULT_SOUND = "entity.experience_orb.pickup";
    public static final String NONE = "none";
    public static final String RANDOM = "random";
    public static final float DEFAULT_VOLUME = 20.0F;
    public static final float MAX_VOLUME = 30.0F;

    private static final Catalog DEFAULT_CATALOG = new Catalog(Collections.singletonList(
            new Entry(DEFAULT_SOUND, "Experience gained", "subtitles.entity.experience_orb.pickup")));
    private static volatile Catalog currentCatalog = DEFAULT_CATALOG;

    private GuessSounds() { }

    public static Catalog catalog() { return currentCatalog; }
    public static boolean catalogReady() { return currentCatalog != DEFAULT_CATALOG; }
    public static synchronized void replaceCatalog(Collection<Entry> entries) { currentCatalog = new Catalog(entries); }

    public static String normalizeChoice(String input) {
        if (input == null) return null;
        String value = input.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("minecraft:")) value = value.substring("minecraft:".length());
        if (value.indexOf(':') >= 0) return null;
        if (NONE.equals(value) || RANDOM.equals(value)) return value;
        Entry entry = currentCatalog.find(value);
        return entry == null ? null : entry.id;
    }

    public static boolean isValidChoice(String input) { return normalizeChoice(input) != null; }

    /** Preserves syntactically valid future IDs until the runtime catalog is discovered. */
    public static String normalizeConfiguredChoice(String input) {
        if (input == null) return null;
        String value = input.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("minecraft:")) value = value.substring("minecraft:".length());
        if (!validPath(value)) return null;
        if (NONE.equals(value) || RANDOM.equals(value)) return value;
        if (!catalogReady()) return value;
        return normalizeChoice(value);
    }

    public static String resolveSound(String selection, Random random) {
        String normalized = normalizeChoice(selection);
        if (normalized == null || NONE.equals(normalized)) return null;
        if (RANDOM.equals(normalized)) {
            List<Entry> entries = currentCatalog.entries();
            return entries.isEmpty() ? null : entries.get(random.nextInt(entries.size())).id;
        }
        return normalized;
    }

    public static final class Entry {
        public final String id;
        public final String caption;
        public final String subtitleKey;

        public Entry(String id, String caption, String subtitleKey) {
            this.id = canonicalId(id);
            this.caption = requireText(caption, "caption");
            this.subtitleKey = subtitleKey == null || subtitleKey.trim().isEmpty() ? null : subtitleKey.trim();
        }
    }

    public static final class Catalog {
        private final List<Entry> entries;
        private final Map<String, Entry> byId;

        public Catalog(Collection<Entry> source) {
            if (source == null) throw new IllegalArgumentException("Sound catalog is null");
            List<Entry> copy = new ArrayList<Entry>();
            Map<String, Entry> index = new LinkedHashMap<String, Entry>();
            for (Entry entry : source) {
                if (entry == null || index.put(entry.id, entry) != null) {
                    throw new IllegalArgumentException("Sound catalog contains a duplicate or null entry");
                }
                copy.add(entry);
            }
            if (copy.isEmpty()) throw new IllegalArgumentException("Sound catalog is empty");
            entries = Collections.unmodifiableList(copy);
            byId = Collections.unmodifiableMap(index);
        }

        public List<Entry> entries() { return entries; }

        public Entry find(String id) {
            if (id == null) return null;
            String value = id.trim().toLowerCase(Locale.ROOT);
            if (value.startsWith("minecraft:")) value = value.substring("minecraft:".length());
            if (value.indexOf(':') >= 0) return null;
            return byId.get(value);
        }
    }

    private static String canonicalId(String input) {
        String value = requireText(input, "sound id").toLowerCase(Locale.ROOT);
        if (value.startsWith("minecraft:")) value = value.substring("minecraft:".length());
        if (!validPath(value) || NONE.equals(value) || RANDOM.equals(value)) {
            throw new IllegalArgumentException("Invalid vanilla sound id: " + input);
        }
        return value;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(name + " is empty");
        return value.trim();
    }

    private static boolean validPath(String value) {
        return value != null && value.matches("[a-z0-9][a-z0-9/._-]*");
    }
}
