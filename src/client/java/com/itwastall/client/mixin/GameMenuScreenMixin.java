package com.itwastall.client.mixin;

import com.itwastall.ItWasTall;
import com.itwastall.client.JournalData;
import com.itwastall.client.hud.JournalScreen;
import com.itwastall.client.hud.MenuBackground;
import com.itwastall.client.hud.Vignette;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The pause menu: the blurred world stays, with a heavier vignette, the bloody PAUSED title on top, a
 * "Journal" button in the bottom-left corner and a line with the current stage and task at the bottom.
 */
@Mixin(GameMenuScreen.class)
public abstract class GameMenuScreenMixin extends Screen {
    @Unique
    private static final Identifier PAUSED = ItWasTall.id("textures/gui/paused.png");

    @Shadow
    @Final
    private boolean showMenu;

    protected GameMenuScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void itwastall$journalButton(CallbackInfo ci) {
        if (!showMenu || !MenuBackground.enabled()) return;
        addDrawableChild(ButtonWidget.builder(Text.literal("Journal"), b -> {
            if (this.client != null) this.client.setScreen(new JournalScreen());
        }).dimensions(6, this.height - 26, 74, 20).build());
        Screen self = (Screen) (Object) this;
        addDrawableChild(ButtonWidget.builder(Text.literal("Settings"), b -> {
            if (this.client != null) this.client.setScreen(new com.itwastall.client.config.ConfigScreen(self));
        }).dimensions(84, this.height - 26, 74, 20).build());
    }

    @Inject(method = "renderBackground", at = @At("TAIL"))
    private void itwastall$vignette(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (showMenu && MenuBackground.enabled()) Vignette.draw(ctx, this.width, this.height, 0.75f);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void itwastall$titleAndStatus(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!showMenu || !MenuBackground.enabled()) return;
        RenderSystem.enableBlend();
        // the visible letters are the middle 256x64 of the 512x64 texture -> drawn 128x32
        ctx.drawTexture(PAUSED, this.width / 2 - 64, 4, 128, 32, 128, 0, 256, 64, 512, 64);
        RenderSystem.disableBlend();
        String task = JournalData.task() == null || JournalData.task().isBlank() || JournalData.task().equals("no task")
                ? "It wants nothing. For now." : JournalData.task();
        String line = JournalData.phase() == 0 ? "Stage -  -  " + task : "Stage " + JournalData.stage() + "  -  " + task;
        ctx.drawCenteredTextWithShadow(textRenderer, textRenderer.trimToWidth(line, this.width - 180), this.width / 2,
                this.height - 18, 0xFF9C8E78);
    }
}
