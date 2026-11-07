package com.itwastall.net;

import com.itwastall.ItWasTall;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Server -> client: tonight is (or is no longer) a blood moon (red sky, red fog, red moon). */
public record BloodMoonPayload(boolean active) implements CustomPayload {
    public static final CustomPayload.Id<BloodMoonPayload> ID = new CustomPayload.Id<>(ItWasTall.id("blood_moon"));
    public static final PacketCodec<RegistryByteBuf, BloodMoonPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.BOOL, BloodMoonPayload::active, BloodMoonPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
