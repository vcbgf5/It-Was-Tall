package com.itwastall.entity;

import com.itwastall.config.ItWasTallConfig;
import com.itwastall.director.Director;
import com.itwastall.director.Stage;
import com.itwastall.net.ModNetworking;
import com.itwastall.registry.ModSounds;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Small scares every {@code scareMinSeconds..scareMaxSeconds} (default 20-60 s): short sounds very close to the player,
 * mostly behind them, that only they hear. Also handles the red on-screen messages ("don't hide. just run.").
 */
public final class ScareSounds {
    private static final Map<UUID, State> STATES = new HashMap<>();
    private static final List<PendingMessage> PENDING = new ArrayList<>();

    private static final class State {
        long nextScare = -1;
        long nextMessage = -1;
        int shelteredNightTicks;
    }

    private record PendingMessage(UUID player, String text, long at) {
    }

    private static final SoundEvent[] UNDERGROUND = {ModSounds.SCARE_PEBBLE, ModSounds.SCARE_PEBBLE, ModSounds.SCARE_FOOTSTEPS,
            ModSounds.SCARE_WHISPER_RUN, ModSounds.SCARE_BREATH, ModSounds.SCARE_METAL, ModSounds.SCARE_GIGGLE, ModSounds.SCARE_STATIC,
            ModSounds.SCARE_KNOCK};
    private static final SoundEvent[] OUTSIDE_NIGHT = {ModSounds.SCARE_TWIG, ModSounds.SCARE_TWIG, ModSounds.SCARE_FOOTSTEPS,
            ModSounds.SCARE_GIGGLE, ModSounds.SCARE_WHISPER_RUN, ModSounds.SCARE_BREATH, ModSounds.SCARE_MUSIC_BOX};
    private static final SoundEvent[] SHELTER_NIGHT = {ModSounds.SCARE_GLASS_TAP, ModSounds.SCARE_GLASS_TAP, ModSounds.SCARE_DOOR,
            ModSounds.SCARE_KNOCK, ModSounds.SCARE_FOOTSTEPS, ModSounds.SCARE_BREATH, ModSounds.SCARE_MUSIC_BOX, ModSounds.SCARE_STATIC,
            ModSounds.SCARE_WHISPER_RUN};
    private static final SoundEvent[] DAY = {ModSounds.SCARE_TWIG, ModSounds.SCARE_DOOR, ModSounds.SCARE_KNOCK, ModSounds.SCARE_PEBBLE};

    private ScareSounds() {
    }

    /** Drops per-player data when a player leaves the server. */
    public static void forget(UUID player) {
        STATES.remove(player);
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(ScareSounds::tick);
    }

    // ------------------------------------------------------------------------ tick ----

    private static void tick(MinecraftServer server) {
        long now = server.getOverworld().getTime();
        tickPending(server, now);
        if (server.getTicks() % 20 != 0) return;

        ItWasTallConfig.Values cfg = ItWasTallConfig.INSTANCE;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.isSpectator() || !player.isAlive()) continue;
            State st = STATES.computeIfAbsent(player.getUuid(), u -> new State());
            Random random = player.getRandom();
            World world = player.getWorld();
            boolean underground = MineEvents.isUnderground(player);
            boolean night = world.isNight();
            boolean sheltered = ShelterCheck.isSheltered(world, player.getBlockPos());

            // ---- small scare sounds
            if (st.nextScare < 0) st.nextScare = now + nextScareDelay(random);
            if (cfg.scareSounds && now >= st.nextScare) {
                SoundEvent[] table = underground ? UNDERGROUND : night ? (sheltered ? SHELTER_NIGHT : OUTSIDE_NIGHT) : DAY;
                boolean daySkip = !underground && !night && random.nextInt(3) != 0; // daytime: only 1 in 3 fire
                if (!daySkip) {
                    playScare(player, table[random.nextInt(table.length)], random);
                    if (Director.phase() == 1) Director.addTension(0.5); // PHASES §2: "inne wydarzenie (straszak)" +0.5, 1x na zdarzenie
                }
                st.nextScare = now + nextScareDelay(random);
            }

