package com.itwastall.client.hud;

import com.itwastall.ItWasTall;
import com.itwastall.net.FlashlightPayload;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;

/**
 * The flashlight on the client: the middle mouse button (outside creative, where it stays pick-block) or the
 * "Flashlight" key switches it; a click; a small framed icon above the walkie-talkie slot while it is on.
 */
public final class FlashlightClient {
    private static final Identifier ON = ItWasTall.id("hud/flashlight_on");
    private static boolean on;
    private static float show;
    private static long lastFrame;

    private FlashlightClient() {}

    public static boolean isOn() {
        return on;
    }

    public static void toggle() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || !ClientPlayNetworking.canSend(FlashlightPayload.ID)) return;
        on = !on;
        ClientPlayNetworking.send(new FlashlightPayload(on));
        client.player.playSound(SoundEvents.BLOCK_LEVER_CLICK, 0.35f, on ? 1.9f : 1.5f);
    }

    public static void reset() {
        on = false;
        show = 0;
    }

    public static void render(DrawContext ctx) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden) return;
        long now = Util.getMeasuringTimeMs();
        float dt = lastFrame == 0 ? 0 : Math.min(0.2f, (now - lastFrame) / 1000f);
        lastFrame = now;
        show = MathHelper.clamp(show + (on ? dt * 5f : -dt * 3f), 0f, 1f);
        if (show <= 0.001f) return;
        int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
        int x = w / 2 + 91 + 6, y = h - 22 - 22;
        RenderSystem.enableBlend();
        HudExtras.box(ctx, x, y, 22, show);
        ctx.setShaderColor(1f, 1f, 1f, show);
        ctx.drawGuiTexture(ON, x + 3, y + 2, 16, 16);
        ctx.setShaderColor(1f, 1f, 1f, 1f);
    }
}
