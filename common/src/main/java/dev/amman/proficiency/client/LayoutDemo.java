package dev.amman.proficiency.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.amman.proficiency.net.DeathRecapPayload;
import dev.amman.proficiency.net.DiscoveryPayload;
import dev.amman.proficiency.net.XpFeedPayload;
import dev.amman.proficiency.net.XpLogPayload;
import dev.amman.proficiency.perk.Talent;
import dev.amman.proficiency.perk.Talents;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.Tempo;
import dev.amman.proficiency.skill.SkillService;
import dev.amman.proficiency.skill.XpFactors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Developer tool for the language layout check, off unless {@code PROFICIENCY_LAYOUT_AUDIT=<dir>}
 * is set and {@code PROFICIENCY_LAYOUT_DEMO} names the languages ("all" for the twelve shipped).
 * Once in a world it walks every language and window setup through every screen that draws text
 * with limited room, saves a screenshot of each to {@code <dir>/shots/} and logs, per scene, which
 * places had to clip or wrap ({@link TextFit#clips}).
 *
 * <p>Needs a creative or cheats-on single-player world: it sets skill levels with commands.
 */
final class LayoutDemo {

    private static final String LANGS = System.getenv("PROFICIENCY_LAYOUT_DEMO");
    private static final String[] ALL = {"en_us", "pl_pl", "zh_cn", "ru_ru", "pt_br", "es_es", "de_de",
            "fr_fr", "ja_jp", "ko_kr", "tr_tr", "uk_ua"};
    /** Window width, height and GUI scale (0 = auto). */
    private static final int[][] SETUPS = {{1920, 1080, 4}, {1280, 720, 0}, {1280, 800, 0}, {1920, 1080, 2},
            {960, 720, 3}, {1280, 720, 4}, {1280, 720, -4},
            {3840, 2160, 0}, {3840, 2160, 4}, {1280, 720, 2}, {960, 540, 2}};

    private record Step(String name, Runnable run, long waitMs, BooleanSupplier until) {
    }

    /** PROFICIENCY_LAYOUT_ONLY=tree,recap limits a run to those scene groups; PROFICIENCY_LAYOUT_SETUPS=0,1 to those window setups. */
    private static final String ONLY = System.getenv("PROFICIENCY_LAYOUT_ONLY");
    private static final String SETUP_FILTER = System.getenv("PROFICIENCY_LAYOUT_SETUPS");

    private static boolean want(String group) {
        if (ONLY == null || ONLY.isEmpty()) {
            return true;
        }
        java.util.List<String> only = java.util.Arrays.asList(ONLY.split(","));
        return only.contains(group) || group.equals("stoneface") && only.contains("stone");
    }

    private static final Deque<Step> STEPS = new ArrayDeque<>();
    private static boolean started;
    private static long readyAt;
    private static BooleanSupplier until = () -> true;
    private static String lang = "";
    private static String setup = "";

    private LayoutDemo() {
    }

    static boolean enabled() {
        return LayoutAudit.active() && LANGS != null && !LANGS.isEmpty();
    }

    static void tick(Minecraft mc) {
        if (!enabled()) {
            return;
        }
        if (mc.level == null) {
            createWorld(mc);
            return;
        }
        if (mc.player == null) {
            return;
        }
        if (!started) {
            if (mc.player.tickCount < 60 || mc.screen != null) {
                return;
            }
            started = true;
            build(mc);
        }
        long now = System.currentTimeMillis();
        if (now < readyAt || !until.getAsBoolean()) {
            return;
        }
        Step step = STEPS.poll();
        if (step == null) {
            LayoutAudit.note("DONE");
            mc.stop();
            return;
        }
        try {
            step.run().run();
        } catch (RuntimeException e) {
            LayoutAudit.note("STEP FAILED " + step.name() + ": " + e);
        }
        readyAt = System.currentTimeMillis() + step.waitMs();
        until = step.until();
    }

    private static boolean creating;

    /** From the title screen, makes a fresh flat creative world with cheats on and joins it. */
    private static void createWorld(Minecraft mc) {
        if (creating || !(mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen)
                || mc.getOverlay() != null) {
            return;
        }
        creating = true;
        net.minecraft.world.level.LevelSettings settings = new net.minecraft.world.level.LevelSettings("l10n"
                + System.currentTimeMillis() / 1000, net.minecraft.world.level.GameType.CREATIVE, false,
                net.minecraft.world.Difficulty.PEACEFUL, true, new net.minecraft.world.level.GameRules(),
                net.minecraft.world.level.WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(settings.levelName(), settings,
                new net.minecraft.world.level.levelgen.WorldOptions(7L, false, false),
                access -> access.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                        .getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value()
                        .createWorldDimensions(), mc.screen);
    }

    private static void add(String name, long waitMs, Runnable run) {
        STEPS.add(new Step(name, run, waitMs, () -> true));
    }

    private static void add(String name, long waitMs, Runnable run, BooleanSupplier until) {
        STEPS.add(new Step(name, run, waitMs, until));
    }

    private static void command(Minecraft mc, String text) {
        mc.player.connection.sendCommand(text);
    }

    private static void build(Minecraft mc) {
        LayoutAudit.dumpGlyphWidths(mc.font);
        // The world: survival (so the inventory screen is the plain one), a pickaxe, varied levels.
        add("gamemode", 400, () -> command(mc, "gamemode survival @s"));
        add("give", 400, () -> command(mc, "give @s iron_pickaxe"));
        int[] levels = {100, 87, 63, 50, 41, 25, 12, 7, 0, 100, 76, 55, 33, 18, 9, 3, 64, 99, 100, 52, 45,
                30, 21, 11, 5, 1, 88, 70, 60, 50, 40, 20, 10, 80};
        for (Skill skill : Skill.VALUES) {
            int level = levels[skill.ordinal() % levels.length];
            if (skill == Skill.MINING) {
                level = 60;
            }
            final int use = level;
            add("level " + skill.id(), 90, () -> command(mc, "proficiency set @s " + skill.id() + " " + use));
        }
        add("streak", 600, () -> command(mc, "proficiency streak @s 30"));
        add("select", 300, () -> mc.player.getInventory().selected = 1);

        String[] langs = "all".equals(LANGS) ? ALL : LANGS.split(",");
        for (String code : langs) {
            add("lang " + code, 500, () -> {
                lang = code;
                mc.getLanguageManager().setSelected(code);
                mc.options.languageCode = code;
                mc.reloadResourcePacks();
            }, () -> mc.getOverlay() == null);
            add("settle", 1200, () -> {
            });
            for (int setupIndex = 0; setupIndex < SETUPS.length; setupIndex++) {
                if (SETUP_FILTER != null && !SETUP_FILTER.isEmpty()
                        && !java.util.Arrays.asList(SETUP_FILTER.split(",")).contains(Integer.toString(setupIndex))) {
                    continue;
                }
                int[] size = SETUPS[setupIndex];
                String tag = size[0] + "x" + size[1] + "-gui" + (size[2] == 0 ? "auto" : size[2] < 0 ? "forced" + -size[2] : size[2]);
                add("window " + tag, 1200, () -> {
                    setup = tag;
                    GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), size[0], size[1]);
                });
                // The framebuffer follows the window one event poll later, and the GUI scale is
                // worked out from the framebuffer, so set the scale only now.
                add("scale " + tag, 800, () -> {
                    mc.options.guiScale().set(Math.abs(size[2]));
                    mc.resizeDisplay();
                    if (size[2] < 0) {
                        // Minecraft keeps a GUI under 320x240 out of reach (1280x720 tops out at scale 3);
                        // force scale 4 there to audit the smallest slab: 320x180.
                        mc.getWindow().setGuiScale(4);
                    }
                });
                add("scale log", 100, () -> LayoutAudit.note("SETUP " + lang + " " + tag + " -> gui "
                        + mc.getWindow().getGuiScaledWidth() + "x" + mc.getWindow().getGuiScaledHeight()
                        + " scale " + mc.getWindow().getGuiScale() + " fb "
                        + mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight()));
                scenes(mc);
            }
        }
    }

    private static void scenes(Minecraft mc) {
        int mark = STEPS.size();
        String group = "";
        prune(mark, group);
        mark = STEPS.size();
        group = "records";
        // Personal records (1.37.0): the Face's Records section with and without values, the classic skill screen's
        // Records line, and the "new best" toast across its animation.
        add("records set", 100, () -> {
            ClientRecords.set(Skill.MINING, new dev.amman.proficiency.skill.PlayerRecords.Entry(312f, 41, 96.5f, 0));
            ClientRecords.set(Skill.BEASTSLAYING, new dev.amman.proficiency.skill.PlayerRecords.Entry(188f, 17, 54f, 9));
            ClientRecords.set(Skill.FISHING, new dev.amman.proficiency.skill.PlayerRecords.Entry(64.5f, 12, 19f, 6));
            ClientRecords.set(Skill.SWORDS, dev.amman.proficiency.skill.PlayerRecords.Entry.EMPTY);
        });
        add("records face", 700, () -> {
            mc.setScreen(new dev.amman.proficiency.client.stone.SkillstoneScreen(Skill.MINING));
            mouse(mc, 2, 2);
        });
        shot(mc, "records-face");
        add("records face end", 400, () -> {
            if (mc.screen instanceof dev.amman.proficiency.client.stone.SkillstoneScreen screen) {
                for (int i = 0; i < 40; i++) {
                    screen.mouseScrolled(10, 10, 0, -1);
                }
            }
        });
        shot(mc, "records-face-end");
        add("records face hover", 400, () -> {
            if (mc.screen instanceof dev.amman.proficiency.client.stone.SkillstoneScreen) {
                mouse(mc, mc.getWindow().getGuiScaledWidth() / 2 + 60, mc.getWindow().getGuiScaledHeight() / 2 + 10);
            }
        });
        shot(mc, "records-face-hover");
        add("records face beast", 700, () -> {
            mc.setScreen(new dev.amman.proficiency.client.stone.SkillstoneScreen(Skill.BEASTSLAYING));
            mouse(mc, 2, 2);
        });
        shot(mc, "records-face-beastslaying");
        add("records face none", 700, () -> {
            mc.setScreen(new dev.amman.proficiency.client.stone.SkillstoneScreen(Skill.SWORDS));
            mouse(mc, 2, 2);
        });
        shot(mc, "records-face-none");
        add("records classic", 700, () -> {
            mc.setScreen(new TalentTreeScreen(Skill.BEASTSLAYING, null));
            mouse(mc, 2, 2);
        });
        shot(mc, "records-classic");
        add("records toast fire", 60, () -> {
            mc.setScreen(null);
            RecordToasts.show(Skill.MINING, dev.amman.proficiency.skill.RecordMetric.RATE, 312f);
        });
        // A frame sheet for the GIF: 28 frames, one step each, as close together as the steps run.
        for (int frame = 0; frame < 28; frame++) {
            final String name = "records-toast-f" + String.format(java.util.Locale.ROOT, "%02d", frame);
            add("records toast frame " + frame, 30, () -> {
                Path dir = LayoutAudit.dir().resolve("shots");
                try {
                    Files.createDirectories(dir);
                    NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget());
                    image.writeToFile(dir.resolve(lang + "__" + setup + "__" + name + ".png"));
                    image.close();
                } catch (java.io.IOException e) {
                    LayoutAudit.note("SHOT FAILED " + name + " " + e);
                }
            });
        }
        add("records toast wait", 4500, () -> {
        });
        add("records toast beast", 60, () -> RecordToasts.show(Skill.BEASTSLAYING,
                dev.amman.proficiency.skill.RecordMetric.EXTRA, 9f));
        add("records toast beast wait", 1200, () -> {
        });
        shot(mc, "records-toast-beastslaying");
        add("records toast wait 2", 4500, () -> {
        });
        add("records toast long", 60, () -> RecordToasts.show(Skill.TRIDENTS,
                dev.amman.proficiency.skill.RecordMetric.GRANT, 12345f));
        add("records toast long wait", 1200, () -> {
        });
        shot(mc, "records-toast-grant");
        add("records toast wait 3", 4500, () -> {
        });
        prune(mark, group);
        mark = STEPS.size();
        group = "panel";
        // 1 skills panel, a tooltip on a row, then scrolled to the end
        add("panel", 700, () -> {
            mc.setScreen(new SkillsScreen());
        });
        add("panel hover", 500, () -> {
            if (mc.screen instanceof SkillsScreen screen) {
                int[] p = screen.rowPoint(Skill.TRIDENTS);
                mouse(mc, p[0], p[1]);
            }
        });
        shot(mc, "panel");
        add("panel scroll", 400, () -> {
            if (mc.screen instanceof SkillsScreen screen) {
                for (int i = 0; i < 40; i++) {
                    screen.mouseScrolled(10, 10, 0, -1);
                }
                mouse(mc, 2, 2);
            }
        });
        shot(mc, "panel-end");
        // The streak block held at a Hearth: the Warmth glyph, "next +n%: at the Hearth" and its tooltip.
        add("panel hearth", 700, () -> {
            dev.amman.proficiency.hearth.net.HearthClients.hud(hearthHud(true));
            mc.setScreen(new SkillsScreen());
        });
        add("panel hearth hover", 500, () -> {
            if (mc.screen instanceof SkillsScreen screen) {
                int[] p = screen.streakPoint();
                mouse(mc, p[0], p[1]);
            }
        });
        shot(mc, "panel-hearth");
        add("panel hearth off", 100, () -> dev.amman.proficiency.hearth.net.HearthClients.hud(hearthHud(false)));
        // Groups folded: the arrows, the "n skills, best N" summary on a folded header.
        add("panel folded", 700, () -> {
            SkillsScreen.foldForDemo(java.util.List.of(dev.amman.proficiency.skill.SkillCategory.GATHERING,
                    dev.amman.proficiency.skill.SkillCategory.MOVEMENT, dev.amman.proficiency.skill.SkillCategory.CRAFTING,
                    dev.amman.proficiency.skill.SkillCategory.CONSTRUCTION, dev.amman.proficiency.skill.SkillCategory.SOCIAL,
                    dev.amman.proficiency.skill.SkillCategory.SURVIVAL));
            mc.setScreen(new SkillsScreen());
            mouse(mc, 2, 2);
        });
        shot(mc, "panel-folded");
        add("panel unfold", 100, () -> SkillsScreen.foldForDemo(java.util.List.of()));

        prune(mark, group);
        mark = STEPS.size();
        group = "stone";
        // The Skillstone (ui.style = modern, 1.30.0): the opening frozen at the storyboard's times, the
        // settled dial, a hovered rune, Combat's 13 rows, a Face, a synergy chip, a refused tab, a rune click.
        for (long at : new long[] {0, 120, 220, 270, 320, 450, 520, 600, 680, 760, 830, 900, 1100}) {
            add("stone intro " + at, 350, () -> {
                dev.amman.proficiency.client.stone.SkillstoneScreen.demoTime(at);
                mc.setScreen(new dev.amman.proficiency.client.stone.SkillstoneScreen());
                mouse(mc, 2, 2);
            });
            shot(mc, "stone-intro-" + String.format(java.util.Locale.ROOT, "%04d", at));
        }
        add("stone dial", 600, () -> {
            dev.amman.proficiency.client.stone.SkillstoneScreen.demoTime(-1);
            dev.amman.proficiency.client.stone.SkillstoneScreen screen = new dev.amman.proficiency.client.stone.SkillstoneScreen();
            screen.skipIntroForDemo();
            mc.setScreen(screen);
            mouse(mc, 2, 2);
        });
        shot(mc, "stone-dial");
        add("stone rune hover", 500, () -> {
            if (mc.screen instanceof dev.amman.proficiency.client.stone.SkillstoneScreen screen) {
                int[] p = screen.hitPoint(0, 4);
                if (p != null) {
                    mouse(mc, p[0], p[1]);
                }
            }
        });
        shot(mc, "stone-rune-hover");
        add("stone rune click", 90, () -> {
            if (mc.screen instanceof dev.amman.proficiency.client.stone.SkillstoneScreen screen) {
                int[] p = screen.hitPoint(0, 0);
                if (p != null) {
                    mouse(mc, p[0], p[1]);
                    screen.mouseClicked(p[0], p[1], 0);
                }
            }
        });
        shot(mc, "stone-rune-click");
        add("stone combat row", 900, () -> {
            if (mc.screen instanceof dev.amman.proficiency.client.stone.SkillstoneScreen screen) {
                int[] p = screen.hitPoint(1, Skill.SWORDS.ordinal());
                if (p != null) {
                    mouse(mc, p[0], p[1]);
                }
            }
        });
        shot(mc, "stone-combat");
        add("stone open face", 60, () -> {
            if (mc.screen instanceof dev.amman.proficiency.client.stone.SkillstoneScreen screen) {
                int[] p = screen.hitPoint(1, Skill.SWORDS.ordinal());
                if (p != null) {
                    screen.mouseClicked(p[0], p[1], 0);
                }
            }
        });
        shot(mc, "stone-turn");
        prune(mark, group);
        mark = STEPS.size();
        group = "stoneface"; // PROFICIENCY_LAYOUT_ONLY=stoneface runs just the Face scenes (stone runs them too)
        add("stone face", 700, () -> {
            mc.setScreen(new dev.amman.proficiency.client.stone.SkillstoneScreen(Skill.MINING));
            mouse(mc, 2, 2);
        });
        shot(mc, "stone-face");
        add("stone face synergy", 500, () -> {
            if (mc.screen instanceof dev.amman.proficiency.client.stone.SkillstoneScreen screen) {
                int[] p = screen.hitPoint(6, 2);
                if (p != null) {
                    mouse(mc, p[0], p[1]);
                }
            }
        });
        shot(mc, "stone-face-synergy");
        // The one-column Face (GUI 4 and 960x720 at GUI 3): the audit line says whether it is on and how long the
        // column is; then the wheel scrolls it to the middle and to the end. Bigger sizes keep two columns and ignore the wheel.
        add("stone face column", 300, () -> {
            if (mc.screen instanceof dev.amman.proficiency.client.stone.SkillstoneScreen screen) {
                LayoutAudit.note("FACE " + setup + " " + screen.faceInfo());
            }
        });
        add("stone face scroll", 400, () -> {
            if (mc.screen instanceof dev.amman.proficiency.client.stone.SkillstoneScreen screen) {
                for (int i = 0; i < 3; i++) {
                    screen.mouseScrolled(10, 10, 0, -1);
                }
            }
        });
        shot(mc, "stone-face-scroll");
        add("stone face end", 400, () -> {
            if (mc.screen instanceof dev.amman.proficiency.client.stone.SkillstoneScreen screen) {
                for (int i = 0; i < 40; i++) {
                    screen.mouseScrolled(10, 10, 0, -1);
                }
                LayoutAudit.note("FACE " + setup + " end " + screen.faceInfo());
            }
        });
        shot(mc, "stone-face-end");
        add("stone refuse", 60, () -> {
            if (mc.screen instanceof dev.amman.proficiency.client.stone.SkillstoneScreen screen) {
                int[] p = screen.hitPoint(3, 2);
                if (p != null) {
                    mouse(mc, p[0], p[1]);
                    screen.mouseClicked(p[0], p[1], 0);
                }
            }
        });
        shot(mc, "stone-refuse");
        prune(mark, group);
        mark = STEPS.size();
        group = "stone";
        // The Tree tab (stage 2): Mining at level 60 from an empty tree, a hovered socket, the root
        // filled rank by rank with a frame sheet of the last rank, a refused click, a capstone flare.
        // A respec costs a vanilla level per 10 points in survival.
        add("stone tree levels", 300, () -> command(mc, "xp add @s 30 levels"));
        add("stone tree respec", 400, () -> command(mc, "proficiency respec mining"));
        // The root's last rank wants materials (Cobblestone and Coal for Mining): bring them.
        add("stone tree give cobble", 300, () -> command(mc, "give @s cobblestone 128"));
        add("stone tree give coal", 300, () -> command(mc, "give @s coal 64"));
        add("stone tree", 800, () -> {
            dev.amman.proficiency.client.stone.SkillstoneScreen.demoClock(-1);
            dev.amman.proficiency.client.stone.SkillstoneScreen screen = new dev.amman.proficiency.client.stone.SkillstoneScreen(Skill.MINING);
            mc.setScreen(screen);
            screen.treeFor(Skill.MINING, null);
            mouse(mc, 2, 2);
        });
        shot(mc, "stone-tree");
        for (int k = 0; k < 4; k++) {
            add("stone tree root " + k, 500, () -> stoneClick(mc, 7, 0));
        }
        add("stone tree hover", 500, () -> {
            if (mc.screen instanceof dev.amman.proficiency.client.stone.SkillstoneScreen screen) {
                int[] p = screen.hitPoint(7, 1);
                if (p != null) {
                    mouse(mc, p[0], p[1]);
                }
            }
        });
        shot(mc, "stone-tree-hover");
        add("stone tree freeze", 100, () -> dev.amman.proficiency.client.stone.SkillstoneScreen.demoClock(0));
        add("stone tree last rank", 600, () -> stoneClick(mc, 7, 0));
        for (long at : new long[] {0, 60, 130, 200, 280, 360, 460, 600, 800, 1000}) {
            add("stone unlock " + at, 120, () -> dev.amman.proficiency.client.stone.SkillstoneScreen.demoClock(at));
            shot(mc, "stone-unlock-" + String.format(java.util.Locale.ROOT, "%04d", at));
        }
        add("stone tree thaw", 600, () -> dev.amman.proficiency.client.stone.SkillstoneScreen.demoClock(-1));
        // Filling the root at level 60 woke the signature and the ability: the unlock slam turned the slab to the Face.
        add("stone tree back", 400, () -> {
            if (mc.screen instanceof dev.amman.proficiency.client.stone.SkillstoneScreen screen) {
                screen.treeFor(Skill.MINING, null);
            }
        });
        shot(mc, "stone-tree-after");
        add("stone tree refuse", 90, () -> {
            java.util.List<dev.amman.proficiency.perk.Talent> nodes = dev.amman.proficiency.perk.Talents.of(Skill.MINING);
            for (int i = nodes.size() - 1; i >= 0; i--) {
                if (nodes.get(i).requiredLevel() > 60) {
                    stoneClick(mc, 7, i);
                    break;
                }
            }
        });
        shot(mc, "stone-tree-refuse");
        add("stone tree scroll", 400, () -> {
            if (mc.screen instanceof dev.amman.proficiency.client.stone.SkillstoneScreen screen) {
                int[] p = screen.hitPoint(7, 0);
                if (p != null) {
                    screen.mouseScrolled(p[0], p[1], -40); // to the end: the last row's labels must show
                }
            }
        });
        shot(mc, "stone-tree-scrolled");
        prune(mark, group);
        mark = STEPS.size();
        group = "stonemat"; // PROFICIENCY_LAYOUT_ONLY=stonemat runs just this scene
        // A node with three materials (one short, one on hand, one a tag): the slate block, the badge, the plate.
        add("stone materials give", 300, () -> command(mc, "give @s iron_ingot 1"));
        add("stone materials give stone", 300, () -> command(mc, "give @s stone 4"));
        add("stone materials give logs", 300, () -> command(mc, "give @s oak_log 2"));
        add("stone materials", 700, () -> {
            java.util.List<dev.amman.proficiency.perk.Talent> nodes = dev.amman.proficiency.perk.Talents.of(Skill.MINING);
            dev.amman.proficiency.client.stone.NodeMaterials.demo(nodes.get(1).id());
            dev.amman.proficiency.client.stone.SkillstoneScreen screen = new dev.amman.proficiency.client.stone.SkillstoneScreen(Skill.MINING);
            mc.setScreen(screen);
            screen.treeFor(Skill.MINING, nodes.get(1).id());
            mouse(mc, 2, 2);
        });
        shot(mc, "stone-materials");
        add("stone materials hover", 500, () -> {
            if (mc.screen instanceof dev.amman.proficiency.client.stone.SkillstoneScreen screen) {
                int[] p = screen.hitPoint(7, 1);
                if (p != null) {
                    mouse(mc, p[0], p[1]);
                }
            }
        });
        shot(mc, "stone-materials-hover");
        add("stone materials end", 100, () -> dev.amman.proficiency.client.stone.NodeMaterials.demo(null));
        // The real root (Cobblestone and Coal): hover, the plate saying what is missing, then what a click pays, then the pay.
        add("stone pay open", 600, () -> {
            dev.amman.proficiency.client.stone.SkillstoneScreen screen = new dev.amman.proficiency.client.stone.SkillstoneScreen(Skill.MINING);
            mc.setScreen(screen);
            screen.treeFor(Skill.MINING, dev.amman.proficiency.perk.Talents.of(Skill.MINING).get(0).id());
            mouse(mc, 2, 2);
        });
        for (int k = 0; k < 4; k++) { // four ranks of five: the next click fills the node, so it is the one that pays
            add("stone pay rank " + k, 500, () -> stoneClick(mc, 7, 0));
        }
        add("stone pay hover", 400, () -> {
            if (mc.screen instanceof dev.amman.proficiency.client.stone.SkillstoneScreen screen) {
                int[] p = screen.hitPoint(7, 0);
                if (p != null) {
                    mouse(mc, p[0], p[1]);
                }
            }
        });
        shot(mc, "stone-pay-missing");
        add("stone pay give cobble", 300, () -> command(mc, "give @s cobblestone 128"));
        add("stone pay give coal", 700, () -> command(mc, "give @s coal 64"));
        shot(mc, "stone-pay-ready");
        add("stone pay click", 60, () -> stoneClick(mc, 7, 0));
        shot(mc, "stone-pay-f1");
        add("stone pay wait 1", 250, () -> { });
        shot(mc, "stone-pay-f2");
        add("stone pay wait 2", 350, () -> { });
        shot(mc, "stone-pay-f3");
        add("stone pay wait 3", 900, () -> { });
        shot(mc, "stone-pay-paid");
        prune(mark, group);
        mark = STEPS.size();
        group = "stone";
        add("stone capstone", 100, () -> {
            dev.amman.proficiency.client.stone.SkillstoneScreen screen = new dev.amman.proficiency.client.stone.SkillstoneScreen(Skill.SWORDS);
            mc.setScreen(screen);
            screen.treeFor(Skill.SWORDS, null);
            mouse(mc, 2, 2);
        });
        add("stone capstone fill", 900, () -> command(mc, "proficiency filltree @s swords"));
        add("stone capstone freeze", 100, () -> {
            dev.amman.proficiency.client.stone.SkillstoneScreen.demoClock(0);
            if (mc.screen instanceof dev.amman.proficiency.client.stone.SkillstoneScreen screen) {
                java.util.List<dev.amman.proficiency.perk.Talent> nodes = dev.amman.proficiency.perk.Talents.of(Skill.SWORDS);
                screen.demoLand(nodes.get(nodes.size() - 1).id());
            }
        });
        for (long at : new long[] {120, 220, 340, 500, 700}) {
            add("stone capstone " + at, 120, () -> dev.amman.proficiency.client.stone.SkillstoneScreen.demoClock(at));
            shot(mc, "stone-capstone-" + String.format(java.util.Locale.ROOT, "%04d", at));
        }
        add("stone capstone thaw", 200, () -> dev.amman.proficiency.client.stone.SkillstoneScreen.demoClock(-1));
        // The Hearth tab (stage 3): the demo's fake Hearth on the stone face, then a stage and a pool tier rising.
        add("stone hearth feed", 900, () -> {
            dev.amman.proficiency.client.stone.SkillstoneScreen.demoClock(-1);
            dev.amman.proficiency.hearth.client.HearthDemo.feed("clear");
            dev.amman.proficiency.hearth.client.HearthDemo.feed("home");
            dev.amman.proficiency.hearth.client.HearthDemo.feed("panel");
        });
        add("stone hearth", 900, () -> {
            mc.setScreen(null);
            dev.amman.proficiency.client.stone.SkillstoneScreen.openHearth();
            mouse(mc, 2, 2);
        });
        shot(mc, "stone-hearth");
        add("stone hearth hover", 400, () -> stoneHover(mc, 8, 0));
        shot(mc, "stone-hearth-hover");
        add("stone hearth rise", 100, () -> {
            dev.amman.proficiency.client.stone.SkillstoneScreen.demoClock(0);
            if (mc.screen instanceof dev.amman.proficiency.client.stone.SkillstoneScreen screen) {
                screen.demoHearthRise();
            }
        });
        for (long at : new long[] {200, 350, 550}) {
            add("stone hearth " + at, 120, () -> dev.amman.proficiency.client.stone.SkillstoneScreen.demoClock(at));
            shot(mc, "stone-hearth-rise-" + String.format(java.util.Locale.ROOT, "%04d", at));
        }
        // Stage 3b (1.32.0): the Hearth's further slab pages on the same fake Hearth.
        add("stone guide", 900, () -> {
            dev.amman.proficiency.client.stone.SkillstoneScreen.demoClock(-1);
            mc.setScreen(null);
            dev.amman.proficiency.client.stone.SkillstoneScreen.openHearth();
            mc.setScreen(new dev.amman.proficiency.client.stone.StoneGuideScreen(mc.screen));
            mouse(mc, 2, 2);
        });
        shot(mc, "stone-guide");
        add("stone guide hover", 700, () -> {
            if (mc.screen instanceof dev.amman.proficiency.client.stone.StoneGuideScreen screen) {
                int[] p = screen.point(1, 2);
                if (p != null) {
                    mouse(mc, p[0], p[1]);
                }
            }
        });
        shot(mc, "stone-guide-hover");
        add("stone guide lane", 700, () -> {
            if (mc.screen instanceof dev.amman.proficiency.client.stone.StoneGuideScreen screen) {
                screen.show(dev.amman.proficiency.hearth.client.HearthGuide.Tab.BUILD, 2, 0);
                mouse(mc, 2, 2);
            }
        });
        shot(mc, "stone-guide-lane");
        add("stone guide boons", 500, () -> {
            if (mc.screen instanceof dev.amman.proficiency.client.stone.StoneGuideScreen screen) {
                screen.show(dev.amman.proficiency.hearth.client.HearthGuide.Tab.BOONS, -1, 0);
            }
        });
        shot(mc, "stone-guide-boons");
        add("stone keepers", 700, () -> {
            mc.setScreen(null);
            dev.amman.proficiency.client.stone.SkillstoneScreen.openHearth();
            mc.setScreen(new dev.amman.proficiency.client.stone.StoneKeepersScreen(mc.screen, -1, false));
            mouse(mc, 2, 2);
        });
        shot(mc, "stone-keepers");
        add("stone keepers hover", 400, () -> {
            if (mc.screen instanceof dev.amman.proficiency.client.stone.StoneKeepersScreen screen) {
                int[] p = screen.keeperPoint();
                mouse(mc, p[0], p[1]);
            }
        });
        shot(mc, "stone-keepers-hover");
        add("stone keepers pool", 600, () -> {
            mc.setScreen(new dev.amman.proficiency.client.stone.StoneKeepersScreen(null, 0, true));
            mouse(mc, 2, 2);
        });
        shot(mc, "stone-keepers-pool");
        add("stone plan", 700, () -> {
            mc.setScreen(dev.amman.proficiency.client.stone.StonePlanScreen.demo(demoPlan(), null));
            mouse(mc, 2, 2);
        });
        shot(mc, "stone-plan");
        add("stone plan open", 500, () -> {
            if (mc.screen instanceof dev.amman.proficiency.client.stone.StonePlanScreen screen) {
                screen.demoOpen();
            }
        });
        shot(mc, "stone-plan-open");
        add("stone plan refuse", 90, () -> {
            if (mc.screen instanceof dev.amman.proficiency.client.stone.StonePlanScreen screen) {
                int[] p = screen.point(1, 3);
                if (p != null) {
                    sheetClick(mc, p);
                }
            }
        });
        shot(mc, "stone-plan-refuse");
        // Stage 4 (1.32.0): Expertise, Mastery and Titles, the Journal, and the dial's fifth corner socket.
        add("stone expertise", 800, () -> {
            dev.amman.proficiency.client.stone.SkillstoneScreen.demoClock(-1);
            mc.setScreen(new dev.amman.proficiency.client.stone.StoneExpertiseScreen(null));
            mouse(mc, 2, 2);
        });
        shot(mc, "stone-expertise");
        add("stone expertise hover", 400, () -> {
            if (mc.screen instanceof dev.amman.proficiency.client.stone.StoneExpertiseScreen screen) {
                int[] p = screen.nodePoint(0);
                if (p != null) {
                    mouse(mc, p[0], p[1]);
                }
            }
        });
        shot(mc, "stone-expertise-hover");
        add("stone expertise freeze", 100, () -> dev.amman.proficiency.client.stone.SkillstoneScreen.demoClock(0));
        add("stone expertise click", 700, () -> {
            if (mc.screen instanceof dev.amman.proficiency.client.stone.StoneExpertiseScreen screen) {
                int[] p = screen.nodePoint(0);
                if (p != null) {
                    sheetClick(mc, p);
                }
            }
        });
        for (long at : new long[] {60, 130, 200, 340, 500}) {
            add("stone expertise " + at, 120, () -> dev.amman.proficiency.client.stone.SkillstoneScreen.demoClock(at));
            shot(mc, "stone-expertise-" + String.format(java.util.Locale.ROOT, "%04d", at));
        }
        add("stone expertise thaw", 300, () -> dev.amman.proficiency.client.stone.SkillstoneScreen.demoClock(-1));
        add("stone mastery", 700, () -> {
            mc.setScreen(new dev.amman.proficiency.client.stone.StoneLedgerScreen(null, false));
            mouse(mc, 2, 2);
        });
        shot(mc, "stone-mastery");
        add("stone titles", 700, () -> {
            mc.setScreen(new dev.amman.proficiency.client.stone.StoneLedgerScreen(null, true));
            mouse(mc, 2, 2);
        });
        shot(mc, "stone-titles");
        add("stone titles hover", 400, () -> {
            if (mc.screen instanceof dev.amman.proficiency.client.stone.StoneLedgerScreen screen) {
                int[] p = screen.point(2, -1);
                if (p != null) {
                    mouse(mc, p[0], p[1]);
                }
            }
        });
        shot(mc, "stone-titles-hover");
        add("stone journal", 700, () -> {
            mc.setScreen(new dev.amman.proficiency.client.stone.StoneJournalScreen(null));
            mouse(mc, 2, 2);
        });
        shot(mc, "stone-journal");
        add("stone journal skills", 600, () -> {
            if (mc.screen instanceof dev.amman.proficiency.client.stone.StoneJournalScreen screen) {
                screen.showTab(3);
            }
        });
        shot(mc, "stone-journal-skills");
        add("stone corner", 900, () -> {
            mc.setScreen(new dev.amman.proficiency.client.stone.SkillstoneScreen());
            dev.amman.proficiency.client.stone.SkillstoneScreen.demoTime(-1);
        });
        add("stone corner hover", 400, () -> stoneHover(mc, 2, 3));
        shot(mc, "stone-corner");
        add("stone hearth thaw", 200, () -> {
            dev.amman.proficiency.client.stone.SkillstoneScreen.demoClock(-1);
            mc.setScreen(null);
            dev.amman.proficiency.hearth.client.HearthDemo.feed("clear");
        });
        add("stone close", 200, () -> mc.setScreen(null));

        prune(mark, group);
        mark = STEPS.size();
        group = "tree";
        // 2 two trees, a node tooltip on the node with the longest description
        for (Skill skill : new Skill[] {Skill.SPELUNKING, Skill.BEASTSLAYING}) {
            add("tree " + skill.id(), 900, () -> {
                injectLog(skill);
                mc.setScreen(new TalentTreeScreen(skill, null));
            });
            add("tree hover", 500, () -> {
                if (mc.screen instanceof TalentTreeScreen screen) {
                    Talent longest = null;
                    int most = -1;
                    for (Talent talent : Talents.of(skill)) {
                        int w = mc.font.width(Component.translatable(talent.descriptionKey()));
                        if (w > most) {
                            most = w;
                            longest = talent;
                        }
                    }
                    int[] p = screen.nodePoint(longest.row(), longest.col());
                    mouse(mc, p[0], p[1]);
                }
            });
            shot(mc, "tree-" + skill.id());
        }
        add("tree synergy hover", 500, () -> {
            if (mc.screen instanceof TalentTreeScreen screen) {
                int[] p = screen.synergyPoint(0);
                mouse(mc, p[0], p[1]);
            }
        });
        shot(mc, "tree-synergy");

        prune(mark, group);
        mark = STEPS.size();
        group = "refunds";
        // The free-block wording (refund fix): the reworded Masonry and Decorating nodes, one tooltip each.
        String[][] refundNodes = {{"masonry", "stonecutter"}, {"masonry", "bulk_order"}, {"masonry", "master_mason"},
                {"decorating", "feng_shui"}, {"decorating", "lamplighter"}, {"decorating", "master_decorator"}};
        for (String[] node : refundNodes) {
            Skill skill = Skill.byId(node[0]);
            add("refunds " + node[1], 700, () -> {
                injectLog(skill);
                mc.setScreen(new TalentTreeScreen(skill, null));
            });
            add("refunds hover " + node[1], 500, () -> {
                if (mc.screen instanceof TalentTreeScreen screen) {
                    Talent talent = Talents.get(skill, node[1]);
                    int[] p = screen.nodePoint(talent.row(), talent.col());
                    mouse(mc, p[0], p[1]);
                }
            });
            shot(mc, "refunds-" + node[0] + "-" + node[1]);
        }

        prune(mark, group);
        mark = STEPS.size();
        group = "synergies";
        // 2a trees with about ten synergies each: the list sorted awake first, the "+N more" row and
        // its tooltip, then scrolled to the end. Filled trees wake some of them; the rest show 1/2.
        for (Skill skill : new Skill[] {Skill.GUARDIAN, Skill.FISHING, Skill.JUMPING}) {
            add("synergies fill " + skill.id(), 300, () -> command(mc, "proficiency filltree @s " + skill.id()));
        }
        for (Skill skill : new Skill[] {Skill.GUARDIAN, Skill.FISHING, Skill.JUMPING}) {
            add("synergies " + skill.id(), 900, () -> {
                injectLog(skill);
                mc.setScreen(new TalentTreeScreen(skill, null));
            });
            shot(mc, "synergies-" + skill.id());
            add("synergies more hover", 500, () -> {
                if (mc.screen instanceof TalentTreeScreen screen) {
                    int[] p = screen.morePoint();
                    if (p != null) {
                        mouse(mc, p[0], p[1]);
                    }
                }
            });
            shot(mc, "synergies-more-" + skill.id());
            add("synergies scrolled", 500, () -> {
                if (mc.screen instanceof TalentTreeScreen screen) {
                    int[] p = screen.synergyPoint(0);
                    for (int i = 0; i < 20; i++) {
                        screen.mouseScrolled(p[0], p[1], 0, -1);
                    }
                    mouse(mc, p[0], p[1]);
                }
            });
            shot(mc, "synergies-scrolled-" + skill.id());
        }
        for (Skill skill : new Skill[] {Skill.GUARDIAN, Skill.FISHING, Skill.JUMPING}) {
            add("synergies respec " + skill.id(), 200, () -> command(mc, "proficiency respec " + skill.id()));
        }

        prune(mark, group);
        mark = STEPS.size();
        group = "expertise";
        // 2b the Expertise tree: part bought (a node tooltip, then the points header), then full with
        // the respec button hovered.
        add("expertise reset", 300, () -> command(mc, "proficiency expertise reset @s"));
        for (String id : new String[] {"breadth", "breadth", "breadth", "martial", "martial", "martial", "craft",
                "craft"}) {
            add("expertise invest " + id, 150, () -> command(mc, "proficiency expertise invest " + id));
        }
        add("expertise open", 1500, () -> mc.setScreen(new ExpertiseScreen(null)));
        add("expertise hover", 500, () -> {
            if (mc.screen instanceof ExpertiseScreen screen) {
                String longest = "breadth";
                int most = -1;
                for (dev.amman.proficiency.perk.ExpertiseNode node : dev.amman.proficiency.perk.Expertise.nodes()) {
                    int w = mc.font.width(Component.translatable(node.descriptionKey()));
                    if (w > most) {
                        most = w;
                        longest = node.id();
                    }
                }
                int[] p = screen.nodePoint(longest);
                mouse(mc, p[0], p[1]);
            }
        });
        shot(mc, "expertise");
        add("expertise header hover", 500, () -> {
            if (mc.screen instanceof ExpertiseScreen screen) {
                int[] p = screen.headerPoint();
                mouse(mc, p[0], p[1]);
            }
        });
        shot(mc, "expertise-points");
        add("expertise fill", 400, () -> command(mc, "proficiency expertise fill @s"));
        add("expertise full", 1800, () -> {
            if (mc.screen instanceof ExpertiseScreen screen) {
                int[] p = screen.respecPoint();
                mouse(mc, p[0], p[1]);
            }
        });
        shot(mc, "expertise-full");
        add("expertise panel", 700, () -> mc.setScreen(new SkillsScreen()));
        add("expertise panel hover", 400, () -> {
            if (mc.screen instanceof SkillsScreen screen) {
                int[] p = screen.expertisePoint();
                mouse(mc, p[0], p[1]);
            }
        });
        shot(mc, "expertise-panel");
        add("expertise done", 300, () -> command(mc, "proficiency expertise reset @s"));

        prune(mark, group);
        mark = STEPS.size();
        group = "hud";
        // 3 the HUD: skill line, XP feed, a banner, a toast
        add("hud", 600, () -> {
            mc.setScreen(null);
            mc.player.getInventory().selected = 1;
            command(mc, "proficiency addxp @s mining 4");
            feed();
            DiscoveryBanner.show(new DiscoveryPayload(DiscoveryPayload.STRUCTURE,
                    "structure.minecraft.ancient_city", "minecraft:ancient_city", Skill.WAYFARING.ordinal(),
                    150f, ""));
            SkillToasts.show(longestSkill(mc), 45);
        });
        add("hud wait", 1700, () -> {
        });
        shot(mc, "hud");
        add("banner 2", 300, () -> DiscoveryBanner.show(new DiscoveryPayload(DiscoveryPayload.BIOME,
                "biome.minecraft.old_growth_pine_taiga", "minecraft:old_growth_pine_taiga",
                Skill.WAYFARING.ordinal(), 40f, "")));
        add("banner 2 wait", 1900, () -> {
        });
        shot(mc, "banner");
        // A Hearth stage sealed by someone else: the longest stage name, the kicker with a name.
        add("banner hearth", 300, () -> DiscoveryBanner.show(new DiscoveryPayload(DiscoveryPayload.HEARTH,
                longestHearthStage(mc), DiscoveryPayload.hearthId(5), -1, 0f, "Wren_The_Builder")));
        add("banner hearth wait", 1900, () -> {
        });
        shot(mc, "banner-hearth");

        prune(mark, group);
        mark = STEPS.size();
        group = "tempo";
        // 3b the Tempo fire on the Mining bar: embers, a step up (the flare), mid heat, the cap, then the lapse
        // sinking to smoke. A grant brings the line up (and sends the server's own chain, at step 0); the chain
        // shown is then fed to the client as the server's packet would be, with a long window that holds it.
        add("tempo quiet", 300, () -> {
            mc.setScreen(null);
            mc.player.getInventory().selected = 1;
            command(mc, "gamerule sendCommandFeedback false");
        });
        // The bar about two thirds full, then the chat cleared: its box would cover the line.
        add("tempo fill", 1500, () -> command(mc, "proficiency addxp @s mining 300"));
        add("tempo chat", 100, () -> mc.gui.getChat().clearMessages(false));
        tempoStep(mc, "tempo low", 1, 1020, 400, 900);
        shot(mc, "tempo-1-low");
        tempoStep(mc, "tempo mid", 12, 1240, 400, 900);
        add("tempo step", 60, () -> TempoBadge.accept(
                new dev.amman.proficiency.net.TempoPayload(Skill.MINING.ordinal(), 13, 25, 1260, 400)));
        shot(mc, "tempo-2-flare");
        add("tempo mid settled", 700, () -> {
        });
        shot(mc, "tempo-3-mid");
        tempoStep(mc, "tempo high", 20, 1400, 400, 900);
        shot(mc, "tempo-4-high");
        tempoStep(mc, "tempo cap", 25, 1500, 400, 900);
        shot(mc, "tempo-5-cap");
        // A one-tick window: it lapses at once and the fire dies down.
        add("tempo lapse", 330, () -> TempoBadge.accept(
                new dev.amman.proficiency.net.TempoPayload(Skill.MINING.ordinal(), 25, 25, 1500, 1)));
        shot(mc, "tempo-6-sinking");
        add("tempo smoke", 450, () -> {
        });
        shot(mc, "tempo-7-smoke");
        add("tempo gone", 1600, () -> {
        });
        shot(mc, "tempo-8-gone");
        prune(mark, group);
        mark = STEPS.size();
        group = "recap";
        // 4 death recap
        add("recap banner", 100, () -> {
            // A respawn into a new place shows the banner and the recap together.
            mc.setScreen(null);
            DiscoveryBanner.show(new DiscoveryPayload(DiscoveryPayload.BIOME, "biome.minecraft.old_growth_pine_taiga",
                    "minecraft:old_growth_pine_taiga", Skill.WAYFARING.ordinal(), 40f, ""));
        });
        add("recap", 900, () -> {
            mc.setScreen(null);
            List<DeathRecapPayload.Row> rows = new ArrayList<>();
            List<Skill> by = new ArrayList<>(List.of(Skill.VALUES));
            by.sort((a, b) -> Integer.compare(
                    mc.font.width(Component.translatable(b.translationKey())),
                    mc.font.width(Component.translatable(a.translationKey()))));
            for (int i = 0; i < 8; i++) {
                rows.add(new DeathRecapPayload.Row(by.get(i).ordinal(), 0.9f - i * 0.1f, i == 0 ? 0.2f : 0f));
            }
            DeathRecapHud.accept(new DeathRecapPayload(rows, 6, 30, 31, 640, 7));
        });
        add("recap wait", 1500, () -> {
        });
        shot(mc, "recap");
        add("recap done", 4500, () -> {
        });

        prune(mark, group);
        mark = STEPS.size();
        group = "tales";
        // 4b the Hearth's tale panel: a homecoming with the longest place names, a Chronicle page, a rated tale
        add("tales", 700, () -> {
            mc.setScreen(null);
            TalesPayInHud.accept(new dev.amman.proficiency.hearth.net.HearthTalesPayload(
                    dev.amman.proficiency.hearth.net.HearthTalesPayload.PAID,
                    dev.amman.proficiency.hearth.HearthPool.HOMECOMING.ordinal(), 1423f, 32_000,
                    -1, List.of("structure.minecraft.ancient_city", "dimension.minecraft.the_nether",
                            "biome.minecraft.old_growth_pine_taiga"), ""));
        });
        add("tales wait", 1700, () -> {
        });
        shot(mc, "tales");
        add("tales retold", 700, () -> {
            mc.setScreen(null);
            TalesPayInHud.accept(new dev.amman.proficiency.hearth.net.HearthTalesPayload(
                    dev.amman.proficiency.hearth.net.HearthTalesPayload.RETOLD,
                    dev.amman.proficiency.hearth.HearthPool.HOMECOMING.ordinal(), 20f, 0, 4, List.of(),
                    "We walked to the stronghold at dusk and it was cold"));
        });
        add("tales retold wait", 1900, () -> {
        });
        shot(mc, "tales-retold");
        add("tales done", 3000, () -> {
        });

        prune(mark, group);
        mark = STEPS.size();
        group = "wheel";
        // 5 ability wheel
        add("wheel", 700, () -> {
            AbilityWheelScreen wheel = AbilityWheelScreen.forPlayer(mc.player);
            if (wheel != null) {
                mc.setScreen(wheel);
            }
        });
        add("wheel hover", 500, () -> {
            if (mc.screen instanceof AbilityWheelScreen wheel) {
                int[] p = wheel.slotPoint(longestSlot(mc, wheel));
                mouse(mc, p[0], p[1]);
            }
        });
        shot(mc, "wheel");
        add("wheel idle", 400, () -> mouse(mc, mc.getWindow().getGuiScaledWidth() / 2,
                mc.getWindow().getGuiScaledHeight() / 2));
        shot(mc, "wheel-idle");

        prune(mark, group);
        mark = STEPS.size();
        group = "waywheel";
        // The Waywheel (ui.style = modern, 1.33.0): three pins on the belt with Mining's Tempo fire and Running
        // cooling, the opening frozen at storyboard times, Gathering's tokens with Mining hovered, Combat's two
        // rings, Construction, a refused release (a locked ability) and a cast that flares the belt.
        add("waywheel style", 300, () -> {
            dev.amman.proficiency.config.ProficiencyClientConfig.setUiStyle(UiStyle.MODERN);
            dev.amman.proficiency.config.ProficiencyClientConfig.setUiWaywheelPins("mining,swords,running");
            mc.setScreen(null);
            mc.player.getInventory().selected = 0;
            command(mc, "gamerule sendCommandFeedback false");
        });
        for (String id : new String[] {"mining", "swords", "running", "woodcutting"}) {
            add("waywheel fill " + id, 400, () -> command(mc, "proficiency filltree @s " + id));
        }
        add("waywheel cast running", 700, () -> ClientInputEvents.cast(Skill.RUNNING));
        add("waywheel tempo", 600, () -> {
            TempoBadge.accept(new dev.amman.proficiency.net.TempoPayload(Skill.MINING.ordinal(), 20, 25, 1400, 6000));
            mc.gui.getChat().clearMessages(false);
        });
        shot(mc, "waywheel-belt");
        add("waywheel freeze", 100, () -> {
            dev.amman.proficiency.client.stone.SkillstoneScreen.demoClock(-1);
            dev.amman.proficiency.client.stone.SkillstoneScreen.demoClock(0);
            mc.setScreen(new dev.amman.proficiency.client.stone.WaywheelScreen(ProficiencyClient.USE_ABILITY));
            mouse(mc, mc.getWindow().getGuiScaledWidth() / 2, mc.getWindow().getGuiScaledHeight() / 2);
        });
        for (long at : new long[] {0, 40, 80, 120, 160, 240, 330}) {
            add("waywheel open " + at, 150, () -> dev.amman.proficiency.client.stone.SkillstoneScreen.demoClock(at));
            shot(mc, "waywheel-open-" + String.format(java.util.Locale.ROOT, "%04d", at));
        }
        add("waywheel thaw", 300, () -> dev.amman.proficiency.client.stone.SkillstoneScreen.demoClock(-1));
        add("waywheel gathering", 500, () -> {
            if (mc.screen instanceof dev.amman.proficiency.client.stone.WaywheelScreen wheel) {
                wheel.showCategory(dev.amman.proficiency.skill.SkillCategory.GATHERING);
            }
        });
        add("waywheel mining", 400, () -> {
            if (mc.screen instanceof dev.amman.proficiency.client.stone.WaywheelScreen wheel) {
                int[] p = wheel.tokenPoint(wheel.tokenOf(Skill.MINING));
                mouse(mc, p[0], p[1]);
            }
        });
        shot(mc, "waywheel-mining");
        add("waywheel rune", 400, () -> {
            if (mc.screen instanceof dev.amman.proficiency.client.stone.WaywheelScreen wheel) {
                int[] p = wheel.runeAt(dev.amman.proficiency.skill.SkillCategory.MOVEMENT.ordinal());
                mouse(mc, p[0], p[1]);
            }
        });
        shot(mc, "waywheel-movement");
        add("waywheel combat", 600, () -> {
            if (mc.screen instanceof dev.amman.proficiency.client.stone.WaywheelScreen wheel) {
                // The cursor back to the hub first: inside the rim it would pick its own rune again.
                mouse(mc, mc.getWindow().getGuiScaledWidth() / 2, mc.getWindow().getGuiScaledHeight() / 2);
                wheel.showCategory(dev.amman.proficiency.skill.SkillCategory.COMBAT);
            }
        });
        add("waywheel swords", 400, () -> {
            if (mc.screen instanceof dev.amman.proficiency.client.stone.WaywheelScreen wheel) {
                int[] p = wheel.tokenPoint(wheel.tokenOf(Skill.SWORDS));
                mouse(mc, p[0], p[1]);
            }
        });
        shot(mc, "waywheel-combat");
        add("waywheel construction", 600, () -> {
            if (mc.screen instanceof dev.amman.proficiency.client.stone.WaywheelScreen wheel) {
                wheel.showCategory(dev.amman.proficiency.skill.SkillCategory.CONSTRUCTION);
                mouse(mc, 4, 4);
            }
        });
        shot(mc, "waywheel-construction");
        add("waywheel locked", 600, () -> {
            if (mc.screen instanceof dev.amman.proficiency.client.stone.WaywheelScreen wheel) {
                wheel.showCategory(dev.amman.proficiency.skill.SkillCategory.GATHERING);
            }
        });
        add("waywheel fishing", 400, () -> {
            if (mc.screen instanceof dev.amman.proficiency.client.stone.WaywheelScreen wheel) {
                int[] p = wheel.tokenPoint(wheel.tokenOf(Skill.FISHING));
                mouse(mc, p[0], p[1]);
            }
        });
        add("waywheel refuse", 100, () -> {
            dev.amman.proficiency.client.stone.SkillstoneScreen.demoClock(0);
            if (mc.screen instanceof dev.amman.proficiency.client.stone.WaywheelScreen wheel) {
                wheel.demoRelease();
            }
        });
        for (long at : new long[] {30, 90, 200}) {
            add("waywheel refuse " + at, 120, () -> dev.amman.proficiency.client.stone.SkillstoneScreen.demoClock(at));
            shot(mc, "waywheel-refuse-" + String.format(java.util.Locale.ROOT, "%04d", at));
        }
        add("waywheel refuse thaw", 700, () -> dev.amman.proficiency.client.stone.SkillstoneScreen.demoClock(-1));
        // A world kept from an earlier run may hold Mining's and Woodcutting's cooldowns: clear them on both
        // sides so the cast below fires instead of being refused.
        add("waywheel cooldowns", 300, () -> {
            net.minecraft.server.MinecraftServer server = mc.getSingleplayerServer();
            java.util.UUID id = mc.player.getUUID();
            for (Skill s : new Skill[] {Skill.MINING, Skill.WOODCUTTING}) {
                dev.amman.proficiency.ProficiencyAttachments.of(mc.player).shortenCooldown(s, 1_000_000L, mc.player.level().getGameTime());
            }
            if (server != null) {
                server.execute(() -> {
                    net.minecraft.server.level.ServerPlayer sp = server.getPlayerList().getPlayer(id);
                    if (sp != null) {
                        for (Skill s : new Skill[] {Skill.MINING, Skill.WOODCUTTING}) {
                            dev.amman.proficiency.ProficiencyAttachments.of(sp).shortenCooldown(s, 1_000_000L, sp.level().getGameTime());
                        }
                    }
                });
            }
        });
        add("waywheel again", 500, () -> {
            mc.setScreen(new dev.amman.proficiency.client.stone.WaywheelScreen(ProficiencyClient.USE_ABILITY));
        });
        add("waywheel cast hover", 400, () -> {
            if (mc.screen instanceof dev.amman.proficiency.client.stone.WaywheelScreen wheel) {
                wheel.showCategory(dev.amman.proficiency.skill.SkillCategory.GATHERING);
                int[] p = wheel.tokenPoint(wheel.tokenOf(Skill.MINING));
                mouse(mc, p[0], p[1]);
            }
        });
        // The cast (1.34.0), frozen at storyboard times: the wheel closes at once, the HUD folds the disc and
        // flies Mining (pinned) to its belt socket, which flares as it lands.
        add("waywheel cast freeze", 100, () -> {
            dev.amman.proficiency.client.stone.SkillstoneScreen.demoClock(-1);
            dev.amman.proficiency.client.stone.SkillstoneScreen.demoClock(0);
        });
        add("waywheel cast", 120, () -> {
            if (mc.screen instanceof dev.amman.proficiency.client.stone.WaywheelScreen wheel) {
                wheel.demoRelease();
            }
        });
        for (long at : new long[] {0, 40, 80, 120, 160, 200, 250, 300, 360, 480}) {
            add("waywheel fly " + at, 150, () -> dev.amman.proficiency.client.stone.SkillstoneScreen.demoClock(at));
            shot(mc, "waywheel-fly-" + String.format(java.util.Locale.ROOT, "%04d", at));
        }
        add("waywheel fly thaw", 700, () -> dev.amman.proficiency.client.stone.SkillstoneScreen.demoClock(-1));
        shot(mc, "waywheel-cast-after");
        // Off the belt: Woodcutting is not pinned, so its token lifts off the wheel and burns away.
        add("waywheel pop open", 500, () -> {
            mc.setScreen(new dev.amman.proficiency.client.stone.WaywheelScreen(ProficiencyClient.USE_ABILITY));
        });
        add("waywheel pop hover", 400, () -> {
            if (mc.screen instanceof dev.amman.proficiency.client.stone.WaywheelScreen wheel) {
                wheel.showCategory(dev.amman.proficiency.skill.SkillCategory.GATHERING);
                int[] p = wheel.tokenPoint(wheel.tokenOf(Skill.WOODCUTTING));
                mouse(mc, p[0], p[1]);
            }
        });
        add("waywheel pop freeze", 100, () -> {
            dev.amman.proficiency.client.stone.SkillstoneScreen.demoClock(-1);
            dev.amman.proficiency.client.stone.SkillstoneScreen.demoClock(0);
        });
        add("waywheel pop", 120, () -> {
            if (mc.screen instanceof dev.amman.proficiency.client.stone.WaywheelScreen wheel) {
                wheel.demoRelease();
            }
        });
        for (long at : new long[] {0, 60, 120, 180}) {
            add("waywheel pop " + at, 150, () -> dev.amman.proficiency.client.stone.SkillstoneScreen.demoClock(at));
            shot(mc, "waywheel-pop-" + String.format(java.util.Locale.ROOT, "%04d", at));
        }
        add("waywheel pop thaw", 500, () -> dev.amman.proficiency.client.stone.SkillstoneScreen.demoClock(-1));
        add("waywheel classic", 200, () -> {
            dev.amman.proficiency.config.ProficiencyClientConfig.setUiStyle(UiStyle.CLASSIC);
            dev.amman.proficiency.config.ProficiencyClientConfig.setUiWaywheelPins("");
            mc.setScreen(null);
        });

        prune(mark, group);
        mark = STEPS.size();
        group = "campstone";
        campstoneScenes(mc);

        prune(mark, group);
        mark = STEPS.size();
        group = "tbA";
        // The style toggle both ways (1.34.0): stone -> classic by the slab's rune, then (tbB) classic -> stone by
        // the classic panel's own button. tbA leaves CLASSIC saved, tbB leaves MODERN; tbrestart only opens the
        // skills screen as the saved config says, so a second launch shows the choice stuck.
        add("tb stone", 1800, () -> {
            dev.amman.proficiency.config.ProficiencyClientConfig.setUiStyle(UiStyle.MODERN);
            mc.setScreen(dev.amman.proficiency.client.stone.SkillstoneScreen.skills());
        });
        shot(mc, "tb1-stone");
        add("tb to classic", 1200, () -> stoneClick(mc, 2, 4));
        shot(mc, "tb2-classic");
        add("tb hover stone button", 300, () -> {
            if (mc.screen instanceof SkillsScreen screen) {
                int[] p = screen.stonePoint();
                mouse(mc, p[0], p[1]);
            }
        });
        shot(mc, "tb3-classic-tooltip");

        prune(mark, group);
        mark = STEPS.size();
        group = "tbB";
        add("tb classic open", 800, () -> {
            dev.amman.proficiency.config.ProficiencyClientConfig.setUiStyle(UiStyle.CLASSIC);
            mc.setScreen(dev.amman.proficiency.client.stone.SkillstoneScreen.skills());
        });
        add("tb back to stone", 1800, () -> {
            if (mc.screen instanceof SkillsScreen screen) {
                int[] p = screen.stonePoint();
                mouse(mc, p[0], p[1]);
                screen.mouseClicked(p[0], p[1], 0);
            }
        });
        shot(mc, "tb4-back-in-stone");

        prune(mark, group);
        mark = STEPS.size();
        group = "tbrestart";
        add("tb restart open", 1800, () -> mc.setScreen(dev.amman.proficiency.client.stone.SkillstoneScreen.skills()));
        shot(mc, "tb-restart");

        prune(mark, group);
        mark = STEPS.size();
        group = "item";
        // 6 item tooltip, collapsed and with Shift
        add("item", 700, () -> {
            mc.setScreen(new InventoryScreen(mc.player));
        });
        add("item hover", 500, () -> {
            int left = (mc.getWindow().getGuiScaledWidth() - 176) / 2;
            int top = (mc.getWindow().getGuiScaledHeight() - 166) / 2;
            mouse(mc, left + 8 + 18 + 8, top + 142 + 8);
            SkillTooltip.forceShift(false);
        });
        shot(mc, "item");
        add("item shift", 500, () -> SkillTooltip.forceShift(true));
        shot(mc, "item-shift");
        add("item compass", 500, () -> {
            int left = (mc.getWindow().getGuiScaledWidth() - 176) / 2;
            int top = (mc.getWindow().getGuiScaledHeight() - 166) / 2;
            mouse(mc, left + 8 + 8, top + 142 + 8);
        });
        shot(mc, "item-compass");
        add("item unshift", 100, () -> SkillTooltip.forceShift(null));

        prune(mark, group);
        mark = STEPS.size();
        group = "tip";
        tipScenes(mc);

        prune(mark, group);
        mark = STEPS.size();
        group = "journal";
        // 7 journal and config
        add("journal", 700, () -> {
            DiscoveryJournalScreen screen = new DiscoveryJournalScreen(null);
            mc.setScreen(screen);
        });
        shot(mc, "journal");
        add("codex titles", 700, () -> mc.setScreen(new CodexTitlesScreen(null)));
        shot(mc, "codex-titles");
        add("codex toast", 900, () -> {
            mc.setScreen(null);
            SkillToasts.codex(longestSkill(mc), 100);
        });
        shot(mc, "codex-toast");
        prune(mark, group);
        mark = STEPS.size();
        group = "actionbar";
        // 8 a long message of ours on the action bar: wider than the window, so it goes to the chat
        add("actionbar", 1500, () -> {
            mc.setScreen(null);
            // What the server's displayClientMessage(message, true) sends.
            mc.getConnection().handleSystemChat(new net.minecraft.network.protocol.game.ClientboundSystemChatPacket(
                    Component.translatable("proficiency.station.nothing"), true));
        });
        shot(mc, "actionbar");
        prune(mark, group);
        mark = STEPS.size();
        group = "callout";
        // 8b a proc's name over the crosshair: the widest one in this language, popped, then fading
        add("callout", 40, () -> {
            mc.setScreen(null);
            Skill widest = Skill.VALUES[0];
            for (Skill skill : Skill.VALUES) {
                if (mc.font.width(Component.translatable(skill.procKey())) > mc.font.width(Component.translatable(widest.procKey()))) {
                    widest = skill;
                }
            }
            ProcCallout.accept(new dev.amman.proficiency.net.ProcFxPayload(widest.ordinal(), mc.player.getId(),
                    false, 0, 0, 0, 0, 0));
        });
        shot(mc, "callout");
        add("callout fading", 650, () -> {
        });
        shot(mc, "callout.fading");
        add("callout done", 700, () -> {
        });
        prune(mark, group);
        mark = STEPS.size();
        group = "hudflights";
        hudflightsScenes(mc);
        prune(mark, group);
        mark = STEPS.size();
        group = "hearth";
        hearthScenes(mc);
        prune(mark, group);
        mark = STEPS.size();
        group = "guide";
        guideScenes(mc);
        prune(mark, group);
        mark = STEPS.size();
        group = "synergyslam";
        slamScenes(mc);
        prune(mark, group);
        mark = STEPS.size();
        group = "milestones";
        milestoneScenes(mc);
        prune(mark, group);
        mark = STEPS.size();
        group = "unlockslam";
        unlockSlamScenes(mc);
        prune(mark, group);
        mark = STEPS.size();
        group = "mattips";
        matTipScenes(mc);
        prune(mark, group);
        mark = STEPS.size();
        group = "combattempo";
        combatTempoScenes(mc);
        prune(mark, group);
        mark = STEPS.size();
        group = "hudstack";
        hudStackScenes(mc);
        prune(mark, group);
        mark = STEPS.size();
        group = "hslayout";
        hudStackLayoutScenes(mc);
        prune(mark, group);
        mark = STEPS.size();
        group = "spend";
        spendScenes(mc);
        prune(mark, group);
        add("close", 300, () -> mc.setScreen(null));
        add("clips", 10, () -> {
            LayoutAudit.note("CLIPS " + lang + " " + setup + " " + TextFit.clips());
            TextFit.resetClips();
        });
    }

    // ---- The synergy discovery slam: frame sheets ----------------------------------------------------------------

    private static Path capDir;
    private static int capIndex;

    /** Starts a frame sheet: every rendered frame is saved, and the slam's clock steps 66 ms per frame. */
    private static void startCapture(Minecraft mc, String name) {
        add("capture start " + name, 250, () -> {
            capDir = LayoutAudit.dir().resolve("frames").resolve(name);
            try {
                Files.createDirectories(capDir);
            } catch (java.io.IOException e) {
                LayoutAudit.note("FRAMES FAILED " + name + " " + e);
            }
            capIndex = 0;
            SynergySlam.demoNow = net.minecraft.Util.getMillis();
            SynergySlam.demoStep = 66;
            SynergySlam.frameSink = LayoutDemo::capFrame;
        });
    }

    private static void stopCapture(String name) {
        add("capture stop " + name, 200, () -> {
            SynergySlam.frameSink = null;
            SynergySlam.demoStep = 0;
            LayoutAudit.note("FRAMES " + name + " " + capIndex);
            SynergySlam.reset();
        });
    }

    private static void capFrame(Minecraft mc) {
        try {
            NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget());
            image.writeToFile(capDir.resolve(String.format("%04d.png", ++capIndex)));
            image.close();
        } catch (java.io.IOException e) {
            LayoutAudit.note("FRAME FAILED " + e);
        }
    }

    /** Wakes synergies with commands and records the slam: MODERN, CLASSIC, three icons, a burst, over a screen, no motion. */
    private static void slamScenes(Minecraft mc) {
        add("slam quiet", 300, () -> {
            mc.setScreen(null);
            command(mc, "gamerule sendCommandFeedback false");
            mc.gui.getChat().clearMessages(false);
            dev.amman.proficiency.config.ProficiencyClientConfig.setUiStyle(UiStyle.MODERN);
            mc.options.screenEffectScale().set(1.0);
            mc.options.showSubtitles().set(false);
        });
        add("slam reset", 700, () -> command(mc, "proficiency reset " + mc.player.getGameProfile().getName()));
        add("slam mining", 500, () -> command(mc, "proficiency filltree " + mc.player.getGameProfile().getName() + " mining"));
        add("slam face open", 700, () -> mc.setScreen(new dev.amman.proficiency.client.stone.SkillstoneScreen(Skill.MINING)));
        shot(mc, "slam-face-before");
        add("slam face close", 300, () -> mc.setScreen(null));
        startCapture(mc, "modern-pair");
        add("slam trigger smithing", 3000, () -> command(mc, "proficiency filltree "
                + mc.player.getGameProfile().getName() + " smithing"));
        stopCapture("modern-pair");
        add("slam face open", 700, () -> mc.setScreen(new dev.amman.proficiency.client.stone.SkillstoneScreen(Skill.MINING)));
        shot(mc, "slam-face-after");
        add("slam face close", 300, () -> mc.setScreen(null));
        // CLASSIC banner style.
        add("slam classic", 300, () -> {
            dev.amman.proficiency.config.ProficiencyClientConfig.setUiStyle(UiStyle.CLASSIC);
            command(mc, "proficiency reset " + mc.player.getGameProfile().getName());
        });
        add("slam classic mining", 700, () -> command(mc, "proficiency filltree "
                + mc.player.getGameProfile().getName() + " mining"));
        startCapture(mc, "classic-pair");
        add("slam classic trigger", 3000, () -> command(mc, "proficiency filltree "
                + mc.player.getGameProfile().getName() + " smithing"));
        stopCapture("classic-pair");
        // Three icons: Tidecaller needs Tridents, Swimming and Fishing.
        add("slam three", 300, () -> {
            dev.amman.proficiency.config.ProficiencyClientConfig.setUiStyle(UiStyle.MODERN);
            command(mc, "proficiency reset " + mc.player.getGameProfile().getName());
        });
        add("slam three a", 500, () -> command(mc, "proficiency filltree " + mc.player.getGameProfile().getName() + " tridents"));
        add("slam three b", 2800, () -> command(mc, "proficiency filltree " + mc.player.getGameProfile().getName() + " swimming"));
        startCapture(mc, "modern-three");
        add("slam three trigger", 3000, () -> command(mc, "proficiency filltree "
                + mc.player.getGameProfile().getName() + " fishing"));
        stopCapture("modern-three");
        // A burst: several skills filled one after the other, so discoveries queue.
        add("slam burst reset", 700, () -> command(mc, "proficiency reset " + mc.player.getGameProfile().getName()));
        startCapture(mc, "modern-queue");
        for (String skill : new String[] {"mining", "smithing", "blocking", "endurance", "woodcutting", "axes"}) {
            add("slam burst " + skill, 120, () -> command(mc, "proficiency filltree "
                    + mc.player.getGameProfile().getName() + " " + skill));
        }
        add("slam burst wait", 6500, () -> {
        });
        stopCapture("modern-queue");
        // Over an open talent tree (where a synergy really wakes), then with Distortion Effects at 0%.
        add("slam tree reset", 700, () -> command(mc, "proficiency reset " + mc.player.getGameProfile().getName()));
        add("slam tree mining", 500, () -> command(mc, "proficiency filltree " + mc.player.getGameProfile().getName() + " mining"));
        add("slam tree open", 900, () -> mc.setScreen(new TalentTreeScreen(Skill.SMITHING, null)));
        startCapture(mc, "over-tree");
        add("slam tree trigger", 3000, () -> command(mc, "proficiency filltree "
                + mc.player.getGameProfile().getName() + " smithing"));
        stopCapture("over-tree");
        add("slam tree close", 300, () -> mc.setScreen(null));
        add("slam calm reset", 700, () -> {
            mc.options.screenEffectScale().set(0.0);
            command(mc, "proficiency reset " + mc.player.getGameProfile().getName());
        });
        add("slam calm mining", 500, () -> command(mc, "proficiency filltree " + mc.player.getGameProfile().getName() + " mining"));
        startCapture(mc, "no-distortion");
        add("slam calm trigger", 3000, () -> command(mc, "proficiency filltree "
                + mc.player.getGameProfile().getName() + " smithing"));
        stopCapture("no-distortion");
        add("slam restore", 200, () -> {
            mc.options.screenEffectScale().set(1.0);
            dev.amman.proficiency.config.ProficiencyClientConfig.setUiStyle(UiStyle.MODERN);
        });
    }

    /** {@link #shot(Minecraft, String)} that waits {@code waitMs} after the frame, for frame sheets across a flight. */
    private static void shotEvery(Minecraft mc, String scene, long waitMs) {
        add("shot " + scene, waitMs, () -> {
            Path dir = LayoutAudit.dir().resolve("shots");
            try {
                Files.createDirectories(dir);
                NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget());
                image.writeToFile(dir.resolve(lang + "__" + setup + "__" + scene + ".png"));
                image.close();
            } catch (java.io.IOException e) {
                LayoutAudit.note("SHOT FAILED " + scene + " " + e);
            }
        });
    }

    /**
     * The three HUD flights of 1.38.0 (bonus loot to the hotbar, the talent point coin to its brazier, the rested
     * pool pouring into the fill): frames across each flight, the brazier at its counts, then a live pass that digs
     * stone for real with the server's own bonus loot packets and rested pool.
     */
    private static void hudflightsScenes(Minecraft mc) {
        add("hf setup", 700, () -> {
            mc.setScreen(null);
            dev.amman.proficiency.config.ProficiencyClientConfig.setUiStyle(UiStyle.MODERN);
            dev.amman.proficiency.config.ProficiencyClientConfig.setUiWaywheelPins("mining,swords,running");
            command(mc, "gamerule sendCommandFeedback false");
            command(mc, "gamemode survival @s");
            command(mc, "tp @s ~ ~ ~ 0 6");
            mc.player.getInventory().selected = 1;
            mc.gui.getChat().clearMessages(false);
        });
        for (String item : new String[] {"diamond 2", "cobblestone 3", "cooked_cod 1"}) {
            add("hf give " + item, 250, () -> command(mc, "give @s " + item));
        }
        add("hf clear chat", 400, () -> mc.gui.getChat().clearMessages(false));
        // 1 Bonus loot: a diamond pair and cobblestone ahead, a cod from behind (not in the hotbar... it is: the
        // cod and the far one test the bag and the off-screen start), one flung from far to the right.
        add("hf bonus", 20, () -> {
            net.minecraft.world.phys.Vec3 eye = mc.player.getEyePosition();
            HudFlights.acceptBonus(new dev.amman.proficiency.net.BonusLootPayload(List.of(
                    new dev.amman.proficiency.net.BonusLootPayload.Entry("minecraft:diamond", 2,
                            (float) eye.x - 1.5f, (float) eye.y - 1.0f, (float) eye.z + 4f),
                    new dev.amman.proficiency.net.BonusLootPayload.Entry("minecraft:cobblestone", 3,
                            (float) eye.x + 2f, (float) eye.y - 1.4f, (float) eye.z + 3f),
                    new dev.amman.proficiency.net.BonusLootPayload.Entry("minecraft:cooked_cod", 1,
                            (float) eye.x, (float) eye.y - 0.5f, (float) eye.z - 3f),
                    new dev.amman.proficiency.net.BonusLootPayload.Entry("minecraft:emerald", 1,
                            (float) eye.x + 30f, (float) eye.y + 2f, (float) eye.z + 8f))));
        });
        for (int i = 1; i <= 9; i++) {
            shotEvery(mc, "hf-bonus-f" + i, 30);
        }
        add("hf bonus settle", 600, () -> {
        });
        // 2 The brazier at its counts, then the coin in flight and landing.
        add("hf brazier 2", 700, () -> HudFlights.demoPoints(2));
        shot(mc, "hf-brazier-2");
        add("hf coin", 10, () -> {
            HudFlights.demoPoints(3);
            HudFlights.onLevelUp(Skill.MINING, 60, 61, mc.getWindow().getGuiScaledWidth(),
                    mc.getWindow().getGuiScaledHeight());
        });
        for (int i = 1; i <= 12; i++) {
            shotEvery(mc, "hf-coin-f" + i, 40);
        }
        add("hf brazier 150", 500, () -> HudFlights.demoPoints(150));
        shot(mc, "hf-brazier-99plus");
        add("hf brazier 0", 600, () -> HudFlights.demoPoints(0));
        shot(mc, "hf-brazier-0");
        add("hf f1", 100, () -> {
            HudFlights.demoPoints(5);
            mc.options.hideGui = true;
        });
        add("hf f1 wait", 500, () -> {
        });
        shot(mc, "hf-brazier-f1");
        add("hf f1 off", 400, () -> {
            mc.options.hideGui = false;
            HudFlights.demoPoints(-1);
        });
        // 3 The rested pool: a pool on Mining, a gain to bring the line up, then the pour.
        add("hf rested", 500, () -> command(mc, "proficiency rested @s mining 900"));
        add("hf gain", 300, () -> command(mc, "proficiency addxp @s mining 8"));
        add("hf pour", 10, () -> HudFlights.demoPour(8, System.currentTimeMillis()));
        for (int i = 1; i <= 8; i++) {
            shotEvery(mc, "hf-pour-f" + i, 40);
        }
        // 4 Live: dig stone for real with the attack key held; the server's bonus loot, its rested spend and any
        // level-up drive the effects. The log carries a HUDFLIGHT line for each.
        add("hf live setup", 800, () -> {
            command(mc, "proficiency rested @s mining 900");
            command(mc, "tp @s ~ ~ ~ 0 0");
            command(mc, "gamerule doTileDrops true");
        });
        add("hf live pool", 400, () -> {
            var skills = dev.amman.proficiency.ProficiencyAttachments.of(mc.player);
            LayoutAudit.note("HUDFLIGHT pool mining rested=" + skills.rested(Skill.MINING) + " reach="
                    + skills.restedReach(Skill.MINING) + " cap=" + skills.restedCap(Skill.MINING));
        });
        for (int round = 0; round < 40; round++) {
            final int r = round;
            add("hf live block " + r, 380, () -> {
                command(mc, "setblock ~ ~1 ~4 stone");
                if (r == 0) {
                    mc.options.keyAttack.setDown(true);
                }
            });
            if (round % 2 == 1) {
                shotEvery(mc, "hf-live-" + round, 5);
            }
        }
        add("hf live end", 300, () -> mc.options.keyAttack.setDown(false));
    }

    /**
     * The Hearth (docs/HEARTH-SPEC.md, "Finish and polish": every new HUD element and screen passes
     * this audit): the glyph, the tracker and the siege countdown at home; Provisioned, Tales and a wave
     * away; a siege outcome; the panel with a pool tooltip, short and with Shift; the keepers tab; a
     * smouldering Hearth's panel. Fed with the client lane's fake payloads; nothing is sent.
     */
    private static void hearthScenes(Minecraft mc) {
        add("hearth home", 1200, () -> {
            mc.setScreen(null);
            dev.amman.proficiency.hearth.client.HearthDemo.forceTracker(true);
            dev.amman.proficiency.hearth.client.HearthDemo.feed("home");
            dev.amman.proficiency.hearth.client.HearthDemo.feed("countdown");
        });
        shot(mc, "hearth-hud");
        // A homecoming with the tracker on: the tracker steps under the Tales panel where there is room (the
        // pay-in's own tier-up plays there) and fades with it where there is none (RightEdge).
        add("hearth tales", 1500, () -> {
            dev.amman.proficiency.hearth.client.HearthDemo.feed("tierup");
            TalesPayInHud.accept(new dev.amman.proficiency.hearth.net.HearthTalesPayload(
                    dev.amman.proficiency.hearth.net.HearthTalesPayload.PAID,
                    dev.amman.proficiency.hearth.HearthPool.HOMECOMING.ordinal(), 312f, 4_100,
                    -1, List.of("structure.minecraft.village_plains"), ""));
        });
        shot(mc, "hearth-tales");
        add("hearth tales done", 3800, () -> {
        });
        add("hearth away", 900, () -> {
            dev.amman.proficiency.hearth.client.HearthDemo.feed("away");
            dev.amman.proficiency.hearth.client.HearthDemo.feed("waves");
        });
        shot(mc, "hearth-away");
        add("hearth outcome", 700, () -> dev.amman.proficiency.hearth.client.HearthDemo.feed("outcome"));
        shot(mc, "hearth-outcome");
        add("hearth panel", 900, () -> {
            dev.amman.proficiency.hearth.client.HearthDemo.feed("clear");
            dev.amman.proficiency.hearth.client.HearthDemo.feed("home");
            dev.amman.proficiency.hearth.client.HearthDemo.feed("panel");
        });
        shot(mc, "hearth-panel");
        add("hearth tip", 500, () -> {
            int[] p = dev.amman.proficiency.hearth.client.HearthDemo.poolPoint(3);
            if (p != null) {
                mouse(mc, p[0], p[1]);
            }
            TooltipDetail.force(false);
        });
        shot(mc, "hearth-panel-tip");
        add("hearth tip shift", 400, () -> TooltipDetail.force(true));
        shot(mc, "hearth-panel-tip-shift");
        add("hearth tip off", 100, () -> TooltipDetail.force(null));
        add("hearth keepers", 800, () -> {
            dev.amman.proficiency.hearth.client.HearthDemo.openKeepers(-1, false);
            mouse(mc, 2, 2);
        });
        shot(mc, "hearth-keepers");
        add("hearth smoulder", 900, () -> {
            mc.setScreen(null);
            dev.amman.proficiency.hearth.client.HearthDemo.feed("smoulder");
        });
        shot(mc, "hearth-smoulder");
        add("hearth done", 300, () -> {
            mc.setScreen(null);
            dev.amman.proficiency.hearth.client.HearthDemo.feed("clear");
            dev.amman.proficiency.hearth.client.HearthDemo.forceTracker(false);
        });
    }

    /** The Hearth guide: its button on the Homebuilder tree, each tab at home, and the guide away from home. */
    private static void guideScenes(Minecraft mc) {
        add("guide home", 900, () -> {
            dev.amman.proficiency.hearth.client.HearthDemo.feed("clear");
            dev.amman.proficiency.hearth.client.HearthDemo.feed("home");
            mc.setScreen(new TalentTreeScreen(Skill.HOMEBUILDER, null));
            mouse(mc, 2, 2);
        });
        shot(mc, "guide-button");
        add("guide button hover", 500, () -> {
            if (mc.screen instanceof TalentTreeScreen screen) {
                int[] p = screen.guidePoint();
                if (p != null) {
                    mouse(mc, p[0], p[1]);
                }
            }
        });
        shot(mc, "guide-button-tip");
        add("guide build", 900, () -> {
            mc.setScreen(new dev.amman.proficiency.hearth.client.HearthGuideScreen(mc.screen));
            mouse(mc, 2, 2);
        });
        shot(mc, "guide-build");
        // Every lane's Build tab: the top of the list, then scrolled to the later stages.
        for (int lane = 0; lane < dev.amman.proficiency.hearth.stages.HearthDesigns.shipped().lanes().size(); lane++) {
            int index = lane;
            add("guide lane " + lane, 700, () -> {
                if (mc.screen instanceof dev.amman.proficiency.hearth.client.HearthGuideScreen guide) {
                    guide.showLane(index);
                }
                mouse(mc, 2, 2);
            });
            shot(mc, "guide-lane-" + lane);
            add("guide lane " + lane + " lower", 500, () -> {
                if (mc.screen instanceof dev.amman.proficiency.hearth.client.HearthGuideScreen guide) {
                    guide.show(dev.amman.proficiency.hearth.client.HearthGuide.Tab.BUILD, 250);
                }
            });
            shot(mc, "guide-lane-" + lane + "-lower");
        }
        add("guide build rules", 500, () -> {
            if (mc.screen instanceof dev.amman.proficiency.hearth.client.HearthGuideScreen guide) {
                guide.show(dev.amman.proficiency.hearth.client.HearthGuide.Tab.BUILD, 10_000);
            }
        });
        shot(mc, "guide-build-end");
        add("guide stage tip", 500, () -> {
            if (mc.screen instanceof dev.amman.proficiency.hearth.client.HearthGuideScreen guide) {
                guide.show(dev.amman.proficiency.hearth.client.HearthGuide.Tab.BUILD, 0);
            }
            mouse(mc, mc.getWindow().getGuiScaledWidth() / 2, mc.getWindow().getGuiScaledHeight() / 2);
        });
        shot(mc, "guide-build-tip");
        add("guide boons", 500, () -> {
            if (mc.screen instanceof dev.amman.proficiency.hearth.client.HearthGuideScreen guide) {
                guide.show(dev.amman.proficiency.hearth.client.HearthGuide.Tab.BOONS, 0);
            }
            mouse(mc, 2, 2);
        });
        shot(mc, "guide-boons");
        add("guide boons end", 500, () -> {
            if (mc.screen instanceof dev.amman.proficiency.hearth.client.HearthGuideScreen guide) {
                guide.show(dev.amman.proficiency.hearth.client.HearthGuide.Tab.BOONS, 10_000);
            }
        });
        shot(mc, "guide-boons-end");
        add("guide comfort", 500, () -> {
            if (mc.screen instanceof dev.amman.proficiency.hearth.client.HearthGuideScreen guide) {
                guide.show(dev.amman.proficiency.hearth.client.HearthGuide.Tab.COMFORT, 0);
            }
        });
        shot(mc, "guide-comfort");
        add("guide comfort end", 500, () -> {
            if (mc.screen instanceof dev.amman.proficiency.hearth.client.HearthGuideScreen guide) {
                guide.show(dev.amman.proficiency.hearth.client.HearthGuide.Tab.COMFORT, 10_000);
            }
        });
        shot(mc, "guide-comfort-end");
        add("guide away", 700, () -> {
            dev.amman.proficiency.hearth.client.HearthDemo.feed("clear");
            mc.setScreen(new dev.amman.proficiency.hearth.client.HearthGuideScreen(null));
        });
        shot(mc, "guide-away");
        ghostScenes(mc);
        add("guide done", 300, () -> mc.setScreen(null));
    }

    /** The campfire the campstone scenes placed, and the style to put back after them. */
    private static net.minecraft.core.BlockPos campfireAt;
    private static UiStyle campStyle;

    /** The real server's answer to an empty hand on that campfire: its stage-0 panel payload, sent to this client. */
    private static void campfireClick(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        if (server == null || campfireAt == null) {
            return;
        }
        java.util.UUID id = mc.player.getUUID();
        net.minecraft.core.BlockPos at = campfireAt;
        server.execute(() -> {
            net.minecraft.server.level.ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                dev.amman.proficiency.hearth.stages.HearthOfferings.panelSender.accept(player,
                        dev.amman.proficiency.hearth.net.HearthNet.campfireState(player.serverLevel(), at));
            }
        });
    }

    /**
     * A plain campfire's preview (1.34.0): the classic panel under CLASSIC, the stone Hearth face for comparison,
     * then the campfire's own slab page under MODERN with hovers, the build guide switched on and a refused
     * Build it for me (frame sheets on the frozen clock).
     */
    private static void campstoneScenes(Minecraft mc) {
        add("campstone setup", 900, () -> {
            campStyle = dev.amman.proficiency.config.ProficiencyClientConfig.uiStyle();
            dev.amman.proficiency.client.stone.SkillstoneScreen.demoClock(-1);
            mc.setScreen(null);
            command(mc, "gamerule sendCommandFeedback false");
            campfireAt = mc.player.blockPosition().offset(0, 0, 3);
            command(mc, "setblock " + campfireAt.getX() + " " + campfireAt.getY() + " " + campfireAt.getZ() + " campfire");
            command(mc, "tp @s ~ ~ ~ 0 25");
        });
        add("campstone classic", 1200, () -> {
            dev.amman.proficiency.config.ProficiencyClientConfig.setUiStyle(UiStyle.CLASSIC);
            mc.setScreen(null);
            campfireClick(mc);
            mouse(mc, 2, 2);
        });
        add("campstone-classic cursor", 300, () -> mouse(mc, 2, 2));
        shot(mc, "campstone-classic");
        add("campstone hearth", 900, () -> {
            mc.setScreen(null);
            dev.amman.proficiency.config.ProficiencyClientConfig.setUiStyle(UiStyle.MODERN);
            dev.amman.proficiency.hearth.client.HearthDemo.feed("clear");
            dev.amman.proficiency.hearth.client.HearthDemo.feed("home");
            dev.amman.proficiency.hearth.client.HearthDemo.feed("panel");
            dev.amman.proficiency.client.stone.SkillstoneScreen.openHearth();
            mouse(mc, 2, 2);
        });
        shot(mc, "campstone-hearth");
        add("campstone stone", 1200, () -> {
            mc.setScreen(null);
            dev.amman.proficiency.hearth.client.HearthDemo.feed("clear");
            campfireClick(mc);
            mouse(mc, 2, 2);
        });
        add("campstone-campfire cursor", 300, () -> mouse(mc, 2, 2));
        shot(mc, "campstone-campfire");
        add("campstone hover stage", 400, () -> {
            if (mc.screen instanceof dev.amman.proficiency.client.stone.StoneCampfireScreen screen) {
                int[] p = screen.partPoint(0);
                mouse(mc, p[0], p[1]);
            }
        });
        shot(mc, "campstone-hover-stage");
        add("campstone hover pool", 400, () -> {
            if (mc.screen instanceof dev.amman.proficiency.client.stone.StoneCampfireScreen screen) {
                int[] p = screen.partPoint(1);
                mouse(mc, p[0], p[1]);
            }
        });
        shot(mc, "campstone-hover-pool");
        add("campstone hover build", 400, () -> {
            if (mc.screen instanceof dev.amman.proficiency.client.stone.StoneCampfireScreen screen) {
                int[] p = screen.platePoint(3);
                if (p != null) {
                    mouse(mc, p[0], p[1]);
                }
            }
        });
        shot(mc, "campstone-hover-build");
        add("campstone guide freeze", 100, () -> {
            mouse(mc, 2, 2);
            dev.amman.proficiency.client.stone.SkillstoneScreen.demoClock(0);
            if (mc.screen instanceof dev.amman.proficiency.client.stone.StoneCampfireScreen screen) {
                screen.demoPress(0);
            }
        });
        for (long at : new long[] {60, 160, 320}) {
            add("campstone guide " + at, 120, () -> dev.amman.proficiency.client.stone.SkillstoneScreen.demoClock(at));
            shot(mc, "campstone-guide-" + String.format(java.util.Locale.ROOT, "%04d", at));
        }
        add("campstone guide thaw", 700, () -> dev.amman.proficiency.client.stone.SkillstoneScreen.demoClock(-1));
        shot(mc, "campstone-guide-on");
        add("campstone refuse freeze", 100, () -> {
            dev.amman.proficiency.client.stone.SkillstoneScreen.demoClock(0);
            if (mc.screen instanceof dev.amman.proficiency.client.stone.StoneCampfireScreen screen) {
                screen.demoPress(3);
            }
        });
        for (long at : new long[] {30, 90, 200}) {
            add("campstone refuse " + at, 120, () -> dev.amman.proficiency.client.stone.SkillstoneScreen.demoClock(at));
            shot(mc, "campstone-refuse-" + String.format(java.util.Locale.ROOT, "%04d", at));
        }
        add("campstone ghosts", 1500, () -> {
            dev.amman.proficiency.client.stone.SkillstoneScreen.demoClock(-1);
            mc.setScreen(null);
        });
        shot(mc, "campstone-ghosts");
        add("campstone done", 400, () -> {
            dev.amman.proficiency.hearth.client.HearthGhost.off();
            mc.setScreen(null);
            if (campfireAt != null) {
                command(mc, "setblock " + campfireAt.getX() + " " + campfireAt.getY() + " " + campfireAt.getZ() + " air");
            }
            if (campStyle != null) {
                dev.amman.proficiency.config.ProficiencyClientConfig.setUiStyle(campStyle);
            }
        });
    }

    /**
     * The panel's build guide: a forge built to the Hearth's stage on this client only, the Hearth's panel with the guide
     * switched on, and the ghosts of the next stage standing in the world around it.
     */
    private static void ghostScenes(Minecraft mc) {
        // A plain campfire (stage 0): half of stage I built on this client, its panel, then its guide.
        add("campfire build", 2000, () -> {
            mc.setScreen(null);
            command(mc, "gamemode spectator");
            net.minecraft.core.BlockPos at = mc.player.blockPosition().offset(0, 0, 8);
            var lane = dev.amman.proficiency.hearth.stages.HearthDesigns.shipped().lanes().get(1);
            int i = 0;
            for (var cell : lane.stage(1).cells()) {
                net.minecraft.world.level.block.state.BlockState state = cell.role() == 'F'
                        ? net.minecraft.world.level.block.Blocks.CAMPFIRE.defaultBlockState()
                        : cell.role() == '.' || i++ % 2 == 0 ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
                        : dev.amman.proficiency.hearth.stages.StageBlocks.laneState(lane, cell.role());
                mc.level.setBlock(at.offset(cell.dx(), cell.dy(), cell.dz()), state, 3);
            }
            command(mc, "tp @s " + (at.getX() + 0.5) + " " + (at.getY() + 4) + " " + (at.getZ() - 7.5) + " 0 25");
            dev.amman.proficiency.hearth.net.HearthClients.get().onState(
                    new dev.amman.proficiency.hearth.net.HearthStatePayload(
                            dev.amman.proficiency.hearth.net.HearthStatePayload.CAMPFIRE, at.asLong(),
                            mc.level.dimension().location().toString(), 0, 0, false, 0, java.util.List.of(), 0, 0,
                            java.util.List.of(), 0, false, 0, -1f, 24f, 8, 16));
        });
        shot(mc, "campfire-panel");
        add("campfire guide", 1500, () -> {
            var state = dev.amman.proficiency.hearth.client.HearthView.panelState();
            if (state != null && !dev.amman.proficiency.hearth.client.HearthGhost.showing(state.pos())) {
                dev.amman.proficiency.hearth.client.HearthGhost.toggle(state);
            }
            mc.setScreen(null);
        });
        shot(mc, "campfire-ghost");
        add("campfire off", 300, () -> dev.amman.proficiency.hearth.client.HearthGhost.off());
        add("ghost build", 2800, () -> {
            mc.setScreen(null);
            command(mc, "gamemode spectator");
            net.minecraft.core.BlockPos at = mc.player.blockPosition().offset(0, 0, 8);
            dev.amman.proficiency.hearth.client.HearthDemo.at(at, mc.level.dimension().location().toString());
            dev.amman.proficiency.hearth.client.HearthDemo.feed("clear");
            dev.amman.proficiency.hearth.client.HearthDemo.feed("home");
            var shown = dev.amman.proficiency.hearth.client.HearthView.state();
            int built = shown == null ? 2 : Math.max(1, Math.min(5, shown.stage()));
            var lane = dev.amman.proficiency.hearth.stages.HearthDesigns.shipped().lanes().get(1);
            for (var cell : lane.stage(built).cells()) {
                net.minecraft.world.level.block.state.BlockState state = cell.role() == 'F'
                        ? dev.amman.proficiency.hearth.HearthBlocks.HEARTH.get().defaultBlockState()
                                .setValue(dev.amman.proficiency.hearth.HearthBlock.STAGE, built)
                        : cell.role() == '.' ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
                        : dev.amman.proficiency.hearth.stages.StageBlocks.laneState(lane, cell.role());
                mc.level.setBlock(at.offset(cell.dx(), cell.dy(), cell.dz()), state, 3);
            }
            command(mc, "tp @s " + (at.getX() + 0.5) + " " + (at.getY() + 5) + " " + (at.getZ() - 11.5) + " 0 20");
        });
        add("ghost on", 1500, () -> {
            var state = dev.amman.proficiency.hearth.client.HearthView.state();
            if (state != null && !dev.amman.proficiency.hearth.client.HearthGhost.showing(state.pos())) {
                dev.amman.proficiency.hearth.client.HearthGhost.toggle(state);
            }
        });
        shot(mc, "ghost-world");
        add("ghost panel", 900, () -> dev.amman.proficiency.hearth.client.HearthDemo.feed("panel"));
        shot(mc, "ghost-panel");
        add("ghost off", 700, () -> {
            dev.amman.proficiency.hearth.client.HearthGhost.off();
            mc.setScreen(null);
            dev.amman.proficiency.hearth.client.HearthDemo.feed("clear");
            command(mc, "gamemode survival @s");
            command(mc, "tp @s 0 -60 0");
        });
    }

    /** Sets the client setting "always show details" for a scene, in memory only. */
    private static void alwaysDetailed(boolean on) {
        try {
            java.lang.reflect.Field field = dev.amman.proficiency.config.ProficiencyClientConfig.class
                    .getDeclaredField("TOOLTIP_ALWAYS_DETAILED");
            field.setAccessible(true);
            ((net.neoforged.neoforge.common.ModConfigSpec.BooleanValue) field.get(null)).set(on);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LayoutAudit.note("ALWAYS DETAILED FAILED " + e);
        }
    }

    /**
     * The two tooltip layers everywhere: a pickaxe at level 0, 30 and 80 (short, Shift, always on),
     * the Mining row of the skills panel, a talent node, the tree header's numbers and signature
     * move, a synergy, and the ability wheel.
     */
    private static void tipScenes(Minecraft mc) {
        add("tip inventory", 700, () -> {
            mc.options.keyShift.setDown(false);
            mc.setScreen(new InventoryScreen(mc.player));
        });
        for (int level : new int[] {0, 30, 80}) {
            add("tip level " + level, 700, () -> command(mc, "proficiency set @s mining " + level));
            add("tip item", 500, () -> {
                int left = (mc.getWindow().getGuiScaledWidth() - 176) / 2;
                int top = (mc.getWindow().getGuiScaledHeight() - 166) / 2;
                mouse(mc, left + 8 + 18 + 8, top + 142 + 8);
                TooltipDetail.force(false);
            });
            shot(mc, "tip-item-L" + level + "-short");
            add("tip item shift", 400, () -> TooltipDetail.force(true));
            shot(mc, "tip-item-L" + level + "-shift");
            add("tip item always", 400, () -> {
                TooltipDetail.force(null);
                alwaysDetailed(true);
            });
            shot(mc, "tip-item-L" + level + "-always");
            add("tip item off", 100, () -> alwaysDetailed(false));
        }
        add("tip panel", 700, () -> {
            command(mc, "proficiency set @s mining 30");
            mc.setScreen(new SkillsScreen());
        });
        add("tip panel hover", 500, () -> {
            if (mc.screen instanceof SkillsScreen screen) {
                int[] p = screen.rowPoint(Skill.MINING);
                mouse(mc, p[0], p[1]);
                TooltipDetail.force(false);
            }
        });
        shot(mc, "tip-panel-short");
        add("tip panel shift", 400, () -> TooltipDetail.force(true));
        shot(mc, "tip-panel-shift");
        add("tip panel off", 100, () -> TooltipDetail.force(null));
        add("tip tree", 900, () -> mc.setScreen(new TalentTreeScreen(Skill.MINING, null)));
        add("tip node", 500, () -> {
            if (mc.screen instanceof TalentTreeScreen screen) {
                Talent node = Talents.get(Skill.MINING, "vein_miner");
                int[] p = screen.nodePoint(node.row(), node.col());
                mouse(mc, p[0], p[1]);
                TooltipDetail.force(false);
            }
        });
        shot(mc, "tip-node-short");
        add("tip node shift", 400, () -> TooltipDetail.force(true));
        shot(mc, "tip-node-shift");
        add("tip header", 500, () -> {
            if (mc.screen instanceof TalentTreeScreen screen) {
                int[] p = screen.statsPoint(false);
                mouse(mc, p[0], p[1]);
                TooltipDetail.force(false);
            }
        });
        shot(mc, "tip-header-short");
        add("tip header shift", 400, () -> TooltipDetail.force(true));
        shot(mc, "tip-header-shift");
        add("tip proc", 500, () -> {
            if (mc.screen instanceof TalentTreeScreen screen) {
                int[] p = screen.statsPoint(true);
                mouse(mc, p[0], p[1]);
                TooltipDetail.force(false);
            }
        });
        shot(mc, "tip-proc-short");
        add("tip proc shift", 400, () -> TooltipDetail.force(true));
        shot(mc, "tip-proc-shift");
        add("tip synergy", 500, () -> {
            if (mc.screen instanceof TalentTreeScreen screen) {
                int[] p = screen.synergyPoint(1);
                mouse(mc, p[0], p[1]);
                TooltipDetail.force(false);
            }
        });
        shot(mc, "tip-synergy-short");
        add("tip synergy shift", 400, () -> TooltipDetail.force(true));
        shot(mc, "tip-synergy-shift");
        add("tip wheel", 700, () -> {
            TooltipDetail.force(false);
            AbilityWheelScreen wheel = AbilityWheelScreen.forPlayer(mc.player);
            if (wheel != null) {
                mc.setScreen(wheel);
            }
        });
        add("tip wheel hover", 500, () -> {
            if (mc.screen instanceof AbilityWheelScreen wheel) {
                int[] p = wheel.slotPoint(Math.min(3, wheel.slotCount() - 1));
                mouse(mc, p[0], p[1]);
            }
        });
        shot(mc, "tip-wheel-short");
        add("tip wheel shift", 400, () -> TooltipDetail.force(true));
        shot(mc, "tip-wheel-shift");
        // NeoForge builds the settings screen itself, so there is none of ours to shoot.
        add("tip always", 100, () -> alwaysDetailed(false));
        add("tip done", 200, () -> {
            alwaysDetailed(false);
            TooltipDetail.force(null);
            mc.setScreen(null);
        });
    }

    /**
     * The Mining line brought back up by a swap of the held item (no grant, so the server sends no chain of
     * its own), then the chain {@code steps} of 25 fed as the server sends it.
     */
    private static void tempoStep(Minecraft mc, String name, int steps, int permille, int windowTicks, long waitMs) {
        add(name + " swap", 150, () -> mc.player.getInventory().selected = 0);
        add(name, waitMs, () -> {
            mc.player.getInventory().selected = 1;
            TempoBadge.accept(
                    new dev.amman.proficiency.net.TempoPayload(Skill.MINING.ordinal(), steps, 25, permille, windowTicks));
        });
    }

    /** Drops the steps added since {@code mark} when their scene group is not wanted. */
    private static void prune(int mark, String group) {
        if (group.isEmpty() || want(group)) {
            return;
        }
        while (STEPS.size() > mark) {
            STEPS.removeLast();
        }
    }

    private static void feed() {
        List<SkillService.XpFeedGain> gains = new ArrayList<>();
        List<XpFactors.Factor> factors = new ArrayList<>();
        factors.add(new XpFactors.Factor("tempo", 1.3f));
        factors.add(new XpFactors.Factor("streak", 1.15f));
        factors.add(new XpFactors.Factor("perk", 1.25f));
        gains.add(new SkillService.XpFeedGain(Skill.MINING.ordinal(), "block.minecraft.deepslate_diamond_ore",
                12.4f, 8f, factors));
        gains.add(new SkillService.XpFeedGain(Skill.BEASTSLAYING.ordinal(), "entity.minecraft.zombified_piglin",
                3.5f, 3f, List.of()));
        gains.add(new SkillService.XpFeedGain(Skill.SPELUNKING.ordinal(), "first|block.minecraft.sculk_catalyst",
                15f, 15f, List.of()));
        for (SkillService.XpFeedGain gain : gains) {
            XpFeedHud.accept(new XpFeedPayload(List.of(gain)));
        }
    }

    private static void injectLog(Skill skill) {
        List<XpLogPayload.Line> lines = new ArrayList<>();
        String[] sources = {"block.minecraft.deepslate_diamond_ore", "entity.minecraft.zombified_piglin",
                "proficiency.xplog.movement.walk", "first|block.minecraft.sculk_catalyst",
                "block.minecraft.ancient_debris", "entity.minecraft.warden"};
        for (int i = 0; i < sources.length; i++) {
            lines.add(new XpLogPayload.Line(skill.ordinal(), sources[i], 4.5f + i, 4f + i, 1 + i, 5000L * i + 2000,
                    5000L * i + 1000, ""));
        }
        ClientXpLog.accept(new XpLogPayload(List.of(skill.ordinal()), lines));
    }

    /** The Hearth stage name that is widest in the current language. */
    private static String longestHearthStage(Minecraft mc) {
        String best = "proficiency.hearth.stages.name.1";
        int widest = -1;
        for (int stage = 1; stage <= 6; stage++) {
            String key = "proficiency.hearth.stages.name." + stage;
            int width = mc.font.width(Component.translatable(key));
            if (width > widest) {
                widest = width;
                best = key;
            }
        }
        return best;
    }

    private static Skill longestSkill(Minecraft mc) {
        Skill best = Skill.VALUES[0];
        int most = -1;
        for (Skill skill : Skill.VALUES) {
            int w = mc.font.width(Component.translatable(skill.translationKey()));
            if (w > most) {
                most = w;
                best = skill;
            }
        }
        return best;
    }

    private static int longestSlot(Minecraft mc, AbilityWheelScreen wheel) {
        return Math.min(3, wheel.slotCount() - 1);
    }

    /** A HUD payload as the Hearth's server sends it: at home with Warmth 0.6 and the stacks safe, or away. */
    private static dev.amman.proficiency.hearth.net.HearthHudPayload hearthHud(boolean home) {
        return new dev.amman.proficiency.hearth.net.HearthHudPayload(home, 0L, home ? 0.6f : 0f, 0f, 0f, 0L, 0L,
                0f, home ? dev.amman.proficiency.hearth.net.HearthHudPayload.FLAG_STREAK_SAFE : 0);
    }

    private static void mouse(Minecraft mc, int guiX, int guiY) {
        double scale = mc.getWindow().getGuiScale();
        try {
            // The window has no focus on a bare X server, so the real cursor never reaches the game;
            // call the handler GLFW would have called.
            // onMove is method_1600 in a Fabric (intermediary) runtime.
            java.lang.reflect.Method move;
            try {
                move = net.minecraft.client.MouseHandler.class.getDeclaredMethod("onMove", long.class, double.class,
                        double.class);
            } catch (NoSuchMethodException e) {
                move = net.minecraft.client.MouseHandler.class.getDeclaredMethod("method_1600", long.class,
                        double.class, double.class);
            }
            move.setAccessible(true);
            move.invoke(mc.mouseHandler, mc.getWindow().getWindow(), guiX * scale, guiY * scale);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LayoutAudit.note("MOUSE FAILED " + e);
            GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), guiX * scale, guiY * scale);
        }
    }

    /** A fake self-build plan for the stone plan page: three roles, one a fluid, stock in a pack and a chest. */
    private static dev.amman.proficiency.hearth.net.HearthBuildPlanPayload demoPlan() {
        java.util.List<dev.amman.proficiency.hearth.net.HearthBuildPlanPayload.Where> where = java.util.List.of(
                new dev.amman.proficiency.hearth.net.HearthBuildPlanPayload.Where(true, 0, 0, 0, 64),
                new dev.amman.proficiency.hearth.net.HearthBuildPlanPayload.Where(false, 3, 0, -2, 32));
        java.util.List<dev.amman.proficiency.hearth.net.HearthBuildPlanPayload.Option> stone = java.util.List.of(
                new dev.amman.proficiency.hearth.net.HearthBuildPlanPayload.Option("minecraft:stone_bricks", 96, where),
                new dev.amman.proficiency.hearth.net.HearthBuildPlanPayload.Option("minecraft:cobblestone", 210, where),
                new dev.amman.proficiency.hearth.net.HearthBuildPlanPayload.Option("minecraft:deepslate_bricks", 12, where),
                new dev.amman.proficiency.hearth.net.HearthBuildPlanPayload.Option("minecraft:mossy_stone_bricks", 40, where));
        java.util.List<dev.amman.proficiency.hearth.net.HearthBuildPlanPayload.Row> rows = java.util.List.of(
                new dev.amman.proficiency.hearth.net.HearthBuildPlanPayload.Row('S', 64, "minecraft:stone_bricks", stone),
                new dev.amman.proficiency.hearth.net.HearthBuildPlanPayload.Row('W', 18, "minecraft:spruce_log", java.util.List.of(
                        new dev.amman.proficiency.hearth.net.HearthBuildPlanPayload.Option("minecraft:spruce_log", 7, where))),
                new dev.amman.proficiency.hearth.net.HearthBuildPlanPayload.Row('l', 4, "minecraft:lantern", java.util.List.of(
                        new dev.amman.proficiency.hearth.net.HearthBuildPlanPayload.Option("minecraft:lantern", 4, where))),
                new dev.amman.proficiency.hearth.net.HearthBuildPlanPayload.Row('v', 1, "minecraft:water_bucket", java.util.List.of()));
        return new dev.amman.proficiency.hearth.net.HearthBuildPlanPayload(net.minecraft.core.BlockPos.asLong(0, 64, 0), 3, 0,
                false, 2, 5, 0, rows);
    }

    /** A click at a GUI point on the open screen, as the mouse would send it. */
    private static void sheetClick(Minecraft mc, int[] p) {
        mouse(mc, p[0], p[1]);
        if (mc.screen != null) {
            mc.screen.mouseClicked(p[0], p[1], 0);
        }
    }

    /** Moves the cursor over the Skillstone's part {@code kind}/{@code index} from the last frame. */
    private static void stoneHover(Minecraft mc, int kind, int index) {
        if (mc.screen instanceof dev.amman.proficiency.client.stone.SkillstoneScreen screen) {
            int[] p = screen.hitPoint(kind, index);
            if (p != null) {
                mouse(mc, p[0], p[1]);
            }
        }
    }

    /** A click on the Skillstone's part {@code kind}/{@code index} from the last frame. */
    private static void stoneClick(Minecraft mc, int kind, int index) {
        if (mc.screen instanceof dev.amman.proficiency.client.stone.SkillstoneScreen screen) {
            int[] p = screen.hitPoint(kind, index);
            if (p != null) {
                mouse(mc, p[0], p[1]);
                screen.mouseClicked(p[0], p[1], 0);
            }
        }
    }

    /**
     * The milestone ceremonies (PROFICIENCY_LAYOUT_ONLY=milestones): level 10, 50 and 100 as frame sheets at fixed ages
     * of the ceremony, the player at night looking up so the fireworks are in view; then the gold Face bar of a level 100
     * skill. The real path (a level-up packet) is the GameTest and the throwaway-world run; this one plays the same class.
     */
    /** PROFICIENCY_LAYOUT_ONLY=milestones,msface runs just the gold Face bar scene. */
    private static boolean faceOnly() {
        return ONLY != null && ONLY.contains("msface");
    }

    private static void milestoneScenes(Minecraft mc) {
        add("ms setup", 500, () -> {
            mc.setScreen(null);
            command(mc, "time set midnight");
            command(mc, "tp @s ~ ~ ~ 0 -24");
        });
        // 10, 50 and 100 are the dense sequences below; 25 and 75 stay as short sheets.
        Object[][] sheets = {
                {25, Skill.WOODCUTTING, new long[] {500, 1300, 2600}},
                {75, Skill.ARCHERY, new long[] {500, 1200, 2000, 3300}}};
        for (Object[] sheet : faceOnly() ? new Object[0][] : sheets) {
            int tier = (Integer) sheet[0];
            Skill skill = (Skill) sheet[1];
            long[] ages = (long[]) sheet[2];
            add("ms start " + tier, 0, () -> MilestoneCeremony.start(skill, tier, 25, 50),
                    () -> MilestoneCeremony.ageMs() >= ages[0]);
            for (int i = 0; i < ages.length; i++) {
                shot(mc, "ms" + tier + "-" + String.format(java.util.Locale.ROOT, "%04d", ages[i]));
                if (i + 1 < ages.length) {
                    long next = ages[i + 1];
                    add("ms wait " + tier + " " + next, 0, () -> {
                    }, () -> MilestoneCeremony.ageMs() >= next || !MilestoneCeremony.active());
                }
            }
            add("ms end " + tier, 0, () -> {
            }, () -> !MilestoneCeremony.active());
        }
        // Dense sequences for the GIFs: a shot every 250 ms of the ceremony's own clock.
        Object[][] seqs = {{10, Skill.SWORDS}, {50, Skill.MINING}, {100, Skill.ARCHERY}};
        for (Object[] seq : faceOnly() ? new Object[0][] : seqs) {
            int tier = (Integer) seq[0];
            Skill skill = (Skill) seq[1];
            long end = dev.amman.proficiency.client.MilestoneFx.durationMs(tier);
            add("seq start " + tier, 0, () -> MilestoneCeremony.start(skill, tier, 25, 50),
                    () -> MilestoneCeremony.ageMs() >= 100);
            int n = 0;
            for (long at = 100; at < end - 500; at += 250) {
                shot(mc, "seq" + tier + "-" + String.format(java.util.Locale.ROOT, "%04d", n++));
                long next = at + 250;
                add("seq wait " + tier + " " + next, 0, () -> {
                }, () -> MilestoneCeremony.ageMs() >= next || !MilestoneCeremony.active());
            }
            add("seq end " + tier, 0, () -> {
            }, () -> !MilestoneCeremony.active());
        }
        add("ms face", 700, () -> {
            command(mc, "proficiency set @s mining 100");
        });
        add("ms face open", 900, () -> {
            mc.setScreen(new dev.amman.proficiency.client.stone.SkillstoneScreen(Skill.MINING));
            mouse(mc, 2, 2);
        });
        shot(mc, "ms-face-gold");
        add("ms face back", 400, () -> {
            mc.setScreen(null);
            command(mc, "proficiency set @s mining 60");
        });
    }

    /**
     * The unlock slam (PROFICIENCY_LAYOUT_ONLY=unlockslam), in every chosen window setup: Mining at level 60 from an
     * empty tree, the root filled for real (the last rank pays Cobblestone and Coal and wakes the signature and the
     * ability), a dense frame sheet every 40 ms of the slam's frozen clock; then the demo hook on
     * PROFICIENCY_SLAM_SKILL (default woodcutting, its level as the world has it) as a short sheet; then a click
     * skipping a running slam.
     */
    private static void unlockSlamScenes(Minecraft mc) {
        for (int setupIndex = 0; setupIndex < SETUPS.length; setupIndex++) {
            if (SETUP_FILTER != null && !SETUP_FILTER.isEmpty()
                    && !java.util.Arrays.asList(SETUP_FILTER.split(",")).contains(Integer.toString(setupIndex))) {
                continue;
            }
            int[] size = SETUPS[setupIndex];
            String tag = size[0] + "x" + size[1] + "-gui" + (size[2] == 0 ? "auto" : size[2]);
            add("slam window " + tag, 1200, () -> {
                setup = tag;
                mc.setScreen(null);
                GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), size[0], size[1]);
            });
            add("slam scale " + tag, 800, () -> {
                mc.options.guiScale().set(Math.abs(size[2]));
                mc.resizeDisplay();
            });
            add("slam levels", 300, () -> command(mc, "xp add @s 30 levels"));
            add("slam set", 300, () -> command(mc, "proficiency set @s mining 60"));
            add("slam respec", 400, () -> command(mc, "proficiency respec mining"));
            add("slam give cobble", 300, () -> command(mc, "give @s cobblestone 128"));
            add("slam give coal", 300, () -> command(mc, "give @s coal 64"));
            add("slam tree", 800, () -> {
                dev.amman.proficiency.client.stone.SkillstoneScreen.demoClock(-1);
                dev.amman.proficiency.client.stone.SkillstoneScreen screen =
                        new dev.amman.proficiency.client.stone.SkillstoneScreen(Skill.MINING);
                mc.setScreen(screen);
                screen.treeFor(Skill.MINING, null);
                mouse(mc, 2, 2);
            });
            for (int k = 0; k < 4; k++) {
                add("slam root " + k, 500, () -> stoneClick(mc, 7, 0));
            }
            add("slam settle", 900, () -> mouse(mc, 2, 2));
            add("slam freeze", 100, () -> dev.amman.proficiency.client.stone.SkillstoneScreen.demoClock(0));
            add("slam last rank", 900, () -> stoneClick(mc, 7, 0));
            int n = 0;
            for (long at = 0; at <= 2120; at += 40) {
                final long ms = at;
                add("slam " + at, 120, () -> dev.amman.proficiency.client.stone.SkillstoneScreen.demoClock(ms));
                shot(mc, "slam-" + String.format(java.util.Locale.ROOT, "%03d", n++));
            }
            add("slam thaw", 400, () -> dev.amman.proficiency.client.stone.SkillstoneScreen.demoClock(-1));
            shot(mc, "slam-after");
            // The demo hook on a chosen skill: the root's socket rises with its materials, as if just paid.
            String id = System.getenv("PROFICIENCY_SLAM_SKILL");
            Skill pick = id == null || Skill.byId(id) == null ? Skill.WOODCUTTING : Skill.byId(id);
            add("slam hook open", 800, () -> {
                dev.amman.proficiency.client.stone.SkillstoneScreen screen =
                        new dev.amman.proficiency.client.stone.SkillstoneScreen(pick);
                mc.setScreen(screen);
                screen.treeFor(pick, null);
                mouse(mc, 2, 2);
            });
            add("slam hook freeze", 300, () -> {
                dev.amman.proficiency.client.stone.SkillstoneScreen.demoClock(0);
                if (mc.screen instanceof dev.amman.proficiency.client.stone.SkillstoneScreen screen) {
                    screen.demoUnlockSlam();
                }
            });
            for (long at : new long[] {300, 700, 1000, 1300, 1400, 1700}) {
                add("slam hook " + at, 120, () -> dev.amman.proficiency.client.stone.SkillstoneScreen.demoClock(at));
                shot(mc, "slam-hook-" + pick.id() + "-" + String.format(java.util.Locale.ROOT, "%04d", at));
            }
            // A click mid-flight lands it at once.
            add("slam skip start", 300, () -> {
                dev.amman.proficiency.client.stone.SkillstoneScreen.demoClock(0);
                if (mc.screen instanceof dev.amman.proficiency.client.stone.SkillstoneScreen screen) {
                    screen.treeFor(pick, null);
                    screen.demoUnlockSlam();
                }
            });
            add("slam skip mid", 200, () -> dev.amman.proficiency.client.stone.SkillstoneScreen.demoClock(700));
            add("slam skip click", 300, () -> {
                if (mc.screen != null) {
                    mc.screen.mouseClicked(4, 4, 0);
                }
            });
            shot(mc, "slam-skipped");
            add("slam done", 300, () -> {
                dev.amman.proficiency.client.stone.SkillstoneScreen.demoClock(-1);
                mc.setScreen(null);
            });
        }
    }

    /**
     * Spendable now (PROFICIENCY_LAYOUT_ONLY=spend, 1.55.0): the Face cards greyed below their levels, the HUD brazier
     * hidden with one unusable point, a coin that fades, the item badge, the why-stuck hovers, a ready toast fired by
     * picking up the last missing material, the dial's ember markers, the dimmed and glowing tree sockets, and the
     * classic screens.
     */
    private static void spendScenes(Minecraft mc) {
        add("sp setup", 1200, () -> {
            mc.setScreen(null);
            dev.amman.proficiency.config.ProficiencyClientConfig.setUiStyle(UiStyle.MODERN);
            command(mc, "gamerule sendCommandFeedback false");
            command(mc, "gamemode survival @s");
            command(mc, "clear @s");
            command(mc, "proficiency reset @s");
            command(mc, "tp @s ~ ~ ~ 0 6");
            mc.player.getInventory().selected = 1;
            mc.gui.getChat().clearMessages(false);
            HudFlights.demoPoints(-1);
            HudFlights.demoMaterials(-1);
            SpendableClient.demoToasts(true);
        });
        // A: Mining 9 with four root ranks: 1 point left, the last rank costs 2. Nothing ready: no brazier.
        add("sp mining 9", 500, () -> command(mc, "proficiency set @s mining 9"));
        for (int k = 0; k < 4; k++) {
            add("sp root " + k, 300, () -> command(mc, "proficiency unlock mining pickwork"));
        }
        add("sp settle a", 2600, () -> {
            SpendableClient.refresh();
            mc.gui.getChat().clearMessages(false);
        });
        shot(mc, "sp-hud-hidden");
        add("sp dud", 10, () -> HudFlights.onLevelUp(Skill.MINING, 8, 9, mc.getWindow().getGuiScaledWidth(),
                mc.getWindow().getGuiScaledHeight()));
        for (int i = 1; i <= 8; i++) {
            shotEvery(mc, "sp-dud-f" + i, 70);
        }
        add("sp face low", 900, () -> {
            mc.setScreen(new dev.amman.proficiency.client.stone.SkillstoneScreen(Skill.MINING));
            mouse(mc, 2, 2);
        });
        shot(mc, "sp-face-locked");
        add("sp face why", 500, () -> stoneHover(mc, 5, 0));
        shot(mc, "sp-face-why-cost");
        // B: Mining 10: 2 points for the filling rank, the items short: the brazier shows the item badge only.
        add("sp mining 10", 600, () -> {
            mc.setScreen(null);
            command(mc, "proficiency set @s mining 10");
            command(mc, "give @s cobblestone 40");
        });
        add("sp settle b", 2600, () -> SpendableClient.refresh());
        shot(mc, "sp-hud-items");
        add("sp face items", 900, () -> {
            mc.setScreen(new dev.amman.proficiency.client.stone.SkillstoneScreen(Skill.MINING));
            mouse(mc, 2, 2);
        });
        add("sp face items why", 500, () -> stoneHover(mc, 5, 0));
        shot(mc, "sp-face-why-items");
        // C: everything but the last cobblestone, then the last one: the ready toast and the brazier lights.
        add("sp almost", 2600, () -> {
            mc.setScreen(null);
            command(mc, "give @s coal 64");
            command(mc, "give @s cobblestone 87");
        });
        add("sp rearm", 300, () -> {
            // The Mining toast of scene A started its 5-minute gap: the demo clears it, so this one shows.
            SpendableClient.demoToasts(true);
            mc.gui.getChat().clearMessages(false);
        });
        shot(mc, "sp-hud-almost");
        add("sp last", 10, () -> command(mc, "give @s cobblestone 1"));
        for (int i = 1; i <= 16; i++) {
            shotEvery(mc, "sp-toast-f" + String.format(java.util.Locale.ROOT, "%02d", i), 140);
        }
        add("sp toast end", 4200, () -> SpendableClient.demoToasts(false));
        shot(mc, "sp-hud-ready");
        // D: the dial: several skills with ranks ready, the markers, the fire well's why-stuck hover (and Shift).
        add("sp others", 1600, () -> {
            command(mc, "proficiency set @s swords 12");
            command(mc, "proficiency set @s woodcutting 30");
            command(mc, "proficiency set @s fishing 7");
            command(mc, "proficiency set @s running 15");
            command(mc, "proficiency set @s axes 1");
            SpendableClient.refresh();
        });
        add("sp dial", 1200, () -> {
            mc.setScreen(new dev.amman.proficiency.client.stone.SkillstoneScreen());
            mouse(mc, 2, 2);
        });
        for (int i = 1; i <= 8; i++) {
            shotEvery(mc, "sp-dial-f" + i, 150);
        }
        add("sp dial why", 500, () -> {
            if (mc.screen instanceof dev.amman.proficiency.client.stone.SkillstoneScreen screen) {
                int[] c = screen.dialCentre();
                mouse(mc, c[0], c[1]);
            }
        });
        shot(mc, "sp-dial-why");
        add("sp dial why shift", 500, () -> TooltipDetail.force(true));
        shot(mc, "sp-dial-why-shift");
        add("sp dial unshift", 100, () -> TooltipDetail.force(null));
        // E: Faces: signature waiting for the root (Woodcutting 30), and Mastery with no star (Swords 100, tree full).
        add("sp face root", 900, () -> {
            mc.setScreen(new dev.amman.proficiency.client.stone.SkillstoneScreen(Skill.WOODCUTTING));
            mouse(mc, 2, 2);
        });
        shot(mc, "sp-face-root");
        add("sp swords 100", 900, () -> {
            mc.setScreen(null);
            command(mc, "proficiency set @s swords 100");
            command(mc, "proficiency filltree @s swords");
        });
        add("sp face mastery", 900, () -> {
            mc.setScreen(new dev.amman.proficiency.client.stone.SkillstoneScreen(Skill.SWORDS));
            mouse(mc, 2, 2);
        });
        shot(mc, "sp-face-mastery");
        // F: the stone tree: root full, Efficiency and Prospector full at Mining 31 (1 point: "1/2 pts", "Lv 60"),
        // then Mining 32 (2 points: the open sockets glow).
        add("sp root fill", 600, () -> {
            mc.setScreen(null);
            command(mc, "proficiency unlock mining pickwork");
            command(mc, "proficiency set @s mining 31");
        });
        for (int k = 0; k < 5; k++) {
            add("sp eff " + k, 250, () -> {
                command(mc, "proficiency unlock mining efficiency");
                command(mc, "proficiency unlock mining prospector");
            });
        }
        add("sp tree", 1200, () -> {
            SpendableClient.refresh();
            dev.amman.proficiency.client.stone.SkillstoneScreen screen =
                    new dev.amman.proficiency.client.stone.SkillstoneScreen(Skill.MINING);
            mc.setScreen(screen);
            screen.treeFor(Skill.MINING, null);
            mouse(mc, 2, 2);
        });
        shot(mc, "sp-tree-dim");
        add("sp mining 32", 1600, () -> command(mc, "proficiency set @s mining 32"));
        for (int i = 1; i <= 4; i++) {
            shotEvery(mc, "sp-tree-ready-f" + i, 200);
        }
        // G: the classic screens: the list's markers, the tree's dim and glowing nodes, a locked signature and rider.
        add("sp classic", 900, () -> {
            dev.amman.proficiency.config.ProficiencyClientConfig.setUiStyle(UiStyle.CLASSIC);
            mc.setScreen(new SkillsScreen());
            mouse(mc, 2, 2);
        });
        shot(mc, "sp-classic-list");
        add("sp classic tree", 900, () -> mc.setScreen(new TalentTreeScreen(Skill.MINING, null)));
        shot(mc, "sp-classic-tree-ready");
        add("sp classic tree dim", 1600, () -> command(mc, "proficiency set @s mining 31"));
        shot(mc, "sp-classic-tree-dim");
        add("sp classic sig", 900, () -> mc.setScreen(new TalentTreeScreen(Skill.WOODCUTTING, null)));
        shot(mc, "sp-classic-sig-locked");
        add("sp classic rider", 900, () -> mc.setScreen(new TalentTreeScreen(Skill.SWORDS, null)));
        shot(mc, "sp-classic-rider-locked");
        add("sp done", 400, () -> {
            dev.amman.proficiency.config.ProficiencyClientConfig.setUiStyle(UiStyle.MODERN);
            mc.setScreen(null);
        });
    }

    /**
     * The "Needed" block on item tooltips (PROFICIENCY_LAYOUT_ONLY=mattips): a lodestone (needed only later: no lines by
     * default), then leather (an unlockable root plus later nodes) by default and with Shift.
     */
    private static void matTipScenes(Minecraft mc) {
        add("mt setup", 900, () -> {
            mc.setScreen(null);
            command(mc, "clear @s");
            command(mc, "proficiency set @s homebuilder 70");
            command(mc, "proficiency set @s riding 65");
            command(mc, "proficiency set @s unarmed 20");
            command(mc, "proficiency set @s axes 70");
            command(mc, "give @s minecraft:lodestone 1");
            command(mc, "give @s minecraft:leather 5");
        });
        add("mt open", 900, () -> mc.setScreen(new InventoryScreen(mc.player)));
        add("mt hover one", 600, () -> {
            int left = (mc.getWindow().getGuiScaledWidth() - 176) / 2;
            int top = (mc.getWindow().getGuiScaledHeight() - 166) / 2;
            mouse(mc, left + 8 + 8, top + 142 + 8);
            SkillTooltip.forceShift(false);
        });
        shot(mc, "mt-later-only");
        add("mt shift one", 600, () -> SkillTooltip.forceShift(true));
        shot(mc, "mt-later-only-shift");
        add("mt unshift one", 100, () -> SkillTooltip.forceShift(false));
        add("mt hover now", 600, () -> {
            int left = (mc.getWindow().getGuiScaledWidth() - 176) / 2;
            int top = (mc.getWindow().getGuiScaledHeight() - 166) / 2;
            mouse(mc, left + 8 + 18 + 8, top + 142 + 8);
        });
        shot(mc, "mt-now-later");
        add("mt shift", 600, () -> SkillTooltip.forceShift(true));
        shot(mc, "mt-now-later-shift");
        add("mt unshift", 100, () -> SkillTooltip.forceShift(null));
    }

    /**
     * Combat Tempo in a real fight (PROFICIENCY_LAYOUT_ONLY=combattempo, 1.49.0): the integrated server's player swings
     * a diamond sword at a still husk through the real attack path (crit event, damage, the Swords grant, the Tempo
     * packet), pauses 5 s, swings again, earns a Courage grant that rides the sword chain, then lands a vanilla jump
     * crit (the white flare) and waits 10 s. The hover lines are logged as the panel would show them.
     */
    private static UiStyle ctStyle;
    private static String ctPins;

    private static void combatTempoScenes(Minecraft mc) {
        add("ct belt", 300, () -> {
            // The belt beside the hotbar shows each pinned skill's fire: Courage's is the one it rides.
            ctStyle = dev.amman.proficiency.config.ProficiencyClientConfig.uiStyle();
            ctPins = dev.amman.proficiency.config.ProficiencyClientConfig.uiWaywheelPins();
            dev.amman.proficiency.config.ProficiencyClientConfig.setUiStyle(UiStyle.MODERN);
            dev.amman.proficiency.config.ProficiencyClientConfig.setUiWaywheelPins("swords,courage,endurance");
        });
        add("ct setup", 600, () -> {
            mc.setScreen(null);
            mc.gui.getChat().clearMessages(false);
            command(mc, "gamerule sendCommandFeedback false");
            command(mc, "difficulty easy");
            command(mc, "gamemode survival @s");
            command(mc, "time set noon");
            command(mc, "item replace entity @s weapon.mainhand with diamond_sword");
            command(mc, "tp @s ~ ~ ~ 0 12");
            // Bars part full, so the fire has room to show on the fill.
            command(mc, "proficiency set @s swords 40");
            command(mc, "proficiency set @s courage 30");
        });
        add("ct fill", 600, () -> {
            command(mc, "proficiency addxp @s swords 400");
            command(mc, "proficiency addxp @s courage 120");
            mc.gui.getChat().clearMessages(false);
        });
        add("ct husk", 900, () -> combatServer(mc, (sp, level) -> {
            for (net.minecraft.world.entity.Entity old : level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                    sp.getBoundingBox().inflate(16), e -> e.getTags().contains("proficiency_ct"))) {
                old.discard();
            }
            // Created directly, never finalized: no spawn-origin tag, so it pays like a wild mob.
            net.minecraft.world.entity.monster.Husk husk = net.minecraft.world.entity.EntityType.HUSK.create(level);
            if (husk == null) {
                return;
            }
            husk.moveTo(sp.getX(), sp.getY(), sp.getZ() + 2.2, 180f, 0f);
            husk.setNoAi(true);
            husk.setPersistenceRequired();
            husk.addTag("proficiency_ct");
            // Undead ignore Regeneration: a deep health pool, topped up before every swing, keeps it standing.
            var maxHealth = husk.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
            if (maxHealth != null) {
                maxHealth.setBaseValue(1000.0);
            }
            husk.setHealth(husk.getMaxHealth());
            level.addFreshEntity(husk);
        }));
        for (int i = 1; i <= 5; i++) {
            add("ct hit " + i, 1000, () -> combatHit(mc, false));
        }
        shot(mc, "ct1-swords-chain");
        add("ct pause a", 2250, () -> {
        });
        shot(mc, "ct2-pause-2.5s");
        add("ct pause b", 2250, () -> {
        });
        shot(mc, "ct3-pause-5s");
        add("ct hit after pause", 350, () -> combatHit(mc, false));
        shot(mc, "ct4-after-5s-pause");
        add("ct courage", 400, () -> combatServer(mc, (sp, level) ->
                SkillService.grant(sp, Skill.COURAGE, 2.0, "entity.minecraft.husk")));
        shot(mc, "ct5-courage-rides");
        add("ct courage panel", 700, () -> {
            dev.amman.proficiency.config.ProficiencyClientConfig.setUiStyle(UiStyle.CLASSIC);
            mc.setScreen(new SkillsScreen());
        });
        add("ct courage hover", 500, () -> {
            if (mc.screen instanceof SkillsScreen screen) {
                int[] p = screen.rowPoint(Skill.COURAGE);
                mouse(mc, p[0], p[1]);
            }
        });
        shot(mc, "ct5b-courage-hover");
        add("ct courage panel close", 300, () -> {
            mc.setScreen(null);
            dev.amman.proficiency.config.ProficiencyClientConfig.setUiStyle(UiStyle.MODERN);
        });
        add("ct hover lines", 50, () -> {
            for (Skill s : new Skill[] {Skill.SWORDS, Skill.COURAGE, Skill.ENDURANCE, Skill.MINING}) {
                StringBuilder text = new StringBuilder();
                for (net.minecraft.network.chat.Component line : TempoBadge.tooltip(s)) {
                    text.append(line.getString());
                }
                LayoutAudit.note("TEMPO HOVER " + s.id() + ": " + text);
            }
        });
        add("ct before crit", 900, () -> combatHit(mc, false));
        add("ct crit", 40, () -> combatHit(mc, true));
        for (int i = 0; i < 6; i++) {
            shot(mc, "ct6-crit-" + i);
        }
        add("ct crit held", 8500, () -> {
        });
        shot(mc, "ct7-crit-held-10s");
        add("ct hover after crit", 50, () -> {
            StringBuilder text = new StringBuilder();
            for (net.minecraft.network.chat.Component line : TempoBadge.tooltip(Skill.SWORDS)) {
                text.append(line.getString());
            }
            LayoutAudit.note("TEMPO HOVER after crit swords: " + text);
        });
        add("ct belt back", 100, () -> {
            if (ctStyle != null) {
                dev.amman.proficiency.config.ProficiencyClientConfig.setUiStyle(ctStyle);
                dev.amman.proficiency.config.ProficiencyClientConfig.setUiWaywheelPins(ctPins == null ? "" : ctPins);
            }
        });
        add("ct clean", 300, () -> combatServer(mc, (sp, level) -> {
            for (net.minecraft.world.entity.Entity old : level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                    sp.getBoundingBox().inflate(16), e -> e.getTags().contains("proficiency_ct"))) {
                old.discard();
            }
        }));
    }

    /**
     * The recent-skills stack (PROFICIENCY_LAYOUT_ONLY=hudstack, 1.54.0) in real play: the client's own keys run,
     * sprint and jump on the flat world (the server pays Running and Jumping as for a player), a sword hits a still
     * husk through the client's attack path, and the Running row crosses a gold level. The harness films it
     * (HS CAPTURE START/STOP in demo.log); "HS MARK" lines time each part, "HS STATE" lines log the line and rows.
     */
    private static UiStyle hsStyle;
    private static String hsPins;
    private static long hsT0;

    private static java.util.concurrent.ExecutorService hsWriter;
    private static final java.util.concurrent.atomic.AtomicInteger HS_QUEUED = new java.util.concurrent.atomic.AtomicInteger();
    private static int hsFrame;
    private static Path hsDir;

    /**
     * Films the HUD for the GIF: every drawn frame (on the real clock, so the animation keeps its true speed and
     * frames are dropped rather than slowed) is cut to the bottom-centre 960x480 window pixels and written as a PNG
     * on a writer thread; the file name carries the frame's time. {@code false} stops.
     */
    private static void hsFilm(boolean on) {
        if (on) {
            hsDir = LayoutAudit.dir().resolve("film");
            try {
                Files.createDirectories(hsDir);
            } catch (java.io.IOException e) {
                LayoutAudit.note("HS FILM FAILED " + e);
                return;
            }
            hsFrame = 0;
            hsWriter = java.util.concurrent.Executors.newFixedThreadPool(3);
            LayoutAudit.note("HS CAPTURE START " + System.currentTimeMillis());
            HudStackRows.filmSink = LayoutDemo::hsShoot;
        } else {
            HudStackRows.filmSink = null;
            if (hsWriter != null) {
                hsWriter.shutdown();
                try {
                    hsWriter.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            LayoutAudit.note("HS CAPTURE STOP " + System.currentTimeMillis() + " frames " + hsFrame);
        }
    }

    private static void hsShoot(Minecraft mc) {
        if (HS_QUEUED.get() >= 6) {
            return;
        }
        NativeImage full = Screenshot.takeScreenshot(mc.getMainRenderTarget());
        int w = Math.min(960, full.getWidth());
        int h = Math.min(480, full.getHeight());
        int x0 = Math.max(0, Math.min(full.getWidth() - w, full.getWidth() / 2 - 420));
        int y0 = full.getHeight() - h;
        NativeImage cut = new NativeImage(w, h, false);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                cut.setPixelRGBA(x, y, full.getPixelRGBA(x0 + x, y0 + y));
            }
        }
        full.close();
        Path file = hsDir.resolve(String.format("%05d-%d.png", ++hsFrame, System.currentTimeMillis()));
        HS_QUEUED.incrementAndGet();
        hsWriter.execute(() -> {
            try {
                cut.writeToFile(file);
            } catch (java.io.IOException e) {
                LayoutAudit.note("HS FRAME FAILED " + e);
            } finally {
                cut.close();
                HS_QUEUED.decrementAndGet();
            }
        });
    }

    private static void hsMark(Minecraft mc, String name) {
        add("hs mark " + name, 10, () -> LayoutAudit.note("HS MARK " + name + " " + System.currentTimeMillis()));
    }

    private static void hsState(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            boolean sprint = mc.player.isSprinting();
            boolean ground = mc.player.onGround();
            combatServer(mc, (sp, level) -> LayoutAudit.note("HS MOVE " + name + " client sprint=" + sprint + " ground="
                    + ground + " server sprint=" + sp.isSprinting() + " ground=" + sp.onGround() + " z="
                    + Math.round(sp.getZ() * 10) / 10.0 + " running=" + dev.amman.proficiency.ProficiencyAttachments.of(sp)
                    .barProgress(Skill.RUNNING)));
        }
        LayoutAudit.note("HS STATE " + name + " t=" + System.currentTimeMillis() + " main="
                + (HudStackRows.mainSkill() == null ? "-" : HudStackRows.mainSkill().id()) + " rows=["
                + HudStackRows.describe() + "]");
    }

    private static void hsKeys(Minecraft mc, boolean forward, boolean sprint, boolean jump) {
        mc.options.keyUp.setDown(forward);
        mc.options.keySprint.setDown(sprint);
        mc.options.keyJump.setDown(jump);
    }

    /**
     * {@code ms} of running with the keys held: a jump every 900 ms, a sword hit every 1000 ms when {@code hit}.
     * A step is 100 ms; the husk is moved beside the runner 150 ms before each hit.
     */
    private static void hsRun(Minecraft mc, String name, long ms, boolean jump, boolean hit) {
        add("hs run start " + name, 10, () -> hsT0 = System.currentTimeMillis());
        for (long t = 0; t < ms; t += 100) {
            final long at = t;
            add("hs run " + name + " " + t, 100, () -> {
                long e = System.currentTimeMillis() - hsT0;
                // Right after a hit the keys let go for one step and press again: a sprint hit stops the sprint on
                // the server, and vanilla only tells the server about a new sprint when the client's own flag changes.
                boolean release = hit && at % 1000 == 600;
                hsKeys(mc, !release, !release, !release && jump && e % 900 < 160);
                if (hit && at % 1000 == 300) {
                    hsHuskBeside(mc);
                }
                if (hit && at % 1000 == 500) {
                    hsSwing(mc);
                }
                if (at % 500 == 0) {
                    hsState(name);
                }
            });
        }
        add("hs run stop " + name, 300, () -> hsKeys(mc, false, false, false));
    }

    /** Moves the test husk just ahead of the runner and to the side, health full. */
    private static void hsHuskBeside(Minecraft mc) {
        combatServer(mc, (sp, level) -> {
            for (net.minecraft.world.entity.LivingEntity husk : level.getEntitiesOfClass(
                    net.minecraft.world.entity.LivingEntity.class, sp.getBoundingBox().inflate(512),
                    e -> e.getTags().contains("proficiency_hs"))) {
                husk.teleportTo(sp.getX() + 1.1, sp.getY(), sp.getZ() + 2.6);
                husk.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                husk.setHealth(husk.getMaxHealth());
            }
        });
    }

    /** A real swing from the client: the attack packet, the server's damage and the Swords grant. */
    private static void hsSwing(Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            return;
        }
        List<net.minecraft.world.entity.monster.Husk> husks = mc.level.getEntitiesOfClass(
                net.minecraft.world.entity.monster.Husk.class, mc.player.getBoundingBox().inflate(5));
        if (husks.isEmpty()) {
            LayoutAudit.note("HS no husk in reach");
            return;
        }
        mc.gameMode.attack(mc.player, husks.get(0));
        mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
    }

    private static void hudStackScenes(Minecraft mc) {
        add("hs setup", 700, () -> {
            mc.setScreen(null);
            mc.options.pauseOnLostFocus = false;
            hsStyle = dev.amman.proficiency.config.ProficiencyClientConfig.uiStyle();
            hsPins = dev.amman.proficiency.config.ProficiencyClientConfig.uiWaywheelPins();
            dev.amman.proficiency.config.ProficiencyClientConfig.setUiStyle(UiStyle.MODERN);
            dev.amman.proficiency.config.ProficiencyClientConfig.setUiWaywheelPins("swords,running,jumping");
            dev.amman.proficiency.config.ProficiencyClientConfig.setSessionHudStack(true);
            command(mc, "gamerule sendCommandFeedback false");
            command(mc, "gamerule doDaylightCycle false");
            command(mc, "difficulty easy");
            command(mc, "gamemode survival @s");
            command(mc, "time set noon");
            command(mc, "effect give @s saturation infinite 0 true");
            command(mc, "gamerule doMobSpawning false");
            command(mc, "kill @e[type=minecraft:slime]");
            command(mc, "item replace entity @s hotbar.0 with diamond_sword");
            command(mc, "tp @s ~ ~ ~ 0 20");
            command(mc, "proficiency set @s swords 41");
            command(mc, "proficiency set @s running 29");
            command(mc, "proficiency set @s jumping 14");
            command(mc, "proficiency set @s courage 30");
            mc.player.getInventory().selected = 0;
        });
        add("hs fill", 700, () -> {
            command(mc, "proficiency addxp @s swords " + Math.round(dev.amman.proficiency.skill.SkillMath.xpToNext(41) * 0.35));
            command(mc, "proficiency addxp @s running " + Math.round(dev.amman.proficiency.skill.SkillMath.xpToNext(29) * 0.3));
            command(mc, "proficiency addxp @s jumping " + Math.round(dev.amman.proficiency.skill.SkillMath.xpToNext(14) * 0.4));
            mc.gui.getChat().clearMessages(false);
        });
        add("hs husk", 900, () -> combatServer(mc, (sp, level) -> {
            for (net.minecraft.world.entity.Entity old : level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                    sp.getBoundingBox().inflate(64), e -> e.getTags().contains("proficiency_hs"))) {
                old.discard();
            }
            net.minecraft.world.entity.monster.Husk husk = net.minecraft.world.entity.EntityType.HUSK.create(level);
            if (husk == null) {
                return;
            }
            husk.moveTo(sp.getX() + 1.1, sp.getY(), sp.getZ() + 2.6, 180f, 0f);
            husk.setNoAi(true);
            husk.setPersistenceRequired();
            husk.addTag("proficiency_hs");
            var maxHealth = husk.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
            if (maxHealth != null) {
                maxHealth.setBaseValue(1000.0);
            }
            husk.setHealth(husk.getMaxHealth());
            level.addFreshEntity(husk);
        }));
        add("hs capture", 1500, () -> hsFilm(true));

        // (a) Sword out, sprinting, jumping and hitting: Swords on the line, Running and Jumping in rows.
        hsMark(mc, "a-sword-run-jump-hit");
        hsRun(mc, "a", 9000, true, true);
        shot(mc, "hs-a-end");
        add("hs a settle", 1500, () -> hsState("a settled"));

        // (b) Empty hand, running and jumping: the line must stay on one skill.
        add("hs b hand", 600, () -> {
            command(mc, "item replace entity @s hotbar.0 with air");
            command(mc, "tp @s ~ ~ ~ 0 20");
        });
        hsMark(mc, "b-empty-run-jump");
        hsRun(mc, "b", 10000, true, false);
        shot(mc, "hs-b-end");

        // (c) A level-up on a stack row: Running two dozen metres short of a gold level, sword out, sprint on.
        add("hs c setup", 900, () -> {
            command(mc, "item replace entity @s hotbar.0 with diamond_sword");
            command(mc, "tp @s ~ ~ ~ 0 20");
        });
        // Running sits at 29 from the setup: one gain lifts its bar to the last few metres before 30.
        add("hs c fill", 900, () -> combatServer(mc, (sp, level) -> {
            // Small real grants (they carry the streak and Tempo multipliers) until the bar is nearly full.
            var skills = dev.amman.proficiency.ProficiencyAttachments.of(sp);
            int was = skills.level(Skill.RUNNING);
            float step = dev.amman.proficiency.skill.SkillMath.xpToNext(was) * 0.002f;
            for (int i = 0; i < 900 && skills.level(Skill.RUNNING) == was && skills.barProgress(Skill.RUNNING) < 0.998f; i++) {
                SkillService.grant(sp, Skill.RUNNING, step, "proficiency.xplog.source.command");
            }
        }));
        hsMark(mc, "c-row-levelup");
        hsRun(mc, "c", 7000, true, true);

        // (d) A fourth skill replaces the row idle longest; then everything lingers out and the rows slide down.
        hsMark(mc, "d-fourth-and-linger");
        add("hs d courage", 400, () -> combatServer(mc, (sp, level) ->
                SkillService.grant(sp, Skill.COURAGE, 2.0, "entity.minecraft.husk")));
        add("hs d mining", 900, () -> combatServer(mc, (sp, level) ->
                SkillService.grant(sp, Skill.MINING, 3.0, "block.minecraft.stone")));
        add("hs d state", 10, () -> hsState("d after fourth"));
        add("hs d wait", 6500, () -> hsState("d lingering"));
        add("hs d end", 10, () -> hsState("d end"));

        // (e) The toggle off: exactly the old single line.
        add("hs e off", 300, () -> {
            dev.amman.proficiency.config.ProficiencyClientConfig.setSessionHudStack(false);
            command(mc, "item replace entity @s hotbar.0 with air");
        });
        hsMark(mc, "e-stack-off");
        hsRun(mc, "e", 6000, true, false);
        add("hs capture stop", 500, () -> hsFilm(false));
        add("hs back", 300, () -> {
            dev.amman.proficiency.config.ProficiencyClientConfig.setSessionHudStack(null);
            if (hsStyle != null) {
                dev.amman.proficiency.config.ProficiencyClientConfig.setUiStyle(hsStyle);
                dev.amman.proficiency.config.ProficiencyClientConfig.setUiWaywheelPins(hsPins == null ? "" : hsPins);
            }
        });
        add("hs clean", 300, () -> combatServer(mc, (sp, level) -> {
            for (net.minecraft.world.entity.Entity old : level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                    sp.getBoundingBox().inflate(64), e -> e.getTags().contains("proficiency_hs"))) {
                old.discard();
            }
        }));
    }

    /**
     * The stack's layout at one window setup (PROFICIENCY_LAYOUT_ONLY=hslayout): three rows over a burning main line,
     * the item name, the action bar, the Waywheel belt, the brazier, then a full chat. "HS AUDIT" checks the stack's
     * column against every one of them in GUI pixels.
     */
    private static void hudStackLayoutScenes(Minecraft mc) {
        add("hsl setup", 600, () -> {
            mc.setScreen(null);
            dev.amman.proficiency.config.ProficiencyClientConfig.setUiStyle(UiStyle.MODERN);
            dev.amman.proficiency.config.ProficiencyClientConfig.setUiWaywheelPins("swords,running,jumping");
            dev.amman.proficiency.config.ProficiencyClientConfig.setSessionHudStack(true);
            dev.amman.proficiency.config.ProficiencyClientConfig.setUiHudStackLingerForDemo(60_000L);
            command(mc, "gamerule sendCommandFeedback false");
            command(mc, "item replace entity @s hotbar.0 with diamond_sword");
            mc.player.getInventory().selected = 0;
            mc.gui.getChat().clearMessages(false);
        });
        add("hsl gains", 700, () -> combatServer(mc, (sp, level) -> {
            SkillService.grant(sp, Skill.RUNNING, 1.0, "proficiency.xplog.source.sprinting");
            SkillService.grant(sp, Skill.JUMPING, 0.4, "proficiency.xplog.source.jumping");
            SkillService.grant(sp, Skill.BEASTSLAYING, 1.0, "entity.minecraft.husk");
            SkillService.grant(sp, Skill.SWORDS, 2.0, "entity.minecraft.husk");
        }));
        add("hsl swords", 500, () -> combatServer(mc, (sp, level) ->
                SkillService.grant(sp, Skill.SWORDS, 2.0, "entity.minecraft.husk")));
        add("hsl name", 200, () -> {
            mc.player.getInventory().selected = 1;
        });
        add("hsl name back", 200, () -> {
            mc.player.getInventory().selected = 0;
            mc.gui.setOverlayMessage(Component.literal("Action bar: a long message from another mod"), false);
        });
        shot(mc, "hsl-stack");
        add("hsl audit", 10, () -> hsAudit(mc, "stack"));
        add("hsl chat", 300, () -> {
            for (int i = 1; i <= 12; i++) {
                mc.gui.getChat().addMessage(Component.literal("<Rowan> chat line " + i + " so the box reaches up the screen"));
            }
        });
        shot(mc, "hsl-chat");
        add("hsl audit chat", 10, () -> hsAudit(mc, "chat"));
        add("hsl back", 300, () -> {
            mc.gui.getChat().clearMessages(false);
            dev.amman.proficiency.config.ProficiencyClientConfig.setUiHudStackLingerForDemo(0L);
        });
    }

    /** Logs the stack's column and whether it meets anything it must clear. */
    private static void hsAudit(Minecraft mc, String scene) {
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        int[] r = HudStackRows.lastRect;
        if (r.length < 4) {
            LayoutAudit.note("HS AUDIT " + setup + " " + scene + " hidden (no column drawn)");
            return;
        }
        List<String> hits = new ArrayList<>();
        int lineTop = SkillHud.lineY(h) - TempoBadge.currentLift();
        if (r[3] > lineTop) {
            hits.add("main label (top " + lineTop + ")");
        }
        // Vanilla: hotbar from h-22, item name at h-59, action bar at h-72..h-63, the Forge line may raise them.
        if (r[3] > h - 72) {
            hits.add("action bar / item name / hotbar zone");
        }
        if (r[0] < 0 || r[1] < 0 || r[2] > w) {
            hits.add("off screen");
        }
        int[] chat = ChatBox.of(mc, h);
        if (chat != null && r[0] < chat[0] && chat[1] < r[3] && chat[2] > r[1]) {
            hits.add("chat (right " + chat[0] + ", top " + chat[1] + ")");
        }
        LayoutAudit.note("HS AUDIT " + setup + " " + scene + " gui " + w + "x" + h + " column " + r[0] + "," + r[1]
                + ".." + r[2] + "," + r[3] + " rows=[" + HudStackRows.describe() + "] "
                + (hits.isEmpty() ? "clear" : "OVERLAP " + String.join("; ", hits)));
    }

    /** Runs {@code action} on the integrated server's thread with this client's server player. */
    private static void combatServer(Minecraft mc,
            java.util.function.BiConsumer<net.minecraft.server.level.ServerPlayer, net.minecraft.server.level.ServerLevel> action) {
        net.minecraft.server.MinecraftServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) {
            LayoutAudit.note("CT no integrated server");
            return;
        }
        java.util.UUID id = mc.player.getUUID();
        server.execute(() -> {
            net.minecraft.server.level.ServerPlayer sp = server.getPlayerList().getPlayer(id);
            if (sp != null) {
                action.accept(sp, sp.serverLevel());
            }
        });
    }

    /** One real swing at the husk; {@code crit} makes it a vanilla jump crit (falling, off the ground). */
    private static void combatHit(Minecraft mc, boolean crit) {
        combatServer(mc, (sp, level) -> {
            var targets = level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                    sp.getBoundingBox().inflate(6), e -> e.getTags().contains("proficiency_ct"));
            if (targets.isEmpty()) {
                LayoutAudit.note("CT no husk");
                return;
            }
            sp.setSprinting(false);
            targets.get(0).setHealth(targets.get(0).getMaxHealth());
            if (crit) {
                sp.fallDistance = 1.5f;
                sp.setOnGround(false);
            }
            sp.attack(targets.get(0));
            sp.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
            if (crit) {
                sp.setOnGround(true);
                sp.fallDistance = 0f;
            }
            LayoutAudit.note("CT hit crit=" + crit + " swords x" + Tempo.preview(sp, Skill.SWORDS) + " courage x"
                    + Tempo.preview(sp, Skill.COURAGE));
        });
    }

    private static void shot(Minecraft mc, String scene) {
        add("shot " + scene, 250, () -> {
            Path dir = LayoutAudit.dir().resolve("shots");
            try {
                Files.createDirectories(dir);
                NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget());
                Path file = dir.resolve(lang + "__" + setup + "__" + scene + ".png");
                image.writeToFile(file);
                image.close();
            } catch (java.io.IOException e) {
                LayoutAudit.note("SHOT FAILED " + scene + " " + e);
            }
        });
    }
}
