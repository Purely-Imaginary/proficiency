package dev.amman.proficiency.neoforge;

import dev.amman.proficiency.platform.TriState;
import dev.amman.proficiency.platform.bus.Event;
import dev.amman.proficiency.platform.bus.EventBus;
import dev.amman.proficiency.platform.bus.EventPriority;
import dev.amman.proficiency.platform.damage.DamageContainer;
import dev.amman.proficiency.platform.event.AnvilUpdateEvent;
import dev.amman.proficiency.platform.event.GrindstoneEvent;
import dev.amman.proficiency.platform.event.RegisterCommandsEvent;
import dev.amman.proficiency.platform.event.entity.EntityJoinLevelEvent;
import dev.amman.proficiency.platform.event.entity.ProjectileImpactEvent;
import dev.amman.proficiency.platform.event.entity.living.BabyEntitySpawnEvent;
import dev.amman.proficiency.platform.event.entity.living.FinalizeSpawnEvent;
import dev.amman.proficiency.platform.event.entity.living.LivingBreatheEvent;
import dev.amman.proficiency.platform.event.entity.living.LivingChangeTargetEvent;
import dev.amman.proficiency.platform.event.entity.living.LivingDamageEvent;
import dev.amman.proficiency.platform.event.entity.living.LivingDeathEvent;
import dev.amman.proficiency.platform.event.entity.living.LivingDropsEvent;
import dev.amman.proficiency.platform.event.entity.living.LivingEntityUseItemEvent;
import dev.amman.proficiency.platform.event.entity.living.LivingEvent;
import dev.amman.proficiency.platform.event.entity.living.LivingFallEvent;
import dev.amman.proficiency.platform.event.entity.living.LivingHealEvent;
import dev.amman.proficiency.platform.event.entity.living.LivingIncomingDamageEvent;
import dev.amman.proficiency.platform.event.entity.living.LivingKnockBackEvent;
import dev.amman.proficiency.platform.event.entity.living.LivingShieldBlockEvent;
import dev.amman.proficiency.platform.event.entity.living.MobEffectEvent;
import dev.amman.proficiency.platform.event.entity.living.MobSpawnEvent;
import dev.amman.proficiency.platform.event.entity.player.AnvilRepairEvent;
import dev.amman.proficiency.platform.event.entity.player.BonemealEvent;
import dev.amman.proficiency.platform.event.entity.player.ItemEntityPickupEvent;
import dev.amman.proficiency.platform.event.entity.player.ItemFishedEvent;
import dev.amman.proficiency.platform.event.entity.player.PlayerContainerEvent;
import dev.amman.proficiency.platform.event.entity.player.PlayerEvent;
import dev.amman.proficiency.platform.event.entity.player.PlayerInteractEvent;
import dev.amman.proficiency.platform.event.level.BlockDropsEvent;
import dev.amman.proficiency.platform.event.level.BlockEvent;
import dev.amman.proficiency.platform.event.level.ExplosionKnockbackEvent;
import dev.amman.proficiency.platform.event.server.ServerStartedEvent;
import dev.amman.proficiency.platform.event.server.ServerStoppingEvent;
import dev.amman.proficiency.platform.event.tick.PlayerTickEvent;
import dev.amman.proficiency.platform.event.tick.ServerTickEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Relays NeoForge's events to the shared bus, so the shared handlers run on NeoForge exactly where
 * they ran when they were NeoForge listeners themselves.
 *
 * <ul>
 * <li>One NeoForge listener per priority, so a shared handler keeps its place among other mods'
 * listeners. A priority with no shared listener costs one lookup and builds nothing.</li>
 * <li>The shared event is a thin subclass that reads and writes the NeoForge event, cancellation
 * included, so a change made in a handler is the change NeoForge sees, and the next priority's
 * handlers see it too.</li>
 * <li>Every relay hears canceled events; the shared bus then skips each handler that did not ask
 * for {@code receiveCanceled}, as NeoForge would.</li>
 * </ul>
 */
public final class NeoForgeEventBridge {

    private static final EventBus SHARED = dev.amman.proficiency.platform.bus.NeoForge.EVENT_BUS;

    private NeoForgeEventBridge() {
    }

    /**
     * Registers a relay at each of the five priorities for this NeoForge event type. A relay builds
     * the shared event only when the shared bus has a listener at its priority, so listeners
     * registered later (a client class, say) are still heard.
     */
    public static <N extends net.neoforged.bus.api.Event, S extends Event> void relay(Class<N> neoType,
            Class<S> sharedType, Function<N, ? extends S> wrap) {
        for (net.neoforged.bus.api.EventPriority neoPriority : net.neoforged.bus.api.EventPriority.values()) {
            EventPriority priority = EventPriority.valueOf(neoPriority.name());
            NeoForge.EVENT_BUS.addListener(neoPriority, true, neoType, event -> {
                if (SHARED.hasListeners(sharedType, priority)) {
                    SHARED.post(priority, wrap.apply(event));
                }
            });
        }
    }

