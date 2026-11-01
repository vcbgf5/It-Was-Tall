package com.itwastall.command;

import com.itwastall.entity.MineEvents;
import com.itwastall.entity.ScareSounds;
import com.itwastall.config.ItWasTallConfig;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.itwastall.entity.TallOneEntity;
import com.itwastall.net.ModNetworking;
import com.itwastall.registry.ModEntities;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Heightmap;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * /itwastall spawn [distance]  - spawns It in front of you (where you are looking), staring at you
 * /itwastall jumpscare         - test the 0.1 s jumpscare
 * /itwastall mine dig|sounds|torches|peek - force a cave event
 * /itwastall scare             - a small scare sound right now
 * /itwastall message [text]    - red on-screen message (default "don't hide. just run.")
 */
public final class ItWasTallCommand {
    private ItWasTallCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess access,
                                CommandManager.RegistrationEnvironment env) {
        dispatcher.register(literal("itwastall")
                .requires(src -> src.hasPermissionLevel(2))
                .then(literal("spawn")
                        .executes(ctx -> spawn(ctx.getSource(), 30))
                        .then(argument("distance", IntegerArgumentType.integer(4, 96))
                                .executes(ctx -> spawn(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "distance")))))
                .then(mineCommand())
                .then(literal("scare").executes(ctx -> {
                    ScareSounds.playRandomScare(ctx.getSource().getPlayerOrThrow());
                    return 1;
                }))
                .then(literal("message")
                        .executes(ctx -> {
                            ScareSounds.showMessage(ctx.getSource().getPlayerOrThrow(), ItWasTallConfig.INSTANCE.scareText);
                            return 1;
                        })
                        .then(argument("text", StringArgumentType.greedyString()).executes(ctx -> {
                            ScareSounds.showMessage(ctx.getSource().getPlayerOrThrow(), StringArgumentType.getString(ctx, "text"));
                            return 1;
                        })))
                .then(literal("jumpscare")
                        .executes(ctx -> {
                            ModNetworking.sendJumpscare(ctx.getSource().getPlayerOrThrow());
                            return 1;
                        })));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> mineCommand() {
        var root = literal("mine");
        for (MineEvents.Kind kind : MineEvents.Kind.values()) {
            root.then(literal(kind.name().toLowerCase(java.util.Locale.ROOT)).executes(ctx -> {
                boolean ok = MineEvents.trigger(ctx.getSource().getPlayerOrThrow(), kind);
                ctx.getSource().sendFeedback(() -> Text.literal(ok ? "Started: " + kind : "Could not start " + kind + " here."), false);
                return ok ? 1 : 0;
            }));
        }
        return root;
    }

    private static int spawn(ServerCommandSource source, int distance) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        ServerWorld world = source.getWorld();
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
        source.sendFeedback(() -> Text.literal("It is watching you (" + distance + " blocks)."), false);
        return 1;
    }
}
