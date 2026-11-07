package com.itwastall.client.hud;

import com.itwastall.ItWasTall;
import com.itwastall.config.ItWasTallConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;

/**
 * Top centre of the screen, in the HUD's leather frame:
 * <ul>
 *   <li>THE EYE — opens slowly while It is watching you (even when you cannot see it), blinks now and then, and
 *   trembles wide open when it is about to come for you;</li>
 *   <li>during a chase, in its place: IT IS COMING and a torn red bar that shrinks as It closes in.</li>
 * </ul>
 */
public final class WatchHud {
    private static final Identifier[] EYE = new Identifier[6];
    private static final float CHASE_RANGE = 32f;

    static {
        for (int i = 0; i < 6; i++) EYE[i] = ItWasTall.id("hud/eye_" + i);
    }

    private static float target, level, show, showChase, barFrac = 1f;
    private static int itId = -1;
    private static long lastFrame, nextBlink, blinkUntil;

    private WatchHud() {}

    static void update(float watch, int id) {
        target = watch;
        itId = id;
    }

    static void reset() {
        target = level = show = showChase = 0;
        barFrac = 1f;
        itId = -1;
    }

    public static void render(DrawContext ctx) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.options.hudHidden || !ItWasTallConfig.INSTANCE.hudExtras) return;
        long now = Util.getMeasuringTimeMs();
        float dt = lastFrame == 0 ? 0 : Math.min(0.2f, (now - lastFrame) / 1000f);
        lastFrame = now;
        boolean calm = ItWasTallConfig.INSTANCE.photosensitiveMode;
        Entity it = itId >= 0 ? client.world.getEntityById(itId) : null;
        boolean chase = ChaseHud.isActive() && it != null;
        boolean watching = !chase && target > 0.01f;
        level += (target - level) * Math.min(1f, dt * 3f);
        show = MathHelper.clamp(show + (watching ? dt * 3f : -dt * 2f), 0f, 1f);
        showChase = MathHelper.clamp(showChase + (chase ? dt * 5f : -dt * 2f), 0f, 1f);
        int w = ctx.getScaledWindowWidth();
        RenderSystem.enableBlend();

        if (show > 0.001f) {
            // frame 1..5 by how hard it watches; a quick blink every few seconds (not when it is about to move)
            int frame = 1 + Math.round(MathHelper.clamp(level, 0f, 1f) * 4);
            if (now > nextBlink) {
                blinkUntil = now + 130;
                nextBlink = now + 2500 + (long) (Math.random() * 3500);
            }
            if (now < blinkUntil && frame < 5) frame = 0;
            int bw = 30, x = w / 2 - bw / 2, y = Math.round(4 - 24 * (1f - ease(show)));
            int jx = frame == 5 && !calm ? Math.round(MathHelper.sin(now / 25f)) : 0;
            HudExtras.box(ctx, x, y, bw, show);
            ctx.setShaderColor(1f, 1f, 1f, show);
            ctx.drawGuiTexture(EYE[frame], x + 3 + jx, y + 3, 24, 14);
            ctx.setShaderColor(1f, 1f, 1f, 1f);
        }

        if (showChase > 0.001f) {
            if (it != null) {
                float d = (float) Math.sqrt(it.squaredDistanceTo(client.player));
                barFrac += (MathHelper.clamp(d / CHASE_RANGE, 0f, 1f) - barFrac) * Math.min(1f, dt * 6f);
            }
            int bw = Math.min(128, Math.max(70, w - 2 * 140)), x = w / 2 - bw / 2;
            int y = Math.round(4 - 28 * (1f - ease(showChase)));
            int a = Math.round(showChase * 255) << 24;
            HudExtras.box(ctx, x, y, bw, showChase);
            ctx.getMatrices().push();
            ctx.getMatrices().translate(0, 0, 1);
            boolean blink = calm || (now / 300) % 2 == 0;
            String title = "IT IS COMING";
            ctx.drawTextWithShadow(client.textRenderer, title, w / 2 - client.textRenderer.getWidth(title) / 2, y + 3,
                    a | (blink ? 0xE0281E : 0x9A1A14));
            int bx0 = x + 6, bx1 = x + bw - 6, by = y + 12;
            ctx.fill(bx0, by, bx1, by + 4, a | 0x1A0A08);
            int f = bx0 + Math.round((bx1 - bx0) * barFrac);
            if (f > bx0) {
                ctx.fill(bx0, by, f, by + 4, a | 0xA01812);
                // a torn edge: dark nicks along the top and bottom that crawl with time
                long seed = calm ? 7 : now / 120;
                for (int i = bx0; i < f; i += 3) {
                    long hsh = (i * 341873128712L + seed * 132897987541L) >>> 33;
                    if (hsh % 5 < 2) ctx.fill(i, by, i + 1, by + 1, a | 0x1A0A08);
                    if (hsh % 7 < 2) ctx.fill(i, by + 3, i + 1, by + 4, a | 0x1A0A08);
                }
                ctx.fill(f - 1, by - 1, f + 1, by + 5, a | 0xE0281E);
            }
            ctx.getMatrices().pop();
        }
    }

    private static float ease(float t) {
        return 1f - (1f - t) * (1f - t);
    }
}
