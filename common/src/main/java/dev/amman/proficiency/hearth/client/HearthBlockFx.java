package dev.amman.proficiency.hearth.client;

import dev.amman.proficiency.hearth.HearthBlock;
import dev.amman.proficiency.hearth.HearthBlockEntity;
import dev.amman.proficiency.hearth.HearthPool;
import dev.amman.proficiency.hearth.charge.PoolColors;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * The Hearth block's own life on the client (docs/HEARTH-SPEC.md, "Building and upgrading": its look
 * grows with the ceiling). The model's animated flame does most of it; this adds what a model cannot,
 * by stage: an ember glow at I, sparks from II, a taller flame from III, smoke rising out of the roof
 * hole from IV, more of it at V, and a slow golden spiral of motes at VI. The model grows with the
 * stage (the flame plane tops out at {@link #FLAME_TOP}, the log bed sits at {@link #BED}); every
 * effect height follows those two tables. A smouldering Hearth only smokes.
 * Motes in the colour of the pool fed most this hour rise from the fire (the block's {@code feed}).
 *
 * <p>GPU-light: about one particle every two or three ticks on average at stage VI, all vanilla
 * particles through {@code addParticle}, so the Particles video setting thins them as it does a
 * campfire's. The smoke is a campfire's, through {@code addAlwaysVisibleParticle} as vanilla's, so the
 * column out of the roof is seen from afar. The block entity's client ticker calls it for every loaded
 * Hearth: past {@link #NEAR} blocks from the camera only the smoke is made (the game drops the other
 * particles there anyway), past {@link #FAR} nothing runs at all.
 */
public final class HearthBlockFx {

    /** The game draws no ordinary particle farther than this from the camera (vanilla's 32 blocks). */
    static final double NEAR = 32;
    /** Past this not even the smoke column is made: it would be a few pixels at most. */
    static final double FAR = 128;

    /** Top of the model's flame plane, in blocks over the block's floor, by stage (index 0 = stage I). */
    private static final double[] FLAME_TOP = {0.69, 0.94, 1.31, 1.63, 1.75, 2.0};
    /** Height of the logs' top (where the fire starts), in blocks, by stage. */
    private static final double[] BED = {0.44, 0.47, 0.66, 0.72, 0.78, 0.84};
    /** Orange motes of the rising ember column. */
    private static final DustParticleOptions CINDER = new DustParticleOptions(new Vector3f(1.0f, 0.55f, 0.12f), 0.55f);

    private static final DustParticleOptions HALO = new DustParticleOptions(new Vector3f(1.0f, 0.84f, 0.32f), 0.8f);
    /** One mote per pool, in the pool's colour ({@link PoolColors}): what fed the Hearth most this hour. */
    private static final DustParticleOptions[] FEED = feedMotes();

    private static DustParticleOptions[] feedMotes() {
        DustParticleOptions[] motes = new DustParticleOptions[HearthPool.COUNT];
        for (HearthPool pool : HearthPool.VALUES) {
            motes[pool.ordinal()] = new DustParticleOptions(Vec3.fromRGB24(PoolColors.rgb(pool)).toVector3f(), 0.7f);
        }
        return motes;
    }

    private HearthBlockFx() {
    }

    /** The client ticker of {@code proficiency:hearth}. */
    public static void clientTick(Level level, BlockPos pos, BlockState state, HearthBlockEntity hearth) {
        double x = pos.getX() + 0.5;
        double y = pos.getY();
        double z = pos.getZ() + 0.5;
        double away = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceToSqr(x, y, z);
        if (away > FAR * FAR) {
            return;
        }
        boolean near = away <= NEAR * NEAR;
        RandomSource random = level.random;
        int stage = Math.max(1, Math.min(6, state.getValue(HearthBlock.STAGE)));
        double bed = BED[stage - 1];
        double top = FLAME_TOP[stage - 1];
        boolean smoulders = state.getValue(HearthBlock.SMOULDERING);
        if (smoulders) {
            if (!near) {
                return;
            }
            if (random.nextFloat() < 0.07f) {
                level.addParticle(ParticleTypes.SMOKE, x + jitter(random, 0.25), y + bed + 0.1, z + jitter(random, 0.25),
                        0.0, 0.02 + random.nextDouble() * 0.01, 0.0);
            }
            return;
        }
        // Smoke, as a campfire: thin while the pit is open, a column out of the roof hole from IV.
        float smoke = stage >= 4 ? 0.06f + 0.015f * (stage - 4) : 0.035f;
        if (random.nextFloat() < smoke) {
            level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true,
                    x + jitter(random, 0.3), y + top + random.nextDouble() * 0.4, z + jitter(random, 0.3),
                    0.0, stage >= 4 ? 0.085 : 0.06, 0.0);
        }
        if (!near) {
            return;
        }
        // Ember glow: small flickers just over the logs.
        if (random.nextFloat() < 0.05f + 0.01f * stage) {
            level.addParticle(ParticleTypes.SMALL_FLAME, x + jitter(random, 0.28 + 0.03 * stage), y + bed + 0.05, z + jitter(random, 0.28 + 0.03 * stage),
                    0.0, 0.004, 0.0);
        }
        // Sparks from II: a pop that arcs out of the fire.
        if (stage >= 2 && random.nextFloat() < 0.012f + 0.006f * (stage - 2)) {
            level.addParticle(ParticleTypes.LAVA, x + jitter(random, 0.2 + 0.02 * stage), y + bed + 0.25, z + jitter(random, 0.2 + 0.02 * stage),
                    0.0, 0.0, 0.0);
        }
        // A taller flame from III: tongues that lick above the model's own.
        if (stage >= 3 && random.nextFloat() < 0.06f + 0.02f * (stage - 3)) {
            level.addParticle(ParticleTypes.FLAME, x + jitter(random, 0.18 + 0.02 * stage), y + top * 0.6 + random.nextDouble() * top * 0.25, z + jitter(random, 0.18 + 0.02 * stage),
                    0.0, 0.025 + random.nextDouble() * 0.02, 0.0);
        }
        // A rising ember column from II: orange cinders drift up the flame, so a bigger Hearth throws them higher.
        if (stage >= 2 && random.nextFloat() < 0.012f * stage) {
            level.addParticle(CINDER, x + jitter(random, 0.15), y + bed + 0.2, z + jitter(random, 0.15),
                    jitter(random, 0.004), 0.02 + 0.012 * stage + random.nextDouble() * 0.01, jitter(random, 0.004));
        }
        // The colour of what fed it most this hour (the block's feed property): a mote now and then that
        // rises out of the fire and drifts, so a mining base's Hearth breathes green and a kitchen's amber.
        int feed = state.getValue(HearthBlock.FEED);
        if (feed > 0 && random.nextFloat() < 0.04f + 0.008f * stage) {
            level.addParticle(FEED[feed - 1], x + jitter(random, 0.22), y + bed + 0.2 + random.nextDouble() * 0.2,
                    z + jitter(random, 0.22), 0.0, 0.03 + random.nextDouble() * 0.02, 0.0);
        }
        // The golden spiral at VI: one mote every other tick climbs a helix round the flame, wide at the
        // brazier's rim and narrowing towards the crown (the same rate as the old two-mote halo).
        if (stage >= 6 && (level.getGameTime() + pos.asLong()) % 2 == 0) {
            long t = level.getGameTime();
            double climb = (t % 80) / 80.0;
            double a = t * 0.11;
            double radius = 0.7 - 0.25 * climb;   // inside the centre cell's diagonal: the cells there hold the build
            level.addParticle(HALO, x + Math.cos(a) * radius, y + 1.0 + climb * (top - 0.9),
                    z + Math.sin(a) * radius, 0.0, 0.004, 0.0);
        }
        // The crackle a campfire makes, now and then.
        if (random.nextInt(320) == 0) {
            level.playLocalSound(x, y + 0.3, z, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS,
                    0.6f + random.nextFloat() * 0.4f, 0.6f + random.nextFloat() * 0.6f, false);
        }
    }

    private static double jitter(RandomSource random, double spread) {
        return (random.nextDouble() * 2 - 1) * spread;
    }
}
