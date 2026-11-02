package com.itwastall.net;

import com.itwastall.ItWasTall;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Server -> client: play the one-shot "IT JOINED" title, subtitle and sound for {@code durationMs} milliseconds. */
public record ItJoinedPayload(int durationMs) implements CustomPayload {
    public static final CustomPayload.Id<ItJoinedPayload> ID = new CustomPayload.Id<>(ItWasTall.id("it_joined"));
    public static final PacketCodec<RegistryByteBuf, ItJoinedPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, ItJoinedPayload::durationMs,
            ItJoinedPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
