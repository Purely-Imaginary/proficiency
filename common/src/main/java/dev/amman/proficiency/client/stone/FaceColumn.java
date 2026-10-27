package dev.amman.proficiency.client.stone;

import dev.amman.proficiency.client.MasteryStars;
import dev.amman.proficiency.client.SkillIcons;
import dev.amman.proficiency.client.SkillstoneMath;
import dev.amman.proficiency.perk.Synergies;
import dev.amman.proficiency.perk.Synergy;
import dev.amman.proficiency.perk.Talent;
import dev.amman.proficiency.skill.ActiveService;
import dev.amman.proficiency.skill.Mastery;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillMath;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The Face at small scaled sizes (GUI 4 on 1080p, 960x720 at GUI 3, 1280x720 at GUI 4): one column inside
 * the slab, identity header, the four cards and the synergies stacked top to bottom, every line wrapped
 * (never cut with "..."), the column scrolling under a scissor. The numbers (breakpoint, wrap, scroll,
 * thumb) are in {@link SkillstoneMath}; larger sizes keep {@link SkillstoneScreen}'s two-column Face.
 */
final class FaceColumn {

    private static final int LINE = 10;
    private static final int GAP = 5;
    private static final int HEAD_H = 42;
    private static final int SOCKET = 18;
    private static final long REFRESH_MS = 250;
    private static final int[] COLOURS = {Stone.GOLD, Stone.INK, Stone.DIM};

    private record Card(int index, int y, int h, String header, List<String> lines, List<Integer> colours) {
    }

    private final SkillstoneScreen s;
    private int scroll;
    private int contentH;
    private int vx0, vy0, vx1, vy1;

    private Skill builtSkill;
    private int builtW = -1;
    private int builtPick = -1;
    private long builtAt = Long.MIN_VALUE / 2;
    private String name = "";
    private String levelText = "";
    private String nextText = "";
    private String spendText = "";
    private final List<Card> cards = new ArrayList<>();
    private int synY = -1;
    private String synCount = "";
    private List<Synergy> synList = List.of();
    private int perRow = 1;
    private int chipY;
    private int chipH;
    private final List<String> chipLines = new ArrayList<>();
    private final List<Integer> chipColours = new ArrayList<>();

    FaceColumn(SkillstoneScreen screen) {
        this.s = screen;
    }

    /** The mouse wheel over the Face: true when it took the notch. */
    boolean scroll(double delta) {
        scroll = SkillstoneMath.clampScroll(scroll + SkillstoneMath.wheelStep(delta, LINE), contentH, vy1 - vy0);
        return true;
    }

    String info() {
        return "content=" + contentH + " view=" + (vy1 - vy0) + " scroll=" + scroll + " cards="
                + cards.stream().mapToInt(c -> c.lines().size()).sum() + "+" + chipLines.size() + " lines";
    }

    private int measure(String text) {
        return s.font().width(text);
    }

