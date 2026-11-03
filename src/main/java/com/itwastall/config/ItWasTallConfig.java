package com.itwastall.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.itwastall.ItWasTall;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Simple JSON config stored in config/itwastall.json. */
public final class ItWasTallConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("itwastall.json");

    public static Values INSTANCE = new Values();

    public static final class Values {
        /** Show the full-screen jumpscare image (false = sound only). */
        public boolean jumpscares = true;
        /** How long the jumpscare image stays on screen. */
        public int jumpscareMillis = 100;
        /** Distance at which a staring Tall One starts chasing. */
        public double chaseTriggerDistance = 12.0;
        /** Seconds of looking at it while walking closer before it starts chasing. */
        public double stareTriggerSeconds = 3.0;
        // ---- haunting (It appearing on its own) ----
        /** It shows up by itself in the distance and stares at you. Every player has their own timer. */
        public boolean hauntEvents = true;
        /** Minutes after joining before the first appearance. */
        public int hauntFirstMinutes = 3;
        /** Minutes between appearances for one player. */
        public int hauntMinMinutes = 5;
        public int hauntMaxMinutes = 7;
        /** Only at night. */
        public boolean hauntOnlyAtNight = true;
        /** Server-wide limit of Its that exist at the same time. */
        public int maxActiveIts = 1;
        /** From Stage 2 onward, It slowly approaches while the player is not looking directly at it. */
        public boolean stalking = true;
        /** Movement speed used while stalking (separate from the chase speed). */
        public double stalkSpeed = 0.12;
        /** It stops approaching at or below this distance from the player. */
        public double stalkStopDistance = 8.0;
        /** It will not begin stalking when farther away than this distance. */
        public double stalkMaxDistance = 36.0;

        /** Speed multiplier while chasing. */
        public double chaseSpeed = 1.45;
        /** Ticks between sinking into the ground and the jumpscare. */
        public int jumpscareDelayMinTicks = 10;
        public int jumpscareDelayMaxTicks = 40;
        /** If it is not approached, it gives up staring after this many seconds. */
        public int stareTimeoutSeconds = 180;

        // ---- window knocks (PHASES Poziom 5: MODPLAN §12) ----
        /** The Tall One knocks three times on a window while you are inside your base at night. */
        public boolean windowKnocks = true;
        /** Chance rolled per check (~once per 20 s) while the conditions hold; 1 knock burst per night. */
        public double windowKnockChance = 0.35;

        // ---- false presence (PHASES Poziom 4: MODPLAN §7, client-side figure only) ----
        /** A fake Tall One, rendered only on one client, gone when you look right at it. */
        public boolean falsePresence = true;

        // ---- lore books (MODPLAN §9) ----
        /** Secret lore books may be placed in free slots of the player's mapped storage. */
        public boolean loreBooks = true;
        /** Chance per eligible check (every 10 seconds) to place one new book. */
        public double loreBookChance = 0.35;
        /** Minimum minutes between books for one player. */
        public int loreBookCooldownMinutes = 5;

        // ---- base tracking (PHASES Poziom 6: MODPLAN §8) ----
        /** How many blocks the base scanner may classify per tick (shared between players). */
        public int scanBlocksPerTick = 64;
        /** Horizontal radius (anchor +-radius) of one base scan. */
        public int baseScanRadius = 16;
        /** Hard cap of interior cells flood-fill may mark (MODPLAN §8.3 "ograniczenie objętości"). */
        public int baseInteriorCap = 2400;
        /** Dark interior corners (block light < 4) worth recording for search goals. */
        public int baseCornerCap = 48;

        // ---- mine / cave events ----
        /** Enable events while the player is underground (digging towards you, sounds, torches, peeking). */
        public boolean mineEvents = true;
        /** It may break blocks while digging towards you (also requires the mobGriefing gamerule). */
        public boolean digging = true;
        /** Seconds a player has to spend underground before cave events can start. */
        public int mineMinSecondsUnderground = 60;
        /** Minimum seconds between two cave events for one player. */
        public int mineEventCooldownSeconds = 240;
        /** Chance per second (after the minimum time) that a cave event starts. */
        public double mineEventChancePerSecond = 0.03;
        /** Y level below which a dark spot counts as "in a mine". */
        public int mineMaxY = 50;
        /** Digging slowdown: 1.0 = like a player with an iron pickaxe, higher = slower and creepier. */
        public double digSlowdown = 2.0;

        // ---- ambience ----
        /** Creepy ambient sounds (wind, whispers, breathing behind you, creaks, drones, distant screams...). */
        public boolean ambientSounds = true;
        /** Chance per second that an ambient sound plays (at night / underground; daytime is 4x rarer). */
        public double ambientChancePerSecond = 0.06;

        // ---- small scares ----
        /** Short scare sounds close to you (twig snap, pebble, tap on glass, footsteps behind, giggle...). */
        public boolean scareSounds = true;
        public int scareMinSeconds = 20;
        public int scareMaxSeconds = 60;

        // ---- on-screen messages ----
        /** Red flickering text in the middle of the screen. */
        public boolean scareMessages = true;
        /** The main message (shown about half of the time, and always after it sinks at your base). */
        public String scareText = "don't hide. just run.";
        /** Other messages that can appear. */
        public List<String> messages = new ArrayList<>(List.of(
                "just run.", "it was tall.", "it sees you.", "turn around.", "you can't hide down here.", "it knows where you sleep."));
        /** Random messages at night / underground every this many minutes. */
        public int messageMinMinutes = 5;
        public int messageMaxMinutes = 15;
        /** How long a message stays on screen. */
        public int messageMillis = 2800;

        // ---- phase activation (PHASE 0 -> ACTIVE) ----
        /** When the "IT JOINED" one-day intro triggers. */
        public Activation activation = new Activation();

        /** Tension accrual and stage thresholds (PHASES.md §2). */
        public Tension tension = new Tension();

        /**
         * Per-stage overrides of director/Stage.java's balance table; entry {@code i} overrides stage {@code i}.
         * Missing fields (or missing entries) fall back to the built-in table values, so leave this
         * array away entirely to use the defaults.
         */
        public StageOverride[] stages = new StageOverride[6];
    }

    /** Activation of the mod after the first full Minecraft day. */
    public static final class Activation {
        /** Physical elapsed server ticks during PHASE 0 before the IT JOINED sequence may fire (24000 = one day). */
        public int afterTicks = 24000;
        /** Require night (time of day 13000..23000) in the Overworld for the automatic activation. */
        public boolean requireNight = true;
    }

    /** Escalation of tension driving the stage auto-promotions. */
    public static final class Tension {
        /** Scales every source of tension. */
        public double escalationSpeed = 1.0;
        /** Tension per second for playing at night in the Overworld. */
        public double nightPerSec = 0.03;
        /** Tension per second for standing in darkness (block light < 4) at night. */
        public double darknessPerSec = 0.10;
        /** Tension per second for being underground (Y below {@code mineMaxY}), day or night. */
        public double undergroundPerSec = 0.12;
        /** Stage threshold i promotes to stage i + 1 when {@code tension >= thresholds[i]}. */
        public List<Integer> thresholds = new ArrayList<>(List.of(20, 40, 55, 70, 85));
    }

    /** Optional per-stage overrides; all fields nullable, null = use the built-in stage table. */
    public static final class StageOverride {
        /** Haunt appearance distance range (blocks). */
        public Integer hauntMinDist;
        public Integer hauntMaxDist;
        /** Cooldown between hauntings per player (seconds). */
        public Integer hauntCooldownMinSec;
        public Integer hauntCooldownMaxSec;
        /** Tall One may start chasing. */
        public Boolean chaseEnabled;
        public Double chaseChance;
        public Boolean sinkEnabled;
        /** Cave digging events. */
        public Boolean digEnabled;
        public Boolean peekEnabled;
        /** 0 = none, 1 = sound only, 2 = sound + figure, 3 = break glass. */
        public Integer windowKnockLevel;
        public Boolean scareTextEnabled;
        public Double scareTextChancePerHideEvent;
        public Boolean jumpscareEnabled;
        /** Chance for a false presence sighting (0..1). */
        public Double falsePresenceChance;
        /** 2 = none, 3 = mine only, 4 = mine + base, 5 = mine + base + base lights. */
        public Integer torchDouseLevel;
        /** 0 = none, 1 = exit + lights, 2 = + dont_look, 3 = + hide_and_seek. */
        public Integer tasksFlag;
    }

    private ItWasTallConfig() {
    }

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                try (Reader r = Files.newBufferedReader(FILE)) {
                    Values v = GSON.fromJson(r, Values.class);
                    if (v != null) INSTANCE = v;
                }
            }
            save();
        } catch (Exception e) {
            ItWasTall.LOGGER.error("Could not load config, using defaults", e);
        }
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer w = Files.newBufferedWriter(FILE)) {
                GSON.toJson(INSTANCE, w);
            }
        } catch (IOException e) {
            ItWasTall.LOGGER.error("Could not save config", e);
        }
    }
}
