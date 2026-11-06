package com.itwastall.mixin;

import com.itwastall.god.GodMode;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.EntityTrackerEntry;
import net.minecraft.server.network.PlayerAssociatedNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

/** The god of the session is never sent to the other players' clients: no body, no held item, no armour, no name. */
@Mixin(targets = "net.minecraft.server.world.ServerChunkLoadingManager$EntityTracker")
public abstract class GodTrackerMixin {
    @Shadow
    @Final
    Entity entity;

    @Shadow
    @Final
    EntityTrackerEntry entry;

    @Shadow
    @Final
    private Set<PlayerAssociatedNetworkHandler> listeners;

    @Inject(method = "updateTrackedStatus(Lnet/minecraft/server/network/ServerPlayerEntity;)V", at = @At("HEAD"), cancellable = true)
    private void itwastall$hideGod(ServerPlayerEntity player, CallbackInfo ci) {
        if (entity instanceof ServerPlayerEntity god && god != player
                && GodMode.isGod(god.getUuid()) && !GodMode.isGod(player.getUuid())) {
            if (listeners.remove(player.networkHandler)) entry.stopTracking(player);
            ci.cancel();
        }
    }
}
