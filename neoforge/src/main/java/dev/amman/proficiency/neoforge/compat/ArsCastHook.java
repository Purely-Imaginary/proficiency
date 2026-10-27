package dev.amman.proficiency.neoforge.compat;

import dev.amman.proficiency.event.ExpansionEvents;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingEvent;

/**
 * Spellcasting pays for real Ars Nouveau casts, by the mana each one costs, (2026-10-14). Ars posts SpellCastEvent (a LivingEvent) only
 * after its mana check, so opening the book, the Scribe's Table and an empty mana bar pay nothing. Looked up
 * by name so the jar neither needs nor loads Ars when it is absent. LOWEST and not receiving canceled: a
 * cast another mod or Ars itself cancels pays nothing.
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
            Class<? extends Event> type = (Class<? extends Event>) Class.forName(EVENT);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, type, event -> {
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