    /** The server-and-common events; the client ones are in {@code NeoForgeEventBridgeClient}. */
    public static void register() {
        // ---- Players ----------------------------------------------------------------------------
        relay(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent.class,
                PlayerEvent.PlayerLoggedInEvent.class, e -> new PlayerEvent.PlayerLoggedInEvent(e.getEntity()));
        relay(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent.class,
                PlayerEvent.PlayerLoggedOutEvent.class, e -> new PlayerEvent.PlayerLoggedOutEvent(e.getEntity()));
        relay(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent.class,
                PlayerEvent.PlayerRespawnEvent.class,
                e -> new PlayerEvent.PlayerRespawnEvent(e.getEntity(), e.isEndConquered()));
        relay(net.neoforged.neoforge.event.entity.player.PlayerEvent.Clone.class, PlayerEvent.Clone.class,
                e -> new PlayerEvent.Clone(e.getEntity(), e.getOriginal(), e.isWasDeath()));
        relay(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent.class,
                PlayerEvent.PlayerChangedDimensionEvent.class,
                e -> new PlayerEvent.PlayerChangedDimensionEvent(e.getEntity(), e.getFrom(), e.getTo()));
        relay(net.neoforged.neoforge.event.entity.player.PlayerEvent.ItemCraftedEvent.class,
                PlayerEvent.ItemCraftedEvent.class,
                e -> new PlayerEvent.ItemCraftedEvent(e.getEntity(), e.getCrafting(), e.getInventory()));
        relay(net.neoforged.neoforge.event.entity.player.PlayerEvent.ItemSmeltedEvent.class,
                PlayerEvent.ItemSmeltedEvent.class,
                e -> new PlayerEvent.ItemSmeltedEvent(e.getEntity(), e.getSmelting()));
        relay(net.neoforged.neoforge.event.entity.player.PlayerEvent.TabListNameFormat.class,
                PlayerEvent.TabListNameFormat.class, TabListName::new);
        relay(net.neoforged.neoforge.event.entity.player.PlayerEvent.BreakSpeed.class,
                PlayerEvent.BreakSpeed.class, BreakSpeed::new);
        relay(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post.class, PlayerTickEvent.Post.class,
                e -> new PlayerTickEvent.Post(e.getEntity()));
        relay(net.neoforged.neoforge.event.entity.player.PlayerContainerEvent.Open.class,
                PlayerContainerEvent.Open.class, e -> new PlayerContainerEvent.Open(e.getEntity(), e.getContainer()));
        relay(net.neoforged.neoforge.event.entity.player.PlayerContainerEvent.Close.class,
                PlayerContainerEvent.Close.class, e -> new PlayerContainerEvent.Close(e.getEntity(), e.getContainer()));
        relay(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem.class,
                PlayerInteractEvent.RightClickItem.class, RightClickItem::new);
        relay(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock.class,
                PlayerInteractEvent.RightClickBlock.class, RightClickBlock::new);
        relay(net.neoforged.neoforge.event.entity.player.AnvilRepairEvent.class, AnvilRepairEvent.class,
                AnvilRepair::new);
        relay(net.neoforged.neoforge.event.entity.player.ItemFishedEvent.class, ItemFishedEvent.class,
                ItemFished::new);
        relay(net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent.Pre.class,
                ItemEntityPickupEvent.Pre.class, PickupPre::new);
        relay(net.neoforged.neoforge.event.entity.player.BonemealEvent.class, BonemealEvent.class,
                Bonemeal::new);

        // ---- Server -----------------------------------------------------------------------------
        relay(net.neoforged.neoforge.event.tick.ServerTickEvent.Post.class, ServerTickEvent.Post.class,
                e -> new ServerTickEvent.Post(e.getServer()));
        relay(net.neoforged.neoforge.event.server.ServerStartedEvent.class, ServerStartedEvent.class,
                e -> new ServerStartedEvent(e.getServer()));
        relay(net.neoforged.neoforge.event.server.ServerStoppingEvent.class, ServerStoppingEvent.class,
                e -> new ServerStoppingEvent(e.getServer()));
        relay(net.neoforged.neoforge.event.RegisterCommandsEvent.class, RegisterCommandsEvent.class,
                e -> new RegisterCommandsEvent(e.getDispatcher(), e.getCommandSelection(), e.getBuildContext()));

        // ---- Damage -----------------------------------------------------------------------------
        relay(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent.class,
                LivingIncomingDamageEvent.class, IncomingDamage::new);
        relay(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Pre.class,
                LivingDamageEvent.Pre.class, e -> new LivingDamageEvent.Pre(e.getEntity(), new Container(e.getContainer())));
        relay(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post.class,
                LivingDamageEvent.Post.class, DamagePost::new);
        relay(net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent.class,
                LivingShieldBlockEvent.class, ShieldBlock::new);
        relay(net.neoforged.neoforge.event.entity.living.LivingDeathEvent.class, LivingDeathEvent.class,
                Death::new);
        relay(net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent.class,
                LivingKnockBackEvent.class, KnockBack::new);
        relay(net.neoforged.neoforge.event.entity.living.LivingFallEvent.class, LivingFallEvent.class,
                Fall::new);
        relay(net.neoforged.neoforge.event.entity.living.LivingHealEvent.class, LivingHealEvent.class,
                Heal::new);
        relay(net.neoforged.neoforge.event.entity.living.LivingDropsEvent.class, LivingDropsEvent.class,
                Drops::new);

        // ---- Living -----------------------------------------------------------------------------
        relay(net.neoforged.neoforge.event.entity.living.MobEffectEvent.Applicable.class,
                MobEffectEvent.Applicable.class, EffectApplicable::new);
        relay(net.neoforged.neoforge.event.entity.living.MobEffectEvent.Added.class,
                MobEffectEvent.Added.class, e -> new MobEffectEvent.Added(e.getEntity(), e.getOldEffectInstance(),
                        e.getEffectInstance(), e.getEffectSource()));
        relay(net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Start.class,
                LivingEntityUseItemEvent.Start.class, UseStart::new);
        relay(net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Tick.class,
                LivingEntityUseItemEvent.Tick.class, UseTick::new);
        relay(net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Finish.class,
                LivingEntityUseItemEvent.Finish.class, UseFinish::new);
        relay(net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent.class,
                LivingChangeTargetEvent.class, ChangeTarget::new);
        relay(net.neoforged.neoforge.event.entity.living.LivingBreatheEvent.class, LivingBreatheEvent.class,
                Breathe::new);
        relay(net.neoforged.neoforge.event.entity.living.LivingEvent.LivingJumpEvent.class,
                LivingEvent.LivingJumpEvent.class, e -> new LivingEvent.LivingJumpEvent(e.getEntity()));
        relay(net.neoforged.neoforge.event.entity.living.LivingEvent.LivingVisibilityEvent.class,
                LivingEvent.LivingVisibilityEvent.class, Visibility::new);
        relay(net.neoforged.neoforge.event.entity.living.MobSpawnEvent.PositionCheck.class,
                MobSpawnEvent.PositionCheck.class, PositionCheck::new);
        relay(net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent.class, FinalizeSpawnEvent.class,
                FinalizeSpawn::new);
        relay(net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent.class,
                BabyEntitySpawnEvent.class, BabySpawn::new);

        // ---- Entities, blocks, menus ------------------------------------------------------------
        relay(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent.class, EntityJoinLevelEvent.class,
                JoinLevel::new);
        relay(net.neoforged.neoforge.event.entity.ProjectileImpactEvent.class, ProjectileImpactEvent.class,
                ProjectileImpact::new);
        relay(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent.class, BlockEvent.BreakEvent.class,
                BlockBreak::new);
        relay(net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent.class,
                BlockEvent.EntityPlaceEvent.class, EntityPlace::new);
        relay(net.neoforged.neoforge.event.level.BlockDropsEvent.class, BlockDropsEvent.class, BlockDrops::new);
        relay(net.neoforged.neoforge.event.level.ExplosionKnockbackEvent.class, ExplosionKnockbackEvent.class,
                ExplosionKnockback::new);
        relay(net.neoforged.neoforge.event.AnvilUpdateEvent.class, AnvilUpdateEvent.class, AnvilUpdate::new);
        relay(net.neoforged.neoforge.event.GrindstoneEvent.OnTakeItem.class, GrindstoneEvent.OnTakeItem.class,
                GrindstoneTake::new);
    }

