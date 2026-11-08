package com.itwastall.god;

import com.itwastall.ItWasTall;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * What the god's panel can do. Every action is one of the mod's own commands (or /time, /weather), run as the
 * god with operator rights — the panel never does anything a command could not. Only a god who is also an
 * operator is listened to; the target must be a player who is online right now.
 */
public final class GodActions {
    /** Actions aimed at a player: %p is that player's name. */
    private static final Map<String, String> ON_PLAYER = Map.ofEntries(
            Map.entry("haunt", "itwastall haunt %p"),
            Map.entry("dig", "itwastall mine dig %p"),
            Map.entry("peek", "itwastall mine peek %p"),
            Map.entry("tpto", "tp %p"),
            Map.entry("spawn", "itwastall spawn 30 %p"),
            Map.entry("feint", "itwastall event feint %p"),
            Map.entry("tree", "itwastall event tree %p"),
            Map.entry("realknock", "itwastall event realknock %p"),
            Map.entry("knock", "itwastall event knock %p"),
            Map.entry("doorknock", "itwastall event doorknock %p"),
            Map.entry("shadow", "itwastall event shadow %p"),
            Map.entry("knockinside", "itwastall event knockinside %p"),
            Map.entry("door", "itwastall event door %p"),
            Map.entry("chest", "itwastall event chest %p"),
            Map.entry("sign", "itwastall event sign %p"),
            Map.entry("falsepresence", "itwastall event falsepresence %p"),
            Map.entry("guest", "itwastall event guest %p"),
            Map.entry("count", "itwastall event count %p"),
            Map.entry("oldone", "itwastall event oldone %p"),
            Map.entry("deepone", "itwastall event deepone %p"),
            Map.entry("hanged", "itwastall event hanged %p"),
            Map.entry("mimic", "itwastall event mimic %p"),
            Map.entry("eyes", "itwastall event eyes %p"),
            Map.entry("voice", "itwastall event voice %p"),
            Map.entry("mimicvoice", "itwastall event mimicvoice %p"),
            Map.entry("recordvoice", "itwastall debug recordvoice %p"),
            Map.entry("corruption", "itwastall event corruption %p"),
            Map.entry("jumpscare", "itwastall jumpscare %p"),
            Map.entry("scare", "itwastall scare %p"),
            Map.entry("outspace", "itwastall outspace %p"),
            Map.entry("below", "itwastall event below %p"),
            Map.entry("fog", "itwastall event fog %p"),
            Map.entry("ritual", "itwastall event ritual %p"),
            Map.entry("ending", "itwastall event ending %p"),
            Map.entry("crackedbedrock", "itwastall event crackedbedrock %p"),
            Map.entry("scene_chopping", "itwastall event scene chopping %p"),
            Map.entry("scene_camp", "itwastall event scene camp %p"),
            Map.entry("scene_missing", "itwastall event scene missing %p"),
            Map.entry("scene_circle", "itwastall event scene circle %p"),
            Map.entry("scene_footprints", "itwastall event scene footprints %p"),
            Map.entry("task_stayinside", "itwastall task stayinside %p"),
            Map.entry("task_lights", "itwastall task lights %p"),
            Map.entry("task_hide", "itwastall task hide %p"),
            Map.entry("task_dontlook", "itwastall task dontlook %p"),
            Map.entry("task_sacrifice", "itwastall task sacrifice %p"));

    /** Actions on the whole world. */
    private static final Map<String, String> WORLD = Map.ofEntries(
            Map.entry("stage_0", "itwastall stage 0"), Map.entry("stage_1", "itwastall stage 1"),
            Map.entry("stage_2", "itwastall stage 2"), Map.entry("stage_3", "itwastall stage 3"),
            Map.entry("stage_4", "itwastall stage 4"), Map.entry("stage_5", "itwastall stage 5"),
            Map.entry("tension_down", "itwastall tension -10"), Map.entry("tension_up", "itwastall tension +10"),
            Map.entry("tension_upup", "itwastall tension +25"),
            Map.entry("day", "time set day"), Map.entry("night", "time set night"), Map.entry("midnight", "time set midnight"),
            Map.entry("bloodmoon", "itwastall event bloodmoon"),
            Map.entry("clear", "weather clear"), Map.entry("rain", "weather rain"), Map.entry("thunder", "weather thunder"));

    private GodActions() {}

    public static void run(@Nullable ServerPlayerEntity god, String action, String target) {
        if (god == null || god.getServer() == null) return;
        MinecraftServer server = god.getServer();
        if (!GodMode.isGod(god.getUuid()) || !god.hasPermissionLevel(2)) return;
        if (action.equals("map")) {
            GodMap.send(god, target);
            return;
        }
        if (action.equals("itform")) {
            ItForm.toggle(god);
            return;
        }
        if (action.equals("lock")) {
            ItForm.lock(god, target);
            return;
        }
        String command = WORLD.get(action);
        if (command == null) {
            String template = ON_PLAYER.get(action);
            if (template == null) return;
            ServerPlayerEntity victim = server.getPlayerManager().getPlayer(target);
            if (victim == null) {
                god.sendMessage(Text.literal("Pick a player first (they must be online).").formatted(Formatting.GRAY), true);
                return;
            }
            command = template.replace("%p", victim.getGameProfile().getName());
        }
        ItWasTall.LOGGER.info("God {}: /{}", god.getName().getString(), command);
        server.getCommandManager().executeWithPrefix(god.getCommandSource().withLevel(4), command);
    }
}
