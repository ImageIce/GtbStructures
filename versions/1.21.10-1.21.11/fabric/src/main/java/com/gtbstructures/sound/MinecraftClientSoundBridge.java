package com.gtbstructures.sound;

import com.gtbstructures.GtbStructuresSettings;
import com.gtbstructures.chat.ModernChatCooldownCounter;
import com.gtbstructures.scoreboard.GtbGame;
import com.gtbstructures.scoreboard.InterfacePreview;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Properties;

/** Client adapter for both Mojang names and Fabric's 1.21 intermediary names. */
public final class MinecraftClientSoundBridge {
    private static final Properties QUALIFIERS = properties("guess-sound-qualifiers");
    private static final Properties FALLBACKS = properties("guess-sound-fallbacks");
    private static final JsonObject ENGLISH = english();
    private static GuessSoundController.Playback playback = MinecraftClientSoundBridge::playNative;
    private static boolean installed;
    private static Object catalogManager;
    private static long nextCatalogCheck;
    private static int catalogHash;

    private MinecraftClientSoundBridge() { }

    public static synchronized void ensureInstalled() {
        if (installed) return;
        GuessSoundController.INSTANCE.install(new GuessSoundController.Settings() {
            public boolean enabled() { return GtbStructuresSettings.INSTANCE.modEnabled; }
            public String selection(int points) {
                String choice = GuessSounds.normalizeConfiguredChoice(GtbStructuresSettings.INSTANCE.guessSound(points));
                return choice == null ? GuessSounds.DEFAULT_SOUND : choice;
            }
            public float volume() { return GtbStructuresSettings.normalizeGuessSoundVolume(GtbStructuresSettings.INSTANCE.guessSoundVolume); }
        }, (id, volume, pitch) -> playback.play(id, volume, pitch));
        installed = true;
    }

    public static void setPlayback(GuessSoundController.Playback value) {
        playback = value == null ? MinecraftClientSoundBridge::playNative : value;
    }

    public static void playNotification(String id, float volume, float pitch) {
        playback.play(id, volume, pitch);
    }

    public static void tickCooldown(boolean liveEnabled, GtbGame.State state, long now) {
        GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
        int sound = ModernChatCooldownCounter.onClientTick(settings.modEnabled,
                settings.chatCooldownCounter, liveEnabled, InterfacePreview.isActive(), state, now,
                settings.chatCooldownSfxEnabled, settings.chatCooldownProgressBeats);
        if (sound == ModernChatCooldownCounter.SOUND_READY) {
            playNotification("block.note_block.pling", 20.0F, 1.5F);
        } else if (sound == ModernChatCooldownCounter.SOUND_PROGRESS) {
            playNotification("block.note_block.hat", 20.0F / 3.0F, 1.0F);
        }
    }

    public static synchronized void ensureCatalog() {
        long now = System.currentTimeMillis();
        if (now < nextCatalogCheck && GuessSounds.catalogReady()) return;
        nextCatalogCheck = now + 1000L;
        Object manager = soundManager();
        Collection<?> keys = availableKeys(manager);
        if (keys == null || keys.isEmpty()) return;
        int hash = keys.hashCode();
        if (manager == catalogManager && hash == catalogHash && GuessSounds.catalogReady()) return;
        refreshCatalog(manager);
        catalogManager = manager;
        catalogHash = hash;
    }

