package dev.amman.proficiency.event;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.skill.ProcService;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillService;
import dev.amman.proficiency.skill.SkillTools;
import dev.amman.proficiency.perk.TalentService;
import net.minecraft.world.item.crafting.RecipeType;
import dev.amman.proficiency.platform.Tags;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import dev.amman.proficiency.skill.SkillService;
import net.minecraft.world.phys.Vec3;
import dev.amman.proficiency.platform.bus.SubscribeEvent;
import dev.amman.proficiency.platform.bus.EventBusSubscriber;
import dev.amman.proficiency.platform.event.entity.player.ItemFishedEvent;
import dev.amman.proficiency.platform.event.entity.player.PlayerEvent;
import dev.amman.proficiency.platform.event.level.BlockDropsEvent;
import dev.amman.proficiency.platform.event.level.BlockEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@EventBusSubscriber(modid = Proficiency.MOD_ID)
public final class GatheringEvents {

    /** Share of the raw skill bonus that becomes a bonus-drop chance rather than break speed. */

    /** Ceiling on a single Timber, so one lucky swing on a jungle giant is not a lag spike. */
    private static final int TIMBER_LIMIT = 128;
    private static final int TIMBER_LIMIT_GREEDY = 192;
    /** Heartwood's proc power raises the limit above, but never past this. */
    private static final int TIMBER_CEILING = 256;

    /**
     * BreakEvent fires before the block goes, BlockDropsEvent as it goes, and the proc is rolled
     * once in the first and read back in the second.
     *
     * <p>This carries the position, and that is the whole point. A plain boolean was wrong twice
     * over. Breaking a block in creative fires BreakEvent but never produces drops, so the flag was
     * left set forever and the next player's right-click crop harvest anywhere on the server was
     * mistaken for a real break and silently earned nothing. And Timber fells its extra logs from
     * inside the origin block's BreakEvent, so those logs' drop events arrived first and ate the
     * proc, landing the Motherlode on an arbitrary neighbour. Matching on position fixes both: a
     * stale context cannot match a different block.
     */
    private record BreakContext(BlockPos pos, Skill skill, boolean procced) {
    }

    private static final ThreadLocal<BreakContext> CURRENT_BREAK = new ThreadLocal<>();

    /**
     * Set while Timber, Landslide or a talent (Vein Miner, Scythe Sweep, Shaper of Earth) is breaking
     * its extra blocks, so they do not proc, pay XP or pay a bonus copy in turn.
     */
    private static final ThreadLocal<Boolean> CASCADING = ThreadLocal.withInitial(() -> false);

    private static final List<ItemStack> TREASURE = List.of(
            new ItemStack(Items.NAUTILUS_SHELL),
            new ItemStack(Items.NAME_TAG),
            new ItemStack(Items.SADDLE),
            new ItemStack(Items.DIAMOND),
            new ItemStack(Items.HEART_OF_THE_SEA),
            new ItemStack(Items.ENCHANTED_GOLDEN_APPLE));

    private GatheringEvents() {
    }

    /** Faster swings. Client-side too, so the crack animation matches what the server will do. */
    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        Skill skill = SkillTools.harvestSkill(player.getMainHandItem(), event.getState());
        if (skill == null || skill == Skill.FARMING) {
            return;
        }
        double bonus = SkillService.bonus(player, skill);
        if (bonus > 0) {
            event.setNewSpeed((float) (event.getOriginalSpeed() * dev.amman.proficiency.skill.SkillPassives.more(bonus)));
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player == null || player.level().isClientSide()) {
            return;
        }
        BlockState state = event.getState();
        // A block a player placed pays nothing: no XP, first-time bonus, proc or talent. The mark
        // is cleared by the drops handler, which sees the same break.
        if (dev.amman.proficiency.skill.PlacedBlocks.isUnpaid(player.level(), event.getPos(), state)) {
            if (player.getAbilities().instabuild) {
                // Creative breaks drop nothing, so the drops handler never forgets the mark.
                dev.amman.proficiency.skill.PlacedBlocks.consume(player.level(), event.getPos(), state);
            }
            return;
        }
        Skill skill = SkillTools.harvestSkill(player.getMainHandItem(), state);
        if (skill == null) {
            return;
        }

