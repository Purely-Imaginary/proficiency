package dev.amman.proficiency.hearth.comfort;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Names, keys and paths for Comfort kinds (docs/HEARTH-SPEC.md, "Comfort"). The twelve shipped kinds
 * are {@link dev.amman.proficiency.hearth.HearthData#DEFAULT_COMFORT_KINDS}; a datapack may list
 * others in {@code comfort.kinds}, so every helper here takes any id and works out a name and a
 * place for it without a table.
 *
 * <p>Each kind owns a block tag {@code proficiency:comfort/<kind>} (and an entity type tag of the
 * same name for the kinds that are entities). A kind a pack adds has no shipped icon and no shipped
 * translation: {@link #shipped} tells, {@link #fallbackName} makes a readable name from the id.
 */
public final class ComfortKinds {

    /** The tag path (inside the mod's namespace) of a kind: {@code comfort/<kind>}. */
    public static final String TAG_PREFIX = "comfort/";

    /** The block tag a datapack fills with the blocks of a fireplace; the {@code fire} kind includes it. */
    public static final String FIREPLACE_TAG = "comfort/fireplace";

    private static final String LANG_PREFIX = "proficiency.hearth.comfort.kind.";
    private static final String ICON_PREFIX = "textures/gui/hearth/comfort/";

    private ComfortKinds() {
    }

    /** A kind id a tag path can carry: lower-case letters, digits, {@code _ . -} and {@code /}. */
    public static boolean valid(String kind) {
        if (kind == null || kind.isEmpty() || kind.length() > 64) {
            return false;
        }
        for (int i = 0; i < kind.length(); i++) {
            char c = kind.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '.' || c == '-'
                    || c == '/';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    /** The path of the kind's tag inside the mod's namespace: {@code comfort/<kind>}. */
    public static String tagPath(String kind) {
        return TAG_PREFIX + kind;
    }

    /** {@code proficiency.hearth.comfort.kind.<kind>}: the kind's display name. */
    public static String translationKey(String kind) {
        return LANG_PREFIX + kind;
    }

    /** {@code proficiency.hearth.comfort.kind.<kind>.desc}: one line on what counts. */
    public static String descriptionKey(String kind) {
        return LANG_PREFIX + kind + ".desc";
    }

    /** The icon's path under {@code assets/proficiency/}: {@code textures/gui/hearth/comfort/<kind>.png}. */
    public static String iconPath(String kind) {
        return ICON_PREFIX + kind + ".png";
    }

    /** Whether the kind is one of the twelve the mod ships a name, a description and an icon for. */
    public static boolean shipped(String kind) {
        return dev.amman.proficiency.hearth.HearthData.DEFAULT_COMFORT_KINDS.contains(kind);
    }

    /** A readable name for a kind with no translation: {@code "wall_art"} reads "Wall Art". */
    public static String fallbackName(String kind) {
        String leaf = kind.substring(kind.lastIndexOf('/') + 1).replace('_', ' ').replace('-', ' ').trim();
        if (leaf.isEmpty()) {
            return kind;
        }
        StringBuilder out = new StringBuilder(leaf.length());
        boolean start = true;
        for (int i = 0; i < leaf.length(); i++) {
            char c = leaf.charAt(i);
            out.append(start ? Character.toUpperCase(c) : c);
            start = c == ' ';
        }
        return out.toString();
    }

    /** The kinds in {@code all} (in its order) that are not in {@code present}: what the panel names as missing. */
    public static List<String> missing(Collection<String> present, List<String> all) {
        List<String> out = new ArrayList<>();
        for (String kind : all) {
            if (!present.contains(kind)) {
                out.add(kind);
            }
        }
        return out;
    }
}
