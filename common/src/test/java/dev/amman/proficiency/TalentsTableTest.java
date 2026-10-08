package dev.amman.proficiency;

import dev.amman.proficiency.perk.TalentOutcome;
import dev.amman.proficiency.perk.PerkEffect;
import dev.amman.proficiency.perk.Talent;
import dev.amman.proficiency.perk.TalentService;
import dev.amman.proficiency.perk.Talents;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillMath;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The shape of every tree, and the promise that a branch is a priority, never a lockout. */
class TalentsTableTest {

    /** Static initialisers run in textual order; touching the table at all is the test. */
    @Test
    void theTableInitialises() {
        assertFalse(Talents.of(Skill.WOODCUTTING).isEmpty());
    }

    @Test
    void everySkillHasItsOwnTree() {
        Set<String> shapes = new HashSet<>();
        for (Skill skill : Skill.VALUES) {
            int size = Talents.of(skill).size();
            assertTrue(size >= 9 && size <= 14, skill.id() + " has " + size + " nodes");
            StringBuilder ids = new StringBuilder();
            Talents.of(skill).forEach(talent -> ids.append(talent.id()).append(','));
            assertTrue(shapes.add(ids.toString()), skill.id() + " is a copy of another tree");
        }
    }

    /** A tree that is only numbers is the thing this replaced. Every tree does things. */
    @Test
    void everyTreeHasAtLeastFiveMechanicsOfItsOwn() {
        for (Skill skill : Skill.VALUES) {
            long specials = Talents.of(skill).stream().filter(talent -> talent.special() != null).count();
            assertTrue(specials >= 5, skill.id() + " has only " + specials + " bespoke nodes");
        }
    }

    @Test
    void everyTreeHasOneRootAndOneCapstoneWithAMechanic() {
        for (Skill skill : Skill.VALUES) {
            assertEquals(1, Talents.of(skill).stream().filter(t -> t.kind() == Talent.Kind.ROOT).count(), skill.id());
            var capstones = Talents.of(skill).stream().filter(t -> t.kind() == Talent.Kind.CAPSTONE).toList();
            assertEquals(1, capstones.size(), skill.id());
            assertNotNull(capstones.get(0).special(), skill.id() + "'s capstone is only numbers");
        }
    }

    /**
     * The rule: a maxed skill fills its whole tree. Choosing a branch first decides the order, and
     * never closes another branch.
     */
    @Test
    void aMaxedSkillCanAffordItsWholeTree() {
        int earned = SkillMath.MAX_LEVEL / Talents.LEVELS_PER_POINT;
        for (Skill skill : Skill.VALUES) {
            assertTrue(Talents.fullTreeCost(skill) <= earned,
                    skill.id() + " costs " + Talents.fullTreeCost(skill) + " but level 100 yields " + earned);
        }
    }

    /**
     * Affording it is not enough: nothing may exclude anything else. A level-100 player who
     * spends greedily, one rank at a time on whatever will take it, must end with every node full,
     * whichever branch they happened to start on.
     */
    @Test
    void atLevelOneHundredEveryNodeFillsWhateverOrderYouChoose() {
        for (Skill skill : Skill.VALUES) {
            for (int startColumn : new int[] {0, 2, 4}) {
                PlayerSkills skills = new PlayerSkills();
                skills.setLevel(skill, SkillMath.MAX_LEVEL);
                boolean progressed = true;
                while (progressed) {
                    progressed = false;
                    // Prefer the chosen branch, then take anything else that opens.
                    for (int pass = 0; pass < 2 && !progressed; pass++) {
                        for (Talent talent : Talents.of(skill)) {
                            boolean preferred = talent.col() == startColumn || talent.row() == 0;
                            if ((pass == 0) != preferred) {
                                continue;
                            }
                            if (skills.check(talent) == TalentOutcome.OK) {
                                skills.addRank(talent);
                                progressed = true;
                                break;
                            }
                        }
                    }
                }
                for (Talent talent : Talents.of(skill)) {
                    assertTrue(skills.isFull(talent), talent.key() + " never filled starting from column "
                            + startColumn + ": " + skills.check(talent));
                }
            }
        }
    }

    /** And before 100 the choice is real: level 50 cannot hold the whole tree. */
    @Test
    void midGameYouStillHaveToPrioritise() {
        int atFifty = 50 / Talents.LEVELS_PER_POINT;
        for (Skill skill : Skill.VALUES) {
            assertTrue(Talents.fullTreeCost(skill) > atFifty, skill.id());
        }
    }

    @Test
    void keysAreUniqueAndResolve() {
        Set<String> keys = new HashSet<>();
        for (Skill skill : Skill.VALUES) {
            for (Talent talent : Talents.of(skill)) {
                assertTrue(keys.add(talent.key()), "duplicate " + talent.key());
                assertEquals(talent, Talents.byKey(talent.key()));
                assertEquals(talent, Talents.get(skill, talent.id()));
            }
        }
    }

