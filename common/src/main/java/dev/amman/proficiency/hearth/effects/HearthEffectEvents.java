package dev.amman.proficiency.hearth.effects;

import dev.amman.proficiency.hearth.HearthRange;
import dev.amman.proficiency.platform.bus.EventPriority;
import dev.amman.proficiency.platform.bus.SubscribeEvent;
import dev.amman.proficiency.platform.event.entity.living.LivingDamageEvent;
import dev.amman.proficiency.platform.event.entity.player.PlayerEvent;
import dev.amman.proficiency.platform.event.server.ServerStartedEvent;
import dev.amman.proficiency.platform.event.server.ServerStoppingEvent;
import dev.amman.proficiency.platform.event.tick.ServerTickEvent;
import dev.amman.proficiency.skill.TelemetryService;
import dev.amman.proficiency.telemetry.Telemetry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;

/**
 * The effects lane's shared-bus handlers, registered from {@link HearthEffects#init} (not from
 * {@code Subscribers}, per the contract).
 *
 * <ul>
 * <li><b>Hearty</b>: there is no exhaustion event, so the one watcher ({@code event.ExhaustionWatch},
 * shared with Night Owl, Lean Times and Long Haul) keeps only the factor the per-player second set of
 * each climb. It looks at the head of the food tick (a shared mixin), before vanilla spends 4.0 of it
 * on a food point, and at its tail, for what the tick added.</li>
 * <li><b>Workshop</b>: the crafting-table refund, and the reversible-recipe set after a reload.</li>
 * <li><b>Telemetry</b> {@code hearth_absorb}: temp HP spent away from home, boss or other.</li>
 * </ul>
 */
public final class HearthEffectEvents {

    private static int recipeCheck;

    private HearthEffectEvents() {
    }

    /**
     * Hearty's step, kept only because the contract lists it (docs/HEARTH-CONTRACT.md never loses an
     * entry); nothing calls it. The one exhaustion watcher
     * ({@link dev.amman.proficiency.event.ExhaustionWatch}) scales every climb by Hearty's share
     * ({@link PlayerEffects#hungerFactor}) times Night Owl's and Lean Times', from the head and the tail
     * of {@code FoodData.tick} ({@code HearthFoodDataMixin}, both loaders). This is a plain look: harmless
     * at any moment, a second one with no climb in between changes nothing.
     *
     * @deprecated the mixin does the looking; call {@code ExhaustionWatch.look} if a look is ever needed
     */
    @Deprecated
    public static void hearty(ServerPlayer player, FoodData food) {
        dev.amman.proficiency.event.ExhaustionWatch.look(player, food);
    }

    @SubscribeEvent
    public static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            HearthWorkshop.crafted(player, event.getCrafting(), event.getInventory());
        }
    }

    /** Lowest: what the other handlers left is what absorption is about to soak. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getNewDamage() > 0)
                || !HearthTempHp.active(player) || HearthRange.homeOf(player) != null) {
            return;
        }
        float soaked = Math.min(event.getNewDamage(), player.getAbsorptionAmount());
        if (soaked > 0) {
            TelemetryService.hearth(player, Telemetry.HEARTH_ABSORB,
                    PlayerEffects.bossHit(event.getSource().getEntity()) ? "boss" : "other", "", soaked);
        }
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        HearthWorkshop.checkRecipes(event.getServer());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++recipeCheck >= 20) {
            recipeCheck = 0;
            HearthWorkshop.checkRecipes(event.getServer());
            HearthStations.prune(event.getServer());
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerEffects.loggedIn(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PlayerEffects.forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        PlayerEffects.forgetAll();
        HearthWorkshop.forget();
    }
}
