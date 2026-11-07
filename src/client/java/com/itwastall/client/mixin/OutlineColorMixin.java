package com.itwastall.client.mixin;

import com.itwastall.client.god.GodClientState;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The prey's outline is blood red (client side only, never changes anything for the others). */
@Mixin(Entity.class)
public abstract class OutlineColorMixin {
    @Inject(method = "getTeamColorValue", at = @At("HEAD"), cancellable = true)
    private void itwastall$preyColor(CallbackInfoReturnable<Integer> cir) {
        Entity self = (Entity) (Object) this;
        if (self.getWorld() != null && self.getWorld().isClient && GodClientState.isLocked(self)) cir.setReturnValue(0xD42A1E);
    }
}
