package dev.amman.proficiency;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import dev.amman.proficiency.perk.PerkEffect;
import dev.amman.proficiency.perk.Talent;
import dev.amman.proficiency.perk.Talents;
import dev.amman.proficiency.skill.Mastery;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillMath;
import dev.amman.proficiency.skill.SkillTuning;
import dev.amman.proficiency.skill.SkillsWire;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Mastery stars: the numbers, the overflow bar, the death rule, the save and the sync packet. */
class MasteryTest {

    private static final Skill S = Skill.MINING;

    @AfterEach
    void restoreTuning() {
        SkillTuning.install(SkillTuning.DEFAULTS);
    }

    private static PlayerSkills maxed() {
        PlayerSkills skills = new PlayerSkills();
        skills.setLevel(S, SkillMath.MAX_LEVEL);
        return skills;
    }

    @Test
    void starNCostsStarBaseTimesN() {
        float last = SkillMath.xpToNext(SkillMath.MAX_LEVEL - 1);
        assertEquals(last, Mastery.starBase(), 1e-3f);
        for (int n = 1; n <= Mastery.MAX_STARS; n++) {
            assertEquals(last * n, Mastery.starCost(n), 1e-2f);
        }
    }

    @Test
    void allFiveStarsCostAboutTheLastFifteenLevels() {
        float lastFifteen = 0f;
        for (int level = 85; level < SkillMath.MAX_LEVEL; level++) {
            lastFifteen += SkillMath.xpToNext(level);
        }
        float total = Mastery.totalCost(Mastery.MAX_STARS);
        assertEquals(15 * SkillMath.xpToNext(99), total, 1f);
        assertTrue(total > lastFifteen && total < lastFifteen * 1.3f,
                "five stars (" + total + ") should be about the last 15 levels (" + lastFifteen + ")");
    }

    @Test
    void theStarFactorIsTunable() {
        SkillTuning.install(new SkillTuning() {
            @Override public double curveFloor() { return DEFAULTS.curveFloor(); }
            @Override public double curveBase() { return DEFAULTS.curveBase(); }
            @Override public double curveExponent() { return DEFAULTS.curveExponent(); }
            @Override public boolean enabled(Skill skill) { return true; }
            @Override public double maxBonus(Skill skill) { return DEFAULTS.maxBonus(skill); }
            @Override public boolean procsEnabled() { return true; }
            @Override public int procUnlockLevel() { return 25; }
            @Override public double procFloor() { return 0.05; }
            @Override public double procChance(Skill skill) { return DEFAULTS.procChance(skill); }
            @Override public double masteryStarFactor() { return 3.0; }
        });
        assertEquals(3 * SkillMath.xpToNext(99), Mastery.starBase(), 1e-2f);
    }

    @Test
    void xpPastLevelOneHundredFillsTheOverflowBar() {
        PlayerSkills skills = maxed();
        float half = Mastery.starCost(1) / 2f;

        Mastery.Result result = skills.addXpMastery(S, half);

        assertEquals(0, result.levels());
        assertEquals(0, result.stars());
        assertEquals(half, skills.overflow(S), 1e-3f);
        assertEquals(0.5f, skills.starProgress(S), 1e-4f);
        assertEquals(0.5f, skills.barProgress(S), 1e-4f);
        assertEquals(0, skills.addXp(S, 1f), "addXp still reports levels only");
        assertEquals(SkillMath.MAX_LEVEL, skills.level(S));
    }

    @Test
    void aFullBarEarnsAStarAndKeepsTheRemainder() {
        PlayerSkills skills = maxed();
        float cost = Mastery.starCost(1);

        Mastery.Result result = skills.addXpMastery(S, cost + 40f);

        assertEquals(1, result.stars());
        assertEquals(1, skills.stars(S));
        assertEquals(40f, skills.overflow(S), 1e-2f);
    }

