package com.itwastall.client.hud;

import com.itwastall.ItWasTall;
import com.itwastall.registry.ModSounds;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.util.Identifier;

/** Full-screen jumpscare image shown for a fixed real-time duration (default 100 ms = 0.1 s). */
public final class JumpscareOverlay {
    private static final Identifier TEXTURE = ItWasTall.id("textures/gui/jumpscare.png");
    private static final int TEX_W = 1104;
    private static final int TEX_H = 760;

    /** Even on a very low frame rate the image must actually be seen. */
    private static final int MIN_FRAMES = 2;

    private static long showUntilNanos;
    private static int framesLeft;

    private JumpscareOverlay() {
    }

    public static void trigger(int durationMs, boolean showImage) {
        if (showImage) {
            showUntilNanos = System.nanoTime() + durationMs * 1_000_000L;
            framesLeft = MIN_FRAMES;
        }
        MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(ModSounds.JUMPSCARE, 1.0f, 1.0f));
    }

    public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        if (System.nanoTime() >= showUntilNanos && framesLeft <= 0) return;
        framesLeft--;
        int sw = ctx.getScaledWindowWidth();
        int sh = ctx.getScaledWindowHeight();
        // "cover" fit: fill the screen, keep the aspect ratio, crop the overflow
        float scale = Math.max(sw / (float) TEX_W, sh / (float) TEX_H);
        int w = Math.round(TEX_W * scale);
        int h = Math.round(TEX_H * scale);
        int x = (sw - w) / 2;
        int y = (sh - h) / 2;
        // in front of everything the HUD drew (hotbar items are rendered with a z offset)
        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, 0, 3000);
        ctx.fill(0, 0, sw, sh, 0xFF000000);
        ctx.drawTexture(TEXTURE, x, y, w, h, 0f, 0f, TEX_W, TEX_H, TEX_W, TEX_H);
        ctx.getMatrices().pop();
    }
}
