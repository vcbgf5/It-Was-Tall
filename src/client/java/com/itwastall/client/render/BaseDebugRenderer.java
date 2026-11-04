package com.itwastall.client.render;

import com.itwastall.base.BaseMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Debug 3D (MODPLAN §8.5) — the server's saved {@link BaseMap} as coloured frames in the
 * world: window OUTSIDE dots red (where the knock figure stands), doors green, lights yellow, chests orange; the anchor (the bed)
 * gets a white gently pulsing double frame. Data arrives through
 * {@link com.itwastall.net.BaseDebugPayload} (the same encoded marker ints the state file
 * stores), stays up for the payload ttl counting down on the client, then clears quietly.
 *
 * <p>Drawn in the AFTER_TRANSLUCENT pass: depth-tested against the world, so walls occlude
 * properly (you see what It knows about the lodge, not an x-ray). The translucent interior
 * fill of §8.5 lands together with flood-fill rooms — the markers themselves are exact.
 */
public final class BaseDebugRenderer {
    /** One unfolded marker: type + world position. */
    private record Marker(int type, BlockPos pos) {
    }

    private static final float[] RED = {1.0f, 0.22f, 0.18f};
    private static final float[] GREEN = {0.25f, 1.0f, 0.35f};
    private static final float[] YELLOW = {1.0f, 0.85f, 0.15f};
    private static final float[] ORANGE = {1.0f, 0.45f, 0.10f};
    private static final float[] WHITE = {1.0f, 1.0f, 1.0f};
    private static final float[] PURPLE = {0.60f, 0.35f, 0.85f};
    private static final float[] INTERIOR = {0.15f, 0.10f, 0.30f, 0.0f}; // r,g,b only; alpha per face

    /** Interior cells drawn as FILLED boxes, hard-bounded so big bases stay smooth. */
    private static final int MAX_FILL_CELLS = 800;

    /** How far the frame floats outside the block shell (blocks of the box's own outline). */
    private static final float GROW = 0.05f;
    private static final float GROW_BIG = 0.11f;

    private static final List<Marker> MARKERS = new ArrayList<>();
    private static BlockPos anchor;
    private static int ticksLeft;

    private BaseDebugRenderer() {
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(BaseDebugRenderer::tick);
        WorldRenderEvents.AFTER_TRANSLUCENT.register(BaseDebugRenderer::render);
    }

    /** Entry point from the payload receiver: unfold the markers once, keep world positions. */
    public static void show(long anchorLong, int[] encodedMarkers, int ttlSeconds) {
        anchor = BlockPos.fromLong(anchorLong);
        MARKERS.clear();
        BaseMap map = new BaseMap(anchorLong);
        for (int v : encodedMarkers) {
            MARKERS.add(new Marker(map.type(v), map.pos(v)));
        }
        ticksLeft = Math.max(1, ttlSeconds * 20);
    }

    /** TTL countdown on the client; a dimension change or the timeout clears everything. */
    private static void tick(MinecraftClient client) {
        if (ticksLeft <= 0) return;
        if (client.world == null) {
            MARKERS.clear();
            ticksLeft = 0;
            return;
        }
        if (--ticksLeft == 0) MARKERS.clear();
    }

    private static void render(WorldRenderContext context) {
        if (MARKERS.isEmpty() || anchor == null || ticksLeft <= 0) return;
        VertexConsumerProvider consumers = context.consumers();
        MatrixStack matrices = context.matrixStack();
        if (consumers == null || matrices == null) return;

        matrices.push();
        // world space -> camera space: the world render passes' local origin is the camera
        var camera = context.camera();
        matrices.translate(-camera.getPos().x, -camera.getPos().y, -camera.getPos().z);
        VertexConsumer lines = consumers.getBuffer(RenderLayer.getLines());

        // the interior comes first (filled, translucent) so the wireframes draw above it
        VertexConsumer fill = consumers.getBuffer(RenderLayer.getDebugQuads());
        int filled = 0;
        for (Marker marker : MARKERS) {
            if (marker.type() != BaseMap.TYPE_ROOM) continue;
            if (filled++ >= MAX_FILL_CELLS) break;
            fillCell(matrices, fill, marker.pos());
        }

        for (Marker marker : MARKERS) {
            switch (marker.type()) {
                case BaseMap.TYPE_ROOM -> { /* drawn filled above */ }
                case BaseMap.TYPE_CORNER -> drawFrame(matrices, lines, marker.pos(), GROW, PURPLE, 0.95f);
                default -> drawFrame(matrices, lines, marker.pos(), GROW, colorFor(marker.type()), 0.85f);
            }
        }

        // the bed (anchor): white double frame, softly pulsing so it reads as the heart
        float pulse = 0.55f + 0.35f * MathHelper.sin((ticksLeft % 40) / 40f * 2f * MathHelper.PI);
        drawFrame(matrices, lines, anchor, GROW_BIG, WHITE, pulse);
        matrices.pop();
    }

