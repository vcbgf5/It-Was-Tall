package com.itwastall.registry;

import com.itwastall.ItWasTall;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModItems {
    public static final Item TALL_ONE_SPAWN_EGG = Registry.register(
            Registries.ITEM,
            ItWasTall.id("tall_one_spawn_egg"),
            new SpawnEggItem(ModEntities.TALL_ONE, 0x151313, 0x9a958c, new Item.Settings()));

    /** The only item that can defeat the Tall One. */
    public static final Item WARDING_CRYSTAL = Registry.register(
            Registries.ITEM,
            ItWasTall.id("warding_crystal"),
            new Item(new Item.Settings().maxCount(1)));

    private ModItems() {
    }

    public static void init() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register(entries -> entries.add(TALL_ONE_SPAWN_EGG));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(entries -> entries.add(WARDING_CRYSTAL));
    }
}
