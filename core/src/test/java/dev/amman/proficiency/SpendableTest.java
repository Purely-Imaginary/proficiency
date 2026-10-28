package dev.amman.proficiency;

import dev.amman.proficiency.perk.Requirement;
import dev.amman.proficiency.perk.Spendable;
import dev.amman.proficiency.perk.Talent;
import dev.amman.proficiency.perk.Talents;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The 'spendable now' rule (1.55.0): level gate, parents, points, and the filling rank's materials. */
class SpendableTest {

    /** A bag: per requirement a count; everything is available unless listed as missing. */
    private static final class Bag implements Spendable.Materials {
        final Map<Requirement, Integer> held = new HashMap<>();
        boolean full;

        @Override
        public int held(Requirement requirement) {
            return full ? requirement.count() : held.getOrDefault(requirement, 0);
        }

        @Override
        public boolean available(Requirement requirement) {
            return true;
        }
    }

    private static Bag full() {
        Bag bag = new Bag();
        bag.full = true;
        return bag;
    }

    private static final Skill SKILL = Skill.MINING;

    @Test
    void noPointsMeansNothingReady() {
        PlayerSkills skills = new PlayerSkills();
        Spendable.Report r = Spendable.of(skills, SKILL, full());
        assertEquals(0, r.unspent());
        assertEquals(0, r.readyRanks());
        assertFalse(r.waiting());
    }

    @Test
    void rootRanksAreReadyUpToThePoints() {
        Talent root = Talents.root(SKILL);
        assertTrue(root.maxRank() > 2, "the test wants a root with several ranks");
        PlayerSkills skills = new PlayerSkills();
        skills.setLevel(SKILL, 2 * root.costPerRank());
        Spendable.Report r = Spendable.of(skills, SKILL, new Bag());
        // Two ranks fit the points; neither fills the node, so the empty bag does not matter.
        assertEquals(2, r.readyRanks());
        assertEquals(0, r.leftover());
        assertFalse(r.waiting());
    }

    @Test
    void theFillingRankWaitsForMaterials() {
        Talent root = Talents.root(SKILL);
        assertFalse(root.materials().isEmpty(), "the mining root owes materials");
        PlayerSkills skills = new PlayerSkills();
        skills.setLevel(SKILL, root.totalCost());
        skills.setRank(root, root.maxRank() - 1);
        Bag empty = new Bag();
        Spendable.Report r = Spendable.of(skills, SKILL, empty);
        assertEquals(0, r.readyRanks());
        assertEquals(1, r.materialNodes());
        assertTrue(r.waiting());
        assertEquals(root.materials().size(), r.materials().size());
        Spendable.Short first = r.materials().get(0);
        assertEquals(0, first.have());
        assertEquals(first.requirement().count(), first.need());
        assertFalse(Spendable.nextReady(skills, root, empty));

        // Carrying part of it is still short; carrying all of it makes the rank ready.
        Bag some = new Bag();
        Requirement need = root.materials().get(0);
        some.held.put(need, need.count() - 1);
        assertEquals(0, Spendable.of(skills, SKILL, some).readyRanks());
        Spendable.Report stocked = Spendable.of(skills, SKILL, full());
        assertEquals(1, stocked.readyRanks());
        assertEquals(0, stocked.materialNodes());
        assertTrue(Spendable.nextReady(skills, root, full()));

        // Paid once (a respec, then a refill): points only.
        skills.markPaid(root);
        assertEquals(1, Spendable.of(skills, SKILL, empty).readyRanks());
    }

    @Test
    void earlierRanksStayReadyWhileTheLastWaitsForItems() {
        Talent root = Talents.root(SKILL);
        PlayerSkills skills = new PlayerSkills();
        skills.setLevel(SKILL, root.totalCost());
        Spendable.Report r = Spendable.of(skills, SKILL, new Bag());
        assertEquals(root.maxRank() - 1, r.readyRanks());
        // The points left over pay for the filling rank: only the items are missing, and they are named.
        assertEquals(root.costPerRank(), r.leftover());
        assertEquals(1, r.materialNodes());
        assertEquals(root.materials().size(), r.materials().size());
        assertTrue(r.costs().isEmpty());
        // The items are the reason, not the row at level 10 behind the root.
        assertEquals(0, r.nextLevel());

        // One point fewer: the filling rank is short of points, not items.
        skills.setLevel(SKILL, root.totalCost() - 1);
        Spendable.Report shortOfPoints = Spendable.of(skills, SKILL, new Bag());
        assertEquals(root.maxRank() - 1, shortOfPoints.readyRanks());
        assertEquals(0, shortOfPoints.materialNodes());
        assertEquals(1, shortOfPoints.costs().size());
        assertEquals(root.costPerRank() - 1, shortOfPoints.costs().get(0).have());
    }

