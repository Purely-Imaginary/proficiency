package dev.amman.proficiency.hearth.effects;

import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.config.ProficiencyConfig;
import dev.amman.proficiency.hearth.HearthRange;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.SurvivalStreak;
import dev.amman.proficiency.skill.TelemetryService;
import dev.amman.proficiency.telemetry.Telemetry;
import net.minecraft.server.level.ServerPlayer;

/**
 * The survival streak does not grow at home (docs/HEARTH-SPEC.md): in range of any Hearth, any
 * stage, charged or not, smouldering or not, {@code SurvivalStreak.tick} returns early. The stacks
 * are kept and still multiply XP earned at home, and a death there still wipes them. The flag is the
 * Hearth's in-range flag, so a broken Hearth, a teleport or {@code hearth.enabled = false} ends the
 * hold within 2 s. The HUD shows it as "Stacks safe at the Hearth" (the {@code STREAK_SAFE} flag).
 */
public final class HearthStreak {

    private HearthStreak() {
    }

    /** Whether the streak is held for this player now: in range of a Hearth that is there, switched on. */
    public static boolean holds(ServerPlayer player) {
        return ProficiencyConfig.hearthEnabled() && player.isAlive() && HearthRange.homeEntity(player) != null;
    }

    /**
     * Called by {@code SurvivalStreak.tick} instead of growing the streak. Telemetry
     * {@code streak_paused}: seconds held at home, "active" when the streak would have grown (the
     * player earned XP in the active window and is below the cap), else "idle".
     */
    public static void paused(ServerPlayer player, long elapsedTicks) {
        if (!SurvivalStreak.enabled()) {
            return;
        }
        PlayerSkills skills = ProficiencyAttachments.of(player);
        boolean active = skills.isActive(player.level().getGameTime(), ProficiencyConfig.streakActiveWindowTicks())
                && !SurvivalStreak.atCap(skills);
        TelemetryService.hearth(player, Telemetry.STREAK_PAUSED, active ? "active" : "idle", "",
                elapsedTicks / 20.0);
    }
}
