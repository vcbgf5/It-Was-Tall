package com.itwastall.net;

import com.itwastall.ItWasTall;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Server -> client: show a red flickering message in the middle of the screen. */
public record ScareTextPayload(String text, int durationMs) implements CustomPayload {
    public static final CustomPayload.Id<ScareTextPayload> ID = new CustomPayload.Id<>(ItWasTall.id("scare_text"));
    public static final PacketCodec<RegistryByteBuf, ScareTextPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.string(256), ScareTextPayload::text,
            PacketCodecs.VAR_INT, ScareTextPayload::durationMs,
            ScareTextPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
