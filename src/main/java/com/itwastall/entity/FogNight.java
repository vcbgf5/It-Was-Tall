package com.itwastall.entity;

import com.itwastall.ItWasTall;
import com.itwastall.config.ItWasTallConfig;
import com.itwastall.director.Director;
import com.itwastall.net.FogNightPayload;
import com.itwastall.net.ModNetworking;
import com.itwastall.registry.ModSounds;
import com.itwastall.world.JournalLog;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * FOG NIGHTS. Now and then (stage 2+) the night comes with a thick grey fog: you see a dozen blocks, no more.
 * Heavy footsteps walk around you in it, and It stands at the very edge of what you can see — openly, between the
 * trees. Every song comes out corrupted. Decided at dusk; never on a blood moon.
 */
public final class FogNight {
    private static boolean active;
    private static long decidedNight = -1;
    private static final Map<UUID, Long> NEXT_STEPS = new HashMap<>();
    private static final Map<UUID, Long> NEXT_IT = new HashMap<>();

    private FogNight() {}

    public static boolean active() {
        return active;
    }

    public static void init() {
        PayloadTypeRegistry.playS2C().register(FogNightPayload.ID, FogNightPayload.CODEC);
        ServerTickEvents.END_SERVER_TICK.register(FogNight::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> send(handler.getPlayer())));
    }

    private static void tick(MinecraftServer server) {
        if (server.getTicks() % 20 != 0) return;
        ServerWorld world = server.getOverworld();
        long time = world.getTimeOfDay(), day = time / 24000L, t = time % 24000L;
        boolean night = t >= 12800 && t < 23200;
        ItWasTallConfig.Values cfg = ItWasTallConfig.INSTANCE;
        if (!active && night && decidedNight != day) {
            decidedNight = day;
            if (cfg.fogNights && Director.phase() == 1 && Director.stageIndex() >= 2 && !BloodMoon.active()
                    && world.getRandom().nextDouble() < cfg.fogNightChance) {
                start(server);
            }
        } else if (active && !night) {
            stop(server);
        }
        if (!active) return;
        long now = world.getTime();
        for (ServerPlayerEntity p : world.getPlayers()) {
            if (p.isSpectator() || ModNetworking.scaresDisabled(p.getUuid())) continue;
            Random r = p.getRandom();
            // heavy footsteps somewhere in the fog, for this player only
            Long steps = NEXT_STEPS.get(p.getUuid());
            if (steps == null || now >= steps) {
                NEXT_STEPS.put(p.getUuid(), now + 20L * (25 + r.nextInt(35)));
                double a = r.nextDouble() * Math.PI * 2, d = 10 + r.nextDouble() * 5;
                Vec3d at = new Vec3d(p.getX() + Math.cos(a) * d, p.getY(), p.getZ() + Math.sin(a) * d);
                MineEvents.playTo(p, ModSounds.STEP, at, 1.3f, 0.75f);
            }
            // It at the edge of the fog
            Long it = NEXT_IT.get(p.getUuid());
            if (it == null) {
                NEXT_IT.put(p.getUuid(), now + 20L * (60 + r.nextInt(60)));
            } else if (now >= it && p.getWorld().getRegistryKey() == World.OVERWORLD && !p.isCreative()) {
                NEXT_IT.put(p.getUuid(), now + 20L * (180 + r.nextInt(120)));
                TheBelow.spawnItInSight(p, 12, 17);
            }
        }
    }

    /** Starts it right now (also /itwastall event fog). */
    public static void start(MinecraftServer server) {
        active = true;
        decidedNight = server.getOverworld().getTimeOfDay() / 24000L;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            send(p);
            ModNetworking.sendScareText(p, "the fog is coming.");
            JournalLog.add(p, "A fog came down. I could not see my own hand. Something walked in it all night.");
        }
        ItWasTall.LOGGER.info("A fog night has begun.");
    }

    public static void stop(MinecraftServer server) {
        active = false;
        NEXT_STEPS.clear();
        NEXT_IT.clear();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) send(p);
    }

    private static void send(ServerPlayerEntity p) {
        if (ServerPlayNetworking.canSend(p, FogNightPayload.ID)) ServerPlayNetworking.send(p, new FogNightPayload(active));
    }
}
