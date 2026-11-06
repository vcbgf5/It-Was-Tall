package com.itwastall.net;

import com.itwastall.ItWasTall;
import com.itwastall.config.ItWasTallConfig;
import com.itwastall.director.Director;
import com.itwastall.world.ItWasTallState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class ModNetworking {
    private ModNetworking() {
    }

    /** Per-player opt-out from the warning screen ("Jumpscares & scares" toggle, server-held). */
    private static final Set<UUID> SCARES_OFF = new HashSet<>();

    public static void init() {
        PayloadTypeRegistry.playS2C().register(JumpscarePayload.ID, JumpscarePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FearStatePayload.ID, FearStatePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ScareTextPayload.ID, ScareTextPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ItJoinedPayload.ID, ItJoinedPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FalsePresencePayload.ID, FalsePresencePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BaseDebugPayload.ID, BaseDebugPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TaskToastPayload.ID, TaskToastPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(JournalPayload.ID, JournalPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ChasePayload.ID, ChasePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SharedJournalPayload.ID, SharedJournalPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(GuestFigurePayload.ID, GuestFigurePayload.CODEC);
        // C2S: the shared NOTES tab (validated and rate-limited in SharedJournal)
        PayloadTypeRegistry.playC2S().register(NoteActionPayload.ID, NoteActionPayload.CODEC);
        // C2S: the client's warning-screen toggle must reach the server or the opt-out is a lie
        PayloadTypeRegistry.playC2S().register(ScaresPrefPayload.ID, ScaresPrefPayload.CODEC);
        // C2S: a deliberate look at a window figure earns a random task (MODPLAN §12 figure look)
        PayloadTypeRegistry.playC2S().register(WindowSeenPayload.ID, WindowSeenPayload.CODEC);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 20 != 0) return;
            ItWasTallState state = ItWasTallState.getOrCreate(server);
            for (ServerPlayerEntity player : PlayerLookup.all(server)) {
                if (!ServerPlayNetworking.canSend(player, FearStatePayload.ID)) continue;
                ServerPlayNetworking.send(player, new FearStatePayload(
                        Director.phase(), Director.stageIndex(), (float) Director.tension(),
                        com.itwastall.task.TaskManager.journalLine(player.getUuid(), server.getOverworld().getTime()),
                        java.util.List.copyOf(state.loreBookIds(player.getUuid()))));
                // red chase overlay: on/off every second (the start is also sent instantly)
                sendChase(player, isChased(player));
                // journal BASE / EVENTS pages: every 5 s is plenty (they change rarely)
                if (server.getTicks() % 100 == 0) sendJournal(player);
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(NoteActionPayload.ID, (payload, context) ->
                com.itwastall.world.SharedJournal.handle(context.player(), payload));
        // multiplayer (also LAN / Essential-hosted worlds): a player who joins gets the whole state at once,
        // not only after the next pulse — and once more two seconds later in case the client was still loading
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            UUID uuid = handler.getPlayer().getUuid();
            server.execute(() -> syncAll(server, uuid));
            SYNC_LATER.put(uuid, server.getTicks() + 40);
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (SYNC_LATER.isEmpty()) return;
            int now = server.getTicks();
            SYNC_LATER.entrySet().removeIf(e -> {
                if (now < e.getValue()) return false;
                syncAll(server, e.getKey());
                return true;
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(WindowSeenPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            if (player == null) return;
            if (payload.behind()) {
                // NIE PATRZ (§15.4): the player turned around at the watcher — task fails
                ItWasTall.LOGGER.debug("{} turned around. It saw that.", player.getName().getString());
                com.itwastall.task.TaskManager.onDontLookCaught(player);
            } else {
                ItWasTall.LOGGER.debug("{} met its gaze through the window.", player.getName().getString());
                com.itwastall.task.TaskManager.onWindowLooked(player);
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(ScaresPrefPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            if (player == null) return;
            if (payload.scaresEnabled()) SCARES_OFF.remove(player.getUuid());
            else SCARES_OFF.add(player.getUuid());
            ItWasTall.LOGGER.info("{} {} scares (client toggle).",
                    player.getName().getString(), payload.scaresEnabled() ? "re-enabled" : "disabled");
        });
    }

    /** True while a real Tall One is in CHASE mode with this player as its victim. */
    public static boolean isChased(ServerPlayerEntity player) {
        return !player.getServerWorld().getEntitiesByType(com.itwastall.registry.ModEntities.TALL_ONE,
                player.getBoundingBox().expand(96),
                it -> (it.getMode() == com.itwastall.entity.TallOneEntity.Mode.CHASE
                        || it.getMode() == com.itwastall.entity.TallOneEntity.Mode.ATTACK
                        || it.getMode() == com.itwastall.entity.TallOneEntity.Mode.FEINT)
                        && player.getUuid().equals(it.getVictimUuid())).isEmpty();
    }

    private static final java.util.Map<UUID, Integer> SYNC_LATER = new java.util.HashMap<>();

    /** Everything a client shows: fear state, its journal pages and the shared journal. */
    private static void syncAll(MinecraftServer server, UUID uuid) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
        if (player == null) return;
        if (ServerPlayNetworking.canSend(player, FearStatePayload.ID)) {
            ItWasTallState state = ItWasTallState.getOrCreate(server);
            ServerPlayNetworking.send(player, new FearStatePayload(Director.phase(), Director.stageIndex(), (float) Director.tension(),
                    com.itwastall.task.TaskManager.journalLine(uuid, server.getOverworld().getTime()), java.util.List.copyOf(state.loreBookIds(uuid))));
        }
        sendJournal(player);
        com.itwastall.world.SharedJournal.sync(player);
    }

    /** The player's own journal pages: BASE, EVENTS and the task history. */
    public static void sendJournal(ServerPlayerEntity player) {
        if (!ServerPlayNetworking.canSend(player, JournalPayload.ID) || player.getServer() == null) return;
        ItWasTallState state = ItWasTallState.getOrCreate(player.getServer());
        java.util.List<String> tasks = new java.util.ArrayList<>(state.taskLog(player.getUuid()));
        if (tasks.size() > 16) tasks = tasks.subList(0, 16);
        ServerPlayNetworking.send(player, new JournalPayload(baseCounts(player),
                com.itwastall.world.JournalLog.entries(player.getUuid()), tasks));
    }

    /** Red chase overlay on/off for this player. Respects the warning-screen scare opt-out. */
    public static void sendChase(ServerPlayerEntity player, boolean active) {
        if (active && scaresDisabled(player.getUuid())) active = false;
        if (ServerPlayNetworking.canSend(player, ChasePayload.ID)) {
            ServerPlayNetworking.send(player, new ChasePayload(active));
        }
    }

    /** Compact BaseMap summary for the journal (see {@link JournalPayload#baseCounts()}). */
    private static java.util.List<Integer> baseCounts(ServerPlayerEntity player) {
        if (com.itwastall.base.BaseTracker.anchorOf(player.getUuid()) == null) return java.util.List.of();
        var map = com.itwastall.base.BaseTracker.mapOf(player.getUuid());
        if (map == null) return java.util.List.of();
        net.minecraft.util.math.BlockPos a = net.minecraft.util.math.BlockPos.fromLong(map.anchor());
        return java.util.List.of(a.getX(), a.getY(), a.getZ(),
                map.count(com.itwastall.base.BaseMap.TYPE_WINDOW),
                map.count(com.itwastall.base.BaseMap.TYPE_DOOR),
                map.count(com.itwastall.base.BaseMap.TYPE_LIGHT),
                map.count(com.itwastall.base.BaseMap.TYPE_CHEST),
                map.count(com.itwastall.base.BaseMap.TYPE_CORNER),
                map.count(com.itwastall.base.BaseMap.TYPE_PLACED));
    }

    /** True when that player asked the server to hold the jumpscare / red-text scares back. */
    public static boolean scaresDisabled(UUID player) {
        return SCARES_OFF.contains(player);
    }

    /** Broadcast of the one-shot "IT JOINED" sequence to every player currently on the server. */
    public static void broadcastItJoined(MinecraftServer server) {
        for (ServerPlayerEntity player : PlayerLookup.all(server)) {
            if (ServerPlayNetworking.canSend(player, ItJoinedPayload.ID)) {
                ServerPlayNetworking.send(player, new ItJoinedPayload(4000));
                com.itwastall.world.JournalLog.add(player, "Something joined this world.");
            }
        }
    }

    public static void sendScareText(ServerPlayerEntity player, String text) {
        ItWasTallConfig.Values cfg = ItWasTallConfig.INSTANCE;
        if (scaresDisabled(player.getUuid())) return; // the warning screen's toggle is absolute
        if (cfg.scareMessages && ServerPlayNetworking.canSend(player, ScareTextPayload.ID)) {
            ServerPlayNetworking.send(player, new ScareTextPayload(text, cfg.messageMillis));
            com.itwastall.world.JournalLog.add(player, "Words on the screen: \"" + text + "\"");
        }
    }

    public static void sendJumpscare(ServerPlayerEntity player) {
        ItWasTallConfig.Values cfg = ItWasTallConfig.INSTANCE;
        if (scaresDisabled(player.getUuid())) return; // the warning screen's toggle is absolute
        ItWasTall.LOGGER.debug("Jumpscare -> {}", player.getName().getString());
        if (ServerPlayNetworking.canSend(player, JumpscarePayload.ID)) {
            ServerPlayNetworking.send(player, new JumpscarePayload(cfg.jumpscareMillis, cfg.jumpscares));
            com.itwastall.world.JournalLog.add(player, "I saw its face. Only for a moment.");
        }
    }

    /**
     * Server -> client: the False Presence figure (MODPLAN §7.4 — client-side only, never a
     * real entity). Returns false when that client cannot receive the payload.
     */
    public static boolean sendFalsePresence(ServerPlayerEntity player, double x, double y, double z,
                                            float yaw, int durationTicks, boolean window) {
        return sendFalsePresence(player, x, y, z, yaw, durationTicks, window, false);
    }

    public static boolean sendFalsePresence(ServerPlayerEntity player, double x, double y, double z,
                                            float yaw, int durationTicks, boolean window, boolean dontlook) {
        if (!ServerPlayNetworking.canSend(player, FalsePresencePayload.ID)) return false;
        ServerPlayNetworking.send(player, new FalsePresencePayload(x, y, z, yaw, durationTicks, window, dontlook));
        com.itwastall.world.JournalLog.add(player, window ? "It knocked on my window. It was looking in."
                : dontlook ? "It stood right behind me. I didn't turn around."
                : "A tall shape in the distance. Then nothing.");
        ItWasTall.LOGGER.debug("False presence -> {}", player.getName().getString());
        return true;
    }

    /** Debug 3D (MODPLAN §8.5): the player's saved BaseMap as coloured world frames. */
    public static boolean sendBaseDebug(ServerPlayerEntity player) {
        return sendBaseDebug(player, false);
    }

    /** @param viewer true = open the 3D base viewer screen, false = 30 s of coloured frames in the world */
    public static boolean sendBaseDebug(ServerPlayerEntity player, boolean viewer) {
        if (!ServerPlayNetworking.canSend(player, BaseDebugPayload.ID)) return false;
        var map = com.itwastall.base.BaseTracker.mapOf(player.getUuid());
        if (map == null) return false;
        ServerPlayNetworking.send(player, new BaseDebugPayload(map.anchor(), map.encodedMarkers(),
                viewer ? BaseDebugPayload.OPEN_VIEWER : 30));
        ItWasTall.LOGGER.debug("Base debug view -> {}", player.getName().getString());
        return true;
    }

    /** TaskToast (MODPLAN §16): new / completed / failed, drawn by the client's TaskToast HUD. */
    public static void sendTaskToast(ServerPlayerEntity player, int kind, String title, String text) {
        if (!ServerPlayNetworking.canSend(player, TaskToastPayload.ID)) return;
        ServerPlayNetworking.send(player, new TaskToastPayload(kind, title, text));
        String verb = kind == TaskToastPayload.KIND_COMPLETED ? "Done: " : kind == TaskToastPayload.KIND_FAILED ? "Failed: " : "It asked: ";
        com.itwastall.world.JournalLog.add(player, verb + title);
    }
}
