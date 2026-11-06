package com.itwastall.client.render;

import com.itwastall.entity.CarcassEntity;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.QuadrupedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/** A dead cow / sheared sheep / pig lying on its side, drawn with the game's own animal models and textures. */
public class CarcassRenderer extends EntityRenderer<CarcassEntity> {
    private static final Identifier[] TEXTURES = {
            Identifier.ofVanilla("textures/entity/cow/cow.png"),
            Identifier.ofVanilla("textures/entity/sheep/sheep.png"),
            Identifier.ofVanilla("textures/entity/pig/pig.png")};
    private final QuadrupedEntityModel<CarcassEntity>[] models;

    @SuppressWarnings("unchecked")
    public CarcassRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.4f;
        models = new QuadrupedEntityModel[]{
                model(ctx.getPart(EntityModelLayers.COW)),
                model(ctx.getPart(EntityModelLayers.SHEEP)),
                model(ctx.getPart(EntityModelLayers.PIG))};
    }

    private static QuadrupedEntityModel<CarcassEntity> model(ModelPart root) {
        return new QuadrupedEntityModel<>(root, false, 8.0f, 4.0f, 2.0f, 2.0f, 24) {
        };
    }

    @Override
    public Identifier getTexture(CarcassEntity entity) {
        return TEXTURES[Math.floorMod(entity.variant(), 3)];
    }

    @Override
    public void render(CarcassEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        QuadrupedEntityModel<CarcassEntity> model = models[Math.floorMod(entity.variant(), 3)];
        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f - entity.getBodyYaw()));
        // fallen over onto its side, legs sticking out stiffly
        matrices.translate(0.4f, 0.33f, 0.0f);   // lift: on its side the body is as high as it was wide
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(90f));
        matrices.scale(-1f, -1f, 1f);
        matrices.translate(0f, -1.501f, 0f);
        model.child = false;
        model.setAngles(entity, 0f, 0f, 0f, 0f, 0f);
        model.render(matrices, consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(getTexture(entity))), light,
                OverlayTexture.DEFAULT_UV, 0xFFB8A8A0);
        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, consumers, light);
    }

    @Override
    protected boolean hasLabel(CarcassEntity entity) {
        return false;
    }
}
