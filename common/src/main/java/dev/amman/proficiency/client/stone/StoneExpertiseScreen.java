package dev.amman.proficiency.client.stone;

import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.client.ClickFx;
import dev.amman.proficiency.client.ClickFxMath;
import dev.amman.proficiency.client.ExpertiseScreen;
import dev.amman.proficiency.client.SkillstoneMath;
import dev.amman.proficiency.net.ExpertisePayload;
import dev.amman.proficiency.perk.Expertise;
import dev.amman.proficiency.perk.ExpertiseNode;
import dev.amman.proficiency.perk.ExpertiseService;
import dev.amman.proficiency.perk.Talent;
import dev.amman.proficiency.perk.TalentOutcome;
import dev.amman.proficiency.skill.PlayerSkills;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Expertise tree as a slab page (1.32.0): the one grid across every skill carved like a talent tree. Coal
 * sockets on dark grooves that run molten once a parent is full, ranked sockets filled with hatched molten metal,
 * full ones burning, rank pips under each, a branch mark in the socket's corner. Above, the points and where they
 * come from, with the unspent points as coals in a brazier; on the right, a sunk slate of what the tree gives
 * now and the respec plate (two clicks). Click a socket for one rank, Shift-click to fill it; the overload
 * feedback is the Tree tab's (pour, flint sparks, flare, "+1 rank", shake; capstone flare and edge glow;
 * refusal wiggle, red flash, thunk and the reason).
 */
public final class StoneExpertiseScreen extends StoneSheet {

    private static final int NODE = 1;
    private static final int RESPEC = 2;
    private static final int COLUMNS = 5;
    private static final int ROWS = 6;
    private static final long FLARE_MS = 520;
    private static final long POUR_MS = 260;
    private static final long CONFIRM_MS = 4000;

    private int gx0, gy0, colStep, rowStep, half, rx0, headH;
    private final Map<String, Integer> seen = new HashMap<>();
    private final Map<String, Long> filledAt = new HashMap<>();
    private final Map<String, Long> flareAt = new HashMap<>();
    private final Map<String, Integer> flareTier = new HashMap<>();
    private final Map<String, Long> pourAt = new HashMap<>();
    private ExpertiseNode hovered;
    private long confirmUntil;
    private int respecFrom = -1;
    /** The respec plate as it was clicked, and when; the charge is settled from these. */
    private int[] respecFx;
    private long respecAt;

    public StoneExpertiseScreen(Screen parent) {
        super(Component.translatable("proficiency.expertise.title"), parent);
    }

    @Override
    String crumb() {
        return SkillstoneScreen.tr("proficiency.stone.back");
    }

    @Override
    int maxHeight() {
        return SkillstoneMath.TREE_H;
    }

    @Override
    void layout() {
        int iw = ix1 - ix0;
        headH = small ? 26 : 34;
        rx0 = ix0 + Math.round(iw * (small ? 0.58f : 0.6f));
        int top = iy0 + headH + (small ? 6 : 10);
        int bottom = iy1 - (small ? 8 : 14);
        colStep = (rx0 - 12 - ix0 - (small ? 12 : 24)) / COLUMNS;
        rowStep = (bottom - top) / ROWS;
        int size = Math.min(colStep - 10, rowStep - 10);
        half = size >= 30 ? 15 : size >= 24 ? 12 : size >= 22 ? 11 : 9;
        gx0 = ix0 + (small ? 6 : 12) + colStep / 2;
        gy0 = top + rowStep / 2 - 2;
    }

    private int nodeX(ExpertiseNode n) {
        return gx0 + n.col() * colStep;
    }

