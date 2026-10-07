package dev.amman.proficiency.client;

import dev.amman.proficiency.config.ProficiencyClientConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Mods > Proficiency > Config: the client file ({@code proficiency-client.toml}), one row per entry
 * under its section header, with the same labels and tooltips as master. NeoForge builds this
 * screen itself from the spec; Forge 1.20.1 has no such screen, so it is drawn here. Switches are
 * On/Off buttons, numbers are sliders in their allowed range. Done saves the file.
 */
public final class ClientConfigScreen extends Screen {

    private static final String LANG = "proficiency.configuration.";
    private static final int ROW = 22;
    private static final int WIDTH = 240;

    private final Screen parent;
    private final List<Header> headers = new ArrayList<>();
    private final List<AbstractWidget> rows = new ArrayList<>();
    private int scroll;
    private int contentHeight;

    private record Header(Component text, int y) {
    }

    public ClientConfigScreen(Screen parent) {
        super(Component.translatable(LANG + "section.proficiency.client.toml.title", "Proficiency"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        headers.clear();
        rows.clear();
        int x = (width - WIDTH) / 2;
        int y = 32;
        String section = "";
        for (ProficiencyClientConfig.Entry entry : ProficiencyClientConfig.entries()) {
            if (!entry.section().equals(section)) {
                section = entry.section();
                y += 4;
                headers.add(new Header(Component.translatable(LANG + section), y));
                y += 14;
            }
            AbstractWidget widget = widget(entry, x, y);
            widget.setTooltip(Tooltip.create(Component.translatable(LANG + entry.key() + ".tooltip")));
            rows.add(widget);
            addWidget(widget);
            y += ROW;
        }
        contentHeight = y;
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds((width - 150) / 2, height - 26, 150, 20).build());
        place();
    }

    @SuppressWarnings("unchecked")
    private AbstractWidget widget(ProficiencyClientConfig.Entry entry, int x, int y) {
        Component label = Component.translatable(LANG + entry.key());
        Object current = entry.value().get();
        if (current instanceof Boolean on) {
            ForgeConfigSpec.ConfigValue<Boolean> value = (ForgeConfigSpec.ConfigValue<Boolean>) entry.value();
            return CycleButton.onOffBuilder(on).create(x, y, WIDTH, 20, label,
                    (button, next) -> value.set(next));
        }
        boolean whole = current instanceof Integer;
        double number = ((Number) current).doubleValue();
        return new Slider(x, y, label, entry, whole, number);
    }

    private final class Slider extends AbstractSliderButton {

        private final Component label;
        private final ProficiencyClientConfig.Entry entry;
        private final boolean whole;

        Slider(int x, int y, Component label, ProficiencyClientConfig.Entry entry, boolean whole, double number) {
            super(x, y, WIDTH, 20, Component.empty(),
                    (number - entry.min()) / Math.max(1e-9, entry.max() - entry.min()));
            this.label = label;
            this.entry = entry;
            this.whole = whole;
            updateMessage();
        }

        private double current() {
            double raw = entry.min() + value * (entry.max() - entry.min());
            return whole ? Math.round(raw) : Math.round(raw * 1000.0) / 1000.0;
        }

        @Override
        protected void updateMessage() {
            double now = current();
            setMessage(Component.empty().append(label).append(": ")
                    .append(whole ? Integer.toString((int) now) : String.format(Locale.ROOT, "%.3f", now)));
        }

        @Override
        @SuppressWarnings("unchecked")
        protected void applyValue() {
            if (whole) {
                ((ForgeConfigSpec.ConfigValue<Integer>) entry.value()).set((int) current());
            } else {
                ((ForgeConfigSpec.ConfigValue<Double>) entry.value()).set(current());
            }
        }
    }

    /** Moves every row by the scroll offset; rows out of the visible band are hidden. */
    private void place() {
        int top = 28;
        int bottom = height - 32;
        int index = 0;
        for (AbstractWidget widget : rows) {
            int baseY = baseY(index++);
            widget.setY(baseY - scroll);
            widget.visible = widget.getY() >= top && widget.getY() + 20 <= bottom;
        }
    }

    private int baseY(int index) {
        int y = 32;
        String section = "";
        int i = 0;
        for (ProficiencyClientConfig.Entry entry : ProficiencyClientConfig.entries()) {
            if (!entry.section().equals(section)) {
                section = entry.section();
                y += 18;
            }
            if (i++ == index) {
                return y;
            }
            y += ROW;
        }
        return y;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int max = Math.max(0, contentHeight - (height - 64));
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(delta) * ROW));
        place();
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        for (Header header : headers) {
            int y = header.y() - scroll;
            if (y >= 28 && y <= height - 40) {
                graphics.drawString(font, header.text(), (width - WIDTH) / 2, y, 0xFFD27F);
            }
        }
        for (AbstractWidget widget : rows) {
            widget.render(graphics, mouseX, mouseY, partialTick);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        ProficiencyClientConfig.SPEC.save();
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }
}
