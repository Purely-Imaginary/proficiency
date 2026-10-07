package dev.amman.proficiency.skill;

import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * XP that one skill earns as a share of other skills' grants. The shares go into a small pot per
 * player and are paid out as one grant at most every few seconds, so mining a vein makes one
 * log line that counts up, not one line per block.
 *
 * <p>Social (idea 35) and Nightwalker (idea 36) both use it. Each decides what goes in; the pot
 * only holds and pays. A death empties it: unpaid XP is part of the bars a death wipes.
 */
public final class SharePot {

    private static final class Pot {
        double base;
        long lastPay = Long.MIN_VALUE;
    }

    private final Skill skill;
    private final String source;
    private final long intervalTicks;
    private final Map<UUID, Pot> pots = new ConcurrentHashMap<>();

    /**
     * @param skill         the skill the pot pays
     * @param source        the XP log and feed line for the payout
     * @param intervalTicks the shortest time between two payouts
     */
    public SharePot(Skill skill, String source, long intervalTicks) {
        this.skill = skill;
        this.source = source;
        this.intervalTicks = intervalTicks;
    }

    /** Adds base XP (before the paid skill's own multipliers). Ignores zero and broken numbers. */
    public void add(UUID player, double base) {
        if (!(base > 0) || !Double.isFinite(base)) {
            return;
        }
        pots.computeIfAbsent(player, id -> new Pot()).base += base;
    }

    /** Base XP collected and not paid yet. */
    public double pending(UUID player) {
        Pot pot = pots.get(player);
        return pot == null ? 0.0 : pot.base;
    }

    /**
     * Whether the pot should pay now: it holds something and the interval has passed. A dead
     * player's pot is emptied here and never pays.
     */
    public boolean due(ServerPlayer player) {
        Pot pot = pots.get(player.getUUID());
        if (pot == null) {
            return false;
        }
        if (!player.isAlive()) {
            pot.base = 0;
            return false;
        }
        return pot.base > 0 && due(player.level().getGameTime(), pot.lastPay, intervalTicks);
    }

    /** The timing rule on its own: the first payout, one after the interval, or a clock that went back. */
    public static boolean due(long now, long lastPay, long intervalTicks) {
        return lastPay == Long.MIN_VALUE || now - lastPay >= intervalTicks || now < lastPay;
    }

    /** Pays everything in the pot now, as one grant. Returns the XP added. */
    public float flush(ServerPlayer player) {
        Pot pot = pots.get(player.getUUID());
        if (pot == null || !(pot.base > 0)) {
            return 0f;
        }
        double base = pot.base;
        pot.base = 0;
        pot.lastPay = player.level().getGameTime();
        return SkillService.grant(player, skill, base, source);
    }

    /** A death: the unpaid XP goes, the payout timing stays. */
    public void wipe(UUID player) {
        Pot pot = pots.get(player);
        if (pot != null) {
            pot.base = 0;
        }
    }

    public void forget(UUID player) {
        pots.remove(player);
    }
}
