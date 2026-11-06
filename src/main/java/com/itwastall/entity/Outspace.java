package com.itwastall.entity;

import com.itwastall.ItWasTall;
import com.itwastall.config.ItWasTallConfig;
import com.itwastall.director.Director;
import com.itwastall.net.ModNetworking;
import com.itwastall.registry.ModDimensions;
import com.itwastall.registry.ModEntities;
import com.itwastall.registry.ModItems;
import com.itwastall.world.JournalLog;
import com.itwastall.world.OutspaceChunkGenerator;
import com.itwastall.world.OutspaceMaze;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * OUTSPACE — the dream world. From stage 5, when someone lies down to sleep, sometimes (once a night,
 * one player) they are pulled into an endless maze of wooden halls for {@code outspaceSeconds} (30 s).
 * Nothing there can hurt you: no damage, no death, nothing is lost. But the Old One lives there — it
 * does not tear open, it just runs at you, and if it reaches you: a jumpscare and you wake up in your bed.
 * Some rooms hold a chest: dream books, notes, and rarely a Warding Crystal (you keep what you find).
 */
public final class Outspace {
    private record Dream(RegistryKey<World> returnDim, @Nullable BlockPos bed, Vec3d fallback, long endsAt, long oldOneAt) {
    }

    private static final Map<UUID, Dream> DREAMERS = new HashMap<>();
    /** Players about to be pulled in (world time when it happens, if they are still asleep). */
    private static final Map<UUID, Long> PULL_AT = new HashMap<>();
    private static long usedNight = -1;

    private static final String[][] DREAM_BOOKS = {
            {"dream_halls", "THE HALLS", "The halls never end.\n\nI walked for an hour. Or a minute.\n\nEvery room has the same torch.\n\nI think the torch is watching me."},
            {"dream_wake", "WAKE UP", "WAKE UP\n\nWAKE UP\n\nWAKE UP\n\nit can hear you thinking"},
            {"dream_builder", "WHO BUILT THIS", "Oak planks. Cobblestone. Torches.\n\nThe way a child builds a house.\n\nThe way a PLAYER builds a house.\n\nThere is no door that leads out."},
            {"dream_steve", "IT LOOKED LIKE ME", "It wore a face I knew.\n\nIt did not open this time.\n\nIt just ran."},
            {"dream_count", "COUNTING", "I count the rooms to stay awake.\n\n1 2 3 4 5 6 7 8 9 10 11 12 13 14\n\nthe 15th room had my bed in it\n\nsomeone was lying in it"}
    };

    private Outspace() {
    }

    public static boolean inOutspace(ServerPlayerEntity player) {
        return player.getWorld().getRegistryKey() == ModDimensions.OUTSPACE;
    }

