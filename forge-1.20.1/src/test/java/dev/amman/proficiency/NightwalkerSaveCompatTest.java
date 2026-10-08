package dev.amman.proficiency;

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
 * Idea 36 added Nightwalker as the 30th skill. A save written before it has no "nightwalker"
 * entry. It must load with Nightwalker at level 0 and every other skill exactly as saved, and the
 * sync packet must carry all thirty.
 */
class NightwalkerSaveCompatTest {

    /** A playerdata attachment as the 29-skill mod wrote it: every skill up to Social. */
    private static JsonObject oldSave() {
        JsonObject skills = new JsonObject();
        for (Skill skill : Skill.VALUES) {
            if (skill.ordinal() >= Skill.NIGHTWALKER.ordinal()) {
                continue;
            }
            JsonObject state = new JsonObject();
            state.addProperty("level", 5 + skill.ordinal());
            state.addProperty("xp", 2.5f + skill.ordinal());
            skills.add(skill.id(), state);
        }
        JsonObject talents = new JsonObject();
        talents.addProperty(Talents.of(Skill.SOCIAL).get(0).key(), 3);
        JsonObject root = new JsonObject();
        root.add("skills", skills);
        root.add("talents", talents);
        root.addProperty("streak", 777L);
        return root;
    }

    @Test
    void nightwalkerIsLastSoNoOldOrdinalMoved() {
        // Nightwalker went in 30th. Later skills (Courage) come after it.
        assertSame(Skill.NIGHTWALKER, Skill.VALUES[29]);
        assertSame(Skill.SOCIAL, Skill.VALUES[28]);
        assertSame(Skill.NIGHTWALKER, Skill.byId("nightwalker"));
    }

    @Test
    void anOldSaveLoadsWithNightwalkerAtZeroAndNothingElseChanged() {
        PlayerSkills restored = PlayerSkills.CODEC.parse(JsonOps.INSTANCE, oldSave())
                .getOrThrow(false, message -> { throw new AssertionError("decode failed: " + message); });

        assertEquals(0, restored.level(Skill.NIGHTWALKER));
        assertEquals(0f, restored.xp(Skill.NIGHTWALKER));
        assertEquals(0, restored.pointsSpent(Skill.NIGHTWALKER));
        assertEquals(0L, restored.cooldownRemaining(Skill.NIGHTWALKER, 0L));
        for (Talent talent : Talents.of(Skill.NIGHTWALKER)) {
            assertEquals(0, restored.rank(talent), talent.key());
        }
        for (Skill skill : Skill.VALUES) {
            if (skill.ordinal() >= Skill.NIGHTWALKER.ordinal()) {
                assertEquals(0, restored.level(skill), skill.id());
                continue;
            }
            assertEquals(5 + skill.ordinal(), restored.level(skill), skill.id());
            assertEquals(2.5f + skill.ordinal(), restored.xp(skill), 1e-6, skill.id());
        }
        assertEquals(3, restored.rank(Talents.of(Skill.SOCIAL).get(0).key()));
        assertEquals(777L, restored.streakTicks());

        JsonObject saved = PlayerSkills.CODEC.encodeStart(JsonOps.INSTANCE, restored)
                .getOrThrow(false, message -> { throw new AssertionError("encode failed: " + message); }).getAsJsonObject();
        assertFalse(saved.getAsJsonObject("skills").has("nightwalker"),
                "an untouched Nightwalker should not be written");
    }

    @Test
    void theSyncPacketCarriesNightwalker() {
        PlayerSkills original = PlayerSkills.CODEC.parse(JsonOps.INSTANCE, oldSave())
                .getOrThrow(false, message -> { throw new AssertionError("decode failed: " + message); });
        original.setLevel(Skill.NIGHTWALKER, 37);
        original.fillTree(Skill.NIGHTWALKER);

        net.minecraft.network.FriendlyByteBuf buf =
                new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        dev.amman.proficiency.net.SyncSkillsPayload.SKILLS_CODEC.encode(buf, original);
        PlayerSkills decoded = dev.amman.proficiency.net.SyncSkillsPayload.SKILLS_CODEC.decode(buf);

        assertEquals(0, buf.readableBytes(), "the packet had bytes left over");
        for (Skill skill : Skill.VALUES) {
            assertEquals(original.level(skill), decoded.level(skill), skill.id());
        }
        assertEquals(37, decoded.level(Skill.NIGHTWALKER));
        for (Talent talent : Talents.of(Skill.NIGHTWALKER)) {
            assertEquals(original.rank(talent), decoded.rank(talent), talent.key());
        }
    }
}
