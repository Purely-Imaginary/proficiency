package dev.amman.proficiency;

import dev.amman.proficiency.perk.MaterialNeeds;
import dev.amman.proficiency.perk.MaterialNeeds.Entry;
import dev.amman.proficiency.perk.MaterialNeeds.Line;
import dev.amman.proficiency.perk.Requirement;
import dev.amman.proficiency.perk.Talent;
import dev.amman.proficiency.perk.Talents;
import dev.amman.proficiency.skill.Skill;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The index, the sorting and the missing-count maths behind the item tooltip's Needed block. */
class MaterialNeedsTest {

    private static final Requirement COAL = Requirement.of("minecraft:coal", 8);

    private static Entry entry(Skill skill, int gate, String id, Requirement requirement) {
        Talent talent = new Talent(id, skill, Talent.Kind.NODE, 1, 0, 1, gate, 1, List.of(),
                java.util.Map.of(), List.of(requirement), null);
        return new Entry(skill, talent, requirement);
    }

    @Test
    void indexFindsAnItemAndItsTags() {
        MaterialNeeds.Index index = MaterialNeeds.build(t -> false, r -> true);
        assertFalse(index.isEmpty());
        boolean found = false;
        for (Skill skill : Skill.VALUES) {
            for (Talent talent : Talents.of(skill)) {
                for (Requirement r : talent.materials()) {
                    String item = r.itemId();
                    List<Entry> hits = index.entriesFor(
                            item == null ? "x:none" : item, r.tag() == null ? Set.of() : Set.of(r.tag()));
                    assertTrue(hits.stream().anyMatch(e -> e.talent() == talent && e.requirement().equals(r)));
                    found = true;
                }
            }
        }
        assertTrue(found);
    }

    @Test
    void paidNodesAndMissingModsAreLeftOut() {
        int all = MaterialNeeds.build(t -> false, r -> true).size();
        assertTrue(all > 0);
        assertEquals(0, MaterialNeeds.build(t -> true, r -> true).size());
        assertEquals(0, MaterialNeeds.build(t -> false, r -> false).size());
        Talent mining = Talents.root(Skill.MINING);
        int without = MaterialNeeds.build(t -> t == mining, r -> true).size();
        assertEquals(all - mining.materials().size(), without);
    }

    @Test
    void anUnrelatedItemHasNoEntries() {
        MaterialNeeds.Index index = MaterialNeeds.build(t -> false, r -> true);
        assertTrue(index.entriesFor("nothing:here", Set.of("nothing:tag")).isEmpty());
    }

    @Test
    void missingIsRequiredMinusHeldNeverNegative() {
        Entry e = entry(Skill.MINING, 10, "a", COAL);
        Line short_ = MaterialNeeds.lines(List.of(e), r -> 5, t -> true).get(0);
        assertEquals(3, short_.missing());
        Line over = MaterialNeeds.lines(List.of(e), r -> 99, t -> true).get(0);
        assertEquals(0, over.missing());
        assertEquals(8, over.required());
    }

    @Test
    void orderIsOpenFirstThenGate() {
        Entry far = entry(Skill.MINING, 60, "far", COAL);
        Entry near = entry(Skill.MINING, 10, "near", COAL);
        Entry mid = entry(Skill.MINING, 30, "mid", COAL);
        Entry locked = entry(Skill.WOODCUTTING, 5, "locked", COAL);
        List<Line> lines = MaterialNeeds.lines(List.of(far, locked, mid, near), r -> 0,
                t -> t.skill() == Skill.MINING && t.requiredLevel() <= 40);
        assertEquals(List.of("near", "mid", "locked", "far"),
                lines.stream().map(l -> l.entry().talent().id()).toList());
        assertTrue(lines.get(0).unlocked());
        assertFalse(lines.get(2).unlocked());
    }

    @Test
    void defaultViewIsImmediateOnlyAndShiftAddsLater() {
        Entry now = entry(Skill.MINING, 10, "now", COAL);
        Entry later = entry(Skill.MINING, 60, "later", COAL);
        Entry later2 = entry(Skill.MINING, 70, "later2", COAL);
        List<Line> lines = MaterialNeeds.lines(List.of(later, now, later2), r -> 0, t -> t.requiredLevel() <= 10);
        assertEquals(1, MaterialNeeds.shown(lines, false).size());
        assertEquals(2, MaterialNeeds.hidden(lines, false));
        assertEquals(3, MaterialNeeds.shown(lines, true).size());
        assertEquals(0, MaterialNeeds.hidden(lines, true));
    }

    @Test
    void nothingImmediateShowsNothingByDefaultAndNoHint() {
        Entry later = entry(Skill.MINING, 60, "later", COAL);
        List<Line> lines = MaterialNeeds.lines(List.of(later), r -> 0, t -> false);
        assertTrue(MaterialNeeds.shown(lines, false).isEmpty());
        assertEquals(0, MaterialNeeds.hidden(lines, false));
        assertEquals(1, MaterialNeeds.shown(lines, true).size());
    }

    @Test
    void immediateUsesTheClickPathRule() {
        dev.amman.proficiency.skill.PlayerSkills skills = new dev.amman.proficiency.skill.PlayerSkills();
        for (Skill skill : Skill.VALUES) {
            skills.setLevel(skill, 50);
        }
        for (Skill skill : Skill.VALUES) {
            for (Talent t : Talents.of(skill)) {
                assertEquals(skills.check(t) == dev.amman.proficiency.perk.TalentOutcome.OK
                                || skills.check(t) == dev.amman.proficiency.perk.TalentOutcome.NOT_ENOUGH_POINTS,
                        skills.isUnlockableNow(t), t.key());
            }
        }
    }

    @Test
    void oneNodeMatchingTwiceIsOneLineWithTheBiggerShortfall() {
        Entry a = entry(Skill.MINING, 10, "same", Requirement.of("minecraft:coal", 4));
        Entry b = new Entry(Skill.MINING, a.talent(), Requirement.ofTag("minecraft:coals", 8));
        List<Line> lines = MaterialNeeds.lines(List.of(a, b), r -> 1, t -> true);
        assertEquals(1, lines.size());
        assertEquals(7, lines.get(0).missing());
    }

    @Test
    void totalIsTheSumOfShortfalls() {
        Entry a = entry(Skill.MINING, 10, "a", Requirement.of("minecraft:coal", 5));
        Entry b = entry(Skill.MINING, 20, "b", Requirement.of("minecraft:coal", 10));
        List<Line> lines = MaterialNeeds.lines(List.of(a, b), r -> 2, t -> true);
        assertEquals(3 + 8, MaterialNeeds.totalMissing(lines));
    }
}
