package com.itwastall.entity;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * Temporary "is the player inside a base" check, until base detection/mapping (BaseMap) is added:
 * the player counts as sheltered when there is a roof above and walls on at least 3 of 4 sides.
 */
public final class ShelterCheck {
    private static final int ROOF_RANGE = 12;
    private static final int WALL_RANGE = 10;

    private ShelterCheck() {
    }

    public static boolean isSheltered(World world, BlockPos feet) {
        BlockPos head = feet.up();
        if (!hasSolid(world, head, Direction.UP, ROOF_RANGE)) return false;
        int walls = 0;
        for (Direction d : Direction.Type.HORIZONTAL) {
            if (hasSolid(world, head, d, WALL_RANGE) || hasSolid(world, feet, d, WALL_RANGE)) walls++;
        }
        return walls >= 3;
    }

    private static boolean hasSolid(World world, BlockPos from, Direction dir, int range) {
        BlockPos.Mutable p = from.mutableCopy();
        for (int i = 0; i < range; i++) {
            p.move(dir);
            var state = world.getBlockState(p);
            if (state.isOpaqueFullCube(world, p) || state.blocksMovement() && !state.isAir()) return true;
        }
        return false;
    }
}
