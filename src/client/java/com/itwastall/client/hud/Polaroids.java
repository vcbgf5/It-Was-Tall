package com.itwastall.client.hud;

import com.itwastall.ItWasTall;
import com.itwastall.client.god.GodClientState;
import com.itwastall.config.ItWasTallConfig;
import com.itwastall.entity.CarcassEntity;
import com.itwastall.entity.DeepOneEntity;
import com.itwastall.entity.HangedEntity;
import com.itwastall.entity.OldOneEntity;
import com.itwastall.entity.TallOneEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * POLAROIDS: the first time you really see something (It, the hanged one, a carcass, the Deep One, the Old One —
 * in front of you, in clear sight), the moment is "photographed": the picture on your screen, without the HUD or
 * your hand, is taken a few frames later, faded to old instant-film colours, and pinned to the left side of the
 * screen on a little white card with a handwritten line. The photo develops from black, stays a while, slides away.
 */
public final class Polaroids {
    private static final Identifier TEX = ItWasTall.id("polaroid/last");
    private static final int PW = 76, PH = 80, IW = 68, IH = 52, SW = IW * 2, SH = IH * 2;
    private static final long SLIDE = 400, SHOW = 7000, DEVELOP = 2000, GAP_MS = 90_000;
    private static final Set<UUID> SHOT = new HashSet<>();
    private static String pendingCaption;
    private static int pendingDelay = -1, scan;
    private static boolean captureNow;
    private static long nextAllowed, shownAt = -1;
    private static String caption = "", sub = "";
    private static boolean hasTexture;

    private Polaroids() {}

    static void reset() {
        SHOT.clear();
        pendingCaption = null;
        pendingDelay = -1;
        captureNow = false;
        shownAt = -1;
        nextAllowed = 0;
    }

    private static boolean enabled(MinecraftClient client) {
        return ItWasTallConfig.INSTANCE.hudExtras && FearHud.isEnabled() && !GodClientState.god
                && client.player != null && !client.player.isSpectator();
    }

    /** Every client tick: is something worth a photo in clear sight right now? */
    public static void tick(MinecraftClient client) {
        if (client.world == null || client.player == null) return;
        if (pendingCaption != null) {
            if (--pendingDelay <= 0) captureNow = true;   // a few frames later: the thing is properly on screen
            return;
        }
        if (++scan % 5 != 0 || !enabled(client) || Util.getMeasuringTimeMs() < nextAllowed) return;
        Vec3d eye = client.player.getEyePos(), look = client.player.getRotationVec(1f);
        for (Entity e : client.world.getEntities()) {
            String what = captionFor(e);
            if (what == null || SHOT.contains(e.getUuid())) continue;
            Vec3d to = e.getBoundingBox().getCenter().subtract(eye);
            double d = to.length();
            if (d < 3 || d > 48 || to.normalize().dotProduct(look) < 0.95 || !client.player.canSee(e)) continue;
            SHOT.add(e.getUuid());
            pendingCaption = what;
            pendingDelay = 4;
            nextAllowed = Util.getMeasuringTimeMs() + GAP_MS;
            return;
        }
    }

    private static String captionFor(Entity e) {
        if (e instanceof TallOneEntity) return "IT WAS THERE.";
        if (e instanceof HangedEntity) return "WHO WAS HE?";
        if (e instanceof CarcassEntity) return "WHAT DID THIS?";
        if (e instanceof DeepOneEntity) return "LOOK UP.";
        if (e instanceof OldOneEntity) return "IT WAS OLD.";
        return null;
    }

    /** At the end of the world render (no HUD, no hand yet): take the photo if one is due. */
    public static void captureIfDue(MinecraftClient client) {
        if (!captureNow) return;
        captureNow = false;
        String what = pendingCaption;
        pendingCaption = null;
        if (client.world == null || what == null) return;
        try {
            Framebuffer fb = client.getFramebuffer();
            NativeImage shot = ScreenshotRecorder.takeScreenshot(fb);
            int w = shot.getWidth(), h = shot.getHeight();
            int ch = Math.round(h * 0.62f), cw = Math.min(w, Math.round(ch * (float) IW / IH));
            NativeImage small = new NativeImage(SW, SH, false);
            shot.resizeSubRectTo((w - cw) / 2, (h - ch) / 2, cw, ch, small);
            shot.close();
            develop(small);
            if (hasTexture) client.getTextureManager().destroyTexture(TEX);
            client.getTextureManager().registerTexture(TEX, new NativeImageBackedTexture(small));
            hasTexture = true;
            caption = what;
            long time = client.world.getTimeOfDay();
            long t = Math.floorMod(time, 24000L);
            sub = (t >= 13000 && t < 23000 ? "night " : "day ") + (time / 24000L + 1);
            shownAt = Util.getMeasuringTimeMs();
        } catch (RuntimeException ex) {
            ItWasTall.LOGGER.debug("Polaroid failed: {}", ex.toString());
        }
    }

