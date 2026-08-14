package com.gtbstructures.scoreboard;

/**
 * Platform-neutral builder/status icon selection logic. Renderers provide the
 * current client tick and draw the returned texture cell.
 */
public final class BuilderIconLogic {
    public static final int CELL_SIZE = 32;
    public static final int CELL_COUNT = 36;
    public static final int GLYPH_SOURCE_SIZE = 16;

    public static final int IDX_SPINNER_0 = 0;
    public static final int IDX_CHECK = 16;
    public static final int IDX_EXIT = 17;
    public static final int IDX_INACTIVE = 18;
    public static final int IDX_BUILD_BG = 19;
    public static final int IDX_SPINNER_BG_0 = 20;

    private BuilderIconLogic() {
    }

    public static int spinnerFrameForTick(int tick) {
        return (Math.max(0, tick) / 3) % 16;
    }

    public static int spinnerCellForTick(int tick) {
        return IDX_SPINNER_0 + spinnerFrameForTick(tick);
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

    public static int iconFor(boolean isBuilder, boolean wasBuilder,
                              boolean leaver, boolean inactive,
                              boolean isBuildPhase, boolean isPrePhase) {
        return iconFor(isBuilder, wasBuilder, leaver, inactive, isBuildPhase, isPrePhase, 0);
    }

    public static int iconFor(boolean isBuilder, boolean wasBuilder,
                              boolean leaver, boolean inactive,
                              boolean isBuildPhase, boolean isPrePhase,
                              int tick) {
        if (leaver) return IDX_EXIT;
        if (isBuilder) {
            if (isBuildPhase || isPrePhase) return spinnerCellForTick(tick);
            return IDX_CHECK;
        }
        if (inactive) return IDX_INACTIVE;
        if (wasBuilder) return IDX_CHECK;
        return -1;
    }

    public static int buildProgressIconFor(boolean isBuilder, boolean wasBuilder,
                                           boolean isBuildPhase, boolean isPrePhase) {
        return buildProgressIconFor(isBuilder, wasBuilder, isBuildPhase, isPrePhase, 0);
    }

    public static int buildProgressIconFor(boolean isBuilder, boolean wasBuilder,
                                           boolean isBuildPhase, boolean isPrePhase,
                                           int tick) {
        if (isBuilder) {
            if (isBuildPhase || isPrePhase) return spinnerCellForTick(tick);
            return IDX_CHECK;
        }
        if (wasBuilder) return IDX_CHECK;
        return -1;
    }

    public static int statusIconFor(boolean leaver, boolean inactive) {
        if (leaver) return IDX_EXIT;
        if (inactive) return IDX_INACTIVE;
        return -1;
    }

    public static int tintFor(int cell) {
        return tintFor(cell, 0);
    }

    public static int tintFor(int cell, int tick) {
        if (cell >= IDX_SPINNER_0 && cell < IDX_CHECK) {
            int phase = (Math.max(0, tick) / 4) % 3;
            if (phase == 0) return 0xFFFFFF;
            if (phase == 1) return 0xFFFF55;
            return 0xFFAA00;
        }
        if (cell >= IDX_SPINNER_BG_0 && cell < CELL_COUNT) return 0x555555;
        switch (cell) {
            case IDX_CHECK: return 0x55FF55;
            case IDX_EXIT: return 0xFF5555;
            case IDX_INACTIVE: return 0xAAAAAA;
            case IDX_BUILD_BG: return 0x555555;
            default: return 0xFFFFFF;
        }
    }
}
