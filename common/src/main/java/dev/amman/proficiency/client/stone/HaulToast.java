package dev.amman.proficiency.client.stone;

import dev.amman.proficiency.client.ClickFx;
import dev.amman.proficiency.client.SkillIcons;
import dev.amman.proficiency.client.SkillPalette;
import dev.amman.proficiency.haul.HaulSummary;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;

/**
 * Today's Haul at login (1.44.0): "Last session: 1,240 XP, 6 levels" and "Press K for your haul", the key being
 * the skills key while the toast is up. A small stone tablet under MODERN, the classic panel under CLASSIC, drawn
 * with fills (no texture), so the same source runs on 1.20.1 and 1.21.1. Shared with Forge ({@code copySharedHearth}).
 */
public final class HaulToast implements Toast {

    /** How long it stays; the skills key opens the haul for as long (and a little longer, for a slow join). */
    public static final long DURATION_MS = 10000L;

    private final Component title;
    private final Component line;
    private final int iconSkill;

    public HaulToast(HaulSummary summary, Component key) {
        this.title = Component.translatable("proficiency.haul.toast.title",
                String.format(java.util.Locale.ROOT, "%,d", Math.round(summary.totalXp())), summary.totalLevels());
        this.line = Component.translatable("proficiency.haul.toast.line", key);
        this.iconSkill = summary.rows().isEmpty() ? -1 : summary.rows().get(0).skill();
    }

    public static void show(Minecraft mc, HaulSummary summary, Component key) {
        mc.getToasts().addToast(new HaulToast(summary, key));
    }

    @Override
    public int width() {
        Minecraft mc = Minecraft.getInstance();
        int text = Math.max(mc.font.width(title), mc.font.width(line)) + (iconSkill >= 0 ? 40 : 20);
        int allowed = Math.max(160, mc.getWindow().getGuiScaledWidth() - 8);
        return Math.min(Math.max(160, text), allowed);
    }

    @Override
    public Toast.Visibility render(GuiGraphics g, ToastComponent parent, long shownFor) {
        Minecraft mc = Minecraft.getInstance();
        int w = width();
        int h = height();
        boolean modern = SkillstoneScreen.modern();
        if (modern) {
            Stone.block(g, 0, 0, w, h, 4, 2, false, Stone.shade(Stone.STONE, 1.0f));
            Stone.block(g, 3, 3, w - 6, h - 6, 3, 1, true, Stone.shade(Stone.STONE, 0.62f));
        } else {
            g.fill(0, 0, w, h, SkillPalette.PANEL);
            g.fill(0, 0, w, 1, SkillPalette.PANEL_BORDER);
            g.fill(0, h - 1, w, h, SkillPalette.PANEL_BORDER);
            g.fill(0, 0, 1, h, SkillPalette.PANEL_BORDER);
            g.fill(w - 1, 0, w, h, SkillPalette.PANEL_BORDER);
        }
        int x = 10;
        if (iconSkill >= 0) {
            SkillIcons.draw(g, dev.amman.proficiency.skill.Skill.VALUES[iconSkill], 8, (h - 16) / 2, 16, 255);
            x = 30;
        }
        int room = w - x - 8;
        int titleC = modern ? Stone.GOLD : 0xFFF2D98A;
        int lineC = modern ? Stone.INK : SkillPalette.TEXT;
        g.drawString(mc.font, Stone.fit(mc.font, title.getString(), room), x, 7, titleC, modern);
        g.drawString(mc.font, Stone.fit(mc.font, line.getString(), room), x, 18, lineC, modern);
        ClickFx.toastBurst(g, w, h, shownFor, 7, 1, 0xF2D98A);
        return shownFor >= DURATION_MS ? Toast.Visibility.HIDE : Toast.Visibility.SHOW;
    }
}
