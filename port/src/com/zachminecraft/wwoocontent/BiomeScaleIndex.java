package com.zachminecraft.wwoocontent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;

/** Weighted climate-region boundaries, with a bounding-volume search index.
 * Larger scales let a biome win farther from its original climate region.
 * The symmetric squared-scale response gives 4x/8x settings meaningfully more
 * reach than 2x, without changing the underlying climate or terrain noises.
 * This changes relative extent, not an exact guaranteed block width.
 */
public final class BiomeScaleIndex {
    private static final double EDGE_ALLOWANCE = 250.0 * 250.0;

    // Quantized climate distances are squared. Scale the selection advantage
    // quadratically as well; the previous log2 response compressed 2x..8x
    // into nearly the same small edge adjustment. Reciprocal values shrink.
    // At 1.0 this is exactly zero and preserves vanilla selection.
    static double biomeBias(double scale) {
        double relativeReach = scale >= 1.0
            ? scale * scale - 1.0
            : 1.0 - 1.0 / (scale * scale);
        return -EDGE_ALLOWANCE * relativeReach;
    }

    private final Node root;
    private final Map<Holder<Biome>, Node> byBiome;

    private BiomeScaleIndex(List<Entry> entries) {
        root = new Node(entries.toArray(Entry[]::new));
        var groups = new HashMap<Holder<Biome>, List<Entry>>();
        for (Entry entry : entries) groups.computeIfAbsent(entry.biome, k -> new ArrayList<>()).add(entry);
        byBiome = new HashMap<>();
        groups.forEach((biome, group) -> byBiome.put(biome, new Node(group.toArray(Entry[]::new))));
    }

    /** null preserves the original vanilla lookup for entirely default sources. */
    public static BiomeScaleIndex create(Climate.ParameterList<Holder<Biome>> parameters, BiomeSizeConfig config) {
        if (!config.hasBiomeOverrides()) return null;
        boolean changed = false;
        var entries = new ArrayList<Entry>();
        for (var pair : parameters.values()) {
            var biome = pair.getSecond();
            double scale = biome.unwrapKey().map(key -> config.scale(key.location().toString())).orElse(1.0);
            changed |= scale != 1.0;
            entries.add(new Entry(pair.getFirst(), biome, scale));
        }
        return changed ? new BiomeScaleIndex(entries) : null;
    }

    public Holder<Biome> find(Climate.TargetPoint point, Holder<Biome> original) {
        long[] target = {point.temperature(), point.humidity(), point.continentalness(),
            point.erosion(), point.depth(), point.weirdness()};
        // Seed with the original winner. Equal fitness retains vanilla's tie
        // decision, so unrelated biomes do not swap just because an index exists.
        var best = new Best(original);
        byBiome.get(original).search(target, best);
        root.search(target, best);
        return best.biome;
    }

    private static final class Best {
        Holder<Biome> biome;
        double score = Double.POSITIVE_INFINITY;
        Best(Holder<Biome> biome) { this.biome = biome; }
    }

    private static final class Entry {
        final Holder<Biome> biome;
        final long[] min = new long[6], max = new long[6];
        final double offsetAndBias;
        Entry(Climate.ParameterPoint point, Holder<Biome> biome, double scale) {
            this.biome = biome;
            offsetAndBias = (double) point.offset() * point.offset() + biomeBias(scale);
            var axes = List.of(point.temperature(), point.humidity(), point.continentalness(),
                point.erosion(), point.depth(), point.weirdness());
            for (int i = 0; i < 6; i++) {
                min[i] = axes.get(i).min(); max[i] = axes.get(i).max();
            }
        }
        double score(long[] target) {
            return distance(target, min, max) + offsetAndBias;
        }
    }

    private static double distance(long[] target, long[] min, long[] max) {
        double total = 0;
        for (int i = 0; i < 6; i++) {
            double d = target[i] < min[i] ? (double) min[i] - target[i]
                : target[i] > max[i] ? (double) target[i] - max[i] : 0;
            total += d * d;
        }
        return total;
    }

    private static final class Node {
        final long[] min = new long[6], max = new long[6];
        final double minOffsetAndBias;
        final Node left, right;
        final Entry[] leaves;
        Node(Entry[] entries) {
            Arrays.fill(min, Long.MAX_VALUE); Arrays.fill(max, Long.MIN_VALUE);
            double minOffset = Double.POSITIVE_INFINITY;
            for (Entry entry : entries) {
                minOffset = Math.min(minOffset, entry.offsetAndBias);
                for (int i = 0; i < 6; i++) {
                    min[i] = Math.min(min[i], entry.min[i]); max[i] = Math.max(max[i], entry.max[i]);
                }
            }
            minOffsetAndBias = minOffset;
            if (entries.length <= 8) {
                leaves = entries; left = right = null;
            } else {
                int splitAxis = 0;
                for (int i = 1; i < 6; i++) if (max[i] - min[i] > max[splitAxis] - min[splitAxis]) splitAxis = i;
                final int axis = splitAxis;
                Arrays.sort(entries, Comparator.comparingLong(e -> e.min[axis] + e.max[axis]));
                int middle = entries.length / 2;
                left = new Node(Arrays.copyOfRange(entries, 0, middle));
                right = new Node(Arrays.copyOfRange(entries, middle, entries.length));
                leaves = null;
            }
        }
        double bound(long[] target) {
            return distance(target, min, max) + minOffsetAndBias;
        }
        void search(long[] target, Best best) {
            if (bound(target) >= best.score) return;
            if (leaves != null) {
                for (Entry entry : leaves) {
                    double score = entry.score(target);
                    if (score < best.score) { best.score = score; best.biome = entry.biome; }
                }
                return;
            }
            if (left.bound(target) <= right.bound(target)) {
                left.search(target, best); right.search(target, best);
            } else {
                right.search(target, best); left.search(target, best);
            }
        }
    }
}
