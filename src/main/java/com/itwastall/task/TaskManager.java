package com.itwastall.task;

import com.itwastall.ItWasTall;
import com.itwastall.base.BaseMap;
import com.itwastall.base.BaseTracker;
import com.itwastall.config.ItWasTallConfig;
import com.itwastall.director.Director;
import com.itwastall.director.Stage;
import com.itwastall.entity.HauntDirector;
import com.itwastall.entity.FalsePresences;
import com.itwastall.entity.MineEvents;
import com.itwastall.entity.ShelterCheck;
import com.itwastall.net.ModNetworking;
import com.itwastall.net.TaskToastPayload;
import com.itwastall.registry.ModSounds;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
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
 *   <li><b>CHOWANY</b> (HIDE_AND_SEEK, stage 5 / tasksFlag >= 3): a 30 s countdown — the
 *       player must stay put in the base — then It walks the mapped rooms: every few seconds
 *       one dark corner (TYPE_CORNER markers, MODPLAN §8.3) gets a step/breath cue, and if
 *       the player sits NEXT to the spot being checked, It founds them and the chase
 *       (HauntDirector.spawnStare) begins.</li>
 * </ul>
 *
 * <p>Assignments and their outcome leave through {@link TaskToastPayload}; the per-player
 * active task survives no restart (kept in server memory like the other directors) and one
 * failure/completion puts the player on a cooldown ({@code taskCooldownSeconds}).
 */
public final class TaskManager {
    public enum Kind {STAY_INSIDE, DOUSE_LIGHTS, HIDE_AND_SEEK, DONT_LOOK, SACRIFICE}

    /** The active task of one player (server-memory; tasks reset on restart). */
    private static final class Task {
        final Kind kind;
        /** World time when the task hard-expires — MUTABLE for hide-and-seek (starts as the
         *  hide countdown, then becomes the seek-phase deadline). */
        long expiresAt;
        /** DOUSE_LIGHTS: positions that must stop glowing. */
        final List<BlockPos> lights;
        /** HIDE_AND_SEEK: dark corners It checks, one at a time. */
        final List<BlockPos> corners;
        /** HIDE_AND_SEEK: world time the seek phase begins (0 while still hiding). */
        long seekStartAt;
        /** HIDE_AND_SEEK: next world time It checks the following corner. */
        long nextCheckAt;
        /** HIDE_AND_SEEK: how many corners have been checked so far. */
        int checked;
        /** STAY_INSIDE / hiding: seconds in a row spent outside (a short grace for stepping through a door). */
        int outsideSeconds;
        /** STAY_INSIDE: how long this one lasts (the window gaze gives a short 30 s one). */
        int seconds;
        /** World time the current phase began (task start; hide-and-seek: again when the seeking starts). */
        long phaseStart;
        /** STAY_INSIDE: false while the player still has to GET inside (phase A), true for the short stay (phase B). */
        boolean inside;
        /** True while the player is away from the Overworld: the task waits (its clocks are pushed back). */
        boolean paused;
        /** HIDE_AND_SEEK: distance from the player to the corner It checked last (blocks, -1 = none yet). */
        double lastCheckDist = -1;
        /** SACRIFICE (§15.5): what must be offered and where. */
        final net.minecraft.item.Item offerItem;
        final int offerCount;
        final net.minecraft.util.math.BlockPos offerChest;

        Task(Kind kind, long expiresAt, List<BlockPos> lights, List<BlockPos> corners) {
            this(kind, expiresAt, lights, corners, null, 0, null);
        }

