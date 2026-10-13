package dev.amman.proficiency.hearth.tales;

import dev.amman.proficiency.hearth.HearthAttachments;
import dev.amman.proficiency.hearth.HearthBlockEntity;
import dev.amman.proficiency.hearth.HearthData;
import dev.amman.proficiency.hearth.HearthEvents;
import dev.amman.proficiency.hearth.HearthHooks;
import dev.amman.proficiency.hearth.HearthLedger;
import dev.amman.proficiency.hearth.HearthPool;
import dev.amman.proficiency.hearth.HearthRange;
import dev.amman.proficiency.hearth.HearthState;
import dev.amman.proficiency.hearth.Hearths;
import dev.amman.proficiency.hearth.PlayerHearth;
import dev.amman.proficiency.hearth.TaleTrip;
import dev.amman.proficiency.hearth.TalesMath;
import dev.amman.proficiency.hearth.net.HearthNet;
import dev.amman.proficiency.hearth.charge.HearthCharge;
import dev.amman.proficiency.hearth.net.HearthTalesPayload;
import dev.amman.proficiency.config.ProficiencyConfig;
import dev.amman.proficiency.skill.TelemetryService;
import dev.amman.proficiency.telemetry.Telemetry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tales (docs/HEARTH-SPEC.md, "Setting out and coming home"). Movement and Expedition XP earned away
 * from home is banked as Tales ({@code tales.share} of it, in {@link PlayerHearth#tales}); coming
 * home to a Hearth you fed pays the bank into the Tales pool (Homecoming) once, with a line about the
 * trip; a death loses the bank.
 *
 * <p>A trip is the stretch between leaving a lit Hearth's range and coming home to one you fed. Its
 * state lives in the player's Hearth data, so a restart does not lose it: the flag {@value #AWAY},
 * the walked-distance reading it began at ({@value #TRIP}) and up to {@link TaleTrip#MAX_PLACES}
 * place flags ({@value #PLACE}{@code <language key>}) for what it found.
 */
public final class TalesBank {

    public static final String AWAY = "tales.away";
    public static final String PLACE = "tales.place.";
    public static final String TRIP = "tales.tripCm";

    /** The statistics a trip's blocks are the sum of: on foot, crouching, swimming and wading. */
    private static final ResourceLocation[] WALKED = {Stats.WALK_ONE_CM, Stats.SPRINT_ONE_CM, Stats.CROUCH_ONE_CM,
            Stats.WALK_ON_WATER_ONE_CM, Stats.WALK_UNDER_WATER_ONE_CM, Stats.SWIM_ONE_CM};

    /**
     * A Hearth the player stands in range of but never fed: asked again only every {@link #UNFED_RECHECK_TICKS} (5 s),
     * not every second for as long as they stay (the trip goes on to the Hearth they did feed). Not saved.
     */
    private record Unfed(String hearth, long until) {
    }

    public static final long UNFED_RECHECK_TICKS = 100L;

    private static final Map<UUID, Unfed> UNFED = new ConcurrentHashMap<>();

    private TalesBank() {
    }

    /** Drops the unfed-Hearth memory (server stopping; a test that wants the Hearth asked again at once). */
    public static void forget() {
        UNFED.clear();
    }

    // ---- Banking ----------------------------------------------------------------------------------

    /** A paid grant: away from a lit Hearth, a Tales feed's XP is banked and a discovery is remembered. */
    public static void onGrant(HearthHooks.Grant grant) {
        if (!grant.play() || grant.firstTime() || grant.aoe()) {
            return;
        }
        HearthData data = Hearths.data();
        boolean place = TaleTrip.kindOf(grant.source()) != null;
        boolean banks = data.tales().banks(grant.skill());
        if (!banks && !place) {
            return;
        }
        ServerPlayer player = grant.player();
        if (HearthRange.isHome(player)) {
            return;
        }
        PlayerHearth hearth = HearthAttachments.of(player);
        markAway(player, hearth);
        if (place) {
            rememberPlace(hearth, grant.source());
        }
        if (!banks) {
            return;
        }
        double add = TalesMath.banked(grant.skill(), grant.beforeRested(), HearthTales.storyteller.applyAsDouble(player),
                data);
        if (!(add > 0.0)) {
            return;
        }
        double before = hearth.tales();
        hearth.addTales(add);
        TelemetryService.hearth(player, Telemetry.HEARTH_TALES, "banked", grant.skill().id(), add);
        if ((long) hearth.tales() != (long) before) {
            HearthNet.markHudDirty(player);
        }
    }

    /** The trip began (once): the flag, the walked distance so far and a clean list of places. */
    public static void markAway(ServerPlayer player, PlayerHearth hearth) {
        if (!hearth.flag(AWAY)) {
            hearth.setFlag(AWAY, true);
            hearth.setMark(TRIP, walkedCm(player));
            hearth.clearFlags(PLACE);
        }
    }

    /** A found place is kept; a full trip lets a better kind (a stronghold, the Nether) push out the weakest (a biome). */
    private static void rememberPlace(PlayerHearth hearth, String source) {
        TaleTrip.Admission admission = TaleTrip.admit(placesOf(hearth), source);
        if (admission.drop() != null) {
            hearth.setFlag(PLACE + admission.drop(), false);
        }
        if (admission.add()) {
            hearth.setFlag(PLACE + source, true);
        }
    }

    public static List<String> placesOf(PlayerHearth hearth) {
        List<String> places = new ArrayList<>();
        for (String flag : hearth.flags()) {
            if (flag.startsWith(PLACE)) {
                places.add(flag.substring(PLACE.length()));
            }
        }
        return places;
    }

    /** The player's walked distance in centimetres, the sum of the statistics a trip counts. */
    public static long walkedCm(ServerPlayer player) {
        long sum = 0L;
        for (ResourceLocation id : WALKED) {
            sum += player.getStats().getValue(Stats.CUSTOM.get(id));
        }
        return sum;
    }

    public static void endTrip(PlayerHearth hearth) {
        hearth.setFlag(AWAY, false);
        hearth.clearMark(TRIP);
        hearth.clearFlags(PLACE);
    }

    // ---- Coming home ------------------------------------------------------------------------------

    /** The per-player second: away marks the trip, arriving at a lit Hearth pays it. */
    public static void onSecond(ServerPlayer player) {
        if (!ProficiencyConfig.hearthEnabled()) {
            return;
        }
        PlayerHearth hearth = HearthAttachments.of(player);
        HearthBlockEntity home = HearthRange.homeEntity(player);
        if (home == null || home.state().smouldering()) {
            markAway(player, hearth);
            return;
        }
        if (hearth.flag(AWAY)) {
            arrive(player, hearth, home);
        }
    }

    /** Whether the player ever added charge to this Hearth: the Hearth they may tell their tales to. */
    public static boolean fed(HearthBlockEntity hearth, ServerPlayer player) {
        return hearth.state().ledger().total(player.getUUID(), null, HearthLedger.Window.LIFETIME) > 0.0;
    }

    /**
     * The player stands at a lit Hearth after a trip. A bank under one charge is not worth telling and
     * ends the trip; a Hearth they never fed hears nothing (the trip goes on to the one they did); a
     * Hearth whose hourly cap is full hears nothing yet (the bank keeps and the trip ends: the next
     * homecoming pays it).
     */
    public static void arrive(ServerPlayer player, PlayerHearth hearth, HearthBlockEntity home) {
        double bank = hearth.tales();
        if (bank < TalesMath.MIN_PAYOUT) {
            endTrip(hearth);
            return;
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        Unfed unfed = UNFED.get(player.getUUID());
        if (unfed != null && now < unfed.until() && unfed.hearth().equals(home.key())) {
            return;
        }
        if (!fed(home, player)) {
            UNFED.put(player.getUUID(), new Unfed(home.key(), now + UNFED_RECHECK_TICKS));
            return;
        }
        UNFED.remove(player.getUUID());
        HearthData data = Hearths.data();
        HearthState state = home.state();
        if (TalesMath.fits(state, bank, now, data) < TalesMath.MIN_PAYOUT) {
            endTrip(hearth);
            player.sendSystemMessage(TalesText.capped());
            return;
        }
        long blocks = TaleTrip.blocks(hearth.mark(TRIP, -1L), walkedCm(player));
        List<String> places = TaleTrip.highlights(placesOf(hearth), TaleTrip.NAMED);
        // The one way to add charge: the hourly cap, the ledger (source tales), hearth_charge telemetry, the
        // tracker dots and the tier watch all live in HearthCharge.credit.
        TalesMath.Settlement settled = TalesMath.settle(bank, data,
                (pool, amount) -> HearthCharge.credit(level, home, player, pool, HearthLedger.TALES, amount, true, null));
        hearth.setTales(settled.kept());
        endTrip(hearth);
        home.setChanged();
        if (!settled.any()) {
            // Nothing was credited (the pools had no room after all): the last real homecoming's payout, and the
            // flat Chronicle bonus that goes with it, stay as they were.
            return;
        }
        hearth.notePaid(settled.paid());
        TelemetryService.hearth(player, Telemetry.HEARTH_TALES, "paid", "", settled.paid());
        HearthNet.markHudDirty(player);

        boolean every = settled.added().size() > 1;
        HearthPool pool = every ? data.talesPool() : settled.added().keySet().iterator().next();
        player.sendSystemMessage(TalesText.paid(blocks, places, settled.paid(),
                every ? TalesText.everyPool() : TalesText.poolName(pool)));
        if (settled.kept() >= TalesMath.MIN_PAYOUT) {
            player.sendSystemMessage(TalesText.kept(settled.kept()));
        }
        TalesFx.paid(player, home.getBlockPos());
        HearthTales.send(player, new HearthTalesPayload(HearthTalesPayload.PAID, pool.ordinal(),
                (float) settled.paid(), (int) Math.min(blocks, Integer.MAX_VALUE), -1, places, ""));
        HearthEvents.TALES_PAID.fire(new HearthEvents.TalesPaid(level, home.getBlockPos(), player, settled.paid()));
    }

    // ---- Dying away -------------------------------------------------------------------------------

    /** A death lost the bank ({@code clearOnDeath} already emptied it); the trip is over too. */
    public static void onDeath(ServerPlayer player, PlayerHearth hearth, double lost) {
        endTrip(hearth);
        if (lost > 0.0) {
            TelemetryService.hearth(player, Telemetry.HEARTH_TALES, "lost", "", lost);
            HearthNet.markHudDirty(player);
            if (lost >= TalesMath.MIN_PAYOUT) {
                player.sendSystemMessage(TalesText.lost(lost));
            }
        }
    }
}
