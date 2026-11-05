package com.itwastall.client.hud;

import com.itwastall.registry.ModSounds;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

/**
 * One-shot "IT JOINED" title when the mod becomes active (server: Director.activatePhase).
 * Big white glowing "IT JOINED" centered, italics "Something has entered your world." below,
 * a subtle vignette, a soft flicker (one milder flashing frame now and then) and the
 * {@code ui.it_joined} sound played once on trigger — like JumpscareOverlay, client-side only.
 * Timed on System.nanoTime() (frames, not ticks) so the hint is exact over the payload duration.
 */
public final class ItJoinedOverlay {
    private static final Text TITLE = Text.literal("IT JOINED");
    private static final Text SUBTITLE = Text.literal("Something has entered your world.")
            .formatted(Formatting.ITALIC);

    private static long startNanos;
    private static long durationNanos = -1;

    private ItJoinedOverlay() {
    }

    public static void trigger(int durationMs) {
        startNanos = System.nanoTime();
        durationNanos = Math.max(1000, durationMs) * 1_000_000L;
        // the low drone (sounds.json maps ui.it_joined) once, when the title appears
        MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(ModSounds.UI_IT_JOINED, 1.0f, ModSounds.vol(0.85f)));
    }

    public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        if (durationNanos < 0) return;
        long elapsed = System.nanoTime() - startNanos;
        if (elapsed >= durationNanos) {
            durationNanos = -1;
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer tr = client.textRenderer;
        int sw = ctx.getScaledWindowWidth();
        int sh = ctx.getScaledWindowHeight();

        float t = elapsed / (float) durationNanos;
        // fade in over the first 12 %, fade out over the last 35 %
        float fade = MathHelper.clamp(t / 0.12f, 0f, 1f) * MathHelper.clamp((1f - t) / 0.35f, 0f, 1f);
        // subtle glow pulse, plus an occasional dip = one milder flashing frame
        float pulse = 0.9f + 0.1f * MathHelper.sin(elapsed / 90_000_000.0f);
        if (Math.random() < 0.05 && !com.itwastall.config.ItWasTallConfig.INSTANCE.photosensitiveMode) pulse *= 0.6f;   // no flashing frame in photosensitive mode
        float alpha = fade * pulse;

        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, 0, 2600);

        // subtle vignette (darker than scare text's, this is meant as an unsettling hint only)
        Vignette.draw(ctx, sw, sh, alpha * 0.85f);

        // bright glowing title centered
        float scale = Math.min(4f, (sw * 0.8f) / Math.max(1, tr.getWidth(TITLE)));
        int titleW = Math.round(tr.getWidth(TITLE) * scale);   // width AFTER scaling
        int titleY = sh / 2 - Math.round(tr.fontHeight * scale);
        int titleX = sw / 2 - titleW / 2;
        drawGlowText(ctx, tr, TITLE.getString(), titleX, titleY, scale, alpha);

        // italic subtitle below
        int subY = titleY + (int) (tr.fontHeight * scale) + 10;
        int subAlpha = Math.max(5, (int) (alpha * 200)) << 24;
        int subW = tr.getWidth(SUBTITLE);
        ctx.drawText(tr, SUBTITLE, sw / 2 - subW / 2, subY, subAlpha | 0x96A0AA, false);

        ctx.getMatrices().pop();
    }

    /** Text with a soft halo: several low-alpha offsets under the main bright copy. */
    private static void drawGlowText(DrawContext ctx, TextRenderer tr, String text, int x, int y, float scale, float alpha) {
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y, 0);
        ctx.getMatrices().scale(scale, scale, 1f);
        int halo = Math.max(4, (int) (alpha * 90)) << 24;
        ctx.drawText(tr, text, -2, 0, halo | 0xFFFFFF, false);
        ctx.drawText(tr, text, 2, 0, halo | 0xFFFFFF, false);
        ctx.drawText(tr, text, 0, -2, halo | 0xFFFFFF, false);
        ctx.drawText(tr, text, 0, 2, halo | 0xFFFFFF, false);
        int a = Math.max(6, (int) (alpha * 255));
        ctx.drawText(tr, text, 0, 0, (a << 24) | 0xFFFFFF, false);
        ctx.getMatrices().pop();
    }
}

