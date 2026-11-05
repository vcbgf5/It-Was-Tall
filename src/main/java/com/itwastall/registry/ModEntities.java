package com.itwastall.registry;

import com.itwastall.ItWasTall;
import com.itwastall.entity.TallOneEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModEntities {
    public static final EntityType<TallOneEntity> TALL_ONE = Registry.register(
            Registries.ENTITY_TYPE,
            ItWasTall.id("tall_one"),
            EntityType.Builder.create(TallOneEntity::new, SpawnGroup.MONSTER)
                    .dimensions(0.7f, 3.4f)
                    .eyeHeight(3.1f)
                    .maxTrackingRange(16)
                    .build("tall_one"));

    /** "Old One" — the Skinwalker Steve: a player-sized thing wearing a skin, with It inside. */
    public static final EntityType<com.itwastall.entity.OldOneEntity> OLD_ONE = Registry.register(
            Registries.ENTITY_TYPE,
            ItWasTall.id("old_one"),
            EntityType.Builder.create(com.itwastall.entity.OldOneEntity::new, SpawnGroup.MONSTER)
                    .dimensions(0.6f, 1.8f)
                    .eyeHeight(1.62f)
                    .maxTrackingRange(10)
                    .build("old_one"));

    private ModEntities() {
    }

    public static void init() {
        FabricDefaultAttributeRegistry.register(TALL_ONE, TallOneEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(OLD_ONE, com.itwastall.entity.OldOneEntity.createAttributes());
    }
}
