package com.itwastall.net;

import com.itwastall.ItWasTall;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * Server -> the god's client: is the god walking as It right now, and which player (entity id, -1 = none) is
 * being hunted — that one is outlined for the god alone.
 */
public record GodFormPayload(boolean active, int lockedId) implements CustomPayload {
    public static final CustomPayload.Id<GodFormPayload> ID = new CustomPayload.Id<>(ItWasTall.id("god_form"));
    public static final PacketCodec<RegistryByteBuf, GodFormPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.BOOL, GodFormPayload::active,
            PacketCodecs.VAR_INT, GodFormPayload::lockedId,
            GodFormPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
