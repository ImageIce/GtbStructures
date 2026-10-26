package com.gtbstructures.items;

import com.gtbstructures.GtbStructuresSettings;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;

import java.util.List;

/** Selects GTBS' bundled classic spawn-egg model without mutating the live stack. */
public final class SpawnEggItemModel26Selector {
    static final String CLASSIC_MODEL_ROOT = "gtbstructures:spawn_egg/classic/";

    private SpawnEggItemModel26Selector() {
    }

    public static ItemStack selectForRender(ItemStack original) {
        if (original == null || original.isEmpty()) return original;
        if (!GtbStructuresSettings.INSTANCE.modEnabled) return original;
        Identifier itemId = BuiltInRegistries.ITEM.getKey(original.getItem());
        ModernSpawnEggData egg = selectedEgg(itemId == null ? null : itemId.toString(),
                GtbStructuresSettings.INSTANCE.spawnEggSkin);
        if (egg == null) return original;

        ItemStack rendered = original.copy();
        rendered.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath(
                "gtbstructures", "spawn_egg/classic/" + egg.textureName));
        rendered.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(
                List.of(), List.of(), List.of(),
                List.of(egg.classicBaseColor, egg.classicOverlayColor)));
        return rendered;
    }

    static String selectedModelIdForTest(String registryName, String skin) {
        ModernSpawnEggData egg = selectedEgg(registryName, skin);
        return egg == null ? null : CLASSIC_MODEL_ROOT + egg.textureName;
    }

    static String selectedModelIdForMasterForTest(String registryName, String skin,
                                                  boolean modEnabled) {
        return modEnabled ? selectedModelIdForTest(registryName, skin) : null;
    }

    static int[] classicColorsForTest(String registryName) {
        ModernSpawnEggData egg = spawnEgg(registryName);
        return egg == null ? null : new int[] {egg.classicBaseColor, egg.classicOverlayColor};
    }

    private static ModernSpawnEggData selectedEgg(String itemId, String skin) {
        if (!"classic".equalsIgnoreCase(skin)) return null;
        return spawnEgg(itemId);
    }

    private static ModernSpawnEggData spawnEgg(String itemId) {
        if (itemId == null || itemId.isEmpty()) return null;
        int separator = itemId.indexOf(':');
        String namespace = separator < 0 ? "minecraft" : itemId.substring(0, separator);
        String path = separator < 0 ? itemId : itemId.substring(separator + 1);
        if (namespace.isEmpty()) namespace = "minecraft";
        if (!"minecraft".equals(namespace) || path.indexOf(':') >= 0) return null;
        if (!path.endsWith("_spawn_egg")) return null;
        return ModernSpawnEggData.byTextureName(
                path.substring(0, path.length() - "_spawn_egg".length()));
    }
}
