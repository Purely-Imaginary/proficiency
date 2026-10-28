package dev.amman.proficiency.client.stone;

import com.mojang.math.Axis;
import dev.amman.proficiency.client.ClickFx;
import dev.amman.proficiency.client.DotFlight;
import dev.amman.proficiency.client.MasteryStars;
import dev.amman.proficiency.client.SkillIcons;
import dev.amman.proficiency.client.SkillPalette;
import dev.amman.proficiency.client.SkillstoneMath;
import dev.amman.proficiency.client.TalentTreeScreen;
import dev.amman.proficiency.client.XpGainDots;
import dev.amman.proficiency.haul.HaulMath;
import dev.amman.proficiency.haul.HaulRating;
import dev.amman.proficiency.haul.HaulSummary;
import dev.amman.proficiency.skill.Skill;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;

/**
 * Today's Haul (1.44.0): the card that shows what a player earned since the last one. A carved Skillstone slab
 * under {@code ui.style = MODERN}, the classic panel under CLASSIC. It plays once: each skill row's bar fills as
 * {@link XpGainDots} stream into it from its XP figure (the HUD bar's dots, {@link DotFlight}), one row after
 * another; a level or a Mastery star crossed pops the row's icon with the {@link ClickFx} success burst and chime;
 * the totals count up under the rows with ticking clicks; the rating stamp ({@link HaulRating}) slams down last.
 * The timeline is {@link HaulMath#timeline}, in milliseconds from the opening, so it plays the same at any frame
 * rate.
 *
 * <p>Any key skips to the end; Esc closes once it has played (or at once when skipping is done); a click on a
 * skill row opens that skill's Face (MODERN) or tree (CLASSIC). A screen, never a pause screen and never a HUD:
 * it takes no input from the world and closes in one call, so a bed's sleep goes on underneath it (the bed's own
 * chat screen comes back when it closes). {@code ui.juicyFeedback} off, or Distortion Effects at 0%, keeps the
 * fills, the counts, the flashes and the sounds and drops every movement (no dots, no pops, no slam).
 *
 * <p>Shared verbatim with Forge 1.20.1 ({@code copySharedHearth}): no version-specific API.
 */
public final class HaulScreen extends Screen {

    private static final int ROW_KIND = 1;
    private static final int CLOSE_KIND = 2;

    private final HaulSummary summary;
    private final boolean modern;
    private final ClickFx click = new ClickFx();
    private final long openedAt = SkillstoneScreen.clock();
    private final List<int[]> hits = new ArrayList<>();
    private final List<Component> tooltip = new ArrayList<>();

    private List<HaulSummary.Row> rows = List.of();
    private int more;
    private HaulMath.Timeline timeline;
    private XpGainDots[] dots = new XpGainDots[0];
    private int[] launched = new int[0];
    private int[] floorSeen = new int[0];
    private int[] xpShown = new int[0];
    private long[] popAt = new long[0];
    private final List<Total> totals = new ArrayList<>();

    /** Added to the clock when a key skipped to the end. */
    private long skip;
    private boolean thud;
    private boolean stamped;
    private long stampHitAt = Long.MIN_VALUE / 2;
    private long lastTick = Long.MIN_VALUE / 2;
    private long lastOrb = Long.MIN_VALUE / 2;
    private int lastTotalShown = -1;

    // Layout (screen px).
    private int sx, sy, sw, sh, ix0, iy0, ix1, iy1;
    private boolean small;
    private int rowH;
    private int rowsTop;
    private int totalsTop;
    private int totalCols;
    private int totalW;
    private int totalH;
    private int footTop;

    private record Total(String key, double value, int kind, Component tip) {
    }

    private static final int PLAIN = 0;
    private static final int PLUS = 1;
    private static final int TEMPO = 2;

    public HaulScreen(HaulSummary summary) {
        super(Component.translatable("proficiency.haul.title"));
        this.summary = summary;
        this.modern = SkillstoneScreen.modern();
    }

