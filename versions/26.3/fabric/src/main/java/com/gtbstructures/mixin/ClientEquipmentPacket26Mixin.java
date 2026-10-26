package com.gtbstructures.mixin;

import com.gtbstructures.items.HandheldItemOverlayRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures equipment updates through the unobfuscated 26.x packet listener. */
@Mixin(targets = "net.minecraft.client.multiplayer.ClientPacketListener", remap = false)
public abstract class ClientEquipmentPacket26Mixin {
    @Inject(
            method = "handleSetEquipment(Lnet/minecraft/network/protocol/game/ClientboundSetEquipmentPacket;)V",
            at = @At("TAIL"),
            remap = false
    )
    private void gtbs$captureMainHandEquipment(@Coerce Object packet, CallbackInfo callback) {
        HandheldItemOverlayRenderer.onEquipmentPacket(packet);
    }
}
