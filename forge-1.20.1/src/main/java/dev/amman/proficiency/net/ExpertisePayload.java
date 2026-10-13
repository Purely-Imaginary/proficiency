package dev.amman.proficiency.net;

import net.minecraft.network.FriendlyByteBuf;
import dev.amman.proficiency.net.codec.ByteBufCodecs;
import dev.amman.proficiency.net.codec.StreamCodec;

/**
 * A click in the Expertise screen: one rank into a node ({@link #INVEST}) or the respec button
 * ({@link #RESPEC}, node empty). The server re-checks everything; the click is only a request.
 */
public record ExpertisePayload(int action, String node) {

    public static final String ID = "expertise";

    public static final int INVEST = 0;
    public static final int RESPEC = 1;

    public static final StreamCodec<FriendlyByteBuf, ExpertisePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, ExpertisePayload::action,
                    ByteBufCodecs.STRING_UTF8, ExpertisePayload::node,
                    ExpertisePayload::new);

}
