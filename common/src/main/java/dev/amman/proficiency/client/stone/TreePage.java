package dev.amman.proficiency.client.stone;

import dev.amman.proficiency.client.ClickFx;
import dev.amman.proficiency.client.ClickFxMath;
import dev.amman.proficiency.client.ClientXpLog;
import dev.amman.proficiency.client.SkillIcons;
import dev.amman.proficiency.client.SkillstoneMath;
import dev.amman.proficiency.client.TalentTreeScreen;
import dev.amman.proficiency.perk.PerkEffect;
import dev.amman.proficiency.perk.Synergies;
import dev.amman.proficiency.perk.Synergy;
import dev.amman.proficiency.perk.Talent;
import dev.amman.proficiency.perk.TalentOutcome;
import dev.amman.proficiency.perk.TalentService;
import dev.amman.proficiency.perk.Talents;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Skillstone's Tree tab (1.30.0, stage 2): the talent tree carved into the slab. Coal sockets
 * on molten grooves, the level gates on a rail at the left, the picked node's detail and the
 * recent XP in a sunk slate at the right. Click a socket for one rank, Shift-click to fill it.
 *
 * <p>Overload feedback (storyboard-interaction): the socket shakes while the server answers, the
 * rank pours in as molten metal with flint sparks, a fire flare and a short slab shake; a filled
 * capstone or keystone flares big with an edge glow; a node that opens new ones lifts the slab in a
 * rising shake; a refusal wiggles the slab, flashes red, thunks and says why next to the node.
 *
 * <p>Shared verbatim with Forge 1.20.1 (copySharedHearth); only {@link StoneNet} differs.
 */
final class TreePage {

    private static final int[] DEFAULT_GATES = {0, 10, 30, 60, 90, 100};
    private static final long POUR_MS = 260;
    private static final long FLARE_MS = 520;

    private final SkillstoneScreen s;
    private Skill skill;

    // Layout
    private boolean small;
    private int tx0;
    private float colw;
    private int ty0;
    private int rowStep;
    private int viewTop;
    private int viewBot;
    private int rail;
    private int rx0;
    private int ry0;
    private int rx1;
    private int ry1;
    private int ns;
    private int nb;
    private int scroll;
    private int scrollMax;
    private int[] gates = DEFAULT_GATES;

    // State
    private Talent focus;
    private Talent hovered;
    private final Map<String, Integer> seen = new HashMap<>();
    private final Map<String, Long> filledAt = new HashMap<>();
    private final Map<String, Long> pourAt = new HashMap<>();
    private final Map<String, Long> flareAt = new HashMap<>();
    private final Map<String, Integer> flareTier = new HashMap<>();
    private Talent reasonNode;
    private String reason;
    private long reasonAt = Long.MIN_VALUE / 2;
    private List<FormattedCharSequence> descCache;
    private Talent descFor;
    private int descWidth;
    // The detail slate's dim lines, rebuilt only when the node, its rank, the level or the paid state change.
    private List<String> extrasCache;
    private Talent extrasFor;
    private int extrasRank;
    private int extrasLevel;
    private boolean extrasPaid;
    private TalentOutcome[] checks = new TalentOutcome[0];

    TreePage(SkillstoneScreen s) {
        this.s = s;
    }

    void open(Skill skill) {
        if (skill != this.skill) {
            this.skill = skill;
            focus = null;
            hovered = null;
            seen.clear();
            filledAt.clear();
            pourAt.clear();
            flareAt.clear();
            scroll = 0;
            reasonNode = null;
            descCache = null;
            extrasCache = null;
            checks = new TalentOutcome[0];
        }
    }

    // ---- Layout ---------------------------------------------------------------------------------

    void layout() {
        int iw = s.ix1 - s.ix0;
        int ih = s.iy1 - s.iy0;
        small = iw < 520 || ih < 300;
        ns = small ? 9 : 12;
        nb = small ? 11 : 15;
        int dw = small ? Math.max(118, Math.min(150, iw * 3 / 10)) : 188;
        rx1 = s.ix1 - (small ? 6 : 14);
        rx0 = rx1 - dw;
        ry0 = s.iy0 + (small ? 30 : 40);
        ry1 = s.iy1 - (small ? 8 : 34);
        rail = s.ix0 + (small ? 12 : 20);
        tx0 = s.ix0 + (small ? 50 : 62);
        int tx1 = rx0 - (small ? 26 : 52);
        colw = (tx1 - tx0) / (float) (SkillstoneMath.TREE_COLS - 1);
        viewTop = s.iy0 + (small ? 26 : 40);
        viewBot = s.iy1 - (small ? 4 : 14);
        ty0 = viewTop + nb + (small ? 4 : 3);
        int tyEnd = s.iy1 - (small ? 4 + nb : 34);
        int minStep = 2 * ns + (small ? 18 : 22);
        rowStep = SkillstoneMath.treeRowStep(tyEnd - ty0, minStep);
        scrollMax = SkillstoneMath.treeScrollMax(rowStep, SkillstoneMath.treeNodeBelow(ns, nb) + (ty0 - viewTop), viewBot - viewTop);
        scroll = SkillstoneMath.clampScroll(scroll, scrollMax);
        gates = gates(skill);
    }

