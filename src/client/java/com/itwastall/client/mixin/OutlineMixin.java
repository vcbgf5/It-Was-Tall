package com.itwastall.client.mixin;

import com.itwastall.client.god.GodClientState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The god walking as It sees their prey outlined through everything (only on the god's own screen). */
@Mixin(MinecraftClient.class)
public abstract class OutlineMixin {
    @Inject(method = "hasOutline", at = @At("HEAD"), cancellable = true)
    private void itwastall$preyOutline(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (GodClientState.isLocked(entity)) cir.setReturnValue(true);
    }
}
