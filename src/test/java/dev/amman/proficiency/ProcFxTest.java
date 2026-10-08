package dev.amman.proficiency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.amman.proficiency.skill.ProcFx;
import dev.amman.proficiency.skill.Skill;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Every skill has its own proc recipe and every recipe stays inside the particle budget. */
class ProcFxTest {

    @Test
    void everySkillHasARecipe() {
        for (Skill skill : Skill.VALUES) {
            assertNotNull(ProcFx.recipe(skill), "no proc recipe for " + skill);
        }
    }

    @Test
    void everyRecipeRespectsTheCap() {
        for (Skill skill : Skill.VALUES) {
            ProcFx.Recipe recipe = ProcFx.recipe(skill);
            assertFalse(recipe.layers().isEmpty(), skill + " has no layers");
            assertTrue(recipe.particles() >= 1 && recipe.particles() <= ProcFx.MAX_PARTICLES,
                    skill + " spawns " + recipe.particles());
            assertTrue(recipe.ticks() <= ProcFx.MAX_TICKS, skill + " runs " + recipe.ticks() + " ticks");
            for (ProcFx.Layer layer : recipe.layers()) {
                assertTrue(layer.count() > 0 && layer.delay() >= 0 && layer.over() >= 0, skill.toString());
            }
        }
    }

    @Test
    void noTwoSkillsShareARecipe() {
        Set<String> seen = new HashSet<>();
        for (Skill skill : Skill.VALUES) {
            assertTrue(seen.add(ProcFx.recipe(skill).layers().toString()), skill + " copies another recipe");
        }
        assertEquals(Skill.VALUES.length, seen.size());
    }
}
