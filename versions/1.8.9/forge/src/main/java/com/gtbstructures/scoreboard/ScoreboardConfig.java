package com.gtbstructures.scoreboard;

import com.gtbstructures.GtbStructures;

/**
 * Knob-bag for the custom GTB scoreboard panel. All fields are loaded/saved by
 * GtbStructures.loadConfig / saveConfig under category "scoreboard".
 *
 * Keeps the persistent custom scoreboard settings shared by both non-vanilla
 * skins.
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

    /** Master toggle. Default off. When on, the panel renders during
     *  ROUND_PRE/BUILD/END and the vanilla sidebar is suppressed for those states. */
    public boolean enabled = false;

    /** Pixels to lift the panel above vertical center. */
    public int heightOffset = 9;

    /** Visual style for the custom scoreboard:
     *  <ul>
     *    <li>"vanilla" - Hypixel's unmodified sidebar.</li>
     *    <li>"default" — fully custom standalone panel (header, timer, theme,
     *        rows). Vanilla sidebar is fully suppressed.</li>
     *    <li>"hypixel" — vanilla sidebar layout is preserved, but the
     *        leaderboard rows are replaced by our custom rows (icons, deltas,
     *        rank colors, emblems). All non-leaderboard rows render exactly
     *        as Hypixel sends them.</li>
     *  </ul> */
    public String skin = "vanilla"; // vanilla, default, or hypixel

    /** Background fill opacity for custom-scoreboard backgrounds. */
    public float backgroundOpacity = DEFAULT_BACKGROUND_OPACITY;

    /** Row highlight intensity, where 1.0 means a fully opaque colored row. */
    public float highlightIntensity = DEFAULT_HIGHLIGHT_INTENSITY;

    /** Existing GTBS defaults: no places, TAB-only titles, emblems/deltas on. */
    public DisplayOption showPointsGainedInRound = DisplayOption.ON;
    public DisplayOption showPlaces = DisplayOption.OFF;
    public DisplayOption showTitles = DisplayOption.EXPANDED;
    public DisplayOption showEmblems = DisplayOption.ON;

    /** Shared text-shadow setting. */
    public boolean textShadow = true;

    /** Keep confirmed leavers together below every active/inactive player. */
    public boolean leftPlayersAtBottom = true;

    /** Convenience — case-insensitive equality check on skin. */
    public boolean isSkin(String name) {
        return skin != null && name != null && skin.equalsIgnoreCase(name);
    }

    /** Custom rendering is active only for non-vanilla skins. */
    public boolean isCustomEnabled() {
        return enabled && !isSkin("vanilla");
    }

    /** Toggle between the vanilla server sidebar and the default custom skin. */
    public void toggleCustom() {
        setCustomEnabled(!isCustomEnabled());
    }

    /** Set the custom-scoreboard master state while keeping skin in sync. */
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

    /** Set the visual skin; vanilla is equivalent to custom scoreboard off. */
    public void setSkin(String name) {
        String normalized = normalizeSkin(name);
        skin = normalized;
        enabled = !"vanilla".equals(normalized);
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
        return alphaColor(0x000000, backgroundOpacity);
    }

    public int titleBackgroundColor() {
        return alphaColor(0x000000, clamp01(backgroundOpacity) + (0x10 / 255.0F));
    }

    public int applyHighlightIntensity(int argb) {
        if (argb == 0) return 0;
        int legacyAlpha = (argb >>> 24) & 0xFF;
        int scaledAlpha = highlightAlpha(legacyAlpha / 255.0F);
        if (scaledAlpha <= 0) return 0;
        return (argb & 0x00FFFFFF) | (scaledAlpha << 24);
    }

    public static float migrateLegacyHighlightIntensity(float legacyValue) {
        return clamp01(clamp01(legacyValue) * DEFAULT_HIGHLIGHT_INTENSITY);
    }

    public void clampVisuals() {
        backgroundOpacity = clamp01(backgroundOpacity);
        highlightIntensity = clamp01(highlightIntensity);
        showPointsGainedInRound = normalizeOption(showPointsGainedInRound, DisplayOption.ON);
        showPlaces = normalizeOption(showPlaces, DisplayOption.OFF);
        showTitles = normalizeOption(showTitles, DisplayOption.EXPANDED);
        showEmblems = normalizeOption(showEmblems, DisplayOption.ON);
    }

    /** Normalize a persisted or requested skin name. */
    public static String normalizeSkin(String name) {
        String normalized = name == null ? "" : name.trim().toLowerCase();
        if ("vanilla".equals(normalized) || "default".equals(normalized)
                || "hypixel".equals(normalized)) {
            return normalized;
        }
        return "hypixel";
    }

    public static DisplayOption normalizeOption(DisplayOption option, DisplayOption fallback) {
        return option == null ? fallback : option;
    }

    public static DisplayOption parseOption(String raw) {
        if (raw == null) return null;
        String normalized = raw.trim().toLowerCase();
        if ("off".equals(normalized) || "no".equals(normalized) || "false".equals(normalized)
                || "0".equals(normalized)) return DisplayOption.OFF;
        if ("expanded".equals(normalized) || "onlywhenexpanded".equals(normalized)
                || "only_when_expanded".equals(normalized) || "tab".equals(normalized)) {
            return DisplayOption.EXPANDED;
        }
        if ("on".equals(normalized) || "yes".equals(normalized) || "true".equals(normalized)
                || "1".equals(normalized)) return DisplayOption.ON;
        return null;
    }

    public static DisplayOption cycleOption(DisplayOption option) {
        option = normalizeOption(option, DisplayOption.OFF);
        if (option == DisplayOption.OFF) return DisplayOption.EXPANDED;
        if (option == DisplayOption.EXPANDED) return DisplayOption.ON;
        return DisplayOption.OFF;
    }

    public static String displayOption(DisplayOption option) {
        option = normalizeOption(option, DisplayOption.OFF);
        if (option == DisplayOption.ON) return "ON";
        if (option == DisplayOption.EXPANDED) return "Only when expanded";
        return "OFF";
    }

    public static float clamp01(float value) {
        if (value < 0.0F) return 0.0F;
        if (value > 1.0F) return 1.0F;
        return value;
    }

    private static boolean optionActive(DisplayOption option, boolean expanded) {
        option = normalizeOption(option, DisplayOption.OFF);
        return option == DisplayOption.ON || (option == DisplayOption.EXPANDED && expanded);
    }

    private static int alphaColor(int rgb, float opacity) {
        int alpha = Math.round(clamp01(opacity) * 255.0F);
        return (alpha << 24) | (rgb & 0x00FFFFFF);
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
