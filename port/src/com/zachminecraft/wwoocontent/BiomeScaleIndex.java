package com.zachminecraft.wwoocontent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;

/** Dilate each biome's native horizontal mask without modifying climate noise.
 * Equal scales share a sample; there are no region caches or width estimates.
 */
public final class BiomeScaleIndex {
    private final Map<Holder<Biome>, Double> scales;
    private final double[] sampleScales;
    private final Climate.ParameterList<Holder<Biome>> defaults;
    private final Map<Holder<Biome>, Node> byBiome;
    private final boolean uniform;

    private BiomeScaleIndex(Climate.ParameterList<Holder<Biome>> parameters, BiomeSizeConfig config) {
        var scaleMap = new HashMap<Holder<Biome>, Double>();
        var groups = new HashMap<Holder<Biome>, List<Entry>>();
        var unchanged = new ArrayList<com.mojang.datafixers.util.Pair<Climate.ParameterPoint, Holder<Biome>>>();
        var distinct = new TreeSet<Double>();
        for (var pair : parameters.values()) {
            var biome = pair.getSecond();
            double scale = biome.unwrapKey().map(k -> config.scale(k.location().toString())).orElse(1.0);
            scaleMap.put(biome, scale);
            distinct.add(scale);
            if (scale == 1.0) unchanged.add(pair);
            groups.computeIfAbsent(biome, b -> new ArrayList<>()).add(new Entry(pair.getFirst(), biome));
        }
        scales = Map.copyOf(scaleMap);
        uniform = distinct.size() == 1;
        distinct.remove(1.0);
        sampleScales = distinct.stream().mapToDouble(Double::doubleValue).toArray();
        defaults = unchanged.isEmpty() ? null : new Climate.ParameterList<>(unchanged);
        byBiome = new HashMap<>();
        groups.forEach((biome, entries) -> byBiome.put(biome, new Node(entries.toArray(Entry[]::new))));
    }

    /** null retains the original code path, including its exact tie decisions. */
    public static BiomeScaleIndex create(Climate.ParameterList<Holder<Biome>> parameters, BiomeSizeConfig config) {
        if (!config.hasBiomeOverrides()) return null;
        boolean changed = parameters.values().stream().anyMatch(pair -> pair.getSecond().unwrapKey()
            .map(k -> config.scale(k.location().toString()) != 1.0).orElse(false));
        return changed ? new BiomeScaleIndex(parameters, config) : null;
    }

    /** Native quart-grid rounding, including negative coordinates. Integer
     * saturation protects only the sampler domain, never a biome's dimensions.
     * Coordinates within the world border do not approach that limit.
     */
    public static int scaledCoordinate(int coordinate, double scale) {
        return (int) Math.floor(coordinate / scale);
    }

    public boolean isUniform() { return uniform; }

    /** A uniform map needs only its warped native lookup. Looking up the
     * unwarped point first can change vanilla's cached tie preference.
     */
    public Holder<Biome> findUniform(int x, int y, int z, Climate.Sampler sampler,
            Climate.TargetPoint point, Function<Climate.TargetPoint, Holder<Biome>> lookup) {
        double scale = sampleScales[0];
        int sx = scaledCoordinate(x, scale), sz = scaledCoordinate(z, scale);
        return lookup.apply(sx == x && sz == z ? point : sampler.sample(sx, y, sz));
    }

    public Holder<Biome> find(int x, int y, int z, Climate.Sampler sampler,
            Climate.TargetPoint point, Holder<Biome> original,
            Function<Climate.TargetPoint, Holder<Biome>> lookup) {
        Holder<Biome> winner = null;
        double best = Double.POSITIVE_INFINITY;
        for (double scale : sampleScales) {
            int sx = scaledCoordinate(x, scale), sz = scaledCoordinate(z, scale);
            var sampled = sx == x && sz == z ? point : sampler.sample(sx, y, sz);
            var candidate = sampled == point ? original : lookup.apply(sampled);
            if (uniform) return candidate;
            // Use only the mask at its own frequency. Unioning its original
            // footprint back in would destroy the intended width multiplier.
            if (scales.getOrDefault(candidate, 1.0) != scale) continue;
            var node = byBiome.get(candidate);
            if (node == null) continue; // Another mod supplied an external holder.
            double score = node.score(sampled);
            if (score < best || (score == best && candidate.equals(original))) {
                winner = candidate;
                best = score;
            }
        }
        if (winner != null) return winner;
        if (scales.getOrDefault(original, 1.0) == 1.0) return original;
        // Vacated space uses unchanged vanilla climate fitness among unscaled
        // entries. If every biome has a distinct override and masks leave a
        // gap, retain the native winner there to avoid holes.
        return defaults == null ? original : defaults.findValue(point);
    }

    private static final class Best {
        Holder<Biome> biome;
        double score = Double.POSITIVE_INFINITY;
        Best(Holder<Biome> biome) { this.biome = biome; }
    }

    private static final class Entry {
        final Holder<Biome> biome;
        final long[] min = new long[6], max = new long[6];
        final double offsetSquared;
        Entry(Climate.ParameterPoint point, Holder<Biome> biome) {
            this.biome = biome;
            offsetSquared = (double) point.offset() * point.offset();
            var axes = List.of(point.temperature(), point.humidity(), point.continentalness(),
                point.erosion(), point.depth(), point.weirdness());
            for (int i = 0; i < 6; i++) {
                min[i] = axes.get(i).min(); max[i] = axes.get(i).max();
            }
        }
        double score(long[] target) {
            return distance(target, min, max) + offsetSquared;
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
        final double minOffsetSquared;
        final Node left, right;
        final Entry[] leaves;
        Node(Entry[] entries) {
            Arrays.fill(min, Long.MAX_VALUE); Arrays.fill(max, Long.MIN_VALUE);
            double minOffset = Double.POSITIVE_INFINITY;
            for (Entry entry : entries) {
                minOffset = Math.min(minOffset, entry.offsetSquared);
                for (int i = 0; i < 6; i++) {
                    min[i] = Math.min(min[i], entry.min[i]); max[i] = Math.max(max[i], entry.max[i]);
                }
            }
            minOffsetSquared = minOffset;
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
        double score(Climate.TargetPoint point) {
            long[] target = {point.temperature(), point.humidity(), point.continentalness(),
                point.erosion(), point.depth(), point.weirdness()};
            var best = new Best(null);
            search(target, best);
            return best.score;
        }
        double bound(long[] target) {
            return distance(target, min, max) + minOffsetSquared;
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

