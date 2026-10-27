package dev.amman.proficiency.hearth.client;

import dev.amman.proficiency.client.ClickFx;
import dev.amman.proficiency.client.HudKit;
import dev.amman.proficiency.client.SkillPalette;
import dev.amman.proficiency.hearth.HearthRules;
import dev.amman.proficiency.hearth.net.HearthBuildPlanPayload;
import dev.amman.proficiency.hearth.net.HearthRequestPayload;
import dev.amman.proficiency.hearth.stages.BuildPlan;
import dev.amman.proficiency.hearth.stages.HearthAutoBuild;
import dev.amman.proficiency.hearth.stages.HearthDesigns;
import dev.amman.proficiency.hearth.stages.StageText;
import dev.amman.proficiency.net.ProficiencyNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The self-build's plan (HearthAutoBuild on the server): one row per kind of block the next stage still needs, with
 * the block it takes, how many it needs, how many the packs and chests in range hold of that block and how many are
 * missing. A row opens to every block in range that fits; hovering one says where it lies, clicking it makes it the
 * block the build takes (a choice is binding: the build waits rather than take another kind). Start and stop are
 * here; the panel's "Build it for me" opens it. It asks the server again every 5 s while open.
 */
public final class HearthBuildScreen extends Screen {
    private final ClickFx click = new ClickFx();

    private static final int PANEL_WIDTH = 420;
    private static final int PADDING = 10;
    private static final int LINE = 10;
    private static final int ROW_H = 26;
    private static final int CELL = 20;
    private static final int OK = 0xFF8FD46A;
    private static final int REFRESH_TICKS = 100;

    /** A plan asked for by the panel opens this screen when it comes; later ones only update an open screen. */
    private static long pendingOpen;

    private HearthBuildPlanPayload plan;
    private int lane;
    private char open;
    private int scroll;
    private int ticks;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int bodyTop;
    private int viewportH;
    private int bodyH;
    private Button laneButton;
    private Button startButton;
    private Button backButton;

    private record Hit(int x, int y, int w, int h, char role, String block, Supplier<List<Component>> tip) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final List<Hit> hits = new ArrayList<>();

    private HearthBuildScreen(HearthBuildPlanPayload plan) {
        super(Component.translatable("proficiency.hearth.client.plan.screen"));
        this.plan = plan;
        this.lane = plan.lane();
    }

    // ---- Opening and the server's answers ---------------------------------------------------------------

    /** The panel's button: asks for the plan; the screen opens when it comes. {@code lane} -1: the Hearth's own. */
    public static void request(long pos, int lane) {
        pendingOpen = pos;
        send(new HearthRequestPayload(HearthRequestPayload.BUILD_PLAN, pos, lane));
    }

    public static void accept(HearthBuildPlanPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        // ui.style = modern: the plan is a Skillstone slab page.
        if (dev.amman.proficiency.client.stone.StonePlanScreen.accept(payload, pendingOpen == payload.pos())) {
            if (pendingOpen == payload.pos()) {
                pendingOpen = 0;
            }
            return;
        }
        if (minecraft.screen instanceof HearthBuildScreen screen && screen.plan.pos() == payload.pos()) {
            screen.update(payload);
        } else if (pendingOpen == payload.pos()) {
            pendingOpen = 0;
            minecraft.setScreen(new HearthBuildScreen(payload));
        }
    }

    private void update(HearthBuildPlanPayload payload) {
        boolean answered = togglePending && payload.running() != toggleWasRunning;
        this.plan = payload;
        this.lane = payload.lane();
        rebuildWidgets();
        if (answered) {
            togglePending = false;
            // Starting the build is the big button of this screen: a full success; stopping is a plain one.
            click.success(ClickFx.buttonKey(startButton), startButton.getX(), startButton.getY(),
                    startButton.getWidth(), startButton.getHeight(),
                    payload.running() ? HearthPalette.EMBER : HearthPalette.SIEGE, payload.running() ? 1 : 0, 1f,
                    System.currentTimeMillis());
        }
    }

    private static void send(HearthRequestPayload payload) {
        ProficiencyNetwork.sendToServer(payload);
    }

    private void ask() {
        send(new HearthRequestPayload(HearthRequestPayload.BUILD_PLAN, plan.pos(), plan.running() ? -1 : lane));
    }

