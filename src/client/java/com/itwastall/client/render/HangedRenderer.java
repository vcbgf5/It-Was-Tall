package com.itwastall.client.render;

import com.itwastall.ItWasTall;
import com.itwastall.entity.HangedEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

/**
 * THE HANGED is drawn as a player wearing the skin of whoever is looking at it, hanging limp on a rope from a
 * branch: arms and legs down, the head fallen to one side. The whole body swings slowly from the top of the
 * rope and the rope twists; the rope and the noose are drawn here too, swinging with it.
 */
public class HangedRenderer extends LivingEntityRenderer<HangedEntity, PlayerEntityModel<HangedEntity>> {
    private static final Identifier ROPE = ItWasTall.id("textures/entity/hanged_rope.png");
    private static final float SCALE = 0.9375f;

    public HangedRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new LimpModel(ctx.getPart(EntityModelLayers.PLAYER)), 0f);
    }

    @Override
    public Identifier getTexture(HangedEntity entity) {
        var player = MinecraftClient.getInstance().player;
        return player != null ? player.getSkinTextures().texture() : DefaultSkinHelper.getTexture();
    }

    @Override
    protected boolean hasLabel(HangedEntity entity) {
        return false;
    }

    @Override
    protected void scale(HangedEntity entity, MatrixStack matrices, float amount) {
        matrices.scale(SCALE, SCALE, SCALE);
    }

    @Override
    protected void setupTransforms(HangedEntity entity, MatrixStack matrices, float animationProgress, float bodyYaw, float tickDelta, float scale) {
        super.setupTransforms(entity, matrices, animationProgress, bodyYaw, tickDelta, scale);
        applySway(entity, matrices, entity.age + tickDelta);
    }

    /** The swing: around the top of the rope, a slow pendulum in two directions plus the twist of the rope. */
    private static void applySway(HangedEntity entity, MatrixStack matrices, float t) {
        float top = entity.ropeTop();
        matrices.translate(0, top, 0);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(MathHelper.sin(t * 0.02f) * 10f));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(MathHelper.sin(t * 0.05f) * 4f));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(MathHelper.sin(t * 0.037f) * 3f));
        matrices.translate(0, -top, 0);
    }

    @Override
    public void render(HangedEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        super.render(entity, yaw, tickDelta, matrices, consumers, light);
        // the rope and the noose, in the same swinging frame as the body
        matrices.push();
        float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, entity.prevBodyYaw, entity.bodyYaw);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f - bodyYaw));
        applySway(entity, matrices, entity.age + tickDelta);
        VertexConsumer vc = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(ROPE));
        float top = entity.ropeTop();
        float neck = HangedEntity.NECK;
        box(matrices, vc, light, -0.04f, neck, -0.04f, 0.04f, top, 0.04f);                      // rope (enters the head)
        box(matrices, vc, light, -0.255f, neck - 0.01f, -0.255f, 0.255f, neck + 0.06f, 0.255f); // noose: a collar under the head
        matrices.pop();
    }

    private static void box(MatrixStack matrices, VertexConsumer vc, int light,
                            float x0, float y0, float z0, float x1, float y1, float z1) {
        MatrixStack.Entry e = matrices.peek();
        Matrix4f m = e.getPositionMatrix();
        float len = y1 - y0;
        // four sides (texture repeats along the rope), top and bottom
        quad(vc, e, m, light, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0, 0, 1, len);
        quad(vc, e, m, light, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, 0, 0, -1, len);
        quad(vc, e, m, light, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, 1, 0, 0, len);
        quad(vc, e, m, light, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, -1, 0, 0, len);
        quad(vc, e, m, light, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0, 1, 0, 0.25f);
        quad(vc, e, m, light, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, -1, 0, 0.25f);
    }

    private static void quad(VertexConsumer vc, MatrixStack.Entry e, Matrix4f m, int light,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float nx, float ny, float nz, float vLen) {
        float v = vLen * 2f;   // two texture repeats per block
        vertex(vc, e, m, light, ax, ay, az, 0f, v, nx, ny, nz);
        vertex(vc, e, m, light, bx, by, bz, 0.25f, v, nx, ny, nz);
        vertex(vc, e, m, light, cx, cy, cz, 0.25f, 0f, nx, ny, nz);
        vertex(vc, e, m, light, dx, dy, dz, 0f, 0f, nx, ny, nz);
    }

    private static void vertex(VertexConsumer vc, MatrixStack.Entry e, Matrix4f m, int light,
                               float x, float y, float z, float u, float v, float nx, float ny, float nz) {
        vc.vertex(m, x, y, z).color(0xFFFFFFFF).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(e, nx, ny, nz);
    }

    /** The player model, limp: arms and legs straight down, the head fallen forward and to one side. */
    static final class LimpModel extends PlayerEntityModel<HangedEntity> {
        LimpModel(ModelPart root) {
            super(root, false);
        }

        @Override
        public void setAngles(HangedEntity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
            super.setAngles(entity, 0f, 0f, animationProgress, headYaw, 0f);
            float t = animationProgress;
            head.pitch = 0.35f;
            head.roll = 0.42f;
            head.yaw = headYaw * MathHelper.RADIANS_PER_DEGREE;
            rightArm.pitch = 0.04f + MathHelper.sin(t * 0.05f + 1f) * 0.03f;
            leftArm.pitch = 0.02f + MathHelper.sin(t * 0.05f + 2f) * 0.03f;
            rightArm.yaw = 0f;
            leftArm.yaw = 0f;
            rightArm.roll = 0.07f;
            leftArm.roll = -0.07f;
            rightLeg.pitch = 0.06f;
            leftLeg.pitch = -0.02f;
            rightLeg.yaw = 0.05f;
            leftLeg.yaw = -0.08f;
            rightLeg.roll = 0.02f;
            leftLeg.roll = -0.02f;
            body.pitch = 0f;
            hat.copyTransform(head);
            leftSleeve.copyTransform(leftArm);
            rightSleeve.copyTransform(rightArm);
            leftPants.copyTransform(leftLeg);
            rightPants.copyTransform(rightLeg);
            jacket.copyTransform(body);
        }
    }
}