    private void rebuild(PlayerSkills skills, Skill skill, long now, int w, int pick) {
        builtSkill = skill;
        builtW = w;
        builtPick = pick;
        builtAt = now;
        int level = skills.level(skill);
        name = Component.translatable(skill.translationKey()).getString();
        levelText = SkillstoneScreen.tr("proficiency.stone.level", level);
        float bar = level >= SkillMath.MAX_LEVEL ? 1f : SkillMath.progress(level, (float) skills.xp(skill));
        nextText = level >= SkillMath.MAX_LEVEL ? SkillstoneScreen.tr("proficiency.stone.maxed")
                : SkillstoneScreen.tr("proficiency.stone.to_next", Math.round(bar * 100), level + 1);
        spendText = SkillstoneScreen.tr("proficiency.stone.to_spend", skills.pointsAvailable(skill));
        cards.clear();
        int y = 2 + HEAD_H + GAP;
        String[][] src = s.cards(skills, now, true);
        for (int i = 0; i < 4; i++) {
            List<String> lines = new ArrayList<>();
            List<Integer> colours = new ArrayList<>();
            // The lines start under the header row; the mastery card draws its stars right-aligned on that header row.
            for (int k = 1; k < src[i].length; k++) {
                if (src[i][k] == null || src[i][k].isEmpty()) {
                    continue;
                }
                int colour = i == 0 ? (k == 1 ? Stone.INK : Stone.DIM) : COLOURS[Math.min(k - 1, 2)];
                for (String part : SkillstoneMath.wrap(src[i][k], this::measure, w - 16)) {
                    lines.add(part);
                    colours.add(colour);
                }
            }
            int h = SkillstoneMath.faceCardHeight(lines.size(), LINE);
            cards.add(new Card(i, y, h, src[i][0], lines, colours));
            y += h + GAP;
        }
        synList = Synergies.involving(skill);
        synY = -1;
        if (!synList.isEmpty()) {
            synY = y;
            int awake = 0;
            for (Synergy syn : synList) {
                if (Synergies.isActive(skills, syn)) {
                    awake++;
                }
            }
            synCount = awake == 0 ? SkillstoneScreen.tr("proficiency.stone.syn_none", synList.size())
                    : SkillstoneScreen.tr("proficiency.stone.syn_count", awake, synList.size() - awake);
            perRow = SkillstoneMath.socketsPerRow(w - 4, SOCKET, synList.size());
            int rows = (synList.size() + perRow - 1) / perRow;
            y += 12 + rows * (SOCKET + 4);
            int show = Math.max(0, Math.min(pick, synList.size() - 1));
            Synergy syn = synList.get(show);
            Skill other = SkillstoneScreen.partner(syn, skill);
            chipLines.clear();
            chipColours.clear();
            String head = other == skill ? SkillstoneScreen.tr(syn.nameKey())
                    : SkillstoneScreen.tr("proficiency.stone.syn_with", SkillstoneScreen.tr(syn.nameKey()),
                            SkillstoneScreen.tr(other.translationKey()));
            addWrapped(head, Stone.GOLD, w - 16);
            addWrapped(SkillstoneScreen.tr(net.minecraft.client.resources.language.I18n.exists(syn.shortKey())
                    ? syn.shortKey() : syn.descriptionKey()), Stone.INK, w - 16);
            StringBuilder needs = new StringBuilder();
            for (Synergy.Need need : syn.requires()) {
                Talent talent = need.talent();
                if (talent == null) {
                    continue;
                }
                if (needs.length() > 0) {
                    needs.append("  ·  ");
                }
                needs.append(SkillstoneScreen.tr("proficiency.stone.need", SkillstoneScreen.tr(talent.nameKey()),
                        Math.min(need.minRank(), skills.rank(talent)), need.minRank()));
            }
            addWrapped(needs.toString(), Stone.DIM, w - 16);
            chipY = y;
            chipH = 8 + chipLines.size() * 11;
            y += chipH;
        }
        contentH = y + 4;
    }

    private void addWrapped(String text, int colour, int w) {
        for (String part : SkillstoneMath.wrap(text, this::measure, w)) {
            chipLines.add(part);
            chipColours.add(colour);
        }
    }

    /** A hit rectangle in slab coordinates, cut to the viewport so a clipped part never takes a click. */
    private void hit(int kind, int index, int x, int y, int w, int h) {
        int y0 = Math.max(y, vy0);
        int y1 = Math.min(y + h, vy1);
        if (y1 > y0) {
            s.hits.add(new int[] {kind, index, x, y0, w, y1 - y0});
        }
    }

