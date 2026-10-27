package dev.amman.proficiency;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import dev.amman.proficiency.perk.Expertise;
import dev.amman.proficiency.perk.ExpertiseNode;
import dev.amman.proficiency.perk.PerkEffect;
import dev.amman.proficiency.perk.Talent;
import dev.amman.proficiency.perk.TalentOutcome;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillCategory;
import dev.amman.proficiency.skill.SkillsWire;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Expertise tree: the points formula, the table's rules, the modifier maths and the save. */
class ExpertiseTest {

    @Test
    void pointsAreOnePerHundredLevelsAndOnePerFiveStars() {
        assertEquals(0, Expertise.points(99, 4));
        assertEquals(1, Expertise.points(100, 0));
        assertEquals(1, Expertise.points(0, 5));
        assertEquals(12 + 3, Expertise.points(1234, 17));
        assertEquals(38, Expertise.pointsFromLevels(3800));
        assertEquals(38, Expertise.pointsFromLevels(99_999));
        assertEquals(38, Expertise.pointsFromStars(190));
        assertEquals(Expertise.MAX_POINTS, Expertise.points(3800, 190));
        assertEquals(76, Expertise.MAX_POINTS);
        assertEquals(Skill.VALUES.length, Expertise.MAX_FROM_LEVELS, "one level point per skill at 100");
    }

    @Test
    void pointsComeFromTheLevelsAndStarsNotFromAStoredCounter() {
        PlayerSkills skills = new PlayerSkills();
        assertEquals(0, skills.expertisePoints());
        skills.setLevel(Skill.MINING, 60);
        skills.setLevel(Skill.SWORDS, 45);
        assertEquals(1, skills.expertisePoints());
        skills.setLevel(Skill.MINING, 100);
        skills.setLevel(Skill.FARMING, 100);
        skills.setStars(Skill.MINING, 5);
        assertEquals(2 + 1, skills.expertisePoints());
        skills.setLevel(Skill.FARMING, 0);
        assertEquals(1 + 1, skills.expertisePoints(), "points follow the levels down as well as up");
    }

    @Test
    void theTreeHasTwelveToEighteenNodesAndAFinishedPlayerFillsIt() {
        List<ExpertiseNode> nodes = Expertise.nodes();
        assertTrue(nodes.size() >= 12 && nodes.size() <= 18, "nodes: " + nodes.size());
        assertTrue(Expertise.fullCost() <= Expertise.MAX_POINTS, "full cost " + Expertise.fullCost());
        assertEquals(1, nodes.stream().filter(n -> n.kind() == Talent.Kind.ROOT).count());
        assertEquals(1, nodes.stream().filter(n -> n.kind() == Talent.Kind.CAPSTONE).count());

        // Spend greedily with every point there is: the whole tree ends full, whatever order.
        PlayerSkills skills = finished();
        boolean bought = true;
        while (bought) {
            bought = false;
            for (ExpertiseNode node : nodes) {
                if (skills.checkExpertise(node) == TalentOutcome.OK) {
                    skills.addExpertiseRank(node);
                    bought = true;
                }
            }
        }
        for (ExpertiseNode node : nodes) {
            assertTrue(skills.isExpertiseFull(node), node.id() + " is not full");
        }
        assertEquals(Expertise.fullCost(), skills.expertiseSpent());
    }

    @Test
    void everyNodeIsWellFormedAndNothingSoftensADeath() {
        Set<String> places = new HashSet<>();
        for (ExpertiseNode node : Expertise.nodes()) {
            assertTrue(places.add(node.row() + "," + node.col()), "two nodes on one cell: " + node.id());
            assertTrue(node.row() >= 0 && node.row() < 6 && node.col() >= 0 && node.col() < 5, node.id());
            assertTrue(node.maxRank() >= 1 && node.costPerRank() >= 1, node.id());
            assertFalse(node.perRank().isEmpty(), node.id());
            assertFalse(node.perRank().containsKey(PerkEffect.DEATH_WARD), "no death softening: " + node.id());
            for (String parent : node.parents()) {
                ExpertiseNode before = Expertise.byId(parent);
                assertNotNull(before, node.id() + " hangs from unknown " + parent);
                assertTrue(before.row() < node.row(), node.id() + " hangs from a lower row");
            }
            assertEquals(node.kind() == Talent.Kind.ROOT, node.parents().isEmpty(), node.id());
        }
    }

