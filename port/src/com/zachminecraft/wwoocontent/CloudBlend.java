package com.zachminecraft.wwoocontent;

/** A time-based transition to the entered biome's exact cloud color and opacity. */
public final class CloudBlend {
    public static final long DURATION_NANOS = 2_000_000_000L;
    private Color from;
    private Color target;
    private int targetArgb;
    private long started;

    public record Color(double red, double green, double blue, double alpha) {
        public static Color fromArgb(int argb) {
            return new Color(((argb >> 16) & 255) / 255.0, ((argb >> 8) & 255) / 255.0,
                (argb & 255) / 255.0, (argb >>> 24) / 255.0);
        }
        private Color mix(Color other, double amount) {
            return new Color(red + (other.red - red) * amount, green + (other.green - green) * amount,
                blue + (other.blue - blue) * amount, alpha + (other.alpha - alpha) * amount);
        }
    }

    public Color update(int argb, long now) {
        if (target == null) {
            // New worlds start in their own palette, without carrying another world's color.
            target = Color.fromArgb(argb); from = target; targetArgb = argb; started = now;
            return target;
        }
        Color current = current(now);
        if (argb != targetArgb) {
            // Retarget from what is currently visible, even during an unfinished fade.
            from = current; target = Color.fromArgb(argb); targetArgb = argb; started = now;
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
