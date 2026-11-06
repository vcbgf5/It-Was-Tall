package com.itwastall.client.mixin;

import com.itwastall.client.hud.MenuBackground;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The title screen of It Was Tall: instead of the rotating panorama, {@link MenuBackground} (fading in with the
 * title screen's own fade). The logo and the buttons come from the built-in pack "It Was Tall UI".
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    @Shadow
    private float backgroundAlpha;

    protected TitleScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "renderPanoramaBackground", at = @At("HEAD"), cancellable = true)
    private void itwastall$nightBackground(DrawContext context, float delta, CallbackInfo ci) {
        if (!MenuBackground.enabled()) return;
        ci.cancel();
        MenuBackground.draw(context, this.width, this.height, this.backgroundAlpha);
    }
}
