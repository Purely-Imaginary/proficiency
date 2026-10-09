package dev.amman.proficiency.event;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.perk.TalentService;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillService;
import dev.amman.proficiency.skill.SkillTools;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.entity.living.BabyEntitySpawnEvent;
import dev.amman.proficiency.compat.LivingIncomingDamageEvent;
import net.minecraftforge.event.entity.player.BonemealEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import dev.amman.proficiency.compat.BlockDropsEvent;
import net.minecraftforge.event.TickEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The gathering trees' specials: Mining, Woodcutting, Excavation, Farming and Fishing.
 *
 * <p>The break and drop pieces are not subscribers. {@link GatheringEvents} owns the order a break
 * happens in (XP, proc, cascade, bonus copies, smelting, collection) and calls into here at the right
 * step, because two separate subscribers on one {@link BlockDropsEvent} have no defined order, and
 * Mountain King has to run after everything that adds a drop. What has its own event (ticks, damage,
 * breeding, bone meal) subscribes here directly.
 */
@Mod.EventBusSubscriber(modid = Proficiency.MOD_ID)
public final class TalentGatheringEvents {

    /** Geologist: how far ore sense looks, and how many ores it marks before it stops looking. */
    private static final int ORE_SENSE_RADIUS = 6;
    private static final int ORE_SENSE_MAX_MARKS = 24;
    /** Vein Miner stops searching well past what it may take, so a huge modded vein costs nothing. */
    private static final int VEIN_SEARCH_LIMIT = 64;
    /** Shaper of Earth: a desert dune can be tall, and a column taller than this is not "on you". */
    private static final int GRAVITY_WELL_HEIGHT = 24;

    private static final int DARK_SIGHT_Y = 30;
    /** Refreshed every second; kept well above 200 ticks, where Night Vision starts to flicker. */
    private static final int DARK_SIGHT_TICKS = 300;
    private static final int DARK_SIGHT_CHECK_TICKS = 20;
    private static final int FERTILE_TICKS = 100;
    private static final int FERTILE_RADIUS = 3;

    private static final double BREEDING_XP = 2.0;
    private static final double BONEMEAL_XP = 0.5;
    /** Sunken Treasure's book is enchanted like a vanilla treasure catch's. */
    private static final int TREASURE_BOOK_LEVELS = 30;

    /** Who is wearing Dark Sight's Night Vision right now, so it is only ever ours we take off. */
    private static final Set<UUID> DARK_SIGHTED = ConcurrentHashMap.newKeySet();

    private TalentGatheringEvents() {
    }

    // ---- Mining ------------------------------------------------------------------------------

    /**
     * Geologist: nearby ore sparkles, visible to the miner only. Its own bounded scan, the same shape
     * as Cave Sense's but smaller: this one can fire on any ore break, not only on a proc.
     */
    static void oreSense(ServerLevel level, Player player, BlockPos centre) {
        int rank = TalentService.rank(player, Skill.MINING, "ore_sense");
        if (rank <= 0 || !(player instanceof ServerPlayer miner)
                || level.getRandom().nextDouble() >= GatheringMath.perRank(rank, 0.05)) {
            return;
        }
        int marked = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int r = ORE_SENSE_RADIUS;
        for (int dx = -r; dx <= r && marked < ORE_SENSE_MAX_MARKS; dx++) {
            for (int dy = -r; dy <= r && marked < ORE_SENSE_MAX_MARKS; dy++) {
                for (int dz = -r; dz <= r && marked < ORE_SENSE_MAX_MARKS; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    cursor.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
                    if (!level.getBlockState(cursor).is(Tags.Blocks.ORES)) {
                        continue;
                    }
                    marked++;
                    level.sendParticles(miner, ParticleTypes.GLOW, true,
                            cursor.getX() + 0.5, cursor.getY() + 0.5, cursor.getZ() + 0.5,
                            4, 0.3, 0.3, 0.3, 0.0);
                }
            }
        }
    }

