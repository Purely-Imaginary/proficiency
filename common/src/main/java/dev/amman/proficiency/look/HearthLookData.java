package dev.amman.proficiency.look;

import dev.amman.proficiency.hearth.HearthAttachments;
import dev.amman.proficiency.hearth.HearthBlockEntity;
import dev.amman.proficiency.hearth.HearthData;
import dev.amman.proficiency.hearth.HearthPool;
import dev.amman.proficiency.hearth.HearthRules;
import dev.amman.proficiency.hearth.HearthState;
import dev.amman.proficiency.hearth.Hearths;
import dev.amman.proficiency.hearth.net.HearthStatePayload;
import dev.amman.proficiency.hearth.stages.HearthAutoBuild;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The server half of the Jade tooltip of a Hearth: the stage, the design lane, the charge tier and the
 * pool feeding it most, the looking player's Warmth, and the self-build as the panel's status line has it.
 * Small on purpose: a handful of numbers and two short strings, written when Jade asks (about twice a
 * second while someone looks). The lane reads the world around the Hearth, so it is cached for two seconds.
 *
 * <p>Both Minecraft versions compile this file unchanged (forge-1.20.1's sharedHearthJava).
 */
public final class HearthLookData {

    public static final String STAGE = "hs";
    public static final String SMOULDERS = "hz";
    public static final String TIER = "ht";
    /** The pool feeding the Hearth most this hour, as its ordinal plus one; absent when none. */
    public static final String FEEDING = "hp";
    public static final String LANE = "hl";
    public static final String WARMTH = "hw";
    public static final String BUILD_ON = "hb";
    public static final String BUILD_STAGE = "hbg";
    public static final String BUILD_DONE = "hbd";
    public static final String BUILD_TOTAL = "hbt";
    public static final String BUILD_SECONDS = "hbs";
    public static final String BUILD_WHY = "hbw";
    public static final String BUILD_MISSING = "hbm";

    private static final long LANE_TTL_TICKS = 40;

    private record Lane(long at, String id) {
    }

    private static final Map<String, Lane> LANES = new ConcurrentHashMap<>();

    private HearthLookData() {
    }

    public static void write(ServerPlayer player, HearthBlockEntity hearth, CompoundTag out) {
        if (!(hearth.getLevel() instanceof ServerLevel level)) {
            return;
        }
        HearthData data = Hearths.data();
        HearthState state = hearth.state();
        out.putInt(STAGE, state.stage());
        boolean smoulders = state.smouldering();
        out.putBoolean(SMOULDERS, smoulders);
        out.putInt(TIER, smoulders ? 0 : state.maxEffectiveTier(data));
        HearthPool top = smoulders ? null : HearthRules.feedingMost(state, level.getGameTime());
        if (top != null) {
            out.putInt(FEEDING, top.ordinal() + 1);
        }
        out.putString(LANE, lane(level, hearth));
        out.putFloat(WARMTH, HearthAttachments.of(player).warmth());
        HearthStatePayload.Build build = HearthAutoBuild.status(hearth);
        if (build.on()) {
            out.putBoolean(BUILD_ON, true);
            out.putInt(BUILD_STAGE, build.stage());
            out.putInt(BUILD_DONE, build.done());
            out.putInt(BUILD_TOTAL, build.total());
            out.putInt(BUILD_SECONDS, build.seconds());
            out.putInt(BUILD_WHY, build.why());
            if (!build.missing().isEmpty()) {
                out.putString(BUILD_MISSING, String.join(",", build.missing()));
            }
        }
    }

    /** The self-build the tag carries, or {@link HearthStatePayload.Build#NONE}. */
    public static HearthStatePayload.Build build(CompoundTag in) {
        if (!in.getBoolean(BUILD_ON)) {
            return HearthStatePayload.Build.NONE;
        }
        String missing = in.getString(BUILD_MISSING);
        return new HearthStatePayload.Build(true, in.getInt(BUILD_STAGE), in.getInt(BUILD_DONE), in.getInt(BUILD_TOTAL),
                in.getInt(BUILD_SECONDS), in.getInt(BUILD_WHY), missing.isEmpty() ? List.of() : List.of(missing.split(",")));
    }

    private static String lane(ServerLevel level, HearthBlockEntity hearth) {
        String key = hearth.key();
        long now = level.getGameTime();
        Lane cached = LANES.get(key);
        if (cached != null && now >= cached.at() && now - cached.at() < LANE_TTL_TICKS) {
            return cached.id();
        }
        String id = HearthAutoBuild.laneId(level, hearth.getBlockPos(), hearth);
        if (LANES.size() > 256) {
            LANES.clear();
        }
        LANES.put(key, new Lane(now, id));
        return id;
    }
}
