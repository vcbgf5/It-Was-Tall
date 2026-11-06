package com.itwastall.client.hud;

import com.itwastall.ItWasTall;
import com.itwastall.net.ScaresPrefPayload;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.MessageScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * The WARNING screen, shown when the player enters a world with the mod (MODPLAN §16: no scares by
 * surprise). The title-screen night behind it, darkened; a parchment page with the warning; the journal's
 * own checkbox for "Jumpscares & scares" (click it or press space); ACCEPT TERMS and QUIT, drawn with the
 * mod's button style (built-in pack "It Was Tall UI"). ESC counts as accepting with the current choice.
 */
public class WarningScreen extends Screen {
    private static final Identifier BACKGROUND = ItWasTall.id("textures/gui/title_background.png");
    private static final Identifier NOTE = ItWasTall.id("textures/gui/note.png");
    private static final Identifier JOURNAL = ItWasTall.id("textures/gui/journal.png");
    private static final int INK = 0xFF2E2016, INK_RED = 0xFF7A1410, INK_FADED = 0xFF6E5C49;

    private static final Text TITLE = Text.literal("WARNING");
    private static final Text SUBTITLE = Text.literal("JUMPSCARES");
    private static final String[] BODY = {
            "A creature watches you at night.",
            "Come too close or stare too long and it may chase you.",
            "Underground it digs through stone and puts out torches.",
            "Sounds, whispers and messages can appear on screen.",
            "In multiplayer the scares are personal.",
            "!Jumpscares: flashing images and loud sounds.",
            "Untick the box if you do not want them."};
    private static final String CHECK_LABEL = "Jumpscares & scares";

    private boolean scaresEnabled = true;
    private int panelX, panelY, panelW, panelH, checkX, checkY;

    public WarningScreen() {
        super(Text.literal("It Was Tall"));
    }

    @Override
    protected void init() {
        panelW = Math.min(330, width - 16);
        // the page is as tall as its text (title, subtitle, wrapped lines, checkbox), never taller than the screen
        int lines = 0;
        for (String line : BODY) lines += textRenderer.wrapLines(Text.literal(line.replace("!", "")), panelW - 36).size();
        panelH = Math.min(height - 36, 14 + 16 + 4 + 12 + 8 + lines * 10 + 8 + 22 + 12);
        panelX = (width - panelW) / 2;
        panelY = Math.max(4, (height - (panelH + 28)) / 2);
        checkX = width / 2 - (20 + 6 + textRenderer.getWidth(CHECK_LABEL)) / 2;
        checkY = panelY + panelH - 34;

        int buttonWidth = Math.min(150, (width - 40) / 2);
        int gap = 8;
        int left = (width - (buttonWidth * 2 + gap)) / 2;
        int buttonY = Math.min(height - 24, panelY + panelH + 6);
        addDrawableChild(ButtonWidget.builder(Text.literal("ACCEPT TERMS"), b -> accept())
                .dimensions(left, buttonY, buttonWidth, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("QUIT"), b -> quit())
                .dimensions(left + buttonWidth + gap, buttonY, buttonWidth, 20).build());
    }

    private void toggle() {
        scaresEnabled = !scaresEnabled;
        if (ClientPlayNetworking.canSend(ScaresPrefPayload.ID)) ClientPlayNetworking.send(new ScaresPrefPayload(scaresEnabled));
        if (client != null) {
            client.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(
                    net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK, 1.0f));
        }
    }

    private void accept() {
        if (ClientPlayNetworking.canSend(ScaresPrefPayload.ID)) ClientPlayNetworking.send(new ScaresPrefPayload(scaresEnabled));
        this.close();
    }

    /** Leaves the world, the same way the pause menu's "Save and Quit" does. */
    private void quit() {
        if (client == null || client.world == null) {
            close();
            return;
        }
        boolean singleplayer = client.isInSingleplayer();
        client.world.disconnect();
        if (singleplayer) client.disconnect(new MessageScreen(Text.translatable("menu.savingLevel")));
        else client.disconnect();
        TitleScreen title = new TitleScreen();
        client.setScreen(singleplayer ? title : new MultiplayerScreen(title));
    }

    /** This screen draws its own background (the night of the title screen); no vanilla blur. */
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // the title-screen night, "cover"-fitted and darkened
        float s = Math.max(width / 1920f, height / 1080f);
        int bw = Math.round(1920 * s), bh = Math.round(1080 * s);
        context.drawTexture(BACKGROUND, (width - bw) / 2, (height - bh) / 2, bw, bh, 0, 0, 1920, 1080, 1920, 1080);
        context.fill(0, 0, width, height, 0x99000000);
        Vignette.draw(context, width, height, 0.9f);

        // the parchment page (note.png, stretched to the panel)
        RenderSystem.enableBlend();
        context.drawTexture(NOTE, panelX, panelY, panelW, panelH, 16, 8, 224, 240, 256, 256);
        RenderSystem.disableBlend();

        int cx = width / 2;
        int y = panelY + 16;
        float breathe = 1.0f + 0.05f * MathHelper.sin(net.minecraft.util.Util.getMeasuringTimeMs() % 1600L / 1600f * 2f * MathHelper.PI);
        context.getMatrices().push();
        context.getMatrices().translate(cx, y + 4, 0);
        context.getMatrices().scale(2f * breathe, 2f * breathe, 1f);
        int tw = textRenderer.getWidth(TITLE);
        context.drawText(textRenderer, TITLE, -tw / 2, -4, INK_RED, false);
        context.getMatrices().pop();
        y += 16;
        int sw = textRenderer.getWidth(SUBTITLE);
        context.drawText(textRenderer, SUBTITLE, cx - sw / 2, y, INK, false);
        context.fill(cx - sw / 2, y + 10, cx + sw / 2, y + 11, 0x807A1410);
        y += 16;
        for (String line : BODY) {
            boolean red = line.startsWith("!");
            String text = red ? line.substring(1) : line;
            for (var wrapped : textRenderer.wrapLines(Text.literal(text), panelW - 36)) {
                int lw = textRenderer.getWidth(wrapped);
                context.drawText(textRenderer, wrapped, cx - lw / 2, y, red ? INK_RED : INK, false);
                y += 10;
            }
        }

        // the journal's checkbox, doubled, with its label
        boolean hover = checkHit(mouseX, mouseY);
        RenderSystem.enableBlend();
        context.drawTexture(JOURNAL, checkX, checkY, 20, 20, scaresEnabled ? 428 : 416, 88, 10, 10, 512, 256);
        RenderSystem.disableBlend();
        if (hover) context.drawBorder(checkX - 1, checkY - 1, 22, 22, 0xFFB01810);
        context.drawText(textRenderer, CHECK_LABEL, checkX + 26, checkY + 6, hover ? INK_RED : INK, false);
        context.drawText(textRenderer, scaresEnabled ? "on" : "off", checkX + 26 + textRenderer.getWidth(CHECK_LABEL) + 6, checkY + 6,
                INK_FADED, false);

        super.render(context, mouseX, mouseY, delta);
    }

    private boolean checkHit(double mx, double my) {
        int w = 20 + 26 + textRenderer.getWidth(CHECK_LABEL);
        return mx >= checkX - 2 && mx <= checkX + w && my >= checkY - 2 && my <= checkY + 22;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && checkHit(mouseX, mouseY)) {
            toggle();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE) {
            toggle();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void close() {
        // ESC = accept with the current choice; the screen comes back on the next world join
        if (ClientPlayNetworking.canSend(ScaresPrefPayload.ID)) ClientPlayNetworking.send(new ScaresPrefPayload(scaresEnabled));
        super.close();
    }
}