    private static int[] gates(Skill skill) {
        int[] out = DEFAULT_GATES.clone();
        if (skill == null) {
            return out;
        }
        int[] min = new int[SkillstoneMath.TREE_ROWS];
        java.util.Arrays.fill(min, Integer.MAX_VALUE);
        for (Talent t : Talents.of(skill)) {
            if (t.row() >= 0 && t.row() < min.length) {
                min[t.row()] = Math.min(min[t.row()], t.requiredLevel());
            }
        }
        for (int r = 0; r < min.length; r++) {
            if (min[r] != Integer.MAX_VALUE) {
                out[r] = min[r];
            }
        }
        return out;
    }

    private int nodeX(Talent t) {
        return Math.round(tx0 + t.col() * colw);
    }

    private int nodeY(Talent t) {
        return ty0 + t.row() * rowStep - scroll;
    }

    private int half(Talent t) {
        return big(t) ? nb : ns;
    }

    private static boolean big(Talent t) {
        return t.kind() == Talent.Kind.ROOT || t.kind() == Talent.Kind.CAPSTONE;
    }

    private static boolean grand(Talent t) {
        return t.kind() == Talent.Kind.CAPSTONE || t.kind() == Talent.Kind.KEYSTONE;
    }

    // ---- Render ---------------------------------------------------------------------------------

    void render(GuiGraphics g, PlayerSkills skills, long now, int mx, int my) {
        Font font = s.font();
        List<Talent> tree = Talents.of(skill);
        outcomes(skills, tree);
        watch(skills, tree, now);
        renderHeader(g, skills, font);
        boolean clip = scrollMax > 0;
        if (clip) {
            Stone.scissor(g, s.ix0 + 2, viewTop, rx0 - 4, viewBot);
        }
        renderRail(g, skills, font);
        boolean inView = mx >= s.ix0 && mx < rx0 - 4 && my >= viewTop && my < viewBot;
        Talent hov = null;
        for (Talent t : tree) {
            int x = nodeX(t);
            int y = nodeY(t);
            int h = half(t);
            if (inView && mx >= x - h - 2 && mx < x + h + 2 && my >= y - h - 2 && my < y + h + 2) {
                hov = t;
            }
        }
        if (hov != hovered) {
            hovered = hov;
            if (hov != null) {
                SkillstoneScreen.sound(SoundEvents.STONE_HIT, 1.1f + 0.05f * hov.col(), 0.18f);
            }
        }
        for (Talent t : tree) {
            for (String pid : t.parents()) {
                Talent p = Talents.get(skill, pid);
                if (p != null) {
                    renderGroove(g, skills, p, t, now);
                }
            }
        }
        for (int i = 0; i < tree.size(); i++) {
            renderNode(g, skills, tree.get(i), i, now, font);
        }
        if (clip) {
            g.disableScissor();
            int barH = Math.max(10, (viewBot - viewTop) * (viewBot - viewTop) / (viewBot - viewTop + scrollMax));
            int barY = viewTop + (viewBot - viewTop - barH) * scroll / Math.max(1, scrollMax);
            g.fill(rx0 - 8, viewTop, rx0 - 7, viewBot, Stone.argb(160, 0x161110));
            g.fill(rx0 - 8, barY, rx0 - 7, barY + barH, Stone.argb(150, Stone.ember(0.5f)));
        }
        renderMarks(g, tree, now, font);
        renderDetail(g, skills, now, mx, my, font);
    }

    /** Off the tree: ranks are only noted, so a rank that landed meanwhile does not play on return. */
    void quietWatch(PlayerSkills skills, Skill shown) {
        if (shown != skill || seen.isEmpty()) {
            return;
        }
        for (Talent t : Talents.of(skill)) {
            seen.put(t.id(), skills.rank(t));
        }
    }

