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
 * Smart base detection (MODPLAN §8 notes). Every chunk gets a score of how likely it is to be this
 * player's base, and a set of the base "kit" it contains:
 * <ul>
 *   <li>blocks the player PLACED (a bed, chests, a furnace, doors and windows weigh much more than dirt);</li>
 *   <li>infrastructure found by a light probe around the player that is NOT part of a generated
 *       structure (an old base from before the mod) — a village house never counts;</li>
 *   <li>time spent there (more when sheltered at night).</li>
 * </ul>
 * Neighbouring strong chunks merge into clusters. A cluster is a base only with a real kit: a bed and
 * one more of {storage, furnace/crafting, door, window}, or three of them without a bed. Time alone,
 * or a pile of dirt, never makes a base.
 */
public final class ChunkScores {
    /** A chunk joins a cluster from this score on. */
    public static final double CHUNK_MIN = 6.0;
    /** A cluster this strong (with a kit) is a base even without a bed click. */
    public static final double BASE_SCORE = 30.0;

    public static final int BED = 1, STORAGE = 2, WORK = 4, DOOR = 8, WINDOW = 16, LIGHT = 32;
    private static final int KIT = BED | STORAGE | WORK | DOOR | WINDOW;

    /** Found by the probe (not placed, not in a structure): player -> chunk -> pos -> category. Session only. */
    private static final Map<UUID, Map<Long, Map<Long, Integer>>> FOUND = new HashMap<>();

    private ChunkScores() {
    }

    public static void forget(UUID player) {
        FOUND.remove(player);
    }

    /** One cluster of neighbouring strong chunks. {@code center} is its bed, else a chest, else a placed block near the middle. */
    public record Cluster(List<Long> chunks, double score, int kit, BlockPos center, boolean centerIsBed) {
        public boolean contains(BlockPos pos) {
            return chunks.contains(new ChunkPos(pos).toLong());
        }

        /** Has the base kit: a bed + one more piece, or three pieces without a bed. */
        public boolean hasKit() {
            int pieces = Integer.bitCount(kit & KIT);
            return (kit & BED) != 0 ? pieces >= 2 : pieces >= 3;
        }

        public boolean isBase() {
            return hasKit() && score >= BASE_SCORE;
        }

        public String kitString() {
            StringBuilder sb = new StringBuilder();
            if ((kit & BED) != 0) sb.append("bed ");
            if ((kit & STORAGE) != 0) sb.append("chest ");
            if ((kit & WORK) != 0) sb.append("furnace/crafting ");
            if ((kit & DOOR) != 0) sb.append("door ");
            if ((kit & WINDOW) != 0) sb.append("window ");
            if ((kit & LIGHT) != 0) sb.append("light ");
            return sb.isEmpty() ? "nothing" : sb.toString().trim();
        }
    }

    /** Called every 20 s for a player in the Overworld: time spent here adds up. */
    public static void addDwell(ItWasTallState state, UUID player, BlockPos pos, boolean shelteredAtNight) {
        state.addChunkDwell(player, new ChunkPos(pos).toLong(), shelteredAtNight ? 1.0f : 0.35f);
    }

    /** The kit category of a block (0 = plain block). */
    public static int category(BlockState st) {
        var b = st.getBlock();
        if (b instanceof BedBlock) return BED;
        if (b instanceof net.minecraft.block.ChestBlock || b instanceof net.minecraft.block.BarrelBlock
                || b instanceof net.minecraft.block.ShulkerBoxBlock) return STORAGE;
        if (b instanceof net.minecraft.block.AbstractFurnaceBlock || b instanceof net.minecraft.block.CraftingTableBlock) return WORK;
        if (b instanceof net.minecraft.block.DoorBlock) return DOOR;
        if (BaseMap.isGlassy(st)) return WINDOW;
        if (st.getLuminance() > 0) return LIGHT;
        return 0;
    }

    private static double weight(int category) {
        return switch (category) {
            case BED -> 12.0;
            case STORAGE, WORK -> 5.0;
            case DOOR, WINDOW -> 3.0;
            case LIGHT -> 2.0;
            default -> 1.0;
        };
    }

