package com.itwastall.director;

import com.itwastall.ItWasTall;
import com.itwastall.config.ItWasTallConfig;
import com.itwastall.net.ModNetworking;
import com.itwastall.world.ItWasTallState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.LightType;
import net.minecraft.world.World;

import java.util.List;

/**
 * Director of the PHASE / STAGE system. Owns the live state of the running server:
 *
 * <ul>
 *   <li>{@link #phase()} — 0 = PRE (one quiet day with only stage-0 sounds), 1 = ACTIVE</li>
 *   <li>{@link #currentStage()} — the current {@link Stage}, re-read live by every system each tick</li>
 *   <li>{@link #tension()} — 0..100, drives automatic stage promotions (upwards only)</li>
 *   <li>{@link #itJoined()} — true once the "IT JOINED" sequence has fired (once per world)</li>
 * </ul>
 *
 * <p><b>Hard requirement (PHASES.md §0):</b> everything mutating the state goes through memory +
 * recurrent {@link ItWasTallState} writes, and every consumer polls the Director every tick —
 * no snapshots at server start — so commands ({@code /itwastall stage <n>}) take effect the
 * very next tick, without a restart.
 *
 * <p>Tension accrues per player per tick: night +{@code nightPerSec}, darkness (block light < 4,
 * night) +{@code darknessPerSec}, underground (Y < {@code mineMaxY}) +{@code undergroundPerSec},
 * all scaled by {@code escalationSpeed} and added as per-second rates (÷20 per tick).
 */
public final class Director {
    private static final Stage[] STAGES = Stage.values();

    private static ItWasTallState state;
    private static MinecraftServer server;

    private static int phase;
    private static Stage stage = Stage.STAGE_0;
    private static double tension;
    private static boolean itJoined;

    /** After {@code setStage} lowered the stage by hand, tension must not instantly undo it;
     *  auto-promotion pauses until the next programmatic tension change. */
    private static boolean autoPromote = true;

    private Director() {
    }

    // ---- lifecycle (wired in ItWasTall.java; runs after the mechanic systems' inits) ----

    /** SERVER_STARTING: load the per-world state from the Overworld and set up the live fields. */
    public static void init(MinecraftServer startingServer) {
        server = startingServer;
        state = ItWasTallState.getOrCreate(server);
        phase = state.phase();
        stage = Stage.fromIndex(state.stage());
        tension = state.tension();
        itJoined = state.itJoined();
        autoPromote = true;
        ItWasTall.LOGGER.info("Director loaded: phase {}, stage {} ({}), tension {}, itJoined {}",
                phase, stage.ordinal(), stage.displayName(), fmt(tension), itJoined);
    }

    /** SERVER_STOPPING: flush the state so nothing of phase/stage/tension is lost. */
    public static void stop(MinecraftServer stoppingServer) {
        save();
        ItWasTall.LOGGER.info("Director saved: phase {}, stage {}, tension {}, itJoined {}",
                phase, stage.ordinal(), fmt(tension), itJoined);
    }

    /** END_SERVER_TICK: activation counter, phase activation, tension accrual, stage promotions. */
    public static void tick(MinecraftServer tickingServer) {
        if (state == null) return;
        ItWasTallConfig.Values cfg = ItWasTallConfig.INSTANCE;

        if (phase == 2) {
            // the end: nothing grows any more
        } else if (phase == 0) {
            // physical activation counter: counted on every tick, saved — a restart never resets it
            state.addActivationTicks(1);
            if (state.activationTicks() >= cfg.activation.afterTicks
                    && (!cfg.activation.requireNight || isActivationNight())) {
                activatePhase(tickingServer);
            }
        } else {
            accrueTension(tickingServer, cfg);
        }

        // periodic world-file write (the counter changes every tick while PRE; changes mark dirty)
        if (tickingServer.getTicks() % 200 == 0) {
            state.saveIfNeeded(tickingServer);
        }

        promoteIfNeeded();
    }

    // ---- phase ----

    /**
     * Forces the PRE -> ACTIVE transition and plays the one-shot "IT JOINED" sequence.
     * Does NOT touch the stage — stage 0 starts working only from now on (tension accrual too).
     *
     * @return false if the mod is already in the ACTIVE phase.
     */
    public static boolean activatePhase(MinecraftServer runningServer) {
        if (phase == 1) return false;
        phase = 1;
        itJoined = true;
        if (state != null) {
            state.setPhase(1);
            state.setItJoined(true);
            state.markDirty();
        }
        // 4 s broadcast to everyone on the server (see ItJoinedOverlay on the client)
        ModNetworking.broadcastItJoined(runningServer != null ? runningServer : server);
        ItWasTall.LOGGER.info("Phase activated: IT JOINED.");
        return true;
    }

    public static Phase phaseValue() {
        return phase == 0 ? Phase.PRE : phase == 2 ? Phase.ENDED : Phase.ACTIVE;
    }

    /** THE ENDING (the last night held until dawn): It is gone. Phase 2 = ENDED — every system that runs only in the
     *  ACTIVE phase falls silent. /itwastall phase activate brings it back. */
    public static void end(MinecraftServer runningServer) {
        phase = 2;
        if (state != null) {
            state.setPhase(2);
            state.markDirty();
        }
        ItWasTall.LOGGER.info("Phase ENDED: It is gone.");
    }

