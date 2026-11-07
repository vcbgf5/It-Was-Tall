package com.itwastall.entity;

import com.itwastall.ItWasTall;
import com.itwastall.base.BaseTracker;
import com.itwastall.config.ItWasTallConfig;
import com.itwastall.director.Director;
import com.itwastall.registry.ModBlocks;
import com.itwastall.registry.ModSounds;
import com.itwastall.world.ItWasTallState;
import com.itwastall.world.JournalLog;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FallingBlock;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * THE CORRUPTION. On the hunting stages a black stone with pulsing red veins appears near your base, and around
 * it the world starts to go wrong: blocks turn into a flickering broken texture, clods of earth hang in the air,
 * some blocks become something they should not be. Break the source — or wait for the dawn — and every block
 * goes back to what it was. All changes are kept in the world data, so a restart puts them back as well.
 */
public final class Corruption {
    private static final int RADIUS = 12, MAX_CHANGES = 120;
    private static final Block[] WRONG = {Blocks.NETHERRACK, Blocks.SOUL_SAND, Blocks.BONE_BLOCK, Blocks.RED_WOOL, Blocks.OBSIDIAN, Blocks.SCULK};

    private static final class Site {
        BlockPos source;
        BlockState sourceOriginal = Blocks.AIR.getDefaultState();
        boolean sawNight;
        final LinkedHashMap<BlockPos, BlockState> changed = new LinkedHashMap<>();
    }

    private static final List<Site> SITES = new ArrayList<>();
    private static final Map<UUID, Long> DECIDED_NIGHT = new HashMap<>();
    private static boolean loaded;

