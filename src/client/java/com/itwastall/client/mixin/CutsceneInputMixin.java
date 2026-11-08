package com.itwastall.client.mixin;

import com.itwastall.client.ending.CutsceneCamera;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The player stands still while the cutscene plays. */
@Mixin(KeyboardInput.class)
public abstract class CutsceneInputMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void itwastall$standStill(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
        if (!CutsceneCamera.active()) return;
        Input in = (Input) (Object) this;
        in.movementForward = 0;
        in.movementSideways = 0;
        in.pressingForward = in.pressingBack = in.pressingLeft = in.pressingRight = false;
        in.jumping = false;
        in.sneaking = false;
    }
}
