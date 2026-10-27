package dev.amman.proficiency.client.stone;

import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.client.ClickFx;
import dev.amman.proficiency.client.ClientCodex;
import dev.amman.proficiency.client.MasteryStars;
import dev.amman.proficiency.client.SkillIcons;
import dev.amman.proficiency.perk.Expertise;
import dev.amman.proficiency.skill.Codex;
import dev.amman.proficiency.skill.CodexService;
import dev.amman.proficiency.skill.Mastery;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillCategory;
import dev.amman.proficiency.skill.SkillMath;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Mastery and Titles as one slab page with two tabs (1.32.0). Both list every skill under its category in a
 * sunk slate, the skill's icon in a socket. Mastery: the level, the stars as glyphs (lit gold, the rest dark),
 * the rider's line; a row opens the skill's Face. Titles: the Codex count and the four milestone sockets (10, 25,
 * 50, 100 kinds), lit when earned, the shown title ringed and burning; a click shows that title (a gold burst
 * and a knock), a locked one is refused with what earns it.
 */
public final class StoneLedgerScreen extends StoneSheet {

    private static final int ROW_HIT = 1;
    private static final int CELL = 2;
    private static final int CLEAR = 3;

    /** One list row: a category heading (skill null) or a skill. */
    private record Entry(SkillCategory category, Skill skill) {
    }

    private static final List<Entry> ENTRIES = build();

    private static List<Entry> build() {
        List<Entry> out = new ArrayList<>();
        for (SkillCategory c : SkillCategory.values()) {
            boolean head = false;
            for (Skill s : Skill.VALUES) {
                if (s.category() == c) {
                    if (!head) {
                        out.add(new Entry(c, null));
                        head = true;
                    }
                    out.add(new Entry(c, s));
                }
            }
        }
        return List.copyOf(out);
    }

    private int tab;
    private int scroll;
    private int viewTop;
    private int viewH;
    private int rowH;

    public StoneLedgerScreen(Screen parent, boolean titles) {
        super(Component.translatable("proficiency.stone.ledger"), parent);
        this.tab = titles ? 1 : 0;
    }

    @Override
    String crumb() {
        return SkillstoneScreen.tr("proficiency.stone.back");
    }

    @Override
    String[] tabs() {
        return new String[] {SkillstoneScreen.tr("proficiency.stone.tab.mastery"), SkillstoneScreen.tr("proficiency.stone.tab.titles")};
    }

    @Override
    int tab() {
        return tab;
    }

    @Override
    void selectTab(int index, long now) {
        tab = index;
        scroll = 0;
    }

    @Override
    int maxWidth() {
        return 520;
    }

    @Override
    int maxHeight() {
        // Taller than the other pages: the 44 rows scroll less.
        return 400;
    }

    @Override
    void layout() {
        rowH = small ? 16 : 20;
        viewTop = iy0 + (small ? 30 : 38);
        viewH = Math.max(rowH * 3, iy1 - (small ? 8 : 14) - viewTop);
    }

    private PlayerSkills skills() {
        Player player = this.minecraft == null ? null : this.minecraft.player;
        return player == null ? null : ProficiencyAttachments.of(player);
    }

    // ---- Render ---------------------------------------------------------------------------------

