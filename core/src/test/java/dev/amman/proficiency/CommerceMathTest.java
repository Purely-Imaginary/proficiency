package dev.amman.proficiency;

import dev.amman.proficiency.perk.PerkEffect;
import dev.amman.proficiency.perk.Synergies;
import dev.amman.proficiency.perk.Synergy;
import dev.amman.proficiency.perk.Talent;
import dev.amman.proficiency.perk.Talents;
import dev.amman.proficiency.skill.CommerceMath;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.ProcFx;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillCategory;
import dev.amman.proficiency.skill.SkillMath;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Commerce skill: XP, the guards' numbers, the discount, Good Deal, the tree and its synergies. */
class CommerceMathTest {

    // ---- XP ------------------------------------------------------------------------------------

    @Test
    void emeraldsAreCountedFromTheOfferOnTheEmeraldSide() {
        assertEquals(1, CommerceMath.emeraldValue("minecraft:emerald", 1));
        assertEquals(27, CommerceMath.emeraldValue("minecraft:emerald_block", 3));
        assertEquals(0, CommerceMath.emeraldValue("minecraft:paper", 24));
        assertEquals(0, CommerceMath.emeraldValue(null, 5));
        // Buying: you pay 5 emeralds.
        assertEquals(5, CommerceMath.emeraldsInvolved("minecraft:emerald", 5, "", 0, "minecraft:bread", 6));
        // Selling: 24 paper for one emerald is one emerald's worth.
        assertEquals(1, CommerceMath.emeraldsInvolved("minecraft:paper", 24, "", 0, "minecraft:emerald", 1));
        // A swap of goods counts as one.
        assertEquals(1, CommerceMath.emeraldsInvolved("minecraft:book", 1, "", 0, "somemod:thing", 1));
        // A price in both an emerald and a book is the emerald part.
        assertEquals(20, CommerceMath.emeraldsInvolved("minecraft:emerald", 20, "minecraft:book", 1,
                "minecraft:enchanted_book", 1));
    }

    @Test
    void aTradePaysByTheSquareRootOfTheEmeraldsAndTheTradersLevel() {
        assertEquals(3.6, CommerceMath.tradeXp(1, 1), 1e-9);
        assertEquals(2.0 + 1.6 * 4.0, CommerceMath.tradeXp(16, 1), 1e-9);
        assertEquals(CommerceMath.tradeXp(16, 1) * 1.8, CommerceMath.tradeXp(16, 5), 1e-9);
        assertTrue(CommerceMath.tradeXp(1, 5) > CommerceMath.tradeXp(1, 1));
        assertTrue(CommerceMath.tradeXp(64, 1) > CommerceMath.tradeXp(8, 1));
        assertEquals(CommerceMath.tradeXp(1, 1), CommerceMath.tradeXp(0, 0), 1e-9, "never under one emerald, level one");
        assertEquals(CommerceMath.tradeXp(9, 5), CommerceMath.tradeXp(9, 99), 1e-9, "the level stops at five");
        assertEquals(CommerceMath.TRADE_CAP, CommerceMath.tradeXp(5000, 5), 1e-9, "capped");
    }

    // ---- Anti-farm numbers ---------------------------------------------------------------------

    @Test
    void theSameOfferPaysLessAndLessDownToATenth() {
        assertEquals(1.0, CommerceMath.repeatFactor(0), 1e-9);
        assertEquals(0.60, CommerceMath.repeatFactor(1), 1e-9);
        assertEquals(0.35, CommerceMath.repeatFactor(2), 1e-9);
        assertEquals(0.20, CommerceMath.repeatFactor(3), 1e-9);
        assertEquals(0.10, CommerceMath.repeatFactor(4), 1e-9);
        assertEquals(0.10, CommerceMath.repeatFactor(400), 1e-9);
        for (int i = 0; i < 10; i++) {
            assertTrue(CommerceMath.repeatFactor(i + 1) <= CommerceMath.repeatFactor(i), "never rises: " + i);
        }
    }

    @Test
    void aBurstOfTradesFadesAfterTwentyAndNeverBelowATenth() {
        assertEquals(1.0, CommerceMath.burstFactor(0), 1e-9);
        assertEquals(1.0, CommerceMath.burstFactor(19), 1e-9);
        assertEquals(20.0 / 21.0, CommerceMath.burstFactor(20), 1e-9);
        assertEquals(20.0 / 41.0, CommerceMath.burstFactor(40), 1e-9);
        assertEquals(CommerceMath.BURST_FLOOR, CommerceMath.burstFactor(5000), 1e-9);
    }