            // ---- random messages at night / underground
            if (st.nextMessage < 0) st.nextMessage = now + nextMessageDelay(random);
            if (now >= st.nextMessage) {
                if (night || underground) showMessage(player, pickMessage(random));
                st.nextMessage = now + nextMessageDelay(random);
            }

            // ---- hiding in your base at night for a long time
            st.shelteredNightTicks = night && sheltered && !underground ? st.shelteredNightTicks + 20 : 0;
            if (st.shelteredNightTicks >= 20 * 180) {
                st.shelteredNightTicks = 0;
                Stage cur = Director.currentStage();
                // PHASES §3 "ScareText": chance per hide event from the stage table
                // (ETAP 3 = limited 10%, ETAP 4 = 40%, ETAP 5 = 60%). PRE and ETAP 0-2 never
                // show the text (the hide-sound itself keeps its old behaviour).
                if (Director.phase() == 1 && cur.scareTextEnabled() && random.nextFloat() < cur.scareTextChancePerHideEvent()) {
                    playScare(player, ModSounds.SCARE_GLASS_TAP, random);
                    queueMessage(player, cfg.scareText, 30);
                    if (Director.phase() == 1) Director.addTension(0.5); // PHASES §2: straszak +0.5, 1x na zdarzenie
                } else if (random.nextFloat() < 0.4f) {
                    playScare(player, ModSounds.SCARE_GLASS_TAP, random);
                }
            }
        }
    }

    private static void tickPending(MinecraftServer server, long now) {
        for (Iterator<PendingMessage> it = PENDING.iterator(); it.hasNext(); ) {
            PendingMessage m = it.next();
            if (now < m.at()) continue;
            it.remove();
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(m.player());
            if (p != null) showMessage(p, m.text());
        }
    }

    /**
     * Interval between small scares, per stage (local mapping of PHASES.md §3, documented here
     * because the Stage table has no scare-interval getters):
     * FAZA PRE = 90-150 s, ETAP 0 = 90-150 s, ETAP 1 = 40-80 s, ETAP 2 = 8-14 s,
     * ETAP 3+ = config scareMin/MaxSeconds scaled x1 (ETAP 3/4) and x0.5 (ETAP 5, "częstotliwość
     * +50% względem Stage 4"). Config scareMin/MaxSeconds keep working as the ETAP 3+ base.
     */
    private static long nextScareDelay(Random random) {
        ItWasTallConfig.Values cfg = ItWasTallConfig.INSTANCE;
        if (Director.phase() == 0) return 20L * (90 + random.nextInt(61)); // PHASE PRE: as quiet as ETAP 0
        switch (Director.currentStage().ordinal()) {
            case 0: return 20L * (90 + random.nextInt(61));
            case 1: return 20L * (40 + random.nextInt(41));
            case 2: return 20L * (8 + random.nextInt(7));
            default: {
                double scale = Director.currentStage().ordinal() >= 5 ? 0.5 : 1.0;
                int min = Math.max(2, (int) (cfg.scareMinSeconds * scale));
                int max = Math.max(min + 1, (int) (cfg.scareMaxSeconds * scale));
                return 20L * (min + random.nextInt(max - min + 1));
            }
        }
    }

    private static long nextMessageDelay(Random random) {
        ItWasTallConfig.Values cfg = ItWasTallConfig.INSTANCE;
        int min = Math.max(1, cfg.messageMinMinutes);
        int max = Math.max(min, cfg.messageMaxMinutes);
        return 20L * 60 * min + random.nextInt(20 * 60 * (max - min) + 1);
    }

    // --------------------------------------------------------------------- public ----

    /** Plays one random small scare that fits where the player is. */
    public static void playRandomScare(ServerPlayerEntity player) {
        Random random = player.getRandom();
        World world = player.getWorld();
        SoundEvent[] table = MineEvents.isUnderground(player) ? UNDERGROUND
                : ShelterCheck.isSheltered(world, player.getBlockPos()) ? SHELTER_NIGHT : OUTSIDE_NIGHT;
        playScare(player, table[random.nextInt(table.length)], random);
    }

    public static String pickMessage(Random random) {
        ItWasTallConfig.Values cfg = ItWasTallConfig.INSTANCE;
        if (cfg.messages == null || cfg.messages.isEmpty() || random.nextBoolean()) return cfg.scareText;
        return cfg.messages.get(random.nextInt(cfg.messages.size()));
    }

    public static void showMessage(ServerPlayerEntity player, String text) {
        ModNetworking.sendScareText(player, text);
    }

    /** Shows a message after {@code delayTicks}. */
    public static void queueMessage(ServerPlayerEntity player, String text, int delayTicks) {
        PENDING.add(new PendingMessage(player.getUuid(), text, player.getServerWorld().getServer().getOverworld().getTime() + delayTicks));
    }

    // -------------------------------------------------------------------- helpers ----

    private static void playScare(ServerPlayerEntity player, SoundEvent sound, Random random) {
        Vec3d pos;
        if (sound == ModSounds.SCARE_PEBBLE && MineEvents.isUnderground(player)) {
            // falls from the ceiling somewhere close
            pos = player.getEyePos().add(random.nextDouble() * 6 - 3, 2 + random.nextDouble() * 2, random.nextDouble() * 6 - 3);
        } else {
            Vec3d look = player.getRotationVec(1f);
            Vec3d back = new Vec3d(-look.x, 0, -look.z);
            back = back.lengthSquared() < 1.0E-4 ? new Vec3d(1, 0, 0) : back.normalize();
            // mostly behind, sometimes to the side
            double angle = (random.nextDouble() - 0.5) * Math.PI * 0.9;
            Vec3d dir = new Vec3d(back.x * Math.cos(angle) - back.z * Math.sin(angle), 0, back.x * Math.sin(angle) + back.z * Math.cos(angle));
            // "close" (1.5-3 blocks right behind you) is stage-gated (see closeAllowed)
            boolean close = (sound == ModSounds.SCARE_BREATH || sound == ModSounds.SCARE_WHISPER_RUN) && closeAllowed(random);
            double dist = close ? 1.2 + random.nextDouble() : 2 + random.nextDouble() * 4;
            pos = player.getEyePos().add(dir.multiply(dist)).add(0, close ? -0.2 : -1.0, 0);
        }
        float volume = sound == ModSounds.SCARE_BREATH || sound == ModSounds.SCARE_WHISPER_RUN ? 0.6f : 1.0f;
        MineEvents.playTo(player, sound, pos, volume, 0.9f + random.nextFloat() * 0.2f);
    }

    /**
     * Is the "close" scare category allowed right now (PHASES.md §3 / Etapy notes on ScareSounds:
     * blocked until ETAP 2; from ETAP 2 a 40% chance to fire close; from ETAP 4 60%; FAZA PRE and
     * ETAP 0-1 never). Local mapping, documented here — option per audit of the phase system.
     */
    private static boolean closeAllowed(Random random) {
        if (Director.phase() == 0) return false;
        return switch (Director.currentStage().ordinal()) {
            case 0, 1 -> false;
            case 2, 3 -> random.nextFloat() < 0.40f;
            default -> random.nextFloat() < 0.60f;
        };
    }

    /**
     * The "don't hide. just run." text after It sank/peeked at the player's shelter
     * (PHASES §3 row ScareText): only while {@code stage.scareTextEnabled()} — ETAP 3 limited
     * (table chance 0.1), ETAP 4 = 0.4, ETAP 5 = 0.6; FAZA PRE and ETAP 0-2 never.
     *
     * @return true when the message was queued (used by TallOneEntity after a sink/peek)
     */
    public static boolean scareTextAfterHide(ServerPlayerEntity player, Random random) {
        if (Director.phase() != 1) return false;
        Stage st = Director.currentStage();
        if (!st.scareTextEnabled() || random.nextFloat() >= st.scareTextChancePerHideEvent()) return false;
        queueMessage(player, ItWasTallConfig.INSTANCE.scareText, 40);
        return true;
    }

    /** World time of the next small scare (-1 = not scheduled), for /itwastall debug events. */
    public static long nextScareAt(UUID player) {
        State st = STATES.get(player);
        return st == null ? -1 : st.nextScare;
    }
}