    /** Old instant film: faded, warm, lifted blacks, a soft dark vignette. Pixels are ABGR. */
    private static void develop(NativeImage img) {
        for (int y = 0; y < SH; y++) {
            for (int x = 0; x < SW; x++) {
                int c = img.getColor(x, y);
                float r = c & 0xFF, g = (c >> 8) & 0xFF, b = (c >> 16) & 0xFF;
                float lum = 0.3f * r + 0.59f * g + 0.11f * b;
                r = (r + (lum - r) * 0.35f) * 1.06f + 10;
                g = (g + (lum - g) * 0.35f) + 4;
                b = (b + (lum - b) * 0.35f) * 0.84f;
                float dx = (x - SW / 2f) / (SW / 2f), dy = (y - SH / 2f) / (SH / 2f);
                float v = 1f - 0.45f * MathHelper.clamp(dx * dx + dy * dy - 0.25f, 0f, 1f);
                r = 16 + r * 0.86f * v;
                g = 14 + g * 0.86f * v;
                b = 12 + b * 0.86f * v;
                img.setColor(x, y, 0xFF000000 | (clamp(b) << 16) | (clamp(g) << 8) | clamp(r));
            }
        }
    }

    private static int clamp(float v) {
        return MathHelper.clamp(Math.round(v), 0, 255);
    }

    public static void render(DrawContext ctx) {
        if (shownAt < 0 || !hasTexture) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden) return;
        long el = Util.getMeasuringTimeMs() - shownAt;
        if (el > SHOW + 2 * SLIDE) {
            shownAt = -1;
            return;
        }
        float in = MathHelper.clamp(el / (float) SLIDE, 0f, 1f), out = MathHelper.clamp((el - SHOW - SLIDE) / (float) SLIDE, 0f, 1f);
        float slide = (1f - (1f - in) * (1f - in)) - out * out;
        float px = 6 - (PW + 14) * (1f - slide), py = 52;
        TextRenderer tr = client.textRenderer;
        var m = ctx.getMatrices();
        m.push();
        m.translate(px + PW / 2f, py + PH / 2f, 200);
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-5f));
        m.translate(-PW / 2f, -PH / 2f, 0);
        ctx.fill(3, 3, PW + 3, PH + 3, 0x66000000);                     // its shadow on the screen
        ctx.fill(0, 0, PW, PH, 0xFFECE4D2);                             // the white card
        ctx.drawTexture(TEX, 4, 4, IW, IH, 0, 0, SW, SH, SW, SH);
        float dev = 1f - MathHelper.clamp(el / (float) DEVELOP, 0f, 1f);  // it develops from black
        if (dev > 0) ctx.fill(4, 4, 4 + IW, 4 + IH, (Math.round(dev * dev * 235) << 24) | 0x0A0806);
        ctx.fill(3, 3, 5 + IW, 4, 0xFFBEB4A0);
        ctx.fill(3, 4 + IH, 5 + IW, 5 + IH, 0xFFBEB4A0);
        ctx.fill(3, 4, 4, 4 + IH, 0xFFBEB4A0);
        ctx.fill(4 + IW, 4, 5 + IW, 4 + IH, 0xFFBEB4A0);
        int cw = tr.getWidth(caption);
        float cs = cw > PW - 10 ? (PW - 10) / (float) cw : 1f;           // long lines are written smaller
        m.push();
        m.translate(5, 61, 0);
        m.scale(cs, cs, 1f);
        ctx.drawText(tr, caption, 0, 0, 0xFF28201A, false);
        m.pop();
        ctx.drawText(tr, sub, 5, 70, 0xFF7A2A1E, false);
        ctx.fill(PW / 2 - 2, -2, PW / 2 + 2, 2, 0xFFA01812);              // the pin
        ctx.fill(PW / 2 - 1, -1, PW / 2, 0, 0xFFE6786E);
        m.pop();
    }
}
