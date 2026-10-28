package dev.amman.proficiency.net;

import dev.amman.proficiency.Proficiency;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Today's Haul (1.44.0): the client asks for its card, {@link #BED} when its player lay down in a bed (sent only
 * while {@code ui.haulCard} is on) or {@link #KEY} from the login reminder's key. Optional: a client sends it only
 * after the server's login hello proved the server has it.
 */
public record HaulRequestPayload(int reason) implements CustomPacketPayload {

    /** Old clients (1.44.0 to 1.47.0) sent this on lying down; the server ignores it now (1.48.0). */
    public static final int BED = 0;
    public static final int KEY = 1;
    /** In bed: "is there an unseen haul?" The server answers with a LOGIN-mode summary, or says nothing. */
    public static final int PEEK = 2;
    /** The bed screen's "Today's Haul" button was clicked: the card, and the log starts over. */
    public static final int OPEN = 3;

    public static final CustomPacketPayload.Type<HaulRequestPayload> TYPE =
            new CustomPacketPayload.Type<>(Proficiency.id("haul_request"));

    public static final StreamCodec<FriendlyByteBuf, HaulRequestPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, HaulRequestPayload::reason, HaulRequestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
