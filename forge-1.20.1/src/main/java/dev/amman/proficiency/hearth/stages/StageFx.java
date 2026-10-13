package dev.amman.proficiency.hearth.stages;

import dev.amman.proficiency.net.DiscoveryPayload;
import dev.amman.proficiency.net.ProficiencyNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * The stage moments as everyone nearby sees and hears them: the campfire flaring into a Hearth, a
 * stage sealed (a beacon-like chord that climbs with the stage, the bell's chime at V, a golden
 * ring at VI), a layer falling and standing again, the fire put out. A few dozen vanilla particles
 * each, sent once (the Particles video setting thins them), never per tick. The banner across the
 * top of the screen is the mod's own {@code DiscoveryBanner} ({@link DiscoveryPayload#HEARTH}).
 */
public final class StageFx {

    private StageFx() {
    }

    private static double cx(BlockPos pos) {
        return pos.getX() + 0.5;
    }

    private static double cz(BlockPos pos) {
        return pos.getZ() + 0.5;
    }

    /** A flat ring of one particle each, {@code points} around the fire at {@code radius} and height {@code dy}. */
    private static void ring(ServerLevel level, BlockPos pos, ParticleOptions particle, double radius, double dy,
            int points, double lift) {
        for (int i = 0; i < points; i++) {
            double a = i * (Math.PI * 2.0 / points);
            level.sendParticles(particle, cx(pos) + Math.cos(a) * radius, pos.getY() + dy, cz(pos) + Math.sin(a) * radius,
                    0, 0.0, lift, 0.0, 1.0);
        }
    }

    /**
     * Particles climbing the four posts of stage III, {@code perPost} on each, on the corner of each post that
     * faces the fire (inside the post they would be hidden).
     */
    private static void posts(ServerLevel level, BlockPos pos, ParticleOptions particle, int perPost) {
        double corner = 1.42;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                for (int i = 0; i < perPost; i++) {
                    double dy = 0.15 + i * (2.7 / Math.max(1, perPost - 1));
                    level.sendParticles(particle, cx(pos) + sx * corner, pos.getY() + dy, cz(pos) + sz * corner,
                            0, 0.0, 0.02, 0.0, 1.0);
                }
            }
        }
    }

    /** The campfire flares and becomes the Hearth. */
    public static void lit(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0f, 0.9f);
        level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.5f, 0.7f);
        level.playSound(null, pos, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS, 1.0f, 1.0f);
        double y = pos.getY() + 0.4;
        level.sendParticles(ParticleTypes.FLAME, cx(pos), y, cz(pos), 24, 0.25, 0.35, 0.25, 0.04);
        level.sendParticles(ParticleTypes.LAVA, cx(pos), y, cz(pos), 5, 0.2, 0.1, 0.2, 0.0);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, cx(pos), y + 0.6, cz(pos), 2, 0.1, 0.1, 0.1, 0.01);
        ring(level, pos, ParticleTypes.SMALL_FLAME, 1.5, 0.05, 16, 0.02);
    }

    /** A stage sealed with its offering. The chord climbs with the stage; V chimes, VI rings gold. */
    public static void sealed(ServerLevel level, BlockPos pos, int stage) {
        float pitch = 0.75f + 0.08f * stage;
        level.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.9f, pitch);
        level.playSound(null, pos, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS, 1.0f, 1.2f);
        if (stage == 5) {
            level.playSound(null, pos, SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 0.6f, 1.0f);
        }
        if (stage >= 6) {
            level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0f, 1.0f);
        }
        level.sendParticles(ParticleTypes.FLAME, cx(pos), pos.getY() + 0.5, cz(pos), 12 + 3 * stage, 0.2, 0.5, 0.2, 0.03);
        // The light runs along the layer just sealed: a ring on the floor or round the roof, and up the four
        // posts at III. (A ring at mid-post height ran through the face of the player making the offering,
        // who stands just where the posts are.)
        if (stage == 3) {
            posts(level, pos, ParticleTypes.FLAME, 5);
        } else {
            double dy = stage == 1 || stage == 2 || stage == 6 ? 0.05 : 3.2;
            double radius = stage == 1 ? 1.5 : 2.5;
            ring(level, pos, stage >= 6 ? ParticleTypes.WAX_ON : ParticleTypes.FLAME, radius, dy, 20, 0.0);
        }
        if (stage >= 6) {
            ring(level, pos, ParticleTypes.END_ROD, 1.0, 0.6, 12, 0.08);
        }
    }

    /** A layer fell: the ceiling dropped. A low hiss and a puff of smoke. */
    public static void fell(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.4f, 0.7f);
        level.sendParticles(ParticleTypes.SMOKE, cx(pos), pos.getY() + 0.5, cz(pos), 10, 0.2, 0.3, 0.2, 0.01);
    }

    /** The ceiling is back up: the fire catches again. */
    public static void rose(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS, 1.0f, 1.1f);
        level.playSound(null, pos, SoundEvents.BLAZE_SHOOT, SoundSource.BLOCKS, 0.25f, 0.6f);
        level.sendParticles(ParticleTypes.FLAME, cx(pos), pos.getY() + 0.5, cz(pos), 10, 0.2, 0.4, 0.2, 0.03);
    }

    /** The Hearth is put out (broken). */
    public static void putOut(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.BLOCKS, 1.0f, 0.8f);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, cx(pos), pos.getY() + 0.4, cz(pos), 10, 0.25, 0.3, 0.25, 0.02);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, cx(pos), pos.getY() + 0.6, cz(pos), 3, 0.1, 0.2, 0.1, 0.01);
    }

    /**
     * The banner across the top of the screen. {@code first}: this player's first Hearth ever lit,
     * the big moment; else a stage, with {@code by} naming who sealed it ("" for the player who did).
     */
    public static void banner(ServerPlayer player, int stage, boolean first, String by) {
        // One channel carries every payload on Forge: sendIfPresent skips a client without it.
        ProficiencyNetwork.sendIfPresent(player, new DiscoveryPayload(
                first ? DiscoveryPayload.HEARTH_FIRST : DiscoveryPayload.HEARTH, StageText.nameKey(stage),
                DiscoveryPayload.hearthId(stage), -1, 0f, by == null ? "" : by));
    }
}
