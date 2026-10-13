package dev.amman.proficiency.compat.xaero;

import dev.amman.proficiency.hearth.HearthPos;
import dev.amman.proficiency.hearth.client.HearthMapMarks;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import xaero.map.highlight.ChunkHighlighter;

import java.util.List;
import java.util.Map;

/**
 * The Hearths' ranges as chunk highlights on Xaero's World Map: a chunk counts when its centre is
 * inside a Hearth's radius. Ember-orange fill, a stronger edge where the range ends. Colours are
 * packed blue, green, red, alpha from the high byte down (seen in a real client: red-first came out blue).
 */
final class HearthHighlighter extends ChunkHighlighter {

    /** Ember orange (#E8742A) at a quarter, the Hearth family's colour. */
    private static final int FILL = 0x2A74E844;
    /** A brighter gold (#FFB347), nearly solid, along the range's edge. */
    private static final int EDGE = 0x47B3FFD0;

    HearthHighlighter() {
        super(true);
    }

    private static Map<Long, Integer> marks(ResourceKey<Level> dimension) {
        return HearthMapMarks.in(dimension.location().toString());
    }

    /** The radius of the Hearth whose range holds this chunk's centre, or -1. */
    private static int covering(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        double x = chunkX * 16 + 8;
        double z = chunkZ * 16 + 8;
        for (Map.Entry<Long, Integer> e : marks(dimension).entrySet()) {
            double dx = x - (HearthPos.unpackX(e.getKey()) + 0.5);
            double dz = z - (HearthPos.unpackZ(e.getKey()) + 0.5);
            double r = e.getValue();
            if (dx * dx + dz * dz <= r * r) {
                return e.getValue();
            }
        }
        return -1;
    }

    @Override
    public boolean chunkIsHighlit(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        return covering(dimension, chunkX, chunkZ) >= 0;
    }

    @Override
    public boolean regionHasHighlights(ResourceKey<Level> dimension, int regionX, int regionZ) {
        double x0 = regionX * 512.0;
        double z0 = regionZ * 512.0;
        for (Map.Entry<Long, Integer> e : marks(dimension).entrySet()) {
            double hx = HearthPos.unpackX(e.getKey()) + 0.5;
            double hz = HearthPos.unpackZ(e.getKey()) + 0.5;
            double nx = Math.max(x0, Math.min(hx, x0 + 512));
            double nz = Math.max(z0, Math.min(hz, z0 + 512));
            double r = e.getValue() + 16;
            if ((nx - hx) * (nx - hx) + (nz - hz) * (nz - hz) <= r * r) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int calculateRegionHash(ResourceKey<Level> dimension, int regionX, int regionZ) {
        int hash = HearthMapMarks.version();
        if (regionHasHighlights(dimension, regionX, regionZ)) {
            for (Map.Entry<Long, Integer> e : marks(dimension).entrySet()) {
                hash = hash * 31 + Long.hashCode(e.getKey()) * 17 + e.getValue();
            }
        }
        return hash;
    }

    @Override
    protected int[] getColors(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        if (!chunkIsHighlit(dimension, chunkX, chunkZ)) {
            return null;
        }
        // resultStore: centre, then the sides north, east, south, west (Xaero's order).
        resultStore[0] = FILL;
        resultStore[1] = chunkIsHighlit(dimension, chunkX, chunkZ - 1) ? FILL : EDGE;
        resultStore[2] = chunkIsHighlit(dimension, chunkX + 1, chunkZ) ? FILL : EDGE;
        resultStore[3] = chunkIsHighlit(dimension, chunkX, chunkZ + 1) ? FILL : EDGE;
        resultStore[4] = chunkIsHighlit(dimension, chunkX - 1, chunkZ) ? FILL : EDGE;
        return resultStore;
    }

    @Override
    public Component getChunkHighlightSubtleTooltip(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        int radius = covering(dimension, chunkX, chunkZ);
        return radius < 0 ? null : Component.translatable("proficiency.hearth.map.tooltip", radius);
    }

    @Override
    public Component getChunkHighlightBluntTooltip(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        return getChunkHighlightSubtleTooltip(dimension, chunkX, chunkZ);
    }

    @Override
    public void addMinimapBlockHighlightTooltips(List<Component> list, ResourceKey<Level> dimension, int x, int z,
            int y) {
    }
}
