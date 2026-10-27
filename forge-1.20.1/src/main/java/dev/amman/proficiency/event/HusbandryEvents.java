package dev.amman.proficiency.event;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.config.ProficiencyConfig;
import dev.amman.proficiency.hearth.HearthRange;
import dev.amman.proficiency.perk.TalentService;
import dev.amman.proficiency.compat.Attr;
import dev.amman.proficiency.compat.LivingDamageEvent;
import dev.amman.proficiency.compat.LivingIncomingDamageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.BabyEntitySpawnEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import dev.amman.proficiency.skill.HusbandryMath;
import dev.amman.proficiency.skill.HusbandryMath.Job;
import dev.amman.proficiency.skill.ProcService;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.Shearable;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Husbandry (the 36th skill): looking after animals. It owns the XP (breeding, taming, shearing,
 * milking, brushing, feeding a baby, honey), the passive (bred babies grow faster, your pets have
 * more health), Twins and its tree. The numbers are in {@link HusbandryMath}.
 *
 * <p>Breeding moved here from Farming (2026-10-26): Farming no longer pays for a breeding, and its
 * Herdsman node only keeps its twin chance, rolled when Twins did not land.
 *
 * <p>Anti-farm rules, all here:
 * <ul>
 * <li>A real player, in survival (a dispenser has no player; a machine's fake player is paid
 * nothing by {@link SkillService}).</li>
 * <li>A pair pays once per 5 minutes; an animal is milked for XP once per 2 minutes and brushed
 * once per 5; a shear pays only for a coat that had regrown; a baby pays for a feeding only when
 * the food really made it grow.</li>
 * <li>A crowded pen (more than 8 animals within 8 blocks) pays less, down to 10%.</li>
 * <li>The spot rule: one spot (12 blocks) pays at most 12 jobs in 5 minutes.</li>
 * <li>Taming pays 8 times in 30 minutes.</li>
 * </ul>
 *
 * <p>The vanilla click on an animal does its work inside {@code interact}, after the shared bus
 * has seen it. So a click is remembered with what it should change (an age, a coat, a bucket, a
 * brush's wear, a hive's honey) and looked at again a tick later: only a change pays.
 */
@Mod.EventBusSubscriber(modid = Proficiency.MOD_ID)
public final class HusbandryEvents {

    public static final String SOURCE_BREED = "proficiency.xplog.source.husbandry_breed";
    public static final String SOURCE_TAME = "proficiency.xplog.source.husbandry_tame";
    public static final String SOURCE_SHEAR = "proficiency.xplog.source.husbandry_shear";
    public static final String SOURCE_MILK = "proficiency.xplog.source.husbandry_milk";
    public static final String SOURCE_BRUSH = "proficiency.xplog.source.husbandry_brush";
    public static final String SOURCE_FEED = "proficiency.xplog.source.husbandry_feed";
    public static final String SOURCE_HONEY = "proficiency.xplog.source.husbandry_honey";

    private static final ResourceLocation PET_HEALTH =
            new ResourceLocation(Proficiency.MOD_ID, "husbandry_pet_health");

    /** A grown-baby growth that has to wait for the baby to exist (the event runs before it spawns). */
    private record Growth(UUID child, ResourceKey<Level> dimension, long due, double bonus, boolean headStart) {
    }

    /** A click whose result is looked at a tick later. */
    private record Pending(UUID player, ResourceKey<Level> dimension, UUID target, BlockPos pos, Job job,
            long due, int before, String subject, InteractionHand hand) {
    }

    /** A pet being tamed: looked at until it is, or the watch runs out. */
    private record Watch(UUID player, ResourceKey<Level> dimension, UUID animal, long until) {
    }

    private record Spot(long tick, double x, double z) {
    }

