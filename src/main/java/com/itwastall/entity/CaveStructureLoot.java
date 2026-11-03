package com.itwastall.entity;

import com.itwastall.registry.ModItems;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Adds story notes and a rare Warding Crystal to opened underground structure chests. */
public final class CaveStructureLoot {
    private static final String NOTE_TAG = "itwastall_cave_note";
    private static final String CRYSTAL_TAG = "itwastall_cave_crystal";
    private static final Map<String, PendingChest> PENDING = new HashMap<>();

    private record Note(String id, String title, String body) {}
    private record PendingChest(RegistryKey<World> dimension, BlockPos pos, int attempts) {
        PendingChest retry() { return new PendingChest(dimension, pos, attempts + 1); }
    }

    private static final List<Note> NOTES = List.of(
            new Note("mine_report", "MINER'S REPORT",
                    "I heard the knocking again.\n\nIt came from below the mineshaft.\n\nWe sealed the tunnel with stone.\n\nThis morning, the stones were on our side of the wall."),
            new Note("crystal_record", "THE BLUE CRYSTAL",
                    "The old pages mention a crystal hidden in the deep places.\n\nIt reacts to the Tall One.\n\nOrdinary weapons do nothing.\n\nIf you find the crystal, do not waste it."),
            new Note("last_expedition", "LAST EXPEDITION",
                    "There were three of us when we entered.\n\nOnly two names were written in the book when we left.\n\nWe found something shining beneath the city.\n\nI can still hear it calling.")
    );

    private CaveStructureLoot() {}

    public static void init() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClient) return ActionResult.PASS;
            BlockPos pos = hit.getBlockPos().toImmutable();
            if (!(world.getBlockEntity(pos) instanceof LootableContainerBlockEntity container)) {
                return ActionResult.PASS;
            }
            Identifier lootTable = container.getLootTable();
            if (lootTable == null || !isCaveLootTable(lootTable)) return ActionResult.PASS;

            String key = world.getRegistryKey().getValue() + "|" + pos.asLong();
            PENDING.putIfAbsent(key, new PendingChest(world.getRegistryKey(), pos, 0));
            return ActionResult.PASS;
        });
        ServerTickEvents.END_SERVER_TICK.register(CaveStructureLoot::tick);
    }

    private static boolean isCaveLootTable(Identifier id) {
        if (!"minecraft".equals(id.getNamespace())) return false;
        return switch (id.getPath()) {
            case "chests/abandoned_mineshaft",
                 "chests/simple_dungeon",
                 "chests/stronghold_corridor",
                 "chests/stronghold_crossing",
                 "chests/ancient_city",
                 "chests/ancient_city_ice_box" -> true;
            default -> false;
        };
    }

    private static void tick(MinecraftServer server) {
        Iterator<Map.Entry<String, PendingChest>> iterator = PENDING.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, PendingChest> entry = iterator.next();
            PendingChest pending = entry.getValue();
            ServerWorld world = server.getWorld(pending.dimension());
            if (world == null || !world.isChunkLoaded(pending.pos())) {
                if (pending.attempts() > 100) iterator.remove();
                else entry.setValue(pending.retry());
                continue;
            }
            if (!(world.getBlockEntity(pending.pos()) instanceof LootableContainerBlockEntity container)) {
                iterator.remove();
                continue;
            }
            // Let vanilla generate its own loot first; never replace or bypass the loot table.
            if (container.getLootTable() != null) {
                if (pending.attempts() > 100) iterator.remove();
                else entry.setValue(pending.retry());
                continue;
            }
            addContents(container, world);
            iterator.remove();
        }
    }

    private static void addContents(LootableContainerBlockEntity container, ServerWorld world) {
        boolean hasNote = false;
        boolean hasCrystal = false;
        for (int slot = 0; slot < container.size(); slot++) {
            NbtComponent data = container.getStack(slot).get(DataComponentTypes.CUSTOM_DATA);
            if (data == null) continue;
            var nbt = data.copyNbt();
            hasNote |= nbt.getBoolean(NOTE_TAG);
            hasCrystal |= nbt.getBoolean(CRYSTAL_TAG);
        }

        int free = firstEmpty(container);
        if (!hasNote && free >= 0) {
            Note note = NOTES.get(world.getRandom().nextInt(NOTES.size()));
            ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
            book.set(DataComponentTypes.WRITTEN_BOOK_CONTENT, new WrittenBookContentComponent(
                    RawFilteredPair.of(note.title()), "Unknown", 0,
                    List.of(RawFilteredPair.of(Text.literal(note.body()))), true));
            var custom = new net.minecraft.nbt.NbtCompound();
            custom.putBoolean(NOTE_TAG, true);
            custom.putString("note_id", note.id());
            book.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(custom));
            container.setStack(free, book);
        }

        // One in five explored structure chests can contain the rare item.
        if (!hasCrystal && world.getRandom().nextInt(5) == 0) {
            free = firstEmpty(container);
            if (free >= 0) {
                ItemStack crystal = new ItemStack(ModItems.WARDING_CRYSTAL);
                var custom = new net.minecraft.nbt.NbtCompound();
                custom.putBoolean(CRYSTAL_TAG, true);
                crystal.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(custom));
                container.setStack(free, crystal);
            }
        }
        container.markDirty();
    }

    private static int firstEmpty(LootableContainerBlockEntity container) {
        for (int slot = 0; slot < container.size(); slot++) {
            if (container.getStack(slot).isEmpty()) return slot;
        }
        return -1;
    }
}
