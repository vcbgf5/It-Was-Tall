package com.itwastall.client.god;

import com.itwastall.client.JournalData;
import com.itwastall.client.hud.Vignette;
import com.itwastall.net.GodActionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * The GOD's panel (G): pick a player on the left, fire an event at them on the right. Three tabs: the events,
 * the scenes and tasks, and the world (stage, tension, time of day, weather). Every button is one command on
 * the server, run only for a god who is an operator.
 */
public class GodPanelScreen extends Screen {
    private static final String[][] EVENTS = {
            {"It: stare", "haunt"}, {"It: near", "spawn"}, {"Fake attack", "feint"}, {"Behind a tree", "tree"},
            {"It knocks", "realknock"}, {"Window knock", "knock"}, {"Door knock", "doorknock"}, {"Shadow at door", "shadow"},
            {"Knock inside", "knockinside"}, {"Door opens", "door"}, {"Chest moved", "chest"}, {"Sign outside", "sign"},
            {"False figure", "falsepresence"}, {"The Guest", "guest"}, {"The Count", "count"}, {"The Old One", "oldone"},
            {"Deep One", "deepone"}, {"The Hanged", "hanged"}, {"Mimics", "mimic"}, {"Eye swarm", "eyes"}, {"It whispers", "voice"}, {"Steal a voice", "mimicvoice"}, {"Record 5 s + play", "recordvoice"}, {"Corruption", "corruption"}, {"Small scare", "scare"}, {"JUMPSCARE", "jumpscare"}};
    private static final String[][] SCENES = {
            {"Chopping block", "scene_chopping"}, {"Empty camp", "scene_camp"}, {"Missing posters", "scene_missing"},
            {"Animal circle", "scene_circle"}, {"Footprints", "scene_footprints"}, {"Dream (outspace)", "outspace"}, {"The Below", "below"}, {"Cracked bedrock", "crackedbedrock"},
            {"Task: stay inside", "task_stayinside"}, {"Task: lights", "task_lights"}, {"Task: hide", "task_hide"},
            {"Task: don't look", "task_dontlook"}, {"Task: offering", "task_sacrifice"}};
    private static final String[][] WORLD = {
            {"Stage 0", "stage_0"}, {"Stage 1", "stage_1"}, {"Stage 2", "stage_2"}, {"Stage 3", "stage_3"},
            {"Stage 4", "stage_4"}, {"Stage 5", "stage_5"}, {"Tension -10", "tension_down"}, {"Tension +10", "tension_up"},
            {"Tension +25", "tension_upup"}, {"Day", "day"}, {"Night", "night"}, {"Midnight", "midnight"},
            {"Clear", "clear"}, {"Rain", "rain"}, {"Thunder", "thunder"}, {"BLOOD MOON", "bloodmoon"}, {"FOG NIGHT", "fog"}};
    private static final String[][] MAP_ACTIONS = {
            {"It digs to them", "dig"}, {"It peeks (cave)", "peek"}, {"It: stare", "haunt"},
            {"JUMPSCARE", "jumpscare"}, {"Fly to them", "tpto"}, {"Rise as It / Sink (H)", "itform"}};
    private static final String[] TAB_NAMES = {"EVENTS", "SCENES & TASKS", "WORLD", "MAP"};
    private static final int MAP = 3;
    private int mapX, mapY, mapSize, ticks;

    private static int tab;
    private static String target = "";

    public GodPanelScreen() {
        super(Text.literal("God Panel"));
    }

