package com.gtbstructures.overlay;

public final class HudPosition {
    private HudPosition() {
    }

    public static int point(float normalized, int min, int max) {
        if (max < min) max = min;
        return min + Math.round((max - min) * clamp01(normalized));
    }

    public static int left(float normalized, int screenWidth, int contentWidth, int margin) {
        int min = margin;
        int max = Math.max(min, screenWidth - contentWidth - margin);
        return point(normalized, min, max);
    }

    public static int top(float normalized, int screenHeight, int contentHeight, int margin) {
        int min = margin;
        int max = Math.max(min, screenHeight - contentHeight - margin);
        return point(normalized, min, max);
    }

    public static int bottom(float normalized, int screenHeight, int contentHeight, int margin) {
        int min = contentHeight + margin;
        int max = Math.max(min, screenHeight - margin);
        return point(normalized, min, max);
    }

    public static int centerX(float normalized, int screenWidth, int contentWidth, int margin) {
        return left(normalized, screenWidth, contentWidth, margin) + contentWidth / 2;
    }

    public static float clamp01(float value) {
        if (value < 0F) return 0F;
        if (value > 1F) return 1F;
        return value;
    }
}
