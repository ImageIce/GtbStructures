package com.gtbstructures.mixin;

import com.gtbstructures.GtbStructuresSettings;
import com.gtbstructures.items.SpawnEggItemModelOverride;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.class_10442", remap = false)
public abstract class SpawnEggItemModelResolverMixin {
    @Redirect(
            method = "method_65596",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/class_1799;method_58694(Lnet/minecraft/class_9331;)Ljava/lang/Object;",
                    remap = false
            ),
            remap = false
    )
    private Object gtbs$useSelectedSpawnEggModel(@Coerce Object stack, @Coerce Object componentType) {
        return SpawnEggItemModelOverride.resolveModel(
                stack, componentType, GtbStructuresSettings.INSTANCE.spawnEggSkin);
    }
}
