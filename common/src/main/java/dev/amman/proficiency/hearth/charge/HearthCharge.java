package dev.amman.proficiency.hearth.charge;

import dev.amman.proficiency.hearth.HearthBlockEntity;
import dev.amman.proficiency.hearth.HearthData;
import dev.amman.proficiency.hearth.HearthHooks;
import dev.amman.proficiency.hearth.HearthLedger;
import dev.amman.proficiency.hearth.HearthPool;
import dev.amman.proficiency.hearth.HearthRange;
import dev.amman.proficiency.hearth.HearthState;
import dev.amman.proficiency.hearth.Hearths;
import dev.amman.proficiency.hearth.SiegeRules;
import dev.amman.proficiency.hearth.net.HearthLedgerPayload;
import dev.amman.proficiency.hearth.net.HearthNet;
import dev.amman.proficiency.hearth.net.HearthRequestPayload;
import dev.amman.proficiency.platform.bus.NeoForge;
import dev.amman.proficiency.skill.RestedMath;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.TelemetryService;
import dev.amman.proficiency.telemetry.Telemetry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The charge lane (docs/HEARTH-CONTRACT.md, lane "charge"): everything that adds charge to a Hearth
 * goes through {@link #credit}, which applies the hourly cap, writes the ledger (player, pool,
 * source), counts the {@code hearth_charge} telemetry, queues the tracker's dots and watches the
 * tiers. Other lanes call it, and the siege API below, instead of touching the state's charge.
 *
 * <ul>
 * <li>{@link ChargeGrants}: the grant hook (mirror into pools), the hold queue, the combat filter.</li>
 * <li>{@link ChargeTick}: decay, holds coming due, the tier watch, tracker rows.</li>
 * <li>{@link ChargeTiers}: tier changes, the tier-up event and chat line.</li>
 * <li>{@link ChargeCommands}: {@code /proficiency hearth ledger}.</li>
 * </ul>
 * Called once from {@code Hearth.init}.
 */
public final class HearthCharge {

    /** A client may ask for the ledger once a second. */
    private static final long REQUEST_COOLDOWN_TICKS = 20L;
    private static final Map<UUID, Long> LAST_REQUEST = new HashMap<>();

    private HearthCharge() {
    }

    public static void init() {
        HearthHooks.GRANT.add(ChargeGrants::onGrant);
        HearthHooks.BLOCK_PLACED.add(ChargeGrants::onPlaced);
        HearthHooks.BLOCK_BROKEN.add(ChargeGrants::onBroken);
        HearthHooks.HEARTH_TICK.add(ChargeTick::onSecond);
        HearthHooks.REQUEST.add(HearthCharge::onRequest);
        NeoForge.EVENT_BUS.register(ChargeEvents.class);
    }

    static void forget(UUID player) {
        LAST_REQUEST.remove(player);
    }

    // ---- Adding charge --------------------------------------------------------------------------

    /**
     * Adds charge for an online player: {@link #credit(ServerLevel, HearthBlockEntity, UUID, String,
     * HearthPool, String, double, boolean, ServerPlayer, Skill)} with their name, and the tracker's
     * dots if they are home at this Hearth ({@code dotsFrom} is the skill whose bar they fly from,
     * null for none).
     */
    public static double credit(ServerLevel level, HearthBlockEntity hearth, ServerPlayer player, HearthPool pool,
            String source, double amount, boolean capped, @Nullable Skill dotsFrom) {
        boolean home = hearth.getBlockPos().equals(HearthRange.homeOf(player));
        return credit(level, hearth, player.getUUID(), player.getGameProfile().getName(), pool, source, amount,
                capped, home ? player : null, dotsFrom);
    }

