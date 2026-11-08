package com.itwastall.client.mixin;

import com.itwastall.client.ending.CutsceneCamera;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** During the ritual's end the camera is the cutscene's (CutsceneCamera), not the player's eyes. */
@Mixin(Camera.class)
public abstract class CutsceneCameraMixin {
    @Shadow
    protected abstract void setPos(double x, double y, double z);

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "update", at = @At("TAIL"))
    private void itwastall$cutscene(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
        if (!CutsceneCamera.active()) return;
        double[] p = CutsceneCamera.pose();
        if (!CutsceneCamera.active()) return;
        setRotation((float) p[3], (float) p[4]);
        setPos(p[0], p[1], p[2]);
    }
}
