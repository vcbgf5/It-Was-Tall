package com.itwastall.voice;

import com.itwastall.ItWasTall;
import com.itwastall.config.ItWasTallConfig;
import com.itwastall.director.Director;
import com.itwastall.entity.EventGate;
import com.itwastall.net.ModNetworking;
import com.itwastall.world.JournalLog;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Heightmap;
import net.minecraft.world.LightType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IT MIMICS YOUR VOICE (config itMimicsVoices, off by default). Short pieces of what players say (0.6-3 s, only
 * the loud ones) are kept in MEMORY only — the last few per player, nothing is ever written to disk — and It plays
 * one back at night, slowed down and too deep, with an echo, from the dark behind a player: your friend's voice
 * calling you from the trees, when your friend is somewhere else.
 */
public final class VoiceMimic {
    private static final int FRAME = 960, MIN_FRAMES = 30, MAX_FRAMES = 150, KEEP = 6;
    private static final Map<UUID, List<short[]>> CURRENT = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> QUIET = new ConcurrentHashMap<>();
    private static final Map<UUID, Deque<short[]>> SNIPPETS = new ConcurrentHashMap<>();
    private static final Map<UUID, String> NAMES = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> NEXT = new HashMap<>();
    private static final Random RANDOM = new Random();

    private VoiceMimic() {}

    /** A running debug recording: every frame of the next 5 s, then played back near the target. */
    private record Recording(List<short[]> frames, long endMs, UUID target, java.util.function.Consumer<String> report) {}
    private static final Map<UUID, Recording> RECORDING = new ConcurrentHashMap<>();
    private static final long RECORD_MS = 5_000;

    static void init() {
        VoiceHooks.mimicNow = p -> play(p, true);
        VoiceHooks.mimicLatestNow = VoiceMimic::playLatest;
        VoiceHooks.recordThenMimic = (from, target, report) -> {
            if (ItWasTallVoicechat.api == null) return false;
            RECORDING.put(from.getUuid(), new Recording(java.util.Collections.synchronizedList(new ArrayList<>()),
                    System.currentTimeMillis() + RECORD_MS, target.getUuid(), report));
            return true;
        };
        ServerTickEvents.END_SERVER_TICK.register(VoiceMimic::tick);
        ServerTickEvents.END_SERVER_TICK.register(VoiceMimic::finishRecordings);
    }

    /** Debug recordings whose 5 s are over: what was said is played back near the target right away. */
    private static void finishRecordings(MinecraftServer server) {
        if (RECORDING.isEmpty()) return;
        long now = System.currentTimeMillis();
        RECORDING.entrySet().removeIf(e -> {
            Recording r = e.getValue();
            if (now < r.endMs()) return false;
            ServerPlayerEntity target = server.getPlayerManager().getPlayer(r.target());
            List<short[]> frames;
            synchronized (r.frames()) {
                frames = new ArrayList<>(r.frames());
            }
            if (frames.isEmpty()) {
                r.report().accept("Nothing was heard in those 5 s (is the microphone on and the player talking?).");
                return true;
            }
            short[] all = new short[frames.size() * FRAME];
            for (int i = 0; i < frames.size(); i++) {
                short[] f = frames.get(i);
                System.arraycopy(f, 0, all, i * FRAME, Math.min(FRAME, f.length));
            }
            Deque<short[]> keep = SNIPPETS.computeIfAbsent(e.getKey(), u -> new ArrayDeque<>());
            synchronized (keep) {
                keep.addLast(all);
                while (keep.size() > KEEP) keep.removeFirst();
            }
            RECORDED_AT.put(e.getKey(), System.currentTimeMillis());
            if (target == null) {
                r.report().accept("Recorded " + frames.size() * 20 / 1000.0 + " s, but the target left.");
            } else if (playVoice(target, e.getKey(), all, true)) {
                r.report().accept("Recorded " + frames.size() * 20 / 1000.0 + " s of voice; It is playing it near "
                        + target.getName().getString() + " now.");
            } else {
                r.report().accept("Recorded, but It could not play it (voice chat not ready).");
            }
            return true;
        });
    }

