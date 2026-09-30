package com.gtbstructures.items;

import com.gtbstructures.GtbStructuresForge;
import com.gtbstructures.GtbStructuresSettings;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Selects GTBS' bundled classic spawn-egg model without mutating the live stack. */
public final class SpawnEggItemModelSelector {
    private static final String CLASSIC_MODEL_ID =
            GtbStructuresForge.MOD_ID + ":spawn_egg/classic";
    private static final Map<String, Item> ITEM_BY_REGISTRY_NAME = new ConcurrentHashMap<>();
    private static volatile Method itemKeyMethod;
    private static volatile Object classicModelIdentifier;

    private SpawnEggItemModelSelector() {
    }

    public static ItemStack selectForRender(ItemStack original) {
        if (original == null || original.isEmpty()) return original;
        if (!GtbStructuresSettings.INSTANCE.modEnabled) return original;
        Object itemId = itemIdentifier(original.getItem());
        ModernSpawnEggData egg = selectedEgg(stringValue(itemId),
                GtbStructuresSettings.INSTANCE.spawnEggSkin);
        if (egg == null) return original;

        ItemStack rendered = original.copy();
        setItemModel(rendered, classicModelIdentifier(itemId));
        rendered.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(
                List.of(), List.of(), List.of(),
                List.of(Integer.valueOf(egg.classicBaseColor), Integer.valueOf(egg.classicOverlayColor))));
        return rendered;
    }

    static String selectedModelIdForTest(String registryName, String skin) {
        return selectedEgg(registryName, skin) == null ? null : CLASSIC_MODEL_ID;
    }

    static int[] classicColorsForTest(String registryName) {
        ModernSpawnEggData egg = spawnEgg(registryName);
        return egg == null ? null : new int[] {egg.classicBaseColor, egg.classicOverlayColor};
    }

    static String registryNameForItem(Object item) {
        return stringValue(itemIdentifier(item));
    }

    /** Returns a client-renderable stack for a vanilla registry id, or EMPTY when absent. */
    public static ItemStack stackForRegistryName(String registryName) {
        String clean = registryName == null ? "" : registryName.trim();
        if (clean.isEmpty()) return ItemStack.EMPTY;
        Item item = ITEM_BY_REGISTRY_NAME.get(clean);
        if (item == null) {
            for (Item candidate : BuiltInRegistries.ITEM) {
                if (clean.equals(registryNameForItem(candidate))) {
                    ITEM_BY_REGISTRY_NAME.put(clean, candidate);
                    item = candidate;
                    break;
                }
            }
        }
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
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

    private static Object itemIdentifier(Object item) {
        if (item == null) return null;
        try {
            Method method = itemKeyMethod;
            if (method == null) {
                method = Registry.class.getMethod("getKey", Object.class);
                itemKeyMethod = method;
            }
            return method.invoke(BuiltInRegistries.ITEM, item);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) throw runtimeException;
            throw new IllegalStateException("Unable to read the Minecraft item identifier", cause);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to read the Minecraft item identifier", e);
        }
    }

    private static Object classicModelIdentifier(Object itemId) {
        if (itemId == null) throw new IllegalStateException("Minecraft returned no item identifier");
        Object cached = classicModelIdentifier;
        if (cached != null && itemId.getClass().isInstance(cached)) return cached;
        try {
            Method factory = itemId.getClass().getMethod(
                    "fromNamespaceAndPath", String.class, String.class);
            Object identifier = factory.invoke(null, GtbStructuresForge.MOD_ID, "spawn_egg/classic");
            if (identifier == null) throw new IllegalStateException("Minecraft rejected " + CLASSIC_MODEL_ID);
            classicModelIdentifier = identifier;
            return identifier;
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) throw runtimeException;
            throw new IllegalStateException("Unable to create " + CLASSIC_MODEL_ID, cause);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to create " + CLASSIC_MODEL_ID, e);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void setItemModel(ItemStack stack, Object identifier) {
        stack.set((DataComponentType) DataComponents.ITEM_MODEL, identifier);
    }

    private static String stringValue(Object value) {
        return value == null ? null : value.toString();
    }
}
