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
        FearHud.setIntensity(0);
    }
}
