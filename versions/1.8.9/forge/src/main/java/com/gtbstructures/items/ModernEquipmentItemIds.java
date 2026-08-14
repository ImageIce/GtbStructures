package com.gtbstructures.items;

final class ModernEquipmentItemIds {
    static final int BED_COLOR_NONE = -1;

    private ModernEquipmentItemIds() {
    }

    static int bedColorMeta(int rawItemId) {
        switch (rawItemId) {
            case 3076: return 0;  // white bed
            case 3077: return 1;  // orange bed
            case 3078: return 2;  // magenta bed
            case 3079: return 3;  // light blue bed
            case 3080: return 4;  // yellow bed
            case 3081: return 5;  // lime bed
            case 3082: return 6;  // pink bed
            case 3083: return 7;  // gray bed
            case 3084: return 8;  // light gray bed
            case 3085: return 9;  // cyan bed
            case 3086: return 10; // purple bed
            case 3087: return 11; // blue bed
            case 3088: return 12; // brown bed
            case 3089: return 13; // green bed
            case 3090: return 15; // black bed
            default: return BED_COLOR_NONE;
        }
    }
}