    /** Opens the card over whatever is up (a bed's chat screen, chat, nothing). */
    public static void open(Minecraft mc, HaulSummary summary) {
        if (summary != null) {
            mc.setScreen(new HaulScreen(summary));
        }
    }

    // ---- Layout ---------------------------------------------------------------------------------

    @Override
    protected void init() {
        small = this.width < 420 || this.height < 300;
        rowH = small ? 19 : 23;
        sw = Math.max(Math.min(this.width - 8, 240), Math.min(this.width - 16, small ? 330 : 400));
        buildTotals();
        int pad = modern ? SkillstoneMath.RIM : 6;
        int inner = sw - 2 * pad - 12;
        // A plate is as wide as its longest label needs, so a narrow slab takes fewer columns, not cut words.
        int widest = 0;
        for (Total total : totals) {
            widest = Math.max(widest, this.font.width(
                    Component.translatable("proficiency.haul.total." + total.key()).getString()));
        }
        int minTotal = Math.max(small ? 64 : 84, widest + 10);
        totalCols = Math.max(1, Math.min(totals.size(), inner / minTotal));
        int totalRows = (totals.size() + totalCols - 1) / totalCols;
        totalH = small ? 24 : 28;
        totalW = (inner - (totalCols - 1) * 4) / totalCols;
        int head = 14;
        int totalsBlock = totalRows * (totalH + 4);
        int foot = small ? 26 : 30;
        // From the slab's top edge to the first row: the rim (or the classic title line) and the "since" line.
        int above = modern ? pad + 4 + head : 20 + head;
        int fixed = above + 4 + totalsBlock + 2 + foot + pad;
        int maxH = this.height - (modern ? 24 : 8);
        List<HaulSummary.Row> all = summary.rows();
        int fit = HaulMath.rowsThatFit(all.size(), maxH - fixed, rowH, 12);
        // After a resize the rows animated so far stay the most there are: the timeline was built for them.
        int cap = timeline == null ? all.size() : dots.length;
        rows = all.subList(0, Math.min(Math.min(all.size(), fit), cap));
        more = all.size() - rows.size();
        int rowsBlock = rows.isEmpty() ? 14 : rows.size() * rowH + (more > 0 ? 12 : 0);
        sh = Math.min(this.height - 8, fixed + rowsBlock);
        sx = (this.width - sw) / 2;
        sy = Math.max(modern ? 14 : 4, (this.height - sh) / 2 + (modern ? 8 : 0));
        ix0 = sx + pad;
        iy0 = sy + pad;
        ix1 = sx + sw - pad;
        iy1 = sy + sh - pad;
        rowsTop = sy + above;
        totalsTop = rowsTop + rowsBlock + 4;
        footTop = iy1 - foot;

        // The animation state is built once; a resize keeps the clock and re-lays the rows only.
        if (timeline == null) {
            double[] from = new double[rows.size()];
            double[] to = new double[rows.size()];
            for (int i = 0; i < rows.size(); i++) {
                from[i] = rows.get(i).from();
                to[i] = rows.get(i).to();
            }
            timeline = HaulMath.timeline(from, to, !totals.isEmpty());
            long now = SkillstoneScreen.clock();
            dots = new XpGainDots[rows.size()];
            launched = new int[rows.size()];
            floorSeen = new int[rows.size()];
            xpShown = new int[rows.size()];
            popAt = new long[rows.size()];
            for (int i = 0; i < rows.size(); i++) {
                dots[i] = new XpGainDots();
                dots[i].snap(from[i], now);
                floorSeen[i] = (int) Math.floor(from[i]);
                popAt[i] = Long.MIN_VALUE / 2;
            }
            if (!ClickFx.motion()) {
                // No movement: the slab is already on the table.
                thud = true;
            }
        }
    }

