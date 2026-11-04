package com.itwastall.net;

import com.itwastall.ItWasTall;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/**
 * Client -> server: the player deliberately looked at a window figure (MODPLAN §12 figure
 * variant). It notices — and hands the player a random task (TaskManager). Bodyless marker
 * packet: everything else lives in server state (per-sighting reporting happens client-side).
 */
public record WindowSeenPayload() implements CustomPayload {
    public static final CustomPayload.Id<WindowSeenPayload> ID =
            new CustomPayload.Id<>(ItWasTall.id("window_seen"));
    public static final PacketCodec<RegistryByteBuf, WindowSeenPayload> CODEC = PacketCodec.unit(new WindowSeenPayload());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
