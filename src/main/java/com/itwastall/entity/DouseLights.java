package com.itwastall.entity;

import com.itwastall.ItWasTall;
import com.itwastall.base.BaseMap;
import com.itwastall.base.BaseTracker;
import com.itwastall.config.ItWasTallConfig;
import com.itwastall.director.Director;
import com.itwastall.director.Stage;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * DOUSE LIGHTS EVENT (MODPLAN §14 "gaszenie pochodni", the {@code DouseLightsEvent} the plan
 * proposes): inside the player's SHELTERED base at night, torches/lanterns/candles go out —
 * one after another, with an extinguished-hiss heard only by the haunted player. The flames
 * do not come back on their own; re-lighting is honest player work.
 *
 * <p>Gates (§14 checklist: conditions, cooldown, chance, frequency limit, multiplayer,
 * cancellation, data): PHASE ACTIVE, stage table {@code torchDouseLevel >= 4} (ETAP 4+
 * "base + mine dousing", mine-side lives in {@link MineEvents}), night, the player sheltered
 * INSIDE the mapped base, one burst per night per player, config chance. The douse list
 * comes from BaseTracker's {@code TYPE_LIGHT} markers — §8 feeds §14 the same way it feeds
 * the tasks. No scan of the whole world, per-player night ledgers only (server memory).
 */
public final class DouseLights {
    /** Blocks It can snuff (§14 "pochodnie" — the home-light family). */
    private static final List<Block> DOUSABLE = List.of(
            Blocks.TORCH, Blocks.WALL_TORCH, Blocks.SOUL_TORCH, Blocks.SOUL_WALL_TORCH,
            Blocks.LANTERN, Blocks.SOUL_LANTERN, Blocks.CANDLE);

    /** Per-player per-night ledger (like WindowKnocks): one burst per night. */
    private static final Map<UUID, Ledger> LEDGERS = new HashMap<>();

    private DouseLights() {
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(DouseLights::tick);
    }

    public static void forget(UUID player) {
        LEDGERS.remove(player);
    }

    private static void tick(MinecraftServer server) {
        if (server.getTicks() % 20 != 0) return;      // one decision tick per second
        ItWasTallConfig.Values cfg = ItWasTallConfig.INSTANCE;
        if (!cfg.baseDousing) return;
        if (Director.phase() == 0) return;            // PHASE PRE: total silence

        Stage st = Director.currentStage();           // live read: /itwastall stage next tick
        int level = st.torchDouseLevel();
        if (level < 4) return;                        // ETAP 2-3: mine dousing only (MineEvents)

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!eligible(player)) continue;
            Ledger ledger = LEDGERS.computeIfAbsent(player.getUuid(), u -> new Ledger());
            long night = nightIndex(player.getWorld());
            if (ledger.night != night) {
                ledger.night = night;
                ledger.count = 0;
            }
            if (ledger.count >= 1) continue;          // §14 frequency limit: one burst per night
            if (player.getRandom().nextDouble() >= clamp01(cfg.baseDouseChance)) continue;

            int doused = douse(player, st);
            if (doused > 0) {
                ledger.count++;
                ItWasTall.LOGGER.info("Base dousing for {}: {} light(s) went out.",
                        player.getName().getString(), doused);
            }
        }
    }

    private static boolean eligible(ServerPlayerEntity player) {
        if (player.isCreative() || player.isSpectator() || !player.isAlive()) return false;
        World world = player.getWorld();
        if (world.getRegistryKey() != World.OVERWORLD) return false;
        if (!world.isNight()) return false;
        // the event is a BASE event: It standing inside your walls snuffing your own lights
        return com.itwastall.entity.ShelterCheck.isSheltered(world, player.getBlockPos());
    }

    /**
     * Snuffs up to {@code level - 2} (ETAP 4 -> 2, ETAP 5 -> 3) of the mapped lights that
     * still burn, choosing the FARTEST-from-the-player first so the darkness creeps closer.
     *
     * @return how many lights actually went out
     */
    private static int douse(ServerPlayerEntity player, Stage st) {
        ServerWorld world = player.getServerWorld();
        BaseMap map = BaseTracker.eventMapOf(player);
        if (map == null) return 0;

        List<BlockPos> burning = new ArrayList<>(map.all(BaseMap.TYPE_LIGHT));
        burning.removeIf(pos -> !isDousable(world, pos));
        if (burning.isEmpty()) return 0;
        burning.sort((a, b) -> Double.compare(
                b.getSquaredDistance(player.getBlockPos()),
                a.getSquaredDistance(player.getBlockPos()))); // farthest first

        int budget = Math.max(1, st.torchDouseLevel() - 2);   // ETAP 4 -> 2, ETAP 5 -> 3
        Set<Long> done = new HashSet<>();
        for (BlockPos pos : burning) {
            if (done.size() >= budget) break;
            if (!done.add(pos.asLong())) continue;
            // re-check right before the block changes: someone may have re-packed the room
            if (!isDousable(world, pos)) continue;
            world.removeBlock(pos, false);            // silent removal, no drops
            world.spawnParticles(ParticleTypes.SMOKE,
                    pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5,
                    8, 0.1, 0.15, 0.1, 0.01);
            MineEvents.playTo(player, SoundEvents.BLOCK_FIRE_EXTINGUISH,
                    Vec3d.ofCenter(pos), 1.2f, 0.7f + player.getRandom().nextFloat() * 0.2f);
        }
        // PHASES §2 hook: felt dread, small but real
        if (!done.isEmpty() && Director.phase() == 1) Director.addTension(1.0);
        return done.size();
    }

    private static boolean isDousable(ServerWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return state.getBlock() != Blocks.AIR && DOUSABLE.contains(state.getBlock());
    }

    private static double clamp01(double chance) {
        return Math.max(0.0, Math.min(1.0, chance));
    }

    private static long nightIndex(World world) {
        return world.getTimeOfDay() / 24000L;
    }

    /** Small per-player reminder for the one-burst-per-night rule. */
    private static final class Ledger {
        long night = -1;
        int count;
    }

    /** How many times this fired for the player in the current night ledger, for /itwastall debug events. */
    public static int ledgerCount(UUID player) {
        Ledger ledger = LEDGERS.get(player);
        return ledger == null ? 0 : ledger.count;
    }
}
