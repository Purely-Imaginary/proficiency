package dev.amman.proficiency.homebuilder;

import dev.amman.proficiency.hearth.HearthHooks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraft.server.level.ServerPlayer;

/**
 * The Homebuilder lane's start on Forge 1.20.1 (common's {@code HomebuilderInit}; docs/HOMEBUILDER-SPEC.md,
 * docs/HEARTH-FORGE-CONTRACT.md, lane forge-extras): registers the
 * skill's listeners: the share of grants earned at home and Hearthglow ({@code GRANT}), the
 * milestones ({@code HearthEvents}), the passive, House Proud's sharing and the pot's payout
 * ({@code PLAYER_SECOND}), Scaffolder ({@code INCOMING_DAMAGE}), Night Watch ({@code HEARTH_TICK}),
 * the death cleanup ({@code DEATH}), and its own Forge-bus handlers ({@link HomebuilderEvents}:
 * magnet tick, the Homeward channel and the hits that break it, commands, login).
 * Called once from {@code Hearth.init}.
 */
public final class HomebuilderInit {

    private HomebuilderInit() {
    }

    public static void init() {
        // The charge lane's GRANT listener is added first (Hearth.init order), so Hearthglow sees a
        // grant after the Hearth mirrored it.
        HearthHooks.GRANT.add(HomebuilderXp::onGrant);
        HearthHooks.GRANT.add(Hearthglow::onGrant);
        // Reach: Homebuilder XP earned in range widens that Hearth's range (docs/HEARTH-REACH-SPEC.md).
        HearthHooks.GRANT.add(HearthReachCharge::onGrant);
        HearthHooks.HEARTH_TICK.add(HearthReachCharge::onHearthSecond);
        HearthHooks.PLAYER_SECOND.add(HomebuilderInit::onPlayerSecond);
        HearthHooks.INCOMING_DAMAGE.add(HomePassive::onDamage);
        HearthHooks.HEARTH_TICK.add(NightWatch::onHearthSecond);
        HearthHooks.DEATH.add((player, data, talesLost) -> onDeath(player));
        HomebuilderXp.init();
        HouseProud.init();
        MinecraftForge.EVENT_BUS.register(HomebuilderEvents.class);
    }

    /** The per-player second (it runs with the Hearth switched off too, so the modifiers come off). */
    static void onPlayerSecond(ServerPlayer player) {
        HomebuilderXp.tick(player);
        HouseProud.expire(player);
        HouseProud.tick(player);
        HomePassive.tick(player);
        NightWatch.tick(player);
        Homeward.noteFed(player, player.level().getGameTime() / 20L);
    }

    /** A death: the unpaid pot goes with the XP bars, a channel breaks (the client is told), the glow ends. */
    static void onDeath(ServerPlayer player) {
        HomebuilderXp.onDeath(player.getUUID());
        Homeward.cancel(player, dev.amman.proficiency.homebuilder.net.HomebuilderPayload.END_HURT);
        Homeward.forget(player.getUUID());
        NightWatch.forget(player.getUUID());
        HomePassive.apply(player, 0.0);
    }
}
