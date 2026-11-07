package com.itwastall.client.mixin;

import com.itwastall.client.JournalData;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * LAST WORDS: the death screen shows the last line of your journal — and if It was what killed you, a red
 * "IT WAS TALL." under the title.
 */
@Mixin(DeathScreen.class)
public abstract class DeathScreenMixin extends Screen {
    @Shadow
    @Final
    private Text message;

    protected DeathScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void itwastall$lastWords(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!com.itwastall.config.ItWasTallConfig.INSTANCE.hudExtras) return;
        String tallOne = Text.translatable("entity.itwastall.tall_one").getString();
        if (message != null && message.getString().contains(tallOne)) {
            ctx.getMatrices().push();
            ctx.getMatrices().translate(this.width / 2f, 54, 0);
            ctx.getMatrices().scale(1.5f, 1.5f, 1f);
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("IT WAS TALL.").formatted(Formatting.DARK_RED), 0, 0, 0xFFFFFFFF);
            ctx.getMatrices().pop();
        }
        var events = JournalData.events();
        if (events == null || events.isEmpty()) return;
        String last = events.get(events.size() - 1);
        String line = "\u201C" + textRenderer.trimToWidth(last, this.width - 80) + "\u201D";
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(line).formatted(Formatting.ITALIC), this.width / 2, 114, 0xFFB4A890);
    }
}
