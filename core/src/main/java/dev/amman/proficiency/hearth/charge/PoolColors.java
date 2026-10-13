package dev.amman.proficiency.hearth.charge;

import dev.amman.proficiency.client.SkillPalette;
import dev.amman.proficiency.hearth.HearthPool;
import dev.amman.proficiency.skill.SkillCategory;

/**
 * One colour per pool, ARGB, for the ledger's stacked bars (the chat table and the panel tab) and
 * any other place that tells pools apart by colour. Each pool takes the category that feeds it, as
 * its icon does (docs/hearth-build-log/art2.md); Regen and Fortified are both Construction there,
 * so Fortified takes a stone slate instead, or the two would merge in a bar.
 */
public final class PoolColors {

    /** Fortified: dressed stone, apart from Regen's Construction teal. */
    public static final int FORTIFIED = 0xFF8C98AB;

    private PoolColors() {
    }

    public static int argb(HearthPool pool) {
        return switch (pool) {
            case HEARTY -> SkillPalette.accent(SkillCategory.GATHERING);
            case REGEN -> SkillPalette.accent(SkillCategory.CONSTRUCTION);
            case FORTIFIED -> FORTIFIED;
            case WORKSHOP -> SkillPalette.accent(SkillCategory.CRAFTING);
            case HOMECOMING -> SkillPalette.accent(SkillCategory.SOCIAL);
        };
    }

    /** The colour without alpha, as chat text colours take it. */
    public static int rgb(HearthPool pool) {
        return argb(pool) & 0xFFFFFF;
    }
}
