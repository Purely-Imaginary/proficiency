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

    /** The chain plus what the last committed action left for {@link #announce}. */
    private static final class Streak {
        final TempoChain chain = new TempoChain();
        boolean pending;
        double multiplier = 1.0;
        long window;
    }

    public static double multiplier(ServerPlayer player, Skill skill) {
        return step(player, skill, true, 0);
    }

    /**
     * What {@link #multiplier} would answer for the next action, without taking the step: the Jade
     * tooltip's "XP you would get now" reads it, and looking must never build a chain.
     */
    public static double preview(ServerPlayer player, Skill skill) {
        return step(player, skill, false, 0);
    }

    /**
     * {@link #preview(ServerPlayer, Skill)} for an action that comes {@code later} actions after the next
     * one: a kill lands one step after the hit that precedes it, so its Jade number takes one more.
     */
    public static double preview(ServerPlayer player, Skill skill, int later) {
        return step(player, skill, false, Math.max(0, later));
    }

    private static double step(ServerPlayer player, Skill skill, boolean commit, int later) {
        long now = player.level().getGameTime();
        long window = window(player, skill);
        Streak real = null;
        TempoChain chain;
        if (commit) {
            real = STREAKS.computeIfAbsent(player.getUUID(), id -> new ConcurrentHashMap<>())
                    .computeIfAbsent(skill, s -> new Streak());
            chain = real.chain;
        } else {
            Map<Skill, Streak> perSkill = STREAKS.get(player.getUUID());
            Streak seen = perSkill == null ? null : perSkill.get(skill);
            chain = seen == null ? new TempoChain() : seen.chain.copy();
        }

        chain.advance(now, window, maxSteps(), MAX_STREAK_TICKS);
        int steps = Math.min(maxSteps(), chain.steps + later);
        // Rhythm scales the bonus, not the base: the same streak is simply worth more.
        double rhythm = dev.amman.proficiency.ProficiencyAttachments.of(player)
                .perkModifier(skill, dev.amman.proficiency.perk.PerkEffect.TEMPO);
        double multiplier = 1.0 + steps * ProficiencyConfig.tempoStep() * rhythm;
        if (real != null) {
            real.pending = true;
            real.multiplier = multiplier;
            real.window = window;
        }
        return multiplier;
    }

    /**
     * Tells the client about the chain the last committed action of {@code skill} left, when it changed. Called by
     * the grant AFTER the XP is paid, so nothing in the send path can lose a grant; one packet per changed skill.
     */
    public static void announce(ServerPlayer player, Skill skill) {
        try {
            Map<Skill, Streak> perSkill = STREAKS.get(player.getUUID());
            Streak streak = perSkill == null ? null : perSkill.get(skill);
            if (streak == null || !streak.pending) {
                return;
            }
            streak.pending = false;
            long now = player.level().getGameTime();
            if (!streak.chain.takeAnnouncement(now, streak.window)) {
                return;
            }
            // The client counts the window in real time: a server running slow stretches it.
            long clientWindow = Math.min(Integer.MAX_VALUE, Math.round(streak.window * slowdown(player)));
            dev.amman.proficiency.net.ProficiencyNetwork.sendTempo(player, skill, streak.chain.steps, maxSteps(),
                    (int) Math.round(streak.multiplier * 1000), (int) clientWindow);
        } catch (RuntimeException ignored) {
            // A cosmetic packet must never take the grant that follows it down.
        }
    }

    /** 1.0 at 20 TPS, more when the server's ticks are longer than 50 ms. */
    private static double slowdown(ServerPlayer player) {
        net.minecraft.server.MinecraftServer server = player.getServer();
        if (server == null) {
            return 1.0;
        }
        return Math.max(1.0, Math.min(4.0, server.getAverageTickTimeNanos() / 50_000_000.0));
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
