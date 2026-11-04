package com.itwastall.world;

import com.itwastall.ItWasTall;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
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
    /** Lore books already placed in this player's base; prevents repeats across restarts. */
    private final Map<UUID, Set<String>> deliveredLoreBooks = new HashMap<>();
    /** Next world-time tick when another lore book may be placed for this player. */
    private final Map<UUID, Long> loreBookCooldowns = new HashMap<>();

    public ItWasTallState() {
    }

    /** Overworld PersistentStateManager accessor (Yarn 1.21.1: getOrCreate(Type, String)). */
    public static ItWasTallState getOrCreate(MinecraftServer server) {
        PersistentStateManager manager = server.getOverworld().getPersistentStateManager();
        return manager.getOrCreate(TYPE, ID);
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
        NbtCompound lore = nbt.getCompound("deliveredLoreBooks");
        for (String key : lore.getKeys()) {
            Set<String> ids = new HashSet<>();
            for (String id : lore.getCompound(key).getKeys()) if (lore.getCompound(key).getBoolean(id)) ids.add(id);
            state.deliveredLoreBooks.put(UUID.fromString(key), ids);
        }
        NbtCompound loreCooldowns = nbt.getCompound("loreBookCooldowns");
        for (String key : loreCooldowns.getKeys()) state.loreBookCooldowns.put(UUID.fromString(key), loreCooldowns.getLong(key));
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
}
