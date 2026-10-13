package com.zachminecraft.wwoocontent;

import net.minecraft.util.CubicSampler;
import net.minecraft.world.phys.Vec3;

/** Blend biome cloud RGB using the same spatial sampler as ClientLevel's sky color. */
public final class CloudBlend {
    private CloudBlend() {}

    public record Color(double red, double green, double blue) {
        public static Color fromArgb(int argb) {
            return new Color(((argb >> 16) & 255) / 255.0, ((argb >> 8) & 255) / 255.0,
                (argb & 255) / 255.0);
        }
    }

    @FunctionalInterface
    public interface BiomeColors {
        int atQuart(int x, int y, int z);
    }

    public static Color sample(double cameraX, double cameraY, double cameraZ, BiomeColors colors) {
        // Exact camera-to-quart transform and Gaussian kernel used by ClientLevel.getSkyColor.
        var position = new Vec3(cameraX, cameraY, cameraZ).subtract(2.0, 2.0, 2.0).scale(0.25);
        var mixed = CubicSampler.gaussianSampleVec3(position, (x, y, z) -> {
            var rgb = Color.fromArgb(colors.atQuart(x, y, z));
            return new Vec3(rgb.red(), rgb.green(), rgb.blue());
        });
        return new Color(mixed.x, mixed.y, mixed.z);
    }
}
