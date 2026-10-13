package dev.amman.proficiency.gametest;

import dev.amman.proficiency.Proficiency;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.hearth.BellRules;
import dev.amman.proficiency.hearth.ChronicleBook;
import dev.amman.proficiency.hearth.FeastWindow;
import dev.amman.proficiency.hearth.HearthAttachments;
import dev.amman.proficiency.hearth.HearthBlock;
import dev.amman.proficiency.hearth.HearthBlockEntity;
import dev.amman.proficiency.hearth.HearthBlocks;
import dev.amman.proficiency.hearth.HearthData;
import dev.amman.proficiency.hearth.HearthEvents;
import dev.amman.proficiency.hearth.HearthLedger;
import dev.amman.proficiency.hearth.HearthPool;
import dev.amman.proficiency.hearth.HearthState;
import dev.amman.proficiency.hearth.HearthWorldData;
import dev.amman.proficiency.hearth.Hearths;
import dev.amman.proficiency.hearth.PlayerHearth;
import dev.amman.proficiency.hearth.SiegeRules;
import dev.amman.proficiency.hearth.TaleTrip;
import dev.amman.proficiency.hearth.TalesMath;
import dev.amman.proficiency.hearth.net.HearthNet;
import dev.amman.proficiency.hearth.tales.ChronicleCommand;
import dev.amman.proficiency.hearth.tales.HearthBell;
import dev.amman.proficiency.hearth.tales.HearthChronicle;
import dev.amman.proficiency.hearth.tales.HearthFeast;
import dev.amman.proficiency.hearth.tales.HearthTales;
import dev.amman.proficiency.hearth.tales.TalesBank;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.RestedMath;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillService;
import dev.amman.proficiency.skill.SpawnOrigin;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.GameType;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The tales lane in a real world on Forge 1.20.1, the port of common's {@code TalesGameTests} (the same
 * test names): Tales banked away and paid on arrival (and lost on
 * a death), the Feast once per in-game day, the Chronicle on a lectern (abuse check 16: one write
 * bonus per homecoming, a page rates once, the command needs op level 2) and the context bell (abuse
 * check 18: alarm beats dinner beats come home, a non-Hearth bell does nothing, the Homeward window is
 * 60 s). The rules themselves are unit tested in core ({@code TalesChronicleTest},
 * {@code HearthGatheringTest}). Each Hearth-placing test has its own batch, because a neighbour's
 * Hearth would be in range of the tiny {@code empty} template.
 */
