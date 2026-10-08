package dev.amman.proficiency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillPassives;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The stat table the tooltips show: every skill has lines, level 0 shows no change, formulas agree. */
class SkillPassivesTest {

    private static final Path EN = TestPaths.LANG.resolve("en_us.json");

    @Test
    void everySkillShowsAtLeastOneStatAndLevelZeroChangesNothing() {
        PlayerSkills fresh = new PlayerSkills();
        for (Skill skill : Skill.VALUES) {
            List<SkillPassives.Stat> stats = SkillPassives.of(fresh, skill);
            assertFalse(stats.isEmpty(), skill + " has no stat line");
            for (SkillPassives.Stat stat : stats) {
                assertEquals(stat.base(), stat.current(), 1e-12, skill + " " + stat.labelKey());
            }
        }
    }

    @Test
    void aMaxedSkillChangesItsFirstStat() {
        for (Skill skill : Skill.VALUES) {
            PlayerSkills skills = new PlayerSkills();
            skills.setLevel(skill, 100);
            SkillPassives.Stat stat = SkillPassives.of(skills, skill).get(0);
            assertNotEquals(stat.base(), stat.current(), skill + " " + stat.labelKey());
        }
    }

    @Test
    void everyStatHasALabelInEnglish() throws IOException {
        JsonObject lang;
        try (Reader reader = Files.newBufferedReader(EN, StandardCharsets.UTF_8)) {
            lang = JsonParser.parseReader(reader).getAsJsonObject();
        }
        PlayerSkills skills = new PlayerSkills();
        for (Skill skill : Skill.VALUES) {
            for (SkillPassives.Stat stat : SkillPassives.of(skills, skill)) {
                assertTrue(lang.has(stat.labelKey()), "no label " + stat.labelKey());
            }
        }
    }

    @Test
    void theNumbersAreTheOnesGameplayUses() {
        PlayerSkills skills = new PlayerSkills();
        skills.setLevel(Skill.MINING, 50);
        List<SkillPassives.Stat> mining = SkillPassives.of(skills, Skill.MINING);
        double bonus = skills.bonus(Skill.MINING);
        assertEquals(1.0 + bonus, mining.get(0).current(), 1e-12, "break speed");
        assertEquals(bonus * SkillPassives.DROP_SHARE, mining.get(1).current(), 1e-12, "double drop");
        assertEquals(4.5 + 1.0, mining.get(2).current(), 1e-12, "reach at level 50");
        assertEquals(4.5, mining.get(2).base(), 1e-12);

        skills.setLevel(Skill.ENDURANCE, 100);
        assertEquals(40.0, SkillPassives.of(skills, Skill.ENDURANCE).get(0).current(), 1e-9);
        assertEquals(20.0, SkillPassives.of(skills, Skill.ENDURANCE).get(0).base(), 1e-9);

        skills.setLevel(Skill.BLOCKING, 100);
        assertEquals(1.0 - Math.min(0.9, skills.bonus(Skill.BLOCKING)),
                SkillPassives.of(skills, Skill.BLOCKING).get(0).current(), 1e-12);

        skills.setLevel(Skill.FARMING, 100);
        assertEquals(skills.bonus(Skill.FARMING), SkillPassives.of(skills, Skill.FARMING).get(0).current(), 1e-12,
                "farming's second harvest is the whole passive");
    }

    @Test
    void theFormulasKeepTheirCaps() {
        assertEquals(0.1, SkillPassives.lessUpTo90(5.0), 1e-12);
        assertEquals(0.25, SkillPassives.anvilCostFactor(5.0), 1e-12);
        assertEquals(0.7, SkillPassives.fallFactor(0.6), 1e-12);
        assertEquals(0.4, SkillPassives.fallFactor(5.0), 1e-12);
        assertEquals(10.0, SkillPassives.airLasts(5.0), 1e-9);
        assertEquals(1.0, SkillPassives.more(0.0), 1e-12);
        assertEquals(1.0, SkillPassives.lessUpTo90(0.0), 1e-12);
    }
}
