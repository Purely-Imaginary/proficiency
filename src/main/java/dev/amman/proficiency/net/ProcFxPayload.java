package dev.amman.proficiency.net;

import dev.amman.proficiency.Proficiency;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A proc just landed; play its particles. Sent to everyone near the player, so bystanders see it
 * too. The client turns it into particles from {@code skill/ProcFx}, so the packet carries only
 * where and about what.
 *
 * @param skillOrdinal the skill, by ordinal
 * @param entityId the player who procced, to find their position and heading
 * @param hasFocus whether (fx, fy, fz) means anything; false means "round the player"
 * @param stateId a {@code Block.getId} block state id, or 0 for none
 * @param height blocks of log above the focus, for the woodcutting trunk
 */
public record ProcFxPayload(int skillOrdinal, int entityId, boolean hasFocus, double fx, double fy,
        double fz, int stateId, int height) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ProcFxPayload> TYPE =
            new CustomPacketPayload.Type<>(Proficiency.id("proc_fx"));

    public static final int MAX_HEIGHT = 16;

    public static final StreamCodec<FriendlyByteBuf, ProcFxPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.skillOrdinal());
                buf.writeVarInt(p.entityId());
                buf.writeBoolean(p.hasFocus());
                if (p.hasFocus()) {
                    buf.writeDouble(p.fx());
                    buf.writeDouble(p.fy());
                    buf.writeDouble(p.fz());
                }
                buf.writeVarInt(p.stateId());
                buf.writeVarInt(p.height());
            },
            buf -> {
                int skill = buf.readVarInt();
                int entity = buf.readVarInt();
                boolean has = buf.readBoolean();
                double x = 0;
                double y = 0;
                double z = 0;
                if (has) {
                    x = buf.readDouble();
                    y = buf.readDouble();
                    z = buf.readDouble();
                }
                int state = buf.readVarInt();
                int height = Math.max(0, Math.min(MAX_HEIGHT, buf.readVarInt()));
                if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                    has = false;
                }
                return new ProcFxPayload(skill, entity, has, x, y, z, Math.max(0, state), height);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
