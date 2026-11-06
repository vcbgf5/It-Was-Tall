package com.itwastall.entity;

import com.itwastall.ItWasTall;
import com.itwastall.base.BaseMap;
import com.itwastall.base.BaseTracker;
import com.itwastall.config.ItWasTallConfig;
import com.itwastall.director.Director;
import com.itwastall.registry.ModEntities;
import com.itwastall.registry.ModSounds;
import com.itwastall.world.JournalLog;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.SignBlock;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationPropertyHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * HOUSE EVENTS — the rest of MODPLAN §12 (knock variants) and the §14 events, all fed by the
 * player's mapped {@link BaseMap}:
 * <ul>
 *   <li>{@link Kind#DOOR_KNOCK} (stage 2+): footsteps outside, then three knocks on one of your doors.</li>
 *   <li>{@link Kind#KNOCK_INSIDE} (stage 3+): three knocks from a dark corner INSIDE your base.</li>
 *   <li>{@link Kind#DOOR_OPEN} (stage 3+, DoorEvent): a wooden door opens by itself while you look
 *       away; from stage 4 it may slam shut again a few seconds later.</li>
 *   <li>{@link Kind#CHEST} (stage 3+, ChestShuffleEvent): a few stacks in one of your chests swap
 *       slots. Nothing is ever taken or replaced — only moved.</li>
 *   <li>{@link Kind#SIGN} (stage 3+, SignEvent): a sign with a red message appears outside.</li>
 *   <li>{@link Kind#SHADOW} (stage 4+): knocking on the door and a dark shadow creeping in under it.</li>
 * </ul>
 * Everything is heard / seen only by the haunted player. One event per player per cooldown,
 * night only, near the mapped base; in multiplayer each player has their own timer.
 */
public final class HouseEvents {
    public enum Kind {DOOR_KNOCK, KNOCK_INSIDE, DOOR_OPEN, CHEST, SIGN, SHADOW}

    private static final Map<UUID, Long> NEXT = new HashMap<>();
    private static final List<Scheduled> SCHEDULED = new ArrayList<>();

    private record Scheduled(long tick, UUID player, Consumer<ServerPlayerEntity> action) {
    }

    private HouseEvents() {
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(HouseEvents::tick);
    }

    public static void forget(UUID player) {
        NEXT.remove(player);
        SCHEDULED.removeIf(s -> s.player.equals(player));
    }

    // ------------------------------------------------------------------------ ticking ----

    private static void tick(MinecraftServer server) {
        long now = server.getOverworld().getTime();
        if (!SCHEDULED.isEmpty()) {
            List<Scheduled> due = new ArrayList<>();
            for (Iterator<Scheduled> it = SCHEDULED.iterator(); it.hasNext(); ) {
                Scheduled s = it.next();
                if (s.tick <= now) {
                    due.add(s);
                    it.remove();
                }
            }
            for (Scheduled s : due) {
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(s.player);
                if (p != null) s.action.accept(p);
            }
        }
        if (server.getTicks() % 20 != 0) return;
        ItWasTallConfig.Values cfg = ItWasTallConfig.INSTANCE;
        if (!cfg.houseEvents || Director.phase() == 0) return;
        int stage = Director.stageIndex();
        if (stage < 2) return;

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!eligible(player)) continue;
            Long next = NEXT.get(player.getUuid());
            if (next != null && now < next) continue;
            Random random = player.getRandom();
            if (random.nextDouble() >= MathHelper.clamp(cfg.houseEventChancePerSecond, 0.0, 1.0)) continue;
            List<Kind> allowed = allowedKinds(stage);
            if (allowed.isEmpty()) continue;
            Kind kind = allowed.get(random.nextInt(allowed.size()));
            if (!EventGate.ready(player)) continue;
            if (start(player, kind)) {
                EventGate.mark(player);
                NEXT.put(player.getUuid(), now + 20L * Math.max(30, cfg.houseEventCooldownSeconds));
            }
        }
    }

    private static List<Kind> allowedKinds(int stage) {
        ItWasTallConfig.Values cfg = ItWasTallConfig.INSTANCE;
        List<Kind> kinds = new ArrayList<>();
        kinds.add(Kind.DOOR_KNOCK);
        if (stage >= 3) {
            kinds.add(Kind.KNOCK_INSIDE);
            if (cfg.doorEvents) kinds.add(Kind.DOOR_OPEN);
            if (cfg.chestShuffle) kinds.add(Kind.CHEST);
            if (cfg.signEvents) kinds.add(Kind.SIGN);
        }
        if (stage >= 4) kinds.add(Kind.SHADOW);
        return kinds;
    }

    private static boolean eligible(ServerPlayerEntity player) {
        if (player.isCreative() || player.isSpectator() || !player.isAlive()) return false;
        World world = player.getWorld();
        if (world.getRegistryKey() != World.OVERWORLD || !world.isNight()) return false;
        return nearBase(player, 40);
    }

    private static boolean nearBase(ServerPlayerEntity player, int range) {
        Long anchor = BaseTracker.eventAnchorOf(player);
        return anchor != null && BlockPos.fromLong(anchor).isWithinDistance(player.getBlockPos(), range);
    }

    /** Test trigger (/itwastall event door|doorknock|knockinside|shadow|chest|sign): no chance, no cooldown. */
    public static boolean trigger(ServerPlayerEntity player, Kind kind) {
        if (BaseTracker.eventMapOf(player) == null) return false;
        return start(player, kind);
    }

    private static boolean start(ServerPlayerEntity player, Kind kind) {
        boolean ok = switch (kind) {
            case DOOR_KNOCK -> doorKnock(player, false);
            case SHADOW -> doorKnock(player, true);
            case KNOCK_INSIDE -> knockInside(player);
            case DOOR_OPEN -> doorOpen(player);
            case CHEST -> chestShuffle(player);
            case SIGN -> sign(player);
        };
        if (ok) ItWasTall.LOGGER.debug("House event {} -> {}", kind, player.getName().getString());
        return ok;
    }

    private static void later(ServerPlayerEntity player, int ticks, Consumer<ServerPlayerEntity> action) {
        SCHEDULED.add(new Scheduled(player.getServerWorld().getServer().getOverworld().getTime() + ticks, player.getUuid(), action));
    }

    // ------------------------------------------------------------------ door knocks ----

    /** Footsteps outside, three knocks on the nearest door; {@code shadow} adds the darkness under it. */
    private static boolean doorKnock(ServerPlayerEntity player, boolean shadow) {
        BlockPos door = nearestDoor(player, 28, false);
        if (door == null) return false;
        ServerWorld world = player.getServerWorld();
        BlockPos outside = doorSide(world, door, player, true);
        BlockPos inside = doorSide(world, door, player, false);
        if (outside == null || inside == null) return false;
        Vec3d out = Vec3d.ofBottomCenter(outside);
        MineEvents.playTo(player, ModSounds.SCARE_FOOTSTEPS, out, 0.9f, 0.9f);
        // ETAP 5: sometimes it is not a sound at all — the real It stands outside and knocks (open = chase)
        if (!shadow && Director.stageIndex() >= 5 && player.getRandom().nextDouble() < ItWasTallConfig.INSTANCE.realKnockChance
                && realKnock(player, door, outside)) {
            JournalLog.add(player, "Something knocked on my door. Through the gap I saw how tall it was.");
            return true;
        }
        Vec3d knockAt = Vec3d.ofCenter(door).add(0, 0.4, 0);
        for (int i = 0; i < 3; i++) {
            int when = 34 + i * 12;
            later(player, when, p -> {
                MineEvents.playTo(p, ModSounds.SCARE_KNOCK, knockAt, 1.1f, 0.85f + p.getRandom().nextFloat() * 0.1f);
                if (shadow) shadowUnder(p, inside);
            });
        }
        if (shadow) {
            // the shadow lingers a little after the last knock, then it is gone
            for (int k = 0; k < 6; k++) later(player, 70 + k * 8, p -> shadowUnder(p, inside));
            later(player, 120, p -> MineEvents.playTo(p, ModSounds.AMBIENT_BREATH, out.add(0, 1.6, 0), 0.7f, 0.8f));
        }
        JournalLog.add(player, shadow ? "Knocking on the door. Something dark crept in under it."
                : "Three knocks on my door. I didn't open.");
        return true;
    }

    /** Test entry (/itwastall event realknock): the real It at the nearest mapped door. */
    public static boolean realKnockNearest(ServerPlayerEntity player) {
        BlockPos door = nearestDoor(player, 40, false);
        if (door == null) return false;
        BlockPos outside = doorSide(player.getServerWorld(), door, player, true);
        return outside != null && realKnock(player, door, outside);
    }

    /** Spawns the one real It just outside {@code door}, facing it, in KNOCK mode. */
    private static boolean realKnock(ServerPlayerEntity player, BlockPos door, BlockPos outside) {
        ServerWorld world = player.getServerWorld();
        if (HauntDirector.countActive(world.getServer()) >= Math.max(1, ItWasTallConfig.INSTANCE.maxActiveIts)) return false;
        TallOneEntity it = ModEntities.TALL_ONE.create(world);
        if (it == null) return false;
        // one step back from the door so the 3.4-block body fits (the doorway itself is only 2 high)
        BlockPos stand = outside.offset(Direction.getFacing(outside.getX() - door.getX(), 0, outside.getZ() - door.getZ()));
        it.refreshPositionAndAngles(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 0f, 0f);
        if (!world.isSpaceEmpty(it)) {
            it.refreshPositionAndAngles(outside.getX() + 0.5, outside.getY(), outside.getZ() + 0.5, 0f, 0f);
            if (!world.isSpaceEmpty(it)) return false;
        }
        it.setVictim(player);
        it.startKnock(door);
        return world.spawnEntity(it);
    }

    /** Dark smoke on the floor right inside the door — only this player sees it. */
    private static void shadowUnder(ServerPlayerEntity player, BlockPos inside) {
        ServerWorld world = player.getServerWorld();
        double x = inside.getX() + 0.5, y = inside.getY() + 0.05, z = inside.getZ() + 0.5;
        world.spawnParticles(player, ParticleTypes.SQUID_INK, true, x, y, z, 6, 0.35, 0.01, 0.35, 0.0);
        world.spawnParticles(player, ParticleTypes.LARGE_SMOKE, true, x, y, z, 3, 0.3, 0.01, 0.3, 0.0);
    }

    /** Three slow knocks from INSIDE the base: a dark corner behind the player, at least 4 blocks away. */
    private static boolean knockInside(ServerPlayerEntity player) {
        BaseMap map = BaseTracker.eventMapOf(player);
        if (map == null) return false;
        List<BlockPos> spots = new ArrayList<>(map.all(BaseMap.TYPE_CORNER));
        if (spots.isEmpty()) spots.addAll(map.all(BaseMap.TYPE_ROOM));
        BlockPos best = null;
        double bestScore = -1e9;
        for (BlockPos s : spots) {
            double dist = Math.sqrt(s.getSquaredDistance(player.getBlockPos()));
            if (dist < 4 || dist > 20) continue;
            // prefer spots behind the player
            double behind = -dot(player, Vec3d.ofCenter(s));
            double score = behind * 4 + player.getRandom().nextDouble();
            if (score > bestScore) {
                bestScore = score;
                best = s;
            }
        }
        if (best == null) return false;
        Vec3d at = Vec3d.ofCenter(best);
        for (int i = 0; i < 3; i++) {
            later(player, i * 22, p -> MineEvents.playTo(p, ModSounds.SCARE_KNOCK, at, 0.9f, 0.75f));
        }
        later(player, 80, p -> MineEvents.playTo(p, ModSounds.AMBIENT_WHISPER, at, 0.6f, 0.8f));
        JournalLog.add(player, "Someone knocked. From inside the house.");
        return true;
    }

    // ------------------------------------------------------------------ door event ----

    /** A closed wooden door of the base opens while the player is not looking; may slam (stage 4+). */
    private static boolean doorOpen(ServerPlayerEntity player) {
        BlockPos door = nearestDoor(player, 20, true);
        if (door == null) return false;
        ServerWorld world = player.getServerWorld();
        BlockState st = world.getBlockState(door);
        if (!(st.getBlock() instanceof DoorBlock doorBlock) || st.get(DoorBlock.OPEN)) return false;
        doorBlock.setOpen(null, world, st, door, true);
        if (Director.stageIndex() >= 4 && player.getRandom().nextBoolean()) {
            later(player, 50 + player.getRandom().nextInt(40), p -> {
                BlockState now = world.getBlockState(door);
                if (now.getBlock() instanceof DoorBlock d && now.get(DoorBlock.OPEN)
                        && world.getOtherEntities(null, new net.minecraft.util.math.Box(door).expand(0.2, 1, 0.2)).isEmpty()) {
                    d.setOpen(null, world, now, door, false);
                }
            });
            JournalLog.add(player, "The door opened by itself. Then it slammed shut.");
        } else {
            JournalLog.add(player, "I found the door open. I closed it. I'm sure I closed it.");
        }
        return true;
    }

    // ------------------------------------------------------------------ chest shuffle ----

    /** Swaps a few slots inside one mapped chest/barrel. Never removes, never replaces an item. */
    private static boolean chestShuffle(ServerPlayerEntity player) {
        BaseMap map = BaseTracker.eventMapOf(player);
        if (map == null) return false;
        ServerWorld world = player.getServerWorld();
        List<BlockPos> chests = map.all(BaseMap.TYPE_CHEST);
        java.util.Collections.shuffle(chests, new java.util.Random(player.getRandom().nextLong()));
        for (BlockPos pos : chests) {
            if (!world.isChunkLoaded(pos)) continue;
            if (pos.isWithinDistance(player.getBlockPos(), 5)) continue;            // not right next to you
            if (dot(player, Vec3d.ofCenter(pos)) > 0.5 && pos.isWithinDistance(player.getBlockPos(), 16)) continue; // you would see it
            if (!(world.getBlockEntity(pos) instanceof LootableContainerBlockEntity box)) continue;
            if (box.getLootTable() != null) continue;                              // never touch unrolled loot
            if (ChestBlockEntity.getPlayersLookingInChestCount(world, pos) > 0) continue;
            int size = box.size();
            List<Integer> filled = new ArrayList<>();
            for (int i = 0; i < size; i++) if (!box.getStack(i).isEmpty()) filled.add(i);
            if (filled.size() < 2) continue;
            Random random = player.getRandom();
            int swaps = 2 + random.nextInt(3);
            for (int s = 0; s < swaps; s++) {
                int a = filled.get(random.nextInt(filled.size()));
                int b = random.nextInt(size);
                if (a == b) continue;
                ItemStack sa = box.getStack(a).copy();
                ItemStack sb = box.getStack(b).copy();
                box.setStack(a, sb);
                box.setStack(b, sa);
            }
            box.markDirty();
            Vec3d at = Vec3d.ofCenter(pos);
            MineEvents.playTo(player, SoundEvents.BLOCK_CHEST_OPEN, at, 0.35f, 0.7f);
            later(player, 25, p -> MineEvents.playTo(p, SoundEvents.BLOCK_CHEST_CLOSE, at, 0.35f, 0.7f));
            JournalLog.add(player, "Things in my chest are not where I left them.");
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ sign ----

    /** A sign with a red message outside the base, facing it, placed where the player is not looking. */
    private static boolean sign(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        Long anchorL = BaseTracker.eventAnchorOf(player);
        if (anchorL == null) return false;
        BlockPos anchor = BlockPos.fromLong(anchorL);
        Random random = player.getRandom();
        ItWasTallConfig.Values cfg = ItWasTallConfig.INSTANCE;
        if (cfg.signMessages == null || cfg.signMessages.isEmpty()) return false;

        // candidates: outside every mapped door first, then a ring around the base
        List<BlockPos> candidates = new ArrayList<>();
        BaseMap map = BaseTracker.eventMapOf(player);
        if (map != null) {
            for (BlockPos d : map.all(BaseMap.TYPE_DOOR)) {
                BlockPos out = doorSide(world, d, player, true);
                if (out != null) candidates.add(out.offset(Direction.Type.HORIZONTAL.random(random)));
            }
        }
        for (int i = 0; i < 16; i++) {
            double a = random.nextDouble() * Math.PI * 2, r = 6 + random.nextDouble() * 6;
            int x = MathHelper.floor(anchor.getX() + Math.cos(a) * r), z = MathHelper.floor(anchor.getZ() + Math.sin(a) * r);
            candidates.add(new BlockPos(x, world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z), z));
        }
        for (BlockPos spot : candidates) {
            if (!world.isAir(spot) || !world.isAir(spot.up())) continue;
            BlockState below = world.getBlockState(spot.down());
            if (!below.isSolidBlock(world, spot.down()) || below.isIn(BlockTags.LEAVES)) continue;
            if (spot.isWithinDistance(player.getBlockPos(), 4)) continue;
            if (dot(player, Vec3d.ofCenter(spot)) > 0.3 && player.squaredDistanceTo(Vec3d.ofCenter(spot)) < 48 * 48) continue;
            // face the base
            double dx = anchor.getX() - spot.getX(), dz = anchor.getZ() - spot.getZ();
            float yaw = (float) (MathHelper.atan2(dz, dx) * MathHelper.DEGREES_PER_RADIAN) - 90f;
            world.setBlockState(spot, Blocks.DARK_OAK_SIGN.getDefaultState()
                    .with(SignBlock.ROTATION, RotationPropertyHelper.fromYaw(yaw)), 3);
            if (world.getBlockEntity(spot) instanceof SignBlockEntity be) {
                String[] lines = cfg.signMessages.get(random.nextInt(cfg.signMessages.size())).split("\\|");
                SignText text = new SignText().withColor(DyeColor.RED);
                for (int i = 0; i < Math.min(4, lines.length); i++) text = text.withMessage(i, Text.literal(lines[i]));
                be.setText(text, true);
                be.setWaxed(true);
                be.markDirty();
            }
            JournalLog.add(player, "Someone put a sign outside my home.");
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ helpers ----

    /** Nearest mapped door (lower half) within range; {@code wooden} = only doors a hand can open. */
    @Nullable
    private static BlockPos nearestDoor(ServerPlayerEntity player, int range, boolean wooden) {
        BaseMap map = BaseTracker.eventMapOf(player);
        if (map == null) return null;
        ServerWorld world = player.getServerWorld();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos p : map.all(BaseMap.TYPE_DOOR)) {
            if (!world.isChunkLoaded(p)) continue;
            BlockState st = world.getBlockState(p);
            if (!(st.getBlock() instanceof DoorBlock)) continue;
            if (st.get(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
                p = p.down();
                st = world.getBlockState(p);
                if (!(st.getBlock() instanceof DoorBlock)) continue;
            }
            if (wooden && !st.isIn(BlockTags.WOODEN_DOORS)) continue;
            if (wooden && dot(player, Vec3d.ofCenter(p)) > 0.3) continue;   // it waits until you look away
            double d = p.getSquaredDistance(player.getBlockPos());
            if (d < bestDist && d <= (double) range * range) {
                bestDist = d;
                best = p.toImmutable();
            }
        }
        return best;
    }

    /** The cell on one side of a door: {@code outside} = the side farther from the player. */
    @Nullable
    private static BlockPos doorSide(ServerWorld world, BlockPos door, ServerPlayerEntity player, boolean outside) {
        BlockState st = world.getBlockState(door);
        if (!(st.getBlock() instanceof DoorBlock)) return null;
        Direction facing = st.get(DoorBlock.FACING);
        BlockPos a = door.offset(facing), b = door.offset(facing.getOpposite());
        boolean aFarther = a.getSquaredDistance(player.getBlockPos()) > b.getSquaredDistance(player.getBlockPos());
        BlockPos pick = (aFarther == outside) ? a : b;
        return world.getBlockState(pick).getCollisionShape(world, pick).isEmpty() ? pick.toImmutable() : null;
    }

    /** Cosine between the player's view and the direction to {@code target}. */
    private static double dot(ServerPlayerEntity player, Vec3d target) {
        Vec3d look = player.getRotationVec(1f);
        Vec3d to = target.subtract(player.getEyePos());
        if (to.lengthSquared() < 1.0E-6) return 1;
        return look.dotProduct(to.normalize());
    }

    /** World time of this player's next roll (-1 = not scheduled yet), for /itwastall debug events. */
    public static long nextAt(UUID player) {
        Long next = NEXT.get(player);
        return next == null ? -1 : next;
    }
}