    @Override
    public void tick() {
        if (++ticks % REFRESH_TICKS == 0) {
            ask();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---- Layout -----------------------------------------------------------------------------------------

    @Override
    protected void init() {
        panelW = Math.min(this.width - 8, Math.max(300, PANEL_WIDTH));
        int headerH = PADDING + LINE + 4 + LINE * 2 + 6;
        int footerH = 6 + 20 + PADDING - 2;
        bodyH = bodyHeight();
        viewportH = Math.max(ROW_H * 3, Math.min(bodyH, this.height - 8 - headerH - footerH));
        panelH = headerH + viewportH + footerH;
        panelX = (this.width - panelW) / 2;
        panelY = Math.max(4, (this.height - panelH) / 2);
        bodyTop = panelY + headerH;
        scroll = Math.max(0, Math.min(scroll, bodyH - viewportH));

        int footerY = panelY + panelH - PADDING - 18;
        Component back = Component.translatable("proficiency.hearth.client.plan.back");
        Component start = startLabel();
        Component laneLabel = laneLabel();
        int backW = this.font.width(back) + 16;
        int startW = Math.max(this.font.width(Component.translatable("proficiency.hearth.client.plan.start")),
                this.font.width(Component.translatable("proficiency.hearth.client.panel.build.stop"))) + 16;
        int laneW = this.font.width(laneLabel) + 16;
        int inner = panelW - PADDING * 2;
        if (backW + laneW + startW + 8 > inner) {
            laneW = Math.max(40, inner - backW - startW - 8);
        }
        int x = panelX + PADDING;
        backButton = addWidget(Button.builder(back, b -> back()).bounds(x, footerY, backW, 18).build());
        laneButton = addWidget(Button.builder(laneLabel, b -> cycleLane())
                .bounds(x + backW + 4, footerY, laneW, 18).build());
        startButton = addWidget(Button.builder(start, b -> toggle())
                .bounds(panelX + panelW - PADDING - startW, footerY, startW, 18).build());
        laneButton.active = !plan.running();
        startButton.active = plan.running() || (plan.foreign() == 0 && !plan.rows().isEmpty()
                && plan.stage() <= HearthDesigns.STAGES);
    }

    private int bodyHeight() {
        int h = 0;
        for (HearthBuildPlanPayload.Row row : plan.rows()) {
            h += ROW_H;
            if (row.role() == open) {
                h += optionsHeight(row);
            }
        }
        return Math.max(h, ROW_H);
    }

    private int optionsHeight(HearthBuildPlanPayload.Row row) {
        if (row.options().isEmpty()) {
            return LINE + 6;
        }
        int perLine = Math.max(1, (panelW - PADDING * 2 - 24) / CELL);
        int lines = (row.options().size() + perLine - 1) / perLine;
        return lines * CELL + 6;
    }

    private Component startLabel() {
        return Component.translatable(plan.running() ? "proficiency.hearth.client.panel.build.stop"
                : "proficiency.hearth.client.plan.start");
    }

    private Component laneLabel() {
        List<HearthDesigns.Lane> lanes = HearthDesigns.shipped().lanes();
        String id = lanes.get(Math.floorMod(lane, lanes.size())).id();
        return Component.translatable("proficiency.hearth.client.panel.guide.lane",
                Component.translatable("proficiency.hearth.guide.lane." + id));
    }

    // ---- Actions ----------------------------------------------------------------------------------------

    private void back() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(new HearthScreen());
        }
    }

    private void cycleLane() {
        lane = (lane + 1) % HearthDesigns.shipped().lanes().size();
        open = 0;
        ask();
    }

    /** Set while a start/stop is out: the button shakes until the plan comes back in the other state. */
    private boolean toggleWasRunning;
    private boolean togglePending;

    private void toggle() {
        // Charge until the server answers; update() turns it into the success, a refusal times out into "no".
        togglePending = true;
        toggleWasRunning = plan.running();
        click.charge(ClickFx.buttonKey(startButton), startButton.getX(), startButton.getY(),
                startButton.getWidth(), startButton.getHeight(),
                plan.running() ? HearthPalette.SIEGE : HearthPalette.EMBER, System.currentTimeMillis());
        send(new HearthRequestPayload(plan.running() ? HearthRequestPayload.BUILD_STOP
                : HearthRequestPayload.BUILD_START, plan.pos(), lane));
        ask();
    }

    private String chosenBlock(char role) {
        for (HearthBuildPlanPayload.Row row : plan.rows()) {
            if (row.role() == role) {
                return row.chosen();
            }
        }
        return null;
    }

