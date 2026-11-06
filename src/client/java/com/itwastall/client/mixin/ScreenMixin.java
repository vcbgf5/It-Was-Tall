package com.itwastall.client.mixin;

import com.itwastall.client.hud.MenuBackground;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every screen outside a world (world list, create world, options, multiplayer, loading and saving messages)
 * draws the same night-forest background as the title screen instead of the rotating panorama. Vanilla then
 * blurs and darkens it as usual. Inside a world (pause menu, inventories) nothing changes.
 */
@Mixin(Screen.class)
public abstract class ScreenMixin {
    @Inject(method = "renderPanoramaBackground", at = @At("HEAD"), cancellable = true)
    private void itwastall$menuBackground(DrawContext context, float delta, CallbackInfo ci) {
        if (!MenuBackground.enabled()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world != null) return;
        Screen self = (Screen) (Object) this;
        ci.cancel();
        MenuBackground.draw(context, self.width, self.height, 1f);
    }
}
