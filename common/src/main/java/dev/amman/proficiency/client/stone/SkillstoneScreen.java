package dev.amman.proficiency.client.stone;

import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.client.ClickFx;
import dev.amman.proficiency.client.ClickFxMath;
import dev.amman.proficiency.client.MasteryStars;
import dev.amman.proficiency.client.ProficiencyClient;
import dev.amman.proficiency.client.SkillIcons;
import dev.amman.proficiency.client.SkillNumbers;
import dev.amman.proficiency.client.SkillsScreen;
import dev.amman.proficiency.client.SpendableClient;
import dev.amman.proficiency.client.SkillstoneMath;
import dev.amman.proficiency.client.UiStyle;
import dev.amman.proficiency.config.ProficiencyClientConfig;
import dev.amman.proficiency.perk.Synergies;
import dev.amman.proficiency.perk.Synergy;
import dev.amman.proficiency.perk.Talent;
import dev.amman.proficiency.skill.ActiveService;
import dev.amman.proficiency.skill.Mastery;
import dev.amman.proficiency.skill.MasteryEffects;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillCategory;
import dev.amman.proficiency.skill.SkillMath;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Skillstone ({@code ui.style = modern}, 1.30.0): one carved slab with a rune dial around a
 * fire well, a category slate, and a Face per skill with Face / Tree / Hearth tabs. Design A of
 * {@code proficiency-uidesign/DESIGNS.md}, approved 2026-10-26.
 *
 * <p>The dial opens with the 1.05 s intro ({@link SkillstoneMath}); a second opening within 30 s, a
 * key or a click skips it. The Tree tab is carved too (stage 2, {@link TreePage}); pages not carved yet
 * (the Hearth, Expertise, Titles, Journal) open their classic screens.
 * Shared verbatim with Forge 1.20.1: no version-specific API (both scroll overloads, no
 * {@code renderBackground}).
 */
public final class SkillstoneScreen extends Screen {

    private enum Page { DIAL, FACE, TREE, HEARTH }

    private static final SkillCategory[] CATS = SkillCategory.values();
    private static final String[] RUNE = {"swords", "mining", "running", "smithing", "spellcasting", "wayfaring",
            "masonry", "social", "nightwalker"};
    private static final int FACE_H = 300;
    private static final long TURN_MS = 180;

    private static long lastOpened = Long.MIN_VALUE / 2;
    private static SkillCategory lastCategory = SkillCategory.GATHERING;

    private Page page;
    private Skill skill;
    private SkillCategory category = lastCategory;
    private final long openedAt;
    private boolean introSkipped;
    private long turnAt = Long.MIN_VALUE / 2;
    private int hoverRune = -1;
    private long hoverAt;
    private int hoverRow = -1;
    private int slateScroll;
    private int synergyPick;
    final ClickFx click = new ClickFx();
    private final TreePage tree = new TreePage(this);
    private final HearthPage hearth = new HearthPage(this);
    private final FaceColumn column = new FaceColumn(this);
    private final UnlockSlam slam = new UnlockSlam(this);
    private long riseAt = Long.MIN_VALUE / 2;
    private long edgeAt = Long.MIN_VALUE / 2;
    private long shakeAt = Long.MIN_VALUE / 2;
    private float shakeAmp;
    private long refuseAt = Long.MIN_VALUE / 2;
    private String note;
    private long noteAt;
    /** The page the note was refused on, and the refused part (a reason chip sits under it off the dial). */
    private Page notePage;
    private int[] noteHit;

    // Layout
    int sx, sy, sw, sh, ix0, iy0, ix1, iy1;
    private boolean compact;
    private int dial, pit, cx, cy;
    private int lx0, ly0, lx1, ly1, rowH;
    final List<int[]> hits = new ArrayList<>();
    private int hitDx;
    private int hitDy;
    private String[][] cardCache;
    private Skill cardSkill;
    private long cardAt;
    final List<Component> tooltip = new ArrayList<>();

    public SkillstoneScreen() {
        this(null);
    }

    public SkillstoneScreen(Skill face) {
        super(Component.translatable("proficiency.screen.title"));
        this.openedAt = Util.getMillis();
        this.page = face == null ? Page.DIAL : Page.FACE;
        this.skill = face;
        if (face != null) {
            this.category = face.category();
        }
        // The second opening within 30 s, or reduced motion, lands on the settled slab.
        this.introSkipped = face != null || openedAt - lastOpened < SkillstoneMath.SKIP_WINDOW_MS || !ClickFx.motion();
        lastOpened = openedAt;
    }

    /** The skills screen the player asked for: the Skillstone when {@code ui.style = modern}. */
    public static Screen skills() {
        return modern() ? new SkillstoneScreen() : new SkillsScreen();
    }

    public static boolean modern() {
        return ProficiencyClientConfig.uiStyle() == UiStyle.MODERN;
    }

    /** Switches {@code ui.style} live and reopens the skills screen in the other style. */
    /**
     * Why a skill's ability is locked: below the unlock level, {@code belowKey} with the level; at or past it the
     * ability waits for the tree's first node ("Take Pickwork in the tree"), not for a level it already has.
     */
    static String abilityLock(dev.amman.proficiency.skill.PlayerSkills skills, Skill skill, String belowKey) {
        if (skills.level(skill) < ActiveService.unlockLevel()) {
            return tr(belowKey, ActiveService.unlockLevel());
        }
        return tr("proficiency.stone.root", SkillNumbers.rootName(skill).getString());
    }

    /** The classic panel's Skillstone view button: MODERN, saved, and the slab opens (never a flip back to classic). */
    public static void toModern() {
        ProficiencyClientConfig.setUiStyle(UiStyle.MODERN);
        lastOpened = Long.MIN_VALUE / 2;
        Minecraft.getInstance().setScreen(new SkillstoneScreen());
    }

    public static void toggle() {
        boolean nowModern = !modern();
        ProficiencyClientConfig.setUiStyle(nowModern ? UiStyle.MODERN : UiStyle.CLASSIC);
        lastOpened = Long.MIN_VALUE / 2;
        Minecraft.getInstance().setScreen(nowModern ? new SkillstoneScreen() : new SkillsScreen());
    }

    @Override
    protected void init() {
        sw = SkillstoneMath.slabWidth(this.width);
        if (page == Page.DIAL) {
            sh = SkillstoneMath.slabHeight(this.height);
            sy = (this.height - sh) / 2;
        } else if (page == Page.HEARTH) {
            sw = SkillstoneMath.treeWidth(this.width);
            sh = Math.min(SkillstoneMath.HEARTH_H, SkillstoneMath.treeHeight(this.height - 8));
            sy = (this.height - sh) / 2 + 4;
        } else if (page == Page.TREE) {
            sw = SkillstoneMath.treeWidth(this.width);
            sh = SkillstoneMath.treeHeight(this.height - 8);
            sy = (this.height - sh) / 2 + 4;
        } else {
            sh = Math.min(FACE_H, this.height - 16);
            sy = (this.height - sh) / 2 + 4;
        }
        sx = (this.width - sw) / 2;
        int rim = SkillstoneMath.RIM;
        ix0 = sx + rim;
        iy0 = sy + rim;
        ix1 = sx + sw - rim;
        iy1 = sy + sh - rim;
        compact = SkillstoneMath.compact(page == Page.DIAL ? sh : sh + 60);
        dial = SkillstoneMath.dialRadius(SkillstoneMath.slabHeight(this.height));
        pit = SkillstoneMath.pitRadius(dial);
        cx = ix0 + dial + (compact ? 20 : 26);
        cy = (iy0 + iy1) / 2 + (compact ? 2 : 0);
        lx0 = cx + dial + (compact ? 24 : 36);
        lx1 = ix1 - (compact ? 6 : 14);
        ly0 = iy0 + (compact ? 10 : 18);
        ly1 = iy1 - (compact ? 26 : 40);
        slateScroll = 0;
        if (page == Page.TREE) {
            tree.open(skill);
            tree.layout();
        }
        if (page == Page.HEARTH) {
            hearth.layout();
        }
    }

    Font font() {
        return this.font;
    }

    /** A short decaying shake of the whole slab, {@code amp} px. */
    void shake(long now, float amp) {
        shakeAt = now;
        shakeAmp = amp;
    }

    /** The unlocking shake: grows and climbs, from {@code at}. */
    void riseShake(long at) {
        riseAt = at;
    }

    /** A capstone's ember glow round the slab's edge. */
    void edgeGlow(long now) {
        edgeAt = now + ClickFxMath.MIN_CHARGE_MS;
    }

    PlayerSkills skillsNow() {
        return playerSkills();
    }

    private PlayerSkills playerSkills() {
        Player player = this.minecraft == null ? null : this.minecraft.player;
        return player == null ? null : ProficiencyAttachments.of(player);
    }

