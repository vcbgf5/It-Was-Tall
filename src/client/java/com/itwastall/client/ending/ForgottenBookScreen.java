package com.itwastall.client.ending;

import com.itwastall.registry.ModBlocks;
import com.itwastall.registry.ModItems;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * THE FORGOTTEN BOOK: left by those who came down before. A first page in their words, then what they built from
 * the stones of the Below (crafting grids), and at the end where the plan and the hearts are.
 */
public class ForgottenBookScreen extends Screen {
    private record Page(String title, @Nullable Item[] grid, @Nullable Item result, int count, String text) {}

    private static final Item FS = ModBlocks.FORGOTTEN_STONE.asItem(), OL = ModBlocks.OLDROCK.asItem();
    private static final Item FB = ModBlocks.FORGOTTEN_STONE_BRICKS.asItem(), OB = ModBlocks.OLDROCK_BRICKS.asItem();
    private static final Page[] PAGES = {
            new Page("THE FORGOTTEN BOOK", null, null, 0,
                    "We came down before you. Below everything, it cannot follow its own rules, and the stone here remembers. "
                            + "We built with it. Up there, it can be sent away: with fire, with stone, and with a heart that is empty. "
                            + "Read on. Build. Do not let the fire die."),
            new Page("Forgotten Stone Bricks", new Item[]{FS, FS, null, FS, FS, null, null, null, null}, FB, 4,
                    "The pale stone, cut. It holds the pillars up."),
            new Page("Oldrock Bricks", new Item[]{OL, OL, null, OL, OL, null, null, null, null}, OB, 4,
                    "The dark stone, cut. The fire must stand on nine of them."),
            new Page("Ritual Pillar", new Item[]{null, FB, null, null, OB, null, null, FB, null}, ModBlocks.RITUAL_PILLAR.asItem(), 1,
                    "Stack two. Put a candle on top and light it. Four of them, three blocks from the fire."),
            new Page("Oldrock Lantern", new Item[]{OB, OB, OB, OB, Items.TORCH, OB, OB, OB, OB}, ModBlocks.OLDROCK_LANTERN.asItem(), 2,
                    "Light that the dark down here cannot eat."),
            new Page("Carved Forgotten Stone", new Item[]{FB, Items.FLINT, null, null, null, null, null, null, null},
                    ModBlocks.CARVED_FORGOTTEN_STONE.asItem(), 1, "We carved its eye into the walls, so it would know we saw it too."),
            new Page("THE RITUAL", null, ModItems.HOLLOW_HEART, 1,
                    "The plan is drawn on a sheet we left in the archives. The hollow hearts lie on the old altars, deep "
                            + "below, where four dead candles stand. Give one to the burning fire, at night. Then hold until dawn."),
    };
    private int page;
    private ButtonWidget prev, next;

    public ForgottenBookScreen() {
        super(Text.literal("Forgotten Book"));
    }

    @Override
    protected void init() {
        int px = width / 2 - 130, py = height / 2 - 95;
        prev = addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> turn(-1)).dimensions(px + 8, py + 166, 20, 20).build());
        next = addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> turn(1)).dimensions(px + 232, py + 166, 20, 20).build());
        updateButtons();
    }

    private void turn(int d) {
        page = Math.max(0, Math.min(PAGES.length - 1, page + d));
        updateButtons();
        if (client != null) {
            client.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(net.minecraft.sound.SoundEvents.ITEM_BOOK_PAGE_TURN, 1f));
        }
    }

    private void updateButtons() {
        prev.active = page > 0;
        next.active = page < PAGES.length - 1;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        int px = width / 2 - 130, py = height / 2 - 95, pw = 260, ph = 190;
        ctx.fill(px - 2, py - 2, px + pw + 2, py + ph + 2, 0xFF2A1E16);
        ctx.fill(px, py, px + pw, py + ph, 0xFFDECCA6);
        ctx.fill(px + 4, py + 4, px + pw - 4, py + ph - 4, 0xFFE6D6B4);
        Page p = PAGES[page];
        ctx.drawCenteredTextWithShadow(textRenderer, p.title(), width / 2, py + 10, 0xFFC8281E);
        int textY = py + 28;
        if (p.grid() != null) {
            int gx = px + 52, gy = py + 30;
            for (int i = 0; i < 9; i++) slot(ctx, gx + (i % 3) * 18, gy + (i / 3) * 18, p.grid()[i] == null ? null : new ItemStack(p.grid()[i]));
            ctx.drawText(textRenderer, "->", gx + 62, gy + 23, 0xFF5A4630, false);
            slot(ctx, gx + 84, gy + 18, new ItemStack(p.result(), p.count()));
            if (p.count() > 1) ctx.drawItemInSlot(textRenderer, new ItemStack(p.result(), p.count()), gx + 85, gy + 19);
            textY = gy + 62;
        } else if (p.result() != null) {
            ctx.drawItem(new ItemStack(p.result()), width / 2 - 8, py + 24);
            textY = py + 46;
        }
        List<OrderedText> lines = textRenderer.wrapLines(Text.literal(p.text()), pw - 30);
        for (int i = 0; i < lines.size(); i++) ctx.drawText(textRenderer, lines.get(i), px + 15, textY + i * 10, 0xFF2A1E16, false);
        ctx.drawCenteredTextWithShadow(textRenderer, (page + 1) + " / " + PAGES.length, width / 2, py + ph - 18, 0xFF9C8E78);
    }

    private void slot(DrawContext ctx, int x, int y, @Nullable ItemStack stack) {
        ctx.fill(x, y, x + 18, y + 18, 0xFF8B8B8B);
        ctx.fill(x, y, x + 17, y + 1, 0xFF373737);
        ctx.fill(x, y, x + 1, y + 17, 0xFF373737);
        ctx.fill(x + 1, y + 17, x + 18, y + 18, 0xFFFFFFFF);
        ctx.fill(x + 17, y + 1, x + 18, y + 18, 0xFFFFFFFF);
        if (stack != null) ctx.drawItem(stack, x + 1, y + 1);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
