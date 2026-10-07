package dev.amman.proficiency.skill;

import dev.amman.proficiency.config.ProficiencyConfig;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rewards rhythm, not just hours.
 *
 * <p>Levels 25 to 75 in a skill were "the same proc, slightly more often", which is the same
 * afternoon repeated. A streak multiplier changes what improving feels like: by hour twenty a
 * player has learned to chain their actions, and this pays for that learned behaviour on top of the
 * number going up. Pause and it decays, so it cannot be left running by an AFK macro.
 */
public final class Tempo {

    private static final Map<UUID, Map<Skill, Streak>> STREAKS = new ConcurrentHashMap<>();

    /** However fluid you are, a chain ends after this and has to be built again. */
    private static final long MAX_STREAK_TICKS = 1200;

    private static int maxSteps() {
        double step = ProficiencyConfig.tempoStep();
        return step <= 0 ? 0 : (int) (ProficiencyConfig.tempoCap() / step);
    }

    private Tempo() {
    }

    private static final class Streak {
        long lastTick;
        long startedTick;
        int steps;
    }

    public static double multiplier(ServerPlayer player, Skill skill) {
        long now = player.level().getGameTime();
        Map<Skill, Streak> perSkill = STREAKS.computeIfAbsent(
                player.getUUID(), id -> new ConcurrentHashMap<>());
        Streak streak = perSkill.computeIfAbsent(skill, s -> new Streak());

        boolean broken = now - streak.lastTick > window(player, skill)
                || now < streak.lastTick
                || now - streak.startedTick > MAX_STREAK_TICKS;
        if (broken) {
            streak.steps = 0;
            streak.startedTick = now;
        } else if (streak.steps < maxSteps()) {
            streak.steps++;
        }
        streak.lastTick = now;
        // Rhythm scales the bonus, not the base: the same streak is simply worth more.
        double rhythm = dev.amman.proficiency.ProficiencyAttachments.of(player)
                .perkModifier(skill, dev.amman.proficiency.perk.PerkEffect.TEMPO);
        return 1.0 + streak.steps * ProficiencyConfig.tempoStep() * rhythm;
    }

    /**
     * Ticks of silence a chain survives. In Step (the Social keystone) doubles it while you have
     * company, so a pause to talk or wait for a friend does not break the chain.
     */
    private static long window(ServerPlayer player, Skill skill) {
        long window = ProficiencyConfig.tempoWindow();
        if (dev.amman.proficiency.perk.TalentService.rank(player, Skill.SOCIAL, "in_step") > 0
                && CompanyBonus.multiplier(player, skill) > 1.0) {
            window *= 2;
        }
        return window;
    }

    public static void forget(UUID player) {
        STREAKS.remove(player);
    }
}
