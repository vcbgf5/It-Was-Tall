package com.itwastall.task;

import com.itwastall.ItWasTall;
import com.itwastall.base.BaseMap;
import com.itwastall.base.BaseTracker;
import com.itwastall.config.ItWasTallConfig;
import com.itwastall.director.Director;
import com.itwastall.director.Stage;
import com.itwastall.entity.HauntDirector;
import com.itwastall.entity.MineEvents;
import com.itwastall.entity.ShelterCheck;
import com.itwastall.net.ModNetworking;
import com.itwastall.net.TaskToastPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * TaskManager (MODPLAN §15, PHASES Poziom 8): tasks are assigned to ONE player at a time,
 * stage-gated by {@code tasksFlag} (stage 3 unlocks both stage-3 tasks), rewarded with
 * tension (complete -8, failed +5 per PHASES §2) and announced with the client TaskToast.
 *
 * <p>Stage-3 pair:
 * <ul>
 *   <li><b>NIE WYCHODŹ NA ZEWNĄTRZ</b> (STAY_INSIDE): assigned at night while sheltered;
 *       leaving the base at ANY point fails it (and on stage>=4 this can wake It up). You
 *       survive {@code taskStayInsideSeconds} in the base and It grows quiet.</li>
 *   <li><b>ZGAŚ ŚWIATŁA</b> (DOUSE_LIGHTS): 3 luminous blocks chosen from the player's SAVED
 *       BaseMap light markers (the base scan feeds the task list — MODPLAN §8 aggregation);
 *       the task completes when all of them no longer glow, expires at dawn.</li>
 * </ul>
 *
 * <p>Assignments and their outcome leave through {@link TaskToastPayload}; the per-player
 * active task survives no restart (kept in server memory like the other directors) and one
 * failure/completion puts the player on a cooldown ({@code taskCooldownSeconds}).
 */
public final class TaskManager {
    public enum Kind {STAY_INSIDE, DOUSE_LIGHTS}

    /** The active task of one player (server-memory; tasks reset on restart). */
    private static final class Task {
        final Kind kind;
        /** World time when the task hard-expires (fail). */
        final long expiresAt;
        /** DOUSE_LIGHTS: positions that must stop glowing. */
        final List<BlockPos> lights;

        Task(Kind kind, long expiresAt, List<BlockPos> lights) {
            this.kind = kind;
            this.expiresAt = expiresAt;
            this.lights = lights;
        }
    }

    private static final Map<UUID, Task> ACTIVE = new HashMap<>();
    /** Last world time the player closed a task; no new assignment before the cooldown. */
    private static final Map<UUID, Long> COOLDOWN = new HashMap<>();

    private static final String TITLE_NEW = "IT ASKS.";
    private static final String TITLE_DONE = "IT FALLS SILENT.";
    private static final String TITLE_FAILED = "IT GRINS.";

    private TaskManager() {
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(TaskManager::tick);
    }

    public static void forget(UUID player) {
        ACTIVE.remove(player);   // unfinished work drops; rejoining resets, like the other directors
        COOLDOWN.remove(player); // gone: forget the debt too
    }

    /** Command/debug: the human line for a player's task slot. */
    public static String status(UUID player) {
        Task task = ACTIVE.get(player);
        return task == null ? "no task" : taskDescription(task);
    }

    /** Force an assignment (commands); returns false when the context refuses it. */
    public static boolean forceAssign(ServerPlayerEntity player, @Nullable Kind requested) {
        Stage st = Director.currentStage();
        if (st.tasksFlag() <= 0) return false;
        Kind kind = pickKind(player, requested, ItWasTallConfig.INSTANCE);
        return kind != null && assign(player, kind);
    }

    // -------------------------------------------------------------- ticking ----

    private static void tick(MinecraftServer server) {
        if (server.getTicks() % 20 != 0) return; // one decision tick per second is plenty
        if (Director.phase() == 0) return;       // PHASE PRE: total silence

        Stage st = Director.currentStage();
        int flag = st.tasksFlag();               // live read: /itwastall stage works next tick
        if (flag <= 0) return;

        long now = server.getOverworld().getTime();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!player.isAlive() || player.isCreative() || player.isSpectator()) continue;
            if (player.getWorld().getRegistryKey() != World.OVERWORLD) continue;

