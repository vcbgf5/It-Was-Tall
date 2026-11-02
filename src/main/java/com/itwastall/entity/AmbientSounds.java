package com.itwastall.entity;

import com.itwastall.config.ItWasTallConfig;
import com.itwastall.director.Director;
import com.itwastall.registry.ModEntities;
import com.itwastall.registry.ModSounds;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Creepy ambience played only to the player who should hear it. What plays depends on where the player is:
 * underground, outside at night, inside a shelter at night, or in daylight (rare). When It is close,
 * a heartbeat is much more likely.
 */
public final class AmbientSounds {
    private static final Map<UUID, State> STATES = new HashMap<>();

    private static final class State {
        long nextAny;
        long nextDrone;
    }

    private record Weighted(SoundEvent sound, int weight, boolean close) {
    }

    private static final Weighted[] UNDERGROUND = {
            w(ModSounds.AMBIENT_DRONE, 25), w(ModSounds.AMBIENT_CREAK, 15), c(ModSounds.AMBIENT_WHISPER, 15),
            c(ModSounds.AMBIENT_BREATH, 10), w(ModSounds.AMBIENT_DISTANT_SCREAM, 10), w(ModSounds.AMBIENT_KNOCK_DISTANT, 10),
            w(ModSounds.DIG, 10), c(ModSounds.AMBIENT_HEARTBEAT, 5), w(ModSounds.AMBIENT_CAVE_DRIP, 20), w(ModSounds.AMBIENT_ROCK_FALL, 8),
            w(ModSounds.AMBIENT_CHAINS, 6), w(ModSounds.AMBIENT_HUM, 6), c(ModSounds.AMBIENT_REVERSED_VOICE, 5)};
    private static final Weighted[] OUTSIDE_NIGHT = {
            w(ModSounds.AMBIENT_WIND, 30), w(ModSounds.AMBIENT_DISTANT_SCREAM, 20), w(ModSounds.AMBIENT_CREAK, 15),
            c(ModSounds.AMBIENT_WHISPER, 10), w(ModSounds.AMBIENT_DRONE, 10), w(ModSounds.STEP, 10), c(ModSounds.AMBIENT_BREATH, 5),
            w(ModSounds.GROWL, 6), w(ModSounds.AMBIENT_REVERSED_VOICE, 4)};
    private static final Weighted[] SHELTER_NIGHT = {
            w(ModSounds.AMBIENT_CREAK, 30), w(ModSounds.AMBIENT_KNOCK_DISTANT, 25), c(ModSounds.AMBIENT_BREATH, 15),
            c(ModSounds.AMBIENT_WHISPER, 15), w(ModSounds.AMBIENT_DRONE, 10), w(ModSounds.STEP, 5), w(ModSounds.AMBIENT_HUM, 8),
            w(ModSounds.SCRAPE, 6), c(ModSounds.AMBIENT_REVERSED_VOICE, 4)};
    private static final Weighted[] DAY = {
            w(ModSounds.AMBIENT_WIND, 40), w(ModSounds.AMBIENT_CREAK, 30), w(ModSounds.AMBIENT_DRONE, 30)};

    private AmbientSounds() {
    }

    private static Weighted w(SoundEvent s, int weight) {
        return new Weighted(s, weight, false);
    }

    /** "close" sounds are played right behind the player. */
    private static Weighted c(SoundEvent s, int weight) {
        return new Weighted(s, weight, true);
    }

    /** Drops per-player data when a player leaves the server. */
    public static void forget(UUID player) {
        STATES.remove(player);
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(AmbientSounds::tick);
    }

    private static void tick(MinecraftServer server) {
        if (server.getTicks() % 20 != 0) return;
        ItWasTallConfig.Values cfg = ItWasTallConfig.INSTANCE;
        if (!cfg.ambientSounds) return;

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.isSpectator() || !player.isAlive()) continue;
            World world = player.getWorld();
            long now = world.getTime();
            State st = STATES.computeIfAbsent(player.getUuid(), u -> new State());
            if (now < st.nextAny) continue;

