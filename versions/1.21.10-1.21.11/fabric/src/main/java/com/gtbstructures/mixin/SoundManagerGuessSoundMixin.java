package com.gtbstructures.mixin;

import com.gtbstructures.sound.MinecraftClientSoundBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Filters matched guess sounds and disabled post-game fireworks before local playback. */
@Mixin(targets = {"net.minecraft.class_1144", "net.minecraft.client.sounds.SoundManager"}, remap = false)
public abstract class SoundManagerGuessSoundMixin {
    @Inject(method = {"method_4873", "play"}, at = @At("HEAD"), cancellable = true,
            remap = false, require = 1)
    private void gtbs$consumeGuessSound(@Coerce Object soundInstance, CallbackInfoReturnable<Object> callback) {
        if (MinecraftClientSoundBridge.consumeOriginal(soundInstance))
            callback.setReturnValue(MinecraftClientSoundBridge.suppressedResult(this));
    }
}