    /** One {@code check} per node per frame, shared by the sockets, the shown node and the detail slate. */
    private void outcomes(PlayerSkills skills, List<Talent> tree) {
        if (checks.length != tree.size()) {
            checks = new TalentOutcome[tree.size()];
        }
        for (int i = 0; i < tree.size(); i++) {
            checks[i] = skills.check(tree.get(i));
        }
    }

    private TalentOutcome outcomeOf(PlayerSkills skills, Talent t) {
        int i = Talents.of(skill).indexOf(t);
        return i >= 0 && i < checks.length && checks[i] != null ? checks[i] : skills.check(t);
    }

    /** A rank that rose since the last frame lands: molten fill, flint sparks, flare, shake. */
    private void watch(PlayerSkills skills, List<Talent> tree, long now) {
        for (Talent t : tree) {
            int rank = skills.rank(t);
            Integer before = seen.put(t.id(), rank);
            if (before == null || rank <= before) {
                continue;
            }
            land(t, before, rank, now);
        }
    }

    private void land(Talent t, int before, int rank, long now) {
        int x = nodeX(t);
        int y = nodeY(t);
        int h = half(t);
        boolean full = rank >= t.maxRank();
        int tier = full ? (grand(t) ? 2 : 1) : 0;
        s.click.success(t.key(), x - h, y - h, 2 * h, 2 * h, Stone.ember(0.75f), tier, rank / (float) t.maxRank(), now);
        if (!ClickFx.on()) {
            return;
        }
        filledAt.put(t.id(), now);
        flareAt.put(t.id(), now);
        flareTier.put(t.id(), tier);
        if (before == 0) {
            pourAt.put(t.id(), now);
        }
        SkillstoneScreen.sound(SoundEvents.FLINTANDSTEEL_USE, 1.2f + 0.1f * rank, 0.55f);
        if (tier >= 2) {
            SkillstoneScreen.sound(SoundEvents.FIRECHARGE_USE, 0.8f, 0.45f);
            s.edgeGlow(now);
            s.shake(now, 4f);
        } else {
            s.shake(now, full ? 2.5f : 1.5f);
        }
        if (full) {
            boolean opens = false;
            for (Talent child : Talents.of(skill)) {
                if (child.parents().contains(t.id())) {
                    opens = true;
                    int cx = nodeX(child);
                    int cy = nodeY(child);
                    int ch = half(child);
                    float dist = (float) Math.hypot(cx - x, cy - y);
                    s.click.ripple(child.key(), cx - ch, cy - ch, 2 * ch, 2 * ch, Stone.ember(0.75f), now,
                            ClickFxMath.MIN_CHARGE_MS + ClickFxMath.rippleDelay(dist));
                }
            }
            if (opens && tier < 2) {
                s.riseShake(now + ClickFxMath.MIN_CHARGE_MS);
            }
        }
    }

    private void renderHeader(GuiGraphics g, PlayerSkills skills, Font font) {
        int x = s.ix0 + (small ? 8 : 14);
        int y = s.iy0 + (small ? 6 : 12);
        Stone.icon(g, SkillIcons.texture(skill), x, y, 16, 1f);
        String name = Component.translatable(skill.translationKey()).getString();
        String spent = SkillstoneScreen.tr("proficiency.stone.spent", skills.pointsSpent(skill), Talents.fullTreeCost(skill));
        Stone.text(g, font, name, x + 22, y, Stone.GOLD);
        Stone.text(g, font, spent, x + 22, y + 12, Stone.DIM);
        int points = skills.pointsAvailable(skill);
        int cx = x + 22 + Math.max(font.width(name), font.width(spent)) + (small ? 8 : 14);
        if (cx + 40 + font.width(SkillstoneScreen.tr("proficiency.stone.to_spend", points)) < rx0) {
            Stone.coals(g, cx, y + 1, Math.min(3, points));
            Stone.text(g, font, SkillstoneScreen.tr("proficiency.stone.to_spend", points), cx + 40, y + 7,
                    points > 0 ? Stone.INK : Stone.DIM);
        }
    }

