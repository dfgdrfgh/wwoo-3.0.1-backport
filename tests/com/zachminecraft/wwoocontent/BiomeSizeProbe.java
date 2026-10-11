package com.zachminecraft.wwoocontent;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import de.cristelknight.cristellib.config.simple.ConfigRegistry;
import de.cristelknight.wwoo.config.WWOOConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
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
            check(config.scale("minecraft:pale_garden") == Double.parseDouble(System.getProperty("wwoo.audit.pale", "1")), "Individual saved scale not loaded");
            check(config.biomeScales().keySet().containsAll(BiomeScaleOptions.ALL), "Missing per-biome defaults");
            check(BiomeSizeConfig.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"biomeScales\":{\"minecraft:pale_garden\":0}}" )).error().isPresent(), "Invalid individual scale accepted");
            var legacy = BiomeSizeConfig.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"sizeMultiplier\":2}" )).getOrThrow();
            check(legacy.sizeMultiplier() == 2 && legacy.scale("minecraft:pale_garden") == 1, "Legacy config migration");
            var custom = new BiomeSizeConfig(1, Map.of("test:biome", 3.0));
            check(BiomeSizeConfig.CODEC.parse(JsonOps.INSTANCE, BiomeSizeConfig.CODEC.encodeStart(JsonOps.INSTANCE, custom).getOrThrow()).getOrThrow().scale("test:biome") == 3, "Custom biome round-trip");

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
            checkIndividual(c, enabled, config);
            System.out.println("BIOME_SIZE_PASS multiplier=" + expected + " enabled=" + enabled);
            return true;
        }
    }

    private static String id(Holder<Biome> biome) { return biome.unwrapKey().orElseThrow().location().toString(); }

    private static double fitness(Climate.ParameterPoint p, Climate.TargetPoint t) {
        var axes = List.of(p.temperature(), p.humidity(), p.continentalness(), p.erosion(), p.depth(), p.weirdness());
        long[] values = {t.temperature(),t.humidity(),t.continentalness(),t.erosion(),t.depth(),t.weirdness()};
        double score = (double)p.offset()*p.offset();
        for (int i=0;i<6;i++) { double d=axes.get(i).distance(values[i]); score += d*d; }
        return score;
    }

    private static void checkIndividual(FeaturePlaceContext<NoneFeatureConfiguration> c, boolean enabled, BiomeSizeConfig saved) {
        var access = c.level().registryAccess();
        var presets = access.registryOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST);
        var overworld = presets.getHolderOrThrow(ResourceKey.create(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST,
            ResourceLocation.withDefaultNamespace("overworld")));
        var parameters = overworld.value().parameters();
        var source = MultiNoiseBiomeSource.createFromPreset(overworld);
        check(BiomeScaleIndex.create(parameters, new BiomeSizeConfig(1)) == null, "Default lookup must bypass index");
        var nether = presets.getHolderOrThrow(ResourceKey.create(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST,
            ResourceLocation.withDefaultNamespace("nether")));
        var large = new BiomeSizeConfig(saved.sizeMultiplier(), Map.of("minecraft:pale_garden",2.0));
        var small = new BiomeSizeConfig(saved.sizeMultiplier(), Map.of("minecraft:pale_garden",0.5));
        var larger = new BiomeSizeConfig(saved.sizeMultiplier(), Map.of("minecraft:pale_garden",4.0));
        var largest = new BiomeSizeConfig(saved.sizeMultiplier(), Map.of("minecraft:pale_garden",8.0));
        check(BiomeScaleIndex.biomeBias(1.0) == 0.0, "Default biome bias must be zero");
        check(BiomeScaleIndex.biomeBias(2.0) == -187500.0, "2x scale bias unexpectedly changed");
        check(BiomeScaleIndex.biomeBias(4.0) == -937500.0, "4x must have visibly stronger reach");
        check(BiomeScaleIndex.biomeBias(8.0) == -3937500.0, "8x must not saturate at 2x");
        check(BiomeScaleIndex.biomeBias(0.5) == 187500.0, "0.5x must shrink symmetrically");
        check(BiomeScaleIndex.biomeBias(0.25) == 937500.0, "0.25x must shrink more than 0.5x");
        check(BiomeScaleIndex.create(nether.value().parameters(), large) == null, "Overworld override changed Nether");
        var expanded = BiomeScaleIndex.create(parameters, large);
        var shrunken = BiomeScaleIndex.create(parameters, small);
        var expanded4 = BiomeScaleIndex.create(parameters, larger);
        var expanded8 = BiomeScaleIndex.create(parameters, largest);
        check(expanded != null && shrunken != null, "Pale Garden missing from generator");
        var random = new Random(9988);
        var allScales = new java.util.HashMap<String, Double>();
        for (var pair : parameters.values()) allScales.put(id(pair.getSecond()), new double[]{0.25,0.5,1,2,8}[random.nextInt(5)]);
        var mixed = new BiomeSizeConfig(1, allScales);
        var indexed = BiomeScaleIndex.create(parameters, mixed);
        for (int n=0;n<512;n++) {
            var point = new Climate.TargetPoint(random.nextInt(24001)-12000,random.nextInt(24001)-12000,
                random.nextInt(24001)-12000,random.nextInt(24001)-12000,random.nextInt(18001)-3000,random.nextInt(24001)-12000);
            var original = parameters.findValue(point);
            var winner = indexed.find(point, original);
            double best=Double.POSITIVE_INFINITY, actual=Double.POSITIVE_INFINITY;
            for (var pair : parameters.values()) {
                double s=mixed.scale(id(pair.getSecond())); double f=fitness(pair.getFirst(),point)+BiomeScaleIndex.biomeBias(s);
                best=Math.min(best,f);
                if (pair.getSecond().equals(winner)) actual=Math.min(actual,f);
            }
            check(Math.abs(best-actual)<0.0001, "Weighted index disagrees with exhaustive search");
        }
        var settings = access.registryOrThrow(Registries.NOISE_SETTINGS).getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD).value();
        var state = RandomState.create(settings, access.lookupOrThrow(Registries.NOISE),1234567L);
        var configured = BiomeScaleIndex.create(parameters, saved);
        int baselineCount=0, largeCount=0, fourCount=0, eightCount=0, smallCount=0, changes=0;
        for (int x=-64;x<64;x++) for (int z=-64;z<64;z++) {
            var point=state.sampler().sample(x*128,16,z*128);
            var original=parameters.findValue(point);
            var bigger=expanded.find(point,original); var smaller=shrunken.find(point,original);
            var bigger4=expanded4.find(point,original); var bigger8=expanded8.find(point,original);
            boolean base=id(original).equals("minecraft:pale_garden"), big=id(bigger).equals("minecraft:pale_garden"), little=id(smaller).equals("minecraft:pale_garden");
            boolean big4=id(bigger4).equals("minecraft:pale_garden"), big8=id(bigger8).equals("minecraft:pale_garden");
            baselineCount += base?1:0; largeCount += big?1:0; fourCount += big4?1:0;
            eightCount += big8?1:0; smallCount += little?1:0;
            check(!big || big4, "4x expansion lost a 2x Pale Garden sample");
            check(!big4 || big8, "8x expansion lost a 4x Pale Garden sample");
            check(!base || big, "Expansion lost existing Pale Garden sample");
            check(!little || base, "Shrinking created a new Pale Garden sample");
            check(original.equals(bigger) || big, "Expansion changed an unrelated biome to a third biome");
            check(original.equals(smaller) || base, "Shrinking changed a region outside Pale Garden");
            var expected=enabled && configured!=null ? configured.find(point,original) : original;
            var hooked=source.getNoiseBiome(point);
            check(expected.equals(hooked), "Actual biome source did not apply individual config");
            if (!hooked.equals(original)) changes++;
            if (Boolean.getBoolean("wwoo.audit.worldgen")) {
                var actual=c.level().getLevel().getChunkSource().getGenerator().getBiomeSource().getNoiseBiome(x*128,16,z*128,state.sampler());
                check(actual.equals(hooked), "Running world biome source disagrees");
            }
        }
        check(baselineCount>0 && smallCount<baselineCount && largeCount>baselineCount, "Individual scale did not change biome extent");
        check(fourCount>largeCount && eightCount>fourCount,
            "Biome expansion appears saturated before reaching 8x");
        check(!enabled || saved.scale("minecraft:pale_garden")==1 || changes>0, "Saved selective scale had no effect");
        System.out.println("BIOME_SIZE_CHECK individual pale_small="+smallCount+" pale_default="+baselineCount+" pale_2x="+largeCount+" pale_4x="+fourCount+" pale_8x="+eightCount+" configured_changes="+changes+" exhaustive_samples=512");
    }
}
