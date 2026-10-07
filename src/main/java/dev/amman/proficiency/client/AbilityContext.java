package dev.amman.proficiency.client;

import dev.amman.proficiency.skill.BuildClassifier;
import dev.amman.proficiency.skill.Skill;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import org.jetbrains.annotations.Nullable;

/**
 * Which ability one key means.
 *
 * <p>Thirty-four abilities cannot each have a keybind and a radial menu is a lot of interface for
 * something you press mid-swing. What you are holding already says what you are doing, so the key
 * reads that instead of asking.
 */
public final class AbilityContext {

    private AbilityContext() {
    }

    @Nullable
    public static Skill current(Player player) {
        ItemStack main = player.getMainHandItem();
        Item item = main.getItem();

        if (main.is(ItemTags.PICKAXES) || item instanceof PickaxeItem) {
            return Skill.MINING;
        }
        if (main.is(ItemTags.AXES) || item instanceof AxeItem) {
            return Skill.WOODCUTTING;
        }
        if (main.is(ItemTags.SHOVELS) || item instanceof ShovelItem) {
            return Skill.EXCAVATION;
        }
        if (main.is(ItemTags.HOES) || item instanceof HoeItem) {
            return Skill.FARMING;
        }
        if (main.is(ItemTags.SWORDS) || item instanceof SwordItem) {
            return Skill.SWORDS;
        }
        if (item instanceof MaceItem) {
            return Skill.MACES;
        }
        if (item instanceof TridentItem) {
            return Skill.TRIDENTS;
        }
        if (item instanceof BowItem) {
            return Skill.ARCHERY;
        }
        if (item instanceof CrossbowItem) {
            return Skill.CROSSBOWS;
        }
        if (item instanceof FishingRodItem) {
            return Skill.FISHING;
        }
        if (item instanceof ShieldItem) {
            return Skill.BLOCKING;
        }
        // Social has no tool; the Friend Compass is the closest thing to one.
        if (item instanceof dev.amman.proficiency.item.FriendCompassItem) {
            return Skill.SOCIAL;
        }
        // Nightwalker has no tool either; a clock is what you hold to know it is night.
        if (item == net.minecraft.world.item.Items.CLOCK) {
            return Skill.NIGHTWALKER;
        }
        // Courage has no weapon of its own (any weapon means that weapon's skill); a goat horn is
        // the war horn you blow before you stand your ground.
        if (item instanceof net.minecraft.world.item.InstrumentItem) {
            return Skill.COURAGE;
        }
        // Guardian has no weapon either; a golden apple is what you hold out to a friend in trouble.
        if (item == net.minecraft.world.item.Items.GOLDEN_APPLE
                || item == net.minecraft.world.item.Items.ENCHANTED_GOLDEN_APPLE) {
            return Skill.GUARDIAN;
        }
        // Tactician's weapon is any ranged one, and those already mean their own skills; a spyglass
        // is what you hold to read the field and call the shot.
        if (item == net.minecraft.world.item.Items.SPYGLASS) {
            return Skill.TACTICIAN;
        }
        // Charger has no weapon of its own either; a banner is what you charge behind. Checked
        // before blocks, because a banner is also a block you can place. Until Charger's ability
        // is unlocked, a banner keeps meaning the building skill it meant before idea 39.
        if (item instanceof BlockItem block) {
            Skill build = BuildClassifier.skillFor(block.getBlock().defaultBlockState());
            if (item instanceof net.minecraft.world.item.BannerItem
                    && (build == null || dev.amman.proficiency.ProficiencyAttachments.of(player)
                            .level(Skill.CHARGER) >= dev.amman.proficiency.skill.ActiveService.unlockLevel())) {
                return Skill.CHARGER;
            }
            return build;
        }

        if (main.isEmpty()) {
            if (player.isInWater()) {
                return Skill.SWIMMING;
            }
            if (player.isCrouching()) {
                return Skill.SNEAKING;
            }
            if (player.isSprinting()) {
                return Skill.RUNNING;
            }
            // Endurance has no tool, so it answers to the moment instead: bare-handed and down to
            // half health, the key means Unbreakable, which is what someone in that spot wants.
            if (player.getHealth() <= player.getMaxHealth() * 0.5f) {
                return Skill.ENDURANCE;
            }
            return Skill.UNARMED;
        }
        return null;
    }
}