    private long t(long now) {
        if (demoT >= 0) {
            return demoT;
        }
        return introSkipped ? SkillstoneMath.INTRO_MS + 1 : now - openedAt;
    }

    /** Each category's skills, built once: the dial and slate read them every frame. */
    private static final java.util.Map<SkillCategory, List<Skill>> BY_CATEGORY = new java.util.EnumMap<>(SkillCategory.class);

    static {
        for (SkillCategory c : SkillCategory.values()) {
            List<Skill> out = new ArrayList<>();
            for (Skill s : Skill.VALUES) {
                if (s.category() == c) {
                    out.add(s);
                }
            }
            BY_CATEGORY.put(c, List.copyOf(out));
        }
    }

    static List<Skill> skillsOf(SkillCategory c) {
        return BY_CATEGORY.getOrDefault(c, List.of());
    }

    static double average(PlayerSkills skills, SkillCategory c) {
        List<Skill> list = skillsOf(c);
        if (list.isEmpty()) {
            return 0;
        }
        double sum = 0;
        for (Skill s : list) {
            sum += skills.level(s);
        }
        return sum / list.size();
    }

    static ResourceLocation runeIcon(int i) {
        Skill s = Skill.byId(RUNE[i]);
        if (s == null) {
            List<Skill> list = skillsOf(CATS[i]);
            s = list.isEmpty() ? Skill.VALUES[0] : list.get(0);
        }
        return SkillIcons.texture(s);
    }

