package com.itwastall.client.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.itwastall.ItWasTall;
import com.itwastall.config.ItWasTallConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * THE SETTINGS SCREEN (Mod Menu, or the pause menu): every option of config/itwastall.json, grouped in sections, with
 * the option's own description as a tooltip (assets/itwastall/config_help.json, made by tools/gen_config_help.py).
 * Switches for yes/no, text boxes for numbers (red while not a valid number). Done saves the file.
 * On a multiplayer server the server's own config decides the events; here you change the copy of this computer.
 */
public class ConfigScreen extends Screen {
    private final Screen parent;
    private OptionList list;
    private final List<Option> options = new ArrayList<>();

    public ConfigScreen(@Nullable Screen parent) {
        super(Text.literal("It Was Tall - Settings"));
        this.parent = parent;
    }

    private record Option(String path, String section, String label, String help) {}

    private void loadOptions() {
        options.clear();
        try (InputStream in = ConfigScreen.class.getResourceAsStream("/assets/itwastall/config_help.json")) {
            if (in == null) return;
            JsonArray arr = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray();
            for (JsonElement e : arr) {
                JsonObject o = e.getAsJsonObject();
                options.add(new Option(o.get("path").getAsString(), o.get("section").getAsString(),
                        o.get("label").getAsString(), o.get("help").getAsString()));
            }
        } catch (Exception ex) {
            ItWasTall.LOGGER.warn("Could not read the settings descriptions: {}", ex.toString());
        }
    }

