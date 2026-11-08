package com.itwastall.entity;

import com.itwastall.ItWasTall;
import com.itwastall.config.ItWasTallConfig;
import com.itwastall.director.Director;
import com.itwastall.director.Stage;
import com.itwastall.net.FalsePresencePayload;
import com.itwastall.net.ModNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * FALSE PRESENCE (MODPLAN §7, PHASES Poziom 4): a fake Tall One that looks exactly like It,
 * is rendered on one single client and is never a real entity (MODPLAN §7.4: no hitbox,
 * no damage, no server entity, does not count against the one-true-Tall-One limit).
 *
 * <p>Stage table (PHASES §3 "FalsePresence %"): 1 = 0.5%, 2 = 1.5%, 3 = 3%, 4 = 4%, 5 = 5%,
 * max 1 sighting per night per player up to ETAP 3, 3 (ETAP 4) / 5 (ETAP 5). The client
 * silently removes the figure the moment the player looks straight at it ("znikniecie po
 * spojrzeniu") or when its short lifetime runs out.
 *
 * <p>All decisions happen on the server; only the placement (a validated spot in the world)
 * is sent to the client in {@link FalsePresencePayload}.
 */
public final class FalsePresences {
    /** Per-player per-night sighting ledger (server memory; resets on restart — like HauntDirector). */
    private static final Map<UUID, Ledger> LEDGERS = new HashMap<>();

    private FalsePresences() {
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(FalsePresences::tick);
    }

    public static void forget(UUID player) {
        LEDGERS.remove(player);
    }

    /** Client-side figure lifetime: keeps it short (MODPLAN §7.2 "pojawia sie na krótko"). */
    private static final int FIGURE_TICKS = 12 * 20;

    private static void tick(MinecraftServer server) {
        if (server.getTicks() % 20 != 0) return; // one roll per second is plenty
        ItWasTallConfig.Values cfg = ItWasTallConfig.INSTANCE;
        if (!cfg.falsePresence) return;
        if (Director.phase() != 1) return;      // PHASE PRE: total silence (PHASES §1)

        Stage st = Director.currentStage();     // live read, /itwastall stage works next tick
        double chance = st.falsePresenceChance();
        if (chance <= 0) return;                // ETAP 0: % = 0

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!eligible(player)) continue;
            Ledger ledger = LEDGERS.computeIfAbsent(player.getUuid(), u -> new Ledger());
            long night = nightIndex(player.getWorld());
            if (ledger.night != night) {
                ledger.night = night;
                ledger.count = 0;
            }
            if (ledger.count >= maxPerNight(st)) continue;
            // roll the stage table chance — use the per-player random so rolls differ per player
            Random random = player.getRandom();
            if (random.nextDouble() >= chance) continue;

            if (!EventGate.ready(player)) continue;
            BlockPos spot = findSpot(player, random, 10, 20);
            if (spot == null) continue;
            if (sendFigure(player, spot, FIGURE_TICKS, false)) {
                EventGate.mark(player);
                ledger.count++;
                ItWasTall.LOGGER.debug("False presence -> {}", player.getName().getString());
            }
        }
    }

    /** Public test trigger (/itwastall event falsepresence): ignores the per-night ledger and chance. */
    public static boolean trigger(ServerPlayerEntity player) {
        if (!eligible(player)) return false;
        Random random = player.getRandom();
        BlockPos spot = findSpot(player, random, 8, 18);
        return spot != null && sendFigure(player, spot, FIGURE_TICKS, false);
    }

    /**
     * Places a figure at an already validated {@code spot} and sends it to {@code player}'s client.
     * Also the entry point for the window-knock silhouette (level 2+): there the figure
     * must stand in a valid gap behind the glass, so the knock system chooses the spot itself.
     */
    public static boolean sendFigure(ServerPlayerEntity player, BlockPos spot, int durationTicks, boolean window) {
        return sendFigure(player, spot, durationTicks, window, false);
    }

    /** Full variant: the NIE PATRZ watcher (§15.4) is sent with {@code dontlook = true}. */
    public static boolean sendFigure(ServerPlayerEntity player, BlockPos spot, int durationTicks,
                                     boolean window, boolean dontlook) {
        if (player == null || durationTicks <= 0) return false;
        // face the player, whatever direction that is
        double dx = player.getX() - (spot.getX() + 0.5);
        double dz = player.getZ() - (spot.getZ() + 0.5);
        float yaw = (float) (MathHelper.atan2(dz, dx) * MathHelper.DEGREES_PER_RADIAN) - 90f;
        return ModNetworking.sendFalsePresence(player, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5,
                yaw, durationTicks, window, dontlook);
    }

    /** A figure may only be sent to this player's client when he is in the right spot for one. */
    private static boolean eligible(ServerPlayerEntity player) {
        if (player.isCreative() || player.isSpectator() || !player.isAlive()) return false;
        World world = player.getWorld();
        if (world.getRegistryKey() != World.OVERWORLD) return false;
        if (!world.isNight()) return false;          // night shapes (PHASES §7 examples)
        if (MineEvents.isUnderground(player)) return false; // the mine has its own scares
        if (ShelterCheck.isSheltered(world, player.getBlockPos())) return false;
        // never double up: no true Tall One close to this player right now
        return true;
    }

    private static long nightIndex(World world) {
        return world.getTimeOfDay() / 24000L;
    }

    /** Per night: 2 up to ETAP 3, 4 in ETAP 4, 6 in ETAP 5 (more than PHASES §3 — fewer felt like it stopped working). */
    private static int maxPerNight(Stage st) {
        return switch (st.ordinal()) {
            case 4 -> 4;
            case 5 -> 6;
            default -> 2;
        };
    }

    /**
     * A spot BEHIND the player (MODPLAN §15.4): opposite the view direction, 3..6 blocks away,
     * small side jitter, same standability rules as any false presence. Null when the neck's
     * back has no room (walls) — the task is then refused rather than misplaced.
     */
    @Nullable
    public static BlockPos findSpotBehind(ServerPlayerEntity player, Random random) {
        ServerWorld world = player.getServerWorld();
        float yaw = player.getYaw() * MathHelper.RADIANS_PER_DEGREE; // player's view yaw
        for (int attempt = 0; attempt < 24; attempt++) {
            float behindYaw = yaw + 180f + (random.nextFloat() * 0.5f - 0.25f) * 40f;
            double dist = 3.0 + random.nextDouble() * 3.0;
            double x = player.getX() - MathHelper.sin(behindYaw) * dist;
            double z = player.getZ() + MathHelper.cos(behindYaw) * dist;
            BlockPos spot = BlockPos.ofFloored(x, player.getBlockY(), z);
            if (Math.abs(spot.getY() - player.getBlockY()) > 1) {
                spot = new BlockPos(spot.getX(), player.getBlockY(), spot.getZ());
            }
            if (!fitsFigure(world, spot)) continue;
            if (spotTaken(world, spot, player)) continue;
            return spot.toImmutable();
        }
        return null;
    }

    /**
     * Looks for a spot between {@code minDist} and {@code maxDist} in front of the player (its
     * vision cone widened to +-70 degrees — the "kacim okiem"/periphery placements from MODPLAN
     * §7.1), on ground needing no digging, at most 8 blocks below or above the player's feet.
     * Returns the spot or null.
     */
    @Nullable
    public static BlockPos findSpot(ServerPlayerEntity player, Random random, int minDist, int maxDist) {
        ServerWorld world = player.getServerWorld();
        float baseYaw = player.getYaw();

        for (int attempt = 0; attempt < 30; attempt++) {
            // wider away from the direction the player sees straight ahead -> periphery hits
            float yawDeg = baseYaw + (random.nextFloat() * 0.9f - 0.1f) * 140f;
            float yaw = yawDeg * MathHelper.RADIANS_PER_DEGREE;
            double dist = minDist + random.nextDouble() * Math.max(1, maxDist - minDist);
            double x = player.getX() - MathHelper.sin(yaw) * dist;
            double z = player.getZ() + MathHelper.cos(yaw) * dist;
            int y = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                    MathHelper.floor(x), MathHelper.floor(z));
            BlockPos spot = BlockPos.ofFloored(x, y, z);
            if (Math.abs(spot.getY() - player.getBlockPos().getY()) > 8) continue;
            if (!fitsFigure(world, spot)) continue;
            if (spotTaken(world, spot, player)) continue;
            if (spot.isWithinDistance(player.getBlockPos(), minDist - 0.5)) continue;
            return spot;
        }
        return null;
    }

    /** Two-high air on a solid floor (a static figure does not need four blocks, unlike true It). */
    private static boolean fitsFigure(ServerWorld world, BlockPos pos) {
        BlockState floor = world.getBlockState(pos.down());
        if (floor.isAir() || floor.getCollisionShape(world, pos.down()).isEmpty()) return false;
        return world.isAir(pos) && world.isAir(pos.up());
    }

    /** Never in front of another player than the haunted one himself, and never two figures overlapping. */
    private static boolean spotTaken(ServerWorld world, BlockPos pos, ServerPlayerEntity target) {
        for (ServerPlayerEntity other : world.getServer().getPlayerManager().getPlayerList()) {
            if (other == target) continue;
            if (other.getBlockPos().isWithinDistance(pos, 6)) return true;
        }
        return false;
    }

    /** Small per-player reminder of how often It has already appeared tonight. */
    private static final class Ledger {
        long night = -1;
        int count;
    }

    /** How many times this fired for the player in the current night ledger, for /itwastall debug events. */
    public static int ledgerCount(UUID player) {
        Ledger ledger = LEDGERS.get(player);
        return ledger == null ? 0 : ledger.count;
    }
}
