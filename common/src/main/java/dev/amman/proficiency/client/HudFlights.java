package dev.amman.proficiency.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.config.ProficiencyClientConfig;
import dev.amman.proficiency.net.BonusLootPayload;
import dev.amman.proficiency.perk.Talents;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Three flights on the HUD (1.38.0), drawn from {@code SkillHud}'s pass: the bonus loot that flies from the world
 * to the hotbar, the talent point coin that flies from the skill bar to the brazier beside the hotbar, and the
 * rested pool that pours into the XP fill. All GUI only, timed in milliseconds, bounded (8 icons, 4 coins, 14
 * dots), a few fills or one quad each, and nothing at all when nothing is flying. The numbers are in
 * {@link HudFlightMath}. Each has its own {@code ui} toggle; Distortion Effects at 0% keeps the glints and sounds
 * but stops the travelling ({@link ClickFx#motion()}).
 */
public final class HudFlights {

    private static final ResourceLocation COIN = ResourceLocation.fromNamespaceAndPath(Proficiency.MOD_ID,
            "textures/gui/point_coin.png");
    private static final ResourceLocation BRAZIER = ResourceLocation.fromNamespaceAndPath(Proficiency.MOD_ID,
            "textures/gui/point_brazier.png");

    private static final int GOLD = 0xFFC247;
    private static final int GOLD_LIGHT = 0xFFF0A8;
    private static final int RESTED_HEAD = 0x8FB6FF;

    // ---- Bonus loot ----------------------------------------------------------------------------

    private static final HudFlightMath.Flights<ItemStack> BONUS = new HudFlightMath.Flights<>();
    /** A glint on a slot: the slot centre and when it began. */
    private record Glint(int x, int y, long at) {
    }

    private static final List<Glint> GLINTS = new ArrayList<>();
    private static long lastPlink = Long.MIN_VALUE / 2;

    // ---- Talent point coin ---------------------------------------------------------------------

    /** {@code lands}: the point it carries is spendable now; otherwise the coin fades before the brazier (1.55.0). */
    private record Coin(long launch, float x, float y, boolean lands) {
        long landsAt() {
            return launch + HudFlightMath.COIN_FLIGHT_MS;
        }
    }

    private static final List<Coin> COINS = new ArrayList<>();
    /**
     * The ranks the brazier shows: ready now (1.55.0, {@link SpendableClient}), not every unspent point. It counts
     * up as coins land and down at once when ranks are spent or stop being ready.
     */
    private static int shownPoints = -1;
    private static int realPoints = -1;
    private static long realChangedAt;
    private static long pointsCheckedAt = Long.MIN_VALUE / 2;
    private static long flareAt = Long.MIN_VALUE / 2;
    private static float visible;
    private static long lastFrame;
    /** Open nodes whose next rank waits for items alone: the item badge on the brazier. */
    private static int materialNodes;
    private static dev.amman.proficiency.perk.Spendable.Short badge;

    // ---- Rested pour ---------------------------------------------------------------------------

    private static final class PourDot {
        final long launch;
        final float lane;
        float startPx = -1;
        boolean landed;

        PourDot(long launch, float lane) {
            this.launch = launch;
            this.lane = lane;
        }
    }

    private static final List<PourDot> POUR = new ArrayList<>();
    private static Skill pourSkill;
    private static int pourLevel = -1;
    private static float pourReach = -1f;
    private static double pourValue = -1;
    private static long pourFlashUntil;
    private static int pourSeed;

    private HudFlights() {
    }

    /** Forgets everything in the air. Logout, a world change, F1. */
    public static void reset() {
        BONUS.clear();
        GLINTS.clear();
        COINS.clear();
        POUR.clear();
        shownPoints = -1;
        realPoints = -1;
        visible = 0f;
        pourSkill = null;
        pourReach = -1f;
        pourLevel = -1;
    }

    private static long clock() {
        return net.minecraft.Util.getMillis();
    }

    // ---- Bonus loot: payload in, flight, landing ------------------------------------------------

    /** The server's note of bonus items (client thread): projects each from the world and launches it. */
    public static void acceptBonus(BonusLootPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !ProficiencyClientConfig.uiBonusFlight()) {
            return;
        }
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 eye = camera.getPosition();
        var look = camera.getLookVector();
        var up = camera.getUpVector();
        var left = camera.getLeftVector();
        double[] f = {look.x(), look.y(), look.z()};
        double[] u = {up.x(), up.y(), up.z()};
        double[] l = {left.x(), left.y(), left.z()};
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        double fov = mc.options.fov().get();
        long now = clock();
        boolean motion = ClickFx.motion();
        if (LayoutAudit.active()) {
            LayoutAudit.note("HUDFLIGHT bonus " + payload.entries().size() + " entries");
        }
        for (BonusLootPayload.Entry e : payload.entries()) {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(e.item()));
            if (item == null || item == net.minecraft.world.item.Items.AIR) {
                continue;
            }
            ItemStack stack = new ItemStack(item);
            if (!motion) {
                // No travelling at Distortion Effects 0%: the glint and the plink at the slot, now.
                land(mc, stack, now);
                continue;
            }
            HudFlightMath.Screen from = HudFlightMath.project(e.x() - eye.x, e.y() - eye.y, e.z() - eye.z,
                    f, u, l, fov, w, h, 6);
            BONUS.add(from.x(), from.y(), stack, Math.max(1, e.count()), now);
        }
    }

    /** The hotbar slot (0..8) holding {@code item}, or -1: where a bonus item most likely landed. */
    private static int hotbarSlotOf(Minecraft mc, Item item) {
        Inventory inv = mc.player.getInventory();
        for (int i = 0; i < 9; i++) {
            if (inv.getItem(i).is(item)) {
                return i;
            }
        }
        return -1;
    }

    /** Centre of hotbar slot {@code slot}, or of the hotbar for -1 (the bag). */
    private static int[] slotCentre(int slot, int guiWidth, int guiHeight) {
        int i = slot < 0 ? 4 : slot;
        return new int[] {guiWidth / 2 - 80 + i * 20, guiHeight - 11};
    }

    private static void land(Minecraft mc, ItemStack stack, long now) {
        int[] c = slotCentre(hotbarSlotOf(mc, stack.getItem()), mc.getWindow().getGuiScaledWidth(),
                mc.getWindow().getGuiScaledHeight());
        GLINTS.add(new Glint(c[0], c[1], now));
        if (GLINTS.size() > 16) {
            GLINTS.remove(0);
        }
        if (now - lastPlink > 70) {
            lastPlink = now;
            // Each plink a little higher than the last inside one burst, so a pile reads as a run of notes.
            long recent = GLINTS.stream().filter(g -> now - g.at() < 400).count();
            play(SoundEvents.AMETHYST_BLOCK_CHIME, 1.5f + 0.08f * Math.min(6, recent), 0.28f);
            play(SoundEvents.ITEM_PICKUP, 1.8f, 0.12f);
        }
    }

    private static void play(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    // ---- The HUD pass --------------------------------------------------------------------------

    /** One call from the HUD pass: draws every flight and the brazier. */
    public static void render(GuiGraphics g, Minecraft mc, PlayerSkills skills) {
        long now = clock();
        renderBonus(g, mc, now);
        renderCoinsAndBrazier(g, mc, skills, now);
    }

    /** The GUI is hidden (F1): nothing is queued for later. */
    public static void hidden() {
        if (!BONUS.live().isEmpty() || !COINS.isEmpty() || !GLINTS.isEmpty() || !POUR.isEmpty()) {
            BONUS.clear();
            GLINTS.clear();
            COINS.clear();
            POUR.clear();
        }
        // The count keeps its place so the brazier does not replay a level-up when F1 comes back.
        pointsCheckedAt = Long.MIN_VALUE / 2;
    }

    private static void renderBonus(GuiGraphics g, Minecraft mc, long now) {
        int w = g.guiWidth();
        int h = g.guiHeight();
        for (var done : BONUS.land(now)) {
            land(mc, done.tag(), now);
        }
        for (var f : BONUS.live()) {
            double t = HudFlightMath.progress(f.launch(), HudFlightMath.BONUS_FLIGHT_MS, now);
            if (t < 0) {
                continue;
            }
            int[] to = slotCentre(hotbarSlotOf(mc, f.tag().getItem()), w, h);
            float[] p = HudFlightMath.bonusPoint(t, f.x(), f.y(), to[0], to[1]);
            // A short glittering trail behind the icon.
            for (int s = 3; s >= 1; s--) {
                float[] q = HudFlightMath.bonusPoint(Math.max(0, t - s * 0.05), f.x(), f.y(), to[0], to[1]);
                int a = 150 - s * 40;
                g.fill(Math.round(q[0]) - 1, Math.round(q[1]) - 1, Math.round(q[0]) + 1, Math.round(q[1]) + 1,
                        (a << 24) | GOLD_LIGHT);
            }
            var pose = g.pose();
            pose.pushPose();
            pose.translate(p[0], p[1], 300);
            pose.scale(p[2], p[2], 1f);
            g.renderItem(f.tag(), -8, -8);
            pose.popPose();
            if (f.count() > 1 && t < 0.92) {
                g.drawString(mc.font, "+" + f.count(), Math.round(p[0]) + 5, Math.round(p[1]) + 1,
                        0xFFFFFF | (Math.round(255 * (float) Math.min(1.0, (1 - t) * 4)) << 24), true);
            }
        }
        for (int i = GLINTS.size() - 1; i >= 0; i--) {
            Glint glint = GLINTS.get(i);
            long age = now - glint.at();
            if (age >= HudFlightMath.GLINT_MS) {
                GLINTS.remove(i);
                continue;
            }
            drawGlint(g, glint.x(), glint.y(), age / (float) HudFlightMath.GLINT_MS);
        }
    }

    /** A white flash on the slot and a four-point star that swells and thins. */
    private static void drawGlint(GuiGraphics g, int x, int y, float t) {
        float k = 1f - t;
        int a = Math.round(255 * k * k);
        g.fill(x - 8, y - 8, x + 8, y + 8, (Math.round(70 * k) << 24) | 0xFFFFFF);
        int arm = 2 + Math.round(8 * (float) Math.sin(Math.PI * Math.min(1f, t * 1.2f)));
        int c = (a << 24) | 0xFFFFFF;
        g.fill(x - arm, y, x + arm + 1, y + 1, c);
        g.fill(x, y - arm, x + 1, y + arm + 1, c);
        int d = Math.max(0, arm / 2 - 1);
        int c2 = (Math.round(a * 0.7f) << 24) | GOLD_LIGHT;
        g.fill(x - d, y - d, x - d + 1, y - d + 1, c2);
        g.fill(x + d, y - d, x + d + 1, y - d + 1, c2);
        g.fill(x - d, y + d, x - d + 1, y + d + 1, c2);
        g.fill(x + d, y + d, x + d + 1, y + d + 1, c2);
    }

    // ---- Talent point coin and the brazier -------------------------------------------------------

    /**
     * A level-up on {@code skill}: when it grants a talent point, the coin leaves the bar for the brazier. Called
     * where the level number turns over, so the coin leaves with the roll.
     */
    public static void onLevelUp(Skill skill, int oldLevel, int newLevel, int guiWidth, int guiHeight) {
        onLevelUp(skill, oldLevel, newLevel, guiWidth / 2f, SkillHud.lineY(guiHeight) + 12);
    }

    /**
     * The same, with the coin leaving from ({@code fromX}, {@code fromY}): a recent-skills stack row's own bar
     * ({@code HudStackRows}) rather than the main line's.
     */
    public static void onLevelUp(Skill skill, int oldLevel, int newLevel, float fromX, float fromY) {
        if (!ProficiencyClientConfig.uiPointCoin()) {
            return;
        }
        int points = newLevel / Talents.LEVELS_PER_POINT - oldLevel / Talents.LEVELS_PER_POINT;
        long now = clock();
        if (LayoutAudit.active()) {
            LayoutAudit.note("HUDFLIGHT coin " + skill.id() + " " + oldLevel + "->" + newLevel);
        }
        // Does the new point buy a rank now? Asked of the skill itself at launch: the tick's summary may lag a frame.
        int readyNow = demoPoints >= 0 ? points : readyIn(skill);
        for (int i = 0; i < points; i++) {
            boolean lands = i < readyNow;
            if (!ClickFx.motion() || COINS.size() >= HudFlightMath.MAX_COINS) {
                // Reduced motion, or a burst past the coins in the air: the point just lands, flare and chime.
                if (lands) {
                    landCoin(now);
                }
                continue;
            }
            COINS.add(new Coin(now + i * 110L, fromX, fromY, lands));
            if (i == 0) {
                play(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.9f, 0.22f);
            }
        }
    }

    private static int readyIn(Skill skill) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return 0;
        }
        return dev.amman.proficiency.perk.Spendable.of(dev.amman.proficiency.ProficiencyAttachments.of(mc.player), skill,
                SpendableClient.materials(mc.player)).readyRanks();
    }

    private static void landCoin(long now) {
        shownPoints = Math.min(Math.max(0, realPoints), Math.max(0, shownPoints) + 1);
        flareAt = now;
        play(SoundEvents.AMETHYST_BLOCK_CHIME, 1.35f, 0.4f);
        play(SoundEvents.LAVA_POP, 1.3f, 0.22f);
    }

    /** Demo only (PROFICIENCY_LAYOUT_DEMO): the unspent count to show instead of the real one; -1 for the real one. */
    private static int demoPoints = -1;

    static void demoPoints(int points) {
        demoPoints = points;
        pointsCheckedAt = Long.MIN_VALUE / 2;
    }

    /** Demo only: a pour of {@code dots} from the segment, as a paid gain would start. */
    static void demoPour(int dots, long now) {
        for (int i = 0; i < Math.min(dots, HudFlightMath.MAX_POUR_LIVE - POUR.size()); i++) {
            POUR.add(new PourDot(now + 40 + i * HudFlightMath.POUR_STAGGER_MS, (float) ((pourSeed++ * 0.6180339887) % 1.0)));
        }
    }

    /** Demo only: the material-blocked nodes to show (-1 for the real ones). */
    private static int demoMaterials = -1;

    static void demoMaterials(int nodes) {
        demoMaterials = nodes;
        pointsCheckedAt = Long.MIN_VALUE / 2;
    }

    /** Ranks ready now over every skill (1.55.0); unspent points that cannot buy anything are not counted. */
    private static int unspent(PlayerSkills skills) {
        dev.amman.proficiency.perk.Spendable.Summary summary = SpendableClient.summary();
        materialNodes = demoMaterials >= 0 ? demoMaterials : summary.materialNodes();
        badge = SpendableClient.firstShort(summary, null);
        if (demoPoints >= 0) {
            return demoPoints;
        }
        return summary.ready();
    }

    private static void renderCoinsAndBrazier(GuiGraphics g, Minecraft mc, PlayerSkills skills, long now) {
        if (!ProficiencyClientConfig.uiPointCoin()) {
            COINS.clear();
            return;
        }
        if (now - pointsCheckedAt > 250) {
            pointsCheckedAt = now;
            int real = unspent(skills);
            if (real != realPoints) {
                if (realPoints >= 0 && real > realPoints) {
                    realChangedAt = now;
                }
                if (real < shownPoints || shownPoints < 0) {
                    // Points were spent (or this is the first look): the brazier follows at once.
                    shownPoints = real;
                } else if (real > shownPoints && COINS.isEmpty() && demoPoints < 0
                        && now - SpendableClient.levelsChangedAt() > 3000) {
                    // More ready with no level-up behind it (the last item picked up, a rank bought elsewhere):
                    // no coin carries it, so the brazier lights at once with a flare (the ready toast chimes).
                    shownPoints = real;
                    flareAt = now;
                }
                realPoints = real;
            }
        }
        if (shownPoints < 0) {
            return;
        }
        // A point that no coin carried (the level-up effect is off, a sync from elsewhere) joins the count
        // once the coins have had their chance.
        if (realPoints > shownPoints + COINS.size() && COINS.isEmpty() && now - realChangedAt > 2500) {
            shownPoints = realPoints;
        }
        int[] socket = HudFlightMath.socket(g.guiWidth(), g.guiHeight());
        for (int i = COINS.size() - 1; i >= 0; i--) {
            Coin coin = COINS.get(i);
            if (coin.lands() && now >= coin.landsAt()) {
                COINS.remove(i);
                landCoin(now);
            } else if (!coin.lands() && now >= coin.launch() + Math.round(HudFlightMath.COIN_FLIGHT_MS * DUD_END)) {
                // A point nothing can be bought with: the coin is gone before the brazier, no flare.
                COINS.remove(i);
            }
        }
        if (socket == null) {
            COINS.clear();
            return;
        }
        boolean landing = false;
        for (Coin coin : COINS) {
            landing |= coin.lands();
        }
        // Nothing ready and nothing waiting for items: the brazier hides (points that cannot be spent do not nag).
        boolean want = shownPoints > 0 || materialNodes > 0 || landing || now - flareAt < HudFlightMath.COIN_FLARE_MS;
        long dt = Math.max(0, Math.min(100, now - lastFrame));
        lastFrame = now;
        visible = want ? Math.min(1f, visible + dt / 220f) : Math.max(0f, visible - dt / 180f);
        if (visible <= 0f && COINS.isEmpty()) {
            return;
        }
        drawBrazier(g, mc.font, socket[0], socket[1], now);
        if (materialNodes > 0 && visible > 0.05f) {
            drawItemBadge(g, mc.font, socket[0], socket[1], now);
        }
        for (Coin coin : COINS) {
            double t = HudFlightMath.progress(coin.launch(), HudFlightMath.COIN_FLIGHT_MS, now);
            if (t >= 0) {
                drawCoin(g, coin, t, socket[0] + HudFlightMath.SOCKET / 2f, socket[1] + HudFlightMath.SOCKET / 2f);
            }
        }
    }

    private static void drawBrazier(GuiGraphics g, Font font, int x, int y, long now) {
        int a = Math.round(255 * visible);
        if (a < 6) {
            return;
        }
        float flare = flareAt > now - HudFlightMath.COIN_FLARE_MS && flareAt <= now
                ? 1f - (now - flareAt) / (float) HudFlightMath.COIN_FLARE_MS : 0f;
        float glow = Math.min(1f, HudFlightMath.glow(Math.max(0, shownPoints)) + flare * 0.5f);
        int s = HudFlightMath.SOCKET;
        // A soft ember halo behind the bowl while it burns.
        if (glow > 0f) {
            int ha = Math.round(a * glow * (0.16f + 0.2f * flare));
            g.fill(x - 3, y - 3, x + s + 3, y + s + 1, (ha << 24) | 0xE2571B);
            g.fill(x, y, x + s, y + s, (Math.round(ha * 0.8f) << 24) | GOLD);
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(1f, 1f, 1f, visible);
        g.blit(BRAZIER, x, y, s, s, 0f, 0f, 18, 18, 18, 54);
        if (glow > 0f) {
            int frame = 1 + (int) ((now / 170) % 2);
            g.setColor(1f, 1f, 1f, visible * Math.min(1f, glow + 0.15f));
            g.blit(BRAZIER, x, y, s, s, 0f, 18f * frame, 18, 18, 18, 54);
        }
        g.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
        if (flare > 0f) {
            // Sparks leave the bowl: six embers rising and thinning.
            for (int i = 0; i < 6; i++) {
                float age = 1f - flare;
                float dx = (i - 2.5f) * 2.6f + (float) Math.sin(i * 2.1) * 2;
                float rise = age * (10 + i * 2.5f);
                int sa = Math.round(255 * flare * flare);
                g.fill(Math.round(x + s / 2f + dx * (0.4f + age)), Math.round(y + 4 - rise),
                        Math.round(x + s / 2f + dx * (0.4f + age)) + 1, Math.round(y + 4 - rise) + 1,
                        (sa << 24) | (i % 2 == 0 ? GOLD_LIGHT : GOLD));
            }
        }
        if (shownPoints > 0 && a > 40) {
            String label = Component.translatable("proficiency.spend.ready", HudFlightMath.pointsLabel(shownPoints)).getString();
            int lw = font.width(label);
            int tx = x + s / 2 - lw / 2;
            int ty = y - 9;
            int rgb = flare > 0f ? 0xFFFFFF : GOLD;
            g.drawString(font, label, tx, ty, (Math.round(a) << 24) | rgb, true);
        }
    }

    /** A coin that carries no spendable point fades out over this stretch of its flight (share of the flight). */
    private static final double DUD_FADE = 0.45;
    private static final double DUD_END = 0.8;

    /**
     * The item badge: the first short material as a half-size item on a dark chip at the brazier's lower right,
     * gold-ringed, with the number of nodes that wait for items when more than one.
     */
    private static void drawItemBadge(GuiGraphics g, Font font, int x, int y, long now) {
        int a = Math.round(255 * visible);
        int bx = x + HudFlightMath.SOCKET - 5;
        int by = y + HudFlightMath.SOCKET - 7;
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        g.fill(bx - 1, by - 1, bx + 10, by + 10, (Math.round(a * 0.92f) << 24) | 0x1A1413);
        int ring = (a << 24) | 0xD2A249;
        g.fill(bx - 1, by - 1, bx + 10, by, ring);
        g.fill(bx - 1, by + 9, bx + 10, by + 10, ring);
        g.fill(bx - 1, by, bx, by + 9, ring);
        g.fill(bx + 9, by, bx + 10, by + 9, ring);
        ItemStack icon = badge == null ? ItemStack.EMPTY : badgeIcon(badge.requirement());
        if (!icon.isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(bx + 0.5f, by + 0.5f, 0);
            g.pose().scale(0.5f, 0.5f, 1f);
            g.renderItem(icon, 0, 0);
            g.pose().popPose();
        } else {
            g.drawString(font, "!", bx + 3, by + 1, (a << 24) | 0xD2A249, false);
        }
        if (materialNodes > 1) {
            String n = materialNodes > 9 ? "9+" : Integer.toString(materialNodes);
            g.pose().pushPose();
            g.pose().translate(0, 0, 150);
            g.drawString(font, n, bx + 11, by + 2, (a << 24) | 0xD2A249, true);
            g.pose().popPose();
        }
        g.pose().popPose();
    }

    private static ItemStack badgeIcon(dev.amman.proficiency.perk.Requirement requirement) {
        Item item = dev.amman.proficiency.perk.Requirements.resolve(requirement);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static void drawCoin(GuiGraphics g, Coin coin, double t, float toX, float toY) {
        float[] p = HudFlightMath.coinPoint(t, coin.x(), coin.y(), toX, toY);
        float spin = HudFlightMath.coinWidth(t, 3);
        float fade = (float) Math.min(1.0, (1.0 - t) * 6 + 0.3);
        if (!coin.lands()) {
            fade *= (float) Math.max(0.0, Math.min(1.0, (DUD_END - t) / (DUD_END - DUD_FADE)));
            if (fade <= 0.02f) {
                return;
            }
        }
        // Embers behind it, thinning along the path.
        for (int s = 1; s <= 6; s++) {
            double back = Math.max(0, t - s * 0.035);
            float[] q = HudFlightMath.coinPoint(back, coin.x(), coin.y(), toX, toY);
            int a = Math.round(200 * (1f - s / 7f) * fade);
            int cx = Math.round(q[0]);
            int cy = Math.round(q[1]) + (s % 2);
            g.fill(cx - 1, cy - 1, cx + 1, cy + 1, (a << 24) | (s % 2 == 0 ? GOLD : 0xE2571B));
        }
        // A halo, then the coin: a quad turned about its vertical axis by squeezing its width.
        int ha = Math.round(46 * fade);
        int cx = Math.round(p[0]);
        int cy = Math.round(p[1]);
        int r = Math.round(11 * p[2]);
        g.fill(cx - r, cy - r, cx + r, cy + r, (ha << 24) | GOLD);
        var pose = g.pose();
        pose.pushPose();
        pose.translate(p[0], p[1], 320);
        pose.scale(p[2] * spin, p[2], 1f);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (!coin.lands()) {
            g.setColor(1f, 1f, 1f, Math.min(1f, fade));
        }
        g.blit(COIN, -8, -8, 16, 16, 0f, 0f, 16, 16, 16, 16);
        g.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
        pose.popPose();
    }

    // ---- Rested pool pours in --------------------------------------------------------------------

    /**
     * Called each frame the skill line is up: when the pool of {@code skill} shrank at the same level, the XP that
     * just paid took it ({@code value} is the bar's value, which must have risen), so dots leave the blue segment. The first look, a new skill and a level change only
     * remember the value.
     */
    public static void observeRested(Skill skill, int level, float reach, double value, long now) {
        double before = pourValue;
        pourValue = value;
        if (!ProficiencyClientConfig.uiRestedPour()) {
            pourSkill = skill;
            pourLevel = level;
            pourReach = reach;
            POUR.clear();
            return;
        }
        // Only a gain pays from the pool; a death or a respec that empties it moves the bar down, not up.
        int want = HudFlightMath.pourFor(skill == pourSkill && level == pourLevel, pourReach, reach, before, value);
        if (want > 0) {
            int room = HudFlightMath.MAX_POUR_LIVE - POUR.size();
            int n = Math.min(want, room);
            long start = now + 40;
            for (int i = 0; i < n; i++) {
                float lane = (float) (((pourSeed++ * 0.6180339887) % 1.0));
                POUR.add(new PourDot(start + i * HudFlightMath.POUR_STAGGER_MS, lane));
            }
            if (n > 0) {
                if (LayoutAudit.active()) {
                    LayoutAudit.note("HUDFLIGHT pour " + n + " dots");
                }
                play(SoundEvents.BOTTLE_FILL, 1.7f, 0.18f);
            }
        }
        if (skill != pourSkill || level != pourLevel) {
            POUR.clear();
        }
        pourSkill = skill;
        pourLevel = level;
        pourReach = reach;
    }

    /**
     * Draws the pour inside the line's bar: {@code rested} is the blue segment as {start, end} bar pixels (or null
     * once it is spent), {@code filled} the fill's width.
     */
    public static void drawPour(GuiGraphics g, int barLeft, int barTop, int filled, int[] rested, int alpha, long now) {
        if (POUR.isEmpty() && now > pourFlashUntil) {
            return;
        }
        boolean motion = ClickFx.motion();
        int edge = barLeft + filled;
        if (now <= pourFlashUntil) {
            int fa = Math.round(alpha * 0.8f * (pourFlashUntil - now) / 120f);
            g.fill(edge - 1, barTop - 1, edge + 1, barTop + 3, (fa << 24) | RESTED_HEAD);
        }
        for (int i = POUR.size() - 1; i >= 0; i--) {
            PourDot dot = POUR.get(i);
            double t = HudFlightMath.progress(dot.launch, HudFlightMath.POUR_FLIGHT_MS, now);
            if (t < 0) {
                continue;
            }
            if (dot.startPx < 0) {
                float a = rested != null ? rested[0] : filled + 2;
                float b = rested != null ? rested[1] : filled + 8;
                dot.startPx = a + (b - a) * dot.lane;
            }
            if (t >= 1.0 || !motion) {
                POUR.remove(i);
                pourFlashUntil = now + 120;
                continue;
            }
            float x0 = barLeft + dot.startPx;
            float y0 = barTop + 1;
            float lift = 5 + 4 * dot.lane;
            for (int s = 2; s >= 0; s--) {
                double e = Math.max(0, HudFlightMath.ease(t - s * 0.06));
                float x = HudFlightMath.bezier(x0, (x0 + edge) / 2f, edge, e);
                float y = HudFlightMath.bezier(y0, y0 - lift * 2f, barTop + 1, e);
                int px = Math.round(x);
                int py = Math.round(y);
                if (s == 0) {
                    g.fill(px - 1, py - 1, px + 2, py + 1, (alpha << 24) | RESTED_HEAD);
                    g.fill(px, py - 2, px + 1, py + 2, (Math.round(alpha * 0.5f) << 24) | RESTED_HEAD);
                } else {
                    int a = Math.round(alpha * (s == 1 ? 0.55f : 0.25f));
                    g.fill(px, py, px + 1, py + 1, (a << 24) | 0x4F86FF);
                }
            }
        }
    }
}
