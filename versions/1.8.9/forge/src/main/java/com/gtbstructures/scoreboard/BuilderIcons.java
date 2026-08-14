package com.gtbstructures.scoreboard;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

/**
 * Builder-state icon renderer. Uses the actual GuessTheUtils icon PNGs
 * (32×32 each, stitched into a horizontal strip) matching the GTU PUA
 * character set (U+EA00-U+EA22) for an identical visual language.
 *
 * <p>Texture layout (gtb_icons.png, 20 cells × 32px = 640×32):
 * <pre>
 *   [0-15] spinner frames     (building — rotating indicator)
 *   [16]   check               (✓ built in a prior round)
 *   [17]   exit                (↗ player left/disconnected)
 *   [18]   inactive            (☾ away/idle)
 *   [19]   build background    (constant not-built placeholder)
 * </pre>
 *
 * <p>Rendered via {@code drawScaledCustomSizeModalRect} to match the
 * font height when drawn alongside player names in the scoreboard panel.
 */
public final class BuilderIcons {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation("gtbstructures", "textures/font/gtb_icons.png");

    public static final int CELL_SIZE = 32;
    private static final int CELL_COUNT = 36;
    /** Actual GTU glyph art is 16x16 in the top-left of each 32px-spaced cell. */
    private static final int GLYPH_SOURCE_SIZE = 16;

    // Icon indices matching the actual texture strip order (gtb_icons.png, 36 cells):
    // [0-15] spinner frames, [16] check, [17] exit, [18] inactive, [19] build bg,
    // [20-35] spinner-masked build backgrounds.
    public static final int IDX_SPINNER_0  = 0;   // 16 frames: 0-15
    public static final int IDX_CHECK      = 16;
    public static final int IDX_EXIT       = 17;  // disconnected / left
    public static final int IDX_INACTIVE   = 18;
    public static final int IDX_BUILD_BG   = 19;
    public static final int IDX_SPINNER_BG_0 = 20;

    private static int lastTick;

