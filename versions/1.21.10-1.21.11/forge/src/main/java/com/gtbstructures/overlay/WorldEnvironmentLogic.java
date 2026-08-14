package com.gtbstructures.overlay;

import com.gtbstructures.diagnostics.RawPacketRecorder;
import com.gtbstructures.scoreboard.GtbGame;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Platform-neutral world-environment overlay logic. Renderers provide live
 * world signals and map returned icon keys to platform items.
 */
public final class WorldEnvironmentLogic {
    public static final int PANEL_WIDTH = 126;
    public static final int ROW_HEIGHT = 18;
    public static final int ICON_SIZE = 16;
    public static final int TEXT_GAP = 4;

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
    private static volatile boolean hasWorldSnapshot;
    private static volatile boolean lastWorldRaining;
    private static volatile boolean lastWorldThundering;
    private static volatile float lastWorldRainStrength;
    private static volatile float lastWorldThunderStrength;

    private WorldEnvironmentLogic() {
    }

    public static boolean shouldRenderLiveForState(GtbGame.State state) {
        return state == GtbGame.State.ROUND_PRE
                || state == GtbGame.State.ROUND_BUILD
                || state == GtbGame.State.ROUND_END;
    }

    public static List<Row> previewRows(boolean overlayEnabled, boolean weatherEnabled,
                                        boolean biomeEnabled, boolean timeEnabled) {
        return previewRows(overlayEnabled, weatherEnabled, biomeEnabled, timeEnabled, "clock");
    }

    public static List<Row> previewRows(boolean overlayEnabled, boolean weatherEnabled,
                                        boolean biomeEnabled, boolean timeEnabled,
                                        String timeIconPreset) {
        if (!overlayEnabled) return Collections.emptyList();
        ArrayList<Row> rows = new ArrayList<Row>();
        if (weatherEnabled) {
            rows.add(new Row("environment_weather", weatherIconKey("Sunny"),
                    "\u00a77Weather: \u00a7fSunny"));
        }
        if (biomeEnabled) {
            rows.add(new Row("environment_biome", biomeIconKey("Plains"),
                    "\u00a77Biome: \u00a7fPlains"));
        }
        if (timeEnabled) {
            rows.add(new Row("environment_time", timeIconKey(6000L, timeIconPreset),
                    "\u00a77Time: \u00a7f12:00"));
        }
        return Collections.unmodifiableList(rows);
    }

    public static int previewRowCount(boolean overlayEnabled, boolean weatherEnabled,
                                      boolean biomeEnabled, boolean timeEnabled) {
        return previewRows(overlayEnabled, weatherEnabled, biomeEnabled, timeEnabled).size();
    }

    public static int previewRowCount(boolean overlayEnabled, boolean weatherEnabled,
                                      boolean biomeEnabled, boolean timeEnabled,
                                      String timeIconPreset) {
        return previewRows(overlayEnabled, weatherEnabled, biomeEnabled, timeEnabled,
                timeIconPreset).size();
    }

    public static List<Row> liveRows(boolean overlayEnabled, boolean weatherEnabled,
                                     boolean biomeEnabled, boolean timeEnabled,
                                     boolean raining, boolean thundering,
                                     float rainStrength, float thunderStrength,
                                     String biomeName, long worldTime,
                                     String timeIconPreset, long now) {
        return liveRows(overlayEnabled, weatherEnabled, biomeEnabled, timeEnabled,
                raining, thundering, rainStrength, thunderStrength,
                biomeName, "", worldTime, timeIconPreset, now);
    }

    public static List<Row> liveRows(boolean overlayEnabled, boolean weatherEnabled,
                                     boolean biomeEnabled, boolean timeEnabled,
                                     boolean raining, boolean thundering,
                                     float rainStrength, float thunderStrength,
                                     String biomeName, String precipitationType,
                                     long worldTime, String timeIconPreset, long now) {
        if (!overlayEnabled) return Collections.emptyList();
        ArrayList<Row> rows = new ArrayList<Row>();
        if (weatherEnabled) {
            String weather = weatherLabelForLiveSignals(raining, thundering,
                    rainStrength, thunderStrength, biomeName, precipitationType, now);
            rows.add(new Row("environment_weather", weatherIconKey(weather),
                    "\u00a77Weather: \u00a7f" + weather));
        }
        if (biomeEnabled) {
            String biome = biomeLabel(biomeName);
            rows.add(new Row("environment_biome", biomeIconKey(biome),
                    "\u00a77Biome: \u00a7f" + biome));
        }
        if (timeEnabled) {
            rows.add(new Row("environment_time", timeIconKey(worldTime, timeIconPreset),
                    "\u00a77Time: \u00a7f" + timeLabel(worldTime)));
        }
        return Collections.unmodifiableList(rows);
    }

