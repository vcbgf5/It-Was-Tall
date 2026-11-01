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

    private ModSounds() {
    }

    private static SoundEvent register(String name) {
        Identifier id = ItWasTall.id(name);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    public static void init() {
    }
}