    private int nodeY(ExpertiseNode n) {
        return gy0 + n.row() * rowStep;
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
        List<ExpertiseNode> nodes = Expertise.nodes();
        watch(skills, nodes, now);
        int pad = small ? 8 : 14;
        int top = iy0 + (small ? 5 : 9);
        // The points and where they come from; the unspent ones wait as coals in the brazier.
        String points = ExpertiseScreen.pointsLine(skills).getString();
        String sources = ExpertiseScreen.sourcesLine(skills).getString();
        int room = rx0 - 50 - (ix0 + pad);
        Stone.text(g, this.font, Stone.fit(this.font, points, room), ix0 + pad, top, Stone.INK);
        Stone.text(g, this.font, Stone.fit(this.font, sources, room), ix0 + pad, top + (small ? 10 : 12), Stone.DIM);
        Stone.coals(g, rx0 - 46, top - 2, skills.expertiseAvailable());
        if (mx >= ix0 + pad && mx < rx0 && my >= top - 2 && my < top + headH - 6) {
            tooltip.addAll(ExpertiseScreen.headerTooltip(skills));
        }
        // Grooves first, then the sockets over them.
        hovered = null;
        for (ExpertiseNode n : nodes) {
            int h = half(n);
            if (mx >= nodeX(n) - h && mx < nodeX(n) + h && my >= nodeY(n) - h && my < nodeY(n) + h) {
                hovered = n;
            }
            for (String pid : n.parents()) {
                ExpertiseNode p = Expertise.byId(pid);
                if (p != null) {
                    groove(g, skills, p, n, now);
                }
            }
        }
        for (int i = 0; i < nodes.size(); i++) {
            node(g, skills, nodes.get(i), i, now);
        }
        marks(g, nodes, now);
        if (hovered != null) {
            tooltip.addAll(ExpertiseScreen.nodeTooltip(hovered, skills, skills.expertiseRank(hovered)));
        }
        side(g, skills, now, mx, my, pad);
    }

    private int half(ExpertiseNode n) {
        return n.kind() == Talent.Kind.CAPSTONE ? (half >= 11 ? 15 : 12) : half;
    }

    private void watch(PlayerSkills skills, List<ExpertiseNode> nodes, long now) {
        for (ExpertiseNode n : nodes) {
            int rank = skills.expertiseRank(n);
            Integer before = seen.put(n.id(), rank);
            if (before != null && rank > before) {
                land(n, before, rank, now);
            }
        }
        if (respecFrom > 0 && respecFx != null && skills.expertiseSpent() >= respecFrom
                && now - respecAt > dev.amman.proficiency.client.ClickFxMath.PENDING_MS) {
            // No answer in time: ClickFx already wiggled the plate; say why and forget the respec.
            say(respecFx, SkillstoneScreen.tr("proficiency.stone.respec_refused"), now);
            respecFrom = -1;
            respecFx = null;
        }
        if (respecFrom > 0 && skills.expertiseSpent() < respecFrom && respecFx != null) {
            // The respec landed: the points pour back into the brazier.
            click.success(key(respecFx), respecFx[2], respecFx[3], respecFx[4], respecFx[5], Stone.ember(0.8f), 1, 1f, now);
            shake(now, 2.5f);
            SkillstoneScreen.sound(SoundEvents.FIRECHARGE_USE, 0.7f, 0.5f);
            respecFrom = -1;
            respecFx = null;
            seen.clear();
        }
    }

    private void land(ExpertiseNode n, int before, int rank, long now) {
        int x = nodeX(n);
        int y = nodeY(n);
        int h = half(n);
        boolean full = rank >= n.maxRank();
        int tier = full ? (n.kind() == Talent.Kind.CAPSTONE ? 2 : 1) : 0;
        click.success(n.id(), x - h, y - h, 2 * h, 2 * h, Stone.ember(0.75f), tier, rank / (float) n.maxRank(), now);
        if (!ClickFx.on()) {
            return;
        }
        filledAt.put(n.id(), now);
        flareAt.put(n.id(), now);
        flareTier.put(n.id(), tier);
        if (before == 0) {
            pourAt.put(n.id(), now);
        }
        SkillstoneScreen.sound(SoundEvents.FLINTANDSTEEL_USE, 1.2f + 0.1f * rank, 0.55f);
        if (tier >= 2) {
            SkillstoneScreen.sound(SoundEvents.FIRECHARGE_USE, 0.8f, 0.45f);
            edgeGlow(now);
            shake(now, 4f);
        } else {
            shake(now, full ? 2.5f : 1.5f);
        }
        if (full) {
            boolean opens = false;
            for (ExpertiseNode child : Expertise.nodes()) {
                if (child.parents().contains(n.id())) {
                    opens = true;
                    int ch = half(child);
                    float dist = (float) Math.hypot(nodeX(child) - x, nodeY(child) - y);
                    click.ripple(child.id(), nodeX(child) - ch, nodeY(child) - ch, 2 * ch, 2 * ch, Stone.ember(0.75f), now,
                            ClickFxMath.MIN_CHARGE_MS + ClickFxMath.rippleDelay(dist));
                }
            }
            if (opens && tier < 2) {
                riseShake(now + ClickFxMath.MIN_CHARGE_MS);
            }
        }
    }

