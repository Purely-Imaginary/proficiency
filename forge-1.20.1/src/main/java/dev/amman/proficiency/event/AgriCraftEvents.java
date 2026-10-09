package dev.amman.proficiency.event;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.compat.AgriCraftCompat;
import dev.amman.proficiency.skill.AgriHarvest;
import dev.amman.proficiency.skill.BuildClassifier;
import dev.amman.proficiency.skill.ProcService;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillPassives;
import dev.amman.proficiency.skill.SkillService;
import dev.amman.proficiency.skill.SkillTools;
import dev.amman.proficiency.xp.XpMatch;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Farming XP for AgriCraft crop sticks (see {@link AgriCraftCompat}; it all stands down when the
 * mod is absent or its API does not match).
 *
 * <p>The usual AgriCraft harvest is a right-click that drops the produce and sets the plant back a
 * growth stage. Nothing is broken, so no break event exists to pay on. A right-click on a crop is
 * therefore snapshotted before the interaction and compared at the end of the same server tick:
 * the same plant, mature before, at a lower stage after, is a harvest. Planting a seed is the
 * same trick (no plant before, a plant after). Breaking a mature plant is paid from the break
 * event, as a ripe vanilla crop is. Every number comes from the xp_sources rules, with wheat's
 * rule as the fallback, so a datapack can retune {@code agricraft:crop}.
 *
 * <p>Only real players are paid: a FakePlayer (a deployer, a farming turtle) pays nothing, and a
 * crop pays at most once per position per tick.
 */
@Mod.EventBusSubscriber(modid = Proficiency.MOD_ID)
public final class AgriCraftEvents {

    /** Most extra stacks a Farming proc or passive may add to one harvest. */
    private static final int MAX_COPIES = 6;

    /**
     * One right-click on a crop. {@code drops} collects what spawned at the crop during the click:
     * AgriCraft's produce is an item entity with no pickup delay, so the player has usually picked it up
     * before the tick ends, and it can only be copied at the moment it appears.
     */
    private record Pending(ServerPlayer player, ServerLevel level, BlockPos pos, AgriHarvest.Snapshot before,
            boolean seed, BlockPos plantPos, AgriHarvest.Snapshot plantBefore, List<ItemStack> drops) {
    }

    /** True while this class spawns its own copies, so they are not recorded as drops in turn. */
    private static boolean copying;

    /** Right-clicks seen this tick, resolved at its end. Server thread only. */
    private static final Map<String, Pending> PENDING = new LinkedHashMap<>();

    /** One pay per position per tick, whichever of break or use came first. */
    private static final Set<String> PAID = new HashSet<>();

    private AgriCraftEvents() {
    }

    private static boolean real(Player player) {
        return player instanceof ServerPlayer && !(player instanceof FakePlayer);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !real(player) || !AgriCraftCompat.active()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockPos pos = event.getPos();
        boolean onCrop = AgriCraftCompat.isCropBlock(level.getBlockState(pos));
        boolean seed = AgriCraftCompat.isSeed(event.getItemStack());
        if (!onCrop && !seed) {
            return;
        }
        // Both hands fire this event; the first one's snapshot is the one from before anything happened.
        String key = player.getUUID() + "|" + level.dimension().location() + "|" + pos.asLong();
        Pending existing = PENDING.get(key);
        if (existing != null && (existing.seed() || !seed)) {
            return;
        }
        if (existing == null && PENDING.size() > 256) {
            return;
        }
        // A seed in the off hand upgrades the main hand's seedless entry (same click, same before-state).
        BlockPos plantPos = event.getFace() == null ? pos : pos.relative(event.getFace());
        if (existing != null) {
            PENDING.put(key, new Pending(player, level, existing.pos(), existing.before(), true,
                    existing.plantPos(), existing.plantBefore(), existing.drops()));
            return;
        }
        PENDING.put(key, new Pending(player, level, pos.immutable(),
                onCrop ? AgriCraftCompat.snapshot(level, pos) : null, seed, plantPos.immutable(),
                AgriCraftCompat.snapshot(level, plantPos), new ArrayList<>()));
    }

