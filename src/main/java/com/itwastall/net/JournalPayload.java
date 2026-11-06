package com.itwastall.net;

import com.itwastall.ItWasTall;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

import java.util.List;

/**
 * Server -> client: the journal's BASE and EVENTS pages (MODPLAN §16).
 *
 * @param baseCounts empty when no base is mapped yet, otherwise
 *                   [anchorX, anchorY, anchorZ, windows, doors, lights, chests, darkCorners, placedBlocks]
 * @param events     the player's {@code JournalLog}, newest first
 * @param tasks      the player's task history (assigned / done / failed), newest first
 */
public record JournalPayload(List<Integer> baseCounts, List<String> events, List<String> tasks) implements CustomPayload {
    public static final CustomPayload.Id<JournalPayload> ID = new CustomPayload.Id<>(ItWasTall.id("journal"));
    public static final PacketCodec<RegistryByteBuf, JournalPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.collection(java.util.ArrayList::new, PacketCodecs.VAR_INT, 16), JournalPayload::baseCounts,
            PacketCodecs.collection(java.util.ArrayList::new, PacketCodecs.string(160), 16), JournalPayload::events,
            PacketCodecs.collection(java.util.ArrayList::new, PacketCodecs.string(160), 16), JournalPayload::tasks,
            JournalPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