    // ---- The shared events, reading and writing NeoForge's ---------------------------------------

    /** NeoForge's damage container behind the shared one. */
    static final class Container extends DamageContainer {
        private final net.neoforged.neoforge.common.damagesource.DamageContainer neo;

        Container(net.neoforged.neoforge.common.damagesource.DamageContainer neo) {
            super(neo.getSource(), neo.getOriginalDamage());
            this.neo = neo;
        }

        @Override
        public net.minecraft.world.damagesource.DamageSource getSource() {
            return neo.getSource();
        }

        @Override
        public float getOriginalDamage() {
            return neo.getOriginalDamage();
        }

        @Override
        public float getNewDamage() {
            return neo.getNewDamage();
        }

        @Override
        public void setNewDamage(float damage) {
            neo.setNewDamage(damage);
        }

        @Override
        public float getBlockedDamage() {
            return neo.getBlockedDamage();
        }

        @Override
        public float getShieldDamage() {
            return neo.getShieldDamage();
        }

        /** NeoForge records a shield block itself, from its LivingShieldBlockEvent. */
        @Override
        public void setBlocked(float blocked, float shield) {
            throw new UnsupportedOperationException("NeoForge sets shield damage from its own event");
        }

        @Override
        public int getPostAttackInvulnerabilityTicks() {
            return neo.getPostAttackInvulnerabilityTicks();
        }

        @Override
        public void setPostAttackInvulnerabilityTicks(int ticks) {
            neo.setPostAttackInvulnerabilityTicks(ticks);
        }

        @Override
        public float getReduction(Reduction reduction) {
            return neo.getReduction(net.neoforged.neoforge.common.damagesource.DamageContainer.Reduction
                    .valueOf(reduction.name()));
        }

        @Override
        public void setReduction(Reduction reduction, float amount) {
            neo.setReduction(net.neoforged.neoforge.common.damagesource.DamageContainer.Reduction
                    .valueOf(reduction.name()), amount);
        }
    }

    static final class TabListName extends PlayerEvent.TabListNameFormat {
        private final net.neoforged.neoforge.event.entity.player.PlayerEvent.TabListNameFormat neo;

        TabListName(net.neoforged.neoforge.event.entity.player.PlayerEvent.TabListNameFormat neo) {
            super(neo.getEntity());
            this.neo = neo;
        }

        @Override
        @Nullable
        public Component getDisplayName() {
            return neo.getDisplayName();
        }

        @Override
        public void setDisplayName(@Nullable Component displayName) {
            neo.setDisplayName(displayName);
        }
    }

    static final class BreakSpeed extends PlayerEvent.BreakSpeed {
        private final net.neoforged.neoforge.event.entity.player.PlayerEvent.BreakSpeed neo;

        BreakSpeed(net.neoforged.neoforge.event.entity.player.PlayerEvent.BreakSpeed neo) {
            super(neo.getEntity(), neo.getState(), neo.getOriginalSpeed(), neo.getPosition().orElse(null));
            this.neo = neo;
        }

