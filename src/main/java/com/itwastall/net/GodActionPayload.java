package com.itwastall.net;

import com.itwastall.ItWasTall;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Client -> server: the god's panel asks for an action ({@code action}) on a player ({@code target}, may be empty). */
public record GodActionPayload(String action, String target) implements CustomPayload {
    public static final CustomPayload.Id<GodActionPayload> ID = new CustomPayload.Id<>(ItWasTall.id("god_action"));
    public static final PacketCodec<RegistryByteBuf, GodActionPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.string(32), GodActionPayload::action,
            PacketCodecs.string(16), GodActionPayload::target,
            GodActionPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
