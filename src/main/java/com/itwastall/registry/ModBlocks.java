package com.itwastall.registry;

import com.itwastall.ItWasTall;
import com.itwastall.block.BloodBlock;
import com.itwastall.block.ChoppingStumpBlock;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;

/** The mod's blocks: the forest chopping block scene (stump with an axe, blood on the ground). */
public final class ModBlocks {
    public static final Block CHOPPING_STUMP = register("chopping_stump", new ChoppingStumpBlock(AbstractBlock.Settings.create()
            .mapColor(MapColor.OAK_TAN).strength(2.0f).sounds(BlockSoundGroup.WOOD).nonOpaque().burnable()));

    public static final Block BLOOD = register("blood", new BloodBlock(AbstractBlock.Settings.create()
            .mapColor(MapColor.DARK_RED).noCollision().breakInstantly().sounds(BlockSoundGroup.HONEY)
            .nonOpaque().pistonBehavior(PistonBehavior.DESTROY)));

    private ModBlocks() {}

    private static Block register(String name, Block block) {
        Registry.register(Registries.BLOCK, ItWasTall.id(name), block);
        Registry.register(Registries.ITEM, ItWasTall.id(name), new BlockItem(block, new Item.Settings()));
        return block;
    }

    public static void init() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> {
            entries.add(CHOPPING_STUMP);
            entries.add(BLOOD);
        });
    }
}
