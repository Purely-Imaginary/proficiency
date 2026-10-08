package dev.amman.proficiency.net;

import net.minecraft.network.FriendlyByteBuf;
import dev.amman.proficiency.net.codec.ByteBufCodecs;
import dev.amman.proficiency.net.codec.StreamCodec;

/** Clicking a perk in the panel. The server re-checks everything; the click is only a request. */
public record UnlockPerkPayload(String perkKey) {

    public static final String ID = "unlock_perk";

    public static final StreamCodec<FriendlyByteBuf, UnlockPerkPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, UnlockPerkPayload::perkKey,
                    UnlockPerkPayload::new);

}
