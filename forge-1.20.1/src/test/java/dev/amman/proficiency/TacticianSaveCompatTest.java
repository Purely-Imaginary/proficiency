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
 * Idea 40 added Tactician as the 34th skill. A save written before it has no "tactician" entry. It
 * must load with Tactician at level 0 and every other skill exactly as saved, and the sync packet
 * must carry all of them.
 */
class TacticianSaveCompatTest {

    /** A playerdata attachment as the 33-skill mod wrote it: every skill up to Charger. */
    private static JsonObject oldSave() {
        JsonObject skills = new JsonObject();
        for (Skill skill : Skill.VALUES) {
            if (skill.ordinal() >= Skill.TACTICIAN.ordinal()) {
                continue;
            }
            JsonObject state = new JsonObject();
            state.addProperty("level", 5 + skill.ordinal());
            state.addProperty("xp", 0.25f + skill.ordinal());
            skills.add(skill.id(), state);
        }
        JsonObject talents = new JsonObject();
        talents.addProperty(Talents.of(Skill.GUARDIAN).get(0).key(), 3);
        talents.addProperty(Talents.of(Skill.COURAGE).get(0).key(), 5);
        talents.addProperty(Talents.of(Skill.CHARGER).get(0).key(), 4);
        JsonObject root = new JsonObject();
        root.add("skills", skills);
        root.add("talents", talents);
        root.addProperty("streak", 777L);
        return root;
    }

    @Test
    void tacticianComesAfterChargerSoNoOldOrdinalMoved() {
        assertSame(Skill.TACTICIAN, Skill.VALUES[33]);
        assertSame(Skill.CHARGER, Skill.VALUES[32]);
        assertSame(Skill.GUARDIAN, Skill.VALUES[31]);
        assertSame(Skill.TACTICIAN, Skill.byId("tactician"));
    }

    @Test
    void anOldSaveLoadsWithTacticianAtZeroAndNothingElseChanged() {
        PlayerSkills restored = PlayerSkills.CODEC.parse(JsonOps.INSTANCE, oldSave())
                .getOrThrow(false, message -> { throw new AssertionError("decode failed: " + message); });

        assertEquals(0, restored.level(Skill.TACTICIAN));
        assertEquals(0f, restored.xp(Skill.TACTICIAN));
        assertEquals(0, restored.pointsSpent(Skill.TACTICIAN));
        assertEquals(0L, restored.cooldownRemaining(Skill.TACTICIAN, 0L));
        for (Talent talent : Talents.of(Skill.TACTICIAN)) {
            assertEquals(0, restored.rank(talent), talent.key());
        }
        for (Skill skill : Skill.VALUES) {
            if (skill.ordinal() >= Skill.TACTICIAN.ordinal()) {
                continue;
            }
            assertEquals(5 + skill.ordinal(), restored.level(skill), skill.id());
            assertEquals(0.25f + skill.ordinal(), restored.xp(skill), 1e-6, skill.id());
        }
        assertEquals(3, restored.rank(Talents.of(Skill.GUARDIAN).get(0).key()));
        assertEquals(5, restored.rank(Talents.of(Skill.COURAGE).get(0).key()));
        assertEquals(4, restored.rank(Talents.of(Skill.CHARGER).get(0).key()));
        assertEquals(777L, restored.streakTicks());

        JsonObject saved = PlayerSkills.CODEC.encodeStart(JsonOps.INSTANCE, restored)
                .getOrThrow(false, message -> { throw new AssertionError("encode failed: " + message); }).getAsJsonObject();
        assertFalse(saved.getAsJsonObject("skills").has("tactician"),
                "an untouched Tactician should not be written");
    }

    @Test
    void theSyncPacketCarriesTactician() {
        PlayerSkills original = PlayerSkills.CODEC.parse(JsonOps.INSTANCE, oldSave())
                .getOrThrow(false, message -> { throw new AssertionError("decode failed: " + message); });
        original.setLevel(Skill.TACTICIAN, 71);
        original.fillTree(Skill.TACTICIAN);

        net.minecraft.network.FriendlyByteBuf buf =
                new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        dev.amman.proficiency.net.SyncSkillsPayload.SKILLS_CODEC.encode(buf, original);
        PlayerSkills decoded = dev.amman.proficiency.net.SyncSkillsPayload.SKILLS_CODEC.decode(buf);

        assertEquals(0, buf.readableBytes(), "the packet had bytes left over");
        for (Skill skill : Skill.VALUES) {
            assertEquals(original.level(skill), decoded.level(skill), skill.id());
            assertEquals(original.xp(skill), decoded.xp(skill), 1e-6, skill.id());
        }
        assertEquals(71, decoded.level(Skill.TACTICIAN));
        for (Talent talent : Talents.of(Skill.TACTICIAN)) {
            assertEquals(original.rank(talent), decoded.rank(talent), talent.key());
        }
    }
}
