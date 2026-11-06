package com.itwastall.client.hud;

import com.itwastall.ItWasTall;
import com.itwastall.config.ItWasTallConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;

/**
 * The menu background of It Was Tall: the night clearing with the Tall One at the edge of the forest (blurred,
 * only the eyes sharp), slowly "breathing" in and out, under a vignette. Used by the title screen and by every
 * other screen outside a world (world list, create world, options, multiplayer, loading), so the whole menu
 * looks the same.
 */
public final class MenuBackground {
    public static final Identifier TEXTURE = ItWasTall.id("textures/gui/title_background.png");

    private MenuBackground() {}

    public static boolean enabled() {
        return ItWasTallConfig.INSTANCE.customTitleScreen;
    }

    public static void draw(DrawContext context, int width, int height, float alpha) {
        double t = Util.getMeasuringTimeMs() / 1000.0;
        float zoom = 1.04f + 0.02f * MathHelper.sin((float) (t * 0.25));
        float s = Math.max(width / 1920f, height / 1080f) * zoom;
        int bw = Math.round(1920 * s), bh = Math.round(1080 * s);
        context.fill(0, 0, width, height, 0xFF000000);
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, MathHelper.clamp(alpha, 0f, 1f));
        context.drawTexture(TEXTURE, (width - bw) / 2, (height - bh) / 2, bw, bh, 0, 0, 1920, 1080, 1920, 1080);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
        Vignette.draw(context, width, height, 0.6f * MathHelper.clamp(alpha, 0f, 1f));
    }
}
