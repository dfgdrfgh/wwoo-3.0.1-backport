package com.zachminecraft.wwoocontent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;

/** A biome-local spawn restriction, not an entity removal or movement rule. */
public final class PaleGardenSpawning {
    private static final ResourceKey<Biome> PALE_GARDEN = ResourceKey.create(
        Registries.BIOME, new ResourceLocation("minecraft:pale_garden"));
    private static final ResourceLocation CREAKING = new ResourceLocation("minecraft:creaking");
    private static final ResourceLocation PHANTOM = new ResourceLocation("minecraft:phantom");

    private PaleGardenSpawning() {}

    public static boolean natural(MobSpawnType reason) {
        return reason == MobSpawnType.NATURAL || reason == MobSpawnType.CHUNK_GENERATION
            || reason == MobSpawnType.PATROL || reason == MobSpawnType.EVENT;
    }

    public static boolean blocked(LevelReader level, BlockPos pos, EntityType<?> type) {
        // The backport supplies the creaking; 1.21.1 has no EntityType.CREAKING constant.
        var id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return !CREAKING.equals(id) && !PHANTOM.equals(id)
            && level.getBiome(pos).is(PALE_GARDEN) && surface(level, pos);
    }

    public static boolean surface(LevelReader level, BlockPos pos) {
        // Use the cached terrain height in the current generation region or loaded world.
        // Ocean-floor height ignores fluids; trees must also be skipped to reach the ground.
        var heightmap = level instanceof WorldGenRegion ? Heightmap.Types.OCEAN_FLOOR_WG : Heightmap.Types.OCEAN_FLOOR;
        int top = level.getHeight(heightmap, pos.getX(), pos.getZ()) - 1;
        var cursor = new BlockPos.MutableBlockPos(pos.getX(), top, pos.getZ());
        for (int y = top; y >= level.getMinBuildHeight(); y--) {
            cursor.setY(y);
            var state = level.getBlockState(cursor);
            if (state.blocksMotion() && !state.is(BlockTags.LEAVES) && !state.is(BlockTags.LOGS)) {
                // Include partial surface blocks: feet on a bottom slab share its block Y.
                return pos.getY() >= y;
            }
        }
        return true;
    }

    public static boolean rejectFreshMob(LevelReader level, Entity entity) {
        if (!(entity instanceof PendingNaturalSpawn pending) || !pending.wwoo$isNaturalSpawn()) return false;
        return blocked(level, entity.blockPosition(), entity.getType());
    }

    public interface PendingNaturalSpawn {
        boolean wwoo$isNaturalSpawn();
        /** Consume the transient flag, so an existing mob can later move freely. */
        boolean wwoo$takeNaturalSpawn();
    }
}
