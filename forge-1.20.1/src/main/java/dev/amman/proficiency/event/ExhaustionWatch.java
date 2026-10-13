package dev.amman.proficiency.event;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.hearth.effects.PlayerEffects;
import dev.amman.proficiency.skill.ExhaustionMath;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The one exhaustion watcher (integration, 2026-10-09). Vanilla has no exhaustion event, so things
 * that shave hunger watch the number: the Hearth's Hearty, Nightwalker's Night Owl and Endurance's
 * Lean Times scale whatever exhaustion climbed since the last look (only the product of their shares
 * stays, {@link ExhaustionMath}), and Running's Long Haul hands back a share of a sprint's charge
 * ({@link #refund}, scaled by the same product so the shares multiply instead of adding up).
 *
 * <p>Two looks per food tick, both from {@code HearthFoodDataMixin} (the Forge 1.20.1 copy of common's):
 * <ul>
 * <li>{@link #beforeFoodTick} at the head, before vanilla spends 4.0 of exhaustion on a food point, so
 * the climb that would cost the point is scaled first (a watcher after the tick saw that roll-over as a
 * drop and handed nothing back). It then records the value vanilla is about to leave
 * ({@link ExhaustionMath#afterRollOver});</li>
 * <li>{@link #afterFoodTick} at the tail, for what the tick itself added (natural regeneration costs
 * up to 6.0). Because the head recorded the post-roll-over value, this look sees exactly that
 * addition, also on a tick that took a food point.</li>
 * </ul>
 * One record of the last kept value per player: separate watchers each kept their own and read each
 * other's write-back as a climb or a drop.
 */
@Mod.EventBusSubscriber(modid = Proficiency.MOD_ID)
public final class ExhaustionWatch {

    private static final Map<UUID, Float> LAST = new ConcurrentHashMap<>();

    private ExhaustionWatch() {
    }

    /**
     * One look: scale the climb since the last look by every share that applies now, and record the
     * result. Harmless at any moment (a second look with no climb in between changes nothing); the food
     * tick's own looks are {@link #beforeFoodTick} and {@link #afterFoodTick}.
     */
    public static void look(ServerPlayer player, FoodData food) {
        UUID id = player.getUUID();
        float now = food.getExhaustionLevel();
        Float before = LAST.get(id);
        float kept = now;
        if (before != null && now > before) {
            kept = ExhaustionMath.scaleClimb(before, now, keep(player));
            if (kept != now) {
                food.setExhaustion(kept);
            }
        }
        LAST.put(id, kept);
    }

    /**
     * The look at the head of {@code FoodData.tick}: scales the climb, then records the value the tick
     * will leave once it has taken its roll-over, so {@link #afterFoodTick} only sees what the tick adds.
     */
    public static void beforeFoodTick(ServerPlayer player, FoodData food) {
        look(player, food);
        LAST.put(player.getUUID(), ExhaustionMath.afterRollOver(food.getExhaustionLevel()));
    }

    /** The look at the tail of {@code FoodData.tick}: what the tick added (regeneration) is scaled too. */
    public static void afterFoodTick(ServerPlayer player, FoodData food) {
        look(player, food);
    }

    /**
     * Long Haul's refund: hands back {@code amount} of exhaustion that vanilla charged for sprinting,
     * times this player's other shares (the charge itself was shaved to that share, so the refund is of
     * what stayed). The record moves down with the value, so the next look measures the next climb from
     * the lowered number and neither scales the refund again nor mistakes it for a drop.
     */
    public static void refund(ServerPlayer player, double amount) {
        FoodData food = player.getFoodData();
        float taken = ExhaustionMath.refundTaken(food.getExhaustionLevel(), amount, keep(player));
        if (!(taken > 0)) {
            return;
        }
        food.setExhaustion(food.getExhaustionLevel() - taken);
        LAST.computeIfPresent(player.getUUID(), (id, last) -> Math.max(0f, last - taken));
    }

    /** The share of a climb that stays for this player now: Hearty x Night Owl x Lean Times. */
    public static double keep(ServerPlayer player) {
        return ExhaustionMath.keep(PlayerEffects.hungerFactor(player), NightwalkerEvents.nightOwlKeep(player),
                TalentEnduranceEvents.leanTimesKeep(player));
    }

    /** Tests: forget a player's last look (the next look only records). */
    public static void forget(UUID player) {
        LAST.remove(player);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST.remove(event.getEntity().getUUID());
    }
}
