package com.gtbstructures.overlay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class EnvironmentChangeNotifier {
    static final long DAY_TICKS = 24000L;
    static final long TIME_NOTICE_THRESHOLD_TICKS = 3000L;

    private boolean observing;
    private String weatherState = "";
    private String biomeState = "";
    private long timeAnchor;
    private boolean weatherEnabled;
    private boolean biomeEnabled;
    private boolean timeEnabled;

    List<Notice> observe(boolean activeRound, boolean previewActive,
                         String currentWeather, String currentBiome, long currentWorldTime,
                         boolean weatherNoticeEnabled, boolean biomeNoticeEnabled,
                         boolean timeNoticeEnabled) {
        if (!activeRound || previewActive) {
            reset();
            return Collections.emptyList();
        }

        String nextWeather = safe(currentWeather);
        String nextBiome = safe(currentBiome);
        long nextTime = positiveModulo(currentWorldTime, DAY_TICKS);
        if (!observing) {
            observing = true;
            weatherState = nextWeather;
            biomeState = nextBiome;
            timeAnchor = nextTime;
            weatherEnabled = weatherNoticeEnabled;
            biomeEnabled = biomeNoticeEnabled;
            timeEnabled = timeNoticeEnabled;
            return Collections.emptyList();
        }

        ArrayList<Notice> notices = new ArrayList<Notice>(3);
        boolean weatherSettingChanged = weatherEnabled != weatherNoticeEnabled;
        boolean biomeSettingChanged = biomeEnabled != biomeNoticeEnabled;
        boolean timeSettingChanged = timeEnabled != timeNoticeEnabled;

        if (!weatherState.equals(nextWeather)
                && weatherNoticeEnabled && !weatherSettingChanged) {
            notices.add(new Notice("Weather", nextWeather));
        }
        weatherState = nextWeather;

        if (!biomeState.equals(nextBiome)
                && biomeNoticeEnabled && !biomeSettingChanged) {
            notices.add(new Notice("Biome", nextBiome));
        }
        biomeState = nextBiome;

        if (!timeNoticeEnabled || timeSettingChanged) {
            timeAnchor = nextTime;
        } else if (circularDistance(timeAnchor, nextTime) >= TIME_NOTICE_THRESHOLD_TICKS) {
            notices.add(new Notice("Time", WorldEnvironmentOverlay.timeLabel(currentWorldTime)));
            timeAnchor = nextTime;
        }

        weatherEnabled = weatherNoticeEnabled;
        biomeEnabled = biomeNoticeEnabled;
        timeEnabled = timeNoticeEnabled;
        return notices;
    }

    void reset() {
        observing = false;
        weatherState = "";
        biomeState = "";
        timeAnchor = 0L;
        weatherEnabled = false;
        biomeEnabled = false;
        timeEnabled = false;
    }

    static long circularDistance(long firstWorldTime, long secondWorldTime) {
        long first = positiveModulo(firstWorldTime, DAY_TICKS);
        long second = positiveModulo(secondWorldTime, DAY_TICKS);
        long direct = Math.abs(first - second);
        return Math.min(direct, DAY_TICKS - direct);
    }

    private static long positiveModulo(long value, long modulo) {
        long result = value % modulo;
        return result < 0L ? result + modulo : result;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    static final class Notice {
        final String metric;
        final String state;

        Notice(String metric, String state) {
            this.metric = metric;
            this.state = state;
        }
    }
}
