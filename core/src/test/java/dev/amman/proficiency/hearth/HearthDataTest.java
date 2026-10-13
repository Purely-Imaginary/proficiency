package dev.amman.proficiency.hearth;

import dev.amman.proficiency.skill.Skill;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Hearth datapack model: the shipped file, merging, replace, and bad values. */
class HearthDataTest {

    static final String DEFAULT_PATH = "data/proficiency/proficiency/hearth/default.json";

    static String shipped() throws IOException {
        try (InputStream in = HearthDataTest.class.getClassLoader().getResourceAsStream(DEFAULT_PATH)) {
            assertNotNull(in, "the shipped " + DEFAULT_PATH + " is on the test classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void theShippedFileIsExactlyTheCodeDefaults() throws IOException {
        HearthDataLoader.Result result = HearthDataLoader.parse("proficiency:hearth/default", shipped());
        assertTrue(result.messages().isEmpty(), "no problems in the shipped file: " + result.messages());
        assertEquals(HearthData.DEFAULTS, result.data());
        assertEquals(1, result.files());
    }

    @Test
    void noFileAtAllMeansTheDefaults() {
        HearthDataLoader.Result result = HearthDataLoader.load(List.of());
        assertEquals(HearthData.DEFAULTS, result.data());
        assertEquals(0, result.files());
    }

    @Test
    void aLaterFileOverridesOneNumberAndKeepsTheRest() throws IOException {
        HearthDataLoader.Result result = HearthDataLoader.load(List.of(
                new HearthDataLoader.SourceFile("proficiency:hearth/default", shipped()),
                new HearthDataLoader.SourceFile("pack:hearth/tune",
                        "{\"siege\": {\"pMax\": 0.05}, \"pools\": {\"hearty\": {\"values\": [1,2,3,4,5,6]}}}")));
        assertTrue(result.messages().isEmpty(), result.messages().toString());
        HearthData d = result.data();
        assertEquals(0.05, d.siege().pMax(), 1e-12);
        assertEquals(HearthData.DEFAULTS.siege().cooldownPlayHours(), d.siege().cooldownPlayHours(), 1e-12);
        assertEquals(List.of(1.0, 2.0, 3.0, 4.0, 5.0, 6.0), d.pool(HearthPool.HEARTY).values());
        // The rest of the pool is kept.
        assertEquals(BoonType.HUNGER_SLOW, d.pool(HearthPool.HEARTY).boon());
        assertEquals(HearthData.DEFAULTS.pool(HearthPool.HEARTY).feeds(), d.pool(HearthPool.HEARTY).feeds());
    }

    @Test
    void theShippedDefaultsLoadFirstThenOurOtherFilesThenOtherNamespaces() throws IOException {
        // As a resource manager might list them: a lane's own file sorts before "default" by path.
        List<HearthDataLoader.SourceFile> listed = List.of(
                new HearthDataLoader.SourceFile("zpack:hearth/a", "{}"),
                new HearthDataLoader.SourceFile("proficiency:hearth/comfort", "{\"hourlyCap\": 321}"),
                new HearthDataLoader.SourceFile("apack:hearth/z", "{}"),
                new HearthDataLoader.SourceFile("apack:hearth/b", "{}"),
                new HearthDataLoader.SourceFile("proficiency:hearth/default", shipped()),
                new HearthDataLoader.SourceFile("proficiency:hearth/zz", "{}"));
        List<String> order = HearthDataLoader.ordered(listed).stream().map(HearthDataLoader.SourceFile::name).toList();
        assertEquals(List.of("proficiency:hearth/default", "proficiency:hearth/comfort", "proficiency:hearth/zz",
                "apack:hearth/b", "apack:hearth/z", "zpack:hearth/a"), order);
        // So the lane's number wins over the shipped default, not the other way round.
        assertEquals(321, HearthDataLoader.load(HearthDataLoader.ordered(listed)).data().hourlyCap(), 1e-12);
        assertEquals(HearthDataLoader.DEFAULT_FILE, "proficiency:hearth/default");
    }

    @Test
    void replaceDropsEverythingBeforeAndDefaultsFillTheGaps() throws IOException {
        HearthDataLoader.Result result = HearthDataLoader.load(List.of(
                new HearthDataLoader.SourceFile("proficiency:hearth/default", shipped()),
                new HearthDataLoader.SourceFile("pack:hearth/a", "{\"mirrorShare\": 0.5}"),
                new HearthDataLoader.SourceFile("pack:hearth/b", "{\"replace\": true, \"hourlyCap\": 50}")));
        assertEquals(50, result.data().hourlyCap(), 1e-12);
        // pack:a's 0.5 went with the replace; the code default fills in.
        assertEquals(HearthData.DEFAULTS.mirrorShare(), result.data().mirrorShare(), 1e-12);
    }

    @Test
    void aBadValueKeepsItsDefaultAndSaysSo() {
        HearthDataLoader.Result result = HearthDataLoader.parse("pack:hearth/bad",
                "{\"mirrorShare\": 2, \"thresholds\": [1, 2, 3], \"pools\": {\"regen\": {\"boon\": \"flying\"}},"
                        + " \"siege\": {\"waves\": 2.5}, \"nonsense\": 1}");
        HearthData d = result.data();
        assertEquals(HearthData.DEFAULTS.mirrorShare(), d.mirrorShare(), 1e-12);
        assertEquals(HearthData.DEFAULTS.thresholds(), d.thresholds());
        assertEquals(BoonType.REGEN_OUT_OF_COMBAT, d.pool(HearthPool.REGEN).boon());
        assertEquals(3, d.siege().waves());
        assertEquals(5, result.messages().size(), result.messages().toString());
    }

    @Test
    void thresholdsMustRise() {
        HearthDataLoader.Result result = HearthDataLoader.parse("pack:hearth/t",
                "{\"thresholds\": [10, 20, 20, 30, 40, 50]}");
        assertEquals(HearthData.DEFAULTS.thresholds(), result.data().thresholds());
        assertFalse(result.messages().isEmpty());
    }

    @Test
    void brokenJsonIsSkippedAndTheRestStays() {
        HearthDataLoader.Result result = HearthDataLoader.load(List.of(
                new HearthDataLoader.SourceFile("pack:hearth/a", "{\"hourlyCap\": 120}"),
                new HearthDataLoader.SourceFile("pack:hearth/b", "{ not json")));
        assertEquals(120, result.data().hourlyCap(), 1e-12);
        assertEquals(1, result.skippedFiles());
        assertEquals(HearthDataLoader.Level.ERROR, result.messages().get(0).level());
    }

    @Test
    void anUnknownSkillInFeedsIsSkippedNotFatal() {
        HearthDataLoader.Result result = HearthDataLoader.parse("pack:hearth/f",
                "{\"pools\": {\"hearty\": {\"feeds\": [\"mining\", \"jetpacking\"]}}}");
        assertEquals(List.of(HearthData.Feed.skill("mining")), result.data().pool(HearthPool.HEARTY).feeds());
        assertEquals(1, result.messages().size());
    }

    @Test
    void skillsFeedThePoolsOfTheSpec() {
        HearthData d = HearthData.DEFAULTS;
        for (Skill skill : new Skill[] {Skill.MINING, Skill.WOODCUTTING, Skill.EXCAVATION, Skill.FARMING, Skill.FISHING}) {
            assertEquals(HearthPool.HEARTY, d.poolFor(skill), skill.id());
        }
        assertEquals(HearthPool.REGEN, d.poolFor(Skill.DECORATING));
        assertEquals(HearthPool.FORTIFIED, d.poolFor(Skill.MASONRY));
        for (Skill skill : new Skill[] {Skill.SMITHING, Skill.COOKING, Skill.ALCHEMY}) {
            assertEquals(HearthPool.WORKSHOP, d.poolFor(skill), skill.id());
        }
        assertEquals(HearthPool.HOMECOMING, d.poolFor(Skill.SOCIAL));
        // Movement, Expedition, Survival, Mastery and combat feed nothing.
        for (Skill skill : new Skill[] {Skill.RUNNING, Skill.SWIMMING, Skill.WAYFARING, Skill.SPELUNKING,
                Skill.BEASTSLAYING, Skill.NIGHTWALKER, Skill.SPELLCASTING, Skill.ENGINEERING, Skill.SWORDS,
                Skill.ENDURANCE}) {
            assertNull(d.poolFor(skill), skill.id());
        }
    }

    @Test
    void aSkillNamedOutrightBeatsItsCategory() {
        HearthData d = HearthDataLoader.parse("pack:hearth/p",
                "{\"pools\": {\"workshop\": {\"feeds\": [\"#category:crafting\", \"mining\"]}}}").data();
        assertEquals(HearthPool.WORKSHOP, d.poolFor(Skill.MINING));
        assertEquals(HearthPool.HEARTY, d.poolFor(Skill.FARMING));
    }

    @Test
    void talesComeFromMovementAndExpedition() {
        HearthData d = HearthData.DEFAULTS;
        assertTrue(d.tales().banks(Skill.RUNNING));
        assertTrue(d.tales().banks(Skill.WAYFARING));
        assertTrue(d.tales().banks(Skill.SPELUNKING));
        assertFalse(d.tales().banks(Skill.MINING));
        assertEquals(HearthPool.HOMECOMING, d.talesPool());
        assertEquals(HearthPool.HOMECOMING, d.feastPool());
        assertEquals(HearthPool.HOMECOMING, d.poolWithBoon(BoonType.READINESS));
    }

    @Test
    void theDefaultsMatchTheSpec() {
        HearthData d = HearthData.DEFAULTS;
        assertEquals(List.of(200.0, 600.0, 1400.0, 2800.0, 5000.0, 8000.0), d.thresholds());
        assertEquals(12, d.comfort().max());
        assertEquals(6, d.stages().size());
        assertEquals("minecraft:heart_of_the_sea", d.stages().get(5).offering());
        assertEquals(600, d.pool(HearthPool.REGEN).holdSeconds());
        assertEquals(600, d.pool(HearthPool.FORTIFIED).holdSeconds());
        assertEquals(0, d.pool(HearthPool.HEARTY).holdSeconds());
        assertEquals(12, d.siege().baseMobs(6));
        assertEquals(40, d.character().forStage(6));
        assertEquals(0, d.character().forStage(2));
    }
}
