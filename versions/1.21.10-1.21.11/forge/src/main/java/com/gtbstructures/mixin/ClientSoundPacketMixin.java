package com.gtbstructures.mixin;

import com.gtbstructures.diagnostics.RawPacketRecorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = {"net.minecraft.client.multiplayer.ClientPacketListener"}, remap = false)
public abstract class ClientSoundPacketMixin {
    @Inject(
            method = {"handleSoundEvent", "handleSoundEntityEvent"},
            at = @At("TAIL"),
            remap = false,
            require = 0
    )
    private void gtbs$captureSoundPacket(@Coerce Object packet, CallbackInfo callback) {
        RawPacketRecorder.INSTANCE.captureSoundPacket(packet);
    }
}
