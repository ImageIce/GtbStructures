package com.gtbstructures.items;

import com.gtbstructures.GtbStructures;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BreakingFour;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.model.IColoredBakedQuad;
import net.minecraftforge.client.model.IFlexibleBakedModel;
import net.minecraftforge.client.model.IPerspectiveAwareModel;
import net.minecraftforge.client.model.ISmartItemModel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import org.apache.commons.lang3.tuple.Pair;

import javax.vecmath.Matrix4f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Supplies the selected GTBS spawn-egg skin to every vanilla item-render context. */
public final class ModernSpawnEggItemRenderPatch {
    private static final ModernSpawnEggItemRenderPatch INSTANCE = new ModernSpawnEggItemRenderPatch();
    private static final ModelResourceLocation SPAWN_EGG_MODEL =
            new ModelResourceLocation("minecraft:spawn_egg", "inventory");
    private static final int MAX_DAMAGE = 255;
    private static boolean installed;

    enum SkinModel {
        VANILLA,
        CLASSIC,
        NEW
    }

    private ModernSpawnEggItemRenderPatch() {
    }

    public static void install() {
        if (installed) return;
        MinecraftForge.EVENT_BUS.register(INSTANCE);
        installed = true;
    }

    @SubscribeEvent
    public void onTextureStitchPre(TextureStitchEvent.Pre event) {
        for (int damage = 0; damage <= MAX_DAMAGE; damage++) {
            ModernSpawnEggData egg = ModernSpawnEggData.byDamage(damage);
            if (egg != null) {
                event.map.registerSprite(newSpriteLocation(egg));
            }
        }
    }

    @SubscribeEvent
    public void onModelBake(ModelBakeEvent event) {
        IBakedModel vanillaModel = event.modelRegistry.getObject(SPAWN_EGG_MODEL);
        if (vanillaModel == null || vanillaModel instanceof SpawnEggSkinModel) return;

        Map<Integer, IBakedModel> classicModels = new HashMap<Integer, IBakedModel>();
        Map<Integer, IBakedModel> newModels = new HashMap<Integer, IBakedModel>();
        for (int damage = 0; damage <= MAX_DAMAGE; damage++) {
            ModernSpawnEggData egg = ModernSpawnEggData.byDamage(damage);
            if (egg == null) continue;

            if (!egg.vanilla18) {
                classicModels.put(Integer.valueOf(damage), createClassicModel(vanillaModel, egg));
            }
            TextureAtlasSprite sprite = event.modelManager.getTextureMap()
                    .getAtlasSprite(newSpriteLocation(egg).toString());
            newModels.put(Integer.valueOf(damage), createNewModel(vanillaModel, sprite));
        }

        event.modelRegistry.putObject(SPAWN_EGG_MODEL,
                new SpawnEggSkinModel(vanillaModel, classicModels, newModels));
    }

    static SkinModel selectedModel(boolean modEnabled, String skin, ModernSpawnEggData egg) {
        if (!modEnabled) return SkinModel.VANILLA;
        if (egg == null) return SkinModel.VANILLA;
        if ("new".equals(skin)) return SkinModel.NEW;
        return egg.vanilla18 ? SkinModel.VANILLA : SkinModel.CLASSIC;
    }

    static ResourceLocation newSpriteLocation(ModernSpawnEggData egg) {
        return new ResourceLocation("gtbstructures",
                "item/spawn_egg/new/" + egg.textureName + "_spawn_egg");
    }

    static int classicColor(ModernSpawnEggData egg, int tintIndex) {
        if (egg == null) return 0xFFFFFF;
        if (tintIndex == 0) return egg.classicBaseColor;
        if (tintIndex == 1) return egg.classicOverlayColor;
        return 0xFFFFFF;
    }

    static boolean isNewSkinQuad(int tintIndex) {
        return tintIndex <= 0;
    }

    private static IBakedModel createClassicModel(IBakedModel vanillaModel, ModernSpawnEggData egg) {
        List<BakedQuad> general = recolorClassicQuads(vanillaModel.getGeneralQuads(), egg);
        List<List<BakedQuad>> faces = new ArrayList<List<BakedQuad>>(EnumFacing.values().length);
        for (EnumFacing face : EnumFacing.values()) {
            faces.add(recolorClassicQuads(vanillaModel.getFaceQuads(face), egg));
        }
        return new FixedEggModel(vanillaModel, general, faces, vanillaModel.getParticleTexture(), egg, null);
    }

    private static List<BakedQuad> recolorClassicQuads(List<BakedQuad> source, ModernSpawnEggData egg) {
        if (source == null || source.isEmpty()) return Collections.emptyList();
        List<BakedQuad> result = new ArrayList<BakedQuad>(source.size());
        for (BakedQuad quad : source) {
            result.add(coloredQuad(quad, classicColor(egg, quad.getTintIndex())));
        }
        return Collections.unmodifiableList(result);
    }

    private static IBakedModel createNewModel(IBakedModel vanillaModel, TextureAtlasSprite sprite) {
        List<BakedQuad> general = retextureNewQuads(vanillaModel.getGeneralQuads(), sprite);
        List<List<BakedQuad>> faces = new ArrayList<List<BakedQuad>>(EnumFacing.values().length);
        for (EnumFacing face : EnumFacing.values()) {
            faces.add(retextureNewQuads(vanillaModel.getFaceQuads(face), sprite));
        }
        return new FixedEggModel(vanillaModel, general, faces, sprite, null, sprite);
    }

