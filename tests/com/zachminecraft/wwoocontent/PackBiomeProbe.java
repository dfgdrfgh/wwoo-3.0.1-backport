package com.zachminecraft.wwoocontent;

import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.feature.*;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import de.cristelknight.cristellib.config.simple.ConfigRegistry;

/** Test-only integration against each generator loaded by the user's SMP pack. */
public final class PackBiomeProbe implements ModInitializer {
    public void onInitialize() { Registry.register(BuiltInRegistries.FEATURE, ResourceLocation.fromNamespaceAndPath("audit","pack_biomes"), new Probe()); }
    static void check(boolean b,String message) { if(!b)throw new AssertionError(message); }
    static String id(net.minecraft.core.Holder<Biome> b) { return b.unwrapKey().orElseThrow().location().toString(); }
    static int scaled(int x,double scale) { return (int)Math.floor(x/scale); }
    private static final class Probe extends Feature<NoneFeatureConfiguration> {
        Probe(){super(NoneFeatureConfiguration.CODEC);}
        public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> c) {
            System.out.println("SMP_PACK_STARTED");
            var saved=ConfigRegistry.get(BiomeSizeConfig.class);
            var holder=ConfigRegistry.holder(BiomeSizeConfig.class);
            int samples=0,changes=0,paleCoverage=0;
            try {
                for(var level:c.level().getLevel().getServer().getAllLevels()) {
                    var generator=level.getChunkSource().getGenerator();
                    var raw=generator.getBiomeSource();
                    System.out.println("SMP_PACK_DIMENSION "+level.dimension().location()+" generator="+generator.getClass().getName()+" source="+raw.getClass().getName());
                    if(!(raw instanceof MultiNoiseBiomeSource source))continue;
                    var running=level.getChunkSource().randomState();
                    var sampler=running.sampler();
                    holder.update(new BiomeSizeConfig(saved.sizeMultiplier()));
                    for(int i=0;i<4096;i++) {
                        int x=i*103-180000,z=i*71-150000,y=i%96-16;
                        check(source.getNoiseBiome(x,y,z,sampler).equals(source.getNoiseBiome(sampler.sample(x,y,z))),"Default biome changed: "+level.dimension());
                        samples++;
                    }
                    var all=new HashMap<String,Double>();
                    for(var biome:source.possibleBiomes())all.put(id(biome),1.0);
                    for(double scale:new double[]{0.25,0.5,1.5,2,4,8}) {
                        all.replaceAll((k,v)->scale);
                        holder.update(new BiomeSizeConfig(saved.sizeMultiplier(),all));
                        for(int i=0;i<4096;i++) {
                            int x=i*103-180000,z=i*71-150000,y=i%96-16;
                            var expected=source.getNoiseBiome(sampler.sample(scaled(x,scale),y,scaled(z,scale)));
                            var actual=source.getNoiseBiome(x,y,z,sampler);
                            check(actual.equals(expected),"Uniform scaling failed in loaded source "+level.dimension()+" scale="+scale+" x="+x+" y="+y+" z="+z+" expected="+id(expected)+" actual="+id(actual)+" repeat_native="+id(source.getNoiseBiome(sampler.sample(scaled(x,scale),y,scaled(z,scale))))+" possible="+all.size());
                            samples++;
                        }
                    }
                    if(source.possibleBiomes().stream().anyMatch(b->id(b).equals("minecraft:pale_garden"))) {
                        for(double scale:new double[]{0.25,0.5,1.5,2,4,8}) {
                            holder.update(new BiomeSizeConfig(saved.sizeMultiplier(),Map.of("minecraft:pale_garden",scale)));
                            // Broad deterministic coverage avoids relying on spawn being near a rare biome.
                            var random=new Random(9988);
                            for(int i=0;i<32768;i++) {
                                int x=random.nextInt(500000)-250000,z=random.nextInt(500000)-250000,y=i%80-12;
                                boolean expected=id(source.getNoiseBiome(sampler.sample(scaled(x,scale),y,scaled(z,scale)))).equals("minecraft:pale_garden");
                                var actual=source.getNoiseBiome(x,y,z,sampler);
                                check(id(actual).equals("minecraft:pale_garden")==expected,"Single biome mask failed: scale="+scale+" x="+x);
                                if(expected)paleCoverage++;
                                if(!actual.equals(source.getNoiseBiome(sampler.sample(x,y,z))))changes++;
                                samples++;
                            }
                        }
                    }
                    // Concurrent calls use the actual mixin and C2ME-transformed source.
                    all.replaceAll((k,v)->k.hashCode()%2==0?0.5:2.0);
                    holder.update(new BiomeSizeConfig(saved.sizeMultiplier(),all));
                    var expected=new ArrayList<net.minecraft.core.Holder<Biome>>();
                    for(int i=0;i<4096;i++)expected.add(source.getNoiseBiome(i*103-180000,16,i*71-150000,sampler));
                    java.util.stream.IntStream.range(0,4096).parallel().forEach(i -> check(source.getNoiseBiome(i*103-180000,16,i*71-150000,sampler).equals(expected.get(i)),"Concurrent biome output changed"));
                    samples+=8192;
                    // Individual config updates must not alter climate or terrain, including Tectonic's router.
                    if(generator instanceof NoiseBasedChunkGenerator ng) {
                        holder.update(new BiomeSizeConfig(saved.sizeMultiplier()));
                        var before=RandomState.create(ng.generatorSettings().value(),level.registryAccess().lookupOrThrow(Registries.NOISE),level.getSeed());
                        holder.update(new BiomeSizeConfig(saved.sizeMultiplier(),Map.of("minecraft:pale_garden",8.0,"minecraft:plains",0.25)));
                        var after=RandomState.create(ng.generatorSettings().value(),level.registryAccess().lookupOrThrow(Registries.NOISE),level.getSeed());
                        for(int i=0;i<512;i++) {
                            int x=i*281-70000,z=i*167-40000,y=i%384-64;
                            check(before.sampler().sample(x,16,z).equals(after.sampler().sample(x,16,z)),"Individual scaling changed pack climate");
                            var point=new DensityFunction.SinglePointContext(x,y,z);
                            check(Double.doubleToLongBits(before.router().finalDensity().compute(point))==Double.doubleToLongBits(after.router().finalDensity().compute(point)),"Individual scaling changed pack terrain density");
                            samples+=2;
                        }
                    }
                    System.out.println("SMP_PACK_CHECK dimension="+level.dimension().location()+" biomes="+all.size()+" climate_and_density_unchanged=true");
                }
                check(paleCoverage>0,"Rare biome tests lack coverage");
                check(changes>0,"Single biome overrides had no effect");
            } finally {holder.update(saved);}
            System.out.println("SMP_PACK_PASS samples="+samples+" pale_coverage="+paleCoverage+" active_changes="+changes);
            return true;
        }
    }
}
