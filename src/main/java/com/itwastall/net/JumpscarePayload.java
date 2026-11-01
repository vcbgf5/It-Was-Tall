package com.itwastall.net;

import com.itwastall.ItWasTall;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Server -> client: flash the jumpscare image for {@code durationMs} milliseconds. */
public record JumpscarePayload(int durationMs, boolean showImage) implements CustomPayload {
    public static final CustomPayload.Id<JumpscarePayload> ID = new CustomPayload.Id<>(ItWasTall.id("jumpscare"));
    public static final PacketCodec<RegistryByteBuf, JumpscarePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, JumpscarePayload::durationMs,
            PacketCodecs.BOOL, JumpscarePayload::showImage,
            JumpscarePayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
