package com.itwastall.client.hud;

import com.itwastall.ItWasTall;
import com.itwastall.client.JournalData;
import com.itwastall.director.Stage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * The J journal (MODPLAN §16): an old leather notebook with two parchment pages, drawn from
 * {@code textures/gui/journal.png} (hand-edited now — do not regenerate it with tools/gen_gui.py).
 * Tabs: STATUS (stage, fear, the task and the task history), BOOKS (everything anyone on the server has
 * read — click to read), BASE, EVENTS and NOTES (a notice board shared by the whole server). Tabs are red bookmark ribbons
 * sticking out of the top of the book. Everything is drawn in "ink" on the paper.
 *
 * <p>No vanilla background blur: {@link #renderBackground} is overridden with a plain darkening,
 * and the book is drawn once — the old version blurred twice (its own call + Screen.render's).
 */
public final class JournalScreen extends Screen {
    private static final Identifier TEX = ItWasTall.id("textures/gui/journal.png");
    private static final int TEX_W = 512, TEX_H = 256;
    private static final int BOOK_W = 400, BOOK_H = 256;

    // ink colours
    private static final int INK = 0xFF2E2016;
    private static final int INK_FADED = 0xFF6E5C49;
    private static final int INK_RED = 0xFF7A1410;

    // page text areas (book space)
    private static final int L_X = 30, R_X = 216, PAGE_TOP = 26, PAGE_W = 156, PAGE_BOTTOM = 226;

    private static final String[] TABS = {"STATUS", "BOOKS", "BASE", "EVENTS", "NOTES"};
    private static final int[] RIBBON_X = {40, 112, 184, 256, 328};
    private static final int TAB_BOOKS = 1, TAB_NOTES = 4;

    /** Mirror of the server's LoreBooks.BOOKS order (§9.2). */
    private static final String[] KNOWN_BOOK_IDS = {
            "01_someone_was_here", "02_the_window", "03_the_mine", "04_dont_open",
            "05_the_player_list", "06_it_joined", "07_observation", "08_the_chest",
            "09_coordinates", "10_the_last_entry", "11_the_list", "12_it_knows"
    };
    private static int tab; // remembered while the game runs
    private float scale = 1f;
    private int bookX, bookY;

    // BOOKS tab: page of the list and the clickable rows drawn this frame (book space: x, y, w, h, index)
    private static int booksPage;
    private final List<int[]> bookHits = new java.util.ArrayList<>();
    private static final int BOOKS_LEFT = 7, BOOKS_RIGHT = 7, BOOK_ROW = 22;
    /** The right page starts below the close X so long lines never run under it. */
    private static final int RIGHT_TOP = PAGE_TOP + 14;
    // NOTES tab: scroll of the board, the text field and the clickable bits
    private static int notesScroll;
    private static String draft = "";
    private net.minecraft.client.gui.widget.TextFieldWidget noteField;
    private final List<long[]> deleteHits = new java.util.ArrayList<>();   // x, y, w, h, noteId

    public JournalScreen() {
        super(Text.literal("Journal"));
    }

    @Override
    protected void init() {
        // fit the book (plus the ribbons above it) into the screen, never upscale past 1:1
        scale = Math.min(1f, Math.min((width - 16) / (float) BOOK_W, (height - 16) / (float) (BOOK_H + 44)));
        bookX = Math.round((width - BOOK_W * scale) / 2f);
        bookY = Math.round((height - (BOOK_H + 44) * scale) / 2f + 44 * scale);

        // NOTES: the input line sits on the right page (screen space, so it follows the book's scale)
        noteField = new net.minecraft.client.gui.widget.TextFieldWidget(textRenderer,
                bookX + Math.round(R_X * scale), bookY + Math.round((PAGE_TOP + 24) * scale),
                Math.round(PAGE_W * scale), 12, Text.literal("Note"));
        noteField.setMaxLength(com.itwastall.world.SharedJournal.MAX_NOTE_LENGTH);
        noteField.setDrawsBackground(false);
        noteField.setEditableColor(INK);
        noteField.setText(draft);
        noteField.setChangedListener(t -> draft = t);
        noteField.setVisible(tab == TAB_NOTES);
        addSelectableChild(noteField);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    /** Plain darkening — no blur (it smeared the book in the old version). */
    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xA0000000);
        Vignette.draw(ctx, width, height, 0.8f);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx, mouseX, mouseY, delta);
        float bx = (mouseX - bookX) / scale, by = (mouseY - bookY) / scale;

        ctx.getMatrices().push();
        ctx.getMatrices().translate(bookX, bookY, 0);
        ctx.getMatrices().scale(scale, scale, 1f);
        RenderSystem.enableBlend();

        // ribbons first: the book covers their lower ends, so they look tucked between the pages
        for (int i = 0; i < TABS.length; i++) {
            boolean active = i == tab;
            boolean hover = ribbonHit(i, bx, by);
            int rx = RIBBON_X[i];
            if (active) ctx.drawTexture(TEX, rx, -34, 424, 0, 20, 40, TEX_W, TEX_H);
            else ctx.drawTexture(TEX, rx, hover ? -30 : -26, 400, 0, 20, 34, TEX_W, TEX_H);
            String label = TABS[i];
            int lw = textRenderer.getWidth(label);
            int color = active ? 0xFFE8D9B8 : hover ? 0xFFCDBB98 : 0xFF8F7C62;
            ctx.drawText(textRenderer, label, rx + 10 - lw / 2, active ? -46 : -40, color, true);
        }
        ctx.drawTexture(TEX, 0, 0, 0, 0, BOOK_W, BOOK_H, TEX_W, TEX_H);

        // close X in the corner of the right page
        boolean closeHover = closeHit(bx, by);
        ctx.drawTexture(TEX, 368, 20, 400, 88, 12, 12, TEX_W, TEX_H);
        if (closeHover) ctx.fill(366, 18, 382, 34, 0x30701010);

        switch (tab) {
            case 0 -> drawStatus(ctx, bx, by);
            case 1 -> drawBooks(ctx, bx, by);
            case 2 -> drawBase(ctx);
            case 3 -> drawEvents(ctx);
            default -> drawNotes(ctx, bx, by);
        }
        RenderSystem.disableBlend();
        ctx.getMatrices().pop();
        if (tab == TAB_NOTES && noteField != null) noteField.render(ctx, mouseX, mouseY, delta);
    }

    // ------------------------------------------------------------------------ pages ----

    private void drawStatus(DrawContext ctx, float bx, float by) {
        MinecraftClient client = MinecraftClient.getInstance();
        int y = heading(ctx, L_X, PAGE_TOP, "What is happening");
        boolean active = JournalData.phase() != 0;
        y = para(ctx, L_X, y, active ? "It is here. It came into this world and it is not leaving."
                : "The first day. Everything is quiet. Too quiet?", INK) + 8;

        Stage stage = Stage.fromIndex(JournalData.stage());
        ctx.drawText(textRenderer, "Stage " + JournalData.stage() + " — " + stage.displayName(), L_X, y, INK_RED, false);
        y += 12;
        for (int i = 0; i <= 5; i++) {
            boolean filled = i <= JournalData.stage();
            ctx.drawTexture(TEX, L_X + i * 14, y, filled ? 412 : 400, 72, 10, 10, TEX_W, TEX_H);
        }
        y += 18;

        ctx.drawText(textRenderer, "Fear", L_X, y, INK, false);
        y += 11;
        ctx.drawTexture(TEX, L_X, y, 400, 48, 104, 10, TEX_W, TEX_H);
        int fill = Math.round(100 * JournalData.tension() / 100f);
        if (fill > 0) ctx.drawTexture(TEX, L_X + 2, y + 2, 400, 60, fill, 6, TEX_W, TEX_H);
        ctx.drawText(textRenderer, Math.round(JournalData.tension()) + "%", L_X + 110, y + 1, INK_FADED, false);
        y += 20;
        para(ctx, L_X, y, stageHint(JournalData.stage()), INK_FADED);

        // right page: the task + what you carry
        int ry = heading(ctx, R_X, PAGE_TOP, "What it wants");
        String task = JournalData.task();
        boolean none = task == null || task.isBlank() || task.equalsIgnoreCase("no task");
        ry = para(ctx, R_X, ry, none ? "Nothing. For now." : task, none ? INK_FADED : INK) + 6;
        // the task history (saved with the world): newest first, one line each
        List<String> history = JournalData.tasks();
        if (!history.isEmpty()) {
            ctx.drawText(textRenderer, "Earlier", R_X, ry, INK_RED, false);
            ry += 11;
            for (int i = 0; i < Math.min(4, history.size()); i++) {
                String h = history.get(i);
                int col = h.endsWith("failed") ? INK_RED : h.endsWith("done") ? INK : INK_FADED;
                String line = h.replace("Night ", "N");
                if (textRenderer.getWidth(line) > PAGE_W) line = textRenderer.trimToWidth(line, PAGE_W - textRenderer.getWidth("...")) + "...";
                ctx.drawText(textRenderer, line, R_X, ry, col, false);
                ry += 10;
            }
            ry += 6;
        }

        int crystals = 0;
        if (client.player != null) {
            for (int slot = 0; slot < client.player.getInventory().size(); slot++) {
                var stack = client.player.getInventory().getStack(slot);
                if (stack.getItem().toString().contains("warding_crystal")) crystals += stack.getCount();
            }
        }
        ry = heading(ctx, R_X, ry, "Warding crystals");
        ry = para(ctx, R_X, ry, crystals == 0 ? "None. Look for the blue glow in old chests underground."
                : crystals + " — the only thing it is afraid of.", crystals == 0 ? INK_FADED : INK) + 12;

        // fear vignette toggle (accessibility)
        int cy = PAGE_BOTTOM - 12;
        boolean on = FearHud.isEnabled();
        ctx.drawTexture(TEX, R_X, cy, on ? 428 : 416, 88, 10, 10, TEX_W, TEX_H);
        boolean hover = toggleHit(bx, by);
        ctx.drawText(textRenderer, "Fear & chase effects", R_X + 14, cy + 1, hover ? INK_RED : INK, false);
    }

    /**
     * BOOKS: everything anyone on the server has read (our books and notes), in the order they were found.
     * Click a title to read it; the reader's X brings you back here.
     */
    private void drawBooks(DrawContext ctx, float bx, float by) {
        bookHits.clear();
        List<com.itwastall.net.SharedJournalPayload.Book> all = JournalData.books();
        int lore = 0;
        for (String id : KNOWN_BOOK_IDS) for (var bk : all) if (bk.id().equals(id)) lore++;
        int y = heading(ctx, L_X, PAGE_TOP, "Books & notes (" + all.size() + ")");
        if (all.isEmpty()) {
            para(ctx, L_X, y, "Nobody has read anything yet.\n\nOur books turn up in chests and in strange places. Right-click one to read it — then it is written in here, for everyone on the server.", INK_FADED);
            int ry = heading(ctx, R_X, PAGE_TOP, "Lore " + lore + " / 12");
            para(ctx, R_X, ry, "Twelve of them tell the story. Find them.", INK_FADED);
            return;
        }
        int perPage = BOOKS_LEFT + BOOKS_RIGHT;
        int pages = Math.max(1, (all.size() + perPage - 1) / perPage);
        booksPage = Math.max(0, Math.min(booksPage, pages - 1));
        int start = booksPage * perPage;
        int ry = RIGHT_TOP;
        for (int k = 0; k < perPage && start + k < all.size(); k++) {
            var bk = all.get(start + k);
            boolean left = k < BOOKS_LEFT;
            int x = left ? L_X : R_X;
            int rowY = left ? y : ry;
            boolean hover = bx >= x && bx <= x + PAGE_W && by >= rowY - 2 && by <= rowY + BOOK_ROW - 4;
            String title = textRenderer.trimToWidth((start + k + 1) + ". " + bk.title(), PAGE_W);
            ctx.drawText(textRenderer, title, x, rowY, hover ? INK_RED : INK, false);
            ctx.drawText(textRenderer, textRenderer.trimToWidth("   found by " + bk.foundBy(), PAGE_W), x, rowY + 10, INK_FADED, false);
            if (hover) ctx.fill(x, rowY + 19, x + PAGE_W, rowY + 20, 0x807A1410);
            bookHits.add(new int[]{x, rowY - 2, PAGE_W, BOOK_ROW - 2, start + k});
            if (left) y += BOOK_ROW;
            else ry += BOOK_ROW;
        }
        // footer: lore counter + page arrows
        int fy = PAGE_BOTTOM - 10;
        ctx.drawText(textRenderer, "Lore " + lore + " / 12", L_X, fy, INK_RED, false);
        if (pages > 1) {
            String label = "page " + (booksPage + 1) + " / " + pages;
            int lw = textRenderer.getWidth(label);
            int cx = R_X + PAGE_W / 2;
            ctx.drawText(textRenderer, label, cx - lw / 2, fy, INK_FADED, false);
            ctx.drawText(textRenderer, "<", cx - lw / 2 - 14, fy, booksPage > 0 ? (pageArrowHit(-1, bx, by) ? INK_RED : INK) : INK_FADED, false);
            ctx.drawText(textRenderer, ">", cx + lw / 2 + 8, fy, booksPage < pages - 1 ? (pageArrowHit(1, bx, by) ? INK_RED : INK) : INK_FADED, false);
        } else {
            ctx.drawText(textRenderer, "Click a title to read it.", R_X, fy, INK_FADED, false);
        }
    }

    private boolean pageArrowHit(int dir, float bx, float by) {
        String label = "page " + (booksPage + 1) + " / 9";
        int lw = textRenderer.getWidth(label);
        int cx = R_X + PAGE_W / 2;
        int ax = dir < 0 ? cx - lw / 2 - 14 : cx + lw / 2 + 8;
        int fy = PAGE_BOTTOM - 10;
        return bx >= ax - 4 && bx <= ax + 10 && by >= fy - 3 && by <= fy + 11;
    }

    /**
     * NOTES: a notice board shared by the whole server. Left page: the notes, newest first (scroll with the
     * wheel); right page: write one. Your own notes have a small x to take them down again.
     */
    private void drawNotes(DrawContext ctx, float bx, float by) {
        deleteHits.clear();
        List<com.itwastall.net.SharedJournalPayload.Note> notes = JournalData.notes();
        int y = heading(ctx, L_X, PAGE_TOP, "For everyone (" + notes.size() + ")");
        String me = MinecraftClient.getInstance().player == null ? "" : MinecraftClient.getInstance().player.getUuidAsString();
        boolean op = MinecraftClient.getInstance().player != null && MinecraftClient.getInstance().player.hasPermissionLevel(2);
        if (notes.isEmpty()) {
            para(ctx, L_X, y, "Nobody has written anything.\n\nLeave a warning for the others.", INK_FADED);
        } else {
            notesScroll = Math.max(0, Math.min(notesScroll, notes.size() - 1));
            for (int i = notes.size() - 1 - notesScroll; i >= 0; i--) {
                var n = notes.get(i);
                List<OrderedText> lines = textRenderer.wrapLines(Text.literal(n.text()), PAGE_W);
                int h = 11 + lines.size() * 10 + 5;
                if (y + h > PAGE_BOTTOM + 4) break;
                String head = textRenderer.trimToWidth(n.author() + " · " + n.when(), PAGE_W - 12);
                ctx.drawText(textRenderer, head, L_X, y, INK_RED, false);
                if (n.authorUuid().equals(me) || op) {
                    int dx = L_X + PAGE_W - 6;
                    boolean hover = bx >= dx - 3 && bx <= dx + 9 && by >= y - 2 && by <= y + 10;
                    ctx.drawText(textRenderer, "x", dx, y, hover ? INK_RED : INK_FADED, false);
                    deleteHits.add(new long[]{dx - 3, y - 2, 12, 12, n.id()});
                }
                y += 11;
                for (OrderedText line : lines) {
                    ctx.drawText(textRenderer, line, L_X, y, INK, false);
                    y += 10;
                }
                y += 5;
            }
            if (notesScroll > 0 || y > PAGE_BOTTOM) {
                ctx.drawText(textRenderer, "scroll for more", L_X, PAGE_BOTTOM + 2, INK_FADED, false);
            }
        }

        int ry = heading(ctx, R_X, PAGE_TOP, "Write a note");
        // the line the text field writes on
        ctx.fill(R_X, ry + 20, R_X + PAGE_W, ry + 21, noteField != null && noteField.isFocused() ? 0xC07A1410 : 0x806E5C49);
        if (draft.isEmpty() && (noteField == null || !noteField.isFocused())) {
            ctx.drawText(textRenderer, "click here and write...", R_X, ry + 8, INK_FADED, false);
        }
        int py = ry + 30;
        boolean pinHover = pinHit(bx, by);
        ctx.drawText(textRenderer, "[ pin it to the board ]", R_X, py, draft.isBlank() ? INK_FADED : pinHover ? INK_RED : INK, false);
        String count = draft.length() + " / " + com.itwastall.world.SharedJournal.MAX_NOTE_LENGTH;
        ctx.drawText(textRenderer, count, R_X + PAGE_W - textRenderer.getWidth(count), py, INK_FADED, false);
        para(ctx, R_X, py + 18, "Everyone on this server reads these in their journal. It is saved with the world.\n\nEnter also pins it.", INK_FADED);
    }

    private boolean pinHit(float bx, float by) {
        int py = PAGE_TOP + 16 + 30;
        return tab == TAB_NOTES && bx >= R_X && bx <= R_X + textRenderer.getWidth("[ pin it to the board ]") && by >= py - 2 && by <= py + 10;
    }

    private void submitNote() {
        String text = draft.strip();
        if (text.isEmpty()) return;
        if (net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(com.itwastall.net.NoteActionPayload.ID)) {
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                    new com.itwastall.net.NoteActionPayload(com.itwastall.net.NoteActionPayload.ADD, 0, text));
        }
        draft = "";
        if (noteField != null) noteField.setText("");
        notesScroll = 0;
        click();
    }

    private void setTab(int newTab) {
        if (tab == newTab) return;
        tab = newTab;
        if (noteField != null) {
            noteField.setVisible(tab == TAB_NOTES);
            if (tab != TAB_NOTES) {
                noteField.setFocused(false);
                setFocused(null);
            }
        }
        click();
    }

    private void drawBase(DrawContext ctx) {
        List<Integer> b = JournalData.baseCounts();
        int y = heading(ctx, L_X, PAGE_TOP, "What it knows");
        if (b.size() < 9) {
            para(ctx, L_X, y, "It has not found your home. Yet.\n\nSleep in a bed and keep your things close. That is how it learns where you live.", INK);
            int ry = heading(ctx, R_X, PAGE_TOP, "Your home");
            para(ctx, R_X, ry, "Empty page.", INK_FADED);
            return;
        }
        y = para(ctx, L_X, y, "It knows where you sleep:", INK);
        ctx.drawText(textRenderer, b.get(0) + ", " + b.get(1) + ", " + b.get(2), L_X + 4, y + 2, INK_RED, false);
        y += 18;
        y = row(ctx, L_X, y, "Windows", b.get(3));
        y = row(ctx, L_X, y, "Doors", b.get(4));
        y = row(ctx, L_X, y, "Lights", b.get(5));
        y = row(ctx, L_X, y, "Chests", b.get(6));
        y = row(ctx, L_X, y, "Dark corners", b.get(7));
        row(ctx, L_X, y, "Blocks you placed", b.get(8));

        int ry = heading(ctx, R_X, PAGE_TOP, "Notes");
        if (b.get(3) > 0) ry = para(ctx, R_X, ry, "It can look in through " + plural(b.get(3), "window", "windows") + ".", INK) + 6;
        if (b.get(5) > 0) ry = para(ctx, R_X, ry, "It counted your lights. It can put them out.", INK) + 6;
        if (b.get(6) > 0) ry = para(ctx, R_X, ry, "It has been in your chests.", INK) + 6;
        if (b.get(7) > 0) ry = para(ctx, R_X, ry, "There are dark corners. Good places to hide. It knows them too.", INK) + 6;
        if (b.get(3) + b.get(5) + b.get(6) + b.get(7) == 0) para(ctx, R_X, ry, "Nothing worth noting. Strange.", INK_FADED);
    }

    private void drawEvents(DrawContext ctx) {
        List<String> ev = JournalData.events();
        int y = heading(ctx, L_X, PAGE_TOP, "What happened");
        if (ev.isEmpty()) {
            para(ctx, L_X, y, "Nothing has happened.\nKeep it that way.", INK_FADED);
            return;
        }
        int x = L_X;
        for (String e : ev) {
            int h = textRenderer.wrapLines(Text.literal(e), PAGE_W).size() * 10 + 5;
            if (y + h > PAGE_BOTTOM) {
                if (x == R_X) break;
                x = R_X;
                y = RIGHT_TOP;
            }
            y = para(ctx, x, y, e, INK) + 5;
        }
    }

    // ---------------------------------------------------------------------- helpers ----

    private int heading(DrawContext ctx, int x, int y, String text) {
        ctx.drawText(textRenderer, text, x, y, INK_RED, false);
        int w = textRenderer.getWidth(text);
        ctx.fill(x, y + 10, x + w, y + 11, 0x807A1410);
        return y + 16;
    }

    /** Wrapped paragraph; returns the y below it. "\n" starts a new line. */
    private int para(DrawContext ctx, int x, int y, String text, int color) {
        for (String part : text.split("\n", -1)) {
            if (part.isEmpty()) {
                y += 6;
                continue;
            }
            for (OrderedText line : textRenderer.wrapLines(Text.literal(part), PAGE_W)) {
                if (y > PAGE_BOTTOM) return y;
                ctx.drawText(textRenderer, line, x, y, color, false);
                y += 10;
            }
        }
        return y;
    }

    private int row(DrawContext ctx, int x, int y, String label, int value) {
        ctx.drawText(textRenderer, label, x + 4, y, INK, false);
        String v = String.valueOf(value);
        ctx.drawText(textRenderer, v, x + PAGE_W - textRenderer.getWidth(v), y, value > 0 ? INK_RED : INK_FADED, false);
        ctx.fill(x + 4, y + 10, x + PAGE_W, y + 11, 0x306E5C49);
        return y + 14;
    }

    private static String plural(int n, String one, String many) {
        return n + " " + (n == 1 ? one : many);
    }

    private static String stageHint(int stage) {
        return switch (stage) {
            case 0 -> "Only sounds. You tell yourself it was the wind.";
            case 1 -> "Something watches from far away. Don't walk towards it.";
            case 2 -> "It comes closer. It has found your windows.";
            case 3 -> "It touches your world now. Lights, doors, tunnels.";
            case 4 -> "It hunts. If it runs at you, run home.";
            default -> "It hunts. Home is not safe anymore.";
        };
    }

    private static boolean ribbonHit(int i, float bx, float by) {
        int rx = RIBBON_X[i];
        return bx >= rx - 14 && bx <= rx + 34 && by >= -50 && by <= 4;
    }

    private static boolean closeHit(float bx, float by) {
        return bx >= 364 && bx <= 384 && by >= 16 && by <= 36;
    }

    private boolean toggleHit(float bx, float by) {
        int cy = PAGE_BOTTOM - 12;
        return tab == 0 && bx >= R_X && bx <= R_X + PAGE_W && by >= cy - 2 && by <= cy + 11;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        float bx = (float) ((mouseX - bookX) / scale), by = (float) ((mouseY - bookY) / scale);
        for (int i = 0; i < TABS.length; i++) {
            if (ribbonHit(i, bx, by)) {
                setTab(i);
                return true;
            }
        }
        if (tab == TAB_BOOKS) {
            for (int[] hit : bookHits) {
                if (bx >= hit[0] && bx <= hit[0] + hit[2] && by >= hit[1] && by <= hit[1] + hit[3]) {
                    var all = JournalData.books();
                    if (hit[4] < all.size()) {
                        var bk = all.get(hit[4]);
                        click();
                        MinecraftClient.getInstance().setScreen(new NoteScreen(bk.title(), bk.body(), this));
                    }
                    return true;
                }
            }
            int pages = Math.max(1, (JournalData.books().size() + BOOKS_LEFT + BOOKS_RIGHT - 1) / (BOOKS_LEFT + BOOKS_RIGHT));
            if (pages > 1 && pageArrowHit(-1, bx, by) && booksPage > 0) {
                booksPage--;
                click();
                return true;
            }
            if (pages > 1 && pageArrowHit(1, bx, by) && booksPage < pages - 1) {
                booksPage++;
                click();
                return true;
            }
        }
        if (tab == TAB_NOTES) {
            for (long[] hit : deleteHits) {
                if (bx >= hit[0] && bx <= hit[0] + hit[2] && by >= hit[1] && by <= hit[1] + hit[3]) {
                    if (net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(com.itwastall.net.NoteActionPayload.ID)) {
                        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                                new com.itwastall.net.NoteActionPayload(com.itwastall.net.NoteActionPayload.DELETE, hit[4], ""));
                    }
                    click();
                    return true;
                }
            }
            if (pinHit(bx, by)) {
                submitNote();
                return true;
            }
            // anywhere on the writing line focuses the field
            if (noteField != null && bx >= R_X && bx <= R_X + PAGE_W && by >= PAGE_TOP + 16 && by <= PAGE_TOP + 40) {
                setFocused(noteField);
                noteField.setFocused(true);
                return true;
            }
        }
        if (closeHit(bx, by)) {
            close();
            return true;
        }
        if (toggleHit(bx, by)) {
            FearHud.setEnabled(!FearHud.isEnabled());
            click();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (tab == TAB_NOTES) {
            notesScroll = Math.max(0, notesScroll - (int) Math.signum(verticalAmount));
            return true;
        }
        if (tab == TAB_BOOKS) {
            booksPage = Math.max(0, booksPage - (int) Math.signum(verticalAmount));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // writing a note: every key goes to the text field (J must not close the journal mid-word)
        if (tab == TAB_NOTES && noteField != null && noteField.isFocused()) {
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
                submitNote();
                return true;
            }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                noteField.setFocused(false);
                setFocused(null);
                return true;
            }
            noteField.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        // the journal key closes it again; arrows flip tabs
        if (com.itwastall.client.ItWasTallClient.journalKeyMatches(keyCode, scanCode)) {
            close();
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT) {
            setTab(Math.floorMod(tab + (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT ? 1 : -1), TABS.length));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private static void click() {
        MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ITEM_BOOK_PAGE_TURN, 1.0f));
    }

}
