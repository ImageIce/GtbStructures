package com.gtbstructures.mixin;

import com.gtbstructures.diagnostics.RawPacketRecorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures every server sound packet before vanilla plays it. */
@Mixin(targets = {"net.minecraft.class_634", "net.minecraft.client.multiplayer.ClientPacketListener"}, remap = false)
public abstract class ClientSoundPacketMixin {
    @Inject(
            method = {
                    "method_11146", "method_11125",
                    "handleSoundEvent", "handleSoundEntityEvent"
            },
            at = @At("TAIL"),
            remap = false,
            require = 0
    )
    private void gtbs$captureSoundPacket(@Coerce Object packet, CallbackInfo callback) {
        RawPacketRecorder.INSTANCE.captureSoundPacket(packet);
    }
}
