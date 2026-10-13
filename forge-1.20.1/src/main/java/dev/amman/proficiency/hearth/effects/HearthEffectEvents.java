package dev.amman.proficiency.hearth.effects;

import dev.amman.proficiency.compat.LivingDamageEvent;
import dev.amman.proficiency.hearth.HearthRange;
import dev.amman.proficiency.skill.TelemetryService;
import dev.amman.proficiency.telemetry.Telemetry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * The effects lane's Forge-bus handlers, registered from {@link HearthEffects#init}.
 *
 * <ul>
 * <li><b>Hearty</b> has no handler here: there is no exhaustion event, so the one watcher
 * ({@code event.ExhaustionWatch}, shared with Night Owl, Lean Times and Long Haul) keeps only the factor
 * the per-player second set of each climb ({@link PlayerEffects#hungerFactor}). {@code HearthFoodDataMixin}
 * calls it at the head of the food tick, before vanilla spends 4.0 of it on a food point, and at its
 * tail, for what the tick added. 1.21.1's {@code hearty(player, food)} (deprecated there, no callers) is
 * not ported.</li>
 * <li><b>Fortified</b>: Forge's {@code LivingHurtEvent} (the port's choice; 1.21.1 cuts on
 * {@code INCOMING_DAMAGE}, before the shield).</li>
 * <li><b>Workshop</b>: the crafting-table refund, and the reversible-recipe set after a reload.</li>
 * <li><b>Temp HP</b>: a hit spends the Hearth's saved share first; {@code hearth_absorb} counts what
 * temp HP soaked away from home, boss or other.</li>
 * </ul>
 */
public final class HearthEffectEvents {

    private static int recipeCheck;

    private HearthEffectEvents() {
    }

    @SubscribeEvent
    public static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            HearthWorkshop.crafted(player, event.getCrafting(), event.getInventory());
        }
    }

    /**
     * Lowest: what the other handlers left is what absorption is about to soak (the port's
     * {@code compat.LivingDamageEvent.Pre}, after armour, before absorption). 1.20.1 has no
     * {@code max_absorption}, so the Hearth's part of the absorption is a saved number: the hit spends
     * it first ({@link HearthTempHp#spent}), and away from home it is counted as {@code hearth_absorb}.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getNewDamage() > 0)
                || !HearthTempHp.active(player)) {
            return;
        }
        float soaked = Math.min(event.getNewDamage(), player.getAbsorptionAmount());
        if (!(soaked > 0)) {
            return;
        }
        HearthTempHp.spent(player, soaked);
        if (HearthRange.homeOf(player) == null) {
            TelemetryService.hearth(player, Telemetry.HEARTH_ABSORB,
                    PlayerEffects.bossHit(event.getSource().getEntity()) ? "boss" : "other", "", soaked);
        }
    }

    /**
     * Fortified (docs/HEARTH-FORGE-CONTRACT.md: Forge's {@code LivingHurtEvent}, before armour, after
     * the shield): a mob's hit at home is cut by the boon times Warmth. Server side only.
     */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (!event.getEntity().level().isClientSide) {
            PlayerEffects.fortified(event);
        }
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        HearthWorkshop.checkRecipes(event.getServer());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
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
