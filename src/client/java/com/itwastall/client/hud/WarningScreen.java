package com.itwastall.client.hud;

import com.itwastall.client.ClientAcceptedState;
import com.itwastall.net.ScaresPrefPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

/**
 * The WARNING screen, shown the first time the player enters a world with the mod
 * (MODPLAN §16 accessibility: no scares by surprise on a fresh install).
 *
 * <p>Title: WARNING / JUMPSCARES. Middle: the "Jumpscares & scares" toggle (no streamer
 * mode — the toggle does both, server-held). Bottom: ACCEPT TERMS, the only way out,
 * remembers itself in config/itwastall_client.json. ESC does NOT skip the warning: it
 * equally acts as an explicit no (toggle OFF) — but the screen still closes so nobody
 * gets stuck; the only trap would be fear itself.
 */
public class WarningScreen extends Screen {
    private boolean scaresEnabled = true;
    private ButtonWidget toggleButton;

    private static final Text TITLE = Text.literal("WARNING");
    private static final Text SUBTITLE = Text.literal("JUMPSCARES");
    private static final Text BODY_1 = Text.literal("It Was Tall adds a tall creature that watches players at night.");
    private static final Text BODY_2 = Text.literal("Approach it or watch it too long and it may chase you.");
    private static final Text BODY_3 = Text.literal("Underground, it can dig through stone and extinguish torches.");
    private static final Text BODY_4 = Text.literal("Random sounds, whispers and on-screen messages can occur.");
    private static final Text BODY_5 = Text.literal("In multiplayer, scares are heard and seen only by affected players.");
    private static final Text BODY_6 = Text.literal("This mod contains jumpscares, flashing images and loud sounds.");
    private static final Text BODY_7 = Text.literal("Turn scares off below if you do not want these effects.");

    public WarningScreen() {
        super(Text.literal("It Was Tall"));
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;

        // the toggle: ON/OFF on click, preference sent to the server directly upon press
        toggleButton = ButtonWidget.builder(toggleLabel(), b -> {
                    scaresEnabled = !scaresEnabled;
                    b.setMessage(toggleLabel());
                    ClientPlayNetworking.send(new ScaresPrefPayload(scaresEnabled));
                })
                .dimensions(centerX - 100, this.height / 2 - 10, 200, 20)
                .build();
        this.addDrawableChild(toggleButton);

        // ACCEPT TERMS: the only door out; remembers the answer per machine
        this.addDrawableChild(ButtonWidget.builder(Text.literal("ACCEPT TERMS"), b -> accept())
                .dimensions(centerX - 100, this.height - 40, 200, 20)
                .build());
    }

    private Text toggleLabel() {
        return Text.literal("Jumpscares & scares: " + (scaresEnabled ? "ON" : "OFF"));
    }

    private void accept() {
        ClientAcceptedState.setAccepted(true);
        ClientPlayNetworking.send(new ScaresPrefPayload(scaresEnabled));
        this.close();
    }

    @Override
    public void close() {
        // ESC or the button both count as an answer; never reopen this session
        ClientAcceptedState.setAccepted(true);
        super.close();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // dark curtain over the world: this screen means it, make it feel like a horror cover
        context.fillGradient(0, 0, this.width, this.height, 0xE0100000, 0xF0000000);

        int centerX = this.width / 2;
        int y = this.height / 4;

        // WARNING: big, red, breathing
        float breathe = 1.0f + 0.04f * MathHelper.sin(
                net.minecraft.util.Util.getMeasuringTimeMs() % 1000L / 1000f * 2f * MathHelper.PI);
        context.getMatrices().push();
        context.getMatrices().translate(centerX, y + 10, 0);
        context.getMatrices().scale(breathe, breathe, 1f);
        context.getMatrices().translate(-centerX, -(y + 10), 0);
        int warnWidth = this.textRenderer.getWidth(TITLE);
        context.drawTextWithShadow(this.textRenderer, TITLE, centerX - warnWidth / 2, y, 0xFF5522);
        context.getMatrices().pop();

        y += 14;
        int subWidth = this.textRenderer.getWidth(SUBTITLE);
        context.drawTextWithShadow(this.textRenderer, SUBTITLE, centerX - subWidth / 2, y, 0xFFCCCCCC);

        y += 22;
        drawCentered(context, BODY_1, y, 0xFFAAAAAA);
        y += 10;
        drawCentered(context, BODY_2, y, 0xFFAAAAAA);
        y += 10;
        drawCentered(context, BODY_3, y, 0xFFAAAAAA);
        y += 10;
        drawCentered(context, BODY_4, y, 0xFFAAAAAA);
        y += 10;
        drawCentered(context, BODY_5, y, 0xFFAAAAAA);
        y += 10;
        drawCentered(context, BODY_6, y, 0xFFFF7777);
        y += 10;
        drawCentered(context, BODY_7, y, 0xFFAAAAAA);

        super.render(context, mouseX, mouseY, delta);
    }

    private void drawCentered(DrawContext context, Text text, int y, int color) {
        int w = this.textRenderer.getWidth(text);
        context.drawTextWithShadow(this.textRenderer, text, this.width / 2 - w / 2, y, color);
    }

    /** No escape hatch except the answer itself. */
    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
}
