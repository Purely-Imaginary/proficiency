package dev.amman.proficiency.hearth.effects;

import dev.amman.proficiency.hearth.HearthHooks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.ToDoubleFunction;

/**
 * The effects lane's start (docs/HEARTH-CONTRACT.md, lane "effects"): Warmth, the boons, temp HP,
 * the streak hold, Workshop, Provisioned and readiness, the edge cues and the {@code hearth_hud}
 * send. Called once from {@code Hearth.init}.
 *
 * <ul>
 * <li>{@link PlayerEffects}: the per-player second ({@code PLAYER_SECOND}): Warmth, Comfort regen and
 * temp HP, Hearty's strength, Provisioned, the edge cues, the HUD; the sleep and damage hooks.</li>
 * <li>{@link HearthStreak}: the survival streak does not grow at home.</li>
 * <li>{@link HearthStations} and {@link HearthWorkshop}: station speed (furnaces, smokers, blast
 * furnaces, brewing stands, through two mixins) and the crafting-table refund.</li>
 * <li>{@link HearthEffectEvents}: the shared-bus handlers (the exhaustion watcher, crafting, the
 * temp-HP telemetry, clean-up).</li>
 * </ul>
 */
public final class HearthEffects {

    private static final List<ToDoubleFunction<ServerPlayer>> WARMTH_BONUS = new CopyOnWriteArrayList<>();
    private static boolean started;

    private HearthEffects() {
    }

    public static synchronized void init() {
        if (started) {
            return;
        }
        started = true;
        HearthHooks.PLAYER_SECOND.add(PlayerEffects::second);
        HearthHooks.SLEEP.add(PlayerEffects::slept);
        HearthHooks.INCOMING_DAMAGE.add(PlayerEffects::incoming);
        HearthHooks.DEATH.add(PlayerEffects::died);
        HearthHooks.HEARTH_TICK.add(HearthWorkshop::hearthSecond);
        MinecraftForge.EVENT_BUS.register(HearthEffectEvents.class);
        // Homebuilder's Warm Welcome (docs/HEARTH-CONTRACT.md, "Homebuilder API"): 10% faster a rank.
        addWarmthBonus(dev.amman.proficiency.homebuilder.HomebuilderApi::warmWelcome);
    }

    /**
     * Another lane's Warmth speed bonus (Homebuilder's Warm Welcome): {@code 0.10} fills Warmth 10%
     * faster. Bonuses add up and apply on top of Homecoming's readiness
     * ({@code WarmthMath.withSpeedBonus}).
     */
    public static void addWarmthBonus(ToDoubleFunction<ServerPlayer> bonus) {
        WARMTH_BONUS.add(bonus);
    }

    /** The summed Warmth speed bonus of every registered source, 0 for none. */
    static double warmthBonus(ServerPlayer player) {
        double sum = 0.0;
        for (ToDoubleFunction<ServerPlayer> bonus : WARMTH_BONUS) {
            try {
                double value = bonus.applyAsDouble(player);
                if (value > 0 && Double.isFinite(value)) {
                    sum += value;
                }
            } catch (RuntimeException e) {
                dev.amman.proficiency.Proficiency.LOG.error("Hearth Warmth bonus failed", e);
            }
        }
        return sum;
    }
}
