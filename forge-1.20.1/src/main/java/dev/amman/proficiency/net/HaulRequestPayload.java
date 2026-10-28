package dev.amman.proficiency.net;

import net.minecraft.network.FriendlyByteBuf;
import dev.amman.proficiency.net.codec.ByteBufCodecs;
import dev.amman.proficiency.net.codec.StreamCodec;

/**
 * Today's Haul (1.44.0): the client asks for its card, {@link #BED} when its player lay down in a bed (only while
 * {@code ui.haulCard} is on) or {@link #KEY} from the login reminder's key. Sent only once the server's login hello
 * proved it has the haul channel.
 */
public record HaulRequestPayload(int reason) {

    /** Old clients (1.44.0 to 1.47.0) sent this on lying down; the server ignores it now (1.48.0). */
    public static final int BED = 0;
    public static final int KEY = 1;
    /** In bed: "is there an unseen haul?" The server answers with a LOGIN-mode summary, or says nothing. */
    public static final int PEEK = 2;
    /** The bed screen's "Today's Haul" button was clicked: the card, and the log starts over. */
    public static final int OPEN = 3;

    public static final StreamCodec<FriendlyByteBuf, HaulRequestPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, HaulRequestPayload::reason, HaulRequestPayload::new);
}
