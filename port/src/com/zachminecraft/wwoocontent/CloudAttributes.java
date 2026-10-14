package com.zachminecraft.wwoocontent;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.fabricmc.loader.api.FabricLoader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Cloud attributes omitted from the target version's biome codec. */
public final class CloudAttributes {
    private static final JsonObject BIOMES = load();
    private static JsonObject load() {
        try (var stream = CloudAttributes.class.getResourceAsStream("/data/wwoocontent/biome_attributes.json")) {
            if (stream == null) throw new IllegalStateException("Missing WWOO biome attributes");
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }
    /** The standalone mod owns tint and opacity when installed. */
    public static boolean enabled() {
        return !FabricLoader.getInstance().isModLoaded("biome_cloud_transitions");
    }
    public static int color() {
        var client = Minecraft.getInstance();
        var camera = client.gameRenderer.getMainCamera();
        if (client.level == null || camera.getEntity() == null) return 0xccffffff;
        var key = client.level.getBiome(camera.getBlockPosition()).unwrapKey();
        if (key.isEmpty()) return 0xccffffff;
        var biome = BIOMES.getAsJsonObject(key.get().location().toString());
        if (biome == null) return 0xccffffff;
        var attribute = biome.getAsJsonObject("attributes").get("minecraft:visual/cloud_color");
        if (attribute == null) return 0xccffffff;
        return attribute.isJsonPrimitive() && attribute.getAsJsonPrimitive().isString()
            ? (int)Long.parseLong(attribute.getAsString().substring(1), 16) : attribute.getAsInt();
    }
    public static float alpha() { return (color() >>> 24) / 255.0f; }
}

