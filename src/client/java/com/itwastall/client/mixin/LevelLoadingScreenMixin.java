package com.itwastall.client.mixin;

import com.itwastall.client.hud.MenuBackground;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.LevelLoadingScreen;
import net.minecraft.server.WorldGenerationProgressTracker;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * World loading in the It Was Tall style: instead of the coloured chunk square, a leather bar slowly filling
 * with blood, "It is waiting..." above it and one hint from the journal below. The percentage line of vanilla
 * stays, and so does the night-forest background (ScreenMixin).
 */
@Mixin(LevelLoadingScreen.class)
public abstract class LevelLoadingScreenMixin extends Screen {
    @Unique
    private static final int TIPS = 10;
    @Unique
    private int itwastall$tip = -1;
    @Unique
    private float itwastall$shown;

    protected LevelLoadingScreenMixin(Text title) {
        super(title);
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screen/world/LevelLoadingScreen;drawChunkMap(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/server/WorldGenerationProgressTracker;IIII)V"))
    private void itwastall$bloodBar(DrawContext ctx, WorldGenerationProgressTracker tracker, int centerX, int centerY, int pixelSize, int pixelMargin) {
        if (!MenuBackground.enabled()) {
            LevelLoadingScreen.drawChunkMap(ctx, tracker, centerX, centerY, pixelSize, pixelMargin);
            return;
        }
        if (itwastall$tip < 0) itwastall$tip = (int) (Util.getMeasuringTimeMs() / 997 % TIPS);
        float target = MathHelper.clamp(tracker.getProgressPercentage() / 100f, 0f, 1f);
        itwastall$shown += (target - itwastall$shown) * 0.08f;      // the blood creeps, never jumps

        int w = 182, h = 9;
        int x0 = centerX - w / 2, y0 = centerY - h / 2;
        // leather frame
        ctx.fill(x0 - 2, y0 - 2, x0 + w + 2, y0 + h + 2, 0xFF0E0806);
        ctx.fill(x0 - 1, y0 - 1, x0 + w + 1, y0 + h + 1, 0xFF3A2418);
        ctx.fill(x0, y0, x0 + w, y0 + h, 0xFF120A07);
        // blood: dark body, lighter wet top line, a drop that runs at the front
        int fx = x0 + Math.round(w * itwastall$shown);
        if (fx > x0) {
            ctx.fill(x0, y0, fx, y0 + h, 0xFF6E120D);
            ctx.fill(x0, y0, fx, y0 + 2, 0xFFA0241B);
            ctx.fill(x0, y0 + h - 2, fx, y0 + h, 0xFF4A0B08);
            double t = Util.getMeasuringTimeMs() / 1000.0;
            int drip = (int) ((t * 6) % 10);
            ctx.fill(fx - 2, y0 + h, fx, y0 + h + 1 + drip / 3, 0xFF6E120D);
        }
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("It is waiting..."), centerX, y0 - 16, 0xFFE8D9B8);
        Text tip = Text.translatable("itwastall.tip." + itwastall$tip);
        ctx.drawCenteredTextWithShadow(textRenderer, tip, centerX, y0 + h + 38, 0xFF9C8E78);
    }
}
