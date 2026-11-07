package com.itwastall.entity;

import com.itwastall.config.ItWasTallConfig;
import com.itwastall.director.Director;
import com.itwastall.director.Stage;
import com.itwastall.registry.ModEntities;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Makes It show up on its own: every {@code hauntMinMinutes..hauntMaxMinutes} (per player) it appears in the distance,
 * in front of the player, and stares. Multiplayer: every player has their own timer, it haunts one player at a time
 * (no two Its near the same player) and there is a server-wide limit of active Its.
 */
public final class HauntDirector {
    private static final Map<UUID, Long> NEXT = new HashMap<>();

    private HauntDirector() {
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(HauntDirector::tick);
    }

    public static void forget(UUID player) {
        NEXT.remove(player);
    }

    private static void tick(MinecraftServer server) {
        if (server.getTicks() % 40 != 0) return;
        ItWasTallConfig.Values cfg = ItWasTallConfig.INSTANCE;
        if (!cfg.hauntEvents) return;
        // PHASES §0/§1: FAZA PRE (before IT JOINED) and ETAP 0 ("CISZA" — the stage table has
        // cooldown 0 there = haunting disabled) -> no hauntings at all. Read live, never cached,
        // so /itwastall phase|stage takes effect from the very next check.
        if (Director.phase() == 0) return;                          // PHASE PRE: total silence
        if (Director.currentStage() == Stage.STAGE_0) return;       // ETAP 0: It does not exist yet
        long now = server.getOverworld().getTime();

        List<ServerPlayerEntity> players = new ArrayList<>(server.getPlayerManager().getPlayerList());
        java.util.Collections.shuffle(players); // fair order when several players are due at once
        for (ServerPlayerEntity player : players) {
            Random random = player.getRandom();
            Long next = NEXT.get(player.getUuid());
            if (next == null) {
                NEXT.put(player.getUuid(), now + 20L * 60 * Math.max(0, cfg.hauntFirstMinutes) + random.nextInt(20 * 60));
                continue;
            }
            if (now < next) continue;
            if (!canHaunt(player)) {
                NEXT.put(player.getUuid(), now + 20L * 30); // try again in 30 s
                continue;
            }
            if (countActive(server) >= Math.max(1, cfg.maxActiveIts)) continue;
            if (spawnStare(player)) {
                NEXT.put(player.getUuid(), now + nextDelay(random));
            } else {
                NEXT.put(player.getUuid(), now + 20L * 20);
            }
        }
    }

    /**
     * Delay until the player's next haunting: the per-stage cooldown table (PHASES §3), read live
     * at write time (every haunting), never cached at init — so /itwastall stage changes the next
     * cooldown directly. The cfg.hauntMin/MaxMinutes values are no longer used here; they survive
     * as the pre-stage defaults and are overridden by config stages[] via the Stage getters.
     */
    private static long nextDelay(Random random) {
        Stage st = Director.currentStage();
        int minSec = Math.max(0, st.hauntCooldownMinSec());
        int maxSec = Math.max(minSec, st.hauntCooldownMaxSec());
        if (maxSec <= 0) return 20L * 300; // sanity fallback; ETAP 0/PRE never reach spawnStare anyway
        return 20L * (minSec + random.nextInt(maxSec - minSec + 1));
    }

    public static boolean canHaunt(ServerPlayerEntity player) {
        ItWasTallConfig.Values cfg = ItWasTallConfig.INSTANCE;
        if (player.isCreative() || player.isSpectator() || !player.isAlive()) return false;
        World world = player.getWorld();
        if (world.getRegistryKey() != World.OVERWORLD) return false;
        if (cfg.hauntOnlyAtNight && !world.isNight()) return false;
        if (MineEvents.isUnderground(player)) return false; // the mine has its own events
        return player.getServerWorld().getEntitiesByType(ModEntities.TALL_ONE, player.getBoundingBox().expand(96), e -> true).isEmpty();
    }

