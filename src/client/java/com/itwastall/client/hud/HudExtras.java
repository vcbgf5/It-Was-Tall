package com.itwastall.client.hud;

import com.itwastall.ItWasTall;
import com.itwastall.client.JournalData;
import com.itwastall.config.ItWasTallConfig;
import com.itwastall.entity.TallOneEntity;
import com.itwastall.net.HudInfoPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;

/**
 * Small HUD pieces:
 * <ul>
 *   <li>THE HEARTBEAT: a heart left of the health bar that beats with your pulse — faster with the tension of the
 *   night and much faster when It is near;</li>
 *   <li>in the top-right corner: a sun / moon with the time left until dusk (or dawn), pulsing red in the last
 *   30 seconds; a house with the distance to your base (red when night comes and you are far); and a little roof
 *   that is lit while you count as inside (base or under a roof).</li>
 * </ul>
 */
public final class HudExtras {
    private static final Identifier HEART = Identifier.ofVanilla("hud/heart/full");
    private static final Identifier SUN = ItWasTall.id("textures/gui/hud/sun.png");
    private static final Identifier MOON = ItWasTall.id("textures/gui/hud/moon.png");
    private static final Identifier HOUSE = ItWasTall.id("textures/gui/hud/house.png");
    private static final Identifier ROOF = ItWasTall.id("textures/gui/hud/roof.png");
    private static final long DUSK = 12500, DAWN = 23500;

    private static boolean hasBase, inside;
    private static BlockPos base = BlockPos.ORIGIN;
    private static float beatPhase;
    private static long lastFrame;

    private HudExtras() {}

    public static void update(HudInfoPayload p) {
        hasBase = p.hasBase();
        base = p.base();
        inside = p.inside();
    }

    public static void reset() {
        hasBase = false;
        inside = false;
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
            boolean calm = ItWasTallConfig.INSTANCE.photosensitiveMode;
            float beat = calm ? 0 : (float) Math.pow(Math.max(0, Math.sin(beatPhase * Math.PI * 2)), 12);
            float s = 1f + 0.35f * beat;
            int hx = w / 2 - 91 - 13, hy = h - 39;
            ctx.getMatrices().push();
            ctx.getMatrices().translate(hx + 4.5f, hy + 4.5f, 0);
            ctx.getMatrices().scale(s, s, 1f);
            ctx.drawGuiTexture(HEART, -4, -4, 9, 9);
            ctx.getMatrices().pop();
            if (bpm >= 100) {
                String txt = Integer.toString(Math.round(bpm));
                ctx.drawTextWithShadow(tr, txt, hx - 2 - tr.getWidth(txt), hy + 1, bpm >= 130 ? 0xFFE0281E : 0xFFB4A890);
            }
        }

        // ---- top-right: shelter, base distance, time until dusk / dawn (right to left)
        // below the vanilla potion icons (they take the top-right corner: good effects in one row, bad in the next)
        boolean good = false, bad = false;
        for (var effect : player.getStatusEffects()) {
            if (!effect.shouldShowIcon()) continue;
            if (effect.getEffectType().value().isBeneficial()) good = true;
            else bad = true;
        }
        int x = w - 4, y = 4 + (good ? 26 : 0) + (bad ? 26 : 0);
        if (!player.isSpectator()) {
            x -= 11;
            ctx.fill(x - 1, y - 1, x + 10, y + 10, 0x90100806);
            if (inside) ctx.drawTexture(ROOF, x, y, 0, 0, 9, 9, 9, 9);
            else {
                com.mojang.blaze3d.systems.RenderSystem.enableBlend();
                ctx.setShaderColor(1f, 1f, 1f, 0.3f);
                ctx.drawTexture(ROOF, x, y, 0, 0, 9, 9, 9, 9);
                ctx.setShaderColor(1f, 1f, 1f, 1f);
            }
        }
        long t = client.world.getTimeOfDay() % 24000L;
        boolean night = t >= DUSK && t < DAWN;
        if (hasBase && client.world.getRegistryKey() == net.minecraft.world.World.OVERWORLD) {
            double dist = Math.sqrt(player.getBlockPos().getSquaredDistance(base));
            if (dist > 16) {
                String d = Math.round(dist) + "";
                boolean danger = dist > 64 && (night || t > 11000);
                x -= tr.getWidth(d) + 15;
                ctx.fill(x - 2, y - 1, x + tr.getWidth(d) + 12, y + 10, 0x90100806);
                ctx.drawTexture(HOUSE, x, y, 0, 0, 9, 9, 9, 9);
                ctx.drawTextWithShadow(tr, d, x + 11, y + 1, danger ? 0xFFE0281E : 0xFFE8D9B8);
            }
        }
        long left = night ? DAWN - t : (t < DUSK ? DUSK - t : DUSK + 24000 - t);
        int secs = (int) (left / 20);
        String clock = (secs / 60) + ":" + String.format("%02d", secs % 60);
        boolean pulse = left < 600 && !ItWasTallConfig.INSTANCE.photosensitiveMode && (now / 400) % 2 == 0;
        x -= tr.getWidth(clock) + 15;
        ctx.fill(x - 2, y - 1, x + tr.getWidth(clock) + 12, y + 10, 0x90100806);
        ctx.drawTexture(night ? MOON : SUN, x, y, 0, 0, 9, 9, 9, 9);
        int color = left < 600 ? (pulse ? 0xFFE0281E : 0xFF8A1A14) : 0xFFE8D9B8;
        ctx.drawTextWithShadow(tr, clock, x + 11, y + 1, color);
    }
}
