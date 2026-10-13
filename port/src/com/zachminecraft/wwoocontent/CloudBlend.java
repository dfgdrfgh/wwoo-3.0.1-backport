package com.zachminecraft.wwoocontent;

/** Interpolate directly between biome cloud RGB colors, without an opacity pulse. */
public final class CloudBlend {
    public static final long DURATION_NANOS = 2_000_000_000L;
    private Color from;
    private Color target;
    private int targetRgb;
    private long started;

    public record Color(double red, double green, double blue) {
        public static Color fromArgb(int argb) {
            return new Color(((argb >> 16) & 255) / 255.0, ((argb >> 8) & 255) / 255.0,
                (argb & 255) / 255.0);
        }
        private Color mix(Color other, double amount) {
            return new Color(red + (other.red - red) * amount, green + (other.green - green) * amount,
                blue + (other.blue - blue) * amount);
        }
    }

    public Color update(int argb, long now) {
        // Attribute opacity does not alter cloud visibility or restart an RGB transition.
        int rgb = argb & 0xffffff;
        if (target == null) {
            // New worlds start in their own palette, without carrying another world's color.
            target = Color.fromArgb(rgb); from = target; targetRgb = rgb; started = now;
            return target;
        }
        Color current = current(now);
        if (rgb != targetRgb) {
            // Retarget from what is currently visible, even during an unfinished fade.
            from = current; target = Color.fromArgb(rgb); targetRgb = rgb; started = now;
            return current;
        }
        return current;
    }

    private Color current(long now) {
        if (from.equals(target)) return target;
        double progress = Math.max(0, Math.min(1, (now - started) / (double)DURATION_NANOS));
        if (progress == 1) return target;
        double eased = progress * progress * (3 - 2 * progress);
        return from.mix(target, eased);
    }

    public void reset() { from = null; target = null; }
}
