package com.gtbstructures.scoreboard;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;

/**
 * Layout helpers for the custom scoreboard panel. Right-edge anchored, vertically
 * centered with a configurable upward offset.
 */
public final class ScoreboardLayout {

    /** Default translucent scoreboard background fill. */
    public static final int BACKGROUND_COLOR = 0x50000000;
    /** Per-line vertical step (font height + 1). */
    public static final int LINE_HEIGHT = 10;
    /** Horizontal padding inside the bg fill. */
    public static final int PAD_X = 2;
    /** Vertical padding inside the bg fill. */
    public static final int PAD_Y = 2;
    /** Edge offset from right edge of screen (matches vanilla sidebar feel). */
    public static final int RIGHT_MARGIN = 2;

    public static final class Placement {
        public final int x;       // top-left x of the panel content (after PAD_X)
        public final int y;       // top-left y of the panel content (after PAD_Y)
        public final int width;   // content width (excluding padding)
        public final int bgX1;    // background fill rect (top-left)
        public final int bgY1;
        public final int bgX2;    // background fill rect (bottom-right, exclusive)
        public final int bgY2;
        public Placement(int x, int y, int width, int bgX1, int bgY1, int bgX2, int bgY2) {
            this.x = x; this.y = y; this.width = width;
            this.bgX1 = bgX1; this.bgY1 = bgY1; this.bgX2 = bgX2; this.bgY2 = bgY2;
        }
    }

    public static Placement place(ScaledResolution sr, FontRenderer fr,
                                  int contentWidth, int contentLines,
                                  int heightOffset) {
        int totalH = contentLines * LINE_HEIGHT;
        int bgW = contentWidth + 2 * PAD_X;
        int bgH = totalH + 2 * PAD_Y;
        int bgX2 = sr.getScaledWidth() - RIGHT_MARGIN;
        int bgX1 = bgX2 - bgW;
        int centerY = sr.getScaledHeight() / 2;
        int bgY1 = centerY - bgH / 2 - heightOffset;
        int bgY2 = bgY1 + bgH;
        int x = bgX1 + PAD_X;
        int y = bgY1 + PAD_Y;
        return new Placement(x, y, contentWidth, bgX1, bgY1, bgX2, bgY2);
    }

    /** Width of the longest line in the panel content. */
    public static int measureMaxWidth(FontRenderer fr, String[] lines) {
        int max = 0;
        for (String s : lines) {
            if (s == null) continue;
            int w = fr.getStringWidth(s);
            if (w > max) max = w;
        }
        return max;
    }

    private ScoreboardLayout() {}
}
