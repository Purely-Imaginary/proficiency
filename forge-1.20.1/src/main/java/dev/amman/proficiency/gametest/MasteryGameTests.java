package dev.amman.proficiency.gametest;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.net.LevelUpPayload;
import dev.amman.proficiency.net.SyncSkillsPayload;
import dev.amman.proficiency.skill.Mastery;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillMath;
import dev.amman.proficiency.skill.SkillService;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Mastery stars in a real world: the overflow bar, the star cap, the death rule and the packets. */
@GameTestHolder(Proficiency.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MasteryGameTests {

    private MasteryGameTests() {
    }

    private static PlayerSkills maxed(ServerPlayer player, Skill skill) {
        PlayerSkills skills = ProficiencyAttachments.of(player);
        skills.setLevel(skill, SkillMath.MAX_LEVEL);
        return skills;
    }

    @GameTest(template = "empty")
    public static void xpPastLevelOneHundredEarnsAStar(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.make(helper);
        PlayerSkills skills = maxed(player, Skill.MINING);

        // A small grant only moves the bar.
        SkillService.grant(player, Skill.MINING, 5.0);
        helper.assertTrue(skills.stars(Skill.MINING) == 0 && skills.overflow(Skill.MINING) > 0f,
                "a small grant at level 100 should fill the overflow bar, not earn a star");

        // A star's worth, with room for every multiplier (tempo, streak) only ever adding.
        SkillService.grant(player, Skill.MINING, Mastery.starCost(1));
        helper.assertTrue(skills.stars(Skill.MINING) >= 1,
                "a star's worth of XP should earn star 1, got " + skills.stars(Skill.MINING));
        helper.assertTrue(skills.level(Skill.MINING) == SkillMath.MAX_LEVEL, "the level stays 100");
        helper.succeed();
    }

    /** Telemetry counts what Mastery kept: XP thrown away past the last star is not "earned". */
    @GameTest(template = "empty")
    public static void cappedXpIsNotCountedByTelemetry(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.make(helper);
        maxed(player, Skill.SWORDS);
        SkillService.grant(player, Skill.SWORDS, Mastery.totalCost(Mastery.MAX_STARS) * 2.0, "block.minecraft.stone");
        dev.amman.proficiency.telemetry.TelemetryHub.data().drain(0L, "");
        SkillService.grant(player, Skill.SWORDS, 1000.0, "block.minecraft.stone");
        java.util.List<String> lines = dev.amman.proficiency.telemetry.TelemetryHub.data().drain(1L, "");
        boolean counted = lines.stream().anyMatch(l -> l.contains("\"type\":\"xp\"") && l.contains("\"skill\":\"swords\"")
                && !l.contains("\"xp\":0}"));
        helper.assertTrue(!counted, "a grant at five stars was counted as earned XP: " + lines);
        helper.succeed();
    }

    /** An operator's addxp is not play: it must not make the player look active for the survival streak. */
    @GameTest(template = "empty")
    public static void aCommandGrantDoesNotMarkThePlayerActive(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.make(helper);
        PlayerSkills skills = ProficiencyAttachments.of(player);
        long now = player.level().getGameTime();
        SkillService.grant(player, Skill.MINING, 10.0, "proficiency.xplog.source.command");
        helper.assertTrue(!skills.isActive(now, 1200), "an addxp command marked the player active");
        SkillService.grant(player, Skill.MINING, 10.0, "block.minecraft.stone");
        helper.assertTrue(skills.isActive(now, 1200), "a real grant did not mark the player active");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void starsStopAtFiveAndTheBarStaysEmpty(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.make(helper);
        PlayerSkills skills = maxed(player, Skill.SWORDS);

        SkillService.grant(player, Skill.SWORDS, Mastery.totalCost(Mastery.MAX_STARS) * 2.0);
        helper.assertTrue(skills.stars(Skill.SWORDS) == Mastery.MAX_STARS,
                "five stars expected, got " + skills.stars(Skill.SWORDS));
        helper.assertTrue(skills.overflow(Skill.SWORDS) == 0f, "nothing banks past star 5");
        SkillService.grant(player, Skill.SWORDS, 1000.0);
        helper.assertTrue(skills.stars(Skill.SWORDS) == Mastery.MAX_STARS
                && skills.overflow(Skill.SWORDS) == 0f, "a grant at five stars changes nothing");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aDeathKeepsEveryStarAndWipesTheBar(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.make(helper);
        PlayerSkills skills = maxed(player, Skill.AXES);
        skills.setStars(Skill.AXES, 3);
        skills.addXpMastery(Skill.AXES, 200f);
        helper.assertTrue(skills.overflow(Skill.AXES) > 0f, "setup: the bar should hold some XP");

        ServerPlayer respawned = MockPlayers.make(helper);
        MinecraftForge.EVENT_BUS.post(new PlayerEvent.Clone(respawned, player, true));
        PlayerSkills fresh = ProficiencyAttachments.of(respawned);
        helper.assertTrue(fresh.stars(Skill.AXES) == 3, "a death must never take a star");
        helper.assertTrue(fresh.overflow(Skill.AXES) == 0f, "a death must empty the overflow bar");
        helper.assertTrue(fresh.level(Skill.AXES) == SkillMath.MAX_LEVEL, "a death must never cost a level");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void theSyncAndLevelUpPacketsCarryStars(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.make(helper);
        PlayerSkills skills = maxed(player, Skill.ARCHERY);
        skills.setStars(Skill.ARCHERY, 4);
        skills.addXpMastery(Skill.ARCHERY, 321f);

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        SyncSkillsPayload.SKILLS_CODEC.encode(buf, skills);
        PlayerSkills read = SyncSkillsPayload.SKILLS_CODEC.decode(buf);
        helper.assertTrue(read.stars(Skill.ARCHERY) == 4, "the sync packet lost the stars");
        helper.assertTrue(Math.abs(read.overflow(Skill.ARCHERY) - skills.overflow(Skill.ARCHERY)) < 1e-3f,
                "the sync packet lost the overflow bar");
        helper.assertTrue(buf.readableBytes() == 0, "the sync packet left bytes unread");

        FriendlyByteBuf star = new FriendlyByteBuf(Unpooled.buffer());
        LevelUpPayload.STREAM_CODEC.encode(star, new LevelUpPayload(Skill.ARCHERY.ordinal(), 100, 5));
        LevelUpPayload back = LevelUpPayload.STREAM_CODEC.decode(star);
        helper.assertTrue(back.stars() == 5 && back.level() == 100 && back.skillOrdinal() == Skill.ARCHERY.ordinal(),
                "the level-up packet lost the star count");
        helper.succeed();
    }
}