    @Test
    void parentsExistSitHigherAndAskForNoMoreLevel() {
        for (Skill skill : Skill.VALUES) {
            for (Talent talent : Talents.of(skill)) {
                assertEquals(talent.kind() == Talent.Kind.ROOT, talent.parents().isEmpty(), talent.key());
                for (String parentId : talent.parents()) {
                    Talent parent = Talents.get(skill, parentId);
                    assertNotNull(parent, talent.key() + " hangs from missing " + parentId);
                    assertTrue(parent.row() < talent.row(), talent.key() + " hangs from a lower row");
                    assertTrue(parent.requiredLevel() <= talent.requiredLevel(), talent.key());
                }
            }
        }
    }

    @Test
    void noTwoNodesShareAGridCell() {
        for (Skill skill : Skill.VALUES) {
            Map<String, String> cells = new HashMap<>();
            for (Talent talent : Talents.of(skill)) {
                String cell = talent.row() + "," + talent.col();
                assertTrue(cells.put(cell, talent.id()) == null, skill.id() + " cell " + cell);
                assertTrue(talent.col() >= 0 && talent.col() <= 4 && talent.row() >= 0 && talent.row() <= 5);
            }
        }
    }

    /**
     * Requirements naming an absent mod are dropped at unlock time, so every node that charges
     * materials must carry at least one that always exists, or it would be free without its mods.
     */
    @Test
    void everyMaterialNodeAsksForSomethingThatAlwaysExists() {
        for (Skill skill : Skill.VALUES) {
            for (Talent talent : Talents.of(skill)) {
                if (talent.materials().isEmpty()) {
                    continue;
                }
                boolean guaranteed = talent.materials().stream().anyMatch(requirement ->
                        requirement.itemId() != null
                                && ("minecraft".equals(requirement.namespace())
                                || "proficiency".equals(requirement.namespace())));
                assertTrue(guaranteed, talent.key());
                talent.materials().forEach(requirement -> assertTrue(requirement.count() > 0));
            }
        }
    }

    /** The four old tiers' lists land at the levels the old tiers did: 10, 60, 60 and 90. */
    @Test
    void materialsSitOnFourGateNodesAtTheOldTierLevels() {
        for (Skill skill : Skill.VALUES) {
            var gates = Talents.of(skill).stream().filter(t -> !t.materials().isEmpty())
                    .map(Talent::requiredLevel).sorted().toList();
            assertEquals(java.util.List.of(0, 60, 60, 90), gates, skill.id());
            Talent root = Talents.of(skill).get(0);
            assertEquals(Talent.Kind.ROOT, root.kind());
            assertEquals(5, root.maxRank(), "the root fills at level 10, where Apprentice was");
        }
    }

    @Test
    void ranksAreBoostsExceptWhereTheyShorten() {
        for (Skill skill : Skill.VALUES) {
            for (Talent talent : Talents.of(skill)) {
                talent.perRank().forEach((effect, value) -> {
                    if (effect == PerkEffect.ABILITY_COOLDOWN) {
                        assertTrue(value < 0, talent.key() + " lengthens the cooldown");
                    } else {
                        assertTrue(value > 0, talent.key() + " " + effect + " is not a boost");
                    }
                });
                assertTrue(talent.maxRank() >= 1 && talent.costPerRank() >= 1, talent.key());
            }
        }
    }

    /** The bespoke Master behaviours the gathering code reads have to still be on a node. */
    @Test
    void theMasterSpecialsTheEventCodeReadsStillExist() {
        assertEquals("auto_smelt", Talents.get(Skill.MINING, "smelter").special());
        assertEquals("timber_greedy", Talents.get(Skill.WOODCUTTING, "canopy").special());
        assertEquals("landslide_wide", Talents.get(Skill.EXCAVATION, "wide_shovel").special());
        assertEquals("always_replant", Talents.get(Skill.FARMING, "green_thumb").special());
        assertEquals("free_place", Talents.get(Skill.MASONRY, "master_mason").special());
        assertEquals("free_place", Talents.get(Skill.DECORATING, "master_decorator").special());
    }

    /** A full Blocking tree is exactly total immunity, not 99% of it. */
    @Test
    void aFullBlockingTreeNeverLosesLevels() {
        PlayerSkills skills = new PlayerSkills();
        skills.fillTree(Skill.BLOCKING);
        assertEquals(1.0, skills.deathWard(Skill.BLOCKING), 1e-9);
    }

    /** Every name and description the tree screen asks for is in the language file. */
    @Test
    void everyNodeHasANameAndADescription() throws java.io.IOException {
        String lang = java.nio.file.Files.readString(
                java.nio.file.Path.of("src/main/resources/assets/proficiency/lang/en_us.json"));
        for (Skill skill : Skill.VALUES) {
            for (Talent talent : Talents.of(skill)) {
                String key = "proficiency.talent." + skill.id() + "." + talent.id();
                assertTrue(lang.contains("\"" + key + "\""), key);
                assertTrue(lang.contains("\"" + key + ".desc\""), key + ".desc");
            }
        }
    }
}
