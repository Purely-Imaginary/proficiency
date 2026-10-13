package dev.amman.proficiency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.amman.proficiency.client.CalloutFade;
import dev.amman.proficiency.perk.Talent;
import dev.amman.proficiency.perk.Talents;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillTuning;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The signature waits for the tree's root as well as the level (user rule 2026-10-11): levelling
 * alone never switches a proc on for someone who has not opened the tree.
 */
class SignatureRootGateTest {

    @AfterEach
    void defaults() {
        SkillTuning.install(SkillTuning.DEFAULTS);
    }

    private static void fill(PlayerSkills skills, Talent talent, int ranks) {
        for (int i = 0; i < ranks; i++) {
            skills.addRank(talent);
        }
    }

    @Test
    void everySkillHasARoot() {
        for (Skill skill : Skill.VALUES) {
            Talent root = Talents.root(skill);
            assertNotNull(root, skill.id());
            assertEquals(Talent.Kind.ROOT, root.kind());
        }
    }

    @Test
    void levelAloneGivesNoProc() {
        PlayerSkills skills = new PlayerSkills();
        for (Skill skill : Skill.VALUES) {
            skills.setLevel(skill, 100);
            assertFalse(skills.rootOpen(skill), skill.id());
            assertEquals(0.0, skills.procChance(skill), skill.id());
        }
    }

    @Test
    void aPartRootIsStillShut() {
        PlayerSkills skills = new PlayerSkills();
        skills.setLevel(Skill.MINING, 60);
        Talent root = Talents.root(Skill.MINING);
        fill(skills, root, root.maxRank() - 1);
        assertFalse(skills.rootOpen(Skill.MINING));
        assertEquals(0.0, skills.procChance(Skill.MINING));
        skills.addRank(root);
        assertTrue(skills.rootOpen(Skill.MINING));
        assertTrue(skills.procChance(Skill.MINING) > 0);
    }

    @Test
    void theRootAloneGivesNoProcBelowTheLevel() {
        PlayerSkills skills = new PlayerSkills();
        skills.setLevel(Skill.MINING, 10);
        Talent root = Talents.root(Skill.MINING);
        fill(skills, root, root.maxRank());
        assertTrue(skills.rootOpen(Skill.MINING));
        assertEquals(0.0, skills.procChance(Skill.MINING));
    }

    @Test
    void aServerCanTurnTheGateOff() {
        // Everything as the defaults, except the gate.
        SkillTuning.install((SkillTuning) java.lang.reflect.Proxy.newProxyInstance(
                SkillTuning.class.getClassLoader(), new Class<?>[] {SkillTuning.class},
                (proxy, method, args) -> method.getName().equals("signatureNeedsRoot") ? Boolean.FALSE
                        : method.invoke(SkillTuning.DEFAULTS, args)));
        PlayerSkills skills = new PlayerSkills();
        skills.setLevel(Skill.MINING, 40);
        assertTrue(skills.rootOpen(Skill.MINING));
        assertTrue(skills.procChance(Skill.MINING) > 0);
    }

    @Test
    void theCalloutHoldsThenFadesAndRises() {
        assertEquals(1f, CalloutFade.alpha(0));
        assertEquals(1f, CalloutFade.alpha(CalloutFade.HOLD_MS));
        assertTrue(CalloutFade.alpha(CalloutFade.HOLD_MS + CalloutFade.FADE_MS / 2) < 1f);
        assertEquals(0f, CalloutFade.alpha(CalloutFade.TOTAL_MS));
        assertFalse(CalloutFade.active(CalloutFade.TOTAL_MS));
        assertEquals(0f, CalloutFade.rise(CalloutFade.HOLD_MS));
        assertEquals(CalloutFade.RISE, CalloutFade.rise(CalloutFade.TOTAL_MS - 1), 0.05f);
        assertTrue(CalloutFade.scale(0) > 1f);
        assertEquals(1f, CalloutFade.scale(CalloutFade.POP_MS));
        // Never an alpha under 4, which the font would draw fully opaque.
        assertTrue((CalloutFade.withAlpha(0xFFFFFF, 0f) >>> 24) >= 4);
        float last = 2f;
        for (long t = 0; t < CalloutFade.TOTAL_MS; t += 25) {
            float a = CalloutFade.alpha(t);
            assertTrue(a <= last, "fade must never brighten at " + t);
            last = a;
        }
    }
}
