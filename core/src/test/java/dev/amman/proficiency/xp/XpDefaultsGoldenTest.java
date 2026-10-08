package dev.amman.proficiency.xp;

/** The golden test against the 1.21.1 defaults (NeoForge and Fabric), whose tag namespace is c:. */
class XpDefaultsGoldenTest extends XpDefaultsGoldenBase {

    @Override
    String oresTag() {
        return "c:ores";
    }

    @Override
    String glassTag() {
        return "c:glass_blocks";
    }
}
