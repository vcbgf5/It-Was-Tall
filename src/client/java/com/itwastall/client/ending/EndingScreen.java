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
 * THE ENDING: black, the story typed line by line, IT WAS TALL. in red, then the credits rise over dark dirt like the
 * game's own: who made it, and the Sleeper talking to the player (red the Sleeper, blue the player). The game's own
 * credits music plays (never corrupted). ESC goes back to the world.
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
    /** Lines of the credits: "S:" the Sleeper speaks, "P:" the player, plain = a title or nothing. */
    private static final String[] CREDITS = {
            "IT WAS TALL", "", "a horror mod for Minecraft", "", "",
            "Mixiki made the mod by himself.", "", "", "", "",
            "S:You are awake.",
            "P:Who are you?",
            "S:I was the one in the bed. In the fifteenth room.",
            "S:I had your face.",
            "P:Something ran after me in those halls.",
            "S:That was the Old One. I only slept.",
            "S:I slept, so that you could dream.", "",
            "P:Is it over?",
            "S:The fire burned until morning. The chains took it up.",
            "S:Yes. It is over.", "",
            "P:What was it?",
            "S:Something tall. Something that watched you build",
            "S:your little house, and wanted to be let in.",
            "S:It knocked because you had a door.",
            "S:It waited because you had nights.", "",
            "P:Why me?",
            "S:Because you looked back.", "",
            "P:Will it come back?",
            "S:Only if you call it. Some do.",
            "S:The silence is very loud, after.", "",
            "P:And you?",
            "S:I will lie down again, in a bed you will never find.",
            "S:I will dream your world for you,",
            "S:so you can keep walking in it.", "",
            "P:I don't want to forget.",
            "S:You won't. Every time the wind stops at the edge of a forest,",
            "S:you will look at the trees.",
            "S:And the trees will be only trees.", "",
            "P:...thank you.", "",
            "S:Wake up, %name%.",
            "S:Wake up.", "", "", "", "",
            "Thank you for playing.", "", "", "", "Press ESC"};
    private static final long PER_CHAR = 45, LINE_PAUSE = 900;
    private static final net.minecraft.util.Identifier DIRT = net.minecraft.util.Identifier.ofVanilla("textures/block/dirt.png");
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
            // the credits roll over dark dirt, like the game's own credits
            float fade = Math.min(1f, (el - creditsAt) / 1500f);
            float scroll = (el - creditsAt) / 40f;                  // 25 px a second
            ctx.setShaderColor(0.25f * fade, 0.25f * fade, 0.25f * fade, 1f);
            ctx.drawTexture(DIRT, 0, 0, 0f, scroll, width, height, 32, 32);
            ctx.setShaderColor(1f, 1f, 1f, 1f);
            String name = client != null && client.player != null ? client.player.getName().getString() : "player";
            for (int i = 0; i < CREDITS.length; i++) {
                int y = (int) (height + 20 + i * 14 - scroll);
                if (y < -10 || y > height + 10) continue;
                String c = CREDITS[i];
                int color;
                if (c.startsWith("S:")) {
                    c = c.substring(2).replace("%name%", name);
                    color = 0xFFC86A5A;                             // the Sleeper
                } else if (c.startsWith("P:")) {
                    c = c.substring(2);
                    color = 0xFF8FC7D0;                             // the player
                } else {
                    color = i == 0 ? 0xFFC8281E : i == CREDITS.length - 1 ? 0xFF9C8E78 : 0xFFE8D9B8;
                }
                ctx.drawCenteredTextWithShadow(textRenderer, c, width / 2, y, color);
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
