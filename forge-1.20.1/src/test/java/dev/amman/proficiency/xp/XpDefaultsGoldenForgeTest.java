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
}