    @Override
    void body(GuiGraphics g, long now, int mx, int my) {
        PlayerSkills skills = skills();
        if (skills == null) {
            return;
        }
        int pad = small ? 8 : 14;
        int x0 = ix0 + pad;
        int x1 = ix1 - pad;
        int top = iy0 + (small ? 5 : 9);
        if (tab == 0) {
            int stars = skills.totalStars();
            int max = Skill.VALUES.length * Mastery.MAX_STARS;
            head(g, SkillstoneScreen.tr("proficiency.stone.mastery_head").toUpperCase(java.util.Locale.ROOT),
                    SkillstoneScreen.tr("proficiency.stone.stars", stars, max), x0, x1, top);
            Stone.text(g, this.font, Stone.fit(this.font, SkillstoneScreen.tr("proficiency.stone.mastery_expertise",
                    Expertise.pointsFromStars(stars), Expertise.MAX_FROM_STARS), x1 - x0), x0, top + (small ? 11 : 13), Stone.DIM);
        } else {
            Codex.Title picked = Codex.parse(ClientCodex.picked());
            String showing = picked == null ? SkillstoneScreen.tr("proficiency.command.title_none")
                    : SkillstoneScreen.tr("proficiency.command.title_current", CodexService.titleName(picked).getString());
            String clear = SkillstoneScreen.tr("proficiency.codex.picker.clear");
            int cw = this.font.width(clear) + 16;
            int ch = small ? 14 : 18;
            Stone.text(g, this.font, Stone.fit(this.font, showing, x1 - x0 - cw - 8), x0, top, picked == null ? Stone.DIM : Stone.GOLD);
            Stone.text(g, this.font, Stone.fit(this.font, SkillstoneScreen.tr("proficiency.codex.picker.hint"), x1 - x0 - cw - 8), x0,
                    top + (small ? 11 : 13), Stone.DIM);
            button(g, now, mx, my, CLEAR, 0, x1 - cw, top, cw, ch, clear, picked != null, false);
        }
        Stone.well(g, x0 - 4, viewTop, x1 - x0 + 8, viewH);
        int content = ENTRIES.size() * rowH + 8;
        scroll = Math.max(0, Math.min(scroll, content - viewH));
        Stone.scissor(g, x0 - 2, viewTop + 3, x1 + 4, viewTop + viewH - 3);
        boolean inView = my >= viewTop + 3 && my < viewTop + viewH - 3;
        int y = viewTop + 5 - scroll;
        for (Entry e : ENTRIES) {
            if (y + rowH > viewTop && y < viewTop + viewH) {
                if (e.skill() == null) {
                    heading(g, e.category(), x0 + 2, x1 - 8, y);
                } else if (tab == 0) {
                    masteryRow(g, skills, e.skill(), now, x0 + 2, x1 - 8, y, inView ? mx : -1, inView ? my : -1);
                } else {
                    titleRow(g, e.skill(), now, x0 + 2, x1 - 8, y, inView ? mx : -1, inView ? my : -1);
                }
            }
            y += rowH;
        }
        g.disableScissor();
        scrollbar(g, x1 - 2, viewTop + 4, viewH - 8, scroll, content);
    }

    private void heading(GuiGraphics g, SkillCategory c, int x0, int x1, int y) {
        String name = SkillstoneScreen.tr(c.translationKey()).toUpperCase(java.util.Locale.ROOT);
        int ty = y + (rowH - 8) / 2 + 1;
        Stone.text(g, this.font, name, x0, ty, Stone.HEAD);
        int gx = x0 + this.font.width(name) + 6;
        if (gx < x1) {
            g.fill(gx, ty + 4, x1, ty + 5, Stone.argb(255, 0x161110));
            g.fill(gx, ty + 5, x1, ty + 6, Stone.argb(90, 0x968278));
        }
    }

    /** The icon in a socket and the name, shared by both tabs; returns where the name ends. */
    private int lead(GuiGraphics g, Skill s, boolean lit, int x0, int y, int room, int colour, boolean hov) {
        int ss = rowH - 2;
        Stone.socket(g, x0 + 4, y, ss, ss, lit ? 0.5f : 0f, false, hov);
        int is = ss >= 18 ? 16 : 8;
        if (lit || hov) {
            Stone.icon(g, SkillIcons.texture(s), x0 + 4 + (ss - is) / 2, y + (ss - is) / 2, is, 1f);
        } else {
            Stone.greyIcon(g, SkillIcons.texture(s), x0 + 4 + (ss - is) / 2, y + (ss - is) / 2, is, 0.5f);
        }
        String name = SkillstoneScreen.tr(s.translationKey());
        Stone.text(g, this.font, Stone.fit(this.font, name, room), x0 + ss + 10, y + (ss - 8) / 2, colour);
        return x0 + ss + 10 + room;
    }

