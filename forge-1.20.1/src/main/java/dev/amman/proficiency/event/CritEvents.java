package dev.amman.proficiency.event;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.perk.TalentService;
import dev.amman.proficiency.skill.CritRules;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillTools;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Critical hits (docs/CRITS-SPEC.md). The loader's crit event decides the crit; {@link CombatEvents}
 * then asks {@link #takeCrit} whether the hit it is scaling was one, and pays it: more hit XP, a
 * likelier proc, and the crit talents (Rend, Flow) through {@link #onCritHit}.
 *
 * <p>Lunge (Swords) forces a crit on a sprinting hit, which vanilla never allows. Leaping Blade
 * (Swords + Jumping) makes a sword crit land harder.
 *
 * <p>Forge 1.20.1's crit event says "crit" through its result: ALLOW forces one, DENY stops one,
 * DEFAULT keeps vanilla's verdict. The 1.21.1 lines have a flag instead (common/ CritEvents).
 */
@Mod.EventBusSubscriber(modid = Proficiency.MOD_ID)
public final class CritEvents {

    /** Lunge: game time each player's next forced crit is ready. */
    private static final Map<UUID, Long> LUNGE_READY_AT = new ConcurrentHashMap<>();
    /** The crit the swing in progress decided on: player to {target entity id, game time}. */
    private static final Map<UUID, long[]> PENDING = new ConcurrentHashMap<>();
    /** Rend: wounded entity to the game time its wound closes. */
    private static final Map<UUID, Long> REND_UNTIL = new ConcurrentHashMap<>();
    /** Past this many open wounds, the closed ones are swept out. */
    private static final int REND_SWEEP_AT = 256;

    private CritEvents() {
    }

    /** Lunge and Leaping Blade change the crit before anything reads it. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onCriticalHit(CriticalHitEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide() || SkillTools.isFakePlayer(player)
                || !(event.getTarget() instanceof LivingEntity)) {
            return;
        }
        Skill skill = SkillTools.meleeSkill(player.getMainHandItem());
        if (skill != Skill.SWORDS) {
            return;
        }
        if (!isCritical(event) && player.isSprinting()) {
            long cooldown = CritRules.lungeCooldownTicks(TalentService.rank(player, Skill.SWORDS, "lunge"));
            long now = player.level().getGameTime();
            if (cooldown > 0 && CritRules.lungeReady(now, LUNGE_READY_AT.getOrDefault(player.getUUID(), Long.MIN_VALUE))) {
                event.setResult(Event.Result.ALLOW);
                event.setDamageModifier(Math.max(event.getDamageModifier(), CritRules.VANILLA_DAMAGE));
                LUNGE_READY_AT.put(player.getUUID(), now + cooldown);
            }
        }
        if (isCritical(event)) {
            event.setDamageModifier(CritRules.critDamage(event.getDamageModifier(),
                    TalentService.hasSynergy(player, "leaping_blade")));
        }
    }

    /** The verdict Forge will act on: ALLOW, or vanilla's crit left at DEFAULT. */
    private static boolean isCritical(CriticalHitEvent event) {
        Event.Result result = event.getResult();
        return result == Event.Result.ALLOW || (result == Event.Result.DEFAULT && event.isVanillaCritical());
    }

    /** Whatever the crit ended up as, after every listener: remembered for the damage that follows. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onCriticalHitDecided(CriticalHitEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }
        if (isCritical(event)) {
            PENDING.put(player.getUUID(), new long[] {event.getTarget().getId(), player.level().getGameTime()});
            // A jump crit as vanilla decided it (not Lunge, not any other crit of ours) holds the Tempo chain longer.
            if (isVanillaJumpCrit(event) && player instanceof net.minecraft.server.level.ServerPlayer server
                    && !SkillTools.isFakePlayer(server) && event.getTarget() instanceof LivingEntity) {
                dev.amman.proficiency.skill.Tempo.markVanillaCrit(server);
            }
        } else {
            PENDING.remove(player.getUUID());
        }
    }

    /**
     * True for a crit vanilla itself decided (the jump crit), false for one only Proficiency forced.
     */
    private static boolean isVanillaJumpCrit(CriticalHitEvent event) {
        return event.isVanillaCritical() && isCritical(event);
    }

    /**
     * Whether the hit {@code player} is landing on {@code target} right now is the crit their swing
     * decided on. Spends it, so a second hurt in the same tick (a Cleave, a thorn) is not one.
     */
    public static boolean takeCrit(Player player, LivingEntity target) {
        long[] pending = PENDING.remove(player.getUUID());
        return pending != null && pending[0] == target.getId()
                && pending[1] == player.level().getGameTime();
    }

    /** A landed critical melee hit: the talents that feed on one. */
    static void onCritHit(Player player, LivingEntity target, Skill skill) {
        long now = player.level().getGameTime();
        if (skill == Skill.AXES && TalentService.rank(player, Skill.AXES, "rend") > 0) {
            if (REND_UNTIL.size() > REND_SWEEP_AT) {
                REND_UNTIL.values().removeIf(until -> until < now);
            }
            REND_UNTIL.put(target.getUUID(), now + CritRules.REND_TICKS);
            if (player.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, target.getX(), target.getY(0.6), target.getZ(),
                        5, 0.25, 0.25, 0.25, 0.05);
                level.playSound(null, target.getX(), target.getY(), target.getZ(),
                        SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.6f, 0.6f);
            }
        }
        if (skill == Skill.UNARMED) {
            int ticks = CritRules.flowTicks(TalentService.rank(player, Skill.UNARMED, "flow"));
            if (ticks > 0) {
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, 0, false, false, true));
                player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, ticks, 0, false, false, true));
            }
        }
    }

    /** Rend: how much harder a weapon hit lands on {@code target} while its wound is open. */
    static float rendMultiplier(LivingEntity target) {
        Long until = REND_UNTIL.get(target.getUUID());
        return until == null ? 1.0f : CritRules.rendMultiplier(target.level().getGameTime(), until);
    }

    public static void forget(UUID player) {
        LUNGE_READY_AT.remove(player);
        PENDING.remove(player);
    }
}