    private void buildTotals() {
        totals.clear();
        totals.add(new Total("xp", summary.totalXp(), PLAIN, null));
        if (summary.totalLevels() > 0) {
            totals.add(new Total("levels", summary.totalLevels(), PLUS, null));
        }
        if (summary.totalStars() > 0) {
            totals.add(new Total("stars", summary.totalStars(), PLUS, null));
        }
        if (summary.points() > 0) {
            totals.add(new Total("points", summary.points(), PLUS, null));
        }
        if (summary.tempoSkill() >= 0 && summary.tempoPermille() > 1000) {
            Skill skill = Skill.VALUES[summary.tempoSkill()];
            totals.add(new Total("tempo", summary.tempoPermille(), TEMPO, Component.translatable(
                    "proficiency.haul.tempo.tip", tempo(summary.tempoPermille()),
                    Component.translatable(skill.translationKey()), summary.tempoSteps())));
        }
        if (summary.found() > 0) {
            totals.add(new Total("found", summary.found(), PLAIN, null));
        }
        if (summary.synergies() > 0) {
            totals.add(new Total("synergies", summary.synergies(), PLAIN, null));
        }
        if (summary.charge() >= 0.5f) {
            totals.add(new Total("charge", summary.charge(), PLUS, null));
        }
    }

    private static String tempo(double permille) {
        return String.format(java.util.Locale.ROOT, "%.2f", permille / 1000.0);
    }

    // ---- Clock ----------------------------------------------------------------------------------

    /** Milliseconds into the card. */
    private long t(long now) {
        return now - openedAt + skip;
    }

    private boolean done(long now) {
        return timeline == null || t(now) >= timeline.end();
    }

    /** Jumps to the end: every bar full, the totals counted, the stamp down (with one thud if it was not yet). */
    private void skipToEnd(long now) {
        if (done(now)) {
            return;
        }
        skip += timeline.end() - t(now);
        for (int i = 0; i < rows.size(); i++) {
            launched[i] = timeline.bursts()[i];
            dots[i].snap(rows.get(i).to(), now);
            floorSeen[i] = (int) Math.floor(rows.get(i).to());
            xpShown[i] = Math.round(rows.get(i).xp());
        }
        thud = true;
        if (!stamped) {
            stamped = true;
            stampHitAt = now;
            sound(SoundEvents.STONE_PLACE, 0.7f, 0.6f);
        }
    }

    private static void sound(SoundEvent event, float pitch, float volume) {
        SkillstoneScreen.sound(event, pitch, volume);
    }

    // ---- Render ---------------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long now = SkillstoneScreen.clock();
        long t = t(now);
        hits.clear();
        tooltip.clear();
        g.fillGradient(0, 0, this.width, this.height, 0x90000000, 0xB8000000);
        boolean motion = ClickFx.motion();

        float dx = 0;
        float dy = 0;
        if (motion) {
            if (t < HaulMath.INTRO_MS) {
                float k = 1f - t / (float) HaulMath.INTRO_MS;
                dy -= 16f * k * k;
            }
            dx += SkillstoneMath.shakeX(now - stampHitAt, 2.5f);
            dy += SkillstoneMath.shakeY(now - stampHitAt, 2.5f);
        }
        if (!thud && t >= HaulMath.INTRO_MS - 40) {
            thud = true;
            if (modern) {
                sound(SoundEvents.STONE_PLACE, 0.85f, 0.55f);
            }
        }
        int ox = Math.round(dx);
        int oy = Math.round(dy);
        g.pose().pushPose();
        g.pose().translate(ox, oy, 0);
        int mx = mouseX - ox;
        int my = mouseY - oy;

        if (modern) {
            Stone.slab(g, sx, sy, sw, sh, SkillstoneMath.RIM);
            rim(g, now, mx, my);
        } else {
            g.fill(sx, sy, sx + sw, sy + sh, SkillPalette.PANEL);
            border(g, sx, sy, sw, sh, SkillPalette.PANEL_BORDER);
            String title = this.title.getString();
            g.drawString(this.font, title, sx + (sw - this.font.width(title)) / 2, sy + 6, SkillPalette.TEXT, false);
            closeButton(g, now, mx, my, sx + sw - 6 - 12, sy + 4, 12, 12);
        }
        subtitle(g);
        rows(g, now, t, mx, my, motion);
        totals(g, now, t, mx, my);
        footer(g, now, t, motion);
        g.pose().popPose();

