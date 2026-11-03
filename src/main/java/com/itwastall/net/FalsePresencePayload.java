package com.itwastall.net;

import com.itwastall.ItWasTall;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * Server -> client: show the False Presence (MODPLAN §7, PHASES Poziom 4).
 * The figure is rendered CLIENT-SIDE ONLY (FakeTallOne added to the client world),
 * never synced back, never a real server entity — so it does not count against the
 * one true Tall One limit (MODPLAN §7.4).
 *
 * @param x/y/z         world position of the figure (feet)
 * @param yaw           the direction the figure should face (usually towards the player)
 * @param durationTicks how long the figure lives before quietly vanishing (even if never looked at)
 * @param window        true = this figure belongs to a window knock (stands behind glass)
 */
public record FalsePresencePayload(double x, double y, double z, float yaw,
                                   int durationTicks, boolean window) implements CustomPayload {
    public static final CustomPayload.Id<FalsePresencePayload> ID = new CustomPayload.Id<>(ItWasTall.id("false_presence"));
    public static final PacketCodec<RegistryByteBuf, FalsePresencePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.DOUBLE, FalsePresencePayload::x,
            PacketCodecs.DOUBLE, FalsePresencePayload::y,
            PacketCodecs.DOUBLE, FalsePresencePayload::z,
            PacketCodecs.FLOAT, FalsePresencePayload::yaw,
            PacketCodecs.VAR_INT, FalsePresencePayload::durationTicks,
            PacketCodecs.BOOL, FalsePresencePayload::window,
            FalsePresencePayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
