package com.itwastall.client.render;

import com.itwastall.ItWasTall;
import com.itwastall.entity.OldOneEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * The Old One: the model {@code geo/entity/old_one.geo.json} drawn with the GAME'S OWN Steve skin
 * (referenced, never copied), then our overlay on top (hollow eyes, stretched mouth, blood, the flesh
 * inside the halves) and two emissive pinpoints in the eye sockets. No head tracking: the model has no
 * single "head" bone (each half carries half of the head, so the body can split down the middle).
 */
public class OldOneRenderer extends GeoEntityRenderer<OldOneEntity> {
    private static final Identifier STEVE = Identifier.ofVanilla("textures/entity/player/wide/steve.png");
    private static final Identifier OVERLAY = ItWasTall.id("textures/entity/old_one_overlay.png");
    private static final Identifier EYES = ItWasTall.id("textures/entity/old_one_eyes.png");

    public OldOneRenderer(EntityRendererFactory.Context context) {
        super(context, new DefaultedEntityGeoModel<OldOneEntity>(ItWasTall.id("old_one"), false) {
            @Override
            public Identifier getTextureResource(OldOneEntity animatable) {
                return STEVE;
            }
        });
        addRenderLayer(new TextureLayer(this, OVERLAY, false));
        addRenderLayer(new TextureLayer(this, EYES, true));
        this.shadowRadius = 0.5f;
    }

    /** Re-renders the same model with another texture: cutout (overlay) or emissive (eyes). */
    private static final class TextureLayer extends GeoRenderLayer<OldOneEntity> {
        private final Identifier texture;
        private final boolean glow;

        TextureLayer(GeoRenderer<OldOneEntity> renderer, Identifier texture, boolean glow) {
            super(renderer);
            this.texture = texture;
            this.glow = glow;
        }

        @Override
        public void render(MatrixStack poseStack, OldOneEntity animatable, BakedGeoModel bakedModel, RenderLayer renderType,
                           VertexConsumerProvider bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
            if (animatable.isInvisible()) return;
            RenderLayer layer = glow ? RenderLayer.getEyes(texture) : RenderLayer.getEntityCutoutNoCull(texture);
            getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, layer, bufferSource.getBuffer(layer),
                    partialTick, glow ? 15728640 : packedLight, packedOverlay, 0xFFFFFFFF);
        }
    }
}
