package com.itwastall.client;

import com.itwastall.client.hud.FearHud;

/** Last server-synced values used by client-only journal and HUD screens. */
public final class JournalData {
    private static int phase;
    private static int stage;
    private static float tension;

    private JournalData() {}

    public static void update(int newPhase, int newStage, float newTension) {
        phase = Math.max(0, Math.min(1, newPhase));
        stage = Math.max(0, Math.min(5, newStage));
        tension = Math.max(0f, Math.min(100f, newTension));
        FearHud.setIntensity(tension / 100f);
    }

    public static int phase() { return phase; }
    public static int stage() { return stage; }
    public static float tension() { return tension; }

    public static void reset() {
        phase = 0;
        stage = 0;
        tension = 0;
        FearHud.setIntensity(0);
    }
}