        @Override
        public float getNewSpeed() {
            return neo.getNewSpeed();
        }

        @Override
        public void setNewSpeed(float newSpeed) {
            neo.setNewSpeed(newSpeed);
        }

        @Override
        public Optional<BlockPos> getPosition() {
            return neo.getPosition();
        }

        @Override
        public boolean isCanceled() {
            return neo.isCanceled();
        }

        @Override
        public void setCanceled(boolean canceled) {
            neo.setCanceled(canceled);
        }
    }

    static final class RightClickItem extends PlayerInteractEvent.RightClickItem {
        private final net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem neo;

        RightClickItem(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem neo) {
            super(neo.getEntity(), neo.getHand());
            this.neo = neo;
        }

        @Override
        public BlockPos getPos() {
            return neo.getPos();
        }

        @Override
        public Level getLevel() {
            return neo.getLevel();
        }

        @Override
        public ItemStack getItemStack() {
            return neo.getItemStack();
        }

        @Override
        public boolean isCanceled() {
            return neo.isCanceled();
        }

        @Override
        public void setCanceled(boolean canceled) {
            neo.setCanceled(canceled);
        }
    }

    static final class RightClickBlock extends PlayerInteractEvent.RightClickBlock {
        private final net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock neo;

        RightClickBlock(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock neo) {
            super(neo.getEntity(), neo.getHand(), neo.getPos());
            this.neo = neo;
        }

        @Override
        public Level getLevel() {
            return neo.getLevel();
        }

        @Override
        public ItemStack getItemStack() {
            return neo.getItemStack();
        }

        @Override
        public InteractionResult getCancellationResult() {
            return neo.getCancellationResult();
        }

        @Override
        public void setCancellationResult(InteractionResult result) {
            neo.setCancellationResult(result);
        }

        @Override
        public boolean isCanceled() {
            return neo.isCanceled();
        }

        @Override
        public void setCanceled(boolean canceled) {
            neo.setCanceled(canceled);
        }
    }

    static final class AnvilRepair extends AnvilRepairEvent {
        private final net.neoforged.neoforge.event.entity.player.AnvilRepairEvent neo;

        AnvilRepair(net.neoforged.neoforge.event.entity.player.AnvilRepairEvent neo) {
            super(neo.getEntity(), neo.getLeft(), neo.getRight(), neo.getOutput());
            this.neo = neo;
        }

        @Override
        public float getBreakChance() {
            return neo.getBreakChance();
        }

        @Override
        public void setBreakChance(float breakChance) {
            neo.setBreakChance(breakChance);
        }
    }

    static final class ItemFished extends ItemFishedEvent {
        private final net.neoforged.neoforge.event.entity.player.ItemFishedEvent neo;

        ItemFished(net.neoforged.neoforge.event.entity.player.ItemFishedEvent neo) {
            super(List.of(), neo.getRodDamage(), neo.getHookEntity());
            this.neo = neo;
        }

        @Override
        public int getRodDamage() {
            return neo.getRodDamage();
        }

        @Override
        public void damageRodBy(int rodDamage) {
            neo.damageRodBy(rodDamage);
        }

        /** NeoForge's own live list: what a handler adds or removes is what drops. */
        @Override
        public NonNullList<ItemStack> getDrops() {
            return neo.getDrops();
        }

        @Override
        public boolean isCanceled() {
            return neo.isCanceled();
        }

        @Override
        public void setCanceled(boolean canceled) {
            neo.setCanceled(canceled);
        }
    }

    static final class PickupPre extends ItemEntityPickupEvent.Pre {
        private final net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent.Pre neo;

        PickupPre(net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent.Pre neo) {
            super(neo.getPlayer(), neo.getItemEntity());
            this.neo = neo;
        }

        @Override
        public TriState canPickup() {
            return TriState.valueOf(neo.canPickup().name());
        }

        @Override
        public void setCanPickup(TriState state) {
            neo.setCanPickup(net.neoforged.neoforge.common.util.TriState.valueOf(state.name()));
        }
    }

    static final class Bonemeal extends BonemealEvent {
        private final net.neoforged.neoforge.event.entity.player.BonemealEvent neo;

        Bonemeal(net.neoforged.neoforge.event.entity.player.BonemealEvent neo) {
            super(neo.getPlayer(), neo.getLevel(), neo.getPos(), neo.getState(), neo.getStack());
            this.neo = neo;
        }

        @Override
        public boolean isValidBonemealTarget() {
            return neo.isValidBonemealTarget();
        }

        @Override
        public boolean isSuccessful() {
            return neo.isSuccessful();
        }

        @Override
        public void setSuccessful(boolean success) {
            neo.setSuccessful(success);
        }

        @Override
        public boolean isCanceled() {
            return neo.isCanceled();
        }

        @Override
        public void setCanceled(boolean canceled) {
            neo.setCanceled(canceled);
        }
    }

    static final class IncomingDamage extends LivingIncomingDamageEvent {
        private final net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent neo;

        IncomingDamage(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent neo) {
            super(neo.getEntity(), new Container(neo.getContainer()));
            this.neo = neo;
        }

        @Override
        public float getAmount() {
            return neo.getAmount();
        }

        @Override
        public float getOriginalAmount() {
            return neo.getOriginalAmount();
        }

        @Override
        public void setAmount(float newDamage) {
            neo.setAmount(newDamage);
        }

