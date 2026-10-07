package dev.amman.proficiency;

import dev.amman.proficiency.skill.Skill;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link Skill#byId} feeds player-controlled input straight from commands and from playerdata
 * loaded off disk (see {@code PlayerSkills#fromParts}, which treats an unresolvable id as "drop
 * it, don't crash the load"). It has to fail quietly on anything that is not exactly one of the
 * twenty-seven known ids, including null and the empty string, rather than throwing.
 */
class SkillByIdEdgeCasesTest {

    @Test
    void byIdReturnsNullForNullInput() {
        assertNull(assertDoesNotThrow(() -> Skill.byId(null)));
    }

    @Test
    void byIdReturnsNullForEmptyString() {
        assertNull(Skill.byId(""));
    }

    @Test
    void byIdReturnsNullForAnUnknownId() {
        assertNull(Skill.byId("not_a_real_skill"));
    }

    @Test
    void byIdIsCaseSensitiveAndRejectsWrongCase() {
        assertNull(Skill.byId("Mining"));
        assertNull(Skill.byId("MINING"));
    }

    @Test
    void byIdRejectsIdWithSurroundingWhitespace() {
        assertNull(Skill.byId(" mining"));
        assertNull(Skill.byId("mining "));
    }
}
