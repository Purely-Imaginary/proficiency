package dev.amman.proficiency.hearth.effects;

import dev.amman.proficiency.hearth.HearthAttachments;
import dev.amman.proficiency.hearth.PlayerHearth;
import dev.amman.proficiency.hearth.TempHp;
import net.minecraft.server.level.ServerPlayer;

/**
 * Temp HP (docs/HEARTH-SPEC.md, "Temp HP"): absorption up to {@code absorptionPerTier x tier x max
 * health}, filled one heart at a time by Comfort regen at full health, full Warmth, out of combat, in
 * range. It lasts until damage spends it; leaving, travelling and logging out keep it.
 *
 * <p>Forge 1.20.1 (docs/HEARTH-FORGE-CONTRACT.md, API map): 1.20.1 has no {@code max_absorption}
 * attribute and does not cap absorption, so there is no modifier to keep. The absorption is set
 * directly, and the Hearth's part of it is a saved number, {@link PlayerHearth#absorbShare}:
 *
 * <ul>
 * <li>No stacking: the Hearth tops up to {@code max(current absorption, its cap)}, never adding to
 * Charger's, Guardian's, Aegis's, Indomitable's or a golden apple's absorption. Only what it added is
 * its share.</li>
 * <li>A hit spends the Hearth's share first ({@link #spent}, from the port's damage event just before
 * absorption soaks it), so another source's absorption is not counted as the Hearth's.</li>
 * <li>The share never exceeds the absorption ({@link #tidy}, once a second) and is 0 once absorption
 * is gone. The player's absorption and the share are both saved, so a relog keeps the temp HP; a
 * death clears both.</li>
 * <li>Lower max health keeps what is there: nothing caps absorption on 1.20.1 (1.21.1 gets the same
 * by never shrinking its modifier while absorption lasts).</li>
 * </ul>
 */
public final class HearthTempHp {

    private HearthTempHp() {
    }

    /**
     * The Hearth's part of this player's absorption (the saved share), never more than the absorption
     * itself; 0 when it has none. It plays the role of 1.21.1's {@code modifier(player)}, which reads a
     * {@code max_absorption} modifier that 1.20.1 does not have.
     */
    public static double share(ServerPlayer player) {
        return Math.min(HearthAttachments.of(player).absorbShare(), Math.max(0f, player.getAbsorptionAmount()));
    }

    /** Whether the player carries the Hearth's temp HP right now (the HUD flag). */
    public static boolean active(ServerPlayer player) {
        return player.getAbsorptionAmount() > 0 && HearthAttachments.of(player).absorbShare() > 0;
    }

    /**
     * One fill step (one heart) towards the Hearth's cap at {@code tier}. True when absorption rose;
     * false when it is at or above the cap already (another source's bigger absorption included).
     */
    static boolean step(ServerPlayer player, PlayerHearth data, int tier, double perTier) {
        double cap = TempHp.cap(player.getMaxHealth(), tier, perTier);
        float current = player.getAbsorptionAmount();
        double next = TempHp.next(current, cap);
        if (!(next > current)) {
            return false;
        }
        player.setAbsorptionAmount((float) next);
        float now = player.getAbsorptionAmount();
        if (!(now > current)) {
            return false;
        }
        // What the Hearth added is its part; it never exceeds what the player now has.
        data.setAbsorbShare(Math.min(now, Math.min(data.absorbShare(), current) + (now - current)));
        return true;
    }

    /** A hit is about to take {@code soaked} of the absorption: the Hearth's share goes first. */
    public static void spent(ServerPlayer player, float soaked) {
        if (!(soaked > 0)) {
            return;
        }
        PlayerHearth data = HearthAttachments.of(player);
        if (data.absorbShare() > 0) {
            data.setAbsorbShare(Math.max(0.0, data.absorbShare() - soaked));
        }
    }

    /**
     * Once a second: the share follows the absorption down (spent by something the damage event did
     * not see, an expiring Absorption effect, an operator's command), and is 0 once absorption is 0.
     */
    static void tidy(ServerPlayer player, PlayerHearth data) {
        float absorption = player.getAbsorptionAmount();
        if (data.absorbShare() > 0 && !(absorption > 0)) {
            data.setAbsorbShare(0.0);
        } else if (data.absorbShare() > absorption) {
            data.setAbsorbShare(absorption);
        }
    }

    /** A death: nothing of the Hearth's absorption carries over. */
    static void clear(ServerPlayer player, PlayerHearth data) {
        data.setAbsorbShare(0.0);
    }
}
