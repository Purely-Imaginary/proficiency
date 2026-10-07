package dev.amman.proficiency.client;

import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.skill.ActiveService;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Hold the ability key and this wheel opens: every ability the player has unlocked, around a ring,
 * always in skill order so a slot stays where the hand learned it. Point the mouse at one and
 * release the key to cast it; release in the middle to cancel. A tap of the key still casts the
 * ability that matches what is held ({@link AbilityContext}), so the wheel is only for the rest:
 * the skills with no item to hold, or a second ability without swapping items.
 *
 * <p>It does not pause and has no blur, so the fight stays visible behind it. Nothing here talks
 * to the server except through {@link ClientInputEvents#cast}, which keeps this file free of
 * loader classes.
 */
public final class AbilityWheelScreen extends Screen {

    /** Mouse this close to the centre picks nothing, so releasing there cancels. */
    private static final int DEAD_ZONE = 22;
    private static final int SLOT = 22;
    private static final int RING_HALF_WIDTH = 17;
    private static final int MIN_RADIUS = 92;

    private final List<Skill> entries;
    @Nullable
    private final Skill inHands;
    private int selected = -1;
    private boolean done;

    private AbilityWheelScreen(List<Skill> entries, @Nullable Skill inHands) {
        super(Component.translatable("proficiency.wheel.title"));
        this.entries = entries;
        this.inHands = inHands;
    }

    /**
     * The wheel for this player, or null when nothing is unlocked yet (the caller says so on the
     * action bar instead of opening an empty ring).
     */
    @Nullable
    public static AbilityWheelScreen forPlayer(Player player) {
        PlayerSkills skills = ProficiencyAttachments.of(player);
        List<Skill> unlocked = new ArrayList<>();
        for (Skill skill : Skill.VALUES) {
            if (skills.level(skill) >= ActiveService.unlockLevel()) {
                unlocked.add(skill);
            }
        }
        return unlocked.isEmpty() ? null
                : new AbilityWheelScreen(List.copyOf(unlocked), AbilityContext.current(player));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0x50000000);
    }

    private int radius() {
        // Room for every slot around the ring, but never off the screen.
        int wanted = (int) Math.ceil(entries.size() * (SLOT + 6) / (2 * Math.PI));
        int most = Math.min(this.width, this.height) / 2 - RING_HALF_WIDTH - 6;
        // The floor keeps the middle wide enough for a two-word ability name on one line.
        return Math.max(MIN_RADIUS, Math.min(wanted, most));
    }

    private int pick(int mouseX, int mouseY) {
        double dx = mouseX - this.width / 2.0;
        double dy = mouseY - this.height / 2.0;
        if (dx * dx + dy * dy < DEAD_ZONE * DEAD_ZONE) {
            return -1;
        }
        // Slot 0 is straight up and the ring runs clockwise, like a clock face.
        double angle = Math.atan2(dy, dx) + Math.PI / 2;
        if (angle < 0) {
            angle += 2 * Math.PI;
        }
        double step = 2 * Math.PI / entries.size();
        return (int) Math.round(angle / step) % entries.size();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        Player player = this.minecraft == null ? null : this.minecraft.player;
        if (player == null) {
            return;
        }
        PlayerSkills skills = ProficiencyAttachments.of(player);
        long now = player.level().getGameTime();

        int pick = pick(mouseX, mouseY);
        if (pick != selected) {
            selected = pick;
            if (pick >= 0 && this.minecraft != null) {
                this.minecraft.getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.8f, 0.15f));
            }
        }

        int cx = this.width / 2;
        int cy = this.height / 2;
        int radius = radius();
        // One solid wheel: the ring's band, then the middle a shade darker so the text reads.
        disc(graphics, cx, cy, radius + RING_HALF_WIDTH, 0xB0121418);
        disc(graphics, cx, cy, radius - RING_HALF_WIDTH, 0x60000000);

        double step = 2 * Math.PI / entries.size();
        for (int i = 0; i < entries.size(); i++) {
            Skill skill = entries.get(i);
            double angle = -Math.PI / 2 + i * step;
            int x = cx + (int) Math.round(radius * Math.cos(angle));
            int y = cy + (int) Math.round(radius * Math.sin(angle));
            int accent = SkillPalette.accent(skill.category());
            long cooldown = skills.cooldownRemaining(skill, now);
            int half = SLOT / 2;
            if (i == selected) {
                graphics.fill(x - half - 2, y - half - 2, x + half + 2, y + half + 2, accent);
                graphics.fill(x - half, y - half, x + half, y + half, 0xF0121418);
            } else {
                graphics.fill(x - half, y - half, x + half, y + half, 0xA023282F);
            }
            graphics.renderItem(icon(skill), x - 8, y - 8);
            if (cooldown > 0) {
                // A cooling ability is greyed and fills back up from the bottom as it recovers.
                double left = cooldown / (double) Math.max(1L, ActiveService.cooldownTicks(skills, skill));
                int shade = (int) Math.round(SLOT * Math.min(1.0, left));
                graphics.fill(x - half, y + half - shade, x + half, y + half, 0xA0000000);
            }
            if (skill == inHands) {
                graphics.fill(x + half - 4, y - half + 1, x + half - 1, y - half + 4, SkillPalette.MAXED);
            }
        }

        centre(graphics, cx, cy, radius - RING_HALF_WIDTH - 10, skills, now);
    }

    /** What the selected slot would cast, in the middle of the ring; the hint when none is. */
    private void centre(GuiGraphics graphics, int cx, int cy, int room, PlayerSkills skills, long now) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        List<Integer> colours = new ArrayList<>();
        int width = Math.max(60, room * 2 - 8);
        if (selected < 0) {
            add(lines, colours, this.title, 0xFFB98BE0, width);
            add(lines, colours, Component.translatable("proficiency.wheel.hint",
                    ProficiencyClient.USE_ABILITY.getTranslatedKeyMessage()), SkillPalette.TEXT_DIM, width);
            add(lines, colours, Component.translatable("proficiency.wheel.cancel"), 0xFF5C636D, width);
        } else {
            Skill skill = entries.get(selected);
            add(lines, colours, Component.translatable(skill.activeKey()), SkillPalette.MAXED, width);
            add(lines, colours, Component.translatable(skill.translationKey()),
                    SkillPalette.accent(skill.category()), width);
            int seconds = (int) Math.round(ActiveService.durationTicks(skills, skill) / 20.0);
            add(lines, colours, Component.translatable("proficiency.wheel.effect",
                    Component.translatable(skill.procKey()), seconds), SkillPalette.TEXT, width);
            long cooldown = skills.cooldownRemaining(skill, now);
            if (cooldown > 0) {
                long total = cooldown / 20;
                add(lines, colours, Component.translatable("proficiency.wheel.cooldown",
                        String.format(Locale.ROOT, "%d:%02d", total / 60, total % 60)), 0xFFE08F8F, width);
            } else {
                add(lines, colours, Component.translatable("proficiency.wheel.ready"), 0xFF8FD18F, width);
            }
            if (skill == inHands) {
                add(lines, colours, Component.translatable("proficiency.wheel.in_hands"),
                        SkillPalette.TEXT_DIM, width);
            }
        }
        int y = cy - lines.size() * 10 / 2;
        for (int i = 0; i < lines.size(); i++) {
            FormattedCharSequence line = lines.get(i);
            graphics.drawString(this.font, line, cx - this.font.width(line) / 2, y, colours.get(i), false);
            y += 10;
        }
    }

    private void add(List<FormattedCharSequence> lines, List<Integer> colours, Component text, int colour,
            int width) {
        for (FormattedCharSequence line : this.font.split(text, width)) {
            lines.add(line);
            colours.add(colour);
        }
    }

    /** A filled disc, one row of fill per pixel row. GuiGraphics has no circle. */
    private static void disc(GuiGraphics graphics, int cx, int cy, int r, int colour) {
        for (int dy = -r; dy <= r; dy++) {
            int half = (int) Math.round(Math.sqrt((double) r * r - (double) dy * dy));
            graphics.fill(cx - half, cy + dy, cx + half, cy + dy + 1, colour);
        }
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (ProficiencyClient.USE_ABILITY.matches(keyCode, scanCode)) {
            fire();
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (ProficiencyClient.USE_ABILITY.matchesMouse(button)) {
            fire();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /** A click casts too, for anyone who lets go of the key before aiming. */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            selected = pick((int) mouseX, (int) mouseY);
            fire();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void fire() {
        if (done) {
            return;
        }
        done = true;
        Skill skill = selected >= 0 ? entries.get(selected) : null;
        onClose();
        if (skill != null) {
            ClientInputEvents.cast(skill);
        }
    }

    /** The item that stands for each skill on the wheel: its tool, or what AbilityContext reads. */
    static ItemStack icon(Skill skill) {
        Item item = switch (skill) {
            case SWORDS -> Items.IRON_SWORD;
            case AXES -> Items.IRON_AXE;
            case MACES -> Items.MACE;
            case TRIDENTS -> Items.TRIDENT;
            case UNARMED -> Items.LEATHER;
            case BLOCKING -> Items.SHIELD;
            case ENDURANCE -> Items.IRON_CHESTPLATE;
            case ARCHERY -> Items.BOW;
            case CROSSBOWS -> Items.CROSSBOW;
            case MINING -> Items.IRON_PICKAXE;
            case WOODCUTTING -> Items.OAK_LOG;
            case EXCAVATION -> Items.IRON_SHOVEL;
            case FARMING -> Items.WHEAT;
            case FISHING -> Items.FISHING_ROD;
            case RUNNING -> Items.SUGAR;
            case SNEAKING -> Items.LEATHER_BOOTS;
            case JUMPING -> Items.RABBIT_FOOT;
            case SWIMMING -> Items.TURTLE_HELMET;
            case SMITHING -> Items.ANVIL;
            case COOKING -> Items.COOKED_BEEF;
            case ALCHEMY -> Items.BREWING_STAND;
            case SPELLCASTING -> Items.ENCHANTED_BOOK;
            case ENGINEERING -> Items.REDSTONE;
            case BEASTSLAYING -> Items.ZOMBIE_HEAD;
            case WAYFARING -> Items.COMPASS;
            case SPELUNKING -> Items.TORCH;
            case MASONRY -> Items.STONE_BRICKS;
            case DECORATING -> Items.FLOWER_POT;
            case SOCIAL -> Items.CAKE;
            case NIGHTWALKER -> Items.CLOCK;
            case COURAGE -> Items.GOAT_HORN;
            case GUARDIAN -> Items.GOLDEN_APPLE;
            case CHARGER -> Items.WHITE_BANNER;
            case TACTICIAN -> Items.SPYGLASS;
        };
        return new ItemStack(item);
    }
}
