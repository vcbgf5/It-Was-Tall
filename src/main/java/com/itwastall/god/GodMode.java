package com.itwastall.god;

import com.itwastall.ItWasTall;
import com.itwastall.net.GodActionPayload;
import com.itwastall.net.GodStatePayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.GameMode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * GOD MODE (/changetogod, operators only), for sessions with friends: one player stops playing and runs the
 * fear for everyone else. The god flies and builds (creative, not spectator), is invisible and silent, gone
 * from the player list and never sent to the other clients at all (GodTrackerMixin) — and opens a panel with
 * G to fire any event at any player, set the stage and tension, the time of day and the weather.
 * Mobs and the mod's own events leave creative players alone, so the god is never haunted.
 */
public final class GodMode {
    /** Gods of this session and the game mode each had before. */
    private static final Map<UUID, GameMode> GODS = new HashMap<>();

    private GodMode() {}

    public static boolean isGod(UUID player) {
        return GODS.containsKey(player);
    }

    public static Set<UUID> gods() {
        return GODS.keySet();
    }

    public static void init() {
        PayloadTypeRegistry.playS2C().register(GodStatePayload.ID, GodStatePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(GodActionPayload.ID, GodActionPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(com.itwastall.net.GodMapPayload.ID, com.itwastall.net.GodMapPayload.CODEC);
        ItForm.init();
        ServerPlayNetworking.registerGlobalReceiver(GodActionPayload.ID, (payload, context) ->
                context.server().execute(() -> GodActions.run(context.player(), payload.action(), payload.target())));
        // someone joining never sees the god in the player list
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> {
            ServerPlayerEntity joined = handler.getPlayer();
            if (isGod(joined.getUuid())) {
                hideFromOthers(joined);
                sendState(joined, true);
            } else if (!GODS.isEmpty()) {
                joined.networkHandler.sendPacket(new PlayerRemoveS2CPacket(List.copyOf(GODS.keySet())));
            }
        }));
        // keep it up: invisibility, silence, creative (a /gamemode would end the godhood on its own)
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (GODS.isEmpty() || server.getTicks() % 20 != 0) return;
            for (UUID id : List.copyOf(GODS.keySet())) {
                ServerPlayerEntity god = server.getPlayerManager().getPlayer(id);
                if (god == null) continue;
                if (!god.isCreative()) {
                    disable(god, false);
                    continue;
                }
                applyEffects(god);
            }
        });
    }

    /** /changetogod: switch god mode on or off for that player. */
    public static void toggle(ServerPlayerEntity player) {
        if (isGod(player.getUuid())) disable(player, true);
        else enable(player);
    }

    public static void enable(ServerPlayerEntity player) {
        if (isGod(player.getUuid())) return;
        GODS.put(player.getUuid(), player.interactionManager.getGameMode());
        player.changeGameMode(GameMode.CREATIVE);
        player.setSilent(true);
        applyEffects(player);
        hideFromOthers(player);
        sendState(player, true);
        player.sendMessage(Text.literal("You are the god of this night. Nobody can see you. Press G for the panel.")
                .formatted(Formatting.DARK_RED), false);
        ItWasTall.LOGGER.info("{} became the god of the session.", player.getName().getString());
    }

    public static void disable(ServerPlayerEntity player, boolean restoreMode) {
        GameMode before = GODS.remove(player.getUuid());
        if (before == null) return;
        ItForm.end(player.getUuid(), player);
        if (restoreMode) player.changeGameMode(before);
        player.setSilent(false);
        player.removeStatusEffect(StatusEffects.INVISIBILITY);
        MinecraftServer server = player.getServer();
        if (server != null) {
            PlayerListS2CPacket back = PlayerListS2CPacket.entryFromPlayer(List.of(player));
            for (ServerPlayerEntity other : server.getPlayerManager().getPlayerList()) {
                if (other != player) other.networkHandler.sendPacket(back);
            }
        }
        sendState(player, false);
        player.sendMessage(Text.literal("You are a player again.").formatted(Formatting.GRAY), false);
        ItWasTall.LOGGER.info("{} is no longer the god of the session.", player.getName().getString());
    }

    /** Leaving the game keeps nothing: a god who logs out comes back as a normal player. */
    public static void forget(UUID player) {
        GODS.remove(player);
        ItForm.end(player, null);
    }

    private static void applyEffects(ServerPlayerEntity god) {
        if (!god.hasStatusEffect(StatusEffects.INVISIBILITY)) {
            god.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, StatusEffectInstance.INFINITE, 0, true, false, false));
        }
        god.setSilent(true);
    }

    private static void hideFromOthers(ServerPlayerEntity god) {
        MinecraftServer server = god.getServer();
        if (server == null) return;
        PlayerRemoveS2CPacket gone = new PlayerRemoveS2CPacket(List.of(god.getUuid()));
        for (ServerPlayerEntity other : server.getPlayerManager().getPlayerList()) {
            if (other != god && !isGod(other.getUuid())) other.networkHandler.sendPacket(gone);
        }
    }

    private static void sendState(ServerPlayerEntity player, boolean god) {
        if (ServerPlayNetworking.canSend(player, GodStatePayload.ID)) ServerPlayNetworking.send(player, new GodStatePayload(god));
    }
}
