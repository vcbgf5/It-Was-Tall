package com.itwastall.net;

import com.itwastall.ItWasTall;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

import java.util.List;

/**
 * Server -> client: lightweight live snapshot for the journal, fear HUD and the task toast
 * area — phase/stage/tension plus the player's CURRENT task line and the lore book ids
 * already found (MODPLAN §9: "what the journal knows"). Pulsed once per second.
 */
public record FearStatePayload(int phase, int stage, float tension, String task, List<String> loreIds) implements CustomPayload {
    public static final CustomPayload.Id<FearStatePayload> ID =
            new CustomPayload.Id<>(ItWasTall.id("fear_state"));
    public static final PacketCodec<RegistryByteBuf, FearStatePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, FearStatePayload::phase,
            PacketCodecs.VAR_INT, FearStatePayload::stage,
            PacketCodecs.FLOAT, FearStatePayload::tension,
            PacketCodecs.string(96), FearStatePayload::task,
            PacketCodecs.collection(java.util.ArrayList::new, PacketCodecs.string(96)),
            FearStatePayload::loreIds,
            FearStatePayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