    @Test
    void barteringFadesAfterTwelveInTenMinutes() {
        assertEquals(1.0, CommerceMath.barterFactor(11), 1e-9);
        assertEquals(12.0 / 13.0, CommerceMath.barterFactor(12), 1e-9);
        assertEquals(CommerceMath.BARTER_FLOOR, CommerceMath.barterFactor(10_000), 1e-9);
    }

    @Test
    void aBarterCreditHoldsFor30SecondsAndNotBackwards() {
        assertTrue(CommerceMath.creditHolds(100, 100));
        assertTrue(CommerceMath.creditHolds(100, 100 + CommerceMath.CREDIT_TICKS));
        assertFalse(CommerceMath.creditHolds(100, 101 + CommerceMath.CREDIT_TICKS));
        assertFalse(CommerceMath.creditHolds(100, 99), "a clock that went back");
        assertFalse(CommerceMath.creditHolds(-1, 5));
    }

    // ---- The discount --------------------------------------------------------------------------

    @Test
    void thePassiveIsTenPercentAtLevelOneHundredAndCapped() {
        assertEquals(0.0, CommerceMath.discount(0.0), 1e-12);
        assertEquals(0.0, CommerceMath.discount(-1.0), 1e-12);
        assertEquals(0.10, CommerceMath.discount(SkillMath.bonus(Skill.COMMERCE, 100)), 1e-9);
        assertEquals(CommerceMath.DISCOUNT_CAP, CommerceMath.discount(50.0), 1e-12);
    }

    @Test
    void theWholeDiscountAddsUpAndStopsAtSixtyPercent() {
        assertEquals(0.10, CommerceMath.totalDiscount(0.10, false, false, false, 0), 1e-9);
        assertEquals(0.20, CommerceMath.totalDiscount(0.10, true, false, false, 0), 1e-9, "Hero's Welcome doubles the passive");
        assertEquals(0.10 + 0.20, CommerceMath.totalDiscount(0.10, false, true, false, 0), 1e-9, "Haggle");
        assertEquals(0.10 + 0.30, CommerceMath.totalDiscount(0.10, false, true, true, 0), 1e-9, "Guild Master widens Haggle");
        assertEquals(0.10 + 0.12, CommerceMath.totalDiscount(0.10, false, false, false, 3), 1e-9, "Old Debts rank 3");
        assertEquals(0.10 + 0.12, CommerceMath.totalDiscount(0.10, false, false, false, 9), 1e-9, "rank is capped at 3");
        assertEquals(CommerceMath.TOTAL_CAP, CommerceMath.totalDiscount(0.30, true, true, true, 3), 1e-9);
        assertEquals(0.0, CommerceMath.totalDiscount(0.0, false, false, false, 0), 1e-12);
    }

    @Test
    void aPriceCutRoundsAndNeverTakesTheLastItem() {
        assertEquals(0, CommerceMath.priceCut(1, 0.5));
        assertEquals(0, CommerceMath.priceCut(4, 0.10));
        assertEquals(1, CommerceMath.priceCut(5, 0.10));
        assertEquals(6, CommerceMath.priceCut(32, 0.20), "32 x 0.20 = 6.4");
        assertEquals(3, CommerceMath.priceCut(4, 0.99), "a price of four goes no lower than one");
        assertEquals(63, CommerceMath.priceCut(64, 5.0));
        assertEquals(0, CommerceMath.priceCut(64, 0.0));
        assertEquals(0, CommerceMath.priceCut(64, -1.0));
    }

    @Test
    void theWholeCutIncludingVanillasStaysUnderTheTotalCap() {
        // 24 emeralds, Haggle and the tree want 15, vanilla's Hero of the Village already took 8: room is 14 - 8.
        assertEquals(6, CommerceMath.priceCut(24, 0.60, 8));
        assertEquals(CommerceMath.priceCut(24, 0.10), CommerceMath.priceCut(24, 0.10, 0), "no vanilla cut changes nothing");
        assertEquals(0, CommerceMath.priceCut(24, 0.60, 20), "vanilla already past the cap: ours adds nothing");
        assertEquals(0, CommerceMath.priceCut(24, 0.60, 14));
        assertEquals(2, CommerceMath.priceCut(24, 0.10, 12), "a small cut that fits is kept");
        assertEquals(14, CommerceMath.priceCut(24, 0.60, -5), "a surcharge is not room");
        assertEquals(0, CommerceMath.priceCut(1, 0.60, 0));
        for (int count = 1; count <= 64; count++) {
            for (double share = 0.0; share <= 0.6001; share += 0.05) {
                assertEquals(CommerceMath.priceCut(count, share), CommerceMath.priceCut(count, share, 0),
                        "no vanilla cut must match the plain cut at " + count + " x " + share);
            }
        }
        assertEquals(2, CommerceMath.priceCut(3, 0.60, 0), "three at the cap prices at one");
    }

