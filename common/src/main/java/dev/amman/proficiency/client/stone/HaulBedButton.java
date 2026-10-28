package dev.amman.proficiency.client.stone;

import dev.amman.proficiency.client.ClickFx;
import dev.amman.proficiency.client.SkillIcons;
import dev.amman.proficiency.client.SkillPalette;
import dev.amman.proficiency.haul.HaulBedMath;
import dev.amman.proficiency.haul.HaulSummary;
import dev.amman.proficiency.skill.Skill;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.function.Supplier;

/**
 * The "Today's Haul" button on the bed's chat screen (1.48.0): lying down no longer opens the card by itself, this
 * button does. It sits just above "Leave Bed" ({@link HaulBedMath#place}), carries the top skill's icon, and breathes
 * a soft glow so a new haul is noticed. It is drawn only while {@code state} holds an unseen haul, and takes a click
 * only then. A carved stone block under MODERN, a classic panel under CLASSIC. Shared with Forge
 * ({@code copySharedHearth}): no version-specific API.
 */
public final class HaulBedButton extends AbstractWidget {

    private static final long FADE_MS = 280;

    private final Supplier<HaulSummary> state;
    private final Runnable onOpen;
    private HaulSummary shown;
    private long shownAt;

    public HaulBedButton(int screenW, int screenH, Supplier<HaulSummary> state, Runnable onOpen) {
        super(0, 0, 10, HaulBedMath.BUTTON_H, Component.translatable("proficiency.haul.bed"));
        this.state = state;
        this.onOpen = onOpen;
        this.active = false;
        place(screenW, screenH);
    }

    private void place(int screenW, int screenH) {
        Minecraft mc = Minecraft.getInstance();
        int[] r = HaulBedMath.place(screenW, screenH, mc.font.width(getMessage()));
        if (r == null) {
            this.visible = false;
            return;
        }
        setX(r[0]);
        setY(r[1]);
        setWidth(r[2]);
    }

    @Override
    public void onClick(double mx, double my) {
        onOpen.run();
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
        HaulSummary now = state.get();
        this.active = now != null;
        if (now == null) {
            shown = null;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        long clock = SkillstoneScreen.clock();
        if (shown != now) {
            shown = now;
            shownAt = clock;
            if (ClickFx.motion()) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.3f, 0.5f));
            }
        }
        float in = Math.min(1f, (clock - shownAt) / (float) FADE_MS);
        float pulse = ClickFx.motion() ? HaulBedMath.pulse(clock) : 0.55f;
        boolean hover = isHoveredOrFocused();
        int x = getX();
        int y = getY() + Math.round((1f - in) * 6f);
        int w = getWidth();
        int h = getHeight();
        boolean modern = SkillstoneScreen.modern();
        int gold = modern ? Stone.GOLD : 0xFFF2D98A;
        if (modern) {
            Stone.lightRect(g, x, y, w, h, Stone.WARM, 5, 0.5f * pulse * in);
            Stone.block(g, x, y, w, h, 4, 2, false, Stone.shade(Stone.STONE, hover ? 1.22f : 1.05f));
            Stone.block(g, x + 3, y + 3, w - 6, h - 6, 3, 1, true, Stone.shade(Stone.STONE, hover ? 0.7f : 0.58f));
        } else {
            int border = blend(SkillPalette.PANEL_BORDER, gold, hover ? 1f : pulse * 0.75f);
            g.fill(x, y, x + w, y + h, SkillPalette.PANEL);
            if (hover) {
                g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0x22FFFFFF);
            }
            g.fill(x, y, x + w, y + 1, border);
            g.fill(x, y + h - 1, x + w, y + h, border);
            g.fill(x, y, x + 1, y + h, border);
            g.fill(x + w - 1, y, x + w, y + h, border);
        }
        int textX = x + 6;
        if (!now.rows().isEmpty()) {
            SkillIcons.draw(g, Skill.VALUES[now.rows().get(0).skill()], x + 5, y + (h - 16) / 2, 16);
            textX = x + 25;
        }
        int room = x + w - 5 - textX;
        String label = Stone.fit(mc.font, getMessage().getString(), room);
        int lx = textX + Math.max(0, (room - mc.font.width(label)) / 2);
        g.drawString(mc.font, label, lx, y + (h - 8) / 2, 0xFF000000 | (modern ? (hover ? 0xFFFFFF : gold) : gold), modern);
    }

    private static int blend(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int out = 0xFF000000;
        for (int shift = 16; shift >= 0; shift -= 8) {
            int ca = (a >> shift) & 0xFF;
            int cb = (b >> shift) & 0xFF;
            out |= Math.round(ca + (cb - ca) * t) << shift;
        }
        return out;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        out.add(NarratedElementType.TITLE, getMessage());
    }
}
