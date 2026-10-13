package com.zachminecraft.wwoocontent;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
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
        try {
            var register=Class.forName("net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry")
                .getMethod("register",EntityType.class,AttributeSupplier.Builder.class);
            register.invoke(null,creaking,Pig.createAttributes());
            register.invoke(null,modded,Pig.createAttributes());
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("Test attribute registration failed",e); }
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
    private static void naturalInsertions(ServerLevel server, BlockPos pos, boolean restricted) {
        EntityType<?>[] types = {EntityType.PIG, EntityType.ZOMBIE, EntityType.CAT, EntityType.WANDERING_TRADER, modded};
        for (EntityType<?> type : types) {
            @SuppressWarnings("unchecked") var mobType = (EntityType<? extends Mob>)type;
            for (var reason : new MobSpawnType[]{MobSpawnType.NATURAL,MobSpawnType.CHUNK_GENERATION,MobSpawnType.PATROL,MobSpawnType.EVENT}) {
                var entity = mob(mobType,server,pos,reason);
                check(server.addFreshEntity(entity) == !restricted, "insertion " + type + " " + reason + " at " + pos);
                if (restricted) check(!server.addFreshEntity(entity), "rejected mob cannot bypass rule on retry");
                else check(!((PaleGardenSpawning.PendingNaturalSpawn)entity).wwoo$isNaturalSpawn(), "flag consumed after successful insertion");
                entity.discard();
            }
        }
    }
    private static final class Probe extends Feature<NoneFeatureConfiguration> {
        Probe() { super(NoneFeatureConfiguration.CODEC); }
        public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> c) {
            var level = c.level(); var pos = c.origin();
            boolean pale = level.getBiome(pos).unwrapKey().orElseThrow().location().toString().equals("minecraft:pale_garden");
            if (level instanceof WorldGenRegion) {
                var surfacePos=new BlockPos(pos.getX(),level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG,pos.getX(),pos.getZ()),pos.getZ());
                var underground=surfacePos.below(12);
                check(PaleGardenSpawning.surface(level,surfacePos), "worldgen surface detected");
                check(!PaleGardenSpawning.surface(level,underground), "worldgen underground detected");
                check(level.addFreshEntity(mob(modded,level,surfacePos,MobSpawnType.CHUNK_GENERATION)) == !pale, "worldgen surface animal restriction");
                check(level.addFreshEntity(mob(modded,level,underground,MobSpawnType.CHUNK_GENERATION)), "worldgen underground animal allowed");
                check(level.addFreshEntity(mob(creaking,level,surfacePos,MobSpawnType.CHUNK_GENERATION)), "worldgen creaking allowed");
                if (pale) System.out.println("WWOO_SPAWN_WORLDGEN_PASS");
                return true;
            }
            var server = (ServerLevel)level;
            server.setBlock(pos.below(),Blocks.GRASS_BLOCK.defaultBlockState(),3);
            server.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
            server.setBlock(pos.above(),Blocks.AIR.defaultBlockState(),3);
            check(SpawnPlacements.checkSpawnRules(EntityType.PIG,server,MobSpawnType.NATURAL,pos,c.random()) == !pale, "placement biome rule");
            check(SpawnPlacements.checkSpawnRules(EntityType.PIG,server,MobSpawnType.COMMAND,pos,c.random()), "command placement allowed");
            naturalInsertions(server,pos,pale);
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
            if (pale) {
                // Leaf cover and a log branch must not turn the forest floor into a cave.
                server.setBlock(pos.above(4),Blocks.OAK_LOG.defaultBlockState(),3);
                server.setBlock(pos.above(5),Blocks.OAK_LEAVES.defaultBlockState(),3);
                check(PaleGardenSpawning.blocked(server,pos,modded), "canopy surface still restricted");
                naturalInsertions(server,pos,true);
                server.setBlock(pos.above(4),Blocks.AIR.defaultBlockState(),3);
                server.setBlock(pos.above(5),Blocks.AIR.defaultBlockState(),3);
                System.out.println("WWOO_SPAWN_CANOPY_PASS");

                var underground=pos.below(12);
                for (int dx=-1;dx<=1;dx++) for (int dz=-1;dz<=1;dz++) {
                    server.setBlock(underground.offset(dx,-1,dz),Blocks.GRASS_BLOCK.defaultBlockState(),3);
                    server.setBlock(underground.offset(dx,0,dz),Blocks.AIR.defaultBlockState(),3);
                    server.setBlock(underground.offset(dx,1,dz),Blocks.AIR.defaultBlockState(),3);
                    server.setBlock(underground.offset(dx,3,dz),Blocks.STONE.defaultBlockState(),3);
                }
                check(!PaleGardenSpawning.blocked(server,underground,modded), "underground policy allowed");
                check(SpawnPlacements.checkSpawnRules(EntityType.PIG,server,MobSpawnType.NATURAL,underground,c.random())
                    == SpawnPlacements.checkSpawnRules(EntityType.PIG,server,MobSpawnType.COMMAND,underground,c.random()), "underground placement retains normal conditions");
                naturalInsertions(server,underground,false);
                System.out.println("WWOO_SPAWN_UNDERGROUND_PASS");

                // Height varies per column; no sea-level/global-Y cutoff may be used.
                for (var slope : new BlockPos[]{new BlockPos(pos.getX()+4,140,pos.getZ()),new BlockPos(pos.getX()+8,100,pos.getZ())}) {
                    server.setBlock(slope.below(),Blocks.GRASS_BLOCK.defaultBlockState(),3);
                    check(PaleGardenSpawning.blocked(server,slope,modded), "local slope surface restricted");
                    check(!PaleGardenSpawning.blocked(server,slope.below(12),modded), "below local slope allowed");
                }
                System.out.println("WWOO_SPAWN_SLOPES_PASS");
                var slab=new BlockPos(pos.getX()+12,140,pos.getZ());
                server.setBlock(slab,Blocks.OAK_SLAB.defaultBlockState(),3);
                var onSlab=mob(modded,server,slab,MobSpawnType.NATURAL);
                onSlab.moveTo(slab.getX()+0.5,slab.getY()+0.5,slab.getZ()+0.5,0,0);
                check(!server.addFreshEntity(onSlab), "bottom slab surface restricted");onSlab.discard();
                System.out.println("WWOO_SPAWN_PARTIAL_PASS");
            }
            System.out.println("WWOO_SPAWN_" + (pale ? "PALE" : "PLAINS") + "_PASS");
            return true;
        }
    }
}
