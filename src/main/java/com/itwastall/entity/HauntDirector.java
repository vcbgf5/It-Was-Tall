package com.itwastall.entity;

import com.itwastall.config.ItWasTallConfig;
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

    private static long nextDelay(Random random) {
        ItWasTallConfig.Values cfg = ItWasTallConfig.INSTANCE;
        int min = Math.max(1, cfg.hauntMinMinutes);
        int max = Math.max(min, cfg.hauntMaxMinutes);
        return 20L * 60 * min + random.nextInt(20 * 60 * (max - min) + 1);
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

    public static int countActive(MinecraftServer server) {
        int n = 0;
        for (ServerWorld w : server.getWorlds()) n += w.getEntitiesByType(ModEntities.TALL_ONE, e -> true).size();
        return n;
    }

    /** Spawns It standing in the distance in front of the player (28-45 blocks), on open ground. */
    public static boolean spawnStare(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        Random random = player.getRandom();
        float baseYaw = player.getYaw();
        for (int attempt = 0; attempt < 24; attempt++) {
            float yaw = (baseYaw + (random.nextFloat() - 0.5f) * 120f) * MathHelper.RADIANS_PER_DEGREE;
            double dist = 28 + random.nextDouble() * 17;
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
            // it should be visible from where the player stands
            if (!world.raycast(new net.minecraft.world.RaycastContext(player.getEyePos(), new Vec3d(x, top.getY() + 2.8, z),
                    net.minecraft.world.RaycastContext.ShapeType.COLLIDER, net.minecraft.world.RaycastContext.FluidHandling.NONE, player))
                    .getType().equals(net.minecraft.util.hit.HitResult.Type.MISS) && attempt < 16) continue;
            it.setVictim(player);
            world.spawnEntity(it);
            return true;
        }
        return false;
    }
}
