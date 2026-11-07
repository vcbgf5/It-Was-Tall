package com.itwastall.client.hud;

import com.itwastall.ItWasTall;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * Red chase overlay: while It is chasing THIS player, a blood-red rim with dark veins
 * ({@code textures/gui/chase_vignette.png}) closes in from the screen edges and beats like a
 * racing heart, with a faint red tint over everything. Fades in fast (~0.3 s) when the chase
 * starts and fades out slowly (~1.5 s) after it ends, so it never just snaps off.
 * Follows the journal's "fear effects" toggle ({@link FearHud#isEnabled()}).
 */
public final class ChaseHud {
    private static final Identifier TEX = ItWasTall.id("textures/gui/chase_vignette.png");

    private static boolean active;
    private static float level;
    private static long lastNanos;
    private static long startNanos;

    private ChaseHud() {
    }

    public static void setActive(boolean value) {
        if (value && !active) startNanos = System.nanoTime();
        active = value;
    }

    public static boolean isActive() {
        return active;
    }

    public static void reset() {
        active = false;
        level = 0f;
    }

    public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0f : Math.min(0.1f, (now - lastNanos) / 1_000_000_000f);
        lastNanos = now;
        // fast in, slow out
        level = active ? Math.min(1f, level + dt / 0.3f) : Math.max(0f, level - dt / 1.5f);
        if (level <= 0.001f || !FearHud.isEnabled()) return;

        int sw = ctx.getScaledWindowWidth();
        int sh = ctx.getScaledWindowHeight();
        // heartbeat: a double "lub-dub" about 2.2 times a second
        double t = (now - startNanos) / 1_000_000_000.0 * 2.2;
        double phase = t - Math.floor(t);
        double beat = Math.exp(-Math.pow((phase - 0.08) / 0.05, 2)) + 0.6 * Math.exp(-Math.pow((phase - 0.30) / 0.05, 2));
        // photosensitive mode: a steady, softer red without the beat
        float pulse = com.itwastall.config.ItWasTallConfig.INSTANCE.photosensitiveMode ? 0.6f : (float) (0.72 + 0.28 * beat);

        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, 0, 950);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // faint red tint over the whole picture
        int tint = MathHelper.clamp(Math.round(level * pulse * 34), 0, 255);
        ctx.fill(0, 0, sw, sh, (tint << 24) | 0x5A0000);
        // the veined red rim, brighter on every beat
        ctx.setShaderColor(1f, 1f, 1f, MathHelper.clamp(level * pulse, 0f, 1f));
        ctx.drawTexture(TEX, 0, 0, sw, sh, 0f, 0f, 256, 256, 256, 256);
        ctx.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
        ctx.getMatrices().pop();
    }
}
