package com.itwastall.world;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.noise.SimplexNoiseSampler;
import net.minecraft.util.math.random.Random;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * THE BELOW: what lies under the bedrock. Endless dark caves between a bedrock floor and a bedrock ceiling
 * (y 0..127): big hollow chambers joined by winding tunnels, of pale Forgotten Stone and dark layered Oldrock,
 * gravel on the floors, still black water in the lowest hollows. Every block is a pure function of the seed and the position.
 */
public final class BelowCaves {
    public static final int HEIGHT = 128;
    private static final Map<Long, Noise> NOISE = new ConcurrentHashMap<>();

    private record Noise(SimplexNoiseSampler cheese, SimplexNoiseSampler tunnelA, SimplexNoiseSampler tunnelB,
                         SimplexNoiseSampler stone, SimplexNoiseSampler floor) {}

    private BelowCaves() {}

    private static Noise noise(long seed) {
        return NOISE.computeIfAbsent(seed, s -> {
            Random r = Random.create(s);
            return new Noise(new SimplexNoiseSampler(r), new SimplexNoiseSampler(r), new SimplexNoiseSampler(r),
                    new SimplexNoiseSampler(r), new SimplexNoiseSampler(r));
        });
    }

    /** True where the cave is open (air or water). */
    public static boolean open(long seed, int x, int y, int z) {
        if (y <= 4 || y >= HEIGHT - 5) return false;
        Noise n = noise(seed);
        // squash the chambers toward the middle height (y 20..100), closed near floor and ceiling
        double edge = Math.min(y - 4, HEIGHT - 5 - y) / 16.0;
        double fade = MathHelper.clamp(edge, 0.0, 1.0);
        double cheese = n.cheese().sample(x / 56.0, y / 26.0, z / 56.0) + 0.25 * n.cheese().sample(x / 19.0, y / 12.0, z / 19.0);
        if (cheese * fade > 0.30) return true;
        // winding tunnels: where two noises are both near zero
        double a = n.tunnelA().sample(x / 64.0, y / 28.0, z / 64.0), b = n.tunnelB().sample(x / 64.0, y / 28.0, z / 64.0);
        return a * a + b * b < 0.02 * fade;
    }

    public static BlockState block(long seed, int x, int y, int z) {
        return block(seed, x, y, z, open(seed, x, y, z), open(seed, x, y + 1, z));
    }

    /** The whole column at x, z (index = y), computing each "open" only once. */
    public static void column(long seed, int x, int z, BlockState[] out) {
        boolean[] open = new boolean[HEIGHT + 1];
        for (int y = 0; y <= HEIGHT; y++) open[y] = y < HEIGHT && open(seed, x, y, z);
        for (int y = 0; y < HEIGHT && y < out.length; y++) out[y] = block(seed, x, y, z, open[y], open[y + 1]);
    }

    private static BlockState block(long seed, int x, int y, int z, boolean open, boolean openAbove) {
        if (y <= 0 || y >= HEIGHT - 1) return Blocks.BEDROCK.getDefaultState();
        if (y <= 3 || y >= HEIGHT - 4) {
            long h = hash(seed, x, y, z);
            if ((h & 3) != 0 || y <= 1 || y >= HEIGHT - 2) return Blocks.BEDROCK.getDefaultState();
        }
        if (open) {
            // still black water in the lowest hollows
            return y < 13 ? Blocks.WATER.getDefaultState() : Blocks.AIR.getDefaultState();
        }
        Noise n = noise(seed);
        boolean floor = openAbove;
        if (floor && n.floor().sample(x / 9.0, 0, z / 9.0) < -0.55) return Blocks.GRAVEL.getDefaultState();
        double s = n.stone().sample(x / 24.0, y / 16.0, z / 24.0);
        if (s > 0.3) return com.itwastall.registry.ModBlocks.OLDROCK.getDefaultState();
        return com.itwastall.registry.ModBlocks.FORGOTTEN_STONE.getDefaultState();
    }

    static long hash(long seed, int x, int y, int z) {
        long h = seed ^ (x * 341873128712L) ^ (y * 132897987541L) ^ (z * 42317861L);
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        return h;
    }
}
