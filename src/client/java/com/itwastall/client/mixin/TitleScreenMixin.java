package com.itwastall.client.mixin;

import com.itwastall.ItWasTall;
import com.itwastall.client.hud.Vignette;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The title screen of It Was Tall: instead of the rotating panorama, the night clearing with the Tall One
 * standing at the edge of the forest (blurred, only the eyes sharp), breathing slowly in and out, and a
 * vignette. The logo and the buttons come from the built-in pack "It Was Tall UI".
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    private static final Identifier ITWASTALL_BACKGROUND = ItWasTall.id("textures/gui/title_background.png");

    @Shadow
    private float backgroundAlpha;

    protected TitleScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "renderPanoramaBackground", at = @At("HEAD"), cancellable = true)
    private void itwastall$nightBackground(DrawContext context, float delta, CallbackInfo ci) {
        if (!com.itwastall.config.ItWasTallConfig.INSTANCE.customTitleScreen) return;
        ci.cancel();
        // slow "breathing" zoom, very slight
        double t = net.minecraft.util.Util.getMeasuringTimeMs() / 1000.0;
        float zoom = 1.04f + 0.02f * MathHelper.sin((float) (t * 0.25));
        float s = Math.max(this.width / 1920f, this.height / 1080f) * zoom;
        int bw = Math.round(1920 * s), bh = Math.round(1080 * s);
        context.fill(0, 0, this.width, this.height, 0xFF000000);
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, MathHelper.clamp(this.backgroundAlpha, 0f, 1f));
        context.drawTexture(ITWASTALL_BACKGROUND, (this.width - bw) / 2, (this.height - bh) / 2, bw, bh, 0, 0, 1920, 1080, 1920, 1080);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
        Vignette.draw(context, this.width, this.height, 0.6f * this.backgroundAlpha);
    }
}
