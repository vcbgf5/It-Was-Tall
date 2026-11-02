package com.itwastall.director;

import com.itwastall.config.ItWasTallConfig;

/**
 * The six escalation stages (PHASES.md §3 "Parametry per ETAP"). Balance values live here;
 * any field set non-null in {@code config/itwastall.json -> stages[i]} overrides the stage's
 * table value (see ItWasTallConfig.StageOverride).
 *
 * <p>Consumers must read these getters live on every tick ({@code Director.currentStage()}) —
 * never snapshot a stage at start — so that {@code /itwastall stage <n>} takes effect from the
 * very next tick without a server restart.
 */
public enum Stage {
    /** ETAP 0 — "CISZA": only sounds, no entity, no events. */
    STAGE_0("SILENCE", 0, 0, 0, 0,
            false, 1.0, false, false, false,
            0, false, 0.0, false, 0.0, 2, 0),
    /** ETAP 1 — "OBSERWATOR": It stands far away, never approaches. */
    STAGE_1("OBSERVER", 40, 60, 8 * 60, 16 * 60,
            false, 1.0, false, false, false,
            0, false, 0.0, false, 0.005, 2, 0),
    /** ETAP 2 — "ZBLIŻENIE": It comes close, first window knocks (sound only). */
    STAGE_2("APPROACH", 20, 30, 6 * 60, 12 * 60,
            false, 1.0, false, false, false,
            1, false, 0.0, false, 0.015, 2, 0),
    /** ETAP 3 — "KONTAKT": digging, mine torch dousing, first tasks, limited scare text (chase optional per config). */
    STAGE_3("CONTACT", 20, 30, 5 * 60, 10 * 60,
            false, 0.35, false, true, false,
            1, true, 0.1, false, 0.03, 3, 1),
    /** ETAP 4 — "ŁOWCA": full horror — chase, sink, peek, jumpscare, base + mine dousing. */
    STAGE_4("HUNTER", 16, 28, 4 * 60, 8 * 60,
            true, 1.0, true, true, true,
            2, true, 0.4, true, 0.04, 4, 2),
    /** ETAP 5 — "POLOWANIE": maximum — It hunts, breaks windows, enters bases. */
    STAGE_5("HUNT", 12, 24, 3 * 60, 6 * 60,
            true, 1.0, true, true, true,
            3, true, 0.6, true, 0.05, 5, 3);

    private static final Stage[] VALUES = values();
    private static final int LAST = VALUES.length - 1;

    private final String displayName;
    private final int hauntMinDist;
    private final int hauntMaxDist;
    /** Minimum/maximum seconds between hauntings per player; 0 = haunting disabled. */
    private final int hauntCooldownMinSec;
    private final int hauntCooldownMaxSec;
    private final boolean chaseEnabled;
    /** Chance that a chase is allowed to start; stage 3 keeps it optional (35%). */
    private final double chaseChance;
    private final boolean sinkEnabled;
    private final boolean digEnabled;
    private final boolean peekEnabled;
    /** 0 = none, 1 = sound_only, 2 = sound + figure, 3 = break_glass. */
    private final int windowKnockLevel;
    /** Stage 3 is "limited": enabled but rare (see {@link #scareTextChancePerHideEvent()}). */
    private final boolean scareTextEnabled;
    /** Chance per hide event: 0 / 0 / 0 / 0.1 / 0.4 / 0.6. */
    private final double scareTextChancePerHideEvent;
    private final boolean jumpscareEnabled;
    private final double falsePresenceChance;
    /** 2 = none, 3 = mine only, 4 = mine + base, 5 = mine + base + base lights. */
    private final int torchDouseLevel;
    /** 0 = none, 1 = exit + lights, 2 = + dont_look, 3 = + hide_and_seek. */
    private final int tasksFlag;

    Stage(String displayName, int hauntMinDist, int hauntMaxDist,
          int hauntCooldownMinSec, int hauntCooldownMaxSec,
          boolean chaseEnabled, double chaseChance,
          boolean sinkEnabled, boolean digEnabled, boolean peekEnabled,
          int windowKnockLevel, boolean scareTextEnabled, double scareTextChancePerHideEvent,
          boolean jumpscareEnabled, double falsePresenceChance,
          int torchDouseLevel, int tasksFlag) {
        this.displayName = displayName;
        this.hauntMinDist = hauntMinDist;
        this.hauntMaxDist = hauntMaxDist;
        this.hauntCooldownMinSec = hauntCooldownMinSec;
        this.hauntCooldownMaxSec = hauntCooldownMaxSec;
        this.chaseEnabled = chaseEnabled;
        this.chaseChance = chaseChance;
        this.sinkEnabled = sinkEnabled;
        this.digEnabled = digEnabled;
        this.peekEnabled = peekEnabled;
        this.windowKnockLevel = windowKnockLevel;
        this.scareTextEnabled = scareTextEnabled;
        this.scareTextChancePerHideEvent = scareTextChancePerHideEvent;
        this.jumpscareEnabled = jumpscareEnabled;
        this.falsePresenceChance = falsePresenceChance;
        this.torchDouseLevel = torchDouseLevel;
        this.tasksFlag = tasksFlag;
    }

