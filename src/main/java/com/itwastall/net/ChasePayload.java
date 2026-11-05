package com.itwastall.net;

import com.itwastall.ItWasTall;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Server -> client: "It is chasing YOU right now" — drives the red chase overlay (ChaseHud). */
public record ChasePayload(boolean active) implements CustomPayload {
    public static final CustomPayload.Id<ChasePayload> ID = new CustomPayload.Id<>(ItWasTall.id("chase"));
    public static final PacketCodec<RegistryByteBuf, ChasePayload> CODEC =
            PacketCodec.tuple(PacketCodecs.BOOL, ChasePayload::active, ChasePayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
