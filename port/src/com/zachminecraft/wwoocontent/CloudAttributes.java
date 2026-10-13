package com.zachminecraft.wwoocontent;

import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Cloud attributes omitted from the target version's biome codec. */
public final class CloudAttributes {
    private static final int DEFAULT = 0xccffffff;
    private static final Map<String, Integer> COLORS = load();
    private static WeakReference<ClientLevel> previousLevel = new WeakReference<>(null);
    private static Vec3 previousPosition;
    private static long previousTick = Long.MIN_VALUE;
    private static CloudBlend.Color cached = CloudBlend.Color.fromArgb(DEFAULT);

    private static Map<String, Integer> load() {
        try (var stream = CloudAttributes.class.getResourceAsStream("/data/wwoocontent/biome_attributes.json")) {
            if (stream == null) throw new IllegalStateException("Missing WWOO biome attributes");
            var biomes = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            var colors = new HashMap<String, Integer>();
            for (var entry : biomes.entrySet()) {
                var attribute = entry.getValue().getAsJsonObject().getAsJsonObject("attributes").get("minecraft:visual/cloud_color");
                if (attribute == null) continue;
                int color = attribute.isJsonPrimitive() && attribute.getAsJsonPrimitive().isString()
                    ? (int)Long.parseLong(attribute.getAsString().substring(1), 16) : attribute.getAsInt();
                colors.put(entry.getKey(), color);
            }
            return Map.copyOf(colors);
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }

    private static int biomeColor(Holder<Biome> biome) {
        return biome.unwrapKey().map(key -> COLORS.getOrDefault(key.location().toString(), DEFAULT)).orElse(DEFAULT);
    }

    public static CloudBlend.Color color() {
        var client = Minecraft.getInstance();
        var camera = client.gameRenderer.getMainCamera();
        if (client.level == null || camera.getEntity() == null) {
            previousLevel.clear(); previousPosition = null; previousTick = Long.MIN_VALUE;
            return CloudBlend.Color.fromArgb(DEFAULT);
        }
        var level = client.level;
        var pos = camera.getPosition();
        long tick = level.getGameTime();
        // RGB and every cloud vertex's alpha share one sample at this camera position.
        if (previousLevel.get() == level && pos.equals(previousPosition) && previousTick == tick) return cached;
        int fallback = biomeColor(level.getBiome(camera.getBlockPosition()));
        int quartY = Math.floorDiv(camera.getBlockPosition().getY(), 4);
        cached = CloudBlend.sample(pos.x, pos.z, (quartX, quartZ) -> {
            // Missing neighboring chunks use the local color without requesting any chunks.
            if (!level.hasChunk(Math.floorDiv(quartX, 4), Math.floorDiv(quartZ, 4))) return fallback;
            return biomeColor(level.getNoiseBiome(quartX, quartY, quartZ));
        });
        if (previousLevel.get() != level) previousLevel = new WeakReference<>(level);
        previousPosition = pos; previousTick = tick;
        return cached;
    }

    public static float alpha() { return (float)color().alpha(); }
}
