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
 * Detects and keeps track of the player's base (MODPLAN §8.1) — smartly: the base is where the
 * player's OWN bed, doors, windows, chests and furnace are, never a villager's house or a dungeon
 * ({@link Ownership}). The anchor is the player's bed (clicked or placed); without one, the
 * strongest chunk cluster with a real base kit becomes the base ({@link ChunkScores}).
 *
 * <p>Fast: while the player is near the base it is rescanned every {@code baseRescanSeconds}
 * (default 1 s); a full scan is budgeted to finish within about that time
 * ({@code scanBlocksPerTick} .. {@code scanMaxBlocksPerTick} blocks per tick). Each scan is built in a
 * scratch map and swapped in when finished, so the map never flickers empty mid-scan.
 *
 * <p>Persistence: the finished map rides inside {@link ItWasTallState} (one saving system,
 * MODPLAN §2) — scans survive restarts, and the map comes back when next asked for.
 */
public final class BaseTracker {
    private static final Map<UUID, Long> ANCHORS = new HashMap<>();
    private static final Map<UUID, BaseMap> MAPS = new HashMap<>();
    private static final Map<UUID, BaseScanner> SCANS = new HashMap<>();
    private static final Map<UUID, Long> RESCAN_AT = new HashMap<>();
    /** The scan in progress is built here and swapped into MAPS when it finishes. */
    private static final Map<UUID, BaseMap> SCRATCH = new HashMap<>();
    /** World time of the next periodic rescan. */
    private static final Map<UUID, Long> NEXT_SCAN_AT = new HashMap<>();
    /** Something changed during a scan: start a fresh one as soon as it finishes. */
    private static final java.util.Set<UUID> RESTART = new java.util.HashSet<>();
    /** World time of an early cluster evaluation (a base block was just placed / used). */
    private static final Map<UUID, Long> EVAL_AT = new HashMap<>();

    /**
     * AREA MAP: where the player is when they are NOT at their own base (no base yet, or far away —
     * a village, a friend's house, a ruin they sleep in). Centred on the chunk (within 3 chunks of the
     * player) where they spent the most time, every window, door, light and dark corner is mapped —
     * ownership does not matter here — so doors still open and it still knocks on the window there.
     * It never becomes the player's base. Session only.
     */
    private static final Map<UUID, BaseMap> AREA_MAPS = new HashMap<>();
    private static final Map<UUID, BaseScanner> AREA_SCANS = new HashMap<>();
    private static final Map<UUID, BaseMap> AREA_SCRATCH = new HashMap<>();
    private static final Map<UUID, Long> AREA_NEXT = new HashMap<>();
    /** Radius of the area scan (blocks): about three chunks across. */
    public static final int AREA_RADIUS = 24;
    /** Farther than this from the own base, the area map takes over for events and tasks. */
    public static final int BASE_EVENT_RANGE = 48;

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
        SCRATCH.remove(player);
        NEXT_SCAN_AT.remove(player);
        RESTART.remove(player);
        EVAL_AT.remove(player);
        AREA_MAPS.remove(player);
        AREA_SCANS.remove(player);
        AREA_SCRATCH.remove(player);
        AREA_NEXT.remove(player);
        ChunkScores.forget(player);
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
        BlockState placed = world.getBlockState(pos);
        // your own bed is the strongest anchor there is
        if (placed.getBlock() instanceof BedBlock && !state.isBedAnchored(player.getUuid())) {
            setAnchor(player, pos);
            state.setBedAnchored(player.getUuid(), true);
            return;
        }
        // any other base block: let the smart evaluation look at it in a second
        if (ChunkScores.category(placed) != 0) EVAL_AT.put(player.getUuid(), world.getTime() + 20);

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
        if (server == null || !(world instanceof ServerWorld sw)) return ActionResult.PASS;
        ItWasTallState data = ItWasTallState.getOrCreate(server);
        java.util.Set<Long> owned = data.placedBlocks(sp.getUuid());
        if (state.getBlock() instanceof BedBlock) {
            // your bed (placed by you, or one that is not part of a village / structure) = your base.
            // Sleeping in a villager's bed does NOT make the village your base.
            if (Ownership.counts(owned, sw, pos)) {
                setAnchor(sp, pos.toImmutable());
                data.setBedAnchored(sp.getUuid(), true);
            }
            return ActionResult.PASS;
        }

