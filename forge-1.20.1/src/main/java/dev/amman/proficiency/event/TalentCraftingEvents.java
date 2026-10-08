package dev.amman.proficiency.event;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.perk.TalentService;
import dev.amman.proficiency.skill.Skill;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.random.WeightedRandom;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.event.GrindstoneEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.AnvilRepairEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Smithing, Cooking and Alchemy talents that do something rather than add a number.
 * {@link CraftingEvents} trains the skills; it calls in here where a talent changes what one of
 * its handlers hands out, and the rest listen for themselves.
 */
@Mod.EventBusSubscriber(modid = Proficiency.MOD_ID)
public final class TalentCraftingEvents {

    /** What Iron Stomach undoes. Food can only make you sick with these three. */
    private static final List<MobEffect> SICKNESS =
            List.of(MobEffects.HUNGER, MobEffects.POISON, MobEffects.CONFUSION);

    private static final int GOURMET_VARIETY = 5;
    private static final int GOURMET_TICKS = 20 * 60;

    /** Inventory stacks by identity when the anvil last computed, to find a shift-clicked result. */
    private static final Map<UUID, Set<ItemStack>> ANVIL_INVENTORY = new ConcurrentHashMap<>();
    private static final Map<UUID, Sickness> SICK_BEFORE = new ConcurrentHashMap<>();
    private static final Map<UUID, Cure> CURES = new ConcurrentHashMap<>();
    private static final Map<UUID, FoodStreak<Item>> MENUS = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<MobEffect, Applied>> ANTIDOTE = new ConcurrentHashMap<>();

    /**
     * Set while this class re-adds an effect it shortened or restored. The re-add posts
     * {@link MobEffectEvent.Added} like any other, and Antidote must not shorten its own work.
     * Server thread only.
     */
    private static boolean reapplying;

    private TalentCraftingEvents() {
    }

    private record Sickness(Map<MobEffect, MobEffectInstance> effects, long tick) {
    }

    private record Cure(Sickness before, long dueTick) {
    }

    private record Applied(int amplifier, int duration, long tick) {
    }

    // ---- Smithing ----------------------------------------------------------------------------

    /**
     * Whetstone and Temper, on a piece of gear just off the bench. The event's stack is the one on
     * the cursor for a click, but only a copy for a shift-click, so the real one is found first.
     */
    static void finishCraftedGear(Player player, ItemStack result) {
        int tough = TalentService.rank(player, Skill.SMITHING, "tough_tools");
        int temper = TalentService.rank(player, Skill.SMITHING, "temper");
        if (tough <= 0 && temper <= 0) {
            return;
        }
        List<Enchantment> gifts = new ArrayList<>();
        if (tough > 0 && player.getRandom().nextDouble() < tough * 0.15) {
            gifts.add(Enchantments.UNBREAKING);
        }
        if (temper > 0 && player.getRandom().nextDouble() < temper * 0.10) {
            gifts.add(Enchantments.SHARPNESS);
        }
        // Sharpness decides what a weapon is: its own item test covers swords, axes and any modded
        // blade that extends them, which a hard-coded list would not.
        gifts.removeIf(gift -> !gift.canEnchant(result));
        if (gifts.isEmpty()) {
            return;
        }
        ItemStack live = craftedStack(player, result);
        for (Enchantment gift : gifts) {
            result.enchant(gift, 1);
            if (live != null && live != result) {
                live.enchant(gift, 1);
            }
        }
    }

