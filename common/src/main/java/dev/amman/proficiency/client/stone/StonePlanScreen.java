package dev.amman.proficiency.client.stone;

import dev.amman.proficiency.client.ClickFx;
import dev.amman.proficiency.client.ClickFxMath;
import dev.amman.proficiency.hearth.HearthRules;
import dev.amman.proficiency.hearth.net.HearthBuildPlanPayload;
import dev.amman.proficiency.hearth.net.HearthRequestPayload;
import dev.amman.proficiency.hearth.stages.BuildPlan;
import dev.amman.proficiency.hearth.stages.HearthAutoBuild;
import dev.amman.proficiency.hearth.stages.HearthDesigns;
import dev.amman.proficiency.hearth.stages.StageText;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The self-build's plan as a slab page (1.32.0). The same plan as the classic screen (the server's
 * {@link HearthBuildPlanPayload}, asked again every 5 s while open): one row per kind of block the next stage
 * still needs, its block in a socket, need / on hand / missing; a row opens to every block in range as small
 * sockets, and a click makes one the block the build takes. The lane plate steps through the designs and the
 * big plate starts or stops the build: it charges until the server answers, then bursts (start) or settles.
 */
public final class StonePlanScreen extends StoneSheet {

    private static final int ROW = 1;
    private static final int OPTION = 2;
    private static final int LANE = 3;
    private static final int START = 4;
    private static final int REFRESH_TICKS = 100;
    private static final int OK = 0x8FD46A;
    private static final int MISSING = 0xFF7864;

    private HearthBuildPlanPayload plan;
    private int lane;
    private char open;
    private int scroll;
    private int ticks;
    private int viewTop;
    private int viewH;
    private int rowH;
    private int cell;
    private boolean togglePending;
    private boolean toggleWasRunning;
    private long toggleAt;
    /** Start / Stop counts as refused once its charge has expired (ClickFx shows the wiggle then). */
    private static final long TOGGLE_TIMEOUT_MS = dev.amman.proficiency.client.ClickFxMath.PENDING_MS;
    private int[] startHit;
    /** Each option hit's block, by its index in the hits (the int[] cannot carry a string). */
    private final List<String> optionBlocks = new ArrayList<>();

    private StonePlanScreen(HearthBuildPlanPayload plan, net.minecraft.client.gui.screens.Screen parent) {
        super(Component.translatable("proficiency.hearth.client.plan.screen"), parent);
        this.plan = plan;
        this.lane = plan.lane();
    }

