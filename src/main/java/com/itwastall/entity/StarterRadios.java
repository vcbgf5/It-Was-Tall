package com.itwastall.entity;

import com.itwastall.ItWasTall;
import com.itwastall.registry.ModItems;
import com.itwastall.world.ItWasTallState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;

/**
 * Every player starts with an old radio (the first time they join), and right by the world spawn stands a chest
 * with two more — for a friend, or for when yours is lost.
 */
public final class StarterRadios {
    private StarterRadios() {}

    public static void init() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> {
            ServerPlayerEntity p = handler.getPlayer();
            if (ItWasTallState.getOrCreate(server).claimStarterRadio(p.getUuid())) {
                ItemStack radio = new ItemStack(ModItems.RADIO);
                if (!p.getInventory().insertStack(radio)) p.dropItem(radio, false);
            }
        }));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 100 != 20) return;
            ItWasTallState state = ItWasTallState.getOrCreate(server);
            if (state.spawnRadioChestPlaced()) return;
            ServerWorld world = server.getOverworld();
            BlockPos spawn = world.getSpawnPos();
            if (!world.isChunkLoaded(spawn.getX() >> 4, spawn.getZ() >> 4)) return;
            for (int[] off : new int[][]{{2, 1}, {-2, 1}, {1, -2}, {-1, 2}, {3, 0}, {0, 3}}) {
                int x = spawn.getX() + off[0], z = spawn.getZ() + off[1];
                BlockPos at = new BlockPos(x, world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z), z);
                if (!world.getBlockState(at).isReplaceable() || !world.getBlockState(at.down()).getFluidState().isEmpty()) continue;
                world.setBlockState(at, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.Type.HORIZONTAL.random(world.getRandom())), Block.NOTIFY_ALL);
                if (world.getBlockEntity(at) instanceof ChestBlockEntity chest) {
                    chest.setStack(12, new ItemStack(ModItems.RADIO));
                    chest.setStack(14, new ItemStack(ModItems.RADIO));
                    chest.markDirty();
                }
                state.markSpawnRadioChest();
                ItWasTall.LOGGER.info("Placed the chest with two radios by the spawn at {}.", at.toShortString());
                return;
            }
        });
    }
}
