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
            "S:You're awake. Good. I wasn't sure you would be.",
            "P:You were the one in the bed. You have my face.",
            "S:I know. I'm sorry about that.",
            "S:It's the only face I have left.", "",
            "P:Who are you?",
            "S:Someone who played this world after you.",
            "S:Years from now. Later than you.",
            "S:I came back to this place one night. I built a little house,",
            "S:just like you did. And then I heard the knocking.",
            "S:I didn't make it to dawn.", "",
            "P:So you've been here ever since?",
            "S:In the bed, in the fifteenth room. Asleep.",
            "S:When you slept, I could see you.",
            "S:I watched you plant your first wheat.",
            "S:I watched you run home in the dark.",
            "S:I was so glad someone else had come.", "",
            "P:Why didn't you warn me?",
            "S:I tried. Sometimes the voice on the radio was me.",
            "S:The notes in the chests were me.",
            "S:The voice from the trees was not.", "",
            "P:Is it really gone?",
            "S:The chains took it up. I felt it go,",
            "S:like a door closing somewhere far below.", "",
            "P:Then you're free. Come back with me.",
            "S:I can't. I've been asleep too long.",
            "S:Somewhere, a screen is still glowing in an empty room.",
            "S:Somewhere, my friends stopped waiting for me to answer.", "",
            "P:That's not fair.",
            "S:No. But you kept the fire burning until morning.",
            "S:That's more than I did. That's enough for me.", "",
            "P:What do I do now?",
            "S:Live in it. Build something that isn't a shelter.",
            "S:Leave a window without bars.",
            "S:Call your friends. Walk somewhere just to see it.",
            "S:And one day, when you stop playing, really stop,",
            "S:go outside. The trees there are only trees.", "",
            "P:Will I see you again?",
            "S:Every time you sleep in a bed in this world,",
            "S:I'll be the dream you don't remember.", "",
            "S:Goodnight, %name%. Thank you for staying.",
            "S:Now wake up.", "", "", "", "",
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
