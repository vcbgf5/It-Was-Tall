package com.itwastall.client.mixin;

import com.itwastall.client.hud.FlashlightClient;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Outside creative the middle mouse button (pick block) switches the flashlight instead. */
@Mixin(MinecraftClient.class)
public abstract class PickBlockMixin {
    @Inject(method = "doItemPick", at = @At("HEAD"), cancellable = true)
    private void itwastall$flashlight(CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient) (Object) this;
        if (client.player == null || client.player.isCreative() || client.player.isSpectator()) return;
        FlashlightClient.toggle();
        ci.cancel();
    }
}
