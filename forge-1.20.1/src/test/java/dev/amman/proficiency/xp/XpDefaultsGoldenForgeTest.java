package dev.amman.proficiency.xp;

/** The golden test against the Forge 1.20.1 defaults, whose common tags live under forge:. */
class XpDefaultsGoldenForgeTest extends XpDefaultsGoldenBase {

    @Override
    String oresTag() {
        return "forge:ores";
    }

    @Override
    String glassTag() {
        return "forge:glass";
    }

    @Override
    double miningBlockXp(double hardness) {
        // Forge 1.20.1 defaults keep per_hardness 0.3; only the NeoForge/Fabric defaults were reduced.
        if (hardness < 0) return 0;
        return Math.min(5.0, 0.5 + hardness * 0.30);
    }
}
