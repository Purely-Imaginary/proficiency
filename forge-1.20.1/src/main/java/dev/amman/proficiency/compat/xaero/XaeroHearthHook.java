package dev.amman.proficiency.compat.xaero;

import dev.amman.proficiency.hearth.client.HearthMapMarks;
import xaero.map.MapProcessor;
import xaero.map.WorldMapSession;
import xaero.map.highlight.AbstractHighlighter;
import xaero.map.highlight.HighlighterRegistry;
import xaero.map.world.MapDimension;
import xaero.map.world.MapWorld;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Only loaded when Xaero's World Map is installed (see {@code compat.XaeroHearthMap}). */
public final class XaeroHearthHook {

    private static final HearthHighlighter HIGHLIGHTER = new HearthHighlighter();
    private static HighlighterRegistry injectedInto;
    private static int drawnVersion = Integer.MIN_VALUE;
    private static Field listField;

    private XaeroHearthHook() {
    }

    public static void tick() throws ReflectiveOperationException {
        WorldMapSession session = WorldMapSession.getCurrentSession();
        if (session == null || session.getMapProcessor() == null) {
            return;
        }
        MapProcessor processor = session.getMapProcessor();
        HighlighterRegistry registry = processor.getHighlighterRegistry();
        if (registry != null && registry != injectedInto) {
            inject(registry);
            injectedInto = registry;
            drawnVersion = Integer.MIN_VALUE;
        }
        if (drawnVersion != HearthMapMarks.version()) {
            drawnVersion = HearthMapMarks.version();
            redraw(processor);
        }
    }

    public static void refresh() {
        drawnVersion = Integer.MIN_VALUE;
    }

    @SuppressWarnings("unchecked")
    private static void inject(HighlighterRegistry registry) throws ReflectiveOperationException {
        if (listField == null) {
            listField = HighlighterRegistry.class.getDeclaredField("highlighters");
            listField.setAccessible(true);
        }
        List<AbstractHighlighter> current = (List<AbstractHighlighter>) listField.get(registry);
        if (current.contains(HIGHLIGHTER)) {
            return;
        }
        List<AbstractHighlighter> with = new ArrayList<>(current);
        with.add(HIGHLIGHTER);
        listField.set(registry, Collections.unmodifiableList(with));
    }

    private static void redraw(MapProcessor processor) {
        MapWorld world = processor.getMapWorld();
        if (world == null) {
            return;
        }
        // What Open Parties and Claims does on a claim change: the handler drops its cached region
        // hashes and tells the dimension's regions, which re-apply highlights on their next draw.
        for (MapDimension dimension : world.getDimensionsList()) {
            if (dimension.getHighlightHandler() != null) {
                dimension.getHighlightHandler().clearCachedHashes();
            }
        }
    }
}