    private static final List<Growth> GROWTHS = new ArrayList<>();
    private static final List<Pending> PENDING = new ArrayList<>();
    private static final List<Watch> WATCHES = new ArrayList<>();
    /** job:subject -> game time of the last payout. */
    private static final Map<String, Long> LAST_PAID = new ConcurrentHashMap<>();
    private static final Map<UUID, ArrayDeque<Spot>> SPOTS = new ConcurrentHashMap<>();
    private static final Map<UUID, ArrayDeque<Long>> TAMES = new ConcurrentHashMap<>();
    /** mob -> {player, game time} of the last player hit on it, for Shared Strength. */
    private static final Map<UUID, long[]> HIT_BY = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> HIT_PLAYER = new ConcurrentHashMap<>();
    /** owner -> game time a Faithful Friend last saved a pet. */
    private static final Map<UUID, Long> FAITHFUL_USED = new ConcurrentHashMap<>();

    private HusbandryEvents() {
    }

    // ---- Who counts ----------------------------------------------------------------------------

    private static boolean counts(ServerPlayer player) {
        // Read from the game mode itself: the GameTest mock claims creative.
        return player.gameMode.isSurvival() && ProficiencyConfig.enabled(Skill.HUSBANDRY);
    }

    private static long now(Entity entity) {
        return entity.level().getGameTime();
    }

    private static int rank(Player player, String special) {
        return TalentService.rank(player, Skill.HUSBANDRY, special);
    }

    // ---- Paying --------------------------------------------------------------------------------

    /**
     * Pays one job, if every guard allows it.
     *
     * @param subject what the cooldown is about (an animal, a pair, a hive), or null for none
     * @return the XP paid, 0 if nothing was
     */
    public static float pay(ServerPlayer player, Job job, Entity at, @org.jetbrains.annotations.Nullable String subject,
            String source) {
        if (!counts(player)) {
            return 0f;
        }
        long now = now(at);
        String key = subject == null ? null : job.name() + ":" + subject;
        if (key != null) {
            Long last = LAST_PAID.get(key);
            if (!HusbandryMath.cooledDown(job, last == null ? -1L : last, now)) {
                return 0f;
            }
        }
        if (!HusbandryMath.spotAllows(recentNear(player, at.getX(), at.getZ(), now))) {
            return 0f;
        }
        String type = BuiltInRegistries.ENTITY_TYPE.getKey(at.getType()).toString();
        double base = ProficiencyConfig.husbandryXp(job) * HusbandryMath.speciesWorth(type)
                * HusbandryMath.penFactor(pen(at));
        float paid = SkillService.grant(player, Skill.HUSBANDRY, base, source);
        if (paid > 0f) {
            noteSpot(player, at, now);
            if (key != null) {
                LAST_PAID.put(key, now);
                if (LAST_PAID.size() > 4096) {
                    LAST_PAID.values().removeIf(t -> now - t > 12000L);
                }
            }
        }
        return paid;
    }

    /** Animals within the pen radius of the job, counting the animal itself. */
    private static int pen(Entity at) {
        double r = HusbandryMath.PEN_RADIUS;
        return at.level().getEntitiesOfClass(Animal.class, at.getBoundingBox().inflate(r, r, r)).size();
    }

    private static int recentNear(ServerPlayer player, double x, double z, long now) {
        ArrayDeque<Spot> spots = SPOTS.get(player.getUUID());
        if (spots == null) {
            return 0;
        }
        int near = 0;
        synchronized (spots) {
            spots.removeIf(spot -> !HusbandryMath.inSpotWindow(spot.tick(), now));
            double limit = HusbandryMath.SPOT_RADIUS * HusbandryMath.SPOT_RADIUS;
            for (Spot spot : spots) {
                double dx = spot.x() - x;
                double dz = spot.z() - z;
                if (dx * dx + dz * dz <= limit) {
                    near++;
                }
            }
        }
        return near;
    }

