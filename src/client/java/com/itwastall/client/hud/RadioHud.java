package com.itwastall.client.hud;

import com.itwastall.entity.Radio;
import com.itwastall.registry.ModItems;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;

/**
 * The radio next to the hotbar (only while you carry one): the radio itself, and beside it OFF, a flicker of
 * static bars (switched on) or a red ON AIR with a jumping level meter (a broadcast is coming through).
 */
public final class RadioHud {
    private static boolean onAir;
    private static long onAirSince;

    private RadioHud() {}

    public static void setOnAir(boolean value) {
        onAir = value;
        onAirSince = Util.getMeasuringTimeMs();
    }

    public static void clear() {
        onAir = false;
    }

    public static void render(DrawContext ctx) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden || client.player.isSpectator()) return;
        if (onAir && Util.getMeasuringTimeMs() - onAirSince > 30_000) onAir = false;   // never stuck on air
        ItemStack radio = ItemStack.EMPTY;
        PlayerInventory inv = client.player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            if (inv.getStack(i).isOf(ModItems.RADIO)) {
                radio = inv.getStack(i);
                break;
            }
        }
        if (radio.isEmpty()) return;
        boolean on = Radio.isOn(radio);
        int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
        int x = w / 2 - 91 - 29 - 56, y = h - 22;          // left of the hotbar (and of the off-hand slot)
        TextRenderer tr = client.textRenderer;
        ctx.fill(x - 2, y - 1, x + 52, y + 21, 0x90100806);
        ctx.fill(x - 2, y - 1, x + 52, y, onAir ? 0xFFA0241B : 0xFF3A2418);
        ctx.drawItem(radio, x, y + 2);
        long t = Util.getMeasuringTimeMs();
        if (onAir) {
            boolean blink = (t / 500) % 2 == 0 || com.itwastall.config.ItWasTallConfig.INSTANCE.photosensitiveMode;
            ctx.drawTextWithShadow(tr, "ON AIR", x + 18, y + 2, blink ? 0xFFE0281E : 0xFF7A1A14);
            for (int i = 0; i < 6; i++) {                     // the level meter of the voice
                int lvl = 2 + (int) (6 * Math.abs(MathHelper.sin((t / 90f) + i * 1.7f) * MathHelper.sin(t / 230f + i)));
                ctx.fill(x + 18 + i * 4, y + 19 - lvl, x + 21 + i * 4, y + 19, 0xFFD8B080);
            }
        } else if (on) {
            ctx.drawTextWithShadow(tr, "ON", x + 18, y + 2, 0xFFB4A890);
            java.util.Random r = new java.util.Random(t / 80);
            for (int i = 0; i < 6; i++) {                     // static: random flicker
                int lvl = 1 + r.nextInt(4);
                ctx.fill(x + 18 + i * 4, y + 19 - lvl, x + 21 + i * 4, y + 19, 0xFF6E6458);
            }
        } else {
            ctx.drawTextWithShadow(tr, "OFF", x + 18, y + 7, 0xFF5F5446);
        }
    }
}
