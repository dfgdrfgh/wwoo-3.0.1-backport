package com.zachminecraft.wwoocontent;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.*;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Isolated runtime fixture: the creaking ID tests the whitelist, not heart mechanics. */
public final class SpawnProbe implements ModInitializer {
    private static EntityType<Pig> creaking, modded;
    public void onInitialize() {
        creaking = Registry.register(BuiltInRegistries.ENTITY_TYPE, ResourceLocation.parse("minecraft:creaking"),
            EntityType.Builder.of(Pig::new, MobCategory.CREATURE).build("minecraft:creaking"));
        modded = Registry.register(BuiltInRegistries.ENTITY_TYPE, ResourceLocation.parse("audit:animal"),
            EntityType.Builder.of(Pig::new, MobCategory.CREATURE).build("audit:animal"));
        Registry.register(BuiltInRegistries.FEATURE, ResourceLocation.parse("audit:spawn_probe"), new Probe());
    }
    private static void check(boolean value, String message) {
        if (!value) throw new IllegalStateException("WWOO_SPAWN_FAIL: " + message);
    }
    private static Mob mob(EntityType<? extends Mob> type, ServerLevelAccessor level, BlockPos pos, MobSpawnType reason) {
        Mob mob = type.create(level.getLevel());
        check(mob != null, "create " + type);
        mob.moveTo(pos.getX()+0.5,pos.getY(),pos.getZ()+0.5,0,0);
        mob.finalizeSpawn(level,level.getCurrentDifficultyAt(pos),reason,null);
        return mob;
    }
    private static final class Probe extends Feature<NoneFeatureConfiguration> {
        Probe() { super(NoneFeatureConfiguration.CODEC); }
        public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> c) {
            var level = c.level(); var pos = c.origin();
            boolean pale = level.getBiome(pos).unwrapKey().orElseThrow().location().toString().equals("minecraft:pale_garden");
            if (level instanceof WorldGenRegion) {
                check(!pale || !level.addFreshEntity(mob(modded,level,pos,MobSpawnType.CHUNK_GENERATION)), "worldgen animal rejected");
                check(level.addFreshEntity(mob(creaking,level,pos,MobSpawnType.CHUNK_GENERATION)), "worldgen creaking allowed");
                if (pale) System.out.println("WWOO_SPAWN_WORLDGEN_PASS");
                return true;
            }
            var server = (ServerLevel)level;
            server.setBlock(pos.below(),Blocks.GRASS_BLOCK.defaultBlockState(),3);
            server.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
            server.setBlock(pos.above(),Blocks.AIR.defaultBlockState(),3);
            check(SpawnPlacements.checkSpawnRules(EntityType.PIG,server,MobSpawnType.NATURAL,pos,c.random()) == !pale, "placement biome rule");
            check(SpawnPlacements.checkSpawnRules(EntityType.PIG,server,MobSpawnType.COMMAND,pos,c.random()), "command placement allowed");
            EntityType<?>[] types = {EntityType.PIG, EntityType.ZOMBIE, EntityType.CAT, EntityType.WANDERING_TRADER, modded};
            for (EntityType<?> type : types) {
                @SuppressWarnings("unchecked") var mobType = (EntityType<? extends Mob>)type;
                for (var reason : new MobSpawnType[]{MobSpawnType.NATURAL,MobSpawnType.CHUNK_GENERATION,MobSpawnType.PATROL,MobSpawnType.EVENT}) {
                    var entity = mob(mobType,server,pos,reason);
                    check(server.addFreshEntity(entity) == !pale, "insertion " + type + " " + reason);
                    if (pale) check(!server.addFreshEntity(entity), "rejected mob cannot bypass rule on retry");
                    else check(!((PaleGardenSpawning.PendingNaturalSpawn)entity).wwoo$isNaturalSpawn(), "flag consumed after successful insertion");
                    entity.discard();
                }
            }
            for (var reason : new MobSpawnType[]{MobSpawnType.COMMAND,MobSpawnType.SPAWN_EGG,MobSpawnType.SPAWNER,MobSpawnType.TRIAL_SPAWNER,MobSpawnType.BREEDING,MobSpawnType.BUCKET,MobSpawnType.CONVERSION}) {
                var entity=mob(modded,server,pos,reason);
                check(server.addFreshEntity(entity), "manual/existing spawn " + reason); entity.discard();
            }
            for (var reason : new MobSpawnType[]{MobSpawnType.NATURAL,MobSpawnType.CHUNK_GENERATION,MobSpawnType.TRIGGERED}) {
                for (var allowed : new EntityType<?>[]{creaking,EntityType.PHANTOM}) {
                    @SuppressWarnings("unchecked") var allowedType=(EntityType<? extends Mob>)allowed;
                    var entity=mob(allowedType,server,pos,reason);
                    check(server.addFreshEntity(entity), "allowed exception " + allowed + " " + reason); entity.discard();
                }
            }
            var loaded = modded.create(server);
            loaded.moveTo(pos.getX()+0.5,pos.getY(),pos.getZ()+0.5,0,0);
            check(server.addFreshEntity(loaded), "unmarked existing entity allowed"); loaded.discard();
            System.out.println("WWOO_SPAWN_" + (pale ? "PALE" : "PLAINS") + "_PASS");
            return true;
        }
    }
}
