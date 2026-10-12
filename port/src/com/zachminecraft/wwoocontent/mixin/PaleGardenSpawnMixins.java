package com.zachminecraft.wwoocontent.mixin;

import com.zachminecraft.wwoocontent.PaleGardenSpawning;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

public final class PaleGardenSpawnMixins {
    @Mixin(SpawnPlacements.class)
    public abstract static class Placement {
        @Inject(method = "method_20638", at = @At("HEAD"), cancellable = true, remap = false)
        private static void wwoo$checkPlacement(EntityType<?> type, ServerLevelAccessor level,
                MobSpawnType reason, BlockPos pos, RandomSource random, CallbackInfoReturnable<Boolean> cir) {
            if (PaleGardenSpawning.natural(reason) && PaleGardenSpawning.blocked(level, pos, type)) cir.setReturnValue(false);
        }
    }

    @Mixin(Mob.class)
    public abstract static class SpawnReason implements PaleGardenSpawning.PendingNaturalSpawn {
        @Unique private boolean wwoo$pendingNaturalSpawn;

        @Inject(method = "method_5943", at = @At("HEAD"), remap = false)
        private void wwoo$rememberReason(ServerLevelAccessor level, DifficultyInstance difficulty,
                MobSpawnType reason, SpawnGroupData group, CallbackInfoReturnable<SpawnGroupData> cir) {
            wwoo$pendingNaturalSpawn = PaleGardenSpawning.natural(reason);
        }

        @Override public boolean wwoo$takeNaturalSpawn() {
            boolean pending = wwoo$pendingNaturalSpawn;
            wwoo$pendingNaturalSpawn = false;
            return pending;
        }

        @Override public boolean wwoo$isNaturalSpawn() { return wwoo$pendingNaturalSpawn; }
    }

    @Mixin(ServerLevel.class)
    public abstract static class ServerInsertion {
        // All fresh server insertion paths converge here, including phantoms and event spawners.
        @Inject(method = "method_14175", at = @At("HEAD"), cancellable = true, remap = false)
        private void wwoo$rejectNaturalMob(Entity entity, CallbackInfoReturnable<Boolean> cir) {
            if (PaleGardenSpawning.rejectFreshMob((ServerLevel)(Object)this, entity)) cir.setReturnValue(false);
        }

        @Inject(method = "method_14175", at = @At("RETURN"), remap = false)
        private void wwoo$finishInsertion(Entity entity, CallbackInfoReturnable<Boolean> cir) {
            if (cir.getReturnValue() && entity instanceof PaleGardenSpawning.PendingNaturalSpawn pending) pending.wwoo$takeNaturalSpawn();
        }
    }

    @Mixin(WorldGenRegion.class)
    public abstract static class GenerationInsertion {
        // Query the generation region's available biome data, never the backing ServerLevel.
        @Inject(method = "method_8649", at = @At("HEAD"), cancellable = true, remap = false)
        private void wwoo$rejectGenerationMob(Entity entity, CallbackInfoReturnable<Boolean> cir) {
            if (PaleGardenSpawning.rejectFreshMob((WorldGenRegion)(Object)this, entity)) cir.setReturnValue(false);
        }

        @Inject(method = "method_8649", at = @At("RETURN"), remap = false)
        private void wwoo$finishGenerationInsertion(Entity entity, CallbackInfoReturnable<Boolean> cir) {
            if (cir.getReturnValue() && entity instanceof PaleGardenSpawning.PendingNaturalSpawn pending) pending.wwoo$takeNaturalSpawn();
        }
    }
}
