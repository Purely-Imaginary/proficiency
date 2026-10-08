package dev.amman.proficiency.event;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.perk.TalentService;
import dev.amman.proficiency.skill.ActiveService;
import dev.amman.proficiency.skill.ProcService;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillTools;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.living.LivingBreatheEvent;
import dev.amman.proficiency.compat.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import dev.amman.proficiency.compat.LivingIncomingDamageEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.TickEvent;

import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The melee talent specials: Swords, Axes, Maces, Tridents and Unarmed. The attacking half is called
 * from {@link CombatEvents}, which owns the order things happen in (damage, then the roll, then the
 * proc); the half where the player is the one being hit, killing, falling or breathing lives here.
 */
@Mod.EventBusSubscriber(modid = Proficiency.MOD_ID)
public final class TalentMeleeEvents {

    private static final long CHAIN_WINDOW = 60;
    private static final long COMBO_WINDOW = 30;
    private static final int COMBO_MAX_STACKS = 5;
    private static final long WIND_RIDER_WINDOW = 60;
    private static final long UNDYING_COOLDOWN = 20 * 60 * 5;
    private static final double DUELIST_RADIUS = 8.0;
    private static final float DUELIST_MULTIPLIER = 1.3f;
    private static final float CRATER_FALL = 6.0f;
    private static final int METEOR_MAX_BLOCKS = 20;
    /** Slowness VI is -90% speed: a crawl, not a stop, so the target can still be knocked about. */
    private static final int CRAWL_AMPLIFIER = 5;
    private static final double LAUNCH_SPEED = 0.9;

    /** Game time until which a player's next sword hit is a chained Perfect Strike. */
    private static final Map<UUID, Long> CHAIN_UNTIL = new ConcurrentHashMap<>();
    /** Unarmed combo: game time of the last punch and the stacks it built. */
    private static final Map<UUID, long[]> COMBO = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> WIND_RIDER_UNTIL = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> UNDYING_READY_AT = new ConcurrentHashMap<>();

    /**
     * Anything that moves or hurts the target of the hit in progress waits here for the end of the
     * tick. Vanilla knockback runs after the damage event and overwrites any velocity set during it,
     * and a second hurt() on an entity inside its own hurt eats the first one's damage through the
     * invulnerability window.
     */
    private static final Queue<Runnable> AFTER_HIT = new ConcurrentLinkedQueue<>();

    private TalentMeleeEvents() {
    }

    // ---- Attacking, called from CombatEvents ---------------------------------------------------

    /** Every talent that makes this one hit land harder. Not called for Cleave/Shockwave splash. */
    static float damageMultiplier(Player player, LivingEntity target, Skill skill) {
        float multiplier = 1.0f;
        switch (skill) {
            case SWORDS -> {
                int executioner = TalentService.rank(player, Skill.SWORDS, "executioner");
                if (executioner > 0 && target.getHealth() < target.getMaxHealth() * 0.3f) {
                    multiplier *= 1.0f + 0.10f * executioner;
                }
                if (TalentService.rank(player, Skill.SWORDS, "duelist") > 0
                        && hostilesNear(player, target) == 0) {
                    multiplier *= DUELIST_MULTIPLIER;
                }
            }
            case AXES -> multiplier *= berserkerMultiplier(
                    TalentService.rank(player, Skill.AXES, "berserker"),
                    player.getHealth(), player.getMaxHealth());
            case MACES -> multiplier *= meteorMultiplier(
                    TalentService.rank(player, Skill.MACES, "meteor"), player.fallDistance);
            case TRIDENTS -> {
                int tidal = TalentService.rank(player, Skill.TRIDENTS, "tidal_edge");
                if (tidal > 0 && player.isInWaterOrRain()) {
                    multiplier *= 1.0f + 0.06f * tidal;
                }
            }
            case UNARMED -> {
                int combo = TalentService.rank(player, Skill.UNARMED, "combo");
                if (combo > 0) {
                    long now = player.level().getGameTime();
                    long[] state = COMBO.computeIfAbsent(player.getUUID(), id -> new long[] {Long.MIN_VALUE / 2, 0});
                    state[1] = comboStacks((int) state[1], now - state[0]);
                    state[0] = now;
                    multiplier *= 1.0f + 0.05f * combo * state[1];
                }
            }
            default -> {
            }
        }
        return multiplier;
    }

