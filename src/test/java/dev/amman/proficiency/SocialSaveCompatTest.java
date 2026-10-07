package dev.amman.proficiency;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import dev.amman.proficiency.perk.Talent;
import dev.amman.proficiency.perk.Talents;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Idea 35 added Social as the 29th skill. A save written before it has no "social" entry. It must
 * load with Social at level 0 and every other skill exactly as saved, and the sync packet must
 * carry the new skill both ways.
 */
class SocialSaveCompatTest {

    /** A playerdata attachment as the 28-skill mod wrote it: every skill but Social. */
    private static JsonObject oldSave() {
        JsonObject skills = new JsonObject();
        int i = 0;
        for (Skill skill : Skill.VALUES) {
            if (skill.ordinal() >= Skill.SOCIAL.ordinal()) {
                continue;
            }
            JsonObject state = new JsonObject();
            state.addProperty("level", 3 + i);
            state.addProperty("xp", 1.5f + i);
            skills.add(skill.id(), state);
            i++;
        }
        JsonObject talents = new JsonObject();
        talents.addProperty(Talents.of(Skill.MINING).get(0).key(), 2);
        JsonObject cooldowns = new JsonObject();
        cooldowns.addProperty("mining", 7_000L);
        JsonArray visited = new JsonArray();
        visited.add("biome:minecraft:plains");

        JsonObject root = new JsonObject();
        root.add("skills", skills);
        root.add("talents", talents);
        root.add("cooldowns", cooldowns);
        root.add("visited", visited);
        root.addProperty("onboarded", true);
        root.addProperty("streak", 12_345L);
        return root;
    }

    @Test
    void theEnumGrewAtTheEndSoOldOrdinalsDidNotMove() {
        // Social went in 29th, after the 28 older skills. Later skills (Nightwalker) come after it.
        assertSame(Skill.SOCIAL, Skill.VALUES[28]);
        assertSame(Skill.DECORATING, Skill.VALUES[27]);
        assertSame(Skill.SOCIAL, Skill.byId("social"));
    }

    @Test
    void anOldSaveLoadsWithSocialAtZeroAndNothingElseChanged() {
        PlayerSkills restored = PlayerSkills.CODEC.parse(JsonOps.INSTANCE, oldSave())
                .getOrThrow(false, message -> { throw new AssertionError("decode failed: " + message); });

        assertEquals(0, restored.level(Skill.SOCIAL));
        assertEquals(0f, restored.xp(Skill.SOCIAL));
        assertEquals(0, restored.pointsSpent(Skill.SOCIAL));
        assertEquals(0L, restored.cooldownRemaining(Skill.SOCIAL, 0L));
        for (Talent talent : Talents.of(Skill.SOCIAL)) {
            assertEquals(0, restored.rank(talent), talent.key());
        }

        int i = 0;
        for (Skill skill : Skill.VALUES) {
            if (skill.ordinal() >= Skill.SOCIAL.ordinal()) {
                continue;
            }
            assertEquals(3 + i, restored.level(skill), skill.id());
            assertEquals(1.5f + i, restored.xp(skill), 1e-6, skill.id());
            i++;
        }
        assertEquals(2, restored.rank(Talents.of(Skill.MINING).get(0).key()));
        assertEquals(7_000L, restored.cooldownRemaining(Skill.MINING, 0L));
        assertEquals(12_345L, restored.streakTicks());

        // Saving it again writes no Social entry until Social earns something.
        JsonObject saved = PlayerSkills.CODEC.encodeStart(JsonOps.INSTANCE, restored)
                .getOrThrow(false, message -> { throw new AssertionError("encode failed: " + message); }).getAsJsonObject();
        assertFalse(saved.getAsJsonObject("skills").has("social"));
    }

    @Test
    void theSyncPacketCarriesSocialAndEveryOtherSkill() {
        PlayerSkills original = PlayerSkills.CODEC.parse(JsonOps.INSTANCE, oldSave())
                .getOrThrow(false, message -> { throw new AssertionError("decode failed: " + message); });
        original.setLevel(Skill.SOCIAL, 42);

        net.minecraft.network.FriendlyByteBuf buf =
                new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        PlayerSkills.STREAM_CODEC.encode(buf, original);
        PlayerSkills decoded = PlayerSkills.STREAM_CODEC.decode(buf);

        assertEquals(0, buf.readableBytes(), "the packet had bytes left over");
        for (Skill skill : Skill.VALUES) {
            assertEquals(original.level(skill), decoded.level(skill), skill.id());
            assertEquals(original.xp(skill), decoded.xp(skill), 1e-6, skill.id());
        }
        assertEquals(42, decoded.level(Skill.SOCIAL));
    }
}