    /** Stage for an index 0..5, clamped. */
    public static Stage fromIndex(int index) {
        return VALUES[Math.min(LAST, Math.max(0, index))];
    }

    private static ItWasTallConfig.StageOverride entry(int index) {
        ItWasTallConfig.Values cfg = ItWasTallConfig.INSTANCE;
        if (cfg == null || cfg.stages == null || index < 0 || index >= cfg.stages.length) return null;
        return cfg.stages[index];
    }

    private static int intOr(int index, Integer override, int fallback) {
        return override != null ? override : fallback;
    }

    private static double doubleOr(Double override, double fallback) {
        return override != null ? override : fallback;
    }

    private static boolean boolOr(Boolean override, boolean fallback) {
        return override != null ? override : fallback;
    }

    public String displayName() {
        return displayName;
    }

    public int hauntMinDist() {
        ItWasTallConfig.StageOverride o = entry(ordinal());
        return o == null ? hauntMinDist : intOr(ordinal(), o.hauntMinDist, hauntMinDist);
    }

    public int hauntMaxDist() {
        ItWasTallConfig.StageOverride o = entry(ordinal());
        return o == null ? hauntMaxDist : intOr(ordinal(), o.hauntMaxDist, hauntMaxDist);
    }

    public int hauntCooldownMinSec() {
        ItWasTallConfig.StageOverride o = entry(ordinal());
        return o == null ? hauntCooldownMinSec : intOr(ordinal(), o.hauntCooldownMinSec, hauntCooldownMinSec);
    }

    public int hauntCooldownMaxSec() {
        ItWasTallConfig.StageOverride o = entry(ordinal());
        return o == null ? hauntCooldownMaxSec : intOr(ordinal(), o.hauntCooldownMaxSec, hauntCooldownMaxSec);
    }

    public boolean chaseEnabled() {
        ItWasTallConfig.StageOverride o = entry(ordinal());
        return o == null ? chaseEnabled : boolOr(o.chaseEnabled, chaseEnabled);
    }

    public double chaseChance() {
        ItWasTallConfig.StageOverride o = entry(ordinal());
        return o == null ? chaseChance : doubleOr(o.chaseChance, chaseChance);
    }

    public boolean sinkEnabled() {
        ItWasTallConfig.StageOverride o = entry(ordinal());
        return o == null ? sinkEnabled : boolOr(o.sinkEnabled, sinkEnabled);
    }

    public boolean digEnabled() {
        ItWasTallConfig.StageOverride o = entry(ordinal());
        return o == null ? digEnabled : boolOr(o.digEnabled, digEnabled);
    }

    public boolean peekEnabled() {
        ItWasTallConfig.StageOverride o = entry(ordinal());
        return o == null ? peekEnabled : boolOr(o.peekEnabled, peekEnabled);
    }

    public int windowKnockLevel() {
        ItWasTallConfig.StageOverride o = entry(ordinal());
        return o == null ? windowKnockLevel : intOr(ordinal(), o.windowKnockLevel, windowKnockLevel);
    }

    public boolean scareTextEnabled() {
        ItWasTallConfig.StageOverride o = entry(ordinal());
        return o == null ? scareTextEnabled : boolOr(o.scareTextEnabled, scareTextEnabled);
    }

    public double scareTextChancePerHideEvent() {
        ItWasTallConfig.StageOverride o = entry(ordinal());
        return o == null ? scareTextChancePerHideEvent : doubleOr(o.scareTextChancePerHideEvent, scareTextChancePerHideEvent);
    }

    public boolean jumpscareEnabled() {
        ItWasTallConfig.StageOverride o = entry(ordinal());
        return o == null ? jumpscareEnabled : boolOr(o.jumpscareEnabled, jumpscareEnabled);
    }

    public double falsePresenceChance() {
        ItWasTallConfig.StageOverride o = entry(ordinal());
        return o == null ? falsePresenceChance : doubleOr(o.falsePresenceChance, falsePresenceChance);
    }

    public int torchDouseLevel() {
        ItWasTallConfig.StageOverride o = entry(ordinal());
        return o == null ? torchDouseLevel : intOr(ordinal(), o.torchDouseLevel, torchDouseLevel);
    }

    public int tasksFlag() {
        ItWasTallConfig.StageOverride o = entry(ordinal());
        return o == null ? tasksFlag : intOr(ordinal(), o.tasksFlag, tasksFlag);
    }
}