        @Override
        public void setInvulnerabilityTicks(int ticks) {
            neo.setInvulnerabilityTicks(ticks);
        }

        @Override
        public boolean isCanceled() {
            return neo.isCanceled();
        }

        @Override
        public void setCanceled(boolean canceled) {
            neo.setCanceled(canceled);
        }
    }

    /** Post reads its numbers once, as NeoForge's does; they come from NeoForge's event. */
    static final class DamagePost extends LivingDamageEvent.Post {
        private final net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post neo;

        DamagePost(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post neo) {
            super(neo.getEntity(), new Container(neoContainer(neo)));
            this.neo = neo;
        }

        @Override
        public float getOriginalDamage() {
            return neo.getOriginalDamage();
        }

        @Override
        public net.minecraft.world.damagesource.DamageSource getSource() {
            return neo.getSource();
        }

        @Override
        public float getNewDamage() {
            return neo.getNewDamage();
        }

        @Override
        public float getBlockedDamage() {
            return neo.getBlockedDamage();
        }

        @Override
        public float getShieldDamage() {
            return neo.getShieldDamage();
        }

        @Override
        public int getPostAttackInvulnerabilityTicks() {
            return neo.getPostAttackInvulnerabilityTicks();
        }

        @Override
        public float getReduction(DamageContainer.Reduction reduction) {
            return neo.getReduction(net.neoforged.neoforge.common.damagesource.DamageContainer.Reduction
                    .valueOf(reduction.name()));
        }

        /**
         * NeoForge's Post keeps its container private; a stand-in with the same numbers lets the
         * shared constructor read them. Every getter above reads NeoForge's event directly.
         */
        private static net.neoforged.neoforge.common.damagesource.DamageContainer neoContainer(
                net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post neo) {
            var copy = new net.neoforged.neoforge.common.damagesource.DamageContainer(neo.getSource(),
                    neo.getOriginalDamage());
            copy.setNewDamage(neo.getNewDamage());
            copy.setPostAttackInvulnerabilityTicks(neo.getPostAttackInvulnerabilityTicks());
            return copy;
        }
    }

    static final class ShieldBlock extends LivingShieldBlockEvent {
        private final net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent neo;

        ShieldBlock(net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent neo) {
            super(neo.getEntity(), new Container(neo.getDamageContainer()), neo.getOriginalBlock());
            this.neo = neo;
        }

        @Override
        public net.minecraft.world.damagesource.DamageSource getDamageSource() {
            return neo.getDamageSource();
        }

        @Override
        public float getOriginalBlockedDamage() {
            return neo.getOriginalBlockedDamage();
        }

        @Override
        public float getBlockedDamage() {
            return neo.getBlockedDamage();
        }

        @Override
        public float shieldDamage() {
            return neo.shieldDamage();
        }

        @Override
        public void setBlockedDamage(float blocked) {
            neo.setBlockedDamage(blocked);
        }

        @Override
        public void setShieldDamage(float damage) {
            neo.setShieldDamage(damage);
        }

        @Override
        public boolean getOriginalBlock() {
            return neo.getOriginalBlock();
        }

        @Override
        public boolean getBlocked() {
            return neo.getBlocked();
        }

        @Override
        public void setBlocked(boolean isBlocked) {
            neo.setBlocked(isBlocked);
        }

        @Override
        public boolean isCanceled() {
            return neo.isCanceled();
        }

        @Override
        public void setCanceled(boolean canceled) {
            neo.setCanceled(canceled);
        }
    }

    static final class Death extends LivingDeathEvent {
        private final net.neoforged.neoforge.event.entity.living.LivingDeathEvent neo;

        Death(net.neoforged.neoforge.event.entity.living.LivingDeathEvent neo) {
            super(neo.getEntity(), neo.getSource());
            this.neo = neo;
        }

        @Override
        public boolean isCanceled() {
            return neo.isCanceled();
        }

        @Override
        public void setCanceled(boolean canceled) {
            neo.setCanceled(canceled);
        }
    }

    static final class KnockBack extends LivingKnockBackEvent {
        private final net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent neo;

        KnockBack(net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent neo) {
            super(neo.getEntity(), neo.getOriginalStrength(), neo.getOriginalRatioX(), neo.getOriginalRatioZ());
            this.neo = neo;
        }

        @Override
        public float getStrength() {
            return neo.getStrength();
        }

        @Override
        public double getRatioX() {
            return neo.getRatioX();
        }

        @Override
        public double getRatioZ() {
            return neo.getRatioZ();
        }

        @Override
        public float getOriginalStrength() {
            return neo.getOriginalStrength();
        }

        @Override
        public double getOriginalRatioX() {
            return neo.getOriginalRatioX();
        }

        @Override
        public double getOriginalRatioZ() {
            return neo.getOriginalRatioZ();
        }

        @Override
        public void setStrength(float strength) {
            neo.setStrength(strength);
        }

        @Override
        public void setRatioX(double ratioX) {
            neo.setRatioX(ratioX);
        }

        @Override
        public void setRatioZ(double ratioZ) {
            neo.setRatioZ(ratioZ);
        }

        @Override
        public boolean isCanceled() {
            return neo.isCanceled();
        }

        @Override
        public void setCanceled(boolean canceled) {
            neo.setCanceled(canceled);
        }
    }

    static final class Fall extends LivingFallEvent {
        private final net.neoforged.neoforge.event.entity.living.LivingFallEvent neo;