    public static String previewRegistrationId(int rowCount, String onlyRowId) {
        return rowCount == 1 ? normalizeRowId(onlyRowId) : "environment";
    }

    public static String weatherLabel(boolean raining, boolean thundering, String biomeName) {
        if (thundering) return "Thunder";
        if (raining) return isSnowBiome(biomeName) ? "Snowing" : "Raining";
        return "Sunny";
    }

    public static String weatherLabelForStrengths(float rainStrength, float thunderStrength,
                                                  String biomeName) {
        return weatherLabelFromSignals(false, false, rainStrength, thunderStrength, biomeName);
    }

    public static String weatherLabelForSignals(boolean raining, boolean thundering,
                                                float rainStrength, float thunderStrength,
                                                String biomeName) {
        return weatherLabelFromSignals(raining, thundering, rainStrength, thunderStrength, biomeName);
    }

    public static String weatherLabelForLiveSignals(boolean raining, boolean thundering,
                                                    float rainStrength, float thunderStrength,
                                                    String biomeName, long now) {
        return weatherLabelForLiveSignals(raining, thundering, rainStrength, thunderStrength,
                biomeName, "", now);
    }

    public static String weatherLabelForLiveSignals(boolean raining, boolean thundering,
                                                    float rainStrength, float thunderStrength,
                                                    String biomeName, String precipitationType,
                                                    long now) {
        float effectiveThunder = Math.max(thunderStrength, trackedThunderStrength(now));
        if (recentClearWeather(now)
                && trackedThunderStrength(now) <= THUNDER_STRENGTH_THRESHOLD
                && !trackedRainActive(now)
                && !recentRainStart(now)) {
            return "Sunny";
        }
        Float trackedRain = trackedRainStrength(now);
        float effectiveRain = trackedRain != null ? trackedRain.floatValue() : rainStrength;
        boolean effectiveRaining = trackedRain != null
                ? trackedRainActive(now)
                : raining || rainStrength > RAIN_STRENGTH_THRESHOLD || recentRainStart(now);
        return weatherLabelFromSignals(effectiveRaining, thundering, effectiveRain,
                effectiveThunder, biomeName, precipitationType);
    }

