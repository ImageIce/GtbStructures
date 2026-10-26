package com.gtbstructures.mixin;

import com.gtbstructures.font.ClassicFontGlyphMetrics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies the classic-font bearing fix to the unobfuscated 26.x glyph type. */
@Mixin(targets = "net.minecraft.client.gui.font.providers.BitmapProvider$Glyph", remap = false)
public abstract class ClassicBitmapGlyph26Mixin {
    @Shadow(remap = false)
    public abstract float scale();

    @Inject(
            method = "info()Lcom/mojang/blaze3d/font/GlyphInfo;",
            at = @At("RETURN"),
            cancellable = true,
            remap = false
    )
    private void gtbs$useClassicUnicodeStyleOffsets(CallbackInfoReturnable<Object> callback) {
        if (!ClassicFontGlyphMetrics.shouldAdjust(scale())) return;
        callback.setReturnValue(ClassicFontGlyphMetrics.withHalfPixelStyleOffsets(callback.getReturnValue()));
    }
}
