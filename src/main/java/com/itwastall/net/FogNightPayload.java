package com.itwastall.net;

import com.itwastall.ItWasTall;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Server -> client: a fog night has begun (or ended): thick grey fog a few blocks away. */
public record FogNightPayload(boolean active) implements CustomPayload {
    public static final CustomPayload.Id<FogNightPayload> ID = new CustomPayload.Id<>(ItWasTall.id("fog_night"));
    public static final PacketCodec<RegistryByteBuf, FogNightPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.BOOL, FogNightPayload::active, FogNightPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
