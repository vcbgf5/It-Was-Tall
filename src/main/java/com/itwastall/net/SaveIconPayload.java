package com.itwastall.net;

import com.itwastall.ItWasTall;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** Server -> client: the game was just saved — the quill writes on its sheet in the corner of the screen. */
public record SaveIconPayload() implements CustomPayload {
    public static final SaveIconPayload INSTANCE = new SaveIconPayload();
    public static final CustomPayload.Id<SaveIconPayload> ID = new CustomPayload.Id<>(ItWasTall.id("save_icon"));
    public static final PacketCodec<ByteBuf, SaveIconPayload> CODEC = PacketCodec.unit(INSTANCE);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