    /**
     * Real Its on the server (MODPLAN §4) that actually matter: loaded ones that ticked in the last 5 s
     * and are within 160 blocks of a player, plus the one remembered in the world data for 30 s after its
     * last tick (an It standing in a chunk that just unloaded). A forgotten It far away never blocks.
     */
    public static int countActive(MinecraftServer server) {
        int n = 0;
        UUID recorded = com.itwastall.world.ItWasTallState.getOrCreate(server).activeIt();
        boolean recordedLoaded = false;
        long now = server.getOverworld().getTime();
        for (ServerWorld w : server.getWorlds()) {
            for (TallOneEntity it : w.getEntitiesByType(ModEntities.TALL_ONE, e -> true)) {
                if (it.isRemoved()) continue;
                if (it.puppetOwner() != null) {   // the god walking as It: it IS the It of the server
                    n++;
                    continue;
                }
                if (it.getUuid().equals(recorded)) recordedLoaded = true;
                boolean ticking = it.age < 100 || now - it.lastTickTime() < 100;
                boolean nearSomeone = w.getClosestPlayer(it, 160) != null;
                if (ticking && nearSomeone) n++;
            }
        }
        if (!recordedLoaded && com.itwastall.world.ItWasTallState.getOrCreate(server).activeItFresh(server.getOverworld().getTime())) n++;
        return n;
    }

    /** Spawns It standing in the distance in front of the player, on open ground. The spawn
     *  distance range comes live from the stage table (PHASES §3 "Haunt dystans") — the old
     *  hard-coded 28-45 only served ETAP 2-ish values. */
    /** Test commands: the current It (if any) quietly leaves so the new one can come. */
    public static void clearForCommand(MinecraftServer server) {
        for (ServerWorld w : server.getWorlds()) {
            for (TallOneEntity it : w.getEntitiesByType(ModEntities.TALL_ONE, e -> e.puppetOwner() == null)) it.discard();
        }
        com.itwastall.world.ItWasTallState.getOrCreate(server).forgetActiveIt();
    }

    /** Test commands: every It that just appeared (younger than 5 ticks) does its full routine. */
    public static void markNewForced(MinecraftServer server) {
        for (ServerWorld w : server.getWorlds()) {
            for (TallOneEntity it : w.getEntitiesByType(ModEntities.TALL_ONE, e -> e.age < 5)) it.setForced(true);
        }
    }

