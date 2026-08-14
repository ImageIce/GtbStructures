package com.gtbstructures.mixin;

import com.gtbstructures.items.HandheldItemOverlayRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.class_634", remap = false)
public abstract class ClientEquipmentPacketMixin {
    @Inject(
            method = "method_11151(Lnet/minecraft/class_2744;)V",
            at = @At("TAIL"),
            remap = false
    )
    private void gtbs$captureMainHandEquipment(@Coerce Object packet, CallbackInfo callback) {
        HandheldItemOverlayRenderer.onEquipmentPacket(packet);
    }
}
