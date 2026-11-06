package com.itwastall.client.hud;

import com.itwastall.config.ItWasTallConfig;
import com.itwastall.net.TaskTimerPayload;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;

/**
 * TASK TIMER: while It has given you a task, a small leather plate in the top-left corner shows what It wants,
 * how long is left (m:ss) and a shrinking bar. The last 10 seconds turn red and pulse (steady in photosensitive
 * mode). The server sends the time every second; between packets the clock runs on locally, so it never
 * stutters. It slides in from the left when a task starts and back out when it ends.
 */
public final class TaskTimerHud {
    private static final Identifier TEX = com.itwastall.ItWasTall.id("textures/gui/toast.png");
    private static final int W = 170, H = 40;
    private static final long SLIDE_MS = 250;

    private static String title = "", detail = "";
    private static int left, total;
    private static boolean danger;
    private static long receivedAt;
    private static boolean active;
    /** Time the plate started sliding in / out (0 = never shown). */
    private static long changedAt;

    private TaskTimerHud() {}

    public static void update(TaskTimerPayload p) {
        boolean nowActive = !p.title().isEmpty();
        if (nowActive != active) changedAt = Util.getMeasuringTimeMs();
        active = nowActive;
        if (nowActive) {   // keep the last text while sliding out
            title = p.title();
            detail = p.detail();
            left = p.left();
            total = Math.max(1, p.total());
            danger = p.danger();
            receivedAt = Util.getMeasuringTimeMs();
        }
    }

    public static void clear() {
        active = false;
        changedAt = 0;
        title = detail = "";
    }

    public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (changedAt == 0 || client.options.hudHidden || !ItWasTallConfig.INSTANCE.taskTimerHud) return;
        long now = Util.getMeasuringTimeMs();
        float t = MathHelper.clamp((now - changedAt) / (float) SLIDE_MS, 0f, 1f);
        float shown = active ? t : 1f - t;              // 0 = hidden, 1 = fully in
        if (shown <= 0f) return;
        float ease = 1f - (1f - shown) * (1f - shown);

        float secondsLeft = Math.max(0f, left - (now - receivedAt) / 1000f);
        int whole = (int) Math.ceil(secondsLeft);
        boolean lastTen = whole <= 10;
        boolean red = danger || lastTen;
        boolean calm = ItWasTallConfig.INSTANCE.photosensitiveMode;

        int x = Math.round(6 - (W + 10) * (1f - ease));
        int y = 6;
        TextRenderer tr = client.textRenderer;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, 0, 1700);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        ctx.setShaderColor(1f, 1f, 1f, 0.92f * ease);
        ctx.drawTexture(TEX, x, y, W, H, 0, 0, 220, 44, 256, 64);   // the toast art is the top-left 220x44
        ctx.setShaderColor(1f, 1f, 1f, 1f);
        int alpha = MathHelper.clamp(Math.round(255 * ease), 8, 255) << 24;

        // what It wants
        ctx.drawText(tr, tr.trimToWidth(title, W - 16), x + 8, y + 6, alpha | 0xE8D9B8, true);
        // the clock, big, on the right; the detail line gets whatever room is left of it
        String clock = (whole / 60) + ":" + String.format("%02d", whole % 60);
        int clockRoom = Math.round(tr.getWidth(clock) * 1.5f) + 6;
        ctx.drawText(tr, tr.trimToWidth(detail, W - 16 - clockRoom), x + 8, y + 18, alpha | 0x9C8E78, false);

        float scale = 1.5f;
        if (lastTen && !calm) {
            double pulse = Math.pow(Math.max(0, Math.sin(now / 1000.0 * Math.PI * 2)), 6);
            scale += (float) (0.18 * pulse);
        }
        int clockColor = red ? 0xD83A30 : 0xE8D9B8;
        ctx.getMatrices().push();
        float cw = tr.getWidth(clock) * scale;
        ctx.getMatrices().translate(x + W - 8 - cw, y + 22 - 4 * scale, 0);
        ctx.getMatrices().scale(scale, scale, 1f);
        ctx.drawText(tr, clock, 0, 0, alpha | clockColor, true);
        ctx.getMatrices().pop();

        // the bar: dried ink that shrinks to nothing
        int bx0 = x + 8, bx1 = x + W - 8, by = y + H - 7;
        float frac = MathHelper.clamp(secondsLeft / total, 0f, 1f);
        ctx.fill(bx0, by, bx1, by + 2, alpha | 0x1A100C);
        int fillTo = bx0 + Math.round((bx1 - bx0) * frac);
        if (fillTo > bx0) ctx.fill(bx0, by, fillTo, by + 2, alpha | (red ? 0xA0241B : 0x8F7C62));

        RenderSystem.disableBlend();
        ctx.getMatrices().pop();
    }
}
