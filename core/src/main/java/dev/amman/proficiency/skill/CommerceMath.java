package dev.amman.proficiency.skill;

import java.util.List;

/**
 * The numbers behind Commerce (the 38th skill). Pure: no Minecraft types, so every rule here is unit
 * tested. {@code CommerceEvents} watches the world and pays; {@link CommerceTracker} keeps the
 * anti-farm memory; this class only decides.
 *
 * <p>Commerce is trading. XP comes from two places, both survival only and both through a real
 * player (a machine's fake player is paid nothing):
 * <ul>
 * <li>a trade taken from a villager, a wandering trader or any modded trader that uses the vanilla
 * merchant screen, scaled by the emeralds the offer involves and the trader's level, with the usual
 * first-time bonus for each profession and each item bought;</li>
 * <li>bartering gold with a piglin, per ingot, with a first-time bonus for each kind of drop. Only a
 * piglin a player handed the ingot to (a click, or an ingot the player threw) pays: a dispenser or a
 * hopper-fed piglin farm has nobody to pay.</li>
 * </ul>
 * The passive is a price discount, written into the offer's special price the way Hero of the
 * Village does it.
 */
public final class CommerceMath {

    private CommerceMath() {
    }

    // ---- XP: trades ----------------------------------------------------------------------------

    /** A trade's flat part of the XP. */
    public static final double TRADE_BASE = 2.0;
    /** A trade's part that grows with the square root of the emeralds involved. */
    public static final double TRADE_PER_ROOT = 1.6;
    /** Every level of the trader above the first adds this share of the XP. */
    public static final double TRADE_PER_LEVEL = 0.20;
    /** One trade never pays more than this before the guards. */
    public static final double TRADE_CAP = 30.0;
    /** The trader level of a wandering trader (it has none). */
    public static final int WANDERING_LEVEL = 2;
    /** The trader level of a merchant that has none (a modded trader). */
    public static final int OTHER_LEVEL = 1;

    /** What an item is worth in emeralds when it is one: an emerald 1, an emerald block 9, else 0. */
    public static int emeraldValue(String itemId, int count) {
        if (itemId == null || count <= 0) {
            return 0;
        }
        return switch (itemId) {
            case "minecraft:emerald" -> count;
            case "minecraft:emerald_block" -> count * 9;
            default -> 0;
        };
    }

    /**
     * The emeralds an offer involves: what you pay in emeralds, or, for a trade that sells to the
     * trader, what it pays you. A swap of goods for goods counts as one.
     */
    public static int emeraldsInvolved(String costA, int countA, String costB, int countB, String result,
            int resultCount) {
        int paid = emeraldValue(costA, countA) + emeraldValue(costB, countB);
        if (paid > 0) {
            return paid;
        }
        return Math.max(1, emeraldValue(result, resultCount));
    }

    /** The XP of one trade before the repeat and burst guards. */
    public static double tradeXp(int emeralds, int traderLevel) {
        int level = Math.max(1, Math.min(5, traderLevel));
        double xp = (TRADE_BASE + TRADE_PER_ROOT * Math.sqrt(Math.max(1, emeralds)))
                * (1.0 + TRADE_PER_LEVEL * (level - 1));
        return Math.min(TRADE_CAP, xp);
    }

    // ---- Anti-farm -----------------------------------------------------------------------------

    /** The same offer pays less each time within one restock cycle (one Minecraft day). */
    public static final long REPEAT_WINDOW_TICKS = 24000L;
    private static final double[] REPEAT = {1.0, 0.60, 0.35, 0.20, 0.10};

    /** The share of the XP an offer pays when it was already paid {@code prior} times in the window. */
    public static double repeatFactor(int prior) {
        if (prior <= 0) {
            return 1.0;
        }
        return REPEAT[Math.min(prior, REPEAT.length - 1)];
    }

