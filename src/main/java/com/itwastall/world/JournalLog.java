package com.itwastall.world;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The journal's EVENTS page (MODPLAN §16 JournalScreen "historia wydarzeń"): a short, per-player
 * history of what It did to that player, written in the journal's own voice ("Night 3 — ...").
 * Kept in server memory (newest first, capped) and pushed to the client with the journal pulse.
 * Hooks live where the events are sent (ModNetworking senders, HauntDirector, structures).
 */
public final class JournalLog {
    private static final int MAX = 12;
    private static final Map<UUID, Deque<String>> LOGS = new HashMap<>();

    private JournalLog() {
    }

    /** Adds one entry for this player; identical back-to-back entries are not repeated. */
    public static void add(ServerPlayerEntity player, String text) {
        if (player == null || text == null || text.isBlank()) return;
        long night = player.getWorld().getTimeOfDay() / 24000L + 1;
        String entry = "Night " + night + " — " + text;
        Deque<String> log = LOGS.computeIfAbsent(player.getUuid(), u -> new ArrayDeque<>());
        if (entry.equals(log.peekFirst())) return;
        log.addFirst(entry);
        while (log.size() > MAX) log.removeLast();
        Checkpoint.save(player);
    }

    /** Newest first. */
    public static List<String> entries(UUID player) {
        Deque<String> log = LOGS.get(player);
        return log == null ? List.of() : new ArrayList<>(log);
    }

    public static void forget(UUID player) {
        LOGS.remove(player);
    }
}