        if (CASCADING.get()) {
            return;
        }

        // Idea 31: a block the held tool cannot harvest drops nothing, so it pays nothing either:
        // no XP, no first-time bonus, no proc, no talents. Creative keeps its old behaviour.
        if (!dropsWithHeldTool(player, state)) {
            return;
        }

        BlockPos pos = event.getPos();
        float hardness = state.getDestroySpeed(event.getLevel(), pos);
        double xp = skill == Skill.FARMING ? 1.0 : SkillTools.blockXp(state, hardness);
        SkillService.grant(player, skill, xp, state.getBlock().getDescriptionId());

        boolean procced = ProcService.fire(player, skill, pos);
        CURRENT_BREAK.set(new BreakContext(pos.immutable(), skill, procced));

        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (procced) {
            switch (skill) {
                case WOODCUTTING -> {
                    // Falling Tree already took the whole tree; doubling up would fight it for blocks.
                    if (!dev.amman.proficiency.ModCompat.fallingTreeHandlesTrees()) {
                        timber(level, player, pos);
                    }
                }
                case EXCAVATION -> landslide(level, player, pos);
                default -> {
                    // Mining, Farming and the rest express their proc through the drops instead.
                }
            }
        }
        // The talents that take more blocks with this one, or react to it being broken.
        switch (skill) {
            case MINING -> {
                if (state.is(Tags.Blocks.ORES)) {
                    TalentGatheringEvents.oreSense(level, player, pos);
                    TalentGatheringEvents.veinMiner(level, player, pos, state);
                }
            }
            case EXCAVATION -> {
                TalentGatheringEvents.mole(player);
                TalentGatheringEvents.gravityWell(level, player, pos, state);
            }
            case FARMING -> TalentGatheringEvents.scythe(level, player, pos, state);
            default -> {
            }
        }
    }

    /**
     * True when breaking this block with the held tool would give its drop. Creative always counts.
     * Reads the creative ability, not isCreative(), which the GameTest mock player always answers yes to.
     */
    public static boolean dropsWithHeldTool(Player player, BlockState state) {
        return player.getAbilities().instabuild
                || !state.requiresCorrectToolForDrops() || player.hasCorrectToolForDrops(state);
    }

    /** Fells the rest of the tree: every log connected to the one you cut, up to the limit. */
    private static void timber(ServerLevel level, Player player, BlockPos origin) {
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        List<BlockPos> felling = new ArrayList<>();
        queue.add(origin);
        seen.add(origin);

        boolean greedy = dev.amman.proficiency.perk.TalentService.hasSpecial(
                player, Skill.WOODCUTTING, "timber_greedy");
        int limit = GatheringMath.timberLimit(greedy ? TIMBER_LIMIT_GREEDY : TIMBER_LIMIT,
                ProcService.power(player, Skill.WOODCUTTING), TIMBER_CEILING);

        while (!queue.isEmpty() && felling.size() < limit) {
            BlockPos current = queue.removeFirst();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        BlockPos next = current.offset(dx, dy, dz);
                        if (!seen.add(next)) {
                            continue;
                        }
                        BlockState state = level.getBlockState(next);
                        if (dev.amman.proficiency.skill.PlacedBlocks.isUnpaid(level, next, state)) {
                            continue;
                        }
                        if (state.is(BlockTags.LOGS)) {
                            felling.add(next);
                            queue.addLast(next);
                        } else if (greedy && state.is(BlockTags.LEAVES)) {
                            // Felled but never expanded from. Enqueuing leaves lets the search walk
                            // canopy to canopy across a whole forest on one proc.
                            felling.add(next);
                        }
                    }
                }
            }
        }
        // Counted before the cascade: afterwards every position is air.
        long logs = felling.stream().filter(pos -> level.getBlockState(pos).is(BlockTags.LOGS)).count();
        // Sapling Keeper: the foot of the trunk may be anywhere in the felled set, not only where you
        // swung, so the bottom logs are noted now and replanted once they are gone.
        List<BlockPos> roots = new ArrayList<>();
        List<Block> rootLogs = new ArrayList<>();
        double replant = TalentGatheringEvents.replantChance(player);
        if (replant > 0) {
            for (BlockPos pos : felling) {
                BlockState state = level.getBlockState(pos);
                if (state.is(BlockTags.LOGS) && TalentGatheringEvents.isBottomLog(level, pos)) {
                    roots.add(pos);
                    rootLogs.add(state.getBlock());
                }
            }
        }
        cascade(level, player, felling, ItemStack.EMPTY);
        for (int i = 0; i < roots.size(); i++) {
            if (level.getRandom().nextDouble() < replant) {
                TalentGatheringEvents.replantSapling(level, roots.get(i), rootLogs.get(i));
            }
        }
        TalentGatheringEvents.woodlandStride(player);
        if (logs > 0 && TalentService.hasSynergy(player, "woodsman")) {
            SkillService.grant(player, Skill.AXES, logs * WOODSMAN_AXES_XP, "proficiency.xplog.source.timber");
        }
    }

    /** Takes the whole face out at once: the 3x3 square perpendicular to where you are looking. */
    private static void landslide(ServerLevel level, Player player, BlockPos origin) {
        Vec3 look = player.getLookAngle();
        Direction facing = Direction.getNearest(look.x, look.y, look.z);
        List<BlockPos> extra = new ArrayList<>();

        int reach = dev.amman.proficiency.perk.TalentService.hasSpecial(
                player, Skill.EXCAVATION, "landslide_wide") ? 2 : 1;
        for (int a = -reach; a <= reach; a++) {
            for (int b = -reach; b <= reach; b++) {
                if (a == 0 && b == 0) {
                    continue;
                }
                BlockPos next = switch (facing.getAxis()) {
                    case X -> origin.offset(0, a, b);
                    case Y -> origin.offset(a, 0, b);
                    case Z -> origin.offset(a, b, 0);
                };
                if (level.getBlockState(next).is(BlockTags.MINEABLE_WITH_SHOVEL)) {
                    extra.add(next);
                }
            }
        }
        cascade(level, player, extra, ItemStack.EMPTY);
        TalentGatheringEvents.fossil(level, player, origin, extra.size());
    }

    /** Cleared each tick so a break that never produced drops cannot strand a stale context. */
    public static void clearBreakContext() {
        CURRENT_BREAK.remove();
    }

    /**
     * Breaks extra blocks on the player's behalf without any of them counting as a break of their
     * own. With an empty tool this is a plain {@code destroyBlock}, which drops as if broken by hand
     * (right for logs, sand and crops). Given a tool, the drops are rolled with it, so a vein of
     * ore keeps its Fortune and its XP.
     */
    static void cascade(ServerLevel level, Player player, List<BlockPos> positions, ItemStack tool) {
        if (positions.isEmpty()) {
            return;
        }
        boolean outer = CASCADING.get();
        CASCADING.set(true);
        try {
            for (BlockPos pos : positions) {
                // Blocks a player placed are not part of the tree or the vein.
                if (dev.amman.proficiency.skill.PlacedBlocks.isUnpaid(level, pos, level.getBlockState(pos))) {
                    continue;
                }
                if (tool.isEmpty()) {
                    level.destroyBlock(pos, true, player);
                    continue;
                }
                BlockState state = level.getBlockState(pos);
                if (state.isAir()) {
                    continue;
                }
                level.levelEvent(2001, pos, Block.getId(state));
                Block.dropResources(state, level, pos,
                        state.hasBlockEntity() ? level.getBlockEntity(pos) : null, player, tool);
                level.setBlock(pos, level.getFluidState(pos).createLegacyBlock(), Block.UPDATE_ALL);
            }
        } finally {
            CASCADING.set(outer);
        }
    }

    /**
     * The payoff for a high gathering skill: a chance at a second lot of drops, and on a proc a
     * third as well. Duplicates what the block actually dropped rather than guessing a loot table,
     * so it behaves for modded blocks.
     */
    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof Player player)) {
            return;
        }
        // Player-placed block: pays nothing and forgets the mark. A ripe planted crop pays as usual.
        if (dev.amman.proficiency.skill.PlacedBlocks.consume(player.level(), event.getPos(), event.getState())) {
            return;
        }
        Skill skill = SkillTools.harvestSkill(event.getTool(), event.getState());
        if (skill == null) {
            return;
        }

        // Blocks felled by Timber or Landslide already paid out through the origin break. They are
        // still smelted and collected, or Mountain King would stop at the first block of a vein.
        if (CASCADING.get()) {
            finishDrops(event, player, skill, true);
            return;
        }

        BreakContext context = CURRENT_BREAK.get();
        boolean sameBlock = context != null && context.pos().equals(event.getPos());
        boolean procced = sameBlock && context.skill() == skill && context.procced();
        if (sameBlock) {
            CURRENT_BREAK.remove();
        }

        // Right Click Harvest drops the crop without ever breaking the block. Without this, the
        // most common way to farm in this pack would earn nothing at all.
        if (!sameBlock && skill == Skill.FARMING) {
            SkillService.grant(player, Skill.FARMING, 1.0, event.getState().getBlock().getDescriptionId());
            procced = ProcService.fire(player, Skill.FARMING, event.getPos());
        }

        int copies = 0;
        double chance = dev.amman.proficiency.skill.SkillPassives.dropChance(skill, SkillService.bonus(player, skill));
        if (chance > 0 && player.level().getRandom().nextDouble() < chance) {
            copies++;
        }
        if (procced) {
            copies += procCopies(player, skill);
        }
        if (copies > 0) {
            duplicate(event, copies);
        }

        // Bountiful Harvest puts the crop back in the ground for you, and Master Farming does it
        // every single time rather than only on a proc.
        boolean replant = procced || TalentService.hasSpecial(player, Skill.FARMING, "always_replant");
        if (replant && skill == Skill.FARMING && !event.getState().isAir()) {
            event.getLevel().setBlockAndUpdate(
                    event.getPos(), event.getState().getBlock().defaultBlockState());
        }

        TalentGatheringEvents.extraDrops(event, player, skill);

        // Sawmill takes the tree Timber felled, and the log you cut to start it is part of that tree.
        boolean timbered = procced && skill == Skill.WOODCUTTING
                && !dev.amman.proficiency.ModCompat.fallingTreeHandlesTrees();
        finishDrops(event, player, skill, timbered);
    }

    /**
     * The proc's extra copies of the drops. Motherlode and Bountiful Harvest scale with proc power
     * directly. Landslide's power buys copies of the origin block on top of the face it clears;
     * Timber's goes into how much tree it fells, so its copies stay flat.
     */
    private static int procCopies(Player player, Skill skill) {
        double power = ProcService.power(player, skill);
        return switch (skill) {
            case MINING, FARMING -> ProcService.roundRandomly(player, 2 * power);
            case EXCAVATION -> 2 + ProcService.roundRandomly(player, power - 1);
            default -> 2;
        };
    }

    /**
     * The last two steps of every gathering drop, cascaded or not: smelting, then collection.
     * Collection is last so it takes exactly what would have hit the ground.
     *
     * @param timbered true when these drops come from a tree Timber felled
     */
    private static void finishDrops(BlockDropsEvent event, Player player, Skill skill, boolean timbered) {
        // Gated on the block being ore, not merely pickaxe-minable. Mining covers cobblestone and
        // stone, and vanilla has real furnace recipes for both, so without this a Master Miner's
        // ordinary cobblestone silently came up as stone with no way to turn it off.
        if (skill == Skill.MINING
                && event.getState().is(Tags.Blocks.ORES)
                && TalentService.hasSpecial(player, Skill.MINING, "auto_smelt")) {
            autoSmelt(event, player);
        }
        boolean collect = switch (skill) {
            case MINING -> TalentService.rank(player, Skill.MINING, "magnet") > 0;
            case WOODCUTTING -> timbered && TalentService.rank(player, Skill.WOODCUTTING, "sawmill") > 0;
            default -> false;
        };
        if (collect) {
            TalentGatheringEvents.collect(event, player);
        }
    }

    /**
     * Master Mining brings ore up already smelted. Looks the recipe up rather than keeping a table
     * of ores, so it works for any mod's ore that has a furnace recipe, which is most of them.
     */
    private static void autoSmelt(BlockDropsEvent event, Player player) {
        ServerLevel level = event.getLevel();
        // Prospector's Forge: the smith in you gets paid for every ore the miner in you smelts,
        // and sometimes the forge gives back more than it took.
        boolean forge = TalentService.hasSynergy(player, "forge_smelt");
        boolean smeltedAny = false;
        for (ItemEntity drop : event.getDrops()) {
            ItemStack raw = drop.getItem();
            if (raw.isEmpty()) {
                continue;
            }
            ItemStack smelted = level.getRecipeManager()
                    .getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(raw), level)
                    .map(holder -> holder.value().getResultItem(level.registryAccess()))
                    .orElse(ItemStack.EMPTY);
            if (smelted.isEmpty()) {
                continue;
            }
            ItemStack replacement = smelted.copy();
            // Clamped: a modded recipe with an output count above one, multiplied by a Fortune
            // drop or by this handler's own duplicate() pass, can otherwise build a stack larger
            // than the item allows, which then behaves oddly on pickup and merge.
            int total = smelted.getCount() * raw.getCount();
            if (forge && level.getRandom().nextDouble() < FORGE_BONUS_CHANCE) {
                total++;
            }
            replacement.setCount(Math.min(total, replacement.getMaxStackSize()));
            drop.setItem(replacement);
            smeltedAny = true;
        }
        if (forge && smeltedAny) {
            SkillService.grant(player, Skill.SMITHING, FORGE_SMITHING_XP, "proficiency.xplog.source.auto_smelt");
        }
    }

    private static final double FORGE_BONUS_CHANCE = 0.25;
    private static final double FORGE_SMITHING_XP = 0.5;
    /** Woodsman's Edge: each log Timber takes teaches the axe arm a little. */
    private static final double WOODSMAN_AXES_XP = 0.25;

    private static void duplicate(BlockDropsEvent event, int copies) {
        List<ItemEntity> extra = new ArrayList<>();
        for (ItemEntity drop : event.getDrops()) {
            ItemStack original = drop.getItem();
            if (original.isEmpty()) {
                continue;
            }
            for (int i = 0; i < copies; i++) {
                extra.add(new ItemEntity(event.getLevel(),
                        drop.getX(), drop.getY(), drop.getZ(), original.copy()));
            }
        }
        event.getDrops().addAll(extra);
    }

    /** Fishing spares the rod, sometimes doubles the catch, and on a proc pulls up treasure. */
    @SubscribeEvent
    public static void onFished(ItemFishedEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }

        double bonus = SkillService.bonus(player, Skill.FISHING);
        // Master Angler: the rod is never worn at all, which makes Patience's saving moot.
        boolean unbreakable = TalentService.rank(player, Skill.FISHING, "unbreakable_rod") > 0;
        if (unbreakable) {
            event.damageRodBy(0);
        }
        if (bonus > 0) {
            // damageRodBy sets rather than adds, and refuses negatives.
            int spared = (int) Math.round(event.getRodDamage() * dev.amman.proficiency.skill.SkillPassives.rodSpared(bonus));
            if (spared > 0 && !unbreakable) {
                event.damageRodBy(Math.max(0, event.getRodDamage() - spared));
            }
            if (player.level().getRandom().nextDouble() < dev.amman.proficiency.skill.SkillPassives.doubleCatch(bonus) && !event.getDrops().isEmpty()) {
                event.getDrops().add(event.getDrops().get(0).copy());
            }
        }

        TalentGatheringEvents.doubleHook(player, event.getDrops());

        ServerLevel level = (ServerLevel) player.level();
        if (ProcService.fire(player, Skill.FISHING)) {
            // Deep Waters: proc power is how many times the treasure table is rolled.
            int rolls = Math.max(1, ProcService.roundRandomly(player, ProcService.power(player, Skill.FISHING)));
            for (int i = 0; i < rolls; i++) {
                int pick = level.getRandom().nextInt(TREASURE.size());
                event.getDrops().add(TREASURE.get(pick).copy());
            }
            TalentGatheringEvents.sunkenTreasure(level, player, event.getDrops());
        }
        // After Double Hook, so the second fish comes up cooked as well.
        TalentGatheringEvents.freshCatch(level, player, event.getDrops());
        // The log names the catch itself, "Raw Cod", rather than the act of fishing.
        String caught = event.getDrops().isEmpty() ? "proficiency.xplog.source.fishing"
                : event.getDrops().get(0).getDescriptionId();
        SkillService.grant(player, Skill.FISHING, 3.0 * TalentGatheringEvents.rainAngler(player), caught);
    }
}
