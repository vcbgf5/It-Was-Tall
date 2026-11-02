package com.itwastall.client;

import com.itwastall.client.hud.ItJoinedOverlay;
import com.itwastall.client.hud.JumpscareOverlay;
import com.itwastall.client.hud.ScareTextOverlay;
import com.itwastall.client.render.TallOneRenderer;
import com.itwastall.net.ItJoinedPayload;
import com.itwastall.net.JumpscarePayload;
import com.itwastall.net.ScareTextPayload;
import com.itwastall.registry.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class ItWasTallClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.TALL_ONE, TallOneRenderer::new);
        ClientPlayNetworking.registerGlobalReceiver(JumpscarePayload.ID,
                (payload, context) -> context.client().execute(() -> JumpscareOverlay.trigger(payload.durationMs(), payload.showImage())));
        ClientPlayNetworking.registerGlobalReceiver(ScareTextPayload.ID,
                (payload, context) -> context.client().execute(() -> ScareTextOverlay.trigger(payload.text(), payload.durationMs())));
        ClientPlayNetworking.registerGlobalReceiver(ItJoinedPayload.ID,
                (payload, context) -> context.client().execute(() -> ItJoinedOverlay.trigger(payload.durationMs())));
    }
}