    public static boolean spawnStare(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        Random random = player.getRandom();
        Stage st = Director.currentStage(); // live read (PHASES §0)
        ItWasTallConfig.Values cfg = ItWasTallConfig.INSTANCE;
        int range = Math.max(16, cfg.maxEventRange);
        int minDist = Math.min(Math.max(1, st.hauntMinDist()), range);
        int maxDist = Math.min(Math.max(minDist, st.hauntMaxDist()), range);
        // TREE (MODPLAN §3.2): stage 2+, sometimes it is half hidden behind a trunk instead of out in the open
        if (st.ordinal() >= 2 && cfg.treePeeks && random.nextDouble() < cfg.treePeekChance
                && spawnBehindTree(player, Math.max(12, Math.min(minDist, 30)), Math.max(18, Math.min(maxDist, 40)))) {
            return true;
        }
        float baseYaw = player.getYaw();
        for (int attempt = 0; attempt < 24; attempt++) {
            float yaw = (baseYaw + (random.nextFloat() - 0.5f) * 120f) * MathHelper.RADIANS_PER_DEGREE;
            double dist = minDist + random.nextDouble() * (maxDist - minDist);
            double x = player.getX() - MathHelper.sin(yaw) * dist;
            double z = player.getZ() + MathHelper.cos(yaw) * dist;
            BlockPos top = BlockPos.ofFloored(x, world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, MathHelper.floor(x), MathHelper.floor(z)), z);
            BlockState below = world.getBlockState(top.down());
            if (!below.getFluidState().isEmpty() || below.getCollisionShape(world, top.down()).isEmpty()) continue;
            if (Math.abs(top.getY() - player.getY()) > 16) continue;

            TallOneEntity it = ModEntities.TALL_ONE.create(world);
            if (it == null) return false;
            it.refreshPositionAndAngles(x, top.getY(), z, 0f, 0f);
            if (!world.isSpaceEmpty(it)) continue;
            // it should be visible from where the player stands.
            // AUDIT FIX: the visibility requirement now applies to ALL attempts (before, attempts
            // 16-23 could place an invisible It); a hidden spawn is skipped and the next attempt
            // (or a later retry in 20 s) finds an open line of sight instead.
            if (!world.raycast(new net.minecraft.world.RaycastContext(player.getEyePos(), new Vec3d(x, top.getY() + 2.8, z),
                    net.minecraft.world.RaycastContext.ShapeType.COLLIDER, net.minecraft.world.RaycastContext.FluidHandling.NONE, player))
                    .getType().equals(net.minecraft.util.hit.HitResult.Type.MISS)) continue;
            it.setVictim(player);
            // Only report success when the world actually accepts the entity.
            // Otherwise the caller schedules a short retry instead of a full cooldown.
            return world.spawnEntity(it);
        }
        return false;
    }

    /**
     * TREE: a trunk (at least 3 logs tall) roughly in front of the player; It stands just behind it and a
     * little to one side, so its head and one shoulder show. Remembers the spot right behind the trunk —
     * that is where it steps when you notice it.
     */
    public static boolean spawnBehindTree(ServerPlayerEntity player, int minDist, int maxDist) {
        ServerWorld world = player.getServerWorld();
        Random random = player.getRandom();
        float baseYaw = player.getYaw();
        for (int attempt = 0; attempt < 40; attempt++) {
            float yaw = (baseYaw + (random.nextFloat() - 0.5f) * 100f) * MathHelper.RADIANS_PER_DEGREE;
            double dist = minDist + random.nextDouble() * Math.max(1, maxDist - minDist);
            int cx = MathHelper.floor(player.getX() - MathHelper.sin(yaw) * dist);
            int cz = MathHelper.floor(player.getZ() + MathHelper.cos(yaw) * dist);
            BlockPos trunk = findTrunk(world, cx, cz, player);
            if (trunk == null) continue;

            Vec3d tc = new Vec3d(trunk.getX() + 0.5, trunk.getY(), trunk.getZ() + 0.5);
            Vec3d away = new Vec3d(tc.x - player.getX(), 0, tc.z - player.getZ());
            if (away.lengthSquared() < 1.0) continue;
            away = away.normalize();
            Vec3d side = new Vec3d(-away.z, 0, away.x).multiply(random.nextBoolean() ? 1 : -1);
            Vec3d hide = tc.add(away.multiply(1.05));
            Vec3d peek = hide.add(side.multiply(0.65));

            TallOneEntity it = ModEntities.TALL_ONE.create(world);
            if (it == null) return false;
            it.refreshPositionAndAngles(peek.x, peek.y, peek.z, 0f, 0f);
            if (!world.isSpaceEmpty(it)) continue;
            if (!world.isSpaceEmpty(it, it.getBoundingBox().offset(hide.subtract(peek)))) continue;
            BlockPos below = BlockPos.ofFloored(peek.x, peek.y - 0.5, peek.z);
            if (world.getBlockState(below).getCollisionShape(world, below).isEmpty()) continue;
            // its head, beside the trunk, must be visible from where the player stands
            Vec3d head = peek.add(side.multiply(0.25)).add(0, 3.0, 0);
            if (!world.raycast(new net.minecraft.world.RaycastContext(player.getEyePos(), head,
                    net.minecraft.world.RaycastContext.ShapeType.VISUAL, net.minecraft.world.RaycastContext.FluidHandling.NONE, player))
                    .getType().equals(net.minecraft.util.hit.HitResult.Type.MISS)) continue;
            it.setVictim(player);
            it.startTree(hide);
            return world.spawnEntity(it);
        }
        return false;
    }

    /** Lowest log of a 3+ log trunk within 2 blocks of (x, z), standing on solid ground near the player's height. */
    @org.jetbrains.annotations.Nullable
    private static BlockPos findTrunk(ServerWorld world, int x, int z, ServerPlayerEntity player) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x + dx, z + dz);
                BlockPos.Mutable p = new BlockPos.Mutable(x + dx, top - 1, z + dz);
                if (!world.getBlockState(p).isIn(net.minecraft.registry.tag.BlockTags.LOGS)) continue;
                int logs = 0;
                while (p.getY() > world.getBottomY() && world.getBlockState(p).isIn(net.minecraft.registry.tag.BlockTags.LOGS) && logs < 40) {
                    p.move(0, -1, 0);
                    logs++;
                }
                // p is now the block under the trunk
                BlockState ground = world.getBlockState(p);
                if (logs < 3 || ground.getCollisionShape(world, p).isEmpty() || !ground.getFluidState().isEmpty()) continue;
                if (Math.abs(p.getY() + 1 - player.getY()) > 8) continue;
                return p.up().toImmutable();
            }
        }
        return null;
    }

    /** World time of this player's next roll (-1 = not scheduled yet), for /itwastall debug events. */
    public static long nextAt(UUID player) {
        Long next = NEXT.get(player);
        return next == null ? -1 : next;
    }
}
