package com.zachminecraft.wwoocontent;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.cristelknight.cristellib.config.simple.ConfigRegistry;
import de.cristelknight.cristellib.config.simple.ConfigSettings;
import java.util.HashMap;
import java.util.Optional;

/** Separate section/file so older WWOO options retain their saved values. */
public record BiomeSizeConfig(double sizeMultiplier) {
    public static final Codec<BiomeSizeConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.doubleRange(0.25, 8.0).optionalFieldOf("sizeMultiplier")
            .xmap(value -> value.orElse(1.0), Optional::of)
            .forGetter(BiomeSizeConfig::sizeMultiplier)
    ).apply(i, BiomeSizeConfig::new));

    public BiomeSizeConfig {
        // Also validate values constructed by the optional config screen.
        sizeMultiplier = Double.isFinite(sizeMultiplier)
            ? Math.clamp(sizeMultiplier, 0.25, 8.0) : 1.0;
    }

    public static final ConfigSettings<BiomeSizeConfig> SETTINGS = new ConfigSettings<>() {
        public String getSubPath() { return "wwoo/biome_size"; }
        public Codec<BiomeSizeConfig> getCodec() { return CODEC; }
        public BiomeSizeConfig getDefault() { return new BiomeSizeConfig(1.0); }
        public String getHeader() { return "Biome Size\nChanges the general size of Overworld biome regions. Natural size variation remains.\n"; }
        public HashMap<String, String> getComments() {
            var comments = new HashMap<String, String>();
            comments.put("sizeMultiplier", "Range: 0.25 to 8.0. 1.0 = unchanged, 2.0 = generally twice as wide, 0.5 = generally half as wide. Biomes still vary naturally; this does not set tree counts or a minimum size. Restart/reopen the world to apply. Only newly generated chunks change. Changing this for an existing world can create terrain seams.");
            return comments;
        }
    };

    public static void register() {
        ConfigRegistry.registerWithScreen(BiomeSizeConfig.class, SETTINGS, "wwoo", "Biome Size", () -> {});
    }
}
