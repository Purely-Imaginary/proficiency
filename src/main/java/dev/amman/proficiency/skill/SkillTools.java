package dev.amman.proficiency.skill;

import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Maps items and blocks onto skills. Tags come first so that Better MC's own weapons and tools are
 * classified correctly without knowing anything about them; the {@code instanceof} checks are the
 * fallback for anything untagged.
 */
public final class SkillTools {

    private SkillTools() {
    }

    /** The skill a melee swing with this stack trains, or null if the stack is not a weapon. */
    @Nullable
    public static Skill meleeSkill(ItemStack stack) {
        if (stack.isEmpty()) {
            return Skill.UNARMED;
        }
        if (stack.is(ItemTags.SWORDS) || stack.getItem() instanceof SwordItem) {
            return Skill.SWORDS;
        }
        if (stack.is(ItemTags.AXES) || stack.getItem() instanceof AxeItem) {
            return Skill.AXES;
        }
        if (isMace(stack)) {
            return Skill.MACES;
        }
        if (stack.getItem() instanceof TridentItem) {
            return Skill.TRIDENTS;
        }
        return null;
    }

    /**
     * Whether breaking this crop is a harvest. Stems never are; a CropBlock (modded subclasses
     * included) asks its own isMaxAge; any other block with an "age" property is ripe at its top
     * value (nether wart 3, cocoa 2, berries); blocks with no age are always ripe.
     */
    public static boolean isRipe(BlockState state) {
        if (state.getBlock() instanceof net.minecraft.world.level.block.StemBlock) {
            return false;
        }
        if (state.getBlock() instanceof CropBlock crop) {
            return crop.isMaxAge(state);
        }
        for (net.minecraft.world.level.block.state.properties.Property<?> property : state.getProperties()) {
            if (property instanceof net.minecraft.world.level.block.state.properties.IntegerProperty age
                    && age.getName().equals("age")) {
                return state.getValue(age) >= age.getPossibleValues().stream()
                        .mapToInt(Integer::intValue).max().orElse(0);
            }
        }
        return true;
    }

    /** A crop that is ready to harvest: the one placed-block exception that still pays. */
    public static boolean isRipeCrop(BlockState state) {
        return (state.getBlock() instanceof CropBlock || state.is(BlockTags.CROPS)) && isRipe(state);
    }

    /** The gathering skill for breaking this block with this tool, or null if nothing applies. */
    @Nullable
    public static Skill harvestSkill(ItemStack tool, BlockState state) {
        if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)) {
            return Skill.WOODCUTTING;
        }
        if (state.getBlock() instanceof CropBlock || state.is(BlockTags.CROPS)) {
            // An unripe crop pays nothing: no XP, first-time bonus, proc, extra drop or replant.
            return isRipe(state) ? Skill.FARMING : null;
        }
        if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
            return isPickaxe(tool) ? Skill.MINING : null;
        }
        if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
            return isShovel(tool) ? Skill.EXCAVATION : null;
        }
        if (state.is(BlockTags.MINEABLE_WITH_AXE)) {
            return Skill.WOODCUTTING;
        }
        return null;
    }

    /**
     * Forge 1.20.1: there is no vanilla mace, so a mace is this mod's own {@code proficiency:mace}
     * or anything in the {@code proficiency:maces} item tag (which takes the common
     * {@code forge:tools/maces} and {@code c:maces} tags of other mods).
     */
    public static final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> MACES_TAG =
            ItemTags.create(dev.amman.proficiency.Proficiency.id("maces"));

    public static boolean isMace(ItemStack stack) {
        return stack.getItem() instanceof dev.amman.proficiency.item.MaceItem || stack.is(MACES_TAG);
    }

    public static boolean isPickaxe(ItemStack stack) {
        return stack.is(ItemTags.PICKAXES) || stack.getItem() instanceof PickaxeItem;
    }

    public static boolean isShovel(ItemStack stack) {
        return stack.is(ItemTags.SHOVELS) || stack.getItem() instanceof ShovelItem;
    }

    /**
     * XP for breaking one block. Scaled by hardness so that a cobblestone wall is not a shortcut to
     * a maxed Mining skill, with a cap so that obsidian farms are not one either.
     */
    public static double blockXp(BlockState state, float hardness) {
        if (hardness < 0) {
            return 0;
        }
        return Math.min(5.0, 0.5 + hardness * 0.30);
    }
}
