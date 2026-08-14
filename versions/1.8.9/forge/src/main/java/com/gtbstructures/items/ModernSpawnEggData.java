package com.gtbstructures.items;

import net.minecraft.util.ResourceLocation;

final class ModernSpawnEggData {
    private static final String ROOT = "textures/item/spawn_egg/";

    final int damage;
    final String textureName;
    final int classicBaseColor;
    final int classicOverlayColor;
    final boolean vanilla18;
    final ResourceLocation newTexture;

    private ModernSpawnEggData(int damage, String textureName, int classicBaseColor, int classicOverlayColor,
                               boolean vanilla18) {
        this.damage = damage;
        this.textureName = textureName;
        this.classicBaseColor = classicBaseColor;
        this.classicOverlayColor = classicOverlayColor;
        this.vanilla18 = vanilla18;
        this.newTexture = new ResourceLocation("gtbstructures", ROOT + "new/" + textureName + "_spawn_egg.png");
    }

    static ModernSpawnEggData byDamage(int damage) {
        switch (damage) {
            case 4: return egg(4, "elder_guardian", 0xCECCBA, 0x747693);
            case 5: return legacyEgg(5, "wither_skeleton", 0x141414, 0x474D4D);
            case 6: return egg(6, "stray", 0x617677, 0xDDEAEA);
            case 23: return egg(23, "husk", 0x797061, 0xE6CC94);
            case 27: return egg(27, "zombie_villager", 0x563C33, 0x799C65);
            case 28: return egg(28, "skeleton_horse", 0x68684F, 0xE5E5D8);
            case 29: return egg(29, "zombie_horse", 0x315234, 0x97C284);
            case 31: return egg(31, "donkey", 0x534539, 0x867566);
            case 32: return egg(32, "mule", 0x1B0200, 0x51331D);
            case 36: return egg(36, "vindicator", 0x959B9B, 0x275E61);
            case 50: return legacyEgg(50, "creeper", 0x0DA70B, 0x000000);
            case 51: return legacyEgg(51, "skeleton", 0xC1C1C1, 0x494949);
            case 52: return legacyEgg(52, "spider", 0x342D27, 0xA80E0E);
            case 54: return legacyEgg(54, "zombie", 0x00AFAF, 0x799C65);
            case 55: return legacyEgg(55, "slime", 0x51A03E, 0x7EBF6E);
            case 56: return legacyEgg(56, "ghast", 0xF9F9F9, 0xBCBCBC);
            case 58: return legacyEgg(58, "enderman", 0x161616, 0x000000);
            case 59: return legacyEgg(59, "cave_spider", 0x0C424E, 0xA80E0E);
            case 60: return legacyEgg(60, "silverfish", 0x6E6E6E, 0x303030);
            case 61: return legacyEgg(61, "blaze", 0xF6B201, 0xFFF87E);
            case 62: return legacyEgg(62, "magma_cube", 0x340000, 0xFCFC00);
            case 65: return legacyEgg(65, "bat", 0x4C3E30, 0x0F0F0F);
            case 66: return legacyEgg(66, "witch", 0x340000, 0x51A03E);
            case 67: return legacyEgg(67, "endermite", 0x161616, 0x6E6E6E);
            case 68: return legacyEgg(68, "guardian", 0x5A8272, 0xF17D30);
            case 69: return egg(69, "shulker", 0x946794, 0x4D3852);
            case 90: return legacyEgg(90, "pig", 0xF0A5A2, 0xDB635F);
            case 91: return legacyEgg(91, "sheep", 0xE7E7E7, 0xFFB5B5);
            case 92: return legacyEgg(92, "cow", 0x443626, 0xA1A1A1);
            case 93: return legacyEgg(93, "chicken", 0xA1A1A1, 0xFF0000);
            case 94: return legacyEgg(94, "squid", 0x223B4D, 0x708899);
            case 95: return legacyEgg(95, "wolf", 0xD7D3D3, 0xCEAF96);
            case 96: return legacyEgg(96, "mooshroom", 0xA00F10, 0xB7B7B7);
            case 97: return egg(97, "snow_golem", 0xD9F2F2, 0x81A4A4);
            case 98: return legacyEgg(98, "ocelot", 0xEFDE7D, 0x564434);
            case 99: return egg(99, "iron_golem", 0xDBCDC2, 0x74A332);
            case 100: return legacyEgg(100, "horse", 0xC09E7D, 0xEEE500);
            case 101: return legacyEgg(101, "rabbit", 0x995F40, 0x734831);
            case 102: return egg(102, "polar_bear", 0xF2F2F2, 0x959590);
            case 103: return egg(103, "panda", 0xE7E7E7, 0x1B1B22);
            case 104: return egg(104, "glow_squid", 0x095656, 0x85F1BC);
            case 105: return egg(105, "goat", 0xA5947C, 0x55493E);
            case 106: return egg(106, "dolphin", 0x223B4D, 0xF9F9F9);
            case 107: return egg(107, "cat", 0xEFC88E, 0x957256);
            case 109: return egg(109, "llama", 0xC09E7D, 0x995F40);
            case 110: return egg(110, "trader_llama", 0xEAA430, 0x456296);
            case 111: return egg(111, "wandering_trader", 0x456296, 0xEAA430);
            case 112: return egg(112, "axolotl", 0xFBC1E3, 0xA62D74);
            case 113: return egg(113, "pufferfish", 0xF6B201, 0x37C3F2);
            case 114: return egg(114, "cod", 0xC1A76A, 0xE5C48B);
            case 115: return egg(115, "salmon", 0xA00F10, 0x0E8474);
            case 116: return egg(116, "tropical_fish", 0xEF6915, 0xFFF9EF);
            case 117: return egg(117, "turtle", 0xE7E7E7, 0x00AFAF);
            case 119: return egg(119, "fox", 0xD5B69F, 0xCC6920);
            case 120: return legacyEgg(120, "villager", 0x563C33, 0xBD8B72);
            case 121: return egg(121, "parrot", 0x0DA70B, 0xFF0000);
            case 122: return egg(122, "vex", 0x7A90A4, 0xE8EDF1);
            case 123: return egg(123, "phantom", 0x43518A, 0x88FF00);
            case 124: return egg(124, "evoker", 0x959B9B, 0x1E1C1A);
            case 127: return egg(127, "bee", 0xEDC343, 0x43241B);
            default: return null;
        }
    }

    private static ModernSpawnEggData egg(int damage, String textureName, int classicBaseColor, int classicOverlayColor) {
        return new ModernSpawnEggData(damage, textureName, classicBaseColor, classicOverlayColor, false);
    }

    private static ModernSpawnEggData legacyEgg(int damage, String textureName, int classicBaseColor, int classicOverlayColor) {
        return new ModernSpawnEggData(damage, textureName, classicBaseColor, classicOverlayColor, true);
    }
}
