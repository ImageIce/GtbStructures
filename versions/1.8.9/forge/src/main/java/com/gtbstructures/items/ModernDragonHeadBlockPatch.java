package com.gtbstructures.items;

import com.gtbstructures.GtbStructures;

import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntitySkull;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class ModernDragonHeadBlockPatch {
    private static boolean installed;

    private ModernDragonHeadBlockPatch() {
    }

    public static void install() {
        if (installed) return;
        @SuppressWarnings("unchecked")
        TileEntitySpecialRenderer<TileEntitySkull> vanilla =
                (TileEntitySpecialRenderer<TileEntitySkull>)
                        TileEntityRendererDispatcher.instance.mapSpecialRenderers.get(TileEntitySkull.class);
        if (vanilla instanceof DragonHeadSkullRenderer) {
            installed = true;
            return;
        }
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntitySkull.class,
                new DragonHeadSkullRenderer(vanilla));
        installed = true;
    }

    static boolean shouldPatchSkullType(boolean modEnabled, int skullType) {
        return modEnabled && skullType == 5;
    }

    private static final class DragonHeadSkullRenderer extends TileEntitySpecialRenderer<TileEntitySkull> {
        private final TileEntitySpecialRenderer<TileEntitySkull> delegate;

        private DragonHeadSkullRenderer(TileEntitySpecialRenderer<TileEntitySkull> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void renderTileEntityAt(TileEntitySkull te, double x, double y, double z,
                                       float partialTicks, int destroyStage) {
            if (te != null && shouldPatchSkullType(GtbStructures.modEnabled, te.getSkullType())) {
                EnumFacing facing = EnumFacing.getFront(te.getBlockMetadata() & 7);
                float rotation = (float) (te.getSkullRotation() * 360) / 16.0F;
                ModernEquipmentRenderPatch.ModernDragonHeadRenderer.renderBlock(
                        (float) x, (float) y, (float) z, facing, rotation);
                return;
            }

            if (delegate != null) {
                delegate.renderTileEntityAt(te, x, y, z, partialTicks, destroyStage);
            }
        }
    }
}
