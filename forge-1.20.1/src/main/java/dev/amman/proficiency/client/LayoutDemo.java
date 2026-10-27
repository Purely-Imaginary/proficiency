package dev.amman.proficiency.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.amman.proficiency.net.DeathRecapPayload;
import dev.amman.proficiency.net.DiscoveryPayload;
import dev.amman.proficiency.net.XpFeedPayload;
import dev.amman.proficiency.net.XpLogPayload;
import dev.amman.proficiency.perk.Talent;
import dev.amman.proficiency.perk.Talents;
import dev.amman.proficiency.skill.Skill;
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
            {3840, 2160, 0}, {3840, 2160, 4}};

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
                        .createWorldDimensions());
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
                    screen.mouseScrolled(10, 10, -1);
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
                    screen.mouseScrolled(10, 10, -1);
                }
            }
        });
        shot(mc, "stone-face-scroll");
        add("stone face end", 400, () -> {
            if (mc.screen instanceof dev.amman.proficiency.client.stone.SkillstoneScreen screen) {
                for (int i = 0; i < 40; i++) {
                    screen.mouseScrolled(10, 10, -1);
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
                        screen.mouseScrolled(p[0], p[1], -1);
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
        add("config", 700, () -> mc.setScreen(new ClientConfigScreen(null)));
        shot(mc, "config");
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
        group = "hearth";
        hearthScenes(mc);
        prune(mark, group);
        mark = STEPS.size();
        group = "guide";
        guideScenes(mc);
        prune(mark, group);
        add("close", 300, () -> mc.setScreen(null));
        add("clips", 10, () -> {
            LayoutAudit.note("CLIPS " + lang + " " + setup + " " + TextFit.clips());
            TextFit.resetClips();
        });
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
    @SuppressWarnings("unchecked")
    private static void alwaysDetailed(boolean on) {
        for (dev.amman.proficiency.config.ProficiencyClientConfig.Entry entry
                : dev.amman.proficiency.config.ProficiencyClientConfig.entries()) {
            if (entry.key().equals("tooltip.alwaysDetailed")) {
                ((net.minecraftforge.common.ForgeConfigSpec.ConfigValue<Boolean>) entry.value()).set(on);
            }
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
        add("tip config", 700, () -> {
            TooltipDetail.force(null);
            alwaysDetailed(true);
            mc.setScreen(new ClientConfigScreen(null));
        });
        add("tip config scroll", 400, () -> {
            if (mc.screen instanceof ClientConfigScreen screen) {
                for (int i = 0; i < 18; i++) {
                    screen.mouseScrolled(10, 10, -1);
                }
                mouse(mc, 2, 2);
            }
        });
        shot(mc, "tip-config");
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
            java.lang.reflect.Method move = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findMethod(
                    net.minecraft.client.MouseHandler.class, "m_91561_", long.class, double.class, double.class);
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
