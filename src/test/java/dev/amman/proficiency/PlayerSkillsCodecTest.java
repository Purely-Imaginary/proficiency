package dev.amman.proficiency;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import dev.amman.proficiency.perk.Talent;
import dev.amman.proficiency.perk.Talents;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The codec grew to carry perks, cooldowns and visited places after
 * {@link PlayerSkillsTest#survivesASaveAndLoadRoundTrip()} was written, which only exercises levels
 * and xp. These cover the newer fields, and the one load-time safety net that field also needs: a
 * perk key that no longer exists in the table must not crash the load.
 */
class PlayerSkillsCodecTest {

    @Test
    void perksCooldownsAndVisitedPlacesSurviveARoundTrip() {
        PlayerSkills original = new PlayerSkills();
        original.setLevel(Skill.WOODCUTTING, 50);
        String perkKey = Talents.of(Skill.WOODCUTTING).get(0).key();
        original.addRank(Talents.byKey(perkKey));
        original.beginFrenzy(Skill.WOODCUTTING, 1_000L, 5_000L);
        original.markVisited("minecraft:plains");
        original.markVisited("minecraft:the_nether");

        JsonElement encoded = PlayerSkills.CODEC.encodeStart(JsonOps.INSTANCE, original)
                .getOrThrow(message -> new AssertionError("encode failed: " + message));
        PlayerSkills restored = PlayerSkills.CODEC.parse(JsonOps.INSTANCE, encoded)
                .getOrThrow(message -> new AssertionError("decode failed: " + message));

        assertTrue((restored.rank(perkKey) > 0), "the unlocked perk should survive");
        assertEquals(5_000L, restored.cooldownRemaining(Skill.WOODCUTTING, 0L),
                "the persisted cooldown should survive");
        assertTrue(restored.hasVisited("minecraft:plains"));
        assertTrue(restored.hasVisited("minecraft:the_nether"));
        assertEquals(2, restored.placesSeen());
    }

    @Test
    void aPerkKeyThatNoLongerExistsIsDroppedWithoutThrowing() {
        PlayerSkills original = new PlayerSkills();
        String stillValidKey = Talents.of(Skill.MINING).get(0).key();
        original.addRank(Talents.byKey(stillValidKey));

        JsonElement encoded = PlayerSkills.CODEC.encodeStart(JsonOps.INSTANCE, original)
                .getOrThrow(message -> new AssertionError("encode failed: " + message));

        // Simulate a mod update that removed a perk: splice an unknown key into the saved list,
        // the way an old playerdata file would look after the table changed under it.
        JsonObject root = encoded.getAsJsonObject();
        JsonObject talents = root.getAsJsonObject("talents");
        talents.addProperty("mining/no_longer_exists", 3);

        PlayerSkills restored = assertDoesNotThrow(() ->
                        PlayerSkills.CODEC.parse(JsonOps.INSTANCE, root)
                                .getOrThrow(message -> new AssertionError("decode failed: " + message)),
                "a stale perk key must be dropped at load, not crash the load");

        assertTrue((restored.rank(stillValidKey) > 0), "the still-valid perk should remain");
        assertFalse((restored.rank("mining/no_longer_exists") > 0),
                "an unknown perk key must not survive the load");
    }

    @Test
    void aSkillIdThatNoLongerExistsIsDroppedWithoutThrowing() {
        PlayerSkills original = new PlayerSkills();
        original.setLevel(Skill.MINING, 12);

        JsonElement encoded = PlayerSkills.CODEC.encodeStart(JsonOps.INSTANCE, original)
                .getOrThrow(message -> new AssertionError("encode failed: " + message));

        JsonObject root = encoded.getAsJsonObject();
        JsonObject skillsObject = root.getAsJsonObject("skills");
        JsonObject bogusState = new JsonObject();
        bogusState.addProperty("level", 99);
        bogusState.addProperty("xp", 0.0f);
        skillsObject.add("no_such_skill", bogusState);

        PlayerSkills restored = assertDoesNotThrow(() ->
                        PlayerSkills.CODEC.parse(JsonOps.INSTANCE, root)
                                .getOrThrow(message -> new AssertionError("decode failed: " + message)),
                "a stale skill id must be dropped at load, not crash the load");

        assertEquals(12, restored.level(Skill.MINING));
    }
}
