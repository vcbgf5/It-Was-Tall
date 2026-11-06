package com.itwastall.entity;

import com.itwastall.ItWasTall;
import com.itwastall.base.BaseMap;
import com.itwastall.base.BaseTracker;
import com.itwastall.config.ItWasTallConfig;
import com.itwastall.director.Director;
import com.itwastall.net.GuestFigurePayload;
import com.itwastall.registry.ModItems;
import com.itwastall.world.ItWasTallState;
import com.itwastall.world.JournalLog;
import com.mojang.authlib.properties.PropertyMap;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * THE GUEST. A name you do not know joins the player list — only on YOUR screen ("guest", or someone who
 * left long ago). It writes in the chat what you wrote earlier. Then you see it: a player far away, its
 * back to you, never turning round. Walk up to it and it is gone ("left the game") — and in your chest
 * there is a note you did not write. The book A GUEST (guest_house structure) describes it.
 */
public final class TheGuest {
    private enum Step {JOINED, ECHOED, FIGURE}

    private static final class Visit {
        UUID profile;
        String name;
        Step step = Step.JOINED;
        long nextAt;
        Vec3d figure;
        long figureUntil;
    }

    private static final Map<UUID, Visit> VISITS = new HashMap<>();
    private static final Map<UUID, Long> NEXT = new HashMap<>();
    /** What each player wrote in the chat (the guest repeats it later). */
    private static final Map<UUID, Deque<String[]>> SAID = new HashMap<>();
    private static final String[] FALLBACK = {"hello?", "who left the door open", "i can see your house from here",
            "why are you running", "wait for me", "this is my world too", "you forgot something"};

    private TheGuest() {
    }

