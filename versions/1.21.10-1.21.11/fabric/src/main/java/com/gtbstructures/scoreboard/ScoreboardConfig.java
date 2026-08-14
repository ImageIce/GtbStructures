package com.gtbstructures.scoreboard;

/**
 * Settings for the custom GTB scoreboard panel.
 */
public class ScoreboardConfig {
    public static final float DEFAULT_BACKGROUND_OPACITY = 0.3108108F;
    public static final float LEGACY_STATUS_HIGHLIGHT_INTENSITY = 0x3D / 255.0F;
    public static final float LEGACY_BUILDER_HIGHLIGHT_INTENSITY = 0x21 / 255.0F;
    public static final float DEFAULT_HIGHLIGHT_INTENSITY = LEGACY_STATUS_HIGHLIGHT_INTENSITY;

    public enum DisplayOption {
        OFF,
        EXPANDED,
        ON
    }

    /**
     * Master toggle. Vanilla skin keeps the server sidebar unchanged.
     */
    public boolean enabled = false;

    /**
     * Pixels to lift the panel above vertical center.
     */
    public int heightOffset = 9;

    /**
     * Visual style: vanilla, default, or hypixel.
     */
    public String skin = "vanilla";
    public float backgroundOpacity = DEFAULT_BACKGROUND_OPACITY;
    public float highlightIntensity = DEFAULT_HIGHLIGHT_INTENSITY;
    public DisplayOption showPointsGainedInRound = DisplayOption.ON;
    public DisplayOption showPlaces = DisplayOption.OFF;
    public DisplayOption showTitles = DisplayOption.EXPANDED;
    public DisplayOption showEmblems = DisplayOption.ON;
    public boolean leftPlayersAtBottom = true;
    public boolean textShadow = true;

    public boolean isSkin(String name) {
        return skin != null && name != null && skin.equalsIgnoreCase(name);
    }

    public boolean isCustomEnabled() {
        return enabled && !isSkin("vanilla");
    }

    public void toggleCustom() {
        setCustomEnabled(!isCustomEnabled());
    }

    public void setCustomEnabled(boolean customEnabled) {
        if (customEnabled) {
            enabled = true;
            if (skin == null || isSkin("vanilla")) {
                skin = "hypixel";
            } else {
                skin = normalizeSkin(skin);
            }
            return;
        }
        enabled = false;
        skin = "vanilla";
    }

    public void setSkin(String name) {
        String normalized = normalizeSkin(name);
        skin = normalized;
        enabled = !"vanilla".equals(normalized);
    }

    public static String normalizeSkin(String name) {
        String normalized = name == null ? "" : name.trim().toLowerCase();
        if ("vanilla".equals(normalized) || "default".equals(normalized)
                || "hypixel".equals(normalized)) {
            return normalized;
        }
        return "hypixel";
    }

    public boolean showPointsGained(boolean expanded) {
        return optionActive(showPointsGainedInRound, expanded);
    }

    public boolean showPlaces(boolean expanded) {
        return optionActive(showPlaces, expanded);
    }

    public boolean showTitles(boolean expanded) {
        return optionActive(showTitles, expanded);
    }

    public boolean showEmblems(boolean expanded) {
        return optionActive(showEmblems, expanded);
    }

    public int backgroundColor() {
        return alphaColor(backgroundOpacity, 0x000000);
    }

    public int titleBackgroundColor() {
        return alphaColor(Math.min(1.0F, backgroundOpacity + 0.0625F), 0x000000);
    }

    public int applyHighlightIntensity(int argb) {
        if (argb == 0) return 0;
        int legacyAlpha = (argb >>> 24) & 0xFF;
        int adjusted = highlightAlpha(legacyAlpha / 255.0F);
        if (adjusted <= 0) return 0;
        return (argb & 0x00FFFFFF) | (adjusted << 24);
    }

    public static float migrateLegacyHighlightIntensity(float legacyValue) {
        return clamp01(clamp01(legacyValue) * DEFAULT_HIGHLIGHT_INTENSITY);
    }

    public void clampVisuals() {
        backgroundOpacity = clamp01(backgroundOpacity);
        highlightIntensity = clamp01(highlightIntensity);
        showPointsGainedInRound = normalizeOption(showPointsGainedInRound);
        showPlaces = normalizeOption(showPlaces);
        showTitles = normalizeOption(showTitles);
        showEmblems = normalizeOption(showEmblems);
    }

    public static DisplayOption parseOption(String raw, DisplayOption fallback) {
        DisplayOption parsed = parseOption(raw);
        return parsed == null ? normalizeOption(fallback) : parsed;
    }

    public static DisplayOption parseOption(String raw) {
        if (raw == null) return null;
        String normalized = raw.trim().toLowerCase();
        if ("on".equals(normalized) || "true".equals(normalized)) return DisplayOption.ON;
        if ("expanded".equals(normalized) || "expand".equals(normalized)
                || "tab".equals(normalized) || "onlywhenexpanded".equals(normalized)) {
            return DisplayOption.EXPANDED;
        }
        if ("off".equals(normalized) || "false".equals(normalized)) return DisplayOption.OFF;
        return null;
    }

    public static DisplayOption cycleOption(DisplayOption option) {
        option = normalizeOption(option);
        if (option == DisplayOption.OFF) return DisplayOption.EXPANDED;
        if (option == DisplayOption.EXPANDED) return DisplayOption.ON;
        return DisplayOption.OFF;
    }

    public static String displayOption(DisplayOption option) {
        option = normalizeOption(option);
        if (option == DisplayOption.ON) return "on";
        if (option == DisplayOption.EXPANDED) return "expanded";
        return "off";
    }

    public static DisplayOption normalizeOption(DisplayOption option) {
        return option == null ? DisplayOption.OFF : option;
    }

    private static boolean optionActive(DisplayOption option, boolean expanded) {
        option = normalizeOption(option);
        return option == DisplayOption.ON || (option == DisplayOption.EXPANDED && expanded);
    }

    private static int alphaColor(float alpha, int rgb) {
        int a = Math.max(0, Math.min(255, Math.round(clamp01(alpha) * 255.0F)));
        return (a << 24) | (rgb & 0x00FFFFFF);
    }

    private static float clamp01(float value) {
        if (value < 0F) return 0F;
        if (value > 1F) return 1F;
        return value;
    }

    private int highlightAlpha(float legacyDefaultIntensity) {
        float value = clamp01(highlightIntensity);
        float baseline = clamp01(legacyDefaultIntensity);
        float defaultValue = DEFAULT_HIGHLIGHT_INTENSITY;
        float alphaFraction;
        if (defaultValue <= 0.0F) {
            alphaFraction = value;
        } else if (value <= defaultValue) {
            alphaFraction = baseline * (value / defaultValue);
        } else {
            float span = 1.0F - defaultValue;
            float progress = span <= 0.0F ? 1.0F : (value - defaultValue) / span;
            alphaFraction = baseline + (1.0F - baseline) * progress;
        }
        return Math.max(0, Math.min(255, Math.round(alphaFraction * 255.0F)));
    }
}