        // A roof or four complete walls are NOT required to recognise a base, but one click is not
        // proof either: using your own chest / furnace / door adds evidence and asks for an early check.
        Long knownAnchor = anchorOf(sp.getUuid());
        if (isBaseClue(state) && Ownership.counts(owned, sw, pos)) {
            data.addChunkDwell(sp.getUuid(), new net.minecraft.util.math.ChunkPos(pos).toLong(), 1.5f);
            EVAL_AT.put(sp.getUuid(), world.getTime() + 20);
        }

        // any other block interaction near a known anchor = the base changed -> rescan
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
        ItWasTallState state = ItWasTallState.getOrCreate(tickingServer);
        long now = tickingServer.getOverworld().getTime();
        int ticks = tickingServer.getTicks();
        for (ServerPlayerEntity player : tickingServer.getPlayerManager().getPlayerList()) {
            if (player.isSpectator() || !player.isAlive() || player.getWorld().getRegistryKey() != World.OVERWORLD) continue;
            UUID uuid = player.getUuid();
            // time spent per chunk, every 20 s
            if (ticks % 400 == 0) {
                boolean calmNight = player.getWorld().isNight()
                        && com.itwastall.entity.ShelterCheck.isSheltered(player.getWorld(), player.getBlockPos());
                ChunkScores.addDwell(state, uuid, player.getBlockPos(), calmNight);
            }
            // light probe for an old base (no bed known yet), every 5 s
            if (ticks % 100 == 50 && !state.isBedAnchored(uuid)) {
                ChunkScores.probe(player.getServerWorld(), state, uuid, player.getBlockPos());
            }
            // smart evaluation every 5 s, or a second after a base block was placed / used
            Long evalAt = EVAL_AT.get(uuid);
            if (ticks % 100 == 0 || (evalAt != null && now >= evalAt)) {
                EVAL_AT.remove(uuid);
                evaluateClusters(player);
            }
            // away from the own base: map the area where the player actually is (every 5 s)
            tickArea(player, state, now);
            // periodic rescan while the player is near the base (default every second)
            Long anchor = ANCHORS.get(uuid);
            if (anchor != null) {
                BlockPos a = BlockPos.fromLong(anchor);
                double dx = player.getX() - a.getX(), dz = player.getZ() - a.getZ();
                BaseScanner scan = SCANS.get(uuid);
                Long next = NEXT_SCAN_AT.get(uuid);
                if (dx * dx + dz * dz < 64 * 64 && (scan == null || !scan.active()) && (next == null || now >= next)) {
                    startScan(uuid, anchor);
                }
            }
        }

