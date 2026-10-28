package dev.amman.proficiency.skill;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * The in-world moment of a Mastery rider: arcs, trails, rings and bursts built from vanilla particles.
 * The server only names where each particle goes; every client draws them itself, under its own
 * Particles video setting. Every shape has a hard point cap, so a rider costs at most a couple of
 * hundred short-lived particles in one go. Visual only: nothing here touches an entity or a block.
 */
final class RiderFx {

    /** No line, arc or ring draws more points than this. */
    static final int MAX_POINTS = 32;

    static final DustParticleOptions PHANTOM = dust(0xCFEFFF, 1.0f);
    static final DustParticleOptions SPARK_GOLD = dust(0xFFE45C, 1.4f);
    static final DustParticleOptions SPARK_WHITE = dust(0xFFFFF0, 1.4f);
    static final DustParticleOptions TIDE = dust(0x6FE6FF, 1.4f);
    static final DustParticleOptions ARCANE = dust(0xB66BFF, 0.6f);
    static final DustParticleOptions BEAM = dust(0x8FF7FF, 0.7f);
    static final DustParticleOptions DUST_GREY = dust(0xE8E4DA, 1.3f);
    static final DustParticleOptions MORTAR = dust(0xF2EEE4, 0.9f);
    static final DustParticleOptions SONAR_GOLD = dust(0xFFC400, 1.0f);
    static final DustParticleOptions SONAR_RED = dust(0xFF3B30, 1.0f);
    static final DustParticleOptions POINTER_GOLD = dust(0xFFC400, 0.7f);
    static final DustParticleOptions POINTER_RED = dust(0xFF3B30, 0.7f);
    static final DustParticleOptions TIDE_PALE = dust(0xD8FBFF, 0.7f);
    static final DustParticleOptions DUST_GROUND = dust(0x9C8466, 1.6f);

    private RiderFx() {
    }

    static DustParticleOptions dust(int rgb, float scale) {
        return new DustParticleOptions(new Vector3f(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f,
                (rgb & 0xFF) / 255f), scale);
    }

    /** Runs a visual a few ticks from now; dropped if the server stops first. */
    static void later(ServerPlayer player, int ticks, Runnable fx) {
        MasteryTasks.later(player.serverLevel().getServer(), ticks, fx);
    }

    static void at(ServerLevel level, ParticleOptions p, Vec3 v) {
        level.sendParticles(p, v.x, v.y, v.z, 1, 0.0, 0.0, 0.0, 0.0);
    }

    static void burst(ServerLevel level, ParticleOptions p, Vec3 v, int count, double spread, double speed) {
        level.sendParticles(p, v.x, v.y, v.z, Math.min(count, MAX_POINTS), spread, spread, spread, speed);
    }

    /** One particle moving with {@code velocity} (blocks per tick). */
    static void moving(ServerLevel level, ParticleOptions p, Vec3 v, Vec3 velocity) {
        level.sendParticles(p, v.x, v.y, v.z, 0, velocity.x, velocity.y, velocity.z, 1.0);
    }

    /** The flat way the player faces (or {@code fallback} when looking straight up or down). */
    static Vec3 facing(ServerPlayer player, Vec3 fallback) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 1.0E-4) {
            flat = new Vec3(fallback.x, 0, fallback.z);
        }
        return flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
    }

    /** Points along a curve from a to b that rises {@code lift} blocks in the middle, {@code perBlock} a block. */
    static void arc(ServerLevel level, ParticleOptions p, Vec3 a, Vec3 b, double lift, double perBlock) {
        int n = steps(a, b, perBlock);
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n;
            at(level, p, a.lerp(b, t).add(0, lift * 4 * t * (1 - t), 0));
        }
    }

    /** Points along a curve from a to b bowed by {@code bend} (a sideways and up offset at the middle). */
    static void bow(ServerLevel level, ParticleOptions p, Vec3 a, Vec3 b, Vec3 bend, double perBlock) {
        int n = steps(a, b, perBlock);
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n;
            at(level, p, a.lerp(b, t).add(bend.scale(4 * t * (1 - t))));
        }
    }

    /** A jagged bolt from a to b: straight ends, sideways kinks in between. */
    static void zigzag(ServerLevel level, ParticleOptions p, ParticleOptions spark, Vec3 a, Vec3 b, double jitter,
            RandomSource random) {
        int n = steps(a, b, 4.0);
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n;
            double j = (i == 0 || i == n) ? 0 : jitter;
            Vec3 v = a.lerp(b, t).add((random.nextDouble() - 0.5) * j, (random.nextDouble() - 0.5) * j,
                    (random.nextDouble() - 0.5) * j);
            at(level, p, v);
            if (i % 2 == 0) {
                at(level, spark, v);
            }
        }
    }

    /** A ring of {@code points} at {@code radius}, each flying outward at {@code speed} (0 = still). */
    static void ring(ServerLevel level, ParticleOptions p, Vec3 center, double radius, int points, double speed) {
        int n = Math.min(points, MAX_POINTS);
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
            Vec3 at = center.add(out.scale(radius));
            if (speed > 0) {
                moving(level, p, at, out.scale(speed));
            } else {
                at(level, p, at);
            }
        }
    }

    /**
     * A blade's arc: {@code count} sweep crescents and a pale trail across {@code span} radians, centred on
     * {@code dir}, from {@code from} to {@code to} of that span (0 = left edge, 1 = right edge).
     */
    static void sweepArc(ServerLevel level, Vec3 center, Vec3 dir, double radius, double span, double from,
            double to, int count) {
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        for (int i = 0; i < count; i++) {
            double t = from + (to - from) * (count == 1 ? 0.5 : i / (double) (count - 1));
            double a = (t - 0.5) * span;
            Vec3 at = center.add(dir.scale(Math.cos(a) * radius)).add(side.scale(Math.sin(a) * radius))
                    .add(0, (t - 0.5) * -0.5, 0);
            // A sweep crescent's size is 1 - xd/2: a small negative xd draws it a little larger.
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 0, -0.5, 0.0, 0.0, 1.0);
            for (int k = 0; k < 3; k++) {
                double tt = t + (k - 1) * 0.04;
                double aa = (tt - 0.5) * span;
                at(level, PHANTOM, center.add(dir.scale(Math.cos(aa) * radius)).add(side.scale(Math.sin(aa) * radius))
                        .add(0, (tt - 0.5) * -0.5, 0));
            }
        }
    }

    private static int steps(Vec3 a, Vec3 b, double perBlock) {
        return Math.max(4, Math.min(MAX_POINTS, (int) Math.ceil(a.distanceTo(b) * perBlock)));
    }
}
