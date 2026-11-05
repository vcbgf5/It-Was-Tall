package com.itwastall.registry;

import com.itwastall.ItWasTall;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public final class ModSounds {
    public static final SoundEvent STARE = register("entity.tall_one.stare");
    public static final SoundEvent SCREAM = register("entity.tall_one.scream");
    public static final SoundEvent STEP = register("entity.tall_one.step");
    public static final SoundEvent SINK = register("entity.tall_one.sink");
    public static final SoundEvent HURT = register("entity.tall_one.hurt");
    public static final SoundEvent ATTACK = register("entity.tall_one.attack");
    public static final SoundEvent KNOCK = register("entity.tall_one.knock");
    public static final SoundEvent DIG = register("entity.tall_one.dig");
    public static final SoundEvent JUMPSCARE = register("jumpscare");

    public static final SoundEvent AMBIENT_DRONE = register("ambient.drone");
    public static final SoundEvent AMBIENT_WIND = register("ambient.wind");
    public static final SoundEvent AMBIENT_CREAK = register("ambient.creak");
    public static final SoundEvent AMBIENT_DISTANT_SCREAM = register("ambient.distant_scream");
    public static final SoundEvent AMBIENT_WHISPER = register("ambient.whisper");
    public static final SoundEvent AMBIENT_BREATH = register("ambient.breath");
    public static final SoundEvent AMBIENT_HEARTBEAT = register("ambient.heartbeat");
    public static final SoundEvent AMBIENT_STING = register("ambient.sting");
    public static final SoundEvent AMBIENT_KNOCK_DISTANT = register("ambient.knock_distant");
    public static final SoundEvent AMBIENT_CAVE_DRIP = register("ambient.cave_drip");
    public static final SoundEvent AMBIENT_ROCK_FALL = register("ambient.rock_fall");
    public static final SoundEvent AMBIENT_CHAINS = register("ambient.chains");
    public static final SoundEvent AMBIENT_HUM = register("ambient.hum");
    public static final SoundEvent AMBIENT_REVERSED_VOICE = register("ambient.reversed_voice");

    public static final SoundEvent GROWL = register("entity.tall_one.growl");
    public static final SoundEvent SNIFF = register("entity.tall_one.sniff");
    public static final SoundEvent SCRAPE = register("entity.tall_one.scrape");

    // small scares
    public static final SoundEvent SCARE_TWIG = register("scare.twig");
    public static final SoundEvent SCARE_PEBBLE = register("scare.pebble");
    public static final SoundEvent SCARE_DOOR = register("scare.door");
    public static final SoundEvent SCARE_GLASS_TAP = register("scare.glass_tap");
    public static final SoundEvent SCARE_KNOCK = register("scare.knock");
    public static final SoundEvent SCARE_FOOTSTEPS = register("scare.footsteps");
    public static final SoundEvent SCARE_WHISPER_RUN = register("scare.whisper_run");
    public static final SoundEvent SCARE_GIGGLE = register("scare.giggle");
    public static final SoundEvent SCARE_METAL = register("scare.metal");
    public static final SoundEvent SCARE_STATIC = register("scare.static");
    public static final SoundEvent SCARE_BREATH = register("scare.breath");
    public static final SoundEvent SCARE_MUSIC_BOX = register("scare.music_box");

    // ui (played on the client)
    public static final SoundEvent UI_MESSAGE = register("ui.message");
    public static final SoundEvent UI_TINNITUS = register("ui.tinnitus");
    // TODO: zamienić na dedykowany plik ogg dla IT JOINED (teraz niski ambient drone)
    public static final SoundEvent UI_IT_JOINED = register("ui.it_joined");
    public static final SoundEvent OLD_ONE_TEAR = register("entity.old_one.tear");

    private ModSounds() {
    }

    private static SoundEvent register(String name) {
        Identifier id = ItWasTall.id(name);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    public static void init() {
    }

    /** MODPLAN §19 "głośność efektów": scales a volume by config {@code effectsVolume} (0..2). */
    public static float vol(float volume) {
        double m = com.itwastall.config.ItWasTallConfig.INSTANCE.effectsVolume;
        return (float) (volume * Math.max(0.0, Math.min(2.0, m)));
    }
}
