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
    /** Game time of each player's last vanilla jump crit (CritEvents marks it before the hit's grants). */
    private static final Map<UUID, Long> VANILLA_CRIT = new ConcurrentHashMap<>();

    /** However fluid you are, a chain ends after this and has to be built again. */
    private static final long MAX_STREAK_TICKS = 1200;

    /** The bonus one step adds in {@code skill}: the combat step for a weapon skill (1.49.0), else the normal one. */
    private static double perStep(Skill skill) {
        return TempoRules.perStep(skill, ProficiencyConfig.tempoStep(), ProficiencyConfig.tempoCombatStep());
    }

    /** Steps to the shared cap in {@code skill}: 25 at the normal step, 10 at the combat one. */
    private static int maxSteps(Skill skill) {
        return TempoRules.maxSteps(ProficiencyConfig.tempoCap(), perStep(skill));
    }

    private Tempo() {
    }

    /** The chain plus what the last committed action left for {@link #announce}. */
    private static final class Streak {
        final TempoChain chain = new TempoChain();
        boolean pending;
        boolean crit;
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
        switch (TempoRules.role(skill)) {
            case EXEMPT:
                return 1.0;
            case RIDE:
                // A support grant takes no step and owns no chain: it pays what the live weapon chain pays.
                return riding(player);
            default:
                break;
        }
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

        int max = maxSteps(skill);
        // A vanilla jump crit with a weapon gives the next gap a longer window (tempo.combat.critWindowMul). Only a
        // real grant knows it was a crit: the Jade preview steps a copy with no grace.
        boolean crit = commit && TempoRules.role(skill) == TempoRules.Role.WEAPON && critNow(player, now);
        chain.advance(now, window, max, MAX_STREAK_TICKS,
                crit ? TempoRules.critWindow(window, ProficiencyConfig.tempoCombatCritWindowMul()) : 0);
        int steps = Math.min(max, chain.steps + later);
        double multiplier = multiplier(player, skill, steps);
        if (real != null) {
            real.pending = true;
            real.crit |= crit;
            real.multiplier = multiplier;
            real.window = window;
        }
        return multiplier;
    }

    /**
     * Actions in the player's current chain of {@code skill}, uncapped (the steps stop at the Tempo cap): what the
     * personal records count. 0 when the skill keeps no chain, or the last one lapsed more than a window ago.
     */
    public static int chainActions(ServerPlayer player, Skill skill) {
        Map<Skill, Streak> perSkill = STREAKS.get(player.getUUID());
        Streak streak = perSkill == null ? null : perSkill.get(skill);
        return streak == null ? 0 : streak.chain.actions;
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
            boolean crit = streak.crit;
            streak.crit = false;
            long now = player.level().getGameTime();
            // The window the next gap really gets: a crit's grace when it left one, so the flames last as long.
            long window = streak.chain.window(streak.window);
            if (!streak.chain.takeAnnouncement(now, window, crit)) {
                return;
            }
            // The client counts the window in real time: a server running slow stretches it.
            long clientWindow = Math.min(Integer.MAX_VALUE, Math.round(window * slowdown(player)));
            dev.amman.proficiency.net.ProficiencyNetwork.sendTempo(player, skill, streak.chain.steps, maxSteps(skill),
                    (int) Math.round(streak.multiplier * 1000), (int) clientWindow);
            if (crit) {
                // Optional channel: an older client simply sees no white flare.
                dev.amman.proficiency.net.ProficiencyNetwork.sendTempoCrit(player, skill);
            }
            dev.amman.proficiency.haul.HaulService.onTempo(player, skill, streak.chain.steps,
                    (int) Math.round(streak.multiplier * 1000));
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
        long window = TempoRules.window(skill, ProficiencyConfig.tempoWindow(), ProficiencyConfig.tempoCombatWindow());
        if (dev.amman.proficiency.perk.TalentService.rank(player, Skill.SOCIAL, "in_step") > 0
                && CompanyBonus.multiplier(player, skill) > 1.0) {
            window *= 2;
        }
        return window;
    }

    /** {@code steps} of {@code skill}'s chain as a multiplier. Rhythm scales the bonus, not the base. */
    private static double multiplier(ServerPlayer player, Skill skill, int steps) {
        double rhythm = dev.amman.proficiency.ProficiencyAttachments.of(player)
                .perkModifier(skill, dev.amman.proficiency.perk.PerkEffect.TEMPO);
        return 1.0 + steps * perStep(skill) * rhythm;
    }

    /**
     * The weapon chain a support skill rides right now ({@link TempoRules#ride}): the most recently stepped live
     * weapon chain, or null. Reads only; never steps or creates a chain.
     */
    public static Skill ridden(ServerPlayer player) {
        Map<Skill, Streak> perSkill = STREAKS.get(player.getUUID());
        if (perSkill == null) {
            return null;
        }
        long now = player.level().getGameTime();
        return TempoRules.ride(weapon -> {
            Streak streak = perSkill.get(weapon);
            if (streak == null) {
                return null;
            }
            TempoChain chain = streak.chain;
            // The window the chain was last stepped with (In Step may have doubled it then).
            long window = streak.window > 0 ? streak.window : window(player, weapon);
            return new TempoRules.Live(chain.lastTick, chain.steps, chain.alive(now, window, MAX_STREAK_TICKS));
        });
    }

    /** What a support grant pays now: the ridden weapon chain's own multiplier, x1.0 with none alive. */
    private static double riding(ServerPlayer player) {
        Skill weapon = ridden(player);
        if (weapon == null) {
            return 1.0;
        }
        Streak streak = STREAKS.get(player.getUUID()).get(weapon);
        return multiplier(player, weapon, Math.min(maxSteps(weapon), streak.chain.steps));
    }

    /**
     * The swing {@code player} is landing this tick is a vanilla jump crit (falling, not on the ground, not
     * sprinting, full attack strength, as vanilla decides). Proficiency's own crits (Lunge, the CRITS-SPEC ones)
     * never call this. The weapon grants of the same tick (the hit and a kill) give the next gap the long window.
     */
    public static void markVanillaCrit(ServerPlayer player) {
        VANILLA_CRIT.put(player.getUUID(), player.level().getGameTime());
    }

    private static boolean critNow(ServerPlayer player, long now) {
        Long at = VANILLA_CRIT.get(player.getUUID());
        return at != null && at == now;
    }

    public static void forget(UUID player) {
        VANILLA_CRIT.remove(player);
        STREAKS.remove(player);
    }
}
