package com.gtbstructures.items;

import com.gtbstructures.GtbStructures;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelArmorStand;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.entity.ArmorStandRenderer;
import net.minecraft.client.renderer.entity.layers.LayerArmorBase;
import net.minecraft.client.renderer.entity.layers.LayerHeldItem;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION_TEX;

@SideOnly(Side.CLIENT)
public final class ModernEquipmentRenderPatch {
    private static boolean installed;
    private static final Field LAYER_RENDERERS_FIELD = findLayerRenderersField();

    private ModernEquipmentRenderPatch() {
    }

    public static void install() {
        if (installed) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.getRenderManager() == null) return;

        Render<EntityArmorStand> render = mc.getRenderManager().getEntityClassRenderObject(EntityArmorStand.class);
        if (!(render instanceof ArmorStandRenderer)) return;

        ArmorStandRenderer armorStandRenderer = (ArmorStandRenderer) render;
        armorStandRenderer.addLayer(new GenericArmorSlotItemLayer(armorStandRenderer.getMainModel()));
        installHeldItemLayer(armorStandRenderer);
        installPlayerHeldItemLayers(mc);
        installed = true;
    }

    static boolean shouldPatchRender(ItemStack stack, int armorIndex, boolean helmetSlot) {
        if (!customEquipmentVisibleForMaster(GtbStructures.modEnabled)) return false;
        if (stack == null || stack.getItem() == null) return false;

        Item item = stack.getItem();
        return hasPatchableRender(true,
                item instanceof ItemArmor,
                !isMismatchedArmorSlot(stack, armorIndex),
                item instanceof ItemBlock,
                item == Items.skull,
                helmetSlot);
    }

    static boolean hasPatchableRender(boolean hasItem, boolean isArmor, boolean armorMatchesSlot, boolean isBlock,
                                      boolean isSkull, boolean isHelmetSlot) {
        return hasItem && !isArmor && (!isHelmetSlot || (!isBlock && !isSkull));
    }

    static boolean isArmorCorrectForSlot(int armorType, int armorIndex) {
        return armorType + armorIndex == 3;
    }

    private static boolean isMismatchedArmorSlot(ItemStack stack, int armorIndex) {
        if (stack == null || !(stack.getItem() instanceof ItemArmor)) return false;
        return !isArmorCorrectForSlot(((ItemArmor) stack.getItem()).armorType, armorIndex);
    }

    private static ModernEquipmentItem decodedModernItem(ItemStack stack) {
        if (stack != null && stack.getItem() == Items.skull && stack.getMetadata() == 5) {
            return ModernEquipmentItem.dragonHead();
        }
        if (stack != null && stack.getItem() == Items.spawn_egg) {
            return ModernEquipmentItem.spawnEgg(stack.getMetadata());
        }
        return null;
    }

    private static ModernEquipmentItem renderableModernItem(ModernEquipmentItem item) {
        if (!customEquipmentVisibleForMaster(GtbStructures.modEnabled)) return null;
        if (item == null) return null;
        if (item.kind == ModernEquipmentItem.Kind.SPAWN_EGG) {
            if (item.spawnEgg == null) return null;
            if (item.spawnEgg.vanilla18 && !"new".equals(GtbStructures.spawnEggSkin)) return null;
        }
        return item;
    }

    static boolean shouldRenderHeldThroughItemModel(ModernEquipmentItem item) {
        return customEquipmentVisibleForMaster(GtbStructures.modEnabled)
                && item != null
                && item.kind == ModernEquipmentItem.Kind.SPAWN_EGG
                && item.spawnEgg != null;
    }

    private static ItemStack heldSpawnEggStack(ItemStack decodedStack, ModernEquipmentItem item) {
        if (!shouldRenderHeldThroughItemModel(item)) return decodedStack;
        ItemStack renderStack = decodedStack != null && decodedStack.getItem() == Items.spawn_egg
                ? decodedStack.copy()
                : new ItemStack(Items.spawn_egg, 1, item.spawnEgg.damage);
        renderStack.setItemDamage(item.spawnEgg.damage);
        return renderStack;
    }

    static boolean customEquipmentVisibleForMaster(boolean modEnabled) {
        return modEnabled;
    }

    private static void installPlayerHeldItemLayers(Minecraft mc) {
        try {
            Map<String, RenderPlayer> skinMap = mc.getRenderManager().getSkinMap();
            if (skinMap == null) return;
            for (RenderPlayer renderPlayer : skinMap.values()) {
                installHeldItemLayer(renderPlayer);
            }
        } catch (Throwable ignored) {
            // Best-effort visual compatibility patch.
        }
    }

    private static void installFilteredArmorLayers(RendererLivingEntity<?> renderer) {
        if (renderer == null) return;
        try {
            List layers = layerRenderers(renderer);
            if (layers == null) return;
            for (int i = 0; i < layers.size(); i++) {
                Object layer = layers.get(i);
                if (layer instanceof FilteringArmorLayer) return;
                if (layer instanceof LayerArmorBase) {
                    layers.set(i, new FilteringArmorLayer((LayerRenderer) layer));
                }
            }
        } catch (Throwable ignored) {
            // If reflection fails, vanilla armor rendering remains unchanged.
        }
    }

    private static void installHeldItemLayer(RendererLivingEntity<?> renderer) {
        if (renderer == null) return;
        try {
            List layers = layerRenderers(renderer);
            if (layers == null) return;
            boolean present = false;
            Iterator iterator = layers.iterator();
            while (iterator.hasNext()) {
                Object layer = iterator.next();
                if (layer instanceof ModernHeldItemLayer) {
                    present = true;
                } else if (layer instanceof LayerHeldItem) {
                    iterator.remove();
                }
            }
            if (!present && renderer.getMainModel() instanceof ModelBiped) {
                ((RendererLivingEntity) renderer).addLayer(new ModernHeldItemLayer(renderer));
            }
        } catch (Throwable ignored) {
            // If reflection fails, leave vanilla rendering in place.
        }
    }

    private static List layerRenderers(RendererLivingEntity<?> renderer) throws IllegalAccessException {
        return LAYER_RENDERERS_FIELD == null ? null : (List) LAYER_RENDERERS_FIELD.get(renderer);
    }

    private static Field findLayerRenderersField() {
        try {
            Field field = ReflectionHelper.findField(RendererLivingEntity.class, "layerRenderers", "field_177097_h");
            field.setAccessible(true);
            return field;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static final class ModernHeldItemLayer implements LayerRenderer<EntityLivingBase> {
        private final RendererLivingEntity<?> livingEntityRenderer;

        private ModernHeldItemLayer(RendererLivingEntity<?> livingEntityRenderer) {
            this.livingEntityRenderer = livingEntityRenderer;
        }

        @Override
        public void doRenderLayer(EntityLivingBase entity, float limbSwing, float limbSwingAmount,
                                  float partialTicks, float ageInTicks, float netHeadYaw,
                                  float headPitch, float scale) {
            ItemStack held = entity.getHeldItem();
            ModernEquipmentItem modernItem = renderableModernItem(decodedModernItem(held));
            if (modernItem == null) {
                modernItem = renderableModernItem(ModernEquipmentItemPatch.INSTANCE.heldItem(entity, held));
            }
            boolean hasVanillaHeldItem = held != null && held.getItem() != null;
            if (modernItem == null && !hasVanillaHeldItem) return;

            GlStateManager.pushMatrix();

            if (this.livingEntityRenderer.getMainModel().isChild) {
                float childScale = 0.5F;
                GlStateManager.translate(0.0F, 0.625F, 0.0F);
                GlStateManager.rotate(-20.0F, -1.0F, 0.0F, 0.0F);
                GlStateManager.scale(childScale, childScale, childScale);
            }

            ((ModelBiped) this.livingEntityRenderer.getMainModel()).postRenderArm(0.0625F);
            GlStateManager.translate(-0.0625F, 0.4375F, 0.0625F);

            if (entity instanceof EntityPlayer && ((EntityPlayer) entity).fishEntity != null) {
                held = new ItemStack(Items.fishing_rod, 0);
                hasVanillaHeldItem = true;
                modernItem = null;
            }

            if (hasVanillaHeldItem) {
                Item item = held.getItem();
                if (item instanceof ItemBlock && Block.getBlockFromItem(item).getRenderType() == 2) {
                    GlStateManager.translate(0.0F, 0.1875F, -0.3125F);
                    GlStateManager.rotate(20.0F, 1.0F, 0.0F, 0.0F);
                    GlStateManager.rotate(45.0F, 0.0F, 1.0F, 0.0F);
                    float blockScale = 0.375F;
                    GlStateManager.scale(-blockScale, -blockScale, blockScale);
                }
            }

            if (entity.isSneaking()) {
                GlStateManager.translate(0.0F, 0.203125F, 0.0F);
            }

            if (shouldRenderHeldThroughItemModel(modernItem)) {
                // The baked-model patch supplies the supplemental sprite while the vanilla item renderer
                // supplies the exact generated-item orientation, offset, scale, depth, and lighting.
                ItemStack renderStack = heldSpawnEggStack(held, modernItem);
                Minecraft.getMinecraft().getItemRenderer().renderItem(entity, renderStack,
                        ItemCameraTransforms.TransformType.THIRD_PERSON);
            } else if (modernItem != null) {
                GlStateManager.translate(0.0F, 0.05F, -0.08F);
                GlStateManager.rotate(-20.0F, 1.0F, 0.0F, 0.0F);
                GlStateManager.rotate(90.0F, 0.0F, 1.0F, 0.0F);
                GlStateManager.scale(0.70F, 0.70F, 0.70F);
                renderModernItem(modernItem);
            } else {
                Minecraft.getMinecraft().getItemRenderer().renderItem(entity, held,
                        ItemCameraTransforms.TransformType.THIRD_PERSON);
            }

            GlStateManager.popMatrix();
        }

        private void renderModernItem(ModernEquipmentItem item) {
            if (item.kind == ModernEquipmentItem.Kind.BED) {
                ModernBedRenderer.render(item.colorMeta);
            } else if (item.kind == ModernEquipmentItem.Kind.DRAGON_HEAD) {
                ModernDragonHeadRenderer.render();
            } else if (item.kind == ModernEquipmentItem.Kind.SPAWN_EGG) {
                ModernSpawnEggRenderer.render(item.spawnEgg);
            }
        }

        @Override
        public boolean shouldCombineTextures() {
            return false;
        }
    }

    private static final class GenericArmorSlotItemLayer implements LayerRenderer<EntityArmorStand> {
        private final ModelRenderer head;
        private final ModelRenderer body;

        private GenericArmorSlotItemLayer(ModelArmorStand model) {
            this.head = model.bipedHead;
            this.body = model.bipedBody;
        }

        @Override
        public void doRenderLayer(EntityArmorStand armorStand, float limbSwing, float limbSwingAmount,
                                  float partialTicks, float ageInTicks, float netHeadYaw,
                                  float headPitch, float scale) {
            if (!customEquipmentVisibleForMaster(GtbStructures.modEnabled)) return;
            renderHelmet(armorStand, scale);
            renderBodySlot(armorStand, armorStand.getCurrentArmor(2), scale, 2, 0.25F, 0.50F);
            renderBodySlot(armorStand, armorStand.getCurrentArmor(1), scale, 1, 0.62F, 0.45F);
            renderBodySlot(armorStand, armorStand.getCurrentArmor(0), scale, 0, 0.95F, 0.40F);
        }

        private void renderHelmet(EntityArmorStand armorStand, float scale) {
            ItemStack helmet = armorStand.getCurrentArmor(3);
            ModernEquipmentItem modernItem = renderableModernItem(decodedModernItem(helmet));
            if (modernItem == null) {
                modernItem = renderableModernItem(ModernEquipmentItemPatch.INSTANCE.armorItem(armorStand, 3, helmet));
            }
            if (modernItem == null && !shouldPatchRender(helmet, 3, true)) return;

            Minecraft mc = Minecraft.getMinecraft();
            GlStateManager.pushMatrix();

            if (armorStand.isSneaking()) {
                GlStateManager.translate(0.0F, 0.2F, 0.0F);
            }

            if (armorStand.isChild()) {
                float childScale = 0.7F;
                GlStateManager.scale(childScale, childScale, childScale);
                GlStateManager.translate(0.0F, 16.0F * scale, 0.0F);
            }

            this.head.postRender(scale);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.translate(0.0F, -0.25F, 0.0F);
            GlStateManager.rotate(180.0F, 0.0F, 1.0F, 0.0F);
            GlStateManager.scale(0.625F, -0.625F, -0.625F);
            if (modernItem != null) {
                renderModernItem(modernItem);
            } else {
                mc.getItemRenderer().renderItem(armorStand, helmet, ItemCameraTransforms.TransformType.HEAD);
            }
            GlStateManager.popMatrix();
        }

        private void renderBodySlot(EntityArmorStand armorStand, ItemStack stack, float scale,
                                    int armorIndex,
                                    float yOffset, float itemScale) {
            ModernEquipmentItem modernItem = renderableModernItem(ModernEquipmentItemPatch.INSTANCE.armorItem(armorStand, armorIndex, stack));
            if (modernItem == null) {
                modernItem = renderableModernItem(decodedModernItem(stack));
            }
            if (modernItem == null && !shouldPatchRender(stack, armorIndex, false)) return;

            Minecraft mc = Minecraft.getMinecraft();
            GlStateManager.pushMatrix();

            if (armorStand.isSneaking()) {
                GlStateManager.translate(0.0F, 0.2F, 0.0F);
            }

            if (armorStand.isChild()) {
                float childScale = 0.7F;
                GlStateManager.scale(childScale, childScale, childScale);
                GlStateManager.translate(0.0F, 16.0F * scale, 0.0F);
            }

            this.body.postRender(scale);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.translate(0.0F, yOffset, -0.18F);
            GlStateManager.rotate(180.0F, 0.0F, 1.0F, 0.0F);
            GlStateManager.scale(itemScale, -itemScale, -itemScale);
            if (modernItem != null) {
                renderModernItem(modernItem);
            } else {
                mc.getItemRenderer().renderItem(armorStand, stack, ItemCameraTransforms.TransformType.HEAD);
            }
            GlStateManager.popMatrix();
        }

        private void renderModernItem(ModernEquipmentItem item) {
            if (item.kind == ModernEquipmentItem.Kind.BED) {
                ModernBedRenderer.render(item.colorMeta);
            } else if (item.kind == ModernEquipmentItem.Kind.DRAGON_HEAD) {
                ModernDragonHeadRenderer.render();
            } else if (item.kind == ModernEquipmentItem.Kind.SPAWN_EGG) {
                ModernSpawnEggRenderer.render(item.spawnEgg);
            }
        }

        @Override
        public boolean shouldCombineTextures() {
            return false;
        }
    }

    private static final class FilteringArmorLayer implements LayerRenderer<EntityArmorStand> {
        private final LayerRenderer delegate;

        private FilteringArmorLayer(LayerRenderer delegate) {
            this.delegate = delegate;
        }

        @Override
        public void doRenderLayer(EntityArmorStand armorStand, float limbSwing, float limbSwingAmount,
                                  float partialTicks, float ageInTicks, float netHeadYaw,
                                  float headPitch, float scale) {
            if (!customEquipmentVisibleForMaster(GtbStructures.modEnabled)) {
                delegate.doRenderLayer(armorStand, limbSwing, limbSwingAmount, partialTicks,
                        ageInTicks, netHeadYaw, headPitch, scale);
                return;
            }
            ItemStack[] hidden = null;
            for (int armorIndex = 0; armorIndex < 4; armorIndex++) {
                ItemStack stack = armorStand.getCurrentArmor(armorIndex);
                if (isMismatchedArmorSlot(stack, armorIndex)) {
                    if (hidden == null) hidden = new ItemStack[4];
                    hidden[armorIndex] = stack;
                    armorStand.setCurrentItemOrArmor(armorIndex + 1, null);
                }
            }

            try {
                delegate.doRenderLayer(armorStand, limbSwing, limbSwingAmount, partialTicks,
                        ageInTicks, netHeadYaw, headPitch, scale);
            } finally {
                if (hidden != null) {
                    for (int armorIndex = 0; armorIndex < hidden.length; armorIndex++) {
                        if (hidden[armorIndex] != null) {
                            armorStand.setCurrentItemOrArmor(armorIndex + 1, hidden[armorIndex]);
                        }
                    }
                }
            }
        }

        @Override
        public boolean shouldCombineTextures() {
            return delegate.shouldCombineTextures();
        }
    }

    private static final class ModernBedRenderer {
        private static final int[][] BED_COLORS = new int[][] {
                {240, 240, 240}, {249, 128, 29}, {199, 78, 189}, {58, 179, 218},
                {254, 216, 61}, {128, 199, 31}, {243, 139, 170}, {71, 79, 82},
                {157, 157, 151}, {22, 156, 156}, {137, 50, 184}, {60, 68, 170},
                {131, 84, 50}, {94, 124, 22}, {176, 46, 38}, {29, 29, 33}
        };

        private ModernBedRenderer() {
        }

        static void render(int colorMeta) {
            int[] color = BED_COLORS[colorMeta >= 0 && colorMeta < BED_COLORS.length ? colorMeta : 14];

            GlStateManager.pushMatrix();
            GlStateManager.disableTexture2D();
            GlStateManager.disableLighting();
            GlStateManager.disableCull();

            drawBox(-0.58F, -0.16F, -0.28F, 0.58F, -0.04F, 0.28F, 92, 58, 35);
            drawBox(-0.58F, -0.04F, -0.28F, 0.20F, 0.12F, 0.28F, color[0], color[1], color[2]);
            drawBox(0.20F, -0.035F, -0.25F, 0.55F, 0.13F, 0.25F, 235, 235, 226);
            drawBox(-0.56F, -0.28F, -0.26F, -0.46F, -0.16F, -0.16F, 92, 58, 35);
            drawBox(-0.56F, -0.28F, 0.16F, -0.46F, -0.16F, 0.26F, 92, 58, 35);
            drawBox(0.46F, -0.28F, -0.26F, 0.56F, -0.16F, -0.16F, 92, 58, 35);
            drawBox(0.46F, -0.28F, 0.16F, 0.56F, -0.16F, 0.26F, 92, 58, 35);

            GlStateManager.enableCull();
            GlStateManager.enableLighting();
            GlStateManager.enableTexture2D();
            GlStateManager.popMatrix();
        }

        private static void drawBox(float minX, float minY, float minZ,
                                    float maxX, float maxY, float maxZ,
                                    int r, int g, int b) {
            Tessellator tessellator = Tessellator.getInstance();
            WorldRenderer world = tessellator.getWorldRenderer();
            world.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);

            face(world, minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b);
            face(world, maxX, minY, minZ, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, r, g, b);
            face(world, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, minX, maxY, minZ, r, g, b);
            face(world, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ, r, g, b);
            face(world, minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, r, g, b);
            face(world, maxX, minY, maxZ, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b);

            tessellator.draw();
        }

        private static void face(WorldRenderer world,
                                 float x1, float y1, float z1,
                                 float x2, float y2, float z2,
                                 float x3, float y3, float z3,
                                 float x4, float y4, float z4,
                                 int r, int g, int b) {
            world.pos(x1, y1, z1).color(r, g, b, 255).endVertex();
            world.pos(x2, y2, z2).color(r, g, b, 255).endVertex();
            world.pos(x3, y3, z3).color(r, g, b, 255).endVertex();
            world.pos(x4, y4, z4).color(r, g, b, 255).endVertex();
        }
    }

    static final class ModernDragonHeadRenderer {
        private static final ResourceLocation DRAGON_TEXTURE =
                new ResourceLocation("textures/entity/enderdragon/dragon.png");
        private static final DragonHeadModel MODEL = new DragonHeadModel();
        private static final float BLOCK_FLOOR_Y_OFFSET = 0.5F;
        private static final float GUI_TRANSLATE_Y_FACTOR = 0.55F;
        private static final float GUI_SCALE_FACTOR = 0.72F;

        private ModernDragonHeadRenderer() {
        }

        static void render() {
            Minecraft.getMinecraft().getTextureManager().bindTexture(DRAGON_TEXTURE);
            GlStateManager.pushMatrix();
            GlStateManager.enableTexture2D();
            GlStateManager.enableLighting();
            GlStateManager.enableCull();
            GlStateManager.scale(-0.85F, -0.85F, 0.85F);
            GlStateManager.translate(0.0F, -0.18F, 0.28F);
            MODEL.render(0.0625F);
            GlStateManager.popMatrix();
        }

        static void renderBlock(float x, float y, float z, EnumFacing facing, float rotation) {
            Minecraft.getMinecraft().getTextureManager().bindTexture(DRAGON_TEXTURE);
            GlStateManager.pushMatrix();
            GlStateManager.disableCull();

            if (facing != EnumFacing.UP) {
                switch (facing) {
                    case NORTH:
                        GlStateManager.translate(x + 0.5F, y + blockYOffset(facing), z + 0.74F);
                        break;
                    case SOUTH:
                        GlStateManager.translate(x + 0.5F, y + blockYOffset(facing), z + 0.26F);
                        rotation = 180.0F;
                        break;
                    case WEST:
                        GlStateManager.translate(x + 0.74F, y + blockYOffset(facing), z + 0.5F);
                        rotation = 270.0F;
                        break;
                    case EAST:
                    default:
                        GlStateManager.translate(x + 0.26F, y + blockYOffset(facing), z + 0.5F);
                        rotation = 90.0F;
                        break;
                }
            } else {
                GlStateManager.translate(x + 0.5F, y + blockYOffset(facing), z + 0.5F);
            }

            GlStateManager.enableRescaleNormal();
            GlStateManager.scale(-1.0F, -1.0F, 1.0F);
            GlStateManager.rotate(180.0F - rotation, 0.0F, 1.0F, 0.0F);
            MODEL.render(0.0625F);
            GlStateManager.enableCull();
            GlStateManager.popMatrix();
        }

        static void renderGuiIcon(Minecraft mc, int x, int y, int size) {
            mc.getTextureManager().bindTexture(DRAGON_TEXTURE);
            GlStateManager.pushMatrix();
            RenderHelper.enableGUIStandardItemLighting();
            GlStateManager.enableTexture2D();
            GlStateManager.enableRescaleNormal();
            GlStateManager.translate((float) x + size * 0.5F, (float) y + size * GUI_TRANSLATE_Y_FACTOR, 100.0F);
            GlStateManager.scale((float) size * GUI_SCALE_FACTOR, (float) size * GUI_SCALE_FACTOR,
                    (float) size * GUI_SCALE_FACTOR);
            GlStateManager.rotate(210.0F, 0.0F, 1.0F, 0.0F);
            GlStateManager.rotate(15.0F, 1.0F, 0.0F, 0.0F);
            MODEL.render(0.0625F);
            GlStateManager.disableRescaleNormal();
            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableLighting();
            GlStateManager.popMatrix();
        }

        static float blockYOffset(EnumFacing facing) {
            return facing == EnumFacing.UP ? BLOCK_FLOOR_Y_OFFSET : 0.25F;
        }

        static float guiScaleYForTest(int size) {
            return (float) size * GUI_SCALE_FACTOR;
        }

        static float guiTranslateYForTest(int size) {
            return (float) size * GUI_TRANSLATE_Y_FACTOR;
        }
    }

    private static final class ModernSpawnEggRenderer {
        private static final ResourceLocation CLASSIC_BASE =
                new ResourceLocation("gtbstructures", "textures/item/spawn_egg/classic/spawn_egg.png");
        private static final ResourceLocation CLASSIC_OVERLAY =
                new ResourceLocation("gtbstructures", "textures/item/spawn_egg/classic/spawn_egg_overlay.png");

        private ModernSpawnEggRenderer() {
        }

        static void render(ModernSpawnEggData egg) {
            if (egg == null) return;

            GlStateManager.pushMatrix();
            GlStateManager.enableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.disableCull();
            GlStateManager.translate(0.0F, 0.0F, -0.01F);

            if ("new".equals(GtbStructures.spawnEggSkin)) {
                Minecraft.getMinecraft().getTextureManager().bindTexture(egg.newTexture);
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                drawQuad();
            } else {
                Minecraft.getMinecraft().getTextureManager().bindTexture(CLASSIC_BASE);
                color(egg.classicBaseColor);
                drawQuad();
                Minecraft.getMinecraft().getTextureManager().bindTexture(CLASSIC_OVERLAY);
                color(egg.classicOverlayColor);
                drawQuad();
            }

            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.enableCull();
            GlStateManager.disableBlend();
            GlStateManager.popMatrix();
        }

        private static void color(int rgb) {
            float r = ((rgb >> 16) & 0xFF) / 255.0F;
            float g = ((rgb >> 8) & 0xFF) / 255.0F;
            float b = (rgb & 0xFF) / 255.0F;
            GlStateManager.color(r, g, b, 1.0F);
        }

        private static void drawQuad() {
            Tessellator tessellator = Tessellator.getInstance();
            WorldRenderer world = tessellator.getWorldRenderer();
            world.begin(GL11.GL_QUADS, POSITION_TEX);
            world.pos(-0.5F, 0.5F, 0.0F).tex(0.0D, 0.0D).endVertex();
            world.pos(0.5F, 0.5F, 0.0F).tex(1.0D, 0.0D).endVertex();
            world.pos(0.5F, -0.5F, 0.0F).tex(1.0D, 1.0D).endVertex();
            world.pos(-0.5F, -0.5F, 0.0F).tex(0.0D, 1.0D).endVertex();
            tessellator.draw();
        }
    }

    private static final class DragonHeadModel extends ModelBase {
        private final ModelRenderer head;
        private final ModelRenderer jaw;

        private DragonHeadModel() {
            this.textureWidth = 256;
            this.textureHeight = 256;
            this.setTextureOffset("head.upperhead", 112, 30);
            this.setTextureOffset("head.upperlip", 176, 44);
            this.setTextureOffset("jaw.jaw", 176, 65);
            this.setTextureOffset("head.scale", 0, 0);
            this.setTextureOffset("head.nostril", 112, 0);

            float offset = -16.0F;
            this.head = new ModelRenderer(this, "head");
            this.head.addBox("upperlip", -6.0F, -1.0F, -8.0F + offset, 12, 5, 16);
            this.head.addBox("upperhead", -8.0F, -8.0F, 6.0F + offset, 16, 16, 16);
            this.head.mirror = true;
            this.head.addBox("scale", -5.0F, -12.0F, 12.0F + offset, 2, 4, 6);
            this.head.addBox("nostril", -5.0F, -3.0F, -6.0F + offset, 2, 2, 4);
            this.head.mirror = false;
            this.head.addBox("scale", 3.0F, -12.0F, 12.0F + offset, 2, 4, 6);
            this.head.addBox("nostril", 3.0F, -3.0F, -6.0F + offset, 2, 2, 4);

            this.jaw = new ModelRenderer(this, "jaw");
            this.jaw.setRotationPoint(0.0F, 4.0F, 8.0F + offset);
            this.jaw.addBox("jaw", -6.0F, 0.0F, -16.0F, 12, 4, 16);
            this.head.addChild(this.jaw);
        }

        private void render(float scale) {
            this.head.rotationPointX = 0.0F;
            this.head.rotationPointY = 0.0F;
            this.head.rotationPointZ = 0.0F;
            this.head.rotateAngleX = 0.0F;
            this.head.rotateAngleY = 0.0F;
            this.head.rotateAngleZ = 0.0F;
            this.jaw.rotateAngleX = 0.16F;
            this.head.render(scale);
        }
    }
}
