package dev.amman.proficiency;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.SurvivalStreak;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The streak's arithmetic, at the shipped defaults: a stack an hour, fifty of them, 1% each. */
class SurvivalStreakTest {

    private static final long HOUR = 72_000L;
    private static final long WINDOW = 6_000L;
    private static final int MAX = 50;

    /** One active hour, a second at a time, with XP earned every minute. */
    private static void liveAnHour(PlayerSkills skills, long start) {
        for (long t = start + 20; t <= start + HOUR; t += 20) {
            if (t % 1200 == 0) {
                skills.noteActive(t);
            }
            skills.tickStreak(t, 20, WINDOW, HOUR, MAX);
        }
    }

    @Test
    void anActiveHourIsOneStack() {
        PlayerSkills skills = new PlayerSkills();
        skills.noteActive(0);
        liveAnHour(skills, 0);
        assertEquals(1, skills.streakStacks(HOUR, MAX));
        assertEquals(1.01, SurvivalStreak.multiplier(skills), 1e-9);
    }

    @Test
    void noXpNoStreak() {
        PlayerSkills skills = new PlayerSkills();
        assertFalse(skills.tickStreak(100, 20, WINDOW, HOUR, MAX), "never active");
        skills.noteActive(0);
        assertFalse(skills.tickStreak(WINDOW + 20, 20, WINDOW, HOUR, MAX), "AFK past the window");
        assertEquals(0, skills.streakTicks());
    }

    @Test
    void itCapsAtFiftyStacksAndPlusFiftyPercent() {
        PlayerSkills skills = new PlayerSkills();
        skills.setStreakTicks(HOUR * 49 + HOUR - 20);
        skills.noteActive(0);
        assertTrue(skills.tickStreak(20, 20, WINDOW, HOUR, MAX), "crossing into the 50th stack");
        assertFalse(skills.tickStreak(40, 20, WINDOW, HOUR, MAX), "nothing past the cap");
        assertEquals(HOUR * MAX, skills.streakTicks());
        assertEquals(1.50, SurvivalStreak.multiplier(skills), 1e-9);
    }

    @Test
    void onlyAStackChangeMarksDirty() {
        PlayerSkills skills = new PlayerSkills();
        skills.noteActive(0);
        skills.clearDirty();
        assertFalse(skills.tickStreak(20, 20, WINDOW, HOUR, MAX));
        assertFalse(skills.isDirty(), "a second of streak is not worth a packet");
    }

    @Test
    void deathTakesTheWholeStreak() {
        PlayerSkills skills = new PlayerSkills();
        skills.setStreakTicks(HOUR * 31 + 500);
        assertEquals(31, skills.loseStreak(HOUR, MAX));
        assertEquals(0, skills.streakTicks());
        assertEquals(0, skills.loseStreak(HOUR, MAX));
    }

    @Test
    void aZeroCapSwitchesItOff() {
        PlayerSkills skills = new PlayerSkills();
        skills.setStreakTicks(HOUR * 10);
        skills.noteActive(0);
        assertEquals(0, skills.streakStacks(HOUR, 0));
        assertFalse(skills.tickStreak(20, 20, WINDOW, HOUR, 0));
    }

    @Test
    void theStreakSurvivesSaveCopyAndRespawnCarry() {
        PlayerSkills original = new PlayerSkills();
        original.setStreakTicks(HOUR * 7 + 123);
        JsonElement encoded = PlayerSkills.CODEC.encodeStart(JsonOps.INSTANCE, original)
                .getOrThrow(message -> new AssertionError("encode failed: " + message));
        PlayerSkills restored = PlayerSkills.CODEC.parse(JsonOps.INSTANCE, encoded)
                .getOrThrow(message -> new AssertionError("decode failed: " + message));
        assertEquals(HOUR * 7 + 123, restored.streakTicks());

        PlayerSkills fresh = new PlayerSkills();
        fresh.copyFrom(restored);
        assertEquals(HOUR * 7 + 123, fresh.streakTicks());
    }

    /** The sync packet gained a field. A one-sided change here disconnects every client. */
    @Test
    void theStreakRidesTheSyncPacket() {
        PlayerSkills original = new PlayerSkills();
        original.setStreakTicks(HOUR * 12 + 7);
        original.setLevel(dev.amman.proficiency.skill.Skill.MINING, 33);
        net.minecraft.network.FriendlyByteBuf buf =
                new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        PlayerSkills.STREAM_CODEC.encode(buf, original);
        PlayerSkills decoded = PlayerSkills.STREAM_CODEC.decode(buf);
        assertEquals(0, buf.readableBytes(), "the reader must consume exactly what the writer wrote");
        assertEquals(HOUR * 12 + 7, decoded.streakTicks());
        assertEquals(33, decoded.level(dev.amman.proficiency.skill.Skill.MINING));
    }

    @Test
    void anOldSaveWithNoStreakLoadsAtZero() {
        JsonElement old = com.google.gson.JsonParser.parseString("{\"skills\":{}}");
        PlayerSkills restored = PlayerSkills.CODEC.parse(JsonOps.INSTANCE, old)
                .getOrThrow(message -> new AssertionError("decode failed: " + message));
        assertEquals(0, restored.streakTicks());
    }

    @Test
    void resetClearsTheStreak() {
        PlayerSkills skills = new PlayerSkills();
        skills.setStreakTicks(HOUR * 3);
        skills.reset();
        assertEquals(0, skills.streakTicks());
    }
}
