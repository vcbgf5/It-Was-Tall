package com.itwastall.client.render;

import com.itwastall.net.GuestFigurePayload;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * THE GUEST, client side: an ordinary-looking player (its name tag, the skin the game picks for that
 * profile) standing far away with its back turned. Only this client sees it; it is removed when the
 * server says so or when its time is up.
 */
public final class GuestFigures {
    private static final Map<UUID, Integer> IDS = new HashMap<>();
    private static final Map<Integer, Long> UNTIL = new HashMap<>();
    private static int nextId = -2_100_000;

    private GuestFigures() {
    }

    public static void handle(GuestFigurePayload p) {
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) return;
        Integer old = IDS.remove(p.profileId());
        if (old != null) {
            world.removeEntity(old, Entity.RemovalReason.DISCARDED);
            UNTIL.remove(old);
        }
        if (p.remove()) return;
        OtherClientPlayerEntity guest = new OtherClientPlayerEntity(world, new GameProfile(p.profileId(), p.name()));
        int id = nextId--;
        guest.setId(id);
        guest.refreshPositionAndAngles(p.x(), p.y(), p.z(), p.yaw(), 0f);
        guest.setHeadYaw(p.yaw());
        guest.setBodyYaw(p.yaw());
        world.addEntity(guest);
        IDS.put(p.profileId(), id);
        UNTIL.put(id, System.currentTimeMillis() + p.durationTicks() * 50L);
    }

    /** Called every client tick: the guest never outstays its time. */
    public static void tick() {
        if (UNTIL.isEmpty()) return;
        ClientWorld world = MinecraftClient.getInstance().world;
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<Integer, Long>> it = UNTIL.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Long> e = it.next();
            if (now < e.getValue()) continue;
            if (world != null) world.removeEntity(e.getKey(), Entity.RemovalReason.DISCARDED);
            IDS.values().remove(e.getKey());
            it.remove();
        }
    }

    public static void clear() {
        IDS.clear();
        UNTIL.clear();
    }
}
