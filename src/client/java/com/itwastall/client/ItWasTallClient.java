package com.itwastall.client;

import com.itwastall.client.hud.JumpscareOverlay;
import com.itwastall.client.render.TallOneRenderer;
import com.itwastall.net.JumpscarePayload;
import com.itwastall.registry.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

public class ItWasTallClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.TALL_ONE, TallOneRenderer::new);
        HudRenderCallback.EVENT.register(JumpscareOverlay::render);
        ClientPlayNetworking.registerGlobalReceiver(JumpscarePayload.ID,
                (payload, context) -> context.client().execute(() -> JumpscareOverlay.trigger(payload.durationMs(), payload.showImage())));
    }
}
