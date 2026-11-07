package com.itwastall.command;

import com.itwastall.config.ItWasTallConfig;
import com.itwastall.director.Director;
import com.itwastall.entity.FalsePresences;
import com.itwastall.entity.HauntDirector;
import com.itwastall.entity.MineEvents;
import com.itwastall.entity.ScareSounds;
import com.itwastall.entity.TallOneEntity;
import com.itwastall.entity.WindowKnocks;
import com.itwastall.net.ModNetworking;
import com.itwastall.registry.ModEntities;
import com.itwastall.task.TaskManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Heightmap;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * All commands work on yourself, or on other players with a target selector (also from the server console):
 * <pre>
 * /itwastall spawn [distance] [player]          - It in front of the player, staring at them
 * /itwastall haunt [players]                    - It appears the "natural" way (in the distance, in front of them)
 * /itwastall jumpscare [players]                - 0.1 s jumpscare
 * /itwastall scare [players]                    - a small scare sound
 * /itwastall message [players] [text]           - red on-screen message (default "don't hide. just run.")
 * /itwastall mine dig|sounds|torches|peek [player] - force a cave event
 * /itwastall stage                                - FAZE (PRE/ACTIVE), ETAP (0-5 + name), tension, itJoined
 * /itwastall stage <0-5>                          - set the stage immediately (runtime, no restart)
 * /itwastall tension                              - show the tension
 * /itwastall tension <+/-(number)>                - add/remove tension
 * /itwastall phase activate                       - force the IT JOINED sequence (PRE -> ACTIVE)
 * /itwastall status                               - how many Its exist on the server + phase/stage info
 * /itwastall event feint|tree|realknock [player]  - force one of the It tricks (fake attack, behind a tree, real knock)
 * /itwastall outspace [player] / outspace wake [player] - send someone into the dream world / wake them up
 * /itwastall debug entity [forget]                - where the real It is, its state and the world-data record
 * /itwastall debug events [player]                - every timer / cooldown of one player's events
 * </pre>
 * With {@code debugAllowedForNonOps} in the config, players without operator rights may use /itwastall debug ...
 */
