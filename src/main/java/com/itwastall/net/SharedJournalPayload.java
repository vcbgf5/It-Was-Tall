package com.itwastall.net;

import com.itwastall.ItWasTall;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

import java.util.List;

/**
 * Server -> client: the server-wide part of the journal — every book / note anyone has read (BOOKS
 * tab, readable by everyone) and the notes players wrote for each other (NOTES tab). Sent when a
 * player joins and to everyone whenever it changes.
 */
public record SharedJournalPayload(List<Book> books, List<Note> notes) implements CustomPayload {
    public static final CustomPayload.Id<SharedJournalPayload> ID = new CustomPayload.Id<>(ItWasTall.id("shared_journal"));

    /** A book or note someone found: {@code foundBy} is the player who read it first. */
    public record Book(String id, String title, String body, String foundBy) {
        public static final PacketCodec<RegistryByteBuf, Book> CODEC = PacketCodec.tuple(
                PacketCodecs.string(96), Book::id,
                PacketCodecs.string(96), Book::title,
                PacketCodecs.string(8192), Book::body,
                PacketCodecs.string(64), Book::foundBy,
                Book::new);
    }

    /** A note on the shared board. */
    public record Note(long id, String author, String authorUuid, String when, String text) {
        public static final PacketCodec<RegistryByteBuf, Note> CODEC = PacketCodec.tuple(
                PacketCodecs.VAR_LONG, Note::id,
                PacketCodecs.string(64), Note::author,
                PacketCodecs.string(40), Note::authorUuid,
                PacketCodecs.string(32), Note::when,
                PacketCodecs.string(256), Note::text,
                Note::new);
    }

    public static final PacketCodec<RegistryByteBuf, SharedJournalPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.collection(java.util.ArrayList::new, Book.CODEC, 512), SharedJournalPayload::books,
            PacketCodecs.collection(java.util.ArrayList::new, Note.CODEC, 512), SharedJournalPayload::notes,
            SharedJournalPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
