package dev.amman.proficiency.hearth.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.amman.proficiency.client.HudKit;
import dev.amman.proficiency.hearth.HearthBlockEntity;
import dev.amman.proficiency.hearth.HearthPool;
import dev.amman.proficiency.hearth.net.HearthClients;
import dev.amman.proficiency.hearth.net.HearthGainPayload;
import dev.amman.proficiency.hearth.net.HearthHudPayload;
import dev.amman.proficiency.hearth.net.HearthLedgerPayload;
import dev.amman.proficiency.hearth.net.HearthPingPayload;
import dev.amman.proficiency.hearth.net.HearthStatePayload;
import dev.amman.proficiency.hearth.net.SiegePanelPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Fake Hearth payloads for the client lane's checks, fed straight into the installed
 * {@code HearthClient} as if the server had sent them. Two users:
 *
 * <ul>
 * <li>The layout demo ({@code client.LayoutDemo}, group "hearth") walks every language and window
 * size through the Hearth's HUD, tracker, panel, keepers tab and siege panel with {@link #feed}.</li>
 * <li>With {@code PROFICIENCY_HEARTH_DEMO=<dir>} in a client's environment, a scripted tour runs once
 * the player is in a world (a real server is fine; it needs operator rights for the few commands),
 * taking a screenshot of each stop into {@code <dir>}. It uses the nearest real Hearth block in the
 * loaded chunks for the range ring and the block's particles.</li>
 * </ul>
 * Developer tool: nothing here runs unless one of those asks for it.
 */
public final class HearthDemo {

    private static final String DIR = System.getenv("PROFICIENCY_HEARTH_DEMO");

    private record Step(String name, long waitMs, Runnable run) {
    }

    private static final Deque<Step> STEPS = new ArrayDeque<>();
    private static boolean started;
    private static long readyAt;
    private static boolean trackerForced;
    private static long pos = BlockPos.asLong(0, 64, 0);
    private static String dimension = "minecraft:overworld";
    /** The last fake ledger fed: while it is the one on show, the panel does not ask the server. */
    private static HearthLedgerPayload fakeLedger;

    private HearthDemo() {
    }

    static boolean trackerForced() {
        return trackerForced;
    }

    /** Shows the tracker whatever the client setting says (demos only). */
    public static void forceTracker(boolean on) {
        trackerForced = on;
    }

    // ---- Fake payloads ------------------------------------------------------------------------------

