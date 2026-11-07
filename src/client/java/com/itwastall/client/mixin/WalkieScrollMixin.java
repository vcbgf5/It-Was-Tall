package com.itwastall.client.mixin;

import com.itwastall.client.hud.WalkieHud;
import net.minecraft.entity.player.PlayerInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The scroll wheel walks into the walkie-talkie slot after slot 9 (or before slot 1), and back out of it. */
@Mixin(PlayerInventory.class)
public abstract class WalkieScrollMixin {
    @Shadow
    public int selectedSlot;

    @Inject(method = "scrollInHotbar", at = @At("HEAD"), cancellable = true)
    private void itwastall$walkieSlot(double amount, CallbackInfo ci) {
        int right = -(int) Math.signum(amount);   // scrolling down moves right, like vanilla
        if (right == 0) return;
        if (WalkieHud.selected) {
            WalkieHud.select(false);
            selectedSlot = right > 0 ? 0 : 8;
            ci.cancel();
        } else if ((right > 0 && selectedSlot == 8) || (right < 0 && selectedSlot == 0)) {
            WalkieHud.select(true);
            ci.cancel();
        }
    }
}
