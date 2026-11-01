package com.itwastall.client.render;

import com.itwastall.ItWasTall;
import com.itwastall.entity.TallOneEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;

/**
 * Uses assets/itwastall/geo/entity/tall_one.geo.json, animations/entity/tall_one.animation.json,
 * textures/entity/tall_one.png and the emissive textures/entity/tall_one_glowmask.png.
 * The main-hand item (iron pickaxe while digging) is drawn in the right hand bone.
 */
public class TallOneRenderer extends GeoEntityRenderer<TallOneEntity> {
    public TallOneRenderer(EntityRendererFactory.Context context) {
        super(context, new DefaultedEntityGeoModel<>(ItWasTall.id("tall_one"), true));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        addRenderLayer(new BlockAndItemGeoLayer<>(this) {
            @Nullable
            @Override
            protected ItemStack getStackForBone(GeoBone bone, TallOneEntity entity) {
                return "right_hand".equals(bone.getName()) && !entity.getMainHandStack().isEmpty() ? entity.getMainHandStack() : null;
            }

            @Override
            protected ModelTransformationMode getTransformTypeForStack(GeoBone bone, ItemStack stack, TallOneEntity entity) {
                return ModelTransformationMode.THIRD_PERSON_RIGHT_HAND;
            }

            @Override
            protected void renderStackForBone(MatrixStack poseStack, GeoBone bone, ItemStack stack, TallOneEntity entity,
                                              VertexConsumerProvider bufferSource, float partialTick, int packedLight, int packedOverlay) {
                poseStack.translate(0, -0.15, 0);
                poseStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90f));
                super.renderStackForBone(poseStack, bone, stack, entity, bufferSource, partialTick, packedLight, packedOverlay);
            }
        });
        this.shadowRadius = 0.45f;
    }
}
