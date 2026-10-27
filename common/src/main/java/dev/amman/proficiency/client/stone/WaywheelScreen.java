package dev.amman.proficiency.client.stone;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.math.Axis;
import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.client.AbilityContext;
import dev.amman.proficiency.client.ClickFx;
import dev.amman.proficiency.client.ClientInputEvents;
import dev.amman.proficiency.client.HomebuilderHud;
import dev.amman.proficiency.client.SkillIcons;
import dev.amman.proficiency.client.SkillstoneMath;
import dev.amman.proficiency.client.WaywheelMath;
import dev.amman.proficiency.config.ProficiencyClientConfig;
import dev.amman.proficiency.skill.ActiveService;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillCategory;
import dev.amman.proficiency.skill.SkillMath;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Waywheel (ui.style = modern, 1.33.0): the Skillstone's quick in-world layer. Hold the key and a carved
 * stone wheel opens under the cursor without pausing: nine category runes around the fire well, each lit by
 * its progress. Flick toward a rune and that category's skills spring out past the rim as stone tokens
 * (Combat's thirteen on two staggered rings). Release on a token to cast its ability, click one to open its
 * Face, right-click to pin it beside the hotbar ({@link WaywheelBelt}). Scroll turns to the next category.
 *
 * <p>Layout and timing rules are in {@link WaywheelMath} (core, unit tested). Written once against APIs both
 * Minecraft versions share; forge-1.20.1 compiles this file as it is (copySharedHearth).
 */
public final class WaywheelScreen extends Screen {

    private static final SkillCategory[] CATS = SkillCategory.values();
    private static final int READY = 0x8FD18F;
    private static final int COOL = 0xE08F8F;
    /** The category the wheel opened on last time, when the hands say nothing. */
    private static SkillCategory lastCategory = SkillCategory.GATHERING;

    /** One token: a skill, or one of Homebuilder's slots (the magnet switch, Homeward) after Construction's skills. */
    private record Token(@Nullable Skill skill, int extra) {
    }

    private final KeyMapping key;
    private final ClickFx click = new ClickFx();
    private final long openedAt;
    /** Opened with the key already up (a tap): no release will come, so a click casts and shift-click opens the Face. */
    private boolean sticky;
    private SkillCategory category;
    private long categoryAt;
    private List<Token> tokens = List.of();
    private double[][] points = new double[0][];
    private int hover = -1;
    private int hoverRune = -1;
    private long closeAt;
    private boolean done;
    @Nullable
    private String refusal;

    private int cx;
    private int cy;
    private int dial;
    private int tok;
    private int pit;
    private int runeHalf;
    /** The side plate's lines, wrapped once per change of what it says (1.34.0), not every frame. */
    @Nullable
    private PlateKey plateKey;
    private final List<String> plateLines = new ArrayList<>();
    private final List<Integer> plateColours = new ArrayList<>();
    private int plateX;
    private int plateY;
    private int plateW;
    private int plateH;

    /** What the plate shows: when none of it changes, neither do its wrapped lines. */
    private record PlateKey(int hover, int category, @Nullable String refusal, int level, boolean unlocked,
                            long cooldownSecs, boolean pinned, boolean extraOn, long runeAverage) {
    }

    public WaywheelScreen(KeyMapping key) {
        super(Component.translatable("proficiency.waywheel.title"));
        this.key = key;
        this.openedAt = clock();
        Player player = Minecraft.getInstance().player;
        Skill held = player == null ? null : AbilityContext.current(player);
        this.category = held != null ? held.category() : lastCategory;
        this.categoryAt = openedAt + (ClickFx.motion() ? WaywheelMath.OPEN_MS / 2 : -WaywheelMath.SPRING_MS * 4);
    }

    /** Opens the wheel for the key that is held now; true when it opened. */
    public static boolean open(Minecraft mc, KeyMapping key) {
        if (mc.player == null || mc.screen != null) {
            return false;
        }
        WaywheelScreen screen = new WaywheelScreen(key);
        screen.sticky = !physicallyDown(mc, key);
        mc.setScreen(screen);
        SkillstoneScreen.sound(SoundEvents.GRINDSTONE_USE, 1.6f, 0.18f);
        return true;
    }

    private static boolean physicallyDown(Minecraft mc, KeyMapping key) {
        InputConstants.Key k = InputConstants.getKey(key.saveString());
        long window = mc.getWindow().getWindow();
        if (k.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, k.getValue()) == GLFW.GLFW_PRESS;
        }
        return k.getValue() >= 0 && InputConstants.isKeyDown(window, k.getValue());
    }

    static long clock() {
        return SkillstoneScreen.clock();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        cx = this.width / 2;
        cy = this.height / 2;
        int[] fit = WaywheelMath.fit(this.width, this.height);
        dial = WaywheelMath.WHEELS[fit[0]];
        tok = fit[1];
        pit = (int) (dial * .44);
        runeHalf = dial >= 128 ? 14 : dial >= 91 ? 12 : dial >= 61 ? 10 : 9;
        plateKey = null;
        layout();
    }

    private void layout() {
        Player player = this.minecraft == null ? null : this.minecraft.player;
        List<Token> list = new ArrayList<>();
        for (Skill s : SkillstoneScreen.skillsOf(category)) {
            list.add(new Token(s, -1));
        }
        if (category == Skill.HOMEBUILDER.category() && player != null) {
            for (int extra : HomebuilderHud.wheelSlots(player)) {
                list.add(new Token(null, extra));
            }
        }
        tokens = list;
        double angle = WaywheelMath.runeAngle(category.ordinal(), CATS.length);
        points = WaywheelMath.tokens(list.size(), angle, dial, tok, true);
        if (!WaywheelMath.inside(points, cx, cy, tok, this.width, this.height)) {
            double[][] flat = WaywheelMath.tokens(list.size(), angle, dial, tok, false);
            if (WaywheelMath.inside(flat, cx, cy, tok, this.width, this.height)) {
                points = flat;
            }
        }
        hover = -1;
    }

    private void choose(SkillCategory c, long now) {
        if (c == category) {
            return;
        }
        category = c;
        lastCategory = c;
        categoryAt = now;
        layout();
        // The rune catches: a struck stone and a short flare of its socket.
        SkillstoneScreen.sound(SoundEvents.STONE_HIT, 0.9f + 0.05f * c.ordinal(), 0.3f);
        int[] r = runePoint(c.ordinal());
        click.burst(c, r[0], r[1], runeHalf * 2, 0xFFFFB050, 0, now);
    }

    private int[] runePoint(int i) {
        double a = WaywheelMath.runeAngle(i, CATS.length);
        return new int[] {(int) Math.round(cx + dial * Math.cos(a)), (int) Math.round(cy + dial * Math.sin(a))};
    }

    /** Screen centre of token {@code i}; the layout demo hovers it. */
    public int[] tokenPoint(int i) {
        return new int[] {(int) Math.round(cx + points[i][0]), (int) Math.round(cy + points[i][1])};
    }

    /** Index of {@code s}'s token in the open category, or -1. */
    public int tokenOf(Skill s) {
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i).skill() == s) {
                return i;
            }
        }
        return -1;
    }

    /** For the layout demo: the centre of rune {@code i} (0 = Combat, clockwise). */
    public int[] runeAt(int i) {
        return runePoint(i);
    }

    /** For the layout demo: let go of the key over whatever is hovered. */
    public void demoRelease() {
        release();
    }

    /** For the layout demo: turn to {@code c} at once. */
    public void showCategory(SkillCategory c) {
        choose(c, clock());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Player player = this.minecraft == null ? null : this.minecraft.player;
        if (player == null) {
            return;
        }
        long now = clock();
        if (closeAt > 0 && now >= closeAt) {
            closeAt = 0;
            onClose();
            return;
        }
        PlayerSkills skills = ProficiencyAttachments.of(player);
        long game = player.level().getGameTime();
        g.fillGradient(0, 0, this.width, this.height, 0x38000000, 0x60000000);

        long t = now - openedAt;
        boolean motion = ClickFx.motion();
        float scale = motion ? WaywheelMath.open(t) : 1f;
        float spin = motion ? WaywheelMath.spin(t) : 0f;

        // Hover: a token first (they reach over other wedges); else the flick inside the rim picks a rune.
        double dx = mouseX - cx;
        double dy = mouseY - cy;
        double r = Math.hypot(dx, dy);
        int newHover = done ? hover : WaywheelMath.nearest(dx, dy, points, tok * 0.72);
        if (!done && newHover < 0 && r > pit * 0.6 && r <= dial + 20 && t >= WaywheelMath.OPEN_MS / 2) {
            choose(CATS[WaywheelMath.rune(dx, dy, CATS.length)], now);
        }
        if (newHover != hover) {
            hover = newHover;
            refusal = null;
            if (newHover >= 0) {
                SkillstoneScreen.sound(SoundEvents.STONE_BUTTON_CLICK_ON, 1.6f + 0.04f * (newHover % 5), 0.22f);
            }
        }
        hoverRune = !done && r > pit * 0.6 && r <= dial + 20 ? WaywheelMath.rune(dx, dy, CATS.length) : -1;

        // ---- The disc, spun and scaled open from the hub.
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        if (scale != 1f) {
            g.pose().scale(scale, scale, 1f);
        }
        if (spin != 0f) {
            g.pose().mulPose(Axis.ZP.rotationDegrees(spin));
        }
        g.pose().translate(-cx, -cy, 0);
        int dn = 2 * (dial + 22);
        g.blit(Stone.tex("wheel_" + dial), cx - dn / 2, cy - dn / 2, 0f, 0f, dn, dn, dn, dn);
        Token ht = hover >= 0 ? tokens.get(hover) : null;
        float fire = ht != null ? 0.45f : 1f;
        int halo = Math.round(pit * 1.6f);
        Stone.light(g, cx, cy, halo, halo, Stone.WARM, 0.75f);
        int pn = 2 * (pit + 5);
        g.blit(Stone.tex("pit_" + pit), cx - pn / 2, cy - pn / 2, 0f, 0f, pn, pn, pn, pn);
        int frame = SkillstoneMath.fireFrame(t, 8);
        Stone.blitAlpha(g, Stone.tex("fire_" + pit), cx - pn / 2, cy - pn / 2, pn, pn, 0f, frame * pn, pn, pn * 8, fire);
        if (ht != null) {
            // The hub shows the hovered skill, its icon held over the low fire.
            int is = pit >= 40 ? 32 : 16;
            if (ht.skill() != null) {
                Stone.icon(g, SkillIcons.texture(ht.skill()), cx - is / 2, cy - is / 2 - 2, is, 1f);
            } else {
                HomebuilderHud.drawSlotIcon(g, ht.extra(), cx - is / 2, cy - is / 2 - 2, is, false);
            }
        }
        int sel = category.ordinal();
        for (int i = 0; i < CATS.length; i++) {
            int[] p = runePoint(i);
            double a = WaywheelMath.runeAngle(i, CATS.length);
            boolean isSel = i == sel;
            boolean hov = i == hoverRune && !isSel;
            if (isSel) {
                Stone.groove(g, (int) Math.round(cx + (pit + 5) * Math.cos(a)), (int) Math.round(cy + (pit + 5) * Math.sin(a)),
                        (int) Math.round(cx + (dial - runeHalf - 1) * Math.cos(a)),
                        (int) Math.round(cy + (dial - runeHalf - 1) * Math.sin(a)), 1f, dial >= 61 ? 3 : 2);
            }
            float lit = SkillstoneMath.heat(SkillstoneScreen.average(skills, CATS[i]));
            float shown = isSel ? 1.3f : hov ? Math.max(lit, 0.9f) : lit;
            int s = runeHalf;
            click.begin(g, CATS[i], p[0] - s, p[1] - s, 2 * s, 2 * s, now);
            Stone.socket(g, p[0] - s, p[1] - s, 2 * s, 2 * s, shown, isSel, hov);
            int is = 16;
            if (shown < 0.08f) {
                Stone.greyIcon(g, SkillstoneScreen.runeIcon(i), p[0] - is / 2, p[1] - is / 2, is, 0.5f);
            } else {
                Stone.icon(g, SkillstoneScreen.runeIcon(i), p[0] - is / 2, p[1] - is / 2, is, Math.min(1f, 0.45f + shown));
            }
            click.end(g, CATS[i], p[0] - s, p[1] - s, 2 * s, 2 * s, now);
        }
        g.pose().popPose();

        // ---- The tokens, springing out past the rim one after another.
        long since = now - categoryAt;
        int[] rp = runePoint(sel);
        if (ht != null && hover < points.length) {
            int[] tp = tokenPoint(hover);
            // A molten channel from the rune to the token under the cursor.
            Stone.groove(g, rp[0], rp[1], tp[0], tp[1], 1f, 2);
        }
        List<String> pins = WaywheelBelt.pins();
        for (int i = 0; i < tokens.size() && i < points.length; i++) {
            float k = motion ? WaywheelMath.spring(since, i) : 1f;
            if (k <= 0.02f) {
                continue;
            }
            int[] p = tokenPoint(i);
            int x0 = p[0] - tok / 2;
            int y0 = p[1] - tok / 2;
            g.pose().pushPose();
            if (k != 1f) {
                // Out of the rune: from its socket to the token's place as it grows.
                float m = Math.min(1f, k);
                g.pose().translate(rp[0] + (p[0] - rp[0]) * m, rp[1] + (p[1] - rp[1]) * m, 0);
                g.pose().scale(k, k, 1f);
                g.pose().translate(-p[0], -p[1], 0);
            }
            Token tk = tokens.get(i);
            click.begin(g, tk, x0, y0, tok, tok, now);
            drawToken(g, tk, x0, y0, i == hover, skills, game, player, pins);
            click.end(g, tk, x0, y0, tok, tok, now);
            g.pose().popPose();
        }
        click.render(g, now, 1f);

        // ---- The side plate: what the token (or the rune) under the cursor is.
        renderPlate(g, skills, game, player, pins);
        renderHint(g);
    }

    private void drawToken(GuiGraphics g, Token tk, int x0, int y0, boolean hov, PlayerSkills skills, long game,
            Player player, List<String> pins) {
        int n = WaywheelToken.baked(tok);
        if (tk.skill() == null) {
            boolean off = tk.extra() == HomebuilderHud.SLOT_MAGNET ? HomebuilderHud.magnetOff()
                    : !HomebuilderHud.homewardReady(player);
            if (!off || hov) {
                int rad = Math.round(n * (hov ? 1.2f : 1.1f));
                Stone.light(g, x0 + n / 2, y0 + n / 2, rad, rad, Stone.WARM, hov ? 0.6f : 0.5f);
            }
            WaywheelToken.base(g, x0, y0, n, hov ? WaywheelToken.RAISED : off ? WaywheelToken.DARK : WaywheelToken.REST, 1f);
            int is = WaywheelToken.iconSide(n);
            HomebuilderHud.drawSlotIcon(g, tk.extra(), x0 + (n - is) / 2, y0 + (n - is) / 2, is, off);
            return;
        }
        Skill s = tk.skill();
        WaywheelToken.skill(g, s, x0, y0, n, hov, pins.contains(s.id()), skills, game, 1f);
    }

    private void renderPlate(GuiGraphics g, PlayerSkills skills, long game, Player player, List<String> pins) {
        Token tk = hover >= 0 ? tokens.get(hover) : null;
        PlateKey key;
        if (tk != null && tk.skill() != null) {
            Skill s = tk.skill();
            boolean unlocked = ActiveService.unlocked(skills, s);
            long cd = unlocked ? skills.cooldownRemaining(s, game) : 0;
            key = new PlateKey(hover, category.ordinal(), refusal, skills.level(s), unlocked, cd > 0 ? cd / 20 + 1 : 0,
                    pins.contains(s.id()), false, 0);
        } else if (tk != null) {
            boolean on = tk.extra() == HomebuilderHud.SLOT_MAGNET ? !HomebuilderHud.magnetOff()
                    : HomebuilderHud.homewardReady(player);
            key = new PlateKey(hover, category.ordinal(), refusal, 0, false, 0, false, on, 0);
        } else {
            key = new PlateKey(-1, category.ordinal(), refusal, 0, false, 0, false, false,
                    Math.round(SkillstoneScreen.average(skills, category)));
        }
        if (!key.equals(plateKey)) {
            plateKey = key;
            layoutPlate(skills, game, player, pins, tk);
        }
        Stone.block(g, Stone.INNER, 100, 40, plateX, plateY, plateW, plateH, 5, 2, false);
        Stone.well(g, plateX + 4, plateY + 4, plateW - 8, plateH - 8);
        int y = plateY + 6;
        for (int i = 0; i < plateLines.size(); i++) {
            Stone.text(g, this.font, plateLines.get(i), plateX + 6, y, plateColours.get(i));
            y += 10;
        }
    }

    /** Builds, wraps and fits the plate's lines and places the plate; only when its key changed. */
    private void layoutPlate(PlayerSkills skills, long game, Player player, List<String> pins, @Nullable Token tk) {
        List<String> lines = new ArrayList<>();
        List<Integer> colours = new ArrayList<>();
        if (tk != null && tk.skill() != null) {
            Skill s = tk.skill();
            lines.add(Component.translatable(s.translationKey()).getString());
            colours.add(Stone.GOLD);
            lines.add(SkillstoneScreen.tr("proficiency.stone.level", skills.level(s)));
            colours.add(Stone.INK);
            lines.add(Component.translatable(s.activeKey()).getString());
            colours.add(Stone.INK);
            if (!ActiveService.unlocked(skills, s)) {
                lines.add(SkillstoneScreen.abilityLock(skills, s, "proficiency.waywheel.locked"));
                colours.add(Stone.LOCK);
            } else {
                long cd = skills.cooldownRemaining(s, game);
                if (cd > 0) {
                    long secs = cd / 20;
                    lines.add(Component.translatable("proficiency.wheel.cooldown",
                            String.format(Locale.ROOT, "%d:%02d", secs / 60, secs % 60)).getString());
                    colours.add(COOL);
                } else {
                    lines.add(Component.translatable("proficiency.wheel.ready").getString());
                    colours.add(READY);
                }
            }
            if (pins.contains(s.id())) {
                lines.add(SkillstoneScreen.tr("proficiency.waywheel.pinned"));
                colours.add(Stone.DIM);
            }
        } else if (tk != null) {
            boolean magnet = tk.extra() == HomebuilderHud.SLOT_MAGNET;
            if (magnet) {
                boolean off = HomebuilderHud.magnetOff();
                lines.add(Component.translatable("proficiency.wheel.magnet").getString());
                colours.add(Stone.GOLD);
                lines.add(Component.translatable(off ? "proficiency.wheel.magnet.off" : "proficiency.wheel.magnet.on").getString());
                colours.add(off ? COOL : READY);
            } else {
                boolean ready = HomebuilderHud.homewardReady(player);
                lines.add(Component.translatable("proficiency.talent.homebuilder.homeward").getString());
                colours.add(Stone.GOLD);
                lines.add(Component.translatable(ready ? "proficiency.wheel.ready" : "proficiency.wheel.homeward.used").getString());
                colours.add(ready ? READY : COOL);
            }
        } else {
            lines.add(Component.translatable(category.translationKey()).getString());
            colours.add(Stone.GOLD);
            lines.add(SkillstoneScreen.tr("proficiency.stone.rune", Math.round(SkillstoneScreen.average(skills, category))));
            colours.add(Stone.INK);
            lines.add(SkillstoneScreen.tr("proficiency.waywheel.skills", SkillstoneScreen.skillsOf(category).size()));
            colours.add(Stone.DIM);
        }
        if (refusal != null) {
            // A refusal that repeats a line the plate already shows (the lock reason) recolours that line instead.
            int same = lines.indexOf(refusal);
            if (same >= 0) {
                colours.set(same, COOL);
            } else {
                lines.add(refusal);
                colours.add(COOL);
            }
        }
        // Opposite the open category's side, so the plate never covers its tokens.
        double a = WaywheelMath.runeAngle(category.ordinal(), CATS.length);
        int reach = WaywheelMath.ring2(dial, tok) + tok / 2;
        int side = Math.max(0, cx - reach - 12);
        int pw = Math.max(60, Math.min(130, side));
        List<String> wrapped = new ArrayList<>();
        List<Integer> wc = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            for (FormattedText part : this.font.getSplitter().splitLines(lines.get(i), pw - 12, Style.EMPTY)) {
                wrapped.add(part.getString());
                wc.add(colours.get(i));
            }
        }
        int ph = 10 + wrapped.size() * 10;
        int px = Math.cos(a) > 0.2 ? Math.max(4, cx - reach - 8 - pw) : Math.min(this.width - 4 - pw, cx + reach + 8);
        if (side < 60) {
            px = Math.cos(a) > 0.2 ? 4 : this.width - 4 - pw;
        }
        plateX = px;
        plateY = Math.max(4, Math.min(this.height - ph - 4, cy - ph / 2));
        plateW = pw;
        plateH = ph;
        plateLines.clear();
        plateColours.clear();
        for (int i = 0; i < wrapped.size(); i++) {
            plateLines.add(Stone.fit(this.font, wrapped.get(i), pw - 12));
            plateColours.add(wc.get(i));
        }
    }

    private void renderHint(GuiGraphics g) {
        String hint = SkillstoneScreen.tr(sticky ? "proficiency.waywheel.hint_tap" : "proficiency.waywheel.hint");
        int w = this.font.width(hint);
        // Under the wheel and the open category's tokens when that clears the hearts and armour rows above
        // the hotbar, else at the top (the category is then at the bottom, so the top is clear).
        // With neither free (a small window with the category at the top), the hint is left out: it would
        // cover the tokens it explains.
        int bottom = cy + dial + 22;
        int top = cy - dial - 22;
        for (double[] p : points) {
            bottom = Math.max(bottom, (int) Math.round(cy + p[1]) + tok / 2);
            top = Math.min(top, (int) Math.round(cy + p[1]) - tok / 2);
        }
        int y;
        if (bottom + 8 + 10 <= this.height - 62) {
            y = bottom + 8;
        } else if (top >= 16) {
            y = 4;
        } else {
            return;
        }
        String shown = Stone.fit(this.font, hint, this.width - 8);
        w = this.font.width(shown);
        g.fill(this.width / 2 - w / 2 - 4, y - 2, this.width / 2 + w / 2 + 4, y + 10, 0x70000000);
        Stone.textCentred(g, this.font, shown, this.width / 2, y, Stone.INK);
    }

    // ---- Input ------------------------------------------------------------------------------------

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (key.matches(keyCode, scanCode)) {
            release();
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (key.matchesMouse(button)) {
            release();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (done) {
            return true;
        }
        int at = WaywheelMath.nearest(mouseX - cx, mouseY - cy, points, tok * 0.72);
        if (button == 1 && at >= 0 && tokens.get(at).skill() != null) {
            pin(at);
            return true;
        }
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (at >= 0) {
            hover = at;
            Token tk = tokens.get(at);
            if (tk.skill() == null || (sticky && !hasShiftDown())) {
                fire();
            } else {
                // Click: the skill's Face on the slab.
                done = true;
                SkillstoneScreen.sound(SoundEvents.STONE_PLACE, 0.9f, 0.6f);
                Minecraft.getInstance().setScreen(new SkillstoneScreen(tk.skill()));
            }
            return true;
        }
        double r = Math.hypot(mouseX - cx, mouseY - cy);
        if (r <= pit + 4) {
            // The fire well: the whole Skillstone.
            done = true;
            Minecraft.getInstance().setScreen(new SkillstoneScreen());
            return true;
        }
        if (r <= dial + 20) {
            choose(CATS[WaywheelMath.rune(mouseX - cx, mouseY - cy, CATS.length)], clock());
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return scrolled(scrollY);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return scrolled(delta);
    }

    private boolean scrolled(double delta) {
        if (done || delta == 0) {
            return false;
        }
        int step = delta < 0 ? 1 : CATS.length - 1;
        choose(CATS[(category.ordinal() + step) % CATS.length], clock());
        return true;
    }

    /** Right-click: on or off the belt beside the hotbar. */
    private void pin(int at) {
        Skill s = tokens.get(at).skill();
        String before = ProficiencyClientConfig.uiWaywheelPins();
        String after = WaywheelMath.toggle(before, s.id(), id -> Skill.byId(id) != null);
        ProficiencyClientConfig.setUiWaywheelPins(after);
        boolean on = WaywheelBelt.pins().contains(s.id());
        int[] p = tokenPoint(at);
        long now = clock();
        if (on) {
            click.success(tokens.get(at), p[0] - tok / 2, p[1] - tok / 2, tok, tok, Stone.argb(255, Stone.GOLD), 0, 0.5f, now);
            SkillstoneScreen.sound(SoundEvents.ANVIL_PLACE, 1.8f, 0.25f);
        } else {
            SkillstoneScreen.sound(SoundEvents.STONE_BUTTON_CLICK_OFF, 0.8f, 0.4f);
        }
    }

    private void release() {
        if (done) {
            return;
        }
        if (WaywheelMath.stickOnRelease(key == dev.amman.proficiency.client.ProficiencyClient.WAYWHEEL,
                clock() - openedAt, hover >= 0)) {
            // A tap of the Waywheel's own key: it was still down when the wheel opened, so stay open as a
            // sticky wheel (click casts, shift-click opens the Face) instead of closing on the release.
            sticky = true;
            return;
        }
        if (hover < 0) {
            // Let go off any token: nothing is cast.
            done = true;
            onClose();
            return;
        }
        fire();
    }

    private void fire() {
        Token tk = tokens.get(hover);
        int[] p = tokenPoint(hover);
        long now = clock();
        if (tk.skill() == null) {
            done = true;
            onClose();
            StoneNet.homebuilder(tk.extra() == HomebuilderHud.SLOT_MAGNET);
            return;
        }
        Skill s = tk.skill();
        Player player = Minecraft.getInstance().player;
        PlayerSkills skills = player == null ? null : ProficiencyAttachments.of(player);
        if (skills == null) {
            onClose();
            return;
        }
        if (!ActiveService.unlocked(skills, s)) {
            refuse(SkillstoneScreen.abilityLock(skills, s, "proficiency.waywheel.locked"), p, now);
            return;
        }
        long cd = skills.cooldownRemaining(s, player.level().getGameTime());
        if (cd > 0) {
            long secs = cd / 20;
            refuse(Component.translatable("proficiency.wheel.cooldown",
                    String.format(Locale.ROOT, "%d:%02d", secs / 60, secs % 60)).getString(), p, now);
            return;
        }
        done = true;
        // The cast must not wait on blocked input: the wheel goes at once. The HUD then folds the disc and
        // flies the token to its belt socket, which flares as it lands (or, off the belt, the token lifts
        // and burns away); the ability's own banner (AbilityFx) answers too.
        WaywheelBelt.cast(s, p[0], p[1], WaywheelToken.baked(tok), dial, cx, cy);
        onClose();
        ClientInputEvents.cast(s);
    }

    /** No: a hard wiggle, a red flash and a thunk on the token, the reason on the plate, then the wheel goes. */
    private void refuse(String why, int[] p, long now) {
        done = true;
        refusal = why;
        Token tk = tokens.get(hover);
        click.no(tk, p[0] - tok / 2, p[1] - tok / 2, tok, tok, now);
        if (!ClickFx.on()) {
            ClickFx.thunk();
        }
        closeAt = now + 420L;
    }
}
