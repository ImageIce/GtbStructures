package com.gtbstructures.overlay;

import com.gtbstructures.GtbStructuresSettings;
import com.gtbstructures.scoreboard.GameTracker;
import com.gtbstructures.scoreboard.GtbGame;
import com.gtbstructures.scoreboard.InterfacePreview;
import com.gtbstructures.scoreboard.PreviewLayoutEditor;
import com.gtbstructures.scoreboard.ScoreboardLayout;
import com.gtbstructures.stats.GameStats;
import com.gtbstructures.util.RuntimeReflectionCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class WorldEnvironmentOverlayRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(WorldEnvironmentOverlayRenderer.class);
    private static final int MARGIN = 2;
    private static final Object CACHE_MISS = new Object();
    private static final ConcurrentMap<String, Object> ICON_ITEMS =
            new ConcurrentHashMap<String, Object>();
    private static final ConcurrentMap<String, Object> ICON_STACKS =
            new ConcurrentHashMap<String, Object>();
    private static final ConcurrentMap<String, Object> IDENTIFIERS =
            new ConcurrentHashMap<String, Object>();
    private static final ConcurrentMap<String, Boolean> WARNED_ICON_FAILURES =
            new ConcurrentHashMap<String, Boolean>();

    private WorldEnvironmentOverlayRenderer() {
    }

    public static void register() {
        if (FabricHudElementBridge.registerBeforeChat(
                "world_environment_overlay", WorldEnvironmentOverlayRenderer::renderEnvironment)) {
            return;
        }
        try {
            Class<?> callbackClass = Class.forName(
                    "net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback");
            Object event = callbackClass.getField("EVENT").get(null);
            Object callback = java.lang.reflect.Proxy.newProxyInstance(
                    WorldEnvironmentOverlayRenderer.class.getClassLoader(),
                    new Class<?>[] {callbackClass},
                    (proxy, method, args) -> {
                        if (method.getName().equals("onHudRender") && args != null && args.length >= 1) {
                            renderEnvironment(args[0]);
                            return null;
                        }
                        if (method.getName().equals("toString")) return "GtbStructures world environment overlay";
                        return null;
                    });
            Method register = event.getClass().getMethod("register", Object.class);
            register.setAccessible(true);
            register.invoke(event, callback);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.error("Unable to register GtbStructures Fabric world environment overlay", e);
        }
    }

    private static void renderEnvironment(Object graphics) {
        try {
            GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
            if (!settings.modEnabled) {
                WorldEnvironmentLogic.clearChatNoticeBaselines();
                return;
            }
            List<WorldEnvironmentLogic.Row> rows;
            Object mc = minecraftClient();
            Object level = fieldAny(mc, "world", "level", "field_1687");
            Object player = fieldAny(mc, "player", "field_1724");
            GtbGame.State state = currentState();
            if (InterfacePreview.isActive()) {
                WorldEnvironmentLogic.clearChatNoticeBaselines();
                if (level != null && player != null) {
                    rows = liveRows(level, invokeAny(player, "getBlockPos", "method_24515"),
                            settings, state, false);
                } else {
                    rows = WorldEnvironmentLogic.previewRows(settings.environmentOverlay,
                            settings.environmentWeatherEnabled, settings.environmentBiomeEnabled,
                            settings.environmentTimeEnabled, settings.environmentTimeIconPreset);
                }
            } else {
                if (!GameStats.INSTANCE.shouldRenderRoundOverlays()
                        || !WorldEnvironmentLogic.shouldRenderLiveForState(state)
                        || level == null || player == null) {
                    WorldEnvironmentLogic.clearChatNoticeBaselines();
                    return;
                }
                boolean observeChatNotices = settings.environmentWeatherChatNotice
                        || settings.environmentBiomeChatNotice
                        || settings.environmentTimeChatNotice;
                if (!observeChatNotices) {
                    WorldEnvironmentLogic.clearChatNoticeBaselines();
                }
                boolean drawEnvironment = settings.environmentOverlay
                        && (settings.environmentWeatherEnabled
                        || settings.environmentBiomeEnabled
                        || settings.environmentTimeEnabled);
                if (!drawEnvironment && !observeChatNotices) return;
                rows = liveRows(level, invokeAny(player, "getBlockPos", "method_24515"),
                        settings, state, observeChatNotices);
            }
            renderRows(graphics, rows, settings);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Unable to render GtbStructures world environment overlay", e);
        }
    }

    private static List<WorldEnvironmentLogic.Row> liveRows(Object level, Object pos,
                                                           GtbStructuresSettings settings,
                                                           GtbGame.State state,
                                                           boolean observeChatNotices)
            throws ReflectiveOperationException {
        boolean raining = booleanValue(invokeAny(level, "isRaining", "method_8419"));
        boolean thundering = booleanValue(invokeAny(level, "isThundering", "method_8546"));
        float rainStrength = floatValue(invokeAnyWithFloat(level, 1.0F,
                "getRainGradient", "getRainLevel", "method_8430"));
        float thunderStrength = floatValue(invokeAnyWithFloat(level, 1.0F,
                "getThunderGradient", "getThunderLevel", "method_8478"));
        long now = System.currentTimeMillis();
        WorldEnvironmentLogic.observeWorldWeatherSnapshot(raining, thundering,
                rainStrength, thunderStrength, now);
        observeLightningEntities(level, now);
        BiomeSnapshot biome = biomeSnapshot(level, pos);
        long worldTime = worldTime(level);
        if (observeChatNotices) {
            String weatherLabel = WorldEnvironmentLogic.weatherLabelForLiveSignals(
                    raining, thundering, rainStrength, thunderStrength,
                    biome.name, biome.precipitationType, now);
            String biomeLabel = WorldEnvironmentLogic.biomeLabel(biome.name);
            for (String message : WorldEnvironmentLogic.observeChatNoticeChanges(
                    state, weatherLabel, biomeLabel, worldTime,
                    settings.environmentWeatherChatNotice,
                    settings.environmentBiomeChatNotice,
                    settings.environmentTimeChatNotice)) {
                GameStats.INSTANCE.queueNotificationMessage(message);
            }
        }
        return WorldEnvironmentLogic.liveRows(settings.environmentOverlay,
                settings.environmentWeatherEnabled, settings.environmentBiomeEnabled,
                settings.environmentTimeEnabled,
                raining, thundering, rainStrength, thunderStrength,
                biome.name, biome.precipitationType,
                worldTime,
                settings.environmentTimeIconPreset, now);
    }

    private static long worldTime(Object level) throws ReflectiveOperationException {
        return longValue(invokeAny(level, "getTimeOfDay", "getDayTime", "method_8532"));
    }

    private static void observeLightningEntities(Object level, long now) {
        try {
            Object entities = invokeAny(level, "entitiesForRendering", "getEntities", "method_18112");
            if (!(entities instanceof Iterable)) return;
            Class<?> lightningClass = lightningEntityClass();
            for (Object entity : (Iterable<?>) entities) {
                if ((lightningClass != null && lightningClass.isInstance(entity))
                        || isLightningEntityClassName(entity == null ? null : entity.getClass().getName())) {
                    WorldEnvironmentLogic.observeLightningSignal(now);
                    return;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Best-effort parity with the 1.8.9 packet signal.
        }
    }

    private static Class<?> lightningEntityClass() {
        try {
            return classAny("net.minecraft.entity.LightningEntity",
                    "net.minecraft.entity.LightningBoltEntity",
                    "net.minecraft.world.entity.LightningBolt",
                    "net.minecraft.class_1538");
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }

    static boolean isLightningEntityClassNameForTest(String className) {
        return isLightningEntityClassName(className);
    }

    private static boolean isLightningEntityClassName(String className) {
        if (className == null) return false;
        String key = className.toLowerCase(Locale.ROOT);
        return "net.minecraft.class_1538".equals(key)
                || (key.contains("lightning") && key.contains("entity"))
                || (key.contains("lightning") && key.contains("bolt"));
    }

    private static BiomeSnapshot biomeSnapshot(Object level, Object pos)
            throws ReflectiveOperationException {
        Object safePos = pos == null ? blockOrigin() : pos;
        Object entry = invokeAnyWithArg(level, safePos, "getBiome", "method_23753", "method_22393");
        if (entry == null) return new BiomeSnapshot("", "");
        Object biome = invokeAny(entry, "value", "method_40232");
        Object key = invokeAny(entry, "getKey", "method_40230");
        String name;
        if (key instanceof Optional) {
            Optional<?> optional = (Optional<?>) key;
            if (optional.isPresent()) {
                key = optional.get();
            } else {
                key = null;
            }
        }
        if (key == null) {
            name = biome == null ? "" : String.valueOf(biome);
        } else {
            Object id = invokeAny(key, "getValue", "method_29177");
            Object path = invokeAny(id, "getPath", "method_12832");
            name = path == null ? "" : path.toString().replace('_', ' ');
        }

        String precipitationType = "";
        if (biome != null) {
            int seaLevel = intValue(invokeAny(level, "getSeaLevel", "method_8615"));
            Object precipitation = invokeAnyArgs(biome,
                    new String[] {"getPrecipitationAt", "getPrecipitation", "method_48162"},
                    safePos, Integer.valueOf(seaLevel));
            if (precipitation instanceof Enum<?>) {
                precipitationType = ((Enum<?>) precipitation).name();
            } else if (precipitation != null) {
                precipitationType = precipitation.toString();
            }
        }
        return new BiomeSnapshot(name, precipitationType);
    }

    private static final class BiomeSnapshot {
        private final String name;
        private final String precipitationType;

        private BiomeSnapshot(String name, String precipitationType) {
            this.name = name;
            this.precipitationType = precipitationType;
        }
    }

    private static GtbGame.State currentState() {
        GtbGame game = GameTracker.INSTANCE.game;
        return game == null ? GtbGame.State.NONE : game.state;
    }

    private static void renderRows(Object graphics, List<WorldEnvironmentLogic.Row> rows,
                                   GtbStructuresSettings settings)
            throws ReflectiveOperationException {
        if (graphics == null || rows == null || rows.isEmpty()) return;
        Object mc = minecraftClient();
        Object font = fieldAny(mc, "textRenderer", "font", "field_1772");
        if (font == null) return;

        int panelHeight = rows.size() * WorldEnvironmentLogic.ROW_HEIGHT;
        Object window = invokeAny(mc, "getWindow", "method_22683");
        int screenWidth = intValue(invokeAny(window, "getScaledWidth", "getGuiScaledWidth", "method_4486"));
        int screenHeight = intValue(invokeAny(window, "getScaledHeight", "getGuiScaledHeight", "method_4502"));
        int x = HudPosition.left(settings.environmentPosX, screenWidth,
                WorldEnvironmentLogic.PANEL_WIDTH, MARGIN);
        int bottom = HudPosition.bottom(settings.environmentPosY, screenHeight, panelHeight, MARGIN);
        int y = bottom - panelHeight;
        if (settings.environmentBackground) {
            fill(graphics, x - 1, y - 1,
                    x + WorldEnvironmentLogic.PANEL_WIDTH + 1, y + panelHeight + 1,
                    ScoreboardLayout.BACKGROUND_COLOR);
        }
        if (InterfacePreview.isActive()) {
            PreviewLayoutEditor.registerWithBackground(
                    WorldEnvironmentLogic.previewRegistrationId(rows.size(), rows.get(0).id),
                    x, y, WorldEnvironmentLogic.PANEL_WIDTH, panelHeight,
                    previewTarget(rows),
                    (bgLeft, bgTop, bgRight, bgBottom, color) ->
                            fill(graphics, bgLeft, bgTop, bgRight, bgBottom, color));
        }
        for (WorldEnvironmentLogic.Row row : rows) {
            renderRow(graphics, font, x, y, row);
            y += WorldEnvironmentLogic.ROW_HEIGHT;
        }
    }

    private static PreviewLayoutEditor.Target previewTarget(List<WorldEnvironmentLogic.Row> rows) {
        if (rows == null || rows.size() != 1) return PreviewLayoutEditor.ENVIRONMENT;
        String id = rows.get(0).id;
        if ("environment_weather".equals(id)) return PreviewLayoutEditor.ENVIRONMENT_WEATHER;
        if ("environment_biome".equals(id)) return PreviewLayoutEditor.ENVIRONMENT_BIOME;
        if ("environment_time".equals(id)) return PreviewLayoutEditor.ENVIRONMENT_TIME;
        return PreviewLayoutEditor.ENVIRONMENT;
    }

    private static void renderRow(Object graphics, Object font, int x, int y,
                                  WorldEnvironmentLogic.Row row)
            throws ReflectiveOperationException {
        int fontHeight = intField(font, 9, "fontHeight", "lineHeight", "field_2000");
        int iconY = y + (WorldEnvironmentLogic.ROW_HEIGHT - WorldEnvironmentLogic.ICON_SIZE) / 2;
        try {
            Object stack = itemStack(row.iconKey);
            if (stack != null && tryInvokeAnyArgs(graphics,
                    new String[] {"drawItem", "renderItem", "item", "method_51427"},
                    stack, x, iconY)) {
                tryInvokeAnyArgs(graphics, new String[] {
                                "drawStackOverlay", "renderItemDecorations", "itemDecorations", "method_51431"},
                        font, stack, x, iconY);
            } else if (WARNED_ICON_FAILURES.putIfAbsent(row.iconKey, Boolean.TRUE) == null) {
                LOGGER.warn("No compatible item renderer for GtbStructures environment icon {}", row.iconKey);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (WARNED_ICON_FAILURES.putIfAbsent(row.iconKey, Boolean.TRUE) == null) {
                LOGGER.warn("Unable to render GtbStructures environment icon {}", row.iconKey, e);
            }
        }

        int textX = x + WorldEnvironmentLogic.ICON_SIZE + WorldEnvironmentLogic.TEXT_GAP;
        int maxTextWidth = WorldEnvironmentLogic.PANEL_WIDTH - (textX - x) - 3;
        tryInvokeAnyArgs(graphics, new String[] {"drawText", "drawString", "text", "method_51433"},
                font, ellipsize(font, row.text, maxTextWidth), textX,
                y + (WorldEnvironmentLogic.ROW_HEIGHT - fontHeight) / 2,
                opaqueColor(0xFFFFFF), true);
    }

    private static int opaqueColor(int color) {
        return (color & 0xFF000000) == 0 ? 0xFF000000 | color : color;
    }

    private static void fill(Object graphics, int left, int top, int right, int bottom, int color)
            throws ReflectiveOperationException {
        tryInvokeAnyArgs(graphics, new String[] {"fill", "method_25294"},
                left, top, right, bottom, color);
    }

    private static String ellipsize(Object font, String text, int maxWidth) throws ReflectiveOperationException {
        if (text == null) return "";
        if (textWidth(font, text) <= maxWidth) return text;
        String ellipsis = "...";
        int ellipsisWidth = textWidth(font, ellipsis);
        String out = text;
        while (!out.isEmpty() && textWidth(font, out) + ellipsisWidth > maxWidth) {
            out = out.substring(0, out.length() - 1);
        }
        return out + ellipsis;
    }

    private static int textWidth(Object font, String text) throws ReflectiveOperationException {
        return intValue(invokeAnyWithArg(font, text, "getWidth", "width", "method_1727"));
    }

    private static Object itemStack(String key) throws ReflectiveOperationException {
        Object cached = ICON_STACKS.get(key);
        if (cached != null) return cached == CACHE_MISS ? null : cached;
        try {
            Object item = itemForKey(key);
            if (item == null) {
                ICON_STACKS.putIfAbsent(key, CACHE_MISS);
                return null;
            }
            Class<?> stackClass = itemStackClass();
            for (Constructor<?> constructor : stackClass.getConstructors()) {
                if (constructor.getParameterCount() == 1
                        && isCompatible(constructor.getParameterTypes()[0], item)) {
                    Object resolved = constructor.newInstance(item);
                    Object raced = ICON_STACKS.putIfAbsent(key, resolved);
                    return raced == null ? resolved : raced;
                }
            }
            ICON_STACKS.putIfAbsent(key, CACHE_MISS);
            return null;
        } catch (ReflectiveOperationException | RuntimeException error) {
            ICON_STACKS.putIfAbsent(key, CACHE_MISS);
            throw error;
        }
    }

    private static Object itemForKey(String key) throws ReflectiveOperationException {
        String registry = modernIconRegistry(key);
        Object cached = ICON_ITEMS.get(registry);
        if (cached != null) return cached == CACHE_MISS ? null : cached;
        Object id = identifier(registry);
        Object itemRegistry = itemRegistry();
        Object resolved = null;
        if (id == null || itemRegistry == null) {
            ICON_ITEMS.putIfAbsent(registry, CACHE_MISS);
            return null;
        }
        Class<?> stackClass = itemStackClass();
        for (Method method : itemRegistry.getClass().getMethods()) {
            if (method.getParameterCount() != 1 || !isCompatible(method.getParameterTypes()[0], id)) continue;
            Object item = method.invoke(itemRegistry, id);
            if (item == null) continue;
            for (Constructor<?> constructor : stackClass.getConstructors()) {
                if (constructor.getParameterCount() == 1
                        && isCompatible(constructor.getParameterTypes()[0], item)) {
                    resolved = item;
                    break;
                }
            }
            if (resolved != null) break;
        }
        Object stored = resolved == null ? CACHE_MISS : resolved;
        Object raced = ICON_ITEMS.putIfAbsent(registry, stored);
        Object result = raced == null ? stored : raced;
        return result == CACHE_MISS ? null : result;
    }

    private static String modernIconRegistry(String key) {
        if ("blaze_rod".equals(key)) return "minecraft:blaze_rod";
        if ("water_bucket".equals(key)) return "minecraft:water_bucket";
        if ("snowball".equals(key)) return "minecraft:snowball";
        if ("double_plant:0".equals(key)) return "minecraft:sunflower";
        if ("netherrack".equals(key)) return "minecraft:netherrack";
        if ("end_stone".equals(key)) return "minecraft:end_stone";
        if ("sand:1".equals(key)) return "minecraft:red_sand";
        if ("sand".equals(key)) return "minecraft:sand";
        if ("sapling:0".equals(key)) return "minecraft:oak_sapling";
        if ("vine".equals(key)) return "minecraft:vine";
        if ("packed_ice".equals(key)) return "minecraft:packed_ice";
        if ("waterlily".equals(key)) return "minecraft:lily_pad";
        if ("sapling:4".equals(key)) return "minecraft:acacia_sapling";
        if ("clock".equals(key)) return "minecraft:clock";
        if ("obsidian".equals(key)) return "minecraft:obsidian";
        if ("wool:7".equals(key)) return "minecraft:gray_wool";
        if ("stone:3".equals(key)) return "minecraft:diorite";
        if ("wool:4".equals(key)) return "minecraft:yellow_wool";
        if ("gold_block".equals(key)) return "minecraft:gold_block";
        if ("stained_hardened_clay:1".equals(key)) return "minecraft:orange_terracotta";
        if ("stained_hardened_clay:9".equals(key)) return "minecraft:cyan_terracotta";
        if ("wool:15".equals(key)) return "minecraft:black_wool";
        if ("grass".equals(key) || "grass_block".equals(key)) return "minecraft:grass_block";
        throw new IllegalArgumentException("Unknown environment icon key " + key);
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
        Object cached = IDENTIFIERS.get(value);
        if (cached != null) return cached == CACHE_MISS ? null : cached;
        Class<?> idClass = classAny("net.minecraft.resources.ResourceLocation",
                "net.minecraft.resources.Identifier",
                "net.minecraft.util.Identifier", "net.minecraft.class_2960", "amj", "amo");
        Object resolved = createIdentifier(idClass, value);
        Object stored = resolved == null ? CACHE_MISS : resolved;
        Object raced = IDENTIFIERS.putIfAbsent(value, stored);
        Object result = raced == null ? stored : raced;
        return result == CACHE_MISS ? null : result;
    }

    private static Object createIdentifier(Class<?> idClass, String value)
            throws ReflectiveOperationException {
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
            return null;
        }
    }

    private static Object minecraftClient() throws ReflectiveOperationException {
        Class<?> clientClass = classAny("net.minecraft.client.MinecraftClient",
                "net.minecraft.client.Minecraft", "net.minecraft.class_310");
        Method method = methodAny(clientClass, "getInstance", "method_1551");
        return method.invoke(null);
    }

    private static Object blockOrigin() throws ReflectiveOperationException {
        return staticFieldAny(classAny("net.minecraft.util.math.BlockPos", "net.minecraft.class_2338"),
                "ORIGIN", "field_10980");
    }

    private static Class<?> classAny(String... names) throws ClassNotFoundException {
        return RuntimeReflectionCache.classAny(names);
    }

    private static Method methodAny(Class<?> type, String... names) throws NoSuchMethodException {
        return RuntimeReflectionCache.methodAny(type, names);
    }

    private static Object fieldAny(Object target, String... names) throws ReflectiveOperationException {
        return RuntimeReflectionCache.fieldAny(target, names);
    }

    private static Object staticFieldAny(Class<?> type, String... names) throws ReflectiveOperationException {
        return RuntimeReflectionCache.staticFieldAny(type, names);
    }

    private static Object invokeAny(Object target, String... names) throws ReflectiveOperationException {
        return RuntimeReflectionCache.invokeAny(target, names);
    }

    private static Object invokeAnyWithFloat(Object target, float value, String... names)
            throws ReflectiveOperationException {
        return RuntimeReflectionCache.invokeAnyArgs(target, names, Float.valueOf(value));
    }

    private static Object invokeAnyWithArg(Object target, Object arg, String... names)
            throws ReflectiveOperationException {
        return RuntimeReflectionCache.invokeAnyWithArg(target, arg, names);
    }

    private static Object invokeAnyArgs(Object target, String[] names, Object... args)
            throws ReflectiveOperationException {
        return RuntimeReflectionCache.invokeAnyArgs(target, names, args);
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

    private static int intField(Object target, int fallback, String... names) {
        try {
            Object value = fieldAny(target, names);
            return intValue(value);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return fallback;
        }
    }

    private static int intValue(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private static long longValue(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    private static float floatValue(Object value) {
        return value instanceof Number ? ((Number) value).floatValue() : 0.0F;
    }

    private static boolean booleanValue(Object value) {
        return value instanceof Boolean && ((Boolean) value).booleanValue();
    }
}
