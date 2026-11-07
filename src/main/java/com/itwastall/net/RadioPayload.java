package com.itwastall.net;

import com.itwastall.ItWasTall;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * Both ways. Server -> client: a broadcast is on the air right now (the radio icon shows ON AIR).
 * Client -> server: the radio key was pressed (switch the radio on / off); {@code onAir} is ignored then.
 */
public record RadioPayload(boolean onAir) implements CustomPayload {
    public static final CustomPayload.Id<RadioPayload> ID = new CustomPayload.Id<>(ItWasTall.id("radio"));
    public static final PacketCodec<RegistryByteBuf, RadioPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.BOOL, RadioPayload::onAir, RadioPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
