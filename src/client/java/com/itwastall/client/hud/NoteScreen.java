package com.itwastall.client.hud;

import com.itwastall.ItWasTall;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Reader for notes found in structures: a torn parchment page ({@code textures/gui/note.png})
 * with the text written in ink. Scroll wheel / arrows move through long notes. No background blur.
 */
public final class NoteScreen extends Screen {
    private static final Identifier TEX = ItWasTall.id("textures/gui/note.png");
    private static final int PAGE_X = 16, PAGE_Y = 8, PAGE_W = 224, PAGE_H = 240;
    private static final int TEXT_X = 34, TEXT_Y = 30, TEXT_W = 186, TEXT_BOTTOM = 210;
    private static final int INK = 0xFF2E2016, INK_RED = 0xFF7A1410, INK_FADED = 0xFF6E5C49;

    private final String noteTitle;
    /** Where X / Escape go back to (the journal when opened from its BOOKS tab), null = close. */
    @org.jetbrains.annotations.Nullable
    private final Screen parent;
    private final String noteBody;
    private List<OrderedText> lines = List.of();
    private int scroll;
    private float scale = 1f;
    private int left, top;

    public NoteScreen(String title, String body) {
        this(title, body, null);
    }

    public NoteScreen(String title, String body, @org.jetbrains.annotations.Nullable Screen parent) {
        super(Text.literal(title == null ? "Note" : title));
        this.parent = parent;
        this.noteTitle = title == null ? "Note" : title;
        // notes from loot tables store line breaks as "|" (SNBT strings cannot hold a newline)
        this.noteBody = body == null ? "" : body.replace("|", "\n");
    }

    @Override
    protected void init() {
        scale = Math.min(1f, Math.min((width - 16) / (float) PAGE_W, (height - 16) / (float) PAGE_H));
        left = Math.round((width - PAGE_W * scale) / 2f - PAGE_X * scale);
        top = Math.round((height - PAGE_H * scale) / 2f - PAGE_Y * scale);
        lines = textRenderer.wrapLines(Text.literal(noteBody), TEXT_W);
        scroll = 0;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xA0000000);
        Vignette.draw(ctx, width, height, 0.8f);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx, mouseX, mouseY, delta);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(left, top, 0);
        ctx.getMatrices().scale(scale, scale, 1f);
        RenderSystem.enableBlend();
        ctx.drawTexture(TEX, 0, 0, 0, 0, 256, 256, 256, 256);
        RenderSystem.disableBlend();

        int tw = textRenderer.getWidth(noteTitle);
        ctx.drawText(textRenderer, noteTitle, TEXT_X + (TEXT_W - tw) / 2, TEXT_Y - 12, INK_RED, false);
        ctx.fill(TEXT_X + (TEXT_W - tw) / 2, TEXT_Y - 2, TEXT_X + (TEXT_W + tw) / 2, TEXT_Y - 1, 0x807A1410);

        int y = TEXT_Y + 6;
        int visible = (TEXT_BOTTOM - y) / 10;
        for (int i = scroll; i < lines.size() && i < scroll + visible; i++) {
            ctx.drawText(textRenderer, lines.get(i), TEXT_X, y, INK, false);
            y += 10;
        }
        // the close X in the top-right corner of the page
        boolean xHover = closeHit(mouseX, mouseY);
        ctx.drawText(textRenderer, "X", CLOSE_X, CLOSE_Y, xHover ? 0xFFB01810 : INK_RED, false);
        if (lines.size() > visible) {
            String more = (scroll + visible < lines.size() ? "▼" : " ") + "  " + (scroll > 0 ? "▲" : " ");
            ctx.drawText(textRenderer, more, TEXT_X + TEXT_W - textRenderer.getWidth(more), TEXT_BOTTOM + 4, INK_FADED, false);
        }
        ctx.getMatrices().pop();
    }

    private static final int CLOSE_X = TEXT_X + TEXT_W - 8, CLOSE_Y = TEXT_Y - 12;

    private boolean closeHit(double mouseX, double mouseY) {
        double px = (mouseX - left) / scale, py = (mouseY - top) / scale;
        return px >= CLOSE_X - 4 && px <= CLOSE_X + 10 && py >= CLOSE_Y - 4 && py <= CLOSE_Y + 12;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && closeHit(mouseX, mouseY)) {
            close();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void close() {
        if (client != null && parent != null) client.setScreen(parent);
        else super.close();
    }

    private int maxScroll() {
        int visible = (TEXT_BOTTOM - TEXT_Y - 6) / 10;
        return Math.max(0, lines.size() - visible);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(verticalAmount)));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN) {
            scroll = Math.min(maxScroll(), scroll + 1);
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_UP) {
            scroll = Math.max(0, scroll - 1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
