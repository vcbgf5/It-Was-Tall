package com.itwastall.client.god;

import com.itwastall.net.GodActionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * The god's own client: is this player the god, are they walking as It, and who is being hunted. While It,
 * a player you keep your eyes on for ~0.6 s becomes your prey (the server starts the chase); the prey is
 * outlined in red for you alone (OutlineMixin).
 */
public final class GodClientState {
    public static boolean god;
    public static boolean itForm;
    public static int lockedId = -1;
    private static int candidateId = -1, candidateTicks;

    private GodClientState() {}

    public static void reset() {
        god = false;
        itForm = false;
        lockedId = -1;
        candidateId = -1;
        candidateTicks = 0;
    }

    public static boolean isLocked(Entity e) {
        return itForm && lockedId >= 0 && e.getId() == lockedId;
    }

    public static void tick(MinecraftClient client) {
        if (!god || !itForm || client.player == null || client.world == null || client.currentScreen != null) {
            candidateTicks = 0;
            return;
        }
        PlayerEntity looked = lookedAt(client);
        int id = looked == null ? -1 : looked.getId();
        if (id != candidateId) {
            candidateId = id;
            candidateTicks = 0;
            return;
        }
        if (id >= 0 && id != lockedId && ++candidateTicks == 12 && ClientPlayNetworking.canSend(GodActionPayload.ID)) {
            ClientPlayNetworking.send(new GodActionPayload("lock", looked.getGameProfile().getName()));
        }
    }

    @Nullable
    private static PlayerEntity lookedAt(MinecraftClient client) {
        PlayerEntity me = client.player;
        Vec3d eye = me.getEyePos(), look = me.getRotationVec(1f);
        PlayerEntity best = null;
        double bestDot = 0.985;
        for (PlayerEntity p : client.world.getPlayers()) {
            if (p == me || p.isSpectator()) continue;
            Vec3d to = new Vec3d(p.getX(), p.getBodyY(0.6), p.getZ()).subtract(eye);
            double dist = to.length();
            if (dist > 96 || dist < 0.5) continue;
            double dot = look.dotProduct(to.multiply(1.0 / dist));
            if (dot > bestDot && me.canSee(p)) {
                bestDot = dot;
                best = p;
            }
        }
        return best;
    }

    /**
     * IT'S EYES: while the god walks as It the screen is how It sees — a dark blood-red cast, a heavy vignette that
     * breathes, film grain, and a faint red pulse at the edges while it hunts someone (night vision comes from the
     * server). Steady, without grain, in photosensitive mode.
     */
    public static void renderVision(DrawContext ctx) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!god || !itForm || client.options.hudHidden) return;
        int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
        boolean calm = com.itwastall.config.ItWasTallConfig.INSTANCE.photosensitiveMode;
        double t = net.minecraft.util.Util.getMeasuringTimeMs() / 1000.0;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, 0, -100);
        ctx.fill(0, 0, w, h, 0x3A3A0400);                                   // blood-red cast
        float breath = calm ? 0.85f : 0.78f + 0.12f * (float) Math.sin(t * 1.3);
        com.itwastall.client.hud.Vignette.draw(ctx, w, h, breath);
        com.itwastall.client.hud.Vignette.draw(ctx, w, h, 0.45f);
        if (lockedId >= 0) {                                                  // hunting: the edges pulse red
            float pulse = calm ? 0.25f : (float) Math.pow(Math.max(0, Math.sin(t * 5.0)), 6) * 0.45f;
            int a = (int) (pulse * 255) << 24;
            int edge = Math.max(8, w / 14);
            ctx.fillGradient(0, 0, w, edge, a | 0x8A0000, 0x008A0000);
            ctx.fillGradient(0, h - edge, w, h, 0x008A0000, a | 0x8A0000);
        }
        if (!calm) {                                                          // film grain
            java.util.Random r = new java.util.Random((long) (t * 20));
            for (int i = 0; i < 260; i++) {
                int x = r.nextInt(Math.max(1, w)), y = r.nextInt(Math.max(1, h));
                ctx.fill(x, y, x + 1, y + 1, r.nextBoolean() ? 0x30FFFFFF : 0x40000000);
            }
        }
        ctx.getMatrices().pop();
    }

    /** A line at the bottom of the god's screen: what the keys do right now. */
    public static void renderHint(DrawContext ctx) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!god || client.options.hudHidden || client.currentScreen != null) return;
        TextRenderer tr = client.textRenderer;
        String line;
        if (itForm) {
            String prey = "";
            if (lockedId >= 0 && client.world != null && client.world.getEntityById(lockedId) instanceof PlayerEntity p) {
                prey = "HUNTING " + p.getGameProfile().getName() + "  -  ";
            }
            line = "IT  -  " + prey + "look at a player to hunt  -  hit to catch  -  [H] sink";
        } else {
            line = "GOD  -  [G] panel  -  [H] rise as It where you look";
        }
        int w = tr.getWidth(line);
        int x = (ctx.getScaledWindowWidth() - w) / 2, y = ctx.getScaledWindowHeight() - 64;
        ctx.fill(x - 4, y - 3, x + w + 4, y + 11, 0x90100806);
        ctx.drawTextWithShadow(tr, line, x, y, itForm ? 0xFFE05A4A : 0xFFB4A890);
    }
}
