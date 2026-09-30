package com.gtbstructures.overlay;

import com.gtbstructures.GtbStructures;
import com.gtbstructures.commands.CommandGtb;
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
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class WorldEnvironmentOverlay {
    public static final WorldEnvironmentOverlay INSTANCE = new WorldEnvironmentOverlay();

    private final EnvironmentChangeNotifier changeNotifier = new EnvironmentChangeNotifier();

    private static final int PANEL_WIDTH = 126;
    private static final int ROW_HEIGHT = 18;
    private static final int ICON_SIZE = 16;
    private static final int TEXT_GAP = 4;
    private static final float RAIN_STRENGTH_THRESHOLD = 0.2F;
    private static final float SERVER_RAIN_STRENGTH_THRESHOLD = 0.001F;
    private static final float THUNDER_STRENGTH_THRESHOLD = 0.001F;
    private static final long RAIN_START_GRACE_MS = 3000L;
    private static final long SERVER_RAIN_SIGNAL_TTL_MS = 180000L;
    private static final long SERVER_THUNDER_SIGNAL_TTL_MS = 180000L;
    private static final long LIGHTNING_SIGNAL_TTL_MS = 30000L;
    private static final long CLEAR_WEATHER_SIGNAL_TTL_MS = 12000L;

    private static volatile float serverRainStrength;
    private static volatile long serverRainUpdatedMs;
    private static volatile boolean serverRainActive;
    private static volatile long rainStartObservedMs;
    private static volatile float serverThunderStrength;
    private static volatile long serverThunderUpdatedMs;
    private static volatile long lightningObservedMs;
    private static volatile long clearWeatherObservedMs;

    private WorldEnvironmentOverlay() {
    }

    public void onClientTick() {
        if (!runtimeEnabledForMaster(GtbStructures.modEnabled)) {
            resetChangeNotices();
            return;
        }
        if (!chatNoticeSamplingEnabled()) {
            resetChangeNotices();
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.theWorld == null || mc.thePlayer == null) {
            resetChangeNotices();
            return;
        }

        BiomeGenBase biome = mc.theWorld.getBiomeGenForCoords(mc.thePlayer.getPosition());
        List<EnvironmentChangeNotifier.Notice> notices = changeNotifier.observe(
                shouldRenderLiveForState(GameStats.INSTANCE.getState()),
                InterfacePreview.isActive(),
                weatherLabel(mc.theWorld, biome),
                biomeLabel(biomeName(biome)),
                mc.theWorld.getWorldTime(),
                GtbStructures.environmentWeatherChatNotice,
                GtbStructures.environmentBiomeChatNotice,
                GtbStructures.environmentTimeChatNotice);
        for (EnvironmentChangeNotifier.Notice notice : notices) {
            mc.thePlayer.addChatMessage(new ChatComponentText(formatChangeNotice(CommandGtb.head, notice)));
        }
    }

    private static boolean chatNoticeSamplingEnabled() {
        return chatNoticeSamplingEnabledForTest(
                GtbStructures.environmentWeatherChatNotice,
                GtbStructures.environmentBiomeChatNotice,
                GtbStructures.environmentTimeChatNotice);
    }

    static boolean chatNoticeSamplingEnabledForTest(boolean weather, boolean biome, boolean time) {
        return weather || biome || time;
    }

    public void resetChangeNotices() {
        changeNotifier.reset();
    }

    private static String formatChangeNotice(String prefix, EnvironmentChangeNotifier.Notice notice) {
        return (prefix == null ? "" : prefix) + "\u00a7f" + notice.metric
                + " \u00a77set to \u00a7f" + notice.state;
    }

    static String formatChangeNoticeForTest(String prefix, String metric, String state) {
        return formatChangeNotice(prefix, new EnvironmentChangeNotifier.Notice(metric, state));
    }

    @SubscribeEvent
    public void onRender(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.CHAT) return;
        if (!runtimeEnabledForMaster(GtbStructures.modEnabled)) return;
        try {
            if (InterfacePreview.isActive()) {
                renderPreview(Minecraft.getMinecraft());
                return;
            }
            renderLive();
        } catch (Throwable ignored) {
            // HUD overlays must never break vanilla rendering.
        }
    }

    public static void renderPreview(Minecraft mc) {
        renderPreview(mc, false);
    }

    public static void collectPreviewLabel(Minecraft mc) {
        renderPreview(mc, true);
    }

    private static void renderPreview(Minecraft mc, boolean layoutOnly) {
        if (!runtimeEnabledForMaster(GtbStructures.modEnabled)) return;
        if (!InterfacePreview.isActive()) return;
        if (!GtbStructures.environmentOverlay) return;
        if (mc != null && mc.theWorld != null && mc.thePlayer != null) {
            renderRows(mc, environmentRows(mc.theWorld, mc.thePlayer.getPosition()), layoutOnly);
        } else {
            renderRows(mc, fallbackPreviewRows(), layoutOnly);
        }
    }

    private static List<Row> fallbackPreviewRows() {
        ArrayList<Row> rows = new ArrayList<Row>();
        if (GtbStructures.environmentWeatherEnabled) {
            rows.add(new Row("environment_weather", weatherIcon("Sunny"), "\u00a77Weather: \u00a7fSunny"));
        }
        if (GtbStructures.environmentBiomeEnabled) {
            rows.add(new Row("environment_biome", biomeIcon("Plains"), "\u00a77Biome: \u00a7fPlains"));
        }
        if (GtbStructures.environmentTimeEnabled) {
            rows.add(new Row("environment_time", timeIcon(6000L), "\u00a77Time: \u00a7f12:00"));
        }
        return rows;
    }

    private static void renderLive() {
        if (!runtimeEnabledForMaster(GtbStructures.modEnabled)) return;
        if (!GtbStructures.environmentOverlay) return;
        if (!GameStats.INSTANCE.shouldRenderRoundOverlays()) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.theWorld == null || mc.thePlayer == null || mc.fontRendererObj == null) return;

        renderRows(mc, environmentRows(mc.theWorld, mc.thePlayer.getPosition()), false);
    }

    static boolean shouldRenderLiveForState(GameStats.TrackingState state) {
        return state == GameStats.TrackingState.ROUND_PRE
                || state == GameStats.TrackingState.ROUND_BUILD
                || state == GameStats.TrackingState.ROUND_END;
    }

    private static List<Row> environmentRows(World world, BlockPos pos) {
        if (world == null) return new ArrayList<Row>();
        if (pos == null) pos = new BlockPos(0, 0, 0);
        BiomeGenBase biome = world.getBiomeGenForCoords(pos);
        ArrayList<Row> rows = new ArrayList<Row>();
        if (GtbStructures.environmentWeatherEnabled) {
            String weather = weatherLabel(world, biome);
            rows.add(new Row("environment_weather", weatherIcon(weather), "\u00a77Weather: \u00a7f" + weather));
        }
        if (GtbStructures.environmentBiomeEnabled) {
            String biomeLabel = biomeLabel(biomeName(biome));
            rows.add(new Row("environment_biome", biomeIcon(biomeLabel), "\u00a77Biome: \u00a7f" + biomeLabel));
        }
        if (GtbStructures.environmentTimeEnabled) {
            long time = world.getWorldTime();
            rows.add(new Row("environment_time", timeIcon(time), "\u00a77Time: \u00a7f" + timeLabel(time)));
        }
        return rows;
    }

    private static void renderRows(Minecraft mc, List<Row> rows, boolean layoutOnly) {
        if (mc == null || mc.fontRendererObj == null || rows == null || rows.isEmpty()) return;
        ScaledResolution sr = new ScaledResolution(mc);
        FontRenderer fr = mc.fontRendererObj;

        int panelHeight = rows.size() * ROW_HEIGHT;
        float panelScale = HudPanelScale.normalize(GtbStructures.environmentScale);
        int scaledWidth = HudPanelScale.scaledPixels(PANEL_WIDTH, panelScale);
        int scaledHeight = HudPanelScale.scaledPixels(panelHeight, panelScale);
        int x = HudPosition.left(GtbStructures.environmentPosX,
                sr.getScaledWidth(), scaledWidth, 2);
        int bottom = HudPosition.bottom(GtbStructures.environmentPosY,
                sr.getScaledHeight(), scaledHeight, 2);
        int top = bottom - scaledHeight;
        if (layoutOnly) {
            PreviewLayoutEditor.register(previewRegistrationIdForRows(rows),
                    x, top, scaledWidth, scaledHeight, previewTargetForRows(rows));
            return;
        }
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x, top, 0.0F);
            GlStateManager.scale(panelScale, panelScale, 1.0F);
            drawBackgroundIfEnabled(0, 0, PANEL_WIDTH, panelHeight);
            int y = 0;
            for (Row row : rows) {
                renderRow(mc, fr, 0, y, row);
                y += ROW_HEIGHT;
            }
        } finally {
            GlStateManager.popMatrix();
        }
    }

    private static void drawBackgroundIfEnabled(int x, int y, int width, int height) {
        if (!GtbStructures.environmentBackground) return;
        Gui.drawRect(x - 1, y - 1, x + width + 1, y + height + 1, ScoreboardLayout.BACKGROUND_COLOR);
    }

    private static String previewRegistrationIdForRows(List<Row> rows) {
        return rows != null && rows.size() == 1 ? rows.get(0).id : "environment";
    }

    private static PreviewLayoutEditor.Target previewTargetForRows(List<Row> rows) {
        return rows != null && rows.size() == 1 ? previewTarget(rows.get(0).id) : PreviewLayoutEditor.ENVIRONMENT;
    }

    static String previewRegistrationIdForTest(int rowCount, String onlyRowId) {
        ArrayList<Row> rows = new ArrayList<Row>();
        for (int i = 0; i < rowCount; i++) {
            rows.add(new Row(i == 0 ? onlyRowId : "environment_extra", null, ""));
        }
        return previewRegistrationIdForRows(rows);
    }

    private static PreviewLayoutEditor.Target previewTarget(String id) {
        if ("environment_weather".equals(id)) return PreviewLayoutEditor.ENVIRONMENT_WEATHER;
        if ("environment_biome".equals(id)) return PreviewLayoutEditor.ENVIRONMENT_BIOME;
        if ("environment_time".equals(id)) return PreviewLayoutEditor.ENVIRONMENT_TIME;
        return PreviewLayoutEditor.ENVIRONMENT;
    }

    private static void renderRow(Minecraft mc, FontRenderer fr, int x, int y, Row row) {
        int iconY = y + (ROW_HEIGHT - ICON_SIZE) / 2;
        renderIcon(mc, fr, row.icon, x, iconY);
        int textX = x + ICON_SIZE + TEXT_GAP;
        int maxTextWidth = PANEL_WIDTH - (textX - x) - 3;
        fr.drawStringWithShadow(ellipsize(fr, row.text, maxTextWidth), textX,
                y + (ROW_HEIGHT - fr.FONT_HEIGHT) / 2, 0xFFFFFF);
    }

    private static void renderIcon(Minecraft mc, FontRenderer fr, ItemStack stack, int x, int y) {
        RenderItem renderItem = mc.getRenderItem();
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.enableRescaleNormal();
        renderItem.renderItemAndEffectIntoGUI(stack, x, y);
        renderItem.renderItemOverlays(fr, stack, x, y);
        GlStateManager.disableRescaleNormal();
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableLighting();
    }

    private static String weatherLabel(World world, BiomeGenBase biome) {
        long now = System.currentTimeMillis();
        Float trackedRain = trackedRainStrength(now);
        float trackedThunder = trackedThunderStrength(now);
        boolean trackedRaining = trackedRainActive(now);
        boolean worldRaining = world != null && world.isRaining();
        boolean worldThundering = world != null && world.isThundering();
        if (recentClearWeather(now)
                && trackedThunder <= THUNDER_STRENGTH_THRESHOLD
                && !trackedRaining
                && !recentRainStart(now)) {
            return "Sunny";
        }
        float rainStrength = trackedRain != null
                ? trackedRain.floatValue()
                : world.getRainStrength(1.0F);
        boolean raining = trackedRain != null
                ? trackedRaining
                : worldRaining || rainStrength > RAIN_STRENGTH_THRESHOLD || recentRainStart(now);
        boolean thundering = worldThundering;
        return weatherLabelFromSignals(raining, thundering,
                rainStrength, Math.max(trackedThunder, rawThunderStrength(world)), biomeName(biome));
    }

    private static float rawThunderStrength(World world) {
        if (world == null) return 0.0F;
        return Math.max(trackedThunderStrength(System.currentTimeMillis()),
                Math.max(world.getThunderStrength(1.0F),
                        Math.max(world.prevThunderingStrength, world.thunderingStrength)));
    }

    public static void observeChangeGameState(int state, float value) {
        observeChangeGameState(state, value, System.currentTimeMillis());
    }

    public static void observeLightningSignal() {
        observeLightningSignal(System.currentTimeMillis());
    }

    public static void observeThunderSound(String soundName) {
        if (!isThunderSound(soundName)) return;
        observeLightningSignal();
    }

    private static void observeChangeGameState(int state, float value, long now) {
        if (state == 8) {
            serverThunderStrength = Math.max(0.0F, value);
            serverThunderUpdatedMs = now;
            clearWeatherObservedMs = 0L;
            return;
        }
        if (state == 1) {
            // Hypixel GTB uses this transition when restoring plot weather to sunny.
            serverRainActive = false;
            serverRainStrength = 0.0F;
            serverRainUpdatedMs = now;
            rainStartObservedMs = 0L;
            serverThunderStrength = 0.0F;
            serverThunderUpdatedMs = now;
            lightningObservedMs = 0L;
            clearWeatherObservedMs = now;
            return;
        }
        if (state == 2) {
            // Hypixel GTB uses this transition when enabling rain, snow, or thunder.
            serverRainActive = true;
            serverRainStrength = Math.max(serverRainStrength, 1.0F);
            serverRainUpdatedMs = now;
            rainStartObservedMs = now;
            clearWeatherObservedMs = 0L;
            return;
        }
        if (state == 7) {
            serverRainStrength = Math.max(0.0F, value);
            serverRainUpdatedMs = now;
            rainStartObservedMs = 0L;
            if (!serverRainActive && serverRainStrength <= SERVER_RAIN_STRENGTH_THRESHOLD) {
                serverThunderStrength = 0.0F;
                serverThunderUpdatedMs = now;
                lightningObservedMs = 0L;
                clearWeatherObservedMs = now;
            } else {
                clearWeatherObservedMs = 0L;
            }
        }
    }

    private static void observeLightningSignal(long now) {
        lightningObservedMs = now;
        clearWeatherObservedMs = 0L;
    }

    private static float trackedThunderStrength(long now) {
        float strength = 0.0F;
        if (serverThunderStrength > THUNDER_STRENGTH_THRESHOLD
                && now - serverThunderUpdatedMs <= SERVER_THUNDER_SIGNAL_TTL_MS) {
            strength = Math.max(strength, serverThunderStrength);
        }
        if (lightningObservedMs > 0L && now - lightningObservedMs <= LIGHTNING_SIGNAL_TTL_MS) {
            strength = Math.max(strength, 1.0F);
        }
        return strength;
    }

    private static Float trackedRainStrength(long now) {
        if (serverRainUpdatedMs <= 0L || now - serverRainUpdatedMs > SERVER_RAIN_SIGNAL_TTL_MS) {
            return null;
        }
        return Float.valueOf(serverRainStrength);
    }

    private static boolean trackedRainActive(long now) {
        if (serverRainUpdatedMs <= 0L || now - serverRainUpdatedMs > SERVER_RAIN_SIGNAL_TTL_MS) {
            return false;
        }
        return serverRainActive || serverRainStrength > SERVER_RAIN_STRENGTH_THRESHOLD;
    }

    private static boolean recentRainStart(long now) {
        return rainStartObservedMs > 0L && now - rainStartObservedMs <= RAIN_START_GRACE_MS;
    }

    private static boolean recentClearWeather(long now) {
        return clearWeatherObservedMs > 0L && now - clearWeatherObservedMs <= CLEAR_WEATHER_SIGNAL_TTL_MS;
    }

    private static boolean isThunderSound(String soundName) {
        if (soundName == null) return false;
        String key = soundName.toLowerCase(Locale.ROOT);
        return key.contains("weather.thunder") || key.contains("ambient.weather.thunder");
    }

    static String weatherLabelForTest(boolean raining, boolean thundering, String biomeName) {
        if (thundering) return "Thunder";
        if (raining) return isSnowBiome(biomeName) ? "Snowing" : "Raining";
        return "Sunny";
    }

    static String weatherLabelForStrengthsForTest(float rainStrength, float thunderStrength, String biomeName) {
        return weatherLabelFromSignals(false, false, rainStrength, thunderStrength, biomeName);
    }

    static String weatherLabelForSignalsForTest(boolean raining, boolean thundering,
                                                float rainStrength, float thunderStrength,
                                                String biomeName) {
        return weatherLabelFromSignals(raining, thundering, rainStrength, thunderStrength, biomeName);
    }

    static void clearWeatherSignalsForTest() {
        serverRainStrength = 0.0F;
        serverRainUpdatedMs = 0L;
        serverRainActive = false;
        rainStartObservedMs = 0L;
        serverThunderStrength = 0.0F;
        serverThunderUpdatedMs = 0L;
        lightningObservedMs = 0L;
        clearWeatherObservedMs = 0L;
    }

    static void observeChangeGameStateForTest(int state, float value, long now) {
        observeChangeGameState(state, value, now);
    }

    static void observeLightningSignalForTest(long now) {
        observeLightningSignal(now);
    }

    static String weatherLabelForTrackedSignalsForTest(float rainStrength, float thunderStrength,
                                                       String biomeName, long now) {
        if (recentClearWeather(now)) return "Sunny";
        Float trackedRain = trackedRainStrength(now);
        boolean trackedRaining = trackedRainActive(now);
        float effectiveRain = trackedRain != null ? trackedRain.floatValue() : rainStrength;
        boolean raining = trackedRain != null
                ? trackedRaining
                : rainStrength > RAIN_STRENGTH_THRESHOLD || recentRainStart(now);
        return weatherLabelFromSignals(raining, false, effectiveRain,
                Math.max(thunderStrength, trackedThunderStrength(now)), biomeName);
    }

    private static String weatherLabelFromSignals(boolean raining, boolean thundering,
                                                  float rainStrength, float thunderStrength,
                                                  String biomeName) {
        if (thundering || thunderStrength > THUNDER_STRENGTH_THRESHOLD) return "Thunder";
        if (raining || rainStrength > RAIN_STRENGTH_THRESHOLD) return isSnowBiome(biomeName) ? "Snowing" : "Raining";
        return "Sunny";
    }

    private static ItemStack weatherIcon(String weather) {
        String key = normalize(weather);
        if ("thunder".equals(key)) return new ItemStack(Items.blaze_rod);
        if ("raining".equals(key)) return new ItemStack(Items.water_bucket);
        if ("snowing".equals(key)) return new ItemStack(Items.snowball);
        return new ItemStack(Blocks.double_plant, 1, 0);
    }

    static String biomeLabelForTest(String rawName) {
        return biomeLabel(rawName);
    }

    private static String biomeLabel(String rawName) {
        String name = normalize(rawName);
        if (name.isEmpty()) return "Unknown";
        if (name.contains("hell") || name.contains("nether")) return "Nether";
        if (name.contains("sky") || name.contains("end")) return "The End";
        if (name.contains("mesa")) return "Mesa";
        if (isSnowBiome(name)) return "Ice Plains";
        if (name.contains("ocean") || name.contains("river")) return "Ocean";
        if (name.contains("desert")) return "Desert";
        if (name.contains("jungle")) return "Jungle";
        if (name.contains("swamp")) return "Swamp";
        if (name.contains("savanna")) return "Savanna";
        if (name.contains("forest") || name.contains("taiga")) return "Forest";
        if (name.contains("plains")) return "Plains";
        return titleCase(name);
    }

    private static ItemStack biomeIcon(String biomeLabel) {
        String key = normalize(biomeLabel);
        if ("nether".equals(key)) return new ItemStack(Blocks.netherrack);
        if ("the end".equals(key) || "end".equals(key)) return new ItemStack(Blocks.end_stone);
        if ("mesa".equals(key)) return new ItemStack(Blocks.sand, 1, 1);
        if ("ocean".equals(key)) return new ItemStack(Items.water_bucket);
        if ("desert".equals(key)) return new ItemStack(Blocks.sand);
        if ("forest".equals(key)) return new ItemStack(Blocks.sapling, 1, 0);
        if ("jungle".equals(key)) return new ItemStack(Blocks.vine);
        if ("ice plains".equals(key)) return new ItemStack(Blocks.packed_ice);
        if ("swamp".equals(key)) return new ItemStack(Blocks.waterlily);
        if ("savanna".equals(key)) return new ItemStack(Blocks.sapling, 1, 4);
        return new ItemStack(Blocks.grass);
    }

    private static ItemStack timeIcon(long worldTime) {
        if (!"blocks".equals(normalizeTimeIconPreset(GtbStructures.environmentTimeIconPreset))) {
            return new ItemStack(Items.clock);
        }
        switch (timePeriodIndex(worldTime)) {
            case 0: return new ItemStack(Blocks.obsidian);
            case 1: return new ItemStack(Blocks.wool, 1, 7);
            case 2: return new ItemStack(Blocks.stone, 1, 3);
            case 3: return new ItemStack(Blocks.wool, 1, 4);
            case 4: return new ItemStack(Blocks.gold_block);
            case 5: return new ItemStack(Blocks.stained_hardened_clay, 1, 1);
            case 6: return new ItemStack(Blocks.stained_hardened_clay, 1, 9);
            default: return new ItemStack(Blocks.wool, 1, 15);
        }
    }

    public static String normalizeTimeIconPreset(String preset) {
        if (preset == null) return "clock";
        String value = preset.trim().toLowerCase(Locale.ROOT);
        return "blocks".equals(value) ? "blocks" : "clock";
    }

    public static boolean runtimeEnabledForMaster(boolean modEnabled) {
        return modEnabled;
    }

    static String timeLabelForTest(long worldTime) {
        return timeLabel(worldTime);
    }

    static int timePeriodIndexForTest(long worldTime) {
        return timePeriodIndex(worldTime);
    }

    static int panelWidthForTest() {
        return PANEL_WIDTH;
    }

    static int previewRowCountForTest(boolean overlay, boolean weather, boolean biome, boolean time) {
        if (!overlay) return 0;
        int count = 0;
        if (weather) count++;
        if (biome) count++;
        if (time) count++;
        return count;
    }

    static String timeLabel(long worldTime) {
        long dayTime = positiveModulo(worldTime, 24000L);
        int hour = (int) ((dayTime / 1000L + 6L) % 24L);
        int minute = (int) ((dayTime % 1000L) * 60L / 1000L);
        return String.format(Locale.ROOT, "%02d:%02d", hour, minute);
    }

    private static int timePeriodIndex(long worldTime) {
        long dayTime = positiveModulo(worldTime, 24000L);
        int hour = (int) ((dayTime / 1000L + 6L) % 24L);
        return hour / 3;
    }

    private static long positiveModulo(long value, long modulo) {
        long result = value % modulo;
        return result < 0 ? result + modulo : result;
    }

    private static boolean isSnowBiome(String rawName) {
        String name = normalize(rawName);
        return name.contains("ice") || name.contains("frozen") || name.contains("cold");
    }

    private static String biomeName(BiomeGenBase biome) {
        return biome == null || biome.biomeName == null ? "" : biome.biomeName;
    }

    private static String normalize(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    private static String titleCase(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        String[] parts = raw.split(" ");
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) out.append(' ');
            String part = parts[i];
            if (part.isEmpty()) continue;
            out.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) out.append(part.substring(1));
        }
        return out.toString();
    }

    private static String ellipsize(FontRenderer fr, String text, int maxWidth) {
        if (text == null) return "";
        if (fr.getStringWidth(text) <= maxWidth) return text;
        String ellipsis = "...";
        int ellipsisWidth = fr.getStringWidth(ellipsis);
        String out = text;
        while (!out.isEmpty() && fr.getStringWidth(out) + ellipsisWidth > maxWidth) {
            out = out.substring(0, out.length() - 1);
        }
        return out + ellipsis;
    }

    private static final class Row {
        final String id;
        final ItemStack icon;
        final String text;

        Row(String id, ItemStack icon, String text) {
            this.id = id == null ? "environment" : id;
            this.icon = icon;
            this.text = text == null ? "" : text;
        }
    }
}