    /** The level gates: a rail lit down to the skill's level, a tick and a number per row. */
    private void renderRail(GuiGraphics g, PlayerSkills skills, Font font) {
        int level = skills.level(skill);
        int top = ty0;
        int bottom = ty0 + (SkillstoneMath.TREE_ROWS - 1) * rowStep;
        vseg(g, rail, top - scroll, bottom - scroll, 0f);
        int litTo = top;
        for (int r = 0; r < gates.length; r++) {
            int y = ty0 + r * rowStep;
            if (level >= gates[r]) {
                litTo = y;
                if (r + 1 < gates.length && level < gates[r + 1]) {
                    float k = (level - gates[r]) / (float) Math.max(1, gates[r + 1] - gates[r]);
                    litTo = y + Math.round(k * rowStep);
                }
            }
        }
        vseg(g, rail, top - scroll, litTo - scroll, 1f);
        for (int r = 0; r < gates.length; r++) {
            int y = ty0 + r * rowStep - scroll;
            boolean on = level >= gates[r];
            g.fill(rail - 3, y - 1, rail + 4, y + 2, Stone.argb(255, on ? Stone.ember(0.8f) : 0x1E1816));
            Stone.text(g, font, Integer.toString(gates[r]), rail + 8, y - 4, on ? Stone.GOLD : Stone.LOCK);
        }
    }

    // Grooves: axis-aligned runs of the kit's groove (shadow, dark channel, glow, molten core).

    private static void hseg(GuiGraphics g, int x0, int x1, int y, float t) {
        int a = Math.min(x0, x1);
        int b = Math.max(x0, x1);
        if (t <= 0f) {
            g.fill(a, y, b + 2, y + 3, Stone.argb(120, 0x968278));
            g.fill(a - 1, y - 1, b + 2, y + 2, Stone.argb(255, 0x161110));
            return;
        }
        int end = x0 + Math.round((x1 - x0) * Math.min(1f, t));
        int la = Math.min(x0, end);
        int lb = Math.max(x0, end);
        g.fill(la - 3, y - 3, lb + 4, y + 4, Stone.argb(70, Stone.ember(0.5f)));
        g.fill(la, y, lb + 1, y + 1, Stone.argb(255, Stone.ember(0.86f)));
    }

    private static void vseg(GuiGraphics g, int x, int y0, int y1, float t) {
        int a = Math.min(y0, y1);
        int b = Math.max(y0, y1);
        if (t <= 0f) {
            g.fill(x, a, x + 3, b + 2, Stone.argb(120, 0x968278));
            g.fill(x - 1, a - 1, x + 2, b + 2, Stone.argb(255, 0x161110));
            return;
        }
        int end = y0 + Math.round((y1 - y0) * Math.min(1f, t));
        int la = Math.min(y0, end);
        int lb = Math.max(y0, end);
        g.fill(x - 3, la - 3, x + 4, lb + 4, Stone.argb(70, Stone.ember(0.5f)));
        g.fill(x, la, x + 1, lb + 1, Stone.argb(255, Stone.ember(0.86f)));
    }

    /** Parent's bottom, down to the elbow, across, down into the child: dark first, then the molten part. */
    private void renderGroove(GuiGraphics g, PlayerSkills skills, Talent p, Talent c, long now) {
        int ax = nodeX(p);
        int ay = nodeY(p) + half(p);
        int bx = nodeX(c);
        int by = nodeY(c) - half(c);
        int hy = nodeY(c) - Math.round(rowStep * 0.36f);
        hy = Math.max(ay + 2, Math.min(hy, by - 2));
        float t = skills.isFull(p) && skills.rank(c) > 0 ? 1f : 0f;
        Long at = pourAt.get(c.id());
        if (at != null && t > 0f) {
            t = SkillstoneMath.pour(now - at - ClickFxMath.MIN_CHARGE_MS, POUR_MS);
            if (t >= 1f) {
                pourAt.remove(c.id());
            }
        }
        int l1 = hy - ay;
        int l2 = Math.abs(bx - ax);
        int l3 = by - hy;
        float tot = Math.max(1, l1 + l2 + l3);
        vseg(g, ax, ay, hy, 0f);
        hseg(g, ax, bx, hy, 0f);
        vseg(g, bx, hy, by, 0f);
        if (t > 0f) {
            float run = t * tot;
            vseg(g, ax, ay, hy, Math.min(1f, run / Math.max(1, l1)));
            run -= l1;
            if (run > 0 && l2 > 0) {
                hseg(g, ax, bx, hy, Math.min(1f, run / l2));
            }
            run -= l2;
            if (run > 0) {
                vseg(g, bx, hy, by, Math.min(1f, run / Math.max(1, l3)));
            }
        }
    }

