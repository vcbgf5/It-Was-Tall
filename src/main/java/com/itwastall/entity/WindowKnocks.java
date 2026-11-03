package com.itwastall.entity;

import com.itwastall.ItWasTall;
import com.itwastall.config.ItWasTallConfig;
import com.itwastall.director.Director;
import com.itwastall.director.Stage;
import com.itwastall.net.ModNetworking;
import com.itwastall.registry.ModSounds;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * WINDOW KNOCK (MODPLAN §12, PHASES Poziom 5): three knocks on a window of the player's base.
 *
 * <p>Warunki (PHASES): przede wszystkim noc, gracz w bazie, jest okno (glass in the base),
 * spełnione warunki etapu. Etap table ({@code windowKnockLevel}):
 * 0 = none, <b>1 = sound only</b> (ETAP 2-3, MODPLAN "wersja dźwiękowa"), <b>2 = sound + figure</b>
 * behind the glass (ETAP 4), 3 = spare for the stage-5 glass breaking (handled while chasing)
 * "rozbicie szyby" — the real breaking lives in TallOneEntity.breakGlassTowards.
 *
 * <p>Max 1 knock burst per night per player (PHASES ETAP 2), 35% chance. The knocks are
 * heard only by the haunted player — other players on the server hear nothing.
 */
public final class WindowKnocks {
    /** Seconds between two knock bursts (three knocks, ABER_COUNT=3). */
    private static final int ABER_COUNT = 3;
    private static final int KNOCK_GAP_TICKS = 10;

    /** Per-player ledger: one knock burst per night (server memory, like the other directors). */
    private static final Map<UUID, Ledger> LEDGERS = new HashMap<>();

    /** Scheduled knock sounds: knock tick + window pos + target player. */
    private record Knock(long tick, UUID player, BlockPos pos) {
    }

    private static final List<Knock> SCHEDULED = new ArrayList<>();

    private WindowKnocks() {
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(WindowKnocks::tick);
    }

    public static void forget(UUID player) {
        LEDGERS.remove(player);
        SCHEDULED.removeIf(k -> k.player.equals(player));
    }

