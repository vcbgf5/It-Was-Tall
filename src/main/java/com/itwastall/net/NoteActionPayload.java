package com.itwastall.net;

import com.itwastall.ItWasTall;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * Client -> server: the NOTES tab. {@code action} {@link #ADD} pins {@code text} to the shared board,
 * {@link #DELETE} removes note {@code id} (only its author or an operator may). The server validates
 * and rate-limits everything.
 */
public record NoteActionPayload(int action, long id, String text) implements CustomPayload {
    public static final int ADD = 0, DELETE = 1;
    public static final CustomPayload.Id<NoteActionPayload> ID = new CustomPayload.Id<>(ItWasTall.id("note_action"));
    public static final PacketCodec<RegistryByteBuf, NoteActionPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, NoteActionPayload::action,
            PacketCodecs.VAR_LONG, NoteActionPayload::id,
            PacketCodecs.string(256), NoteActionPayload::text,
            NoteActionPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