    /**
     * One filled, translucent cube (the §8.5 "wnętrze — półprzezroczyste"): the DEBUG_QUADS
     * layer disables culling, so a single entry per face covers both windings for free.
     * Faces are drawn vertex-time against the LIVE matrix (camera-relative already), which
     * keeps 800+ cells well inside one frame's render budget.
     */
    private static void fillCell(MatrixStack matrices, VertexConsumer consumer, BlockPos pos) {
        var entry = matrices.peek();
        float x0 = pos.getX(), y0 = pos.getY(), z0 = pos.getZ();
        // slightly inset so neighbouring cells do not z-fight each other's edges
        float x1 = x0 + 0.999f, y1 = y0 + 0.999f, z1 = z0 + 0.999f;
        float r = 0.16f, g = 0.10f, b = 0.30f, a = 0.16f;

        for (float[][] face : FACES) {
            for (float[] v : face) {
                float px = v[0] == 0f ? x0 : x1;
                float py = v[1] == 0f ? y0 : y1;
                float pz = v[2] == 0f ? z0 : z1;
                consumer.vertex(entry.getPositionMatrix(), px, py, pz);
                consumer.color(r, g, b, a);
                consumer.texture(0f, 0f);
                consumer.light(net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE);
                consumer.normal(face[4][0], face[4][1], face[4][2]);
            }
        }
    }

    /** Quad winding is OUTSIDE-agnostic here (debug quads never cull): face = 4 corner triples
     *  in 0/1 space + the face normal as a 5th row, index 4. */
    private static final float[][][] FACES = {
            // DOWN (0,-1,0)
            {{0,0,0},{1,0,0},{1,0,1},{0,0,1},{0,-1,0}},
            // UP (0,1,0)
            {{0,1,0},{1,1,0},{1,1,1},{0,1,1},{0,1,0}},
            // NORTH (0,0,-1)
            {{0,0,0},{1,0,0},{1,1,0},{0,1,0},{0,0,-1}},
            // SOUTH (0,0,1)
            {{0,0,1},{1,0,1},{1,1,1},{0,1,1},{0,0,1}},
            // WEST (-1,0,0)
            {{0,0,0},{0,0,1},{0,1,1},{0,1,0},{-1,0,0}},
            // EAST (1,0,0)
            {{1,0,0},{1,0,1},{1,1,1},{1,1,0},{1,0,0}},
    };

    /** One axis-aligned wire cuboid around a block (world-render coordinates). */
    private static void drawFrame(MatrixStack matrices, VertexConsumer lines, BlockPos pos,
                                  float grow, float[] rgb, float alpha) {
        WorldRenderer.drawBox(matrices, lines,
                pos.getX() - grow, pos.getY() - grow, pos.getZ() - grow,
                pos.getX() + 1 + grow, pos.getY() + 1 + grow, pos.getZ() + 1 + grow,
                rgb[0], rgb[1], rgb[2], alpha);
    }

    private static float[] colorFor(int type) {
        return switch (type) {
            case BaseMap.TYPE_WINDOW -> RED;
            case BaseMap.TYPE_DOOR -> GREEN;
            case BaseMap.TYPE_LIGHT -> YELLOW;
            case BaseMap.TYPE_CHEST -> ORANGE;
            default -> WHITE;
        };
    }
}
