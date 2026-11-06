package com.itwastall.world;

import com.itwastall.ItWasTall;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtLongArray;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Persistent state of the phase/stage system, stored in the Overworld's PersistentStateManager
 * (saved inside the world folder, so a server restart does not reset it).
 *
 * <p>Holds the PHASE (0 = PRE, 1 = ACTIVE), the stage (0-5), the tension (0-100), the physical
 * elapsed-tick counter of the activation (counted on every server tick while PRE and saved —
 * a restart never resets it) plus generic per-player maps (timers / per-player counters) that
 * later systems (HauntDirector, tasks...) can hang their data on.
 *
 * <p>Every mutation calls {@link #markDirty()}; {@link Director} calls {@link #saveIfNeeded(MinecraftServer)}
 * on a timer, with {@link PersistentState#save} doing the actual (dirty-checked) write.
 */
public final class ItWasTallState extends PersistentState {
    /** Id both for the dat file (world/data/itwastall_state.dat) and the manager key. */
    public static final String ID = "itwastall_state";

    private static final PersistentState.Type<ItWasTallState> TYPE = new PersistentState.Type<>(
            ItWasTallState::new, ItWasTallState::createFromNbt, DataFixTypes.LEVEL);

    private int phase;          // 0 = PRE (quiet), 1 = ACTIVE (IT JOINED played)
    private int stage;          // 0..5, see director.Stage
    private double tension;     // 0..100
    private boolean itJoined;   // IT JOINED sequence already played (never repeats)
    private long activationTicks; // physical elapsed ticks counted while PRE; persisted

    /** Generic per-player long values (future: next-haunt timestamps, task timers...). */
    private final Map<UUID, Long> playerTimers = new HashMap<>();
    /** Generic per-player int values (future: counters like "nudged twice", "closed eyes"...). */
    private final Map<UUID, Integer> playerCounters = new HashMap<>();
    /** Per-player base maps (BaseTracker -> BaseMap.toNbt), MODPLAN §8.3/§20: saved with the world. */
    private final Map<UUID, NbtCompound> playerBases = new HashMap<>();
    /** Every block position placed by each player, recorded regardless of phase or stage. */
    private final Map<UUID, Set<Long>> playerPlacedBlocks = new HashMap<>();
    /** Lore books already placed in this player's base; prevents repeats across restarts. */
    private final Map<UUID, Set<String>> deliveredLoreBooks = new HashMap<>();
    /** Next world-time tick when another lore book may be placed for this player. */
    private final Map<UUID, Long> loreBookCooldowns = new HashMap<>();

    /**
     * MODPLAN §4: the one real Tall One, remembered in the world data so the server limit also counts
     * an It standing in an UNLOADED chunk and no duplicate survives a restart. {@code activeItSeen} is
     * the Overworld time of its last tick; a record that has not ticked for {@link #ACTIVE_IT_STALE_TICKS}
     * no longer blocks new hauntings (and if that old It ever loads again, it removes itself).
     */
    @org.jetbrains.annotations.Nullable
    private UUID activeIt;
    private String activeItDim = "";
    private long activeItPos;
    private long activeItSeen;
    public static final long ACTIVE_IT_STALE_TICKS = 20L * 60 * 10;

    /** Time spent per chunk (ChunkScores, MODPLAN §8 notes): player -> chunk -> dwell points. */
    private final Map<UUID, Map<Long, Float>> chunkDwell = new HashMap<>();
    /** Players whose base anchor is a bed they used (a bed always wins over a chunk cluster). */
    private final Set<UUID> bedAnchored = new HashSet<>();
    private static final int MAX_DWELL_CHUNKS = 400;

    /** Server-wide journal (BOOKS tab): every book / note someone on the server has read, in order. */
    private final java.util.List<NbtCompound> sharedBooks = new java.util.ArrayList<>();
    /** Server-wide journal (NOTES tab): notes players wrote for each other, oldest first. */
    private final java.util.List<NbtCompound> sharedNotes = new java.util.ArrayList<>();
    private long nextNoteId = 1;
    /** Per-player task history for the journal, newest first. */
    private final Map<UUID, java.util.List<String>> taskLog = new HashMap<>();

    public ItWasTallState() {
    }

    /** Overworld PersistentStateManager accessor (Yarn 1.21.1: getOrCreate(Type, String)). */
    public static ItWasTallState getOrCreate(MinecraftServer server) {
        PersistentStateManager manager = server.getOverworld().getPersistentStateManager();
        return manager.getOrCreate(TYPE, ID);
    }

    /** Reads a saved state (also used by the GameTests to check that a save round-trips). */
    public static ItWasTallState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        return createFromNbt(nbt, lookup);
    }

    private static ItWasTallState createFromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        ItWasTallState state = new ItWasTallState();
        state.phase = nbt.getInt("phase");
        state.stage = nbt.getInt("stage");
        state.tension = nbt.getDouble("tension");
        state.itJoined = nbt.getBoolean("itJoined");
        state.activationTicks = nbt.getLong("activationTicks");
        NbtCompound timers = nbt.getCompound("playerTimers");
        for (String key : timers.getKeys()) state.playerTimers.put(UUID.fromString(key), timers.getLong(key));
        NbtCompound counters = nbt.getCompound("playerCounters");
        for (String key : counters.getKeys()) state.playerCounters.put(UUID.fromString(key), counters.getInt(key));
        NbtCompound bases = nbt.getCompound("playerBases");
        for (String key : bases.getKeys()) state.playerBases.put(UUID.fromString(key), bases.getCompound(key));
        NbtCompound placedBlocks = nbt.getCompound("playerPlacedBlocks");
        for (String key : placedBlocks.getKeys()) {
            Set<Long> positions = new HashSet<>();
            for (long packed : placedBlocks.getLongArray(key)) positions.add(packed);
            state.playerPlacedBlocks.put(UUID.fromString(key), positions);
        }
        NbtCompound lore = nbt.getCompound("deliveredLoreBooks");
        for (String key : lore.getKeys()) {
            Set<String> ids = new HashSet<>();
            for (String id : lore.getCompound(key).getKeys()) if (lore.getCompound(key).getBoolean(id)) ids.add(id);
            state.deliveredLoreBooks.put(UUID.fromString(key), ids);
        }
        NbtCompound loreCooldowns = nbt.getCompound("loreBookCooldowns");
        for (String key : loreCooldowns.getKeys()) state.loreBookCooldowns.put(UUID.fromString(key), loreCooldowns.getLong(key));
        NbtCompound dwell = nbt.getCompound("chunkDwell");
        for (String key : dwell.getKeys()) {
            NbtCompound one = dwell.getCompound(key);
            long[] chunks = one.getLongArray("c");
            int[] bits = one.getIntArray("v");
            Map<Long, Float> m = new HashMap<>();
            for (int i = 0; i < Math.min(chunks.length, bits.length); i++) m.put(chunks[i], Float.intBitsToFloat(bits[i]));
            state.chunkDwell.put(UUID.fromString(key), m);
        }
        for (String key : nbt.getCompound("bedAnchored").getKeys()) state.bedAnchored.add(UUID.fromString(key));
        net.minecraft.nbt.NbtList books = nbt.getList("sharedBooks", net.minecraft.nbt.NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < books.size(); i++) state.sharedBooks.add(books.getCompound(i));
        net.minecraft.nbt.NbtList notes = nbt.getList("sharedNotes", net.minecraft.nbt.NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < notes.size(); i++) state.sharedNotes.add(notes.getCompound(i));
        state.nextNoteId = Math.max(1, nbt.getLong("nextNoteId"));
        NbtCompound tasks = nbt.getCompound("taskLog");
        for (String key : tasks.getKeys()) {
            net.minecraft.nbt.NbtList list = tasks.getList(key, net.minecraft.nbt.NbtElement.STRING_TYPE);
            java.util.List<String> lines = new java.util.ArrayList<>();
            for (int i = 0; i < list.size(); i++) lines.add(list.getString(i));
            state.taskLog.put(UUID.fromString(key), lines);
        }
        if (nbt.containsUuid("activeIt")) {
            state.activeIt = nbt.getUuid("activeIt");
            state.activeItDim = nbt.getString("activeItDim");
            state.activeItPos = nbt.getLong("activeItPos");
            state.activeItSeen = nbt.getLong("activeItSeen");
        }
        return state;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        nbt.putInt("phase", phase);
        nbt.putInt("stage", stage);
        nbt.putDouble("tension", tension);
        nbt.putBoolean("itJoined", itJoined);
        nbt.putLong("activationTicks", activationTicks);
        NbtCompound timers = new NbtCompound();
        playerTimers.forEach((uuid, value) -> timers.putLong(uuid.toString(), value));
        nbt.put("playerTimers", timers);
        NbtCompound counters = new NbtCompound();
        playerCounters.forEach((uuid, value) -> counters.putInt(uuid.toString(), value));
        nbt.put("playerCounters", counters);
        NbtCompound bases = new NbtCompound();
        playerBases.forEach((uuid, value) -> bases.put(uuid.toString(), value));
        nbt.put("playerBases", bases);
        NbtCompound placedBlocks = new NbtCompound();
        playerPlacedBlocks.forEach((uuid, positions) ->
                placedBlocks.put(uuid.toString(), new NbtLongArray(
                        positions.stream().mapToLong(Long::longValue).toArray())));
        nbt.put("playerPlacedBlocks", placedBlocks);
        NbtCompound lore = new NbtCompound();
        deliveredLoreBooks.forEach((uuid, ids) -> {
            NbtCompound entries = new NbtCompound();
            for (String id : ids) entries.putBoolean(id, true);
            lore.put(uuid.toString(), entries);
        });
        nbt.put("deliveredLoreBooks", lore);
        NbtCompound loreCooldowns = new NbtCompound();
        loreBookCooldowns.forEach((uuid, value) -> loreCooldowns.putLong(uuid.toString(), value));
        nbt.put("loreBookCooldowns", loreCooldowns);
        NbtCompound dwell = new NbtCompound();
        chunkDwell.forEach((uuid, m) -> {
            long[] chunks = new long[m.size()];
            int[] bits = new int[m.size()];
            int i = 0;
            for (Map.Entry<Long, Float> e : m.entrySet()) {
                chunks[i] = e.getKey();
                bits[i++] = Float.floatToIntBits(e.getValue());
            }
            NbtCompound one = new NbtCompound();
            one.putLongArray("c", chunks);
            one.putIntArray("v", bits);
            dwell.put(uuid.toString(), one);
        });
        nbt.put("chunkDwell", dwell);
        NbtCompound beds = new NbtCompound();
        for (UUID uuid : bedAnchored) beds.putBoolean(uuid.toString(), true);
        nbt.put("bedAnchored", beds);
        net.minecraft.nbt.NbtList books = new net.minecraft.nbt.NbtList();
        books.addAll(sharedBooks);
        nbt.put("sharedBooks", books);
        net.minecraft.nbt.NbtList notes = new net.minecraft.nbt.NbtList();
        notes.addAll(sharedNotes);
        nbt.put("sharedNotes", notes);
        nbt.putLong("nextNoteId", nextNoteId);
        NbtCompound tasks = new NbtCompound();
        taskLog.forEach((uuid, lines) -> {
            net.minecraft.nbt.NbtList list = new net.minecraft.nbt.NbtList();
            for (String line : lines) list.add(net.minecraft.nbt.NbtString.of(line));
            tasks.put(uuid.toString(), list);
        });
        nbt.put("taskLog", tasks);
        if (activeIt != null) {
            nbt.putUuid("activeIt", activeIt);
            nbt.putString("activeItDim", activeItDim);
            nbt.putLong("activeItPos", activeItPos);
            nbt.putLong("activeItSeen", activeItSeen);
        }
        return nbt;
    }

    /**
     * Saves this state (and any other dirty persistent states of the Overworld) if something was changed.
     * ItWasTallState#save(File, ...) skips itself again when nothing is dirty, so repeated calls are cheap.
     */
    public void saveIfNeeded(MinecraftServer server) {
        if (isDirty()) server.getOverworld().getPersistentStateManager().save();
    }

    // ---- phase / stage / tension ----

    public int phase() {
        return phase;
    }

    public void setPhase(int phase) {
        this.phase = MathHelper.clamp(phase, 0, 1);
        markDirty();
    }

    public int stage() {
        return stage;
    }

    public void setStage(int stage) {
        this.stage = MathHelper.clamp(stage, 0, 5);
        markDirty();
    }

    public double tension() {
        return tension;
    }

    public void setTension(double tension) {
        this.tension = MathHelper.clamp(tension, 0.0, 100.0);
        markDirty();
    }

    public boolean itJoined() {
        return itJoined;
    }

    public void setItJoined(boolean itJoined) {
        this.itJoined = itJoined;
        markDirty();
    }

    // ---- activation counter ----

    public long activationTicks() {
        return activationTicks;
    }

    public void addActivationTicks(long ticks) {
        activationTicks += ticks;
        markDirty();
    }

    // ---- generic per-player helpers ----

    public Long getPlayerTimer(UUID player) {
        return playerTimers.get(player);
    }

    public void putPlayerTimer(UUID player, long value) {
        playerTimers.put(player, value);
        markDirty();
    }

    public void removePlayerTimer(UUID player) {
        if (playerTimers.remove(player) != null) markDirty();
    }

    public Integer getPlayerCounter(UUID player) {
        return playerCounters.get(player);
    }

    public void putPlayerCounter(UUID player, int value) {
        playerCounters.put(player, value);
        markDirty();
    }

    public void removePlayerCounter(UUID player) {
        if (playerCounters.remove(player) != null) markDirty();
    }

    // ---- player-placed block history (BaseTracker) ----

    /** Returns true only for a newly recorded position. */
    public boolean recordPlacedBlock(UUID player, long packedPos) {
        boolean added = playerPlacedBlocks.computeIfAbsent(player, ignored -> new HashSet<>()).add(packedPos);
        if (added) markDirty();
        return added;
    }

    /** Snapshot of every position this player has placed, including before phase/stage activation. */
    public Set<Long> placedBlocks(UUID player) {
        return Set.copyOf(playerPlacedBlocks.getOrDefault(player, Set.of()));
    }

    // ---- lore books (MODPLAN §9) ----

    public boolean hasLoreBook(UUID player, String id) {
        return deliveredLoreBooks.getOrDefault(player, Set.of()).contains(id);
    }

    public void markLoreBookDelivered(UUID player, String id) {
        if (deliveredLoreBooks.computeIfAbsent(player, ignored -> new HashSet<>()).add(id)) markDirty();
    }

    public int deliveredLoreBookCount(UUID player) {
        return deliveredLoreBooks.getOrDefault(player, Set.of()).size();
    }

    /** The lore book ids already handed to this player (the journal's "found" list). */
    public Set<String> loreBookIds(UUID player) {
        return deliveredLoreBooks.getOrDefault(player, Set.of());
    }

    public long loreBookCooldown(UUID player) {
        return loreBookCooldowns.getOrDefault(player, 0L);
    }

    public void setLoreBookCooldown(UUID player, long until) {
        loreBookCooldowns.put(player, until);
        markDirty();
    }

    // ---- per-player base maps (BaseTracker, MODPLAN §8) ----

    public NbtCompound getPlayerBase(UUID player) {
        return playerBases.get(player);
    }

    public void putPlayerBase(UUID player, NbtCompound baseNbt) {
        playerBases.put(player, baseNbt);
        markDirty();
    }

    public void removePlayerBase(UUID player) {
        if (playerBases.remove(player) != null) markDirty();
    }

    // ---- the one real Tall One (MODPLAN §4) ----

    @org.jetbrains.annotations.Nullable
    public UUID activeIt() {
        return activeIt;
    }

    public String activeItDim() {
        return activeItDim;
    }

    public long activeItPos() {
        return activeItPos;
    }

    public long activeItSeen() {
        return activeItSeen;
    }

    /** True while the record still counts against the server limit (it ticked recently). */
    public boolean activeItFresh(long now) {
        return activeIt != null && now - activeItSeen < ACTIVE_IT_STALE_TICKS;
    }

    /** Called by the It every second while it exists (and once when it appears). */
    public void noteActiveIt(UUID uuid, String dim, long pos, long now) {
        boolean changed = !uuid.equals(activeIt) || !dim.equals(activeItDim) || pos != activeItPos;
        activeIt = uuid;
        activeItDim = dim;
        activeItPos = pos;
        activeItSeen = now;
        if (changed) markDirty();   // the "seen" time alone is not worth a disk write every second
    }

    /** Forgets the record, but only if it still belongs to this It. */
    public void clearActiveIt(UUID uuid) {
        if (uuid.equals(activeIt)) {
            activeIt = null;
            activeItDim = "";
            markDirty();
        }
    }

    /** Admin escape hatch (/itwastall debug entity forget). */
    public void forgetActiveIt() {
        activeIt = null;
        activeItDim = "";
        markDirty();
    }

    // ---- chunk scores (BaseTracker / ChunkScores) ----

    public void addChunkDwell(UUID player, long chunk, float amount) {
        Map<Long, Float> m = chunkDwell.computeIfAbsent(player, u -> new HashMap<>());
        m.merge(chunk, amount, Float::sum);
        if (m.size() > MAX_DWELL_CHUNKS) {
            // forget the chunk the player cared least about
            Long weakest = null;
            float min = Float.MAX_VALUE;
            for (Map.Entry<Long, Float> e : m.entrySet()) {
                if (e.getValue() < min) {
                    min = e.getValue();
                    weakest = e.getKey();
                }
            }
            if (weakest != null) m.remove(weakest);
        }
        markDirty();
    }

    public Map<Long, Float> chunkDwell(UUID player) {
        return chunkDwell.getOrDefault(player, Map.of());
    }

    public boolean isBedAnchored(UUID player) {
        return bedAnchored.contains(player);
    }

    public void setBedAnchored(UUID player, boolean bed) {
        if (bed ? bedAnchored.add(player) : bedAnchored.remove(player)) markDirty();
    }

    // ---- shared journal (BOOKS / NOTES tabs) and the task history ----

    public java.util.List<NbtCompound> sharedBooks() {
        return java.util.Collections.unmodifiableList(sharedBooks);
    }

    public boolean hasSharedBook(String id) {
        for (NbtCompound b : sharedBooks) if (id.equals(b.getString("id"))) return true;
        return false;
    }

    public void addSharedBook(NbtCompound book) {
        sharedBooks.add(book);
        markDirty();
    }

    public java.util.List<NbtCompound> sharedNotes() {
        return java.util.Collections.unmodifiableList(sharedNotes);
    }

    /** Adds a note (the oldest ones go when there are more than {@code max}); returns its id. */
    public long addSharedNote(NbtCompound note, int max) {
        long id = nextNoteId++;
        note.putLong("id", id);
        sharedNotes.add(note);
        while (sharedNotes.size() > max) sharedNotes.remove(0);
        markDirty();
        return id;
    }

    @org.jetbrains.annotations.Nullable
    public NbtCompound sharedNote(long id) {
        for (NbtCompound n : sharedNotes) if (n.getLong("id") == id) return n;
        return null;
    }

    public boolean removeSharedNote(long id) {
        boolean removed = sharedNotes.removeIf(n -> n.getLong("id") == id);
        if (removed) markDirty();
        return removed;
    }

    public java.util.List<String> taskLog(UUID player) {
        return taskLog.getOrDefault(player, java.util.List.of());
    }

    /** Newest first; {@code replaceNewest} rewrites the newest line (a task's result replaces "in progress"). */
    public void putTaskLog(UUID player, String line, boolean replaceNewest, int max) {
        java.util.List<String> lines = taskLog.computeIfAbsent(player, u -> new java.util.ArrayList<>());
        if (replaceNewest && !lines.isEmpty()) lines.set(0, line);
        else lines.add(0, line);
        while (lines.size() > max) lines.remove(lines.size() - 1);
        markDirty();
    }
}
