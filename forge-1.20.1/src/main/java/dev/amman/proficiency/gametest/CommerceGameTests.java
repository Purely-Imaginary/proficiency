package dev.amman.proficiency.gametest;

import com.mojang.authlib.GameProfile;
import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.event.CommerceEvents;
import dev.amman.proficiency.perk.Talents;
import dev.amman.proficiency.skill.ActiveService;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.ProcService;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillService;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Commerce in a real world with mock players: a trade through the real merchant menu (so the mixins
 * run), the first-time kinds, the repeat guard, creative and fake players, the discount written and taken
 * back, Haggle and Good Deal, and every tree mechanic. Bartering runs a real piglin's brain. The
 * arithmetic is in CommerceMathTest and CommerceTrackerTest.
 *
 * <p>Each test is its own batch, and every player a test makes leaves the server at the end.
 */
@GameTestHolder(Proficiency.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CommerceGameTests {

    /** The mod's own empty template; Forge adds the "proficiency:" namespace itself. */
    private static final String T = "empty";
    private static final AtomicInteger MENU_IDS = new AtomicInteger(100);

    private CommerceGameTests() {
    }

    /** A named stand-in for a machine's fake player (the mod finds them by class name). */
    private static final class TradeMachineFakePlayer extends ServerPlayer {
        TradeMachineFakePlayer(net.minecraft.server.MinecraftServer server, net.minecraft.server.level.ServerLevel level,
                GameProfile profile) {
            super(server, level, profile);
        }

        @Override
        public boolean isSpectator() {
            return false;
        }
    }

    private static final class Cast {
        final GameTestHelper helper;
        final List<ServerPlayer> players = new ArrayList<>();

        Cast(GameTestHelper helper) {
            this.helper = helper;
            Vec3 origin = helper.absoluteVec(Vec3.ZERO);
            for (ServerPlayer other : List.copyOf(helper.getLevel().players())) {
                if (other.position().distanceTo(origin) < 12) {
                    other.setPos(other.getX(), other.getY() + 400, other.getZ());
                }
            }
            clear();
        }

        void clear() {
            Vec3 origin = helper.absoluteVec(Vec3.ZERO);
            // Only this test's own corner: batches run side by side, and a wider sweep would discard the
            // mobs of a siege test running next door.
            AABB box = new AABB(origin, origin).inflate(16);
            for (Mob mob : helper.getLevel().getEntitiesOfClass(Mob.class, box)) {
                mob.discard();
            }
            for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, box)) {
                item.discard();
            }
        }

        ServerPlayer player(double x, double z) {
            ServerPlayer player = MockPlayers.make(helper);
            player.setGameMode(GameType.SURVIVAL);
            player.setPos(helper.absoluteVec(new Vec3(x, 2, z)));
            player.setYRot(0f);
            CommerceEvents.forget(player.getUUID());
            players.add(player);
            return player;
        }

        /** Leaves the world tidy without finishing the test (for tests that finish through succeedWhen). */
        void tidy() {
            for (ServerPlayer player : players) {
                ProcService.forget(player.getUUID());
                CommerceEvents.forget(player.getUUID());
                player.server.getPlayerList().remove(player);
            }
            clear();
        }

        void done() {
            tidy();
            helper.succeed();
        }
    }

    private static float logged(ServerPlayer player, String source) {
        var log = SkillService.xpLog(player);
        float sum = 0f;
        if (log != null) {
            for (var entry : log.entries()) {
                if (entry.skill() == Skill.COMMERCE.ordinal() && source.equals(entry.source())) {
                    sum += entry.amount();
                }
            }
        }
        return sum;
    }

    private static PlayerSkills skills(ServerPlayer player) {
        return ProficiencyAttachments.of(player);
    }

    private static void rank(ServerPlayer player, String talent, int rank) {
        skills(player).setRank(Talents.get(Skill.COMMERCE, talent), rank);
    }

    private static MerchantOffer offer(net.minecraft.world.item.Item pay, int payCount, net.minecraft.world.item.Item buy,
            int buyCount, int maxUses) {
        return new MerchantOffer(new ItemStack(pay, payCount), new ItemStack(buy, buyCount), maxUses, 2, 0.0f);
    }

    /** A villager with exactly these offers (a price multiplier of 0 keeps demand out of the numbers). */
    private static Villager villager(GameTestHelper helper, VillagerProfession profession, int level, double x, double z,
            MerchantOffer... offers) {
        Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos((int) x, 2, (int) z));
        villager.setVillagerData(villager.getVillagerData().setProfession(profession).setLevel(level));
        villager.getOffers().clear();
        for (MerchantOffer offer : offers) {
            villager.getOffers().add(offer);
        }
        return villager;
    }

    private static int count(ServerPlayer player, net.minecraft.world.item.Item item) {
        return player.getInventory().countItem(item);
    }

    private static MerchantMenu open(ServerPlayer player, Villager villager) {
        villager.setTradingPlayer(player);
        MerchantMenu menu = new MerchantMenu(MENU_IDS.incrementAndGet(), player.getInventory(), villager);
        player.containerMenu = menu;
        return menu;
    }

    private static void close(ServerPlayer player, MerchantMenu menu) {
        menu.removed(player);
        player.containerMenu = player.inventoryMenu;
    }

    /** Takes offer {@code index} through the vanilla slots: select, fill the inputs, take the result. */
    private static boolean take(GameTestHelper helper, ServerPlayer player, MerchantMenu menu, int index) {
        menu.setSelectionHint(index);
        menu.tryMoveItems(index);
        Slot out = menu.getSlot(2);
        ItemStack result = out.getItem().copy();
        if (result.isEmpty()) {
            return false;
        }
        out.onTake(player, result);
        // The click would hand the result over; the test does it by hand.
        player.getInventory().placeItemBackInInventory(result);
        return true;
    }

    /** One whole trade: open, take, close. */
    private static boolean trade(GameTestHelper helper, ServerPlayer player, Villager villager, int index) {
        MerchantMenu menu = open(player, villager);
        boolean ok = take(helper, player, menu, index);
        close(player, menu);
        return ok;
    }

    private static void stock(ServerPlayer player, net.minecraft.world.item.Item item, int count) {
        player.getInventory().add(new ItemStack(item, count));
    }

    // ---- XP ------------------------------------------------------------------------------------

    /** A trade through the real menu pays, and the first profession and first item bought pay the first-time bonus. */
    @GameTest(template = T, batch = "commerce_trade", timeoutTicks = 100)
    public static void aTradeThroughTheMenuPaysAndCountsFirsts(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player(1.5, 1.5);
        Villager farmer = villager(helper, VillagerProfession.FARMER, 3, 3, 3,
                offer(Items.EMERALD, 8, Items.BREAD, 6, 12));
        stock(player, Items.EMERALD, 16);
        helper.assertTrue(trade(helper, player, farmer, 0), "the trade did not go through");
        helper.assertTrue(count(player, Items.BREAD) == 6, "the bread was not delivered: " + count(player, Items.BREAD));
        float paid = logged(player, CommerceEvents.SOURCE_TRADE);
        helper.assertTrue(paid > 2f && paid < 40f, "an 8 emerald trade paid " + paid);
        helper.assertTrue(skills(player).hasVisited("first:commerce:entity.minecraft.villager.farmer"),
                "no first-time bonus for the farmer profession");
        helper.assertTrue(skills(player).hasVisited("first:commerce:item.minecraft.bread"),
                "no first-time bonus for the bread");
        cast.done();
    }

    /** A higher trader level and more emeralds pay more. */
    @GameTest(template = T, batch = "commerce_level", timeoutTicks = 100)
    public static void aHigherLevelAndABiggerPriceHavePayMore(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer small = cast.player(1.5, 1.5);
        ServerPlayer big = cast.player(1.5, 4.5);
        Villager novice = villager(helper, VillagerProfession.LIBRARIAN, 1, 3, 3,
                offer(Items.EMERALD, 1, Items.BOOKSHELF, 1, 12));
        Villager master = villager(helper, VillagerProfession.CLERIC, 5, 5, 3,
                offer(Items.EMERALD, 20, Items.ENDER_PEARL, 1, 12));
        stock(small, Items.EMERALD, 4);
        stock(big, Items.EMERALD, 30);
        helper.assertTrue(trade(helper, small, novice, 0) && trade(helper, big, master, 0), "a trade did not go through");
        float low = logged(small, CommerceEvents.SOURCE_TRADE);
        float high = logged(big, CommerceEvents.SOURCE_TRADE);
        helper.assertTrue(low > 0f && high > low * 2f, "1 emerald at level 1 paid " + low + ", 20 at level 5 paid " + high);
        cast.done();
    }

    /** The same offer again and again pays less each time: the paper loop is not a farm. */
    @GameTest(template = T, batch = "commerce_repeat", timeoutTicks = 100)
    public static void theSameOfferPaysLessEachTime(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player(1.5, 1.5);
        Villager mason = villager(helper, VillagerProfession.CARTOGRAPHER, 2, 3, 3,
                offer(Items.EMERALD, 4, Items.COMPASS, 1, 99));
        stock(player, Items.EMERALD, 40);
        float last = Float.MAX_VALUE;
        float first = 0f;
        for (int i = 0; i < 5; i++) {
            float before = logged(player, CommerceEvents.SOURCE_TRADE);
            helper.assertTrue(trade(helper, player, mason, 0), "trade " + i + " did not go through");
            float gain = logged(player, CommerceEvents.SOURCE_TRADE) - before;
            helper.assertTrue(gain > 0f && gain < last, "trade " + i + " paid " + gain + " after " + last);
            last = gain;
            if (i == 0) {
                first = gain;
            }
        }
        helper.assertTrue(last < first * 0.2f, "the fifth lap paid " + last + " of " + first);
        cast.done();
    }

    /** Survival only. */
    @GameTest(template = T, batch = "commerce_creative", timeoutTicks = 100)
    public static void creativeTradingPaysNothing(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player(1.5, 1.5);
        player.setGameMode(GameType.CREATIVE);
        Villager farmer = villager(helper, VillagerProfession.FARMER, 1, 3, 3,
                offer(Items.EMERALD, 8, Items.BREAD, 6, 12));
        stock(player, Items.EMERALD, 16);
        helper.assertTrue(trade(helper, player, farmer, 0), "the trade did not go through");
        helper.assertTrue(logged(player, CommerceEvents.SOURCE_TRADE) == 0f, "a creative trade paid");
        cast.done();
    }

    /** A machine's fake player trades for nothing: no XP, no discount. */
    @GameTest(template = T, batch = "commerce_fake", timeoutTicks = 100)
    public static void aFakePlayerGetsNothing(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        var level = helper.getLevel();
        ServerPlayer fake = new TradeMachineFakePlayer(level.getServer(), level,
                new GameProfile(UUID.randomUUID(), "trade-machine"));
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, fake);
        fake.setGameMode(GameType.SURVIVAL);
        fake.setPos(helper.absoluteVec(new Vec3(1.5, 2, 1.5)));
        skills(fake).setLevel(Skill.COMMERCE, 100);
        Villager farmer = villager(helper, VillagerProfession.FARMER, 1, 3, 3,
                offer(Items.EMERALD, 32, Items.BREAD, 6, 12));
        stock(fake, Items.EMERALD, 40);
        MerchantMenu menu = open(fake, farmer);
        helper.assertTrue(menu.getOffers().get(0).getCostA().getCount() == 32,
                "a fake player got a discount: " + menu.getOffers().get(0).getCostA().getCount());
        helper.assertTrue(take(helper, fake, menu, 0), "the trade did not go through");
        close(fake, menu);
        helper.assertTrue(logged(fake, CommerceEvents.SOURCE_TRADE) == 0f, "a fake player was paid");
        level.getServer().getPlayerList().remove(fake);
        cast.done();
    }

    // ---- The discount ----------------------------------------------------------------------------

    /** Level 100 takes 10% off, the screen shows it, and closing the screen puts the price back. */
    @GameTest(template = T, batch = "commerce_discount", timeoutTicks = 100)
    public static void theDiscountIsWrittenAndTakenBack(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player(1.5, 1.5);
        skills(player).setLevel(Skill.COMMERCE, 100);
        Villager farmer = villager(helper, VillagerProfession.FARMER, 1, 3, 3,
                offer(Items.EMERALD, 32, Items.BREAD, 6, 12));
        MerchantOffer offer = farmer.getOffers().get(0);
        MerchantMenu menu = open(player, farmer);
        int cut = offer.getCostA().getCount();
        helper.assertTrue(cut == 29, "a level 100 player pays " + cut + " instead of 29");
        close(player, menu);
        helper.assertTrue(offer.getSpecialPriceDiff() == 0 && offer.getCostA().getCount() == 32,
                "the cut was not taken back: " + offer.getSpecialPriceDiff());
        // Level 0 has none.
        skills(player).setLevel(Skill.COMMERCE, 0);
        MerchantMenu plain = open(player, farmer);
        helper.assertTrue(offer.getCostA().getCount() == 32, "a level 0 player got a discount");
        close(player, plain);
        cast.done();
    }

    /** The cut is on the first price only, and a trade's payment is the cut price. */
    @GameTest(template = T, batch = "commerce_pays_cut", timeoutTicks = 100)
    public static void theTradePaysTheCutPrice(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player(1.5, 1.5);
        skills(player).setLevel(Skill.COMMERCE, 100);
        Villager farmer = villager(helper, VillagerProfession.FARMER, 1, 3, 3,
                offer(Items.EMERALD, 32, Items.BREAD, 6, 12));
        stock(player, Items.EMERALD, 40);
        helper.assertTrue(trade(helper, player, farmer, 0), "the trade did not go through");
        helper.assertTrue(count(player, Items.EMERALD) == 40 - 29, "paid " + (40 - count(player, Items.EMERALD)) + " not 29");
        cast.done();
    }

    /** A wandering trader (anything that is not a villager) gets the discount too, and it is taken back. */
    @GameTest(template = T, batch = "commerce_wanderer", timeoutTicks = 100)
    public static void aWanderingTraderGetsTheDiscountAndItIsTakenBack(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player(1.5, 1.5);
        skills(player).setLevel(Skill.COMMERCE, 100);
        var trader = helper.spawnWithNoFreeWill(EntityType.WANDERING_TRADER, new BlockPos(3, 2, 3));
        trader.getOffers().clear();
        trader.getOffers().add(offer(Items.EMERALD, 20, Items.NAME_TAG, 1, 5));
        MerchantOffer offer = trader.getOffers().get(0);
        trader.setTradingPlayer(player);
        MerchantMenu menu = new MerchantMenu(MENU_IDS.incrementAndGet(), player.getInventory(), trader);
        player.containerMenu = menu;
        helper.assertTrue(offer.getCostA().getCount() == 18, "a wandering trader charged " + offer.getCostA().getCount());
        stock(player, Items.EMERALD, 20);
        helper.assertTrue(take(helper, player, menu, 0), "the trade did not go through");
        close(player, menu);
        helper.assertTrue(offer.getSpecialPriceDiff() == 0, "a wandering trader kept the cut: " + offer.getSpecialPriceDiff());
        helper.assertTrue(logged(player, CommerceEvents.SOURCE_TRADE) > 0f, "a wandering trader's trade paid nothing");
        cast.done();
    }

    /** Hero's Welcome doubles the passive's cut while Hero of the Village is on you. */
    @GameTest(template = T, batch = "commerce_hero", timeoutTicks = 100)
    public static void herosWelcomeDoublesThePassive(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player(1.5, 1.5);
        skills(player).setLevel(Skill.COMMERCE, 100);
        Villager farmer = villager(helper, VillagerProfession.FARMER, 1, 3, 3,
                offer(Items.EMERALD, 32, Items.BREAD, 6, 12));
        MerchantOffer offer = farmer.getOffers().get(0);
        rank(player, "heros_welcome", 1);
        player.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 600, 0));
        MerchantMenu menu = open(player, farmer);
        int cost = offer.getCostA().getCount();
        close(player, menu);
        // A menu made by hand skips vanilla's own reputation and Hero cuts, so this is ours alone: the passive twice over is 20% of 32, 6.
        helper.assertTrue(cost == 32 - 6, "Hero's Welcome priced it at " + cost + ", expected 26");
        player.removeAllEffects();
        cast.done();
    }

    /** Old Debts: a villager you cured takes more off. */
    @GameTest(template = T, batch = "commerce_cured", timeoutTicks = 100)
    public static void oldDebtsStacksOnACuredVillager(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player(1.5, 1.5);
        skills(player).setLevel(Skill.COMMERCE, 100);
        rank(player, "old_debts", 3);
        Villager cured = villager(helper, VillagerProfession.FARMER, 1, 3, 3,
                offer(Items.EMERALD, 32, Items.BREAD, 6, 12));
        Villager other = villager(helper, VillagerProfession.FARMER, 1, 5, 3,
                offer(Items.EMERALD, 32, Items.BREAD, 6, 12));
        cured.getGossips().add(player.getUUID(), GossipType.MAJOR_POSITIVE, 20);
        MerchantMenu a = open(player, cured);
        int curedCost = cured.getOffers().get(0).getCostA().getCount();
        close(player, a);
        MerchantMenu b = open(player, other);
        int otherCost = other.getOffers().get(0).getCostA().getCount();
        close(player, b);
        // Ours alone again: 22% of 32 is 7, 10% is 3.
        helper.assertTrue(otherCost - curedCost >= 3, "a cured villager charged " + curedCost + " against " + otherCost);
        cast.done();
    }

    /** Haggle: a deeper cut while it runs (an open screen too), and Good Deal on every trade. */
    @GameTest(template = T, batch = "commerce_haggle", timeoutTicks = 100)
    public static void haggleCutsDeeperAndEveryTradeIsAGoodDeal(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player(1.5, 1.5);
        PlayerSkills skills = skills(player);
        skills.setLevel(Skill.COMMERCE, 100);
        skills.fillTree(Skill.COMMERCE);
        Villager farmer = villager(helper, VillagerProfession.FARMER, 1, 3, 3,
                offer(Items.EMERALD, 32, Items.BREAD, 6, 12));
        MerchantOffer offer = farmer.getOffers().get(0);
        stock(player, Items.EMERALD, 40);
        MerchantMenu menu = open(player, farmer);
        int before = offer.getCostA().getCount();
        helper.assertTrue(ActiveService.activate(player, Skill.COMMERCE) == null, "Haggle would not start");
        helper.assertTrue(!player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE), "Haggle gave Hero of the Village on top of its own discount");
        int during = offer.getCostA().getCount();
        helper.assertTrue(during < before - 4, "an open screen kept " + during + " (was " + before + ")");
        int purse = count(player, Items.EMERALD) + menu.getSlot(0).getItem().getCount();
        helper.assertTrue(take(helper, player, menu, 0), "the trade did not go through");
        close(player, menu);
        int spent = purse - count(player, Items.EMERALD);
        helper.assertTrue(spent > 0 && spent < during, "the trade cost " + spent + " of " + during + ": no Good Deal refund");
        helper.assertTrue(offer.getSpecialPriceDiff() == 0, "Haggle left a cut behind: " + offer.getSpecialPriceDiff());
        cast.done();
    }

    /** Hero of the Village already cut the price: Haggle on top stays under the 60% total cap, and our cut is returned (vanilla resets its own on close). */
    @GameTest(template = T, batch = "commerce_hero_cap", timeoutTicks = 100)
    public static void haggleOnTopOfAVanillaCutStaysUnderTheCap(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player(1.5, 1.5);
        PlayerSkills skills = skills(player);
        skills.setLevel(Skill.COMMERCE, 100);
        skills.fillTree(Skill.COMMERCE);
        Villager farmer = villager(helper, VillagerProfession.FARMER, 1, 3, 3,
                offer(Items.EMERALD, 24, Items.BREAD, 6, 12));
        MerchantOffer offer = farmer.getOffers().get(0);
        offer.setSpecialPriceDiff(-8);
        helper.assertTrue(ActiveService.activate(player, Skill.COMMERCE) == null, "Haggle would not start");
        MerchantMenu menu = open(player, farmer);
        int during = offer.getCostA().getCount();
        helper.assertTrue(during >= 10, "vanilla's cut plus ours priced 24 at " + during + " (cap is 10)");
        close(player, menu);
        helper.assertTrue(offer.getSpecialPriceDiff() >= -8, "our cut was left behind: " + offer.getSpecialPriceDiff());
        cast.done();
    }

    // ---- The tree ------------------------------------------------------------------------------

    /** Last Piece: a trade sometimes does not use a use up. */
    @GameTest(template = T, batch = "commerce_last_piece", timeoutTicks = 200)
    public static void lastPieceSometimesKeepsAUse(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player(1.5, 1.5);
        rank(player, "last_piece", 3);
        Villager farmer = villager(helper, VillagerProfession.FARMER, 1, 3, 3,
                offer(Items.EMERALD, 1, Items.BREAD, 1, 1000));
        MerchantOffer offer = farmer.getOffers().get(0);
        for (int i = 0; i < 50; i++) {
            offer.increaseUses();
            CommerceEvents.tradeDone(player, farmer, offer);
        }
        helper.assertTrue(offer.getUses() < 50 && offer.getUses() > 20,
                "50 trades at 30% left " + offer.getUses() + " uses (expected about 35)");
        // Without the rank, none are kept.
        rank(player, "last_piece", 0);
        int base = offer.getUses();
        for (int i = 0; i < 10; i++) {
            offer.increaseUses();
            CommerceEvents.tradeDone(player, farmer, offer);
        }
        helper.assertTrue(offer.getUses() == base + 10, "a rank-0 player kept uses: " + offer.getUses());
        cast.done();
    }

    /** Sweetener: the trader sometimes throws in more. */
    @GameTest(template = T, batch = "commerce_sweetener", timeoutTicks = 200)
    public static void sweetenerThrowsInSomethingExtra(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player(1.5, 1.5);
        rank(player, "sweetener", 3);
        Villager farmer = villager(helper, VillagerProfession.FARMER, 1, 3, 3,
                offer(Items.EMERALD, 1, Items.WHEAT, 16, 1000), offer(Items.EMERALD, 5, Items.DIAMOND_SWORD, 1, 1000));
        for (int i = 0; i < 80; i++) {
            CommerceEvents.tradeDone(player, farmer, farmer.getOffers().get(0));
        }
        int wheat = count(player, Items.WHEAT);
        helper.assertTrue(wheat >= 4 && wheat % 4 == 0, "a bulk buy was sweetened by " + wheat + " (a quarter of 16 is 4 each)");
        int food = count(player, Items.BREAD) + count(player, Items.APPLE) + count(player, Items.COOKIE)
                + count(player, Items.BAKED_POTATO) + count(player, Items.SWEET_BERRIES) + count(player, Items.CARROT);
        helper.assertTrue(food == 0, "a bulk trade gave a gift as well: " + food);
        for (int i = 0; i < 80; i++) {
            CommerceEvents.tradeDone(player, farmer, farmer.getOffers().get(1));
        }
        food = count(player, Items.BREAD) + count(player, Items.APPLE) + count(player, Items.COOKIE)
                + count(player, Items.BAKED_POTATO) + count(player, Items.SWEET_BERRIES) + count(player, Items.CARROT);
        helper.assertTrue(food > 0, "a single item never came with a gift in 80 trades");
        helper.assertTrue(count(player, Items.DIAMOND_SWORD) == 0, "the sweetener copied a sword");
        cast.done();
    }

    /** Good Deal through the signature: with the proc forced, part of the price comes back. */
    @GameTest(template = T, batch = "commerce_good_deal", timeoutTicks = 100)
    public static void aGoodDealGivesPartOfThePriceBack(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player(1.5, 1.5);
        skills(player).setLevel(Skill.COMMERCE, 100);
        rank(player, "market_stall", 5);
        Villager farmer = villager(helper, VillagerProfession.FARMER, 1, 3, 3,
                offer(Items.EMERALD, 10, Items.BREAD, 6, 12));
        stock(player, Items.EMERALD, 20);
        ProcService.forceNext(player, Skill.COMMERCE);
        helper.assertTrue(trade(helper, player, farmer, 0), "the trade did not go through");
        int spent = 20 - count(player, Items.EMERALD);
        helper.assertTrue(spent < 10 && spent >= 4, "the trade cost " + spent + " of 10 (expected about half)");
        cast.done();
    }

    /** Regulars: a villager you traded with restocks again, sooner than the day cycle. */
    @GameTest(template = T, batch = "commerce_regulars", timeoutTicks = 200)
    public static void regularsRestockAgainSoonerThanTheDayCycle(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player(1.5, 1.5);
        rank(player, "regulars", 3);
        Villager farmer = villager(helper, VillagerProfession.FARMER, 1, 3, 3,
                offer(Items.EMERALD, 1, Items.BREAD, 1, 12));
        MerchantOffer offer = farmer.getOffers().get(0);
        for (int i = 0; i < 5; i++) {
            offer.increaseUses();
        }
        CommerceEvents.tradeDone(player, farmer, offer);
        helper.assertTrue(CommerceEvents.isRegular(farmer.getUUID()), "the villager was not remembered");
        helper.assertTrue(offer.getUses() == 5, "it restocked at once");
        CommerceEvents.backdateRegular(farmer.getUUID(), 1300L);
        helper.succeedWhen(() -> {
            helper.assertTrue(offer.getUses() == 0, "the villager did not restock for a regular (uses " + offer.getUses() + ")");
            cast.tidy();
        });
    }

    /** Without the rank nothing restocks. */
    @GameTest(template = T, batch = "commerce_not_regular", timeoutTicks = 200)
    public static void noRankNoRestock(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player(1.5, 1.5);
        Villager farmer = villager(helper, VillagerProfession.FARMER, 1, 3, 3,
                offer(Items.EMERALD, 1, Items.BREAD, 1, 12));
        MerchantOffer offer = farmer.getOffers().get(0);
        offer.increaseUses();
        CommerceEvents.tradeDone(player, farmer, offer);
        helper.assertTrue(!CommerceEvents.isRegular(farmer.getUUID()), "a villager was remembered without the rank");
        cast.done();
    }

    // ---- Bartering -----------------------------------------------------------------------------

    private static Piglin piglin(GameTestHelper helper, double x, double z) {
        // Not spawnWithNoFreeWill: that strips the brain, and the brain is what finishes a barter.
        Piglin piglin = helper.spawn(EntityType.PIGLIN, new BlockPos((int) x, 2, (int) z));
        piglin.setImmuneToZombification(true);
        piglin.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.GOLD_INGOT));
        return piglin;
    }

    /** A piglin a player handed an ingot to pays that player when its brain finishes the barter. */
    @GameTest(template = T, batch = "commerce_barter", timeoutTicks = 400)
    public static void aBarterPaysThePlayerWhoGaveTheIngot(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player(1.5, 1.5);
        Piglin piglin = piglin(helper, 3, 3);
        CommerceEvents.creditForTest(piglin, player);
        helper.succeedWhen(() -> {
            helper.assertTrue(logged(player, CommerceEvents.SOURCE_BARTER) > 0f, "the barter paid nothing yet");
            helper.assertTrue(skills(player).level(Skill.COMMERCE) >= 0, "no skill");
            cast.tidy();
        });
    }

    /** A click with an ingot credits a piglin vanilla takes it with (even one holding something else), not an admiring one. */
    @GameTest(template = T, batch = "commerce_click_credit", timeoutTicks = 100)
    public static void onlyAPiglinThatTakesTheIngotCanBeCredited(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player(1.5, 1.5);
        ItemStack ingot = new ItemStack(Items.GOLD_INGOT);
        Piglin holding = piglin(helper, 3, 3);
        holding.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
        helper.assertTrue(CommerceEvents.creditClick(player, holding, ingot), "a piglin holding a shield refuses the ingot");
        Piglin admiring = piglin(helper, 5, 5);
        admiring.getBrain().setMemory(MemoryModuleType.ADMIRING_ITEM, true);
        helper.assertTrue(!CommerceEvents.creditClick(player, admiring, ingot), "an admiring piglin was credited");
        Piglin hurt = piglin(helper, 7, 3);
        hurt.getBrain().setMemory(MemoryModuleType.ADMIRING_DISABLED, true);
        helper.assertTrue(!CommerceEvents.creditClick(player, hurt, ingot), "a hurt piglin was credited");
        Piglin free = piglin(helper, 7, 7);
        helper.assertTrue(!CommerceEvents.creditClick(player, free, new ItemStack(Items.GOLD_NUGGET)), "a nugget was credited");
        holding.discard();
        admiring.discard();
        hurt.discard();
        free.discard();
        cast.done();
    }

    /** A piglin nobody handed the ingot to (a dispenser, a hopper farm) pays nothing. */
    @GameTest(template = T, batch = "commerce_machine_barter", timeoutTicks = 400)
    public static void anAutomatedBarterPaysNothing(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player(1.5, 1.5);
        Piglin piglin = piglin(helper, 3, 3);
        AtomicInteger waited = new AtomicInteger();
        helper.onEachTick(() -> {
            if (waited.incrementAndGet() == 120) {
                helper.assertTrue(piglin.getOffhandItem().isEmpty(), "the piglin never finished bartering");
                helper.assertTrue(logged(player, CommerceEvents.SOURCE_BARTER) == 0f, "a piglin with no giver paid");
                cast.done();
            }
        });
    }

    /** Gold Rush: a second roll of the loot now and then. */
    @GameTest(template = T, batch = "commerce_gold_rush", timeoutTicks = 200)
    public static void goldRushRollsTheLootAgain(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player(1.5, 1.5);
        rank(player, "gold_rush", 3);
        Piglin piglin = piglin(helper, 3, 3);
        piglin.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        int rolled = 0;
        for (int i = 0; i < 60; i++) {
            CommerceEvents.creditForTest(piglin, player);
            if (!CommerceEvents.barterDone(piglin, List.of(new ItemStack(Items.OBSIDIAN))).isEmpty()) {
                rolled++;
            }
        }
        helper.assertTrue(rolled >= 5 && rolled <= 35, "Gold Rush rolled again " + rolled + " times in 60 (expected about 18)");
        rank(player, "gold_rush", 0);
        for (int i = 0; i < 20; i++) {
            CommerceEvents.creditForTest(piglin, player);
            helper.assertTrue(CommerceEvents.barterDone(piglin, List.of(new ItemStack(Items.OBSIDIAN))).isEmpty(),
                    "a rank-0 player got a second roll");
        }
        cast.done();
    }

    /** An ingot a player threw is credited to the thrower; one nobody threw is not. */
    @GameTest(template = T, batch = "commerce_thrown", timeoutTicks = 100)
    public static void aThrownIngotCreditsTheThrower(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player(1.5, 1.5);
        Piglin piglin = piglin(helper, 3, 3);
        piglin.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        ItemEntity thrown = new ItemEntity(helper.getLevel(), piglin.getX(), piglin.getY(), piglin.getZ(),
                new ItemStack(Items.GOLD_INGOT));
        thrown.setThrower(player.getUUID());
        CommerceEvents.thrownIngot(piglin, thrown);
        float paid = CommerceEvents.barterDone(piglin, List.of(new ItemStack(Items.OBSIDIAN))).isEmpty()
                ? logged(player, CommerceEvents.SOURCE_BARTER) : -1f;
        helper.assertTrue(paid > 0f, "a thrown ingot did not pay its thrower (" + paid + ")");
        float before = logged(player, CommerceEvents.SOURCE_BARTER);
        ItemEntity dropped = new ItemEntity(helper.getLevel(), piglin.getX(), piglin.getY(), piglin.getZ(),
                new ItemStack(Items.GOLD_INGOT));
        CommerceEvents.thrownIngot(piglin, dropped);
        CommerceEvents.barterDone(piglin, List.of(new ItemStack(Items.OBSIDIAN)));
        helper.assertTrue(logged(player, CommerceEvents.SOURCE_BARTER) == before, "an ingot nobody threw paid");
        cast.done();
    }
}
