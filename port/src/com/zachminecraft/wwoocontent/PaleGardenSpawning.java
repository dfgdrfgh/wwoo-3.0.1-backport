package com.zachminecraft.wwoocontent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;

/** A biome-local spawn restriction, not an entity removal or movement rule. */
public final class PaleGardenSpawning {
    private static final ResourceKey<Biome> PALE_GARDEN = ResourceKey.create(
        Registries.BIOME, ResourceLocation.parse("minecraft:pale_garden"));
    private static final ResourceLocation CREAKING = ResourceLocation.parse("minecraft:creaking");
    private static final ResourceLocation PHANTOM = ResourceLocation.parse("minecraft:phantom");

    private PaleGardenSpawning() {}

    public static boolean natural(MobSpawnType reason) {
        return reason == MobSpawnType.NATURAL || reason == MobSpawnType.CHUNK_GENERATION
            || reason == MobSpawnType.PATROL || reason == MobSpawnType.EVENT;
    }

    public static boolean blocked(LevelReader level, BlockPos pos, EntityType<?> type) {
        // The backport supplies the creaking; 1.21.1 has no EntityType.CREAKING constant.
        var id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return !CREAKING.equals(id) && !PHANTOM.equals(id)
            && level.getBiome(pos).is(PALE_GARDEN);
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
