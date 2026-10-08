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
 * Idea 37 added Courage as the 31st skill. A save written before it has no "courage" entry. It
 * must load with Courage at level 0 and every other skill exactly as saved, and the sync packet
 * must carry all of them.
 */
class CourageSaveCompatTest {

    /** A playerdata attachment as the 30-skill mod wrote it: every skill up to Nightwalker. */
    private static JsonObject oldSave() {
        JsonObject skills = new JsonObject();
        for (Skill skill : Skill.VALUES) {
            if (skill.ordinal() >= Skill.COURAGE.ordinal()) {
                continue;
            }
            JsonObject state = new JsonObject();
            state.addProperty("level", 7 + skill.ordinal());
            state.addProperty("xp", 0.5f + skill.ordinal());
            skills.add(skill.id(), state);
        }
        JsonObject talents = new JsonObject();
        talents.addProperty(Talents.of(Skill.NIGHTWALKER).get(0).key(), 4);
        talents.addProperty(Talents.of(Skill.ENDURANCE).get(0).key(), 5);
        JsonObject root = new JsonObject();
        root.add("skills", skills);
        root.add("talents", talents);
        root.addProperty("streak", 4242L);
        return root;
    }

    @Test
    void courageIsLastAndAFighterSoNoOldOrdinalMoved() {
        assertSame(Skill.COURAGE, Skill.VALUES[30]);
        assertSame(Skill.NIGHTWALKER, Skill.VALUES[29]);
        assertSame(Skill.SOCIAL, Skill.VALUES[28]);
        assertSame(Skill.COURAGE, Skill.byId("courage"));
        assertSame(SkillCategory.COMBAT, Skill.COURAGE.category());
    }

    @Test
    void anOldSaveLoadsWithCourageAtZeroAndNothingElseChanged() {
        PlayerSkills restored = PlayerSkills.CODEC.parse(JsonOps.INSTANCE, oldSave())
                .getOrThrow(message -> new AssertionError("decode failed: " + message));

        assertEquals(0, restored.level(Skill.COURAGE));
        assertEquals(0f, restored.xp(Skill.COURAGE));
        assertEquals(0, restored.pointsSpent(Skill.COURAGE));
        assertEquals(0L, restored.cooldownRemaining(Skill.COURAGE, 0L));
        for (Talent talent : Talents.of(Skill.COURAGE)) {
            assertEquals(0, restored.rank(talent), talent.key());
        }
        for (Skill skill : Skill.VALUES) {
            if (skill.ordinal() >= Skill.COURAGE.ordinal()) {
                continue;
            }
            assertEquals(7 + skill.ordinal(), restored.level(skill), skill.id());
            assertEquals(0.5f + skill.ordinal(), restored.xp(skill), 1e-6, skill.id());
        }
        assertEquals(4, restored.rank(Talents.of(Skill.NIGHTWALKER).get(0).key()));
        assertEquals(5, restored.rank(Talents.of(Skill.ENDURANCE).get(0).key()));
        assertEquals(4242L, restored.streakTicks());

        JsonObject saved = PlayerSkills.CODEC.encodeStart(JsonOps.INSTANCE, restored)
                .getOrThrow(message -> new AssertionError("encode failed: " + message)).getAsJsonObject();
        assertFalse(saved.getAsJsonObject("skills").has("courage"),
                "an untouched Courage should not be written");
    }

    @Test
    void theSyncPacketCarriesCourage() {
        PlayerSkills original = PlayerSkills.CODEC.parse(JsonOps.INSTANCE, oldSave())
                .getOrThrow(message -> new AssertionError("decode failed: " + message));
        original.setLevel(Skill.COURAGE, 64);
        original.fillTree(Skill.COURAGE);

        net.minecraft.network.FriendlyByteBuf buf =
                new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        dev.amman.proficiency.net.SyncSkillsPayload.SKILLS_CODEC.encode(buf, original);
        PlayerSkills decoded = dev.amman.proficiency.net.SyncSkillsPayload.SKILLS_CODEC.decode(buf);

        assertEquals(0, buf.readableBytes(), "the packet had bytes left over");
        for (Skill skill : Skill.VALUES) {
            assertEquals(original.level(skill), decoded.level(skill), skill.id());
            assertEquals(original.xp(skill), decoded.xp(skill), 1e-6, skill.id());
        }
        assertEquals(64, decoded.level(Skill.COURAGE));
        for (Talent talent : Talents.of(Skill.COURAGE)) {
            assertEquals(original.rank(talent), decoded.rank(talent), talent.key());
        }
    }
}
