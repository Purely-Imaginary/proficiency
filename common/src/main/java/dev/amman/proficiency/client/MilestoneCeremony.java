package dev.amman.proficiency.client;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.client.stone.Stone;
import dev.amman.proficiency.config.ProficiencyClientConfig;
import dev.amman.proficiency.platform.bus.Dist;
import dev.amman.proficiency.platform.bus.EventBusSubscriber;
import dev.amman.proficiency.platform.bus.SubscribeEvent;
import dev.amman.proficiency.platform.client.event.RenderGuiEvent;
import dev.amman.proficiency.skill.MilestoneTiers;
import dev.amman.proficiency.skill.Skill;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.FireworkExplosion;

import java.util.ArrayList;
import java.util.List;

/**
 * Level 10, 25, 50, 75 and 100 in a skill crown the ordinary level-up (which still plays, untouched):
 * a banner naming the milestone and what it opened, a firework in the skill's colour over the player
 * and a rising fanfare, each tier bigger than the last. Level 100 is the gold Mastered ceremony: a
 * full-width banner with a slow shine, the skill icon stamped with a gold rim, a ring of fireworks,
 * bells and a cheering crowd. Under the MODERN style the banner is carved Skillstone with ember; under
 * CLASSIC it is the letters-on-the-sky look of the discovery banner.
 *
 * <p>The server decides which ceremony plays and when ({@code MilestoneTiers}, once per skill per
 * player); this class only plays it. The script (sounds, fireworks, easing) is {@link MilestoneFx},
 * read against a wall clock, so it is the same at any frame rate. Setting {@code ui.milestoneCeremonies};
 * Distortion Effects 0% stops the banner's movement and keeps the flashes, fireworks and sounds.
 */
@EventBusSubscriber(modid = Proficiency.MOD_ID, value = Dist.CLIENT)
public final class MilestoneCeremony {

    private static final int GOLD = 0xFFD24A;
    private static final int GOLD_DARK = 0x9A6A12;
    private static final int GOLD_LIGHT = 0xFFF1B0;

    private static Skill skill;
    private static int tier;
    private static long startedAt;
    private static boolean active;
    private static List<MilestoneFx.Cue> cues = List.of();
    private static List<MilestoneFx.Burst> bursts = List.of();
    private static int cueIndex;
    private static int burstIndex;
    private static List<String> lines = List.of();

    private MilestoneCeremony() {
    }

    /** From the milestone packet. Client thread. */
    public static void start(Skill skill, int tier, int procUnlock, int abilityUnlock) {
        if (!ProficiencyClientConfig.uiMilestoneCeremonies() || MilestoneTiers.index(tier) < 0) {
            return;
        }
        MilestoneCeremony.skill = skill;
        MilestoneCeremony.tier = tier;
        startedAt = System.currentTimeMillis();
        cues = MilestoneFx.cues(tier);
        bursts = MilestoneFx.bursts(tier);
        cueIndex = 0;
        burstIndex = 0;
        lines = openedLines(skill, tier, procUnlock, abilityUnlock);
        active = true;
    }

    /** Leaving a world: a ceremony from the last one must not finish in this one. */
    public static void clear() {
        active = false;
        skill = null;
    }

    public static boolean active() {
        return active;
    }

    /** Milliseconds since the ceremony began, -1 with none running (the layout demo's frame sheets wait on this). */
    static long ageMs() {
        return active ? System.currentTimeMillis() - startedAt : -1L;
    }

    /** What the tier opened, as up to three short lines. */
    static List<String> openedLines(Skill skill, int tier, int procUnlock, int abilityUnlock) {
        MilestoneTiers.Opened opened = MilestoneTiers.opened(skill, tier, procUnlock, abilityUnlock);
        List<String> out = new ArrayList<>();
        if (opened.treeNodes() > 0) {
            out.add(Component.translatable(opened.treeNodes() == 1 ? "proficiency.milestone.opened.tree.one"
                    : "proficiency.milestone.opened.tree", opened.treeNodes()).getString());
        }
        if (opened.signature()) {
            out.add(Component.translatable("proficiency.milestone.opened.signature").getString());
        }
        if (opened.ability()) {
            out.add(Component.translatable("proficiency.milestone.opened.ability").getString());
        }
        if (opened.stars()) {
            out.add(Component.translatable("proficiency.milestone.opened.stars").getString());
        }
        if (opened.expertise()) {
            out.add(Component.translatable("proficiency.milestone.opened.expertise").getString());
        }
        return out.size() > 3 ? out.subList(0, 3) : out;
    }

