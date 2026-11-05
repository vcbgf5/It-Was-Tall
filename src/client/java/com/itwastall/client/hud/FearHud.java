package com.itwastall.client.hud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.MathHelper;

/** Subtle fear vignette. The synced tension value is supplied by the client networking layer. */
public final class FearHud {
    private static float intensity;
    private static boolean enabled = true;

    private FearHud() {}

    public static void setIntensity(float value) {
        intensity = MathHelper.clamp(value, 0f, 1f);
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        if (!enabled || intensity <= 0.01f) return;
        int sw = ctx.getScaledWindowWidth();
        int sh = ctx.getScaledWindowHeight();
        // soft radial vignette; above 70 % fear it pulses like a heartbeat
        float a = intensity * 0.55f;
        if (intensity > 0.7f) {
            double t = (System.nanoTime() / 1_000_000_000.0) * (1.0 + intensity); // faster when afraid
            double beat = Math.pow(Math.max(0, Math.sin(t * Math.PI * 2)), 8);
            a *= (float) (1.0 + 0.35 * beat);
        }
        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, 0, 900);
        Vignette.draw(ctx, sw, sh, a);
        ctx.getMatrices().pop();
    }
}
