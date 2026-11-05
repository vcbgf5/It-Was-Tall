package com.itwastall.entity;

import com.itwastall.config.ItWasTallConfig;
import com.itwastall.director.Director;
import com.itwastall.registry.ModEntities;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Brings the Old One: every {@code oldOneMinMinutes..oldOneMaxMinutes} (per player, from
 * {@code oldOneMinStage}) it appears 8-12 blocks away, just OUTSIDE the player's view — you turn
 * around and there is "a player" standing there. Never while a Tall One exists (it would have nothing
 * to come out of it) and never two near one player.
 */
public final class OldOneDirector {
    private static final Map<UUID, Long> NEXT = new HashMap<>();

    private OldOneDirector() {
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(OldOneDirector::tick);
    }

    public static void forget(UUID player) {
        NEXT.remove(player);
    }

    private static void tick(MinecraftServer server) {
        if (server.getTicks() % 40 != 0) return;
        ItWasTallConfig.Values cfg = ItWasTallConfig.INSTANCE;
        if (!cfg.oldOne || Director.phase() == 0 || Director.stageIndex() < cfg.oldOneMinStage) return;
        long now = server.getOverworld().getTime();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            Random random = player.getRandom();
            Long next = NEXT.get(player.getUuid());
            if (next == null) {
                NEXT.put(player.getUuid(), now + 20L * 60 * Math.max(0, cfg.oldOneFirstMinutes) + random.nextInt(20 * 120));
                continue;
            }
            if (now < next) continue;
            if (!eligible(player)) {
                NEXT.put(player.getUuid(), now + 20L * 30);
                continue;
            }
            if (spawnFor(player)) {
                int min = Math.max(1, cfg.oldOneMinMinutes), max = Math.max(min, cfg.oldOneMaxMinutes);
                NEXT.put(player.getUuid(), now + 20L * 60 * min + random.nextInt(20 * 60 * (max - min) + 1));
            } else {
                NEXT.put(player.getUuid(), now + 20L * 20);
            }
        }
    }

    private static boolean eligible(ServerPlayerEntity player) {
        if (player.isCreative() || player.isSpectator() || !player.isAlive()) return false;
        World world = player.getWorld();
        if (world.getRegistryKey() != World.OVERWORLD) return false;
        if (ItWasTallConfig.INSTANCE.oldOneOnlyAtNight && !world.isNight()) return false;
        if (MineEvents.isUnderground(player) || ShelterCheck.isSheltered(world, player.getBlockPos())) return false;
        if (HauntDirector.countActive(player.getServerWorld().getServer()) > 0) return false;   // It must be free to come out
        return player.getServerWorld().getEntitiesByType(ModEntities.OLD_ONE, player.getBoundingBox().expand(96), e -> true).isEmpty();
    }

    /** Spawns one Old One near the player, out of view. Also the test entry (/itwastall event oldone). */
    public static boolean spawnFor(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        Random random = player.getRandom();
        Vec3d look = player.getRotationVec(1f);
        for (int attempt = 0; attempt < 24; attempt++) {
            // somewhere to the side or behind: 75..285 degrees away from where you look
            double ang = Math.atan2(look.z, look.x) + Math.toRadians(75 + random.nextDouble() * 210);
            double dist = 8 + random.nextDouble() * 4;
            double x = player.getX() + Math.cos(ang) * dist, z = player.getZ() + Math.sin(ang) * dist;
            int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, MathHelper.floor(x), MathHelper.floor(z));
            if (Math.abs(y - player.getY()) > 5) continue;
            BlockPos below = BlockPos.ofFloored(x, y - 1, z);
            BlockState ground = world.getBlockState(below);
            if (!ground.getFluidState().isEmpty() || ground.isIn(BlockTags.LEAVES) || ground.getCollisionShape(world, below).isEmpty()) continue;
            OldOneEntity one = ModEntities.OLD_ONE.create(world);
            if (one == null) return false;
            one.refreshPositionAndAngles(MathHelper.floor(x) + 0.5, y, MathHelper.floor(z) + 0.5, 0f, 0f);
            if (!world.isSpaceEmpty(one)) continue;
            one.setVictim(player);
            world.spawnEntity(one);
            return true;
        }
        return false;
    }
}