    private Corruption() {}

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(Corruption::tick);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
            SITES.clear();
            loaded = false;
        });
    }

    public static void forget(UUID player) {
        DECIDED_NIGHT.remove(player);
    }

    private static void tick(MinecraftServer server) {
        if (server.getTicks() % 20 != 0) return;
        ServerWorld world = server.getOverworld();
        if (!loaded) load(server, world);
        boolean dirty = false;
        for (Site site : List.copyOf(SITES)) {
            if (world.isNight()) site.sawNight = true;
            if (!world.isChunkLoaded(site.source.getX() >> 4, site.source.getZ() >> 4)) continue;
            if (!world.getBlockState(site.source).isOf(ModBlocks.CORRUPTION_SOURCE)) {   // someone broke the source
                restore(world, site, true);
                dirty = true;
                continue;
            }
            if (site.sawNight && world.isDay()) {                                       // the dawn mends it
                restore(world, site, false);
                dirty = true;
                continue;
            }
            if (server.getTicks() % 200 == 0 && site.changed.size() < MAX_CHANGES) {
                int n = 2 + world.getRandom().nextInt(3);
                for (int i = 0; i < n; i++) if (spread(world, site)) dirty = true;
            }
            if (server.getTicks() % 160 == 0) {
                world.playSound(null, site.source, ModSounds.AMBIENT_HUM, SoundCategory.HOSTILE, ModSounds.vol(0.7f), 0.6f);
            }
        }
        // new corruption: once in a while on the hunting stages, near the base of someone inside it at night
        ItWasTallConfig.Values cfg = ItWasTallConfig.INSTANCE;
        if (cfg.corruption && SITES.isEmpty() && world.isNight() && Director.phase() == 1 && Director.stageIndex() >= 4
                && server.getTicks() % 200 == 0) {
            long night = world.getTimeOfDay() / 24000L;
            for (ServerPlayerEntity p : world.getPlayers()) {
                if (p.isCreative() || p.isSpectator() || !BaseTracker.isInBase(p)) continue;
                if (Long.valueOf(night).equals(DECIDED_NIGHT.get(p.getUuid()))) continue;
                DECIDED_NIGHT.put(p.getUuid(), night);
                if (world.getRandom().nextDouble() >= cfg.corruptionChance || !EventGate.ready(p)) continue;
                Long anchor = BaseTracker.anchorOf(p.getUuid());
                if (anchor != null && startNear(world, BlockPos.fromLong(anchor), 14, 28)) {
                    EventGate.mark(p);
                    JournalLog.add(p, "Outside, the world started to look wrong. Something black was pulsing out there.");
                    dirty = true;
                    break;
                }
            }
        }
        if (dirty) save(server);
    }

    /** /itwastall event corruption: a source a few blocks in front of the player. */
    public static boolean startInFront(ServerPlayerEntity p) {
        BlockPos ahead = BlockPos.ofFloored(p.getPos().add(p.getRotationVec(1f).multiply(1, 0, 1).normalize().multiply(8)));
        boolean ok = startNear(p.getServerWorld(), ahead, 0, 3);
        if (ok) save(p.getServer());
        return ok;
    }

    private static boolean startNear(ServerWorld world, BlockPos center, int min, int max) {
        if (world.getRegistryKey() != net.minecraft.world.World.OVERWORLD) return false;
        Random r = world.getRandom();
        for (int attempt = 0; attempt < 30; attempt++) {
            double a = r.nextDouble() * Math.PI * 2, d = min + r.nextDouble() * Math.max(1, max - min);
            int x = MathHelper.floor(center.getX() + Math.cos(a) * d), z = MathHelper.floor(center.getZ() + Math.sin(a) * d);
            if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
            BlockPos at = new BlockPos(x, world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            BlockState below = world.getBlockState(at.down());
            if (!below.getFluidState().isEmpty() || below.getCollisionShape(world, at.down()).isEmpty()) continue;
            Site site = new Site();
            site.source = at;
            site.sourceOriginal = world.getBlockState(at);
            site.sawNight = world.isNight();
            world.setBlockState(at, ModBlocks.CORRUPTION_SOURCE.getDefaultState(), Block.NOTIFY_ALL);
            SITES.add(site);
            for (int i = 0; i < 6; i++) spread(world, site);
            world.playSound(null, at, SoundEvents.BLOCK_SCULK_CATALYST_BLOOM, SoundCategory.HOSTILE, 1.0f, 0.5f);
            ItWasTall.LOGGER.info("Corruption started at {}.", at.toShortString());
            return true;
        }
        return false;
    }

    private static boolean spread(ServerWorld world, Site site) {
        Random r = world.getRandom();
        int x = site.source.getX() + r.nextInt(RADIUS * 2 + 1) - RADIUS, z = site.source.getZ() + r.nextInt(RADIUS * 2 + 1) - RADIUS;
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return false;
        BlockPos pos = new BlockPos(x, world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
        if (pos.equals(site.source) || site.changed.containsKey(pos)) return false;
        BlockState st = world.getBlockState(pos);
        float hardness = st.getHardness(world, pos);
        if (st.isAir() || !st.getFluidState().isEmpty() || st.hasBlockEntity() || st.getBlock() instanceof DoorBlock
                || hardness < 0 || hardness >= 50 || st.isOf(ModBlocks.GLITCH) || st.isOf(ModBlocks.CORRUPTION_SOURCE)) return false;
        int roll = r.nextInt(100);
        if (roll < 20 && st.isFullCube(world, pos) && !(st.getBlock() instanceof FallingBlock)) {
            // a clod of earth hanging in the air above
            BlockPos up = pos.up(3 + r.nextInt(3));
            if (!world.getBlockState(up).isAir() || site.changed.containsKey(up)) return false;
            site.changed.put(up, world.getBlockState(up));
            world.setBlockState(up, st, Block.NOTIFY_ALL);
        } else if (roll < 35) {
            site.changed.put(pos, st);
            world.setBlockState(pos, WRONG[r.nextInt(WRONG.length)].getDefaultState(), Block.NOTIFY_ALL);
        } else {
            site.changed.put(pos, st);
            world.setBlockState(pos, ModBlocks.GLITCH.getDefaultState(), Block.NOTIFY_ALL);
        }
        return true;
    }

    private static void restore(ServerWorld world, Site site, boolean broken) {
        List<Map.Entry<BlockPos, BlockState>> entries = new ArrayList<>(site.changed.entrySet());
        for (int i = entries.size() - 1; i >= 0; i--) {
            world.setBlockState(entries.get(i).getKey(), entries.get(i).getValue(), Block.NOTIFY_ALL);
        }
        if (world.getBlockState(site.source).isOf(ModBlocks.CORRUPTION_SOURCE) || world.getBlockState(site.source).isAir()) {
            world.setBlockState(site.source, site.sourceOriginal, Block.NOTIFY_ALL);
        }
        SITES.remove(site);
        world.playSound(null, site.source, SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.BLOCKS, 1.0f, 0.6f);
        for (ServerPlayerEntity p : world.getPlayers()) {
            if (p.getBlockPos().isWithinDistance(site.source, 64)) {
                p.sendMessage(Text.literal(broken ? "The world mends itself." : "With the light, everything is as it was.")
                        .formatted(Formatting.GRAY, Formatting.ITALIC), true);
            }
        }
        ItWasTall.LOGGER.info("Corruption at {} mended ({} blocks).", site.source.toShortString(), entries.size());
    }

    // ---------------------------------------------------------------- persistence

    private static void save(MinecraftServer server) {
        NbtCompound root = new NbtCompound();
        NbtList list = new NbtList();
        for (Site s : SITES) {
            NbtCompound c = new NbtCompound();
            c.putLong("src", s.source.asLong());
            c.put("srcState", NbtHelper.fromBlockState(s.sourceOriginal));
            c.putBoolean("sawNight", s.sawNight);
            NbtList changes = new NbtList();
            for (Map.Entry<BlockPos, BlockState> e : s.changed.entrySet()) {
                NbtCompound ch = new NbtCompound();
                ch.putLong("p", e.getKey().asLong());
                ch.put("s", NbtHelper.fromBlockState(e.getValue()));
                changes.add(ch);
            }
            c.put("changes", changes);
            list.add(c);
        }
        root.put("sites", list);
        ItWasTallState.getOrCreate(server).setCorruption(root);
    }

    private static void load(MinecraftServer server, ServerWorld world) {
        loaded = true;
        SITES.clear();
        NbtCompound root = ItWasTallState.getOrCreate(server).corruption();
        var blocks = Registries.BLOCK.getReadOnlyWrapper();
        NbtList list = root.getList("sites", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound c = list.getCompound(i);
            Site s = new Site();
            s.source = BlockPos.fromLong(c.getLong("src"));
            s.sourceOriginal = NbtHelper.toBlockState(blocks, c.getCompound("srcState"));
            s.sawNight = c.getBoolean("sawNight");
            NbtList changes = c.getList("changes", NbtElement.COMPOUND_TYPE);
            for (int k = 0; k < changes.size(); k++) {
                NbtCompound ch = changes.getCompound(k);
                s.changed.put(BlockPos.fromLong(ch.getLong("p")), NbtHelper.toBlockState(blocks, ch.getCompound("s")));
            }
            SITES.add(s);
        }
    }

    @Nullable
    public static String debugLine() {
        return SITES.isEmpty() ? "none" : SITES.size() + " site(s), " + SITES.get(0).changed.size() + " blocks at " + SITES.get(0).source.toShortString();
    }
}
