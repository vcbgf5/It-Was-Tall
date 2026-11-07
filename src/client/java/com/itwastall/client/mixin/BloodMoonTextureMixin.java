package com.itwastall.client.mixin;

import com.itwastall.client.BloodMoonClient;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Blood moon: the moon itself is drawn blood red. */
@Mixin(WorldRenderer.class)
public abstract class BloodMoonTextureMixin {
    private static final Identifier ITWASTALL$MOON = Identifier.ofVanilla("textures/environment/moon_phases.png");

    @Redirect(method = "renderSky", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderTexture(ILnet/minecraft/util/Identifier;)V"), require = 0)
    private void itwastall$redMoon(int slot, Identifier texture) {
        RenderSystem.setShaderTexture(slot, texture);
        if (BloodMoonClient.active && ITWASTALL$MOON.equals(texture)) RenderSystem.setShaderColor(1.0f, 0.22f, 0.18f, 1.0f);
    }
}
