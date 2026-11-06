package com.itwastall.client.mixin;

import com.itwastall.client.hud.AchievementsLink;
import com.itwastall.client.hud.JournalScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.advancement.AdvancementsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The advancements screen (L) gets a "Journal" button in the top-left corner: the It Was Tall tab sits among the
 * normal Minecraft tabs, and from here you go straight to the journal (and the journal's AWARDS ribbon comes back).
 */
@Mixin(AdvancementsScreen.class)
public abstract class AdvancementsScreenMixin extends Screen {
    protected AdvancementsScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void itwastall$journalButton(CallbackInfo ci) {
        if (!AchievementsLink.available()) return;
        addDrawableChild(ButtonWidget.builder(Text.literal("< Journal"), b -> {
            if (this.client != null) this.client.setScreen(new JournalScreen());
        }).dimensions(6, 6, 74, 20).tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("Open the It Was Tall journal (J)"))).build());
    }
}
