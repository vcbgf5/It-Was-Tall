package com.itwastall.net;

import com.itwastall.ItWasTall;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Server -> client: lightweight live phase/stage/tension snapshot for the journal and fear HUD. */
public record FearStatePayload(int phase, int stage, float tension) implements CustomPayload {
    public static final CustomPayload.Id<FearStatePayload> ID =
            new CustomPayload.Id<>(ItWasTall.id("fear_state"));
    public static final PacketCodec<RegistryByteBuf, FearStatePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, FearStatePayload::phase,
            PacketCodecs.VAR_INT, FearStatePayload::stage,
            PacketCodecs.FLOAT, FearStatePayload::tension,
            FearStatePayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