    private void masteryRow(GuiGraphics g, PlayerSkills skills, Skill s, long now, int x0, int x1, int y, int mx, int my) {
        boolean hov = mx >= x0 && mx < x1 && my >= y && my < y + rowH - 1;
        int level = skills.level(s);
        boolean max = level >= SkillMath.MAX_LEVEL;
        int stars = max ? MasteryStars.shown(skills, s) : 0;
        if (hov) {
            Stone.fillRound(g, x0, y - 1, x1 - x0, rowH, 3, Stone.argb(30, 0xFFF0DC));
        }
        int starW = this.font.width(MasteryStars.text(Mastery.MAX_STARS));
        int levelW = this.font.width("100") + 8;
        int nameRoom = Math.max(20, Math.min(small ? 90 : 120, x1 - x0 - starW - levelW - 40));
        int end = lead(g, s, max, x0, y, nameRoom, max ? Stone.INK : Stone.DIM, hov);
        int ty = y + (rowH - 2 - 8) / 2;
        Stone.textRight(g, this.font, Integer.toString(level), end + levelW, ty, max ? Stone.GOLD : Stone.DIM);
        int sx0 = end + levelW + 10;
        if (max) {
            if (stars > 0) {
                Stone.light(g, sx0 + MasteryStars.width(stars) / 2, ty + 4, MasteryStars.width(stars) / 2 + 8, 8, Stone.WARM, 0.5f);
            }
            g.drawString(this.font, MasteryStars.text(stars), sx0, ty, Stone.argb(255, MasteryStars.GOLD), true);
            g.drawString(this.font, MasteryStars.text(Mastery.MAX_STARS - stars), sx0 + MasteryStars.width(stars), ty,
                    Stone.argb(255, 0x3A302C), false);
            String rider = MasteryStars.riderLine(skills, s);
            int rx = sx0 + starW + 10;
            if (!rider.isEmpty() && rx + 30 < x1) {
                Stone.text(g, this.font, Stone.fit(this.font, stripStars(rider), x1 - rx), rx, ty, Stone.DIM);
            }
        } else {
            // Below 100: how far to the stars, as a thin groove.
            Stone.groove(g, sx0, Math.min(x1, sx0 + starW + 40), ty + 4, level / (float) SkillMath.MAX_LEVEL, 3, false);
        }
        int top = Math.max(y, viewTop + 3);
        int bottom = Math.min(y + rowH - 1, viewTop + viewH - 3);
        if (bottom > top) {
            hits.add(new int[] {ROW_HIT, s.ordinal(), x0, top, x1 - x0, bottom - top});
        }
        if (hov) {
            tooltip.add(Component.translatable(s.translationKey()).copy().withStyle(ChatFormatting.WHITE));
            if (max) {
                tooltip.addAll(MasteryStars.riderTooltip(skills, s));
            } else {
                tooltip.add(Component.literal(SkillstoneScreen.tr("proficiency.stone.mastery_locked")).withStyle(ChatFormatting.GRAY));
            }
            tooltip.add(Component.literal(SkillstoneScreen.tr("proficiency.stone.open_face")).withStyle(ChatFormatting.YELLOW));
        }
    }

    /** The rider line starts with the star glyphs; the row draws its own. */
    private static String stripStars(String rider) {
        int i = 0;
        while (i < rider.length() && (rider.charAt(i) == MasteryStars.GLYPH || rider.charAt(i) == ' ')) {
            i++;
        }
        return rider.substring(i);
    }

