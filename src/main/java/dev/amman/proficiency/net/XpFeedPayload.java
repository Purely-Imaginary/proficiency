package dev.amman.proficiency.net;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.skill.SkillService;
import dev.amman.proficiency.skill.XpFactors;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * The opt-in debug feed: every XP gain since the last half-second sync tick, unmerged, each with the multipliers that were not 1.0. Only sent
 * to a player who turned it on with {@code /skills xpfeed}, so nobody else pays for it.
 */
public record XpFeedPayload(List<SkillService.XpFeedGain> gains) implements CustomPacketPayload {

    private static final int MAX_SOURCE_LENGTH = 256;
    private static final int MAX_FACTOR_ID = 16;

    public static final CustomPacketPayload.Type<XpFeedPayload> TYPE =
            new CustomPacketPayload.Type<>(Proficiency.id("xp_feed"));

    public static final StreamCodec<FriendlyByteBuf, XpFeedPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.gains().size());
                for (SkillService.XpFeedGain gain : payload.gains()) {
                    buf.writeVarInt(gain.skill());
                    String source = gain.source().length() > MAX_SOURCE_LENGTH ? "" : gain.source();
                    buf.writeUtf(source, MAX_SOURCE_LENGTH);
                    buf.writeFloat(gain.amount());
                    buf.writeFloat(gain.base());
                    int count = Math.min(XpFactors.MAX, gain.factors().size());
                    buf.writeVarInt(count);
                    for (int i = 0; i < count; i++) {
                        buf.writeUtf(gain.factors().get(i).id(), MAX_FACTOR_ID);
                        buf.writeFloat(gain.factors().get(i).value());
                    }
                }
            },
            buf -> {
                int size = Math.min(SkillService.XP_FEED_MAX, Math.max(0, buf.readVarInt()));
                List<SkillService.XpFeedGain> gains = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    int skill = buf.readVarInt();
                    String source = buf.readUtf(MAX_SOURCE_LENGTH);
                    float amount = buf.readFloat();
                    float base = buf.readFloat();
                    int count = Math.min(XpFactors.MAX, Math.max(0, buf.readVarInt()));
                    List<XpFactors.Factor> factors = new ArrayList<>(count);
                    for (int f = 0; f < count; f++) {
                        factors.add(new XpFactors.Factor(buf.readUtf(MAX_FACTOR_ID), buf.readFloat()));
                    }
                    gains.add(new SkillService.XpFeedGain(skill, source, amount, base,
                            List.copyOf(factors)));
                }
                return new XpFeedPayload(List.copyOf(gains));
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
