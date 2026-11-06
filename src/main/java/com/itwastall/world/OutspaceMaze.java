package com.itwastall.world;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.WallTorchBlock;
import net.minecraft.util.math.Direction;

/**
 * The maze of OUTSPACE — the dream world — as a pure function of block coordinates, so every chunk
 * builds its part on its own and the halls always line up across chunk borders.
 *
 * <p>Layout: a grid of 6 x 6 cells. Each cell is a 5 x 5 room ringed by oak plank walls (one block
 * thick, shared with the neighbour) with oak log posts in the corners. Every cell opens a 2-wide door
 * to the east or to the south (a binary-tree maze: everything is connected), and sometimes to the
 * other side too (a few loops). Now and then four cells merge into one big hall with a pillar in
 * the middle. Floor Y = {@value #FLOOR} (cobblestone), 5 blocks of air, ceiling Y = {@value #CEILING}
 * (cobblestone), solid stone above and below, bedrock at the bottom. Wall torches hang here and there.
 */
public final class OutspaceMaze {
    public static final int CELL = 6;
    public static final int FLOOR = 19, CEILING = 25, HEIGHT = 64;
    public static final int TORCH_Y = 22;

    private static final BlockState AIR = Blocks.AIR.getDefaultState();
    private static final BlockState STONE = Blocks.STONE.getDefaultState();
    private static final BlockState BEDROCK = Blocks.BEDROCK.getDefaultState();
    private static final BlockState COBBLE = Blocks.COBBLESTONE.getDefaultState();
    private static final BlockState MOSSY = Blocks.MOSSY_COBBLESTONE.getDefaultState();
    private static final BlockState PLANKS = Blocks.OAK_PLANKS.getDefaultState();
    private static final BlockState LOG = Blocks.OAK_LOG.getDefaultState();

    private OutspaceMaze() {
    }

    static long hash(long seed, int a, int b, int salt) {
        long h = seed ^ (a * 0x9E3779B97F4A7C15L) ^ (b * 0xC2B2AE3D27D4EB4FL) ^ (salt * 0x165667B19E3779F9L);
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return h;
    }

    /** Door from cell (cx, cz) to its east neighbour. */
    public static boolean east(long seed, int cx, int cz) {
        long h = hash(seed, cx, cz, 1);
        return (h & 1) == 0 || ((h >>> 8) & 1023) < 120;
    }

    /** Door from cell (cx, cz) to its south neighbour. */
    public static boolean south(long seed, int cx, int cz) {
        long h = hash(seed, cx, cz, 1);
        return (h & 1) == 1 || ((h >>> 20) & 1023) < 120;
    }

    /** Big halls: 2 x 2 groups of cells, about one group in nine. */
    public static boolean hall(long seed, int cx, int cz) {
        return Math.floorMod(hash(seed, Math.floorDiv(cx, 2), Math.floorDiv(cz, 2), 2), 9) == 0;
    }

    private static boolean sameHall(long seed, int ax, int az, int bx, int bz) {
        return Math.floorDiv(ax, 2) == Math.floorDiv(bx, 2) && Math.floorDiv(az, 2) == Math.floorDiv(bz, 2) && hall(seed, ax, az);
    }

    /** What stands in the room layer (Y FLOOR+1 .. CEILING-1) at column (x, z): 0 air, 1 plank wall, 2 log post. */
    public static int wall(long seed, int x, int z) {
        int lx = Math.floorMod(x, CELL), lz = Math.floorMod(z, CELL);
        int cx = Math.floorDiv(x, CELL), cz = Math.floorDiv(z, CELL);
        if (lx == 0 && lz == 0) return 2;
        if (lx == 0) {
            boolean open = sameHall(seed, cx - 1, cz, cx, cz) || (east(seed, cx - 1, cz) && (lz == 2 || lz == 3));
            return open ? 0 : 1;
        }
        if (lz == 0) {
            boolean open = sameHall(seed, cx, cz - 1, cx, cz) || (south(seed, cx, cz - 1) && (lx == 2 || lx == 3));
            return open ? 0 : 1;
        }
        return 0;
    }

    /** The block at any position of the dream world. */
    public static BlockState block(long seed, int x, int y, int z) {
        if (y <= 0) return BEDROCK;
        if (y < FLOOR) return STONE;
        if (y == FLOOR) return (hash(seed, x, z, 3) & 7) == 0 ? MOSSY : COBBLE;
        if (y == CEILING) return COBBLE;
        if (y > CEILING) return STONE;
        int w = wall(seed, x, z);
        if (w == 2) return LOG;
        if (w == 1) return PLANKS;
        if (y == TORCH_Y) {
            BlockState torch = torch(seed, x, z);
            if (torch != null) return torch;
        }
        return AIR;
    }

    /** A wall torch on the north wall of a cell (local x 4, local z 1), facing into the room, in ~40 % of cells. */
    private static BlockState torch(long seed, int x, int z) {
        int lx = Math.floorMod(x, CELL), lz = Math.floorMod(z, CELL);
        if (lx != 4 || lz != 1) return null;
        int cx = Math.floorDiv(x, CELL), cz = Math.floorDiv(z, CELL);
        if (wall(seed, x, z - 1) == 0) return null;                    // no wall behind it (a hall)
        if (Math.floorMod(hash(seed, cx, cz, 4), 10) >= 4) return null;
        return Blocks.WALL_TORCH.getDefaultState().with(WallTorchBlock.FACING, Direction.SOUTH);
    }

    /** Centre of cell (cx, cz) at floor level + 1 (where a player or mob stands). */
    public static double cellCenterX(int cx) {
        return cx * CELL + 3.5;
    }

    public static double cellCenterZ(int cz) {
        return cz * CELL + 3.5;
    }
}
