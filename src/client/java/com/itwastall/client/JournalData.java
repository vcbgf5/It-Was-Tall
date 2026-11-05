package com.itwastall.client;

import com.itwastall.client.hud.FearHud;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Last server-synced values used by client-only journal and HUD screens. */
public final class JournalData {
    private static int phase;
    private static int stage;
    private static float tension;
    /** The player's current task as the server reports it ("no task" while idle). */
    private static String task = "no task";
    /** Lore book ids this player has already received (MODPLAN §9 journal listing). */
    private static final Set<String> LORE = new HashSet<>();
    /** BASE page: empty = no base yet, else anchor xyz + windows, doors, lights, chests, corners, placed. */
    private static java.util.List<Integer> baseCounts = java.util.List.of();
    /** EVENTS page, newest first. */
    private static java.util.List<String> events = java.util.List.of();

    private JournalData() {}

    public static void update(int newPhase, int newStage, float newTension, String newTask,
                              java.util.List<String> loreIds) {
        phase = Math.max(0, Math.min(1, newPhase));
        stage = Math.max(0, Math.min(5, newStage));
        tension = Math.max(0f, Math.min(100f, newTension));
        task = newTask == null || newTask.isBlank() ? "no task" : newTask;
        LORE.clear();
        LORE.addAll(loreIds);
        FearHud.setIntensity(tension / 100f);
    }

    public static void updateJournal(java.util.List<Integer> newBase, java.util.List<String> newEvents) {
        baseCounts = newBase == null ? java.util.List.of() : java.util.List.copyOf(newBase);
        events = newEvents == null ? java.util.List.of() : java.util.List.copyOf(newEvents);
    }

    public static java.util.List<Integer> baseCounts() { return baseCounts; }
    public static java.util.List<String> events() { return events; }
    public static int phase() { return phase; }
    public static int stage() { return stage; }
    public static float tension() { return tension; }
    public static String task() { return task; }
    public static Set<String> loreIds() { return Collections.unmodifiableSet(LORE); }
    public static boolean hasLore(String id) { return LORE.contains(id); }

    public static void reset() {
        phase = 0;
        stage = 0;
        tension = 0;
        task = "no task";
        LORE.clear();
        baseCounts = java.util.List.of();
        events = java.util.List.of();
        FearHud.setIntensity(0);
    }
}
