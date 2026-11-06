package com.itwastall.entity;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OUTSPACE keeps nothing you spend and takes nothing you own: on waking up, the inventory is put back exactly as
 * it was before the dream (blocks you placed and things you used are back, tools as worn as before) — and
 * whatever you FOUND in the dream (books, crystals, chest loot) comes back with you on top.
 */
public final class DreamInventory {
    private DreamInventory() {}

    /** The whole inventory (main, armour, off hand) as NBT. */
    public static NbtList snapshot(ServerPlayerEntity player) {
        return player.getInventory().writeNbt(new NbtList());
    }

    /**
     * Puts the inventory from before the dream back and adds what was found in the dream.
     *
     * @return true when something found in the dream was added
     */
    public static boolean restore(ServerPlayerEntity player, NbtList saved) {
        PlayerInventory inv = player.getInventory();
        // what the dream left in the pockets now, and what was there before
        List<ItemStack> now = new ArrayList<>();
        for (int i = 0; i < inv.size(); i++) if (!inv.getStack(i).isEmpty()) now.add(inv.getStack(i).copy());
        inv.clear();
        inv.readNbt(saved);
        List<ItemStack> before = new ArrayList<>();
        for (int i = 0; i < inv.size(); i++) if (!inv.getStack(i).isEmpty()) before.add(inv.getStack(i));

        // found = per item type, how many more there are now than before (worn tools do not count as new)
        Map<Item, Integer> extra = new HashMap<>();
        for (ItemStack s : now) extra.merge(s.getItem(), s.getCount(), Integer::sum);
        for (ItemStack s : before) extra.merge(s.getItem(), -s.getCount(), Integer::sum);

        // take the found ones from the dream stacks: first stacks that did not exist before (a new book keeps its text)
        now.sort((a, b) -> Boolean.compare(existedBefore(a, before), existedBefore(b, before)));
        List<ItemStack> found = new ArrayList<>();
        for (ItemStack s : now) {
            int want = extra.getOrDefault(s.getItem(), 0);
            if (want <= 0) continue;
            int take = Math.min(want, s.getCount());
            found.add(s.copyWithCount(take));
            extra.put(s.getItem(), want - take);
        }
        for (ItemStack f : found) {
            if (!inv.insertStack(f) && !f.isEmpty()) player.dropItem(f, false);
        }
        inv.markDirty();
        player.playerScreenHandler.sendContentUpdates();
        return !found.isEmpty();
    }

    private static boolean existedBefore(ItemStack s, List<ItemStack> before) {
        for (ItemStack b : before) if (ItemStack.areItemsAndComponentsEqual(b, s)) return true;
        return false;
    }
}
