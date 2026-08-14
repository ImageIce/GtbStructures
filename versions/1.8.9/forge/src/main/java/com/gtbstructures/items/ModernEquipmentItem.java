package com.gtbstructures.items;

public final class ModernEquipmentItem {
    enum Kind {
        BED,
        DRAGON_HEAD,
        SPAWN_EGG
    }

    final Kind kind;
    final int colorMeta;
    final ModernSpawnEggData spawnEgg;

    private ModernEquipmentItem(Kind kind, int colorMeta, ModernSpawnEggData spawnEgg) {
        this.kind = kind;
        this.colorMeta = colorMeta;
        this.spawnEgg = spawnEgg;
    }

    public static ModernEquipmentItem fromRawItem(int rawItemId, int rawDamage) {
        int bedColor = ModernEquipmentItemIds.bedColorMeta(rawItemId);
        if (bedColor != ModernEquipmentItemIds.BED_COLOR_NONE) {
            return new ModernEquipmentItem(Kind.BED, bedColor, null);
        }
        if (rawItemId == 383) {
            return spawnEgg(rawDamage);
        }
        return null;
    }

    static ModernEquipmentItem dragonHead() {
        return new ModernEquipmentItem(Kind.DRAGON_HEAD, 0, null);
    }

    static ModernEquipmentItem spawnEgg(int damage) {
        ModernSpawnEggData egg = ModernSpawnEggData.byDamage(damage);
        return egg == null ? null : new ModernEquipmentItem(Kind.SPAWN_EGG, 0, egg);
    }

    public String kindName() {
        return kind == null ? null : kind.name();
    }

    public int colorMeta() {
        return colorMeta;
    }

    public Integer spawnEggDamage() {
        return spawnEgg == null ? null : Integer.valueOf(spawnEgg.damage);
    }

    public String spawnEggTextureName() {
        return spawnEgg == null ? null : spawnEgg.textureName;
    }
}
