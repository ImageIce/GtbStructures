package com.gtbstructures.mixin;

import com.gtbstructures.GtbStructuresFabric;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Repairs encoded Build Battle team colors as soon as vanilla applies the packet. */
@Pseudo
@Mixin(targets = {
        "net.minecraft.class_634",
        "net.minecraft.client.multiplayer.ClientPacketListener"
}, remap = false)
public abstract class ClientPlayerTeamPacketMixin {
    @Inject(
            method = {
                    "method_11099(Lnet/minecraft/class_5900;)V",
                    "handleSetPlayerTeamPacket(Lnet/minecraft/network/protocol/game/ClientboundSetPlayerTeamPacket;)V"
            },
            at = @At("RETURN"),
            remap = false,
            require = 1
    )
    private void gtbs$repairEncodedTeamColor(@Coerce Object packet, CallbackInfo callback) {
        GtbStructuresFabric.onFabricPlayerTeamPacketApplied(this, packet);
    }
}
