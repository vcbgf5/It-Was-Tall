package com.itwastall.entity;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.function.Function;

/**
 * DigPathfinder (MODPLAN §13): A* through rock for the Tall One's 1 x 4 body.
 *
 * <p>A node is the block its feet stand in. Moving one block costs 2 plus the time an iron pickaxe
 * needs for the hardest block that has to go (all cells of a step are broken together, as in
 * {@code TallOneEntity.tickDig}). Fluids, bedrock / obsidian-class blocks (hardness &lt; 0 or &ge; 50)
 * and anything with a block entity (chests, furnaces, spawners — never eat the player's things) are
 * walls. The search is capped ({@link #MAX_NODES} expansions, {@link #MAX_RANGE} blocks from the
 * start) so a single plan stays cheap; the caller re-plans every few seconds as the player moves.
 */
public final class DigPathfinder {
    public static final int MAX_NODES = 2500;
    public static final int MAX_RANGE = 48;

    private DigPathfinder() {
    }

    private record Node(long pos, int g, int f) {
    }

    /**
     * @param breakTicks ticks to break a block of the given hardness (the entity's own tool speed)
     * @return the feet positions to walk through, start excluded, or null when no path was found
     */
    @Nullable
    public static List<BlockPos> find(World world, BlockPos start, BlockPos goal, Function<Float, Integer> breakTicks) {
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingInt(Node::f));
        Map<Long, Integer> best = new HashMap<>();
        Map<Long, Long> parent = new HashMap<>();
        long s = start.asLong();
        best.put(s, 0);
        open.add(new Node(s, 0, 2 * start.getManhattanDistance(goal)));
        int expanded = 0;
        while (!open.isEmpty() && expanded < MAX_NODES) {
            Node n = open.poll();
            Integer known = best.get(n.pos);
            if (known != null && n.g > known) continue;
            expanded++;
            BlockPos p = BlockPos.fromLong(n.pos);
            if (reached(p, goal)) return rebuild(parent, n.pos, s);
            for (Direction d : Direction.values()) {
                BlockPos q = p.offset(d);
                if (q.getManhattanDistance(start) > MAX_RANGE) continue;
                int cost = stepCost(world, p, d, breakTicks);
                if (cost < 0) continue;
                int g = n.g + cost;
                long qk = q.asLong();
                Integer old = best.get(qk);
                if (old != null && old <= g) continue;
                best.put(qk, g);
                parent.put(qk, n.pos);
                open.add(new Node(qk, g, g + 2 * q.getManhattanDistance(goal)));
            }
        }
        return null;
    }

    /** Close enough to strike: next to the player, roughly at the same height. */
    private static boolean reached(BlockPos p, BlockPos goal) {
        int dx = Math.abs(p.getX() - goal.getX()), dz = Math.abs(p.getZ() - goal.getZ());
        int dy = p.getY() - goal.getY();
        return dx <= 1 && dz <= 1 && dy >= -1 && dy <= 1;
    }

    /** Cost of moving the feet from {@code feet} one block in {@code dir}; -1 when impossible. */
    public static int stepCost(World world, BlockPos feet, Direction dir, Function<Float, Integer> breakTicks) {
        int ticks = 0;
        for (BlockPos c : cellsFor(feet, dir)) {
            BlockState st = world.getBlockState(c);
            if (!st.getFluidState().isEmpty()) return -1;
            if (st.getCollisionShape(world, c).isEmpty()) continue;
            if (world.getBlockEntity(c) != null) return -1;          // never dig through the player's things
            float hardness = st.getHardness(world, c);
            if (hardness < 0 || hardness >= 50f) return -1;           // bedrock, obsidian...
            ticks = Math.max(ticks, breakTicks.apply(hardness));
        }
        return 2 + ticks;
    }

    /** The cells that must be clear to move the 1 x 4 body one block in {@code dir}. */
    public static List<BlockPos> cellsFor(BlockPos feet, Direction dir) {
        List<BlockPos> cells = new ArrayList<>(4);
        switch (dir) {
            case DOWN -> cells.add(feet.down());
            case UP -> cells.add(feet.up(4));
            default -> {
                BlockPos side = feet.offset(dir);
                for (int i = 0; i < 4; i++) cells.add(side.up(i));
            }
        }
        return cells;
    }

    private static List<BlockPos> rebuild(Map<Long, Long> parent, long end, long start) {
        List<BlockPos> path = new ArrayList<>();
        long cur = end;
        while (cur != start) {
            path.add(BlockPos.fromLong(cur));
            Long prev = parent.get(cur);
            if (prev == null) break;
            cur = prev;
        }
        Collections.reverse(path);
        return path;
    }
}