    @Test
    void oneHugeGrantEarnsSeveralStarsAndStopsAtFive() {
        PlayerSkills skills = maxed();

        Mastery.Result result = skills.addXpMastery(S, Mastery.totalCost(5) * 10f);

        assertEquals(5, result.stars());
        assertEquals(5, skills.stars(S));
        assertEquals(0f, skills.overflow(S), "nothing is banked past the last star");
        assertEquals(1f, skills.starProgress(S));
        assertEquals(Mastery.Result.NONE, skills.addXpMastery(S, 1000f), "a sixth star does not exist");
        assertEquals(5, skills.stars(S));
        assertEquals(0f, skills.overflow(S));
    }

    @Test
    void starsComeInOrderAtGrowingPrices() {
        PlayerSkills skills = maxed();
        skills.addXpMastery(S, Mastery.starCost(1) - 1f);
        assertEquals(0, skills.stars(S));
        skills.addXpMastery(S, 1f);
        assertEquals(1, skills.stars(S));
        // Star 2 is twice as dear: one star's worth of XP is not enough.
        skills.addXpMastery(S, Mastery.starCost(1));
        assertEquals(1, skills.stars(S));
        skills.addXpMastery(S, Mastery.starCost(1));
        assertEquals(2, skills.stars(S));
    }

    @Test
    void whatALevelUpLeavesOverCarriesIntoTheBar() {
        PlayerSkills skills = new PlayerSkills();
        skills.setLevel(S, SkillMath.MAX_LEVEL - 1);
        float need = SkillMath.xpToNext(SkillMath.MAX_LEVEL - 1);

        Mastery.Result result = skills.addXpMastery(S, need + 123f);

        assertEquals(1, result.levels());
        assertEquals(SkillMath.MAX_LEVEL, skills.level(S));
        assertEquals(123f, skills.overflow(S), 1e-2f);
        assertEquals(0f, skills.xp(S));
    }

    @Test
    void nothingBelowLevelOneHundredEarnsStars() {
        PlayerSkills skills = new PlayerSkills();
        skills.setLevel(S, 60);
        skills.addXpMastery(S, 100f);
        assertEquals(0, skills.stars(S));
        assertEquals(0f, skills.overflow(S));
    }

    @Test
    void maxStarsOfZeroSwitchesTheBarOff() {
        SkillTuning.install(new SkillTuning() {
            @Override public double curveFloor() { return DEFAULTS.curveFloor(); }
            @Override public double curveBase() { return DEFAULTS.curveBase(); }
            @Override public double curveExponent() { return DEFAULTS.curveExponent(); }
            @Override public boolean enabled(Skill skill) { return true; }
            @Override public double maxBonus(Skill skill) { return DEFAULTS.maxBonus(skill); }
            @Override public boolean procsEnabled() { return true; }
            @Override public int procUnlockLevel() { return 25; }
            @Override public double procFloor() { return 0.05; }
            @Override public double procChance(Skill skill) { return DEFAULTS.procChance(skill); }
            @Override public int masteryMaxStars() { return 0; }
        });
        PlayerSkills skills = maxed();
        assertEquals(Mastery.Result.NONE, skills.addXpMastery(S, 100000f));
        assertEquals(0f, skills.overflow(S));
        assertEquals(0, Mastery.maxStars());
    }

    @Test
    void aCappedConfigStopsAtItsStarCount() {
        SkillTuning.install(new SkillTuning() {
            @Override public double curveFloor() { return DEFAULTS.curveFloor(); }
            @Override public double curveBase() { return DEFAULTS.curveBase(); }
            @Override public double curveExponent() { return DEFAULTS.curveExponent(); }
            @Override public boolean enabled(Skill skill) { return true; }
            @Override public double maxBonus(Skill skill) { return DEFAULTS.maxBonus(skill); }
            @Override public boolean procsEnabled() { return true; }
            @Override public int procUnlockLevel() { return 25; }
            @Override public double procFloor() { return 0.05; }
            @Override public double procChance(Skill skill) { return DEFAULTS.procChance(skill); }
            @Override public int masteryMaxStars() { return 2; }
        });
        PlayerSkills skills = maxed();
        skills.addXpMastery(S, 1_000_000f);
        assertEquals(2, skills.stars(S));
        assertEquals(0f, skills.overflow(S));
        assertEquals(1.0f, skills.starProgress(S), "a skill at the configured cap reads full, not empty");
        assertEquals(1.0f, skills.barProgress(S));
        assertEquals(2, Mastery.lastStar());
    }

