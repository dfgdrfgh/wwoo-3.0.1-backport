package com.zachminecraft.wwoocontent;

import com.mojang.blaze3d.systems.RenderSystem;

/** Tint the existing cloud mesh for a single draw without altering other rendering. */
public final class CloudTint {
    private CloudTint() {}

    public static void draw(CloudBlend.Color color, Runnable draw) {
        // getShaderColor exposes a shared array: copy it before setShaderColor mutates it.
        float[] previous = RenderSystem.getShaderColor().clone();
        RenderSystem.setShaderColor(previous[0] * (float)color.red(),
            previous[1] * (float)color.green(), previous[2] * (float)color.blue(),
            previous[3] * (float)(color.alpha() / 0.8));
        try {
            draw.run();
        } finally {
            RenderSystem.setShaderColor(previous[0], previous[1], previous[2], previous[3]);
        }
    }
}
