package com.zachminecraft.wwoocontent;

import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;

/** Runtime helper lives outside the Mixin-only package. */
public final class PlantSupport {
    private PlantSupport() {}
    public static boolean hasTag(String path) {
        // Named tag holders can remain after a data-pack reload. A populated
        // marker distinguishes the active pack from those stale empty holders,
        // while allowing deliberate empty support tags to remain restrictive.
        if (!Blocks.STONE.defaultBlockState().is(TagKey.create(Registries.BLOCK,ResourceLocation.fromNamespaceAndPath("wwoocontent","plant_support_active")))) return false;
        return BuiltInRegistries.BLOCK.getTag(TagKey.create(Registries.BLOCK,ResourceLocation.withDefaultNamespace(path))).isPresent();
    }
    public static boolean tagged(BlockState state,String path) {
        return state.is(TagKey.create(Registries.BLOCK,ResourceLocation.withDefaultNamespace(path)));
    }
}
