package dev.amman.proficiency.client;

import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.net.UnlockPerkPayload;
import dev.amman.proficiency.perk.PerkEffect;
import dev.amman.proficiency.perk.Requirement;
import dev.amman.proficiency.perk.Synergies;
import dev.amman.proficiency.perk.Synergy;
import dev.amman.proficiency.perk.Talent;
import dev.amman.proficiency.perk.TalentService;
import dev.amman.proficiency.perk.Talents;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.XpFactors;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import dev.amman.proficiency.platform.net.ClientPacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One skill's talent tree, drawn as a grid of nodes joined by elbow lines, with the synergies that
 * touch this tree down the right-hand side. Click a node to put a rank in; shift-click to fill it.
 * The server re-checks every click, so everything drawn here is only a forecast.
 */
public final class TalentTreeScreen extends Screen {

    private static final int PADDING = 10;
    /** Title, summary, the signature move, and the "your numbers" line. */
    private static final int HEADER = 39;
    private static final int FOOTER = 14;
    private static final int NODE = 24;
    private static final int COL_STEP = 46;
    private static final int ROW_STEP = 34;
    private static final int COLUMNS = 5;
    private static final int ROWS = 6;
    private static final int SIDE_GAP = 14;
    private static final int SIDE_WIDTH = 160;
    private static final int SYNERGY_ROW = 12;
    private static final int LOG_ROW = 10;
    /** Gap between the last synergy and the recent-XP header. */
    private static final int LOG_GAP = 6;
    /** A log line is drawn at full strength this long, then fades to {@link #LOG_FADED_ALPHA}. */
    private static final long LOG_FRESH_MILLIS = 10_000L;
    private static final long LOG_FADE_MILLIS = 50_000L;
    private static final int LOG_FADED_ALPHA = 0x70;

    private static final int GRID_WIDTH = (COLUMNS - 1) * COL_STEP + NODE;
    private static final int GRID_HEIGHT = (ROWS - 1) * ROW_STEP + NODE;
    private static final int PANEL_WIDTH = PADDING * 2 + GRID_WIDTH + SIDE_GAP + SIDE_WIDTH;
    private static final int PANEL_HEIGHT = PADDING + HEADER + GRID_HEIGHT + FOOTER + PADDING;

    private final Skill skill;
    @Nullable
    private final Screen parent;
    private final List<Synergy> synergies;

    private int panelX;
    private int panelY;
    /** Where the synergy list ended on the last frame; the recent-XP list starts below it. */
    private int synergiesBottom;

    public TalentTreeScreen(Skill skill, @Nullable Screen parent) {
        super(Component.translatable("proficiency.tree.title",
                Component.translatable(skill.translationKey())));
        this.skill = skill;
        this.parent = parent;
        this.synergies = Synergies.involving(skill);
    }

    @Override
    protected void init() {
        panelX = (this.width - PANEL_WIDTH) / 2;
        panelY = Math.max(4, (this.height - PANEL_HEIGHT) / 2);
    }

    private int nodeX(Talent talent) {
        return panelX + PADDING + talent.col() * COL_STEP;
    }

    private int nodeY(Talent talent) {
        return panelY + PADDING + HEADER + talent.row() * ROW_STEP;
    }

    private int accent() {
        return SkillPalette.accent(skill.category());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, SkillPalette.PANEL);
        border(graphics, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, SkillPalette.PANEL_BORDER);

        PlayerSkills skills = playerSkills();
        if (skills == null) {
            return;
        }

        graphics.drawString(this.font, this.title, panelX + PADDING, panelY + PADDING, accent(), false);
        Component summary = Component.translatable("proficiency.tree.summary",
                skills.level(skill), skills.pointsAvailable(skill),
                skills.pointsSpent(skill), Talents.fullTreeCost(skill));
        // The header lines share the top rows with the synergy column, so they stop at the grid.
        int headerLimit = panelX + PADDING + GRID_WIDTH;
        drawClipped(graphics, summary.getString(), panelX + PADDING, panelY + PADDING + 10,
                headerLimit, SkillPalette.TEXT_DIM);
        // The signature move, named and explained. Cut to the grid; hover for the whole line.
        int procY = panelY + PADDING + 19;
        int procWidth = GRID_WIDTH;
        Component procLine = Component.translatable("proficiency.tree.proc",
                Component.translatable(skill.procKey()),
                Component.translatable(skill.procDescKey()));
        drawClipped(graphics, procLine.getString(), panelX + PADDING, procY, headerLimit, accent());
        boolean procHovered = inside(mouseX, mouseY, panelX + PADDING, procY, procWidth, 9);
        // Your numbers now: passive, proc chance and power, ability. Hover for where they come from.
        int statsY = panelY + PADDING + 28;
        drawClipped(graphics, SkillNumbers.line(skills, skill).getString(), panelX + PADDING, statsY,
                headerLimit, SkillPalette.TEXT);
        boolean statsHovered = inside(mouseX, mouseY, panelX + PADDING, statsY, procWidth, 9);

