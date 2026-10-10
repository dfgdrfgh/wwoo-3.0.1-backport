package com.zachminecraft.wwoocontent;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import de.cristelknight.cristellib.config.simple.ConfigRegistry;
import de.cristelknight.wwoo.config.WWOOConfig;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Test-only mod: checks the actual mixin-transformed, seeded server router. */
public final class BiomeSizeProbe implements ModInitializer {
    public void onInitialize() {
        Registry.register(BuiltInRegistries.FEATURE, ResourceLocation.fromNamespaceAndPath("audit", "biome_size"), new Probe());
    }

    private record NoiseRow(String key, boolean climate, double xz, double y) {}
    private static List<NoiseRow> rows(NoiseRouter router) {
        List<NoiseRow> rows = new ArrayList<>();
        router.mapAll(f -> {
            if (f instanceof DensityFunctions.Noise n) {
                rows.add(new NoiseRow(n.noise().noiseData().unwrapKey().orElseThrow().location().toString(),
                    BiomeScaling.isClimate(n.noise()), n.xzScale(), n.yScale()));
            } else if (f instanceof DensityFunctions.ShiftedNoise n) {
                rows.add(new NoiseRow(n.noise().noiseData().unwrapKey().orElseThrow().location().toString(),
                    BiomeScaling.isClimate(n.noise()), n.xzScale(), n.yScale()));
            }
            return f;
        });
        return rows;
    }
    private static void check(boolean test, String message) {
        if (!test) throw new AssertionError(message);
    }

    private static final class Probe extends Feature<NoneFeatureConfiguration> {
        Probe() { super(NoneFeatureConfiguration.CODEC); }
        public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> c) {
            double expected = Double.parseDouble(System.getProperty("wwoo.audit.multiplier"));
            boolean enabled = Boolean.parseBoolean(System.getProperty("wwoo.audit.enabled"));
            var config = ConfigRegistry.get(BiomeSizeConfig.class);
            check(config.sizeMultiplier() == expected, "Saved/default biome-size value was not loaded");
            check(!ConfigRegistry.get(WWOOConfig.class).removeOres(), "Existing removeOres=false was lost");
            check(BiomeSizeConfig.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{}")).getOrThrow().sizeMultiplier() == 1.0, "Missing setting default");
            check(BiomeSizeConfig.CODEC.encodeStart(JsonOps.INSTANCE, new BiomeSizeConfig(1.0)).getOrThrow().getAsJsonObject().get("sizeMultiplier").getAsDouble() == 1.0, "Default setting must be visible in the saved file");
            for (double invalid : new double[] {0, -1, 0.2, 8.1}) {
                check(BiomeSizeConfig.CODEC.parse(JsonOps.INSTANCE,
                    JsonParser.parseString("{\"sizeMultiplier\":" + invalid + "}")).error().isPresent(), "Invalid file value accepted");
            }
            check(new BiomeSizeConfig(Double.NaN).sizeMultiplier() == 1.0, "Nonfinite screen value");
            check(new BiomeSizeConfig(100).sizeMultiplier() == 8.0, "Screen upper bound");
            check(new BiomeSizeConfig(0).sizeMultiplier() == 0.25, "Screen lower bound");

            var access = c.level().registryAccess();
            var settings = access.registryOrThrow(Registries.NOISE_SETTINGS);
            var noises = access.lookupOrThrow(Registries.NOISE);
            var holder = ConfigRegistry.holder(BiomeSizeConfig.class);
            try {
                for (var key : List.of(NoiseGeneratorSettings.OVERWORLD, NoiseGeneratorSettings.LARGE_BIOMES,
                        NoiseGeneratorSettings.AMPLIFIED, NoiseGeneratorSettings.NETHER, NoiseGeneratorSettings.END)) {
                    var s = settings.getHolderOrThrow(key).value();
                    var original = rows(s.noiseRouter());
                    check(BiomeScaling.scaleRouter(s.noiseRouter(), 1.0) == s.noiseRouter(), "Default router must be identical");
                    holder.update(new BiomeSizeConfig(1.0));
                    var baseline = RandomState.create(s, noises, 1234567L);
                    holder.update(config);
                    var resized = RandomState.create(s, noises, 1234567L);
                    var before = rows(baseline.router());
                    var after = rows(resized.router());
                    boolean runningWorld = key.equals(NoiseGeneratorSettings.OVERWORLD) && Boolean.getBoolean("wwoo.audit.worldgen");
                    var running = c.level().getLevel().getChunkSource().randomState();
                    check(!runningWorld || after.equals(rows(running.router())), "Loaded world's router did not apply the setting");
                    boolean overworld = !key.equals(NoiseGeneratorSettings.NETHER) && !key.equals(NoiseGeneratorSettings.END);
                    double effective = enabled && overworld ? expected : 1.0;
                    check(before.size() == after.size(), "Router topology changed: " + key);
                    int climate = 0;
                    for (int i = 0; i < before.size(); i++) {
                        var a = before.get(i); var b = after.get(i);
                        check(a.key.equals(b.key), "Noise ordering changed");
                        check(a.y == b.y, "Vertical frequency changed: " + a.key);
                        check(b.xz == a.xz / (a.climate ? effective : 1.0), "Wrong horizontal frequency: " + a.key);
                        if (a.climate) climate++;
                    }
                    check(!overworld || climate > 0, "No climate noises checked");
                    check(original.equals(rows(s.noiseRouter())), "Shared registry settings were mutated");
                    int differences = 0;
                    for (int i = 0; i < 128; i++) {
                        int x = i * 281 - 17000, z = i * 167 - 8000;
                        var a = baseline.sampler().sample(x / 4, 16, z / 4);
                        var b = resized.sampler().sample(x / 4, 16, z / 4);
                        check(!runningWorld || b.equals(running.sampler().sample(x / 4, 16, z / 4)), "Loaded world uses different climate sampling");
                        if (!a.equals(b)) differences++;
                        check(Double.isFinite(resized.router().finalDensity().compute(new DensityFunction.SinglePointContext(x, 64, z))), "Nonfinite terrain density");
                    }
                    check(effective == 1.0 ? differences == 0 : differences > 0, "Climate samples did not reflect multiplier");
                    System.out.println("BIOME_SIZE_CHECK " + key.location() + " climate_nodes=" + climate + " changed_samples=" + differences);
                }
            } finally { holder.update(config); }
            System.out.println("BIOME_SIZE_PASS multiplier=" + expected + " enabled=" + enabled);
            return true;
        }
    }
}
