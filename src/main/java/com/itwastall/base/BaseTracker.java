package com.itwastall.base;

import com.itwastall.ItWasTall;
import com.itwastall.config.ItWasTallConfig;
import com.itwastall.world.ItWasTallState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
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
        // Restore and refresh a known base as soon as its owner joins the Overworld.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, joinedServer) -> {
            ServerPlayerEntity player = handler.getPlayer();
            if (player.getServerWorld() == joinedServer.getOverworld()) {
                Long anchor = anchorOf(player.getUuid());
                if (anchor != null) triggerRescan(player);
            }
        });
    }

    public static void forget(UUID player) {
        ANCHORS.remove(player);
        MAPS.remove(player);
        SCANS.remove(player);
        RESCAN_AT.remove(player);
        SHELTER_SECONDS.remove(player);
    }

    // ---------------------------------------------------------------- hooks ----

    /**
     * Called after a successful block placement. This history starts immediately and is
     * deliberately independent of Director phase, stage, and tension.
     */
    public static void onBlockPlaced(ServerPlayerEntity player, World world, BlockPos placedPos) {
        if (world.isClient || world.getRegistryKey() != World.OVERWORLD || server == null) return;
        BlockPos pos = placedPos.toImmutable();
        ItWasTallState state = ItWasTallState.getOrCreate(server);
        if (!state.recordPlacedBlock(player.getUuid(), pos.asLong())) return;

        Long anchor = anchorOf(player.getUuid());
        if (anchor == null && isBaseClue(world.getBlockState(pos))) {
            setAnchor(player, pos);
            return;
        }

        if (anchor != null) {
            BaseMap map = MAPS.computeIfAbsent(player.getUuid(), ignored -> new BaseMap(anchor));
            map.add(BlockPos.fromLong(anchor), BaseMap.TYPE_PLACED, pos);
            if (pos.isWithinDistance(BlockPos.fromLong(anchor), 48)) scheduleScan(player, anchor, false);
            persist(player, map);
        }
    }

    /** The bed click sets the anchor immediately — cheap, reliable, exactly what it looks like. */
    private static ActionResult onUseBlock(PlayerEntity player, World world, Hand hand, BlockHitResult hit) {
        if (world.isClient || !(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
        if (world.getRegistryKey() != World.OVERWORLD) return ActionResult.PASS;
        BlockPos pos = hit.getBlockPos();
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof BedBlock) {
            setAnchor(sp, pos.toImmutable());
            if (server != null) ItWasTallState.getOrCreate(server).setBedAnchored(sp.getUuid(), true);
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

        // chunk scores (MODPLAN §8 notes): time spent per chunk every 20 s, cluster check every minute
        if (tickingServer.getTicks() % 400 == 0) {
            ItWasTallState state = ItWasTallState.getOrCreate(tickingServer);
            for (ServerPlayerEntity player : tickingServer.getPlayerManager().getPlayerList()) {
                if (player.isSpectator() || !player.isAlive() || player.getWorld().getRegistryKey() != World.OVERWORLD) continue;
                boolean calmNight = player.getWorld().isNight()
                        && com.itwastall.entity.ShelterCheck.isSheltered(player.getWorld(), player.getBlockPos());
                ChunkScores.addDwell(state, player.getUuid(), player.getBlockPos(), calmNight);
            }
        }
        if (tickingServer.getTicks() % 1200 == 600) {
            for (ServerPlayerEntity player : tickingServer.getPlayerManager().getPlayerList()) {
                if (player.getWorld().getRegistryKey() == World.OVERWORLD) evaluateClusters(player);
            }
        }

        runScans(tickingServer);
    }

    /**
     * No bed? The strongest cluster of chunks (blocks placed + time spent) becomes the base once it is
     * strong enough; a non-bed base moves when another cluster becomes clearly stronger (they moved house).
     */
    private static void evaluateClusters(ServerPlayerEntity player) {
        if (server == null) return;
        ItWasTallState state = ItWasTallState.getOrCreate(server);
        UUID uuid = player.getUuid();
        if (state.isBedAnchored(uuid)) return;          // a bed always wins
        List<ChunkScores.Cluster> clusters = ChunkScores.clusters(player.getServerWorld(), state, uuid);
        if (clusters.isEmpty()) return;
        ChunkScores.Cluster best = clusters.get(0);
        if (best.score() < ChunkScores.BASE_SCORE) return;
        Long anchor = anchorOf(uuid);
        if (anchor == null) {
            setAnchor(player, best.center());
            ItWasTall.LOGGER.info("Base of {} found from chunk scores ({} chunks, score {}).",
                    player.getName().getString(), best.chunks().size(), Math.round(best.score()));
            return;
        }
        BlockPos current = BlockPos.fromLong(anchor);
        if (best.contains(current)) return;
        double currentScore = 0;
        for (ChunkScores.Cluster c : clusters) if (c.contains(current)) currentScore = c.score();
        if (best.score() >= Math.max(ChunkScores.BASE_SCORE * 2, currentScore * 2.0)) {
            setAnchor(player, best.center());
            ItWasTall.LOGGER.info("Base of {} moved to a stronger cluster at {}.", player.getName().getString(),
                    best.center().toShortString());
        }
    }

    /** Debug lines: the strongest clusters of that player. */
    public static List<String> clusterSummary(ServerPlayerEntity player) {
        List<String> lines = new ArrayList<>();
        if (server == null) return lines;
        ItWasTallState state = ItWasTallState.getOrCreate(server);
        List<ChunkScores.Cluster> clusters = ChunkScores.clusters(player.getServerWorld(), state, player.getUuid());
        lines.add("chunk clusters: " + clusters.size() + (state.isBedAnchored(player.getUuid()) ? " (bed anchor wins)" : "")
                + ", base from score " + Math.round(ChunkScores.BASE_SCORE));
        for (int i = 0; i < Math.min(3, clusters.size()); i++) {
            ChunkScores.Cluster c = clusters.get(i);
            lines.add("  #" + (i + 1) + " score " + Math.round(c.score()) + ", " + c.chunks().size() + " chunks, centre " + c.center().toShortString());
        }
        return lines;
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
        if (!scanner.active()) {
            BaseMap map = MAPS.computeIfAbsent(player.getUuid(), u -> new BaseMap(anchor));
            map.clearScanMarkers();
            restorePlacedMarkers(player.getUuid(), map);
            scanner.start(anchor);
        }
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
                + " (placed " + map.count(BaseMap.TYPE_PLACED)
                + ", windows " + map.count(BaseMap.TYPE_WINDOW)
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
        lines.add("placed:  " + sample(map, BaseMap.TYPE_PLACED));
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
        map.clearMarkers();                 // old offsets used the previous anchor
        // Restore every earlier player placement relative to this anchor, even if it predates
        // the bed, the scan, or activation of the mod's phase/stage system.
        if (server != null) {
            for (long packed : ItWasTallState.getOrCreate(server).placedBlocks(uuid)) {
                map.add(anchorPos, BaseMap.TYPE_PLACED, BlockPos.fromLong(packed));
            }
        }
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
        if (force || !scanner.active()) {
            BaseMap map = MAPS.computeIfAbsent(uuid, u -> new BaseMap(anchor));
            map.clearScanMarkers();
            restorePlacedMarkers(uuid, map);
            scanner.start(anchor);
        }
    }

    private static void runScans(MinecraftServer runningServer) {
        if (SCANS.isEmpty()) return;
        int budgetLeft = Math.max(8, ItWasTallConfig.INSTANCE.scanBlocksPerTick);
        List<UUID> uuids = new ArrayList<>(SCANS.keySet());
        int activeScans = 0;
        for (UUID uuid : uuids) {
            BaseScanner scan = SCANS.get(uuid);
            ServerPlayerEntity player = runningServer.getPlayerManager().getPlayer(uuid);
            if (scan != null && scan.active() && player != null
                    && player.getServerWorld() == runningServer.getOverworld()) activeScans++;
        }
        if (activeScans == 0) return;

        for (UUID uuid : uuids) {
            BaseScanner scanner = SCANS.get(uuid);
            if (scanner == null || !scanner.active()) continue;
            ServerPlayerEntity player = runningServer.getPlayerManager().getPlayer(uuid);
            Long anchor = ANCHORS.get(uuid);
            // Anchors belong to the Overworld; never scan the same coordinates in another dimension.
            if (player == null || anchor == null
                    || player.getServerWorld() != runningServer.getOverworld()) continue;
            int myBudget = Math.max(1, budgetLeft / activeScans);
            BaseMap map = MAPS.computeIfAbsent(uuid, u -> new BaseMap(anchor));
            boolean done = scanner.tick(runningServer.getOverworld(),
                    BlockPos.fromLong(anchor), map, myBudget);
            budgetLeft -= myBudget;
            activeScans--;
            if (done) {
                persist(player, map);
                ItWasTall.LOGGER.info("Base scan finished for {} ({}).",
                        player.getName().getString(), map);
                SCANS.remove(uuid);
            }
            if (budgetLeft <= 0) break;
        }
    }

    private static void restorePlacedMarkers(UUID uuid, BaseMap map) {
        if (server == null) return;
        BlockPos anchor = map.anchorPos();
        for (long packed : ItWasTallState.getOrCreate(server).placedBlocks(uuid)) {
            map.add(anchor, BaseMap.TYPE_PLACED, BlockPos.fromLong(packed));
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
