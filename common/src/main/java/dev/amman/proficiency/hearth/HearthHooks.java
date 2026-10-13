package dev.amman.proficiency.hearth;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.config.ProficiencyConfig;
import dev.amman.proficiency.hearth.net.HearthRequestPayload;
import dev.amman.proficiency.platform.event.entity.living.LivingIncomingDamageEvent;
import dev.amman.proficiency.skill.Skill;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * The call sites the Hearth lanes fill (docs/HEARTH-CONTRACT.md, "Hooks"). The game calls a
 * dispatcher ({@code onGrant}, {@code onPlayerSecond}, ...); each lane adds its listeners from its own
 * init class ({@code HEARTH_TICK.add(...)}), so no lane edits a shared file to be called.
 *
 * <ul>
 * <li>Listeners run on the server thread, in the order they were added. One that throws is logged
 * (rate limited) and the next still runs: a bug in one lane never stops an XP grant.</li>
 * <li>With {@code hearth.enabled = false} every dispatcher is silent except {@link #PLAYER_SECOND}
 * and {@link #DEATH}, which keep running so lanes can undo per-player effects (the in-range flag
 * runs out on its own once Hearths stop ticking).</li>
 * </ul>
 */
public final class HearthHooks {

    // ---- Listener types -------------------------------------------------------------------------

    /**
     * One paid grant, seen at the end of {@code SkillService.grantOnce} once the filters passed
     * (fake players, spectators, disabled skills, zero amounts never get here).
     *
     * @param baseAmount   what the call site asked for, before every multiplier (Homebuilder's share base)
     * @param beforeRested the grant after every multiplier but before the rested extra: the Hearth mirrors this
     * @param amount       what the player got, the rested extra included
     * @param banked       what was kept (0 for a skill with every star; mirror {@code beforeRested}, not this)
     * @param play         false for an operator's grant and a teaching payout, which feed nothing
     * @param aoe          an area tool's extra block (already paid at the reduced share)
     */
    public record Grant(ServerPlayer player, Skill skill, @Nullable String source, double baseAmount,
            float beforeRested, float amount, float banked, boolean play, boolean aoe) {

        /** The first-time bonus line ({@code first|...}), which the Hearth never mirrors. */
        public boolean firstTime() {
            return source != null && source.startsWith(dev.amman.proficiency.skill.FirstTimeKinds.FIRST_PREFIX);
        }
    }

    @FunctionalInterface
    public interface GrantListener {
        void onGrant(Grant grant);
    }

    @FunctionalInterface
    public interface PlayerListener {
        void onPlayer(ServerPlayer player);
    }

    @FunctionalInterface
    public interface DamageListener {
        /** Change the damage through the event; it is the shared {@code LivingIncomingDamageEvent}. */
        void onDamage(LivingIncomingDamageEvent event);
    }

    @FunctionalInterface
    public interface BlockPlacedListener {
        /** A block was placed (it is in the world now); {@code placer} is the entity, null for none. */
        void onPlaced(ServerLevel level, BlockPos pos, BlockState placed, @Nullable Entity placer);
    }

    @FunctionalInterface
    public interface BlockBrokenListener {
        /** A player is breaking a block and nothing canceled it; {@code state} is the block still there. */
        void onBroken(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player);
    }

    @FunctionalInterface
    public interface ItemEatenListener {
        /** A player finished eating; {@code food} is a copy of what was eaten. */
        void onEaten(ServerPlayer player, ItemStack food);
    }

    @FunctionalInterface
    public interface BlockUseListener {
        /**
         * A right click on a block, server side, before the block or the item gets it. Return
         * {@link InteractionResult#PASS} to let it through; anything else consumes the click (the
         * first non-PASS answer wins and later listeners are not asked).
         */
        InteractionResult onUse(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state,
                InteractionHand hand, ItemStack held);
    }

    @FunctionalInterface
    public interface HearthTickListener {
        /** Once a second for every ticking Hearth block entity. */
        void onSecond(ServerLevel level, BlockPos pos, HearthBlockEntity hearth);
    }

    @FunctionalInterface
    public interface DeathListener {
        /**
         * A player respawned after a death; their Hearth data was carried across and
         * {@link PlayerHearth#clearOnDeath} already ran. {@code talesLost} is what the bank held.
         */
        void onDeath(ServerPlayer player, PlayerHearth data, double talesLost);
    }

    @FunctionalInterface
    public interface RequestListener {
        /** A client asked for something (the panel's ledger tab, a fresh state). */
        void onRequest(ServerPlayer player, HearthRequestPayload request);
    }

    // ---- The hooks ------------------------------------------------------------------------------

    public static final Hook<GrantListener> GRANT = new Hook<>("grant");
    public static final Hook<PlayerListener> PLAYER_SECOND = new Hook<>("playerSecond");
    public static final Hook<DamageListener> INCOMING_DAMAGE = new Hook<>("incomingDamage");
    public static final Hook<PlayerListener> SLEEP = new Hook<>("sleep");
    public static final Hook<BlockPlacedListener> BLOCK_PLACED = new Hook<>("blockPlaced");
    public static final Hook<BlockBrokenListener> BLOCK_BROKEN = new Hook<>("blockBroken");
    public static final Hook<ItemEatenListener> ITEM_EATEN = new Hook<>("itemEaten");
    public static final Hook<BlockUseListener> BLOCK_USE = new Hook<>("blockUse");
    public static final Hook<HearthTickListener> HEARTH_TICK = new Hook<>("hearthTick");
    public static final Hook<DeathListener> DEATH = new Hook<>("death");
    public static final Hook<RequestListener> REQUEST = new Hook<>("request");

    private HearthHooks() {
    }

    private static boolean on() {
        return ProficiencyConfig.hearthEnabled();
    }

    // ---- Dispatchers (the game calls these) -----------------------------------------------------

    /** From {@code SkillService.grantOnce}, once per paid grant. */
    public static void onGrant(ServerPlayer player, Skill skill, @Nullable String source, double baseAmount,
            float beforeRested, float amount, float banked, boolean play, boolean aoe) {
        if (GRANT.isEmpty() || !on()) {
            return;
        }
        Grant grant = new Grant(player, skill, source, baseAmount, beforeRested, amount, banked, play, aoe);
        GRANT.each(l -> l.onGrant(grant));
    }

    /** From the once-a-second player loop in {@code PlayerLifecycleEvents}, for every online player. */
    public static void onPlayerSecond(ServerPlayer player) {
        PLAYER_SECOND.each(l -> l.onPlayer(player));
    }

    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!INCOMING_DAMAGE.isEmpty() && on()) {
            INCOMING_DAMAGE.each(l -> l.onDamage(event));
        }
    }

    /** A real sleep: the player is waking after {@code sleepCounter} reached 100. */
    public static void onSleep(ServerPlayer player) {
        if (!SLEEP.isEmpty() && on()) {
            SLEEP.each(l -> l.onPlayer(player));
        }
    }

    public static void onBlockPlaced(ServerLevel level, BlockPos pos, BlockState placed, @Nullable Entity placer) {
        if (!BLOCK_PLACED.isEmpty() && on()) {
            BLOCK_PLACED.each(l -> l.onPlaced(level, pos, placed, placer));
        }
    }

    public static void onBlockBroken(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player) {
        if (!BLOCK_BROKEN.isEmpty() && on()) {
            BLOCK_BROKEN.each(l -> l.onBroken(level, pos, state, player));
        }
    }

    public static void onItemEaten(ServerPlayer player, ItemStack food) {
        if (!ITEM_EATEN.isEmpty() && on()) {
            ITEM_EATEN.each(l -> l.onEaten(player, food));
        }
    }

    /** The first non-PASS answer, or PASS. */
    public static InteractionResult onBlockUse(ServerPlayer player, ServerLevel level, BlockPos pos,
            BlockState state, InteractionHand hand, ItemStack held) {
        if (BLOCK_USE.isEmpty() || !on()) {
            return InteractionResult.PASS;
        }
        for (BlockUseListener listener : BLOCK_USE.listeners()) {
            try {
                InteractionResult result = listener.onUse(player, level, pos, state, hand, held);
                if (result != null && result != InteractionResult.PASS) {
                    return result;
                }
            } catch (RuntimeException e) {
                BLOCK_USE.failed(e);
            }
        }
        return InteractionResult.PASS;
    }

    public static void onHearthSecond(ServerLevel level, BlockPos pos, HearthBlockEntity hearth) {
        if (!HEARTH_TICK.isEmpty() && on()) {
            HEARTH_TICK.each(l -> l.onSecond(level, pos, hearth));
        }
    }

    public static void onDeath(ServerPlayer player, PlayerHearth data, double talesLost) {
        DEATH.each(l -> l.onDeath(player, data, talesLost));
    }

    public static void onRequest(ServerPlayer player, HearthRequestPayload request) {
        if (!REQUEST.isEmpty() && on()) {
            REQUEST.each(l -> l.onRequest(player, request));
        }
    }

    // ---- The registry ---------------------------------------------------------------------------

    /** An ordered, thread-safe list of listeners of one kind. */
    public static final class Hook<L> {

        private final String name;
        private final List<L> listeners = new CopyOnWriteArrayList<>();
        private final AtomicInteger failures = new AtomicInteger();

        public Hook(String name) {
            this.name = name;
        }

        public void add(L listener) {
            listeners.add(listener);
        }

        public boolean remove(L listener) {
            return listeners.remove(listener);
        }

        public List<L> listeners() {
            return listeners;
        }

        public boolean isEmpty() {
            return listeners.isEmpty();
        }

        public String name() {
            return name;
        }

        /** Calls every listener; one that throws is logged and skipped. */
        public void each(Consumer<L> call) {
            for (L listener : listeners) {
                try {
                    call.accept(listener);
                } catch (RuntimeException e) {
                    failed(e);
                }
            }
        }

        void failed(RuntimeException e) {
            int n = failures.incrementAndGet();
            if (n <= 10 || n % 1000 == 0) {
                Proficiency.LOG.error("Hearth hook {} listener failed (failure {})", name, n, e);
            }
        }
    }
}
