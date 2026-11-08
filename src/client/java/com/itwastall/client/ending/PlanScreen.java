package com.itwastall.client.ending;

import com.itwastall.ItWasTall;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** THE RITUAL PLAN: the sketch (top view and side view) and, under it, what to do — in the hand of whoever drew it. */
public class PlanScreen extends Screen {
    private static final Identifier SKETCH = ItWasTall.id("textures/gui/ritual_plan.png");
    private static final String[] STEPS = {
            "1. A floor of Oldrock Bricks, three by three.",
            "2. A campfire in the middle of it. It must burn.",
            "3. Four Ritual Pillars, two high, three blocks from the fire.",
            "4. A lit candle on every pillar.",
            "5. At night, give the fire the Hollow Heart.",
            "Then keep it all burning until dawn. It will try to stop you."};

    public PlanScreen() {
        super(Text.literal("Ritual Plan"));
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        float s = Math.min(1f, Math.min((height - 84) / 192f, (width - 20) / 256f));
        int w = Math.round(256 * s), h = Math.round(192 * s);
        int x = (width - w) / 2, y = 8;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y, 0);
        ctx.getMatrices().scale(s, s, 1f);
        ctx.drawTexture(SKETCH, 0, 0, 0, 0, 256, 192, 256, 192);
        ctx.drawText(textRenderer, "THE RITUAL", 12, 10, 0xFF5A1A12, false);
        ctx.drawText(textRenderer, "from above", 14, 28, 0xFF6B5638, false);
        ctx.drawText(textRenderer, "from the side", 136, 28, 0xFF6B5638, false);
        ctx.getMatrices().pop();
        int ty = y + h + 6;
        for (int i = 0; i < STEPS.length; i++) {
            int color = i == STEPS.length - 1 ? 0xFFC8281E : 0xFFE8D9B8;
            ctx.drawCenteredTextWithShadow(textRenderer, STEPS[i], width / 2, ty + i * 11, color);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
