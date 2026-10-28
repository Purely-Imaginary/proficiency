package dev.amman.proficiency.skill;

import java.util.function.Function;

/**
 * Which Tempo rule each skill follows (1.49.0). Pure, so the server grant, the Jade preview and the client HUD
 * all read the same answer.
 *
 * <ul>
 *   <li>{@link Role#OWN}: the skill owns a chain at the normal window and step (Mining and everything else).</li>
 *   <li>{@link Role#WEAPON}: the weapon skills own a chain at the combat window and step. A fight has pauses
 *       (a creeper backs off, an arrow flies), so the chain survives longer and climbs faster to the same cap.</li>
 *   <li>{@link Role#RIDE}: the combat support skills (Courage, Charger, Guardian, Tactician, Blocking) pay a
 *       few times a minute, too seldom to build a chain of their own. They own no chain and never step one:
 *       they read the live weapon chain ({@link #ride}).</li>
 *   <li>{@link Role#EXEMPT}: no step, no multiplier, no flames. Endurance (paid for being hit), and the skills
 *       paid in lumps (Social, Nightwalker, Homebuilder).</li>
 * </ul>
 */
public final class TempoRules {

    public enum Role { OWN, WEAPON, RIDE, EXEMPT }

    /** The skills whose chains a {@link Role#RIDE} skill can ride, in a fixed order. */
    public static final Skill[] WEAPONS = {Skill.SWORDS, Skill.AXES, Skill.MACES, Skill.TRIDENTS, Skill.UNARMED,
            Skill.ARCHERY, Skill.CROSSBOWS, Skill.BEASTSLAYING};

    private TempoRules() {
    }

    public static Role role(Skill skill) {
        return switch (skill) {
            case SWORDS, AXES, MACES, TRIDENTS, UNARMED, ARCHERY, CROSSBOWS, BEASTSLAYING -> Role.WEAPON;
            case COURAGE, CHARGER, GUARDIAN, TACTICIAN, BLOCKING -> Role.RIDE;
            case ENDURANCE, SOCIAL, NIGHTWALKER, HOMEBUILDER -> Role.EXEMPT;
            default -> Role.OWN;
        };
    }

    /** The window before In Step: the combat one for a weapon skill, else the normal one. */
    public static long window(Skill skill, long normalTicks, long combatTicks) {
        return role(skill) == Role.WEAPON ? combatTicks : normalTicks;
    }

    /** The bonus per step: the combat one for a weapon skill, else the normal one. */
    public static double perStep(Skill skill, double normal, double combat) {
        return role(skill) == Role.WEAPON ? combat : normal;
    }

    /** Steps to the shared cap; 0 when the step is 0. Tolerant of 0.5 / 0.05 landing a hair under 10. */
    public static int maxSteps(double cap, double perStep) {
        return perStep <= 0 || cap <= 0 ? 0 : (int) Math.floor(cap / perStep + 1e-9);
    }

    /**
     * The window the next gap gets after a vanilla jump crit with a weapon: {@code window} times {@code mul}
     * (tempo.combat.critWindowMul, 2.0), or 0 (no grace) when the multiplier does not lengthen it.
     */
    public static long critWindow(long window, double mul) {
        if (!(mul > 1.0) || window <= 0) {
            return 0;
        }
        return Math.min(24_000L, Math.round(window * mul));
    }

    /** What a rider needs to know about one weapon chain at the moment of asking. */
    public record Live(long lastTick, int steps, boolean alive) {
    }

    /**
     * The weapon chain a {@link Role#RIDE} skill rides: of the weapon chains that are alive, the one stepped
     * most recently; on a tie (a kill pays the weapon and Beastslaying on one tick) the one with more steps,
     * then the first in {@link #WEAPONS}. Null when no weapon chain lives: the rider then pays x1.0.
     *
     * @param state the chain of a weapon skill, or null when it has none
     */
    public static Skill ride(Function<Skill, Live> state) {
        Skill best = null;
        Live bestLive = null;
        for (Skill weapon : WEAPONS) {
            Live live = state.apply(weapon);
            if (live == null || !live.alive()) {
                continue;
            }
            if (bestLive == null || live.lastTick() > bestLive.lastTick()
                    || live.lastTick() == bestLive.lastTick() && live.steps() > bestLive.steps()) {
                best = weapon;
                bestLive = live;
            }
        }
        return best;
    }
}