    @Test
    void nonFiniteOverflowReadsAsEmpty() {
        PlayerSkills nan = PlayerSkills.read(bufferWith(null, 1, Float.NaN));
        assertEquals(0f, nan.overflow(S));
        PlayerSkills inf = PlayerSkills.read(bufferWith(null, 1, Float.POSITIVE_INFINITY));
        assertEquals(0f, inf.overflow(S));
        assertEquals(0f, new PlayerSkills.MasteryState(1, Float.NaN).overflow());
    }

    @Test
    void deathEmptiesTheBarButNeverTakesAStar() {
        PlayerSkills skills = maxed();
        skills.addXpMastery(S, Mastery.starCost(1) + Mastery.starCost(2) + 500f);
        assertEquals(2, skills.stars(S));
        assertTrue(skills.overflow(S) > 0f);

        Map<Skill, Float> lost = skills.applyDeathPenalty();

        assertEquals(2, skills.stars(S), "an earned star is permanent");
        assertEquals(0f, skills.overflow(S));
        assertEquals(SkillMath.MAX_LEVEL, skills.level(S));
        assertTrue(lost.containsKey(S), "the wiped bar is reported like any other");
        assertTrue(lost.get(S) > 0f);
    }

    @Test
    void deathWithAnEmptyBarReportsNothing() {
        PlayerSkills skills = maxed();
        skills.setStars(S, 3);
        assertTrue(skills.applyDeathPenalty().isEmpty());
        assertEquals(3, skills.stars(S));
    }

    @Test
    void aWardKeepsItsShareOfTheMasteryBarLikeAnyOtherBar() {
        PlayerSkills skills = maxed();
        // Any death-ward node will do: find one and fill it.
        Talent ward = null;
        for (Talent talent : Talents.of(S)) {
            if (talent.perRank().containsKey(PerkEffect.DEATH_WARD)) {
                ward = talent;
            }
        }
        if (ward == null) {
            return; // Mining has no ward node; the bar path is the same code as the level bar's.
        }
        skills.setRank(ward, ward.maxRank());
        skills.addXpMastery(S, 400f);
        float before = skills.overflow(S);
        skills.applyDeathPenalty();
        assertEquals(before * skills.deathWard(S), skills.overflow(S), 1e-2f);
    }

    @Test
    void settingALowerLevelClearsStarsAndTheBar() {
        PlayerSkills skills = maxed();
        skills.addXpMastery(S, Mastery.starCost(1) + 50f);
        skills.setLevel(S, 99);
        assertEquals(0, skills.stars(S));
        assertEquals(0f, skills.overflow(S));

        skills.setLevel(S, 100);
        skills.setStars(S, 4);
        skills.reset();
        assertEquals(0, skills.stars(S));
    }

    @Test
    void starsAreCosmeticTheyChangeNoNumber() {
        PlayerSkills plain = maxed();
        PlayerSkills starred = maxed();
        starred.setStars(S, 5);
        for (Skill skill : Skill.VALUES) {
            assertEquals(plain.bonus(skill), starred.bonus(skill), 0.0);
            assertEquals(plain.procChance(skill), starred.procChance(skill), 0.0);
            assertEquals(plain.pointsAvailable(skill), starred.pointsAvailable(skill));
            assertEquals(plain.pointsEarned(skill), starred.pointsEarned(skill));
        }
        assertEquals(100, starred.pointsEarned(S));
    }

