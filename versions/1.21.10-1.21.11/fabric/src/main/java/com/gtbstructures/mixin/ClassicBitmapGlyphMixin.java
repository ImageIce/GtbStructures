package com.gtbstructures.mixin;

import com.gtbstructures.font.ClassicFontGlyphMetrics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.class_386$class_388", remap = false)
public abstract class ClassicBitmapGlyphMixin {
    @Shadow(remap = false)
    public abstract float comp_603();

    @Inject(
            method = "method_73331()Lnet/minecraft/class_379;",
            at = @At("RETURN"),
            cancellable = true,
            remap = false
    )
    private void gtbs$useClassicUnicodeStyleOffsets(CallbackInfoReturnable<Object> callback) {
        if (!ClassicFontGlyphMetrics.shouldAdjust(comp_603())) return;
        callback.setReturnValue(ClassicFontGlyphMetrics.withHalfPixelStyleOffsets(callback.getReturnValue()));
    }
}