    private static long gameTime() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? 0 : minecraft.level.getGameTime();
    }

    /**
     * Feeds one fake moment to the client, as if the server had sent it (scenes:
     * {@link HearthDemoData#SCENES}).
     */
    public static void feed(String scene) {
        for (Object payload : HearthDemoData.payloads(scene, pos, dimension, gameTime())) {
            deliver(payload);
        }
    }

    /** One payload to the installed client handler, by type. */
    static void deliver(Object payload) {
        var client = HearthClients.get();
        if (payload instanceof HearthStatePayload state) {
            client.onState(state);
        } else if (payload instanceof HearthHudPayload hud) {
            // The loaders' path: remembered for the skills panel's streak block, then handed on.
            HearthClients.hud(hud);
        } else if (payload instanceof HearthGainPayload gains) {
            client.onGains(gains);
        } else if (payload instanceof HearthLedgerPayload ledger) {
            fakeLedger = ledger;
            client.onLedger(ledger);
        } else if (payload instanceof SiegePanelPayload siege) {
            client.onSiegePanel(siege);
        } else if (payload instanceof HearthPingPayload ping) {
            client.onPing(ping);
        } else if (payload instanceof dev.amman.proficiency.hearth.net.HearthTrackerPayload tracker) {
            client.onTracker(tracker);
        }
    }

    /** True while the ledger on show for this Hearth is a demo's fake one (the panel then asks nobody). */
    public static boolean fakeLedgerAt(long hearth) {
        HearthLedgerPayload shown = HearthView.ledger();
        return shown != null && shown == fakeLedger && shown.pos() == hearth;
    }

    /** The fake Hearth sits here (the layout demo leaves it at the origin; the tour uses a real block). */
    public static void at(BlockPos where, String dim) {
        pos = where.asLong();
        dimension = dim;
    }

    public static void openKeepers(int pool, boolean lifetime) {
        Minecraft.getInstance().setScreen(HearthScreen.keepers(pool, lifetime));
    }

    /** Where to point for the tooltip of a pool row in the open panel, or null. */
    public static int[] poolPoint(int pool) {
        return Minecraft.getInstance().screen instanceof HearthScreen screen ? screen.poolPoint(pool) : null;
    }

    public static int[] keeperPoint() {
        return Minecraft.getInstance().screen instanceof HearthScreen screen ? screen.keeperPoint() : null;
    }

    // ---- The scripted tour --------------------------------------------------------------------------

    static void tick(Minecraft minecraft, long now) {
        if (DIR == null || DIR.isEmpty()) {
            return;
        }
        if (!started) {
            if (minecraft.player == null || minecraft.player.tickCount < 100 || minecraft.screen != null) {
                return;
            }
            // Give whoever set up the scene a minute to put a Hearth down near the player.
            if (nearestHearth(minecraft) == null && minecraft.player.tickCount < 1300) {
                return;
            }
            started = true;
            build(minecraft);
        }
        if (now < readyAt) {
            return;
        }
        Step step = STEPS.poll();
        if (step == null) {
            return;
        }
        try {
            step.run().run();
        } catch (RuntimeException e) {
            note("STEP FAILED " + step.name() + ": " + e);
        }
        readyAt = now + step.waitMs();
    }

    private static void add(String name, long waitMs, Runnable run) {
        STEPS.add(new Step(name, waitMs, run));
    }

    private static void command(Minecraft minecraft, String text) {
        if (minecraft.player != null) {
            minecraft.player.connection.sendCommand(text);
        }
    }

    private static void build(Minecraft mc) {
        BlockPos hearth = nearestHearth(mc);
        String dim = mc.level.dimension().location().toString();
        at(hearth == null ? mc.player.blockPosition() : hearth, dim);
        note("TOUR hearth " + (hearth == null ? "none (fake at the player)" : hearth.toShortString()));
        forceTracker(true);
        BlockPos h = BlockPos.of(pos);
        String stand = (h.getX() + 0.5) + " " + h.getY() + " " + (h.getZ() - 5.5);
        add("quiet", 300, () -> {
            command(mc, "gamerule sendCommandFeedback false");
            command(mc, "gamemode survival");
            mc.getToasts().clear();
        });
        add("stand", 4500, () -> command(mc, "tp @s " + stand + " 0 20"));
        add("select", 300, () -> mc.player.getInventory().selected = pickaxeSlot(mc));
        add("home", 1500, () -> feed("home"));
        shot("01-hud-home");
        add("xp", 150, () -> command(mc, "proficiency addxp @s mining 6"));
        add("gains", 330, () -> feed("gains"));
        shot("02-tracker-dots");
        add("settle", 1500, () -> { });
        shot("03-hud-line");
        add("tierup", 700, () -> feed("tierup"));
        add("tierup wait", 260, () -> { });
        shot("04-tracker-tierup");
        add("full", 1150, () -> feed("full"));
        shot("05-hud-glow");
        add("chat", 600, () -> mc.setScreen(new net.minecraft.client.gui.screens.ChatScreen("")));
        add("chat hover", 500, () -> mouse(mc, HearthHud.hitPoint()));
        shot("05b-hud-tooltip");
        add("chat shift", 400, () -> HudKit.forceDetail(true));
        shot("05c-hud-tooltip-shift");
        add("chat close", 300, () -> {
            HudKit.forceDetail(null);
            mc.setScreen(null);
        });
        add("panel", 900, () -> feed("panel"));
        add("ledger", 300, () -> feed("keepers"));
        shot("06-panel");
        add("panel hover", 500, () -> mouse(mc, poolPoint(HearthPool.WORKSHOP.ordinal())));
        shot("07-panel-tip");
        add("panel shift", 400, () -> HudKit.forceDetail(true));
        shot("08-panel-tip-shift");
        add("unshift", 100, () -> HudKit.forceDetail(null));
        add("sealed", 380, () -> feed("sealed"));
        shot("06b-panel-stage-sealed");
        add("keepers", 700, () -> openKeepers(-1, false));
        add("keepers hover", 400, () -> mouse(mc, keeperPoint()));
        shot("09-keepers");
        add("keepers pool", 600, () -> openKeepers(HearthPool.HEARTY.ordinal(), true));
        shot("10-keepers-pool");
        add("close", 600, () -> mc.setScreen(null));
        add("away", 1200, () -> feed("away"));
        shot("11-hud-away");
        add("countdown", 1600, () -> feed("countdown"));
        shot("12-siege-countdown");
        add("waves", 900, () -> feed("waves"));
        shot("13-siege-waves");
        add("outcome", 1200, () -> feed("outcome"));
        shot("14-siege-outcome");
        add("clear", 1200, () -> feed("clear"));
        add("ping", 900, () -> feed("ping"));
        shot("15-bell-ping");
        add("home again", 1500, () -> feed("home"));
        add("smoulder", 900, () -> feed("smoulder"));
        shot("16-panel-smoulder");
        add("close 2", 400, () -> mc.setScreen(null));
        // The range ring from above, then the block close up at each stage.
        // A spectator floats where it is put: the camera shots must not fall (or die).
        add("spectate", 400, () -> command(mc, "gamemode spectator"));
        add("oblique", 2500, () -> command(mc, "tp @s " + (h.getX() + 0.5) + " " + (h.getY() + 14) + " "
                + (h.getZ() - 16.5) + " 0 38"));
        add("ring", 450, () -> HearthRangeRing.showFor(pos, dimension, net.minecraft.Util.getMillis()));
        shot("17-range-ring");
        add("high", 2000, () -> command(mc, "tp @s " + (h.getX() + 0.5) + " " + (h.getY() + 44) + " "
                + (h.getZ() + 0.5) + " 0 90"));
        add("ring 2", 1000, () -> { });
        shot("18-range-ring-top");
        add("survive", 1200, () -> {
            command(mc, "tp @s " + (h.getX() + 0.5) + " " + (h.getY() + 0.2) + " " + (h.getZ() - 3.2) + " 0 18");
            command(mc, "gamemode survival");
        });
        for (int stage : new int[] {1, 4, 6}) {
            // On this client only: the server's Hearth keeps its real stage (the stages lane would set a
            // setblock's stage back to the ceiling within a second), and the client particles follow the
            // blockstate the client sees. The motes take the colour of the pool fed most this hour (the
            // block's feed): none at I, Workshop's at IV, Hearty's at VI.
            int feed = stage == 1 ? 0 : 1 + (stage == 4 ? HearthPool.WORKSHOP : HearthPool.HEARTY).ordinal();
            add("stage " + stage, 600, () -> mc.level.setBlock(h, dev.amman.proficiency.hearth.HearthBlocks.HEARTH.get()
                    .defaultBlockState().setValue(dev.amman.proficiency.hearth.HearthBlock.STAGE, stage)
                    .setValue(dev.amman.proficiency.hearth.HearthBlock.FEED, feed), 3));
            add("close up " + stage, 2500, () -> command(mc, "tp @s " + (h.getX() + 0.5) + " " + (h.getY() + 0.2) + " "
                    + (h.getZ() - 3.2) + " 0 18"));
            shot("19-block-stage-" + stage);
        }
        // The same moments again, this time sent by the server over the real channels
        // (/proficiency hearth demo, operator only), into a client that forgot everything first.
        String at = " " + h.getX() + " " + h.getY() + " " + h.getZ();
        add("net stand", 1500, () -> {
            mc.setScreen(null);
            HearthClients.leaveWorld();
            command(mc, "tp @s " + stand + " 0 20");
        });
        add("net home", 1800, () -> command(mc, "proficiency hearth demo @s home" + at));
        shot("20-net-home");
        add("net gains", 450, () -> command(mc, "proficiency hearth demo @s gains" + at));
        shot("21-net-gains");
        add("net countdown", 1800, () -> command(mc, "proficiency hearth demo @s countdown" + at));
        shot("22-net-countdown");
        add("net panel", 1500, () -> command(mc, "proficiency hearth demo @s panel" + at));
        shot("23-net-panel");
        add("net close", 300, () -> mc.setScreen(null));
        // The charge lane's tier-up chat line (the demo command sends it first, as the server does): dropped
        // when the tracker plays the moment on the screen, let through when it cannot. Read the client log's
        // [CHAT] lines: one "Fortified reached II" for the hidden-GUI and the tracker-off cases, none for the first.
        add("net tierup", 3500, () -> command(mc, "proficiency hearth demo @s tierup" + at));
        add("net tierup held", 100, () -> note("CHAT tracker on: held " + HearthChat.heldCount()));
        chatShot(mc, "24-net-tierup-tracker-on");
        add("net home 2", 1800, () -> command(mc, "proficiency hearth demo @s home" + at));
        add("net hide", 200, () -> mc.options.hideGui = true);
        add("net tierup hidden", 3500, () -> command(mc, "proficiency hearth demo @s tierup" + at));
        add("net show", 300, () -> {
            mc.options.hideGui = false;
            note("CHAT gui hidden: held " + HearthChat.heldCount());
        });
        chatShot(mc, "25-net-tierup-gui-hidden");
        // A walk out with the GUI hidden: home, F1, leave, wait past the tracker's linger, F1 again. The
        // tracker stays gone (its home clock ran while hidden) instead of lingering as if you had just left.
        add("net home 2b", 1500, () -> command(mc, "proficiency hearth demo @s home" + at));
        add("net hide 2", 300, () -> mc.options.hideGui = true);
        add("net away hidden", TrackerPresence.LINGER_MS + TrackerPresence.FADE_OUT_MS + 1000,
                () -> command(mc, "proficiency hearth demo @s away" + at));
        add("net show 2", 400, () -> mc.options.hideGui = false);
        shot("25b-net-left-gui-hidden");
        add("net home 3", 1800, () -> command(mc, "proficiency hearth demo @s home" + at));
        add("net tracker off", 200, () -> forceTracker(false));
        add("net tierup off", 1500, () -> command(mc, "proficiency hearth demo @s tierup" + at));
        add("net tierup off held", 100, () -> note("CHAT tracker off: held " + HearthChat.heldCount()));
        chatShot(mc, "26-net-tierup-tracker-off");
        add("done", 100, () -> {
            // Nothing forced outlives the tour, whatever step it last got through.
            forceTracker(false);
            HudKit.forceDetail(null);
            note("DONE");
        });
    }

    private static int pickaxeSlot(Minecraft mc) {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).is(net.minecraft.tags.ItemTags.PICKAXES)) {
                return i;
            }
        }
        return mc.player.getInventory().selected;
    }

    private static BlockPos nearestHearth(Minecraft mc) {
        BlockPos at = mc.player.blockPosition();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        int cx = at.getX() >> 4;
        int cz = at.getZ() >> 4;
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                LevelChunk chunk = mc.level.getChunkSource().getChunk(cx + dx, cz + dz, false);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity entity : chunk.getBlockEntities().values()) {
                    if (entity instanceof HearthBlockEntity) {
                        double d = entity.getBlockPos().distSqr(at);
                        if (d < bestD) {
                            bestD = d;
                            best = entity.getBlockPos().immutable();
                        }
                    }
                }
            }
        }
        return best;
    }

    private static void mouse(Minecraft mc, int[] point) {
        if (point == null) {
            return;
        }
        double scale = mc.getWindow().getGuiScale();
        try {
            // The window has no focus on a bare X server: call the handler GLFW would have called.
            // onMove by its SRG name, so the shipped (reobfuscated) jar finds it too.
            java.lang.reflect.Method move = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findMethod(
                    net.minecraft.client.MouseHandler.class, "m_91561_", long.class, double.class, double.class);
            move.invoke(mc.mouseHandler, mc.getWindow().getWindow(), point[0] * scale, point[1] * scale);
        } catch (ReflectiveOperationException | RuntimeException e) {
            note("MOUSE FAILED " + e);
        }
    }

    /** A shot with the chat open (its history on show), then the chat closed again. */
    private static void chatShot(Minecraft mc, String scene) {
        add("chat open " + scene, 500, () -> mc.setScreen(new net.minecraft.client.gui.screens.ChatScreen("")));
        shot(scene);
        add("chat close " + scene, 300, () -> mc.setScreen(null));
    }

    private static void shot(String scene) {
        add("shot " + scene, 250, () -> {
            Minecraft mc = Minecraft.getInstance();
            try {
                Path dir = Path.of(DIR);
                Files.createDirectories(dir);
                NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget());
                image.writeToFile(dir.resolve(scene + ".png"));
                image.close();
                note("SHOT " + scene);
            } catch (java.io.IOException e) {
                note("SHOT FAILED " + scene + " " + e);
            }
        });
    }

    private static void note(String text) {
        if (DIR == null || DIR.isEmpty()) {
            return;
        }
        try {
            Files.createDirectories(Path.of(DIR));
            Files.writeString(Path.of(DIR).resolve("tour.log"), text + "\n", StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (java.io.IOException e) {
            // A log that cannot be written must not stop the client.
        }
    }
}