    void render(GuiGraphics g, PlayerSkills skills, Skill skill, long now, int mx, int my, int pick) {
        vx0 = s.ix0 + 6;
        vx1 = s.ix1 - 6 - SkillstoneMath.FACE_TRACK_W - 2;
        vy0 = s.iy0 + 4;
        vy1 = s.iy1 - 4;
        int w = vx1 - vx0 - 4;
        if (builtSkill != skill) {
            scroll = 0;
        }
        // The chip shows the hovered socket, else the picked one (as the two-column Face does). The chip is the
        // last block of the column, so a hover that changes it never moves the sockets under the cursor.
        int hovered = builtSkill == skill && builtW == w ? hoveredSocket(mx, my) : -1;
        int show = hovered >= 0 ? hovered : pick;
        if (builtSkill != skill || builtW != w || builtPick != show || now - builtAt >= REFRESH_MS) {
            rebuild(skills, skill, now, w, show);
        }
        scroll = SkillstoneMath.clampScroll(scroll, contentH, vy1 - vy0);
        int x0 = vx0 + 2;
        int top = vy0 - scroll;
        boolean inside = mx >= vx0 && mx < vx1 + 2 && my >= vy0 && my < vy1;
        Stone.scissor(g, vx0 - 2, vy0, vx1 + 2, vy1);

        // Header: the icon socket, name, level and the points brazier.
        int hy = top + 2;
        Stone.socket(g, x0, hy, 40, 40, 1f, false, false);
        Stone.icon(g, SkillIcons.texture(skill), x0 + 8, hy + 8, 24, 1f);
        int tx = x0 + 48;
        int level = skills.level(skill);
        Stone.text(g, s.font(), Stone.fit(s.font(), name, vx1 - tx - 42), tx, hy + 2, Stone.GOLD);
        Stone.text(g, s.font(), levelText, tx, hy + 14, Stone.INK);
        int points = skills.pointsAvailable(skill);
        Stone.textRight(g, s.font(), spendText, vx1 - 2, hy + 33, points > 0 ? Stone.INK : Stone.DIM);
        float bar = level >= SkillMath.MAX_LEVEL ? 1f : SkillMath.progress(level, (float) skills.xp(skill));
        Stone.groove(g, tx, vx1 - 2, hy + 27, bar, 3, true);
        Stone.text(g, s.font(), nextText, tx, hy + 33, Stone.DIM);
        int bx = vx1 - 40;
        Stone.coals(g, bx, hy, Math.min(3, points));
        hit(5, 0, bx, hy, 36, 16);
        if (inside && mx >= bx && mx < bx + 36 && my >= hy && my < hy + 16) {
            s.tooltip.add(Component.literal(SkillstoneScreen.tr("proficiency.stone.brazier")));
        }

        for (Card c : cards) {
            int ky = top + c.y();
            if (ky + c.h() < vy0 || ky > vy1) {
                continue;
            }
            Stone.well(g, x0, ky, w, c.h());
            Stone.text(g, s.font(), c.header(), x0 + 8, ky + 5, Stone.HEAD);
            int yy = ky + 5 + LINE + 1;
            for (int k = 0; k < c.lines().size(); k++) {
                Stone.text(g, s.font(), c.lines().get(k), x0 + 8, yy, c.colours().get(k));
                yy += LINE;
            }
            if (c.index() == 2 && ActiveService.unlocked(skills, skill)) {
                boolean ready = skills.cooldownRemaining(skill, s.gameTime()) <= 0;
                int px = x0 + w - 14;
                int py = ky + 7;
                if (ready) {
                    Stone.light(g, px + 2, py + 2, 10, 10, 0x28963C, 0.9f);
                    g.fill(px, py, px + 5, py + 5, Stone.argb(255, Stone.GREEN));
                } else {
                    g.fill(px, py, px + 5, py + 5, Stone.argb(255, 0x5A3C30));
                }
            }
            if (c.index() == 3) {
                int stars = level >= SkillMath.MAX_LEVEL ? MasteryStars.shown(skills, skill) : 0;
                String lit = MasteryStars.text(stars);
                String dark = MasteryStars.text(Mastery.MAX_STARS - stars);
                int sx0 = x0 + w - 8 - s.font().width(lit + dark);
                Stone.text(g, s.font(), lit, sx0, ky + 5, 0xFFC83A);
                Stone.text(g, s.font(), dark, sx0 + s.font().width(lit), ky + 5, 0x6E625C);
            }
            if (inside && mx >= x0 && mx < x0 + w && my >= ky && my < ky + c.h()) {
                s.cardTooltip(c.index(), skills);
            }
        }

        if (synY >= 0) {
            renderSynergies(g, skills, skill, now, mx, my, inside, x0, top, w, pick);
        }
        g.disableScissor();

        int[] thumb = SkillstoneMath.scrollThumb(scroll, contentH, vy1 - vy0, vy1 - vy0);
        if (thumb != null) {
            int tx0 = s.ix1 - 6 - SkillstoneMath.FACE_TRACK_W;
            g.fill(tx0, vy0, tx0 + SkillstoneMath.FACE_TRACK_W, vy1, Stone.argb(160, Stone.OUTLINE));
            g.fill(tx0, vy0 + thumb[0], tx0 + SkillstoneMath.FACE_TRACK_W, vy0 + thumb[0] + thumb[1],
                    Stone.argb(255, Stone.HEAD));
        }
    }

