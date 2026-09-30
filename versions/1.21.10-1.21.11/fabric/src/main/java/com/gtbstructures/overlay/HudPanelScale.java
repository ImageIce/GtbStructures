package com.gtbstructures.overlay;

import java.util.Locale;

/** Shared parsing and geometry rules for independently-scaled HUD panels. */
public final class HudPanelScale {
    public static final float MIN = 0.5F;
    public static final float MAX = 1.5F;
    public static final float DEFAULT = 1.0F;

    private HudPanelScale() {
    }

    public static float normalize(float value) {
        if (!Float.isFinite(value)) return DEFAULT;
        return Math.max(MIN, Math.min(MAX, value));
    }

    /** Accepts a decimal ratio or percent with no sign, exponent, or inner whitespace. */
    public static Float parse(String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        if (!value.matches("(?:\\d+(?:\\.\\d*)?|\\.\\d+)%?")) return null;
        boolean percent = value.endsWith("%");
        if (percent) value = value.substring(0, value.length() - 1);
        try {
            float parsed = Float.parseFloat(value);
            if (percent) parsed /= 100.0F;
            if (!Float.isFinite(parsed) || parsed < MIN || parsed > MAX) return null;
            return Float.valueOf(parsed);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static int scaledPixels(int basePixels, float scale) {
        if (basePixels <= 0) return 0;
        return Math.max(1, (int) Math.ceil(basePixels * normalize(scale)));
    }

    public static int basePixels(int scaledPixels, float scale) {
        if (scaledPixels <= 0) return 0;
        return Math.max(1, Math.round(scaledPixels / normalize(scale)));
    }

    public static String formatPercent(float scale) {
        float percent = normalize(scale) * 100.0F;
        int integral = Math.round(percent);
        if (Math.abs(percent - integral) < 0.0001F) return integral + "%";
        return String.format(Locale.ROOT, "%.1f%%", Float.valueOf(percent));
    }
}
