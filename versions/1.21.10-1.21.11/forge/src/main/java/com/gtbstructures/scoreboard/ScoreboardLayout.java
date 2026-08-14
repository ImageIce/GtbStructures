package com.gtbstructures.scoreboard;

/**
 * Layout helpers for the custom scoreboard panel. Right-edge anchored and
 * vertically centered with a configurable upward offset.
 */
public final class ScoreboardLayout {
    public static final int BACKGROUND_COLOR = 0x50000000;
    public static final int LINE_HEIGHT = 10;
    public static final int PAD_X = 2;
    public static final int PAD_Y = 2;
    public static final int RIGHT_MARGIN = 2;

    public interface StringMeasurer {
        int width(String text);
    }

    public static final class Placement {
        public final int x;
        public final int y;
        public final int width;
        public final int bgX1;
        public final int bgY1;
        public final int bgX2;
        public final int bgY2;

        public Placement(int x, int y, int width, int bgX1, int bgY1, int bgX2, int bgY2) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.bgX1 = bgX1;
            this.bgY1 = bgY1;
            this.bgX2 = bgX2;
            this.bgY2 = bgY2;
        }
    }

    public static Placement place(int screenWidth, int screenHeight, int contentWidth,
                                  int contentLines, int heightOffset) {
        int totalHeight = contentLines * LINE_HEIGHT;
        int bgWidth = contentWidth + 2 * PAD_X;
        int bgHeight = totalHeight + 2 * PAD_Y;
        int bgX2 = screenWidth - RIGHT_MARGIN;
        int bgX1 = bgX2 - bgWidth;
        int centerY = screenHeight / 2;
        int bgY1 = centerY - bgHeight / 2 - heightOffset;
        int bgY2 = bgY1 + bgHeight;
        int x = bgX1 + PAD_X;
        int y = bgY1 + PAD_Y;
        return new Placement(x, y, contentWidth, bgX1, bgY1, bgX2, bgY2);
    }

    public static int measureMaxWidth(StringMeasurer measurer, String[] lines) {
        int max = 0;
        for (String line : lines) {
            if (line == null) continue;
            int width = measurer.width(line);
            if (width > max) max = width;
        }
        return max;
    }

    private ScoreboardLayout() {
    }
}