    /**
     * Vein Miner: the connected blocks of the same ore go with the one you broke, two per rank.
     * They break with your pickaxe, so Fortune and Silk Touch apply and ore XP drops, but through
     * the cascade, so they never proc or pay a bonus copy of their own.
     */
    static void veinMiner(ServerLevel level, Player player, BlockPos origin, BlockState state) {
        int rank = TalentService.rank(player, Skill.MINING, "vein_miner");
        if (rank <= 0 || !player.hasCorrectToolForDrops(state)) {
            return;
        }
        int limit = 2 * rank;
        Block ore = state.getBlock();
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        List<BlockPos> vein = new ArrayList<>();
        seen.add(origin);
        queue.add(origin);
        while (!queue.isEmpty() && vein.size() < limit && seen.size() < VEIN_SEARCH_LIMIT) {
            BlockPos current = queue.removeFirst();
            for (int dx = -1; dx <= 1 && vein.size() < limit; dx++) {
                for (int dy = -1; dy <= 1 && vein.size() < limit; dy++) {
                    for (int dz = -1; dz <= 1 && vein.size() < limit; dz++) {
                        BlockPos next = current.offset(dx, dy, dz);
                        if (!seen.add(next) || !level.getBlockState(next).is(ore)) {
                            continue;
                        }
                        vein.add(next);
                        queue.addLast(next);
                    }
                }
            }
        }
        GatheringEvents.cascade(level, player, vein, player.getMainHandItem());
    }

