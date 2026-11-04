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
 * @param dontlook      true = a §15.4 NIE PATRZ watcher standing behind the player: turning
 *                      around at it is exactly the thing the task forbids
 */
public record FalsePresencePayload(double x, double y, double z, float yaw,
                                   int durationTicks, boolean window, boolean dontlook) implements CustomPayload {
    public static final CustomPayload.Id<FalsePresencePayload> ID = new CustomPayload.Id<>(ItWasTall.id("false_presence"));
    /** Manual codec: PacketCodec.tuple has no overload for seven fields (1.21.1 yarn). */
    public static final PacketCodec<RegistryByteBuf, FalsePresencePayload> CODEC = PacketCodec.of(
            FalsePresencePayload::write, FalsePresencePayload::read);

    private static void write(FalsePresencePayload payload, RegistryByteBuf buf) {
        buf.writeDouble(payload.x);
        buf.writeDouble(payload.y);
        buf.writeDouble(payload.z);
        buf.writeFloat(payload.yaw);
        buf.writeVarInt(Math.max(1, payload.durationTicks));
        buf.writeBoolean(payload.window);
        buf.writeBoolean(payload.dontlook);
    }

    private static FalsePresencePayload read(RegistryByteBuf buf) {
        return new FalsePresencePayload(buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readFloat(), Math.max(1, buf.readVarInt()), buf.readBoolean(), buf.readBoolean());
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
