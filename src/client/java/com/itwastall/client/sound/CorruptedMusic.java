package com.itwastall.client.sound;

import com.itwastall.client.JournalData;
import com.itwastall.config.ItWasTallConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.MusicSound;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;

/**
 * CORRUPTED MUSIC: when the game is about to play one of its own songs, sometimes (more often in every stage,
 * always on fog nights and the last night) it plays it WRONG instead — the original track, slowed down, warbling like
 * a stretched tape, dropping out, stopping like a dying tape deck and starting again. No sound file of the game is
 * copied: it is the game's own music, bent while it plays.
 */
public final class CorruptedMusic {
    private static final float[] CHANCE = {0f, 0.2f, 0.4f, 0.6f, 0.85f, 1f};
    /** Set by fog nights / the last night: every song comes out corrupted. */
    public static volatile boolean forced;
    private static Track current;

    private CorruptedMusic() {}

    public static boolean isPlaying() {
        MinecraftClient client = MinecraftClient.getInstance();
        return current != null && !current.isDone() && client.getSoundManager().isPlaying(current);
    }

    /** MusicTracker is about to play this song: play it corrupted instead? True = done, skip the normal one. */
    public static boolean tryReplace(MusicSound music) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null || !ItWasTallConfig.INSTANCE.corruptedMusic) return false;
        int stage = MathHelper.clamp(JournalData.stage(), 0, CHANCE.length - 1);
        float chance = forced ? 1f : (JournalData.phase() == 1 ? CHANCE[stage] : 0f);
        if (client.world.random.nextFloat() >= chance) return false;
        if (isPlaying()) return true;
        current = new Track(music.getSound().value(), stage);
        client.getSoundManager().play(current);
        return true;
    }

    public static void stop() {
        if (current != null) {
            MinecraftClient.getInstance().getSoundManager().stop(current);
            current = null;
        }
    }

    /** The song, bent every tick: slower and deeper, wow and flutter, drop-outs, tape stops, sudden jumps. */
    private static final class Track extends MovingSoundInstance {
        private final float base;
        private final int stage;
        private int t, tapeStop = -1, dropout, jump;

        Track(SoundEvent event, int stage) {
            super(event, SoundCategory.MUSIC, SoundInstance.createRandom());
            this.stage = stage;
            this.base = 0.93f - 0.05f * stage;
            this.repeat = false;
            this.relative = true;
            this.attenuationType = AttenuationType.NONE;
            this.volume = 0.01f;
            this.pitch = base;
        }

        @Override
        public boolean shouldAlwaysPlay() {
            return true;
        }

        @Override
        public void tick() {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null) {
                setDone();
                return;
            }
            t++;
            Random r = this.random;
            float p = base + 0.025f * MathHelper.sin(t * 0.31f) + 0.012f * MathHelper.sin(t * 1.7f);
            float v = Math.min(1f, t / 60f);                       // fade in
            if (tapeStop < 0 && r.nextInt(900) == 0) tapeStop = 0;  // the tape deck dies...
            if (tapeStop >= 0) {
                tapeStop++;
                if (tapeStop <= 30) p *= 1f - 0.5f * (tapeStop / 30f);
                else if (tapeStop <= 42) v = 0f;                    // ...silence...
                else tapeStop = -1;                                 // ...and it starts again
            }
            if (dropout > 0) {
                dropout--;
                v = 0f;
            } else if (r.nextInt(400) == 0) {
                dropout = 2 + r.nextInt(5);
            }
            if (stage >= 4) {
                if (jump > 0) {
                    jump--;
                    p *= 1.25f;
                } else if (r.nextInt(600) == 0) {
                    jump = 20;
                }
            }
            this.pitch = MathHelper.clamp(p, 0.5f, 2f);
            this.volume = v;
        }
    }
}
