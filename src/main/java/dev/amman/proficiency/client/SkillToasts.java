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

    static final class SkillToast implements Toast {

        private static final ResourceLocation BACKGROUND =
                ResourceLocation.withDefaultNamespace("toast/advancement");
        private static final long DURATION_MS = 4000L;

        private final Skill skill;
        private int level;
        private long lastChanged;
        private boolean changed = true;

        private SkillToast(Skill skill, int level) {
            this.skill = skill;
            this.level = level;
        }

        @Override
        public Toast.Visibility render(GuiGraphics graphics, ToastComponent parent, long shownFor) {
            if (changed) {
                lastChanged = shownFor;
                changed = false;
            }

            graphics.blitSprite(BACKGROUND, 0, 0, width(), height());

            Minecraft minecraft = Minecraft.getInstance();
            graphics.drawString(minecraft.font,
                    Component.translatable("proficiency.toast.title"),
                    10, 7, SkillPalette.accent(skill.category()), false);
            graphics.drawString(minecraft.font,
                    Component.translatable("proficiency.toast.line",
                            Component.translatable(skill.translationKey()), level),
                    10, 18, 0xFFFFFFFF, false);

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