    private static List<BakedQuad> retextureNewQuads(List<BakedQuad> source, TextureAtlasSprite sprite) {
        if (source == null || source.isEmpty()) return Collections.emptyList();
        List<BakedQuad> result = new ArrayList<BakedQuad>(source.size());
        for (BakedQuad quad : source) {
            if (!isNewSkinQuad(quad.getTintIndex())) continue;
            result.add(coloredQuad(new BreakingFour(quad, sprite), 0xFFFFFF));
        }
        return Collections.unmodifiableList(result);
    }

    private static BakedQuad coloredQuad(BakedQuad quad, int rgb) {
        int[] data = Arrays.copyOf(quad.getVertexData(), quad.getVertexData().length);
        int abgr = 0xFF000000
                | ((rgb & 0x0000FF) << 16)
                | (rgb & 0x00FF00)
                | ((rgb & 0xFF0000) >>> 16);
        for (int vertex = 0; vertex < 4; vertex++) {
            data[vertex * 7 + 3] = abgr;
        }
        return new IColoredBakedQuad.ColoredBakedQuad(data, -1, quad.getFace());
    }

    private static final class SpawnEggSkinModel implements ISmartItemModel {
        private final IBakedModel vanillaModel;
        private final Map<Integer, IBakedModel> classicModels;
        private final Map<Integer, IBakedModel> newModels;

        private SpawnEggSkinModel(IBakedModel vanillaModel, Map<Integer, IBakedModel> classicModels,
                                  Map<Integer, IBakedModel> newModels) {
            this.vanillaModel = vanillaModel;
            this.classicModels = classicModels;
            this.newModels = newModels;
        }

        @Override
        public IBakedModel handleItemState(ItemStack stack) {
            ModernSpawnEggData egg = stack == null ? null : ModernSpawnEggData.byDamage(stack.getMetadata());
            SkinModel selected = selectedModel(
                    GtbStructures.modEnabled, GtbStructures.spawnEggSkin, egg);
            IBakedModel model = null;
            if (selected == SkinModel.CLASSIC) {
                model = classicModels.get(Integer.valueOf(egg.damage));
            } else if (selected == SkinModel.NEW) {
                model = newModels.get(Integer.valueOf(egg.damage));
            }
            return model == null ? vanillaModel : model;
        }

        @Override
        public List<BakedQuad> getFaceQuads(EnumFacing face) {
            return vanillaModel.getFaceQuads(face);
        }

        @Override
        public List<BakedQuad> getGeneralQuads() {
            return vanillaModel.getGeneralQuads();
        }

        @Override
        public boolean isAmbientOcclusion() {
            return vanillaModel.isAmbientOcclusion();
        }

        @Override
        public boolean isGui3d() {
            return vanillaModel.isGui3d();
        }

        @Override
        public boolean isBuiltInRenderer() {
            return vanillaModel.isBuiltInRenderer();
        }

        @Override
        public TextureAtlasSprite getParticleTexture() {
            return vanillaModel.getParticleTexture();
        }

        @Override
        public ItemCameraTransforms getItemCameraTransforms() {
            return vanillaModel.getItemCameraTransforms();
        }
    }

    private static final class FixedEggModel implements IPerspectiveAwareModel {
        private final IBakedModel template;
        private final List<BakedQuad> generalQuads;
        private final List<List<BakedQuad>> faceQuads;
        private final TextureAtlasSprite particleTexture;
        private final ModernSpawnEggData classicEgg;
        private final TextureAtlasSprite newSprite;

        private FixedEggModel(IBakedModel template, List<BakedQuad> generalQuads,
                              List<List<BakedQuad>> faceQuads, TextureAtlasSprite particleTexture,
                              ModernSpawnEggData classicEgg, TextureAtlasSprite newSprite) {
            this.template = template;
            this.generalQuads = generalQuads;
            this.faceQuads = faceQuads;
            this.particleTexture = particleTexture;
            this.classicEgg = classicEgg;
            this.newSprite = newSprite;
        }

        @Override
        public List<BakedQuad> getFaceQuads(EnumFacing face) {
            return faceQuads.get(face.ordinal());
        }

        @Override
        public List<BakedQuad> getGeneralQuads() {
            return generalQuads;
        }

        @Override
        public boolean isAmbientOcclusion() {
            return template.isAmbientOcclusion();
        }

        @Override
        public boolean isGui3d() {
            return template.isGui3d();
        }

        @Override
        public boolean isBuiltInRenderer() {
            return false;
        }

        @Override
        public TextureAtlasSprite getParticleTexture() {
            return particleTexture;
        }

        @Override
        public ItemCameraTransforms getItemCameraTransforms() {
            return template.getItemCameraTransforms();
        }

        @Override
        public VertexFormat getFormat() {
            return template instanceof IFlexibleBakedModel
                    ? ((IFlexibleBakedModel) template).getFormat()
                    : DefaultVertexFormats.ITEM;
        }

        @Override
        public Pair<? extends IFlexibleBakedModel, Matrix4f> handlePerspective(
                ItemCameraTransforms.TransformType transformType) {
            if (template instanceof IPerspectiveAwareModel) {
                Pair<? extends IFlexibleBakedModel, Matrix4f> perspective =
                        ((IPerspectiveAwareModel) template).handlePerspective(transformType);
                IBakedModel perspectiveTemplate = perspective.getLeft();
                IBakedModel model = perspectiveTemplate == template
                        ? this
                        : (classicEgg != null
                        ? createClassicModel(perspectiveTemplate, classicEgg)
                        : createNewModel(perspectiveTemplate, newSprite));
                return Pair.of((IFlexibleBakedModel) model, perspective.getRight());
            }
            return IPerspectiveAwareModel.MapWrapper.handlePerspective(this,
                    IPerspectiveAwareModel.MapWrapper.getTransforms(template.getItemCameraTransforms()),
                    transformType);
        }
    }
}
