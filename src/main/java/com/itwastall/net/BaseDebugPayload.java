package com.itwastall.net;

import com.itwastall.ItWasTall;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

/**
 * Server -> client (modplan §8.5 debug 3D): the saved BaseMap markers so the client can draw
 * the coloured frames in the world itself (windows blue, doors green, lights yellow, chests
 * orange, the anchor white). Markers are the same encoded ints as {@code BaseMap.fromNbt}
 * stores, so no re-encoding bloats the packet; the client unfolds them around the anchor.
 *
 * <p>Manual codec: PacketCodecs has no int[] constant here, and writeIntArray keeps the
 * payload down to 4 bytes per marker.
 */
public record BaseDebugPayload(long anchor, int[] markers, int ttlSeconds) implements CustomPayload {
    /** Special ttl: open the rotatable 3D base viewer instead of showing world frames. */
    public static final int OPEN_VIEWER = 121;

    public static final CustomPayload.Id<BaseDebugPayload> ID = new CustomPayload.Id<>(ItWasTall.id("base_debug"));
    public static final PacketCodec<RegistryByteBuf, BaseDebugPayload> CODEC = PacketCodec.of(
            BaseDebugPayload::write,
            BaseDebugPayload::read);

    private static void write(BaseDebugPayload payload, RegistryByteBuf buf) {
        buf.writeLong(payload.anchor);
        int[] markers = payload.markers == null ? new int[0] : payload.markers;
        buf.writeVarInt(markers.length);
        for (int marker : markers) buf.writeInt(marker);
        buf.writeVarInt(payload.ttlSeconds);
    }

    private static BaseDebugPayload read(RegistryByteBuf buf) {
        long anchor = buf.readLong();
        int count = buf.readVarInt();
        int[] markers = new int[count];
        for (int i = 0; i < count; i++) markers[i] = buf.readInt();
        return new BaseDebugPayload(anchor, markers, PacketCodecsHelper.clampTtl(buf.readVarInt()));
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    /** Tiny helper so the ttl clamp lives next to the codec (no GC churn per payload). */
    private static final class PacketCodecsHelper {
        static int clampTtl(int ttl) {
            return Math.max(1, Math.min(OPEN_VIEWER, ttl));
        }
    }
}
