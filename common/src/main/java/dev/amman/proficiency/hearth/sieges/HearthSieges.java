package dev.amman.proficiency.hearth.sieges;

import dev.amman.proficiency.hearth.HearthBlockEntity;
import dev.amman.proficiency.hearth.HearthHooks;
import dev.amman.proficiency.hearth.tales.HearthBell;
import dev.amman.proficiency.platform.bus.NeoForge;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

import java.util.Collection;
import java.util.List;

/**
 * The sieges lane's start: registers its Hearth listeners (docs/HEARTH-CONTRACT.md, lane "sieges").
 * Owns: everything siege: the play clock, the roll, the horn and trophy behaviour, waves, the siege
 * panel's server side.
 * Called once from {@code Hearth.init}.
 *
 * <ul>
 * <li>{@code HEARTH_TICK}: {@link SiegeService} (roll, countdown, nightfall, waves, douse, outcomes;
 * Warmth 0 by a smouldering Hearth).</li>
 * <li>{@code BLOCK_USE}, first in line: flint and steel relights a smouldering Hearth.</li>
 * <li>{@code BLOCK_BROKEN}: a Hearth broken mid-siege takes its siege with it.</li>
 * <li>{@code HearthBell.siegeLink} (the Tales lane's bell): the alarm reads the siege's mobs, who it
 * concerns, and pops the panel up.</li>
 * <li>Shared bus: {@link SiegeEvents} (play clock, siege mob deaths, loads and removals, griefing, sleep, login)
 * and {@link SiegeCommands} ({@code /proficiency hearth siege ...}).</li>
 * </ul>
 */
public final class HearthSieges {

    private HearthSieges() {
    }

    public static void init() {
        HearthHooks.HEARTH_TICK.add(SiegeService::onHearthSecond);
        // First, so a lane that answers every click on the Hearth (the panel) never eats a relight.
        HearthHooks.BLOCK_USE.listeners().add(0, SiegeEvents::onBlockUse);
        HearthHooks.BLOCK_BROKEN.add(SiegeEvents::onBlockBroken);
        NeoForge.EVENT_BUS.register(SiegeEvents.class);
        NeoForge.EVENT_BUS.register(SiegeCommands.class);
        // The Tales lane's bell: its alarm makes this siege's mobs glow and pops the panel up.
        HearthBell.siegeLink = new HearthBell.SiegeLink() {
            @Override
            public List<Mob> mobs(ServerLevel level, HearthBlockEntity hearth) {
                return SiegeService.siegeMobs(level, hearth);
            }

            @Override
            public Collection<ServerPlayer> concerned(ServerLevel level, HearthBlockEntity hearth) {
                return SiegeService.concerned(level, hearth);
            }

            @Override
            public void showPanel(ServerLevel level, HearthBlockEntity hearth, Collection<ServerPlayer> players) {
                SiegeService.showPanel(level, hearth, players);
            }
        };
    }
}
