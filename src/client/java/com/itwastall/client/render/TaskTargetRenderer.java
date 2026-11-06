package com.itwastall.client.render;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.OptionalDouble;

/**
 * THE OFFERING: the chest It wants the items in glows through walls (a dark-red translucent block with a
 * pulsing outline, drawn with the depth test off) for as long as the task runs, so it can be found from
 * anywhere in the house. Same render path as the base viewer (vertex consumers after the translucent pass).
 */
public final class TaskTargetRenderer {
    /** Lines that ignore the depth buffer (seen through blocks). */
    private static final RenderLayer XRAY_LINES = RenderLayer.of("itwastall_xray_lines",
            VertexFormats.LINES, VertexFormat.DrawMode.LINES, 256, false, true,
            RenderLayer.MultiPhaseParameters.builder()
                    .program(RenderPhase.LINES_PROGRAM)
                    .lineWidth(new RenderPhase.LineWidth(OptionalDouble.of(3.0)))
                    .layering(RenderPhase.VIEW_OFFSET_Z_LAYERING)
                    .transparency(RenderPhase.TRANSLUCENT_TRANSPARENCY)
                    .depthTest(RenderPhase.ALWAYS_DEPTH_TEST)
                    .cull(RenderPhase.DISABLE_CULLING)
                    .writeMaskState(RenderPhase.COLOR_MASK)
                    .build(false));
    /** A translucent fill that ignores the depth buffer. */
    private static final RenderLayer XRAY_FILL = RenderLayer.of("itwastall_xray_fill",
            VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS, 256, false, true,
            RenderLayer.MultiPhaseParameters.builder()
                    .program(RenderPhase.COLOR_PROGRAM)
                    .transparency(RenderPhase.TRANSLUCENT_TRANSPARENCY)
                    .depthTest(RenderPhase.ALWAYS_DEPTH_TEST)
                    .cull(RenderPhase.DISABLE_CULLING)
                    .writeMaskState(RenderPhase.COLOR_MASK)
                    .build(false));

    @Nullable
    private static BlockPos target;

    private TaskTargetRenderer() {}

    public static void init() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(TaskTargetRenderer::render);
    }

    public static void set(@Nullable BlockPos pos) {
        target = pos;
    }

    private static void render(WorldRenderContext context) {
        BlockPos pos = target;
        VertexConsumerProvider consumers = context.consumers();
        MatrixStack matrices = context.matrixStack();
        if (pos == null || consumers == null || matrices == null || MinecraftClient.getInstance().player == null) return;
        var cam = context.camera().getPos();
        if (cam.squaredDistanceTo(pos.toCenterPos()) > 96 * 96) return;

        float pulse = com.itwastall.config.ItWasTallConfig.INSTANCE.photosensitiveMode ? 0.85f
                : 0.6f + 0.4f * MathHelper.sin(Util.getMeasuringTimeMs() / 1000f * MathHelper.PI * 1.4f);
        float g = 0.02f;
        float x0 = pos.getX() - g, y0 = pos.getY() - g, z0 = pos.getZ() - g;
        float x1 = pos.getX() + 1 + g, y1 = pos.getY() + 1 + g, z1 = pos.getZ() + 1 + g;

        matrices.push();
        // world space -> camera space (the world render passes' local origin is the camera)
        matrices.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = matrices.peek().getPositionMatrix();

        VertexConsumer fill = consumers.getBuffer(XRAY_FILL);
        float r = 0.75f, gr = 0.10f, b = 0.07f, a = 0.22f * pulse;
        quad(fill, m, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, r, gr, b, a);
        quad(fill, m, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, r, gr, b, a);
        quad(fill, m, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, r, gr, b, a);
        quad(fill, m, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, r, gr, b, a);
        quad(fill, m, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, r, gr, b, a);
        quad(fill, m, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, r, gr, b, a);

        VertexConsumer lines = consumers.getBuffer(XRAY_LINES);
        WorldRenderer.drawBox(matrices, lines, x0, y0, z0, x1, y1, z1, 0.95f, 0.22f, 0.16f, 0.55f + 0.45f * pulse);
        matrices.pop();

        if (consumers instanceof VertexConsumerProvider.Immediate immediate) {
            immediate.draw(XRAY_FILL);
            immediate.draw(XRAY_LINES);
        }
    }

    private static void quad(VertexConsumer buf, Matrix4f m,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float r, float g, float b, float a) {
        buf.vertex(m, ax, ay, az).color(r, g, b, a);
        buf.vertex(m, bx, by, bz).color(r, g, b, a);
        buf.vertex(m, cx, cy, cz).color(r, g, b, a);
        buf.vertex(m, dx, dy, dz).color(r, g, b, a);
    }
}