    public static synchronized void refreshCatalog(Object manager) {
        Collection<?> keys = availableKeys(manager);
        if (keys == null || keys.isEmpty()) return;
        var entries = new ArrayList<GuessSounds.Entry>();
        for (Object key : keys) {
            String id = String.valueOf(key);
            if (!id.startsWith("minecraft:") && id.contains(":")) continue;
            String path = id.startsWith("minecraft:") ? id.substring(10) : id;
            if (path.startsWith("music.") || path.startsWith("music_disc.") || path.startsWith("record.")) continue;
            Object event = call(manager, new String[]{"getSoundEvent", "method_4869", "get"}, key);
            Object component = call(event, new String[]{"getSubtitle", "method_4886"});
            String subtitleKey = subtitleKey(component);
            String caption = subtitleKey != null && ENGLISH.has(subtitleKey) ? ENGLISH.get(subtitleKey).getAsString() : null;
            if (caption == null) caption = FALLBACKS.getProperty(path);
            if (caption == null && component != null) caption = string(call(component, new String[]{"getString", "method_54160"}));
            if (caption == null && component instanceof String) caption = (String) component;
            if (caption == null || caption.isEmpty()) caption = humanize(path);
            String qualifier = QUALIFIERS.getProperty(path);
            if (qualifier != null) caption += " (" + qualifier + ")";
            entries.add(new GuessSounds.Entry(path, caption, subtitleKey));
        }
        entries.sort(Comparator.comparing((GuessSounds.Entry e) -> !GuessSounds.DEFAULT_SOUND.equals(e.id))
                .thenComparing(e -> e.id));
        if (!entries.isEmpty()) {
            GuessSounds.replaceCatalog(entries);
            // Config loads before resource discovery; validate deferred IDs against this target now.
            GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
            for (int points = 1; points <= 3; points++) {
                String choice = GuessSounds.normalizeChoice(settings.guessSound(points));
                settings.setGuessSound(points, choice == null ? GuessSounds.DEFAULT_SOUND : choice);
            }
        }
    }

    private static Collection<?> availableKeys(Object manager) {
        Object keys = call(manager, new String[]{"getAvailableSounds", "method_4864"});
        return keys instanceof Map ? ((Map<?, ?>) keys).keySet() : keys instanceof Collection ? (Collection<?>) keys : null;
    }

    private static String subtitleKey(Object component) {
        Object contents = call(component, new String[]{"getContents", "method_10851"});
        return string(call(contents, new String[]{"getKey", "method_11022"}));
    }

    public static void preview(int points) {
        ensureCatalog(); ensureInstalled(); GuessSoundController.INSTANCE.preview(points);
    }

    public static boolean consumeOriginal(Object instance) {
        ensureInstalled();
        // Reading the event ID is valid before resolve(); getSound() is a sampled audio file instead.
        String id = string(call(instance, new String[]{"getIdentifier", "getLocation", "method_4775"}));
        float volume = soundVolume(instance);
        float pitch = soundPitch(instance);
        if (id == null) return false;
        GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
        if (!settings.modEnabled) EndGameFireworkController.INSTANCE.reset();
        if (EndGameFireworkController.shouldSuppress(id, settings.modEnabled,
                settings.endGameFireworkSounds,
                EndGameFireworkController.INSTANCE.isPostGameLatched(),
                GuessSoundController.INSTANCE.isPlayingReplacement())) return true;
        return GuessSoundController.INSTANCE.consumeOriginal(new GuessSoundController.OriginalSound() {
            public String canonicalSoundId() { return id; }
            public float volume() { return volume; }
            public float pitch() { return pitch; }
        }, System.currentTimeMillis());
    }

    // At play() HEAD the audio sample is unresolved; its public getters depend on that sample.
    static float soundVolume(Object sound) {
        return rawNumber(sound, new String[]{"volume", "field_5442"}, new String[]{"getVolume", "method_4781"});
    }

    static float soundPitch(Object sound) {
        return rawNumber(sound, new String[]{"pitch", "field_5441"}, new String[]{"getPitch", "method_4782"});
    }

    private static float rawNumber(Object sound, String[] fields, String[] accessors) {
        if (sound == null) return 0F;
        for (Class<?> type = sound.getClass(); type != null; type = type.getSuperclass()) {
            for (String name : fields) try {
                var field = type.getDeclaredField(name);
                field.setAccessible(true);
                Object value = field.get(sound);
                if (value instanceof Number) return ((Number) value).floatValue();
            } catch (ReflectiveOperationException | RuntimeException ignored) { }
        }
        return number(call(sound, accessors));
    }

