package com.itwastall.entity;

import com.itwastall.registry.ModItems;
import com.itwastall.registry.ModSounds;
import com.itwastall.registry.ModVoices;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * THE RADIO. Right-click to switch it on (it glows) or off. While it is on and in your inventory it hisses with
 * static, and every minute or so a voice breaks through: "Do not go outside tonight.", "It knows your name." —
 * or it reads out where you are standing, digit by digit. On the day before a blood moon it warns you.
 */
public final class Radio {
    private static final class Broadcast {
        final Deque<long[]> clips = new ArrayDeque<>();   // {at tick, clip index}
        final List<SoundEvent> sounds = new ArrayList<>();
    }

    private static final Map<UUID, Long> NEXT = new HashMap<>();
    private static final Map<UUID, Broadcast> PLAYING = new HashMap<>();

    private Radio() {}

    public static class RadioItem extends Item {
        public RadioItem(Settings settings) {
            super(settings);
        }

        @Override
        public TypedActionResult<ItemStack> use(World world, net.minecraft.entity.player.PlayerEntity user, Hand hand) {
            ItemStack stack = user.getStackInHand(hand);
            if (!world.isClient) {
                boolean on = !isOn(stack);
                stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, on);
                world.playSound(null, user.getBlockPos(), SoundEvents.BLOCK_LEVER_CLICK, net.minecraft.sound.SoundCategory.PLAYERS, 0.6f, on ? 1.2f : 0.8f);
                user.sendMessage(Text.literal(on ? "The radio crackles to life." : "The radio goes quiet.").formatted(Formatting.GRAY, Formatting.ITALIC), true);
                if (on && user instanceof ServerPlayerEntity sp) NEXT.put(sp.getUuid(), sp.getServerWorld().getTime() + 20L * (15 + sp.getRandom().nextInt(20)));
            }
            return TypedActionResult.success(stack, world.isClient);
        }
    }

    public static boolean isOn(ItemStack stack) {
        return Boolean.TRUE.equals(stack.get(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE));
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(Radio::tick);
    }

    public static void forget(UUID player) {
        NEXT.remove(player);
        PLAYING.remove(player);
    }

    private static boolean carriesOnRadio(ServerPlayerEntity p) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (s.isOf(ModItems.RADIO) && isOn(s)) return true;
        }
        return false;
    }

    private static void tick(MinecraftServer server) {
        long tick = server.getOverworld().getTime();
        // clips of a running broadcast, each at its time
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            Broadcast b = PLAYING.get(p.getUuid());
            if (b == null) continue;
            while (!b.clips.isEmpty() && b.clips.peekFirst()[0] <= tick) {
                long[] c = b.clips.pollFirst();
                MineEvents.playTo(p, b.sounds.get((int) c[1]), p.getEyePos(), ModSounds.vol(1.0f), 1.0f);
            }
            if (b.clips.isEmpty()) PLAYING.remove(p.getUuid());
        }
        if (server.getTicks() % 20 != 0) return;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (!p.isAlive() || p.isSpectator() || !carriesOnRadio(p)) continue;
            // the hiss, every 3 seconds (the clip is 3 s long)
            if (server.getTicks() % 60 == 0 && !PLAYING.containsKey(p.getUuid())) {
                MineEvents.playTo(p, ModVoices.radio("static"), p.getEyePos(), ModSounds.vol(0.25f), 1.0f);
            }
            Long next = NEXT.get(p.getUuid());
            if (next == null) {
                NEXT.put(p.getUuid(), tick + 20L * (20 + p.getRandom().nextInt(20)));
                continue;
            }
            if (tick < next || PLAYING.containsKey(p.getUuid())) continue;
            NEXT.put(p.getUuid(), tick + 20L * (45 + p.getRandom().nextInt(50)));
            broadcast(p, tick);
        }
    }

    /** One broadcast: a warning, the blood moon, or the player's own position read out. */
    public static void broadcast(ServerPlayerEntity p, long tick) {
        Broadcast b = new Broadcast();
        var r = p.getRandom();
        if (BloodMoon.comingTonight(p.getServer()) && !p.getServerWorld().isNight()) {
            add(b, tick + 10, "red_moon");
            add(b, tick + 70, "repeat");
            add(b, tick + 100, "red_moon");
        } else if (r.nextInt(10) < 3) {
            long at = tick + 10;
            at = add(b, at, "position") + 32;
            at = number(b, at, p.getBlockX());
            at = add(b, at + 6, "z") + 12;
            number(b, at, p.getBlockZ());
        } else {
            String line = ModVoices.RADIO_LINES[r.nextInt(ModVoices.RADIO_LINES.length)];
            add(b, tick + 10, line);
            if (r.nextBoolean()) {
                add(b, tick + 90, "repeat");
                add(b, tick + 115, line);
            }
        }
        PLAYING.put(p.getUuid(), b);
    }

    private static long add(Broadcast b, long at, String key) {
        SoundEvent s = ModVoices.radio(key);
        if (s == null) return at;
        b.sounds.add(s);
        b.clips.addLast(new long[]{at, b.sounds.size() - 1});
        return at;
    }

    /** "x" and the number digit by digit ("minus one four two"). */
    private static long number(Broadcast b, long at, int value) {
        // "x" is said before the first number only (the second one follows "z")
        if (b.sounds.stream().noneMatch(s -> s == ModVoices.radio("x"))) at = add(b, at, "x") + 12;
        if (value < 0) at = add(b, at, "minus") + 14;
        for (char ch : Integer.toString(Math.abs(value)).toCharArray()) at = add(b, at, "d" + ch) + 13;
        return at;
    }
}