    public static void observeChangeGameState(int state, float value, long now) {
        recordChangeGameState(state, value);
        if (state == 8) {
            serverThunderStrength = Math.max(0.0F, value);
            serverThunderUpdatedMs = now;
            clearWeatherObservedMs = 0L;
            return;
        }
        if (state == 1) {
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

    public static void observeLightningSignal(long now) {
        recordLightningSignal();
        rememberLightningSignal(now);
    }

    private static void rememberLightningSignal(long now) {
        lightningObservedMs = now;
        clearWeatherObservedMs = 0L;
    }

    public static void observeThunderSound(String soundName, long now) {
        if (isThunderSound(soundName)) {
            recordThunderSound(soundName);
            rememberLightningSignal(now);
        }
    }

    public static void observeWorldWeatherSnapshot(boolean raining, boolean thundering,
                                                   float rainStrength, float thunderStrength,
                                                   long now) {
        float safeRainStrength = Math.max(0.0F, rainStrength);
        float safeThunderStrength = Math.max(0.0F, thunderStrength);
        if (!hasWorldSnapshot) {
            rememberWorldWeatherSnapshot(raining, thundering, safeRainStrength, safeThunderStrength);
            return;
        }
        if (!lastWorldRaining && raining) {
            observeChangeGameState(2, 0.0F, now);
        } else if (lastWorldRaining && !raining) {
            observeChangeGameState(1, 0.0F, now);
        }
        if (raining
                && Math.abs(safeRainStrength - lastWorldRainStrength) > SERVER_RAIN_STRENGTH_THRESHOLD) {
            observeChangeGameState(7, safeRainStrength, now);
        }
        if (!lastWorldThundering && thundering) {
            observeChangeGameState(8,
                    safeThunderStrength > THUNDER_STRENGTH_THRESHOLD ? safeThunderStrength : 1.0F,
                    now);
        } else if (lastWorldThundering && !thundering) {
            observeChangeGameState(8, 0.0F, now);
        } else if (thundering
                && Math.abs(safeThunderStrength - lastWorldThunderStrength) > THUNDER_STRENGTH_THRESHOLD) {
            observeChangeGameState(8, safeThunderStrength, now);
        }
        rememberWorldWeatherSnapshot(raining, thundering, safeRainStrength, safeThunderStrength);
    }

    public static String weatherLabelForTrackedSignals(float rainStrength, float thunderStrength,
                                                       String biomeName, long now) {
        return weatherLabelForLiveSignals(
                rainStrength > RAIN_STRENGTH_THRESHOLD || recentRainStart(now),
                false, rainStrength, thunderStrength, biomeName, now);
    }

    public static void clearWeatherSignals() {
        serverRainStrength = 0.0F;
        serverRainUpdatedMs = 0L;
        serverRainActive = false;
        rainStartObservedMs = 0L;
        serverThunderStrength = 0.0F;
        serverThunderUpdatedMs = 0L;
        lightningObservedMs = 0L;
        clearWeatherObservedMs = 0L;
        hasWorldSnapshot = false;
        lastWorldRaining = false;
        lastWorldThundering = false;
        lastWorldRainStrength = 0.0F;
        lastWorldThunderStrength = 0.0F;
    }

    public static String biomeLabel(String rawName) {
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

    public static String weatherIconKey(String weather) {
        String key = normalize(weather);
        if ("thunder".equals(key)) return "blaze_rod";
        if ("raining".equals(key)) return "water_bucket";
        if ("snowing".equals(key)) return "snowball";
        return "double_plant:0";
    }

    public static String biomeIconKey(String biomeLabel) {
        String key = normalize(biomeLabel);
        if ("nether".equals(key)) return "netherrack";
        if ("the end".equals(key) || "end".equals(key)) return "end_stone";
        if ("mesa".equals(key)) return "sand:1";
        if ("ocean".equals(key)) return "water_bucket";
        if ("desert".equals(key)) return "sand";
        if ("forest".equals(key)) return "sapling:0";
        if ("jungle".equals(key)) return "vine";
        if ("ice plains".equals(key)) return "packed_ice";
        if ("swamp".equals(key)) return "waterlily";
        if ("savanna".equals(key)) return "sapling:4";
        return "grass";
    }

    public static String timeIconKey(long worldTime, String preset) {
        if (!"blocks".equals(normalizeTimeIconPreset(preset))) return "clock";
        switch (timePeriodIndex(worldTime)) {
            case 0: return "obsidian";
            case 1: return "wool:7";
            case 2: return "stone:3";
            case 3: return "wool:4";
            case 4: return "gold_block";
            case 5: return "stained_hardened_clay:1";
            case 6: return "stained_hardened_clay:9";
            default: return "wool:15";
        }
    }

    public static String normalizeTimeIconPreset(String preset) {
        if (preset == null) return "clock";
        String value = preset.trim().toLowerCase(Locale.ROOT);
        return "blocks".equals(value) ? "blocks" : "clock";
    }

    public static String timeLabel(long worldTime) {
        long dayTime = positiveModulo(worldTime, 24000L);
        int hour = (int) ((dayTime / 1000L + 6L) % 24L);
        int minute = (int) ((dayTime % 1000L) * 60L / 1000L);
        return String.format(Locale.ROOT, "%02d:%02d", hour, minute);
    }

    public static int timePeriodIndex(long worldTime) {
        long dayTime = positiveModulo(worldTime, 24000L);
        int hour = (int) ((dayTime / 1000L + 6L) % 24L);
        return hour / 3;
    }

    public static boolean isThunderSound(String soundName) {
        if (soundName == null) return false;
        String key = soundName.toLowerCase(Locale.ROOT);
        return key.contains("weather.thunder") || key.contains("ambient.weather.thunder");
    }

    private static void recordChangeGameState(int state, float value) {
        RawPacketRecorder.INSTANCE.captureSummary("S2BPacketChangeGameState",
                "{\"class\":\"modern\""
                        + ",\"state\":" + state
                        + ",\"value\":" + value
                        + ",\"meaning\":" + RawPacketRecorder.json(changeGameStateMeaning(state))
                        + "}");
    }

    private static void recordLightningSignal() {
        RawPacketRecorder.INSTANCE.captureSummary("S2CPacketSpawnGlobalEntity",
                "{\"class\":\"modern\""
                        + ",\"type\":1"
                        + ",\"meaning\":\"lightning\""
                        + "}");
    }

    private static void recordThunderSound(String soundName) {
        RawPacketRecorder.INSTANCE.captureSummary("S29PacketSoundEffect",
                "{\"class\":\"modern\""
                        + ",\"soundName\":" + RawPacketRecorder.json(soundName)
                        + ",\"weatherThunder\":true"
                        + "}");
    }

    private static String changeGameStateMeaning(int state) {
        switch (state) {
            case 1: return "begin_raining";
            case 2: return "end_raining";
            case 7: return "rain_strength";
            case 8: return "thunder_strength";
            default: return "state_" + state;
        }
    }

    private static String weatherLabelFromSignals(boolean raining, boolean thundering,
                                                  float rainStrength, float thunderStrength,
                                                  String biomeName) {
        return weatherLabelFromSignals(raining, thundering, rainStrength, thunderStrength,
                biomeName, "");
    }

    private static String weatherLabelFromSignals(boolean raining, boolean thundering,
                                                  float rainStrength, float thunderStrength,
                                                  String biomeName, String precipitationType) {
        if (thundering || thunderStrength > THUNDER_STRENGTH_THRESHOLD) return "Thunder";
        if (raining || rainStrength > RAIN_STRENGTH_THRESHOLD) {
            return isSnowPrecipitation(precipitationType, biomeName) ? "Snowing" : "Raining";
        }
        return "Sunny";
    }

    private static boolean isSnowPrecipitation(String precipitationType, String biomeName) {
        String precipitation = normalize(precipitationType);
        if (!precipitation.isEmpty()) return "snow".equals(precipitation);
        return isSnowBiome(biomeName);
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

    private static void rememberWorldWeatherSnapshot(boolean raining, boolean thundering,
                                                     float rainStrength, float thunderStrength) {
        hasWorldSnapshot = true;
        lastWorldRaining = raining;
        lastWorldThundering = thundering;
        lastWorldRainStrength = rainStrength;
        lastWorldThunderStrength = thunderStrength;
    }

    private static boolean isSnowBiome(String rawName) {
        String name = normalize(rawName);
        return name.contains("ice") || name.contains("frozen") || name.contains("cold");
    }

    private static long positiveModulo(long value, long modulo) {
        long result = value % modulo;
        return result < 0 ? result + modulo : result;
    }

    private static String normalize(String raw) {
        return raw == null ? "" : raw.trim().replace('_', ' ').toLowerCase(Locale.ROOT);
    }

    private static String normalizeRowId(String id) {
        return id == null || id.trim().isEmpty() ? "environment" : id;
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

    public static final class ChatNoticeTracker {
        private static final long DAY_TICKS = 24000L;
        private static final long TIME_NOTICE_TICKS = 3000L;

        private String weather;
        private String biome;
        private Long timeAnchor;

        public List<String> observe(boolean activeRound, String currentWeather,
                                    String currentBiome, long worldTime,
                                    boolean weatherEnabled, boolean biomeEnabled,
                                    boolean timeEnabled) {
            if (!activeRound) {
                reset();
                return Collections.emptyList();
            }

            ArrayList<String> notices = new ArrayList<String>();
            String safeWeather = currentWeather == null ? "" : currentWeather;
            String safeBiome = currentBiome == null ? "" : currentBiome;
            long currentTime = positiveModulo(worldTime, DAY_TICKS);

            if (weather != null && !weather.equals(safeWeather) && weatherEnabled) {
                notices.add(changeMessage("Weather", safeWeather));
            }
            weather = safeWeather;

            if (biome != null && !biome.equals(safeBiome) && biomeEnabled) {
                notices.add(changeMessage("Biome", safeBiome));
            }
            biome = safeBiome;

            if (timeAnchor == null || !timeEnabled) {
                timeAnchor = Long.valueOf(currentTime);
            } else if (wrappedTimeDistance(timeAnchor.longValue(), currentTime) >= TIME_NOTICE_TICKS) {
                notices.add(changeMessage("Time", timeLabel(currentTime)));
                timeAnchor = Long.valueOf(currentTime);
            }
            return notices.isEmpty()
                    ? Collections.<String>emptyList()
                    : Collections.unmodifiableList(notices);
        }

        public void reset() {
            weather = null;
            biome = null;
            timeAnchor = null;
        }

        private static String changeMessage(String metric, String state) {
            return "\u00a7f" + metric + " \u00a77set to \u00a7f" + state;
        }

        private static long wrappedTimeDistance(long first, long second) {
            long difference = Math.abs(first - second);
            return Math.min(difference, DAY_TICKS - difference);
        }
    }

    public static final class Row {
        public final String id;
        public final String iconKey;
        public final String text;

        public Row(String id, String iconKey, String text) {
            this.id = normalizeRowId(id);
            this.iconKey = iconKey == null ? "" : iconKey;
            this.text = text == null ? "" : text;
        }
    }
}
