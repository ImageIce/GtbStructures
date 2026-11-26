package com.gtbstructures.scoreboard;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.IChatComponent;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;

import java.nio.FloatBuffer;

/** Uses the same SHOW_TEXT rendering path as a hovered chat component. */
final class NativeChatTooltip extends GuiScreen {
    void render(Minecraft client, ScaledResolution resolution, String text, final int x, final int y) {
        setWorldAndResolution(client, resolution.getScaledWidth(), resolution.getScaledHeight());
        final IChatComponent caption = new ChatComponentText("").setChatStyle(new ChatStyle()
                .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new ChatComponentText("\u00a7f" + text))));
        withRenderState(new Runnable() {
            @Override
            public void run() {
                handleComponentHover(caption, x, y);
            }
        });
    }

    static void withRenderState(Runnable draw) {
        RenderState previous = new RenderState();
        // Native hover rendering also changes light parameters, which are not cached by Minecraft.
        GL11.glPushAttrib(GL11.GL_LIGHTING_BIT);
        try {
            GlStateManager.enableTexture2D();
            GlStateManager.enableAlpha();
            GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE, GL11.GL_ZERO);
            GlStateManager.disableDepth();
            // Raw-GL callers can leave the cache stale: the z=300 box would hide z=0 font glyphs.
            // Keep the cache disabled above and force the actual driver state to match it.
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GlStateManager.color(1F, 1F, 1F, 1F);
            draw.run();
        } finally {
            previous.restore();
            GL11.glPopAttrib();
        }
    }

    private static final class RenderState {
        private final int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        private final boolean alpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        private final int alphaFunction = GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC);
        private final float alphaReference = GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF);
        private final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        private final int sourceRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        private final int destinationRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        private final int sourceAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        private final int destinationAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        private final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        private final boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        private final boolean light0 = GL11.glIsEnabled(GL11.GL_LIGHT0);
        private final boolean light1 = GL11.glIsEnabled(GL11.GL_LIGHT1);
        private final boolean colorMaterial = GL11.glIsEnabled(GL11.GL_COLOR_MATERIAL);
        private final int colorMaterialFace = GL11.glGetInteger(GL11.GL_COLOR_MATERIAL_FACE);
        private final int colorMaterialParameter = GL11.glGetInteger(GL11.GL_COLOR_MATERIAL_PARAMETER);
        private final boolean rescaleNormal = GL11.glIsEnabled(GL12_RESCALE_NORMAL);
        // LWJGL 2 requires sixteen slots for glGetFloatv even when querying an RGBA vector.
        private final FloatBuffer color = BufferUtils.createFloatBuffer(16);
        private final boolean texture;
        private final int textureBinding;

        private static final int GL12_RESCALE_NORMAL = 32826;

        RenderState() {
            GL11.glGetFloat(GL11.GL_CURRENT_COLOR, color);
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
            texture = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
            textureBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        }

        void restore() {
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GlStateManager.bindTexture(textureBinding);
            if (texture) GlStateManager.enableTexture2D(); else GlStateManager.disableTexture2D();
            GlStateManager.alphaFunc(alphaFunction, alphaReference);
            if (alpha) GlStateManager.enableAlpha(); else GlStateManager.disableAlpha();
            GlStateManager.tryBlendFuncSeparate(sourceRgb, destinationRgb, sourceAlpha, destinationAlpha);
            if (blend) GlStateManager.enableBlend(); else GlStateManager.disableBlend();
            if (depth) GlStateManager.enableDepth(); else GlStateManager.disableDepth();
            if (lighting) GlStateManager.enableLighting(); else GlStateManager.disableLighting();
            if (light0) GlStateManager.enableLight(0); else GlStateManager.disableLight(0);
            if (light1) GlStateManager.enableLight(1); else GlStateManager.disableLight(1);
            GlStateManager.colorMaterial(colorMaterialFace, colorMaterialParameter);
            if (colorMaterial) GlStateManager.enableColorMaterial(); else GlStateManager.disableColorMaterial();
            if (rescaleNormal) GlStateManager.enableRescaleNormal(); else GlStateManager.disableRescaleNormal();
            GlStateManager.color(color.get(0), color.get(1), color.get(2), color.get(3));
            GlStateManager.setActiveTexture(activeTexture);
        }
    }
}
