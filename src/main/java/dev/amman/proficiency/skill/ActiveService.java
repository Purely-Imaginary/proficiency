package dev.amman.proficiency.skill;

import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.config.ProficiencyConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import dev.amman.proficiency.perk.PerkEffect;
import dev.amman.proficiency.perk.Talent;
import dev.amman.proficiency.perk.Talents;

/**
 * Active abilities. Every skill has exactly one, and it is always the same promise: for twenty
 * seconds your signature proc fires on every single action instead of rolling for it.
 *
 * <p>Mining Frenzy is a Motherlode on every block. Lumberfall is a Timber on every tree. Berserk is
 * a Perfect Strike on every swing. Reusing the proc machinery rather than inventing twenty-seven
 * bespoke abilities is what makes this affordable, and it also means an ability always does the
 * thing the player already associates with that skill, only relentlessly.
 */
public final class ActiveService {

    public static int unlockLevel() {
        return ProficiencyConfig.abilityUnlockLevel();
    }

    public static int durationTicks() {
        return ProficiencyConfig.abilityDuration();
    }

    private ActiveService() {
    }

    public static boolean isFrenzied(Player player, Skill skill) {
        return ProficiencyAttachments.of(player).frenzyRemaining(skill, gameTime(player)) > 0;
    }

    public static long cooldownRemaining(Player player, Skill skill) {
        return ProficiencyAttachments.of(player).cooldownRemaining(skill, gameTime(player));
    }

    /** Returns null on success, or the reason it did not fire. */
    @Nullable
    public static Component activate(ServerPlayer player, Skill skill) {
        PlayerSkills skills = ProficiencyAttachments.of(player);
        long now = gameTime(player);

        if (skills.level(skill) < unlockLevel()) {
            return Component.translatable("proficiency.active.locked",
                    Component.translatable(skill.translationKey()), unlockLevel());
        }
        long cooldown = skills.cooldownRemaining(skill, now);
        if (cooldown > 0) {
            return Component.translatable("proficiency.active.cooldown",
                    Component.translatable(skill.activeKey()), cooldown / 20);
        }

        int duration = durationTicks(skills, skill);
        skills.beginFrenzy(skill, now + duration, now + cooldownTicks(skills, skill));

        MobEffect effect = accompaniment(skill);
        if (effect != null) {
            player.addEffect(new MobEffectInstance(
                    net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT
                            .wrapAsHolder(effect),
                    duration, 1));
        }

        player.displayClientMessage(Component.translatable(skill.activeKey())
                .withStyle(ChatFormatting.BOLD, ChatFormatting.GOLD), true);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8f, 1.3f);
        player.serverLevel().sendParticles(ParticleTypes.END_ROD,
                player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.5, 0.8, 0.5, 0.08);
        if (skill == Skill.CHARGER) {
            // Charge! is more than the usual twenty seconds: a dash, a war cry, a first blood.
            dev.amman.proficiency.event.ChargerEvents.chargeOut(player);
        }
        return null;
    }

    /** Vigor, Surge, Overflow and the capstone stretch it. */
    public static int durationTicks(PlayerSkills skills, Skill skill) {
        return (int) Math.round(durationTicks()
                * skills.perkModifier(skill, PerkEffect.ABILITY_DURATION));
    }

    /** Focus and the capstone shrink it, never below a quarter. */
    public static long cooldownTicks(PlayerSkills skills, Skill skill) {
        return Math.round(ProficiencyConfig.abilityCooldown()
                * skills.perkModifier(skill, PerkEffect.ABILITY_COOLDOWN));
    }

    /**
     * Surge: every proc that was actually rolled takes a second per rank off the cooldown. Frenzy
     * procs do not count, or the ability would refill itself while it ran.
     */
    static void onProc(ServerPlayer player, Skill skill) {
        PlayerSkills skills = ProficiencyAttachments.of(player);
        int rank = dev.amman.proficiency.perk.TalentService.rank(skills, skill, "surge");
        if (rank > 0) {
            skills.shortenCooldown(skill, 20L * rank, gameTime(player));
        }
    }

    /** A themed buff on top, so the twenty seconds feel different and not only luckier. */
    @Nullable
    private static MobEffect accompaniment(Skill skill) {
        // Unbreakable is about taking the hits, not dealing them, so it gets Resistance where the
        // rest of combat gets Strength.
        // Shield Wall is about taking the hits for others: Resistance too.
        if (skill == Skill.ENDURANCE || skill == Skill.GUARDIAN) {
            return MobEffects.DAMAGE_RESISTANCE.value();
        }
        return switch (skill.category()) {
            case GATHERING, CONSTRUCTION -> MobEffects.DIG_SPEED.value();
            case COMBAT -> MobEffects.DAMAGE_BOOST.value();
            case MOVEMENT, EXPEDITION -> MobEffects.MOVEMENT_SPEED.value();
            case MASTERY, CRAFTING -> MobEffects.LUCK.value();
            // Kindred Spirits: people like you, so villagers do too.
            case SOCIAL -> MobEffects.HERO_OF_THE_VILLAGE.value();
            // Eclipse: you see them, they lose you.
            case SURVIVAL -> MobEffects.NIGHT_VISION.value();
        };
    }

    private static long gameTime(Player player) {
        return player.level().getGameTime();
    }
}