    @Test
    void aFullTreeIsAStepNotADoubling() {
        PlayerSkills skills = new PlayerSkills();
        skills.fillExpertise();
        for (Skill skill : Skill.VALUES) {
            for (PerkEffect effect : PerkEffect.values()) {
                double part = skills.modifier(skill, effect).expertise();
                assertTrue(part >= 0.85 && part <= 1.15, skill + " " + effect + " " + part);
            }
        }
        // Every effect the design names reaches every category.
        for (SkillCategory category : SkillCategory.values()) {
            Skill sample = null;
            for (Skill skill : Skill.VALUES) {
                if (skill.category() == category) {
                    sample = skill;
                    break;
                }
            }
            assertTrue(skills.modifier(sample, PerkEffect.BONUS).expertise() > 1.0, category.id());
            assertTrue(skills.modifier(sample, PerkEffect.PROC_CHANCE).expertise() > 1.0, category.id());
        }
    }

    @Test
    void theExpertisePartMultipliesIntoTheGameplayValue() {
        PlayerSkills skills = new PlayerSkills();
        skills.setLevel(Skill.SWORDS, 60);
        double before = skills.perkModifier(Skill.SWORDS, PerkEffect.BONUS);
        ExpertiseNode martial = Expertise.byId("martial");
        skills.setExpertiseRank(martial, martial.maxRank());
        PlayerSkills.Modifier m = skills.modifier(Skill.SWORDS, PerkEffect.BONUS);
        assertEquals(1.0 + 0.02 * martial.maxRank(), m.expertise(), 1e-12);
        assertEquals(before * m.expertise(), m.total(), 1e-12);
        assertEquals(m.total(), skills.perkModifier(Skill.SWORDS, PerkEffect.BONUS), 1e-12);
        // Martial is combat only: Mining's passive does not move.
        assertEquals(1.0, skills.modifier(Skill.MINING, PerkEffect.BONUS).expertise(), 1e-12);
        // The cooldown node shortens: its part is below one.
        ExpertiseNode wind = Expertise.byId("second_wind");
        skills.setExpertiseRank(wind, 1);
        assertTrue(skills.modifier(Skill.MINING, PerkEffect.ABILITY_COOLDOWN).expertise() < 1.0);
    }

    @Test
    void checkAsksForParentsThenPoints() {
        PlayerSkills skills = new PlayerSkills();
        ExpertiseNode root = Expertise.byId("breadth");
        ExpertiseNode martial = Expertise.byId("martial");
        assertEquals(TalentOutcome.NOT_ENOUGH_POINTS, skills.checkExpertise(root));
        assertEquals(TalentOutcome.PARENT_NOT_FULL, skills.checkExpertise(martial));
        skills.setLevel(Skill.MINING, 100);
        skills.setLevel(Skill.SWORDS, 100);
        skills.setLevel(Skill.FARMING, 100);
        skills.setLevel(Skill.RUNNING, 100);
        skills.setLevel(Skill.COOKING, 100);
        assertEquals(5, skills.expertiseAvailable());
        for (int i = 0; i < root.maxRank(); i++) {
            assertEquals(TalentOutcome.OK, skills.checkExpertise(root));
            skills.addExpertiseRank(root);
        }
        assertEquals(TalentOutcome.MAXED, skills.checkExpertise(root));
        assertEquals(TalentOutcome.OK, skills.checkExpertise(martial));
        skills.addExpertiseRank(martial);
        assertEquals(0, skills.expertiseAvailable());
        assertEquals(TalentOutcome.NOT_ENOUGH_POINTS, skills.checkExpertise(martial));
        assertEquals(5, skills.respecExpertise());
        assertEquals(5, skills.expertiseAvailable());
        assertTrue(skills.expertiseRanks().isEmpty());
    }

