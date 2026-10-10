package com.zachminecraft.wwoocontent;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The Overworld biomes supplied by WWOO; IDs remain stable in saved configs. */
public final class BiomeScaleOptions {
    public static final Map<String, List<String>> GROUPS;
    public static final List<String> ALL;
    static {
        var groups = new LinkedHashMap<String, List<String>>();
        add(groups, "Forests", "forest flower_forest birch_forest old_growth_birch_forest dark_forest pale_garden dappled_forest cherry_grove windswept_forest");
        add(groups, "Taigas and Snow", "taiga old_growth_pine_taiga old_growth_spruce_taiga snowy_taiga snowy_plains ice_spikes grove snowy_slopes frozen_peaks jagged_peaks");
        add(groups, "Plains and Mountains", "plains sunflower_plains meadow windswept_hills windswept_gravelly_hills stony_peaks mushroom_fields");
        add(groups, "Warm Biomes", "desert savanna savanna_plateau windswept_savanna badlands wooded_badlands eroded_badlands jungle sparse_jungle bamboo_jungle");
        add(groups, "Wetlands and Shores", "swamp mangrove_swamp river frozen_river beach snowy_beach stony_shore");
        add(groups, "Oceans", "ocean deep_ocean cold_ocean deep_cold_ocean frozen_ocean deep_frozen_ocean lukewarm_ocean deep_lukewarm_ocean warm_ocean");
        add(groups, "Caves", "lush_caves dripstone_caves deep_dark sulfur_caves");
        GROUPS = java.util.Collections.unmodifiableMap(groups);
        ALL = groups.values().stream().flatMap(List::stream).toList();
    }
    private static void add(Map<String, List<String>> groups, String title, String ids) {
        groups.put(title, Arrays.stream(ids.split(" ")).map(id -> "minecraft:" + id).toList());
    }
    private BiomeScaleOptions() {}
}