    /** A debug recording of this player is running (voice chat thread asks before decoding). */
    static boolean isRecording(UUID player) {
        Recording rec = RECORDING.get(player);
        return rec != null && System.currentTimeMillis() < rec.endMs();
    }

    /** One 20 ms frame for the debug recording (every frame, quiet ones too). */
    static void recordDebug(UUID player, short[] pcm) {
        Recording rec = RECORDING.get(player);
        if (rec != null && System.currentTimeMillis() < rec.endMs()) rec.frames().add(pcm.clone());
    }

    /** One 20 ms frame of a player's voice (voice chat thread). */
    static void record(UUID player, short[] pcm) {
        double sum = 0;
        for (short s : pcm) sum += s * (double) s;
        boolean loud = Math.sqrt(sum / Math.max(1, pcm.length)) > 900;
        List<short[]> cur = CURRENT.computeIfAbsent(player, u -> new ArrayList<>());
        if (loud) {
            QUIET.put(player, 0);
            cur.add(pcm.clone());
        } else if (!cur.isEmpty()) {
            cur.add(pcm.clone());
            if (QUIET.merge(player, 1, Integer::sum) > 12) endOfSpeech(player);
        }
        if (cur.size() >= MAX_FRAMES) endOfSpeech(player);
    }

    static void endOfSpeech(UUID player) {
        List<short[]> cur = CURRENT.remove(player);
        QUIET.remove(player);
        if (cur == null || cur.size() < MIN_FRAMES) return;
        short[] all = new short[cur.size() * FRAME];
        int at = 0;
        for (short[] f : cur) {
            System.arraycopy(f, 0, all, at, Math.min(FRAME, f.length));
            at += FRAME;
        }
        Deque<short[]> keep = SNIPPETS.computeIfAbsent(player, u -> new ArrayDeque<>());
        synchronized (keep) {
            keep.addLast(all);
            while (keep.size() > KEEP) keep.removeFirst();
        }
        RECORDED_AT.put(player, System.currentTimeMillis());
    }

