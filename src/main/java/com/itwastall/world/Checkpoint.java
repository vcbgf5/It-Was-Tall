package com.itwastall.world;

import com.itwastall.ItWasTall;
import com.itwastall.net.ModNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * CHECKPOINTS: when something worth keeping happens to a player (an achievement, a new journal line, a shared note,
 * a finished task) the mod writes its own world data and that player's advancements to disk right away — not only
 * at the next autosave — and the quill writes on its sheet in the corner of the player's screen.
 * Disk writes are spaced at least 5 s apart; the icon at most every 3 s per player.
 */
public final class Checkpoint {
    private static final long DISK_GAP_MS = 5_000, ICON_GAP_MS = 3_000;
    private static final Map<UUID, Long> LAST_ICON = new HashMap<>();
    private static long lastDisk;

    private Checkpoint() {}

    public static void save(ServerPlayerEntity player) {
        if (player == null) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;
        if (!server.isOnThread()) {
            server.execute(() -> save(player));
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastDisk >= DISK_GAP_MS) {
            lastDisk = now;
            try {
                ItWasTallState.getOrCreate(server).markDirty();
                server.getOverworld().getPersistentStateManager().save();
                player.getAdvancementTracker().save();
            } catch (RuntimeException e) {
                ItWasTall.LOGGER.warn("Checkpoint save failed: {}", e.toString());
            }
        }
        icon(player);
    }

    /** Only the quill (the world was saved some other way, e.g. the autosave). */
    public static void icon(ServerPlayerEntity player) {
        long now = System.currentTimeMillis();
        Long last = LAST_ICON.get(player.getUuid());
        if (last != null && now - last < ICON_GAP_MS) return;
        LAST_ICON.put(player.getUuid(), now);
        ModNetworking.sendSaveIcon(player);
    }

    public static void iconAll(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) icon(p);
    }
}
