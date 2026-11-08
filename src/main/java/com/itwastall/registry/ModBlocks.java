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

    /** A shoe print in the dirt (the empty camp's trail). */
    public static final Block FOOTPRINT = register("footprint", new com.itwastall.block.FootprintBlock(AbstractBlock.Settings.create()
            .mapColor(MapColor.BROWN).noCollision().breakInstantly().sounds(BlockSoundGroup.GRAVEL)
            .nonOpaque().pistonBehavior(PistonBehavior.DESTROY), false));

    /** The tall one's print: long, narrow, far too big. Gone by morning. */
    public static final Block TALL_FOOTPRINT = register("tall_footprint", new com.itwastall.block.FootprintBlock(AbstractBlock.Settings.create()
            .mapColor(MapColor.BLACK).noCollision().breakInstantly().sounds(BlockSoundGroup.GRAVEL)
            .nonOpaque().pistonBehavior(PistonBehavior.DESTROY).ticksRandomly(), true));

    /** A stone with an eye carved into its top (the middle of the animal circle). */
    public static final Block EYE_STONE = register("eye_stone", new Block(AbstractBlock.Settings.create()
            .mapColor(MapColor.STONE_GRAY).strength(1.5f, 6.0f).requiresTool().sounds(BlockSoundGroup.STONE)));

    /** CORRUPTION: a block whose texture is simply wrong (it flickers). Nothing drops from it. */
    public static final Block GLITCH = register("glitch", new Block(AbstractBlock.Settings.create()
            .mapColor(MapColor.MAGENTA).strength(0.6f).sounds(BlockSoundGroup.AMETHYST_BLOCK).dropsNothing()));

    /** CORRUPTION: where the wrongness comes from. Break it and the world mends itself. */
    public static final Block CORRUPTION_SOURCE = register("corruption_source", new Block(AbstractBlock.Settings.create()
            .mapColor(MapColor.BLACK).strength(3.0f, 6.0f).sounds(BlockSoundGroup.SCULK_CATALYST).luminance(s -> 4).dropsNothing()));

    private ModBlocks() {}

    /**
     * CRACKED BEDROCK: deep in the mines the floor of the world sometimes cracks (It dug at it from below). An iron
     * pickaxe breaks it, slowly; under the last of it there is nothing — and below the nothing, THE BELOW.
     */
    public static final Block CRACKED_BEDROCK = register("cracked_bedrock", new Block(AbstractBlock.Settings.create()
            .mapColor(MapColor.DEEPSLATE_GRAY).strength(30.0f, 1200.0f).requiresTool().sounds(BlockSoundGroup.DEEPSLATE)
            .luminance(s -> 3).dropsNothing()));

    /** THE BELOW: the pale, worn stone of the caves under the bedrock (someone scratched tally marks into it). */
    public static final Block FORGOTTEN_STONE = register("forgotten_stone", new Block(AbstractBlock.Settings.create()
            .mapColor(MapColor.STONE_GRAY).strength(3.0f, 6.0f).requiresTool().sounds(BlockSoundGroup.STONE)));

    /** THE BELOW: dark layered rock, older than the world above. */
    public static final Block OLDROCK = register("oldrock", new Block(AbstractBlock.Settings.create()
            .mapColor(MapColor.BLACK).strength(3.5f, 6.0f).requiresTool().sounds(BlockSoundGroup.DEEPSLATE)));

    // ---- what is built from the stones of the Below (recipes in the Forgotten Book) ----
    public static final Block FORGOTTEN_STONE_BRICKS = register("forgotten_stone_bricks", new Block(AbstractBlock.Settings.create()
            .mapColor(MapColor.STONE_GRAY).strength(3.0f, 6.0f).requiresTool().sounds(BlockSoundGroup.STONE)));
    public static final Block OLDROCK_BRICKS = register("oldrock_bricks", new Block(AbstractBlock.Settings.create()
            .mapColor(MapColor.BLACK).strength(3.5f, 6.0f).requiresTool().sounds(BlockSoundGroup.DEEPSLATE_BRICKS)));
    public static final Block CARVED_FORGOTTEN_STONE = register("carved_forgotten_stone", new Block(AbstractBlock.Settings.create()
            .mapColor(MapColor.STONE_GRAY).strength(3.0f, 6.0f).requiresTool().sounds(BlockSoundGroup.STONE)));
    /** Two of them, a lit candle on top: four such pillars around the fire make the ritual. */
    public static final Block RITUAL_PILLAR = register("ritual_pillar", new Block(AbstractBlock.Settings.create()
            .mapColor(MapColor.STONE_GRAY).strength(3.0f, 6.0f).requiresTool().sounds(BlockSoundGroup.STONE)));
    public static final Block OLDROCK_LANTERN = register("oldrock_lantern", new Block(AbstractBlock.Settings.create()
            .mapColor(MapColor.ORANGE).strength(2.0f, 6.0f).requiresTool().sounds(BlockSoundGroup.DEEPSLATE_BRICKS).luminance(s -> 14)));

    private static Block register(String name, Block block) {
        Registry.register(Registries.BLOCK, ItWasTall.id(name), block);
        Registry.register(Registries.ITEM, ItWasTall.id(name), new BlockItem(block, new Item.Settings()));
        return block;
    }

    public static void init() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> {
            entries.add(CHOPPING_STUMP);
            entries.add(BLOOD);
            entries.add(FOOTPRINT);
            entries.add(TALL_FOOTPRINT);
            entries.add(EYE_STONE);
        });
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.NATURAL).register(entries -> {
            entries.add(FORGOTTEN_STONE);
            entries.add(OLDROCK);
            entries.add(CRACKED_BEDROCK);
        });
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.BUILDING_BLOCKS).register(entries -> {
            entries.add(FORGOTTEN_STONE_BRICKS);
            entries.add(OLDROCK_BRICKS);
            entries.add(CARVED_FORGOTTEN_STONE);
            entries.add(RITUAL_PILLAR);
            entries.add(OLDROCK_LANTERN);
        });
    }
}
