package dev.amman.proficiency.client.stone;

import dev.amman.proficiency.client.ClickFx;
import dev.amman.proficiency.client.ClickFxMath;
import dev.amman.proficiency.hearth.HearthPool;
import dev.amman.proficiency.hearth.SiegeRules;
import dev.amman.proficiency.hearth.client.HearthBuildScreen;
import dev.amman.proficiency.hearth.client.HearthGhost;
import dev.amman.proficiency.hearth.client.HearthGuide;
import dev.amman.proficiency.hearth.client.HearthRangeRing;
import dev.amman.proficiency.hearth.client.HearthText;
import dev.amman.proficiency.hearth.client.HearthTrackerModel;
import dev.amman.proficiency.hearth.client.HearthView;
import dev.amman.proficiency.hearth.net.HearthStatePayload;
import dev.amman.proficiency.hearth.net.PoolRow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;

import java.util.List;

/**
 * The Skillstone's Hearth tab (1.31.0, stage 3): the Hearth panel carved into the slab, after
 * {@code A-skillstone/4-hearth.png}. Left: the six stage stones on a molten groove, the fireplace
 * burning as big as the stage, the next stage and its Character groove, the comfort kinds as
 * sockets. Right: the five pools as crucibles of molten charge with the ceiling line, the boon
 * lines, the siege line and the plates (build it for me / the plan, show range, guide, keepers).
 *
 * <p>Reads {@link HearthView#panelState()} every frame, like the classic panel. A stage or a pool
 * tier that rises while it is open flares and lifts the slab. Shared verbatim with Forge 1.20.1.
 */
final class HearthPage {

    private static final ResourceLocation LOGS = ResourceLocation.tryParse("minecraft:textures/block/campfire_log_lit.png");
    private static final ResourceLocation BRICK = Stone.tex("hearth_brick");
    private static final ResourceLocation FIRE = Stone.tex("hearth_fire");
    private static final int FIRE_W = 110;
    private static final int FIRE_H = 74;
    private static final String[] PLATES = {"build", "range", "guide", "keepers"};

    private final SkillstoneScreen s;
    private boolean small;
    private int seenStage = -1;
    private final int[] seenTier = {-1, -1, -1, -1, -1};
    private long stageAt = Long.MIN_VALUE / 2;
    private final long[] tierAt = new long[HearthPool.COUNT];

    HearthPage(SkillstoneScreen s) {
        this.s = s;
        java.util.Arrays.fill(tierAt, Long.MIN_VALUE / 2);
    }

    void layout() {
        small = s.ix1 - s.ix0 < 560 || s.iy1 - s.iy0 < 330;
    }

    private static ResourceLocation icon(String path) {
        return dev.amman.proficiency.hearth.client.TrackerHud.icon(path);
    }

    static String roman(int n) {
        return n <= 0 ? "0" : HearthText.roman(n);
    }

    // ---- Render ---------------------------------------------------------------------------------

    void render(GuiGraphics g, long now, int mx, int my) {
        Font font = s.font();
        HearthStatePayload state = HearthView.panelState();
        if (state == null) {
            Stone.textCentred(g, font, SkillstoneScreen.tr("proficiency.stone.hearth_away"), (s.ix0 + s.ix1) / 2,
                    (s.iy0 + s.iy1) / 2 - 4, Stone.DIM);
            return;
        }
        watch(state, now);
        int iw = s.ix1 - s.ix0;
        int pad = small ? 8 : 16;
        int lx0 = s.ix0 + pad;
        int lx1 = s.ix0 + Math.round(iw * (small ? 0.47f : 0.44f));
        int px0 = lx1 + (small ? 14 : 28);
        int px1 = s.ix1 - (small ? 8 : 14);
        renderLeft(g, state, font, now, mx, my, lx0, lx1);
        renderRight(g, state, font, now, mx, my, px0, px1);
    }

