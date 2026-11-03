package com.itwastall.client;

import com.itwastall.client.hud.ItJoinedOverlay;
import com.itwastall.client.hud.JournalScreen;
import com.itwastall.client.hud.JumpscareOverlay;
import com.itwastall.client.hud.ScareTextOverlay;
import com.itwastall.client.hud.WarningScreen;
import com.itwastall.client.render.BaseDebugRenderer;
import com.itwastall.client.render.FalsePresenceRenderer;
import com.itwastall.client.render.TallOneRenderer;
import com.itwastall.net.BaseDebugPayload;
import com.itwastall.net.FalsePresencePayload;
import com.itwastall.net.ItJoinedPayload;
import com.itwastall.net.JumpscarePayload;
import com.itwastall.net.ScareTextPayload;
import com.itwastall.registry.ModEntities;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class ItWasTallClient implements ClientModInitializer {
    private static KeyBinding journalKey;

    @Override
    public void onInitializeClient() {

        journalKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.itwastall.open_journal", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J,
                "category.itwastall"));

        EntityRendererRegistry.register(
                ModEntities.TALL_ONE,
                TallOneRenderer::new
        );

        FalsePresenceRenderer.init();
        BaseDebugRenderer.init();

        ClientPlayNetworking.registerGlobalReceiver(
                JumpscarePayload.ID,
                (payload, context) -> context.client().execute(() ->
                        JumpscareOverlay.trigger(
                                payload.durationMs(),
                                payload.showImage()
                        )
                )
        );

        ClientPlayNetworking.registerGlobalReceiver(
                ScareTextPayload.ID,
                (payload, context) -> context.client().execute(() ->
                        ScareTextOverlay.trigger(
                                payload.text(),
                                payload.durationMs()
                        )
                )
        );

        ClientPlayNetworking.registerGlobalReceiver(
                ItJoinedPayload.ID,
                (payload, context) -> context.client().execute(() ->
                        ItJoinedOverlay.trigger(payload.durationMs())
                )
        );

        // False Presence
        ClientPlayNetworking.registerGlobalReceiver(
                FalsePresencePayload.ID,
                (payload, context) -> context.client().execute(() ->
                        FalsePresenceRenderer.show(
                                payload.x(),
                                payload.y(),
                                payload.z(),
                                payload.yaw(),
                                payload.durationTicks(),
                                payload.window()
                        )
                )
        );

        // Base Debug
        ClientPlayNetworking.registerGlobalReceiver(
                BaseDebugPayload.ID,
                (payload, context) -> context.client().execute(() ->
                        BaseDebugRenderer.show(
                                payload.anchor(),
                                payload.markers(),
                                payload.ttlSeconds()
                        )
                )
        );

        // WARNING SCREEN
        // Retry opening the warning after joining a world.
        final int[] warningDelay = {-1};

        ClientPlayConnectionEvents.JOIN.register(
                (handler, sender, client) ->
                        client.execute(() -> warningDelay[0] = 40)
        );

        ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) ->
                        client.execute(() -> warningDelay[0] = -1)
        );

        ClientTickEvents.END_CLIENT_TICK.register(client -> {

            while (journalKey.wasPressed()) {
                if (client.player != null && client.currentScreen == null) {
                    client.setScreen(new JournalScreen());
                }
            }

            if (warningDelay[0] < 0) {
                return;
            }

            if (client.player == null || client.world == null) {
                return;
            }

            if (ClientAcceptedState.accepted()) {
                warningDelay[0] = -1;
                return;
            }

            if (warningDelay[0] > 0) {
                warningDelay[0]--;
                return;
            }

            if (client.currentScreen == null) {
                client.setScreen(new WarningScreen());
                warningDelay[0] = -1;
            }
        });
    }
}