        Task(Kind kind, long expiresAt, List<BlockPos> lights, List<BlockPos> corners,
             net.minecraft.item.Item offerItem, int offerCount, net.minecraft.util.math.BlockPos offerChest) {
            this.kind = kind;
            this.expiresAt = expiresAt;
            this.lights = lights;
            this.corners = corners;
            this.offerItem = offerItem;
            this.offerCount = offerCount;
            this.offerChest = offerChest;
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

    /** What the TASK TIMER in the corner of the screen shows. */
    public record Timer(String title, String detail, int left, int total, boolean danger, @org.jetbrains.annotations.Nullable BlockPos target) {
    }

    /** The TASK TIMER of a player's running task, or null when there is none. */
    public static Timer timerFor(UUID player, long now) {
        Task task = ACTIVE.get(player);
        if (task == null) return null;
        long start = task.phaseStart > 0 ? task.phaseStart : now;
        int left = (int) Math.max(0, (task.expiresAt - now + 19) / 20);
        int total = (int) Math.max(1, (task.expiresAt - start + 19) / 20);
        int lights = task.lights.size();
        String title = switch (task.kind) {
            case STAY_INSIDE -> task.inside ? "STAY INSIDE" : "GET INSIDE";
            case DOUSE_LIGHTS -> "DOUSE THE LIGHTS";
            case HIDE_AND_SEEK -> task.seekStartAt == 0 ? "HIDE" : "STAY HIDDEN";
            case DONT_LOOK -> "DON'T LOOK BACK";
            case SACRIFICE -> "THE OFFERING";
        };
        String detail = switch (task.kind) {
            case STAY_INSIDE -> task.inside ? "don't go out" : "a house or under a roof";
            case DOUSE_LIGHTS -> lights + " light" + (lights == 1 ? "" : "s") + " left";
            case HIDE_AND_SEEK -> task.seekStartAt == 0 ? "get inside, find a corner"
                    : task.lastCheckDist >= 0 && task.lastCheckDist <= 8 ? "It is close. Corner " + task.checked + "/" + task.corners.size()
                    : "It checks corner " + task.checked + "/" + task.corners.size();
            case DONT_LOOK -> "do not turn around";
            case SACRIFICE -> task.offerCount + "x " + task.offerItem.getName().getString();
        };
        if (task.paused) detail = "paused (away)";
        if (detail.length() > 90) detail = detail.substring(0, 90);
        boolean danger = task.kind == Kind.DOUSE_LIGHTS || task.kind == Kind.SACRIFICE
                || (task.kind == Kind.STAY_INSIDE && !task.inside);
        BlockPos target = task.kind == Kind.SACRIFICE ? task.offerChest : null;
        return new Timer(title, detail, left, total, danger, target);
    }

    /** The journal's "What it wants" line: plain words and the time left, e.g. "Don't go outside. 24 s left." */
    public static String journalLine(UUID player, long now) {
        Task task = ACTIVE.get(player);
        if (task == null) return "no task";
        long left = Math.max(0, (task.expiresAt - now) / 20);
        return switch (task.kind) {
            case STAY_INSIDE -> task.inside ? "Stay inside. Don't go out. " + left + " s left."
                    : "Get inside: a house or under a roof. " + left + " s left.";
            case DOUSE_LIGHTS -> "Put out " + task.lights.size() + " light" + (task.lights.size() == 1 ? "" : "s") + " in the house. " + left + " s left.";
            case HIDE_AND_SEEK -> task.seekStartAt == 0 ? "Hide. It starts looking in " + left + " s."
                    : "It is walking the rooms (corner " + task.checked + "/" + task.corners.size() + "). Stay hidden. " + left + " s left.";
            case DONT_LOOK -> "Something stands behind you. Do not turn around. " + left + " s left.";
            case SACRIFICE -> "Put " + task.offerCount + "x " + task.offerItem.getName().getString() + " in the chest at "
                    + task.offerChest.getX() + " " + task.offerChest.getY() + " " + task.offerChest.getZ() + ". " + left + " s left.";
        };
    }

    /** Force an assignment (commands); returns false when the context refuses it. */
    public static boolean forceAssign(ServerPlayerEntity player, @Nullable Kind requested) {
        // test command: works on every stage; replaces a running task
        Task running = ACTIVE.remove(player.getUuid());
        if (running != null) logTask(player, running, "cancelled", true);
        return assign(player, requested == null ? Kind.STAY_INSIDE : requested);
    }

    // -------------------------------------------------------------- ticking ----

    private static void tick(MinecraftServer server) {
        if (server.getTicks() % 20 != 0) return; // one decision tick per second is plenty
        long now = server.getOverworld().getTime();

        // 1) a RUNNING task always runs to its end: on every stage, in every phase and game mode (a task given
        //    by a command used to freeze on low stages / in creative: the timer sat at 0:00 forever)
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            Task task = ACTIVE.get(player.getUuid());
            if (task == null || !player.isAlive() || player.isSpectator()) continue;
            if (player.getWorld().getRegistryKey() != World.OVERWORLD) {
                // away (Nether, the dream): the task waits for you, its clocks stand still
                task.paused = true;
                task.expiresAt += 20;
                task.phaseStart += 20;
                if (task.nextCheckAt > 0) task.nextCheckAt += 20;
                continue;
            }
            task.paused = false;
            tickTask(player, task, now, server);
        }

        // 2) new assignments keep all their rules
        if (Director.phase() == 0) return;       // PHASE PRE: total silence
        Stage st = Director.currentStage();
        int flag = st.tasksFlag();               // live read: /itwastall stage works next tick
        if (flag <= 0) return;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!player.isAlive() || player.isCreative() || player.isSpectator()) continue;
            if (player.getWorld().getRegistryKey() != World.OVERWORLD) continue;
            if (ACTIVE.containsKey(player.getUuid())) continue;
            maybeAssign(player, st, flag, now);
        }
    }

    /**
     * "Don't go outside" in two steps: first GET inside (taskGetInsideSeconds, 120 s), then STAY inside for a
     * short while (seconds, 10 s). Already inside when it starts: straight to the short stay.
     */
    private static Task newStayInside(ServerPlayerEntity player, long now, int seconds) {
        Task task = new Task(Kind.STAY_INSIDE, 0, List.of(), List.of());
        task.seconds = Math.max(3, seconds);
        if (isInside(player)) {
            task.inside = true;
            task.expiresAt = now + 20L * task.seconds;
        } else {
            task.expiresAt = now + 20L * Math.max(10, ItWasTallConfig.INSTANCE.taskGetInsideSeconds);
        }
        return task;
    }

    /** Seconds outside before "don't go outside" / hiding fails (walking through your own door is fine). */
    private static final int OUTSIDE_GRACE_SECONDS = 3;

    /** "Inside" for the tasks: in your base (its mapped rooms, or right by the bed) OR anywhere built-in (roof + walls). */
    public static boolean isInside(ServerPlayerEntity player) {
        return ShelterCheck.isSheltered(player.getWorld(), player.getBlockPos()) || BaseTracker.isInBase(player);
    }

    private static void tickTask(ServerPlayerEntity player, Task task, long now, MinecraftServer server) {
        World world = player.getWorld();
        boolean sheltered = isInside(player);
        task.outsideSeconds = sheltered ? 0 : task.outsideSeconds + 1;
        switch (task.kind) {
            case STAY_INSIDE -> {
                if (!task.inside) {
                    // phase A: GET inside (base or under a roof) before the time runs out
                    if (sheltered) {
                        task.inside = true;
                        task.outsideSeconds = 0;
                        task.phaseStart = now;
                        task.expiresAt = now + 20L * task.seconds;
                        ModNetworking.sendTaskTimer(player);
                        sendToast(player, TaskToastPayload.KIND_NEW, "STAY INSIDE.", "Don't go out for " + task.seconds + "s.");
                    } else if (now >= task.expiresAt) {
                        fail(player, task, server);   // never made it inside
                    }
                    return;
                }
                // phase B (MODPLAN 15.1): leaving fails — in the base or under a roof counts as inside,
                // and a few seconds of grace cover stepping through a doorway
                if (task.outsideSeconds > OUTSIDE_GRACE_SECONDS) {
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
            case HIDE_AND_SEEK -> tickHideAndSeek(player, task, now, world, sheltered, server);
            case DONT_LOOK -> {
                // §15.4: the watcher stands behind the player for the fixed duration. Surviving
                // it completes the task; turning around is reported by the client renderer and
                // routes through onDontLookCaught -> fail below.
                if (now >= task.expiresAt) complete(player, task);
            }
            case SACRIFICE -> tickSacrifice(player, task, now, world, server);
        }
    }

    /**
     * §15.5 OFIARA: the designated chest must CONTAIN the required offering. It collects its
     * due (removes the stack) and falls silent; a broken casket robs the deal. The chest is
     * one of the player's mapped TYPE_CHEST markers — §8 feeding §15 again.
     */
    private static void tickSacrifice(ServerPlayerEntity player, Task task, long now,
                                      World world, MinecraftServer server) {
        if (world.getBlockState(task.offerChest).isAir()
                || !(world.getBlockEntity(task.offerChest) instanceof net.minecraft.block.entity.LootableContainerBlockEntity chest)) {
            fail(player, task, server);   // the agreed altar is gone
            return;
        }
        // count the required items (comparing the offered item id, ignoring components)
        int found = 0;
        for (int slot = 0; slot < chest.size(); slot++) {
            var stack = chest.getStack(slot);
            if (!stack.isEmpty() && stack.getItem() == task.offerItem) found += stack.getCount();
        }
        if (found < task.offerCount) {
            if (now >= task.expiresAt) fail(player, task, server);  // not in time
            return;
        }
        // It takes its due; the player keeps everything else untouched
        int left = task.offerCount;
        for (int slot = 0; slot < chest.size() && left > 0; slot++) {
            var stack = chest.getStack(slot);
            if (stack.isEmpty() || stack.getItem() != task.offerItem) continue;
            int take = Math.min(left, stack.getCount());
            stack.decrement(take);
            left -= take;
        }
        chest.markDirty();
        complete(player, task);
    }

    /**
     * §15.4 NIE PATRZ: the client renderer saw the player rotate onto the behind-figure.
     * Only meaningful when that player's active task really is DONT_LOOK (the window-look
     * gaze of §12 arrives through the same payload with {@code behind = false}).
     */
    public static void onDontLookCaught(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        Task task = ACTIVE.get(uuid);
        if (task == null || task.kind != Kind.DONT_LOOK) return;
        ItWasTall.LOGGER.info("{} looked at the watcher. It saw that.", player.getName().getString());
        fail(player, task, player.getServer());
        ModNetworking.sendJumpscare(player);   // it was RIGHT there when you turned around
    }

    /**
     * MODPLAN §15.2 CHOWANY: phase A = hide during the countdown (any move toward danger is
     * fine, but LEAVING the base fails like 15.1); phase B = It walks the rooms corner by
     * corner. The player is FOUND when he waits right next to the spot now being checked —
     * hiding in the checked spot's room with a wall between you is the intended safe play.
     */
    private static void tickHideAndSeek(ServerPlayerEntity player, Task task, long now,
                                        World world, boolean sheltered, MinecraftServer server) {
        ItWasTallConfig.Values cfg = ItWasTallConfig.INSTANCE;
        if (task.seekStartAt == 0) {
            // ---- phase A: the 30 s hide countdown (walk in, find a dark corner) ----
            if (now >= task.expiresAt) {
                if (!sheltered) {
                    fail(player, task, server);   // the search starts and you are still outside
                    return;
                }
                double tension = Director.tension();
                task.seekStartAt = now;
                task.nextCheckAt = now + 40;   // the first corner right away: you hear (and see) It walk at once
                task.expiresAt = now + 20L * cfg.taskSeekSeconds; // redefine: survive the seeking
                task.phaseStart = now;
                ModNetworking.sendTaskTimer(player);
                sendToast(player, TaskToastPayload.KIND_NEW, "IT ENTERS.",
                        "It walks the rooms. Stay hidden.");
                ItWasTall.LOGGER.info("Hide-and-seek seek phase started for {}.", player.getName().getString());
                // PHASES §2 hook: the dread of the search itself
                if (Director.phase() == 1) Director.addTension(1.0);
            }
            return;
        }

        // ---- phase B: search ----
        if (now >= task.nextCheckAt) {
            task.nextCheckAt = now + cfg.taskSeekCheckSeconds * 20L;
            int index = task.checked++;
            if (task.corners.size() == 0) {
                complete(player, task);   // no map to search: It gives up fast = success
                return;
            }
            BlockPos checking = task.corners.get(index % task.corners.size());
            double near = Math.sqrt(checking.getSquaredDistance(player.getBlockPos()));
            task.lastCheckDist = near;
            // the audible cue: a step and a breath AT the checked corner — the player who
            // listens knows exactly how close the search has come (louder when it is close)
            MineEvents.playTo(player, ModSounds.STEP,
                    new Vec3d(checking.getX() + 0.5, checking.getY() + 0.5, checking.getZ() + 0.5),
                    near <= 8 ? 1.3f : 0.8f, 0.85f + player.getRandom().nextFloat() * 0.2f);
            // and you can SEE it: It stands in that corner for the length of one check (gone when you look at it)
            float yaw = (float) (net.minecraft.util.math.MathHelper.atan2(player.getZ() - (checking.getZ() + 0.5), player.getX() - (checking.getX() + 0.5))
                    * (180.0 / Math.PI)) - 90f;
            ModNetworking.sendFalsePresence(player, checking.getX() + 0.5, checking.getY(), checking.getZ() + 0.5,
                    yaw, Math.max(20, cfg.taskSeekCheckSeconds * 20 - 5), false);
            ModNetworking.sendTaskTimer(player);
            if (player.getRandom().nextBoolean()) {
                MineEvents.playTo(player, ModSounds.AMBIENT_BREATH,
                        new Vec3d(checking.getX() + 0.5, checking.getY() + 0.5, checking.getZ() + 0.5),
                        0.7f, 1.0f);
            }
            double distance = Math.sqrt(checking.getSquaredDistance(player.getBlockPos()));
            if (distance <= cfg.taskSeekFindDistance) {
                // FOUND: "moze odnalesc gracza i rozpoczac poscig" (§15.2)
                ItWasTall.LOGGER.info("It found {} hiding at {}", player.getName().getString(), checking.toShortString());
                fail(player, task, server);
                if (Director.currentStage().tasksFlag() >= 3) {
                    HauntDirector.spawnStare(player); // the chase is born from the search itself
                    ModNetworking.sendJumpscare(player);
                }
                return;
            }
        }
        if (now >= task.expiresAt) complete(player, task); // It leaves without finding you
    }

    // ------------------------------------------------------------- assigning ----

    /**
     * WINDOW GAZE (§12 figure variant): you looked at the figure behind the glass — It noticed.
     * You get a short "don't go outside" (taskWindowLookSeconds, default 30 s): stay in the base or
     * under a roof until it is over. Works wherever the window was (your base or a village house);
     * an active task stays untouched, and the usual task cooldown does not block it.
     */
    public static void onWindowLooked(ServerPlayerEntity player) {
        com.itwastall.advancement.Achievements.grant(player, "in_the_window");
        if (Director.phase() == 0) return;      // PHASE PRE: total silence
        UUID uuid = player.getUuid();
        if (ACTIVE.containsKey(uuid)) return;   // already working: no double assignment
        if (player.isSpectator() || !player.isAlive()) return;
        int seconds = Math.max(3, ItWasTallConfig.INSTANCE.taskWindowLookSeconds);
        long now = player.getServer() != null ? player.getServer().getOverworld().getTime() : player.getWorld().getTime();
        Task task = newStayInside(player, now, seconds);
        task.phaseStart = now;
        ACTIVE.put(uuid, task);
        ModNetworking.sendTaskTimer(player);
        logTask(player, task, "now (the window)", false);
        sendToast(player, TaskToastPayload.KIND_NEW, "IT SAW YOU.", task.inside
                ? "You looked at it. Stay inside for " + task.seconds + "s."
                : "You looked at it. Get inside: " + (task.expiresAt - now) / 20 + "s.");
        ItWasTall.LOGGER.info("{} looked at the window figure: {} s inside.", player.getName().getString(), seconds);
    }

    private static void maybeAssign(ServerPlayerEntity player, Stage st, int flag, long now) {
        UUID uuid = player.getUuid();
        Long cooldownUntil = COOLDOWN.get(uuid);
        if (cooldownUntil != null && now < cooldownUntil) return;

        World world = player.getWorld();
        // tasks live at night (MODPLAN §15); day is for mining fear
        if (!world.isNight()) return;

        ItWasTallConfig.Values cfg = ItWasTallConfig.INSTANCE;
        if (player.getRandom().nextDouble() >= cfg.taskChancePerSecond) return;

        Kind wanted;
        boolean sheltered = isInside(player);
        if (!sheltered) {
            // NIE PATRZ (§15.4) works outdoors too — the watcher simply steps in behind you
            wanted = Kind.DONT_LOOK;
        } else {
            // fair pick from everything the stage enables (ETAP 4 adds NIE PATRZ, ETAP 5 HIDE);
            // lights/corners need a mapped base, STAY_INSIDE always fits — so fall back to it
            int pool = flag >= 3 ? 5 : flag >= 2 ? 4 : 2;
            wanted = switch (player.getRandom().nextInt(pool)) {
                case 0 -> Kind.STAY_INSIDE;
                case 2 -> Kind.DONT_LOOK;
                case 3 -> flag >= 3 ? Kind.HIDE_AND_SEEK : Kind.DOUSE_LIGHTS;
                case 4 -> Kind.SACRIFICE;
                default -> Kind.DOUSE_LIGHTS;
            };
        }
        // the wanted one first; if the place cannot host it (no lights, no corners, no chest here),
        // any other task this stage allows — so every task really happens, never a silent no-show
        List<Kind> order = new ArrayList<>();
        order.add(wanted);
        List<Kind> rest = new ArrayList<>(List.of(Kind.values()));
        java.util.Collections.shuffle(rest, new java.util.Random(player.getRandom().nextLong()));
        for (Kind k : rest) if (!order.contains(k)) order.add(k);
        for (Kind k : order) {
            if (!sheltered && k != Kind.DONT_LOOK) continue;          // outdoors only the watcher fits
            if (!allowedAt(k, flag)) continue;
            Kind kind = pickKind(player, k, cfg);
            if (kind == k && assign(player, kind)) {
                COOLDOWN.put(uuid, now + cfg.taskCooldownSeconds * 20L);
                return;
            }
        }
    }

    /** Which tasks a stage's tasksFlag unlocks: 1 = stay inside + lights, 2 = + don't look + offering, 3 = + hide and seek. */
    private static boolean allowedAt(Kind kind, int flag) {
        return switch (kind) {
            case STAY_INSIDE, DOUSE_LIGHTS -> flag >= 1;
            case DONT_LOOK, SACRIFICE -> flag >= 2;
            case HIDE_AND_SEEK -> flag >= 3;
        };
    }

    /** Chooses a kind the CONTEXT allows; {@code requested} wins when explicitly given.
     *  HIDE_AND_SEEK additionally needs tasksFlag >= 3 (ETAP 5, PHASES Poziomy list). */
    @Nullable
    private static Kind pickKind(ServerPlayerEntity player, @Nullable Kind requested,
                                 ItWasTallConfig.Values cfg) {
        Stage st = Director.currentStage();
        int flag = st.tasksFlag();
        if (requested == Kind.DOUSE_LIGHTS) {
            List<BlockPos> lights = pickLights(player, cfg);
            return lights == null ? null : Kind.DOUSE_LIGHTS;
        }
        if (requested == Kind.HIDE_AND_SEEK) {
            if (flag < 3) return null;
            List<BlockPos> corners = pickCorners(player);
            return corners == null ? null : Kind.HIDE_AND_SEEK;
        }
        if (requested == Kind.DONT_LOOK) {
            // ETAP 4 unlocks the watcher (§15.4); the behind-spot is validated in assign()
            return flag < 2 ? null : Kind.DONT_LOOK;
        }
        if (requested == Kind.SACRIFICE) {
            return flag < 2 ? null : Kind.SACRIFICE;   // ETAP 4+; the altar is chosen in assign()
        }
        if (requested == Kind.STAY_INSIDE) return Kind.STAY_INSIDE;
        return Kind.STAY_INSIDE; // stage-3 default: the safest first task
    }

    /**
     * Hides-skim spot pool: the base scan's dark corners (MODPLAN §8.3), shuffled so the
     * search feels room-walking rather than nearest-first. Null when nothing was mapped.
     */
    @Nullable
    private static List<BlockPos> pickCorners(ServerPlayerEntity player) {
        BaseMap map = BaseTracker.eventMapOf(player);
        if (map == null) return null;
        List<BlockPos> corners = new ArrayList<>(map.all(BaseMap.TYPE_CORNER));
        if (corners.size() < 4) {
            // a well-lit base has few dark corners: It walks the rooms instead (every 6th interior cell)
            List<BlockPos> rooms = map.all(BaseMap.TYPE_ROOM);
            for (int i = 0; i < rooms.size(); i += 6) corners.add(rooms.get(i));
        }
        if (corners.size() < 2) return null; // nowhere to search — refuse the task
        java.util.Collections.shuffle(corners, new java.util.Random(
                player.getServerWorld().getSeed()));
        return corners;
    }

    /**
     * Chooses the light markers It would personally extinguish (closest to the player).
     * Returns null when the mapping has no usable lights within reach.
     */
    @Nullable
    private static List<BlockPos> pickLights(ServerPlayerEntity player, ItWasTallConfig.Values cfg) {
        ServerWorld world = player.getServerWorld();
        BaseMap map = BaseTracker.eventMapOf(player);
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

    /** The designated altar (§15.5): the mapped chest/barelle nearest the player. */
    @Nullable
    private static net.minecraft.util.math.BlockPos pickChest(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        BaseMap map = BaseTracker.eventMapOf(player);
        if (map == null) return null;
        List<BlockPos> chests = new ArrayList<>(map.all(BaseMap.TYPE_CHEST));
        chests.removeIf(pos -> !(world.getBlockEntity(pos) instanceof net.minecraft.block.entity.LootableContainerBlockEntity));
        chests.sort((a, b) -> Double.compare(
                a.getSquaredDistance(player.getBlockPos()),
                b.getSquaredDistance(player.getBlockPos())));
        return chests.isEmpty() ? null : chests.get(player.getRandom().nextInt(Math.min(2, chests.size())));
    }

    /** The required offering — the first valid item from config (falling back to bread). */
    private static net.minecraft.item.Item pickOfferItem(ItWasTallConfig.Values cfg, ServerPlayerEntity player) {
        for (String raw : cfg.taskSacrificeItems.split(",")) {
            String name = raw.trim().toLowerCase(java.util.Locale.ROOT);
            if (name.isEmpty()) continue;
            net.minecraft.util.Identifier id = name.contains(":")
                    ? net.minecraft.util.Identifier.of(name)
                    : net.minecraft.util.Identifier.ofVanilla(name);
            if (id == null) continue;
            net.minecraft.item.Item item = net.minecraft.registry.Registries.ITEM.get(id);
            if (item != null && item != net.minecraft.item.Items.AIR) return item;
        }
        ItWasTall.LOGGER.warn("taskSacrificeItems had no valid item — defaulting to bread.");
        return net.minecraft.item.Items.BREAD;
    }

    private static boolean assign(ServerPlayerEntity player, Kind kind) {
        ItWasTallConfig.Values cfg = ItWasTallConfig.INSTANCE;
        World world = player.getWorld();
        long now = world.getTime();
        Task task;
        if (kind == Kind.DOUSE_LIGHTS) {
            List<BlockPos> lights = pickLights(player, cfg);
            if (lights == null) return false;
            task = new Task(Kind.DOUSE_LIGHTS, now + 20L * cfg.taskLightsDeadlineSeconds, lights, List.of());
        } else if (kind == Kind.HIDE_AND_SEEK) {
            List<BlockPos> corners = pickCorners(player);
            if (corners == null) return false;
            // expiresAt starts as the 30 s hide countdown (§15.2), later redefined to the seek end
            task = new Task(Kind.HIDE_AND_SEEK, now + 20L * cfg.taskHideCountdownSeconds, List.of(), corners);
        } else if (kind == Kind.DONT_LOOK) {
            BlockPos behind = FalsePresences.findSpotBehind(player, player.getRandom());
            if (behind == null) return false;   // no open room at your back: no watcher now
            // the watcher appears INSTANTLY and watches for the duration (§15.4: "np. 30 sekund")
            if (!FalsePresences.sendFigure(player, behind, (int) (20L * cfg.taskDontLookSeconds), false, true)) {
                return false;
            }
            MineEvents.playTo(player, ModSounds.STARE, new Vec3d(
                            behind.getX() + 0.5, behind.getY() + 1.0, behind.getZ() + 0.5),
                    0.9f, 1.0f);
            MineEvents.playTo(player, ModSounds.AMBIENT_BREATH, new Vec3d(
                            behind.getX() + 0.5, behind.getY() + 1.0, behind.getZ() + 0.5),
                    0.8f, 1.0f);
            task = new Task(Kind.DONT_LOOK, now + 20L * cfg.taskDontLookSeconds, List.of(), List.of());
        } else if (kind == Kind.SACRIFICE) {
            net.minecraft.util.math.BlockPos chest = pickChest(player);
            if (chest == null) return false;              // no mapped storage to mark an altar in
            net.minecraft.item.Item item = pickOfferItem(cfg, player);
            task = new Task(Kind.SACRIFICE,
                    now + 20L * cfg.taskSacrificeSeconds, List.of(), List.of(),
                    item, Math.max(1, cfg.taskSacrificeCount), chest);
            ItWasTall.LOGGER.info("Sacrifice altar at {} for {} x{}.",
                    chest.toShortString(), item, task.offerCount);
        } else {
            task = newStayInside(player, now, cfg.taskStayInsideSeconds);
        }
        task.phaseStart = now;
        ACTIVE.put(player.getUuid(), task);
        ModNetworking.sendTaskTimer(player);
        logTask(player, task, "now", false);
        sendToast(player, TaskToastPayload.KIND_NEW, TITLE_NEW, newToastText(kind, task));
        ItWasTall.LOGGER.info("Task assigned to {}: {} ({} light(s), {} corner(s)).",
                player.getName().getString(), task.kind, task.lights.size(), task.corners.size());
        return true;
    }

    // -------------------------------------------------------------- results ----

    private static void complete(ServerPlayerEntity player, Task task) {
        ACTIVE.remove(player.getUuid());
        ModNetworking.sendTaskTimer(player);
        logTask(player, task, "done", true);
        sendToast(player, TaskToastPayload.KIND_COMPLETED, TITLE_DONE, taskTitle(task) + " — done.");
        com.itwastall.advancement.Achievements.taskCompleted(player, task.kind.name());
        // PHASES §2: wykonane zadanie -8 "It grows quiet" — no fleeting victory, tension falls
        if (Director.phase() == 1) Director.addTension(-8.0);
        putCooldown(player);
        ItWasTall.LOGGER.info("{} completed the {} task.", player.getName().getString(), task.kind);
    }

    private static void fail(ServerPlayerEntity player, Task task, MinecraftServer server) {
        ACTIVE.remove(player.getUuid());
        ModNetworking.sendTaskTimer(player);
        logTask(player, task, "failed", true);
        sendToast(player, TaskToastPayload.KIND_FAILED, TITLE_FAILED, taskTitle(task) + " — failed.");
        com.itwastall.advancement.Achievements.grant(player, "task_failed");
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

    /** Journal task history (saved with the world): "Night 3 — don't go outside — done". */
    private static void logTask(ServerPlayerEntity player, Task task, String result, boolean replaceNewest) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        long night = player.getWorld().getTimeOfDay() / 24000L + 1;
        String what = taskTitle(task) + (task.kind == Kind.STAY_INSIDE && task.seconds > 0 ? " (" + task.seconds + "s)" : "");
        com.itwastall.world.ItWasTallState.getOrCreate(server)
                .putTaskLog(player.getUuid(), "Night " + night + " — " + what + " — " + result, replaceNewest, 16);
        ModNetworking.sendJournal(player);
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
            case HIDE_AND_SEEK -> "hide and seek";
            case DONT_LOOK -> "don't look back";
            case SACRIFICE -> "the offering";
        };
    }

    private static String newToastText(Kind kind, Task task) {
        ItWasTallConfig.Values cfg = ItWasTallConfig.INSTANCE;
        return switch (kind) {
            case STAY_INSIDE -> task.inside ? "It is at the door. Stay inside for " + task.seconds + "s."
                    : "It is coming. Get inside (a house or under a roof): " + cfg.taskGetInsideSeconds + "s.";
            case DOUSE_LIGHTS -> "Douse the lights (" + task.lights.size() + ").";
            case HIDE_AND_SEEK -> "HIDE. " + cfg.taskHideCountdownSeconds
                    + "s. It will walk every dark corner.";
            case DONT_LOOK -> "Something watches your back. Do NOT turn for "
                    + cfg.taskDontLookSeconds + "s.";
            case SACRIFICE -> "Offer " + task.offerCount + "x "
                    + task.offerItem.getName().getString() + " in the chest at "
                    + task.offerChest.getX() + " " + task.offerChest.getY() + " " + task.offerChest.getZ() + ".";
        };
    }

    private static String taskDescription(Task task) {
        return switch (task.kind) {
            case STAY_INSIDE -> "STAY_INSIDE until " + task.expiresAt;
            case DOUSE_LIGHTS -> "DOUSE_LIGHTS (" + task.lights.size() + " lights left)";
            case HIDE_AND_SEEK -> task.seekStartAt == 0 ? "HIDE_AND_SEEK (hiding)"
                    : "HIDE_AND_SEEK (It checked " + task.checked + " corner(s))";
            case DONT_LOOK -> "DONT_LOOK (survive without turning)";
            case SACRIFICE -> "SACRIFICE (" + task.offerCount + "x "
                    + task.offerItem.getName().getString() + ")";
        };
    }
}