    @Test
    void starsAndBarSurviveASaveAndLoad() {
        PlayerSkills skills = maxed();
        skills.addXpMastery(S, Mastery.starCost(1) + Mastery.starCost(2) + 77f);

        JsonElement encoded = PlayerSkills.CODEC.encodeStart(JsonOps.INSTANCE, skills)
                .getOrThrow(message -> new AssertionError("encode failed: " + message));
        PlayerSkills restored = PlayerSkills.CODEC.parse(JsonOps.INSTANCE, encoded)
                .getOrThrow(message -> new AssertionError("decode failed: " + message));

        assertEquals(2, restored.stars(S));
        assertEquals(77f, restored.overflow(S), 1e-2f);
        assertEquals(SkillMath.MAX_LEVEL, restored.level(S));
    }

    @Test
    void aSaveFromBeforeMasteryLoadsWithZeroStars() {
        // A level 100 skill saved by 1.2.0: no "mastery" field at all.
        JsonObject skills = new JsonObject();
        JsonObject mining = new JsonObject();
        mining.addProperty("level", 100);
        mining.addProperty("xp", 0f);
        skills.add("mining", mining);
        JsonObject root = new JsonObject();
        root.add("skills", skills);

        PlayerSkills restored = PlayerSkills.CODEC.parse(JsonOps.INSTANCE, root)
                .getOrThrow(message -> new AssertionError("decode failed: " + message));

        assertEquals(100, restored.level(Skill.MINING));
        assertEquals(0, restored.stars(Skill.MINING));
        assertEquals(0f, restored.overflow(Skill.MINING));
    }

    @Test
    void staleMasteryDataOnALowerSkillIsDropped() {
        JsonObject root = new JsonObject();
        JsonObject mastery = new JsonObject();
        JsonObject entry = new JsonObject();
        entry.addProperty("stars", 4);
        entry.addProperty("overflow", 9f);
        mastery.add("mining", entry);
        root.add("mastery", mastery);

        PlayerSkills restored = PlayerSkills.CODEC.parse(JsonOps.INSTANCE, root)
                .getOrThrow(message -> new AssertionError("decode failed: " + message));

        assertEquals(0, restored.stars(Skill.MINING));
    }

    @Test
    void copyFromCarriesStarsAcrossARespawn() {
        PlayerSkills old = maxed();
        old.addXpMastery(S, Mastery.starCost(1) + 10f);
        PlayerSkills fresh = new PlayerSkills();
        fresh.copyFrom(old);
        assertEquals(1, fresh.stars(S));
        assertEquals(10f, fresh.overflow(S), 1e-2f);
    }

    /** The sync packet's byte layout through an in-memory buffer. */
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

    @Test
    void theSyncPacketRoundTripsStarsAndTheBar() {
        PlayerSkills skills = maxed();
        skills.setLevel(Skill.SWORDS, 100);
        skills.addXpMastery(S, Mastery.starCost(1) + Mastery.starCost(2) + 31f);
        skills.setStars(Skill.SWORDS, 5);
        skills.setLevel(Skill.FARMING, 42);

        Buffer buffer = new Buffer();
        skills.write(buffer);
        PlayerSkills read = PlayerSkills.read(buffer);

        assertTrue(buffer.queue.isEmpty(), "the reader consumes exactly what the writer wrote");
        assertEquals(2, read.stars(S));
        assertEquals(31f, read.overflow(S), 1e-2f);
        assertEquals(5, read.stars(Skill.SWORDS));
        assertEquals(0, read.stars(Skill.FARMING));
        assertEquals(42, read.level(Skill.FARMING));
    }

