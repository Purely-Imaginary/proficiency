package dev.amman.proficiency;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import dev.amman.proficiency.perk.Talent;
import dev.amman.proficiency.perk.Talents;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillCategory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Idea 38 added Guardian as the 32nd skill. A save written before it has no "guardian" entry. It
 * must load with Guardian at level 0 and every other skill exactly as saved, and the sync packet
 * must carry all of them.
 */
class GuardianSaveCompatTest {

    /** A playerdata attachment as the 31-skill mod wrote it: every skill up to Courage. */
    private static JsonObject oldSave() {
        JsonObject skills = new JsonObject();
        for (Skill skill : Skill.VALUES) {
            if (skill.ordinal() >= Skill.GUARDIAN.ordinal()) {
                continue;
            }
            JsonObject state = new JsonObject();
            state.addProperty("level", 7 + skill.ordinal());
            state.addProperty("xp", 0.5f + skill.ordinal());
            skills.add(skill.id(), state);
        }
        JsonObject talents = new JsonObject();
        talents.addProperty(Talents.of(Skill.COURAGE).get(0).key(), 4);
        talents.addProperty(Talents.of(Skill.ENDURANCE).get(0).key(), 5);
        JsonObject root = new JsonObject();
        root.add("skills", skills);
        root.add("talents", talents);
        root.addProperty("streak", 4242L);
        return root;
    }

    @Test
    void guardianIsLastAndAFighterSoNoOldOrdinalMoved() {
        assertSame(Skill.GUARDIAN, Skill.VALUES[31]);
        assertSame(Skill.COURAGE, Skill.VALUES[30]);
        assertSame(Skill.NIGHTWALKER, Skill.VALUES[29]);
        assertSame(Skill.GUARDIAN, Skill.byId("guardian"));
        assertSame(SkillCategory.COMBAT, Skill.GUARDIAN.category());
    }

    @Test
    void anOldSaveLoadsWithGuardianAtZeroAndNothingElseChanged() {
        PlayerSkills restored = PlayerSkills.CODEC.parse(JsonOps.INSTANCE, oldSave())
                .getOrThrow(false, message -> { throw new AssertionError("decode failed: " + message); });

        assertEquals(0, restored.level(Skill.GUARDIAN));
        assertEquals(0f, restored.xp(Skill.GUARDIAN));
        assertEquals(0, restored.pointsSpent(Skill.GUARDIAN));
        assertEquals(0L, restored.cooldownRemaining(Skill.GUARDIAN, 0L));
        for (Talent talent : Talents.of(Skill.GUARDIAN)) {
            assertEquals(0, restored.rank(talent), talent.key());
        }
        for (Skill skill : Skill.VALUES) {
            if (skill.ordinal() >= Skill.GUARDIAN.ordinal()) {
                continue;
            }
            assertEquals(7 + skill.ordinal(), restored.level(skill), skill.id());
            assertEquals(0.5f + skill.ordinal(), restored.xp(skill), 1e-6, skill.id());
        }
        assertEquals(4, restored.rank(Talents.of(Skill.COURAGE).get(0).key()));
        assertEquals(5, restored.rank(Talents.of(Skill.ENDURANCE).get(0).key()));
        assertEquals(4242L, restored.streakTicks());

        JsonObject saved = PlayerSkills.CODEC.encodeStart(JsonOps.INSTANCE, restored)
                .getOrThrow(false, message -> { throw new AssertionError("encode failed: " + message); }).getAsJsonObject();
        assertFalse(saved.getAsJsonObject("skills").has("guardian"),
                "an untouched Guardian should not be written");
    }

    @Test
    void theSyncPacketCarriesGuardian() {
        PlayerSkills original = PlayerSkills.CODEC.parse(JsonOps.INSTANCE, oldSave())
                .getOrThrow(false, message -> { throw new AssertionError("decode failed: " + message); });
        original.setLevel(Skill.GUARDIAN, 64);
        original.fillTree(Skill.GUARDIAN);

        net.minecraft.network.FriendlyByteBuf buf =
                new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        PlayerSkills.STREAM_CODEC.encode(buf, original);
        PlayerSkills decoded = PlayerSkills.STREAM_CODEC.decode(buf);

        assertEquals(0, buf.readableBytes(), "the packet had bytes left over");
        for (Skill skill : Skill.VALUES) {
            assertEquals(original.level(skill), decoded.level(skill), skill.id());
            assertEquals(original.xp(skill), decoded.xp(skill), 1e-6, skill.id());
        }
        assertEquals(64, decoded.level(Skill.GUARDIAN));
        for (Talent talent : Talents.of(Skill.GUARDIAN)) {
            assertEquals(original.rank(talent), decoded.rank(talent), talent.key());
        }
    }
}
