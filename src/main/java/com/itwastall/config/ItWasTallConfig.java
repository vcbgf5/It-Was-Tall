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
        /** Speed multiplier while chasing. */
        public double chaseSpeed = 1.45;
        /** Ticks between sinking into the ground and the jumpscare. */
        public int jumpscareDelayMinTicks = 10;
        public int jumpscareDelayMaxTicks = 40;
        /** If it is not approached, it gives up staring after this many seconds. */
        public int stareTimeoutSeconds = 180;

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
