package dev.amman.proficiency.net;

import dev.amman.proficiency.Proficiency;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Clicking a perk in the panel. The server re-checks everything; the click is only a request. */
public record UnlockPerkPayload(String perkKey) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<UnlockPerkPayload> TYPE =
            new CustomPacketPayload.Type<>(Proficiency.id("unlock_perk"));

    public static final StreamCodec<FriendlyByteBuf, UnlockPerkPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, UnlockPerkPayload::perkKey,
                    UnlockPerkPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
