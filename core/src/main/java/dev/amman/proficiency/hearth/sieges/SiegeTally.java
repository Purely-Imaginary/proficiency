package dev.amman.proficiency.hearth.sieges;

import dev.amman.proficiency.hearth.SiegeRules;

/**
 * How each siege mob's end counts toward the outcome (docs/HEARTH-SPEC.md, "Defended: every wave dead
 * before dawn, killed by players or their pets"). Pure.
 *
 * <ul>
 * <li>{@link Fall#KILL}: a player, a player's pet, or the last player to hurt it within vanilla's kill
 * credit window killed it. It pays the killer and counts for the defence.</li>
 * <li>{@link Fall#FRIENDLY}: another siege mob killed it (a skeleton's stray arrow, a creeper's blast),
 * or a siege creeper blew itself up at the defenders. Nobody's kill, and it spoils nothing: players
 * cannot set that up.</li>
 * <li>{@link Fall#UNEARNED}: anything else (lava, a fall, drowning, a golem, a portal, a mob that
 * vanished). A siege with even one of these cannot be defended any more: automation does not earn
 * the burst or the trophy.</li>
 * </ul>
 *
 * <p>A mob whose chunk is unloaded has not fallen at all: it still counts as standing.
 */
public final class SiegeTally {

    public enum Fall { KILL, FRIENDLY, UNEARNED }

    private SiegeTally() {
    }

    /** One mob's end: credited to a player (or a pet's owner), dealt by a fellow siege mob, or neither. */
    public static Fall fall(boolean playerCredit, boolean byFellowSiegeMob) {
        if (playerCredit) {
            return Fall.KILL;
        }
        return byFellowSiegeMob ? Fall.FRIENDLY : Fall.UNEARNED;
    }

    /** Every wave spawned and none standing: a defence when no mob fell unearned, else neither won nor lost. */
    public static SiegeRules.Outcome cleared(int unearned) {
        return unearned <= 0 ? SiegeRules.Outcome.DEFENDED : SiegeRules.Outcome.UNEARNED;
    }
}
