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
                    screen.mouseScrolled(10, 10, -1);
                }
                mouse(mc, 2, 2);
            }
        });
        shot(mc, "panel-end");

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
        add("close", 300, () -> mc.setScreen(null));
        add("clips", 10, () -> {
            LayoutAudit.note("CLIPS " + lang + " " + setup + " " + TextFit.clips());
            TextFit.resetClips();
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
