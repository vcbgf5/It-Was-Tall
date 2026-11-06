package com.itwastall.registry;

import com.itwastall.ItWasTall;
import com.itwastall.world.OutspaceChunkGenerator;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.World;

/** OUTSPACE, the dream world: its chunk generator type and the world key (the dimension itself is datapack JSON). */
public final class ModDimensions {
    public static final RegistryKey<World> OUTSPACE = RegistryKey.of(RegistryKeys.WORLD, ItWasTall.id("outspace"));

    private ModDimensions() {
    }

    public static void init() {
        Registry.register(Registries.CHUNK_GENERATOR, ItWasTall.id("outspace"), OutspaceChunkGenerator.CODEC);
    }
}
