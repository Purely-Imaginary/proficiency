package dev.amman.proficiency.skill;

import dev.amman.proficiency.config.ProficiencyConfig;
import dev.amman.proficiency.telemetry.Telemetry;
import dev.amman.proficiency.telemetry.TelemetryHub;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDate;

/**
 * Loader-side glue for the balance telemetry in core: it answers "is it on", finds the world
 * folder, and turns the game's events into counter calls. Every call is a memory update; the
 * file is written by {@link TelemetryHub#flushIfDue} every five minutes and on stop.
 */
public final class TelemetryService {

    private static long lastTickMs;

    private TelemetryService() {
    }

    private static Telemetry data() {
        return TelemetryHub.data();
    }

    /** A grant that has just been added to the player's bar; {@code level} is the level after it. */
    public static void grant(ServerPlayer player, Skill skill, @Nullable String source, double base,
            double finalXp, int level) {
        grant(player, skill, source, base, finalXp, level, null);
    }

    /** As above, filed under {@code kindOverride} (such as the area-tool kind) when that is not null. */
    public static void grant(ServerPlayer player, Skill skill, @Nullable String source, double base,
            double finalXp, int level, @Nullable String kindOverride) {
        if (!ProficiencyConfig.telemetryEnabled()) {
            return;
        }
        data().grant(player.getUUID(), player.getGameProfile().getName(), skill, source, base, finalXp,
                level, System.currentTimeMillis(), kindOverride);
    }

    /** The extra XP a grant gained from the rested pool: its own telemetry kind. */
    public static void rested(ServerPlayer player, Skill skill, double xp) {
        if (ProficiencyConfig.telemetryEnabled()) {
            data().rested(player.getUUID(), player.getGameProfile().getName(), skill, xp);
        }
    }

    public static void levelUps(ServerPlayer player, Skill skill, int count, int level) {
        if (ProficiencyConfig.telemetryEnabled()) {
            data().levelUps(player.getUUID(), player.getGameProfile().getName(), skill, count, level);
        }
    }

    public static void roll(ServerPlayer player, Skill skill, double chance, boolean hit) {
        if (ProficiencyConfig.telemetryEnabled()) {
            data().roll(player.getUUID(), player.getGameProfile().getName(), skill, chance, hit);
        }
    }

    public static void proc(ServerPlayer player, Skill skill) {
        if (ProficiencyConfig.telemetryEnabled()) {
            data().proc(player.getUUID(), player.getGameProfile().getName(), skill);
        }
    }

    /** {@code lost} is the fraction of each bar the death wiped; the XP is that share of its level's cost. */
    public static void death(ServerPlayer player, PlayerSkills skills,
            java.util.Map<Skill, Float> lost, int streakLost) {
        if (!ProficiencyConfig.telemetryEnabled()) {
            return;
        }
        double[] xp = new double[Skill.VALUES.length];
        int[] levels = new int[Skill.VALUES.length];
        for (Skill skill : Skill.VALUES) {
            levels[skill.ordinal()] = skills.level(skill);
            Float share = lost.get(skill);
            if (share != null && share > 0f) {
                xp[skill.ordinal()] = share * (double) SkillMath.xpToNext(skills.level(skill));
            }
        }
        data().death(player.getUUID(), player.getGameProfile().getName(), xp, levels, streakLost);
    }

    /** Once a second from the server tick, for every online player. */
    public static void tick(ServerPlayer player) {
        if (!ProficiencyConfig.telemetryEnabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        // Real elapsed time, so a lagging server does not under-count; clamped against a stalled clock.
        long elapsed = lastTickMs == 0 ? 1000 : Math.max(0, Math.min(5000, now - lastTickMs));
        // At the controls means real input (moving, turning, earning XP from play), not the
        // survival streak's "any XP lately": passive trickles would keep an AFK player "active".
        boolean active = player.isAlive() && !player.isSpectator()
                && data().activeNow(player.getUUID(), player.getX(), player.getY(), player.getZ(),
                        player.getYRot(), player.getXRot(), now);
        data().tick(player.getUUID(), player.getGameProfile().getName(), active, elapsed, now);
    }

    /** Called once per second after the players were ticked: stamps the clock, flushes when due. */
    public static void afterPlayers(MinecraftServer server) {
        long now = System.currentTimeMillis();
        lastTickMs = now;
        if (!ProficiencyConfig.telemetryEnabled()) {
            return;
        }
        java.nio.file.Path folder = server.getWorldPath(LevelResource.ROOT).resolve("proficiency")
                .resolve("telemetry");
        // Also reopen when the folder is another world's: a single-player JVM that crashed or never
        // stopped would otherwise keep writing the old world's counts into the new one.
        if (!TelemetryHub.runningOn(folder)) {
            TelemetryHub.start(folder, ProficiencyConfig.telemetryRetentionDays(), LocalDate.now(), now);
        }
        TelemetryHub.flushIfDue(now, LocalDate.now());
    }

    /** Writes what is left and closes the session; safe when it never started. */
    public static void stop() {
        if (TelemetryHub.running()) {
            TelemetryHub.stop(System.currentTimeMillis(), LocalDate.now());
        }
        lastTickMs = 0;
    }
}