    /** The synergy socket under the mouse in the last built layout, or -1. */
    private int hoveredSocket(int mx, int my) {
        if (synY < 0 || synList == null || perRow <= 0 || mx < vx0 || mx >= vx1 + 2 || my < vy0 || my >= vy1) {
            return -1;
        }
        int x0 = vx0 + 2;
        int y = vy0 - scroll + synY + 12;
        for (int i = 0; i < synList.size(); i++) {
            int px = x0 + (i % perRow) * (SOCKET + 4);
            int py = y + (i / perRow) * (SOCKET + 4);
            if (mx >= px && mx < px + SOCKET && my >= py && my < py + SOCKET) {
                return i;
            }
        }
        return -1;
    }

    private void renderSynergies(GuiGraphics g, PlayerSkills skills, Skill skill, long now, int mx, int my,
                                 boolean inside, int x0, int top, int w, int pick) {
        int y = top + synY;
        Stone.text(g, s.font(), SkillstoneScreen.tr("proficiency.stone.synergies"), x0, y, Stone.HEAD);
        Stone.textRight(g, s.font(), synCount, x0 + w, y, Stone.DIM);
        int sel = Math.max(0, Math.min(pick, synList.size() - 1));
        for (int i = 0; i < synList.size(); i++) {
            Synergy syn = synList.get(i);
            int px = x0 + (i % perRow) * (SOCKET + 4);
            int py = y + 12 + (i / perRow) * (SOCKET + 4);
            if (py + SOCKET < vy0 || py > vy1) {
                continue;
            }
            boolean on = Synergies.isActive(skills, syn);
            boolean hov = inside && mx >= px && mx < px + SOCKET && my >= Math.max(py, vy0)
                    && my < Math.min(py + SOCKET, vy1);
            boolean picked = i == sel;
            Object key = syn;
            s.click.begin(g, key, px, py, SOCKET, SOCKET, now);
            Stone.socket(g, px, py, SOCKET, SOCKET, on ? 1f : picked || hov ? 0.6f : SkillstoneScreen.near(skills, syn) ? 0.25f : 0f,
                    picked, hov);
            ResourceLocation ic = SkillIcons.texture(SkillstoneScreen.partner(syn, skill));
            if (on || picked || hov) {
                Stone.icon(g, ic, px + 1, py + 1, 16, 1f);
            } else {
                Stone.greyIcon(g, ic, px + 1, py + 1, 16, SkillstoneScreen.near(skills, syn) ? 0.8f : 0.45f);
            }
            s.click.end(g, key, px, py, SOCKET, SOCKET, now);
            hit(6, i, px, py, SOCKET, SOCKET);
        }
        int cy0 = top + chipY;
        if (cy0 + chipH >= vy0 && cy0 <= vy1) {
            Stone.block(g, x0, cy0, w, chipH, 3, 1, false, 0x241C1A);
            Stone.outlineRound(g, x0, cy0, w, chipH, 3, Stone.argb(255, 0xE68232));
            for (int k = 0; k < chipLines.size(); k++) {
                Stone.text(g, s.font(), chipLines.get(k), x0 + 8, cy0 + 6 + k * 11, chipColours.get(k));
            }
        }
    }
}