    // ---- Good Deal -----------------------------------------------------------------------------

    @Test
    void aGoodDealGivesHalfBackAndNeverMoreThanNinetyPercent() {
        assertEquals(0.50, CommerceMath.refundShare(1.0), 1e-9);
        assertEquals(0.65, CommerceMath.refundShare(1.3), 1e-9);
        assertEquals(0.50, CommerceMath.refundShare(0.2), 1e-9, "never below the base");
        assertEquals(CommerceMath.REFUND_CAP, CommerceMath.refundShare(9.0), 1e-9);
        assertEquals(1, CommerceMath.refund(1, 0.5), "a one-item price comes back whole");
        assertEquals(2, CommerceMath.refund(3, 0.5));
        assertEquals(8, CommerceMath.refund(16, 0.5));
        assertEquals(16, CommerceMath.refund(16, 5.0), "never more than was paid");
        assertEquals(0, CommerceMath.refund(0, 0.5));
        assertEquals(0, CommerceMath.refund(5, 0.0));
    }

    // ---- The tree's numbers --------------------------------------------------------------------

    @Test
    void regularsRestockEarlierWithEachRankAndTwiceADayAtMost() {
        assertEquals(3600L, CommerceMath.regularWait(1));
        assertEquals(2400L, CommerceMath.regularWait(2));
        assertEquals(1200L, CommerceMath.regularWait(3));
        assertFalse(CommerceMath.regularDue(0, 0L, 99_999L, 0), "no rank, no restock");
        assertFalse(CommerceMath.regularDue(3, 0L, 1199L, 0));
        assertTrue(CommerceMath.regularDue(3, 0L, 1200L, 0));
        assertTrue(CommerceMath.regularDue(3, 0L, 1200L, 1));
        assertFalse(CommerceMath.regularDue(3, 0L, 99_999L, 2), "two extra restocks used today");
        assertFalse(CommerceMath.regularDue(3, 500L, 400L, 0), "a clock that went back");
        assertFalse(CommerceMath.regularDue(1, 0L, 2400L, 0), "rank 1 waits three minutes");
    }

    @Test
    void theChancesGrowPerRankAndStopAtRankThree() {
        assertEquals(0.30, CommerceMath.freeUseChance(3), 1e-9);
        assertEquals(0.30, CommerceMath.freeUseChance(7), 1e-9);
        assertEquals(0.0, CommerceMath.freeUseChance(-1), 1e-9);
        assertEquals(0.30, CommerceMath.extraRollChance(3), 1e-9);
        assertEquals(0.24, CommerceMath.sweetenerChance(3), 1e-9);
        assertEquals(0.08, CommerceMath.sweetenerChance(1), 1e-9);
    }

    @Test
    void aBulkBuyGetsAQuarterMoreAndASingleItemGetsAGift() {
        assertEquals(0, CommerceMath.bulkExtra(1));
        assertEquals(0, CommerceMath.bulkExtra(3));
        assertEquals(1, CommerceMath.bulkExtra(4));
        assertEquals(4, CommerceMath.bulkExtra(16));
        assertEquals(16, CommerceMath.bulkExtra(64));
        assertFalse(CommerceMath.GIFTS.isEmpty());
        for (String gift : CommerceMath.GIFTS) {
            assertTrue(gift.startsWith("minecraft:"), gift);
        }
    }

    @Test
    void professionKindsAreVillagerTranslationKeys() {
        assertEquals("entity.minecraft.villager.farmer", CommerceMath.professionKind("farmer"));
        assertEquals("entity.minecraft.villager.none", CommerceMath.professionKind(null));
        assertEquals("entity.minecraft.villager.none", CommerceMath.professionKind(""));
    }

    // ---- The skill, its tree and its synergies ---------------------------------------------------

    @Test
    void commerceIsSkillThirtyEightAndASocialOne() {
        assertSame(Skill.COMMERCE, Skill.VALUES[Skill.VALUES.length - 1]);
        assertSame(Skill.COMMERCE, Skill.VALUES[37]);
        assertSame(Skill.RIDING, Skill.VALUES[36]);
        assertSame(SkillCategory.SOCIAL, Skill.COMMERCE.category());
        assertSame(Skill.COMMERCE, Skill.byId("commerce"));
        assertNotNull(ProcFx.recipe(Skill.COMMERCE));
    }

