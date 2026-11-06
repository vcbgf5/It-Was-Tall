package com.itwastall.client.render;

import com.itwastall.entity.SleeperEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.util.Identifier;

/**
 * THE SLEEPER is drawn as a player wearing the skin of whoever is looking at it — everyone finds
 * themselves asleep in that bed. The vanilla living-entity renderer already lays it down in the bed
 * (sleeping pose + bed direction).
 */
public class SleeperRenderer extends LivingEntityRenderer<SleeperEntity, PlayerEntityModel<SleeperEntity>> {
    public SleeperRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new PlayerEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER), false), 0.5f);
    }

    @Override
    public Identifier getTexture(SleeperEntity entity) {
        var player = MinecraftClient.getInstance().player;
        return player != null ? player.getSkinTextures().texture() : DefaultSkinHelper.getTexture();
    }

    @Override
    protected boolean hasLabel(SleeperEntity entity) {
        return false;
    }
}
