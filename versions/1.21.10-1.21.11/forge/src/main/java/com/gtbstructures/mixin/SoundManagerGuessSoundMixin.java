package com.gtbstructures.mixin;

import com.gtbstructures.sound.MinecraftClientSoundBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Cancels only the matched local experience-orb sound before SoundManager plays it. */
@Mixin(targets = {"net.minecraft.client.sounds.SoundManager"}, remap = false)
public abstract class SoundManagerGuessSoundMixin {
    @Inject(method = {"play"}, at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void gtbs$consumeGuessSound(@Coerce Object soundInstance, CallbackInfoReturnable<Object> callback) {
        if (MinecraftClientSoundBridge.consumeOriginal(soundInstance))
            callback.setReturnValue(MinecraftClientSoundBridge.suppressedResult(this));
    }
}