    private static void tick(MinecraftServer server) {
        resolveScheduled(server);

        if (server.getTicks() % 20 != 0) return;
        ItWasTallConfig.Values cfg = ItWasTallConfig.INSTANCE;
        if (!cfg.windowKnocks) return;
        if (Director.phase() == 0) return; // PHASE PRE: total silence

        Stage st = Director.currentStage(); // live read (PHASES §0)
        int level = st.windowKnockLevel();
        if (level <= 0) return;             // ETAP 0-1: it never knocks yet

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!eligible(player)) continue;
            Random random = player.getRandom();
            if (random.nextDouble() >= MathHelper.clamp(cfg.windowKnockChance, 0.0, 1.0)) continue;
            if (!startKnock(player, level)) continue;
            Ledger ledger = LEDGERS.computeIfAbsent(player.getUuid(), u -> new Ledger());
            ledger.night = nightIndex(player.getWorld());
            ledger.count++;
        }
    }

    /** Public test trigger (/itwastall event knock): ignores the per-night ledger and chance. */
    public static boolean trigger(ServerPlayerEntity player) {
        Stage st = Director.currentStage();
        return startKnock(player, Math.max(1, st.windowKnockLevel()));
    }

    /** Decides the window, schedules 3 knocks and (level >= 2) places the figure behind the glass. */
    private static boolean startKnock(ServerPlayerEntity player, int level) {
        BlockPos window = findWindow(player);
        if (window == null) return false;
        long now = player.getWorld().getTime();
        for (int i = 0; i < ABER_COUNT; i++) {
            SCHEDULED.add(new Knock(now + (long) i * KNOCK_GAP_TICKS, player.getUuid(), window));
        }
        if (level >= 2) {
            // ETAP 4 "+ cień/drzwi" (PHASES §3, wiersz WindowKnock): the figure stands one cell
            // off the glass, facing in. Routed through FalsePresences -> client-side fake entity,
            // so it is never a real It (MODPLAN §7.4).
            BlockPos outside = outsideCell(player.getServerWorld(), window, player);
            if (outside == null || !FalsePresences.sendFigure(player, outside, 10 * 20, true)) {
                // no outside room for a figure: the sound still played, which is fine ("pukanie w
                // szybę bez widocznej postaci" is a legit stage-2 variant in MODPLAN §12)
                ItWasTall.LOGGER.debug("Window knock without figure (no open cell behind glass).");
            } else {
                ItWasTall.LOGGER.debug("Window figure -> {}", player.getName().getString());
            }
        }
        return true;
    }

    /** Fires the three scheduled knocks exactly on time (works while the player stands in the base). */
    private static void resolveScheduled(MinecraftServer server) {
        if (SCHEDULED.isEmpty()) return;
        long now = server.getOverworld().getTime();
        List<Knock> due = new ArrayList<>();
        for (Knock k : SCHEDULED) {
            if (k.tick <= now) {
                due.add(k);
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(k.player);
                if (p != null) knock(p, k.pos);
            }
        }
        SCHEDULED.removeAll(due);
    }

    private static boolean eligible(ServerPlayerEntity player) {
        if (player.isCreative() || player.isSpectator() || !player.isAlive()) return false;
        World world = player.getWorld();
        if (world.getRegistryKey() != World.OVERWORLD) return false;
        if (!world.isNight()) return false; // "przede wszystkim noc"
        if (!ShelterCheck.isSheltered(world, player.getBlockPos())) return false; // gracz w bazie
        Ledger ledger = LEDGERS.get(player.getUuid());
        if (ledger == null) return true;
        return ledger.night != nightIndex(world) || ledger.count == 0; // 0 knocks tonight
    }

    private static long nightIndex(World world) {
        return world.getTimeOfDay() / 24000L;
    }

    /**
     * Finds a glass window near the player — first from the saved BaseMap (Poziom 6: what It
     * already mapped), falling back to the geometric search while the base is still unmapped.
     * Both require a passable outside cell ("on the street side") so the knock has a real place.
     */
    @Nullable
    private static BlockPos findWindow(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        // 1) the mapped base is the smarter source: windows the scan actually saw
        BlockPos mapped = com.itwastall.base.BaseTracker.nearestMarker(world, player, com.itwastall.base.BaseMap.TYPE_WINDOW);
        if (mapped != null && outsideCell(world, mapped, player) != null) return mapped;
        // 2) fallback heuristic (base not scanned yet)
        BlockPos base = player.getBlockPos();
        for (BlockPos pos : BlockPos.iterateOutwards(base, 14, 5, 14)) {
            if (!isGlassy(world, pos)) continue;
            if (outsideCell(world, pos, player) == null) continue;
            return pos.toImmutable();
        }
        return null;
    }

    /**
     * Finds the outside face relative to the player. The previous implementation selected
     * the first air block in Direction.values(), which could be above/below the glass or
     * even on the room side. The silhouette must stand directly outside the window.
     */
    @Nullable
    private static BlockPos outsideCell(ServerWorld world, BlockPos glass, ServerPlayerEntity player) {
        double dx = player.getX() - (glass.getX() + 0.5);
        double dz = player.getZ() - (glass.getZ() + 0.5);
        if (Math.abs(dx) + Math.abs(dz) < 0.001) return null;

        Direction towardPlayer = Direction.getFacing((float) dx, 0.0f, (float) dz);
        BlockPos side = glass.offset(towardPlayer.getOpposite());
        if (!world.isAir(side) || !world.isAir(side.up())) return null;
        return side.toImmutable();
    }

    private static boolean isGlassy(ServerWorld world, BlockPos pos) {
        BlockState st = world.getBlockState(pos);
        if (st.isOf(Blocks.GLASS) || st.isOf(Blocks.TINTED_GLASS)) return true;
        if (st.isIn(BlockTags.IMPERMEABLE)) return !st.isOf(Blocks.TINTED_GLASS);
        return st.getBlock() instanceof net.minecraft.block.StainedGlassBlock
                || st.getBlock() instanceof net.minecraft.block.PaneBlock;
    }

    /** One knock: audible only to the haunted player (it is HIS window). */
    private static void knock(ServerPlayerEntity player, BlockPos window) {
        MineEvents.playTo(player, ModSounds.KNOCK,
                new Vec3d(window.getX() + 0.5, window.getY() + 0.5, window.getZ() + 0.5),
                1.0f, KNOCK_PITCH + player.getRandom().nextFloat() * 0.1f);
    }

    private static final float KNOCK_PITCH = 0.9f;

    /** Small per-player reminder of how many knock ledgers this player had tonight. */
    private static final class Ledger {
        long night = -1;
        int count;
    }
}
