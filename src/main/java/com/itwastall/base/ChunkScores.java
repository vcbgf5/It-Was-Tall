package com.itwastall.base;

import com.itwastall.world.ItWasTallState;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * MODPLAN §8 notes ("wykrywanie baz bez pełnej zabudowy"): every chunk gets a score of how likely it is
 * to be this player's base, from the blocks they placed there (infrastructure counts much more than
 * dirt) and the time they spent there (more when sheltered at night). Neighbouring strong chunks are
 * merged into clusters; the strongest cluster can become the base when the player never used a bed.
 *
 * <p>The time part is saved in {@link ItWasTallState}; the placed part is recomputed from the saved
 * placement history, so nothing here needs its own file.
 */
public final class ChunkScores {
    /** A chunk joins a cluster from this score on. */
    public static final double CHUNK_MIN = 6.0;
    /** A cluster this strong is a base even without a bed. */
    public static final double BASE_SCORE = 45.0;

    private ChunkScores() {
    }

    /** One cluster of neighbouring strong chunks. {@code center} is a placed block near its weighted middle. */
    public record Cluster(List<Long> chunks, double score, BlockPos center) {
        public boolean contains(BlockPos pos) {
            return chunks.contains(new ChunkPos(pos).toLong());
        }
    }

    /** Called every 20 s for a player in the Overworld: time spent here adds up. */
    public static void addDwell(ItWasTallState state, UUID player, BlockPos pos, boolean shelteredAtNight) {
        state.addChunkDwell(player, new ChunkPos(pos).toLong(), shelteredAtNight ? 1.0f : 0.35f);
    }

    /** Weight of one placed block: infrastructure says "base", plain blocks only a little. */
    private static double weight(ServerWorld world, BlockPos pos) {
        if (!world.isChunkLoaded(pos)) return 1.0;
        BlockState st = world.getBlockState(pos);
        if (st.isAir()) return 0.25;                       // placed and removed again
        var b = st.getBlock();
        if (b instanceof BedBlock) return 12.0;
        if (b instanceof net.minecraft.block.ChestBlock || b instanceof net.minecraft.block.BarrelBlock
                || b instanceof net.minecraft.block.AbstractFurnaceBlock || b instanceof net.minecraft.block.CraftingTableBlock) return 5.0;
        if (b instanceof net.minecraft.block.DoorBlock || BaseMap.isGlassy(st)) return 3.0;
        if (st.getLuminance() > 0) return 2.0;
        return 1.0;
    }

    /** Score per chunk for that player (placed blocks + time spent). */
    public static Map<Long, Double> scores(ServerWorld world, ItWasTallState state, UUID player) {
        Map<Long, Double> out = new HashMap<>();
        for (long packed : state.placedBlocks(player)) {
            BlockPos p = BlockPos.fromLong(packed);
            out.merge(new ChunkPos(p).toLong(), weight(world, p), Double::sum);
        }
        state.chunkDwell(player).forEach((chunk, dwell) -> out.merge(chunk, (double) dwell * 0.5, Double::sum));
        return out;
    }

    /** Strong chunks merged with their 8 neighbours, strongest cluster first. */
    public static List<Cluster> clusters(ServerWorld world, ItWasTallState state, UUID player) {
        Map<Long, Double> scores = scores(world, state, player);
        Set<Long> strong = new HashSet<>();
        scores.forEach((chunk, score) -> {
            if (score >= CHUNK_MIN) strong.add(chunk);
        });
        Set<Long> placed = state.placedBlocks(player);
        List<Cluster> out = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (long start : strong) {
            if (!seen.add(start)) continue;
            List<Long> members = new ArrayList<>();
            Deque<Long> queue = new ArrayDeque<>();
            queue.add(start);
            while (!queue.isEmpty()) {
                long c = queue.poll();
                members.add(c);
                int cx = ChunkPos.getPackedX(c), cz = ChunkPos.getPackedZ(c);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        long n = ChunkPos.toLong(cx + dx, cz + dz);
                        if (strong.contains(n) && seen.add(n)) queue.add(n);
                    }
                }
            }
            double total = 0;
            for (long c : members) total += scores.getOrDefault(c, 0.0);
            out.add(new Cluster(members, total, center(world, members, scores, placed)));
        }
        out.sort(Comparator.comparingDouble(Cluster::score).reversed());
        return out;
    }

    /** A real placed block closest to the score-weighted middle of the cluster (or the middle chunk's centre). */
    private static BlockPos center(ServerWorld world, List<Long> members, Map<Long, Double> scores, Set<Long> placed) {
        double sx = 0, sz = 0, sw = 0;
        for (long c : members) {
            double w = scores.getOrDefault(c, 0.0);
            sx += (ChunkPos.getPackedX(c) * 16 + 8) * w;
            sz += (ChunkPos.getPackedZ(c) * 16 + 8) * w;
            sw += w;
        }
        double mx = sx / Math.max(1e-6, sw), mz = sz / Math.max(1e-6, sw);
        Set<Long> chunkSet = new HashSet<>(members);
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (long packed : placed) {
            BlockPos p = BlockPos.fromLong(packed);
            if (!chunkSet.contains(new ChunkPos(p).toLong())) continue;
            double d = (p.getX() - mx) * (p.getX() - mx) + (p.getZ() - mz) * (p.getZ() - mz);
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        if (best != null) return best.toImmutable();
        int x = (int) Math.floor(mx), z = (int) Math.floor(mz);
        int y = world.isChunkLoaded(new BlockPos(x, 0, z))
                ? world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z) : 64;
        return new BlockPos(x, y, z);
    }
}