    @Override
    protected void init() {
        if (options.isEmpty()) loadOptions();
        list = new OptionList(client, width, height - 64, 32, 24);
        String section = null;
        for (Option o : options) {
            Target t = Target.of(o.path());
            if (t == null) continue;
            if (!o.section().equals(section)) {
                section = o.section();
                list.add(new SectionEntry(Character.toUpperCase(section.charAt(0)) + section.substring(1)));
            }
            list.add(new OptionEntry(o, t));
        }
        addDrawableChild(list);
        int bw = 100, y = height - 27;
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> {
            ItWasTallConfig.save();
            close();
        }).dimensions(width / 2 + 4, y, bw, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Defaults"), b -> {
            int version = ItWasTallConfig.INSTANCE.configVersion;
            ItWasTallConfig.INSTANCE = new ItWasTallConfig.Values();
            ItWasTallConfig.INSTANCE.configVersion = version;
            clearAndInit();
        }).dimensions(width / 2 - bw - 4, y, bw, 20).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, 8, 0xE8D9B8);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("On a server, the server's own settings decide the events."),
                width / 2, 19, 0x9C8E78);
        OptionEntry hovered = list.hoveredOption(mouseX, mouseY);
        if (hovered != null && !hovered.option.help().isEmpty()) {
            List<OrderedText> lines = textRenderer.wrapLines(Text.literal(hovered.option.help()), 220);
            ctx.drawOrderedTooltip(textRenderer, lines, mouseX, mouseY);
        }
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    // ------------------------------------------------------------------ reflection ----

    /** The field an option path points at ("x" in Values, "activation.x" in a nested section) and its owner. */
    private record Target(Field field, Object owner) {
        static Target of(String path) {
            try {
                Object owner = ItWasTallConfig.INSTANCE;
                String[] parts = path.split("\\.");
                for (int i = 0; i < parts.length - 1; i++) {
                    owner = owner.getClass().getField(parts[i]).get(owner);
                }
                Field f = owner.getClass().getField(parts[parts.length - 1]);
                return new Target(f, owner);
            } catch (ReflectiveOperationException e) {
                return null;
            }
        }

        Object get() {
            try {
                return field.get(owner);
            } catch (IllegalAccessException e) {
                return null;
            }
        }

        /** Sets from text; false when the text is not a valid value for this field. */
        boolean set(String text) {
            try {
                Class<?> t = field.getType();
                if (t == int.class) field.setInt(owner, Integer.parseInt(text.trim()));
                else if (t == long.class) field.setLong(owner, Long.parseLong(text.trim()));
                else if (t == double.class) field.setDouble(owner, Double.parseDouble(text.trim()));
                else if (t == float.class) field.setFloat(owner, Float.parseFloat(text.trim()));
                else if (t == String.class) field.set(owner, text);
                else return false;
                return true;
            } catch (NumberFormatException | IllegalAccessException e) {
                return false;
            }
        }
    }

    // ------------------------------------------------------------------ the list ----

    private class OptionList extends ElementListWidget<Row> {
        OptionList(MinecraftClient client, int width, int height, int y, int itemHeight) {
            super(client, width, height, y, itemHeight);
        }

        void add(Row e) {
            addEntry(e);
        }

        @Override
        public int getRowWidth() {
            return Math.min(360, width - 40);
        }

        @Nullable
        OptionEntry hoveredOption(int mouseX, int mouseY) {
            Row e = getHoveredEntry();
            return e instanceof OptionEntry o && mouseX < getRowLeft() + getRowWidth() / 2 ? o : null;
        }
    }

    private abstract static class Row extends ElementListWidget.Entry<Row> {
    }

    private class SectionEntry extends Row {
        private final Text text;

        SectionEntry(String title) {
            this.text = Text.literal(title).styled(s -> s.withBold(true).withColor(0xC8281E));
        }

        @Override
        public void render(DrawContext ctx, int index, int y, int x, int w, int h, int mouseX, int mouseY, boolean hovered, float delta) {
            TextRenderer tr = MinecraftClient.getInstance().textRenderer;
            ctx.drawTextWithShadow(tr, text, x, y + 8, 0xFFFFFFFF);
            ctx.fill(x, y + 19, x + w, y + 20, 0x55C8281E);
        }

        @Override
        public List<? extends Element> children() {
            return List.of();
        }

        @Override
        public List<? extends Selectable> selectableChildren() {
            return List.of();
        }
    }

    private class OptionEntry extends Row {
        final Option option;
        private final Target target;
        private final ClickableWidget widget;

        OptionEntry(Option option, Target target) {
            this.option = option;
            this.target = target;
            Object value = target.get();
            if (target.field().getType() == boolean.class) {
                widget = ButtonWidget.builder(onOff((Boolean) value), b -> {
                    try {
                        boolean now = !target.field().getBoolean(target.owner());
                        target.field().setBoolean(target.owner(), now);
                        b.setMessage(onOff(now));
                    } catch (IllegalAccessException ignored) {
                    }
                }).dimensions(0, 0, 120, 20).build();
            } else {
                TextFieldWidget field = new TextFieldWidget(MinecraftClient.getInstance().textRenderer, 0, 0, 120, 18, Text.literal(option.label()));
                field.setMaxLength(256);
                field.setText(String.valueOf(value));
                field.setChangedListener(s -> field.setEditableColor(target.set(s) ? 0xE0E0E0 : 0xFF5050));
                widget = field;
            }
        }

        private static Text onOff(boolean on) {
            return Text.literal(on ? "ON" : "OFF").styled(s -> s.withColor(on ? 0x7ED07E : 0xC86A5A));
        }

        @Override
        public void render(DrawContext ctx, int index, int y, int x, int w, int h, int mouseX, int mouseY, boolean hovered, float delta) {
            TextRenderer tr = MinecraftClient.getInstance().textRenderer;
            String label = option.label();
            int max = w - 130;
            if (tr.getWidth(label) > max) label = tr.trimToWidth(label, max - 6) + "...";
            ctx.drawTextWithShadow(tr, label, x, y + 6, hovered ? 0xFFFFFF : 0xD8CBB0);
            widget.setX(x + w - 120);
            widget.setY(y + (widget instanceof TextFieldWidget ? 1 : 0));
            widget.render(ctx, mouseX, mouseY, delta);
        }

        @Override
        public List<? extends Element> children() {
            return List.of(widget);
        }

        @Override
        public List<? extends Selectable> selectableChildren() {
            return List.of(widget);
        }
    }
}
