package com.itwastall.entity;

import com.itwastall.ItWasTall;
import com.itwastall.config.ItWasTallConfig;
import com.itwastall.director.Director;
import com.itwastall.net.BloodMoonPayload;
import com.itwastall.net.ModNetworking;
import com.itwastall.world.JournalLog;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * THE BLOOD MOON. A rare night (stage 3+) with a red sky, red fog and a red moon. Every event of the mod comes
 * twice as often. It is decided in the morning: that day the radio warns about it and the sun "looks wrong".
 */
public final class BloodMoon {
    private static long decidedDay = -1;
    private static long plannedNight = -1;
    private static boolean active;

    private BloodMoon() {}

    public static boolean active() {
        return active;
    }

    /** During a blood moon the waits between events are halved. */
    public static long scale(long ticks) {
        return active ? ticks / 2 : ticks;
    }

    /** True on the day before a planned blood moon (the radio warns about it). */
    public static boolean comingTonight(MinecraftServer server) {
        return plannedNight == server.getOverworld().getTimeOfDay() / 24000L && !active;
    }

    public static void init() {
        PayloadTypeRegistry.playS2C().register(BloodMoonPayload.ID, BloodMoonPayload.CODEC);
        ServerTickEvents.END_SERVER_TICK.register(BloodMoon::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> send(handler.getPlayer())));
    }

    private static void tick(MinecraftServer server) {
        if (server.getTicks() % 40 != 0) return;
        ServerWorld world = server.getOverworld();
        long time = world.getTimeOfDay();
        long day = time / 24000L, t = time % 24000L;
        ItWasTallConfig.Values cfg = ItWasTallConfig.INSTANCE;
        // in the morning: is tonight a blood moon?
        if (t < 12000 && decidedDay != day) {
            decidedDay = day;
            if (cfg.bloodMoon && Director.phase() == 1 && Director.stageIndex() >= 3
                    && world.getRandom().nextDouble() < cfg.bloodMoonChance) {
                plannedNight = day;
                for (ServerPlayerEntity p : world.getPlayers()) {
                    p.sendMessage(Text.literal("The sun looks wrong today.").formatted(Formatting.DARK_RED, Formatting.ITALIC), true);
                }
                ItWasTall.LOGGER.info("Tonight (day {}) will be a blood moon.", day);
            }
        }
        boolean night = t >= 12800 && t < 23200;
        if (!active && night && plannedNight == day) start(server);
        else if (active && !night) stop(server);
    }

    /** Starts it right now (also /itwastall event bloodmoon). */
    public static void start(MinecraftServer server) {
        active = true;
        plannedNight = server.getOverworld().getTimeOfDay() / 24000L;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            send(p);
            ModNetworking.sendScareText(p, "the moon is red.");
            JournalLog.add(p, "The moon was red. Everything came out that night.");
            com.itwastall.advancement.Achievements.grant(p, "blood_moon");
        }
        ItWasTall.LOGGER.info("The blood moon has risen.");
    }

    public static void stop(MinecraftServer server) {
        active = false;
        plannedNight = -1;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) send(p);
    }

    private static void send(ServerPlayerEntity p) {
        if (ServerPlayNetworking.canSend(p, BloodMoonPayload.ID)) ServerPlayNetworking.send(p, new BloodMoonPayload(active));
    }
}
