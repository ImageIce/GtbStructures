package com.gtbstructures.mixin;

import java.lang.reflect.Constructor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.clock.ClockInstance;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adapts 26.x clock, biome, and optional item-registry lookups for the environment HUD. */
@Mixin(targets = "com.gtbstructures.overlay.WorldEnvironmentOverlayRenderer", remap = false)
public abstract class WorldEnvironment26Mixin {
    @Inject(method = "worldTime(Ljava/lang/Object;)J", at = @At("HEAD"),
            cancellable = true, remap = false)
    private static void gtbs$read26WorldTime(Object level, CallbackInfoReturnable<Long> callback) {
        if (level instanceof ClientLevel clientLevel) {
            try {
                Holder<WorldClock> overworldClock = clientLevel.registryAccess()
                        .lookupOrThrow(Registries.WORLD_CLOCK)
                        .get(WorldClocks.OVERWORLD)
                        .orElse(null);
                if (overworldClock == null) return;
                ClockInstance clock = clientLevel.clockManager().getInstance(overworldClock);
                callback.setReturnValue(clock.totalTicks());
            } catch (RuntimeException ignored) {
                // Keep the shared reflective fallback when the native overworld clock is unavailable.
            }
        }
    }

    @Inject(method = "biomeSnapshot(Ljava/lang/Object;Ljava/lang/Object;)"
            + "Lcom/gtbstructures/overlay/WorldEnvironmentOverlayRenderer$BiomeSnapshot;",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void gtbs$read26Biome(Object level, Object pos, CallbackInfoReturnable<Object> callback) {
        if (!(level instanceof ClientLevel clientLevel)) return;
        try {
            BlockPos blockPos = pos instanceof BlockPos provided ? provided : playerBlockPos();
            Holder<Biome> holder = clientLevel.getBiome(blockPos);
            Biome biome = holder.value();
            String name = holder.unwrapKey().map(key -> key.identifier().getPath())
                    .orElse("").replace('_', ' ');
            String precipitation = biome.getPrecipitationAt(blockPos, clientLevel.getSeaLevel()).name();
            callback.setReturnValue(newBiomeSnapshot(name, precipitation));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Let the shared reflective fallback handle an unexpected 26.x runtime shape.
        }
    }

    @Inject(method = "itemStack(Ljava/lang/String;)Ljava/lang/Object;", at = @At("HEAD"),
            cancellable = true, remap = false)
    private static void gtbs$create26EnvironmentItem(String key, CallbackInfoReturnable<Object> callback) {
        Item item = itemForKey26(key);
        if (item != null) callback.setReturnValue(new ItemStack(item));
    }

    private static Item itemForKey26(String key) {
        return itemForRegistry26(modernIconRegistry(key));
    }

    private static String registryForIconForTest(String key) {
        return modernIconRegistry(key);
    }

    private static Item itemForRegistry26(String registry) {
        if (registry == null || registry.isEmpty()) return null;
        int colon = registry.indexOf(':');
        if (colon <= 0 || colon + 1 >= registry.length()) return null;
        Identifier id = Identifier.fromNamespaceAndPath(registry.substring(0, colon), registry.substring(colon + 1));
        return BuiltInRegistries.ITEM.get(id).map(Holder.Reference::value).orElse(null);
    }

    private static Object newBiomeSnapshot(String name, String precipitation) throws ReflectiveOperationException {
        Class<?> snapshot = Class.forName("com.gtbstructures.overlay.WorldEnvironmentOverlayRenderer$BiomeSnapshot");
        Constructor<?> constructor = snapshot.getDeclaredConstructor(String.class, String.class);
        constructor.setAccessible(true);
        return constructor.newInstance(name, precipitation);
    }

    private static BlockPos playerBlockPos() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player == null ? BlockPos.ZERO : minecraft.player.blockPosition();
    }

    private static String modernIconRegistry(String key) {
        if ("blaze_rod".equals(key)) return "minecraft:blaze_rod";
        if ("water_bucket".equals(key)) return "minecraft:water_bucket";
        if ("snowball".equals(key)) return "minecraft:snowball";
        if ("double_plant:0".equals(key)) return "minecraft:sunflower";
        if ("netherrack".equals(key)) return "minecraft:netherrack";
        if ("end_stone".equals(key)) return "minecraft:end_stone";
        if ("sand:1".equals(key)) return "minecraft:red_sand";
        if ("sand".equals(key)) return "minecraft:sand";
        if ("sapling:0".equals(key)) return "minecraft:oak_sapling";
        if ("vine".equals(key)) return "minecraft:vine";
        if ("packed_ice".equals(key)) return "minecraft:packed_ice";
        if ("waterlily".equals(key)) return "minecraft:lily_pad";
        if ("sapling:4".equals(key)) return "minecraft:acacia_sapling";
        if ("clock".equals(key)) return "minecraft:clock";
        if ("obsidian".equals(key)) return "minecraft:obsidian";
        if ("wool:7".equals(key)) return "minecraft:gray_wool";
        if ("stone:3".equals(key)) return "minecraft:diorite";
        if ("wool:4".equals(key)) return "minecraft:yellow_wool";
        if ("gold_block".equals(key)) return "minecraft:gold_block";
        if ("stained_hardened_clay:1".equals(key)) return "minecraft:orange_terracotta";
        if ("stained_hardened_clay:9".equals(key)) return "minecraft:cyan_terracotta";
        if ("wool:15".equals(key)) return "minecraft:black_wool";
        if ("grass".equals(key)) return "minecraft:grass_block";
        return key == null || key.indexOf(':') >= 0 ? key : "minecraft:" + key;
    }
}
