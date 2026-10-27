package dev.amman.proficiency.event;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.config.ProficiencyConfig;
import dev.amman.proficiency.perk.TalentService;
import dev.amman.proficiency.platform.EntityData;
import dev.amman.proficiency.platform.bus.EventBusSubscriber;
import dev.amman.proficiency.platform.bus.SubscribeEvent;
import dev.amman.proficiency.platform.event.entity.EntityJoinLevelEvent;
import dev.amman.proficiency.platform.event.entity.living.LivingFallEvent;
import dev.amman.proficiency.platform.event.entity.living.LivingIncomingDamageEvent;
import dev.amman.proficiency.platform.event.entity.player.PlayerEvent;
import dev.amman.proficiency.platform.event.entity.player.PlayerInteractEvent;
import dev.amman.proficiency.platform.event.tick.PlayerTickEvent;
import dev.amman.proficiency.skill.ProcService;
import dev.amman.proficiency.skill.RidingMath;
import dev.amman.proficiency.skill.RidingMath.Kind;
import dev.amman.proficiency.skill.RidingTracker;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ItemSteerable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Riding (the 37th skill): metres ridden on a mount, in a boat, in a minecart, or under elytra. It
 * owns the XP, the passive (a faster, higher-jumping mount; a sparing elytra), Tailwind and its
 * tree. The numbers are in {@link RidingMath}, the anti-farm state in {@link RidingTracker}.
 *
 * <p>Running, Sneaking, Swimming and Jumping already ignore a player who is a passenger (and any
 * tick that moves more than 10 blocks); this skill pays for exactly that gap. Anti-farm, all in
 * the tracker: a boat or a steered mount pays only while you steer it, a leashed mount pays
 * nothing, a 10 block step (portal, teleport, the Whistle) is skipped, a ride that gets nowhere
 * pays nothing (net displacement) and ground you rode in the last 10 minutes pays nothing. Only
 * the one who steers is paid, only in survival, and a fake player is paid nothing by
 * {@link SkillService}.
 *
 * <p>The passive is a transient attribute modifier on the mount while you steer it, never saved,
 * and removed the moment you get off.
 */
@EventBusSubscriber(modid = Proficiency.MOD_ID)
public final class RidingEvents {

    public static final String SOURCE_MOUNT = "proficiency.xplog.source.riding_mount";
    public static final String SOURCE_BOAT = "proficiency.xplog.source.riding_boat";
    public static final String SOURCE_MINECART = "proficiency.xplog.source.riding_minecart";
    public static final String SOURCE_ELYTRA = "proficiency.xplog.source.riding_elytra";

    private static final ResourceLocation MOUNT_SPEED = Proficiency.id("riding_speed");
    private static final ResourceLocation MOUNT_JUMP = Proficiency.id("riding_jump");
    private static final ResourceLocation SEAT_REACH = Proficiency.id("riding_high_seat");

    /** On an arrow: it was loosed from a mount (Saddle Archer). */
    private static final String MOUNTED_SHOT = "proficiency_riding_mounted_shot";

    /** One player's riding: the tracker and everything the tree remembers between ticks. */
    private static final class Ride {
        final RidingTracker tracker = new RidingTracker();
        boolean paying;
        UUID vehicleId;
        UUID mountId;
        /** The mount that carries our modifiers: held directly, so a clear never depends on finding it in a level. */
        WeakReference<LivingEntity> mountRef;
        long mountCheckAt = -100L;
        /** The vehicle's position at the last tick: a client-steered vehicle's own xo is not reliable. */
        UUID motionId;
        long motionAt = -1L;
        double motionX;
        double motionZ;
        double speed;
        double headX;
        double headZ;
        float lastYaw = Float.NaN;
        boolean looked;
        String rateId;
        double rate;
        long rateAt = -1000L;
        ItemStack elytraStack;
        long hurtAt = -100L;
        final Set<String> firsts = new HashSet<>();
        long softStart = -1L;
        long softLast = -1L;
        int elytraDamage = -1;
        double altitude = RidingMath.HIGH_ALTITUDE;
        long altitudeAt = -100L;
        final Map<UUID, Long> trampled = new HashMap<>();
        long whistleAt = -1L;
        InteractionHand rocketHand;
        ItemStack rocketCopy;
        int rocketBefore;
        long rocketDue;
    }

