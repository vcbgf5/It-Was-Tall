package com.itwastall.client.hud;

import com.itwastall.config.ItWasTallConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Util;
import net.minecraft.world.World;

/**
 * At dusk in the Overworld, "NIGHT 3" is typed in the middle of the screen, letter by letter like on a typewriter
 * (a dry click per key, the number in red), stays a moment with a blinking cursor, then fades away.
 */
public final class NightTitle {
    private static final long DUSK = 13000, START_DELAY = 300, PER_CHAR = 150, HOLD = 2600, FADE = 1000;
    private static long prevTime = -1, startMs = -1;
    private static int typed;
    private static String text = "";

    private NightTitle() {}

    static void reset() {
        prevTime = -1;
        startMs = -1;
    }

    /** Every client tick: did the sun just go down? */
    public static void tick(MinecraftClient client) {
        if (client.world == null || client.player == null) {
            prevTime = -1;
            return;
        }
        if (client.world.getRegistryKey() != World.OVERWORLD) {
            prevTime = -1;
            return;
        }
        long time = client.world.getTimeOfDay();
        long t = Math.floorMod(time, 24000L), pt = Math.floorMod(prevTime, 24000L);
        if (prevTime >= 0 && time > prevTime && time - prevTime < 200 && pt < DUSK && t >= DUSK) {
            text = "NIGHT " + (time / 24000L + 1);
            startMs = Util.getMeasuringTimeMs();
            typed = 0;
        }
        prevTime = time;
    }

    public static void render(DrawContext ctx) {
        if (startMs < 0) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden || !ItWasTallConfig.INSTANCE.hudExtras) return;
        long el = Util.getMeasuringTimeMs() - startMs;
        int chars = (int) Math.max(0, Math.min(text.length(), (el - START_DELAY) / PER_CHAR + (el >= START_DELAY ? 1 : 0)));
        while (typed < chars) {                          // one dry key click per letter (not for the space)
            if (text.charAt(typed) != ' ') {
                client.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), 0.35f, 1.7f + (float) Math.random() * 0.3f);
            }
            typed++;
        }
        long typedAt = START_DELAY + text.length() * PER_CHAR;
        float alpha = el < typedAt + HOLD ? 1f : 1f - (el - typedAt - HOLD) / (float) FADE;
        if (alpha <= 0f) {
            startMs = -1;
            return;
        }
        TextRenderer tr = client.textRenderer;
        int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
        float scale = 3f;
        int full = tr.getWidth(text);
        int a = Math.max(4, Math.round(alpha * 255)) << 24;
        int space = text.indexOf(' ');
        String shown = text.substring(0, chars);
        String word = shown.substring(0, Math.min(shown.length(), space));
        String num = shown.length() > space + 1 ? shown.substring(space + 1) : "";
        ctx.getMatrices().push();
        ctx.getMatrices().translate(w / 2f - full * scale / 2f, h / 3f - 4 * scale, 0);
        ctx.getMatrices().scale(scale, scale, 1f);
        ctx.drawTextWithShadow(tr, word, 0, 0, a | 0xD8CBB0);
        int nx = tr.getWidth(text.substring(0, space + 1));
        if (!num.isEmpty()) ctx.drawTextWithShadow(tr, num, nx, 0, a | 0xC8281E);
        boolean cursor = ItWasTallConfig.INSTANCE.photosensitiveMode || (Util.getMeasuringTimeMs() / 400) % 2 == 0;
        if (cursor && alpha >= 1f) ctx.drawTextWithShadow(tr, "_", tr.getWidth(shown) + 1, 0, a | 0xD8CBB0);
        ctx.getMatrices().pop();
    }
}
