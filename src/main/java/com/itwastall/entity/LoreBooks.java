package com.itwastall.entity;

import com.itwastall.ItWasTall;
import com.itwastall.base.BaseMap;
import com.itwastall.base.BaseTracker;
import com.itwastall.config.ItWasTallConfig;
import com.itwastall.director.Director;
import com.itwastall.world.ItWasTallState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Secretly places the MODPLAN §9 lore books into free slots in the player's mapped storage.
 * It never replaces items, never forces a loot-table chest to generate, and persists delivery
 * and cooldown state through ItWasTallState.
 */
public final class LoreBooks {
    private static final Map<UUID, Long> LAST_CHECK = new HashMap<>();
    private static final String ITEM_ID_KEY = "itwastall_lore_book";

    private record Book(String id, String title, String body) {}

    private static final List<Book> BOOKS = List.of(
            new Book("01_someone_was_here", "SOMEONE WAS HERE",
                    "I found this book in my chest.\n\nI don't remember putting it there.\n\nI live alone.\n\nI think.\n\nThere was something outside yesterday.\n\nI didn't see its face.\n\nBut it was tall.\n\nVery tall."),
            new Book("02_the_window", "THE WINDOW",
                    "I saw something outside my house.\n\nIt was standing near the trees.\n\nI looked away for a second.\n\nIt was closer.\n\nI closed the curtains.\n\nI haven't opened them since."),
            new Book("03_the_mine", "THE MINE",
                    "I heard someone mining.\n\nThere was nobody else online.\n\nI followed the sound.\n\nI found a tunnel.\n\nAt the end of it...\n\nSomething was standing there.\n\nIt was looking at me."),
            new Book("04_dont_open", "DON'T OPEN",
                    "It knocked three times.\n\nI checked the door.\n\nNothing.\n\nThen it knocked again.\n\nI checked the window.\n\nNothing.\n\nThen I heard another knock.\n\nFrom inside."),
            new Book("05_the_player_list", "THE PLAYER LIST",
                    "I checked the player list.\n\nThere was another name.\n\nI didn't recognize him.\n\nNobody invited him.\n\nNobody could find him.\n\nBut he was still there."),
            new Book("06_it_joined", "IT JOINED",
                    "Something joined this world.\n\nI saw it in the distance.\n\nIt didn't move.\n\nI checked the player list.\n\nThere was nobody new.\n\nSo what did I see?"),
            new Book("07_observation", "OBSERVATION",
                    "It doesn't always follow me.\n\nSometimes it just stands there.\n\nI can see it watching.\n\nWhen I turn around...\n\nIt's gone.\n\nI don't think it leaves."),
            new Book("08_the_chest", "THE CHEST",
                    "I locked my storage.\n\nI checked every item.\n\nEverything was normal.\n\nThen I found another book.\n\nI never put it there."),
            new Book("09_coordinates", "COORDINATES",
                    "I found coordinates written on a piece of paper.\n\nI went there.\n\nThere was nothing.\n\nI came back the next day.\n\nSomething was standing there."),
            new Book("10_the_last_entry", "THE LAST ENTRY",
                    "I don't think it wants to kill me.\n\nI think it wants me to know it's here.\n\nI stopped sleeping.\n\nI stopped leaving my house.\n\nIt still finds me.\n\nI can hear it outside."),
            new Book("11_the_list", "THE LIST", ""),
            new Book("12_it_knows", "IT KNOWS",
                    "You found all the books.\n\nYou know about the knocking.\n\nYou know about the mines.\n\nYou know about the thing outside your window.\n\nBut there is something you still don't understand.\n\nIt was never outside.")
    );

