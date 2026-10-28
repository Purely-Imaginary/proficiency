package dev.amman.proficiency.client;

import dev.amman.proficiency.skill.Skill;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** "Skill increased: Axes 14", in the corner, one slot per skill so a grind does not stack up. */
public final class SkillToasts {

    private SkillToasts() {
    }

    /** A Mastery star has no toast: the HUD line plays its own small star moment. */
    public static void star(Skill skill, int stars) {
        if (Minecraft.getInstance().player != null) {
            LevelUpFx.queueStar(skill, stars);
        }
    }

    public static void show(Skill skill, int level) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        // The HUD line plays its own level-up moment for this.
        LevelUpFx.queue(skill, level);
        // Levelling the same skill twice in a row refreshes the toast that is already up.
        SkillToast existing = minecraft.getToasts().getToast(SkillToast.class, skill);
        if (existing != null) {
            existing.update(level);
            return;
        }
        minecraft.getToasts().addToast(new SkillToast(skill, level));
    }

    /**
     * A Codex title earned: the same frame as the level toast, in the skill's colour, the title's name
     * under a "Codex milestone" line. One per title, so a 100 right after a 50 does not replace it.
     */
    public static void codex(Skill skill, int kinds) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        minecraft.getToasts().addToast(new CodexToast(skill, kinds));
        minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f));
    }

    /**
     * A skill got a talent rank you can take right now (1.55.0, {@link SpendableClient}): "Mining: Pickwork ready",
     * in the level toast's frame, with an ember spark burst. One slot per skill; the chime is the caller's.
     */
    public static void ready(Skill skill, Component node) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        Component line = node.getString().isEmpty()
                ? Component.translatable("proficiency.toast.ready.line", Component.translatable(skill.translationKey()),
                        Component.translatable("proficiency.spend.ready", 1))
                : Component.translatable("proficiency.toast.ready.line", Component.translatable(skill.translationKey()), node);
        minecraft.getToasts().addToast(new ReadyToast(skill, Component.translatable("proficiency.toast.ready.title"), line,
                "ready:" + skill.id()));
    }

    /** Ranks already ready when the world loaded: one toast for them all. */
    public static void readySummary(int skills, int ranks) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        minecraft.getToasts().addToast(new ReadyToast(null,
                Component.translatable("proficiency.toast.ready.summary", skills),
                Component.translatable("proficiency.toast.ready.summary_line", ranks), "ready:summary"));
    }

    static final class ReadyToast implements Toast {

        private static final long DURATION_MS = 5000L;
        private static final int EMBER = 0xFFB347;

        private final Skill skill;
        private final Component title;
        private final Component line;
        private final String token;

        private ReadyToast(Skill skill, Component title, Component line, String token) {
            this.skill = skill;
            this.title = title;
            this.line = line;
            this.token = token;
        }

        @Override
        public int width() {
            Minecraft minecraft = Minecraft.getInstance();
            int text = Math.max(minecraft.font.width(title), minecraft.font.width(line)) + (skill == null ? 0 : 20);
            int allowed = Math.max(160, minecraft.getWindow().getGuiScaledWidth() - 8);
            return Math.min(Math.max(160, text + 20), allowed);
        }

        @Override
        public Toast.Visibility render(GuiGraphics graphics, ToastComponent parent, long shownFor) {
            int w = width();
            if (w <= 160) {
                graphics.blit(TEXTURE, 0, 0, 0, 0, w, height());
            } else {
                graphics.blit(TEXTURE, 0, 0, 0, 0, 80, height());
                graphics.blit(TEXTURE, 80, 0, w - 160, height(), 79, 0, 1, height(), 256, 256);
                graphics.blit(TEXTURE, w - 80, 0, 80, 0, 80, height());
            }
            Minecraft minecraft = Minecraft.getInstance();
            int tx = 10;
            if (skill != null) {
                SkillIcons.draw(graphics, skill, 8, 8, 16);
                tx = 30;
            }
            // The ember dot of the dial, breathing beside the title.
            float pulse = 0.5f + 0.5f * (float) Math.sin(shownFor / 300.0);
            int dx = w - 14;
            graphics.fill(dx - 1, 6, dx + 5, 12, (Math.round(90 + 80 * pulse) << 24) | 0xFF8A2A);
            graphics.fill(dx, 7, dx + 4, 11, 0xFFFFB347);
            int room = w - tx - 20;
            graphics.drawString(minecraft.font, TextFit.clip(minecraft.font, title.getString(), room), tx, 7,
                    0xFF000000 | EMBER, false);
            graphics.drawString(minecraft.font, TextFit.clip(minecraft.font, line.getString(), w - tx - 8), tx, 18,
                    0xFFFFFFFF, false);
            ClickFx.toastBurst(graphics, w, height(), shownFor, skill == null ? 7 : skill.ordinal(), 1, EMBER);
            return shownFor >= DURATION_MS ? Visibility.HIDE : Visibility.SHOW;
        }

        @Override
        public Object getToken() {
            return token;
        }
    }

    static final class CodexToast implements Toast {

        private static final long DURATION_MS = 6000L;

        private final Skill skill;
        private final int kinds;
        private final Component title;
        private final Component line;

        private CodexToast(Skill skill, int kinds) {
            this.skill = skill;
            this.kinds = kinds;
            this.title = Component.translatable("proficiency.codex.toast.title");
            this.line = Component.translatable("proficiency.codex.toast.line",
                    dev.amman.proficiency.skill.CodexService.titleName(
                            new dev.amman.proficiency.skill.Codex.Title(skill, kinds)), kinds);
        }

        @Override
        public int width() {
            Minecraft minecraft = Minecraft.getInstance();
            int text = Math.max(minecraft.font.width(title), minecraft.font.width(line));
            int allowed = Math.max(160, minecraft.getWindow().getGuiScaledWidth() - 8);
            return Math.min(Math.max(160, text + 20), allowed);
        }

        @Override
        public Toast.Visibility render(GuiGraphics graphics, ToastComponent parent, long shownFor) {
            int w = width();
            // The advancement toast's frame, as the level toast draws it in 1.20.1.
            if (w <= 160) {
                graphics.blit(TEXTURE, 0, 0, 0, 0, w, height());
            } else {
                graphics.blit(TEXTURE, 0, 0, 0, 0, 80, height());
                graphics.blit(TEXTURE, 80, 0, w - 160, height(), 79, 0, 1, height(), 256, 256);
                graphics.blit(TEXTURE, w - 80, 0, 80, 0, 80, height());
            }
            Minecraft minecraft = Minecraft.getInstance();
            int room = w - 20;
            graphics.drawString(minecraft.font, TextFit.clip(minecraft.font, title.getString(), room), 10, 7,
                    SkillPalette.accent(skill.category()), false);
            graphics.drawString(minecraft.font, TextFit.clip(minecraft.font, line.getString(), room), 10, 18,
                    0xFFF2D98A, false);
            ClickFx.toastBurst(graphics, w, height(), shownFor, skill.ordinal(), 2, 0xF2D98A);
            return shownFor >= DURATION_MS ? Visibility.HIDE : Visibility.SHOW;
        }

        @Override
        public Object getToken() {
            return skill.id() + ":" + kinds;
        }
    }

    static final class SkillToast implements Toast {

        private static final long DURATION_MS = 4000L;

        private final Skill skill;
        private int level;
        private long lastChanged;
        private boolean changed = true;

        private SkillToast(Skill skill, int level) {
            this.skill = skill;
            this.level = level;
        }

        /** The frame is 160 wide; a longer translation widens it (up to the screen) rather than spilling out. */
        @Override
        public int width() {
            Minecraft minecraft = Minecraft.getInstance();
            int text = Math.max(minecraft.font.width(Component.translatable("proficiency.toast.title")),
                    minecraft.font.width(lineText()));
            int wanted = Math.max(160, text + 20);
            int allowed = Math.max(160, minecraft.getWindow().getGuiScaledWidth() - 8);
            return Math.min(wanted, allowed);
        }

        private Component lineText() {
            return Component.translatable("proficiency.toast.line",
                    Component.translatable(skill.translationKey()), level);
        }

        @Override
        public Toast.Visibility render(GuiGraphics graphics, ToastComponent parent, long shownFor) {
            if (changed) {
                lastChanged = shownFor;
                changed = false;
            }

            // The advancement toast's frame: in 1.20.1 the top strip of toasts.png, not a sprite.
            // Wider than 160, the left and right halves stay and the middle column is stretched.
            int w = width();
            if (w <= 160) {
                graphics.blit(TEXTURE, 0, 0, 0, 0, w, height());
            } else {
                graphics.blit(TEXTURE, 0, 0, 0, 0, 80, height());
                graphics.blit(TEXTURE, 80, 0, w - 160, height(), 79, 0, 1, height(), 256, 256);
                graphics.blit(TEXTURE, w - 80, 0, 80, 0, 80, height());
            }

            Minecraft minecraft = Minecraft.getInstance();
            int room = w - 20;
            String title = TextFit.clip(minecraft.font, Component.translatable("proficiency.toast.title").getString(), room);
            String line = TextFit.clip(minecraft.font, lineText().getString(), room);
            graphics.drawString(minecraft.font, title, 10, 7, SkillPalette.accent(skill.category()), false);
            graphics.drawString(minecraft.font, line, 10, 18, 0xFFFFFFFF, false);
            ClickFx.toastBurst(graphics, w, height(), shownFor - lastChanged, skill.ordinal() + level,
                    level % 25 == 0 ? 2 : 1, SkillPalette.accent(skill.category()));

            return shownFor - lastChanged >= DURATION_MS ? Visibility.HIDE : Visibility.SHOW;
        }

        /** One slot per skill: levelling Mining ten times in a row updates in place. */
        @Override
        public Object getToken() {
            return skill;
        }

        private void update(int newLevel) {
            this.level = newLevel;
            this.changed = true;
        }
    }
}
