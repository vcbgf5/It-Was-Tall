package com.itwastall.client.hud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.List;

/** Reusable reader for notes found in generated structures. */
public final class NoteScreen extends Screen {
    private final String noteTitle;
    private final String noteBody;

    public NoteScreen(String title, String body) {
        super(Text.literal(title == null ? "Note" : title));
        this.noteTitle = title == null ? "Note" : title;
        this.noteBody = body == null ? "" : body;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx, mouseX, mouseY, delta);
        int left = width / 2 - 150;
        int top = height / 2 - 105;
        ctx.fill(left - 12, top - 12, left + 312, top + 222, 0xF01B1916);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(noteTitle), width / 2, top, 0xFFE6D7B5);
        List<OrderedText> wrapped = textRenderer.wrapLines(Text.literal(noteBody), 290);
        int y = top + 28;
        for (OrderedText line : wrapped) {
            if (y > top + 190) break;
            ctx.drawTextWithShadow(textRenderer, line, left, y, 0xFFE0DED8);
            y += 12;
        }
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("ESC — close"), width / 2, top + 205, 0xFF999999);
        super.render(ctx, mouseX, mouseY, delta);
    }
}