    private LoreBooks() {}

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(LoreBooks::tick);
    }

    public static void forget(UUID player) {
        LAST_CHECK.remove(player);
    }

    private static void tick(MinecraftServer server) {
        if (!ItWasTallConfig.INSTANCE.loreBooks || Director.phase() != 1) return;

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID uuid = player.getUuid();
            ServerWorld world = player.getServerWorld();
            if (world.getRegistryKey() != World.OVERWORLD) continue;

            long now = world.getTime();
            Long last = LAST_CHECK.get(uuid);
            if (last != null && now - last < 200L) continue;
            LAST_CHECK.put(uuid, now);

            ItWasTallState saved = ItWasTallState.getOrCreate(server);
            if (now < saved.loreBookCooldown(uuid)) continue;
            if (world.getRandom().nextDouble() >= Math.max(0.0, Math.min(1.0,
                    ItWasTallConfig.INSTANCE.loreBookChance))) continue;

            BaseMap map = BaseTracker.mapOf(uuid);
            if (map == null || map.count(BaseMap.TYPE_CHEST) == 0) continue;

            Book next = nextBook(server, saved, uuid);
            if (next == null) continue;
            if (placeInBase(world, map, next)) {
                saved.markLoreBookDelivered(uuid, next.id());
                long cooldown = Math.max(1, ItWasTallConfig.INSTANCE.loreBookCooldownMinutes) * 60L * 20L;
                saved.setLoreBookCooldown(uuid, now + cooldown);
                ItWasTall.LOGGER.info("Placed lore book '{}' in {}'s mapped storage.",
                        next.title(), player.getName().getString());
            }
        }
    }

    private static Book nextBook(MinecraftServer server, ItWasTallState state, UUID player) {
        int delivered = state.deliveredLoreBookCount(player);
        // The final reveal only appears after all ten narrative entries and THE LIST were delivered.
        if (delivered >= 11 && !state.hasLoreBook(player, "12_it_knows")) {
            return BOOKS.get(11);
        }

        List<Book> available = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            Book book = BOOKS.get(i);
            if (!state.hasLoreBook(player, book.id())) available.add(book);
        }
        if (available.isEmpty()) return null;
        return available.get(server.getOverworld().getRandom().nextInt(available.size()));
    }

    private static boolean placeInBase(ServerWorld world, BaseMap map, Book book) {
        List<BlockPos> chests = map.all(BaseMap.TYPE_CHEST);
        if (chests.isEmpty()) return false;

        // Rotate the first candidate so the same chest is not always selected.
        int start = world.getRandom().nextInt(chests.size());
        Set<Long> checked = new HashSet<>();
        for (int offset = 0; offset < chests.size(); offset++) {
            BlockPos pos = chests.get((start + offset) % chests.size());
            if (!checked.add(pos.asLong()) || !world.isChunkLoaded(pos)) continue;
            if (!(world.getBlockEntity(pos) instanceof LootableContainerBlockEntity inventory)) continue;

            // Do not alter unopened generated loot; let vanilla generate it naturally first.
            if (inventory.getLootTable() != null) continue;

            // Crash-safe duplicate check: the unique item tag is checked before insertion.
            if (containsBook(inventory, book.id())) return false;

            for (int slot = 0; slot < inventory.size(); slot++) {
                if (!inventory.getStack(slot).isEmpty()) continue;
                inventory.setStack(slot, createBook(book, world.getServer()));
                inventory.markDirty();
                return true;
            }
        }
        return false;
    }

    private static boolean containsBook(LootableContainerBlockEntity inventory, String id) {
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            NbtComponent custom = stack.get(DataComponentTypes.CUSTOM_DATA);
            if (custom != null && id.equals(custom.copyNbt().getString(ITEM_ID_KEY))) return true;
        }
        return false;
    }

    private static ItemStack createBook(Book book, MinecraftServer server) {
        String body = book.id().equals("11_the_list") ? playerList(server) : book.body();
        ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);
        WrittenBookContentComponent content = new WrittenBookContentComponent(
                RawFilteredPair.of(book.title()),
                "Unknown",
                0,
                List.of(RawFilteredPair.of(Text.literal(body))),
                true
        );
        stack.set(DataComponentTypes.WRITTEN_BOOK_CONTENT, content);
        var custom = new net.minecraft.nbt.NbtCompound();
        custom.putString(ITEM_ID_KEY, book.id());
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(custom));
        return stack;
    }

    private static String playerList(MinecraftServer server) {
        List<String> names = server.getPlayerManager().getPlayerList().stream()
                .map(player -> player.getName().getString())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        if (names.isEmpty()) return "I checked the player list.\n\nNobody was there.\n\nI still heard someone walking.";
        return "I checked the player list.\n\nThese names were there:\n\n"
                + String.join("\n", names)
                + "\n\nI don't remember inviting everyone.";
    }
}
