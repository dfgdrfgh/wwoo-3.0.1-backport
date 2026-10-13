package com.zachminecraft.wwoocontent.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.zachminecraft.wwoocontent.CloudAttributes;
import com.zachminecraft.wwoocontent.CloudBlend;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Apply biome RGB after Cirrus chooses its weather, dimension and time-of-day tint. */
@Pseudo
@Mixin(targets="com.jvn.cirrus.client.CirrusCloudRenderer", remap=false)
public abstract class CirrusCloudRendererMixin {
    @Unique private CloudBlend.Color wwoo$frameColor;

    @Inject(method="render", at=@At("HEAD"), remap=false)
    private void wwoo$sampleCloudColor(CallbackInfo ci) {
        wwoo$frameColor = CloudAttributes.color();
    }

    @ModifyExpressionValue(method="render", at=@At(value="INVOKE",
        target="Lcom/jvn/cirrus/client/CirrusCloudTint;color(Lnet/minecraft/class_638;FLnet/minecraft/class_243;)Lnet/minecraft/class_243;",
        remap=false), remap=false)
    private Vec3 wwoo$tintCirrusClouds(Vec3 original) {
        var color = wwoo$frameColor;
        // One result feeds every layer and pass, including Cirrus's fog-whitening path.
        return new Vec3(original.x * color.red(), original.y * color.green(), original.z * color.blue());
    }
}
