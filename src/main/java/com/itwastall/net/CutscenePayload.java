package com.itwastall.net;

import com.itwastall.ItWasTall;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

/** Server -> client: the ritual's end begins at this fire — the camera takes over for it (CutsceneCamera). */
public record CutscenePayload(BlockPos fire) implements CustomPayload {
    public static final CustomPayload.Id<CutscenePayload> ID = new CustomPayload.Id<>(ItWasTall.id("cutscene"));
    public static final PacketCodec<RegistryByteBuf, CutscenePayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, CutscenePayload::fire, CutscenePayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
