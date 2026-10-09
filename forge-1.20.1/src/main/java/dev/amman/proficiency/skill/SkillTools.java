package dev.amman.proficiency.skill;

import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
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
        if (isMace(stack)) {
            return Skill.MACES;
        }
        if (stack.getItem() instanceof TridentItem) {
            return Skill.TRIDENTS;
        }
        return null;
    }

    /**
     * A machine's stand-in player (Create deployers, Mekanism miners, Ars turrets, rituals): it earns
     * nothing and gets nothing. Also true for a player with no connection, which could never be sent
     * a packet. See {@link FakeActors}.
     */
    public static boolean isFakePlayer(net.minecraft.world.entity.player.Player player) {
        return FakeActors.isFake(player)
                || (player instanceof net.minecraft.server.level.ServerPlayer server && server.connection == null);
    }

    /**
     * Whether breaking this crop is a harvest. Stems never are; a CropBlock (modded subclasses
     * included) asks its own isMaxAge; any other block with an "age" property is ripe at its top
     * value (nether wart 3, cocoa 2, berries); blocks with no age are always ripe.
     */
    public static boolean isRipe(BlockState state) {
        if (dev.amman.proficiency.compat.AgriCraftCompat.isCropBlock(state)) {
            // Its growth is in a block entity: without a level nothing can be known, so it is not ripe.
            return false;
        }
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

    /** As {@link #isRipeCrop(BlockState)}, and it can read an AgriCraft crop's block entity. */
    public static boolean isRipeCrop(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
            BlockState state) {
        if (dev.amman.proficiency.compat.AgriCraftCompat.isCropBlock(state)) {
            return dev.amman.proficiency.compat.AgriCraftCompat.maturePlant(level, pos);
        }
        return isRipeCrop(state);
    }

    /**
     * The rule that decides what breaking this block pays, or null when none applies. An AgriCraft
     * crop is always a crop: a rule of its own (a datapack's) decides, and without one it pays what
     * wheat pays, whatever tag-based rule (mineable/axe) would otherwise have caught the block.
     */
    @Nullable
    public static XpMatch breakMatch(BlockState state, double hardness) {
        XpMatch match = XpSources.table().match(XpDomain.BREAK, XpSubjects.block(state, hardness));
        if (dev.amman.proficiency.compat.AgriCraftCompat.isCropBlock(state)
                && (match == null || (!match.paysNothing() && !match.has("crop")))) {
            return XpSources.table().match(XpDomain.BREAK,
                    XpSubjects.block(net.minecraft.world.level.block.Blocks.WHEAT.defaultBlockState(), hardness));
        }
        return match;
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
            case "axe" -> isAxe(tool);
            case "hoe" -> isHoe(tool);
            default -> true;
        };
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

    /**
     * Paxels (a pickaxe, shovel and axe in one) are tagged {@code c:tools/paxels} or
     * {@code forge:tools/paxels} by the mods that make them. A tag that does not exist is simply empty.
     */
    private static final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> PAXELS_C =
            ItemTags.create(new net.minecraft.resources.ResourceLocation("c", "tools/paxels"));
    private static final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> PAXELS_FORGE =
            ItemTags.create(new net.minecraft.resources.ResourceLocation("forge", "tools/paxels"));

    public static boolean isPaxel(ItemStack stack) {
        return stack.is(PAXELS_C) || stack.is(PAXELS_FORGE);
    }

    /**
     * What a tool can do is also read from the tool itself, so a hammer, an excavator, a paxel or a
     * Meka-Tool counts whatever it is tagged: the loader's tool actions ("can this dig like a
     * pickaxe?"). The tags and the vanilla classes stay as fallbacks. A paxel is a pickaxe, a shovel
     * and an axe at once; the block decides which skill the swing pays, so it never pays twice.
     * Weapons are not read this way: {@link #meleeSkill} keeps its own rule.
     */
    public static boolean isPickaxe(ItemStack stack) {
        return stack.is(ItemTags.PICKAXES) || stack.getItem() instanceof PickaxeItem || isPaxel(stack)
                || stack.canPerformAction(net.minecraftforge.common.ToolActions.PICKAXE_DIG);
    }

    public static boolean isShovel(ItemStack stack) {
        return stack.is(ItemTags.SHOVELS) || stack.getItem() instanceof ShovelItem || isPaxel(stack)
                || stack.canPerformAction(net.minecraftforge.common.ToolActions.SHOVEL_DIG);
    }

    public static boolean isAxe(ItemStack stack) {
        return stack.is(ItemTags.AXES) || stack.getItem() instanceof AxeItem || isPaxel(stack)
                || stack.canPerformAction(net.minecraftforge.common.ToolActions.AXE_DIG);
    }

    public static boolean isHoe(ItemStack stack) {
        return stack.is(ItemTags.HOES) || stack.getItem() instanceof HoeItem
                || stack.canPerformAction(net.minecraftforge.common.ToolActions.HOE_DIG);
    }
}
