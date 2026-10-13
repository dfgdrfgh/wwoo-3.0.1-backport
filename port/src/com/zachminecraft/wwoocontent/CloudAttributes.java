package com.zachminecraft.wwoocontent;

import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Cloud attributes omitted from the target version's biome codec. */
public final class CloudAttributes {
    private static final int DEFAULT = 0xccffffff;
    private static final Map<String, Integer> COLORS = load();

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
            return CloudBlend.Color.fromArgb(DEFAULT);
        }
        var manager = client.level.getBiomeManager();
        var position = camera.getPosition();
        // Sample the actual noise-biome volume just as the sky does, using each biome's cloud RGB.
        return CloudBlend.sample(position.x, position.y, position.z,
            (x, y, z) -> biomeColor(manager.getNoiseBiomeAtQuart(x, y, z)));
    }
}