    public static void init() {
        EntitySleepEvents.START_SLEEPING.register((entity, sleepingPos) -> {
            if (entity instanceof ServerPlayerEntity sp) maybePull(sp);
        });
        // nothing hurts in a dream: no damage, and certainly no death (nothing is dropped)
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
                !(entity instanceof ServerPlayerEntity sp && inOutspace(sp)));
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayerEntity sp && inOutspace(sp)) {
                sp.setHealth(sp.getMaxHealth());
                wake(sp, false);
                return false;
            }
            return true;
        });
        // someone who logged out mid-dream (or after a restart) wakes up at home, never stuck in the maze
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> {
            ServerPlayerEntity p = handler.getPlayer();
            if (inOutspace(p) && !DREAMERS.containsKey(p.getUuid())) sendHome(p, null);
        }));
        ServerTickEvents.END_SERVER_TICK.register(Outspace::tick);
    }

    public static void forget(UUID player) {
        DREAMERS.remove(player);
        PULL_AT.remove(player);
    }

    // ------------------------------------------------------------------ entering ----

    private static void maybePull(ServerPlayerEntity player) {
        ItWasTallConfig.Values cfg = ItWasTallConfig.INSTANCE;
        if (!cfg.outspace || Director.phase() != 1 || Director.stageIndex() < cfg.outspaceMinStage) return;
        if (player.isCreative() || player.isSpectator() || player.getWorld().getRegistryKey() != World.OVERWORLD) return;
        long night = player.getWorld().getTimeOfDay() / 24000L;
        if (night == usedNight) return;                       // one dreamer a night, on the whole server
        if (player.getRandom().nextDouble() >= MathHelper.clamp(cfg.outspaceChance, 0.0, 1.0)) return;
        usedNight = night;
        PULL_AT.put(player.getUuid(), player.getServer().getOverworld().getTime() + 40);   // 2 s into the sleep
    }

    /** Pulls the player into the dream (also /itwastall outspace). False when the dimension is missing. */
    public static boolean enter(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null || inOutspace(player)) return false;
        ServerWorld out = server.getWorld(ModDimensions.OUTSPACE);
        if (out == null) {
            ItWasTall.LOGGER.warn("The outspace dimension is missing (datapack not loaded?).");
            return false;
        }
        BlockPos bed = player.getSpawnPointPosition();
        RegistryKey<World> bedDim = bed != null ? player.getSpawnPointDimension() : player.getWorld().getRegistryKey();
        if (bed == null && player.getSleepingPosition().isPresent()) bed = player.getSleepingPosition().get();
        Vec3d fallback = player.getPos();
        if (player.isSleeping()) player.wakeUp(true, true);

        Random random = player.getRandom();
        int cx = random.nextInt(400) - 200, cz = random.nextInt(400) - 200;
        Vec3d start = new Vec3d(OutspaceMaze.cellCenterX(cx), OutspaceMaze.FLOOR + 1, OutspaceMaze.cellCenterZ(cz));
        long now = server.getOverworld().getTime();
        int seconds = Math.max(5, ItWasTallConfig.INSTANCE.outspaceSeconds);
        DREAMERS.put(player.getUuid(), new Dream(bedDim, bed, fallback, now + 20L * seconds, now + 60));
        player.teleportTo(new TeleportTarget(out, start, Vec3d.ZERO, random.nextFloat() * 360f, 0f, TeleportTarget.NO_OP));
        ModNetworking.sendScareText(player, "you are dreaming.");
        JournalLog.add(player, "I fell asleep and woke up somewhere else. Wooden halls. Torches. No way out.");
        ItWasTall.LOGGER.info("{} was pulled into outspace for {} s.", player.getName().getString(), seconds);
        return true;
    }

    // ------------------------------------------------------------------ waking up ----

    /** Ends the dream: back to the bed (or where they fell asleep). {@code caught} = the Old One got them. */
    public static void wake(ServerPlayerEntity player, boolean caught) {
        Dream d = DREAMERS.remove(player.getUuid());
        for (OldOneEntity one : dreamOldOnes(player)) one.discard();
        sendHome(player, d);
        JournalLog.add(player, caught ? "It caught me in the dream. I woke up in my bed, screaming."
                : "I dreamed of endless wooden halls. Then I woke up.");
    }

    private static void sendHome(ServerPlayerEntity player, @Nullable Dream d) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        RegistryKey<World> dim = d != null ? d.returnDim() : (player.getSpawnPointPosition() != null ? player.getSpawnPointDimension() : World.OVERWORLD);
        ServerWorld world = server.getWorld(dim);
        if (world == null) world = server.getOverworld();
        BlockPos bed = d != null ? d.bed() : player.getSpawnPointPosition();
        Vec3d target = null;
        if (bed != null) {
            BlockState st = world.getBlockState(bed);
            if (st.getBlock() instanceof BedBlock) {
                Direction facing = st.get(BedBlock.FACING);
                target = BedBlock.findWakeUpPosition(EntityType.PLAYER, world, bed, facing, player.getYaw()).orElse(null);
            }
        }
        if (target == null && d != null && d.returnDim() == world.getRegistryKey()) target = d.fallback();
        if (target == null) {
            BlockPos spawn = world.getSpawnPos();
            target = Vec3d.ofBottomCenter(world.getTopPosition(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, spawn));
        }
        player.teleportTo(new TeleportTarget(world, target, Vec3d.ZERO, player.getYaw(), 0f, TeleportTarget.NO_OP));
    }

    // ------------------------------------------------------------------ ticking ----

    private static void tick(MinecraftServer server) {
        long now = server.getOverworld().getTime();
        if (!PULL_AT.isEmpty()) {
            for (UUID uuid : new ArrayList<>(PULL_AT.keySet())) {
                if (now < PULL_AT.get(uuid)) continue;
                PULL_AT.remove(uuid);
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(uuid);
                if (p != null && p.isSleeping()) enter(p);
            }
        }
        if (DREAMERS.isEmpty()) return;
        for (UUID uuid : new ArrayList<>(DREAMERS.keySet())) {
            Dream d = DREAMERS.get(uuid);
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(uuid);
            if (p == null) {
                DREAMERS.remove(uuid);          // logged out: the JOIN hook sends them home next time
                continue;
            }
            if (!inOutspace(p)) {
                DREAMERS.remove(uuid);          // left some other way (command, death elsewhere...)
                continue;
            }
            if (now >= d.endsAt()) {
                wake(p, false);
                continue;
            }
            long left = (d.endsAt() - now) / 20;
            if (now % 20 == 0) {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.SATURATION, 40, 0, true, false));
                decorateAround(p);
                if (left == 5) ModNetworking.sendScareText(p, "wake up.");
            }
            if (now >= d.oldOneAt() && dreamOldOnes(p).isEmpty() && now % 20 == 10) spawnDreamOldOne(p);
        }
    }

    private static List<OldOneEntity> dreamOldOnes(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return List.of();
        ServerWorld out = server.getWorld(ModDimensions.OUTSPACE);
        if (out == null) return List.of();
        List<? extends OldOneEntity> found = out.getEntitiesByType(ModEntities.OLD_ONE,
                e -> e.isDream() && player.getUuid().equals(e.victimUuid()));
        return new ArrayList<>(found);
    }

    /** The Old One appears a few rooms away and comes for you. */
    private static void spawnDreamOldOne(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        int pcx = Math.floorDiv(player.getBlockX(), OutspaceMaze.CELL), pcz = Math.floorDiv(player.getBlockZ(), OutspaceMaze.CELL);
        Random random = player.getRandom();
        for (int attempt = 0; attempt < 16; attempt++) {
            int dx = random.nextInt(7) - 3, dz = random.nextInt(7) - 3;
            if (Math.abs(dx) + Math.abs(dz) < 2) continue;
            OldOneEntity one = ModEntities.OLD_ONE.create(world);
            if (one == null) return;
            one.refreshPositionAndAngles(OutspaceMaze.cellCenterX(pcx + dx), OutspaceMaze.FLOOR + 1, OutspaceMaze.cellCenterZ(pcz + dz), 0f, 0f);
            if (!world.isSpaceEmpty(one)) continue;
            one.setVictim(player);
            one.setDream(true);
            world.spawnEntity(one);
            MineEvents.playTo(player, com.itwastall.registry.ModSounds.AMBIENT_STING, one.getPos(), 1.0f, 0.8f);
            return;
        }
    }

    /** Some rooms near the dreamer hold a chest (decided by the room, so it never doubles up). */
    private static void decorateAround(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        long seed = world.getChunkManager().getChunkGenerator() instanceof OutspaceChunkGenerator g ? g.seed() : 0L;
        int pcx = Math.floorDiv(player.getBlockX(), OutspaceMaze.CELL), pcz = Math.floorDiv(player.getBlockZ(), OutspaceMaze.CELL);
        double chance = MathHelper.clamp(ItWasTallConfig.INSTANCE.outspaceLootChance, 0.0, 1.0);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int cx = pcx + dx, cz = pcz + dz;
                long h = mix(seed, cx, cz);
                trySleeperRoom(world, seed, cx, cz);
                if ((h & 0xFFFF) / 65536.0 >= chance) continue;
                BlockPos at = new BlockPos(cx * OutspaceMaze.CELL + 1, OutspaceMaze.FLOOR + 1, cz * OutspaceMaze.CELL + 1);
                if (!world.isChunkLoaded(at) || !world.getBlockState(at).isAir()) continue;   // already there (or blocked)
                world.setBlockState(at, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.SOUTH), 2);
                if (world.getBlockEntity(at) instanceof ChestBlockEntity chest) {
                    String[] book = DREAM_BOOKS[(int) Math.floorMod(h >>> 20, DREAM_BOOKS.length)];
                    chest.setStack(13, ModItems.loreBook(book[0], book[1], book[2]));
                    if (((h >>> 32) & 0xFF) < 40) chest.setStack(11, new ItemStack(ModItems.WARDING_CRYSTAL));   // ~15 %
                    if (((h >>> 40) & 0xFF) < 90) chest.setStack(15, new ItemStack(net.minecraft.item.Items.TORCH, 1 + (int) ((h >>> 48) & 3)));
                    chest.markDirty();
                }
            }
        }
    }

    /**
     * The Sleeper's room: in the top-left cell of some great halls, a bed in the corner with someone in it,
     * a wool rug (wool swallows footsteps) and a chest with a Warding Crystal. Rebuilt for every dreamer:
     * if the bed is there but its sleeper is gone, a new one lies down.
     */
    private static void trySleeperRoom(ServerWorld world, long seed, int cx, int cz) {
        if (Math.floorMod(cx, 2) != 0 || Math.floorMod(cz, 2) != 0 || !OutspaceMaze.hall(seed, cx, cz)) return;
        long h = mix(seed ^ 0x5133_1EE7L, cx, cz);
        if ((h & 0xFFFF) / 65536.0 >= MathHelper.clamp(ItWasTallConfig.INSTANCE.outspaceSleeperChance, 0.0, 1.0)) return;
        int x0 = cx * OutspaceMaze.CELL, z0 = cz * OutspaceMaze.CELL, y = OutspaceMaze.FLOOR + 1;
        BlockPos head = new BlockPos(x0 + 2, y, z0 + 1), foot = head.south();
        if (!world.isChunkLoaded(head) || !world.isChunkLoaded(foot.south(2))) return;
        boolean bedThere = world.getBlockState(head).getBlock() instanceof BedBlock;
        if (!bedThere) {
            if (!world.getBlockState(head).isAir() || !world.getBlockState(foot).isAir()) return;
            SleeperEntity s = SleeperEntity.makeBed(world, head, Direction.NORTH);
            if (s == null) return;
            BlockPos chestPos = head.east();
            if (world.getBlockState(chestPos).isAir()) {
                world.setBlockState(chestPos, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.SOUTH), 2);
                if (world.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
                    chest.setStack(13, new ItemStack(ModItems.WARDING_CRYSTAL));
                    chest.setStack(11, ModItems.loreBook("dream_face", "IT HAS YOUR FACE",
                            "I found my own bed in the dream.\n\nSomeone was sleeping in it.\n\nI did not run. I did not breathe.\n\nI took the blue stone and walked away on the wool.\n\nIt did not wake up.\n\nThis time."));
                    chest.markDirty();
                }
            }
            for (int dz = 0; dz <= 2; dz++) {
                BlockPos rug = foot.south().add(-1, 0, dz - 1);
                for (int dx = 0; dx <= 2; dx++) {
                    BlockPos r = rug.add(dx, 0, 0);
                    if (world.getBlockState(r).isAir()) world.setBlockState(r, Blocks.WHITE_CARPET.getDefaultState(), 2);
                }
            }
            BlockPos candle = head.west();
            if (world.getBlockState(candle).isAir()) world.setBlockState(candle, Blocks.CANDLE.getDefaultState(), 2);
            return;
        }
        // the bed is there: make sure someone sleeps in it
        if (world.getEntitiesByType(ModEntities.SLEEPER, new net.minecraft.util.math.Box(head).expand(8), e -> true).isEmpty()) {
            SleeperEntity s = ModEntities.SLEEPER.create(world);
            if (s == null) return;
            s.refreshPositionAndAngles(head.getX() + 0.5, head.getY() + 0.6875, head.getZ() + 0.5, 180f, 0f);
            world.spawnEntity(s);
            s.lieIn(head);
        }
    }

    private static long mix(long seed, int cx, int cz) {
        long h = seed * 31 + cx * 0x9E3779B97F4A7C15L + cz * 0xC2B2AE3D27D4EB4FL + 0x5DEECE66DL;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return h;
    }

    /** /itwastall debug events line. */
    public static String debugLine(ServerPlayerEntity player) {
        Dream d = DREAMERS.get(player.getUuid());
        long now = player.getServer() == null ? 0 : player.getServer().getOverworld().getTime();
        if (d != null) return "in outspace, " + Math.max(0, (d.endsAt() - now) / 20) + " s left";
        long night = player.getWorld().getTimeOfDay() / 24000L;
        return usedNight == night ? "someone dreamed tonight already" : "awake (stage " + ItWasTallConfig.INSTANCE.outspaceMinStage + "+ sleep, "
                + Math.round(ItWasTallConfig.INSTANCE.outspaceChance * 100) + " % once a night)";
    }
}
