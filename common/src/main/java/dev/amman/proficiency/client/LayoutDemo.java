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
            {960, 720, 3}};

    private record Step(String name, Runnable run, long waitMs, BooleanSupplier until) {
    }

    /** PROFICIENCY_LAYOUT_ONLY=tree,recap limits a run to those scene groups; PROFICIENCY_LAYOUT_SETUPS=0,1 to those window setups. */
    private static final String ONLY = System.getenv("PROFICIENCY_LAYOUT_ONLY");
    private static final String SETUP_FILTER = System.getenv("PROFICIENCY_LAYOUT_SETUPS");

    private static boolean want(String group) {
        return ONLY == null || ONLY.isEmpty() || java.util.Arrays.asList(ONLY.split(",")).contains(group);
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
                String tag = size[0] + "x" + size[1] + "-gui" + (size[2] == 0 ? "auto" : size[2]);
                add("window " + tag, 1200, () -> {
                    setup = tag;
                    GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), size[0], size[1]);
                });
                // The framebuffer follows the window one event poll later, and the GUI scale is
                // worked out from the framebuffer, so set the scale only now.
                add("scale " + tag, 800, () -> {
                    mc.options.guiScale().set(size[2]);
                    mc.resizeDisplay();
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
            java.lang.reflect.Method move = net.minecraft.client.MouseHandler.class.getDeclaredMethod("onMove",
                    long.class, double.class, double.class);
            move.setAccessible(true);
            move.invoke(mc.mouseHandler, mc.getWindow().getWindow(), guiX * scale, guiY * scale);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LayoutAudit.note("MOUSE FAILED " + e);
            GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), guiX * scale, guiY * scale);
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
