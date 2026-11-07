package com.itwastall.client.mixin;

import com.itwastall.client.BloodMoonClient;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Blood moon: the fog (and the clear colour behind the sky) turns red as well. */
@Mixin(BackgroundRenderer.class)
public abstract class BloodFogMixin {
    @Shadow
    private static float red;
    @Shadow
    private static float green;
    @Shadow
    private static float blue;

    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private static void itwastall$redFog(Camera camera, float tickDelta, ClientWorld world, int viewDistance, float skyDarkness, CallbackInfo ci) {
        if (!BloodMoonClient.active || camera.getSubmersionType() != net.minecraft.block.enums.CameraSubmersionType.NONE) return;
        float[] t = BloodMoonClient.tint(red, green, blue);
        red = t[0];
        green = t[1];
        blue = t[2];
        RenderSystem.clearColor(red, green, blue, 0.0f);
    }
}