    private static final Map<UUID, Ride> RIDES = new ConcurrentHashMap<>();

    private RidingEvents() {
    }

    // ---- Who counts ----------------------------------------------------------------------------

    private static boolean counts(ServerPlayer player) {
        // Read from the game mode itself: the GameTest mock claims creative.
        return player.gameMode.isSurvival() && ProficiencyConfig.enabled(Skill.RIDING);
    }

    private static int rank(Player player, String special) {
        return TalentService.rank(player, Skill.RIDING, special);
    }

    private static Ride ride(Player player) {
        return RIDES.computeIfAbsent(player.getUUID(), id -> new Ride());
    }

    /**
     * Blocks per tick the vehicle moved between this player's last two ticks. The vehicle's own
     * {@code xo} is the position before ITS last tick, which for a mount the client steers is already
     * the current one, so the speed is taken from the rider's tick instead.
     */
    private static void trackMotion(ServerPlayer player, Ride ride, Entity vehicle, long now) {
        float yaw = player.getYRot();
        ride.looked = !Float.isNaN(ride.lastYaw) && Math.abs(Mth.wrapDegrees(yaw - ride.lastYaw)) > 0.5f;
        ride.lastYaw = yaw;
        if (vehicle == null) {
            ride.motionId = null;
            ride.speed = 0.0;
            ride.headX = 0.0;
            ride.headZ = 0.0;
            return;
        }
        UUID id = vehicle.getUUID();
        if (id.equals(ride.motionId) && now >= ride.motionAt && now - ride.motionAt <= 2L) {
            double dt = Math.max(1L, now - ride.motionAt);
            ride.headX = (vehicle.getX() - ride.motionX) / dt;
            ride.headZ = (vehicle.getZ() - ride.motionZ) / dt;
            ride.speed = Math.sqrt(ride.headX * ride.headX + ride.headZ * ride.headZ);
        } else {
            ride.speed = 0.0;
            ride.headX = 0.0;
            ride.headZ = 0.0;
        }
        ride.motionId = id;
        ride.motionAt = now;
        ride.motionX = vehicle.getX();
        ride.motionZ = vehicle.getZ();
    }

    /** The living mount a player really steers, or null (a passenger, a boat, a minecart, a player). */
    private static LivingEntity steeredMount(Player player) {
        return player.getControlledVehicle() instanceof LivingEntity mount && !(mount instanceof Player) ? mount : null;
    }

    // ---- The tick ------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide()
                || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        Ride ride = ride(player);
        long now = level.getGameTime();
        Entity vehicle = player.getVehicle();
        UUID vehicleId = vehicle == null ? null : vehicle.getUUID();

        // Getting off: Soft Landing starts, the mount loses the passive.
        if (ride.vehicleId != null && !ride.vehicleId.equals(vehicleId)) {
            dismounted(player, ride, level, now);
        }
        ride.vehicleId = vehicleId;
        trackMotion(player, ride, vehicle, now);

        boolean on = counts(player);
        applyMount(player, ride, level, vehicle, on);
        applySeat(player, on);
        finishRocket(player, ride, now);

