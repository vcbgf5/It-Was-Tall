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
 * </pre>
 */
public final class ItWasTallCommand {
    private ItWasTallCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess access,
                                CommandManager.RegistrationEnvironment env) {
        dispatcher.register(literal("itwastall")
                .requires(src -> src.hasPermissionLevel(2))
                .then(literal("spawn")
                        .executes(ctx -> spawn(ctx.getSource(), ctx.getSource().getPlayerOrThrow(), 30))
                        .then(argument("distance", IntegerArgumentType.integer(4, 96))
                                .executes(ctx -> spawn(ctx.getSource(), ctx.getSource().getPlayerOrThrow(), IntegerArgumentType.getInteger(ctx, "distance")))
                                .then(argument("player", EntityArgumentType.player())
                                        .executes(ctx -> spawn(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"),
                                                IntegerArgumentType.getInteger(ctx, "distance"))))))
                .then(forPlayers("haunt", p -> {
                    if (!HauntDirector.spawnStare(p)) throw new IllegalStateException("no free spot");
                }))
                .then(forPlayers("jumpscare", p -> ModNetworking.sendJumpscare(p)))
                .then(forPlayers("scare", p -> ScareSounds.playRandomScare(p)))
                .then(literal("message")
                        .executes(ctx -> each(ctx, List.of(ctx.getSource().getPlayerOrThrow()), "message",
                                p -> ScareSounds.showMessage(p, ItWasTallConfig.INSTANCE.scareText)))
                        .then(argument("players", EntityArgumentType.players())
                                .executes(ctx -> each(ctx, EntityArgumentType.getPlayers(ctx, "players"), "message",
                                        p -> ScareSounds.showMessage(p, ItWasTallConfig.INSTANCE.scareText)))
                                .then(argument("text", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            String text = StringArgumentType.getString(ctx, "text");
                                            return each(ctx, EntityArgumentType.getPlayers(ctx, "players"), "message", p -> ScareSounds.showMessage(p, text));
                                        }))))
                .then(mineCommand())
                .then(eventCommand())
                .then(debugCommand())
                .then(baseCommand())
                .then(literal("stage")
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
                                })))
                .then(literal("tension")
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
                                })))
                .then(literal("phase")
                        .then(literal("activate")
                                .executes(ctx -> {
                                    boolean ok = Director.activatePhase(ctx.getSource().getServer());
                                    if (ok) {
                                        ctx.getSource().sendFeedback(() -> Text.literal("Phase activated — IT JOINED broadcast to everyone."), false);
                                    } else {
                                        ctx.getSource().sendError(Text.literal("Phase is already ACTIVE."));
                                    }
                                    return ok ? 1 : 0;
                                })))
                .then(literal("status").executes(ctx -> {
                    int n = HauntDirector.countActive(ctx.getSource().getServer());
                    ctx.getSource().sendFeedback(() -> Text.literal("Its on the server: " + n + " / " + ItWasTallConfig.INSTANCE.maxActiveIts), false);
                    ctx.getSource().sendFeedback(() -> Text.literal(Director.statusLine()), false);
                    return n;
                })));
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
                }));
        return root;
    }

    private static LiteralArgumentBuilder<ServerCommandSource> eventKind(String name, PlayerAction action) {
        return literal(name)
                .executes(ctx -> each(ctx, List.of(ctx.getSource().getPlayerOrThrow()), "event " + name, action))
                .then(argument("player", EntityArgumentType.player())
                        .executes(ctx -> each(ctx, List.of(EntityArgumentType.getPlayer(ctx, "player")),
                                "event " + name, action)));
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
                            return 1;
                        }))
                .then(literal("view")
                        .executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            if (!ModNetworking.sendBaseDebug(p)) {
                                throw new IllegalStateException("no mapped base yet (click a bed, wait for the scan)");
                            }
                            ctx.getSource().sendFeedback(() -> Text.literal(
                                    "Base debug frames shown for 30 s: windows blue, doors green, lights yellow, chests orange, bed white."), false);
                            return 1;
                        }));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> mineCommand() {
        var root = literal("mine");
        for (MineEvents.Kind kind : MineEvents.Kind.values()) {
            String name = kind.name().toLowerCase(Locale.ROOT);
            PlayerAction action = p -> {
                if (!MineEvents.trigger(p, kind)) throw new IllegalStateException("can't start " + name + " here");
            };
            root.then(literal(name)
                    .executes(ctx -> each(ctx, List.of(ctx.getSource().getPlayerOrThrow()), "mine " + name, action))
                    .then(argument("player", EntityArgumentType.player())
                            .executes(ctx -> each(ctx, List.of(EntityArgumentType.getPlayer(ctx, "player")), "mine " + name, action))));
        }
        return root;
    }

    private static int spawn(ServerCommandSource source, ServerPlayerEntity player, int distance) throws CommandSyntaxException {
        int active = HauntDirector.countActive(source.getServer());
        int limit = Math.max(1, ItWasTallConfig.INSTANCE.maxActiveIts);
        if (active >= limit) {
            source.sendError(Text.literal("Cannot spawn It: the server limit is already reached (" + active + "/" + limit + ")."));
            return 0;
        }
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
        world.spawnEntity(it);
        source.sendFeedback(() -> Text.literal("It is watching " + player.getName().getString() + " (" + distance + " blocks)."), false);
        return 1;
    }
}
