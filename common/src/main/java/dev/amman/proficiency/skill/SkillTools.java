package dev.amman.proficiency.skill;

import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import dev.amman.proficiency.xp.XpDomain;
import dev.amman.proficiency.xp.XpMatch;
import dev.amman.proficiency.xp.XpSources;
import dev.amman.proficiency.xp.XpSubjects;
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
        if (stack.getItem() instanceof MaceItem) {
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
        XpMatch match = breakMatch(state, 0.0);
        return match != null && match.has("crop") && isRipe(state);
    }

    /** The rule that decides what breaking this block pays, or null when none applies. */
    @Nullable
    public static XpMatch breakMatch(BlockState state, double hardness) {
        return XpSources.table().match(XpDomain.BREAK, XpSubjects.block(state, hardness));
    }

    /**
     * The gathering skill for breaking this block with this tool, or null if nothing applies. The
     * rules are data (xp_sources, "break"): the first that fits wins, and its tool, if it names
     * one, must be in hand. An unripe crop pays nothing.
     */
    @Nullable
    public static Skill harvestSkill(ItemStack tool, BlockState state) {
        XpMatch match = breakMatch(state, 0.0);
        if (match == null || match.paysNothing()) {
            return null;
        }
        if (match.has("crop") && !isRipe(state)) {
            return null;
        }
        String needed = match.rule().tool;
        if (needed != null && !holdsTool(tool, needed)) {
            return null;
        }
        return match.rule().skill;
    }

    /** XP for breaking one block with the rule's own numbers (hardness scales most of them). */
    public static double breakXp(BlockState state, float hardness) {
        XpMatch match = breakMatch(state, hardness);
        return match == null ? 0.0 : match.xp();
    }

    private static boolean holdsTool(ItemStack tool, String kind) {
        return switch (kind) {
            case "pickaxe" -> isPickaxe(tool);
            case "shovel" -> isShovel(tool);
            case "axe" -> tool.is(ItemTags.AXES) || tool.getItem() instanceof AxeItem;
            case "hoe" -> tool.is(ItemTags.HOES) || tool.getItem() instanceof HoeItem;
            default -> true;
        };
    }

    public static boolean isPickaxe(ItemStack stack) {
        return stack.is(ItemTags.PICKAXES) || stack.getItem() instanceof PickaxeItem;
    }

    public static boolean isShovel(ItemStack stack) {
        return stack.is(ItemTags.SHOVELS) || stack.getItem() instanceof ShovelItem;
    }
}
