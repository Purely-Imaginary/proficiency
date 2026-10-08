package dev.amman.proficiency;

import dev.amman.proficiency.client.RestedBar;
import dev.amman.proficiency.perk.Talent;
import dev.amman.proficiency.perk.Talents;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillMath;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The blue segment's pixel maths, and the three teaching nodes in the Social tree. */
class RestedClientTest {

    @Test
    void theBlueSegmentStartsAfterTheFillAndNeverLeavesTheBar() {
        assertEquals(0, RestedBar.pixels(0f, 70, 10));
        assertEquals(0, RestedBar.pixels(0.5f, 70, 70), "a full bar has no room");
        assertEquals(35, RestedBar.pixels(0.5f, 70, 10));
        assertEquals(60, RestedBar.pixels(1f, 70, 10), "cut at the end of the bar");
        assertEquals(1, RestedBar.pixels(0.001f, 70, 10), "any pool shows at least a pixel");
    }

    @Test
    void socialHasThreeTeachingNodesAndStillFitsAMaxedSkill() {
        List<String> specials = Talents.of(Skill.SOCIAL).stream().map(Talent::special).toList();
        for (String tag : new String[] {"teach_rate", "teach_radius", "teacher_cut"}) {
            assertTrue(specials.contains(tag), tag);
        }
        assertTrue(Talents.fullTreeCost(Skill.SOCIAL) <= SkillMath.MAX_LEVEL / Talents.LEVELS_PER_POINT,
                "Social costs " + Talents.fullTreeCost(Skill.SOCIAL));
    }
}