    @Override
    protected void init() {
        // tabs
        int tw = Math.min(100, (width - 28) / 4);
        for (int i = 0; i < TAB_NAMES.length; i++) {
            int t = i;
            addDrawableChild(ButtonWidget.builder(Text.literal((tab == i ? "> " : "") + TAB_NAMES[i] + (tab == i ? " <" : "")), b -> {
                tab = t;
                clearAndInit();
                if (t == MAP) send("map");
            }).dimensions(width / 2 - (tw * 4 + 12) / 2 + i * (tw + 4), 22, tw, 18).build());
        }
        if (tab == MAP) {
            initMap();
            return;
        }
        int top = 46;
        // players (not for the world tab)
        int left = 8;
        int listW = tab == 2 ? 0 : Math.min(110, width / 4);
        if (tab != 2) {
            List<String> names = players();
            if (!names.contains(target)) target = names.isEmpty() ? "" : names.get(0);
            for (int i = 0; i < names.size() && top + i * 20 < height - 30; i++) {
                String n = names.get(i);
                addDrawableChild(ButtonWidget.builder(Text.literal(n.equals(target) ? "> " + n + " <" : n), b -> {
                    target = n;
                    clearAndInit();
                }).dimensions(left, top + i * 20, listW, 18).build());
            }
        }
        // actions
        String[][] actions = tab == 0 ? EVENTS : tab == 1 ? SCENES : WORLD;
        int x0 = left + listW + (listW > 0 ? 8 : 0);
        int avail = width - x0 - 8;
        int cols = Math.max(2, Math.min(5, avail / 92));
        int bw = (avail - (cols - 1) * 4) / cols;
        for (int i = 0; i < actions.length; i++) {
            String action = actions[i][1];
            int c = i % cols, r = i / cols;
            addDrawableChild(ButtonWidget.builder(Text.literal(actions[i][0]), b -> send(action))
                    .dimensions(x0 + c * (bw + 4), top + r * 20, bw, 18).build());
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> close())
                .dimensions(width - 68, height - 24, 60, 18).build());
    }

    /** MAP tab: players with where they are, the mini-map around the chosen one, actions on them. */
    private void initMap() {
        int top = 46, left = 8;
        int listW = Math.min(110, width / 4);
        List<String> names = players();
        if (!names.contains(target)) target = names.isEmpty() ? "" : names.get(0);
        for (int i = 0; i < names.size() && top + i * 30 < height - 40; i++) {
            String n = names.get(i);
            addDrawableChild(ButtonWidget.builder(Text.literal(n.equals(target) ? "> " + n + " <" : n), b -> {
                target = n;
                clearAndInit();
                send("map");
            }).dimensions(left, top + i * 30, listW, 18).build());
        }
        int actW = Math.min(120, Math.max(90, width / 5));
        mapSize = Math.max(64, Math.min(height - 80, width - listW - actW - 40));
        mapSize = mapSize / 64 * 64;
        mapX = left + listW + 12;
        mapY = top + 12;
        int ax = mapX + mapSize + 12;
        for (int i = 0; i < MAP_ACTIONS.length; i++) {
            String action = MAP_ACTIONS[i][1];
            addDrawableChild(ButtonWidget.builder(Text.literal(MAP_ACTIONS[i][0]), b -> send(action))
                    .dimensions(ax, top + i * 22, actW, 18).build());
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> close())
                .dimensions(width - 68, height - 24, 60, 18).build());
    }

    @Override
    public void tick() {
        super.tick();
        if (tab == MAP && ++ticks % 20 == 0 && !target.isEmpty()) send("map");
    }

    private void renderMap(DrawContext ctx) {
        // status under each player's name
        List<String> names = players();
        for (int i = 0; i < names.size() && 46 + i * 30 < height - 40; i++) {
            String st = GodMapData.STATUS.getOrDefault(names.get(i), "...");
            ctx.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(st, Math.min(110, width / 4)), 10, 46 + i * 30 + 20, 0xFF9C8E78);
        }
        // frame
        ctx.fill(mapX - 3, mapY - 3, mapX + mapSize + 3, mapY + mapSize + 3, 0xFF0E0806);
        ctx.fill(mapX - 2, mapY - 2, mapX + mapSize + 2, mapY + mapSize + 2, 0xFF5A1610);
        ctx.fill(mapX, mapY, mapX + mapSize, mapY + mapSize, 0xFF050505);
        var map = GodMapData.map;
        if (map == null || !map.target().equals(target)) {
            ctx.drawCenteredTextWithShadow(textRenderer, target.isEmpty() ? "No other players." : "Loading map...",
                    mapX + mapSize / 2, mapY + mapSize / 2 - 4, 0xFF9C8E78);
            return;
        }
        int n = com.itwastall.net.GodMapPayload.SIZE, s = mapSize / n;
        byte[] c = map.colors();
        for (int z = 0; z < n; z++) {
            for (int x = 0; x < n; x++) {
                int argb = GodMapData.color(c[z * n + x]);
                ctx.fill(mapX + x * s, mapY + z * s, mapX + x * s + s, mapY + z * s + s, argb);
            }
        }
        ctx.drawCenteredTextWithShadow(textRenderer, "N", mapX + mapSize / 2, mapY - 12, 0xFFE8D9B8);
        String head = map.target() + "  -  " + GodMapData.STATUS.getOrDefault(map.target(), "")
                + (map.cave() ? "   (cave slice at Y " + map.center().getY() + ")" : "");
        ctx.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(head, width - mapX - 8), mapX, mapY + mapSize + 6, 0xFFE8D9B8);
        // markers: It first, then players, the chosen one last (on top)
        for (int pass = 0; pass < 3; pass++) {
            for (var m : map.markers()) {
                int kind = m.kind();
                if (kind == 4) continue;
                if ((pass == 0) != (kind == 3) || (pass == 2) != (kind == 1)) continue;
                int px = mapX + (m.x() - map.center().getX() + n / 2) * s + s / 2;
                int pz = mapY + (m.z() - map.center().getZ() + n / 2) * s + s / 2;
                if (px < mapX || pz < mapY || px >= mapX + mapSize || pz >= mapY + mapSize) continue;
                switch (kind) {
                    case 3 -> {
                        ctx.fill(px - 3, pz - 1, px + 4, pz + 2, 0xFFA0241B);
                        ctx.fill(px - 1, pz - 3, px + 2, pz + 4, 0xFFA0241B);
                        ctx.fill(px - 1, pz - 1, px + 2, pz + 2, 0xFF000000);
                    }
                    case 1 -> {
                        ctx.fill(px - 3, pz - 3, px + 4, pz + 4, 0xFF000000);
                        ctx.fill(px - 2, pz - 2, px + 3, pz + 3, 0xFFE0281E);
                        ctx.drawTextWithShadow(textRenderer, m.name(), px + 5, pz - 4, 0xFFFF6A5A);
                    }
                    case 2 -> ctx.fill(px - 2, pz - 2, px + 3, pz + 3, 0xFF8A8A8A);
                    default -> {
                        ctx.fill(px - 2, pz - 2, px + 3, pz + 3, 0xFFF0F0F0);
                        ctx.drawTextWithShadow(textRenderer, m.name(), px + 4, pz - 4, 0xFFE8E8E8);
                    }
                }
            }
        }
    }

    private void send(String action) {
        if (ClientPlayNetworking.canSend(GodActionPayload.ID)) ClientPlayNetworking.send(new GodActionPayload(action, target));
    }

    private List<String> players() {
        List<String> out = new ArrayList<>();
        if (client == null || client.getNetworkHandler() == null || client.player == null) return out;
        String me = client.player.getGameProfile().getName();
        for (PlayerListEntry e : client.getNetworkHandler().getPlayerList()) {
            String n = e.getProfile().getName();
            if (!n.equals(me)) out.add(n);
        }
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xB0080404);
        Vignette.draw(ctx, width, height, 0.8f);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        if (tab == MAP) renderMap(ctx);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("GOD PANEL").formatted(net.minecraft.util.Formatting.DARK_RED), width / 2, 8, 0xFFFFFFFF);
        if (tab == MAP) return;
        String status = (tab == 2 ? "" : "Target: " + (target.isEmpty() ? "nobody online" : target) + "   ")
                + "Stage " + JournalData.stage() + "   Tension " + Math.round(JournalData.tension()) + " / 100";
        ctx.drawTextWithShadow(textRenderer, status, 8, height - 18, 0xFF9C8E78);
        if (tab != 2 && players().isEmpty()) {
            ctx.drawTextWithShadow(textRenderer, "No other players.", 8, 50, 0xFF9C8E78);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
