package com.zachminecraft.wwoocontent.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GrassBlock.class)
public abstract class BonemealMixin {
    @Inject(method="performBonemeal", at=@At("HEAD"), cancellable=true)
    private void wwoo$bonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state, CallbackInfo ci) {
        var registry=level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE);
        // Leave the older fill-in biomes' existing flower behavior intact.
        boolean modern=level.getBiome(pos).value().getGenerationSettings().features().stream()
            .flatMap(set -> set.stream()).flatMap(f -> f.value().getFeatures())
            .anyMatch(f -> {var id=registry.getKey(f);return id!=null && id.getNamespace().equals("wwoocontent");});
        if (!modern) return;
        var tag=TagKey.create(Registries.CONFIGURED_FEATURE,new ResourceLocation("can_spawn_from_bone_meal"));
        var allowed=registry.getTag(tag);
        var grassFeature=level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE)
            .getOptional(new ResourceLocation("grass_bonemeal"));
        BlockPos above=pos.above();
        outer: for(int attempt=0;attempt<128;attempt++) {
            BlockPos test=above;
            for(int i=0;i<attempt/16;i++) {
                test=test.offset(random.nextInt(3)-1,(random.nextInt(3)-1)*random.nextInt(3)/2,random.nextInt(3)-1);
                if (!level.getBlockState(test.below()).is((Block)(Object)this) || level.getBlockState(test).isCollisionShapeFullBlock(level,test)) continue outer;
            }
            var current=level.getBlockState(test);
            if (current.is(Blocks.GRASS) && random.nextFloat()<0.1F && Blocks.GRASS instanceof BonemealableBlock grow && grow.isValidBonemealTarget(level,test,current,false))
                grow.performBonemeal(level,random,test,current);
            if (!current.isAir() || level.isOutsideBuildHeight(test)) continue;
            if (random.nextFloat()<0.125F) {
                var flowers=level.getBiome(test).value().getGenerationSettings().features().stream()
                    .flatMap(set -> set.stream()).flatMap(f -> f.value().getFeatures())
                    .filter(f -> allowed.isPresent() && registry.wrapAsHolder(f).is(tag)).toList();
                if (!flowers.isEmpty()) flowers.get(random.nextInt(flowers.size())).place(level,level.getChunkSource().getGenerator(),random,test);
            } else if (grassFeature.isPresent()) grassFeature.get().place(level,level.getChunkSource().getGenerator(),random,test);
        }
        ci.cancel();
    }
}
