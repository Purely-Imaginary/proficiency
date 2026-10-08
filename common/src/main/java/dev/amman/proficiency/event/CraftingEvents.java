package dev.amman.proficiency.event;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.skill.ProcService;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillService;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import dev.amman.proficiency.platform.bus.SubscribeEvent;
import dev.amman.proficiency.platform.bus.EventBusSubscriber;
import dev.amman.proficiency.platform.event.AnvilUpdateEvent;
import dev.amman.proficiency.platform.event.entity.living.LivingEntityUseItemEvent;
import dev.amman.proficiency.platform.event.entity.player.AnvilRepairEvent;
import dev.amman.proficiency.platform.event.entity.player.PlayerContainerEvent;
import dev.amman.proficiency.platform.event.entity.player.PlayerEvent;
import dev.amman.proficiency.platform.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Smithing, Cooking and Alchemy. */
@EventBusSubscriber(modid = Proficiency.MOD_ID)
public final class CraftingEvents {

    private static final int BREW_SLOTS = 3;

    private static final Map<UUID, ItemStack[]> BREW_SNAPSHOTS = new ConcurrentHashMap<>();
    private static final Map<UUID, Pending> PENDING_POTIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, HaggleListener> HAGGLERS = new ConcurrentHashMap<>();

    private CraftingEvents() {
    }

    /** @param stronger Catalyst landed: the lasting effects go up a level on the same tick. */
    private record Pending(List<MobEffectInstance> effects, long dueTick, boolean stronger) {
    }

    // ---- Smithing ----------------------------------------------------------------------------

