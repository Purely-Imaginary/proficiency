package dev.amman.proficiency.event;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.perk.TalentService;
import dev.amman.proficiency.skill.Skill;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import dev.amman.proficiency.platform.bus.EventPriority;
import dev.amman.proficiency.platform.bus.SubscribeEvent;
import dev.amman.proficiency.platform.bus.EventBusSubscriber;
import dev.amman.proficiency.platform.event.entity.living.LivingDamageEvent;
import dev.amman.proficiency.platform.event.entity.living.LivingDropsEvent;
import dev.amman.proficiency.platform.event.entity.living.LivingEvent;
import dev.amman.proficiency.platform.event.entity.living.LivingIncomingDamageEvent;
import dev.amman.proficiency.platform.event.entity.player.PlayerEvent;
import dev.amman.proficiency.platform.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The bespoke nodes of the Running, Sneaking, Jumping and Swimming trees. The per-metre XP and the
 * fall handler stay in {@link MovementEvents}; the fall talents are called from there so that all
 * of them see one fall in one fixed order.
 *
 * <p>Effects this class keeps up continuously (Speed, Invisibility, Night Vision, Dolphin's Grace,
 * Slow Falling) are short, refreshed on a throttle, and marked as ours by being ambient with no
 * particles. Only an effect carrying that mark is ever refreshed or removed here, so a potion the
 * player drank is never overwritten or stripped. A potion arriving on top of ours takes the
 * instance over (vanilla copies its ambient flag), which also takes it out of our hands.
 */
@EventBusSubscriber(modid = Proficiency.MOD_ID)
public final class TalentMovementEvents {

    /** How often the kept-up effects are refreshed and checked. Their durations are twice this. */
    private static final int REFRESH_TICKS = 10;
    private static final int KEPT_DURATION = 40;

    /**
     * Night Vision flickers on screen for its last 200 ticks ({@code GameRenderer
     * .getNightVisionScale}), so a short refreshed one would flicker forever. It is kept at 15
     * seconds instead, never drops under 200 between refreshes, and is removed outright on
     * surfacing rather than left to run out.
     */
    private static final int NIGHT_VISION_DURATION = 300;

    /** Skywalker's Slow Falling: long enough for any drop, taken away again on landing. */
    private static final int SLOW_FALL_DURATION = 200;

    private static final int NIGHTBLADE_STILL_TICKS = 60;
    private static final int WINDWALKER_SPRINT_TICKS = 160;
    private static final long ESCAPE_COOLDOWN = 1200;
    private static final long SKYWALKER_COOLDOWN = 600;
    private static final float SKYWALKER_FALL = 12.0f;
    private static final double SLIPSTREAM_RADIUS = 6.0;
    /** Leviathan Blood: one health every two seconds while hurt in water. */
    private static final int SEA_HEALING_TICKS = 40;
    private static final float SEA_HEALING_AMOUNT = 1.0f;

    static final float ZEPHYR_CAP = 4.0f;
    static final double GHOST_STEP_CHANCE = 0.15;
    /** "From behind" is within 60 degrees either side of straight behind the target. */
    static final double BEHIND_COS = -0.5;
    /** Bounce's upward speed: about two blocks, under the three a landing starts to hurt at. */
    static final double BOUNCE_SPEED = 0.6;

    /** A stomp hurts a mob with the player as its source, and must not stomp or backstab again. */
    private static final ThreadLocal<Boolean> STOMPING = ThreadLocal.withInitial(() -> false);

    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    private TalentMovementEvents() {
    }

    private static final class State {
        double lastX;
        double lastY;
        double lastZ;
        boolean seeded;
        int stillTicks;
        int sprintTicks;
        boolean shadowed;
        long escapeReadyAt;
        long skywalkerReadyAt;
    }

