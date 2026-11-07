package com.itwastall.client.hud;

import com.itwastall.ItWasTall;
import com.itwastall.client.JournalData;
import com.itwastall.config.ItWasTallConfig;
import com.itwastall.entity.TallOneEntity;
import com.itwastall.net.HudInfoPayload;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * The extra HUD, in the mod's own style (pixel icons from tools/gen_hud_icons.py, one shared leather frame):
 * <ul>
 *   <li>THE HEARTBEAT: an anatomical heart left of the health bar that contracts with every beat — faster with
 *   the tension of the night, much faster with It near (dark when racing, BPM from 100);</li>
 *   <li>top-right, in frames: the STAGE (It a little closer in each icon), the CLOCK (sun / moon and the time
 *   until dusk or dawn, red in the last 30 s), the COMPASS (a needle pointing to your bed, with the distance),
 *   and the SHELTER (a house whose window lights up while you are inside).</li>
 * </ul>
 * Everything slides in from the edge and fades out; the shelter's window fades on like a lantern.
 */
public final class HudExtras {
    private static final Identifier FRAME = ItWasTall.id("hud/frame");
    private static final Identifier SUN = ItWasTall.id("hud/sun"), MOON = ItWasTall.id("hud/moon");
    private static final Identifier SHELTER_ON = ItWasTall.id("hud/shelter_on"), SHELTER_OFF = ItWasTall.id("hud/shelter_off");
    private static final Identifier NEEDLE = ItWasTall.id("hud/needle");
    private static final Identifier[] HEART = {ItWasTall.id("hud/heart_0"), ItWasTall.id("hud/heart_1"), ItWasTall.id("hud/heart_2")};
    private static final Identifier[] STAGE = new Identifier[6];
    private static final long DUSK = 12500, DAWN = 23500;

    static {
        for (int i = 0; i < 6; i++) STAGE[i] = ItWasTall.id("hud/stage_" + i);
    }

    private static boolean hasBase, inside;
    private static BlockPos base = BlockPos.ORIGIN;
    private static float beatPhase;
    private static long lastFrame;
    // slide-in / fade states (0 hidden .. 1 shown)
    private static float showStage, showClock, showCompass, showShelter, lit;

    private HudExtras() {}

    public static void update(HudInfoPayload p) {
        hasBase = p.hasBase();
        base = p.base();
        inside = p.inside();
        WatchHud.update(p.watch(), p.itId());
    }

    public static void reset() {
        WatchHud.reset();
        NightTitle.reset();
        SaveIcon.reset();
        Polaroids.reset();
        hasBase = false;
        inside = false;
        showStage = showClock = showCompass = showShelter = lit = 0;
    }

    private static float approach(float v, boolean target, float dt, float speed) {
        return MathHelper.clamp(v + (target ? dt : -dt) * speed, 0f, 1f);
    }