    /** Any trade: this many paid in the burst window pay in full... */
    public static final int BURST_FREE = 20;
    /** ...a burst window is 5 minutes... */
    public static final long BURST_WINDOW_TICKS = 6000L;
    /** ...and a busier player gets BURST_FREE divided by the trades so far, never below this. */
    public static final double BURST_FLOOR = 0.10;

    public static double burstFactor(int recent) {
        return rateFactor(recent, BURST_FREE, BURST_FLOOR);
    }

    /** Barters: the first 12 in 10 minutes pay in full, then the same fade. */
    public static final int BARTER_FREE = 12;
    public static final long BARTER_WINDOW_TICKS = 12000L;
    public static final double BARTER_FLOOR = 0.10;

    public static double barterFactor(int recent) {
        return rateFactor(recent, BARTER_FREE, BARTER_FLOOR);
    }

    private static double rateFactor(int recent, int free, double floor) {
        if (recent < free) {
            return 1.0;
        }
        return Math.max(floor, (double) free / (recent + 1));
    }

    // ---- XP: bartering -------------------------------------------------------------------------

    /** XP per gold ingot a piglin takes. */
    public static final double BARTER_XP = 5.0;
    /** The player has to be within this many blocks of the piglin when the barter ends. */
    public static final double BARTER_RANGE = 32.0;
    /** A piglin keeps the memory of who gave it the ingot this long (30 s; admiring takes 6 s). */
    public static final long CREDIT_TICKS = 600L;

    public static boolean creditHolds(long given, long now) {
        return given >= 0 && now >= given && now - given <= CREDIT_TICKS;
    }

    // ---- The passive: the discount -------------------------------------------------------------

    /** The share of the passive that is price. At level 100 the passive is 0.40, so 10% off. */
    public static final double DISCOUNT_SHARE = 0.25;
    /** The passive alone never goes past this share off. */
    public static final double DISCOUNT_CAP = 0.30;
    /** The sum of every discount (passive, Haggle, cured villagers) never goes past this. */
    public static final double TOTAL_CAP = 0.60;

    /** The price share the passive takes off. */
    public static double discount(double bonus) {
        return bonus > 0 ? Math.min(DISCOUNT_CAP, bonus * DISCOUNT_SHARE) : 0.0;
    }

    /** Haggle: this much more off while it runs (20 points)... */
    public static final double HAGGLE_DISCOUNT = 0.20;
    /** ...and the Guild Master capstone adds this much to it. */
    public static final double GUILD_HAGGLE_EXTRA = 0.10;

    /** Old Debts: this much more off, per rank, from a villager you cured of zombification. */
    public static final double CURED_PER_RANK = 0.04;

    public static double curedDiscount(int rank) {
        return Math.max(0, Math.min(3, rank)) * CURED_PER_RANK;
    }

    /**
     * The whole discount on one trader's offers.
     *
     * @param passive      the passive's share (see {@link #discount})
     * @param heroWelcome  Hero's Welcome is learned and Hero of the Village is on you: the passive doubles
     * @param haggling     Haggle is running
     * @param guildMaster  the capstone is learned
     * @param curedRank    Old Debts rank when this villager was cured by you, else 0
     */
    public static double totalDiscount(double passive, boolean heroWelcome, boolean haggling, boolean guildMaster,
            int curedRank) {
        double sum = heroWelcome ? passive * 2.0 : passive;
        if (haggling) {
            sum += HAGGLE_DISCOUNT + (guildMaster ? GUILD_HAGGLE_EXTRA : 0.0);
        }
        sum += curedDiscount(curedRank);
        return Math.max(0.0, Math.min(TOTAL_CAP, sum));
    }

    /**
     * How many items to take off the first price: the share of the count, rounded, and never so many
     * that the price falls under one item.
     */
    public static int priceCut(int count, double share) {
        if (count <= 1 || share <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(count - 1, (int) Math.round(count * share)));
    }

