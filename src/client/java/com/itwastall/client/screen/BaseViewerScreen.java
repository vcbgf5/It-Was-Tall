package com.itwastall.client.screen;

import com.itwastall.base.BaseMap;
import com.itwastall.client.JournalData;
import com.itwastall.client.hud.Vignette;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Debug 3D base viewer (MODPLAN §8.5, "/itwastall debug view"): a rotatable miniature of the
 * player's base — the REAL blocks from the client world inside the mapped bounds — with coloured
 * marker cubes for what the server has mapped (windows, doors, lights, chests, dark corners, bed).
 *
 * <p>Mouse drag rotates, scroll zooms, 1-5 toggle marker layers, [ and ] lower / raise the cut
 * (hide everything above a height to look inside), R resets. Left: legend with counts; right: the
 * player's latest events. No background blur.
 */
public final class BaseViewerScreen extends Screen {
    private record Cube(int x, int y, int z, BlockState state) {
    }

    private static final int MAX_W = 32, MAX_H = 20, MAX_BLOCKS = 6000;
    private static final String[] LAYER_NAMES = {"Windows", "Doors", "Lights", "Chests", "Dark corners"};
    private static final int[] LAYER_TYPES = {BaseMap.TYPE_WINDOW, BaseMap.TYPE_DOOR, BaseMap.TYPE_LIGHT, BaseMap.TYPE_CHEST, BaseMap.TYPE_CORNER};
    private static final BlockState[] LAYER_CUBES = {
            Blocks.LIGHT_BLUE_CONCRETE.getDefaultState(), Blocks.LIME_CONCRETE.getDefaultState(),
            Blocks.YELLOW_CONCRETE.getDefaultState(), Blocks.ORANGE_CONCRETE.getDefaultState(),
            Blocks.PURPLE_CONCRETE.getDefaultState()};
    private static final int[] LAYER_COLORS = {0xFF6CC3EA, 0xFF7FD34A, 0xFFF2D23C, 0xFFF08A24, 0xFFA45ED8};

    private final BlockPos anchor;
    private final int[] markers;
    private final List<Cube> blocks = new ArrayList<>();
    private final List<int[]> markerCubes = new ArrayList<>(); // {layer, x, y, z} relative to minimum
    private final boolean[] layerOn = {true, true, true, true, true};
    private final int[] layerCount = new int[5];
    private int minX, minY, minZ, sizeX, sizeY, sizeZ;
    private int cutY;
    private float yaw = 35f, pitch = 28f, zoom = 1f;
    private boolean far;

    public BaseViewerScreen(long anchor, int[] markers) {
        super(Text.literal("It knows your home"));
        this.anchor = BlockPos.fromLong(anchor);
        this.markers = markers == null ? new int[0] : markers;
    }

    @Override
    protected void init() {
        if (blocks.isEmpty() && markerCubes.isEmpty()) collect();
    }

    private void collect() {
        // bounds of the mapped markers, clamped around the anchor
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE;
        int x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
        List<int[]> decoded = new ArrayList<>();
        for (int m : markers) {
            int type = m & 0xF;
            BlockPos p = anchor.add(signed9(m >> 4), signed9(m >> 13), signed9(m >> 22));
            decoded.add(new int[]{type, p.getX(), p.getY(), p.getZ()});
            x0 = Math.min(x0, p.getX()); y0 = Math.min(y0, p.getY()); z0 = Math.min(z0, p.getZ());
            x1 = Math.max(x1, p.getX()); y1 = Math.max(y1, p.getY()); z1 = Math.max(z1, p.getZ());
        }
        if (decoded.isEmpty()) {
            x0 = x1 = anchor.getX(); y0 = y1 = anchor.getY(); z0 = z1 = anchor.getZ();
        }
        x0 = Math.max(x0 - 1, anchor.getX() - MAX_W / 2); x1 = Math.min(x1 + 1, anchor.getX() + MAX_W / 2);
        z0 = Math.max(z0 - 1, anchor.getZ() - MAX_W / 2); z1 = Math.min(z1 + 1, anchor.getZ() + MAX_W / 2);
        y0 = Math.max(y0 - 1, anchor.getY() - 6); y1 = Math.min(y1 + 1, y0 + MAX_H);
        minX = x0; minY = y0; minZ = z0;
        sizeX = x1 - x0 + 1; sizeY = y1 - y0 + 1; sizeZ = z1 - z0 + 1;
        cutY = sizeY;

        ClientWorld world = MinecraftClient.getInstance().world;
        if (world != null && world.isChunkLoaded(anchor)) {
            BlockPos.Mutable p = new BlockPos.Mutable();
            for (int x = x0; x <= x1 && blocks.size() < MAX_BLOCKS; x++) {
                for (int y = y0; y <= y1; y++) {
                    for (int z = z0; z <= z1; z++) {
                        p.set(x, y, z);
                        BlockState st = world.getBlockState(p);
                        if (st.isAir() || !st.getFluidState().isEmpty() && st.isOf(Blocks.WATER)) continue;
                        if (st.isIn(BlockTags.LEAVES) || (st.isReplaceable() && !st.isOf(Blocks.SNOW))) continue;
                        if (hidden(world, p)) continue;
                        blocks.add(new Cube(x - x0, y - y0, z - z0, st));
                    }
                }
            }
        } else {
            far = true;
        }
        for (int[] d : decoded) {
            for (int l = 0; l < LAYER_TYPES.length; l++) {
                if (d[0] == LAYER_TYPES[l]) {
                    layerCount[l]++;
                    if (d[1] >= x0 && d[1] <= x1 && d[2] >= y0 && d[2] <= y1 && d[3] >= z0 && d[3] <= z1) {
                        markerCubes.add(new int[]{l, d[1] - x0, d[2] - y0, d[3] - z0});
                    }
                }
            }
        }
    }

