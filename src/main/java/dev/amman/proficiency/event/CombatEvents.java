package dev.amman.proficiency.event;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.perk.TalentService;
import dev.amman.proficiency.skill.ProcService;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillService;
import dev.amman.proficiency.skill.SkillTools;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@EventBusSubscriber(modid = Proficiency.MOD_ID)
public final class CombatEvents {

    /** How far a Cleave or a Shockwave reaches past the thing you actually hit. */
    private static final double SPLASH_RADIUS = 3.0;

    /**
     * Cleave damages bystanders, and that damage comes back through this same handler with the
     * same player as its source. Without this flag the first cleave would cleave, and so would
     * every hit it caused, until the stack ran out.
     */
    private static final ThreadLocal<Boolean> SPLASHING = ThreadLocal.withInitial(() -> false);

    /**
     * A riposte hits the attacker, and if the attacker is also a blocking player with the proc they
     * can riposte back. Each bounce is damped to 75%, so it terminates eventually, but "eventually"
     * is not a depth limit. This is.
     */
    private static final int MAX_RIPOSTE_DEPTH = 2;

    private static final ThreadLocal<Integer> RIPOSTE_DEPTH = ThreadLocal.withInitial(() -> 0);

    private CombatEvents() {
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof Player player) || player.level().isClientSide()) {
            return;
        }
        if (event.getEntity() == player) {
            return;
        }

        // A touch spell's direct entity is the player, and meleeSkill treats an empty hand as
        // Unarmed rather than as "not a weapon", so without this every self-cast hit was scaled and
        // paid twice: once as Spellcasting and once as whatever was in the other hand.
        if (ExpansionEvents.isArcaneSource(source)) {
            return;
        }

        Entity direct = source.getDirectEntity();
        Skill skill;
        if (direct == player) {
            skill = SkillTools.meleeSkill(player.getMainHandItem());
        } else if (direct instanceof AbstractArrow arrow) {
            skill = arrowSkill(arrow);
        } else if (direct instanceof ThrownTrident) {
            skill = Skill.TRIDENTS;
        } else {
            return;
        }
        if (skill == null) {
            return;
        }

        LivingEntity target = event.getEntity();
        double bonus = SkillService.bonus(player, skill);
        float amount = bonus > 0 ? (float) (event.getAmount() * (1.0 + bonus)) : event.getAmount();
        amount *= synergyDamage(player, skill);

        if (!SPLASHING.get()) {
            amount *= TalentMeleeEvents.damageMultiplier(player, target, skill);
            // Sword Saint and Cratermaker force the roll here. Marksman forces it earlier, from a
            // HIGH-priority handler; either way the flag is spent by the fire() just below.
            boolean chained = TalentMeleeEvents.beforeRoll(player, skill);
            boolean procced = ProcService.fire(player, skill, target);
            if (procced) {
                amount = applyProc(player, target, skill, amount);
            }
            TalentMeleeEvents.afterHit(player, target, skill, procced, chained);
        }

        if (amount != event.getAmount()) {
            event.setAmount(amount);
        }
        if (!SPLASHING.get()) {
            // Spawn-egg and command mobs pay nothing, spawner mobs a share (SpawnOrigin).
            SkillService.grant(player, skill, dev.amman.proficiency.skill.SpawnOrigin.xpFactor(target),
                    target.getType().getDescriptionId());
        }
    }

    /**
     * Silent Hunter's Ambush: anything you hit from a crouch takes a third more. Juggernaut: a
     * blow you caught on the shield charges the next mace or axe swing within five seconds.
     */
    private static float synergyDamage(Player player, Skill skill) {
        float multiplier = 1.0f;
        if (!SPLASHING.get() && player.isCrouching()
                && TalentService.hasSynergy(player, "ambush")) {
            multiplier *= AMBUSH_MULTIPLIER;
        }
        if ((skill == Skill.MACES || skill == Skill.AXES) && !SPLASHING.get()) {
            Long charged = JUGGERNAUT_CHARGE.remove(player.getUUID());
            if (charged != null && player.level().getGameTime() - charged <= JUGGERNAUT_WINDOW
                    && TalentService.hasSynergy(player, "juggernaut")) {
                multiplier *= JUGGERNAUT_MULTIPLIER;
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        net.minecraft.sounds.SoundEvents.MACE_SMASH_GROUND_HEAVY,
                        net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 1.1f);
            }
        }
        return multiplier;
    }

    private static final float AMBUSH_MULTIPLIER = 1.3f;
    private static final float JUGGERNAUT_MULTIPLIER = 1.5f;
    private static final long JUGGERNAUT_WINDOW = 100;

    /** Game time of each player's last charging block. One entry per player, cleared on use. */
    private static final java.util.Map<java.util.UUID, Long> JUGGERNAUT_CHARGE =
            new java.util.concurrent.ConcurrentHashMap<>();

    public static void forget(java.util.UUID player) {
        JUGGERNAUT_CHARGE.remove(player);
    }

    /**
     * The moment itself. Every weapon proc hits harder; some do something on top. Proc power scales
     * the multiplier and the splash, so Precision, Mastery and the "hits harder" nodes are felt.
     */
    private static float applyProc(Player player, LivingEntity target, Skill skill, float amount) {
        float power = (float) ProcService.power(player, skill);
        switch (skill) {
            case AXES -> {
                splash(player, target, Skill.AXES, amount * 0.5f * power, false);
                return amount * 1.6f * power;
            }
            case MACES -> {
                splash(player, target, Skill.MACES, amount * 0.4f * power, true);
                return amount * 1.6f * power;
            }
            case UNARMED -> {
                double knockback = 1.2 * TalentMeleeEvents.onKnockout(player, target);
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                        Math.round(60 * power), 2));
                target.knockback(knockback, player.getX() - target.getX(), player.getZ() - target.getZ());
                return amount * 2.0f * power;
            }
            default -> {
                return amount * 2.0f * power;
            }
        }
    }

    /** Cleave (axes) or Shockwave (maces): the bystanders, and the talents on everything it hits. */
    private static void splash(Player player, LivingEntity origin, Skill skill, float amount,
            boolean knockback) {
        TalentMeleeEvents.onSplashHit(player, origin, skill, true);
        AABB area = origin.getBoundingBox()
                .inflate(TalentMeleeEvents.splashRadius(player, skill, SPLASH_RADIUS));
        List<LivingEntity> nearby = player.level().getEntitiesOfClass(LivingEntity.class, area,
                entity -> entity != player && entity != origin && entity.isAlive()
                        && !(entity instanceof Player));
        if (nearby.isEmpty()) {
            return;
        }
        SPLASHING.set(true);
        try {
            for (LivingEntity entity : nearby) {
                entity.hurt(player.damageSources().playerAttack(player), amount);
                if (knockback) {
                    entity.knockback(0.8,
                            player.getX() - entity.getX(), player.getZ() - entity.getZ());
                }
                TalentMeleeEvents.onSplashHit(player, entity, skill, false);
            }
        } finally {
            SPLASHING.set(false);
        }
    }

    @Nullable
    private static Skill arrowSkill(AbstractArrow arrow) {
        if (!(arrow.getOwner() instanceof Player)) {
            return null;
        }
        ItemStack weapon = arrow.getWeaponItem();
        if (weapon != null && weapon.getItem() instanceof CrossbowItem) {
            return Skill.CROSSBOWS;
        }
        return Skill.ARCHERY;
    }

    /**
     * A vanilla shield already stops all of the damage, so there is no damage left for the skill to
     * remove. It buys shield durability instead, and on a proc it hands the hit back.
     */
    @SubscribeEvent
    public static void onShieldBlock(LivingShieldBlockEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) {
            return;
        }
        if (!event.getBlocked()) {
            return;
        }
        if (TalentService.hasSynergy(player, "juggernaut")) {
            JUGGERNAUT_CHARGE.put(player.getUUID(), player.level().getGameTime());
        }
        double bonus = SkillService.bonus(player, Skill.BLOCKING);
        if (bonus > 0) {
            event.setShieldDamage((float) (event.shieldDamage() * (1.0 - Math.min(0.9, bonus))));
        }

        LivingEntity attacker = event.getDamageSource().getEntity() instanceof LivingEntity living
                ? living : null;
        if (ProcService.fire(player, Skill.BLOCKING, attacker)) {
            event.setShieldDamage(0f);
            if (attacker != null && RIPOSTE_DEPTH.get() < MAX_RIPOSTE_DEPTH) {
                RIPOSTE_DEPTH.set(RIPOSTE_DEPTH.get() + 1);
                SPLASHING.set(true);
                try {
                    attacker.hurt(player.damageSources().thorns(player),
                            event.getBlockedDamage() * 0.75f
                                    * (float) ProcService.power(player, Skill.BLOCKING));
                } finally {
                    SPLASHING.set(false);
                    RIPOSTE_DEPTH.set(RIPOSTE_DEPTH.get() - 1);
                }
            }
        }
        SkillService.grant(player, Skill.BLOCKING, 1.0, attacker != null
                ? attacker.getType().getDescriptionId() : "proficiency.xplog.source.blocked");
    }

    /** The other half of Blocking: you get shoved around less while holding the shield up. */
    @SubscribeEvent
    public static void onKnockback(LivingKnockBackEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) {
            return;
        }
        if (!player.isBlocking()) {
            return;
        }
        double bonus = SkillService.bonus(player, Skill.BLOCKING);
        if (bonus > 0) {
            event.setStrength((float) (event.getStrength() * (1.0 - Math.min(0.9, bonus))));
        }
    }
}