public final class ItWasTallCommand {
    private ItWasTallCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess access,
                                CommandManager.RegistrationEnvironment env) {
        // the root is open to everyone only when debugAllowedForNonOps is on; then only /itwastall debug ...
        // is usable without operator rights (MODPLAN §19), every other branch still needs level 2
        // GOD MODE: an operator stops playing and runs the night for the others (toggle)
        dispatcher.register(literal("changetogod")
                .requires(src -> src.hasPermissionLevel(2))
                .executes(ctx -> {
                    com.itwastall.god.GodMode.toggle(ctx.getSource().getPlayerOrThrow());
                    return 1;
                }));
        dispatcher.register(literal("itwastall")
                .requires(src -> src.hasPermissionLevel(2) || ItWasTallConfig.INSTANCE.debugAllowedForNonOps)
                .then(op(literal("spawn")
                        .executes(ctx -> spawn(ctx.getSource(), ctx.getSource().getPlayerOrThrow(), 30))
                        .then(argument("distance", IntegerArgumentType.integer(4, 96))
                                .executes(ctx -> spawn(ctx.getSource(), ctx.getSource().getPlayerOrThrow(), IntegerArgumentType.getInteger(ctx, "distance")))
                                .then(argument("player", EntityArgumentType.player())
                                        .executes(ctx -> spawn(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"),
                                                IntegerArgumentType.getInteger(ctx, "distance")))))))
                .then(op(forPlayers("haunt", p -> {
                    HauntDirector.clearForCommand(p.getServer());
                    if (!HauntDirector.spawnStare(p)) throw new IllegalStateException("no free spot with a clear view in front of the player");
                    HauntDirector.markNewForced(p.getServer());
                })))
                .then(op(forPlayers("jumpscare", p -> ModNetworking.sendJumpscare(p))))
                .then(op(forPlayers("scare", p -> ScareSounds.playRandomScare(p))))
                .then(op(literal("message")
                        .executes(ctx -> each(ctx, List.of(ctx.getSource().getPlayerOrThrow()), "message",
                                p -> ScareSounds.showMessage(p, ItWasTallConfig.INSTANCE.scareText)))
                        .then(argument("players", EntityArgumentType.players())
                                .executes(ctx -> each(ctx, EntityArgumentType.getPlayers(ctx, "players"), "message",
                                        p -> ScareSounds.showMessage(p, ItWasTallConfig.INSTANCE.scareText)))
                                .then(argument("text", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            String text = StringArgumentType.getString(ctx, "text");
                                            return each(ctx, EntityArgumentType.getPlayers(ctx, "players"), "message", p -> ScareSounds.showMessage(p, text));
                                        })))))
                .then(op(literal("outspace")
                        .executes(ctx -> each(ctx, List.of(ctx.getSource().getPlayerOrThrow()), "outspace", ItWasTallCommand::dream))
                        .then(argument("player", EntityArgumentType.player())
                                .executes(ctx -> each(ctx, List.of(EntityArgumentType.getPlayer(ctx, "player")), "outspace", ItWasTallCommand::dream)))
                        .then(literal("wake")
                                .executes(ctx -> each(ctx, List.of(ctx.getSource().getPlayerOrThrow()), "outspace wake", ItWasTallCommand::wake))
                                .then(argument("player", EntityArgumentType.player())
                                        .executes(ctx -> each(ctx, List.of(EntityArgumentType.getPlayer(ctx, "player")), "outspace wake", ItWasTallCommand::wake))))))
                .then(op(mineCommand()))
                .then(op(eventCommand()))
                .then(op(taskCommand()))
                .then(debugCommand())
                .then(op(baseCommand()))
                .then(op(literal("stage")
                        .executes(ctx -> {
                            ctx.getSource().sendFeedback(() -> Text.literal(Director.statusLine()), false);
                            return Director.stageIndex();
                        })
                        .then(argument("stage", IntegerArgumentType.integer(0, 5))
                                .executes(ctx -> {
                                    int n = IntegerArgumentType.getInteger(ctx, "stage");
                                    Director.setStage(n);
                                    String feedback = "Stage set to " + n + " (" + Director.currentStage().displayName() + ")."
                                            + " Applies from the next tick, no restart needed.";
                                    ctx.getSource().sendFeedback(() -> Text.literal(feedback), false);
                                    return n;
                                }))))
                .then(op(literal("tension")
                        .executes(ctx -> {
                            double v = Director.tension();
                            ctx.getSource().sendFeedback(() -> Text.literal("Tension: " + fmt(v) + " / 100 (stage " + Director.stageIndex() + ")"), false);
                            return (int) v; // narrow: brigadier wants an int status code here
                        })
                        .then(argument("amount", StringArgumentType.word())
                                .executes(ctx -> {
                                    double amount = parseSigned(StringArgumentType.getString(ctx, "amount"));
                                    Director.addTension(amount);
                                    ctx.getSource().sendFeedback(() -> Text.literal("Tension " + (amount >= 0 ? "+ " : "- ") + fmt(Math.abs(amount))
                                            + " -> " + fmt(Director.tension()) + " / 100 (stage " + Director.stageIndex() + ")"), false);
                                    return 1;
                                }))))
                .then(op(literal("phase")
                        .then(literal("activate")
                                .executes(ctx -> {
                                    boolean ok = Director.activatePhase(ctx.getSource().getServer());
                                    if (ok) {
                                        ctx.getSource().sendFeedback(() -> Text.literal("Phase activated — IT JOINED broadcast to everyone."), false);
                                    } else {
                                        ctx.getSource().sendError(Text.literal("Phase is already ACTIVE."));
                                    }
                                    return ok ? 1 : 0;
                                }))))
                .then(op(literal("status").executes(ctx -> {
                    int n = HauntDirector.countActive(ctx.getSource().getServer());
                    ctx.getSource().sendFeedback(() -> Text.literal("Its on the server: " + n + " / " + ItWasTallConfig.INSTANCE.maxActiveIts), false);
                    ctx.getSource().sendFeedback(() -> Text.literal(Director.statusLine()), false);
                    return n;
                }))));
    }

    /** Command readout: PHASE, ETAP (index + name), tension, itJoined. */
    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    /** "/itwastall tension <amount>": accept "10", "+10", "-7.5". */
    private static double parseSigned(String raw) {
        String s = raw.trim();
        try {
            if (s.startsWith("+")) s = s.substring(1);
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Not a number: " + raw);
        }
    }

    private interface PlayerAction {
        void run(ServerPlayerEntity player) throws Exception;
    }

    /** "/itwastall name" for yourself and "/itwastall name <players>" for others. */
    private static LiteralArgumentBuilder<ServerCommandSource> forPlayers(String name, PlayerAction action) {
        return literal(name)
                .executes(ctx -> each(ctx, List.of(ctx.getSource().getPlayerOrThrow()), name, action))
                .then(argument("players", EntityArgumentType.players())
                        .executes(ctx -> each(ctx, EntityArgumentType.getPlayers(ctx, "players"), name, action)));
    }

    private static int each(CommandContext<ServerCommandSource> ctx, Collection<ServerPlayerEntity> players, String what, PlayerAction action) {
        int ok = 0;
        for (ServerPlayerEntity p : players) {
            try {
                action.run(p);
                ok++;
            } catch (Exception e) {
                ctx.getSource().sendError(Text.literal(what + " failed for " + p.getName().getString() + ": " + e.getMessage()));
            }
        }
        int done = ok;
        ctx.getSource().sendFeedback(() -> Text.literal(what + ": " + done + " player(s)"), false);
        return ok;
    }

    /**
     * "/itwastall event <kind>" — force an event on demand (MODPLAN §21 test list):
     * falsepresence = the client-side False Presence figure, knock = three knocks on a window.
     * Both bypass the per-night ledger; normal stage/day-time rules still gate the placement.
     */
    private static LiteralArgumentBuilder<ServerCommandSource> eventCommand() {
        var root = literal("event");
        root.then(eventKind("falsepresence", p -> {
                    if (!FalsePresences.trigger(p)) throw new IllegalStateException("no spot for a false presence here");
                }))
                .then(eventKind("knock", p -> {
                    if (!WindowKnocks.trigger(p)) throw new IllegalStateException("no window near the player's base");
                }))
                .then(eventKind("feint", p -> {
                    HauntDirector.clearForCommand(p.getServer());
                    TallOneEntity it = spawnAt(p, 22);
                    if (it == null) throw new IllegalStateException("no room 22 blocks in front of the player");
                    it.setForced(true);
                    it.beginFeint(p);
                }))
                .then(eventKind("tree", p -> {
                    HauntDirector.clearForCommand(p.getServer());
                    if (!HauntDirector.spawnBehindTree(p, 10, 40)) throw new IllegalStateException("no tree with a 3-log trunk 10-40 blocks in front of the player");
                    HauntDirector.markNewForced(p.getServer());
                }))
                .then(eventKind("realknock", p -> {
                    HauntDirector.clearForCommand(p.getServer());
                    if (!com.itwastall.entity.HouseEvents.realKnockNearest(p)) throw new IllegalStateException("no door near the player with room outside (base or area map)");
                    HauntDirector.markNewForced(p.getServer());
                }))
                .then(eventKind("deepone", p -> {
                    if (!com.itwastall.entity.DeepOneEntity.spawnFor(p)) throw new IllegalStateException("no ceiling in sight to hang from");
                }))
                .then(eventKind("sleeper", p -> {
                    if (!com.itwastall.entity.SleeperEntity.placeFor(p)) throw new IllegalStateException("no room for a bed 3 blocks in front of the player");
                }))
                .then(eventKind("guest", p -> {
                    com.itwastall.entity.TheGuest.stop(p);
                    if (!com.itwastall.entity.TheGuest.start(p)) throw new IllegalStateException("could not start the guest");
                }))
                .then(eventKind("chopping", p -> {
                    if (!com.itwastall.entity.DynamicStructures.buildChoppingInFront(p)) throw new IllegalStateException("no solid ground in front of the player");
                }))
                .then(eventKind("mimic", p -> {
                    if (com.itwastall.entity.Mimics.spawnFor(p, 3) == 0) throw new IllegalStateException("no free ground around the player (or 3 mimics are already near)");
                }))
                .then(eventKind("hanged", p -> {
                    if (!com.itwastall.entity.HangedTrees.spawnFor(p, 8, 40, false)) throw new IllegalStateException("no tree with a free branch nearby");
                }))
                .then(eventKind("count", p -> {
                    com.itwastall.entity.TheCount.stop(p);
                    if (!com.itwastall.entity.TheCount.start(p)) throw new IllegalStateException("could not start counting");
                }))
                .then(eventKind("oldone", p -> {
                    if (!com.itwastall.entity.OldOneDirector.spawnFor(p)) throw new IllegalStateException("no free spot near the player");
                }))
                .then(houseKind("doorknock", com.itwastall.entity.HouseEvents.Kind.DOOR_KNOCK, "no mapped door near the player"))
                .then(houseKind("shadow", com.itwastall.entity.HouseEvents.Kind.SHADOW, "no mapped door near the player"))
                .then(houseKind("knockinside", com.itwastall.entity.HouseEvents.Kind.KNOCK_INSIDE, "no dark corner mapped in the base"))
                .then(houseKind("door", com.itwastall.entity.HouseEvents.Kind.DOOR_OPEN, "no closed wooden door you are not looking at"))
                .then(houseKind("chest", com.itwastall.entity.HouseEvents.Kind.CHEST, "no mapped chest with items, out of sight"))
                .then(houseKind("sign", com.itwastall.entity.HouseEvents.Kind.SIGN, "no free spot for a sign outside the base"))
                .then(literal("structure")
                        .then(structureKind("cemetery", com.itwastall.entity.DynamicStructures.Kind.CEMETERY))
                        .then(structureKind("shrine", com.itwastall.entity.DynamicStructures.Kind.SHRINE))
                        .then(structureKind("cottage", com.itwastall.entity.DynamicStructures.Kind.COTTAGE))
                        .then(structureKind("blackcopy", com.itwastall.entity.DynamicStructures.Kind.BLACKCOPY))
                        .then(structureKind("countingstones", com.itwastall.entity.DynamicStructures.Kind.COUNTING_STONES))
                        .then(structureKind("guesthouse", com.itwastall.entity.DynamicStructures.Kind.GUEST_HOUSE))
                        .then(structureKind("dreamwell", com.itwastall.entity.DynamicStructures.Kind.DREAM_WELL))
                        .then(structureKind("choppingblock", com.itwastall.entity.DynamicStructures.Kind.CHOPPING_BLOCK))
                        .then(structureKind("emptycamp", com.itwastall.entity.DynamicStructures.Kind.EMPTY_CAMP))
                        .then(structureKind("missing", com.itwastall.entity.DynamicStructures.Kind.MISSING_POSTERS))
                        .then(structureKind("animalcircle", com.itwastall.entity.DynamicStructures.Kind.ANIMAL_CIRCLE)))
                .then(literal("scene")
                        .then(sceneKind("chopping", com.itwastall.entity.DynamicStructures.Kind.CHOPPING_BLOCK, 5))
                        .then(sceneKind("camp", com.itwastall.entity.DynamicStructures.Kind.EMPTY_CAMP, 7))
                        .then(sceneKind("missing", com.itwastall.entity.DynamicStructures.Kind.MISSING_POSTERS, 10))
                        .then(sceneKind("circle", com.itwastall.entity.DynamicStructures.Kind.ANIMAL_CIRCLE, 8))
                        .then(eventKind("footprints", p -> {
                            if (!com.itwastall.entity.TallFootprints.walkToDoor(p)) throw new IllegalStateException("no mapped door in your base");
                        })))
                // any of the world-generated structures, right in front of the player (test/explore)
                .then(literal("build")
                        .then(argument("name", StringArgumentType.word())
                                .suggests((ctx, b) -> net.minecraft.command.CommandSource.suggestMatching(com.itwastall.world.StructureBuilder.NAMES, b))
                                .executes(ctx -> build(ctx, ctx.getSource().getPlayerOrThrow(), StringArgumentType.getString(ctx, "name")))
                                .then(argument("player", EntityArgumentType.player())
                                        .executes(ctx -> build(ctx, EntityArgumentType.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "name"))))));
        return root;
    }

    private static LiteralArgumentBuilder<ServerCommandSource> houseKind(String name, com.itwastall.entity.HouseEvents.Kind kind, String failure) {
        return eventKind(name, p -> {
            if (!com.itwastall.entity.HouseEvents.trigger(p, kind)) throw new IllegalStateException(failure);
        });
    }

    private static LiteralArgumentBuilder<ServerCommandSource> eventKind(String name, PlayerAction action) {
        return literal(name)
                .executes(ctx -> each(ctx, List.of(ctx.getSource().getPlayerOrThrow()), "event " + name, action))
                .then(argument("player", EntityArgumentType.player())
                        .executes(ctx -> each(ctx, List.of(EntityArgumentType.getPlayer(ctx, "player")),
                                "event " + name, action)));
    }

    /** /itwastall event scene X [player] — build a night scene a few blocks in front of the player (tests). */
    private static LiteralArgumentBuilder<ServerCommandSource> sceneKind(
            String name, com.itwastall.entity.DynamicStructures.Kind kind, int distance) {
        return eventKind(name, p -> {
            if (!com.itwastall.entity.DynamicStructures.buildInFront(p, kind, distance)) throw new IllegalStateException("no solid ground in front of the player");
        });
    }

    /** /itwastall event structure X [player] — force-build one of the §17.1 structures. */
    private static LiteralArgumentBuilder<ServerCommandSource> structureKind(
            String name, com.itwastall.entity.DynamicStructures.Kind kind) {
        String what = "event structure " + name;
        return literal(name)
                .executes(ctx -> each(ctx, List.of(ctx.getSource().getPlayerOrThrow()), what,
                        p -> {
                            if (!com.itwastall.entity.DynamicStructures.trigger(p, kind)
                                    && kind == com.itwastall.entity.DynamicStructures.Kind.BLACKCOPY) {
                                throw new IllegalStateException("no mapped base or no room behind the player");
                            }
                        }))
                .then(argument("player", EntityArgumentType.player())
                        .executes(ctx -> each(ctx, List.of(EntityArgumentType.getPlayer(ctx, "player")), what,
                                p -> com.itwastall.entity.DynamicStructures.trigger(p, kind))));
    }

    /**
     * "/itwastall task" — status of your current task slot;
     * "/itwastall task stayinside|lights [player]" — force-assign one of the stage-3 tasks.
     * Still respects the stage gate (tasksFlag) so /task never bypasses the balance table.
     */
    private static LiteralArgumentBuilder<ServerCommandSource> taskCommand() {
        var root = literal("task")
                .executes(ctx -> {
                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                    ctx.getSource().sendFeedback(() -> Text.literal(
                            "Task: " + TaskManager.status(p.getUuid())), false);
                    return 1;
                })
                .then(literal("stayinside")
                        .executes(ctx -> forceTask(ctx, List.of(ctx.getSource().getPlayerOrThrow()),
                                TaskManager.Kind.STAY_INSIDE))
                        .then(argument("player", EntityArgumentType.player())
                                .executes(ctx -> forceTask(ctx,
                                        List.of(EntityArgumentType.getPlayer(ctx, "player")),
                                        TaskManager.Kind.STAY_INSIDE))))
                .then(literal("lights")
                        .executes(ctx -> forceTask(ctx, List.of(ctx.getSource().getPlayerOrThrow()),
                                TaskManager.Kind.DOUSE_LIGHTS))
                        .then(argument("player", EntityArgumentType.player())
                                .executes(ctx -> forceTask(ctx,
                                        List.of(EntityArgumentType.getPlayer(ctx, "player")),
                                        TaskManager.Kind.DOUSE_LIGHTS))))
                .then(literal("hide")
                        .executes(ctx -> forceTask(ctx, List.of(ctx.getSource().getPlayerOrThrow()),
                                TaskManager.Kind.HIDE_AND_SEEK))
                        .then(argument("player", EntityArgumentType.player())
                                .executes(ctx -> forceTask(ctx,
                                        List.of(EntityArgumentType.getPlayer(ctx, "player")),
                                        TaskManager.Kind.HIDE_AND_SEEK))))
                .then(literal("dontlook")
                        .executes(ctx -> forceTask(ctx, List.of(ctx.getSource().getPlayerOrThrow()),
                                TaskManager.Kind.DONT_LOOK))
                        .then(argument("player", EntityArgumentType.player())
                                .executes(ctx -> forceTask(ctx,
                                        List.of(EntityArgumentType.getPlayer(ctx, "player")),
                                        TaskManager.Kind.DONT_LOOK))))
                .then(literal("sacrifice")
                        .executes(ctx -> forceTask(ctx, List.of(ctx.getSource().getPlayerOrThrow()),
                                TaskManager.Kind.SACRIFICE))
                        .then(argument("player", EntityArgumentType.player())
                                .executes(ctx -> forceTask(ctx,
                                        List.of(EntityArgumentType.getPlayer(ctx, "player")),
                                        TaskManager.Kind.SACRIFICE))));
        return root;
    }

    private static int forceTask(CommandContext<ServerCommandSource> ctx,
                                 Collection<ServerPlayerEntity> players, TaskManager.Kind kind) {
        int ok = 0;
        for (ServerPlayerEntity p : players) {
            if (TaskManager.forceAssign(p, kind)) {
                ok++;
            } else {
                ctx.getSource().sendError(Text.literal("task refused for " + p.getName().getString()
                        + " (nothing for it here: no lights / dark corners / chest mapped nearby, or no room behind you)"));
            }
        }
        int done = ok;
        ctx.getSource().sendFeedback(() -> Text.literal("task " + kind.name().toLowerCase(Locale.ROOT)
                + ": assigned to " + done + "/" + players.size() + " player(s)"), false);
        return ok;
    }

    /** "/itwastall base rescan" — force a fresh scan of your base (needs a known anchor). */
    private static LiteralArgumentBuilder<ServerCommandSource> baseCommand() {
        return literal("base")
                .then(literal("rescan")
                        .executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            if (!com.itwastall.base.BaseTracker.triggerRescan(p)) {
                                throw new IllegalStateException("no known base anchor (sleep in a bed first)");
                            }
                            ctx.getSource().sendFeedback(() -> Text.literal("Base rescan queued (runs gradually)."), false);
                            return 1;
                        }));
    }

    /** Operator-only branch of /itwastall. */
    private static <T extends com.mojang.brigadier.builder.ArgumentBuilder<ServerCommandSource, T>> T op(T builder) {
        return builder.requires(src -> src.hasPermissionLevel(2));
    }

    /** "/itwastall debug base" + "/itwastall debug view" — MODPLAN §8.5 debug suite. */
    private static LiteralArgumentBuilder<ServerCommandSource> debugCommand() {
        return literal("debug")
                .then(literal("base")
                        .executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            ctx.getSource().sendFeedback(() -> Text.literal(
                                    com.itwastall.base.BaseTracker.summary(p.getUuid())), false);
                            ctx.getSource().sendFeedback(() -> Text.literal(
                                    "pending scan columns: " + com.itwastall.base.BaseTracker.pendingWork(p.getUuid())), false);
                            for (String line : com.itwastall.base.BaseTracker.markerSample(p.getUuid())) {
                                ctx.getSource().sendFeedback(() -> Text.literal(line), false);
                            }
                            for (String line : com.itwastall.base.BaseTracker.clusterSummary(p)) {
                                ctx.getSource().sendFeedback(() -> Text.literal(line), false);
                            }
                            ctx.getSource().sendFeedback(() -> Text.literal(com.itwastall.base.BaseTracker.areaSummary(p)), false);
                            // §8.5: "debug base" also shows the coloured frames in the world
                            if (ModNetworking.sendBaseDebug(p, false)) {
                                ctx.getSource().sendFeedback(() -> Text.literal(
                                        "Frames shown for 30 s: windows red, doors green, lights yellow, chests orange, dark corners purple."), false);
                            }
                            return 1;
                        }))
                .then(literal("view")
                        .executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            if (!ModNetworking.sendBaseDebug(p, true)) {
                                throw new IllegalStateException("no mapped base yet (click a bed, wait for the scan)");
                            }
                            return 1;
                        }))
                // MODPLAN §21: where the real It is, what it is doing, who it is after
                .then(literal("entity")
                        .executes(ctx -> debugEntity(ctx.getSource()))
                        .then(op(literal("forget").executes(ctx -> {
                            com.itwastall.world.ItWasTallState.getOrCreate(ctx.getSource().getServer()).forgetActiveIt();
                            ctx.getSource().sendFeedback(() -> Text.literal("Forgot the remembered It (the slot is free)."), true);
                            return 1;
                        }))))
                // MODPLAN §21: every timer and cooldown of one player's events
                .then(literal("events")
                        .executes(ctx -> debugEvents(ctx.getSource(), ctx.getSource().getPlayerOrThrow()))
                        .then(argument("player", EntityArgumentType.player())
                                .executes(ctx -> debugEvents(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player")))));
    }

    private static int debugEntity(ServerCommandSource source) {
        var server = source.getServer();
        var state = com.itwastall.world.ItWasTallState.getOrCreate(server);
        long now = server.getOverworld().getTime();
        int n = 0;
        for (ServerWorld w : server.getWorlds()) {
            for (TallOneEntity it : w.getEntitiesByType(ModEntities.TALL_ONE, e -> true)) {
                n++;
                var victim = it.getVictimUuid() == null ? null : server.getPlayerManager().getPlayer(it.getVictimUuid());
                String who = victim == null ? "nobody" : victim.getName().getString() + " (" + (int) it.distanceTo(victim) + " m)";
                String line = "It " + it.getUuidAsString().substring(0, 8) + " in " + w.getRegistryKey().getValue()
                        + " at " + it.getBlockPos().toShortString() + " — " + it.getMode() + " for " + it.modeTicks() / 20 + " s, after " + who
                        + (it.isInvisible() ? ", invisible" : "");
                source.sendFeedback(() -> Text.literal(line), false);
            }
            for (com.itwastall.entity.SleeperEntity sl : w.getEntitiesByType(ModEntities.SLEEPER, e -> true)) {
                String line = "Sleeper at " + sl.getBlockPos().toShortString() + " in " + w.getRegistryKey().getValue() + (sl.isAwake() ? " — awake" : " — asleep");
                source.sendFeedback(() -> Text.literal(line), false);
            }
            for (com.itwastall.entity.DeepOneEntity d : w.getEntitiesByType(ModEntities.DEEP_ONE, e -> true)) {
                String line = "Deep One at " + d.getBlockPos().toShortString();
                source.sendFeedback(() -> Text.literal(line), false);
            }
            for (com.itwastall.entity.OldOneEntity one : w.getEntitiesByType(ModEntities.OLD_ONE, e -> true)) {
                String line = "Old One at " + one.getBlockPos().toShortString() + (one.isSplitting() ? " — tearing open" : " — standing");
                source.sendFeedback(() -> Text.literal(line), false);
            }
        }
        if (state.activeIt() != null) {
            String rec = "World data: It " + state.activeIt().toString().substring(0, 8) + " in " + state.activeItDim()
                    + " at " + net.minecraft.util.math.BlockPos.fromLong(state.activeItPos()).toShortString()
                    + ", last seen " + (now - state.activeItSeen()) / 20 + " s ago"
                    + (state.activeItFresh(now) ? " (counts against the limit)" : " (stale, does not count)");
            source.sendFeedback(() -> Text.literal(rec), false);
        } else {
            source.sendFeedback(() -> Text.literal("World data: no It remembered."), false);
        }
        int loaded = n;
        source.sendFeedback(() -> Text.literal("Loaded Its: " + loaded + ", counted: " + HauntDirector.countActive(server)
                + " / " + Math.max(1, ItWasTallConfig.INSTANCE.maxActiveIts)), false);
        return 1;
    }

    private static String nearestIt(ServerPlayerEntity p) {
        double best = Double.MAX_VALUE;
        for (TallOneEntity it : p.getServerWorld().getEntitiesByType(ModEntities.TALL_ONE, e -> true)) best = Math.min(best, it.distanceTo(p));
        return best == Double.MAX_VALUE ? "" : " (nearest It " + (int) best + " m away)";
    }

    private static int debugEvents(ServerCommandSource source, ServerPlayerEntity p) {
        long now = p.getServerWorld().getServer().getOverworld().getTime();
        java.util.UUID u = p.getUuid();
        java.util.function.LongFunction<String> in = at -> at < 0 ? "not scheduled" : at <= now ? "due now" : "in " + (at - now) / 20 + " s";
        List<String> lines = List.of(
                "Events of " + p.getName().getString() + " — phase " + (Director.phase() == 0 ? "PRE" : "ACTIVE") + ", stage " + Director.stageIndex(),
                "  real It counted against the limit: " + HauntDirector.countActive(p.getServer()) + " / " + Math.max(1, ItWasTallConfig.INSTANCE.maxActiveIts)
                        + nearestIt(p),
                "  shared event gap (minEventCooldown): " + (com.itwastall.entity.EventGate.secondsLeft(p) == 0 ? "ready" : com.itwastall.entity.EventGate.secondsLeft(p) + " s left"),
                "  haunting (It in the distance): " + in.apply(HauntDirector.nextAt(u)),
                "  Old One: " + in.apply(com.itwastall.entity.OldOneDirector.nextAt(u)),
                "  the hanged: " + in.apply(com.itwastall.entity.HangedTrees.nextAt(u)),
                "  tall footprints: " + in.apply(com.itwastall.entity.TallFootprints.nextAt(u)),
                "  mimics: " + in.apply(com.itwastall.entity.Mimics.nextAt(u)),
                "  house events: " + in.apply(com.itwastall.entity.HouseEvents.nextAt(u)),
                "  small scare: " + in.apply(ScareSounds.nextScareAt(u)),
                "  window knocks tonight: " + WindowKnocks.ledgerCount(u),
                "  false presences tonight: " + FalsePresences.ledgerCount(u),
                "  lights doused tonight: " + com.itwastall.entity.DouseLights.ledgerCount(u),
                "  structures today: " + com.itwastall.entity.DynamicStructures.ledgerCount(u),
                "  mine: " + MineEvents.debugLine(u),
                "  dream: " + com.itwastall.entity.Outspace.debugLine(p),
                "  the one who counts: " + com.itwastall.entity.TheCount.debugLine(u, now),
                "  the guest: " + com.itwastall.entity.TheGuest.debugLine(u, now),
                "  task: " + TaskManager.status(u));
        for (String line : lines) source.sendFeedback(() -> Text.literal(line), false);
        return 1;
    }

    private static LiteralArgumentBuilder<ServerCommandSource> mineCommand() {
        var root = literal("mine");
        for (MineEvents.Kind kind : MineEvents.Kind.values()) {
            String name = kind.name().toLowerCase(Locale.ROOT);
            PlayerAction action = p -> {
                if (kind == MineEvents.Kind.DIG || kind == MineEvents.Kind.PEEK) HauntDirector.clearForCommand(p.getServer());
                if (!MineEvents.trigger(p, kind)) throw new IllegalStateException("can't start " + name + " here (needs to be underground)");
                HauntDirector.markNewForced(p.getServer());
            };
            root.then(literal(name)
                    .executes(ctx -> each(ctx, List.of(ctx.getSource().getPlayerOrThrow()), "mine " + name, action))
                    .then(argument("player", EntityArgumentType.player())
                            .executes(ctx -> each(ctx, List.of(EntityArgumentType.getPlayer(ctx, "player")), "mine " + name, action))));
        }
        return root;
    }

    private static void dream(ServerPlayerEntity p) {
        if (!com.itwastall.entity.Outspace.enter(p)) throw new IllegalStateException("already dreaming, or the outspace dimension is missing");
    }

    private static void wake(ServerPlayerEntity p) {
        if (!com.itwastall.entity.Outspace.inOutspace(p)) throw new IllegalStateException("not dreaming");
        com.itwastall.entity.Outspace.wake(p, false);
    }

    private static int build(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity p, String name) {
        if (!com.itwastall.world.StructureBuilder.NAMES.contains(name)) {
            ctx.getSource().sendError(Text.literal("Unknown structure '" + name + "'. Try: " + String.join(", ", com.itwastall.world.StructureBuilder.NAMES)));
            return 0;
        }
        if (!com.itwastall.world.StructureBuilder.placeInFront(p, name)) {
            ctx.getSource().sendError(Text.literal("Could not place " + name + " (template missing?)"));
            return 0;
        }
        ctx.getSource().sendFeedback(() -> Text.literal("Built " + name + " in front of " + p.getName().getString() + "."), true);
        return 1;
    }

    private static boolean freeSlot(ServerPlayerEntity player) {
        return HauntDirector.countActive(player.getServer()) < Math.max(1, ItWasTallConfig.INSTANCE.maxActiveIts);
    }

    /** An It on the ground {@code distance} blocks in front of the player (null if there is no room). */
    @org.jetbrains.annotations.Nullable
    private static TallOneEntity spawnAt(ServerPlayerEntity player, int distance) {
        ServerWorld world = player.getServerWorld();
        float yawRad = player.getYaw() * MathHelper.RADIANS_PER_DEGREE;
        double x = player.getX() - MathHelper.sin(yawRad) * distance;
        double z = player.getZ() + MathHelper.cos(yawRad) * distance;
        int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, MathHelper.floor(x), MathHelper.floor(z));
        TallOneEntity it = ModEntities.TALL_ONE.create(world);
        if (it == null) return null;
        it.refreshPositionAndAngles(x, y, z, player.getYaw() + 180f, 0f);
        if (!world.isSpaceEmpty(it)) return null;
        it.setVictim(player);
        return world.spawnEntity(it) ? it : null;
    }

    private static int spawn(ServerCommandSource source, ServerPlayerEntity player, int distance) throws CommandSyntaxException {
        HauntDirector.clearForCommand(source.getServer());   // a test command replaces the current It
        ServerWorld world = player.getServerWorld();
        float yawRad = player.getYaw() * MathHelper.RADIANS_PER_DEGREE;
        double x = player.getX() - MathHelper.sin(yawRad) * distance;
        double z = player.getZ() + MathHelper.cos(yawRad) * distance;
        int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, MathHelper.floor(x), MathHelper.floor(z));

        TallOneEntity it = ModEntities.TALL_ONE.create(world);
        if (it == null) return 0;
        it.refreshPositionAndAngles(x, y, z, player.getYaw() + 180f, 0f);
        it.setVictim(player);
        it.setPersistent();
        it.setForced(true);
        world.spawnEntity(it);
        source.sendFeedback(() -> Text.literal("It is watching " + player.getName().getString() + " (" + distance + " blocks)."), false);
        return 1;
    }
}
