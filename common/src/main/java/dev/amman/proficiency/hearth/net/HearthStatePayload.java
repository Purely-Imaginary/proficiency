package dev.amman.proficiency.hearth.net;

import dev.amman.proficiency.Proficiency;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * A Hearth as its panel, HUD glyph and tracker show it. Sent on entering range ({@link #ENTER}), on a
 * tier change ({@link #TIER}), when the panel opens ({@link #PANEL}: the client opens the screen) and
 * after any other change worth showing ({@link #UPDATE}). Never on a timer. Server: Stages (enter,
 * panel, tier) and Charge (tier). Client: the client lane through {@code HearthClient.onState}.
 *
 * @param range         the cylinder: radius, below, above (for the range ring)
 * @param comfortKinds  the kinds present now; the client names the missing ones
 * @param characterNext the Character the next stage needs (0 when none)
 * @param siegesOn      random sieges are on (the first horn was blown here)
 * @param playHoursSinceSiege play hours since the last siege ended, -1 when none
 * @param extra         what the client needs from the server's data to draw it right (thresholds, boon
 *                      numbers, the comfort kinds there are, the next offering); {@link Extra#NONE} lets the
 *                      client fall back to the shipped defaults. Added by the client lane, written last on
 *                      the wire.
 */
public record HearthStatePayload(int reason, long pos, String dimension, int stage, int ceiling,
        boolean smouldering, int comfort, List<String> comfortKinds, int character, int characterNext,
        List<PoolRow> pools, int defenceStreak, boolean siegesOn, int siegePhase, float playHoursSinceSiege,
        float radius, int below, int above, Extra extra) implements CustomPacketPayload {

    /** The payload as the foundation built it, with no {@link Extra}. */
    public HearthStatePayload(int reason, long pos, String dimension, int stage, int ceiling,
            boolean smouldering, int comfort, List<String> comfortKinds, int character, int characterNext,
            List<PoolRow> pools, int defenceStreak, boolean siegesOn, int siegePhase, float playHoursSinceSiege,
            float radius, int below, int above) {
        this(reason, pos, dimension, stage, ceiling, smouldering, comfort, comfortKinds, character, characterNext,
                pools, defenceStreak, siegesOn, siegePhase, playHoursSinceSiege, radius, below, above, Extra.NONE);
    }

    /**
     * One pool's boon as the panel prints it: the boon type id ({@code BoonType} name in lower case),
     * its number at the pool's effective tier and at the tier above, and a second number for the boons
     * that have one (Comfort regen: temp HP as a share of max health; Workshop: the refund chance;
     * readiness: Provisioned minutes). Regen's {@code value} is seconds per heart, readiness's the
     * seconds Warmth takes to fill; the others are fractions.
     */
    public record Boon(int pool, String boon, float value, float extra, float next, float nextExtra) {
    }

    /**
     * The server's data the client cannot know: the tier thresholds (the tracker's bars), each pool's
     * boon numbers, every Comfort kind there is (to name the missing ones), the Character maximum, the
     * offering the next stage asks for (an item id, "" at stage VI), the spacing between Hearths (the red
     * ring), the siege warning in ticks (the countdown bar) and all six offerings (an item id or
     * {@code #tag} each; added by the stages lane, written last): the client swallows a click with one
     * of them on the Hearth, and a dedicated server's datapack never reaches the client otherwise.
     */
    public record Extra(List<Float> thresholds, List<Boon> boons, List<String> comfortAll, int characterMax,
            String nextOffering, int spacing, long warningTicks, List<String> offerings) {

        public static final Extra NONE = new Extra(List.of(), List.of(), List.of(), 0, "", 0, 0L);

        /** The extra as the client lane built it, with no offerings. */
        public Extra(List<Float> thresholds, List<Boon> boons, List<String> comfortAll, int characterMax,
                String nextOffering, int spacing, long warningTicks) {
            this(thresholds, boons, comfortAll, characterMax, nextOffering, spacing, warningTicks, List.of());
        }

        /** From one Hearth's state and the live data. */
        public static Extra of(dev.amman.proficiency.hearth.HearthState state,
                dev.amman.proficiency.hearth.HearthData data, int spacing) {
            List<Float> thresholds = new ArrayList<>();
            for (Double threshold : data.thresholds()) {
                thresholds.add(threshold.floatValue());
            }
            List<Boon> boons = new ArrayList<>();
            for (dev.amman.proficiency.hearth.HearthPool pool : dev.amman.proficiency.hearth.HearthPool.VALUES) {
                dev.amman.proficiency.hearth.HearthData.PoolSpec spec = data.pool(pool);
                if (spec == null || spec.boon() == null) {
                    continue;
                }
                int tier = state.effectiveTier(pool, data);
                int next = Math.min(dev.amman.proficiency.hearth.HearthData.TIERS, tier + 1);
                boons.add(new Boon(pool.ordinal(), spec.boon().name().toLowerCase(java.util.Locale.ROOT),
                        (float) boonValue(spec, tier, data), (float) boonExtra(spec, tier, data),
                        (float) boonValue(spec, next, data), (float) boonExtra(spec, next, data)));
            }
            int stage = state.stage();
            // A layer that fell owes no second offering (the block entity remembers the highest paid), so the
            // panel shows none while the stage above is only being rebuilt.
            String offering = stage >= 0 && stage < data.stages().size() && state.highestOffering() <= stage
                    ? data.stages().get(stage).offering() : "";
            List<String> offerings = new ArrayList<>(dev.amman.proficiency.hearth.HearthData.TIERS);
            for (int i = 0; i < dev.amman.proficiency.hearth.HearthData.TIERS; i++) {
                String id = i < data.stages().size() ? data.stages().get(i).offering() : null;
                offerings.add(id == null ? "" : id);
            }
            return new Extra(thresholds, boons, List.copyOf(data.comfort().kinds()), data.character().max(),
                    offering == null ? "" : offering, spacing, data.siege().warningTicks(), List.copyOf(offerings));
        }

        private static double boonValue(dev.amman.proficiency.hearth.HearthData.PoolSpec spec, int tier,
                dev.amman.proficiency.hearth.HearthData data) {
            return switch (spec.boon()) {
                case READINESS -> dev.amman.proficiency.hearth.HearthRules.warmthRiseSeconds(tier, data);
                case REFUND_CHANCE -> spec.refundAt(tier);
                default -> spec.value(tier);
            };
        }

        private static double boonExtra(dev.amman.proficiency.hearth.HearthData.PoolSpec spec, int tier,
                dev.amman.proficiency.hearth.HearthData data) {
            return switch (spec.boon()) {
                case REGEN_OUT_OF_COMBAT -> spec.absorptionPerTier() * tier;
                case STATION_SPEED -> spec.refundAt(tier);
                case READINESS -> dev.amman.proficiency.hearth.HearthRules.provisionedMinutes(tier, data);
                default -> 0.0;
            };
        }

        void write(FriendlyByteBuf buf) {
            int count = Math.min(16, thresholds.size());
            buf.writeVarInt(count);
            for (int i = 0; i < count; i++) {
                buf.writeFloat(thresholds.get(i));
            }
            int boonCount = Math.min(16, boons.size());
            buf.writeVarInt(boonCount);
            for (int i = 0; i < boonCount; i++) {
                Boon boon = boons.get(i);
                buf.writeVarInt(boon.pool());
                buf.writeUtf(boon.boon(), MAX_STRING);
                buf.writeFloat(boon.value());
                buf.writeFloat(boon.extra());
                buf.writeFloat(boon.next());
                buf.writeFloat(boon.nextExtra());
            }
            int kinds = Math.min(MAX_KINDS, comfortAll.size());
            buf.writeVarInt(kinds);
            for (int i = 0; i < kinds; i++) {
                buf.writeUtf(comfortAll.get(i), MAX_STRING);
            }
            buf.writeVarInt(characterMax);
            buf.writeUtf(nextOffering, MAX_STRING);
            buf.writeVarInt(spacing);
            buf.writeVarLong(warningTicks);
            List<String> sent = offerings == null ? List.of() : offerings;
            int offeringCount = Math.min(MAX_OFFERINGS, sent.size());
            buf.writeVarInt(offeringCount);
            for (int i = 0; i < offeringCount; i++) {
                buf.writeUtf(sent.get(i), MAX_STRING);
            }
        }

        static Extra read(FriendlyByteBuf buf) {
            int count = Math.min(16, Math.max(0, buf.readVarInt()));
            List<Float> thresholds = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                thresholds.add(buf.readFloat());
            }
            int boonCount = Math.min(16, Math.max(0, buf.readVarInt()));
            List<Boon> boons = new ArrayList<>(boonCount);
            for (int i = 0; i < boonCount; i++) {
                boons.add(new Boon(buf.readVarInt(), buf.readUtf(MAX_STRING), buf.readFloat(), buf.readFloat(),
                        buf.readFloat(), buf.readFloat()));
            }
            int kinds = Math.min(MAX_KINDS, Math.max(0, buf.readVarInt()));
            List<String> comfortAll = new ArrayList<>(kinds);
            for (int i = 0; i < kinds; i++) {
                comfortAll.add(buf.readUtf(MAX_STRING));
            }
            int characterMax = buf.readVarInt();
            String offering = buf.readUtf(MAX_STRING);
            int spacing = buf.readVarInt();
            long warning = buf.readVarLong();
            int offeringCount = Math.min(MAX_OFFERINGS, Math.max(0, buf.readVarInt()));
            List<String> offerings = new ArrayList<>(offeringCount);
            for (int i = 0; i < offeringCount; i++) {
                offerings.add(buf.readUtf(MAX_STRING));
            }
            return new Extra(List.copyOf(thresholds), List.copyOf(boons), List.copyOf(comfortAll), characterMax,
                    offering, spacing, warning, List.copyOf(offerings));
        }
    }

    public static final int ENTER = 0;
    public static final int TIER = 1;
    public static final int PANEL = 2;
    public static final int UPDATE = 3;
    /**
     * An empty-hand click on a plain campfire: the panel at stage 0 (a fresh state, nothing saved), so the
     * build guide reaches stage I before any Hearth exists. The client keeps it apart from the Hearth it
     * is in range of ({@code HearthView.panelState}).
     */
    public static final int CAMPFIRE = 4;

    private static final int MAX_KINDS = 64;
    /** The six stages' offerings, with room for a pack that adds none (the data has exactly six). */
    private static final int MAX_OFFERINGS = 16;
    private static final int MAX_STRING = 256;

    public static final CustomPacketPayload.Type<HearthStatePayload> TYPE =
            new CustomPacketPayload.Type<>(Proficiency.id("hearth_state"));

    public static final StreamCodec<FriendlyByteBuf, HearthStatePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.reason());
                buf.writeLong(p.pos());
                buf.writeUtf(p.dimension(), MAX_STRING);
                buf.writeVarInt(p.stage());
                buf.writeVarInt(p.ceiling());
                buf.writeBoolean(p.smouldering());
                buf.writeVarInt(p.comfort());
                int kinds = Math.min(MAX_KINDS, p.comfortKinds().size());
                buf.writeVarInt(kinds);
                for (int i = 0; i < kinds; i++) {
                    buf.writeUtf(p.comfortKinds().get(i), MAX_STRING);
                }
                buf.writeVarInt(p.character());
                buf.writeVarInt(p.characterNext());
                PoolRow.writeList(buf, p.pools());
                buf.writeVarInt(p.defenceStreak());
                buf.writeBoolean(p.siegesOn());
                buf.writeVarInt(p.siegePhase());
                buf.writeFloat(p.playHoursSinceSiege());
                buf.writeFloat(p.radius());
                buf.writeVarInt(p.below());
                buf.writeVarInt(p.above());
                (p.extra() == null ? Extra.NONE : p.extra()).write(buf);
            },
            buf -> {
                int reason = buf.readVarInt();
                long pos = buf.readLong();
                String dimension = buf.readUtf(MAX_STRING);
                int stage = buf.readVarInt();
                int ceiling = buf.readVarInt();
                boolean smouldering = buf.readBoolean();
                int comfort = buf.readVarInt();
                int kinds = Math.min(MAX_KINDS, Math.max(0, buf.readVarInt()));
                List<String> comfortKinds = new ArrayList<>(kinds);
                for (int i = 0; i < kinds; i++) {
                    comfortKinds.add(buf.readUtf(MAX_STRING));
                }
                int character = buf.readVarInt();
                int characterNext = buf.readVarInt();
                List<PoolRow> pools = PoolRow.readList(buf);
                int streak = buf.readVarInt();
                boolean siegesOn = buf.readBoolean();
                int siegePhase = buf.readVarInt();
                float hours = buf.readFloat();
                float radius = buf.readFloat();
                int below = buf.readVarInt();
                int above = buf.readVarInt();
                Extra extra = Extra.read(buf);
                return new HearthStatePayload(reason, pos, dimension, stage, ceiling, smouldering, comfort,
                        List.copyOf(comfortKinds), character, characterNext, pools, streak, siegesOn, siegePhase,
                        hours, radius, below, above, extra);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
