package com.itwastall.mixin;

import com.itwastall.base.BaseTracker;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Records successful player block placements on the server, before any phase/stage gate. */
@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
    @Inject(method = "place", at = @At("RETURN"))
    private void itwastall$recordPlacedBlock(ItemPlacementContext context,
            CallbackInfoReturnable<ActionResult> cir) {
        if (!cir.getReturnValue().isAccepted()) return;
        World world = context.getWorld();
        if (world.isClient || !(context.getPlayer() instanceof ServerPlayerEntity player)) return;
        BaseTracker.onBlockPlaced(player, world, context.getBlockPos());
    }
}
