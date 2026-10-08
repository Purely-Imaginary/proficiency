package dev.amman.proficiency.skill;

/**
 * Pure rules for the first-time bonus: which source keys are the same kind, and the tier maths.
 * No registry access here, so it is unit tested; {@link FirstTimeTiers} does the lookup.
 */
public final class FirstTimeKinds {

    /** The XP-log source of a first-time bonus is this prefix plus the kind key. */
    public static final String FIRST_PREFIX = "first|";

    private FirstTimeKinds() {
    }

    /**
     * The canonical kind of a source key, so a variant is not new. Only these rules, and only on
     * block keys ({@code block.<namespace>.<path>}, any namespace):
     * <ul>
     *   <li>a {@code deepslate_} prefix on an ore is dropped: deepslate_iron_ore is iron_ore;</li>
     *   <li>a {@code stripped_} prefix is dropped;</li>
     *   <li>a {@code _wood} suffix becomes {@code _log}, and {@code _hyphae} becomes {@code _stem}.</li>
     * </ul>
     * Anything else comes back unchanged. Conservative on purpose: a wrong merge hides a bonus.
     */
    public static String canonical(String source) {
        if (source == null || !source.startsWith("block.")) {
            return source;
        }
        int dot = source.indexOf('.', "block.".length());
        if (dot < 0 || dot == source.length() - 1) {
            return source;
        }
        String head = source.substring(0, dot + 1);
        String path = source.substring(dot + 1);
        if (path.startsWith("deepslate_") && path.endsWith("_ore")) {
            path = path.substring("deepslate_".length());
        }
        if (path.startsWith("stripped_") && path.length() > "stripped_".length()) {
            path = path.substring("stripped_".length());
        }
        if (path.endsWith("_wood") && path.length() > "_wood".length()) {
            path = path.substring(0, path.length() - "_wood".length()) + "_log";
        } else if (path.endsWith("_hyphae") && path.length() > "_hyphae".length()) {
            path = path.substring(0, path.length() - "_hyphae".length()) + "_stem";
        }
        return head + path;
    }

    /**
     * The kind plus every raw source key that {@link #canonical} folds into it. Keys stored before
     * families existed are raw, so any of these being seen means the kind was already paid.
     */
    public static java.util.List<String> legacyVariants(String kind) {
        java.util.List<String> out = new java.util.ArrayList<>();
        out.add(kind);
        if (kind == null || !kind.startsWith("block.")) {
            return out;
        }
        int dot = kind.indexOf('.', "block.".length());
        if (dot < 0 || dot == kind.length() - 1) {
            return out;
        }
        String head = kind.substring(0, dot + 1);
        String path = kind.substring(dot + 1);
        java.util.List<String> paths = new java.util.ArrayList<>();
        paths.add(path);
        if (path.endsWith("_log") && path.length() > "_log".length()) {
            paths.add(path.substring(0, path.length() - "_log".length()) + "_wood");
        } else if (path.endsWith("_stem") && path.length() > "_stem".length()) {
            paths.add(path.substring(0, path.length() - "_stem".length()) + "_hyphae");
        }
        java.util.List<String> raws = new java.util.ArrayList<>();
        for (String candidate : paths) {
            raws.add(candidate);
            raws.add("stripped_" + candidate);
            if (candidate.endsWith("_ore")) {
                raws.add("deepslate_" + candidate);
            }
        }
        for (String raw : raws) {
            String key = head + raw;
            // Only keys that really fold into this kind, so a guess can never over-match.
            if (!out.contains(key) && kind.equals(canonical(key))) {
                out.add(key);
            }
        }
        return out;
    }

    /** Masonry and Decorating: every block is a new kind there, so they get a daily cap. */
    public static boolean isBuildSkill(Skill skill) {
        return skill == Skill.MASONRY || skill == Skill.DECORATING;
    }

    /** Ores x2, the rare ones x5. */
    public static double blockMultiplier(boolean ore, boolean premiumOre) {
        return premiumOre ? 5.0 : ore ? 2.0 : 1.0;
    }

    /** A notable boss x10, else by max health: 100 or more x5, 40 or more x3. */
    public static double entityMultiplier(boolean notableBoss, double maxHealth) {
        if (notableBoss) {
            return 10.0;
        }
        return maxHealth >= 100.0 ? 5.0 : maxHealth >= 40.0 ? 3.0 : 1.0;
    }

    /**
     * By item rarity: uncommon x2, rare x3, epic x5. Takes the rarity's enum name ({@code
     * Rarity.name()}), so this class needs no Minecraft; null or anything else is x1.
     */
    public static double itemMultiplier(String rarity) {
        if (rarity == null) {
            return 1.0;
        }
        return switch (rarity) {
            case "UNCOMMON" -> 2.0;
            case "RARE" -> 3.0;
            case "EPIC" -> 5.0;
            default -> 1.0;
        };
    }
}