    private void choose(char role, String block) {
        send(new HearthRequestPayload(HearthRequestPayload.BUILD_CHOOSE, plan.pos(), role, block));
        ask();
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.2f));
    }

    // ---- Rendering --------------------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        hits.clear();
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, SkillPalette.PANEL);
        border(graphics, panelX, panelY, panelW, panelH, SkillPalette.PANEL_BORDER);
        int x = panelX + PADDING;
        int width = panelW - PADDING * 2;
        int y = panelY + PADDING;
        drawHeader(graphics, x, y, width);

        graphics.enableScissor(panelX, bodyTop, panelX + panelW, bodyTop + viewportH);
        graphics.pose().pushPose();
        graphics.pose().translate(0, -scroll, 0);
        int my = mouseY + scroll;
        boolean inBody = mouseY >= bodyTop && mouseY < bodyTop + viewportH;
        int rowY = bodyTop;
        if (plan.rows().isEmpty()) {
            graphics.drawString(this.font, Component.translatable(plan.stage() > HearthDesigns.STAGES
                    ? "proficiency.hearth.build.complete" : "proficiency.hearth.client.plan.nothing"), x, rowY + 4,
                    SkillPalette.TEXT_DIM, false);
        }
        List<BuildPlan.Row> sums = sums();
        for (int i = 0; i < plan.rows().size(); i++) {
            HearthBuildPlanPayload.Row row = plan.rows().get(i);
            rowY = drawRow(graphics, row, sums.get(i), x, rowY, width, inBody ? mouseX : -1, inBody ? my : -1);
        }
        graphics.pose().popPose();
        graphics.disableScissor();
        if (bodyH > viewportH) {
            int thumb = Math.max(8, viewportH * viewportH / bodyH);
            int thumbTop = bodyTop + (int) ((viewportH - thumb) * (scroll / (float) (bodyH - viewportH)));
            graphics.fill(panelX + panelW - 5, bodyTop, panelX + panelW - 3, bodyTop + viewportH, SkillPalette.TRACK);
            graphics.fill(panelX + panelW - 5, thumbTop, panelX + panelW - 3, thumbTop + thumb, SkillPalette.TEXT);
        }
        int footerY = panelY + panelH - PADDING - 18;
        graphics.fill(panelX + PADDING, footerY - 5, panelX + panelW - PADDING, footerY - 4, SkillPalette.PANEL_BORDER);
        click.renderButton(graphics, backButton, mouseX, mouseY, partialTick);
        click.renderButton(graphics, laneButton, mouseX, mouseY, partialTick);
        click.renderButton(graphics, startButton, mouseX, mouseY, partialTick);
        if (plan.running()) {
            graphics.fill(startButton.getX(), startButton.getY() + startButton.getHeight(),
                    startButton.getX() + startButton.getWidth(), startButton.getY() + startButton.getHeight() + 2,
                    HearthPalette.EMBER);
        }
        if (inBody) {
            for (Hit hit : hits) {
                if (hit.contains(mouseX, my) && hit.tip() != null) {
                    graphics.renderComponentTooltip(this.font, hit.tip().get(), mouseX, mouseY);
                    break;
                }
            }
        }
        click.render(graphics, System.currentTimeMillis(), 1f);
    }

    private void drawHeader(GuiGraphics graphics, int x, int y, int width) {
        MutableComponent stage = plan.stage() > HearthDesigns.STAGES ? Component.translatable("proficiency.hearth.client.panel.complete")
                : Component.translatable("proficiency.hearth.build.stage", HearthRules.roman(plan.stage()),
                StageText.stageName(plan.stage()));
        String title = Component.translatable("proficiency.hearth.client.plan.title", stage).getString();
        HudKit.draw(graphics, this.font, "hearth.plan.title", title, x, y, width, HearthPalette.EMBER, false);
        y += LINE + 4;
        int[] totals = BuildPlan.totals(sums());
        int onHand = totals[0] - totals[1];
        String summary = Component.translatable("proficiency.hearth.client.plan.summary", totals[0], onHand, totals[1],
                HearthAutoBuild.duration((long) (totals[0] + plan.clears()) * plan.secondsPerBlock())).getString();
        HudKit.draw(graphics, this.font, "hearth.plan.summary", summary, x, y, width,
                totals[1] == 0 ? OK : SkillPalette.TEXT, false);
        y += LINE;
        Component note;
        int colour = SkillPalette.TEXT_DIM;
        if (plan.foreign() > 0) {
            note = Component.translatable("proficiency.hearth.client.plan.foreign", plan.foreign());
            colour = HearthPalette.SIEGE;
        } else if (plan.running()) {
            note = Component.translatable("proficiency.hearth.client.plan.running");
            colour = HearthPalette.EMBER;
        } else if (plan.clears() > 0) {
            note = Component.translatable("proficiency.hearth.client.plan.clears", plan.clears());
        } else {
            note = Component.translatable("proficiency.hearth.client.plan.hint");
        }
        HudKit.draw(graphics, this.font, "hearth.plan.note", note.getString(), x, y, width, colour, false);
    }

    /** One role: icon, name and chosen block, then need/have/missing; open, the blocks it may take. */
    private int drawRow(GuiGraphics graphics, HearthBuildPlanPayload.Row row, BuildPlan.Row sum, int x, int y,
            int width, int mx, int my) {
        boolean hovered = mx >= x && mx < x + width && my >= y && my < y + ROW_H - 2;
        boolean isOpen = row.role() == open;
        if (hovered || isOpen) {
            graphics.fill(x - 3, y, x + width + 3, y + ROW_H - 2, 0x22FFFFFF);
        }
        ItemStack icon = icon(row.chosen());
        long clock = System.currentTimeMillis();
        Object rowKey = "row:" + row.role();
        click.begin(graphics, rowKey, x, y + 3, 16, 16, clock);
        if (icon.isEmpty()) {
            graphics.fill(x, y + 3, x + 16, y + 19, SkillPalette.TRACK);
        } else {
            graphics.renderItem(icon, x, y + 3);
        }
        click.end(graphics, rowKey, x, y + 3, 16, 16, clock);
        int textX = x + 22;
        int textW = width - 22 - 12;
        MutableComponent name = capital(StageText.role(row.role()).getString());
        Component chosen = row.chosen().isEmpty() ? Component.translatable("proficiency.hearth.client.plan.none")
                : name(row.chosen());
        String head = name.getString() + " · " + chosen.getString();
        HudKit.draw(graphics, this.font, "hearth.plan.row", head, textX, y + 2, textW, SkillPalette.TEXT, false);
        Component counts = sum.missing() == 0
                ? Component.translatable("proficiency.hearth.client.plan.counts", sum.need(), sum.have())
                : Component.translatable("proficiency.hearth.client.plan.counts_missing", sum.need(), sum.have(), sum.missing());
        graphics.drawString(this.font, HudKit.clip(this.font, counts.getString(), textW), textX, y + 13,
                sum.missing() == 0 ? OK : HearthPalette.SIEGE, false);
        if (!HearthDesigns.isFluid(row.role())) {
            graphics.drawString(this.font, isOpen ? "▲" : "▼", x + width - 8, y + 8, SkillPalette.TEXT_DIM, false);
        }
        hits.add(new Hit(x, y, width, ROW_H - 2, row.role(), null, HearthDesigns.isFluid(row.role()) ? null
                : () -> List.of(Component.translatable("proficiency.hearth.client.plan.pick")
                        .withStyle(ChatFormatting.GRAY))));
        y += ROW_H;
        if (!isOpen) {
            return y;
        }
        if (row.options().isEmpty()) {
            graphics.drawString(this.font, Component.translatable("proficiency.hearth.client.plan.empty"), textX, y,
                    HearthPalette.LOCKED, false);
            return y + LINE + 6;
        }
        int perLine = Math.max(1, (width - 24) / CELL);
        for (int i = 0; i < row.options().size(); i++) {
            HearthBuildPlanPayload.Option option = row.options().get(i);
            int cx = textX + (i % perLine) * CELL;
            int cy = y + (i / perLine) * CELL;
            boolean selected = option.block().equals(row.chosen());
            boolean over = mx >= cx && mx < cx + CELL - 2 && my >= cy && my < cy + CELL - 2;
            Object optKey = "opt:" + row.role() + ":" + option.block();
            click.begin(graphics, optKey, cx, cy, CELL - 2, CELL - 2, clock);
            graphics.fill(cx, cy, cx + CELL - 2, cy + CELL - 2, over ? 0x44FFFFFF : SkillPalette.TRACK);
            if (selected) {
                border(graphics, cx - 1, cy - 1, CELL, CELL, HearthPalette.EMBER);
            }
            graphics.renderItem(icon(option.block()), cx + 1, cy + 1);
            graphics.renderItemDecorations(this.font, icon(option.block()), cx + 1, cy + 1, shortCount(option.count()));
            click.end(graphics, optKey, cx, cy, CELL - 2, CELL - 2, clock);
            hits.add(new Hit(cx, cy, CELL - 2, CELL - 2, row.role(), option.block(),
                    () -> optionTip(row, option, selected)));
        }
        int lines = (row.options().size() + perLine - 1) / perLine;
        return y + lines * CELL + 6;
    }

    private List<Component> optionTip(HearthBuildPlanPayload.Row row, HearthBuildPlanPayload.Option option,
            boolean selected) {
        List<Component> lines = new ArrayList<>();
        lines.add(name(option.block()).copy().withStyle(selected ? ChatFormatting.GOLD : ChatFormatting.WHITE));
        lines.add(Component.translatable("proficiency.hearth.client.plan.on_hand", option.count())
                .withStyle(ChatFormatting.GRAY));
        for (HearthBuildPlanPayload.Where where : option.where()) {
            if (where.pack()) {
                lines.add(Component.translatable("proficiency.hearth.client.plan.where_pack", where.count())
                        .withStyle(ChatFormatting.DARK_GRAY));
            } else {
                lines.add(Component.translatable("proficiency.hearth.client.plan.where", container(where),
                        StageText.where(where.dx(), where.dy(), where.dz()), where.count()).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        if (!HearthDesigns.isFluid(row.role())) {
            lines.add(Component.translatable(selected ? "proficiency.hearth.client.plan.chosen"
                    : "proficiency.hearth.client.plan.choose").withStyle(selected ? ChatFormatting.GOLD : ChatFormatting.YELLOW));
        }
        return lines;
    }

    /** The container's own name ("Chest", "Barrel"), read from the client's world. */
    private Component container(HearthBuildPlanPayload.Where where) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            BlockPos at = BlockPos.of(plan.pos()).offset(where.dx(), where.dy(), where.dz());
            if (minecraft.level.isLoaded(at)) {
                return minecraft.level.getBlockState(at).getBlock().getName();
            }
        }
        return Component.translatable("proficiency.hearth.client.plan.storage");
    }

    // ---- Input ------------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            click.onClick(this, mouseX, mouseY, HearthPalette.EMBER, startButton);
        }
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0 || mouseY < bodyTop || mouseY >= bodyTop + viewportH) {
            return false;
        }
        double my = mouseY + scroll;
        for (Hit hit : hits) {
            if (!hit.contains(mouseX, my)) {
                continue;
            }
            long clock = System.currentTimeMillis();
            int screenY = hit.y() - scroll;
            if (hit.block() != null) {
                if (!HearthDesigns.isFluid(hit.role())) {
                    boolean fresh = chosenBlock(hit.role()) == null || !chosenBlock(hit.role()).equals(hit.block());
                    choose(hit.role(), hit.block());
                    // A new pick bursts out of its cell; re-picking the chosen one only blips.
                    click.success("opt:" + hit.role() + ":" + hit.block(), hit.x(), screenY, hit.w(), hit.h(),
                            HearthPalette.EMBER, fresh ? 1 : 0, 0.7f, clock);
                }
            } else if (!HearthDesigns.isFluid(hit.role())) {
                open = open == hit.role() ? 0 : hit.role();
                if (ClickFx.on()) {
                    click.press("row:" + hit.role(), hit.x(), screenY + 3, 16, 16, HearthPalette.EMBER, clock);
                } else {
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                }
                rebuildWidgets();
            } else {
                // Fluids are fixed by the design: nothing to pick, and the icon says so.
                click.no("row:" + hit.role(), hit.x(), screenY + 3, 16, 16, clock);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (bodyH > viewportH) {
            scroll = Math.max(0, Math.min(bodyH - viewportH, scroll - (int) (scrollY * ROW_H)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollY);
    }

    // ---- Helpers ----------------------------------------------------------------------------------------

    /** What is on hand per row, the stock split between rows that take the same block. */
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

    private static MutableComponent capital(String text) {
        return Component.literal(text.isEmpty() ? text : text.substring(0, 1).toUpperCase(java.util.Locale.ROOT)
                + text.substring(1));
    }

    private static String shortCount(int count) {
        return count >= 1000 ? (count / 1000) + "k" : String.valueOf(count);
    }

    private static void border(GuiGraphics graphics, int x, int y, int w, int h, int colour) {
        graphics.fill(x, y, x + w, y + 1, colour);
        graphics.fill(x, y + h - 1, x + w, y + h, colour);
        graphics.fill(x, y, x + 1, y + h, colour);
        graphics.fill(x + w - 1, y, x + w, y + h, colour);
    }
}
