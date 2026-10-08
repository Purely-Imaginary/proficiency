package dev.amman.proficiency;

import dev.amman.proficiency.platform.ModList;

/**
 * What else is in the instance. Checked once at load and never again, and only ever by id, so this
 * class is safe with or without any of the mods it names.
 *
 * <p>These are not cosmetic. Falling Tree already fells a whole tree from one break, which makes
 * the Timber proc redundant and, worse, double work: both would try to take the same logs. Right
 * Click Harvest takes a crop without ever firing a break event, so Farming would silently earn
 * nothing for the way most people actually harvest in this pack.
 */
public final class ModCompat {

    private static Boolean fallingTree;
    private static Boolean rightClickHarvest;
    private static Boolean veinMining;

    private ModCompat() {
    }

    private static boolean loaded(String id) {
        return ModList.get() != null && ModList.get().isLoaded(id);
    }

    /** True when another mod already fells whole trees and Timber should stand down. */
    public static boolean fallingTreeHandlesTrees() {
        if (fallingTree == null) {
            fallingTree = loaded("fallingtree");
        }
        return fallingTree;
    }

    public static boolean rightClickHarvestPresent() {
        if (rightClickHarvest == null) {
            rightClickHarvest = loaded("rightclickharvest");
        }
        return rightClickHarvest;
    }

    public static boolean veinMiningPresent() {
        if (veinMining == null) {
            veinMining = loaded("veinmining");
        }
        return veinMining;
    }
}
