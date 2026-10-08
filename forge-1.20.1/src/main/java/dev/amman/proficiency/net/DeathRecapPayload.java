package dev.amman.proficiency.net;

import dev.amman.proficiency.net.codec.StreamCodec;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * What a death wiped, for the recap panel after respawn: every bar that lost progress with the
 * fill it had before and the fill it kept (a ward keeps some), plus the streak that went. Sent
 * once, from the respawn clone, so the client never has to guess from a before/after of its own.
 *
 * @param rows biggest loss first, at most {@link #MAX_ROWS}
 * @param more how many more bars were lost than {@code rows} lists, for the "+N more" line
 * @param streakStacks stacks the death took (0 when there was no streak)
 * @param streakPercent the bonus those stacks were worth, in whole percent
 * @param restedXp the rested XP the death emptied, in whole XP, summed over every skill (protocol 6)
 * @param restedSkills how many skills had any rested XP to lose
 */
public record DeathRecapPayload(List<Row> rows, int more, int streakStacks, int streakPercent,
        int restedXp, int restedSkills) {

    public static final String ID = "death_recap";

    /** The panel is small; the chat lines still list everything. */
    public static final int MAX_ROWS = 8;

    /** Most rows a decoder will read at all; more than this is a broken packet, not a long list. */
    private static final int HARD_LIMIT = 64;

    /** A death that lost no rested XP, as before protocol 6. */
    public DeathRecapPayload(List<Row> rows, int more, int streakStacks, int streakPercent) {
        this(rows, more, streakStacks, streakPercent, 0, 0);
    }

    /** One wiped bar. {@code before} and {@code after} are fills from 0 to 1. */
    public record Row(int skillOrdinal, float before, float after) {
    }

    public static final StreamCodec<FriendlyByteBuf, DeathRecapPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.rows().size());
                for (Row row : payload.rows()) {
                    buf.writeVarInt(row.skillOrdinal());
                    buf.writeFloat(row.before());
                    buf.writeFloat(row.after());
                }
                buf.writeVarInt(payload.more());
                buf.writeVarInt(payload.streakStacks());
                buf.writeVarInt(payload.streakPercent());
                buf.writeVarInt(payload.restedXp());
                buf.writeVarInt(payload.restedSkills());
            },
            buf -> {
                int count = buf.readVarInt();
                if (count < 0 || count > HARD_LIMIT) {
                    throw new io.netty.handler.codec.DecoderException("Bad death recap row count " + count);
                }
                // Read every row the sender wrote so the rest of the packet stays in step, and keep
                // only as many as the panel has room for.
                List<Row> rows = new ArrayList<>(Math.min(count, MAX_ROWS));
                for (int i = 0; i < count; i++) {
                    Row row = new Row(buf.readVarInt(), buf.readFloat(), buf.readFloat());
                    if (i < MAX_ROWS) {
                        rows.add(row);
                    }
                }
                return new DeathRecapPayload(rows, Math.max(0, buf.readVarInt()), buf.readVarInt(),
                        buf.readVarInt(), Math.max(0, buf.readVarInt()), Math.max(0, buf.readVarInt()));
            });

    /** As {@link #of(Map, float[], PlayerSkills, int, int, Map)}, for a death with no rested XP to lose. */
    public static DeathRecapPayload of(Map<Skill, Float> lost, float[] before, PlayerSkills after,
            int streakStacks, int streakPercent) {
        return of(lost, before, after, streakStacks, streakPercent, Map.of());
    }

    /**
     * Builds the payload from the penalty's result. {@code before} is every skill's fill before the
     * wipe, indexed by ordinal; {@code after} is read from the skills as they stand now.
     */
    public static DeathRecapPayload of(Map<Skill, Float> lost, float[] before, PlayerSkills after,
            int streakStacks, int streakPercent, Map<Skill, Float> restedLost) {
        float restedTotal = 0f;
        for (float xp : restedLost.values()) {
            restedTotal += xp;
        }
        List<Row> rows = new ArrayList<>(Math.min(lost.size(), MAX_ROWS));
        lost.entrySet().stream()
                .sorted(Map.Entry.<Skill, Float>comparingByValue(Comparator.reverseOrder()))
                .limit(MAX_ROWS)
                .forEach(entry -> {
                    Skill skill = entry.getKey();
                    rows.add(new Row(skill.ordinal(), before[skill.ordinal()], after.barProgress(skill)));
                });
        return new DeathRecapPayload(rows, Math.max(0, lost.size() - rows.size()), Math.max(0, streakStacks),
                Math.max(0, streakPercent), Math.round(restedTotal), restedLost.size());
    }

    /** True when there is nothing to show. */
    public boolean isEmpty() {
        return rows.isEmpty() && more <= 0 && streakStacks <= 0 && restedXp <= 0;
    }
}
