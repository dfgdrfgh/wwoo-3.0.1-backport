package com.zachminecraft.wwoocontent;

/** Continuous horizontal blending on the biome's four-block sampling grid. */
public final class CloudBlend {
    public static final double RADIUS = 16.0;
    private static final double CELL = 4.0;
    private CloudBlend() {}

    @FunctionalInterface public interface BiomeColors {
        int colorAt(int quartX, int quartZ);
    }

    public record Color(double red, double green, double blue, double alpha) {
        public static Color fromArgb(int argb) {
            return new Color(((argb >> 16) & 255) / 255.0, ((argb >> 8) & 255) / 255.0,
                (argb & 255) / 255.0, (argb >>> 24) / 255.0);
        }
    }

    public static Color sample(double x, double z, BiomeColors source) {
        int minX = (int)Math.ceil((x - RADIUS - CELL / 2) / CELL);
        int maxX = (int)Math.floor((x + RADIUS - CELL / 2) / CELL);
        int minZ = (int)Math.ceil((z - RADIUS - CELL / 2) / CELL);
        int maxZ = (int)Math.floor((z + RADIUS - CELL / 2) / CELL);
        double total = 0, alpha = 0, colorWeight = 0, red = 0, green = 0, blue = 0;
        for (int qx = minX; qx <= maxX; qx++) {
            double wx = weight(qx * CELL + CELL / 2 - x);
            if (wx == 0) continue;
            for (int qz = minZ; qz <= maxZ; qz++) {
                double w = wx * weight(qz * CELL + CELL / 2 - z);
                if (w == 0) continue;
                var color = Color.fromArgb(source.colorAt(qx, qz));
                double opacity = w * color.alpha;
                total += w; alpha += opacity;
                // Visible biome colors receive their spatial weight, independent of opacity.
                // Ignore fully invisible colors so a cloud-free neighbor cannot add a dark fringe.
                if (color.alpha > 0) {
                    colorWeight += w;
                    red += w * color.red; green += w * color.green; blue += w * color.blue;
                }
            }
        }
        return colorWeight > 0 ? new Color(red / colorWeight, green / colorWeight, blue / colorWeight, alpha / total)
            : new Color(1, 1, 1, 0);
    }

    private static double weight(double distance) {
        double t = distance / RADIUS;
        if (Math.abs(t) >= 1) return 0;
        double falloff = 1 - t * t;
        // Both value and slope reach zero when a sample enters/leaves the window.
        double squared = falloff * falloff;
        return squared * squared;
    }
}
