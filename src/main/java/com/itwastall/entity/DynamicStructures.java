package com.itwastall.entity;

import com.itwastall.ItWasTall;
import com.itwastall.base.BaseMap;
import com.itwastall.base.BaseTracker;
import com.itwastall.config.ItWasTallConfig;
import com.itwastall.director.Director;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DYNAMIC STRUCTURES (MODPLAN §17.1): rare, server-built world oddities that appear BEHIND a
 * player at night — a lonely cemetery, a roadside shrine, an abandoned cottage whose iron
 * door is wired to a dead redstone trail (the player's own flavour: "zepsute drzwi") — and
 * the black copy of the player's base ("czarna kopia bazy") built from their mapped BaseMap
 * bounds in blackstone and deepslate, 60-100 blocks away where they cannot see.
 *
 * <p>§17 checklist honour: they are RARE (chance config + one build per player per night),
 * NOT badly placed (behind the view cone, 60-100 blocks out, never near another player, never
 * on water), carry LORE (flavour written books in the cemetery/shrine/cottage chests), and
 * never touch the original base (they only ever place into fresh terrain). Everything is
 * plain block placement — no jigsaw, no chunk generation, so worldgen pressure is zero.
 */
public final class DynamicStructures {

    public enum Kind {CEMETERY, SHRINE, COTTAGE, BLACKCOPY}

    /** One build per player per night (the §17 frequency limit). */
    private static final Map<UUID, Ledger> LEDGERS = new HashMap<>();

    /** Max footprint of the black copy shell (§17.1: "ograniczenia wielkości"). */
    private static final int BLACK_MAX_W = 12, BLACK_MAX_H = 6;

    private DynamicStructures() {
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(DynamicStructures::tick);
    }

    public static void forget(UUID player) {
        LEDGERS.remove(player);
    }

    // ---------------------------------------------------------------- gate + spot

    private static void tick(MinecraftServer server) {
        if (server.getTicks() % 20 != 0) return;      // one decision tick per second
        ItWasTallConfig.Values cfg = ItWasTallConfig.INSTANCE;
        if (!cfg.dynamicStructures) return;
        if (Director.phase() == 0) return;            // PHASE PRE: total silence

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!eligible(player)) continue;
            Ledger ledger = LEDGERS.computeIfAbsent(player.getUuid(), u -> new Ledger());
            long day = dayIndex(player.getWorld());
            if (ledger.day != day) {
                ledger.day = day;
                ledger.count = 0;
            }
            if (ledger.count >= 1) continue;          // §17: one build per night
            if (player.getRandom().nextDouble() >= clamp01(cfg.structureChance)) continue;

            // black copy only when the player HAS a mapped base and its own chance fires;
            // otherwise the rare night flavour set: cemetery / shrine / cottage
            Kind kind;
            if (hasBase(player) && player.getRandom().nextDouble() < clamp01(cfg.blackCopyChance)) {
                kind = Kind.BLACKCOPY;
            } else {
                kind = switch (player.getRandom().nextInt(3)) {
                    case 0 -> Kind.CEMETERY;
                    case 1 -> Kind.SHRINE;
                    default -> Kind.COTTAGE;
                };
            }
            BlockPos spot = findSpot(player, player.getRandom(), kind);
            if (spot == null) continue;
            build(player, spot, kind);
            ledger.count++;
            ItWasTall.LOGGER.info("A {} rose behind {} at {}.", kind, player.getName().getString(),
                    spot.toShortString());
        }
    }

    private static boolean eligible(ServerPlayerEntity player) {
        if (player.isCreative() || player.isSpectator() || !player.isAlive()) return false;
        World world = player.getWorld();
        if (world.getRegistryKey() != World.OVERWORLD) return false;
        // §17: the structures are NIGHT creatures — by day the world stays honest
        return world.isNight();
    }

    private static boolean hasBase(ServerPlayerEntity player) {
        BaseMap map = BaseTracker.mapOf(player.getUuid());
        return map != null && map.count() >= 8;
    }

    /**
     * A buildable spot BEHIND the player (their view cone widened to 120..240 degrees), 60-100
     * blocks out, on dry solid ground, never within 40 blocks of another player (they must not
     * watch it appear). Black copies also refuse to crowd the original base.
     */
    static BlockPos findSpot(ServerPlayerEntity player, Random random, Kind kind) {
        boolean blackCopy = kind == Kind.BLACKCOPY;
        int radius = switch (kind) {
            case SHRINE -> 2;
            case COTTAGE -> 5;
            case CEMETERY -> 6;
            case BLACKCOPY -> 7;
        };
        ServerWorld world = player.getServerWorld();
        float yaw = player.getYaw() * MathHelper.RADIANS_PER_DEGREE;
        for (int attempt = 0; attempt < 24; attempt++) {
            float behindYaw = yaw + 180f + (random.nextFloat() - 0.5f) * 120f;
            double dist = 60.0 + random.nextDouble() * 40.0;
            int x = MathHelper.floor(player.getX() - MathHelper.sin(behindYaw) * dist);
            int z = MathHelper.floor(player.getZ() + MathHelper.cos(behindYaw) * dist);
            int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            if (y < world.getBottomY() + 1) continue;
            BlockPos ground = new BlockPos(x, y, z);
            BlockState floor = world.getBlockState(ground);
            if (floor.isAir() || floor.isLiquid()) continue;
            if (world.getBlockState(ground.up()).isLiquid()) continue;
            // never on a tree (the heightmap stops on logs under the canopy) and only on fairly flat land
            if (floor.isIn(net.minecraft.registry.tag.BlockTags.LOGS) || floor.isIn(net.minecraft.registry.tag.BlockTags.LEAVES)) continue;
            if (!flatEnough(world, ground, radius)) continue;
            // no other player close enough to watch the build happen
            boolean watched = false;
            for (ServerPlayerEntity other : world.getPlayers()) {
                if (!other.isAlive()) continue;
                if (other.getBlockPos().isWithinDistance(ground, 40)) watched = true;
            }
            if (watched) continue;
            // the black copy mirrors the base — it must not PRESS into it either
            if (blackCopy) {
                Long anchor = BaseTracker.anchorOf(player.getUuid());
                if (anchor != null && BlockPos.fromLong(anchor).isWithinDistance(ground, 48)) continue;
            }
            return ground.toImmutable();
        }
        return null;
    }

    // ---------------------------------------------------------------- build entry

    /** Builds the structure RIGHT NOW around the given ground block. Server-side only. */
    public static void build(ServerPlayerEntity player, BlockPos ground, Kind kind) {
        ServerWorld world = player.getServerWorld();
        switch (kind) {
            case CEMETERY -> buildCemetery(world, ground, player);
            case SHRINE -> buildShrine(world, ground, player);
            case COTTAGE -> buildCottage(world, ground, player);
            case BLACKCOPY -> buildBlackCopy(world, ground, player);
        }
        // PHASES §2: the world itself grew stranger tonight
        if (Director.phase() == 1) Director.addTension(1.0);
    }

    /** Test/event trigger (/itwastall event structure X): skips chance and the ledger. */
    public static boolean trigger(ServerPlayerEntity player, Kind kind) {
        BlockPos spot = findSpot(player, player.getRandom(), kind);
        if (spot == null) return false;
        build(player, spot, kind);
        return true;
    }

    // ---------------------------------------------------------------- site preparation

    /** Connectable blocks placed during a build: their connections are fixed in one final pass. */
    private static final List<BlockPos> CONNECT = new java.util.ArrayList<>();

    /** Max height difference inside the footprint: 3 blocks (the foundation hides the rest). */
    private static boolean flatEnough(ServerWorld world, BlockPos ground, int radius) {
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int dx = -radius; dx <= radius; dx += Math.max(1, radius)) {
            for (int dz = -radius; dz <= radius; dz += Math.max(1, radius)) {
                int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, ground.getX() + dx, ground.getZ() + dz);
                BlockState top = world.getBlockState(new BlockPos(ground.getX() + dx, y - 1, ground.getZ() + dz));
                if (top.isLiquid() || top.isIn(net.minecraft.registry.tag.BlockTags.LOGS)) return false;
                min = Math.min(min, y);
                max = Math.max(max, y);
            }
        }
        return max - min <= 3;
    }

    /**
     * Makes room for a structure: inside the footprint (±halfA along {@code a}, ±halfB along
     * {@code b}) everything from the ground up to {@code height} is cleared (grass, flowers,
     * leaves, logs), the ground row becomes {@code top}, and dips below it are filled with a
     * foundation column so nothing floats on a slope.
     */
    private static void prepareSite(ServerWorld world, BlockPos ground, Direction a, int halfA, Direction b, int halfB,
                                    int height, BlockState top, BlockState foundation) {
        for (int i = -halfA; i <= halfA; i++) {
            for (int j = -halfB; j <= halfB; j++) {
                BlockPos col = ground.offset(a, i).offset(b, j);
                for (int y = 1; y <= height; y++) pos(world, col.up(y), Blocks.AIR.getDefaultState());
                pos(world, col, top);
                for (int y = 1; y <= 8; y++) {
                    BlockPos below = col.down(y);
                    BlockState st = world.getBlockState(below);
                    if (!st.isAir() && !st.isReplaceable() && !st.isLiquid()) break;
                    pos(world, below, foundation);
                }
            }
        }
    }

    private static BlockPos at(BlockPos base, Direction fwd, int f, Direction right, int r, int up) {
        return base.offset(fwd, f).offset(right, r).up(up);
    }

    private static BlockState stairs(BlockState stairs, Direction facing, boolean upsideDown) {
        return stairs.with(net.minecraft.block.StairsBlock.FACING, facing)
                .with(net.minecraft.block.StairsBlock.HALF, upsideDown
                        ? net.minecraft.block.enums.BlockHalf.TOP : net.minecraft.block.enums.BlockHalf.BOTTOM);
    }

    private static BlockState candles(Random random, boolean lit) {
        return Blocks.CANDLE.getDefaultState()
                .with(net.minecraft.block.CandleBlock.CANDLES, 1 + random.nextInt(3))
                .with(net.minecraft.block.CandleBlock.LIT, lit);
    }

    // ---------------------------------------------------------------- cemetery

    /**
     * A forgotten burial ground (11 x 9): a broken spruce fence with a gap for a gate, two rows
     * of headstones with their grave mounds, cobwebs, dead bushes and soul lanterns on the corner
     * posts — and one grave that has been DUG OPEN, with a chest lying at the bottom.
     */
    private static void buildCemetery(ServerWorld world, BlockPos ground, ServerPlayerEntity player) {
        Random random = player.getRandom();
        Direction f = Direction.Type.HORIZONTAL.random(random);
        Direction r = f.rotateYClockwise();
        prepareSite(world, ground, f, 4, r, 5, 4, Blocks.PODZOL.getDefaultState(), Blocks.DIRT.getDefaultState());
        // patchy ground
        for (int i = -4; i <= 4; i++) {
            for (int j = -5; j <= 5; j++) {
                int roll = random.nextInt(6);
                if (roll == 0) pos(world, at(ground, f, i, r, j, 0), Blocks.COARSE_DIRT.getDefaultState());
                else if (roll == 1) pos(world, at(ground, f, i, r, j, 0), Blocks.ROOTED_DIRT.getDefaultState());
            }
        }
        // the fence: rotten, with holes, a gap in the front for the gate
        for (int i = -4; i <= 4; i++) {
            for (int j = -5; j <= 5; j++) {
                boolean edge = Math.abs(i) == 4 || Math.abs(j) == 5;
                if (!edge) continue;
                if (i == 4 && Math.abs(j) <= 1) continue;          // gate gap (front)
                if (random.nextInt(5) == 0) continue;               // fallen sections
                pos(world, at(ground, f, i, r, j, 1), Blocks.SPRUCE_FENCE.getDefaultState());
            }
        }
        // corner posts with soul lanterns
        for (int si : new int[]{-4, 4}) {
            for (int sj : new int[]{-5, 5}) {
                pos(world, at(ground, f, si, r, sj, 1), Blocks.SPRUCE_FENCE.getDefaultState());
                pos(world, at(ground, f, si, r, sj, 2), Blocks.SPRUCE_FENCE.getDefaultState());
                if (random.nextInt(3) != 0) pos(world, at(ground, f, si, r, sj, 3), Blocks.SOUL_LANTERN.getDefaultState());
            }
        }
        // a path through the middle
        for (int i = -3; i <= 4; i++) pos(world, at(ground, f, i, r, 0, 0), Blocks.DIRT_PATH.getDefaultState());

        // two rows of graves on each side of the path; headstone at the back, mound in front
        int openRow = random.nextInt(2), openCol = random.nextInt(2);
        BlockState[] stones = {Blocks.MOSSY_STONE_BRICK_WALL.getDefaultState(), Blocks.COBBLESTONE_WALL.getDefaultState(),
                Blocks.STONE_BRICK_WALL.getDefaultState(), Blocks.MOSSY_COBBLESTONE_WALL.getDefaultState()};
        for (int row = 0; row < 2; row++) {
            int fi = row == 0 ? -2 : 1;
            for (int side : new int[]{-1, 1}) {
                for (int col = 0; col < 2; col++) {
                    int rj = side * (2 + col * 2);
                    BlockPos stone = at(ground, f, fi - 1, r, rj, 1);
                    pos(world, stone, stones[random.nextInt(stones.length)]);
                    if (random.nextInt(3) == 0) pos(world, stone.up(), candles(random, false));
                    else if (random.nextInt(4) == 0) pos(world, stone.up(), Blocks.STONE_BUTTON.getDefaultState()
                            .with(net.minecraft.block.ButtonBlock.FACE, net.minecraft.block.enums.BlockFace.FLOOR));
                    boolean open = row == openRow && col == openCol && side == 1;
                    BlockPos mound1 = at(ground, f, fi, r, rj, 0), mound2 = at(ground, f, fi + 1, r, rj, 0);
                    if (open) {
                        // dug open: a 1x2 pit, two deep, dirt thrown on both sides, the chest at the bottom
                        for (BlockPos m : new BlockPos[]{mound1, mound2}) {
                            pos(world, m, Blocks.AIR.getDefaultState());
                            pos(world, m.down(), Blocks.AIR.getDefaultState());
                            pos(world, m.down(2), Blocks.COARSE_DIRT.getDefaultState());
                        }
                        pos(world, at(ground, f, fi, r, rj + 1, 1), Blocks.DIRT.getDefaultState());
                        pos(world, at(ground, f, fi + 1, r, rj - 1, 1), Blocks.COARSE_DIRT.getDefaultState());
                        BlockPos chestPos = mound1.down();
                        if (placeChest(world, chestPos, f)) {
                            fillChest(world, chestPos, List.of(
                                    stack(Items.BONE, 2 + random.nextInt(4)),
                                    stack(Items.ROTTEN_FLESH, 1 + random.nextInt(2)),
                                    stack(Items.CANDLE, 1),
                                    random.nextInt(4) == 0 ? stack(Items.GOLD_NUGGET, 2 + random.nextInt(4)) : stack(Items.STRING, 1)
                            ), book(random.nextBoolean() ? "THE NAMES" : "DO NOT DIG",
                                    random.nextBoolean()
                                            ? "There are more graves here than people who ever lived\nnear the river.\n\nSomeone keeps digging a new one.\n\nEvery night the count is different.\n\nI stopped counting."
                                            : "This one was empty when we opened it.\n\nNot robbed. EMPTY.\n\nThe ground was dug from the inside.\n\nIf you are reading this,\ndo not stay until dark."));
                        }
                    } else {
                        pos(world, mound1, Blocks.COARSE_DIRT.getDefaultState());
                        pos(world, mound2, Blocks.COARSE_DIRT.getDefaultState());
                        if (random.nextInt(3) == 0) pos(world, mound1.up(), Blocks.DEAD_BUSH.getDefaultState());
                    }
                }
            }
        }
        // cobwebs on the fence and a couple of dead bushes
        for (int k = 0; k < 4; k++) {
            int i = random.nextInt(9) - 4, j = random.nextBoolean() ? -5 : 5;
            if (world.getBlockState(at(ground, f, i, r, j, 1)).isOf(Blocks.SPRUCE_FENCE))
                pos(world, at(ground, f, i, r, j, 2), Blocks.COBWEB.getDefaultState());
        }
        for (int k = 0; k < 3; k++) {
            BlockPos bush = at(ground, f, random.nextInt(7) - 3, r, random.nextInt(9) - 4, 1);
            if (world.getBlockState(bush).isAir() && !world.getBlockState(bush.down()).isOf(Blocks.DIRT_PATH))
                pos(world, bush, Blocks.DEAD_BUSH.getDefaultState());
        }
        finishConnections(world);
        play(world, ground, random);
    }

    // ---------------------------------------------------------------- shrine

    /**
     * A roadside kapliczka: a stone brick column on a cobbled step, a niche with a skull where
     * a saint used to stand, a little pyramid roof of dark oak, candles burning at the foot,
     * wither roses in the grass and an offering chest in front.
     */
    private static void buildShrine(ServerWorld world, BlockPos ground, ServerPlayerEntity player) {
        Random random = player.getRandom();
        Direction f = Direction.Type.HORIZONTAL.random(random);   // the side it faces (the road)
        Direction r = f.rotateYClockwise();
        prepareSite(world, ground, f, 2, r, 2, 7, Blocks.COARSE_DIRT.getDefaultState(), Blocks.COBBLESTONE.getDefaultState());
        // a 3x3 step
        for (int i = -1; i <= 1; i++)
            for (int j = -1; j <= 1; j++)
                pos(world, at(ground, f, i, r, j, 1), (i == 0 && j == 0) ? Blocks.STONE_BRICKS.getDefaultState()
                        : Blocks.MOSSY_STONE_BRICK_SLAB.getDefaultState());
        // the column
        pos(world, at(ground, f, 0, r, 0, 2), Blocks.STONE_BRICKS.getDefaultState());
        pos(world, at(ground, f, 0, r, 0, 3), Blocks.CHISELED_STONE_BRICKS.getDefaultState());
        // the niche: back + side walls around the skull
        pos(world, at(ground, f, -1, r, 0, 4), Blocks.STONE_BRICKS.getDefaultState());
        pos(world, at(ground, f, 0, r, -1, 4), Blocks.STONE_BRICK_WALL.getDefaultState());
        pos(world, at(ground, f, 0, r, 1, 4), Blocks.STONE_BRICK_WALL.getDefaultState());
        pos(world, at(ground, f, 0, r, 0, 3).up(), Blocks.SKELETON_SKULL.getDefaultState()
                .with(net.minecraft.block.SkullBlock.ROTATION, net.minecraft.util.math.RotationPropertyHelper.fromDirection(f)));
        // pyramid roof
        pos(world, at(ground, f, 0, r, 0, 5), Blocks.DARK_OAK_PLANKS.getDefaultState());
        pos(world, at(ground, f, 1, r, 0, 5), stairs(Blocks.DARK_OAK_STAIRS.getDefaultState(), f.getOpposite(), false));
        pos(world, at(ground, f, -1, r, 0, 5), stairs(Blocks.DARK_OAK_STAIRS.getDefaultState(), f, false));
        pos(world, at(ground, f, 0, r, 1, 5), stairs(Blocks.DARK_OAK_STAIRS.getDefaultState(), r.getOpposite(), false));
        pos(world, at(ground, f, 0, r, -1, 5), stairs(Blocks.DARK_OAK_STAIRS.getDefaultState(), r, false));
        pos(world, at(ground, f, 0, r, 0, 6), Blocks.DARK_OAK_SLAB.getDefaultState());
        pos(world, at(ground, f, 0, r, 0, 7), Blocks.DARK_OAK_FENCE.getDefaultState());
        // candles on the step corners (lit — someone still comes here)
        for (int i : new int[]{-1, 1})
            for (int j : new int[]{-1, 1})
                if (random.nextInt(4) != 0) pos(world, at(ground, f, i, r, j, 1).up(), candles(random, true));
        // wither roses in front, the offering chest on the road side
        for (int j = -2; j <= 2; j++)
            if (random.nextInt(3) == 0 && j != 0) pos(world, at(ground, f, 2, r, j, 1), Blocks.WITHER_ROSE.getDefaultState());
        BlockPos chestPos = at(ground, f, 2, r, 0, 1);
        if (placeChest(world, chestPos, f)) {
            fillChest(world, chestPos, List.of(
                    stack(Items.BREAD, 1 + random.nextInt(2)),
                    stack(Items.CANDLE, 1 + random.nextInt(2)),
                    random.nextInt(3) == 0 ? stack(Items.EMERALD, 1) : stack(Items.HONEY_BOTTLE, 1)
            ), book("IT ACCEPTS",
                    "Leave something in the box.\n\nFood. Light. Anything it wants.\n\nThe village left bread here\nfor sixty years.\n\nLast winter the box was full.\n\nWe were not."));
        }
        finishConnections(world);
        play(world, ground, random);
    }

    // ---------------------------------------------------------------- cottage

    /**
     * An abandoned spruce cottage (7 x 5, gable roof): cobbled foundation, log corner posts,
     * plank walls with glass-pane windows (some smashed), an EMPTY interior with a rotten bed,
     * a table, a fallen chair, cobwebs, a hanging soul lantern, a sign scratched with "it was
     * tall" and one chest. The front door is IRON and jammed: a lever beside it and a dead
     * redstone trail that peters out in the grass, connected to nothing.
     */
    private static void buildCottage(ServerWorld world, BlockPos ground, ServerPlayerEntity player) {
        Random random = player.getRandom();
        Direction f = Direction.Type.HORIZONTAL.random(random);   // the front faces f
        Direction r = f.rotateYClockwise();
        Direction l = r.getOpposite();
        prepareSite(world, ground, f, 4, r, 5, 9, Blocks.COARSE_DIRT.getDefaultState(), Blocks.COBBLESTONE.getDefaultState());

        BlockState log = Blocks.SPRUCE_LOG.getDefaultState();
        BlockState plank = Blocks.SPRUCE_PLANKS.getDefaultState();
        // floor (fd -2..2, rj -3..3) on a cobbled ring
        for (int i = -2; i <= 2; i++) {
            for (int j = -3; j <= 3; j++) {
                boolean edge = Math.abs(i) == 2 || Math.abs(j) == 3;
                pos(world, at(ground, f, i, r, j, 0), edge
                        ? (random.nextInt(3) == 0 ? Blocks.MOSSY_COBBLESTONE.getDefaultState() : Blocks.COBBLESTONE.getDefaultState())
                        : (random.nextInt(7) == 0 ? Blocks.COARSE_DIRT.getDefaultState() : plank));   // rotten boards
            }
        }
        // walls y1..3
        for (int i = -2; i <= 2; i++) {
            for (int j = -3; j <= 3; j++) {
                boolean edge = Math.abs(i) == 2 || Math.abs(j) == 3;
                boolean corner = Math.abs(i) == 2 && Math.abs(j) == 3;
                for (int y = 1; y <= 3; y++) {
                    BlockPos p = at(ground, f, i, r, j, y);
                    if (!edge) pos(world, p, Blocks.AIR.getDefaultState());          // hollow inside
                    else pos(world, p, corner ? log : plank);
                }
            }
        }
        // windows: front/back at rj ±2, sides at fd 0; some smashed (left empty)
        int[][] windows = {{2, -2}, {2, 2}, {-2, -2}, {-2, 2}, {0, -3}, {0, 3}};
        for (int[] w : windows) {
            BlockPos p = at(ground, f, w[0], r, w[1], 2);
            pos(world, p, random.nextInt(4) == 0 ? Blocks.AIR.getDefaultState() : Blocks.GLASS_PANE.getDefaultState());
        }
        // gable roof: slopes towards ±r, ridge along f; overhang one block front and back
        BlockState st = Blocks.SPRUCE_STAIRS.getDefaultState();
        for (int i = -3; i <= 3; i++) {
            for (int k = 0; k <= 3; k++) {
                int y = 4 + k, j = 4 - k;
                if (k < 3) {
                    pos(world, at(ground, f, i, r, j, y), stairs(st, l, false));     // right slope faces inward
                    pos(world, at(ground, f, i, r, -j, y), stairs(st, r, false));    // left slope faces inward
                } else {
                    pos(world, at(ground, f, i, r, 0, y), Blocks.SPRUCE_SLAB.getDefaultState());   // ridge
                }
            }
        }
        // gable ends (front and back walls up into the roof)
        for (int i : new int[]{-2, 2}) {
            for (int y = 4; y <= 6; y++) {
                int half = 6 - y;           // y4: |j|<=2, y5: |j|<=1, y6: j=0
                for (int j = -half; j <= half; j++) pos(world, at(ground, f, i, r, j, y), plank);
            }
        }
        // ceiling beams inside (air under the roof stays open, like an attic)
        for (int j = -2; j <= 2; j++) pos(world, at(ground, f, 0, r, j, 4), Blocks.STRIPPED_SPRUCE_LOG.getDefaultState()
                .with(net.minecraft.block.PillarBlock.AXIS, r.getAxis()));

        // the jammed iron door in the middle of the front wall
        BlockState door = Blocks.IRON_DOOR.getDefaultState()
                .with(DoorBlock.FACING, f)
                .with(DoorBlock.OPEN, false);
        BlockPos doorPos = at(ground, f, 2, r, 0, 1);
        pos(world, doorPos, door.with(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        pos(world, doorPos.up(), door.with(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        // a lever on the OUTSIDE of the wall, beside the door, and a dead dust trail into the grass
        pos(world, at(ground, f, 3, r, 1, 2), Blocks.LEVER.getDefaultState()
                .with(LeverBlock.FACE, net.minecraft.block.enums.BlockFace.WALL)
                .with(LeverBlock.FACING, f));
        BlockPos dust = at(ground, f, 3, r, 1, 1);
        for (int k = 0; k < 4; k++) {
            if (world.getBlockState(dust.down()).isAir()) break;
            pos(world, dust, Blocks.REDSTONE_WIRE.getDefaultState());
            dust = dust.offset(f);
        }

        // inside: rotten bed, table, fallen chair, cobwebs, hanging soul lantern, the sign, the chest
        BlockPos bedFoot = at(ground, f, -1, r, -2, 1);
        BlockState bed = Blocks.BROWN_BED.getDefaultState().with(net.minecraft.block.BedBlock.FACING, l);
        pos(world, bedFoot, bed.with(net.minecraft.block.BedBlock.PART, net.minecraft.block.enums.BedPart.FOOT));
        pos(world, bedFoot.offset(l), bed.with(net.minecraft.block.BedBlock.PART, net.minecraft.block.enums.BedPart.HEAD));
        pos(world, at(ground, f, -1, r, 2, 1), Blocks.CRAFTING_TABLE.getDefaultState());
        pos(world, at(ground, f, 0, r, 2, 1), stairs(st, r, false));                      // the chair, turned away
        pos(world, at(ground, f, 1, r, -2, 1), stairs(st, f.getOpposite(), true));         // knocked over
        pos(world, at(ground, f, -1, r, 2, 3), Blocks.COBWEB.getDefaultState());
        pos(world, at(ground, f, 1, r, -2, 3), Blocks.COBWEB.getDefaultState());
        pos(world, at(ground, f, -1, r, -1, 3), Blocks.COBWEB.getDefaultState());
        pos(world, at(ground, f, 0, r, 0, 3), Blocks.SOUL_LANTERN.getDefaultState().with(net.minecraft.block.LanternBlock.HANGING, true));
        BlockPos signPos = at(ground, f, -1, r, 0, 2);
        pos(world, signPos, Blocks.SPRUCE_WALL_SIGN.getDefaultState().with(net.minecraft.block.WallSignBlock.FACING, f));
        if (world.getBlockEntity(signPos) instanceof net.minecraft.block.entity.SignBlockEntity sign) {
            sign.setText(new net.minecraft.block.entity.SignText()
                    .withMessage(0, Text.literal("it was"))
                    .withMessage(1, Text.literal("tall"))
                    .withMessage(3, Text.literal("don't open")), true);
            sign.markDirty();
        }
        BlockPos chestPos = at(ground, f, 1, r, 2, 1);
        if (placeChest(world, chestPos, l)) {
            fillChest(world, chestPos, List.of(
                    stack(Items.BREAD, 1),
                    stack(Items.STICK, 1 + random.nextInt(3)),
                    stack(Items.TORCH, 1 + random.nextInt(2)),
                    random.nextInt(4) == 0 ? stack(Items.IRON_INGOT, 1 + random.nextInt(2)) : stack(Items.STRING, 2)
            ), book("THE DOOR",
                    "The door will not open.\n\nYou can flip the lever forever.\n\nThe redstone does nothing.\n\nIt never did.\n\nWhoever built this did not want\nsomeone to come IN.\n\nThey wanted something to stay\ninside."));
        }
        finishConnections(world);
        play(world, ground, random);
    }

    // ---------------------------------------------------------------- black copy

    /**
     * CZARNA KOPIA BAZY (§17.1): a block-by-block copy of what the player BUILT (the BaseMap's
     * TYPE_PLACED history plus its door / light / chest markers), translated far behind the
     * player and turned black — stone becomes blackstone and deepslate, glass turns tinted,
     * torches burn with soul fire, the bed is black, and every door is just a hole. Size is
     * clamped ({@value #COPY_MAX_W} x {@value #COPY_MAX_H} x {@value #COPY_MAX_W}); the real base
     * is only READ, never touched. With too little history it falls back to a hollow shell.
     */
    private static void buildBlackCopy(ServerWorld world, BlockPos ground, ServerPlayerEntity player) {
        BaseMap map = BaseTracker.mapOf(player.getUuid());
        if (map == null || map.count() < 8) return;   // §17.1: it feeds on saved base data
        BlockPos anchor = map.anchorPos();
        Random random = player.getRandom();

        // what to copy: the blocks the player placed + the base's doors / lights / chests
        java.util.Set<BlockPos> sources = new java.util.HashSet<>();
        for (int marker : map.encodedMarkers()) {
            int type = map.type(marker);
            if (type == BaseMap.TYPE_PLACED || type == BaseMap.TYPE_DOOR || type == BaseMap.TYPE_LIGHT || type == BaseMap.TYPE_CHEST) {
                BlockPos p = map.pos(marker);
                if (Math.abs(p.getX() - anchor.getX()) <= COPY_MAX_W / 2 && Math.abs(p.getZ() - anchor.getZ()) <= COPY_MAX_W / 2
                        && p.getY() - anchor.getY() >= -4 && p.getY() - anchor.getY() < COPY_MAX_H - 4) {
                    sources.add(p);
                }
            }
        }
        if (sources.size() < 12) {
            buildBlackShell(world, ground, map, random);
            play(player, ground);
            return;
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos p : sources) {
            minX = Math.min(minX, p.getX()); maxX = Math.max(maxX, p.getX());
            minY = Math.min(minY, p.getY()); maxY = Math.max(maxY, p.getY());
            minZ = Math.min(minZ, p.getZ()); maxZ = Math.max(maxZ, p.getZ());
        }
        int w = maxX - minX, d = maxZ - minZ, h = maxY - minY;
        // the copy's lowest row lands on the ground, centred on the chosen spot
        BlockPos origin = ground.add(-w / 2, 0, -d / 2);
        prepareSite(world, origin.add(w / 2, 0, d / 2), Direction.EAST, w / 2 + 1, Direction.SOUTH, d / 2 + 1,
                h + 2, Blocks.DEEPSLATE_TILES.getDefaultState(), Blocks.COBBLED_DEEPSLATE.getDefaultState());

        BlockPos chestCopy = null;
        int placed = 0;
        for (BlockPos src : sources) {
            if (!world.isChunkLoaded(src.getX() >> 4, src.getZ() >> 4)) continue;
            BlockState s = world.getBlockState(src);
            BlockPos dst = origin.add(src.getX() - minX, src.getY() - minY, src.getZ() - minZ);
            if (s.getBlock() instanceof net.minecraft.block.BedBlock) {
                // beds are two blocks: copy both halves in black
                BlockState black = Blocks.BLACK_BED.getStateWithProperties(s);
                pos(world, dst, black);
                Direction toOther = s.get(net.minecraft.block.BedBlock.PART) == net.minecraft.block.enums.BedPart.FOOT
                        ? s.get(net.minecraft.block.BedBlock.FACING) : s.get(net.minecraft.block.BedBlock.FACING).getOpposite();
                pos(world, dst.offset(toOther), black.with(net.minecraft.block.BedBlock.PART,
                        s.get(net.minecraft.block.BedBlock.PART) == net.minecraft.block.enums.BedPart.FOOT
                                ? net.minecraft.block.enums.BedPart.HEAD : net.minecraft.block.enums.BedPart.FOOT));
                placed += 2;
                continue;
            }
            BlockState black = blacken(world, s, src, random);
            if (black == null) continue;
            pos(world, dst, black);
            placed++;
            if (chestCopy == null && black.isOf(Blocks.CHEST)) chestCopy = dst;
            if (placed > 3000) break;   // hard cap: one build must stay cheap
        }
        if (chestCopy != null) {
            fillChest(world, chestCopy, List.of(), book("IT COPIED YOU",
                    "It walked through your home.\n\nIt counted every block.\n\nNow it has one too.\n\nIt is waiting inside\nfor you to come home."));
        }
        finishConnections(world);
        play(player, ground);
    }

    private static final int COPY_MAX_W = 32, COPY_MAX_H = 20;

    /** Black palette for one copied block; null = leave it out (plants, carpets, loose stuff). */
    private static BlockState blacken(ServerWorld world, BlockState s, BlockPos src, Random random) {
        var b = s.getBlock();
        if (s.isAir() || s.isLiquid()) return null;
        if (b instanceof DoorBlock) return Blocks.AIR.getDefaultState();                  // no honest entrance
        if (b == Blocks.GLASS || b instanceof net.minecraft.block.StainedGlassBlock || b == Blocks.TINTED_GLASS)
            return Blocks.TINTED_GLASS.getDefaultState();
        if (b instanceof net.minecraft.block.PaneBlock && b != Blocks.IRON_BARS)
            return Blocks.BLACK_STAINED_GLASS_PANE.getStateWithProperties(s);
        if (b == Blocks.WALL_TORCH) return Blocks.SOUL_WALL_TORCH.getStateWithProperties(s);
        if (b == Blocks.TORCH) return Blocks.SOUL_TORCH.getDefaultState();
        if (b instanceof net.minecraft.block.LanternBlock) return Blocks.SOUL_LANTERN.getStateWithProperties(s);
        if (b instanceof ChestBlock) return Blocks.CHEST.getStateWithProperties(s).with(ChestBlock.CHEST_TYPE, net.minecraft.block.enums.ChestType.SINGLE);
        if (b instanceof net.minecraft.block.StairsBlock) return Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS.getStateWithProperties(s);
        if (b instanceof net.minecraft.block.SlabBlock) return Blocks.POLISHED_BLACKSTONE_BRICK_SLAB.getStateWithProperties(s);
        if (b instanceof net.minecraft.block.WallBlock) return Blocks.BLACKSTONE_WALL.getStateWithProperties(s);
        if (b instanceof net.minecraft.block.FenceBlock) return Blocks.NETHER_BRICK_FENCE.getStateWithProperties(s);
        if (b instanceof net.minecraft.block.TrapdoorBlock) return Blocks.IRON_TRAPDOOR.getStateWithProperties(s);
        if (b instanceof net.minecraft.block.PillarBlock) return Blocks.POLISHED_BASALT.getStateWithProperties(s);
        if (s.isOpaqueFullCube(world, src) || s.isSolid()) {
            // a stable pick per position so the copy looks built, not random noise
            int hsh = (src.getX() * 31 + src.getY() * 17 + src.getZ() * 13) & 7;
            return switch (hsh) {
                case 0, 1, 2 -> Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState();
                case 3, 4 -> Blocks.BLACKSTONE.getDefaultState();
                case 5 -> Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.getDefaultState();
                default -> Blocks.DEEPSLATE_TILES.getDefaultState();
            };
        }
        return null;
    }

    /** Fallback when too little of the base is known: a hollow black shell of the base's size. */
    private static void buildBlackShell(ServerWorld world, BlockPos ground, BaseMap map, Random random) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int marker : map.encodedMarkers()) {
            BlockPos p = map.pos(marker);
            minX = Math.min(minX, p.getX()); maxX = Math.max(maxX, p.getX());
            minY = Math.min(minY, p.getY()); maxY = Math.max(maxY, p.getY());
            minZ = Math.min(minZ, p.getZ()); maxZ = Math.max(maxZ, p.getZ());
        }
        int w = MathHelper.clamp(maxX - minX, 4, 12), d = MathHelper.clamp(maxZ - minZ, 4, 12);
        int h = MathHelper.clamp(maxY - minY, 3, 6);
        BlockPos origin = ground.add(-w / 2, 0, -d / 2);
        prepareSite(world, ground, Direction.EAST, w / 2 + 1, Direction.SOUTH, d / 2 + 1, h + 2,
                Blocks.DEEPSLATE_TILES.getDefaultState(), Blocks.COBBLED_DEEPSLATE.getDefaultState());
        for (int x = 0; x <= w; x++)
            for (int z = 0; z <= d; z++)
                for (int y = 1; y <= h; y++) {
                    boolean shell = x == 0 || x == w || z == 0 || z == d || y == h;
                    BlockPos p = origin.add(x, y, z);
                    pos(world, p, shell ? blacken(world, Blocks.STONE.getDefaultState(), p, random) : Blocks.AIR.getDefaultState());
                }
        int cx = w / 2, cz = d / 2;
        for (int y = 1; y <= 2; y++) {             // a doorway on every side — a copy has no honest entrance
            pos(world, origin.add(0, y, cz), Blocks.AIR.getDefaultState());
            pos(world, origin.add(w, y, cz), Blocks.AIR.getDefaultState());
            pos(world, origin.add(cx, y, 0), Blocks.AIR.getDefaultState());
            pos(world, origin.add(cx, y, d), Blocks.AIR.getDefaultState());
        }
        pos(world, origin.add(cx, 1, cz), Blocks.SOUL_CAMPFIRE.getDefaultState());
    }

    private static Direction facingTowards(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dz = to.getZ() - from.getZ();
        return Math.abs(dx) >= Math.abs(dz)
                ? (dx >= 0 ? Direction.EAST : Direction.WEST)
                : (dz >= 0 ? Direction.SOUTH : Direction.NORTH);
    }

    // ---------------------------------------------------------------- helpers

    /** Places at absolute coordinates, sending to clients without neighbour cascades. */
    private static void pos(ServerWorld world, BlockPos at, BlockState state) {
        world.setBlockState(at, state, 2);
        var b = state.getBlock();
        if (b instanceof net.minecraft.block.PaneBlock || b instanceof net.minecraft.block.FenceBlock
                || b instanceof net.minecraft.block.WallBlock) {
            CONNECT.add(at.toImmutable());
        }
    }

    /** Fences, walls and panes connect to their neighbours once everything is placed. */
    private static void finishConnections(ServerWorld world) {
        for (BlockPos p : CONNECT) {
            BlockState st = world.getBlockState(p);
            BlockState fixed = net.minecraft.block.Block.postProcessState(st, world, p);
            if (fixed != st) world.setBlockState(p, fixed, 2);
        }
        CONNECT.clear();
    }

    private static BlockState gravelOrDirt(Random random) {
        return random.nextBoolean()
                ? Blocks.GRAVEL.getDefaultState()
                : Blocks.COARSE_DIRT.getDefaultState();
    }

    private static boolean placeChest(ServerWorld world, BlockPos at, Direction facing) {
        if (world.getBlockState(at).isAir() && !world.getBlockState(at.down()).isAir()) {
            world.setBlockState(at, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, facing), 2);
            return true;
        }
        return false;
    }

    private static void fillChest(ServerWorld world, BlockPos at, List<ItemStack> loot, ItemStack book) {
        if (world.getBlockEntity(at) instanceof LootableContainerBlockEntity chest) {
            int slot = 0;
            for (ItemStack stack : loot) {
                if (slot >= chest.size()) break;
                chest.setStack(slot++, stack);
            }
            if (slot < chest.size()) chest.setStack(slot, book);
            chest.markDirty();
        }
    }

    /** A one-hit distant rumble only the haunted player hears (the world changed behind you). */
    private static void play(ServerWorld world, BlockPos at, Random random) {
        world.playSound(null, at, SoundEvents.BLOCK_STONE_PLACE, SoundCategory.AMBIENT,
                0.4f, 0.5f + random.nextFloat() * 0.3f);
    }

    /** A private cue to the owner alone: the night got heavier. */
    private static void play(ServerPlayerEntity player, BlockPos at) {
        MineEvents.playTo(player, SoundEvents.AMBIENT_CAVE.value(),
                net.minecraft.util.math.Vec3d.ofCenter(at), 0.8f, 0.6f);
    }

    private static ItemStack stack(net.minecraft.item.Item item, int count) {
        return new ItemStack(item, count);
    }

    /** Flavour written book (untracked — the tracked §9 lore stays in the player's own chests). */
    private static ItemStack book(String title, String body) {
        ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);
        stack.set(DataComponentTypes.WRITTEN_BOOK_CONTENT, new WrittenBookContentComponent(
                RawFilteredPair.of(title), "Unknown", 0,
                List.of(RawFilteredPair.of(Text.literal(body))), true));
        return stack;
    }

    private static double clamp01(double chance) {
        return Math.max(0.0, Math.min(1.0, chance));
    }

    private static long dayIndex(World world) {
        return world.getTimeOfDay() / 24000L;
    }

    /** Small per-player reminder of the one-build-per-night rule (§17). */
    private static final class Ledger {
        long day = -1;
        int count;
    }
}