    /** A stage or a pool tier that rose since the last frame: flare it and lift the slab. */
    private void watch(HearthStatePayload state, long now) {
        if (seenStage >= 0 && state.stage() > seenStage && ClickFx.on()) {
            stageAt = now;
            s.riseShake(now);
            SkillstoneScreen.sound(SoundEvents.FIRECHARGE_USE, 0.7f, 0.5f);
        }
        seenStage = state.stage();
        for (PoolRow row : state.pools()) {
            int p = row.pool();
            if (p < 0 || p >= seenTier.length) {
                continue;
            }
            if (seenTier[p] >= 0 && row.tier() > seenTier[p] && ClickFx.on()) {
                tierAt[p] = now;
                s.shake(now, 2f);
                SkillstoneScreen.sound(SoundEvents.FLINTANDSTEEL_USE, 1.1f + 0.1f * row.tier(), 0.5f);
            }
            seenTier[p] = row.tier();
        }
    }

    private void renderLeft(GuiGraphics g, HearthStatePayload state, Font font, long now, int mx, int my, int lx0, int lx1) {
        int top = s.iy0 + (small ? 6 : 12);
        int stage = state.stage();
        Stone.text(g, font, SkillstoneScreen.tr("proficiency.stone.stages"), lx0, top, Stone.HEAD);
        Stone.textRight(g, font, SkillstoneScreen.tr("proficiency.stone.stage_of", roman(stage), roman(6)), lx1, top, Stone.DIM);
        // Six stage stones joined by a groove, molten up to the stage built.
        int sy = top + (small ? 11 : 14);
        int sh = small ? 20 : 26;
        int step = (lx1 - lx0 - 4) / 6;
        int sw = Math.min(small ? 30 : 36, step - (small ? 4 : 9));
        for (int i = 0; i < 6; i++) {
            int x = lx0 + 2 + i * step;
            if (i < 5) {
                int gy = sy + sh / 2 - 1;
                Stone.groove(g, x + sw, x + step, gy, i + 1 < stage ? 1f : 0f, 3, i + 1 < stage);
            }
        }
        for (int i = 0; i < 6; i++) {
            int x = lx0 + 2 + i * step;
            boolean on = i + 1 <= stage;
            boolean hov = mx >= x && mx < x + sw && my >= sy && my < sy + sh;
            long flare = now - stageAt;
            Object key = "stage" + i;
            s.click.begin(g, key, x, sy, sw, sh, now);
            Stone.socket(g, x, sy, sw, sh, on ? 0.8f : hov ? 0.4f : 0f, i + 1 == stage, hov);
            ResourceLocation ic = icon("stage/" + (i + 1));
            int ix = x + (sw - 16) / 2;
            int iy = sy + (sh - 16) / 2;
            if (on) {
                Stone.icon(g, ic, ix, iy, 16, 1f);
            } else {
                Stone.greyIcon(g, ic, ix, iy, 16, 0.5f);
            }
            s.click.end(g, key, x, sy, sw, sh, now);
            if (i + 1 == stage && flare >= 0 && flare < 700) {
                float k = flare / 700f;
                Stone.light(g, x + sw / 2, sy + sh / 2, Math.round(20 + 40 * k), Math.round(20 + 40 * k), 0xC86E1E,
                        1.2f * (1f - k));
            }
            if (hov) {
                s.tooltip.add(HearthText.stageName(i + 1));
                s.tooltip.add(HearthText.stageBuild(i + 1));
            }
        }
        // The fireplace: brick surround, a sunk firebox, the logs and a flame as big as the stage.
        int bottom = s.iy1 - (small ? 26 : 42);
        int fy0 = sy + sh + (small ? 14 : 18);
        int fy1 = Math.min(bottom - (small ? 28 : 40), fy0 + 166);
        int cx = (lx0 + lx1) / 2;
        int fw = Math.min(184, Math.min(lx1 - lx0 - 30, Math.round((fy1 - fy0) * 1.12f)));
        float lit = state.smouldering() ? 0.25f : Math.min(1f, 0.35f + stage / 6f * 0.65f);
        float flame = stage > 0 && !state.smouldering() ? 0.6f + 0.4f * Math.min(6, stage) / 6f : 0f;
        String name = HearthText.stageName(stage).getString();
        String label = stage > 0 ? SkillstoneScreen.tr("proficiency.stone.stage_plate", name, roman(stage)) : name;
        fireplace(g, font, now, cx, fy0, fy1, fw, small, lit, flame, label,
                state.smouldering() ? SkillstoneScreen.tr("proficiency.stone.smouldering") : null);
        // Next stage and the Character groove.
        int ny = fy1 + (small ? 4 : 10);
        String next = stage >= 6 ? SkillstoneScreen.tr("proficiency.stone.stage_last")
                : SkillstoneScreen.tr("proficiency.stone.next_stage", HearthText.stageName(stage + 1).getString());
        String chr = SkillstoneScreen.tr("proficiency.stone.character", state.character(),
                Math.max(state.character(), state.characterNext()));
        int chrW = font.width(chr);
        Stone.text(g, font, Stone.fit(font, next, lx1 - lx0 - chrW - 8), lx0, ny, Stone.INK);
        Stone.textRight(g, font, chr, lx1, ny, Stone.DIM);
        float ch = state.characterNext() <= 0 ? 1f : Math.min(1f, state.character() / (float) state.characterNext());
        Stone.groove(g, lx0, lx1, ny + (small ? 12 : 14), ch, 3, true);
        if (mx >= lx0 && mx < lx1 && my >= ny - 2 && my < ny + 16 && stage < 6) {
            s.tooltip.add(HearthText.stageName(stage + 1));
            s.tooltip.add(HearthText.stageBuild(stage + 1));
        }
        // Comfort kinds.
        List<String> all = HearthView.comfortAll();
        int kinds = Math.max(1, all.size());
        int size = Math.min(21, (lx1 - lx0 + 2) / kinds - 2);
        // Too narrow for sockets (GUI 4 at 720p): bare 8 px icons, lit or grey, still one row.
        boolean bare = size < 14;
        int kstep = bare ? Math.max(9, (lx1 - lx0 + 1) / kinds) : size + 2;
        if (bare) {
            size = 8;
        }
        int cy = s.iy1 - (small ? 6 : 12) - Math.max(size, 12);
        Stone.text(g, font, SkillstoneScreen.tr("proficiency.stone.comfort"), lx0, cy - 12, Stone.HEAD);
        Stone.textRight(g, font, SkillstoneScreen.tr("proficiency.stone.kinds", state.comfortKinds().size(), all.size()), lx1,
                cy - 12, Stone.DIM);
        for (int i = 0; i < all.size(); i++) {
            String kind = all.get(i);
            int x = lx0 + i * kstep;
            if (x + size > lx1 + 2) {
                break;
            }
            boolean has = state.comfortKinds().contains(kind);
            boolean hov = mx >= x && mx < x + size && my >= cy && my < cy + size;
            if (bare) {
                if (has) {
                    Stone.light(g, x + 4, cy + 4, 7, 7, Stone.WARM, 0.6f);
                }
            } else {
                Stone.socket(g, x, cy, size, size, has ? 0.45f : 0f, false, hov);
            }
            int is = size >= 18 ? 16 : 8;
            ResourceLocation ic = icon("comfort/" + kind);
            if (has) {
                Stone.icon(g, ic, x + (size - is) / 2, cy + (size - is) / 2, is, 1f);
            } else {
                Stone.greyIcon(g, ic, x + (size - is) / 2, cy + (size - is) / 2, is, 0.5f);
            }
            if (hov) {
                s.tooltip.add(HearthText.kind(kind));
                s.tooltip.add(HearthText.kindDesc(kind));
                s.tooltip.add(Component.literal(SkillstoneScreen.tr(has ? "proficiency.stone.kind_have" : "proficiency.stone.kind_missing")));
            }
        }
    }

