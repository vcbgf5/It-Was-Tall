package com.itwastall.net;

import com.itwastall.ItWasTall;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/** Server -> client: THE EYES — where the pairs of eyes open in the dark between the trees (drawn client side only). */
public record EyeSwarmPayload(List<BlockPos> spots) implements CustomPayload {
    public static final CustomPayload.Id<EyeSwarmPayload> ID = new CustomPayload.Id<>(ItWasTall.id("eye_swarm"));
    public static final PacketCodec<RegistryByteBuf, EyeSwarmPayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC.collect(PacketCodecs.toList(32)), EyeSwarmPayload::spots,
            EyeSwarmPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
