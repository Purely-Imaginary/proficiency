package dev.amman.proficiency.platform.hooks;

import dev.amman.proficiency.platform.bus.NeoForge;

/**
 * Every class NeoForge found by its {@code @EventBusSubscriber} scan, listed by hand because
 * Fabric has no scan. Adding a handler class means adding it here.
 */
public final class Subscribers {

    private Subscribers() {
    }

    public static void registerCommon() {
        for (Class<?> holder : new Class<?>[] {
                dev.amman.proficiency.command.SkillCommand.class,
                dev.amman.proficiency.event.PlayerLifecycleEvents.class,
                dev.amman.proficiency.event.CombatEvents.class,
                dev.amman.proficiency.event.CraftingEvents.class,
                dev.amman.proficiency.event.ExpansionEvents.class,
                dev.amman.proficiency.event.GatheringEvents.class,
                dev.amman.proficiency.event.MovementEvents.class,
                dev.amman.proficiency.event.TalentCraftingEvents.class,
                dev.amman.proficiency.event.TalentExpansionEvents.class,
                dev.amman.proficiency.event.TalentGatheringEvents.class,
                dev.amman.proficiency.event.TalentMeleeEvents.class,
                dev.amman.proficiency.event.TalentMovementEvents.class,
                dev.amman.proficiency.event.TalentRangedEvents.class,
                dev.amman.proficiency.event.EnduranceEvents.class,
                dev.amman.proficiency.event.TalentEnduranceEvents.class,
                dev.amman.proficiency.event.NightwalkerEvents.class,
                dev.amman.proficiency.event.CourageEvents.class,
                dev.amman.proficiency.event.GuardianEvents.class,
                dev.amman.proficiency.event.ChargerEvents.class,
                dev.amman.proficiency.event.TacticianEvents.class,
                dev.amman.proficiency.item.SpecialItemEvents.class,
                dev.amman.proficiency.recipe.StationEvents.class,
        }) {
            NeoForge.EVENT_BUS.register(holder);
        }
    }

    /** Client-dist subscribers; only called from the client entrypoint. */
    public static void registerClient() {
        NeoForge.EVENT_BUS.register(dev.amman.proficiency.client.ClientInputEvents.class);
        NeoForge.EVENT_BUS.register(dev.amman.proficiency.client.SkillHud.class);
        NeoForge.EVENT_BUS.register(dev.amman.proficiency.client.DiscoveryBanner.class);
        NeoForge.EVENT_BUS.register(dev.amman.proficiency.client.XpFeedHud.class);
        NeoForge.EVENT_BUS.register(dev.amman.proficiency.client.DarkSight.class);
        NeoForge.EVENT_BUS.register(dev.amman.proficiency.client.CalledShotMarks.class);
    }
}
