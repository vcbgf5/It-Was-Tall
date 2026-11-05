package com.itwastall.client.mixin;

import com.itwastall.client.hud.ItJoinedOverlay;
import com.itwastall.client.hud.FearHud;
import com.itwastall.client.hud.TaskToast;
import com.itwastall.client.hud.ChaseHud;
import com.itwastall.client.hud.JumpscareOverlay;
import com.itwastall.client.hud.ScareTextOverlay;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws the jumpscare after (= on top of) the whole vanilla HUD, including the hotbar and chat. */
@Mixin(InGameHud.class)
public class InGameHudMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void itwastall$drawJumpscare(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        ScareTextOverlay.render(context, tickCounter);
        JumpscareOverlay.render(context, tickCounter);
        ItJoinedOverlay.render(context, tickCounter);
        FearHud.render(context, tickCounter);
        ChaseHud.render(context, tickCounter);
        TaskToast.render(context, tickCounter);
    }
}
