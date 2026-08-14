package com.gtbstructures.mixin;

import com.gtbstructures.items.SpawnEggItemModelSelector;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ItemModelResolver.class)
public abstract class SpawnEggItemModelMixin {
    @ModifyVariable(method = "appendItemLayers", at = @At("HEAD"), argsOnly = true)
    private ItemStack gtbs$selectSpawnEggModel(ItemStack stack) {
        return SpawnEggItemModelSelector.selectForRender(stack);
    }
}
