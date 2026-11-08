package com.itwastall.client.ending;

import com.itwastall.client.sound.CorruptedMusic;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.MusicType;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

/**
 * THE ENDING: black, the story typed line by line, IT WAS TALL. in red, then the credits rise. The game's own credits
 * music plays (never corrupted). ESC goes back to the world.
 */
public class EndingScreen extends Screen {
    private static final String[] STORY = {
            "The fire burned until morning.",
            "When the sun came up, the edge of the forest was empty.",
            "No footsteps. No knocking. No eyes between the trees.",
            "Whatever it was, it is gone.",
            "",
            "But some nights, when the wind stops,",
            "you still look at the treeline."};
    private static final String[] CREDITS = {
            "IT WAS TALL", "", "a horror mod for Minecraft", "", "", "made by vcbgf5", "", "", "Thank you for playing.", "",
            "It is gone.", "", "", "", "Press ESC"};
    private static final long PER_CHAR = 45, LINE_PAUSE = 900;
    private long start;
    private SoundInstance music;

    public EndingScreen() {
        super(Text.literal("The End"));
    }

    @Override
    protected void init() {
        if (start == 0) {
            start = Util.getMeasuringTimeMs();
            CorruptedMusic.forced = false;
            CorruptedMusic.stop();
            MinecraftClient c = MinecraftClient.getInstance();
            c.getMusicTracker().stop();
            music = PositionedSoundInstance.music(MusicType.CREDITS.getSound().value());
            c.getSoundManager().play(music);
        }
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xFF000000);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        long el = Util.getMeasuringTimeMs() - start - 1500;
        int y0 = height / 2 - STORY.length * 7 - 20;
        long at = 0;
        for (int i = 0; i < STORY.length; i++) {
            String line = STORY[i];
            long len = line.length() * PER_CHAR;
            if (el < at) break;
            int chars = (int) Math.min(line.length(), (el - at) / PER_CHAR);
            ctx.drawCenteredTextWithShadow(textRenderer, line.substring(0, chars), width / 2, y0 + i * 14, i >= 5 ? 0xFF9C8E78 : 0xFFE8D9B8);
            at += len + LINE_PAUSE;
        }
        long titleAt = at + 600;
        if (el >= titleAt) {
            float a = Math.min(1f, (el - titleAt) / 1500f);
            int alpha = Math.max(4, Math.round(a * 255)) << 24;
            ctx.getMatrices().push();
            ctx.getMatrices().translate(width / 2f, y0 + STORY.length * 14 + 24, 0);
            ctx.getMatrices().scale(3f, 3f, 1f);
            ctx.drawCenteredTextWithShadow(textRenderer, "IT WAS TALL.", 0, 0, alpha | 0xC8281E);
            ctx.getMatrices().pop();
        }
        long creditsAt = titleAt + 5000;
        if (el >= creditsAt) {
            float scroll = (el - creditsAt) / 40f;                  // 25 px a second
            for (int i = 0; i < CREDITS.length; i++) {
                int y = (int) (height + 20 + i * 14 - scroll);
                if (y < -10 || y > height + 10) continue;
                int color = i == 0 ? 0xFFC8281E : i == CREDITS.length - 1 ? 0xFF9C8E78 : 0xFFE8D9B8;
                ctx.drawCenteredTextWithShadow(textRenderer, CREDITS[i], width / 2, y, color);
            }
        }
    }

    @Override
    public void removed() {
        if (music != null && client != null) client.getSoundManager().stop(music);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