    /** Dark Sight: Night Vision below Y 30 while a pickaxe is in hand, and only then. */
    private static void darkSight(ServerPlayer player) {
        boolean wants = player.getY() < DARK_SIGHT_Y
                && TalentService.rank(player, Skill.MINING, "dark_sight") > 0
                && SkillTools.isPickaxe(player.getMainHandItem());
        if (wants) {
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, DARK_SIGHT_TICKS, 0,
                    true, false, true));
            DARK_SIGHTED.add(player.getUUID());
            return;
        }
        if (DARK_SIGHTED.remove(player.getUUID())) {
            // A potion drunk on top outlasts ours and is left alone.
            MobEffectInstance current = player.getEffect(MobEffects.NIGHT_VISION);
            if (current != null && current.isAmbient() && current.getDuration() <= DARK_SIGHT_TICKS) {
                player.removeEffect(MobEffects.NIGHT_VISION);
            }
        }
    }

    /** Hard Hat: falling blocks, anvils and stalactites hurt 50% less per rank. */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) {
            return;
        }
        DamageSource source = event.getSource();
        if (!source.is(DamageTypes.FALLING_BLOCK) && !source.is(DamageTypes.FALLING_ANVIL)
                && !source.is(DamageTypes.FALLING_STALACTITE)) {
            return;
        }
        int rank = TalentService.rank(player, Skill.MINING, "hard_hat");
        if (rank <= 0) {
            return;
        }
        double kept = 1.0 - GatheringMath.perRank(rank, 0.50);
        if (kept <= 0) {
            event.setCanceled(true);
            return;
        }
        event.setAmount((float) (event.getAmount() * kept));
    }

    // ---- Woodcutting -------------------------------------------------------------------------

    /** Sapling Keeper and Elder of the Grove: the chance a felled bottom log leaves its sapling. */
    static double replantChance(Player player) {
        if (TalentService.rank(player, Skill.WOODCUTTING, "full_replant") > 0) {
            return 1.0;
        }
        return GatheringMath.perRank(TalentService.rank(player, Skill.WOODCUTTING, "replant_sapling"), 0.33);
    }

    /** True when the log at {@code pos} stood on the ground: the bottom of a tree, not a branch. */
    static boolean isBottomLog(Level level, BlockPos pos) {
        return level.getBlockState(pos.below()).is(BlockTags.DIRT);
    }

    /**
     * Plants the sapling a log came from where the log stood. Found by name rather than by a table,
     * so a modded {@code x_log} with an {@code x_sapling} next to it works; anything else, a mangrove
     * or a nether stem, is skipped quietly. Call only once the log is gone.
     */
    static void replantSapling(ServerLevel level, BlockPos pos, Block log) {
        ResourceLocation logId = BuiltInRegistries.BLOCK.getKey(log);
        String saplingPath = GatheringMath.saplingPathFor(logId.getPath());
        if (saplingPath == null || !isBottomLog(level, pos) || !level.getBlockState(pos).isAir()) {
            return;
        }
        Optional<Block> sapling = BuiltInRegistries.BLOCK.getOptional(
                new ResourceLocation(logId.getNamespace(), saplingPath));
        if (sapling.isEmpty()) {
            return;
        }
        BlockState planted = sapling.get().defaultBlockState();
        if (planted.canSurvive(level, pos)) {
            level.setBlockAndUpdate(pos, planted);
        }
    }

    /** Woodland Stride: three seconds of Speed per rank after each Timber. */
    static void woodlandStride(Player player) {
        int rank = TalentService.rank(player, Skill.WOODCUTTING, "woodland_stride");
        if (rank > 0) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60 * rank, 0));
        }
    }

    // ---- Excavation --------------------------------------------------------------------------

    /** What Archaeologist and Fossil Hunter turn up. Built per call: ItemStacks are mutable. */
    static ItemStack randomFind(RandomSource random) {
        return switch (random.nextInt(7)) {
            case 0 -> new ItemStack(Items.BONE);
            case 1 -> new ItemStack(Items.GOLD_NUGGET, 1 + random.nextInt(3));
            case 2 -> new ItemStack(Items.IRON_NUGGET, 1 + random.nextInt(3));
            case 3 -> new ItemStack(Items.CLAY_BALL, 1 + random.nextInt(2));
            case 4 -> new ItemStack(Items.FLINT);
            case 5 -> new ItemStack(Items.BOWL);
            default -> new ItemStack(Items.EMERALD);
        };
    }

    /** Mole: each block dug has a small chance to fill one hunger point. */
    static void mole(Player player) {
        int rank = TalentService.rank(player, Skill.EXCAVATION, "mole");
        if (rank > 0 && player.getRandom().nextDouble() < GatheringMath.perRank(rank, 0.03)) {
            player.getFoodData().eat(1, 0.0f);
        }
    }

    /** Fossil Hunter: a Landslide turns up one find for every three blocks it cleared. */
    static void fossil(ServerLevel level, Player player, BlockPos origin, int cleared) {
        if (cleared < 3 || TalentService.rank(player, Skill.EXCAVATION, "fossil") <= 0) {
            return;
        }
        for (int i = 0; i < cleared / 3; i++) {
            Block.popResource(level, origin, randomFind(level.getRandom()));
        }
    }

    /**
     * Shaper of Earth: digging a gravity block takes the column above it with it, so nothing falls
     * on you. Only gravity blocks, only straight up, and only so far. Top down, so no block is left
     * hanging for a tick to fall from.
     */
    static void gravityWell(ServerLevel level, Player player, BlockPos origin, BlockState state) {
        if (!(state.getBlock() instanceof FallingBlock)
                || TalentService.rank(player, Skill.EXCAVATION, "gravity_well") <= 0) {
            return;
        }
        List<BlockPos> column = new ArrayList<>();
        for (int dy = 1; dy <= GRAVITY_WELL_HEIGHT; dy++) {
            BlockPos above = origin.above(dy);
            if (!(level.getBlockState(above).getBlock() instanceof FallingBlock)) {
                break;
            }
            column.add(above);
        }
        java.util.Collections.reverse(column);
        GatheringEvents.cascade(level, player, column, ItemStack.EMPTY);
    }

    // ---- Farming -----------------------------------------------------------------------------

    /** A crop at its last stage: the only thing a harvest talent pays out on. */
    static boolean isRipe(BlockState state) {
        return state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state);
    }

    /**
     * Scythe Sweep: a hoe harvest takes the ripe crops around it, one block further per rank. Same
     * layer only and ripe only, through the cascade so the swept crops cannot sweep again. Green
     * Thumb still replants them, or a sweep would leave bare farmland behind.
     */
    static void scythe(ServerLevel level, Player player, BlockPos origin, BlockState state) {
        int radius = TalentService.rank(player, Skill.FARMING, "scythe");
        if (radius <= 0 || !isRipe(state) || !player.getMainHandItem().is(ItemTags.HOES)) {
            return;
        }
        List<BlockPos> swept = new ArrayList<>();
        List<Block> crops = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                BlockPos next = origin.offset(dx, 0, dz);
                BlockState crop = level.getBlockState(next);
                if (isRipe(crop)) {
                    swept.add(next);
                    crops.add(crop.getBlock());
                }
            }
        }
        GatheringEvents.cascade(level, player, swept, ItemStack.EMPTY);
        if (TalentService.hasSpecial(player, Skill.FARMING, "always_replant")) {
            for (int i = 0; i < swept.size(); i++) {
                if (level.getBlockState(swept.get(i)).isAir()) {
                    level.setBlockAndUpdate(swept.get(i), crops.get(i).defaultBlockState());
                }
            }
        }
    }

    /** Fertile Soil: every five seconds, each growing crop near you may grow a stage. */
    private static void fertile(ServerPlayer player) {
        int rank = TalentService.rank(player, Skill.FARMING, "fertile");
        if (rank <= 0) {
            return;
        }
        double chance = GatheringMath.perRank(rank, 0.05);
        ServerLevel level = player.serverLevel();
        BlockPos centre = player.blockPosition();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int r = FERTILE_RADIUS;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    cursor.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
                    BlockState state = level.getBlockState(cursor);
                    if (!(state.getBlock() instanceof CropBlock crop) || crop.isMaxAge(state)
                            || level.getRandom().nextDouble() >= chance) {
                        continue;
                    }
                    level.setBlock(cursor, crop.getStateForAge(crop.getAge(state) + 1), Block.UPDATE_CLIENTS);
                    level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                            cursor.getX() + 0.5, cursor.getY() + 0.5, cursor.getZ() + 0.5,
                            3, 0.25, 0.25, 0.25, 0.0);
                }
            }
        }
    }

    /** Husbandry: breeding trains Farming, and sometimes the pair has twins. */
    @SubscribeEvent
    public static void onBabySpawn(BabyEntitySpawnEvent event) {
        Player player = event.getCausedByPlayer();
        if (player == null || player.level().isClientSide()) {
            return;
        }
        int rank = TalentService.rank(player, Skill.FARMING, "twins");
        if (rank <= 0) {
            return;
        }
        SkillService.grant(player, Skill.FARMING, BREEDING_XP, "proficiency.xplog.source.breeding");
        if (event.getChild() == null
                || !(event.getParentA() instanceof Animal mother)
                || !(event.getParentB() instanceof Animal father)
                || !(mother.level() instanceof ServerLevel level)
                || level.getRandom().nextDouble() >= GatheringMath.perRank(rank, 0.10)) {
            return;
        }
        AgeableMob twin = mother.getBreedOffspring(level, father);
        if (twin == null) {
            return;
        }
        twin.setBaby(true);
        twin.moveTo(mother.getX(), mother.getY(), mother.getZ(), 0.0f, 0.0f);
        level.addFreshEntityWithPassengers(twin);
    }

    /**
     * Druid: bone meal trains Farming and a quarter of the time is not used up. Vanilla shrinks the
     * stack by one after this event, only server-side and only on a valid target, so growing it by
     * one first under those same conditions is exactly "not used up".
     */
    @SubscribeEvent
    public static void onBonemeal(BonemealEvent event) {
        // Forge 1.20.1 hands a dispenser's bone meal a fake player; NeoForge hands it none.
        Player player = event.getEntity();
        if (player == null || player instanceof net.minecraftforge.common.util.FakePlayer
                || !(event.getLevel() instanceof ServerLevel level)
                || !(event.getBlock().getBlock() instanceof net.minecraft.world.level.block.BonemealableBlock target)
                || !target.isValidBonemealTarget(level, event.getPos(), event.getBlock(), false)
                || TalentService.rank(player, Skill.FARMING, "druid") <= 0) {
            return;
        }
        SkillService.grant(player, Skill.FARMING, BONEMEAL_XP, "proficiency.xplog.source.bone_meal");
        if (level.getRandom().nextDouble() < 0.25 && !event.getStack().isEmpty()) {
            event.getStack().grow(1);
        }
    }

    // ---- Drops -------------------------------------------------------------------------------

    /**
     * The talents that add something to an ordinary (not cascaded) break's drops: Forager, Sifter,
     * Archaeologist, Seed Saver and Harvest Lord. Sapling Keeper runs here too, because by the time
     * drops are built the log is gone and there is room for the sapling.
     */
    static void extraDrops(BlockDropsEvent event, Player player, Skill skill) {
        ServerLevel level = event.getLevel();
        BlockState state = event.getState();
        BlockPos pos = event.getPos();
        RandomSource random = level.getRandom();
        switch (skill) {
            case WOODCUTTING -> {
                if (state.is(BlockTags.LEAVES)) {
                    int rank = TalentService.rank(player, Skill.WOODCUTTING, "forager");
                    if (rank > 0 && random.nextDouble() < GatheringMath.perRank(rank, 0.10)) {
                        addDrop(event, new ItemStack(Items.APPLE));
                    }
                } else if (state.is(BlockTags.LOGS) && isBottomLog(level, pos)
                        && random.nextDouble() < replantChance(player)) {
                    replantSapling(level, pos, state.getBlock());
                }
            }
            case EXCAVATION -> {
                boolean gravel = state.is(Tags.Blocks.GRAVEL);
                if (gravel) {
                    int rank = TalentService.rank(player, Skill.EXCAVATION, "sifter");
                    if (rank > 0 && random.nextDouble() < GatheringMath.perRank(rank, 0.15)) {
                        addDrop(event, new ItemStack(Items.FLINT));
                    }
                }
                if (gravel || state.is(BlockTags.DIRT) || state.is(BlockTags.SAND)) {
                    int rank = TalentService.rank(player, Skill.EXCAVATION, "archaeology");
                    if (rank > 0 && random.nextDouble() < GatheringMath.perRank(rank, 0.01)) {
                        addDrop(event, randomFind(random));
                    }
                }
            }
            case FARMING -> {
                if (!isRipe(state)) {
                    return;
                }
                int seeds = TalentService.rank(player, Skill.FARMING, "seed_saver");
                if (seeds > 0 && random.nextDouble() < GatheringMath.perRank(seeds, 0.15)) {
                    ItemStack seed = state.getBlock().getCloneItemStack(level, pos, state);
                    if (!seed.isEmpty()) {
                        seed.setCount(1);
                        addDrop(event, seed);
                    }
                }
                if (TalentService.rank(player, Skill.FARMING, "golden_crop") > 0 && random.nextDouble() < 0.02) {
                    addDrop(event, new ItemStack(random.nextBoolean() ? Items.GOLDEN_CARROT : Items.GOLDEN_APPLE));
                }
            }
            default -> {
            }
        }
    }

    private static void addDrop(BlockDropsEvent event, ItemStack stack) {
        BlockPos pos = event.getPos();
        event.getDrops().add(new ItemEntity(event.getLevel(),
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack));
    }

    /**
     * Mountain King and Sawmill: the drops go into your inventory instead of onto the ground. Runs
     * last, after bonus copies and smelting, so what you get is what would have dropped. Whatever
     * does not fit stays a drop, so a full inventory loses nothing.
     */
    static void collect(BlockDropsEvent event, Player player) {
        for (ItemEntity drop : event.getDrops()) {
            ItemStack stack = drop.getItem().copy();
            if (stack.isEmpty()) {
                continue;
            }
            player.getInventory().add(stack);
            drop.setItem(stack);
        }
        event.getDrops().removeIf(drop -> drop.getItem().isEmpty());
    }

    // ---- Fishing -----------------------------------------------------------------------------

    /** Double Hook: sometimes a second of the fish you caught. Junk and treasure are not fish. */
    static void doubleHook(Player player, List<ItemStack> drops) {
        int rank = TalentService.rank(player, Skill.FISHING, "double_hook");
        if (rank <= 0 || player.getRandom().nextDouble() >= GatheringMath.perRank(rank, 0.10)) {
            return;
        }
        for (ItemStack stack : drops) {
            if (stack.is(ItemTags.FISHES)) {
                drops.add(stack.copy());
                return;
            }
        }
    }

    /**
     * Fresh Catch: raw fish come up cooked. Cooked by the furnace recipe rather than a table, so a
     * modded fish with one cooks too, and a pufferfish, which has none, stays raw.
     */
    static void freshCatch(ServerLevel level, Player player, List<ItemStack> drops) {
        int rank = TalentService.rank(player, Skill.FISHING, "fresh_catch");
        if (rank <= 0 || player.getRandom().nextDouble() >= GatheringMath.perRank(rank, 0.20)) {
            return;
        }
        for (int i = 0; i < drops.size(); i++) {
            ItemStack raw = drops.get(i);
            if (!raw.is(ItemTags.FISHES)) {
                continue;
            }
            ItemStack cooked = level.getRecipeManager()
                    .getRecipeFor(RecipeType.SMELTING, new net.minecraft.world.SimpleContainer(raw), level)
                    .map(recipe -> recipe.getResultItem(level.registryAccess()))
                    .orElse(ItemStack.EMPTY);
            if (!cooked.isEmpty()) {
                ItemStack replacement = cooked.copy();
                replacement.setCount(Math.min(raw.getCount(), replacement.getMaxStackSize()));
                drops.set(i, replacement);
            }
        }
    }

    /** Rain Dancer: the XP multiplier for a catch while it rains. */
    static double rainAngler(Player player) {
        int rank = TalentService.rank(player, Skill.FISHING, "rain_angler");
        return rank > 0 && player.level().isRaining() ? 1.0 + rank * 0.15 : 1.0;
    }

    /** Sunken Treasure: an enchanted book, from the pool vanilla loot draws from, with the treasure. */
    static void sunkenTreasure(ServerLevel level, Player player, List<ItemStack> drops) {
        if (TalentService.rank(player, Skill.FISHING, "sunken_treasure") <= 0) {
            return;
        }
        // 1.21's #on_random_loot pool is 1.20.1's "treasure allowed" random enchanting.
        drops.add(EnchantmentHelper.enchantItem(level.getRandom(), new ItemStack(Items.BOOK),
                TREASURE_BOOK_LEVELS, true));
    }

    // ---- Ticks and cleanup -------------------------------------------------------------------

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % DARK_SIGHT_CHECK_TICKS == 0) {
            darkSight(player);
        }
        if (player.tickCount % FERTILE_TICKS == 0) {
            fertile(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        DARK_SIGHTED.remove(event.getEntity().getUUID());
    }
}