    private static void tick(MinecraftServer server) {
        if (server.getTicks() % 40 != 0) return;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) NAMES.put(p.getUuid(), p.getGameProfile().getName());
        ItWasTallConfig.Values cfg = ItWasTallConfig.INSTANCE;
        if (!cfg.itMimicsVoices || Director.phase() != 1 || Director.stageIndex() < 3) return;
        ServerWorld world = server.getOverworld();
        if (!world.isNight()) return;
        long now = world.getTime();
        for (ServerPlayerEntity p : world.getPlayers()) {
            if (p.isCreative() || p.isSpectator()) continue;
            Long next = NEXT.get(p.getUuid());
            if (next == null) {
                NEXT.put(p.getUuid(), now + 20L * 60 * (6 + RANDOM.nextInt(7)));
                continue;
            }
            if (now < next || !EventGate.ready(p)) continue;
            NEXT.put(p.getUuid(), now + 20L * 60 * (6 + RANDOM.nextInt(7)));
            if (play(p, false)) EventGate.mark(p);
        }
    }

    /** Plays one stolen voice piece in the dark near the player (another player's voice if there is one). */
    static boolean play(ServerPlayerEntity target, boolean forced) {
        VoicechatServerApi api = ItWasTallVoicechat.api;
        if (api == null || (!forced && !ItWasTallConfig.INSTANCE.itMimicsVoices)) return false;
        UUID from = null;
        short[] voice = null;
        List<UUID> owners = new ArrayList<>(SNIPPETS.keySet());
        owners.sort((a, b) -> Boolean.compare(a.equals(target.getUuid()), b.equals(target.getUuid())));   // someone else first
        for (UUID owner : owners) {
            Deque<short[]> keep = SNIPPETS.get(owner);
            if (keep == null) continue;
            synchronized (keep) {
                if (keep.isEmpty()) continue;
                List<short[]> list = new ArrayList<>(keep);
                voice = list.get(RANDOM.nextInt(list.size()));
            }
            from = owner;
            break;
        }
        if (voice == null) return false;
        return playVoice(target, from, voice, forced);
    }

    /** Debug: the piece recorded last (by anyone) is played near the target now, whatever the config says. */
    private static final Map<UUID, Long> RECORDED_AT = new ConcurrentHashMap<>();

    static boolean playLatest(ServerPlayerEntity target) {
        UUID newest = null;
        long best = Long.MIN_VALUE;
        for (Map.Entry<UUID, Long> e : RECORDED_AT.entrySet()) {
            if (e.getValue() > best && SNIPPETS.containsKey(e.getKey())) {
                best = e.getValue();
                newest = e.getKey();
            }
        }
        if (newest == null) return false;
        Deque<short[]> keep = SNIPPETS.get(newest);
        short[] voice;
        synchronized (keep) {
            voice = keep.peekLast();
        }
        return voice != null && playVoice(target, newest, voice, true);
    }

    /** It plays this voice piece (recorded from {@code from}) somewhere dark behind the target. */
    private static boolean playVoice(ServerPlayerEntity target, UUID from, short[] voice, boolean forced) {
        VoicechatServerApi api = ItWasTallVoicechat.api;
        if (api == null) return false;
        ServerWorld world = target.getServerWorld();
        BlockPos spot = darkSpotBehind(world, target);
        if (spot == null && forced) spot = BlockPos.ofFloored(target.getPos().subtract(target.getRotationVector().multiply(7, 0, 7)));
        if (spot == null) return false;
        LocationalAudioChannel channel = api.createLocationalAudioChannel(UUID.randomUUID(), api.fromServerLevel(world),
                api.createPosition(spot.getX() + 0.5, spot.getY() + 1.6, spot.getZ() + 0.5));
        if (channel == null) return false;
        channel.setCategory(ItWasTallVoicechat.CAT_IT);
        channel.setDistance(32f);
        AudioPlayer player = api.createAudioPlayer(channel, api.createEncoder(), wrong(voice));
        player.startPlaying();
        String name = NAMES.getOrDefault(from, "someone");
        boolean own = from.equals(target.getUuid());
        JournalLog.add(target, own ? "I heard myself talking in the dark. I wasn't talking."
                : "I heard " + name + " calling me from the trees. " + name + " was nowhere near.");
        ItWasTall.LOGGER.info("It played back a voice ({}) near {}.", own ? "their own" : name, target.getName().getString());
        if (!own && !ModNetworking.scaresDisabled(target.getUuid())) com.itwastall.advancement.Achievements.grant(target, "voice");
        return true;
    }

    /** Slower and too deep, a little muffled, with an echo — and padded to whole frames. */
    private static short[] wrong(short[] in) {
        double rate = 0.86;
        int n = (int) (in.length / rate);
        float[] out = new float[n + 9600];
        float lp = 0;
        for (int i = 0; i < n; i++) {
            double src = i * rate;
            int k = (int) src;
            double f = src - k;
            float a = in[Math.min(in.length - 1, k)], b = in[Math.min(in.length - 1, k + 1)];
            lp += 0.45f * ((float) (a + (b - a) * f) - lp);
            out[i] += lp * 0.85f;
            out[i + 7200] += lp * 0.3f;          // a 150 ms echo
        }
        int frames = (out.length + FRAME - 1) / FRAME;
        short[] res = new short[frames * FRAME];
        for (int i = 0; i < out.length; i++) res[i] = (short) Math.max(-32767, Math.min(32767, out[i]));
        return res;
    }

    private static BlockPos darkSpotBehind(ServerWorld world, ServerPlayerEntity p) {
        float yaw = p.getYaw() * MathHelper.RADIANS_PER_DEGREE;
        for (int attempt = 0; attempt < 20; attempt++) {
            double ang = yaw + Math.PI + (RANDOM.nextDouble() - 0.5) * 1.6;
            double d = 9 + RANDOM.nextDouble() * 8;
            int x = MathHelper.floor(p.getX() - Math.sin(ang) * d), z = MathHelper.floor(p.getZ() + Math.cos(ang) * d);
            if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
            int y = com.itwastall.entity.MineEvents.isUnderground(p) ? p.getBlockY() : world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos at = new BlockPos(x, y, z);
            if (world.getLightLevel(LightType.BLOCK, at) <= 5) return at;
        }
        return null;
    }
}