        Fall(net.neoforged.neoforge.event.entity.living.LivingFallEvent neo) {
            super(neo.getEntity(), neo.getDistance(), neo.getDamageMultiplier());
            this.neo = neo;
        }

        @Override
        public float getDistance() {
            return neo.getDistance();
        }

        @Override
        public void setDistance(float distance) {
            neo.setDistance(distance);
        }

        @Override
        public float getDamageMultiplier() {
            return neo.getDamageMultiplier();
        }

        @Override
        public void setDamageMultiplier(float damageMultiplier) {
            neo.setDamageMultiplier(damageMultiplier);
        }

        @Override
        public boolean isCanceled() {
            return neo.isCanceled();
        }

        @Override
        public void setCanceled(boolean canceled) {
            neo.setCanceled(canceled);
        }
    }

    static final class Heal extends LivingHealEvent {
        private final net.neoforged.neoforge.event.entity.living.LivingHealEvent neo;

        Heal(net.neoforged.neoforge.event.entity.living.LivingHealEvent neo) {
            super(neo.getEntity(), neo.getAmount());
            this.neo = neo;
        }

        @Override
        public float getAmount() {
            return neo.getAmount();
        }

        @Override
        public void setAmount(float amount) {
            neo.setAmount(amount);
        }

        @Override
        public boolean isCanceled() {
            return neo.isCanceled();
        }

        @Override
        public void setCanceled(boolean canceled) {
            neo.setCanceled(canceled);
        }
    }

    static final class Drops extends LivingDropsEvent {
        private final net.neoforged.neoforge.event.entity.living.LivingDropsEvent neo;

        Drops(net.neoforged.neoforge.event.entity.living.LivingDropsEvent neo) {
            super(neo.getEntity(), neo.getSource(), neo.getDrops(), neo.isRecentlyHit());
            this.neo = neo;
        }

        @Override
        public Collection<ItemEntity> getDrops() {
            return neo.getDrops();
        }

        @Override
        public boolean isCanceled() {
            return neo.isCanceled();
        }

        @Override
        public void setCanceled(boolean canceled) {
            neo.setCanceled(canceled);
        }
    }

    static final class EffectApplicable extends MobEffectEvent.Applicable {
        private final net.neoforged.neoforge.event.entity.living.MobEffectEvent.Applicable neo;

        EffectApplicable(net.neoforged.neoforge.event.entity.living.MobEffectEvent.Applicable neo) {
            super(neo.getEntity(), neo.getEffectInstance(), neo.getEffectSource(), false);
            this.neo = neo;
        }

        @Override
        public void setResult(Result result) {
            neo.setResult(net.neoforged.neoforge.event.entity.living.MobEffectEvent.Applicable.Result
                    .valueOf(result.name()));
        }

        @Override
        public Result getResult() {
            return Result.valueOf(neo.getResult().name());
        }

        @Override
        public boolean getApplicationResult() {
            return neo.getApplicationResult();
        }
    }

    static final class UseStart extends LivingEntityUseItemEvent.Start {
        private final net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Start neo;

        UseStart(net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Start neo) {
            super(neo.getEntity(), neo.getItem(), neo.getHand(), neo.getDuration());
            this.neo = neo;
        }

        @Override
        public int getDuration() {
            return neo.getDuration();
        }

        @Override
        public void setDuration(int duration) {
            neo.setDuration(duration);
        }

        @Override
        public boolean isCanceled() {
            return neo.isCanceled();
        }

        @Override
        public void setCanceled(boolean canceled) {
            neo.setCanceled(canceled);
        }
    }

    static final class UseTick extends LivingEntityUseItemEvent.Tick {
        private final net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Tick neo;

        UseTick(net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Tick neo) {
            super(neo.getEntity(), neo.getItem(), neo.getDuration());
            this.neo = neo;
        }

        @Override
        public int getDuration() {
            return neo.getDuration();
        }

        @Override
        public void setDuration(int duration) {
            neo.setDuration(duration);
        }

        @Override
        @Nullable
        public net.minecraft.world.InteractionHand getHand() {
            return neo.getHand();
        }

        @Override
        public boolean isCanceled() {
            return neo.isCanceled();
        }

        @Override
        public void setCanceled(boolean canceled) {
            neo.setCanceled(canceled);
        }
    }

    static final class UseFinish extends LivingEntityUseItemEvent.Finish {
        private final net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Finish neo;

        UseFinish(net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Finish neo) {
            super(neo.getEntity(), neo.getItem(), neo.getDuration(), neo.getResultStack());
            this.neo = neo;
        }

        @Override
        public int getDuration() {
            return neo.getDuration();
        }

        @Override
        public void setDuration(int duration) {
            neo.setDuration(duration);
        }

        @Override
        @Nullable
        public net.minecraft.world.InteractionHand getHand() {
            return neo.getHand();
        }

        @Override
        public ItemStack getResultStack() {
            return neo.getResultStack();
        }

        @Override
        public void setResultStack(ItemStack result) {
            neo.setResultStack(result);
        }
    }

    static final class ChangeTarget extends LivingChangeTargetEvent {
        private final net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent neo;

        ChangeTarget(net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent neo) {
            super(neo.getEntity(), neo.getNewAboutToBeSetTarget());
            this.neo = neo;
        }

        @Override
        @Nullable
        public LivingEntity getNewAboutToBeSetTarget() {
            return neo.getNewAboutToBeSetTarget();
        }

        @Override
        public boolean isCanceled() {
            return neo.isCanceled();
        }

