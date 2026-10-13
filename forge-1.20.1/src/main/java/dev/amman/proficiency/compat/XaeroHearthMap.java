package dev.amman.proficiency.compat;

import dev.amman.proficiency.Proficiency;
import net.minecraftforge.fml.ModList;

/**
 * The Hearth ranges on Xaero's World Map (docs/HEARTH-REACH-SPEC.md, "Map"). Xaero builds its
 * highlighter list once per map session and seals it; this adds ours to the list of the session in
 * progress (checked every client tick, one field read when nothing changed) and asks the map to
 * redraw when the marks change. Nothing here loads a Xaero class unless Xaero's World Map is
 * installed: the work is in {@code compat.xaero.XaeroHearthHook}.
 */
public final class XaeroHearthMap {

    private static final String MOD_ID = "xaeroworldmap";
    private static Boolean present;
    private static boolean failed;

    private XaeroHearthMap() {
    }

    private static boolean present() {
        if (present == null) {
            present = ModList.get() != null && ModList.get().isLoaded(MOD_ID);
        }
        return present && !failed;
    }

    /** Every client tick: make sure the current map session draws the Hearths. */
    public static void tick() {
        if (!present()) {
            return;
        }
        try {
            dev.amman.proficiency.compat.xaero.XaeroHearthHook.tick();
        } catch (Throwable t) {
            // A Xaero version whose internals moved: switch the overlay off for this run, never crash.
            failed = true;
            Proficiency.LOG.warn("Hearth map overlay disabled: Xaero's World Map changed ({})", t.toString());
        }
    }

    /** The marks or the toggle changed: redraw. */
    public static void refresh() {
        if (!present()) {
            return;
        }
        try {
            dev.amman.proficiency.compat.xaero.XaeroHearthHook.refresh();
        } catch (Throwable t) {
            failed = true;
            Proficiency.LOG.warn("Hearth map overlay disabled: Xaero's World Map changed ({})", t.toString());
        }
    }
}
