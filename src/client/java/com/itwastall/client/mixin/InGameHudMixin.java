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
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws the jumpscare after (= on top of) the whole vanilla HUD, including the hotbar and chat. */
@Mixin(InGameHud.class)
public class InGameHudMixin {
    /** While the walkie-talkie slot is selected, the hotbar's own selection frame is not drawn. */
    @Redirect(method = "renderHotbar", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lnet/minecraft/util/Identifier;IIII)V"), require = 0)
    private void itwastall$hideSelection(DrawContext context, net.minecraft.util.Identifier texture, int x, int y, int w, int h) {
        if (com.itwastall.client.hud.WalkieHud.selected && texture.getPath().equals("hud/hotbar_selection")) return;
        context.drawGuiTexture(texture, x, y, w, h);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void itwastall$drawJumpscare(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        ScareTextOverlay.render(context, tickCounter);
        JumpscareOverlay.render(context, tickCounter);
        ItJoinedOverlay.render(context, tickCounter);
        FearHud.render(context, tickCounter);
        ChaseHud.render(context, tickCounter);
        com.itwastall.client.hud.TaskTimerHud.render(context, tickCounter);
        TaskToast.render(context, tickCounter);
        com.itwastall.client.god.GodClientState.renderHint(context);
        com.itwastall.client.hud.RadioHud.render(context);
        com.itwastall.client.hud.WalkieHud.render(context);
    }
}