    @Test
    void respecCostsOneLevelPerTenPoints() {
        assertEquals(0, Expertise.respecPrice(0));
        assertEquals(1, Expertise.respecPrice(3));
        assertEquals(2, Expertise.respecPrice(25));
        assertEquals(7, Expertise.respecPrice(70));
    }

    @Test
    void theSaveRoundTripsAndAnOldSaveLoadsEmpty() {
        PlayerSkills original = new PlayerSkills();
        original.setLevel(Skill.MINING, 80);
        original.setExpertiseRank(Expertise.byId("breadth"), 2);
        JsonElement encoded = PlayerSkills.CODEC.encodeStart(JsonOps.INSTANCE, original)
                .getOrThrow(message -> new AssertionError("encode failed: " + message));
        PlayerSkills restored = PlayerSkills.CODEC.parse(JsonOps.INSTANCE, encoded)
                .getOrThrow(message -> new AssertionError("decode failed: " + message));
        assertEquals(2, restored.expertiseRank(Expertise.byId("breadth")));

        // An old save: no field at all.
        JsonObject old = encoded.getAsJsonObject().deepCopy();
        old.remove("expertise");
        PlayerSkills fromOld = PlayerSkills.CODEC.parse(JsonOps.INSTANCE, old)
                .getOrThrow(message -> new AssertionError("decode failed: " + message));
        assertTrue(fromOld.expertiseRanks().isEmpty());
        assertEquals(80, fromOld.level(Skill.MINING));

        // A node that left the table is dropped; a rank past the maximum is clamped.
        JsonObject stale = encoded.getAsJsonObject().deepCopy();
        JsonObject ranks = stale.getAsJsonObject("expertise");
        ranks.addProperty("no_such_node", 2);
        ranks.addProperty("breadth", 99);
        PlayerSkills fromStale = PlayerSkills.CODEC.parse(JsonOps.INSTANCE, stale)
                .getOrThrow(message -> new AssertionError("decode failed: " + message));
        assertEquals(Expertise.byId("breadth").maxRank(), fromStale.expertiseRank(Expertise.byId("breadth")));
        assertEquals(1, fromStale.expertiseRanks().size());
    }

    @Test
    void theSyncPacketCarriesTheRanks() {
        PlayerSkills skills = finished();
        skills.fillExpertise();
        Buffer buffer = new Buffer();
        skills.write(buffer);
        PlayerSkills read = PlayerSkills.read(buffer);
        assertTrue(buffer.queue.isEmpty(), "the reader consumes exactly what the writer wrote");
        assertEquals(skills.expertiseRanks(), read.expertiseRanks());
        assertEquals(skills.expertisePoints(), read.expertisePoints());
        assertEquals(skills.perkModifier(Skill.MINING, PerkEffect.XP_RATE),
                read.perkModifier(Skill.MINING, PerkEffect.XP_RATE), 1e-12);
    }

    @Test
    void copyAndResetCarryTheTree() {
        PlayerSkills skills = new PlayerSkills();
        skills.fillExpertise();
        PlayerSkills copy = new PlayerSkills();
        copy.copyFrom(skills);
        assertEquals(skills.expertiseRanks(), copy.expertiseRanks());
        copy.reset();
        assertTrue(copy.expertiseRanks().isEmpty());
    }

    private static PlayerSkills finished() {
        PlayerSkills skills = new PlayerSkills();
        for (Skill skill : Skill.VALUES) {
            skills.setLevel(skill, 100);
            skills.setStars(skill, 5);
        }
        assertEquals(Expertise.MAX_POINTS, skills.expertisePoints());
        return skills;
    }

    private static final class Buffer implements SkillsWire.Out, SkillsWire.In {
        private final ArrayDeque<Object> queue = new ArrayDeque<>();

        @Override public void writeVarInt(int value) { queue.add(value); }
        @Override public void writeFloat(float value) { queue.add(value); }
        @Override public void writeUtf(String value) { queue.add(value); }
        @Override public void writeVarLong(long value) { queue.add(value); }
        @Override public int readVarInt() { return (Integer) queue.remove(); }
        @Override public float readFloat() { return (Float) queue.remove(); }
        @Override public String readUtf() { return (String) queue.remove(); }
        @Override public long readVarLong() { return (Long) queue.remove(); }
    }
}
