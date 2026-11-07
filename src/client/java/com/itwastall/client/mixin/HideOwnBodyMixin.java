package com.itwastall.client.mixin;

import com.itwastall.client.god.GodClientState;
import com.itwastall.entity.TallOneEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The god walking as It never sees the It body they are wearing (it stands exactly where they stand). */
@Mixin(EntityRenderDispatcher.class)
public abstract class HideOwnBodyMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void itwastall$hideOwnBody(E entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        if (!GodClientState.itForm || !(entity instanceof TallOneEntity)) return;
        var player = MinecraftClient.getInstance().player;
        if (player != null && entity.squaredDistanceTo(player) < 2.5 * 2.5) cir.setReturnValue(false);
    }
}
