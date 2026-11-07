package com.itwastall.client.render;

import com.itwastall.ItWasTall;
import com.itwastall.registry.ModSounds;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * THE EYES, client side: pairs of glowing eyes open one after another between the trees (slowly at first, then
 * faster), blink now and then and follow you with their gaze. Bring a light (a torch or any glowing block in
 * your hand) or walk up to them and they close one after another, the farthest first — and then the last pair
 * opens right in front of your face, and closes.
 */
public final class EyeSwarmRenderer {
    private static final Identifier TEX = ItWasTall.id("textures/entity/eye_pair.png");
    private static final int OPENING = 0, CLOSING = 1, FINAL = 2;

    private static final class Eye {
        final Vec3d pos;
        final int openAt;
        int closeAt = Integer.MAX_VALUE;
        int blinkAt;
        Eye(Vec3d pos, int openAt, int blinkAt) {
            this.pos = pos;
            this.openAt = openAt;
            this.blinkAt = blinkAt;
        }
    }

    private static final List<Eye> EYES = new ArrayList<>();
    private static int ticks, state = -1, finalAt;
    private static Vec3d finalPos = Vec3d.ZERO;
    private static final Random RANDOM = Random.create();

    private EyeSwarmRenderer() {}

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(c -> tick());
        WorldRenderEvents.AFTER_TRANSLUCENT.register(EyeSwarmRenderer::render);
    }

    /** The server picked the spots: the first pair opens now, the rest follow, faster and faster. */
    public static void start(List<BlockPos> spots) {
        EYES.clear();
        ticks = 0;
        state = OPENING;
        int at = 10;
        int gap = 50;
        for (BlockPos s : spots) {
            Vec3d p = new Vec3d(s.getX() + 0.2 + RANDOM.nextDouble() * 0.6, s.getY() + 0.3 + RANDOM.nextDouble() * 0.5, s.getZ() + 0.2 + RANDOM.nextDouble() * 0.6);
            EYES.add(new Eye(p, at, at + 40 + RANDOM.nextInt(120)));
            at += gap;
            gap = Math.max(6, (int) (gap * 0.7));
        }
    }

    public static void clear() {
        EYES.clear();
        state = -1;
    }

    private static void tick() {
        if (state < 0) return;
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null || client.isPaused()) {
            if (player == null) clear();
            return;
        }
        ticks++;
        for (Eye e : EYES) {
            if (ticks == e.openAt && state == OPENING) {
                client.world.playSound(e.pos.x, e.pos.y, e.pos.z, ModSounds.AMBIENT_WHISPER, SoundCategory.HOSTILE, 0.25f, 0.7f + RANDOM.nextFloat() * 0.2f, false);
            }
            if (ticks > e.blinkAt + 4) e.blinkAt = ticks + 60 + RANDOM.nextInt(160);
        }
        if (state == OPENING) {
            boolean light = glows(player.getMainHandStack()) || glows(player.getOffHandStack());
            boolean close = false;
            for (Eye e : EYES) if (ticks >= e.openAt && e.pos.squaredDistanceTo(player.getEyePos()) < 5.5 * 5.5) close = true;
            if (light || close || ticks > 20 * 75) {
                state = CLOSING;
                List<Eye> order = new ArrayList<>(EYES);
                Vec3d me = player.getEyePos();
                order.sort(Comparator.comparingDouble((Eye e) -> e.pos.squaredDistanceTo(me)).reversed());
                int at = ticks + 4;
                for (Eye e : order) {
                    e.closeAt = at;
                    at += 4;
                }
                finalAt = at + 16;
            }
        } else if (state == CLOSING && ticks >= finalAt) {
            state = FINAL;
            Vec3d look = player.getRotationVec(1f);
            finalPos = player.getEyePos().add(look.x * 1.3, look.y * 1.3, look.z * 1.3);
            client.world.playSound(player.getX(), player.getY(), player.getZ(), ModSounds.AMBIENT_STING, SoundCategory.HOSTILE, 0.9f, 0.8f, false);
            finalAt = ticks + 26;
        } else if (state == FINAL && ticks >= finalAt) {
            clear();
        }
    }

    private static boolean glows(ItemStack stack) {
        if (stack.isOf(Items.TORCH) || stack.isOf(Items.SOUL_TORCH) || stack.isOf(Items.LANTERN) || stack.isOf(Items.SOUL_LANTERN)) return true;
        if (stack.getItem() instanceof BlockItem bi) {
            BlockState st = bi.getBlock().getDefaultState();
            return st.getLuminance() >= 10;
        }
        return false;
    }

    private static void render(WorldRenderContext context) {
        if (state < 0 || EYES.isEmpty() && state != FINAL) return;
        VertexConsumerProvider consumers = context.consumers();
        MatrixStack matrices = context.matrixStack();
        if (consumers == null || matrices == null) return;
        Vec3d cam = context.camera().getPos();
        float t = ticks + context.tickCounter().getTickDelta(true);
        VertexConsumer vc = consumers.getBuffer(RenderLayer.getEyes(TEX));
        matrices.push();
        matrices.translate(-cam.x, -cam.y, -cam.z);
        if (state == FINAL) {
            float open = MathHelper.clamp(Math.min((t - (finalAt - 26)) / 4f, (finalAt - t) / 4f), 0f, 1f);
            pair(matrices, vc, context, finalPos, 0.42f, open);
        } else {
            for (Eye e : EYES) {
                float open;
                if (t < e.openAt) continue;
                open = Math.min(1f, (t - e.openAt) / 8f);
                if (t >= e.closeAt) open = Math.max(0f, 1f - (t - e.closeAt) / 3f);
                if (t >= e.blinkAt && t < e.blinkAt + 4) open *= 0.1f;
                if (open <= 0.01f) continue;
                pair(matrices, vc, context, e.pos, 0.32f, open);
            }
        }
        matrices.pop();
        if (consumers instanceof VertexConsumerProvider.Immediate immediate) immediate.draw(RenderLayer.getEyes(TEX));
    }

    /** One pair of eyes facing the camera; {@code open} squeezes it shut vertically. */
    private static void pair(MatrixStack matrices, VertexConsumer vc, WorldRenderContext context, Vec3d at, float halfW, float open) {
        matrices.push();
        matrices.translate(at.x, at.y, at.z);
        matrices.multiply(context.camera().getRotation());
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f m = entry.getPositionMatrix();
        float hw = halfW, hh = halfW * 0.5f * open;
        float v0 = 0.5f - 0.5f * open, v1 = 0.5f + 0.5f * open;
        int light = LightmapTextureManager.MAX_LIGHT_COORDINATE;
        vertex(vc, entry, m, -hw, -hh, 1f, v1, light);
        vertex(vc, entry, m, hw, -hh, 0f, v1, light);
        vertex(vc, entry, m, hw, hh, 0f, v0, light);
        vertex(vc, entry, m, -hw, hh, 1f, v0, light);
        matrices.pop();
    }

    private static void vertex(VertexConsumer vc, MatrixStack.Entry e, Matrix4f m, float x, float y, float u, float v, int light) {
        vc.vertex(m, x, y, 0f).color(0xFFFFFFFF).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(e, 0f, 0f, 1f);
    }
}