    static void sound(SoundEvent event, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, volume));
    }

    static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    // ---- Render ---------------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long now = clock();
        g.fillGradient(0, 0, this.width, this.height, 0xA8000000, 0xC8000000);
        PlayerSkills skills = playerSkills();
        if (skills == null) {
            return;
        }
        hits.clear();
        tooltip.clear();
        float dx = 0;
        float dy = 0;
        if (ClickFx.motion()) {
            dx += SkillstoneMath.shakeX(now - shakeAt, shakeAmp) + SkillstoneMath.refuseX(now - refuseAt, 2f);
            dy += SkillstoneMath.shakeY(now - shakeAt, shakeAmp);
            dx += 0.5f * SkillstoneMath.riseShake(now - riseAt, 2f);
            dy -= Math.abs(SkillstoneMath.riseShake(now - riseAt, 2.5f));
            dy += slam.jolt(now);
        }
        g.pose().pushPose();
        g.pose().translate(Math.round(dx), Math.round(dy), 0);
        long turn = now - turnAt;
        boolean turning = turn >= 0 && turn < TURN_MS && ClickFx.motion();
        if (turning) {
            float k = Math.max(0.04f, Math.abs(1f - 2f * turn / (float) TURN_MS));
            float mid = sx + sw / 2f;
            g.pose().translate(mid, 0, 0);
            g.pose().scale(k, 1f, 1f);
            g.pose().translate(-mid, 0, 0);
        }
        int mx = mouseX - Math.round(dx);
        int my = mouseY - Math.round(dy);
        // Hits are recorded in the slab's own frame; a click is moved into it the same way as the hover.
        hitDx = Math.round(dx);
        hitDy = Math.round(dy);
        if (page == Page.DIAL) {
            renderDial(g, skills, now, mx, my);
        } else if (page == Page.TREE) {
            renderTree(g, skills, now, mx, my);
        } else if (page == Page.HEARTH) {
            Stone.slab(g, sx, sy, sw, sh, SkillstoneMath.RIM);
            renderTabs(g, skills, now, mx, my);
            hearth.render(g, now, mx, my);
        } else {
            renderFace(g, skills, now, mx, my);
        }
        if (page != Page.TREE && skill != null) {
            // Ranks that land while the tree is not shown are taken in silently, so they never fire late.
            tree.quietWatch(skills, skill);
        }
        renderNote(g, now);
        slam.renderUnder(g, now);
        g.pose().popPose();
        float red = SkillstoneMath.refuseFlash(now - refuseAt);
        if (red > 0) {
            Stone.outlineRound(g, sx - 1, sy - 1, sw + 2, sh + 2, 12, Stone.argb(Math.round(200 * red), Stone.RED));
        }
        long edge = now - edgeAt;
        if (edge >= 0 && edge < EDGE_MS && ClickFx.on()) {
            // The capstone's edge glow: ember rings round the slab, fading outwards and over time.
            float k = 1f - edge / (float) EDGE_MS;
            for (int i = 0; i < 6; i++) {
                int a = Math.round(220 * k * (1f - i / 6f));
                Stone.outlineRound(g, sx - 1 - i, sy - 1 - i, sw + 2 + 2 * i, sh + 2 + 2 * i, 12 + i,
                        Stone.argb(a, Stone.ember(0.9f - i * 0.08f)));
            }
        }
        slam.render(g, skills, now, Math.round(dx), Math.round(dy));
        click.render(g, now, 1f);
        if (slam.active()) {
            tooltip.clear();
        }
        if (!tooltip.isEmpty()) {
            Stone.tooltip(g, this.font, tooltip, mouseX, mouseY, this.width);
        }
    }

    private static final long EDGE_MS = 700;

    // ---- Tree -----------------------------------------------------------------------------------

    private void renderTree(GuiGraphics g, PlayerSkills skills, long now, int mx, int my) {
        if (skill == null) {
            page = Page.DIAL;
            init();
            return;
        }
        Stone.slab(g, sx, sy, sw, sh, SkillstoneMath.RIM);
        renderTabs(g, skills, now, mx, my);
        tree.render(g, skills, now, mx, my);
    }

    // ---- Dial -----------------------------------------------------------------------------------

    private void renderDial(GuiGraphics g, PlayerSkills skills, long now, int mx, int my) {
        long t = t(now);
        boolean intro = t < SkillstoneMath.INTRO_MS;
        int oy = intro ? Math.round((1f - SkillstoneMath.rise(t)) * (this.height - sy + 10)) + SkillstoneMath.squash(t) : 0;
        if (intro && t < SkillstoneMath.RISE_MS + 20 && t >= 0 && !groundSound) {
            groundSound = true;
            sound(SoundEvents.GRINDSTONE_USE, 0.5f, 0.25f);
        }
        if (intro && t >= SkillstoneMath.RISE_MS && !landSound) {
            landSound = true;
            sound(SoundEvents.STONE_PLACE, 0.6f, 0.9f);
        }
        if (intro && t >= SkillstoneMath.STRIKE_MS && !strikeSound) {
            strikeSound = true;
            sound(SoundEvents.FLINTANDSTEEL_USE, 1.0f, 0.8f);
        }
        if (intro && t >= SkillstoneMath.ROAR_MS && !roarSound) {
            roarSound = true;
            sound(SoundEvents.FIRECHARGE_USE, 0.7f, 0.25f);
        }
        g.pose().pushPose();
        g.pose().translate(0, oy, 0);
        Stone.slab(g, sx, sy, sw, sh, SkillstoneMath.RIM);
        // The dial ring and the fire well.
        int dn = 2 * (dial + 22);
        g.blit(Stone.tex("dial_" + dial), cx - dn / 2, cy - dn / 2, 0f, 0f, dn, dn, dn, dn);
        float fire = intro ? SkillstoneMath.fire(t) : 1f;
        if (fire > 0) {
            int halo = Math.round(pit * (1.3f + 0.5f * fire));
            Stone.light(g, cx, cy, halo, halo, Stone.WARM, 0.5f + 0.4f * fire);
        }
        int pn = 2 * (pit + 5);
        g.blit(Stone.tex("pit_" + pit), cx - pn / 2, cy - pn / 2, 0f, 0f, pn, pn, pn, pn);
        int frame = SkillstoneMath.fireFrame(now - openedAt, 8);
        Stone.blitAlpha(g, Stone.tex("fire_" + pit), cx - pn / 2, cy - pn / 2, pn, pn, 0f, frame * pn, pn, pn * 8, fire);
        float strike = intro ? SkillstoneMath.strike(t) : 0f;
        if (strike > 0) {
            Stone.light(g, cx, cy, Math.round(pit * 1.25f), Math.round(pit * 1.25f), 0xFFD08C, strike * 0.55f);
        }
        float slate = intro ? SkillstoneMath.slate(t) : 1f;
        if (slate > 0.3f) {
            Stone.textCentred(g, this.font, tr("proficiency.stone.levels", skills.totalLevels()), cx,
                    cy + pit + (compact ? 6 : 10), Stone.GOLD);
        }
        // Runes, clockwise from the top.
        int n = CATS.length;
        int s = compact ? 10 : 14;
        int newHover = -1;
        for (int i = 0; i < n; i++) {
            double a = -Math.PI / 2 + i * 2 * Math.PI / n;
            int rx = (int) Math.round(cx + dial * Math.cos(a));
            int ry = (int) Math.round(cy + dial * Math.sin(a));
            if (!intro && Math.abs(mx - rx) <= s + 2 && Math.abs(my - (ry + oy)) <= s + 2) {
                newHover = i;
            }
        }
        if (newHover != hoverRune) {
            hoverRune = newHover;
            hoverAt = now;
            if (newHover >= 0) {
                sound(SoundEvents.STONE_HIT, (newHover % 2 == 0) ? 1.25f : 0.95f, 0.25f);
            }
        }
        for (int i = 0; i < n; i++) {
            double a = -Math.PI / 2 + i * 2 * Math.PI / n;
            int rx = (int) Math.round(cx + dial * Math.cos(a));
            int ry = (int) Math.round(cy + dial * Math.sin(a));
            float real = SkillstoneMath.heat(average(skills, CATS[i]));
            float lit = intro ? SkillstoneMath.runeHeat(i, n, t, real) : real;
            boolean sel = CATS[i] == category && slate > 0;
            boolean hov = i == hoverRune;
            if (sel) {
                Stone.groove(g, (int) Math.round(cx + (pit + 5) * Math.cos(a)), (int) Math.round(cy + (pit + 5) * Math.sin(a)),
                        (int) Math.round(cx + (dial - s - 1) * Math.cos(a)), (int) Math.round(cy + (dial - s - 1) * Math.sin(a)),
                        1f, 3);
            }
            float out = sel ? SkillstoneMath.HOVER_OUT
                    : hov ? SkillstoneMath.HOVER_OUT * (ClickFx.motion() ? SkillstoneMath.hover(now - hoverAt) : 1f) : 0f;
            if (out > 0.5f) {
                g.fill(rx - s, ry - s, rx + s, ry + s, Stone.argb(255, 0x181211));
                rx = Math.round(rx + out * (float) Math.cos(a));
                ry = Math.round(ry + out * (float) Math.sin(a)) - (sel ? 2 : Math.round(out * 0.4f));
            }
            float shown = sel ? 1.3f : hov ? Math.max(lit, 0.9f) : lit;
            Object key = CATS[i];
            click.begin(g, key, rx - s, ry - s, 2 * s, 2 * s, now);
            Stone.socket(g, rx - s, ry - s, 2 * s, 2 * s, shown, sel, hov);
            int is = 16;
            if (shown < 0.08f) {
                Stone.greyIcon(g, runeIcon(i), rx - is / 2, ry - is / 2, is, 0.5f);
            } else {
                Stone.icon(g, runeIcon(i), rx - is / 2, ry - is / 2, is, Math.min(1f, 0.45f + shown));
            }
            click.end(g, key, rx - s, ry - s, 2 * s, 2 * s, now);
            int readyHere = intro ? 0 : SpendableClient.summary().readyIn(CATS[i]);
            if (readyHere > 0) {
                // Ranks ready in this category's skills: an ember dot with the count on the rune's corner.
                dev.amman.proficiency.client.SpendableClient.ember(g, this.font, rx + s - 4, ry - s - 1, readyHere, now);
            }
            hits.add(new int[] {0, i, rx - s, ry - s + oy, 2 * s, 2 * s});
            if (hov) {
                tooltip.add(Component.translatable(CATS[i].translationKey()));
                tooltip.add(Component.literal(tr("proficiency.stone.rune", Math.round(average(skills, CATS[i])))));
                if (readyHere > 0) {
                    tooltip.add(Component.literal(SpendableClient.readyText(readyHere)).withStyle(net.minecraft.ChatFormatting.GOLD));
                }
            }
        }
        if (!intro && hoverRune < 0 && (mx - cx) * (mx - cx) + (my - cy) * (my - cy) <= pit * pit) {
            // The fire well: why the points wait, every skill (Shift: all of them, every reason).
            tooltip.add(Component.literal(tr("proficiency.spend.dial_head")).withStyle(net.minecraft.ChatFormatting.GOLD));
            tooltip.addAll(SpendableClient.whyStuck(null, null));
        }
        if (slate > 0) {
            renderSlate(g, skills, now, mx, my - oy, slate, oy);
        }
        if (slate > 0.3f) {
            renderCorner(g, skills, mx, my - oy, oy);
        }
        g.pose().popPose();
        if (intro) {
            float dust = SkillstoneMath.dust(t);
            if (dust > 0) {
                for (int k = 0; k < 90; k++) {
                    float hx = dev.amman.proficiency.client.ClickFxMath.hash(k, 7);
                    float hy = dev.amman.proficiency.client.ClickFxMath.hash(k, 13);
                    int px = Math.round(sx - 10 + hx * (sw + 20));
                    int py = Math.round(sy + oy + sh - 4 + hy * 14 - (1f - dust) * 10 * hy);
                    int c = k % 3 == 0 ? 0x786E64 : k % 3 == 1 ? 0x605650 : 0x8C8076;
                    g.fill(px, py, px + 2, py + 2, Stone.argb(Math.round(255 * dust), c));
                }
            }
        }
    }

    private boolean groundSound;
    private boolean landSound;
    private boolean strikeSound;
    private boolean roarSound;

    private void renderSlate(GuiGraphics g, PlayerSkills skills, long now, int mx, int my, float slide, int oy) {
        int shift = Math.round((1f - slide) * 30);
        g.pose().pushPose();
        g.pose().translate(shift, 0, 0);
        Stone.well(g, lx0, ly0, lx1 - lx0, ly1 - ly0);
        String name = Component.translatable(category.translationKey()).getString().toUpperCase(Locale.ROOT);
        int hw = this.font.width(name) + 24;
        Stone.plate(g, this.font, (lx0 + lx1) / 2 - hw / 2, ly0 - 9, hw, 18, name, Stone.GOLD, false);
        List<Skill> list = skillsOf(category);
        int rows = Math.max(1, list.size());
        rowH = Math.max(12, Math.min(compact ? 22 : 30, (ly1 - ly0 - 18) / rows));
        int contentH = rows * rowH;
        int viewTop = ly0 + 12;
        int viewH = ly1 - 4 - viewTop;
        slateScroll = Math.max(0, Math.min(slateScroll, Math.max(0, contentH + 4 - viewH)));
        int y = ly0 + 16 - slateScroll;
        hoverRow = -1;
        boolean clip = contentH + 4 > viewH;
        if (clip) {
            Stone.scissor(g, lx0 + 2, viewTop, lx1 - 2, viewTop + viewH);
        }
        for (int i = 0; i < list.size(); i++) {
            Skill s = list.get(i);
            int lvl = skills.level(s);
            boolean in = mx >= lx0 + 4 && mx < lx1 - 4 && my >= y - 3 && my < y + rowH - 3
                    && my >= viewTop && my < viewTop + viewH && slide >= 1f;
            if (in) {
                hoverRow = i;
                Stone.lightRect(g, lx0 + 6, y - 3, lx1 - lx0 - 12, rowH - 3, 0x46200A, 2, 0.7f);
                g.fill(lx0 + 3, y - 3, lx0 + 5, y + rowH - 6, Stone.argb(255, Stone.ember(0.85f)));
            }
            Object key = s;
            int isz = rowH >= 18 ? 16 : 8;
            click.begin(g, key, lx0 + 10, y, isz, isz, now);
            if (lvl > 0) {
                Stone.icon(g, SkillIcons.texture(s), lx0 + 10, y, isz, 1f);
            } else {
                Stone.greyIcon(g, SkillIcons.texture(s), lx0 + 10, y, isz, 0.4f);
            }
            click.end(g, key, lx0 + 10, y, isz, isz, now);
            int tx = lx0 + (isz == 16 ? 32 : 22);
            String lv = Integer.toString(lvl);
            int room = lx1 - 10 - this.font.width(lv) - 6 - tx
                    - SpendableClient.emberWidth(this.font, SpendableClient.summary().ready(s)) - (SpendableClient.summary().ready(s) > 0 ? 6 : 0);
            Stone.text(g, this.font, Stone.fit(this.font, Component.translatable(s.translationKey()).getString(), room),
                    tx, y + 1, lvl > 0 ? Stone.INK : Stone.LOCK);
            Stone.textRight(g, this.font, lv, lx1 - 10, y + 1, lvl > 0 ? Stone.GOLD : Stone.LOCK);
            int readyRow = SpendableClient.summary().ready(s);
            if (readyRow > 0) {
                // Ranks ready now in this skill's tree: the ember dot and its count, left of the level.
                int ew = SpendableClient.emberWidth(this.font, readyRow);
                SpendableClient.ember(g, this.font, lx1 - 16 - this.font.width(lv) - ew, y + 2, readyRow, now);
            }
            if (rowH >= 22) {
                Stone.groove(g, tx, lx1 - 10, y + 13, lvl / (float) SkillMath.MAX_LEVEL, 3, in);
            }
            // Only the part of a row inside the slate's window is clickable: a row scrolled under the
            // header plate or past the well's bottom is out of sight and must not open a Face.
            int top = Math.max(y - 3, viewTop);
            int bottom = Math.min(y - 3 + rowH, viewTop + viewH);
            if (bottom > top) {
                hits.add(new int[] {1, s.ordinal(), lx0 + 4 + shift, top + oy, lx1 - lx0 - 8, bottom - top});
            }
            y += rowH;
        }
        if (clip) {
            g.disableScissor();
        }
        if (contentH + 4 > viewH) {
            // A thin ember scroll mark on the slate's right edge.
            int barH = Math.max(8, viewH * viewH / (contentH + 4));
            int barY = viewTop + (viewH - barH) * slateScroll / Math.max(1, contentH + 4 - viewH);
            g.fill(lx1 - 4, barY, lx1 - 2, barY + barH, Stone.argb(200, Stone.ember(0.6f)));
        }
        // The plate under the slate: what the hovered (or first) skill wants.
        String msg;
        Skill focus = hoverRow >= 0 ? list.get(hoverRow) : null;
        if (focus == null) {
            for (Skill s : list) {
                if (SpendableClient.summary().ready(s) > 0) {
                    focus = s;
                    break;
                }
            }
        }
        if (note != null && notePage == Page.DIAL && now - noteAt < 2500) {
            msg = note;
        } else if (focus == null) {
            msg = tr("proficiency.stone.pick");
        } else if (SpendableClient.summary().ready(focus) > 0) {
            msg = tr("proficiency.spend.points_wait", Component.translatable(focus.translationKey()).getString(),
                    SpendableClient.summary().ready(focus));
        } else if (SpendableClient.summary().of(focus).waiting()) {
            msg = tr("proficiency.spend.points_stuck", Component.translatable(focus.translationKey()).getString(),
                    SpendableClient.summary().of(focus).leftover());
        } else {
            msg = tr("proficiency.stone.at_level", Component.translatable(focus.translationKey()).getString(),
                    skills.level(focus));
        }
        int by = iy1 - (compact ? 20 : 30);
        int px0 = compact ? lx0 + 5 * 22 : lx0;
        if (lx1 - px0 < 60) {
            // Too narrow beside the five sockets: the plate takes the slate's full width one row up.
            px0 = lx0;
            by -= compact ? 18 : 22;
        }
        int bw = Math.max(24, Math.min(lx1 - px0, this.font.width(msg) + 30));
        msg = Stone.fit(this.font, msg, bw - 12);
        Stone.plate(g, this.font, (px0 + lx1) / 2 - bw / 2, by, bw, compact ? 16 : 20, msg, Stone.INK, false);
        g.pose().popPose();
    }

    /** The corner sockets: Expertise, Titles, Journal, Mastery and the classic-view toggle. */
    private void renderCorner(GuiGraphics g, PlayerSkills skills, int mx, int my, int oy) {
        String[] icons = {"character", "trophy", "ledger", "*", null};
        int size = compact ? 18 : 22;
        int step = size + 4;
        // Compact: how many sockets the slate's footer row holds. At 320x240 (960x720 GUI 3) it holds three, and
        // the Mastery star and the classic toggle ran off the slab: those go to the slab's free left corners.
        int fits = compact ? Math.max(1, (ix1 - 4 - lx0 + 4) / step) : icons.length;
        for (int j = 0; j < icons.length; j++) {
            // Compact: the dial fills the slab's height, so the sockets sit in the slate's footer row.
            int kx = compact ? lx0 + j * step : ix0 + 10 + j * step;
            int ky = compact ? iy1 - 20 - (size - 16) / 2 : iy1 - size - 10;
            if (compact && j >= fits) {
                // The classic toggle in the top-left corner, the others up from the bottom-left corner.
                kx = ix0 + 6;
                ky = j == 4 ? iy0 + 6 : iy1 - 6 - size - (3 - j) * step;
            }
            if (!compact && j == 4) {
                // Five sockets would reach the dial's lower runes: the classic toggle sits in the top-left corner.
                kx = ix0 + 10;
                ky = iy0 + 10;
            }
            boolean hov = mx >= kx && mx < kx + size && my >= ky && my < ky + size;
            boolean free = j == 0 && skills.expertiseAvailable() > 0;
            Stone.socket(g, kx, ky, size, size, free ? 0.8f : hov ? 0.6f : 0f, false, hov);
            int ic = 16;
            int ox = kx + (size - ic) / 2;
            int oyy = ky + (size - ic) / 2;
            if ("*".equals(icons[j])) {
                // Mastery: the star glyph, gold when lit.
                String star = String.valueOf(MasteryStars.GLYPH);
                g.drawString(this.font, star, kx + (size - this.font.width(star)) / 2 + 1, ky + (size - 8) / 2,
                        Stone.argb(255, hov ? MasteryStars.GOLD : 0x8C7E76), true);
            } else if (icons[j] != null) {
                ResourceLocation tex = dev.amman.proficiency.Proficiency.id("textures/gui/hearth/" + icons[j] + ".png");
                if (hov || free) {
                    Stone.icon(g, tex, ox, oyy, ic, 1f);
                } else {
                    Stone.greyIcon(g, tex, ox, oyy, ic, 0.35f);
                }
            } else {
                int c = Stone.argb(255, hov ? Stone.GOLD : Stone.DIM);
                for (int r = 0; r < 3; r++) {
                    g.fill(ox + 3, oyy + 3 + r * 4, ox + 13, oyy + 5 + r * 4, c);
                }
            }
            hits.add(new int[] {2, j, kx, ky + oy, size, size});
            if (hov) {
                String label = switch (j) {
                    case 0 -> free ? tr("proficiency.expertise.button.free", skills.expertiseAvailable())
                            : tr("proficiency.expertise.button");
                    case 1 -> tr("proficiency.codex.button");
                    case 2 -> tr("proficiency.journal.button");
                    case 3 -> tr("proficiency.stone.tab.mastery");
                    default -> tr("proficiency.stone.classic");
                };
                tooltip.add(Component.literal(label));
            }
        }
    }

    // ---- Face -----------------------------------------------------------------------------------

    private void renderFace(GuiGraphics g, PlayerSkills skills, long now, int mx, int my) {
        if (skill == null) {
            page = Page.DIAL;
            init();
            return;
        }
        Stone.slab(g, sx, sy, sw, sh, SkillstoneMath.RIM);
        renderTabs(g, skills, now, mx, my);
        if (oneColumn()) {
            column.render(g, skills, skill, now, mx, my, synergyPick);
            return;
        }
        boolean small = compact || sw < 520;
        int box = small ? 48 : 80;
        int ex = ix0 + (small ? 10 : 22);
        int ey = iy0 + (small ? 12 : 22);
        Stone.socket(g, ex, ey, box, box, 1f, false, false);
        int isz = box - 16;
        Stone.icon(g, SkillIcons.texture(skill), ex + 8, ey + 8, isz, 1f);
        int level = skills.level(skill);
        String name = Component.translatable(skill.translationKey()).getString();
        int y = ey + box + (small ? 6 : 10);
        if (!small && this.font.width(name) * 2 <= box + 40) {
            g.pose().pushPose();
            g.pose().translate(ex + box / 2f - this.font.width(name), y, 0);
            g.pose().scale(2f, 2f, 1f);
            Stone.text(g, this.font, name, 0, 0, Stone.GOLD);
            g.pose().popPose();
            y += 22;
        } else {
            Stone.textCentred(g, this.font, Stone.fit(this.font, name, box + 30), ex + box / 2, y, Stone.GOLD);
            y += 12;
        }
        Stone.textCentred(g, this.font, tr("proficiency.stone.level", level), ex + box / 2, y, Stone.INK);
        y += 14;
        float bar = level >= SkillMath.MAX_LEVEL ? 1f : SkillMath.progress(level, (float) skills.xp(skill));
        Stone.groove(g, ex, ex + box, y, bar, 3, true, level >= SkillMath.MAX_LEVEL);
        y += 8;
        Stone.textCentred(g, this.font, level >= SkillMath.MAX_LEVEL ? tr("proficiency.stone.maxed")
                : tr("proficiency.stone.to_next", Math.round(bar * 100), level + 1), ex + box / 2, y, Stone.DIM);
        y += small ? 12 : 22;
        dev.amman.proficiency.perk.Spendable.Report spend = SpendableClient.report(skill);
        int points = spend.readyRanks();
        if (y + 22 < iy1) {
            int bx = ex + box / 2 - 18;
            Stone.coals(g, bx, y, Math.min(3, points));
            if (spend.materialNodes() > 0) {
                itemBadge(g, spend, bx + 30, y + 8, now);
            }
            hits.add(new int[] {5, 0, bx, y, 36, 16});
            if (mx >= bx && mx < bx + 36 && my >= y && my < y + 16) {
                brazierTooltip(skills);
            }
            Stone.textCentred(g, this.font, SpendableClient.readyText(points), ex + box / 2, y + 18,
                    points > 0 ? Stone.INK : Stone.DIM);
        }
        // The four cards.
        int c0 = ex + box + (small ? 14 : 24);
        int cw = (ix1 - (small ? 8 : 16) - c0 - 10) / 2;
        int chh = small ? 50 : 66;
        int top = iy0 + (small ? 12 : 22);
        if (cardCache == null || cardSkill != skill || now - cardAt >= CARD_REFRESH_MS) {
            cardCache = cards(skills, now);
            cardSkill = skill;
            cardAt = now;
        }
        String[][] cards = cardCache;
        faceSmall = small;
        for (int i = 0; i < 4; i++) {
            int kx = c0 + (i % 2) * (cw + 10);
            int ky = top + (i / 2) * (chh + (small ? 6 : 10));
            slam.slot(i, kx, ky, cw, chh);
            if (slam.hides(i, now)) {
                // The card is in flight: its slot waits dark (UnlockSlam draws it).
                continue;
            }
            drawCard(g, skills, cards, i, kx, ky, cw, chh, small);
            if (mx >= kx && mx < kx + cw && my >= ky && my < ky + chh) {
                cardTooltip(i, skills);
            }
        }
        int below = top + 2 * (chh + (small ? 6 : 10)) + (small ? 0 : 4);
        if (dev.amman.proficiency.client.ClientRecords.enabled()) {
            below = renderRecords(g, c0, below - (small ? 2 : 4), (ix1 - (small ? 8 : 16) - 10) - c0, small, mx, my)
                    + (small ? 4 : 6);
        }
        renderSynergies(g, skills, now, mx, my, c0, below, small);
    }

    /**
     * The Records strip under the four cards: a header and up to four values in two columns (the skill's own best
     * last). Returns the y under it. Nothing but a dim line when the player has set none yet.
     */
    private int renderRecords(GuiGraphics g, int x0, int y0, int w, boolean small, int mx, int my) {
        List<String> lines = dev.amman.proficiency.client.ClientRecords.lines(skill);
        int line = small ? 10 : 12;
        int rows = Math.max(1, (lines.size() + 1) / 2);
        int h = (small ? 5 : 7) + line + (small ? 1 : 2) + rows * line + 3;
        if (y0 + h + 30 > iy1) {
            // Not enough slab: the synergies keep the room; the records stay in the card's tooltip.
            return y0 + 2;
        }
        Stone.well(g, x0, y0, w, h);
        Stone.text(g, this.font, tr("proficiency.records.title"), x0 + 8, y0 + (small ? 5 : 7), Stone.HEAD);
        int yy = y0 + (small ? 5 : 7) + line + (small ? 1 : 2);
        if (lines.isEmpty()) {
            Stone.text(g, this.font, Stone.fit(this.font, tr("proficiency.records.none"), w - 16), x0 + 8, yy,
                    Stone.DIM);
        }
        int half = (w - 16) / 2;
        for (int i = 0; i < lines.size(); i++) {
            int cx = x0 + 8 + (i % 2) * (half + 4);
            Stone.text(g, this.font, Stone.fit(this.font, lines.get(i), half - 2), cx, yy + (i / 2) * line,
                    i == 0 ? Stone.GOLD : Stone.INK);
        }
        if (mx >= x0 && mx < x0 + w && my >= y0 && my < y0 + h) {
            cardTooltip(4, null);
        }
        return y0 + h;
    }

    /** The two-column Face's card {@code i} at ({@code kx}, {@code ky}): well, header, lines, ready dot, stars. */
    private void drawCard(GuiGraphics g, PlayerSkills skills, String[][] cards, int i, int kx, int ky, int cw, int chh,
                          boolean small) {
        int level = skills.level(skill);
        int line = small ? 10 : 12;
        Stone.well(g, kx, ky, cw, chh);
        boolean locked = i < cardLocked.length && cardLocked[i];
        cardHeader(g, cards[i][0], i, locked, kx, ky + (small ? 5 : 7), small);
        int yy = ky + (small ? 5 : 7) + line + (small ? 1 : 2);
        int[] colours = {Stone.GOLD, Stone.INK, Stone.DIM};
        for (int k = 1; k < cards[i].length && k < 4; k++) {
            if (cards[i][k] == null || cards[i][k].isEmpty()) {
                continue;
            }
            int colour = colours[k - 1];
            if (i == 0) {
                colour = k == 1 ? Stone.INK : Stone.DIM;
            }
            if (locked) {
                // Greyed: the name and what it does in the lock colour, the unlock condition in warm ink.
                colour = k == 3 ? LOCK_WHY : Stone.LOCK;
            }
            if (yy + 8 <= ky + chh - 2) {
                Stone.text(g, this.font, Stone.fit(this.font, cards[i][k], cw - 16), kx + 8, yy, colour);
            }
            yy += line;
        }
        if (i == 2 && ActiveService.unlocked(skills, skill)) {
            boolean ready = skills.cooldownRemaining(skill, gameTime()) <= 0;
            int px = kx + cw - 14;
            int py = ky + 9;
            if (ready) {
                Stone.light(g, px + 2, py + 2, 10, 10, 0x28963C, 0.9f);
                g.fill(px, py, px + 5, py + 5, Stone.argb(255, Stone.GREEN));
            } else {
                g.fill(px, py, px + 5, py + 5, Stone.argb(255, 0x5A3C30));
            }
        }
        if (i == 3) {
            int stars = level >= SkillMath.MAX_LEVEL ? MasteryStars.shown(skills, skill) : 0;
            int max = Mastery.MAX_STARS;
            String lit = MasteryStars.text(stars);
            String dark = MasteryStars.text(max - stars);
            int sx0 = kx + cw - 8 - this.font.width(lit + dark);
            Stone.text(g, this.font, lit, sx0, ky + (small ? 5 : 7), 0xFFC83A);
            Stone.text(g, this.font, dark, sx0 + this.font.width(lit), ky + (small ? 5 : 7), locked ? 0x4A403C : 0x6E625C);
        }
    }

    /** The Face brazier's hover: what it is, then why the points wait (this skill; Shift: every skill). */
    void brazierTooltip(PlayerSkills skills) {
        tooltip.add(Component.literal(tr("proficiency.stone.brazier")));
        tooltip.addAll(SpendableClient.whyStuck(skill, null));
    }

    /** The item badge on a Face brazier: the first short material, gold-ringed, as the tree's socket badge. */
    void itemBadge(GuiGraphics g, dev.amman.proficiency.perk.Spendable.Report spend, int bx, int by, long now) {
        if (spend.materials().isEmpty()) {
            return;
        }
        Stone.fillRound(g, bx - 1, by - 1, 10, 10, 2, Stone.argb(235, 0x1A1413));
        Stone.outlineRound(g, bx - 1, by - 1, 10, 10, 2, Stone.argb(255, 0xD2A249));
        net.minecraft.world.item.ItemStack icon = NodeMaterials.icon(spend.materials().get(0).requirement(), now);
        if (!icon.isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(bx, by, 0);
            g.pose().scale(0.5f, 0.5f, 1f);
            g.renderItem(icon, 0, 0);
            g.pose().popPose();
        }
    }

    /** Which Face cards are locked (signature, ability, mastery), worked out with the card text. */
    final boolean[] cardLocked = new boolean[4];

    /** The unlock condition on a locked card: readable, but not the card's gold. */
    static final int LOCK_WHY = 0xD8A27A;

    /**
     * A card's header row: the skill icon (lit, or the unlit rune tint when locked), the header and, when locked,
     * the padlock after it. Shared with the one-column Face.
     */
    void cardHeader(GuiGraphics g, String header, int i, boolean locked, int kx, int ty, boolean small) {
        int is = small ? 8 : 10;
        int ix = kx + 7;
        int iy = ty - (is - 8) / 2 - 1;
        if (i == 0 || !locked) {
            Stone.icon(g, SkillIcons.texture(skill), ix, iy, is, 1f);
        } else {
            Stone.greyIcon(g, SkillIcons.texture(skill), ix, iy, is, 0.45f);
        }
        int hx = ix + is + 4;
        Stone.text(g, this.font, header, hx, ty, locked ? Stone.LOCK : Stone.HEAD);
        if (locked) {
            dev.amman.proficiency.client.SpendableClient.lock(g, hx + this.font.width(header) + 4, ty, Stone.argb(255, Stone.LOCK));
        }
    }

    /** Whether the last two-column Face was the small one. */
    private boolean faceSmall;

    /** The unlock slam's flying card: Face card {@code i} drawn at the origin, {@code w} x {@code h}, as the Face has it. */
    void paintCard(GuiGraphics g, PlayerSkills skills, int i, int w, int h, long now) {
        if (skill == null) {
            return;
        }
        if (oneColumn()) {
            column.paint(g, skills, skill, i, w);
            return;
        }
        if (cardCache == null || cardSkill != skill) {
            cardCache = cards(skills, now);
            cardSkill = skill;
            cardAt = now;
        }
        drawCard(g, skills, cardCache, i, 0, 0, w, h, faceSmall);
    }

    /** The one-column Face reports where card {@code index} sits (the slam's target slot). */
    void slamSlot(int index, int x, int y, int w, int h) {
        slam.slot(index, x, y, w, h);
    }

    boolean slamHides(int index, long now) {
        return slam.hides(index, now);
    }

    /** The unlock slam starts: called by the tree when a filled node woke the signature or the ability. */
    void unlockSlam(Talent node, int x, int y, int half, boolean signature, boolean ability, boolean paid, long now) {
        slam.start(node, x, y, half, 2 * half - 8, signature, ability, paid, now);
    }

    /** The unlock slam turns the slab to the Face under its flying socket, the card {@code index} in view. */
    void slamTurn(int index, long now) {
        if (page != Page.FACE) {
            turnTo(Page.FACE, now);
        }
        column.reveal(index);
    }

    long gameTime() {
        return this.minecraft != null && this.minecraft.level != null ? this.minecraft.level.getGameTime() : 0L;
    }

    /** The Face cards are rebuilt four times a second: enough for the cooldown clock, no per-frame strings. */
    private static final long CARD_REFRESH_MS = 250;

    /** Header, name, line, dim line for passive, signature, ability and mastery. */
    private String[][] cards(PlayerSkills skills, long now) {
        return cards(skills, now, false);
    }

    /** {@code allRows}: the one-column Face lists every passive row, not the first three. */
    String[][] cards(PlayerSkills skills, long now, boolean allRows) {
        int level = skills.level(skill);
        List<String> rows = SkillNumbers.nowRows(skills, skill);
        String[] passive = {tr("proficiency.stone.passive"), rows.isEmpty() ? "" : rows.get(0),
                rows.size() > 1 ? rows.get(1) : "", rows.size() > 2 ? rows.get(2) : ""};
        if (allRows && rows.size() > 3) {
            passive = new String[rows.size() + 1];
            passive[0] = tr("proficiency.stone.passive");
            for (int r = 0; r < rows.size(); r++) {
                passive[r + 1] = rows.get(r);
            }
        }
        double chance = skills.procChance(skill);
        String sigState;
        cardLocked[0] = false;
        cardLocked[1] = chance <= 0;
        if (chance > 0) {
            sigState = tr("proficiency.stone.chance", String.format(Locale.ROOT, "%.1f", chance * 100));
        } else if (level < dev.amman.proficiency.config.ProficiencyConfig.procUnlockLevel()) {
            sigState = tr("proficiency.spend.lock_level", dev.amman.proficiency.config.ProficiencyConfig.procUnlockLevel());
        } else {
            sigState = tr("proficiency.stone.root", SkillNumbers.rootName(skill).getString());
        }
        String shortKey = I18n.exists(skill.procShortKey()) ? skill.procShortKey() : skill.procDescKey();
        String[] signature = {tr("proficiency.stone.signature"), tr(skill.procKey()), tr(shortKey), sigState};
        String abState;
        cardLocked[2] = !ActiveService.unlocked(skills, skill);
        if (cardLocked[2]) {
            abState = abilityLock(skills, skill, "proficiency.spend.lock_level");
        } else {
            long cooling = skills.cooldownRemaining(skill, gameTime());
            if (cooling > 0) {
                long secs = cooling / 20;
                abState = tr("proficiency.wheel.cooldown", String.format(Locale.ROOT, "%d:%02d", secs / 60, secs % 60));
            } else {
                abState = tr("proficiency.stone.ready", ProficiencyClient.USE_ABILITY.getTranslatedKeyMessage().getString());
            }
        }
        int seconds = (int) Math.round(ActiveService.durationTicks(skills, skill) / 20.0);
        String abLine = skill == Skill.HOMEBUILDER ? tr("proficiency.wheel.effect.homebuilder", seconds)
                : tr("proficiency.wheel.effect", tr(skill.procKey()), seconds);
        String[] ability = {tr("proficiency.stone.ability"), tr(skill.activeKey()), abLine, abState};
        MasteryEffects.Effect effect = MasteryEffects.of(skill);
        String[] mastery;
        if (effect == null || Mastery.maxStars() <= 0) {
            cardLocked[3] = false;
            mastery = new String[] {tr("proficiency.stone.mastery"), "", tr("proficiency.stone.mastery_none"), ""};
        } else {
            int stars = level >= SkillMath.MAX_LEVEL ? MasteryEffects.stars(skills, skill) : 0;
            // The rider works from the first star: below level 100, or at 100 with no star yet, it is locked.
            cardLocked[3] = stars <= 0;
            String what = tr(effect.shortKey(), MasteryEffects.format(effect.at(Math.max(1, stars))));
            mastery = new String[] {tr("proficiency.stone.mastery"), tr(effect.nameKey()), what,
                    stars > 0 ? tr("proficiency.stone.stars", stars, Mastery.maxStars())
                            : tr("proficiency.spend.lock_mastery")};
        }
        return new String[][] {passive, signature, ability, mastery};
    }

    void cardTooltip(int i, PlayerSkills skills) {
        switch (i) {
            case 0 -> {
                tooltip.add(Component.translatable(skill.descriptionKey()));
                tooltip.addAll(SkillNumbers.statRows(skills, skill));
            }
            case 1 -> {
                tooltip.add(Component.translatable(skill.procKey()));
                tooltip.add(Component.translatable(skill.procDescKey()));
            }
            case 2 -> {
                tooltip.add(Component.translatable(skill.activeKey()));
                tooltip.add(Component.translatable("proficiency.stats.ability",
                        SkillNumbers.seconds(ActiveService.durationTicks(skills, skill)),
                        SkillNumbers.seconds(ActiveService.cooldownTicks(skills, skill))));
            }
            case 4 -> {
                tooltip.add(Component.translatable("proficiency.records.title"));
                tooltip.add(Component.translatable("proficiency.records.tooltip"));
            }
            default -> tooltip.addAll(MasteryStars.riderTooltip(skills, skill));
        }
    }

    private void renderTabs(GuiGraphics g, PlayerSkills skills, long now, int mx, int my) {
        boolean small = sw < 520;
        int tw = small ? 46 : 56;
        int step = small ? 50 : 62;
        String[] names = {tr("proficiency.stone.tab.face"), tr("proficiency.stone.tab.tree"), tr("proficiency.stone.tab.hearth")};
        int x = ix1 - 3 * step;
        for (int i = 0; i < 3; i++) {
            boolean on = i == (page == Page.TREE ? 1 : page == Page.HEARTH ? 2 : 0);
            boolean hov = mx >= x && mx < x + tw && my >= iy0 - 15 && my < iy0 + 3;
            Object key = "tab" + i;
            click.begin(g, key, x, iy0 - 15, tw, 18, now);
            if (i == 1 && !on && skill != null && SpendableClient.summary().ready(skill) > 0) {
                Stone.light(g, x + tw / 2, iy0 - 6, tw / 2 + 6, 14, Stone.WARM, 0.7f);
            }
            Stone.block(g, x, iy0 - 15, tw, 18, 3, 2, false, Stone.shade(Stone.STONE, on ? 1.3f : hov ? 1.15f : 0.95f));
            Stone.textCentred(g, this.font, Stone.fit(this.font, names[i], tw - 6), x + tw / 2, iy0 - 10,
                    on ? Stone.GOLD : hov ? Stone.INK : Stone.DIM);
            click.end(g, key, x, iy0 - 15, tw, 18, now);
            hits.add(new int[] {3, i, x, iy0 - 15, tw, 18});
            x += step;
        }
        int bw = small ? 56 : 70;
        boolean hov = mx >= ix0 + 4 && mx < ix0 + 4 + bw && my >= iy0 - 15 && my < iy0 + 3;
        Stone.block(g, ix0 + 4, iy0 - 15, bw, 18, 3, 2, false, Stone.shade(Stone.STONE, hov ? 1.15f : 0.95f));
        Stone.textCentred(g, this.font, tr("proficiency.stone.back"), ix0 + 4 + bw / 2, iy0 - 10, hov ? Stone.INK : Stone.DIM);
        hits.add(new int[] {4, 0, ix0 + 4, iy0 - 15, bw, 18});
    }

    static Skill partner(Synergy synergy, Skill self) {
        for (Synergy.Need need : synergy.requires()) {
            if (need.skill() != self) {
                return need.skill();
            }
        }
        for (Synergy.Grant grant : synergy.grants()) {
            if (grant.skill() != null && grant.skill() != self) {
                return grant.skill();
            }
        }
        return self;
    }

    private void renderSynergies(GuiGraphics g, PlayerSkills skills, long now, int mx, int my, int x0, int sy0,
                                 boolean small) {
        List<Synergy> list = Synergies.involving(skill);
        if (list.isEmpty() || sy0 + 30 > iy1) {
            return;
        }
        int awake = 0;
        for (Synergy s : list) {
            if (Synergies.isActive(skills, s)) {
                awake++;
            }
        }
        int right = ix1 - (small ? 8 : 16);
        Stone.text(g, this.font, tr("proficiency.stone.synergies"), x0, sy0, Stone.HEAD);
        String count = awake == 0 ? tr("proficiency.stone.syn_none", list.size())
                : tr("proficiency.stone.syn_count", awake, list.size() - awake);
        Stone.textRight(g, this.font, count, right, sy0, Stone.DIM);
        int size = small ? 18 : 22;
        int avail = right - x0 - size;
        int perRow = Math.max(1, Math.min(list.size(), avail / (size + 4) + 1));
        int rows = (list.size() + perRow - 1) / perRow;
        int step = perRow > 1 ? Math.min(110, avail / (perRow - 1)) : 0;
        int rowStep = size + 6;
        int top = sy0 + (small ? 12 : 14);
        // A row of sockets cannot run out of the slab: fewer rows show, the chip lists the rest.
        rows = Math.min(rows, Math.max(1, (iy1 - 4 - top) / rowStep));
        synergyPick = Math.max(0, Math.min(synergyPick, list.size() - 1));
        int hovered = -1;
        for (int i = 0; i < Math.min(list.size(), rows * perRow); i++) {
            int r = i / perRow;
            int c = i % perRow;
            int px = x0 + c * step;
            int py = top + r * rowStep;
            if (c + 1 < perRow && i + 1 < list.size()) {
                Stone.groove(g, px + size, x0 + (c + 1) * step, py + size / 2, 0f, 2, false);
            }
        }
        for (int i = 0; i < Math.min(list.size(), rows * perRow); i++) {
            Synergy syn = list.get(i);
            int r = i / perRow;
            int c = i % perRow;
            int px = x0 + c * step;
            int py = top + r * rowStep;
            boolean on = Synergies.isActive(skills, syn);
            boolean hov = mx >= px && mx < px + size && my >= py && my < py + size;
            if (hov) {
                hovered = i;
            }
            boolean sel = i == synergyPick;
            Object key = syn;
            click.begin(g, key, px, py, size, size, now);
            Stone.socket(g, px, py, size, size, on ? 1f : sel || hov ? 0.6f : near(skills, syn) ? 0.25f : 0f, sel, hov);
            ResourceLocation ic = SkillIcons.texture(partner(syn, skill));
            if (on || sel || hov) {
                Stone.icon(g, ic, px + (size - 16) / 2, py + (size - 16) / 2, 16, 1f);
            } else {
                Stone.greyIcon(g, ic, px + (size - 16) / 2, py + (size - 16) / 2, 16, near(skills, syn) ? 0.8f : 0.45f);
            }
            click.end(g, key, px, py, size, size, now);
            hits.add(new int[] {6, i, px, py, size, size});
        }
        int show = hovered >= 0 ? hovered : synergyPick;
        Synergy syn = list.get(show);
        Skill other = partner(syn, skill);
        List<String> chip = new ArrayList<>();
        chip.add(other == skill ? tr(syn.nameKey())
                : tr("proficiency.stone.syn_with", tr(syn.nameKey()), tr(other.translationKey())));
        chip.add(tr(I18n.exists(syn.shortKey()) ? syn.shortKey() : syn.descriptionKey()));
        StringBuilder needs = new StringBuilder();
        for (Synergy.Need need : syn.requires()) {
            Talent talent = need.talent();
            if (talent == null) {
                continue;
            }
            if (needs.length() > 0) {
                needs.append("  ·  ");
            }
            needs.append(tr("proficiency.stone.need", tr(talent.nameKey()), Math.min(need.minRank(), skills.rank(talent)),
                    need.minRank()));
        }
        chip.add(needs.toString());
        int cy0 = top + rows * rowStep;
        int chipW = 0;
        for (String s : chip) {
            chipW = Math.max(chipW, this.font.width(s));
        }
        int maxW = right - x0;
        chipW = Math.min(maxW, chipW + 16);
        int lines = chip.get(2).isEmpty() ? 2 : 3;
        int chipH = 8 + lines * 12;
        if (cy0 + chipH > iy1 - 2) {
            // Not enough slab under the row: the chip becomes the socket's tooltip.
            if (hovered >= 0) {
                for (String s : chip) {
                    if (!s.isEmpty()) {
                        tooltip.add(Component.literal(s));
                    }
                }
            }
            return;
        }
        int sockX = x0 + (show % perRow) * step;
        int cx0 = Math.min(sockX, right - chipW);
        Stone.block(g, cx0, cy0, chipW, chipH, 3, 1, false, 0x241C1A);
        Stone.outlineRound(g, cx0, cy0, chipW, chipH, 3, Stone.argb(255, 0xE68232));
        Stone.text(g, this.font, Stone.fit(this.font, chip.get(0), chipW - 16), cx0 + 8, cy0 + 6, Stone.GOLD);
        Stone.text(g, this.font, Stone.fit(this.font, chip.get(1), chipW - 16), cx0 + 8, cy0 + 18, Stone.INK);
        if (lines == 3) {
            Stone.text(g, this.font, Stone.fit(this.font, chip.get(2), chipW - 16), cx0 + 8, cy0 + 30, Stone.DIM);
        }
    }

    static boolean near(PlayerSkills skills, Synergy syn) {
        for (Synergy.Need need : syn.requires()) {
            Talent t = need.talent();
            if (t != null && skills.rank(t) > 0) {
                return true;
            }
        }
        return false;
    }

    // ---- Input ----------------------------------------------------------------------------------

    private boolean skipIntro() {
        if (page == Page.DIAL && !introSkipped && Util.getMillis() - openedAt < SkillstoneMath.INTRO_MS) {
            introSkipped = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (skipIntro()) {
            return true;
        }
        if (slam.active()) {
            // The unlock slam never holds the slab: a click lands it at once.
            slam.skip(clock());
            return true;
        }
        if (button != 0 || this.minecraft == null) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        long now = clock();
        PlayerSkills skills = playerSkills();
        mouseX -= hitDx;
        mouseY -= hitDy;
        for (int i = hits.size() - 1; i >= 0; i--) {
            int[] h = hits.get(i);
            if (mouseX < h[2] || mouseX >= h[2] + h[4] || mouseY < h[3] || mouseY >= h[3] + h[5]) {
                continue;
            }
            switch (h[0]) {
                case 0 -> { // rune
                    SkillCategory c = CATS[h[1]];
                    if (c != category) {
                        category = c;
                        lastCategory = c;
                        slateScroll = 0;
                        sound(SoundEvents.STONE_PLACE, 1.2f, 0.6f);
                        click.success(c, h[2], h[3], h[4], h[5], Stone.ember(0.7f), 0, 1f, now);
                    }
                    return true;
                }
                case 1 -> { // skill row
                    Skill s = Skill.VALUES[h[1]];
                    click.success(s, h[2] + 6, h[3] + 3, 16, 16, Stone.ember(0.7f), 1, 1f, now);
                    openFace(s, now);
                    return true;
                }
                case 2 -> { // corner sockets
                    ClickFx.plainClick();
                    switch (h[1]) {
                        case 0 -> this.minecraft.setScreen(new StoneExpertiseScreen(this));
                        case 1 -> this.minecraft.setScreen(new StoneLedgerScreen(this, true));
                        case 2 -> this.minecraft.setScreen(new StoneJournalScreen(this));
                        case 3 -> this.minecraft.setScreen(new StoneLedgerScreen(this, false));
                        default -> toggle();
                    }
                    return true;
                }
                case 3 -> { // tabs
                    if (h[1] == 0 && page != Page.FACE) {
                        ClickFx.plainClick();
                        turnTo(Page.FACE, now);
                    } else if (h[1] == 1 && page != Page.TREE) {
                        ClickFx.plainClick();
                        turnTo(Page.TREE, now);
                    } else if (h[1] == 2 && page != Page.HEARTH) {
                        if (dev.amman.proficiency.hearth.client.HearthView.panelState() != null) {
                            ClickFx.plainClick();
                            turnTo(Page.HEARTH, now);
                        } else {
                            refuse("tab2", h, tr("proficiency.stone.hearth_closed"), now);
                        }
                    }
                    return true;
                }
                case 4 -> { // back to the dial
                    ClickFx.plainClick();
                    page = Page.DIAL;
                    turnAt = now;
                    sound(SoundEvents.STONE_HIT, 0.8f, 0.4f);
                    init();
                    return true;
                }
                case 5 -> { // brazier: the points wait in the tree
                    if (skills != null && skills.pointsAvailable(skill) > 0) {
                        ClickFx.plainClick();
                        turnTo(Page.TREE, now);
                    } else {
                        refuse("brazier", h, tr("proficiency.stone.no_points"), now);
                    }
                    return true;
                }
                case 8 -> { // Hearth plate
                    hearth.click(h[1], h, now);
                    return true;
                }
                case 7 -> { // talent socket
                    if (skills != null) {
                        tree.click(skills, h[1], h, now);
                    }
                    return true;
                }
                case 6 -> { // synergy socket
                    synergyPick = h[1];
                    click.success(Synergies.involving(skill).get(h[1]), h[2], h[3], h[4], h[5], Stone.ember(0.7f), 0, 0.5f, now);
                    return true;
                }
                default -> {
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Overload refusal: the part wiggles hard, the slab jolts, a red flash and a thunk. */
    void refuse(Object key, int[] h, String why, long now) {
        click.no(key, h[2], h[3], h[4], h[5], now);
        refuseAt = now;
        if (!ClickFx.on()) {
            ClickFx.plainClick();
        }
        note = why;
        noteAt = now;
        notePage = page;
        noteHit = h.clone();
    }

    /** Off the dial a refusal's reason sits on a dark chip under the refused part, kept on the slab. */
    private void renderNote(GuiGraphics g, long now) {
        if (note == null || notePage != page || page == Page.DIAL || noteHit == null || now - noteAt >= NOTE_MS) {
            return;
        }
        float k = 1f - (now - noteAt) / (float) NOTE_MS;
        int fade = Math.round(255 * Math.min(1f, k * 3));
        String shown = Stone.fit(this.font, note, ix1 - ix0 - 16);
        int w = this.font.width(shown);
        int tx = Math.max(ix0 + 6, Math.min(noteHit[2] + noteHit[4] / 2 - w / 2, ix1 - 6 - w));
        int ty = noteHit[3] + noteHit[5] + 5;
        Stone.fillRound(g, tx - 4, ty - 3, w + 8, 13, 2, Stone.argb(Math.round(fade * 0.9f), 0x2A1210));
        Stone.outlineRound(g, tx - 4, ty - 3, w + 8, 13, 2, Stone.argb(fade, 0x8C2A1E));
        g.drawString(this.font, shown, tx, ty, Stone.argb(fade, 0xFF7864), true);
    }

    private static final long NOTE_MS = 2500;

    /** Turns the slab to another page of the same skill (the 180 ms edge-on turn). */
    private void turnTo(Page to, long now) {
        page = to;
        turnAt = now;
        sound(SoundEvents.STONE_PLACE, to == Page.TREE ? 0.8f : 0.9f, 0.6f);
        init();
    }

    private void openFace(Skill s, long now) {
        skill = s;
        page = Page.FACE;
        turnAt = now;
        shakeAt = now;
        shakeAmp = 2f;
        synergyPick = 0;
        sound(SoundEvents.STONE_PLACE, 0.9f, 0.7f);
        init();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Esc closes at once, even mid-opening; any other key only skips the opening.
        if (keyCode != 256 && skipIntro()) {
            return true;
        }
        if (slam.active()) {
            // Esc (or any key) lands the unlock slam at once; the next Esc closes.
            slam.skip(clock());
            return true;
        }
        // Backspace: from a Face or a tree back to the dial.
        if (keyCode == 259 && page != Page.DIAL) {
            page = Page.DIAL;
            turnAt = clock();
            init();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return scrolled(mouseX, mouseY, scrollY);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return scrolled(mouseX, mouseY, delta);
    }

    private boolean scrolled(double mouseX, double mouseY, double delta) {
        if (page == Page.TREE) {
            return tree.scroll(mouseX - hitDx, mouseY - hitDy, delta);
        }
        if (page == Page.FACE && oneColumn()) {
            return column.scroll(delta);
        }
        if (page == Page.DIAL && mouseX >= lx0 && mouseX < lx1 && mouseY >= ly0 && mouseY < ly1) {
            slateScroll -= (int) Math.round(delta * Math.max(12, rowH));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (page == Page.TREE && button == 0 && tree.drag(mouseX - hitDx, mouseY - hitDy, dragY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---- Layout audit hooks ---------------------------------------------------------------------

    /**
     * The one-column Face; PROFICIENCY_FACE_COLUMN=off shows the two-column one for before shots. Demo only: it
     * counts only while the layout demo runs (PROFICIENCY_LAYOUT_AUDIT and PROFICIENCY_LAYOUT_DEMO set).
     */
    private static final boolean COLUMN_OFF = "off".equals(System.getenv("PROFICIENCY_FACE_COLUMN"))
            && demoEnv("PROFICIENCY_LAYOUT_AUDIT") && demoEnv("PROFICIENCY_LAYOUT_DEMO");

    private static boolean demoEnv(String name) {
        String v = System.getenv(name);
        return v != null && !v.isEmpty();
    }

    private boolean oneColumn() {
        return SkillstoneMath.faceOneColumn(this.width) && !COLUMN_OFF;
    }

    /** For the layout audit: whether the Face is one column, and its content and view heights and scroll. */
    public String faceInfo() {
        return "oneColumn=" + oneColumn() + " gui=" + this.width + "x" + this.height
                + " slab=" + sw + "x" + sh + " " + column.info();
    }

    /** For the layout demo: the slab rect, x y w h. */
    public int[] slabRect() {
        return new int[] {sx, sy, sw, sh};
    }

    /** For the layout demo: open the face of {@code s} without the turn. */
    public void face(Skill s) {
        skill = s;
        slam.cancel();
        page = Page.FACE;
        introSkipped = true;
        if (this.minecraft != null) {
            init();
        }
    }

    /** For the layout demo: open the tree of {@code s}, {@code node} picked (null: none). */
    public void treeFor(Skill s, String node) {
        skill = s;
        slam.cancel();
        page = Page.TREE;
        introSkipped = true;
        if (this.minecraft != null) {
            init();
        }
        if (node != null) {
            tree.focus(node);
        }
    }

    /** Opens the Hearth face (the Hearth block's panel under {@code ui.style = MODERN}); keeps an open slab. */
    public static void openHearth() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof SkillstoneScreen open) {
            if (open.skill == null) {
                // From the dial: the Hearth face belongs to Homebuilder, so its Face and Tree tabs have a skill.
                open.skill = Skill.HOMEBUILDER;
            }
            if (open.page != Page.HEARTH) {
                open.turnTo(Page.HEARTH, clock());
            }
            return;
        }
        if (mc.screen == null) {
            SkillstoneScreen screen = new SkillstoneScreen(Skill.HOMEBUILDER);
            screen.page = Page.HEARTH;
            mc.setScreen(screen);
        }
    }

    /** For the frame sheets: play a stage and a pool tier rising on the Hearth face. */
    public void demoHearthRise() {
        hearth.demoRise(clock());
    }

    /** For the layout demo: skip the opening. */
    public void skipIntroForDemo() {
        introSkipped = true;
    }

    private static long demoT = -1;
    private static long demoBase = -1;
    private static long demoOffset;

    /** The screen's clock: the wall clock, or the frame sheets' frozen one. */
    static long clock() {
        return demoBase >= 0 ? demoBase + demoOffset : Util.getMillis();
    }

    /**
     * For the frame sheets: freeze the screen's clock and move it to {@code ms} after the freeze
     * (-1: back to the wall clock). Clicks and their answers then land on the frozen time.
     */
    public static void demoClock(long ms) {
        if (ms < 0) {
            demoBase = -1;
            return;
        }
        if (demoBase < 0) {
            demoBase = Util.getMillis();
        }
        demoOffset = ms;
    }

    /** For the frame sheets: play a rank landing on tree node {@code id} as if the server had answered. */
    public void demoLand(String id) {
        tree.demoLand(id, clock());
    }

    /**
     * For the layout demo: the unlock slam on this tree's root as if it had just filled and paid, the signature
     * waking (and the ability too at its level).
     */
    public void demoUnlockSlam() {
        Talent root = skill == null ? null : dev.amman.proficiency.perk.Talents.root(skill);
        if (root != null && page == Page.TREE) {
            tree.demoSlam(root.id(), clock());
        }
    }

    /** For the frame sheets: freeze the opening at {@code ms} (-1: run on the clock). */
    public static void demoTime(long ms) {
        demoT = ms;
    }

    /**
     * For the layout demo: the centre of a clickable part from the last frame. Kinds: 0 rune,
     * 1 skill row (index = skill ordinal), 2 corner socket, 3 tab, 4 back, 5 brazier, 6 synergy,
     * 7 talent socket (index in {@code Talents.of(skill)}), 8 Hearth plate (build, range, guide, keepers).
     */
    /** The dial's fire well (the why-stuck hover), in GUI pixels; the layout demo hovers it. */
    public int[] dialCentre() {
        return new int[] {cx, cy};
    }

    public int[] hitPoint(int kind, int index) {
        for (int[] h : hits) {
            if (h[0] == kind && h[1] == index) {
                return new int[] {h[2] + h[4] / 2, h[3] + h[5] / 2};
            }
        }
        return null;
    }
}
