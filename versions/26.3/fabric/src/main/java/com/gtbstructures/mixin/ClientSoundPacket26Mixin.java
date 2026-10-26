package com.gtbstructures.mixin;

import com.gtbstructures.diagnostics.RawPacketRecorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures both server sound packet forms through the named 26.3 listener. */
@Mixin(targets = "net.minecraft.client.multiplayer.ClientPacketListener", remap = false)
public abstract class ClientSoundPacket26Mixin {
    @Inject(
            method = "handleSoundEvent(Lnet/minecraft/network/protocol/game/ClientboundSoundPacket;)V",
            at = @At("TAIL"), remap = false, require = 1
    )
    private void gtbs$captureSoundPacket(@Coerce Object packet, CallbackInfo callback) {
        RawPacketRecorder.INSTANCE.captureSoundPacket(packet);
    }

    @Inject(
            method = "handleSoundEntityEvent(Lnet/minecraft/network/protocol/game/ClientboundSoundEntityPacket;)V",
            at = @At("TAIL"), remap = false, require = 1
    )
    private void gtbs$captureEntitySoundPacket(@Coerce Object packet, CallbackInfo callback) {
        RawPacketRecorder.INSTANCE.captureSoundPacket(packet);
    }
}
