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

    /** Bumped when defaults change in a way old config files should pick up (see {@link #migrate}). */
    private static final int CONFIG_VERSION = 3;

    public static final class Values {
        /** Do not edit: lets the mod update old config files to new defaults. */
        public int configVersion = CONFIG_VERSION;
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
        /** MOVE_UNSEEN (MODPLAN §3): while you look away, a distant It can reappear closer — never in view. */
        public boolean moveUnseen = true;
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
        /** FEINT (MODPLAN §3.2, stage 3+): sometimes it screams and runs at you, then simply is not there. */
        public boolean feints = true;
        /** Chance, once per sighting (after ~1.5 s of you looking at it), that the sighting turns into a feint. */
        public double feintChance = 0.25;
        /** TREE (MODPLAN §3.2, stage 2+): instead of standing in the open it peeks out from behind a tree. */
        public boolean treePeeks = true;
        /** Chance that one haunting tries the tree variant first (falls back to the open field if no tree fits). */
        public double treePeekChance = 0.35;
        /** KNOCK (stage 5): chance that a door-knock house event is the real It standing outside the door. */
        public double realKnockChance = 0.4;

        // ---- the new creatures ----
        /** THE ONE WHO COUNTS: at night, alone outside, a countdown from ten; a figure closer with every number. */
        public boolean countEvents = true;
        /** THE HANGED: someone hangs from a tree at night (stage 2+), wearing your face, until morning. */
        public boolean hangedTrees = true;
        /** THE FOOTPRINTS: huge prints walk up to your door and under your windows at night (gone by morning). */
        public boolean tallFootprints = true;
        /** THE MIMICS: bloodied cows / sheep / pigs that follow you at night (stage 2+), hit once and vanish. */
        public boolean mimics = true;
        /** Minutes between two hanged figures for one player (at most one per night). */
        public int hangedMinMinutes = 15;
        public int hangedMaxMinutes = 25;
        public int countMinStage = 2;
        /** Chance per 10 s check while eligible (night, outside, alone; 25-40 min per player between counts). */
        public double countChance = 0.03;

        /** THE DEEP ONE: crawls on cave ceilings deep underground, douses torches, shakes blocks out of your hands. */
        public boolean deepOne = true;
        public int deepOneMinStage = 3;
        /** Only below this height (the player's feet). */
        public int deepOneMaxY = 0;
        /** Chance per 10 s check while eligible (6-10 min per player between two). */
        public double deepOneChance = 0.12;

        /** THE GUEST: an unknown name in YOUR player list, an echo of your chat, a far figure with its back to you. */
        public boolean guest = true;
        public int guestMinStage = 4;
        /** Chance per 30 s check (40-70 min per player between two visits). */
        public double guestChance = 0.04;

        // ---- OUTSPACE, the dream world ----
        /** Sometimes a sleeper is pulled into the dream maze (no damage, no death, the Old One hunts there). */
        public boolean outspace = true;
        /** First stage at which it can happen. */
        public int outspaceMinStage = 5;
        /** Chance when someone lies down to sleep (at most one player a night on the whole server). */
        public double outspaceChance = 0.35;
        /** How long a dream lasts before you wake up in your bed (seconds). */
        public int outspaceSeconds = 30;
        /** Chance that a room of the maze holds a chest (dream books, torches, rarely a Warding Crystal). */
        public double outspaceLootChance = 0.08;
        /** Share of the great halls of the dream that hold the Sleeper's bed (with a Warding Crystal by it). */
        public double outspaceSleeperChance = 0.35;

        // ---- comfort / server settings (MODPLAN §19) ----
        /** Softer visuals: no flashing frames, gentler vignette and chase pulse, jumpscare image replaced by a dark fade. */
        public boolean photosensitiveMode = false;
        /** Multiplier for the volume of every sound the mod plays to a player (0 = mute the mod, 1 = normal). */
        public double effectsVolume = 1.0;
        /** Minimum seconds between two bigger events (house events, window knocks, false presences, Old One,
         *  dynamic structures) for the same player. 0 = no global gap, only the per-event cooldowns. */
        public int minEventCooldown = 25;
        /** Nothing the mod spawns or places may be farther than this from the player it is meant for (blocks). */
        public int maxEventRange = 100;
        /** The title screen shows the night clearing with the Tall One instead of the panorama (client side). */
        public boolean customTitleScreen = true;
        /** The TASK TIMER in the top-left corner while a task runs (client side). */
        public boolean taskTimerHud = true;
        /** Lets players without operator rights use /itwastall debug ... (read-only views). */
        public boolean debugAllowedForNonOps = false;

        // ---- window knocks (PHASES Poziom 5: MODPLAN §12) ----
        /** The Tall One knocks three times on a window while you are inside your base at night. */
        public boolean windowKnocks = true;
        /** Chance rolled per check (~once per 20 s) while the conditions hold; 1 knock burst per night. */
        public double windowKnockChance = 0.35;

        // ---- the Old One (Skinwalker Steve) ----
        /** A "player" named Old One appears close by and stares; walk up to it and It climbs out of it. */
        public boolean oldOne = true;
        /** First stage at which it can appear. */
        public int oldOneMinStage = 2;
        /** Minutes (per player) before the first one / between two of them. */
        public int oldOneFirstMinutes = 8;
        public int oldOneMinMinutes = 12;
        public int oldOneMaxMinutes = 24;
        /** Only after dark (false = any time; it looks more wrong in daylight). */
        public boolean oldOneOnlyAtNight = false;
        /** How close you get before it tears open. */
        public double oldOneSplitDistance = 4.5;

        // ---- house events (MODPLAN §12 variants + §14 DoorEvent / ChestShuffleEvent / SignEvent) ----
        /** Things happen around your mapped base at night: doors, knocks, chests, signs. */
        public boolean houseEvents = true;
        /** Chance per second (at night, near your base, stage 2+) that one house event starts. */
        public double houseEventChancePerSecond = 0.012;
        /** Minimum seconds between two house events for one player. */
        public int houseEventCooldownSeconds = 150;
        /** A wooden door of your base opens by itself (stage 3+); it may slam shut again (stage 4+). */
        public boolean doorEvents = true;
        /** A few stacks inside one of your chests swap places (stage 3+). Items are never removed. */
        public boolean chestShuffle = true;
        /** A sign with a message appears outside your base (stage 3+). */
        public boolean signEvents = true;
        /** Messages for the sign event: up to 4 lines each, separated by "|". */
        public List<String> signMessages = new ArrayList<>(List.of(
                "I SAW YOU|THROUGH THE|WINDOW", "IT WAS|TALL", "TURN|AROUND", "I KNOW|WHERE YOU|SLEEP",
                "DON'T HIDE|JUST RUN", "LET ME|IN", "I WAS|HERE", "YOU LEFT|THE DOOR|OPEN"));

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
        // ---- base tracking (PHASES Poziom 6: MODPLAN §8) ----
        /** Minimum blocks the base scanner classifies per tick; it goes faster on its own so a full scan
         *  finishes within {@code baseRescanSeconds}, but never above {@code scanMaxBlocksPerTick}. */
        public int scanBlocksPerTick = 64;
        /** Upper limit of blocks per tick for one base scan (all players together: twice this). */
        public int scanMaxBlocksPerTick = 3000;
        /** While you are within 64 blocks of your base it is rescanned this often (seconds). */
        public int baseRescanSeconds = 1;
        /** Horizontal radius (anchor +-radius) of one base scan. */
        public int baseScanRadius = 16;
        /** Hard cap of interior cells flood-fill may mark (MODPLAN §8.3 "ograniczenie objętości"). */
        public int baseInteriorCap = 2400;
        /** Dark interior corners (block light < 4) worth recording for search goals. */
        public int baseCornerCap = 48;

        // ---- tasks (PHASES Poziom 8: MODPLAN §15) ----
        /** Chance per second (at night, sheltered, stage 3+) that a task gets assigned. */
        public double taskChancePerSecond = 0.02;
        /** Seconds one player sits on the task cooldown after completing/failing one. */
        public int taskCooldownSeconds = 300;
        /** "Don't go outside", step 1: seconds the player has to GET inside (a house / under a roof). */
        public int taskGetInsideSeconds = 120;
        /** "Don't go outside", step 2: seconds the player then has to STAY inside (MODPLAN 15.1). */
        public int taskStayInsideSeconds = 10;
        /** "Douse the lights": how many light markers It picks (up to). */
        public int taskLightsCount = 3;
        /** "Douse the lights": seconds until the assignment expires (dawn also kills it). */
        public int taskLightsDeadlineSeconds = 300;

        /** "Hide and seek" (§15.2, ETAP 5): seconds of the initial hide countdown. */
        public int taskHideCountdownSeconds = 30;
        /** Looking at the figure behind a window gives a "don't go outside" task this long (seconds). */
        public int taskWindowLookSeconds = 10;
        /** NIE PATRZ (§15.4, ETAP 4): seconds the watcher stands behind the player. */
        public int taskDontLookSeconds = 30;
        /** OFIARA (§15.5, ETAP 4+): comma-separated possible offering items (vanilla or mod ids). */
        public String taskSacrificeItems = "bread,coal,apple";
        /** OFIARA: how many of the offering item are demanded. */
        public int taskSacrificeCount = 1;
        /** OFIARA: seconds until the unkept offering deal collapses. */
        public int taskSacrificeSeconds = 180;

        // ---- base dousing (§14 "gaszenie pochodni", ETAP 4+) ----
        /** Enables the DouseLightsEvent snuffing mapped base lights at night. */
        public boolean baseDousing = true;
        /** Chance per second (night, sheltered, ETAP 4+) that the burst starts. */
        public double baseDouseChance = 0.25;

        // ---- dynamic structures (§17.1) ----
        /** Enables rare server-built structures (cemetery, shrine, cottage, black copy). */
        public boolean dynamicStructures = true;
        /** Chance per second (night, behind the player, one per night) that a build starts. */
        public double structureChance = 0.02;
        /** Of those builds, the chance it is the base's black copy (needs a mapped base). */
        public double blackCopyChance = 0.25;
        /** How long It walks the rooms searching before it gives up (survive this long). */
        public int taskSeekSeconds = 90;
        /** Seconds between two checked dark corners (step/breath cues play at that spot). */
        public int taskSeekCheckSeconds = 6;
        /** Blocks: player sitting this close to the checked corner is FOUND (chase follows). */
        public double taskSeekFindDistance = 3.5;

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

    /**
     * Version 2: the event limits felt like "it works once, then never again" — the old, tight values in
     * existing config files are loosened once (values the player set by hand to something else stay).
     */
    private static Values migrate(Values v) {
        if (v.configVersion < 2) {
            if (v.minEventCooldown == 45) v.minEventCooldown = 25;
            if (v.houseEventCooldownSeconds == 300) v.houseEventCooldownSeconds = 150;
            if (v.oldOneMinMinutes == 20) v.oldOneMinMinutes = 12;
            if (v.oldOneMaxMinutes == 40) v.oldOneMaxMinutes = 24;
            ItWasTall.LOGGER.info("It Was Tall config updated to version 2 (looser event limits).");
        }
        if (v.configVersion < 3) {
            // "don't go outside" is now: 120 s to get inside, then a short stay of 10 s
            if (v.taskStayInsideSeconds == 120) v.taskStayInsideSeconds = 10;
            if (v.taskWindowLookSeconds == 30) v.taskWindowLookSeconds = 10;
            if (v.taskGetInsideSeconds <= 0) v.taskGetInsideSeconds = 120;
            ItWasTall.LOGGER.info("It Was Tall config updated to version 3 (get inside 120 s, stay inside 10 s).");
        }
        v.configVersion = CONFIG_VERSION;
        return v;
    }

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                try (Reader r = Files.newBufferedReader(FILE)) {
                    Values v = GSON.fromJson(r, Values.class);
                    if (v != null) INSTANCE = migrate(v);
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
