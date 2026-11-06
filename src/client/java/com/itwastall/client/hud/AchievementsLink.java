package com.itwastall.client.hud;

import com.itwastall.ItWasTall;
import net.minecraft.advancement.PlacedAdvancement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.advancement.AdvancementsScreen;
import net.minecraft.client.network.ClientAdvancementManager;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import org.jetbrains.annotations.Nullable;

/** The way between the journal and the advancements screen (L): both open each other. */
public final class AchievementsLink {
    private AchievementsLink() {}

    /** Opens the vanilla advancements screen with the "It Was Tall" tab selected. */
    public static void openItWasTallTab(@Nullable Screen parent) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayNetworkHandler handler = client.getNetworkHandler();
        if (handler == null) return;
        ClientAdvancementManager manager = handler.getAdvancementHandler();
        client.setScreen(new AdvancementsScreen(manager, parent));
        PlacedAdvancement root = manager.getManager().get(ItWasTall.id("root"));
        if (root != null) manager.selectTab(root.getAdvancementEntry(), true);
    }

    /** True when the screen shows the advancements of It Was Tall (to show the journal button there). */
    public static boolean available() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.getNetworkHandler() != null;
    }
}