    /** A block fully enclosed by opaque cubes can never be seen — skip it. */
    private static boolean hidden(ClientWorld world, BlockPos p) {
        for (Direction d : Direction.values()) {
            BlockPos q = p.offset(d);
            if (!world.getBlockState(q).isOpaqueFullCube(world, q)) return false;
        }
        return true;
    }

    private static int signed9(int v) {
        v &= 0x1FF;
        return v >= 256 ? v - 512 : v;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xE0080809);
        Vignette.draw(ctx, width, height, 0.9f);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx, mouseX, mouseY, delta);
        renderModel(ctx);

        // title + help
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("IT KNOWS YOUR HOME"), width / 2, 10, 0xFFC0140F);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("drag: rotate · scroll: zoom · 1-5: layers · [ ]: cut · R: reset · ESC"),
                width / 2, height - 14, 0xFF7A7068);
        if (far) {
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Your base is too far away to see its blocks — only the markers are shown."),
                    width / 2, 24, 0xFFB0A898);
        }

        // legend (left)
        int y = 40;
        ctx.drawTextWithShadow(textRenderer, "Anchor " + anchor.toShortString(), 10, y, 0xFFE8D9B8);
        y += 14;
        for (int l = 0; l < LAYER_NAMES.length; l++) {
            int color = layerOn[l] ? LAYER_COLORS[l] : 0xFF4A4642;
            ctx.fill(10, y + 1, 17, y + 8, color);
            ctx.drawTextWithShadow(textRenderer, (l + 1) + "  " + LAYER_NAMES[l] + ": " + layerCount[l], 22, y, layerOn[l] ? 0xFFE0D8C8 : 0xFF6A645C);
            y += 12;
        }
        y += 6;
        ctx.drawTextWithShadow(textRenderer, "Blocks shown: " + blocks.size(), 10, y, 0xFF8F857A);
        y += 12;
        ctx.drawTextWithShadow(textRenderer, "Cut height: " + cutY + " / " + sizeY, 10, y, 0xFF8F857A);

        // latest events (right)
        int rx = width - 170, ry = 40;
        ctx.drawTextWithShadow(textRenderer, "Latest events", rx, ry, 0xFFC0140F);
        ry += 14;
        List<String> ev = JournalData.events();
        if (ev.isEmpty()) {
            ctx.drawTextWithShadow(textRenderer, "Nothing yet.", rx, ry, 0xFF6A645C);
        }
        for (int i = 0; i < Math.min(6, ev.size()); i++) {
            for (OrderedText line : textRenderer.wrapLines(Text.literal(ev.get(i)), 160)) {
                if (ry > height - 30) break;
                ctx.drawTextWithShadow(textRenderer, line, rx, ry, 0xFFB8AE9E);
                ry += 10;
            }
            ry += 4;
        }
    }

    private void renderModel(DrawContext ctx) {
        MinecraftClient client = MinecraftClient.getInstance();
        BlockRenderManager brm = client.getBlockRenderManager();
        VertexConsumerProvider.Immediate vcp = ctx.getVertexConsumers();
        MatrixStack m = ctx.getMatrices();

        float fit = Math.min(width * 0.55f, height * 0.7f) / Math.max(8f, (float) Math.sqrt(sizeX * sizeX + sizeY * sizeY + sizeZ * sizeZ));
        float s = fit * zoom;
        m.push();
        m.translate(width / 2f, height / 2f + 8, 300);
        m.scale(s, -s, s);                                    // GUI y points down
        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
        m.translate(-sizeX / 2f, -sizeY / 2f, -sizeZ / 2f);
        int light = LightmapTextureManager.MAX_LIGHT_COORDINATE;
        for (Cube c : blocks) {
            if (c.y >= cutY) continue;
            m.push();
            m.translate(c.x, c.y, c.z);
            brm.renderBlockAsEntity(c.state, m, vcp, light, OverlayTexture.DEFAULT_UV);
            m.pop();
        }
        for (int[] mc : markerCubes) {
            if (!layerOn[mc[0]] || mc[2] >= cutY) continue;
            m.push();
            m.translate(mc[1] + 0.3f, mc[2] + 0.3f, mc[3] + 0.3f);
            m.scale(0.4f, 0.4f, 0.4f);
            brm.renderBlockAsEntity(LAYER_CUBES[mc[0]], m, vcp, light, OverlayTexture.DEFAULT_UV);
            m.pop();
        }
        // the anchor (bed): a white cube
        m.push();
        m.translate(anchor.getX() - minX + 0.25f, anchor.getY() - minY + 0.25f, anchor.getZ() - minZ + 0.25f);
        m.scale(0.5f, 0.5f, 0.5f);
        brm.renderBlockAsEntity(Blocks.WHITE_CONCRETE.getDefaultState(), m, vcp, light, OverlayTexture.DEFAULT_UV);
        m.pop();
        ctx.draw();
        m.pop();
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        yaw += (float) deltaX * 0.8f;
        pitch = MathHelper.clamp(pitch + (float) deltaY * 0.6f, -10f, 89f);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        zoom = MathHelper.clamp(zoom * (verticalAmount > 0 ? 1.12f : 1f / 1.12f), 0.3f, 6f);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_5) {
            int l = keyCode - GLFW.GLFW_KEY_1;
            layerOn[l] = !layerOn[l];
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT_BRACKET) {
            cutY = Math.max(1, cutY - 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT_BRACKET) {
            cutY = Math.min(sizeY, cutY + 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_R) {
            yaw = 35f;
            pitch = 28f;
            zoom = 1f;
            cutY = sizeY;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
