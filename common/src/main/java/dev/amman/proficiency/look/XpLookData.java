package dev.amman.proficiency.look;

import dev.amman.proficiency.config.ProficiencyConfig;
import dev.amman.proficiency.event.CombatEvents;
import dev.amman.proficiency.event.GatheringEvents;
import dev.amman.proficiency.skill.CritRules;
import dev.amman.proficiency.skill.PlacedBlocks;
import dev.amman.proficiency.skill.RangeXp;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillService;
import dev.amman.proficiency.skill.SkillTools;
import dev.amman.proficiency.skill.SpawnOrigin;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The server half of the Jade tooltip: what looking at a block or a mob would pay the looking player,
 * written into the small tag Jade carries to the client. It asks the same resolvers the XP code asks
 * ({@code SkillTools.harvestSkill}, {@code breakXp}, {@code PlacedBlocks.peekUnpaid},
 * {@code CombatEvents.killBonus}, {@code SkillService.preview}), reads and changes nothing (no Tempo step, no
 * placed mark dropped), and writes
 * nothing at all when there is nothing to show, so an empty tag is the common answer.
 *
 * <p>Both Minecraft versions compile this file unchanged (forge-1.20.1's sharedHearthJava).
 */
public final class XpLookData {

    /** Skill ordinal; absent when there is nothing to show. */
    public static final String SKILL = "pxs";
    /** What breaking the block pays now, or what one hit of the mob pays. */
    public static final String XP = "pxv";
    /** The kill bonus of a mob. */
    public static final String KILL = "pxk";
    /** The first-time bonus that would be paid now, 0 or absent when none. */
    public static final String FIRST = "pxf";
    /** {@link LookRules} flag bits. */
    public static final String FLAGS = "pxl";

    private XpLookData() {
    }

    /**
     * What breaking the block at {@code pos} with the held item would pay {@code player} now. The same order
     * of gates as {@code GatheringEvents.onBlockBreak}: a placed block pays nothing, no rule or an unripe crop
     * pays nothing, and a block the held tool cannot harvest pays nothing.
     */
    public static void block(ServerPlayer player, ServerLevel level, BlockPos pos, CompoundTag out) {
        if (SkillTools.isFakePlayer(player) || player.isSpectator() || !level.isLoaded(pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return;
        }
        Skill skill = SkillTools.harvestSkill(player.getMainHandItem(), state);
        if (skill == null) {
            return;
        }
        boolean canHarvest = GatheringEvents.dropsWithHeldTool(player, state);
        boolean placed = PlacedBlocks.peekUnpaid(level, pos, state);
        double base = SkillTools.breakXp(state, state.getDestroySpeed(level, pos));
        float xp = SkillService.preview(player, skill, base, 1.0);
        LookRules.Verdict verdict = LookRules.block(true, canHarvest, placed, ProficiencyConfig.enabled(skill), xp);
        if (verdict == LookRules.Verdict.HIDDEN) {
            return;
        }
        out.putInt(SKILL, skill.ordinal());
        if (verdict == LookRules.Verdict.PLACED) {
            out.putInt(FLAGS, LookRules.PLACED);
            return;
        }
        out.putFloat(XP, xp);
        float first = SkillService.previewFirstTime(player, skill, state.getBlock().getDescriptionId());
        if (first > 0) {
            out.putFloat(FIRST, first);
            out.putInt(FLAGS, LookRules.FIRST);
        }
    }

    /**
     * What a hit and a kill of this mob would pay with the held weapon. A dummy, a player and anything the
     * weapon in hand gives no skill for show nothing; a mob that came from a spawn egg or a command says it
     * pays nothing.
     */
    public static void mob(ServerPlayer player, Entity entity, CompoundTag out) {
        if (!(entity instanceof LivingEntity victim) || entity instanceof Player
                || SkillTools.isFakePlayer(player) || player.isSpectator() || CombatEvents.paysNoCombatXp(victim)) {
            return;
        }
        Skill skill = heldCombatSkill(player.getMainHandItem());
        if (skill == null || !ProficiencyConfig.enabled(skill)) {
            return;
        }
        double origin = SpawnOrigin.xpFactor(victim);
        out.putInt(SKILL, skill.ordinal());
        if (!(origin > 0)) {
            out.putInt(FLAGS, LookRules.MOB | LookRules.PLACED);
            return;
        }
        // The real hit also scales by the crit factor (taken as no crit: it cannot be known) and, for a bow or
        // a crossbow, the range of the shot as it stands now; the kill takes one more Tempo step than the hit.
        double factor = CritRules.xpFactor(skill, false)
                * RangeXp.factor(skill, player.distanceTo(victim), ProficiencyConfig.rangedXpPerDoubling());
        float hit = SkillService.preview(player, skill, origin, 1.0, factor, 0);
        float kill = SkillService.preview(player, skill, CombatEvents.killBonus(victim) * origin, 1.0, 1.0, 1);
        if (!LookRules.mobPays(true, hit, kill)) {
            out.remove(SKILL);
            return;
        }
        out.putFloat(XP, hit);
        out.putFloat(KILL, kill);
        int flags = LookRules.MOB;
        // Only a kill pays the first-time bonus of a mob, and only when the kill itself pays.
        float first = kill > 0
                ? SkillService.previewFirstTime(player, skill, victim.getType().getDescriptionId()) : 0f;
        if (first > 0) {
            out.putFloat(FIRST, first);
            flags |= LookRules.FIRST;
        }
        out.putInt(FLAGS, flags);
    }

    /** The skill the held item fights with: a bow or a crossbow shoots, anything else is a melee swing. */
    @Nullable
    static Skill heldCombatSkill(ItemStack stack) {
        if (stack.getItem() instanceof CrossbowItem) {
            return Skill.CROSSBOWS;
        }
        if (stack.getItem() instanceof BowItem) {
            return Skill.ARCHERY;
        }
        return SkillTools.meleeSkill(stack);
    }
}
