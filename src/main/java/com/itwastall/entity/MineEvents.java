package com.itwastall.entity;

import com.itwastall.config.ItWasTallConfig;
import com.itwastall.director.Director;
import com.itwastall.director.Stage;
import com.itwastall.registry.ModEntities;
import com.itwastall.registry.ModSounds;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.LightType;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Things that happen while a player is in a mine / cave:
 * <ul>
 *     <li>{@link Kind#DIG} - It appears inside the rock up to 30 blocks above the player and digs its way down
 *     with an iron pickaxe, then chases.</li>
 *     <li>{@link Kind#SOUNDS} - only digging sounds, coming closer from above, then a single loud crack.</li>
 *     <li>{@link Kind#TORCHES} - torches behind the player go out one by one, from the farthest to the closest.</li>
 *     <li>{@link Kind#PEEK} - It stands right behind the player. Turning around = 0.1 s jumpscare.</li>
 * </ul>
 */
public final class MineEvents {
    public enum Kind {DIG, SOUNDS, TORCHES, PEEK}

    private static final Map<UUID, PlayerState> STATES = new HashMap<>();
    private static final List<Active> ACTIVE = new ArrayList<>();

    private static final class PlayerState {
        int undergroundTicks;
        int cooldown;
    }

    /** A running event; {@link #tick()} returns true when finished. */
    private interface Active {
        boolean tick();
    }

    private MineEvents() {
    }

    /** Drops per-player data when a player leaves the server. */
    public static void forget(UUID player) {
        STATES.remove(player);
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(MineEvents::tick);
    }

    public static boolean isUnderground(PlayerEntity player) {
        World world = player.getWorld();
        if (world.getRegistryKey() != World.OVERWORLD) return false;
        BlockPos pos = player.getBlockPos();
        return player.getY() < ItWasTallConfig.INSTANCE.mineMaxY && world.getLightLevel(LightType.SKY, pos.up()) == 0;
    }

    // ------------------------------------------------------------------ ticking ----

    private static void tick(MinecraftServer server) {
        ACTIVE.removeIf(Active::tick);
        if (server.getTicks() % 20 != 0) return;

        ItWasTallConfig.Values cfg = ItWasTallConfig.INSTANCE;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.isCreative() || player.isSpectator() || !player.isAlive()) continue;
            PlayerState st = STATES.computeIfAbsent(player.getUuid(), u -> new PlayerState());
            st.cooldown = Math.max(0, st.cooldown - 20);
            st.undergroundTicks = isUnderground(player) ? st.undergroundTicks + 20 : Math.max(0, st.undergroundTicks - 60);

            if (!cfg.mineEvents || st.cooldown > 0 || st.undergroundTicks < cfg.mineMinSecondsUnderground * 20) continue;
            if (player.getRandom().nextDouble() >= cfg.mineEventChancePerSecond * stageChanceMul()) continue;
            if (itIsNear(player)) continue;
            if (trigger(player, randomKind(player.getRandom()))) {
                st.cooldown = cfg.mineEventCooldownSeconds * 20;
            }
        }
    }

    private static boolean itIsNear(ServerPlayerEntity player) {
        Box box = player.getBoundingBox().expand(64);
        return !player.getServerWorld().getEntitiesByType(ModEntities.TALL_ONE, box, e -> true).isEmpty();
    }

    /**
     * Per-stage chance multiplier for mine events (local mapping — the Stage table has no dedicated
     * getter; values follow PHASES.md §3/Etapy notes, documented here):
     * FAZA PRE = x0.5 (PHASES §1), ETAP 0 = x0.5 (" tylko SOUNDS, szansa połowa bazowa"),
     * ETAP 1 = x1.0, ETAP 2 = +40%, ETAP 3+ = +50%.
     */
    private static double stageChanceMul() {
        if (Director.phase() == 0) return 0.5;
        return switch (Director.currentStage().ordinal()) {
            case 0 -> 0.5;
            case 1 -> 1.0;
            case 2 -> 1.4;
            default -> 1.5;
        };
    }

    /**
     * Picks an event kind considering only what the current stage allows (PHASES.md §3
     * "Parametry per ETAP", rows DIG / TORCHES(douse) / PEEK; SOUNDS is always allowed).
     * Base spread keeps the old 35/25/20/20 weights; PEEK arrives at ETAP 4 and is rolled
     * with a higher weight at ETAP 5 (local mapping, noted here). FAZA PRE: only SOUNDS.
     */
    private static Kind randomKind(Random random) {
        if (Director.phase() == 0) return Kind.SOUNDS; // PHASES §1: listening sounds only
        Stage st = Director.currentStage();
        List<Kind> pool = new ArrayList<>();
        addTimes(pool, Kind.DIG, st.digEnabled() ? 35 : 0);
        addTimes(pool, Kind.SOUNDS, 25);
        addTimes(pool, Kind.TORCHES, st.torchDouseLevel() >= 3 ? 20 : 0); // level 3 = mine only
        addTimes(pool, Kind.PEEK, st.peekEnabled() ? (st.ordinal() >= 5 ? 30 : 15) : 0);
        if (pool.isEmpty()) return Kind.SOUNDS; // ETAP 0: dig/torches/peek all blocked -> sounds
        return pool.get(random.nextInt(pool.size()));
    }

    private static void addTimes(List<Kind> pool, Kind kind, int times) {
        for (int i = 0; i < times; i++) pool.add(kind);
    }

    /** Starts an event for the player. Falls back to a milder event if the chosen one can't happen here. */
    public static boolean trigger(ServerPlayerEntity player, Kind kind) {
        boolean ok = switch (kind) {
            case DIG -> spawnDigger(player) || startSounds(player);
            case SOUNDS -> startSounds(player);
            case TORCHES -> startTorches(player) || startSounds(player);
            case PEEK -> spawnPeeker(player) || startTorches(player) || startSounds(player);
        };
        if (ok && Director.phase() == 1) Director.addTension(0.5); // PHASES §2: "inne wydarzenie (kopalnia)" +0.5, 1x na zdarzenie
        return ok;
    }

    // ---------------------------------------------------------------------- DIG ----

    private static boolean spawnDigger(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        if (!ItWasTallConfig.INSTANCE.digging || !world.getGameRules().getBoolean(net.minecraft.world.GameRules.DO_MOB_GRIEFING)) {
            return false;
        }
        Random random = world.getRandom();
        BlockPos origin = player.getBlockPos();
        for (int attempt = 0; attempt < 48; attempt++) {
            BlockPos pos = origin.add(random.nextInt(17) - 8, 10 + random.nextInt(15), random.nextInt(17) - 8);
            if (pos.getSquaredDistance(origin) > 30 * 30) continue;
            if (!isSolidRockColumn(world, pos)) continue;

            // hollow out a pocket for its body
            for (int y = 0; y < 4; y++) world.setBlockState(pos.up(y), Blocks.CAVE_AIR.getDefaultState());
            TallOneEntity it = ModEntities.TALL_ONE.create(world);
            if (it == null) return false;
            it.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360f, 0f);
            it.setVictim(player);
            it.setPersistent();
            world.spawnEntity(it);
            it.startDig();
            playTo(player, ModSounds.DIG, Vec3d.ofCenter(pos), 1.5f, 0.6f);
            return true;
        }
        return false;
    }

    private static boolean isSolidRockColumn(ServerWorld world, BlockPos pos) {
        for (int y = -1; y < 5; y++) {
            BlockPos p = pos.up(y);
            BlockState st = world.getBlockState(p);
            float hardness = st.getHardness(world, p);
            if (!st.isOpaqueFullCube(world, p) || !st.getFluidState().isEmpty() || hardness < 0 || hardness >= 50f) return false;
            if (st.hasBlockEntity()) return false; // never eat chests/spawners
        }
        return true;
    }

    // ------------------------------------------------------------------- SOUNDS ----

    private static boolean startSounds(ServerPlayerEntity player) {
        Random r = player.getRandom();
        Vec3d start = player.getPos().add(r.nextInt(13) - 6, 18 + r.nextInt(8), r.nextInt(13) - 6);
        ACTIVE.add(new DigSoundTrail(player, start));
        return true;
    }

    private static final class DigSoundTrail implements Active {
        private static final int LENGTH = 260;
        private final ServerPlayerEntity player;
        private final Vec3d start;
        private int t;
        private int next = 0;

        DigSoundTrail(ServerPlayerEntity player, Vec3d start) {
            this.player = player;
            this.start = start;
        }

        @Override
        public boolean tick() {
            if (player.isRemoved() || !player.isAlive()) return true;
            t++;
            Vec3d end = player.getPos().add(0, 3.5, 0);
            Vec3d pos = start.lerp(end, Math.min(1.0, t / (double) LENGTH));
            if (t >= next && t < LENGTH) {
                Random r = player.getRandom();
                playTo(player, ModSounds.DIG, pos, 1.0f, 0.75f + r.nextFloat() * 0.3f);
                if (r.nextInt(3) == 0) playTo(player, SoundEvents.BLOCK_STONE_HIT, pos, 1.0f, 0.5f);
                // little pauses make it feel like someone is actually mining
                next = t + (r.nextInt(5) == 0 ? 30 + r.nextInt(30) : 8 + r.nextInt(8));
            }
            if (t == LENGTH + 40) {
                playTo(player, SoundEvents.BLOCK_STONE_BREAK, end, 2.0f, 0.5f);
                return true;
            }
            return false;
        }
    }

    // ------------------------------------------------------------------ TORCHES ----

    private static boolean startTorches(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        BlockPos c = player.getBlockPos();
        List<BlockPos> torches = new ArrayList<>();
        for (BlockPos p : BlockPos.iterate(c.add(-20, -8, -20), c.add(20, 8, 20))) {
            BlockState st = world.getBlockState(p);
            if (st.isOf(Blocks.TORCH) || st.isOf(Blocks.WALL_TORCH) || st.isOf(Blocks.SOUL_TORCH) || st.isOf(Blocks.SOUL_WALL_TORCH)) {
                torches.add(p.toImmutable());
            }
        }
        if (torches.isEmpty()) return false;
        torches.sort(Comparator.comparingDouble((BlockPos p) -> p.getSquaredDistance(c)).reversed());
        ACTIVE.add(new TorchDouse(player, torches));
        return true;
    }

    private static final class TorchDouse implements Active {
        private static final int MAX = 8;
        private final ServerPlayerEntity player;
        private final List<BlockPos> torches;
        private int t;
        private int doused;

        TorchDouse(ServerPlayerEntity player, List<BlockPos> torches) {
            this.player = player;
            this.torches = torches;
        }

        @Override
        public boolean tick() {
            if (player.isRemoved() || ++t > 20 * 90) return true;
            if (t % 25 != 0) return false;
            ServerWorld world = player.getServerWorld();
            Vec3d look = player.getRotationVec(1f);
            for (Iterator<BlockPos> it = torches.iterator(); it.hasNext(); ) {
                BlockPos p = it.next();
                BlockState st = world.getBlockState(p);
                if (!(st.isOf(Blocks.TORCH) || st.isOf(Blocks.WALL_TORCH) || st.isOf(Blocks.SOUL_TORCH) || st.isOf(Blocks.SOUL_WALL_TORCH))) {
                    it.remove();
                    continue;
                }
                Vec3d to = Vec3d.ofCenter(p).subtract(player.getEyePos()).normalize();
                if (look.dotProduct(to) > 0.25) continue; // player is looking at it - wait
                world.removeBlock(p, false);
                world.playSound(null, p, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.6f, 0.6f);
                world.spawnParticles(ParticleTypes.SMOKE, p.getX() + 0.5, p.getY() + 0.6, p.getZ() + 0.5, 8, 0.08, 0.08, 0.08, 0.01);
                it.remove();
                doused++;
                break;
            }
            return torches.isEmpty() || doused >= MAX;
        }
    }

    // --------------------------------------------------------------------- PEEK ----

    private static boolean spawnPeeker(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        Vec3d look = player.getRotationVec(1f);
        Vec3d back = new Vec3d(-look.x, 0, -look.z);
        if (back.lengthSquared() < 1.0E-4) return false;
        back = back.normalize();
        TallOneEntity it = ModEntities.TALL_ONE.create(world);
        if (it == null) return false;
        for (double d : new double[]{2.5, 3.0, 3.5, 2.0, 4.0}) {
            Vec3d p = player.getPos().add(back.multiply(d));
            for (int dy = 1; dy >= -2; dy--) {
                BlockPos bp = BlockPos.ofFloored(p.x, player.getY() + dy, p.z);
                if (world.getBlockState(bp.down()).getCollisionShape(world, bp.down()).isEmpty()) continue;
                it.refreshPositionAndAngles(p.x, bp.getY(), p.z, 0f, 0f);
                if (world.isSpaceEmpty(it)) {
                    it.setVictim(player);
                    world.spawnEntity(it);
                    it.startPeek();
                    playTo(player, ModSounds.AMBIENT_BREATH, it.getEyePos(), 0.8f, 0.9f);
                    return true;
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------- helpers ----

    /** Plays a sound that only this player hears. */
    public static void playTo(ServerPlayerEntity player, SoundEvent sound, Vec3d pos, float volume, float pitch) {
        player.networkHandler.sendPacket(new PlaySoundS2CPacket(Registries.SOUND_EVENT.getEntry(sound), SoundCategory.HOSTILE,
                pos.x, pos.y, pos.z, ModSounds.vol(volume), pitch, player.getRandom().nextLong()));
    }

    /** Underground seconds and event cooldown (ticks) for /itwastall debug events. */
    public static String debugLine(UUID player) {
        PlayerState st = STATES.get(player);
        return st == null ? "no data" : "underground " + st.undergroundTicks / 20 + " s, cooldown " + st.cooldown / 20 + " s";
    }
}
