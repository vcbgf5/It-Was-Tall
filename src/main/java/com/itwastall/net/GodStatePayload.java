package com.itwastall.net;

import com.itwastall.ItWasTall;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Server -> client: this player is (or is no longer) the GOD of the session (opens the panel with G). */
public record GodStatePayload(boolean god) implements CustomPayload {
    public static final CustomPayload.Id<GodStatePayload> ID = new CustomPayload.Id<>(ItWasTall.id("god_state"));
    public static final PacketCodec<RegistryByteBuf, GodStatePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.BOOL, GodStatePayload::god, GodStatePayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