            Task task = ACTIVE.get(player.getUuid());
            if (task != null) {
                tickTask(player, task, now, server);
                continue;
            }
            maybeAssign(player, st, flag, now);
        }
    }

    private static void tickTask(ServerPlayerEntity player, Task task, long now, MinecraftServer server) {
        World world = player.getWorld();
        boolean sheltered = ShelterCheck.isSheltered(world, player.getBlockPos());
        switch (task.kind) {
            case STAY_INSIDE -> {
                // MODPLAN 15.1: leaving at ANY point fails — no mercy, It feels the draft
                if (!sheltered) {
                    fail(player, task, server);
                    return;
                }
                if (now >= task.expiresAt) complete(player, task);
            }
            case DOUSE_LIGHTS -> {
                // success invariant: every chosen spot is dead (air or a cold block), not
                // necessarily REMOVED by the player (a replacement counts: that's honest work)
                boolean allCold = true;
                for (BlockPos light : task.lights) {
                    var state = world.getBlockState(light);
                    if (!state.isAir() && state.getLuminance() > 0) {
                        allCold = false;
                        break;
                    }
                }
                if (allCold) {
                    complete(player, task);
                    return;
                }
                // dawn kills the assignment: It no longer bothers with the daylight gamble
                if (now >= task.expiresAt) fail(player, task, server);
            }
        }
    }

    // ------------------------------------------------------------- assigning ----

    private static void maybeAssign(ServerPlayerEntity player, Stage st, int flag, long now) {
        UUID uuid = player.getUuid();
        Long cooldownUntil = COOLDOWN.get(uuid);
        if (cooldownUntil != null && now < cooldownUntil) return;

        World world = player.getWorld();
        // tasks live at night, from the shelter (MODPLAN 15.1); day is for mining fear
        if (!world.isNight()) return;
        if (!ShelterCheck.isSheltered(world, player.getBlockPos())) return;

        ItWasTallConfig.Values cfg = ItWasTallConfig.INSTANCE;
        if (player.getRandom().nextDouble() >= cfg.taskChancePerSecond) return;
        // 50/50 between the two stage-3 tasks; NYC lights need a mapped base, so fall back
        Kind wanted = player.getRandom().nextBoolean() ? Kind.DOUSE_LIGHTS : Kind.STAY_INSIDE;
        Kind kind = pickKind(player, wanted, cfg);
        if (kind != null) {
            assign(player, kind);
            COOLDOWN.put(uuid, now + cfg.taskCooldownSeconds * 20L);
        }
    }

    /** Chooses a kind the CONTEXT allows; {@code requested} wins when explicitly given. */
    @Nullable
    private static Kind pickKind(ServerPlayerEntity player, @Nullable Kind requested,
                                 ItWasTallConfig.Values cfg) {
        if (requested == Kind.DOUSE_LIGHTS) {
            List<BlockPos> lights = pickLights(player, cfg);
            return lights == null ? null : Kind.DOUSE_LIGHTS;
        }
        return Kind.STAY_INSIDE; // stage-3 default: the safest first task
    }

    /**
     * Chooses the light markers It would personally extinguish (closest to the player).
     * Returns null when the mapping has no usable lights within reach.
     */
    @Nullable
    private static List<BlockPos> pickLights(ServerPlayerEntity player, ItWasTallConfig.Values cfg) {
        ServerWorld world = player.getServerWorld();
        BaseMap map = BaseTracker.mapOf(player.getUuid());
        if (map == null) return null;
        List<BlockPos> candidates = new ArrayList<>(map.all(BaseMap.TYPE_LIGHT));
        candidates.removeIf(pos -> !pos.isWithinDistance(player.getBlockPos(), 24)
                || world.getBlockState(pos).isAir()
                || world.getBlockState(pos).getLuminance() <= 0);
        if (candidates.isEmpty()) return null;
        candidates.sort((a, b) -> Double.compare(
                a.getSquaredDistance(player.getBlockPos()),
                b.getSquaredDistance(player.getBlockPos())));
        int count = Math.min(cfg.taskLightsCount, candidates.size());
        return new ArrayList<>(candidates.subList(0, count));
    }

    private static boolean assign(ServerPlayerEntity player, Kind kind) {
        ItWasTallConfig.Values cfg = ItWasTallConfig.INSTANCE;
        World world = player.getWorld();
        long now = world.getTime();
        Task task;
        if (kind == Kind.DOUSE_LIGHTS) {
            List<BlockPos> lights = pickLights(player, cfg);
            if (lights == null) return false;
            task = new Task(Kind.DOUSE_LIGHTS, now + 20L * cfg.taskLightsDeadlineSeconds, lights);
        } else {
            task = new Task(Kind.STAY_INSIDE, now + 20L * cfg.taskStayInsideSeconds, List.of());
        }
        ACTIVE.put(player.getUuid(), task);
        sendToast(player, TaskToastPayload.KIND_NEW, TITLE_NEW, newToastText(kind, task));
        ItWasTall.LOGGER.info("Task assigned to {}: {} ({} light(s)).",
                player.getName().getString(), task.kind, task.lights.size());
        return true;
    }

    // -------------------------------------------------------------- results ----

    private static void complete(ServerPlayerEntity player, Task task) {
        ACTIVE.remove(player.getUuid());
        sendToast(player, TaskToastPayload.KIND_COMPLETED, TITLE_DONE, taskTitle(task) + " — done.");
        // PHASES §2: wykonane zadanie -8 "It grows quiet" — no fleeting victory, tension falls
        if (Director.phase() == 1) Director.addTension(-8.0);
        putCooldown(player);
        ItWasTall.LOGGER.info("{} completed the {} task.", player.getName().getString(), task.kind);
    }

    private static void fail(ServerPlayerEntity player, Task task, MinecraftServer server) {
        ACTIVE.remove(player.getUuid());
        sendToast(player, TaskToastPayload.KIND_FAILED, TITLE_FAILED, taskTitle(task) + " — failed.");
        // PHASES §2: niewykonane zadanie +5
        if (Director.phase() == 1) Director.addTension(+5.0);
        // MODPLAN 15.1: "Wyjście może uruchomić atak" — on the hunter stages this wakes It up
        if (task.kind == Kind.STAY_INSIDE && Director.currentStage().ordinal() >= 4
                && player.getRandom().nextFloat() < 0.5f) {
            HauntDirector.spawnStare(player); // best effort: spawn needs good light
        }
        putCooldown(player);
        ItWasTall.LOGGER.info("{} failed the {} task.", player.getName().getString(), task.kind);
    }

    private static void putCooldown(ServerPlayerEntity player) {
        COOLDOWN.put(player.getUuid(), player.getWorld().getTime()
                + ItWasTallConfig.INSTANCE.taskCooldownSeconds * 20L);
    }

    private static void sendToast(ServerPlayerEntity player, int kind, String title, String text) {
        if (ModNetworking.scaresDisabled(player.getUuid())) return; // opt-out holds for tasks too
        ModNetworking.sendTaskToast(player, kind, title, text);
    }

    // -------------------------------------------------------------- helpers ----

    private static String taskTitle(Task task) {
        return switch (task.kind) {
            case STAY_INSIDE -> "don't go outside";
            case DOUSE_LIGHTS -> "douse the lights";
        };
    }

    private static String newToastText(Kind kind, Task task) {
        ItWasTallConfig.Values cfg = ItWasTallConfig.INSTANCE;
        return switch (kind) {
            case STAY_INSIDE -> "It is at the door. Stay inside for " + cfg.taskStayInsideSeconds + "s.";
            case DOUSE_LIGHTS -> "Douse the lights (" + task.lights.size() + ").";
        };
    }

    private static String taskDescription(Task task) {
        return switch (task.kind) {
            case STAY_INSIDE -> "STAY_INSIDE until " + task.expiresAt;
            case DOUSE_LIGHTS -> "DOUSE_LIGHTS (" + task.lights.size() + " lights left)";
        };
    }
}