    private static Buffer bufferWith(Buffer unused, int stars, float overflow) {
        PlayerSkills base = new PlayerSkills();
        Buffer buffer = new Buffer();
        base.write(buffer);
        // Replace the mastery block (34 skills of varint + float) with hostile values. The rested
        // block (34 floats and varints) follows it and is kept as it was.
        Object[] all = buffer.queue.toArray();
        buffer.queue.clear();
        int mastery = Skill.VALUES.length * 2;
        int rested = Skill.VALUES.length * 2;
        for (int i = 0; i < all.length - mastery - rested; i++) {
            buffer.queue.add(all[i]);
        }
        for (int i = 0; i < Skill.VALUES.length; i++) {
            buffer.queue.add(stars);
            buffer.queue.add(overflow);
        }
        for (int i = all.length - rested; i < all.length; i++) {
            buffer.queue.add(all[i]);
        }
        return buffer;
    }

    @Test
    void hostileValuesAreClampedOnRead() {
        PlayerSkills read = PlayerSkills.read(bufferWith(null, 99, -5f));
        assertEquals(Mastery.MAX_STARS, read.stars(S));
        assertEquals(0f, read.overflow(S));
    }

    private static void installCap(int cap) {
        SkillTuning.install(new SkillTuning() {
            @Override public double curveFloor() { return DEFAULTS.curveFloor(); }
            @Override public double curveBase() { return DEFAULTS.curveBase(); }
            @Override public double curveExponent() { return DEFAULTS.curveExponent(); }
            @Override public boolean enabled(Skill skill) { return true; }
            @Override public double maxBonus(Skill skill) { return DEFAULTS.maxBonus(skill); }
            @Override public boolean procsEnabled() { return true; }
            @Override public int procUnlockLevel() { return 25; }
            @Override public double procFloor() { return 0.05; }
            @Override public double procChance(Skill skill) { return DEFAULTS.procChance(skill); }
            @Override public int masteryMaxStars() { return cap; }
        });
    }

    @Test
    void withStarsOffALevel100BarReadsFullNotEmpty() {
        installCap(0);
        PlayerSkills skills = maxed();
        assertEquals(1.0f, skills.barProgress(S), "1.2.0 showed a full bar at 100; so must a switched-off feature");
        assertEquals(1.0f, skills.starProgress(S));
    }

    @Test
    void shownStarsNeverExceedTheLoweredCap() {
        installCap(3);
        assertEquals(3, Mastery.shownStars(5), "a lowered cap must not show 5/3");
        assertEquals(2, Mastery.shownStars(2));
        assertEquals(0, Mastery.shownStars(-1));
        installCap(0);
        assertEquals(5, Mastery.shownStars(5), "off: earned stars still show, out of five");
    }

    @Test
    void anOpStarCountIsClampedToTheCap() {
        installCap(3);
        assertEquals(3, Mastery.clampOpStars(5));
        assertEquals(0, Mastery.clampOpStars(0));
        assertEquals(0, Mastery.clampOpStars(-4));
        installCap(0);
        assertEquals(0, Mastery.clampOpStars(5));
    }

    @Test
    void theResultSaysHowMuchWasBankedPastTheLastStar() {
        installCap(1);
        PlayerSkills skills = maxed();
        float cost = Mastery.starCost(1);
        Mastery.Result first = skills.addXpMastery(S, cost + 400f);
        assertEquals(1, first.stars());
        assertEquals(cost, first.bankedOf(cost + 400f), 1e-2f, "the 400 over the last star was thrown away");
        Mastery.Result after = skills.addXpMastery(S, 250f);
        assertEquals(0f, after.bankedOf(250f), "capped XP is not banked");
        installCap(5);
        PlayerSkills fresh = maxed();
        assertEquals(100f, fresh.addXpMastery(S, 100f).bankedOf(100f), 1e-3f, "below the cap all of it stays");
        PlayerSkills climbing = new PlayerSkills();
        assertEquals(7f, climbing.addXpMastery(S, 7f).bankedOf(7f), 1e-3f, "so does XP on the way up");
    }
}
