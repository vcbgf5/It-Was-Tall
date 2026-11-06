package com.itwastall.entity;

import com.itwastall.ItWasTall;
import com.itwastall.config.ItWasTallConfig;
import com.itwastall.director.Director;
import com.itwastall.net.ModNetworking;
import com.itwastall.registry.ModSounds;
import com.itwastall.world.JournalLog;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * THE ONE WHO COUNTS. At night, alone out in the open, you hear it: "ten". Then "nine". With every
 * number a figure stands a little closer between the trees. At "zero" it is there — a jumpscare (and on
 * the hunting stages the real It may follow). Two ways out, both written in the book THE COUNT:
 * get under a roof before zero, or stand still (turn your back to it) until it is done counting.
 */
public final class TheCount {
    private static final String[] WORDS = {"zero.", "one.", "two.", "three.", "four.", "five.", "six.", "seven.", "eight.", "nine.", "ten."};
    private static final int STEP_TICKS = 30;

    private static final class Count {
        int number = 10;
        long nextAt;
        Vec3d anchor;           // where the player stood when the counting started
        boolean moved;
    }

    private static final Map<UUID, Count> ACTIVE = new HashMap<>();
    private static final Map<UUID, Long> NEXT = new HashMap<>();

    private TheCount() {
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(TheCount::tick);
    }

    public static void forget(UUID player) {
        ACTIVE.remove(player);
        NEXT.remove(player);
    }

    private static boolean eligible(ServerPlayerEntity p) {
        if (p.isCreative() || p.isSpectator() || !p.isAlive()) return false;
        World w = p.getWorld();
        if (w.getRegistryKey() != World.OVERWORLD || !w.isNight()) return false;
        if (MineEvents.isUnderground(p) || ShelterCheck.isSheltered(w, p.getBlockPos())) return false;
        // alone: no other player within 32 blocks
        for (ServerPlayerEntity o : p.getServerWorld().getPlayers()) if (o != p && o.squaredDistanceTo(p) < 32 * 32) return false;
        return true;
    }

    private static void tick(MinecraftServer server) {
        long now = server.getOverworld().getTime();
        // running counts
        for (UUID uuid : new ArrayList<>(ACTIVE.keySet())) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(uuid);
            Count c = ACTIVE.get(uuid);
            if (p == null || !p.isAlive() || p.getWorld().getRegistryKey() != World.OVERWORLD) {
                ACTIVE.remove(uuid);
                continue;
            }
            if (p.getPos().squaredDistanceTo(c.anchor) > 1.5 * 1.5) c.moved = true;
            if (ShelterCheck.isSheltered(p.getWorld(), p.getBlockPos())) {
                ACTIVE.remove(uuid);
                ModNetworking.sendScareText(p, "...");
                JournalLog.add(p, "Something was counting. I made it under a roof. It stopped.");
                com.itwastall.advancement.Achievements.grant(p, "counted");
                if (Director.phase() == 1) Director.addTension(-2.0);
                continue;
            }
            if (now < c.nextAt) continue;
            c.nextAt = now + STEP_TICKS;
            c.number--;
            if (c.number > 0) step(p, c);
            else finish(p, c);
        }
        // starting new ones (one check every 10 s)
        if (server.getTicks() % 200 != 0) return;
        ItWasTallConfig.Values cfg = ItWasTallConfig.INSTANCE;
        if (!cfg.countEvents || Director.phase() != 1 || Director.stageIndex() < cfg.countMinStage) return;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (ACTIVE.containsKey(p.getUuid())) continue;
            Long next = NEXT.get(p.getUuid());
            if (next != null && now < next) continue;
            if (!eligible(p) || !EventGate.ready(p)) continue;
            if (p.getRandom().nextDouble() >= MathHelper.clamp(cfg.countChance, 0.0, 1.0)) continue;
            start(p);
        }
    }

    /** Cancels a running count (test command restarts it). */
    public static void stop(ServerPlayerEntity p) {
        ACTIVE.remove(p.getUuid());
    }

    /** Starts the count (also /itwastall event count). */
    public static boolean start(ServerPlayerEntity p) {
        if (ACTIVE.containsKey(p.getUuid())) return false;
        Count c = new Count();
        long now = p.getServerWorld().getServer().getOverworld().getTime();
        c.nextAt = now + STEP_TICKS;
        c.anchor = p.getPos();
        ACTIVE.put(p.getUuid(), c);
        NEXT.put(p.getUuid(), now + 20L * 60 * (15 + p.getRandom().nextInt(11)));
        EventGate.mark(p);
        say(p, c, 10);
        ItWasTall.LOGGER.info("The One Who Counts started for {}.", p.getName().getString());
        return true;
    }

    /** One number: the word, a whisper and the figure a few blocks closer. */
    private static void step(ServerPlayerEntity p, Count c) {
        say(p, c, c.number);
    }

    private static void say(ServerPlayerEntity p, Count c, int number) {
        ModNetworking.sendScareText(p, WORDS[number]);
        Random r = p.getRandom();
        // 10 -> about 36 blocks away, 1 -> about 5
        int dist = Math.max(4, 3 + number * 3 + r.nextInt(3));
        BlockPos spot = FalsePresences.findSpot(p, r, dist, dist + 3);
        if (spot != null) {
            FalsePresences.sendFigure(p, spot, STEP_TICKS + 6, false);
            MineEvents.playTo(p, ModSounds.AMBIENT_WHISPER, Vec3d.ofCenter(spot).add(0, 1.5, 0), 1.0f, 0.75f + (10 - number) * 0.03f);
        } else {
            MineEvents.playTo(p, ModSounds.AMBIENT_WHISPER, p.getEyePos().add(p.getRotationVec(1f).multiply(-4)), 1.0f, 0.8f);
        }
    }

    private static void finish(ServerPlayerEntity p, Count c) {
        ACTIVE.remove(p.getUuid());
        if (!c.moved) {
            // you stood still: it walked right past you
            ModNetworking.sendScareText(p, "...miss.");
            MineEvents.playTo(p, ModSounds.SCARE_BREATH, p.getEyePos().add(p.getRotationVec(1f).multiply(-1.2)), 1.0f, 0.9f);
            JournalLog.add(p, "It counted down to zero. I didn't move. It walked right past me.");
            com.itwastall.advancement.Achievements.grant(p, "counted");
            return;
        }
        ModNetworking.sendScareText(p, WORDS[0]);
        ModNetworking.sendJumpscare(p);
        JournalLog.add(p, "It counted to zero. I was still out in the open.");
        if (Director.phase() == 1) Director.addTension(4.0);
        // on the hunting stages the real thing may come after the zero
        if (Director.stageIndex() >= 4 && p.getRandom().nextBoolean()
                && HauntDirector.countActive(p.getServerWorld().getServer()) < Math.max(1, ItWasTallConfig.INSTANCE.maxActiveIts)) {
            HauntDirector.spawnStare(p);
        }
    }

    public static String debugLine(UUID player, long now) {
        Count c = ACTIVE.get(player);
        if (c != null) return "counting: " + c.number + (c.moved ? " (moved)" : " (still)");
        Long next = NEXT.get(player);
        return next == null || now >= next ? "can count" : "next in " + (next - now) / 20 + " s";
    }
}
