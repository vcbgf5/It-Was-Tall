package com.itwastall.client.hud;

import com.itwastall.registry.ModSounds;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.util.math.MathHelper;

import java.util.Random;

/**
 * Big red flickering message in the middle of the screen ("don't hide. just run.").
 * Fades in and out, flickers, sometimes glitches letters and shifts sideways for a few frames.
 */
public final class ScareTextOverlay {
    private static final String GLITCH = "#%&@$?!/\\|*";
    private static final Random RANDOM = new Random();

    private static String text = "";
    private static long startNanos;
    private static long durationNanos;
    private static int glitchFrames;
    private static String glitched = "";
    private static int jitterX;

    private ScareTextOverlay() {
    }

    public static void trigger(String message, int durationMs) {
        text = message;
        startNanos = System.nanoTime();
        durationNanos = Math.max(500, durationMs) * 1_000_000L;
        glitchFrames = 0;
        MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(ModSounds.UI_MESSAGE, 1.0f, 0.9f));
    }

    public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        if (text.isEmpty()) return;
        long elapsed = System.nanoTime() - startNanos;
        if (elapsed >= durationNanos) {
            text = "";
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer tr = client.textRenderer;
        int sw = ctx.getScaledWindowWidth();
        int sh = ctx.getScaledWindowHeight();

        float t = elapsed / (float) durationNanos;
        float fade = MathHelper.clamp(t / 0.12f, 0f, 1f) * MathHelper.clamp((1f - t) / 0.25f, 0f, 1f);
        float flicker = RANDOM.nextFloat() < 0.07f ? 0.15f : 0.75f + RANDOM.nextFloat() * 0.25f;
        float alpha = fade * flicker;

        // glitch: for a few frames swap random letters and shift sideways
        if (glitchFrames <= 0 && RANDOM.nextFloat() < 0.04f) {
            glitchFrames = 2 + RANDOM.nextInt(4);
            StringBuilder sb = new StringBuilder(text);
            for (int i = 0; i < 1 + text.length() / 8; i++) {
                int idx = RANDOM.nextInt(sb.length());
                if (sb.charAt(idx) != ' ') sb.setCharAt(idx, GLITCH.charAt(RANDOM.nextInt(GLITCH.length())));
            }
            glitched = sb.toString();
            jitterX = RANDOM.nextInt(9) - 4;
        }
        String shown = glitchFrames > 0 ? glitched : text;
        int dx = glitchFrames > 0 ? jitterX : 0;
        if (glitchFrames > 0) glitchFrames--;

        float scale = Math.min(4f, (sw * 0.85f) / Math.max(1, tr.getWidth(text)));
        int textW = tr.getWidth(shown);

        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, 0, 2500);
        // dark vignette behind the text
        int dark = (int) (alpha * 110) << 24;
        ctx.fillGradient(0, 0, sw, sh / 2, 0, dark);
        ctx.fillGradient(0, sh / 2, sw, sh, dark, 0);
        Vignette.draw(ctx, sw, sh, alpha * 0.7f);

        ctx.getMatrices().translate(sw / 2f + dx, sh / 2f - 4 * scale, 0);
        ctx.getMatrices().scale(scale, scale, 1f);
        int a = Math.max(5, (int) (alpha * 255));
        // faint ghost copies for a blurry, shaking look
        int ghost = Math.max(5, (int) (alpha * 70)) << 24;
        ctx.drawText(tr, shown, -textW / 2 - 1, 1, ghost | 0x400000, false);
        ctx.drawText(tr, shown, -textW / 2 + 1, -1, ghost | 0x00202A, false);
        ctx.drawText(tr, shown, -textW / 2, 0, (a << 24) | 0xC0140F, false);
        ctx.getMatrices().pop();
    }
}