    /** Returns the spinner frame index (0-15, cycles every 4 ticks). */
    public static int getSpinnerFrame() {
        int tick = 0;
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc != null && mc.thePlayer != null) {
                tick = mc.thePlayer.ticksExisted;
            }
        } catch (Exception ignored) {}
        return (tick / 3) % 16;
    }

    public static boolean isSpinnerFrame(int cell) {
        return cell >= IDX_SPINNER_0 && cell < IDX_CHECK;
    }

    public static int backgroundForBuildIcon(int buildIcon) {
        if (isSpinnerFrame(buildIcon)) {
            return IDX_SPINNER_BG_0 + (buildIcon - IDX_SPINNER_0);
        }
        return IDX_BUILD_BG;
    }

    /**
     * Returns the icon index for a player's state, or -1 if no icon applies.
     * Callers should pass these to {@link #draw(int, int, int, int)}.
     *
     * @param isBuilder true if this player is the current round's builder
     * @param wasBuilder true if this player built in a prior round (excluding the current one)
     * @param leaver true if this player disconnected/left mid-game
     * @param inactive true if this player has been idle past the threshold
     * @param isBuildPhase true when in ROUND_BUILD
     * @param isPrePhase true when in ROUND_PRE (upcoming builder gets no icon yet)
     */
    public static int iconFor(boolean isBuilder, boolean wasBuilder,
                              boolean leaver, boolean inactive,
                              boolean isBuildPhase, boolean isPrePhase) {
        // Leaver overrides everything — even the builder can leave mid-round.
        if (leaver) return IDX_EXIT;
        // Builder slot wins over inactive — you can't be "away" while actively building.
        if (isBuilder) {
            if (isBuildPhase || isPrePhase) return IDX_SPINNER_0 + getSpinnerFrame();
            return IDX_CHECK;  // ROUND_END
        }
        // Non-builder: inactive (zzz) takes priority over the past-builder check.
        if (inactive) return IDX_INACTIVE;
        if (wasBuilder) return IDX_CHECK;
        return -1;
    }

    /** Returns only the build-slot foreground: spinner/check, or -1. */
    public static int buildProgressIconFor(boolean isBuilder, boolean wasBuilder,
                                           boolean isBuildPhase, boolean isPrePhase) {
        if (isBuilder) {
            if (isBuildPhase || isPrePhase) return IDX_SPINNER_0 + getSpinnerFrame();
            return IDX_CHECK;
        }
        if (wasBuilder) return IDX_CHECK;
        return -1;
    }

    /** Returns only the inline row-status icon: left/away, or -1. */
    public static int statusIconFor(boolean leaver, boolean inactive) {
        if (leaver) return IDX_EXIT;
        if (inactive) return IDX_INACTIVE;
        return -1;
    }

    /**
     * Draw an icon cell at (x, y) at the given display size.
     *
     * @param cell     icon index (0-19)
     * @param x        screen x (left edge)
     * @param y        screen y (top edge)
     * @param drawSize output size in pixels (square)
     */
    public static void draw(int cell, int x, int y, int drawSize) {
        draw(cell, x, y, drawSize, 0xFFFFFF);
    }

    /**
     * Draw an icon cell with a multiplicative RGB tint. The texture art is
     * mostly white-on-transparent, so a tint of e.g. 0xFF5555 yields a red
     * version of the same glyph.
     *
     * @param cell     icon index (0-19)
     * @param x        screen x (left edge)
     * @param y        screen y (top edge)
     * @param drawSize output size in pixels (square)
     * @param tintRgb  0xRRGGBB color multiplier; 0xFFFFFF = no tint
     */
    public static void draw(int cell, int x, int y, int drawSize, int tintRgb) {
        draw(cell, x, y, drawSize, tintRgb, 0.0f);
    }

    public static void draw(int cell, int x, int y, int drawSize, int tintRgb, float yOffset) {
        if (cell < 0 || cell >= CELL_COUNT) return;
        Minecraft mc = Minecraft.getMinecraft();
        mc.getTextureManager().bindTexture(TEXTURE);
        float r = ((tintRgb >> 16) & 0xFF) / 255f;
        float g = ((tintRgb >> 8) & 0xFF) / 255f;
        float b = (tintRgb & 0xFF) / 255f;
        GlStateManager.color(r, g, b, 1f);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(770, 771);
        int cellX = cell * CELL_SIZE;
        GlStateManager.pushMatrix();
        GlStateManager.translate(0.0f, yOffset, 0.0f);
        Gui.drawScaledCustomSizeModalRect(x, y, cellX, 0,
                GLYPH_SOURCE_SIZE, GLYPH_SOURCE_SIZE,
                drawSize, drawSize, CELL_COUNT * CELL_SIZE, CELL_SIZE);
        GlStateManager.popMatrix();
        // Reset color so subsequent vanilla HUD draws aren't tinted.
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    /**
     * Draw at native 32x32 size.
     */
    public static void draw(int cell, int x, int y) {
        draw(cell, x, y, CELL_SIZE);
    }

    /**
     * Returns the per-frame tint for a given icon cell:
     * <ul>
     *   <li>spinner (0-15) → 3-tier cycling gradient (white → yellow → gold),
     *       4 frames per tier (12-tick cycle)</li>
     *   <li>check  (16) → §a green</li>
     *   <li>exit   (17) → §c red</li>
     *   <li>inactive (18) → §7 gray</li>
     *   <li>fallback → 0xFFFFFF (no tint)</li>
     * </ul>
     */
    public static int tintFor(int cell) {
        if (cell >= IDX_SPINNER_0 && cell < IDX_CHECK) {
            int tick = currentTick();
            int phase = (tick / 4) % 3;
            if (phase == 0) return 0xFFFFFF; // §f white
            if (phase == 1) return 0xFFFF55; // §e yellow
            return 0xFFAA00;                 // §6 gold
        }
        if (cell >= IDX_SPINNER_BG_0 && cell < CELL_COUNT) return 0x555555;
        switch (cell) {
            case IDX_CHECK:    return 0x55FF55; // §a
            case IDX_EXIT:     return 0xFF5555; // §c
            case IDX_INACTIVE: return 0xAAAAAA; // §7
            case IDX_BUILD_BG:  return 0x555555; // §8
            default:           return 0xFFFFFF;
        }
    }

    private static int currentTick() {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc != null && mc.thePlayer != null) return mc.thePlayer.ticksExisted;
        } catch (Exception ignored) {}
        return 0;
    }

    private BuilderIcons() {}
}