    public static void init() {
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            Deque<String[]> said = SAID.computeIfAbsent(sender.getUuid(), u -> new ArrayDeque<>());
            said.addLast(new String[]{message.getContent().getString(), Long.toString(System.currentTimeMillis())});
            while (said.size() > 20) said.removeFirst();
        });
        ServerTickEvents.END_SERVER_TICK.register(TheGuest::tick);
    }

    public static void forget(UUID player) {
        VISITS.remove(player);
        NEXT.remove(player);
        SAID.remove(player);
    }

    private static void tick(MinecraftServer server) {
        if (server.getTicks() % 20 != 0) return;
        long now = server.getOverworld().getTime();
        for (UUID uuid : new ArrayList<>(VISITS.keySet())) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(uuid);
            if (p == null) {
                VISITS.remove(uuid);
                continue;
            }
            advance(p, VISITS.get(uuid), now);
        }
        if (server.getTicks() % 600 != 0) return;                        // a new guest: checks every 30 s
        ItWasTallConfig.Values cfg = ItWasTallConfig.INSTANCE;
        if (!cfg.guest || Director.phase() != 1 || Director.stageIndex() < cfg.guestMinStage) return;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (VISITS.containsKey(p.getUuid()) || p.isSpectator()) continue;
            Long next = NEXT.get(p.getUuid());
            if (next != null && now < next) continue;
            if (p.getWorld().getRegistryKey() != World.OVERWORLD || !EventGate.ready(p)) continue;
            if (p.getRandom().nextDouble() >= MathHelper.clamp(cfg.guestChance, 0.0, 1.0)) continue;
            start(p);
        }
    }

    /** Ends a running visit at once (test command restarts it). */
    public static void stop(ServerPlayerEntity p) {
        Visit v = VISITS.get(p.getUuid());
        if (v != null) leave(p, v);
    }

    /** Begins a visit (also /itwastall event guest). */
    public static boolean start(ServerPlayerEntity p) {
        if (VISITS.containsKey(p.getUuid())) return false;
        MinecraftServer server = p.getServer();
        if (server == null) return false;
        Visit v = new Visit();
        v.name = pickName(p);
        v.profile = UUID.nameUUIDFromBytes(("itwastall-guest:" + v.name + ":" + p.getUuid()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        long now = server.getOverworld().getTime();
        v.nextAt = now + 20L * 20;
        VISITS.put(p.getUuid(), v);
        NEXT.put(p.getUuid(), now + 20L * 60 * (25 + p.getRandom().nextInt(21)));
        EventGate.mark(p);
        addToList(p, v);
        p.sendMessage(Text.translatable("multiplayer.player.joined", v.name).formatted(Formatting.YELLOW), false);
        ItWasTall.LOGGER.info("The Guest ({}) visits {}.", v.name, p.getName().getString());
        return true;
    }

    private static void advance(ServerPlayerEntity p, Visit v, long now) {
        switch (v.step) {
            case JOINED -> {
                if (now < v.nextAt) return;
                p.sendMessage(Text.translatable("chat.type.text", Text.literal(v.name), Text.literal(echo(p))), false);
                v.step = Step.ECHOED;
                v.nextAt = now + 20L * 20;
            }
            case ECHOED -> {
                if (now < v.nextAt) return;
                BlockPos spot = FalsePresences.findSpot(p, p.getRandom(), 34, 54);
                if (spot == null) {
                    v.nextAt = now + 20L * 10;           // nowhere to stand yet: try again soon
                    return;
                }
                v.figure = Vec3d.ofBottomCenter(spot);
                // its back to you: it looks the same way you would look at it
                float yaw = (float) (MathHelper.atan2(v.figure.z - p.getZ(), v.figure.x - p.getX()) * MathHelper.DEGREES_PER_RADIAN) - 90f;
                int duration = 20 * 180;
                v.figureUntil = now + duration;
                v.step = Step.FIGURE;
                send(p, new GuestFigurePayload(v.profile, v.name, v.figure.x, v.figure.y, v.figure.z, yaw, duration, false));
            }
            case FIGURE -> {
                double dx = p.getX() - v.figure.x, dz = p.getZ() - v.figure.z;
                boolean reached = dx * dx + dz * dz < 6 * 6;
                if (!reached && now < v.figureUntil) return;
                leave(p, v);
                if (reached) {
                    leaveNote(p, v);
                    com.itwastall.advancement.Achievements.grant(p, "guest");
                    JournalLog.add(p, "A player called " + v.name + " stood with its back to me. When I got close, it left the game.");
                }
            }
        }
    }

    private static void leave(ServerPlayerEntity p, Visit v) {
        VISITS.remove(p.getUuid());
        send(p, new GuestFigurePayload(v.profile, v.name, 0, 0, 0, 0, 0, true));
        p.networkHandler.sendPacket(new PlayerRemoveS2CPacket(List.of(v.profile)));
        p.sendMessage(Text.translatable("multiplayer.player.left", v.name).formatted(Formatting.YELLOW), false);
    }

    /** A note in one of the chests of the place you live (or in your pocket if there is none). */
    private static void leaveNote(ServerPlayerEntity p, Visit v) {
        ItemStack note = ModItems.note("guest_note_" + Math.abs(p.getRandom().nextInt()), "A NOTE",
                "you came so close.|next time i will turn around.|- " + v.name);
        BaseMap map = BaseTracker.eventMapOf(p);
        if (map != null) {
            for (BlockPos c : map.all(BaseMap.TYPE_CHEST)) {
                if (!p.getServerWorld().isChunkLoaded(c) || !(p.getServerWorld().getBlockEntity(c) instanceof LootableContainerBlockEntity inv)) continue;
                if (inv.getLootTable() != null) continue;
                for (int slot = 0; slot < inv.size(); slot++) {
                    if (inv.getStack(slot).isEmpty()) {
                        inv.setStack(slot, note);
                        inv.markDirty();
                        return;
                    }
                }
            }
        }
        p.getInventory().offerOrDrop(note);
    }

    private static void send(ServerPlayerEntity p, GuestFigurePayload payload) {
        if (ServerPlayNetworking.canSend(p, GuestFigurePayload.ID)) ServerPlayNetworking.send(p, payload);
    }

    /**
     * The fake list entry goes only to this one player. The vanilla packet can only be built from a real
     * connected player, so this one is written byte by byte in the packet's own format and decoded.
     */
    private static void addToList(ServerPlayerEntity p, Visit v) {
        MinecraftServer server = p.getServer();
        if (server == null) return;
        EnumSet<PlayerListS2CPacket.Action> actions = EnumSet.of(PlayerListS2CPacket.Action.ADD_PLAYER,
                PlayerListS2CPacket.Action.UPDATE_GAME_MODE, PlayerListS2CPacket.Action.UPDATE_LISTED,
                PlayerListS2CPacket.Action.UPDATE_LATENCY);
        RegistryByteBuf buf = new RegistryByteBuf(Unpooled.buffer(), server.getRegistryManager());
        try {
            buf.writeEnumSet(actions, PlayerListS2CPacket.Action.class);
            buf.writeVarInt(1);
            buf.writeUuid(v.profile);
            // EnumSet iterates in declaration order: ADD_PLAYER, UPDATE_GAME_MODE, UPDATE_LISTED, UPDATE_LATENCY
            buf.writeString(v.name, 16);
            PacketCodecs.PROPERTY_MAP.encode(buf, new PropertyMap());
            buf.writeVarInt(0);                    // survival
            buf.writeBoolean(true);                // listed
            buf.writeVarInt(40 + p.getRandom().nextInt(80));
            p.networkHandler.sendPacket(PlayerListS2CPacket.CODEC.decode(buf));
        } catch (RuntimeException e) {
            ItWasTall.LOGGER.warn("Could not add the guest to the player list: {}", e.toString());
        } finally {
            buf.release();
        }
    }

    /** Something this player wrote a while ago, or one of a few lines when they never wrote anything. */
    private static String echo(ServerPlayerEntity p) {
        Deque<String[]> said = SAID.get(p.getUuid());
        if (said != null) {
            List<String> old = new ArrayList<>();
            long cutoff = System.currentTimeMillis() - 60_000;
            for (String[] s : said) if (Long.parseLong(s[1]) < cutoff) old.add(s[0]);
            if (!old.isEmpty()) return old.get(p.getRandom().nextInt(old.size()));
        }
        return FALLBACK[p.getRandom().nextInt(FALLBACK.length)];
    }

    /** "guest" — or someone who wrote in this world's journal and is not online now. */
    private static String pickName(ServerPlayerEntity p) {
        MinecraftServer server = p.getServer();
        Set<String> online = new HashSet<>();
        for (ServerPlayerEntity o : server.getPlayerManager().getPlayerList()) online.add(o.getName().getString());
        List<String> gone = new ArrayList<>();
        ItWasTallState state = ItWasTallState.getOrCreate(server);
        for (NbtCompound n : state.sharedNotes()) gone.add(n.getString("author"));
        for (NbtCompound b : state.sharedBooks()) gone.add(b.getString("foundBy"));
        gone.removeIf(n -> n.isBlank() || online.contains(n) || n.length() > 16);
        if (!gone.isEmpty() && p.getRandom().nextBoolean()) return gone.get(p.getRandom().nextInt(gone.size()));
        return "guest";
    }

    public static String debugLine(UUID player, long now) {
        Visit v = VISITS.get(player);
        if (v != null) return v.name + " is visiting (" + v.step.name().toLowerCase() + ")";
        Long next = NEXT.get(player);
        return next == null || now >= next ? "can visit" : "next in " + (next - now) / 20 + " s";
    }
}