    // ---- The tick ---------------------------------------------------------------------------

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide() || !player.isAlive()) {
            return;
        }
        State state = STATES.computeIfAbsent(player.getUUID(), id -> new State());
        double dx = player.getX() - state.lastX;
        double dy = player.getY() - state.lastY;
        double dz = player.getZ() - state.lastZ;
        boolean moved = !state.seeded || dx * dx + dy * dy + dz * dz > 0.0004;
        state.lastX = player.getX();
        state.lastY = player.getY();
        state.lastZ = player.getZ();
        state.seeded = true;

        state.sprintTicks = player.isSprinting() ? state.sprintTicks + 1 : 0;
        boolean refresh = player.tickCount % REFRESH_TICKS == 0;

        nightblade(player, state, moved, refresh);
        skywalker(player, state);

        if (refresh) {
            if (state.sprintTicks >= WINDWALKER_SPRINT_TICKS
                    && TalentService.rank(player, Skill.RUNNING, "windwalker") > 0) {
                keep(player, MobEffects.MOVEMENT_SPEED, KEPT_DURATION);
            }
            swimming(player);
        }
        if (player.tickCount % 20 == 0 && player.isSprinting()
                && TalentService.rank(player, Skill.RUNNING, "slipstream") > 0) {
            slipstream(player);
        }
    }

    /** Nightblade: three seconds crouched and still, and you are gone until you move. */
    private static void nightblade(Player player, State state, boolean moved, boolean refresh) {
        if (TalentService.rank(player, Skill.SNEAKING, "still_shadow") <= 0) {
            if (state.shadowed) {
                drop(player, MobEffects.INVISIBILITY, KEPT_DURATION);
                state.shadowed = false;
            }
            state.stillTicks = 0;
            return;
        }
        if (moved) {
            state.stillTicks = 0;
            if (state.shadowed) {
                drop(player, MobEffects.INVISIBILITY, KEPT_DURATION);
                state.shadowed = false;
            }
            return;
        }
        if (player.isCrouching() && state.stillTicks < NIGHTBLADE_STILL_TICKS) {
            state.stillTicks++;
        }
        if (!state.shadowed && state.stillTicks >= NIGHTBLADE_STILL_TICKS) {
            state.shadowed = true;
            keep(player, MobEffects.INVISIBILITY, KEPT_DURATION);
        } else if (state.shadowed && refresh) {
            keep(player, MobEffects.INVISIBILITY, KEPT_DURATION);
        }
    }

    /**
     * Skywalker. Slow Falling resets fall distance every tick it runs, so the landing is free; it
     * is taken away on touching down so it does not float the next few jumps too.
     */
    private static void skywalker(Player player, State state) {
        if (player.onGround() || player.isInWater()) {
            drop(player, MobEffects.SLOW_FALLING, SLOW_FALL_DURATION);
            return;
        }
        if (player.fallDistance <= SKYWALKER_FALL || player.hasEffect(MobEffects.SLOW_FALLING)
                || player.getAbilities().flying || player.isFallFlying()) {
            return;
        }
        long now = player.level().getGameTime();
        if (now < state.skywalkerReadyAt
                || TalentService.rank(player, Skill.JUMPING, "auto_slow_fall") <= 0) {
            return;
        }
        state.skywalkerReadyAt = now + SKYWALKER_COOLDOWN;
        keep(player, MobEffects.SLOW_FALLING, SLOW_FALL_DURATION);
    }

    /** Aquatic Eyes, Tidewalker and Leviathan Blood. */
    private static void swimming(Player player) {
        if (TalentService.rank(player, Skill.SWIMMING, "aqua_vision") > 0
                && player.isEyeInFluid(FluidTags.WATER)) {
            keep(player, MobEffects.NIGHT_VISION, NIGHT_VISION_DURATION);
        } else {
            drop(player, MobEffects.NIGHT_VISION, NIGHT_VISION_DURATION);
        }
        if (!player.isInWater()) {
            return;
        }
        if (TalentService.rank(player, Skill.SWIMMING, "tidewalker") > 0) {
            keep(player, MobEffects.DOLPHINS_GRACE, KEPT_DURATION);
        }
        if (player.tickCount % SEA_HEALING_TICKS == 0
                && player.getHealth() < player.getMaxHealth()
                && TalentService.rank(player, Skill.SWIMMING, "sea_healing") > 0) {
            player.heal(SEA_HEALING_AMOUNT);
        }
    }

    /** Slipstream: everyone in your wake. Once a second, and the list is only nearby players. */
    private static void slipstream(Player player) {
        List<Player> near = player.level().getEntitiesOfClass(Player.class,
                player.getBoundingBox().inflate(SLIPSTREAM_RADIUS),
                other -> other != player && !other.isSpectator() && other.isAlive()
                        && other.distanceToSqr(player) <= SLIPSTREAM_RADIUS * SLIPSTREAM_RADIUS);
        for (Player other : near) {
            keep(other, MobEffects.MOVEMENT_SPEED, KEPT_DURATION);
        }
    }

    // ---- Effects we own ---------------------------------------------------------------------

    /** Ambient and particle-free is how this class marks an effect as its own. */
    private static boolean ours(MobEffectInstance effect, int maxDuration) {
        return effect.isAmbient() && !effect.isVisible() && effect.getDuration() <= maxDuration;
    }

    /** Adds or refreshes a level-one effect, unless the player already has one that is not ours. */
    private static void keep(Player player, Holder<MobEffect> effect, int duration) {
        MobEffectInstance existing = player.getEffect(effect);
        if (existing != null && !ours(existing, duration)) {
            return;
        }
        player.addEffect(new MobEffectInstance(effect, duration, 0, true, false, true));
    }

    private static void drop(Player player, Holder<MobEffect> effect, int maxDuration) {
        MobEffectInstance existing = player.getEffect(effect);
        if (existing != null && ours(existing, maxDuration)) {
            player.removeEffect(effect);
        }
    }

    // ---- Damage -----------------------------------------------------------------------------

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) {
            return;
        }
        DamageSource source = event.getSource();

        if (target instanceof Player victim) {
            // Fleetfoot: melee only, which means the thing that hit you is the thing that swung.
            Entity attacker = source.getEntity();
            if (victim.isSprinting() && attacker instanceof LivingEntity
                    && source.getDirectEntity() == attacker
                    && !source.is(DamageTypeTags.IS_PROJECTILE)
                    && !source.is(DamageTypeTags.IS_EXPLOSION)
                    && TalentService.rank(victim, Skill.RUNNING, "ghost_step") > 0
                    && victim.getRandom().nextDouble() < GHOST_STEP_CHANCE) {
                event.setCanceled(true);
                victim.level().playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                        SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.PLAYERS, 0.8f, 1.6f);
                return;
            }
            if (source.is(DamageTypeTags.IS_DROWNING)) {
                int lungs = TalentService.rank(victim, Skill.SWIMMING, "deep_lungs");
                if (lungs > 0) {
                    event.setAmount((float) (event.getAmount() * Math.max(0.0, 1.0 - lungs * 0.25)));
                }
            }
            return;
        }

        if (!(source.getEntity() instanceof Player player) || STOMPING.get()) {
            return;
        }
        double multiplier = 1.0;
        int backstab = TalentService.rank(player, Skill.SNEAKING, "backstab");
        if (backstab > 0) {
            Vec3 facing = Vec3.directionFromRotation(0.0f, target.yBodyRot);
            if (behind(facing.x, facing.z, player.getX() - target.getX(), player.getZ() - target.getZ())) {
                multiplier *= 1.0 + backstab * 0.10;
            }
        }
        if (player.hasEffect(MobEffects.INVISIBILITY)
                && !(target instanceof Mob mob && mob.getTarget() == player)
                && TalentService.rank(player, Skill.SNEAKING, "assassinate") > 0) {
            multiplier *= 2.0;
        }
        if (multiplier != 1.0) {
            event.setAmount((float) (event.getAmount() * multiplier));
        }
    }

    /**
     * Zephyr caps the damage a fall actually deals, not the distance: fall damage is distance minus
     * the safe distance times several multipliers, so no distance maps cleanly onto "4". Lowest
     * priority so it caps whatever other mods added. Armour and Feather Falling still apply after.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFallDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide()
                && event.getSource().is(DamageTypeTags.IS_FALL)
                && event.getAmount() > ZEPHYR_CAP
                && TalentService.rank(player, Skill.JUMPING, "zephyr") > 0) {
            event.setAmount(ZEPHYR_CAP);
        }
    }

    /** Escape Artist: the moment a hit leaves you under 30%, run. */
    @SubscribeEvent
    public static void onDamaged(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()
                || player.isDeadOrDying() || player.getHealth() >= player.getMaxHealth() * 0.3f) {
            return;
        }
        int rank = TalentService.rank(player, Skill.RUNNING, "escape");
        if (rank <= 0) {
            return;
        }
        State state = STATES.computeIfAbsent(player.getUUID(), id -> new State());
        long now = player.level().getGameTime();
        if (now < state.escapeReadyAt) {
            return;
        }
        state.escapeReadyAt = now + ESCAPE_COOLDOWN;
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40 * rank, 1));
    }

    /** Unseen. Vanilla already shrinks a crouched player's detection range; this shrinks it more. */
    @SubscribeEvent
    public static void onVisibility(LivingEvent.LivingVisibilityEvent event) {
        if (!(event.getEntity() instanceof Player player) || !player.isCrouching()
                || !(event.getLookingEntity() instanceof Mob)) {
            return;
        }
        int rank = TalentService.rank(player, Skill.SNEAKING, "unseen");
        if (rank > 0) {
            event.modifyVisibility(Math.max(0.0, 1.0 - rank * 0.10));
        }
    }

    /** Pickpocket: every drop of the kill, twice. */
    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide() || !(dead instanceof Mob)
                || !(event.getSource().getEntity() instanceof Player player) || !player.isCrouching()) {
            return;
        }
        int rank = TalentService.rank(player, Skill.SNEAKING, "pickpocket");
        if (rank <= 0 || player.getRandom().nextDouble() >= rank * 0.10) {
            return;
        }
        List<ItemEntity> copies = new ArrayList<>();
        for (ItemEntity drop : event.getDrops()) {
            ItemEntity copy = new ItemEntity(dead.level(), drop.getX(), drop.getY(), drop.getZ(),
                    drop.getItem().copy());
            copy.setDefaultPickUpDelay();
            copies.add(copy);
        }
        event.getDrops().addAll(copies);
    }

    /**
     * Pearl Diver. Vanilla divides underwater mining twice: by the submerged-mining attribute, and
     * by five again when you are not standing on anything, which a diver usually is not. Both are
     * undone. Low priority so it multiplies the speed Gathering has already set, and on both sides
     * like Gathering's, or the crack animation and the server disagree about when the block goes.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        if (!player.isEyeInFluid(FluidTags.WATER)
                || TalentService.rank(player, Skill.SWIMMING, "pearl_diver") <= 0) {
            return;
        }
        double factor = 1.0;
        double submerged = player.getAttributeValue(Attributes.SUBMERGED_MINING_SPEED);
        if (submerged > 0 && submerged < 1.0) {
            factor /= submerged;
        }
        if (!player.onGround()) {
            factor *= 5.0;
        }
        if (factor != 1.0) {
            event.setNewSpeed((float) (event.getNewSpeed() * factor));
        }
    }

    // ---- Called from MovementEvents.onFall -----------------------------------------------------

    /** Feather Weight always, Cat Landing from a crouch. Multiplies LivingFallEvent's multiplier. */
    static double fallDamageFactor(Player player) {
        int feather = TalentService.rank(player, Skill.JUMPING, "feather");
        int cat = TalentService.rank(player, Skill.SNEAKING, "cat_landing");
        return fallFactor(feather, cat, player.isCrouching());
    }

    /**
     * Stomp. Mobs do not collide with players, so falling "onto" one means landing inside its box;
     * the nearest mob overlapping your feet takes the hit. The damage is sourced to you with no
     * direct entity, so Combat does not also read it as a weapon swing and train a skill on it.
     */
    static void stomp(Player player, float fallen) {
        if (fallen < 3.0f || STOMPING.get()) {
            return;
        }
        int rank = TalentService.rank(player, Skill.JUMPING, "stomp");
        if (rank <= 0) {
            return;
        }
        Mob victim = player.level().getEntitiesOfClass(Mob.class,
                        player.getBoundingBox().inflate(0.3, 0.0, 0.3).expandTowards(0.0, -0.5, 0.0),
                        Mob::isAlive)
                .stream()
                .min(Comparator.comparingDouble(mob -> mob.distanceToSqr(player)))
                .orElse(null);
        if (victim == null) {
            return;
        }
        DamageSource stomp = player.damageSources().source(DamageTypes.PLAYER_ATTACK, null, player);
        STOMPING.set(true);
        try {
            victim.hurt(stomp, 2.0f * rank);
        } finally {
            STOMPING.set(false);
        }
        player.level().playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                SoundEvents.MACE_SMASH_GROUND, SoundSource.PLAYERS, 0.8f, 1.2f);
    }

    /**
     * Bounce. The server does not move a player, the client does, so the new velocity only matters
     * once the client hears about it: sent as a motion packet straight away.
     */
    static void bounce(Player player) {
        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(motion.x, Math.max(motion.y, BOUNCE_SPEED), motion.z);
        player.resetFallDistance();
        player.hasImpulse = true;
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.connection.send(new ClientboundSetEntityMotionPacket(serverPlayer));
        }
    }

    // ---- Pure arithmetic, unit tested -------------------------------------------------------------

    /** 10% per rank each, multiplied together, never below nothing. */
    static double fallFactor(int featherRanks, int catRanks, boolean crouching) {
        double factor = Math.max(0.0, 1.0 - featherRanks * 0.10);
        if (crouching) {
            factor *= Math.max(0.0, 1.0 - catRanks * 0.10);
        }
        return factor;
    }

    /**
     * Whether an attacker at offset (ax, az) from the target is behind it, given the unit vector
     * (fx, fz) the target's body faces.
     */
    static boolean behind(double fx, double fz, double ax, double az) {
        double length = Math.sqrt(ax * ax + az * az);
        if (length < 1.0e-6) {
            return false;
        }
        return (fx * ax + fz * az) / length < BEHIND_COS;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        STATES.remove(event.getEntity().getUUID());
    }
}