    /**
     * Light probe around the player (17 x 9 x 17): records beds, chests, furnaces, doors, windows and lights
     * that are NOT inside a generated structure and were not placed by this player (those count already).
     */
    public static void probe(ServerWorld world, ItWasTallState state, UUID player, BlockPos center) {
        Set<Long> owned = state.placedBlocks(player);
        Map<Long, Map<Long, Integer>> found = FOUND.computeIfAbsent(player, u -> new HashMap<>());
        // the probed box replaces what was known there (a removed chest stops counting)
        for (Map<Long, Integer> m : found.values()) {
            m.keySet().removeIf(p -> {
                BlockPos q = BlockPos.fromLong(p);
                return Math.abs(q.getX() - center.getX()) <= 8 && Math.abs(q.getZ() - center.getZ()) <= 8
                        && q.getY() >= center.getY() - 3 && q.getY() <= center.getY() + 5;
            });
        }
        BlockPos.Mutable p = new BlockPos.Mutable();
        Map<Long, Boolean> structureByChunk = new HashMap<>();
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                for (int dy = -3; dy <= 5; dy++) {
                    p.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (!world.isChunkLoaded(p)) continue;
                    BlockState st = world.getBlockState(p);
                    if (st.isAir()) continue;
                    int cat = category(st);
                    if (cat == 0 || owned.contains(p.asLong())) continue;
                    long chunk = new ChunkPos(p).toLong();
                    // cheap pre-filter: a chunk without any structure start nearby never needs the piece test
                    boolean maybeStructure = structureByChunk.computeIfAbsent(chunk,
                            c -> world.getStructureAccessor().hasStructureReferences(p));
                    if (maybeStructure && Ownership.inGeneratedStructure(world, p)) continue;
                    found.computeIfAbsent(chunk, c -> new HashMap<>()).put(p.asLong(), cat);
                }
            }
        }
        found.values().removeIf(Map::isEmpty);
    }

    private record ChunkData(double score, int kit) {
    }

    private static Map<Long, ChunkData> chunkData(ServerWorld world, ItWasTallState state, UUID player) {
        Map<Long, Double> score = new HashMap<>();
        Map<Long, Integer> kit = new HashMap<>();
        for (long packed : state.placedBlocks(player)) {
            BlockPos p = BlockPos.fromLong(packed);
            long chunk = new ChunkPos(p).toLong();
            int cat;
            if (world.isChunkLoaded(p)) {
                BlockState st = world.getBlockState(p);
                if (st.isAir()) continue;                          // placed and removed again
                cat = category(st);
            } else {
                cat = 0;                                           // unloaded: counts as a plain block
            }
            score.merge(chunk, weight(cat), Double::sum);
            if (cat != 0) kit.merge(chunk, cat, (a, b) -> a | b);
        }
        FOUND.getOrDefault(player, Map.of()).forEach((chunk, blocks) -> blocks.values().forEach(cat -> {
            score.merge(chunk, weight(cat) * 0.8, Double::sum);
            kit.merge(chunk, cat, (a, b) -> a | b);
        }));
        state.chunkDwell(player).forEach((chunk, dwell) -> score.merge(chunk, (double) dwell * 0.5, Double::sum));
        Map<Long, ChunkData> out = new HashMap<>();
        score.forEach((chunk, s) -> out.put(chunk, new ChunkData(s, kit.getOrDefault(chunk, 0))));
        return out;
    }

    /** Strong chunks merged with their 8 neighbours, strongest cluster first. */
    public static List<Cluster> clusters(ServerWorld world, ItWasTallState state, UUID player) {
        Map<Long, ChunkData> data = chunkData(world, state, player);
        Set<Long> strong = new HashSet<>();
        data.forEach((chunk, d) -> {
            if (d.score() >= CHUNK_MIN) strong.add(chunk);
        });
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
            int kit = 0;
            for (long c : members) {
                total += data.get(c).score();
                kit |= data.get(c).kit();
            }
            out.add(center(world, state, player, members, data, total, kit));
        }
        out.sort(Comparator.comparingDouble(Cluster::score).reversed());
        return out;
    }

    /** Anchor of a cluster: its bed, else its chest, else the placed block nearest the weighted middle. */
    private static Cluster center(ServerWorld world, ItWasTallState state, UUID player, List<Long> members,
                                  Map<Long, ChunkData> data, double total, int kit) {
        Set<Long> chunkSet = new HashSet<>(members);
        List<BlockPos> candidates = new ArrayList<>();
        for (long packed : state.placedBlocks(player)) {
            BlockPos p = BlockPos.fromLong(packed);
            if (chunkSet.contains(new ChunkPos(p).toLong())) candidates.add(p);
        }
        FOUND.getOrDefault(player, Map.of()).forEach((chunk, blocks) -> {
            if (chunkSet.contains(chunk)) blocks.keySet().forEach(p -> candidates.add(BlockPos.fromLong(p)));
        });
        for (int wanted : new int[]{BED, STORAGE}) {
            for (BlockPos p : candidates) {
                if (world.isChunkLoaded(p) && category(world.getBlockState(p)) == wanted) {
                    return new Cluster(members, total, kit, p.toImmutable(), wanted == BED);
                }
            }
        }
        double sx = 0, sz = 0, sw = 0;
        for (long c : members) {
            double w = data.get(c).score();
            sx += (ChunkPos.getPackedX(c) * 16 + 8) * w;
            sz += (ChunkPos.getPackedZ(c) * 16 + 8) * w;
            sw += w;
        }
        double mx = sx / Math.max(1e-6, sw), mz = sz / Math.max(1e-6, sw);
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : candidates) {
            double d = (p.getX() - mx) * (p.getX() - mx) + (p.getZ() - mz) * (p.getZ() - mz);
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        if (best == null) {
            int x = (int) Math.floor(mx), z = (int) Math.floor(mz);
            int y = world.isChunkLoaded(new BlockPos(x, 0, z))
                    ? world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z) : 64;
            best = new BlockPos(x, y, z);
        }
        return new Cluster(members, total, kit, best.toImmutable(), false);
    }
}
