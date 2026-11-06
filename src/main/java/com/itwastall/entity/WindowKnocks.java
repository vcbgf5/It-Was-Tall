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
            if (!EventGate.ready(player)) continue;
            if (!startKnock(player, level)) continue;
            EventGate.mark(player);
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
        ServerWorld world = player.getServerWorld();
        BlockPos dot = findWindow(player);
        if (dot == null) return false;

        // Two cases arrive from findWindow(): a mapped exterior dot (the red BaseMap marker,
        // already an OUTSIDE cell with the pane facing the player) or the unmapped geometric
        // fallback, which returns the glass itself. Resolve the knock target and the figure
        // spawn spot for both.
        BlockPos glass;
        BlockPos figureSpot;
        if (exteriorDotUsable(world, dot, player)) {
            glass = glassFacing(world, dot, player);
            figureSpot = dot.toImmutable();
        } else {
            glass = dot;                                        // fallback: the glass itself
            figureSpot = outsideCell(world, glass, player);     // may be null: sound-only knock
        }
        if (glass == null) return false;

        long now = player.getWorld().getTime();
        for (int i = 0; i < ABER_COUNT; i++) {
            SCHEDULED.add(new Knock(now + (long) i * KNOCK_GAP_TICKS, player.getUuid(), glass));
        }
        if (level >= 2 && figureSpot != null) {
            // ETAP 4 "+ cień/drzwi" (PHASES §3, wiersz WindowKnock): the figure stands exactly
            // on the red exterior dot, one cell off the glass. FALSE-presences route -> client
            // side fake entity, so it is never a real It (MODPLAN §7.4); the client renderer
            // aligns its head with the pane ("head perfect on the window height").
            if (!FalsePresences.sendFigure(player, figureSpot, 10 * 20, true)) {
                ItWasTall.LOGGER.debug("Window knock without figure (no open cell behind glass).");
            } else {
                ItWasTall.LOGGER.debug("Window figure -> {}", player.getName().getString());
            }
        }
        return true;
    }

    /** The pane between an exterior dot and the player (validated by exteriorDotUsable). */
    @Nullable
    private static BlockPos glassFacing(ServerWorld world, BlockPos dot, ServerPlayerEntity player) {
        double dx = player.getX() - (dot.getX() + 0.5);
        double dz = player.getZ() - (dot.getZ() + 0.5);
        if (Math.abs(dx) + Math.abs(dz) < 0.001) return null;
        Direction towardPlayer = Direction.getFacing((float) dx, 0.0f, (float) dz);
        BlockPos glass = dot.offset(towardPlayer);
        return isGlassy(world, glass) ? glass.toImmutable() : null;
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
        int perNight = switch (Director.stageIndex()) {     // stage 2-3: once a night, 4: twice, 5: three times
            case 4 -> 2;
            case 5 -> 3;
            default -> 1;
        };
        return ledger.night != nightIndex(world) || ledger.count < perNight;
    }

    private static long nightIndex(World world) {
        return world.getTimeOfDay() / 24000L;
    }

    /**
     * Finds a glass window near the player — first from the saved BaseMap (Poziom 6: what It
     * already mapped), falling back to the geometric search while the base is still unmapped.
     *
     * <p>SINCE the exterior-marker change, a mapped WINDOW marker IS the outside cell (the
     * "red dot" the scanner records beside every pane). That dot is the exact figure spawn
     * spot: revalidated here (still open, still glass behind it), and the knock sounds are
     * aimed at the glass next to it so they ring in the right place.
     */
    @Nullable
    private static BlockPos findWindow(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        // 1) the mapped base is the smarter source: exterior dots the scan actually recorded
        BlockPos dot = com.itwastall.base.BaseTracker.nearestMarker(world, player, com.itwastall.base.BaseMap.TYPE_WINDOW);
        if (dot != null && exteriorDotUsable(world, dot, player)) return dot;
        // 2) fallback heuristic (base not scanned yet): marker not available, knock sound only
        //    spot-level figure comes from outsideCell() in startKnock
        BlockPos base = player.getBlockPos();
        for (BlockPos pos : BlockPos.iterateOutwards(base, 14, 5, 14)) {
            if (!isGlassy(world, pos)) continue;
            if (outsideCell(world, pos, player) == null) continue;
            return pos.toImmutable();
        }
        return null;
    }

    /**
     * Revalidates a mapped exterior dot right before use: it must still be an open,
     * standable cell with air above AND a glass pane facing back toward the player's side
     * (the base may have changed since the scan; a dot with no glass left is worthless).
     *
     * @return true when the dot is usable; knocks are then aimed at the found pane
     */
    private static boolean exteriorDotUsable(ServerWorld world, BlockPos dot, ServerPlayerEntity player) {
        if (!world.isAir(dot) || !world.isAir(dot.up())) return false;
        // the pane has to sit between the dot and the player's side of the wall
        double dx = player.getX() - (dot.getX() + 0.5);
        double dz = player.getZ() - (dot.getZ() + 0.5);
        if (Math.abs(dx) + Math.abs(dz) < 0.001) return false;
        Direction towardPlayer = Direction.getFacing((float) dx, 0.0f, (float) dz);
        return isGlassy(world, dot.offset(towardPlayer));
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

    /** How many times this fired for the player in the current night ledger, for /itwastall debug events. */
    public static int ledgerCount(UUID player) {
        Ledger ledger = LEDGERS.get(player);
        return ledger == null ? 0 : ledger.count;
    }
}
