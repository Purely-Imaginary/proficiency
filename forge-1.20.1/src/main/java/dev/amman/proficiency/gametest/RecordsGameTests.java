package dev.amman.proficiency.gametest;

import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.config.ProficiencyConfig;
import dev.amman.proficiency.skill.PlayerRecords;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.RecordMetric;
import dev.amman.proficiency.skill.RecordTracker;
import dev.amman.proficiency.skill.RecordsService;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillService;
import dev.amman.proficiency.Proficiency;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Personal records in a real world: a record breaks, the toast is rationed, and the records outlive death and relog. */
@GameTestHolder(Proficiency.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RecordsGameTests {

    private RecordsGameTests() {
    }

    private static ServerPlayer player(GameTestHelper helper, int slot) {
        ServerPlayer player = MockPlayers.make(helper);
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(1_000_000.0 + slot * 5_000.0, 80.0, 1_000_000.0);
        return player;
    }

    @GameTest(template = "empty")
    public static void aRealGrantSetsTheRecordsAndABiggerOneRaisesThem(GameTestHelper helper) {
        ServerPlayer player = player(helper, 31);
        PlayerRecords records = ProficiencyAttachments.of(player).records();
        helper.assertTrue(records.isEmpty(Skill.MINING), "a new player has no records");
        float first = SkillService.grant(player, Skill.MINING, 4.0, "block.minecraft.stone");
        PlayerRecords.Entry one = records.get(Skill.MINING);
        helper.assertTrue(first > 0f && one.grant() >= first - 0.001f, "the grant is a record (a first-time bonus may be bigger): " + one);
        helper.assertTrue(one.rate() >= first && one.chain() >= 1, "the rate and the chain are set: " + one);
        float bigger = SkillService.grant(player, Skill.MINING, 40.0, "block.minecraft.diamond_ore");
        helper.assertTrue(records.get(Skill.MINING).grant() >= bigger, "a bigger grant raised it");
        helper.assertTrue(records.get(Skill.MINING).rate() >= first + bigger - 0.01f,
                "the rate is the XP of the last minute: " + records.get(Skill.MINING));
        float small = SkillService.grant(player, Skill.MINING, 1.0, "block.minecraft.stone");
        helper.assertTrue(small < bigger && records.get(Skill.MINING).grant() >= bigger,
                "a smaller grant lowers nothing");
        helper.assertTrue(records.isEmpty(Skill.SWORDS), "another skill's records are untouched");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void anOperatorGrantIsNotPlayAndNeverCounts(GameTestHelper helper) {
        ServerPlayer player = player(helper, 32);
        SkillService.grant(player, Skill.MINING, 500.0, "proficiency.xplog.source.command");
        helper.assertTrue(ProficiencyAttachments.of(player).records().isEmpty(Skill.MINING),
                "/proficiency addxp is not play: " + ProficiencyAttachments.of(player).records().get(Skill.MINING));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void theServerSwitchStopsTracking(GameTestHelper helper) {
        ServerPlayer player = player(helper, 33);
        boolean before = ProficiencyConfig.RECORDS_ENABLED.get();
        ProficiencyConfig.RECORDS_ENABLED.set(false);
        try {
            SkillService.grant(player, Skill.MINING, 10.0, "block.minecraft.stone");
            helper.assertTrue(ProficiencyAttachments.of(player).records().isEmpty(Skill.MINING),
                    "records.enabled=false tracks nothing");
        } finally {
            ProficiencyConfig.RECORDS_ENABLED.set(before);
        }
        SkillService.grant(player, Skill.MINING, 10.0, "block.minecraft.stone");
        helper.assertFalse(ProficiencyAttachments.of(player).records().isEmpty(Skill.MINING),
                "switched back on, it tracks again");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aBrokenRecordToastsOnceThenWaitsForTheCooldownAndNeverInTheFirstMinute(GameTestHelper helper) {
        ServerPlayer player = player(helper, 34);
        PlayerSkills skills = ProficiencyAttachments.of(player);
        skills.records().set(Skill.MINING, new PlayerRecords.Entry(100_000f, 1000, 50f, 0));
        long t = 0;
        for (; t < 1300; t += 20) {
            RecordsService.onGrant(player, Skill.MINING, "block.minecraft.stone", 5f, true, t);
            helper.assertTrue(RecordsService.lastOutcome(player.getUUID()).toast() == null,
                    "no toast in the first minute, tick " + t);
        }
        RecordsService.onGrant(player, Skill.MINING, "block.minecraft.stone", 80f, true, t);
        RecordTracker.Outcome broke = RecordsService.lastOutcome(player.getUUID());
        helper.assertTrue(broke.toast() == RecordMetric.GRANT && Math.abs(broke.toastValue() - 80f) < 0.01f,
                "an 80 XP grant beats 50 and toasts: " + broke);
        t += 100;
        RecordsService.onGrant(player, Skill.MINING, "block.minecraft.stone", 120f, true, t);
        helper.assertTrue(RecordsService.lastOutcome(player.getUUID()).toast() == null,
                "30 s have not passed: no second toast");
        helper.assertTrue(skills.records().get(Skill.MINING).grant() == 120f, "but the record is stored");
        t += 620;
        RecordsService.onGrant(player, Skill.MINING, "block.minecraft.stone", 5f, true, t);
        helper.assertTrue(RecordsService.lastOutcome(player.getUUID()).toast() == RecordMetric.GRANT,
                "after the cooldown the held-back record is announced");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void recordsSurviveDeathRespawnAndARelog(GameTestHelper helper) {
        ServerPlayer player = player(helper, 35);
        PlayerSkills skills = ProficiencyAttachments.of(player);
        skills.setLevel(Skill.MINING, 30);
        skills.addXp(Skill.MINING, 500f);
        skills.records().set(Skill.MINING, new PlayerRecords.Entry(312f, 41, 96.5f, 0));
        skills.records().set(Skill.BEASTSLAYING, new PlayerRecords.Entry(0f, 0, 0f, 7));

        // Death wipes the bar and never a record.
        skills.applyDeathPenalty();
        helper.assertTrue(skills.records().get(Skill.MINING).rate() == 312f, "the death left the rate alone");

        // The respawn copies the old body's skills into the new one.
        PlayerSkills respawned = new PlayerSkills();
        respawned.copyFrom(skills);
        helper.assertTrue(respawned.records().get(Skill.BEASTSLAYING).extra() == 7, "the respawn kept the kills");

        // A relog is the save codec: what the attachment writes is what it reads back.
        Tag saved = PlayerSkills.CODEC.encodeStart(NbtOps.INSTANCE, skills).result().orElseThrow();
        PlayerSkills loaded = PlayerSkills.CODEC.parse(NbtOps.INSTANCE, saved).result().orElseThrow();
        PlayerRecords.Entry mining = loaded.records().get(Skill.MINING);
        helper.assertTrue(mining.rate() == 312f && mining.chain() == 41 && mining.grant() == 96.5f,
                "the records came back from the save: " + mining);
        helper.assertTrue(loaded.records().get(Skill.BEASTSLAYING).extra() == 7, "and the skill's own best");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void anOperatorResetClearsOneSkillOrAll(GameTestHelper helper) {
        ServerPlayer player = player(helper, 36);
        PlayerRecords records = ProficiencyAttachments.of(player).records();
        records.set(Skill.MINING, new PlayerRecords.Entry(10f, 2, 3f, 0));
        records.set(Skill.FISHING, new PlayerRecords.Entry(10f, 2, 3f, 4));
        records.reset(Skill.MINING);
        helper.assertTrue(records.isEmpty(Skill.MINING) && !records.isEmpty(Skill.FISHING), "one skill cleared");
        records.reset();
        helper.assertTrue(records.isEmpty(), "all cleared");
        helper.succeed();
    }
}