        List<Talent> tree = Talents.of(skill);
        for (Talent talent : tree) {
            for (String parentId : talent.parents()) {
                Talent from = Talents.get(skill, parentId);
                if (from != null) {
                    connector(graphics, from, talent, skills.isFull(from) ? accent() : SkillPalette.TRACK);
                }
            }
        }

        Talent hovered = null;
        for (Talent talent : tree) {
            drawNode(graphics, talent, skills);
            if (inside(mouseX, mouseY, nodeX(talent), nodeY(talent), NODE, NODE)) {
                hovered = talent;
            }
        }

        Synergy hoveredSynergy = drawSynergies(graphics, skills, mouseX, mouseY);
        ClientXpLog.Line hoveredLog = drawXpLog(graphics, synergiesBottom + LOG_GAP, mouseX, mouseY);

        graphics.drawString(this.font, Component.translatable("proficiency.tree.footer", skill.id()),
                panelX + PADDING, panelY + PANEL_HEIGHT - PADDING - 8, SkillPalette.TEXT_DIM, false);

        if (statsHovered && hovered == null) {
            // Wrapped: the passive's description is one long line.
            List<net.minecraft.util.FormattedCharSequence> wrapped = new ArrayList<>();
            for (Component line : SkillNumbers.tooltip(skills, skill)) {
                wrapped.addAll(this.font.split(line, 260));
            }
            graphics.renderTooltip(this.font, wrapped, mouseX, mouseY);
        } else if (procHovered && hovered == null) {
            graphics.renderTooltip(this.font, this.font.split(procLine, 220), mouseX, mouseY);
        } else if (hovered != null) {
            graphics.renderComponentTooltip(this.font, nodeTooltip(hovered, skills), mouseX, mouseY);
        } else if (hoveredSynergy != null) {
            graphics.renderComponentTooltip(this.font, synergyTooltip(hoveredSynergy, skills), mouseX, mouseY);
        } else if (hoveredLog != null) {
            graphics.renderComponentTooltip(this.font, logTooltip(hoveredLog), mouseX, mouseY);
        }
    }

    /** Elbow from the bottom of one node to the top of the next: down, across, down. */
    private void connector(GuiGraphics graphics, Talent from, Talent to, int colour) {
        int x1 = nodeX(from) + NODE / 2;
        int y1 = nodeY(from) + NODE;
        int x2 = nodeX(to) + NODE / 2;
        int y2 = nodeY(to);
        int mid = y2 - (ROW_STEP - NODE) / 2;
        graphics.fill(x1, y1, x1 + 1, mid + 1, colour);
        graphics.fill(Math.min(x1, x2), mid, Math.max(x1, x2) + 1, mid + 1, colour);
        graphics.fill(x2, mid, x2 + 1, y2, colour);
    }

    private void drawNode(GuiGraphics graphics, Talent talent, PlayerSkills skills) {
        int x = nodeX(talent);
        int y = nodeY(talent);
        int rank = skills.rank(talent);
        boolean full = skills.isFull(talent);
        boolean open = skills.check(talent) == TalentService.Outcome.OK;
        boolean big = talent.kind() == Talent.Kind.KEYSTONE || talent.kind() == Talent.Kind.CAPSTONE;

        int fill = full ? (accent() & 0x00FFFFFF) | 0x88000000
                : rank > 0 ? (accent() & 0x00FFFFFF) | 0x44000000
                : 0xFF181B20;
        int edge = full ? SkillPalette.MAXED
                : open || rank > 0 ? accent()
                : SkillPalette.TRACK;

        graphics.fill(x, y, x + NODE, y + NODE, fill);
        border(graphics, x, y, NODE, NODE, edge);
        if (big) {
            border(graphics, x - 2, y - 2, NODE + 4, NODE + 4, edge);
        }
        if (talent.kind() == Talent.Kind.BRIDGE) {
            // Bridges are the synergy inside a tree; a notch on each side says "joins two".
            graphics.fill(x - 3, y + NODE / 2 - 1, x, y + NODE / 2 + 1, edge);
            graphics.fill(x + NODE, y + NODE / 2 - 1, x + NODE + 3, y + NODE / 2 + 1, edge);
        }
        if (!talent.materials().isEmpty() && !skills.hasPaid(talent)) {
            // A corner pip: this node will want something from your bags when it fills.
            graphics.fill(x + NODE - 4, y + 1, x + NODE - 1, y + 4, 0xFFD2A249);
        }

        String text = talent.maxRank() == 1 ? (full ? "✔" : "◆") : rank + "/" + talent.maxRank();
        int colour = full ? SkillPalette.MAXED : open || rank > 0 ? SkillPalette.TEXT : SkillPalette.TEXT_DIM;
        graphics.drawCenteredString(this.font, text, x + NODE / 2 + 1, y + (NODE - 8) / 2, colour);
    }

    @Nullable
    private Synergy drawSynergies(GuiGraphics graphics, PlayerSkills skills, int mouseX, int mouseY) {
        int x = panelX + PADDING + GRID_WIDTH + SIDE_GAP;
        int y = panelY + PADDING;
        graphics.drawString(this.font, Component.translatable("proficiency.tree.synergies"),
                x, y, 0xFFB98BE0, false);
        y += 11;

        double discipline = Synergies.discipline(skills, skill.category());
        graphics.drawString(this.font, Component.translatable("proficiency.tree.discipline",
                        Component.translatable(skill.category().translationKey()),
                        (int) Math.round(discipline * 100)),
                x, y, discipline > 0 ? SkillPalette.TEXT : SkillPalette.TEXT_DIM, false);
        y += SYNERGY_ROW + 4;

        Synergy hovered = null;
        for (Synergy synergy : synergies) {
            boolean active = Synergies.isActive(skills, synergy);
            int met = 0;
            for (Synergy.Need need : synergy.requires()) {
                if (skills.rank(need.talent()) >= need.minRank()) {
                    met++;
                }
            }
            int total = synergy.requires().size();
            if (synergy.grandmastersNeeded() > 0) {
                met = Math.min(skills.grandmasters(), synergy.grandmastersNeeded());
                total = synergy.grandmastersNeeded();
            }
            String marker = active ? "✦ " : met + "/" + total + " ";
            graphics.drawString(this.font, Component.literal(marker).append(synergy.displayName()),
                    x, y, active ? 0xFFD9B8FF : met > 0 ? SkillPalette.TEXT : SkillPalette.TEXT_DIM, false);
            if (inside(mouseX, mouseY, x, y - 1, SIDE_WIDTH, SYNERGY_ROW)) {
                hovered = synergy;
            }
            y += SYNERGY_ROW;
        }
        synergiesBottom = y;
        return hovered;
    }

    /**
     * This skill's last few XP gains, under the synergies: "+12.0 · Iron Ore ×5" with the age on
     * the right. It is the one place that answers "did that just pay, and how much", which the bar
     * alone cannot for a skill at level 60 where a block is a sliver. Only this tree's skill is
     * listed; the hover tooltip has what does not fit on the line (see {@link #logTooltip}).
     *
     * <p>The list stops above the footer line rather than at the panel edge: the footer's text is
     * wider than the grid and runs underneath this column. Lines older than ten seconds fade, so
     * the eye lands on what just happened.
     */
    @Nullable
    private ClientXpLog.Line drawXpLog(GuiGraphics graphics, int top, int mouseX, int mouseY) {
        int x = panelX + PADDING + GRID_WIDTH + SIDE_GAP;
        int bottom = panelY + PANEL_HEIGHT - PADDING - 8 - 4;
        if (top + 11 + LOG_ROW > bottom) {
            return null;
        }
        graphics.drawString(this.font, Component.translatable("proficiency.tree.xp_log"),
                x, top, 0xFFB98BE0, false);
        int y = top + 11;

        List<ClientXpLog.Line> lines = ClientXpLog.lines(skill);
        if (lines.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("proficiency.tree.xp_log.empty"),
                    x, y, SkillPalette.TEXT_DIM, false);
            return null;
        }
        long now = System.currentTimeMillis();
        ClientXpLog.Line hovered = null;
        for (ClientXpLog.Line line : lines) {
            if (y + LOG_ROW > bottom) {
                break;
            }
            long age = Math.max(0L, now - line.at());
            int alpha = fade(age);

            String ago = ago(age);
            int agoWidth = this.font.width(ago);
            graphics.drawString(this.font, ago, x + SIDE_WIDTH - agoWidth, y,
                    withAlpha(SkillPalette.TEXT_DIM, alpha), false);

            // Amount in the skill's colour, then the source, then the count dimmed, all cut to
            // what is left once the age has its room on the right. Every line is this tree's
            // skill, so the skill's name would only take the source's room.
            int room = SIDE_WIDTH - agoWidth - 4;
            String amount = String.format(java.util.Locale.ROOT, "+%.1f", line.amount());
            String source = line.source().isEmpty() ? "" : " · " + sourceName(line.source());
            String count = line.count() > 1 ? " ×" + line.count() : "";
            int cursor = x;
            cursor = drawClipped(graphics, amount, cursor, y, x + room, withAlpha(accent(), alpha));
            cursor = drawClipped(graphics, source, cursor, y, x + room, withAlpha(SkillPalette.TEXT, alpha));
            drawClipped(graphics, count, cursor, y, x + room, withAlpha(SkillPalette.TEXT_DIM, alpha));
            if (inside(mouseX, mouseY, x, y - 1, SIDE_WIDTH, LOG_ROW)) {
                hovered = line;
            }
            y += LOG_ROW;
        }
        return hovered;
    }

    /**
     * Everything about one log line that does not fit on it: the whole source name, how many gains
     * it merges and over how long, what was asked for before multipliers against what was paid,
     * and each multiplier of the newest gain by name.
     */
    private List<Component> logTooltip(ClientXpLog.Line line) {
        List<Component> out = new java.util.ArrayList<>();
        java.util.Locale root = java.util.Locale.ROOT;
        out.add(Component.translatable("proficiency.tree.xp_log.tip.title",
                        String.format(root, "%.1f", line.amount()),
                        Component.translatable(line.skill().translationKey()))
                .withColor(accent()));
        if (!line.source().isEmpty()) {
            out.add(Component.literal(sourceName(line.source())).withColor(SkillPalette.TEXT));
        }
        long now = System.currentTimeMillis();
        String last = ago(Math.max(0L, now - line.at()));
        if (line.count() > 1) {
            out.add(Component.translatable("proficiency.tree.xp_log.tip.merged", line.count(),
                    ago(Math.max(0L, now - line.first())), last).withColor(SkillPalette.TEXT_DIM));
            out.add(Component.translatable("proficiency.tree.xp_log.tip.average",
                    String.format(root, "%.2f", line.amount() / line.count()))
                    .withColor(SkillPalette.TEXT_DIM));
        } else {
            out.add(Component.translatable("proficiency.tree.xp_log.tip.single", last)
                    .withColor(SkillPalette.TEXT_DIM));
        }
        if (line.base() > 0) {
            out.add(Component.translatable("proficiency.tree.xp_log.tip.base",
                    String.format(root, "%.1f", line.base()),
                    String.format(root, "%.1f", line.amount())).withColor(SkillPalette.TEXT_DIM));
        }
        List<XpFactors.Factor> factors = XpFactors.decode(line.factors());
        if (factors.isEmpty()) {
            out.add(Component.translatable("proficiency.tree.xp_log.tip.no_factors")
                    .withColor(SkillPalette.TEXT_DIM));
        } else {
            out.add(Component.translatable(line.count() > 1
                    ? "proficiency.tree.xp_log.tip.factors_newest"
                    : "proficiency.tree.xp_log.tip.factors").withColor(0xFFB98BE0));
            for (XpFactors.Factor factor : factors) {
                out.add(Component.literal("  ")
                        .append(Component.translatable("proficiency.xpfeed.factor." + factor.id()))
                        .append(String.format(root, " ×%.2f", factor.value()))
                        .withColor(factor.value() >= 1f ? 0xFF8FD18F : 0xFFE08F8F));
            }
        }
        return out;
    }

    /**
     * Draws as much of {@code text} as fits before {@code limit}, with an ellipsis when it had to
     * cut, and returns where the next piece starts.
     */
    private int drawClipped(GuiGraphics graphics, String text, int x, int y, int limit, int colour) {
        if (text.isEmpty() || x >= limit) {
            return x;
        }
        int room = limit - x;
        String shown = text;
        if (this.font.width(text) > room) {
            int ellipsis = this.font.width("…");
            shown = room > ellipsis ? this.font.plainSubstrByWidth(text, room - ellipsis) + "…" : "";
        }
        graphics.drawString(this.font, shown, x, y, colour, false);
        return x + this.font.width(shown);
    }

    /**
     * A source is a translation key. Blocks, entities and biomes have their own; the damage types
     * and movement lines are this mod's. Anything unknown still reads as words: the last part of
     * the key, underscores to spaces, first letter up.
     */
    static String sourceName(String key) {
        // The first-time bonus line: "first|<kind key>", shown as "First time · Iron Ore".
        if (key.startsWith("first|")) {
            return Component.translatable("proficiency.xplog.source.first_time",
                    sourceName(key.substring("first|".length()))).getString();
        }
        String tail = key.substring(key.lastIndexOf('.') + 1).replace('_', ' ');
        String fallback = tail.isEmpty() ? key
                : Character.toUpperCase(tail.charAt(0)) + tail.substring(1);
        return Component.translatableWithFallback(key, fallback).getString();
    }

    private static String ago(long millis) {
        long seconds = millis / 1000L;
        if (seconds < 1) {
            return Component.translatable("proficiency.tree.xp_log.now").getString();
        }
        if (seconds < 60) {
            return Component.translatable("proficiency.tree.xp_log.seconds", seconds).getString();
        }
        if (seconds < 3600) {
            return Component.translatable("proficiency.tree.xp_log.minutes", seconds / 60).getString();
        }
        return Component.translatable("proficiency.tree.xp_log.hours", seconds / 3600).getString();
    }

    /** Full strength while fresh, then a straight fade down to a floor that stays readable. */
    private static int fade(long age) {
        if (age <= LOG_FRESH_MILLIS) {
            return 0xFF;
        }
        double t = Math.min(1.0, (age - LOG_FRESH_MILLIS) / (double) LOG_FADE_MILLIS);
        return (int) Math.round(0xFF - (0xFF - LOG_FADED_ALPHA) * t);
    }

    private static int withAlpha(int colour, int alpha) {
        return (colour & 0x00FFFFFF) | (alpha << 24);
    }

    private List<Component> nodeTooltip(Talent talent, PlayerSkills skills) {
        List<Component> lines = new ArrayList<>();
        int rank = skills.rank(talent);
        lines.add(talent.displayName().copy().withStyle(style -> style.withColor(accent())));
        lines.add(Component.translatable("proficiency.talent.kind." + talent.kind().name().toLowerCase())
                .withStyle(ChatFormatting.DARK_GRAY));
        lines.add(Component.translatable(talent.descriptionKey()).withStyle(ChatFormatting.GRAY));

        for (Map.Entry<PerkEffect, Double> effect : talent.perRank().entrySet()) {
            String per = signedPercent(effect.getValue());
            Component text = Component.translatable(effect.getKey().translationKey(), per);
            lines.add(talent.maxRank() == 1
                    ? text.copy().withStyle(ChatFormatting.DARK_AQUA)
                    : Component.translatable("proficiency.tree.per_rank", text,
                            signedPercent(effect.getValue() * rank)).withStyle(ChatFormatting.DARK_AQUA));
        }

        lines.add(Component.translatable("proficiency.tree.rank", rank, talent.maxRank(),
                talent.costPerRank(), talent.requiredLevel()).withStyle(ChatFormatting.GRAY));

        if (!talent.materials().isEmpty()) {
            if (skills.hasPaid(talent)) {
                lines.add(Component.translatable("proficiency.tree.paid").withStyle(ChatFormatting.DARK_GREEN));
            } else {
                lines.add(Component.translatable("proficiency.tree.materials").withStyle(ChatFormatting.GOLD));
                Player player = this.minecraft == null ? null : this.minecraft.player;
                for (Requirement requirement : TalentService.payableMaterials(talent)) {
                    int held = player == null ? 0 : requirement.countIn(player.getInventory());
                    lines.add(Component.translatable("proficiency.perk.missing_line",
                                    requirement.displayName(), Math.min(held, requirement.count()),
                                    requirement.count())
                            .withStyle(held >= requirement.count() ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
                }
            }
        }

        TalentService.Outcome outcome = skills.check(talent);
        if (outcome == TalentService.Outcome.OK) {
            lines.add(Component.translatable("proficiency.tree.click").withStyle(ChatFormatting.YELLOW));
        } else {
            lines.add(TalentService.explain(skills, talent, outcome).copy()
                    .withStyle(outcome == TalentService.Outcome.MAXED ? ChatFormatting.GOLD : ChatFormatting.RED));
        }

        // Which synergies elsewhere this node feeds, so the tree shows where it leads.
        for (Synergy synergy : Synergies.all()) {
            for (Synergy.Need need : synergy.requires()) {
                if (need.skill() == skill && need.talentId().equals(talent.id())) {
                    lines.add(Component.translatable("proficiency.tree.feeds", synergy.displayName(),
                            need.minRank()).withStyle(ChatFormatting.DARK_PURPLE));
                }
            }
        }
        return lines;
    }

    private List<Component> synergyTooltip(Synergy synergy, PlayerSkills skills) {
        List<Component> lines = new ArrayList<>();
        boolean active = Synergies.isActive(skills, synergy);
        lines.add(synergy.displayName().copy().withStyle(active ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.WHITE));
        lines.add(Component.translatable(synergy.descriptionKey()).withStyle(ChatFormatting.GRAY));
        for (Synergy.Need need : synergy.requires()) {
            int rank = skills.rank(need.talent());
            lines.add(Component.translatable("proficiency.synergy.need",
                            Component.translatable(need.skill().translationKey()), need.talent().displayName(),
                            Math.min(rank, need.minRank()), need.minRank())
                    .withStyle(rank >= need.minRank() ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
        }
        if (synergy.grandmastersNeeded() > 0) {
            lines.add(Component.translatable("proficiency.synergy.need_grandmasters",
                    Math.min(skills.grandmasters(), synergy.grandmastersNeeded()),
                    synergy.grandmastersNeeded()).withStyle(ChatFormatting.DARK_GRAY));
        }
        for (Synergy.Grant grant : synergy.grants()) {
            Component target = grant.skill() == null
                    ? Component.translatable("proficiency.synergy.every_skill")
                    : Component.translatable(grant.skill().translationKey());
            lines.add(Component.translatable("proficiency.synergy.grant", target,
                            Component.translatable(grant.effect().translationKey(),
                                    signedPercent(grant.multiplier() - 1.0)))
                    .withStyle(ChatFormatting.DARK_AQUA));
        }
        return lines;
    }

    private static String signedPercent(double fraction) {
        long percent = Math.round(fraction * 100);
        return (percent >= 0 ? "+" : "") + percent + "%";
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        PlayerSkills skills = playerSkills();
        if (button == 0 && skills != null) {
            for (Talent talent : Talents.of(skill)) {
                if (!inside(mouseX, mouseY, nodeX(talent), nodeY(talent), NODE, NODE)) {
                    continue;
                }
                // Shift fills the node in one go; the server takes each rank separately and stops
                // at the first one it refuses.
                int clicks = hasShiftDown() ? talent.maxRank() - skills.rank(talent) : 1;
                for (int i = 0; i < Math.max(1, clicks); i++) {
                    ClientPacketDistributor.sendToServer(new UnlockPerkPayload(talent.key()));
                }
                if (this.minecraft != null) {
                    this.minecraft.getSoundManager().play(
                            SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private static void border(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    @Nullable
    private PlayerSkills playerSkills() {
        Player player = this.minecraft == null ? null : this.minecraft.player;
        return player == null ? null : ProficiencyAttachments.of(player);
    }
}
