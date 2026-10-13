package com.zachminecraft.wwoocontent;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.joml.Matrix4f;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;

/** Exercise the release's transformed cloud draw hook before any window or world is created. */
public final class CloudRenderProbe implements PreLaunchEntrypoint {
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void near(double actual, double expected, String message) {
        require(Math.abs(actual - expected) < 2e-7, message + ": " + actual + " != " + expected);
    }

    @Override public void onPreLaunch() {
        try {
            check();
            System.out.println("CLOUD_RENDER_PASS: release mixin applied to real client, white-to-gray tint, coherent passes, draw arguments and shader-state restoration");
            System.exit(0);
        } catch (Throwable failure) {
            failure.printStackTrace();
            System.exit(1);
        }
    }

    private static void check() throws Exception {
        // Loading this actual client class applies the release mixin and validates its selectors.
        Class<?> rendererClass = Class.forName("net.minecraft.class_761");
        var hooks = Arrays.stream(rendererClass.getDeclaredMethods())
            .filter(m -> m.getName().contains("wwoo$tintCloudDraw") && m.getParameterCount() == 5
                && m.getParameterTypes()[4] == Operation.class).toList();
        require(hooks.size() == 1, "exactly one transformed draw wrapper");
        var hook = hooks.getFirst(); hook.setAccessible(true);
        var frameColor = rendererClass.getDeclaredField("wwoo$frameColor"); frameColor.setAccessible(true);

        // The real constructor creates GPU buffers. This fixture exercises only the injected hook.
        var unsafeType = Class.forName("sun.misc.Unsafe");
        var singleton = unsafeType.getDeclaredField("theUnsafe"); singleton.setAccessible(true);
        Object renderer = unsafeType.getMethod("allocateInstance", Class.class).invoke(singleton.get(null), rendererClass);
        RenderSystem.initRenderThread();
        float[] originalState = {.7f, .8f, .9f, .65f};
        RenderSystem.setShaderColor(originalState[0], originalState[1], originalState[2], originalState[3]);
        var modelView = new Matrix4f(); var projection = new Matrix4f();
        long start = 10_000_000_000L;
        var blend = new CloudBlend();
        blend.update(0xccffffff, start); blend.update(0x66828282, start);
        double previous = 1;
        for (int frame = 0; frame <= 120; frame++) {
            var color = blend.update(0x66828282, start + frame * CloudBlend.DURATION_NANOS / 120);
            frameColor.set(renderer, color);
            float[][] passes = new float[2][];
            for (int pass = 0; pass < 2; pass++) {
                final int index = pass;
                int[] calls = {0};
                Operation<Void> draw = args -> {
                    calls[0]++;
                    require(args.length == 4 && args[0] == null && args[1] == modelView
                        && args[2] == projection && args[3] == null, "original draw arguments preserved");
                    passes[index] = RenderSystem.getShaderColor().clone();
                    return null;
                };
                hook.invoke(renderer, null, modelView, projection, null, draw);
                require(calls[0] == 1, "each pass invokes its original draw once");
                require(Arrays.equals(RenderSystem.getShaderColor(), originalState), "no tint leaks into later rendering");
            }
            require(Arrays.equals(passes[0], passes[1]), "depth and color passes have identical tint");
            near(passes[0][0], originalState[0] * color.red(), "red uniform");
            near(passes[0][1], originalState[1] * color.green(), "green uniform");
            near(passes[0][2], originalState[2] * color.blue(), "blue uniform");
            near(.8 * passes[0][3], originalState[3] * color.alpha(), "vanilla vertex alpha scaled exactly once");
            double red = passes[0][0] / originalState[0];
            require(red <= previous + 2e-7 && red >= 130.0 / 255 - 2e-7, "white dims toward gray without a brightness flash");
            previous = red;
        }
        near(previous, 130.0 / 255, "exact destination gray");
        RuntimeException sentinel = new RuntimeException("test draw failure");
        Operation<Void> fail = args -> { throw sentinel; };
        try {
            hook.invoke(renderer, null, modelView, projection, null, fail);
            throw new AssertionError("draw exception should propagate");
        } catch (InvocationTargetException expected) {
            require(expected.getCause() == sentinel, "preserve original draw exception");
        }
        require(Arrays.equals(RenderSystem.getShaderColor(), originalState), "restore shader state even after failure");
    }
}