    /**
     * The fireplace (brick surround, sunk firebox, vanilla's lit campfire logs, the fire's glow and a baked flame
     * of {@code flame} times full size, 0 for none) with its name on a plate; shared with the campfire's page.
     * {@code lit} 0..1 is the glow, {@code note} a red line in the firebox or null.
     */
    static void fireplace(GuiGraphics g, Font font, long now, int cx, int fy0, int fy1, int fw, boolean small, float lit,
                          float flame, String label, String note) {
        int fx0 = cx - fw / 2;
        int fx1 = fx0 + fw;
        if (fy1 - fy0 > 40) {
            Stone.block(g, BRICK, 112, 56, fx0, fy0, fw, fy1 - fy0, 6, 3, false);
            int inset = Math.max(8, fw / 8);
            int bx0 = fx0 + inset;
            int bx1 = fx1 - inset;
            int by0 = fy0 + (small ? 16 : 26);
            int by1 = fy1 - 4;
            Stone.light(g, cx, by1 - (by1 - by0) / 3, Math.round(fw * 0.6f), Math.round((by1 - by0) * 0.7f), Stone.WARM,
                    0.75f * lit);
            Stone.well(g, bx0, by0, bx1 - bx0, by1 - by0);
            // The fire's round glow, spilling past the firebox onto the surround (the mockup's second light).
            int glow = Math.round(Math.min(72, (bx1 - bx0) * 0.55f));
            Stone.light(g, cx, by1 - 24, glow, glow, 0xBE5A19, 1.4f * lit);
            Stone.scissor(g, bx0 + 2, by0 + 2, bx1 - 2, by1 - 2);
            int logW = 32;
            int logs = Math.max(1, Math.min(3, (bx1 - bx0 - 8) / logW));
            int lx = cx - logs * logW / 2;
            for (int k = 0; k < logs; k++) {
                g.blit(LOGS, lx + k * logW, by1 - 18, 32, 16, 0f, 0f, 16, 8, 16, 64);
            }
            if (flame > 0) {
                float size = flame;
                int w = Math.min(bx1 - bx0 - 4, Math.round(FIRE_W * size * Math.min(1f, (bx1 - bx0) / 120f)));
                int h = Math.min(by1 - by0 - 10, Math.round(FIRE_H * size * Math.min(1f, (bx1 - bx0) / 120f)));
                int frame = (int) ((now / 100L) % 8);
                g.blit(FIRE, cx - w / 2, by1 - 14 - h, w, h, 0f, (float) (frame * FIRE_H), FIRE_W, FIRE_H, FIRE_W, FIRE_H * 8);
            }
            g.disableScissor();
            int pw = Math.min(fw + 10, font.width(label) + 24);
            Stone.plate(g, font, cx - pw / 2, fy0 - 2, pw, small ? 14 : 18, Stone.fit(font, label, pw - 8), Stone.GOLD, false);
            if (note != null) {
                Stone.textCentred(g, font, note, cx, by0 + 6, 0xFF8C78);
            }
        }
    }

