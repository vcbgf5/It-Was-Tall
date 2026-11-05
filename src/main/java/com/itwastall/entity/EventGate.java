package com.itwastall.entity;

import com.itwastall.config.ItWasTallConfig;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * MODPLAN §19 {@code minEventCooldown}: one shared gap between the bigger events of one player (house
 * events, window knocks, false presences, the Old One, dynamic structures), on top of each event's own
 * cooldown — so two of them never land in the same minute. Test commands ignore it.
 */
public final class EventGate {
    private static final Map<UUID, Long> LAST = new HashMap<>();

    private EventGate() {
    }

    private static long now(ServerPlayerEntity player) {
        return player.getServerWorld().getServer().getOverworld().getTime();
    }

    /** True when the shared gap since this player's last bigger event has passed. */
    public static boolean ready(ServerPlayerEntity player) {
        int gap = Math.max(0, ItWasTallConfig.INSTANCE.minEventCooldown);
        if (gap == 0) return true;
        Long last = LAST.get(player.getUuid());
        return last == null || now(player) - last >= gap * 20L;
    }

    /** Records that a bigger event just started for this player. */
    public static void mark(ServerPlayerEntity player) {
        LAST.put(player.getUuid(), now(player));
    }

    /** Seconds until the next bigger event may start (0 = now), for /itwastall debug events. */
    public static long secondsLeft(ServerPlayerEntity player) {
        Long last = LAST.get(player.getUuid());
        if (last == null) return 0;
        long left = Math.max(0, ItWasTallConfig.INSTANCE.minEventCooldown) * 20L - (now(player) - last);
        return Math.max(0, left / 20);
    }

    public static void forget(UUID player) {
        LAST.remove(player);
    }
}
