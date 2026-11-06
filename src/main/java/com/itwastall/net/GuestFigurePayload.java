package com.itwastall.net;

import com.itwastall.ItWasTall;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Uuids;

import java.util.UUID;

/**
 * Server -> one client: THE GUEST stands at (x, y, z) as an ordinary-looking player named {@code name},
 * its back turned ({@code yaw}), for {@code durationTicks}; {@code remove = true} takes it away.
 * Purely client-side: nobody else sees it, it has no hitbox on the server.
 */
public record GuestFigurePayload(UUID profileId, String name, double x, double y, double z, float yaw,
                                 int durationTicks, boolean remove) implements CustomPayload {
    public static final CustomPayload.Id<GuestFigurePayload> ID = new CustomPayload.Id<>(ItWasTall.id("guest_figure"));
    public static final PacketCodec<RegistryByteBuf, GuestFigurePayload> CODEC = PacketCodec.of(
            (p, buf) -> {
                Uuids.PACKET_CODEC.encode(buf, p.profileId());
                buf.writeString(p.name(), 16);
                buf.writeDouble(p.x());
                buf.writeDouble(p.y());
                buf.writeDouble(p.z());
                buf.writeFloat(p.yaw());
                buf.writeVarInt(p.durationTicks());
                buf.writeBoolean(p.remove());
            },
            buf -> new GuestFigurePayload(Uuids.PACKET_CODEC.decode(buf), buf.readString(16), buf.readDouble(), buf.readDouble(),
                    buf.readDouble(), buf.readFloat(), buf.readVarInt(), buf.readBoolean()));

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