    private static void seg(GuiGraphics g, int x0, int y0, int x1, int y1, boolean lit) {
        int a = Math.min(x0, x1);
        int b = Math.max(x0, x1);
        int c = Math.min(y0, y1);
        int d = Math.max(y0, y1);
        if (!lit) {
            g.fill(a, c, b + 3, d + 3, Stone.argb(120, 0x968278));
            g.fill(a - 1, c - 1, b + 2, d + 2, Stone.argb(255, 0x161110));
            return;
        }
        g.fill(a - 3, c - 3, b + 4, d + 4, Stone.argb(70, Stone.ember(0.5f)));
        g.fill(a, c, b + 1, d + 1, Stone.argb(255, Stone.ember(0.86f)));
    }

    /** Parent's bottom, down, across, down into the child; molten once the parent is full and the child ranked. */
    private void groove(GuiGraphics g, PlayerSkills skills, ExpertiseNode p, ExpertiseNode c, long now) {
        int ax = nodeX(p);
        int ay = nodeY(p) + half(p);
        int bx = nodeX(c);
        int by = nodeY(c) - half(c);
        int hy = Math.max(ay + 2, Math.min(nodeY(c) - Math.round(rowStep * 0.4f), by - 2));
        seg(g, ax, ay, ax, hy, false);
        seg(g, ax, hy, bx, hy, false);
        seg(g, bx, hy, bx, by, false);
        float t = skills.isExpertiseFull(p) && skills.expertiseRank(c) > 0 ? 1f : 0f;
        Long at = pourAt.get(c.id());
        if (at != null && t > 0f) {
            t = SkillstoneMath.pour(now - at - ClickFxMath.MIN_CHARGE_MS, POUR_MS);
            if (t >= 1f) {
                pourAt.remove(c.id());
            }
        }
        if (t <= 0f) {
            return;
        }
        int l1 = hy - ay;
        int l2 = Math.abs(bx - ax);
        int l3 = by - hy;
        float run = t * Math.max(1, l1 + l2 + l3);
        seg(g, ax, ay, ax, ay + Math.round(Math.min(run, l1)), true);
        run -= l1;
        if (run > 0 && l2 > 0) {
            seg(g, ax, hy, ax + Integer.signum(bx - ax) * Math.round(Math.min(run, l2)), hy, true);
        }
        run -= l2;
        if (run > 0) {
            seg(g, bx, hy, bx, hy + Math.round(Math.min(run, l3)), true);
        }
    }

