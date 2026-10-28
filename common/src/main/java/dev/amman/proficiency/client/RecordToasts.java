package dev.amman.proficiency.client;

import dev.amman.proficiency.config.ProficiencyClientConfig;
import dev.amman.proficiency.skill.RecordMetric;
import dev.amman.proficiency.skill.Skill;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;

/**
 * "New best: 312 Mining XP/min": the toast of a personal record, in the skill toast's frame with a small gold
 * star, a chime and a burst. The server rations them (one per skill per 30 s); this only draws.
 */
public final class RecordToasts {

    private static final ResourceLocation BACKGROUND = ResourceLocation.withDefaultNamespace("toast/advancement");
    private static final long DURATION_MS = 5000L;
    private static final int GOLD = 0xF2D98A;

    /** A 7x7 star, row by row. */
    private static final String[] STAR = {
            "...#...",
            "...#...",
            "#######",
            ".#####.",
            "..###..",
            ".##.##.",
            ".#...#.",
    };

    private RecordToasts() {
    }

    public static void show(Skill skill, RecordMetric metric, float value) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !ProficiencyClientConfig.uiRecordToasts()) {
            return;
        }
        minecraft.getToasts().addToast(new RecordToast(skill, metric, value));
        chime();
    }

    /** A rising two-note chime and a soft orb, the notes of ClickFx's success without its screen effects. */
    private static void chime() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.5f, 0.8f));
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.9f, 0.6f));
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.1f, 0.4f));
    }

    /** The toast's sentence for a record, for the toast and the tests. */
    public static Component line(Skill skill, RecordMetric metric, float value) {
        String number = ClientRecords.number(value);
        Component name = Component.translatable(skill.translationKey());
        return switch (metric) {
            case RATE -> Component.translatable("proficiency.records.toast.rate", number, name);
            case CHAIN -> Component.translatable("proficiency.records.toast.chain", number, name);
            case GRANT -> Component.translatable("proficiency.records.toast.grant", number, name);
            case EXTRA -> Component.translatable("proficiency.records.toast." + ClientRecords.extraKey(skill), number,
                    name);
        };
    }

    private static final class RecordToast implements Toast {

        private final Skill skill;
        private final RecordMetric metric;
        private final Component title = Component.translatable("proficiency.records.toast.title");
        private final Component line;

        private RecordToast(Skill skill, RecordMetric metric, float value) {
            this.skill = skill;
            this.metric = metric;
            this.line = metric == RecordMetric.EXTRA && ClientRecords.extraKey(skill) == null
                    ? Component.empty() : line(skill, metric, value);
        }

        @Override
        public int width() {
            Minecraft minecraft = Minecraft.getInstance();
            int text = Math.max(minecraft.font.width(title), minecraft.font.width(line));
            int allowed = Math.max(160, minecraft.getWindow().getGuiScaledWidth() - 8);
            return Math.min(Math.max(160, text + 38), allowed);
        }

        @Override
        public Toast.Visibility render(GuiGraphics graphics, ToastComponent parent, long shownFor) {
            int w = width();
            graphics.blitSprite(BACKGROUND, 0, 0, w, height());
            Minecraft minecraft = Minecraft.getInstance();
            int room = w - 36;
            graphics.drawString(minecraft.font, TextFit.clip(minecraft.font, title.getString(), room), 26, 7,
                    SkillPalette.accent(skill.category()), false);
            graphics.drawString(minecraft.font, TextFit.clip(minecraft.font, line.getString(), room), 26, 18,
                    0xFF000000 | GOLD, false);
            star(graphics, 10, 12, shownFor);
            ClickFx.toastBurst(graphics, w, height(), shownFor, skill.ordinal() * 7 + metric.ordinal(), 2, GOLD);
            return shownFor >= DURATION_MS ? Visibility.HIDE : Visibility.SHOW;
        }

        /** The star pops in over 280 ms, then glints once. */
        private static void star(GuiGraphics graphics, int x, int y, long shownFor) {
            float pop = Math.min(1f, shownFor / 280f);
            int shown = Math.round(pop * STAR.length);
            int shade = shownFor > 280 && shownFor < 520 ? 0xFFFFFFFF : 0xFFFFD24A;
            for (int row = 0; row < STAR.length; row++) {
                if (row >= shown) {
                    break;
                }
                for (int col = 0; col < STAR[row].length(); col++) {
                    if (STAR[row].charAt(col) == '#') {
                        graphics.fill(x + col, y + row, x + col + 1, y + row + 1, shade);
                    }
                }
            }
        }

        @Override
        public Object getToken() {
            return "record:" + skill.id();
        }
    }
}