    /**
     * A plan from the server, under {@code ui.style = modern}: updates an open plan page of the same Hearth, or
     * opens one when the panel asked ({@code asked}). False when the classic screen should take it.
     */
    public static boolean accept(HearthBuildPlanPayload payload, boolean asked) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof StonePlanScreen screen && screen.plan.pos() == payload.pos()) {
            screen.update(payload);
            return true;
        }
        if (asked && SkillstoneScreen.modern()) {
            mc.setScreen(new StonePlanScreen(payload, mc.screen instanceof SkillstoneScreen ? mc.screen : null));
            return true;
        }
        return false;
    }

    /** For the layout demo: a plan page over {@code parent} without the server. */
    public static StonePlanScreen demo(HearthBuildPlanPayload payload, net.minecraft.client.gui.screens.Screen parent) {
        return new StonePlanScreen(payload, parent);
    }

    private void update(HearthBuildPlanPayload payload) {
        boolean answered = togglePending && payload.running() != toggleWasRunning;
        this.plan = payload;
        this.lane = payload.lane();
        if (answered && startHit != null) {
            togglePending = false;
            long now = SkillstoneScreen.clock();
            // Starting the build is this page's big button: a full success, a shake and a flare; stopping settles.
            if (payload.running()) {
                click.success("b" + START + ":0", startHit[2], startHit[3], startHit[4], startHit[5], Stone.ember(0.8f), 2, 1f, now);
                riseShake(now);
                SkillstoneScreen.sound(SoundEvents.FIRECHARGE_USE, 0.9f, 0.6f);
            } else {
                click.success("b" + START + ":0", startHit[2], startHit[3], startHit[4], startHit[5], Stone.LOCK, 0, 1f, now);
            }
        }
    }

    private void ask() {
        StoneNet.hearth(new HearthRequestPayload(HearthRequestPayload.BUILD_PLAN, plan.pos(), plan.running() ? -1 : lane));
    }

    @Override
    public void tick() {
        // The server answers a refused start with an unchanged plan (or not at all): settle the plate as a refusal.
        if (togglePending && SkillstoneScreen.clock() - toggleAt > TOGGLE_TIMEOUT_MS) {
            togglePending = false;
            if (startHit != null) {
                say(startHit, SkillstoneScreen.tr(toggleWasRunning ? "proficiency.stone.plan_not_stopped"
                        : "proficiency.stone.plan_not_started"), SkillstoneScreen.clock());
                if (!ClickFx.on()) {
                    ClickFx.plainClick();
                }
            }
        }
        if (++ticks % REFRESH_TICKS == 0) {
            ask();
        }
    }

    @Override
    String crumb() {
        return SkillstoneScreen.tr("proficiency.stone.back_hearth");
    }

    @Override
    int maxWidth() {
        return 480;
    }

    @Override
    void layout() {
        rowH = small ? 24 : 28;
        cell = small ? 24 : 26;
        viewTop = iy0 + (small ? 37 : 50);
        viewH = Math.max(rowH, iy1 - (small ? 28 : 40) - viewTop);
    }

    // ---- Render ---------------------------------------------------------------------------------

    @Override
    void body(GuiGraphics g, long now, int mx, int my) {
        optionBlocks.clear();
        int pad = small ? 8 : 14;
        int x0 = ix0 + pad;
        int x1 = ix1 - pad;
        int y = iy0 + (small ? 6 : 10);
        String stage = plan.stage() > HearthDesigns.STAGES ? SkillstoneScreen.tr("proficiency.hearth.client.panel.complete")
                : SkillstoneScreen.tr("proficiency.hearth.build.stage", HearthRules.roman(plan.stage()),
                StageText.stageName(plan.stage()).getString());
        List<BuildPlan.Row> sums = sums();
        int[] totals = BuildPlan.totals(sums);
        head(g, stage.toUpperCase(java.util.Locale.ROOT), SkillstoneScreen.tr("proficiency.stone.plan_blocks", totals[0]),
                x0, x1, y);
        y += small ? 11 : 13;
        String summary = SkillstoneScreen.tr("proficiency.hearth.client.plan.summary", totals[0], totals[0] - totals[1], totals[1],
                HearthAutoBuild.duration((long) (totals[0] + plan.clears()) * plan.secondsPerBlock()));
        Stone.text(g, this.font, Stone.fit(this.font, summary, x1 - x0), x0, y, totals[1] == 0 ? OK : Stone.INK);
        y += small ? 10 : 12;
        String note;
        int colour = Stone.DIM;
        if (plan.foreign() > 0) {
            note = SkillstoneScreen.tr("proficiency.hearth.client.plan.foreign", plan.foreign());
            colour = MISSING;
        } else if (plan.running()) {
            note = SkillstoneScreen.tr("proficiency.hearth.client.plan.running");
            colour = Stone.GOLD;
        } else if (plan.clears() > 0) {
            note = SkillstoneScreen.tr("proficiency.hearth.client.plan.clears", plan.clears());
        } else {
            note = SkillstoneScreen.tr("proficiency.hearth.client.plan.hint");
        }
        Stone.text(g, this.font, Stone.fit(this.font, note, x1 - x0), x0, y, colour);

        Stone.well(g, x0 - 4, viewTop, x1 - x0 + 8, viewH);
        int content = bodyHeight(x1 - x0 - 2);
        scroll = Math.max(0, Math.min(scroll, content - (viewH - 8)));
        Stone.scissor(g, x0 - 2, viewTop + 3, x1 + 4, viewTop + viewH - 3);
        boolean inView = my >= viewTop + 3 && my < viewTop + viewH - 3;
        int ry = viewTop + 5 - scroll;
        if (plan.rows().isEmpty()) {
            Stone.text(g, this.font, SkillstoneScreen.tr(plan.stage() > HearthDesigns.STAGES ? "proficiency.hearth.build.complete"
                    : "proficiency.hearth.client.plan.nothing"), x0 + 4, ry + 4, Stone.DIM);
        }
        for (int i = 0; i < plan.rows().size(); i++) {
            ry = row(g, now, plan.rows().get(i), sums.get(i), i, x0 + 2, x1 - 8, ry, inView ? mx : -1, inView ? my : -1);
        }
        g.disableScissor();
        scrollbar(g, x1 - 2, viewTop + 4, viewH - 8, scroll, content);

        // The plates: the lane (not while building) and the big start / stop.
        int ph = small ? 16 : 20;
        int py = iy1 - (small ? 6 : 12) - ph;
        List<HearthDesigns.Lane> lanes = HearthDesigns.shipped().lanes();
        String laneLabel = SkillstoneScreen.tr("proficiency.hearth.client.panel.guide.lane",
                SkillstoneScreen.tr("proficiency.hearth.guide.lane." + lanes.get(Math.floorMod(lane, lanes.size())).id()));
        String start = SkillstoneScreen.tr(plan.running() ? "proficiency.hearth.client.panel.build.stop"
                : "proficiency.hearth.client.plan.start");
        int sw0 = this.font.width(start) + (small ? 20 : 30);
        int lw = Math.min(x1 - x0 - sw0 - 8, this.font.width(laneLabel) + 16);
        button(g, now, mx, my, LANE, 0, x0, py, lw, ph, laneLabel, !plan.running(), false);
        boolean can = canStart();
        button(g, now, mx, my, START, 0, x1 - sw0, py, sw0, ph, start, can, can && !plan.running());
        if (plan.running()) {
            g.fill(x1 - sw0 + 3, py + ph, x1 - 3, py + ph + 2, Stone.argb(255, Stone.ember(0.8f)));
        }
        startHit = new int[] {START, 0, x1 - sw0, py, sw0, ph};
    }

    private boolean canStart() {
        return plan.running() || (plan.foreign() == 0 && !plan.rows().isEmpty() && plan.stage() <= HearthDesigns.STAGES);
    }

    private int perLine(int width) {
        return Math.max(1, (width - 30) / cell);
    }

    private int bodyHeight(int width) {
        int h = 10;
        for (HearthBuildPlanPayload.Row row : plan.rows()) {
            h += rowH;
            if (row.role() == open) {
                h += row.options().isEmpty() ? 16 : ((row.options().size() + perLine(width) - 1) / perLine(width)) * cell + 6;
            }
        }
        return h;
    }

    /** One role: its block in a socket, role and block, need / have / missing; open, the blocks it may take. */
    private int row(GuiGraphics g, long now, HearthBuildPlanPayload.Row row, BuildPlan.Row sum, int index, int x0, int x1,
                    int y, int mx, int my) {
        boolean fluid = HearthDesigns.isFluid(row.role());
        boolean isOpen = row.role() == open;
        boolean hov = mx >= x0 && mx < x1 && my >= y && my < y + rowH - 2;
        if (hov || isOpen) {
            Stone.fillRound(g, x0 - 2, y - 2, x1 - x0 + 4, rowH - 2, 3, Stone.argb(isOpen ? 40 : 26, 0xFFF0DC));
        }
        int ss = rowH - 6;
        boolean done = sum.missing() == 0;
        Object key = "b" + ROW + ":" + index;
        click.begin(g, key, x0, y, ss, ss, now);
        Stone.socket(g, x0, y, ss, ss, done ? 0.5f : 0f, isOpen, hov);
        ItemStack icon = icon(row.chosen());
        if (!icon.isEmpty()) {
            g.renderItem(icon, x0 + (ss - 16) / 2, y + (ss - 16) / 2);
        }
        click.end(g, key, x0, y, ss, ss, now);
        int tx = x0 + ss + 6;
        String role = StageText.role(row.role()).getString();
        role = role.isEmpty() ? role : role.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + role.substring(1);
        String chosen = row.chosen().isEmpty() ? SkillstoneScreen.tr("proficiency.hearth.client.plan.none") : name(row.chosen()).getString();
        Stone.text(g, this.font, Stone.fit(this.font, role + "  ·  " + chosen, x1 - tx - 14), tx, y + 1, Stone.INK);
        String counts = done ? SkillstoneScreen.tr("proficiency.hearth.client.plan.counts", sum.need(), sum.have())
                : SkillstoneScreen.tr("proficiency.hearth.client.plan.counts_missing", sum.need(), sum.have(), sum.missing());
        Stone.text(g, this.font, Stone.fit(this.font, counts, x1 - tx - 14), tx, y + (small ? 11 : 13), done ? OK : MISSING);
        if (!fluid) {
            Stone.textRight(g, this.font, isOpen ? "▲" : "▼", x1, y + (rowH - 10) / 2, hov ? Stone.GOLD : Stone.DIM);
        }
        clipHit(new int[] {ROW, index, x0, y, x1 - x0, rowH - 2});
        if (hov) {
            tooltip.add(Component.translatable(fluid ? "proficiency.stone.plan_fixed" : "proficiency.hearth.client.plan.pick")
                    .withStyle(ChatFormatting.GRAY));
        }
        y += rowH;
        if (!isOpen) {
            return y;
        }
        if (row.options().isEmpty()) {
            Stone.text(g, this.font, SkillstoneScreen.tr("proficiency.hearth.client.plan.empty"), tx, y, Stone.LOCK);
            return y + 16;
        }
        int per = perLine(x1 - x0 + 8);
        for (int i = 0; i < row.options().size(); i++) {
            HearthBuildPlanPayload.Option option = row.options().get(i);
            int cx = tx + (i % per) * cell;
            int cy = y + (i / per) * cell;
            int cs = cell - 2;
            boolean selected = option.block().equals(row.chosen());
            boolean over = mx >= cx && mx < cx + cs && my >= cy && my < cy + cs;
            Object okey = "opt" + row.role() + option.block();
            click.begin(g, okey, cx, cy, cs, cs, now);
            Stone.socket(g, cx, cy, cs, cs, selected ? 0.8f : 0f, selected, over);
            ItemStack stack = icon(option.block());
            g.renderItem(stack, cx + (cs - 16) / 2, cy + (cs - 16) / 2);
            g.renderItemDecorations(this.font, stack, cx + (cs - 16) / 2, cy + (cs - 16) / 2, shortCount(option.count()));
            click.end(g, okey, cx, cy, cs, cs, now);
            clipHit(new int[] {OPTION, optionBlocks.size(), cx, cy, cs, cs, row.role()});
            optionBlocks.add(option.block());
            if (over) {
                optionTip(row, option, selected);
            }
        }
        return y + ((row.options().size() + per - 1) / per) * cell + 6;
    }

    /** A hit inside the scrolled well, cut to what is visible; one scrolled out is not clickable. */
    private void clipHit(int[] h) {
        int top = Math.max(h[3], viewTop + 3);
        int bottom = Math.min(h[3] + h[5], viewTop + viewH - 3);
        if (bottom > top) {
            h[3] = top;
            h[5] = bottom - top;
            hits.add(h);
        }
    }

    private void optionTip(HearthBuildPlanPayload.Row row, HearthBuildPlanPayload.Option option, boolean selected) {
        tooltip.add(name(option.block()).copy().withStyle(selected ? ChatFormatting.GOLD : ChatFormatting.WHITE));
        tooltip.add(Component.translatable("proficiency.hearth.client.plan.on_hand", option.count()).withStyle(ChatFormatting.GRAY));
        for (HearthBuildPlanPayload.Where where : option.where()) {
            if (where.pack()) {
                tooltip.add(Component.translatable("proficiency.hearth.client.plan.where_pack", where.count())
                        .withStyle(ChatFormatting.DARK_GRAY));
            } else {
                tooltip.add(Component.translatable("proficiency.hearth.client.plan.where", container(where),
                        StageText.where(where.dx(), where.dy(), where.dz()), where.count()).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        if (!HearthDesigns.isFluid(row.role())) {
            tooltip.add(Component.translatable(selected ? "proficiency.hearth.client.plan.chosen"
                    : "proficiency.hearth.client.plan.choose").withStyle(selected ? ChatFormatting.GOLD : ChatFormatting.YELLOW));
        }
    }

    private Component container(HearthBuildPlanPayload.Where where) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            BlockPos at = BlockPos.of(plan.pos()).offset(where.dx(), where.dy(), where.dz());
            if (mc.level.isLoaded(at)) {
                return mc.level.getBlockState(at).getBlock().getName();
            }
        }
        return Component.translatable("proficiency.hearth.client.plan.storage");
    }

    // ---- Input ----------------------------------------------------------------------------------

    @Override
    void hit(int[] h, long now, boolean shift) {
        switch (h[0]) {
            case ROW -> {
                // Hits come from the last frame; a refresh may have shortened the plan since.
                if (h[1] < 0 || h[1] >= plan.rows().size()) {
                    return;
                }
                HearthBuildPlanPayload.Row row = plan.rows().get(h[1]);
                int ss = rowH - 6;
                int[] socket = {ROW, h[1], h[2], h[3], ss, ss};
                if (HearthDesigns.isFluid(row.role())) {
                    refuse("b" + ROW + ":" + h[1], socket, SkillstoneScreen.tr("proficiency.stone.plan_fixed"), now);
                    return;
                }
                open = open == row.role() ? 0 : row.role();
                click.press("b" + ROW + ":" + h[1], h[2], h[3], ss, ss, Stone.ember(0.75f), now);
                SkillstoneScreen.sound(SoundEvents.STONE_HIT, open == 0 ? 0.9f : 1.2f, 0.5f);
            }
            case OPTION -> {
                if (h[1] < 0 || h[1] >= optionBlocks.size()) {
                    return;
                }
                char role = (char) h[6];
                String block = optionBlocks.get(h[1]);
                String was = null;
                for (HearthBuildPlanPayload.Row row : plan.rows()) {
                    if (row.role() == role) {
                        was = row.chosen();
                    }
                }
                boolean fresh = was == null || !was.equals(block);
                StoneNet.hearth(new HearthRequestPayload(HearthRequestPayload.BUILD_CHOOSE, plan.pos(), role, block));
                ask();
                // A new pick bursts out of its socket and the slab gives a knock; re-picking only blips.
                click.success("opt" + role + block, h[2], h[3], h[4], h[5], Stone.ember(0.75f), fresh ? 1 : 0, 0.7f, now);
                if (fresh) {
                    shake(now + ClickFxMath.MIN_CHARGE_MS, 1.5f);
                }
                SkillstoneScreen.sound(SoundEvents.STONE_PLACE, 1.2f, 0.5f);
            }
            case LANE -> {
                if (plan.running()) {
                    refuse(key(h), h, SkillstoneScreen.tr("proficiency.hearth.client.plan.running"), now);
                    return;
                }
                lane = (lane + 1) % HearthDesigns.shipped().lanes().size();
                open = 0;
                click.press(key(h), h[2], h[3], h[4], h[5], Stone.ember(0.75f), now);
                SkillstoneScreen.sound(SoundEvents.STONE_HIT, 1.0f, 0.5f);
                ask();
            }
            case START -> {
                if (!canStart()) {
                    String why = plan.foreign() > 0 ? SkillstoneScreen.tr("proficiency.hearth.client.plan.foreign", plan.foreign())
                            : SkillstoneScreen.tr(plan.stage() > HearthDesigns.STAGES ? "proficiency.hearth.build.complete"
                            : "proficiency.hearth.client.plan.nothing");
                    refuse(key(h), h, why, now);
                    return;
                }
                // Charges until the server answers; update() turns it into the burst.
                togglePending = true;
                toggleAt = now;
                toggleWasRunning = plan.running();
                click.charge(key(h), h[2], h[3], h[4], h[5], plan.running() ? Stone.LOCK : Stone.ember(0.8f), now);
                StoneNet.hearth(new HearthRequestPayload(plan.running() ? HearthRequestPayload.BUILD_STOP
                        : HearthRequestPayload.BUILD_START, plan.pos(), lane));
                ask();
            }
            default -> {
            }
        }
    }

    @Override
    boolean scroll(double mx, double my, double delta) {
        int before = scroll;
        scroll = Math.max(0, scroll - (int) Math.signum(delta) * rowH);
        return scroll != before;
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            if (parent != null) {
                this.minecraft.setScreen(parent);
            } else {
                SkillstoneScreen.openHearth();
            }
        }
    }

    /** For the layout demo: open the first row that has blocks to pick from. */
    public void demoOpen() {
        for (HearthBuildPlanPayload.Row row : plan.rows()) {
            if (!row.options().isEmpty() && !HearthDesigns.isFluid(row.role())) {
                open = row.role();
                return;
            }
        }
    }

    // ---- Helpers --------------------------------------------------------------------------------

    private List<BuildPlan.Row> sums() {
        Map<Character, Integer> needs = new LinkedHashMap<>();
        Map<Character, String> chosen = new HashMap<>();
        Map<String, Integer> stock = new HashMap<>();
        for (HearthBuildPlanPayload.Row row : plan.rows()) {
            needs.put(row.role(), row.need());
            chosen.put(row.role(), row.chosen());
            for (HearthBuildPlanPayload.Option option : row.options()) {
                stock.put(option.block(), option.count());
            }
        }
        return BuildPlan.rows(needs, chosen, stock);
    }

    private static ItemStack icon(String id) {
        ResourceLocation key = id.isEmpty() ? null : ResourceLocation.tryParse(id);
        if (key == null) {
            return ItemStack.EMPTY;
        }
        if (BuiltInRegistries.ITEM.containsKey(key)) {
            return new ItemStack(BuiltInRegistries.ITEM.get(key));
        }
        Block block = BuiltInRegistries.BLOCK.get(key);
        return new ItemStack(block.asItem());
    }

    private static Component name(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null) {
            return Component.literal(id);
        }
        if (BuiltInRegistries.BLOCK.containsKey(key)) {
            return BuiltInRegistries.BLOCK.get(key).getName();
        }
        return icon(id).getHoverName();
    }

    private static String shortCount(int count) {
        return count >= 1000 ? (count / 1000) + "k" : String.valueOf(count);
    }
}