    /**
     * Adds charge to one pool and returns what went in.
     *
     * @param source   the ledger source: a skill id, or {@link HearthLedger#SIEGE}, {@code TALES},
     *                 {@code CHRONICLE}, {@code FEAST}
     * @param capped   through the pool's rolling hourly cap (grants, Tales); false for bursts outside
     *                 it (a siege victory, a rated tale)
     * @param dotsTo   the player whose tracker shows the gain as dots, or null
     * @param dotsFrom the skill bar the dots launch from, or null
     */
    public static double credit(ServerLevel level, HearthBlockEntity hearth, UUID player, String name, HearthPool pool,
            String source, double amount, boolean capped, @Nullable ServerPlayer dotsTo, @Nullable Skill dotsFrom) {
        if (hearth == null || pool == null || player == null || !(amount > 0) || Double.isInfinite(amount)) {
            return 0.0;
        }
        HearthState state = hearth.state();
        HearthData data = Hearths.data();
        String src = source == null || source.isEmpty() ? "unknown" : source;
        String who = name == null ? "" : name;
        int tierBefore = state.chargeTier(pool, data);
        double taken = capped ? state.addCapped(pool, amount, level.getGameTime(), data) : state.addUncapped(pool, amount);
        if (capped && taken < amount) {
            TelemetryService.hearth(player, who, Telemetry.HEARTH_CHARGE, pool.id(), ChargeRules.CAPPED_SOURCE,
                    amount - taken);
        }
        if (!(taken > 0)) {
            return 0.0;
        }
        state.ledger().add(player, who, pool, src, taken, RestedMath.today());
        dev.amman.proficiency.haul.HaulService.onCharge(level.getServer(), player, taken);
        TelemetryService.hearth(player, who, Telemetry.HEARTH_CHARGE, pool.id(), src, taken);
        if (dotsTo != null) {
            HearthNet.queueGain(dotsTo, pool, (float) taken, dotsFrom);
        }
        hearth.setChanged();
        ChargeTick.markTrackerDirty(hearth);
        // Charge moves a tier only when the pool's charge tier rose; anything else (the ceiling,
        // Comfort) is the Hearth tick's once-a-second check. A raid kill credits five pools and a
        // siege burst players x five: this keeps the full check to the credits that cross a line.
        if (state.chargeTier(pool, data) > tierBefore) {
            ChargeTiers.check(level, hearth);
        }
        return taken;
    }

    /**
     * Takes {@code fraction} of every pool's charge (a doused siege: {@code siege.dousedLoss}). It
     * comes off the pools, never the ledger.
     */
    public static void cut(ServerLevel level, HearthBlockEntity hearth, double fraction) {
        if (!(fraction > 0) || Double.isInfinite(fraction)) {
            return;
        }
        HearthState state = hearth.state();
        double keep = Math.max(0.0, 1.0 - Math.min(1.0, fraction));
        for (HearthPool pool : HearthPool.VALUES) {
            state.setCharge(pool, state.charge(pool) * keep);
        }
        hearth.setChanged();
        ChargeTick.markTrackerDirty(hearth);
        ChargeTiers.check(level, hearth);
    }

    // ---- The siege lane's calls -----------------------------------------------------------------

    /** A siege mob killed by this player (or their pet): the ledger's siege-kill count. */
    public static void siegeKill(HearthBlockEntity hearth, UUID player, String name) {
        hearth.state().ledger().addSiegeKill(player, name);
        hearth.setChanged();
    }

    /** A siege was defended: every player who was there gets a defended siege in the ledger. */
    public static void siegeDefended(HearthBlockEntity hearth, Collection<ServerPlayer> present) {
        for (ServerPlayer player : present) {
            hearth.state().ledger().addSiegeDefended(player.getUUID(), player.getGameProfile().getName());
        }
        hearth.setChanged();
    }

    /**
     * The victory burst ({@link SiegeRules#burst}, outside the hourly cap) into every pool, filed in
     * the ledger under {@code siege} and split by siege kills; with no kills, evenly over the players
     * who were there. Nobody to file it under means no burst. Returns the charge added.
     *
     * @param streak the defence streak including this defence
     */
    public static double siegeBurst(ServerLevel level, HearthBlockEntity hearth, Map<UUID, Integer> kills,
            List<ServerPlayer> present, int streak) {
        return siegeBurst(level, hearth, kills, present, streak, 1.0);
    }

