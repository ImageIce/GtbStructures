package com.gtbstructures.mixin;

import com.gtbstructures.GtbStructuresForge;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Repairs encoded Build Battle team colors as soon as vanilla applies the packet. */
@Mixin(ClientPacketListener.class)
public abstract class ClientPlayerTeamPacketMixin {
    @Inject(method = "handleSetPlayerTeamPacket", at = @At("RETURN"))
    private void gtbs$repairEncodedTeamColor(ClientboundSetPlayerTeamPacket packet,
                                             CallbackInfo callback) {
        GtbStructuresForge.onPlayerTeamPacketApplied(
                (ClientPacketListener) (Object) this, packet);
    }
}
