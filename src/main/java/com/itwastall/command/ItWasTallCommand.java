package com.itwastall.command;

import com.itwastall.config.ItWasTallConfig;
import com.itwastall.entity.HauntDirector;
import com.itwastall.entity.MineEvents;
import com.itwastall.entity.ScareSounds;
import com.itwastall.entity.TallOneEntity;
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
 * /itwastall status                             - how many Its exist on the server
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
                .then(literal("status").executes(ctx -> {
                    int n = HauntDirector.countActive(ctx.getSource().getServer());
                    ctx.getSource().sendFeedback(() -> Text.literal("Its on the server: " + n + " / " + ItWasTallConfig.INSTANCE.maxActiveIts), false);
                    return n;
                })));
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
