package dev.amman.proficiency.hearth.client;

import dev.amman.proficiency.hearth.BellRules;
import dev.amman.proficiency.hearth.HearthData;
import dev.amman.proficiency.hearth.HearthPool;
import dev.amman.proficiency.hearth.HearthState;
import dev.amman.proficiency.hearth.SiegeRules;
import dev.amman.proficiency.hearth.net.HearthGainPayload;
import dev.amman.proficiency.hearth.net.HearthHudPayload;
import dev.amman.proficiency.hearth.net.HearthLedgerPayload;
import dev.amman.proficiency.hearth.net.HearthPingPayload;
import dev.amman.proficiency.hearth.net.HearthStatePayload;
import dev.amman.proficiency.hearth.net.PoolRow;
import dev.amman.proficiency.hearth.net.SiegePanelPayload;
import dev.amman.proficiency.skill.Skill;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Fake Hearth payloads, the same on both sides: the client's demo ({@link HearthDemo}) feeds them to
 * itself, the operator command ({@link HearthDemoCommand}) sends them over the wire. No client class
 * here, so a dedicated server can load it.
 */
public final class HearthDemoData {

    /** Every scene {@link #payloads} knows. */
    public static final List<String> SCENES = List.of("home", "full", "gains", "tierup", "panel", "smoulder",
            "keepers", "away", "countdown", "waves", "outcome", "doused", "clear", "ping", "sealed");

    /** The pool and tier the {@code tierup} scene raises (Fortified 330 to 640: I to II). */
    public static final HearthPool TIER_UP_POOL = HearthPool.FORTIFIED;
    public static final int TIER_UP_TIER = 2;

    private HearthDemoData() {
    }

    /** A Hall with some Comfort and Character and its five pools at different tiers. */
    static HearthState hall(boolean smouldering) {
        HearthState state = new HearthState();
        state.setStage(4);
        state.setCharacter(20);
        // Six kinds of Comfort hold Comfort regen at III (half of 6); Character 20 holds Workshop's tier V
        // (the Gathering Place wants 24): one pool of each lock.
        state.setComfortKinds(Set.of("bed", "seat", "light", "fire", "books", "storage"));
        state.setCharge(HearthPool.HEARTY, 1880);
        state.setCharge(HearthPool.REGEN, 2900);
        state.setCharge(HearthPool.FORTIFIED, 330);
        state.setCharge(HearthPool.WORKSHOP, 5200);
        state.setCharge(HearthPool.HOMECOMING, 160);
        state.setSmouldering(smouldering);
        return state;
    }

    static HearthStatePayload state(int reason, HearthState state, long pos, String dimension, int defence,
            boolean siegesOn) {
        HearthData data = HearthData.DEFAULTS;
        int ceiling = state.ceiling(data);
        return new HearthStatePayload(reason, pos, dimension, state.stage(), ceiling, state.smouldering(),
                state.comfort(), List.copyOf(state.comfortKinds()), state.character(),
                ceiling < HearthData.TIERS ? data.character().forStage(ceiling + 1) : 0, PoolRow.of(state, data),
                defence, siegesOn, 0, siegesOn ? 6.4f : -1f, 24f, 8, 16, HearthStatePayload.Extra.of(state, data, 64));
    }

    private static UUID id(String name) {
        return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
    }

    private static HearthLedgerPayload.Source src(String id, float amount) {
        return new HearthLedgerPayload.Source(id, amount);
    }

    static HearthLedgerPayload ledger(long pos) {
        return new HearthLedgerPayload(pos, List.of(
                new HearthLedgerPayload.Row(id("Amman"), "Amman", List.of(620f, 120f, 40f, 310f, 60f),
                        List.of(1240f, 380f, 160f, 1900f, 90f),
                        List.of(src("mining", 410f), src("masonry", 300f), src("tales", 150f)),
                        List.of(src("smithing", 1400f), src("mining", 900f), src("masonry", 400f)), 14, 2),
                new HearthLedgerPayload.Row(id("Rowan"), "Rowan", List.of(380f, 260f, 0f, 120f, 140f),
                        List.of(640f, 380f, 170f, 1500f, 70f),
                        List.of(src("farming", 300f), src("decorating", 260f), src("feast", 120f)),
                        List.of(src("cooking", 1300f), src("farming", 600f), src("decorating", 380f)), 6, 2),
                new HearthLedgerPayload.Row(id("Wren"), "Wren", List.of(90f, 0f, 0f, 0f, 30f),
                        List.of(90f, 0f, 0f, 0f, 30f), List.of(src("fishing", 90f), src("social", 30f)),
                        List.of(src("fishing", 90f), src("social", 30f)), 0, 0)));
    }

