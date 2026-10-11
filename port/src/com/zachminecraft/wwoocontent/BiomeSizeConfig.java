package com.zachminecraft.wwoocontent;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.cristelknight.cristellib.config.simple.ConfigRegistry;
import de.cristelknight.cristellib.config.simple.ConfigSettings;
import java.util.HashMap;
import java.util.Optional;
import java.util.Map;
import java.util.TreeMap;

/** Separate section/file so older WWOO options retain their saved values. */
public record BiomeSizeConfig(double sizeMultiplier, Map<String, Double> biomeScales) {
    public static final Codec<BiomeSizeConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.doubleRange(0.25, 8.0).optionalFieldOf("sizeMultiplier")
            .xmap(value -> value.orElse(1.0), Optional::of)
            .forGetter(BiomeSizeConfig::sizeMultiplier),
        Codec.unboundedMap(Codec.STRING, Codec.doubleRange(0.25, 8.0))
            .optionalFieldOf("biomeScales")
            .xmap(value -> value.orElse(Map.of()), Optional::of)
            .forGetter(BiomeSizeConfig::biomeScales)
    ).apply(i, BiomeSizeConfig::new));

    public BiomeSizeConfig {
        // Also validate values constructed by the optional config screen.
        sizeMultiplier = Double.isFinite(sizeMultiplier)
            ? Math.clamp(sizeMultiplier, 0.25, 8.0) : 1.0;
        var scales = new TreeMap<String, Double>();
        for (String biome : BiomeScaleOptions.ALL) scales.put(biome, 1.0);
        biomeScales.forEach((biome, value) -> scales.put(biome,
            Double.isFinite(value) ? Math.clamp(value, 0.25, 8.0) : 1.0));
        biomeScales = java.util.Collections.unmodifiableMap(scales);
    }

    public BiomeSizeConfig(double sizeMultiplier) { this(sizeMultiplier, Map.of()); }
    public double scale(String biome) { return biomeScales.getOrDefault(biome, 1.0); }
    public boolean hasBiomeOverrides() { return biomeScales.values().stream().anyMatch(v -> v != 1.0); }

    public static final ConfigSettings<BiomeSizeConfig> SETTINGS = new ConfigSettings<>() {
        public String getSubPath() { return "wwoo/biome_size"; }
        public Codec<BiomeSizeConfig> getCodec() { return CODEC; }
        public BiomeSizeConfig getDefault() { return new BiomeSizeConfig(1.0); }
        public String getHeader() { return "Biome Size\nGlobal map scale and individual Overworld biome scales. Natural size variation remains.\n"; }
        public HashMap<String, String> getComments() {
            var comments = new HashMap<String, String>();
            comments.put("sizeMultiplier", "Range: 0.25 to 8.0. 1.0 = unchanged, 2.0 = generally twice as wide, 0.5 = generally half as wide. Biomes still vary naturally; this does not set tree counts or a minimum size. Restart/reopen the world to apply. Only newly generated chunks change. Changing this for an existing world can create terrain seams.");
            comments.put("biomeScales", "Each biome scales its native horizontal pattern about the world origin: 1.0 = unchanged, above 1.0 = expand, below 1.0 = shrink. Range: 0.25 to 8.0. Expanding a biome takes space from its neighbors and can make it more common. Natural patch widths are approximately multiplied, with grid rounding and competition where different scales overlap. Positions also scale; climate noise and terrain stay unchanged. No fixed block widths. Restart/reopen the world to apply; only new chunks change.");
            return comments;
        }
    };

    public static void register() {
        ConfigRegistry.registerWithScreen(BiomeSizeConfig.class, SETTINGS, "wwoo", "Biome Size", () -> {});
    }
}

