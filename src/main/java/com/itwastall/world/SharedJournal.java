package com.itwastall.world;

import com.itwastall.ItWasTall;
import com.itwastall.net.NoteActionPayload;
import com.itwastall.net.SharedJournalPayload;
import com.itwastall.registry.ModItems;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.TypedActionResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The server-wide half of the journal.
 * <ul>
 *   <li>BOOKS: right-click one of our books (or a note) and it is recorded for the whole server —
 *       everyone's journal lists it, anyone can open it and read it there.</li>
 *   <li>NOTES: a shared notice board; players write short notes for each other (saved with the world).</li>
 * </ul>
 * Everything lives in {@link ItWasTallState} and is sent to each player on join and to everyone on change.
 */
public final class SharedJournal {
    public static final int MAX_NOTES = 200, MAX_NOTE_LENGTH = 200;
    /** Milliseconds between two notes of one player (spam guard). */
    private static final long NOTE_COOLDOWN_MS = 3000;
    private static final Map<UUID, Long> LAST_NOTE = new HashMap<>();

    private SharedJournal() {
    }

    public static void init() {
        // server side of reading: the client opens the reader itself, the server records the book
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (!world.isClient && player instanceof ServerPlayerEntity sp && ModItems.isReadable(stack)) {
                NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
                NbtCompound nbt = data == null ? new NbtCompound() : data.copyNbt();
                registerBook(sp, nbt.getString(ModItems.NOTE_ID), nbt.getString(ModItems.NOTE_TITLE), nbt.getString(ModItems.NOTE_BODY));
            }
            return TypedActionResult.pass(ItemStack.EMPTY);
        });
    }

    public static void forget(UUID player) {
        LAST_NOTE.remove(player);
    }

    /** Records a read book for the whole server (once per book id) and tells everyone. */
    public static void registerBook(ServerPlayerEntity reader, String id, String title, String body) {
        MinecraftServer server = reader.getServer();
        if (server == null) return;
        String t = title == null || title.isBlank() ? "Note" : title.strip();
        String key = id == null || id.isBlank() ? "book_" + t.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_") : id;
        ItWasTallState state = ItWasTallState.getOrCreate(server);
        if (state.hasSharedBook(key)) return;
        NbtCompound book = new NbtCompound();
        book.putString("id", key);
        book.putString("title", t.length() > 90 ? t.substring(0, 90) : t);
        String b = body == null ? "" : body.replace("|", "\n");
        book.putString("body", b.length() > 8000 ? b.substring(0, 8000) : b);
        book.putString("foundBy", reader.getName().getString());
        book.putLong("day", server.getOverworld().getTimeOfDay() / 24000L + 1);
        state.addSharedBook(book);
        JournalLog.add(reader, "I read \"" + t + "\". It is in the journal now.");
        Text msg = Text.literal("[Journal] ").formatted(Formatting.DARK_RED)
                .append(Text.literal(reader.getName().getString() + " found \"" + t + "\" — open your journal (J) to read it.")
                        .formatted(Formatting.GRAY, Formatting.ITALIC));
        for (ServerPlayerEntity p : PlayerLookup.all(server)) p.sendMessage(msg, false);
        ItWasTall.LOGGER.info("{} added '{}' to the shared journal.", reader.getName().getString(), t);
        broadcast(server);
    }

    /** NOTES tab actions from a client: validated, rate-limited, saved, broadcast. */
    public static void handle(ServerPlayerEntity player, NoteActionPayload payload) {
        if (player == null || player.getServer() == null) return;
        MinecraftServer server = player.getServer();
        ItWasTallState state = ItWasTallState.getOrCreate(server);
        if (payload.action() == NoteActionPayload.ADD) {
            long now = System.currentTimeMillis();
            Long last = LAST_NOTE.get(player.getUuid());
            if (last != null && now - last < NOTE_COOLDOWN_MS) return;
            String text = clean(payload.text());
            if (text.isEmpty()) return;
            LAST_NOTE.put(player.getUuid(), now);
            NbtCompound note = new NbtCompound();
            note.putString("author", player.getName().getString());
            note.putString("authorUuid", player.getUuidAsString());
            note.putString("when", "Day " + (server.getOverworld().getTimeOfDay() / 24000L + 1));
            note.putString("text", text);
            state.addSharedNote(note, MAX_NOTES);
            broadcast(server);
        } else if (payload.action() == NoteActionPayload.DELETE) {
            NbtCompound note = state.sharedNote(payload.id());
            if (note == null) return;
            boolean own = player.getUuidAsString().equals(note.getString("authorUuid"));
            if (!own && !player.hasPermissionLevel(2)) return;
            if (state.removeSharedNote(payload.id())) broadcast(server);
        }
    }

    /** No colour codes, no control characters, single spaces, at most MAX_NOTE_LENGTH characters. */
    private static String clean(String raw) {
        if (raw == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : raw.toCharArray()) {
            if (c == '§' || c < 32 || c == 127) sb.append(' ');
            else sb.append(c);
        }
        String s = sb.toString().replaceAll("\\s+", " ").strip();
        return s.length() > MAX_NOTE_LENGTH ? s.substring(0, MAX_NOTE_LENGTH) : s;
    }

    private static SharedJournalPayload payload(MinecraftServer server) {
        ItWasTallState state = ItWasTallState.getOrCreate(server);
        List<SharedJournalPayload.Book> books = new ArrayList<>();
        for (NbtCompound b : state.sharedBooks()) {
            books.add(new SharedJournalPayload.Book(b.getString("id"), b.getString("title"), b.getString("body"), b.getString("foundBy")));
        }
        List<SharedJournalPayload.Note> notes = new ArrayList<>();
        for (NbtCompound n : state.sharedNotes()) {
            notes.add(new SharedJournalPayload.Note(n.getLong("id"), n.getString("author"), n.getString("authorUuid"),
                    n.getString("when"), n.getString("text")));
        }
        return new SharedJournalPayload(books, notes);
    }

    /** Sends the shared journal to one player (on join). */
    public static void sync(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null || !ServerPlayNetworking.canSend(player, SharedJournalPayload.ID)) return;
        ServerPlayNetworking.send(player, payload(server));
    }

    /** Sends the shared journal to everyone online (after a change). */
    public static void broadcast(MinecraftServer server) {
        SharedJournalPayload p = payload(server);
        for (ServerPlayerEntity player : PlayerLookup.all(server)) {
            if (ServerPlayNetworking.canSend(player, SharedJournalPayload.ID)) ServerPlayNetworking.send(player, p);
        }
    }
}