    /** {@code PHASE} for command feedback: 0 = PRE, 1 = ACTIVE. */
    public static int phase() {
        return phase;
    }

    public enum Phase {
        PRE, ACTIVE, ENDED
    }    // ---- stage ----

    /**
     * Live stage of the running server. Call this every tick — never cache the result (hard
     * requirement PHASES.md §0: commands must take effect the next tick, no restarts).
     */
    public static Stage currentStage() {
        return stage;
    }

    public static int stageIndex() {
        return stage.ordinal();
    }

    /** Command feedback line: PHASE (PRE/ACTIVE), ETAP (0-5 + name), tension, itJoined. */
    public static String statusLine() {
        return "PHASE: " + (phase == 0 ? "PRE" : "ACTIVE")
                + " | ETAP: " + stage.ordinal() + " " + stage.displayName()
                + " | Tension: " + String.format(java.util.Locale.ROOT, "%.1f", tension)
                + " | It joined: " + (itJoined ? "yes" : "no");
    }

    /**
     * Runtime convenience (admin/test command!). Sets the stage immediately in memory and writes it
     * to {@link ItWasTallState}, allowing downward moves — the one documented exception to the
     * "stages only go up" rule. After a downward move automatic promotion is held back until the
     * next programmatic tension change, so a high tension does not instantly revert the admin's call.
     */
    public static void setStage(int requestedStage) {
        Stage next = Stage.fromIndex(requestedStage);
        if (state != null) state.setStage(next.ordinal());
        stage = next;
        // re-arm only if the memory stage is already at/above the tension's natural tier
        autoPromote = naturalTier(tension) <= stage.ordinal();
        ItWasTall.LOGGER.info("Stage set to {} ({}), tension {}.", stage.ordinal(), stage.displayName(), fmt(tension));
        save();
    }

    // ---- tension ----

    public static double tension() {
        return tension;
    }

    /** Adds (or with a negative amount removes) tension, then auto-promotes. */
    public static void addTension(double amount) {
        setTension(tension + amount);
    }

    /** Sets tension clamped to 0..100, writes the state and auto-promotes (upwards only). */
    public static void setTension(double value) {
        tension = MathHelper.clamp(value, 0.0, 100.0);
        if (state != null) state.setTension(tension);
        autoPromote = true;
        promoteIfNeeded();
        save();
    }

    /** Stage promotions only go up (PHASES.md §0); runs from both ticks and tension changes. */
    private static void promoteIfNeeded() {
        if (!autoPromote || tension <= 0) {
            return;
        }
        List<Integer> thresholds = thresholds();
        while (stage.ordinal() < 5 && stage.ordinal() < thresholds.size()
                && tension >= thresholds.get(stage.ordinal())) {
            stage = Stage.fromIndex(stage.ordinal() + 1);
            if (state != null) state.setStage(stage.ordinal());
            ItWasTall.LOGGER.info("Tension {} promoted stage to {} ({}).", fmt(tension), stage.ordinal(), stage.displayName());
        }
    }

    /** Natural stage tier for a tension: how many thresholds, from the bottom, are met. */
    private static int naturalTier(double tensionValue) {
        List<Integer> thresholds = thresholds();
        int tier = 0;
        for (int t : thresholds) {
            if (tensionValue >= t) tier++;
            else break;
        }
        return tier;
    }

    private static List<Integer> thresholds() {
        List<Integer> thresholds = ItWasTallConfig.INSTANCE.tension.thresholds;
        if (thresholds == null || thresholds.size() < 5) {
            return List.of(20, 40, 55, 70, 85);
        }
        return thresholds;
    }

    // ---- tension accrual ----

    private static void accrueTension(MinecraftServer runningServer, ItWasTallConfig.Values cfg) {
        ItWasTallConfig.Tension t = cfg.tension;
        double rate = 0.0;
        for (ServerPlayerEntity player : runningServer.getPlayerManager().getPlayerList()) {
            if (!player.isAlive()) continue;
            ServerWorld world = player.getServerWorld();
            if (world.getRegistryKey() != World.OVERWORLD) continue;

            long timeOfDay = world.getTimeOfDay() % 24000L;
            boolean night = timeOfDay >= 13000 && timeOfDay < 23000;
            if (night) rate += t.nightPerSec;
            if (night && world.getLightLevel(LightType.BLOCK, player.getBlockPos()) < 4) {
                rate += t.darknessPerSec;
            }
            if (player.getBlockPos().getY() < cfg.mineMaxY) {
                rate += t.undergroundPerSec;
            }
        }
        if (rate == 0.0) return;
        double perTick = rate * t.escalationSpeed / 20.0;
        tension = MathHelper.clamp(tension + perTick, 0.0, 100.0);
        state.setTension(tension);
        promoteIfNeeded();
    }

    // ---- helpers ----

    private static boolean isActivationNight() {
        ServerWorld overworld = server.getOverworld();
        long timeOfDay = overworld.getTimeOfDay() % 24000L;
        return timeOfDay > 13000 && timeOfDay < 23000;
    }

    private static void save() {
        if (state != null && server != null) state.saveIfNeeded(server);
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.1f", v);
    }
}
