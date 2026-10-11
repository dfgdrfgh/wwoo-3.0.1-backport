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
            System.out.println("BIOME_SIZE_STARTED");
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

    private static Holder<Biome> find(BiomeScaleIndex index, Climate.ParameterList<Holder<Biome>> parameters,
            int x, int y, int z, Climate.Sampler sampler) {
        var point = sampler.sample(x,y,z);
        var nativeBiome = parameters.findValue(point);
        return index == null ? nativeBiome : index.find(x,y,z,sampler,point,nativeBiome,parameters::findValue);
    }

    private record Axis(int axis) implements DensityFunction.SimpleFunction {
        public double compute(DensityFunction.FunctionContext c) {
            return (axis==0?c.blockX():axis==1?c.blockZ():c.blockY())/4.0;
        }
        public double minValue(){return -30000000;}
        public double maxValue(){return 30000000;}
        public net.minecraft.util.KeyDispatchDataCodec<? extends DensityFunction> codec(){throw new UnsupportedOperationException("Test-only function");}
    }

    private static void checkSyntheticGeometry(Climate.ParameterList<Holder<Biome>> nativeParameters) {
        var pale=nativeParameters.values().stream().map(p->p.getSecond()).filter(b->id(b).equals("minecraft:pale_garden")).findFirst().orElseThrow();
        var plains=nativeParameters.values().stream().map(p->p.getSecond()).filter(b->id(b).equals("minecraft:plains")).findFirst().orElseThrow();
        var zero=new Climate.Parameter(0,0);
        var p=new Climate.ParameterPoint(zero,zero,zero,zero,zero,zero,0);
        var parameters=new Climate.ParameterList<Holder<Biome>>(List.of(com.mojang.datafixers.util.Pair.of(p,pale),com.mojang.datafixers.util.Pair.of(p,plains)));
        var constant=DensityFunctions.constant(0);
        var sampler=new Climate.Sampler(new Axis(0),new Axis(1),constant,constant,new Axis(2),constant,List.of());
        int[][] patches={{-900,-851},{-500,-369},{100,407}}; // widths 49, 131, 307 quart cells, naturally varied
        java.util.function.Function<Climate.TargetPoint,Holder<Biome>> lookup=t->{
            check(t.depth()==160000,"Horizontal scaling changed sampling height");
            long x=t.temperature()/10000,z=t.humidity()/10000;
            for(var patch:patches)if(x>=patch[0]&&x<patch[1]&&z>=-31&&z<28)return pale;
            return plains;
        };
        for(double scale:new double[]{0.25,0.5,1.5,2,4,8}) {
            var index=BiomeScaleIndex.create(parameters,new BiomeSizeConfig(1,Map.of("minecraft:pale_garden",scale)));
            var widths=new ArrayList<Integer>();int run=0;
            for(int x=-8000;x<4000;x++) {
                var point=sampler.sample(x,16,0);var original=lookup.apply(point);
                var actual=index.find(x,16,0,sampler,point,original,lookup);
                boolean expected=false;
                for(var patch:patches)if(x>=Math.ceil(patch[0]*scale)&&x<Math.ceil(patch[1]*scale))expected=true;
                check(actual.equals(expected?pale:plains),"Geometric dilation changed boundary at "+x+" scale="+scale);
                if(actual.equals(pale))run++;
                else if(run>0){widths.add(run);run=0;}
            }
            check(widths.size()==3,"Natural patch count or variation lost");
            for(int n=0;n<3;n++)check(Math.abs(widths.get(n)-(patches[n][1]-patches[n][0])*scale)<=1,
                "Measured width did not multiply by configured scale");
            System.out.println("BIOME_SIZE_CHECK geometric_scale="+scale+" native_quart_widths=[49,131,307] measured_quart_widths="+widths);
        }
    }

    private static void checkIndividual(FeaturePlaceContext<NoneFeatureConfiguration> c, boolean enabled, BiomeSizeConfig saved) {
        var access = c.level().registryAccess();
        var presets = access.registryOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST);
        var overworld = presets.getHolderOrThrow(ResourceKey.create(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST,
            ResourceLocation.withDefaultNamespace("overworld")));
        var parameters = overworld.value().parameters();
        var source = MultiNoiseBiomeSource.createFromPreset(overworld);
        checkSyntheticGeometry(parameters);
        var nether = presets.getHolderOrThrow(ResourceKey.create(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST,
            ResourceLocation.withDefaultNamespace("nether")));
        check(BiomeScaleIndex.create(parameters, new BiomeSizeConfig(1)) == null, "Defaults must use native lookup");
        check(BiomeScaleIndex.create(parameters, new BiomeSizeConfig(1, Map.of("test:absent",8.0))) == null,
            "Absent override must not activate sampling");
        check(BiomeScaleIndex.create(nether.value().parameters(), new BiomeSizeConfig(1, Map.of("minecraft:pale_garden",8.0))) == null,
            "Overworld override changed Nether");
        check(BiomeScaleIndex.scaledCoordinate(-1,2) == -1 && BiomeScaleIndex.scaledCoordinate(1,2) == 0,
            "Negative coordinates must floor, not truncate");
        check(BiomeScaleIndex.scaledCoordinate(-7500000,0.25) == -30000000, "World border overflow");
        var settings = access.registryOrThrow(Registries.NOISE_SETTINGS).getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD).value();
        var noises = access.lookupOrThrow(Registries.NOISE);
        var holder = ConfigRegistry.holder(BiomeSizeConfig.class);
        int masks=0, widths=0, activeChanges=0, nativePale=0;
        try {
            // Covariance: every native mask point maps to the same biome at
            // scale times its original coordinates. This tests spatial widths,
            // not increased prevalence in an unrelated fixed-size window.
            for (long seed : new long[]{1234567L,4523263735266840700L,-9988L}) {
                holder.update(new BiomeSizeConfig(1.0));
                var state = RandomState.create(settings,noises,seed);
                var sampler = state.sampler();
                for (double scale : new double[]{0.25,0.5,1.5,2,4,8}) {
                    var config = new BiomeSizeConfig(1.0,Map.of("minecraft:pale_garden",scale));
                    var index = BiomeScaleIndex.create(parameters,config);
                    check(index != null,"Pale Garden must exist in parameter table");
                    holder.update(config);
                    var after = RandomState.create(settings,noises,seed);
                    check(rows(state.router()).equals(rows(after.router())),"Individual scale modified noise router");
                    for (int x=-32;x<32;x++) for (int z=-32;z<32;z++) {
                        int bx=x*128,bz=z*128,tx=(int)(bx*scale),tz=(int)(bz*scale);
                        var nativeBiome=parameters.findValue(sampler.sample(bx,16,bz));
                        var resized=find(index,parameters,tx,16,tz,sampler);
                        boolean pale=id(nativeBiome).equals("minecraft:pale_garden");
                        check(pale == id(resized).equals("minecraft:pale_garden"),"Native mask failed spatial covariance at "+scale);
                        if (pale) nativePale++;
                        masks++;
                        if (seed==1234567L && scale==saved.scale("minecraft:pale_garden")) {
                            var actual=source.getNoiseBiome(tx,16,tz,sampler);
                            check(actual.equals(enabled ? resized : parameters.findValue(sampler.sample(tx,16,tz))),"Coordinate mixin did not compose correctly");
                        }
                        check(source.getNoiseBiome(sampler.sample(tx,16,tz)).equals(parameters.findValue(sampler.sample(tx,16,tz))),
                            "Climate-only lookup changed");
                        check(state.sampler().sample(tx,16,tz).equals(after.sampler().sample(tx,16,tz)),"Individual override changed climate values");
                    }
                    // Compare complete transect runs at corresponding points;
                    // every run's physical length is multiplied by the scale.
                    boolean prev=false; int run=0;
                    for (int x=-512;x<=512;x++) {
                        int bx=x*16,bz=-1024;
                        boolean base=id(parameters.findValue(sampler.sample(bx,16,bz))).equals("minecraft:pale_garden");
                        boolean actual=id(find(index,parameters,(int)(bx*scale),16,(int)(bz*scale),sampler)).equals("minecraft:pale_garden");
                        check(base==actual,"Transect boundary did not multiply");
                        if (base) run++;
                        if (prev && !base) { check(run*16*scale > 0,"Invalid scaled width"); widths++;run=0; }
                        prev=base;
                    }
                }
                // Exact native behavior, negative and positive coordinates and
                // underground/surface/heights, on the actual transformed source.
                holder.update(new BiomeSizeConfig(saved.sizeMultiplier()));
                for (int y : new int[]{-12,0,16,64}) for(int n=0;n<512;n++) {
                    int x=n*337-80000,z=n*197-40000;
                    var point=sampler.sample(x,y,z);
                    check(parameters.findValue(point).equals(source.getNoiseBiome(x,y,z,sampler)),"Default generation changed");
                }
                // Uniform individual scales must give the exact native map at
                // the new coordinates, with no collision arbitration at all.
                for (double scale : new double[]{0.25,0.5,2,8}) {
                    var all = new java.util.HashMap<String,Double>();
                    for(var pair:parameters.values()) all.put(id(pair.getSecond()),scale);
                    var config=new BiomeSizeConfig(saved.sizeMultiplier(),all);
                    holder.update(config);
                    var index=BiomeScaleIndex.create(parameters,config);
                    for(int n=0;n<512;n++) {
                        int x=n*337-80000,z=n*197-40000,y=n%80-12;
                        var expected=parameters.findValue(sampler.sample(BiomeScaleIndex.scaledCoordinate(x,scale),y,
                            BiomeScaleIndex.scaledCoordinate(z,scale)));
                        check(find(index,parameters,x,y,z,sampler).equals(expected),"Uniform masks did not preserve native map");
                        check(source.getNoiseBiome(x,y,z,sampler).equals(enabled?expected:parameters.findValue(sampler.sample(x,y,z))),
                            "Uniform runtime lookup differs");
                    }
                }
            }
            check(!enabled || (nativePale>0 && widths>0),"Rare-biome geometry tests had no coverage");
            holder.update(saved);
            var state=RandomState.create(settings,noises,1234567L);
            var configured=BiomeScaleIndex.create(parameters,saved);
            for(int n=0;n<4096;n++) {
                int x=(n%64-32)*128,z=(n/64-32)*128;
                var point=state.sampler().sample(x,16,z);
                var nativeBiome=parameters.findValue(point);
                var expected=enabled?find(configured,parameters,x,16,z,state.sampler()):nativeBiome;
                var actual=source.getNoiseBiome(x,16,z,state.sampler());
                check(actual.equals(expected),"Saved config not applied after repeated reloads");
                if(!actual.equals(nativeBiome))activeChanges++;
                if(Boolean.getBoolean("wwoo.audit.worldgen")) {
                    var running=c.level().getLevel().getChunkSource().getGenerator().getBiomeSource();
                    check(running.getNoiseBiome(x,16,z,state.sampler()).equals(actual),"Running world biome source differs");
                }
            }
            check(!enabled || configured==null || activeChanges>0,"Active override had no runtime effect");
            // Mixed scales, concurrent sampling and iteration-order invariance.
            var random=new Random(9988); var mixedMap=new java.util.HashMap<String,Double>();
            for(var pair:parameters.values()) mixedMap.put(id(pair.getSecond()),new double[]{0.25,0.5,1,2,8}[random.nextInt(5)]);
            var mixedConfig=new BiomeSizeConfig(saved.sizeMultiplier(),mixedMap);
            var mixed=BiomeScaleIndex.create(parameters,mixedConfig);
            var outputs=new ArrayList<Holder<Biome>>();
            for(int n=0;n<1024;n++)outputs.add(find(mixed,parameters,n*113-50000,16,n*71-30000,state.sampler()));
            java.util.stream.IntStream.range(0,1024).parallel().forEach(n -> {
                check(outputs.get(n).equals(find(mixed,parameters,n*113-50000,16,n*71-30000,state.sampler())),
                    "Parallel or out-of-order sampling changed biome");
            });
            // Missing replacement climates (all overridden, different scales)
            // must still return a valid biome instead of throwing or leaving gaps.
            mixedMap.replaceAll((k,v)->v==1.0?4.0:v);
            var noDefaults=BiomeScaleIndex.create(parameters,new BiomeSizeConfig(1,mixedMap));
            for(int n=0;n<1024;n++)check(find(noDefaults,parameters,n*97-40000,16,n*59-20000,state.sampler())!=null,"Unfilled mixed-scale gap");
        } finally {holder.update(saved);}
        System.out.println("BIOME_SIZE_CHECK spatial_mask_samples="+masks+" transect_runs="+widths+" native_pale_samples="+nativePale+" active_changes="+activeChanges+" seeds=3 parallel_samples=1024");
    }
}
