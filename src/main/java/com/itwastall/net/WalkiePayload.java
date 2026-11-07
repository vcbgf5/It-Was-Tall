package com.itwastall.net;

import com.itwastall.ItWasTall;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * The walkie-talkie. Client -> server: this player holds the talk button ({@code active}) or let go; {@code name}
 * is ignored. Server -> client: {@code name} started / stopped talking on the walkie-talkie; with an empty name it
 * only says whether voice chat is available on this server ({@code active}).
 */
public record WalkiePayload(String name, boolean active) implements CustomPayload {
    public static final CustomPayload.Id<WalkiePayload> ID = new CustomPayload.Id<>(ItWasTall.id("walkie"));
    public static final PacketCodec<RegistryByteBuf, WalkiePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.string(16), WalkiePayload::name,
            PacketCodecs.BOOL, WalkiePayload::active,
            WalkiePayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
