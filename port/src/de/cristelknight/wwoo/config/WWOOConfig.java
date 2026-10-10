package de.cristelknight.wwoo.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.cristelknight.cristellib.config.simple.ConfigRegistry;
import de.cristelknight.cristellib.config.simple.ConfigSettings;
import java.util.HashMap;
import java.util.Optional;

/** 3.0.1 exposes only the remove-ores option; 2.6.7 add-on switches are retired. */
public record WWOOConfig(boolean removeOres) {
    // Cristel Lib evaluates pack conditions against the encoded config. Keep
    // the default value explicit there, while still accepting older {} files.
    public static final Codec<WWOOConfig> CODEC=RecordCodecBuilder.create(i -> i.group(Codec.BOOL.optionalFieldOf("removeOres").xmap(v -> v.orElse(true), Optional::of).forGetter(WWOOConfig::removeOres)).apply(i,WWOOConfig::new));
    public static final ConfigSettings<WWOOConfig> SETTINGS=new ConfigSettings<>() {
        public String getSubPath() { return "wwoo/config"; }
        public Codec<WWOOConfig> getCodec() { return CODEC; }
        public WWOOConfig getDefault() { return new WWOOConfig(true); }
        public String getHeader() { return "WWOO Config\n"; }
        public HashMap<String,String> getComments() {
            var comments=new HashMap<String,String>();
            comments.put("removeOres","Removes underground andesite, diorite, granite, gravel, and dirt patches. Requires a game restart.");
            return comments;
        }
    };
    public static void register() {
        ConfigRegistry.registerWithScreen(WWOOConfig.class,SETTINGS,"wwoo","WWOO Config",() -> {});
        com.zachminecraft.wwoocontent.BiomeSizeConfig.register();
    }
}

