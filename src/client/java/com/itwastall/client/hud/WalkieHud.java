package com.itwastall.client.hud;

import com.itwastall.ItWasTall;
import com.itwastall.net.WalkiePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;

/**
 * THE WALKIE-TALKIE slot: an extra slot to the right of the hotbar that every player has. Select it with key 0 or
 * by scrolling past the last (or before the first) hotbar slot; while it is selected the right mouse button is
 * the talk button (your item is not used). TX shows while you talk, RX and a name while someone else does.
 */
public final class WalkieHud {
    private static final Identifier ICON = ItWasTall.id("textures/gui/walkie.png");

    public static boolean selected;
    private static boolean transmitting;
    /** Voice chat on this server (null = not told yet). */
    private static Boolean available;
    private static String rxName = "";
    private static long rxSince;

    private WalkieHud() {}

    public static void select(boolean value) {
        if (selected == value) return;
        selected = value;
        if (!value) setTransmitting(false);
    }

    public static void onServerMessage(WalkiePayload p) {
        if (p.name().isEmpty()) {
            available = p.active();
            return;
        }
        if (p.active()) {
            rxName = p.name();
            rxSince = Util.getMeasuringTimeMs();
        } else if (p.name().equals(rxName)) {
            rxName = "";
        }
    }

    public static void reset() {
        selected = false;
        transmitting = false;
        available = null;
        rxName = "";
    }

    /** Called every client tick: number keys leave the slot, the use key talks while it is selected. */
    public static void tick(MinecraftClient client) {
        if (client.player == null) return;
        for (var key : client.options.hotbarKeys) {
            if (key.isPressed()) select(false);
        }
        boolean talk = selected && client.currentScreen == null && client.options.useKey.isPressed() && !client.player.isSpectator();
        setTransmitting(talk);
        if (!rxName.isEmpty() && Util.getMeasuringTimeMs() - rxSince > 60_000) rxName = "";
    }

    private static void setTransmitting(boolean value) {
        if (transmitting == value) return;
        transmitting = value;
        if (ClientPlayNetworking.canSend(WalkiePayload.ID)) ClientPlayNetworking.send(new WalkiePayload("", value));
    }

    public static void render(DrawContext ctx) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden || client.player.isSpectator()) return;
        int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
        // the slot in the HUD's own leather frame, right of the hotbar; selected = a blood-red border
        int x = w / 2 + 91 + 6, y = h - 22, sw = 22;
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        HudExtras.box(ctx, x, y, sw, 1f);
        ctx.drawTexture(ICON, x + 3, y + 2, 0, 0, 16, 16, 16, 16);
        if (selected) {
            int c = 0xFFE0281E;
            ctx.fill(x, y, x + sw, y + 1, c);
            ctx.fill(x, y + 19, x + sw, y + 20, c);
            ctx.fill(x, y, x + 1, y + 20, c);
            ctx.fill(x + sw - 1, y, x + sw, y + 20, c);
        }
        TextRenderer tr = client.textRenderer;
        x -= 6;                                                  // labels keep their old place next to the slot
        long t = Util.getMeasuringTimeMs();
        if (transmitting) {
            ctx.drawTextWithShadow(tr, "TX", x + 31, y + 3, (t / 400) % 2 == 0 ? 0xFFE0281E : 0xFF8A1A14);
            for (int i = 0; i < 5; i++) {
                int lvl = 1 + (int) (5 * Math.abs(MathHelper.sin(t / 80f + i * 1.3f)));
                ctx.fill(x + 31 + i * 3, y + 20 - lvl, x + 33 + i * 3, y + 20, 0xFFD8B080);
            }
        } else if (!rxName.isEmpty()) {
            ctx.drawTextWithShadow(tr, "RX " + rxName, x + 31, y + 8, 0xFF7ED07E);
        } else if (selected) {
            String hint = Boolean.FALSE.equals(available) ? "needs Simple Voice Chat" : "hold right click to talk";
            ctx.drawTextWithShadow(tr, hint, x + 31, y + 8, 0xFF9C8E78);
        }
    }
}