    private static PoolRow rowOf(HearthStatePayload state, int pool) {
        for (PoolRow row : state.pools()) {
            if (row.pool() == pool) {
                return row;
            }
        }
        return null;
    }

    private void renderRight(GuiGraphics g, HearthStatePayload state, Font font, long now, int mx, int my, int px0, int px1) {
        int top = s.iy0 + (small ? 6 : 12);
        Stone.text(g, font, SkillstoneScreen.tr("proficiency.stone.pools"), px0, top, Stone.HEAD);
        Stone.textRight(g, font, SkillstoneScreen.tr("proficiency.stone.ceiling", roman(state.ceiling())), px1, top, Stone.DIM);
        double[] thresholds = HearthView.thresholds();
        int maxTier = Math.max(1, thresholds.length);
        int n = HearthPool.COUNT;
        int gap = small ? 6 : 11;
        int cw = Math.min(46, (px1 - px0 - (n - 1) * gap) / n);
        gap = n > 1 ? (px1 - px0 - n * cw) / (n - 1) : 0;
        gap = Math.min(gap, small ? 10 : 14);
        int cy0 = top + (small ? 22 : 28);
        int ih = s.iy1 - s.iy0;
        int cy1 = cy0 + (small ? Math.max(40, ih / 4) : Math.min(110, ih * 3 / 10));
        for (int i = 0; i < n; i++) {
            PoolRow row = rowOf(state, i);
            int x = px0 + i * (cw + gap);
            Stone.well(g, x, cy0, cw, cy1 - cy0);
            int inner = cy1 - cy0 - 8;
            double value = row == null ? 0 : HearthTrackerModel.value(row.charge(), thresholds);
            float lvl = (float) Math.max(0, Math.min(1, value / maxTier));
            int fh = Math.round(inner * lvl);
            boolean locked = row != null && row.lock() != 0;
            if (fh > 0) {
                Stone.light(g, x + cw / 2, cy1 - 4 - fh / 2, cw, fh / 2 + 12, Stone.WARM, locked ? 0.25f : 0.45f);
                int mol = locked ? Stone.mix(Stone.ember(0.5f), 0x707070, 0.45f) : Stone.ember(0.5f);
                g.fill(x + 4, cy1 - 4 - fh, x + cw - 4, cy1 - 4, Stone.argb(255, mol));
                g.fill(x + 4, cy1 - 4 - fh, x + 8, cy1 - 4, Stone.argb(255, Stone.shade(mol, 0.72f)));
                g.fill(x + cw - 12, cy1 - 4 - fh, x + cw - 8, cy1 - 4, Stone.argb(255, Stone.shade(mol, 1.2f)));
                g.fill(x + 4, cy1 - 4 - fh, x + cw - 4, cy1 - 3 - fh, Stone.argb(255, Stone.shade(mol, 1.45f)));
                for (int yy = cy1 - 4 - fh + 3; yy < cy1 - 5; yy += 5) {
                    int bx = x + 8 + Math.floorMod(yy * 7 + (int) (now / 160L), Math.max(1, cw - 16));
                    g.fill(bx, yy, bx + 1, yy + 1, Stone.argb(255, Stone.shade(mol, 1.25f)));
                }
            }
            for (int t = 1; t < maxTier; t++) {
                int ty = cy1 - 4 - Math.round(inner * t / (float) maxTier);
                g.fill(x + 2, ty, x + 6, ty + 1, Stone.argb(255, 0xA08C82));
            }
            int cap = cy1 - 4 - Math.round(inner * Math.min(maxTier, state.ceiling()) / (float) maxTier);
            g.fill(x + 2, cap, x + cw - 2, cap + 1, Stone.argb(220, 0xFF8C5A));
            int tier = row == null ? 0 : row.tier();
            long flare = now - tierAt[i];
            Object key = "pool" + i;
            s.click.begin(g, key, x + cw / 2 - 13, cy0 - 9, 26, 16, now);
            Stone.plate(g, font, x + cw / 2 - 13, cy0 - 9, 26, 16, roman(tier), tier > 0 ? Stone.GOLD : Stone.DIM, false);
            s.click.end(g, key, x + cw / 2 - 13, cy0 - 9, 26, 16, now);
            if (flare >= 0 && flare < 700) {
                float k = flare / 700f;
                Stone.light(g, x + cw / 2, cy1 - 4 - fh, Math.round(cw * (0.6f + k)), Math.round(cw * (0.6f + k)), 0xC86E1E,
                        1.2f * (1f - k));
            }
            ResourceLocation ic = icon("pool/" + HearthPool.VALUES[i].id());
            if (tier > 0) {
                Stone.icon(g, ic, x + cw / 2 - 8, cy1 + 5, 16, 1f);
            } else {
                Stone.greyIcon(g, ic, x + cw / 2 - 8, cy1 + 5, 16, 0.6f);
            }
            if (!HearthView.previewing()) {
                s.hits.add(new int[] {8, 10 + i, x, cy0 - 9, cw, cy1 - cy0 + 31});
            }
            if (mx >= x && mx < x + cw && my >= cy0 - 9 && my < cy1 + 22) {
                s.tooltip.add(HearthText.pool(i));
                Component boon = HearthText.boonLine(i, tier, false);
                if (boon != null) {
                    s.tooltip.add(boon);
                }
                if (row != null && row.lock() != 0) {
                    s.tooltip.add(HearthText.needFull(row.lock(), row.need(), state.comfort(), state.character()));
                }
                if (!HearthView.previewing()) {
                    s.tooltip.add(Component.literal(SkillstoneScreen.tr("proficiency.stone.pool_keepers"))
                            .withStyle(net.minecraft.ChatFormatting.GRAY));
                }
            }
        }
        // Boon lines: what each pool gives now (dim: what its first tier would give).
        int line = small ? 10 : 12;
        int by = cy1 + (small ? 26 : 32);
        int nameW = 0;
        for (int i = 0; i < n; i++) {
            nameW = Math.max(nameW, font.width(HearthText.pool(i).getString()));
        }
        nameW = Math.min(nameW + 8, (px1 - px0) / 3);
        for (int i = 0; i < n; i++) {
            PoolRow row = rowOf(state, i);
            int tier = row == null ? 0 : row.tier();
            Component now0 = state.smouldering() ? null : HearthText.boonLine(i, tier, false);
            boolean on = now0 != null;
            Component shown = on ? now0 : HearthText.boonLine(i, 0, true);
            Stone.text(g, font, Stone.fit(font, HearthText.pool(i).getString(), nameW - 4), px0, by + i * line,
                    on ? Stone.INK : Stone.LOCK);
            if (shown != null) {
                Stone.text(g, font, Stone.fit(font, shown.getString(), px1 - px0 - nameW), px0 + nameW, by + i * line,
                        on ? 0xCDC2B6 : Stone.LOCK);
            }
        }
        // The siege line and the self-build's status.
        int plateH = small ? 16 : 20;
        int plateY = s.iy1 - (small ? 6 : 12) - plateH;
        int y = by + n * line + (small ? 4 : 8);
        if (y + 16 <= plateY - 2) {
            Stone.icon(g, icon("siege"), px0, y, 16, 1f);
            String siege = SkillstoneScreen.tr("proficiency.stone.siege_line", state.defenceStreak(), siegesLine(state).getString());
            Stone.text(g, font, Stone.fit(font, siege, px1 - px0 - 20), px0 + 20, y + 4, Stone.DIM);
            y += 18;
        }
        if (y + 16 <= plateY - 2) {
            Stone.icon(g, icon("character"), px0, y, 16, 1f);
            String design;
            if (state.build().on()) {
                design = HearthText.buildStatus(state.build(), state.build().seconds()).getString();
            } else {
                var lanes = HearthGuide.lanes();
                design = lanes.isEmpty() ? "" : SkillstoneScreen.tr("proficiency.stone.design",
                        HearthGuide.laneName(lanes.get(Math.floorMod(HearthGhost.lane(), lanes.size())).id()).getString(),
                        lanes.size());
            }
            Stone.text(g, font, Stone.fit(font, design, px1 - px0 - 20), px0 + 20, y + 4, Stone.DIM);
        }
        // The plates.
        int x = px0;
        int gapP = small ? 4 : 6;
        int total = 0;
        String[] labels = new String[PLATES.length];
        for (int i = 0; i < PLATES.length; i++) {
            labels[i] = plateLabel(i, state);
            total += font.width(labels[i]) + (small ? 10 : 16) + (i > 0 ? gapP : 0);
        }
        // Too wide for the column: four even plates, the labels cut, the full label on hover.
        boolean even = total > px1 - px0;
        int evenW = (px1 - px0 - 3 * gapP) / 4;
        for (int i = 0; i < PLATES.length; i++) {
            String full = labels[i];
            int w = even ? evenW : font.width(full) + (small ? 10 : 16);
            String label = Stone.fit(font, full, w - 8);
            boolean hov = mx >= x && mx < x + w && my >= plateY && my < plateY + plateH;
            boolean enabled = i != 0 || canBuild(state);
            Object key = "hplate" + i;
            s.click.begin(g, key, x, plateY, w, plateH, now);
            Stone.plate(g, font, x, plateY, w, plateH, label, !enabled ? Stone.LOCK : hov ? Stone.GOLD : Stone.INK, hov && enabled);
            s.click.end(g, key, x, plateY, w, plateH, now);
            s.hits.add(new int[] {8, i, x, plateY, w, plateH});
            if (hov && !label.equals(full)) {
                s.tooltip.add(Component.literal(full));
            }
            x += w + gapP;
        }
    }