    private void node(GuiGraphics g, PlayerSkills skills, ExpertiseNode n, int index, long now) {
        int x = nodeX(n);
        int y = nodeY(n);
        int h = half(n);
        int rank = skills.expertiseRank(n);
        boolean full = rank >= n.maxRank();
        TalentOutcome outcome = skills.checkExpertise(n);
        boolean open = outcome == TalentOutcome.OK;
        Long fillStart = filledAt.get(n.id());
        float fill = fillStart == null ? 1f : SkillstoneMath.fill(now - fillStart - ClickFxMath.MIN_CHARGE_MS);
        if (fillStart != null && fill >= 1f && now - fillStart > 2000) {
            filledAt.remove(n.id());
        }
        float shown = fill >= 1f ? rank : Math.max(0, rank - 1) + fill;
        click.begin(g, n.id(), x - h, y - h, 2 * h, 2 * h, now);
        if (open && rank == 0) {
            Stone.light(g, x, y, h + 6, h + 6, Stone.WARM, 0.35f);
        }
        Stone.socket(g, x - h, y - h, 2 * h, 2 * h, shown / n.maxRank(), n == hovered, open || n == hovered);
        int i0 = x - h + 4;
        int j0 = y - h + 4;
        int in = 2 * h - 8;
        if (rank > 0 && !(full && fill >= 1f)) {
            int fh = Math.round(in * Math.min(1f, shown / n.maxRank()));
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
                g.fill(i0, j0 + in - fh, i0 + in, j0 + in - fh + 1, Stone.argb(255, Stone.ember(0.97f)));
            }
        }
        if (full) {
            int frame = (int) (((now / 100L) + n.col() * 3L + n.row()) % 8);
            Stone.blitAlpha(g, Stone.tex("flame_" + in), i0, j0, in, in, 0f, frame * in, in, in * 8, fill);
        }
        // The branch: a small mark in the socket's corner in the branch's colour (gold for every skill).
        int accent = ExpertiseScreen.accent(n) & 0xFFFFFF;
        g.fill(x - h + 2, y - h + 2, x - h + 5, y - h + 5, Stone.argb(255, accent));
        if (n.kind() == Talent.Kind.CAPSTONE) {
            Stone.outlineRound(g, x - h - 2, y - h - 2, 2 * h + 4, 2 * h + 4, 4,
                    Stone.argb(255, full ? Stone.ember(0.9f) : Stone.shade(Stone.HEAD, 0.6f)));
        }
        click.end(g, n.id(), x - h, y - h, 2 * h, 2 * h, now);
        int count = n.maxRank();
        int px0 = x - (count * 4) / 2 + 1;
        for (int r = 0; r < count; r++) {
            int c = r < Math.floor(shown + 0.001f) ? Stone.ember(0.85f) : 0x1A1413;
            g.fill(px0 + r * 4, y + h + 3, px0 + r * 4 + 3, y + h + 6, Stone.argb(255, c));
        }
        if (rowStep - 2 * h >= 26) {
            // The node's name under its pips, as on the Tree tab; the full name is in the hover.
            String name = Component.translatable(n.nameKey()).getString();
            Stone.textCentred(g, this.font, Stone.fit(this.font, name, colStep - 6), x + 1, y + h + 9,
                    full ? Stone.GOLD : rank > 0 || open ? Stone.INK : Stone.DIM);
        }
        hits.add(new int[] {NODE, index, x - h, y - h, 2 * h, 2 * h});
    }

    /** The fire flare and the rising "+1 rank" of a landing rank. */
    private void marks(GuiGraphics g, List<ExpertiseNode> nodes, long now) {
        for (ExpertiseNode n : nodes) {
            Long at = flareAt.get(n.id());
            if (at == null) {
                continue;
            }
            long dt = now - at - ClickFxMath.MIN_CHARGE_MS;
            if (dt > SkillstoneMath.RANK_MARK_MS) {
                flareAt.remove(n.id());
                continue;
            }
            if (dt < 0) {
                continue;
            }
            int x = nodeX(n);
            int y = nodeY(n);
            int tier = flareTier.getOrDefault(n.id(), 0);
            if (dt < FLARE_MS) {
                float k = dt / (float) FLARE_MS;
                int r = Math.round((16 + 34 * k) * (tier >= 2 ? 1.8f : 1f));
                Stone.light(g, x, y, r, r, 0xC86E1E, (1.2f - 0.7f * k) * (1f - k));
            }
            float a = SkillstoneMath.markAlpha(dt);
            if (a > 0.05f) {
                String mark = SkillstoneScreen.tr(tier >= 2 ? "proficiency.stone.mark_full" : "proficiency.stone.mark_rank");
                int rise = Math.round(SkillstoneMath.markRise(dt) * 0.6f);
                g.drawString(this.font, mark, x + 1 - this.font.width(mark) / 2, y - half(n) - 10 - rise,
                        Stone.argb(Math.round(255 * a), 0xFFECAA), true);
            }
        }
    }

    /** The sunk slate of what the tree gives, and the respec plate under it. */
    private void side(GuiGraphics g, PlayerSkills skills, long now, int mx, int my, int pad) {
        int x0 = rx0;
        int x1 = ix1 - pad;
        int y0 = iy0 + (small ? 5 : 9);
        int ph = small ? 16 : 20;
        int py = iy1 - (small ? 6 : 12) - ph;
        Stone.well(g, x0, y0, x1 - x0, py - 6 - y0);
        int x = x0 + 8;
        int y = y0 + 8;
        Stone.text(g, this.font, Stone.fit(this.font, SkillstoneScreen.tr("proficiency.expertise.gives").toUpperCase(java.util.Locale.ROOT),
                x1 - x - 8), x, y, Stone.HEAD);
        y += small ? 12 : 15;
        List<ExpertiseScreen.SideLine> lines = ExpertiseScreen.bonusTexts(skills.expertiseRanks());
        int line = small ? 10 : 11;
        int limit = py - 14;
        if (lines.isEmpty()) {
            Stone.text(g, this.font, Stone.fit(this.font, SkillstoneScreen.tr("proficiency.expertise.gives.none"), x1 - x - 8), x, y,
                    Stone.DIM);
        }
        for (int i = 0; i < lines.size(); i++) {
            ExpertiseScreen.SideLine l = lines.get(i);
            if (y + line > limit && i < lines.size() - 1) {
                Stone.text(g, this.font, SkillstoneScreen.tr("proficiency.expertise.gives.more", lines.size() - i), x, y, Stone.DIM);
                break;
            }
            String shown = Stone.fit(this.font, l.text(), x1 - x - 8 - l.indent());
            Stone.text(g, this.font, shown, x + l.indent(), y, l.heading() ? Stone.DIM : Stone.INK);
            if (!shown.equals(l.text()) && mx >= x0 && mx < x1 && my >= y - 1 && my < y + line - 1) {
                tooltip.add(Component.literal(l.text()));
            }
            y += line;
        }
        int spent = skills.expertiseSpent();
        String label = now < confirmUntil ? SkillstoneScreen.tr("proficiency.expertise.respec.confirm")
                : SkillstoneScreen.tr("proficiency.expertise.respec.button", Expertise.respecPrice(spent));
        int w = Math.min(x1 - x0, this.font.width(label) + 20);
        int bx = x1 - w;
        button(g, now, mx, my, RESPEC, 0, bx, py, w, ph, label, spent > 0, now < confirmUntil);
        if (mx >= bx && mx < x1 && my >= py && my < py + ph) {
            tooltip.addAll(ExpertiseScreen.respecTooltip(skills));
        }
    }

    // ---- Input ----------------------------------------------------------------------------------

    @Override
    void hit(int[] h, long now, boolean shift) {
        PlayerSkills skills = skills();
        if (skills == null) {
            return;
        }
        if (h[0] == RESPEC) {
            if (skills.expertiseSpent() <= 0) {
                refuse(key(h), h, SkillstoneScreen.tr("proficiency.stone.respec_none"), now);
                return;
            }
            if (now < confirmUntil) {
                confirmUntil = 0;
                respecFrom = skills.expertiseSpent();
                respecFx = h.clone();
                respecAt = now;
                StoneNet.expertise(new ExpertisePayload(ExpertisePayload.RESPEC, ""));
                click.charge(key(h), h[2], h[3], h[4], h[5], Stone.ember(0.8f), now);
            } else {
                confirmUntil = now + CONFIRM_MS;
                click.arm(key(h), h[2], h[3], h[4], h[5], Stone.RED, now);
                SkillstoneScreen.sound(SoundEvents.STONE_HIT, 0.7f, 0.6f);
            }
            return;
        }
        if (h[0] != NODE || h[1] >= Expertise.nodes().size()) {
            return;
        }
        ExpertiseNode n = Expertise.nodes().get(h[1]);
        TalentOutcome outcome = skills.checkExpertise(n);
        if (outcome != TalentOutcome.OK) {
            refuse(n.id(), h, ExpertiseService.explain(skills, n, outcome).getString(), now);
            return;
        }
        int clicks = shift ? n.maxRank() - skills.expertiseRank(n) : 1;
        for (int i = 0; i < Math.max(1, clicks); i++) {
            StoneNet.expertise(new ExpertisePayload(ExpertisePayload.INVEST, n.id()));
        }
        click.charge(n.id(), h[2], h[3], h[4], h[5], Stone.ember(0.75f), now);
    }

    /** For the layout demo: the centre of node {@code index}. */
    public int[] nodePoint(int index) {
        return point(NODE, index);
    }
}
