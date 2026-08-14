package com.gtbstructures.overlay;

import com.gtbstructures.GtbStructuresSettings;
import com.gtbstructures.ClientChatBridge;
import com.gtbstructures.chat.ChatHeader;
import com.gtbstructures.scoreboard.GameTracker;
import com.gtbstructures.scoreboard.GtbGame;
import com.gtbstructures.scoreboard.InterfacePreview;
import com.gtbstructures.scoreboard.PreviewLayoutEditor;
import com.gtbstructures.scoreboard.ScoreboardLayout;
import com.gtbstructures.stats.GameStats;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

public final class WorldEnvironmentOverlayRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(WorldEnvironmentOverlayRenderer.class);
    private static final int MARGIN = 2;
    private static final WorldEnvironmentLogic.ChatNoticeTracker CHAT_NOTICE_TRACKER =
            new WorldEnvironmentLogic.ChatNoticeTracker();
    private static volatile Method resourceKeyIdentifierMethod;
    private static volatile Method identifierPathMethod;

    private WorldEnvironmentOverlayRenderer() {
    }

    public static void register() {
        AddGuiOverlayLayersEvent.BUS.addListener(WorldEnvironmentOverlayRenderer::addLayer);
    }

    private static void addLayer(AddGuiOverlayLayersEvent event) {
        ForgeHudLayerBridge.addAboveChat(event.getLayeredDraw(), "world_environment",
                WorldEnvironmentOverlayRenderer::render);
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        try {
            renderEnvironment(graphics);
        } catch (RuntimeException e) {
            LOGGER.warn("Unable to render GtbStructures world environment overlay", e);
        }
    }

    private static void renderEnvironment(GuiGraphicsExtractor graphics) {
        GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
        if (!settings.modEnabled) {
            CHAT_NOTICE_TRACKER.reset();
            return;
        }
        List<WorldEnvironmentLogic.Row> rows;
        Minecraft mc = Minecraft.getInstance();
        if (InterfacePreview.isActive()) {
            CHAT_NOTICE_TRACKER.reset();
            if (mc.level != null && mc.player != null) {
                rows = liveRows(mc.level, mc.player.blockPosition(), settings, false);
            } else {
                rows = WorldEnvironmentLogic.previewRows(settings.environmentOverlay,
                        settings.environmentWeatherEnabled, settings.environmentBiomeEnabled,
                        settings.environmentTimeEnabled, settings.environmentTimeIconPreset);
            }
        } else {
            if (!GameStats.INSTANCE.shouldRenderRoundOverlays()
                    || !WorldEnvironmentLogic.shouldRenderLiveForState(currentState())) {
                CHAT_NOTICE_TRACKER.reset();
                return;
            }
            if (mc.level == null || mc.player == null) {
                CHAT_NOTICE_TRACKER.reset();
                return;
            }
            boolean observeChatNotices = chatNoticesEnabled(settings);
            if (!settings.environmentOverlay && !observeChatNotices) {
                CHAT_NOTICE_TRACKER.reset();
                return;
            }
            if (!observeChatNotices) CHAT_NOTICE_TRACKER.reset();
            rows = liveRows(mc.level, mc.player.blockPosition(), settings, observeChatNotices);
        }
        renderRows(graphics, rows, settings);
    }

    private static List<WorldEnvironmentLogic.Row> liveRows(ClientLevel level, BlockPos pos,
                                                           GtbStructuresSettings settings,
                                                           boolean observeChatNotices) {
        boolean raining = level.isRaining();
        boolean thundering = level.isThundering();
        float rainStrength = level.getRainLevel(1.0F);
        float thunderStrength = level.getThunderLevel(1.0F);
        long now = System.currentTimeMillis();
        WorldEnvironmentLogic.observeWorldWeatherSnapshot(raining, thundering,
                rainStrength, thunderStrength, now);
        observeLightningEntities(level, now);
        BiomeSnapshot biome = biomeSnapshot(level, pos);
        String weatherLabel = WorldEnvironmentLogic.weatherLabelForLiveSignals(
                raining, thundering, rainStrength, thunderStrength,
                biome.name, biome.precipitationType, now);
        String biomeLabel = WorldEnvironmentLogic.biomeLabel(biome.name);
        long worldTime = level.getOverworldClockTime();
        if (observeChatNotices) {
            List<String> notices = CHAT_NOTICE_TRACKER.observe(true,
                    weatherLabel, biomeLabel, worldTime,
                    settings.environmentWeatherChatNotice,
                    settings.environmentBiomeChatNotice,
                    settings.environmentTimeChatNotice);
            sendChatNotices(notices);
        }
        return WorldEnvironmentLogic.liveRows(settings.environmentOverlay,
                settings.environmentWeatherEnabled, settings.environmentBiomeEnabled,
                settings.environmentTimeEnabled,
                raining, thundering, rainStrength, thunderStrength,
                biome.name, biome.precipitationType, worldTime,
                settings.environmentTimeIconPreset, now);
    }

    private static boolean chatNoticesEnabled(GtbStructuresSettings settings) {
        return settings.environmentWeatherChatNotice
                || settings.environmentBiomeChatNotice
                || settings.environmentTimeChatNotice;
    }

    private static void sendChatNotices(List<String> notices) {
        if (notices == null || notices.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui == null) return;
        String prefix = ChatHeader.prefix();
        for (String notice : notices) {
            ClientChatBridge.addLocalSystemMessage(Component.literal(prefix + notice));
        }
    }

    private static void observeLightningEntities(ClientLevel level, long now) {
        try {
            for (Entity entity : level.entitiesForRendering()) {
                if (entity instanceof LightningBolt
                        || isLightningEntityClassName(entity == null ? null : entity.getClass().getName())) {
                    WorldEnvironmentLogic.observeLightningSignal(now);
                    return;
                }
            }
        } catch (RuntimeException ignored) {
            // Best-effort parity with the 1.8.9 packet signal.
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

    private static BiomeSnapshot biomeSnapshot(ClientLevel level, BlockPos pos) {
        BlockPos safePos = pos == null ? BlockPos.ZERO : pos;
        Holder<Biome> biome = level.getBiome(safePos);
        if (biome == null) return new BiomeSnapshot("", "");
        String name = biome.unwrapKey()
                .map(WorldEnvironmentOverlayRenderer::resourceKeyPath)
                .orElseGet(() -> String.valueOf(biome.value()));
        Biome.Precipitation precipitation = biome.value()
                .getPrecipitationAt(safePos, level.getSeaLevel());
        return new BiomeSnapshot(name, precipitation == null ? "" : precipitation.name());
    }

    static String resourceKeyPathForTest(Object resourceKey) {
        return resourceKeyPath(resourceKey);
    }

    private static String resourceKeyPath(Object resourceKey) {
        if (resourceKey == null) return "";
        try {
            Method identifierMethod = resourceKeyIdentifierMethod;
            if (identifierMethod == null
                    || !identifierMethod.getDeclaringClass().isInstance(resourceKey)) {
                identifierMethod = findResourceKeyIdentifierMethod(resourceKey.getClass());
                resourceKeyIdentifierMethod = identifierMethod;
            }
            Object identifier = identifierMethod.invoke(resourceKey);
            if (identifier == null) return "";

            Method pathMethod = identifierPathMethod;
            if (pathMethod == null || !pathMethod.getDeclaringClass().isInstance(identifier)) {
                pathMethod = identifier.getClass().getMethod("getPath");
                identifierPathMethod = pathMethod;
            }
            Object path = pathMethod.invoke(identifier);
            return path == null ? "" : path.toString().replace('_', ' ');
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) throw runtimeException;
            throw new IllegalStateException("Unable to read the biome identifier", cause);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to read the biome identifier", e);
        }
    }

    private static Method findResourceKeyIdentifierMethod(Class<?> resourceKeyClass)
            throws NoSuchMethodException {
        for (String methodName : new String[] {"identifier", "location"}) {
            try {
                return resourceKeyClass.getMethod(methodName);
            } catch (NoSuchMethodException ignored) {
                // Try the identifier accessor from the other supported Minecraft version.
            }
        }
        throw new NoSuchMethodException(resourceKeyClass.getName() + ".identifier/location()");
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

    private static void renderRows(GuiGraphicsExtractor graphics, List<WorldEnvironmentLogic.Row> rows,
                                   GtbStructuresSettings settings) {
        if (graphics == null || rows == null || rows.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        if (font == null) return;

        int panelHeight = rows.size() * WorldEnvironmentLogic.ROW_HEIGHT;
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int x = HudPosition.left(settings.environmentPosX, screenWidth,
                WorldEnvironmentLogic.PANEL_WIDTH, MARGIN);
        int bottom = HudPosition.bottom(settings.environmentPosY, screenHeight, panelHeight, MARGIN);
        int y = bottom - panelHeight;
        if (settings.environmentBackground) {
            graphics.fill(x - 1, y - 1,
                    x + WorldEnvironmentLogic.PANEL_WIDTH + 1, y + panelHeight + 1,
                    ScoreboardLayout.BACKGROUND_COLOR);
        }
        if (InterfacePreview.isActive()) {
            PreviewLayoutEditor.registerWithBackground(
                    WorldEnvironmentLogic.previewRegistrationId(rows.size(), rows.get(0).id),
                    x, y, WorldEnvironmentLogic.PANEL_WIDTH, panelHeight,
                    previewTarget(rows),
                    (left, top, right, bottomEdge, color) ->
                            graphics.fill(left, top, right, bottomEdge, color));
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

    private static void renderRow(GuiGraphicsExtractor graphics, Font font, int x, int y,
                                  WorldEnvironmentLogic.Row row) {
        int iconY = y + (WorldEnvironmentLogic.ROW_HEIGHT - WorldEnvironmentLogic.ICON_SIZE) / 2;
        ItemStack stack = itemStack(row.iconKey);
        graphics.item(stack, x, iconY);
        graphics.itemDecorations(font, stack, x, iconY);

        int textX = x + WorldEnvironmentLogic.ICON_SIZE + WorldEnvironmentLogic.TEXT_GAP;
        int maxTextWidth = WorldEnvironmentLogic.PANEL_WIDTH - (textX - x) - 3;
        graphics.text(font, ellipsize(font, row.text, maxTextWidth), textX,
                y + (WorldEnvironmentLogic.ROW_HEIGHT - font.lineHeight) / 2,
                opaqueColor(0xFFFFFF), true);
    }

    private static String ellipsize(Font font, String text, int maxWidth) {
        if (text == null) return "";
        if (font.width(text) <= maxWidth) return text;
        String ellipsis = "...";
        int ellipsisWidth = font.width(ellipsis);
        String out = text;
        while (!out.isEmpty() && font.width(out) + ellipsisWidth > maxWidth) {
            out = out.substring(0, out.length() - 1);
        }
        return out + ellipsis;
    }

    private static int opaqueColor(int color) {
        return (color & 0xFF000000) == 0 ? 0xFF000000 | color : color;
    }

    private static ItemStack itemStack(String key) {
        return new ItemStack(itemForKey(key));
    }

    private static Item itemForKey(String key) {
        if ("blaze_rod".equals(key)) return Items.BLAZE_ROD;
        if ("water_bucket".equals(key)) return Items.WATER_BUCKET;
        if ("snowball".equals(key)) return Items.SNOWBALL;
        if ("double_plant:0".equals(key)) return Items.SUNFLOWER;
        if ("netherrack".equals(key)) return Items.NETHERRACK;
        if ("end_stone".equals(key)) return Items.END_STONE;
        if ("sand:1".equals(key)) return Items.RED_SAND;
        if ("sand".equals(key)) return Items.SAND;
        if ("sapling:0".equals(key)) return Items.OAK_SAPLING;
        if ("vine".equals(key)) return Items.VINE;
        if ("packed_ice".equals(key)) return Items.PACKED_ICE;
        if ("waterlily".equals(key)) return Items.LILY_PAD;
        if ("sapling:4".equals(key)) return Items.ACACIA_SAPLING;
        if ("clock".equals(key)) return Items.CLOCK;
        if ("obsidian".equals(key)) return Items.OBSIDIAN;
        if ("wool:7".equals(key)) return Items.GRAY_WOOL;
        if ("stone:3".equals(key)) return Items.DIORITE;
        if ("wool:4".equals(key)) return Items.YELLOW_WOOL;
        if ("gold_block".equals(key)) return Items.GOLD_BLOCK;
        if ("stained_hardened_clay:1".equals(key)) return Items.ORANGE_TERRACOTTA;
        if ("stained_hardened_clay:9".equals(key)) return Items.CYAN_TERRACOTTA;
        if ("wool:15".equals(key)) return Items.BLACK_WOOL;
        return Items.GRASS_BLOCK;
    }
}