    private static String plateLabel(int i, HearthStatePayload state) {
        return switch (i) {
            case 0 -> SkillstoneScreen.tr(state.build().on() ? "proficiency.hearth.client.panel.build.plan"
                    : "proficiency.hearth.client.panel.build.start");
            case 1 -> SkillstoneScreen.tr("proficiency.stone.show_range");
            case 2 -> SkillstoneScreen.tr("proficiency.stone.guide");
            default -> SkillstoneScreen.tr("proficiency.stone.keepers");
        };
    }

    private static boolean canBuild(HearthStatePayload state) {
        return !HearthView.previewing() && state.reason() != HearthStatePayload.CAMPFIRE && state.stage() < 6;
    }

    private static Component siegesLine(HearthStatePayload state) {
        SiegeRules.Phase phase = SiegeRules.Phase.byOrdinal(state.siegePhase());
        if (phase == SiegeRules.Phase.ACTIVE) {
            return Component.translatable("proficiency.hearth.client.panel.siege_active");
        }
        if (phase == SiegeRules.Phase.COUNTDOWN) {
            return Component.translatable("proficiency.hearth.client.panel.siege_due");
        }
        if (!state.siegesOn()) {
            return Component.translatable("proficiency.hearth.client.panel.sieges_off");
        }
        if (state.playHoursSinceSiege() < 0) {
            return Component.translatable("proficiency.hearth.client.panel.sieges_on_never");
        }
        if (state.playHoursSinceSiege() < 1f) {
            return Component.translatable("proficiency.hearth.client.panel.sieges_on_recent");
        }
        return Component.translatable("proficiency.hearth.client.panel.sieges_on", Math.round(state.playHoursSinceSiege()));
    }

