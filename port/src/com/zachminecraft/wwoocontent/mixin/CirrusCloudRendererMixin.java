package com.zachminecraft.wwoocontent.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.zachminecraft.wwoocontent.CloudAttributes;
import com.zachminecraft.wwoocontent.CloudBlend;
import com.zachminecraft.wwoocontent.CloudOpacity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Apply biome RGB after Cirrus chooses its weather, dimension and time-of-day tint. */
@Pseudo
@Mixin(targets="com.jvn.cirrus.client.CirrusCloudRenderer", remap=false)
public abstract class CirrusCloudRendererMixin {
    @Unique private CloudBlend.Color wwoo$frameColor;
    @Unique private boolean wwoo$fastOpacity;

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

    @Inject(method="drawLayer",at=@At("HEAD"),remap=false)
    private void wwoo$cirrusLayerMode(@Coerce Object mesh,@Coerce Object definition,PoseStack poses,Matrix4f frustum,Matrix4f projection,
            double height,double x,double z,double wind,Vec3 cloud,Vector3f sunWorld,float sunWeight,
            Vector3f sunView,Vector3f moonView,float rain,float thunder,@Coerce Object lightning,@Coerce Object texture,
            long frame,boolean shaderPack,boolean depthOnly,CallbackInfo ci) {
        wwoo$fastOpacity=CloudOpacity.fast(definition);
    }

    @ModifyExpressionValue(method="drawLayer",at=@At(value="INVOKE",
        target="Lcom/jvn/cirrus/client/CirrusCloudRenderer;cloudShader(Z)Lnet/minecraft/class_5944;",remap=false),remap=false)
    private ShaderInstance wwoo$cirrusOpacityMode(ShaderInstance shader) {
        var uniform=shader.getUniform(CloudOpacity.UNIFORM);
        if(uniform!=null)uniform.set(wwoo$fastOpacity?1.0f:0.0f);
        return shader;
    }
}
