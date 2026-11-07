package com.itwastall.entity;

import com.itwastall.ItWasTall;
import com.itwastall.base.BaseTracker;
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
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Schedules THE HANGED: at night, on stage 2+, a player walking outside may see someone hanging from a tree
 * 20-45 blocks ahead. One per player per night, every 15-25 minutes at most (config hanged*).
 */
public final class HangedTrees {
    private static final Map<UUID, Long> NEXT = new HashMap<>();
    private static final Map<UUID, Long> NIGHT = new HashMap<>();

    private HangedTrees() {}

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(HangedTrees::tick);
    }

    public static void forget(UUID player) {
        NEXT.remove(player);
        NIGHT.remove(player);
    }

    /** World time of this player's next roll (-1 = not scheduled yet), for /itwastall debug events. */
    public static long nextAt(UUID player) {
        Long next = NEXT.get(player);
        return next == null ? -1 : next;
    }

    private static void tick(MinecraftServer server) {
        if (server.getTicks() % 40 != 0) return;
        ItWasTallConfig.Values cfg = ItWasTallConfig.INSTANCE;
        if (!cfg.hangedTrees || Director.phase() != 1 || Director.stageIndex() < 2) return;
        ServerWorld world = server.getOverworld();
        if (!world.isNight()) return;
        long now = world.getTime();
        long night = world.getTimeOfDay() / 24000L;
        for (ServerPlayerEntity p : world.getPlayers()) {
            if (!p.isAlive() || p.isSpectator() || p.isCreative()) continue;
            Long next = NEXT.get(p.getUuid());
            if (next == null) {
                NEXT.put(p.getUuid(), now + BloodMoon.scale(20L * 60 * randomMinutes(p, cfg)));
                continue;
            }
            if (now < next) continue;
            if (Long.valueOf(night).equals(NIGHT.get(p.getUuid()))) continue;           // one per night
            if (BaseTracker.isInBase(p) || !EventGate.ready(p)) continue;
            if (spawnFor(p, 20, 45, true)) {
                NIGHT.put(p.getUuid(), night);
                EventGate.mark(p);
            }
            NEXT.put(p.getUuid(), now + BloodMoon.scale(20L * 60 * randomMinutes(p, cfg)));
        }
    }

    private static int randomMinutes(ServerPlayerEntity p, ItWasTallConfig.Values cfg) {
        int lo = Math.max(1, cfg.hangedMinMinutes), hi = Math.max(lo, cfg.hangedMaxMinutes);
        return lo + p.getRandom().nextInt(hi - lo + 1);
    }

    /**
     * Hangs one on a tree {@code min}..{@code max} blocks from the player (ahead of them when {@code ahead}).
     * The ones from earlier nights keep hanging where they are.
     */
    public static boolean spawnFor(ServerPlayerEntity p, int min, int max, boolean ahead) {
        ServerWorld world = p.getServerWorld();
        if (world.getRegistryKey() != World.OVERWORLD) return false;
        BlockPos branch = null;
        var r = p.getRandom();
        for (int attempt = 0; attempt < 80 && branch == null; attempt++) {
            double angle = ahead && attempt < 50
                    ? Math.toRadians(p.getYaw() + 90 + (r.nextDouble() * 140 - 70))
                    : r.nextDouble() * Math.PI * 2;
            double dist = min + r.nextDouble() * (max - min);
            int x = MathHelper.floor(p.getX() + Math.cos(angle) * dist);
            int z = MathHelper.floor(p.getZ() + Math.sin(angle) * dist);
            if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
            branch = findBranch(world, x, z, p);
        }
        if (branch == null) return false;

        HangedEntity hanged = ModEntities.HANGED.create(world);
        if (hanged == null) return false;
        float yaw = (float) (MathHelper.atan2(p.getZ() - (branch.getZ() + 0.5), p.getX() - (branch.getX() + 0.5)) * MathHelper.DEGREES_PER_RADIAN) - 90f;
        hanged.hangFrom(branch, 1.4f, yaw);
        if (!world.spawnEntity(hanged)) return false;
        ItWasTall.LOGGER.info("The Hanged hangs at {} for {}.", branch.toShortString(), p.getName().getString());
        return true;
    }

    /**
     * A free branch of a tree near (x, z): a leaves or log block right next to a trunk of 4+ logs, 3-7 logs up,
     * with three blocks of air under it (room for the rope and the body).
     */
    @Nullable
    private static BlockPos findBranch(ServerWorld world, int x, int z, ServerPlayerEntity player) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x + dx, z + dz);
                BlockPos.Mutable p = new BlockPos.Mutable(x + dx, top - 1, z + dz);
                if (!world.getBlockState(p).isIn(BlockTags.LOGS)) continue;
                int logs = 0;
                while (p.getY() > world.getBottomY() && world.getBlockState(p).isIn(BlockTags.LOGS) && logs < 40) {
                    p.move(0, -1, 0);
                    logs++;
                }
                BlockState ground = world.getBlockState(p);
                if (logs < 4 || ground.getCollisionShape(world, p).isEmpty() || !ground.getFluidState().isEmpty()) continue;
                if (Math.abs(p.getY() + 1 - player.getY()) > 12) continue;
                BlockPos base = p.up().toImmutable();
                for (int h = 3; h <= Math.min(7, logs - 1); h++) {
                    BlockPos trunk = base.up(h);
                    for (Direction dir : Direction.Type.HORIZONTAL) {
                        BlockPos branch = trunk.offset(dir);
                        BlockState b = world.getBlockState(branch);
                        if (!b.isIn(BlockTags.LEAVES) && !b.isIn(BlockTags.LOGS)) continue;
                        if (world.getBlockState(branch.down()).isAir() && world.getBlockState(branch.down(2)).isAir()
                                && world.getBlockState(branch.down(3)).isAir()) {
                            return branch;
                        }
                    }
                }
            }
        }
        return null;
    }
}