    // ---- Input ----------------------------------------------------------------------------------

    void click(int index, int[] h, long now) {
        HearthStatePayload state = HearthView.panelState();
        Minecraft mc = Minecraft.getInstance();
        if (state == null) {
            return;
        }
        if (index >= 10) {
            // A crucible: the keepers ranked by that pool.
            s.click.press("pool" + (index - 10), h[2] + h[4] / 2 - 13, h[3], 26, 16, Stone.ember(0.75f), now);
            SkillstoneScreen.sound(SoundEvents.STONE_HIT, 1.1f, 0.5f);
            mc.setScreen(new StoneKeepersScreen(s, index - 10, false));
            return;
        }
        Object key = "hplate" + index;
        switch (index) {
            case 0 -> {
                if (!canBuild(state)) {
                    s.refuse(key, h, null, now);
                    return;
                }
                s.click.success(key, h[2], h[3], h[4], h[5], Stone.ember(0.75f), 1, 1f, now);
                HearthBuildScreen.request(state.pos(), HearthGhost.showing(state.pos()) ? HearthGhost.lane() : -1);
            }
            case 1 -> {
                ClickFx.plainClick();
                HearthRangeRing.showFor(state.pos(), state.dimension(), net.minecraft.Util.getMillis());
                mc.setScreen(null);
                mc.gui.setOverlayMessage(Component.translatable("proficiency.hearth.client.panel.range_shown")
                        .withStyle(style -> style.withColor(0xE68232)), false);
            }
            case 2 -> {
                ClickFx.plainClick();
                mc.setScreen(new StoneGuideScreen(s));
            }
            default -> {
                ClickFx.plainClick();
                mc.setScreen(new StoneKeepersScreen(s, -1, false));
            }
        }
    }

    /** For the frame sheets: play a stage rise and a pool tier rise. */
    void demoRise(long now) {
        stageAt = now + ClickFxMath.MIN_CHARGE_MS;
        tierAt[0] = now + ClickFxMath.MIN_CHARGE_MS;
        s.riseShake(now + ClickFxMath.MIN_CHARGE_MS);
    }
}
