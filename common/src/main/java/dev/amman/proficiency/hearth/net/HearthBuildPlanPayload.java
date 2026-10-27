package dev.amman.proficiency.hearth.net;

import dev.amman.proficiency.Proficiency;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Server to client: the self-build's plan for the Hearth at {@code pos}, for the plan screen. The stage it builds,
 * the lane (an index into the shipped designs), whether it runs, the seconds per block, how many cells only need
 * clearing and how many hold a player's block. Per role: the blocks still needed, the chosen block id and every
 * block in range that fits the role, with its count and where it lies. Sent when the screen asks
 * ({@link HearthRequestPayload#BUILD_PLAN}) and after a choice, a start or a stop.
 */
public record HearthBuildPlanPayload(long pos, int stage, int lane, boolean running, int secondsPerBlock, int clears,
        int foreign, List<Row> rows) implements CustomPacketPayload {

    /** Where some of a block lies: a container's offset from the fire, or a player's pack ({@code pack}). */
    public record Where(boolean pack, int dx, int dy, int dz, int count) {
    }

    /** One block that fits a role: its id, the count in all sources and the biggest places it lies. */
    public record Option(String block, int count, List<Where> where) {
    }

    /** One role of the stage: blocks needed, the chosen block id ("" none yet) and the options, most first. */
    public record Row(char role, int need, String chosen, List<Option> options) {
    }

    public static final int MAX_ROWS = 40;
    public static final int MAX_OPTIONS = 24;
    public static final int MAX_WHERE = 6;
    private static final int MAX_ID = 128;

    public static final CustomPacketPayload.Type<HearthBuildPlanPayload> TYPE =
            new CustomPacketPayload.Type<>(Proficiency.id("hearth_build_plan"));

    public static final StreamCodec<FriendlyByteBuf, HearthBuildPlanPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeLong(p.pos());
                buf.writeVarInt(p.stage());
                buf.writeVarInt(p.lane());
                buf.writeBoolean(p.running());
                buf.writeVarInt(p.secondsPerBlock());
                buf.writeVarInt(p.clears());
                buf.writeVarInt(p.foreign());
                int rows = Math.min(MAX_ROWS, p.rows().size());
                buf.writeVarInt(rows);
                for (int i = 0; i < rows; i++) {
                    Row row = p.rows().get(i);
                    buf.writeChar(row.role());
                    buf.writeVarInt(row.need());
                    buf.writeUtf(row.chosen(), MAX_ID);
                    int options = Math.min(MAX_OPTIONS, row.options().size());
                    buf.writeVarInt(options);
                    for (int j = 0; j < options; j++) {
                        Option option = row.options().get(j);
                        buf.writeUtf(option.block(), MAX_ID);
                        buf.writeVarInt(option.count());
                        int wheres = Math.min(MAX_WHERE, option.where().size());
                        buf.writeVarInt(wheres);
                        for (int k = 0; k < wheres; k++) {
                            Where where = option.where().get(k);
                            buf.writeBoolean(where.pack());
                            buf.writeVarInt(where.dx());
                            buf.writeVarInt(where.dy());
                            buf.writeVarInt(where.dz());
                            buf.writeVarInt(where.count());
                        }
                    }
                }
            },
            buf -> {
                long pos = buf.readLong();
                int stage = buf.readVarInt();
                int lane = buf.readVarInt();
                boolean running = buf.readBoolean();
                int seconds = buf.readVarInt();
                int clears = buf.readVarInt();
                int foreign = buf.readVarInt();
                int rowCount = Math.min(MAX_ROWS, Math.max(0, buf.readVarInt()));
                List<Row> rows = new ArrayList<>(rowCount);
                for (int i = 0; i < rowCount; i++) {
                    char role = buf.readChar();
                    int need = buf.readVarInt();
                    String chosen = buf.readUtf(MAX_ID);
                    int optionCount = Math.min(MAX_OPTIONS, Math.max(0, buf.readVarInt()));
                    List<Option> options = new ArrayList<>(optionCount);
                    for (int j = 0; j < optionCount; j++) {
                        String block = buf.readUtf(MAX_ID);
                        int count = buf.readVarInt();
                        int whereCount = Math.min(MAX_WHERE, Math.max(0, buf.readVarInt()));
                        List<Where> where = new ArrayList<>(whereCount);
                        for (int k = 0; k < whereCount; k++) {
                            where.add(new Where(buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                                    buf.readVarInt()));
                        }
                        options.add(new Option(block, count, List.copyOf(where)));
                    }
                    rows.add(new Row(role, need, chosen, List.copyOf(options)));
                }
                return new HearthBuildPlanPayload(pos, stage, lane, running, seconds, clears, foreign, List.copyOf(rows));
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
