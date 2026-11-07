package com.itwastall.net;

import com.itwastall.ItWasTall;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

/**
 * Server -> client, every second: where the player's base is (its bed / anchor; {@code hasBase} false = no base
 * mapped yet) and whether the player counts as inside right now (in the base or under a roof).
 */
public record HudInfoPayload(boolean hasBase, BlockPos base, boolean inside) implements CustomPayload {
    public static final CustomPayload.Id<HudInfoPayload> ID = new CustomPayload.Id<>(ItWasTall.id("hud_info"));
    public static final PacketCodec<RegistryByteBuf, HudInfoPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.BOOL, HudInfoPayload::hasBase,
            BlockPos.PACKET_CODEC, HudInfoPayload::base,
            PacketCodecs.BOOL, HudInfoPayload::inside,
            HudInfoPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
