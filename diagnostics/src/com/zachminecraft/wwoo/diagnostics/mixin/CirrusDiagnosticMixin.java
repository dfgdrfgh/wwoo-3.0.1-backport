package com.zachminecraft.wwoo.diagnostics.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.zachminecraft.wwoo.diagnostics.CloudDiagnostic;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="com.jvn.cirrus.client.CirrusCloudRenderer",remap=false)
public abstract class CirrusDiagnosticMixin {
    @Inject(method="render",at=@At("HEAD"),remap=false)
    private void wwoo_diag$begin(ClientLevel level,PoseStack poses,Matrix4f frustum,Matrix4f projection,
            float partial,int ticks,double x,double y,double z,boolean depthOnly,CallbackInfo ci) {
        CloudDiagnostic.beginFrame(level,partial,depthOnly);
    }
    @Inject(method="render",at=@At("RETURN"),remap=false)
    private void wwoo_diag$finish(CallbackInfo ci) {CloudDiagnostic.finishFrame();}

    @Inject(method="drawLayer",at=@At("HEAD"),remap=false)
    private void wwoo_diag$layer(@Coerce Object mesh,@Coerce Object definition,PoseStack poses,Matrix4f frustum,Matrix4f projection,
            double height,double x,double z,double wind,Vec3 cloud,Vector3f sunWorld,float sunWeight,
            Vector3f sunView,Vector3f moonView,float rain,float thunder,@Coerce Object lightning,@Coerce Object texture,
            long frame,boolean shaderPack,boolean depthOnly,CallbackInfo ci) {CloudDiagnostic.layer(definition);}

    // Only the visible color pass. Fancy depth prepasses and the debug depth pass stay untouched.
    @WrapOperation(method="drawLayer",at=@At(value="INVOKE",ordinal=1,
        target="Lnet/minecraft/class_291;method_34427(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lnet/minecraft/class_5944;)V",remap=false),remap=false)
    private void wwoo_diag$measureCloudDraw(VertexBuffer buffer,Matrix4f modelView,Matrix4f projection,
            ShaderInstance shader,Operation<Void> original) {
        CloudDiagnostic.draw(shader,()->original.call(buffer,modelView,projection,shader),
            ()->{buffer.bind();buffer.drawWithShader(modelView,projection,shader);});
    }
}
