package dev.amman.proficiency.platform.hooks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * NeoForge's block snapshot capture during {@code Item.useOn}: every block the use changes is
 * recorded with the state it had before, so place events can be posted afterwards (and a canceled
 * one reverted). Server thread only.
 */
public final class PlaceCapture {

    public record Frame(Level level, Map<BlockPos, BlockState> before) {
    }

    private static final ArrayDeque<Frame> FRAMES = new ArrayDeque<>();

    private PlaceCapture() {
    }

    public static Frame begin(Level level) {
        Frame frame = new Frame(level, new LinkedHashMap<>());
        FRAMES.push(frame);
        return frame;
    }

    public static void end(Frame frame) {
        FRAMES.remove(frame);
    }

    /** Called from Level.setBlock: remember the first state this position had during the use. */
    public static void record(Level level, BlockPos pos) {
        Frame frame = FRAMES.peek();
        if (frame != null && frame.level() == level && !frame.before().containsKey(pos)) {
            frame.before().put(pos.immutable(), level.getBlockState(pos));
        }
    }
}