    private void renderNode(GuiGraphics g, PlayerSkills skills, Talent t, int index, long now, Font font) {
        TalentOutcome outcome = index < checks.length && checks[index] != null ? checks[index] : skills.check(t);
        int x = nodeX(t);
        int y = nodeY(t);
        int h = half(t);
        if (y + h + 20 < viewTop - 20 || y - h > viewBot + 20) {
            return;
        }
        int level = skills.level(skill);
        boolean locked = level < t.requiredLevel();
        int rank = skills.rank(t);
        boolean full = rank >= t.maxRank();
        boolean avail = outcome == TalentOutcome.OK || outcome == TalentOutcome.MISSING_MATERIALS;
        Long fillStart = filledAt.get(t.id());
        float fill = fillStart == null ? 1f : SkillstoneMath.fill(now - fillStart - ClickFxMath.MIN_CHARGE_MS);
        if (fillStart != null && fill >= 1f && now - fillStart > 2000) {
            filledAt.remove(t.id());
        }
        float shownRank = fill >= 1f ? rank : Math.max(0, rank - 1) + fill;
        float lit = locked ? 0f : shownRank / t.maxRank();
        Object key = t.key();
        s.click.begin(g, key, x - h, y - h, 2 * h, 2 * h, now);
        Stone.socket(g, x - h, y - h, 2 * h, 2 * h, lit, t == focus || t == hovered, avail || t == hovered);
        int i0 = x - h + 4;
        int j0 = y - h + 4;
        int in = 2 * h - 8;
        if (rank > 0 && !(full && fill >= 1f)) {
            int fh = Math.round(in * Math.min(1f, shownRank / t.maxRank()));
            for (int yy = j0 + in - fh; yy < j0 + in; yy++) {
                if (yy >= j0 + in - 2) {
                    g.fill(i0, yy, i0 + in, yy + 1, Stone.argb(255, Stone.ember(0.62f)));
                    continue;
                }
                for (int xx = i0 + Math.floorMod(-(i0 + yy), 3); xx < i0 + in; xx += 3) {
                    g.fill(xx, yy, xx + 1, yy + 1, Stone.argb(255, Stone.ember(0.55f + 0.3f * ((xx * 7 + yy * 3) % 5) / 5f)));
                }
            }
            if (fill < 1f && fh > 0) {
                // The molten front: a bright line where the pour has reached.
                g.fill(i0, j0 + in - fh, i0 + in, j0 + in - fh + 1, Stone.argb(255, Stone.ember(0.97f)));
            }
        }
        if (full) {
            int frame = (int) (((now / 100L) + t.col() * 3L + t.row()) % 8);
            Stone.blitAlpha(g, Stone.tex("flame_" + in), i0, j0, in, in, 0f, frame * in, in, in * 8, fill);
        }
        if (locked) {
            Stone.textCentred(g, font, Integer.toString(t.requiredLevel()), x + 1, y - 4, Stone.LOCK);
        }
        if (!t.materials().isEmpty() && !skills.hasPaid(t)) {
            g.fill(x + h - 5, y - h + 2, x + h - 2, y - h + 5, Stone.argb(255, 0xD2A249));
        }
        s.click.end(g, key, x - h, y - h, 2 * h, 2 * h, now);
        // Rank pips under the socket.
        int n = t.maxRank();
        int px0 = x - (n * 4) / 2 + 1;
        for (int r = 0; r < n; r++) {
            int c = r < Math.floor(shownRank + 0.001f) ? Stone.ember(0.85f) : locked ? 0x282220 : 0x1A1413;
            g.fill(px0 + r * 4, y + h + 3, px0 + r * 4 + 3, y + h + 6, Stone.argb(255, c));
        }
        String name = Component.translatable(t.nameKey()).getString();
        int colour = full ? Stone.GOLD : locked ? Stone.LOCK : Stone.INK;
        if (big(t)) {
            int room = rx0 - 10 - (x + h + 8);
            Stone.text(g, font, Stone.fit(font, name, room), x + h + 8, y - 4, colour);
        } else {
            // Edge columns may not run under the detail slate or into the gate numbers.
            int room = Math.min(Math.round(colw) - 2, 2 * Math.min(rx0 - 6 - x, x - (rail + 6)));
            String shown = Stone.fit(font, name, Math.max(20, room));
            Stone.textCentred(g, font, shown, x + 1, y + h + 8, colour);
        }
        // Clickable only inside the tree's window.
        int top = Math.max(y - h, viewTop);
        int bottom = Math.min(y + h, viewBot);
        if (bottom > top) {
            s.hits.add(new int[] {7, index, x - h, top, 2 * h, bottom - top});
        }
    }

