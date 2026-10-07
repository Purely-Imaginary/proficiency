package dev.amman.proficiency;

import com.mojang.serialization.JsonOps;
import dev.amman.proficiency.skill.PlayerSkills;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PlayerSkills#markVisited} is the only thing Wayfaring's "somewhere new" payment is gated
 * on: it must pay exactly once per place, survive a save/load round trip, and travel across death
 * via {@link PlayerSkills#copyFrom}.
 */
class PlayerSkillsVisitedTest {

    @Test
    void markVisitedReturnsTrueOnlyTheFirstTime() {
        PlayerSkills skills = new PlayerSkills();
        assertTrue(skills.markVisited("minecraft:plains"), "first visit should pay");
        assertFalse(skills.markVisited("minecraft:plains"), "second visit must not pay again");
        assertFalse(skills.markVisited("minecraft:plains"), "nor a third time");
        assertEquals(1, skills.placesSeen());
    }

    @Test
    void markVisitedMarksTheStateDirtyOnlyOnANewPlace() {
        PlayerSkills skills = new PlayerSkills();
        skills.clearDirty();
        skills.markVisited("minecraft:plains");
        assertTrue(skills.isDirty(), "a genuinely new place must dirty the state");

        skills.clearDirty();
        skills.markVisited("minecraft:plains");
        assertFalse(skills.isDirty(), "revisiting the same place must not dirty the state");
    }

    @Test
    void visitedPlacesSurviveACodecRoundTrip() {
        PlayerSkills original = new PlayerSkills();
        original.markVisited("minecraft:plains");
        original.markVisited("minecraft:the_end");

        var encoded = PlayerSkills.CODEC.encodeStart(JsonOps.INSTANCE, original)
                .getOrThrow(message -> new AssertionError("encode failed: " + message));
        PlayerSkills restored = PlayerSkills.CODEC.parse(JsonOps.INSTANCE, encoded)
                .getOrThrow(message -> new AssertionError("decode failed: " + message));

        assertTrue(restored.hasVisited("minecraft:plains"));
        assertTrue(restored.hasVisited("minecraft:the_end"));
        assertEquals(2, restored.placesSeen());
        // And a round trip must not somehow let the same place pay out again.
        assertFalse(restored.markVisited("minecraft:plains"),
                "a place already visited before saving must still be visited after loading");
    }

    @Test
    void copyFromCarriesVisitedPlaces() {
        PlayerSkills source = new PlayerSkills();
        source.markVisited("minecraft:plains");
        source.markVisited("minecraft:nether_wastes");

        PlayerSkills destination = new PlayerSkills();
        destination.markVisited("minecraft:plains_already_had_this_one");
        destination.copyFrom(source);

        assertTrue(destination.hasVisited("minecraft:plains"));
        assertTrue(destination.hasVisited("minecraft:nether_wastes"));
        assertEquals(2, destination.placesSeen(),
                "copyFrom must replace the destination's visited set, not merge into it");
    }

    @Test
    void hasAnyPlaceIgnoresFirstTimeAndStructureKeys() {
        PlayerSkills skills = new PlayerSkills();
        assertFalse(skills.hasAnyPlace());
        skills.markVisited("first:mining:minecraft:iron_ore");
        skills.markVisited("structure:minecraft:village");
        assertEquals(2, skills.placesSeen());
        assertFalse(skills.hasAnyPlace(), "these are not the spawn place");
        skills.markVisited("biome:minecraft:plains");
        assertTrue(skills.hasAnyPlace());
        PlayerSkills other = new PlayerSkills();
        other.markVisited("dim:minecraft:overworld");
        assertTrue(other.hasAnyPlace());
    }
}
