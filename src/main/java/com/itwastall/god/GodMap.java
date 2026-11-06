package com.itwastall.god;

import com.itwastall.base.BaseTracker;
import com.itwastall.entity.MineEvents;
import com.itwastall.entity.Outspace;
import com.itwastall.entity.TallOneEntity;
import com.itwastall.net.GodMapPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * The GOD PANEL's mini-map: what is around the chosen player and where everybody is. On the surface it is drawn
 * like a Minecraft map (top blocks, shaded by height); underground it is a slice through the caves at the
 * player's feet: open passages light, rock dark, so you see the tunnels they are standing in.
 */
public final class GodMap {
    private static final int HALF = GodMapPayload.SIZE / 2;

    private GodMap() {}

    /** Where a player is, in a few words (for the panel's player list and the markers). */
    public static String status(ServerPlayerEntity p) {
        int y = p.getBlockY();
        if (Outspace.inOutspace(p)) return "DREAMING (outspace)";
        if (p.getWorld().getRegistryKey() == World.NETHER) return "NETHER  Y " + y;
        if (p.getWorld().getRegistryKey() == World.END) return "THE END  Y " + y;
        if (MineEvents.isUnderground(p)) return "CAVE  Y " + y;
        if (BaseTracker.isInBase(p)) return "IN BASE";
        return "SURFACE  Y " + y;
    }

    public static void send(ServerPlayerEntity god, String targetName) {
        if (god.getServer() == null || !ServerPlayNetworking.canSend(god, GodMapPayload.ID)) return;
        ServerPlayerEntity target = god.getServer().getPlayerManager().getPlayer(targetName);
        if (target == null || GodMode.isGod(target.getUuid())) return;
        ServerWorld world = target.getServerWorld();
        BlockPos center = target.getBlockPos();
        boolean cave = MineEvents.isUnderground(target) || world.getDimension().hasCeiling();
        byte[] colors = cave ? caveSlice(world, center) : surface(world, center);

        List<GodMapPayload.Marker> markers = new ArrayList<>();
        for (ServerPlayerEntity p : god.getServer().getPlayerManager().getPlayerList()) {
            boolean here = p.getWorld() == world;
            int kind;
            if (p == god) kind = 2;
            else if (GodMode.isGod(p.getUuid())) continue;
            else if (p == target) kind = 1;
            else kind = here ? 0 : 4;
            if (p == god && !here) continue;
            markers.add(new GodMapPayload.Marker(p.getGameProfile().getName(), status(p), p.getBlockX(), p.getBlockZ(), kind));
        }
        Box area = new Box(center).expand(HALF, 48, HALF);
        for (TallOneEntity it : world.getEntitiesByClass(TallOneEntity.class, area, e -> e.isAlive())) {
            if (markers.size() >= 60) break;
            markers.add(new GodMapPayload.Marker("It", it.getMode().name(), it.getBlockX(), it.getBlockZ(), 3));
        }
        ServerPlayNetworking.send(god, new GodMapPayload(target.getGameProfile().getName(), center, cave, colors, markers));
    }

    private static byte[] surface(ServerWorld world, BlockPos c) {
        int n = GodMapPayload.SIZE;
        byte[] out = new byte[n * n];
        int[] prevRow = new int[n];
        for (int dz = -HALF - 1; dz < HALF; dz++) {
            int[] row = new int[n];
            for (int dx = -HALF; dx < HALF; dx++) {
                int x = c.getX() + dx, z = c.getZ() + dz, i = dx + HALF;
                if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                    row[i] = Integer.MIN_VALUE;
                    continue;
                }
                int top = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
                row[i] = top;
                if (dz < -HALF) continue;                         // the extra row only feeds the shading
                BlockPos pos = new BlockPos(x, top, z);
                BlockState st = world.getBlockState(pos);
                MapColor color = st.getMapColor(world, pos);
                MapColor.Brightness b = MapColor.Brightness.NORMAL;
                if (prevRow[i] != Integer.MIN_VALUE) {
                    if (top > prevRow[i]) b = MapColor.Brightness.HIGH;
                    else if (top < prevRow[i]) b = MapColor.Brightness.LOW;
                }
                out[(dz + HALF) * n + i] = color == MapColor.CLEAR ? 0 : color.getRenderColorByte(b);
            }
            prevRow = row;
        }
        return out;
    }

    private static byte[] caveSlice(ServerWorld world, BlockPos c) {
        int n = GodMapPayload.SIZE;
        byte[] out = new byte[n * n];
        int y = c.getY();
        for (int dz = -HALF; dz < HALF; dz++) {
            for (int dx = -HALF; dx < HALF; dx++) {
                int x = c.getX() + dx, z = c.getZ() + dz;
                if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
                BlockPos feet = new BlockPos(x, y, z);
                BlockState at = world.getBlockState(feet);
                BlockState head = world.getBlockState(feet.up());
                byte value;
                if (!at.getFluidState().isEmpty()) {
                    value = at.getMapColor(world, feet).getRenderColorByte(MapColor.Brightness.NORMAL);
                } else if (at.getCollisionShape(world, feet).isEmpty() && head.getCollisionShape(world, feet.up()).isEmpty()) {
                    // an open passage: the floor under it, lit
                    BlockPos below = feet.down();
                    BlockState floor = world.getBlockState(below);
                    MapColor fc = floor.getMapColor(world, below);
                    value = fc == MapColor.CLEAR ? MapColor.GRAY.getRenderColorByte(MapColor.Brightness.LOW)
                            : fc.getRenderColorByte(MapColor.Brightness.HIGH);
                } else {
                    MapColor rc = at.getMapColor(world, feet);
                    value = (rc == MapColor.CLEAR ? MapColor.STONE_GRAY : rc).getRenderColorByte(MapColor.Brightness.LOWEST);
                }
                out[(dz + HALF) * n + (dx + HALF)] = value;
            }
        }
        return out;
    }
}