    @SubscribeEvent
    public static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }
        ItemStack result = event.getCrafting();
        if (result.isEmpty()) {
            return;
        }
        // Damageable is a good enough proxy for "tool, weapon or armour", and it holds for the
        // modded gear in Better MC without knowing anything about it.
        if (result.isDamageableItem()) {
            SkillService.grant(player, Skill.SMITHING, Math.max(1, result.getCount()) * 0.5,
                    result.getDescriptionId());
            // Before Masterwork copies it, so the extra pieces carry the same enchantments.
            TalentCraftingEvents.finishCraftedGear(player, result);
            // Masterwork: the bench turns out another, and proc power (Fine Edge) can make it two.
            if (ProcService.fire(player, Skill.SMITHING)) {
                int pieces = Math.max(1, ProcService.roundRandomly(player,
                        ProcService.power(player, Skill.SMITHING)));
                for (int i = 0; i < pieces; i++) {
                    ItemStack piece = result.copy();
                    TalentCraftingEvents.legendary(player, piece);
                    player.getInventory().placeItemBackInInventory(piece);
                }
            }
        } else if (result.has(DataComponents.FOOD)) {
            // Farmer's Delight cooks on a board and in a pot, never through a furnace, so without
            // this a cook who uses the mod the pack ships earns nothing at all.
            SkillService.grant(player, Skill.COOKING, Math.max(1, result.getCount()) * 0.75,
                    result.getDescriptionId());
            if (ProcService.fire(player, Skill.COOKING)) {
                player.getInventory().placeItemBackInInventory(result.copyWithCount(extraPortions(player)));
            }
        }
    }

    @SubscribeEvent
    public static void onAnvilRepair(AnvilRepairEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }
        double bonus = SkillService.bonus(player, Skill.SMITHING);
        if (bonus > 0) {
            event.setBreakChance((float) (event.getBreakChance() * dev.amman.proficiency.skill.SkillPassives.lessUpTo90(bonus)));
        }
        SkillService.grant(player, Skill.SMITHING, 2.0, "proficiency.xplog.source.anvil");
    }

    /**
     * A skilled smith argues the anvil down. {@link AnvilUpdateEvent} fires before vanilla works
     * out the cost, so the discount is applied by {@link HaggleListener} once the cost is final.
     * The event only marks that a fresh cost is on its way.
     */
    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            HaggleListener haggler = HAGGLERS.get(player.getUUID());
            if (haggler != null) {
                haggler.expectFreshCost();
            }
        }
    }

    /**
     * The one case the listener cannot see: vanilla's new cost equals the discounted one already
     * showing, so nothing changed and nothing was reported. The next tick discounts it.
     */
    @SubscribeEvent
    public static void onAnvilTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player
                && player.containerMenu instanceof AnvilMenu menu) {
            HaggleListener haggler = HAGGLERS.get(player.getUUID());
            if (haggler != null) {
                haggler.settle(menu);
            }
        }
    }

    /** Never below one level, so repairs still cost. Zero means "nothing to take" and stays zero. */
    public static int haggled(int cost, double bonus) {
        if (cost <= 0 || bonus <= 0) {
            return cost;
        }
        return (int) Math.max(1L, Math.round(cost * dev.amman.proficiency.skill.SkillPassives.anvilCostFactor(bonus)));
    }

    /**
     * Rewrites the anvil's cost right after vanilla sets it. It hears the result slot change,
     * which the menu reports before its data slots in the same broadcast, so the client is sent the
     * discounted cost and never the full one. The cost it wrote itself comes back as a data change
     * on the next broadcast and is recognised, so it is never discounted twice. The cost is what
     * {@code AnvilMenu.onTake} charges, so the player pays what they see.
     */
    private static final class HaggleListener implements ContainerListener {

        private final Player player;
        /** The discounted cost this listener last wrote, or -1 when the next cost is vanilla's. */
        private int written = -1;
        private boolean fresh;

        HaggleListener(Player player) {
            this.player = player;
        }

        void expectFreshCost() {
            written = -1;
            fresh = true;
        }

        void settle(AnvilMenu menu) {
            if (fresh) {
                discount(menu);
            }
        }

        private void discount(AbstractContainerMenu menu) {
            if (!(menu instanceof AnvilMenu anvil)) {
                return;
            }
            fresh = false;
            int cost = anvil.getCost();
            if (cost == written) {
                return;
            }
            int reduced = haggled(cost, SkillService.bonus(player, Skill.SMITHING));
            if (reduced != cost) {
                dev.amman.proficiency.platform.Services.platform().setAnvilCost(anvil, reduced);
            }
            written = reduced;
        }

        @Override
        public void slotChanged(AbstractContainerMenu menu, int slot, ItemStack stack) {
            if (slot == AnvilMenu.RESULT_SLOT) {
                discount(menu);
            }
        }

        @Override
        public void dataChanged(AbstractContainerMenu menu, int index, int value) {
            // The anvil's only data slot is its cost; more material on the right changes the cost
            // without changing the result.
            discount(menu);
        }
    }

    // ---- Cooking -----------------------------------------------------------------------------

    @SubscribeEvent
    public static void onSmelted(PlayerEvent.ItemSmeltedEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }
        ItemStack result = event.getSmelting();
        // A shift-click fires this event twice (see SmeltTake), and the second stack is empty or
        // is the part that never left the furnace. Neither was taken, so neither pays.
        ItemStack inSlot = player.containerMenu instanceof AbstractFurnaceMenu menu
                ? menu.getSlot(AbstractFurnaceMenu.RESULT_SLOT).getItem() : ItemStack.EMPTY;
        if (SmeltTake.takenCount(result.getCount(), result == inSlot) <= 0) {
            return;
        }
        boolean isFood = result.has(DataComponents.FOOD);
        Skill skill = isFood ? Skill.COOKING : Skill.SMITHING;

        if (isFood) {
            double bonus = SkillService.bonus(player, Skill.COOKING);
            if (bonus > 0 && player.level().getRandom().nextDouble() < bonus) {
                player.getInventory().placeItemBackInInventory(result.copyWithCount(1));
            }
            if (ProcService.fire(player, Skill.COOKING)) {
                player.getInventory().placeItemBackInInventory(result.copyWithCount(extraPortions(player)));
            }
        }
        SkillService.grant(player, skill, Math.max(1, result.getCount()) * 1.0, result.getDescriptionId());
    }

    /** A cooking proc's extra portions: two, and more with proc power (Banquet). */
    private static int extraPortions(Player player) {
        return Math.max(1, ProcService.roundRandomly(player, 2.0 * ProcService.power(player, Skill.COOKING)));
    }

    // ---- Alchemy -----------------------------------------------------------------------------

    /**
     * There is no brewing event that knows which player did the brewing, so the brewing stand's
     * potion slots are photographed when a player opens it and compared when they close it.
     */
    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (event.getContainer() instanceof AnvilMenu anvil && event.getEntity() instanceof ServerPlayer player) {
            HaggleListener haggler = new HaggleListener(player);
            HAGGLERS.put(player.getUUID(), haggler);
            anvil.addSlotListener(haggler);
            return;
        }
        if (!(event.getContainer() instanceof BrewingStandMenu menu)) {
            return;
        }
        ItemStack[] snapshot = new ItemStack[BREW_SLOTS];
        for (int i = 0; i < BREW_SLOTS; i++) {
            snapshot[i] = menu.getSlot(i).getItem().copy();
        }
        BREW_SNAPSHOTS.put(event.getEntity().getUUID(), snapshot);
    }

    @SubscribeEvent
    public static void onContainerClose(PlayerContainerEvent.Close event) {
        Player player = event.getEntity();
        if (event.getContainer() instanceof AnvilMenu) {
            HAGGLERS.remove(player.getUUID());
        }
        ItemStack[] before = BREW_SNAPSHOTS.remove(player.getUUID());
        if (before == null || !(event.getContainer() instanceof BrewingStandMenu menu)) {
            return;
        }
        if (player.level().isClientSide()) {
            return;
        }
        int brewed = 0;
        ItemStack sample = ItemStack.EMPTY;
        for (int i = 0; i < BREW_SLOTS; i++) {
            ItemStack after = menu.getSlot(i).getItem();
            if (after.isEmpty() || !after.has(DataComponents.POTION_CONTENTS)) {
                continue;
            }
            if (!ItemStack.matches(before[i], after)) {
                brewed++;
                sample = after;
            }
        }
        if (brewed > 0) {
            SkillService.grant(player, Skill.ALCHEMY, brewed * 3.0, sample.getDescriptionId());
            TalentCraftingEvents.extraBrew(player, sample);
        }
    }

    @SubscribeEvent
    public static void onFinishUsing(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PotionContents contents = event.getItem().get(DataComponents.POTION_CONTENTS);
        if (contents == null) {
            return;
        }
        List<MobEffectInstance> effects = new ArrayList<>();
        contents.getAllEffects().forEach(instance -> effects.add(new MobEffectInstance(instance)));
        if (effects.isEmpty()) {
            return;
        }
        SkillService.grant(player, Skill.ALCHEMY, 1.0, event.getItem().getDescriptionId());

        // Efficient Draught: you drank it and you still have it. Strong Brew can make it two.
        if (ProcService.fire(player, Skill.ALCHEMY)) {
            int kept = Math.max(1, ProcService.roundRandomly(player, ProcService.power(player, Skill.ALCHEMY)));
            player.getInventory().placeItemBackInInventory(event.getItem().copyWithCount(kept));
        }

        boolean stronger = TalentCraftingEvents.catalyst(player, effects);
        TalentCraftingEvents.panacea(player, effects);
        TalentCraftingEvents.shareBrew(player, effects);

        if (stronger || SkillService.bonus(player, Skill.ALCHEMY) > 0) {
            // Vanilla applies the effects during this same call, so the stretch waits a tick.
            PENDING_POTIONS.put(player.getUUID(),
                    new Pending(effects, player.level().getGameTime() + 1, stronger));
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }
        Pending pending = PENDING_POTIONS.get(player.getUUID());
        if (pending == null || player.level().getGameTime() < pending.dueTick()) {
            return;
        }
        PENDING_POTIONS.remove(player.getUUID());

        double bonus = SkillService.bonus(player, Skill.ALCHEMY);
        for (MobEffectInstance drunk : pending.effects()) {
            if (bonus <= 0) {
                break;
            }
            Holder<MobEffect> effect = drunk.getEffect();
            MobEffectInstance active = player.getEffect(effect);
            if (active == null || active.isInfiniteDuration()) {
                continue;
            }
            int stretched = (int) Math.round(active.getDuration() * dev.amman.proficiency.skill.SkillPassives.more(bonus));
            if (stretched <= active.getDuration()) {
                continue;
            }
            player.addEffect(new MobEffectInstance(
                    effect,
                    stretched,
                    active.getAmplifier(),
                    active.isAmbient(),
                    active.isVisible(),
                    active.showIcon()));
        }
        if (pending.stronger()) {
            TalentCraftingEvents.strengthen(player, pending.effects());
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        BREW_SNAPSHOTS.remove(id);
        PENDING_POTIONS.remove(id);
        HAGGLERS.remove(id);
    }
}