    /** Over the nodes: the fire flare of a landing rank, its "+1 rank" mark and a refusal's reason. */
    private void renderMarks(GuiGraphics g, List<Talent> tree, long now, Font font) {
        for (Talent t : tree) {
            Long at = flareAt.get(t.id());
            if (at == null) {
                continue;
            }
            long dt = now - at - ClickFxMath.MIN_CHARGE_MS;
            if (dt > SkillstoneMath.RANK_MARK_MS) {
                flareAt.remove(t.id());
                continue;
            }
            if (dt < 0) {
                continue;
            }
            int x = nodeX(t);
            int y = nodeY(t);
            int tier = flareTier.getOrDefault(t.id(), 0);
            if (dt < FLARE_MS) {
                float k = dt / (float) FLARE_MS;
                int r = Math.round((16 + 34 * k) * (tier >= 2 ? 1.8f : 1f));
                Stone.light(g, x, y, r, r, 0xC86E1E, (1.2f - 0.7f * k) * (1f - k));
            }
            float a = SkillstoneMath.markAlpha(dt);
            if (a > 0.05f) {
                String mark = SkillstoneScreen.tr(tier >= 2 ? "proficiency.stone.mark_full" : "proficiency.stone.mark_rank");
                int rise = Math.round(SkillstoneMath.markRise(dt) * (big(t) ? 1f : 0.6f));
                // Over a small socket; beside a big one, above its label, so it never sits on the row above.
                int mx0 = big(t) ? x + half(t) + 8 : x + 1 - font.width(mark) / 2;
                int yy = big(t) ? y - 16 - rise : y - half(t) - 10 - rise;
                g.drawString(font, mark, mx0, yy, Stone.argb(Math.round(255 * a), 0xFFECAA), true);
            }
        }
        if (reasonNode != null && now - reasonAt < SkillstoneMath.REASON_MS) {
            int x = nodeX(reasonNode);
            int y = nodeY(reasonNode);
            int h = half(reasonNode);
            float k = 1f - (now - reasonAt) / (float) SkillstoneMath.REASON_MS;
            Stone.light(g, x, y, 22, 22, 0x78140A, 0.9f * Math.min(1f, k * 2));
            // The reason sits on a dark chip just above the socket, kept inside the tree's side of the slab.
            String shown = Stone.fit(font, reason, rx0 - 12 - (s.ix0 + 6));
            int w = font.width(shown);
            int tx = Math.max(s.ix0 + 6, Math.min(x + 1 - w / 2, rx0 - 8 - w));
            int ty = y - h - 14;
            int fade = Math.round(255 * Math.min(1f, k * 3));
            Stone.fillRound(g, tx - 4, ty - 3, w + 8, 13, 2, Stone.argb(Math.round(fade * 0.9f), 0x2A1210));
            Stone.outlineRound(g, tx - 4, ty - 3, w + 8, 13, 2, Stone.argb(fade, 0x8C2A1E));
            g.drawString(font, shown, tx, ty, Stone.argb(fade, 0xFF7864), true);
        }
    }

    // ---- Detail slate ---------------------------------------------------------------------------

    private Talent shownNode(PlayerSkills skills) {
        if (hovered != null) {
            return hovered;
        }
        if (focus != null) {
            return focus;
        }
        Talent first = null;
        for (Talent t : Talents.of(skill)) {
            if (first == null) {
                first = t;
            }
            if (outcomeOf(skills, t) == TalentOutcome.OK) {
                return t;
            }
        }
        return first;
    }

