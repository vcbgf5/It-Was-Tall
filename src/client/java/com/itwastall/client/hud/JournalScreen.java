package com.itwastall.client.hud;

import com.itwastall.client.JournalData;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** In-game journal opened with J. It only displays information currently available to the client. */
public final class JournalScreen extends Screen {
    private int tab;
    private ButtonWidget fearButton;
    private static final String[] TABS = {"STATUS", "BOOKS", "BASE", "EVENTS"};

    /** Mirror of the server's LoreBooks.BOOKS order (§9.2) — one source of truth per side. */
    private static final String[] KNOWN_BOOK_IDS = {
            "01_someone_was_here", "02_the_window", "03_the_mine", "04_dont_open",
            "05_the_player_list", "06_it_joined", "07_observation", "08_the_chest",
            "09_coordinates", "10_the_last_entry", "11_the_list", "12_it_knows"
    };
    private static final String[] KNOWN_BOOKS = {
            "SOMEONE WAS HERE", "THE WINDOW", "THE MINE", "DON'T OPEN",
            "THE PLAYER LIST", "IT JOINED", "OBSERVATION", "THE CHEST",
            "COORDINATES", "THE LAST ENTRY", "THE LIST", "IT KNOWS"
    };

    public JournalScreen() {
        super(Text.literal("It Was Tall — Journal"));
    }

    @Override
    protected void init() {
        int totalWidth = 4 * 86 + 3 * 4;
        int left = (width - totalWidth) / 2;
        for (int i = 0; i < TABS.length; i++) {
            final int selected = i;
            addDrawableChild(ButtonWidget.builder(Text.literal(TABS[i]), b -> tab = selected)
                    .dimensions(left + i * 90, 42, 86, 20).build());
        }
        fearButton = ButtonWidget.builder(fearLabel(), b -> {
                    boolean next = !FearHud.isEnabled();
                    FearHud.setEnabled(next);
                    b.setMessage(fearLabel());
                })
                .dimensions(width / 2 - 85, 66, 170, 20).build();
        addDrawableChild(fearButton);
        addDrawableChild(ButtonWidget.builder(Text.literal("CLOSE"), b -> close())
                .dimensions(width / 2 - 45, height - 34, 90, 20).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx, mouseX, mouseY, delta);
        ctx.fill(width / 2 - 190, 18, width / 2 + 190, height - 20, 0xE8101118);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, 24, 0xFFE8E8E8);
        super.render(ctx, mouseX, mouseY, delta);

        List<String> lines = content();
        int y = 94;
        int maxWidth = 340;
        for (String line : lines) {
            for (OrderedText wrapped : textRenderer.wrapLines(Text.literal(line), maxWidth)) {
                if (y > height - 58) break;
                ctx.drawTextWithShadow(textRenderer, wrapped, width / 2 - 170, y, 0xFFD0D0D0);
                y += 13;
            }
            if (y > height - 58) break;
        }
    }

    private Text fearLabel() {
        return Text.literal("Fear vignette: " + (FearHud.isEnabled() ? "ON" : "OFF"));
    }

    private List<String> content() {
        MinecraftClient client = MinecraftClient.getInstance();
        List<String> lines = new ArrayList<>();
        switch (tab) {
            case 0 -> {
                lines.add("WORLD STATUS");
                lines.add("");
                if (client.player != null && client.world != null) {
                    lines.add("World: " + client.world.getRegistryKey().getValue());
                    lines.add("Position: " + client.player.getBlockPos().toShortString());
                    lines.add("Health: " + Math.round(client.player.getHealth()) + " / "
                            + Math.round(client.player.getMaxHealth()));
                    lines.add("Phase: " + (JournalData.phase() == 0 ? "PRE" : "ACTIVE"));
                    lines.add("Stage: " + JournalData.stage() + " / 5");
                    lines.add("Tension: " + Math.round(JournalData.tension()) + " / 100");
                    // the live task line — what It asked of you right now
                    lines.add("Task: " + com.itwastall.client.JournalData.task());
                    int crystals = 0;
                    for (int slot = 0; slot < client.player.getInventory().size(); slot++) {
                        var stack = client.player.getInventory().getStack(slot);
                        if (stack.getItem().toString().contains("warding_crystal")) crystals += stack.getCount();
                    }
                    lines.add("Warding crystals: " + (crystals == 0 ? "none (find the blue glow)" : crystals));
                } else lines.add("No world is currently loaded.");
                lines.add("");
                lines.add("The journal updates every second from the server.");
            }
            case 1 -> {
                lines.add("LORE — BOOKS " + JournalData.loreIds().size() + " / 12");
                lines.add("");
                if (com.itwastall.client.JournalData.loreIds().isEmpty()) {
                    lines.add("Nothing yet. Books appear by themselves");
                    lines.add("in the chests of your base.");
                    lines.add("?");
                    lines.add("");
                    lines.add("(check chests after a scan runs)");
                } else {
                    for (int i = 0; i < KNOWN_BOOKS.length; i++) {
                        String id = KNOWN_BOOK_IDS[i];
                        lines.add((JournalData.hasLore(id) ? "• " : "  ???  ")
                                + (JournalData.hasLore(id) ? KNOWN_BOOKS[i] : "?"));
                    }
                    lines.add("");
                    lines.add(JournalData.loreIds().size() >= 11
                            ? "One more book keeps appearing only now."
                            : "The last entries stay hidden until the rest are found.");
                }
                lines.add("");
                lines.add("Cave notes are readable from loot chests underground.");
            }
            case 2 -> {
                lines.add("BASE DISCOVERIES");
                lines.add("");
                lines.add("Base mapping is handled by the server.");
                lines.add("The detailed base journal will appear when base data is synced.");
            }
            default -> {
                lines.add("EVENT HISTORY");
                lines.add("");
                lines.add("No event history has been synced yet.");
                lines.add("This page will list discoveries and encounters as tracking is added.");
            }
        }
        return lines;
    }
}
