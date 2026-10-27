package dev.amman.proficiency.event;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.config.ProficiencyConfig;
import dev.amman.proficiency.perk.TalentService;
import dev.amman.proficiency.platform.bus.EventBusSubscriber;
import dev.amman.proficiency.platform.bus.SubscribeEvent;
import dev.amman.proficiency.platform.event.entity.player.PlayerEvent;
import dev.amman.proficiency.platform.event.entity.player.PlayerInteractEvent;
import dev.amman.proficiency.platform.event.tick.ServerTickEvent;
import dev.amman.proficiency.skill.ActiveService;
import dev.amman.proficiency.skill.CommerceMath;
import dev.amman.proficiency.skill.CommerceTracker;
import dev.amman.proficiency.skill.ProcService;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillService;
import dev.amman.proficiency.skill.SkillTools;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Commerce (the 38th skill): trading with villagers, wandering traders and any modded trader that
 * opens the vanilla merchant screen, and bartering with piglins. It owns the XP, the discount
 * (written into each offer's special price the way Hero of the Village does it), Good Deal, Haggle
 * and the tree. The numbers are in {@link CommerceMath}; the anti-farm memory in {@link CommerceTracker}.
 *
 * <p>Three mixins feed it (the vanilla paths have no event on every loader): the merchant menu opening
 * and closing ({@link #menuOpened}, {@link #menuClosed}), a result taken from the trade slot
 * ({@link #tradeDone}), and a piglin finishing a barter ({@link #barterDone}) or picking up an ingot
 * ({@link #thrownIngot}).
 *
 * <p>Anti-farm rules, all here or in the tracker:
 * <ul>
 * <li>A real player in survival; a machine's fake player gets nothing, not even the discount.</li>
 * <li>The same offer pays less each time within a day (1, 0.6, 0.35, 0.2, 0.1), more than 20 trades in
 * 5 minutes fade, so the paper-for-emerald loop is worth a tenth by its fifth lap.</li>
 * <li>A piglin pays only when a player handed it the ingot (a click, or an ingot the player threw): a
 * dispenser, a hopper or a piglin farm has nobody to credit. The player has to be within 32 blocks
 * when the barter ends, and more than 12 barters in 10 minutes fade.</li>
 * </ul>
 */
@EventBusSubscriber(modid = Proficiency.MOD_ID)
public final class CommerceEvents {

    public static final String SOURCE_TRADE = "proficiency.xplog.source.commerce_trade";
    public static final String SOURCE_BARTER = "proficiency.xplog.source.commerce_barter";

    /** The offers a menu cut, to give back. */
    private record Cut(MerchantOffer offer, int amount) {
    }

    private record Open(MerchantMenu menu, Merchant merchant, List<Cut> cuts) {
    }

    /** A villager someone traded with: Regulars restocks it again later. */
    private static final class Regular {
        final UUID player;
        final ResourceKey<Level> dimension;
        long lastTrade;
        long day = -1L;
        int extra;

        Regular(UUID player, ResourceKey<Level> dimension, long lastTrade) {
            this.player = player;
            this.dimension = dimension;
            this.lastTrade = lastTrade;
        }
    }

    private record Credit(UUID player, long tick) {
    }

    private static final Map<UUID, Open> OPEN = new ConcurrentHashMap<>();
    private static final Map<UUID, Regular> REGULARS = new ConcurrentHashMap<>();
    private static final Map<UUID, CommerceTracker> TRACKERS = new ConcurrentHashMap<>();
    private static final Map<UUID, Credit> CREDITS = new ConcurrentHashMap<>();

    private CommerceEvents() {
    }

    // ---- Who counts ----------------------------------------------------------------------------

    private static boolean counts(ServerPlayer player) {
        // Read from the game mode itself: the GameTest mock claims creative.
        return player.gameMode.isSurvival() && !SkillTools.isFakePlayer(player)
                && ProficiencyConfig.enabled(Skill.COMMERCE);
    }

    private static int rank(Player player, String special) {
        return TalentService.rank(player, Skill.COMMERCE, special);
    }

    private static long now(Entity entity) {
        return entity.level().getGameTime();
    }

    private static CommerceTracker tracker(ServerPlayer player) {
        return TRACKERS.computeIfAbsent(player.getUUID(), id -> new CommerceTracker());
    }

    // ---- The merchant screen: the discount -----------------------------------------------------

    /** The share the passive, Haggle and the tree take off this merchant's prices for this player. */
    public static double discountShare(ServerPlayer player, Merchant merchant) {
        double passive = CommerceMath.discount(ProficiencyAttachments.of(player).bonus(Skill.COMMERCE));
        boolean hero = rank(player, "heros_welcome") > 0 && player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE);
        boolean haggling = ActiveService.isFrenzied(player, Skill.COMMERCE);
        boolean guild = rank(player, "guild_master") > 0;
        int cured = merchant instanceof Villager villager && curedBy(villager, player) ? rank(player, "cure_bonus") : 0;
        return CommerceMath.totalDiscount(passive, hero, haggling, guild, cured);
    }

    /** True for a villager whose cure was done by this player (it holds a major positive memory of them). */
    static boolean curedBy(Villager villager, Player player) {
        return villager.getGossips().getReputation(player.getUUID(), type -> type == GossipType.MAJOR_POSITIVE) > 0;
    }

    /** Called when a trade menu has been made for a player: cuts the price of every offer. */
    public static void menuOpened(ServerPlayer player, Merchant merchant, MerchantMenu menu) {
        giveBack(OPEN.remove(player.getUUID()));
        if (!counts(player) || !ProficiencyConfig.commerceDiscount()) {
            return;
        }
        applyDiscount(player, merchant, menu);
    }

    private static void applyDiscount(ServerPlayer player, Merchant merchant, MerchantMenu menu) {
        double share = discountShare(player, merchant);
        if (share <= 0) {
            return;
        }
        MerchantOffers offers = menu.getOffers();
        List<Cut> cuts = new ArrayList<>();
        for (MerchantOffer offer : offers) {
            int cut = CommerceMath.priceCut(offer.getBaseCostA().getCount(), share,
                    Math.max(0, -offer.getSpecialPriceDiff()));
            if (cut > 0) {
                offer.addToSpecialPriceDiff(-cut);
                cuts.add(new Cut(offer, cut));
            }
        }
        OPEN.put(player.getUUID(), new Open(menu, merchant, cuts));
    }

    /** Called as a trade menu closes: gives the cut back before the villager resets its own prices. */
    public static void menuClosed(ServerPlayer player, MerchantMenu menu) {
        Open open = OPEN.get(player.getUUID());
        if (open != null && open.menu() == menu) {
            OPEN.remove(player.getUUID());
            giveBack(open);
        }
    }

    private static void giveBack(Open open) {
        if (open == null) {
            return;
        }
        for (Cut cut : open.cuts()) {
            int diff = cut.offer().getSpecialPriceDiff();
            int restored = diff + cut.amount();
            if (diff <= 0 && restored > 0) {
                // The villager already reset its own prices; there is nothing of ours left to take back.
                restored = 0;
            }
            cut.offer().setSpecialPriceDiff(restored);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            giveBack(OPEN.remove(player.getUUID()));
            CREDITS.values().removeIf(credit -> credit.player().equals(player.getUUID()));
            TRACKERS.remove(player.getUUID());
        }
    }

    /** Haggle: the ability started. A trade screen that is already open gets the deeper cut at once. */
    public static void haggle(ServerPlayer player, int ticks) {
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY(1.0), player.getZ(),
                    14, 0.5, 0.5, 0.5, 0.0);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.VILLAGER_TRADE,
                    SoundSource.PLAYERS, 0.9f, 1.1f);
        }
        Open open = OPEN.get(player.getUUID());
        if (open == null || player.containerMenu != open.menu() || !counts(player)
                || !ProficiencyConfig.commerceDiscount()) {
            return;
        }
        giveBack(OPEN.remove(player.getUUID()));
        applyDiscount(player, open.merchant(), open.menu());
        resend(player, open.menu());
        player.displayClientMessage(Component.translatable("proficiency.commerce.haggle",
                Math.round(discountShare(player, open.merchant()) * 100) + "%"), true);
    }

    /** Sends the open menu's offers again, so the client shows the prices and uses the server holds. */
    private static void resend(ServerPlayer player, MerchantMenu menu) {
        player.sendMerchantOffers(menu.containerId, menu.getOffers(), menu.getTraderLevel(), menu.getTraderXp(),
                menu.showProgressBar(), menu.canRestock());
    }

    // ---- A trade taken -------------------------------------------------------------------------

    /** Called right after the merchant was told a trade went through (the offer's uses already went up). */
    public static void tradeDone(ServerPlayer player, Merchant merchant, MerchantOffer offer) {
        if (!counts(player)) {
            return;
        }
        long now = now(player);
        ItemStack costA = offer.getBaseCostA();
        ItemStack costB = offer.getCostB();
        ItemStack result = offer.getResult();
        String idA = id(costA);
        String idB = id(costB);
        String idR = id(result);

        if (merchant instanceof Villager villager && rank(player, "regulars") > 0) {
            Regular regular = REGULARS.get(villager.getUUID());
            if (regular == null || !regular.player.equals(player.getUUID())) {
                regular = new Regular(player.getUUID(), villager.level().dimension(), now);
                REGULARS.put(villager.getUUID(), regular);
            }
            regular.lastTrade = now;
        }

        int emeralds = CommerceMath.emeraldsInvolved(idA, costA.getCount(), idB, costB.getCount(), idR,
                result.getCount());
        double xp = CommerceMath.tradeXp(emeralds, traderLevel(merchant)) * ProficiencyConfig.commerceTradeFactor();
        String signature = idA + "+" + idB + ">" + idR + stackKey(result);
        double factor = tracker(player).tradeFactor(signature, now);
        float paid = SkillService.grant(player, Skill.COMMERCE, xp * factor, SOURCE_TRADE);
        if (paid > 0f) {
            SkillService.grantFirstTimeOnly(player, Skill.COMMERCE, professionKind(merchant));
            ItemStack bought = CommerceMath.emeraldValue(idR, result.getCount()) > 0 ? costA : result;
            SkillService.grantFirstTimeOnly(player, Skill.COMMERCE, bought.getDescriptionId());
            goodDeal(player, merchant, offer);
        }

        double freeUse = CommerceMath.freeUseChance(rank(player, "free_use"));
        if (freeUse > 0 && offer.getUses() > 0 && player.getRandom().nextDouble() < freeUse) {
            refundUse(offer);
            if (player.containerMenu instanceof MerchantMenu menu) {
                resend(player, menu);
            }
        }
        double sweet = CommerceMath.sweetenerChance(rank(player, "sweetener"));
        if (sweet > 0 && player.getRandom().nextDouble() < sweet) {
            sweeten(player, merchant, result);
        }
    }

    private static String id(ItemStack stack) {
        return stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    /** What tells two stacks of one item apart (an enchanted book's enchantment): the component patch. */
    private static String stackKey(ItemStack stack) {
        return stack.isEmpty() ? "" : "#" + stack.getComponentsPatch();
    }

    private static int traderLevel(Merchant merchant) {
        if (merchant instanceof Villager villager) {
            return villager.getVillagerData().getLevel();
        }
        return merchant instanceof WanderingTrader ? CommerceMath.WANDERING_LEVEL : CommerceMath.OTHER_LEVEL;
    }

    private static String professionKind(Merchant merchant) {
        if (merchant instanceof Villager villager) {
            return CommerceMath.professionKind(
                    BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession()).getPath());
        }
        return merchant instanceof Entity entity ? entity.getType().getDescriptionId()
                : CommerceMath.professionKind(null);
    }

    /** Good Deal, the signature: part of the price goes back into your inventory. */
    private static void goodDeal(ServerPlayer player, Merchant merchant, MerchantOffer offer) {
        LivingEntity target = merchant instanceof LivingEntity living ? living : null;
        if (!ProcService.fire(player, Skill.COMMERCE, target)) {
            return;
        }
        double share = CommerceMath.refundShare(ProficiencyAttachments.of(player).procPower(Skill.COMMERCE));
        ItemStack first = giveBackPart(player, offer.getCostA(), share);
        giveBackPart(player, offer.getCostB(), share);
        if (!first.isEmpty()) {
            player.displayClientMessage(Component.translatable("proficiency.commerce.good_deal",
                    Component.literal(first.getCount() + " ").append(first.getHoverName())), true);
        }
    }

    private static ItemStack giveBackPart(ServerPlayer player, ItemStack paid, double share) {
        int back = CommerceMath.refund(paid.getCount(), share);
        if (paid.isEmpty() || back <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = paid.copy();
        stack.setCount(back);
        player.getInventory().placeItemBackInInventory(stack.copy());
        return stack;
    }

    /** Last Piece: takes one use back off the offer, through the public calls (there is no setter). */
    public static void refundUse(MerchantOffer offer) {
        int uses = offer.getUses();
        if (uses <= 0) {
            return;
        }
        offer.resetUses();
        for (int i = 1; i < uses; i++) {
            offer.increaseUses();
        }
    }

    /** Sweetener: a quarter more of a bulk buy, or a small gift with a single item. */
    private static void sweeten(ServerPlayer player, Merchant merchant, ItemStack result) {
        ItemStack gift;
        int extra = CommerceMath.bulkExtra(result.getCount());
        if (extra > 0) {
            gift = result.copy();
            gift.setCount(extra);
        } else {
            String giftId = CommerceMath.GIFTS.get(player.getRandom().nextInt(CommerceMath.GIFTS.size()));
            gift = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(giftId)));
        }
        if (gift.isEmpty()) {
            return;
        }
        player.getInventory().placeItemBackInInventory(gift.copy());
        Component name = merchant instanceof Entity entity ? entity.getDisplayName() : Component.literal("The trader");
        player.displayClientMessage(Component.translatable("proficiency.commerce.sweetener", name,
                Component.literal(gift.getCount() + " ").append(gift.getHoverName())), true);
        if (player.level() instanceof ServerLevel level && merchant instanceof Entity entity) {
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, entity.getX(), entity.getY(1.2), entity.getZ(),
                    6, 0.3, 0.3, 0.3, 0.0);
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.VILLAGER_YES,
                    SoundSource.NEUTRAL, 0.8f, 1.2f);
        }
    }

    // ---- Regulars ------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 40 != 11) {
            return;
        }
        if (!CREDITS.isEmpty()) {
            long clock = server.overworld().getGameTime();
            CREDITS.values().removeIf(credit -> clock - credit.tick() > CommerceMath.CREDIT_TICKS);
        }
        if (REGULARS.isEmpty()) {
            return;
        }
        Set<UUID> gone = new HashSet<>();
        for (Map.Entry<UUID, Regular> entry : REGULARS.entrySet()) {
            Regular regular = entry.getValue();
            ServerLevel level = server.getLevel(regular.dimension);
            if (level == null) {
                gone.add(entry.getKey());
                continue;
            }
            long now = level.getGameTime();
            if (now < regular.lastTrade || now - regular.lastTrade > CommerceMath.REGULAR_MEMORY_TICKS) {
                gone.add(entry.getKey());
                continue;
            }
            Entity entity = level.getEntity(entry.getKey());
            ServerPlayer player = server.getPlayerList().getPlayer(regular.player);
            if (!(entity instanceof Villager villager) || !villager.isAlive() || player == null
                    || villager.getTradingPlayer() != null) {
                continue;
            }
            long day = now / CommerceMath.DAY_TICKS;
            if (regular.day != day) {
                regular.day = day;
                regular.extra = 0;
            }
            if (!CommerceMath.regularDue(rank(player, "regulars"), regular.lastTrade, now, regular.extra)
                    || !needsRestock(villager)) {
                continue;
            }
            regular.extra++;
            for (MerchantOffer offer : villager.getOffers()) {
                offer.updateDemand();
                offer.resetUses();
            }
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, villager.getX(), villager.getY(2.0), villager.getZ(),
                    8, 0.3, 0.3, 0.3, 0.0);
            if (player.level() == level && player.distanceToSqr(villager) < 24.0 * 24.0) {
                player.displayClientMessage(Component.translatable("proficiency.commerce.restocked",
                        villager.getDisplayName()), true);
            }
        }
        gone.forEach(REGULARS::remove);
    }

    private static boolean needsRestock(Villager villager) {
        for (MerchantOffer offer : villager.getOffers()) {
            if (offer.needsRestock()) {
                return true;
            }
        }
        return false;
    }

    // ---- Bartering -----------------------------------------------------------------------------

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)
                || player.level().isClientSide() || !(event.getTarget() instanceof Piglin piglin)) {
            return;
        }
        creditClick(player, piglin, event.getItemStack());
    }

    /** A player right-clicked a piglin holding {@code stack}: credit them only if vanilla takes the ingot. */
    public static boolean creditClick(ServerPlayer player, Piglin piglin, ItemStack stack) {
        if (!stack.is(Items.GOLD_INGOT) || piglin.isBaby() || !counts(player) || !canTakeIngot(piglin)) {
            return false;
        }
        CREDITS.put(piglin.getUUID(), new Credit(player.getUUID(), now(player)));
        return true;
    }

    /**
     * Vanilla's canAdmire: an adult piglin takes a clicked barter item unless it is already admiring one or
     * admiring is switched off (hurt). Whatever it holds in its offhand it drops to take the ingot.
     */
    public static boolean canTakeIngot(Piglin piglin) {
        return !piglin.isBaby()
                && !piglin.getBrain().hasMemoryValue(MemoryModuleType.ADMIRING_ITEM)
                && !piglin.getBrain().hasMemoryValue(MemoryModuleType.ADMIRING_DISABLED);
    }

    /** A piglin is about to pick up an item: an ingot a player threw credits that player. */
    public static void thrownIngot(Piglin piglin, ItemEntity item) {
        if (item.getItem().is(Items.GOLD_INGOT) && item.getOwner() instanceof ServerPlayer player
                && !piglin.isBaby() && counts(player)) {
            CREDITS.put(piglin.getUUID(), new Credit(player.getUUID(), now(player)));
        }
    }

    /**
     * A piglin finished a barter and rolled {@code drops}. Pays the player who gave it the ingot (if
     * anyone did) and returns the extra stacks a second roll gave (Gold Rush), usually none.
     */
    public static List<ItemStack> barterDone(Piglin piglin, List<ItemStack> drops) {
        Credit credit = CREDITS.remove(piglin.getUUID());
        if (credit == null || !(piglin.level() instanceof ServerLevel level)) {
            return List.of();
        }
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(credit.player());
        long now = level.getGameTime();
        if (player == null || player.level() != level || !counts(player)
                || !CommerceMath.creditHolds(credit.tick(), now)
                || player.distanceToSqr(piglin) > CommerceMath.BARTER_RANGE * CommerceMath.BARTER_RANGE) {
            return List.of();
        }
        double factor = tracker(player).barterFactor(now);
        float paid = SkillService.grant(player, Skill.COMMERCE, ProficiencyConfig.commerceBarterXp() * factor,
                SOURCE_BARTER);
        if (paid > 0f) {
            firstDrops(player, drops);
        }
        double rush = CommerceMath.extraRollChance(rank(player, "gold_rush"));
        if (rush <= 0 || player.getRandom().nextDouble() >= rush) {
            return List.of();
        }
        List<ItemStack> more = rollBarter(piglin, level);
        if (more.isEmpty()) {
            return List.of();
        }
        if (paid > 0f) {
            firstDrops(player, more);
        }
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, piglin.getX(), piglin.getY(1.5), piglin.getZ(), 10, 0.4,
                0.4, 0.4, 0.0);
        level.playSound(null, piglin.getX(), piglin.getY(), piglin.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.NEUTRAL, 1.0f, 1.3f);
        player.displayClientMessage(Component.translatable("proficiency.commerce.gold_rush"), true);
        return more;
    }

    private static void firstDrops(ServerPlayer player, List<ItemStack> drops) {
        Set<String> seen = new HashSet<>();
        for (ItemStack drop : drops) {
            if (!drop.isEmpty() && seen.add(drop.getDescriptionId())) {
                SkillService.grantFirstTimeOnly(player, Skill.COMMERCE, drop.getDescriptionId());
            }
        }
    }

    /** One more roll of the piglin's barter loot table, as vanilla rolls it. */
    private static List<ItemStack> rollBarter(Piglin piglin, ServerLevel level) {
        var table = level.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.PIGLIN_BARTERING);
        return table.getRandomItems(new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, piglin)
                .create(LootContextParamSets.PIGLIN_BARTER));
    }

    // ---- For tests -----------------------------------------------------------------------------

    /** Forgets one player's memory (a test starts clean). */
    public static void forget(UUID player) {
        giveBack(OPEN.remove(player));
        TRACKERS.remove(player);
        CREDITS.values().removeIf(credit -> credit.player().equals(player));
        REGULARS.values().removeIf(regular -> regular.player.equals(player));
    }

    /** Test hook: marks a piglin as handed an ingot by this player now. */
    public static void creditForTest(Piglin piglin, ServerPlayer player) {
        CREDITS.put(piglin.getUUID(), new Credit(player.getUUID(), now(player)));
    }

    /** Test hook: when a villager's Regular entry says the last trade was (for Regulars). */
    public static void backdateRegular(UUID villager, long ticks) {
        Regular regular = REGULARS.get(villager);
        if (regular != null) {
            regular.lastTrade -= ticks;
        }
    }

    public static boolean isRegular(UUID villager) {
        return REGULARS.containsKey(villager);
    }
}
