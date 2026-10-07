package dev.amman.proficiency;

import com.mojang.serialization.JsonOps;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A frenzy is deliberately {@code transient} (see {@link PlayerSkills#frenzyUntil} in source) while
 * its cooldown is persisted, so a save/load round trip must keep the cooldown but always come back
 * with no frenzy in progress, regardless of how long the frenzy still had to run when it was saved.
 */
class PlayerSkillsFrenzyPersistenceTest {

    @Test
    void codecRoundTripKeepsTheCooldownButDropsAnInProgressFrenzy() {
        PlayerSkills original = new PlayerSkills();
        long now = 1_000L;
        original.beginFrenzy(Skill.MINING, now + 400L, now + 6_000L);
        // Confirm the frenzy is genuinely still active before we encode it.
        assertEquals(400L, original.frenzyRemaining(Skill.MINING, now));

        var encoded = PlayerSkills.CODEC.encodeStart(JsonOps.INSTANCE, original)
                .getOrThrow(false, message -> { throw new AssertionError("encode failed: " + message); });
        PlayerSkills restored = PlayerSkills.CODEC.parse(JsonOps.INSTANCE, encoded)
                .getOrThrow(false, message -> { throw new AssertionError("decode failed: " + message); });

        assertEquals(6_000L, restored.cooldownRemaining(Skill.MINING, now),
                "the persisted cooldown must survive a round trip");
        assertEquals(0L, restored.frenzyRemaining(Skill.MINING, now),
                "a frenzy must never survive a round trip, even mid-flight");
    }
}
