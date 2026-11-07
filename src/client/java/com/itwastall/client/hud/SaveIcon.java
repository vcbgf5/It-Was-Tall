package com.itwastall.client.hud;

import com.itwastall.ItWasTall;
import com.itwastall.config.ItWasTallConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

/**
 * Bottom-right corner: the game was saved — a quill writes four lines on a sheet of parchment and signs it with a
 * drop of blood (8 frames in one second), the sheet stays a moment and fades away.
 */
public final class SaveIcon {
    private static final Identifier[] FRAMES = new Identifier[8];
    private static final long WRITE = 1000, HOLD = 1500, FADE = 600;
    private static long startMs = -1;

    static {
        for (int i = 0; i < 8; i++) FRAMES[i] = ItWasTall.id("hud/save_" + i);
    }

    private SaveIcon() {}

    public static void trigger() {
        long now = Util.getMeasuringTimeMs();
        // a second save while it is still showing just keeps the sheet up (no restart of the writing)
        if (startMs >= 0 && now - startMs < WRITE + HOLD) startMs = Math.max(startMs, now - WRITE);
        else startMs = now;
    }

    static void reset() {
        startMs = -1;
    }

    public static void render(DrawContext ctx) {
        if (startMs < 0) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden || !ItWasTallConfig.INSTANCE.hudExtras) return;
        long el = Util.getMeasuringTimeMs() - startMs;
        if (el > WRITE + HOLD + FADE) {
            startMs = -1;
            return;
        }
        float alpha = el < WRITE + HOLD ? Math.min(1f, el / 150f) : 1f - (el - WRITE - HOLD) / (float) FADE;
        int frame = (int) Math.min(7, el * 8 / WRITE);
        int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
        int x = w - 24, y = h - 24;
        RenderSystem.enableBlend();
        HudExtras.box(ctx, x, y, 20, alpha);
        ctx.setShaderColor(1f, 1f, 1f, alpha);
        ctx.drawGuiTexture(FRAMES[frame], x + 2, y + 2, 16, 16);
        ctx.setShaderColor(1f, 1f, 1f, 1f);
    }
}
