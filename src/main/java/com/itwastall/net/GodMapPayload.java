package com.itwastall.net;

import com.itwastall.ItWasTall;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * Server -> the god's client: the mini-map of the GOD PANEL — 64 x 64 blocks around the chosen player (a map of
 * the surface, or a slice through the caves at their feet when they are underground), plus where everyone is.
 *
 * @param colors 64*64 map colour bytes (MapColor id * 4 + brightness), row by row from the north-west; 0 = unknown
 */
public record GodMapPayload(String target, BlockPos center, boolean cave, byte[] colors, List<Marker> markers) implements CustomPayload {
    public static final int SIZE = 64;

    /**
     * @param kind 0 another player, 1 the chosen player, 2 you (the god), 3 It, 4 a player in another world
     */
    public record Marker(String name, String status, int x, int z, int kind) {
        public static final PacketCodec<RegistryByteBuf, Marker> CODEC = PacketCodec.tuple(
                PacketCodecs.string(16), Marker::name,
                PacketCodecs.string(32), Marker::status,
                PacketCodecs.VAR_INT, Marker::x,
                PacketCodecs.VAR_INT, Marker::z,
                PacketCodecs.VAR_INT, Marker::kind,
                Marker::new);
    }

    public static final CustomPayload.Id<GodMapPayload> ID = new CustomPayload.Id<>(ItWasTall.id("god_map"));
    public static final PacketCodec<RegistryByteBuf, GodMapPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.string(16), GodMapPayload::target,
            BlockPos.PACKET_CODEC, GodMapPayload::center,
            PacketCodecs.BOOL, GodMapPayload::cave,
            PacketCodecs.BYTE_ARRAY, GodMapPayload::colors,
            Marker.CODEC.collect(PacketCodecs.toList(64)), GodMapPayload::markers,
            GodMapPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
