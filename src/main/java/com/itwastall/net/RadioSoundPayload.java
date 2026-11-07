package com.itwastall.net;

import com.itwastall.ItWasTall;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server -> client: play a radio sound (a voice clip or the static) WITHOUT a position — like music, in the
 * Ambient volume category — so it never stays behind where it started and sounds the same in both ears.
 */
public record RadioSoundPayload(Identifier sound, float volume) implements CustomPayload {
    public static final CustomPayload.Id<RadioSoundPayload> ID = new CustomPayload.Id<>(ItWasTall.id("radio_sound"));
    public static final PacketCodec<RegistryByteBuf, RadioSoundPayload> CODEC = PacketCodec.tuple(
            Identifier.PACKET_CODEC, RadioSoundPayload::sound,
            PacketCodecs.FLOAT, RadioSoundPayload::volume,
            RadioSoundPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