    private void renderDetail(GuiGraphics g, PlayerSkills skills, long now, int mx, int my, Font font) {
        Stone.well(g, rx0, ry0, rx1 - rx0, ry1 - ry0);
        Talent t = shownNode(skills);
        if (t == null) {
            return;
        }
        int pad = small ? 6 : 10;
        int w = rx1 - rx0 - 2 * pad;
        int x = rx0 + pad;
        int y = ry0 + (small ? 6 : 10);
        int rank = skills.rank(t);
        String rk = rank + "/" + t.maxRank();
        Stone.text(g, font, Stone.fit(font, Component.translatable(t.nameKey()).getString(), w - font.width(rk) - 6), x, y,
                Stone.GOLD);
        Stone.textRight(g, font, rk, rx1 - pad, y, Stone.INK);
        y += 14;
        Stone.groove(g, x, rx1 - pad, y, rank / (float) t.maxRank(), 3, true);
        y += small ? 6 : 10;
        TalentOutcome outcome = outcomeOf(skills, t);
        // The action plate sits at the bottom; everything above it shares what is left.
        int plateH = small ? 16 : 20;
        int foot = outcome == TalentOutcome.OK && t.maxRank() - rank > 1 ? 12 : 0;
        int plateY = ry1 - (small ? 6 : 12) - foot - plateH;
        int line = small ? 10 : 11;
        if (descCache == null || descFor != t || descWidth != w) {
            descCache = font.split(Component.translatable(t.descriptionKey()), w);
            descFor = t;
            descWidth = w;
        }
        for (FormattedCharSequence seq : descCache) {
            if (y + line > plateY - 4) {
                break;
            }
            g.drawString(font, seq, x, y, Stone.argb(255, Stone.INK), true);
            y += line;
        }
        y += small ? 4 : 8;
        int level = skills.level(skill);
        boolean paid = skills.hasPaid(t);
        if (extrasCache == null || extrasFor != t || extrasRank != rank || extrasLevel != level || extrasPaid != paid) {
            extrasCache = extras(skills, t);
            extrasFor = t;
            extrasRank = rank;
            extrasLevel = level;
            extrasPaid = paid;
        }
        for (String extra : extrasCache) {
            if (y + line > plateY - 4) {
                break;
            }
            Stone.text(g, font, Stone.fit(font, extra, w), x, y, Stone.DIM);
            y += line;
        }
        // Recent XP, as many lines as fit above the plate.
        List<ClientXpLog.Line> log = ClientXpLog.lines(skill);
        if (y + 8 + 13 + line <= plateY - 4) {
            y += small ? 4 : 8;
            g.fill(x, y, rx1 - pad, y + 1, Stone.argb(255, 0x463A36));
            y += small ? 5 : 8;
            Stone.text(g, font, SkillstoneScreen.tr("proficiency.stone.recent_xp"), x, y, Stone.HEAD);
            y += 13;
            if (log.isEmpty()) {
                Stone.text(g, font, Stone.fit(font, SkillstoneScreen.tr("proficiency.stone.recent_none"), w), x, y, Stone.DIM);
            }
            long wall = System.currentTimeMillis();
            for (int i = 0; i < log.size() && y + line <= plateY - 4; i++) {
                ClientXpLog.Line l = log.get(i);
                String amount = String.format(Locale.ROOT, "+%.1f", l.amount());
                String ago = TalentTreeScreen.ago(Math.max(0L, wall - l.at()));
                String what = TalentTreeScreen.sourceName(l.source()) + (l.count() > 1 ? " x" + l.count() : "");
                int ax = x + Math.max(34, font.width(amount) + 6);
                Stone.text(g, font, amount, x, y, Stone.GOLD);
                Stone.text(g, font, Stone.fit(font, what, rx1 - pad - font.width(ago) - 6 - ax), ax, y, Stone.INK);
                Stone.textRight(g, font, ago, rx1 - pad, y, Stone.DIM);
                y += line;
            }
        }
        // The action plate: what a click does, or why it cannot.
        String act;
        int colour;
        if (outcome == TalentOutcome.OK) {
            act = SkillstoneScreen.tr("proficiency.stone.click_rank", t.costPerRank());
            colour = Stone.INK;
        } else if (outcome == TalentOutcome.MAXED) {
            act = SkillstoneScreen.tr("proficiency.stone.node_full");
            colour = Stone.GOLD;
        } else if (outcome == TalentOutcome.MISSING_MATERIALS) {
            act = SkillstoneScreen.tr("proficiency.stone.click_materials");
            colour = Stone.HEAD;
        } else {
            act = TalentService.explain(skills, t, outcome).getString();
            colour = 0xFF8C78;
        }
        Stone.plate(g, font, x, plateY, w, plateH, Stone.fit(font, act, w - 10), colour, false);
        if (foot > 0) {
            Stone.textCentred(g, font, Stone.fit(font, SkillstoneScreen.tr("proficiency.stone.shift_fill"), w), (rx0 + rx1) / 2,
                    plateY + plateH + 4, Stone.DIM);
        }
    }

