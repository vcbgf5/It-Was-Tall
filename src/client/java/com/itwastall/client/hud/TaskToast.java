package com.itwastall.client.hud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.MathHelper;

/** Small, non-blocking notification used by task and objective systems. */
public final class TaskToast {
    private static String title = "";
    private static String message = "";
    private static long untilNanos;
    private static final long DISPLAY_NANOS = 3_500_000_000L;

    private TaskToast() {}

    public static void show(String newTitle, String newMessage) {
        title = newTitle == null ? "" : newTitle;
        message = newMessage == null ? "" : newMessage;
        untilNanos = System.nanoTime() + DISPLAY_NANOS;
    }

    public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        if (untilNanos == 0) return;
        long remaining = untilNanos - System.nanoTime();
        if (remaining <= 0) {
            untilNanos = 0;
            return;
        }
        float fade = Math.min(1f, Math.min((DISPLAY_NANOS - remaining) / 250_000_000f,
                remaining / 450_000_000f));
        int alpha = MathHelper.clamp((int) (fade * 230), 0, 230);
        int sw = ctx.getScaledWindowWidth();
        int left = sw - 224;
        int top = 28;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, 0, 1800);
        ctx.fill(left, top, sw - 12, top + 48, (alpha << 24) | 0x11141B);
        ctx.fill(left, top, left + 3, top + 48, (alpha << 24) | 0xA33B35);
        ctx.drawTextWithShadow(net.minecraft.client.MinecraftClient.getInstance().textRenderer,
                title, left + 11, top + 8, (alpha << 24) | 0xFFFFFF);
        ctx.drawText(net.minecraft.client.MinecraftClient.getInstance().textRenderer,
                message, left + 11, top + 25, (alpha << 24) | 0xC7CBD2, false);
        ctx.getMatrices().pop();
    }
}