@GameTestHolder(Proficiency.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TalesGameTests {

    private static final String T = "empty";

    private TalesGameTests() {
    }

    // ---- Helpers ----------------------------------------------------------------------------------

    private static ServerPlayer survivor(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.make(helper);
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static HearthBlockEntity lightHearth(GameTestHelper helper, BlockPos rel, int stage) {
        helper.setBlock(rel, HearthBlocks.HEARTH.get().defaultBlockState().setValue(HearthBlock.STAGE, Math.max(1, stage))
                .setValue(HearthBlock.SMOULDERING, false));
        HearthBlockEntity hearth = (HearthBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(rel));
        hearth.state().setStage(stage);
        hearth.state().onLit(helper.getLevel().getGameTime(), 0L);
        hearth.setChanged();
        return hearth;
    }

    /** The Hearth's own tick would write this each second; the stages lane does it for real. */
    private static void atHome(ServerPlayer player, HearthBlockEntity hearth) {
        ServerLevel level = player.serverLevel();
        HearthAttachments.of(player).seenBy(hearth.packedPos(), level.dimension().location().toString(),
                level.getGameTime());
    }

    private static void fed(HearthBlockEntity hearth, ServerPlayer player) {
        hearth.state().ledger().add(player.getUUID(), player.getGameProfile().getName(), HearthPool.HEARTY, "mining",
                5, RestedMath.today());
    }

    private static double homecoming(HearthBlockEntity hearth) {
        return hearth.state().charge(HearthPool.HOMECOMING);
    }

    private static double ledgerSource(HearthBlockEntity hearth, UUID player, String source) {
        for (HearthLedger.SourceTotal total : hearth.state().ledger().sources(player, HearthPool.HOMECOMING,
                HearthLedger.Window.LIFETIME)) {
            if (total.source().equals(source)) {
                return total.total();
            }
        }
        return 0.0;
    }

    /** A book and quill with these pages (1.20.1: plain strings under the stack's {@code pages} tag). */
    private static ItemStack book(String... pages) {
        ItemStack book = new ItemStack(Items.WRITABLE_BOOK);
        ListTag list = new ListTag();
        for (String page : pages) {
            list.add(StringTag.valueOf(page));
        }
        book.getOrCreateTag().put("pages", list);
        return book;
    }

    /** A right click on a block, as Forge posts it (1.20.1's event carries the hit). */
    private static PlayerInteractEvent.RightClickBlock click(ServerPlayer player, BlockPos pos) {
        return new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, pos,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }

    /** A compass bound to a lodestone at {@code pos} (1.20.1: LodestonePos and LodestoneDimension in NBT). */
    private static void bind(ItemStack compass, ServerLevel level, BlockPos pos) {
        CompoundTag tag = compass.getOrCreateTag();
        tag.put("LodestonePos", NbtUtils.writeBlockPos(pos));
        Level.RESOURCE_KEY_CODEC.encodeStart(NbtOps.INSTANCE, level.dimension()).result()
                .ifPresent(dimension -> tag.put("LodestoneDimension", dimension));
        tag.putBoolean("LodestoneTracked", true);
    }

    // ---- Tales: banking ---------------------------------------------------------------------------

    @GameTest(template = T, batch = "tales_bank")
    public static void movementAndExpeditionXpAwayFromHomeIsBankedAsTales(GameTestHelper helper) {
        ExtrasTestGuard.watch(helper);
        ServerPlayer player = survivor(helper);
        PlayerHearth data = HearthAttachments.of(player);
        HearthNet.takeHudDirty(player);
        float paid = SkillService.grant(player, Skill.RUNNING, 40.0, "proficiency.xplog.source.sprinting");
        helper.assertTrue(paid > 0f, "setup: the grant paid nothing");
        double banked = data.tales();
        helper.assertTrue(banked > 0.0 && banked <= 0.05 * paid + 1e-6,
                "Running away from home banks about 5% as Tales: " + banked + " of " + paid);
        helper.assertTrue(data.flag(TalesBank.AWAY), "the trip began");
        helper.assertTrue(HearthNet.takeHudDirty(player), "the HUD is told when the scroll count changes");
        SkillService.grant(player, Skill.WAYFARING, 40.0, "structure.minecraft.stronghold");
        helper.assertTrue(data.tales() > banked, "Expedition XP banks too");
        helper.assertTrue(TalesBank.placesOf(data).contains("structure.minecraft.stronghold"),
                "a discovery is remembered for the homecoming line");

        // XP that is not a Tales feed banks nothing.
        double before = data.tales();
        SkillService.grant(player, Skill.MINING, 40.0, "block.minecraft.stone");
        helper.assertTrue(data.tales() == before, "Mining is not a Tales feed");
        // A first-time bonus line is not travel either.
        SkillService.grant(player, Skill.RUNNING, 40.0, "first|running|sprint");
        helper.assertTrue(data.tales() == before, "a first-time bonus is no tale: " + data.tales() + " vs " + before);
        helper.succeed();
    }

    @GameTest(template = T, batch = "tales_storyteller")
    public static void storytellerAddsAThirdAtFullRanksAndNothingBefore(GameTestHelper helper) {
        ExtrasTestGuard.watch(helper);
        ServerPlayer player = survivor(helper);
        helper.assertTrue(HearthTales.storyteller.applyAsDouble(player) == 0.0, "no Storyteller rank, no extra share");
        PlayerSkills skills = ProficiencyAttachments.of(player);
        skills.setLevel(Skill.HOMEBUILDER, 100);
        skills.fillTree(Skill.HOMEBUILDER);
        double extra = HearthTales.storyteller.applyAsDouble(player);
        helper.assertTrue(Math.abs(extra - 0.30) < 1e-9, "three ranks of Storyteller add 30%: " + extra);
        // The banked amount takes it: 1.3 times the plain share of the same XP.
        HearthData data = Hearths.data();
        double plain = TalesMath.banked(Skill.RUNNING, 40.0, 0.0, data);
        helper.assertTrue(Math.abs(TalesMath.banked(Skill.RUNNING, 40.0, extra, data) - plain * 1.3) < 1e-9,
                "the bank grows by the Storyteller share");
        helper.succeed();
    }

    @GameTest(template = T, batch = "tales_bank_home")
    public static void nothingIsBankedAtAHearth(GameTestHelper helper) {
        ExtrasTestGuard.watch(helper);
        ServerPlayer player = survivor(helper);
        HearthBlockEntity hearth = lightHearth(helper, new BlockPos(1, 2, 1), 2);
        atHome(player, hearth);
        PlayerHearth data = HearthAttachments.of(player);
        SkillService.grant(player, Skill.RUNNING, 40.0, "proficiency.xplog.source.sprinting");
        helper.assertTrue(data.tales() == 0.0, "Movement XP earned at home is not a tale: " + data.tales());
        helper.assertFalse(data.flag(TalesBank.AWAY), "no trip at home");
        // Smouldering is not home: boons are off and a trip is on.
        hearth.state().setSmouldering(true);
        SkillService.grant(player, Skill.RUNNING, 40.0, "proficiency.xplog.source.sprinting");
        helper.assertTrue(data.tales() > 0.0, "a smouldering Hearth is not home");
        helper.setBlock(new BlockPos(1, 2, 1), Blocks.AIR);
        helper.succeed();
    }

    // ---- Tales: arriving --------------------------------------------------------------------------

    @GameTest(template = T, batch = "tales_pay")
    public static void comingHomeToAHearthYouFedPaysTheTales(GameTestHelper helper) {
        ExtrasTestGuard.watch(helper);
        ServerPlayer player = survivor(helper);
        HearthBlockEntity hearth = lightHearth(helper, new BlockPos(1, 2, 1), 2);
        PlayerHearth data = HearthAttachments.of(player);
        List<Double> paidEvents = new ArrayList<>();
        HearthEvents.TALES_PAID.add(event -> {
            if (event.player() == player) {
                paidEvents.add(event.tales());
            }
        });
        data.addTales(120);
        TalesBank.markAway(player, data);
        data.setFlag(TalesBank.PLACE + "structure.minecraft.stronghold", true);
        data.setMark(TalesBank.TRIP, TalesBank.walkedCm(player) - 320_000L);

        // A Hearth you never fed hears nothing: the trip goes on.
        atHome(player, hearth);
        TalesBank.onSecond(player);
        helper.assertTrue(data.tales() == 120.0 && data.flag(TalesBank.AWAY), "an unfed Hearth pays nothing");
        helper.assertTrue(homecoming(hearth) == 0.0, "no charge from an unfed arrival");

        fed(hearth, player);
        // (An unfed Hearth is asked again every few seconds, not every second: let it ask now.)
        TalesBank.forget();
        HearthNet.takeHudDirty(player);
        atHome(player, hearth);
        TalesBank.onSecond(player);
        helper.assertTrue(data.tales() == 0.0, "the bank is paid out: " + data.tales());
        helper.assertTrue(homecoming(hearth) == 120.0, "Homecoming took the Tales: " + homecoming(hearth));
        helper.assertTrue(ledgerSource(hearth, player.getUUID(), HearthLedger.TALES) == 120.0, "ledger source tales");
        helper.assertTrue(data.lastTalesPaid() == 120.0 && data.chronicleOpen(), "the Chronicle bonus is open");
        helper.assertFalse(data.flag(TalesBank.AWAY) || !TalesBank.placesOf(data).isEmpty(), "the trip ended");
        helper.assertTrue(paidEvents.size() == 1 && paidEvents.get(0) == 120.0, "TALES_PAID fired once: " + paidEvents);
        helper.assertTrue(HearthNet.takeHudDirty(player), "the HUD hears the scroll emptied");

        // Nothing more to pay, and no second payout from standing there.
        TalesBank.onSecond(player);
        helper.assertTrue(homecoming(hearth) == 120.0 && paidEvents.size() == 1, "paid once");
        helper.setBlock(new BlockPos(1, 2, 1), Blocks.AIR);
        helper.succeed();
    }

    @GameTest(template = T, batch = "tales_pay_real", timeoutTicks = 140)
    public static void theRealHearthTickAndPlayerLoopPayTheTalesOnArrival(GameTestHelper helper) {
        ExtrasTestGuard.watch(helper);
        ServerPlayer player = survivor(helper);
        ServerLevel level = helper.getLevel();
        HearthBlockEntity hearth = lightHearth(helper, new BlockPos(1, 2, 1), 2);
        BlockPos at = hearth.getBlockPos();
        // Nobody calls atHome here: the stages lane's Hearth tick (forge-server's HearthPresence) flags the player
        // standing by the fire, and the once-a-second player loop (the PLAYER_SECOND hook this lane registered)
        // does the rest. A Forge line without that production tick fails here (fix round, 2026-10-10).
        player.setPos(at.getX() + 1.5, at.getY(), at.getZ() + 0.5);
        fed(hearth, player);
        PlayerHearth data = HearthAttachments.of(player);
        data.addTales(60);
        data.setFlag(TalesBank.AWAY, true);
        helper.runAfterDelay(100, () -> {
            // Homebuilder pays XP for Tales paid in, Social earns a share of any XP, and the charge lane mirrors that
            // into this pool once the pot pays (every 5 s): the ledger cell of the source is the exact fact, the
            // pool total only has to hold at least that.
            helper.assertTrue(homecoming(hearth) >= 60.0, "the Tales were paid on their own: " + homecoming(hearth));
            helper.assertTrue(data.tales() == 0.0 && !data.flag(TalesBank.AWAY), "the bank is empty and the trip over");
            helper.assertTrue(ledgerSource(hearth, player.getUUID(), HearthLedger.TALES) == 60.0, "ledger source tales");
            helper.setBlock(new BlockPos(1, 2, 1), Blocks.AIR);
            helper.succeed();
        });
    }

    @GameTest(template = T, batch = "tales_pay_cap")
    public static void theHourlyCapKeepsWhatItRefusesInTheBank(GameTestHelper helper) {
        ExtrasTestGuard.watch(helper);
        ServerPlayer player = survivor(helper);
        HearthBlockEntity hearth = lightHearth(helper, new BlockPos(1, 2, 1), 2);
        fed(hearth, player);
        PlayerHearth data = HearthAttachments.of(player);
        data.addTales(500);
        TalesBank.markAway(player, data);
        atHome(player, hearth);
        TalesBank.onSecond(player);
        helper.assertTrue(homecoming(hearth) == 300.0, "the cap is 300 a pool an hour: " + homecoming(hearth));
        helper.assertTrue(data.tales() == 200.0, "what the cap refused stays banked: " + data.tales());
        // The cap is full now: the next homecoming pays nothing and the trip ends.
        TalesBank.markAway(player, data);
        atHome(player, hearth);
        TalesBank.onSecond(player);
        helper.assertTrue(homecoming(hearth) == 300.0 && data.tales() == 200.0, "a full cap pays nothing");
        helper.assertFalse(data.flag(TalesBank.AWAY), "the trip ended, the bank keeps");
        helper.setBlock(new BlockPos(1, 2, 1), Blocks.AIR);
        helper.succeed();
    }

    @GameTest(template = T, batch = "tales_pay_unfed", timeoutTicks = 200)
    public static void anUnfedHearthIsAskedAgainEveryFewSecondsNotEverySecond(GameTestHelper helper) {
        ExtrasTestGuard.watch(helper);
        ServerPlayer player = survivor(helper);
        ServerLevel level = helper.getLevel();
        HearthBlockEntity hearth = lightHearth(helper, new BlockPos(1, 2, 1), 2);
        PlayerHearth data = HearthAttachments.of(player);
        data.addTales(90);
        TalesBank.markAway(player, data);
        atHome(player, hearth);
        TalesBank.onSecond(player);
        helper.assertTrue(data.flag(TalesBank.AWAY) && data.tales() == 90.0, "an unfed Hearth hears nothing, the trip goes on");
        // Fed a moment later: the Hearth is not asked again yet (the ledger is not read every second).
        fed(hearth, player);
        atHome(player, hearth);
        TalesBank.onSecond(player);
        helper.assertTrue(data.tales() == 90.0 && homecoming(hearth) == 0.0, "still inside the recheck window: "
                + data.tales());
        helper.runAfterDelay(TalesBank.UNFED_RECHECK_TICKS + 5, () -> {
            atHome(player, hearth);
            TalesBank.onSecond(player);
            helper.assertTrue(data.tales() == 0.0 && !data.flag(TalesBank.AWAY), "asked again, fed now: the Tales are paid");
            helper.assertTrue(ledgerSource(hearth, player.getUUID(), HearthLedger.TALES) == 90.0, "ledger source tales");
            helper.assertTrue(level.getGameTime() > 0, "time passed");
            helper.setBlock(new BlockPos(1, 2, 1), Blocks.AIR);
            helper.succeed();
        });
    }

    @GameTest(template = T, batch = "tales_places")
    public static void aFullTripStillRemembersAStrongholdFoundAfterEightBiomes(GameTestHelper helper) {
        ExtrasTestGuard.watch(helper);
        ServerPlayer player = survivor(helper);
        PlayerHearth data = HearthAttachments.of(player);
        for (String biome : List.of("plains", "forest", "desert", "taiga", "savanna", "jungle", "swamp", "badlands")) {
            SkillService.grant(player, Skill.WAYFARING, 40.0, "biome.minecraft." + biome);
        }
        helper.assertTrue(TalesBank.placesOf(data).size() == 8, "setup: eight places: " + TalesBank.placesOf(data));
        SkillService.grant(player, Skill.WAYFARING, 40.0, "structure.minecraft.stronghold");
        SkillService.grant(player, Skill.WAYFARING, 40.0, "dimension.minecraft.the_nether");
        List<String> places = TalesBank.placesOf(data);
        helper.assertTrue(places.size() == 8 && places.contains("structure.minecraft.stronghold")
                && places.contains("dimension.minecraft.the_nether"), "the better places made room: " + places);
        helper.assertTrue(TaleTrip.highlights(places, TaleTrip.NAMED).subList(0, 2)
                .equals(List.of("structure.minecraft.stronghold", "dimension.minecraft.the_nether")),
                "and the line names them first: " + TaleTrip.highlights(places, TaleTrip.NAMED));
        helper.succeed();
    }

    @GameTest(template = T, batch = "tales_pay_small")
    public static void aBankUnderOneChargeIsNotWorthAHomecoming(GameTestHelper helper) {
        ExtrasTestGuard.watch(helper);
        ServerPlayer player = survivor(helper);
        HearthBlockEntity hearth = lightHearth(helper, new BlockPos(1, 2, 1), 2);
        fed(hearth, player);
        PlayerHearth data = HearthAttachments.of(player);
        data.addTales(0.6);
        TalesBank.markAway(player, data);
        atHome(player, hearth);
        TalesBank.onSecond(player);
        helper.assertTrue(homecoming(hearth) == 0.0 && data.tales() == 0.6, "dust stays banked");
        helper.assertFalse(data.flag(TalesBank.AWAY), "and the trip ends");
        helper.setBlock(new BlockPos(1, 2, 1), Blocks.AIR);
        helper.succeed();
    }

    // ---- Tales: dying -----------------------------------------------------------------------------

    @GameTest(template = T, batch = "tales_death")
    public static void aDeathAwayFromHomeLosesTheBankedTales(GameTestHelper helper) {
        ExtrasTestGuard.watch(helper);
        ServerPlayer dead = survivor(helper);
        ServerPlayer fresh = survivor(helper);
        PlayerHearth before = HearthAttachments.of(dead);
        before.addTales(75);
        TalesBank.markAway(dead, before);
        before.setFlag(TalesBank.PLACE + "biome.minecraft.desert", true);
        // Abuse check 6: no boon softens a death; the Tales of the trip go with you.
        MinecraftForge.EVENT_BUS.post(new PlayerEvent.Clone(fresh, dead, true));
        PlayerHearth after = HearthAttachments.of(fresh);
        helper.assertTrue(after.tales() == 0.0, "the banked Tales are lost: " + after.tales());
        helper.assertFalse(after.flag(TalesBank.AWAY) || !TalesBank.placesOf(after).isEmpty(), "the trip is over");
        helper.assertFalse(after.chronicleOpen(), "the open Chronicle bonus goes too");

        // Leaving the End is a clone that is no death: the Tales travel with you.
        ServerPlayer traveller = survivor(helper);
        after.addTales(12);
        MinecraftForge.EVENT_BUS.post(new PlayerEvent.Clone(traveller, fresh, false));
        helper.assertTrue(HearthAttachments.of(traveller).tales() == 12.0, "an End exit keeps the Tales");
        helper.succeed();
    }

    // ---- The Feast --------------------------------------------------------------------------------

    private static void eat(ServerPlayer player) {
        MinecraftForge.EVENT_BUS.post(new LivingEntityUseItemEvent.Finish(player, new ItemStack(Items.BREAD), 0,
                ItemStack.EMPTY));
    }

    @GameTest(template = T, batch = "tales_feast")
    public static void aFeastWarmsEveryEaterOncePerInGameDay(GameTestHelper helper) {
        ExtrasTestGuard.watch(helper);
        ServerPlayer a = survivor(helper);
        ServerPlayer b = survivor(helper);
        ServerPlayer c = survivor(helper);
        ServerLevel level = helper.getLevel();
        HearthBlockEntity hearth = lightHearth(helper, new BlockPos(1, 2, 1), 2);
        List<Integer> feasts = new ArrayList<>();
        HearthEvents.FEAST.add(event -> {
            if (event.hearth().equals(hearth.getBlockPos())) {
                feasts.add(event.eaters().size());
            }
        });
        for (ServerPlayer p : List.of(a, b, c)) {
            atHome(p, hearth);
            HearthAttachments.of(p).setWarmth(0.3);
        }
        // Eating alone is no Feast.
        eat(a);
        helper.assertTrue(homecoming(hearth) == 0.0 && HearthAttachments.of(a).warmth() == 0.3f, "solo play gets nothing");

        // A second eater inside the window starts it: everyone's Warmth fills, +20 for the extra eater.
        eat(b);
        helper.assertTrue(HearthAttachments.of(a).warmth() == 1.0f && HearthAttachments.of(b).warmth() == 1.0f,
                "the eaters' Warmth is full");
        helper.assertTrue(HearthAttachments.of(c).warmth() == 0.3f, "someone who did not eat is not warmed");
        helper.assertTrue(homecoming(hearth) == 20.0, "20 Homecoming charge for one extra eater: " + homecoming(hearth));
        helper.assertTrue(ledgerSource(hearth, a.getUUID(), HearthLedger.FEAST)
                + ledgerSource(hearth, b.getUUID(), HearthLedger.FEAST) == 20.0, "the ledger holds the Feast's 20");
        helper.assertTrue(feasts.equals(List.of(2)), "FEAST fired with both eaters: " + feasts);
        helper.assertTrue(HearthAttachments.of(a).lastFeast() == level.getGameTime(), "the Feast is marked on the player");

        // A third eater joins the open window: another 20.
        eat(c);
        helper.assertTrue(HearthAttachments.of(c).warmth() == 1.0f && homecoming(hearth) == 40.0,
                "joining adds one more extra eater: " + homecoming(hearth));

        // Once per in-game day per Hearth (abuse: a loop of meals is no loop of charge): with the
        // window long closed, the same day's meals pay nothing and warm nobody.
        long day = hearth.state().feast().lastFeastDay();
        hearth.state().feast().restore(day, HearthState.NEVER);
        for (ServerPlayer p : List.of(a, b, c)) {
            HearthAttachments.of(p).setWarmth(0.2);
        }
        eat(a);
        eat(b);
        helper.assertTrue(homecoming(hearth) == 40.0, "a second Feast on the same day pays nothing: " + homecoming(hearth));
        helper.assertTrue(HearthAttachments.of(a).warmth() == 0.2f, "and warms nobody");

        // The next in-game day is a new Feast.
        hearth.state().feast().restore(day - 1, HearthState.NEVER);
        eat(a);
        eat(b);
        helper.assertTrue(homecoming(hearth) == 60.0, "the next day feasts again: " + homecoming(hearth));
        helper.assertTrue(HearthAttachments.of(a).warmth() == 1.0f, "Warmth full again");
        helper.setBlock(new BlockPos(1, 2, 1), Blocks.AIR);
        helper.succeed();
    }

    @GameTest(template = T, batch = "tales_feast_out")
    public static void aMealAwayFromTheHearthOrAtASmoulderingOneIsNoFeast(GameTestHelper helper) {
        ExtrasTestGuard.watch(helper);
        ServerPlayer a = survivor(helper);
        ServerPlayer b = survivor(helper);
        HearthBlockEntity hearth = lightHearth(helper, new BlockPos(1, 2, 1), 2);
        // Only a is at the Hearth.
        atHome(a, hearth);
        eat(a);
        eat(b);
        helper.assertTrue(homecoming(hearth) == 0.0, "a meal away from the Hearth is not at the table");
        // Both there, but the fire is out.
        atHome(b, hearth);
        hearth.state().setSmouldering(true);
        eat(a);
        eat(b);
        helper.assertTrue(homecoming(hearth) == 0.0 && !hearth.state().feast().feastedOn(
                Math.floorDiv(helper.getLevel().getDayTime(), HearthData.TICKS_PER_DAY)), "a smouldering Hearth holds no Feast");
        helper.setBlock(new BlockPos(1, 2, 1), Blocks.AIR);
        helper.succeed();
    }

    @GameTest(template = T, batch = "tales_feast_walkoff")
    public static void aFeastIsNotSpentOnAnEaterWhoAlreadyWalkedOff(GameTestHelper helper) {
        ExtrasTestGuard.watch(helper);
        ServerPlayer a = survivor(helper);
        ServerPlayer b = survivor(helper);
        ServerLevel level = helper.getLevel();
        HearthBlockEntity hearth = lightHearth(helper, new BlockPos(1, 2, 1), 2);
        long day = Math.floorDiv(level.getDayTime(), HearthData.TICKS_PER_DAY);
        List<Integer> feasts = new ArrayList<>();
        HearthEvents.FEAST.add(event -> {
            if (event.hearth().equals(hearth.getBlockPos())) {
                feasts.add(event.eaters().size());
            }
        });
        atHome(a, hearth);
        atHome(b, hearth);
        HearthAttachments.of(a).setWarmth(0.3);
        HearthAttachments.of(b).setWarmth(0.3);
        eat(a);
        // A leaves the table; B eats inside the window. One at the table is not a Feast and spends nothing.
        HearthAttachments.of(a).clearInRange();
        eat(b);
        helper.assertTrue(feasts.isEmpty(), "no Feast fired for one eater at the table: " + feasts);
        helper.assertTrue(homecoming(hearth) == 0.0, "nothing was charged: " + homecoming(hearth));
        helper.assertTrue(HearthAttachments.of(b).warmth() == 0.3f, "B was not warmed alone");
        helper.assertFalse(hearth.state().feast().feastedOn(day), "the day's Feast is not spent");
        // A is back and eats again: now two are at the table, and the day's Feast is theirs.
        atHome(a, hearth);
        eat(a);
        helper.assertTrue(feasts.equals(List.of(2)), "the Feast fired with both: " + feasts);
        helper.assertTrue(homecoming(hearth) == 20.0, "one extra eater: " + homecoming(hearth));
        helper.assertTrue(HearthAttachments.of(a).warmth() == 1.0f && HearthAttachments.of(b).warmth() == 1.0f,
                "both are warmed");
        helper.assertTrue(hearth.state().feast().feastedOn(day), "and the day is spent now");
        helper.setBlock(new BlockPos(1, 2, 1), Blocks.AIR);
        helper.succeed();
    }

    // ---- The Chronicle ----------------------------------------------------------------------------

    private static Path chronicleFile(ServerLevel level) {
        return HearthChronicle.file(level.getServer());
    }

    private static List<String> chronicleLines(ServerLevel level) {
        try {
            Path file = chronicleFile(level);
            return Files.isRegularFile(file) ? Files.readAllLines(file, StandardCharsets.UTF_8) : List.of();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @GameTest(template = T, batch = "tales_chronicle", timeoutTicks = 120)
    public static void aNewPageOnALecternIsFiledAndPaysTheFlatBonusOncePerHomecoming(GameTestHelper helper) {
        ExtrasTestGuard.watch(helper);
        ServerPlayer player = survivor(helper);
        ServerLevel level = helper.getLevel();
        HearthBlockEntity hearth = lightHearth(helper, new BlockPos(1, 2, 1), 2);
        BlockPos lecternRel = new BlockPos(3, 2, 1);
        BlockPos lectern = helper.absolutePos(lecternRel);
        helper.setBlock(lecternRel, Blocks.LECTERN);
        PlayerHearth data = HearthAttachments.of(player);
        data.notePaid(100);
        atHome(player, hearth);
        int linesBefore = chronicleLines(level).size();

        // The player uses the lectern (registering it) and sets a book and quill on it.
        MinecraftForge.EVENT_BUS.post(click(player, lectern));
        helper.assertTrue(LecternBlock.tryPlaceBook(player, level, lectern, level.getBlockState(lectern),
                book("We walked to the stronghold at dusk.\nIt was cold.")), "setup: the book went on the lectern");
        helper.runAfterDelay(45, () -> {
            HearthWorldData world = HearthWorldData.get(level.getServer());
            Optional<ChronicleBook.Page> first = world.chronicle().page(1);
            ChronicleBook.Page page = world.chronicle().page(world.chronicle().nextId() - 1).orElse(null);
            helper.assertTrue(page != null && page.author().equals(player.getUUID()), "a page by the player: " + page);
            helper.assertTrue(page.tales() == 100.0, "the first page goes with the homecoming's Tales: " + page.tales());
            helper.assertTrue(page.title().equals("We walked to the stronghold at dusk."), "title: " + page.title());
            helper.assertTrue(page.hearth().equals(hearth.key()), "the page knows its Hearth");
            // Homebuilder's XP (and Social's share of it) mirrors back into the pool a little within the test's
            // ticks, so the exact fact is the ledger cell of this lane's source, not the pool total.
            helper.assertTrue(ledgerSource(hearth, player.getUUID(), HearthLedger.CHRONICLE) == 25.0,
                    "the flat bonus is 25% of the 100 paid, ledger source chronicle: " + homecoming(hearth));
            helper.assertTrue(homecoming(hearth) >= 25.0, "and it is in the pool: " + homecoming(hearth));
            helper.assertFalse(data.chronicleOpen(), "the bonus is spent");
            List<String> lines = chronicleLines(level);
            helper.assertTrue(lines.size() == linesBefore + 1, "one line in chronicle.jsonl: " + lines.size());
            String line = lines.get(lines.size() - 1);
            helper.assertTrue(line.contains("\"author\":\"" + player.getUUID() + "\"") && line.contains("We walked to the stronghold")
                    && line.contains("\"id\":" + page.id()) && line.contains("\"tales\":100.0") && line.contains("\"real\":\""),
                    "the line carries the page: " + line);

            // A second page after the same homecoming pays nothing more (abuse check 16).
            ItemStack lectern2 = book("We walked to the stronghold at dusk.\nIt was cold.", "Then we went home.");
            LecternBlockEntity be = (LecternBlockEntity) level.getBlockEntity(lectern);
            be.setBook(lectern2);
            atHome(player, hearth);
            helper.assertTrue(HearthChronicle.scan(level, hearth, lectern) == 1, "only the new page is filed");
            helper.assertTrue(ledgerSource(hearth, player.getUUID(), HearthLedger.CHRONICLE) == 25.0,
                    "no second write bonus for the same homecoming: " + homecoming(hearth));
            ChronicleBook.Page second = world.chronicle().page(world.chronicle().nextId() - 1).orElseThrow();
            helper.assertTrue(second.tales() == 0.0 && second.id() == page.id() + 1, "the second page went with no Tales");
            helper.assertTrue(HearthChronicle.scan(level, hearth, lectern) == 0, "an unchanged book files nothing");
            helper.assertTrue(first.isPresent(), "page 1 exists");
            helper.setBlock(new BlockPos(1, 2, 1), Blocks.AIR);
            helper.setBlock(lecternRel, Blocks.AIR);
            helper.succeed();
        });
    }

    @GameTest(template = T, batch = "tales_chronicle_old")
    public static void aBookAlreadyOnALecternIsNotNewWriting(GameTestHelper helper) {
        ExtrasTestGuard.watch(helper);
        ServerPlayer player = survivor(helper);
        ServerLevel level = helper.getLevel();
        HearthBlockEntity hearth = lightHearth(helper, new BlockPos(1, 2, 1), 2);
        BlockPos lecternRel = new BlockPos(3, 2, 1);
        BlockPos lectern = helper.absolutePos(lecternRel);
        helper.setBlock(lecternRel, Blocks.LECTERN);
        // The book was there before anyone used the lectern: it is the shelf, not a tale.
        helper.assertTrue(LecternBlock.tryPlaceBook(player, level, lectern, level.getBlockState(lectern),
                book("Old page one.", "Old page two.")), "setup");
        PlayerHearth data = HearthAttachments.of(player);
        data.notePaid(100);
        atHome(player, hearth);
        long next = HearthWorldData.get(level.getServer()).chronicle().nextId();
        MinecraftForge.EVENT_BUS.post(click(player, lectern));
        helper.assertTrue(HearthChronicle.scan(level, hearth, lectern) == 0, "old pages are not filed");
        helper.assertTrue(HearthWorldData.get(level.getServer()).chronicle().nextId() == next, "no page was written");
        helper.assertTrue(data.chronicleOpen() && homecoming(hearth) == 0.0, "no bonus for a shelf");
        // New writing on the same book is.
        ((LecternBlockEntity) level.getBlockEntity(lectern)).setBook(book("Old page one.", "Old page two.", "New page."));
        helper.assertTrue(HearthChronicle.scan(level, hearth, lectern) == 1, "the new page is filed");
        helper.assertTrue(homecoming(hearth) == 25.0, "and pays the flat bonus");
        // A lectern outside every Hearth's range is nobody's business.
        helper.setBlock(new BlockPos(1, 2, 1), Blocks.AIR);
        helper.setBlock(lecternRel, Blocks.AIR);
        helper.succeed();
    }

    @GameTest(template = T, batch = "tales_chronicle_backoff")
    public static void aBookWithPagesNobodyCanWriteYetIsNotReadAgainEverySecond(GameTestHelper helper) {
        ExtrasTestGuard.watch(helper);
        ServerPlayer player = survivor(helper);
        ServerLevel level = helper.getLevel();
        HearthBlockEntity hearth = lightHearth(helper, new BlockPos(1, 2, 1), 2);
        BlockPos lecternRel = new BlockPos(3, 2, 1);
        BlockPos lectern = helper.absolutePos(lecternRel);
        helper.setBlock(lecternRel, Blocks.LECTERN);
        HearthChronicle.register(level, hearth, lectern, level.getBlockState(lectern));
        ((LecternBlockEntity) level.getBlockEntity(lectern)).setBook(book("A page nobody was home to write."));
        long next = HearthWorldData.get(level.getServer()).chronicle().nextId();

        // Nobody is at the Hearth: nothing is filed, and the book is left alone for a few seconds.
        HearthAttachments.of(player).clearInRange();
        helper.assertTrue(HearthChronicle.scan(level, hearth, lectern) == 0, "nobody home, nothing filed");
        atHome(player, hearth);
        helper.assertTrue(HearthChronicle.scan(level, hearth, lectern) == 0,
                "the book is not read again at once (the backoff holds)");
        helper.assertTrue(HearthWorldData.get(level.getServer()).chronicle().nextId() == next, "still no page");

        // Someone using the lectern ends the wait: the page is filed to them.
        HearthChronicle.onLecternUse(player, level, lectern, level.getBlockState(lectern));
        helper.assertTrue(HearthChronicle.scan(level, hearth, lectern) == 1, "a use of the lectern reads the book");
        ChronicleBook.Page page = HearthWorldData.get(level.getServer()).chronicle().page(next).orElseThrow();
        helper.assertTrue(page.author().equals(player.getUUID()), "by the one at home: " + page);
        helper.assertTrue(HearthChronicle.scan(level, hearth, lectern) == 0, "and an unchanged book files nothing");
        helper.setBlock(new BlockPos(1, 2, 1), Blocks.AIR);
        helper.setBlock(lecternRel, Blocks.AIR);
        helper.succeed();
    }

    // ---- Rating (abuse check 16) ------------------------------------------------------------------

    private static int run(GameTestHelper helper, int permission, String command) {
        CommandDispatcher<CommandSourceStack> dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
        CommandSourceStack source = helper.getLevel().getServer().createCommandSourceStack().withPermission(permission);
        ParseResults<CommandSourceStack> parsed = dispatcher.parse(command, source);
        try {
            return dispatcher.execute(parsed);
        } catch (CommandSyntaxException e) {
            return -1;
        }
    }

    @GameTest(template = T, batch = "tales_rating")
    public static void aPageRatesOnceFromTheCommandAndTheCommandNeedsOpLevelTwo(GameTestHelper helper) {
        ExtrasTestGuard.watch(helper);
        ServerPlayer author = survivor(helper);
        ServerLevel level = helper.getLevel();
        HearthBlockEntity hearth = lightHearth(helper, new BlockPos(1, 2, 1), 3);
        HearthWorldData world = HearthWorldData.get(level.getServer());
        ChronicleBook.Page page = world.chronicle().write(author.getUUID(), "Amman", hearth.key(), 80, 1, "The stronghold");
        String command = "proficiency hearth tale " + page.id() + " 4";

        // Op level 2 only: a player without it does not even see the command.
        helper.assertTrue(run(helper, 0, command) == -1, "the command is refused below op level 2");
        helper.assertTrue(run(helper, 1, command) == -1, "and at level 1");
        helper.assertTrue(!world.chronicle().page(page.id()).orElseThrow().rated() && homecoming(hearth) == 0.0,
                "a refused command rates nothing");

        // Fill the hourly cap first: a rated tale is a burst outside it.
        hearth.state().addCapped(HearthPool.HOMECOMING, 300, level.getGameTime(), Hearths.data());
        helper.assertTrue(run(helper, 2, command) == 1, "op level 2 rates the page");
        ChronicleBook.Page rated = world.chronicle().page(page.id()).orElseThrow();
        helper.assertTrue(rated.rated() && rated.stars() == 4, "four stars: " + rated);
        // 4 stars x 5% x 80 Tales = 16, on top of the capped 300.
        helper.assertTrue(homecoming(hearth) == 316.0, "the burst is stars x 5% of the page's Tales, outside the cap: "
                + homecoming(hearth));
        helper.assertTrue(ledgerSource(hearth, author.getUUID(), HearthLedger.CHRONICLE) == 16.0, "ledger: chronicle");
        helper.assertTrue(rated.told(), "the author is online: told at once");

        // A second call for the same page is refused and pays nothing (abuse check 16).
        helper.assertTrue(run(helper, 4, "proficiency hearth tale " + page.id() + " 5") == 0, "a page rates once");
        helper.assertTrue(homecoming(hearth) == 316.0, "nothing more was paid");
        helper.assertTrue(world.chronicle().page(page.id()).orElseThrow().stars() == 4, "the first rating stands");
        helper.assertTrue(run(helper, 2, "proficiency hearth tale 9999 3") == 0, "an unknown page is refused");
        helper.assertTrue(run(helper, 2, "proficiency hearth tale 1 6") == -1, "stars are 0 to 5");
        helper.setBlock(new BlockPos(1, 2, 1), Blocks.AIR);
        helper.succeed();
    }

    @GameTest(template = T, batch = "tales_rating_gone")
    public static void aRatingForABrokenHearthStillRatesTheOnePage(GameTestHelper helper) {
        ExtrasTestGuard.watch(helper);
        ServerPlayer author = survivor(helper);
        ServerLevel level = helper.getLevel();
        HearthWorldData world = HearthWorldData.get(level.getServer());
        BlockPos nowhere = helper.absolutePos(new BlockPos(1, 2, 1));
        ChronicleBook.Page page = world.chronicle().write(author.getUUID(), "Amman",
                HearthBlockEntity.key(level, nowhere), 80, 1, "Lost");
        HearthChronicle.Rated rated = HearthChronicle.rate(level.getServer(), page.id(), 5);
        helper.assertTrue(rated.status() == HearthChronicle.Status.OK && !rated.hearthFound() && rated.paid() == 0.0,
                "rated, nothing paid: " + rated);
        helper.assertTrue(HearthChronicle.rate(level.getServer(), page.id(), 5).status() == HearthChronicle.Status.ALREADY,
                "still only once");
        helper.succeed();
    }

    @GameTest(template = T, batch = "tales_rating_login")
    public static void anAuthorWhoWasAwayIsToldAtLoginOnce(GameTestHelper helper) {
        ExtrasTestGuard.watch(helper);
        ServerPlayer author = survivor(helper);
        ServerLevel level = helper.getLevel();
        HearthWorldData world = HearthWorldData.get(level.getServer());
        ChronicleBook.Page page = world.chronicle().write(author.getUUID(), "Amman", "x|0,0,0", 80, 1, "The stronghold");
        // Rated while they were offline (no announcement happened).
        world.chronicle().rate(page.id(), 3);
        helper.assertTrue(world.chronicle().unannouncedFor(author.getUUID()).size() == 1, "setup: a tale waits for its author");
        MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(author));
        helper.assertTrue(world.chronicle().unannouncedFor(author.getUUID()).isEmpty(), "told at login");
        helper.assertTrue(world.chronicle().page(page.id()).orElseThrow().told(), "and marked told");
        MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(author));
        helper.assertTrue(world.chronicle().page(page.id()).orElseThrow().told(), "once");
        helper.succeed();
    }

    @GameTest(template = T, batch = "tales_rating_file")
    public static void ratingsLeftInAFileAreAppliedOnceAndFiled(GameTestHelper helper) {
        ExtrasTestGuard.watch(helper);
        ServerPlayer author = survivor(helper);
        ServerLevel level = helper.getLevel();
        HearthBlockEntity hearth = lightHearth(helper, new BlockPos(1, 2, 1), 2);
        HearthWorldData world = HearthWorldData.get(level.getServer());
        ChronicleBook.Page one = world.chronicle().write(author.getUUID(), "Amman", hearth.key(), 40, 1, "One");
        ChronicleBook.Page two = world.chronicle().write(author.getUUID(), "Amman", hearth.key(), 40, 2, "Two");
        Path queue = HearthChronicle.queue(level.getServer());
        try {
            Files.createDirectories(queue.getParent());
            Files.writeString(queue, "{\"id\":" + one.id() + ",\"stars\":5}\nnot json\n{\"id\":" + two.id()
                    + ",\"stars\":0}\n{\"id\":" + one.id() + ",\"stars\":1}\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        int applied = HearthChronicle.drainQueue(level.getServer());
        helper.assertTrue(applied == 2, "two ratings applied: " + applied);
        helper.assertTrue(world.chronicle().page(one.id()).orElseThrow().stars() == 5, "the first rating stands");
        helper.assertTrue(world.chronicle().page(two.id()).orElseThrow().stars() == 0, "zero stars is a rating");
        helper.assertTrue(homecoming(hearth) == 10.0, "5 stars x 5% x 40 Tales: " + homecoming(hearth));
        helper.assertFalse(Files.exists(queue), "the file is taken");
        helper.assertTrue(HearthChronicle.drainQueue(level.getServer()) == 0, "nothing to apply twice");
        helper.setBlock(new BlockPos(1, 2, 1), Blocks.AIR);
        helper.succeed();
    }

    @GameTest(template = T, batch = "tales_rating_leftover")
    public static void aReadingFileLeftByADeadDrainIsReadBeforeTheNextQueue(GameTestHelper helper) {
        ExtrasTestGuard.watch(helper);
        ServerPlayer author = survivor(helper);
        ServerLevel level = helper.getLevel();
        HearthBlockEntity hearth = lightHearth(helper, new BlockPos(1, 2, 1), 2);
        HearthWorldData world = HearthWorldData.get(level.getServer());
        ChronicleBook.Page old = world.chronicle().write(author.getUUID(), "Amman", hearth.key(), 40, 1, "Old");
        ChronicleBook.Page fresh = world.chronicle().write(author.getUUID(), "Amman", hearth.key(), 40, 2, "Fresh");
        Path queue = HearthChronicle.queue(level.getServer());
        Path reading = queue.resolveSibling(HearthChronicle.READING);
        try {
            Files.createDirectories(queue.getParent());
            // The drain that died had moved this one and not finished it; a new rating arrived meanwhile.
            Files.writeString(reading, "{\"id\":" + old.id() + ",\"stars\":5}\n", StandardCharsets.UTF_8);
            Files.writeString(queue, "{\"id\":" + fresh.id() + ",\"stars\":3}\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        int applied = HearthChronicle.drainQueue(level.getServer());
        helper.assertTrue(applied == 2, "both ratings were applied: " + applied);
        helper.assertTrue(world.chronicle().page(old.id()).orElseThrow().stars() == 5, "the leftover was not overwritten");
        helper.assertTrue(world.chronicle().page(fresh.id()).orElseThrow().stars() == 3, "and the new one was read too");
        helper.assertTrue(homecoming(hearth) == 10.0 + 6.0, "5 stars and 3 stars of 40 Tales: " + homecoming(hearth));
        helper.assertFalse(Files.exists(reading) || Files.exists(queue), "both files are taken");
        // A line the dead drain had already applied reads as already rated and pays nothing twice.
        try {
            Files.writeString(reading, "{\"id\":" + old.id() + ",\"stars\":5}\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        helper.assertTrue(HearthChronicle.drainQueue(level.getServer()) == 0, "nothing applied twice");
        helper.assertTrue(homecoming(hearth) == 16.0, "nothing paid twice: " + homecoming(hearth));
        helper.setBlock(new BlockPos(1, 2, 1), Blocks.AIR);
        helper.succeed();
    }

    @GameTest(template = T, batch = "tales_rating_deferred")
    public static void aRatingWhoseHearthIsNotLoadedWaitsInTheFileUnrated(GameTestHelper helper) {
        ExtrasTestGuard.watch(helper);
        ServerPlayer author = survivor(helper);
        ServerLevel level = helper.getLevel();
        HearthWorldData world = HearthWorldData.get(level.getServer());
        // A Hearth at the far edge of the world: its chunk is not loaded (and there is no Hearth in it).
        BlockPos far = new BlockPos(2_000_000, 64, 2_000_000);
        helper.assertFalse(level.isLoaded(far), "setup: the chunk is unloaded");
        ChronicleBook.Page page = world.chronicle().write(author.getUUID(), "Amman", HearthBlockEntity.key(level, far),
                80, 1, "Far away");
        Path queue = HearthChronicle.queue(level.getServer());
        try {
            Files.createDirectories(queue.getParent());
            Files.writeString(queue, "{\"id\":" + page.id() + ",\"stars\":4}\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        // No chunk loads allowed: the rating is put back, the page is exactly as it was.
        helper.assertTrue(HearthChronicle.drainQueue(level.getServer(), 0) == 0, "nothing applied without a load");
        helper.assertFalse(world.chronicle().page(page.id()).orElseThrow().rated(), "the page is not marked rated");
        helper.assertTrue(Files.isRegularFile(queue), "the line is back in the file");
        helper.assertFalse(Files.exists(queue.resolveSibling(HearthChronicle.READING)), "and nothing is left half-read");
        try {
            helper.assertTrue(Files.readString(queue, StandardCharsets.UTF_8).contains("\"id\":" + page.id()),
                    "with its id");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        // With a load to spend it goes through: the Hearth is gone from there, so it rates once and pays nothing.
        helper.assertTrue(HearthChronicle.drainQueue(level.getServer(), 1) == 1, "applied once a load is allowed");
        ChronicleBook.Page rated = world.chronicle().page(page.id()).orElseThrow();
        helper.assertTrue(rated.rated() && rated.stars() == 4, "rated: " + rated);
        helper.assertFalse(Files.exists(queue), "the file is taken");
        helper.succeed();
    }

    // ---- The bell (abuse check 18) ----------------------------------------------------------------

    @GameTest(template = T, batch = "tales_bell")
    public static void theBellMakesOneCallByTheMomentAndAnotherBellDoesNothing(GameTestHelper helper) {
        ExtrasTestGuard.watch(helper);
        ServerPlayer ringer = survivor(helper);
        ServerLevel level = helper.getLevel();
        BlockPos hearthRel = new BlockPos(1, 2, 1);
        HearthBlockEntity hearth = lightHearth(helper, hearthRel, 5);
        HearthState state = hearth.state();
        BlockPos at = hearth.getBlockPos();
        BlockPos bell = at.offset(2, 3, 0);
        BlockPos otherBell = at.offset(6, 3, 0);
        helper.setBlock(hearthRel.offset(2, 3, 0), Blocks.BELL);
        helper.setBlock(hearthRel.offset(6, 3, 0), Blocks.BELL);
        atHome(ringer, hearth);

        // Any bell that is not the Hearth's own does nothing special.
        helper.assertTrue(HearthBell.ring(ringer, level, otherBell).call() == BellRules.Call.NONE,
                "a bell outside the Hearth's check box has no job");
        helper.assertTrue(state.lastComeHome() == HearthState.NEVER, "and calls nobody");

        // Nothing special going on: come home.
        BellRules.Ring home = HearthBell.ring(ringer, level, bell);
        helper.assertTrue(home.call() == BellRules.Call.COME_HOME && home.ready(), "come home: " + home);
        long now = level.getGameTime();
        helper.assertTrue(state.lastComeHome() == now, "the call is on its cooldown");
        PlayerHearth data = HearthAttachments.of(ringer);
        helper.assertTrue(data.heardBell(now) && data.bellPos() == at.asLong(), "the ringer heard it: Homeward's flag");
        helper.assertTrue(data.heardBell(now + 59 * 20L) && !data.heardBell(now + 61 * 20L),
                "the Homeward window is 60 s (abuse check 18)");
        // Only for the players who heard it (integration): someone who logs in after the ring has none.
        ServerPlayer late = survivor(helper);
        helper.assertFalse(HearthAttachments.of(late).heardBell(now), "a player who was not online heard nothing");
        BellRules.Ring again = HearthBell.ring(ringer, level, bell);
        helper.assertTrue(again.call() == BellRules.Call.COME_HOME && !again.ready(), "a second ring waits 5 minutes");
        helper.assertTrue(state.lastComeHome() == now, "and does not restart it");

        // Someone just ate: the dinner bell, and it does not fall through to come home.
        state.setLastComeHome(HearthState.NEVER);
        HearthFeast.eat(ringer, hearth);
        BellRules.Ring dinner = HearthBell.ring(ringer, level, bell);
        helper.assertTrue(dinner.call() == BellRules.Call.DINNER && dinner.ready(), "dinner beats come home: " + dinner);
        helper.assertTrue(state.feast().dinnerOpen(level.getGameTime()), "the two-minute window is open");
        helper.assertTrue(state.lastComeHome() == HearthState.NEVER, "no come-home was made");
        BellRules.Ring dinnerAgain = HearthBell.ring(ringer, level, bell);
        helper.assertTrue(dinnerAgain.call() == BellRules.Call.DINNER && !dinnerAgain.ready(), "one dinner bell per Feast");

        // A siege (or its countdown): the alarm beats both, and siege mobs glow.
        Zombie raider = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 2, 3));
        Zombie villager = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 2, 4));
        SpawnOrigin.tagSiege(raider);
        state.setSiegePhase(SiegeRules.Phase.COUNTDOWN);
        BellRules.Ring alarm = HearthBell.ring(ringer, level, bell);
        helper.assertTrue(alarm.call() == BellRules.Call.ALARM && alarm.ready(), "alarm beats dinner: " + alarm);
        helper.assertTrue(raider.hasEffect(MobEffects.GLOWING), "a siege mob in range glows");
        helper.assertFalse(villager.hasEffect(MobEffects.GLOWING), "an ordinary mob does not");
        helper.assertTrue(state.lastAlarm() == level.getGameTime(), "the alarm is on its 30 s cooldown");
        BellRules.Ring alarmAgain = HearthBell.ring(ringer, level, bell);
        helper.assertTrue(alarmAgain.call() == BellRules.Call.ALARM && !alarmAgain.ready(), "a second alarm waits");

        // Below stage V the bell has no job.
        state.setStage(4);
        state.setSiegePhase(SiegeRules.Phase.IDLE);
        helper.assertTrue(HearthBell.ring(ringer, level, bell).call() == BellRules.Call.NONE, "a Hearth below stage V has no bell job");
        raider.discard();
        villager.discard();
        helper.setBlock(hearthRel, Blocks.AIR);
        helper.setBlock(hearthRel.offset(2, 3, 0), Blocks.AIR);
        helper.setBlock(hearthRel.offset(6, 3, 0), Blocks.AIR);
        helper.succeed();
    }

    @GameTest(template = T, batch = "tales_bell_siege")
    public static void theAlarmAsksTheSiegeLaneForItsMobsItsPlayersAndItsPanel(GameTestHelper helper) {
        ExtrasTestGuard.watch(helper);
        ServerPlayer ringer = survivor(helper);
        ServerPlayer bystander = survivor(helper);
        ServerLevel level = helper.getLevel();
        BlockPos hearthRel = new BlockPos(1, 2, 1);
        HearthBlockEntity hearth = lightHearth(helper, hearthRel, 5);
        BlockPos bell = hearth.getBlockPos().offset(2, 3, 0);
        helper.setBlock(hearthRel.offset(2, 3, 0), Blocks.BELL);
        atHome(ringer, hearth);
        hearth.state().setSiegePhase(SiegeRules.Phase.ACTIVE);
        // A siege mob the siege lane tracks but that carries no tag in this test: the link names it.
        Zombie tracked = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 2, 3));
        Zombie stranger = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 2, 4));
        List<ServerPlayer> shown = new ArrayList<>();
        HearthBell.SiegeLink link = new HearthBell.SiegeLink() {
            @Override
            public List<net.minecraft.world.entity.Mob> mobs(ServerLevel lvl, HearthBlockEntity h) {
                return List.of(tracked);
            }

            @Override
            public java.util.Collection<ServerPlayer> concerned(ServerLevel lvl, HearthBlockEntity h) {
                return List.of(ringer);
            }

            @Override
            public void showPanel(ServerLevel lvl, HearthBlockEntity h, java.util.Collection<ServerPlayer> players) {
                shown.addAll(players);
            }
        };
        HearthBell.siegeLink = link;
        try {
            BellRules.Ring alarm = HearthBell.ring(ringer, level, bell);
            helper.assertTrue(alarm.call() == BellRules.Call.ALARM && alarm.ready(), "an active siege rings the alarm: " + alarm);
            helper.assertTrue(tracked.hasEffect(MobEffects.GLOWING), "the siege lane's mob glows");
            helper.assertFalse(stranger.hasEffect(MobEffects.GLOWING), "a mob it does not name does not");
            helper.assertTrue(shown.equals(List.of(ringer)), "the panel pops up for the players the siege names: " + shown);
            helper.assertTrue(HearthAttachments.of(bystander).heardBell(level.getGameTime()) == false,
                    "an alarm is no come-home call: nobody gets Homeward's flag");
        } finally {
            HearthBell.siegeLink = null;
        }
        tracked.discard();
        stranger.discard();
        helper.setBlock(hearthRel, Blocks.AIR);
        helper.setBlock(hearthRel.offset(2, 3, 0), Blocks.AIR);
        helper.succeed();
    }

    @GameTest(template = T, batch = "tales_bell_click")
    public static void aClickCountsOnlyWhenVanillaRangTheBell(GameTestHelper helper) {
        ExtrasTestGuard.watch(helper);
        ServerPlayer ringer = survivor(helper);
        ServerLevel level = helper.getLevel();
        BlockPos hearthRel = new BlockPos(1, 2, 1);
        HearthBlockEntity hearth = lightHearth(helper, hearthRel, 5);
        BlockPos bell = hearth.getBlockPos().offset(2, 3, 0);
        helper.setBlock(hearthRel.offset(2, 3, 0), Blocks.BELL);
        atHome(ringer, hearth);
        BlockState state = level.getBlockState(bell);
        // Vanilla only rings a bell hit on its body: a click on the top does nothing, so no call.
        BlockHitResult top = new BlockHitResult(Vec3.atCenterOf(bell).add(0.0, 0.5, 0.0), Direction.UP, bell, false);
        MinecraftForge.EVENT_BUS.post(click(ringer, bell));
        state.use(level, ringer, InteractionHand.MAIN_HAND, top);
        HearthBell.tick(level.getServer());
        helper.assertTrue(hearth.state().lastComeHome() == HearthState.NEVER, "a click vanilla did not ring makes no call");

        // A hit on the body of a floor bell, on the axis it faces, rings it: vanilla awards its statistic and the
        // Hearth calls everyone home.
        Direction facing = state.getValue(BellBlock.FACING);
        BlockHitResult side = new BlockHitResult(Vec3.atCenterOf(bell).relative(facing, 0.5), facing, bell, false);
        int before = ringer.getStats().getValue(Stats.CUSTOM.get(Stats.BELL_RING));
        MinecraftForge.EVENT_BUS.post(click(ringer, bell));
        InteractionResult result = state.use(level, ringer, InteractionHand.MAIN_HAND, side);
        helper.assertTrue(ringer.getStats().getValue(Stats.CUSTOM.get(Stats.BELL_RING)) > before,
                "setup: vanilla rang the bell (state " + state + ", block entity " + level.getBlockEntity(bell)
                        + ", result " + result + ", hit " + side.getLocation() + ")");
        HearthBell.tick(level.getServer());
        helper.assertTrue(hearth.state().lastComeHome() == level.getGameTime(), "a rung bell calls everyone home");
        // The click is spent: the next tick does not ring it again.
        hearth.state().setLastComeHome(HearthState.NEVER);
        HearthBell.tick(level.getServer());
        helper.assertTrue(hearth.state().lastComeHome() == HearthState.NEVER, "one ring, one call");
        helper.setBlock(hearthRel, Blocks.AIR);
        helper.setBlock(hearthRel.offset(2, 3, 0), Blocks.AIR);
        helper.succeed();
    }

    @GameTest(template = T, batch = "tales_bell_compass")
    public static void aCompassBoundToTheHeartLodestoneHumsWithTheBell(GameTestHelper helper) {
        ExtrasTestGuard.watch(helper);
        ServerPlayer player = survivor(helper);
        ServerLevel level = helper.getLevel();
        HearthBlockEntity hearth = lightHearth(helper, new BlockPos(1, 2, 1), 6);
        BlockPos at = hearth.getBlockPos();
        ItemStack compass = new ItemStack(Items.COMPASS);
        bind(compass, level, at.below());
        player.getInventory().setItem(0, compass);
        helper.assertTrue(HearthBell.hasCompassBoundTo(player, level, at), "a compass bound under the fire");
        bind(compass, level, at.offset(30, 0, 0));
        helper.assertFalse(HearthBell.hasCompassBoundTo(player, level, at), "a compass bound elsewhere does not hum");
        player.getInventory().setItem(0, ItemStack.EMPTY);
        helper.setBlock(new BlockPos(1, 2, 1), Blocks.AIR);
        helper.succeed();
    }

    // ---- The command's shape ----------------------------------------------------------------------

    @GameTest(template = T, batch = "tales_command_shape")
    public static void theTaleCommandMergesIntoTheProficiencyTree(GameTestHelper helper) {
        ExtrasTestGuard.watch(helper);
        CommandDispatcher<CommandSourceStack> dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
        helper.assertTrue(dispatcher.getRoot().getChild("proficiency") != null, "the proficiency command exists");
        helper.assertTrue(dispatcher.getRoot().getChild("proficiency").getChild("hearth") != null, "with a hearth branch");
        helper.assertTrue(dispatcher.getRoot().getChild("proficiency").getChild("hearth").getChild("tale") != null,
                "and the tale command in it");
        helper.assertTrue(dispatcher.getRoot().getChild("proficiency").getChild("list") != null,
                "the mod's own commands are still there");
        AtomicInteger roots = new AtomicInteger();
        dispatcher.getRoot().getChildren().forEach(node -> {
            if (node.getName().equals("proficiency")) {
                roots.incrementAndGet();
            }
        });
        helper.assertTrue(roots.get() == 1, "one proficiency root");
        helper.assertTrue(ChronicleCommand.node().getLiteral().equals("proficiency"), "the node is built on the shared root");
        helper.succeed();
    }

    // ---- The Feast's day is the world's day -------------------------------------------------------

    @GameTest(template = T, batch = "tales_feast_window")
    public static void theFeastWindowIsThirtySecondsAndTheDinnerBellMakesItTwoMinutes(GameTestHelper helper) {
        ExtrasTestGuard.watch(helper);
        ServerPlayer a = survivor(helper);
        ServerPlayer b = survivor(helper);
        HearthBlockEntity hearth = lightHearth(helper, new BlockPos(1, 2, 1), 5);
        atHome(a, hearth);
        atHome(b, hearth);
        ServerLevel level = helper.getLevel();
        FeastWindow feast = hearth.state().feast();
        long day = Math.floorDiv(level.getDayTime(), HearthData.TICKS_PER_DAY);
        long now = level.getGameTime();
        feast.onEat(a.getUUID(), now - 31 * 20L, day, Hearths.data());
        helper.assertTrue(feast.onEat(b.getUUID(), now, day, Hearths.data()).kind() == FeastWindow.Kind.NONE,
                "a meal 31 s before is outside the 30 s window");
        // The dinner bell opens the long window: the same two meals now make a Feast.
        hearth.state().feast().restore(HearthState.NEVER, HearthState.NEVER);
        feast.onEat(a.getUUID(), now - 31 * 20L, day, Hearths.data());
        feast.openDinner(now - 5 * 20L, Hearths.data());
        helper.assertTrue(feast.onEat(b.getUUID(), now, day, Hearths.data()).kind() == FeastWindow.Kind.STARTED,
                "under the dinner bell the late eater joins the long window");
        helper.setBlock(new BlockPos(1, 2, 1), Blocks.AIR);
        helper.succeed();
    }
}
