package com.itwastall.entity;

import com.itwastall.ItWasTall;
import com.itwastall.base.BaseMap;
import com.itwastall.base.BaseTracker;
import com.itwastall.config.ItWasTallConfig;
import com.itwastall.director.Director;
import com.itwastall.registry.ModBlocks;
import com.itwastall.registry.ModSounds;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * THE FOOTPRINTS: while you are inside at night, a line of long, narrow, far too big footprints comes out of
 * the dark, straight to your door, and then walks around the house, stopping under every window. You hear the
 * last steps. By morning the prints are gone (FootprintBlock fades them by day).
 */
public final class TallFootprints {
    private static final Map<UUID, Long> NEXT = new HashMap<>();
    private static final Map<UUID, Long> NIGHT = new HashMap<>();

    private TallFootprints() {}

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(TallFootprints::tick);
    }

    public static void forget(UUID player) {
        NEXT.remove(player);
        NIGHT.remove(player);
    }

    public static long nextAt(UUID player) {
        Long next = NEXT.get(player);
        return next == null ? -1 : next;
    }

    private static void tick(MinecraftServer server) {
        if (server.getTicks() % 40 != 0) return;
        ItWasTallConfig.Values cfg = ItWasTallConfig.INSTANCE;
        if (!cfg.tallFootprints || Director.phase() != 1 || Director.stageIndex() < 2) return;
        ServerWorld world = server.getOverworld();
        if (!world.isNight()) return;
        long now = world.getTime();
        long night = world.getTimeOfDay() / 24000L;
        for (ServerPlayerEntity p : world.getPlayers()) {
            if (!p.isAlive() || p.isSpectator() || p.isCreative()) continue;
            Long next = NEXT.get(p.getUuid());
            if (next == null) {
                NEXT.put(p.getUuid(), now + 20L * 60 * (8 + p.getRandom().nextInt(10)));
                continue;
            }
            if (now < next || Long.valueOf(night).equals(NIGHT.get(p.getUuid()))) continue;
            if (!BaseTracker.isInBase(p) || !EventGate.ready(p)) continue;
            if (walkToDoor(p)) {
                NIGHT.put(p.getUuid(), night);
                EventGate.mark(p);
            }
            NEXT.put(p.getUuid(), now + 20L * 60 * (15 + p.getRandom().nextInt(11)));
        }
    }

    /** Lays the trail to the nearest mapped door of the player's base and around its windows. */
    public static boolean walkToDoor(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        if (world.getRegistryKey() != World.OVERWORLD) return false;
        BaseMap map = BaseTracker.eventMapOf(player);
        if (map == null) return false;
        List<BlockPos> doors = map.all(BaseMap.TYPE_DOOR);
        if (doors.isEmpty()) return false;
        BlockPos door = doors.stream().min(java.util.Comparator.comparingDouble(d -> d.getSquaredDistance(player.getBlockPos()))).orElseThrow();
        BlockState ds = world.getBlockState(door);
        if (ds.getBlock() instanceof DoorBlock && ds.get(DoorBlock.HALF) == net.minecraft.block.enums.DoubleBlockHalf.UPPER) door = door.down();
        Direction out = outside(world, door, ds);
        if (out == null) return false;

        var r = player.getRandom();
        BlockPos step = door.offset(out);             // the spot right outside the door
        // from out of the dark: 22 blocks away, a little to one side, walking straight in
        double side = (r.nextDouble() - 0.5) * 10;
        Vec3d end = Vec3d.ofCenter(step);
        Vec3d perp = new Vec3d(out.rotateYClockwise().getOffsetX(), 0, out.rotateYClockwise().getOffsetZ());
        Vec3d start = end.add(out.getOffsetX() * 22.0, 0, out.getOffsetZ() * 22.0).add(perp.multiply(side));
        int placed = 0;
        for (double t = 0; t <= 1.0001; t += 1.0 / 11) {   // a print every two blocks
            Vec3d at = start.lerp(end, t);
            Vec3d dir = end.subtract(start);
            Direction facing = Direction.getFacing(dir.x, 0, dir.z);
            if (print(world, BlockPos.ofFloored(at.x, end.y, at.z), facing)) placed++;
        }
        print(world, step, out.getOpposite());        // stood at the door, facing it
        // then around the house: one print under each window it looked into
        for (BlockPos window : map.all(BaseMap.TYPE_WINDOW)) {
            if (window.getSquaredDistance(door) > 24 * 24) continue;
            Direction wOut = outside(world, window, world.getBlockState(window));
            if (wOut == null) continue;
            if (print(world, window.offset(wOut).down(), wOut.getOpposite()) || print(world, window.offset(wOut), wOut.getOpposite())) placed++;
        }
        if (placed == 0) return false;
        // the last steps, heard from inside
        for (int i = 0; i < 3; i++) {
            MineEvents.playTo(player, ModSounds.STEP, Vec3d.ofCenter(step).add(out.getOffsetX() * (2 - i), 0, out.getOffsetZ() * (2 - i)),
                    1.1f, 0.55f + i * 0.03f);
        }
        ItWasTall.LOGGER.info("Tall footprints walked to the door of {} at {}.", player.getName().getString(), door.toShortString());
        return true;
    }

    /** The side of a door / window that is out in the open (not under a roof), or null. */
    @Nullable
    private static Direction outside(ServerWorld world, BlockPos pos, BlockState state) {
        Direction[] tryDirs;
        if (state.getBlock() instanceof DoorBlock) {
            Direction f = state.get(DoorBlock.FACING);
            tryDirs = new Direction[]{f, f.getOpposite()};
        } else {
            tryDirs = new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        }
        for (Direction d : tryDirs) {
            BlockPos p = pos.offset(d);
            if (world.getBlockState(p).isAir() && !ShelterCheck.isSheltered(world, p)) return d;
        }
        return null;
    }

    private static boolean print(ServerWorld world, BlockPos near, Direction facing) {
        for (int dy = 2; dy >= -3; dy--) {
            BlockPos p = near.up(dy);
            BlockState st = world.getBlockState(p);
            if ((st.isAir() || (st.isReplaceable() && st.getFluidState().isEmpty()))
                    && world.getBlockState(p.down()).isSideSolidFullSquare(world, p.down(), Direction.UP)) {
                world.setBlockState(p, ModBlocks.TALL_FOOTPRINT.getDefaultState().with(com.itwastall.block.FootprintBlock.FACING, facing), Block.NOTIFY_ALL);
                return true;
            }
        }
        return false;
    }
}
