package com.zachminecraft.wwoocontent;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.impl.builders.DoubleFieldBuilder;
import net.minecraft.network.chat.Component;

/** Optional client screen: one tab, with expandable groups of individual biomes. */
public final class BiomeSizeScreen {
    public static void add(ConfigBuilder builder, ConfigEntryBuilder entries, BiomeSizeConfig config,
            BiConsumer<String, Object> update) {
        var category = builder.getOrCreateCategory(Component.literal("Biome Size"));
        category.addEntry(entries.startTextDescription(Component.literal(
            "Choose a biome to expand or shrink. 1.0 keeps its current size variation. "
            + "Reopen the world after saving; changes affect new chunks.")).build());
        category.addEntry(field(entries, "Global Biome Scale", config.sizeMultiplier())
            .setTooltip(Component.literal("Scales the whole Overworld biome map. 2.0 generally makes regions twice as wide. Individual scales below adjust selected biomes."))
            .setSaveConsumer(value -> update.accept("sizeMultiplier", value)).build());
        var edits = new TreeMap<>(config.biomeScales());
        for (var group : BiomeScaleOptions.GROUPS.entrySet()) {
            var section = entries.startSubCategory(Component.literal(group.getKey())).setExpanded(false);
            for (String id : group.getValue()) section.add(biomeField(entries, id, config, edits, update).build());
            category.addEntry(section.build());
        }
        var extra = edits.keySet().stream().filter(id -> !BiomeScaleOptions.ALL.contains(id)).toList();
        if (!extra.isEmpty()) {
            var section = entries.startSubCategory(Component.literal("Other Biomes")).setExpanded(false);
            for (String id : extra) section.add(biomeField(entries, id, config, edits, update).build());
            category.addEntry(section.build());
        }
    }

    private static DoubleFieldBuilder biomeField(ConfigEntryBuilder entries, String id, BiomeSizeConfig config,
            Map<String, Double> edits, BiConsumer<String, Object> update) {
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        var name = new StringBuilder();
        for (String word : path.split("_")) {
            if (word.isEmpty()) continue;
            if (!name.isEmpty()) name.append(' ');
            name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        if (name.isEmpty()) name.append(id);
        if (!id.startsWith("minecraft:")) name.append(" (").append(id).append(')');
        return field(entries, name.toString(), config.scale(id))
            .setTooltip(Component.literal(id), Component.literal(
                "1.0 = unchanged; higher = expand, lower = shrink. Relative boundary scale, not an exact width. Expanding takes space from neighbors and can make this biome more common. Natural variation remains. Only affects biomes used by your world generator."))
            .setSaveConsumer(value -> {
                edits.put(id, value);
                update.accept("biomeScales", Map.copyOf(edits));
            });
    }

    private static DoubleFieldBuilder field(ConfigEntryBuilder entries, String name, double value) {
        return entries.startDoubleField(Component.literal(name), value)
            .setDefaultValue(1.0).setMin(0.25).setMax(8.0)
            .setErrorSupplier(v -> !Double.isFinite(v) || v < 0.25 || v > 8.0
                ? Optional.of(Component.literal("Enter a number from 0.25 to 8.0")) : Optional.empty());
    }
    private BiomeSizeScreen() {}
}