    public static void render(DrawContext ctx) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null || client.options.hudHidden || !ItWasTallConfig.INSTANCE.hudExtras) return;
        int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
        TextRenderer tr = client.textRenderer;
        long now = Util.getMeasuringTimeMs();
        float dt = lastFrame == 0 ? 0 : Math.min(0.2f, (now - lastFrame) / 1000f);
        lastFrame = now;
        boolean calm = ItWasTallConfig.INSTANCE.photosensitiveMode;
        RenderSystem.enableBlend();

        // ---- the heartbeat (only where the health bar is shown)
        if (!player.isCreative() && !player.isSpectator()) {
            double near = 0;
            for (var e : client.world.getEntities()) {
                if (e instanceof TallOneEntity) {
                    double d = e.distanceTo(player);
                    if (d < 48) near = Math.max(near, (48 - d) / 48.0);
                }
            }
            float bpm = (float) (62 + JournalData.tension() * 0.55 + near * 80);
            beatPhase = (beatPhase + dt * bpm / 60f) % 1f;
            float beat = calm ? 0 : (float) Math.pow(Math.max(0, Math.sin(beatPhase * Math.PI * 2)), 12);
            int frame = beat > 0.4f ? 1 : bpm >= 120 ? 2 : 0;
            float s = 0.75f * (1f + 0.18f * beat);
            int hx = w / 2 - 91 - 14, hy = h - 40;
            ctx.getMatrices().push();
            ctx.getMatrices().translate(hx + 6f, hy + 6f, 0);
            ctx.getMatrices().scale(s, s, 1f);
            ctx.drawGuiTexture(HEART[frame], -8, -8, 16, 16);
            ctx.getMatrices().pop();
            if (bpm >= 100) {
                String txt = Integer.toString(Math.round(bpm));
                ctx.drawTextWithShadow(tr, txt, hx - 2 - tr.getWidth(txt), hy + 2, bpm >= 130 ? 0xFFE0281E : 0xFFB4A890);
            }
        }

        // ---- top-right cluster, right to left, below the vanilla potion icons
        boolean good = false, bad = false;
        for (var effect : player.getStatusEffects()) {
            if (!effect.shouldShowIcon()) continue;
            if (effect.getEffectType().value().isBeneficial()) good = true;
            else bad = true;
        }
        int x = w - 4, y = 4 + (good ? 26 : 0) + (bad ? 26 : 0);
        long t = client.world.getTimeOfDay() % 24000L;
        boolean night = t >= DUSK && t < DAWN;
        boolean overworld = client.world.getRegistryKey() == net.minecraft.world.World.OVERWORLD;
        double dist = hasBase ? Math.sqrt(player.getBlockPos().getSquaredDistance(base)) : 0;

        showShelter = approach(showShelter, !player.isSpectator(), dt, 4f);
        lit = approach(lit, inside, dt, 2.5f);
        showCompass = approach(showCompass, hasBase && overworld && dist > 16, dt, 4f);
        showClock = approach(showClock, true, dt, 3f);
        showStage = approach(showStage, JournalData.phase() > 0 || JournalData.stage() > 0, dt, 2f);

        // shelter: a house whose window lights up
        if (showShelter > 0) {
            x -= 20;
            int sx = slide(x, showShelter);
            box(ctx, sx, y, 20, showShelter);
            alpha(ctx, showShelter);
            ctx.drawGuiTexture(SHELTER_OFF, sx + 2, y + 2, 16, 16);
            alpha(ctx, showShelter * lit);
            ctx.drawGuiTexture(SHELTER_ON, sx + 2, y + 2, 16, 16);
            alpha(ctx, 1f);
            x -= 2;
        }
        // compass to the bed
        if (showCompass > 0) {
            String d = Long.toString(Math.round(dist));
            int bw = 20 + tr.getWidth(d) + 2;
            x -= bw;
            int cx = slide(x, showCompass);
            box(ctx, cx, y, bw, showCompass);
            float target = (float) (MathHelper.atan2(base.getZ() + 0.5 - player.getZ(), base.getX() + 0.5 - player.getX()) * MathHelper.DEGREES_PER_RADIAN) - 90f;
            float rel = MathHelper.wrapDegrees(target - player.getYaw());
            alpha(ctx, showCompass);
            ctx.getMatrices().push();
            ctx.getMatrices().translate(cx + 10f, y + 10f, 0);
            ctx.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rel));
            ctx.drawGuiTexture(NEEDLE, -8, -8, 16, 16);
            ctx.getMatrices().pop();
            alpha(ctx, 1f);
            boolean danger = dist > 64 && (night || t > 11000);
            ctx.drawTextWithShadow(tr, d, cx + 19, y + 6, fade(danger ? 0xE0281E : 0xE8D9B8, showCompass));
            x -= 2;
        }
        // clock until dusk / dawn
        long left = night ? DAWN - t : (t < DUSK ? DUSK - t : DUSK + 24000 - t);
        int secs = (int) (left / 20);
        String clock = (secs / 60) + ":" + String.format("%02d", secs % 60);
        int bw = 20 + tr.getWidth(clock) + 2;
        x -= bw;
        int kx = slide(x, showClock);
        box(ctx, kx, y, bw, showClock);
        alpha(ctx, showClock);
        ctx.drawGuiTexture(night ? MOON : SUN, kx + 2, y + 2, 16, 16);
        alpha(ctx, 1f);
        boolean pulse = left < 600 && !calm && (now / 400) % 2 == 0;
        ctx.drawTextWithShadow(tr, clock, kx + 19, y + 6, fade(left < 600 ? (pulse ? 0xE0281E : 0x8A1A14) : 0xE8D9B8, showClock));
        x -= 2;
        // the stage: It a little closer in each icon
        if (showStage > 0) {
            x -= 20;
            int px = slide(x, showStage);
            box(ctx, px, y, 20, showStage);
            alpha(ctx, showStage);
            int st = MathHelper.clamp(JournalData.phase() == 0 ? 0 : JournalData.stage(), 0, 5);
            ctx.drawGuiTexture(STAGE[st], px + 2, y + 2, 16, 16);
            alpha(ctx, 1f);
        }
        RenderSystem.disableBlend();
    }

    /** The shared leather frame of every HUD piece. */
    static void box(DrawContext ctx, int x, int y, int width, float alpha) {
        HudExtras.alpha(ctx, alpha);
        ctx.drawGuiTexture(FRAME, x, y, width, 20);
        HudExtras.alpha(ctx, 1f);
    }

    /** Pieces slide in from the right edge (30 px) as they appear. */
    private static int slide(int x, float shown) {
        float e = 1f - (1f - shown) * (1f - shown);
        return Math.round(x + (1f - e) * 30f);
    }

    private static void alpha(DrawContext ctx, float a) {
        ctx.setShaderColor(1f, 1f, 1f, MathHelper.clamp(a, 0f, 1f));
    }

    private static int fade(int rgb, float a) {
        return (MathHelper.clamp(Math.round(a * 255), 8, 255) << 24) | rgb;
    }
}
