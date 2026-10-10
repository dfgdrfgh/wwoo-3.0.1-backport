package com.zachminecraft.wwoocontent;

import de.cristelknight.cristellib.config.simple.ConfigRegistry;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseRouter;

/** Scale the shared climate noises before Minecraft seeds its noise router.
 * Terrain and biome sampling then use the same continentalness/erosion/ridges.
 * Cave, aquifer, ore and surface-detail noise frequencies are left intact.
 */
public final class BiomeScaling {
    private static final Set<String> CLIMATE = Set.of(
        "temperature", "vegetation", "continentalness", "erosion", "ridge",
        "temperature_large", "vegetation_large", "continentalness_large", "erosion_large"
    );
    private BiomeScaling() {}

    public static NoiseRouter configuredRouter(NoiseRouter router) {
        if (!Blocks.STONE.defaultBlockState().is(TagKey.create(Registries.BLOCK,
                ResourceLocation.fromNamespaceAndPath("wwoocontent", "plant_support_active")))) {
            return router;
        }
        return scaleRouter(router, ConfigRegistry.get(BiomeSizeConfig.class).sizeMultiplier());
    }

    public static boolean isClimate(DensityFunction.NoiseHolder noise) {
        return noise.noiseData().unwrapKey().map(key -> {
            var id = key.location();
            return id.getNamespace().equals("minecraft") && CLIMATE.contains(id.getPath());
        }).orElse(false);
    }

    public static NoiseRouter scaleRouter(NoiseRouter router, double multiplier) {
        // Identity at the default also avoids a second traversal/allocation.
        if (multiplier == 1.0) return router;
        if (!Double.isFinite(multiplier) || multiplier < 0.25 || multiplier > 8.0) {
            throw new IllegalArgumentException("Biome size multiplier must be between 0.25 and 8.0");
        }
        // Nether/End routers have constant continents. Do not scale their
        // temperature/vegetation noises, even where they share vanilla keys.
        if (router.continents().minValue() == router.continents().maxValue()) return router;
        Map<DensityFunction, DensityFunction> scaled = new IdentityHashMap<>();
        return router.mapAll(function -> scaled.computeIfAbsent(function, value -> {
            if (value instanceof DensityFunctions.Noise noise && isClimate(noise.noise())) {
                return new DensityFunctions.Noise(noise.noise(),
                    noise.xzScale() / multiplier, noise.yScale());
            }
            if (value instanceof DensityFunctions.ShiftedNoise noise && isClimate(noise.noise())) {
                return new DensityFunctions.ShiftedNoise(noise.shiftX(), noise.shiftY(), noise.shiftZ(),
                    noise.xzScale() / multiplier, noise.yScale(), noise.noise());
            }
            return value;
        }));
    }
}
