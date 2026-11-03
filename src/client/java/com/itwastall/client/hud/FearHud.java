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
        int alpha = MathHelper.clamp((int) (intensity * 48), 0, 48);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, 0, 900);
        int color = (alpha << 24) | 0x240609;
        ctx.fillGradient(0, 0, sw, sh / 5, color, 0);
        ctx.fillGradient(0, sh * 4 / 5, sw, sh, 0, color);
        ctx.fillGradient(0, 0, sw / 6, sh, color, 0);
        ctx.fillGradient(sw * 5 / 6, 0, sw, sh, 0, color);
        ctx.getMatrices().pop();
    }
}