    /** The dim lines under the description: per-rank numbers, what the node opens, what it feeds. */
    private List<String> extras(PlayerSkills skills, Talent t) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<PerkEffect, Double> e : t.perRank().entrySet()) {
            String per = signed(e.getValue());
            String text = Component.translatable(e.getKey().translationKey(), per).getString();
            out.add(t.maxRank() > 1 ? SkillstoneScreen.tr("proficiency.stone.per_rank", text) : text);
        }
        if (skills.level(skill) < t.requiredLevel()) {
            out.add(SkillstoneScreen.tr("proficiency.stone.opens_at", t.requiredLevel()));
        }
        StringBuilder opens = new StringBuilder();
        for (Talent c : Talents.of(skill)) {
            if (c.parents().contains(t.id())) {
                if (opens.length() > 0) {
                    opens.append(", ");
                }
                opens.append(Component.translatable(c.nameKey()).getString());
            }
        }
        if (opens.length() > 0) {
            out.add(SkillstoneScreen.tr("proficiency.stone.opens", opens.toString()));
        }
        int fed = 0;
        for (Synergy syn : Synergies.all()) {
            for (Synergy.Need need : syn.requires()) {
                if (need.skill() == skill && need.talentId().equals(t.id())) {
                    if (fed++ < 2) {
                        out.add(SkillstoneScreen.tr("proficiency.stone.feeds", Component.translatable(syn.nameKey()).getString(),
                                Math.min(skills.rank(t), need.minRank()), need.minRank()));
                    }
                }
            }
        }
        if (fed == 0 && t.kind() != Talent.Kind.ROOT) {
            out.add(SkillstoneScreen.tr("proficiency.stone.feeds_none"));
        }
        if (!t.materials().isEmpty() && !skills.hasPaid(t)) {
            out.add(SkillstoneScreen.tr("proficiency.stone.materials"));
        }
        return out;
    }

    private static String signed(double fraction) {
        long percent = Math.round(fraction * 100);
        return (percent >= 0 ? "+" : "") + percent + "%";
    }

    // ---- Input ----------------------------------------------------------------------------------

    /** A click on socket {@code index}: one rank (Shift: fill), or the refusal with its reason. */
    void click(PlayerSkills skills, int index, int[] h, long now) {
        List<Talent> tree = Talents.of(skill);
        if (index < 0 || index >= tree.size()) {
            return;
        }
        Talent t = tree.get(index);
        focus = t;
        descCache = null;
        TalentOutcome outcome = skills.check(t);
        int x = nodeX(t);
        int y = nodeY(t);
        int hh = half(t);
        if (outcome == TalentOutcome.OK || outcome == TalentOutcome.MISSING_MATERIALS) {
            int clicks = Screen.hasShiftDown() ? t.maxRank() - skills.rank(t) : 1;
            for (int i = 0; i < Math.max(1, clicks); i++) {
                StoneNet.unlock(t);
            }
            s.click.charge(t.key(), x - hh, y - hh, 2 * hh, 2 * hh, Stone.ember(0.75f), now);
            SkillstoneScreen.sound(SoundEvents.STONE_HIT, 0.7f, 0.5f);
            ClickFx.clickIfOff();
        } else {
            reasonNode = t;
            reason = outcome == TalentOutcome.MAXED ? SkillstoneScreen.tr("proficiency.stone.node_full")
                    : TalentService.explain(skills, t, outcome).getString();
            reasonAt = now;
            s.refuse(t.key(), new int[] {7, index, x - hh, y - hh, 2 * hh, 2 * hh}, null, now);
        }
    }

    boolean scroll(double mx, double my, double delta) {
        if (scrollMax <= 0 || mx < s.ix0 || mx >= rx0 || my < viewTop || my >= viewBot) {
            return false;
        }
        scroll = SkillstoneMath.clampScroll(scroll - (int) Math.round(delta * rowStep / 2.0), scrollMax);
        return true;
    }

    boolean drag(double mx, double my, double dy) {
        if (scrollMax <= 0 || mx < s.ix0 || mx >= rx0 || my < viewTop || my >= viewBot) {
            return false;
        }
        scroll = SkillstoneMath.clampScroll(scroll - (int) Math.round(dy), scrollMax);
        return true;
    }

    /** For the frame sheets: the landing of the last rank of {@code id}. */
    void demoLand(String id, long now) {
        Talent t = Talents.get(skill, id);
        if (t != null) {
            focus = t;
            land(t, t.maxRank() - 1, t.maxRank(), now);
        }
    }

    /** For the layout demo: the focused node by id. */
    void focus(String id) {
        focus = Talents.get(skill, id);
        descCache = null;
    }
}
