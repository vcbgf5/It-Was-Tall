package com.itwastall.client.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.Items;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** In-game journal opened with J. It only displays information currently available to the client. */
public final class JournalScreen extends Screen {
    private int tab;
    private static final String[] TABS = {"STATUS", "BOOKS", "BASE", "EVENTS"};

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
        int y = 78;
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
                    lines.add("Phase: " + (com.itwastall.client.JournalData.phase() == 0 ? "PRE" : "ACTIVE"));
                    lines.add("Stage: " + com.itwastall.client.JournalData.stage() + " / 5");
                    lines.add("Tension: " + Math.round(com.itwastall.client.JournalData.tension()) + " / 100");
                } else lines.add("No world is currently loaded.");
                lines.add("");
                lines.add("The journal updates as the mod provides more discoveries.");
            }
            case 1 -> {
                lines.add("BOOKS IN YOUR INVENTORY");
                lines.add("");
                if (client.player == null) {
                    lines.add("No player is available.");
                } else {
                    int found = 0;
                    for (int slot = 0; slot < client.player.getInventory().size(); slot++) {
                        var stack = client.player.getInventory().getStack(slot);
                        if (stack.isOf(Items.WRITTEN_BOOK) || stack.isOf(Items.WRITABLE_BOOK)) {
                            lines.add("• " + stack.getName().getString());
                            found++;
                        }
                    }
                    if (found == 0) lines.add("No books carried. Check the chests around your base.");
                }
                lines.add("");
                lines.add("Books stored in chests remain readable in the chest.");
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
