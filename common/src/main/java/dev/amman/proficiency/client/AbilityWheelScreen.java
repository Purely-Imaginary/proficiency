package dev.amman.proficiency.client;

import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.skill.ActiveService;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillPassives;
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
    /** Homebuilder's slots after the abilities (the magnet switch, Homeward); see {@link HomebuilderHud}. */
    private final List<Integer> extras;
    @Nullable
    private final Skill inHands;
    private final ClickFx click = new ClickFx();
    private long closeAt;
    private int selected = -1;
    private boolean done;

    private AbilityWheelScreen(List<Skill> entries, List<Integer> extras, @Nullable Skill inHands) {
        super(Component.translatable("proficiency.wheel.title"));
        this.entries = entries;
        this.extras = extras;
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
            if (ActiveService.unlocked(skills, skill)) {
                unlocked.add(skill);
            }
        }
        List<Integer> extras = HomebuilderHud.wheelSlots(player);
        return unlocked.isEmpty() && extras.isEmpty() ? null
                : new AbilityWheelScreen(List.copyOf(unlocked), List.copyOf(extras), AbilityContext.current(player));
    }

    /** Every slot round the ring: the abilities, then Homebuilder's. */
    private int count() {
        return entries.size() + extras.size();
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
        int wanted = (int) Math.ceil(count() * (SLOT + 6) / (2 * Math.PI));
        int most = Math.min(this.width, this.height) / 2 - RING_HALF_WIDTH - 6;
        // The floor keeps the middle wide enough for a two-word ability name on one line, but the
        // screen has the last word: a ring that leaves the window is no use at any size.
        return Math.min(most, Math.max(MIN_RADIUS, wanted));
    }

    int slotCount() {
        return count();
    }

    /** Screen position of slot i's centre; the layout demo hovers it. */
    int[] slotPoint(int index) {
        double angle = -Math.PI / 2 + index * 2 * Math.PI / count();
        return new int[] {this.width / 2 + (int) Math.round(radius() * Math.cos(angle)),
                this.height / 2 + (int) Math.round(radius() * Math.sin(angle))};
    }

    /** The slot's side: 22 when they all fit round the ring, smaller when the ring is crowded. */
    private int slotSize() {
        int fit = (int) Math.floor(2 * Math.PI * radius() / count()) - 2;
        return Math.max(12, Math.min(SLOT, fit));
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
        double step = 2 * Math.PI / count();
        return (int) Math.round(angle / step) % count();
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
        if (!done && pick != selected) {
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

        double step = 2 * Math.PI / count();
        boolean icons = SkillIcons.enabled();
        long clock = System.currentTimeMillis();
        for (int i = 0; i < count(); i++) {
            double angle = -Math.PI / 2 + i * step;
            int x = cx + (int) Math.round(radius * Math.cos(angle));
            int y = cy + (int) Math.round(radius * Math.sin(angle));
            int half = slotSize() / 2;
            click.begin(graphics, i, x - half, y - half, half * 2, half * 2, clock);
            drawSlot(graphics, i, x, y, icons, player, skills, now);
            click.end(graphics, i, x - half, y - half, half * 2, half * 2, clock);
        }
        click.render(graphics, clock, 1f);
        if (closeAt > 0 && clock >= closeAt) {
            closeAt = 0;
            onClose();
            return;
        }

        centre(graphics, cx, cy, radius - RING_HALF_WIDTH - 10, skills, now);
    }

    private void drawSlot(GuiGraphics graphics, int i, int x, int y, boolean icons, Player player,
            PlayerSkills skills, long now) {
        if (i >= entries.size()) {
            extraSlot(graphics, extras.get(i - entries.size()), x, y, i == selected, player);
            return;
        }
        Skill skill = entries.get(i);
        int accent = SkillPalette.accent(skill.category());
        long cooldown = skills.cooldownRemaining(skill, now);
        int half = slotSize() / 2;
        if (i == selected) {
            graphics.fill(x - half - 2, y - half - 2, x + half + 2, y + half + 2, accent);
            graphics.fill(x - half, y - half, x + half, y + half, 0xF0121418);
        } else {
            graphics.fill(x - half, y - half, x + half, y + half, 0xA023282F);
        }
        if (icons) {
            // The skill's own icon: an item stood in for most skills only loosely (a cake for
            // Social, a golden apple for Guardian), the drawn one names the skill.
            int iconSize = Math.min(SkillIcons.LARGE, half * 2 - 2);
            SkillIcons.draw(graphics, skill, x - iconSize / 2, y - iconSize / 2, iconSize);
        } else {
            graphics.renderItem(icon(skill), x - 8, y - 8);
        }
        if (cooldown > 0) {
            // A cooling ability is greyed and fills back up from the bottom as it recovers.
            double left = cooldown / (double) Math.max(1L, ActiveService.cooldownTicks(skills, skill));
            int shade = (int) Math.round(half * 2 * Math.min(1.0, left));
            graphics.fill(x - half, y + half - shade, x + half, y + half, 0xA0000000);
        }
        if (skill == inHands) {
            graphics.fill(x + half - 4, y - half + 1, x + half - 1, y - half + 4, SkillPalette.MAXED);
        }
    }

    /** What the selected slot would cast, in the middle of the ring; the hint when none is. */
    private void centre(GuiGraphics graphics, int cx, int cy, int room, PlayerSkills skills, long now) {
        List<String> lines = new ArrayList<>();
        List<Integer> colours = new ArrayList<>();
        int width = Math.max(60, room * 2 - 8);
        if (selected >= entries.size()) {
            extraCentre(lines, colours, extras.get(selected - entries.size()), skills, width);
        } else if (selected < 0) {
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
            // House Proud is not "the proc every time": it doubles the passive and shares it.
            add(lines, colours, skill == Skill.HOMEBUILDER
                    ? Component.translatable("proficiency.wheel.effect.homebuilder", seconds)
                    : Component.translatable("proficiency.wheel.effect",
                            Component.translatable(skill.procKey()), seconds), SkillPalette.TEXT, width);
            long cooldown = skills.cooldownRemaining(skill, now);
            if (cooldown > 0) {
                long total = cooldown / 20;
                add(lines, colours, Component.translatable("proficiency.wheel.cooldown",
                        String.format(Locale.ROOT, "%d:%02d", total / 60, total % 60)), 0xFFE08F8F, width);
            } else {
                add(lines, colours, Component.translatable("proficiency.wheel.ready"), 0xFF8FD18F, width);
            }
            if (TooltipDetail.detailed()) {
                add(lines, colours, Component.translatable("proficiency.stats.ability",
                        SkillNumbers.seconds(ActiveService.durationTicks(skills, skill)),
                        SkillNumbers.seconds(ActiveService.cooldownTicks(skills, skill))),
                        SkillPalette.TEXT_DIM, width);
                if (skill == inHands) {
                    add(lines, colours, Component.translatable("proficiency.wheel.in_hands"),
                            SkillPalette.TEXT_DIM, width);
                }
            } else {
                add(lines, colours, TooltipDetail.hint(), 0xFF5C636D, width);
            }
        }
        // The middle is a disc: a line near its top or bottom has less room than one at the
        // centre, so each line is cut to the chord at its own height.
        int disc = Math.max(20, room + 10 - 2);
        int y = cy - lines.size() * 10 / 2;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int fromCentre = Math.abs(y + 4 - cy) + 5;
            int chord = (int) (2 * Math.sqrt(Math.max(0, disc * disc - fromCentre * fromCentre)));
            String shown = TextFit.clip(this.font, line, Math.max(20, chord));
            if (!shown.equals(line)) {
                TextFit.note("wheel.centre");
            }
            graphics.drawString(this.font, shown, cx - this.font.width(shown) / 2, y, colours.get(i), false);
            y += 10;
        }
    }

    private void add(List<String> lines, List<Integer> colours, Component text, int colour, int width) {
        for (String line : TextFit.wrapPlain(this.font, text.getString(), width)) {
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
        if (selected >= entries.size()) {
            int extra = extras.get(selected - entries.size());
            onClose();
            dev.amman.proficiency.platform.net.ClientPacketDistributor.sendToServer(
                    new dev.amman.proficiency.homebuilder.net.HomebuilderActionPayload(
                            extra == HomebuilderHud.SLOT_MAGNET
                                    ? dev.amman.proficiency.homebuilder.net.HomebuilderActionPayload.MAGNET
                                    : dev.amman.proficiency.homebuilder.net.HomebuilderActionPayload.HOMEWARD));
            return;
        }
        Skill skill = selected >= 0 ? entries.get(selected) : null;
        if (skill != null && ClickFx.on() && this.minecraft != null && this.minecraft.player != null) {
            // Let the slot answer before the wheel goes: a wiggle and a thunk when it is cooling, a pop when it fires.
            long clock = System.currentTimeMillis();
            int half = slotSize() / 2;
            int[] at = slotPoint(selected);
            boolean cooling = ProficiencyAttachments.of(this.minecraft.player)
                    .cooldownRemaining(skill, this.minecraft.player.level().getGameTime()) > 0;
            if (cooling) {
                click.no(selected, at[0] - half, at[1] - half, half * 2, half * 2, clock);
            } else {
                click.success(selected, at[0] - half, at[1] - half, half * 2, half * 2,
                        SkillPalette.accent(skill.category()), 0, 0.6f, clock);
            }
            if (cooling) {
                // Nothing fires, so there is no input to hold up: let the slot answer "no" first.
                closeAt = clock + 300L;
            } else {
                // The cast must not wait on a quarter second of blocked input: the wheel goes at once.
                onClose();
            }
            ClientInputEvents.cast(skill);
            return;
        }
        onClose();
        if (skill != null) {
            ClientInputEvents.cast(skill);
        }
    }

    /** One of Homebuilder's slots: its icon on the ring, dimmed while the magnet is off or Homeward is spent. */
    private void extraSlot(GuiGraphics graphics, int extra, int x, int y, boolean picked, Player player) {
        int half = slotSize() / 2;
        if (picked) {
            graphics.fill(x - half - 2, y - half - 2, x + half + 2, y + half + 2, HomebuilderHud.GOLD);
            graphics.fill(x - half, y - half, x + half, y + half, 0xF0121418);
        } else {
            graphics.fill(x - half, y - half, x + half, y + half, 0xA023282F);
        }
        boolean off = extra == HomebuilderHud.SLOT_MAGNET ? HomebuilderHud.magnetOff()
                : !HomebuilderHud.homewardReady(player);
        int iconSize = Math.min(SkillIcons.LARGE, half * 2 - 2);
        HomebuilderHud.drawSlotIcon(graphics, extra, x - iconSize / 2, y - iconSize / 2, iconSize, off);
        if (extra == HomebuilderHud.SLOT_HOMEWARD && off) {
            // Spent for today: greyed like a cooling ability.
            graphics.fill(x - half, y - half, x + half, y + half, 0x70000000);
        }
    }

    /** The middle of the ring for a Homebuilder slot: what it is, its state, and what a click does. */
    private void extraCentre(List<String> lines, List<Integer> colours, int extra, PlayerSkills skills, int width) {
        Player player = this.minecraft == null ? null : this.minecraft.player;
        if (extra == HomebuilderHud.SLOT_MAGNET) {
            boolean off = HomebuilderHud.magnetOff();
            add(lines, colours, Component.translatable("proficiency.wheel.magnet"), HomebuilderHud.GOLD, width);
            add(lines, colours, Component.translatable(off ? "proficiency.wheel.magnet.off"
                    : "proficiency.wheel.magnet.on"), off ? 0xFFE08F8F : 0xFF8FD18F, width);
            add(lines, colours, Component.translatable(off ? "proficiency.wheel.magnet.turn_on"
                    : "proficiency.wheel.magnet.turn_off"), SkillPalette.TEXT, width);
            if (TooltipDetail.detailed()) {
                add(lines, colours, Component.translatable("proficiency.wheel.magnet.detail",
                        SkillNumbers.value(SkillPassives.Format.NUMBER, SkillPassives.homeMagnet(skills))), SkillPalette.TEXT_DIM, width);
            } else {
                add(lines, colours, TooltipDetail.hint(), 0xFF5C636D, width);
            }
            return;
        }
        add(lines, colours, Component.translatable("proficiency.talent.homebuilder.homeward"), HomebuilderHud.GOLD, width);
        if (HomebuilderHud.channelling()) {
            add(lines, colours, Component.translatable("proficiency.wheel.homeward.running"), SkillPalette.TEXT, width);
        } else if (player != null && !HomebuilderHud.homewardReady(player)) {
            add(lines, colours, Component.translatable("proficiency.wheel.homeward.used"), 0xFFE08F8F, width);
        } else {
            add(lines, colours, Component.translatable("proficiency.wheel.ready"), 0xFF8FD18F, width);
        }
        if (TooltipDetail.detailed()) {
            add(lines, colours, Component.translatable("proficiency.wheel.homeward.detail"), SkillPalette.TEXT_DIM, width);
        } else {
            add(lines, colours, TooltipDetail.hint(), 0xFF5C636D, width);
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
            case HOMEBUILDER -> Items.CAMPFIRE;
            case HUSBANDRY -> Items.LEAD;
            case RIDING -> Items.SADDLE;
            case COMMERCE -> Items.EMERALD;
        };
        return new ItemStack(item);
    }
}
