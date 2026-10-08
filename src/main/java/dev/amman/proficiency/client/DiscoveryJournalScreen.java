package dev.amman.proficiency.client;

import dev.amman.proficiency.skill.JournalData;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Every biome, structure and dimension found, and the first-time kinds per skill. "Found x of N"
 * counts against what the game has (biomes from the client's registry, structures and dimensions
 * from the server's lists); an unfound entry is "???", so the list is a checklist and not a spoiler.
 * Same panel look as {@link SkillsScreen}.
 */
public final class DiscoveryJournalScreen extends Screen {

    private enum Tab {
        BIOMES("biome:", SkillCategory.GATHERING),
        STRUCTURES("structure:", SkillCategory.EXPEDITION),
        DIMENSIONS("dim:", SkillCategory.MOVEMENT),
        SKILLS("first:", SkillCategory.MASTERY);

        final String prefix;
        final SkillCategory category;

        Tab(String prefix, SkillCategory category) {
            this.prefix = prefix;
            this.category = category;
        }

        String key() {
            return "proficiency.journal.tab." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private static final int PANEL_WIDTH = 340;
    private static final int PANEL_MAX_HEIGHT = 320;
    private static final int PADDING = 10;
    private static final int TAB_HEIGHT = 16;
    private static final int TAB_GAP = 3;
    private static final int LINE = 11;
    private static final int BUTTON_HEIGHT = 16;
    private static final int BAR_HEIGHT = 4;

    /** One drawn line of the scrolling list. {@code right} is the count on a header, or "". */
    private record Line(String text, String right, int colour, boolean header) {
    }

    private final Screen parent;
    private final List<Line> lines = new ArrayList<>();
    private final List<Button> tabButtons = new ArrayList<>();
    private Button backButton;
    private Tab tab = Tab.BIOMES;
    private int found;
    private int total;
    private int scroll;

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int viewTop;
    private int viewHeight;
    private int tabsHeight = TAB_HEIGHT;
    private final TextFit.Hovers hovers = new TextFit.Hovers();

    public DiscoveryJournalScreen(Screen parent) {
        super(Component.translatable("proficiency.journal.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(PANEL_WIDTH, this.width - 8);
        panelHeight = Math.min(PANEL_MAX_HEIGHT, this.height - 8);
        panelX = (this.width - panelWidth) / 2;
        panelY = (this.height - panelHeight) / 2;

        int tabsTop = panelY + PADDING + 14;
        // Four tabs of equal width when every label fits one; else widths by label; else two rows.
        int inner = panelWidth - PADDING * 2;
        int[] want = new int[4];
        int sum = 0;
        int widest = 0;
        for (Tab each : Tab.values()) {
            want[each.ordinal()] = this.font.width(Component.translatable(each.key())) + 12;
            sum += want[each.ordinal()];
            widest = Math.max(widest, want[each.ordinal()]);
        }
        int equal = (inner - TAB_GAP * 3) / 4;
        boolean twoRows = widest > equal && sum + TAB_GAP * 3 > inner;
        tabsHeight = twoRows ? TAB_HEIGHT * 2 + TAB_GAP : TAB_HEIGHT;
        tabButtons.clear();
        int cursor = panelX + PADDING;
        for (Tab each : Tab.values()) {
            int i = each.ordinal();
            int x;
            int y = tabsTop;
            int w;
            if (twoRows) {
                w = (inner - TAB_GAP) / 2;
                x = panelX + PADDING + (i % 2) * (w + TAB_GAP);
                y = tabsTop + (i / 2) * (TAB_HEIGHT + TAB_GAP);
            } else if (widest > equal) {
                // Spread the spare room evenly over the labels.
                w = want[i] + (inner - TAB_GAP * 3 - sum) / 4;
                x = cursor;
                cursor += w + TAB_GAP;
            } else {
                w = equal;
                x = panelX + PADDING + i * (equal + TAB_GAP);
            }
            Button button = addWidget(Button.builder(Component.translatable(each.key()), b -> select(each))
                    .bounds(x, y, w, TAB_HEIGHT).build());
            tabButtons.add(button);
        }
        if (widest > equal) {
            TextFit.note(twoRows ? "journal.tabs_two_rows" : "journal.tabs_by_label");
        }
        // Summary line and bar sit under the tabs; the list takes what is left above Back.
        viewTop = tabsTop + tabsHeight + 24;
        int backTop = panelY + panelHeight - PADDING - BUTTON_HEIGHT;
        viewHeight = Math.max(LINE * 3, backTop - 4 - viewTop);
        int backWidth = Math.max(80, this.font.width(Component.translatable("proficiency.journal.back")) + 16);
        backButton = addWidget(Button.builder(Component.translatable("proficiency.journal.back"),
                        b -> onClose())
                .bounds(panelX + (panelWidth - backWidth) / 2, backTop, backWidth, BUTTON_HEIGHT).build());
        rebuild();
    }

    private void select(Tab next) {
        tab = next;
        scroll = 0;
        rebuild();
    }

    // ---- Data ---------------------------------------------------------------------------------

    private void rebuild() {
        lines.clear();
        Set<String> visited = ClientVisited.visited();
        int accent = SkillPalette.accent(tab.category);
        if (tab == Tab.SKILLS) {
            buildSkills(visited, accent);
            return;
        }
        String kind = tab == Tab.BIOMES ? "biome" : tab == Tab.STRUCTURES ? "structure" : "dimension";
        UnaryOperator<String> canon = tab == Tab.STRUCTURES ? JournalData.structureCanon() : id -> id;
        Function<String, String> name = id -> {
            ResourceLocation parsed = ResourceLocation.tryParse(id);
            return parsed == null ? id
                    : DiscoveryBanner.name(parsed.toLanguageKey(kind), id).getString();
        };
        JournalData.Tab data = JournalData.tab(universe(), JournalData.withPrefix(visited, tab.prefix),
                canon, name);
        found = data.found();
        total = data.total();
        boolean grouped = data.groups().size() > 1;
        for (JournalData.Group group : data.groups()) {
            if (grouped) {
                lines.add(new Line(namespaceName(group.namespace()), group.found() + "/" + group.entries().size(),
                        accent, true));
            }
            for (JournalData.Entry entry : group.entries()) {
                lines.add(entry.found()
                        ? new Line(entry.label(), "", SkillPalette.TEXT, false)
                        : new Line(Component.translatable("proficiency.journal.unknown").getString(), "",
                                SkillPalette.TEXT_DIM, false));
            }
        }
    }

    private void buildSkills(Set<String> visited, int accent) {
        Map<String, List<String>> kinds = JournalData.firstKinds(visited);
        found = 0;
        total = 0;
        boolean any = false;
        int textWidth = panelWidth - PADDING * 2 - 6;
        for (Skill skill : Skill.VALUES) {
            List<String> names = kinds.get(skill.id());
            if (names == null || names.isEmpty()) {
                continue;
            }
            any = true;
            found += names.size();
            lines.add(new Line(Component.translatable(skill.translationKey()).getString(),
                    String.valueOf(names.size()), SkillPalette.accent(skill.category()), true));
            List<String> labels = new ArrayList<>();
            for (String kind : names) {
                labels.add(TalentTreeScreen.sourceName(kind));
            }
            labels.sort(String.CASE_INSENSITIVE_ORDER);
            for (var piece : this.font.split(FormattedText.of(String.join(", ", labels)), textWidth)) {
                lines.add(new Line(toPlain(piece), "", SkillPalette.TEXT, false));
            }
        }
        if (!any) {
            lines.add(new Line(Component.translatable("proficiency.journal.skills.none").getString(), "",
                    SkillPalette.TEXT_DIM, false));
        }
    }

    private static String toPlain(net.minecraft.util.FormattedCharSequence sequence) {
        StringBuilder out = new StringBuilder();
        sequence.accept((index, style, codePoint) -> {
            out.appendCodePoint(codePoint);
            return true;
        });
        return out.toString();
    }

    /** What the game has, for the current tab. Biomes from this client, the rest from the server. */
    private List<String> universe() {
        var minecraft = this.minecraft;
        if (minecraft == null || minecraft.level == null) {
            return List.of();
        }
        var access = minecraft.level.registryAccess();
        switch (tab) {
            case BIOMES -> {
                return access.registryOrThrow(Registries.BIOME).keySet().stream()
                        .map(ResourceLocation::toString).toList();
            }
            case STRUCTURES -> {
                if (!ClientVisited.structures().isEmpty()) {
                    return ClientVisited.structures();
                }
                // No list from the server (no channel). The client seldom has this registry, so
                // this is usually empty and the count is then just what was found.
                return access.registry(Registries.STRUCTURE)
                        .map(registry -> registry.keySet().stream().map(ResourceLocation::toString).toList())
                        .orElse(List.of());
            }
            default -> {
                if (!ClientVisited.dimensions().isEmpty()) {
                    return ClientVisited.dimensions();
                }
                ClientPacketListener connection = minecraft.getConnection();
                Set<String> out = new LinkedHashSet<>();
                if (connection != null) {
                    connection.levels().forEach(key -> out.add(key.location().toString()));
                }
                return List.copyOf(out);
            }
        }
    }

    private static String namespaceName(String namespace) {
        if (namespace.equals("minecraft")) {
            return "Minecraft";
        }
        return ModList.get().getModContainerById(namespace)
                .map(container -> container.getModInfo().getDisplayName())
                .orElse(namespace);
    }

    // ---- Drawing ------------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // No super.render: it paints the widgets, and the panel has to go under them.
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, SkillPalette.PANEL);
        border(graphics, panelX, panelY, panelWidth, panelHeight, SkillPalette.PANEL_BORDER);
        hovers.clear();
        String titleText = TextFit.clip(this.font, this.title.getString(), panelWidth - PADDING * 2);
        graphics.drawString(this.font, titleText, panelX + (panelWidth - this.font.width(titleText)) / 2,
                panelY + PADDING, SkillPalette.TEXT, false);

        int accent = SkillPalette.accent(tab.category);
        for (int i = 0; i < tabButtons.size(); i++) {
            Button button = tabButtons.get(i);
            button.render(graphics, mouseX, mouseY, partialTick);
            if (i == tab.ordinal()) {
                graphics.fill(button.getX(), button.getY() + button.getHeight(),
                        button.getX() + button.getWidth(), button.getY() + button.getHeight() + 2, accent);
            }
        }
        backButton.render(graphics, mouseX, mouseY, partialTick);

        drawSummary(graphics, accent);
        drawList(graphics);
        if (mouseY >= viewTop && mouseY < viewTop + viewHeight) {
            List<Component> full = hovers.at(mouseX, mouseY);
            if (full != null) {
                TextFit.tooltip(graphics, this.font, full, mouseX, mouseY, Math.min(300, this.width - 16));
            }
        }
    }

    private void drawSummary(GuiGraphics graphics, int accent) {
        int left = panelX + PADDING;
        int width = panelWidth - PADDING * 2;
        int top = viewTop - 19;
        Component text;
        if (tab == Tab.SKILLS) {
            text = Component.translatable("proficiency.journal.skills.summary", found);
            TextFit.draw(graphics, this.font, "journal.summary", text.getString(), left, top, width,
                    SkillPalette.TEXT, false);
            return;
        }
        boolean complete = total > 0 && found >= total;
        text = Component.translatable("proficiency.journal.found", found, total);
        TextFit.draw(graphics, this.font, "journal.summary", text.getString(), left, top, width,
                complete ? SkillPalette.MAXED : SkillPalette.TEXT, false);
        int barTop = top + 11;
        graphics.fill(left, barTop, left + width, barTop + BAR_HEIGHT, SkillPalette.TRACK);
        int filled = total <= 0 ? 0 : Math.round(width * Math.min(1f, found / (float) total));
        if (found > 0 && filled < 1) {
            filled = 1;
        }
        if (filled > 0) {
            graphics.fill(left, barTop, left + filled, barTop + BAR_HEIGHT,
                    complete ? SkillPalette.MAXED : accent);
        }
    }

    private void drawList(GuiGraphics graphics) {
        int left = panelX + PADDING;
        int width = panelWidth - PADDING * 2 - 6;
        graphics.fill(left, viewTop - 3, left + panelWidth - PADDING * 2, viewTop - 2, SkillPalette.PANEL_BORDER);
        graphics.enableScissor(panelX, viewTop, panelX + panelWidth, viewTop + viewHeight);
        int y = viewTop - scroll;
        for (Line line : lines) {
            if (y + LINE > viewTop && y < viewTop + viewHeight) {
                int rightWidth = line.right().isEmpty() ? 0 : this.font.width(line.right()) + 4;
                int room = width - rightWidth - (line.header() ? 0 : 4);
                String shown = clip(line.text(), room);
                graphics.drawString(this.font, shown, left + (line.header() ? 0 : 4), y, line.colour(), false);
                if (!shown.equals(line.text())) {
                    TextFit.note("journal.row");
                    hovers.add(left, y, room, LINE, Component.literal(line.text()));
                }
                if (!line.right().isEmpty()) {
                    graphics.drawString(this.font, line.right(), left + width - this.font.width(line.right()),
                            y, SkillPalette.TEXT_DIM, false);
                }
            }
            y += LINE;
        }
        graphics.disableScissor();
        int content = lines.size() * LINE;
        if (content > viewHeight) {
            int thumb = Math.max(8, viewHeight * viewHeight / content);
            int travel = viewHeight - thumb;
            int thumbTop = viewTop + (int) (travel * (scroll / (float) (content - viewHeight)));
            graphics.fill(panelX + panelWidth - 3, thumbTop, panelX + panelWidth - 1, thumbTop + thumb,
                    SkillPalette.TEXT_DIM);
        }
    }

    /** Cuts to fit, with an ellipsis, so a long modded name cannot run into the count. */
    private String clip(String text, int width) {
        return TextFit.clip(this.font, text, width);
    }


    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int max = Math.max(0, lines.size() * LINE - viewHeight);
        if (max > 0) {
            scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(scrollY) * LINE * 3));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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

    private static void border(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }
}
