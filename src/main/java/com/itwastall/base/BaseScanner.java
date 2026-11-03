package com.itwastall.base;

import com.itwastall.ItWasTall;
import com.itwastall.config.ItWasTallConfig;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.LightType;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * Scans a base cube column by column (MODPLAN §8.2), then flood-fills the interior
 * (§8.3): air space reachable from the anchor's room, bounded by walls, windows and
 * liquids, with a hard volume cap (config {@code baseInteriorCap}, default 2400 cells).
 * Sky-lit cells (skylight level 15) are NOT interior — open courtyards above a roofless
 * area exit the flood — exactly the "skylight + blocks above" rule of §8.3. Dark interior
 * corners (block light < 4) become TYPE_CORNER markers, reclaimed by future
 * SearchBaseGoal / the Hide-And-Seek task.
 *
 * <p>Budgets: one shared {@code scanBlocksPerTick} block budget per tick (config), split
 * fairly by BaseTracker when several players scan at once. Nothing here touches chunks.
 */
public final class BaseScanner {
    private static final int UP_ROWS = 8;    // above the anchor's floor row (walls, roof)
    private static final int DOWN_ROWS = 4;  // below (porches, basements)
    /** Rows one column costs (debug readouts, budget math). */
    public static final int ROWS_TOTAL = UP_ROWS + DOWN_ROWS + 1;

    /** Horizontal radius (anchor +-radius), clamped to 4..32. */
    private final int radius;
    private final Queue<BlockPos> columns = new ArrayDeque<>();
    /** Row offset of the NEXT classified block; -4..+9 (UP_ROWS + 1 = column done). */
    private int rowCursor = -DOWN_ROWS;
    private boolean columnsDone;

    /** Flood-fill state (§8.3): BFS frontier + visited cells (cap keeps memory sane). */
    private final Queue<BlockPos> frontier = new ArrayDeque<>();
    private final Set<Long> visited = new HashSet<>();
    private int roomCells;
    private int cornerMarkers;
    private boolean seeded;

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
        columnsDone = false;
        frontier.clear();
        visited.clear();
        roomCells = 0;
        cornerMarkers = 0;
        seeded = false;
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
     * Runs at most {@code budgetBlocks} classifications/flood steps; appends markers.
     *
     * @return true when BOTH passes are finished
     */
    public boolean tick(ServerWorld world, BlockPos anchorPos, BaseMap map, int budgetBlocks) {
        int budget = sweepColumns(world, anchorPos, map, budgetBlocks);
        if (columns.isEmpty() && !columnsDone) {
            columnsDone = true;
            seedFlood(world, anchorPos);
        }
        if (columnsDone) budget = sweepFlood(world, anchorPos, map, budget);

        if (columns.isEmpty() && frontier.isEmpty()) {
            active = false;
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------ columns ----

    private int sweepColumns(ServerWorld world, BlockPos anchorPos, BaseMap map, int budget) {
        while (budget > 0 && !columns.isEmpty()) {
            BlockPos column = columns.peek();
            int y = anchorPos.getY() + rowCursor;
            classify(world, map, anchorPos, column, y);
            budget--;
            if (rowCursor >= UP_ROWS) {     // +8 was the last row -> column done
                columns.poll();
                rowCursor = -DOWN_ROWS;
            } else {
                rowCursor++;
            }
        }
        return budget;
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

    // -------------------------------------------------------- flood fill ----

    /**
     * The seed is the first open cell near the bed (up, then side neighbours, then below):
     * the bed itself is solid, the room around it is the flood that maps the interior.
     */
    private void seedFlood(ServerWorld world, BlockPos anchorPos) {
        BlockPos[] candidates = new BlockPos[]{
                anchorPos.up(),
                anchorPos.up(2),
                anchorPos.up().north(), anchorPos.up().south(),
                anchorPos.up().east(), anchorPos.up().west(),
                anchorPos.down()};
        Set<Long> already = new HashSet<>();
        for (BlockPos candidate : candidates) {
            if (visited.contains(candidate.asLong())
                    || already.contains(candidate.asLong())) continue;
            if (!passable(world, candidate)) continue;
            already.add(candidate.asLong());
            frontier.add(candidate);
            visited.add(candidate.asLong());
            break;
        }
        seeded = true;
    }

    private int sweepFlood(ServerWorld world, BlockPos anchorPos, BaseMap map, int budget) {
        if (!seeded) return budget;
        int roomCap = Math.max(200, ItWasTallConfig.INSTANCE.baseInteriorCap);
        int cornerCap = Math.max(8, ItWasTallConfig.INSTANCE.baseCornerCap);

        while (budget > 0 && !frontier.isEmpty()) {
            BlockPos cell = frontier.poll();
            for (Direction dir : Direction.values()) {
                BlockPos next = cell.offset(dir);
                if (!inFloodRange(anchorPos, next)) continue;
                long key = next.asLong();
                if (visited.contains(key)) continue;
                visited.add(key);           // reserve even before classifying (budget-safe)
                if (!passable(world, next)) continue;
                if (roomCells >= roomCap) {            // cap reached: It stops mapping here
                    frontier.clear();                  // and the flood finishes cleanly
                    return budget;
                }

                budget--;
                boolean interior = world.getLightLevel(LightType.SKY, next) < 15;
                if (interior) {
                    roomCells++;
                    map.add(anchorPos, BaseMap.TYPE_ROOM, next);
                    if (cornerMarkers < cornerCap
                            && world.getLightLevel(LightType.BLOCK, next) < 4) {
                        cornerMarkers++;
                        map.add(anchorPos, BaseMap.TYPE_CORNER, next);
                    }
                }
                frontier.add(next);         // flood continues through open space only
            }
        }
        return budget;
    }

    /** Flood stays inside the anchor's neighbourhood (2 blocks of slack, +-8 vertically). */
    private static boolean inFloodRange(BlockPos anchorPos, BlockPos pos) {
        int dx = Math.abs(pos.getX() - anchorPos.getX());
        int dz = Math.abs(pos.getZ() - anchorPos.getZ());
        int dy = pos.getY() - anchorPos.getY();
        return dx <= 18 && dz <= 18 && dy >= -8 && dy <= UP_ROWS + 2;
    }

    /** Enterable: air, or any non-colliding state (plants, torches...) — but liquids and glass STOP
     *  the flood: you cannot float through a wall of tinted glass, and neither can Interior-It. */
    private static boolean passable(ServerWorld world, BlockPos pos) {
        var state = world.getBlockState(pos);
        if (state.isAir()) return true;
        if (!state.getFluidState().isEmpty()) return false;
        if (BaseMap.isGlassy(state)) return false;
        return state.getCollisionShape(world, pos).isEmpty();
    }
}