        boolean gliding = on && vehicle == null && player.isFallFlying()
                && player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA);
        if (gliding) {
            spareElytra(player, ride, now);
        } else {
            ride.elytraDamage = -1;
            ride.elytraStack = null;
        }
        if (!on) {
            endPaying(ride);
            return;
        }
        pay(player, ride, level, vehicle, gliding, now);
        LivingEntity steered = steeredMount(player);
        if (steered != null && player.tickCount % 2 == 0 && rank(player, "trample") > 0) {
            trample(player, steered, ride, level, now);
        }
    }

    private static void endPaying(Ride ride) {
        if (ride.paying) {
            ride.paying = false;
            ride.tracker.endRide();
        }
    }

    // ---- Paying --------------------------------------------------------------------------------

    private static void pay(ServerPlayer player, Ride ride, ServerLevel level, Entity vehicle, boolean gliding,
            long now) {
        String id;
        Kind kind;
        boolean input;
        String context;
        String source;
        String firstKey;
        if (gliding) {
            id = RidingMath.RATE_ELYTRA;
            kind = Kind.ELYTRA;
            input = true;
            context = level.dimension().location() + "|elytra";
            source = SOURCE_ELYTRA;
            firstKey = "item.minecraft.elytra";
        } else if (vehicle != null && !(vehicle instanceof Player)) {
            id = BuiltInRegistries.ENTITY_TYPE.getKey(vehicle.getType()).toString();
            kind = RidingMath.kindOf(id);
            if (kind.needsInput() && player.getControlledVehicle() != vehicle) {
                // A passenger, or a mount nobody steers (an unsaddled one, a pig without its stick).
                endPaying(ride);
                return;
            }
            if (vehicle instanceof Mob mob && mob.isLeashed()) {
                // Dragged along on a lead: not a ride.
                endPaying(ride);
                return;
            }
            input = steering(player, ride, vehicle);
            context = level.dimension().location() + "|" + vehicle.getUUID();
            source = switch (kind) {
                case BOAT -> SOURCE_BOAT;
                case MINECART -> SOURCE_MINECART;
                default -> SOURCE_MOUNT;
            };
            firstKey = vehicle.getType().getDescriptionId();
        } else {
            endPaying(ride);
            return;
        }
        if (!id.equals(ride.rateId) || now - ride.rateAt >= 100L || now < ride.rateAt) {
            ride.rateId = id;
            ride.rateAt = now;
            ride.rate = RidingMath.rate(ProficiencyConfig.ridingRates(), id);
        }
        double rate = ride.rate;
        ride.paying = true;
        RidingTracker.Result step = ride.tracker.step(now, context, player.getX(), player.getY(), player.getZ(),
                kind.needsInput(), input);
        if (step.reason() != RidingTracker.Reason.PAID || !(rate > 0)) {
            return;
        }
        double base = step.metres() * rate;
        if (gliding) {
            base *= RidingMath.elytraFactor(altitude(player, ride, level, now), step.metres() * 20.0);
        }
        double pile = ride.tracker.add(base);
        if (pile <= 0) {
            return;
        }
        float paid = SkillService.grant(player, Skill.RIDING, pile, source);
        if (paid <= 0f) {
            return;
        }
        if (ride.firsts.add(firstKey)) {
            // The first ride on each kind of mount: the usual first-time bonus and banner.
            SkillService.grantFirstTimeOnly(player, Skill.RIDING, firstKey);
        }
        for (int roll = ride.tracker.procRolls(pile); roll > 0; roll--) {
            if (ProcService.fire(player, Skill.RIDING)) {
                tailwind(player, vehicle, gliding);
            }
        }
    }

    /**
     * Whether the player is steering. A mount or a boat wants a key held (forward, back or sideways);
     * a pig or a strider goes forward on its own once you hold its stick, and is steered with the
     * look, so those want the view to turn (half a degree in a tick) or a key: holding the stick
     * still on a straight road is not riding.
     */
    private static boolean steering(ServerPlayer player, Ride ride, Entity vehicle) {
        boolean keys = player.zza != 0f || player.xxa != 0f;
        if (vehicle instanceof ItemSteerable) {
            return player.getControlledVehicle() == vehicle && (keys || ride.looked);
        }
        return keys;
    }

    /** Blocks of air under the player, looked at twice a second. */
    private static double altitude(ServerPlayer player, Ride ride, ServerLevel level, long now) {
        if (now - ride.altitudeAt < 10L) {
            return ride.altitude;
        }
        ride.altitudeAt = now;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(player.getBlockX(), player.getBlockY(),
                player.getBlockZ());
        double found = RidingMath.HIGH_ALTITUDE;
        int floor = level.getMinBuildHeight();
        for (int down = 0; down <= (int) RidingMath.HIGH_ALTITUDE && pos.getY() >= floor; down++) {
            BlockState state = level.getBlockState(pos);
            if (!state.getCollisionShape(level, pos).isEmpty() || !state.getFluidState().isEmpty()) {
                found = player.getY() - (pos.getY() + 1.0);
                break;
            }
            pos.move(0, -1, 0);
        }
        ride.altitude = Math.max(0.0, found);
        return ride.altitude;
    }

    // ---- Tailwind ------------------------------------------------------------------------------

    /**
     * Tailwind: a mount gets Speed II and Jump Boost II, elytra push the player along their look,
     * and in a boat or a minecart the ride
     * regenerates the rider a little (it feeds nobody).
     */
    public static void tailwind(ServerPlayer player, Entity vehicle, boolean gliding) {
        double power = ProcService.power(player, Skill.RIDING);
        if (gliding) {
            Vec3 push = player.getLookAngle().scale(RidingMath.tailwindPush(power));
            player.setDeltaMovement(player.getDeltaMovement().add(push));
            player.hurtMarked = true;
            return;
        }
        if (vehicle instanceof LivingEntity mount && !(mount instanceof Player)) {
            int ticks = RidingMath.tailwindTicks(power);
            mount.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, 1, false, false, true));
            mount.addEffect(new MobEffectInstance(MobEffects.JUMP, ticks, 1, false, false, true));
            return;
        }
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, RidingMath.TAILWIND_REFRESH_TICKS, 0));
    }

    /**
     * Full Gallop (the ability): for its duration the mount you steer, and every tamed horse of yours
     * within 24 blocks, runs at Speed II and jumps with Jump Boost II. The "Tailwind every time" half
     * is the usual frenzy.
     */
    public static void gallop(ServerPlayer player, int ticks) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        Set<LivingEntity> mounts = new HashSet<>();
        if (player.getVehicle() instanceof LivingEntity mount && !(mount instanceof Player)) {
            mounts.add(mount);
        }
        AABB box = player.getBoundingBox().inflate(RidingMath.GALLOP_RADIUS);
        mounts.addAll(level.getEntitiesOfClass(AbstractHorse.class, box,
                horse -> horse.isTamed() && player.getUUID().equals(horse.getOwnerUUID())));
        for (LivingEntity mount : mounts) {
            mount.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, 1, false, false, true));
            mount.addEffect(new MobEffectInstance(MobEffects.JUMP, ticks, 1, false, false, true));
            level.sendParticles(ParticleTypes.CLOUD, mount.getX(), mount.getY() + 0.2, mount.getZ(), 8, 0.4, 0.1, 0.4,
                    0.02);
        }
    }

    // ---- The passive ---------------------------------------------------------------------------

    /** Sets the speed and jump modifiers on the mount the player steers, and clears the one it left. */
    private static void applyMount(ServerPlayer player, Ride ride, ServerLevel level, Entity vehicle, boolean on) {
        LivingEntity mount = on ? steeredMount(player) : null;
        UUID now = mount == null ? null : mount.getUUID();
        if (ride.mountId != null && !ride.mountId.equals(now)) {
            clearHeld(ride);
        }
        ride.mountId = now;
        if (mount == null) {
            return;
        }
        long time = level.getGameTime();
        if (ride.mountRef == null || ride.mountRef.get() != mount) {
            ride.mountRef = new WeakReference<>(mount);
            ride.mountCheckAt = -100L;
        }
        // Once a half second is plenty: the level only moves slowly, and a level-0 rider has no modifier to find.
        if (time - ride.mountCheckAt < 10L && time >= ride.mountCheckAt) {
            return;
        }
        ride.mountCheckAt = time;
        double bonus = SkillService.bonus(player, Skill.RIDING);
        set(mount.getAttribute(Attributes.MOVEMENT_SPEED), MOUNT_SPEED, RidingMath.speedMultiplier(bonus) - 1.0);
        set(mount.getAttribute(Attributes.JUMP_STRENGTH), MOUNT_JUMP, RidingMath.jumpMultiplier(bonus) - 1.0);
    }

    /** Takes the modifiers off the mount this ride holds, wherever it is now (another level, unloaded, dead). */
    private static void clearHeld(Ride ride) {
        LivingEntity held = ride.mountRef == null ? null : ride.mountRef.get();
        if (held != null) {
            clear(held);
        }
        ride.mountRef = null;
        ride.mountId = null;
        ride.mountCheckAt = -100L;
    }

    /** Takes the Riding modifiers off a mount. */
    public static void clear(LivingEntity mount) {
        set(mount.getAttribute(Attributes.MOVEMENT_SPEED), MOUNT_SPEED, 0.0);
        set(mount.getAttribute(Attributes.JUMP_STRENGTH), MOUNT_JUMP, 0.0);
    }

    private static void set(AttributeInstance attribute, ResourceLocation id, double amount) {
        set(attribute, id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    private static void set(AttributeInstance attribute, ResourceLocation id, double amount,
            AttributeModifier.Operation operation) {
        if (attribute == null) {
            return;
        }
        AttributeModifier existing = attribute.getModifier(id);
        if (!(amount > 0)) {
            if (existing != null) {
                attribute.removeModifier(id);
            }
            return;
        }
        if (existing != null && existing.amount() == amount && existing.operation() == operation) {
            return;
        }
        attribute.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
    }

    /** High Seat: while mounted, block and attack reach grow. */
    private static void applySeat(ServerPlayer player, boolean on) {
        double reach = on && steeredMount(player) != null ? RidingMath.seatReach(rank(player, "high_seat")) : 0.0;
        set(player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE), SEAT_REACH, reach,
                AttributeModifier.Operation.ADD_VALUE);
        set(player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE), SEAT_REACH, reach,
                AttributeModifier.Operation.ADD_VALUE);
    }

    /**
     * The elytra passive: a wear point the glide just took is given back with the spared chance.
     * There is no event for an item losing durability, so the damage is watched from tick to tick.
     */
    private static void spareElytra(ServerPlayer player, Ride ride, long now) {
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (chest != ride.elytraStack) {
            // A different elytra (or a re-equipped one): its damage is a new baseline, never a repair to credit.
            ride.elytraStack = chest;
            ride.elytraDamage = -1;
        }
        int damage = chest.getDamageValue();
        // Only the glide's own wear is given back: a hit that wore the elytra a moment ago is not.
        if (ride.elytraDamage >= 0 && damage == ride.elytraDamage + 1 && now - ride.hurtAt > 1L) {
            int kept = RidingMath.elytraWear(ride.elytraDamage, damage, SkillService.bonus(player, Skill.RIDING),
                    player.getRandom().nextDouble());
            if (kept != damage) {
                chest.setDamageValue(kept);
                damage = kept;
            }
        }
        ride.elytraDamage = damage;
    }

    // ---- Getting off ---------------------------------------------------------------------------

    private static void dismounted(ServerPlayer player, Ride ride, ServerLevel level, long now) {
        clearHeld(ride);
        endPaying(ride);
        if (rank(player, "soft_landing") > 0 && counts(player) && RidingMath.softLandingReady(ride.softLast, now)) {
            ride.softStart = now;
            ride.softLast = now;
        }
    }

    /** Soft Landing: no fall damage for 4 seconds after you leave a mount, a boat or a minecart. */
    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide()) {
            return;
        }
        Ride ride = RIDES.get(player.getUUID());
        if (ride != null && rank(player, "soft_landing") > 0
                && RidingMath.softLandingActive(ride.softStart, player.level().getGameTime())) {
            event.setCanceled(true);
        }
    }

    // ---- Damage --------------------------------------------------------------------------------

    /** Couched Lance, Saddle Archer and Barding all change a hit; they are settled here in one place. */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) {
            return;
        }
        DamageSource source = event.getSource();
        Entity attacker = source.getEntity();
        double multiplier = 1.0;

        if (victim instanceof ServerPlayer hurt) {
            // Remembered for the elytra watch: a hit that wears the elytra is not glide wear.
            ride(hurt).hurtAt = victim.level().getGameTime();
        }
        // Barding: a mount its rider steers takes less (never from the rider).
        if (!(victim instanceof Player) && victim.getControllingPassenger() instanceof ServerPlayer rider
                && rider != attacker && counts(rider) && steeredMount(rider) == victim) {
            multiplier *= RidingMath.bardingMultiplier(rank(rider, "barding"));
        }
        // Only a rider who steers a living mount rides into battle: not a passenger, not a boat or a minecart.
        if (attacker instanceof ServerPlayer hitter && counts(hitter) && steeredMount(hitter) != null
                && victim != hitter.getVehicle()) {
            Entity direct = source.getDirectEntity();
            if (direct == hitter) {
                int lance = rank(hitter, "mounted_lance");
                if (lance > 0) {
                    multiplier *= RidingMath.lanceMultiplier(lance, ride(hitter).speed);
                }
            }
        }
        if (attacker instanceof ServerPlayer shooter && source.getDirectEntity() instanceof AbstractArrow arrow
                && EntityData.of(arrow).getBoolean(MOUNTED_SHOT)) {
            multiplier *= RidingMath.archerMultiplier(rank(shooter, "mounted_shot"));
        }
        if (multiplier != 1.0) {
            event.setAmount((float) (event.getAmount() * multiplier));
        }
    }

    /** Saddle Archer has to know the arrow left a mount: by the time it lands the rider may be off. */
    @SubscribeEvent
    public static void onArrowLoosed(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || event.loadedFromDisk()
                || !(event.getEntity() instanceof AbstractArrow arrow)
                || !(arrow.getOwner() instanceof ServerPlayer player) || steeredMount(player) == null
                || !counts(player)) {
            return;
        }
        if (rank(player, "mounted_shot") > 0) {
            EntityData.of(arrow).putBoolean(MOUNTED_SHOT, true);
        }
    }

    // ---- Stampede ------------------------------------------------------------------------------

    /** The capstone: a mount at a full gallop tramples the hostile mobs in front of it. Nobody earns XP for it. */
    private static void trample(ServerPlayer player, LivingEntity mount, Ride ride, ServerLevel level, long now) {
        Vec3 heading = new Vec3(ride.headX, 0.0, ride.headZ);
        if (heading.length() < RidingMath.TRAMPLE_SPEED) {
            return;
        }
        Vec3 dir = heading.normalize();
        AABB box = mount.getBoundingBox().inflate(0.3).move(dir.x * 0.8, 0.0, dir.z * 0.8);
        if (ride.trampled.size() > 64) {
            ride.trampled.values().removeIf(t -> now - t > RidingMath.TRAMPLE_COOLDOWN * 5);
        }
        double damage = RidingMath.trampleDamage(ProficiencyAttachments.of(player).level(Skill.RIDING));
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e instanceof Enemy && e != mount && e.isAlive() && !mount.hasPassenger(e))) {
            Long last = ride.trampled.get(target.getUUID());
            if (last != null && now >= last && now - last < RidingMath.TRAMPLE_COOLDOWN) {
                continue;
            }
            ride.trampled.put(target.getUUID(), now);
            if (target.hurt(level.damageSources().mobAttack(mount), (float) damage)) {
                target.knockback(0.6, -dir.x, -dir.z);
                level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.5), target.getZ(), 6, 0.3, 0.3,
                        0.3, 0.2);
            }
        }
    }

    // ---- Items: the Whistle and Rocket Saver ---------------------------------------------------

    @SubscribeEvent
    public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)
                || player.level().isClientSide() || !counts(player)) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (stack.is(Items.GOAT_HORN)) {
            whistle(player);
        } else if (stack.is(Items.FIREWORK_ROCKET) && player.isFallFlying()) {
            rocket(player, event.getHand(), stack);
        }
    }

    /** The Whistle: a goat horn calls the nearest tamed horse of yours that is free to come. */
    private static void whistle(ServerPlayer player) {
        int rank = rank(player, "call_mount");
        if (rank <= 0 || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        Ride ride = ride(player);
        long now = level.getGameTime();
        if (!RidingMath.whistleReady(ride.whistleAt, now, rank)) {
            long left = RidingMath.whistleCooldown(rank) - (now - ride.whistleAt);
            player.displayClientMessage(Component.translatable("proficiency.riding.rest", Math.max(1, left / 20)), true);
            return;
        }
        if (player.isFallFlying()) {
            player.displayClientMessage(Component.translatable("proficiency.riding.no_room"), true);
            return;
        }
        double range = RidingMath.whistleRange(rank);
        AbstractHorse best = null;
        double bestDistance = Double.MAX_VALUE;
        for (AbstractHorse horse : level.getEntitiesOfClass(AbstractHorse.class, player.getBoundingBox().inflate(range),
                h -> h.isAlive() && h.isTamed() && player.getUUID().equals(h.getOwnerUUID()) && !h.isVehicle()
                        && !h.isLeashed() && !(h instanceof Llama))) {
            double distance = horse.distanceTo(player);
            if (distance <= range && distance < bestDistance) {
                best = horse;
                bestDistance = distance;
            }
        }
        if (best == null) {
            player.displayClientMessage(Component.translatable("proficiency.riding.no_mount"), true);
            return;
        }
        Vec3 spot = safeSpot(level, best, player);
        if (spot == null) {
            // A tunnel, a wall, a ledge: nowhere the horse would not be stuck in stone or dropped in a hole.
            player.displayClientMessage(Component.translatable("proficiency.riding.no_room"), true);
            return;
        }
        ride.whistleAt = now;
        level.sendParticles(ParticleTypes.CLOUD, best.getX(), best.getY(0.5), best.getZ(), 10, 0.4, 0.4, 0.4, 0.02);
        best.getNavigation().stop();
        best.fallDistance = 0.0f;
        best.moveTo(spot.x, spot.y, spot.z, player.getYRot(), 0.0f);
        level.sendParticles(ParticleTypes.CLOUD, best.getX(), best.getY(0.5), best.getZ(), 10, 0.4, 0.4, 0.4, 0.02);
        level.playSound(null, best.getX(), best.getY(), best.getZ(), SoundEvents.HORSE_ANGRY, SoundSource.NEUTRAL,
                0.8f, 1.2f);
        player.displayClientMessage(Component.translatable("proficiency.riding.call", best.getDisplayName()), true);
    }

    /**
     * Where the called horse can stand: in front of the player first, then beside and behind, at two
     * distances. The spot must be clear of blocks, entities and liquid and have ground within a block
     * below. Null when there is none, and then the call is refused.
     */
    private static Vec3 safeSpot(ServerLevel level, AbstractHorse horse, ServerPlayer player) {
        double yaw = Math.toRadians(player.getYRot());
        double fx = -Math.sin(yaw);
        double fz = Math.cos(yaw);
        double[][] turns = {{1, 0}, {0, 1}, {0, -1}, {-1, 0}};
        for (double distance : new double[] {1.8, 2.6}) {
            for (double[] turn : turns) {
                double dx = (fx * turn[0] - fz * turn[1]) * distance;
                double dz = (fz * turn[0] + fx * turn[1]) * distance;
                Vec3 at = new Vec3(player.getX() + dx, player.getY(), player.getZ() + dz);
                AABB box = horse.getBoundingBox().move(at.subtract(horse.position()));
                if (level.noCollision(horse, box) && !level.containsAnyLiquid(box)
                        && !level.noCollision(horse, box.move(0.0, -1.0, 0.0))) {
                    return at;
                }
            }
        }
        return null;
    }

    /** Rocket Saver: the rocket the vanilla use is about to spend may come back a tick later. */
    private static void rocket(ServerPlayer player, InteractionHand hand, ItemStack stack) {
        double chance = RidingMath.rocketSaveChance(rank(player, "rocket_saver"));
        Ride ride = ride(player);
        if (chance <= 0 || ride.rocketCopy != null || player.getRandom().nextDouble() >= chance) {
            return;
        }
        ride.rocketHand = hand;
        ride.rocketBefore = stack.getCount();
        ride.rocketCopy = stack.copyWithCount(1);
        ride.rocketDue = player.level().getGameTime() + 1;
    }

    private static void finishRocket(ServerPlayer player, Ride ride, long now) {
        if (ride.rocketCopy == null || now < ride.rocketDue) {
            return;
        }
        ItemStack copy = ride.rocketCopy;
        ride.rocketCopy = null;
        ItemStack held = player.getItemInHand(ride.rocketHand);
        if (held.isEmpty() && ride.rocketBefore == 1) {
            player.setItemInHand(ride.rocketHand, copy);
        } else if (ItemStack.isSameItemSameComponents(held, copy) && held.getCount() == ride.rocketBefore - 1) {
            held.grow(1);
        } else {
            return;
        }
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.FIREWORK, player.getX(), player.getY(0.5), player.getZ(), 6, 0.3, 0.3,
                    0.3, 0.05);
        }
    }

    // ---- Housekeeping --------------------------------------------------------------------------

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Ride ride = RIDES.get(event.getEntity().getUUID());
        if (ride != null) {
            clearHeld(ride);
        }
        forget(event.getEntity().getUUID());
    }

    /** Forgets one player's riding (a logout, or a test starting clean). */
    public static void forget(UUID player) {
        RIDES.remove(player);
    }
}
