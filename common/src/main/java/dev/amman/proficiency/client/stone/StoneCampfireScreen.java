package dev.amman.proficiency.client.stone;

import dev.amman.proficiency.client.ClickFx;
import dev.amman.proficiency.client.SkillstoneMath;
import dev.amman.proficiency.hearth.HearthPool;
import dev.amman.proficiency.hearth.SiegeRules;
import dev.amman.proficiency.hearth.client.HearthGhost;
import dev.amman.proficiency.hearth.client.HearthGuide;
import dev.amman.proficiency.hearth.client.HearthRangeRing;
import dev.amman.proficiency.hearth.client.HearthText;
import dev.amman.proficiency.hearth.client.HearthTrackerModel;
import dev.amman.proficiency.hearth.client.HearthView;
import dev.amman.proficiency.hearth.net.HearthStatePayload;
import dev.amman.proficiency.hearth.net.PoolRow;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A plain campfire's preview as a slab page (1.34.0): what the classic panel shows for an empty-hand click on a
 * campfire that is not a Hearth yet ({@link HearthStatePayload#CAMPFIRE}, stage 0), carved like the Hearth face.
 * Left: the six stage stones (stage I ringed as the next), the Hearth face's fireplace with the campfire's own
 * logs and a small flame while it burns, Character with the next stage's mark, the twelve comfort kinds.
 * Right: the next stage, its build and its offering in a sunk slate; the five pools as empty crucibles with the
 * ceiling, what each gives at its first tier, the defence line; plates for the build guide (the ghost blocks of
 * stage I around the campfire), its design, Show range and Build it for me (refused: there is no Hearth to build
 * yet). Reads {@link HearthView#panelState()} every frame, like the classic panel; closing it drops the preview.
 * Shared verbatim with Forge 1.20.1.
 */
public final class StoneCampfireScreen extends StoneSheet {

    static final int PLATE = 1;
    private static final int P_GUIDE = 0;
    private static final int P_LANE = 1;
    private static final int P_RANGE = 2;
    private static final int P_BUILD = 3;
    /** Where the last frame drew stage I's stone and the first crucible (the layout demo hovers them). */
    private int[] stageRect = new int[4];
    private int[] poolRect = new int[4];
    /** When Build it for me was last refused: its plate says why for {@link #REFUSED_MS}. */
    private long buildRefusedAt = Long.MIN_VALUE / 2;
    private static final long REFUSED_MS = 2000;

    public StoneCampfireScreen() {
        super(Component.translatable("proficiency.hearth.client.panel.campfire"), null);
    }

    /** Opens the page for a campfire's preview, or keeps the open one (it reads the newest state every frame). */
    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof StoneCampfireScreen) {
            return;
        }
        if (mc.screen == null) {
            mc.setScreen(new StoneCampfireScreen());
        } else {
            // Another screen is open: the page is not shown, so the preview must not linger in HearthView (the
            // Hearth tab would read the campfire and refuse Build it for me).
            HearthView.clearPreview();
        }
    }

    @Override
    String crumb() {
        return SkillstoneScreen.tr("proficiency.stone.close");
    }

    @Override
    int maxWidth() {
        return SkillstoneMath.TREE_W;
    }

    @Override
    int maxHeight() {
        return SkillstoneMath.HEARTH_H;
    }

    @Override
    public void removed() {
        // As the classic panel: a campfire's stage-0 state lives only while its page is open (the guide stays on).
        HearthView.clearPreview();
        super.removed();
    }

    private static ResourceLocation icon(String path) {
        return dev.amman.proficiency.hearth.client.TrackerHud.icon(path);
    }

    private static String roman(int n) {
        return HearthPage.roman(n);
    }

    private static Component dim(String key, Object... args) {
        return Component.translatable("proficiency.hearth.client." + key, args).withStyle(ChatFormatting.GRAY);
    }

    // ---- Render ---------------------------------------------------------------------------------

    @Override
    void body(GuiGraphics g, long now, int mx, int my) {
        HearthStatePayload state = HearthView.panelState();
        if (state == null) {
            Stone.textCentred(g, this.font, SkillstoneScreen.tr("proficiency.hearth.client.panel.none"), (ix0 + ix1) / 2,
                    (iy0 + iy1) / 2 - 4, Stone.DIM);
            return;
        }
        int iw = ix1 - ix0;
        int pad = small ? 8 : 16;
        int lx0 = ix0 + pad;
        int lx1 = ix0 + Math.round(iw * (small ? 0.46f : 0.44f));
        int px0 = lx1 + (small ? 12 : 28);
        int px1 = ix1 - (small ? 8 : 14);
        renderLeft(g, state, this.font, now, mx, my, lx0, lx1);
        renderRight(g, state, this.font, now, mx, my, px0, px1);
    }

    private static boolean hov(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** True while the campfire in the world burns (the payload does not say; the client's world does). */
    private static boolean burning(HearthStatePayload state) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return true;
        }
        BlockState block = mc.level.getBlockState(BlockPos.of(state.pos()));
        return block.getOptionalValue(CampfireBlock.LIT).orElse(true);
    }

    private void renderLeft(GuiGraphics g, HearthStatePayload state, Font font, long now, int mx, int my, int lx0, int lx1) {
        int top = iy0 + (small ? 6 : 12);
        int stage = state.stage();
        Stone.text(g, font, SkillstoneScreen.tr("proficiency.stone.stages"), lx0, top, Stone.HEAD);
        Stone.textRight(g, font, SkillstoneScreen.tr("proficiency.stone.stage_of", roman(stage), roman(6)), lx1, top, Stone.DIM);
        // Six dark stage stones on a cold groove; the next one (stage I) ringed in ember.
        int sy = top + (small ? 11 : 14);
        int sh = small ? 20 : 26;
        int step = (lx1 - lx0 - 4) / 6;
        int sw = Math.min(small ? 30 : 36, step - (small ? 4 : 9));
        for (int i = 0; i < 5; i++) {
            int x = lx0 + 2 + i * step;
            Stone.groove(g, x + sw, x + step, sy + sh / 2 - 1, i + 1 < stage ? 1f : 0f, 3, false);
        }
        for (int i = 0; i < 6; i++) {
            int x = lx0 + 2 + i * step;
            boolean on = i + 1 <= stage;
            boolean next = i == stage;
            boolean hv = hov(mx, my, x, sy, sw, sh);
            if (i == 0) {
                stageRect = new int[] {x, sy, sw, sh};
            }
            Object key = "stage" + i;
            click.begin(g, key, x, sy, sw, sh, now);
            Stone.socket(g, x, sy, sw, sh, on ? 0.8f : next ? 0.3f : hv ? 0.4f : 0f, next, hv);
            ResourceLocation ic = icon("stage/" + (i + 1));
            int ix = x + (sw - 16) / 2;
            int iy = sy + (sh - 16) / 2;
            if (on) {
                Stone.icon(g, ic, ix, iy, 16, 1f);
            } else {
                Stone.greyIcon(g, ic, ix, iy, 16, next ? 0.85f : 0.5f);
            }
            click.end(g, key, x, sy, sw, sh, now);
            if (hv) {
                tooltip.add(Component.translatable("proficiency.hearth.client.panel.heading", HearthText.roman(i + 1),
                        HearthText.stageName(i + 1)).withStyle(style -> style.withColor(0xE68232)));
                tooltip.add(HearthText.stageBuild(i + 1).copy().withStyle(ChatFormatting.GRAY));
            }
        }
        // Bottom up: the comfort sockets, their missing line and head; then Character; the fireplace takes the rest.
        List<String> all = HearthView.comfortAll();
        int kinds = Math.max(1, all.size());
        int size = Math.min(21, (lx1 - lx0 + 2) / kinds - 2);
        boolean bare = size < 14;
        int kstep = bare ? Math.max(9, (lx1 - lx0 + 1) / kinds) : size + 2;
        if (bare) {
            size = 8;
        }
        int cy = iy1 - (small ? 6 : 12) - Math.max(size, 12);
        boolean missingLine = !small;
        int comfortHead = cy - 12 - (missingLine ? 11 : 0);
        int chrNote = small ? 0 : 11;
        int chrY = comfortHead - (small ? 22 : 30) - chrNote;
        // The fireplace sits in the middle of the room between the stones and Character (at most 166 tall).
        int room0 = sy + sh + (small ? 14 : 18);
        int room1 = chrY - (small ? 4 : 10);
        int fy0 = room0 + Math.max(0, (room1 - room0 - 166) / 2);
        int fy1 = Math.min(room1, fy0 + 166);
        int cx = (lx0 + lx1) / 2;
        int fw = Math.min(184, Math.min(lx1 - lx0 - 30, Math.round((fy1 - fy0) * 1.12f)));
        boolean burns = burning(state);
        HearthPage.fireplace(g, font, now, cx, fy0, fy1, fw, small, burns ? 0.42f : 0.12f, burns ? 0.42f : 0f,
                SkillstoneScreen.tr("proficiency.stone.campfire_plate"), null);
        // Character: the bar to its most, the next stage's mark, the note under it.
        int max = Math.max(1, HearthView.characterMax());
        int character = state.character();
        int need = state.characterNext();
        String chr = HearthText.character(character, max).getString();
        String chrHead = SkillstoneScreen.tr("proficiency.stone.character_head");
        Stone.text(g, font, chrHead, lx0, chrY, Stone.HEAD);
        // At a narrow page "Character 0/50" runs into the CHARACTER head: the head already names it, so show the
        // numbers alone there (the hover still gives the full line).
        int chrRoom = lx1 - lx0 - font.width(chrHead) - 6;
        String chrShown = font.width(chr) <= chrRoom ? chr : Stone.fit(font, character + "/" + max, chrRoom);
        Stone.textRight(g, font, chrShown, lx1, chrY, character >= max ? Stone.GOLD : Stone.DIM);
        int gy = chrY + (small ? 13 : 15);
        Stone.groove(g, lx0, lx1, gy, Math.min(1f, character / (float) max), 3, true);
        if (need > 0 && need <= max) {
            int nx = lx0 + Math.round((lx1 - lx0) * need / (float) max);
            g.fill(Math.min(lx1 - 1, nx), gy - 4, Math.min(lx1, nx + 1), gy + 4,
                    Stone.argb(255, character >= need ? 0x8FD46A : Stone.RED));
        }
        String note = need > 0
                ? SkillstoneScreen.tr("proficiency.hearth.client.panel.character_next", HearthText.roman(Math.min(6, state.ceiling() + 1)), need)
                : SkillstoneScreen.tr("proficiency.hearth.client.panel.character_done");
        String noteShown = Stone.fit(font, note, lx1 - lx0);
        if (!small) {
            Stone.text(g, font, noteShown, lx0, gy + 6, Stone.DIM);
        }
        if (hov(mx, my, lx0, chrY - 2, lx1 - lx0, gy + 6 + chrNote - chrY + 2)) {
            tooltip.add(Component.literal(chr).withStyle(style -> style.withColor(0xDEA06E)));
            if (small || !noteShown.equals(note)) {
                tooltip.add(Component.literal(note).withStyle(ChatFormatting.GRAY));
            }
            tooltip.add(dim("panel.character.tip"));
            tooltip.add(dim("panel.character.gates"));
        }
        // Comfort: the head, the missing kinds, the sockets.
        List<String> missing = new ArrayList<>();
        for (String kind : all) {
            if (!state.comfortKinds().contains(kind)) {
                missing.add(HearthText.kind(kind).getString());
            }
        }
        String missingText = missing.isEmpty() ? SkillstoneScreen.tr("proficiency.hearth.client.panel.comfort_all")
                : SkillstoneScreen.tr("proficiency.hearth.client.panel.missing", String.join(", ", missing));
        Stone.text(g, font, SkillstoneScreen.tr("proficiency.stone.comfort"), lx0, comfortHead, Stone.HEAD);
        Stone.textRight(g, font, SkillstoneScreen.tr("proficiency.stone.kinds", state.comfortKinds().size(), all.size()), lx1,
                comfortHead, Stone.DIM);
        String missingShown = Stone.fit(font, missingText, lx1 - lx0);
        if (missingLine) {
            Stone.text(g, font, missingShown, lx0, comfortHead + 12, Stone.LOCK);
        }
        if (hov(mx, my, lx0, comfortHead - 2, lx1 - lx0, cy - comfortHead)) {
            tooltip.add(HearthText.comfort(state.comfort(), all.size()).copy().withStyle(style -> style.withColor(0xDEA06E)));
            tooltip.add(Component.literal(missingText).withStyle(ChatFormatting.GRAY));
            tooltip.add(dim("panel.comfort.tip"));
            tooltip.add(dim("panel.comfort.gates"));
        }
        for (int i = 0; i < all.size(); i++) {
            String kind = all.get(i);
            int x = lx0 + i * kstep;
            if (x + size > lx1 + 2) {
                break;
            }
            boolean has = state.comfortKinds().contains(kind);
            boolean hv = hov(mx, my, x, cy, size, size);
            if (bare) {
                if (has) {
                    Stone.light(g, x + 4, cy + 4, 7, 7, Stone.WARM, 0.6f);
                }
            } else {
                Stone.socket(g, x, cy, size, size, has ? 0.45f : 0f, false, hv);
            }
            int is = size >= 18 ? 16 : 8;
            ResourceLocation ic = icon("comfort/" + kind);
            if (has) {
                Stone.icon(g, ic, x + (size - is) / 2, cy + (size - is) / 2, is, 1f);
            } else {
                Stone.greyIcon(g, ic, x + (size - is) / 2, cy + (size - is) / 2, is, 0.5f);
            }
            if (hv) {
                tooltip.add(HearthText.kind(kind));
                Component desc = HearthText.kindDesc(kind);
                if (desc != null) {
                    tooltip.add(desc.copy().withStyle(ChatFormatting.GRAY));
                }
                tooltip.add(Component.literal(SkillstoneScreen.tr(has ? "proficiency.stone.kind_have"
                        : "proficiency.stone.kind_missing")).withStyle(has ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
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

    private static ItemStack offering() {
        String id = HearthView.nextOffering();
        ResourceLocation key = id == null || id.isEmpty() ? null : ResourceLocation.tryParse(id);
        if (key == null) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.get(key);
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    /** What a pool gives at its tier, or (dim) what its first tier would give, as the classic panel says it. */
    private static String boonText(HearthStatePayload state, int pool, int tier) {
        Component now = state.smouldering() ? null : HearthText.boonLine(pool, tier, false);
        if (now != null) {
            return now.getString();
        }
        Component first = HearthText.boonLine(pool, 0, true);
        return first == null ? SkillstoneScreen.tr("proficiency.hearth.client.boon.none")
                : SkillstoneScreen.tr("proficiency.hearth.client.panel.at_first", first.getString());
    }

    private void renderRight(GuiGraphics g, HearthStatePayload state, Font font, long now, int mx, int my, int px0, int px1) {
        int top = iy0 + (small ? 6 : 12);
        int stage = state.stage();
        int pw = px1 - px0;
        // The next stage: its name as the head, the Character it wants on the right, then its build and offering.
        String head = stage >= 6 ? SkillstoneScreen.tr("proficiency.hearth.client.panel.complete")
                : SkillstoneScreen.tr("proficiency.hearth.client.panel.next", HearthText.roman(stage + 1),
                        HearthText.stageName(stage + 1).getString());
        int need = state.characterNext();
        String needText = need > 0 ? HearthText.character(state.character(), need).getString() : "";
        int needW = needText.isEmpty() ? 0 : font.width(needText) + 8;
        Stone.text(g, font, Stone.fit(font, head.toUpperCase(Locale.ROOT), pw - needW), px0, top, Stone.HEAD);
        if (!needText.isEmpty()) {
            Stone.textRight(g, font, needText, px1, top, state.character() >= need ? 0x8FD46A : 0xFF7864);
        }
        int line = small ? 10 : 11;
        List<net.minecraft.util.FormattedCharSequence> build = font.split(HearthText.stageBuild(stage + 1), pw - 16);
        int buildLines = Math.min(small ? 1 : 3, build.size());
        int slotS = small ? 20 : 24;
        int wy0 = top + (small ? 11 : 14);
        int wy1 = wy0 + 8 + buildLines * line + 4 + slotS + 6;
        Stone.well(g, px0, wy0, pw, wy1 - wy0);
        int ty = wy0 + 6;
        for (int i = 0; i < buildLines; i++) {
            g.drawString(font, build.get(i), px0 + 8, ty, Stone.argb(255, 0xCDC2B6), true);
            ty += line;
        }
        if (build.size() > buildLines && hov(mx, my, px0, wy0, pw, ty - wy0)) {
            tooltip.add(HearthText.stageBuild(stage + 1).copy().withStyle(ChatFormatting.GRAY));
        }
        ItemStack offering = offering();
        int oy = ty + 3;
        Stone.socket(g, px0 + 6, oy, slotS, slotS, offering.isEmpty() ? 0f : 0.35f, false, false);
        if (!offering.isEmpty()) {
            g.renderItem(offering, px0 + 6 + (slotS - 16) / 2, oy + (slotS - 16) / 2);
            String off = SkillstoneScreen.tr("proficiency.hearth.client.panel.offering", offering.getHoverName().getString());
            Stone.text(g, font, Stone.fit(font, off, pw - slotS - 22), px0 + slotS + 14, oy + (slotS - 8) / 2, Stone.INK);
            if (hov(mx, my, px0 + 6, oy, pw - 12, slotS)) {
                tooltip.add(Component.literal(off));
            }
        }
        // Bottom up: two rows of plates, the defence line, the boon lines; the crucibles take what is left.
        int plateH = small ? 16 : 20;
        int gapP = small ? 4 : 6;
        int plateY1 = iy1 - (small ? 6 : 12) - plateH;
        int plateY0 = plateY1 - plateH - gapP;
        int defY = plateY0 - (small ? 16 : 22);
        int n = HearthPool.COUNT;
        int bline = small ? 10 : 12;
        int poolsHead = wy1 + (small ? 6 : 10);
        int cy0 = poolsHead + (small ? 21 : 26);
        int iconS = small ? 8 : 16;
        // The crucibles want at least this much; the boon lines go first when they do not fit.
        int minCru = small ? 26 : 40;
        int boonH = n * bline + (small ? 3 : 6);
        int under = iconS + (small ? 6 : 10);
        boolean boons = defY - boonH - under - cy0 >= minCru;
        int cy1 = Math.min(cy0 + (boons ? (small ? 60 : 96) : 110), defY - (boons ? boonH : 0) - under);
        Stone.text(g, font, SkillstoneScreen.tr("proficiency.stone.pools"), px0, poolsHead, Stone.HEAD);
        String ceiling = SkillstoneScreen.tr("proficiency.stone.ceiling", roman(state.ceiling()));
        Stone.textRight(g, font, ceiling, px1, poolsHead, Stone.DIM);
        int cw0 = font.width(ceiling);
        if (hov(mx, my, px1 - cw0, poolsHead - 2, cw0, 11)) {
            tooltip.add(Component.translatable("proficiency.hearth.client.panel.ceiling", HearthText.roman(state.ceiling()))
                    .withStyle(style -> style.withColor(0xE68232)));
            tooltip.add(dim("panel.ceiling.tip"));
        }
        double[] thresholds = HearthView.thresholds();
        int maxTier = Math.max(1, thresholds.length);
        int gap = small ? 6 : 11;
        int cw = Math.min(46, (pw - (n - 1) * gap) / n);
        gap = n > 1 ? Math.min(small ? 10 : 18, (pw - n * cw) / (n - 1)) : 0;
        int rowW = n * cw + (n - 1) * gap;
        int cx0 = px0 + (pw - rowW) / 2;
        if (cy1 - cy0 >= 16) {
            for (int i = 0; i < n; i++) {
                PoolRow row = rowOf(state, i);
                int x = cx0 + i * (cw + gap);
                Stone.well(g, x, cy0, cw, cy1 - cy0);
                if (i == 0) {
                    poolRect = new int[] {x, cy0, cw, cy1 - cy0};
                }
                int inner = cy1 - cy0 - 8;
                double value = row == null ? 0 : HearthTrackerModel.value(row.charge(), thresholds);
                int fh = Math.round(inner * (float) Math.max(0, Math.min(1, value / maxTier)));
                if (fh > 0) {
                    g.fill(x + 4, cy1 - 4 - fh, x + cw - 4, cy1 - 4, Stone.argb(255, Stone.ember(0.5f)));
                }
                for (int t = 1; t < maxTier; t++) {
                    int tky = cy1 - 4 - Math.round(inner * t / (float) maxTier);
                    g.fill(x + 2, tky, x + 6, tky + 1, Stone.argb(255, 0xA08C82));
                }
                // The ceiling: at 0 it sits on the crucible's floor, so nothing can charge past it yet.
                int cap = cy1 - 4 - Math.round(inner * Math.min(maxTier, state.ceiling()) / (float) maxTier);
                g.fill(x + 2, cap, x + cw - 2, cap + 1, Stone.argb(220, 0xFF8C5A));
                int tier = row == null ? 0 : row.tier();
                Stone.plate(g, font, x + cw / 2 - 13, cy0 - (small ? 8 : 9), 26, small ? 14 : 16, roman(tier),
                        tier > 0 ? Stone.GOLD : Stone.DIM, false);
                ResourceLocation ic = icon("pool/" + HearthPool.VALUES[i].id());
                int iy = cy1 + (small ? 3 : 5);
                if (tier > 0) {
                    Stone.icon(g, ic, x + cw / 2 - iconS / 2, iy, iconS, 1f);
                } else {
                    Stone.greyIcon(g, ic, x + cw / 2 - iconS / 2, iy, iconS, 0.6f);
                }
                if (hov(mx, my, x, cy0 - 9, cw, cy1 - cy0 + 9 + under)) {
                    poolTip(state, i, row, thresholds);
                }
            }
        }
        if (boons) {
            int by = cy1 + under + (small ? 2 : 4);
            int nameW = 0;
            for (int i = 0; i < n; i++) {
                nameW = Math.max(nameW, font.width(HearthText.pool(i).getString()));
            }
            nameW = Math.min(nameW + 8, pw / 3);
            for (int i = 0; i < n; i++) {
                PoolRow row = rowOf(state, i);
                int tier = row == null ? 0 : row.tier();
                boolean on = !state.smouldering() && HearthText.boonLine(i, tier, false) != null;
                String text = boonText(state, i, tier);
                String shown = Stone.fit(font, text, pw - nameW);
                Stone.text(g, font, Stone.fit(font, HearthText.pool(i).getString(), nameW - 4), px0, by + i * bline,
                        on ? Stone.INK : Stone.LOCK);
                Stone.text(g, font, shown, px0 + nameW, by + i * bline, on ? 0xCDC2B6 : Stone.LOCK);
                if (hov(mx, my, px0, by + i * bline - 1, pw, bline) && !shown.equals(text)) {
                    tooltip.add(Component.literal(text));
                }
            }
        }
        // Defence: the streak and whether sieges come.
        if (defY >= cy1 + under) {
            Stone.icon(g, icon("siege"), px0, defY, 16, 1f);
            String siege = SkillstoneScreen.tr("proficiency.stone.siege_line", state.defenceStreak(), siegesLine(state).getString());
            String siegeShown = Stone.fit(font, siege, pw - 20);
            Stone.text(g, font, siegeShown, px0 + 20, defY + 4, Stone.DIM);
            if (hov(mx, my, px0, defY, pw, 16)) {
                tooltip.add(Component.translatable("proficiency.hearth.client.panel.defence", state.defenceStreak())
                        .withStyle(style -> style.withColor(0xDEA06E)));
                tooltip.add(siegesLine(state).copy().withStyle(ChatFormatting.GRAY));
                tooltip.add(dim("panel.defence.tip"));
            }
        }
        // The plates: Show range and Build it for me on top, the guide and its design under them. A refused Build it
        // for me reads the reason on its own plate (no chip).
        int half = (pw - gapP) / 2;
        boolean guideOn = HearthGhost.showing(state.pos());
        button(g, now, mx, my, PLATE, P_GUIDE, px0, plateY1, half, plateH,
                SkillstoneScreen.tr(guideOn ? "proficiency.stone.ghost_on" : "proficiency.stone.ghost_off"), true, guideOn);
        var lanes = HearthGuide.lanes();
        String lane = lanes.isEmpty() ? "" : SkillstoneScreen.tr("proficiency.hearth.client.panel.guide.lane",
                HearthGuide.laneName(lanes.get(Math.floorMod(HearthGhost.lane(), lanes.size())).id()).getString());
        button(g, now, mx, my, PLATE, P_LANE, px0 + half + gapP, plateY1, pw - half - gapP, plateH, lane, !lanes.isEmpty(), false);
        button(g, now, mx, my, PLATE, P_RANGE, px0, plateY0, half, plateH, SkillstoneScreen.tr("proficiency.stone.show_range"),
                true, false);
        int bx = px0 + half + gapP;
        int bw = pw - half - gapP;
        long refused = now - buildRefusedAt;
        if (refused >= 0 && refused < REFUSED_MS) {
            String why = SkillstoneScreen.tr("proficiency.stone.campfire_build");
            String shown = Stone.fit(font, why, bw - 8);
            Object key = "b" + PLATE + ":" + P_BUILD;
            click.begin(g, key, bx, plateY0, bw, plateH, now);
            Stone.plate(g, font, bx, plateY0, bw, plateH, shown, 0xFF7864, false);
            click.end(g, key, bx, plateY0, bw, plateH, now);
            hits.add(new int[] {PLATE, P_BUILD, bx, plateY0, bw, plateH});
            // The hover below gives the reason in full; no second copy here.
        } else {
            button(g, now, mx, my, PLATE, P_BUILD, bx, plateY0, bw, plateH,
                    SkillstoneScreen.tr("proficiency.hearth.client.panel.build.start"), false, false);
        }
        if (hov(mx, my, px0, plateY1, half, plateH)) {
            tooltip.add(Component.literal(SkillstoneScreen.tr("proficiency.stone.ghost_tip")).withStyle(ChatFormatting.GRAY));
        } else if (hov(mx, my, px0 + half + gapP, plateY0, pw - half - gapP, plateH)) {
            tooltip.add(Component.literal(SkillstoneScreen.tr("proficiency.stone.campfire_build")).withStyle(ChatFormatting.RED));
            tooltip.add(dim("panel.build.tip"));
        }
    }

    private void poolTip(HearthStatePayload state, int pool, PoolRow row, double[] thresholds) {
        int tier = row == null ? 0 : row.tier();
        tooltip.add(HearthText.pool(pool).copy().withStyle(style -> style.withColor(
                dev.amman.proficiency.hearth.client.HearthPalette.pool(pool) & 0xFFFFFF)));
        double charge = row == null ? 0 : row.charge();
        int chargeTier = row == null ? 0 : row.chargeTier();
        if (chargeTier < thresholds.length) {
            tooltip.add(Component.translatable("proficiency.hearth.client.panel.charge", Math.round(charge),
                    Math.round(thresholds[chargeTier]), HearthText.roman(chargeTier + 1)).withStyle(ChatFormatting.GRAY));
        }
        Component held = row == null ? null : HearthText.held(row.lock(), row.need(), tier);
        if (held != null) {
            tooltip.add(held.copy().withStyle(ChatFormatting.RED));
        }
        tooltip.add(Component.literal(boonText(state, pool, tier)));
        HearthPool p = HearthPool.byIndex(pool);
        tooltip.add(dim("panel.feeds." + (p == null ? "hearty" : p.id())));
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

    @Override
    void hit(int[] h, long now, boolean shift) {
        HearthStatePayload state = HearthView.panelState();
        Minecraft mc = Minecraft.getInstance();
        if (state == null || h[0] != PLATE) {
            return;
        }
        Object key = key(h);
        switch (h[1]) {
            case P_GUIDE -> {
                boolean on = HearthGhost.toggle(state);
                if (on) {
                    // The ghosts of stage I stand up around the campfire: a burst and a flint strike.
                    click.success(key, h[2], h[3], h[4], h[5], Stone.ember(0.75f), 1, 1f, now);
                    shake(now, 1.5f);
                    SkillstoneScreen.sound(SoundEvents.FLINTANDSTEEL_USE, 1.2f, 0.5f);
                } else {
                    click.press(key, h[2], h[3], h[4], h[5], Stone.ember(0.6f), now);
                    SkillstoneScreen.sound(SoundEvents.STONE_HIT, 0.9f, 0.5f);
                }
            }
            case P_LANE -> {
                if (HearthGuide.lanes().isEmpty()) {
                    // The plate is drawn disabled when no design data loaded: nothing to step.
                    return;
                }
                HearthGhost.cycleLane();
                click.press(key, h[2], h[3], h[4], h[5], Stone.ember(0.75f), now);
                SkillstoneScreen.sound(SoundEvents.STONE_HIT, 1.1f, 0.5f);
            }
            case P_RANGE -> {
                ClickFx.plainClick();
                HearthRangeRing.showFor(state.pos(), state.dimension(), net.minecraft.Util.getMillis());
                mc.setScreen(null);
                mc.gui.setOverlayMessage(Component.translatable("proficiency.hearth.client.panel.range_shown")
                        .withStyle(style -> style.withColor(0xE68232)), false);
            }
            default -> {
                // The wiggle, the flash and the thunk; the reason replaces the plate's own label (no chip: below the
                // plate is the guide's row, above it the defence line).
                refuse(key, h, null, now);
                buildRefusedAt = now;
            }
        }
    }

    // ---- Layout demo ----------------------------------------------------------------------------

    /** For the layout demo: the centre of plate {@code index} (0 guide, 1 design, 2 range, 3 build). */
    public int[] platePoint(int index) {
        return point(PLATE, index);
    }

    /** For the layout demo: the centre of stage I's stone (0) or the first crucible (1), on screen. */
    public int[] partPoint(int which) {
        int[] r = which == 0 ? stageRect : poolRect;
        return new int[] {r[0] + r[2] / 2, r[1] + r[3] / 2};
    }

    /** For the layout demo: press plate {@code index} as a click would. */
    public void demoPress(int index) {
        for (int[] h : hits) {
            if (h[0] == PLATE && h[1] == index) {
                hit(h, SkillstoneScreen.clock(), false);
                return;
            }
        }
    }
}
