package com.itwastall.entity;

import com.itwastall.net.WalkiePayload;
import com.itwastall.registry.ModVoices;
import com.itwastall.voice.VoiceHooks;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * THE WALKIE-TALKIE: not an item — every player has one, in the extra slot next to the hotbar (key 0 or the
 * scroll wheel), and holds the right mouse button to talk. The voice goes to every other player, crackling like
 * a radio (ItWasTallVoicechat, Simple Voice Chat). This class only knows who is talking right now.
 */
public final class Walkie {
    /** Players holding the talk button (read from the voice chat thread as well). */
    private static final Set<UUID> TRANSMITTING = ConcurrentHashMap.newKeySet();

    private Walkie() {}

    public static boolean isTransmitting(UUID player) {
        return TRANSMITTING.contains(player);
    }

    public static void init() {
        PayloadTypeRegistry.playS2C().register(WalkiePayload.ID, WalkiePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(WalkiePayload.ID, WalkiePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(WalkiePayload.ID, (payload, context) ->
                context.server().execute(() -> setTransmitting(context.player(), payload.active())));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> {
            ServerPlayerEntity p = handler.getPlayer();
            if (ServerPlayNetworking.canSend(p, WalkiePayload.ID)) ServerPlayNetworking.send(p, new WalkiePayload("", VoiceHooks.available));
        }));
    }

    public static void forget(UUID player) {
        TRANSMITTING.remove(player);
    }

    private static void setTransmitting(ServerPlayerEntity p, boolean on) {
        if (p == null || p.getServer() == null) return;
        boolean changed = on ? TRANSMITTING.add(p.getUuid()) : TRANSMITTING.remove(p.getUuid());
        if (!changed) return;
        WalkiePayload msg = new WalkiePayload(p.getGameProfile().getName(), on);
        for (ServerPlayerEntity q : p.getServer().getPlayerManager().getPlayerList()) {
            if (q == p) continue;
            if (ServerPlayNetworking.canSend(q, WalkiePayload.ID)) ServerPlayNetworking.send(q, msg);
            Radio.playTo(q, ModVoices.radio("squelch"), 0.5f, -1);   // the click and hiss of a walkie-talkie keying up / down
        }
    }
}