            Random random = player.getRandom();
            boolean itNear = !player.getServerWorld()
                    .getEntitiesByType(ModEntities.TALL_ONE, player.getBoundingBox().expand(48), e -> true).isEmpty();
            boolean underground = MineEvents.isUnderground(player);
            boolean night = world.isNight();
            boolean sheltered = ShelterCheck.isSheltered(world, player.getBlockPos());

            double chance = cfg.ambientChancePerSecond;
            Weighted[] table;
            if (underground) table = UNDERGROUND;
            else if (night && sheltered) table = SHELTER_NIGHT;
            else if (night) table = OUTSIDE_NIGHT;
            else {
                table = DAY;
                chance *= 0.25;
            }
            if (itNear) chance *= 2;
            if (random.nextDouble() >= chance) continue;

            // Heartbeat only when It is really close AND from ETAP 3 up (PHASES.md §4: "heartbeat
            // od Stage 3"); in FAZA PRE It doesn't exist yet, so never.
            boolean heartbeatOk = itNear && Director.phase() == 1 && Director.currentStage().ordinal() >= 3;
            Weighted pick = heartbeatOk && random.nextInt(3) == 0 ? c(ModSounds.AMBIENT_HEARTBEAT, 1) : pick(table, random);
            // "close" category is stage-gated exactly like in ScareSounds (PHASES §3): blocked
            // until ETAP 2, 40% from ETAP 2, 60% from ETAP 4; PRE never.
            if (pick.close() && !closeAllowed(random)) pick = pickWide(table, random);
            if (pick.sound() == ModSounds.AMBIENT_DRONE || pick.sound() == ModSounds.AMBIENT_WIND || pick.sound() == ModSounds.AMBIENT_HUM) {
                if (now < st.nextDrone) continue;
                st.nextDrone = now + 20 * 30;
            }
            play(player, pick, random);
            st.nextAny = now + 20 * (6 + random.nextInt(6));
        }
    }

    private static Weighted pick(Weighted[] table, Random random) {
        int total = 0;
        for (Weighted w : table) total += w.weight();
        int r = random.nextInt(total);
        for (Weighted w : table) {
            r -= w.weight();
            if (r < 0) return w;
        }
        return table[0];
    }

    /** Weighted pick restricted to the wide (non-"close") entries — the stage-gated fallback. */
    private static Weighted pickWide(Weighted[] table, Random random) {
        List<Weighted> wide = new ArrayList<>();
        for (Weighted w : table) if (!w.close()) wide.add(w);
        return wide.isEmpty() ? table[0] : pick(wide.toArray(new Weighted[0]), random);
    }

    /**
     * Is the "close" ambience category allowed right now (same stage mapping as ScareSounds,
     * PHASES.md §3: blocked until ETAP 2, 40% from ETAP 2, 60% from ETAP 4; FAZA PRE never).
     */
    private static boolean closeAllowed(Random random) {
        if (Director.phase() == 0) return false;
        return switch (Director.currentStage().ordinal()) {
            case 0, 1 -> false;
            case 2, 3 -> random.nextFloat() < 0.40f;
            default -> random.nextFloat() < 0.60f;
        };
    }

    private static void play(ServerPlayerEntity player, Weighted w, Random random) {
        Vec3d pos;
        if (w.close()) {
            Vec3d look = player.getRotationVec(1f);
            Vec3d back = new Vec3d(-look.x, 0, -look.z);
            back = back.lengthSquared() < 1.0E-4 ? new Vec3d(1, 0, 0) : back.normalize();
            pos = player.getEyePos().add(back.multiply(1.5 + random.nextDouble() * 1.5)).add(0, -0.3, 0);
        } else {
            double a = random.nextDouble() * Math.PI * 2;
            double d = 8 + random.nextDouble() * 16;
            pos = player.getEyePos().add(Math.cos(a) * d, random.nextDouble() * 8 - 4, Math.sin(a) * d);
        }
        float volume = w.close() ? 0.7f : 1.6f;
        float pitch = 0.85f + random.nextFloat() * 0.25f;
        MineEvents.playTo(player, w.sound(), pos, volume, pitch);
    }
}
