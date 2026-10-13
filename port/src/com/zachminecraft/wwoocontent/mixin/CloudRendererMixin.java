package com.zachminecraft.wwoocontent.mixin;

import com.zachminecraft.wwoocontent.CloudAttributes;
import com.zachminecraft.wwoocontent.CloudBlend;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class CloudRendererMixin {
    @Unique private CloudBlend.Color wwoo$frameColor;
    @Inject(method="method_3259", at=@At("HEAD"), remap=false)
    private void wwoo$sampleCloudColor(CallbackInfo ci) {
        // Both fancy-cloud passes use the same instant in the transition.
        wwoo$frameColor = CloudAttributes.color();
    }

    @WrapOperation(method="method_3259", at=@At(value="INVOKE",
        target="Lnet/minecraft/class_291;method_34427(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lnet/minecraft/class_5944;)V",
        remap=false), remap=false)
    private void wwoo$tintCloudDraw(VertexBuffer buffer, Matrix4f modelView, Matrix4f projection,
                                    ShaderInstance shader, Operation<Void> original) {
        // Snapshot the shared shader-color array into locals before changing it.
        // Tint the original operation directly, without a helper or capturing callback.
        float[] state = RenderSystem.getShaderColor();
        float red = state[0], green = state[1], blue = state[2], alpha = state[3];
        var color = wwoo$frameColor;
        RenderSystem.setShaderColor(red * (float)color.red(), green * (float)color.green(),
            blue * (float)color.blue(), alpha);
        try {
            original.call(buffer, modelView, projection, shader);
        } finally {
            RenderSystem.setShaderColor(red, green, blue, alpha);
        }
    }
}

