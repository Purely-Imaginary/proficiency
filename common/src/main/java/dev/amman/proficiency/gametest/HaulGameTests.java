package dev.amman.proficiency.gametest;

import com.mojang.authlib.GameProfile;
import dev.amman.proficiency.haul.HaulLog;
import dev.amman.proficiency.haul.HaulService;
import dev.amman.proficiency.haul.HaulSummary;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillService;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.util.UUID;

/**
 * Today's Haul (1.44.0): a grant is recorded, the log is saved with the player and comes back after a relog (the
 * player's data written and read into a new player entity), a respawn carries it, and showing the card empties it
 * for good.
 */
public final class HaulGameTests {

    private static final String T = "proficiency:empty";

    /** Fabric instantiates each test class, as a {@code fabric-gametest} entrypoint. */
    public HaulGameTests() {
    }

    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.make(helper);
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    /** A player entity that is in no list: what the server builds before it reads a returning player's file. */
    private static ServerPlayer reloaded(GameTestHelper helper, CompoundTag saved) {
        var level = helper.getLevel();
        ServerPlayer fresh = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "haul-relog"),
                net.minecraft.server.level.ClientInformation.createDefault());
        fresh.load(saved);
        return fresh;
    }

    private static void leave(ServerPlayer player) {
        HaulService.onLogout(player.getUUID());
        player.server.getPlayerList().remove(player);
    }

    @GameTest(template = T)
    public static void aSessionSurvivesARelogUntilItIsShown(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        SkillService.grantNoFirstTime(player, Skill.MINING, 400.0, null);
        SkillService.grantNoFirstTime(player, Skill.FARMING, 30.0, null);
        HaulLog live = HaulService.log(player);
        helper.assertTrue(live.totalXp() > 0, "the grants are in the log");
        helper.assertTrue(live.totalLevels() > 0, "400 XP from level 0 crosses levels");
        double xp = live.totalXp();
        int levels = live.totalLevels();

        // Log out: the server writes the player's file and forgets its copy.
        CompoundTag saved = player.saveWithoutId(new CompoundTag());
        leave(player);
        ServerPlayer back = reloaded(helper, saved);
        HaulLog loaded = HaulService.readSaved(back);
        helper.assertTrue(Math.abs(loaded.totalXp() - xp) < 1e-3, "XP after a relog: " + loaded.totalXp() + " vs " + xp);
        helper.assertTrue(loaded.totalLevels() == levels, "levels after a relog");
        helper.assertTrue(loaded.entry(Skill.MINING) != null && loaded.entry(Skill.FARMING) != null, "both rows");
        helper.succeed();
    }

    @GameTest(template = T)
    public static void showingTheCardEmptiesTheLogForGood(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        SkillService.grantNoFirstTime(player, Skill.WOODCUTTING, 120.0, null);
        HaulSummary card = HaulService.take(player);
        helper.assertTrue(card != null && card.rows().size() == 1, "one row: " + card);
        HaulSummary.Row row = card.rows().get(0);
        helper.assertTrue(row.skill() == Skill.WOODCUTTING.ordinal(), "the skill");
        helper.assertTrue(row.to() > row.from(), "the bar moved: " + row.from() + " to " + row.to());
        helper.assertTrue(HaulService.take(player) == null, "shown once");
        CompoundTag saved = player.saveWithoutId(new CompoundTag());
        leave(player);
        helper.assertTrue(HaulService.readSaved(reloaded(helper, saved)).isEmpty(), "and not back after a relog");
        helper.succeed();
    }

    @GameTest(template = T)
    public static void aRespawnCarriesTheHaul(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        SkillService.grantNoFirstTime(player, Skill.SWORDS, 60.0, null);
        CompoundTag empty = new CompoundTag();
        ServerPlayer respawned = reloaded(helper, player.saveWithoutId(empty));
        // A respawn's new body starts without the old one's free-form data; the Clone hook copies it.
        dev.amman.proficiency.platform.EntityData.of(respawned).remove(HaulService.TAG);
        HaulService.onClone(player, respawned);
        helper.assertTrue(HaulService.readSaved(respawned).entry(Skill.SWORDS) != null, "carried across");
        leave(player);
        helper.succeed();
    }
}
