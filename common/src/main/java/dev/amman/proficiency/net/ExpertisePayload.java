package dev.amman.proficiency.net;

import dev.amman.proficiency.Proficiency;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A click in the Expertise screen: one rank into a node ({@link #INVEST}) or the respec button
 * ({@link #RESPEC}, node empty). The server re-checks everything; the click is only a request.
 */
public record ExpertisePayload(int action, String node) implements CustomPacketPayload {

    public static final int INVEST = 0;
    public static final int RESPEC = 1;

    public static final CustomPacketPayload.Type<ExpertisePayload> TYPE =
            new CustomPacketPayload.Type<>(Proficiency.id("expertise"));

    public static final StreamCodec<FriendlyByteBuf, ExpertisePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, ExpertisePayload::action,
                    ByteBufCodecs.STRING_UTF8, ExpertisePayload::node,
                    ExpertisePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
