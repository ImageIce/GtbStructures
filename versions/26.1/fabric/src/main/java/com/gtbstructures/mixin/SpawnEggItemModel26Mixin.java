package com.gtbstructures.mixin;

import com.gtbstructures.items.SpawnEggItemModel26Selector;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Selects the classic spawn-egg model through the unobfuscated 26.x item renderer. */
@Mixin(ItemModelResolver.class)
public abstract class SpawnEggItemModel26Mixin {
    @ModifyVariable(method = "appendItemLayers", at = @At("HEAD"), argsOnly = true)
    private ItemStack gtbs$selectSpawnEggModel(ItemStack stack) {
        return SpawnEggItemModel26Selector.selectForRender(stack);
    }
}
