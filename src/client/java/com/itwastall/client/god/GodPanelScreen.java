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
            {"Deep One", "deepone"}, {"The Hanged", "hanged"}, {"Small scare", "scare"}, {"JUMPSCARE", "jumpscare"}};
    private static final String[][] SCENES = {
            {"Chopping block", "scene_chopping"}, {"Empty camp", "scene_camp"}, {"Missing posters", "scene_missing"},
            {"Animal circle", "scene_circle"}, {"Footprints", "scene_footprints"}, {"Dream (outspace)", "outspace"},
            {"Task: stay inside", "task_stayinside"}, {"Task: lights", "task_lights"}, {"Task: hide", "task_hide"},
            {"Task: don't look", "task_dontlook"}, {"Task: offering", "task_sacrifice"}};
    private static final String[][] WORLD = {
            {"Stage 0", "stage_0"}, {"Stage 1", "stage_1"}, {"Stage 2", "stage_2"}, {"Stage 3", "stage_3"},
            {"Stage 4", "stage_4"}, {"Stage 5", "stage_5"}, {"Tension -10", "tension_down"}, {"Tension +10", "tension_up"},
            {"Tension +25", "tension_upup"}, {"Day", "day"}, {"Night", "night"}, {"Midnight", "midnight"},
            {"Clear", "clear"}, {"Rain", "rain"}, {"Thunder", "thunder"}};
    private static final String[] TAB_NAMES = {"EVENTS", "SCENES & TASKS", "WORLD"};

    private static int tab;
    private static String target = "";

    public GodPanelScreen() {
        super(Text.literal("God Panel"));
    }

    @Override
    protected void init() {
        // tabs
        int tw = Math.min(110, (width - 24) / 3);
        for (int i = 0; i < TAB_NAMES.length; i++) {
            int t = i;
            addDrawableChild(ButtonWidget.builder(Text.literal((tab == i ? "> " : "") + TAB_NAMES[i] + (tab == i ? " <" : "")), b -> {
                tab = t;
                clearAndInit();
            }).dimensions(width / 2 - (tw * 3 + 8) / 2 + i * (tw + 4), 22, tw, 18).build());
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
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("GOD PANEL").formatted(net.minecraft.util.Formatting.DARK_RED), width / 2, 8, 0xFFFFFFFF);
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
