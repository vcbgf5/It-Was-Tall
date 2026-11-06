package com.itwastall.gametest;

import com.itwastall.base.BaseMap;
import com.itwastall.base.ChunkScores;
import com.itwastall.config.ItWasTallConfig;
import com.itwastall.entity.DigPathfinder;
import com.itwastall.entity.ShelterCheck;
import com.itwastall.entity.TallOneEntity;
import com.itwastall.registry.ModEntities;
import com.itwastall.world.ItWasTallState;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.List;
import java.util.UUID;

/**
 * MODPLAN §24.2 — server GameTests. Run with {@code ./gradlew runGametest} (a headless server that
 * runs every test and exits; the report goes to build/gametest/junit.xml).
 */
public class ItWasTallGameTests implements FabricGameTest {

    /** A roof and walls make a shelter; an open floor does not (ShelterCheck drives SINK and tasks). */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void shelterNeedsRoofAndWalls(TestContext ctx) {
        for (int x = 1; x <= 5; x++) for (int z = 1; z <= 5; z++) ctx.setBlockState(x, 0, z, Blocks.STONE);
        BlockPos feet = ctx.getAbsolutePos(new BlockPos(3, 1, 3));
        ctx.assertFalse(ShelterCheck.isSheltered(ctx.getWorld(), feet), "an open floor must not count as shelter");

        for (int x = 2; x <= 4; x++) {
            for (int z = 2; z <= 4; z++) {
                ctx.setBlockState(x, 3, z, Blocks.STONE);                          // roof
                if (x == 3 && z == 3) continue;
                for (int y = 1; y <= 2; y++) ctx.setBlockState(x, y, z, Blocks.STONE);  // walls
            }
        }
        ctx.assertTrue(ShelterCheck.isSheltered(ctx.getWorld(), feet), "a closed stone hut must count as shelter");
        ctx.complete();
    }

    /** Base markers survive the save format (offsets are packed into 9-bit fields). */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void baseMapRoundTrip(TestContext ctx) {
        BlockPos anchor = new BlockPos(100, 64, -40);
        BaseMap map = new BaseMap(anchor.asLong());
        BlockPos window = anchor.add(5, 2, -7), door = anchor.add(-12, 0, 3), chest = anchor.add(0, -1, 30);
        map.add(anchor, BaseMap.TYPE_WINDOW, window);
        map.add(anchor, BaseMap.TYPE_DOOR, door);
        map.add(anchor, BaseMap.TYPE_CHEST, chest);
        BaseMap back = BaseMap.fromNbt(map.toNbt());
        ctx.assertTrue(back.anchor() == anchor.asLong(), "anchor changed in the round trip");
        ctx.assertTrue(back.all(BaseMap.TYPE_WINDOW).equals(List.of(window)), "window marker lost: " + back.all(BaseMap.TYPE_WINDOW));
        ctx.assertTrue(back.all(BaseMap.TYPE_DOOR).equals(List.of(door)), "door marker lost: " + back.all(BaseMap.TYPE_DOOR));
        ctx.assertTrue(back.all(BaseMap.TYPE_CHEST).equals(List.of(chest)), "chest marker lost: " + back.all(BaseMap.TYPE_CHEST));
        ctx.complete();
    }