    /**
     * Hostiles other than the target within Duelist's radius. The target counts as the one foe
     * whatever it is, so a duel with a player or a cow still is one.
     */
    private static int hostilesNear(Player player, LivingEntity target) {
        return player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(DUELIST_RADIUS),
                entity -> entity != target && entity instanceof Enemy && entity.isAlive()
                        && entity.distanceToSqr(player) <= DUELIST_RADIUS * DUELIST_RADIUS).size();
    }

    /**
     * Talents that promise the roll lands: Sword Saint's chain and Cratermaker. Returns true when
     * this hit is a chained Perfect Strike, which must not arm another chain or Sword Saint would
     * be a permanent Berserk for anyone who swings every three seconds. A frenzy already lands
     * every roll, and a forced flag set during one would sit unused until after it, so none is set.
     */
    static boolean beforeRoll(Player player, Skill skill) {
        if (ActiveService.isFrenzied(player, skill)) {
            return false;
        }
        if (skill == Skill.SWORDS) {
            Long until = CHAIN_UNTIL.remove(player.getUUID());
            if (until != null && player.level().getGameTime() <= until
                    && TalentService.rank(player, Skill.SWORDS, "chain_strike") > 0) {
                ProcService.forceNext(player, Skill.SWORDS);
                return true;
            }
        }
        if (skill == Skill.MACES && player.fallDistance >= CRATER_FALL
                && TalentService.rank(player, Skill.MACES, "crater") > 0) {
            ProcService.forceNext(player, Skill.MACES);
        }
        return false;
    }

    /** Everything that happens on a landed main hit, whether or not it procced. */
    static void afterHit(Player player, LivingEntity target, Skill skill, boolean procced, boolean chained) {
        long now = player.level().getGameTime();
        switch (skill) {
            case SWORDS -> {
                if (!procced) {
                    return;
                }
                int bleed = TalentService.rank(player, Skill.SWORDS, "bleed");
                if (bleed > 0) {
                    target.addEffect(new MobEffectInstance(MobEffects.WITHER, 40 * bleed, 0), player);
                }
                int lifesteal = TalentService.rank(player, Skill.SWORDS, "lifesteal");
                if (lifesteal > 0) {
                    player.heal(lifesteal);
                }
                if (!chained && TalentService.rank(player, Skill.SWORDS, "chain_strike") > 0) {
                    CHAIN_UNTIL.put(player.getUUID(), now + CHAIN_WINDOW);
                }
            }
            case MACES -> {
                if (TalentService.rank(player, Skill.MACES, "wind_rider") > 0) {
                    WIND_RIDER_UNTIL.put(player.getUUID(), now + WIND_RIDER_WINDOW);
                }
            }
            case TRIDENTS -> {
                if (procced) {
                    tridentProc(player, target);
                }
            }
            case UNARMED -> {
                if (TalentService.rank(player, Skill.UNARMED, "fist_heal") > 0) {
                    player.heal(1.0f);
                }
            }
            default -> {
            }
        }
    }

    private static void tridentProc(Player player, LivingEntity target) {
        int harpoon = TalentService.rank(player, Skill.TRIDENTS, "harpoon");
        if (harpoon > 0) {
            AFTER_HIT.add(() -> {
                if (!target.isAlive()) {
                    return;
                }
                Vec3 pull = player.position().subtract(target.position()).multiply(1, 0, 1);
                if (pull.lengthSqr() < 1.0e-4) {
                    return;
                }
                pull = pull.normalize().scale(0.4 * harpoon);
                target.setDeltaMovement(pull.x, 0.25, pull.z);
                target.hurtMarked = true;
            });
        }
        // Channeling's own test: a thunderstorm, and open sky over the target. The bolt is visual
        // and only the target is struck, so a melee proc does not also hit the player holding it.
        if (TalentService.rank(player, Skill.TRIDENTS, "conductor") > 0
                && player.level() instanceof ServerLevel level && level.isThundering()
                && level.canSeeSky(target.blockPosition())) {
            AFTER_HIT.add(() -> {
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                if (bolt == null || !target.isAlive()) {
                    return;
                }
                bolt.moveTo(target.getX(), target.getY(), target.getZ());
                bolt.setVisualOnly(true);
                if (player instanceof ServerPlayer serverPlayer) {
                    bolt.setCause(serverPlayer);
                }
                level.addFreshEntity(bolt);
                target.thunderHit(level, bolt);
            });
        }
    }

    /** How far past the thing you hit a Cleave or a Shockwave reaches. */
    static double splashRadius(Player player, Skill skill, double base) {
        return switch (skill) {
            case AXES -> base + TalentService.rank(player, Skill.AXES, "wide_cleave");
            case MACES -> base + TalentService.rank(player, Skill.MACES, "aftershock");
            default -> base;
        };
    }

    /** One thing a Cleave or Shockwave hit, the main target included. */
    static void onSplashHit(Player player, LivingEntity entity, Skill skill, boolean mainTarget) {
        if (skill == Skill.AXES) {
            int sunder = TalentService.rank(player, Skill.AXES, "sunder");
            if (sunder > 0) {
                entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40 * sunder, 0), player);
            }
        } else if (skill == Skill.MACES) {
            int concussion = TalentService.rank(player, Skill.MACES, "concussion");
            if (mainTarget && concussion > 0) {
                entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                        20 * concussion, CRAWL_AMPLIFIER), player);
            }
            if (TalentService.rank(player, Skill.MACES, "launch") > 0) {
                AFTER_HIT.add(() -> {
                    if (entity.isAlive()) {
                        Vec3 motion = entity.getDeltaMovement();
                        entity.setDeltaMovement(motion.x, Math.max(motion.y, LAUNCH_SPEED), motion.z);
                        entity.hurtMarked = true;
                    }
                });
            }
        }
    }

    /** The knockout blow's own effects on top of its slow. Returns the knockback multiplier. */
    static double onKnockout(Player player, LivingEntity target) {
        int stagger = TalentService.rank(player, Skill.UNARMED, "stagger");
        if (stagger > 0) {
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40 * stagger, 0), player);
        }
        return TalentService.rank(player, Skill.UNARMED, "one_inch") > 0 ? 3.0 : 1.0;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Runnable task;
        while ((task = AFTER_HIT.poll()) != null) {
            task.run();
        }
    }

    // ---- Being hit ------------------------------------------------------------------------------

    /**
     * Parry and Iron Skin: the player is the one taking the hit. High priority so a parried swing
     * is gone before {@link CombatEvents} pays the attacker XP or rolls their proc for it.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlayerHurt(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) {
            return;
        }
        DamageSource source = event.getSource();
        Skill held = SkillTools.meleeSkill(player.getMainHandItem());
        if (held == Skill.SWORDS && isMelee(source)) {
            int parry = TalentService.rank(player, Skill.SWORDS, "parry");
            if (parry > 0 && player.getRandom().nextDouble() < 0.05 * parry) {
                event.setCanceled(true);
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.8f, 1.4f);
                return;
            }
        }
        if (held == Skill.UNARMED) {
            int ironSkin = TalentService.rank(player, Skill.UNARMED, "iron_skin");
            if (ironSkin > 0) {
                event.setAmount(event.getAmount() * (1.0f - 0.05f * ironSkin));
            }
        }
    }

    /** Something swung at you: the thing that dealt it is the thing that caused it, and alive. */
    private static boolean isMelee(DamageSource source) {
        return source.getDirectEntity() instanceof LivingEntity
                && source.getDirectEntity() == source.getEntity()
                && !source.is(DamageTypeTags.IS_EXPLOSION)
                && !source.is(DamageTypeTags.IS_PROJECTILE);
    }

    /**
     * Undying Rage. Read after armour, where the damage is the real amount, and before absorption
     * and health are touched. /kill and the void still kill: they bypass invulnerability.
     */
    @SubscribeEvent
    public static void onDamagePre(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) {
            return;
        }
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        float buffer = player.getHealth() + player.getAbsorptionAmount();
        if (event.getNewDamage() < buffer
                || SkillTools.meleeSkill(player.getMainHandItem()) != Skill.AXES
                || TalentService.rank(player, Skill.AXES, "undying_rage") <= 0) {
            return;
        }
        long now = player.level().getGameTime();
        Long readyAt = UNDYING_READY_AT.get(player.getUUID());
        if (readyAt != null && now < readyAt) {
            return;
        }
        UNDYING_READY_AT.put(player.getUUID(), now + UNDYING_COOLDOWN);
        event.setNewDamage(Math.max(0f, buffer - 1.0f));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.6f, 0.7f);
    }

    /** Bloodrush and Warlord: anything killed by an axe swing, Cleave's splash included. */
    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof Player player) || player.level().isClientSide()
                || source.getDirectEntity() != player || event.getEntity() == player
                || SkillTools.meleeSkill(player.getMainHandItem()) != Skill.AXES) {
            return;
        }
        int bloodrush = TalentService.rank(player, Skill.AXES, "bloodrush");
        if (bloodrush > 0) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40 * bloodrush, 0));
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 40 * bloodrush, 0));
        }
        if (TalentService.rank(player, Skill.AXES, "warlord") > 0) {
            player.heal(2.0f);
        }
    }

    /**
     * Wind Rider. Zeroes the damage rather than cancelling the event, and runs last: a cancelled
     * fall never reaches the Jumping tree, so Stomp and Bounce would silently stop working for the
     * three seconds after every mace hit.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) {
            return;
        }
        Long until = WIND_RIDER_UNTIL.get(player.getUUID());
        if (until != null && player.level().getGameTime() <= until) {
            event.setDamageMultiplier(0f);
        }
    }

    /**
     * Deep Breath. Both sides: air is ticked on the client as well, and a server-only answer leaves
     * the owner's bubble bar draining on screen while nothing is actually lost.
     */
    @SubscribeEvent
    public static void onBreathe(LivingBreatheEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.canBreathe()) {
            return;
        }
        if (holdingTrident(player) && TalentService.rank(player, Skill.TRIDENTS, "trident_breath") > 0) {
            event.setCanBreathe(true);
        }
    }

    /** Sea King, refreshed once a second rather than checked every tick. */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Player player = event.player;
        if (player.level().isClientSide() || player.tickCount % 20 != 0) {
            return;
        }
        if (player.isInWater() && holdingTrident(player)
                && TalentService.rank(player, Skill.TRIDENTS, "sea_king") > 0) {
            player.addEffect(new MobEffectInstance(MobEffects.CONDUIT_POWER, 100, 0, true, false, true));
        }
    }

    private static boolean holdingTrident(Player player) {
        return player.isHolding(stack -> stack.getItem() instanceof TridentItem);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        CHAIN_UNTIL.remove(id);
        COMBO.remove(id);
        WIND_RIDER_UNTIL.remove(id);
        UNDYING_READY_AT.remove(id);
    }

    // ---- Pure arithmetic, unit tested -----------------------------------------------------------

    /** A punch inside the window adds a stack, up to five; a late one starts again from none. */
    public static int comboStacks(int previous, long ticksSinceLast) {
        if (ticksSinceLast < 0 || ticksSinceLast > COMBO_WINDOW) {
            return 0;
        }
        return Math.min(COMBO_MAX_STACKS, previous + 1);
    }

    /** +3% per rank for every whole 10% of health missing. */
    public static float berserkerMultiplier(int rank, float health, float maxHealth) {
        if (rank <= 0 || !(maxHealth > 0)) {
            return 1.0f;
        }
        float missing = Math.max(0f, Math.min(1f, 1f - health / maxHealth));
        int tenths = (int) Math.floor(missing * 10f + 1.0e-4f);
        return 1.0f + 0.03f * rank * tenths;
    }

    /** +4% per rank for every whole block fallen, counting at most twenty. */
    public static float meteorMultiplier(int rank, float fallDistance) {
        if (rank <= 0 || !(fallDistance > 0)) {
            return 1.0f;
        }
        int blocks = Math.min(METEOR_MAX_BLOCKS, (int) Math.floor(fallDistance));
        return 1.0f + 0.04f * rank * blocks;
    }
}