    private void titleRow(GuiGraphics g, Skill s, long now, int x0, int x1, int y, int mx, int my) {
        int found = ClientCodex.kinds(s);
        int n = Codex.MILESTONES.length;
        int cw = small ? 24 : 30;
        int gap = small ? 4 : 6;
        int cellsW = n * cw + (n - 1) * gap;
        int cx0 = x1 - cellsW;
        int countW = this.font.width("100") + 8;
        lead(g, s, found > 0, x0, y, Math.max(20, cx0 - countW - 8 - (x0 + rowH + 8)), found > 0 ? Stone.INK : Stone.DIM,
                mx >= x0 && mx < cx0 && my >= y && my < y + rowH - 1);
        int ty = y + (rowH - 2 - 8) / 2;
        Stone.textRight(g, this.font, Integer.toString(found), cx0 - 8, ty, Stone.DIM);
        for (int c = 0; c < n; c++) {
            int milestone = Codex.MILESTONES[c];
            Codex.Title cell = new Codex.Title(s, milestone);
            boolean earned = found >= milestone;
            boolean picked = cell.id().equals(ClientCodex.picked());
            int x = cx0 + c * (cw + gap);
            int h = rowH - 2;
            boolean over = mx >= x && mx < x + cw && my >= y && my < y + h;
            Object key = "cell" + s.ordinal() + ":" + c;
            click.begin(g, key, x, y, cw, h, now);
            Stone.socket(g, x, y, cw, h, picked ? 1f : earned ? 0.4f : 0f, picked, over);
            if (picked) {
                int frame = (int) ((now / 100L + c) % 8);
                int in = h - 8 >= 14 ? 14 : 10;
                Stone.blitAlpha(g, Stone.tex("flame_" + in), x + (cw - in) / 2, y + (h - in) / 2, in, in, 0f, frame * in, in, in * 8, 0.55f);
            }
            Stone.textCentred(g, this.font, Integer.toString(milestone), x + cw / 2 + 1, y + (h - 8) / 2,
                    picked ? Stone.GOLD : earned ? Stone.INK : Stone.LOCK);
            click.end(g, key, x, y, cw, h, now);
            int top = Math.max(y, viewTop + 3);
            int bottom = Math.min(y + h, viewTop + viewH - 3);
            if (bottom > top) {
                hits.add(new int[] {CELL, s.ordinal() * 8 + c, x, top, cw, bottom - top});
            }
            if (over) {
                tooltip.add(CodexService.titleName(cell).copy().withStyle(earned ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY));
                tooltip.add(Component.translatable("proficiency.codex.picker.needs", milestone,
                        Component.translatable(s.translationKey())).withStyle(ChatFormatting.GRAY));
                tooltip.add(ClientCodex.line(found).copy().withStyle(ChatFormatting.DARK_GRAY));
                tooltip.add(Component.translatable(picked ? "proficiency.codex.picker.picked"
                        : earned ? "proficiency.codex.picker.click" : "proficiency.codex.picker.locked")
                        .withStyle(picked ? ChatFormatting.GOLD : earned ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
            }
        }
    }

    // ---- Input ----------------------------------------------------------------------------------

    @Override
    void hit(int[] h, long now, boolean shift) {
        switch (h[0]) {
            case ROW_HIT -> {
                if (tab != 0 || this.minecraft == null) {
                    return;
                }
                Skill s = Skill.VALUES[h[1]];
                SkillstoneScreen.sound(SoundEvents.STONE_PLACE, 0.9f, 0.7f);
                this.minecraft.setScreen(new SkillstoneScreen(s));
            }
            case CELL -> {
                Skill s = Skill.VALUES[h[1] / 8];
                int c = h[1] % 8;
                int milestone = Codex.MILESTONES[c];
                Object key = "cell" + s.ordinal() + ":" + c;
                if (ClientCodex.kinds(s) < milestone) {
                    refuse(key, h, SkillstoneScreen.tr("proficiency.codex.picker.needs", milestone, SkillstoneScreen.tr(s.translationKey())), now);
                    return;
                }
                String id = new Codex.Title(s, milestone).id();
                boolean fresh = !id.equals(ClientCodex.picked());
                ClientCodex.pick(id);
                // A new title bursts in gold and knocks the slab; picking the one shown only blips.
                click.success(key, h[2], h[3], h[4], h[5], Stone.GOLD, fresh ? 1 : 0, c / (float) Math.max(1, Codex.MILESTONES.length - 1), now);
                if (fresh) {
                    shake(now + dev.amman.proficiency.client.ClickFxMath.MIN_CHARGE_MS, 2f);
                    SkillstoneScreen.sound(SoundEvents.FLINTANDSTEEL_USE, 1.0f + 0.1f * c, 0.5f);
                }
            }
            case CLEAR -> {
                if (Codex.parse(ClientCodex.picked()) == null) {
                    refuse(key(h), h, SkillstoneScreen.tr("proficiency.command.title_none"), now);
                    return;
                }
                ClientCodex.pick("");
                click.success(key(h), h[2], h[3], h[4], h[5], Stone.LOCK, 0, 1f, now);
                ClickFx.clickIfOff();
            }
            default -> {
            }
        }
    }

    @Override
    boolean scroll(double mx, double my, double delta) {
        int before = scroll;
        scroll = Math.max(0, scroll - (int) Math.signum(delta) * rowH * 2);
        return scroll != before;
    }

    /** For the layout demo: scroll to {@code to} pixels. */
    public void scrollTo(int to) {
        scroll = to;
    }
}