        runScans(tickingServer);
        runAreaScans(tickingServer);
    }

    /** True while the player stands within {@link #BASE_EVENT_RANGE} of their own base. */
    public static boolean atOwnBase(ServerPlayerEntity player) {
        Long anchor = anchorOf(player.getUuid());
        if (anchor == null) return false;
        BlockPos a = BlockPos.fromLong(anchor);
        double dx = player.getX() - a.getX(), dz = player.getZ() - a.getZ();
        return dx * dx + dz * dz <= (double) BASE_EVENT_RANGE * BASE_EVENT_RANGE;
    }

    /** The map events and tasks should use for this player right now: the own base when near it, else the area map. */
    @Nullable
    public static BaseMap eventMapOf(ServerPlayerEntity player) {
        if (atOwnBase(player)) return mapOf(player.getUuid());
        return AREA_MAPS.get(player.getUuid());
    }

    /** Centre of {@link #eventMapOf}: the base anchor, or the centre of the area map. */
    @Nullable
    public static Long eventAnchorOf(ServerPlayerEntity player) {
        if (atOwnBase(player)) return anchorOf(player.getUuid());
        BaseMap area = AREA_MAPS.get(player.getUuid());
        return area == null ? null : area.anchor();
    }

    /** Inside the base: on a mapped interior cell of the own base / area, or right next to the base anchor. */
    public static boolean isInBase(ServerPlayerEntity player) {
        BaseMap map = eventMapOf(player);
        if (map == null) return false;
        BlockPos feet = player.getBlockPos();
        List<BlockPos> rooms = map.all(BaseMap.TYPE_ROOM);
        if (rooms.contains(feet) || rooms.contains(feet.up())) return true;
        if (!atOwnBase(player)) return false;
        BlockPos a = map.anchorPos();
        return Math.abs(feet.getX() - a.getX()) <= 5 && Math.abs(feet.getZ() - a.getZ()) <= 5 && Math.abs(feet.getY() - a.getY()) <= 3;
    }

    /** Picks the area centre and starts an area scan when one is due. */
    private static void tickArea(ServerPlayerEntity player, ItWasTallState state, long now) {
        UUID uuid = player.getUuid();
        if (atOwnBase(player)) return;
        BaseScanner scan = AREA_SCANS.get(uuid);
        if (scan != null && scan.active()) return;
        Long next = AREA_NEXT.get(uuid);
        if (next != null && now < next) return;
        // the chunk within 3 chunks where the player spent the most time; their own chunk wins ties
        net.minecraft.util.math.ChunkPos pc = new net.minecraft.util.math.ChunkPos(player.getBlockPos());
        Map<Long, Float> dwell = state.chunkDwell(uuid);
        long best = pc.toLong();
        float bestDwell = dwell.getOrDefault(best, 0f);
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                long c = net.minecraft.util.math.ChunkPos.toLong(pc.x + dx, pc.z + dz);
                float d = dwell.getOrDefault(c, 0f);
                if (d > bestDwell + 0.01f) {
                    bestDwell = d;
                    best = c;
                }
            }
        }
        int cx = net.minecraft.util.math.ChunkPos.getPackedX(best), cz = net.minecraft.util.math.ChunkPos.getPackedZ(best);
        // centre: where the player stands if that is (next to) the chosen chunk, else the middle of it
        BlockPos center = Math.abs(cx - pc.x) <= 1 && Math.abs(cz - pc.z) <= 1
                ? player.getBlockPos().toImmutable()
                : new BlockPos(cx * 16 + 8, player.getBlockY(), cz * 16 + 8);
        BaseScanner scanner = AREA_SCANS.computeIfAbsent(uuid, u -> new BaseScanner(AREA_RADIUS));
        AREA_SCRATCH.put(uuid, new BaseMap(center.asLong()));
        scanner.start(center.asLong(), pos -> true);   // everything counts here: village doors and windows too
    }

    private static void runAreaScans(MinecraftServer runningServer) {
        if (AREA_SCANS.isEmpty()) return;
        int maxPerTick = Math.max(64, ItWasTallConfig.INSTANCE.scanMaxBlocksPerTick);
        long now = runningServer.getOverworld().getTime();
        for (UUID uuid : new ArrayList<>(AREA_SCANS.keySet())) {
            BaseScanner scanner = AREA_SCANS.get(uuid);
            BaseMap scratch = AREA_SCRATCH.get(uuid);
            if (scanner == null || !scanner.active() || scratch == null) continue;
            ServerPlayerEntity player = runningServer.getPlayerManager().getPlayer(uuid);
            if (player == null || player.getServerWorld() != runningServer.getOverworld()) continue;
            // the whole area within ~5 s
            int budget = Math.min(maxPerTick, Math.max(64, (int) Math.ceil(scanner.fullWork() * 1.3 / 100.0)));
            if (scanner.tick(runningServer.getOverworld(), scratch.anchorPos(), scratch, budget)) {
                AREA_MAPS.put(uuid, scratch);
                AREA_SCRATCH.remove(uuid);
                AREA_NEXT.put(uuid, now + 100L);
            }
        }
    }

    /** Debug line for the area map. */
    public static String areaSummary(ServerPlayerEntity player) {
        if (atOwnBase(player)) return "area map: not used (you are at your base)";
        BaseMap area = AREA_MAPS.get(player.getUuid());
        if (area == null) return "area map: not scanned yet";
        return "area map around " + area.anchorPos().toShortString() + ": windows " + area.count(BaseMap.TYPE_WINDOW)
                + ", doors " + area.count(BaseMap.TYPE_DOOR) + ", lights " + area.count(BaseMap.TYPE_LIGHT)
                + ", chests " + area.count(BaseMap.TYPE_CHEST) + ", dark corners " + area.count(BaseMap.TYPE_CORNER);
    }

    /**
     * The smart part. A bed anchor stays as long as that bed exists and is the player's. Otherwise the
     * strongest cluster that has a real base kit (your bed / chests / furnace / doors / windows, outside
     * any generated structure) becomes the base; a non-bed base moves when another cluster is clearly
     * stronger, and an anchor that turns out to sit in someone else's structure is dropped.
     */
    private static void evaluateClusters(ServerPlayerEntity player) {
        if (server == null) return;
        ItWasTallState state = ItWasTallState.getOrCreate(server);
        ServerWorld world = player.getServerWorld();
        UUID uuid = player.getUuid();
        Long anchor = anchorOf(uuid);
        java.util.Set<Long> owned = state.placedBlocks(uuid);

        if (anchor != null && state.isBedAnchored(uuid)) {
            BlockPos bed = BlockPos.fromLong(anchor);
            if (!world.isChunkLoaded(bed)) return;
            BlockState st = world.getBlockState(bed);
            if (st.getBlock() instanceof BedBlock && Ownership.counts(owned, world, bed)) return;   // still your bed
            state.setBedAnchored(uuid, false);       // the bed is gone (or was never yours): look again
        }
        List<ChunkScores.Cluster> clusters = ChunkScores.clusters(world, state, uuid);
        ChunkScores.Cluster best = null;
        for (ChunkScores.Cluster c : clusters) {
            if (c.isBase()) {
                best = c;
                break;
            }
        }
        if (anchor == null) {
            if (best != null) adopt(player, best, "found");
            return;
        }
        BlockPos current = BlockPos.fromLong(anchor);
        boolean currentValid = !world.isChunkLoaded(current) || Ownership.counts(owned, world, current);
        if (!currentValid) {
            // e.g. an old anchor on a villager's chest: that was never your base
            if (best != null) adopt(player, best, "moved out of a generated structure to");
            else clearAnchor(player);
            return;
        }
        if (best == null || best.contains(current)) return;
        double currentScore = 0;
        for (ChunkScores.Cluster c : clusters) if (c.contains(current)) currentScore = c.score();
        if (best.score() >= Math.max(ChunkScores.BASE_SCORE * 2, currentScore * 2.0)) adopt(player, best, "moved to");
    }

    private static void adopt(ServerPlayerEntity player, ChunkScores.Cluster cluster, String how) {
        setAnchor(player, cluster.center());
        if (server != null) ItWasTallState.getOrCreate(server).setBedAnchored(player.getUuid(), cluster.centerIsBed());
        ItWasTall.LOGGER.info("Base of {} {} {} (score {}, kit: {}).", player.getName().getString(), how,
                cluster.center().toShortString(), Math.round(cluster.score()), cluster.kitString());
    }

    /** Forgets the base entirely (no evidence left for it). */
    private static void clearAnchor(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        ANCHORS.remove(uuid);
        MAPS.remove(uuid);
        SCANS.remove(uuid);
        SCRATCH.remove(uuid);
        if (server != null) ItWasTallState.getOrCreate(server).removePlayerBase(uuid);
        ItWasTall.LOGGER.info("Base of {} forgotten: it was not theirs.", player.getName().getString());
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
            lines.add("  #" + (i + 1) + " score " + Math.round(c.score()) + ", " + c.chunks().size() + " chunks, kit: " + c.kitString()
                    + (c.isBase() ? " — BASE" : c.hasKit() ? " — kit ok, score too low" : " — no base kit") + ", centre " + c.center().toShortString());
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
        BaseMap map = eventMapOf(player);
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
        startScan(player.getUuid(), anchor);
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

    /** Queues a scan now; if one is running, a fresh one follows right after it (changes are never lost). */
    private static void scheduleScan(ServerPlayerEntity player, long anchor, boolean force) {
        UUID uuid = player.getUuid();
        BaseScanner scanner = SCANS.get(uuid);
        if (scanner != null && scanner.active() && !force) {
            RESTART.add(uuid);
            return;
        }
        startScan(uuid, anchor);
    }

    /** Starts a scan into a scratch map; markers only for the player's own (or non-structure) blocks. */
    private static void startScan(UUID uuid, long anchor) {
        BaseScanner scanner = SCANS.computeIfAbsent(uuid, u -> new BaseScanner(ItWasTallConfig.INSTANCE.baseScanRadius));
        SCRATCH.put(uuid, new BaseMap(anchor));
        RESTART.remove(uuid);
        if (server == null) {
            scanner.start(anchor);
            return;
        }
        java.util.Set<Long> owned = ItWasTallState.getOrCreate(server).placedBlocks(uuid);
        ServerWorld ow = server.getOverworld();
        Map<Long, Boolean> refs = new HashMap<>();
        scanner.start(anchor, pos -> Ownership.placedBy(owned, ow, pos)
                || !(refs.computeIfAbsent(net.minecraft.util.math.ChunkPos.toLong(pos.getX() >> 4, pos.getZ() >> 4),
                        c -> ow.getStructureAccessor().hasStructureReferences(pos))
                     && Ownership.inGeneratedStructure(ow, pos)));
    }

    private static void runScans(MinecraftServer runningServer) {
        if (SCANS.isEmpty()) return;
        ItWasTallConfig.Values cfg = ItWasTallConfig.INSTANCE;
        int minPerTick = Math.max(8, cfg.scanBlocksPerTick);
        int maxPerTick = Math.max(minPerTick, cfg.scanMaxBlocksPerTick);
        int totalLeft = maxPerTick * 2;            // all players together never exceed this per tick
        long now = runningServer.getOverworld().getTime();
        for (UUID uuid : new ArrayList<>(SCANS.keySet())) {
            BaseScanner scanner = SCANS.get(uuid);
            if (scanner == null || !scanner.active()) continue;
            ServerPlayerEntity player = runningServer.getPlayerManager().getPlayer(uuid);
            Long anchor = ANCHORS.get(uuid);
            // Anchors belong to the Overworld; never scan the same coordinates in another dimension.
            if (player == null || anchor == null || player.getServerWorld() != runningServer.getOverworld()) continue;
            // enough per tick to finish a whole scan (plus flood fill) within baseRescanSeconds
            int need = (int) Math.ceil(scanner.fullWork() * 1.3 / (20.0 * Math.max(1, cfg.baseRescanSeconds)));
            int myBudget = Math.min(totalLeft, Math.max(minPerTick, Math.min(maxPerTick, need)));
            if (myBudget <= 0) break;
            BaseMap scratch = SCRATCH.computeIfAbsent(uuid, u -> new BaseMap(anchor));
            boolean done = scanner.tick(runningServer.getOverworld(), BlockPos.fromLong(anchor), scratch, myBudget);
            totalLeft -= myBudget;
            if (done) {
                BaseMap map = MAPS.computeIfAbsent(uuid, u -> new BaseMap(anchor));
                if (map.anchor() == anchor && !map.sameScanMarkers(scratch)) {
                    map.replaceScanMarkers(scratch);
                    restorePlacedMarkers(uuid, map);
                    persist(player, map);
                    ItWasTall.LOGGER.debug("Base map of {} updated ({}).", player.getName().getString(), map);
                }
                SCRATCH.remove(uuid);
                NEXT_SCAN_AT.put(uuid, now + 20L * Math.max(1, cfg.baseRescanSeconds));
                if (RESTART.remove(uuid)) startScan(uuid, anchor);
            }
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
