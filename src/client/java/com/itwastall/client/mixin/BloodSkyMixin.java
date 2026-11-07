package com.itwastall.client.mixin;

import com.itwastall.client.BloodMoonClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Blood moon: the sky colour (and with it the horizon) turns dark red. */
@Mixin(ClientWorld.class)
public abstract class BloodSkyMixin {
    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
    private void itwastall$redSky(Vec3d cameraPos, float tickDelta, CallbackInfoReturnable<Vec3d> cir) {
        if (!BloodMoonClient.active) return;
        Vec3d c = cir.getReturnValue();
        float[] t = BloodMoonClient.tint((float) c.x, (float) c.y, (float) c.z);
        cir.setReturnValue(new Vec3d(t[0], t[1], t[2]));
    }
}
