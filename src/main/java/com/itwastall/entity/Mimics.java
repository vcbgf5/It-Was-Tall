package com.itwastall.entity;

import com.itwastall.ItWasTall;
import com.itwastall.base.BaseTracker;
import com.itwastall.config.ItWasTallConfig;
import com.itwastall.director.Director;
import com.itwastall.registry.ModEntities;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Schedules THE MIMICS: at night, stage 2+, 1-3 of them come out of the dark near a player every 10-20 min. */
public final class Mimics {
    private static final Map<UUID, Long> NEXT = new HashMap<>();
    private static final int MAX_NEAR = 3;

    private Mimics() {}

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(Mimics::tick);
    }

    public static void forget(UUID player) {
        NEXT.remove(player);
    }

    public static long nextAt(UUID player) {
        Long n = NEXT.get(player);
        return n == null ? -1 : n;
    }

    private static void tick(MinecraftServer server) {
        if (server.getTicks() % 40 != 0) return;
        ItWasTallConfig.Values cfg = ItWasTallConfig.INSTANCE;
        if (!cfg.mimics || Director.phase() != 1 || Director.stageIndex() < 2) return;
        ServerWorld world = server.getOverworld();
        if (!world.isNight()) return;
        long now = world.getTime();
        for (ServerPlayerEntity p : world.getPlayers()) {
            if (!p.isAlive() || p.isSpectator() || p.isCreative()) continue;
            Long next = NEXT.get(p.getUuid());
            if (next == null) {
                NEXT.put(p.getUuid(), now + 20L * 60 * (10 + p.getRandom().nextInt(11)));
                continue;
            }
            if (now < next) continue;
            if (BaseTracker.isInBase(p) || !EventGate.ready(p)) continue;
            if (spawnFor(p, 1 + p.getRandom().nextInt(3)) > 0) EventGate.mark(p);
            NEXT.put(p.getUuid(), now + 20L * 60 * (10 + p.getRandom().nextInt(11)));
        }
    }

    /** Spawns up to {@code count} mimics 16-30 blocks from the player, out of their sight. Returns how many. */
    public static int spawnFor(ServerPlayerEntity p, int count) {
        ServerWorld world = p.getServerWorld();
        if (world.getRegistryKey() != World.OVERWORLD) return 0;
        int near = world.getEntitiesByClass(MimicEntity.class, new Box(p.getBlockPos()).expand(64), e -> true).size();
        int want = Math.min(count, MAX_NEAR - near);
        var r = p.getRandom();
        int variantBase = r.nextInt(3);
        int made = 0;
        for (int attempt = 0; attempt < 40 && made < want; attempt++) {
            double angle = Math.toRadians(p.getYaw() + 90 + 180 + (r.nextDouble() * 160 - 80));   // behind the player
            double dist = 16 + r.nextDouble() * 14;
            int x = MathHelper.floor(p.getX() + Math.cos(angle) * dist), z = MathHelper.floor(p.getZ() + Math.sin(angle) * dist);
            if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
            int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos at = new BlockPos(x, y, z);
            BlockState floor = world.getBlockState(at.down());
            if (!floor.getFluidState().isEmpty() || floor.getCollisionShape(world, at.down()).isEmpty()) continue;
            if (!world.getBlockState(at).isAir() || !world.getBlockState(at.up()).isAir()) continue;
            if (Math.abs(y - p.getY()) > 10) continue;
            MimicEntity m = ModEntities.MIMIC.create(world);
            if (m == null) return made;
            m.setVariant(r.nextInt(4) == 0 ? r.nextInt(3) : variantBase);   // mostly one kind, like a small herd
            float yaw = MimicEntity.yawTowards(x + 0.5, z + 0.5, p.getX(), p.getZ());
            m.refreshPositionAndAngles(x + 0.5, y, z + 0.5, yaw, 0f);
            m.setHeadYaw(yaw);
            m.setBodyYaw(yaw);
            m.setFollowFor(20 * (40 + r.nextInt(50)));
            if (world.spawnEntity(m)) made++;
        }
        if (made > 0) ItWasTall.LOGGER.info("{} mimic(s) came out of the dark near {}.", made, p.getName().getString());
        return made;
    }
}
