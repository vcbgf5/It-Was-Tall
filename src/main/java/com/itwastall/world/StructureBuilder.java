package com.itwastall.world;

import com.itwastall.ItWasTall;
import net.minecraft.block.Block;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;

import java.util.List;
import java.util.Optional;

/**
 * Places one of the mod's structure templates (data/itwastall/structure/*.nbt) right away — for the
 * test command /itwastall event build and for the night-time dynamic structures. Chests keep their
 * loot tables, so the hint books are there when you open them.
 */
public final class StructureBuilder {
    public static final List<String> NAMES = List.of("abandoned_cabin", "roadside_shrine", "torch_tunnel", "sign_room",
            "crystal_grotto", "deep_lair", "counting_stones", "guest_house", "dream_well");

    private StructureBuilder() {
    }

    /** How far below the ground the template starts (the same offsets as in worldgen). */
    public static int yOffset(String name) {
        return "dream_well".equals(name) ? -5 : -1;
    }

    /** Places {@code name} with its ground row one below {@code ground}'s y (offset applied), centred on it. */
    public static boolean placeAt(ServerWorld world, String name, BlockPos ground, BlockRotation rotation) {
        Optional<StructureTemplate> t = world.getStructureTemplateManager().getTemplate(ItWasTall.id(name));
        if (t.isEmpty()) return false;
        Vec3i size = t.get().getSize();
        BlockPos origin = ground.add(-size.getX() / 2, yOffset(name) + 1, -size.getZ() / 2);
        StructurePlacementData data = new StructurePlacementData().setIgnoreEntities(true).setRotation(rotation);
        // rotate around the template's centre so it stays where it was asked for
        BlockPos pivot = new BlockPos(size.getX() / 2, 0, size.getZ() / 2);
        data.setPosition(pivot);
        return t.get().place(world, origin, origin, data, world.getRandom(), Block.NOTIFY_LISTENERS);
    }

    /** /itwastall event build NAME: about 8 blocks in front of the player, at their feet level. */
    public static boolean placeInFront(ServerPlayerEntity player, String name) {
        Direction f = player.getHorizontalFacing();
        BlockPos ground = player.getBlockPos().offset(f, 9).down();
        BlockRotation rot = switch (f) {
            case SOUTH -> BlockRotation.CLOCKWISE_180;
            case WEST -> BlockRotation.COUNTERCLOCKWISE_90;
            case EAST -> BlockRotation.CLOCKWISE_90;
            default -> BlockRotation.NONE;
        };
        return placeAt(player.getServerWorld(), name, ground, rot);
    }
}
