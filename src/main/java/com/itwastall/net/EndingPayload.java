package com.itwastall.net;

import com.itwastall.ItWasTall;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** Server -> client: the ritual held until dawn — show the ending (text and credits). */
public record EndingPayload() implements CustomPayload {
    public static final EndingPayload INSTANCE = new EndingPayload();
    public static final CustomPayload.Id<EndingPayload> ID = new CustomPayload.Id<>(ItWasTall.id("ending"));
    public static final PacketCodec<ByteBuf, EndingPayload> CODEC = PacketCodec.unit(INSTANCE);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
