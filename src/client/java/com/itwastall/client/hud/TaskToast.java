package com.itwastall.client.hud;

import com.itwastall.net.TaskToastPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.util.math.MathHelper;

/** Small, non-blocking notification used by task and objective systems (MODPLAN §16). */
public final class TaskToast {
    private static final Identifier TEX = com.itwastall.ItWasTall.id("textures/gui/toast.png");
    private static String title = "";
    private static String message = "";
    private static long untilNanos;
    private static final long DISPLAY_NANOS = 3_500_000_000L;

    /** Accent bar colour of the current toast: new = tall red, done = green, failed = bright red. */
    private static int accent = 0xA33B35;

    private static final int ACCENT_NEW = 0xA33B35;
    private static final int ACCENT_DONE = 0x3AA35A;
    private static final int ACCENT_FAILED = 0xB02020;

    private TaskToast() {}

    public static void show(String newTitle, String newMessage) {
        show(TaskToastPayload.KIND_NEW, newTitle, newMessage);
    }

    /** Kind-aware entry point for the server's TaskToastPayload (§15: new / completed / failed). */
    public static void show(int kind, String newTitle, String newMessage) {
        title = newTitle == null ? "" : newTitle;
        message = newMessage == null ? "" : newMessage;
        accent = switch (kind) {
            case TaskToastPayload.KIND_COMPLETED -> ACCENT_DONE;
            case TaskToastPayload.KIND_FAILED -> ACCENT_FAILED;
            default -> ACCENT_NEW;
        };
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
        // slides in from the right edge
        float slide = 1f - Math.min(1f, (DISPLAY_NANOS - remaining) / 220_000_000f);
        int alpha = MathHelper.clamp((int) (fade * 255), 5, 255);
        var tr = MinecraftClient.getInstance().textRenderer;
        int sw = ctx.getScaledWindowWidth();
        int left = sw - 228 + Math.round(slide * 60);
        int top = 24;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, 0, 1800);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        ctx.setShaderColor(1f, 1f, 1f, fade);
        ctx.drawTexture(TEX, left, top, 0, 0, 220, 44, 256, 64);
        ctx.setShaderColor(1f, 1f, 1f, 1f);
        // a smear of colour on the left edge: red = new, green = done, bright red = failed
        ctx.fill(left + 4, top + 6, left + 7, top + 38, (Math.round(fade * 220) << 24) | accent);
        ctx.drawText(tr, title, left + 14, top + 8, (alpha << 24) | 0xE8D9B8, true);
        java.util.List<OrderedText> lines = tr.wrapLines(Text.literal(message), 196);
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            ctx.drawText(tr, lines.get(i), left + 14, top + 21 + i * 10, (alpha << 24) | 0xB4A890, false);
        }
        RenderSystem.disableBlend();
        ctx.getMatrices().pop();
    }
}
