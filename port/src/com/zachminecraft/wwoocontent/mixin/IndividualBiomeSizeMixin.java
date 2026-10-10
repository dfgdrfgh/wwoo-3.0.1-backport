package com.zachminecraft.wwoocontent.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.zachminecraft.wwoocontent.BiomeScaleIndex;
import com.zachminecraft.wwoocontent.BiomeScaling;
import com.zachminecraft.wwoocontent.BiomeSizeConfig;
import de.cristelknight.cristellib.config.simple.ConfigRegistry;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MultiNoiseBiomeSource.class)
public abstract class IndividualBiomeSizeMixin {
    @Unique private volatile BiomeSizeConfig wwoo$config;
    @Unique private volatile BiomeScaleIndex wwoo$index;

    // Inline selectors are intermediary because these helpers use no AP/refmap.
    @WrapOperation(method = "method_38167", remap = false,
        at = @At(value = "INVOKE", remap = false,
            target = "Lnet/minecraft/class_6544$class_6547;method_39529(Lnet/minecraft/class_6544$class_6553;)Ljava/lang/Object;"))
    private Object wwoo$individualSize(Climate.ParameterList<Holder<Biome>> parameters,
            Climate.TargetPoint point, Operation<Object> original) {
        @SuppressWarnings("unchecked")
        var vanilla = (Holder<Biome>) original.call(parameters, point);
        if (!BiomeScaling.isPackActive()) return vanilla;
        var config = ConfigRegistry.get(BiomeSizeConfig.class);
        if (wwoo$config != config) {
            synchronized (this) {
                if (wwoo$config != config) {
                    wwoo$index = BiomeScaleIndex.create(parameters, config);
                    wwoo$config = config;
                }
            }
        }
        var index = wwoo$index;
        return index == null ? vanilla : index.find(point, vanilla);
    }
}
