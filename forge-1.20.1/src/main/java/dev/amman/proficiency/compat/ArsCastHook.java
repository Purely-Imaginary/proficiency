package dev.amman.proficiency.compat;

import dev.amman.proficiency.event.ExpansionEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModList;

/**
 * Spellcasting pays for real Ars Nouveau casts, by the mana each one costs, (2026-10-14; port of the NeoForge ArsCastHook). Ars 4.x posts
 * SpellCastEvent (a LivingEvent) only after its mana check. Looked up by name; LOWEST, canceled casts skipped.
 */
public final class ArsCastHook {

    private static final String EVENT = "com.hollingsworth.arsnouveau.api.event.SpellCastEvent";

    private ArsCastHook() {
    }

    @SuppressWarnings("unchecked")
    public static void register() {
        if (!ModList.get().isLoaded("ars_nouveau")) {
            return;
        }
        try {
            Class<Event> type = (Class<Event>) Class.forName(EVENT);
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, type, event -> {
                if (event instanceof LivingEvent living && living.getEntity() instanceof Player player) {
                    ExpansionEvents.onRealSpellCast(player, manaCost(event));
                }
            });
            ExpansionEvents.markArsCastHooked();
        } catch (ClassNotFoundException | ClassCastException e) {
            dev.amman.proficiency.Proficiency.LOG.warn("Ars Nouveau is loaded but {} was not found; casts pay on click", EVENT);
        }
    }

    /** SpellCastEvent.spell.getCost(): the spell's base mana cost, read by name (Ars is not a compile dependency). */
    private static int manaCost(Object event) {
        try {
            Object spell = event.getClass().getField("spell").get(event);
            Object cost = spell == null ? null : spell.getClass().getMethod("getCost").invoke(spell);
            return cost instanceof Integer i ? i : 0;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 0;
        }
    }
}