    /** The payloads of one scene for a Hearth at {@code pos}; empty for an unknown scene. */
    public static List<Object> payloads(String scene, long pos, String dim, long game) {
        return switch (scene) {
            case "home" -> List.of(state(HearthStatePayload.ENTER, hall(false), pos, dim, 2, true),
                    new HearthHudPayload(true, pos, 0.62f, 1f / 60f, 0f, 0L, 0L, 0f,
                            HearthHudPayload.FLAG_STREAK_SAFE | HearthHudPayload.FLAG_EDGE_HOME));
            case "full" -> List.of(new HearthHudPayload(true, pos, 0.985f, 1f / 60f, 0f, 0L, 0L, 0f,
                    HearthHudPayload.FLAG_STREAK_SAFE | HearthHudPayload.FLAG_TEMP_HP));
            case "gains" -> List.of(new HearthGainPayload(List.of(
                    new HearthGainPayload.Gain(HearthPool.HEARTY.ordinal(), 60f, Skill.MINING.ordinal()),
                    new HearthGainPayload.Gain(HearthPool.WORKSHOP.ordinal(), 45f, Skill.SMITHING.ordinal()),
                    new HearthGainPayload.Gain(HearthPool.HOMECOMING.ordinal(), 20f, -1))));
            case "tierup" -> {
                HearthState state = hall(false);
                state.setCharge(HearthPool.FORTIFIED, 640);
                yield List.of(state(HearthStatePayload.TIER, state, pos, dim, 2, true),
                        new HearthGainPayload(List.of(new HearthGainPayload.Gain(HearthPool.FORTIFIED.ordinal(), 310f,
                                Skill.MASONRY.ordinal()))));
            }
            case "panel" -> List.of(state(HearthStatePayload.PANEL, hall(false), pos, dim, 2, true), ledger(pos));
            case "smoulder" -> List.of(state(HearthStatePayload.PANEL, hall(true), pos, dim, 0, false));
            case "sealed" -> {
                // The Hall becomes a Gathering Place: an open panel plays its unlock sweep.
                HearthState state = hall(false);
                state.setStage(5);
                state.setCharacter(26);
                yield List.of(state(HearthStatePayload.UPDATE, state, pos, dim, 2, true));
            }
            case "keepers" -> List.of(ledger(pos));
            case "away" -> List.of(new HearthHudPayload(false, pos, 0f, -1f / 30f, 0.22f, game - 4000, game + 14_000, 37f,
                    HearthHudPayload.FLAG_EDGE_SET_OUT));
            case "countdown" -> List.of(new SiegePanelPayload(SiegePanelPayload.COUNTDOWN, pos, dim, game + 22_000, 0, 3,
                    0, 0, 2, 3));
            case "waves" -> List.of(
                    new SiegePanelPayload(SiegePanelPayload.WAVES, pos, dim, game, 2, 3, 9, 0, 3, 3),
                    new SiegePanelPayload(SiegePanelPayload.WAVES, pos, dim, game, 2, 3, 7, 0, 3, 3));
            case "outcome" -> List.of(new SiegePanelPayload(SiegePanelPayload.OUTCOME, pos, dim, game, 3, 3, 0,
                    SiegeRules.Outcome.DEFENDED.ordinal(), 3, 3));
            case "doused" -> List.of(new SiegePanelPayload(SiegePanelPayload.OUTCOME, pos, dim, game, 3, 3, 0,
                    SiegeRules.Outcome.DOUSED.ordinal(), 3, 3));
            case "clear" -> List.of(new SiegePanelPayload(SiegePanelPayload.CLEAR, pos, dim, 0, 0, 0, 0, 0, 0, 0));
            case "ping" -> List.of(new HearthPingPayload(BellRules.Call.COME_HOME.ordinal(), pos, dim, 1200));
            default -> List.of();
        };
    }
}
