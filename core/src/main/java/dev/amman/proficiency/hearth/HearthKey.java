package dev.amman.proficiency.hearth;

/**
 * A Hearth's key as the block entity writes it ({@code "dimension|x,y,z"}): a Chronicle page and a
 * ledger remember the Hearth by it, and a rating finds the Hearth again from it.
 */
public record HearthKey(String dimension, int x, int y, int z) {

    /** The key's parts, or null when the text is not a Hearth key. */
    public static HearthKey parse(String key) {
        if (key == null) {
            return null;
        }
        int bar = key.lastIndexOf('|');
        if (bar <= 0) {
            return null;
        }
        String[] parts = key.substring(bar + 1).split(",", -1);
        if (parts.length != 3) {
            return null;
        }
        try {
            return new HearthKey(key.substring(0, bar), Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()), Integer.parseInt(parts[2].trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public long packed() {
        return HearthPos.pack(x, y, z);
    }

    @Override
    public String toString() {
        return dimension + "|" + x + "," + y + "," + z;
    }
}
