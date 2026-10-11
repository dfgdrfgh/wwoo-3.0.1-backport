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
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MultiNoiseBiomeSource.class)
public abstract class IndividualBiomeSizeMixin {
    // Atomically publish config and index for parallel chunk workers.
    @Unique private volatile State wwoo$state;
    public record State(BiomeSizeConfig config, Climate.ParameterList<Holder<Biome>> parameters,
            BiomeScaleIndex index) {}

    @Invoker(value = "method_49506", remap = false)
    public abstract Climate.ParameterList<Holder<Biome>> wwoo$parameters();

    // Only coordinate-aware lookup is wrapped. Original point-lookup hooks
    // remain in the operation chain; climate-only lookups are unchanged.
    @WrapOperation(method = "method_38109", remap = false,
        at = @At(value = "INVOKE", remap = false,
            target = "Lnet/minecraft/class_4766;method_38167(Lnet/minecraft/class_6544$class_6553;)Lnet/minecraft/class_6880;"))
    private Holder<Biome> wwoo$individualSize(MultiNoiseBiomeSource source,
            Climate.TargetPoint point, Operation<Holder<Biome>> original,
            int x, int y, int z, Climate.Sampler sampler) {
        var vanilla = original.call(source, point);
        if (!BiomeScaling.isPackActive()) return vanilla;
        var config = ConfigRegistry.get(BiomeSizeConfig.class);
        var parameters = wwoo$parameters();
        var state = wwoo$state;
        if (state == null || state.config() != config || state.parameters() != parameters) {
            synchronized (this) {
                state = wwoo$state;
                if (state == null || state.config() != config || state.parameters() != parameters) {
                    state = new State(config, parameters, BiomeScaleIndex.create(parameters, config));
                    wwoo$state = state;
                }
            }
        }
        return state.index() == null ? vanilla : state.index().find(x, y, z, sampler, point, vanilla,
            sampled -> original.call(source, sampled));
    }
}

