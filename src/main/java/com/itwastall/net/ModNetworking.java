package com.itwastall.net;

import com.itwastall.ItWasTall;
import com.itwastall.config.ItWasTallConfig;
import com.itwastall.director.Director;
import com.itwastall.world.ItWasTallState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class ModNetworking {
    private ModNetworking() {
    }

    /** Per-player opt-out from the warning screen ("Jumpscares & scares" toggle, server-held). */
    private static final Set<UUID> SCARES_OFF = new HashSet<>();

    public static void init() {
        PayloadTypeRegistry.playS2C().register(JumpscarePayload.ID, JumpscarePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FearStatePayload.ID, FearStatePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ScareTextPayload.ID, ScareTextPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ItJoinedPayload.ID, ItJoinedPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FalsePresencePayload.ID, FalsePresencePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BaseDebugPayload.ID, BaseDebugPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TaskToastPayload.ID, TaskToastPayload.CODEC);
        // C2S: the client's warning-screen toggle must reach the server or the opt-out is a lie
        PayloadTypeRegistry.playC2S().register(ScaresPrefPayload.ID, ScaresPrefPayload.CODEC);
        // C2S: a deliberate look at a window figure earns a random task (MODPLAN §12 figure look)
        PayloadTypeRegistry.playC2S().register(WindowSeenPayload.ID, WindowSeenPayload.CODEC);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 20 != 0) return;
            ItWasTallState state = ItWasTallState.getOrCreate(server);
            for (ServerPlayerEntity player : PlayerLookup.all(server)) {
                if (!ServerPlayNetworking.canSend(player, FearStatePayload.ID)) continue;
                ServerPlayNetworking.send(player, new FearStatePayload(
                        Director.phase(), Director.stageIndex(), (float) Director.tension(),
                        com.itwastall.task.TaskManager.status(player.getUuid()),
                        java.util.List.copyOf(state.loreBookIds(player.getUuid()))));
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(WindowSeenPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            if (player == null) return;
            ItWasTall.LOGGER.debug("{} met its gaze through the window.", player.getName().getString());
            com.itwastall.task.TaskManager.onWindowLooked(player);
        });
        ServerPlayNetworking.registerGlobalReceiver(ScaresPrefPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            if (player == null) return;
            if (payload.scaresEnabled()) SCARES_OFF.remove(player.getUuid());
            else SCARES_OFF.add(player.getUuid());
            ItWasTall.LOGGER.info("{} {} scares (client toggle).",
                    player.getName().getString(), payload.scaresEnabled() ? "re-enabled" : "disabled");
        });
    }

    /** True when that player asked the server to hold the jumpscare / red-text scares back. */
    public static boolean scaresDisabled(UUID player) {
        return SCARES_OFF.contains(player);
    }

    /** Broadcast of the one-shot "IT JOINED" sequence to every player currently on the server. */
    public static void broadcastItJoined(MinecraftServer server) {
        for (ServerPlayerEntity player : PlayerLookup.all(server)) {
            if (ServerPlayNetworking.canSend(player, ItJoinedPayload.ID)) {
                ServerPlayNetworking.send(player, new ItJoinedPayload(4000));
            }
        }
    }

    public static void sendScareText(ServerPlayerEntity player, String text) {
        ItWasTallConfig.Values cfg = ItWasTallConfig.INSTANCE;
        if (scaresDisabled(player.getUuid())) return; // the warning screen's toggle is absolute
        if (cfg.scareMessages && ServerPlayNetworking.canSend(player, ScareTextPayload.ID)) {
            ServerPlayNetworking.send(player, new ScareTextPayload(text, cfg.messageMillis));
        }
    }

    public static void sendJumpscare(ServerPlayerEntity player) {
        ItWasTallConfig.Values cfg = ItWasTallConfig.INSTANCE;
        if (scaresDisabled(player.getUuid())) return; // the warning screen's toggle is absolute
        ItWasTall.LOGGER.debug("Jumpscare -> {}", player.getName().getString());
        if (ServerPlayNetworking.canSend(player, JumpscarePayload.ID)) {
            ServerPlayNetworking.send(player, new JumpscarePayload(cfg.jumpscareMillis, cfg.jumpscares));
        }
    }

    /**
     * Server -> client: the False Presence figure (MODPLAN §7.4 — client-side only, never a
     * real entity). Returns false when that client cannot receive the payload.
     */
    public static boolean sendFalsePresence(ServerPlayerEntity player, double x, double y, double z,
                                            float yaw, int durationTicks, boolean window) {
        if (!ServerPlayNetworking.canSend(player, FalsePresencePayload.ID)) return false;
        ServerPlayNetworking.send(player, new FalsePresencePayload(x, y, z, yaw, durationTicks, window));
        ItWasTall.LOGGER.debug("False presence -> {}", player.getName().getString());
        return true;
    }

    /** Debug 3D (MODPLAN §8.5): the player's saved BaseMap as coloured world frames. */
    public static boolean sendBaseDebug(ServerPlayerEntity player) {
        if (!ServerPlayNetworking.canSend(player, BaseDebugPayload.ID)) return false;
        var map = com.itwastall.base.BaseTracker.mapOf(player.getUuid());
        if (map == null) return false;
        ServerPlayNetworking.send(player, new BaseDebugPayload(map.anchor(), map.encodedMarkers(), 30));
        ItWasTall.LOGGER.debug("Base debug view -> {}", player.getName().getString());
        return true;
    }

    /** TaskToast (MODPLAN §16): new / completed / failed, drawn by the client's TaskToast HUD. */
    public static void sendTaskToast(ServerPlayerEntity player, int kind, String title, String text) {
        if (!ServerPlayNetworking.canSend(player, TaskToastPayload.ID)) return;
        ServerPlayNetworking.send(player, new TaskToastPayload(kind, title, text));
    }
}