    /** As above, the burst times {@code scale} (the siege level against the ceiling, SiegeRules.burstScale). */
    public static double siegeBurst(ServerLevel level, HearthBlockEntity hearth, Map<UUID, Integer> kills,
            List<ServerPlayer> present, int streak, double scale) {
        Map<UUID, Integer> weights = new LinkedHashMap<>();
        boolean anyKills = false;
        if (kills != null) {
            for (Map.Entry<UUID, Integer> entry : kills.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null && entry.getValue() > 0) {
                    weights.put(entry.getKey(), entry.getValue());
                    anyKills = true;
                }
            }
        }
        if (!anyKills && present != null) {
            for (ServerPlayer player : present) {
                weights.put(player.getUUID(), 1);
            }
        }
        if (weights.isEmpty()) {
            return 0.0;
        }
        Map<UUID, String> names = new HashMap<>();
        Map<UUID, ServerPlayer> online = new HashMap<>();
        if (present != null) {
            for (ServerPlayer player : present) {
                names.put(player.getUUID(), player.getGameProfile().getName());
            }
        }
        for (UUID id : weights.keySet()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player != null) {
                online.put(id, player);
                names.putIfAbsent(id, player.getGameProfile().getName());
            }
            HearthLedger.Entry known = hearth.state().ledger().get(id);
            names.putIfAbsent(id, known == null || known.name() == null ? "" : known.name());
        }
        HearthData data = Hearths.data();
        double[] totals = new double[HearthPool.COUNT];
        for (HearthPool pool : HearthPool.VALUES) {
            totals[pool.ordinal()] = SiegeRules.burst(hearth.state().charge(pool), streak, data, scale);
        }
        double added = 0.0;
        BlockPos pos = hearth.getBlockPos();
        for (HearthPool pool : HearthPool.VALUES) {
            for (Map.Entry<UUID, Double> part : ChargeRules.split(totals[pool.ordinal()], weights).entrySet()) {
                ServerPlayer player = online.get(part.getKey());
                boolean dots = player != null && pos.equals(HearthRange.homeOf(player));
                added += credit(level, hearth, part.getKey(), names.get(part.getKey()), pool, HearthLedger.SIEGE,
                        part.getValue(), false, dots ? player : null, null);
            }
        }
        return added;
    }

    // ---- The ledger to a client -----------------------------------------------------------------

    /** The panel's "Hearth-keepers" packet: both windows, everyone who fed it (rolled to today first). */
    public static HearthLedgerPayload ledgerPayload(HearthBlockEntity hearth) {
        HearthLedger ledger = rollToToday(hearth);
        List<HearthLedgerPayload.Row> rows = new ArrayList<>();
        for (UUID id : KeeperRows.everyone(ledger, HearthLedgerPayload.MAX_ROWS)) {
            KeeperRows.Row week = KeeperRows.row(ledger, id, null, HearthLedger.Window.WEEK);
            KeeperRows.Row life = KeeperRows.row(ledger, id, null, HearthLedger.Window.LIFETIME);
            rows.add(new HearthLedgerPayload.Row(id, clip(life.name()), floats(week.perPool()), floats(life.perPool()),
                    sources(week.sources()), sources(life.sources()), life.siegeKills(), life.siegesDefended()));
        }
        return new HearthLedgerPayload(hearth.packedPos(), List.copyOf(rows));
    }

    /**
     * The Hearth's ledger, its week rolled to today. Marks the block entity changed only when the day
     * moved, so reading the ledger (a command, the panel) does not dirty the chunk.
     */
    static HearthLedger rollToToday(HearthBlockEntity hearth) {
        HearthLedger ledger = hearth.state().ledger();
        long day = ledger.currentDay();
        ledger.rollTo(RestedMath.today());
        if (ledger.currentDay() != day) {
            hearth.setChanged();
        }
        return ledger;
    }

    /** Sends the ledger packet; call it when the panel opens (the client asks with {@code REQUEST LEDGER} too). */
    public static void sendLedger(ServerPlayer player, HearthBlockEntity hearth) {
        HearthNet.sendLedger(player, ledgerPayload(hearth));
    }

    /** May this player read this Hearth's ledger: in its range, home there, or an operator. */
    public static boolean mayRead(ServerPlayer player, HearthBlockEntity hearth) {
        BlockPos pos = hearth.getBlockPos();
        return player.hasPermissions(2) || player.level() == hearth.getLevel()
                && (HearthRange.inRange(player.serverLevel(), pos, player.position()) || pos.equals(HearthRange.homeOf(player)));
    }

    /** The {@code REQUEST} listener: {@code LEDGER} answers with the packet, at most once a second. */
    static void onRequest(ServerPlayer player, HearthRequestPayload request) {
        if (request.what() != HearthRequestPayload.LEDGER) {
            return;
        }
        long now = player.serverLevel().getGameTime();
        Long last = LAST_REQUEST.get(player.getUUID());
        if (last != null && now >= last && now - last < REQUEST_COOLDOWN_TICKS) {
            return;
        }
        LAST_REQUEST.put(player.getUUID(), now);
        BlockPos pos = BlockPos.of(request.pos());
        HearthBlockEntity hearth = HearthRange.entity(player.serverLevel(), pos);
        if (hearth != null && mayRead(player, hearth)) {
            sendLedger(player, hearth);
        }
    }

    private static List<Float> floats(double[] values) {
        List<Float> out = new ArrayList<>(values.length);
        for (double value : values) {
            out.add((float) value);
        }
        return List.copyOf(out);
    }

    private static List<HearthLedgerPayload.Source> sources(List<HearthLedger.SourceTotal> sources) {
        List<HearthLedgerPayload.Source> out = new ArrayList<>();
        for (HearthLedger.SourceTotal source : sources) {
            if (out.size() >= HearthLedgerPayload.MAX_SOURCES) {
                break;
            }
            out.add(new HearthLedgerPayload.Source(clip(source.source()), (float) source.total()));
        }
        return List.copyOf(out);
    }

    private static String clip(String text) {
        String safe = text == null ? "" : text;
        return safe.length() > 64 ? safe.substring(0, 64) : safe;
    }
}