    // ---- The clock ------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!active) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            active = false;
            return;
        }
        long age = System.currentTimeMillis() - startedAt;
        if (age >= MilestoneFx.durationMs(tier)) {
            active = false;
            return;
        }
        while (cueIndex < cues.size() && cues.get(cueIndex).atMs() <= age) {
            playCue(minecraft, cues.get(cueIndex++));
        }
        while (burstIndex < bursts.size() && bursts.get(burstIndex).atMs() <= age) {
            fire(minecraft, minecraft.player, bursts.get(burstIndex++));
        }
        if (minecraft.options.hideGui) {
            return;
        }
        draw(event.getGuiGraphics(), minecraft.font, age);
    }

    // ---- Sound and fireworks ---------------------------------------------------------------------

    private static void playCue(Minecraft minecraft, MilestoneFx.Cue cue) {
        SoundEvent sound = switch (cue.sound()) {
            case CHIME -> SoundEvents.NOTE_BLOCK_CHIME.value();
            case LEVELUP -> SoundEvents.PLAYER_LEVELUP;
            case TOAST -> SoundEvents.UI_TOAST_CHALLENGE_COMPLETE;
            case BELL -> SoundEvents.BELL_BLOCK;
            case CROWD -> SoundEvents.VILLAGER_CELEBRATE;
            case LAUNCH -> SoundEvents.FIREWORK_ROCKET_LAUNCH;
        };
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, cue.pitch(), cue.volume()));
    }

    /** One firework, ahead of and above the player, in the skill's colour (gold in the gold ones). */
    private static void fire(Minecraft minecraft, Player player, MilestoneFx.Burst burst) {
        ClientLevel level = minecraft.level;
        if (level == null || skill == null) {
            return;
        }
        double yaw = Math.toRadians(player.getYRot() + burst.angle());
        double x = player.getX() - Math.sin(yaw) * burst.radius() * MilestoneFx.DISTANCE;
        double z = player.getZ() + Math.cos(yaw) * burst.radius() * MilestoneFx.DISTANCE;
        double y = player.getEyeY() + burst.height() * MilestoneFx.DISTANCE * 0.8 - 1.0;
        int accent = SkillPalette.accent(skill.category()) & 0xFFFFFF;
        int i = MilestoneFx.index(tier);
        IntArrayList colours = new IntArrayList();
        colours.add(burst.gold() ? GOLD : accent);
        colours.add(burst.gold() ? accent : GOLD_LIGHT);
        IntArrayList fade = new IntArrayList();
        fade.add(0xFFFFFF);
        fade.add(burst.gold() ? GOLD : accent);
        FireworkExplosion.Shape shape = FireworkExplosion.Shape.values()[Math.max(0, Math.min(4, burst.shape()))];
        level.createFireworks(x, y, z, 0, 0, 0,
                List.of(new FireworkExplosion(shape, colours, fade, i >= 2, i >= 1)));
    }

    // ---- The banner -------------------------------------------------------------------------------

    private static void draw(GuiGraphics g, Font font, long age) {
        float alpha = MilestoneFx.alpha(tier, age);
        if (alpha <= 0.02f) {
            return;
        }
        boolean mastered = tier >= 100;
        boolean modern = ProficiencyClientConfig.uiStyle() == UiStyle.MODERN;
        boolean motion = ClickFx.motion();
        int guiW = g.guiWidth();
        int guiH = g.guiHeight();
        String name = Component.translatable(skill.translationKey()).getString();
        String rank = Component.translatable("proficiency.milestone.rank." + tier).getString();
        int accent = SkillPalette.accent(skill.category()) & 0xFFFFFF;

        // The slide: down from above on the way in, back up on the way out (no slide at 0% Distortion).
        float in = MilestoneFx.slide(tier, age);
        float outT = 1f - MilestoneFx.clamp01((MilestoneFx.durationMs(tier) - age) / (float) MilestoneFx.FADE_OUT_MS);
        float drop = motion ? (1f - in) * -1f - outT * outT : 0f;

        if (mastered) {
            drawMastered(g, font, age, alpha, guiW, guiH, name, rank, accent, modern, drop, motion);
        } else if (modern) {
            drawStone(g, font, age, alpha, guiW, guiH, name, rank, accent, drop, motion);
        } else {
            drawClassic(g, font, age, alpha, guiW, guiH, name, rank, accent);
        }
    }

    /** The carved banner: Skillstone slab, ember groove, the skill's rune in a socket, the lines it opened. */
    private static void drawStone(GuiGraphics g, Font font, long age, float alpha, int guiW, int guiH, String name,
            String rank, int accent, float drop, boolean motion) {
        int i = MilestoneFx.index(tier);
        float k = 1.0f + 0.1f * i;
        String kicker = Component.translatable("proficiency.milestone.kicker", rank).getString();
        String title = Component.translatable("proficiency.milestone.title", name, tier).getString();
        int titleW = Math.round(font.width(title) * 1.5f);
        int textW = Math.max(titleW, font.width(kicker));
        for (String line : lines) {
            textW = Math.max(textW, font.width(line) + 8);
        }
        int w = 8 + 36 + 10 + textW + 14;
        w = Math.max(w, 170 + 28 * i);
        int h = 10 + 9 + 3 + 14 + 3 + lines.size() * 10 + 12;
        h = Math.max(h, 52);
        float fit = Math.min(k, (guiW - 8) / (float) w);
        int x = -w / 2;
        int top = Math.round(guiH * 0.10f);
        g.pose().pushPose();
        g.pose().translate(guiW / 2f, top + drop * (h * fit + top), 0f);
        g.pose().scale(fit, fit, 1f);

        float pulse = 0.5f + 0.5f * (float) Math.sin(age / 260.0);
        // The glow behind the slab, in the skill's colour: bigger and brighter at the higher tiers.
        Stone.light(g, 0, h / 2, w / 2 + 16 + 6 * i, h / 2 + 12 + 4 * i, accent, (0.22f + 0.07f * i) * alpha);
        Stone.slab(g, x, 0, w, h, 4);
        // A thin band of the skill's colour under the rim, so each skill reads at a glance.
        g.fill(x + 6, 5, x + w - 6, 7, Stone.argb(200, accent));

        int sx = x + 10;
        int sy = (h - 36) / 2 + 1;
        Stone.socket(g, sx, sy, 36, 36, 1f, false, false);
        Stone.icon(g, SkillIcons.texture(skill), sx + 6, sy + 6, 24, alpha);
        Stone.light(g, sx + 18, sy + 18, 26, 26, Stone.WARM, (0.5f + 0.35f * pulse) * alpha);

        int tx = sx + 36 + 10;
        int ty = 11;
        g.drawString(font, kicker, tx, ty, Stone.argb(Math.round(255 * alpha), Stone.HEAD), true);
        g.pose().pushPose();
        g.pose().translate(tx, ty + 11, 0f);
        g.pose().scale(1.5f, 1.5f, 1f);
        g.drawString(font, title, 0, 0, Stone.argb(Math.round(255 * alpha), Stone.GOLD), true);
        g.pose().popPose();
        int ly = ty + 11 + 14 + 3;
        for (String line : lines) {
            g.fill(tx, ly + 3, tx + 3, ly + 6, Stone.argb(Math.round(255 * alpha), Stone.ember(0.8f)));
            g.drawString(font, TextFit.clip(font, line, x + w - 12 - (tx + 8)), tx + 8, ly,
                    Stone.argb(Math.round(255 * alpha), Stone.INK), true);
            ly += 10;
        }
        // The ember groove along the foot pours in from the left in the first 700 ms.
        float pour = HudMath.smooth(age / 700f);
        Stone.groove(g, x + 10, x + w - 10, h - 8, pour, 3, true);
        // The shine: one quick sweep across the slab.
        shine(g, MilestoneFx.shine(tier, age), x, 0, w, h, 0.30f * alpha);
        g.pose().popPose();
        if (motion && age < 1500) {
            int cx = Math.round(guiW / 2f + (sx + 18 - 0) * fit);
            int cy = Math.round(top + (sy + 18) * fit + drop * (h * fit + top));
            ClickFx.drawSparks(g, cx, cy, Math.max(0, age - 260), skill.ordinal() + tier, Math.min(2, 1 + i / 2),
                    30f + 12f * i, accent);
        }
    }

    /** The classic look: letters on the sky, the kicker between rules, the opened lines in grey-gold. */
    private static void drawClassic(GuiGraphics g, Font font, long age, float alpha, int guiW, int guiH, String name,
            String rank, int accent) {
        int i = MilestoneFx.index(tier);
        String kicker = Component.translatable("proficiency.milestone.kicker", rank).getString();
        String title = Component.translatable("proficiency.milestone.title", name, tier).getString();
        float scale = Math.min(2.0f + 0.35f * i, (guiW * 0.9f) / Math.max(1, font.width(title)));
        int a = Math.max(8, Math.round(alpha * 255));
        int top = Math.round(guiH * 0.10f);
        g.pose().pushPose();
        g.pose().translate(guiW / 2f, top, 0f);
        int kw = font.width(kicker);
        g.drawString(font, kicker, -kw / 2, 0, (a << 24) | BannerStyle.kickerTint(accent), true);
        int rule = Math.min(48 + 10 * i, Math.max(0, guiW / 2 - kw / 2 - 12));
        g.fill(-kw / 2 - 6 - rule, 4, -kw / 2 - 6, 5, (a << 24) | BannerStyle.kickerTint(accent));
        g.fill(kw / 2 + 6, 4, kw / 2 + 6 + rule, 5, (a << 24) | BannerStyle.kickerTint(accent));
        g.pose().pushPose();
        g.pose().translate(0f, 13f, 0f);
        g.pose().scale(scale, scale, 1f);
        // The skill's icon sits left of the title, as the discovery banner's does.
        int tw = font.width(title);
        int iconUnits = ProficiencyClientConfig.uiSkillIcons() ? 13 : 0;
        float shift = iconUnits / 2f;
        g.drawString(font, title, Math.round(-tw / 2f + shift), 0, (a << 24) | accent, true);
        if (iconUnits > 0) {
            SkillIcons.draw(g, skill, Math.round(-tw / 2f + shift) - 13, -1, 11, a);
        }
        g.pose().popPose();
        int y = 13 + Math.round(9 * scale) + 5;
        for (String line : lines) {
            int lw = font.width(line);
            g.drawString(font, line, -lw / 2, y, (a << 24) | 0xE0D2A8, true);
            y += 10;
        }
        g.pose().popPose();
    }

    /** The gold full-width banner: a slow shine, the icon stamped with a gold rim, the crown of the whole mod. */
    private static void drawMastered(GuiGraphics g, Font font, long age, float alpha, int guiW, int guiH, String name,
            String rank, int accent, boolean modern, float drop, boolean motion) {
        int h = 30 + 14 + lines.size() * 10 + 8;
        h = Math.max(h, 66);
        int top = Math.round(guiH * 0.09f);
        int y0 = top + Math.round(drop * (h + top));
        int a = Math.round(alpha * 255);
        // The strip: carved stone under a wash of gold in MODERN, a smoked panel in CLASSIC.
        if (modern) {
            Stone.tile(g, Stone.OUTER, 96, 48, 0, 0, 0, y0, guiW, h);
            g.fill(0, y0, guiW, y0 + h, Stone.argb(Math.round(110 * alpha), 0x3A2A08));
            g.fill(0, y0, guiW, y0 + h, Stone.argb(Math.round(60 * alpha), GOLD));
        } else {
            g.fill(0, y0, guiW, y0 + h, Stone.argb(Math.round(190 * alpha), 0x14100A));
            g.fill(0, y0, guiW, y0 + h, Stone.argb(Math.round(50 * alpha), GOLD));
        }
        g.fill(0, y0, guiW, y0 + 3, Stone.argb(a, GOLD_DARK));
        g.fill(0, y0 + 1, guiW, y0 + 2, Stone.argb(a, GOLD));
        g.fill(0, y0 + h - 3, guiW, y0 + h, Stone.argb(a, GOLD_DARK));
        g.fill(0, y0 + h - 2, guiW, y0 + h - 1, Stone.argb(a, GOLD));
        // A glow along the foot, brighter as the stamp lands.
        Stone.lightRect(g, guiW / 4, y0 + h - 2, guiW / 2, 2, GOLD, 8, 0.5f * alpha);

        String title = rank.toUpperCase(java.util.Locale.ROOT);
        String sub = Component.translatable("proficiency.milestone.title", name, tier).getString();
        float titleScale = 2.6f;
        int tw = Math.round(font.width(title) * titleScale);
        int textW = Math.max(tw, font.width(sub));
        for (String line : lines) {
            textW = Math.max(textW, font.width(line) + 8);
        }
        int iconBox = 40;
        int groupW = iconBox + 14 + textW;
        float fit = Math.min(1f, (guiW - 12) / (float) groupW);
        int gx = Math.round(guiW / 2f - groupW * fit / 2f);
        g.pose().pushPose();
        g.pose().translate(gx, y0, 0f);
        g.pose().scale(fit, fit, 1f);

        // The stamp: the icon slams down from big to size, a gold rim round it, a ring and sparks on landing.
        int ix = 0;
        int iy = (h - iconBox) / 2;
        if (MilestoneFx.stampVisible(age)) {
            float sc = motion ? MilestoneFx.stampScale(age) : 1f;
            g.pose().pushPose();
            g.pose().translate(ix + iconBox / 2f, iy + iconBox / 2f, 0f);
            g.pose().scale(sc, sc, 1f);
            goldRim(g, -iconBox / 2, -iconBox / 2, iconBox);
            Stone.icon(g, SkillIcons.texture(skill), -14, -14, 28, alpha);
            g.pose().popPose();
        }
        float ring = MilestoneFx.stampRing(age);
        if (ring >= 0f) {
            float r = 14f + ring * 46f;
            HudMath.ring(g, ix + iconBox / 2, iy + iconBox / 2, r, r, Stone.argb(Math.round((1f - ring) * 230), GOLD_LIGHT),
                    2, 1, 64);
            Stone.light(g, ix + iconBox / 2, iy + iconBox / 2, 30, 30, GOLD, (1f - ring) * 1.1f);
            if (motion) {
                ClickFx.drawSparks(g, ix + iconBox / 2, iy + iconBox / 2, Math.round(ring * 700), skill.ordinal(), 2,
                        64f, GOLD);
            }
        }

        int tx = ix + iconBox + 14;
        g.pose().pushPose();
        g.pose().translate(tx, 9f, 0f);
        g.pose().scale(titleScale, titleScale, 1f);
        g.drawString(font, title, 0, 0, Stone.argb(a, GOLD), true);
        g.pose().popPose();
        int sy = 9 + Math.round(9 * titleScale) + 3;
        g.drawString(font, sub, tx, sy, Stone.argb(a, GOLD_LIGHT), true);
        int ly = sy + 12;
        for (String line : lines) {
            g.fill(tx, ly + 3, tx + 3, ly + 6, Stone.argb(a, GOLD));
            g.drawString(font, line, tx + 8, ly, Stone.argb(a, 0xF6EBD0), true);
            ly += 10;
        }
        g.pose().popPose();
        // The slow shine, across the whole strip, again and again.
        shine(g, MilestoneFx.shine(tier, age), 0, y0, guiW, h, 0.34f * alpha);
    }

    /** A gold frame round the stamped icon: dark, bright and light bands with four corner studs. */
    private static void goldRim(GuiGraphics g, int x, int y, int size) {
        g.fill(x, y, x + size, y + size, Stone.argb(235, 0x1C1406));
        for (int b = 0; b < 3; b++) {
            int c = b == 0 ? GOLD_DARK : b == 1 ? GOLD : GOLD_LIGHT;
            int o = b;
            g.fill(x + o, y + o, x + size - o, y + o + 1, Stone.argb(255, c));
            g.fill(x + o, y + size - o - 1, x + size - o, y + size - o, Stone.argb(255, c));
            g.fill(x + o, y + o, x + o + 1, y + size - o, Stone.argb(255, c));
            g.fill(x + size - o - 1, y + o, x + size - o, y + size - o, Stone.argb(255, c));
        }
        for (int cx : new int[] {x, x + size - 3}) {
            for (int cy : new int[] {y, y + size - 3}) {
                g.fill(cx, cy, cx + 3, cy + 3, Stone.argb(255, GOLD_LIGHT));
            }
        }
    }

    /** A slanted band of white-gold light sweeping across a rect, {@code pos} 0 to 1 (-1 for none). */
    private static void shine(GuiGraphics g, float pos, int x, int y, int w, int h, float strength) {
        if (pos < 0f || strength <= 0.01f) {
            return;
        }
        int head = x - 24 + Math.round(pos * (w + 48));
        int band = 14;
        for (int row = 0; row < h; row++) {
            int from = Math.max(x, head - row / 2);
            int to = Math.min(x + w, head - row / 2 + band);
            if (to > from) {
                g.fill(from, y + row, to, y + row + 1, Stone.argb(Math.round(255 * strength), 0xFFF4D0));
            }
        }
    }
}
