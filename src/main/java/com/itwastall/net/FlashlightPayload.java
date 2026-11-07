package com.itwastall.net;

import com.itwastall.ItWasTall;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Client -> server: the player switched the flashlight on / off (middle mouse button). */
public record FlashlightPayload(boolean on) implements CustomPayload {
    public static final CustomPayload.Id<FlashlightPayload> ID = new CustomPayload.Id<>(ItWasTall.id("flashlight"));
    public static final PacketCodec<RegistryByteBuf, FlashlightPayload> CODEC =
            PacketCodec.tuple(PacketCodecs.BOOL, FlashlightPayload::on, FlashlightPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