    /**
     * Records what a harvest drops at a pending crop (server thread, during the click). The window is
     * narrow on purpose: only a mature crop with a click pending this tick, only inside the crop's own
     * 0.75-block box. AgriCraft's pickup delay cannot be used to tell produce from a tossed stack
     * (its drop is picked up in the same tick), so the narrowness is the guard.
     */
    @SubscribeEvent
    public static void onItemSpawn(EntityJoinLevelEvent event) {
        if (copying || PENDING.isEmpty() || event.getLevel().isClientSide()
                || !(event.getEntity() instanceof ItemEntity item)) {
            return;
        }
        for (Pending pending : PENDING.values()) {
            if (pending.level() != event.getLevel() || pending.before() == null || !pending.before().mature()) {
                continue;
            }
            BlockPos at = pending.pos();
            if (Math.abs(item.getX() - (at.getX() + 0.5)) < 0.75 && Math.abs(item.getZ() - (at.getZ() + 0.5)) < 0.75
                    && item.getY() >= at.getY() - 0.25 && item.getY() < at.getY() + 1.25) {
                pending.drops().add(item.getItem().copy());
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!PENDING.isEmpty()) {
            List<Pending> todo = new ArrayList<>(PENDING.values());
            PENDING.clear();
            for (Pending pending : todo) {
                try {
                    resolve(pending);
                } catch (RuntimeException e) {
                    Proficiency.LOG.warn("AgriCraft XP check failed: {}", e.toString());
                }
            }
        }
        PAID.clear();
    }

    private static void resolve(Pending p) {
        if (p.player().isRemoved() || p.player().level() != p.level()) {
            return;
        }
        AgriHarvest.Snapshot after = AgriCraftCompat.snapshot(p.level(), p.pos());
        if (AgriHarvest.isHarvest(p.before(), after)) {
            harvested(p.player(), p.level(), p.pos(), p.before(), p.drops());
            return;
        }
        if (!p.seed() || p.player().isCreative()) {
            return;
        }
        // Planting: on bare sticks the crop is the clicked block; on soil the seed made a new crop
        // block next to it.
        if (AgriHarvest.isPlanting(p.before(), after) && p.before() != null) {
            planted(p.player(), p.level(), p.pos(), after);
        } else if (p.before() == null) {
            AgriHarvest.Snapshot planted = AgriCraftCompat.snapshot(p.level(), p.plantPos());
            if (AgriHarvest.isPlanting(p.plantBefore(), planted)) {
                planted(p.player(), p.level(), p.plantPos(), planted);
            }
        }
    }

    /** A mature plant was broken (called from the break event with the block entity still there). */
    static void onBreak(Player player, BlockPos pos) {
        if (!real(player) || !AgriCraftCompat.active()) {
            return;
        }
        AgriHarvest.Snapshot at = AgriCraftCompat.snapshot(player.level(), pos);
        if (AgriHarvest.breakPays(at)) {
            harvested((ServerPlayer) player, (ServerLevel) player.level(), pos, at, null);
        }
    }

    private static boolean firstPayThisTick(ServerLevel level, BlockPos pos) {
        return PAID.add(AgriHarvest.payKey(level.dimension().location().toString(), pos.asLong(),
                level.getGameTime()));
    }

    private static void harvested(ServerPlayer player, ServerLevel level, BlockPos pos,
            AgriHarvest.Snapshot plant, List<ItemStack> drops) {
        BlockState state = level.getBlockState(pos);
        XpMatch rule = SkillTools.breakMatch(state.isAir() ? crop() : state, 0.0);
        if (rule == null || rule.paysNothing() || player.getAbilities().instabuild
                || !firstPayThisTick(level, pos)) {
            return;
        }
        Skill skill = rule.rule().skill;
        SkillService.grant(player, skill, rule.xp(), AgriHarvest.plantKey(plant.plantId()));
        if (drops == null || skill != Skill.FARMING) {
            // A break drops through the world's own drop handling, which AgriCraft's block does its
            // own way: no proc, no bonus copy, nothing to duplicate.
            return;
        }
        // Bountiful Harvest and the passive's bonus drop copy what the harvest just dropped. Seed
        // Saver, Golden Crop and replanting are for blocks that break: the plant is still there.
        boolean procced = ProcService.fire(player, Skill.FARMING, pos);
        int copies = 0;
        double chance = SkillPassives.dropChance(Skill.FARMING, SkillService.bonus(player, Skill.FARMING));
        if (chance > 0 && level.getRandom().nextDouble() < chance) {
            copies++;
        }
        if (procced) {
            copies += GatheringEvents.procCopies(player, Skill.FARMING);
        }
        duplicateDrops(level, pos, drops, Math.min(copies, MAX_COPIES));
    }

    private static BlockState crop() {
        return Blocks.WHEAT.defaultBlockState();
    }

    private static void duplicateDrops(ServerLevel level, BlockPos pos, List<ItemStack> drops, int copies) {
        if (copies <= 0 || drops.isEmpty()) {
            return;
        }
        copying = true;
        try {
            for (int i = 0; i < copies; i++) {
                for (ItemStack stack : drops) {
                    ItemEntity extra = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                            stack.copy());
                    level.addFreshEntity(extra);
                }
            }
        } finally {
            copying = false;
        }
    }

    private static void planted(ServerPlayer player, ServerLevel level, BlockPos pos, AgriHarvest.Snapshot plant) {
        if (!firstPayThisTick(level, pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        XpMatch rule = BuildClassifier.placement(state);
        if (rule != null && rule.paysNothing()) {
            return;
        }
        if (rule == null || !rule.has("planting")) {
            rule = BuildClassifier.placement(crop());
        }
        if (rule == null || rule.paysNothing()) {
            return;
        }
        SkillService.grant(player, rule.rule().skill, rule.xp(), AgriHarvest.plantKey(plant.plantId()));
    }
}
