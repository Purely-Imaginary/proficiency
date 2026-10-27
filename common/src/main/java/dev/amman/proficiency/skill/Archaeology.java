package dev.amman.proficiency.skill;

import dev.amman.proficiency.perk.TalentService;
import dev.amman.proficiency.xp.XpDomain;
import dev.amman.proficiency.xp.XpMatch;
import dev.amman.proficiency.xp.XpSources;
import dev.amman.proficiency.xp.XpSubjects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Brushing a suspicious sand or gravel block (Excavation). The brush mixin calls {@link #completed}
 * the moment the tenth stroke lands, before the block turns into its plain block, and {@link
 * #extraStroke} after every counted stroke. The rules are data ({@code brush} in xp_sources); the
 * numbers of Careful Hands are {@link BrushRules}.
 *
 * <p>Anti-farm: a suspicious block a player placed pays nothing (the placed-blocks store, which also
 * drops the mark), a machine's fake player gets nothing, and only a finished brush pays: partial
 * strokes are worth nothing, and the block is gone afterwards, so there is nothing to repeat.
 */
public final class Archaeology {

    private Archaeology() {
    }

    /**
     * A brush finished {@code state} at {@code pos}. Pays the rule's XP and the first-time bonus of
     * the block and of the find, but only when the rule pays something; for Careful Hands may roll
     * the loot table once more, whatever the rule says (the talent is not an XP source).
     *
     * @param found the item the block held (may be empty), not yet dropped
     * @param table the loot table the block was generated with, or null when it has none left
     * @return the second find for the caller to drop the way vanilla drops a find, or empty
     */
    public static ItemStack completed(ServerLevel level, BlockPos pos, BlockState state, Player player,
            ItemStack found, @Nullable LootTable table) {
        if (SkillTools.isFakePlayer(player)) {
            return ItemStack.EMPTY;
        }
        if (PlacedBlocks.consume(level, pos, state)) {
            // A player put this block here: it pays nothing, and neither does a talent on it.
            return ItemStack.EMPTY;
        }
        XpMatch match = XpSources.table().match(XpDomain.BRUSH, XpSubjects.block(state));
        Skill paying = null;
        if (match != null && !match.paysNothing() && match.xp() > 0) {
            paying = match.rule().skill;
            SkillService.grant(player, paying, match.xp(), state.getBlock().getDescriptionId());
            if (!found.isEmpty()) {
                SkillService.grantFirstTimeOnly(player, paying, found.getItem().getDescriptionId());
            }
        }
        if (table != null && BrushRules.secondRoll(TalentService.rank(player, Skill.EXCAVATION, "careful_hands"),
                player.getRandom().nextDouble())) {
            ItemStack more = roll(level, pos, player, table);
            if (!more.isEmpty()) {
                if (paying != null) {
                    SkillService.grantFirstTimeOnly(player, paying, more.getItem().getDescriptionId());
                }
                return more;
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * Careful Hands, faster brushing: after a counted stroke, may count one more. Returns how many
     * extra strokes to add (0 or 1). The caller adds them to the block's own counter.
     */
    public static int extraStroke(Player player, int strokesNow) {
        if (SkillTools.isFakePlayer(player) || !(player instanceof ServerPlayer)) {
            return 0;
        }
        int rank = TalentService.rank(player, Skill.EXCAVATION, "careful_hands");
        return BrushRules.extraStroke(rank, strokesNow, player.getRandom().nextDouble()) ? 1 : 0;
    }

    /** One more roll of the block's table, with the player's luck, as vanilla rolls it. */
    public static ItemStack roll(ServerLevel level, BlockPos pos, Player player, LootTable table) {
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .withLuck(player.getLuck())
                .withParameter(LootContextParams.THIS_ENTITY, player)
                .create(LootContextParamSets.CHEST);
        var items = table.getRandomItems(params, level.random.nextLong());
        return items.isEmpty() ? ItemStack.EMPTY : items.get(0);
    }
}
