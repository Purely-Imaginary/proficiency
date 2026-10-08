package dev.amman.proficiency.gametest;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.config.ProficiencyConfig;
import dev.amman.proficiency.net.DeathRecapPayload;
import dev.amman.proficiency.net.SyncSkillsPayload;
import dev.amman.proficiency.skill.Mastery;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.RestedMath;
import dev.amman.proficiency.skill.RestedService;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillMath;
import dev.amman.proficiency.skill.SkillService;
import dev.amman.proficiency.skill.TeachingService;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Rested XP and teaching in a real world: the spend on a grant (doubling, additive with the
 * survival bonus), the death wipe, teacher credit and the packets. The arithmetic is in
 * RestedTest (core); these check the wiring.
 */
@GameTestHolder(Proficiency.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RestedGameTests {

    private RestedGameTests() {
    }

    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.make(helper);
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    /**
     * Moves these players to a spot of their own, far from every other test's players. Mock players
     * never leave and a teacher is anyone near who used a skill in the last 5 seconds, so a
     * neighbour left over from another test would teach (or fail to) and spoil the numbers.
     */
    private static void isolate(int slot, ServerPlayer... players) {
        for (ServerPlayer player : players) {
            player.setPos(1_000_000.0 + slot * 5_000.0, 80.0, 1_000_000.0);
        }
    }

    /** A player at {@code level} in mining, optionally with a full pool and a survival streak. */
    private static PlayerSkills setup(ServerPlayer player, int level, boolean pool, int stacks) {
        PlayerSkills skills = ProficiencyAttachments.of(player);
        skills.setLevel(Skill.MINING, level);
        if (pool) {
            skills.setRested(Skill.MINING, skills.restedCap(Skill.MINING));
        }
        if (stacks > 0) {
            skills.setStreakTicks(ProficiencyConfig.streakStepTicks() * stacks);
        }
        return skills;
    }

    @GameTest(template = "empty")
    public static void aGrantFromAPoolPaysDoubleAndDrainsIt(GameTestHelper helper) {
        ServerPlayer rested = player(helper);
        ServerPlayer plain = player(helper);
        PlayerSkills restedSkills = setup(rested, 40, true, 0);
        setup(plain, 40, false, 0);
        float before = restedSkills.rested(Skill.MINING);

        float withPool = SkillService.grant(rested, Skill.MINING, 10.0, "block.minecraft.stone");
        float without = SkillService.grant(plain, Skill.MINING, 10.0, "block.minecraft.stone");
        helper.assertTrue(without > 0f, "setup: the plain grant paid nothing");
        helper.assertTrue(Math.abs(withPool / without - 2f) < 0.02f,
                "a grant from a pool should be double: " + withPool + " against " + without);
        helper.assertTrue(restedSkills.rested(Skill.MINING) < before, "the pool did not drain");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void restedAddsToTheSurvivalBonusInsteadOfMultiplyingIt(GameTestHelper helper) {
        ServerPlayer rested = player(helper);
        ServerPlayer plain = player(helper);
        setup(rested, 40, true, 20);
        setup(plain, 40, false, 20);

        float withPool = SkillService.grant(rested, Skill.MINING, 10.0, "block.minecraft.stone");
        float without = SkillService.grant(plain, Skill.MINING, 10.0, "block.minecraft.stone");
        // Streak x1.2: plain x (1.2 + 1) over plain x 1.2 is 1.83, not the 2.0 a multiplication gives.
        double expected = (1.0 + 0.01 * 20 + 1.0) / (1.0 + 0.01 * 20);
        float ratio = withPool / without;
        helper.assertTrue(Math.abs(ratio - expected) < 0.02 && ratio < 1.95f,
                "rested should add to the streak, ratio " + ratio + " expected " + expected);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void anOperatorsAddXpDoesNotSpendThePool(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        PlayerSkills skills = setup(player, 40, true, 0);
        float before = skills.rested(Skill.MINING);
        SkillService.grant(player, Skill.MINING, 10.0, "proficiency.xplog.source.command");
        helper.assertTrue(skills.rested(Skill.MINING) == before, "an addxp command spent rested XP");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void atLevelOneHundredRestedFeedsTheStarBar(GameTestHelper helper) {
        ServerPlayer rested = player(helper);
        ServerPlayer plain = player(helper);
        PlayerSkills restedSkills = setup(rested, SkillMath.MAX_LEVEL, true, 0);
        PlayerSkills plainSkills = setup(plain, SkillMath.MAX_LEVEL, false, 0);
        helper.assertTrue(restedSkills.rested(Skill.MINING) > 0f && Mastery.maxStars() > 0,
                "setup: a level 100 skill should hold a pool while stars are open");
        SkillService.grant(rested, Skill.MINING, 20.0, "block.minecraft.stone");
        SkillService.grant(plain, Skill.MINING, 20.0, "block.minecraft.stone");
        float ratio = restedSkills.overflow(Skill.MINING) / plainSkills.overflow(Skill.MINING);
        helper.assertTrue(Math.abs(ratio - 2f) < 0.05f, "the star bar should fill double, ratio " + ratio);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void anAfkPlayerRestsNothingAndAnActiveOneDoes(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        isolate(1, player);
        PlayerSkills skills = setup(player, 30, false, 0);
        skills.setLevel(Skill.SWORDS, 30);
        RestedService.tick(player, 20);
        helper.assertTrue(skills.rested(Skill.MINING) == 0f && skills.rested(Skill.SWORDS) == 0f,
                "a player who never earned XP this session is AFK and must not rest");

        skills.noteActive(player.level().getGameTime());
        RestedService.tick(player, 20);
        helper.assertTrue(skills.rested(Skill.MINING) > 0f, "an active player's unused skill should rest");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aSkillInUseDoesNotRest(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        isolate(2, player);
        PlayerSkills skills = setup(player, 30, false, 0);
        skills.setLevel(Skill.SWORDS, 30);
        SkillService.grant(player, Skill.MINING, 1.0, "block.minecraft.stone");
        RestedService.tick(player, 20);
        helper.assertTrue(skills.rested(Skill.MINING) == 0f, "the skill just used rested");
        helper.assertTrue(skills.rested(Skill.SWORDS) > 0f, "an unused skill should rest while another is in use");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aTeacherFillsAStudentThreeTimesAsFastAndIsRemembered(GameTestHelper helper) {
        ServerPlayer teacher = player(helper);
        ServerPlayer student = player(helper);
        isolate(3, teacher, student);
        long now = teacher.level().getGameTime();
        PlayerSkills teaching = ProficiencyAttachments.of(teacher);
        teaching.setLevel(Skill.MINING, 100);
        teaching.noteActive(now);
        teaching.noteUsed(Skill.MINING, now, RestedMath.today());
        PlayerSkills learning = ProficiencyAttachments.of(student);
        learning.setLevel(Skill.MINING, 20);
        learning.setLevel(Skill.SWORDS, 20);
        learning.noteActive(now);

        RestedService.tick(student, 20);
        float taught = learning.rested(Skill.MINING);
        float idle = learning.rested(Skill.SWORDS);
        helper.assertTrue(idle > 0f, "setup: the idle rate should be positive");
        helper.assertTrue(Math.abs(taught / idle - 3f) < 0.05f, "teaching should fill 3x, ratio " + taught / idle);
        List<dev.amman.proficiency.skill.RestedPool.Part> parts = learning.restedTaught(Skill.MINING);
        helper.assertTrue(parts.size() == 1 && parts.get(0).teacher().equals(teacher.getUUID()),
                "the part must remember its teacher: " + parts);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void anAfkTeacherTeachesNothing(GameTestHelper helper) {
        ServerPlayer teacher = player(helper);
        ServerPlayer student = player(helper);
        isolate(4, teacher, student);
        long now = teacher.level().getGameTime();
        PlayerSkills teaching = ProficiencyAttachments.of(teacher);
        teaching.setLevel(Skill.MINING, 100);
        // Used the skill a moment ago but has since gone AFK: the activity window is minutes, so
        // the stamp that proves "active" is too old.
        teaching.noteActive(now - ProficiencyConfig.streakActiveWindowTicks() - 100);
        teaching.noteUsed(Skill.MINING, now, RestedMath.today());
        PlayerSkills learning = ProficiencyAttachments.of(student);
        learning.setLevel(Skill.MINING, 20);
        learning.noteActive(now);

        RestedService.tick(student, 20);
        helper.assertTrue(learning.restedTaught(Skill.MINING).isEmpty(), "an AFK teacher filled a pool");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void spendingATeachersPartPaysTheTeacherSocialXp(GameTestHelper helper) {
        ServerPlayer teacher = player(helper);
        ServerPlayer student = player(helper);
        PlayerSkills learning = setup(student, 40, false, 0);
        learning.fillRestedTaught(Skill.MINING, teacher.getUUID(), 200f);
        PlayerSkills teaching = ProficiencyAttachments.of(teacher);
        teaching.setLevel(Skill.SOCIAL, 30);
        float before = teaching.xp(Skill.SOCIAL);

        SkillService.grant(student, Skill.MINING, 10.0, "block.minecraft.stone");
        double owed = TeachingService.ledger(teacher.server).owedTo(teacher.getUUID());
        helper.assertTrue(owed > 0, "the spend should have left credit for the teacher");

        TeachingService.tick(teacher);
        helper.assertTrue(teaching.xp(Skill.SOCIAL) > before, "the teacher was not paid Social XP");
        helper.assertTrue(TeachingService.ledger(teacher.server).owedTo(teacher.getUUID()) == 0.0,
                "the credit should be gone once paid");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void creditWaitingForAnOfflineTeacherIsPaidAtLogin(GameTestHelper helper) {
        ServerPlayer teacher = player(helper);
        ServerPlayer student = player(helper);
        PlayerSkills teaching = ProficiencyAttachments.of(teacher);
        teaching.setLevel(Skill.SOCIAL, 30);
        float before = teaching.xp(Skill.SOCIAL);
        // Credit that piled up while the teacher was away, as the ledger holds it.
        TeachingService.ledger(teacher.server).add(teacher.getUUID(), student.getUUID(), 5.0);
        RestedService.onLogin(teacher);
        helper.assertTrue(teaching.xp(Skill.SOCIAL) > before, "login did not pay the waiting credit");
        helper.assertTrue(TeachingService.ledger(teacher.server).owedTo(teacher.getUUID()) == 0.0,
                "the credit is still waiting after login");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aDeathEmptiesEveryPoolAndTheTeachersPendingCredit(GameTestHelper helper) {
        ServerPlayer teacher = player(helper);
        ServerPlayer student = player(helper);
        PlayerSkills skills = setup(student, 40, true, 0);
        skills.setLevel(Skill.SWORDS, 40);
        skills.fillRestedTaught(Skill.SWORDS, teacher.getUUID(), 100f);

        ServerPlayer respawned = player(helper);
        // The respawned body has the student's identity in a real game; here its own UUID stands in.
        TeachingService.ledger(teacher.server).add(teacher.getUUID(), respawned.getUUID(), 7.0);
        MinecraftForge.EVENT_BUS.post(new PlayerEvent.Clone(respawned, student, true));

        PlayerSkills fresh = ProficiencyAttachments.of(respawned);
        helper.assertTrue(fresh.rested(Skill.MINING) == 0f && fresh.rested(Skill.SWORDS) == 0f,
                "a death must empty every pool");
        helper.assertTrue(fresh.restedTaught(Skill.SWORDS).isEmpty(), "a death must empty the teacher parts");
        helper.assertTrue(TeachingService.ledger(teacher.server).owedTo(teacher.getUUID()) == 0.0,
                "the teacher's pending credit for this student must go with it");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void theSyncAndRecapPacketsCarryRestedXp(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        PlayerSkills skills = setup(player, 40, true, 0);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        SyncSkillsPayload.SKILLS_CODEC.encode(buf, skills);
        PlayerSkills read = SyncSkillsPayload.SKILLS_CODEC.decode(buf);
        helper.assertTrue(Math.abs(read.rested(Skill.MINING) - skills.rested(Skill.MINING)) < 1e-3f,
                "the sync packet lost the rested pool");
        helper.assertTrue(buf.readableBytes() == 0, "the sync packet left bytes unread");

        FriendlyByteBuf recap = new FriendlyByteBuf(Unpooled.buffer());
        DeathRecapPayload payload = DeathRecapPayload.of(Map.of(), new float[Skill.VALUES.length], skills, 0, 0,
                Map.of(Skill.MINING, 123f, Skill.SWORDS, 7f));
        DeathRecapPayload.STREAM_CODEC.encode(recap, payload);
        DeathRecapPayload back = DeathRecapPayload.STREAM_CODEC.decode(recap);
        helper.assertTrue(back.restedXp() == 130 && back.restedSkills() == 2, "the recap lost the rested XP: " + back);
        helper.assertTrue(!back.isEmpty(), "a death that only lost rested XP still has something to show");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void restedXpIsItsOwnTelemetryKind(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        setup(player, 40, true, 0);
        dev.amman.proficiency.telemetry.TelemetryHub.data().drain(0L, "");
        SkillService.grant(player, Skill.MINING, 10.0, "block.minecraft.stone");
        List<String> lines = dev.amman.proficiency.telemetry.TelemetryHub.data().drain(1L, "");
        boolean rested = lines.stream().anyMatch(l -> l.contains("\"type\":\"xp\"") && l.contains("\"kind\":\"rested\"")
                && l.contains("\"skill\":\"mining\""));
        helper.assertTrue(rested, "no rested kind in the telemetry: " + lines);
        helper.succeed();
    }
}
