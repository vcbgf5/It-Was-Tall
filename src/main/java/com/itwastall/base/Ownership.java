package com.itwastall.base;

import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.Set;

/**
 * "Is this the player's own stuff?" — the heart of the smart base detection. A bed, door, window, chest
 * or furnace counts for a player's base only when the player PLACED it (the placement history), or when
 * it is not part of a world-generated structure (an old base built before the mod was installed).
 * A villager's house, a dungeon chest, a ruined portal or a trail-ruin door never makes a base.
 */
public final class Ownership {
    private Ownership() {
    }

    /** Inside one of the pieces of a world-generated structure (village house, dungeon, outpost...). */
    public static boolean inGeneratedStructure(ServerWorld world, BlockPos pos) {
        if (!world.isChunkLoaded(pos)) return false;
        return world.getStructureAccessor().getStructureContaining(pos, structure -> true).hasChildren();
    }

    /** Placed by this player (doors and beds: either half / part). */
    public static boolean placedBy(Set<Long> owned, ServerWorld world, BlockPos pos) {
        if (owned.contains(pos.asLong())) return true;
        BlockState st = world.getBlockState(pos);
        if (st.getBlock() instanceof DoorBlock) return owned.contains(pos.down().asLong()) || owned.contains(pos.up().asLong());
        if (st.getBlock() instanceof BedBlock) {
            for (Direction d : Direction.Type.HORIZONTAL) if (owned.contains(pos.offset(d).asLong())) return true;
        }
        return false;
    }

    /** Counts for the player's base: their own block, or a "wild" one outside every generated structure. */
    public static boolean counts(Set<Long> owned, ServerWorld world, BlockPos pos) {
        return placedBy(owned, world, pos) || !inGeneratedStructure(world, pos);
    }
}
