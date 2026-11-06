package com.itwastall.client;

import com.itwastall.client.hud.ItJoinedOverlay;
import com.itwastall.client.hud.JournalScreen;
import com.itwastall.client.hud.JumpscareOverlay;
import com.itwastall.client.hud.ScareTextOverlay;
import com.itwastall.client.hud.TaskToast;
import com.itwastall.client.hud.WarningScreen;
import com.itwastall.client.render.BaseDebugRenderer;
import com.itwastall.client.render.FalsePresenceRenderer;
import com.itwastall.client.render.TallOneRenderer;
import com.itwastall.net.BaseDebugPayload;
import com.itwastall.net.FalsePresencePayload;
import com.itwastall.net.FearStatePayload;
import com.itwastall.net.ItJoinedPayload;
import com.itwastall.net.JumpscarePayload;
import com.itwastall.net.ScareTextPayload;
import com.itwastall.net.TaskToastPayload;
import com.itwastall.net.TaskToastPayload;
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

    /** Lets the journal close itself with the same key that opened it. */
    public static boolean journalKeyMatches(int keyCode, int scanCode) {
        return journalKey != null && journalKey.matchesKey(keyCode, scanCode);
    }

    @Override
    public void onInitializeClient() {
        // the built-in pack "It Was Tall UI": every Minecraft button / slider / checkbox and the title logo in the
        // mod's style; enabled by default, can be switched off in Options > Resource Packs
        net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(com.itwastall.ItWasTall.MOD_ID).ifPresent(mod ->
                net.fabricmc.fabric.api.resource.ResourceManagerHelper.registerBuiltinResourcePack(
                        com.itwastall.ItWasTall.id("itwastall_ui"), mod, net.minecraft.text.Text.literal("It Was Tall UI"),
                        net.fabricmc.fabric.api.resource.ResourcePackActivationType.DEFAULT_ENABLED));

        journalKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.itwastall.open_journal", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J,
                "category.itwastall"));

        EntityRendererRegistry.register(
                ModEntities.TALL_ONE,
                TallOneRenderer::new
        );

        EntityRendererRegistry.register(ModEntities.OLD_ONE, com.itwastall.client.render.OldOneRenderer::new);
        EntityRendererRegistry.register(ModEntities.DEEP_ONE, com.itwastall.client.render.DeepOneRenderer::new);
        EntityRendererRegistry.register(ModEntities.SLEEPER, com.itwastall.client.render.SleeperRenderer::new);

        FalsePresenceRenderer.init();

        // notes (MODPLAN §16): right-click opens the torn-page NoteScreen
        net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, world, hand) -> {
            net.minecraft.item.ItemStack stack = player.getStackInHand(hand);
            if (!com.itwastall.registry.ModItems.isReadable(stack)) {
                return net.minecraft.util.TypedActionResult.pass(net.minecraft.item.ItemStack.EMPTY);
            }
            if (world.isClient) {
                var data = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
                var nbt = data == null ? new net.minecraft.nbt.NbtCompound() : data.copyNbt();
                String title = nbt.getString(com.itwastall.registry.ModItems.NOTE_TITLE);
                String body = nbt.getString(com.itwastall.registry.ModItems.NOTE_BODY);
                net.minecraft.client.MinecraftClient.getInstance().setScreen(
                        new com.itwastall.client.hud.NoteScreen(title.isEmpty() ? "Note" : title, body, null));
                net.minecraft.client.MinecraftClient.getInstance().getSoundManager().play(
                        net.minecraft.client.sound.PositionedSoundInstance.master(net.minecraft.sound.SoundEvents.ITEM_BOOK_PAGE_TURN, 0.8f));
            }
            return net.minecraft.util.TypedActionResult.success(stack, world.isClient);
        });
        BaseDebugRenderer.init();

        ClientPlayNetworking.registerGlobalReceiver(com.itwastall.net.ChasePayload.ID, (payload, context) ->
                context.client().execute(() -> com.itwastall.client.hud.ChaseHud.setActive(payload.active())));

        ClientPlayNetworking.registerGlobalReceiver(com.itwastall.net.JournalPayload.ID, (payload, context) ->
                context.client().execute(() -> JournalData.updateJournal(payload.baseCounts(), payload.events(), payload.tasks())));
        ClientPlayNetworking.registerGlobalReceiver(com.itwastall.net.GuestFigurePayload.ID, (payload, context) ->
                context.client().execute(() -> com.itwastall.client.render.GuestFigures.handle(payload)));
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(c -> com.itwastall.client.render.GuestFigures.tick());
        ClientPlayNetworking.registerGlobalReceiver(com.itwastall.net.SharedJournalPayload.ID, (payload, context) ->
                context.client().execute(() -> JournalData.updateShared(payload.books(), payload.notes())));

        ClientPlayNetworking.registerGlobalReceiver(com.itwastall.net.TaskTimerPayload.ID, (payload, context) ->
                context.client().execute(() -> com.itwastall.client.hud.TaskTimerHud.update(payload)));
        ClientPlayNetworking.registerGlobalReceiver(FearStatePayload.ID, (payload, context) ->
                context.client().execute(() -> JournalData.update(
                        payload.phase(), payload.stage(), payload.tension(),
                        payload.task(), payload.loreIds())));

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
                (payload, context) -> context.client().execute(() -> {
                    if (payload.ttlSeconds() == BaseDebugPayload.OPEN_VIEWER) {
                        context.client().setScreen(new com.itwastall.client.screen.BaseViewerScreen(
                                payload.anchor(), payload.markers()));
                    } else {
                        BaseDebugRenderer.show(payload.anchor(), payload.markers(), payload.ttlSeconds());
                    }
                })
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
                        client.execute(() -> { warningDelay[0] = -1; JournalData.reset(); com.itwastall.client.hud.ChaseHud.reset(); com.itwastall.client.render.GuestFigures.clear(); com.itwastall.client.hud.TaskTimerHud.clear(); })
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

            if (warningDelay[0] > 0) {
                warningDelay[0]--;
                return;
            }

            if (client.currentScreen == null) {
                client.setScreen(new WarningScreen());
                warningDelay[0] = -1;
            }
        });

        // TaskToast (MODPLAN §15/§16): the server's TaskManager feeds the HUD box; rendering
        // itself lives in InGameHudMixin (HEAD), here only the S2C receiver is wired.
        ClientPlayNetworking.registerGlobalReceiver(TaskToastPayload.ID, (payload, context) ->
                context.client().execute(() ->
                        TaskToast.show(payload.kind(), payload.title(), payload.text())));
    }
}
