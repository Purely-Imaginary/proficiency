package dev.amman.proficiency.net;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.skill.XpLog;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * The recent-XP lists for the tree screens. Each packet names the skills it carries and holds
 * every line of each of them (at most {@link XpLog#CAPACITY} per skill); the client replaces just
 * those skills. Sent by the same half-second sync tick as the skills and only for skills that
 * changed, so a mining session costs two small packets a second, each with only Mining in it.
 *
 * <p>Times go over as ages ("this many millis before the packet was built") rather than as the
 * server's clock, so a client whose clock disagrees with the server's still reads "5s ago".
 */
public record XpLogPayload(List<Integer> skills, List<Line> lines) implements CustomPacketPayload {

    /** Longest source key accepted off the wire. Translation keys are far shorter than this. */
    private static final int MAX_SOURCE_LENGTH = 256;
    /** Longest factor text accepted off the wire; a full set of factors is under 150. */
    private static final int MAX_FACTORS_LENGTH = 256;
    /** Most skills in one packet; there are far fewer skills than this. */
    private static final int MAX_SKILLS = 64;

    /**
     * One line, as {@link XpLog.Entry} with ages instead of times.
     *
     * @param firstAgeMillis how long before the packet the line's oldest gain was
     * @param ageMillis      how long before the packet the line's newest gain was
     */
    public record Line(int skill, String source, float amount, float base, int count,
            long firstAgeMillis, long ageMillis, String factors) {
    }

    public static final CustomPacketPayload.Type<XpLogPayload> TYPE =
            new CustomPacketPayload.Type<>(Proficiency.id("xp_log"));

    public static final StreamCodec<FriendlyByteBuf, XpLogPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.skills().size());
                for (int skill : payload.skills()) {
                    buf.writeVarInt(skill);
                }
                buf.writeVarInt(payload.lines().size());
                for (Line line : payload.lines()) {
                    buf.writeVarInt(line.skill());
                    buf.writeUtf(line.source(), MAX_SOURCE_LENGTH);
                    buf.writeFloat(line.amount());
                    buf.writeFloat(line.base());
                    buf.writeVarInt(line.count());
                    buf.writeVarLong(Math.max(0L, line.firstAgeMillis()));
                    buf.writeVarLong(Math.max(0L, line.ageMillis()));
                    buf.writeUtf(line.factors(), MAX_FACTORS_LENGTH);
                }
            },
            buf -> {
                int skillCount = Math.min(MAX_SKILLS, Math.max(0, buf.readVarInt()));
                List<Integer> skills = new ArrayList<>(skillCount);
                for (int i = 0; i < skillCount; i++) {
                    skills.add(buf.readVarInt());
                }
                int size = Math.min(XpLog.CAPACITY * skillCount, Math.max(0, buf.readVarInt()));
                List<Line> lines = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    lines.add(new Line(buf.readVarInt(), buf.readUtf(MAX_SOURCE_LENGTH),
                            buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readVarLong(),
                            buf.readVarLong(), buf.readUtf(MAX_FACTORS_LENGTH)));
                }
                return new XpLogPayload(List.copyOf(skills), List.copyOf(lines));
            });

    /** A snapshot of the given skills of one player's log, aged against {@code now}. */
    public static XpLogPayload of(XpLog log, List<Integer> skills, long now) {
        List<Line> lines = new ArrayList<>();
        for (int skill : skills) {
            for (XpLog.Entry entry : log.entries(skill)) {
                String source = entry.source().length() > MAX_SOURCE_LENGTH ? "" : entry.source();
                String factors = entry.factors().length() > MAX_FACTORS_LENGTH ? "" : entry.factors();
                lines.add(new Line(entry.skill(), source, entry.amount(), entry.base(), entry.count(),
                        now - entry.first(), now - entry.at(), factors));
            }
        }
        return new XpLogPayload(List.copyOf(skills), List.copyOf(lines));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
