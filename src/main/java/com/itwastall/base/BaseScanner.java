package com.itwastall.base;

import com.itwastall.ItWasTall;
import com.itwastall.config.ItWasTallConfig;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

/**
 * Scans a base cube column by column (MODPLAN §8.2): spread across ticks, budgeted blocks
 * per tick (config {@code scanBlocksPerTick} shared between pending scans by BaseTracker),
 * no full-world touches. A cube is (2r+1)^2 columns (anchor +-radius), each swept through
 * a narrow row range around the anchor's floor height — a rescan of a small base runs a few
 * seconds and stays invisible to server timing.
 */
public final class BaseScanner {
    private static final int UP_ROWS = 8;    // above the anchor's floor row (walls, roof)
    private static final int DOWN_ROWS = 4;  // below (porches, basements)
    /** Rows one column costs (debug readouts, budget math). */
    public static final int ROWS_TOTAL = UP_ROWS + DOWN_ROWS + 1;

    /** Horizontal radius (anchor +-radius), clamped to 4..32. */
    private final int radius;
    private final Queue<BlockPos> columns = new ArrayDeque<>();
    /** Row offset of the NEXT classified block; valid range -4..+9 (UP_ROWS + 1 = column done). */
    private int rowCursor = -DOWN_ROWS;
    private boolean active;

    public BaseScanner(int radius) {
        this.radius = Math.max(4, Math.min(32, radius));
    }

    public boolean active() {
        return active;
    }

    public int queuedColumns() {
        return columns.size();
    }

    /** Queue one scan around {@code anchor}; replaces any older one for the same map. */
    public void start(long anchorLong) {
        BlockPos anchor = BlockPos.fromLong(anchorLong);
        columns.clear();
        rowCursor = -DOWN_ROWS;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                columns.add(anchor.add(dx, 0, dz));
            }
        }
        active = true;
        ItWasTall.LOGGER.debug("Base scan queued around {} ({} columns).",
                anchor.toShortString(), columns.size());
    }

    /**
     * Runs at most {@code budgetBlocks} classifications; appends found markers into {@code map}.
     *
     * @return true when the whole cube is finished
     */
    public boolean tick(ServerWorld world, BlockPos anchorPos, BaseMap map, int budgetBlocks) {
        while (budgetBlocks > 0 && !columns.isEmpty()) {
            BlockPos column = columns.peek();
            int y = anchorPos.getY() + rowCursor;
            classify(world, map, anchorPos, column, y);
            budgetBlocks--;
            if (rowCursor >= UP_ROWS) {     // +8 was the last row -> column done
                columns.poll();
                rowCursor = -DOWN_ROWS;
            } else {
                rowCursor++;
            }
        }
        if (columns.isEmpty()) active = false;
        return !active;
    }

    private void classify(ServerWorld world, BaseMap map, BlockPos anchorPos, BlockPos column, int y) {
        BlockPos pos = new BlockPos(column.getX(), y, column.getZ());
        var state = world.getBlockState(pos);
        if (state.isAir()) return;

        if (BaseMap.isGlassy(state)) {
            if (isWindowSide(world, pos)) map.add(anchorPos, BaseMap.TYPE_WINDOW, pos);
            return;
        }
        if (state.getBlock() instanceof net.minecraft.block.DoorBlock) {
            // one marker per door: the lower half carries the position
            if (state.get(net.minecraft.block.DoorBlock.HALF) != net.minecraft.block.enums.DoubleBlockHalf.UPPER) {
                map.add(anchorPos, BaseMap.TYPE_DOOR, pos);
            }
            return;
        }
        if (state.getBlock() instanceof net.minecraft.block.ChestBlock
                || state.getBlock() instanceof net.minecraft.block.BarrelBlock) {
            map.add(anchorPos, BaseMap.TYPE_CHEST, pos);
            return;
        }
        if (state.getLuminance() > 0) {
            map.add(anchorPos, BaseMap.TYPE_LIGHT, pos);
        }
    }

    /** A window only counts when one could actually stand next to it on the outside. */
    private static boolean isWindowSide(ServerWorld world, BlockPos pos) {
        for (Direction dir : HORIZONTAL) {
            var side = world.getBlockState(pos.offset(dir));
            if (side.isAir() && world.getBlockState(pos.offset(dir).up()).isAir()) return true;
        }
        return false;
    }

    private static final List<Direction> HORIZONTAL =
            List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);
}
