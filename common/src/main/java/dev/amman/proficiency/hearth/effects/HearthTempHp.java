package dev.amman.proficiency.hearth.effects;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.hearth.PlayerHearth;
import dev.amman.proficiency.hearth.TempHp;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Temp HP (docs/HEARTH-SPEC.md, "Temp HP"): absorption up to {@code absorptionPerTier x tier x max
 * health}, filled one heart at a time by Comfort regen at full health, full Warmth, out of combat, in
 * range. It lasts until damage spends it; leaving, travelling and logging out keep it.
 *
 * <ul>
 * <li>Absorption is clamped to {@code max_absorption}, 0 by default, so the Hearth keeps its own
 * persistent modifier, {@code proficiency:hearth_absorption}, sized to its cap. It is only ever grown
 * while absorption lasts (shrinking it would clamp the absorption and claw temp HP back) and removed
 * by the player loop once absorption is 0. A permanent modifier is saved with the player, so a relog
 * keeps the temp HP; a death clears it like any absorption.</li>
 * <li>No stacking: the Hearth tops up to {@code max(current absorption, its cap)}, never adding to
 * Charger's, Guardian's, Aegis's, Indomitable's or a golden apple's absorption.</li>
 * </ul>
 */
public final class HearthTempHp {

    public static final ResourceLocation MODIFIER_ID = Proficiency.id("hearth_absorption");

    private HearthTempHp() {
    }

    /** The modifier's size on this player, 0 when it is not there. */
    public static double modifier(ServerPlayer player) {
        AttributeInstance max = player.getAttribute(Attributes.MAX_ABSORPTION);
        return max == null ? 0.0 : modifier(max);
    }

    private static double modifier(AttributeInstance max) {
        AttributeModifier mod = max.getModifier(MODIFIER_ID);
        return mod == null ? 0.0 : mod.amount();
    }

    /** Whether the player carries the Hearth's temp HP right now (the HUD flag). */
    public static boolean active(ServerPlayer player) {
        return player.getAbsorptionAmount() > 0 && modifier(player) > 0;
    }

    /**
     * One fill step (one heart) towards the Hearth's cap at {@code tier}. True when absorption rose;
     * false when it is at or above the cap already (another source's bigger absorption included).
     */
    static boolean step(ServerPlayer player, PlayerHearth data, int tier, double perTier) {
        AttributeInstance max = player.getAttribute(Attributes.MAX_ABSORPTION);
        if (max == null) {
            return false;
        }
        double cap = TempHp.cap(player.getMaxHealth(), tier, perTier);
        float current = player.getAbsorptionAmount();
        double next = TempHp.next(current, cap);
        if (!(next > current)) {
            return false;
        }
        double had = modifier(max);
        double size = TempHp.modifier(had, next, cap);
        if (size > had) {
            max.addOrReplacePermanentModifier(new AttributeModifier(MODIFIER_ID, size,
                    AttributeModifier.Operation.ADD_VALUE));
        }
        data.setAbsorbModifier(Math.max(had, size));
        player.setAbsorptionAmount((float) next);
        return player.getAbsorptionAmount() > current;
    }

    /**
     * Once a second: the modifier goes once absorption is spent, and the saved copy follows the
     * attribute (the attribute is the truth; a relog or an op's command may have changed it).
     */
    static void tidy(ServerPlayer player, PlayerHearth data) {
        AttributeInstance max = player.getAttribute(Attributes.MAX_ABSORPTION);
        if (max == null) {
            return;
        }
        double had = modifier(max);
        if (had > 0 && !(player.getAbsorptionAmount() > 0)) {
            max.removeModifier(MODIFIER_ID);
            data.setAbsorbModifier(0.0);
        } else if (had != data.absorbModifier()) {
            data.setAbsorbModifier(had);
        }
    }

    /** A death: no modifier on the respawned player, nothing saved. */
    static void clear(ServerPlayer player, PlayerHearth data) {
        AttributeInstance max = player.getAttribute(Attributes.MAX_ABSORPTION);
        if (max != null && max.getModifier(MODIFIER_ID) != null) {
            max.removeModifier(MODIFIER_ID);
        }
        data.setAbsorbModifier(0.0);
    }
}
