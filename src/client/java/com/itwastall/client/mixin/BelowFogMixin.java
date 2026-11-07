package com.itwastall.client.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** THE BELOW: a black fog closes in after a couple of dozen blocks — the caves have no far end you can see. */
@Mixin(BackgroundRenderer.class)
public abstract class BelowFogMixin {
    @Shadow
    private static float red;
    @Shadow
    private static float green;
    @Shadow
    private static float blue;

    private static boolean itwastall$below() {
        ClientWorld w = MinecraftClient.getInstance().world;
        return w != null && w.getRegistryKey() == com.itwastall.registry.ModDimensions.BELOW;
    }

    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private static void itwastall$blackFog(Camera camera, float tickDelta, ClientWorld world, int viewDistance, float skyDarkness, CallbackInfo ci) {
        if (!itwastall$below()) return;
        red = 0.01f;
        green = 0.01f;
        blue = 0.012f;
        RenderSystem.clearColor(red, green, blue, 0.0f);
    }

    @Inject(method = "applyFog", at = @At("TAIL"), require = 0)
    private static void itwastall$closeFog(Camera camera, BackgroundRenderer.FogType fogType, float viewDistance, boolean thickFog, float tickDelta, CallbackInfo ci) {
        if (!itwastall$below() || camera.getSubmersionType() != net.minecraft.block.enums.CameraSubmersionType.NONE) return;
        RenderSystem.setShaderFogStart(fogType == BackgroundRenderer.FogType.FOG_SKY ? 0f : 4f);
        RenderSystem.setShaderFogEnd(Math.min(viewDistance, 30f));
    }
}
