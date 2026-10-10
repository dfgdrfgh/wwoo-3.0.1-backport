package com.zachminecraft.wwoocontent.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.zachminecraft.wwoocontent.BiomeScaling;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RandomState.class)
public abstract class BiomeSizeMixin {
    // This project's helpers are remapped without an annotation processor.
    // Use the 1.21.1 intermediary selector, as the existing mixins do.
    @ModifyExpressionValue(method = "<init>", remap = false, at = @At(value = "INVOKE", remap = false,
        target = "Lnet/minecraft/class_5284;comp_477()Lnet/minecraft/class_6953;"))
    private NoiseRouter wwoo$scaleBiomes(NoiseRouter original) {
        return BiomeScaling.configuredRouter(original);
    }
}