    private static void noteSpot(ServerPlayer player, Entity at, long now) {
        ArrayDeque<Spot> spots = SPOTS.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>());
        synchronized (spots) {
            spots.add(new Spot(now, at.getX(), at.getZ()));
            while (spots.size() > 128) {
                spots.poll();
            }
        }
    }

    // ---- Breeding and Twins --------------------------------------------------------------------

    @SubscribeEvent
    public static void onBabySpawn(BabyEntitySpawnEvent event) {
        Player cause = event.getCausedByPlayer();
        if (cause == null || cause.level().isClientSide() || event.isCanceled()) {
            return;
        }
        boolean twinned = false;
        if (cause instanceof ServerPlayer player && event.getChild() != null
                && event.getParentA() instanceof Animal mother
                && event.getParentB() instanceof Animal father
                && mother.level() instanceof ServerLevel level) {
            String pair = pairKey(mother, father);
            pay(player, Job.BREED, mother, pair, SOURCE_BREED);
            if (counts(player)) {
                scheduleGrowth(player, event.getChild(), level);
                twinned = rollTwins(player, mother, father, level);
            }
        }
        if (!twinned) {
            // Farming's Herdsman keeps its own chance (and no XP) when Twins did not land.
            TalentGatheringEvents.farmingTwins(event);
        }
    }

    private static String pairKey(Entity a, Entity b) {
        String x = a.getUUID().toString();
        String y = b.getUUID().toString();
        return x.compareTo(y) < 0 ? x + "+" + y : y + "+" + x;
    }

    private static boolean rollTwins(ServerPlayer player, Animal mother, Animal father, ServerLevel level) {
        // No Twins in a crowded pen: a breeder farm gets no free babies either.
        if (HusbandryMath.penFactor(pen(mother)) < 1.0 || !ProcService.fire(player, Skill.HUSBANDRY, mother)) {
            return false;
        }
        spawnExtra(player, mother, father, level);
        int big = rank(player, "big_litter");
        if (big > 0 && level.getRandom().nextDouble() < HusbandryMath.tripletsChance(big)) {
            spawnExtra(player, mother, father, level);
        }
        return true;
    }

    private static void spawnExtra(ServerPlayer player, Animal mother, Animal father, ServerLevel level) {
        AgeableMob extra = mother.getBreedOffspring(level, father);
        if (extra == null) {
            return;
        }
        extra.setBaby(true);
        extra.moveTo(mother.getX(), mother.getY(), mother.getZ(), 0.0f, 0.0f);
        level.addFreshEntityWithPassengers(extra);
        scheduleGrowth(player, extra, level);
    }

    /** Faster growing for a bred baby, once it exists: the event runs before the vanilla spawn. */
    private static void scheduleGrowth(ServerPlayer player, AgeableMob child, ServerLevel level) {
        double bonus = ProficiencyAttachments.of(player).bonus(Skill.HUSBANDRY);
        boolean head = rank(player, "herd_master") > 0;
        if (bonus <= 0 && !head) {
            return;
        }
        synchronized (GROWTHS) {
            GROWTHS.add(new Growth(child.getUUID(), level.dimension(), level.getGameTime() + 5, bonus, head));
        }
    }

    // ---- Clicks on animals and hives ---------------------------------------------------------------

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)
                || player.level().isClientSide() || !counts(player)) {
            return;
        }
        Entity target = event.getTarget();
        InteractionHand hand = event.getHand();
        ItemStack stack = event.getItemStack();
        long now = now(player);
        ResourceKey<Level> dim = player.level().dimension();

        if (target instanceof AbstractHorse horse && !horse.isTamed() && !horse.isBaby()
                && hand == InteractionHand.MAIN_HAND && stack.isEmpty()) {
            int gentle = rank(player, "gentle_hands");
            if (gentle > 0) {
                horse.modifyTemper(HusbandryMath.gentleTemper(gentle));
            }
        }
        if ((target instanceof TamableAnimal tamable && !tamable.isTame())
                || (target instanceof AbstractHorse wild && !wild.isTamed())) {
            watchTame(player, target, now, dim);
        }
        if (!(target instanceof Animal animal)) {
            return;
        }
        if (animal.isBaby()) {
            if (animal.isFood(stack)) {
                queue(new Pending(player.getUUID(), dim, animal.getUUID(), null, Job.FEED, now + 2,
                        animal.getAge(), animal.getUUID().toString(), hand));
            }
            return;
        }
        if (stack.is(Items.SHEARS) && (animal instanceof Sheep || animal instanceof MushroomCow)
                && animal instanceof Shearable shearable && shearable.readyForShearing()) {
            // A mooshroom is replaced by a cow when sheared, so it is judged by the shears' wear.
            boolean mooshroom = animal instanceof MushroomCow;
            queue(new Pending(player.getUUID(), dim, animal.getUUID(), null, Job.SHEAR, now + 2,
                    mooshroom ? stack.getDamageValue() : 1, mooshroom ? "mooshroom" : null, hand));
        } else if (stack.is(Items.BUCKET) && (animal instanceof Cow || animal instanceof Goat)) {
            queue(new Pending(player.getUUID(), dim, animal.getUUID(), null, Job.MILK, now + 2,
                    player.getInventory().countItem(Items.MILK_BUCKET), animal.getUUID().toString(), hand));
        }
    }

    @SubscribeEvent
    public static void onClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)
                || player.level().isClientSide() || !counts(player)) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (!stack.is(Items.GLASS_BOTTLE) && !stack.is(Items.SHEARS)) {
            return;
        }
        BlockPos pos = event.getPos();
        BlockState state = player.level().getBlockState(pos);
        if (!(state.getBlock() instanceof BeehiveBlock)
                || state.getValue(BeehiveBlock.HONEY_LEVEL) < BeehiveBlock.MAX_HONEY_LEVELS) {
            return;
        }
        queue(new Pending(player.getUUID(), player.level().dimension(), null, pos.immutable(), Job.HONEY,
                now(player) + 2, state.getValue(BeehiveBlock.HONEY_LEVEL), "hive:" + player.level().dimension().location() + ":" + pos.asLong(),
                event.getHand()));
    }

    private static void queue(Pending pending) {
        synchronized (PENDING) {
            for (Pending p : PENDING) {
                if (p.player().equals(pending.player()) && p.job() == pending.job()
                        && java.util.Objects.equals(p.target(), pending.target())
                        && java.util.Objects.equals(p.pos(), pending.pos())) {
                    return;
                }
            }
            PENDING.add(pending);
        }
    }

    private static void watchTame(ServerPlayer player, Entity animal, long now, ResourceKey<Level> dim) {
        synchronized (WATCHES) {
            for (Watch watch : WATCHES) {
                if (watch.animal().equals(animal.getUUID()) && watch.player().equals(player.getUUID())) {
                    return;
                }
            }
            WATCHES.add(new Watch(player.getUUID(), dim, animal.getUUID(), now + HusbandryMath.TAME_WATCH_TICKS));
        }
    }

    // ---- A tick later --------------------------------------------------------------------------

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (GROWTHS.isEmpty() && PENDING.isEmpty() && WATCHES.isEmpty()) {
            return;
        }
        List<Growth> growths;
        synchronized (GROWTHS) {
            growths = new ArrayList<>(GROWTHS);
        }
        for (Growth growth : growths) {
            ServerLevel level = server.getLevel(growth.dimension());
            if (level == null) {
                drop(growth);
                continue;
            }
            Entity child = level.getEntity(growth.child());
            if (child instanceof AgeableMob baby) {
                if (baby.isBaby()) {
                    baby.setAge(HusbandryMath.bredBabyAge(baby.getAge(), growth.bonus(), growth.headStart()));
                }
                drop(growth);
            } else if (level.getGameTime() > growth.due()) {
                drop(growth);
            }
        }
        List<Pending> pending;
        synchronized (PENDING) {
            pending = new ArrayList<>(PENDING);
        }
        for (Pending p : pending) {
            ServerLevel level = server.getLevel(p.dimension());
            if (level == null) {
                drop(p);
                continue;
            }
            if (level.getGameTime() < p.due()) {
                continue;
            }
            drop(p);
            ServerPlayer player = server.getPlayerList().getPlayer(p.player());
            if (player != null && player.level() == level) {
                resolve(player, level, p);
            }
        }
        List<Watch> watches;
        synchronized (WATCHES) {
            watches = new ArrayList<>(WATCHES);
        }
        for (Watch watch : watches) {
            ServerLevel level = server.getLevel(watch.dimension());
            ServerPlayer player = server.getPlayerList().getPlayer(watch.player());
            Entity animal = level == null ? null : level.getEntity(watch.animal());
            if (level == null || player == null || animal == null || level.getGameTime() > watch.until()
                    || !animal.isAlive()) {
                drop(watch);
                continue;
            }
            if (owner(animal) != null && owner(animal).equals(player.getUUID())) {
                drop(watch);
                tamed(player, animal);
            }
        }
    }

    private static void drop(Growth value) {
        synchronized (GROWTHS) {
            GROWTHS.remove(value);
        }
    }

    private static void drop(Pending value) {
        synchronized (PENDING) {
            PENDING.remove(value);
        }
    }

    private static void drop(Watch value) {
        synchronized (WATCHES) {
            WATCHES.remove(value);
        }
    }

    @org.jetbrains.annotations.Nullable
    private static UUID owner(Entity animal) {
        if (animal instanceof TamableAnimal t && t.isTame()) {
            return t.getOwnerUUID();
        }
        if (animal instanceof AbstractHorse h && h.isTamed()) {
            return h.getOwnerUUID();
        }
        return null;
    }

    private static void tamed(ServerPlayer player, Entity animal) {
        if (!counts(player)) {
            return;
        }
        long now = now(animal);
        ArrayDeque<Long> times = TAMES.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>());
        synchronized (times) {
            times.removeIf(t -> now < t || now - t >= HusbandryMath.TAME_WINDOW_TICKS);
            if (times.size() >= HusbandryMath.TAME_LIMIT) {
                return;
            }
        }
        if (pay(player, Job.TAME, animal, animal.getUUID().toString(), SOURCE_TAME) > 0f) {
            synchronized (times) {
                times.add(now);
            }
            // The first of a species: the usual first-time bonus and banner.
            SkillService.grantFirstTimeOnly(player, Skill.HUSBANDRY, animal.getType().getDescriptionId());
        }
    }

    private static void resolve(ServerPlayer player, ServerLevel level, Pending p) {
        if (p.job() == Job.HONEY) {
            BlockState state = level.getBlockState(p.pos());
            if (state.getBlock() instanceof BeehiveBlock
                    && state.getValue(BeehiveBlock.HONEY_LEVEL) < p.before()) {
                pay(player, Job.HONEY, player, p.subject(), SOURCE_HONEY);
            }
            return;
        }
        Entity target = level.getEntity(p.target());
        if (p.job() == Job.SHEAR && "mooshroom".equals(p.subject()) && (target == null || !target.isAlive())) {
            ItemStack held = player.getItemInHand(p.hand());
            if (held.isEmpty() || held.getDamageValue() > p.before()) {
                pay(player, Job.SHEAR, player, null, SOURCE_SHEAR);
            }
            return;
        }
        if (!(target instanceof Animal animal) || !animal.isAlive()) {
            return;
        }
        switch (p.job()) {
            case FEED -> {
                // A feeding that grew the baby: the age moved up by more than the tick that passed.
                if (animal.getAge() > p.before() + 40) {
                    pay(player, Job.FEED, animal, p.subject(), SOURCE_FEED);
                }
            }
            case SHEAR -> {
                if (animal instanceof Shearable shearable && !shearable.readyForShearing()
                        && pay(player, Job.SHEAR, animal, null, SOURCE_SHEAR) > 0f) {
                    extraWool(player, animal);
                }
            }
            case MILK -> {
                if (player.getInventory().countItem(Items.MILK_BUCKET) > p.before()
                        && pay(player, Job.MILK, animal, p.subject(), SOURCE_MILK) > 0f) {
                    freshPail(player);
                }
            }
            case BRUSH -> {
                ItemStack held = player.getItemInHand(p.hand());
                if (held.isEmpty() || held.getDamageValue() > p.before()) {
                    pay(player, Job.BRUSH, animal, p.subject(), SOURCE_BRUSH);
                }
            }
            default -> {
            }
        }
    }

    private static void extraWool(ServerPlayer player, Animal animal) {
        int extra = HusbandryMath.extraWool(rank(player, "shear_more"));
        if (extra <= 0 || !(animal instanceof Sheep sheep)) {
            return;
        }
        DyeColor color = sheep.getColor();
        Item wool = BuiltInRegistries.ITEM.get(new ResourceLocation(color.getName() + "_wool"));
        if (wool == Items.AIR) {
            return;
        }
        ItemEntity drop = new ItemEntity(animal.level(), animal.getX(), animal.getY(1.0), animal.getZ(),
                new ItemStack(wool, extra));
        animal.level().addFreshEntity(drop);
    }

    /** A second bucket of milk, from a spare empty bucket: never a bucket from nothing. */
    private static void freshPail(ServerPlayer player) {
        double chance = HusbandryMath.pailChance(rank(player, "fresh_pail"));
        if (chance <= 0 || player.getRandom().nextDouble() >= chance) {
            return;
        }
        int slot = player.getInventory().findSlotMatchingItem(new ItemStack(Items.BUCKET));
        if (slot < 0) {
            return;
        }
        player.getInventory().getItem(slot).shrink(1);
        player.getInventory().placeItemBackInInventory(new ItemStack(Items.MILK_BUCKET));
    }

    // ---- Pets ----------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player.level().isClientSide()
                || player.tickCount % 100 != 7) {
            return;
        }
        double bonus = ProficiencyAttachments.of(player).bonus(Skill.HUSBANDRY);
        double amount = HusbandryMath.petHealthMultiplier(bonus) - 1.0;
        AABB box = player.getBoundingBox().inflate(32.0);
        for (LivingEntity pet : player.level().getEntitiesOfClass(LivingEntity.class, box,
                e -> isPet(e) && player.getUUID().equals(owner(e)))) {
            applyPetHealth(pet, amount);
        }
    }

    private static boolean isPet(Entity e) {
        return e instanceof TamableAnimal || e instanceof AbstractHorse;
    }

    /** Sets the pet's Husbandry health modifier; a pet that gains max health gains that much health. */
    static void applyPetHealth(LivingEntity pet, double amount) {
        AttributeInstance health = pet.getAttribute(Attributes.MAX_HEALTH);
        if (health == null) {
            return;
        }
        AttributeModifier existing = health.getModifier(Attr.uuid(PET_HEALTH));
        if (existing != null && Math.abs(existing.getAmount() - amount) < 1e-6) {
            return;
        }
        double before = pet.getMaxHealth();
        health.removeModifier(Attr.uuid(PET_HEALTH));
        if (amount > 0) {
            Attr.setPermanent(health, Attr.mod(PET_HEALTH, amount, AttributeModifier.Operation.MULTIPLY_BASE));
        }
        double gained = pet.getMaxHealth() - before;
        if (gained > 0) {
            pet.heal((float) gained);
        } else if (pet.getHealth() > pet.getMaxHealth()) {
            pet.setHealth(pet.getMaxHealth());
        }
    }

    /**
     * The pet side of damage: a wolf bites with a share of its owner's weapon training (Apprentice
     * Wolf), a pet hits harder on a mob its owner just hit (Shared Strength), and a pet that would
     * die inside a lit Hearth's range is saved (Faithful Friend).
     */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide() || !(victim.level() instanceof ServerLevel level)) {
            return;
        }
        DamageSource source = event.getSource();
        Entity attacker = source.getEntity();
        long now = level.getGameTime();

        if (attacker instanceof Player hitter && !(victim instanceof Player)) {
            HIT_BY.put(victim.getUUID(), new long[] {now});
            HIT_PLAYER.put(victim.getUUID(), hitter.getUUID());
            if (HIT_BY.size() > 2048) {
                HIT_BY.entrySet().removeIf(e -> now - e.getValue()[0] > HusbandryMath.SHARED_WINDOW_TICKS);
                HIT_PLAYER.keySet().removeIf(id -> !HIT_BY.containsKey(id));
            }
        }
        if (attacker != null && isPet(attacker) && owner(attacker) != null
                && level.getPlayerByUUID(owner(attacker)) instanceof ServerPlayer ownerPlayer) {
            double multiplier = 1.0;
            if (attacker instanceof Wolf) {
                multiplier *= HusbandryMath.wolfLearns(bestWeaponBonus(ownerPlayer), rank(ownerPlayer, "wolf_learns"));
            }
            int shared = rank(ownerPlayer, "shared_strength");
            long[] hit = HIT_BY.get(victim.getUUID());
            if (shared > 0 && hit != null && ownerPlayer.getUUID().equals(HIT_PLAYER.get(victim.getUUID()))
                    && now - hit[0] <= HusbandryMath.SHARED_WINDOW_TICKS) {
                multiplier *= HusbandryMath.sharedStrength(shared);
            }
            if (multiplier != 1.0) {
                event.setAmount((float) (event.getAmount() * multiplier));
            }
        }
    }

    /** Faithful Friend runs on the final, mitigated amount: only a hit that would really kill is cut. */
    @SubscribeEvent
    public static void onPetDamagePre(LivingDamageEvent.Pre event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide() || !(victim.level() instanceof ServerLevel level)) {
            return;
        }
        if (isPet(victim) && owner(victim) != null
                && level.getPlayerByUUID(owner(victim)) instanceof ServerPlayer ownerPlayer
                && event.getNewDamage() >= victim.getHealth()) {
            faithful(event, victim, ownerPlayer, level, level.getGameTime());
        }
    }

    private static double bestWeaponBonus(ServerPlayer owner) {
        var skills = ProficiencyAttachments.of(owner);
        double best = 0.0;
        for (Skill skill : new Skill[] {Skill.SWORDS, Skill.AXES, Skill.MACES, Skill.TRIDENTS, Skill.UNARMED}) {
            best = Math.max(best, skills.bonus(skill));
        }
        return best;
    }

    private static void faithful(LivingDamageEvent.Pre event, LivingEntity pet, ServerPlayer owner,
            ServerLevel level, long now) {
        if (rank(owner, "faithful_friend") <= 0 || !counts(owner)) {
            return;
        }
        Long used = FAITHFUL_USED.get(owner.getUUID());
        if (!HusbandryMath.faithfulReady(used == null ? -1L : used, now)) {
            return;
        }
        var hearth = HearthRange.hearthEntityAt(level, pet.blockPosition());
        if (hearth == null || hearth.state().smouldering()) {
            return;
        }
        FAITHFUL_USED.put(owner.getUUID(), now);
        float keep = Math.max(1.0f, (float) (pet.getMaxHealth() * HusbandryMath.FAITHFUL_HEALTH));
        event.setNewDamage(Math.max(0f, pet.getHealth() - keep));
        if (pet.getHealth() < keep) {
            pet.setHealth(keep);
        }
        level.sendParticles(ParticleTypes.HEART, pet.getX(), pet.getY(1.0), pet.getZ(), 6, 0.4, 0.4, 0.4, 0.0);
        level.playSound(null, pet.getX(), pet.getY(), pet.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.NEUTRAL, 1.0f, 1.2f);
        owner.displayClientMessage(Component.translatable("proficiency.husbandry.faithful", pet.getDisplayName()), true);
    }

    // ---- Pasture Blessing ----------------------------------------------------------------------

    /**
     * The ability: for its duration every pet of yours within 24 blocks regenerates (and, with Herd
     * Master, runs faster). The "Twins on every breeding" half is the usual frenzy.
     */
    public static void blessing(ServerPlayer player, int ticks) {
        AABB box = player.getBoundingBox().inflate(HusbandryMath.BLESSING_RADIUS);
        boolean speed = rank(player, "herd_master") > 0;
        for (LivingEntity pet : player.level().getEntitiesOfClass(LivingEntity.class, box,
                e -> isPet(e) && player.getUUID().equals(owner(e)))) {
            pet.addEffect(new MobEffectInstance(MobEffects.REGENERATION, ticks, 0));
            if (speed) {
                pet.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, 0));
            }
            if (player.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pet.getX(), pet.getY(1.0), pet.getZ(),
                        6, 0.4, 0.4, 0.4, 0.0);
            }
        }
    }

    // ---- For tests -----------------------------------------------------------------------------

    /** Forgets one player's remembered payouts, and every cooldown (a test starts clean). */
    public static void forget(UUID player) {
        SPOTS.remove(player);
        TAMES.remove(player);
        FAITHFUL_USED.remove(player);
        LAST_PAID.clear();
        HIT_BY.clear();
        HIT_PLAYER.clear();
        synchronized (PENDING) {
            PENDING.removeIf(p -> p.player().equals(player));
        }
        synchronized (WATCHES) {
            WATCHES.removeIf(w -> w.player().equals(player));
        }
    }

}
