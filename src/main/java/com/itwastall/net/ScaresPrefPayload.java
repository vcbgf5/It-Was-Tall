package com.itwastall.net;

import com.itwastall.ItWasTall;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * Client -> server: the warning screen's "Jumpscares & scares" toggle (the player just
 * accepted the warning). The server keeps the per-player preference in memory and stops
 * sending jumpscare / scare-text payloads to that player — nothing on the client side can
 * fake silence, the server must simply hold back.
 *
 * @param scaresEnabled true = normal flow (default); false = no jumpscare payload / no red text for this player
 */
public record ScaresPrefPayload(boolean scaresEnabled) implements CustomPayload {
    public static final CustomPayload.Id<ScaresPrefPayload> ID = new CustomPayload.Id<>(ItWasTall.id("scares_pref"));
    public static final PacketCodec<RegistryByteBuf, ScaresPrefPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.BOOL, ScaresPrefPayload::scaresEnabled,
            ScaresPrefPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
