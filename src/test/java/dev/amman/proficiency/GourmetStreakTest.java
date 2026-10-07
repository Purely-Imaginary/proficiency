package dev.amman.proficiency;

import dev.amman.proficiency.event.TalentCraftingEvents.FoodStreak;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GourmetStreakTest {

    private static boolean eatAll(FoodStreak<String> streak, String... foods) {
        boolean completed = false;
        for (String food : foods) {
            completed = streak.eat(food);
        }
        return completed;
    }

    @Test
    void fiveDifferentFoodsComplete() {
        FoodStreak<String> streak = new FoodStreak<>(5);
        assertFalse(eatAll(streak, "bread", "apple", "steak", "carrot"));
        assertTrue(streak.eat("pie"));
    }

    @Test
    void aRepeatRestartsJustAfterTheEarlierServing() {
        FoodStreak<String> streak = new FoodStreak<>(5);
        eatAll(streak, "a", "b", "c", "a");
        // b c a remain
        assertEquals(3, streak.size());
        assertFalse(streak.eat("d"));
        assertTrue(streak.eat("e"));
    }

    @Test
    void theSameFoodTwiceNeverCompletes() {
        FoodStreak<String> streak = new FoodStreak<>(5);
        for (int i = 0; i < 20; i++) {
            assertFalse(streak.eat("bread"));
        }
        assertEquals(1, streak.size());
    }

    @Test
    void aCompletedRunStartsOverSoTheRewardIsNotEveryMeal() {
        FoodStreak<String> streak = new FoodStreak<>(5);
        assertTrue(eatAll(streak, "a", "b", "c", "d", "e"));
        assertEquals(0, streak.size());
        assertFalse(streak.eat("f"));
        assertFalse(eatAll(streak, "g", "h", "i"));
        assertTrue(streak.eat("a"));
    }
}