    @Nullable
    private static ItemStack craftedStack(Player player, ItemStack result) {
        if (player.containerMenu.getCarried() == result) {
            return result;
        }
        for (ItemStack stack : player.getInventory().items) {
            if (stack == result) {
                return result;
            }
        }
        // A shift-click moved the real stack into the inventory. Any identical fresh piece is
        // indistinguishable from it, so the first match is as good as the right one.
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && ItemStack.matches(stack, result)) {
                return stack;
            }
        }
        return null;
    }

    /** Legendary: a Masterwork piece in diamond or netherite gets one enchantment it lacks. */
    static void legendary(Player player, ItemStack piece) {
        if (TalentService.rank(player, Skill.SMITHING, "legendary") <= 0) {
            return;
        }
        // The repair material is how the game itself tells a diamond pick from an iron one, and
        // it holds for modded gear too.
        Item item = piece.getItem();
        if (!item.isValidRepairItem(piece, new ItemStack(Items.DIAMOND))
                && !item.isValidRepairItem(piece, new ItemStack(Items.NETHERITE_INGOT))) {
            return;
        }
        Set<Enchantment> existing = EnchantmentHelper.getEnchantments(piece).keySet();
        // Level 30 is the enchanting table's top slot: the levels a player would expect to see.
        // No treasure: 1.21's #in_enchanting_table is 1.20.1's table pool.
        List<EnchantmentInstance> options = new ArrayList<>(EnchantmentHelper.getAvailableEnchantmentResults(
                30, piece, false));
        options.removeIf(option -> !EnchantmentHelper.isEnchantmentCompatible(existing, option.enchantment));
        WeightedRandom.getRandomItem(player.getRandom(), options)
                .ifPresent(option -> piece.enchant(option.enchantment, option.level));
    }

    /**
     * The anvil result is found the same way as a crafted one, except that a shift-click hands the
     * repair event an emptied stack rather than a copy. So the inventory is remembered by identity
     * each time the anvil works out a result, and the stack that is new afterwards is the result.
     */
    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        if (TalentService.rank(player, Skill.SMITHING, "reforge") <= 0
                && TalentService.rank(player, Skill.SMITHING, "no_prior_work") <= 0) {
            return;
        }
        Set<ItemStack> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        seen.addAll(player.getInventory().items);
        ANVIL_INVENTORY.put(player.getUUID(), seen);
    }

    @SubscribeEvent
    public static void onAnvilRepair(AnvilRepairEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        int reforge = TalentService.rank(player, Skill.SMITHING, "reforge");
        boolean keepCost = TalentService.rank(player, Skill.SMITHING, "no_prior_work") > 0;
        if (reforge <= 0 && !keepCost) {
            return;
        }
        ItemStack left = event.getLeft();
        ItemStack output = anvilOutput(player, event.getOutput(), left);
        if (output == null) {
            return;
        }
        boolean repaired = left.isDamageableItem() && output.isDamageableItem()
                && output.getDamageValue() < left.getDamageValue();
        if (repaired && reforge > 0 && player.getRandom().nextDouble() < reforge * 0.10) {
            output.setDamageValue(0);
        }
        if (keepCost) {
            // Grand Artisan: whatever the anvil did, the next job costs what this one did.
            output.setRepairCost(left.getBaseRepairCost());
        }
    }

    @Nullable
    private static ItemStack anvilOutput(ServerPlayer player, ItemStack output, ItemStack left) {
        if (!output.isEmpty()) {
            return output;
        }
        Set<ItemStack> seen = ANVIL_INVENTORY.get(player.getUUID());
        if (seen == null) {
            return null;
        }
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && !seen.contains(stack) && stack.is(left.getItem())) {
                return stack;
            }
        }
        return null;
    }

    @SubscribeEvent
    public static void onContainerClose(PlayerContainerEvent.Close event) {
        if (event.getContainer() instanceof AnvilMenu) {
            ANVIL_INVENTORY.remove(event.getEntity().getUUID());
        }
    }

    /**
     * Disenchanter. The take event does not say who is at the grindstone, so the player is the one
     * whose open grindstone holds these exact input stacks. One event per grind, few players.
     */
    @SubscribeEvent
    public static void onGrindstoneTake(GrindstoneEvent.OnTakeItem event) {
        if (event.getXp() <= 0) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.containerMenu instanceof GrindstoneMenu menu
                    && menu.getSlot(0).getItem() == event.getTopItem()
                    && menu.getSlot(1).getItem() == event.getBottomItem()) {
                if (TalentService.rank(player, Skill.SMITHING, "disenchanter") > 0) {
                    event.setXp(event.getXp() * 2);
                }
                return;
            }
        }
    }

    // ---- Cooking -----------------------------------------------------------------------------

    /**
     * Iron Stomach has to know what was on you before the food, or it would strip a real potion's
     * Poison along with the pufferfish's. The last tick of eating is the moment before.
     */
    @SubscribeEvent
    public static void onUseStart(LivingEntityUseItemEvent.Start event) {
        rememberSickness(event.getEntity(), event.getItem());
    }

    @SubscribeEvent
    public static void onUseTick(LivingEntityUseItemEvent.Tick event) {
        rememberSickness(event.getEntity(), event.getItem());
    }

    private static void rememberSickness(LivingEntity entity, ItemStack item) {
        if (!(entity instanceof ServerPlayer player) || item.getFoodProperties(player) == null
                || TalentService.rank(player, Skill.COOKING, "iron_stomach") <= 0) {
            return;
        }
        Map<MobEffect, MobEffectInstance> effects = new HashMap<>();
        for (MobEffect sickness : SICKNESS) {
            MobEffectInstance active = player.getEffect(sickness);
            if (active != null) {
                effects.put(sickness, new MobEffectInstance(active));
            }
        }
        SICK_BEFORE.put(player.getUUID(), new Sickness(effects, player.level().getGameTime()));
    }

    /** Vanilla has already fed the player when this runs, so the extras land on top. */
    @SubscribeEvent
    public static void onFinishUsing(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack item = event.getItem();
        FoodProperties food = item.getFoodProperties(player);
        if (food == null) {
            return;
        }
        FoodData data = player.getFoodData();

        int hearty = TalentService.rank(player, Skill.COOKING, "hearty");
        if (hearty > 0) {
            data.eat(hearty, 0.0f);
        }
        // After Hearty, because saturation is capped at the hunger bar and Hearty raises it.
        if (TalentService.rank(player, Skill.COOKING, "double_saturation") > 0) {
            data.setSaturation(Math.min(data.getFoodLevel(), data.getSaturationLevel() + (food.getNutrition() * food.getSaturationModifier() * 2.0f)));
        }

        int comfort = TalentService.rank(player, Skill.COOKING, "comfort");
        if (comfort > 0) {
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40 * comfort, 0));
        }

        int shared = TalentService.rank(player, Skill.COOKING, "shared_meal");
        if (shared > 0) {
            for (ServerPlayer other : playersNear(player, 8.0)) {
                other.getFoodData().eat(shared, 0.0f);
            }
        }

        Sickness before = SICK_BEFORE.remove(player.getUUID());
        if (before != null && TalentService.rank(player, Skill.COOKING, "iron_stomach") > 0) {
            // A tick later, like Potency: anything a mod applies after this event is caught too.
            CURES.put(player.getUUID(), new Cure(before, player.level().getGameTime() + 1));
        }

        if (TalentService.rank(player, Skill.COOKING, "gourmet") > 0) {
            FoodStreak<Item> streak = MENUS.computeIfAbsent(player.getUUID(),
                    id -> new FoodStreak<>(GOURMET_VARIETY));
            if (streak.eat(item.getItem())) {
                player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, GOURMET_TICKS, 1));
                player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, GOURMET_TICKS, 0));
            }
        }
    }

    /**
     * Puts the three sicknesses back the way they were before the meal: gone if they were absent,
     * the old instance if the food made one worse.
     */
    private static void cure(ServerPlayer player, Cure cure) {
        long elapsed = player.level().getGameTime() - cure.before().tick();
        for (MobEffect sickness : SICKNESS) {
            MobEffectInstance now = player.getEffect(sickness);
            if (now == null) {
                continue;
            }
            MobEffectInstance was = cure.before().effects().get(sickness);
            if (was == null) {
                player.removeEffect(sickness);
                continue;
            }
            if (was.isInfiniteDuration()) {
                continue;
            }
            int remaining = was.getDuration() - (int) elapsed;
            if (now.getAmplifier() > was.getAmplifier() || now.getDuration() > remaining + 1) {
                player.removeEffect(sickness);
                if (remaining > 0) {
                    quietlyAdd(player, new MobEffectInstance(sickness, remaining, was.getAmplifier(),
                            was.isAmbient(), was.isVisible(), was.showIcon()));
                }
            }
        }
    }

    // ---- Alchemy -----------------------------------------------------------------------------

    /**
     * Catalyst's roll for a potion just drunk. An instant effect is already spent, so it is applied
     * once more now, which for Healing and Harming is exactly one level stronger (4 &lt;&lt; amplifier).
     * Returns whether the lasting effects should be raised a level, which happens a tick later.
     */
    static boolean catalyst(ServerPlayer player, List<MobEffectInstance> effects) {
        int rank = TalentService.rank(player, Skill.ALCHEMY, "catalyst");
        if (rank <= 0 || player.getRandom().nextDouble() >= rank * 0.10) {
            return false;
        }
        for (MobEffectInstance effect : effects) {
            if (effect.getEffect().isInstantenous()) {
                effect.getEffect().applyInstantenousEffect(
                        player, player, player, effect.getAmplifier(), 1.0);
            }
        }
        return true;
    }

    /** Catalyst's second half: the potion's lasting effects, one amplifier up. */
    static void strengthen(Player player, List<MobEffectInstance> effects) {
        for (MobEffectInstance effect : effects) {
            if (effect.getEffect().isInstantenous()) {
                continue;
            }
            MobEffectInstance active = player.getEffect(effect.getEffect());
            // Only if what is running is this potion's level: never raise a stronger one already on.
            if (active == null || active.getAmplifier() != effect.getAmplifier()) {
                continue;
            }
            player.addEffect(new MobEffectInstance(effect.getEffect(), active.getDuration(),
                    active.getAmplifier() + 1, active.isAmbient(), active.isVisible(), active.showIcon()));
        }
    }

    /** Panacea: the worst harmful effect on you that this potion did not just give you. */
    static void panacea(ServerPlayer player, List<MobEffectInstance> drunk) {
        if (TalentService.rank(player, Skill.ALCHEMY, "panacea") <= 0) {
            return;
        }
        Set<MobEffect> own = new HashSet<>();
        drunk.forEach(effect -> own.add(effect.getEffect()));
        player.getActiveEffects().stream()
                .filter(active -> active.getEffect().getCategory() == MobEffectCategory.HARMFUL)
                .filter(active -> !own.contains(active.getEffect()))
                .max(Comparator.comparingInt(MobEffectInstance::getAmplifier)
                        .thenComparingInt(active -> active.isInfiniteDuration()
                                ? Integer.MAX_VALUE : active.getDuration()))
                .map(MobEffectInstance::getEffect)
                .ifPresent(player::removeEffect);
    }

    /** Grand Alchemist: everyone within 5 blocks gets the potion as brewed. */
    static void shareBrew(ServerPlayer player, List<MobEffectInstance> effects) {
        if (TalentService.rank(player, Skill.ALCHEMY, "shared_brew") <= 0) {
            return;
        }
        for (ServerPlayer other : playersNear(player, 5.0)) {
            for (MobEffectInstance effect : effects) {
                if (effect.getEffect().isInstantenous()) {
                    effect.getEffect().applyInstantenousEffect(
                            player, player, other, effect.getAmplifier(), 1.0);
                } else {
                    other.addEffect(new MobEffectInstance(effect), player);
                }
            }
        }
    }

    /** Brewer's Eye: one roll per batch taken out, for one more of what was brewed. */
    static void extraBrew(Player player, ItemStack brewed) {
        int rank = TalentService.rank(player, Skill.ALCHEMY, "brewers_eye");
        if (rank > 0 && player.getRandom().nextDouble() < rank * 0.20) {
            player.getInventory().placeItemBackInInventory(brewed.copyWithCount(1));
        }
    }

    /**
     * Antidote. An effect's duration cannot be changed in place, and this event fires before the
     * effect is stored, so the application is noted here and cut short on the player's next tick.
     */
    @SubscribeEvent
    public static void onEffectAdded(MobEffectEvent.Added event) {
        if (reapplying || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MobEffectInstance added = event.getEffectInstance();
        MobEffect effect = added.getEffect();
        if (added.isInfiniteDuration() || effect.isInstantenous()
                || effect.getCategory() != MobEffectCategory.HARMFUL
                || TalentService.rank(player, Skill.ALCHEMY, "antidote") <= 0) {
            return;
        }
        ANTIDOTE.computeIfAbsent(player.getUUID(), id -> new ConcurrentHashMap<>())
                .put(added.getEffect(), new Applied(added.getAmplifier(), added.getDuration(),
                        player.level().getGameTime()));
    }

    private static void shorten(ServerPlayer player, Map<MobEffect, Applied> applied) {
        int rank = TalentService.rank(player, Skill.ALCHEMY, "antidote");
        long now = player.level().getGameTime();
        for (Map.Entry<MobEffect, Applied> entry : applied.entrySet()) {
            Applied was = entry.getValue();
            if (now <= was.tick()) {
                continue;
            }
            applied.remove(entry.getKey());
            MobEffectInstance active = player.getEffect(entry.getKey());
            long elapsed = now - was.tick();
            // Only an application that actually took. A weaker one that vanilla ignored would
            // otherwise cut the running effect again, and again, every time it is reapplied.
            if (rank <= 0 || active == null || active.isInfiniteDuration()
                    || active.getAmplifier() != was.amplifier()
                    || active.getDuration() > was.duration()
                    || active.getDuration() < was.duration() - elapsed - 1) {
                continue;
            }
            int remaining = (int) Math.round(was.duration() * (1.0 - Math.min(1.0, rank * 0.10))) - (int) elapsed;
            if (remaining >= active.getDuration()) {
                continue;
            }
            player.removeEffect(entry.getKey());
            if (remaining > 0) {
                quietlyAdd(player, new MobEffectInstance(entry.getKey(), remaining, active.getAmplifier(),
                        active.isAmbient(), active.isVisible(), active.showIcon()));
            }
        }
    }

    // ---- Shared ------------------------------------------------------------------------------

    private static void quietlyAdd(ServerPlayer player, MobEffectInstance instance) {
        reapplying = true;
        try {
            player.addEffect(instance);
        } finally {
            reapplying = false;
        }
    }

    private static List<ServerPlayer> playersNear(ServerPlayer player, double radius) {
        return player.serverLevel().getEntitiesOfClass(ServerPlayer.class,
                player.getBoundingBox().inflate(radius),
                other -> other != player && other.isAlive() && !other.isSpectator()
                        && other.distanceToSqr(player) <= radius * radius);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }
        UUID id = player.getUUID();
        Cure cure = CURES.get(id);
        if (cure != null && player.level().getGameTime() >= cure.dueTick()) {
            CURES.remove(id);
            cure(player, cure);
        }
        Map<MobEffect, Applied> applied = ANTIDOTE.get(id);
        if (applied != null) {
            shorten(player, applied);
            if (applied.isEmpty()) {
                ANTIDOTE.remove(id, applied);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        ANVIL_INVENTORY.remove(id);
        SICK_BEFORE.remove(id);
        CURES.remove(id);
        MENUS.remove(id);
        ANTIDOTE.remove(id);
    }

    /**
     * Gourmet's "five different foods in a row". Eating something already in the run restarts the
     * run just after its earlier serving, so A B C A D E completes on E (B C A D E). A completed
     * run empties, so the reward is per five meals, not every meal after the fifth.
     */
    public static final class FoodStreak<T> {

        private final int length;
        private final Deque<T> run = new ArrayDeque<>();

        public FoodStreak(int length) {
            this.length = length;
        }

        /** Records one meal. True when it completes a run of {@code length} different foods. */
        public boolean eat(T food) {
            if (run.contains(food)) {
                while (!run.isEmpty() && !run.pollFirst().equals(food)) {
                    // drop everything up to and including the earlier serving
                }
            }
            run.addLast(food);
            if (run.size() >= length) {
                run.clear();
                return true;
            }
            return false;
        }

        public int size() {
            return run.size();
        }
    }
}
