package com.zachminecraft.wwoocontent.mixin;

import static com.zachminecraft.wwoocontent.PlantSupport.tagged;
import static com.zachminecraft.wwoocontent.PlantSupport.hasTag;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import java.util.OptionalInt;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 26.3's support tags are data-driven; 1.21.1 hardcodes several of these checks. */
public final class PlantSupportMixins {
    private PlantSupportMixins() {}
    @Mixin(BushBlock.class)
    public abstract static class Vegetation {
        @Inject(method="mayPlaceOn",at=@At("HEAD"),cancellable=true)
        private void wwoo$support(BlockState ground,BlockGetter level,BlockPos pos,CallbackInfoReturnable<Boolean> ci) {
            if (!hasTag("supports_vegetation")) return;
            ci.setReturnValue(tagged(ground,"supports_vegetation"));
        }
    }
    @Mixin({CropBlock.class,StemBlock.class,AttachedStemBlock.class})
    public abstract static class Crops {
        @Inject(method="mayPlaceOn",at=@At("HEAD"),cancellable=true)
        private void wwoo$support(BlockState ground,BlockGetter level,BlockPos pos,CallbackInfoReturnable<Boolean> ci) {
            String tag=(Object)this instanceof CropBlock ? "supports_crops" : "supports_stem_crops";
            if (!hasTag(tag)) return;
            ci.setReturnValue(tagged(ground,tag));
        }
    }
    @Mixin(CocoaBlock.class)
    public abstract static class Cocoa {
        @Inject(method="canSurvive",at=@At("HEAD"),cancellable=true)
        private void wwoo$support(BlockState state,LevelReader level,BlockPos pos,CallbackInfoReturnable<Boolean> ci) {
            if (!hasTag("supports_cocoa")) return;
            ci.setReturnValue(tagged(level.getBlockState(pos.relative(state.getValue(CocoaBlock.FACING))),"supports_cocoa"));
        }
    }
    @Mixin(CactusBlock.class)
    public abstract static class Cactus {
        @Inject(method="canSurvive",at=@At("HEAD"),cancellable=true)
        private void wwoo$support(BlockState state,LevelReader level,BlockPos pos,CallbackInfoReturnable<Boolean> ci) {
            if (!hasTag("supports_cactus")) return;
            for(var direction:Direction.Plane.HORIZONTAL) {
                var neighbor=level.getBlockState(pos.relative(direction));
                if(neighbor.isSolid() || level.getFluidState(pos.relative(direction)).is(FluidTags.LAVA)) {ci.setReturnValue(false);return;}
            }
            var below=level.getBlockState(pos.below());
            ci.setReturnValue((below.is(Blocks.CACTUS) || tagged(below,"supports_cactus")) && !level.getBlockState(pos.above()).liquid());
        }
    }
    @Mixin(SugarCaneBlock.class)
    public abstract static class SugarCane {
        @Inject(method="canSurvive",at=@At("HEAD"),cancellable=true)
        private void wwoo$support(BlockState state,LevelReader level,BlockPos pos,CallbackInfoReturnable<Boolean> ci) {
            if (!hasTag("supports_sugar_cane") || !hasTag("supports_sugar_cane_adjacently")) return;
            var below=pos.below();var ground=level.getBlockState(below);
            if(ground.is(Blocks.SUGAR_CANE)) {ci.setReturnValue(true);return;}
            if(tagged(ground,"supports_sugar_cane")) {
                for(var direction:Direction.Plane.HORIZONTAL) {
                    var adjacent=below.relative(direction);
                    if(level.getFluidState(adjacent).is(FluidTags.WATER) || tagged(level.getBlockState(adjacent),"supports_sugar_cane_adjacently")) {ci.setReturnValue(true);return;}
                }
            }
            ci.setReturnValue(false);
        }
    }
    @Mixin(LeavesBlock.class)
    public abstract static class LeafSupport {
        @Inject(method="getOptionalDistanceAt",at=@At("HEAD"),cancellable=true)
        private static void wwoo$support(BlockState state,CallbackInfoReturnable<OptionalInt> ci) {
            if(tagged(state,"prevents_nearby_leaf_decay")) ci.setReturnValue(OptionalInt.of(0));
        }
    }
}
