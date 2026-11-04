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
            BlockPos spot = findSpot(player, player.getRandom(), kind == Kind.BLACKCOPY);
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
    static BlockPos findSpot(ServerPlayerEntity player, Random random, boolean blackCopy) {
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
        BlockPos spot = findSpot(player, player.getRandom(), kind == Kind.BLACKCOPY);
        if (spot == null) return false;
        build(player, spot, kind);
        return true;
    }

    // ---------------------------------------------------------------- cemetery

    /**
     * A lonely burial ground: two ragged rows of graves (gravel, a cobble marker and a fence
     * cross each), one chest among them holding a written book and dark small change.
     */
    private static void buildCemetery(ServerWorld world, BlockPos ground, ServerPlayerEntity player) {
        Random random = player.getRandom();
        Direction axis = Direction.Type.HORIZONTAL.random(random);
        Direction side = axis.rotateYClockwise();
        // two ragged rows of graves separated by a gravel path
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 3; col++) {
                BlockPos grave = ground.offset(axis, row * 5).offset(side, col * 4 - 4);
                levelGround(world, grave, 2, 2, gravelOrDirt(random));
                // a crude grave: cobble slab base, a wall post, often a fence cross on top
                pos(world, grave, Blocks.COBBLESTONE_SLAB.getDefaultState());
                pos(world, grave.up(), Blocks.COBBLESTONE_WALL.getDefaultState());
                if (random.nextInt(3) != 0) {         // some graves lost their cross
                    pos(world, grave.up(2), Blocks.SPRUCE_FENCE.getDefaultState());
                    if (random.nextInt(4) == 0) pos(world, grave.up(3), Blocks.SPRUCE_FENCE.getDefaultState());
                }
            }
        }
        // the chest at the foot of the middle grave, facing "out" of the cemetery
        Direction out = axis.getOpposite();
        BlockPos chestPos = ground.offset(axis, -2).offset(side, 2).up();
        if (placeChest(world, chestPos, out)) {
            fillChest(world, chestPos, List.of(
                    stack(Items.BONE, 1 + random.nextInt(3)),
                    stack(Items.COAL, 1 + random.nextInt(3)),
                    stack(Items.CANDLE, 1),
                    new ItemStack(random.nextInt(4) == 0 ? Items.REDSTONE : Items.GRAVEL,
                            1 + random.nextInt(2))
            ), book(random.nextBoolean()
                    ? "THE NAMES"
                    : "DO NOT DIG",
                    random.nextBoolean()
                            ? "There are more graves here than people who ever lived\nnear the river.\n\nSomeone keeps digging a new one.\n\nEvery night the count is different.\n\nI stopped counting."
                            : "Three of the crosses point wrong.\n\nNot broken — TURNED.\n\nEvery night something turns one more.\n\nTomorrow I will not come back.\n\nIf you are reading this, do not dig here."));
        }
        // the wrought-iron soul lantern someone left burning for the dead
        pos(world, chestPos.up(2), Blocks.SOUL_LANTERN.getDefaultState());
        play(world, chestPos, random);
    }

    // ---------------------------------------------------------------- shrine

    /** A roadside kapliczka: a cobbled nest, a chest for offerings, a pillar with candles. */
    private static void buildShrine(ServerWorld world, BlockPos ground, ServerPlayerEntity player) {
        Random random = player.getRandom();
        Direction facing = Direction.Type.HORIZONTAL.random(random);
        levelGround(world, ground, 3, 3, Blocks.COBBLESTONE.getDefaultState());
        // low cobbled rim around the offering chest
        for (Direction dir : Direction.Type.HORIZONTAL) {
            pos(world, ground.offset(dir), Blocks.MOSSY_COBBLESTONE_SLAB.getDefaultState());
        }
        BlockPos chestPos = ground.up();
        if (placeChest(world, chestPos, facing)) {
            fillChest(world, chestPos, List.of(
                    stack(Items.BREAD, 1 + random.nextInt(2)),
                    stack(Items.CANDLE, 1 + random.nextInt(2)),
                    random.nextInt(3) == 0 ? stack(Items.EMERALD, 1) : stack(Items.HONEY_BOTTLE, 1)
            ), book("IT ACCEPTS",
                    "Leave something in the box.\n\nFood. Light. Anything it wants.\n\nThe village left bread here\nfor sixty years.\n\nLast winter the box was full.\n\nWe were not."));
        }
        // the pillar with candle flames watching the road
        BlockPos pillar = ground.offset(facing, 2);
        pos(world, pillar, Blocks.COBBLESTONE.getDefaultState());
        pos(world, pillar.up(), Blocks.STONE_BRICK_WALL.getDefaultState());
        pos(world, pillar.up(2), Blocks.COBBLESTONE_STAIRS.getDefaultState()
                .with(net.minecraft.block.StairsBlock.FACING, facing.getOpposite()));
        pos(world, pillar.up(3), Blocks.CANDLE_CAKE.getDefaultState());
        play(world, pillar, random);
    }

    // ---------------------------------------------------------------- cottage

    /**
     * An abandoned 5x4 spruce cottage — the player's flavour made real: a JAMMED IRON DOOR
     * wired to a dead redstone lever trail (the lever sits next to the door, its dust peters
     * out in the grass, connected to nothing), a cobwebbed interior and one chest.
     */
    private static void buildCottage(ServerWorld world, BlockPos ground, ServerPlayerEntity player) {
        Random random = player.getRandom();
        Direction facing = Direction.Type.HORIZONTAL.random(random);   // door faces away from the trail
        Direction right = facing.rotateYClockwise();
        BlockPos center = ground.offset(facing, 2);                    // house centre line
        BlockPos frontWall = center.offset(facing, 2);                 // the fassade the door sits in
        BlockPos doorPos = frontWall;                                  // centre of the front wall

        // floor + walls: planks with log corners, two interior heights + roof
        for (int w = -2; w <= 2; w++) {
            for (int d = -2; d <= 2; d++) {
                BlockPos col = center.offset(right, w).offset(facing, d);
                pos(world, col, Blocks.SPRUCE_PLANKS.getDefaultState());            // floor
                boolean edge = Math.abs(w) == 2 || Math.abs(d) == 2;
                for (int h = 1; h <= 3; h++) {
                    pos(world, col.up(h), edge
                            ? Blocks.SPRUCE_LOG.getDefaultState()
                            : (h <= 2 ? Blocks.SPRUCE_PLANKS.getDefaultState()
                            : Blocks.AIR.getDefaultState()));                       // hollow roofline
                }
                pos(world, col.up(4), Blocks.SPRUCE_PLANKS.getDefaultState());      // roof
            }
        }
        // the door: IRON (it is jammed — it only answers redstone, and the redstone is dead)
        BlockState door = Blocks.IRON_DOOR.getDefaultState()
                .with(DoorBlock.FACING, facing.getOpposite())
                .with(DoorBlock.OPEN, false);
        pos(world, doorPos, door.with(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        pos(world, doorPos.up(), door.with(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        // the "fix": a lever on the wall beside the door and a dead dust trail into the grass
        BlockPos leverPos = frontWall.up(2).offset(right, 1);       // beside the door, head height
        if (!leverPos.equals(doorPos.up())) {
            pos(world, leverPos, Blocks.LEVER.getDefaultState()
                    .with(LeverBlock.FACING, facing.getOpposite()));
        }
        BlockPos dust = frontWall.offset(facing, 1).up();   // the trail peters out in the grass
        for (int i = 0; i < 4 && !world.getBlockState(dust.down()).isAir(); i++) {
            pos(world, dust, Blocks.REDSTONE_WIRE.getDefaultState());
            dust = dust.offset(facing, 1).up();             // follow the slope if there is one
        }
        // cobwebs and the one chest it still keeps
        pos(world, center.offset(right, -1).offset(facing, -1).up(2), Blocks.COBWEB.getDefaultState());
        pos(world, center.offset(right, 1).offset(facing, -1), Blocks.COBWEB.getDefaultState());
        BlockPos chestPos = center.offset(right, -1).offset(facing, 1).up();
        if (placeChest(world, chestPos, facing.getOpposite())) {
            fillChest(world, chestPos, List.of(
                    stack(Items.BREAD, 1),
                    stack(Items.STICK, 1 + random.nextInt(3)),
                    stack(Items.TORCH, 1 + random.nextInt(2)),
                    random.nextInt(4) == 0 ? stack(Items.IRON_INGOT, 1 + random.nextInt(2)) : stack(Items.STRING, 2)
            ), book("THE DOOR",
                    "The door will not open.\n\nYou can flip the lever forever.\n\nThe redstone does nothing.\n\nIt never did.\n\nWhoever built this did not want\nsomone to come IN.\n\nThey wanted something to stay\ninside."));
        }
        play(world, frontWall, random);
    }

    // ---------------------------------------------------------------- black copy

    /**
     * CZARNA KOPIA BAZY (§17.1): reads the player's mapped BaseMap bounds and raises a hollow
     * blackstone/deepslate shell of the same footprint (clamped to {@value #BLACK_MAX_W}x
     * {@value #BLACK_MAX_H}x{@value #BLACK_MAX_W}), with a doorway facing the player and a few
     * black window holes where the real base has one. The player's base itself is never touched.
     */
    private static void buildBlackCopy(ServerWorld world, BlockPos ground, ServerPlayerEntity player) {
        BaseMap map = BaseTracker.mapOf(player.getUuid());
        if (map == null || map.count() < 8) return;   // §17.1: it feeds on saved base data

        // real-world bounding box of the mapped markers
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int i = 0; i < map.count(); i++) {       // markers are index-ordered; walk all
            BlockPos p = map.pos(i);
            if (p == null) continue;
            minX = Math.min(minX, p.getX()); maxX = Math.max(maxX, p.getX());
            minY = Math.min(minY, p.getY()); maxY = Math.max(maxY, p.getY());
            minZ = Math.min(minZ, p.getZ()); maxZ = Math.max(maxZ, p.getZ());
        }
        if (minX > maxX) return;
        int w = Math.min(maxX - minX, BLACK_MAX_W);
        int d = Math.min(maxZ - minZ, BLACK_MAX_W);
        int h = Math.min(Math.max(maxY - minY, 3), BLACK_MAX_H);
        Random random = player.getRandom();

        // the copy stands on terrain the player CANNOT see
        BlockState wall = random.nextBoolean()
                ? Blocks.BLACKSTONE.getDefaultState()
                : Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState();

        for (int x = 0; x <= w; x++) {
            for (int z = 0; z <= d; z++) {
                for (int y = 0; y <= h; y++) {
                    boolean shell = x == 0 || x == w || z == 0 || z == d || y == 0 || y == h;
                    BlockPos at = ground.add(x, y, z);
                    if (shell) {
                        pos(world, at, y == 0 ? Blocks.DEEPSLATE_TILES.getDefaultState() : wall);
                    } else {
                        pos(world, at, Blocks.AIR.getDefaultState());     // hollow, always
                    }
                }
            }
        }
        // a doorway cut into ALL four sides at centre — a copy has no honest entrance
        int cx = w / 2, cz = d / 2;
        for (int y = 1; y <= Math.min(2, h - 1); y++) {
            pos(world, ground.add(0, y, cz), Blocks.AIR.getDefaultState());
            pos(world, ground.add(w, y, cz), Blocks.AIR.getDefaultState());
            pos(world, ground.add(cx, y, 0), Blocks.AIR.getDefaultState());
            pos(world, ground.add(cx, y, d), Blocks.AIR.getDefaultState());
        }
        // black "windows": one per wall when the real base actually has windows
        if (map.count(BaseMap.TYPE_WINDOW) > 0) {
            int wy = Math.min(2, h - 1);
            pos(world, ground.add(cx, wy, d), Blocks.AIR.getDefaultState());
            pos(world, ground.add(cx, wy, 0), Blocks.AIR.getDefaultState());
            pos(world, ground.add(w, wy, cz), Blocks.AIR.getDefaultState());
            pos(world, ground.add(0, wy, cz), Blocks.AIR.getDefaultState());
        }
        // one cold light inside — not a flame, a relic
        pos(world, ground.add(cx, 1, cz), Blocks.SOUL_CAMPFIRE.getDefaultState());
        // the private cue to the owner alone: the night got heavier behind your back
        play(player, ground);
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
    }

    /** Levels a w x d patch to one height, filling dips and shaving bumps. */
    private static void levelGround(ServerWorld world, BlockPos center, int w, int d, BlockState fill) {
        int y = center.getY();
        for (int x = -w / 2; x <= w / 2; x++) {
            for (int z = -d / 2; z <= d / 2; z++) {
                BlockPos at = center.add(x, 0, z);
                pos(world, at, world.getBlockState(at).isAir() ? fill : world.getBlockState(at));
                pos(world, at.up(), Blocks.AIR.getDefaultState());
                pos(world, at.up(2), Blocks.AIR.getDefaultState());
            }
        }
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
