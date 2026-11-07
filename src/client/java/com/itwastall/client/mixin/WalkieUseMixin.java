package com.itwastall.client.mixin;

import com.itwastall.client.hud.WalkieHud;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While the walkie-talkie slot is selected the right mouse button only talks: the held item is not used. */
@Mixin(MinecraftClient.class)
public abstract class WalkieUseMixin {
    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    private void itwastall$talkInstead(CallbackInfo ci) {
        if (WalkieHud.selected) ci.cancel();
    }
}
