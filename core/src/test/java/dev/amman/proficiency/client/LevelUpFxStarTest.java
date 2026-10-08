package dev.amman.proficiency.client;

import dev.amman.proficiency.skill.Skill;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Mastery star moment: lighter than a level-up, never the gold one, no number roll. */
class LevelUpFxStarTest {

    @AfterEach
    void clean() {
        LevelUpFx.reset();
    }

    @Test
    void aStarIsNotTheGoldLevelUpEvenAtLevelOneHundred() {
        LevelUpFx.start(Skill.MINING, 99, 100, 0, false);
        assertTrue(LevelUpFx.gold(), "reaching 100 is the big gold moment");

        LevelUpFx.startStar(Skill.MINING, 2, 0, false);
        assertFalse(LevelUpFx.gold());
        assertEquals(2, LevelUpFx.star());
        assertEquals(1f, LevelUpFx.roll(10), "no number rolls for a star");
    }

    @Test
    void aStarIsShorterAndDimmerThanALevelUpAndTheFifthIsAsLongAsGold() {
        LevelUpFx.startStar(Skill.MINING, 1, 0, false);
        assertTrue(LevelUpFx.active(LevelUpFx.STAR_MS - 1));
        assertFalse(LevelUpFx.active(LevelUpFx.STAR_MS));
        assertTrue(LevelUpFx.flash(0) <= 0.5f);

        LevelUpFx.startStar(Skill.MINING, 5, 0, false);
        assertTrue(LevelUpFx.active(LevelUpFx.GOLD_MS - 1));
        assertFalse(LevelUpFx.active(LevelUpFx.GOLD_MS));
    }

    @Test
    void aBorrowedStarLineStaysUpLongerThanTheEffect() {
        LevelUpFx.startStar(Skill.MINING, 3, 0, true);
        assertTrue(LevelUpFx.lineUp(LevelUpFx.STAR_MS + 100));
        assertFalse(LevelUpFx.lineUp(LevelUpFx.BORROW_STAR_MS));
    }

    @Test
    void theQueuedStarCountIsReadAfterTheSkillIsTaken() {
        LevelUpFx.queueStar(Skill.SWORDS, 4);
        assertEquals(Skill.SWORDS, LevelUpFx.takeQueuedSkill());
        assertEquals(4, LevelUpFx.queuedStars());
        LevelUpFx.queue(Skill.SWORDS, 12);
        LevelUpFx.takeQueuedSkill();
        assertEquals(0, LevelUpFx.queuedStars(), "an ordinary level-up clears the star count");
    }

    @Test
    void anOrdinaryStartForgetsTheStar() {
        LevelUpFx.startStar(Skill.MINING, 5, 0, false);
        LevelUpFx.start(Skill.MINING, 9, 10, 5000, false);
        assertEquals(0, LevelUpFx.star());
    }
}
