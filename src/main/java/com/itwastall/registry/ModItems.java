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

    public static final Item OLD_ONE_SPAWN_EGG = Registry.register(
            Registries.ITEM,
            ItWasTall.id("old_one_spawn_egg"),
            new SpawnEggItem(ModEntities.OLD_ONE, 0x00A8A8, 0x3B3B9E, new Item.Settings()));

    public static final Item DEEP_ONE_SPAWN_EGG = Registry.register(
            Registries.ITEM,
            ItWasTall.id("deep_one_spawn_egg"),
            new SpawnEggItem(ModEntities.DEEP_ONE, 0xA8A39A, 0x3A3532, new Item.Settings()));

    public static final Item SLEEPER_SPAWN_EGG = Registry.register(
            Registries.ITEM,
            ItWasTall.id("sleeper_spawn_egg"),
            new SpawnEggItem(ModEntities.SLEEPER, 0xA01818, 0xE8E8E8, new Item.Settings()));

    /** The only item that can defeat the Tall One. */
    public static final Item WARDING_CRYSTAL = Registry.register(
            Registries.ITEM,
            ItWasTall.id("warding_crystal"),
            new Item(new Item.Settings().maxCount(1)));

    /** A torn note (MODPLAN §16 NoteScreen): right-click to read. Title/body live in CUSTOM_DATA. */
    public static final Item NOTE = Registry.register(
            Registries.ITEM,
            ItWasTall.id("note"),
            new Item(new Item.Settings().maxCount(1)));

    /** Our own book (every book in the mod is one of these): right-click to read; it then goes into
     *  everyone's journal on the server (BOOKS tab). Title/body live in CUSTOM_DATA like a note's. */
    public static final Item LORE_BOOK = Registry.register(
            Registries.ITEM,
            ItWasTall.id("lore_book"),
            new Item(new Item.Settings().maxCount(1)));

    public static final String NOTE_ID = "note_id", NOTE_TITLE = "note_title", NOTE_BODY = "note_body";

    private ModItems() {
    }

    /** Builds a readable note: shown by name in the inventory, opened in the client's NoteScreen. */
    public static net.minecraft.item.ItemStack note(String id, String title, String body) {
        return readable(NOTE, id, title, body);
    }

    /** Builds one of our books (opened in the client's book reader, recorded in the shared journal). */
    public static net.minecraft.item.ItemStack loreBook(String id, String title, String body) {
        return readable(LORE_BOOK, id, title, body);
    }

    /** True for the items that open in the reader and go into the journal when read. */
    public static boolean isReadable(net.minecraft.item.ItemStack stack) {
        return stack.isOf(NOTE) || stack.isOf(LORE_BOOK);
    }

    private static net.minecraft.item.ItemStack readable(Item item, String id, String title, String body) {
        net.minecraft.item.ItemStack stack = new net.minecraft.item.ItemStack(item);
        net.minecraft.nbt.NbtCompound data = new net.minecraft.nbt.NbtCompound();
        data.putString(NOTE_ID, id);
        data.putString(NOTE_TITLE, title);
        data.putString(NOTE_BODY, body);
        stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_DATA, net.minecraft.component.type.NbtComponent.of(data));
        stack.set(net.minecraft.component.DataComponentTypes.ITEM_NAME, net.minecraft.text.Text.literal(title));
        stack.set(net.minecraft.component.DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(java.util.List.of(
                net.minecraft.text.Text.literal("Right-click to read").formatted(net.minecraft.util.Formatting.DARK_GRAY,
                        net.minecraft.util.Formatting.ITALIC),
                net.minecraft.text.Text.literal("Then it is in everyone's journal").formatted(net.minecraft.util.Formatting.DARK_GRAY,
                        net.minecraft.util.Formatting.ITALIC))));
        return stack;
    }

    public static void init() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register(entries -> {
            entries.add(TALL_ONE_SPAWN_EGG);
            entries.add(OLD_ONE_SPAWN_EGG);
            entries.add(DEEP_ONE_SPAWN_EGG);
            entries.add(SLEEPER_SPAWN_EGG);
        });
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(entries -> entries.add(WARDING_CRYSTAL));
    }
}