    /**
     * Like {@link #priceCut(int, double)} but the whole price cut, vanilla's own special price (Hero of the
     * Village, a cure, good gossip) included, never goes past {@link #TOTAL_CAP} of the first price.
     *
     * @param vanillaCut the items vanilla already took off this offer's first price (0 when none)
     */
    public static int priceCut(int count, double share, int vanillaCut) {
        int cut = priceCut(count, share);
        int room = priceCut(count, TOTAL_CAP) - Math.max(0, vanillaCut);
        return Math.max(0, Math.min(cut, room));
    }

    // ---- Good Deal (the signature) -------------------------------------------------------------

    /** A Good Deal gives back this share of what you paid... */
    public static final double REFUND_BASE = 0.50;
    /** ...never more than this share, however strong the proc. */
    public static final double REFUND_CAP = 0.90;

    /** The refunded share for a proc power multiplier (Better Deals raises it). */
    public static double refundShare(double procPower) {
        return Math.min(REFUND_CAP, REFUND_BASE * Math.max(1.0, procPower));
    }

    /** How many of {@code paid} items come back: rounded up, so even a one-item price is given back. */
    public static int refund(int paid, double share) {
        if (paid <= 0 || share <= 0) {
            return 0;
        }
        return Math.min(paid, (int) Math.ceil(paid * share - 1e-9));
    }

    // ---- The tree ------------------------------------------------------------------------------

    /** Regulars: a villager you traded with restocks again this many ticks after the trade: 3600, 2400, 1200. */
    public static long regularWait(int rank) {
        return 4800L - 1200L * Math.max(1, Math.min(3, rank));
    }

    /** Regulars: at most this many extra restocks per villager per day... */
    public static final int REGULAR_PER_DAY = 2;
    /** ...a Minecraft day. */
    public static final long DAY_TICKS = 24000L;
    /** A regular is forgotten this long after the last trade. */
    public static final long REGULAR_MEMORY_TICKS = 24000L;

    public static boolean regularDue(int rank, long lastTrade, long now, int extraToday) {
        return rank > 0 && extraToday < REGULAR_PER_DAY && lastTrade >= 0 && now >= lastTrade
                && now - lastTrade >= regularWait(rank);
    }

    /** Last Piece: this chance per rank that a trade does not use up one of the offer's uses. */
    public static final double FREE_USE_PER_RANK = 0.10;

    public static double freeUseChance(int rank) {
        return Math.max(0, Math.min(3, rank)) * FREE_USE_PER_RANK;
    }

    /** Gold Rush: this chance per rank that a barter rolls its loot a second time. */
    public static final double EXTRA_ROLL_PER_RANK = 0.10;

    public static double extraRollChance(int rank) {
        return Math.max(0, Math.min(3, rank)) * EXTRA_ROLL_PER_RANK;
    }

    /** Sweetener: this chance per rank that the trader throws in an extra item. */
    public static final double SWEETENER_PER_RANK = 0.08;

    public static double sweetenerChance(int rank) {
        return Math.max(0, Math.min(3, rank)) * SWEETENER_PER_RANK;
    }

    /** A bought stack of this many or more gets a quarter more of the same; a smaller one gets a small gift. */
    public static final int BULK = 4;

    /** The extra items of a bulk trade: a quarter of the stack, at least one. */
    public static int bulkExtra(int resultCount) {
        return resultCount >= BULK ? Math.max(1, Math.round(resultCount * 0.25f)) : 0;
    }

    /** The small gifts a trader throws in with a single item. */
    public static final List<String> GIFTS = List.of("minecraft:bread", "minecraft:apple", "minecraft:cookie",
            "minecraft:baked_potato", "minecraft:sweet_berries", "minecraft:carrot");

    // ---- Skill and tree sanity -----------------------------------------------------------------

    /** The trader a villager's trades come from, as the first-time kind: the profession's name key. */
    public static String professionKind(String professionPath) {
        return "entity.minecraft.villager." + (professionPath == null || professionPath.isEmpty() ? "none" : professionPath);
    }
}
