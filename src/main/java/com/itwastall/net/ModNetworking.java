package com.itwastall.net;

import com.itwastall.ItWasTall;
import com.itwastall.config.ItWasTallConfig;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

public final class ModNetworking {
    private ModNetworking() {
    }

    public static void init() {
        PayloadTypeRegistry.playS2C().register(JumpscarePayload.ID, JumpscarePayload.CODEC);
    }

    public static void sendJumpscare(ServerPlayerEntity player) {
        ItWasTallConfig.Values cfg = ItWasTallConfig.INSTANCE;
        ItWasTall.LOGGER.debug("Jumpscare -> {}", player.getName().getString());
        if (ServerPlayNetworking.canSend(player, JumpscarePayload.ID)) {
            ServerPlayNetworking.send(player, new JumpscarePayload(cfg.jumpscareMillis, cfg.jumpscares));
        }
    }
}