    /** SoundManager.play returns PlayResult in every supported modern client. */
    public static Object suppressedResult(Object manager) {
        for (Method method : manager.getClass().getMethods()) {
            if ((method.getName().equals("play") || method.getName().equals("method_4873"))
                    && method.getParameterCount() == 1 && method.getReturnType().isEnum()) {
                for (Object value : method.getReturnType().getEnumConstants())
                    if (((Enum<?>) value).name().equals("NOT_STARTED")) return value;
            }
        }
        throw new IllegalStateException("Unsupported SoundManager.play return type");
    }

    static Object createNativeSound(String id, float volume, float pitch) throws ReflectiveOperationException {
        Class<?> soundClass = type("net.minecraft.client.resources.sounds.SimpleSoundInstance", "net.minecraft.class_1109");
        for (Constructor<?> ctor : soundClass.getConstructors()) {
            Class<?>[] p = ctor.getParameterTypes();
            if (p.length != 12 || !p[1].isEnum() || p[2] != float.class || p[3] != float.class
                    || p[5] != boolean.class || p[6] != int.class || !p[7].isEnum() || p[11] != boolean.class) continue;
            Object location = callStatic(p[0], new String[]{"parse", "method_60654"}, "minecraft:" + id);
            Object random = callStatic(p[4], new String[]{"create", "method_43047"});
            if (random == null) {
                Class<?> instance = type("net.minecraft.client.resources.sounds.SoundInstance", "net.minecraft.class_1113");
                random = callStatic(instance, new String[]{"createUnseededRandom", "method_43221"});
            }
            return ctor.newInstance(location, enumValue(p[1], "PLAYERS"), volume, pitch, random,
                    false, 0, enumValue(p[7], "NONE"), 0D, 0D, 0D, true);
        }
        throw new NoSuchMethodException("SimpleSoundInstance relative sound constructor");
    }

    private static void playNative(String id, float volume, float pitch) {
        try {
            Object sound = createNativeSound(id, volume, pitch);
            Object manager = soundManager();
            if (manager == null) return;
            call(manager, new String[]{"play", "method_4873"}, sound);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("Cannot play guess sound " + id, e); }
    }

    private static Object enumValue(Class<?> type, String name) {
        for (Object value : type.getEnumConstants()) if (((Enum<?>) value).name().equals(name)) return value;
        throw new IllegalStateException("Missing enum value " + name);
    }

    private static Object soundManager() {
        try {
            Class<?> client = type("net.minecraft.client.Minecraft", "net.minecraft.class_310");
            return call(callStatic(client, new String[]{"getInstance", "method_1551"}), new String[]{"getSoundManager", "method_1483"});
        } catch (ClassNotFoundException e) { return null; }
    }

    private static Class<?> type(String... names) throws ClassNotFoundException {
        for (String name : names) try { return Class.forName(name); } catch (ClassNotFoundException ignored) { }
        throw new ClassNotFoundException(names[0]);
    }

    private static Object call(Object target, String[] names, Object... args) {
        return target == null ? null : invoke(target.getClass(), target, names, args);
    }

    private static Object callStatic(Class<?> type, String[] names, Object... args) { return invoke(type, null, names, args); }

    private static Object invoke(Class<?> type, Object target, String[] names, Object[] args) {
        for (String name : names) for (Method method : type.getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
            try { return method.invoke(target, args); } catch (ReflectiveOperationException | IllegalArgumentException ignored) { }
        }
        return null;
    }

    private static String string(Object value) { return value == null ? null : String.valueOf(value); }
    private static float number(Object value) { return value instanceof Number ? ((Number) value).floatValue() : 0F; }
    private static String humanize(String path) {
        String text = path.replace('.', ' ').replace('_', ' ');
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static JsonObject english() {
        try (InputStream stream = MinecraftClientSoundBridge.class.getResourceAsStream("/assets/minecraft/lang/en_us.json")) {
            return stream == null ? new JsonObject() : JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) { return new JsonObject(); }
    }

    private static Properties properties(String name) {
        Properties result = new Properties();
        try (InputStream stream = MinecraftClientSoundBridge.class.getResourceAsStream("/assets/gtbstructures/" + name + ".properties")) {
            if (stream != null) result.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (Exception e) { throw new IllegalStateException("Cannot load guess sound captions", e); }
        return result;
    }
}
