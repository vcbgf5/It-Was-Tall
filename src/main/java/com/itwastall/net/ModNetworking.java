package com.itwastall.net;

import com.itwastall.ItWasTall;
import com.itwastall.config.ItWasTallConfig;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

public final class ModNetworking {
    private ModNetworking() {
    }

    public static void init() {
        PayloadTypeRegistry.playS2C().register(JumpscarePayload.ID, JumpscarePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ScareTextPayload.ID, ScareTextPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ItJoinedPayload.ID, ItJoinedPayload.CODEC);
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
        if (cfg.scareMessages && ServerPlayNetworking.canSend(player, ScareTextPayload.ID)) {
            ServerPlayNetworking.send(player, new ScareTextPayload(text, cfg.messageMillis));
        }
    }

    public static void sendJumpscare(ServerPlayerEntity player) {
        ItWasTallConfig.Values cfg = ItWasTallConfig.INSTANCE;
        ItWasTall.LOGGER.debug("Jumpscare -> {}", player.getName().getString());
        if (ServerPlayNetworking.canSend(player, JumpscarePayload.ID)) {
            ServerPlayNetworking.send(player, new JumpscarePayload(cfg.jumpscareMillis, cfg.jumpscares));
        }
    }
}
