package dev.amman.proficiency.client;

import java.util.List;
import java.util.function.Predicate;

/**
 * The pinned skills, parsed once per change of the config's comma list (1.34.0). The Waywheel asks for every
 * token every frame and the belt every HUD frame; before this each ask split and checked the string again.
 * The list is kept until the string differs; the check of a known id runs only then.
 */
public final class WaywheelPins {

    private final Predicate<String> known;
    private String csv;
    private List<String> pins = List.of();

    public WaywheelPins(Predicate<String> known) {
        this.known = known;
    }

    /** The pinned ids for {@code csv} ({@link WaywheelMath#pins}), from the cache while it is the same text. */
    public List<String> get(String csv) {
        String s = csv == null ? "" : csv;
        if (!s.equals(this.csv)) {
            this.csv = s;
            this.pins = List.copyOf(WaywheelMath.pins(s, known));
        }
        return pins;
    }

    public boolean pinned(String csv, String id) {
        return get(csv).contains(id);
    }
}