    @Test
    void theTreeFillsAtLevelOneHundredForAtMostOneHundredPoints() {
        int cost = Talents.fullTreeCost(Skill.COMMERCE);
        assertTrue(cost <= 100, "costs " + cost);
        PlayerSkills skills = new PlayerSkills();
        skills.setLevel(Skill.COMMERCE, 100);
        skills.fillTree(Skill.COMMERCE);
        Talents.of(Skill.COMMERCE).forEach(talent -> assertTrue(skills.isFull(talent), talent.key()));
    }

    @Test
    void theTreeHasSevenBespokeMechanicsAndNoneIsACopy() {
        Set<String> mine = new HashSet<>();
        for (Talent talent : Talents.of(Skill.COMMERCE)) {
            if (talent.special() != null) {
                mine.add(talent.special());
            }
        }
        assertEquals(Set.of("regulars", "free_use", "gold_rush", "sweetener", "cure_bonus", "heros_welcome",
                "guild_master"), mine);
        Set<String> theirs = new HashSet<>();
        for (Skill other : Skill.VALUES) {
            if (other == Skill.COMMERCE) {
                continue;
            }
            for (Talent talent : Talents.of(other)) {
                if (talent.special() != null) {
                    theirs.add(talent.special());
                }
            }
        }
        for (String special : mine) {
            assertFalse(theirs.contains(special), special + " is another tree's tag");
        }
    }

    @Test
    void theRootOpensAtLevelZeroAndTheCapstoneAtOneHundred() {
        assertEquals("market_stall", Talents.root(Skill.COMMERCE).id());
        assertEquals(100, Talents.get(Skill.COMMERCE, "guild_master").requiredLevel());
        assertEquals(90, Talents.get(Skill.COMMERCE, "heros_welcome").requiredLevel());
    }

    @Test
    void tenSynergiesPairCommerceWithTheTreesThatSellToTheVillagers() {
        String[] ids = {"market_day", "fresh_stock", "book_deal", "pantry_account", "apothecary_trade",
                "stone_contract", "good_furnishings", "dig_for_gold", "trade_tools", "fletchers_stall"};
        Set<Skill> partners = new HashSet<>();
        for (String id : ids) {
            Synergy synergy = Synergies.byId(id);
            assertNotNull(synergy, id);
            boolean mine = false;
            for (Synergy.Need need : synergy.requires()) {
                if (need.skill() == Skill.COMMERCE) {
                    mine = true;
                } else {
                    partners.add(need.skill());
                }
                assertTrue(need.minRank() <= Talents.get(need.skill(), need.talentId()).maxRank(), id);
            }
            assertTrue(mine, id + " does not need Commerce");
        }
        assertTrue(partners.containsAll(Set.of(Skill.SOCIAL, Skill.FISHING, Skill.SPELLCASTING, Skill.COOKING,
                Skill.ALCHEMY)), "partners: " + partners);
        assertEquals(10, partners.size());
    }

    @Test
    void aSynergyWakesWhenBothHalvesAreBought() {
        Synergy pair = Synergies.byId("book_deal");
        PlayerSkills skills = new PlayerSkills();
        skills.setRank(Talents.get(Skill.COMMERCE, "silver_tongue"), 3);
        assertFalse(Synergies.isActive(skills, pair));
        skills.setRank(Talents.get(Skill.SPELLCASTING, "arcane_focus"), 3);
        assertTrue(Synergies.isActive(skills, pair));
        assertTrue(Synergies.multiplier(skills, Skill.COMMERCE, PerkEffect.PROC_CHANCE) >= 1.10 - 1e-9);
    }

    @Test
    void theStatNodesRaiseTheirOwnNumbers() {
        PlayerSkills skills = new PlayerSkills();
        skills.setLevel(Skill.COMMERCE, 100);
        double plain = skills.bonus(Skill.COMMERCE);
        skills.setRank(Talents.get(Skill.COMMERCE, "fair_dealing"), 5);
        assertTrue(skills.bonus(Skill.COMMERCE) > plain, "Fair Dealing is a bigger discount");
        assertTrue(skills.procPower(Skill.COMMERCE) >= 1.0);
        skills.setRank(Talents.get(Skill.COMMERCE, "better_deals"), 3);
        assertEquals(1.30, skills.procPower(Skill.COMMERCE), 1e-9, "Better Deals: +10% proc power per rank");
    }
}
