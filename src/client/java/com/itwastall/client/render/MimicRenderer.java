package com.itwastall.client.render;

import com.itwastall.ItWasTall;
import com.itwastall.entity.MimicEntity;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.QuadrupedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/**
 * THE MIMIC: the game's own cow / sheep / pig model and texture, with a blood overlay drawn over it (and over the
 * sheep's wool).
 */
public class MimicRenderer extends MobEntityRenderer<MimicEntity, QuadrupedEntityModel<MimicEntity>> {
    private static final Identifier[] TEXTURES = {
            Identifier.ofVanilla("textures/entity/cow/cow.png"),
            Identifier.ofVanilla("textures/entity/sheep/sheep.png"),
            Identifier.ofVanilla("textures/entity/pig/pig.png")};
    private static final Identifier[] BLOOD = {
            ItWasTall.id("textures/entity/mimic/cow_blood.png"),
            ItWasTall.id("textures/entity/mimic/sheep_blood.png"),
            ItWasTall.id("textures/entity/mimic/pig_blood.png")};
    private static final Identifier FUR = Identifier.ofVanilla("textures/entity/sheep/sheep_fur.png");
    private static final Identifier FUR_BLOOD = ItWasTall.id("textures/entity/mimic/sheep_fur_blood.png");

    private final QuadrupedEntityModel<MimicEntity>[] models;
    private final QuadrupedEntityModel<MimicEntity> fur;

    @SuppressWarnings("unchecked")
    public MimicRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, model(ctx.getPart(EntityModelLayers.COW), 10f), 0.6f);
        models = new QuadrupedEntityModel[]{
                this.model,
                model(ctx.getPart(EntityModelLayers.SHEEP), 8f),
                model(ctx.getPart(EntityModelLayers.PIG), 4f)};
        fur = model(ctx.getPart(EntityModelLayers.SHEEP_FUR), 8f);
        addFeature(new Blood(this));
    }

    private static QuadrupedEntityModel<MimicEntity> model(ModelPart root, float childHeadY) {
        return new QuadrupedEntityModel<>(root, false, childHeadY, 4.0f, 2.0f, 2.0f, 24) {
        };
    }

    @Override
    public void render(MimicEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        this.model = models[Math.floorMod(entity.variant(), 3)];
        super.render(entity, yaw, tickDelta, matrices, consumers, light);
    }

    @Override
    public Identifier getTexture(MimicEntity entity) {
        return TEXTURES[Math.floorMod(entity.variant(), 3)];
    }

    private final class Blood extends FeatureRenderer<MimicEntity, QuadrupedEntityModel<MimicEntity>> {
        Blood(FeatureRendererContext<MimicEntity, QuadrupedEntityModel<MimicEntity>> context) {
            super(context);
        }

        @Override
        public void render(MatrixStack matrices, VertexConsumerProvider consumers, int light, MimicEntity entity,
                           float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
            int v = Math.floorMod(entity.variant(), 3);
            getContextModel().render(matrices, consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(BLOOD[v])), light, OverlayTexture.DEFAULT_UV, -1);
            if (v == MimicEntity.SHEEP) {
                getContextModel().copyStateTo(fur);
                fur.animateModel(entity, limbAngle, limbDistance, tickDelta);
                fur.setAngles(entity, limbAngle, limbDistance, animationProgress, headYaw, headPitch);
                fur.render(matrices, consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(FUR)), light, OverlayTexture.DEFAULT_UV, 0xFFD8D0C8);
                fur.render(matrices, consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(FUR_BLOOD)), light, OverlayTexture.DEFAULT_UV, -1);
            }
        }
    }
}
