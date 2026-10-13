package dev.amman.proficiency.hearth.comfort;

/**
 * What counts as one entry of Character (docs/HEARTH-SPEC.md, "Comfort"). A block counts as the
 * item that places it ({@code block.asItem()}), so a torch and its wall twin are one entry; the
 * common code works that id out. The optional family folding lives here, pure:
 *
 * <ul>
 * <li>a block in a tag {@code proficiency:hearth/character_family/<name>} is one entry for the whole
 * tag: {@link #familyEntry(String)};</li>
 * <li>a block in {@code #proficiency:hearth/character_family} itself is folded with its numbered
 * variants: {@code chipped:stone_bricks_7} counts as {@code chipped:stone_bricks}
 * ({@link #foldNumbered}).</li>
 * </ul>
 */
public final class CharacterKeys {

    /** The tag (inside the mod's namespace) that marks blocks whose numbered variants fold into one. */
    public static final String FAMILY_TAG = "hearth/character_family";

    /** The folder of per-family tags: every member of {@code hearth/character_family/<name>} is one entry. */
    public static final String FAMILY_FOLDER = FAMILY_TAG + "/";

    /** The tag of blocks that are bulk filler or the Hearth itself and never count. */
    public static final String IGNORE_TAG = "hearth/character_ignore";

    private CharacterKeys() {
    }

    /**
     * Folds a numbered variant into its family: a trailing {@code _<digits>} is dropped
     * ({@code chipped:acacia_planks_12} to {@code chipped:acacia_planks}). An id with no such
     * suffix, or with nothing before it, is unchanged.
     */
    public static String foldNumbered(String id) {
        int end = id.length();
        int i = end;
        while (i > 0 && id.charAt(i - 1) >= '0' && id.charAt(i - 1) <= '9') {
            i--;
        }
        if (i == end || i < 2 || id.charAt(i - 1) != '_' || id.charAt(i - 2) == ':') {
            return id;
        }
        return id.substring(0, i - 1);
    }

    /** The entry a per-family tag makes of all its members: {@code #<tag id>}. */
    public static String familyEntry(String tagId) {
        return "#" + tagId;
    }

    /** Whether a tag path (inside the mod's namespace) is one of the per-family tags. */
    public static boolean isFamilyTag(String path) {
        return path.startsWith(FAMILY_FOLDER) && path.length() > FAMILY_FOLDER.length();
    }
}