        click.render(g, now, 1f);
        if (!tooltip.isEmpty()) {
            Stone.tooltip(g, this.font, tooltip, mouseX, mouseY, this.width);
        }
    }

    /** MODERN: the crumb back (closes) and the name plate on the rim's top edge, as on every slab page. */
    private void rim(GuiGraphics g, long now, int mx, int my) {
        int y = iy0 - 15;
        String back = Component.translatable("proficiency.haul.close").getString();
        int bw = Math.max(small ? 56 : 66, this.font.width(back) + 14);
        boolean hov = mx >= ix0 + 4 && mx < ix0 + 4 + bw && my >= y && my < y + 18;
        click.begin(g, "close", ix0 + 4, y, bw, 18, now);
        Stone.block(g, ix0 + 4, y, bw, 18, 3, 2, false, Stone.shade(Stone.STONE, hov ? 1.15f : 0.95f));
        Stone.textCentred(g, this.font, back, ix0 + 4 + bw / 2, y + 5, hov ? Stone.INK : Stone.DIM);
        click.end(g, "close", ix0 + 4, y, bw, 18, now);
        hits.add(new int[] {CLOSE_KIND, 0, ix0 + 4, y, bw, 18});
        String title = this.title.getString();
        int pw = Math.min(ix1 - ix0 - 2 * (bw + 12), this.font.width(title) + 28);
        if (pw >= 40) {
            int px = (sx + sx + sw) / 2 - pw / 2;
            Stone.light(g, px + pw / 2, y + 9, pw / 2 + 10, 14, Stone.WARM, 0.45f);
            Stone.plate(g, this.font, px, y, pw, 18, Stone.fit(this.font, title, pw - 10), Stone.GOLD, false);
        }
    }

    private void closeButton(GuiGraphics g, long now, int mx, int my, int x, int y, int w, int h) {
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + h;
        g.fill(x, y, x + w, y + h, hov ? 0xFF3A424D : SkillPalette.TRACK);
        g.drawString(this.font, "x", x + (w - this.font.width("x")) / 2, y + 2, hov ? SkillPalette.TEXT : SkillPalette.TEXT_DIM,
                false);
        hits.add(new int[] {CLOSE_KIND, 0, x, y, w, h});
    }

    private void subtitle(GuiGraphics g) {
        int minutes = summary.minutes();
        String since = minutes <= 0 ? Component.translatable("proficiency.haul.since.now").getString()
                : minutes < 60 ? Component.translatable("proficiency.haul.since.minutes", minutes).getString()
                : Component.translatable("proficiency.haul.since.hours", minutes / 60, minutes % 60).getString();
        int y = rowsTop - 12;
        int colour = modern ? Stone.HEAD : SkillPalette.TEXT_DIM;
        String shown = Stone.fit(this.font, since, ix1 - ix0 - 12);
        if (modern) {
            Stone.text(g, this.font, shown, ix0 + 6, y, colour);
        } else {
            g.drawString(this.font, shown, ix0 + 4, y, colour, false);
        }
    }

    // ---- Rows -----------------------------------------------------------------------------------

    private void rows(GuiGraphics g, long now, long t, int mx, int my, boolean motion) {
        if (rows.isEmpty()) {
            String none = Component.translatable("proficiency.haul.none").getString();
            int c = modern ? Stone.DIM : SkillPalette.TEXT_DIM;
            g.drawString(this.font, none, (ix0 + ix1 - this.font.width(none)) / 2, rowsTop + 2, c, modern);
            return;
        }
        int iconX = ix0 + 5;
        int textX = ix0 + (small ? 26 : 30);
        int right = ix1 - 6;
        int xpW = this.font.width(Component.translatable("proficiency.haul.row.xp", "00000").getString());
        for (int i = 0; i < rows.size(); i++) {
            HaulSummary.Row row = rows.get(i);
            Skill skill = row.skillValue();
            int y = rowsTop + i * rowH;
            int accent = SkillPalette.procAccent(skill) & 0xFFFFFF;
            double shownUnits = advance(i, row, now, t, motion);
            boolean started = t >= timeline.start()[i];
            boolean hov = mx >= ix0 && mx < ix1 && my >= y && my < y + rowH;

            // Row backing: a faint plate for the hovered row and the one filling now.
            boolean live = started && t < timeline.launch(i, timeline.bursts()[i] - 1) + HaulMath.SETTLE_MS;
            if (hov || live) {
                int a = hov ? 70 : 40;
                if (modern) {
                    Stone.fillRound(g, ix0 + 2, y, ix1 - ix0 - 4, rowH - 1, 3, Stone.argb(a, 0xFFE6C8));
                } else {
                    g.fill(ix0 + 1, y, ix1 - 1, y + rowH - 1, Stone.argb(a / 2, 0xFFFFFF));
                }
            }

            // The icon, in a small socket under MODERN; the pop when a level lands.
            int iconSize = 16;
            int iy = y + (rowH - iconSize) / 2;
            Object key = "row" + i;
            if (modern) {
                float lit = Math.max(0f, 1f - (now - popAt[i]) / 600f);
                Stone.socket(g, iconX - 1, iy - 1, iconSize + 2, iconSize + 2, started ? 0.25f + lit : 0f, false, hov);
            }
            click.begin(g, key, iconX, iy, iconSize, iconSize, now);
            SkillIcons.draw(g, skill, iconX + (modern ? 0 : 0), iy, iconSize, started || !motion ? 255 : 120);
            click.end(g, key, iconX, iy, iconSize, iconSize, now);

            // Name and level, then the XP figure on the right.
            int ty = y + (small ? 1 : 3);
            int textC = modern ? Stone.INK : SkillPalette.TEXT;
            int dimC = modern ? Stone.DIM : SkillPalette.TEXT_DIM;
            String level = levelText(row, shownUnits);
            int levelW = this.font.width(level);
            int nameRoom = right - xpW - 8 - textX - levelW - 6;
            String name = Stone.fit(this.font, Component.translatable(skill.translationKey()).getString(), nameRoom);
            draw(g, name, textX, ty, textC);
            int lvX = textX + this.font.width(name) + 6;
            boolean crossed = HaulMath.levelAt(shownUnits) > HaulMath.levelAt(row.from())
                    || HaulMath.starsAt(shownUnits) > HaulMath.starsAt(row.from());
            draw(g, level, lvX, ty, crossed ? (modern ? Stone.GOLD : 0xFFF2D98A) : dimC);
            if (shownUnits >= 100) {
                int stars = Math.min(5, HaulMath.starsAt(shownUnits));
                if (stars > 0 && lvX + levelW + 3 + MasteryStars.width(stars) < right - xpW - 4) {
                    MasteryStars.draw(g, this.font, lvX + levelW + 3, ty, stars, 255);
                }
            }
            double fraction = row.to() > row.from() ? (shownUnits - row.from()) / (row.to() - row.from()) : 1;
            int xpNow = HaulMath.countUp(row.xp(), fraction);
            if (xpNow > xpShown[i]) {
                if (now - lastOrb >= HaulMath.TICK_GAP_MS) {
                    lastOrb = now;
                    sound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.9f + 0.6f * (float) Math.min(1, fraction), 0.12f);
                }
                xpShown[i] = xpNow;
            }
            String xp = Component.translatable("proficiency.haul.row.xp", fmt(xpNow)).getString();
            int xpC = !started ? dimC : modern ? Stone.GOLD : 0xFFF2D98A;
            draw(g, xp, right - this.font.width(xp), ty, xpC);

            // The bar: the part of the current level filled, wrapping at each level the dots carry it past.
            int barY = y + rowH - (small ? 5 : 6);
            int barX0 = textX;
            int barX1 = right - xpW - 8;
            float fill = (float) (shownUnits - Math.floor(shownUnits));
            boolean maxed = shownUnits >= 100 + dev.amman.proficiency.skill.Mastery.maxStars();
            if (maxed) {
                fill = 1f;
            }
            boolean glow = live || now - popAt[i] < 400;
            if (modern) {
                Stone.groove(g, barX0, barX1, barY, fill, small ? 3 : 4, glow);
            } else {
                g.fill(barX0, barY - 1, barX1, barY + 2, SkillPalette.TRACK);
                int fx = barX0 + Math.round((barX1 - barX0) * fill);
                g.fill(barX0, barY - 1, fx, barY + 2, 0xFF000000 | accent);
            }
            int headX = barX0 + Math.round((barX1 - barX0) * fill);
            float flash = dots[i].flash(now);
            if (modern && (flash > 0 || dots[i].inAir() > 0)) {
                // The bar's head glows while the dots pour in.
                Stone.light(g, headX, barY, 10, 6, Stone.WARM, 0.5f + 0.6f * flash);
            }
            if (motion && dots[i].inAir() > 0) {
                int head = modern ? Stone.ember(0.95f) : 0xFFFFFF;
                int trail = modern ? Stone.ember(0.7f) : accent;
                new DotFlight(right - xpW / 2f, ty + 4, 3, 10, 5).draw(g, dots[i], headX, barY, trail, head, 255, now);
            } else {
                if (flash > 0) {
                    g.fill(headX - 1, barY - 2, headX + 1, barY + 2, Stone.argb(Math.round(255 * flash), 0xFFFFFF));
                }
            }
            hits.add(new int[] {ROW_KIND, i, ix0, y, ix1 - ix0, rowH});
            if (hov) {
                tooltip.add(Component.translatable("proficiency.haul.row.tip",
                        Component.translatable(skill.translationKey())));
            }
        }
        if (more > 0) {
            String line = Component.translatable("proficiency.haul.more", more).getString();
            int y = rowsTop + rows.size() * rowH + 2;
            draw(g, line, textX, y, modern ? Stone.DIM : SkillPalette.TEXT_DIM);
        }
    }

    /**
     * Moves row {@code i} on: fires the bursts that are due, lands dots, and pops the icon for each level (or star)
     * the shown bar crossed since the last frame. Returns the bar position to draw.
     */
    private double advance(int i, HaulSummary.Row row, long now, long t, boolean motion) {
        XpGainDots d = dots[i];
        int count = timeline.bursts()[i];
        while (launched[i] < count && t >= timeline.launch(i, launched[i])) {
            int k = launched[i];
            double a = k == 0 ? row.from() : HaulMath.Timeline.burstTo(row.from(), row.to(), k - 1, count);
            double b = HaulMath.Timeline.burstTo(row.from(), row.to(), k, count);
            if (motion) {
                d.gain(a, b, now);
            }
            launched[i]++;
        }
        double real = launched[i] == 0 ? row.from()
                : HaulMath.Timeline.burstTo(row.from(), row.to(), launched[i] - 1, count);
        double shown = d.update(real, now);
        int floor = (int) Math.floor(shown + 1e-6);
        if (floor > floorSeen[i]) {
            floorSeen[i] = floor;
            popAt[i] = now;
            int iconSize = 16;
            int y = rowsTop + i * rowH + (rowH - iconSize) / 2;
            boolean star = floor > 100;
            int colour = star ? MasteryStars.GOLD : modern ? Stone.ember(0.9f) : SkillPalette.procAccent(row.skillValue()) & 0xFFFFFF;
            if (ClickFx.on()) {
                click.success("row" + i, ix0 + 5, y, iconSize, iconSize, colour, star || floor == 100 ? 2 : 1,
                        Math.min(1f, (floor % 10) / 10f + 0.1f), now);
            } else {
                sound(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.2f + 0.04f * (floor % 10), 0.6f);
            }
        }
        return shown;
    }

    private String levelText(HaulSummary.Row row, double shownUnits) {
        int fromLv = HaulMath.levelAt(row.from());
        int nowLv = HaulMath.levelAt(shownUnits);
        if (nowLv > fromLv) {
            return Component.translatable("proficiency.haul.row.levels", fromLv, nowLv).getString();
        }
        return Component.translatable("proficiency.haul.row.level", nowLv).getString();
    }

    // ---- Totals ---------------------------------------------------------------------------------

    private void totals(GuiGraphics g, long now, long t, int mx, int my) {
        if (totals.isEmpty()) {
            return;
        }
        double f = timeline.totals(t);
        int shownXp = (int) Math.round(summary.totalXp() * f);
        if (shownXp != lastTotalShown && f > 0 && f < 1 && now - lastTick >= HaulMath.TICK_GAP_MS) {
            lastTick = now;
            sound(SoundEvents.UI_BUTTON_CLICK.value(), 1.3f + 0.7f * (float) f, 0.18f);
        }
        lastTotalShown = shownXp;
        int x0 = ix0 + 6;
        for (int n = 0; n < totals.size(); n++) {
            Total total = totals.get(n);
            int col = n % totalCols;
            int row = n / totalCols;
            int x = x0 + col * (totalW + 4);
            int y = totalsTop + row * (totalH + 4);
            String value = switch (total.kind()) {
                case PLUS -> "+" + fmt((int) Math.round(total.value() * f));
                case TEMPO -> "x" + tempo(1000 + (total.value() - 1000) * f);
                default -> fmt((int) Math.round(total.value() * f));
            };
            String label = Component.translatable("proficiency.haul.total." + total.key()).getString();
            boolean counted = f >= 1;
            if (modern) {
                Stone.well(g, x, y, totalW, totalH);
                int vc = counted ? Stone.GOLD : Stone.INK;
                Stone.textCentred(g, this.font, Stone.fit(this.font, value, totalW - 6), x + totalW / 2, y + 3, vc);
                Stone.textCentred(g, this.font, Stone.fit(this.font, label, totalW - 6), x + totalW / 2,
                        y + totalH - 11, Stone.DIM);
            } else {
                g.fill(x, y, x + totalW, y + totalH, 0xFF181C21);
                border(g, x, y, totalW, totalH, SkillPalette.PANEL_BORDER);
                int vc = counted ? 0xFFF2D98A : SkillPalette.TEXT;
                String v = Stone.fit(this.font, value, totalW - 6);
                g.drawString(this.font, v, x + (totalW - this.font.width(v)) / 2, y + 3, vc, false);
                String l = Stone.fit(this.font, label, totalW - 6);
                g.drawString(this.font, l, x + (totalW - this.font.width(l)) / 2, y + totalH - 11,
                        SkillPalette.TEXT_DIM, false);
            }
            if (total.tip() != null && mx >= x && mx < x + totalW && my >= y && my < y + totalH) {
                tooltip.add(total.tip());
            }
        }
    }

    // ---- Footer: the hint and the stamp ---------------------------------------------------------

    private void footer(GuiGraphics g, long now, long t, boolean motion) {
        boolean over = done(now);
        String hint = Component.translatable(over ? "proficiency.haul.hint.done" : "proficiency.haul.hint").getString();
        HaulRating rating = summary.rating();
        String stamp = Component.translatable(rating.translationKey()).getString().toUpperCase(java.util.Locale.ROOT);
        float scale = small ? 1.25f : 1.6f;
        int stampW = Math.round((this.font.width(stamp) + 12) * scale);
        int hintRoom = ix1 - ix0 - 12 - stampW - 8;
        int hy = footTop + (iy1 - footTop) / 2 - 4;
        draw(g, Stone.fit(this.font, hint, Math.max(20, hintRoom)), ix0 + 6, hy, modern ? Stone.DIM : SkillPalette.TEXT_DIM);

        long ms = t - timeline.stampAt();
        if (ms < 0) {
            return;
        }
        if (!stamped && HaulMath.stampHit(ms)) {
            stamped = true;
            stampHitAt = now;
            sound(SoundEvents.STONE_PLACE, 0.6f, 0.7f);
            sound(SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 0.8f, 0.5f);
            int cx = ix1 - 6 - stampW / 2;
            int cy = footTop + (iy1 - footTop) / 2;
            click.burst("stamp", cx, cy, stampW, rating.colour(), rating.burstTier(), now);
            if (rating.burstTier() >= 2) {
                sound(SoundEvents.PLAYER_LEVELUP, 1.3f, 0.35f);
            } else {
                sound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8f + 0.1f * rating.rank(), 0.4f);
            }
        }
        float s = motion ? HaulMath.stampScale(ms) : 1f;
        float alpha = HaulMath.stampAlpha(ms);
        if (s <= 0f || alpha <= 0f) {
            return;
        }
        int a = Math.round(255 * alpha);
        int ink = rating.colour();
        int cx = ix1 - 6 - stampW / 2;
        int cy = footTop + (iy1 - footTop) / 2;
        int tw = this.font.width(stamp);
        g.pose().pushPose();
        g.pose().translate(cx, cy, 200);
        g.pose().mulPose(Axis.ZP.rotationDegrees(-9f));
        g.pose().scale(scale * s, scale * s, 1f);
        int bw = tw + 10;
        int bh = 14;
        if (stamped && now - stampHitAt < 500 && modern) {
            Stone.light(g, 0, 0, bw, bh + 6, ink, 0.6f * (1f - (now - stampHitAt) / 500f));
        }
        // A rubber stamp: a double frame in ink, the word in the middle, a little wear in the fill.
        g.fill(-bw / 2, -bh / 2, bw / 2, bh / 2, Stone.argb(Math.round(a * 0.22f), ink));
        frame(g, -bw / 2, -bh / 2, bw, bh, Stone.argb(a, ink));
        frame(g, -bw / 2 + 2, -bh / 2 + 2, bw - 4, bh - 4, Stone.argb(Math.round(a * 0.7f), ink));
        g.drawString(this.font, stamp, -tw / 2, -3, Stone.argb(a, ink), false);
        g.pose().popPose();
    }

    // ---- Helpers --------------------------------------------------------------------------------

    private void draw(GuiGraphics g, String s, int x, int y, int colour) {
        if (modern) {
            Stone.text(g, this.font, s, x, y, colour);
        } else {
            g.drawString(this.font, s, x, y, colour, false);
        }
    }

    private static void frame(GuiGraphics g, int x, int y, int w, int h, int argb) {
        g.fill(x, y, x + w, y + 1, argb);
        g.fill(x, y + h - 1, x + w, y + h, argb);
        g.fill(x, y + 1, x + 1, y + h - 1, argb);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, argb);
    }

    private static void border(GuiGraphics g, int x, int y, int w, int h, int argb) {
        frame(g, x, y, w, h, argb);
    }

    private static String fmt(int n) {
        return String.format(java.util.Locale.ROOT, "%,d", n);
    }

    // ---- Input ----------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        long now = SkillstoneScreen.clock();
        if (button == 0) {
            for (int[] h : hits) {
                if (mouseX < h[2] || mouseX >= h[2] + h[4] || mouseY < h[3] || mouseY >= h[3] + h[5]) {
                    continue;
                }
                if (h[0] == CLOSE_KIND) {
                    ClickFx.plainClick();
                    onClose();
                    return true;
                }
                if (h[0] == ROW_KIND && h[1] < rows.size()) {
                    ClickFx.plainClick();
                    Skill skill = rows.get(h[1]).skillValue();
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(modern ? new SkillstoneScreen(skill) : new TalentTreeScreen(skill, null));
                    }
                    return true;
                }
            }
        }
        if (!done(now)) {
            skipToEnd(now);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        long now = SkillstoneScreen.clock();
        if (!done(now)) {
            skipToEnd(now);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(null);
        }
    }

    /** For the capture rig: the card's length, in ms. */
    public long lengthMs() {
        return timeline == null ? 0 : timeline.end();
    }
}