    /** The digging A* reaches the player through stone but never through obsidian. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void digPathAvoidsObsidian(TestContext ctx) {
        for (int x = 0; x < 8; x++) {
            for (int y = 1; y < 8; y++) {
                for (int z = 0; z < 8; z++) {
                    ctx.setBlockState(x, y, z, x == 4 && z < 6 ? Blocks.OBSIDIAN : Blocks.STONE);
                }
            }
        }
        BlockPos start = ctx.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos goal = ctx.getAbsolutePos(new BlockPos(7, 1, 1));
        List<BlockPos> path = DigPathfinder.find(ctx.getWorld(), start, goal, h -> Math.max(4, (int) Math.ceil(h * 5f)));
        ctx.assertTrue(path != null && !path.isEmpty(), "no dig path through the stone");
        BlockPos prev = start;
        for (BlockPos step : path) {
            Direction dir = null;
            for (Direction d : Direction.values()) if (prev.offset(d).equals(step)) dir = d;
            ctx.assertTrue(dir != null, "the path jumps from " + prev.toShortString() + " to " + step.toShortString());
            for (BlockPos cell : DigPathfinder.cellsFor(prev, dir)) {
                ctx.assertFalse(ctx.getWorld().getBlockState(cell).isOf(Blocks.OBSIDIAN), "the path digs through obsidian at " + cell.toShortString());
            }
            prev = step;
        }
        ctx.complete();
    }

    /** MODPLAN §4: two Tall Ones in the world — after a moment only one is left. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 60)
    public void onlyOneTallOne(TestContext ctx) {
        if (Math.max(1, ItWasTallConfig.INSTANCE.maxActiveIts) > 1) {
            ctx.complete();   // the server allows more than one: nothing to test
            return;
        }
        ItWasTallState state = ItWasTallState.getOrCreate(ctx.getWorld().getServer());
        state.forgetActiveIt();
        ctx.spawnEntity(ModEntities.TALL_ONE, 1, 1, 1);
        ctx.spawnEntity(ModEntities.TALL_ONE, 6, 1, 6);
        ctx.waitAndRun(10, () -> {
            int alive = ctx.getWorld().getEntitiesByType(ModEntities.TALL_ONE, ctx.getTestBox().expand(2), e -> !e.isRemoved()).size();
            ctx.killAllEntities();
            state.forgetActiveIt();
            ctx.assertTrue(alive <= 1, "found " + alive + " Tall Ones; the duplicate should have removed itself");
            ctx.complete();
        });
    }

    /** The world data keeps the one It, the chunk scores and the bed flag through a save. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void worldStateRoundTrip(TestContext ctx) {
        ItWasTallState st = new ItWasTallState();
        UUID it = UUID.randomUUID(), player = UUID.randomUUID();
        st.noteActiveIt(it, "minecraft:overworld", 1234L, 1000L);
        st.addChunkDwell(player, 77L, 2.5f);
        st.setBedAnchored(player, true);
        NbtCompound nbt = st.writeNbt(new NbtCompound(), ctx.getWorld().getRegistryManager());
        ItWasTallState back = ItWasTallState.fromNbt(nbt, ctx.getWorld().getRegistryManager());
        ctx.assertTrue(it.equals(back.activeIt()), "the remembered It was lost");
        ctx.assertTrue(back.activeItPos() == 1234L && "minecraft:overworld".equals(back.activeItDim()), "the It's position was lost");
        ctx.assertTrue(back.activeItFresh(1000L + 20), "a recently seen It must count");
        ctx.assertFalse(back.activeItFresh(1000L + ItWasTallState.ACTIVE_IT_STALE_TICKS + 1), "a long-unseen It must not block hauntings");
        Float dwell = back.chunkDwell(player).get(77L);
        ctx.assertTrue(dwell != null && Math.abs(dwell - 2.5f) < 1e-4, "chunk time lost: " + dwell);
        ctx.assertTrue(back.isBedAnchored(player), "bed flag lost");
        ctx.complete();
    }

    /** Two neighbouring chunks full of placed blocks make one cluster; a lone far chunk stays separate. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void chunkClustersMergeNeighbours(TestContext ctx) {
        ItWasTallState st = new ItWasTallState();
        UUID player = UUID.randomUUID();
        int bx = 160_000, bz = 160_000;   // far away: unloaded chunks, every placed block weighs 1
        for (int i = 0; i < 30; i++) {
            st.recordPlacedBlock(player, new BlockPos(bx + (i % 16), 70, bz + i / 16).asLong());
            st.recordPlacedBlock(player, new BlockPos(bx + 16 + (i % 16), 70, bz + i / 16).asLong());
        }
        for (int i = 0; i < 10; i++) st.recordPlacedBlock(player, new BlockPos(bx + 400 + i, 70, bz).asLong());
        List<ChunkScores.Cluster> clusters = ChunkScores.clusters(ctx.getWorld(), st, player);
        ctx.assertTrue(clusters.size() == 2, "expected 2 clusters, got " + clusters.size());
        ChunkScores.Cluster best = clusters.get(0);
        ctx.assertTrue(best.chunks().size() == 2, "neighbouring chunks were not merged: " + best.chunks().size());
        ctx.assertTrue(Math.abs(best.score() - 60.0) < 1e-6, "cluster score should be 60, got " + best.score());
        ctx.assertFalse(best.isBase(), "a pile of plain blocks must not be a base (no bed, chest, furnace, door or window)");
        ctx.assertTrue(best.contains(best.center()), "the cluster centre lies outside the cluster");
        ctx.complete();
    }

    /** Your own chest + furnace + window (and some building) make a base; the anchor is the chest. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void ownKitMakesABase(TestContext ctx) {
        ItWasTallState st = new ItWasTallState();
        UUID player = UUID.randomUUID();
        BlockPos chest = new BlockPos(1, 1, 1), furnace = new BlockPos(2, 1, 1), glass = new BlockPos(3, 1, 1);
        ctx.setBlockState(chest, Blocks.CHEST);
        ctx.setBlockState(furnace, Blocks.FURNACE);
        ctx.setBlockState(glass, Blocks.GLASS);
        for (BlockPos p : List.of(chest, furnace, glass)) st.recordPlacedBlock(player, ctx.getAbsolutePos(p).asLong());
        for (int x = 0; x < 5; x++) {
            for (int z = 3; z < 7; z++) {
                ctx.setBlockState(x, 1, z, Blocks.OAK_PLANKS);
                st.recordPlacedBlock(player, ctx.getAbsolutePos(new BlockPos(x, 1, z)).asLong());
            }
        }
        List<ChunkScores.Cluster> clusters = ChunkScores.clusters(ctx.getWorld(), st, player);
        ctx.assertTrue(!clusters.isEmpty() && clusters.get(0).isBase(),
                "chest + furnace + window + 20 planks should be a base: " + (clusters.isEmpty() ? "no cluster" : clusters.get(0).kitString()));
        ctx.assertTrue(clusters.get(0).center().equals(ctx.getAbsolutePos(chest)), "without a bed the chest should be the anchor");
        ctx.complete();
    }

    /** OUTSPACE: floor and ceiling everywhere, and every room has at least one way out (no sealed cells). */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void outspaceMazeIsClosed(TestContext ctx) {
        long seed = 1_311_666L;
        for (int cx = -40; cx <= 40; cx++) {
            for (int cz = -40; cz <= 40; cz++) {
                int x0 = cx * com.itwastall.world.OutspaceMaze.CELL, z0 = cz * com.itwastall.world.OutspaceMaze.CELL;
                ctx.assertTrue(com.itwastall.world.OutspaceMaze.block(seed, x0 + 3, com.itwastall.world.OutspaceMaze.FLOOR, z0 + 3).isSolidBlock(ctx.getWorld(), BlockPos.ORIGIN), "no floor in cell " + cx + " " + cz);
                ctx.assertTrue(com.itwastall.world.OutspaceMaze.block(seed, x0 + 3, com.itwastall.world.OutspaceMaze.CEILING, z0 + 3).isSolidBlock(ctx.getWorld(), BlockPos.ORIGIN), "no ceiling in cell " + cx + " " + cz);
                ctx.assertTrue(com.itwastall.world.OutspaceMaze.block(seed, x0 + 3, com.itwastall.world.OutspaceMaze.FLOOR + 1, z0 + 3).isAir(), "the middle of cell " + cx + " " + cz + " is blocked");
                // a way out: any of the four walls has an opening at room height
                int exits = 0;
                for (int k = 1; k < com.itwastall.world.OutspaceMaze.CELL; k++) {
                    if (com.itwastall.world.OutspaceMaze.wall(seed, x0, z0 + k) == 0) exits++;
                    if (com.itwastall.world.OutspaceMaze.wall(seed, x0 + com.itwastall.world.OutspaceMaze.CELL, z0 + k) == 0) exits++;
                    if (com.itwastall.world.OutspaceMaze.wall(seed, x0 + k, z0) == 0) exits++;
                    if (com.itwastall.world.OutspaceMaze.wall(seed, x0 + k, z0 + com.itwastall.world.OutspaceMaze.CELL) == 0) exits++;
                }
                ctx.assertTrue(exits > 0, "cell " + cx + " " + cz + " is sealed");
            }
        }
        ctx.complete();
    }

    /** New AI states are appended: the saved ordinals of the old ones must never move. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void savedModeOrdinalsAreStable(TestContext ctx) {
        TallOneEntity.Mode[] m = TallOneEntity.Mode.values();
        String[] old = {"STARE", "CHASE", "SINK", "DIG", "PEEK", "EMERGE"};
        for (int i = 0; i < old.length; i++) {
            ctx.assertTrue(m[i].name().equals(old[i]), "Mode " + i + " is " + m[i] + ", saved worlds expect " + old[i]);
        }
        ctx.complete();
    }
}
