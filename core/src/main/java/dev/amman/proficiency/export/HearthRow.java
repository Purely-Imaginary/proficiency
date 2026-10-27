package dev.amman.proficiency.export;

import dev.amman.proficiency.hearth.HearthData;
import dev.amman.proficiency.hearth.HearthLedger;
import dev.amman.proficiency.hearth.HearthPool;
import dev.amman.proficiency.hearth.HearthState;

import java.util.List;


/**
 * One Hearth as the export shows it. A Hearth has no stored owner: the owner is whoever fed it most over
 * its whole life (the ledger's lifetime ranking), null when nobody has fed it. {@code loaded} is false for a
 * Hearth whose chunk was not loaded at the time: only its place and reach radius are known then.
 */
public record HearthRow(
        String dimension,
        int x,
        int y,
        int z,
        boolean loaded,
        String ownerUuid,
        String ownerName,
        int stage,
        boolean smouldering,
        /** Design lane id, "" when unknown. */
        String lane,
        /** Highest effective charge tier over the pools. */
        int tier,
        /** Charge and effective tier per pool, in {@link HearthPool#VALUES} order. */
        double[] charge,
        int[] poolTier,
        /** Reach radius in blocks. */
        int radius,
        double reachXp,
        /** Self-build: on, the stage it builds, blocks done of total. */
        boolean buildOn,
        int buildStage,
        int buildDone,
        int buildTotal,
        int contributors) {

    /** A Hearth that is not loaded: where it is and how far it reaches. */
    public static HearthRow unloaded(String dimension, int x, int y, int z, int radius) {
        return new HearthRow(dimension, x, y, z, false, null, null, 0, false, "", 0,
                new double[HearthPool.COUNT], new int[HearthPool.COUNT], radius, 0.0,
                false, 0, 0, 0, 0);
    }

    public static HearthRow of(String dimension, int x, int y, int z, HearthState state, HearthData data,
            String lane, int radius, boolean buildOn, int buildStage, int buildDone, int buildTotal) {
        double[] charge = new double[HearthPool.COUNT];
        int[] tiers = new int[HearthPool.COUNT];
        for (HearthPool pool : HearthPool.VALUES) {
            charge[pool.ordinal()] = state.charge(pool);
            tiers[pool.ordinal()] = state.effectiveTier(pool, data);
        }
        List<HearthLedger.Rank> ranks = state.ledger().ranking(null, HearthLedger.Window.LIFETIME);
        HearthLedger.Rank owner = ranks.isEmpty() ? null : ranks.get(0);
        return new HearthRow(dimension, x, y, z, true,
                owner == null ? null : owner.player().toString(), owner == null ? null : owner.name(),
                state.stage(), state.smouldering(), lane == null ? "" : lane,
                state.smouldering() ? 0 : state.maxEffectiveTier(data), charge, tiers, radius, state.reach(),
                buildOn, buildStage, buildDone, buildTotal, ranks.size());
    }

}