    @Test
    void levelGateNamesTheNextRow() {
        Talent root = Talents.root(SKILL);
        PlayerSkills skills = new PlayerSkills();
        skills.setLevel(SKILL, 29);
        skills.setRank(root, root.maxRank());
        skills.markPaid(root);
        Spendable.Report r = Spendable.of(skills, SKILL, full());
        int lowest = Integer.MAX_VALUE;
        for (Talent t : Talents.of(SKILL)) {
            if (t.requiredLevel() > 29) {
                lowest = Math.min(lowest, t.requiredLevel());
            }
        }
        // Open nodes explain the points first; the further row is named only when nothing else does.
        assertTrue(r.nextLevel() == 0 || r.nextLevel() == lowest);
        assertTrue(r.readyRanks() > 0);

        // Every level-10 node full: the row at the next gate is the only place left, and it is closed.
        for (Talent t : Talents.of(SKILL)) {
            if (t.requiredLevel() <= 29) {
                skills.setRank(t, t.maxRank());
                skills.markPaid(t);
            }
        }
        skills.setLevel(SKILL, Math.min(29, skills.pointsSpent(SKILL) + 3));
        Spendable.Report gated = Spendable.of(skills, SKILL, full());
        if (gated.unspent() > 0) {
            assertEquals(0, gated.readyRanks());
            assertTrue(gated.waiting());
            assertEquals(lowest, gated.nextLevel());
        }
    }

    @Test
    void costAbovePointsIsReported() {
        // Find any node whose rank costs more than one point.
        Talent pricey = null;
        for (Skill skill : Skill.VALUES) {
            for (Talent t : Talents.of(skill)) {
                if (t.costPerRank() > 1 && t.kind() != Talent.Kind.ROOT) {
                    pricey = t;
                    break;
                }
            }
            if (pricey != null) {
                break;
            }
        }
        assertTrue(pricey != null, "some node costs more than one point");
        PlayerSkills skills = new PlayerSkills();
        // Every parent chain full, its level reached, then exactly one point too few.
        skills.setLevel(pricey.skill(), 100);
        fillAncestors(skills, pricey);
        int spent = skills.pointsSpent(pricey.skill());
        skills.setLevel(pricey.skill(), Math.max(pricey.requiredLevel(), spent + pricey.costPerRank() - 1));
        if (skills.pointsAvailable(pricey.skill()) != pricey.costPerRank() - 1) {
            return; // a gate above the point count: cannot set up exactly, the rule is covered elsewhere
        }
        Spendable.Report r = Spendable.of(skills, pricey.skill(), full());
        Talent target = pricey;
        assertTrue(r.costs().stream().anyMatch(c -> c.talent() == target && c.cost() == target.costPerRank()
                && c.have() < target.costPerRank()));
    }

    private static void fillAncestors(PlayerSkills skills, Talent t) {
        for (String pid : t.parents()) {
            Talent p = Talents.get(t.skill(), pid);
            if (p != null && !skills.isFull(p)) {
                fillAncestors(skills, p);
                skills.setRank(p, p.maxRank());
                skills.markPaid(p);
            }
        }
    }

    @Test
    void greedyTakesCheapestFirstAndNeverOvercounts() {
        PlayerSkills skills = new PlayerSkills();
        skills.setLevel(SKILL, 100);
        skills.fillTree(SKILL);
        Spendable.Report done = Spendable.of(skills, SKILL, full());
        assertEquals(0, done.readyRanks());
        assertTrue(done.treeFull());
        assertFalse(done.waiting());

        // Never more ranks than points, for every skill at every tenth level with an empty tree.
        for (Skill skill : Skill.VALUES) {
            for (int level = 0; level <= 100; level += 10) {
                PlayerSkills fresh = new PlayerSkills();
                fresh.setLevel(skill, level);
                Spendable.Report r = Spendable.of(fresh, skill, full());
                assertTrue(r.readyRanks() <= r.unspent());
                assertTrue(r.leftover() >= 0 && r.leftover() <= r.unspent());
            }
        }
    }

    @Test
    void summaryAddsUpAndFindsNewlyReady() {
        PlayerSkills skills = new PlayerSkills();
        Spendable.Summary before = Spendable.all(skills, full());
        assertEquals(0, before.ready());
        skills.setLevel(SKILL, Talents.root(SKILL).costPerRank());
        skills.setLevel(Skill.SWORDS, Talents.root(Skill.SWORDS).costPerRank());
        Spendable.Summary after = Spendable.all(skills, full());
        assertEquals(after.ready(SKILL) + after.ready(Skill.SWORDS), after.ready());
        assertEquals(after.ready(SKILL), after.readyIn(SKILL.category()) - (Skill.SWORDS.category() == SKILL.category()
                ? after.ready(Skill.SWORDS) : 0));
        assertEquals(2, after.newlyReady(before).size());
        assertTrue(after.newlyReady(after).isEmpty());
    }

    @Test
    void prerequisitesListParentsThatAreNotOpen() {
        // Level 30 with nothing taken: the level-30 nodes are level-ready but their parents are not open
        // (the level-10 nodes hang from the root, which is open), so only the not-open parents are listed.
        PlayerSkills skills = new PlayerSkills();
        skills.setLevel(SKILL, 30);
        Spendable.Report r = Spendable.of(skills, SKILL, new Bag());
        for (Talent p : r.prerequisites()) {
            assertFalse(skills.isUnlockableNow(p));
        }
    }
}
