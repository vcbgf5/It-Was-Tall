package com.itwastall.entity;

import com.itwastall.ItWasTall;
import com.itwastall.base.BaseTracker;
import com.itwastall.config.ItWasTallConfig;
import com.itwastall.director.Director;
import com.itwastall.net.EyeSwarmPayload;
import com.itwastall.world.JournalLog;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Heightmap;
import net.minecraft.world.LightType;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * THE EYES. In a dark wood at night a pair of white eyes opens between the trees. Then another. Then a dozen.
 * Hold up a light or walk at them and they close, one after another — and the last pair opens right in front of
 * your face. The server only picks the spots in the dark between the trunks; the client draws and runs it.
 */
public final class EyeSwarms {
    private static final Map<UUID, Long> NEXT = new HashMap<>();

    private EyeSwarms() {}

    public static void init() {
        PayloadTypeRegistry.playS2C().register(EyeSwarmPayload.ID, EyeSwarmPayload.CODEC);
        ServerTickEvents.END_SERVER_TICK.register(EyeSwarms::tick);
    }

    public static void forget(UUID player) {
        NEXT.remove(player);
    }

    public static long nextAt(UUID player) {
        Long n = NEXT.get(player);
        return n == null ? -1 : n;
    }

    private static void tick(MinecraftServer server) {
        if (server.getTicks() % 40 != 0) return;
        ItWasTallConfig.Values cfg = ItWasTallConfig.INSTANCE;
        if (!cfg.eyeSwarms || Director.phase() != 1 || Director.stageIndex() < 2) return;
        ServerWorld world = server.getOverworld();
        if (!world.isNight()) return;
        long now = world.getTime();
        for (ServerPlayerEntity p : world.getPlayers()) {
            if (!p.isAlive() || p.isSpectator() || p.isCreative()) continue;
            Long next = NEXT.get(p.getUuid());
            if (next == null) {
                NEXT.put(p.getUuid(), now + BloodMoon.scale(20L * 60 * (12 + p.getRandom().nextInt(11))));
                continue;
            }
            if (now < next || BaseTracker.isInBase(p) || !EventGate.ready(p) || !inWoods(world, p.getBlockPos())) continue;
            if (start(p)) EventGate.mark(p);
            NEXT.put(p.getUuid(), now + BloodMoon.scale(20L * 60 * (12 + p.getRandom().nextInt(11))));
        }
    }

    /** At least a few trunks around: the eyes need trees to hide between. */
    private static boolean inWoods(ServerWorld world, BlockPos at) {
        int logs = 0;
        for (BlockPos p : BlockPos.iterate(at.add(-10, -1, -10), at.add(10, 4, 10))) {
            if (world.getBlockState(p).isIn(BlockTags.LOGS) && ++logs >= 12) return true;
        }
        return false;
    }

    /** Opens the eyes around that player (also /itwastall event eyes). */
    public static boolean start(ServerPlayerEntity p) {
        ServerWorld world = p.getServerWorld();
        if (world.getRegistryKey() != World.OVERWORLD || !ServerPlayNetworking.canSend(p, EyeSwarmPayload.ID)) return false;
        var r = p.getRandom();
        List<BlockPos> spots = new ArrayList<>();
        for (int attempt = 0; attempt < 400 && spots.size() < 16; attempt++) {
            double angle = r.nextDouble() * Math.PI * 2;
            double dist = 8 + r.nextDouble() * 15;
            int x = MathHelper.floor(p.getX() + Math.cos(angle) * dist), z = MathHelper.floor(p.getZ() + Math.sin(angle) * dist);
            if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
            int ground = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (Math.abs(ground - p.getY()) > 8) continue;
            BlockPos spot = new BlockPos(x, ground + 1 + r.nextInt(2), z);
            BlockState below = world.getBlockState(spot.down(spot.getY() - ground + 1));
            if (!world.getBlockState(spot).isAir() || !below.getFluidState().isEmpty()) continue;
            if (world.getLightLevel(LightType.BLOCK, spot) > 4) continue;           // only in the dark
            if (!nearTree(world, spot)) continue;                                   // between the trunks
            boolean crowded = false;
            for (BlockPos o : spots) if (o.getSquaredDistance(spot) < 2.5 * 2.5) crowded = true;
            if (!crowded) spots.add(spot);
        }
        if (spots.size() < 4) return false;
        ServerPlayNetworking.send(p, new EyeSwarmPayload(spots));
        JournalLog.add(p, "Eyes. Between the trees. More every time I looked.");
        com.itwastall.advancement.Achievements.grant(p, "eyes");
        ItWasTall.LOGGER.info("{} pairs of eyes opened around {}.", spots.size(), p.getName().getString());
        return true;
    }

    private static boolean nearTree(ServerWorld world, BlockPos spot) {
        for (BlockPos q : BlockPos.iterate(spot.add(-2, -2, -2), spot.add(2, 1, 2))) {
            BlockState st = world.getBlockState(q);
            if (st.isIn(BlockTags.LOGS) || st.isIn(BlockTags.LEAVES)) return true;
        }
        return false;
    }
}
