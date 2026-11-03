package com.itwastall.base;

import com.itwastall.ItWasTall;
import com.itwastall.config.ItWasTallConfig;
import com.itwastall.world.ItWasTallState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Detects and keeps track of the player's base (MODPLAN §8.1). The anchor is the bed the
 * player right-clicks into (the base's heart); every rescan fills {@link BaseMap} with
 * windows / doors / lights / chests around it. When there is no bed yet, standing sheltered
 * for ten calm seconds at night marks the spot instead — the fear is free, the mapping is a bonus.
 *
 * <p>Budgeted: rescans flow through {@link BaseScanner}, sharing {@code scanBlocksPerTick}
 * blocks per tick fairly between all pending scans. Block use near the anchor re-queues a
 * scan with a 15 s debounce (MODPLAN §8.4: update on changes, never rescan rashly).
 *
 * <p>Persistence: the finished map rides inside {@link ItWasTallState} (one saving system,
 * MODPLAN §2) — scans survive restarts, and the map comes back when next asked for.
 */
public final class BaseTracker {
    private static final Map<UUID, Long> ANCHORS = new HashMap<>();
    private static final Map<UUID, BaseMap> MAPS = new HashMap<>();
    private static final Map<UUID, BaseScanner> SCANS = new HashMap<>();
    private static final Map<UUID, Long> RESCAN_AT = new HashMap<>();
    /** In-shelter calm seconds, only for players with no bed anchor yet. */
    private static final Map<UUID, Integer> SHELTER_SECONDS = new HashMap<>();

    @Nullable
    private static MinecraftServer server;

    private BaseTracker() {
    }

    public static void init() {
        ServerLifecycleEvents.SERVER_STARTING.register(starting -> server = starting);
        ServerTickEvents.END_SERVER_TICK.register(BaseTracker::tick);
        UseBlockCallback.EVENT.register(BaseTracker::onUseBlock);
    }

    public static void forget(UUID player) {
        ANCHORS.remove(player);
        MAPS.remove(player);
        SCANS.remove(player);
        RESCAN_AT.remove(player);
        SHELTER_SECONDS.remove(player);
    }

    // ---------------------------------------------------------------- hooks ----

    /** The bed click sets the anchor immediately — cheap, reliable, exactly what it looks like. */
    private static ActionResult onUseBlock(PlayerEntity player, World world, Hand hand, BlockHitResult hit) {
        if (world.isClient || !(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
        if (world.getRegistryKey() != World.OVERWORLD) return ActionResult.PASS;
        BlockPos pos = hit.getBlockPos();
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof BedBlock) {
            setAnchor(sp, pos.toImmutable());
            return ActionResult.PASS;
        }

        // A roof or four complete walls are NOT required to recognise a base.
        // Interacting with a useful base block is an independent anchor clue:
        // chests, workstations, furnaces, doors, glass and placed light sources.
        Long knownAnchor = ANCHORS.get(sp.getUuid());
        if (knownAnchor == null && isBaseClue(state)) {
            setAnchor(sp, pos.toImmutable());
            return ActionResult.PASS;
        }

        // any other block interaction near a known anchor = the base changed -> debounced rescan
        Long anchor = knownAnchor;
        if (anchor != null && pos.isWithinDistance(BlockPos.fromLong(anchor), 48)) {
            scheduleScan(sp, anchor, false);
        }
        return ActionResult.PASS;
    }

    /** Blocks commonly used as base infrastructure; deliberately independent of roof/wall checks. */
    private static boolean isBaseClue(BlockState state) {
        var block = state.getBlock();
        return block instanceof net.minecraft.block.ChestBlock
                || block instanceof net.minecraft.block.BarrelBlock
                || block instanceof net.minecraft.block.AbstractFurnaceBlock
                || block instanceof net.minecraft.block.CraftingTableBlock
                || block instanceof net.minecraft.block.DoorBlock
                || BaseMap.isGlassy(state)
                || state.getLuminance() > 0;
    }

    private static void tick(MinecraftServer tickingServer) {
        // calm-shelter anchor inference for players whose base is still unknown
        boolean oncePerSecond = tickingServer.getTicks() % 20 == 0;
        for (ServerPlayerEntity player : tickingServer.getPlayerManager().getPlayerList()) {
            if (!oncePerSecond) break;
            UUID uuid = player.getUuid();
            if (ANCHORS.containsKey(uuid)) continue;
            if (player.isCreative() || player.isSpectator() || !player.isAlive()) continue;
            World world = player.getWorld();
            if (world.getRegistryKey() != World.OVERWORLD) continue;
            if (!world.isNight()
                    || !com.itwastall.entity.ShelterCheck.isSheltered(world, player.getBlockPos())) {
                SHELTER_SECONDS.remove(uuid); // leaving resets the calm count
                continue;
            }
            int calm = SHELTER_SECONDS.merge(uuid, 1, Integer::sum);
            if (calm >= 10) {
                setAnchor(player, player.getBlockPos().toImmutable());
                SHELTER_SECONDS.remove(uuid);
            }
        }

        runScans(tickingServer);
    }

    // ------------------------------------------------------------ public API ----

    /** Anchor (bed / calm-shelter spot) of that player; null while unknown. */
    @Nullable
    public static Long anchorOf(UUID uuid) {
        Long known = ANCHORS.get(uuid);
        if (known != null) return known;
        BaseMap restored = fromState(uuid);
        if (restored == null) return null;
        ANCHORS.put(uuid, restored.anchor());
        MAPS.put(uuid, restored);
        return restored.anchor();
    }

    /**
     * Live map of that player's base. Rebuilt from {@link ItWasTallState} on first touch
     * (also the cheap path after a restart).
     */
    @Nullable
    public static BaseMap mapOf(UUID uuid) {
        BaseMap live = MAPS.get(uuid);
        if (live != null) return live;
        BaseMap restored = fromState(uuid);
        if (restored == null) return null;
        MAPS.put(uuid, restored);
        return restored;
    }

    /** Marker closest to the player (e.g. HANDY WINDOW for the knock event). */
    @Nullable
    public static BlockPos nearestMarker(ServerWorld world, ServerPlayerEntity player, int type) {
        BaseMap map = mapOf(player.getUuid());
        if (map == null) return null;
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos marker : map.all(type)) {
            double dist = marker.getSquaredDistance(player.getBlockPos());
            if (dist < bestDist && marker.isWithinDistance(player.getBlockPos(), 48)) {
                bestDist = dist;
                best = marker;
            }
        }
        return best;
    }

    /** Force a rescan (command / debug). False when no anchor is known yet. */
    public static boolean triggerRescan(ServerPlayerEntity player) {
        Long anchor = anchorOf(player.getUuid());
        if (anchor == null) return false;
        BaseScanner scanner = SCANS.computeIfAbsent(player.getUuid(),
                u -> new BaseScanner(ItWasTallConfig.INSTANCE.baseScanRadius));
        if (!scanner.active()) scanner.start(anchor);
        return true;
    }

    /** For the command readout: the live (or remembered) anchor as a short string. */
    @Nullable
    public static String anchorString(UUID uuid) {
        Long anchor = anchorOf(uuid);
        return anchor == null ? null : BlockPos.fromLong(anchor).toShortString();
    }

    /** Work still queued for that player's scan (debug readout). */
    public static int pendingWork(UUID uuid) {
        BaseScanner scan = SCANS.get(uuid);
        return scan == null ? 0 : scan.queuedColumns() * (BaseScanner.ROWS_TOTAL);
    }

    /** All markers a base has per type, as a tiny text card (debug readout). */
    public static String summary(UUID uuid) {
        BaseMap map = anchorOf(uuid) == null ? null : mapOf(uuid);
        if (map == null) return "no base mapped yet";
        return "anchor " + BlockPos.fromLong(map.anchor()).toShortString()
                + ", markers " + map.count()
                + " (windows " + map.count(BaseMap.TYPE_WINDOW)
                + ", doors " + map.count(BaseMap.TYPE_DOOR)
                + ", lights " + map.count(BaseMap.TYPE_LIGHT)
                + ", chests " + map.count(BaseMap.TYPE_CHEST) + ")";
    }

    /** Sparse debug list: up to 6 markers per type, world-space. */
    public static List<String> markerSample(UUID uuid) {
        List<String> lines = new ArrayList<>();
        BaseMap map = mapOf(uuid);
        if (map == null) return lines;
        lines.add("windows: " + sample(map, BaseMap.TYPE_WINDOW));
        lines.add("doors:   " + sample(map, BaseMap.TYPE_DOOR));
        lines.add("lights:  " + sample(map, BaseMap.TYPE_LIGHT));
        lines.add("chests:  " + sample(map, BaseMap.TYPE_CHEST));
        lines.add("rooms:   " + map.count(BaseMap.TYPE_ROOM)
                + " interior cells, dark corners " + map.count(BaseMap.TYPE_CORNER));
        return lines;
    }

    private static String sample(BaseMap map, int type) {
        List<BlockPos> all = map.all(type);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(6, all.size()); i++) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(all.get(i).toShortString());
        }
        if (all.size() > 6) sb.append(" +").append(all.size() - 6).append(" more");
        if (all.isEmpty()) sb.append('-');
        return sb.toString();
    }

    // ------------------------------------------------------------ internals ----

    private static void setAnchor(ServerPlayerEntity player, BlockPos anchorPos) {
        UUID uuid = player.getUuid();
        long anchor = anchorPos.asLong();
        Long previous = ANCHORS.put(uuid, anchor);
        BaseMap map = MAPS.computeIfAbsent(uuid, u -> new BaseMap(anchor));
        map.setAnchor(anchor);
        map.clearMarkers();                 // old markers are offsets around the old anchor
        scheduleScan(player, anchor, true);
        if (!Long.valueOf(anchor).equals(previous)) {
            persist(player, map);
            ItWasTall.LOGGER.info("Base anchor set for {} at {}.",
                    player.getName().getString(), anchorPos.toShortString());
        }
    }

    /** Queues a scan; {@code force} skips the change-debounce (commands / new anchors). */
    private static void scheduleScan(ServerPlayerEntity player, long anchor, boolean force) {
        UUID uuid = player.getUuid();
        long now = player.getWorld().getTime();
        Long rescanAt = RESCAN_AT.get(uuid);
        if (!force && rescanAt != null && now < rescanAt) return;
        RESCAN_AT.put(uuid, now + 15 * 20L);
        BaseScanner scanner = SCANS.computeIfAbsent(uuid,
                u -> new BaseScanner(ItWasTallConfig.INSTANCE.baseScanRadius));
        if (!scanner.active()) scanner.start(anchor);
    }

    private static void runScans(MinecraftServer runningServer) {
        if (SCANS.isEmpty()) return;
        int budgetLeft = Math.max(8, ItWasTallConfig.INSTANCE.scanBlocksPerTick);
        int activeScans = 0;
        for (BaseScanner scan : SCANS.values()) if (scan.active()) activeScans++;
        if (activeScans == 0) return;

        List<UUID> uuids = new ArrayList<>(SCANS.keySet());
        for (UUID uuid : uuids) {
            BaseScanner scanner = SCANS.get(uuid);
            if (!scanner.active()) continue;
            ServerPlayerEntity player = runningServer.getPlayerManager().getPlayer(uuid);
            Long anchor = ANCHORS.get(uuid);
            if (player == null || anchor == null) continue; // owner away: the scan waits for him
            int myBudget = Math.max(4, budgetLeft / activeScans);
            boolean done = scanner.tick(player.getServerWorld(),
                    BlockPos.fromLong(anchor), MAPS.computeIfAbsent(uuid, u -> new BaseMap(anchor)), myBudget);
            if (done) {
                persist(player, MAPS.get(uuid));
                ItWasTall.LOGGER.info("Base scan finished for {} ({}).",
                        player.getName().getString(), MAPS.get(uuid));
                SCANS.remove(uuid);
            }
        }
    }

    private static void persist(ServerPlayerEntity player, BaseMap map) {
        if (server == null) return;
        ItWasTallState.getOrCreate(server).putPlayerBase(player.getUuid(), map.toNbt());
    }

    @Nullable
    private static BaseMap fromState(UUID uuid) {
        if (server == null) return null;
        NbtCompound nbt = ItWasTallState.getOrCreate(server).getPlayerBase(uuid);
        return nbt == null ? null : BaseMap.fromNbt(nbt);
    }
}