        @Override
        public void setCanceled(boolean canceled) {
            neo.setCanceled(canceled);
        }
    }

    static final class Breathe extends LivingBreatheEvent {
        private final net.neoforged.neoforge.event.entity.living.LivingBreatheEvent neo;

        Breathe(net.neoforged.neoforge.event.entity.living.LivingBreatheEvent neo) {
            super(neo.getEntity(), neo.canBreathe(), neo.getConsumeAirAmount(), neo.getRefillAirAmount());
            this.neo = neo;
        }

        @Override
        public boolean canBreathe() {
            return neo.canBreathe();
        }

        @Override
        public void setCanBreathe(boolean canBreathe) {
            neo.setCanBreathe(canBreathe);
        }

        @Override
        public int getConsumeAirAmount() {
            return neo.getConsumeAirAmount();
        }

        @Override
        public void setConsumeAirAmount(int consumeAirAmount) {
            neo.setConsumeAirAmount(consumeAirAmount);
        }

        @Override
        public int getRefillAirAmount() {
            return neo.getRefillAirAmount();
        }

        @Override
        public void setRefillAirAmount(int refillAirAmount) {
            neo.setRefillAirAmount(refillAirAmount);
        }
    }

    static final class Visibility extends LivingEvent.LivingVisibilityEvent {
        private final net.neoforged.neoforge.event.entity.living.LivingEvent.LivingVisibilityEvent neo;

        Visibility(net.neoforged.neoforge.event.entity.living.LivingEvent.LivingVisibilityEvent neo) {
            super(neo.getEntity(), neo.getLookingEntity(), neo.getVisibilityModifier());
            this.neo = neo;
        }

        @Override
        public void modifyVisibility(double mod) {
            neo.modifyVisibility(mod);
        }

        @Override
        public double getVisibilityModifier() {
            return neo.getVisibilityModifier();
        }
    }

    static final class PositionCheck extends MobSpawnEvent.PositionCheck {
        private final net.neoforged.neoforge.event.entity.living.MobSpawnEvent.PositionCheck neo;

        PositionCheck(net.neoforged.neoforge.event.entity.living.MobSpawnEvent.PositionCheck neo) {
            super(neo.getEntity(), neo.getLevel(), neo.getSpawnType());
            this.neo = neo;
        }

        @Override
        public double getX() {
            return neo.getX();
        }

        @Override
        public double getY() {
            return neo.getY();
        }

        @Override
        public double getZ() {
            return neo.getZ();
        }

        @Override
        public void setResult(Result result) {
            neo.setResult(net.neoforged.neoforge.event.entity.living.MobSpawnEvent.PositionCheck.Result
                    .valueOf(result.name()));
        }

        @Override
        public Result getResult() {
            return Result.valueOf(neo.getResult().name());
        }
    }

    /** Read-only for the shared code, but NeoForge's can be canceled by another mod first. */
    static final class FinalizeSpawn extends FinalizeSpawnEvent {
        private final net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent neo;

        FinalizeSpawn(net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent neo) {
            super(neo.getEntity(), neo.getLevel(), neo.getSpawnType());
            this.neo = neo;
        }

        @Override
        public double getX() {
            return neo.getX();
        }

        @Override
        public double getY() {
            return neo.getY();
        }

        @Override
        public double getZ() {
            return neo.getZ();
        }

        @Override
        public boolean isCanceled() {
            return neo.isCanceled();
        }
    }

    static final class BabySpawn extends BabyEntitySpawnEvent {
        private final net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent neo;

        BabySpawn(net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent neo) {
            super(neo.getParentA(), neo.getParentB(), neo.getChild());
            this.neo = neo;
        }

        @Override
        @Nullable
        public Player getCausedByPlayer() {
            return neo.getCausedByPlayer();
        }

        @Override
        @Nullable
        public AgeableMob getChild() {
            return neo.getChild();
        }

        @Override
        public void setChild(@Nullable AgeableMob proposedChild) {
            neo.setChild(proposedChild);
        }

        @Override
        public boolean isCanceled() {
            return neo.isCanceled();
        }

        @Override
        public void setCanceled(boolean canceled) {
            neo.setCanceled(canceled);
        }
    }

    static final class JoinLevel extends EntityJoinLevelEvent {
        private final net.neoforged.neoforge.event.entity.EntityJoinLevelEvent neo;

        JoinLevel(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent neo) {
            super(neo.getEntity(), neo.getLevel(), neo.loadedFromDisk());
            this.neo = neo;
        }

        @Override
        public boolean isCanceled() {
            return neo.isCanceled();
        }

        @Override
        public void setCanceled(boolean canceled) {
            neo.setCanceled(canceled);
        }
    }

    static final class ProjectileImpact extends ProjectileImpactEvent {
        private final net.neoforged.neoforge.event.entity.ProjectileImpactEvent neo;

        ProjectileImpact(net.neoforged.neoforge.event.entity.ProjectileImpactEvent neo) {
            super(neo.getProjectile(), neo.getRayTraceResult());
            this.neo = neo;
        }

        @Override
        public boolean isCanceled() {
            return neo.isCanceled();
        }

        @Override
        public void setCanceled(boolean canceled) {
            neo.setCanceled(canceled);
        }
    }

    static final class BlockBreak extends BlockEvent.BreakEvent {
        private final net.neoforged.neoforge.event.level.BlockEvent.BreakEvent neo;

