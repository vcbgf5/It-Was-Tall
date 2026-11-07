package com.itwastall.entity;

import com.itwastall.net.FlashlightPayload;
import com.itwastall.registry.ModEntities;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LightBlock;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * THE FLASHLIGHT: everyone carries one (no item), the middle mouse button switches it on. Where you look, the world
 * lights up — an invisible light block sits on the spot the beam hits (brighter the closer it is), and a weaker one
 * halfway along a long beam. They are real light, so the others see your beam too. They move with your gaze and
 * vanish when you switch off, leave, or change worlds. When It is near, the light flickers.
 */
public final class Flashlight {
    private static final double RANGE = 28;
    private static final Set<UUID> ON = new HashSet<>();
    private static final Map<UUID, List<BlockPos>> PLACED = new HashMap<>();
    private static final Map<UUID, RegistryKey<World>> PLACED_IN = new HashMap<>();

    private Flashlight() {}

    public static void init() {
        PayloadTypeRegistry.playC2S().register(FlashlightPayload.ID, FlashlightPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(FlashlightPayload.ID, (payload, context) -> {
            ServerPlayerEntity p = context.player();
            if (payload.on()) ON.add(p.getUuid());
            else {
                ON.remove(p.getUuid());
                clear(p.getServer(), p.getUuid());
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> server.execute(() -> {
            ON.remove(handler.getPlayer().getUuid());
            clear(server, handler.getPlayer().getUuid());
        }));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (UUID u : new ArrayList<>(PLACED.keySet())) clear(server, u);
            ON.clear();
        });
        ServerTickEvents.END_SERVER_TICK.register(Flashlight::tick);
    }

    private static void tick(MinecraftServer server) {
        if (server.getTicks() % 2 != 0) return;
        for (UUID u : new ArrayList<>(PLACED.keySet())) {
            if (!ON.contains(u)) clear(server, u);
        }
        for (UUID u : new ArrayList<>(ON)) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(u);
            if (p == null) {
                ON.remove(u);
                clear(server, u);
                continue;
            }
            ServerWorld world = p.getServerWorld();
            RegistryKey<World> was = PLACED_IN.get(u);
            if (was != null && was != world.getRegistryKey()) clear(server, u);
            List<BlockPos> want = new ArrayList<>(2);
            List<Integer> levels = new ArrayList<>(2);
            boolean flicker = itNear(p) && p.getRandom().nextFloat() < 0.35f;
            if (!p.isSpectator() && !flicker) beam(p, world, want, levels);
            apply(world, u, want, levels);
        }
    }

    /** Where the light goes: the spot the beam hits (or the end of the beam), and halfway along a long one. */
    private static void beam(ServerPlayerEntity p, ServerWorld world, List<BlockPos> want, List<Integer> levels) {
        Vec3d eye = p.getEyePos(), dir = p.getRotationVec(1f);
        BlockHitResult hit = world.raycast(new RaycastContext(eye, eye.add(dir.multiply(RANGE)),
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
        double dist;
        BlockPos spot;
        if (hit.getType() == HitResult.Type.BLOCK) {
            spot = hit.getBlockPos().offset(hit.getSide());
            dist = hit.getPos().distanceTo(eye);
        } else {
            spot = BlockPos.ofFloored(eye.add(dir.multiply(RANGE)));
            dist = RANGE;
        }
        int level = MathHelper.clamp(15 - (int) (dist / 2.5), 5, 15);
        if (hit.getType() != HitResult.Type.BLOCK) level = 5;
        want.add(spot);
        levels.add(level);
        if (dist > 8) {
            BlockPos mid = BlockPos.ofFloored(eye.add(dir.multiply(dist * 0.5)));
            if (!mid.equals(spot)) {
                want.add(mid);
                levels.add(Math.max(5, level - 4));
            }
        }
    }

    private static void apply(ServerWorld world, UUID u, List<BlockPos> want, List<Integer> levels) {
        List<BlockPos> had = PLACED.getOrDefault(u, List.of());
        List<BlockPos> now = new ArrayList<>(2);
        for (BlockPos old : had) {
            if (!want.contains(old)) restore(world, old);
        }
        for (int i = 0; i < want.size(); i++) {
            BlockPos pos = want.get(i);
            if (!world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) continue;
            BlockState st = world.getBlockState(pos);
            BlockState light = Blocks.LIGHT.getDefaultState().with(LightBlock.LEVEL_15, levels.get(i));
            if (st.isOf(Blocks.LIGHT)) {
                if (!had.contains(pos)) continue;                  // someone's own light block: not ours to touch
                if (st.get(LightBlock.LEVEL_15) != levels.get(i)) world.setBlockState(pos, light, Block.NOTIFY_LISTENERS);
                now.add(pos);
            } else if (st.isAir()) {
                world.setBlockState(pos, light, Block.NOTIFY_LISTENERS);
                now.add(pos);
            }
        }
        if (now.isEmpty()) {
            PLACED.remove(u);
            PLACED_IN.remove(u);
        } else {
            PLACED.put(u, now);
            PLACED_IN.put(u, world.getRegistryKey());
        }
    }

    private static void restore(ServerWorld world, BlockPos pos) {
        if (world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4) && world.getBlockState(pos).isOf(Blocks.LIGHT)) {
            world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        }
    }

    private static void clear(MinecraftServer server, UUID u) {
        List<BlockPos> had = PLACED.remove(u);
        RegistryKey<World> key = PLACED_IN.remove(u);
        if (had == null || server == null || key == null) return;
        ServerWorld world = server.getWorld(key);
        if (world == null) return;
        for (BlockPos pos : had) restore(world, pos);
    }

    private static boolean itNear(ServerPlayerEntity p) {
        return !p.getServerWorld().getEntitiesByType(ModEntities.TALL_ONE, p.getBoundingBox().expand(24),
                it -> it.puppetOwner() == null || !it.puppetOwner().equals(p.getUuid())).isEmpty();
    }
}
