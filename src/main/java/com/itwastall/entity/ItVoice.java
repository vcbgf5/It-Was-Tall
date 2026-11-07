package com.itwastall.entity;

import com.itwastall.config.ItWasTallConfig;
import com.itwastall.registry.ModSounds;
import com.itwastall.registry.ModVoices;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * IT SPEAKS: a few words in that slowed-down, far too deep voice. Out of the It standing in the distance
 * ("I'm here.", "I know everything."), at the start of a chase ("Run."), at a knock ("Let me in."), and on its
 * own now and then: a single whisper from somewhere close behind you — in the mines "What are you mining here?".
 */
public final class ItVoice {
    private static final Map<UUID, Long> NEXT_WHISPER = new HashMap<>();
    private static final Map<UUID, Long> LAST_LINE = new HashMap<>();

    private ItVoice() {}

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(ItVoice::tick);
    }

    public static void forget(UUID player) {
        NEXT_WHISPER.remove(player);
        LAST_LINE.remove(player);
    }

    /** One line out of a group, spoken at a point in the world (everyone near hears it). */
    public static void say(ServerWorld world, Vec3d at, String[] group, float volume) {
        if (!ItWasTallConfig.INSTANCE.itVoice) return;
        Random r = world.getRandom();
        SoundEvent line = ModVoices.it(group[r.nextInt(group.length)]);
        if (line == null) return;
        world.playSound(null, at.x, at.y, at.z, line, SoundCategory.HOSTILE, ModSounds.vol(volume), 0.95f + r.nextFloat() * 0.08f);
    }

    /** One line only this player hears, from {@code at} (whispers, the mines). Rate-limited per player. */
    public static boolean whisperTo(ServerPlayerEntity player, Vec3d at, String[] group, float volume) {
        if (!ItWasTallConfig.INSTANCE.itVoice) return false;
        long now = player.getServerWorld().getTime();
        Long last = LAST_LINE.get(player.getUuid());
        if (last != null && now - last < 20 * 12) return false;
        LAST_LINE.put(player.getUuid(), now);
        Random r = player.getRandom();
        SoundEvent line = ModVoices.it(group[r.nextInt(group.length)]);
        if (line == null) return false;
        MineEvents.playTo(player, line, at, ModSounds.vol(volume), 0.95f + r.nextFloat() * 0.08f);
        return true;
    }

    /** Test / god panel: a whisper right now, from just behind the player (ignores the rate limit). */
    public static boolean whisperNow(ServerPlayerEntity p) {
        LAST_LINE.remove(p.getUuid());
        Vec3d back = p.getRotationVec(1f).multiply(-2.5, 0, -2.5);
        String[] group = MineEvents.isUnderground(p) ? ModVoices.IT_CAVE
                : com.itwastall.base.BaseTracker.isInBase(p) ? ModVoices.IT_BASE : ModVoices.IT_ANYWHERE;
        return whisperTo(p, p.getEyePos().add(back.x, 0.2, back.z), group, 0.7f);
    }

    /** Lone whispers: every few minutes on the hunting stages, from just behind the player. */
    private static void tick(MinecraftServer server) {
        if (server.getTicks() % 40 != 0) return;
        ItWasTallConfig.Values cfg = ItWasTallConfig.INSTANCE;
        if (!cfg.itVoice || com.itwastall.director.Director.phase() != 1 || com.itwastall.director.Director.stageIndex() < 3) return;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (!p.isAlive() || p.isSpectator() || p.isCreative()) continue;
            long now = p.getServerWorld().getTime();
            Long next = NEXT_WHISPER.get(p.getUuid());
            if (next == null) {
                NEXT_WHISPER.put(p.getUuid(), now + 20L * 60 * (4 + p.getRandom().nextInt(6)));
                continue;
            }
            if (now < next) continue;
            NEXT_WHISPER.put(p.getUuid(), now + 20L * 60 * (4 + p.getRandom().nextInt(6)));
            if (com.itwastall.net.ModNetworking.scaresDisabled(p.getUuid())) continue;
            Vec3d back = p.getRotationVec(1f).multiply(-2.5, 0, -2.5);
            Vec3d at = p.getEyePos().add(back.x, 0.2, back.z);
            String[] group = MineEvents.isUnderground(p) ? ModVoices.IT_CAVE
                    : com.itwastall.base.BaseTracker.isInBase(p) ? ModVoices.IT_BASE : ModVoices.IT_ANYWHERE;
            whisperTo(p, at, group, 0.55f);
        }
    }
}