        BlockBreak(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent neo) {
            super((Level) neo.getLevel(), neo.getPos(), neo.getState(), neo.getPlayer());
            this.neo = neo;
        }

        @Override
        public LevelAccessor getLevel() {
            return neo.getLevel();
        }

        @Override
        public boolean isCanceled() {
            return neo.isCanceled();
        }

        @Override
        public void setCanceled(boolean canceled) {
            neo.setCanceled(canceled);
        }
    }

    static final class EntityPlace extends BlockEvent.EntityPlaceEvent {
        private final net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent neo;

        EntityPlace(net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent neo) {
            super(neo.getLevel(), neo.getPos(), neo.getState(), neo.getPlacedBlock(), neo.getPlacedAgainst(),
                    neo.getEntity());
            this.neo = neo;
        }

        @Override
        public BlockState getState() {
            return neo.getState();
        }

        @Override
        @Nullable
        public Entity getEntity() {
            return neo.getEntity();
        }

        @Override
        public BlockState getPlacedBlock() {
            return neo.getPlacedBlock();
        }

        @Override
        public BlockState getPlacedAgainst() {
            return neo.getPlacedAgainst();
        }

        @Override
        public boolean isCanceled() {
            return neo.isCanceled();
        }

        @Override
        public void setCanceled(boolean canceled) {
            neo.setCanceled(canceled);
        }
    }

    static final class BlockDrops extends BlockDropsEvent {
        private final net.neoforged.neoforge.event.level.BlockDropsEvent neo;

        BlockDrops(net.neoforged.neoforge.event.level.BlockDropsEvent neo) {
            super(neo.getLevel(), neo.getPos(), neo.getState(), neo.getBlockEntity(), neo.getDrops(),
                    neo.getBreaker(), neo.getTool());
            this.neo = neo;
        }

        @Override
        public List<ItemEntity> getDrops() {
            return neo.getDrops();
        }

        @Override
        public int getDroppedExperience() {
            return neo.getDroppedExperience();
        }

        @Override
        public void setDroppedExperience(int experience) {
            neo.setDroppedExperience(experience);
        }

        @Override
        public boolean isCanceled() {
            return neo.isCanceled();
        }

        @Override
        public void setCanceled(boolean canceled) {
            neo.setCanceled(canceled);
        }
    }

    static final class ExplosionKnockback extends ExplosionKnockbackEvent {
        private final net.neoforged.neoforge.event.level.ExplosionKnockbackEvent neo;

        ExplosionKnockback(net.neoforged.neoforge.event.level.ExplosionKnockbackEvent neo) {
            super(neo.getLevel(), neo.getExplosion(), neo.getAffectedEntity(), neo.getKnockbackVelocity());
            this.neo = neo;
        }

        @Override
        public List<BlockPos> getAffectedBlocks() {
            return neo.getAffectedBlocks();
        }

        @Override
        public Vec3 getKnockbackVelocity() {
            return neo.getKnockbackVelocity();
        }

        @Override
        public void setKnockbackVelocity(Vec3 newKnockbackVelocity) {
            neo.setKnockbackVelocity(newKnockbackVelocity);
        }
    }

    static final class AnvilUpdate extends AnvilUpdateEvent {
        private final net.neoforged.neoforge.event.AnvilUpdateEvent neo;

        AnvilUpdate(net.neoforged.neoforge.event.AnvilUpdateEvent neo) {
            super(neo.getLeft(), neo.getRight(), neo.getName(), neo.getCost(), neo.getPlayer());
            this.neo = neo;
        }

        @Override
        public ItemStack getOutput() {
            return neo.getOutput();
        }

        @Override
        public void setOutput(ItemStack output) {
            neo.setOutput(output);
        }

        @Override
        public long getCost() {
            return neo.getCost();
        }

        @Override
        public void setCost(long cost) {
            if (neo != null) {
                neo.setCost(cost);
            }
        }

        @Override
        public int getMaterialCost() {
            return neo.getMaterialCost();
        }

        @Override
        public void setMaterialCost(int materialCost) {
            if (neo != null) {
                neo.setMaterialCost(materialCost);
            }
        }

        @Override
        public boolean isCanceled() {
            return neo.isCanceled();
        }

        @Override
        public void setCanceled(boolean canceled) {
            neo.setCanceled(canceled);
        }
    }

    static final class GrindstoneTake extends GrindstoneEvent.OnTakeItem {
        private final net.neoforged.neoforge.event.GrindstoneEvent.OnTakeItem neo;

        GrindstoneTake(net.neoforged.neoforge.event.GrindstoneEvent.OnTakeItem neo) {
            super(neo.getTopItem(), neo.getBottomItem(), neo.getXp());
            this.neo = neo;
        }

        @Override
        public int getXp() {
            return neo.getXp();
        }

        @Override
        public void setXp(int xp) {
            if (neo != null) {
                neo.setXp(xp);
            }
        }

        @Override
        public ItemStack getNewTopItem() {
            return neo.getNewTopItem();
        }

        @Override
        public ItemStack getNewBottomItem() {
            return neo.getNewBottomItem();
        }

        @Override
        public void setNewTopItem(ItemStack newTop) {
            neo.setNewTopItem(newTop);
        }

        @Override
        public void setNewBottomItem(ItemStack newBottom) {
            neo.setNewBottomItem(newBottom);
        }

        @Override
        public boolean isCanceled() {
            return neo.isCanceled();
        }

        @Override
        public void setCanceled(boolean canceled) {
            neo.setCanceled(canceled);
        }
    }
}
