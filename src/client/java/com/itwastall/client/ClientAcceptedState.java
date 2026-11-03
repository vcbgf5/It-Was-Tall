package com.itwastall.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.itwastall.ItWasTall;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Client-local acceptance of the jumpscare warning (config/itwastall_client.json).
 *
 * <p>The warning screen shows ONCE per machine: accept it and never be bothered again;
 * the file is per launch dir ("first entry into the world"), so a multi-player PC sees
 * it on each user's first contact with the mod. The scares toggle itself lives on the
 * server (ScaresPrefPayload) — this file only remembers that you answered the screen.
 */
public final class ClientAcceptedState {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("itwastall_client.json");

    private static boolean loaded;
    private static boolean accepted;

    private ClientAcceptedState() {
    }

    public static boolean accepted() {
        ensureLoaded();
        return accepted;
    }

    public static void setAccepted(boolean value) {
        accepted = value;
        if (Files.notExists(FILE)) {
            try {
                Files.createDirectories(FILE.getParent());
            } catch (IOException e) {
                ItWasTall.LOGGER.error("Could not create config dir", e);
            }
        }
        try (Writer w = Files.newBufferedWriter(FILE)) {
            GSON.toJson(new Data(accepted), w);
        } catch (IOException e) {
            ItWasTall.LOGGER.error("Could not save the warning acceptance", e);
        }
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try {
            if (Files.exists(FILE)) {
                try (Reader r = Files.newBufferedReader(FILE)) {
                    Data data = GSON.fromJson(r, Data.class);
                    accepted = data != null && data.accepted;
                }
            }
        } catch (Exception e) {
            ItWasTall.LOGGER.error("Could not read the warning acceptance, showing the screen again", e);
            accepted = false;
        }
    }

    private record Data(boolean accepted) {
    }
}
