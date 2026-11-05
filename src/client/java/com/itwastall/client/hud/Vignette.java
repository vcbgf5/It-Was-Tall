package com.itwastall.client.hud;

import com.itwastall.ItWasTall;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * Smooth radial vignette from {@code textures/gui/vignette.png} (dark red-black edges, clear centre),
 * stretched over the whole screen. Shared by the fear HUD, the screen messages, IT JOINED and the
 * journal / note screens, so every dark edge in the mod looks the same (no boxy gradients).
 */
public final class Vignette {
    private static final Identifier TEX = ItWasTall.id("textures/gui/vignette.png");

    private Vignette() {
    }

    /** @param alpha 0..1 strength */
    public static void draw(DrawContext ctx, int width, int height, float alpha) {
        alpha = MathHelper.clamp(alpha, 0f, 1f);
        if (alpha <= 0.003f) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        ctx.setShaderColor(1f, 1f, 1f, alpha);
        ctx.drawTexture(TEX, 0, 0, width, height, 0f, 0f, 256, 256, 256, 256);
        ctx.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }
}
