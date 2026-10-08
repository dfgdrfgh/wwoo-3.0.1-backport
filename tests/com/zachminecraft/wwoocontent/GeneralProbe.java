package com.zachminecraft.wwoocontent;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.world.level.levelgen.feature.*;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import de.cristelknight.cristellib.data.condition.conditions.ConfigValueCondition;

/** Read-only diagnostics for the dedicated-server integration test. */
public final class GeneralProbe implements ModInitializer {
    public void onInitialize() {
        Registry.register(BuiltInRegistries.FEATURE,ResourceLocation.fromNamespaceAndPath("audit","probe"),new Probe());
    }
    private static final class Probe extends Feature<NoneFeatureConfiguration> {
        Probe() { super(NoneFeatureConfiguration.CODEC); }
        public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> c) {
            var condition=new ConfigValueCondition("de.cristelknight.wwoo.config.WWOOConfig","removeOres",new JsonPrimitive(true));
            System.out.println("AUDIT_REMOVE_CONDITION="+condition.test());
            var registry=c.level().registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
            for(String ore:new String[]{"ore_andesite_lower","ore_andesite_upper","ore_diorite_lower","ore_diorite_upper","ore_granite_lower","ore_granite_upper","ore_dirt","ore_gravel"}) {
                for(String id:new String[]{"minecraft:"+ore,"wwoocontent:vanilla/"+ore}) {
                    var value=registry.getOptional(ResourceLocation.parse(id));
                    if(value.isPresent()) {
                        var data=PlacedFeature.DIRECT_CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE,c.level().registryAccess()),value.get()).getOrThrow().getAsJsonObject();
                        System.out.println("AUDIT_ORE="+id+" "+data.get("placement"));
                    }
                }
            }
            return true;
        }
    }
}
