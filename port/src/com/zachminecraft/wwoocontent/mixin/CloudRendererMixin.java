package com.zachminecraft.wwoocontent.mixin;

import com.zachminecraft.wwoocontent.CloudAttributes;
import com.zachminecraft.wwoocontent.CloudBlend;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class CloudRendererMixin {
    @Unique private CloudBlend.Color wwoo$previousColor;
    @Inject(method="method_3259", at=@At("HEAD"), remap=false)
    private void wwoo$refreshCloudColor(CallbackInfo ci) {
        var color = CloudAttributes.color();
        if (!color.equals(wwoo$previousColor)) {
            ((CloudRendererAccess)this).wwoo$regenerateClouds(true);
            wwoo$previousColor = color;
        }
    }
    @ModifyArg(method="method_3239", at=@At(value="INVOKE",target="Lnet/minecraft/class_4588;method_22915(FFFF)Lnet/minecraft/class_4588;",remap=false), index=3, remap=false)
    private float wwoo$cloudAlpha(float alpha) { return CloudAttributes.alpha(); }
}

